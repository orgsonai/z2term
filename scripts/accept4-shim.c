/* z2term ビルド用 LD_PRELOAD シム。
 *
 * 一部の実機環境(オンデバイス aarch64, proot/z2root 下)では libc の accept() が
 * ENOSYS(Function not implemented)を返す。JDK17 の sun.nio.ch.Net.accept は
 * libc accept() を呼ぶため、Gradle デーモンの TCP IPC(accept ループ)が落ち、
 * "Could not connect to the Gradle daemon" でビルド不能になる。
 *
 * 一方 accept4 システムコール自体は全フラグで正常動作する(実測確認済)。そこで
 * accept()/accept4() を accept4 syscall へ直接橋渡しして回避する。
 *
 * 適用は scripts/gw.sh が「libc accept() が ENOSYS の環境でだけ」自動で行う。
 * PC など accept() が正常な環境では使わない(LD_PRELOAD しても動作は等価で無害)。
 */
#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>

int accept(int fd, struct sockaddr *addr, socklen_t *len) {
    return (int)syscall(SYS_accept4, fd, addr, len, 0);
}

int accept4(int fd, struct sockaddr *addr, socklen_t *len, int flags) {
    return (int)syscall(SYS_accept4, fd, addr, len, flags);
}
