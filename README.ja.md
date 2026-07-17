# Z2Term — Zero 2 Terminal

[English](README.md) ・ **日本語**

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## スクリーンショット

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/cui-terminal.png" width="280" alt="CUI: Alpine 端末と独自キーボード"><br><sub>CUI — Alpine 端末 + 独自キーボード</sub></td>
    <td align="center" width="50%"><img src="docs/images/gui-thunderbird.png" width="280" alt="GUI: Xvnc 上で動く Thunderbird"><br><sub>GUI — Xvnc 上で動く Thunderbird</sub></td>
  </tr>
</table>

## ダウンロード

**最新の APK は GitHub Releases から直接ダウンロードできます**（ビルド不要）:

- 最新版に飛ぶ: **<https://github.com/orgsonai/z2term/releases/latest>**

Android 端末で APK をタップ → 「提供元不明のアプリ」のインストールを許可するとインストールできます。
(`full` フレーバー・prebuilt 同梱で APK 単体で動作完結。Google Play では配布していません)

## 現在のバージョン

**0.8.159-alpha (versionCode 167).** 最新の APK と全リリース履歴は **[GitHub Releases](https://github.com/orgsonai/z2term/releases)** にあります。

## 機能

- **ターミナルエミュレータ** — VT100 / xterm、256色・トゥルーカラー、6 テーマ、検索付きスクロールバック、UTF-8 と East Asian Width、代替スクリーン、OSC 4 / 7 / 8 / 10 / 11 / 12 / 52。
- **root 不要の Linux ディストロ** — PRoot で Alpine / Ubuntu / Arch / Kali を動かし、`apk` / `apt` / `pacman` で何でも導入。
- **実行エンジン** — PRoot（既定）、z2root（root 不要の ptrace ベースエンジン）、chroot（root 端末向け）。バージョンを 7 回タップするとエンジン選択がアンロックされる。
- **マルチタブ** — CUI / GUI タブ、ドラッグで並べ替え、OS にプロセスを落とされてもセッション復元。
- **Linux GUI** — Xvnc + openbox と内蔵 RFB クライアント。Thunderbird や mpv などのデスクトップアプリを音声・動画つきで動かせる。
- **SSH / SFTP** — 公開鍵認証（秘匿フィールドは Android Keystore で暗号化）、known_hosts 確認、ファイル転送、既定で localhost のみ bind する内蔵 `sshd`（dropbear）。
- **日本語 IME** — Viterbi かな漢字変換、予測、頻度/新しさ学習、独自オンスクリーンキーボード。
- **Android ブリッジ** — 端末から本体機能を呼ぶ: `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip` / `z2-battery` / `z2-vibrate` / `z2-say` / `z2-torch` / `z2-media` / `z2-volume` / `z2-sensor` / `z2-intent`。
- **セルフ adb** — `z2adb` で端末自身のワイヤレスデバッグへ localhost 接続。PC・USB・root すべて不要。
- **内蔵ヘルプ** — `z2help`（または `z2term`）で全 `z2*` ヘルパーの分類済み早見表を表示。`z2version` でアプリ版数とタブが実際に動いているエンジンを確認。
- **脆弱性試験** — `z2scan self` が自端末/localhost を自己診断（公開ポート・sshd 設定・SSH 鍵の権限・world-writable/SUID・PATH）。外部ツール不要。`z2scan net/host/cve` は localhost に nmap/lynis/trivy をかける薄いラッパー（外部対象は明示許可制）。結果はローカルに留まります。
- **FOSS フレーバー** — 第三者 prebuilt を一切同梱せず、初回起動時にディストロを DL して SHA-256 で検証。

### 未対応 / 今後の検討

- ローカルポートフォワーディング (-L) / リバース転送 (-R)
- mosh プロトコル対応 (UDP ベース)
- リバース DNS / IPv6 接続のリトライ強化
- proot 自前実装でネイティブ外部表記を完全に無くす (FOSS-PURE フェーズ2)
- IME 学習履歴のリセット UI / バックアップ

## ビルド要件

| 項目 | バージョン |
|---|---|
| Android Studio | Ladybug 2024.3.1 以上 |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 (AGP 内蔵) |
| Gradle | 9.3.1 |
| NDK | 27.0+ |
| CMake | 3.22.1+ |
| 最小 SDK | 29 (Android 10) |
| ターゲット SDK | 35 (Android 15) |

## セットアップ

### 1. 依存バイナリを配置

ビルド前に以下を手動配置する必要があります（リポジトリには含まれていません）:

**Alpine rootfs** → `app/src/main/assets/`
- `alpine-minirootfs-aarch64.tar.gz`
- `alpine-minirootfs-armv7.tar.gz`

詳細: [app/src/main/assets/README.md](app/src/main/assets/README.md)

**PRoot バイナリ** → `app/src/main/jniLibs/`
- `arm64-v8a/libproot.so` (および `libproot_loader.so`)
- `armeabi-v7a/libproot.so` (および `libproot_loader.so`)

詳細: [app/src/main/jniLibs/README.md](app/src/main/jniLibs/README.md)

### 2. ビルド

```bash
./gradlew assembleFullRelease
# 出力: app/build/outputs/apk/full/release/app-full-release.apk
```

(fork 側で署名鍵が無くても OK — `build.gradle.kts` は `keystore.properties` 不在時 debug 鍵にフォールバックします)

### 3. インストール

```bash
adb install -r app/build/outputs/apk/full/release/app-full-release.apk
```

## プロジェクト構造

```
z2term/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                  ← Alpine rootfs を配置
│       ├── cpp/                     ← JNI ネイティブコード
│       │   ├── CMakeLists.txt
│       │   └── pty_jni.cpp
│       ├── java/com/zerotoship/z2term/
│       │   ├── Z2TermApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── channel/             ← ProcessChannel / SshChannel (M5)
│       │   ├── core/                ← TerminalSession + SessionManager
│       │   ├── pty/                 ← PTY 抽象化
│       │   ├── proot/               ← PRoot 起動
│       │   ├── distro/              ← rootfs 展開 (Alpine + Ubuntu)
│       │   ├── emulator/            ← VT100/xterm エミュレータコア
│       │   ├── settings/            ← DataStore 永続化
│       │   ├── service/             ← TerminalService / AudioBridge (foreground + Wake/WifiLock)
│       │   ├── gui/                  ← GUI (Xvnc + 内蔵 RFB クライアント / GuiSession)
│       │   ├── saf/                  ← SAF DocumentsProvider
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme + カスタムフォント
│       │       ├── settings/        ← 設定 UI
│       │       ├── ssh/             ← SSH プロファイル UI (M5)
│       │       └── terminal/        ← ターミナル UI + Renderer + キーマッパー
│       ├── jniLibs/                 ← proot バイナリを配置
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── ja/                        ← 日本語ドキュメント
│   │   ├── DESIGN-SPEC.md         ← 設計書 兼 仕様書（技術文書）
│   │   └── HANDBOOK.md            ← 利用者向けハンドブック
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   └── HANDBOOK.md            ← getting started handbook
│   ├── images/                    ← スクリーンショット等（共通）
│   ├── RELEASE.md                 ← リリース手順
│   └── SSH-INTO-Z2TERM.md
├── metadata/                     ← F-Droid メタデータ
└── .github/workflows/build.yml   ← CI (full + foss 両ビルド)
```

## ビルドバリアント

| Flavor | 用途 | 同梱内容 |
|---|---|---|
| `full` | 内部/Play Store 配布 | assets と prebuilt バイナリを含む (各自配置が必要)。初回オフライン起動可 |
| `foss` | ライセンス表記の最小化 | **Alpine rootfs を除外** → 起動時に `DistroDownloader` で DL (SHA-256 検証)。proot/talloc は同梱継続 (W^X で `nativeLibraryDir` からの execve が必須) のため GPL-2.0/LGPL-3.0 表記は残る。初回オフライン起動は不可 |

```bash
./gradlew assembleFullDebug   # 通常開発
./gradlew assembleFossDebug   # ライセンス最小化フレーバー (rootfs 除外・起動時 DL)
```

## 動作確認の流れ

1. proot バイナリも Alpine rootfs もない状態でビルド・インストール
   → Android `/system/bin/sh` フォールバックで動作するはず
   → `ls /system/bin` などを試して動作確認

2. Alpine rootfs を assets に配置してビルド・インストール
   → 「PRoot バイナリが見つかりません」警告でフォールバックするはず

3. proot バイナリも jniLibs に配置してビルド・インストール
   → Alpine Linux が起動するはず
   → `apk update && apk add zsh` を試す

### z2root コマンド群テスト（`scripts/z2root-cmdtest.sh`）

「今後も *壊れやすいコマンド* がエラーなく動く」ことを確認する回帰スモーク。
z2root の難所（ptrace/seccomp・fakeroot 偽装・パス変換・/proc 偽装・pty・大量
fork/exec・ld.so reloc）を踏むコマンドに絞り、cd/ls のような自明系は入れない。
狙いは「systemic な退行を *多数のコマンドが一斉に落ちる* 形で一発検知し、コマンド
ごとの後追い修正をやらないで済む」こと。z2root タブのゲスト内でそのまま実行:

```sh
sh scripts/z2root-cmdtest.sh              # 標準（ネット/ビルド込み）
SKIP_NET=1   sh scripts/z2root-cmdtest.sh # ネット/パッケージ系をスキップ
SKIP_BUILD=1 sh scripts/z2root-cmdtest.sh # cc コンパイル等をスキップ
RUN_SSHD=1   sh scripts/z2root-cmdtest.sh # dropbear ループバック ssh（z2root 単独だとセッションが落ちる可能性）
RUN_PRIV=1   sh scripts/z2root-cmdtest.sh # losetup/mount など真に root が要る操作も実行（非 root では EPERM が正常）
```

POSIX sh／busybox ash 互換で、**未導入コマンドは fail でなく skip** するので
どのディストリでも同じに走る＝各ゲストで回して「非ゼロ終了一覧が空」になれば
OS 差なく健全、と読める。10 グループ: ①ランタイム実起動（claude headless と
`--version` の対比・node spawn・python venv/mp/ssl・ripgrep）②VCS 重い操作
（clone/gc/checkout＝hardlink/pack/rename）③パッケージ管理（apt/apk/dnf/pacman・
pip/venv・npm）④pty/端末（script/tmux/stty・`/dev/pts`・任意で dropbear）
⑤/proc・fakeroot 境界 ⑥ビルド（cc execve chain＋ld.so reloc）⑦パス変換/symlink
canonicalize ⑧ディスク/FS（dd・mkfs・parted をファイル相手に。root 系は
`RUN_PRIV`）⑨IPC/特殊 syscall（AF_UNIX・FIFO・flock・inotify・xattr・
copy_file_range・nested ptrace(strace/gdb)・Go 生 syscall・sqlite3・rsync）
⑩名前解決/TLS（getent・curl TLS・nslookup）。出力は画面と
`/tmp/z2root-cmdtest-<時刻>.log`、末尾に非ゼロ終了一覧。proot タブで同じものを
流せば対照ログが取れる。

注: `io_uring`（ptrace/seccomp を丸ごとバイパス）や `statx`/`openat2` のフック漏れ
はコマンドテストでは捕まらない＝seccomp フィルタ側で確認すること。

## ライセンス

本アプリ本体 (`app/src/main/java/com/zerotoship/z2term/**`) のライセンスは **GPL-3.0** です。
Copyright (c) 2026 Zero to Ship。対応ソース（GPL v3 §6）: <https://github.com/orgsonai/z2term>（ルートの `LICENSE` に全文）。
同梱バイナリ・rootfs・フォント等のライセンスは下記「同梱 OSS と対応ソース」を参照。

## 同梱 OSS と対応ソース（GPL/LGPL 頒布要件）

`full` フレーバーの APK には以下の prebuilt が含まれます。各成果物の**対応ソース**は
下記 URL から取得可能（GPL v2 §3 / GPL v3 §6 / LGPL v3 §4 への対応）。

| 同梱物 | ライセンス | 対応ソース取得方法 |
|---|---|---|
| `libproot.so` / `libproot_loader.so` | GPL-2.0 | [termux/proot](https://github.com/termux/proot) / `scripts/build-proot.sh` が DL する Termux パッケージのバージョン参照 |
| `libtalloc.so` | LGPL-3.0 | [Samba talloc](https://gitlab.com/samba-team/samba/-/tree/master/lib/talloc) / 同上 |
| `alpine-minirootfs-*.tgz` 内の各パッケージ | 個別 (GPL-2.0 / GPL-3.0 / MIT / BSD 他) | [Alpine aports](https://gitlab.alpinelinux.org/alpine/aports) — `scripts/alpine-packages.txt` の各パッケージ名で参照 |
| Fira Code / IBM Plex Mono / JetBrains Mono | OFL-1.1 | [tonsky/FiraCode](https://github.com/tonsky/FiraCode) / [IBM/plex](https://github.com/IBM/plex) / [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |

設定画面 →「OSS ライセンス / 対応ソース」から、上記情報をアプリ内でも一覧/全文表示できます
（`assets/licenses/` にライセンス全文を配置）。

### 対応ソースの取得手順 (例)

```sh
# PRoot 同等のソース取得 (Termux パッケージ)
git clone https://github.com/termux/proot.git

# talloc 同等のソース取得
git clone https://gitlab.com/samba-team/samba.git
ls samba/lib/talloc

# Alpine rootfs に入っている bash 等のソース
curl -O https://gitlab.alpinelinux.org/alpine/aports/-/archive/master/aports-master.tar.gz
```

z2term 自身のビルド時にどのバージョンが取得されるかは `scripts/build-proot.sh` /
`scripts/build-alpine-rootfs.sh` の `PROOT_VER_AARCH64` / `ALPINE_VERSION` を参照。

## 配布方針

| チャネル | フレーバー | 状況 |
|---|---|---|
| **GitHub Releases / 直接 APK 配布** | `full` (prebuilt 同梱) | 主たる配布経路。APK 単体で動作完結 |
| **F-Droid** | `foss` (rootfs 除外) | **非対象** (実行時 DL を許容)。`foss` は外部ライセンス表記の最小化が目的で F-Droid 向けではない。proot/talloc は同梱継続のため再現性ビルド適合は対象外 |
| **Google Play** | — | proot による外部コード実行が DPA §4.4 に抵触する可能性が高く、**配布予定なし** |

## SSH サーバ (sshd) の既定挙動

端末内 `sshd` コマンドは既定で **127.0.0.1 限定 bind + 鍵認証のみ** で起動します
（dropbear wrapper、`SshdScript.kt`）。LAN/WAN 公開する場合は明示的に:

```sh
sshd --lan          # 全 NIC bind、~/.ssh/authorized_keys が空だと起動拒否
Z2_SSHD_LAN=1 sshd  # env でも可
```

## 関連

- [Zero to Ship Project](https://github.com/orgsonai)
- [Termux](https://github.com/termux/termux-app) - 参考実装
- [PRoot](https://proot-me.github.io/) - ユーザランド chroot
- [Alpine Linux](https://alpinelinux.org/) - メインディストロ
