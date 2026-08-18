# jniLibs ディレクトリ

このディレクトリには、ソースから生成する z2root の native 成果物を配置します。

## なぜ `lib*.so` にリネームするのか

Android 10 以降、APK 同梱の実行ファイルは原則として実行禁止です。
唯一の例外が **`jniLibs/<abi>/lib*.so`** に配置されたファイルで、これらは
APK インストール時に `nativeLibraryDir` に展開され、実行可能フラグが付きます。

つまり、z2root のような実行ファイルを APK に同梱して動かすには、
拡張子を `.so` にリネームして jniLibs に置く以外の方法はありません。
Termux も含む全ての類似アプリで使われている標準テクニックです。

## 配置先

| 配置先 | 元バイナリ |
|---|---|
| `arm64-v8a/libz2root.so` | z2root (aarch64) |
| `arm64-v8a/libz2accept.so` | accept→accept4 シム |

armv7 (32bit) は M7 同梱方針では生成しません。`build.gradle.kts` の
`abiFilters` も `arm64-v8a` のみ。

## z2root

ソースは `app/src/main/cpp/z2root/z2root.c`。実行エンジンはこれだけです。

```bash
bash scripts/build-z2root.sh
# 出力:
#   arm64-v8a/libz2root.so   (自前 ptrace エンジン)
#   arm64-v8a/libz2accept.so (accept→accept4 LD_PRELOAD シム)
```

NDK パスはスクリプトが自己解決する (環境変数 `ANDROID_NDK_HOME`/`NDK`/… か
`local.properties` の `sdk.dir`+`ndk.version`、`$ANDROID_HOME` 配下の ndk を探索)。

`lib*.so` 名でjniLibsへ置き、`nativeLibraryDir`からexecveします。
`.so` 自体はビルド成果物のためgit管理外です。

⚠️ **手動実行は通常不要**: `build.gradle.kts` の Gradle タスク `buildZ2rootNative`
が全フレーバーのjniLibsマージ前に上記スクリプトを自動実行するため、
`./gradlew assemble*` だけで常にソースと一致した `.so` が再生成されAPKに
同梱される (git 管理外の `.so` が古いまま同梱される "stale .so" 事故を構造的に防止)。
