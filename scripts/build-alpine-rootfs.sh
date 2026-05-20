#!/usr/bin/env bash
# Alpine minirootfs に必要パッケージを追加したカスタム rootfs を生成し、
# app/src/main/assets/ に配置する。
#
# 依存 (host):
#   - curl
#   - tar, gzip
#   - fakeroot (非 root 環境で setuid/0xxx modes を正しく扱うため必須)
#   - apk-tools-static (このスクリプトが自動 DL する)
#   - qemu-aarch64 (apk-tools-static の post-install script が aarch64 バイナリを
#     呼ぶ場合があるため; --no-scripts 指定で原則回避)
#
# 出力:
#   app/src/main/assets/alpine-minirootfs-aarch64.tar.gz
#
# 使い方:
#   bash scripts/build-alpine-rootfs.sh             # arm64 のみ (推奨)
#   bash scripts/build-alpine-rootfs.sh aarch64 armv7  # 両 ABI
#
# 環境変数:
#   ALPINE_VERSION    既定 3.21.0
#   ALPINE_BRANCH     既定 v3.21
#   PKG_LIST          既定 scripts/alpine-packages.txt
#   FORCE             既定 0、1 なら既存 rootfs を上書き

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ALPINE_VERSION="${ALPINE_VERSION:-3.21.0}"
ALPINE_BRANCH="${ALPINE_BRANCH:-v3.21}"
PKG_LIST="${PKG_LIST:-${PROJECT_ROOT}/scripts/alpine-packages.txt}"
FORCE="${FORCE:-0}"

ARCHS=("$@")
if [[ ${#ARCHS[@]} -eq 0 ]]; then
    ARCHS=(aarch64)
fi

WORK_DIR="${PROJECT_ROOT}/build/alpine-rootfs"
ASSETS_DIR="${PROJECT_ROOT}/app/src/main/assets"
mkdir -p "${WORK_DIR}" "${ASSETS_DIR}"

# ---------------------------------------------------------------------------
# パッケージリストを配列に
# ---------------------------------------------------------------------------
mapfile -t PACKAGES < <(grep -vE '^\s*(#|$)' "${PKG_LIST}" | tr -d ' \t\r')
echo "[info] packages (${#PACKAGES[@]}):"
printf '         %s\n' "${PACKAGES[@]}"

# ---------------------------------------------------------------------------
# apk-tools-static を取得 (x86_64 host バイナリ、--arch でクロス展開対応)
# ---------------------------------------------------------------------------
APK_STATIC_DIR="${WORK_DIR}/apk-tools-static"
APK_STATIC="${APK_STATIC_DIR}/sbin/apk.static"
if [[ ! -x "${APK_STATIC}" ]]; then
    echo "[info] downloading apk-tools-static ..."
    mkdir -p "${APK_STATIC_DIR}"
    # 最新の apk-tools-static を fetch
    APK_TOOLS_URL="https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/main/x86_64/"
    apk_pkg="$(curl -sL "${APK_TOOLS_URL}" \
        | grep -oE 'apk-tools-static-[0-9][^"<]*\.apk' \
        | sort -V | tail -n1)"
    [[ -z "${apk_pkg}" ]] && { echo "ERROR: apk-tools-static が見つかりません" >&2; exit 1; }
    curl -sL "${APK_TOOLS_URL}${apk_pkg}" -o "${APK_STATIC_DIR}/apk.apk"
    tar -xzf "${APK_STATIC_DIR}/apk.apk" -C "${APK_STATIC_DIR}"
    rm -f "${APK_STATIC_DIR}/apk.apk"
fi
[[ -x "${APK_STATIC}" ]] || { echo "ERROR: ${APK_STATIC} 取得失敗" >&2; exit 1; }
echo "[info] apk-tools-static OK: $(${APK_STATIC} --version 2>&1 | head -n1)"

# ---------------------------------------------------------------------------
# Alpine keys (公式 keys-dir) を取得
# ---------------------------------------------------------------------------
KEYS_DIR="${WORK_DIR}/keys"
if [[ ! -d "${KEYS_DIR}" || -z "$(ls -A "${KEYS_DIR}" 2>/dev/null)" ]]; then
    echo "[info] downloading alpine-keys ..."
    mkdir -p "${KEYS_DIR}"
    ALPINE_KEYS_URL="https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/main/x86_64/"
    keys_pkg="$(curl -sL "${ALPINE_KEYS_URL}" \
        | grep -oE 'alpine-keys-[0-9][^"<]*\.apk' \
        | sort -V | tail -n1)"
    [[ -z "${keys_pkg}" ]] && { echo "ERROR: alpine-keys が見つかりません" >&2; exit 1; }
    tmpdir="$(mktemp -d)"
    curl -sL "${ALPINE_KEYS_URL}${keys_pkg}" -o "${tmpdir}/keys.apk"
    tar -xzf "${tmpdir}/keys.apk" -C "${tmpdir}"
    cp -v "${tmpdir}/etc/apk/keys/"*.rsa.pub "${KEYS_DIR}/" 2>/dev/null || true
    cp -v "${tmpdir}/usr/share/apk/keys/"*.rsa.pub "${KEYS_DIR}/" 2>/dev/null || true
    cp -rv "${tmpdir}/usr/share/apk/keys/"*/ "${KEYS_DIR}/" 2>/dev/null || true
    rm -rf "${tmpdir}"
fi
echo "[info] keys: $(ls "${KEYS_DIR}" | wc -l) files"

# ---------------------------------------------------------------------------
# 各 ABI でルートを構築
# ---------------------------------------------------------------------------
build_one() {
    local arch="$1"                  # aarch64 | armv7
    local out_name
    case "${arch}" in
        # .tgz 拡張子: aapt が `.tar.gz` を見ると assets 内で勝手に解凍 → .tar に
        # リネーム保存してしまい、ランタイムが open できなくなる。
        # `.tgz` は同等の意味だが aapt の自動処理対象外。
        aarch64) out_name="alpine-minirootfs-aarch64.tgz" ;;
        armv7)   out_name="alpine-minirootfs-armv7.tgz" ;;
        *) echo "unknown arch: ${arch}" >&2; return 1 ;;
    esac

    local rootfs_dir="${WORK_DIR}/${arch}/rootfs"
    local out_path="${ASSETS_DIR}/${out_name}"

    if [[ -f "${out_path}" && "${FORCE}" != "1" ]]; then
        echo "[skip] ${out_path} already exists (FORCE=1 で再生成)"
        return 0
    fi

    echo ""
    echo "[info] === building rootfs for ${arch} ==="
    rm -rf "${rootfs_dir}"
    mkdir -p "${rootfs_dir}"

    # fakeroot で apk を実行。非 root 環境でも apk が setuid bit や
    # 0xxx mode を「論理的に」設定でき、続く tar も fakeroot 経由で
    # 同じ metadata を参照してアーカイブできる。
    # /tmp/z2term-fakeroot.env に fakeroot の状態 DB を保存し、
    # apk install と tar pack の 2 ステップで共有する。
    local FR_STATE="${WORK_DIR}/${arch}/fakeroot.state"
    rm -f "${FR_STATE}"

    fakeroot -i "${FR_STATE}" -s "${FR_STATE}" -- \
        "${APK_STATIC}" \
            --root "${rootfs_dir}" \
            --keys-dir "${KEYS_DIR}" \
            --arch "${arch}" \
            --repository "https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/main" \
            --repository "https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/community" \
            --no-cache \
            --initdb \
            --no-scripts \
            add "${PACKAGES[@]}"

    # 物理ファイルは fakeroot の対象外なので、未承認のまま 0111 等の
    # 読めないモードで作成されることがある。tar が open() で蹴られないよう
    # ユーザー読み取り権限を全ファイルに付与する (fakeroot の metadata は
    # 失われないので tar 上のモードは正しいままになる)。
    find "${rootfs_dir}" -type f -exec chmod u+r {} +
    find "${rootfs_dir}" -type d -exec chmod u+rx {} +

    # /etc/apk/repositories を rootfs 内に保存 (端末上で apk add しやすくする)
    mkdir -p "${rootfs_dir}/etc/apk"
    cat >"${rootfs_dir}/etc/apk/repositories" <<EOF
https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/main
https://dl-cdn.alpinelinux.org/alpine/${ALPINE_BRANCH}/community
EOF

    # /etc/resolv.conf / /etc/hosts は DistroInstaller.postInstallSetup で
    # 上書きされるので、ここでは触れない (重複動作回避)。

    # Z2Term 用 motd / プロファイル
    mkdir -p "${rootfs_dir}/etc/profile.d"
    cat >"${rootfs_dir}/etc/profile.d/z2term.sh" <<'EOF'
# Z2Term auto-generated profile (bundled with the APK)
export TERM="${TERM:-xterm-256color}"
# mosh など UTF-8 native locale を要求するツール向け。musl は C.UTF-8 で
# nl_langinfo(CODESET)="UTF-8" を返すため mosh-client の locale チェックを満たす。
export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"
export EDITOR="${EDITOR:-vi}"
export PAGER="${PAGER:-less}"
alias ll='ls -alF'
alias la='ls -A'
alias l='ls -CF'
EOF
    chmod 0644 "${rootfs_dir}/etc/profile.d/z2term.sh"

    # /root の bashrc / zshrc 最低限
    mkdir -p "${rootfs_dir}/root"
    cat >"${rootfs_dir}/root/.bashrc" <<'EOF'
[ -f /etc/profile ] && . /etc/profile
export PS1='\[\e[32m\]\u@z2term\[\e[0m\]:\[\e[34m\]\w\[\e[0m\]\$ '
EOF
    cat >"${rootfs_dir}/root/.zshrc" <<'EOF'
[ -f /etc/profile ] && . /etc/profile
autoload -Uz compinit && compinit -u
autoload -Uz vcs_info
precmd() { vcs_info }
setopt prompt_subst
PROMPT='%F{green}%n@z2term%f:%F{blue}%~%f%# '
EOF
    chmod 0644 "${rootfs_dir}/root/.bashrc" "${rootfs_dir}/root/.zshrc"

    # /etc/shells に bash/zsh を追加 (Alpine デフォは ash のみ)
    {
        echo "/bin/sh"
        echo "/bin/ash"
        echo "/bin/bash"
        echo "/bin/zsh"
    } >"${rootfs_dir}/etc/shells"
    chmod 0644 "${rootfs_dir}/etc/shells"

    # /etc/passwd の root シェルを zsh に変更 (chsh 等で参照される)
    if [[ -f "${rootfs_dir}/etc/passwd" ]]; then
        sed -i -E 's|^(root:[^:]*:0:0:[^:]*:[^:]*:)[^:]*$|\1/bin/zsh|' \
            "${rootfs_dir}/etc/passwd"
    fi

    # tar.gz に圧縮 (POSIX ustar、所有者は root:root に正規化)
    # fakeroot で wrap して setuid/owner などの論理 metadata を tar に乗せる。
    echo "[info] packing ${out_name} ..."
    fakeroot -i "${FR_STATE}" -s "${FR_STATE}" -- \
        bash -c "cd '${rootfs_dir}' && tar --owner=0 --group=0 --numeric-owner --format=ustar -cf - ." \
        | gzip -9 >"${out_path}"

    local sz_h sz_b
    sz_b=$(stat -c %s "${out_path}")
    sz_h=$(numfmt --to=iec-i --suffix=B "${sz_b}")
    echo "[done] ${out_path} (${sz_h})"
}

for arch in "${ARCHS[@]}"; do
    build_one "${arch}"
done

echo ""
echo "[summary] assets:"
ls -lah "${ASSETS_DIR}/"*.tar.gz 2>/dev/null || true
