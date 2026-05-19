#!/usr/bin/env bash
# Z2Term 同梱バンドル生成のマスタースクリプト。
#
# 1. PRoot バイナリを Termux deb から取得して jniLibs に配置
# 2. apk-tools-static で Alpine rootfs を構築 (zsh/bash/openssh/screen 等)
# 3. tar.gz (拡張子 .tgz) として assets に配置
#
# 出力:
#   app/src/main/jniLibs/arm64-v8a/libproot.so
#   app/src/main/jniLibs/arm64-v8a/libproot_loader.so
#   app/src/main/assets/alpine-minirootfs-aarch64.tgz
#
# 環境変数:
#   ARCHS         "aarch64" (既定) / "aarch64 armv7"
#   FORCE         1 で既存 rootfs を上書き再生成 (既定 0)
#   AUTO_LATEST   1 で Termux PRoot の最新版を自動選択 (既定 0)
#   ALPINE_BRANCH 既定 v3.21
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
need fakeroot

# --------- PRoot 取得 ---------
echo "=== [1/3] PRoot fetch ==="
bash "${SCRIPT_DIR}/build-proot.sh"

# --------- Alpine rootfs 構築 ---------
echo ""
echo "=== [2/3] Alpine rootfs build ==="
# shellcheck disable=SC2086
bash "${SCRIPT_DIR}/build-alpine-rootfs.sh" ${ARCHS:-aarch64}

# --------- フォント取得 ---------
echo ""
echo "=== [3/3] Fonts fetch ==="
bash "${SCRIPT_DIR}/fetch-fonts.sh"

echo ""
echo "=== [done] バンドル成果物 ==="
echo "jniLibs:"
find "${PROJECT_ROOT}/app/src/main/jniLibs" -type f -name 'libproot*.so' -exec ls -lah {} \;
echo ""
echo "assets:"
find "${PROJECT_ROOT}/app/src/main/assets" -type f \( -name '*.tgz' -o -name '*.tar.gz' \) -exec ls -lah {} \;
echo ""
echo "次のステップ:"
echo "  ./gradlew :app:assembleFossDebug"
echo "  adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk"
