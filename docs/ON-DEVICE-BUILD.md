# スマホの z2term で Android アプリをビルドする (PRoot ビルド環境ガイド)

最終更新: 2026-06-01 / 対象 z2term version: 0.8.4-alpha (12) 以降

スマホの z2term（PRoot 内 Arch Linux ARM64）で **任意の Android / Gradle プロジェクト**を
ビルドするための環境構築ガイド。z2term 自身に限らず使い回せる。

> ポイント: **環境構築（§2）は PRoot に対して 1 回やれば全プロジェクト共通**。
> プロジェクトごとに変わるのは「ソースの場所」と「`local.properties`」だけ（§3）。

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

### 全体フロー

```
[1回だけ] z2term の設定 → 「実験的 / 開発者向け」→「Android ホスト bind」ON → 再起動
        ↓
[1回だけ] PRoot 内に JDK / SDK / NDK / build-tools を構築 (§2)
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

### 2-A. ベースのツール

```bash
pacman -Syu --noconfirm
pacman -S --noconfirm jdk17-openjdk cmake git unzip p7zip wget which

# JAVA_HOME を通す (再起動後も効くよう .bashrc にも追記)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
java -version   # openjdk 17 が出れば OK
```

### 2-B. Android SDK ディレクトリの土台

```bash
mkdir -p /root/android-sdk/{ndk,build-tools,platforms,platform-tools,cmake}
```

### 2-C. NDK (termux-ndk r29 ARM64) ※ native コードがある場合のみ必須

公式 NDK は ARM64 ホスト用が無いので termux-ndk を使う。
（URL は `github.com/lzhiyong/termux-ndk/releases` で最新を確認。2026-06 時点は下記）

```bash
cd /root
wget -O ndk.7z "https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.7z"
7z x ndk.7z
# 展開先のディレクトリ名を local.properties の ndk.version と一致させる
mv android-ndk-r29* /root/android-sdk/ndk/29.0.14206865
```

> 重要: `/root/android-sdk/ndk/<名前>` の**ディレクトリ名**を
> 各プロジェクトの `local.properties` の `ndk.version` と**完全一致**させること。

### 2-D. build-tools / platforms (termux-sdk aarch64)

termux-sdk aarch64 には cmake 4.2.1 / ninja / build-tools 35.0.0 / platform-tools 35.0.2 が入る。

```bash
cd /root
wget -O sdk-tools.7z "https://github.com/lzhiyong/termux-ndk/releases/download/android-sdk/android-sdk-aarch64.7z"
7z x sdk-tools.7z
# 必要な版を SDK 配下へ (プロジェクトの compileSdk / buildToolsVersion に合わせる)
cp -r android-sdk/build-tools/35.0.0 /root/android-sdk/build-tools/35.0.0
cp -r android-sdk/platforms/android-35 /root/android-sdk/platforms/android-35
```

platforms（`android-35` などの `android.jar`）が termux-sdk に無い場合は、
PC 側 SDK の `platforms/android-35` をコピーするか、`cmdline-tools` の `sdkmanager`
（公式の platforms は中身がアーキ非依存の jar なので ARM64 でも使える）で入れる。

> プロジェクトによって必要な `compileSdk` / build-tools 版が違う。
> 足りない版が出たら、その版だけ同じ要領で追加配置すればよい。

これらの aapt2 等は `/system/bin/linker64` を要求するので、
**「Android ホスト bind」が ON でセッション再起動済み**であることが前提。確認:

```bash
ls /system/bin/linker64                              # 見えれば bind 成功
/root/android-sdk/build-tools/35.0.0/aapt2 version   # 動けば OK
```

### 2-E. CMake を gradle が見つけられるように ※ CMake を使うプロジェクトのみ

```bash
ln -sf /usr/bin/cmake /root/android-sdk/cmake/3.22.1/bin/cmake 2>/dev/null || true
```

---

## 3. プロジェクトごとの手順（これだけ毎回やる）

```bash
# 1. ソースを PRoot から触れる場所に置く
cp -r /sdcard/.../<your-project> ~/myapp
cd ~/myapp

# 2. local.properties を PRoot 用に書く
cat > local.properties <<'PROPS'
sdk.dir=/root/android-sdk
ndk.version=29.0.14206865
PROPS

# 3. (native ありプロジェクト) CMake/NDK キャッシュをクリア
rm -rf app/.cxx app/build/intermediates/cxx
sh ./gradlew --stop

# 4. ビルド
sh ./gradlew assembleDebug      # フレーバーがあれば assemble<Flavor>Debug
```

成果物: `app/build/outputs/apk/.../*.apk`

> `ndk.version` の行は native コード（CMake/JNI）が無いプロジェクトでは不要。
> その場合 §2-C / §2-E もスキップしてよい。

---

## 4. うまくいかないとき

| 症状 | 原因 / 対処 |
|---|---|
| `aapt2: no such file` / `linker64 not found` | 「Android ホスト bind」OFF か再起動忘れ。ON にして z2term を再起動 |
| `NDK did not have a source.properties` | `ndk.version` の値とディレクトリ名が不一致。`/root/android-sdk/ndk/<名前>` と揃える |
| `Failed to find Build Tools revision X` | その版を §2-D の要領で `/root/android-sdk/build-tools/X` に配置 |
| `CMake ... not found` | §2-E の symlink、または `cmake.dir` を local.properties に追記 |
| x86_64 の aapt2 が呼ばれて落ちる | `/root/android-sdk/build-tools/` に termux 版(ARM64)を上書き配置。Maven 経由の x86_64 版が混ざっていないか確認 |
| daemon が不安定 / OOM | `sh ./gradlew --stop` で daemon を落として再実行。メモリの少ない端末は `org.gradle.jvmargs` を絞る |

---

## 5. 付録: z2term 自身をビルドする場合（具体例）

z2term は native コード（PRoot/talloc, CMake）ありなので §2 を全部やる。

```bash
cp -r /sdcard/.../05_z2term ~/z2term && cd ~/z2term
cat > local.properties <<'PROPS'
sdk.dir=/root/android-sdk
ndk.version=29.0.14206865
PROPS
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
