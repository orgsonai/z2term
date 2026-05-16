# Z2Term — Zero 2 Terminal

**Z2Term** (ズィートゥーターム) は Android 端末上で動作する独自実装のターミナルアプリです。
複数の Linux ディストリビューション (Alpine / Ubuntu / Arch / Kali) を PRoot 経由で動作させ、
`pacman` / `apt` などのパッケージマネージャ経由で任意のコマンドをインストール可能にします。

> Zero to Ship プロジェクトの第5作目です。
> ブログ: https://zero-to-ship-app.vercel.app

## 現在のバージョン

**0.1.0-alpha (M1: PoC)**

このバージョンは Milestone 1 のPoC実装です。

### M1 で動くこと

- Android 標準シェル (`/system/bin/sh`) の起動と入出力
- assets に Alpine rootfs を配置すれば、PRoot 経由で Alpine の起動
- 基本的なターミナル表示（生テキスト）
- 特殊キー入力 (ESC, TAB, Ctrl-C, Ctrl-D, Ctrl-L, 矢印キー)
- ZTS Theme (ダーク + ZTS グリーン #22c55e)

### M1 で動かないこと（M2 以降で対応）

- ANSI エスケープシーケンスの解釈（色付き表示など）
- マルチタブ
- バックグラウンド維持
- マルチディストロ
- `apt` / `pacman` で外部パッケージインストール（PRoot + Alpine 環境下なら `apk add` は動く可能性あり）

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
z2term-m1/
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
│       │   ├── service/             ← (M3) バックグラウンド維持
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme
│       │       └── terminal/        ← ターミナル UI
│       ├── jniLibs/                 ← proot バイナリを配置
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
└── docs/
    └── M1-HANDOFF.md
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
