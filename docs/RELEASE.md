# Z2Term リリース手順

最終更新: 2026-05-20

リリースビルドは **R8 (minify) + リソース圧縮 + native シンボル除去** が有効。
debug ビルド (104MB) に対し release は約 **53MB** まで縮む。

## 1. 同梱物の生成 (初回 / rootfs 変更時のみ)

```bash
bash scripts/build-bundle.sh          # PRoot + Alpine rootfs + フォント一括
# rootfs だけ作り直すなら:
FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64
```

rootfs の中身を変えたら `DistroBundle.ROOTFS_VERSION` を +1 すること
(端末側が APK 入替で自動再展開する)。

## 2. 署名鍵の用意 (本番配布時)

```bash
# キーストア生成 (一度だけ)
keytool -genkeypair -v \
  -keystore z2term-release.jks \
  -alias z2term \
  -keyalg RSA -keysize 4096 -validity 10000

# テンプレをコピーして値を埋める
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties
```

`keystore.properties` と `*.jks` は `.gitignore` 済み (コミットされない)。
`keystore.properties` が無い場合は **debug 鍵にフォールバック**してビルドは通る
(動作確認用。Play/F-Droid 配布には本番鍵が必須)。

## 3. リリースビルド

```bash
# FOSS フレーバー (F-Droid 向け、rootfs は同梱)
./gradlew :app:assembleFossRelease
# 出力: app/build/outputs/apk/foss/release/app-foss-release.apk

# full フレーバー
./gradlew :app:assembleFullRelease
```

## 4. 署名の確認

```bash
APKSIGNER=$(ls $ANDROID_HOME/build-tools/*/apksigner | tail -1)
"$APKSIGNER" verify --print-certs \
  app/build/outputs/apk/foss/release/app-foss-release.apk
```

`CN=Android Debug` と出たら debug 鍵 (= keystore.properties 未設定)。
本番鍵なら自分の DN が表示される。

## 5. CI で PAT 無しリリース (tag push → GitHub Release, foss 版)

`v*` タグを push すると、GitHub Actions (`.github/workflows/build.yml` の `release` ジョブ) が
**署名済み FOSS release APK** をビルドして GitHub Release に添付する。認証は Actions 組み込みの
`GITHUB_TOKEN`(自動発行)なので **PAT を手元に置く必要がない**。full 版は同梱 rootfs(≈195MB)が
重いため当面 CI 対象外で、full だけ従来どおり手動リリースする(§3 でビルド → `gh release upload`)。

### 一度だけ: リポジトリ Secrets を登録

`Settings → Secrets and variables → Actions → New repository secret` に 4 つ登録する。
署名鍵は §2 の本番 `*.jks` と同一を使う(既存リリースの更新として入るように)。

| Secret 名 | 値 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 本番キーストアを base64 化した文字列 (`base64 -w0 z2term-release.jks`) |
| `RELEASE_STORE_PASSWORD` | キーストアのパスワード |
| `RELEASE_KEY_ALIAS` | 鍵エイリアス (例 `z2term`) |
| `RELEASE_KEY_PASSWORD` | 鍵のパスワード |

```bash
# base64 文字列を作る (改行なし)
base64 -w0 z2term-release.jks > keystore.b64   # この中身を RELEASE_KEYSTORE_BASE64 に貼る
```

Secret が未登録だと debug 署名事故を防ぐためジョブは**明示的に失敗**する。

### リリースする (毎回)

```bash
# 版数を上げてコミット済みの状態で、そのコミットにタグを打って push (SSH。PAT 不要)
git tag v0.8.xxx-alpha
git push origin v0.8.xxx-alpha
```

- `build` ジョブ(lint/テスト/full+foss debug)を通過後に `release` ジョブが走る。
- リリースが未作成なら **新規作成して Latest** に、既にあれば **foss APK を差し替え** (`--clobber`)。
  → 先に手動で `gh release create`(notes 付き)しておき、CI に foss APK を載せてもらう運用も可。
- 初回は生成された foss APK が端末に正常インストールできるか(manifest/arsc 欠落が無いか)を確認する。
  ローカルの box64 aapt2 問題は CI(x86_64 の素の aapt2)では起きない想定。

## R8 keep ルール (app/proguard-rules.pro)

R8 で壊れやすい箇所を明示 keep 済み:

- **JNI**: `PtyProcess` + `$Companion` (native シンボル名が obfuscation で変わると
  `System.loadLibrary("z2term")` 後のメソッド解決が失敗するため)
- **JSch**: 内部 reflection (KeyExchange/Cipher 動的解決) のため全 keep
- **Compose / Coroutines / DataStore**: reflection 参照を keep
- **xz**: `-dontwarn org.tukaani.xz.**`
- **SAF**: `Z2TermDocumentsProvider` (Manifest 参照だが念のため明示)
- **data class**: `SshProfile` / `DistroSpec` / `Snippet`

新しく reflection / JNI / Manifest 参照クラスを足したら、ここに keep を追加すること。

## 既知の注意

- リリース APK は debug 鍵フォールバックでも端末インストール可だが、
  debug 署名アプリと **applicationId が衝突**するとインストールできない
  (foss は `.foss` suffix が付く)。
- `lintVitalRelease` がエラーを出したら `app/build.gradle.kts` の lint 設定を確認。
