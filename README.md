# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.2.0-alpha (M2: 実用ターミナル)**

Milestone 2 で「実用ターミナル」相当の機能を実装。

### M2 で動くこと

- VT100 / xterm 互換のエスケープシーケンス処理 (色・装飾・カーソル制御・スクロール領域・SGR 256色 / RGB)
- Compose Canvas による独自レンダラ (`TerminalRenderer`) ・属性連続セルを 1 描画にまとめる最適化
- 6 種類の同梱テーマ: ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night
- 動的端末サイズ (画面サイズ + フォントメトリクスから rows/cols を逆算)
- 5,000 行 (設定で 500〜50,000 まで可変) のスクロールバック
- 縦ドラッグでスクロールバック閲覧 + 右端インジケータ + 最下部へ戻る FAB
- 特殊キーバー強化: Ctrl 系 (^A/^C/^D/^E/^K/^L/^R/^U/^W/^Z)、Home/End、PgUp/PgDn、F1〜F12
- 設定画面 (ModalBottomSheet): テーマ・フォントサイズ・スクロールバック行数を DataStore で永続化
- 全文コピー / クリップボードペースト
- UTF-8 マルチバイト入出力 (日本語などをセル単位で正しく表示)

### M2 でまだ対応していないこと (M3 以降で対応予定)

- 代替スクリーン (vim/htop 終了時に画面が消えない問題)
- マルチタブ / セッション切替
- バックグラウンド維持 (foreground service)
- マルチディストロ (Ubuntu / Arch / Kali)
- 範囲選択モード (タップ&ドラッグでテキスト選択)
- 物理キーボード入力 (Ctrl + 任意キーの組み合わせ)
- IBM Plex Mono / Outfit などのカスタムフォント同梱

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
│       │   ├── pty/                 ← PTY 抽象化
│       │   ├── proot/               ← PRoot 起動
│       │   ├── distro/              ← rootfs 展開
│       │   ├── emulator/            ← VT100/xterm エミュレータコア (M2)
│       │   ├── settings/            ← DataStore 永続化 (M2)
│       │   ├── service/             ← (M3) バックグラウンド維持
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme
│       │       ├── settings/        ← 設定 UI (M2)
│       │       └── terminal/        ← ターミナル UI + Renderer
│       ├── jniLibs/                 ← proot バイナリを配置
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
└── docs/
    ├── M1-HANDOFF.md
    └── M2-HANDOFF.md
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
