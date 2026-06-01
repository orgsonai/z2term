# スマホの z2term で Android アプリをビルドする (PRoot ビルド環境ガイド)

最終更新: 2026-06-02 / 対象 z2term version: 0.8.4-alpha (12) 以降
状態: **実機検証済み**（moto g66j / 非 root / proot で z2term 自身を `assembleFullDebug` →
`BUILD SUCCESSFUL` を確認）

スマホの z2term（PRoot 内 Arch Linux ARM64）で **任意の Android / Gradle プロジェクト**を
ビルドするための環境構築ガイド。z2term 自身に限らず使い回せる。

> ポイント: **環境構築（§2）は PRoot に対して 1 回やれば全プロジェクト共通**。
> プロジェクトごとに変わるのは「ソースの場所」と「`local.properties`」だけ（§3）。

> 貼り付けの注意: 設定ファイルを作るとき **heredoc（`<<'PROPS' … PROPS`）は使わない**。
> SSH 等でインデント付き貼り付けをすると終端行が認識されず固まる。
> 本ガイドは全部 **`printf` で 1 行書き込み**にしてある。

---

## 0. なぜ手順が要るのか（仕組み）

Android アプリのビルドには `aapt2` / `zipalign` などの **build-tools** と **NDK**（native
コードがある場合）が要る。

- Google 公式の build-tools / NDK は **x86_64 Linux ホスト用バイナリ**しか無い。
  スマホの PRoot は **ARM64** なので公式バイナリは動かない。
- ARM64 版は `lzhiyong/termux-ndk` / `termux-sdk` が配布している。ただしこれらは
  **Android のリンカ `/system/bin/linker64`** を要求する ARM aarch64 ELF。
- z2term の PRoot 内には既定で `/system` が見えないので、そのままでは動かない。

→ **z2term 0.8.4-alpha(12) の「Android ホスト bind」トグル**を ON にすると
  `/system` `/apex` が PRoot/chroot 内へ bind され、Android リンカが見えるようになり、
  termux 系 build-tools が PRoot 内で動く。**これが端末内ビルドの肝**で、
  どのプロジェクトをビルドする場合でも共通の前提。

> 補足: 実行時に `WARNING: linker: ... /linkerconfig/ld.config.txt` が出ることがあるが、
> これは無害（フォールバックして正常動作する）。

### 全体フロー

```
[1回だけ] z2term の設定 → 「実験的 / 開発者向け」→「Android ホスト bind」ON → 再起動
        ↓
[1回だけ] PRoot 内に JDK / SDK / NDK / build-tools を構築 + aapt2 の罠対策 (§2)
        ↓
[プロジェクトごと] ソースを置く + local.properties を書く + ./gradlew (§3)
```

---

## 1. ビルド環境の併用（PC と スマホで同じソースを使う）

`build.gradle.kts` に NDK 版や SDK パスを直書きしない。**`local.properties`
（環境ごと・git 管理外）で切り替える**。これで同じソースツリーを PC でも スマホでも使える。

| 環境 | `sdk.dir` | `ndk.version` |
|---|---|---|
| PC (x86_64) | `/opt/android-sdk` | 書かない（AGP 既定 NDK を使う） |
| スマホ z2term (PRoot ARM64) | `/root/android-sdk` | `29.0.14206865`（termux-ndk r29） |

`local.properties` は `.gitignore` 対象なのでコミットされず、両環境で衝突しない。
build.gradle 側で `local.properties` の `ndk.version` を読む書き方の例（z2term の `app/build.gradle.kts` 参照）:

```kotlin
val localProps = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}
android {
    localProps.getProperty("ndk.version")?.takeIf { it.isNotBlank() }?.let { ndkVersion = it }
}
```

---

## 2. PRoot 内 Arch Linux ARM64 の環境構築（1 回だけ・全プロジェクト共通）

z2term の PRoot シェル（Arch Linux ARM64）に入ってから実行する。

### 2-0. 前提: 「Android ホスト bind」を ON にして bind を確認

z2term 設定 → 「実験的 / 開発者向け」→「Android ホスト bind (/system, /apex)」ON →
**z2term を再起動**。proot シェルで確認:

```bash
ls /system/bin/linker64       # ファイルが見えれば bind 成功 (見えなければ ON / 再起動を確認)
```

### 2-A. ベースのツール

```bash
pacman -Syu --noconfirm
pacman -S --noconfirm jdk17-openjdk cmake git unzip p7zip wget which

# JAVA_HOME を通す (使っているシェルの rc に追記。zsh なら ~/.zshrc, bash なら ~/.bashrc)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.zshrc
java -version   # openjdk 17 が出れば OK
```

### 2-B. Android SDK ディレクトリの土台

```bash
mkdir -p /root/android-sdk/{ndk,build-tools,platforms,platform-tools,cmake}
```

### 2-C. NDK (termux-ndk r29 ARM64) ※ native コードがある場合のみ必須

公式 NDK は ARM64 ホスト用が無いので termux-ndk を使う（拡張子は **.7z**）。
（URL は `github.com/lzhiyong/termux-ndk/releases` で最新を確認。2026-06 時点は下記）

```bash
cd /root
wget -c -O ndk.7z "https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.7z"
7z x ndk.7z
ls -d android-ndk*    # 展開フォルダ名を確認 (例: android-ndk-r29)
# ディレクトリ名を local.properties の ndk.version と一致させて配置
mv android-ndk-r29* /root/android-sdk/ndk/29.0.14206865
```

> **7z の「Dangerous link path was ignored」警告に注意。** 7z は上位階層へ辿る
> シンボリックリンクを安全のため**スキップ**する。NDK では `clang++ → clang` が
> スキップされる（これは C++ ビルドに必須）ので、手動で作り直す:

```bash
cd /root/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin
ln -sf clang clang++
./clang --version    # "aarch64-unknown-linux-musl" の clang 21 が出れば OK
```

> （`simpleperf`(x86_64) / `lldb` 関連のスキップはビルドに不要なので無視してよい）
> 重要: `/root/android-sdk/ndk/<名前>` の**ディレクトリ名**を
> 各プロジェクトの `local.properties` の `ndk.version` と**完全一致**させること。

### 2-D. build-tools / platforms / cmake (termux-sdk aarch64)

termux-sdk aarch64 には **build-tools 34/35/36・platforms android-34/35/36・cmake 3.22.1・
cmdline-tools・platform-tools** が一式入っている。**`/root` で展開すると `/root/android-sdk`
にそのまま合流する**（§2-B で作った土台＋§2-C の NDK と同じ場所にマージされる）。

```bash
cd /root
wget -c -O sdk-tools.7z "https://github.com/lzhiyong/termux-ndk/releases/download/android-sdk/android-sdk-aarch64.7z"
7z x sdk-tools.7z      # 上書き確認が出たら A (Always) を選ぶ
# 確認
ls /root/android-sdk/build-tools     # 34.0.0 35.0.0 36.0.0
ls /root/android-sdk/platforms       # android-34 android-35 android-36
```

> プロジェクトの `compileSdk` に合う platform / build-tools がここに含まれていれば追加作業不要。
> 足りない版だけ後から個別配置すればよい。

### 2-E. ★ aapt2 の罠対策（**最重要・これが無いと必ず失敗する**）

落とし穴が 2 つある。両方つぶす。

**(1) 選択された build-tools の aapt2 が壊れている/無い場合がある**
AGP はインストール済みの**最も新しい** build-tools（例: 36.0.0）を選ぶ。termux-sdk の
36.0.0 は `aapt2` が**ダミーのシンボリックリンク**になっていることがあり「corrupted」と
判定される。確実に動く 35.0.0 の実バイナリで差し替える:

```bash
rm -f /root/android-sdk/build-tools/36.0.0/aapt2
cp /root/android-sdk/build-tools/35.0.0/aapt2 /root/android-sdk/build-tools/36.0.0/aapt2
chmod +x /root/android-sdk/build-tools/36.0.0/aapt2
/root/android-sdk/build-tools/36.0.0/aapt2 version   # "aapt2 2.19-..." が出れば OK
```

**(2) AGP は既定で Maven から x86_64 版 aapt2 を落として使う**
そのままだと `AAPT2 aapt2-x.x.x-linux Daemon startup failed`（x86_64 が動かない）で落ちる。
**`android.aapt2FromMavenOverride`** で ARM64 版に強制する。`~/.gradle/gradle.properties`
に置けば**全プロジェクト共通**で効く（git 管理外）:

```bash
mkdir -p ~/.gradle
printf 'android.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2\norg.gradle.jvmargs=-Xmx3g\n' > ~/.gradle/gradle.properties
cat ~/.gradle/gradle.properties
```

> 出力が 2 行（override と jvmargs）になっていること。
> この設定を変えたら `sh ./gradlew --stop` で daemon を再起動してからビルドする
> （gradle.properties は起動時にしか読まれない）。

### 2-F. CMake の symlink ※ 通常は不要

termux-sdk に cmake 3.22.1 が含まれるので普通は不要。CMake が見つからないと言われたら:

```bash
ln -sf /usr/bin/cmake /root/android-sdk/cmake/3.22.1/bin/cmake 2>/dev/null || true
# または local.properties に cmake.dir=/root/android-sdk/cmake/3.22.1 を追記
```

---

## 3. プロジェクトごとの手順（これだけ毎回やる）

```bash
# 1. ソースを PRoot から触れる場所に置く (git clone でも /sdcard からコピーでも可)
cd /root && git clone https://github.com/<you>/<your-project>.git myapp && cd myapp
# (private で clone できない時は cp -r /sdcard/.../<project> ~/myapp)

# 2. local.properties を PRoot 用に書く (heredoc を使わず printf で)
printf 'sdk.dir=/root/android-sdk\nndk.version=29.0.14206865\n' > local.properties
cat local.properties

# 3. (native ありプロジェクト) CMake/NDK キャッシュをクリア
rm -rf app/.cxx app/build/intermediates/cxx
sh ./gradlew --stop

# 4. ビルド
sh ./gradlew assembleDebug      # フレーバーがあれば assemble<Flavor>Debug
```

成果物: `app/build/outputs/apk/.../*.apk`

> `ndk.version` の行は native コード（CMake/JNI）が無いプロジェクトでは不要。
> その場合 §2-C / §2-F もスキップしてよい（§2-E の aapt2 対策は native の有無に関係なく必須）。

---

## 4. うまくいかないとき

| 症状 | 原因 / 対処 |
|---|---|
| `AAPT2 aapt2-x.x.x-linux Daemon startup failed` | Maven の x86_64 aapt2 が使われている。§2-E (2) の `aapt2FromMavenOverride` が未設定/未反映。`~/.gradle/gradle.properties` を確認し `--stop` 後に再ビルド |
| `Installed Build Tools revision X is corrupted` / `missing AAPT2` | 選択された build-tools の `aapt2` がダミーリンク。§2-E (1) で実バイナリに差し替え |
| `aapt2: no such file` / `linker64 not found` | 「Android ホスト bind」OFF か再起動忘れ。ON にして z2term を再起動（§2-0） |
| 7z 展開時 `Dangerous link path was ignored` | 7z が symlink をスキップしただけ。NDK の `clang++ → clang` だけ手動再作成（§2-C） |
| heredoc で `heredoc>` のまま固まる | インデント付き貼り付けで終端行が認識されない。Ctrl+C で抜けて `printf` 版を使う |
| `NDK did not have a source.properties` | `ndk.version` の値とディレクトリ名が不一致。`/root/android-sdk/ndk/<名前>` と揃える |
| `CMake ... not found` | §2-F の symlink、または `cmake.dir` を local.properties に追記 |
| daemon が不安定 / OOM | `sh ./gradlew --stop` で daemon を落として再実行。メモリの少ない端末は `~/.gradle/gradle.properties` の `org.gradle.jvmargs` を `-Xmx2g` 等に絞る |

---

## 5. 付録: z2term 自身をビルドする場合（検証済みの具体例）

z2term は native コード（PRoot/talloc, CMake）ありなので §2 を全部やる。

```bash
cd /root && git clone https://github.com/orgsonai/z2term.git && cd z2term
# (既にコピーがあるなら) cd ~/tmp/app_project/05_z2term && git pull origin main
printf 'sdk.dir=/root/android-sdk\nndk.version=29.0.14206865\n' > local.properties
rm -rf app/.cxx app/build/intermediates/cxx
sh ./gradlew --stop
sh ./gradlew assembleFullDebug
# → app/build/outputs/apk/full/debug/app-full-debug.apk
```

- 「Android ホスト bind」トグルの実装: `ProotLauncher.kt`（proot `-b /system -b /apex` /
  chroot `mount --bind`）、`AppSettings.kt`、`SettingsSheet.kt`
- foss フレーバーは jniLibs/assets の物理移動が前提（`app/build.gradle.kts` のコメント参照）
- PC ビルドの通常手順: `docs/RELEASE.md`

---

## 6. 安全上の注意

⚠ 「Android ホスト bind」は Android のシステムディレクトリ（`/system` `/apex`）を端末内に
晒す。**ビルド用途で使い、終わったら OFF に戻す**運用が無難（OFF + セッション再起動で
bind は外れる）。
