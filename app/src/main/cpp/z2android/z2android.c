/* APK-installed entry point for Android-shell z2-* commands.
 * Writable scripts are read by the system shell, never executed as binaries.
 * Symlinks in the private command directory point at this installed executable.
 */
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifndef Z2_ANDROID_SHELL
#define Z2_ANDROID_SHELL "/system/bin/sh"
#endif

int main(int argc, char **argv) {
    const char *name = strrchr(argv[0], '/');
    name = name ? name + 1 : argv[0];
    const char *dir = getenv("Z2_ANDROID_SCRIPTS");
    if (!dir || dir[0] != '/' || strncmp(name, "z2", 2) != 0 ||
        strspn(name, "abcdefghijklmnopqrstuvwxyz0123456789-") != strlen(name)) {
        fprintf(stderr, "z2android: start commands from a z2term Android shell\n");
        return 126;
    }
    size_t size = strlen(dir) + strlen(name) + 2;
    char *script = malloc(size);
    char **args = calloc((size_t)argc + 2, sizeof(char *));
    if (!script || !args) {
        fprintf(stderr, "z2android: out of memory\n");
        free(script);
        free(args);
        return 126;
    }
    snprintf(script, size, "%s/%s", dir, name);
    args[0] = "sh";
    args[1] = script;
    for (int i = 1; i < argc; ++i) args[i + 1] = argv[i];
    execv(Z2_ANDROID_SHELL, args);
    int error = errno;
    perror("z2android: cannot start system shell");
    free(script);
    free(args);
    return error == ENOENT ? 127 : 126;
}
