// z2usb — Android USB Host API の fd を透過的に受け取る LD_PRELOAD シム (GPL-3.0)。
//
// Android のアプリ UID は /dev/bus/usb/... を直接 open できない。利用者がシステムの
// USB 許可画面で許可すると、アプリは同じ usbfs fd を UsbManager.openDevice() から得られる。
// このシムは open/openat の対象が usbfs ノードのときだけアプリ内ブローカーへ要求し、
// SCM_RIGHTS で届いた fd を呼び出し元へ返す。それ以外の open は raw openat syscall へ流す。
//
// musl/glibc のどちらへもロードするため libc 非依存 (-nostdlib)。getenv と errno の置き場だけ
// 実行先 libc の weak symbol を使い、socket/connect/write/recvmsg/openat/close/fcntl は生 syscall。

#include <fcntl.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>
#include <sys/un.h>

extern int *__errno_location(void) __attribute__((weak));
extern char *getenv(const char *) __attribute__((weak));

enum {
    Z2_NR_FCNTL = 25,
    Z2_NR_OPENAT = 56,
    Z2_NR_CLOSE = 57,
    Z2_NR_WRITE = 64,
    Z2_NR_SOCKET = 198,
    Z2_NR_CONNECT = 203,
    Z2_NR_RECVMSG = 212,
};

static long z2_syscall6(long nr, long a0, long a1, long a2, long a3, long a4, long a5) {
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x3 __asm__("x3") = a3;
    register long x4 __asm__("x4") = a4;
    register long x5 __asm__("x5") = a5;
    register long x8 __asm__("x8") = nr;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8)
                     : "memory", "cc");
    return x0;
}

static int z2_result(long value) {
    if (value < 0) {
        if (__errno_location) *__errno_location() = (int)-value;
        return -1;
    }
    return (int)value;
}

static size_t z2_strlen(const char *s) {
    size_t n = 0;
    if (!s) return 0;
    while (s[n]) n++;
    return n;
}

static int z2_usb_path(const char *path) {
    static const char prefix[] = "/dev/bus/usb/";
    size_t i = 0;
    if (!path) return 0;
    while (prefix[i]) {
        if (path[i] != prefix[i]) return 0;
        i++;
    }
    for (int n = 0; n < 3; n++, i++) if (path[i] < '0' || path[i] > '9') return 0;
    if (path[i++] != '/') return 0;
    for (int n = 0; n < 3; n++, i++) if (path[i] < '0' || path[i] > '9') return 0;
    return path[i] == '\0';
}

static void z2_close(int fd) {
    if (fd >= 0) (void)z2_syscall6(Z2_NR_CLOSE, fd, 0, 0, 0, 0, 0);
}

static int z2_write_all(int fd, const char *data, size_t len) {
    while (len) {
        long n = z2_syscall6(Z2_NR_WRITE, fd, (long)data, (long)len, 0, 0, 0);
        if (n < 0) return z2_result(n);
        if (n == 0) {
            if (__errno_location) *__errno_location() = 5; // EIO
            return -1;
        }
        data += n;
        len -= (size_t)n;
    }
    return 0;
}

static int z2_usb_open(const char *path, int flags) {
    const char *name = getenv ? getenv("Z2USB_SOCKET") : 0;
    const size_t name_len = z2_strlen(name);
    if (!name || name_len == 0 || name_len >= sizeof(((struct sockaddr_un *)0)->sun_path) - 1) {
        if (__errno_location) *__errno_location() = 38; // ENOSYS
        return -1;
    }

    int socket_fd = z2_result(z2_syscall6(
        Z2_NR_SOCKET, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, 0, 0, 0));
    if (socket_fd < 0) return -1;

    struct sockaddr_un address = {0};
    address.sun_family = AF_UNIX;
    // abstract namespace: sun_path[0] は NUL、名前は sun_path[1] から。末尾 NUL は長さに含めない。
    for (size_t i = 0; i < name_len; i++) address.sun_path[i + 1] = name[i];
    const size_t address_len = offsetof(struct sockaddr_un, sun_path) + 1 + name_len;
    long connected = z2_syscall6(
        Z2_NR_CONNECT, socket_fd, (long)&address, (long)address_len, 0, 0, 0);
    if (connected < 0) {
        z2_close(socket_fd);
        return z2_result(connected);
    }

    char request[384];
    static const char command[] = "OPEN ";
    const size_t path_len = z2_strlen(path);
    if (sizeof(command) - 1 + path_len + 1 > sizeof(request)) {
        z2_close(socket_fd);
        if (__errno_location) *__errno_location() = 36; // ENAMETOOLONG
        return -1;
    }
    size_t request_len = 0;
    for (size_t i = 0; i < sizeof(command) - 1; i++) request[request_len++] = command[i];
    for (size_t i = 0; i < path_len; i++) request[request_len++] = path[i];
    request[request_len++] = '\n';
    if (z2_write_all(socket_fd, request, request_len) != 0) {
        z2_close(socket_fd);
        return -1;
    }

    unsigned char status = 5; // EIO until a complete reply arrives
    struct iovec iov = {&status, sizeof(status)};
    union {
        struct cmsghdr align;
        unsigned char bytes[CMSG_SPACE(sizeof(int))];
    } control = {0};
    struct msghdr message = {0};
    message.msg_iov = &iov;
    message.msg_iovlen = 1;
    message.msg_control = control.bytes;
    message.msg_controllen = sizeof(control.bytes);
    long received = z2_syscall6(Z2_NR_RECVMSG, socket_fd, (long)&message, 0, 0, 0, 0);
    if (received < 0) {
        z2_close(socket_fd);
        return z2_result(received);
    }
    if (received != 1 || status != 0) {
        z2_close(socket_fd);
        if (__errno_location) *__errno_location() = status ? status : 5;
        return -1;
    }

    int usb_fd = -1;
    for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message);
         cmsg;
         cmsg = CMSG_NXTHDR(&message, cmsg)) {
        if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS &&
            cmsg->cmsg_len >= CMSG_LEN(sizeof(int))) {
            __builtin_memcpy(&usb_fd, CMSG_DATA(cmsg), sizeof(usb_fd));
            break;
        }
    }
    z2_close(socket_fd);
    if (usb_fd < 0) {
        if (__errno_location) *__errno_location() = 74; // EBADMSG
        return -1;
    }
    if (flags & O_CLOEXEC) {
        (void)z2_syscall6(Z2_NR_FCNTL, usb_fd, F_SETFD, FD_CLOEXEC, 0, 0, 0);
    }
    return usb_fd;
}

static int z2_openat_impl(int dirfd, const char *path, int flags, unsigned int mode) {
    if (z2_usb_path(path)) return z2_usb_open(path, flags);
    return z2_result(z2_syscall6(Z2_NR_OPENAT, dirfd, (long)path, flags, mode, 0, 0));
}

static int z2_needs_mode(int flags) {
    return (flags & O_CREAT) || ((flags & O_TMPFILE) == O_TMPFILE);
}

int open(const char *path, int flags, ...) {
    unsigned int mode = 0;
    if (z2_needs_mode(flags)) {
        va_list ap; va_start(ap, flags); mode = va_arg(ap, unsigned int); va_end(ap);
    }
    return z2_openat_impl(AT_FDCWD, path, flags, mode);
}

int open64(const char *path, int flags, ...) {
    unsigned int mode = 0;
    if (z2_needs_mode(flags)) {
        va_list ap; va_start(ap, flags); mode = va_arg(ap, unsigned int); va_end(ap);
    }
    return z2_openat_impl(AT_FDCWD, path, flags, mode);
}

int openat(int dirfd, const char *path, int flags, ...) {
    unsigned int mode = 0;
    if (z2_needs_mode(flags)) {
        va_list ap; va_start(ap, flags); mode = va_arg(ap, unsigned int); va_end(ap);
    }
    return z2_openat_impl(dirfd, path, flags, mode);
}

int openat64(int dirfd, const char *path, int flags, ...) {
    unsigned int mode = 0;
    if (z2_needs_mode(flags)) {
        va_list ap; va_start(ap, flags); mode = va_arg(ap, unsigned int); va_end(ap);
    }
    return z2_openat_impl(dirfd, path, flags, mode);
}

int __open_2(const char *path, int flags) { return z2_openat_impl(AT_FDCWD, path, flags, 0); }
int __open64_2(const char *path, int flags) { return z2_openat_impl(AT_FDCWD, path, flags, 0); }
int __openat_2(int dirfd, const char *path, int flags) { return z2_openat_impl(dirfd, path, flags, 0); }
int __openat64_2(int dirfd, const char *path, int flags) { return z2_openat_impl(dirfd, path, flags, 0); }
