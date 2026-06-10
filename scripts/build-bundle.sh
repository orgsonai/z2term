#!/usr/bin/env bash
# Z2Term 同梱バンドル生成のマスタースクリプト。
#
# git 管理外 (gitignore 対象) で APK に同梱される生成物を「1 コマンドで全部」揃える。
# clone/clean 直後の環境で最初にこれを実行すれば、PC でもスマホでも同じ手順で
# 同じ同梱物セットが揃う (= 集め方が環境ごとにバラついて別物 APK ができる事故を防ぐ)。
#
# 1. PRoot バイナリ群を Termux deb から取得 (libproot/libproot_loader/libtalloc)
# 2. z2root 自前エンジンを NDK でクロスビルド (libz2root/libz2accept)
# 3. プログラミングフォントを取得 (IBMPlex/JetBrainsMono/FiraCode)
# 4. apk-tools-static で Alpine rootfs を構築し .tgz として assets に配置
# 最後に全同梱物が揃ったかを点検し、欠落があれば非ゼロ終了する。
#
# 出力 (すべて gitignore):
#   app/src/main/jniLibs/arm64-v8a/libproot.so
#   app/src/main/jniLibs/arm64-v8a/libproot_loader.so
#   app/src/main/jniLibs/arm64-v8a/libtalloc.so
#   app/src/main/jniLibs/arm64-v8a/libz2root.so
#   app/src/main/jniLibs/arm64-v8a/libz2accept.so
#   app/src/main/assets/fonts/{IBMPlexMono,JetBrainsMono,FiraCode}-Regular.ttf
#   app/src/full/assets/alpine-minirootfs-aarch64.tgz
#
# 前提: z2root のクロスビルドに NDK が要る。local.properties の sdk.dir+ndk.version
#   から自動解決する (build-z2root.sh)。env ANDROID_NDK_HOME でも可。
#
# 環境変数:
#   ARCHS         "aarch64" (既定) / "aarch64 armv7"
#   FORCE         1 で既存 rootfs を上書き再生成 (既定 0)
#   AUTO_LATEST   1 で Termux PRoot の最新版を自動選択 (既定 0)
#   ALPINE_BRANCH 既定 v3.21
#   SKIP_ROOTFS   1 で Alpine rootfs 構築を省略 (fakeroot が無い環境向け。
#                 その場合 rootfs は別環境で生成して持ち込む)
#
# 使い方:
#   bash scripts/build-bundle.sh
#   ARCHS="aarch64 armv7" FORCE=1 bash scripts/build-bundle.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# --------- 依存チェック ---------
need() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "ERROR: '$1' が見つかりません。インストールしてください。" >&2
        exit 1
    fi
}
need curl
need tar
need gzip
need ar
need unzip
# fakeroot は rootfs 構築 (build-alpine-rootfs.sh) でのみ使う。SKIP_ROOTFS 時は不要。
[[ "${SKIP_ROOTFS:-0}" == "1" ]] || need fakeroot

# --------- PRoot 取得 ---------
echo "=== [1/4] PRoot fetch ==="
bash "${SCRIPT_DIR}/build-proot.sh"

# --------- z2root クロスビルド ---------
echo ""
echo "=== [2/4] z2root native build ==="
bash "${SCRIPT_DIR}/build-z2root.sh"

# --------- フォント取得 ---------
echo ""
echo "=== [3/4] Fonts fetch ==="
bash "${SCRIPT_DIR}/fetch-fonts.sh"

# --------- Alpine rootfs 構築 ---------
echo ""
if [[ "${SKIP_ROOTFS:-0}" == "1" ]]; then
    echo "=== [4/4] Alpine rootfs build — SKIP (SKIP_ROOTFS=1) ==="
    echo "[warn] rootfs は別環境で生成して app/src/full/assets/ に持ち込むこと"
else
    echo "=== [4/4] Alpine rootfs build ==="
    # shellcheck disable=SC2086
    bash "${SCRIPT_DIR}/build-alpine-rootfs.sh" ${ARCHS:-aarch64}
fi

# --------- 完了点検: git 管理外の同梱物が全部揃ったか ---------
echo ""
echo "=== [verify] 同梱物マニフェスト ==="
REQUIRED=(
    "app/src/main/jniLibs/arm64-v8a/libproot.so"
    "app/src/main/jniLibs/arm64-v8a/libproot_loader.so"
    "app/src/main/jniLibs/arm64-v8a/libtalloc.so"
    "app/src/main/jniLibs/arm64-v8a/libz2root.so"
    "app/src/main/jniLibs/arm64-v8a/libz2accept.so"
    "app/src/main/assets/fonts/IBMPlexMono-Regular.ttf"
    "app/src/main/assets/fonts/JetBrainsMono-Regular.ttf"
    "app/src/main/assets/fonts/FiraCode-Regular.ttf"
)
# rootfs は full フレーバーのみ同梱 (foss は実行時 DL)。SKIP_ROOTFS 時は必須から外す。
if [[ "${SKIP_ROOTFS:-0}" != "1" ]]; then
    REQUIRED+=("app/src/full/assets/alpine-minirootfs-aarch64.tgz")
fi

missing=0
for rel in "${REQUIRED[@]}"; do
    f="${PROJECT_ROOT}/${rel}"
    if [[ -s "${f}" ]]; then
        printf '  OK   %-58s %s\n' "${rel}" "$(du -h "${f}" | cut -f1)"
    else
        printf '  MISS %s\n' "${rel}"
        missing=$((missing + 1))
    fi
done

echo ""
if [[ "${missing}" -gt 0 ]]; then
    echo "ERROR: ${missing} 件の同梱物が欠落しています。上記 MISS を再生成してください。" >&2
    exit 1
fi

echo "=== [done] git 管理外の同梱物が全て揃いました ==="
echo "次のステップ:"
echo "  ./gradlew :app:assembleFossDebug"
echo "  adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk"
