// z2accept — LD_PRELOAD シム (GPL-3.0)。
//
// musl 製のサーバ (Alpine の Xvnc / dropbear 等) は accept(2) を syscall 202 で直接呼ぶ。
// ところが Android の untrusted_app seccomp は accept(202) を禁止している (bionic は
// accept4(242) しか使わないため allowlist に無い) → 呼ぶと SIGSYS で弾かれる。z2root は
// その SIGSYS を「成功」と握り潰すしかなく、結果 accept がまともな接続ソケットを返せず、
// VNC(z2gui の Xvnc) や SSH のサーバが接続を一切受けられない (GUI が真っ黒のまま等)。
//
// このシムは accept() を accept4(...,0) に橋渡しするだけ。accept4 は Android 許可なので
// SIGSYS にならない。libc 非依存 (生 svc / -nostdlib) で musl・glibc どちらにも LD_PRELOAD
// でき、errno だけ実行時解決の __errno_location() 経由で正しく立てる。
//
// 適用は z2root エンジン経由の起動全体 (ProotLauncher が LD_PRELOAD で注入)。読み込み失敗は
// ld.so が警告して無視するだけ (非致命) なので、accept を使わないコマンドには無害。

// libc ヘッダを引かない (bionic 依存を避けるため)。型は最小限の前方宣言で足りる。
extern int *__errno_location(void);

// aarch64 raw syscall: accept4(fd, addr, addrlen, flags) = __NR_accept4(242)。
static long z2_accept4(long fd, long addr, long len, long flags) {
    register long x0 __asm__("x0") = fd;
    register long x1 __asm__("x1") = addr;
    register long x2 __asm__("x2") = len;
    register long x3 __asm__("x3") = flags;
    register long x8 __asm__("x8") = 242;
    __asm__ volatile("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x3), "r"(x8) : "memory", "cc");
    return x0;
}

// accept(fd, struct sockaddr *addr, socklen_t *addrlen) を accept4(...,0) で実装。
int accept(int fd, void *addr, void *addrlen) {
    long r = z2_accept4(fd, (long)addr, (long)addrlen, 0);
    if (r < 0) { *__errno_location() = (int)(-r); return -1; }
    return (int)r;
}
