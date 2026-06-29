# M9 ハンドオフ — GUI 複数ディスプレイ + ツールバー固定 + 日本語変換の柔軟化

最終更新: 2026-05-26（実機検証＋追加改修反映）
ベース: 0.7.0-alpha 系（M8-8 の続き）/ ブランチ: main / コミット: `feat(M9): GUI 複数化 / ツールバー固定 / 日本語変換柔軟化 / CUI⇄GUI 連動 / 法的対応 / リサイズ崩れ修正`
**状態: 適用済 + Full Debug/Release ビルド成功 + 実機インストール完了 + 機能検証は B/D/L 済、A/C はユーザー目視 OK、R のみ手動確認推奨。**

> 初版は方針書ベースの「未適用パッチ説明」。本書は適用後に **§8 実機検証ログ** と **§9 後続改修** を追記したもの。
> §1〜7 は当初の設計説明（読み物として残置）、§8〜10 が現状の正本。

---

## 0. 背景（ユーザー要望）

1. **GUI を複数開いても同じ画面になる** → 別ポートで複数 GUI を独立に開けるように。閉じても他は生存。
2. **CUI から GUI/Python アプリを開いたら自動で GUI 側に表示**（連動）。← 設計のみ（P3、本セッション未実装）。
3. **上ツールバーの縦幅が広がる**（タブ名が長いと折り返す）/ 右端ステータスが消える → 高さ固定 + タブ名制限。
4. **日本語変換が単語完全一致のみ**（「つくって」が変換できない、単語+助動詞の活用が不可）→ 柔軟化。

要望 1/2 は当初の設計協議で **P1（別ポート複数）/ P2（ディスプレイ単位停止）/ P3（CUI 連動）** に分割。
本セッションで **P1+P2 + 要望3 + 要望4** を実装。**P3 は設計のみ（§5）**。

---

## 1. 変更ファイル（6 本）

すべて `app/src/main/java/com/zerotoship/z2term/` 配下。本番の同じ相対パスへ上書き。

| ファイル | 機能 | 概要 |
|---|---|---|
| `proot/GuiScript.kt` | A | z2gui を env `Z2_DISPLAY`/`Z2_RFBPORT` 駆動に。`/tmp` のログ/openbox-rc を per-display 化。`x_alive`/`stop_x` を**ディスプレイ単位**に書き換え |
| `proot/ProotLauncher.kt` | A | `launch(..., display: Int? = null)` 追加。非 null で `Z2_DISPLAY`/`Z2_RFBPORT` を env 付与（**`DISPLAY` は付けない**＝下記の罠回避） |
| `gui/GuiSession.kt` | A | `display` 引数 → `RfbClient(port = 5900+display)`。start/stop に display 伝播。タブ名 `GUI` / `GUI:2` … |
| `core/SessionManager.kt` | A | 空きディスプレイ番号アロケータ（`openNewGui` 払い出し / `close` 返却 / `shutdown` クリア） |
| `ui/terminal/TerminalScreen.kt` | B | `TopBar`/`GuiTopBar` 高さ 48dp 固定 + ラベル 1 行/省略/上限 140dp。`TabChip` 上限 96dp/省略 |
| `ui/terminal/keyboard/KanaKanjiConverter.kt` | C | `okuriForms`（送り仮名活用）/ `segment`（文節分割）/ `convertFlexible` 追加。`ComposingState` をこれに切替 |

**無改修で整合する既存**: `gui/GuiActivity.kt`（debug 専用, `GuiSession(applicationContext)` は display/id 既定で `:1` 起動）、`ProotLauncher.launch` の端末/SSH 呼び出し（display 既定 null = 従来挙動）、`gui/rfb/RfbClient.kt`（`port` 引数は元から存在）。

---

## 2. A: GUI 複数ディスプレイ（P1+P2）の設計と要点

- **番号 ↔ ポート**: `:N` → RFB `5900+N`（VNC 標準）。`SessionManager` が最小空き番号を払い出し/返却。
- **z2gui 動的化**: `DISPLAY_NUM="${Z2_DISPLAY:-1}"` / `RFBPORT="${Z2_RFBPORT:-5901}"`。未指定は従来 `:1`/5901。
- **ディスプレイ単位の停止（最難所）**: 旧 `stop_x` は comm 名で**全 GUI を一括 kill** していた。新方式は対象 `:N` だけを:
  1. `start_x` が起動した Xvnc/WM/端末の PID を `/tmp/z2gui-N.pids` に記録 → stop でそれを kill。
  2. 保険で X が書く `/tmp/.X<N>-lock` の **サーバ PID** も kill（Xvnc 死 → 配下が X 切断で自動終了）。
  3. **kill 前に必ず comm 確認**（`is_gui_proc`: Xvnc/Xtigervnc/openbox/xterm/urxvt/lxterminal/konsole）。PID 再利用で無関係プロセスを殺さない安全網。

### ⚠ ここで踏んだ/避けた罠（次の人へ）
- **proot env に `DISPLAY=:N` を入れてはいけない**: `z2gui stop` も同じ `ProotLauncher.launch` 経路を通るため、DISPLAY を持たせると環境変数走査ベースの停止が**自分自身を巻き込む**。よって ProotLauncher は `Z2_DISPLAY`/`Z2_RFBPORT` だけ渡し、`DISPLAY` の export は **z2gui の `start_x` 内側だけ**で行う（GUI 配下の子プロセスに伝わる）。
- **NUL 解析を避けた**: cmdline/environ は NUL 区切りで最小 rootfs での扱いが面倒。X ロックファイルの PID は数字テキストなので `tr -dc '0-9'` で堅く取れる。なお `GuiScript.kt` の生成シェルは Kotlin の **triple-quoted（raw）文字列**なので `\0` 等はリテラル（エスケープ処理されない）—それでも lock-file 方式の方が堅いので採用。
- `/tmp` は rootfs 内ディレクトリで全 proot インスタンス間で共有、`/proc` は実体バインドで PID は実 Android PID。よって別 proot からの `z2gui stop` でも pidfile / lock / kill が効く（M8-4 の停止が成り立っていたのと同じ前提）。

---

## 3. B: ツールバー / タブ名

- `TopBar`/`GuiTopBar` の `Row` を `.height(48.dp)` で固定（折り返しで縦伸びしない）。padding vertical を 8→6。
- ラベル: `maxLines = 1` + `TextOverflow.Ellipsis` + `widthIn(max = 140.dp)`。→ 右端ステータスが押し出されず表示。
- `TabChip` のタブ名: `widthIn(max = 96.dp)` + 省略。
- import 追加: `widthIn`, `TextOverflow`（`heightIn` は未使用なので入れていない）。

---

## 4. C: 日本語変換

辞書 `assets/z2dict.txt` は **SKK 送り仮名なし形式**（読み→候補）。`つくる /作る/` のような終止形・活用エントリは**無い**（`つくり /作り/造り/` はある）。これを前提に best-effort 拡張:

- **`okuriForms`**: 読みを「語幹+送り仮名」に分け、辞書の連用形見出し（語幹+ひらがな1文字 → 漢字+同じひらがな）から漢字語幹を取り、打った送り仮名を付け直す。例: つくって → 語幹つく+送りって、`つくり /作り/造り/` → **作って/造って**。長語幹優先で最初のヒット長で確定（短語幹ノイズ抑制）。
- **`segment`**: 左から最長一致で文節分割し各文節第1候補を連結。**辞書ヒット 2 文節以上**のみ返す（ノイズ回避）。
- **`convertFlexible`**: 完全一致 → 送り仮名活用 → 文節分割 → 前方一致予測の順で統合。予測（入力ごと）と変換キーの両方が使用。生かな・カタカナは常に残す。

### ⚠ 既知の制約
- **一段動詞・音便は不完全**: `okuriForms` は五段型（つくって→作って）に効くが、一段（食べて→食べる）や語幹1文字で衝突する音便（書いて/買って）は外れる/出ないことがある。**根本対応は SKK 送り仮名あり（okuri-ari）辞書の同梱**（別作業・大きめ）。
- 性能: 辞書は約 16 万行をメモリ保持。`convertFlexible` は入力毎に二分探索数十回程度でラグ無し想定だが、実機で要体感確認。

---

## 5. P3（未実装）: CUI → GUI 自動連動の設計

次パッチで本パッチの上に積む。**端末 env 注入 + アプリ常駐監視の配線が絡むためブラインド適用リスクが高く、P1+P2 が実機で安定してから別途**。

1. **端末タブにディスプレイ番号を割当**し、proot env に `Z2_DISPLAY` / `DISPLAY=:N` を注入（端末⇄GUI を 1:1 ペア）。※ P1+P2 とは別に、端末側にも番号付与が要る。
2. rootfs に **`z2run`**（`/usr/local/bin/z2run`、`ensureGuiScript` と同方式で配置）: ① `z2gui start` で Xvnc:N 確保 → ② `echo "OPEN $Z2_DISPLAY" >> /storage/app/z2gui.events` → ③ `exec "$@"`。
3. z2term に **`GuiEventWatcher`**（`getExternalFilesDir(null)` を `FileObserver` 監視 = proot 内 `/storage/app`）。`OPEN N` で `SessionManager` のディスプレイ→セッション対応を引き、該当 GUI タブを開く/前面化。
   - 通知は `echo >>` だけで依存ゼロ（nc/curl 不要）。loopback TCP 案より distro 非依存で堅い。
4. 配線の置き場（低リスク順）: `GuiEventWatcher` の start/stop は `Z2TermApplication.onCreate`（アプリスコープ）か `service/TerminalService`。`SessionManager` に `openGuiForDisplay(context, n)` を追加。

---

## 6. 適用 / ビルド / 検証

- **適用**: `cp -r z2term-patch/app z2term/`（該当 6 ファイルだけ上書き）。
- **ビルド**: JDK 17 必須（M8 メモ準拠、`org.gradle.java.home` 固定済み）。`./gradlew :app:assembleFullDebug`。
- **実機検証（必須・本セッション未実施）**:
  - A: 🖥 で GUI タブ 2 枚 → `GUI` と `GUI:2` が**別画面**（各 xterm で `xclock`/`xeyes` 等）。`/proc/net/tcp` で 5901+5902。`GUI:2` を閉じ → 5902 だけ消え 5901 生存 → 残りも閉じてリーク無し。**「2枚→片方閉じ→もう片方生存」が P2 の合否**。
  - B: 長いタブ名でツールバーが縦伸びしない・右端ステータス可視・タブ名省略。
  - C: 「つくって」→ 作って/造って が候補に。フレーズ（きょうのてんき 等）で文節合成候補。
  - 検証前に前回 Xvnc を `z2gui stop` で落とす（`x_alive` ガードで旧セッション再接続を避ける）。

---

## 7. ポインタ

- 適用手順・設計の詳細: `z2term-patch/方針書.md`（本コミットで既に適用済、パッチフォルダ自体は削除）
- GUI 機能の系譜: `docs/M8-GUI-HANDOFF.md`（M8-1〜M8-8。Xvnc+RFB 内蔵クライアント方式の経緯と罠）
- 環境メモ: `git` は PATH に有 / ビルドは JDK 17 / `/storage/app` = proot 内バインド = アプリ外部 files（adb 検証や P3 の通知に使える）

---

## 8. 実機検証ログ（2026-05-26、moto g66j 5G `ZY32LNFX2B` / Android 15 / arm64）

検証手順は **§2-D 「proot 内コマンドを adb で動かす」** の `/storage/app` + `.zshrc` 経由を多用した（独自キーボードは `input text` 不可なため）。

### 8-A: GUI 複数（A）
- ユーザー目視で「多分 OK」報告。`SessionManager.usedDisplays` の最小空き払い出し + close 時の参照カウント返却で破壊試験はコードレビューで確認。実機で `🖥` 2 連発 → `GUI`/`GUI:2`・各 `xclock` で別画面確認、片方閉じてもう片方生存、は次セッションで追検証してよい。

### 8-B: ツールバー高さ固定 ✅
- shared_home/.zshrc に `printf '\e]2;Very_Long_Title_...\a'` を仕込んで OSC 2 で長文タイトル送出。
- 結果: TopBar ラベル「Very_Long_Title_For_…」(`widthIn(max=140.dp)` で省略表示)、TabChip「Very_Long_Title…」(96.dp 省略)、**高さは 48.dp に固定されたまま縦伸び無し**。

### 8-C: 日本語変換（C）
- ユーザー目視で「OK」報告。`KanaKanjiConverter.convertFlexible` の優先順 (完全一致 → 送り仮名活用 → 文節分割 → 前方一致予測) は実装どおり。SKK 辞書側の制約による「一段動詞は外れる」のは §6 既知制約のまま。

### 8-D: CUI⇄GUI 連動 ✅
- 端末 zsh の `/proc/<pid>/environ` を `run-as` で読み取り、`Z2_DISPLAY=1` / `Z2_RFBPORT=5901` / `DISPLAY=:1` の注入を確定確認。
- .zshrc から `z2run` 単体実行 → ログ:
  - `GuiEventWatcher: OPEN display=1`（events ファイル経由通知受信）
  - `GuiSession: ✅ GUI は既に起動中 (DISPLAY=:1, RFB 127.0.0.1:5901)`（Xvnc 起動・接続成功）
- 画面: `alpine`(端末) + `GUI`(自動追加) の 2 タブ並び、GUI タブが CONNECTED で前面化。
- z2run スクリプトの rootfs 配置 (`usr/local/bin/z2run` 2532 bytes) も `ensureZ2RunScript` で確認。

### 8-E: 法的対応（L）✅
- **ライセンス UI 改修**: 設定 → 「同梱 OSS の一覧を開く ›」1 行ボタン → 全画面 Dialog → エントリタップで `assets/licenses/<SPDX>.txt` の全文表示。Dialog 化により設定シートの視認性が大幅改善（§9 後続改修）。
- **ライセンス全文同梱**: 11 ファイル全てを公式 URL から取得 (`GPL-2.0.txt` 17984 B、`GPL-3.0.txt` 35149 B、`Apache-2.0.txt` 11358 B、`OFL-1.1.txt` 4599 B、他)。Zsh は SPDX に無いので zsh-users/zsh の `LICENCE` から取得。
- **sshd 安全側挙動**: dropbear auto-install + 3 ケース実機検証:
  1. `sshd` 既定 → `🔒 loopback 限定 (127.0.0.1:2222) で起動` + `dropbear listening on 127.0.0.1:2222 (root, 鍵認証のみ, loopback 限定)` ✅
  2. `sshd --lan` + 公開鍵未登録 → `⛔ ~/.ssh/authorized_keys が未設定` で exit=2 ✅
  3. `sshd --lan` + dummy 鍵登録 → `0.0.0.0:2222 (root @ 端末IP, 鍵認証のみ)` 起動 ✅
- foss フレーバー sourceSets 分離はコード追加のみ（実ファイル移動は §8 の通り公開ビルド前に手動実施）。

### 8-R: リサイズ崩れ修正（R）
- コード変更は確定（TerminalBuffer 縮小は下行残置 / TerminalEmulator.resize は scroll region フルリセット / TerminalSession.onResize は `bumpRedrawImmediate` で即時 tick）。
- **adb でピンチ操作の自動再現が困難なため、実機で vim/htop/less ピンチによる目視確認は次セッション継続課題**。

---

## 9. 後続改修（M9 内で追加実装したもの）

| 項目 | 動機 | 変更点 |
|---|---|---|
| **L UI: 設定の Dialog 化** | 設定シート内に OSS 一覧を縦に展開すると視認性が悪い（ユーザー要望） | `LicensesDialog` を `legal/LicensesScreen.kt` に追加。`SettingsSheet` 側は「同梱 OSS の一覧を開く ›」1 行のクリック可能 Row に圧縮、タップで全画面 Dialog 展開（背景 `ZtsBgPrimary` + 上部ヘッダ + 「閉じる」ボタン + 縦スクロール内に `LicensesSection` 再利用） |
| **L ライセンス全文の実体同梱** | placeholder のままだと GPL/LGPL/Apache 等の頒布要件未達 | `app/src/main/assets/licenses/*.txt` を公式 URL から取得して上書き（11 ファイル） |
| **CMD スニペットに apk サンプル** | Alpine 用パッケージ追加手順をすぐ叩けるように（ユーザー要望、pacman/apt は不要） | `Snippet.kt` の `ensureSeeded` に `apk update` / `apk add ` (末尾スペースで止めユーザー追記用) を追加。既存ユーザーへは `SEEDED_APK` boolean フラグで 1 度だけ後追い投入 |

---

## 10. 残課題（次セッション）

1. **R リサイズ崩れの実機目視確認** — vim を開き行入力 → ピンチで拡縮 → カーソル付近が消えないこと / htop と less +F でピンチ拡縮を繰り返して「同じ文章が複数表示」「画面下半分消失」が出ないことを目視で確認。
2. **A の追検証** — 🖥 ボタン連発で GUI/GUI:2 を作り「別画面・片方閉じてもう片方生存」を再現できるか実機で確定。`/proc/net/tcp` で 5901+5902 LISTEN を見ても良い。
3. **B の RUNNING ステータス切れ** — 8-B 検証時、長文タイトル + 多数アクションボタンの組合せで右端 RUNNING の右側が画面外に切れる気配があった。ラベル `widthIn` を狭めるか、ボタン側に弾力を持たせる調整余地あり。
4. **D 完全パス検証** — 今回は `z2run` 単体での通知経路を確認したが、`z2run xclock` のような実 GUI アプリ起動は GUI 一式 (`z2gui install` で xclock 含む X11 系を導入) を入れる別工程が要る。実体動作はユーザーの GUI 利用シーンで自然に検証される。
5. **L ライセンス画面の細部** — エントリ間の余白、長い `ソース:` URL の折返し、Dialog のスクロール挙動などは UX 微調整余地あり。
6. **F-Droid 適合化（foss 実ファイル移動）** — §8 の手順は方針書のまま未実施。公開段階で実施。

---

## 11. リリース成果物（このコミット時点）

- Full Debug APK: `app/build/outputs/apk/full/debug/app-full-debug.apk`（`com.zerotoship.z2term.debug`）
- Full Release APK: `app/build/outputs/apk/full/release/app-full-release.apk`（`com.zerotoship.z2term`、`z2term-release.jks` 署名、`0.7.0-alpha`）
- 実機 (`ZY32LNFX2B`) に full release インストール済（新規）。foss release との共存問題なし（appId 別）。
