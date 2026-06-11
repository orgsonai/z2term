#!/bin/sh
# z2root コマンド群テスト（ゲスト内で直接実行）
#
# 狙い: z2root エンジンの難所（ptrace/seccomp・fakeroot 偽装・パス変換・/proc 偽装・
# pty(/dev/pts)・大量 fork/exec・ld.so reloc）を踏む *壊れやすい特殊コマンド* に絞って、
# 今後それらがエラーなく動くかを確認する回帰スモーク。
#
# 重要な観点:
#   - `claude --version` は通るのに claude 本体が立ち上がらない、git の重い操作が
#     コケる、等「軽い subset は OK でも実体はダメ」を捕まえる。だから --version 等の
#     軽量パスではなく *実起動・実操作* を踏む。
#   - cd/ls/cp/echo のような自明な coreutils は信号が薄いので原則入れない。
#
# 方針:
#   - 出力は全部そのままログへ（画面 + /tmp/z2root-cmdtest-<時刻>.log）。
#   - 未インストールはエラー扱いせず skip（"入ってない" と "壊れた" を区別）。
#   - 健全なら exit 0 になるよう各コマンドを組み、末尾に *非ゼロ終了一覧* を出す。
#
# 使い方:
#   sh scripts/z2root-cmdtest.sh              # 標準（ネット/ビルド込み）
#   SKIP_NET=1   sh scripts/z2root-cmdtest.sh  # ネット/パッケージ系を飛ばす
#   SKIP_BUILD=1 sh scripts/z2root-cmdtest.sh  # cc コンパイル等を飛ばす
#   RUN_SSHD=1    sh scripts/z2root-cmdtest.sh # dropbear ループバック ssh を実行
#       ※ z2root 単独だと SSH reset を踏んでセッションが落ちる可能性あり（既知）。
#         ログはファイルに残る。落ちたら proot タブから z2root の dropbear へ ssh して再現。
#   RUN_PRIV=1    sh scripts/z2root-cmdtest.sh # 真に root が要る操作(losetup/mount/デバイス)も実行
#       ※ 非 root の z2root では EPERM が *正常*。実 chroot(root)エンジンでの確認用。
#
# proot タブで同じものを流せば対照ログが取れる。

LOG="${LOG:-/tmp/z2root-cmdtest-$(date +%Y%m%d-%H%M%S).log}"

# 画面とログの両方へ（busybox ash でも動くよう tee で再 exec する）
if [ -z "${_Z2_TEE:-}" ]; then
	export _Z2_TEE=1 LOG
	exec sh "$0" "$@" 2>&1 | tee "$LOG"
fi

FAILS="$(mktemp /tmp/z2cmdtest-fails.XXXXXX 2>/dev/null || echo /tmp/z2cmdtest-fails.$$)"
: >"$FAILS"
NSKIP=0

banner() {
	printf '\n======================================================================\n'
	printf '## %s\n' "$1"
	printf '======================================================================\n'
}
have() { command -v "$1" >/dev/null 2>&1; }
skip() {
	printf '\n(skip: %s)\n' "$1"
	NSKIP=$((NSKIP + 1))
}
run() {
	printf '\n$ %s\n' "$*"
	"$@"
	rc=$?
	printf '[exit %s]\n' "$rc"
	[ "$rc" -ne 0 ] && printf '%s\n' "$*" >>"$FAILS"
	return 0
}
runc() {
	printf '\n$ %s\n' "$1"
	sh -c "$1"
	rc=$?
	printf '[exit %s]\n' "$rc"
	# 複数行コマンド（heredoc 等）は先頭 1 行だけを一覧に記録
	[ "$rc" -ne 0 ] && printf '%s\n' "$1" | head -1 >>"$FAILS"
	return 0
}
opt() {
	c="$1"
	shift
	if have "$c"; then run "$@"; else skip "$c 未インストール"; fi
}
optc() {
	c="$1"
	shift
	if have "$c"; then runc "$1"; else skip "$c 未インストール"; fi
}

banner "環境情報"
opt z2version z2version --short
run uname -a
optc cat 'cat /etc/os-release 2>/dev/null | head -3'
run id

WORK="$(mktemp -d /tmp/z2cmdtest.XXXXXX 2>/dev/null || echo /tmp/z2cmdtest.$$)"
mkdir -p "$WORK"
cd "$WORK" || exit 1
PKG=none
for p in apt-get apk dnf pacman; do have "$p" && { PKG="$p"; break; }; done

# ---------------------------------------------------------------------------
banner "1. ランタイム本体の *実起動*（--version では露見しない経路）"
# ---------------------------------------------------------------------------
# 核心: 軽量パスと実起動を対比する。実起動はサブプロセス spawn・/proc 参照・
# 設定/認証ファイル読み・(claude は)内蔵 ripgrep 等を踏むので壊れ方が違う。
optc claude 'claude --version | head -1'   # 軽量 subset（通る想定）
if have claude; then
	# headless 実起動: ランタイムを本当にブートする。認証/ネット無しだと認証エラーで
	# 非ゼロになり得るが、それは z2root クラッシュ(segv/loader 失敗/ハング)とログ上で区別可能。
	runc 'timeout 40 claude -p "reply with exactly: z2ok" 2>&1 | head -20; echo "[claude -p exit=$?]"'
else
	skip "claude 未インストール"
fi
# node: 子プロセス spawn + fs（claude/各種 CLI の土台）
optc node 'node -e "const{execSync}=require(\"child_process\");console.log(\"node-spawn:\",execSync(\"echo hi\").toString().trim());const fs=require(\"fs\");fs.writeFileSync(\"n.tmp\",\"x\");console.log(\"node-fs-ok\")"'
# python: fork/exec・multiprocessing・ssl(C 拡張ロード)・cwd 取得をまとめて踏む
optc python3 'python3 - <<PY
import subprocess, os, sys
bad = 0
print("py-spawn:", subprocess.check_output(["echo","hi"]).decode().strip())
print("py-cwd:", os.getcwd())
try:
    import ssl; print("py-ssl:", ssl.OPENSSL_VERSION.split()[0])
except Exception as e:
    print("py-ssl-FAIL:", e); bad = 1
try:
    import multiprocessing as mp
    with mp.Pool(2) as p:
        print("py-mp:", sum(p.map(abs, [-1,-2,-3])))
except Exception as e:
    print("py-mp-FAIL:", e); bad = 1
sys.exit(bad)
PY'
# ripgrep: claude が内部で多用。mmap/並列探索で z2root を踏む
optc rg 'rg --version | head -1; rg -n claude "'"$WORK"'" 2>/dev/null | head -1; echo "[rg exit=$?]"'

# ---------------------------------------------------------------------------
banner "2. VCS の重い操作（hardlink / pack / rename / mmap）"
# ---------------------------------------------------------------------------
# git --version では出ない。clone→pack→gc→checkout で hardlink/rename を踏む。
run git --version
runc 'git init -q r && cd r && git -c user.email=a@b -c user.name=t commit -q --allow-empty -m c0 && for i in 1 2 3; do echo $i>f$i; git add f$i; git -c user.email=a@b -c user.name=t commit -q -m c$i; done && git gc -q && git log --oneline | wc -l && git checkout -q HEAD~1 && git status -s; echo "[git heavy exit=$?]"'
if [ "${SKIP_NET:-0}" != "1" ]; then
	# clone は hardlink を多用（既知の z2root 難所）。clone 後にビルド/参照まで。
	runc 'git clone --depth 1 https://github.com/octocat/Hello-World.git "'"$WORK"'/HW" 2>&1 | tail -2 && git -C "'"$WORK"'/HW" log --oneline | head -1; echo "[git clone exit=$?]"'
fi

# ---------------------------------------------------------------------------
banner "3. パッケージ管理（fakeroot / 大量 fork-exec / dpkg /proc / symlink）"
# ---------------------------------------------------------------------------
if [ "${SKIP_NET:-0}" = "1" ]; then
	skip "SKIP_NET=1 (パッケージ系)"
else
	case "$PKG" in
		apt-get) run apt-get update; run apt-get install -y hello; opt hello hello; optc dpkg 'dpkg -L hello | head -3' ;;
		apk)     run apk update; run apk add --no-cache figlet; optc figlet 'figlet z2 | head -4' ;;
		dnf)     run dnf -y install hello; opt hello hello ;;
		pacman)  run pacman -Sy --noconfirm hello; opt hello hello ;;
		none)    skip "apt/apk/dnf/pacman 未検出" ;;
	esac
	# 言語系パッケージ: コンパイル/symlink/node を踏む
	# python venv 作成（symlink/exec/コピーを大量に踏む）→ その pip で install
	optc python3 'python3 -m venv "'"$WORK"'/venv" 2>&1 | tail -2 && "'"$WORK"'/venv/bin/python" -c "print(\"venv-ok\")" && "'"$WORK"'/venv/bin/pip" install --quiet wheel 2>&1 | tail -1 && "'"$WORK"'/venv/bin/python" -c "import wheel;print(\"venv-pip-ok\")"; echo "[venv exit=$?]"'
	optc pip3 'pip3 install --quiet --no-input --user wheel 2>&1 | tail -2; python3 -c "import wheel;print(\"pip-wheel-ok\")"'
	optc npm 'npm install -g --silent left-pad 2>&1 | tail -2; node -e "console.log(require(\"left-pad\")(\"x\",3))"'
fi

# ---------------------------------------------------------------------------
banner "4. PTY / 端末制御（/dev/pts を踏む: SSH reset と同根の経路）"
# ---------------------------------------------------------------------------
runc 'ls -ld /dev/pts; ls -l /dev/ptmx 2>/dev/null'
optc stty 'stty -a 2>&1 | head -2 || echo "(no tty)"'
# script は pty を確保して chmod/ioctl を踏む（dropbear と同系統）
optc script 'script -qc "echo pty-ok" /dev/null 2>&1 | head -2; echo "[script exit=$?]"'
optc tmux 'tmux -f /dev/null new -d -s z2t "sleep 2" 2>&1 && tmux ls 2>&1 && tmux kill-server 2>&1; echo "[tmux exit=$?]"'
if [ "${RUN_SSHD:-0}" = "1" ]; then
	printf '\n(RUN_SSHD=1: dropbear ループバック ssh を実行。z2root 単独だとセッションが落ちる可能性)\n'
	optc dropbear 'dropbear -R -p 2222 -F & sleep 1; ssh -p 2222 -o StrictHostKeyChecking=no -o BatchMode=yes localhost true 2>&1 | head -5; echo "[ssh-loopback exit=$?]"'
else
	skip "dropbear ループバック ssh（RUN_SSHD=1 で実行）"
fi

# ---------------------------------------------------------------------------
banner "5. /proc・fakeroot 偽装の境界"
# ---------------------------------------------------------------------------
run id
run whoami
run readlink /proc/self/cwd       # claude 起動不可だった真因の逆変換
run readlink /proc/self/exe
runc 'grep -E "^(Uid|Gid):" /proc/self/status'
runc 'cat /proc/self/loginuid 2>/dev/null; echo'
runc 'head -3 /proc/self/maps'
optc ps 'ps aux 2>/dev/null | head -3 || ps | head -3'
optc top 'top -bn1 2>&1 | head -5'

# ---------------------------------------------------------------------------
banner "6. ビルド（execve chain: cc→as→ld + ld.so reloc）"
# ---------------------------------------------------------------------------
if [ "${SKIP_BUILD:-0}" = "1" ]; then
	skip "SKIP_BUILD=1"
elif have cc || have gcc; then
	CC=cc; have cc || CC=gcc
	runc 'printf "#include <stdio.h>\nint main(){puts(\"cc-built-ok\");return 0;}\n" > h.c && '"$CC"' -O2 -o h h.c && ./h; echo "[build exit=$?]"'
else
	skip "cc/gcc 未インストール"
fi

# ---------------------------------------------------------------------------
banner "7. パス変換 / symlink canonicalize（z2root 固有）"
# ---------------------------------------------------------------------------
# symlink を含むディレクトリで cwd 絶対化（/proc/<tid>/cwd 由来の変換）
runc 'mkdir -p real/sub && ln -s real slink && cd "'"$WORK"'/slink/sub" && pwd && pwd -P; echo "[cwd-canon exit=$?]"'
# 相対 shebang スクリプトの実行（#! 解決 + PATH 探索）
runc 'printf "#!/bin/sh\necho shebang-ok cwd=\$(pwd)\n" > s.sh && chmod +x s.sh && PATH="$PWD:$PATH" s.sh; echo "[shebang exit=$?]"'
# tar の symlink/hardlink 保持 create→extract（既知の tar 症状経路）
optc tar 'ln test-hard 2>/dev/null; tar czf a.tgz real slink s.sh && mkdir -p ex && tar xzf a.tgz -C ex && find ex -type l -o -type f | sort | head; echo "[tar exit=$?]"'

# ---------------------------------------------------------------------------
banner "8. ディスク / FS 作成 / ブロックデバイス（dd・mkfs・パーティション）"
# ---------------------------------------------------------------------------
# 非 root でも踏める "ファイルイメージ相手" の形を標準で叩く（seek/ioctl/大 IO/loop）。
# デバイスや mount/losetup など真に root が要るものは RUN_PRIV=1 のときだけ。
run dd if=/dev/zero of=disk.img bs=1M count=16
run dd if=/dev/urandom of=rand.bin bs=1k count=8
optc truncate 'truncate -s 64M sparse.img && ls -l sparse.img'
optc fallocate 'fallocate -l 8M fa.img 2>&1 && ls -l fa.img; echo "[fallocate exit=$?]"'
# ファイルを FS 化（mke2fs/mkfs.vfat はファイル相手なら非 root で動く）
if have mkfs.ext4; then runc 'mkfs.ext4 -q -F disk.img && file disk.img 2>/dev/null; echo "[mkfs.ext4 exit=$?]"'; else skip "mkfs.ext4 未インストール"; fi
optc mkfs.vfat 'mkfs.vfat fa.img >/dev/null 2>&1; file fa.img 2>/dev/null; echo "[mkfs.vfat exit=$?]"'
# パーティションテーブル（ファイル相手なら非 root で可）
optc parted 'parted -s sparse.img mklabel gpt mkpart primary 1MiB 32MiB && parted -s sparse.img print; echo "[parted exit=$?]"'
optc fdisk 'fdisk -l disk.img 2>&1 | head -5; echo "[fdisk -l exit=$?]"'
optc blkid 'blkid disk.img 2>&1; echo "[blkid exit=$?]"'
optc lsblk 'lsblk 2>&1 | head -3; echo "[lsblk exit=$?]"'
if [ "${RUN_PRIV:-0}" = "1" ]; then
	printf '\n(RUN_PRIV=1: root 必須の操作も実行。非 root では EPERM が正常)\n'
	optc losetup 'L=$(losetup -f --show disk.img 2>&1) && echo "loop=$L" && losetup -d "$L"; echo "[losetup exit=$?]"'
	runc 'mkdir -p mnt && mount -o loop disk.img mnt 2>&1 && ls mnt && umount mnt 2>&1; echo "[mount exit=$?]"'
else
	skip "losetup/mount などデバイス系（RUN_PRIV=1 で実行・root/実 chroot 用）"
fi

# ---------------------------------------------------------------------------
banner "9. IPC / 特殊 syscall（z2root の変換漏れ・補足漏れが出やすい難所）"
# ---------------------------------------------------------------------------
# AF_UNIX ソケットのパス変換（sockaddr_un.sun_path は変換漏れの定番）
optc python3 'python3 - <<PY
import socket, os
p = os.path.join(os.getcwd(), "s.sock")
try: os.unlink(p)
except FileNotFoundError: pass
srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM); srv.bind(p); srv.listen(1)
cli = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM); cli.connect(p)
conn,_ = srv.accept(); cli.sendall(b"unix-sock-ok"); print(conn.recv(32).decode())
print("sun_path-exists:", os.path.exists(p))
PY'
# FIFO（named pipe）読み書き
optc mkfifo 'rm -f p.fifo; mkfifo p.fifo && { echo fifo-ok > p.fifo & } && cat p.fifo; echo "[fifo exit=$?]"'
# flock（fcntl ロック）
optc flock 'flock f.lock -c "echo flock-ok"; echo "[flock exit=$?]"'
# inotify（watch descriptor のパス変換）
optc inotifywait 'inotifywait -qq -t 3 -e create . & w=$!; sleep 0.4; touch trig.x; wait $w; echo "[inotify exit=$?]"'
# 拡張属性 xattr（パス＋fakeroot）
if have setfattr && have getfattr; then runc 'echo x>xa.txt && setfattr -n user.z2 -v ok xa.txt 2>&1 && getfattr -d xa.txt 2>&1 | head -3; echo "[xattr exit=$?]"'; else skip "setfattr/getfattr 未インストール"; fi
# copy_file_range / sendfile 経由の cp（最近の coreutils が使う高速コピー syscall）
runc 'dd if=/dev/zero of=cfr.bin bs=1M count=8 2>/dev/null && cp cfr.bin cfr2.bin && cmp cfr.bin cfr2.bin && echo cp-cfr-ok; echo "[cp-cfr exit=$?]"'
# ネストした ptrace（z2root 自身が ptracer ＝二重 ptrace）
optc strace 'strace -f -e trace=execve echo nested-ptrace-ok 2>&1 | tail -3; echo "[strace exit=$?]"'
optc gdb 'gdb -q --batch -ex run --args /bin/echo gdb-run-ok 2>&1 | tail -3; echo "[gdb exit=$?]"'
# Go バイナリ（libc を介さない生 syscall ＝seccomp 設計を踏む）
optc go 'printf "package main\nimport \"fmt\"\nfunc main(){fmt.Println(\"go-ok\")}\n" > g.go && go run g.go 2>&1 | tail -3; echo "[go exit=$?]"'
# sqlite3（fcntl バイトレンジロック＋mmap＋fsync）
optc sqlite3 'sqlite3 t.db "create table x(a);insert into x values(1),(2);select count(*) from x;" 2>&1; echo "[sqlite exit=$?]"'
# rsync（fakeroot 所有権偽装＋大量 syscall）
optc rsync 'mkdir -p rs/src && echo r>rs/src/f && rsync -a rs/src/ rs/dst/ 2>&1 && cat rs/dst/f; echo "[rsync exit=$?]"'

# ---------------------------------------------------------------------------
banner "10. 名前解決 / TLS（NSS dlopen・getaddrinfo・証明書検証）"
# ---------------------------------------------------------------------------
if [ "${SKIP_NET:-0}" = "1" ]; then
	skip "SKIP_NET=1 (名前解決/TLS)"
else
	optc getent 'getent hosts example.com 2>&1 | head -1; echo "[getent exit=$?]"'
	optc curl 'curl -sS -o /dev/null -w "http=%{http_code} tls=%{ssl_verify_result}\n" https://example.com 2>&1; echo "[curl-tls exit=$?]"'
	optc nslookup 'nslookup example.com 2>&1 | tail -4; echo "[nslookup exit=$?]"'
fi

# ---------------------------------------------------------------------------
banner "結果サマリ（非ゼロ終了したコマンド一覧）"
# ---------------------------------------------------------------------------
NFAIL=$(wc -l <"$FAILS" 2>/dev/null | tr -d ' ')
printf 'skip(未インストール等): %s 件\n' "$NSKIP"
if [ "${NFAIL:-0}" -eq 0 ]; then
	printf '非ゼロ終了: 0 件 — 全コマンド exit 0\n'
else
	printf '非ゼロ終了: %s 件（z2root の退行 / または期待非ゼロを含む。上の本文で要確認）:\n' "$NFAIL"
	sed 's/^/  - /' "$FAILS"
	printf '注: claude -p の認証/ネット不通・timeout(124)・パッケージ既導入などは健全でも非ゼロになり得る。\n'
	printf '    本文ログで segv / loader 失敗 / ハング(timeout) と区別すること。\n'
fi
printf 'ログ: %s\n' "$LOG"

cd / && rm -rf "$WORK" 2>/dev/null
rm -f "$FAILS" 2>/dev/null
