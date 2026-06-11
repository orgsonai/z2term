#!/usr/bin/env bash
# z2term ビルド用 ./gradlew ラッパー。
#
# 一部の実機環境(オンデバイス aarch64, proot/z2root 下)では libc の accept() が
# ENOSYS(Function not implemented)を返す。JDK17 の sun.nio.ch.Net.accept は
# libc accept() を呼ぶため、Gradle デーモンの TCP IPC(accept ループ)が落ち、
# "Could not connect to the Gradle daemon" でビルド不能になる。詳細と真因は
# scripts/accept4-shim.c のコメント参照。
#
# このラッパーは「libc accept() が ENOSYS の環境でだけ」自動で accept4 シムを
# LD_PRELOAD して ./gradlew を起動する。PC など accept() が正常な環境では素の
# ./gradlew を呼ぶだけなので無害(マルチデバイス運用を壊さない)。
#
# 使い方:
#   bash scripts/gw.sh :app:assembleFullRelease
#   bash scripts/gw.sh help          # シム適用の有無を確認できる
#
# 環境変数:
#   CC                  シムのコンパイラ (既定: cc)
#   Z2TERM_FORCE_SHIM   1 で accept() 判定を飛ばし常にシムを噛ませる
#   Z2TERM_NO_SHIM      1 で判定もシムも無効化し素の ./gradlew を呼ぶ

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLEW="${PROJECT_ROOT}/gradlew"
SHIM_SRC="${PROJECT_ROOT}/scripts/accept4-shim.c"
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/z2term"
SHIM_SO="${CACHE_DIR}/accept4-shim.so"
CC="${CC:-cc}"

log() { printf '[gw] %s\n' "$*" >&2; }

# libc accept() が ENOSYS かを小さなプローブで実測する。
# exit 0 = accept() 正常(シム不要) / exit 1 = ENOSYS など異常(シム必要)。
need_shim() {
    [ "${Z2TERM_NO_SHIM:-0}" = 1 ] && return 1
    [ "${Z2TERM_FORCE_SHIM:-0}" = 1 ] && return 0
    command -v "$CC" >/dev/null 2>&1 || { log "no $CC; シム判定不可→素のまま"; return 1; }

    local probe_c probe_bin
    probe_c="$(mktemp --suffix=.c)"
    probe_bin="$(mktemp)"
    cat >"$probe_c" <<'EOF'
#define _GNU_SOURCE
#include <sys/socket.h>
#include <netinet/in.h>
#include <errno.h>
#include <string.h>
#include <unistd.h>
/* ループバックに listen→connect し、libc accept() が成功するか試す。
   accept() が ENOSYS を返すなら 1、成功すれば 0 で抜ける。 */
int main(void) {
    int ls = socket(AF_INET, SOCK_STREAM, 0);
    if (ls < 0) return 0; /* 判定不能時は安全側=シム不要 */
    struct sockaddr_in a; memset(&a, 0, sizeof a);
    a.sin_family = AF_INET; a.sin_addr.s_addr = htonl(INADDR_LOOPBACK); a.sin_port = 0;
    if (bind(ls, (struct sockaddr*)&a, sizeof a) < 0) return 0;
    if (listen(ls, 1) < 0) return 0;
    socklen_t al = sizeof a;
    if (getsockname(ls, (struct sockaddr*)&a, &al) < 0) return 0;
    int cs = socket(AF_INET, SOCK_STREAM, 0);
    if (cs < 0) return 0;
    if (connect(cs, (struct sockaddr*)&a, sizeof a) < 0) return 0;
    int as = accept(ls, 0, 0);
    if (as < 0 && errno == ENOSYS) return 1;
    return 0;
}
EOF
    if ! "$CC" -O0 -o "$probe_bin" "$probe_c" 2>/dev/null; then
        rm -f "$probe_c" "$probe_bin"
        log "プローブのコンパイル失敗→素のまま"
        return 1
    fi
    local rc
    if "$probe_bin"; then
        rc=1   # probe exit 0 = accept() 正常 = シム不要
    else
        rc=0   # probe exit 非0 = ENOSYS = シム必要
    fi
    rm -f "$probe_c" "$probe_bin"
    return "$rc"
}

build_shim() {
    mkdir -p "$CACHE_DIR"
    if [ ! -f "$SHIM_SO" ] || [ "$SHIM_SRC" -nt "$SHIM_SO" ]; then
        log "accept4 シムをビルド: $SHIM_SO"
        "$CC" -shared -fPIC -o "$SHIM_SO" "$SHIM_SRC"
    fi
}

if need_shim; then
    build_shim
    log "libc accept() が ENOSYS → LD_PRELOAD=$SHIM_SO でビルド"
    exec env LD_PRELOAD="${LD_PRELOAD:+$LD_PRELOAD:}$SHIM_SO" "$GRADLEW" "$@"
else
    exec "$GRADLEW" "$@"
fi
