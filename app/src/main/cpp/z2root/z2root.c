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
#include <sys/stat.h>    // lstat/stat/struct stat (link2symlink のコピーfallback)
#include <sys/sysmacros.h> // major/minor/makedev (コピー fallback の inode 偽装)
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
    uid_t real_uid;                   // トレーサ(=Android アプリ)の実 uid。fake_root で getuid を 0 に偽装しても
    gid_t real_gid;                   // SCM_CREDENTIALS は実 uid/gid でしか送れない(EPERM 回避)ため保持する。
};

// ---- pid -> syscall entry/exit トグル の簡易マップ ----------------------------
// PTRACE_O_TRACESYSGOOD でも entry/exit の区別はトレーサ側で管理する必要がある。
// マルチスレッド(clone)では entry/exit がスレッド単位で交錯しうる。最小版は
// pid(tid) 単位で保持する。TODO: スレッド境界の厳密化。

#define MAP_CAP 256
#define STATUS_FD_MAX 16    // 同時に追跡する /proc 偽装 fd の上限(超過分は非追跡)
#define STATUS_BUF_MAX 16384 // /proc status/loginuid を読み込む temp 用バッファ上限(status は通常 ~2KB)
#define CMDLINE_MAX 4096    // /proc/<pid>/cmdline 控え上限(NUL 連結 argv。カーネルも arg_end-arg_start 制限あり)
#define TASK_COMM_LEN 16    // /proc/<pid>/comm の上限(カーネル定数 = 15 文字 + NUL)
// proc 偽装 fd の種別(read 時にどの偽装を当てるか)。
#define PROC_FD_NONE     0
#define PROC_FD_STATUS   1  // /proc/.../status (Uid/Gid/Groups/Cap*/Name を root 一貫 + argv0 basename に)
#define PROC_FD_LOGINUID 2  // /proc/.../loginuid (監査ログイン uid を 0 に)
#define PROC_FD_CMDLINE  3  // /proc/.../cmdline (ローダラッパー漏れを元 argv へ差し替え)
#define PROC_FD_COMM     4  // /proc/.../comm (libz2root.so 漏れを argv0 basename へ差し替え)
#define PROC_FD_STAT     5  // /proc/<pid>/stat の field 2 "(libz2root.so)" を argv0 basename へ
                            // (busybox ps 等は速度のため status/comm でなく stat field 2 を読む)
struct pid_state {
    pid_t pid;
    int at_exit;            // 0: 次は syscall-entry, 1: 次は syscall-exit
    int used;
    int started;            // 0: まだ最初の停止(TRACEFORK 由来の初期 SIGSTOP)を消化していない
    long entry_nr;          // entry で記録した syscall 番号 (exit 時の戻り値逆変換用)
    unsigned long aux_addr; // getcwd 等の対象バッファアドレス
    unsigned long aux_len;  // readlinkat の bufsiz(戻りバッファ逆変換でホストパス長の上限に使う)
    char aux_path[PATH_MAX_Z]; // readlinkat 対象 symlink のホスト実パス(exit で自前 readlink し直す用, 空=未確定)
    int aux_kind;           // read entry で控えた追跡 fd の種別(PROC_FD_*, exit で偽装を分岐)
    int pending_open_kind;  // fakeroot: openat entry で偽装対象 proc パスを検出した種別(exit で fd を採取)
    int status_fds[STATUS_FD_MAX];      // fakeroot: 偽装対象 proc を指す fd 群, -1=空
    int status_fd_kind[STATUS_FD_MAX];  // 上記 fd の種別(PROC_FD_*), status_fds と添字対応
    int subst_active;       // readfree: openat で /proc を temp 差し替え中(exit で temp を unlink)
    int link_pending;       // link2symlink: linkat を実ハードリンクで試行中(exit で失敗ならコピーfallback)
    int link_follow;        // 上記 linkat の AT_SYMLINK_FOLLOW(コピー時に symlink を辿るか)
    char link_oldhost[PATH_MAX_Z];  // 同 old のホスト実パス(コピー元)
    char link_newhost[PATH_MAX_Z];  // 同 new のホスト実パス(コピー先)
    int linkcopy_hit;       // entry で stat 対象パスがコピー先と一致した linkcopy 添字(-1=非該当)
    char exe_guest[PATH_MAX_Z];  // /proc/<pid>/exe をゲスト視点で返すための execve 済みプログラム(ゲスト絶対)
    int aux_is_self_exe;    // readlinkat entry で「対象が /proc/<own>/exe」と判定したフラグ(exit で exe_guest を返す)
    char proc_cmdline[CMDLINE_MAX]; // /proc/<pid>/cmdline 用に控えた元 argv(NUL 連結。length 保存・NUL 終端不要)
    size_t proc_cmdline_len;        // proc_cmdline の有効バイト数(0=未記録)
    char proc_comm[TASK_COMM_LEN];  // /proc/<pid>/comm 用 argv0 basename(NUL 終端、最大 15 文字)
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
    g_map[free_slot].link_pending = 0;
    g_map[free_slot].exe_guest[0] = '\0';
    g_map[free_slot].aux_is_self_exe = 0;
    g_map[free_slot].proc_cmdline_len = 0;
    g_map[free_slot].proc_comm[0] = '\0';
    for (int k = 0; k < STATUS_FD_MAX; k++) {
        g_map[free_slot].status_fds[k] = -1;
        g_map[free_slot].status_fd_kind[k] = PROC_FD_NONE;
    }
    return &g_map[free_slot];
}

// 既存スロットの検索のみ(無ければ NULL)。host_path_for のパス変換ホットパスから
// 呼ぶための非破壊版。state_for は free スロットを掴んでしまうので使えない。
static struct pid_state *state_lookup(pid_t pid) {
    for (int i = 0; i < MAP_CAP; i++)
        if (g_map[i].used && g_map[i].pid == pid) return &g_map[i];
    return NULL;
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

// process_vm_writev が EFAULT した時のフォールバック。PTRACE_POKEDATA は kernel の
// __access_remote_vm 経由で、vma が無ければ expand_stack() を呼んでスタックを grow して
// から書く。一方 process_vm_writev(GUP) は grow しないため、sp 直下でも sp がページ境界
// 付近に来た起動初期では未 grow の下位ページで EFAULT になる(間欠 cannot-open / ZLE 未ロード)。
// word 境界外の端バイトは PEEKDATA で読み戻してマージし保全する。
static int poke_write_tracee_mem(pid_t pid, unsigned long addr, const void *buf, size_t len) {
    const unsigned long WS = sizeof(long);
    const unsigned char *src = (const unsigned char *)buf;
    unsigned long start = addr & ~(WS - 1);
    unsigned long stop  = (addr + len + WS - 1) & ~(WS - 1);
    for (unsigned long a = start; a < stop; a += WS) {
        unsigned long word = 0;
        if (a < addr || a + WS > addr + len) {   // 端ワード: 未書込バイトを既存内容で埋める
            errno = 0;
            long peek = ptrace(PTRACE_PEEKDATA, pid, (void *)a, (void *)0);
            if (peek == -1 && errno != 0) return -1;
            word = (unsigned long)peek;
        }
        unsigned char *wb = (unsigned char *)&word;
        for (unsigned long i = 0; i < WS; i++) {
            unsigned long cur = a + i;
            if (cur >= addr && cur < addr + len) wb[i] = src[cur - addr];
        }
        if (ptrace(PTRACE_POKEDATA, pid, (void *)a, (void *)word) != 0) return -1;
    }
    return 0;
}

static int write_tracee_mem(pid_t pid, unsigned long addr, const void *buf, size_t len) {
    struct iovec local = { (void *)buf, len };
    struct iovec remote = { (void *)addr, len };
    ssize_t n = process_vm_writev(pid, &local, 1, &remote, 1, 0);
    if (n == (ssize_t)len) return 0;
    // 速い経路(process_vm_writev)が未 grow 下位ページ等で失敗 → POKEDATA で grow しつつ書く。
    return poke_write_tracee_mem(pid, addr, buf, len);
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
// NT_ARM_SYSTEM_CALL regset を書く必要がある。
// (現状は未使用だが、syscall を別 syscall へ化かす基盤プリミティブなので温存する。)
__attribute__((unused))
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

    // bind 優先。重なり合う bind (例: /root と /root/.claude/downloads) では
    // 最長一致 = 最も具体的な bind を採用する。登録順の最初一致だと先に登録した
    // 親 bind (/root) が子 bind (/root/.claude/downloads) を覆い隠し、distro 別の
    // 隔離オーバーレイが効かず musl↔glibc の本体上書きが起きる (項目4 再発の真因)。
    const struct bind_entry *best = NULL;
    for (int i = 0; i < cfg->nbinds; i++) {
        const struct bind_entry *b = &cfg->binds[i];
        if (strncmp(guest_path, b->guest, b->guest_len) == 0 &&
            (guest_path[b->guest_len] == '/' || guest_path[b->guest_len] == '\0')) {
            if (best == NULL || b->guest_len > best->guest_len) best = b;
        }
    }
    if (best != NULL) {
        snprintf(out, cap, "%s%s", best->host, guest_path + best->guest_len);
        return 1;
    }
    snprintf(out, cap, "%s%s", cfg->rootfs, guest_path);
    return 1;
}

// ホスト実パス host を、ゲスト視点の絶対パスへ逆変換して buf へ。
//  1) bind.host 配下 → bind.guest + 残り。 2) rootfs 配下 → 残り。
//  3) /files/distros/<name>/ パターンからゲストパスを復元(stale prefix 救済)。
//  4) いずれでもない → そのまま(ホスト=ゲストとみなす)。戻り値は buf。
static const char *host_to_guest(const struct config *cfg, const char *host,
                                 char *buf, size_t cap) {
    // 逆変換も最長一致。重なり合う host 側 prefix で最も具体的な bind を選ぶ
    // (translate_abs と対称。隔離オーバーレイの host 実体を正しくゲストへ戻すため)。
    const struct bind_entry *best = NULL;
    size_t best_hl = 0;
    for (int i = 0; i < cfg->nbinds; i++) {
        const struct bind_entry *b = &cfg->binds[i];
        size_t hl = strlen(b->host);
        if (strncmp(host, b->host, hl) == 0 && (host[hl] == '/' || host[hl] == '\0')) {
            if (best == NULL || hl > best_hl) { best = b; best_hl = hl; }
        }
    }
    if (best != NULL) {
        snprintf(buf, cap, "%s%s", best->guest, host + best_hl);
        return buf;
    }
    if (strncmp(host, cfg->rootfs, cfg->rootfs_len) == 0 &&
        (host[cfg->rootfs_len] == '/' || host[cfg->rootfs_len] == '\0')) {
        const char *g = host + cfg->rootfs_len;
        snprintf(buf, cap, "%s", g[0] ? g : "/");
        return buf;
    }

    // proot --link2symlink が残した .l2s symlink は作成時のホスト絶対パスを
    // 保持する。Android OS のメジャーバージョンアップ(例 15→16)で data
    // ディレクトリの絶対 prefix 正規化(/data/data ↔ /data/user/0 等)が変わると、
    // .l2s が抱える絶対 prefix が現在の rootfs と食い違い、上の rootfs/bind 直接
    // 照合が外れる。stale な絶対パスを素通し→translate_abs が rootfs を二重前置
    // →ENOENT となり、shell やライブラリ(.l2s 多段 symlink)が "cannot open
    // shared object file" で起動不能になる(OS 15→16 で zsh が起動不能の実機報告)。
    // host の中の rootfs ディレクトリ構造マーカー "/files/distros/<name>/" を
    // 手掛かりに、prefix に依らずゲストパスを復元する。realpath は dangling な
    // stale パスには効かず、かつ syscall walk で重いので用いない。これは純粋な
    // 文字列処理で、該当しなければ下の素通しへ落ちる。
    {
        const char *marker = strstr(host, "/files/distros/");
        if (marker) {
            const char *after = marker + 15;       // strlen("/files/distros/")
            const char *slash = strchr(after, '/'); // <name> の直後
            if (slash) {
                snprintf(buf, cap, "%s", slash);
                return buf;
            }
        }
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
                    // proot/旧 z2root の --link2symlink が残したレガシー .l2s チェーンは
                    // リンク先に「ホスト実パス」(例 .../shared_home/android-sdk/… )を格納する。
                    // これをそのままゲストとして walk すると translate_abs が rootfs を二重
                    // 前置して ENOENT になり、NDK の libc++_shared.so 等(.l2s 多段 symlink)を
                    // open で辿れなかった。絶対リンク先が host(rootfs/bind の host 側)なら
                    // ゲストへ逆変換する。host_to_guest は該当しなければ素通しなので通常の
                    // ゲスト絶対 symlink には無害。
                    char glink[PATH_MAX_Z];
                    if (link[0] == '/')
                        host_to_guest(cfg, link, glink, sizeof(glink));
                    else
                        snprintf(glink, sizeof(glink), "%s", link);
                    char rest[PATH_MAX_Z];
                    snprintf(rest, sizeof(rest), "%s", pending + pi);
                    // 絶対 symlink: ルートから。相対 symlink: 親(=現 result)基準。
                    if (glink[0] == '/') result[0] = '\0';
                    if (rest[0])
                        snprintf(pending, sizeof(pending), "%s/%s", glink, rest);
                    else
                        snprintf(pending, sizeof(pending), "%s", glink);
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

        // /proc/<own pid>/exe(self/exe・thread-self/exe は上の self→pid 展開後にここへ来る)は
        // カーネルの実 symlink がトレーシ自身が execve した ELF ではなく、z2root のローダ
        // (libz2root.so) や ホスト側ローダを指している。Go ランタイム等が起動時に
        // /proc/self/exe を open/stat/readlink して libbacktrace 用に自プログラムへ辿るが、
        // libz2root.so をそのまま返すと open は ENOENT(host_to_guest で rootfs+host 二重前置)に
        // なり、Go は "libbacktrace could not find executable to open" で panic する(/proc/self/cwd
        // と同じ思想の修正。cwd は §host_path_for で対応済)。execve 時に記録した exe_guest
        // (ゲスト絶対パス)へ差し替えて、open/stat/readlink を本来のプログラムへ向ける。
        {
            char self_exe_pat[64];
            snprintf(self_exe_pat, sizeof(self_exe_pat), "/proc/%d/exe", (int)pid);
            if (strcmp(guest_abs, self_exe_pat) == 0) {
                struct pid_state *st = state_lookup(pid);
                if (st && st->exe_guest[0])
                    snprintf(guest_abs, sizeof(guest_abs), "%s", st->exe_guest);
            }
        }
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
        case 45:  p[out->n++] = (struct path_arg){0, -1, 1, -1, 0, 0}; break;             // truncate (path arg0, dirfd 無し, follow。ftruncate(46) は fd なので対象外)
        case 5: case 8: case 11: case 14:   // setxattr/getxattr/listxattr/removexattr (path arg0, follow)
            p[out->n++] = (struct path_arg){0, -1, 1, -1, 0, 0}; break;
        case 6: case 9: case 12: case 15:   // lsetxattr/lgetxattr/llistxattr/lremovexattr (path arg0, no-follow)
            p[out->n++] = (struct path_arg){0, -1, 0, -1, 0, 0}; break;
        case 264:  // name_to_handle_at(dirfd, path, handle, mnt_id, flags) path arg1, dirfd arg0
            // 既定は最終 symlink を辿らない。AT_SYMLINK_FOLLOW(0x400) 指定時のみ follow(linkat 同様)。
            // open_by_handle_at(265) は path ではなく不透明 file_handle を取るため変換不可。
            // かつ CAP_DAC_READ_SEARCH 必須=untrusted_app では EPERM で弾かれるため非対象。
            p[out->n++] = (struct path_arg){1, 0, 0, 4, 0x400, 0}; break;
        default: return 0;
    }
    return 1;
}

// syscall-entry で、必要ならパス引数をホスト実パスへ書き換える。
// 書き換えたパス文字列はトレーシのスタック下(sp 直下)へ置き、該当レジスタを差し替える。
// scratch は sp 直下に置く。process_vm_writev はリモート書込時にスタックを grow しない
// (kernel 6.x)ため、sp がページ境界付近に来た起動初期は base が未 grow の下位ページへ
// 落ちて EFAULT になり得る(間欠 "cannot open shared object file" / zsh ZLE 未ロード)。
// その救済は write_tracee_mem の POKEDATA フォールバック(expand_stack で grow)が担う。
// scratch_base のページ境界クランプは「速い経路 process_vm_writev のヒット率」を上げる
// 最適化で、外れても POKEDATA が確実に書く(sp 境界丁度=救済不能だった残ケースも解消)。
#define SCRATCH_OFFSET 16

// sp を含む present ページのサイズ。main で sysconf により設定(4K/16K 端末差に追従)。
static unsigned long g_pagesize = 4096;

// scratch 文字列(total バイト)を置く tracee アドレスを返す。
// 既定は sp 直下 (sp - SCRATCH_OFFSET - total)。base が present ページ境界を割ると速い経路
// (process_vm_writev)は EFAULT になるので、total が収まるならページ境界へ引き上げて
// process_vm_writev のヒット率を上げる(最適化)。収まらず下位ページへ落ちても
// write_tracee_mem が POKEDATA でフォールバックして確実に書く。
static unsigned long scratch_base(unsigned long sp, size_t total) {
    unsigned long floor = sp & ~(g_pagesize - 1);
    unsigned long base = (sp - SCRATCH_OFFSET - total) & ~15UL;
    if (base < floor && (sp - floor) >= total) base = floor;
    return base;
}

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
// skip_reloc=1: 対象 ELF が自己 relocation する(ld.so 本体など)ため loader 側の
//   RELATIVE/RELR 肩代わりを抑止する(二重 relocation 防止)。動的バイナリ起動経路で使う。
static void wrap_with_loader(const struct config *cfg, struct exec_plan *plan, int skip_reloc) {
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
    plan_push(plan, skip_reloc ? "--loader-noreloc" : "--loader");
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

// ELF の e_type を返す(2=ET_EXEC, 3=ET_DYN)。非ELF/読めない場合 -1。
// 動的 ET_EXEC を musl ld.so の明示起動不可問題向けの loader-exec 経路へ振り分ける判定用。
static int elf_e_type(const char *path) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    unsigned char e[18];
    ssize_t r = pread(fd, e, sizeof(e), 0);
    close(fd);
    if (r != (ssize_t)sizeof(e) || memcmp(e, "\x7f""ELF", 4) != 0) return -1;
    unsigned short t; memcpy(&t, e + 0x10, 2);
    return (int)t;
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
        // interp が動的(ld.so 経由 idyn==1)なら loader 対象は自己 relocation する ld.so 本体。
        wrap_with_loader(cfg, plan, idyn == 1);
        return 0;
    }

    // 2) 動的 ELF: rootfs 内のローダ経由で起動。
    char interp[PATH_MAX_Z];
    if (read_elf_interp(host_prog, interp, sizeof(interp)) == 1) {
        char host_loader[PATH_MAX_Z];
        if (!translate_abs(cfg, interp, host_loader, sizeof(host_loader)))
            snprintf(host_loader, sizeof(host_loader), "%s", interp);

        // ET_EXEC(非PIE)動的 + musl ld.so: musl は ET_EXEC を「コマンドとして明示起動」
        // できない(`Not a valid dynamic program`)。本体と ld.so を両方マップし、カーネルが
        // PT_INTERP 経由で起動したのと同じ auxv(AT_BASE=ld.so の base, AT_PHDR/ENTRY=本体)を
        // 組む loader-exec 経路へ回す。glibc ld.so は明示起動で ET_EXEC を受けるため非対象に
        // して既存経路(Arch claude 等)を温存する。loader 無効時は従来経路へフォールバック。
        if (cfg->use_loader && cfg->self_path[0] != '\0') {
            const char *lb = strrchr(interp, '/'); lb = lb ? lb + 1 : interp;
            if (strncmp(lb, "ld-musl", 7) == 0 && elf_e_type(host_prog) == 2 /* ET_EXEC */) {
                snprintf(plan->target, sizeof(plan->target), "%s", cfg->self_path);
                plan->nprefix = 0;
                plan_push(plan, "z2root");
                plan_push(plan, "--loader-exec");
                plan_push(plan, host_loader);  // ld.so 本体(host)
                plan_push(plan, host_prog);    // ET_EXEC 本体(host)
                plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
                return 0;
            }
        }

        snprintf(plan->target, sizeof(plan->target), "%s", host_loader);
        plan_push(plan, host_loader);
        // Android の bionic linker(/system/bin/linker64) は glibc/musl の ld.so と違い
        // `--argv0 <name>` を解さず、そのまま実プログラムの argv[1] へ漏らす。Android
        // ネイティブの build-tools(aapt2 等。interp=linker64)が "expected absolute path:
        // --argv0" で daemon 起動失敗していた。bionic のときは --argv0 を渡さない
        // (argv0 は実プログラムパスのままになるが Android ツールは argv0 を見ないため実害なし)。
        const char *ib = strrchr(interp, '/');
        ib = ib ? ib + 1 : interp;
        int interp_is_bionic = (strcmp(ib, "linker64") == 0 || strcmp(ib, "linker") == 0);
        if (!interp_is_bionic) {
            plan_push(plan, "--argv0");
            plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
        }
        // ld.so が開く実プログラムは「ゲストパス」を渡す。ld.so の open() は
        // tracee として傍受・翻訳されるため、host_prog(=ホスト実パス)を渡すと
        // bind 配下(例: -b <home>:/root の /root/a.out)で「ゲストパス扱い→rootfs
        // 前置」され ENOENT になる(rootfs 配下のみ二重変換抑止で偶然動いていた)。
        // host_prog をゲスト視点へ逆変換して渡せば rootfs/bind の両方で正しく開ける。
        char guest_real[PATH_MAX_Z];
        host_to_guest(cfg, host_prog, guest_real, sizeof(guest_real));
        plan_push(plan, guest_real);
        // 動的 ELF: loader 対象は ld.so 本体。ld.so は _dl_start で自己 relocation するため
        // loader 側の RELATIVE/RELR 肩代わりを抑止(さもないと二重 relocation で SIGSEGV)。
        wrap_with_loader(cfg, plan, 1);
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

    // 3) 静的 ELF / その他: loader には「ゲストパス」を渡す。loader(load_elf_and_jump)の
    //    open() も tracee として傍受・翻訳されるため、host_prog(ホスト実パス)を渡すと
    //    bind 配下(例: -b <home>:/root の /root/.../clang-21 = NDK 静的 clang)で「ゲスト
    //    パス扱い→rootfs 前置」され ENOENT になる(rootfs 配下は二重変換抑止で偶然動く)。
    //    動的 ELF 経路(上記 §2)が ld.so に guest_real を渡すのと同じ理由・同じ host_to_guest
    //    逆変換で、rootfs/bind の両方で静的バイナリを正しくマップできる。
    char guest_static[PATH_MAX_Z];
    host_to_guest(cfg, host_prog, guest_static, sizeof(guest_static));
    snprintf(plan->target, sizeof(plan->target), "%s", guest_static);
    plan_push(plan, (orig_argv0 && orig_argv0[0]) ? orig_argv0 : guest_prog);
    // 静的 ELF 直接ロード: 自己 relocation しない bionic static-PIE 等のため loader 側で
    // RELATIVE/RELR を肩代わり適用する(0.8.59 の挙動を維持)。
    wrap_with_loader(cfg, plan, 0);
    return 0;
}

static FILE *g_trc;       // 定義は下方(trc_init 付近)。診断ログ用に前方宣言。
static int  g_trc_on;

// execve(at) 時に元 argv と argv0 basename を per-tracee に控える。
// /proc/<pid>/cmdline・/proc/<pid>/comm・/proc/<pid>/status:Name を本来の(ローダ包み前の)
// 表記で返すため。argv が NULL/空のときは guest_prog 単体で控える。
static void record_exec_argv(struct pid_state *st, const char *guest_prog,
                             char *const *argv, int argc) {
    if (!st) return;
    size_t off = 0;
    if (argv && argc > 0) {
        for (int i = 0; i < argc; i++) {
            const char *a = argv[i] ? argv[i] : "";
            size_t l = strlen(a) + 1;
            if (off + l > sizeof(st->proc_cmdline)) break;  // 上限到達=以降切り捨て
            memcpy(st->proc_cmdline + off, a, l);
            off += l;
        }
    } else {
        const char *a = guest_prog ? guest_prog : "";
        size_t l = strlen(a) + 1;
        if (l <= sizeof(st->proc_cmdline)) {
            memcpy(st->proc_cmdline, a, l);
            off = l;
        }
    }
    st->proc_cmdline_len = off;

    // /proc/<pid>/comm はカーネルが exec した実行ファイル basename を使う(TASK_COMM_LEN=16)。
    // 同じ規則で guest_prog の basename(最大 15 文字)を入れる。ps/pgrep が拾うのはここ。
    const char *src = (guest_prog && guest_prog[0]) ? guest_prog : "z2root";
    const char *slash = strrchr(src, '/');
    const char *bn = slash ? slash + 1 : src;
    size_t bl = strlen(bn);
    if (bl >= TASK_COMM_LEN) bl = TASK_COMM_LEN - 1;
    memcpy(st->proc_comm, bn, bl);
    st->proc_comm[bl] = '\0';
}

// execve(at) 時に「ゲスト視点でどのプログラムを実行するか」を per-tracee に記録する。
// /proc/<pid>/exe の readlink/open はカーネルの実 symlink(z2root のローダ=libz2root.so 等)を
// 返すため、Go ランタイム/libbacktrace 等が起動時に自プログラムを開けず即 panic する。
// ここで記録した exe_guest を host_path_for と rewrite_readlink_result で返す。
// 入力 guest_prog は execve に渡された(ゲスト視点の)プログラム文字列。相対パスは
// /proc/<pid>/cwd を逆変換して絶対化する。execveat の fd 基準相対は省略(稀)。
// 失敗は静かに無視(以後の /proc/self/exe は従来挙動=ローダ露出に戻るだけ)。
static void record_exec_guest(const struct config *cfg, pid_t pid, struct pid_state *st,
                              const char *guest_prog, long dirfd) {
    if (!st || !guest_prog || !guest_prog[0]) return;
    char guest_abs[PATH_MAX_Z];
    if (guest_prog[0] == '/') {
        snprintf(guest_abs, sizeof(guest_abs), "%s", guest_prog);
    } else if (dirfd == AT_FDCWD) {
        char proc[64], host_cwd[PATH_MAX_Z];
        snprintf(proc, sizeof(proc), "/proc/%d/cwd", (int)pid);
        ssize_t n = readlink(proc, host_cwd, sizeof(host_cwd) - 1);
        if (n < 0) return;
        host_cwd[n] = '\0';
        char guest_cwd[PATH_MAX_Z];
        host_to_guest(cfg, host_cwd, guest_cwd, sizeof(guest_cwd));
        snprintf(guest_abs, sizeof(guest_abs), "%s/%s", guest_cwd, guest_prog);
    } else {
        return;  // execveat の fd 基準相対は対象外
    }
    char resolved[PATH_MAX_Z];
    canonicalize_guest(cfg, pid, guest_abs, 1, resolved, sizeof(resolved));
    snprintf(st->exe_guest, sizeof(st->exe_guest), "%s", resolved);
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] EXE-record pid=%d guest_prog='%s' -> exe_guest='%s'\n",
                pid, guest_prog, st->exe_guest);
}

// tracee の execve/execveat を傍受し、ローダ差し替え / シバン解決を適用する。
// path_idx: パス引数のレジスタ index (execve=0, execveat=1)。argv は +1, envp は +2。
static void rewrite_execve(const struct config *cfg, pid_t pid,
                           struct user_pt_regs *regs, int path_idx) {
    unsigned long path_addr = regs->regs[path_idx];
    if (path_addr == 0) return;

    char guest_prog[PATH_MAX_Z];
    if (read_tracee_str(pid, path_addr, guest_prog, sizeof(guest_prog)) < 0) return;

    // /proc/<pid>/exe をゲスト視点で返すため、execve 直前のゲストプログラムパスを
    // 控える。エントリ時点での記録なので失敗 execve では古い値が残るが、次に成功した
    // execve で上書きされるため実害は限定的(fork-exec の子は exec 失敗時に exit する)。
    {
        struct pid_state *st = state_lookup(pid);
        long ed = (path_idx == 1) ? (long)(int)regs->regs[0] : (long)AT_FDCWD;
        record_exec_guest(cfg, pid, st, guest_prog, ed);
    }

    // 元 argv を tracee から読む。個数上限を設けず動的確保で全件読む
    // (固定長で切ると dpkg の byte-compile 等 argv の多い exec が壊れる)。
    unsigned long argv_addr = regs->regs[path_idx + 1];
    char **args = NULL;
    int n = 0, cap = 0;
    if (argv_addr) {
        for (;; n++) {
            unsigned long p = 0;
            struct iovec lo = { &p, 8 };
            struct iovec re = { (void *)(argv_addr + (unsigned long)n * 8), 8 };
            if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != 8) break;
            if (p == 0) break;
            if (n == cap) {
                int ncap = cap ? cap * 2 : 32;
                char **na = realloc(args, (size_t)ncap * sizeof(*na));
                if (!na) break;
                args = na; cap = ncap;
            }
            char *s = malloc(PATH_MAX_Z);
            if (!s) break;
            if (read_tracee_str(pid, p, s, PATH_MAX_Z) < 0) s[0] = '\0';
            args[n] = s;
        }
    }

    // 元 argv/comm を per-tracee に控える(plan_exec / wrap_with_loader が argv を書き換える
    // 前の値=本来の guest argv)。/proc/<pid>/{cmdline,comm,status:Name} 偽装で使う。
    {
        struct pid_state *st = state_lookup(pid);
        record_exec_argv(st, guest_prog, args, n);
    }

    struct exec_plan plan;
    int rc = plan_exec(cfg, pid, guest_prog, (n > 0) ? args[0] : guest_prog, &plan);

    if (rc == 1) {
        // passthrough: loader を噛ませず path レジスタを host パスへ変換するだけ。
        // 非ELF/存在しないパスはカーネルが ENOENT/ENOEXEC を返し、execvp が次候補へ進める。
        size_t plen = strlen(plan.target) + 1;
        unsigned long base = scratch_base(regs->sp, plen);
        if (write_tracee_mem(pid, base, plan.target, plen) == 0) {
            regs->regs[path_idx] = base;
            set_regs(pid, regs);
        }
        for (int j = 0; j < n; j++) free(args[j]);
        free(args);
        return;
    }

    // 最終 argv = plan.prefix[..] + args[plan.orig_start..]
    int maxparts = plan.nprefix + n + 1;
    const char **parts = malloc((size_t)maxparts * sizeof(*parts));
    if (!parts) { for (int j = 0; j < n; j++) free(args[j]); free(args); return; }
    int pc = 0;
    for (int j = 0; j < plan.nprefix; j++) parts[pc++] = plan.prefix[j];
    for (int j = plan.orig_start; j < n; j++) parts[pc++] = args[j];

    // tracee スタック下に [target 文字列][argv blob][8B align][ポインタ配列 + NULL] を配置。
    // blob/ptrs は argv サイズに応じて動的確保する(固定 8KB だと大きい argv で書き換えを
    // 取りこぼし、ゲストパスのまま execve され ENOENT になっていた)。
    size_t target_len = strlen(plan.target) + 1;
    size_t blob_sz = 0;
    for (int j = 0; j < pc; j++) blob_sz += strlen(parts[j]) + 1;
    size_t total = target_len + blob_sz + 8 + (size_t)(pc + 1) * 8;
    unsigned long base = scratch_base(regs->sp, total);

    char *blob = malloc(blob_sz ? blob_sz : 1);
    unsigned long *ptrs = malloc((size_t)(pc + 1) * sizeof(*ptrs));
    if (blob && ptrs) {
        unsigned long blob_base = base + target_len;
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

    free(blob);
    free(ptrs);
    free(parts);
    for (int j = 0; j < n; j++) free(args[j]);
    free(args);
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

// syscall-exit で readlinkat(78) の戻りバッファに入ったリンク先を逆変換する。
// /proc/self/cwd・/proc/<pid>/cwd・/proc/self/exe 等の magic symlink はホスト実パス
// (/data/.../shared_home …)を返す。getcwd は逆変換済みだが、cwd を readlink 経由で
// 取る実装(Bun 製の claude code 等)はホスト実パスを掴み「working directory が無い」と
// 誤判定して即終了し、起動できない。ここで host_to_guest() してゲストパスへ戻す
// (proot も /proc/*/cwd・exe・root の readlink を同様に逆変換する)。
static void rewrite_readlink_result(const struct config *cfg, pid_t pid,
                                    const struct pid_state *st) {
    unsigned long buf = st->aux_addr, bufsiz = st->aux_len;
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] RL-rewrite pid=%d ret=%ld buf=0x%lx bufsiz=%lu aux_path='%s' self_exe=%d\n",
                pid, ret, buf, bufsiz, st->aux_path, st->aux_is_self_exe);
    // /proc/<own>/exe(self/exe・thread-self/exe を含む)は記録済みゲスト exe を直接返す。
    // カーネル側の readlinkat は host_path_for の差し替えで exe_guest のホスト実パス(通常
    // ファイル)へ向くため -EINVAL を返しているが、ここで成功(glen)に偽装してバッファを
    // 上書きする(getcwd/chmod 系の失敗→0 偽装と同様)。
    if (st->aux_is_self_exe && st->exe_guest[0] && buf && bufsiz) {
        size_t glen = strlen(st->exe_guest);
        if (glen > bufsiz) glen = bufsiz;
        if (write_tracee_mem(pid, buf, st->exe_guest, glen) == 0) {
            regs.regs[0] = (unsigned long)glen;   // readlink は書き込みバイト数(NUL含まず)
            set_regs(pid, &regs);
            if (g_trc_on)
                fprintf(g_trc, "[z2trc] RL-rewrite SELF-EXE -> '%s' glen=%zu\n",
                        st->exe_guest, glen);
        }
        return;
    }
    if (ret <= 0 || buf == 0) return;             // readlink 失敗 / バッファ無し
    if ((unsigned long)ret > bufsiz) return;      // 異常値

    char host[PATH_MAX_Z];
    if (st->aux_path[0]) {
        // 自前で full バッファ readlink し直す(tracee バッファは切り詰められている
        // 可能性がある)。aux_path はホスト実パスなのでトレーサから直接読める。
        ssize_t hn = readlink(st->aux_path, host, sizeof(host) - 1);
        if (hn < 0) { if (g_trc_on) fprintf(g_trc, "[z2trc] RL-rewrite self-readlink FAIL errno=%d\n", errno); return; }
        host[hn] = '\0';
    } else {
        // dirfd 相対などでホストパス未確定: 従来どおり tracee バッファから読む
        if ((size_t)ret >= PATH_MAX_Z) return;
        struct iovec local = { host, (size_t)ret };
        struct iovec remote = { (void *)buf, (size_t)ret };
        if (process_vm_readv(pid, &local, 1, &remote, 1, 0) != (ssize_t)ret) return;
        host[ret] = '\0';                         // readlink は NUL 終端しないので補う
    }
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] RL-rewrite host='%s'\n", host);
    if (host[0] != '/') return;                   // 相対リンク先は変換不要

    char g[PATH_MAX_Z];
    host_to_guest(cfg, host, g, sizeof(g));
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] RL-rewrite guest='%s' glen=%zu\n", g, strlen(g));
    if (strcmp(g, host) == 0) return;             // rootfs/bind 配下でない=変換不要

    size_t glen = strlen(g);
    if (glen > bufsiz) glen = bufsiz;             // readlink はバッファ長で切り詰める
    int wr = write_tracee_mem(pid, buf, g, glen);
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] RL-rewrite WRITE glen=%zu wr=%d -> regs0=%zu\n", glen, wr, glen);
    if (wr == 0) {
        regs.regs[0] = (unsigned long)glen;       // readlink は書き込みバイト数(NUL含まず)を返す
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
// ---- コピー fallback の inode 偽装キャッシュ -----------------------------------
// Android(SELinux untrusted_app)は link(2) を端末全域で拒否するため linkat は常に
// コピー fallback になる(別 inode)。git(>=2.46)は link 後に dest を lstat し src と
// (st_dev,st_ino)を比較し、違えば "hardlink different from source" で die する。
// そこでコピー fallback したペアを控え、直後の dest の stat 結果を src の (dev,ino)
// へ偽装して検証を通す。git は link→即 lstat なので一度通せば十分=ヒット後に破棄し、
// 他ツールへの inode 偽装の窓を最小化する(小リングで多重 clone にも追従)。
#define LINKCOPY_CACHE 32
struct linkcopy_ent {
    int used;
    char dest_host[PATH_MAX_Z];        // コピーで作った dest のホスト実パス(照合キー)
    unsigned long src_dev, src_ino;    // 偽装で見せる src の (dev,ino)
};
static struct linkcopy_ent g_linkcopy[LINKCOPY_CACHE];
static int g_linkcopy_next;
static int g_linkcopy_used;          // 有効エントリ数(0 なら stat hot path で照合を省く)

// copy_for_link が成功させた「コピー先のホスト実パス」と src の (dev,ino) を記録する。
//
// 照合キーは dest のホスト実パス。inode 番号(+dev)で照合する旧実装は誤ヒットを起こした:
// dest は rootfs bind 配下=ゲスト全ファイルと同じ host /data パーティション上にあるため
// st_dev はゲスト全域で同一の固定値で、dev を条件に足しても識別力がゼロ。結果 inode 番号
// だけの照合に等しく、Arch 起動中に init/ld が stat した無関係ファイルの inode が記録済み
// dest_ino と衝突して st_ino を無縁の src 値へ誤偽装し、ゲストが即 exitCode=-1 で死ぬ退行を
// 招いた(0.8.62 で記録が初成功し hot path が常時 ON 化したことで顕在化、0.8.63 の (dev,ino)
// 厳格化でも dev 不識別ゆえ未解決)。パスで照合すれば「まさにコピーした dest を stat した
// とき」だけ偽装でき、無関係ファイルへの誤ヒットは原理的に起きない。
static void linkcopy_record(unsigned long src_dev, unsigned long src_ino,
                            const char *dest_host) {
    if (!dest_host || !dest_host[0]) return;
    struct linkcopy_ent *e = &g_linkcopy[g_linkcopy_next];
    g_linkcopy_next = (g_linkcopy_next + 1) % LINKCOPY_CACHE;
    if (!e->used) g_linkcopy_used++;
    e->used = 1;
    snprintf(e->dest_host, sizeof(e->dest_host), "%s", dest_host);
    e->src_dev = src_dev;
    e->src_ino = src_ino;
    if (g_trc_on)
        fprintf(g_trc, "[z2trc] linkcopy REC src(dev=%lu ino=%lu) dest=%s used=%d\n",
                src_dev, src_ino, dest_host, g_linkcopy_used);
}

// stat 対象のホスト実パスが記録済み dest と一致するエントリを返す(添字)。-1=不一致。
static int linkcopy_find_by_path(const char *host_path) {
    if (g_linkcopy_used == 0 || !host_path || !host_path[0]) return -1;
    for (int i = 0; i < LINKCOPY_CACHE; i++)
        if (g_linkcopy[i].used && strcmp(g_linkcopy[i].dest_host, host_path) == 0)
            return i;
    return -1;
}

// buf は entry で記録した stat 系の出力バッファアドレス(stat 以外では未使用)。
// lc_idx は entry で「対象パス==コピー先」と判定した linkcopy エントリ添字(-1=非該当)。
static void fake_root_on_exit(pid_t pid, long nr, unsigned long buf, int lc_idx) {
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
            // entry で「対象パス==コピー先」と確定した時だけ (st_dev@0, st_ino@8) を src へ
            // 偽装し git(>=2.46)の hardlink 検証を通す。inode 照合をやめパス照合にしたので
            // 無関係ファイルへの誤偽装は起きない(fd ベースの fstat=80 は entry でパスを取れず
            // lc_idx=-1=非該当のため偽装しない。git の検証は lstat=newfstatat 経路を使う)。
            if (lc_idx >= 0) {
                struct linkcopy_ent *e = &g_linkcopy[lc_idx];
                write_tracee_mem(pid, buf + 0, &e->src_dev, 8);
                write_tracee_mem(pid, buf + 8, &e->src_ino, 8);
                if (g_trc_on)
                    fprintf(g_trc, "[z2trc] linkcopy FAKE nr=%ld dest=%s -> src(dev=%lu ino=%lu)\n",
                            nr, e->dest_host, e->src_dev, e->src_ino);
                e->used = 0; g_linkcopy_used--;  // 一度通せば十分(偽装窓を最小化)
            }
            return;
        }
        case 291: {  // statx: stx_uid(off20)/stx_gid(off24)
            unsigned int zero = 0;
            if (ret != 0 || buf == 0) return;
            write_tracee_mem(pid, buf + 20, &zero, 4);
            write_tracee_mem(pid, buf + 24, &zero, 4);
            // パス照合でコピー先 statx のときだけ (stx_ino@32, stx_dev_major@128/minor@132)
            // を src へ偽装(struct stat 版と同じく git の hardlink 検証対策)。
            if (lc_idx >= 0) {
                struct linkcopy_ent *e = &g_linkcopy[lc_idx];
                unsigned int smaj = major((dev_t)e->src_dev);
                unsigned int smin = minor((dev_t)e->src_dev);
                write_tracee_mem(pid, buf + 32, &e->src_ino, 8);
                write_tracee_mem(pid, buf + 128, &smaj, 4);
                write_tracee_mem(pid, buf + 132, &smin, 4);
                if (g_trc_on)
                    fprintf(g_trc, "[z2trc] linkcopy FAKE statx dest=%s -> src(dev=%lu ino=%lu)\n",
                            e->dest_host, e->src_dev, e->src_ino);
                e->used = 0; g_linkcopy_used--;
            }
            return;
        }
    }
}

// AF_UNIX の SCM_CREDENTIALS(ancillary)を扱う sendmsg/recvmsg の uid/gid 偽装。
// 背景: fake_root(-0) は getuid/getgid を 0 に偽装するため、ゲスト(例: PulseAudio /
// dbus)は SCM_CREDENTIALS に uid=0 を載せて sendmsg する。しかしカーネルは「申告 uid が
// 実 uid/euid/suid のいずれか、または CAP_SETUID」でなければ EPERM を返す。非特権の
// Android アプリ uid(実 uid≠0)では一致せず EPERM → ゲストの接続が即死する(PulseAudio
// の "Connection died")。proot 同様、cred を実 uid/gid へ書き換えてカーネルに通す。
//   - sendmsg(entry): 載っている SCM_CREDENTIALS の uid/gid を実値へ(pid は実 pid のまま)。
//   - recvmsg(exit) : 受け取った SCM_CREDENTIALS の uid/gid を 0 へ(root の見え方を一貫)。
// msghdr(LP64): msg_control=off32, msg_controllen=off40。cmsghdr: len(8)/level(4)/type(4)、
// データは +16。ucred: pid(0)/uid(4)/gid(8)。
#define Z_CMSG_CTRL_MAX 4096
static int read_msg_control(pid_t pid, unsigned long msgp,
                            unsigned long *ctrl, unsigned long *ctrllen) {
    unsigned long cc[2];
    struct iovec lo = { cc, sizeof cc };
    struct iovec re = { (void *)(msgp + 32), sizeof cc };  // msg_control / msg_controllen
    if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != (ssize_t)sizeof cc) return -1;
    *ctrl = cc[0];
    *ctrllen = cc[1];
    return 0;
}
// 制御バッファを走査し、SCM_CREDENTIALS の ucred.uid/gid を (new_uid,new_gid) へ。
// 変更があれば tracee メモリへ書き戻す。
static void patch_scm_creds(pid_t pid, unsigned long ctrl, unsigned long ctrllen,
                            unsigned int new_uid, unsigned int new_gid) {
    if (ctrl == 0 || ctrllen < 16 || ctrllen > Z_CMSG_CTRL_MAX) return;
    char buf[Z_CMSG_CTRL_MAX];
    struct iovec lo = { buf, (size_t)ctrllen };
    struct iovec re = { (void *)ctrl, (size_t)ctrllen };
    if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != (ssize_t)ctrllen) return;
    size_t off = 0;
    int changed = 0;
    while (off + 16 <= (size_t)ctrllen) {
        unsigned long clen;
        int level, type;
        memcpy(&clen, buf + off, 8);
        memcpy(&level, buf + off + 8, 4);
        memcpy(&type, buf + off + 12, 4);
        if (clen < 16) break;
        if (level == SOL_SOCKET && type == SCM_CREDENTIALS && off + 16 + 12 <= (size_t)ctrllen) {
            memcpy(buf + off + 16 + 4, &new_uid, 4);  // ucred.uid
            memcpy(buf + off + 16 + 8, &new_gid, 4);  // ucred.gid
            changed = 1;
        }
        size_t adv = (clen + 7) & ~((size_t)7);  // CMSG_ALIGN
        if (adv == 0) break;
        off += adv;
    }
    if (changed) write_tracee_mem(pid, ctrl, buf, (size_t)ctrllen);
}
// sendmsg(entry): SCM_CREDENTIALS の uid/gid を実値へ(EPERM 回避)。msg=regs[1]。
static void rewrite_sendmsg_creds(const struct config *cfg, pid_t pid,
                                  const struct user_pt_regs *regs) {
    unsigned long msgp = regs->regs[1];
    if (msgp == 0) return;
    unsigned long ctrl, ctrllen;
    if (read_msg_control(pid, msgp, &ctrl, &ctrllen) != 0) return;
    patch_scm_creds(pid, ctrl, ctrllen, (unsigned int)cfg->real_uid, (unsigned int)cfg->real_gid);
}
// recvmsg(exit): 受信した SCM_CREDENTIALS の uid/gid を 0 へ(root の見え方を一貫)。
// カーネルが msg_controllen を書き戻すので exit で読む。msg ポインタは entry で控えた値。
static void rewrite_recvmsg_creds(pid_t pid, unsigned long msgp) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    if ((long)regs.regs[0] < 0) return;  // recvmsg 失敗
    if (msgp == 0) return;
    unsigned long ctrl, ctrllen;
    if (read_msg_control(pid, msgp, &ctrl, &ctrllen) != 0) return;
    patch_scm_creds(pid, ctrl, ctrllen, 0, 0);
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

// guest パスが /proc/<...>/cmdline 形か(/proc/self/cmdline・/proc/<pid>/cmdline・
// /proc/<pid>/task/<tid>/cmdline いずれも末尾照合で拾える)。
static int is_proc_cmdline_path(const char *p) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    size_t n = strlen(p);
    return n >= 8 && strcmp(p + (n - 8), "/cmdline") == 0;
}

// guest パスが /proc/<...>/comm 形か。
static int is_proc_comm_path(const char *p) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    size_t n = strlen(p);
    return n >= 5 && strcmp(p + (n - 5), "/comm") == 0;
}

// guest パスが /proc/<pid>/stat または /proc/<pid>/task/<tid>/stat 形か。
// 全体統計の /proc/stat(n==10)は除外する(中身は cpu/intr/ctxt 等で comm 文字列無し)。
static int is_proc_stat_path(const char *p) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    size_t n = strlen(p);
    if (n <= 10) return 0;  // "/proc/stat" 以下の長さは対象外(per-pid stat は最短 /proc/N/stat=12)
    return strcmp(p + (n - 5), "/stat") == 0;
}

// 開こうとしている proc パスの偽装種別を返す(非対象は PROC_FD_NONE)。
static int proc_open_kind(const char *p) {
    if (is_proc_status_path(p)) return PROC_FD_STATUS;
    if (is_proc_loginuid_path(p)) return PROC_FD_LOGINUID;
    if (is_proc_cmdline_path(p)) return PROC_FD_CMDLINE;
    if (is_proc_comm_path(p)) return PROC_FD_COMM;
    if (is_proc_stat_path(p)) return PROC_FD_STAT;
    return PROC_FD_NONE;
}

// /proc/<pid>/... または /proc/self/... を解析して対象 pid を返す(0=不明/非数値)。
// self/thread-self は呼び出し側のトレーシ pid(self)へ解決。task/<tid>/... は主スレッド
// (=ファイルを開いた pid からたどる)で代表させる(マルチスレッドの個別 comm は本実装では
// 区別せず main の controlling argv0 をそのまま見せる=ps/pgrep の主目的に十分)。
static pid_t proc_path_pid(const char *p, pid_t self) {
    if (strncmp(p, "/proc/", 6) != 0) return 0;
    const char *r = p + 6;
    if (strcmp(r, "self") == 0 || strncmp(r, "self/", 5) == 0) return self;
    if (strncmp(r, "thread-self/", 12) == 0 || strcmp(r, "thread-self") == 0) return self;
    pid_t v = 0;
    while (*r >= '0' && *r <= '9') { v = v * 10 + (pid_t)(*r - '0'); r++; }
    return v;
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

// /proc/<pid>/stat の field 2 "(<comm>)" を name へ書き換え(length 保存)。
// カーネル形式: "<pid> (<comm>) <state> <ppid> ..." (改行 1 行)。comm 自身に '('/')' を
// 含み得るため右端探索(最後の ") ")で閉じ位置を取る(/proc/<pid>/stat 互換パーサと同流儀)。
// 新 name が中身より短ければ空白埋め=後続フィールドのオフセットを崩さない。
static void fake_stat_comm(char *b, size_t len, const char *name) {
    if (!name || !name[0] || len < 4) return;
    size_t lp = 0;
    while (lp < len && b[lp] != '(') lp++;
    if (lp >= len) return;
    size_t line_end = 0;
    while (line_end < len && b[line_end] != '\n') line_end++;
    size_t rp = len;
    if (line_end > lp + 2) {
        for (size_t i = line_end - 1; i > lp; i--)
            if (b[i] == ' ' && b[i - 1] == ')') { rp = i - 1; break; }
    }
    if (rp >= len) return;
    size_t inner = rp - (lp + 1);
    if (inner == 0) return;
    size_t nl = strlen(name);
    if (nl > inner) nl = inner;
    memcpy(b + lp + 1, name, nl);
    for (size_t j = lp + 1 + nl; j < rp; j++) b[j] = ' ';
}

// status バッファ先頭の "Name:\t<comm>" 行を name へ書き換える(length 保存=
// 後続行のオフセットを崩さない。新 name が短ければ末尾を空白で埋める)。
// 行が無い/短すぎる場合は何もしない。z2root では comm がカーネル設定の "libz2root.so"
// 漏れになるので、ここで argv0 basename へ差し替えて ps/pgrep の見た目を整える。
static void fake_status_name(char *b, size_t len, const char *name) {
    if (!name || !name[0]) return;
    if (len < 6 || memcmp(b, "Name:", 5) != 0) return;
    size_t le = 5;
    while (le < len && b[le] != '\n') le++;
    if (le >= len || le < 6) return;
    b[5] = '\t';
    size_t nl = strlen(name);
    if (nl > le - 6) nl = le - 6;
    memcpy(b + 6, name, nl);
    for (size_t j = 6 + nl; j < le; j++) b[j] = ' ';
}

// read() exit: 追跡 fd からの読み取りバッファ(buf, ret バイト)を種別に応じて root 偽装する。
// st は status の Name: 行 / cmdline / comm 偽装で argv 控えを引くため。
// 非 readfree(Z2ROOT_NO_READFREE=1)経路の補完。readfree 既定 ON では openat-time 差し替えが
// 先に走るためここは通らない。
static void fake_proc_on_read(pid_t pid, unsigned long buf, int kind,
                              const struct pid_state *st) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (ret <= 0 || buf == 0) return;
    size_t len = (size_t)ret;
    if (len > PATH_MAX_Z) len = PATH_MAX_Z;  // status は read 1 回で全体が収まる前提

    if (kind == PROC_FD_LOGINUID || kind == PROC_FD_STATUS || kind == PROC_FD_STAT) {
        // length 保存タイプ(in-place 偽装→そのまま書き戻し)。
        char b[PATH_MAX_Z];
        struct iovec lo = { b, len };
        struct iovec re = { (void *)buf, len };
        if (process_vm_readv(pid, &lo, 1, &re, 1, 0) != (ssize_t)len) return;
        if (kind == PROC_FD_LOGINUID) {
            fake_loginuid_buf(b, len);
        } else if (kind == PROC_FD_STAT) {
            if (st && st->proc_comm[0]) fake_stat_comm(b, len, st->proc_comm);
        } else {
            fake_status_buf(b, len);
            if (st && st->proc_comm[0]) fake_status_name(b, len, st->proc_comm);
        }
        write_tracee_mem(pid, buf, b, len);
    } else if (kind == PROC_FD_COMM) {
        if (!st || !st->proc_comm[0]) return;
        char out[TASK_COMM_LEN + 1];
        size_t cl = strlen(st->proc_comm);
        memcpy(out, st->proc_comm, cl);
        out[cl] = '\n';
        size_t want = cl + 1;
        if (want > len) want = len;            // user バッファ超は切り詰め(カーネル ret 上限)
        if (write_tracee_mem(pid, buf, out, want) == 0) {
            regs.regs[0] = (unsigned long)want;    // 長さが変わるので read 戻り値も調整
            set_regs(pid, &regs);
        }
    } else if (kind == PROC_FD_CMDLINE) {
        if (!st || st->proc_cmdline_len == 0) return;
        size_t want = st->proc_cmdline_len;
        if (want > len) want = len;
        if (write_tracee_mem(pid, buf, st->proc_cmdline, want) == 0) {
            regs.regs[0] = (unsigned long)want;
            set_regs(pid, &regs);
        }
    }
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

    // 対象 pid のトレース状態(cmdline/comm 偽装の元データ用)。非トレース pid は
    // 通常 /proc 経由で素通しさせる(ホスト Android プロセスの cmdline/comm はそのまま=
    // ps が PID 1 の init 等を見られる)。
    pid_t target = proc_path_pid(g, pid);
    struct pid_state *tst = (target > 0) ? state_lookup(target) : NULL;

    char buf[STATUS_BUF_MAX];
    size_t total = 0;

    if (kind == PROC_FD_CMDLINE) {
        if (!tst || tst->proc_cmdline_len == 0) return 0;          // 控え無し=実 /proc
        if (tst->proc_cmdline_len > sizeof(buf)) return 0;          // 上限超(まず起きない)
        memcpy(buf, tst->proc_cmdline, tst->proc_cmdline_len);
        total = tst->proc_cmdline_len;
    } else if (kind == PROC_FD_COMM) {
        if (!tst || !tst->proc_comm[0]) return 0;
        size_t cl = strlen(tst->proc_comm);
        memcpy(buf, tst->proc_comm, cl);
        buf[cl] = '\n';                                             // /proc/<pid>/comm はカーネルが末尾 \n を付ける
        total = cl + 1;
    } else {
        // STATUS / LOGINUID / STAT: 実 /proc ファイルを読み、種別に応じた偽装を当てる。
        // self/thread-self は tid へ解決。読めなければ非差し替え。
        char real[PATH_MAX_Z];
        resolve_proc_self(g, pid, real, sizeof(real));
        int rfd = open(real, O_RDONLY | O_CLOEXEC);
        if (rfd < 0) return 0;
        ssize_t n;
        while (total < sizeof(buf) && (n = read(rfd, buf + total, sizeof(buf) - total)) > 0)
            total += (size_t)n;
        close(rfd);

        if (kind == PROC_FD_LOGINUID) {
            fake_loginuid_buf(buf, total);
        } else if (kind == PROC_FD_STAT) {
            // busybox/procps の ps は速度のため stat field 2 (<comm>) を読む。control。
            if (tst && tst->proc_comm[0]) fake_stat_comm(buf, total, tst->proc_comm);
        } else {  // PROC_FD_STATUS
            fake_status_buf(buf, total);
            // Name: 行は本来カーネル設定の "libz2root.so" が漏れるので argv0 basename へ。
            if (tst && tst->proc_comm[0]) fake_status_name(buf, total, tst->proc_comm);
        }
    }

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
    unsigned long base = scratch_base(regs->sp, (len + 7) & ~7UL);
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

// --link2symlink: ハードリンク linkat(37) のエミュレート。
//
// 旧実装は「linkat→symlinkat に化かし new を old のゲスト絶対パスへの symlink にする」
// だったが、これは git の loose object 確定 (tmp に書く→link(tmp,final)→unlink(tmp)) で
// 壊れた: final が直後に消える tmp を指す dangling symlink になり「not a valid object」。
// (dpkg は old が残るので無害だっただけ。)
//
// 新実装は「まず実ハードリンクを試し、失敗 (Android アプリ内 FS は link を EACCES で拒否)
// したらトレーサ側で old を new へコピーして成功 (0) を返す」。
//   - 実ハードリンクが通る環境ではそのまま本来の共有 inode 意味論を保つ。
//   - 通らない /data 上でも new が独立した実ファイルになり、old を後で unlink しても残る
//     = git/coreutils/ビルド系の「リンクで原子的に確定」パターンが汎用的に動く。
// entry で host パスへ翻訳して実 linkat を走らせ、exit で戻り値を見てコピー fallback する。

// linkat 失敗時のコピー fallback (トレーサ自身がホスト実パスで実行)。
// 返り値: 0=通常ファイルをコピー生成(linkcopy 記録対象) / 1=symlink を再生成しただけ
// (git の hardlink 検証対象外=記録不要) / -1=失敗。成功時 out_src_dev/out_src_ino に src の
// (dev,ino) を返す(NULL 可)。
static int copy_for_link(const char *src, const char *dst, int follow,
                         unsigned long *out_src_dev, unsigned long *out_src_ino) {
    struct stat stt;
    if (lstat(src, &stt) != 0) return -1;
    // AT_SYMLINK_FOLLOW 無しで old が symlink: ハードリンクは symlink 自体への別名なので
    // 同じ中身の symlink を作り直す。
    if (S_ISLNK(stt.st_mode) && !follow) {
        char tgt[PATH_MAX_Z];
        ssize_t n = readlink(src, tgt, sizeof(tgt) - 1);
        if (n < 0) return -1;
        tgt[n] = '\0';
        return symlink(tgt, dst) == 0 ? 1 : -1;
    }
    if (S_ISLNK(stt.st_mode) && follow) {  // 実体へ解決
        if (stat(src, &stt) != 0) return -1;
    }
    if (!S_ISREG(stt.st_mode)) return -1;  // ディレクトリ/デバイス等は捏造しない (本来の EPERM を残す)
    int in = open(src, O_RDONLY | O_CLOEXEC);
    if (in < 0) return -1;
    // EXCL: new が既存なら本来 link は EEXIST。ここへは来ない想定だが安全側で上書きしない。
    int out = open(dst, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, stt.st_mode & 07777);
    if (out < 0) { close(in); return -1; }
    char buf[65536];
    int ok = 1;
    ssize_t r;
    while ((r = read(in, buf, sizeof(buf))) > 0) {
        ssize_t w = 0;
        while (w < r) {
            ssize_t k = write(out, buf + w, (size_t)(r - w));
            if (k < 0) { ok = 0; break; }
            w += k;
        }
        if (!ok) break;
    }
    if (r < 0) ok = 0;
    fchmod(out, stt.st_mode & 07777);
    close(in);
    close(out);
    if (!ok) { unlink(dst); return -1; }
    if (out_src_dev) *out_src_dev = (unsigned long)stt.st_dev;
    if (out_src_ino) *out_src_ino = (unsigned long)stt.st_ino;
    return 0;
}

// linkat entry: old/new をホスト実パスへ翻訳して実 linkat を走らせる。コピー fallback 用に
// 翻訳後パスを控える。戻り値 1=処理した (exit でコピー fallback を見る), 0=未処理 (通常翻訳へ)。
static int linkat_entry(const struct config *cfg, pid_t pid, struct user_pt_regs *regs,
                        struct pid_state *st) {
    long olddirfd = (long)(int)regs->regs[0];
    long newdirfd = (long)(int)regs->regs[2];
    unsigned long old_addr = regs->regs[1];
    unsigned long new_addr = regs->regs[3];
    int follow = (regs->regs[4] & (unsigned long)AT_SYMLINK_FOLLOW) ? 1 : 0;
    if (old_addr == 0 || new_addr == 0) return 0;

    char oldp[PATH_MAX_Z], newp[PATH_MAX_Z];
    if (read_tracee_str(pid, old_addr, oldp, sizeof(oldp)) < 0) return 0;
    if (read_tracee_str(pid, new_addr, newp, sizeof(newp)) < 0) return 0;

    char host_old[PATH_MAX_Z], host_new[PATH_MAX_Z];
    if (host_path_for(cfg, pid, oldp, follow, olddirfd, host_old, sizeof(host_old)) != 0) return 0;
    if (host_path_for(cfg, pid, newp, 0, newdirfd, host_new, sizeof(host_new)) != 0) return 0;

    snprintf(st->link_oldhost, sizeof(st->link_oldhost), "%s", host_old);
    snprintf(st->link_newhost, sizeof(st->link_newhost), "%s", host_new);
    st->link_follow = follow;
    st->link_pending = 1;

    // 翻訳済み絶対パスを tracee スタック下スクラッチへ置き、linkat(AT_FDCWD, host_old,
    // AT_FDCWD, host_new, 0) として実行させる (follow は host_old 解決で消化済み)。
    size_t ol = strlen(host_old) + 1, nl = strlen(host_new) + 1;
    size_t total = ((ol + 7) & ~7UL) + ((nl + 7) & ~7UL);
    unsigned long base = scratch_base(regs->sp, total);
    unsigned long optr = base, nptr = base + ((ol + 7) & ~7UL);
    if (write_tracee_mem(pid, optr, host_old, ol) != 0) { st->link_pending = 0; return 0; }
    if (write_tracee_mem(pid, nptr, host_new, nl) != 0) { st->link_pending = 0; return 0; }
    regs->regs[0] = (unsigned long)AT_FDCWD;
    regs->regs[1] = optr;
    regs->regs[2] = (unsigned long)AT_FDCWD;
    regs->regs[3] = nptr;
    regs->regs[4] = 0;
    set_regs(pid, regs);
    return 1;
}

// linkat exit: 実ハードリンクが失敗していたらコピー fallback して成功 (0) に偽装する。
static void linkat_exit(struct pid_state *st, pid_t pid) {
    struct user_pt_regs regs;
    if (get_regs(pid, &regs) != 0) return;
    long ret = (long)regs.regs[0];
    if (ret >= 0) return;  // 実ハードリンク成功 = そのまま
    int err = (int)(-ret);
    // link 不可由来のみコピーへ。EEXIST/ENOENT/ENOSPC 等の本物のエラーは保持する。
    if (err != EACCES && err != EPERM && err != EXDEV &&
        err != EMLINK && err != EROFS && err != EOPNOTSUPP && err != ENOSYS)
        return;
    unsigned long sdev = 0, sino = 0;
    int rc = copy_for_link(st->link_oldhost, st->link_newhost, st->link_follow, &sdev, &sino);
    if (rc < 0) return;  // コピー失敗 = 元のエラーを残す
    if (rc == 0)         // 通常ファイルのコピー時のみ記録(symlink 再生成は検証対象外)
        linkcopy_record(sdev, sino, st->link_newhost);  // git の hardlink 検証対策
    regs.regs[0] = 0;
    set_regs(pid, &regs);
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
    unsigned long base = scratch_base(regs->sp, sizeof(nun));
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
    // linkat(37) の link2symlink エミュレートは handle_syscall_entry 側で entry/exit を
    // 通して処理する (実ハードリンク試行→失敗時コピー fallback)。ここでは通常パス変換に任せる
    // = link2symlink OFF か、entry 側の翻訳に失敗したフォールバック時のみ到達する。

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
        int hrc = host_path_for(cfg, pid, guest, deref, dirfd, hosts[hn], sizeof(hosts[hn]));
        if (g_trc_on)
            fprintf(g_trc, "[z2trc] xlat pid=%d nr=%ld guest='%s' rc=%d host='%s'\n",
                    pid, nr, guest, hrc, hrc == 0 ? hosts[hn] : "");
        if (hrc != 0)
            continue;
        hidx[hn] = pa->idx;
        hn++;
    }
    if (hn == 0) return;

    // スタック下に各 host 文字列を連続配置(下方向に確保)。syscall 実行中は
    // カーネルがユーザスタックを使わないため sp 直下への書き込みは安全。
    size_t total = 0;
    for (int i = 0; i < hn; i++) total += ((strlen(hosts[i]) + 1 + 7) & ~7UL);
    unsigned long base = scratch_base(regs->sp, total);
    unsigned long off = 0;
    int wrote = 0;
    for (int i = 0; i < hn; i++) {
        size_t len = strlen(hosts[i]) + 1;
        errno = 0;
        int wr = write_tracee_mem(pid, base + off, hosts[i], len);
        if (g_trc_on)
            fprintf(g_trc, "[z2trc] scratch pid=%d sp=0x%llx base=0x%lx off=%lu len=%zu wr=%d errno=%d(%s)\n",
                    pid, (unsigned long long)regs->sp, base, off, len, wr,
                    errno, wr ? strerror(errno) : "ok");
        if (wr != 0) continue;
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
    45,                       // truncate (path 版。ftruncate(46) は fd なので非対象)
    5, 6, 8, 9, 11, 12, 14, 15, // *setxattr/*getxattr/*listxattr/*removexattr の path 版(l*=no-follow)。f* は fd
    264,                      // name_to_handle_at (path 版。open_by_handle_at(265) は handle で path 無=非対象)
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
    211, 212,                 // sendmsg / recvmsg (AF_UNIX SCM_CREDENTIALS の uid/gid 偽装)
};

// トレースではなく ENOSYS で明示拒否する syscall。io_uring は submission ring 経由で
// openat/read/write 等を非同期実行し、ptrace/seccomp の per-call トラップを丸ごと
// 素通りする(=パス変換も fakeroot 偽装も効かない最危険経路)。従来は外側の Android
// untrusted_app seccomp が io_uring を SIGSYS で弾き、トレーサが SIGSYS を ENOSYS へ
// 化かして安全な旧経路(epoll/openat 等)へ倒していた(z2root.c の SIGSYS ハンドラ)が、
// それは Android フィルタ依存だった。ここで z2root 自前のフィルタでも ENOSYS を返し、
// Android がトラップしないコンテキストでも確実にフォールバックさせる(防御の多層化)。
// 注: Android が io_uring を RET_TRAP する現行端末では action 優先順(TRAP>ERRNO)で
// 従来どおり SIGSYS 経路を通る=挙動不変。Android が弾かない場合だけ本 ERRNO が効く。
static const int kDenySyscalls[] = {
    425, 426, 427,            // io_uring_setup / io_uring_enter / io_uring_register
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

    int dnrs[16];
    int d = 0;
    for (size_t i = 0; i < sizeof(kDenySyscalls)/sizeof(int); i++) dnrs[d++] = kDenySyscalls[i];

    // BPF プログラムを組む。レイアウト:
    //   [0] LD arch
    //   [1] arch != AARCH64 → ALLOW
    //   [2] LD nr
    //   [3 .. 3+D-1]     deny 比較 D 個(一致で DENY=ENOSYS へ、不一致で次へ)
    //   [3+D .. 3+D+C-1] trace 比較 C 個(一致で TRACE へ、不一致で次へ)
    //   [3+D+C] ALLOW
    //   [4+D+C] TRACE
    //   [5+D+C] DENY (RET_ERRNO ENOSYS)
    int C = n;
    int D = d;
    struct sock_filter prog[8 + 64];
    int p = 0;
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch));
    prog[p++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 0, (__u8)(1 + D + C));
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr));
    for (int i = 0; i < D; i++)
        prog[p++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (unsigned)dnrs[i], (__u8)(1 + D + C - i), 0);
    for (int i = 0; i < C; i++)
        prog[p++] = (struct sock_filter)BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (unsigned)nrs[i], (__u8)(C - i), 0);
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE);
    prog[p++] = (struct sock_filter)BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | (38u & SECCOMP_RET_DATA)); // ENOSYS

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
    if (nr == 78) return 1;                          // readlinkat: 戻りバッファ(リンク先)逆変換
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
        case 212: return 1;          // recvmsg: 受信 SCM_CREDENTIALS の uid/gid を 0 へ
        default: return 0;                            // パス変換のみ(execve/unlinkat/sendmsg 等)
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
    st->linkcopy_hit = -1;
    st->aux_is_self_exe = 0;
    if (st->entry_nr == 29) {                // ioctl: termios2→legacy 書換のみ(exit 後処理不要)
        st->aux_addr = 0;
        maybe_rewrite_ioctl(pid, &regs);
        return 0;
    }
    if (cfg->link2symlink && st->entry_nr == 37) {  // linkat: 実ハードリンク試行→exit でコピー fallback
        st->aux_addr = 0;
        if (linkat_entry(cfg, pid, &regs, st)) return 1;
        st->link_pending = 0;  // 翻訳失敗 = 通常パス変換へフォールバック(コピー fallback 無し)
    }
    unsigned long aux = 0;
    if (st->entry_nr == 17) aux = regs.regs[0];      // getcwd buf
    else if (st->entry_nr == 78) {                   // readlinkat: buf=regs[2], bufsiz=regs[3]
        aux = regs.regs[2];
        st->aux_len = regs.regs[3];
        // 対象 symlink のホスト実パスを控える。exit で自前 full バッファ readlink
        // し直すため(tracee の bufsiz は lstat st_size=ゲスト長基準で確保され、
        // カーネルが書いたホストパスが途中で切れ、host_to_guest 後に更に短くなる)。
        st->aux_path[0] = '\0';
        unsigned long paddr = regs.regs[1];
        char gpath[PATH_MAX_Z];
        if (paddr && read_tracee_str(pid, paddr, gpath, sizeof(gpath)) >= 0) {
            long rdirfd = (long)(int)regs.regs[0];
            char rhost[PATH_MAX_Z];
            if (host_path_for(cfg, pid, gpath, 0, rdirfd, rhost, sizeof(rhost)) == 0)
                snprintf(st->aux_path, sizeof(st->aux_path), "%s", rhost);
            // /proc/<own>/exe(self/exe・thread-self/exe・/proc/<own pid>/exe)を識別。
            // 該当時は exit で exe_guest を直接返す(カーネルが見せる libz2root.so を覆い隠す)。
            char pid_exe[64];
            snprintf(pid_exe, sizeof(pid_exe), "/proc/%d/exe", (int)pid);
            if (strcmp(gpath, "/proc/self/exe") == 0 ||
                strcmp(gpath, "/proc/thread-self/exe") == 0 ||
                strcmp(gpath, pid_exe) == 0)
                st->aux_is_self_exe = 1;
        }
    }
    else if (cfg->fake_root) switch (st->entry_nr) {
        case 80:  aux = regs.regs[1]; break;         // fstat: stat 出力バッファ(fd ベース=パス相関不可)
        case 79:  aux = regs.regs[2];                // newfstatat: 出力バッファ=regs[2], path=regs[1]
            // コピー先を stat したときだけ exit で inode を偽装するためパスを照合する。
            if (cfg->link2symlink && g_linkcopy_used) {
                char gpath[PATH_MAX_Z], rhost[PATH_MAX_Z];
                if (regs.regs[1] && read_tracee_str(pid, regs.regs[1], gpath, sizeof(gpath)) >= 0 &&
                    host_path_for(cfg, pid, gpath, 0, (long)(int)regs.regs[0], rhost, sizeof(rhost)) == 0)
                    st->linkcopy_hit = linkcopy_find_by_path(rhost);
            }
            break;
        case 291: aux = regs.regs[4];                // statx: 出力バッファ=regs[4], path=regs[1]
            if (cfg->link2symlink && g_linkcopy_used) {
                char gpath[PATH_MAX_Z], rhost[PATH_MAX_Z];
                if (regs.regs[1] && read_tracee_str(pid, regs.regs[1], gpath, sizeof(gpath)) >= 0 &&
                    host_path_for(cfg, pid, gpath, 0, (long)(int)regs.regs[0], rhost, sizeof(rhost)) == 0)
                    st->linkcopy_hit = linkcopy_find_by_path(rhost);
            }
            break;
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
        case 211:  // sendmsg: SCM_CREDENTIALS の uid/gid を実値へ(entry で完結)
            rewrite_sendmsg_creds(cfg, pid, &regs);
            break;
        case 212:  // recvmsg: msg ポインタを控え、exit で受信 cred を 0 へ
            aux = regs.regs[1];
            break;
    }
    st->aux_addr = aux;
    maybe_rewrite_path(cfg, pid, &regs);
    return syscall_needs_exit(cfg, st);
}

// syscall-exit 時の処理(戻り値・構造体の逆変換 / 偽装)。
static void handle_syscall_exit(const struct config *cfg, pid_t pid, struct pid_state *st) {
    if (st->link_pending && st->entry_nr == 37) {  // linkat: 失敗ならコピー fallback で成功偽装
        linkat_exit(st, pid);
        st->link_pending = 0;
        return;
    }
    if (st->entry_nr == 17 && st->aux_addr) {
        rewrite_getcwd_result(cfg, pid, st->aux_addr);
    } else if (st->entry_nr == 78 && st->aux_addr) {
        rewrite_readlink_result(cfg, pid, st);
    } else if (cfg->fake_root) {
        if (st->entry_nr == 56 && st->subst_active) {
            subst_on_exit(cfg, pid, st);  // readfree: 差し替えた temp を unlink
        } else if (st->entry_nr == 56 && st->pending_open_kind != PROC_FD_NONE) {
            struct user_pt_regs r;  // openat 成功なら戻り fd を種別付きで追跡
            if (get_regs(pid, &r) == 0 && (long)r.regs[0] >= 0)
                status_fd_add(st, (int)r.regs[0], st->pending_open_kind);
            st->pending_open_kind = PROC_FD_NONE;
        } else if (st->entry_nr == 63 && st->aux_addr) {
            fake_proc_on_read(pid, st->aux_addr, st->aux_kind, st);
        } else if (st->entry_nr == 212 && st->aux_addr) {
            rewrite_recvmsg_creds(pid, st->aux_addr);
        } else {
            fake_root_on_exit(pid, st->entry_nr, st->aux_addr, st->linkcopy_hit);
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
                // 初期プログラムも /proc/<pid>/exe で見える必要があるので seed する
                // (rewrite_execve は呼ばないため、ここで明示的に記録)。
                record_exec_guest(cfg, pid, st, cfg->command[0], AT_FDCWD);
                // 同様に cmdline/comm も cfg->command 全体から seed。
                int argc0 = 0;
                while (cfg->command[argc0]) argc0++;
                record_exec_argv(st, cfg->command[0], (char *const *)cfg->command, argc0);
                ptrace(PTRACE_CONT, pid, 0, 0);
                continue;
            }
            if (g_trace) {
                struct user_pt_regs r;
                if (get_regs(pid, &r) == 0) {
                    long nr = r.regs[8];
                    char pbuf[160]; pbuf[0] = 0;
                    if (nr == 29) snprintf(pbuf, sizeof pbuf, " req=0x%lx fd=%ld", (unsigned long)r.regs[1], (long)r.regs[0]);
                    else if (nr == 78) {  // readlinkat: path=regs[1], bufsiz=regs[3]
                        char rl[120]; rl[0]=0; read_tracee_str(pid, r.regs[1], rl, sizeof rl);
                        snprintf(pbuf, sizeof pbuf, "%s bufsiz=%ld", rl, (long)r.regs[3]);
                    }
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
                            else if (nr == 78) {                   // readlinkat: path=regs[1], bufsiz=regs[3]
                                char rl[120]; rl[0]=0; read_tracee_str(pid, r.regs[1], rl, sizeof rl);
                                snprintf(pbuf, sizeof pbuf, " %s bufsiz=%ld", rl, (long)r.regs[3]);
                            }
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
            struct pid_state *nst = state_for((pid_t)newpid);
            struct pid_state *pst = state_lookup(pid);
            // 親の exe_guest を子へ継承する(fork/clone は exec しない限り親と同じ ELF)。
            // execve した時点で子側 record_exec_guest が上書きするので二重指定の害は無い。
            if (nst && pst && pst->exe_guest[0])
                snprintf(nst->exe_guest, sizeof(nst->exe_guest), "%s", pst->exe_guest);
            // cmdline/comm も同様に継承(execve まで親と同じ argv を見せる)。
            if (nst && pst && pst->proc_cmdline_len) {
                memcpy(nst->proc_cmdline, pst->proc_cmdline, pst->proc_cmdline_len);
                nst->proc_cmdline_len = pst->proc_cmdline_len;
                memcpy(nst->proc_comm, pst->proc_comm, sizeof(nst->proc_comm));
            }
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
            if (st) {
                st->at_exit = 0;
                // seccomp フォールバック経路でも初期 exe を seed する。
                record_exec_guest(cfg, pid, st, cfg->command[0], AT_FDCWD);
                int argc0 = 0;
                while (cfg->command[argc0]) argc0++;
                record_exec_argv(st, cfg->command[0], (char *const *)cfg->command, argc0);
            }
            ptrace(PTRACE_SYSCALL, pid, 0, 0);
            continue;
        }

        // group-stop(SIGSTOP/TSTP/TTIN/TTOU の 2 段目)は PTRACE_GETSIGINFO が EINVAL。
        // 尊重して再開しない(ジョブ制御 Ctrl+Z / tcsetpgrp の SIGTTOU を壊さない)。
        if (sig == SIGSTOP || sig == SIGTSTP || sig == SIGTTIN || sig == SIGTTOU) {
            siginfo_t si;
            if (ptrace(PTRACE_GETSIGINFO, pid, 0, &si) < 0) continue;
        }

        // Android seccomp が untrusted_app に禁ずる syscall の SIGSYS。配送せず
        // ここで戻り値を作る(SIGSYS 停止時 x8 は syscall 番号を保持)。
        //
        // 偽装方針を 2 つに分ける:
        //  (A) fakeroot 用の権限変更系(set*id/setgroups=143-159, chown=54/55,
        //      chmod=52/53)だけ 0(成功)に化かす。戻り値だけで成立し、バッファ
        //      書き換えが要らないので syscall 未実行の SIGSYS でも 0 で足りる。
        //  (B) それ以外は全て -ENOSYS。claude(node) が使う新 syscall
        //      (io_uring=425-427 / epoll_pwait2=441 / clone3=435 / statx=291 /
        //      close_range=436 / faccessat2=439 等)を 0 偽装すると、libc/libuv が
        //      「成功」と誤認してフォールバックせず、(a) epoll_pwait2 は待たず即
        //      0 イベントでループ空回り→無反応、(b) statx は未初期化バッファを
        //      正常な stat と誤読、(c) clone3 はスレッド未生成のまま 0 を返し
        //      自分を子と誤認、(d) io_uring は ring fd=0 を uv__close で abort、と
        //      いずれもハング/異常終了する。-ENOSYS を見せて既存の安全な経路
        //      (epoll_pwait/fstatat/clone 等)へ退避させる(proot も同経路で動く)。
        //      `claude --version' 等の即終了系は (B) のループに入らず動くが、
        //      対話起動は (B) のループ待ちでハングする、が実機の観測と一致。
        if (sig == SIGSYS) {
            struct user_pt_regs regs;
            if (get_regs(pid, &regs) == 0) {
                long nr = (long)regs.regs[8];
                int priv = (nr == 143 || nr == 144 || nr == 145 || nr == 146 ||
                            nr == 147 || nr == 149 || nr == 151 || nr == 152 ||
                            nr == 159 || nr == 54  || nr == 55  || nr == 52  ||
                            nr == 53);
                regs.regs[0] = priv ? 0UL : (unsigned long)-38L /* -ENOSYS */;
                set_regs(pid, &regs);
                if (g_trc_on)
                    fprintf(g_trc, "[z2trc] SIGSYS nr=%ld -> %s pid=%d\n",
                            nr, priv ? "0" : "ENOSYS", pid);
            }
            z_resume(pid, seccomp_mode, state_for(pid), 0);
            continue;
        }

        // それ以外(signal-delivery-stop / 通常シグナル)はそのまま転送。
        if (g_trace) {
            siginfo_t si; memset(&si, 0, sizeof si);
            int gr = ptrace(PTRACE_GETSIGINFO, pid, 0, &si);
            fprintf(g_trc, "[z2trc] pid=%d DELIVER sig=%d gr=%d si_code=%d si_pid=%d si_addr=%p\n",
                    pid, sig, gr, gr==0?si.si_code:-999, gr==0?si.si_pid:-1,
                    gr==0?si.si_addr:NULL);
            // SEGV/BUS/ILL は致命的: 落ちる瞬間の guest レジスタを全ダンプして
            // 「pc 自体が野良(call先破壊) か / 正規 pc が壊れたデータを読んだか」を切り分ける。
            if (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL) {
                struct user_pt_regs cr;
                if (get_regs(pid, &cr) == 0) {
                    fprintf(g_trc, "[z2trc]   pc=0x%llx lr=0x%llx sp=0x%llx\n",
                            (unsigned long long)cr.pc, (unsigned long long)cr.regs[30],
                            (unsigned long long)cr.sp);
                    for (int i = 0; i < 31; i += 4) {
                        fprintf(g_trc, "[z2trc]   x%-2d=0x%016llx x%-2d=0x%016llx x%-2d=0x%016llx x%-2d=0x%016llx\n",
                                i,   (unsigned long long)cr.regs[i],
                                i+1, (unsigned long long)(i+1<31?cr.regs[i+1]:0),
                                i+2, (unsigned long long)(i+2<31?cr.regs[i+2]:0),
                                i+3, (unsigned long long)(i+3<31?cr.regs[i+3]:0));
                    }
                }
            }
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
static void load_elf_and_jump(const char *path, char **child_argv, char **child_envp,
                              int skip_reloc) {
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

    // ローダの RELATIVE/RELR 肩代わり＋phdr バイアスは「自己 relocation しない bionic
    // (Android NDK)の static-PIE crt」専用の救済。glibc/musl の ld.so や static-PIE は
    // 自身で relocation するため肩代わりすると二重適用になり、さらに phdr バイアスで
    // PT_DYNAMIC.p_vaddr が base 分二重算入されて musl の base 自己算出
    // (base = 実行時 &_DYNAMIC − PT_DYNAMIC.p_vaddr)が 0 になり SIGSEGV する。
    // bionic ELF のみが PT_NOTE に ".note.android.ident"(owner="Android")を持つので
    // これで両者を判別し、肩代わりを bionic に限定する。
    // (Alpine 起動退行の真因: ld-musl を直接 exec すると PT_INTERP 非保持ゆえ case 3=
    //  skip_reloc=0 に落ち、glibc/musl なのに肩代わりされていた)
    int is_bionic = 0;
    for (unsigned i = 0; i < e_phnum && !is_bionic; i++) {
        unsigned int p_type; memcpy(&p_type, ph[i], 4);
        if (p_type != 4 /* PT_NOTE */) continue;
        unsigned long n_off, n_sz;
        memcpy(&n_off, ph[i] + 8,  8);
        memcpy(&n_sz,  ph[i] + 32, 8);
        if (n_sz == 0 || n_sz > 4096) continue;
        unsigned char nbuf[4096];
        if (pread(fd, nbuf, n_sz, (off_t)n_off) != (ssize_t)n_sz) continue;
        unsigned long p = 0;
        while (p + 12 <= n_sz) {
            unsigned int namesz, descsz;
            memcpy(&namesz, nbuf + p,     4);
            memcpy(&descsz, nbuf + p + 4, 4);
            unsigned long name_off = p + 12;
            if (namesz == 8 && name_off + 8 <= n_sz &&
                memcmp(nbuf + name_off, "Android", 8) == 0) { is_bionic = 1; break; }
            unsigned long adv = 12 + ((namesz + 3UL) & ~3UL) + ((descsz + 3UL) & ~3UL);
            if (adv <= 12) break;
            p += adv;
        }
    }
    // skip_reloc(case 2 の ld.so) でなく、かつ bionic ELF のときだけ肩代わりする。
    int apply_loader_reloc = (!skip_reloc && is_bionic);

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

    // ET_DYN(static-PIE)はカーネルもインタプリタも relocation を適用しない。
    // bionic NDK の static-PIE crt は自己 relocation しないため、ローダが RELR/RELA の
    // R_AARCH64_RELATIVE を肩代わり適用する(ld.so/proot loader 相当)。これで単純な
    // static-PIE(write のみ)が動く。static 非PIE(ET_EXEC)は base==0 で素通り=退行なし。
    //
    // ただし「自己 relocation する ELF」(glibc/musl の ld.so 本体や、自己再配置 crt を持つ
    // static-PIE プログラム)に対してローダが肩代わり適用すると、エントリ後に本体がもう一度
    // RELATIVE を適用して load bias が二重加算され、全ポインタが ×2 になり SIGSEGV(blr x8 で
    // x8=実ポインタ×2)する。case 2(ld.so 経由の動的起動)は skip_reloc=1 で、case 3 に落ちる
    // glibc/musl 系(bionic note 無し)は is_bionic=0 で、いずれも apply_loader_reloc=0 になり抑止。
    if (apply_loader_reloc && e_type == 3 /* ET_DYN */ && base != 0) {
        unsigned long dyn_va = 0;
        for (unsigned i = 0; i < e_phnum; i++) {
            unsigned int p_type; memcpy(&p_type, ph[i], 4);
            if (p_type == 2 /* PT_DYNAMIC */) { memcpy(&dyn_va, ph[i] + 16, 8); break; }
        }
        if (dyn_va) {
            unsigned long *dyn = (unsigned long *)(base + dyn_va);
            unsigned long relr = 0, relrsz = 0, rela = 0, relasz = 0, relaent = 24;
            for (unsigned long *d = dyn; d[0] != 0 /* DT_NULL */; d += 2) {
                unsigned long tag = d[0], val = d[1];
                switch (tag) {
                    case 36: /* DT_RELR */    case 0x6fffe000: /* DT_ANDROID_RELR */    relr = val; break;
                    case 35: /* DT_RELRSZ */  case 0x6fffe001: /* DT_ANDROID_RELRSZ */  relrsz = val; break;
                    case 7:  /* DT_RELA */    rela = val; break;
                    case 8:  /* DT_RELASZ */  relasz = val; break;
                    case 9:  /* DT_RELAENT */ relaent = val; break;
                }
            }
            if (rela && relaent) {
                for (unsigned long off = 0; off + relaent <= relasz; off += relaent) {
                    unsigned long *r = (unsigned long *)(base + rela + off);
                    unsigned long r_offset = r[0], r_info = r[1], r_addend = r[2];
                    if ((r_info & 0xffffffff) == 1027 /* R_AARCH64_RELATIVE */)
                        *(unsigned long *)(base + r_offset) = base + r_addend;
                }
            }
            if (relr) {
                unsigned long *p = (unsigned long *)(base + relr);
                unsigned long *pend = p + relrsz / 8;
                unsigned long *where = NULL;
                for (; p < pend; p++) {
                    unsigned long e = *p;
                    if ((e & 1) == 0) {
                        where = (unsigned long *)(base + e);
                        *where++ += base;
                    } else {
                        for (int i = 0; (e >>= 1) != 0; i++)
                            if (e & 1) where[i] += base;
                        where += 63;
                    }
                }
            }
        }
    }

    unsigned long entry    = base + e_entry;
    // AT_PHDR は phdr の「仮想アドレス」。PT_PHDR があればそれを使う。無い場合、
    // ファイルオフセット e_phoff を含む PT_LOAD を探し p_vaddr/p_offset 差で vaddr へ
    // 変換する。ET_EXEC(p_vaddr != p_offset、例 0x400000)では e_phoff をそのまま
    // AT_PHDR に渡すと musl/bionic の起動が phdr を 0 番地近傍と誤認して segfault する
    // (静的 musl の apk.static 等。PT_PHDR を持たず first LOAD が vaddr 0x400000)。
    unsigned long phdr_mem;
    if (has_pt_phdr) {
        phdr_mem = base + pt_phdr_va;
    } else {
        phdr_mem = base + e_phoff;  // フォールバック(first LOAD が offset0/vaddr0 のとき正)
        for (unsigned i = 0; i < e_phnum; i++) {
            unsigned int p_type; memcpy(&p_type, ph[i], 4);
            if (p_type != PT_LOAD_Z) continue;
            unsigned long p_offset, p_vaddr, p_filesz;
            memcpy(&p_offset, ph[i] + 8,  8);
            memcpy(&p_vaddr,  ph[i] + 16, 8);
            memcpy(&p_filesz, ph[i] + 32, 8);
            if (e_phoff >= p_offset && e_phoff < p_offset + p_filesz) {
                phdr_mem = base + p_vaddr + (e_phoff - p_offset);
                break;
            }
        }
    }

    // bionic の static 起動(__libc_init_mte / __bionic_get_tls_segment)は load_bias を
    // 即値 0 と仮定し phdr->p_vaddr を絶対アドレスとして扱う(=ET_EXEC 前提)。ET_DYN
    // (static-PIE)では p_vaddr がリンク時相対のため note/TLS 走査が 0 番地近傍を触って
    // segfault する。p_vaddr を base で事前バイアスした phdr コピーを AT_PHDR に渡し、
    // bionic の bias=0 仮定を成立させる。
    // ただし glibc/musl(bionic note 無し)ではバイアスしてはならない。musl ld.so は AT_PHDR
    // の PT_DYNAMIC.p_vaddr から自身の load base を逆算(base = 実行時 &_DYNAMIC −
    // PT_DYNAMIC.p_vaddr)するため、事前バイアスすると base が二重算入で 0 になり SIGSEGV する
    // (Alpine 起動 exitCode=-1 の真因)。glibc ld.so は GOT 相対ブートストラップで AT_PHDR に
    // 非依存のため顕在化しなかった。apply_loader_reloc(=bionic かつ非 skip_reloc)に限定。
    if (apply_loader_reloc && e_type == 3 /* ET_DYN */ && base != 0) {
        static unsigned char ph_biased[MAX_PH][56];
        for (unsigned i = 0; i < e_phnum; i++) {
            memcpy(ph_biased[i], ph[i], 56);
            unsigned long v; memcpy(&v, ph[i] + 16, 8); v += base;
            memcpy(ph_biased[i] + 16, &v, 8);
        }
        phdr_mem = (unsigned long)ph_biased;
    }

    // この時点で ET_EXEC のセグメント MAP_FIXED がローダ自身の heap(0x400000 付近)を
    // 上書きしている可能性がある。malloc を使う fprintf は壊れた arena を触り得るので、
    // デバッグ出力は stack バッファ + write(2) で malloc-free に行う。
    int dbg = (getenv("Z2ROOT_LOADER_DEBUG") != NULL);
    if (dbg) {
        char b[256];
        int l = snprintf(b, sizeof(b),
                "z2root loader: type=%u base=%lx e_entry=%lx entry=%lx "
                "phdr=%lx phnum=%u phent=%u has_ptphdr=%d\n",
                e_type, base, e_entry, entry, phdr_mem, e_phnum, e_phentsize, has_pt_phdr);
        if (l > 0) write(2, b, (size_t)l);
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
        char b[160];
        int l = snprintf(b, sizeof(b),
                "z2root loader: argc=%d envc=%d words=%d sp=%p JUMPING\n",
                argc_n, envc_n, words, (void *)sp);
        if (l > 0) write(2, b, (size_t)l);
    }

    // sp を新フレームへ、x0=0(rtld_fini) で entry へ分岐。戻らない。
    __asm__ volatile(
        "mov sp, %0\n"
        "mov x0, #0\n"
        "br  %1\n"
        : : "r"(sp), "r"(entry) : "memory", "x0");
    __builtin_unreachable();
}

// PT_LOAD を匿名 PROT_EXEC メモリへマップし base/entry/phdr を返す(自己 relocation する
// ld.so 本体と、ld.so が再配置する ET_EXEC 本体専用。ローダ側 relocation 肩代わりは行わない)。
// 成功 0 / 失敗 -1。load_exec_via_interp が本体・ld.so を各 1 回マップするのに使う。
struct img_map { unsigned long base, entry, phdr, phent, phnum; };
static int map_img(const char *path, struct img_map *out) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    unsigned char eh[64];
    if (pread(fd, eh, sizeof(eh), 0) != (ssize_t)sizeof(eh)) { close(fd); return -1; }
    if (memcmp(eh, "\x7f""ELF", 4) != 0) { close(fd); return -1; }
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

    unsigned char ph[MAX_PH][56];
    if (e_phnum > MAX_PH) e_phnum = MAX_PH;
    for (unsigned i = 0; i < e_phnum; i++)
        if (pread(fd, ph[i], 56, (off_t)(e_phoff + (unsigned long)i * e_phentsize)) != 56) {
            close(fd); return -1;
        }

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
    if (min_va == ~0UL) { close(fd); return -1; }

    // ET_DYN(ld.so)は連続領域を予約して base を決める。ET_EXEC(本体)は p_vaddr そのまま(base=0)。
    unsigned long base = 0;
    if (e_type == 3 /* ET_DYN */) {
        unsigned long span = ((max_va + pmask) & ~pmask) - (min_va & ~pmask);
        void *resv = mmap(NULL, span, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (resv == MAP_FAILED) { close(fd); return -1; }
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
        void *seg = mmap((void *)seg_start, seg_end - seg_start,
                         PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED, -1, 0);
        if (seg == MAP_FAILED) { close(fd); return -1; }
        if (p_filesz > 0 &&
            pread(fd, (char *)seg_start + off_in_pg, p_filesz, (off_t)p_offset) != (ssize_t)p_filesz) {
            close(fd); return -1;
        }
        int prot = ((p_flags & PF_R_Z) ? PROT_READ : 0) |
                   ((p_flags & PF_W_Z) ? PROT_WRITE : 0) |
                   ((p_flags & PF_X_Z) ? PROT_EXEC : 0);
        if (mprotect((void *)seg_start, seg_end - seg_start, prot) != 0) { close(fd); return -1; }
    }
    close(fd);

    unsigned long phdr_mem;
    if (has_pt_phdr) {
        phdr_mem = base + pt_phdr_va;
    } else {
        phdr_mem = base + e_phoff;
        for (unsigned i = 0; i < e_phnum; i++) {
            unsigned int p_type; memcpy(&p_type, ph[i], 4);
            if (p_type != PT_LOAD_Z) continue;
            unsigned long p_offset, p_vaddr, p_filesz;
            memcpy(&p_offset, ph[i] + 8,  8);
            memcpy(&p_vaddr,  ph[i] + 16, 8);
            memcpy(&p_filesz, ph[i] + 32, 8);
            if (e_phoff >= p_offset && e_phoff < p_offset + p_filesz) {
                phdr_mem = base + p_vaddr + (e_phoff - p_offset);
                break;
            }
        }
    }
    out->base  = base;
    out->entry = base + e_entry;
    out->phdr  = phdr_mem;
    out->phent = e_phentsize;
    out->phnum = e_phnum;
    return 0;
}

// 動的 ET_EXEC(非PIE)本体を ld.so 経由で起動する。musl ld.so は ET_EXEC の「コマンド明示
// 起動」を拒否する(`Not a valid dynamic program`)ため、本体と ld.so を両方マップし、カーネルが
// PT_INTERP 経由で exec したのと同じ初期スタックを組む: AT_PHDR/ENTRY=本体, AT_BASE=ld.so の
// load base。これで musl は「インタプリタとして起動された」と判定し、本体を relocation して
// 起動する(proot loader 相当)。child_argv は本体に渡す argv([argv0, args...])。戻らない。
__attribute__((noreturn))
static void load_exec_via_interp(const char *interp_path, const char *prog_path,
                                 char **child_argv, char **child_envp) {
    struct img_map prog, interp;
    if (map_img(prog_path, &prog) != 0)     loader_fail("map prog", prog_path);
    if (map_img(interp_path, &interp) != 0) loader_fail("map interp", interp_path);

    long pagesz = sysconf(_SC_PAGESIZE);
    if (pagesz <= 0) pagesz = 4096;

    int dbg = (getenv("Z2ROOT_LOADER_DEBUG") != NULL);
    if (dbg) {
        char b[256];
        int l = snprintf(b, sizeof(b),
                "z2root loader-exec: prog entry=%lx phdr=%lx phnum=%lu  interp base=%lx entry=%lx\n",
                prog.entry, prog.phdr, prog.phnum, interp.base, interp.entry);
        if (l > 0) write(2, b, (size_t)l);
    }

    int argc_n = 0; while (child_argv[argc_n]) argc_n++;
    int envc_n = 0; while (child_envp[envc_n]) envc_n++;

    unsigned long av[][2] = {
        { AT_PHDR_Z,   prog.phdr },     // 本体の phdr(ld.so はこれを見て「自分はインタプリタ」と判定)
        { AT_PHENT_Z,  prog.phent },
        { AT_PHNUM_Z,  prog.phnum },
        { AT_PAGESZ_Z, (unsigned long)pagesz },
        { AT_BASE_Z,   interp.base },   // ld.so の load base(非0=インタプリタ起動の目印)
        { AT_FLAGS_Z,  0 },
        { AT_ENTRY_Z,  prog.entry },    // 本体(ET_EXEC)のエントリ。ld.so が relocation 後にここへ飛ぶ
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

    int words = 1 + (argc_n + 1) + (envc_n + 1) + (naux * 2 + 2);
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

    // ld.so の entry へ分岐(ld.so が自己 relocation→本体 relocation→AT_ENTRY へ飛ぶ)。
    __asm__ volatile(
        "mov sp, %0\n"
        "mov x0, #0\n"
        "br  %1\n"
        : : "r"(sp), "r"(interp.entry) : "memory", "x0");
    __builtin_unreachable();
}

// --loader[/--loader-noreloc] <elf> <argv0> [args...]: 自前ローダのエントリ。
// --loader-noreloc は対象 ELF が自己 relocation する(ld.so 本体など)ため、ローダ側の
// RELATIVE/RELR 肩代わりを抑止する(二重 relocation 防止)。戻らない(失敗時のみ _exit)。
__attribute__((noreturn))
static void loader_main(int argc, char **argv) {
    if (getenv("Z2ROOT_LOADER_DEBUG")) {
        char b[256];
        int l = snprintf(b, sizeof(b), "z2root loader_main: argc=%d a1=%s a2=%s a3=%s\n",
                         argc, argc>1?argv[1]:"-", argc>2?argv[2]:"-", argc>3?argv[3]:"-");
        write(2, b, l);
    }
    // --loader-exec <ld.so> <prog> <argv0> [args...]: 動的 ET_EXEC を ld.so 経由で起動。
    if (strcmp(argv[1], "--loader-exec") == 0) {
        if (argc < 5) {
            fprintf(stderr, "z2root loader: usage: --loader-exec <ld.so> <prog> <argv0> [args...]\n");
            _exit(2);
        }
        load_exec_via_interp(argv[2], argv[3], &argv[4], environ);
    }
    if (argc < 4) {
        fprintf(stderr, "z2root loader: usage: --loader[/--loader-noreloc] <elf> <argv0> [args...]\n");
        _exit(2);
    }
    int skip_reloc = (strcmp(argv[1], "--loader-noreloc") == 0);
    load_elf_and_jump(argv[2], &argv[3], environ, skip_reloc);
}

int main(int argc, char **argv) {
    // --loader モード(自分自身を nativeLibraryDir から execve して入る)を最優先で分岐。
    if (argc >= 2 && (strcmp(argv[1], "--loader") == 0 ||
                      strcmp(argv[1], "--loader-noreloc") == 0 ||
                      strcmp(argv[1], "--loader-exec") == 0)) loader_main(argc, argv);

    long pgsz = sysconf(_SC_PAGESIZE);
    if (pgsz > 0) g_pagesize = (unsigned long)pgsz;

    struct config cfg;
    char *const *command = parse_args(argc, argv, &cfg);
    cfg.command = command;

    // トレーサ自身は seccomp 偽装の対象外なので getuid/getgid は実値(=Android アプリ uid)。
    // fake_root 下の tracee が SCM_CREDENTIALS を送る際、この実値へ書き換えてカーネルの
    // 資格チェック(claimed uid == 実 uid or CAP_SETUID)を満たし EPERM を避ける。
    cfg.real_uid = getuid();
    cfg.real_gid = getgid();

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
