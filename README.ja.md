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
- 0.8.18-alpha 直リンク: [z2term-0.8.18-alpha.apk](https://github.com/orgsonai/z2term/releases/download/v0.8.18-alpha/z2term-0.8.18-alpha.apk)

Android 端末で APK をタップ → 「提供元不明のアプリ」のインストールを許可するとインストールできます。
(`full` フレーバー・prebuilt 同梱で APK 単体で動作完結。Google Play では配布していません)

## 現在のバージョン

**0.8.18-alpha (versionCode 26) — `foss` フレーバーから Alpine rootfs を外す（実行時 DL・SHA-256 検証）。外部ライセンス表記の最小化が目的**

Milestone 7〜12 で SFTP、GUI (Xvnc+VNC)、複数 GUI タブ、IME 学習、英語 UI、横画面キーボード、スクロールバック検索、セッション復元、root 端末向け chroot エンジン、GUI 音声/動画再生などを実装。0.8.4 で日本語かな漢字変換を長文向けに強化。0.8.5 以降で UI/キーボードの細かな改善と各種修正を継続。

### 0.8.5〜0.8.18 で追加・変更された機能

- **`foss` フレーバーから Alpine rootfs を外す** (0.8.18): 外部ライセンス表記を最小化するため、`foss` APK に約 49MB の Alpine rootfs を同梱しないようにした。初回起動時に Alpine 公式 CDN から DL（SHA-256 検証）するため、**`foss` ビルドは初回オフライン起動ができない**。`proot`/`talloc` は `nativeLibraryDir` からの実行が必須（W^X 制約）で外せないため引き続き同梱し、OSS ライセンス画面では両フレーバーで表示するようにした。`full` フレーバーは従来どおり（rootfs 同梱・オフライン即起動）で変更なし
- **GUI 一式の未導入時に自動取得** (0.8.17): GUI ターミナル（や Xvnc/openbox）が未導入のとき 🖥 起動が毎回失敗していたのを修正。導入済みなら従来どおり無通信で即起動、未導入のときだけ不足分を取得する（ダウンロード確認 ON なら同意ダイアログ後に取得）
- **英語キーボードの左端を META キーに** (0.8.17): English UI の独自キーボード（4 方向フリック）で `a` の左に空いていた隙間を META キー（= Alt 相当の ESC プレフィックス修飾）で埋めた。日本語キーボード・シンプル配列は変更なし
- **長押し選択の信頼性向上** (0.8.16): `ScaleGestureDetector` の quick scale (1本指ダブルタップ+ドラッグでズーム) を OFF。これが有効だと長押しが間欠的に発火しない症状（ピンチ後に直る）が出ていた。2本指ピンチは引き続き有効
- **一括予測がブロック境界に追従** (0.8.16): スプリット変換中に ◀ ▶ で先頭ブロックの境界を動かすと、候補バーの薄緑「文まるごと」ピル（一括予測）も「先頭ブロック最尤 + 残りかな最尤」に組み直されて再フローする
- **日本語キーボードのフォント統一** (0.8.15): 「シンプル」「4 方向フリック」どちらを選んでも、かな文字のサイズが 4 方向フリック基準でそろうように（キーボード高さに応じた拡縮は維持）
- **タブのドラッグ並べ替え** (0.8.14): タブを長押し→左右ドラッグで CUI/GUI タブを並べ替え。1 操作で端から端まで移動可。タブを閉じるのはダブルタップ（停止処理は裏で行い即座に消える）
- **設定の全画面ページ化** (0.8.14): 従来の下から重なるボトムシートをやめ、戻る矢印 + システムバック対応の「別ページ」に
- **起動時の `cd` 自動注入を廃止** (0.8.13): セッション復元時にユーザーの意図しない作業ディレクトリ移動を起こさないよう、復元タブもシェル既定の cwd で起動
- **ツールバーのスニペットを📜タブ化** (0.8.12): スニペットと **SSH 接続 / SFTP** を 1 つのシートにタブ統合、設定からは SSH プロファイル項目を削除
- **SSH ed25519 公開鍵認証の修正** (0.8.11): Android で `Auth fail publickey` になる問題を BouncyCastle 追加で解消
- **独自キーボードの主キー拡大 + スタイル名整理** (0.8.7〜0.8.10): qwerty/数字キーのフォント拡大、特殊キー表記 `C-C`→`^C`、スタイル名を「シンプル / 4 方向フリック」に
- **CI 修正** (0.8.6): `gradle.properties` の絶対 `java.home` 撤去 + lint エラー解消
- **キーボード/候補バー改善 + IME 強化** (0.8.5): 候補バー 2 行化、設定スクロール修正、N-best 複数候補・常用語追加

### 0.8.4 (M13) で追加された機能

- **日本語・長文の自動ブロック分割**: 長い文を打つと変換キーを押さなくても先頭のかたまりから予測。ブロック毎に確定して次へ自動で進む
- **文まるごと一括予測**: 各ブロックを変換して連結した「一文まるごと」候補を候補バーに薄緑ピルで提示、タップで一括確定（使われない文節組み換え候補は廃止）
- **外部ストレージ(SDカード)認識**（オプトイン）: `/storage/XXXX-XXXX` を proot/chroot へ bind
- **Android ホスト bind（実験的）**: `/system` `/apex` を bind し端末内ビルド等の活路に
- 未確定中の **◀▶・変換キーの背景強調を停止**（静かな表示）

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
│   ├── ja/                        ← 日本語ドキュメント
│   │   ├── DESIGN-SPEC.md         ← 設計書 兼 仕様書（技術文書）
│   │   └── HANDBOOK.md            ← 利用者向けハンドブック
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   └── HANDBOOK.md            ← getting started handbook
│   ├── images/                    ← スクリーンショット等（共通）
│   ├── RELEASE.md                 ← リリース手順
│   ├── SSH-INTO-Z2TERM.md
│   ├── GUI-REWRITE-HANDOFF.md
│   └── M1-HANDOFF.md 〜 M13-HANDOFF.md  ← マイルストーン引き継ぎ
├── metadata/                     ← F-Droid メタデータ
└── .github/workflows/build.yml   ← CI (full + foss 両ビルド)
```

## ビルドバリアント

| Flavor | 用途 | 同梱内容 |
|---|---|---|
| `full` | 内部/Play Store 配布 | assets と prebuilt バイナリを含む (各自配置が必要)。rootfs は `src/full/assets` に置く |
| `foss` | F-Droid / サイドロード | **rootfs なし**（`DistroDownloader` で実行時 DL・SHA-256 検証）。`proot`/`talloc` は W^X 制約で `nativeLibraryDir` から実行する必要があり引き続き同梱 |

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
Copyright (c) 2026 Zero to Ship。対応ソース（GPL v3 §6）: <https://github.com/orgsonai/z2term>（ルートの `LICENSE` に全文）。
同梱バイナリ・rootfs・フォント等のライセンスは下記「同梱 OSS と対応ソース」を参照。

## 同梱 OSS と対応ソース（GPL/LGPL 頒布要件）

APK には以下の prebuilt が含まれます。各成果物の**対応ソース**は
下記 URL から取得可能（GPL v2 §3 / GPL v3 §6 / LGPL v3 §4 への対応）。
「`foss` 同梱」列は `foss` フレーバーにも含まれるかを示す（rootfs は `full` 専用で、`foss` は実行時 DL）。
アプリ内の OSS ライセンス画面はフレーバーに応じてこの一覧をフィルタする。

| 同梱物 | ライセンス | `foss` 同梱 | 対応ソース取得方法 |
|---|---|---|---|
| `libproot.so` / `libproot_loader.so` | GPL-2.0 | あり | [termux/proot](https://github.com/termux/proot) / `scripts/build-proot.sh` が DL する Termux パッケージのバージョン参照 |
| `libtalloc.so` | LGPL-3.0 | あり | [Samba talloc](https://gitlab.com/samba-team/samba/-/tree/master/lib/talloc) / 同上 |
| `alpine-minirootfs-*.tgz` 内の各パッケージ | 個別 (GPL-2.0 / GPL-3.0 / MIT / BSD 他) | なし (実行時 DL) | [Alpine aports](https://gitlab.alpinelinux.org/alpine/aports) — `scripts/alpine-packages.txt` の各パッケージ名で参照 |
| Fira Code / IBM Plex Mono / JetBrains Mono | OFL-1.1 | あり | [tonsky/FiraCode](https://github.com/tonsky/FiraCode) / [IBM/plex](https://github.com/IBM/plex) / [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |

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
| **F-Droid** | `foss` (rootfs 除外) | rootfs を `foss` から除外済み（`src/full/` へ移動・実行時 DL 化）。完全適合は未了: `proot`/`talloc` が prebuilt バイナリのまま残る（FOSS 引き継ぎのフェーズ2 = proot 自前実装が必要）＋ F-Droid metadata 提出 |
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
