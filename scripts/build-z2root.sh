#!/usr/bin/env bash
# z2term 自前 ptrace エンジン (z2root) を Android NDK でクロスビルドし、
# app/src/main/jniLibs/<abi>/libz2root.so に PIE 実行ファイルとして配置する。
#
# 背景: FOSS フェーズ2 (docs/FOSS-PURE-HANDOFF.md §5)。proot/talloc を自前コードに
# 置き換えて外部ライセンス表記をゼロ化する取り組みの土台。build-proot.sh と同じ
# 「jniLibs に lib*.so 名で実行バイナリを置く」方式に揃える (Android API29+ は
# nativeLibraryDir からの execve のみ許可されるため、self-built でも同梱が必要)。
#
# 対応: aarch64 (arm64-v8a) のみ。armeabi-v7a は当面非対象 (既存方針どおり)。
#
# 使い方:
#   ANDROID_NDK_HOME=/path/to/ndk bash scripts/build-z2root.sh
#   (NDK / ANDROID_NDK / ANDROID_NDK_ROOT のいずれでも可)
#
# 環境変数:
#   ANDROID_NDK_HOME    NDK ルート (必須。未設定なら NDK/ANDROID_NDK/ANDROID_NDK_ROOT を探索)
#   Z2ROOT_API          ターゲット API レベル (既定: 29)

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${PROJECT_ROOT}/app/src/main/cpp/z2root/z2root.c"
JNI_DIR="${PROJECT_ROOT}/app/src/main/jniLibs"
API="${Z2ROOT_API:-29}"

[[ -f "${SRC}" ]] || { echo "ERROR: source not found: ${SRC}" >&2; exit 1; }

# NDK ルートを解決。明示 env を最優先し、無ければ Gradle/AGP と同じ場所
# (local.properties の sdk.dir + ndk.version / ANDROID_HOME 配下の ndk) を自動発見する。
# これにより Gradle の buildZ2rootNative タスクが env 未指定でも解決できる
# (= git pull だけで .so がソースと乖離する stale 事故を防ぐ自動化の土台)。
NDK="${ANDROID_NDK_HOME:-${NDK:-${ANDROID_NDK:-${ANDROID_NDK_ROOT:-}}}}"

# local.properties から sdk.dir / ndk.version を拾うヘルパ。
LOCAL_PROPS="${PROJECT_ROOT}/local.properties"
prop() { [[ -f "${LOCAL_PROPS}" ]] && sed -n "s/^$1=//p" "${LOCAL_PROPS}" | head -n1 | tr -d '\r'; }

if [[ -z "${NDK}" ]]; then
    SDK_DIR="$(prop sdk.dir)"
    SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
    NDK_VER="$(prop ndk.version)"
    if [[ -n "${SDK_DIR}" && -n "${NDK_VER}" && -d "${SDK_DIR}/ndk/${NDK_VER}" ]]; then
        NDK="${SDK_DIR}/ndk/${NDK_VER}"
    elif [[ -n "${SDK_DIR}" && -d "${SDK_DIR}/ndk" ]]; then
        # ndk.version 未指定(PC 既定)時は最新の NDK を選ぶ。
        NDK="$(ls -d "${SDK_DIR}"/ndk/*/ 2>/dev/null | sort -V | tail -n1)"
        NDK="${NDK%/}"
    fi
fi

# termux-ndk 等は android-ndk-rNN サブディレクトリを噛ませる構成があるため吸収する。
if [[ -n "${NDK}" && ! -d "${NDK}/toolchains" ]]; then
    sub="$(ls -d "${NDK}"/android-ndk-*/ 2>/dev/null | head -n1)"
    [[ -n "${sub}" ]] && NDK="${sub%/}"
fi

[[ -n "${NDK}" ]] || { echo "ERROR: NDK パス未設定 (ANDROID_NDK_HOME を指定するか local.properties に sdk.dir/ndk.version を書く)" >&2; exit 1; }
[[ -d "${NDK}" ]] || { echo "ERROR: NDK ディレクトリが無い: ${NDK}" >&2; exit 1; }

# ホスト tag (clang prebuilt の配置先) を判定。
case "$(uname -s)" in
    Linux)  HOST_TAG="linux-x86_64" ;;
    Darwin) HOST_TAG="darwin-x86_64" ;;
    *)      echo "ERROR: 未対応ホスト OS: $(uname -s)" >&2; exit 1 ;;
esac

TOOLCHAIN="${NDK}/toolchains/llvm/prebuilt/${HOST_TAG}"
CC="${TOOLCHAIN}/bin/aarch64-linux-android${API}-clang"
[[ -x "${CC}" ]] || { echo "ERROR: clang が無い: ${CC}" >&2; exit 1; }

OUT_DIR="${JNI_DIR}/arm64-v8a"
mkdir -p "${OUT_DIR}"
OUT="${OUT_DIR}/libz2root.so"

echo "[info] building z2root (aarch64, API ${API}) ..."
# -static (非PIE ET_EXEC) 必須: --loader モード(自前 ELF ローダ)では本バイナリ自体が
# ローダ子として ptrace 配下で起動するが、動的リンクだと bionic リンカの /proc/self/exe
# 解決がトレーサのパス変換に壊され SIGABRT する。静的化して bionic リンカ自体を無くすことで回避。
# なお bionic の -static-pie は NDK r28c では C ランタイムの自己再配置が main 到達前に
# SIGSEGV する(実機 ZY32LNFX2B で確認)。非PIE の -static は健全に起動するため -static を使う。
"${CC}" \
    -std=c11 -O2 -Wall -Wextra \
    -static \
    -o "${OUT}" \
    "${SRC}"

chmod 0755 "${OUT}"
echo "[ok] wrote ${OUT}"
file "${OUT}" 2>/dev/null || true

# --- accept→accept4 LD_PRELOAD シム (libz2accept.so) ---------------------------
# Android の untrusted_app seccomp は accept(202) を禁止(bionic は accept4 を使う)するため、
# musl 製サーバ(Alpine の Xvnc / dropbear 等)の accept が SIGSYS で弾かれ GUI/SSH が接続を
# 受けられない。z2root 起動時に LD_PRELOAD し accept を accept4(...,0) へ橋渡しする極小シム。
# libc 非依存(-nostdlib + 生 svc)で musl/glibc どちらにも効く。詳細は z2accept.c 冒頭。
SHIM_SRC="${PROJECT_ROOT}/app/src/main/cpp/z2accept/z2accept.c"
SHIM_OUT="${OUT_DIR}/libz2accept.so"
echo "[info] building z2accept shim (aarch64, API ${API}) ..."
"${CC}" \
    -shared -nostdlib -fPIC -O2 -Wall \
    -Wl,-soname,libz2accept.so \
    -o "${SHIM_OUT}" \
    "${SHIM_SRC}"
chmod 0644 "${SHIM_OUT}"
echo "[ok] wrote ${SHIM_OUT}"
file "${SHIM_OUT}" 2>/dev/null || true
