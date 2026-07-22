#!/usr/bin/env bash
# Termux の公式リポジトリから PRoot バイナリと libtalloc を取得し、
# app/src/full/jniLibs/<abi>/lib*.so にリネームして配置する。
#
# PRoot は third-party prebuilt のため F-Droid 非適合。よって full フレーバー専用の
# src/full/jniLibs に置き、foss フレーバー (src/main/jniLibs しか読まない) には同梱
# しない。foss の実行エンジンはソースビルドの z2root (build-z2root.sh)。
#
# 履歴: 当初は proot-me/proot のソースをクロスビルドする方針だったが、
# proot は talloc ライブラリに依存し NDK にも Termux 以外のクロス
# tarball にも含まれず、talloc 自体もクロスビルドが面倒。
# Termux パッケージは Android で実機動作確認済みなのでこちらを採用。
#
# 出力:
#   app/src/full/jniLibs/arm64-v8a/libproot.so
#   app/src/full/jniLibs/arm64-v8a/libproot_loader.so
#   app/src/full/jniLibs/arm64-v8a/libtalloc.so          (proot の依存)
#   app/src/full/jniLibs/arm64-v8a/libandroid-shmem.so   (proot の依存・新)
#
# 使い方:
#   bash scripts/build-proot.sh
#
# 環境変数:
#   PROOT_VERSION       既定: スクリプト内 PROOT_VER_AARCH64
#   LIBTALLOC_VERSION   既定: スクリプト内 LIBTALLOC_VER_AARCH64
#   AUTO_LATEST=1       最新版を自動検出

set -euo pipefail

# 低速・不安定な回線でも取得が落ちないようにする共通 curl オプション。
#   --retry 系      : 一時的な失敗・切断を自動で再試行する
#   --connect-timeout: 接続だけは早めに見切る (本体の転送時間は制限しない)
#   --speed-limit/time: 60 秒間 1KB/s を割り続けたら「停止」とみなして打ち切る
#     (--max-time だと 168MB の rootfs のような大きい取得を回線速度次第で誤爆させるため使わない)
CURL_NET=(--retry 5 --retry-delay 3 --retry-connrefused
          --connect-timeout 20 --speed-limit 1024 --speed-time 60)


PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI_DIR="${PROJECT_ROOT}/app/src/full/jniLibs"
WORK_DIR="${PROJECT_ROOT}/build/proot-fetch"
mkdir -p "${WORK_DIR}"

TERMUX_REPO="https://packages.termux.dev/apt/termux-main/pool/main/p/proot/"
TERMUX_LIBTALLOC_REPO="https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/"
TERMUX_SHMEM_REPO="https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/"
PROOT_VER_AARCH64="${PROOT_VERSION:-5.1.107.77}"
LIBTALLOC_VER_AARCH64="${LIBTALLOC_VERSION:-2.4.3}"
LIBSHMEM_VER_AARCH64="${LIBSHMEM_VERSION:-0.7}"

# pool ディレクトリ一覧から最新版番号を取り出す。
# Termux は pool に最新版しか残さないため、ピン留めした版は予告なく 404 になる。
latest_ver() {
    local repo="$1" prefix="$2"
    curl -sL "${CURL_NET[@]}" "${repo}" \
        | grep -oE "${prefix}_[0-9][^\"<]*_aarch64\.deb" \
        | sed -E "s/^${prefix}_([^_]+)_aarch64\.deb/\1/" \
        | sort -V | tail -n1
}

if [[ "${AUTO_LATEST:-0}" == "1" ]]; then
    PROOT_VER_AARCH64="$(latest_ver "${TERMUX_REPO}" proot)"
    LIBTALLOC_VER_AARCH64="$(latest_ver "${TERMUX_LIBTALLOC_REPO}" libtalloc)"
    LIBSHMEM_VER_AARCH64="$(latest_ver "${TERMUX_SHMEM_REPO}" libandroid-shmem)"
    [[ -z "${PROOT_VER_AARCH64}" || -z "${LIBTALLOC_VER_AARCH64}" || -z "${LIBSHMEM_VER_AARCH64}" ]] && { echo "ERROR: バージョン自動検出失敗" >&2; exit 1; }
    echo "[info] AUTO_LATEST: proot=${PROOT_VER_AARCH64}, libtalloc=${LIBTALLOC_VER_AARCH64}, libandroid-shmem=${LIBSHMEM_VER_AARCH64}"
fi

fetch_deb() {
    local repo="$1" deb_name="$2"
    local cache="${WORK_DIR}/${deb_name}"
    if [[ ! -f "${cache}" ]]; then
        echo "[info] fetching ${deb_name} ..." >&2
        curl -sL "${CURL_NET[@]}" "${repo}${deb_name}" -o "${cache}"
        [[ -s "${cache}" ]] || { echo "ERROR: 取得失敗 ${deb_name}" >&2; rm -f "${cache}"; return 1; }
    fi
    # 404 等で curl が HTML を保存しても -s は通ってしまうため、ar で .deb 妥当性を検証する
    # (検証漏れ時の症状: 後段の `ar x` が "file format not recognized" で落ちる)。
    if ! ar t "${cache}" >/dev/null 2>&1; then
        echo "ERROR: ${deb_name} は有効な .deb ではない (404/HTML 等の可能性)" >&2
        rm -f "${cache}"; return 1
    fi
    # Path のみ stdout に出す (caller が変数で受けるため info を混ぜない)
    printf '%s\n' "${cache}"
}

# ピン版を取得し、pool から消えていた場合は最新版へフォールバックする。
fetch_deb_resolving() {
    local repo="$1" prefix="$2" ver="$3" arch="$4" path
    if path="$(fetch_deb "${repo}" "${prefix}_${ver}_${arch}.deb")"; then
        printf '%s\n' "${path}"; return 0
    fi
    local latest; latest="$(latest_ver "${repo}" "${prefix}")"
    [[ -n "${latest}" ]] || { echo "ERROR: ${prefix} の最新版検出に失敗" >&2; return 1; }
    echo "[info] ${prefix} ${ver} が pool に無いため ${latest} を使用" >&2
    fetch_deb "${repo}" "${prefix}_${latest}_${arch}.deb"
}

# deb の data.tar.xz をカレントに展開する。
#
# tar の非ゼロ終了を握り潰しているのは、deb 内の symlink が原因の「パーミッション復元失敗」
# を無視するため。2 パターンある:
#   - share/doc/*/copyright → 別パッケージが持つ LICENSES/*.txt へのリンク (宛先が deb 内に無い)
#   - libtalloc.so / libtalloc.so.2 → 実体 libtalloc.so.2.4.3 へのリンク (実体より先に展開される)
# どちらも root で実行したときだけ問題になる (tar は root だと既定でパーミッションを復元し、
# その際に壊れた/未作成の宛先を辿って ENOENT になる。非 root では復元しないので起きない)。
#
# 取り出したいのは実体ファイルだけで、これらの symlink は使わない。展開が本当に失敗した場合は
# 呼び出し元の find による存在確認で捕まるので、ここで落とさなくても検出漏れにはならない。
extract_deb_data() {
    tar -xJf data.tar.xz || true
}

fetch_one() {
    local abi="$1"            # arm64-v8a | armeabi-v7a
    local termux_arch="$2"    # aarch64    | arm
    local proot_ver="$3"
    local libtalloc_ver="$4"
    local libshmem_ver="$5"

    local out_dst="${JNI_DIR}/${abi}"
    mkdir -p "${out_dst}"

    # ----- proot binary + loader -----
    local proot_deb_path
    proot_deb_path="$(fetch_deb_resolving "${TERMUX_REPO}" proot "${proot_ver}" "${termux_arch}")"
    local proot_extract="${WORK_DIR}/${abi}-proot"
    rm -rf "${proot_extract}"; mkdir -p "${proot_extract}"
    (cd "${proot_extract}" && ar x "${proot_deb_path}" && extract_deb_data)

    local proot_bin loader_bin
    proot_bin="$(find "${proot_extract}" -name 'proot' -type f -path '*/usr/bin/*' | head -n1)"
    loader_bin="$(find "${proot_extract}" -name 'loader' -type f -path '*/libexec/proot/*' | head -n1)"

    [[ -n "${proot_bin}"  ]] || { echo "ERROR: proot binary が deb 内に見つからない (${abi})" >&2; return 1; }
    [[ -n "${loader_bin}" ]] || { echo "ERROR: loader binary が deb 内に見つからない (${abi})" >&2; return 1; }

    cp -v "${proot_bin}"  "${out_dst}/libproot.so"
    cp -v "${loader_bin}" "${out_dst}/libproot_loader.so"
    chmod 0755 "${out_dst}/libproot.so" "${out_dst}/libproot_loader.so"

    # ----- libtalloc.so.2 (proot の依存) -----
    # Termux deb 内では libtalloc.so / libtalloc.so.2 は libtalloc.so.X.Y.Z への
    # symlink。リネーム規約上 Android jniLibs に置けるのは lib*.so 形式のみなので
    # 実体ファイル (libtalloc.so.X.Y.Z) を libtalloc.so として配置する。
    # ランタイムで filesDir/proot-libs/libtalloc.so.2 にコピーされ、proot から
    # LD_LIBRARY_PATH 経由で見えるようにする (ProotLauncher 参照)。
    local libtalloc_deb_path
    libtalloc_deb_path="$(fetch_deb_resolving "${TERMUX_LIBTALLOC_REPO}" libtalloc "${libtalloc_ver}" "${termux_arch}")"
    local libtalloc_extract="${WORK_DIR}/${abi}-libtalloc"
    rm -rf "${libtalloc_extract}"; mkdir -p "${libtalloc_extract}"
    (cd "${libtalloc_extract}" && ar x "${libtalloc_deb_path}" && extract_deb_data)

    local libtalloc_real
    libtalloc_real="$(find "${libtalloc_extract}" -name 'libtalloc.so.*' -type f ! -name '*.so.2' | head -n1)"
    [[ -z "${libtalloc_real}" ]] && \
        libtalloc_real="$(find "${libtalloc_extract}" -name 'libtalloc*' -type f | head -n1)"
    [[ -n "${libtalloc_real}" ]] || { echo "ERROR: libtalloc real ファイルが deb 内に見つからない (${abi})" >&2; return 1; }

    cp -v "${libtalloc_real}" "${out_dst}/libtalloc.so"
    chmod 0755 "${out_dst}/libtalloc.so"

    # ----- libandroid-shmem.so (proot の依存・新) -----
    # 新しい Termux proot は SysV 共有メモリのために libandroid-shmem.so にリンクされる。
    # 不在だと起動時に `CANNOT LINK EXECUTABLE "proot": library "libandroid-shmem.so"
    # not found` で即落ちする。SONAME は無印 (libandroid-shmem.so) なので jniLibs 規約
    # (lib*.so) にそのまま合致する。実体を libandroid-shmem.so として配置し、ランタイムで
    # libtalloc 同様 LD_LIBRARY_PATH (proot-libs) から見えるようにする (ProotLauncher 参照)。
    local libshmem_deb_path
    libshmem_deb_path="$(fetch_deb_resolving "${TERMUX_SHMEM_REPO}" libandroid-shmem "${libshmem_ver}" "${termux_arch}")"
    local libshmem_extract="${WORK_DIR}/${abi}-libshmem"
    rm -rf "${libshmem_extract}"; mkdir -p "${libshmem_extract}"
    (cd "${libshmem_extract}" && ar x "${libshmem_deb_path}" && extract_deb_data)

    local libshmem_real
    libshmem_real="$(find "${libshmem_extract}" -name 'libandroid-shmem.so' -type f | head -n1)"
    [[ -n "${libshmem_real}" ]] || { echo "ERROR: libandroid-shmem.so が deb 内に見つからない (${abi})" >&2; return 1; }

    cp -v "${libshmem_real}" "${out_dst}/libandroid-shmem.so"
    chmod 0755 "${out_dst}/libandroid-shmem.so"
}

# arm64 (現在 Z2Term は arm64 のみ同梱方針)
fetch_one arm64-v8a aarch64 "${PROOT_VER_AARCH64}" "${LIBTALLOC_VER_AARCH64}" "${LIBSHMEM_VER_AARCH64}"

echo ""
echo "[done] jniLibs に PRoot + libtalloc + libandroid-shmem 配置完了:"
find "${JNI_DIR}" -type f \( -name 'libproot*.so' -o -name 'libtalloc.so' -o -name 'libandroid-shmem.so' \) -exec ls -la {} \;
echo ""
echo "次は ./gradlew :app:assembleFossDebug でリビルドしてください。"
