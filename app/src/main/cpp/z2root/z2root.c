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
//    link2symlink(linkat→symlinkat。Android FS の link() EACCES を symlink で回避)。
// 残り難所(readlinkat 戻り値逆変換 / /proc 偽装 /
//    マルチスレッド境界の厳密化)は TODO で明示。実機で小さく逐次検証して育てる。

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <asm/ptrace.h>  // struct user_pt_regs (aarch64 uapi)
#include <linux/elf.h>   // NT_PRSTATUS

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
};

// ---- pid -> syscall entry/exit トグル の簡易マップ ----------------------------
// PTRACE_O_TRACESYSGOOD でも entry/exit の区別はトレーサ側で管理する必要がある。
// マルチスレッド(clone)では entry/exit がスレッド単位で交錯しうる。最小版は
// pid(tid) 単位で保持する。TODO: スレッド境界の厳密化。

#define MAP_CAP 256
struct pid_state {
    pid_t pid;
    int at_exit;            // 0: 次は syscall-entry, 1: 次は syscall-exit
    int used;
    long entry_nr;          // entry で記録した syscall 番号 (exit 時の戻り値逆変換用)
    unsigned long aux_addr; // getcwd 等の対象バッファアドレス
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
static void canonicalize_guest(const struct config *cfg, const char *in,
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
        snprintf(guest_abs, sizeof(guest_abs), "%s", in_path);
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
    canonicalize_guest(cfg, guest_abs, deref_final, resolved, sizeof(resolved));
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
#define PLAN_MAX_PREFIX 8
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

// guest_prog を resolve し host 実パスへ。orig_argv0 は ELF バイナリ起動時の argv0。
// pid は相対 exec パスを /proc/<pid>/cwd で絶対化するため(run_child は自身の pid)。
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
        plan_push(plan, host_prog);
        return 0;
    }

    // 3) 静的 ELF / その他: パスだけ host へ、argv0 はそのまま。
    snprintf(plan->target, sizeof(plan->target), "%s", host_prog);
    plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
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
    plan_exec(cfg, pid, guest_prog, (n > 0) ? args[0] : guest_prog, &plan);

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

// fakeroot(-0): syscall-exit で uid/gid 関連の戻り値・構造体を root(0) に偽装する。
// proot の -0 相当。ホストのアプリ uid/gid がゲストへ露出するのを防ぎ、root 前提の
// パッケージ操作(apk/apt の chown 等)が EPERM で失敗しないよう成功に見せる。
//   - getuid/geteuid/getgid/getegid → 0
//   - getgroups → 補助グループ 0 個(ホスト gid の露出を消す。id の groups が root だけになる)
//   - set*id / fchownat / fchown が EPERM 等で失敗したら成功(0)に握りつぶす
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
        // set*id / chown 系: 失敗(EPERM 等)を成功(0)へ。ホスト権限は実際には変わらない。
        case 143: case 144: case 145: case 146: case 147: case 149:
        case 151: case 152: case 159: case 54: case 55:
            if (ret < 0) { regs.regs[0] = 0; set_regs(pid, &regs); }
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

static void maybe_rewrite_path(const struct config *cfg, pid_t pid, struct user_pt_regs *regs) {
    long nr = (long)regs->regs[8];
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
    if (cfg->rootfs[0] == '\0' || i >= argc) usage_die(argv[0]);
    cfg->rootfs_len = strlen(cfg->rootfs);
    // 末尾の "/" は剥がす (二重スラッシュ防止)。
    while (cfg->rootfs_len > 1 && cfg->rootfs[cfg->rootfs_len - 1] == '/') {
        cfg->rootfs[--cfg->rootfs_len] = '\0';
    }
    return &argv[i];
}

// ---- 子プロセス: TRACEME してゲストコマンドを exec ----------------------------

static int run_child(const struct config *cfg) {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) != 0) {
        perror("PTRACE_TRACEME");
        return 127;
    }
    // ゲスト視点の cwd へ移動 (ホスト実パスへ変換してから chdir)。
    char host_cwd[PATH_MAX_Z];
    if (translate_abs(cfg, cfg->cwd, host_cwd, sizeof(host_cwd))) {
        if (chdir(host_cwd) != 0) chdir(cfg->rootfs);
    }

    // 最初の execve はトレーサがまだ傍受していない(このプロセス自身の呼び出し)ので、
    // ローダ差し替え / シバン解決を自前で行う。動的バイナリは PT_INTERP(ローダ)を
    // rootfs 内へ向けて明示起動しないと、カーネルがホスト / からローダを解決して ENOENT。
    int n = 0;
    while (cfg->command[n]) n++;

    struct exec_plan plan;
    if (plan_exec(cfg, getpid(), cfg->command[0], cfg->command[0], &plan) == 0) {
        // [prefix..., <元args[orig_start..]>, NULL]
        char **nv = malloc((size_t)(plan.nprefix + n + 1) * sizeof(char *));
        if (nv) {
            int k = 0;
            for (int j = 0; j < plan.nprefix; j++) nv[k++] = plan.prefix[j];
            for (int j = plan.orig_start; j < n; j++) nv[k++] = (char *)cfg->command[j];
            nv[k] = NULL;
            execve(plan.target, nv, environ);
            free(nv);  // execve 失敗時のみ到達
        }
    }
    // フォールバック: パスだけ host へ変換して素直に exec。
    char real_guest[PATH_MAX_Z];
    resolve_guest_symlink(cfg, cfg->command[0], real_guest, sizeof(real_guest));
    char host_cmd[PATH_MAX_Z];
    if (!translate_abs(cfg, real_guest, host_cmd, sizeof(host_cmd)))
        snprintf(host_cmd, sizeof(host_cmd), "%s", real_guest);
    execve(host_cmd, cfg->command, environ);
    fprintf(stderr, "z2root: execve(%s) failed: %s\n", host_cmd, strerror(errno));
    return 127;
}

// ---- 親プロセス: ptrace ループ ------------------------------------------------

static int run_tracer(const struct config *cfg, pid_t child) {
    int status;
    // 最初の停止 (exec トラップ) を待つ。
    if (waitpid(child, &status, 0) < 0) { perror("waitpid"); return 1; }

    int opts = PTRACE_O_TRACESYSGOOD | PTRACE_O_TRACEFORK |
               PTRACE_O_TRACEVFORK | PTRACE_O_TRACECLONE;
    if (cfg->kill_on_exit) opts |= PTRACE_O_EXITKILL;
    ptrace(PTRACE_SETOPTIONS, child, 0, (void *)(long)opts);

    state_for(child);
    ptrace(PTRACE_SYSCALL, child, 0, 0);

    int exit_code = 0;
    int alive = 1;
    while (alive > 0) {
        pid_t pid = waitpid(-1, &status, __WALL);
        if (pid < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (WIFEXITED(status) || WIFSIGNALED(status)) {
            if (pid == child) exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
            state_drop(pid);
            if (pid == child) alive = 0;
            continue;
        }

        if (WIFSTOPPED(status)) {
            int sig = WSTOPSIG(status);
            // syscall stop (TRACESYSGOOD なら SIGTRAP|0x80)
            if (sig == (SIGTRAP | 0x80)) {
                struct pid_state *st = state_for(pid);
                if (st && st->at_exit == 0) {
                    struct user_pt_regs regs;
                    if (get_regs(pid, &regs) == 0) {
                        st->entry_nr = (long)regs.regs[8];
                        unsigned long aux = 0;
                        if (st->entry_nr == 17) aux = regs.regs[0];  // getcwd buf
                        else if (cfg->fake_root) switch (st->entry_nr) {  // stat 出力バッファ
                            case 80:  aux = regs.regs[1]; break;  // fstat
                            case 79:  aux = regs.regs[2]; break;  // newfstatat
                            case 291: aux = regs.regs[4]; break;  // statx
                        }
                        st->aux_addr = aux;
                        maybe_rewrite_path(cfg, pid, &regs);
                    }
                    st->at_exit = 1;
                } else if (st) {
                    // syscall-exit: 戻り値・構造体の逆変換 / 偽装。
                    if (st->entry_nr == 17 && st->aux_addr) {
                        rewrite_getcwd_result(cfg, pid, st->aux_addr);
                    } else if (cfg->fake_root) {
                        fake_root_on_exit(pid, st->entry_nr, st->aux_addr);
                    }
                    st->at_exit = 0;
                }
                ptrace(PTRACE_SYSCALL, pid, 0, 0);
                continue;
            }
            // fork/clone/exec イベント。新規子は自動で停止しているので継続再開。
            int event = (status >> 16) & 0xff;
            if (event == PTRACE_EVENT_FORK || event == PTRACE_EVENT_VFORK ||
                event == PTRACE_EVENT_CLONE) {
                unsigned long newpid = 0;
                ptrace(PTRACE_GETEVENTMSG, pid, 0, &newpid);
                state_for((pid_t)newpid);
                ptrace(PTRACE_SYSCALL, pid, 0, 0);
                continue;
            }
            // それ以外のシグナルはそのまま転送 (group-stop 等)。
            int deliver = (sig == SIGTRAP) ? 0 : sig;
            ptrace(PTRACE_SYSCALL, pid, 0, (void *)(long)deliver);
            continue;
        }
    }
    return exit_code;
}

int main(int argc, char **argv) {
    struct config cfg;
    char *const *command = parse_args(argc, argv, &cfg);
    cfg.command = command;

    pid_t child = fork();
    if (child < 0) { perror("fork"); return 1; }
    if (child == 0) {
        _exit(run_child(&cfg));
    }
    return run_tracer(&cfg, child);
}
