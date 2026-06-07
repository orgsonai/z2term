// z2root — z2term 自前の最小 proot 互換エンジン (FOSS フェーズ2)
//
// 目的: proot(GPL-2.0) + talloc(LGPL-3.0) を、自分のコード(GPL-3.0)で置き換え、
//       外部バイナリの同梱・ライセンス表記をゼロ化する (docs/FOSS-PURE-HANDOFF.md §5)。
//
// 本ファイルは「長旅」の最初のスケルトン。ptrace(PTRACE_SYSCALL) で子プロセスの
// syscall を entry で傍受し、パス引数を rootfs ルート化 + bind 解決で書き換える。
// proot 互換の argv subset を受け、ProotLauncher の起動インターフェースをそのまま流用できる。
//
// 対応: aarch64 (arm64-v8a) のみ。Android API29+ の W^X 制約のため、本バイナリ自体は
//       nativeLibraryDir に lib*.so 名で同梱して execve する (proot と同じ前提)。
//
// 実装済: execve ローダ差し替え / getcwd 逆変換 / #! シバン解決(1段) /
//    canonicalize(パス内 symlink 解決 + . / .. 畳み) / cwd 相対パス絶対化(/proc/<pid>/cwd) /
//    dirfd 相対パスの非変換(*at の dirfd 委譲) / 2パス syscall(rename/link/symlink) /
//    相対 exec パス解決 / fakeroot(-0) uid-gid 偽装(get*id→0 / getgroups→0個 /
//    set*id・chown 失敗の成功偽装 / stat の uid-gid→0) /
//    link2symlink(linkat→symlinkat。Android FS の link() EACCES を symlink で回避) /
//    rootfs/bind.host の起動時 realpath 正規化(アプリの mount namespace は
//    /data/user/0/<pkg> を /data/data/<pkg> へ解決するため、chdir 後 getcwd が返す
//    canonical 形と bind.host(context.filesDir 由来の /data/user/0 形)が食い違い、
//    pwd / 相対 ls がホスト cwd を露出していた。両者を realpath で揃えて解消) /
//    /proc 偽装(fakeroot -0: /proc/<pid>/status の read を傍受し Uid:/Gid: 行を 0・
//    Groups: 行を空白・Cap{Prm,Eff,Bnd} を全 cap に書き換え、/proc/<pid>/loginuid を
//    0 に化かす。get*id syscall 偽装と一貫した root の見え方にする)。
// 残り難所(readlinkat 戻り値逆変換 / マルチスレッド境界の厳密化)は
//    TODO で明示。実機で小さく逐次検証して育てる。

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>      // siginfo_t / SIGTSTP 等 (ジョブ制御の group-stop 判定)
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/user.h>
#include <sys/socket.h>  // sockaddr / AF_UNIX (bind/connect の unix ソケットパス翻訳)
#include <sys/un.h>      // struct sockaddr_un
#include <sys/wait.h>
#include <sys/mman.h>    // mmap / mprotect (自前ローダ)
#include <sys/auxv.h>    // getauxval (自前ローダの auxv 構築)
#include <alloca.h>      // alloca (自前ローダの初期スタック構築)
#include <asm/ptrace.h>  // struct user_pt_regs (aarch64 uapi)
#include <linux/elf.h>   // NT_PRSTATUS
#include <sys/prctl.h>   // prctl (PR_SET_NO_NEW_PRIVS / PR_SET_SECCOMP)
#include <linux/seccomp.h>  // SECCOMP_MODE_FILTER / SECCOMP_RET_*
#include <linux/filter.h>   // struct sock_filter / sock_fprog (BPF)
#include <linux/audit.h>    // AUDIT_ARCH_AARCH64
#include <stddef.h>      // offsetof (seccomp_data フィールド)

#ifndef PTRACE_EVENT_SECCOMP
#define PTRACE_EVENT_SECCOMP 7
#endif
#ifndef PTRACE_O_TRACESECCOMP
#define PTRACE_O_TRACESECCOMP 0x00000080
#endif

extern char **environ;

// ---- 設定 (proot 互換 argv subset をパースして格納) --------------------------

#define MAX_BINDS 64
#define PATH_MAX_Z 4096

struct bind_entry {
    char host[PATH_MAX_Z];   // ホスト側の実パス (例: /dev, /proc, <filesDir>/shared_home)
    char guest[PATH_MAX_Z];  // ゲストから見えるパス (例: /dev, /proc, /root)
    size_t guest_len;
};

struct config {
    char rootfs[PATH_MAX_Z];          // -r <rootfs> (ゲストの "/" にマップされるホスト実パス)
    size_t rootfs_len;
    char cwd[PATH_MAX_Z];             // -w <dir>   (ゲスト視点の作業ディレクトリ)
    int fake_root;                    // -0
    int kill_on_exit;                 // --kill-on-exit
    int link2symlink;                 // --link2symlink (linkat→symlinkat エミュレート)
    struct bind_entry binds[MAX_BINDS];
    int nbinds;
    char *const *command;             // 残りの argv (ゲスト視点のコマンド + 引数)
    int use_loader;                   // 1: rootfs ELF を自前ローダ(--loader)経由で起動
    char self_path[PATH_MAX_Z];       // /proc/self/exe (nativeLibraryDir の libz2root.so)
    int readfree;                     // /proc 偽装を openat 時 temp 差し替えにし read/close をトレース対象から外す(高速化・既定 ON・Z2ROOT_NO_READFREE で無効化)
};

// ---- pid -> syscall entry/exit トグル の簡易マップ ----------------------------
// PTRACE_O_TRACESYSGOOD でも entry/exit の区別はトレーサ側で管理する必要がある。
// マルチスレッド(clone)では entry/exit がスレッド単位で交錯しうる。最小版は
// pid(tid) 単位で保持する。TODO: スレッド境界の厳密化。

#define MAP_CAP 256
#define STATUS_FD_MAX 16    // 同時に追跡する /proc 偽装 fd の上限(超過分は非追跡)
#define STATUS_BUF_MAX 16384 // /proc status/loginuid を読み込む temp 用バッファ上限(status は通常 ~2KB)
// proc 偽装 fd の種別(read 時にどの偽装を当てるか)。
#define PROC_FD_NONE     0
#define PROC_FD_STATUS   1  // /proc/.../status (Uid/Gid/Groups/Cap* を root 一貫に)
#define PROC_FD_LOGINUID 2  // /proc/.../loginuid (監査ログイン uid を 0 に)
struct pid_state {
    pid_t pid;
    int at_exit;            // 0: 次は syscall-entry, 1: 次は syscall-exit
    int used;
    int started;            // 0: まだ最初の停止(TRACEFORK 由来の初期 SIGSTOP)を消化していない
    long entry_nr;          // entry で記録した syscall 番号 (exit 時の戻り値逆変換用)
    unsigned long aux_addr; // getcwd 等の対象バッファアドレス
    int aux_kind;           // read entry で控えた追跡 fd の種別(PROC_FD_*, exit で偽装を分岐)
    int pending_open_kind;  // fakeroot: openat entry で偽装対象 proc パスを検出した種別(exit で fd を採取)
    int status_fds[STATUS_FD_MAX];      // fakeroot: 偽装対象 proc を指す fd 群, -1=空
    int status_fd_kind[STATUS_FD_MAX];  // 上記 fd の種別(PROC_FD_*), status_fds と添字対応
    int subst_active;       // readfree: openat で /proc を temp 差し替え中(exit で temp を unlink)
};
static struct pid_state g_map[MAP_CAP];

static struct pid_state *state_for(pid_t pid) {
    int free_slot = -1;
    for (int i = 0; i < MAP_CAP; i++) {
        if (g_map[i].used && g_map[i].pid == pid) return &g_map[i];
        if (!g_map[i].used && free_slot < 0) free_slot = i;
    }
    if (free_slot < 0) return NULL;  // TODO: 動的拡張
    g_map[free_slot].used = 1;
    g_map[free_slot].pid = pid;
    g_map[free_slot].at_exit = 0;
    g_map[free_slot].started = 0;
    g_map[free_slot].pending_open_kind = PROC_FD_NONE;
    g_map[free_slot].aux_kind = PROC_FD_NONE;
    g_map[free_slot].subst_active = 0;
    for (int k = 0; k < STATUS_FD_MAX; k++) {
        g_map[free_slot].status_fds[k] = -1;
        g_map[free_slot].status_fd_kind[k] = PROC_FD_NONE;
    }
    return &g_map[free_slot];
}

static void state_drop(pid_t pid) {
    for (int i = 0; i < MAP_CAP; i++) {
        if (g_map[i].used && g_map[i].pid == pid) { g_map[i].used = 0; return; }
    }
}

// ---- トレーシ メモリ I/O -------------------------------------------------------

static ssize_t read_tracee_str(pid_t pid, unsigned long addr, char *buf, size_t cap) {
    // process_vm_readv はページ境界で短く読めるので、null 終端が来るまでチャンク読み。
    size_t off = 0;
    while (off < cap - 1) {
        size_t want = cap - 1 - off;
        if (want > 256) want = 256;
        struct iovec local = { buf + off, want };
        struct iovec remote = { (void *)(addr + off), want };
        ssize_t n = process_vm_readv(pid, &local, 1, &remote, 1, 0);
        if (n <= 0) {
            if (off == 0) return -1;
            break;
        }
        for (ssize_t i = 0; i < n; i++) {
            if (buf[off + i] == '\0') { return (ssize_t)(off + i); }
        }
        off += n;
    }
    buf[off] = '\0';
    return (ssize_t)off;
}

static int write_tracee_mem(pid_t pid, unsigned long addr, const void *buf, size_t len) {
    struct iovec local = { (void *)buf, len };
    struct iovec remote = { (void *)addr, len };
    ssize_t n = process_vm_writev(pid, &local, 1, &remote, 1, 0);
    return (n == (ssize_t)len) ? 0 : -1;
}

// ---- レジスタ取得/設定 (aarch64) ----------------------------------------------

static int get_regs(pid_t pid, struct user_pt_regs *regs) {
    struct iovec iov = { regs, sizeof(*regs) };
    return ptrace(PTRACE_GETREGSET, pid, (void *)NT_PRSTATUS, &iov);
}
static int set_regs(pid_t pid, struct user_pt_regs *regs) {
    struct iovec iov = { regs, sizeof(*regs) };
    return ptrace(PTRACE_SETREGSET, pid, (void *)NT_PRSTATUS, &iov);
}

#ifndef NT_ARM_SYSTEM_CALL
#define NT_ARM_SYSTEM_CALL 0x404
#endif
// syscall-entry で実際に dispatch される syscall 番号を変更する(aarch64)。
// 注意: aarch64 では regs[8] を書き換えるだけでは dispatch 先は変わらない。
// NT_ARM_SYSTEM_CALL regset を書く必要がある(link2symlink で linkat→symlinkat に化かす)。
static void set_syscall_nr(pid_t pid, int nr) {
    struct iovec iov = { &nr, sizeof(nr) };
    ptrace(PTRACE_SETREGSET, pid, (void *)NT_ARM_SYSTEM_CALL, &iov);
}

// ---- パス変換 ------------------------------------------------------------------

// 絶対ゲストパス guest_path を、ホスト実パス out へ変換する。
//  1) bind に一致(完全 or 配下)すれば bind.host + 残り。
//  2) それ以外は rootfs + guest_path。
// 既に rootfs/bind.host 配下を指している場合(二重変換)は false を返して書き換え抑止。
// 戻り値: 変換して書き換えるべきなら 1、不要なら 0。
static int translate_abs(const struct config *cfg, const char *guest_path, char *out, size_t cap) {
    if (guest_path[0] != '/') return 0;  // 相対パスは最小版では非対象 (TODO)

    // 二重変換防止: 既にホスト rootfs を指していれば触らない。
    if (strncmp(guest_path, cfg->rootfs, cfg->rootfs_len) == 0) return 0;

    // bind 優先。長い guest_len から照合したいが最小版は登録順で十分。
    for (int i = 0; i < cfg->nbinds; i++) {
        const struct bind_entry *b = &cfg->binds[i];
        if (strncmp(guest_path, b->guest, b->guest_len) == 0 &&
            (guest_path[b->guest_len] == '/' || guest_path[b->guest_len] == '\0')) {
            snprintf(out, cap, "%s%s", b->host, guest_path + b->guest_len);
            return 1;
        }
    }
    snprintf(out, cap, "%s%s", cfg->rootfs, guest_path);
    return 1;
}

// ホスト実パス host を、ゲスト視点の絶対パスへ逆変換して buf へ。
//  1) bind.host 配下 → bind.guest + 残り。 2) rootfs 配下 → 残り。
//  3) いずれでもない → そのまま(ホスト=ゲストとみなす)。戻り値は buf。
static const char *host_to_guest(const struct config *cfg, const char *host,
                                 char *buf, size_t cap) {
    for (int i = 0; i < cfg->nbinds; i++) {
        const struct bind_entry *b = &cfg->binds[i];
        size_t hl = strlen(b->host);
        if (strncmp(host, b->host, hl) == 0 && (host[hl] == '/' || host[hl] == '\0')) {
            snprintf(buf, cap, "%s%s", b->guest, host + hl);
            return buf;
        }
    }
    if (strncmp(host, cfg->rootfs, cfg->rootfs_len) == 0 &&
        (host[cfg->rootfs_len] == '/' || host[cfg->rootfs_len] == '\0')) {
        const char *g = host + cfg->rootfs_len;
        snprintf(buf, cap, "%s", g[0] ? g : "/");
        return buf;
    }
    snprintf(buf, cap, "%s", host);
    return buf;
}

// ゲスト絶対パス in を、proot 相当に「正規化(. / .. を畳む)＋パス内 symlink を
// rootfs 内で逐次解決」して、ゲスト絶対パス out(symlink を含まない)を返す。
// deref_final=1 なら最終コンポーネントの symlink も辿る(open/stat 等)。0 なら
// 最終要素はそのまま残す(lstat/unlinkat/readlinkat/新規作成名など)。
// これを通すと openat 等に渡るホストパスが symlink を一切含まなくなり、絶対
// symlink がホスト "/" を指して ENOENT になる問題(Alpine の /bin 等)を防ぐ。
static void canonicalize_guest(const struct config *cfg, pid_t pid, const char *in,
                               int deref_final, char *out, size_t cap) {
    char result[PATH_MAX_Z];   // 構築中のゲスト絶対パス(末尾 "/" 無し。"" は "/")
    char pending[PATH_MAX_Z];  // 未処理の残りパス
    result[0] = '\0';
    snprintf(pending, sizeof(pending), "%s", in);

    size_t pi = 0;
    while (pending[pi] == '/') pi++;
    int guard = 0;
    while (pending[pi] != '\0') {
        if (++guard > 512) break;  // symlink ループ保護

        char comp[PATH_MAX_Z];
        size_t ci = 0;
        while (pending[pi] != '/' && pending[pi] != '\0' && ci < sizeof(comp) - 1)
            comp[ci++] = pending[pi++];
        comp[ci] = '\0';
        while (pending[pi] == '/') pi++;
        int is_last = (pending[pi] == '\0');

        if (comp[0] == '\0' || strcmp(comp, ".") == 0) continue;
        if (strcmp(comp, "..") == 0) {
            char *s = strrchr(result, '/');
            if (s) *s = '\0'; else result[0] = '\0';
            continue;
        }

        char cand[PATH_MAX_Z];
        snprintf(cand, sizeof(cand), "%s/%s", result, comp);

        // /proc/self・/proc/thread-self の magic symlink は、トレーサ(z2root 親)が
        // readlink すると「トレーサ自身の pid」へ化ける。host_path_for は先頭の
        // /proc/self... だけ tracee へ書き換えるが、間接 symlink(/proc/net→self/net 等)
        // で walk 途中に現れる self/thread-self は素通りしていた(EACCES の原因)。
        // ここで tracee の pid へ明示解決する(プロセス/スレッドで fd・cwd・root 共有)。
        if ((!is_last || deref_final) && strcmp(result, "/proc") == 0 &&
            (strcmp(comp, "self") == 0 || strcmp(comp, "thread-self") == 0)) {
            char rest[PATH_MAX_Z];
            snprintf(rest, sizeof(rest), "%s", pending + pi);
            result[0] = '\0';
            if (rest[0]) snprintf(pending, sizeof(pending), "/proc/%d/%s", (int)pid, rest);
            else snprintf(pending, sizeof(pending), "/proc/%d", (int)pid);
            pi = 0;
            while (pending[pi] == '/') pi++;
            continue;
        }

        if (!is_last || deref_final) {
            char host[PATH_MAX_Z];
            if (translate_abs(cfg, cand, host, sizeof(host))) {
                char link[PATH_MAX_Z];
                ssize_t ln = readlink(host, link, sizeof(link) - 1);
                if (ln >= 0) {
                    link[ln] = '\0';
                    char rest[PATH_MAX_Z];
                    snprintf(rest, sizeof(rest), "%s", pending + pi);
                    // 絶対 symlink: ルートから。相対 symlink: 親(=現 result)基準。
                    if (link[0] == '/') result[0] = '\0';
                    if (rest[0])
                        snprintf(pending, sizeof(pending), "%s/%s", link, rest);
                    else
                        snprintf(pending, sizeof(pending), "%s", link);
                    pi = 0;
                    while (pending[pi] == '/') pi++;
                    continue;
                }
            }
        }
        snprintf(result, sizeof(result), "%s", cand);
    }
    if (result[0] == '\0') snprintf(out, cap, "/");
    else snprintf(out, cap, "%s", result);
}

// tracee の syscall パス引数(絶対 or cwd 相対)を、symlink 解決込みのホスト実パスへ。
// 相対パスは /proc/<tid>/cwd(ホスト実パス)をゲスト cwd へ逆変換して絶対化する。
// 戻り値: 0=変換した(host_out 有効), -1=変換不要/不可(既に rootfs 配下/空 等)。
// dirfd: パスの基準となる *at の dirfd(無い syscall は AT_FDCWD を渡す)。
// 相対パスは dirfd==AT_FDCWD のときだけ cwd 基準で絶対化する。実 fd 基準
// (dirfd != AT_FDCWD)の相対パスは触らない(絶対化すると dirfd が無視され壊れる)。
static int host_path_for(const struct config *cfg, pid_t pid, const char *in_path,
                         int deref_final, long dirfd, char *host_out, size_t cap) {
    if (in_path[0] == '\0') return -1;
    // 自前で書いた scratch(既にホスト rootfs 配下)を二重変換しない。
    if (strncmp(in_path, cfg->rootfs, cfg->rootfs_len) == 0) return -1;

    char guest_abs[PATH_MAX_Z];
    if (in_path[0] == '/') {
        // /proc/self・/proc/thread-self を tracee の pid へ展開する。これらの magic
        // symlink を canonicalize_guest がそのまま readlink するとトレーサ(z2root 親)
        // 自身の pid に解決され、/proc/self/fd/N が tracee でなくトレーサの fd を指す。
        // 結果 musl の ttyname()(openpty の名前解決)が壊れ、dropbear の SSH PTY
        // セッションが "ttyname fails for openpty device" で即切断する。スレッドと
        // プロセスは fd/cwd/root テーブルを共有するので self/thread-self とも tid で可。
        const char *tail = NULL;
        if (strncmp(in_path, "/proc/self", 10) == 0 &&
            (in_path[10] == '/' || in_path[10] == '\0'))
            tail = in_path + 10;
        else if (strncmp(in_path, "/proc/thread-self", 17) == 0 &&
                 (in_path[17] == '/' || in_path[17] == '\0'))
            tail = in_path + 17;
        if (tail) snprintf(guest_abs, sizeof(guest_abs), "/proc/%d%s", (int)pid, tail);
        else snprintf(guest_abs, sizeof(guest_abs), "%s", in_path);
    } else {
        if (dirfd != AT_FDCWD) return -1;  // fd 相対は dirfd に委ねる
        char proc[64], host_cwd[PATH_MAX_Z];
        snprintf(proc, sizeof(proc), "/proc/%d/cwd", (int)pid);
        ssize_t n = readlink(proc, host_cwd, sizeof(host_cwd) - 1);
        if (n < 0) return -1;
        host_cwd[n] = '\0';
        char guest_cwd[PATH_MAX_Z];
        host_to_guest(cfg, host_cwd, guest_cwd, sizeof(guest_cwd));
        snprintf(guest_abs, sizeof(guest_abs), "%s/%s", guest_cwd, in_path);
    }

    char resolved[PATH_MAX_Z];
    canonicalize_guest(cfg, pid, guest_abs, deref_final, resolved, sizeof(resolved));
    if (!translate_abs(cfg, resolved, host_out, cap))
        snprintf(host_out, cap, "%s", resolved);
    return 0;
}

// ---- execve ローダ差し替え (FOSS フェーズ2 §5-2(b)) ----------------------------
// 動的 ELF はカーネルが PT_INTERP(動的ローダ)を「ホストの /」から解決するため、
// rootfs 内の musl/glibc ローダが見つからず ENOENT になる。proot と同様に
//   execve(<rootfs のローダ>, ["--argv0", <元argv0>, <実プログラム>, <元args...>])
// へ書き換えてローダを明示起動する。これで rootfs 内の動的バイナリが動く。

#define MAX_ARGS 256

// host_path の ELF を読み、PT_INTERP(ゲスト視点の絶対パス文字列)を interp へ。
// 戻り値: 1=動的(interp 有), 0=非動的/非ELF(静的・スクリプト等), -1=読めない。
static int read_elf_interp(const char *host_path, char *interp, size_t cap) {
    int fd = open(host_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    unsigned char e[64];
    if (read(fd, e, sizeof(e)) != (ssize_t)sizeof(e)) { close(fd); return -1; }
    if (memcmp(e, "\x7f""ELF", 4) != 0) { close(fd); return 0; }  // 非ELF
    // 64bit little-endian (aarch64) 前提。
    unsigned long phoff;
    unsigned short phentsize, phnum;
    memcpy(&phoff, e + 0x20, 8);
    memcpy(&phentsize, e + 0x36, 2);
    memcpy(&phnum, e + 0x38, 2);
    for (unsigned short i = 0; i < phnum; i++) {
        unsigned char ph[56];
        if (pread(fd, ph, sizeof(ph), (off_t)(phoff + (unsigned long)i * phentsize)) != (ssize_t)sizeof(ph))
            break;
        unsigned int p_type;
        memcpy(&p_type, ph, 4);
        if (p_type == 3 /* PT_INTERP */) {
            unsigned long p_offset, p_filesz;
            memcpy(&p_offset, ph + 8, 8);
            memcpy(&p_filesz, ph + 32, 8);
            if (p_filesz == 0 || p_filesz >= cap) break;
            if (pread(fd, interp, p_filesz, (off_t)p_offset) != (ssize_t)p_filesz) break;
            interp[p_filesz] = '\0';
            close(fd);
            return 1;
        }
    }
    close(fd);
    return 0;
}

// ゲスト視点の symlink を rootfs 内で辿り、最終的なゲスト絶対パスを out へ。
// (Alpine の /bin/sh -> /bin/busybox 等。絶対 symlink 中心。相対 symlink の
//  ".." 正規化は最小版では省略 = TODO。)
static void resolve_guest_symlink(const struct config *cfg, const char *guest_in,
                                  char *out, size_t cap) {
    char cur[PATH_MAX_Z];
    snprintf(cur, sizeof(cur), "%s", guest_in);
    for (int depth = 0; depth < 16; depth++) {
        char host[PATH_MAX_Z];
        if (!translate_abs(cfg, cur, host, sizeof(host))) break;
        char link[PATH_MAX_Z];
        ssize_t ln = readlink(host, link, sizeof(link) - 1);
        if (ln < 0) break;  // symlink でない or 解決完了
        link[ln] = '\0';
        if (link[0] == '/') {
            snprintf(cur, sizeof(cur), "%s", link);
        } else {
            char dir[PATH_MAX_Z];
            snprintf(dir, sizeof(dir), "%s", cur);
            char *slash = strrchr(dir, '/');
            if (slash) slash[1] = '\0'; else { dir[0] = '/'; dir[1] = '\0'; }
            char tmp[PATH_MAX_Z];
            snprintf(tmp, sizeof(tmp), "%s%s", dir, link);
            snprintf(cur, sizeof(cur), "%s", tmp);
        }
    }
    snprintf(out, cap, "%s", cur);
}

// syscall のパス引数記述子。aarch64 は open/stat/access の素の形を持たず *at が中心。
// x0=arg0 ... x5=arg5。2 パス syscall(rename/link/symlink)は最大 2 要素。
//   idx      : パス文字列を指すレジスタ index
//   dirfd_reg: そのパスの基準 dirfd レジスタ index(-1=dirfd 無し=常に cwd 基準)
//   deref    : 最終コンポーネントの symlink を辿るか(1=follow, 0=最終はそのまま)
//   flag_reg : AT_SYMLINK_* で deref が動的に決まる場合のフラグレジスタ index(無ければ -1)
//   flag_follow_bit / flag_nofollow_bit: 立っていれば follow/非follow に倒すビット
struct path_arg { int idx; int dirfd_reg; int deref; int flag_reg; int flag_follow_bit; int flag_nofollow_bit; };
struct sc_paths { struct path_arg a[2]; int n; };

// nr のパス引数記述を out へ。対象外なら 0、対象なら 1。
static int syscall_paths(long nr, struct sc_paths *out) {
    out->n = 0;
    struct path_arg *p = out->a;
    switch (nr) {
        case 56:  case 437: p[out->n++] = (struct path_arg){1, 0, 1, -1, 0, 0}; break;    // openat/openat2
        case 79:  p[out->n++] = (struct path_arg){1, 0, 1, 3, 0, 0x100}; break;           // newfstatat (flags arg3)
        case 291: p[out->n++] = (struct path_arg){1, 0, 1, 2, 0, 0x100}; break;           // statx (flags arg2)
        case 48:  case 439: p[out->n++] = (struct path_arg){1, 0, 1, 3, 0, 0x100}; break; // faccessat/2 (flags arg3)
        case 78:  p[out->n++] = (struct path_arg){1, 0, 0, -1, 0, 0}; break;              // readlinkat (最終は辿らない)
        case 35:  p[out->n++] = (struct path_arg){1, 0, 0, -1, 0, 0}; break;              // unlinkat
        case 34:  p[out->n++] = (struct path_arg){1, 0, 0, -1, 0, 0}; break;              // mkdirat (新規名)
        case 33:  p[out->n++] = (struct path_arg){1, 0, 0, -1, 0, 0}; break;              // mknodat (新規名)
        case 53:  p[out->n++] = (struct path_arg){1, 0, 1, 3, 0, 0x100}; break;           // fchmodat (flags arg3)
        case 54:  p[out->n++] = (struct path_arg){1, 0, 1, 4, 0, 0x100}; break;           // fchownat (flags arg4)
        case 88:  p[out->n++] = (struct path_arg){1, 0, 1, 3, 0, 0x100}; break;           // utimensat (path arg1, flags arg3。dpkg の mtime 設定)
        case 49:  p[out->n++] = (struct path_arg){0, -1, 1, -1, 0, 0}; break;             // chdir (dirfd 無し)
        case 43:  p[out->n++] = (struct path_arg){0, -1, 1, -1, 0, 0}; break;             // statfs (dirfd 無し)
        case 36:  p[out->n++] = (struct path_arg){2, 1, 0, -1, 0, 0}; break;              // symlinkat (linkpath arg2, dirfd arg1。target は非変換)
        case 37:  // linkat(olddir, old, newdir, new, flags) flags&AT_SYMLINK_FOLLOW(0x400)
            p[out->n++] = (struct path_arg){1, 0, 0, 4, 0x400, 0};
            p[out->n++] = (struct path_arg){3, 2, 0, -1, 0, 0};
            break;
        case 38:  case 276:  // renameat / renameat2 (olddir, old, newdir, new[, flags])
            p[out->n++] = (struct path_arg){1, 0, 0, -1, 0, 0};
            p[out->n++] = (struct path_arg){3, 2, 0, -1, 0, 0};
            break;
        default: return 0;
    }
    return 1;
}

// syscall-entry で、必要ならパス引数をホスト実パスへ書き換える。
// 書き換えたパス文字列はトレーシのスタック下(sp - SCRATCH)へ置き、該当レジスタを差し替える。
// レッドゾーン確保のため sp から十分下げた所をスクラッチ基点にする。
#define SCRATCH_OFFSET 2048

// host_path 先頭を読み、"#!" スクリプトならシバン行のインタプリタ(ゲスト視点絶対パス)を
// interp へ、オプション引数(あれば 1 個)を arg へ。
// 戻り値: 1=スクリプト(interp 有), 0=非スクリプト, -1=読めない。
static int read_script_shebang(const char *host_path, char *interp, size_t icap,
                               char *arg, size_t acap) {
    int fd = open(host_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char line[256];
    ssize_t n = read(fd, line, sizeof(line) - 1);
    close(fd);
    if (n < 2 || line[0] != '#' || line[1] != '!') return 0;
    line[n] = '\0';
    char *nl = memchr(line, '\n', (size_t)n);
    if (nl) *nl = '\0';
    char *p = line + 2;
    while (*p == ' ' || *p == '\t') p++;
    char *istart = p;
    while (*p && *p != ' ' && *p != '\t') p++;
    if (p == istart) return 0;
    char saved = *p;
    *p = '\0';
    snprintf(interp, icap, "%s", istart);
    arg[0] = '\0';
    if (saved) {
        p++;
        while (*p == ' ' || *p == '\t') p++;
        if (*p) {
            char *end = p + strlen(p);
            while (end > p && (end[-1] == ' ' || end[-1] == '\t')) *--end = '\0';
            snprintf(arg, acap, "%s", p);
        }
    }
    return 1;
}

// 実行計画: ゲストのプログラムを実際に exec するための最終形を組み立てる。
// 動的 ELF(ローダ経由) / 静的 ELF / #! スクリプト(1段) を統一して扱う。
//   target     : execve に渡すホスト実パス
//   prefix[]   : 合成した先頭 argv 群 (argv0 含む)
//   orig_start : 元 argv のこの index 以降を prefix の後ろに連結する
// 最終 argv = prefix[0..nprefix-1] + orig_argv[orig_start..]
#define PLAN_MAX_PREFIX 12   // ローダ包み(["z2root","--loader",<elf>])で先頭に +3 するため余裕を持つ
struct exec_plan {
    char target[PATH_MAX_Z];
    char prefix[PLAN_MAX_PREFIX][PATH_MAX_Z];
    int nprefix;
    int orig_start;
};

static void plan_push(struct exec_plan *plan, const char *s) {
    if (plan->nprefix < PLAN_MAX_PREFIX)
        snprintf(plan->prefix[plan->nprefix++], PATH_MAX_Z, "%s", s);
}

// use_loader 時: 実際に exec する rootfs ELF を、execve せず nativeLibraryDir 常駐の
// 自前ローダ(libz2root.so の --loader モード)経由で起動するよう plan を包む。
// Android の untrusted_app は app data 領域のファイルを execve できない(W^X)ため、
// 許可されている nativeLibraryDir の libz2root.so だけを execve し、その中で対象 ELF を
// 匿名 PROT_EXEC メモリへ手動マップして jump する(proot の libproot_loader 相当)。
//   変換: target=<elf>, prefix=[a0,a1,...]
//      →  target=<self libz2root.so>, prefix=["z2root","--loader",<elf>, a0,a1,...]
static void wrap_with_loader(const struct config *cfg, struct exec_plan *plan) {
    if (!cfg->use_loader || cfg->self_path[0] == '\0') return;

    char elf[PATH_MAX_Z];
    snprintf(elf, sizeof(elf), "%s", plan->target);

    char saved[PLAN_MAX_PREFIX][PATH_MAX_Z];
    int saved_n = plan->nprefix;
    if (saved_n > PLAN_MAX_PREFIX) saved_n = PLAN_MAX_PREFIX;
    for (int i = 0; i < saved_n; i++)
        snprintf(saved[i], PATH_MAX_Z, "%s", plan->prefix[i]);

    plan->nprefix = 0;
    plan_push(plan, "z2root");      // ローダの argv0 (中身不問)
    plan_push(plan, "--loader");
    plan_push(plan, elf);           // 匿名メモリへマップして jump する ELF
    for (int i = 0; i < saved_n; i++)
        plan_push(plan, saved[i]);  // マップ先 ELF へ渡す argv (argv0 含む)

    snprintf(plan->target, sizeof(plan->target), "%s", cfg->self_path);
}

// host_prog 先頭が ELF マジックか。開けない(存在しない PATH 候補)/非ELF なら 0。
// loader 包みするか「素の execve でカーネルに任せるか」を分ける判定に使う。
static int file_is_elf(const char *path) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    unsigned char m[4];
    ssize_t r = read(fd, m, sizeof(m));
    close(fd);
    return (r == 4 && m[0] == 0x7f && m[1] == 'E' && m[2] == 'L' && m[3] == 'F');
}

// guest_prog を resolve し host 実パスへ。orig_argv0 は ELF バイナリ起動時の argv0。
// pid は相対 exec パスを /proc/<pid>/cwd で絶対化するため(run_child は自身の pid)。
// 戻り値: 0 = loader 包み済み(plan->target/prefix 有効) / 1 = passthrough
//   (host_prog が非ELF。loader を噛ませず素の execve をカーネルに通し、
//    ENOENT/ENOEXEC を呼び出し側 execvp に返させる。plan->target に host_prog のみ)。
static int plan_exec(const struct config *cfg, pid_t pid, const char *guest_prog,
                     const char *orig_argv0, struct exec_plan *plan) {
    plan->nprefix = 0;
    plan->orig_start = 1;  // 元 argv0 は prefix で置換するため常に [1..] を連結

    // 相対 exec パスも /proc/<pid>/cwd で絶対化し、symlink 解決込みの host 実パスへ。
    char host_prog[PATH_MAX_Z];
    if (host_path_for(cfg, pid, guest_prog, 1, AT_FDCWD, host_prog, sizeof(host_prog)) != 0) {
        char real_guest[PATH_MAX_Z];
        resolve_guest_symlink(cfg, guest_prog, real_guest, sizeof(real_guest));
        if (!translate_abs(cfg, real_guest, host_prog, sizeof(host_prog)))
            snprintf(host_prog, sizeof(host_prog), "%s", real_guest);
    }

    // 1) #! スクリプト: シバンのインタプリタを起動し、スクリプトを引数に渡す。
    char sb_interp[PATH_MAX_Z], sb_arg[PATH_MAX_Z];
    if (read_script_shebang(host_prog, sb_interp, sizeof(sb_interp),
                            sb_arg, sizeof(sb_arg)) == 1) {
        char interp_real[PATH_MAX_Z];
        resolve_guest_symlink(cfg, sb_interp, interp_real, sizeof(interp_real));
        char host_interp[PATH_MAX_Z];
        if (!translate_abs(cfg, interp_real, host_interp, sizeof(host_interp)))
            snprintf(host_interp, sizeof(host_interp), "%s", interp_real);
        char interp_loader[PATH_MAX_Z];
        int idyn = read_elf_interp(host_interp, interp_loader, sizeof(interp_loader));
        if (idyn == 1) {
            char host_loader[PATH_MAX_Z];
            if (!translate_abs(cfg, interp_loader, host_loader, sizeof(host_loader)))
                snprintf(host_loader, sizeof(host_loader), "%s", interp_loader);
            snprintf(plan->target, sizeof(plan->target), "%s", host_loader);
            plan_push(plan, host_loader);
            plan_push(plan, "--argv0");
            plan_push(plan, sb_interp);   // インタプリタの argv0 はシバン表記どおり
            plan_push(plan, host_interp);
        } else {
            snprintf(plan->target, sizeof(plan->target), "%s", host_interp);
            plan_push(plan, sb_interp);   // argv0
        }
        if (sb_arg[0]) plan_push(plan, sb_arg);
        // スクリプトパスは元のゲストパスを渡す(open 時に変換される / $0 もゲスト表記)。
        plan_push(plan, guest_prog);
        wrap_with_loader(cfg, plan);
        return 0;
    }

    // 2) 動的 ELF: rootfs 内のローダ経由で起動。
    char interp[PATH_MAX_Z];
    if (read_elf_interp(host_prog, interp, sizeof(interp)) == 1) {
        char host_loader[PATH_MAX_Z];
        if (!translate_abs(cfg, interp, host_loader, sizeof(host_loader)))
            snprintf(host_loader, sizeof(host_loader), "%s", interp);
        snprintf(plan->target, sizeof(plan->target), "%s", host_loader);
        plan_push(plan, host_loader);
        plan_push(plan, "--argv0");
        plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
        // ld.so が開く実プログラムは「ゲストパス」を渡す。ld.so の open() は
        // tracee として傍受・翻訳されるため、host_prog(=ホスト実パス)を渡すと
        // bind 配下(例: -b <home>:/root の /root/a.out)で「ゲストパス扱い→rootfs
        // 前置」され ENOENT になる(rootfs 配下のみ二重変換抑止で偶然動いていた)。
        // host_prog をゲスト視点へ逆変換して渡せば rootfs/bind の両方で正しく開ける。
        char guest_real[PATH_MAX_Z];
        host_to_guest(cfg, host_prog, guest_real, sizeof(guest_real));
        plan_push(plan, guest_real);
        wrap_with_loader(cfg, plan);
        return 0;
    }

    // host_prog が ELF でない(開けない=存在しない PATH 候補 / 非実行ファイル)なら
    // loader を噛ませない。execvp は PATH を順に execve して ENOENT なら次候補へ進むが、
    // loader 包みすると「execve 成功 → loader が open 失敗で _exit(127)」となり子が 127 で
    // 即死し、呼び出し側(dpkg の execvp 等)が次候補を試せず失敗する。素の execve を通して
    // カーネルに ENOENT/ENOEXEC を返させる(rewrite_execve 側で path だけ host へ変換)。
    if (!file_is_elf(host_prog)) {
        snprintf(plan->target, sizeof(plan->target), "%s", host_prog);
        return 1;
    }

    // 3) 静的 ELF / その他: パスだけ host へ、argv0 はそのまま。
    snprintf(plan->target, sizeof(plan->target), "%s", host_prog);
    plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
    wrap_with_loader(cfg, plan);
    return 0;
}

// tracee の execve/execveat を傍受し、ローダ差し替え / シバン解決を適用する。
// path_idx: パス引数のレジスタ index (execve=0, execveat=1)。argv は +1, envp は +2。
static void rewrite_execve(const struct config *cfg, pid_t pid,
                           struct user_pt_regs *regs, int path_idx) {
    unsigned long path_addr = regs->regs[path_idx];
    if (path_addr == 0) return;

    char guest_prog[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, guest_prog, sizeof(guest_prog)) < 0) return;

    // 元 argv を tracee から読む。
    unsigned long argv_addr = regs->regs[path_idx + 1];
    char *args[MAX_ARGS];
    int n = 0;
    if (argv_addr) {
        for (; n < MAX_ARGS; n++) {
            unsigned long p = 0;
            struct iovec lo = { &p, 8 };
            struct iovec re = { (void *)(argv_addr + (unsigned long)n * 8), 8 };
            if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != 8) break;
            if (p == 0) break;
            char *s = malloc(PATH_MAX_Z);
            if (!s) break;
            if (read_tracee_str(pid, p, s, PATH_MAX_Z) < 0) s[0] = '\0';
            args[n] = s;
        }
    }

    struct exec_plan plan;
    int rc = plan_exec(cfg, pid, guest_prog, (n > 0) ? args[0] : guest_prog, &plan);

    if (rc == 1) {
        // passthrough: loader を噛ませず path レジスタを host パスへ変換するだけ。
        // 非ELF/存在しないパスはカーネルが ENOENT/ENOEXEC を返し、execvp が次候補へ進める。
        size_t plen = strlen(plan.target) + 1;
        unsigned long base = (regs->sp - SCRATCH_OFFSET - plen) & ~15UL;
        if (write_tracee_mem(pid, base, plan.target, plen) == 0) {
            regs->regs[path_idx] = base;
            set_regs(pid, regs);
        }
        for (int j = 0; j < n; j++) free(args[j]);
        return;
    }

    // 最終 argv = plan.prefix[..] + args[plan.orig_start..]
    const char *parts[MAX_ARGS + PLAN_MAX_PREFIX];
    int pc = 0;
    for (int j = 0; j < plan.nprefix && pc < (int)(MAX_ARGS + PLAN_MAX_PREFIX); j++)
        parts[pc++] = plan.prefix[j];
    for (int j = plan.orig_start; j < n && pc < (int)(MAX_ARGS + PLAN_MAX_PREFIX); j++)
        parts[pc++] = args[j];

    // tracee スタック下に [target 文字列][argv blob][8B align][ポインタ配列 + NULL] を配置。
    size_t target_len = strlen(plan.target) + 1;
    size_t blob_sz = 0;
    for (int j = 0; j < pc; j++) blob_sz += strlen(parts[j]) + 1;
    size_t total = target_len + blob_sz + 8 + (size_t)(pc + 1) * 8;
    unsigned long base = (regs->sp - SCRATCH_OFFSET - total) & ~15UL;

    char blob[8192];
    if (blob_sz <= sizeof(blob)) {
        unsigned long blob_base = base + target_len;
        unsigned long ptrs[MAX_ARGS + PLAN_MAX_PREFIX];
        size_t boff = 0;
        for (int j = 0; j < pc; j++) {
            size_t l = strlen(parts[j]) + 1;
            memcpy(blob + boff, parts[j], l);
            ptrs[j] = blob_base + boff;
            boff += l;
        }
        unsigned long arr = (blob_base + boff + 7) & ~7UL;
        unsigned long nullp = 0;
        int ok = (write_tracee_mem(pid, base, plan.target, target_len) == 0);
        if (ok) ok = (write_tracee_mem(pid, blob_base, blob, boff) == 0);
        for (int j = 0; j < pc && ok; j++)
            ok = (write_tracee_mem(pid, arr + (unsigned long)j * 8, &ptrs[j], 8) == 0);
        if (ok) ok = (write_tracee_mem(pid, arr + (unsigned long)pc * 8, &nullp, 8) == 0);
        if (ok) {
            regs->regs[path_idx] = base;
            regs->regs[path_idx + 1] = arr;
            set_regs(pid, regs);
        }
    }

    for (int j = 0; j < n; j++) free(args[j]);
}

// syscall-exit で getcwd(17) の戻りバッファに入ったホスト実パスをゲストパスへ逆変換する。
// (未実装だと cwd がホストパス /<rootfs>/... や bind の host パスを露出し、$PWD やプロンプトが壊れる)
// rootfs 配下だけでなく bind 配下(例: -b <host home>:/root)も host_to_guest() で逆変換する。
static void rewrite_getcwd_result(const struct config *cfg, pid_t pid, unsigned long buf) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (ret <= 0) return;  // getcwd 失敗

    char host[PATH_MAX_Z];
    if (read_tracee_str(pid, buf, host, sizeof(host)) < 0) return;

    char g[PATH_MAX_Z];
    host_to_guest(cfg, host, g, sizeof(g));
    if (strcmp(g, host) == 0) return;  // bind/rootfs いずれにも該当せず=変換不要

    size_t len = strlen(g) + 1;
    if (write_tracee_mem(pid, buf, g, len) == 0) {
        regs.regs[0] = len;  // getcwd は書き込みバイト数(NUL含む)を返す
        set_regs(pid, &regs);
    }
}

// [DEBUG] Z2ROOT_TRACE がパス(先頭 '/')ならそのファイル(追記)へ、それ以外(例 "1")は
// stderr=PTY へトレースを出す。SSH PTY reset 調査用。sentinel が無ければ env 自体が来ない。
static FILE *g_trc = NULL;
static int  g_trc_on = 0;
static void trc_init(void) {
    if (g_trc_on) return;
    const char *tv = getenv("Z2ROOT_TRACE");
    if (!tv) return;
    g_trc = (tv[0] == '/') ? fopen(tv, "a") : NULL;
    if (!g_trc) g_trc = stderr;
    setvbuf(g_trc, NULL, _IOLBF, 0);
    g_trc_on = 1;
}

// fakeroot(-0): syscall-exit で uid/gid 関連の戻り値・構造体を root(0) に偽装する。
// proot の -0 相当。ホストのアプリ uid/gid がゲストへ露出するのを防ぎ、root 前提の
// パッケージ操作(apk/apt の chown 等)が EPERM で失敗しないよう成功に見せる。
//   - getuid/geteuid/getgid/getegid → 0
//   - getgroups → 補助グループ 0 個(ホスト gid の露出を消す。id の groups が root だけになる)
//   - set*id / fchownat / fchown / fchmod / fchmodat が EPERM 等で失敗したら成功(0)に握りつぶす
//   - newfstatat/fstat/statx の結果は st_uid/st_gid を 0 に上書き(所有者を root に見せる)
// buf は entry で記録した stat 系の出力バッファアドレス(stat 以外では未使用)。
static void fake_root_on_exit(pid_t pid, long nr, unsigned long buf) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];

    switch (nr) {
        case 174: case 175: case 176: case 177:  // getuid/geteuid/getgid/getegid
            regs.regs[0] = 0; set_regs(pid, &regs); return;
        case 158:  // getgroups: 補助グループ 0 個に偽装(ホスト gid を隠す)
            if (ret >= 0) { regs.regs[0] = 0; set_regs(pid, &regs); }
            return;
        // set*id / chown / chmod 系: 失敗(EPERM 等)を成功(0)へ。ホスト権限は実際には
        // 変わらない。chmod(52/53) は dropbear が SSH PTY 確立時に chmod(/dev/pts/N) を
        // 呼ぶが、untrusted_app は pts を chmod できず EPERM → dropbear がセッションを
        // 即終了(接続リセット)するため、root と同じく成功に見せる必要がある。
        case 143: case 144: case 145: case 146: case 147: case 149:
        case 151: case 152: case 159: case 54: case 55: case 52: case 53:
            if (ret < 0) {
                if (g_trc_on && (nr == 52 || nr == 53))
                    fprintf(g_trc, "[z2trc] FAKE chmod nr=%ld ret=%ld->0 pid=%d\n", nr, ret, pid);
                regs.regs[0] = 0; set_regs(pid, &regs);
            }
            return;
        case 79: case 80: {  // newfstatat / fstat: struct stat の st_uid(off24)/st_gid(off28)
            unsigned int zero = 0;
            if (ret != 0 || buf == 0) return;
            write_tracee_mem(pid, buf + 24, &zero, 4);
            write_tracee_mem(pid, buf + 28, &zero, 4);
            return;
        }
        case 291: {  // statx: stx_uid(off20)/stx_gid(off24)
            unsigned int zero = 0;
            if (ret != 0 || buf == 0) return;
            write_tracee_mem(pid, buf + 20, &zero, 4);
            write_tracee_mem(pid, buf + 24, &zero, 4);
            return;
        }
    }
}

// fakeroot(-0) の /proc 偽装: get*id syscall を 0 に偽装しても、ゲストが
// /proc/self/status(や /proc/<pid>/status)を直接読むとホストのアプリ uid/gid が
// テキストで露出する(id -a / dpkg / apt の一部・各種スクリプトが参照)。read() の
// 戻りバッファをスキャンし、Uid: / Gid: 行の各数値を 0、Groups: 行の数値を空白、
// CapPrm/CapEff/CapBnd を全 cap セットに書き換えて root 一貫の見え方にする。
// loginuid(別ファイル /proc/.../loginuid)も 0 に化かす。length は保存する
// (数値を「右詰め 0 + 前空白」/ 固定幅 hex に置換)ため read の戻り値もバッファ
// 後続も崩さない。
// TODO: read 分割でヘッダがチャンク跨ぎの場合(現状は read 1 回で status 全体
//       が収まる前提=cat/glibc fread のバッファは status サイズ超なので実害なし)。

// guest パスが /proc/<...>/status 形か(self / <pid> / task/<tid> いずれも末尾 /status)。
static int is_proc_status_path(const char *p) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    size_t n = strlen(p);
    return n >= 7 && strcmp(p + (n - 7), "/status") == 0;
}

// guest パスが /proc/<...>/loginuid 形か。
static int is_proc_loginuid_path(const char *p) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    size_t n = strlen(p);
    return n >= 9 && strcmp(p + (n - 9), "/loginuid") == 0;
}

// 開こうとしている proc パスの偽装種別を返す(非対象は PROC_FD_NONE)。
static int proc_open_kind(const char *p) {
    if (is_proc_status_path(p)) return PROC_FD_STATUS;
    if (is_proc_loginuid_path(p)) return PROC_FD_LOGINUID;
    return PROC_FD_NONE;
}

static void status_fd_add(struct pid_state *st, int fd, int kind) {
    if (fd < 0 || kind == PROC_FD_NONE) return;
    for (int i = 0; i < STATUS_FD_MAX; i++)
        if (st->status_fds[i] == fd) { st->status_fd_kind[i] = kind; return; }
    for (int i = 0; i < STATUS_FD_MAX; i++)
        if (st->status_fds[i] < 0) { st->status_fds[i] = fd; st->status_fd_kind[i] = kind; return; }
    // 満杯: 追跡を諦める(この fd は非偽装。実害は uid 露出のみ)。
}

static void status_fd_remove(struct pid_state *st, int fd) {
    for (int i = 0; i < STATUS_FD_MAX; i++)
        if (st->status_fds[i] == fd) { st->status_fds[i] = -1; st->status_fd_kind[i] = PROC_FD_NONE; return; }
}

// fd の追跡種別を返す(非追跡は PROC_FD_NONE)。
static int status_fd_kind_of(const struct pid_state *st, int fd) {
    if (fd < 0) return PROC_FD_NONE;
    for (int i = 0; i < STATUS_FD_MAX; i++) if (st->status_fds[i] == fd) return st->status_fd_kind[i];
    return PROC_FD_NONE;
}

// openat entry: 開こうとしている guest パスが偽装対象 proc なら pending に種別を立てる。
// (maybe_rewrite_path がパス引数を host へ書き換える前に呼ぶこと=元の guest パスを見る)
static void note_proc_open(pid_t pid, const struct user_pt_regs *regs, struct pid_state *st) {
    st->pending_open_kind = PROC_FD_NONE;
    unsigned long path_addr = regs->regs[1];  // openat(dirfd, pathname, ...)
    if (path_addr == 0) return;
    char p[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, p, sizeof(p)) < 0) return;
    st->pending_open_kind = proc_open_kind(p);
}

static int is_hex_digit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

// Cap 行(CapPrm/CapEff/CapBnd)の 16 進値を全 cap セットへ書き換える(length 保存)。
// root は CapPrm/CapEff/CapBnd が全 cap、CapInh/CapAmb は 0 が通常なので後者は触らない。
// uid=0 なのに CapEff=0 という矛盾(偽装の綻び)を消す狙い。
static void fake_cap_field(char *b, size_t ls, size_t le) {
    // canonical = cap 0..40 の全ビット = 000001ffffffffff(16 hex)。
    static const char full[16] = {'0','0','0','0','0','1','f','f','f','f','f','f','f','f','f','f'};
    size_t j = ls + 7;                              // "CapXxx:" の後ろ
    while (j < le && !is_hex_digit(b[j])) j++;      // hex run の先頭へ
    size_t hs = j;
    while (j < le && is_hex_digit(b[j])) j++;        // hex run の末尾へ
    size_t he = j;
    size_t runlen = he - hs;                         // 通常 16
    for (size_t m = 0; m < runlen; m++) {
        size_t r = runlen - 1 - m;                   // 右からの距離
        b[hs + m] = (r < 16) ? full[15 - r] : '0';   // 右詰めで full を流し込む
    }
}

// status バッファ内の Uid:/Gid: 行の数値を 0(前空白詰めで length 保存)、Groups: 行の
// 数値を空白、Cap{Prm,Eff,Bnd} を全 cap に書き換える。行頭ラベルでのみ反応する。
static void fake_status_buf(char *b, size_t len) {
    size_t i = 0;
    while (i < len) {
        size_t ls = i, le = i;
        while (le < len && b[le] != '\n') le++;
        size_t llen = le - ls;
        if (llen >= 4 && (memcmp(b + ls, "Uid:", 4) == 0 || memcmp(b + ls, "Gid:", 4) == 0)) {
            size_t j = ls + 4;
            while (j < le) {
                if (b[j] >= '0' && b[j] <= '9') {
                    size_t k = j;
                    while (k < le && b[k] >= '0' && b[k] <= '9') k++;
                    for (size_t m = j; m + 1 < k; m++) b[m] = ' ';  // 上位桁を空白
                    b[k - 1] = '0';                                  // 末尾を 0
                    j = k;
                } else j++;
            }
        } else if (llen >= 7 && memcmp(b + ls, "Groups:", 7) == 0) {
            for (size_t j = ls + 7; j < le; j++)
                if (b[j] >= '0' && b[j] <= '9') b[j] = ' ';
        } else if (llen >= 7 && (memcmp(b + ls, "CapPrm:", 7) == 0 ||
                                 memcmp(b + ls, "CapEff:", 7) == 0 ||
                                 memcmp(b + ls, "CapBnd:", 7) == 0)) {
            fake_cap_field(b, ls, le);
        }
        i = le + 1;  // 改行をスキップ(末尾改行無しでも len で終端)
    }
}

// loginuid バッファ内の 10 進数字をすべて '0' に置換(先頭ゼロ詰めで length 保存=
// atoi すれば 0=root)。4294967295(未設定)も 0 に化ける。
static void fake_loginuid_buf(char *b, size_t len) {
    for (size_t i = 0; i < len; i++)
        if (b[i] >= '0' && b[i] <= '9') b[i] = '0';
}

// read() exit: 追跡 fd からの読み取りバッファ(buf, ret バイト)を種別に応じて root 偽装する。
static void fake_proc_on_read(pid_t pid, unsigned long buf, int kind) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (ret <= 0 || buf == 0) return;
    size_t len = (size_t)ret;
    if (len > PATH_MAX_Z) len = PATH_MAX_Z;  // status は read 1 回で全体が収まる前提
    char b[PATH_MAX_Z];
    struct iovec lo = { b, len };
    struct iovec re = { (void *)buf, len };
    if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != (ssize_t)len) return;
    if (kind == PROC_FD_LOGINUID) fake_loginuid_buf(b, len);
    else                          fake_status_buf(b, len);
    write_tracee_mem(pid, buf, b, len);
}

// readfree モード: openat 時に /proc 偽装を「temp ファイル差し替え」で行う。
// 従来は status/loginuid を指す fd を追跡し read() exit ごとにバッファを書き換えていたが、
// それには read(と close)を seccomp トレース対象に残す必要があり、対話シェルや dd の
// 大量 read が proot 比で遅かった。ここでは openat の瞬間に偽装済み内容を rootfs 内の
// temp へ書き出し、openat のパス引数をその temp(ホスト実パス)へ差し替える。以後ゲストは
// 通常ファイルを read するだけなので read/close をトレース対象から外せる(= proot 同等速)。
// temp は openat-exit で unlink する(ゲストは fd を保持済み=open-unlink で内容は生きる)。

// /proc/self/... や /proc/thread-self/... の "self" をトレーシ tid(pid)へ解決して
// 実ホストパスを得る(/proc は -b /proc で host /proc に 1:1。ゲスト pid == host pid)。
static void resolve_proc_self(const char *g, pid_t pid, char *out, size_t cap) {
    const char *rest = g + 6;  // "/proc/" の後ろ
    if (strncmp(rest, "self/", 5) == 0)
        snprintf(out, cap, "/proc/%d/%s", (int)pid, rest + 5);
    else if (strncmp(rest, "thread-self/", 12) == 0)
        snprintf(out, cap, "/proc/%d/%s", (int)pid, rest + 12);
    else
        snprintf(out, cap, "%s", g);
}

// readfree モードの temp 差し替え本体。戻り値 1=差し替えた(呼び出し側は
// maybe_rewrite_path をスキップし openat-exit で temp を unlink)、0=非対象/失敗
// (通常 openat にフォールバック)。失敗時に uid 露出する可能性はあるが稀。
static int try_subst_proc_open(const struct config *cfg, pid_t pid,
                               struct user_pt_regs *regs, struct pid_state *st) {
    unsigned long path_addr = regs->regs[1];  // openat(dirfd, pathname, ...)
    if (path_addr == 0) return 0;
    char g[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, g, sizeof(g)) < 0) return 0;
    int kind = proc_open_kind(g);
    if (kind == PROC_FD_NONE) return 0;

    // 実 /proc ファイルを読む(self/thread-self は tid へ解決)。読めなければ非差し替え。
    char real[PATH_MAX_Z];
    resolve_proc_self(g, pid, real, sizeof(real));
    int rfd = open(real, O_RDONLY | O_CLOEXEC);
    if (rfd < 0) return 0;
    char buf[STATUS_BUF_MAX];
    size_t total = 0;
    ssize_t n;
    while (total < sizeof(buf) && (n = read(rfd, buf + total, sizeof(buf) - total)) > 0)
        total += (size_t)n;
    close(rfd);

    // 偽装を当てる(read 経路と同じ書き換え)。
    if (kind == PROC_FD_LOGINUID) fake_loginuid_buf(buf, total);
    else                          fake_status_buf(buf, total);

    // rootfs 内 temp(tid 名)へ書き出す。同 tid の openat entry→exit は直列なので
    // 同名の衝突は起きない(超える前に exit で unlink される)。
    char tmp[PATH_MAX_Z];
    snprintf(tmp, sizeof(tmp), "%s/.z2subst.%d", cfg->rootfs, (int)pid);
    int wfd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (wfd < 0) return 0;
    if (total > 0 && write(wfd, buf, total) != (ssize_t)total) { close(wfd); unlink(tmp); return 0; }
    close(wfd);

    // openat のパス引数を temp のホスト実パスへ差し替える(スタック下スクラッチ)。
    // tmp は cfg->rootfs 配下=host_path_for の二重変換ガードに掛かるが、呼び出し側で
    // maybe_rewrite_path 自体をスキップするので確実に素通しになる。
    size_t len = strlen(tmp) + 1;
    unsigned long base = (regs->sp - SCRATCH_OFFSET - ((len + 7) & ~7UL)) & ~15UL;
    if (write_tracee_mem(pid, base, tmp, len) != 0) { unlink(tmp); return 0; }
    regs->regs[1] = base;
    set_regs(pid, regs);
    st->subst_active = 1;
    return 1;
}

// openat-exit: readfree の temp 差し替えで作った temp を消す(ゲストは fd 保持済み)。
static void subst_on_exit(const struct config *cfg, pid_t pid, struct pid_state *st) {
    if (!st->subst_active) return;
    char tmp[PATH_MAX_Z];
    snprintf(tmp, sizeof(tmp), "%s/.z2subst.%d", cfg->rootfs, (int)pid);
    unlink(tmp);
    st->subst_active = 0;
}

// --link2symlink: linkat(ハードリンク)を symlinkat へ化かし、target に「元ファイルの
// ゲスト絶対パス」を入れた symlink でエミュレートする。Android のアプリ内ストレージは
// link() を EACCES で拒否するため、これが無いと dpkg/apt の status-old バックリンク等が
// 壊れる(proot の --link2symlink 相当)。aarch64 に素の link は無く linkat(37)→symlinkat(36)。
// target をゲスト絶対パスで持つのは、z2root の canonicalize がアクセス時に symlink 中身を
// ゲスト視点で再解決し、最終的にホスト実パスへ畳むため(rootfs 内の絶対 symlink と同じ扱い)。
// 戻り値: 1=変換した, 0=変換せず(呼び出し側で通常 linkat 変換にフォールバック)。
static int rewrite_link2symlink(const struct config *cfg, pid_t pid, struct user_pt_regs *regs) {
    long olddirfd = (long)(int)regs->regs[0];
    unsigned long old_addr = regs->regs[1];
    long newdirfd = (long)(int)regs->regs[2];
    unsigned long new_addr = regs->regs[3];
    if (old_addr == 0 || new_addr == 0) return 0;

    char oldp[PATH_MAX_Z], newp[PATH_MAX_Z];
    if (read_tracee_str(pid, old_addr, oldp, sizeof(oldp)) < 0) return 0;
    if (read_tracee_str(pid, new_addr, newp, sizeof(newp)) < 0) return 0;

    // target = oldpath のゲスト絶対パス(symlink の中身)。
    char target[PATH_MAX_Z];
    if (oldp[0] == '/') {
        snprintf(target, sizeof(target), "%s", oldp);
    } else {
        // 相対 oldpath は基準 dir(cwd または dirfd)を /proc 経由でゲスト絶対化する。
        char proc[64], host_dir[PATH_MAX_Z], guest_dir[PATH_MAX_Z];
        if (olddirfd == AT_FDCWD)
            snprintf(proc, sizeof(proc), "/proc/%d/cwd", (int)pid);
        else
            snprintf(proc, sizeof(proc), "/proc/%d/fd/%d", (int)pid, (int)olddirfd);
        ssize_t n = readlink(proc, host_dir, sizeof(host_dir) - 1);
        if (n < 0) return 0;
        host_dir[n] = '\0';
        host_to_guest(cfg, host_dir, guest_dir, sizeof(guest_dir));
        snprintf(target, sizeof(target), "%s/%s", guest_dir, oldp);
    }

    // linkpath(symlink を作る場所) = newpath を host 実パスへ。fd 相対は dirfd に委ねる。
    char host_new[PATH_MAX_Z];
    const char *linkpath;
    long eff_newdirfd;
    if (host_path_for(cfg, pid, newp, 0, newdirfd, host_new, sizeof(host_new)) == 0) {
        linkpath = host_new;
        eff_newdirfd = AT_FDCWD;
    } else {
        linkpath = newp;       // fd 相対(変換不要) — dirfd 基準のまま作る
        eff_newdirfd = newdirfd;
    }

    // target と linkpath を tracee スタック下スクラッチへ連続配置。
    size_t tlen = strlen(target) + 1;
    size_t llen = strlen(linkpath) + 1;
    size_t total = ((tlen + 7) & ~7UL) + ((llen + 7) & ~7UL);
    unsigned long base = (regs->sp - SCRATCH_OFFSET - total) & ~15UL;
    unsigned long tptr = base;
    unsigned long lptr = base + ((tlen + 7) & ~7UL);
    if (write_tracee_mem(pid, tptr, target, tlen) != 0) return 0;
    if (write_tracee_mem(pid, lptr, linkpath, llen) != 0) return 0;

    // symlinkat(x0=target, x1=newdirfd, x2=linkpath)。
    regs->regs[0] = tptr;
    regs->regs[1] = (unsigned long)eff_newdirfd;
    regs->regs[2] = lptr;
    regs->regs[8] = 36;  // 表示用。実 dispatch は NT_ARM_SYSTEM_CALL で変える。
    set_regs(pid, regs);
    set_syscall_nr(pid, 36);
    return 1;
}

// AF_UNIX の pathname ソケット (bind/connect) の sun_path をホスト実パスへ書き換える。
// proot は connect/bind の sockaddr を翻訳するが z2root は未対応だった = Xvnc が作る
// /tmp/.X11-unix/X1 や dbus/pulseaudio の unix ソケットが「ホストの実 /tmp」を指して
// ENOENT で失敗し、Linux GUI (z2gui) がサーバを立てられず/接続できなかった。
// abstract ソケット (sun_path[0]=='\0') は名前空間上の名前でファイルではないため翻訳しない
// (共有 netns なのでそのまま通り、X クライアントの abstract 接続は元から動く)。
static void maybe_rewrite_sockaddr(const struct config *cfg, pid_t pid, struct user_pt_regs *regs) {
    unsigned long addr = regs->regs[1];
    unsigned long addrlen = regs->regs[2];
    const size_t path_off = offsetof(struct sockaddr_un, sun_path);
    if (addr == 0 || addrlen <= path_off) return;

    struct sockaddr_un un;
    memset(&un, 0, sizeof(un));
    size_t rd = addrlen > sizeof(un) ? sizeof(un) : addrlen;
    struct iovec local = { &un, rd };
    struct iovec remote = { (void *)addr, rd };
    if (process_vm_readv(pid, &local, 1, &remote, 1, 0) != (ssize_t)rd) return;
    if (un.sun_family != AF_UNIX) return;
    if (un.sun_path[0] == '\0') return;  // abstract ソケットは翻訳しない

    // sun_path は最大 108B で必ずしも null 終端されない。読めた範囲で長さを確定する。
    size_t pathcap = rd - path_off;
    if (pathcap > sizeof(un.sun_path)) pathcap = sizeof(un.sun_path);
    size_t pl = strnlen(un.sun_path, pathcap);
    char guest[sizeof(un.sun_path) + 1];
    memcpy(guest, un.sun_path, pl);
    guest[pl] = '\0';
    if (guest[0] != '/') return;  // 相対ソケットパスは非対象

    char host[PATH_MAX_Z];
    // ソケット自体 (最終要素) は symlink を辿らない。deref=0。
    if (host_path_for(cfg, pid, guest, 0, AT_FDCWD, host, sizeof(host)) != 0) return;
    size_t hl = strlen(host);
    if (hl >= sizeof(un.sun_path)) return;  // 108B に収まらなければ据え置き (安全側)

    struct sockaddr_un nun;
    memset(&nun, 0, sizeof(nun));
    nun.sun_family = AF_UNIX;
    memcpy(nun.sun_path, host, hl);  // null 終端は memset 済み
    socklen_t nlen = (socklen_t)(path_off + hl + 1);
    unsigned long base = (regs->sp - SCRATCH_OFFSET - sizeof(nun)) & ~15UL;
    if (write_tracee_mem(pid, base, &nun, nlen) != 0) return;
    regs->regs[1] = base;
    regs->regs[2] = nlen;
    set_regs(pid, regs);
}

static void maybe_rewrite_path(const struct config *cfg, pid_t pid, struct user_pt_regs *regs) {
    long nr = (long)regs->regs[8];
    if (nr == 200 || nr == 203) { maybe_rewrite_sockaddr(cfg, pid, regs); return; }  // bind / connect
    if (nr == 221) { rewrite_execve(cfg, pid, regs, 0); return; }  // execve
    if (nr == 281) { rewrite_execve(cfg, pid, regs, 1); return; }  // execveat
    if (nr == 37 && cfg->link2symlink) {                          // linkat → symlinkat
        if (rewrite_link2symlink(cfg, pid, regs)) return;
        // 変換できなければ通常の linkat パス変換にフォールバック。
    }

    struct sc_paths sp;
    if (!syscall_paths(nr, &sp)) return;

    // 各パス引数を host 実パスへ変換し、まとめてスタック下スクラッチに置く。
    char hosts[2][PATH_MAX_Z];
    int hidx[2];
    int hn = 0;
    for (int i = 0; i < sp.n; i++) {
        struct path_arg *pa = &sp.a[i];
        unsigned long addr = regs->regs[pa->idx];
        if (addr == 0) continue;
        char guest[PATH_MAX_Z];
        if (read_tracee_str(pid, addr, guest, sizeof(guest)) < 0) continue;

        int deref = pa->deref;
        if (pa->flag_reg >= 0) {
            unsigned long fl = regs->regs[pa->flag_reg];
            if (pa->flag_follow_bit && (fl & (unsigned long)pa->flag_follow_bit)) deref = 1;
            if (pa->flag_nofollow_bit && (fl & (unsigned long)pa->flag_nofollow_bit)) deref = 0;
        }
        long dirfd = (pa->dirfd_reg < 0) ? AT_FDCWD : (long)(int)regs->regs[pa->dirfd_reg];
        if (host_path_for(cfg, pid, guest, deref, dirfd, hosts[hn], sizeof(hosts[hn])) != 0)
            continue;
        hidx[hn] = pa->idx;
        hn++;
    }
    if (hn == 0) return;

    // スタック下に各 host 文字列を連続配置(下方向に確保)。syscall 実行中は
    // カーネルがユーザスタックを使わないため sp 直下への書き込みは安全。
    size_t total = 0;
    for (int i = 0; i < hn; i++) total += ((strlen(hosts[i]) + 1 + 7) & ~7UL);
    unsigned long base = (regs->sp - SCRATCH_OFFSET - total) & ~15UL;
    unsigned long off = 0;
    int wrote = 0;
    for (int i = 0; i < hn; i++) {
        size_t len = strlen(hosts[i]) + 1;
        if (write_tracee_mem(pid, base + off, hosts[i], len) != 0) continue;
        regs->regs[hidx[i]] = base + off;
        off += (len + 7) & ~7UL;
        wrote = 1;
    }
    if (wrote) set_regs(pid, regs);
}

// ---- argv パース --------------------------------------------------------------

static void usage_die(const char *me) {
    fprintf(stderr,
        "%s: z2term 自前 ptrace エンジン (proot 互換 subset)\n"
        "usage: %s [-0] [--kill-on-exit] [--link2symlink] -r <rootfs>\n"
        "          [-b host[:guest]]... [-w <cwd>] -- <command> [args...]\n",
        me, me);
    exit(2);
}

// ホスト実パスを正規化(realpath)して in-place 置換する。存在しなければ据え置き。
// 理由: Android の app data は /data/user/0/<pkg> が /data/data/<pkg> への symlink。
//   chdir 後に /proc/<pid>/cwd が返すのは「正規化後」のホストパス(/data/data/...)
//   だが、ProotLauncher が渡す bind.host / rootfs は非正規パス(/data/user/0/...)の
//   ことがある。揃えておかないと host_to_guest の照合が外れ、pwd や相対パス(ls .)が
//   ホスト実パスを露出する(getcwd 逆変換が効かず、相対 . の stat が rootfs+host で ENOENT)。
static void canon_host_inplace(char *path, size_t cap) {
    char resolved[PATH_MAX_Z];
    if (realpath(path, resolved)) snprintf(path, cap, "%s", resolved);
}

static void add_bind(struct config *cfg, const char *spec) {
    if (cfg->nbinds >= MAX_BINDS) return;
    struct bind_entry *b = &cfg->binds[cfg->nbinds];
    const char *colon = strchr(spec, ':');
    if (colon) {
        size_t hlen = (size_t)(colon - spec);
        if (hlen >= sizeof(b->host)) return;
        memcpy(b->host, spec, hlen);
        b->host[hlen] = '\0';
        snprintf(b->guest, sizeof(b->guest), "%s", colon + 1);
    } else {
        // host==guest (例: -b /dev)
        snprintf(b->host, sizeof(b->host), "%s", spec);
        snprintf(b->guest, sizeof(b->guest), "%s", spec);
    }
    canon_host_inplace(b->host, sizeof(b->host));  // /proc/<pid>/cwd と照合できる正規パスへ
    b->guest_len = strlen(b->guest);
    cfg->nbinds++;
}

static char *const *parse_args(int argc, char **argv, struct config *cfg) {
    memset(cfg, 0, sizeof(*cfg));
    snprintf(cfg->cwd, sizeof(cfg->cwd), "/");

    int i = 1;
    // argv[0] が "proot" や "z2root" のラッパ名でも、解析は i=1 から (オプション位置)。
    for (; i < argc; i++) {
        const char *a = argv[i];
        if (strcmp(a, "-0") == 0) { cfg->fake_root = 1; }
        else if (strcmp(a, "--kill-on-exit") == 0) { cfg->kill_on_exit = 1; }
        else if (strcmp(a, "--link2symlink") == 0) { cfg->link2symlink = 1; }
        else if (strcmp(a, "-r") == 0 && i + 1 < argc) { snprintf(cfg->rootfs, sizeof(cfg->rootfs), "%s", argv[++i]); }
        else if (strcmp(a, "-b") == 0 && i + 1 < argc) { add_bind(cfg, argv[++i]); }
        else if (strcmp(a, "-w") == 0 && i + 1 < argc) { snprintf(cfg->cwd, sizeof(cfg->cwd), "%s", argv[++i]); }
        else if (strcmp(a, "--") == 0) { i++; break; }
        else if (a[0] != '-') { break; }  // ここからコマンド
        else { /* 未知オプションは無視 (proot 互換のため寛容に) */ }
    }
    cfg->readfree = (getenv("Z2ROOT_NO_READFREE") == NULL);  // /proc 偽装の read 非トレース化(既定 ON・Z2ROOT_NO_READFREE で無効化)
    if (cfg->rootfs[0] == '\0' || i >= argc) usage_die(argv[0]);
    canon_host_inplace(cfg->rootfs, sizeof(cfg->rootfs));  // bind と同様 /proc/<pid>/cwd と揃える
    cfg->rootfs_len = strlen(cfg->rootfs);
    // 末尾の "/" は剥がす (二重スラッシュ防止)。
    while (cfg->rootfs_len > 1 && cfg->rootfs[cfg->rootfs_len - 1] == '/') {
        cfg->rootfs[--cfg->rootfs_len] = '\0';
    }
    return &argv[i];
}

// ---- seccomp-bpf フィルタ (高速化の要) ---------------------------------------
// 旧実装は PTRACE_SYSCALL で「全 syscall を entry/exit の2回」トラップしていた=
// fork/exec/read/write の多い対話シェルや apt が proot(seccomp 併用)比で 20〜25倍
// 遅かった。ここで「パス変換・fakeroot 偽装・getcwd 逆変換・/proc status 偽装に
// 必要な syscall だけ」を SECCOMP_RET_TRACE に、残りを SECCOMP_RET_ALLOW にする
// BPF フィルタを子に入れる。トレーサは PTRACE_O_TRACESECCOMP + 既定 PTRACE_CONT で、
// フィルタ該当 syscall のときだけ PTRACE_EVENT_SECCOMP(=entry)で止まる。残りは
// カーネル内で素通り=ネイティブ速度。これで proot 同等の速さになる。
//
// 注意: Android は untrusted_app に既に seccomp フィルタを入れている。フィルタは
// 重畳評価され「より重い action が勝つ」(RET_TRAP > RET_TRACE > RET_ALLOW)。よって
// 本フィルタの RET_TRACE は Android の ALLOW に勝ち(=狙った syscall を捕捉でき)、
// Android が権限系 syscall に出す RET_TRAP(SIGSYS) は本フィルタの指定に勝つ(=従来
// どおり SIGSYS として握り潰す)。NO_NEW_PRIVS を立てれば非特権でも導入できる。

// トレース対象 syscall(aarch64 番号)。パス変換が要るもの + fd 追跡(openat/close/read)。
static const int kTraceSyscallsBase[] = {
    17,                       // getcwd (戻り値逆変換)
    56, 437,                  // openat / openat2
    79, 291,                  // newfstatat / statx
    48, 439,                  // faccessat / faccessat2
    78,                       // readlinkat
    35, 34, 33,               // unlinkat / mkdirat / mknodat
    53, 88,                   // fchmodat / utimensat
    49, 43,                   // chdir / statfs
    36, 37, 38, 276,          // symlinkat / linkat / renameat / renameat2
    221, 281,                 // execve / execveat
    57, 63,                   // close / read (fd 追跡・status/loginuid 偽装)
    29,                       // ioctl (glibc termios2 → legacy termios へ書換。isatty 回避)
    200, 203,                 // bind / connect (AF_UNIX pathname ソケットのパス翻訳。GUI/dbus/pulse)
};
// fakeroot(-0) のとき追加でトレースする syscall(戻り値/構造体を root に偽装)。
static const int kTraceSyscallsFakeroot[] = {
    174, 175, 176, 177,       // getuid / geteuid / getgid / getegid
    158,                      // getgroups
    143, 144, 145, 146, 147, 149, 151, 152, 159, 54, 55, 80,
    // setregid/setgid/setreuid/setuid/setresuid/setresgid/setfsuid/setfsgid/
    // setgroups/fchownat/fchown/fstat(=fake_root_on_exit の対象)
};

// プロセスへ seccomp フィルタを導入する。成功 0 / 失敗 -1。
static int install_seccomp_filter(const struct config *cfg) {
    int nrs[64];
    int n = 0;
    for (size_t i = 0; i < sizeof(kTraceSyscallsBase)/sizeof(int); i++) {
        int s = kTraceSyscallsBase[i];
        // readfree: /proc 偽装は openat 時 temp 差し替えで完結=read/close の追跡が不要。
        // 大量に呼ばれる read(63)/close(57) を捕捉しないことで native 速度に近づける。
        if (cfg->readfree && (s == 63 || s == 57)) continue;
        nrs[n++] = s;
    }
    if (cfg->fake_root)
        for (size_t i = 0; i < sizeof(kTraceSyscallsFakeroot)/sizeof(int); i++) nrs[n++] = kTraceSyscallsFakeroot[i];

    // BPF プログラムを組む。レイアウト:
    //   [0] LD arch
    //   [1] arch != AARCH64 → ALLOW
    //   [2] LD nr
    //   [3 .. 3+C-1] nr 比較 C 個(一致で TRACE へ、不一致で次へ)
    //   [3+C] ALLOW
    //   [4+C] TRACE
    int C = n;
    struct sock_filter prog[5 + 64];
    int p = 0;
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch));
    prog[p++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 0, (__u8)(1 + C));
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr));
    for (int i = 0; i < C; i++)
        prog[p++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (unsigned)nrs[i], (__u8)(C - i), 0);
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE);

    struct sock_fprog fprog = { .len = (unsigned short)p, .filter = prog };
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) return -1;
    if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &fprog, 0, 0) != 0) return -1;
    return 0;
}

// ---- 子プロセス: TRACEME してゲストコマンドを exec ----------------------------

static int run_child(const struct config *cfg) {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) != 0) {
        perror("PTRACE_TRACEME");
        return 127;
    }
    // トレーサと握手する。先に SIGSTOP で止まり、トレーサが PTRACE_O_TRACESECCOMP を
    // 立ててから seccomp フィルタを入れて execve する。これをしないと「フィルタ導入
    // 済みだがトレーサが TRACESECCOMP 未設定」の窓で最初の execve が ENOSYS になる。
    raise(SIGSTOP);
    // ゲスト視点の cwd へ移動 (ホスト実パスへ変換してから chdir)。
    // (フィルタ導入前に済ませる=この chdir はトレーサに翻訳させない)
    char host_cwd[PATH_MAX_Z];
    if (translate_abs(cfg, cfg->cwd, host_cwd, sizeof(host_cwd))) {
        if (chdir(host_cwd) != 0) chdir(cfg->rootfs);
    }

    // 最初の execve はトレーサがまだ傍受していない(このプロセス自身の呼び出し)ので、
    // ローダ差し替え / シバン解決を自前で行う。動的バイナリは PT_INTERP(ローダ)を
    // rootfs 内へ向けて明示起動しないと、カーネルがホスト / からローダを解決して ENOENT。
    // 重要: plan_exec / フォールバック解決は openat/read/readlink 等を呼ぶ。これらは
    // seccomp フィルタの対象なので、フィルタ導入「前」に済ませておく(導入後に呼ぶと
    // トレーサがブートストラップ準備の syscall を翻訳してしまう)。
    int n = 0;
    while (cfg->command[n]) n++;

    char **primary_argv = NULL;
    const char *primary_target = NULL;
    struct exec_plan plan;
    if (plan_exec(cfg, getpid(), cfg->command[0], cfg->command[0], &plan) == 0) {
        // [prefix..., <元args[orig_start..]>, NULL]
        char **nv = malloc((size_t)(plan.nprefix + n + 1) * sizeof(char *));
        if (nv) {
            int k = 0;
            for (int j = 0; j < plan.nprefix; j++) nv[k++] = plan.prefix[j];
            for (int j = plan.orig_start; j < n; j++) nv[k++] = (char *)cfg->command[j];
            nv[k] = NULL;
            primary_argv = nv;
            primary_target = plan.target;
        }
    }
    // フォールバック: パスだけ host へ変換して素直に exec(これも事前解決しておく)。
    char real_guest[PATH_MAX_Z];
    resolve_guest_symlink(cfg, cfg->command[0], real_guest, sizeof(real_guest));
    char host_cmd[PATH_MAX_Z];
    if (!translate_abs(cfg, real_guest, host_cmd, sizeof(host_cmd)))
        snprintf(host_cmd, sizeof(host_cmd), "%s", real_guest);

    // seccomp フィルタを導入(失敗してもフォールバックで続行=トレーサが全 syscall を
    // PTRACE_SYSCALL で見る旧挙動になるだけ。Z2ROOT_NO_SECCOMP=1 で明示無効化も可)。
    // この後は execve 以外の syscall を呼ばない(ブートストラップ execve を最初の
    // PTRACE_EVENT_SECCOMP にして、トレーサ側のブートストラップ判定を成立させる)。
    if (!getenv("Z2ROOT_NO_SECCOMP")) install_seccomp_filter(cfg);

    if (primary_target) execve(primary_target, primary_argv, environ);
    execve(host_cmd, cfg->command, environ);
    fprintf(stderr, "z2root: execve(%s) failed: %s\n", host_cmd, strerror(errno));
    return 127;
}

// ---- 親プロセス: ptrace ループ ------------------------------------------------

// この traced syscall は exit-stop での後処理(戻り値/構造体の偽装・逆変換)が要るか。
// seccomp モードでは「要らない」syscall は entry の PTRACE_EVENT_SECCOMP 後すぐ
// PTRACE_CONT して exit を取らない=ストップ回数を半減できる。
static int syscall_needs_exit(const struct config *cfg, const struct pid_state *st) {
    long nr = st->entry_nr;
    if (nr == 17) return 1;                          // getcwd: 戻りバッファ逆変換
    if (!cfg->fake_root) return 0;                   // fakeroot 系/ fd 追跡は -0 配下のみ
    switch (nr) {
        case 79: case 80: case 291: return 1;        // stat 系: st_uid/st_gid 偽装
        case 56: return st->pending_open_kind != PROC_FD_NONE;  // openat: status/loginuid fd 追跡
        case 63: return st->aux_kind != PROC_FD_NONE;           // read: 追跡 fd のバッファ偽装
        case 57: return 0;                            // close: entry で追跡解除のみ
        case 174: case 175: case 176: case 177: case 158:
        case 143: case 144: case 145: case 146: case 147: case 149:
        case 151: case 152: case 159: case 54: case 55:
        case 52: case 53: return 1;  // 戻り値を 0(成功)へ(chmod/chown/set*id の EPERM 偽装)
        default: return 0;                            // パス変換のみ(execve/unlinkat 等)
    }
}

// syscall-entry 時の処理(パス変換・fd 追跡・偽装対象の控え)。need_exit を返す。
// glibc 2.42+ の tcgetattr/tcsetattr は termios2 ABI(TCGETS2/TCSETS2…)で ioctl する。
// しかし Android の untrusted_app は pty への TCGETS2 系 ioctl を拒否(EACCES)するため、
// glibc 製ゲスト(Arch/Ubuntu 等)では isatty() が失敗 → bash/zsh が「端末でない」と判断し
// 非対話で起動 → プロンプトが出ない(固まって見える)。musl(Alpine)は旧 TCGETS を使うので無事。
// proot と同様、entry で termios2 ioctl を legacy(TCGETS/TCSETS…)へ書き換えて回避する。
// 先頭の struct termios 部分は termios2 と同レイアウトで、通常 baud では c_ispeed/c_ospeed を
// 使わない(速度は c_cflag の CBAUD から解決)ため実害なく動く。
static void maybe_rewrite_ioctl(pid_t pid, struct user_pt_regs *regs) {
    unsigned long legacy;
    switch (regs->regs[1]) {                 // regs[1] = ioctl request 番号
        case 0x802c542aUL: legacy = 0x5401; break;  // TCGETS2  → TCGETS
        case 0x402c542bUL: legacy = 0x5402; break;  // TCSETS2  → TCSETS
        case 0x402c542cUL: legacy = 0x5403; break;  // TCSETSW2 → TCSETSW
        case 0x402c542dUL: legacy = 0x5404; break;  // TCSETSF2 → TCSETSF
        default: return;
    }
    regs->regs[1] = legacy;
    set_regs(pid, regs);
}

static int handle_syscall_entry(const struct config *cfg, pid_t pid, struct pid_state *st) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) { st->entry_nr = -1; return 0; }
    st->entry_nr = (long)regs.regs[8];
    st->aux_kind = PROC_FD_NONE;
    if (st->entry_nr == 29) {                // ioctl: termios2→legacy 書換のみ(exit 後処理不要)
        st->aux_addr = 0;
        maybe_rewrite_ioctl(pid, &regs);
        return 0;
    }
    unsigned long aux = 0;
    if (st->entry_nr == 17) aux = regs.regs[0];      // getcwd buf
    else if (cfg->fake_root) switch (st->entry_nr) {
        case 80:  aux = regs.regs[1]; break;         // fstat: stat 出力バッファ
        case 79:  aux = regs.regs[2]; break;         // newfstatat
        case 291: aux = regs.regs[4]; break;         // statx
        case 56:  // openat: /proc status・loginuid 検出
            // readfree: temp 差し替えに成功したらパスは確定済み=以降のパス変換は不要。
            if (cfg->readfree && try_subst_proc_open(cfg, pid, &regs, st))
                return 1;  // exit で temp を unlink するため need_exit
            note_proc_open(pid, &regs, st);
            break;
        case 57:  if (!cfg->readfree) status_fd_remove(st, (int)regs.regs[0]); break;  // close: fd 追跡解除
        case 63:  // read: 追跡 fd なら buf を控えて exit で偽装(readfree では read を追跡しない)
            if (!cfg->readfree) {
                st->aux_kind = status_fd_kind_of(st, (int)regs.regs[0]);
                if (st->aux_kind != PROC_FD_NONE) aux = regs.regs[1];
            }
            break;
    }
    st->aux_addr = aux;
    maybe_rewrite_path(cfg, pid, &regs);
    return syscall_needs_exit(cfg, st);
}

// syscall-exit 時の処理(戻り値・構造体の逆変換 / 偽装)。
static void handle_syscall_exit(const struct config *cfg, pid_t pid, struct pid_state *st) {
    if (st->entry_nr == 17 && st->aux_addr) {
        rewrite_getcwd_result(cfg, pid, st->aux_addr);
    } else if (cfg->fake_root) {
        if (st->entry_nr == 56 && st->subst_active) {
            subst_on_exit(cfg, pid, st);  // readfree: 差し替えた temp を unlink
        } else if (st->entry_nr == 56 && st->pending_open_kind != PROC_FD_NONE) {
            struct user_pt_regs r;  // openat 成功なら戻り fd を種別付きで追跡
            if (get_regs(pid, &r) == 0 && (long)r.regs[0] >= 0)
                status_fd_add(st, (int)r.regs[0], st->pending_open_kind);
            st->pending_open_kind = PROC_FD_NONE;
        } else if (st->entry_nr == 63 && st->aux_addr) {
            fake_proc_on_read(pid, st->aux_addr, st->aux_kind);
        } else {
            fake_root_on_exit(pid, st->entry_nr, st->aux_addr);
        }
    }
}

// 再開ヘルパー。seccomp モードでは「exit 待ち(at_exit)」のときだけ PTRACE_SYSCALL、
// それ以外は PTRACE_CONT(=次の seccomp イベント/シグナルまで素通り)。seccomp 不使用
// (フォールバック)では従来どおり全 syscall を PTRACE_SYSCALL で見る。
static void z_resume(pid_t pid, int seccomp_on, const struct pid_state *st, long sig) {
    long req;
    if (seccomp_on != 1) req = PTRACE_SYSCALL;
    else req = (st && st->at_exit) ? PTRACE_SYSCALL : PTRACE_CONT;
    ptrace(req, pid, 0, (void *)sig);
}

static int run_tracer(const struct config *cfg, pid_t child) {
    int status;
    // 最初の停止 = 子の raise(SIGSTOP)(握手)。
    if (waitpid(child, &status, 0) < 0) { perror("waitpid"); return 1; }

    int opts = PTRACE_O_TRACESYSGOOD | PTRACE_O_TRACEFORK |
               PTRACE_O_TRACEVFORK | PTRACE_O_TRACECLONE | PTRACE_O_TRACESECCOMP;
    if (cfg->kill_on_exit) opts |= PTRACE_O_EXITKILL;
    ptrace(PTRACE_SETOPTIONS, child, 0, (void *)(long)opts);

    struct pid_state *cst = state_for(child);
    if (cst) cst->started = 1;  // 握手の SIGSTOP は消化済み
    // 子を再開。以降 chdir(導入前=native)→フィルタ導入→execve と進む。
    ptrace(PTRACE_CONT, child, 0, 0);

    int seccomp_mode = -1;   // -1: 未判定 / 0: seccomp 無効(フォールバック) / 1: 有効
    int boot_done = 0;       // ブートストラップ execve(子の最初の execve)を消化したか
    int exit_code = 0;
    int alive = 1;
    trc_init();
    int g_trace = g_trc_on;  // [DEBUG] PTY 調査用一時トレース(出力先は g_trc)
    while (alive > 0) {
        pid_t pid = waitpid(-1, &status, __WALL);
        if (pid < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (WIFEXITED(status) || WIFSIGNALED(status)) {
            if (g_trace) fprintf(g_trc, "[z2trc] pid=%d %s code/sig=%d\n", pid,
                WIFEXITED(status) ? "EXITED" : "KILLED",
                WIFEXITED(status) ? WEXITSTATUS(status) : WTERMSIG(status));
            if (pid == child) exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
            state_drop(pid);
            if (pid == child) alive = 0;
            continue;
        }
        if (!WIFSTOPPED(status)) continue;

        int sig = WSTOPSIG(status);
        int event = (status >> 16) & 0xff;
        if (g_trace && event != PTRACE_EVENT_SECCOMP && sig != (SIGTRAP | 0x80)) {
            const char *ev = event == PTRACE_EVENT_FORK ? "FORK" :
                event == PTRACE_EVENT_VFORK ? "VFORK" :
                event == PTRACE_EVENT_CLONE ? "CLONE" :
                event == PTRACE_EVENT_EXEC ? "EXEC" : "";
            fprintf(g_trc, "[z2trc] pid=%d STOP sig=%d ev=%s\n", pid, sig, ev);
        }

        // seccomp イベント(=フィルタ該当 syscall の entry)。seccomp モード確定。
        if (event == PTRACE_EVENT_SECCOMP) {
            seccomp_mode = 1;
            struct pid_state *st = state_for(pid);
            if (!st) { ptrace(PTRACE_CONT, pid, 0, 0); continue; }
            if (pid == child && !boot_done) {
                // ブートストラップ execve(run_child が既に host 解決済み)。翻訳しない。
                boot_done = 1;
                st->entry_nr = 221; st->at_exit = 0;
                ptrace(PTRACE_CONT, pid, 0, 0);
                continue;
            }
            if (g_trace) {
                struct user_pt_regs r;
                if (get_regs(pid, &r) == 0) {
                    long nr = r.regs[8];
                    char pbuf[160]; pbuf[0] = 0;
                    if (nr == 29) snprintf(pbuf, sizeof pbuf, " req=0x%lx fd=%ld", (unsigned long)r.regs[1], (long)r.regs[0]);
                    else if (nr == 56 || nr == 79 || nr == 53 || nr == 54)
                        read_tracee_str(pid, r.regs[1], pbuf, sizeof pbuf);
                    fprintf(g_trc, "[z2trc] pid=%d SYS nr=%ld %s\n", pid, nr, pbuf);
                }
            }
            int need_exit = handle_syscall_entry(cfg, pid, st);
            st->at_exit = need_exit ? 1 : 0;
            ptrace(need_exit ? PTRACE_SYSCALL : PTRACE_CONT, pid, 0, 0);
            continue;
        }

        // syscall-stop (TRACESYSGOOD: SIGTRAP|0x80)。
        if (sig == (SIGTRAP | 0x80)) {
            struct pid_state *st = state_for(pid);
            if (seccomp_mode == 1) {
                // seccomp モードでは PTRACE_SYSCALL で取った exit のみ来る。
                if (st) { handle_syscall_exit(cfg, pid, st); st->at_exit = 0; }
                z_resume(pid, 1, st, 0);
            } else {
                // フォールバック(全 syscall トレース): entry/exit トグル。
                if (st && st->at_exit == 0) {
                    if (g_trace) {
                        struct user_pt_regs r;
                        if (get_regs(pid, &r) == 0) {
                            long nr = r.regs[8];
                            char pbuf[160]; pbuf[0] = 0;
                            if (nr == 57 || nr == 63 || nr == 64)  // close/read/write
                                snprintf(pbuf, sizeof pbuf, " fd=%ld", (long)r.regs[0]);
                            else if (nr == 29)                     // ioctl
                                snprintf(pbuf, sizeof pbuf, " req=0x%lx fd=%ld", (unsigned long)r.regs[1], (long)r.regs[0]);
                            else if (nr == 56 || nr == 79)         // openat/newfstatat
                                read_tracee_str(pid, r.regs[1], pbuf, sizeof pbuf);
                            fprintf(g_trc, "[z2trc] pid=%d SYS nr=%ld%s\n", pid, nr, pbuf);
                        }
                    }
                    handle_syscall_entry(cfg, pid, st); st->at_exit = 1;
                }
                else if (st) { handle_syscall_exit(cfg, pid, st); st->at_exit = 0; }
                ptrace(PTRACE_SYSCALL, pid, 0, 0);
            }
            continue;
        }

        // fork/clone/vfork イベント。新規子を登録して再開。
        if (event == PTRACE_EVENT_FORK || event == PTRACE_EVENT_VFORK ||
            event == PTRACE_EVENT_CLONE) {
            unsigned long newpid = 0;
            ptrace(PTRACE_GETEVENTMSG, pid, 0, &newpid);
            state_for((pid_t)newpid);
            z_resume(pid, seccomp_mode, state_for(pid), 0);
            continue;
        }

        // TRACEFORK で生まれた新規子の最初の SIGSTOP(アタッチ由来の人工停止)。
        // 握り潰して再開しないと group-stop に化けてシェルの wait がハングする。
        {
            struct pid_state *nst = state_for(pid);
            if (nst && nst->started == 0) {
                nst->started = 1;
                // 新規子にもオプションを設定(継承されない環境への保険)。
                ptrace(PTRACE_SETOPTIONS, pid, 0, (void *)(long)opts);
                long d = (sig == SIGSTOP || sig == SIGTRAP) ? 0 : sig;
                z_resume(pid, seccomp_mode, nst, d);
                continue;
            }
        }

        // 子の最初の通常 SIGTRAP(=ブートストラップ execve の exec-stop)。seccomp が
        // 効いていれば既に EVENT_SECCOMP 経由で boot_done 済み。ここに -1 で来たら
        // seccomp 不発=フォールバック(全 syscall トレース)へ切り替える。
        if (seccomp_mode == -1 && pid == child && sig == SIGTRAP) {
            seccomp_mode = 0;
            boot_done = 1;
            struct pid_state *st = state_for(pid);
            if (st) st->at_exit = 0;
            ptrace(PTRACE_SYSCALL, pid, 0, 0);
            continue;
        }

        // group-stop(SIGSTOP/TSTP/TTIN/TTOU の 2 段目)は PTRACE_GETSIGINFO が EINVAL。
        // 尊重して再開しない(ジョブ制御 Ctrl+Z / tcsetpgrp の SIGTTOU を壊さない)。
        if (sig == SIGSTOP || sig == SIGTSTP || sig == SIGTTIN || sig == SIGTTOU) {
            siginfo_t si;
            if (ptrace(PTRACE_GETSIGINFO, pid, 0, &si) < 0) continue;
        }

        // Android seccomp が untrusted_app に禁ずる権限系 syscall(setfsuid 等)の
        // SIGSYS。配送せず戻り値 0(成功偽装)にして継続(fakeroot 方針)。
        if (sig == SIGSYS) {
            struct user_pt_regs regs;
            if (get_regs(pid, &regs) == 0) { regs.regs[0] = 0; set_regs(pid, &regs); }
            z_resume(pid, seccomp_mode, state_for(pid), 0);
            continue;
        }

        // それ以外(signal-delivery-stop / 通常シグナル)はそのまま転送。
        if (g_trace) {
            siginfo_t si; memset(&si, 0, sizeof si);
            int gr = ptrace(PTRACE_GETSIGINFO, pid, 0, &si);
            fprintf(g_trc, "[z2trc] pid=%d DELIVER sig=%d gr=%d si_code=%d si_pid=%d\n",
                    pid, sig, gr, gr==0?si.si_code:-999, gr==0?si.si_pid:-1);
        }
        long deliver = (sig == SIGTRAP) ? 0 : sig;
        z_resume(pid, seccomp_mode, state_for(pid), deliver);
    }
    return exit_code;
}

// ---- nativeLibraryDir 常駐の自前 ELF ローダ (--loader モード) -----------------
// Android API29+ の untrusted_app は app data 領域のファイルを execve できない(W^X /
// SELinux)。そこで execve できるのは nativeLibraryDir 常駐の本バイナリ(libz2root.so)
// 自身だけ。--loader モードでは、対象 ELF をファイルから読んで匿名 PROT_EXEC メモリへ
// 手動マップし、初期スタック(argc/argv/envp/auxv)を組んで entry へ jump する。
// execve も file-backed PROT_EXEC も使わないため W^X を回避できる(proot の
// libproot_loader 相当を自前 GPL-3.0 コードで実装)。
//
// マップ対象は「PT_INTERP を持たない ELF」に限る = 動的ローダ本体(ld-musl / ld.so。
// 自己再配置 PIE)か、静的 ELF。動的プログラムは plan_exec が ld 本体を target にする
// ため、ここでマップするのは常に ld 本体で、その後の共有ライブラリ群は ld が
// file-backed PROT_EXEC で mmap する(file-backed PROT_EXEC は untrusted_app でも許可。
// execve / execute だけが W^X で禁止される)。

#define PT_LOAD_Z   1
#define PT_PHDR_Z   6
#define PF_X_Z 1
#define PF_W_Z 2
#define PF_R_Z 4

#define AT_NULL_Z   0
#define AT_PHDR_Z   3
#define AT_PHENT_Z  4
#define AT_PHNUM_Z  5
#define AT_PAGESZ_Z 6
#define AT_BASE_Z   7
#define AT_FLAGS_Z  8
#define AT_ENTRY_Z  9
#define AT_UID_Z    11
#define AT_EUID_Z   12
#define AT_GID_Z    13
#define AT_EGID_Z   14
#define AT_HWCAP_Z  16
#define AT_CLKTCK_Z 17
#define AT_SECURE_Z 23
#define AT_RANDOM_Z 25
#define AT_HWCAP2_Z 26
#define AT_EXECFN_Z 31
#define AT_SYSINFO_EHDR_Z 33

__attribute__((noreturn))
static void loader_fail(const char *msg, const char *path) {
    fprintf(stderr, "z2root loader: %s(%s): %s\n", msg, path, strerror(errno));
    _exit(127);
}

// path の ELF(PT_INTERP 無し)を匿名 PROT_EXEC メモリへマップし、
// child_argv / child_envp で entry へ jump する。成功すれば戻らない。
__attribute__((noreturn))
static void load_elf_and_jump(const char *path, char **child_argv, char **child_envp) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) loader_fail("open", path);

    unsigned char eh[64];
    if (pread(fd, eh, sizeof(eh), 0) != (ssize_t)sizeof(eh)) loader_fail("read ehdr", path);
    if (memcmp(eh, "\x7f""ELF", 4) != 0) loader_fail("not ELF", path);
    // 64bit LE / aarch64 前提(既存方針)。
    unsigned long e_entry, e_phoff;
    unsigned short e_phentsize, e_phnum, e_type;
    memcpy(&e_type,      eh + 0x10, 2);
    memcpy(&e_entry,     eh + 0x18, 8);
    memcpy(&e_phoff,     eh + 0x20, 8);
    memcpy(&e_phentsize, eh + 0x36, 2);
    memcpy(&e_phnum,     eh + 0x38, 2);

    long pagesz = sysconf(_SC_PAGESIZE);
    if (pagesz <= 0) pagesz = 4096;
    unsigned long pmask = (unsigned long)pagesz - 1;

    // プログラムヘッダを読む(最大 MAX_PH 個)。
    #define MAX_PH 64
    static unsigned char ph[MAX_PH][56];
    if (e_phnum > MAX_PH) e_phnum = MAX_PH;
    for (unsigned i = 0; i < e_phnum; i++) {
        if (pread(fd, ph[i], 56, (off_t)(e_phoff + (unsigned long)i * e_phentsize)) != 56)
            loader_fail("read phdr", path);
    }

    // PT_LOAD の vaddr 範囲と PT_PHDR を求める。
    unsigned long min_va = ~0UL, max_va = 0, pt_phdr_va = 0;
    int has_pt_phdr = 0;
    for (unsigned i = 0; i < e_phnum; i++) {
        unsigned int p_type; memcpy(&p_type, ph[i], 4);
        if (p_type == PT_PHDR_Z) { memcpy(&pt_phdr_va, ph[i] + 16, 8); has_pt_phdr = 1; }
        if (p_type != PT_LOAD_Z) continue;
        unsigned long p_vaddr, p_memsz;
        memcpy(&p_vaddr, ph[i] + 16, 8);
        memcpy(&p_memsz, ph[i] + 40, 8);
        if (p_vaddr < min_va) min_va = p_vaddr;
        if (p_vaddr + p_memsz > max_va) max_va = p_vaddr + p_memsz;
    }
    if (min_va == ~0UL) loader_fail("no PT_LOAD", path);

    // ET_DYN(PIE)は連続領域を予約してから各セグメントを MAP_FIXED で埋める。
    // ET_EXEC は p_vaddr をそのまま使う(base=0)。
    unsigned long base = 0;
    if (e_type == 3 /* ET_DYN */) {
        unsigned long span = ((max_va + pmask) & ~pmask) - (min_va & ~pmask);
        void *resv = mmap(NULL, span, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (resv == MAP_FAILED) loader_fail("reserve", path);
        base = (unsigned long)resv - (min_va & ~pmask);
    }

    for (unsigned i = 0; i < e_phnum; i++) {
        unsigned int p_type, p_flags;
        memcpy(&p_type, ph[i], 4);
        if (p_type != PT_LOAD_Z) continue;
        memcpy(&p_flags, ph[i] + 4, 4);
        unsigned long p_offset, p_vaddr, p_filesz, p_memsz;
        memcpy(&p_offset, ph[i] + 8,  8);
        memcpy(&p_vaddr,  ph[i] + 16, 8);
        memcpy(&p_filesz, ph[i] + 32, 8);
        memcpy(&p_memsz,  ph[i] + 40, 8);

        unsigned long seg_start = (base + p_vaddr) & ~pmask;
        unsigned long seg_end   = (base + p_vaddr + p_memsz + pmask) & ~pmask;
        unsigned long off_in_pg = (base + p_vaddr) - seg_start;

        // file-backed PROT_EXEC は W^X で不可。匿名 RW で確保→中身を read→本来の保護へ。
        void *seg = mmap((void *)seg_start, seg_end - seg_start,
                         PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
        if (seg == MAP_FAILED) loader_fail("mmap seg", path);
        if (p_filesz > 0 &&
            pread(fd, (char *)seg_start + off_in_pg, p_filesz, (off_t)p_offset)
                != (ssize_t)p_filesz)
            loader_fail("read seg", path);
        int prot = ((p_flags & PF_R_Z) ? PROT_READ : 0) |
                   ((p_flags & PF_W_Z) ? PROT_WRITE : 0) |
                   ((p_flags & PF_X_Z) ? PROT_EXEC : 0);
        if (mprotect((void *)seg_start, seg_end - seg_start, prot) != 0)
            loader_fail("mprotect", path);
    }
    close(fd);

    unsigned long entry    = base + e_entry;
    unsigned long phdr_mem = has_pt_phdr ? (base + pt_phdr_va) : (base + e_phoff);

    int dbg = (getenv("Z2ROOT_LOADER_DEBUG") != NULL);
    if (dbg) {
        fprintf(stderr, "z2root loader: type=%u base=%lx e_entry=%lx entry=%lx "
                "phdr=%lx phnum=%u phent=%u has_ptphdr=%d\n",
                e_type, base, e_entry, entry, phdr_mem, e_phnum, e_phentsize, has_pt_phdr);
        fflush(stderr);
    }

    // ---- 初期スタックを現スタック上に構築して sp を切替え jump ----
    // 対象 ELF は PT_INTERP を持たない(ld 本体 or 静的)ので、カーネルが ld を直接
    // exec したのと同じ auxv を作る: AT_PHDR/ENTRY=この ELF 自身、AT_BASE=0。
    // これで musl/glibc の ld は "コマンドとして起動された" と判定し argv からプログラムを
    // 読み込む(argv は plan_exec が ["--argv0", <prog>, ...] を組んでいる)。
    int argc_n = 0; while (child_argv[argc_n]) argc_n++;
    int envc_n = 0; while (child_envp[envc_n]) envc_n++;

    unsigned long av[][2] = {
        { AT_PHDR_Z,   phdr_mem },
        { AT_PHENT_Z,  e_phentsize },
        { AT_PHNUM_Z,  e_phnum },
        { AT_PAGESZ_Z, (unsigned long)pagesz },
        { AT_BASE_Z,   0 },
        { AT_FLAGS_Z,  0 },
        { AT_ENTRY_Z,  entry },
        { AT_UID_Z,    getauxval(AT_UID_Z) },
        { AT_EUID_Z,   getauxval(AT_EUID_Z) },
        { AT_GID_Z,    getauxval(AT_GID_Z) },
        { AT_EGID_Z,   getauxval(AT_EGID_Z) },
        { AT_SECURE_Z, 0 },
        { AT_RANDOM_Z, getauxval(AT_RANDOM_Z) },
        { AT_HWCAP_Z,  getauxval(AT_HWCAP_Z) },
        { AT_HWCAP2_Z, getauxval(AT_HWCAP2_Z) },
        { AT_CLKTCK_Z, getauxval(AT_CLKTCK_Z) },
        { AT_SYSINFO_EHDR_Z, getauxval(AT_SYSINFO_EHDR_Z) },
        { AT_EXECFN_Z, (unsigned long)child_argv[0] },
    };
    int naux = (int)(sizeof(av) / sizeof(av[0]));

    // ワード数: argc + (argv..+NULL) + (envp..+NULL) + (auxv..*2 + AT_NULL ペア)
    int words = 1 + (argc_n + 1) + (envc_n + 1) + (naux * 2 + 2);

    // 現スタック下端に領域を確保(jump 後はこのフレームの直下=より低位へ伸びる)。
    // 16B 整列のため上方向に丸める(確保領域内に収める)。
    unsigned long ap = (unsigned long)alloca((size_t)words * 8 + 16);
    unsigned long *sp = (unsigned long *)((ap + 15) & ~15UL);

    unsigned long *w = sp;
    *w++ = (unsigned long)argc_n;
    for (int i = 0; i < argc_n; i++) *w++ = (unsigned long)child_argv[i];
    *w++ = 0;
    for (int i = 0; i < envc_n; i++) *w++ = (unsigned long)child_envp[i];
    *w++ = 0;
    for (int i = 0; i < naux; i++) { *w++ = av[i][0]; *w++ = av[i][1]; }
    *w++ = AT_NULL_Z; *w++ = 0;

    if (dbg) {
        fprintf(stderr, "z2root loader: argc=%d envc=%d words=%d sp=%p JUMPING\n",
                argc_n, envc_n, words, (void *)sp);
        fflush(stderr);
    }

    // sp を新フレームへ、x0=0(rtld_fini) で entry へ分岐。戻らない。
    __asm__ volatile(
        "mov sp, %0\n"
        "mov x0, #0\n"
        "br  %1\n"
        : : "r"(sp), "r"(entry) : "memory", "x0");
    __builtin_unreachable();
}

// --loader <elf> <argv0> [args...]: 自前ローダのエントリ。戻らない(失敗時のみ _exit)。
__attribute__((noreturn))
static void loader_main(int argc, char **argv) {
    if (getenv("Z2ROOT_LOADER_DEBUG")) {
        char b[256];
        int l = snprintf(b, sizeof(b), "z2root loader_main: argc=%d a1=%s a2=%s a3=%s\n",
                         argc, argc>1?argv[1]:"-", argc>2?argv[2]:"-", argc>3?argv[3]:"-");
        write(2, b, l);
    }
    if (argc < 4) {
        fprintf(stderr, "z2root loader: usage: --loader <elf> <argv0> [args...]\n");
        _exit(2);
    }
    load_elf_and_jump(argv[2], &argv[3], environ);
}

int main(int argc, char **argv) {
    // --loader モード(自分自身を nativeLibraryDir から execve して入る)を最優先で分岐。
    if (argc >= 2 && strcmp(argv[1], "--loader") == 0) loader_main(argc, argv);

    struct config cfg;
    char *const *command = parse_args(argc, argv, &cfg);
    cfg.command = command;

    // 自前ローダ経路: 自分(libz2root.so, nativeLibraryDir 常駐)の実パスを得る。
    // 取得できれば use_loader=1。Z2ROOT_NO_LOADER=1 で旧来の直接 execve 経路へ退避可。
    cfg.use_loader = 0;
    cfg.self_path[0] = '\0';
    if (!getenv("Z2ROOT_NO_LOADER")) {
        ssize_t sl = readlink("/proc/self/exe", cfg.self_path, sizeof(cfg.self_path) - 1);
        if (sl > 0) { cfg.self_path[sl] = '\0'; cfg.use_loader = 1; }
    }

    pid_t child = fork();
    if (child < 0) { perror("fork"); return 1; }
    if (child == 0) {
        _exit(run_child(&cfg));
    }
    return run_tracer(&cfg, child);
}
