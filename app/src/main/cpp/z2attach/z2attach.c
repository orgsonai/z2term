// z2attach — アプリのタブに繋ぎっぱなしにするクライアント (GPL-3.0)。
//
// `z2-session attach <先>` の実体。アプリが開いている AF_UNIX の受付ソケットへ繋ぎ、
// 自分の標準入出力とタブの PTY を素通しで繋ぐ。抜けるのは **`Ctrl+]`**、または
// **行頭の `~.`**(ssh と同じ)。
//
// ⚠ **`~.` だけでは SSH 越しに抜けられない**(0.8.369・実機で指摘)。ssh クライアントの
// エスケープも「行頭の `~`」なので、SSH でログインした先で attach して `~.` を打つと、
// **手前の ssh が先に食って SSH ごと切れる**(内側へは 1 バイトも届かない)。ssh 多段と同じ
// `~~.` で抜けられはするが、覚え方を押し付けるだけなので、**ssh と衝突しないキーを併設する**。
// `Ctrl+]`(0x1D) は ssh のエスケープ処理を素通りするので、何段越しでも必ずここへ届く。
//
// なぜ /bin/sh で書けないか:
//   - 端末を raw にする (tcsetattr) 手段が無い。canonical のままだと Ctrl+C も矢印も
//     行単位に丸められてタブへ届かない。
//   - 標準入力とソケットを同時に待つ (poll) 手段が無い。
//   - 窓の大きさの変化 (SIGWINCH) を受け取る手段が無い。
//
// ⚠ 置き場は jniLibs の lib*.so 名 (= APK 導入時に nativeLibraryDir へ展開される唯一の形)。
// そこから rootfs の /usr/local/bin/z2attach へ配られる (ProotLauncher)。z2accept シムと同じ流儀。
//
// ⚠ z2root 配下で動く前提。connect(2) のパスは z2root が翻訳するので、ここではゲストから
// 見えるパス (/root/.z2term/attach.sock) をそのまま渡す。翻訳の実績は 0.8.327 (gpg-agent)。

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <termios.h>
#include <unistd.h>

// --- フレーム (アプリ側 AttachServer.kt と同じ定義) ---------------------------
// [種類 1 byte][長さ 2 byte BE][中身]
#define F_DATA   0  // 端末データ (双方向)
#define F_SIZE   1  // 広さ "<行> <列>" (client -> app)
#define F_NOTICE 2  // お知らせ / 断り (app -> client)
#define F_TARGET 3  // 繋ぎ先の文字列 (client -> app・最初の 1 通)

#define MAX_PAYLOAD 8192  // 読み取りの塊と同じ。長さ 2 byte に必ず収まる

// 抜けるキー。⚠ **押した瞬間に抜ける**(次の 1 文字を待たない)。待つ作りにすると
// 「押したのに抜けない」と見えるほうが害が大きい。この 1 文字をタブへ送りたいときは
// 行頭で `~` に続けて打つ (`~` の次は素通し)。
#define DETACH_KEY 0x1D  // Ctrl+]

static struct termios saved_tio;
static int tio_saved = 0;
static volatile sig_atomic_t winch_pending = 0;

static void restore_tio(void) {
    if (tio_saved) {
        tcsetattr(STDIN_FILENO, TCSAFLUSH, &saved_tio);
        tio_saved = 0;
    }
}

// ⚠ 異常終了でも必ず端末を戻す。戻さないと「抜けたあと自分の端末が壊れたまま」になり、
//    それが一番たちの悪い壊れ方 (打った文字が見えない/改行が効かない)。
static void on_fatal_signal(int sig) {
    restore_tio();
    _exit(128 + sig);
}

static void on_winch(int sig) {
    (void)sig;
    winch_pending = 1;
}

/** 中身を全部書き切る (短い write を繰り返す)。 */
static int write_all(int fd, const unsigned char *buf, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = write(fd, buf + off, len - off);
        if (n > 0) { off += (size_t)n; continue; }
        if (n < 0 && errno == EINTR) continue;
        return -1;
    }
    return 0;
}

static int send_frame(int fd, unsigned char type, const unsigned char *payload, size_t len) {
    unsigned char head[3];
    if (len > MAX_PAYLOAD) len = MAX_PAYLOAD;
    head[0] = type;
    head[1] = (unsigned char)((len >> 8) & 0xFF);
    head[2] = (unsigned char)(len & 0xFF);
    if (write_all(fd, head, 3) < 0) return -1;
    if (len > 0 && write_all(fd, payload, len) < 0) return -1;
    return 0;
}

/** 今の窓の大きさをアプリへ伝える。取れなければ何もしない (勝手な既定値を送らない)。 */
static int send_size(int fd) {
    struct winsize ws;
    if (ioctl(STDIN_FILENO, TIOCGWINSZ, &ws) < 0) return 0;
    if (ws.ws_row == 0 || ws.ws_col == 0) return 0;
    char buf[64];
    int n = snprintf(buf, sizeof(buf), "%u %u", (unsigned)ws.ws_row, (unsigned)ws.ws_col);
    if (n <= 0) return 0;
    return send_frame(fd, F_SIZE, (const unsigned char *)buf, (size_t)n);
}

/** ちょうど len バイト読む。相手が閉じたら 0、失敗は -1。 */
static int read_exact(int fd, unsigned char *buf, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = read(fd, buf + off, len - off);
        if (n > 0) { off += (size_t)n; continue; }
        if (n == 0) return 0;
        if (errno == EINTR) continue;
        return -1;
    }
    return 1;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: z2attach <tab>\n");
        return 2;
    }
    const char *target = argv[1];

    // ⚠ 人が打つためのもの。マクロから叩かれた (端末でない) ときは、raw にしようがないので
    //    黙って変な状態になる前に断る。
    if (!isatty(STDIN_FILENO) || !isatty(STDOUT_FILENO)) {
        fprintf(stderr, "z2-session attach: not a terminal "
                        "(attach is for typing; use send / capture from a script)\n");
        return 2;
    }

    const char *sock_path = getenv("Z2_ATTACH_SOCK");
    if (sock_path == NULL || sock_path[0] == '\0') sock_path = "/root/.z2term/attach.sock";

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    // ⚠ sun_path は 108 バイト。受付を 1 本にしてあるので実運用では溢れないが、
    //    Z2_ATTACH_SOCK で差し替えられる以上ここで必ず確かめる。
    if (strlen(sock_path) >= sizeof(addr.sun_path)) {
        fprintf(stderr, "z2-session attach: socket path too long: %s\n", sock_path);
        return 1;
    }
    strncpy(addr.sun_path, sock_path, sizeof(addr.sun_path) - 1);

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        fprintf(stderr, "z2-session attach: socket: %s\n", strerror(errno));
        return 1;
    }
    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        // ⚠ ここが「アプリが動いていない」の唯一の現れ方。理由を言い切る。
        fprintf(stderr, "z2-session attach: cannot reach the app (%s). "
                        "Is z2term running?\n", strerror(errno));
        close(fd);
        return 1;
    }

    // 繋ぎ先と「自分が居るタブ」を 1 通で渡す。改行より後ろが自分の id (無ければ省く)。
    //
    // ⭐ **自分自身へ繋ぐと暴走する。** 送った先の出力はこちらの標準出力へ書かれ、それは
    // 同じタブの出力なので、また送られてくる。止まらないので、アプリ側 (AttachServer) が
    // 断れるように「呼んだ側がどのタブか」を渡す。ゲストの中からタブを知る手段はこの
    // 環境変数だけ ([ProotLauncher] が起動時に入れる)。
    //
    // ⚠ 古いアプリと新しい z2attach の組み合わせでは、改行から後ろは
    // 「繋ぎ先の名前の一部」として扱われて**そんなタブは無いと言われる**。z2attach は
    // 起動のたびにアプリが rootfs へ配り直すので実運用では起きないが、id が無いときに
    // 改行ごと省いておけば、少なくとも普段の使い方では以前と同じバイト列になる。
    const char *self = getenv("Z2_SESSION_ID");
    unsigned char hello[MAX_PAYLOAD];
    size_t hello_len = strlen(target);
    if (hello_len > sizeof(hello)) hello_len = sizeof(hello);
    memcpy(hello, target, hello_len);
    if (self != NULL && self[0] != '\0') {
        size_t self_len = strlen(self);
        if (hello_len + 1 + self_len <= sizeof(hello)) {
            hello[hello_len++] = '\n';
            memcpy(hello + hello_len, self, self_len);
            hello_len += self_len;
        }
    }

    if (send_frame(fd, F_TARGET, hello, hello_len) < 0 ||
        send_size(fd) < 0) {
        fprintf(stderr, "z2-session attach: cannot talk to the app\n");
        close(fd);
        return 1;
    }

    // 最初の返事は必ず F_NOTICE。"OK <タブ名>" か "ERR <理由>"。
    unsigned char head[3];
    int r = read_exact(fd, head, 3);
    if (r <= 0 || head[0] != F_NOTICE) {
        fprintf(stderr, "z2-session attach: no answer from the app\n");
        close(fd);
        return 1;
    }
    size_t nlen = ((size_t)head[1] << 8) | (size_t)head[2];
    if (nlen > MAX_PAYLOAD) nlen = MAX_PAYLOAD;
    unsigned char notice[MAX_PAYLOAD + 1];
    if (nlen > 0 && read_exact(fd, notice, nlen) <= 0) {
        fprintf(stderr, "z2-session attach: no answer from the app\n");
        close(fd);
        return 1;
    }
    notice[nlen] = '\0';
    if (strncmp((char *)notice, "OK ", 3) != 0) {
        const char *msg = (char *)notice;
        if (strncmp(msg, "ERR ", 4) == 0) msg += 4;
        fprintf(stderr, "z2-session attach: %s\n", msg);
        close(fd);
        return 1;
    }

    // --- ここから素通し ---------------------------------------------------
    if (tcgetattr(STDIN_FILENO, &saved_tio) == 0) {
        struct termios raw = saved_tio;
        cfmakeraw(&raw);
        raw.c_cc[VMIN] = 1;
        raw.c_cc[VTIME] = 0;
        if (tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw) == 0) tio_saved = 1;
    }
    atexit(restore_tio);
    signal(SIGWINCH, on_winch);
    signal(SIGHUP, on_fatal_signal);
    signal(SIGTERM, on_fatal_signal);
    signal(SIGPIPE, SIG_IGN);

    // ⚠ SSH 越しでは `~.` は**手前の ssh に食われて届かない**ので、案内に出さない。
    //    出すと「書いてあるとおり打ったら SSH ごと切れた」になる。
    if (getenv("SSH_TTY") != NULL || getenv("SSH_CONNECTION") != NULL ||
        getenv("SSH_CLIENT") != NULL) {
        fprintf(stderr, "[%s — 抜けるには Ctrl+] / detach with Ctrl+]]\r\n",
                (char *)notice + 3);
    } else {
        fprintf(stderr, "[%s — 抜けるには Ctrl+] か行頭で ~. / "
                        "detach with Ctrl+] or ~. at start of line]\r\n",
                (char *)notice + 3);
    }

    // 抜け方は 2 通り: `Ctrl+]` はどこでも即座に、`~.` は行頭のときだけ (ssh と同じ)。
    // 行頭で `~` を見たら次の 1 文字を待つ。`~~` は `~` 1 文字、`~Ctrl+]` は Ctrl+] 1 文字。
    int at_line_start = 1;
    int in_escape = 0;
    int exit_code = 0;
    unsigned char in_buf[MAX_PAYLOAD];
    unsigned char out_buf[MAX_PAYLOAD + 2];  // 持ち越した `~` のぶん余裕を取る
    unsigned char pay[MAX_PAYLOAD + 1];

    for (;;) {
        if (winch_pending) {
            winch_pending = 0;
            if (send_size(fd) < 0) break;
        }
        struct pollfd pfd[2];
        pfd[0].fd = STDIN_FILENO; pfd[0].events = POLLIN; pfd[0].revents = 0;
        pfd[1].fd = fd;           pfd[1].events = POLLIN; pfd[1].revents = 0;
        int pr = poll(pfd, 2, -1);
        if (pr < 0) {
            if (errno == EINTR) continue;  // SIGWINCH はここへ来る
            break;
        }

        // 打った文字 -> アプリ
        if (pfd[0].revents & (POLLIN | POLLHUP)) {
            ssize_t n = read(STDIN_FILENO, in_buf, sizeof(in_buf));
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) break;
            size_t out_len = 0;
            int detach = 0;
            for (ssize_t i = 0; i < n; i++) {
                unsigned char c = in_buf[i];
                if (in_escape) {
                    in_escape = 0;
                    if (c == '.') { detach = 1; break; }
                    // `~~` なら `~` 1 文字。`~Ctrl+]` なら Ctrl+] 1 文字 (抜けずにタブへ送る
                    // 唯一の手段)。それ以外は `~` と本人の両方を通す。
                    if (c == DETACH_KEY) { out_buf[out_len++] = DETACH_KEY; at_line_start = 0; continue; }
                    out_buf[out_len++] = '~';
                    if (c == '~') { at_line_start = 0; continue; }
                    out_buf[out_len++] = c;
                    at_line_start = (c == '\r' || c == '\n');
                    continue;
                }
                if (c == DETACH_KEY) { detach = 1; break; }
                if (at_line_start && c == '~') { in_escape = 1; continue; }
                out_buf[out_len++] = c;
                at_line_start = (c == '\r' || c == '\n');
            }
            if (out_len > 0 && send_frame(fd, F_DATA, out_buf, out_len) < 0) break;
            if (detach) break;
        }

        // タブの出力 -> 画面
        if (pfd[1].revents & (POLLIN | POLLHUP)) {
            if (read_exact(fd, head, 3) <= 0) break;
            size_t len = ((size_t)head[1] << 8) | (size_t)head[2];
            if (len > MAX_PAYLOAD) break;  // 約束を破ったフレーム。切る
            if (len > 0 && read_exact(fd, pay, len) <= 0) break;
            if (head[0] == F_DATA) {
                if (len > 0 && write_all(STDOUT_FILENO, pay, len) < 0) break;
            } else if (head[0] == F_NOTICE) {
                // タブが閉じた等。理由を出して終わる
                pay[len] = '\0';
                restore_tio();
                fprintf(stderr, "\r\n[%s]\r\n", (char *)pay);
                exit_code = 1;
                close(fd);
                return exit_code;
            }
        }
    }

    restore_tio();
    fprintf(stderr, "\r\n");
    close(fd);
    return exit_code;
}
