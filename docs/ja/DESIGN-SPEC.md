# Z2Term 設計書 兼 仕様書

最終更新: 2026-06-14 / 対象バージョン: 0.8.94-alpha (versionCode 102)

> 本書は Z2Term の **詳細設計 + 仕様** をまとめた技術文書。実装担当・レビュー担当向け。
> 利用者向けのやさしい説明は `docs/ja/HANDBOOK.md` を参照。
> セッション間の進捗引き継ぎは `docs/M12-HANDOFF.md`（最新）および各 `M*-HANDOFF.md` を参照。
> English version: `docs/en/DESIGN-SPEC.md`.

---

## 目次

1. [概要](#1-概要)
2. [技術スタック](#2-技術スタック)
3. [全体アーキテクチャ](#3-全体アーキテクチャ)
4. [レイヤ別 詳細設計](#4-レイヤ別-詳細設計)
5. [主要データフロー](#5-主要データフロー)
6. [機能仕様](#6-機能仕様)
7. [設定項目](#7-設定項目)
8. [パーミッション](#8-パーミッション)
9. [ビルド / 同梱物](#9-ビルド--同梱物)
10. [既知の制約と設計上の罠](#10-既知の制約と設計上の罠)
11. [用語集](#11-用語集)

---

## 1. 概要

**Z2Term** は Android 単体で動く独自実装のターミナルエミュレータ + Linux 実行環境。

- **root 不要**: `forkpty(3)` + **PRoot** (ユーザー空間の chroot/bind エミュレーション) で、
  通常権限のアプリ内に Linux ディストロ (Alpine / Ubuntu / Arch / Kali) を展開して動かす。
- **自前のターミナルエミュレータ**: xterm 互換の VT/ANSI 解釈を Kotlin で実装。
- **自前の UI / キーボード**: Jetpack Compose。独自フリックキーボード (英字 + 日本語/カタカナ) と OS IME を切替可能。
- **SSH 両方向**: 端末から外部へ (JSch クライアント)、PC から端末へ (dropbear サーバ)。
- **ファイル連携**: SAF DocumentsProvider で他アプリから rootfs/ホームを R/W、proot 内から Android 共有ストレージへ `cd`。
- **GUI デスクトップ**: distro 内で Xvnc + 軽量 WM/アプリを起動し、内蔵 RFB(VNC) クライアントで表示（`gui/` パッケージ）。動画はソフト描画、音声はオプトインで PulseAudio→TCP→AudioTrack ブリッジ（`AudioBridge`）。
- **実行エンジン**: 既定は PRoot。裏設定（設定→アプリ情報のバージョン行を 7 タップ）で **非 root の自前 ptrace エンジン「z2root」**に切替可。さらに root 端末では root セルフテスト成功時に **「実 chroot」エンジン**も選択可（`su` 経由 bind mount + `chroot`。`executionEngine`）。トグル発火後 3 秒はバージョン行を**タップ不可**にして連打による即時再トグルを防ぐ（0.8.70。従来はタップを受けるが無視で不自然だった）。**foss は proot prebuilt を同梱せず常に z2root 実走**のため、エンジン選択肢に PRoot チップを出さない（z2root / root 解放時 chroot のみ。0.8.93。従来は選べても z2root に倒れる見せかけだった）。

対応 ABI は **arm64-v8a のみ**。最低 Android 10 (API 29)、ターゲット API 35。

### 配布フレーバー

| フレーバー | applicationId | 用途 |
|---|---|---|
| `full` | `com.zerotoship.z2term` | 通常配布 (rootfs/proot 同梱・初回オフライン起動可) |
| `foss` | `com.zerotoship.z2term.foss` | F-Droid 適合。third-party prebuilt (proot/talloc) と Alpine rootfs を APK から外し、実行エンジンは同梱ソースからビルドする z2root、rootfs は起動時 DL (初回オフライン起動不可) |

`debug` ビルドは更に `.debug` サフィックスが付く。

---

## 2. 技術スタック

| 分類 | 採用 | 版/補足 |
|---|---|---|
| 言語 | Kotlin | 2.2.10 |
| ビルド | AGP | 9.1.1 (※ `kotlin-android` プラグイン併用不可) |
| UI | Jetpack Compose | BOM 2025.01.00 + Material3 |
| ネイティブ | C++ (forkpty JNI) | NDK 28、CMake 3.22.1、`c++_shared`、android-29 |
| 永続化 | DataStore Preferences | 1.1.2 (設定 / SSH プロファイル) |
| SSH クライアント | JSch (mwiede fork) | 0.2.26 (+ BouncyCastle 1.84 で ed25519/curve25519 を有効化) |
| 解凍 | org.tukaani:xz | 1.10 (DL distro の `.tar.xz`)。gzip は JDK 標準 |
| Linux 実行 | PRoot + libtalloc | jniLibs に `.so` 同梱 (Termux ビルド由来) |
| 同梱 OS | Alpine Linux ARM minirootfs | full は `src/full/assets` に `.tgz` 同梱。foss は非同梱で公式 CDN から起動時 DL |

---

## 3. 全体アーキテクチャ

```
┌───────────────────────────── UI 層 (Compose) ─────────────────────────────┐
│ MainActivity → TerminalScreen                                              │
│  ├ TopBar (📋/📜/💡/🔒常駐/🔍/⌨/⚙ 並べ替え可) ├ TabBar ├ Renderer(Canvas)   │
│  ├ TerminalInputView(AndroidView: ジェスチャ/IME/選択) ├ ScrollIndicators │
│  ├ TerminalKeyboard(独自) / JapaneseFlickKeyboard / SpecialKeyBar          │
│  └ SettingsSheet / SshProfilesSheet / SnippetsSheet / HostKeyDialog        │
└───────────────────────────────────────────────────────────────────────────┘
                 │ writeBytes(入力)              ▲ emulator buffer(描画)
                 ▼                               │
┌──────────────────────────── ドメイン層 ───────────────────────────────────┐
│ SessionManager ─持つ→ TerminalSession[*]                                   │
│   TerminalSession: 状態機械 / readLoop / resize / 選択 / cwd / label       │
│     ├ emulator: TerminalEmulator (VT 解釈, 専用 1 スレッド)                │
│     └ channel: ProcessChannel = LocalPtyChannel | SshChannel              │
└───────────────────────────────────────────────────────────────────────────┘
                 │                                       │
                 ▼ (ローカル)                            ▼ (リモート)
┌──────── 実行基盤 ────────┐                  ┌──────── SSH ────────┐
│ ProotLauncher            │                  │ SshChannel (JSch)    │
│  → PtyProcess (forkpty)  │                  │  shell + -L 転送     │
│    → proot → distro shell│                  └──────────────────────┘
└──────────────────────────┘
        │ 展開/更新
        ▼
┌──────── distro / 永続 ────────┐   ┌─ Service ─┐   ┌─ SAF ─┐   ┌─ 設定 ─┐
│ DistroBundle/Spec/Installer/  │   │ Terminal  │   │ Docs  │   │ AppSet │
│ Downloader (assets / DL)      │   │ Service   │   │Provider│  │ tings  │
└───────────────────────────────┘   └───────────┘   └────────┘  └────────┘
```

**ライフサイクル設計の要点**:
- `TerminalSession` は **UI から独立**して生存 (`SessionManager` が保持)。Activity 破棄でも PTY/emulator 状態を維持。
- `TerminalService` (フォアグラウンドサービス) が常駐化を担い、バックグラウンドでも PTY を維持する。`AudioBridge`(GUI 音声) も同サービス系で扱う。
- emulator の状態更新は **専用シングルスレッド** (`z2term-emu-*`) に集約し、Compose は `StateFlow` 経由で読む。
- **GUI デスクトップ**は別 Activity (`GuiActivity`) として起動し、distro 内 Xvnc に内蔵 RFB クライアントで接続する（[§4.12](#412-gui-デスクトップ-gui)）。実行エンジンは PRoot 既定、裏設定で z2root（非 root）、root 端末ではさらに chroot に切替可（[§4.3](#43-proot-実行-prootprootlauncherkt-prootsshdscriptkt)）。

---

## 4. レイヤ別 詳細設計

### 4.1 ネイティブ (`cpp/pty_jni.cpp`, `libz2term`)

- `forkpty(3)` で擬似端末 (PTY) を作り、子プロセスで `execve()`。Bionic libc に API 21+ で存在。
- JNI 公開: `nativeCreate(command, args, env, cwd, rows, cols) → (fd<<32 | pid)`、`nativeResize(fd, rows, cols)` (`TIOCSWINSZ`)、シグナル送出、`waitpid`。
- 子では `setsid` / `TIOCSCTTY` で制御端末を確立。

### 4.2 PTY ラッパー (`pty/PtyProcess.kt`)

- `nativeCreate` の戻り値から `FileDescriptor` を生成し、`reader`(FileInputStream)/`writer`(FileOutputStream) を公開。
- `resize(rows,cols)` / `sendSignal` / `close` / `waitFor`。
- **JNI シンボル注意**: `@JvmStatic external` を companion に置くと外側クラス名で export される (CMake/JNI 命名規約)。

### 4.3 PRoot 実行 (`proot/ProotLauncher.kt`, `proot/SshdScript.kt`)

- バイナリは `nativeLibraryDir/libproot.so` (+ `libproot_loader.so`)。`libtalloc.so` を SONAME 通り `libtalloc.so.2` に展開し `LD_LIBRARY_PATH` に通す。
- `launch(distroId, command, rows, cols, fallbackShell)` が proot 引数を組み立てて `PtyProcess.create`:
  - `--kill-on-exit -0 --link2symlink -r <rootfs> -b /dev -b /proc -b /sys -b <shared_home>:/root`
  - **外部ストレージ bind**: `/storage/emulated/0:/sdcard`、`getExternalFilesDir:/storage/app`
  - `-w /root`、env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 PATH=… TMPDIR=/tmp` + 履歴系 env。
- **共有ホーム**: `filesDir/shared_home` を全 distro 共通で `/root` にバインド (← 端末の `~` の実体)。
- **HOME のディストリ別隔離 (0.8.72, `.claude/downloads` を 0.8.73 で追加, z2root の最長一致 bind を 0.8.75 で修正)**: `/root` 全体は共有のままにしつつ、**arch 依存物が入る一部サブディレクトリだけをディストリ別オーバーレイで上書き bind** する (`isolatedHomeSubdirs` = `.local .cache .npm .npm-global .nvm .cargo .rustup .config .claude/downloads`)。`filesDir/home_overlay/<distroId>/<sub>` を `/root/<sub>` に重ね bind し、`shared_home/<sub>` はマウントポイントとして用意する (ネストパス `.claude/downloads` も `mkdir -p` で親ごと作成)。proot は `-b <shared_home>:/root` の後に各サブディレクトリ bind を重ね、chroot も `mount -o bind <SHOME> $RFS/root` の後に同様に重ねる (掃除時は `root` より先に lazy umount)。**狙い**: musl(Alpine)↔glibc(Arch/Ubuntu/Kali) で HOME 内の native (npm global の node/claude・**Claude Code 本体 `~/.claude/downloads/claude`**・`~/.cache` のコンパイル済みアドオン・nvm の node 本体等) が混ざって壊れる問題を、ディストリ別に分けて根治する。**項目4 の真因**: 旧版は `.claude/downloads` が共有だったため、Alpine(musl) と Arch(glibc) が同じ native 本体を上書き合い `Not a valid dynamic program` で双方起動不可になっていた。0.8.73 でオーバーレイ bind を足したが、**z2root エンジンでは隔離が効かず再発**した (2026-06-11 実機検証)。真因は z2root のパス変換 (`z2root.c` の `translate_abs`/`host_to_guest`) が **bind を登録順の最初一致で解決**しており、先に登録される親 bind `/root` が子 bind `/root/.claude/downloads` を覆い隠していたこと。proot は最長一致なので効いていた engine 差。**0.8.75 で両変換関数を最長一致 (最も具体的=guest_len 最長の bind 優先) に修正**し、z2root でも `.claude/downloads` だけがオーバーレイへ、`.claude/.credentials.json` 等は共有 HOME へ正しく解決されるようにした。`.claude` 直下の認証 (`.credentials.json`)・設定・projects、書類・git リポジトリ等の通常ファイルは `/root` 直下のまま共有される。**移行注意**: 既存 `shared_home/<sub>` の中身はオーバーレイに覆われて各ディストリからは見えなくなる (消えてはおらず影に入るだけ)。各ディストリで一度 `claude` を入れ直すと native 本体が各オーバーレイに収まる。
- `resolveShell`: 指定シェルが rootfs に無ければ `defaultShell → /bin/sh` にフォールバック (usrmerge 考慮)。
- `isDistroReady`: `bin/busybox|bin/bash` 等の実体 + `.z2term-version` マーカー (同梱 distro のみ `ROOTFS_VERSION` 比較)。
- 起動毎に冪等で注入: `ensureShellHistoryConfig` (履歴 rc)、`ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear ラッパー)、`ensureOsc7CwdConfig` (cwd 復元用 OSC7 フック)、`ensureZ2ApiScripts` (`z2-*` ブリッジ)、`ensureZ2AdbScript` (`/usr/local/bin/z2adb`)、`ensureZ2HelpScript` (`/usr/local/bin/z2help` + エイリアス `/usr/local/bin/z2term`)、`ensureZ2ScanScript` (`/usr/local/bin/z2scan`)、GUI/z2run スクリプト、`ensureVersionScript` (`/usr/local/bin/z2version`)。
- **`z2version` コマンド (0.8.70)**: 端末から `z2version` でアプリ本体の版数 (`versionName`/`versionCode`/flavor/package/実行エンジン/rootfs 世代) を確認できる。launch 毎に書き直すので「今走っているアプリ」の版数が出る＝APK とゲストの版数不一致を即切り分け。`z2version --short` は版数 1 行のみ。proot/z2root/chroot の全起動経路に配置。
- **`z2adb` コマンド (0.8.88・セルフ adb)**: PC を繋がず、端末が**自分自身**の adb デーモン (Android のワイヤレスデバッグ) に `localhost` で繋ぐヘルパー (LADB 相当・root も USB も不要)。前提は Android 11+ の開発者オプション → ワイヤレスデバッグ ON。`z2adb setup` で distro に adb クライアントを導入 (apk: `android-tools` / apt: `adb` / pacman: `android-tools` を `detect_pm` で自動判定)、`z2adb pair <ポート> [6桁コード]` でペアリング、`z2adb connect <ポート>` で接続、以降 `z2adb shell` / `pm` / `logcat` 等を passthrough。宛先はポートのみなら `Z2ADB_HOST` (既定 `127.0.0.1`) を補い `host:port` ならそのまま。`setup`/`pair`/`connect`/`status`/`help` 以外は素の adb へ委譲し、`pair`/`connect`/`status` は adb 未導入時に一度だけ自動導入を試みる。PRoot/z2root は TCP を素通しする (dropbear と同経路) ため localhost に到達する。proot/z2root/chroot の全起動経路に配置 ([`Z2AdbScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2AdbScript.kt))。**adb サーバの先行起動 (0.8.89)**: adb は通常クライアント実行時に daemon が無ければ**自身を `execl(自パス)` で再起動**するが、z2root は `/proc/self/exe` を APK 内 `libz2root.so` と返すため ENOENT で失敗する (adb 全般の問題)。そこで `ensure_adb` が `start_server` を呼び、**自己 exec を伴わない `adb nodaemon server` を background で先行起動**する。起動前に `/proc/net/tcp{,6}` を見て対象ポート (`ADB_SERVER_SOCKET` のポート・既定 `5037`) が既に LISTEN (`0A`) なら立てない**冪等ガード** (`server_up`) を持ち、二重 bind による `Address already in use` の abort を避ける。以降のクライアントは fork せず既存サーバに繋がる。
- **`z2help` / `z2term` コマンド (0.8.90)**: ディストロに注入する独自 `z2*` コマンドの早見表を端末から引けるヘルプ。引数なしで全 `z2*` コマンドの分類済み一覧 (版数・情報／スマホ機能／GUI／つなぐ／ヘルプ) ＋一行説明を表示し、先頭にアプリ版数 (`z2version --short`) を併記する。本体は全て静的テキストで、quote 付き heredoc (`<<'Z2HELP_EOF'`) に入れるためシェル展開されない (外部入力なし)。`z2term` は当面 `z2help` の薄いエイリアス (`exec /usr/local/bin/z2help "$@"`) として同梱する予約コマンドで、将来 `z2term` を別用途に使いたくなったら [`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt) の `z2termAliasScript` を差し替えればよい。表示言語は `LocaleHelper.language` に追従。proot/z2root/chroot の全起動経路に配置 ([`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt))。
- **`z2scan` コマンド (0.8.91・脆弱性試験)**: 自端末/localhost 限定の脆弱性試験ヘルパー。z2term の哲学 (自端末・localhost 限定・非侵襲・外部送信なし・distro 公式パッケージのみ) に沿わせた 2 本立て。① **自己診断** (`z2scan self`): 外部ツール不要で、`/proc/net/tcp{,6}` から全インタフェース待ち受け (`0.0.0.0`/`::`) の TCP LISTEN を検出、`sshd_config` の危険設定 (PermitEmptyPasswords/PasswordAuthentication/PermitRootLogin yes)、`~/.ssh` と `authorized_keys` のパーミッション、主要ディレクトリの world-writable ファイル、SUID バイナリ (擬似 root 下なので参考表示)、`PATH` の空要素/`.` 混入を点検し、検出件数>0 で exit 1。② **スキャナ** (`net`/`host`/`cve`): distro 公式の `nmap`/`lynis`/`trivy`/`grype` を `ensure_pkg` (`detect_pm` で apk/apt/pacman 判定) で一度だけ導入して叩く薄いラッパー。`z2scan net` の nmap は `-sT -Pn` (root 不要)・**既定対象 `127.0.0.1`**、localhost 以外の対象は `--allow-remote` の明示＋警告が無いと拒否する (無許可のマス標的化を構造的に防ぐ)。`host` は lynis (無ければ `self` へフォールバック)、`cve` は trivy/grype があれば rootfs の既知 CVE をスキャン。スキャナ本体は同梱せず・結果はローカル出力のみ (F-Droid 適合・外部送信なし)。表示言語は `LocaleHelper.language` に追従。proot/z2root/chroot の全起動経路に配置 ([`Z2ScanScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ScanScript.kt))。
- `launchAndroidSh`: proot 不可時のフォールバック (`/system/bin/sh` + 最小 mkshrc)。

**実行エンジン z2root (裏機能・非 root)**: `executionEngine = "z2root"` のとき、`launch()` がバイナリを `nativeLibraryDir/libz2root.so`（自前 ptrace エンジン）に差し替える。proot 互換 argv subset を受けるので引数・env はそのまま流用（`PROOT_*`/talloc は z2root が無視）。`libz2root.so` 未同梱（`scripts/build-z2root.sh` 未実行）の場合は proot へフォールバック（**full のみ**。foss は proot を持たないため z2root が必須で、欠落時は engine binary not found で停止）。パス変換は proot 相当に強化済み（パス内 symlink の canonicalize / `/proc/<tid>/cwd` による cwd 相対パス絶対化 / `dirfd` 相対は非変換 / `renameat2`・`linkat`・`symlinkat` の2パス変換 / `utimensat` のパス変換 / execve ローダ差し替え・`#!` シバン解決 / 非 ELF・存在しない PATH 候補は loader を噛ませず素の execve でカーネルに `ENOENT`/`ENOEXEC` を返させる passthrough）。実機 Ubuntu 24.04 で `apt install hello` が end-to-end 成功（`Unpacking`→`Setting up`→`Hello, world!` 実行）まで確認済み (0.8.30)。0.8.32 で **seccomp-bpf による高速化**を導入＝従来は `PTRACE_SYSCALL` で全 syscall を 2 回トラップしていたのを、パス変換・fakeroot 偽装・getcwd 逆変換・/proc 偽装に必要な syscall だけ `SECCOMP_RET_TRACE` で捕捉し残りはネイティブ実行にした（proot と同方式）。実機ベンチで fork/exec 約2.3倍・read 約3倍・実 IO は proot の約2倍以内、FS 走査は proot より高速。0.8.34 で **read 非トレース化**を導入し 0.8.35 で**既定 ON**化＝seccomp 化後も `/proc/<pid>/status`・`loginuid` の偽装のために `read`/`close` を捕捉し続けるコストが残り、小 read 連打（`dd bs=1` 等）が proot 比約9倍だった。read-free では偽装を `openat` の瞬間に行う＝偽装済み内容を rootfs 内の使い捨て temp に書き出し `openat` のパスをそこへ差し替える（直後に unlink＝open-then-unlink）。以後の read は通常ファイルへの読み取りなので `read`/`close` を seccomp 対象から外せる（ネイティブ速）。実機検証（run-as）で `dd bs=1 ×300000` が約 8.1s→約 0.28s（proot 約 0.32s をわずかに上回る）、status/loginuid 偽装は維持・temp 残骸なしを確認。`Z2ROOT_NO_READFREE=1` で旧 read トレース経路へフォールバック可。0.8.36 で **glibc distro（Arch/Ubuntu）の対話シェルが起動しない不具合**を修正＝z2root + Arch で画面が真っ黒・プロンプト無し（固まって見える）になっていた。原因は、新しい glibc(2.42+) の `tcgetattr` が `ioctl(TCGETS2)` を使うが Android はアプリの pty への TCGETS2 を拒否（`EACCES`）するため `isatty()` が失敗し、bash/zsh が「端末でない」と判断して非対話起動（`PS1` 無し）になっていたこと（musl の Alpine は旧 `TCGETS` で無事、proot は ioctl を書き換えるので無事）。修正＝z2root が `ioctl` をトレースし、`TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` を entry で legacy（`TCGETS`/`TCSETS`/…）へ書き換える（先頭の `struct termios` 部分は termios2 と同レイアウトで通常 baud では実害なし）。実機検証＝Arch + z2root で対話 `[…]$` プロンプトに到達しコマンド実行を確認、Alpine(musl) は回帰なし。0.8.37 で **bind マウント配下のバイナリ直接実行**を修正＝ホーム（`-b <home>:/root` の `/root`）でコンパイルした実行ファイルを `./a.out` で動かせなかった（`error while loading shared libraries: … cannot open shared object file`、静的は `z2root loader: open(…): No such file or directory`）。原因＝動的 ELF のとき rootfs 内 `ld.so` に渡すプログラムパスを**ホスト実パス**にしていたが、`ld.so` 自身の `open()` も tracee として翻訳されるため、bind 配下のホストパスが「ゲストパス扱い」され rootfs を前置されて ENOENT になっていた（rootfs 配下のバイナリは host パスがそのまま rootfs 配下で二重変換抑止に当たり偶然動いていた）。修正＝`ld.so` には `host_to_guest` で逆変換した**ゲストパス**を渡す（`#!` シバン経路と同じ思想）ことで rootfs/bind の両方で正しく開ける。実機検証＝`cd /root && gcc -O2 hello.c -o hello && ./hello` が `sum(1..100)=5050` を出力、rootfs 内バイナリは回帰なし。`pacman -U` でのオフライン gcc 導入（run-as は SELinux `runas_app` ドメインで `sendmsg` が遮断されネット不可のため）と gcc 16.1.1 での実コンパイルも確認。静的バイナリは自前ローダ下で依然 segfault（別件・既知の制限）。フェーズ2（FOSS の外部表記ゼロ化）の実体で、詳細は `docs/FOSS-PURE-HANDOFF.md` §5。0.8.38 で **Linux GUI（`z2gui`: Xvnc + openbox + 端末）が z2root で動かない不具合**を修正＝z2root を選んで GUI を起動すると「VNC サーバが立たない／ビューアが接続できない」状態だった。原因＝z2root が AF_UNIX ソケットの `bind()`/`connect()` の `sun_path`（パス）を翻訳していなかったこと。X サーバはディスプレイソケットを `/tmp/.X11-unix/X1` に作るが、z2root がそのパスを無変換で通すためカーネルが**ホストの実 `/tmp`**（アプリには存在しない）へ作ろうとして `ENOENT` になっていた（同じ穴で dbus / pulseaudio の unix ソケットも壊れる）。proot はソケットアドレスを翻訳するので GUI が動いていた。修正＝z2root が `bind`/`connect`（aarch64 200/203）をトレースし、pathname AF_UNIX ソケットの `sun_path` を rootfs 内のホスト実パスへ書き換える（abstract ソケット = `sun_path[0]=='\0'` は名前空間上の名前でファイルではないため触らない＝共有 loopback でそのまま動く）。実機検証（run-as）＝`/tmp/.X11-unix/Xtest` への unix ソケット `bind()`+`connect()` が成功し、ソケットが**ホストの `/tmp` ではなく rootfs 内**に作られることを確認、ファイルパス翻訳に回帰なし。0.8.39 で **GUI が z2root で実際に描画**するところまで到達(0.8.38 で Xvnc は起動するが画面が真っ黒・"Connection reset" のままだった残課題を解消)。原因＝Alpine の `Xvnc` は musl 製で `accept(2)` を syscall 202 で直接呼ぶが、Android の untrusted_app seccomp は `accept`(202) を禁止(bionic は `accept4`(242) しか使わず allowlist に 202 が無い)→VNC 接続のたびに SIGSYS で弾かれ z2root が握り潰すしかなく接続が成立しない(毎回 `accepted: ::0` で切断)。SIGSYS 地点で `accept`→`accept4` に差し替えて再実行する手は aarch64 では不安定(syscall がスキップされ pc を綺麗に巻き戻せない)だったため、**libc 非依存の極小 `LD_PRELOAD` シム `libz2accept.so`(生 `svc`・依存ライブラリ無し)で `accept()` を `accept4(...,0)` に橋渡し**する方式を採用。z2root 起動時に全ゲストプロセスへ LD_PRELOAD する(`ProotLauncher` が rootfs の `/usr/local/lib/libz2accept.so` へ配置＋`LD_PRELOAD` を env 注入。シムは `scripts/build-z2root.sh` が生成・gitignore)。読み込み失敗は ld.so が警告して無視する非致命。実機(untrusted_app・実アプリ)検証＝z2root + Alpine + GUI で RFB ハンドシェイク完走(`accepted: 127.0.0.1::…`／protocol 3.8／pixel format)し openbox + xterm のデスクトップが描画。dropbear 等 `accept` する SSH サーバも併せて解消。0.8.40 で **GUI アプリ(mpv 等)が z2root 配下で X11 `BadAccess` を出して segfault する不具合**を修正＝Xvnc を `-extension MIT-SHM` 付きで起動し X 共有メモリ拡張を無効化した。クライアントが MIT-SHM(`X_ShmAttach`)を試みると、z2root では SysV 共有メモリの相乗りが通らず X サーバが `BadAccess` を返し、その非同期 X エラーで mpv 等が segfault していた(proot では `shmget` 自体が失敗してアプリ側が自動で非 SHM 描画にフォールバックするため顕在化しなかった)。VNC はローカル接続で共有メモリの利点がほぼ無いため、拡張ごと無効化して全クライアントを確実に通常描画(`XPutImage`)へ落とす(proot エンジンにも無害)。`z2gui` ランチャ(`GuiScript.kt`)は起動毎に rootfs へ書き直されるので既存 distro にも次回 GUI 起動から反映される。0.8.43 で **`/proc/self`・`/proc/thread-self` の中間パス誤解決**を修正＝0.8.41 は先頭の `/proc/self…` だけを `host_path_for()` で tracee pid へ書き換えていたが、間接 symlink が抜けていた。ゲストが `/proc/net/tcp` を開くと、カーネルの magic symlink `/proc/net` → `self/net` により `canonicalize_guest()` がパス途中で `self` 成分を walk し、それをトレーサ(z2root 親)として `readlink` するため `/proc/<別ホスト pid>/net/tcp` に解決され `EACCES` になっていた。修正＝`canonicalize_guest()` が `/proc` 直下に現れた `self`／`thread-self` 成分を(magic symlink を `readlink` せず)tracee pid へ解決する(先頭パス書き換えと整合)。開発環境で直接 `/proc/self/net/dev` と間接 `/proc/net/dev` が同一解決になることを確認(残る `EACCES` は外側サンドボックスが per-pid `net/*` を制限するためで実機では出ない)。SSH 認証直後リセットの調査中の動的トレースで発見。リセット自体は実機検証が必要(開発環境の失敗は stdin クローズ＝channel EOF で dropbear が PTY master を close→カーネル SIGHUP するアーティファクトで、stdin を開けばログインシェルは起動し MOTD まで出る＝PTY 経路は概ね機能。実機の対話 ssh は EOF を出さないため別要因の疑い)。`z2root.c` の `Z2ROOT_TRACE` 計測はこの実機トレース用に意図的に残置。0.8.44 で **設定の「実行エンジン」セクションに「このタブの実エンジン」表示**を追加＝設定チップ（＝次に起動する選択値）ではなく、そのタブが実際に起動したエンジン（`TerminalSession.actualEngine`。`ProotLauncher.resolveLaunchEngine()` か chroot 経路の結果）を出す読み取り専用行。選択が倒れたとき（z2root 未同梱→proot、chroot プローブ失敗→proot）も実態を正しく表示する。併せて、エンジン選択を表示/非表示する**バージョン行 7 タップのトグルに 3 秒のクールダウン**を入れ、連打で即座に逆方向へ戻らないようにした。0.8.47 で **`--link2symlink`（ハードリンク `linkat` のエミュレート）を作り直し、git・npm・コピー系コマンドが z2root で壊れる不具合**を修正＝旧実装は `linkat(old,new)` を「`new` を `old` のゲスト絶対パスへの symlink」に化かしていたが、これは git の loose object 確定（`tmp` に書く→`link(tmp,final)`→`unlink(tmp)`）で `final` が直後に消える `tmp` を指す**dangling symlink** になり「`fatal: … is not a valid object`」でコミットが壊れた（dpkg は元ファイルが残るので無害だっただけ）。npm の global install もキャッシュからの**ハードリンク**で展開するため、`claude code` が「ロゴも出ず無反応」だったのも同じ dangling 化（本体 JS が壊れる）が有力。修正＝**まず実ハードリンクを試し、Android のアプリ内 FS が `link()` を `EACCES`/`EPERM`/`EXDEV` 等で拒否したときだけ、トレーサ側で `old` を `new` へコピーして成功(0)を返す**方式に変更（`linkat` を entry でホスト実パスへ翻訳して実行し、exit で戻り値を見てコピー fallback）。実ハードリンクが通る環境では本来の共有 inode 意味論を保ち、通らない `/data` 上でも `new` が独立した実ファイルになるので `old` を後で `unlink` しても残る＝「リンクで原子的に確定」する汎用パターン（git/coreutils/ビルド系）が一様に動く。`new` が既に存在する（本来 `EEXIST`）等の本物のエラーは保持する。実機検証＝`ln orig hard; rm orig; cat hard` が `hard`（実ファイル）として中身を保持し、`git init`→`add`→`commit`→`log`→`cat-file` の全サイクルが成功。⚠️**旧 z2root で `npm install` 済みのパッケージは既にファイルが dangling symlink 化しているため、本修正後に再インストールが必要**。0.8.48 で **「stale `libz2root.so`」事故を構造的に防止**＝z2root/z2accept の `.so` はビルド成果物（git 管理外）で `git pull` や CMake では再生成されないため、`z2root.c` を git で直しても**古い `.so` が APK に同梱され続ける**（前述 0.8.47 の git/npm 破壊が長引いた真因がこれ）。Gradle タスク `buildZ2rootNative` を追加し、`full` フレーバーの jniLibs マージ前に `scripts/build-z2root.sh` を自動実行する＝`./gradlew assembleFull*` だけで常に現ソースから `.so` を再生成（手動手順ゼロ）。`build-z2root.sh` は NDK パスを自己解決する（環境変数／`local.properties` の `sdk.dir`+`ndk.version`／`$ANDROID_HOME`）。`foss` フレーバーは実行時 DL のため対象外。z2root 自体の挙動は 0.8.47 から変更なし。0.8.49 で **`claude code`(node) が z2root 配下で起動しない不具合**を修正＝node が起動直後に `node: src/unix/core.c:646: uv__close: Assertion 'fd > STDERR_FILENO' failed.`＋SIGABRT で落ちていた。原因＝z2root の SIGSYS ハンドラは Android seccomp が禁ずる syscall を**一律 0（成功偽装）**で握り潰す fakeroot 方針だが、これが `io_uring_setup`(425) にも適用され、libuv が偽装された `0` を有効な io_uring の ring fd と誤認→fd 0 をバックエンドとして保持→後で `uv__close(0)` を呼ぶ→`uv__close` は fd ≤ STDERR_FILENO(2) の close で abort する安全弁＝assertion 発火。修正＝SIGSYS ハンドラが io_uring 3 番号(`io_uring_setup`=425/`io_uring_enter`=426/`io_uring_register`=427)だけは 0 でなく **`-ENOSYS`(-38)** を返し「未実装」を見せて libuv を epoll バックエンドへフォールバックさせる（proot は元から io_uring 不可なので動いていた＝同じ状態に揃える）。他の SIGSYS は従来どおり 0 偽装。検証＝dev シェルは proot 配下で z2root をネストすると二重 ptrace でマスクされるため、z2root エンジンで立てた sshd へ ssh（単一 ptrace の実条件）で再現・修正確認（LD_PRELOAD で `io_uring_setup` を強制 ENOSYS にすると node も git も治ることを実証してから本体修正）。⚠️ ハードリンク方式の `git clone` が `fatal: hardlink different from source` で失敗する件は別タスク（当面 `git clone --no-hardlinks` で回避）。0.8.53 で **GUI 音声が z2root 配下で無音になる不具合**を修正（proot では動作済み）。原因は2つ。(1) PulseAudio の `--daemonize` は detach 時に `/proc/self/exe` を re-`execve` して自己 daemon 化するが、z2root では `/proc/self/exe` がランチャ（`libz2root.so`）に解決され「cannot self execute」で daemon が起動しない。GUI 起動スクリプト（`GuiScript.kt`）を `--daemonize` 廃止＝`setsid pulseaudio -n --exit-idle-time=-1 … &`（`setsid`+`&` で背景化、停止は `pactl exit`）へ変更。(2) PulseAudio クライアントは `AF_UNIX` ハンドシェイクで `SCM_CREDENTIALS` に自分の uid/gid を載せて `sendmsg` するが、カーネルは申告 uid が実/実効/保存 uid のいずれか（または `CAP_SETUID`）と一致しないと `EPERM` を返す。z2root の fake_root は uid=0 を偽装する一方で非特権アプリの実 uid は非 0 のため不一致→`sendmsg(2)` が `EPERM`→クライアントが "Connection died" で死ぬ。修正＝z2root が fake_root 配下で `sendmsg`(211)/`recvmsg`(212) をトレースし `SCM_CREDENTIALS` の ucred を書き換える（送信時はプロセスの実 uid/gid へ、受信時は 0 へ戻す）。これでカーネルはメッセージを受理しつつ rootfs からは root に見える。`SCM_RIGHTS`/memfd の受け渡しは無変更（元から動作）。検証＝z2root + GUI で音が出る・`/tmp/z2gui-audio-<display>.log` に "Connection died" が出ない・`pactl info` で `z2sink` が見える。0.8.54 で **bind マウント配下の静的 ELF がオンデバイス（z2term 自身）で exec できない不具合**を修正し、併せて **`scripts/build-z2root.sh` をオンデバイス自己ホストビルド対応**にした。原因＝静的 ELF を `--loader` で起動する際、loader にプログラムの**ホスト実パス**を渡していたが、loader 自身の `open()` も tracee として翻訳されるため、bind 配下（`-b <home>:/root` 下の NDK 静的 clang 等）が「ゲストパス扱い→rootfs 前置」され ENOENT（`z2root loader: open(…/clang-21): No such file`）になっていた（0.8.37 が動的 ELF で直したのと同じ穴の静的版）。修正＝動的 ELF 経路が `ld.so` に `guest_real` を渡すのと同じく、loader にも `host_to_guest` で逆変換した**ゲストパス**を渡す＝rootfs/bind の両方で静的バイナリを正しく map できる。ビルド側＝NDK の clang は静的 ELF なので**この修正を含む APK を入れる前の現行エンジン下では exec 不可**。そこで `build-z2root.sh` に自動フォールバックを追加＝NDK clang が exec できなければ exec 可能な rootfs の動的 clang をクロスコンパイラに使い（`--target=aarch64-linux-android29 --sysroot=<NDK sysroot>`）、NDK の静的ライブラリ/crt を **GNU ld で手動リンク**する（clang ドライバの自動リンクは lld 専用フラグ `--use-android-relr-tags` を渡し GNU ld が拒否するため使わない）。PC ビルドは probe（`clang --version` が "clang version" を出すか）を通過し従来どおり NDK ツールチェーンを使う＝挙動不変。検証＝この z2root term 上で `bash scripts/build-z2root.sh` が完走し `libz2root.so`（静的 EXEC AArch64・NDK r29・依存なし・stripped）と `libz2accept.so` を `jniLibs/arm64-v8a/` に生成＝ネイティブ部分のオンデバイス自己ホストビルドが成立。(A) ローダ修正と (B) フォールバックは密結合（A が無いと自己ホストした z2root が静的バイナリを exec できず、B が無いと A 入り `.so` をオンデバイスで作れない）。0.8.55 で **オンデバイス（z2term）での `assembleFullRelease` を通せるよう accept シム `libz2accept.so` を bionic 安全化**した。オンデバイスビルドでは JVM（musl）の `accept`(202) を通すためビルド全体に `LD_PRELOAD=libz2accept.so` を注入するが、シムが `__errno_location`（musl/glibc の errno 実体）を**非 weak の未解決シンボル**として参照していたため、AGP が起動する **bionic 製 aapt2 に LD_PRELOAD が漏れると `cannot locate symbol __errno_location` で aapt2 が起動失敗**し `processFullReleaseResources` で停止していた（bionic は `__errno()` を使い `__errno_location` を持たない）。修正＝`__errno_location` を `__attribute__((weak))` ＋ NULL ガードにし、未解決でもロードを通す（bionic では 0 に解決＝無害、musl/glibc では従来どおり errno を設定）。検証＝proot エンジン下で `LD_PRELOAD=libz2accept.so ./gradlew :app:assembleFullRelease` が `BUILD SUCCESSFUL`（当時は「z2root は重い full ビルドでフリーズする」と見て proot で検証したが、後に 0.8.62 を z2root 上で 16m58s・フリーズ無しで完走＝重い full ビルドで z2root と proot に差は無いと判明。どちらのエンジンでもビルド可）、生成 APK（69MB・release 鍵署名）の同梱 `libz2accept.so` が WEAK `__errno_location`・`libz2root.so` が case-3 修正入り NDK r29 静的 EXEC であることを unzip+readelf で確認。なお merge の増分キャッシュが旧 `.so` を stale 同梱する事象に当たったため fullRelease 中間物を rm して再ビルドした（0.8.48 の `buildZ2rootNative` 依存だけでは増分 merge を強制更新できない場合がある）。0.8.56 で **オンデバイス（z2term）での `assembleFullRelease` を阻む 2 つの parity gap を修正**した。(1) **レガシー `--link2symlink`（`.l2s`）チェーンを open で辿れない**＝NDK の `libc++_shared.so` が proot/旧 z2root の link2symlink で多段 symlink 化（`libc++_shared.so`→`.l2s.…0001`→`.l2s.…0001.000N`＝実体）されており、CMake のネイティブリンクが `ld.lld: unable to find library -lc++_shared` で失敗していた。原因＝`canonicalize_guest()` が `readlink` で得たリンク先を常に「ゲストパス」として walk するが、link2symlink が格納するリンク先は**ホスト実パス**（`.../shared_home/android-sdk/…`）のため、そのまま walk すると `translate_abs` が rootfs を二重前置して ENOENT になっていた。修正＝`canonicalize_guest()` で絶対リンク先を `host_to_guest()` でゲストへ逆変換してから継続する（該当しないリンク先は素通しなので通常の絶対 symlink には無害）。(2) **Android ネイティブの aapt2 が起動できない**＝CMake gap を外すと次に `processFossDebugResources`/`…ReleaseResources` の AAPT2 daemon 起動が `error: expected absolute path: "--argv0"` で失敗した。原因＝aapt2 は Android の aarch64 ELF（interp=`/system/bin/linker64`）で、z2root は動的 ELF を `<interp> --argv0 <name> <prog> <args>` で起動するが、**この端末（Android 12）の bionic linker64 は glibc/musl の ld.so と違い `--argv0` を解さず**、実プログラムの argv へ素通しするため aapt2 が `--argv0` をパス引数と誤認していた（`/system/bin/linker64 aapt2 version` は成功、`--argv0` 付きは同エラー、と実証）。kotlinc/java（glibc ld.so）は `--argv0` を解すので通っていた。修正＝`plan_exec()` の動的 ELF 経路で interp basename が `linker64`/`linker`（bionic）のときだけ `--argv0`+argv0 を渡さない（bionic では argv0 が実プログラムパスになるが Android ツールは argv0 を見ないため実害なし）。✅**この 2 件は 0.8.56 APK を本体 UI でインストールし z2root エンジン上で e2e 検証済み（2026-06-09）**＝`.l2s` チェーン（NDK `libc++_shared.so`）を cp 実体化なしで open でき先頭 ELF マジック取得、aapt2 が `--argv0` エラー無く `version`／`daemon`（`Ready`）起動。詳細経緯は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §7/§8。0.8.57 で **readlinkat 戻り値の切り詰めバグ**を修正＝`.l2s` 等の symlink を `readlink(2)` すると `/root/android-sdk/n`（19B）のように途中で切れていた。原因＝tracee はリンク長 `lstat` の `st_size`（z2root がゲスト長に逆変換済み＝短い）でバッファを確保するのに、カーネルはホスト実パス（長い）をそのバッファへ切り詰めて書き込み、それを `host_to_guest()` するとさらに短くなっていた。修正＝proot 同様、exit で z2root 自身が対象 symlink のホスト実パスを full バッファで `readlink` し直してから `host_to_guest()` 変換し `bufsiz` でクランプして書き戻す（entry で対象のホスト実パスを `pid_state.aux_path` に控える。`dirfd` 相対などホストパス未確定時は従来の tracee バッファ読みにフォールバック）。リンカは open するだけなので 0.8.56 のビルド成立には影響しないが、`.l2s` 系を `readlink` 依存で扱うツールへの備え。⚠️**0.8.57 の readlink 修正自体の e2e は本修正入り APK の本体 UI インストール後に確認が必要**。0.8.58 で **B-3＝ローカル `git clone` の `fatal: hardlink different from source` を修正**した。真因は Android SELinux（`untrusted_app`）が `link(2)` を端末全域で禁止する OS 制約で、link2symlink が常に copy-fallback（別 inode）になり、git 2.46+ の「`link()` 後に dest を lstat し src と `st_dev`/`st_ino` 一致を検証」に落ちる点。修正＝copy-fallback 成立時に (src_dev, src_ino, dest_ino) を小リング（32件）へ記録し、stat 系（`newfstatat`/`fstat`/`statx`）exit で dest_ino 一致時に `st_dev`/`st_ino`（statx は `stx_ino`＋`stx_dev_major/minor`）を src 値へ偽装。一致したら即エビクトで偽装窓を最小化、有効エントリ 0 の間は hot path を素通り。実 `link()` 成功経路は fallback しないので `ln`/`npm`/`tar` 等に退行なし。e2e は本修正入り APK 導入後に確認が必要。0.8.59 で **自前ローダ（`load_elf_and_jump`）が static-PIE（ET_DYN）を扱えるよう relocation 適用と phdr バイアスを追加**した（従来から続く「静的バイナリが segfault する」既知制限の一部解消）。NDK ビルドの静的バイナリには ET_EXEC（非PIE）と ET_DYN（static-PIE）があり、後者はカーネルもインタプリタも relocation せず、bionic NDK の static-PIE crt も自己 relocation しないため、`base!=0` でロードすると未 relocate ポインタ参照や `__libc_init_mte`/`__bionic_get_tls_segment` の「`load_bias=0` 即値仮定（phdr の `p_vaddr` を絶対アドレス扱い＝ET_EXEC 前提）」で落ちる。修正＝ローダが ld.so/proot loader 相当の下準備を肩代わりする＝(1) `PT_DYNAMIC` を辿って RELR/RELA（`DT_RELR`/`DT_ANDROID_RELR`/`DT_RELA`）の `R_AARCH64_RELATIVE`(1027) を `*(base+off)=base+addend` で自前適用、(2) phdr のコピーを作り各 `p_vaddr` に `base` を加算した配列を `AT_PHDR` に渡し bionic の `bias=0` 仮定を成立させる。いずれも `ET_DYN && base!=0` のときだけ動作し ET_EXEC（`base==0`）は素通り＝非PIE 静的バイナリと NDK clang/lld 自身に退行なし。in-process 検証ハーネスで単純 static-PIE（`write` のみ）が動くこと・非PIE が回帰しないことを確認。⚠️**ただし printf/malloc/pthread/TLS を使う「リッチな」static-PIE は別の根本制約で依然 crash する＝ローダでは解決不能**。`__attribute__((constructor))` を仕込んだ static-PIE では `CTOR_RAN` が出ず `main` のみ実行されることから、**bionic NDK の static-PIE crt（`_start`）が `.init_array` コンストラクタを呼ばない**（非PIE crt は `__init_array_start/end` を読み structors にセットするが、static-PIE crt の `_start_main` は `fini` しか処理せず init_array セットアップ命令が欠落）のが真因。コンストラクタは libc 初期化後・`main` 前に走る必要がありローダは `_start` へ jump 後に制御を失う＝後追い呼び出し不可で、これは proot/カーネルでも同じ結果になる **NDK 固有の制約（z2root の parity gap ではない）**。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11。0.8.62 で **B-3（git clone の hardlink 偽装）が稼働 0.8.61 上で構造的に未発火だった件を再修正**した。稼働エンジン上で C プローブにより切り分けた結果、linkat copy-fallback 200 件すべてが別 inode の dest を生成し stat 偽装が一度も発火しない（0 fake）ことが判明＝0.8.58 の「コンパイル済だがおそらく動く」仮定を否定。真因は旧 `linkcopy_record` が dest のホストパスを**後から `stat()` し直して** inode を採取しており、tracee が `newfstatat` で読む inode とずれて照合が常に miss していたこと。修正＝`copy_for_link` を out-param 付きへ変更し、**コピー生成直後の出力 fd を `fstat()`** して dest inode を確定採取（tracee が後で見る実体と同一を保証）＋`linkcopy_record` を値渡し化して再 `stat()` を排除。コンパイル確認済・実機 e2e（git clone が hardlink 検証を通ること）は次 APK 導入後。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10。0.8.63 で **0.8.62 が招いた z2root 起動退行（ゲスト＝`Arch Linux ARM` が起動直後に `exitCode=-1` で即死）を修正**した。真因＝0.8.62 で linkcopy の記録が**初めて成功するようになった**結果、それまで `g_linkcopy_used==0` で素通りしていた `newfstatat`/`fstat`/`statx` exit の stat 偽装ホットパスが常時 ON になったこと。この偽装は照合キーが **inode 番号だけ**で（コメントは「別 fs の inode 衝突リスクは無視できる」としていたが誤り）、Android `untrusted_app` は `link(2)` を全域禁止＝ゲストのハードリンクは全部 copy-fallback して記録されるため、起動中に init/ld が stat した無関係なファイルの inode 番号がたまたま記録済み dest と衝突すると、その `st_dev`/`st_ino` が無縁の src 値へ偽装され、ゲストの起動時 stat が壊れて即死していた。修正＝照合キーを dest の **`(dev, ino)` 両方**に厳格化（生成直後の実体は host の `(dev, ino)` が一意なので、`copy_for_link` の `fstat` で `dest_dev` も採取し `linkcopy_find` を dev+ino 一致に変更。statx は `stx_dev_major/minor` から dev を復元して照合）。B-3 の hardlink 偽装は維持。コンパイル確認済・実機 e2e は次 APK 導入後。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10。0.8.64 で **0.8.63 が直せていなかった同じ起動退行（ゲスト即 `exitCode=-1`）を実際に修正**した。0.8.63 の `(dev, ino)` 厳格化は無効だった＝dest はコピーで rootfs bind 配下＝ゲスト全ファイルと同じ host `/data` パーティション上に作られるため `st_dev` は rootfs 全域で同一の固定値で、`(dev, ino)` 照合は実質 inode 単独照合と変わらず、起動中の無関係ファイルの inode 衝突で誤偽装が継続していた。修正＝inode 照合を**パス相関**へ置換。`linkcopy_record` がコピー先の**ホスト実パス**を記録し、`newfstatat`/`statx` の **entry** で stat 対象のホストパスを `host_path_for` で解決して記録済み dest と一致したときだけ **exit** で `st_dev`/`st_ino`（statx は `stx_ino`＋`stx_dev_major/minor`）を src へ偽装する。これで無関係ファイルへの誤ヒットは原理的に起きない（fd ベースの `fstat` は entry でパスを取れないため inode 偽装の対象外＝uid/gid 偽装のみ。git の hardlink 検証は `lstat`/`newfstatat` 経路を使うため B-3 に影響なし）。B-3 の hardlink 偽装は維持。コンパイル確認済・実機 e2e は次 APK 導入後。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10。0.8.67 で **起動退行の真因を確定し根治**した＝**0.8.62〜0.8.64 の stat 偽装をめぐる修正はこの退行の真因ではなかった（誤診）**。診断トレース＋SIGSEGV 全レジスタダンプで真因を特定＝0.8.59 で `load_elf_and_jump` に入れた RELATIVE/RELR 肩代わりが、全動的バイナリの起動経路でロードされる `ld.so`（`ld-linux-aarch64.so.1`）にも当たっていたこと。glibc/musl/bionic の ld.so は `_dl_start` で自己 relocate するため、ローダが load bias を二重加算し RELATIVE 再配置の全ポインタが ×2 になって `blr x8`（x8=実値×2）で命令フェッチ SIGSEGV していた（決定的証拠＝`pc==si_addr==x8==実 ld.so アドレス×2`、別 run でも一致）。修正＝ローダの肩代わりを `skip_reloc` でゲートし、`plan_exec` の動的 ELF／動的 interp 経路（loader 対象＝自己 relocate する ld.so）は `--loader-noreloc` で抑止、静的 PIE 直接ロードのみ `--loader` で 0.8.59 どおり適用する。stat 偽装（パス相関）自体は B-3 用として有効なので残置。コンパイル確認済・実機 e2e は次 APK 導入後。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10「0.8.67 で起動退行を真に根治」。0.8.78 で **musl `ld.so` の動的 ET_EXEC 明示起動不可問題を根治**＝musl の `ld.so` は ET_EXEC (非PIE) を「コマンドとして明示起動」できず `Not a valid dynamic program` で落ちるため、Alpine(musl) では glibc/musl の ET_EXEC バイナリ (claude 本体・`cc` 等) が z2root で起動できなかった。`--loader-exec <ld.so> <prog> <argv0> [args...]` 経路を新設し、本体と `ld.so` を両方 `mmap` し、**カーネルが `PT_INTERP` 経由で exec したのと同じ初期スタック/auxv** (`AT_PHDR`/`AT_PHENT`/`AT_PHNUM`=本体の phdr、`AT_ENTRY`=本体エントリ、`AT_BASE`=`ld.so` の load base) を組んで `ld.so` のエントリへ分岐する (`load_exec_via_interp`/`map_img`)。これで musl は「インタプリタとして起動された」と判定し本体を relocation して起動する (proot loader 相当)。振り分けは `plan_exec` で **interp basename が `ld-musl*` かつ対象が ET_EXEC のときだけ**行い、glibc `ld.so` (ET_EXEC を明示起動で受ける既存 Arch claude 経路) や PIE は非対象として温存する。`use_loader` 無効時は従来経路へフォールバック。⚠️**実機 e2e は本修正入り APK 導入後に確認が必要**。0.8.84 で **大きい argv を渡す exec が `ENOENT` で失敗する退行を修正**＝`rewrite_execve` は元 argv をトレーシから読み再構成してゲストのスタック下に `[target][argv blob][ポインタ配列]` を置き path/argv レジスタを差し替えるが、(1) argv 連結バッファが固定長 `char blob[8192]` で `blob_sz>8192` のとき `if (blob_sz<=sizeof(blob))` が偽になり**書き換えを丸ごとスキップ**→path レジスタにゲストパスが残ったまま execve され ENOENT、(2) argv 読み取り上限 `MAX_ARGS 256` で 256 個目以降を切り捨て、の二重制限を持っていた。クロスディストロ cmdtest e2e で Kali の `apt-get install python3` が dpkg の byte-compile（`python3.13 -E -S py_compile.py <287ファイル＝~11KB argv>`）で踏んで `cannot execute: required file not found` 失敗するのを発見（二分で「argv 総バイト ~7.5KB 超・カーネル ARG_MAX 2MB 以下＝z2root 内部バッファ起因」と確定）。修正＝argv 読み取りを上限なしの動的確保（`realloc`）に、`blob`/`parts`/`ptrs` を argv サイズ依存の `malloc` にして `MAX_ARGS` を撤去（scratch は従来どおり `sp` 直下＝growsdown stack を `process_vm_writev` が伸長するため大 argv でも mapped）。Alpine/Ubuntu の cmdtest は非ゼロ 0 件。⚠️**Kali での python 導入完走＋大 argv exec の実機 e2e は本修正入り APK 導入後に確認が必要**。

**実行エンジン chroot (裏機能・要 root)**: `executionEngine = "chroot"` のとき `launchChroot()` を使う。

- **エンジン選択の解放/解除（トグル）**: 設定のバージョンを 7 回タップで `engineSelectorUnlocked` をトグルする（非 root でも可）。解放時は `true`（proot / z2root が選べる）になり、続けて `probeRootChroot()` のセルフテストが成功した場合のみ `rootChrootUnlocked=true` となり chroot も選択肢に加わる。解放済みの状態でさらに 7 回タップすると `false` に戻し、同時に `executionEngine` を既定の proot へリセットして「表示前の状態」へ復帰する（0.8.33 で双方向トグル化）。
- `probeRootChroot()`: `su -c id`(uid=0) + `su -c "chroot <rootfs> /bin/sh -c echo"` のセルフテスト。結果は `RootProbe`(Ok/NoRoot/ChrootBlocked)。
- `launchChroot()`: `su -c` で bind mount(/dev,/dev/pts,/proc,/sys,/root,/sdcard) → `chroot` → login shell。`ensure*`(z2-*/OSC7/履歴/sshd/gui/z2run) は proot 経路と共通で流用。
- **Ctrl+C / ジョブ制御**: su 経由だと制御端末を所有できないため、login shell を **`setsid -c` 経由**で起動して有効化。
- chroot 起動失敗時は proot へ自動フォールバック（`TerminalSession.startTerminal`）。SELinux Enforcing 下の root 端末(moto g13/Magisk)で end-to-end 検証済み。`full` フレーバー専用。

### 4.4 ディストロ管理 (`distro/`)

- `DistroBundle`: `ROOTFS_VERSION`(=6)、`VERSION_MARKER`、`BUNDLED_DISTRO_ID="alpine"`。
- `DistroSpec`: id/表示名/パッケージマネージャ/同梱可否/asset 名/DL URL or index URL/既定シェル/DL サイズ目安。
  - Alpine = 同梱 (`alpine-minirootfs-aarch64.tgz`, zsh)。Ubuntu/Arch/Kali = linuxcontainers の index から最新 `rootfs.tar.xz` を実行時解決して DL (bash)。
- `DistroInstaller`: 依存無しの手書き tar パーサ (ustar/GNU `L`/PAX `x`/`g`、symlink/hardlink)。`decompress` がマジックバイトで gzip/xz 判定。
  - `postInstallSetup`: resolv.conf/hosts、`pacman.conf` (sandbox/DownloadUser 無効化)、apt の Sandbox::User=root、version マーカー書込。
  - パーミッションは **owner-only** (`setUnixMode(ownerOnly=true)`)。world-writable だと sudo が拒否する。
- `DistroDownloader`: HTTP DL + SHA256 検証、`cacheDir/distros/<id>-<abi>.tgz` にキャッシュ。

### 4.5 ターミナルエミュレータ (`emulator/`)

- `TerminalEmulator`: バイト列を状態機械 (Ground/Escape/CSI/OSC…) で処理。
  - 文字幅: East Asian Width 対応 (`ambiguousAsWide` 設定で曖昧幅を 2 セル化)。BMP 外 (絵文字 😀 / CJK 拡張) はサロゲートペアを左セル=高サロゲート・右セル (`wideCont`)=低サロゲートに分けて 2 セル格納する。**描画 (`TerminalRenderer.glyphAt`)・選択コピー (`getRangeText`)・行テキスト (`toText`) では左右セルを結合して 1 グリフとして扱う** (0.8.74)。以前は右セルを捨てて高サロゲート単独を描画/出力し、孤立サロゲート＝豆腐(?)になっていた。
  - SGR: 太字/下線/反転/取消線、16/256/RGB(truecolor)。
  - DEC モード: 代替画面、カーソルキー (DECCKM)、**マウスレポート** (X10/Normal/Button/Any × Legacy/SGR/urxvt)。
  - OSC: 7(cwd)/8(hyperlink)/10-12(前景/背景/カーソル色、`?` で query 応答)/52(クリップボード)/palette。OSC タイトルは UTF-8 デコード（日本語タブ名の文字化け防止）。
  - **URL/OSC8 リンクのセルに下線表示**。長い URL は折り返し元の行に wrapped フラグを持たせて検出（タップで開く）。
  - bracketed paste (DECSET 2004) 対応。
  - `cursorKeyBytes`, `encodeMouseEvent`, `resize`(cursor-aware), scrollback。
- `SearchEngine` (M11): スクロールバック全文検索。🔍 → 文字入力 → ↑↓ で前後ジャンプ。CJK は **セル列**でハイライト位置を計算。
- `TerminalBuffer`/`TerminalRow`/`TerminalCell`/`SgrAttribute`: セル格納とスクロールバック。
- `TerminalColors`/`AvailableThemes`: 9 テーマ (ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night / Catppuccin Mocha / Catppuccin Latte / Monokai)。

### 4.6 ドメイン (`core/`)

- `SessionManager` (object): `TerminalSession` のリスト + active を `StateFlow` で公開。`ensureFirst`/`openNew`/`close`/`setActive`/`moveSession`（タブのドラッグ並べ替え）。`close` は先に UI からタブを外し、停止処理 (PTY/SSH 切断・GUI=Xvnc 停止) は裏で実行してタブ消去のもたつきを防ぐ。
- `TerminalSession`: 状態機械 `IDLE→INSTALLING→STARTING→RUNNING→EXITED/ERROR`。
  - emulator 専用 dispatcher、PTY 読みループ、`writeBytes`、resize、`startTerminal`/`switchDistro`/`restart`/`reinstallDistro`/`startSsh`。
  - `StateFlow`: uiState / redrawTick(≈60fps コアレッシング) / scrollOffset / cellMetrics / selection / cwd / label / settingsFlow。
- `TerminalSelection` / `CellMetrics`: 選択範囲 (絶対行) と 1 セル寸法。
- `SessionStore`/`SessionManager` (M11): タブ構成 `{id,label,distro,cwd}` + activeId を DataStore に保存する（書き込みのみ）。**0.8.70 で起動時の自動復元を無効化**＝起動の度に複数タブが開く挙動を避けるため、`ensureFirst` は常に新規 1 タブだけを開く（ユーザー要望）。`save` は将来の復元 UI / デバッグ用に残すが読み戻し経路は持たない。**cwd は OSC7 で捕捉**（`ensureOsc7CwdConfig` が bash/zsh のプロンプトフックで OSC7 を吐かせる）。

### 4.7 通信チャネル (`channel/`)

- `ProcessChannel` (interface): `reader`/`writer`/`isAlive`/`exitCode`/`resize`/`close`。
- `LocalPtyChannel`: PtyProcess をラップ (ローカル proot)。
- `SshChannel`: JSch でリモート接続。`shell` チャネル + `-L` ローカルポート転送、host key 検証 (`KnownHosts`/`HostKeyVerificationDialog`)、鍵は Keystore で暗号化 (`KeystoreCrypt`)。
- `SshProfile`/`PortForward`: DataStore (`z2term_ssh`) に JSON 永続化。

### 4.8 設定 (`settings/AppSettings.kt`)

- DataStore (`z2term_settings`) を `Snapshot` データクラス + `Flow` で公開。各 setter は suspend。
- 項目は[§7](#7-設定項目)。

### 4.9 常駐サービス (`service/TerminalService.kt`)

- `foregroundServiceType=specialUse`。`start`/`detach`(常駐解除のみ・セッション維持)/`stop`(全終了)。
- `PARTIAL_WAKE_LOCK`、通知 (`ic_notification` = 透過 Z2 アイコン、タップで復帰 / 停止アクション)。

### 4.10 ファイル連携 (`saf/Z2TermDocumentsProvider.kt`)

- `DocumentsProvider` (authority `<applicationId>.documents`、`permission=MANAGE_DOCUMENTS`)。
- 公開ルート: **ホーム = `shared_home`** (端末の `/root` と同一実体) + 各 distro の rootfs(`/`)。
- traversal 防止: 許可ルート `[shared_home, distros]` 配下のみ。R/W/作成/削除/リネーム対応。

### 4.11 UI 詳細 (`ui/`)

- `terminal/TerminalScreen.kt`: 全体レイアウト。TopBar / TabBar / 描画領域 / キーボードトグル / キーボード領域。`KeyboardMode = CUSTOM | SYSTEM`。**横画面**は `LocalView.OnLayoutChangeListener` で向きを検知し、`landscapeKeyboardPosition`/`Width`/`Height` 設定に従って Row レイアウト (`SideKeyboardColumn`) に切替。`landscapeScaledStyle()` で keyHeight/font が横画面高さに比例拡縮。
  - **ツールバー (`ReorderableToolbar`)**: 📋貼付 / 📜コマンド / 💡画面消灯ロック / 🔒バックグラウンド常駐 / 🔍検索 / ⌨キーボード切替 / ⚙設定 を `ToolbarItem` のリストで描く。**通常タップ=動作、長押しドラッグで並べ替え** (`detectDragGesturesAfterLongPress` + 隣との中心越えで `order` 入替)。長押し中は `ToolbarTooltip` で簡易説明を Popup 表示。並びは `AppSettings.toolbarOrder` (カンマ区切り id) に永続化し、`mergeToolbarOrder` で既存順とマージするのでボタン追加/削除でも壊れない。🔒常駐は既定で 💡 の右。GUI タブ (`GuiTopBar`) も同 `ReorderableToolbar` を共有 (検索なし・📋/📜 は keysym 橋渡し)。
- `terminal/TerminalRenderer.kt`: ネイティブ Canvas に **セル単位 drawText** (advance≠cellW のサブピクセル誤差累積を回避)。背景→選択ハイライト→文字→カーソル→選択ハンドルの順。
- `terminal/input/TerminalInputView.kt` (AndroidView): 物理キー/OS IME 入力、ジェスチャ (タップ/長押し選択/ドラッグスクロール/ピンチ拡縮/マウスクリック送出)。選択は[§6.5](#65-テキスト選択-ux)。
- `terminal/keyboard/`:
  - `TerminalKeyboard.kt`: 5 行独自キーボード。3 状態 Shift、フリック、全キー長押し連打。**押下時にキー背景を明るい緑に**、**フリック中はしきい値超えた方向のヒントを太字 + 1.6 倍拡大** (中央文字は不変)。
  - `JapaneseFlickKeyboard.kt`: 内蔵 日本語/カタカナ フリック。同じプレス/フリック視覚フィードバック。
  - `KeyboardStyle.kt`: COMPACT(44dp) / SPACIOUS(60dp、4 方向フリック)。`naturalHeight`。`.copy()` で横画面用に拡縮済 style を作る。
  - `KeyGestures.kt`: タップ + 長押し連打の共通ジェスチャ (`onPressedChange` コールバックで press 状態を Composable に伝える)。
  - `components/SpecialKeyBar.kt`: OS IME 時の特殊キー列。
- `settings/SettingsSheet.kt` + `SshAccessHelper.kt`: 設定モーダル + SSH/ストレージ ヘルパー。
- `ssh/SshProfilesSheet.kt` + `HostKeyVerificationDialog.kt`: SSH プロファイル UI + 鍵検証。
- `snippets/SnippetsSheet.kt`: コマンドスニペット (1 行タップで挿入、並替/編集)。

### 4.12 GUI デスクトップ (`gui/`)

- distro 内で **Xvnc**(VNC サーバ) + 軽量 WM/アプリを起動（`proot/GuiScript.kt` が冪等で配置・起動。GUI 自動起動 / 横画面対応）。
- **GUI 一式の導入 (`ensure_pkgs`)**: Xvnc / openbox / 選択ターミナルが揃っていれば**無通信で即起動**（導入済みを毎回 update/再取得しないポリシー）。**未導入のときだけ**不足分を `install_pkgs`（apk add / apt install / pacman -S）で取得し、取れなければ明確に案内して失敗する。app 側 (`TerminalScreen`) のダウンロード確認ゲート (`confirmBeforeDownload`) が同意を取ってから走る。`clean` 指定時のみ cache を消して入れ直す (`clean_pkgs`、破損状態の救済)。
- `GuiSession`/`GuiActivity`/`GuiScreen`/`GuiViewport`/`GuiInputView`/`GuiKeyMapper`/`GuiEventWatcher` + `gui/rfb/RfbClient.kt`(内蔵 RFB クライアント)。端末タブと GUI タブをペアリングし IME 連動。
- **入力**: `GuiInputView` のジェスチャ — **2 本指 = ピンチ(ズーム/パン)**、**3 本指縦移動 = ホイール上/下スクロール**（一度 3 本指になったら全指が離れるまでスクロール扱い）。旧スクロールボタンと `RfbClient.scrollWheel` は撤去。
- **動画**: GPU 無し端末で `gpu` 出力が失敗するため、mpv を **`vo=x11` 既定 + `LIBGL_ALWAYS_SOFTWARE`** でソフト描画させて正常再生。
- **音声 (`service/AudioBridge.kt`)**: **オプトイン**（設定「GUI 音声」`guiAudioEnabled` ON 時のみ）。distro 内 PulseAudio(`-n` 方式で起動) → TCP → Android `AudioTrack` でブリッジ。

### 4.13 Android API ブリッジ (`Z2ApiBridge` / `Z2ApiScript`)

- 端末から Android 機能を叩くコマンド群: `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip (set/get)` / `z2-battery` / `z2-vibrate`。
- `ProotLauncher.ensureZ2ApiScripts` が launch 毎に `/usr/local/bin` へ書き出す。req/resp は `getExternalFilesDir/z2api` を `FileObserver` で監視、引数は base64、atomic rename。

---

## 5. 主要データフロー

### 5.1 入力 → 出力

```
キー/IME/フリック → onBytes(ByteArray)
   → TerminalSession.writeBytes → channel.writer (PTY/SSH)
   → distro shell が処理 → 標準出力
   → readLoop (IO) が channel.reader を読む
   → emulator.processBytes (専用スレッド) → buffer 更新
   → redrawTick/StateFlow 通知 → TerminalRenderer が Canvas 再描画
```

### 5.2 起動シーケンス

```
MainActivity.onCreate → SessionManager.ensureFirst → setContent(TerminalScreen)
TerminalScreen: active が IDLE なら startTerminal()
  → isProotAvailable? → isDistroReady? (未/旧なら DistroInstaller で展開、非同梱は先に DL)
  → ProotLauncher.launch (履歴 rc + sshd ラッパー注入、shared_home/sdcard bind)
  → LocalPtyChannel → RUNNING → readLoop 開始 → initCommand 送出
失敗時: launchAndroidSh にフォールバック
```

---

## 6. 機能仕様

### 6.1 独自キーボード (ASCII)

- レイアウト (5 行): `ESC 1..0 ⌫` / `TAB q..p` / `あ a..l ⏎` / `⇧ z..m,./` / `CTRL ?# ALT SPACE ←↓↑→`。
- **Shift**: OFF → ONESHOT → LOCKED の 3 状態循環。**CTRL/ALT/記号(?#)**: トグル。
- **フリック**: 英字キーの **下フリック = ローマ字大文字**。上/左右 = 記号 (緑ヒント表示、下フリックはヒント無し)。COMPACT は上 + 下、SPACIOUS は 4 方向 + 下。
- **長押し連打**: 数字 / 矢印 / space / 英字キーは押しっぱなしで連打 (初回 400ms→55ms)。⌫ は 500ms→60ms、左右フリックで Ctrl+W / Ctrl+U。修飾キーは連打対象外。
- 「あ」キー → 内蔵 日本語フリックへ切替。TopBar「あ」 → OS IME 切替 (別系統)。
- **英語ロケール (`showJapaneseKeyboard=false`)**: 「あ」キーが無いぶん SPACIOUS では ⇧/CTRL を 1 段下げ、`a` の左端を **META キー** (= Alt と同じ ESC プレフィックス修飾) にして a 行頭の空きをなくす。Row 5 左は CTRL。COMPACT は元々ホーム行に左キーが無いため変更なし。

### 6.2 日本語 フリックキーボード

- 標準 12 キー配列 (5 列 × 4 行、ヒント表示):
  ```
  ESC      あ   か  さ   ⌫
  ◀/▼     た   な  は   ▶/▲
  ␣       ま   や  ら   変換
  ABC      小゛゜ わ  、。  ⏎    ← ABC=英字へ
  ```
  Row 2 の両端は左右キーの真下に上下キーを半行ずつ積み (`JpEdgeStack`、1:1)、◀ ▶ ▼ ▲ を
  全て同じサイズに揃える。◀ の下 (左) に ▼ (下)、▶ の下 (右) に ▲ (上)。打鍵で `flush()` 後に
  カーソル上下を送出。スペース/変換は Row 3 で 1 行のまま (押しやすさ優先)。
- フリック規約: タップ=あ段 / 左=い / 上=う / 右=え / 下=お。
- **濁点キー (小゛゜)**: 直前のかなを 濁点→半濁点→小書き→元 に循環 (循環表はひらがな基準)。かなの連打は循環させず素直に重ねる (「つつ」が「っ」にならない)。
- **⌫**: 左フリック=単語削除 (Ctrl+W) / 右フリック=行頭まで削除 (Ctrl+U)。
- 長音 `ー` は `わ` の右フリック。カタカナは専用キーを廃止し、候補バーのカタカナ候補で選ぶ (§6.2.1)。

#### 6.2.1 かな漢字変換 (`KanaKanjiConverter` / `ComposingState`)

SKK 辞書 (`assets/z2dict.txt` 約16万行) + 常用動詞/形容詞の活用補完を二分探索で引く best-effort 変換。打鍵ごとに候補バー (`CandidateBar`) を更新する。

- **候補生成 (`convertFlexible`)**: 学習履歴(完全一致) → 完全一致(`convert`) → 送り仮名活用(`okuriForms`) → 文節分割合成(`segment`) → 学習履歴(前方一致) → 前方一致予測(`predict`)。生かな・カタカナは常に確定候補として残す。
- **学習履歴** (`ImeHistoryStore`): 確定語を頻度・直近 7 日でランキングし上位に出す。
- **文節分割合成 (`segment`)**: 内容語(最長辞書一致) + 後続の助詞/送り仮名を 1 文節として連結 (例: きょうの → 今日の)。**助詞** (の/は/が…) と**文末助動詞** (でしょう/ました/です…) は単漢字エントリ (野/葉/増田…) を持つため**かなのまま残す** (`PARTICLES` / `AUX_KANA`)。辞書ヒット 1 文節以上 ∧ 漢字を含むときに返す。
- **スプリット変換**: 変換キー (または ◀▶) で先頭文節にフォーカス (`autoSplitHeadLen` = 内容語 + 後続助詞を文節として取り込む)。◀▶ でブロック範囲を伸縮、候補タップ/⏎ で確定すると次ブロックへ自動で進む。変換キー連打で候補をサイクル。
- **長文の自動ブロック分割**: 文が 2 文節以上に分かれる長文は、変換キーを押さなくても自動で先頭文節にスプリットしブロック毎に予測する (`KkcConverter.bunsetsu` で判定)。例: あしたのてんきは… → 明日の / 天気は / …。打ちかけの 1 語では分割しない。**文節境界は正確なラティス最短経路 (`nbest` 1 位) の分割を使う** (0.8.29)。位置 DP の `segments` は単一右文脈しか持たない近似で、接続コスト次第で経路がずれて誤分割していた (例: おねがいします → 尾根が/医師ます と切れて先頭「おねが」でブロック固定し、正解の「お願いします」が候補に出なかった)。`nbest` 1 位ではこれが 1 文節「お願いします」にまとまり自動分割されない＝正しく候補先頭に出る。
- **動的ブロック分割 (学習)** (0.8.71): ブロック境界を辞書コストだけで固定せず、**ユーザーが確定した読みブロックの頻度で学習**する。確定済み `(読み→表層)` は `ImeHistoryStore` に頻度・直近性付きで残っており、`ImeHistoryStore.learnedBlock(読み)` が `(最頻表層, コスト下げ幅)` を返す (`KkcConverter.learnedBlock` に配線)。`KkcConverter.nbest` のラティス構築で、2 文字以上の読みが学習ブロックに一致したらその読み全体のノードコストを下げる (`BLOCK_BASE_BONUS=3000` + `count` 比例 `BLOCK_COUNT_STEP=1500`(上限 count4) + 直近 `BLOCK_RECENT_BONUS=1000`)。これでカタカナ化ペナルティ(4000)+接続コストを 1〜2 回の確定で上回り、**「こまんど」→「こ」「まんど」と誤分割していた頻用読みが、次回以降 1 ブロック「こまんど」=コマンドに自動でまとまる**。辞書に無い読みでも学習表層で合成ノード (`lc=rc=0`) を足すので、任意の「良く使う読みの塊」をブロックとして覚えられる (未学習読みは一切挙動不変＝退行面は確定済み読みに限定)。**コスト割引は「ユーザーが実際に確定した表層」だけに効かせる** (0.8.74)。以前は同じ読みの全表層へ一律にボーナスを掛けていたため、塊 (ブロック) は維持できても辞書最小コストの別表層が勝ち、ユーザーが選んだ漢字が反映されなかった (例: きく→聴く を学習しても 聞く が出続ける)。学習表層へ集中させることで「打ち慣れた変換」が文中でも勝つ。**結合読みを 1 語コスト基準にし、連続確定 run を結合ブロックとして学習する** (0.8.85): (1) 学習ブロックの合成ノードコストを、長さ比例の未知かな (`UNK_COST * 文字数`) ではなく **1 語分の `UNK_COST` 基準** (`(UNK_COST - bonus)`) にした。以前は辞書外の外来語 (`びるど`→`ビルド`。`びるど` は lex に無い) で `UNK_COST×3` になり辞書分割 (`びる`+`ど`) に頻度ボーナスを足しても勝てず、何度使っても 1 ブロックへ繋ぎ止まらなかった。1 語基準なら「使うほど (count↑) 下がる」ボーナスが分割コストを越え、頻用の塊が自動で 1 ブロックにまとまる (`BlockLearningTest` で `びるど` が高頻度で 1 文節「ビルド」へ収束・未学習では分割のまま、を検証)。(2) 自動分割で割れた語は各ブロック単体でしか学習されず「全体読み」が履歴に入らなかったため、`ComposingState` が同一スプリット run 中の連続確定を `committedRun` に貯め、run が尽きた時 (`learnMergedRun`) と一括確定時に **結合読み→結合表層** を `ImeHistoryStore` へ記録する (読み長 2〜`MERGE_MAX_READING_LEN`=6 に限定し長文の丸ごと 1 ブロック化を防ぐ。`text` 編集で run はクリア)。これで「びる」「ど」と割れて確定した塊も次回 (1) のボーナス対象になり 1 ブロックへ繋ぎ止まる。
- **文まるごと一括予測** (`fullPrediction`): スプリット中で後続 (tail) が残るとき、**先頭ブロックの最尤候補 (= `candidates` 先頭) + 残りかなの Viterbi 1-best** を連結した「文まるごと」候補を候補バーに薄緑ピルで 1 つ出す。タップ (`commitFull`) で全文を一括確定。**◀▶ で `splitHeadLen` が動くと `refreshPredict` 経由で再構築され、境界変更に追従して再フローする** (0.8.16)。残りかなの Viterbi では先頭表層を文脈にして bigram リランクを通す。**一括確定の学習はブロック単位** (0.8.74): `fullPrediction` 構成時に文節ブロックの `(読み, 表層)` 内訳 (`fullPredictionBlocks` = 先頭ブロック + `bunsetsu(tail)`) を控え、`commitFull` で各ブロックの `(読み→表層)` と隣接ブロック間 bigram を学習する。以前は文全体を 1 キー (`record(text, full)`) で覚えていたため、その読みが丸ごと再来したときしか効かず、頻用ブロック (今日の / 天気は …) が別の文で再利用されなかった (= 長文で「絶対に使わない組み合わせ」が優先され続ける一因)。内訳が `full` と不整合なときは従来どおり文全体 1 エントリへフォールバック。※旧「読み全体の Viterbi 1-best (境界非依存)」は ◀▶ で薄字が動かないので 0.8.16 で差し替え。旧「文節組み換えバリエーション (`multiSegmentVariants`)」は使われない候補ばかりのため 0.8.4 で廃止。
- **再変換**: 確定直後 (composing 空) に変換キー=「再変換」で直前確定を読みに戻す (`restoreLastCommit`)。
- **キー背景**: 未確定中は ◀▶・変換キーの背景を緑にせず静かに保つ (緑は「再変換」ヒント時の変換キーのみ)。

### 6.3 SSH (端末 → 外部)

- `SshProfilesSheet` で host/port/user/認証 (パスワード or 秘密鍵+パスフレーズ)/initCommand/`-L` 転送を編集。
- 接続時は `SessionManager.openNew` + `startSsh(profile)`。host key は `HostKeyVerificationDialog` で確認 (`KnownHosts` に保存)。

### 6.4 SSH サーバ (PC → 端末) ※dropbear

- **OpenSSH `/usr/sbin/sshd` は proot 不可** (privsep 破綻 + 新 OpenSSH は `UsePrivilegeSeparation` で起動不可)。→ **dropbear** を使用。
- 端末で **`sshd`** = `/usr/local/sbin/sshd` ラッパー (ProotLauncher が毎起動配置、PATH 優先)。`dropbearBootstrapScript` が本体。
  - ポート優先順: `-p` / `-o Port=N` 引数 → `/etc/ssh/sshd_config` の `Port` → 既定 2222。
  - `-f <config>` / `-D`(前景) / `-t`(設定確認) 対応。特権ポート(<1024)は proot で bind 不可を警告。
  - dropbear 未導入なら pacman/apt/apk/dnf/zypper で自動 install。既存 dropbear は pkill→pidof→pidfile→`/proc` 走査で確実停止。
- 設定の「sshd 起動」ボタンも `sshd` を実行。表示の `ssh -p <port>` は sshd_config の Port を反映。

### 6.5 テキスト選択 UX

- 長押しで選択開始 → ドラッグで範囲拡張。**`GestureDetector` は onLongPress 後 onScroll を送らない**ため、`touchMode != NONE` の間は detectors を介さず生 `MOTION_MOVE` で追従。
- ハンドル当たり判定を拡大 (行高×2.2 / 最低 96px、近い端を選択、左端でも掴める)。**末端付近ドラッグで範囲変更**。
- **拡大鏡**: 選択中、端末描画 View を `android.widget.Magnifier` で指の上に表示。
- **端で自動スクロール**: 検知ゾーン 行高×2.5 / 最低 80px。上端→過去 / 下端→最新を 45ms 毎にスクロールしつつ選択を画面外まで伸ばす。
- 選択中「コピー」フローティングボタン、タップで選択解除。

### 6.6 コマンド履歴の永続化

- proot は終了時 SIGKILL で履歴が書かれない → 起動毎に rc/env を注入。bash: `histappend` + `PROMPT_COMMAND='history -a'`、zsh: `INC_APPEND_HISTORY`/`SHARE_HISTORY`。1 コマンド毎に追記し、再起動後も ↑ で履歴を辿れる。

### 6.7 ファイル共有 / 外部ストレージ

- SAF ホーム = `shared_home` (端末の `/root` と一致)。各 distro の rootfs(`/`) も公開。
- proot 内から `cd /sdcard` で Android 共有ストレージ (要 全ファイルアクセス許可)、`/storage/app` はアプリ専用領域 (権限不要)。

### 6.8 その他 UI

- タブ複数化（**長押し→左右ドラッグで並べ替え**、ダブルタップで閉じる）、ピンチでフォント拡縮 (8–32sp)、スクロール + 最新へ戻る ↓、スニペット、テーマ/フォント実プレビュー。
- 設定 (`SettingsSheet`): 0.8.14 で従来の下から重なるボトムシートをやめ、**全画面の「別ページ」**として表示（上部に戻る矢印 ← + システムバック対応）。

---

## 7. 設定項目

| 項目 | キー | 既定 | 範囲/候補 |
|---|---|---|---|
| テーマ | themeName | "ZTS Theme" | 9 種 |
| フォント | fontId | "monospace" | System / IBM Plex / JetBrains / Fira Code |
| フォントサイズ | fontSizeSp | 13 | 8–32 |
| スクロールバック行数 | scrollbackLines | 5000 | 500–50000 |
| ディストロ | distroId | "alpine" | alpine / ubuntu / archlinux / kali |
| 曖昧幅を全角 | ambiguousAsWide | false | true/false |
| 初期コマンド | initCommand | "" | 任意 |
| ログインシェル | loginShell | "/bin/zsh" | /bin/zsh, /bin/bash, /bin/sh |
| キーボードスタイル | keyboardStyleId | "spacious" | compact / spacious |
| キーボードモード | keyboardMode | "custom" | custom / system |
| 横画面キーボード位置 | landscapeKeyboardPosition | "bottom" | left / bottom / right |
| 横画面サイドKB幅 | landscapeKeyboardWidthDp | 420 | 280–700 dp |
| 横画面キーボード高さ | landscapeKeyboardHeightDp | 320 | 200–500 dp |
| 縦画面キーボード高さ | portraitKeyboardHeightDp | 320 | 200–500 dp |
| GUI ターミナル | guiTerminalId | "xterm" | GUI 内で起動するターミナル |
| GUI 音声 | guiAudioEnabled | false | true/false（オプトイン PulseAudio ブリッジ） |
| GUI 拡大率 | guiMagnification | 1.5 | 0.5–3.0 |
| ダウンロード前確認 | confirmBeforeDownload | true | true/false |
| 常駐サービス | keepAliveService | true | true/false（**設定画面ではなくツールバーの 🔒 ロックで ON/OFF**） |
| ツールバー並び順 | toolbarOrder | ""（既定順） | カンマ区切り id。長押しドラッグで更新 |
| 実行エンジン (裏設定) | executionEngine | "proot" | proot / z2root / chroot（chroot は root 解放時のみ） |
| エンジン選択解放 (裏設定) | engineSelectorUnlocked | false | バージョン 7 回タップでトグル（root 不要・解除時は proot へリセット） |
| chroot 解放フラグ (裏設定) | rootChrootUnlocked | false | 7 タップ時の root セルフテスト成功で true |
| 言語 | (専用 SharedPrefs `z2term_locale`) | OS 既定 | ja / en |

`noInstallTimeout`（インストールタイムアウト無効化）・`cleanInstallGuiArmed`（GUI クリーン再展開フラグ）等も DataStore (`z2term_settings`) に保持。SSH プロファイルは別 DataStore (`z2term_ssh`) に JSON で保存。

---

## 8. パーミッション

| 権限 | 用途 |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | distro DL、SSH、パッケージ取得 |
| FOREGROUND_SERVICE(_SPECIAL_USE) | 常駐ターミナル |
| POST_NOTIFICATIONS | 常駐通知 (Android 13+) |
| WAKE_LOCK | バックグラウンド維持 |
| MANAGE_EXTERNAL_STORAGE | `cd /sdcard` で共有ストレージ全体へ R/W (設定から許可導線) |
| READ/WRITE_EXTERNAL_STORAGE (maxSdk) | 旧 API 用 (`requestLegacyExternalStorage`) |

---

## 9. ビルド / 同梱物

```bash
bash scripts/build-bundle.sh          # 同梱物一括生成
# 個別: build-proot.sh / build-alpine-rootfs.sh aarch64 / fetch-fonts.sh
sh scripts/z2root-cmdtest.sh          # z2root の難所を踏む壊れやすいコマンド群を横断テスト(全10グループ・未導入はskip・末尾に非ゼロ一覧。SKIP_NET/SKIP_BUILD/RUN_SSHD/RUN_PRIV)
./gradlew :app:assembleFullDebug      # APK (full = rootfs 同梱)
./gradlew :app:assembleFossDebug      # APK (foss = rootfs 非同梱・起動時 DL)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

- full の同梱: `jniLibs/arm64-v8a/{libproot,libproot_loader,libtalloc}.so`(両フレーバー共通)、`src/full/assets/alpine-minirootfs-aarch64.tgz`(full のみ)、`assets/fonts/*.ttf`(共通)。
- foss は rootfs を含めず、`DistroSpec.ALPINE` の公式 CDN URL + SHA-256 で起動時に取得 (`DistroSpec.bundledInApk` が false)。proot/talloc prebuilt は F-Droid 非適合のため foss から除外し、実行エンジンは同梱ソースからビルドする z2root を使う。
- **assets の rootfs は `.tgz` 拡張子**で置く (`.tar.gz` だと aapt が解凍リネームする)。
- **`useLegacyPackaging=true` 必須** (execve する .so を nativeLibraryDir に実体配置するため)。
- rootfs 構成変更時: `scripts/alpine-packages.txt` 編集 → `DistroBundle.ROOTFS_VERSION` を +1 → `FORCE=1 build-alpine-rootfs.sh` → assemble (利用者は APK 入替で自動再展開)。

---

## 10. 既知の制約と設計上の罠

**PRoot のカーネル特権制約 (修正不能)**: root に見えても `ip`/`nmap -sS`/`ping`/特権ポート bind は不可。代替は `nmap -sT` 等。OpenSSH sshd も privsep 破綻のため dropbear を使う。

**踏みやすい罠 (再発防止)**:
- 端末の `/root` は `distros/<distro>/root` でなく **`filesDir/shared_home`**。SAF/外部ストレージ bind もこれ基準。
- 複数行スクリプトを端末に直接打鍵すると **zsh が `#` コメントを誤実行/継続プロンプトで崩れる** → ファイル化して `sh` 実行。
- dropbear を kill せず再起動すると "Address already in use"。
- `GestureDetector` は **onLongPress 後 onScroll を送らない** → 長押し選択は生 MOTION_MOVE で。
- `ScaleGestureDetector` の **quick scale (1本指ダブルタップ+ドラッグでズーム) が有効**だと、単指 DOWN が内部の double-tap 監視に取り込まれて `GestureDetector.onLongPress` が間欠的に発火しなくなる（2本指ピンチ後にだけ直る症状）。本アプリは 2 本指ピンチのみ使うので `isQuickScaleEnabled = false` で OFF にする (0.8.16)。
- Compose `BasicTextField` で realtime PTY 入力は IME 同期破綻 → `TerminalInputView` + 自前 InputConnection。
- AndroidView の factory で `requestFocus` すると IME が勝手に出る。
- Mozc は `FORCE_ASCII` を無視する (日本語 IME で ASCII 入力は保証されない)。
- SGR run まとめ drawText でカーソルズレ → セル単位 drawText。
- KDoc 内に `*/`(例: `*.tgz`) を書くとコメント早閉じ。
- `setUnixMode` は owner-only 必須 (world-writable だと sudo 拒否)。
- proot launch で固定 `/bin/sh` だと busybox ash が走り zsh 機能が使えない → `resolveShell`。
- **chroot エンジンは su 経由だと制御端末を所有できず Ctrl+C/ジョブ制御が効かない** → login shell を `setsid -c` 経由で起動。
- **GUI 動画**: GPU 無し端末で mpv の `gpu` 出力は化け/半分描画になる → `vo=x11` 既定 + `LIBGL_ALWAYS_SOFTWARE`。
- **GUI 音声**: PulseAudio は `-n` 方式で起動しないと既存設定と競合。`AudioBridge` の接続先 port を 0 のまま渡すと無音（既定ポートを明示）。**z2root 配下では** `--daemonize` が `/proc/self/exe`（=ランチャ）の自己 re-exec で失敗する→`setsid …&` で背景化。AF_UNIX の `SCM_CREDENTIALS` は fake_root の uid=0 だとカーネルが `sendmsg` を `EPERM`→z2root が `sendmsg`/`recvmsg`(211/212) の ucred を実 uid へ書換（0.8.53）。
- **折り返し URL の検出**: wrapped フラグは「継続行」でなく「折り返し元の行」に持たせる（逆だと長 URL がタップできない）。

---

## 11. 用語集

| 用語 | 意味 |
|---|---|
| PRoot | root 無しで chroot/bind/fakeroot を実現するユーザー空間ツール |
| rootfs | Linux ディストロのルートファイルシステム一式 |
| PTY | 擬似端末。アプリ ↔ シェル間の入出力経路 |
| forkpty | PTY を作りつつ fork する libc 関数 |
| SAF | Storage Access Framework。他アプリからファイルを開く Android の仕組み |
| dropbear | 軽量 SSH サーバ/クライアント。proot でも動く |
| SGR | Select Graphic Rendition。文字色/装飾の ANSI 制御 |
| EAW | East Asian Width。全角/半角の文字幅区分 |
| 共有ホーム | `filesDir/shared_home`。全 distro 共通の `/root` 実体 |
```
