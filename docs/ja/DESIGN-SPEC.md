# Z2Term 設計書 兼 仕様書

最終更新: 2026-06-06 / 対象バージョン: 0.8.37-alpha (versionCode 45)

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
- **実行エンジン**: 既定は PRoot。裏設定（バージョン 7 タップ）で **非 root の自前 ptrace エンジン「z2root」**（実験的）に切替可。さらに root 端末では root セルフテスト成功時に **「実 chroot」エンジン**も選択可（`su` 経由 bind mount + `chroot`。`executionEngine`）。

対応 ABI は **arm64-v8a のみ**。最低 Android 10 (API 29)、ターゲット API 35。

### 配布フレーバー

| フレーバー | applicationId | 用途 |
|---|---|---|
| `full` | `com.zerotoship.z2term` | 通常配布 (rootfs/proot 同梱・初回オフライン起動可) |
| `foss` | `com.zerotoship.z2term.foss` | 外部ライセンス表記の最小化。Alpine rootfs を APK から外し起動時 DL (proot/talloc は同梱継続・初回オフライン起動不可) |

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
| SSH クライアント | JSch (mwiede fork) | 0.2.21 (BouncyCastle 不要) |
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
- **GUI デスクトップ**は別 Activity (`GuiActivity`) として起動し、distro 内 Xvnc に内蔵 RFB クライアントで接続する（[§4.12](#412-gui-デスクトップ-gui)）。実行エンジンは PRoot 既定、裏設定で z2root（非 root・実験的）、root 端末ではさらに chroot に切替可（[§4.3](#43-proot-実行-prootprootlauncherkt-prootsshdscriptkt)）。

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
- `resolveShell`: 指定シェルが rootfs に無ければ `defaultShell → /bin/sh` にフォールバック (usrmerge 考慮)。
- `isDistroReady`: `bin/busybox|bin/bash` 等の実体 + `.z2term-version` マーカー (同梱 distro のみ `ROOTFS_VERSION` 比較)。
- 起動毎に冪等で注入: `ensureShellHistoryConfig` (履歴 rc)、`ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear ラッパー)、`ensureOsc7CwdConfig` (cwd 復元用 OSC7 フック)、`ensureZ2ApiScripts` (`z2-*` ブリッジ)、GUI/z2run スクリプト。
- `launchAndroidSh`: proot 不可時のフォールバック (`/system/bin/sh` + 最小 mkshrc)。

**実行エンジン z2root (裏機能・非 root・実験的)**: `executionEngine = "z2root"` のとき、`launch()` がバイナリを `nativeLibraryDir/libz2root.so`（自前 ptrace エンジン）に差し替える。proot 互換 argv subset を受けるので引数・env はそのまま流用（`PROOT_*`/talloc は z2root が無視）。`libz2root.so` 未同梱（`scripts/build-z2root.sh` 未実行）の場合は proot へフォールバック。パス変換は proot 相当に強化済み（パス内 symlink の canonicalize / `/proc/<tid>/cwd` による cwd 相対パス絶対化 / `dirfd` 相対は非変換 / `renameat2`・`linkat`・`symlinkat` の2パス変換 / `utimensat` のパス変換 / execve ローダ差し替え・`#!` シバン解決 / 非 ELF・存在しない PATH 候補は loader を噛ませず素の execve でカーネルに `ENOENT`/`ENOEXEC` を返させる passthrough）。実機 Ubuntu 24.04 で `apt install hello` が end-to-end 成功（`Unpacking`→`Setting up`→`Hello, world!` 実行）まで確認済み (0.8.30)。0.8.32 で **seccomp-bpf による高速化**を導入＝従来は `PTRACE_SYSCALL` で全 syscall を 2 回トラップしていたのを、パス変換・fakeroot 偽装・getcwd 逆変換・/proc 偽装に必要な syscall だけ `SECCOMP_RET_TRACE` で捕捉し残りはネイティブ実行にした（proot と同方式）。実機ベンチで fork/exec 約2.3倍・read 約3倍・実 IO は proot の約2倍以内、FS 走査は proot より高速。0.8.34 で **read 非トレース化**を導入し 0.8.35 で**既定 ON**化＝seccomp 化後も `/proc/<pid>/status`・`loginuid` の偽装のために `read`/`close` を捕捉し続けるコストが残り、小 read 連打（`dd bs=1` 等）が proot 比約9倍だった。read-free では偽装を `openat` の瞬間に行う＝偽装済み内容を rootfs 内の使い捨て temp に書き出し `openat` のパスをそこへ差し替える（直後に unlink＝open-then-unlink）。以後の read は通常ファイルへの読み取りなので `read`/`close` を seccomp 対象から外せる（ネイティブ速）。実機検証（run-as）で `dd bs=1 ×300000` が約 8.1s→約 0.28s（proot 約 0.32s をわずかに上回る）、status/loginuid 偽装は維持・temp 残骸なしを確認。`Z2ROOT_NO_READFREE=1` で旧 read トレース経路へフォールバック可。0.8.36 で **glibc distro（Arch/Ubuntu）の対話シェルが起動しない不具合**を修正＝z2root + Arch で画面が真っ黒・プロンプト無し（固まって見える）になっていた。原因は、新しい glibc(2.42+) の `tcgetattr` が `ioctl(TCGETS2)` を使うが Android はアプリの pty への TCGETS2 を拒否（`EACCES`）するため `isatty()` が失敗し、bash/zsh が「端末でない」と判断して非対話起動（`PS1` 無し）になっていたこと（musl の Alpine は旧 `TCGETS` で無事、proot は ioctl を書き換えるので無事）。修正＝z2root が `ioctl` をトレースし、`TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` を entry で legacy（`TCGETS`/`TCSETS`/…）へ書き換える（先頭の `struct termios` 部分は termios2 と同レイアウトで通常 baud では実害なし）。実機検証＝Arch + z2root で対話 `[…]$` プロンプトに到達しコマンド実行を確認、Alpine(musl) は回帰なし。0.8.37 で **bind マウント配下のバイナリ直接実行**を修正＝ホーム（`-b <home>:/root` の `/root`）でコンパイルした実行ファイルを `./a.out` で動かせなかった（`error while loading shared libraries: … cannot open shared object file`、静的は `z2root loader: open(…): No such file or directory`）。原因＝動的 ELF のとき rootfs 内 `ld.so` に渡すプログラムパスを**ホスト実パス**にしていたが、`ld.so` 自身の `open()` も tracee として翻訳されるため、bind 配下のホストパスが「ゲストパス扱い」され rootfs を前置されて ENOENT になっていた（rootfs 配下のバイナリは host パスがそのまま rootfs 配下で二重変換抑止に当たり偶然動いていた）。修正＝`ld.so` には `host_to_guest` で逆変換した**ゲストパス**を渡す（`#!` シバン経路と同じ思想）ことで rootfs/bind の両方で正しく開ける。実機検証＝`cd /root && gcc -O2 hello.c -o hello && ./hello` が `sum(1..100)=5050` を出力、rootfs 内バイナリは回帰なし。`pacman -U` でのオフライン gcc 導入（run-as は SELinux `runas_app` ドメインで `sendmsg` が遮断されネット不可のため）と gcc 16.1.1 での実コンパイルも確認。静的バイナリは自前ローダ下で依然 segfault（別件・既知の制限）。フェーズ2（FOSS の外部表記ゼロ化）の実体で、詳細は `docs/FOSS-PURE-HANDOFF.md` §5。

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
  - 文字幅: East Asian Width 対応 (`ambiguousAsWide` 設定で曖昧幅を 2 セル化)。サロゲートペア対応。
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
- `SessionStore`/`SessionManager` (M11): タブ構成 `{id,label,distro,cwd}` + activeId を DataStore に保存し、OS kill 後の再起動でタブ構成 (順序含む) を復元（GUI タブは対象外）。各タブは新規 PTY で起動する。**cwd は OSC7 で捕捉**（`ensureOsc7CwdConfig` が bash/zsh のプロンプトフックで OSC7 を吐かせる）が、**起動時の `cd <cwd>` 自動注入は 0.8.13 で廃止**（ユーザーの意図しない移動を避けるため、復元タブもシェル既定の cwd で起動する）。「通知の停止」では空保存して復元しない。

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
- **文まるごと一括予測** (`fullPrediction`): スプリット中で後続 (tail) が残るとき、**先頭ブロックの最尤候補 (= `candidates` 先頭) + 残りかなの Viterbi 1-best** を連結した「文まるごと」候補を候補バーに薄緑ピルで 1 つ出す。タップ (`commitFull`) で全文を一括確定。**◀▶ で `splitHeadLen` が動くと `refreshPredict` 経由で再構築され、境界変更に追従して再フローする** (0.8.16)。残りかなの Viterbi では先頭表層を文脈にして bigram リランクを通す。※旧「読み全体の Viterbi 1-best (境界非依存)」は ◀▶ で薄字が動かないので 0.8.16 で差し替え。旧「文節組み換えバリエーション (`multiSegmentVariants`)」は使われない候補ばかりのため 0.8.4 で廃止。
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
./gradlew :app:assembleFullDebug      # APK (full = rootfs 同梱)
./gradlew :app:assembleFossDebug      # APK (foss = rootfs 非同梱・起動時 DL)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

- full の同梱: `jniLibs/arm64-v8a/{libproot,libproot_loader,libtalloc}.so`(両フレーバー共通)、`src/full/assets/alpine-minirootfs-aarch64.tgz`(full のみ)、`assets/fonts/*.ttf`(共通)。
- foss は rootfs を含めず、`DistroSpec.ALPINE` の公式 CDN URL + SHA-256 で起動時に取得 (`DistroSpec.bundledInApk` が false)。proot/talloc は W^X 制約で同梱必須。
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
- **GUI 音声**: PulseAudio は `-n` 方式で起動しないと既存設定と競合。`AudioBridge` の接続先 port を 0 のまま渡すと無音（既定ポートを明示）。
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
