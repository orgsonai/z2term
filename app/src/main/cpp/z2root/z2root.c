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
// ⚠️ 未ビルド・未検証のスケルトン。難所(getcwd 逆変換 / 2パス syscall / link2symlink /
//    /proc 偽装 / マルチスレッド)は TODO で明示。実機で小さく逐次検証して育てる。

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
    int link2symlink;                 // --link2symlink (受理のみ。TODO: 実装)
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

// syscall 番号 -> パス引数のレジスタindex。複数パス syscall は TODO (最小版は1パスのみ)。
// aarch64 は open/stat/access の素の形を持たず *at が中心。x0=arg0 ... x5=arg5。
static int path_arg_index(long nr) {
    switch (nr) {
        case 56:  return 1;  // openat       (dirfd, path, ...)
        case 437: return 1;  // openat2
        case 79:  return 1;  // newfstatat   (dirfd, path, ...)
        case 291: return 1;  // statx        (dirfd, path, ...)
        case 48:  return 1;  // faccessat
        case 439: return 1;  // faccessat2
        case 78:  return 1;  // readlinkat   (TODO: 戻り値のシンボリックリンク先逆変換)
        case 35:  return 1;  // unlinkat
        case 34:  return 1;  // mkdirat
        case 33:  return 1;  // mknodat
        case 53:  return 1;  // fchmodat
        case 54:  return 1;  // fchownat
        case 281: return 1;  // execveat
        case 221: return 0;  // execve       (path, argv, envp)
        case 49:  return 0;  // chdir        (path)   ※ getcwd 逆変換は未実装(TODO)
        case 43:  return 0;  // statfs
        default:  return -1;
    }
}

// syscall-entry で、必要ならパス引数をホスト実パスへ書き換える。
// 書き換えたパス文字列はトレーシのスタック下(sp - SCRATCH)へ置き、該当レジスタを差し替える。
// レッドゾーン確保のため sp から十分下げた所をスクラッチ基点にする。
#define SCRATCH_OFFSET 2048

// tracee の execve/execveat を傍受し、動的バイナリならローダ経由の起動へ書き換える。
// path_idx: パス引数のレジスタ index (execve=0, execveat=1)。argv は +1, envp は +2。
static void rewrite_execve(const struct config *cfg, pid_t pid,
                           struct user_pt_regs *regs, int path_idx) {
    unsigned long path_addr = regs->regs[path_idx];
    if (path_addr == 0) return;

    char guest_prog[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, guest_prog, sizeof(guest_prog)) < 0) return;

    char real_guest[PATH_MAX_Z];
    resolve_guest_symlink(cfg, guest_prog, real_guest, sizeof(real_guest));
    char host_prog[PATH_MAX_Z];
    if (!translate_abs(cfg, real_guest, host_prog, sizeof(host_prog)))
        snprintf(host_prog, sizeof(host_prog), "%s", real_guest);

    char interp[PATH_MAX_Z];
    int dyn = read_elf_interp(host_prog, interp, sizeof(interp));

    if (dyn != 1) {
        // 静的 ELF / スクリプト等: パス引数だけをホスト実パスへ差し替える。
        // (#! スクリプトのインタプリタ書き換えは未対応 = TODO)
        unsigned long scratch = regs->sp - SCRATCH_OFFSET;
        size_t len = strlen(host_prog) + 1;
        if (write_tracee_mem(pid, scratch, host_prog, len) == 0) {
            regs->regs[path_idx] = scratch;
            set_regs(pid, regs);
        }
        return;
    }

    char host_loader[PATH_MAX_Z];
    if (!translate_abs(cfg, interp, host_loader, sizeof(host_loader)))
        snprintf(host_loader, sizeof(host_loader), "%s", interp);

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

    // 新 argv: [host_loader, "--argv0", <元argv0>, host_prog, <元args[1..]>]
    // busybox は argv0 の basename で applet を判定するため、--argv0 で元 argv0 を保つ。
    const char *parts[MAX_ARGS + 4];
    int pc = 0;
    parts[pc++] = host_loader;
    parts[pc++] = "--argv0";
    parts[pc++] = (n > 0) ? args[0] : real_guest;
    parts[pc++] = host_prog;
    for (int j = 1; j < n && pc < MAX_ARGS + 3; j++) parts[pc++] = args[j];

    // tracee スタック下に [文字列 blob][8B align][ポインタ配列 + NULL] を配置。
    size_t blob_sz = 0;
    for (int j = 0; j < pc; j++) blob_sz += strlen(parts[j]) + 1;
    size_t total = blob_sz + 8 + (size_t)(pc + 1) * 8;
    unsigned long base = (regs->sp - SCRATCH_OFFSET - total) & ~15UL;

    char blob[8192];
    if (blob_sz <= sizeof(blob)) {
        unsigned long ptrs[MAX_ARGS + 4];
        size_t boff = 0;
        for (int j = 0; j < pc; j++) {
            size_t l = strlen(parts[j]) + 1;
            memcpy(blob + boff, parts[j], l);
            ptrs[j] = base + boff;
            boff += l;
        }
        unsigned long arr = (base + boff + 7) & ~7UL;
        unsigned long nullp = 0;
        if (write_tracee_mem(pid, base, blob, boff) == 0) {
            int ok = 1;
            for (int j = 0; j < pc && ok; j++)
                ok = (write_tracee_mem(pid, arr + (unsigned long)j * 8, &ptrs[j], 8) == 0);
            if (ok) ok = (write_tracee_mem(pid, arr + (unsigned long)pc * 8, &nullp, 8) == 0);
            if (ok) {
                regs->regs[path_idx] = ptrs[0];
                regs->regs[path_idx + 1] = arr;
                set_regs(pid, regs);
            }
        }
    }

    for (int j = 0; j < n; j++) free(args[j]);
}

// syscall-exit で getcwd(17) の戻りバッファに入ったホスト実パスをゲストパスへ逆変換する。
// (未実装だと cwd がホストパス /<rootfs>/... を露出し、$PWD やプロンプトが壊れる)
// bind の逆変換は最小版では未対応 = TODO。
static void rewrite_getcwd_result(const struct config *cfg, pid_t pid, unsigned long buf) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (ret <= 0) return;  // getcwd 失敗

    char host[PATH_MAX_Z];
    if (read_tracee_str(pid, buf, host, sizeof(host)) < 0) return;
    if (strncmp(host, cfg->rootfs, cfg->rootfs_len) != 0) return;

    const char *guest = host + cfg->rootfs_len;
    char g[PATH_MAX_Z];
    if (guest[0] == '\0') snprintf(g, sizeof(g), "/");
    else snprintf(g, sizeof(g), "%s", guest);

    size_t len = strlen(g) + 1;
    if (write_tracee_mem(pid, buf, g, len) == 0) {
        regs.regs[0] = len;  // getcwd は書き込みバイト数(NUL含む)を返す
        set_regs(pid, &regs);
    }
}

static void maybe_rewrite_path(const struct config *cfg, pid_t pid, struct user_pt_regs *regs) {
    long nr = (long)regs->regs[8];
    if (nr == 221) { rewrite_execve(cfg, pid, regs, 0); return; }  // execve
    if (nr == 281) { rewrite_execve(cfg, pid, regs, 1); return; }  // execveat

    int idx = path_arg_index(nr);
    if (idx < 0) return;

    unsigned long path_addr = regs->regs[idx];
    if (path_addr == 0) return;

    char guest[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, guest, sizeof(guest)) < 0) return;

    char host[PATH_MAX_Z];
    if (!translate_abs(cfg, guest, host, sizeof(host))) return;

    // スタック下のスクラッチ領域へ書き、レジスタを差し替える。syscall 実行中は
    // カーネルがユーザスタックを使わないため sp 直下への書き込みは安全。
    unsigned long scratch = regs->sp - SCRATCH_OFFSET;
    size_t len = strlen(host) + 1;
    if (write_tracee_mem(pid, scratch, host, len) != 0) return;
    regs->regs[idx] = scratch;
    set_regs(pid, regs);
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
    // ローダ差し替えを自前で行う。動的バイナリは PT_INTERP(ローダ)を rootfs 内へ
    // 向けて明示起動しないと、カーネルがホスト / からローダを解決して ENOENT になる。
    const char *guest_cmd = cfg->command[0];
    char real_guest[PATH_MAX_Z];
    resolve_guest_symlink(cfg, guest_cmd, real_guest, sizeof(real_guest));
    char host_cmd[PATH_MAX_Z];
    if (!translate_abs(cfg, real_guest, host_cmd, sizeof(host_cmd)))
        snprintf(host_cmd, sizeof(host_cmd), "%s", real_guest);

    char interp[PATH_MAX_Z];
    if (read_elf_interp(host_cmd, interp, sizeof(interp)) == 1) {
        char host_loader[PATH_MAX_Z];
        if (!translate_abs(cfg, interp, host_loader, sizeof(host_loader)))
            snprintf(host_loader, sizeof(host_loader), "%s", interp);
        int n = 0;
        while (cfg->command[n]) n++;
        // [loader, "--argv0", <元argv0>, host_cmd, <元args[1..]>, NULL]
        char **nv = malloc((size_t)(n + 5) * sizeof(char *));
        if (nv) {
            int k = 0;
            nv[k++] = host_loader;
            nv[k++] = "--argv0";
            nv[k++] = (char *)cfg->command[0];
            nv[k++] = host_cmd;
            for (int j = 1; j < n; j++) nv[k++] = (char *)cfg->command[j];
            nv[k] = NULL;
            execve(host_loader, nv, environ);
            free(nv);  // execve 失敗時のみ到達
        }
    }
    // 静的バイナリ or ローダ起動失敗時のフォールバック。
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
                        st->aux_addr = (st->entry_nr == 17) ? regs.regs[0] : 0;  // getcwd buf
                        maybe_rewrite_path(cfg, pid, &regs);
                    }
                    st->at_exit = 1;
                } else if (st) {
                    // syscall-exit: 戻り値の逆変換。
                    // TODO: readlinkat のリンク先逆変換もここに足す。
                    if (st->entry_nr == 17 && st->aux_addr) {
                        rewrite_getcwd_result(cfg, pid, st->aux_addr);
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
