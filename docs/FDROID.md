# Z2Term F-Droid 提出手順

最終更新: 2026-08-26

F-Droid は「APK を受け取って配る」ところではなく、**ソースから自分でビルドして配る**ところ。
だから提出物は APK ではなく、`fdroiddata` という F-Droid 側のリポジトリに置く
**ビルドレシピ (yml 1 ファイル)** になる。

このリポジトリ側の控えは `metadata/com.zerotoship.z2term.yml`。提出するのはこの中身そのもの。

---

## 1. F-Droid のビルドが何をするか

提出後、F-Droid のビルドサーバーは毎回こう動く。ここを知らないと、なぜ下の設定が
必要なのか分からなくなる。

1. yml の `commit:` が指すタグを clone する
2. `local.properties` を作って `sdk.dir` と `ndk.dir` を書き込む
3. **署名設定を機械的に削除する** — `signingConfigs { ... }` ブロックと
   `signingConfig = <空白を含まない式>` の行を消す (署名は F-Droid 自身が行うため)
4. `prebuild:` のコマンドを `app/` の中で実行する
5. **scanner** がソースツリー全体を走査し、バイナリらしきものがあれば**ビルドを止める**
6. `./gradlew assembleRelease` を実行する
7. できた未署名 APK に F-Droid の鍵で署名して配布する

3 と 5 が z2term では引っかかるので、対策が入れてある。

### 3 への対策 — `app/build.gradle.kts`

署名設定の解決を `buildTypes` の**外**でやり、`release` の中は 1 行にしてある。

```kotlin
val releaseSigningConfig = signingConfigs.findByName("release")
    ?: signingConfigs.getByName("debug")

buildTypes {
    release {
        signingConfig = releaseSigningConfig   // ← F-Droid はこの行を消す
    }
}
```

⛔ **`release { }` の中で `?:` を使って 2 行に跨いで書かないこと。**
F-Droid は 1 行目だけを消すので `?: signingConfigs.getByName("debug")` が孤立して残り、
**Kotlin の構文エラーでビルドが落ちる**。0.8.414 でこの形に直した。

### 5 への対策 — yml の `scanignore:`

scanner は prebuild の**後**に走るので、生成したばかりのファイルも検査対象になる。
z2term では 2 つが引っかかるので、理由を添えて除外している。

| 除外するもの | 理由 |
|---|---|
| `app/src/main/jniLibs/arm64-v8a` | prebuild が `app/src/main/cpp/` の**ソースから生成した**実行体。git には入っていない (`.gitignore` 済み) ので同梱物ではない |
| `app/src/main/assets/kkc_matrix.bin` | かな漢字変換の品詞接続コスト表。int16 の数表であって実行コードではない。出典と著作権表示は `app/src/main/assets/KKC-DICT-NOTICE.txt` |

⚠ `scanignore` に書いたパスは、**実在し、かつ実際に 1 件以上の指摘を消していないと
それ自体がエラーになる**。使わなくなったら消すこと。

### NDK の渡し方

`app/build.gradle.kts` が見るのは `local.properties` の `ndk.version` だが、
F-Droid が書くのは `ndk.dir` だけ。噛み合わないので、prebuild の 1 行目で
NDK 同梱の `source.properties` から版数を写して追記している。

```yaml
prebuild:
  - "grep Pkg.Revision $$NDK$$/source.properties | tr -d ' ' | sed s/Pkg.Revision=/ndk.version=/ >> ../local.properties"
  - "ANDROID_NDK_HOME=$$NDK$$ bash ../scripts/build-z2root.sh"
```

`local.properties` は prebuild より前に作られるので、追記で足りる。

---

## 2. 提出するタグを作る

F-Droid は**タグを指定してビルドする**ので、先にリリースを作る。手順は
`/root/tmp/app_project/05_z2term/docs/RELEASE.md` の通り。

```sh
cd /root/tmp/app_project/05_z2term
git tag v0.8.415-alpha
git push origin v0.8.415-alpha
git push github v0.8.415-alpha
```

⚠ **yml の `commit:` に書いたタグが GitHub に無いとビルドできない。**
`metadata/com.zerotoship.z2term.yml` の `commit:` / `versionCode:` / `versionName:` と、
`app/build.gradle.kts` の版数が一致していることを push 前に確かめる。

また、店頭に出る「更新内容」は**ビルドするタグの中にある**
`metadata/<locale>/changelogs/<versionCode>.txt` から読まれる。
タグを打つ前にこのファイルを入れ、**500 文字以内か確かめる** (§7)。

```sh
# タグを打つ前に必ず流す。500 を超えていたら削る
for f in metadata/*/changelogs/*.txt; do printf "%5d  %s\n" "$(wc -m < "$f")" "$f"; done
```

⛔ **タグの中身は後から直せない。** タグを push すると GitHub Actions が
署名済み APK を作って GitHub Release を公開し、アプリ内更新 (`z2-update`) が
それを配り始めるので、タグの打ち直しは事実上できない。上限超過に気付いても
その版の更新内容は切られたまま出る。

⚠ 現時点の `v0.8.415-alpha` タグの中の `en-US/changelogs/423.txt` は **597 文字**で、
末尾 2 項目が切られる (手元では 487 文字に直してあるが、タグには入っていない)。
**初回提出をこのタグへ向けるとその状態で店頭に出る**。次に版を上げたときの
タグへ向けて出せば解消する。

---

## 3. fdroiddata へ提出する

GitLab のアカウントが要る (F-Droid は GitLab)。

```sh
# 1. https://gitlab.com/fdroid/fdroiddata を fork してから
git clone https://gitlab.com/<自分のGitLab-ID>/fdroiddata ~/fdroiddata
cd ~/fdroiddata
git checkout -b com.zerotoship.z2term

# 2. 控えをそのまま置く
cp /root/tmp/app_project/05_z2term/metadata/com.zerotoship.z2term.yml metadata/

# 3. コミットして push
git add metadata/com.zerotoship.z2term.yml
git commit -m "New App: com.zerotoship.z2term"
git push origin com.zerotoship.z2term
```

そのあと https://gitlab.com/fdroid/fdroiddata/-/merge_requests で
`com.zerotoship.z2term` ブランチを元にマージリクエストを出す。
ブランチ名もコミットメッセージも上の形が F-Droid の慣例。

マージされてから実際に配信に載るまで **24〜48 時間**かかる。

---

## 4. 先にビルドを試す (任意・強く推奨)

F-Droid のビルドサーバーと同じ環境が Docker イメージで公開されている。
**この端末 (Android の chroot) では Docker が動かないので、PC で行う。**

```sh
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
sudo docker run --rm -itu vagrant --entrypoint /bin/bash \
  -v ~/fdroiddata:/build:z \
  -v ~/fdroidserver:/home/vagrant/fdroidserver:Z \
  registry.gitlab.com/fdroid/fdroidserver:buildserver
```

コンテナの中で:

```sh
. /etc/profile
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver"
export JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 > /dev/null \
  | grep 'java.home' | awk -F'=' '{print $2}' | tr -d ' ')
cd /build
fdroid readmeta
fdroid rewritemeta com.zerotoship.z2term
fdroid lint com.zerotoship.z2term
fdroid build com.zerotoship.z2term
```

`fdroid rewritemeta` は yml の書式を F-Droid の正規形に整える (コメントは消える)。
**整えた結果をこちらの控えへ書き戻すのではなく、控えは人が読める形のまま保つ**こと。
提出するのは rewritemeta 後のものでよい。

---

## 5. 審査で聞かれそうなこと

| 論点 | 事実 |
|---|---|
| 実行時に rootfs を落として実行する | 初回起動で利用者が配布元を選んで同意したうえで、公式 CDN から取得し SHA-256 で検証する。取得物は自由ソフトウェア。同じ作りの端末アプリが先例として F-Droid にある (AntiFeature も付いていない) |
| アプリ内更新 (`z2-update`) | インストール元が `org.fdroid*` のときは**断って F-Droid 側へ誘導する** (`app/src/main/java/com/zerotoship/z2term/update/UpdateInstaller.kt` の `isManagedByStore`) |
| `REQUEST_INSTALL_PACKAGES` を宣言している | ⚠ **ここは必ず先に説明を用意する。** 上のアプリ内更新のための宣言で、F-Droid から入れた版では経路ごと断る。落とすのは GitHub Releases の**自分自身の APK だけ**で、最後のインストールは利用者の 1 タップが要る (アプリが黙って入れ替える方法は Android に無い) |
| 同梱バイナリ | 無し。実行エンジン z2root / z2accept / z2attach は毎ビルド、ソースから生成する |
| 依存ライブラリ | AndroidX / Compose / DataStore / JSch (mwiede fork) / BouncyCastle / XZ。すべて FOSS で、Google Play Services や Firebase は入っていない |
| フォント | `scripts/fetch-fonts.sh` が取ってくるもので git に入っていない。F-Droid ビルドでは**同梱しない** (端末の monospace へ自動フォールバック) |

---

## 6. 通ったあとの更新

yml の下 2 行がこうなっているので、以後は**タグを打てば F-Droid が自動で追従する**。

```yaml
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+-alpha$
```

`v` + 数字 + `-alpha` のタグを見つけると、その中の versionCode / versionName を読んで
ビルド定義を自動で足す。**タグ名の付け方を変えないこと** (変えると追従が止まる)。

ただし `prebuild` や `scanignore` の中身を変えたくなったときは、
fdroiddata 側へもう一度マージリクエストを出す必要がある。

---

## 7. 既知の注意

- **更新内容は 500 文字まで。** `metadata/<locale>/changelogs/<versionCode>.txt` は
  500 文字で**黙って切られる**。長いと文の途中で途切れる。
  既存の `en-US/changelogs/301.txt` (676) `401.txt` (820) `6.txt` (532) は上限超過だが、
  ビルド定義に載っていない版なので F-Droid からは読まれない。
- **説明は 4000 文字、要約は 80 文字まで。** こちらも超過分は切られる。
- 店頭の説明・画像は**このリポジトリの `metadata/<locale>/` から読まれる**
  (`short_description.txt` / `full_description.txt` / `images/icon.png` /
  `images/phoneScreenshots/` / `changelogs/<versionCode>.txt`)。
  ja-JP に画像が無い場合は en-US のものが使われる。
- CMake は F-Droid のビルドサーバーに**あらかじめ入っていない**。AGP が SDK から
  自動取得する想定だが、もし取得に失敗したら yml のビルド定義に次を足す。

  ```yaml
  sudo:
    - apt-get update
    - apt-get install -y cmake ninja-build
  ```
