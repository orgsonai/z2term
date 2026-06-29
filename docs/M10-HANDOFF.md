# M10 ハンドオフ — GUI 進捗整形 / インストール時間制限切替 / IME 学習履歴 / 言語スイッチ / Konsole 修正 / 横画面キーボード / プレス可視化

最終更新: 2026-05-28（Phase 1+2+3 全バッチ + Konsole on Arch 完全動作 + 横画面キーボード位置/幅/高さ可変 + プレス可視化 + クリーンインストール以外オフライン化、実機で全項目動作確認済）
ベース: 0.7.0-alpha 系（M9 の続き）/ ブランチ: main / **すべてローカルコミットのみ、push 未実施**

### コミット履歴（M10 全 30 本、新しい順）

| Commit | 種別 | 概要 |
|---|---|---|
| `957f099` | fix | FlickKey 中央文字色を pressed 時に黒へ追従 |
| `0ca0233` | fix | フリック中央二重表示の撤回 + クリーンインストール以外を完全オフライン化 |
| `eced9f9` | feat | 横画面キーボード高さもスライダーで可変 + プレス/フリック視覚フィードバック |
| `c466771` | fix | Konsole 起動時の冗長な再導入チェックを fast-path で省略 |
| `2738f34` | feat | 横画面サイドキーボードの幅をスライダーで可変に |
| `1b34d8a` | fix | Konsole on Arch を本体不在からも救済 + 横画面 KB を View 監視で確実に検知 |
| `2243631` | docs | 引き継ぎを fbddfec + 4c05555 まで反映 (Konsole 4段階 / 横画面キーボード位置) |
| `4c05555` | feat | 横画面のキーボード位置を 左/下/右 から選択できるように (`landscapeKeyboardPosition`) |
| `fbddfec` | fix | Konsole on Arch を bsdtar 直接展開 + LD_LIBRARY_PATH で最後まで救済 (4段階) |
| `943893f` | fix | Arch proot のファイル展開取りこぼしを `pacman --overwrite '*'` で救済 |
| `eadb1fe` | fix | Arch でも konsole の Qt6 declarative を無条件追加 |
| `c5f1993` | fix | Konsole の Qt6 依存補完を ldd 不依存に変更 |
| `30626a3` | fix | Konsole の Qt6 依存欠落を補完導入 |
| `1c15495` | fix | Konsole 起動を更に堅牢化 + 診断ログ追加 |
| `028cb02` | fix | Konsole 起動修正 + 🖥 ボタンを CUI⇄GUI 連動に |
| `1dba306` | i18n | SAF Provider / shell スクリプト / 例外・ログ 残置を翻訳 |
| `e8ca17b` | docs | D3 Batch3-6 完了状態を反映 |
| `6b48872` | i18n | 通知 / Toast / GuiTerminal+KeyboardStyle 表示名 |
| `ec021e1` | i18n | SshAccessHelper + Storage + TerminalScreen GUI 起動確認 |
| `eff9d66` | i18n | OssComponents + 設定シート残り + 独自テーマエディタ |
| `d592444` | i18n | SFTP シートを strings.xml 化 |
| `5bc2e7f` | docs | D3 Batch1/2 完了状態 / 残バッチ計画を反映 |
| `3f93755` | i18n | Snippets + SSH プロファイル + HostKey 検証ダイアログ |
| `b859512` | i18n | ライセンス画面 + 確認ダイアログを strings.xml 化 |
| `a352c25` | docs | ハンドオフ作成（初版） |
| `6799d88` | feat | アプリ内言語スイッチ + 第 1 弾翻訳 |
| `f3fe1f4` | feat | GUI 進捗整形 + インストール時間制限切替 + IME 学習履歴 |

### 状態
- **ビルド**: Full Debug / Full Release 両方 BUILD SUCCESSFUL。実機 (`ZY32LNFX2B`) にインストール済。
- **実機検証進捗**:
  - **🖥 ボタンの CUI⇄GUI 連動: ✅**
  - **Konsole on Alpine: ✅**
  - **Konsole on Arch: ✅**（4段階フォールバックを経て起動成功、現在は fast-path で 2 回目以降は何もせず即起動）
  - **横画面キーボード位置 (左/下/右): ✅**（View.OnLayoutChangeListener で向き変更を確実に検知）
  - **横画面キーボード幅スライダー (280-700dp): ✅**
  - **横画面キーボード高さスライダー (200-500dp): ✅**
  - **押下時の背景強調 + フリック方向ヒント拡大: ✅**
  - **通常 🖥 起動の完全オフライン化（クリーンインストール以外でネット禁止）: ✅**
  - Phase 1-A 進捗テキスト整形 / 1-B タイムアウト無効化 / 2-C IME 学習履歴 / 3 言語スイッチ: 既存機能、本セッションで再回帰確認済
- **翻訳進捗**: 開始時 340 件のハードコード日本語 → 残 68 件（~80% 完了）。残はファクトリ data class 内の Japanese 定数（言語切替の対象、UI 表示は全て動的）、コメント、Japanese フリックキーボードの「変換」「小゛゜」など意図的なもの。**実 UI で日本語が残るのは事実上ゼロ**。

---

## 0. 背景（ユーザー要望）

1. **GUI ターミナルインストール時の進捗バー・文字がバグっている** → 正しく表示。
2. **OS / ターミナルインストールのタイムアウトが邪魔** → 設定でタイムアウトなしにできるように。
3. **日本語キーボードがまだ使いにくい**。特に**予測変換**が外部アプリ（Gboard 等）に遠く及ばない。履歴/学習が無い。
4. **アプリの英語版を実装したい**。日本語キーボードは隠して英語対応に。

要望 1/2 を Phase 1、要望 3 を Phase 2、要望 4 を Phase 3 として分割。
本セッションで Phase 1 全部 + Phase 2 全部 + Phase 3 の D1（土台）+ D2（高頻度 UI 翻訳）まで実装。
**Phase 3-D3（残約 320 件の翻訳）は次セッション以降に分割**。

---

## 1. 変更ファイル一覧（18 本、新規 2 本）

### Phase 1+2 コミット (f3fe1f4)

| ファイル | Phase | 概要 |
|---|---|---|
| `gui/GuiSession.kt` | 1-A, 1-B | `drainPty` で ANSI/CR/制御文字を剥がし最終行を抽出。`connectWithRetry(timeoutMs: Long?)` で null=無期限化。`noInstallTimeout` を AppSettings から参照。 |
| `gui/GuiScreen.kt` | 1-A | 進捗 `Text` に `maxLines=4 + softWrap + ellipsis + monospace + widthIn(360.dp)`。長行で右端ステータスを押し出さない。 |
| `settings/AppSettings.kt` | 1-B | `noInstallTimeout: Boolean`（既定 OFF）+ setter + 関連定数（`DEFAULT_GUI_CONNECT_TIMEOUT_MS`, `EXTENDED_DOWNLOAD_READ_TIMEOUT_MS`）。 |
| `core/TerminalSession.kt` | 1-B, 2-C | `setNoInstallTimeout` 追加。`downloadDistroArchive` で設定に応じて `readTimeoutMs` を 30s→5min に拡張。 |
| `distro/DistroDownloader.kt` | 1-B | `download(readTimeoutMs)` を引数化。HTTP の read timeout を呼出し側で指定可能に。 |
| `ui/settings/SettingsSheet.kt` | 1-B | トグル「インストールのタイムアウトを無効化」追加。 |
| `ui/terminal/keyboard/ImeHistoryStore.kt` | 2-C | **新規**。`filesDir/ime_history.json` で確定済み (reading → word) を永続化。count + 直近 7 日 recency でスコア化、上限 4000 件、1 秒 debounce 書込。 |
| `ui/terminal/keyboard/KanaKanjiConverter.kt` | 2-C | `convertFlexible` の優先順を「履歴(完全一致) → 辞書一致 → 送り仮名活用 → 文節分割 → 履歴(前方一致) → 辞書予測」へ変更。`ComposingState.commit`/`commitRaw` で `record()`。 |
| `ui/terminal/TerminalScreen.kt` | 2-C | `ImeHistoryStore.ensureLoaded` を端末タブ/GUI タブの両方で起動時にロード。 |

### Phase 3-D1+D2 コミット (6799d88)

| ファイル | Phase | 概要 |
|---|---|---|
| `settings/LocaleHelper.kt` | 3-D1 | **新規**。専用 SharedPreferences (`z2term_locale`) で言語を持つ。`applyLocale(Context)` で `Configuration.setLocale` を上書き。 |
| `MainActivity.kt` | 3-D1 | `attachBaseContext` で `LocaleHelper.applyLocale` を差し込む。 |
| `Z2TermApplication.kt` | 3-D1 | 同上（Toast / Service など Activity 外文字列の Locale も揃える）。 |
| `ui/settings/SettingsSheet.kt` | 3-D1, 3-D2 | 先頭に「言語 / Language」セクション追加（切替時 `Activity.recreate()`）。主要 Section title / ToggleField / SliderField / TextField / ActionButton を `stringResource` 化。 |
| `ui/terminal/keyboard/TerminalKeyboard.kt` | 3-D1 | `showJapaneseKeyboard: Boolean` 引数追加。false のとき「あ」キーを不可視スペーサに置換（レイアウト保持）。 |
| `ui/terminal/TerminalScreen.kt` | 3-D1 | `TerminalKeyboard` 呼出し 2 箇所で `showJapaneseKeyboard = LocaleHelper.language(context) == LANG_JA` を渡す。 |
| `core/TerminalSession.kt` | 3-D2 | 起動バナー（PRoot 不在 / rootfs 展開 / DL 進捗 / SSH 接続 / 致命的エラー）を `appContext.getString(R.string.banner_*)` に置換。 |
| `res/values/strings.xml` | 3-D2 | 約 40 個の英語キー追加（settings_*, banner_*）。 |
| `res/values-ja/strings.xml` | 3-D2 | 同数の日本語ミラー追加。 |

---

## 2. Phase 1-A: 進捗バー / 文字バグの原因と修正

### 原因（読まれていなかった部分）

旧 `drainPty` は `String(buf, 0, n).trim().lineSequence().lastOrNull { it.isNotBlank() }.take(200)` で最後の行を `_message` に流していた。問題:

1. **ANSI エスケープ未処理**: `apk add` / `apt-get install` / `pacman -Sy` の出力に色制御 (`ESC [ ... m`) / OSC タイトル (`ESC ] ... BEL`) が混ざり、`Text` には `[33mFetched` のような文字列のまま流入。
2. **CR(`\r`) 未処理**: apk update の `fetch https://...` 等が CR で同一行を上書き表示するが、`lineSequence` は `\n` でしか分割せず、`\r` で繋がった巨大な 1 論理行が表示されていた。
3. **長行で右端押し出し**: `Text` に折返し・最大行制限がなく、長い行で右端アイコンや次行が画面外へ。
4. **進行行が埋もれる**: `(1/9) Installing X` のような最も意味のある行が、`Fetched` / 直後の改行で上書きされ、見えない瞬間が多発。

### 修正

`GuiSession.sanitizeProgressLine(raw: String): String?`:

1. ANSI CSI (`ESC [ ... letter`) と OSC (`ESC ] ... BEL|ESC\`) を `ANSI_REGEX` で剥がす。
2. `\n` で論理行に分割し、各行で最後の `\r` 以降を採用（apk の進捗バー最終状態）。
3. 残った C0 制御文字 (TAB/LF 以外) と DEL を `CONTROL_REGEX` で除去。
4. `PKG_PROGRESS_REGEX` (`(x/y)` / `[x/y]` / `Get:N`) にヒットする行があれば**最優先**で採用。
5. 上記が無ければ最後の非空行を `MAX_PROGRESS_CHARS=160` で切り詰め。

`GuiScreen` の Text:
- `maxLines = 4`, `softWrap = true`, `overflow = Ellipsis`, `fontFamily = Monospace`, `widthIn(max = 360.dp)`。
- 縦に最大 4 行で省略、横は 360dp 以内で折返し。長行で右端押し出しが起きない。

### ⚠ 罠 / 注意

- 正規表現は Kotlin リテラルに **ESC (0x1B) を直接書いている**。エディタや diff ツールで見えなくなりがち。`hexdump` で `1b 5c 5c 5b ...` を確認した上で書いた。将来の編集時は `xxd` で確認すること。
- ANSI_REGEX は OSC を `BEL` または `ESC \\` で終端させているが、最小 rootfs によっては未終端の OSC を吐く可能性がある（その場合は次の論理行で剥がれる）。
- `lineSequence` の代わりに `split('\n')` を使ったのは、Kotlin の `lineSequence` が `\r\n` / `\r` も区切りとみなしてしまうため（CR 区切りで最終セグメントを採る前段で破壊される）。

---

## 3. Phase 1-B: インストールタイムアウト無効化

### 現状の制限（無効化前）

- **GUI 接続待ち（`GuiSession.connectWithRetry`）**: 5 分（300_000 ms）の hard cap。Arch の pacman フルインストール、回線が細いとき、Konsole 等の大物導入で間に合わないケースがあった。
- **distro rootfs ダウンロード（`DistroDownloader`）**: TCP 単位で connectTimeout=15s / readTimeout=30s。低速回線で 30s 無通信 → 失敗するケース。
- **rootfs 展開（`DistroInstaller`）**: タイムアウトなし（成功するまで動く）。

### 設定

`AppSettings.noInstallTimeout: Boolean`（既定 OFF）。SettingsSheet にトグル追加。

ON のとき:
- `GuiSession.connectWithRetry(timeoutMs = null)` で **無期限**（`ptyClosed` のみで打ち切り → z2gui が死んだ瞬間にエラー化されるので、永久に詰まることはない）。
- `DistroDownloader.download(readTimeoutMs = 300_000)` で **5 分**。完全 0 にしないのは、回線断時にスレッドが永久ブロックされる事故を避けるため。

OFF（既定）のとき: 従来通り 5 分 / 30 秒で打ち切り。

ユーザーが途中で止めたいときは GUI タブの「✕」ボタンで `GuiSession.stop()` が呼ばれる（既存機能、無効化 ON でも有効）。

---

## 4. Phase 2-C: IME 学習履歴 + 候補ランキング

### 設計

`ImeHistoryStore`（object, singleton）:
- 保存先: `filesDir/ime_history.json`（プロセス間共有不要なため SharedPreferences でなく独自 JSON）。
- 構造: `Map<reading: String, List<Entry(word, count, lastUsedAt)>>`。
- スコア: `count + recencyBoost`。recencyBoost は最近 7 日内なら最大 +5、それ以前は 0。
- 上限: 4000 件。超えたら score 昇順で 10% カット（過剰削除と頻削除の両方を避ける）。
- 書込: 1 秒 debounce で coalesce（連打入力時の I/O 抑制）。
- 排他: `Mutex` で record/予測/save を直列化。

`ComposingState`:
- `commit(candidate)` で `ImeHistoryStore.record(text, candidate)`。
- `commitRaw()` で `ImeHistoryStore.record(text, text)`（生かなも記憶 → 次回上位）。
- 1 文字単語は `MIN_WORD_LEN=2` でスキップ（単打ひらがな等のノイズ抑制）。

`KanaKanjiConverter.convertFlexible` の候補生成順:
1. **学習履歴・完全一致** ([`ImeHistoryStore.historyFor`]) ← **最上位**
2. 辞書・完全一致 ([`convert`])
3. 送り仮名活用 ([`okuriForms`])
4. 文節分割 ([`segment`])
5. **学習履歴・前方一致** ([`ImeHistoryStore.predictHistory`]) ← 辞書予測より優先
6. 辞書・前方一致予測 ([`predict`])

### 期待挙動

- 「ありがと」を 1 度 commit → 次回「あり」と打ったとき「ありがと」が予測トップに。
- 「あいうえお」のような生かな確定も学習 → 次回「あいうえ」で予測される。
- 7 日以上前の語は recency boost 0 → count だけが効く（古いが頻出は残る）。
- SKK 辞書ベースの送り仮名活用（M9）は維持。履歴と辞書の二段で補強する設計。

### ⚠ 罠 / 注意

- 学習データは**端末ローカル**（同期せず）。アプリ削除で消える。バックアップは未実装。
- 「みんな」「ばか」など不適切語を一度確定すると上位化する。気になるなら `filesDir/ime_history.json` を削除（次回起動で空からやり直し）。
- 履歴は `KanaKanjiConverter.ensureLoaded` の辞書ロードとは独立。辞書未ロード状態でも履歴は機能する（候補 1 のみ）。
- `record` は `loaded == true` のみ動作。`ensureLoaded` 前の確定は記録されない（影響範囲は起動直後 1 秒以内のみ）。

---

## 5. Phase 3-D1: 言語スイッチ土台

### LocaleHelper

```
LocaleHelper.applyLocale(context)
  → SharedPreferences "z2term_locale" / key "lang" を同期取得
  → Locale(lang) で Configuration を上書きした wrap Context を返す
```

`MainActivity.attachBaseContext(newBase)` で `super.attachBaseContext(LocaleHelper.applyLocale(newBase))`。
`Z2TermApplication.attachBaseContext(base)` も同じ（Activity 外の getString も Locale に追従）。

### なぜ DataStore でなく SharedPreferences か

`attachBaseContext` は同期メソッド。DataStore は suspend / Flow ベースで、`runBlocking { settings.flow.first() }` を Activity 生成のたびに呼ぶのは避けたい（cold start 遅延・ANR リスク）。
言語設定だけは独立した SharedPreferences で同期取得し、`AppSettings` の DataStore とは別管理（後で同期したくなったら `setLanguage` 内で両方書く）。

### 言語切替の反映

設定シートの「言語 / Language」ChipRow で `ja`/`en` を選ぶと:
1. `LocaleHelper.setLanguage(context, lang)` で SharedPreferences へ書込。
2. `(context as? Activity)?.recreate()` で Activity 再生成。
3. 新 `attachBaseContext` が走り、新 Locale で全 Composable が再構築。
4. `TerminalKeyboard(showJapaneseKeyboard = ...)` が `LANG_JA` なら true / そうでなければ false で再描画。

### TerminalKeyboard の「あ」キー

`showJapaneseKeyboard = false` のときは `BasicKey("あ"...)` を `Box(modifier = Modifier.weight(1.4f).height(style.keyHeight))` の**不可視スペーサ**に置換。これでレイアウト幅を保ち、他のキー位置が動かない。

---

## 6. Phase 3-D2: 第 1 弾翻訳（約 40 個）

### 対象

- 設定シート: 主要 Section title（テーマ / フォントファミリー / フォントサイズ / スクロールバック / ディストロ / ログインシェル / リモート / 独自キーボードスタイル / GUI のターミナル / アプリ情報）。
- 設定シート: ToggleField 4 種（クリーンインストール / 全角曖昧文字 / バックグラウンド常駐 / ダウンロード前確認 / インストールタイムアウト無効化）。
- 設定シート: SliderField / TextField / ActionButton 各 1。
- 起動バナー: PRoot 不在 / rootfs 展開（初回・更新・進捗・完了・失敗・フォールバック）/ DL（開始・進捗・検証・完了・失敗・案内）/ distro 起動 / SSH（接続・失敗）/ 致命的エラー。

### 文字列リソースの方針

- `values/strings.xml` を **英語版（既定）**、`values-ja/strings.xml` を日本語に。OS Locale=ja で日本語、それ以外で英語、`LocaleHelper` で上書き。
- 既存の小さな strings (`status_running` 等) は雛形そのまま残置（使われていないが互換のため）。
- 新規キーは `settings_*` / `banner_*` 接頭辞でグループ化。

### 残置（D3 対象）

D2 で **未翻訳** のものを一覧化:

- ライセンス画面（`LicensesDialog`, `LicensesSection`, `legal/OssComponents.kt`）— 「OSS ライセンス / 対応ソース」「閉じる」「同梱 OSS の一覧を開く」等。
- 確認ダイアログ（`DownloadConfirmDialog`）— 「クリーンインストール」「ダウンロードして切替」等の本文。
- スニペット シート（`SnippetsSheet`）— 並べ替え / 追加 / 編集 UI。
- SSH プロファイル シート（`SshProfilesSheet`, `SftpSheet`, `HostKeyVerificationDialog`）— ホストキー警告等。
- スニペット シード（`Snippet.kt:ensureSeeded`）— 同梱コマンドのコメント。これは「ユーザーが端末に流すコマンド名」なので翻訳しないのも選択肢（英語コメントで統一でも良い）。
- Toast / `_toastEvents.tryEmit(...)` — `TerminalSession` 内、SshAccessHelper 内など散在。
- カラー名（`ColorSpec.kt`）— 「Red」「Blue」等は既に英語、`TerminalColors.kt` のテーマ名は固有名（翻訳不要）。
- `DistroSpec.displayName` / `GuiTerminal.displayName` — 「Alpine Linux」「xterm」など固有名（翻訳不要）。
- 設定シート末尾の各種補足 `Text` — 「Alpine は同梱…」「色を自分で選んだテーマを…」など細かい説明文。

合計 280〜310 個程度。**1 セッションでは終わらない見込み**。

---

## 7. ビルド / 検証

### ビルド

```bash
./gradlew :app:assembleFullDebug
./gradlew :app:testFullDebugUnitTest
```

両方 BUILD SUCCESSFUL を確認済。生成物: `app/build/outputs/apk/full/debug/app-full-debug.apk`。

### 実機検証（**本セッション未実施・次回必須**）

1. **インストール**: `adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk`
2. **Phase 1-A**: 設定で **クリーンインストール** ON にして Alpine を選び直し → 進捗テキストが整って 1〜4 行で表示されること。`(1/9) Installing libX11` のような進行行が見えること。
3. **Phase 1-B**: 設定の「インストールのタイムアウトを無効化」を ON → 🖥 で GUI を起動 → 5 分を超えても接続を諦めず待つこと（pacman 等大物導入のシミュレーション）。
4. **Phase 2-C**: 日本語フリックで「ありがと」を 3〜5 回確定 → 別のタブで「あり」と打ったとき、予測上位に「ありがと」が出ること。
5. **Phase 3-D1**: 設定の「言語 / Language」で English → 設定シートが英語に切替り、「あ」キーが消え、レイアウトが崩れないこと。再度 日本語に戻して即時反映を確認。
6. **Phase 3-D2**: English モードで Alpine を起動 → バナーが英語表記（"📦 Initial extraction of Alpine Linux…" 等）になること。

### 既知の未対応 / Limitations

- D3 残約 300 件の日本語ハードコード（§6 参照）。English モードでも一部画面は日本語のまま見える。
- IME 学習データはアプリローカル / バックアップなし。
- Phase 1-B の `connectWithRetry(timeoutMs = null)` は GUI 接続だけ無期限。`DistroInstaller`（rootfs 展開）は元から無タイムアウトなのでそちらは影響なし。
- TerminalKeyboard の「あ」キー不可視スペーサは weight=1.4f を保つが、長押し連打などのジェスチャは発火しない（タッチ判定なし＝意図通り）。

---

## 7-A. Konsole 起動修正の経緯 (重要・引継ぎポイント)

### ユーザー報告

> Konsole がインストール成功してますが、立ち上がりません

### 切り分け経緯（コミット順）

| コミット | 仮説 | 実機検証結果 |
|---|---|---|
| 028cb02 | TERM_ARGS に konsole case が無い + DBus セッション不在 | ❌ 同じ libQt6QuickWidgets.so.6 不在エラー |
| 1c15495 | dbus-launch/daemon 起動を堅牢化 + 診断ログを `/tmp/z2gui-term-N.log` に出すように | ✅ 診断は出るようになった。DBus/DISPLAY/XDG は OK と判明 |
| 30626a3 | Alpine の konsole が qt6-qtdeclarative を引かない仮説 → 補完導入 | ldd 検査が musl-utils 不在で空振り |
| c5f1993 | ldd 不依存に変更し Alpine では無条件 Qt6 追加 | **Alpine ✅ 起動成功** / Arch は同じエラー |
| eadb1fe | Arch (pacman) でも qt6-declarative + qt6-5compat を無条件追加 | ❌ pacman -Q では `qt6-declarative 6.11.1-2` が居るのに `/usr/lib/libQt6QuickWidgets.so.6` が無い |
| 943893f (現状) | proot 内 pacman の **ファイル展開取りこぼし** が原因と判断 → `--overwrite '*'` で強制再展開 | **未検証** |

### 確定した事実（実機ログから）

ユーザーの Arch 環境（moto g66j 5G `ZY32LNFX2B`）にて `cat /tmp/z2gui-term-1.log` の出力:
```
=== konsole launch diagnostic ===
DISPLAY=:1  DBUS_SESSION_BUS_ADDRESS=unix:path=/tmp/dbus-rkMD5AbZdy,guid=...
XDG_RUNTIME_DIR=/tmp/z2gui-xdg-1
konsole: error while loading shared libraries: libQt6QuickWidgets.so.6: cannot open shared object file
TERM_ARGS=--separate --nofork -e /bin/bash
```

`pacman -Q | grep qt6` の結果:
```
qt6-5compat 6.11.1-1
qt6-base 6.11.1-1
qt6-declarative 6.11.1-2   ← 入っているのに /usr/lib/libQt6QuickWidgets.so.6 が無い
qt6-multimedia 6.11.1-1
... etc
```

`find / -name 'libQt6QuickWidgets.so.6'` の結果:
```
/root/venv/lib/python3.14/site-packages/PySide6/Qt/lib/libQt6QuickWidgets.so.6
```
→ PySide6 同梱のものだけ。本来あるべき `/usr/lib/libQt6QuickWidgets.so.6` は不在。

### 経緯と最終形 (957f099 まで)

**初回起動の救済 (fbddfec / 1b34d8a)**:
ユーザーの実機で実証された真因は2つあった:
1. `pacman -Sy` が `could not create database entry qt6-declarative-6.11.1-2 / transaction failed` で打ち切られ、依存関係的に後ろにある **Konsole 本体まで届かない**まま「installed」と記録されていた (シンボリックリンク qt6-* は出来るが /usr/bin/konsole 自体が無い)。
2. proot の chown/chmod 取りこぼしで pacman が `installed` 記録だけ残しファイル展開が抜ける事もある (libQt6QuickWidgets.so.6 不在問題)。

これに対し `ensure_konsole_qt6` を独立関数化、Konsole バイナリ自体と libQt6QuickWidgets.so.6 を **段階0〜3** で確実に揃える方針にした:

1. **段階0**: `which konsole` で不在なら `pacman -Sy --overwrite '*' --noconfirm konsole` を単独で再導入 (qt6 依存の部分展開残りを許容)。
2. **段階1**: libQt6QuickWidgets.so.6 不在なら PM 標準の reinstall (`pacman -S --overwrite '*'` / `apt-get install --reinstall` / `apk fix --reinstall`)。
3. **段階2**: それでも不在なら cache の .pkg.tar.zst を **bsdtar / tar+unzstd で / に直接展開** (proot の chown/chmod 経路を回避、pacman DB と乖離するが ABI 上は問題なし)。
4. **段階3** (起動直前): /usr/lib に無くても、PySide6 等の他経路で持ち込まれた libQt6QuickWidgets.so.6 を `LD_LIBRARY_PATH` に積む。検索パスは `/root/venv` / `/root/.venv` / `/usr/lib/python*` / `/opt/*/lib/python*` / `/home/*/.venv*`。それでも無ければ `find /usr /opt /root -maxdepth 8` で最後の捜索。

起動診断 (`/tmp/z2gui-term-N.log` 先頭) に `LD_LIBRARY_PATH` と `/usr/lib に居るか` を併記。

**fast-path (c466771)**:
2 回目以降の起動で毎回 pacman -Sy が走るのを抑止するため、`ensure_konsole_qt6` の先頭に「`konsole` バイナリ・`/usr/lib/libQt6QuickWidgets.so.6`・`dbus-launch/-daemon` の 3 つが揃っていれば即 return」のガードを入れた。完全動作後の通常起動は他ターミナル (xterm 等) と同じく一直線で立ち上がる。

**通常起動の完全オフライン化 (0ca0233)**:
ユーザー方針で「クリーンインストール以外でネットワークを禁じる」が確定。これに対応:
- `ensure_pkgs` から `install_pkgs` 自動呼出しを撤去。未導入なら **「クリーンインストール ON で 🖥」** の案内メッセージで即終了 (exit 1)。
- `ensure_konsole_qt6` から `pacman -Sy / -Sw / apk add / apt-get install` をすべて削除。`/var/cache/pacman/pkg`・`/etc/apk/cache`・`/var/cache/apt/archives` に既に落ちている `.pkg.tar.zst / .apk / .deb` を bsdtar / tar / dpkg -x で取り出すだけ。
- ネットを叩くのは **clean install ON 時の `clean_pkgs` だけ** (ユーザー明示の opt-in)。

**確認済 (実機 2026-05-28)**: Arch + Konsole で `bash — Konsole` ウィンドウが起動し、2 回目以降の `🖥` は fast-path で pacman を叩かずに即起動。


---

## 7-B. 横画面キーボード位置設定 (4c05555)

### 背景

縦画面では問題ないが、横画面に回しても**キーボードが常に下に出る**ため、端末画面が縦に細長くなる。
横画面では左/右に出せた方が、端末を広く使える (ユーザー要望 2026-05-27)。

### 設計

- 新規設定 `AppSettings.landscapeKeyboardPosition: String`。値は `"left"` / `"bottom"` / `"right"`。既定 `"bottom"` (従来挙動)。
- 設定シートに **「キーボード位置 (横画面)」** チップ群を追加。日本語/English 両対応 (strings.xml に 4 件追加)。
- 縦画面では値に関わらず常に下配置 (既存の挙動を保持)。
- 横画面 + `left`/`right` + **独自キーボード (CUSTOM) + 折りたたまれていない** ときだけサイド配置に切替。
  - **SYSTEM (OS IME) モードは OS が必ず下端に描く**ので、横画面でも SpecialKeyBar は下のまま。
- レイアウトは Column → Row に切替。`Row(weight=1f)` 内に `SideKeyboardColumn` (左 or 右) と Terminal/GUI Box (`weight(1f).fillMaxHeight()`) を並べる。

### SideKeyboardColumn (現行: 2738f34 + eced9f9 で幅・高さ可変)

- **幅**: 新規設定 `AppSettings.landscapeKeyboardWidthDp` (既定 420dp、範囲 280-700dp、30dp 刻みスライダー)。10 キー幅で 1 キー = 幅 / 10 dp。420dp なら 42dp/key で押しやすい。
- **高さ**: 新規設定 `AppSettings.landscapeKeyboardHeightDp` (既定 320dp、範囲 200-500dp、20dp 刻みスライダー)。横画面時のみ適用 (左/下/右どの配置でも有効)。
- 横画面時は `TerminalScreen.landscapeScaledStyle(base, heightDp)` で `KeyboardStyle.copy()` 経由に keyHeight / keyFontSp / flickHintFontSp / naturalHeight を targetHeight に比例拡縮 (フォントは 0.85〜1.4 倍に丸めて潰れ防止)。

### 向き検知 (1b34d8a)

`LocalConfiguration.current.orientation` は **configChanges を declare 済 Activity では即座に再評価されない**事例が報告されているため不採用。代わりに `LocalView` の `OnLayoutChangeListener` で実 View 寸法を State 化して向きを判定する (`DisposableEffect` で attach/detach)。端末タブ / GUI タブの両方に同じ仕組み。

### GUI 画面側の挙動

- 既存の GUI 領域 Box は `onSizeChanged` で実寸を測って VNC 解像度を決めるので、サイド配置で横幅が縮めば自動的に VNC が新解像度で再ネゴ → GUI が新領域にぴったり描ける。
- 端末側と同じく Row + SideKeyboardColumn の構造に統一。

### 動作確認 (実機 2026-05-28)

- ✅ 横画面で「右」配置 → キーボード列が右、端末/GUI が残り幅にフィット
- ✅ 「左」配置 → キーボードが左
- ✅ 「下」配置 → 従来通り下配置
- ✅ 縦画面で常に下配置 (設定値に関わらず)
- ✅ SYSTEM モードでは横画面でも SpecialKeyBar が下のまま
- ✅ ▾ 折りたたみで側のキーボード列が消えて端末/GUI が全幅に

### 既知の制約

- 横画面 → 縦画面の回転で GUI 領域の解像度が変わる = VNC 再ネゴ。`onSizeChanged` で size が確定するまでの最初の数フレームは黒画面に見えることがある。

---

## 7-C. キーボードのプレス可視化 + フリック方向強調 (eced9f9 / 0ca0233 / 957f099)

### 背景

ユーザー要望 (2026-05-28): 「クリック時にキー背景を色変えて、どのキーをフリック入力しているか分かるように」。一方で中央文字を飛ばし先に差し替える方式は「同じ文字がヒントと中央に二重表示されて見づらい」のでヒント側だけの強調で良い、と方針確定。

### 設計

**プレス可視化** (BasicKey / FlickKey / JpKey / JpFlickKey):
- 各キーに `var pressed by remember { mutableStateOf(false) }` を持たせ、`pointerInput` の down/up で切替。
- pressed = true のとき背景を `ZtsGreenBright` / 文字色を `Color.Black` / 枠を `ZtsGreen` に変更。
- `detectTapWithRepeat` に `onPressedChange: (Boolean) -> Unit` を追加して、repeat 経路のキー (BackspaceKey / SpaceKey / 数字行など) からも press 状態が外に届くようにした。

**フリック方向強調** (FlickKey / JpFlickKey):
- `var flickPreview by remember { mutableStateOf<Char?>(null) }`。down からの (dx, dy) が touchSlop × 1.4 を超えたら絶対値の大きい方向で対応する flick char を予測値として状態に入れる。下回ったら null に戻す (= タップ予定)。
- 中央テキストは **常に主文字のまま** (二重表示防止)。
- 四隅 / 端のヒント (`HintText` / `Hint`) は `emphasized = flickPreview == thisDirection` のとき太字 + サイズ 1.6〜1.7 倍に拡大、色を `Color.Black` に変える。
- 確定は指を離した瞬間。`flickPreview != null` なら `onFlick(committed)`、null なら `onTap()`。途中で方向を変えても安全。

### 動作確認 (実機 2026-05-28)

- ✅ 全キー: タッチ中に背景が明るい緑になる
- ✅ フリック中: しきい値を超えた瞬間に該当方向のヒントが拡大 + 太字
- ✅ 中央文字は変わらず元のラベルのまま (二重表示なし)

---

## 8. 残課題（次セッション以降）

### 翻訳の進捗状況（M10 セッション末時点）

| バッチ | 内容 | 状態 |
|---|---|---|
| D2 | 設定シート主要 + 起動バナー (40 個) | ✅ 完了 (6799d88) |
| D3 Batch 1 | ライセンス画面 + 確認ダイアログ (25 個) | ✅ 完了 (b859512) |
| D3 Batch 2 | Snippets + SSH プロファイル + HostKey (44 個) | ✅ 完了 (3f93755) |
| D3 Batch 3 | SFTP シート (32 個) | ✅ 完了 (d592444) |
| D3 Batch 4 | OssComponents + 設定残り + テーマエディタ (60 個) | ✅ 完了 (eff9d66) |
| D3 Batch 5 | SshAccessHelper / Storage / TerminalScreen GUI 確認 (25 個) | ✅ 完了 (ec021e1) |
| D3 Batch 6 | 通知 / Toast / GuiTerminal/KeyboardStyle 表示名 (16 個) | ✅ 完了 (6b48872) |
| D3 Batch 7 | SAF Provider + shell scripts + 例外・ログ (40 個) | ✅ 完了 (1dba306) |

**開始時 340 件 → 現在残 68 件（~80% 完了）。UI 視認部の翻訳は事実上完了。**

残 68 件の内訳:

| 場所 | 件数 | 性質 | 翻訳の必要性 |
|---|---|---|---|
| `proot/GuiScript.kt` | 26 | `GuiScriptStrings.ja()` data class 内の日本語定数 | en() ファクトリと併存・実 UI は言語切替済 |
| `proot/SshdScript.kt` | 13 | `SshdScriptStrings.ja()` 同上 | 同上 |
| `gui/rfb/RfbClient.kt` | 7 | 例外 throw メッセージ (Context 無し → 英語化) | adb logcat 経由のみ |
| `gui/GuiEventWatcher.kt` | 6 | Log.* メッセージ (英語化済) | 同上 |
| その他 | <5 ずつ | docstring/コメント、JapaneseFlick の「変換」「小゛゜」(意図的) | 残置可 |

### M10 セッション末の動作状態 (2026-05-28)

主要項目はすべて実機 (`ZY32LNFX2B`) で動作確認済:

- ✅ Konsole on Alpine / Arch 両方起動成功 (§7-A)
- ✅ 横画面キーボード位置 (左/下/右) + 幅・高さスライダー (§7-B)
- ✅ プレス可視化 + フリック方向強調 (§7-C)
- ✅ 通常 `🖥` 起動の完全オフライン化 (クリーンインストールのみネット使用)
- ✅ Konsole 起動 2 回目以降の fast-path (pacman を一切叩かない)

### push のタイミング判断

- 現在 30 コミット先行。 push は明示指示待ち (CLAUDE.md 方針)。
- 直近の動作確認は済んでいるので、ユーザーが OK 出したら `git push origin main`。

### その他の中期課題

1. **F-Droid 適合化** (M9 から持ち越し)。Phase 3 の strings.xml 整備は F-Droid Weblate 連携の前準備にもなる。
2. **IME 学習データのリセット UI** (設定で「学習履歴を消去」ボタンを足すか検討)。
3. **HANDOFF / README の英訳** (公開時)。
4. **GUI 一式が未導入の状態で 🖥 を押した時の UX**: 現状は端末側にメッセージが流れて GUI タブは ERROR 状態。設定の「クリーンインストール」へ誘導するボタンを GUI タブのエラー画面に直接出すか検討。

### 引き継ぎ用クイックスタート (次セッション)

```bash
# 1) 状態確認
cd /home/orgson/tmp_folder/3/13_Zero-to-Ship/app_project/z2term
git log --oneline | head -35     # M10 全 31 本が見える (be0aae5 〜 HEAD)
git status                       # clean

# 2) 実機接続確認
adb devices                      # ZY32LNFX2B device が見える

# 3) ビルド & インストール (必要なら)
./gradlew :app:assembleFullDebug :app:assembleFullRelease
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r app/build/outputs/apk/full/release/app-full-release.apk
#   ※ Full フレーバー (appId: .debug / なし) を使用。Foss は M9 までの主検証で並列共存可。

# 4) Konsole 起動の確認 (アプリ内端末で)
#    設定で distro = Arch & GUI ターミナル = konsole → 🖥
#    fast-path が効いて pacman ログが出ない事を確認 (cat /tmp/z2gui-term-1.log)
```

### 次セッションで触り得る場所 (引き継ぎヒント)

- **GUI 未導入時 UX**: 通常 🖥 で未導入の場合、`ensure_pkgs` が exit 1 して GUI タブが ERROR になる。設定の「クリーンインストール ON」へ直接遷移するボタンを GUI タブに足すと親切。
- **F-Droid 公開**: Phase 3 の strings.xml 整備で Weblate 連携の地ならしは済んだ。次は `fastlane/metadata/android/` の整備 + `metadata/com.zerotoship.z2term.foss.yml` (F-Droid データ) の追加。
- **IME 学習履歴のリセット UI**: `filesDir/ime_history.json` を消すボタンを設定シートに。
- **HANDOFF / README の英訳**: 公開フェーズに入る前に。strings.xml の翻訳はほぼ完了している。
- **横画面キーボード幅 / 高さ の永続化テスト**: スライダーの操作中に Activity rotate が起きると DataStore の write が間に合わず古い値で再描画される可能性。実機で要観察。

### 関連メモ (auto-memory)

- [[z2term-no-network-policy]]: クリーンインストール以外でネット禁止 (ユーザー方針)
- [[z2term-konsole-arch-rootcause]]: pacman transaction rollback → bsdtar 直接展開で解消
- [[z2term-compose-configchanges-orientation]]: configChanges 宣言時 LocalConfiguration が追従しない workaround
- [[z2term-keyboard-visual-feedback-policy]]: 押下時背景 + フリックヒント拡大のみ。中央差し替え禁止

---

## 9. ポインタ / 関連ドキュメント

- 設計の系譜: `docs/M9-HANDOFF.md`（GUI 複数化 / 日本語変換柔軟化）、`docs/M8-GUI-HANDOFF.md`（Xvnc+RFB の経緯）。
- 学習履歴の保存ファイル: `filesDir/ime_history.json`（adb 経由で `run-as <appId> cat ...` で覗ける）。
- 言語の保存ファイル: SharedPreferences `z2term_locale` (`run-as <appId> cat shared_prefs/z2term_locale.xml`)。
- 設定の保存ファイル: DataStore Preferences `files/datastore/z2term_settings.preferences_pb` (`run-as ... | strings` で目視可)。
- 環境メモ: ビルドは JDK 17、`./gradlew :app:assembleFullDebug` を 1 回叩けば APK が出る。
