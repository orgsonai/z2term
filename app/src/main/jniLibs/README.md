# jniLibs ディレクトリ

このディレクトリには PRoot バイナリを **`lib*.so` 命名規約** で配置します。

## ⚠️ 自動取得: `scripts/build-proot.sh` を使ってください

M7 同梱方針:

```bash
bash scripts/build-proot.sh         # Termux 公式パッケージから取得 (arm64 のみ)
# 出力:
#   arm64-v8a/libproot.so        (Termux proot バイナリ)
#   arm64-v8a/libproot_loader.so (proot 用 loader)
```

スクリプトは `https://packages.termux.dev/apt/termux-main/pool/main/p/proot/` から
最新の `proot_<version>_aarch64.deb` を取得し、内部の `usr/bin/proot` と
`usr/libexec/proot/loader` を `libproot.so` / `libproot_loader.so` にリネームして
配置します。

クロスビルド方式 (proot-me/proot からの make build) は talloc 依存で
NDK ビルドが通らないため採用していません。

## なぜ `lib*.so` にリネームするのか

Android 10 以降、APK 同梱の実行ファイルは原則として実行禁止です。
唯一の例外が **`jniLibs/<abi>/lib*.so`** に配置されたファイルで、これらは
APK インストール時に `nativeLibraryDir` に展開され、実行可能フラグが付きます。

つまり、`proot` のような実行ファイルを APK に同梱して動かすには、
拡張子を `.so` にリネームして jniLibs に置く以外の方法はありません。
Termux も含む全ての類似アプリで使われている標準テクニックです。

## 配置先

| 配置先 | 元バイナリ |
|---|---|
| `arm64-v8a/libproot.so`        | proot (aarch64) |
| `arm64-v8a/libproot_loader.so` | loader (aarch64) |

armv7 (32bit) は M7 同梱方針では生成しません。`build.gradle.kts` の
`abiFilters` も `arm64-v8a` のみ。

## サイズ目安

- libproot.so:        約 210KB
- libproot_loader.so: 約 18KB

## z2root (FOSS フェーズ2・自前 proot 互換エンジン)

外部ライセンス表記の完全ゼロ化に向け、proot/talloc を自前コード(GPL-3.0)で
置き換える取り組み (`docs/FOSS-PURE-HANDOFF.md` §5)。ソースは
`app/src/main/cpp/z2root/z2root.c`。

```bash
ANDROID_NDK_HOME=/path/to/ndk bash scripts/build-z2root.sh
# 出力:
#   arm64-v8a/libz2root.so   (自前 ptrace エンジン。proot 互換 argv subset)
```

proot と同じ「`lib*.so` 名で jniLibs に置いて `nativeLibraryDir` から execve」
方式。`.so` 自体はビルド成果物のため git 管理外 (上記 proot 同様)。`libz2root.so`
は現状アプリ未配線で、`build.gradle.kts` の CMake にも未登録 (= APK には未同梱)。
