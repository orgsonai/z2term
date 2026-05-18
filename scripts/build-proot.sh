#!/usr/bin/env bash
# proot を自前ビルドして app/src/main/jniLibs/<abi>/ に libproot.so / libproot_loader.so を配置する。
#
# 依存: git, make, Android NDK (ANDROID_NDK_HOME か ANDROID_NDK_ROOT)
# Termux への依存は無い。proot-me/proot を clone してビルドするだけ。
#
# 使い方:
#   bash scripts/build-proot.sh
#
# 出力:
#   app/src/main/jniLibs/arm64-v8a/libproot.so
#   app/src/main/jniLibs/arm64-v8a/libproot_loader.so
#   app/src/main/jniLibs/armeabi-v7a/libproot.so          (32bit 端末用)
#   app/src/main/jniLibs/armeabi-v7a/libproot_loader.so
#
# 仕組み:
#   - proot のソースは Makefile ベース。NDK の clang をクロスコンパイラとして渡し
#     `make -C src CROSS_COMPILE=... loader.elf proot` で生成。
#   - 生成された ELF 実行ファイルを `lib<name>.so` の命名規約に rename して
#     jniLibs に置けば、APK の展開時に実行可能領域に配置される (Android 10+ の制約)。

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK_DIR="${PROJECT_ROOT}/build/proot-src"
JNI_DIR="${PROJECT_ROOT}/app/src/main/jniLibs"
PROOT_REPO="${PROOT_REPO:-https://github.com/proot-me/proot.git}"
PROOT_REF="${PROOT_REF:-master}"

NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "${NDK_HOME}" ]]; then
    # Android SDK の標準位置にあれば自動検出
    for guess in \
        "${ANDROID_HOME:-}/ndk" \
        "${ANDROID_SDK_ROOT:-}/ndk" \
        "${HOME}/Android/Sdk/ndk" \
        "/opt/android-sdk/ndk"
    do
        if [[ -d "${guess}" ]]; then
            NDK_HOME="$(ls -d "${guess}"/*/ 2>/dev/null | tail -n1 | sed 's:/$::')"
            [[ -n "${NDK_HOME}" ]] && break
        fi
    done
fi

if [[ -z "${NDK_HOME}" || ! -d "${NDK_HOME}" ]]; then
    echo "ERROR: Android NDK が見つかりません。ANDROID_NDK_HOME を設定してください。" >&2
    exit 1
fi
echo "[info] NDK: ${NDK_HOME}"

TOOLCHAIN="${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
if [[ ! -d "${TOOLCHAIN}/bin" ]]; then
    echo "ERROR: NDK toolchain が見つからない: ${TOOLCHAIN}" >&2
    exit 1
fi
export PATH="${TOOLCHAIN}/bin:${PATH}"

# proot ソースの取得
mkdir -p "${WORK_DIR}"
if [[ ! -d "${WORK_DIR}/.git" ]]; then
    echo "[info] cloning ${PROOT_REPO}"
    git clone --depth 1 --branch "${PROOT_REF}" "${PROOT_REPO}" "${WORK_DIR}"
else
    echo "[info] reusing existing checkout at ${WORK_DIR}"
    (cd "${WORK_DIR}" && git fetch --depth 1 origin "${PROOT_REF}" && git reset --hard FETCH_HEAD)
fi

API_LEVEL="${ANDROID_API_LEVEL:-29}"

build_one() {
    local abi="$1"           # arm64-v8a | armeabi-v7a
    local host="$2"          # aarch64-linux-android | armv7a-linux-androideabi
    local loader_arch="$3"   # arm64 | arm

    echo "[info] === building proot for ${abi} (host=${host}, api=${API_LEVEL}) ==="

    local CC="${host}${API_LEVEL}-clang"
    local AR="llvm-ar"
    local STRIP="llvm-strip"
    local LD="ld.lld"

    pushd "${WORK_DIR}/src" >/dev/null
    make distclean >/dev/null 2>&1 || true

    # proot の Makefile は CROSS_COMPILE を見るので、wrapper を CC として渡す。
    # Android NDK の clang は <triple><api>-clang の命名で API level も含むので、
    # CROSS_COMPILE 方式では足りない → CC/AR/STRIP/LD を直接指定する。
    make -j"$(nproc)" \
        CC="${CC}" \
        AR="${AR}" \
        STRIP="${STRIP}" \
        LD="${LD}" \
        CFLAGS="-O2 -fPIE -DHAS_LOADER_32BIT=0" \
        LDFLAGS="-pie -fuse-ld=lld" \
        loader.elf || true
    make -j"$(nproc)" \
        CC="${CC}" \
        AR="${AR}" \
        STRIP="${STRIP}" \
        LD="${LD}" \
        CFLAGS="-O2 -fPIE" \
        LDFLAGS="-pie -fuse-ld=lld" \
        proot
    popd >/dev/null

    local dst="${JNI_DIR}/${abi}"
    mkdir -p "${dst}"
    cp -v "${WORK_DIR}/src/proot"      "${dst}/libproot.so"
    cp -v "${WORK_DIR}/src/loader.elf" "${dst}/libproot_loader.so"
    "${TOOLCHAIN}/bin/llvm-strip" "${dst}/libproot.so" "${dst}/libproot_loader.so" || true
}

build_one arm64-v8a    aarch64-linux-android        arm64
build_one armeabi-v7a  armv7a-linux-androideabi     arm

echo ""
echo "[done] jniLibs に proot 配置完了:"
find "${JNI_DIR}" -type f -name 'libproot*.so' -exec ls -la {} \;
echo ""
echo "次は ./gradlew assembleFullDebug でリビルドしてください。"
