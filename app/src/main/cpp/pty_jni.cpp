// Z2Term PTY ネイティブ実装
// forkpty / signal / ioctl 周りを JNI で公開する。
//
// 注意:
// - Android Bionic libc には forkpty(3) が glibc と同じインターフェースで存在する (API 21+)
// - openpty() も同様に利用可能
// - termios の設定はおおむね POSIX 準拠

#include <jni.h>
#include <android/log.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "Z2Term-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/**
 * JNI 文字列配列を char* 配列に変換（末尾 NULL 付き）。
 * 呼び出し元が free_string_array() で解放する責務を持つ。
 */
char** to_c_string_array(JNIEnv* env, jobjectArray jarr) {
    if (jarr == nullptr) {
        char** result = (char**) malloc(sizeof(char*));
        result[0] = nullptr;
        return result;
    }
    jsize len = env->GetArrayLength(jarr);
    char** result = (char**) malloc(sizeof(char*) * (len + 1));
    for (jsize i = 0; i < len; ++i) {
        jstring jstr = (jstring) env->GetObjectArrayElement(jarr, i);
        const char* utf = env->GetStringUTFChars(jstr, nullptr);
        result[i] = strdup(utf);
        env->ReleaseStringUTFChars(jstr, utf);
        env->DeleteLocalRef(jstr);
    }
    result[len] = nullptr;
    return result;
}

void free_string_array(char** arr) {
    if (!arr) return;
    for (int i = 0; arr[i] != nullptr; ++i) {
        free(arr[i]);
    }
    free(arr);
}

/**
 * PTY 用の termios を標準的な対話シェル向け設定にする。
 */
void setup_terminal_modes(int fd) {
    struct termios tio;
    if (tcgetattr(fd, &tio) != 0) return;

    // 入力フラグ: CR を NL に変換、ブレーク無視
    tio.c_iflag |= (ICRNL | IXON | IXANY | IMAXBEL | BRKINT);
    tio.c_iflag &= ~(IGNBRK | INLCR | IGNCR | ISTRIP);

    // 出力フラグ: NL を CR-NL に
    tio.c_oflag |= (OPOST | ONLCR);

    // ローカルフラグ: エコー、行編集、シグナル生成
    tio.c_lflag |= (ISIG | ICANON | IEXTEN | ECHO | ECHOE | ECHOK | ECHOKE | ECHOCTL);
    tio.c_lflag &= ~(ECHONL | NOFLSH | TOSTOP);

    // 制御フラグ: 8bit、HUPCL（CD 喪失時にハングアップ）
    tio.c_cflag |= (CREAD | CS8 | HUPCL);
    tio.c_cflag &= ~(CSTOPB | PARENB);

    // 制御文字（標準的な値）
    tio.c_cc[VINTR]    = 0x03;  // Ctrl-C
    tio.c_cc[VQUIT]    = 0x1c;  // Ctrl-\
    tio.c_cc[VERASE]   = 0x7f;  // DEL
    tio.c_cc[VKILL]    = 0x15;  // Ctrl-U
    tio.c_cc[VEOF]     = 0x04;  // Ctrl-D
    tio.c_cc[VSTART]   = 0x11;  // Ctrl-Q
    tio.c_cc[VSTOP]    = 0x13;  // Ctrl-S
    tio.c_cc[VSUSP]    = 0x1a;  // Ctrl-Z
    tio.c_cc[VREPRINT] = 0x12;  // Ctrl-R
    tio.c_cc[VWERASE]  = 0x17;  // Ctrl-W
    tio.c_cc[VLNEXT]   = 0x16;  // Ctrl-V
    tio.c_cc[VDISCARD] = 0x0f;  // Ctrl-O
    tio.c_cc[VMIN]     = 1;
    tio.c_cc[VTIME]    = 0;

    tcsetattr(fd, TCSANOW, &tio);
}

} // namespace

extern "C" {

/**
 * forkpty() で PTY を作成し、子プロセスで execve() する。
 * 戻り値: fd と pid をパックした jlong。上位 32bit が fd、下位 32bit が pid。
 */
JNIEXPORT jlong JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeCreate(
        JNIEnv* env,
        jobject /* companion */,
        jstring jcmd,
        jobjectArray jargs,
        jobjectArray jenv,
        jstring jcwd,
        jint rows,
        jint cols) {

    const char* cmd = env->GetStringUTFChars(jcmd, nullptr);
    const char* cwd = env->GetStringUTFChars(jcwd, nullptr);
    char** argv = to_c_string_array(env, jargs);
    char** envp = to_c_string_array(env, jenv);

    LOGI("Creating PTY: cmd=%s, cwd=%s, rows=%d, cols=%d", cmd, cwd, rows, cols);

    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    int master_fd = -1;
    pid_t pid = forkpty(&master_fd, nullptr, nullptr, &ws);

    if (pid < 0) {
        LOGE("forkpty failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(jcmd, cmd);
        env->ReleaseStringUTFChars(jcwd, cwd);
        free_string_array(argv);
        free_string_array(envp);
        return ((jlong)(-1) << 32) | (jlong)0xFFFFFFFF;
    }

    if (pid == 0) {
        // ───────── 子プロセス ─────────
        // 制御端末になる
        setsid();
        ioctl(0, TIOCSCTTY, 0);

        // 作業ディレクトリ変更
        if (chdir(cwd) != 0) {
            LOGW("chdir to %s failed: %s", cwd, strerror(errno));
        }

        // termios 設定
        setup_terminal_modes(STDIN_FILENO);

        // 標準シグナルを既定値に戻す
        for (int sig = 1; sig < 32; ++sig) {
            signal(sig, SIG_DFL);
        }

        // execve で置き換え
        execve(cmd, argv, envp);

        // ここに来たら失敗
        LOGE("execve(%s) failed: %s", cmd, strerror(errno));
        _exit(127);
    }

    // ───────── 親プロセス ─────────
    LOGI("PTY created successfully: pid=%d, master_fd=%d", pid, master_fd);

    // master_fd を非ブロッキングモードに「しない」（read で待ちたい）
    // ただし O_CLOEXEC は付けておく
    int flags = fcntl(master_fd, F_GETFD);
    fcntl(master_fd, F_SETFD, flags | FD_CLOEXEC);

    env->ReleaseStringUTFChars(jcmd, cmd);
    env->ReleaseStringUTFChars(jcwd, cwd);
    free_string_array(argv);
    free_string_array(envp);

    // fd と pid をパック
    return ((jlong)master_fd << 32) | ((jlong)pid & 0xFFFFFFFF);
}

JNIEXPORT void JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeResize(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint fd,
        jint rows,
        jint cols) {
    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    if (ioctl(fd, TIOCSWINSZ, &ws) != 0) {
        LOGW("TIOCSWINSZ failed: %s", strerror(errno));
    }
}

JNIEXPORT void JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeSendSignal(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint pid,
        jint signal) {
    if (kill(pid, signal) != 0) {
        LOGW("kill(%d, %d) failed: %s", pid, signal, strerror(errno));
    }
}

JNIEXPORT jboolean JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeIsAlive(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint pid) {
    // kill(pid, 0) で生存確認
    if (kill(pid, 0) == 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeGetExitCode(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint pid) {
    int status = 0;
    pid_t result = waitpid(pid, &status, WNOHANG);
    if (result == pid) {
        if (WIFEXITED(status)) {
            return WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            return 128 + WTERMSIG(status);
        }
    }
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeWaitFor(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint pid) {
    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_nativeClose(
        JNIEnv* /* env */,
        jobject /* companion */,
        jint fd,
        jint pid) {
    LOGI("Closing PTY: fd=%d, pid=%d", fd, pid);
    close(fd);
    // SIGHUP 送信
    kill(pid, SIGHUP);
    // 1秒待ってまだ生きていたら SIGKILL
    for (int i = 0; i < 10; ++i) {
        usleep(100 * 1000);  // 100ms
        if (kill(pid, 0) != 0) break;  // 死んでいる
    }
    if (kill(pid, 0) == 0) {
        kill(pid, SIGKILL);
    }
    // waitpid で回収（ノンブロッキング）
    int status;
    waitpid(pid, &status, WNOHANG);
}

/**
 * int fd から java.io.FileDescriptor オブジェクトを生成。
 * リフレクションで private コンストラクタを叩く。
 */
JNIEXPORT jobject JNICALL
Java_com_zerotoship_z2term_pty_PtyProcess_00024Companion_createFileDescriptor(
        JNIEnv* env,
        jobject /* companion */,
        jint fd) {
    jclass fdClass = env->FindClass("java/io/FileDescriptor");
    if (!fdClass) return nullptr;

    jmethodID ctor = env->GetMethodID(fdClass, "<init>", "()V");
    if (!ctor) return nullptr;

    jobject fdObject = env->NewObject(fdClass, ctor);
    if (!fdObject) return nullptr;

    // descriptor フィールドに fd を設定
    jfieldID descriptorField = env->GetFieldID(fdClass, "descriptor", "I");
    if (descriptorField) {
        env->SetIntField(fdObject, descriptorField, fd);
    }
    return fdObject;
}

} // extern "C"
