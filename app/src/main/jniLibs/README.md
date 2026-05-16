# jniLibs ディレクトリ

このディレクトリには、各アーキテクチャ向けのネイティブバイナリを配置します。

## 必要なファイル

### proot バイナリ

Termux プロジェクトのプリビルド版を使用します。

| 配置先 | 元バイナリ | ファイル名 |
|---|---|---|
| `arm64-v8a/libproot.so` | proot (aarch64) | リネームして配置 |
| `arm64-v8a/libproot_loader.so` | loader (aarch64) | リネームして配置 |
| `armeabi-v7a/libproot.so` | proot (armv7) | リネームして配置 |
| `armeabi-v7a/libproot_loader.so` | loader (armv7) | リネームして配置 |

### なぜ `lib*.so` にリネームするのか

Android 10 以降、APK 同梱の実行ファイルは原則として実行禁止です。
唯一の例外が **`jniLibs/<abi>/lib*.so`** に配置されたファイルで、これらは
APK インストール時に `nativeLibraryDir` に展開され、実行可能フラグが付きます。

つまり、`proot` のような実行ファイルを APK に同梱して動かすには、
拡張子を `.so` にリネームして jniLibs に置く以外の方法はありません。

これは Termux も含む全ての類似アプリで使われている標準テクニックです。

## proot バイナリ入手方法

### 方法 A: Termux プロジェクトのプリビルド版を使う（推奨）

Termux の `proot` パッケージから抽出します:

```bash
# Termux 端末で実行
pkg install proot
which proot
# /data/data/com.termux/files/usr/bin/proot

# adb 経由で取得
adb pull /data/data/com.termux/files/usr/bin/proot
adb pull /data/data/com.termux/files/usr/libexec/proot/loader
```

### 方法 B: GitHub の termux-packages から取得

```bash
# Termux のビルド済みパッケージリポジトリから
# https://packages.termux.dev/apt/termux-main/pool/main/p/proot/
# proot_5.4.0_aarch64.deb 等をダウンロードして展開
```

### 方法 C: ソースからクロスコンパイル

```bash
git clone https://github.com/proot-me/proot
cd proot
# Android NDK でクロスコンパイル
make -C src loader.elf loader-m32.elf build.h
make -C src proot
```

## 配置手順

```bash
# 例: aarch64 用
cp /path/to/proot       arm64-v8a/libproot.so
cp /path/to/loader      arm64-v8a/libproot_loader.so

# 例: armv7 用
cp /path/to/proot.armv7 armeabi-v7a/libproot.so
cp /path/to/loader.armv7 armeabi-v7a/libproot_loader.so
```

## サイズの目安

- proot: 約 1-2MB（圧縮済み）
- loader: 約 50KB

両アーキテクチャ合わせて APK サイズに約 4-5MB 追加されます。

## ライセンス

proot は **GPL-2.0** ライセンスです。
Z2Term 全体は **GPL-3.0** で配布するため、ライセンス互換性に問題ありません。

ライセンス表記をアプリ内の「About」画面に必ず含めてください（M7 で実装予定）。
