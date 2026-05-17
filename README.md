# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.5.0-alpha (M5: SSH + ジェスチャ + 配布準備)**

Milestone 5 で SSH クライアント・ピンチ操作・OSC 拡張・配布パイプラインを実装。

### M5 で追加された機能

- **SSH クライアント** (JSch): プロファイル保存、パスワード認証、xterm-256color 接続
- **ピンチでフォントサイズ変更** + 二本指ドラッグでスクロールバック閲覧
- **OSC 拡張**: 4 (palette set) / 10/11/12 (default fg/bg/cursor) / 52 (clipboard)
- **EAW Ambiguous 切替**: 罫線・矢印を CJK ロケール向けに wide 扱いするトグル
- **配布パイプライン**: GitHub Actions CI + Release signing + ProGuard + F-Droid metadata

### M4 以前の機能

- East Asian Width / マルチタブ / IME 連動 / カスタムフォント / WakeLock (M4)
- 代替スクリーン / フォアグラウンドサービス / 物理キーボード / 範囲選択 /
  マルチディストロ (Alpine + Ubuntu) (M3)
- VT100/xterm エミュレータ / 6 テーマ / スクロールバック /
  クリップボード / UTF-8 (M2)
- PRoot + Alpine の PoC (M1)

### M5 でまだ対応していないこと (M6 以降で対応予定)

- SSH 公開鍵認証 + Android Keystore でのパスワード暗号化
- known_hosts 永続化 + ホスト鍵検証
- 起動時自動コマンド (init スクリプト)
- F-Droid 用 FOSS ビルドバリアント (assets 抜き)
- OSC 7 (current directory) / OSC 8 (hyperlinks)
- ローカルポートフォワーディング / リバース SSH

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
│       │   ├── service/             ← TerminalService (foreground + WakeLock)
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
│   ├── M1-HANDOFF.md
│   ├── M2-HANDOFF.md
│   ├── M3-HANDOFF.md
│   ├── M4-HANDOFF.md
│   └── M5-HANDOFF.md
├── metadata/                     ← F-Droid メタデータ (M5)
└── .github/workflows/build.yml   ← CI (M5)
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
