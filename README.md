# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.6.0-alpha (M6: SSH 強化 + FOSS フレーバー + リンク対応)**

Milestone 6 で SSH のセキュリティ強化、OSC 7/8、起動コマンド、FOSS フレーバーを実装。

### M6 で追加された機能

- **SSH 公開鍵認証** + Android Keystore (AES-256/GCM) で機密フィールドを暗号化
- **known_hosts 永続化**: 初回接続時のフィンガープリント確認ダイアログ、MITM 検知
- **OSC 7 (cwd)**: シェルの current directory を `session.cwd` に反映
- **OSC 8 (hyperlinks)**: リンク領域に下線、タップで `Intent.ACTION_VIEW` 起動
- **起動時 init コマンド**: グローバル + SSH プロファイル別、RUNNING 400ms 後に自動送出
- **FOSS ビルドフレーバー**: F-Droid 適合 (assets / prebuilt なし)、`DistroDownloader` で
  公式 URL から tar.gz を取得 + SHA-256 検証

### M5 以前の機能

- SSH 基礎 / ピンチ / OSC 4/10/11/12/52 / EAW Ambiguous / 配布パイプライン (M5)
- East Asian Width / マルチタブ / IME 連動 / カスタムフォント / WakeLock (M4)
- 代替スクリーン / フォアグラウンドサービス / 物理キーボード / 範囲選択 /
  マルチディストロ (M3)
- VT100/xterm エミュレータ / 6 テーマ / スクロールバック / UTF-8 (M2)
- PRoot + Alpine の PoC (M1)

### M6 でまだ対応していないこと (M7 以降で対応予定)

- ローカルポートフォワーディング (-L) / リバース転送 (-R)
- SFTP ファイル転送
- mosh プロトコル対応 (UDP ベース)
- 端末セッション分離 (each tab = independent emulator + buffer state)
- リバース DNS / IPv6 接続のリトライ強化

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
│   ├── M5-HANDOFF.md
│   └── M6-HANDOFF.md
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

GPL-3.0

## 関連

- [Zero to Ship Project](https://github.com/orgsonai)
- [Termux](https://github.com/termux/termux-app) - 参考実装
- [PRoot](https://proot-me.github.io/) - ユーザランド chroot
- [Alpine Linux](https://alpinelinux.org/) - メインディストロ
