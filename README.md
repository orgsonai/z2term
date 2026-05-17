# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.3.0-alpha (M3: 常駐ターミナル化)**

Milestone 3 でバックグラウンド維持・代替スクリーン・マルチディストロを実装。

### M3 で追加された機能

- 代替スクリーン (DECSET 1049/1047/47) ・vim/htop 終了時に通常画面が復帰
- フォアグラウンドサービスによる **バックグラウンド維持** ・Activity 破棄でも PTY が生き続ける
- 永続通知に「停止」アクションを設置 ・タップで MainActivity 復帰、停止でセッション終了
- 物理キーボード対応 (Bluetooth キーボード等) ・Ctrl/Alt 修飾と F-key・矢印を含む全マッピング
- 長押し → ドラッグの **範囲選択モード**、底部に「キャンセル/コピー」アクションバー
- **マルチディストロ**: Alpine + Ubuntu を切替可能 (設定 → ディストロ)
- TerminalSession を ViewModel から独立させた SessionManager シングルトン構造

### M2 以前から引き続き動くこと

- VT100/xterm エスケープシーケンス (色・装飾・スクロール領域・SGR 256色/RGB)
- Compose Canvas 独自レンダラ + 属性連続セル一括描画
- 6 テーマ (ZTS/Solarized/Dracula/Gruvbox/Nord/Tokyo Night)
- 動的端末サイズ、スクロールバック (500〜50,000 行)、最下部 FAB
- 特殊キーバー (Ctrl 系 / Home/End / PgUp/PgDn / F1〜F12)
- 設定永続化 (DataStore Preferences)
- 全文コピー / クリップボードペースト / UTF-8 ストリーミングデコード

### M3 でまだ対応していないこと (M4 以降で対応予定)

- マルチタブ / 複数同時セッション (現状はシングルセッション)
- East Asian Width 対応 (全角文字を 2 セル幅扱い)
- IME 連動 (現状の入力欄はサジェスト等で挙動が違うことがある)
- IBM Plex Mono / Outfit などのカスタムフォント同梱
- WakeLock / ネットワーク維持の細かな制御

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
│       │   ├── core/                ← TerminalSession + SessionManager (M3)
│       │   ├── pty/                 ← PTY 抽象化
│       │   ├── proot/               ← PRoot 起動
│       │   ├── distro/              ← rootfs 展開 (Alpine + Ubuntu, M3)
│       │   ├── emulator/            ← VT100/xterm エミュレータコア (M2)
│       │   ├── settings/            ← DataStore 永続化 (M2-3)
│       │   ├── service/             ← TerminalService (foreground, M3)
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme
│       │       ├── settings/        ← 設定 UI (M2)
│       │       └── terminal/        ← ターミナル UI + Renderer + キーマッパー
│       ├── jniLibs/                 ← proot バイナリを配置
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
└── docs/
    ├── M1-HANDOFF.md
    ├── M2-HANDOFF.md
    └── M3-HANDOFF.md
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

GPL-3.0

## 関連

- [Zero to Ship Project](https://github.com/orgsonai)
- [Termux](https://github.com/termux/termux-app) - 参考実装
- [PRoot](https://proot-me.github.io/) - ユーザランド chroot
- [Alpine Linux](https://alpinelinux.org/) - メインディストロ
