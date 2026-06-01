# 端末内ビルド (z2term の PRoot で z2term 自身をビルドする) 手順

最終更新: 2026-06-01 / 対象 version: 0.8.4-alpha (12) 以降

スマホの z2term（PRoot 内 Arch Linux ARM64）で Android アプリ（z2term 自身を含む）を
ビルドするための環境構築コマンド集。PC（x86_64）でのビルドと**同じソースツリーを併用**する
前提で書いている。

---

## 0. 全体像（なぜ手順が要るのか）

Android アプリのビルドには `aapt2` / `zipalign` などの **build-tools** と **NDK** が要る。

- Google 公式の build-tools / NDK は **x86_64 Linux ホスト用バイナリ**しか無い。
  スマホの PRoot は **ARM64** なので公式バイナリは動かない。
- ARM64 版は `lzhiyong/termux-ndk` / `termux-sdk` が配布している。ただしこれらは
  **Android のリンカ `/system/bin/linker64`** を要求する ARM aarch64 ELF。
- z2term の PRoot 内には既定で `/system` が見えないので、そのままでは動かない。

→ **0.8.4-alpha(12) で追加した「Android ホスト bind」トグル**を ON にすると
  `/system` `/apex` が PRoot/chroot 内へ bind され、Android リンカが見えるようになり、
  termux 系 build-tools が PRoot 内で動く。これが端末内ビルドの肝。

### 最短フロー

```
[1回だけ] R1 入り APK (0.8.4-alpha 以降) を PC か GitHub Actions で作る
        ↓
       スマホにインストール
        ↓
   設定 → 「実験的 / 開発者向け」→「Android ホスト bind」を ON → セッション再起動
        ↓
[以降] z2term の PRoot 内で下記の環境構築 → ./gradlew assembleFullDebug
```

> 0.8.3 以前の APK にはこのトグルが無いので、**初回だけは別経路でビルドした
> 0.8.4 以降の APK** が必要。これが入れ替われば以降は端末内で完結できる。

---

## 1. PC とスマホの併用（NDK 切り替え）

`app/build.gradle.kts` には NDK 版を直書きしない。代わりに **`local.properties`
（環境ごと・git 管理外）の `ndk.version`** で切り替える。これで同じソースを両方で使える。

### PC（x86_64）側の `local.properties`

```properties
sdk.dir=/opt/android-sdk
# ndk.version は書かない (= AGP 既定 NDK を使う。例: 28.2.13676358)
```

### スマホ z2term の PRoot 内の `local.properties`

```properties
sdk.dir=/root/android-sdk
ndk.version=29.0.14206865
```

`ndk.version` を書いた環境だけその NDK を使い、書かない PC 側は普段どおり。
`local.properties` は `.gitignore` 対象なので、**コミットされず衝突しない**。

---

## 2. PRoot 内 Arch Linux ARM64 の環境構築コマンド

z2term の PRoot シェル（Arch Linux ARM64）に入ってから実行する。

### 2-A. ベースのツール

```bash
# パッケージ DB 更新 + JDK17 / cmake / git / unzip / p7zip / wget を入れる
pacman -Syu --noconfirm
pacman -S --noconfirm jdk17-openjdk cmake git unzip p7zip wget which

# JAVA_HOME を通す (シェル再起動後も効くよう .bashrc にも追記)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
java -version   # openjdk 17 が出れば OK
```

### 2-B. Android SDK ディレクトリの土台

```bash
mkdir -p /root/android-sdk/{ndk,build-tools,platforms,platform-tools,cmake}
```

### 2-C. NDK (termux-ndk r29 ARM64)

公式 NDK は ARM64 ホスト用が無いので termux-ndk を使う。
（リリース URL は `github.com/lzhiyong/termux-ndk/releases` で最新を確認）

```bash
cd /root
# 例: r29 の aarch64 版を取得 (ファイル名は releases ページで要確認)
wget -O ndk.zip "https://github.com/lzhiyong/termux-ndk/releases/download/<tag>/android-ndk-r29-aarch64.zip"
unzip -q ndk.zip
# 展開先を SDK 配下へ。ディレクトリ名は local.properties の ndk.version と一致させる
mv android-ndk-r29* /root/android-sdk/ndk/29.0.14206865
```

> 重要: `/root/android-sdk/ndk/29.0.14206865` の **ディレクトリ名**を
> `local.properties` の `ndk.version=29.0.14206865` と**完全一致**させること。

### 2-D. build-tools (termux-sdk aarch64 / aapt2・zipalign・aidl)

```bash
cd /root
# termux-ndk リポの android-sdk-aarch64.7z などを取得して展開 (URL は releases 参照)
wget -O sdk-tools.7z "https://github.com/lzhiyong/termux-ndk/releases/download/<tag>/android-sdk-aarch64.7z"
7z x sdk-tools.7z
# 展開された build-tools を SDK 配下へ (例: 35.0.0)
cp -r android-sdk/build-tools/35.0.0 /root/android-sdk/build-tools/35.0.0
cp -r android-sdk/platforms/android-35 /root/android-sdk/platforms/android-35
```

これらの aapt2 等は `/system/bin/linker64` を要求する ARM aarch64 ELF なので、
**「Android ホスト bind」トグルが ON でセッション再起動済み**であることが前提。
確認:

```bash
ls /system/bin/linker64        # これが見えれば bind 成功
/root/android-sdk/build-tools/35.0.0/aapt2 version   # バージョンが出れば動いている
```

### 2-E. CMake を gradle が見つけられるように

```bash
# pacman の cmake を SDK 配下の想定パスに見せる (バージョンは合わせる)
ln -sf /usr/bin/cmake /root/android-sdk/cmake/3.22.1/bin/cmake 2>/dev/null || true
```

---

## 3. ソースを用意してビルド

```bash
# プロジェクトを PRoot から触れる場所に置く (例: 共有ストレージ経由でコピー)
cp -r /sdcard/.../05_z2term ~/z2term
cd ~/z2term

# local.properties を PRoot 用に (上の 1 章を参照)
cat > local.properties <<'PROPS'
sdk.dir=/root/android-sdk
ndk.version=29.0.14206865
PROPS

# CMake / NDK キャッシュをクリア (環境を変えた直後は必須)
rm -rf app/.cxx app/build/intermediates/cxx
sh ./gradlew --stop

# ビルド (full フレーバーの debug)
sh ./gradlew assembleFullDebug
```

成果物: `app/build/outputs/apk/full/debug/app-full-debug.apk`

---

## 4. うまくいかないとき

| 症状 | 原因 / 対処 |
|---|---|
| `aapt2: no such file` 系 / `linker64 not found` | 「Android ホスト bind」が OFF か、セッション未再起動。ON にして z2term を再起動 |
| `NDK did not have a source.properties` | `ndk.version` の値とディレクトリ名が不一致。`/root/android-sdk/ndk/<名前>` と揃える |
| `CMake ... not found` | 2-E の symlink、または `cmake.dir` を local.properties に追記 |
| x86_64 の aapt2 が呼ばれて落ちる | `/root/android-sdk/build-tools/` に termux 版(ARM64)を上書き配置。Maven 経由の x86_64 版が混ざっていないか確認 |
| daemon が不安定 / OOM | `sh ./gradlew --stop` で daemon を落としてから再実行。メモリの少ない端末は `org.gradle.jvmargs` を絞る |

---

## 5. 関連

- 「Android ホスト bind」トグルの実装: `ProotLauncher.kt`（proot `-b /system -b /apex` /
  chroot `mount --bind`）、`AppSettings.kt`、`SettingsSheet.kt`
- PC ビルドの通常手順: `docs/RELEASE.md`
- ⚠ ホスト bind は Android のシステムディレクトリを端末内に晒す。**ビルド用途で使い、
  終わったら OFF に戻す**運用が無難（OFF + 再起動で bind は外れる）。
