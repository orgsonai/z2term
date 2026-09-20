# Z2Term F-Droid 提出手順

最終更新: 2026-09-20

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

3 と 5、それに 6 のビルド本体が z2term では引っかかるので、対策が入れてある。

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

### 6 への対策 — フォント同梱漏れ検査の格下げ

プログラミングフォント (`app/src/main/assets/fonts/*.ttf`) は `scripts/fetch-fonts.sh`
がネットワークから取ってくるもので、**git に入っていない**。F-Droid ではネットワーク取得を
避けたいので同梱しない方針だが、`app/build.gradle.kts` の `verifyBundledFonts` タスクが
「同梱必須物の欠落」を **ビルドエラーとして止める**作りになっている
(手元で `git clean` 後に走らせる事故を防ぐための検査)。

F-Droid は clone したてのツリーをビルドするので、対策が無いと
**`assembleRelease` が毎回ここで落ちる**。yml の prebuild で格下げフラグを渡している。

```yaml
prebuild:
  - "echo allowMissingBundledAssets=true >> ../gradle.properties"
```

フォントが無いときは `TerminalFonts.isAvailable()` が assets を確認して端末の monospace へ
落とすので、**フォントの選択肢が 1 つ (System Monospace) になるだけ**で動作に支障はない。

⛔ **この検査を build.gradle.kts 側で消さないこと。** 手元と CI では
`scripts/fetch-fonts.sh` を必ず流しており、フォント無しの APK を配らないための検査として
効かせ続ける。F-Droid のビルドでだけ警告へ落とす。

### NDK の渡し方

`app/build.gradle.kts` が見るのは `local.properties` の `ndk.version` だが、
F-Droid が書くのは `ndk.dir` だけ。噛み合わないので、prebuild の 1 行目で
NDK 同梱の `source.properties` から版数を写して追記している。

```yaml
prebuild:
  - "grep Pkg.Revision $$NDK$$/source.properties | tr -d ' ' | sed s/Pkg.Revision=/ndk.version=/ >> ../local.properties"
  - "echo allowMissingBundledAssets=true >> ../gradle.properties"
  - "ANDROID_NDK_HOME=$$NDK$$ bash ../scripts/build-z2root.sh"
```

`local.properties` は prebuild より前に作られるので、追記で足りる
(2 行目は 1 つ上の節、3 行目は実行エンジンのソースビルド)。

---

## 2. 提出するタグを作る

F-Droid は**タグを指定してビルドする**ので、先にリリースを作る。手順は
`docs/RELEASE.md` の通り。

```sh
git tag v0.8.639-alpha
git push origin v0.8.639-alpha
git push github v0.8.639-alpha
```

⚠ **yml の `commit:` に書いたタグが GitHub に無いとビルドできない。**
`metadata/com.zerotoship.z2term.yml` の `commit:` / `versionCode:` / `versionName:` と、
`app/build.gradle.kts` の版数が一致していることを push 前に確かめる。

また、店頭に出る「更新内容」は**ビルドするタグの中にある**
`metadata/<locale>/changelogs/<versionCode>.txt` から読まれる。
タグを打つ前にこのファイルを入れ、**500 文字以内か確かめる** (§7)。

```sh
# タグを打つ前に必ず流す。版数の一致・更新内容の有無と長さ・scanignore の実在をまとめて見る
YML=metadata/com.zerotoship.z2term.yml
VC=$(sed -n 's/ *versionCode = //p' app/build.gradle.kts)
VN=$(sed -n 's/ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
echo "app: $VN ($VC)"; grep -E "versionName:|versionCode:|commit:|^Current" "$YML"
for L in ja-JP en-US; do
  f="metadata/$L/changelogs/$VC.txt"
  if [ -f "$f" ]; then printf "%-34s %4d 文字 (<=500)\n" "$f" "$(wc -m < "$f")"
  else echo "$f: **無い** → 店頭の更新内容が空になる"; fi
  printf "%-34s %4d 文字 (<=4000)\n" "metadata/$L/full_description.txt" "$(wc -m < metadata/$L/full_description.txt)"
  printf "%-34s %4d 文字 (<=80)\n"   "metadata/$L/short_description.txt" "$(wc -m < metadata/$L/short_description.txt)"
done
sed -n '/scanignore:/,/gradle:/p' "$YML" | sed -n 's/^ *- //p' | while read -r x; do
  [ -e "$x" ] && echo "scanignore 実在 OK  $x" || echo "scanignore が無い  $x"
done
```

上の `versionName` / `versionCode` / `commit` / `CurrentVersion*` が
`app/build.gradle.kts` の版数と揃っていること、更新内容が両言語にあり 500 文字以内で
あること、`scanignore` の 2 つが実在することを目で確かめる。

⛔ **タグの中身は後から直せない。** タグを push すると GitHub Actions が
署名済み APK を作って GitHub Release を公開し、アプリ内更新 (`z2-update`) が
それを配り始めるので、タグの打ち直しは事実上できない。上限超過に気付いても
その版の更新内容は切られたまま出る。

### いま提出先に決めてあるタグ

`metadata/com.zerotoship.z2term.yml` の `Builds:` は **0.8.639-alpha (versionCode 647) /
`commit: v0.8.639-alpha`** を指している。このタグは**まだ打っていない**ので、提出の前に
上のとおり打って両方へ push する (打つと GitHub Actions が署名済み APK を作り、
GitHub Release が公開される = 通常のリリースと同じ)。

更新内容 `metadata/{ja-JP,en-US}/changelogs/647.txt` は用意済みで、ja 287 / en 483 文字
(いずれも 500 以内)。

---

## 3. fdroiddata へ提出する

GitLab のアカウントが要る (F-Droid は GitLab)。

```sh
# 1. https://gitlab.com/fdroid/fdroiddata を fork してから
git clone https://gitlab.com/<自分のGitLab-ID>/fdroiddata ~/fdroiddata
cd ~/fdroiddata
git checkout -b com.zerotoship.z2term

# 2. 控えをそのまま置く
cp <z2term リポジトリ>/metadata/com.zerotoship.z2term.yml metadata/

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
**スマホ (Android の chroot) では Docker が動かないので、PC (Arch デスクトップ) で行う。**
⚠ **2026-09-20 時点でこの検証はまだ実施していない。** 下の 4 つはソースを読んで
組み立てた対策で、実際のビルドで確かめたわけではない: 署名設定の削除・NDK の受け渡し・
scanner の除外・フォント検査の格下げ。

```sh
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver ~/fdroidserver
# docker グループに入っている PC なら sudo は要らない
docker run --rm -itu vagrant --entrypoint /bin/bash \
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
