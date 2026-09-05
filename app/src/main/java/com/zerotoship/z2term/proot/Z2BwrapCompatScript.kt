package com.zerotoship.z2term.proot

/** GUI子プロセスだけに先頭追加する互換コマンド置き場。通常の端末PATHには入れない。 */
internal const val Z2TERM_GUI_COMPAT_DIR = "/usr/local/lib/z2term-gui-compat"

/**
 * glycinが画像デコーダを起動するときに使うbubblewrapのAndroid互換入口。
 *
 * Androidのアプリsandbox内ではuser/mount namespaceをもう一段作れないため、通常の
 * bubblewrapはloaderを起動する前に終了する。GTKはアイコン1枚を読めないだけでもabort
 * するので、結果はRemmina/gThumb等の個別障害ではなく、新しいGTKアプリ全体の起動障害になる。
 *
 * このwrapperは実行対象が`glycin-*` loaderの場合だけ、bind/namespace/seccomp指示を
 * 読み飛ばし、glycinが指定した環境変数とcwdを再現してloaderを直接実行する。それ以外の
 * bubblewrap用途は`/usr/bin/bwrap`へそのまま渡すため、一般コマンドの意味は変えない。
 * glycin独自の内側sandboxは省略されるが、外側のAndroidアプリUID/SELinux sandboxは残る。
 */
internal fun z2BwrapCompatScript(): String = """
    |#!/bin/sh
    |# z2term managed: glycin bubblewrap compatibility inside the Android app sandbox
    |REAL_BWRAP=/usr/bin/bwrap
    |
    |# option数を知っているものだけ読み、実際にexecされるコマンドを特定する。
    |# bind元などにglycinという名前が含まれるだけでは互換経路へ入れない。
    |command_path=${'$'}(
    |  while [ ${'$'}# -gt 0 ]; do
    |    case "${'$'}1" in
    |      --) shift; [ ${'$'}# -gt 0 ] && printf '%s\n' "${'$'}1"; break ;;
    |      --setenv|--ro-bind|--ro-bind-try|--bind|--bind-try|--dev-bind|--dev-bind-try|--symlink|--file|--bind-data|--ro-bind-data|--chmod)
    |        [ ${'$'}# -ge 3 ] || exit 2; shift 3 ;;
    |      --unsetenv|--chdir|--dev|--proc|--tmpfs|--mqueue|--dir|--remount-ro|--perms|--size|--uid|--gid|--hostname|--lock-file|--userns|--userns2|--pidns|--sync-fd|--seccomp|--add-seccomp-fd)
    |        [ ${'$'}# -ge 2 ] || exit 2; shift 2 ;;
    |      --unshare-all|--unshare-user|--unshare-user-try|--unshare-ipc|--unshare-pid|--unshare-net|--unshare-uts|--unshare-cgroup|--unshare-cgroup-try|--share-net|--die-with-parent|--as-pid-1|--new-session|--clearenv|--disable-userns|--assert-userns-disabled)
    |        shift ;;
    |      -*) exit 2 ;;
    |      *) printf '%s\n' "${'$'}1"; break ;;
    |    esac
    |  done
    |)
    |case "${'$'}command_path" in
    |  */glycin-*|glycin-*) is_glycin=1 ;;
    |  *) is_glycin=0 ;;
    |esac
    |
    |# GUI経路以外、またはglycin以外の利用者には本物のbubblewrapを使わせる。
    |if [ "${'$'}{Z2ROOT_ENGINE:-}" != 1 ] || [ "${'$'}is_glycin" != 1 ]; then
    |  exec "${'$'}REAL_BWRAP" "${'$'}@"
    |fi
    |
    |workdir=
    |while [ ${'$'}# -gt 0 ]; do
    |  case "${'$'}1" in
    |    --) shift; break ;;
    |    --setenv)
    |      [ ${'$'}# -ge 3 ] || exit 2
    |      export "${'$'}2=${'$'}3"
    |      shift 3
    |      ;;
    |    --unsetenv)
    |      [ ${'$'}# -ge 2 ] || exit 2
    |      unset "${'$'}2"
    |      shift 2
    |      ;;
    |    --chdir)
    |      [ ${'$'}# -ge 2 ] || exit 2
    |      workdir=${'$'}2
    |      shift 2
    |      ;;
    |    --ro-bind|--ro-bind-try|--bind|--bind-try|--dev-bind|--dev-bind-try|--symlink|--file|--bind-data|--ro-bind-data|--chmod)
    |      [ ${'$'}# -ge 3 ] || exit 2
    |      shift 3
    |      ;;
    |    --dev|--proc|--tmpfs|--mqueue|--dir|--remount-ro|--perms|--size|--uid|--gid|--hostname|--lock-file|--userns|--userns2|--pidns|--sync-fd|--seccomp|--add-seccomp-fd)
    |      [ ${'$'}# -ge 2 ] || exit 2
    |      shift 2
    |      ;;
    |    --unshare-all|--unshare-user|--unshare-user-try|--unshare-ipc|--unshare-pid|--unshare-net|--unshare-uts|--unshare-cgroup|--unshare-cgroup-try|--share-net|--die-with-parent|--as-pid-1|--new-session|--clearenv|--disable-userns|--assert-userns-disabled)
    |      # clearenvでAndroid/z2rootの外側sandbox情報まで失うとloader自体が動けない。
    |      # glycinが後続の--setenvで指定する値は上で反映する。
    |      shift
    |      ;;
    |    -*)
    |      echo "z2term: unsupported glycin bwrap option: ${'$'}1" >&2
    |      exit 2
    |      ;;
    |    *) break ;;
    |  esac
    |done
    |
    |[ ${'$'}# -gt 0 ] || exit 2
    |[ -z "${'$'}workdir" ] || cd "${'$'}workdir" || exit 2
    |exec "${'$'}@"
""".trimMargin() + "\n"
