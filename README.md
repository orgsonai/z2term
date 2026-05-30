# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.8.3-alpha (versionCode 11) — M11/M12: スクロールバック検索 / セッション復元 / Android API ブリッジ / root chroot 裏機能 / GUI 音声・動画 / URL 下線 / 三本指スクロール**

Milestone 7〜12 で SFTP、GUI (Xvnc+VNC)、複数 GUI タブ、IME 学習、英語 UI、横画面キーボード、スクロールバック検索、セッション復元、root 端末向け chroot エンジン、GUI 音声/動画再生などを実装。

### M11〜M12 で追加された機能

- **スクロールバック検索** (`SearchEngine.kt`): 🔍 → 文字入力 → ↑↓ で前後ジャンプ、CJK セル列でハイライト位置を計算
- **セッション復元** (`SessionStore.kt`): OS kill 後の再起動でタブ構成 + cwd を復元（cwd は OSC7 で捕捉）
- **Android API ブリッジ**: 端末から `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip` / `z2-battery` / `z2-vibrate`
- **root chroot 裏機能** (full フレーバー・要 root): バージョン 7 回タップで「実行エンジン (proot / chroot)」を解放。実 root 端末で検証済み
- **GUI 動画/音声**: mpv をソフト描画で正常再生、PulseAudio→TCP→AudioTrack の **GUI 音声ブリッジ**（オプトイン）
- **URL/OSC8 リンクに下線表示**、折り返し長 URL の検出修正
- **三本指スクロール**: 画面内スクロールボタンを廃止し三本指ドラッグに統一
- **日本語 IME 強化**: ⌫ 左右フリックで単語/全削除、フリック表記反転、活用辞書、かな連打循環の廃止
- **OSC タイトル UTF-8 デコード**でタブ名の日本語文字化けを解消

### M10 で追加された機能

- **Konsole on Arch 起動修正** (4 段階フォールバック → ローカル cache のみで再構成)
- **横画面のキーボード位置** を 左 / 下 / 右 から選択可、**幅・高さもスライダー**で可変
- **押下時に背景強調 + フリック方向ヒント拡大** で「どこを押した / どこに飛ばす」が見える
- **通常 🖥 起動の完全オフライン化**: クリーンインストール以外でネットを叩かない
- **アプリ内 言語スイッチ** (日本語 / English) + 多数の UI 翻訳
- **インストールタイムアウト無効化** トグル
- **IME 学習履歴** で予測変換が頻度・直近 7 日でランキング

### M7〜M9 の主な追加

- **SFTP ファイル転送** (M7)
- **GUI (Xvnc + 内蔵 RFB クライアント) + Linux デスクトップ起動** (M8)
- **GUI を複数タブで並走** + IME 連動 + 端末タブと GUI タブをペアリング (M8-4〜6)
- **アプリ内テーマエディタ** + **OSC 4/10/11/12** 反映 (M9)
- **送り仮名活用** + **柔軟漢字変換** (M9)

### M6 以前の機能

- **SSH 公開鍵認証** + Android Keystore (AES-256/GCM) で機密フィールドを暗号化 (M6)
- **known_hosts 永続化**: 初回接続時のフィンガープリント確認ダイアログ、MITM 検知 (M6)
- **OSC 7 (cwd)** / **OSC 8 (hyperlinks)** (M6)
- **FOSS ビルドフレーバー**: F-Droid 適合、`DistroDownloader` で SHA-256 検証 (M6)

- SSH 基礎 / ピンチ / OSC 4/10/11/12/52 / EAW Ambiguous / 配布パイプライン (M5)
- East Asian Width / マルチタブ / IME 連動 / カスタムフォント / WakeLock (M4)
- 代替スクリーン / フォアグラウンドサービス / 物理キーボード / 範囲選択 /
  マルチディストロ (M3)
- VT100/xterm エミュレータ / 6 テーマ / スクロールバック / UTF-8 (M2)
- PRoot + Alpine の PoC (M1)

### 未対応 / 今後の検討

- ローカルポートフォワーディング (-L) / リバース転送 (-R)
- mosh プロトコル対応 (UDP ベース)
- リバース DNS / IPv6 接続のリトライ強化
- F-Droid 公開 (FOSS フレーバー整備済、メタデータ準備中)
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
# Debug APK
./gradlew assembleDebug

# 出力: app/build/outputs/apk/debug/app-debug.apk
```

### 3. インストール

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
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
│       │   ├── service/             ← TerminalService / AudioBridge (foreground + WakeLock)
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
│   ├── DESIGN-SPEC.md             ← 設計書 兼 仕様書（技術文書）
│   ├── HANDBOOK.md                ← 利用者向けハンドブック
│   ├── RELEASE.md                 ← リリース手順
│   ├── SSH-INTO-Z2TERM.md
│   ├── GUI-REWRITE-HANDOFF.md
│   └── M1-HANDOFF.md 〜 M12-HANDOFF.md  ← マイルストーン引き継ぎ
├── metadata/                     ← F-Droid メタデータ
└── .github/workflows/build.yml   ← CI (full + foss 両ビルド)
```

## ビルドバリアント

| Flavor | 用途 | 同梱内容 |
|---|---|---|
| `full` | 内部/Play Store 配布 | assets と prebuilt バイナリを含む (各自配置が必要) |
| `foss` | F-Droid 配布 | prebuilt 一切なし。`DistroDownloader` でランタイム取得 |

```bash
./gradlew assembleFullDebug   # 通常開発
./gradlew assembleFossDebug   # F-Droid 適合確認
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

## ライセンス

本アプリ本体 (`app/src/main/java/com/zerotoship/z2term/**`) のライセンスは **GPL-3.0** です。
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
| **F-Droid** | `foss` (prebuilt 除外) | 適合化進行中。`src/full/` への物理移動 + F-Droid metadata 提出が必要 (詳細は `z2term-patch/方針書.md` §法的対応) |
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
