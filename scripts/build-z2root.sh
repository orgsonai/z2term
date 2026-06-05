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

# NDK ルートを解決。
NDK="${ANDROID_NDK_HOME:-${NDK:-${ANDROID_NDK:-${ANDROID_NDK_ROOT:-}}}}"
[[ -n "${NDK}" ]] || { echo "ERROR: NDK パス未設定 (ANDROID_NDK_HOME を指定)" >&2; exit 1; }
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
# -static-pie 必須: --loader モード(自前 ELF ローダ)では本バイナリ自体がローダ子として
# ptrace 配下で起動するが、動的リンクだと bionic リンカの /proc/self/exe 解決が
# トレーサのパス変換に壊され SIGABRT する。静的化して bionic リンカ自体を無くすことで回避。
"${CC}" \
    -std=c11 -O2 -Wall -Wextra \
    -static-pie \
    -o "${OUT}" \
    "${SRC}"

chmod 0755 "${OUT}"
echo "[ok] wrote ${OUT}"
file "${OUT}" 2>/dev/null || true
