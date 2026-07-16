# セッション引き継ぎ（現状ローリング）

最終更新: 2026-07-16 / 対象版数: **0.8.154-alpha (versionCode 162)** / ブランチ: internal。

## 引き継ぎサマリ — 0.8.154 (マクロ拡充: トリガー3種+アクション3種 + 公開マクロガイド、 main で開発・merge 済/実機未検証)

ユーザー要望「難しいところは不要でお勧めで追加」に応じ、**権限不要で確実に動く軽いものだけ**を足した段。BT(実行時権限フロー) と 常時オンのセンサー(電池食い) は意図的に除外。加えて**公開用のマクロ仕様書**を新設。main で開発 → internal へ merge (今回は履歴統合済みで競合なし)。**origin/main へは未 push**。

- **`b6d5f45` 0.8.154(162)**
  - **トリガー追加** (`SystemEventService`, events.jsonl・全て権限不要): `headset_plugged`/`headset_unplugged` (ACTION_HEADSET_PLUG の state) ・`airplane_on`/`airplane_off` (ACTION_AIRPLANE_MODE_CHANGED) ・`ringer_normal`/`ringer_vibrate`/`ringer_silent` (RINGER_MODE_CHANGED_ACTION)。動的レシーバの IntentFilter とハンドラに追加しただけ。
  - **アクション追加** (`Z2ApiBridge`+`Z2ApiScript`, 全て権限不要): `z2-media`(play/pause/playpause/next/previous/stop → `AudioManager.dispatchMediaKeyEvent`) ・`z2-volume`(up/down/mute/unmute/N/N% → STREAM_MUSIC、結果 current/max を返す) ・`z2-intent`(**`am start` 風の汎用 Intent 発火**。`-a/-d/-t/-p/-n/-f/--es/--ez/--ei/--broadcast/--service` を Kotlin 側で parse し startActivity/broadcast/startService)。z2-intent がマクロの要 (アプリ起動/設定画面/アラーム設定/地図/共有を 1 本で網羅)。
  - **公開マクロガイド新設**: `docs/ja/MACRO-GUIDE.md` + 英訳 `docs/en/MACRO-GUIDE.md`。**人間可読**(3段構成=トリガー/判断/アクション・準備・全リファレンス表・テンプレ・jq 無し版・トラブルシュート) かつ **AI 可読**(機械可読な表 +「AI にマクロ生成させるコピペ用指示文」で"存在しない機能を使うな"と制約)。**引き継ぎ書ではないので main = GitHub 公開対象**。README/DESIGN-SPEC/HANDBOOK からリンク。
  - 検証: foss debug unit test 全 pass (※この環境は test worker がまれに 't' 停止でハングする既知フレーク。ハング時は worker JVM を kill して再実行で通る。daemon は温存)。**実機 e2e は未** (通知検知(0.8.149)以外の 0.8.152-154 の検知/アクションは JVM+コンパイルまで)。

## 引き継ぎサマリ — 0.8.152〜153 (MacroDroid ライト: システムイベント検知 + z2-say/z2-torch、 **main で開発**・merge 済/実機未検証)

**今回は前回の教訓どおり機能を `main` で開発した** (`internal` は HANDOFF 追加＋main merge のみ)。2 コミットとも **main へコミット済** (未 push)。その後 `main → internal` を merge (コード/公開 docs の競合は「機能コミットが internal=オリジナル / main=cherry-pick の二重履歴」由来で、全て main 側 (theirs) を採用して解決。マージ後の internal ツリーは HANDOFF を除き main と完全一致)。**origin/main へは未 push** (ユーザー判断)。

前回の次タスク「MacroDroid ライト」の推奨着手分 (システムイベント検知＝画面/ロック/充電/電池/WiFi＋z2-say/z2-torch) を実装した段。z2term の「トリガー(Android→シェル)」と「アクション(シェル→Android)」の両輪を増やした。

- **`1f298a3` 0.8.152(160) — システムイベント検知 (汎用入口 / 通知検知の姉妹機能)**
  - 新規 `service/SystemEventService.kt`: 画面 ON/OFF・ロック解除(USER_PRESENT)・充電開始/停止(POWER_CONNECTED/DISCONNECTED)・電池 低下/回復(BATTERY_LOW/OKAY)・Wi‑Fi 接続/切断 を検知し `~/.z2term/events.jsonl` (=`filesDir/shared_home/.z2term/events.jsonl`) へ 1 行 1 イベントで追記。
  - **なぜ FG サービス**: これらは Android 8+ の**暗黙ブロードキャスト制限**で manifest 宣言レシーバでは配信されない。生きたプロセス内で `registerReceiver` した**動的レシーバ**でしか拾えないため、opt-in の FG サービス (`foregroundServiceType=specialUse`, `NOTIFICATION_ID=1003`, `CHANNEL_ID=z2term_events`) を常駐させその中で登録。稼働中は常駐通知が出る。
  - `{event}` = `screen_on`/`screen_off`/`unlocked`/`power_connected`/`power_disconnected`/`battery_low`/`battery_okay`/`wifi_connected`/`wifi_disconnected`。出力は `systemEventLogFormat` テンプレ (`{time}{ts}{event}{level}{ssid}`・`\n``\t`・空=JSONL) を `render()` が置換。Wi‑Fi の **SSID は位置情報権限が無いと空** (v1 は権限要求せず best-effort)。Wi‑Fi は接続/切断の状態変化のみ 1 回発火 (`lastWifiConnected` で同一状態連続を抑制)。
  - 変更: `AppSettings`(`systemEventCaptureEnabled`/`systemEventLogFormat` + keys/defaults/setter) / `TerminalSession`(setter) / `AndroidManifest.xml`(SystemEventService 登録) / `BootReceiver`(ON なら boot 後に自動常駐) / `Z2TermApplication`(前面起動時に ON なら再アサート・background 起動時の FGS 例外は握る) / `SettingsSheet`(「システムイベント検知」セクション: トグル+プリセット+テンプレ編集。トグル ON/OFF で `SystemEventService.sync()`) / strings(ja/en)。`EventRenderTest` 追加。
  - **JVM ユニットテストで検証済み** (EventRenderTest + 既存 NotificationRenderTest、foss debug 全 pass)。
- **`3f8f39c` 0.8.153(161) — アクション追加 z2-say(TTS) / z2-torch(フラッシュライト)**
  - `Z2ApiBridge` に `say`/`torch` dispatch を追加。`z2-say <text>`: 端末標準 TTS で読み上げ。**TTS 初期化は非同期**なので初回は `pendingSpeech` キューに溜め `onInit`=SUCCESS でまとめて流す (TTS は Main で生成)。`stop()` で `shutdown()`。`z2-torch on|off|toggle`(既定 toggle): `CameraManager.setTorchMode` (**権限不要**) でフラッシュ付きカメラを制御、`torchOn` でトグル状態を保持、結果状態(on/off)を返す (need_resp 1)。フラッシュ無し端末は例外→ERR。
  - `Z2ApiScript` に `z2-say`(引数無しは stdin)・`z2-torch` ラッパー追加。foss debug コンパイル pass。

### ⚠️ 検証状況 / 次やること
- **未検証(要実機・このビルド環境では e2e 不可)**: (1) events.jsonl に各イベントが実際に追記されるか (画面/ロック/充電/電池/WiFi の実発火)、(2) 常駐 FG 通知の見た目と BOOT 自動常駐、(3) `z2-say` の実読み上げ (TTS エンジン有無・日本語 Locale)、(4) `z2-torch` の実点灯/消灯/トグル。通知検知(0.8.149)は実機済だが本 2 機能は JVM テスト+コンパイルまで。
- **公開状況**: **origin/main へは未 push**。前回同様、公開判断はユーザー。push するなら `main` を origin へ。GitHub Release も別途 (前回の `v0.8.151-alpha` draft がアセット未添付で残置している件も未完のまま)。
- **MacroDroid ライトの残り (次段候補)**: イベント検知に SMS/着信(要権限)・BT/イヤホン・SSID 変化を追加。アクションに音量/明るさ/メディア操作。個別サーバー start/stop など。
- **ブランチ運用メモ**: main→internal merge は二重履歴のため毎回コード/docs が競合する。internal はコード的に main のミラーなので**競合は theirs(main) 採用でよい**。HANDOFF のみ internal 固有。

---

## 引き継ぎサマリ — 0.8.146〜151 (キャッシュ刷新 / IME 記号当て字修正 / 常駐サーバー / 通知検知 / ボトムシート修正、 internal コミット・local へ push)

このセッションの成果は **`internal` ブランチのコミット群** (`local` へ push、 **origin/main へは未反映**)。 ※ 機能は internal 限定ではなく通常機能。 main は 0.8.141 以降ずっと遅れており、 公開時に main へ昇格 → origin push が別途要る (要ユーザー判断)。

- **`6b16003` 0.8.146(154) — IME「と→＆」修正 + キャッシュ削除の刷新**
  - IME: `kkc_lex.tsv` が 1 文字ひらがな読みに記号表層を低コストで持つ (と→＆ 3177 < と→と 5381、 他 に→２/ご→５/ざ→the) ため最優先候補が記号になっていた。 `KkcConverter.loadFromStreams` に `SYMBOL_READING_PENALTY`(+10000) を追加し「読み 1 文字ひらがな ∧ 表層が記号のみ」を最下位へ。 `SymbolReadingPenaltyTest` 追加。 **JVM ユニットテストで検証済み**。
  - キャッシュ: Android `cacheDir` はインストール直後に空になり実質無意味だったため、 実際に容量を食う **rootfs 内の再取得可能キャッシュ** (pacman/apt/apk・`~/.cache` + アプリ一時) を直接掃除する `settings/RootfsCacheCleaner.kt` を新設。 ワンタップ即削除を廃し **確認ダイアログで「項目名 … サイズ」を列挙**。 `/tmp`・パッケージ本体・設定・ユーザファイルには触れない。
- **`e2f15d5` 0.8.147(155) — 常駐サーバー機構 (アプリを開かず任意サーバーを常駐)**
  - 新規: `settings/ServerEntry.kt`(定義+プリセット, DataStore JSON) / `proot/ServerSupervisorScript.kt`(全 enabled サーバーを auto-restart ループで起動し `var/lib/z2term-servers/<token>.status` に状態出力) / `service/ServerDaemonManager.kt`(`ProotLauncher.launch` で supervisor を headless 常駐・kill=一括停止・status 読取) / `service/ServerDaemonService.kt`(専用 FG サービス) / `service/BootReceiver.kt`(RECEIVE_BOOT_COMPLETED) / `ui/settings/ServersSheet.kt`(管理 UI)。
  - 変更: `AppSettings`(serverEntries/serversAutostartOnBoot) / `TerminalSession`(setter) / `AndroidManifest.xml`(RECEIVE_BOOT_COMPLETED + ServerDaemonService + BootReceiver) / `SettingsSheet`(「常駐サーバー」セクション) / strings。
  - 仕組み: proot/z2root は全プロセスが 1 本のエンジンの子 → supervisor 1 本を生かして常駐、 kill で一括停止。 サーバー本体はユーザーが distro に導入する前提 (非ハードコード)。 停止は v1 で一括のみ (個別 start/stop は次段)。
- **`c37bf28` 0.8.148(156) — 常駐サーバーの省電力モード**
  - `serversLowPower` トグル追加。 ON で `ServerDaemonService` が WakeLock/WifiLock を握らず Doze 許可 (電池優先・画面消灯中の着信は遅延/取りこぼしうる)。 既定 OFF。 `README.ja.md` の版数更新漏れ (0.8.145→) も是正。

- **`dbe03f3` 0.8.150(158) — ボトムシートがナビバーに被る不具合修正**
  - 下からせり上がる ModalBottomSheet 6 種 (Servers/CustomTheme/ImeHistory/Sftp/Snippets/ClipboardHistory) の `contentWindowInsets` が `statusBars` のみで 3 ボタンナビ分の下パディングが無く、最下部ボタンが押せなかった。→ `systemBars` に変更。
- **`4992ac5` 0.8.149(157) — 通知検知 (汎用入口)** ＋ **`f45f6be` 0.8.151(159) — 出力フォーマットのテンプレート化**
  - 経緯: 当初「別アプリ (`10_AI-ext/14_notilog`)」案 → ユーザー真意は「**特定通知保存の決め打ち機能**を公開アプリに入れるのが NG」なだけで、**汎用の通知検知**を z2term に入れるのは OK (先のデーモンはユーザーの自由)。別アプリ `14_notilog` は**破棄済**。
  - `service/NotificationLogService.kt`(`NotificationListenerService`)。OS「通知アクセス」許可で自動常駐。設定 `notificationCaptureEnabled` ON で届いた通知を `~/.z2term/notifications.jsonl`(=`filesDir/shared_home/.z2term/…`、shared_home は `-b …:/root` で端末の `~`) へ追記。**出力は `notificationLogFormat` テンプレート**を `render()` が置換 (`{time}{app}{title}{text}{text1}` 等・`\n``\t`・空=JSONL)。設定「通知検知」に許可導線＋プリセット＋テンプレ編集欄。`NotificationRenderTest` 追加。
  - **実機検証済 (2026-07-16)**: 実機で `~/.z2term/notifications.jsonl` に実通知 (メッセージ/銀行アプリ等) が追記されるのを確認。**通知検知は end-to-end で動作**。

### ⚠️ 検証状況 / ビルド
- **実機ビルドは解決済 (2026-07-16)**: `~/.gradle/gradle.properties`(git 管理外) が**空になっていた**のが原因群。復元した 2 行で full release が通り、正常 APK を確認:
  - `android.aapt2FromMavenOverride=/root/.cache/z2term/aapt2bin/aapt2` (既存の box64 aapt2 ラッパー。無いと processResources で `Daemon startup failed`)
  - `android.enableResourceOptimizations=false` (無いと release の aapt2 optimize が空出力で **manifest/arsc 欠落 → 「パッケージ解析エラー」**。詳細 [[project_ondevice_release_apk_fix]])
  - 以後は素の `scripts/gw.sh :app:assembleFullRelease` で OK。健全性は `unzip -l <apk> | grep AndroidManifest`。ネイティブ aapt2 override 別解は [[project_build_aapt2_box64_binfmt]]。
- **未検証(要実機確認)**: キャッシュ削除ダイアログ・常駐サーバー起動/BOOT 自動常駐・ボトムシート修正の見た目・通知フォーマットの各プリセット。通知検知本体は検証済。
- **公開状況 (2026-07-16)**: 6 機能コミット (0.8.146〜151) を **main へ cherry-pick して `origin/main` へ push 済** (`ddf117f`, HANDOFF 非混入を確認)。GitHub 公開 main = 0.8.151。
- **GitHub リリース = draft のまま未完 (アセット未添付)**: 新しい PAT で `v0.8.151-alpha` の **draft** を作成 (`--latest`・notes 入り) までは済んだが、**この端末の回線が断続的に不安定 (TLS handshake timeout / unexpected EOF) で APK アップロードが失敗**、アセット 0 件。ユーザー判断で **draft はそのまま残置**。完了させるには回線の安定した環境で: `GH_TOKEN=<PAT> gh release upload v0.8.151-alpha app/build/outputs/apk/full/release/app-full-release.apk app/build/outputs/apk/foss/release/app-foss-release.apk` → `gh release edit v0.8.151-alpha --draft=false --latest`。両 APK はビルド済 (full 195MB / foss 21MB, 0.8.151)。※注意: リトライを `gh ... | tail -1` で囲むと gh の失敗が隠れる (パイプ終了コードが tail のになる)。前例 [[reference_github_release_on_device]]。
- **CI にリリース自動化は無い**: `.github/workflows/build.yml` は main push で AAB を artifact 化するだけ (`softprops`/`gh release create` なし・tags トリガ無し)。「SSH だけで Release」経路は存在しない = release は手動 `gh` か Web。
- **ブランチ運用の教訓**: 今セッションは誤って internal で開発した。以後は機能は main で。internal は HANDOFF 追加＋main の随時 merge のみ。

### 次タスク — MacroDroid ライト (ユーザー承認済みの方向)
z2term は既に「アクション(シェル→Android: `z2-notify/toast/share/open/clip/battery/vibrate`)」＋「トリガー(Android→シェル: 通知検知)」を持つ。条件/ロジックはターミナル側 (script/cron/常駐サーバー) で書ける前提で、両輪を増やす:
- **システムイベント検知** (通知検知と同じ opt-in＋テンプレ＋`~/.z2term/events.jsonl` 方式): 電池/充電・画面 ON/OFF・ロック解除・Wi‑Fi 接続/SSID・イヤホン/BT・(要権限で SMS/着信)。BroadcastReceiver 群で実装。
- **アクション追加**: `z2-say`(TTS)・`z2-torch`(フラッシュライト)・メディア操作/音量/明るさ。z2-* ブリッジ (`proot/Z2ApiScript.kt` 系) に追加。
- 推奨着手: 「イベント検知＝電池/充電/画面/WiFi」＋「z2-say・z2-torch」。全て非 root・opt-in・完全ローカルの汎用フックで。

---

## 引き継ぎサマリ — 0.8.140 (z2root ローダ: musl ET_EXEC 起動の 3 バグ修正、 push 済 `abe3245`)

autoupdater が claude を glibc→**musl native (ET_EXEC)** に差し替えて以降、 FOSS タブ
(z2root エンジン強制) で起動できなくなっていた件のローダ層を修正。 musl ld.so は ET_EXEC を
明示起動できないため、 本体と ld.so を自前マップし auxv を組んで起動する専用経路
(`load_exec_via_interp` / `--loader-exec`) を通る。 ここに 3 つの独立した不具合があった。

- **(A) bind パス未逆変換** (`z2root.c` の musl ET_EXEC 分岐): loader-exec の引数に
  **ホスト実パス**を直接渡していたため、 loader 内の `map_img` の open が tracee として
  傍受され二重にパス変換され ENOENT。 隣の通常動的 ELF 経路と同様、 `host_to_guest` で
  ゲスト視点へ逆変換してから渡すよう修正。
- **(B) loader/guest アドレス衝突** (`build-z2root.sh`): loader 本体 (static 非PIE
  ET_EXEC) は既定で低位 `0x200000` 付近を固定占有。 低位ベースの非PIE ET_EXEC ゲストを
  `map_img` の `MAP_FIXED` でマップすると **loader 自身の実行中コードを上書き**し SIGSEGV。
  リンク時 `--image-base=0x100000000` で loader を 4GB へ退避し、 低位 ET_EXEC ゲスト全般の
  衝突を回避 (clang は `-Wl,--image-base`、 フォールバック ld.lld は `--image-base`)。
- **(C) 初期スタックを専用 anon mmap へ** (`z2root.c` の `load_exec_via_interp`): interp へ
  飛ぶ前の `sp` をカーネルの `[stack]` VMA 上の `alloca` ではなく**専用の通常 anonymous
  mmap 領域**に置く。 一部ランタイムはスレッド初期化時に `stack_top - RLIMIT_STACK` を一発で
  深くプローブしてガードを張るが、 この端末の kernel では `[stack]` の自動伸長が sp から遠い
  下方フォルトを救済できず素の SIGSEGV になる (near-sp の逐次伸長は効くので起動初期は通る)。
  通常 anon VMA なら領域内の任意ページが touch 時に demand-fault で入り自動伸長に依存しない。
  領域サイズ = `RLIMIT_STACK` + 余白とし、 `setrlimit` で `RLIMIT_STACK` を領域に整合させて
  プローブ先が必ず領域内に収まるようにする。

- **実機検証** (2026-06-27): FOSS release で claude が **ランタイムのフル初期化まで到達**
  (ローダ層の 3 障害はすべて解消)。 残課題はローダ外: musl 版バイナリのランタイム内部
  (モジュール bootstrap) で near-null の SIGSEGV が出るが、 これは実行エンジン非依存・
  対象バイナリ固有寄りで別タスク。
- **運用上の解決済み経路** (2026-06-27): 上記 musl ランタイムクラッシュを迂回する実績経路
  として、 glibc distro (Arch) タブで公式インストーラ
  (`curl -fsSL https://claude.ai/install.sh`) を実行し **glibc native claude 2.1.195** を
  導入、 z2root 上で対話オンボーディング (テーマ選択) まで起動確認。 glibc ld.so は ET_EXEC を
  明示起動できるため musl 専用の `--loader-exec` 経路を通らず、 ランタイムクラッシュも回避。
  落とし穴: 既存バイナリが最新版だとインストーラは再 DL をスキップし libc 違いの旧版を温存する
  → `rm -f ~/.claude/downloads/claude` してから再実行で platform 再検出。 新レイアウトは
  `~/.local/bin/claude` (symlink) → `~/.local/share/claude/versions/<ver>`。
- **git** (push 済 `abe3245`, origin/main): 本コミット (`z2root.c` + `build-z2root.sh` +
  版数 + README + DESIGN-SPEC (ja/en) + 本ファイル を 1 コミット)。 `.so` は gitignore、
  gradle が再生成。 docs 追記 `1546803` も push 済。
- **GitHub リリース公開済** (2026-06-28): `v0.8.140-alpha (148)` を **Latest** で公開
  (<https://github.com/orgsonai/z2term/releases/tag/v0.8.140-alpha>)。 アセット =
  `app-full-release.apk` (195MB) + `app-foss-release.apk` (20.8MB)。 検証: versionCode
  148 / 署名 `CN=Z2Term, O=ZeroToShip, C=JP` (release 鍵) / 同梱 `libz2root.so` は
  0.8.140 ソースから新規ビルド (`merged_jni_libs` → AGP strip → APK 内 で md5 連鎖一致＝
  stale でないことを確認)。 ビルドは `bash scripts/gw.sh :app:assembleFullRelease
  :app:assembleFossRelease` で `buildZ2rootNative` が `.so` を自動再生成。

---

## 引き継ぎサマリ — 0.8.139 (SGR underline サブパラメータ `4:n` の修正、 push 未)

styled underline (波線/二重/点線/破線) を使う TUI を抜けたあと、
**通常テキストに下線などの装飾が残る** 報告への対応。

- **背景/真因**: `processCsi` が CSI の `:` 区切り (サブパラメータ) を `;` 区切りと
  完全に同一視して `csiParams` に平坦化していた。 このため styled underline の
  `\e[4:3m` (波線) を `[4,3]` と読み **下線 + イタリック**、 `\e[4:1m`→下線+ボールド、
  `\e[4:5m`→下線+点滅。 さらに `\e[4:0m` (下線オフ) は `0` を全リセット扱いして
  前景/背景色まで消していた。 余計な装飾フラグが居残るのが症状の本質。
- **修正** (`TerminalEmulator.kt`): パーサに `csiParamIsSub` + `csiPendingSub` を追加し、
  `:` で確定したパラメータを「直前パラメータのサブパラメータ」と印付ける。
  `applySgr` の `4` を分岐し、 サブパラメータ付きなら `0`=下線オフ・非 0=下線オン、
  連続サブパラメータは読み飛ばして別 SGR と誤解釈しない (`4` 単体は単線下線のまま)。
  styled 種別は描画上は一律下線。 38/48/58 の拡張色は位置ベース読取りのままで退行なし。
- **テスト**: 新規 `SgrUnderlineSubparamTest` 4 ケース + `SgrUnderlineAltScreenExitTest`
  1 ケース。 修正前は subparam 4 ケースが fail → 修正後は全 pass、 emulator スイート退行なし。
- **git** (push 未): HEAD は本コミット (`TerminalEmulator` + 新規テスト + 版数 + README +
  DESIGN-SPEC (ja/en) + 本ファイル を 1 コミット)。
- **実機検証**: styled underline を使う TUI を抜けたあと下線が残らないことの実機確認は次セッション。

---

## 引き継ぎサマリ — 0.8.138 (タップ→click を opt-in から切り離し、 push 未)

0.8.137 の SGR mouse 入力 opt-in を実機投入したところ、
**既定 OFF の状態だと mouse capture を有効化する TUI でタップが届かない**
という退行が判明したため戻し作業を実施。

- **背景**: 0.8.137 で `TerminalInputView.onSingleTapUp` 内の `sendMouseClick` を
  `isSgrMouseInputActive(sess)` (= opt-in + mouseEnabled) 配下に閉じ込めた。
  0.8.116〜0.8.136 までは `sess.emulator.mouseEnabled` だけで自動送出していたので、
  「137 にしたらタップが効かなくなった」という体感の退行。
- **症状**: opt-in OFF で mouse capture を有効化する TUI 上で
  (1) タップしてもクリックハンドラが反応しない、
  (2) タップが効かないので結局長押し → Z2Term 自身のテキスト選択が暴発する
  (= ユーザーには「タップでドラッグ/選択扱いになる」と見える)。
- **修正**: `onSingleTapUp` の判定を `sess.emulator.mouseEnabled && sendMouseClick(...)`
  に戻して、 0.8.116〜0.8.136 と同じ挙動に戻した。
  ロングタップ→右クリック (button 2) と 1 指 drag→motion (button 32) は引き続き
  opt-in (`AppSettings.sgrMouseInputEnabled`) 配下に置く。
- **新しい挙動表**:

  | ジェスチャ | opt-in OFF (既定) | opt-in ON |
  |---|---|---|
  | 1 指タップ + mouse capture TUI | **button 0 press+release (SGR click)** | button 0 press+release |
  | 1 指タップ + 通常 shell | Z2Term focus / IME / URL 開く | Z2Term focus / IME / URL 開く |
  | 1 指長押し + mouse capture TUI | Z2Term テキスト選択開始 | **button 2 press+release (右クリック)** |
  | 1 指ドラッグ + mouse capture TUI | scrollback / alt wheel (既存) | **button 0 press + button 32 motion + button 0 release** |
  | 二本指 swipe | wheel (変更なし) | wheel (変更なし) |

- **git** (push 未): HEAD は本コミット (`TerminalInputView` + 版数 + README + DESIGN-SPEC
  (ja/en) + 本ファイル を 1 コミット)。
- **APK ビルド**: foss debug unit test 全 pass。 `MouseEncodeTest` 14 ケースは退行なし
  (タップ→click は 0.8.116 以降ずっと encode 経路を共有しているので追加テスト無し)。
- **実機検証**: タップ→ TUI クリックハンドラ発火、 ロングタップ→ Z2Term テキスト選択開始
  (opt-in OFF 時)、 opt-in ON 時に長押し→右クリック / drag→motion が乗ることの確認は次セッション。
- **TUI 側の依頼書**: タップ press / Ctrl+X が届かない件 (`/tmp/loggit-stdin-press-missing.md`)
  は端末アプリ側ではなく TUI 側の stdin 読み取り問題と切り分け済み。 別マターとして TUI 側で対応待ち。

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 上記挙動表のとおりに動くこと。 0.8.137 で実機ユーザー報告のあった
   「タップでドラッグ/選択扱いになる」が解消すること。
2. **release ビルド + GitHub Release 公開** (中): foss + full の release を 0.8.138 で公開。
3. **(任意)** opt-in を「タップ click + 右クリック」「タップ click + 右クリック + drag」 の
   2 段階に分割する案は保留 (現状 1 トグルで「右クリック + drag」がセットで切替わる)。
   実機で運用してから判断する。

---

## 引き継ぎサマリ — 0.8.137 (SGR mouse 入力 opt-in、 push 未・実機検証は未)

前回引き継ぎの最優先依頼 (`docs/SGR-MOUSE-INPUT-HANDOFF.md`) に対応する段。
**1 指タップ / ロングタップ / 1 指ドラッグ** を SGR mouse (`\x1b[<n;col;row>M/m`) として
PTY master に書き出す経路を、 既定 OFF の opt-in (Kitty graphics file/temp/shm と同じ運用)
で追加した。

- **git** (push 未): HEAD は本コミット (AppSettings + TerminalInputView + Settings UI +
  strings (ja/en) + test + docs + version up を 1 コミット)。
- **APK ビルド**: foss debug の Kotlin コンパイル / unit test 全 pass。 release / full ビルド・
  GitHub Release は未公開。
- **テスト**: `MouseEncodeTest` を 10 → 14 ケースへ拡張 (右クリック press/release のバイト列固定 /
  1 指ドラッグ motion の button 32 + 'M' 終端固定 / NORMAL は motion を抑止して null /
  BUTTON_EVENT で motion 許可)。 既存の wheel / left click / 各 encoding / DECRST 連動の
  10 ケースは退行なし。
- **実機検証は未**。 検証ポイント:
  - 設定 → 実験的 → 「SGR mouse 送出 (タッチ→マウスイベント変換)」を ON にし、
    mouse capture を有効化する TUI (カレンダー pane / ファイラ / 複数 pane フォーカス切替 等)
    で 1 指タップでフォーカス移動 / リスト項目選択ができるか
  - 1 指長押しで右クリック相当 (button 2) のメニュー / コンテキスト挙動が出るか
  - 1 指ドラッグでリスト / カレンダー上の範囲選択や caret 位置決めが追従するか
    (drag 中の motion がセル境界をまたぐたびに 1 件ずつ送られていること)
  - drag 中に画面外へ抜けて指を離しても TUI 側が press 状態のまま stuck しないこと
    (ACTION_CANCEL → button 0 release の保険が効くか)
  - opt-in OFF (既定) で従来通りタップが Z2Term 自身の操作 (フォーカス / テキスト選択 /
    scrollback スワイプ) として動作すること (= 0.8.136 までの挙動が完全保持されること)
  - 二本指スワイプ → wheel (button 64/65) が opt-in に関係なく従来通り送出されること
  - 物理マウスを繋いだ場合の挙動 (依頼書で「前回検証 OK のはず」と書かれていたパス) が
    退行していないこと
- **依頼書側の整理**: `docs/SGR-MOUSE-INPUT-HANDOFF.md` で TUI 側が「現状未実装」と書いて
  いたシングルタップ→button 0 (sendMouseClick) は 0.8.116 で実装済だったが、 これは
  opt-in OFF でも動いていた。 今回の修正で sendMouseClick も opt-in 配下に置いたので、
  「opt-in OFF にしたら 0.8.116 以降のタップ→click 送出が動かなくなる」 microregression
  あり (代わりに OFF ではタップで Z2Term 自身の focus に戻る、 依頼書の意図どおり)。

### 何が動くようになったか

| ジェスチャ | opt-in OFF (既定) | opt-in ON + TUI が mouse capture ON |
|---|---|---|
| 1 指タップ | Z2Term の focus / IME / URL 開き | button 0 press+release (左クリック) |
| 1 指長押し | Z2Term のテキスト選択開始 | button 2 press+release (右クリック) |
| 1 指ドラッグ | 1 指 swipe (scrollback / alt screen wheel) | button 0 press + button 32 motion 連発 + button 0 release |
| 二本指スワイプ | 既存通り wheel (mouseEnabled 時) | 既存通り wheel (opt-in に依存しない) |
| 二本指ピンチ | 既存通りフォントズーム | 既存通りフォントズーム |

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 上記検証ポイントを mouse capture を要求する TUI で実走。
2. **release ビルド + GitHub Release 公開** (中): foss + full の release を 0.8.137 で公開。
3. **(任意) 二本指スワイプの wheel への明示振り分け** (低): 現状 2 指 swipe は
   ScaleGestureDetector に取られて wheel が出ない経路が多い。 「pinch span がほぼ動かず
   y 移動が大きい」を wheel として横流しする実装が依頼書 nice-to-have。
4. **(任意) mouse 強制 OFF エスケープハンドル** (低): TUI が freeze して mouse capture を
   切らないまま死んだとき、 二本指長押し or 三本指タップで強制 OFF。 依頼書の提案。

### 関連ファイル (0.8.137)

- 改修: `app/src/main/java/com/zerotoship/z2term/settings/AppSettings.kt`
  (`sgrMouseInputEnabled` Snapshot フィールド + setter + DataStore key + DEFAULT 定数) /
  `app/src/main/java/com/zerotoship/z2term/core/TerminalSession.kt`
  (`setSgrMouseInputEnabled` setter) /
  `app/src/main/java/com/zerotoship/z2term/ui/settings/SettingsSheet.kt`
  (Experimental セクションに ToggleField + 警告文) /
  `app/src/main/java/com/zerotoship/z2term/ui/terminal/input/TerminalInputView.kt`
  (`sgrMouseDragActive`/`sgrMouseLastCol`/`sgrMouseLastRow` 状態 +
   `isSgrMouseInputActive` ヘルパ + `sendMouseRightClick` (button 2) +
   `sendSgrMouseDrag` (button 0 press → button 32 motion) +
   `sendSgrMouseDragRelease` (ACTION_UP で button 0 release) +
   既存 `sendMouseClick` を opt-in ゲートに集約 +
   `onLongPress`/`onScroll` 経路に opt-in 振り分け)
- テスト: `app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt` を 14 ケースへ拡張
- strings: `app/src/main/res/values-ja/strings.xml` + `values/strings.xml` に
  `settings_sgr_mouse_input_*` 3 件追加
- docs: `docs/ja/DESIGN-SPEC.md` / `docs/en/DESIGN-SPEC.md` の概要 + 変更履歴に 0.8.137 追記、
  `README.md` の版数表記更新
- 依頼書: `docs/SGR-MOUSE-INPUT-HANDOFF.md` (本実装の元になった依頼書、 内容そのまま保持)

---

## 引き継ぎサマリ — 0.8.136 (段階 10 / file/temp/shm 転送 opt-in)

0.8.132〜0.8.135 で Kitty graphics の **id 32bit / animation / `o=z`** が揃ったあと、 残スコープだった
**file/temp/shm 転送 (`t=f`/`t=t`/`t=s`)** を、 セキュリティ既定 OFF + opt-in で開ける形で
入れた段。 これで Kitty graphics protocol の主要スコープ (段階 1〜10) が一通り揃う。

- **git** (push 未): HEAD は本コミット (段階 10 = parser + host source + Settings + docs + version up を 1 コミット)。
- **APK ビルド**: foss debug の unit test 全 pass。 release / full ビルドは未実行、 GitHub Release も未公開。
- **テスト**:
  - `KittyGraphicsParserTest` を 23 → 30 (7 ケース追加: no-source Discard / File/Temp/Shm delegation / query OK/ENOTSUPPORTED / frame transfer delegation)。
  - 新規 `KittyHostTransferSourceTest` 12 ケース (read 全長 / offset+size / 負 size=末尾 / TempFile 自動 unlink / shm の `/dev/shm` rebase / `..` 拒否 / 絶対パス必須 / empty name 拒否 / 未存在 / offset 超過 / 0 slice / 上限超過拒否)。
- **実機検証は未**。 検証ポイント:
  - 設定 → 実験的 → 「Kitty graphics: 外部ファイル転送」を ON にし、 image viewer 系 TUI で `a=T,t=f` 経由の画像が rootfs 内のパスから出るか
  - `a=q,t=f` の応答が opt-in OFF/ON で切り替わるか (TUI 起動時の capability 判定)
  - `t=t` (temp file) を使う TUI 終了後、 該当ファイルが残っていない事 (auto unlink)
  - opt-in OFF で従来どおり file/temp/shm が一切受け付けられない事 (回帰防止)

### 何が動くようになったか

| 機能 | 状況 |
|---|---|
| `t=f` (regular file) | ✅ opt-in 時に rootfs 配下のファイル読込 |
| `t=t` (temp file) | ✅ opt-in 時に読了後 auto unlink |
| `t=s` (shared memory) | ✅ opt-in 時に `<rootfs>/dev/shm/<name>` へ rebase |
| Kitty 仕様 `O=N`/`S=N` (offset/size 部分読み) | ✅ |
| path traversal (`/../`) | ✅ 拒否 |
| 16 MiB 上限 (zip-bomb / DoS) | ✅ |
| `TempFile` の auto unlink | ✅ |
| `a=q` の OK 応答 (opt-in 時) | ✅ |
| AppSettings opt-in `kittyExternalFileEnabled` (既定 OFF) + UI トグル + 警告 | ✅ |

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 段階 6〜10 はすべて unit test までしか確認していない (詳細は前回引き継ぎ参照)。
2. **release ビルド + GitHub Release 公開** (中): foss + full の release を 0.8.136 で公開。
3. **(任意) external file 読込の対象範囲拡張**: 現状は **rootfs 配下のみ**。 ホスト共有領域 (例: `/storage/emulated/0/Download`) を含めたい場合は別 opt-in が必要。 image viewer 系の典型ユースケースは rootfs 内で完結するため、 当面不要。

---

## 引き継ぎサマリ — 0.8.132〜0.8.135 (4 コミット・push 未・実機検証は未)

0.8.131 の Virtual placement + Unicode placeholder に続けて、 Kitty graphics 残作業を
**5 段階** (段階 6〜段階 10、 段階 6 から 9 まで 1 コミットずつ + 段階 10 で本 docs 更新) で
進めた段。 これで Kitty graphics の **id 32bit 化 / animation 再生 / zlib 圧縮入力** が
parser/buffer/renderer 全層で揃い、 残スコープは「file/temp/shm 転送 (`t=f`/`t=t`/`t=s`)」
のみ (z2root のパス変換・SHM 権限 semantics の再検証が必要なため当面保留)。

- **git** (まだ origin/main へは push していない):
  - HEAD は段階 9 の `33e1e15`。 本セッションは 段階 6→7→8→9 を各 1 コミットで実装
    (`ff5d154`/`da34f31`/`a95d5fd`/`33e1e15`) + 段階 10 の docs 更新 (本 commit)。
- **APK ビルド**: foss debug の unit test 74 ケース全 pass。 release / full ビルドは未実行・
  GitHub Release も未公開。
- **テスト**:
  - `KittyGraphicsParserTest` を 18 → 25 ケース (`Frame` 系 3 + zlib/`o=z` 系 3 + 通常 `Put` 退行防止)。
  - `KittyPlaceholderCellTest` を 6 → 9 ケース (underline R で上位 8bit / SGR 59 で reset /
    SGR 0 が underline も道連れにリセット)。
  - 新規 `AnimationPlaybackTest` 3 ケース (init inactive / advance false / unknown id null)。
- **実機検証は未**。 検証ポイント:
  - 段階 6 (0.8.132 image id 32bit): underline color (`\e[58;2;R;0;0m`) でエンコードした
    32bit image id の TUI と衝突しないこと (≦24bit のときは退行しない)
  - 段階 7 (0.8.133 frame 蓄積): `a=f` を送ってきても画面汚染しない / frame 0 は引き続き描画
  - 段階 8 (0.8.134 animation 再生): `chafa --format kitty --animation <gif>` で実際に
    アニメが動くこと、 frame delay が概ね期待値通り、 停止時 CPU を握らない
  - 段階 9 (0.8.135 zlib 入力): `o=z` を使う TUI (chafa の compress オプション等) で画像が
    正しく表示される / 不正 zlib stream で画面汚染しない / a=q で `o=z` が OK 応答

### 何が動くようになったか (段階 6〜9)

| 機能 | 段階 | 状況 |
|---|---|---|
| Underline color (SGR 58:2:R:G:B / 58:5:idx / 59) パース | 6 | ✅ (描画は未対応・id 受け渡し専用) |
| Placeholder image id 32bit (上位 8bit を underline R から) | 6 | ✅ |
| `a=f` (frame transmit) 受領 + Bitmap 化 + `animations[]` 蓄積 | 7 | ✅ |
| `a=f` で `z=` が delay(ms) として解釈される (Z-index ではなく) | 7 | ✅ |
| Animation playback state machine (frame 切替 + delay 駆動 + loop) | 8 | ✅ |
| Renderer が現 frame の bitmap で `drawBitmap` (実 placement / virtual 両方) | 8 | ✅ |
| `LaunchedEffect` + `withFrameMillis` で active 時のみ再描画 (アイドルは 100ms poll) | 8 | ✅ |
| `o=z` (zlib 圧縮 payload) を inflate (zip-bomb 対策 16 MiB 打ち切り) | 9 | ✅ |
| `a=q` (query) が `o=z` OK / 未対応 `o=` ENOTSUPPORTED を応答 | 9 | ✅ |
| File / temp / shm 転送 (`t=f`/`t=t`/`t=s`) | — | ❌ z2root パス変換 + shm 権限要再検証で保留 |

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 段階 6〜9 はすべて unit test までしか確認していない。
   - chafa / image viewer / GIF プレビュー系 TUI で `a=T` → `a=f` → animation 再生
   - `o=z` を使う TUI で実際に画像が出るか
   - 0.8.131 までの通常 Put / VirtualPut / 静止画描画が退行していないこと
2. **release ビルド + GitHub Release 公開** (中): foss + full の release を 0.8.135 で
   公開。 手順は MEMORY `reference_github_release_on_device.md` 参照、 release は **1
   フレーバーずつ** ビルドする (MEMORY `feedback_build_one_flavor_at_a_time.md`)。
3. **file/temp/shm 転送** (低): z2root のパス変換が `t=f` (file) で `/tmp/<x>` 等の
   ホスト/ゲスト変換を正しく行えるか、 `t=s` (shm) の `SCM_RIGHTS` 経由が proot / z2root の
   ucred 偽装下で動くかの再検証が必要。 セキュリティ面では「TUI から任意ファイル読取」を
   許す経路なので、 既定 OFF + 明示 opt-in 設定が望ましい。

---

## 引き継ぎサマリ — 0.8.131 (push 未・実機検証は未)

0.8.127〜0.8.130 の Kitty graphics 4 段階に続けて、 **段階 5 のうち Virtual placement
+ Unicode placeholder (`a=p,U=1` / `a=T,U=1` + `U+10EEEE` + combining diacritic)** を
実装した段。 これで画像ビューア系 TUI が **「bitmap 登録」と「描画位置決定」を分けて送る**
仕様 (chafa の `--format kitty --passthrough` や image viewer 群の主流) で画像が出る
ようになる。

- **git** (まだ origin/main へは push していない):
  - HEAD は前回の `1d1707a`。 本セッションは 1 コミットとしてまとめる予定 (本作業 + docs)。
- **APK ビルド**: foss debug の unit test は 45 ケース全 pass。 release / full ビルドは未実行・
  GitHub Release も未公開。
- **テスト**:
  - `KittyGraphicsParserTest` を 16 → 18 ケース (`VirtualPut` 判定 + 通常 `Put` 退行防止)。
  - 新規 `KittyPlaceholderCellTest` 6 ケース (image id 抽出 / srcRow+srcCol+placementIdLow
    更新 / 通常文字後の stage 解除 / 連続 placeholder の独立 / toText 空白置換 /
    セル上書きで ref クリア)。
- **実機検証は未**。 検証ポイント (chafa / kitty 互換 image viewer):
  - `chafa --format kitty --passthrough <png>` 等で **virtual placement + placeholder** 経路の
    画像が出るか (これまで「query は OK と返るのに画像が一切出ない」だった TUI が動くこと)
  - placeholder セルが画像タイルに置き換わって表示され、 行を改行/スクロールしても
    タイル位置が崩れないこと
  - 0.8.130 までの通常 `a=p` (cursor 位置 placement) と通常 `a=T` (transmit + display) が
    退行していないこと

### 何が動くようになったか (段階 5 の第 1 弾)

| 機能 | 状況 |
|---|---|
| `a=p,U=1` で virtual placement 登録 (cursor は動かさず spec だけ Buffer に保持) | ✅ |
| `a=T,U=1` で bitmap 転送 + 同時に virtual placement 登録 | ✅ |
| 本文中の `U+10EEEE` (placeholder) セル 1 個ごとに 1 タイル描画 | ✅ |
| placeholder セルの fg truecolor (`\e[38;2;R;G;B`) から image id 24bit 抽出 | ✅ |
| 後続 combining diacritic (Kitty 仕様 297 要素表) で srcRow / srcCol / placementIdLow 順次指定 | ✅ |
| diacritic 全省略時 (`U+10EEEE` 単独) は (srcRow=0, srcCol=0) 扱い | ✅ |
| Renderer で srcRect→dstRect の 1 セル矩形タイル切り出し描画 | ✅ |
| Pass 2.7 (z<0) / Pass 3.5 (z>=0) で virtual placement も Z-index に従って 2 層描画 | ✅ |
| 削除 (`a=d,d=A`/`d=I`/`d=p`) で仮想 placement 登録も併せて消去 | ✅ |
| placeholder セルのコピー時空白置換 (toText / getRangeText) | ✅ |
| セル上書きで placeholder ref を必ずリセット | ✅ |
| Animation frames (`a=a,r=N,...`) | ❌ 残段階 |
| 画像 id 32bit 拡張 (placeholder セルの underline color から上位 8bit を読む) | ❌ 簡易実装は 24bit のみ |
| File / temp / shm 転送 (`t=f`/`t=t`/`t=s`) | ❌ セキュリティ要検討 |

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 0.8.127〜0.8.131 はすべて unit test までしか確認していない。
   - 0.8.130 の query 応答 / chafa 単発描画 / `a=p` 再配置 / `a=d` 削除 / Z-index 2 層
   - 0.8.131 の `chafa --format kitty --passthrough` / image viewer (画像本体 + placeholder の
     2 段送り設計の TUI) で virtual placement 描画が成功するか
   - 退行点として 0.8.130 までの通常 `a=p` / `a=T` が崩れていないか
2. **release ビルド + GitHub Release 公開** (中): full / foss の両 release を 0.8.131 で
   作って `v0.8.131-alpha` として公開。 手順は MEMORY `reference_github_release_on_device.md`
   参照 (gh デバイス認証は 502 多いので `GH_TOKEN` env が確実)。 release は **1 フレーバー
   ずつ** ビルドする (MEMORY `feedback_build_one_flavor_at_a_time.md`)。
3. **段階 5 残項目** (任意・低):
   - Animation frames (`a=a,r=N,...`) — Choreographer/Handler と z-index 連携が必要で大規模
   - Image id 32bit 拡張 (上位 8bit を placeholder セルの underline color から読む) — 24bit
     で衝突が起きてからで OK
   - File / temp / shm 転送 (`t=f`/`t=t`/`t=s`) — z2root 環境下のセキュリティ要検討

### 関連ファイル (0.8.131)

- 新規: `app/src/main/java/com/zerotoship/z2term/emulator/KittyPlaceholder.kt`
  (Kitty 仕様 297 要素 diacritic 表 + `PlaceholderRef` data class)
- 改修: `KittyGraphicsParser.kt` (`Result.VirtualPut` 追加 / `Result.Transmit.unicodePlaceholder`
  追加 / `a=p,U=1` と `a=T,U=1` 振り分け) / `TerminalImage.kt` (`VirtualPlacementSpec` 追加) /
  `TerminalCell.kt` (`placeholder: PlaceholderRef?` 追加 + copyFrom/clear/setClearedWith で
  リセット) / `TerminalRow.kt` (`setPlaceholder` 追加 + `setChar` で ref リセット +
  `toText` で空白置換) / `TerminalBuffer.kt` (`virtualPlacements` map +
  `registerVirtualPlacement` / `getVirtualPlacement` + 削除系で仮想も消去 +
  `getRangeText` 空白置換) / `TerminalEmulator.kt` (`putCodepoint` で `U+10EEEE` 検知 +
  diacritic stage 処理 + `putKittyPlaceholder` / `applyPlaceholderDiacritic` 追加 +
  `finalizeStringSequence` に `VirtualPut` と `Transmit(unicodePlaceholder)` 経路 +
  putChar / putWideChar / putSurrogatePair で stage リセット) /
  `TerminalRenderer.kt` (`drawPlaceholderTiles` 追加 / Pass 2.7・3.5 から呼び出し)
- テスト: `KittyGraphicsParserTest.kt` 18 ケース / 新規 `KittyPlaceholderCellTest.kt` 6 ケース
- docs: `docs/ja/DESIGN-SPEC.md` / `docs/en/DESIGN-SPEC.md` の §4.5 と変更履歴に 0.8.131 を
  追記、 README.md の版数表記更新

---

## 引き継ぎサマリ — 0.8.127〜0.8.130 (4 コミット・push 済・実機検証は未)

TUI 側からのバグ報告 (Tab/Enter/SGR mouse/cursor 飛び/kitty graphics 漏れ) を受けて入った
4 段階の修正。 前半 (0.8.127) は **文字列系シーケンス (DCS/APC/PM/SOS) の本文吸収** で
画面汚染を止める防御。 後半 (0.8.128〜0.8.130) は **Kitty graphics protocol の実装** を
段階的に積み増す: 最小描画 → アクション拡張 + 多 placement + 生 RGB(A) → query 応答 +
quiet level + Z-index 2 層描画。

- **git** (origin/main へ push 済):
  - `4f7484f` (0.8.130) Kitty graphics の query 応答 + quiet level + Z-index 2 層描画
  - `6c27a98` (0.8.129) Kitty graphics アクション拡張 + 多 placement + 生 RGB(A) 入力
  - `452ae8e` (0.8.128) Kitty graphics の最小描画 (a=T,f=100,t=d 単発+チャンク連結, a=d 全消去)
  - `e51e47b` (0.8.127) DCS/APC/PM/SOS の本文吸収を追加し TUI 画面汚染と cursor 飛びを根絶
- **APK ビルド**: foss debug までは確認済 (`scripts/gw.sh :app:assembleFossDebug` で BUILD SUCCESSFUL)。
  release / full ビルドは未実行。GitHub Release も未公開。
- **テスト**: `KittyGraphicsParserTest` 16 ケース + `StringStateAbsorbTest` 5 ケースを新規追加。 既存 `ScrollRegionLineFeedTest` / `WrapCopyTest` / `MouseEncodeTest` も全 pass で退行なし。
- **実機検証は未**。 Kitty graphics の Bitmap 描画自体は `BitmapFactory` が unit test 環境
  (Robolectric なし) では動かないため、 実機での `a=T,f=100,t=d` 描画 / `a=p` 再配置 /
  `a=d,d=I,I=N` 削除 / `a=q` 応答 / `z=N` 上下重なりは導入後に確認が必要。

### 何が動くようになったか (4 段階の累積)

| 機能 | 段階 | 状況 |
|---|---|---|
| 文字列系シーケンス (DCS/APC/PM/SOS) の本文吸収 | 0.8.127 | ✅ 5 ケーステスト |
| 単発画像 PNG (`a=T,f=100,t=d`) 表示 + チャンク (`m=1`/`m=0`) 連結 | 0.8.128 | ✅ |
| 全画像消去 (`a=d`) | 0.8.128 | ✅ |
| Transmit only (`a=t`) / Put existing (`a=p`) / 詳細削除 (`d=A`/`d=I`/`d=i`/`d=p`) | 0.8.129 | ✅ |
| 生 RGB / RGBA (`f=24` / `f=32`) | 0.8.129 | ✅ |
| 多 placement 並列 (同 anchor 行に複数画像) | 0.8.129 | ✅ |
| query 応答 (`a=q` → `OK` / `ENOTSUPPORTED:...`) + quiet level (`q=0/1/2`) | 0.8.130 | ✅ |
| Z-index 2 層描画 (`z<0` テキスト下 / `z>=0` テキスト上) | 0.8.130 | ✅ |
| Animation frames (`a=a`) | 未対応 | 段階 5 候補 |
| Virtual placement + Unicode placeholder (`a=p,U=1`) | 未対応 | 段階 5 候補 |
| File / temp / shm 転送 (`t=f`/`t=t`/`t=s`) | 未対応 | セキュリティ要検討 |

### 経緯 (依頼書とのやり取り)

1. 初回の依頼書 (`/tmp/terminal-app-issues.md`) は 5 件: Ctrl 制御キー不達 / Tab 不達 /
   Enter → LF / SGR mouse 漏れ / 異常終了時の復旧。
2. 実機ログ (`/tmp/loggit-events.log`) を確認したところ、**Tab と SGR mouse は依頼書の誤診**
   (実際は届いていた)。`/tmp/check-raw.sh` を z2root タブで走らせた結果、**z2term の PTY layer
   は完全に健全** (`stty raw -ixon -icrnl` 後の Enter → `0d`、Ctrl+Q → `11` が素通り) で、
   Enter→LF と Ctrl+Q 不達は **TUI 側で `enable_raw_mode` が完全に効いていない** ことが真因
   と判定。 返信文面は `/tmp/terminal-app-issues-reply.md` に保存して TUI 側へ差し戻し。
3. 後続の指摘 (mouse SGR 漏れ / cursor 行頭飛び / kitty graphics 漏れ) は **出力 (ANSI)
   パーサ側** の話で、`processEscape` の `else` で DCS/APC/PM/SOS を未対応のまま GROUND に
   戻していたため本文が画面に漏れていたのが共通真因。`State.STRING` を追加して
   `ST (ESC \)` / `BEL` まで本文を読み捨てる経路を入れたのが 0.8.127。
4. その先「Kitty graphics を描画まで」の要望を受けて、0.8.128 → 0.8.130 を段階的に積んだ
   (各段階 1 コミット、 docs と版数を同時更新)。

### 次セッションへの残作業 (優先度順)

1. **実機検証** (高): 0.8.127〜0.8.130 はビルド・unit test までしか確認していない。 実機で
   以下を実走する。
   - `printf '\e_Gi=1,a=q,t=d,f=100,s=1,v=1;\e\\'` で `\e_Gi=1;OK\e\\` が PTY に返るか
   - `chafa --format kitty <png>` 等で `a=T,f=100,t=d` の単発画像が出るか
   - 同じ画像を `a=p,i=1` で別位置に再配置できるか
   - `a=d,d=I,I=1` で消えるか / `a=d,d=A` で全消去できるか
   - `z<0` / `z>=0` で文字との上下が変わるか
   - 0.8.127 の DCS/APC 吸収で「描画しない画像 protocol を流したときに画面汚染しないか」
2. **release ビルド + GitHub Release 公開** (中): full / foss の両 release を 0.8.130 で
   作って `v0.8.130-alpha` として公開。 手順は MEMORY `reference_github_release_on_device.md`
   参照 (gh デバイス認証は 502 多いので `GH_TOKEN` env が確実)。 release は **1 フレーバー
   ずつ** ビルドする (MEMORY `feedback_build_one_flavor_at_a_time.md`)。
3. **段階 5 (任意・低)**: TUI 側で要望が出れば。
   - Animation frames (`a=a,r=N,...`) — 大規模
   - Virtual placement + Unicode placeholder (`a=p,U=1`) — テキストレイアウト連動
   - File 転送 (`t=f`/`t=t`/`t=s`) — z2root 環境下のセキュリティ要検討

### 関連ファイル

- 新規: `app/src/main/java/com/zerotoship/z2term/emulator/KittyGraphicsParser.kt` /
  `TerminalImage.kt`
- 改修: `TerminalEmulator.kt` (`State.STRING` 追加 / Kitty parser 連携 /
  `setCellMetricsHint` / query 応答送信) / `TerminalRow.kt` (`images: MutableList<>` 化 /
  invalidate ロジック) / `TerminalBuffer.kt` (画像キャッシュ + id 別削除) /
  `TerminalRenderer.kt` (Pass 2.7 + Pass 3.5 の 2 層 image 描画 + `drawImagePlacement` ヘルパ)
- テスト: `StringStateAbsorbTest.kt` / `KittyGraphicsParserTest.kt`
- docs: `docs/ja/DESIGN-SPEC.md` / `docs/en/DESIGN-SPEC.md` の §4.5 と変更履歴に 0.8.127〜0.8.130 を追記
- 検証物 (リポ外): `/tmp/terminal-app-issues.md` (依頼書) / `/tmp/terminal-app-issues-reply.md`
  (返信文面) / `/tmp/loggit-events.log` (TUI 側 event 実機ログ) / `/tmp/check-raw.sh`
  (z2root の raw mode 確認スクリプト) / `/tmp/run-loggit.sh` (TUI 起動ラッパ、依頼書同梱)

---

## 引き継ぎサマリ — 0.8.118 全完 (push 済・full/foss release 済・GitHub Release 公開済・実機 e2e 完)

このセッションの成果が全て完了状態。次セッションは新規修正からスタートで OK。

- **git**: `bb1ea88` (0.8.118) と `3269729` (0.8.117) を origin/main へ push 済。`git status` クリーン。
- **APK ビルド済 (両 flavor・release・同鍵 `CN=Z2Term`)**:
  - full: `app/build/outputs/apk/full/release/app-full-release.apk` (195 MB・versionCode 126)
  - foss: `app/build/outputs/apk/foss/release/app-foss-release.apk` (20.8 MB・proot/talloc/alpine 非同梱)
  - バックアップ: `/home/orgson/z2-apk-backup/app-full-release-0.8.118-bb1ea88-z2root.apk` (md5 `c29969efac7b…`) / `app-foss-release-0.8.118-bb1ea88.apk` (md5 `d17c1f48a818…`) + `.md5` 添付
- **GitHub Release**: `v0.8.118-alpha` を **Latest** で公開済 (`prerelease:false` / `draft:false`)。両 APK 添付 (state=uploaded)。URL: <https://github.com/orgsonai/z2term/releases/tag/v0.8.118-alpha>
- **実機検証 (Arch z2root タブ・SSH 経由)**: 0.8.111〜0.8.118 全部 OK。`pgrep bash`/`pidof bash`/`pgrep dropbear`/`pidof dropbear` 正常返却、`ps -e -o pid,comm,args` の comm 列が `bash`/`dropbear`/`zsh` 等正常 (z2root tracer 自身のみ `libz2root.so` = 想定通り)、`top -bn1` COMMAND 列同様、`/proc/<pid>/stat` 第2列 `(bash)`/`(dropbear)`/`(zsh)` 短縮済み、`go version`/`adb start-server` 起動成功、scrollback 上方向スワイプ (0.8.116) 体感 OK。
- **adb 環境**: PC adb から `adb -s ZY32LNFX2B install -r <APK>` で同鍵 in-place 更新。**APK 上書き直後は dropbear が落ちる**ので、本体 UI でタブ開き直し or sshd 再起動が必要 (ユーザー操作)。
- **gh 認証**: 既に keyring 経由で済んでいる (`gh auth status` 即 OK)。次セッションでトークン受け取りなしで `gh release create` 可。

### 次セッションへの残作業 (優先度順)
1. **非 readfree 経路 (`note_proc_open`) に同じ dirfd 解決を入れる (低・実害なし)**: 0.8.118 は readfree (デフォルト) のみ修正。`Z2ROOT_NO_READFREE=1` 実行時のみ非対応。対称化のため別セッションで `note_proc_open` 側 (`z2root.c:1494` 周辺) にも同じ「dirfd 相対 openat → `/proc/<self>/fd/<dirfd>` readlink で絶対化」を入れる。1 コミット・version bump で完結。
2. **foss APK の実機 e2e (任意・低)**: full と同コード経路なので確認したのと same と判断したが、F-Droid 公開を意識するなら別途確認しても良い。
3. **新規要望/バグ報告待ち**

## 2026-06-21 追記 — z2root: try_subst_proc_open に dirfd 相対 openat の解決を追加 0.8.118

0.8.117 を実機導入後 SSH で再検証したところ:

- ✅ **`cat /proc/<pid>/stat`** (絶対パス openat) では第2列が `(bash)`/`(zsh)`/`(dropbear)` で**末尾空白なしで詰まっている**=0.8.117 の左シフト修正は正しく動作
- ✅ **`cat /proc/$$/comm`/`head -1 /proc/$$/status`** も同様に詰まっている
- ❌ **procps-ng の `pgrep bash`/`pidof bash`/`ps -o comm`/`top`** は依然として失敗 / `libz2root.so` 表示のまま

### 真因
procps-ng の `readproctab2` は `opendir("/proc")` で得た dirfd を使い、各 pid に対し `openat(dirfd, "<pid>/stat", ...)` のような**相対パス openat**で /proc 配下を読む (効率化)。`try_subst_proc_open` は openat の pathname 引数だけを `proc_open_kind` に渡しており、判定は **`strncmp(p, "/proc/", 6) == 0`** の絶対パス始まりのみ ⇒ 相対パス `"<pid>/stat"` は非対象扱い ⇒ temp 差し替え非発火 ⇒ 元の `(libz2root.so)` をホスト /proc から素通り。`cat` は **`openat(AT_FDCWD, "/proc/<pid>/stat", ...)`** で絶対パス openat なので正しく差し替えされていた = 見え方が経路によって割れていた。

### 修正 (`z2root.c`)
`try_subst_proc_open` の冒頭に dirfd 解決ロジックを追加:
- `raw[0] != '/'` かつ `dirfd != AT_FDCWD` のとき、`/proc/<self_pid>/fd/<dirfd>` を `readlink` して dirfd の指すホスト実パス (`/proc` 想定) を取得
- dirpath が `/proc` または `/proc/...` の場合のみ `<dirpath>/<raw>` を組み立てて `proc_open_kind` 判定 / 以降の差し替え処理へ
- それ以外の dirfd 相対 openat は素通し (誤検知ゼロ)
- 差し替え成功時、元が dirfd 相対だった場合は `regs[0]` を `AT_FDCWD` に倒す (tmp の絶対パスが procfd 経由と認識される事故を防止)

### バージョン
**0.8.118-alpha (versionCode 126)**。`libz2root.so` 再ビルド済 (NDK r28c)。実機検証は新 APK 導入後 `pgrep bash`/`pidof bash`/`top -bn1`/`ps -o pid,comm,args` の COMMAND/comm 列が argv0 basename になることを確認。

### 残課題
- 非 readfree 経路 (`note_proc_open`) には同じ dirfd 解決を入れていない。デフォルトは readfree なので実害なし。`Z2ROOT_NO_READFREE=1` で実行する場合は非対応。

---

## 2026-06-21 追記 — z2root: /proc/<pid>/stat と status の comm 書換を「短縮時は左シフトで詰める」版に 0.8.117

ユーザーの SSH 経由実機検証（Arch z2root タブ）で 0.8.111〜0.8.113 の検証中に発見した残バグへの対応。

### 検証結果サマリ（0.8.116-alpha(124) の実機）
- ✅ **0.8.111 (`/proc/self/exe`)**: 完全成功。`readlink /proc/self/exe` がゲスト視点 (`/usr/bin/readlink` 等) に書換、**`go version go1.18 gccgo` 起動成功**、**`adb start-server` → `daemon started successfully`**（旧バグ `execl returned -1: No such file or directory` が完全消滅）。
- 🟡 **0.8.112 (`/proc/<pid>/{comm,status:Name}`)**: comm/Name 自体は他pid でも復元成功（`/proc/<pid>/comm` = `bash`/`zsh`/`dropbear`、`status:Name` も同様）。**だが `pgrep bash` / `pidof bash` が何もヒットしない**。
- 🟡 **0.8.113 (`/proc/<pid>/stat` 第2列)**: 書換は他pid でも成功 (`(zsh         )` / `(dropbear    )` / `(bash        )` のように argv0 basename へ)。**だが `top -bn1` の COMMAND 列が `libz2root+` のように切られて表示**。

### 真因
0.8.112/0.8.113 の `fake_stat_comm`/`fake_status_name` は**長さ保存で末尾を空白パディング**する実装だった (後続フィールドのオフセットを崩さないため)。しかし procps-ng の `pgrep`/`pidof` は **comm を末尾空白付きで完全一致比較**するため `bash<spaces>` と `bash` が一致しない＝ヒットしない。`top` も `/proc/<pid>/stat` 系統で取得した文字列を表示幅で切るため `libz2root+` のように先頭が出る。

### 修正 (`z2root.c`)
- `fake_stat_comm` / `fake_status_name` の戻り値を `void` → `size_t` (新しいバッファ長) に変更。新 name が元より短い場合は閉じ `)` 以降 (stat) / 次行 (status) を `memmove` で**左シフトしてバッファ全体を短縮**する。
- 呼び出し元 2 経路で長さ反映:
  - **readfree 経路 (`try_subst_proc_open`)**: 戻り値で `total` を更新し temp ファイルに新しい長さで書き出す（temp なので長さ自由）。
  - **非 readfree 経路 (`fake_proc_on_read`)**: `write_tracee_mem` で新しい長さを書き戻し後、`regs.regs[0]` (read 戻り値) を新しい長さに更新。
- status は **`fake_status_name` を `fake_status_buf` より先に呼ぶ**順番に直し、Uid/Gid/Cap*/Groups の length 保存書換が短縮後のバッファに対して走るようにした (順序逆転で旧挙動から無害な改善)。

### 期待される実機効果
- `pgrep bash` / `pgrep dropbear` / `pidof bash` がヒット
- `top -bn1` の COMMAND 列が `bash` / `dropbear` / `zsh` 等で正常表示
- `ps -ef` の COMMAND 列が argv0 basename
- 0.8.111 (self/exe) は既に動いていた分はそのまま維持

### バージョン
**0.8.117-alpha (versionCode 125)**。`scripts/build-z2root.sh` で `libz2root.so` を再ビルド済 (NDK r28c)。`libz2accept.so` も同じくリビルド。`.so` は gitignore (commit には乗らない)。実機 e2e は新 APK 導入後、SSH 経由で `pgrep`/`pidof`/`top`/`ps -ef` の再検証 → 全部正常になれば 0.8.111/112/113/117 を全部クローズ。

### git の状態
0.8.117 のコード (`z2root.c`) + version bump (`app/build.gradle.kts`) + docs (README/README.ja, DESIGN-SPEC ja+en, 本ファイル) を 1 コミットにまとめる予定。ビルド・Release は次セッション。

---

## 2026-06-21 追記 — scrollback 中の上方向スワイプを「最新側へ戻る」操作として吸収 0.8.116

0.8.115 実機確認後のユーザー報告「上下スクロール動くようになった。ただ下スクロール (=次へ) が**どの位置からでも勝手に一番下まで行ってしまう**。一度上に行ったらもう一番下しか行けない」への対応。

- **真因**: 0.8.115 は `mouseEnabled` 中の上方向スワイプを常に wheel-down として PTY 送信していたが、[`TerminalSession.writeBytes`](../app/src/main/java/com/zerotoship/z2term/core/TerminalSession.kt) の冒頭が `_scrollOffset.value = 0` (「ユーザー入力時は必ず最下行へジャンプ。スクロールバック中に typing 結果が見えなくなる事故を防ぐ」)。これが wheel イベント送信のたびに発火＝scrollback で過去ログを見ていても 1 ノッチ目で **scrollOffset が 0 に強制リセット＝視点が一気に最下端へジャンプ**するため、「scrollback で過去を見ている → 少し下方向 (=次へ) に動かしたい → 即最下端まで飛ぶ」体験になっていた。一度最下端に飛ぶと scrollback で戻すしか手段がない＝「もう一番下しか行けない」状態。
- **修正 ([`TerminalInputView.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/input/TerminalInputView.kt))**: `onScroll` の wheel 送信ゲートに `&& sess.scrollOffset.value == 0` を追加。scrollback で過去を見ている (scrollOffset > 0) 間は上方向も既存の scrollback 経路 (`scrollAccumDy` で吸収 → `sess.scrollBy(-rowDelta)`) に倒し、scrollback が 0 になった瞬間から wheel-down 送信が始まる。`onFling` も同じ条件分岐 (`mouseEnabled && velocityY < 0f && sess.scrollOffset.value == 0` のときだけ no-op) で、scrollback 中の上方向フリングは scrollback 慣性スクロールで最新側へ戻れる。`writeBytes` の `scrollOffset = 0` リセット動作自体は他の入力経路 (キー入力 / コマンド貼付等) で正しく必要なので触らない。
- **docs**: README/README.ja を 0.8.116 に、DESIGN-SPEC(ja/en) のマウスレポート節を「方向 + scrollback 位置で振り分け」に書き換え＋末尾変更履歴に 0.8.116 エントリ。
- ⚠️ **実機 e2e 未** (dev 環境では UI 検証不可)。新 APK で ① SGR マウスレポート対応の TUI で scrollback >0 の状態 (過去ログを見ている途中) から指を上方向スワイプ → 一気に最下端へ飛ばずスムーズに最新側へ戻ること、② 完全に最下端 (scrollOffset==0) に着いたあと更に指を上 → wheel-down で TUI の次ページが進むこと、③ 指を下方向は従来通り scrollback で過去をたぐれること、を確認すること。

### 2026-06-21 続き — 0.8.116 はコミット済 (未 push) / ビルド・Release 未

セッション末の引き継ぎ依頼で中断したため、0.8.116 は **コード/テスト/docs を 1 コミット (`4fec6b5`) にまとめた状態**で停止している。次セッションへの引き継ぎ:

- **git の状態**: `4fec6b5` (0.8.116, このセッションの修正) と `35b598c` (0.8.115) はローカル `main` のみで origin/main にも乗っている → **`git push` 確認**: 実は前回 0.8.115 を push した時点で本ブランチは origin/main 直前まで一致していたはず。0.8.116 コミットは **未 push**。`git status` で確認してから push。
- **ビルド未実施**: full/foss release ともビルドしていない。前回パターン (片方ずつ) でやる場合、まず stale 対策の `rm -rf app/build/intermediates/{merged_native_libs,merged_jni_libs,stripped_native_libs}/fullRelease app/build/outputs/apk/full/release` → `bash scripts/gw.sh :app:assembleFullRelease` → 検証 → バックアップ。foss も同様 (中間物 rm から)。所要は full ~9 分 / foss ~5 分。
- **GitHub Release 未**: 現在 Latest は `v0.8.115-alpha`。0.8.116 を出す場合は `v0.8.116-alpha` で `--latest`・両 APK 添付・`--prerelease` 無し ([[reference_github_release_on_device]])。gh トークンは前回セッションで使い終わって `/tmp/.ghtok` は削除済 → 次セッションは再度ユーザーからトークンを受け取って `GH_TOKEN` env 経由で叩く。
- **0.8.116 で native (`.so`) は無変更**: 0.8.115 と同じ `libz2root.so` (.text sha256 = `30b2a2f2a5bde9e9b98310cf77a1220ecf7c7ae9c1d29ca44270a41c6963722c`) が同梱される想定。再ビルド後に **APK 内 .so と src .so の `.text` sha256 一致**を必ず確認 ([[project_z2root_stale_apk_jnilibs]])。
- **テスト**: `bash scripts/gw.sh :app:compileFullDebugKotlin :app:testFullDebugUnitTest` 緑 (BUILD SUCCESSFUL, [`MouseEncodeTest`](../app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt) 含む全通過)。

### 実機 e2e の残（次セッションで本体 UI 導入後に確認）
0.8.116 の full release APK 1 本で **0.8.111〜0.8.116 をまとめて検証可能**。以下を優先順で:

1. **0.8.116 (UI/Kotlin)**: scrollback 中の上方向スワイプが最下端へ飛ばずスムーズに最新側へ戻ること／最下端着地後の指上で wheel-down が TUI に届くこと／指下スワイプは scrollback で過去をたぐれること
2. **0.8.115/0.8.114 (UI/Kotlin)**: SGR マウスレポート対応の TUI でタップスクロールが効くこと（0.8.116 に包括される）
3. **0.8.113 (z2root native)**: `ps -ef` / `top` のラベルが `(libz2root.so)` でなく argv0 basename になる
4. **0.8.112 (z2root native)**: `pgrep <name>` / `pidof` / `top` の名前列 (`/proc/<pid>/cmdline` / `comm` / `status:Name`) が正常になる
5. **0.8.111 (z2root native)**: `go version` / `go build` が起動／adb の `execl(自パス)` 系再起動が ENOENT で落ちない

その他 IME 系 (0.8.109/104/103/102 等) も実機未検証だが、これらは独立した経路なので別途。

## 2026-06-21 追記 — スワイプを方向で振り分け・下方向は scrollback フォールバック 0.8.115

ユーザー報告「SGR マウスレポート対応の TUI でタップスクロールで読み込み出来るようになったが、逆に上にスクロール (=前ページに戻る) が出来ない」への対応。0.8.114 の後追い修正で同日リリース。

- **真因**: 0.8.114 は `mouseEnabled=true` の間、全方向のスワイプを wheel イベントに変換していた。しかし **多くの読み物系 TUI は wheel-up (`evScrollUp` 相当) を意図的に無視して端末の scrollback に任せる**設計を採っている (典型的なソース実装: `case evScrollUp: // 既に印字済みの内容へ戻る操作は端末のスクロールバックに任せる。ここでは新規行を流さない`)。結果 0.8.114 では上方向スワイプ＝過去を見たい操作で「TUI も無視・scrollback も奪われた」状態 = 何も動かない、になっていた。
- **修正 ([`TerminalInputView.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/input/TerminalInputView.kt))**: `onScroll` で `mouseEnabled && distanceY > 0f` (指が上 = 次へ) のみ `sendMouseWheelFromSwipe` を通し wheel-down (button 65) を PTY 送信、それ以外 (下方向、または mouseEnabled=false) は既存の scrollback ロジックへ。`onFling` も `velocityY < 0f` (上振り) のみ no-op、`velocityY > 0f` (下振り) は scrollback 慣性スクロールを許可。`sendMouseWheelFromSwipe` は wheel-down 専用に簡略化 (button 固定、notch は正のみ、`distanceY <= 0f` は早期 return)。
- **回帰テスト追加 ([`MouseEncodeTest.kt`](../app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt))**: SGR/URXVT/LEGACY 各エンコーディングの出力 (先頭 ESC・button・terminator) と DECSET `?1000h`+`?1006h` の状態遷移を JVM 単体テストで固定。今回のデバッグ中に「`encodeMouseEvent` の SGR 出力に ESC が抜けているのでは」と一瞬疑った (Read tool で ESC 文字が表示されず混乱した) 経験から、出力バイト列を assertArrayEquals で完全固定して **ESC の有無を将来確実に検出**できるようにした。
- **教訓 (`.kt` 内の ESC リテラル)**: Kotlin 文字列リテラルに ASCII ESC (0x1B) をそのまま埋め込んでも、Read tool やエディタによっては不可視で、目視では分からない。検証は `sed -n '<line>p' <file> | cat -A` で **`^[` 表示の有無で確認**するのが確実。
- **docs**: README/README.ja を 0.8.115 に、DESIGN-SPEC(ja/en) のマウスレポート節を「方向で振り分け」説明へ書き換え＋末尾変更履歴に 0.8.115 エントリ。
- ⚠️ **実機 e2e 未** (dev 環境では UI 検証不可)。新 APK で ① SGR マウスレポート対応の TUI で指を上方向スワイプ＝次ページが進む ② 指を下方向スワイプ＝scrollback で過去ログをたぐれる (TUI は反応しない・端末 scrollback が動く) ③ 通常 shell (mouseEnabled=false) は従来通り、を確認すること。

## 2026-06-20 追記 — マウスレポート ON 時のスワイプを TUI 側ホイールへ送る 0.8.114

ユーザー報告「SGR マウスレポート対応の TUI を z2term で開くとタップスクロールが反応せず次のページに進めない」への対応。

- **原因**: TUI が `?1000h`/`?1006h` でマウスレポートを要求していても、`TerminalInputView.onScroll` はそれを見ず常に scrollback 操作（`scrollOffset` 加減）に倒れていた＝TUI 側にホイールイベントが届かないのでページが進まない。タップ＝クリック送信は `sendMouseClick` で既に届いていたが、スワイプ→ホイール変換だけ抜けていた。
- **修正 ([`TerminalInputView.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/input/TerminalInputView.kt))**: `onScroll` の冒頭で `sess.emulator.mouseEnabled` を見て分岐し、新規 `sendMouseWheelFromSwipe(x, y, distanceY, sess)` を呼ぶ。中身は: ① `mouseWheelAccumDy += distanceY` を 40px (`MOUSE_WHEEL_STEP_PX`) で割って notch 数を出す（端数は累積に持ち越し）／② `distanceY > 0`（指が上に動く = ユーザー目線で「次へ進めたい」）を wheel-down (button 65)、逆を wheel-up (button 64) にマップ／③ タップ位置の画面 row/col に丸めて `encodeMouseEvent(button, col0, row0, press=true)` を `abs(notches)` 回送り `sess.writeBytes()` で PTY に流す。あわせて `onFling` を `mouseEnabled` 中は no-op に＝慣性で勝手に scrollback を走らせない。`onDown` で `mouseWheelAccumDy` もリセット。`mouseEnabled=false` の通常タブは 1 文字も変更なし（既存挙動完全保持）。
- **docs**: README/README.ja を 0.8.114 に、DESIGN-SPEC(ja/en) の「マウスレポート」節にスワイプ→ホイールの説明を追記＋末尾変更履歴に 0.8.114 エントリ。HANDBOOK は該当章なし（変更なし）。
- ⚠️ **実機 e2e 未**（dev 環境では UI 検証不可）。新 APK で ① SGR マウスレポート対応の TUI でスワイプで次/前のページに進むこと（複数ノッチぶん長いスワイプで一気に進めること）、② less/man など他の TUI でも同様に効くこと、③ 通常 shell（mouseEnabled=false）の scrollback スワイプ／フリングが従来通り動くこと、を確認すること。

## 2026-06-20 続き — 0.8.114 を full release ビルド済・foss release は中間物 rm まで（次セッション続行）

このセッションの後半でビルド作業を実施。**full release は完了・実機導入待ち**、**foss release は中間物 rm まで実施し、コマンド発行直前で「引き継ぎ」指示により中断**。次セッションは下記の続きから入れる状態。

### full release 0.8.114（完成・バックアップ済）
- **ビルド**: `bash scripts/gw.sh :app:assembleFullRelease` ＝ **BUILD SUCCESSFUL (8m 56s)**。半並列 (`--max-workers=4`)、accept4 シム経由、フリーズなし。stale 対策で `merged_native_libs/fullRelease`・`merged_jni_libs/fullRelease`・`stripped_native_libs/fullRelease`・`outputs/apk/full/release` を事前 rm 済。
- **APK**: `app/build/outputs/apk/full/release/app-full-release.apk`（195MB）。
- **検証（全パス）**:
  1. 版数: `output-metadata.json` で **versionCode 122 / versionName 0.8.114-alpha**
  2. 署名: `apksigner verify --print-certs` ＝ `Signer #1 certificate DN: CN=Z2Term, O=ZeroToShip, C=JP`（過去鍵と同一＝更新インストール可）exit=0
  3. **stale でない確証（決定的）**: APK 内 `libz2root.so` の `.text` セクション sha256 ＝ src `app/src/main/jniLibs/arm64-v8a/libz2root.so` の `.text` セクション sha256 ＝ `30b2a2f2a5bde9e9b98310cf77a1220ecf7c7ae9c1d29ca44270a41c6963722c`（完全一致）。事前 rm が効いた証拠＝0.8.111〜0.8.113 の native 変更が APK に乗っている
  4. `.so` 同梱: libz2root/libz2accept/libz2term/libproot/libtalloc/libc++_shared、assets/alpine-minirootfs-aarch64.tgz（175MB）も同梱
  5. src C ソース照合: `app/src/main/cpp/z2root/z2root.c` に `PROC_FD_STAT`(0.8.113)・`PROC_FD_COMM`(0.8.112)・`host_path_for`+`proc_self/exe` 復元(0.8.111)・`proc_comm[TASK_COMM_LEN]` 等の文字列を grep 確認。bash アクセス可能な `apksigner` は `/root/android-sdk/build-tools/35.0.0/apksigner`（PATH 通っていないので絶対指定で叩く）
- **バックアップ**: `/root/z2-apk-backup/app-full-release-0.8.114-4caea36-z2root.apk`（md5 `447119d084b1ac27bae25b43b7e41a9a`・`.md5` 添付済）。

### foss release 0.8.114（事前 rm まで・gradle 未実行）
- **実施済**: `app/build/intermediates/{merged_native_libs,merged_jni_libs,stripped_native_libs}/fossRelease` と `app/build/outputs/apk/foss/release` を rm。
- **未実行**: `bash scripts/gw.sh :app:assembleFossRelease`。次セッションで**そのまま流すだけ**で良い（事前 rm 不要、もう済んでいる）。所要時間は full と同程度（8〜10 分目安）。
- **期待値**: APK サイズ ~21MB（0.8.90 foss release が 20.9MB だったので近い数字）、versionCode 122 / versionName **`0.8.114-alpha-foss`**（`versionNameSuffix = "-foss"`）、`.so` は **proot/talloc/alpine いずれも 0**（`onlyFullFlavor` 経路で除外）＝ z2root 系のみ。`OssComponents` の foss 一覧と `LicensesScreen.kt` の文言は 0.8.114 で**未変更**なので素直に通る想定。
- **検証手順は full と同じ**（版数・署名・`.so` 同梱・stale 検証）。検証コマンド集はこのセクションの「検証（全パス）」を `app/build/outputs/apk/foss/release/app-foss-release.apk` に置き換えて流せばよい。
- バックアップ先候補: `/root/z2-apk-backup/app-foss-release-0.8.114-4caea36.apk`（+`.md5`）。命名は full のパターンに合わせ `-z2root` サフィックスは付けない（既存 foss バックアップに合わせる）。

### 残作業（優先順）
1. **foss release 0.8.114 ビルド**（次セッション即着手・コマンド 1 本）
2. **GitHub Release 公開**（`v0.8.114-alpha`）: 既存運用に従い `--prerelease` 付けず `--latest` で作成→full/foss APK 添付（[[reference_github_release_on_device]]）。前回 0.8.90 の release ノートを雛形に
3. **実機 e2e**（dev 環境では不可・ユーザー端末操作が必須）:
   - 0.8.114: SGR マウスレポート対応の TUI でスワイプ＝次/前ページ送り、長いスワイプで多行送り、通常 shell の scrollback スワイプ・フリングが従来通り
   - 0.8.113: `ps -ef` / `pgrep` / `pidof` / `top` のラベルが argv0 basename に（`stat` field 2 含めて libz2root.so でない）
   - 0.8.112: `/proc/<pid>/cmdline` / `comm` / `status:Name` の復元（procps と busybox 双方）
   - 0.8.111: `go version` / `go build` が起動／adb `execl(自パス)` 系の再起動成功

## 2026-06-17 追記 — 学習履歴ベースの予測変換に作り直し＋予測確定は実際の読みで学習 0.8.109

ユーザー要望: 日本語予測変換を「本物」にする。打った読みで始まる学習済み語句を学習履歴から絞り込んで表示する（フィルタ的な予測変換）。また予測を確定したとき、打った接頭辞でなく語句の実際の読みで学習する。

- **候補順の変更** ([`KanaKanjiConverter.convertFlexible`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KanaKanjiConverter.kt)): 完全一致履歴 → **前方一致履歴（予測変換）** → Viterbi → 完全一致/送り仮名 → 辞書前方一致、の順へ。打った読みで始まる学習済み語句を文まるごと変換より先に候補先頭へ出す。`allowPrediction=false`（後続 tail のある先頭ブロック）では従来どおり抑止。
- **予測確定の学習見出しを実際の読みに** ([`ComposingState.commit`]): 予測候補を選んだら `record` の見出しを打った接頭辞でなく語句の実際の読みにする。`ImeHistoryStore.predictHistoryWithReading` ＋ `KkcConverter.predictionReadingMap`（履歴優先・辞書見出しで補完）で表層→読みを逆引きし、`refreshPredict` で `predictionReadings` に控えて `commit` が参照。接頭辞だけの不正な履歴見出しが残らず、次回も同じ読みで予測に再利用される。前方一致予測を選んだ場合は 1 文節に収まらないので `committedRun`（結合ブロック学習）には積まない。
- **長文ブロック変換は未変更**: `commitFull` / 自動ブロック分割 / `fullPrediction` の組み立ては触っていない。`fullPrediction` は tail があるときだけ作られ、その場合 `allowPrediction=false` で新しい前方一致予測は候補に入らない＝先頭ブロック候補順・薄緑ピルは従来どおり。
- **docs**: DESIGN-SPEC(ja/en)・HANDBOOK(ja/en) に予測変換を追記。あわせて IME 変更履歴中の詳細な「読み|表層」参考例を除去し対応内容だけに簡潔化（ユーザー方針）。
- **検証**: `gw.sh :app:testFossDebugUnitTest` 緑（`BlockLearningTest`/`KkcEvalTest` 回帰ガード含む）。`assembleFullDebug` BUILD SUCCESSFUL。実機実打でユーザーが「良い感じ」と確認済み。`assembleFullRelease` をビルド中。

## 2026-06-16 追記 — 予測変換: 学習した平仮名が長文予測に反映されない件を修正 0.8.104

ユーザー報告: 「してください」と打つと一度も使ったことのない漢字「仕手下さい」と長文変換され、何度平仮名で確定しても直らない。回数優先度が漢字だけで平仮名に効いていないのでは、との指摘。

- **原因**: 長文一括予測 ([`KanaKanjiConverter.refreshPredict`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KanaKanjiConverter.kt) の `fullPrediction`) の先頭ブロック表層を `candidates.firstOrNull()` から取っていた。`candidates` は [`buildList`] が「読みと同一表層＝平仮名」を `out.remove(reading)` で除外するため、学習履歴 ([`ImeHistoryStore.historyFor`]) が平仮名「して」を 1 位で返しても候補列から消え、`candidates.first()` が常に最上位の漢字「仕手」になっていた。結果、何度平仮名を確定しても**長文予測の先頭が漢字のまま**＝頻度学習が漢字候補の中だけで効き、平仮名が土俵に乗らない状態だった。
- **修正（学習反映のみ・低リスク）**: `fullPrediction` の先頭/末尾表層を**学習履歴（平仮名を含む頻度 1 位）優先**にした。先頭は `ImeHistoryStore.historyFor(splitHead, 1)` を最優先、無ければ従来どおり `candidates.first()`。末尾も全体一致の履歴があれば優先、無ければ従来の Viterbi 1-best。さらに `commitFull` の学習内訳 (`fullPredictionBlocks`) を**表示中の表層と一致**させ、平仮名を表示しているのに裏で bunsetsu の漢字を再学習して count を増やす取りこぼしを防いだ。
- 辞書の初期コスト・`KANA_PREFERRED`・ラティスは触っていない（他変換への退行リスク回避）。学習ゼロの初回は従来どおりの既定（珍しい漢字が出ることはある）だが、平仮名を数回確定すれば長文予測も平仮名へ倒れる。
- ⚠️ **実機確認は未**（dev 環境では UI 検証不可）。新 APK で「してください」を平仮名で数回確定後、長文予測が「してください」になることを確認すること。

## 2026-06-16 追記 — 端末ツールバーの cwd 表示削除＋ボタン右詰め 0.8.104

ユーザー報告: 端末上部ツールバーで「右側が空いているのにボタンが横スクロールする／カレントディレクトリ表示でボタン枠が狭い」。

- **原因**: [`TerminalScreen.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/TerminalScreen.kt) の terminal TopBar が `osLabel` / `cwd(weight 1f, fill=false)` / `ボタン群Box(weight 1f)` の構成で、`cwd` と `ボタンBox` が残り幅を約半々に分け合うため、ボタン Box が画面の約半分しか幅をもらえず 7 ボタンが入らず `horizontalScroll` が発火。一方 cwd が短いと余白が空き「右が空いてるのにスクロール」状態になっていた。
- **修正**: カレントディレクトリ表示（`cwd` Text と `session.cwd.collectAsState()`）を削除。これでボタン Box が残り幅を全取りし、`osLabel`（左詰め）＋ボタン群 `Box(weight 1f, CenterEnd)`（右詰め）に。通常解像度では 7 ボタンが収まり横スクロール解消、低解像度時のみ従来どおりスクロールで全ボタン到達可。
- `session.cwd` flow 自体は `SessionManager` のセッション永続化で使用中のため残置（UI 表示のみ削除）。
- ⚠️ **実機 UI 確認は未**（dev 環境では不可）。新 APK で「ボタンが OS 名の右に右詰めで全部見える／不要なスクロールが無い」ことを確認すること。

## 2026-06-16 追記 — 初回オープン時のスクロール飛び/隙間／タブ切替後の内蔵キーボード無反応の 2 件修正 0.8.103

ユーザー報告 2 件への対応。JVM 単体テスト（`gw.sh :app:testFullDebugUnitTest`）で回帰なしを確認。実機 UI の最終確認は未（dev 環境では不可）。

- **① 初回オープン時に末端が上下に張り付かず、下端付近スクロールで最下プロンプトが中央へ瞬間移動しキーボードとの間に隙間（上スクロールも同様、ピンチで解消）**: 原因は [`TerminalRenderer.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/TerminalRenderer.kt) の `bottomAbsRow` が scrollOffset==0 では表示行 `canvasRows`、scroll 中では emulator の `buf.rows` と**異なる基準**を使っていたこと。初回オープンで両者が未同期（120ms デバウンスの resize 前）の間、scrollOffset 0→1 の瞬間に `buf.rows - canvasRows` 行ぶん下端が飛び、隙間が出ていた。ピンチは即時 resize で同期するため解消していた。`bottomAtRest = cursorAbsRow.coerceAtLeast(scrollbackSize + canvasRows - 1)` を定義し `bottomAbsRow = bottomAtRest - scrollOffset` に**一本化**＝resize 同期前でも自己整合させ飛び/隙間を根治。120ms デバウンス自体は PTY winsize 用なので維持。
- **② 端末タブを切り替えると内蔵キーボードの入力がターミナルへ届かなくなる（キーは反応するが入力されない／あ⇄ABC 切替で復帰）**: あ⇄ABC は内蔵キーボードのサブツリーを作り直す操作で、これで直る＝バグはキーボードサブツリー内に残る `pointerInput`/`remember` 状態が前タブの composing/session を掴んだまま残ることが確定的。[`TerminalScreen.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/TerminalScreen.kt) の 3 つのキーボード描画箇所（下部 `TerminalKeyboard`＋左右 `SideKeyboardColumn`）を `key(active.id) { ... }` で包み、タブ切替時にサブツリーを作り直し＝あ⇄ABC 手動切替と同じ復帰を自動化。`composing` は元々 `remember(active.id)` で再生成済みなので新 session に正しく束縛される。
- ⚠️ **実機 e2e 未**: ① 初回オープン直後の上下端張り付き・スクロールで飛び/隙間が出ないこと、② タブ往復後に内蔵キーボード（ASCII/かな）でそのまま入力できること、を新 APK で確認すること。
- **ビルド済み**: `gw.sh :app:assembleFullRelease`＝BUILD SUCCESSFUL（9m08s）。署名済み APK = `app/build/outputs/apk/full/release/app-full-release.apk`（約 186MB / versionCode 111）。本体 UI からインストールして上記 ① ② を実機確認する。
- **残（このセッション未着手）**: foss release ビルドと GitHub Release は未。

## 2026-06-16 追記 — ツールバー潰れ／IME 過剰漢字化／予測被り／折り返しコピーの 4 件修正 0.8.102

ユーザー報告 4 件への対応。すべて JVM 単体テスト（`gw.sh :app:testFullDebugUnitTest`＝BUILD SUCCESSFUL・KkcEvalTest 回帰ガード含め全通過）で確認。実機 UI/IME/コピーの最終確認は未（dev 環境では不可）。

- **① 低解像度端末でツールバーが潰れて押せない**: [`TerminalScreen.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/TerminalScreen.kt) の terminal/GUI 両 TopBar で、固定幅 7 ボタンが狭画面で溢れて潰れていた。`ReorderableToolbar` に `modifier` 引数を追加し、`Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd)` で右寄せ＋ `Modifier.horizontalScroll(rememberScrollState())` を付与＝収まらない分は横スクロールで全ボタン到達可に。
- **② 平仮名が使わない漢字へ過剰変換（例: もんだいありません→問題在りません）**: IPADIC が存在/補助動詞の漢字表層（在り/有り/居る/下さい 等）に平仮名より低コストを与えるのが原因。[`KkcConverter.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcConverter.kt) に**読みでゲートした表層ペナルティ** `KANA_PREFERRED`（+`KANA_PREFERRED_PENALTY=4000`）を追加。`loadFromStreams` で「読みが該当かつ表層先頭が指定漢字」の語に加点＝あり/いる/なる/する/ください/いただく/こと/もの/ところ/ため/ほど 系を平仮名優先に倒す。**読みで絞るので同字別読みの複合語（有名・仕事・時間）は壊さない**（`AuxKanaPenaltyTest` で保証）。
- **③ 短ブロック学習で長文予測が被る（してください→して下さい下さい）**: [`KanaKanjiConverter.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KanaKanjiConverter.kt) `convertFlexible` に `allowPrediction: Boolean = true` を追加し、分割変換で**末尾ブロックが続くとき（`hasTail`）は非末尾ブロックの prefix 予測補完を抑止**（学習済み「してください→して下さい」が頭ブロック候補に出て末尾「ください→下さい」と連結する経路を断つ）。`fullPrediction` 構築にも `headSurface.endsWith(tailSurface)` の防御的 dedup を追加。
- **④ ソフト折り返しワンラインのコピーに境界空白が混入**: 全角/CJK/絵文字が右端 1 セルに収まらず次行へ折り返す際の**余りセル（空白）**が `getRangeText` のコピーに混入していた。[`TerminalEmulator.kt`](../app/src/main/java/com/zerotoship/z2term/emulator/TerminalEmulator.kt) `ensureRoomFor` で autowrap 時に余りセルを `wideCont=true`（埋め草）でマークし、コピーから除外。素の ASCII autowrap は元々ワンラインで取れる（`WrapCopyTest` で確認）。ハード改行（`\r\n`）は改行のまま保持。
- **テスト追加**: [`AuxKanaPenaltyTest.kt`](../app/src/test/java/com/zerotoship/z2term/ui/terminal/keyboard/AuxKanaPenaltyTest.kt)（②）／[`WrapCopyTest.kt`](../app/src/test/java/com/zerotoship/z2term/emulator/WrapCopyTest.kt)（④）。
- ⚠️ **実機 e2e 未**: ① 狭画面でツールバー横スクロール・全ボタン押下、② 各種平仮名入力が漢字に化けないこと、③ 学習後の長文予測が被らないこと、④ 折り返しワンラインのコピペがワンラインになること、を新 APK で確認すること。
- **残（このセッション未着手）**: full/foss release ビルドと GitHub Release は未。

## 2026-06-14 追記 — 実行エンジンの「実験的」表記を除去＋フォールバック文言の整理 0.8.94

ユーザー指摘「z2root はもう実験的ではない」「ProotLauncher のコメントに proot へフォールバックと書いてあるが foss だと矛盾」への対応。z2root は実機検証も積み上がり既定運用に乗っているので「実験的」ラベルを落とし、フォールバックの説明を flavor 別に正した。

- **strings (ja/en)**: `settings_section_engine`＝「実行エンジン (実験的)」→「実行エンジン」。`settings_engine_desc`＝「(非 root・実験的。未同梱なら PRoot にフォールバック)」を削り「z2root は非 root の自前 ptrace エンジンです」に簡潔化（foss UI でも出る共有文言なので、foss に無い PRoot フォールバックの記述を除去）。
- **コメント**: `AppSettings.kt` の `ENGINE_Z2ROOT` 説明から「実験的」除去（「foss の既定エンジン」に）。`ProotLauncher.kt:214` 周辺のフォールバック説明を「proot へ倒すのは **full のみ**。foss は倒す先が無く z2root 欠落時は exists チェック(220 行)で停止」と明確化（コードは不変＝両 flavor で正しく動作）。
- **docs**: DESIGN-SPEC(ja/en) の z2root「実験的」3 箇所を除去し、§の z2root フォールバック説明に full 限定の注記を追加。
- **検証**: 要 `gw.sh :app:assembleFossRelease`/`assembleFullRelease`（このセッションでビルドするなら）。文字列/コメントのみで挙動変更なし。

## 2026-06-14 追記 — foss のエンジン選択から PRoot チップを除去 0.8.93

ユーザー指摘「foss は proot を同梱しないのに、バージョン 7 タップのエンジン選択に PRoot が出てしまう」。`ProotLauncher.kt:218` は `BuildConfig.IS_FOSS` のとき設定に関わらず常に z2root を実走するため、foss の PRoot チップは**選んでも z2root に倒れる見せかけ**だった。検討の結果（distro pm で proot を入れる案 = F-Droid 的には可だが必要性薄い）、**選択肢から PRoot を消す**方針に決定。

- **変更**: [`SettingsSheet.kt`](../app/src/main/java/com/zerotoship/z2term/ui/settings/SettingsSheet.kt) のエンジン選択 `engineOptions` で `!BuildConfig.IS_FOSS` のときだけ `ENGINE_PROOT` を add。foss は z2root（＋root 解放時 chroot）のみ表示。既定値が proot のままだと foss でどのチップも選択表示されない問題は、表示用 `selectedEngine` を z2root に読み替えて回避（実走エンジンと一致）。full は従来どおり PRoot 既定。
- **F-Droid メモ**: proot を foss に入れたい場合の正攻法は distro 公式 pm（`apk add proot` 等）でユーザーが導入する形＝同梱でも app DL でもないのでセーフ。app が proot prebuilt を実行時 DL するのは F-Droid 非適合寄りで不可。今回は導入経路は作らず非表示のみ。
- **検証**: `gw.sh :app:compileFossDebugKotlin`＝BUILD SUCCESSFUL。実機 UI（foss で 7 タップ→エンジン節に PRoot が出ないこと）は未確認。

## 2026-06-14 追記 — クラッシュロガー（0.8.86 追加）を撤去 0.8.92

ユーザー判断「クラッシュのトレースログを取るようにしていたが、クラッシュが全く起きないのでもう不要」。間欠的な起動クラッシュ採取用に 0.8.86（`2df97a9`）で入れた仕組みを撤去した。

- **削除**: `CrashLogger.kt` を丸ごと削除し、[`Z2TermApplication.kt`](../app/src/main/java/com/zerotoship/z2term/Z2TermApplication.kt) `onCreate` 冒頭の `CrashLogger.install(this)` 呼び出しを除去。`Thread.setDefaultUncaughtExceptionHandler` は元々既存ハンドラへチェーンする無害な実装だったので、OS のクラッシュ挙動への影響はもとより無い。
- **docs**: README/DESIGN-SPEC には既にクラッシュロガーの記述は無し（後続版で履歴整理済）。本 HANDOFF の旧記述（下方の 0.8.86 追記）は履歴として残す。版数を 0.8.92（versionCode 100）へ。
- **検証**: `gw.sh :app:compileFullDebugKotlin`＝BUILD SUCCESSFUL。`filesDir/crash/` に既に溜まったログは実機側の手動削除でよい（アプリは参照しなくなる）。

## 2026-06-13 追記 — `z2scan` コマンドを新設（自端末/localhost 限定の脆弱性試験）0.8.91・コミット＋push 済（`6260993`）

ユーザー要望「z2term 専用コマンドとして脆弱性試験を追加」への対応。方針確認で **対象＝両方（自己診断＋スキャナ）/ コマンド名＝`z2scan`** に合意。z2term の哲学（自端末・localhost 限定・非侵襲・外部送信なし・distro 公式パッケージのみ＝F-Droid 適合・同梱物ゼロ）に沿わせた 2 本立てで実装。既存 z2*/z2-* ヘルパー注入パターンと完全同型。

- **新規 [`Z2ScanScript.kt`](../app/src/main/java/com/zerotoship/z2term/proot/Z2ScanScript.kt)** の `z2scanScript(lang)`（ja/en）。サブコマンド: ① **`self`**（外部ツール不要の自己診断）＝`/proc/net/tcp{,6}` から全インタフェース待ち受け（`0.0.0.0`/`::`・state `0A`）の TCP LISTEN を検出、`sshd_config` の危険設定（PermitEmptyPasswords/PasswordAuthentication/PermitRootLogin yes）、`~/.ssh`(700)・`authorized_keys`(600) のパーミッション、主要 dir の world-writable、SUID バイナリ（擬似 root 下なので INFO 表示）、`PATH` の空要素/`.` を点検。検出件数>0 で exit 1。② **`setup`**＝`nmap`/`lynis` を `ensure_pkg`（`detect_pm` で apk/apt/pacman 判定＝z2adb パターン流用）で導入。③ **`net [--allow-remote] [対象]`**＝`nmap -sT -Pn`（root 不要）、**既定対象 `127.0.0.1`**、localhost 以外は `--allow-remote` 明示＋警告が無いと拒否（無許可マス標的化を構造的に防ぐ）。④ **`host`**＝lynis（無ければ `self` へフォールバック）。⑤ **`cve`**＝trivy/grype があれば rootfs の既知 CVE スキャン。
- **配線**: [`ProotLauncher.kt`](../app/src/main/java/com/zerotoship/z2term/proot/ProotLauncher.kt) に `ensureZ2ScanScript(rootfs)` を追加し、proot/z2root 経路（`ensureZ2HelpScript` 直後）と chroot 経路の**両方**で呼ぶ。launch 毎に `/usr/local/bin/z2scan` を上書き・UI 言語追従。
- **実装メモ（次セッション注意）**: 本体は trimMargin、usage は quote 付き heredoc `<<'Z2SCAN_USAGE'`。シェル `$` は全て `${'$'}`(=変数 d)。**awk のフィールド参照（`$2`/`$4`）と正規表現末尾 `$`（`/^0+$/`）は直後が数字/記号なので Kotlin ではリテラル扱い**＝そのままで OK（`$ALPHA` だけが Kotlin テンプレートと衝突するので注意）。メッセージは `$mXxx`（Kotlin val）で ja/en 注入。
- **docs**: HANDBOOK §11 早見表（ja/en）に「セキュリティ」節、DESIGN-SPEC（ja/en）に注入一覧＋専用項目、README/README.ja の機能一覧＋版数（0.8.91）。
- **検証**: `gw.sh :app:compileFullDebugKotlin`＝BUILD SUCCESSFUL。**full release 0.8.91 ビルド済**＝`assembleFullRelease` BUILD SUCCESSFUL（8m19s・半並列・stale 対策で `merged_native_libs`/`merged_jni_libs`/`stripped_native_libs`/`outputs/apk/full/release` を事前 rm）。APK `app/build/outputs/apk/full/release/app-full-release.apk`（195MB・versionCode 99）、署名 `CN=Z2Term, O=ZeroToShip, C=JP`（既存鍵＝更新可）、`.so` 同梱＝proot/proot_loader/talloc/z2root/z2accept/z2term（full 正常・native 無変更で再利用）。**z2scan は launch 毎生成のスクリプトなので APK 内には無い**（導入→再起動で各タブへ注入）。
- ⚠️ **実機 e2e 未**: dev 環境では確認不可。新 APK 導入後に各タブで `z2scan self`（自己診断の出力・findings 判定）/`net`（localhost への nmap・外部対象が `--allow-remote` 無しで拒否されること）/`host`（lynis or fallback）を確認すること。
- **残（このセッションで未着手）**: ① **APK バックアップ**（`/root/z2-apk-backup/` への退避は未。ディスク使用率に留意）。② **foss release ビルド**は未。③ **GitHub Release 公開**は未（手順は [[reference_github_release_on_device]]＝`--prerelease` 付けず `--latest`）。④ コードレビューで挙げた **`Z2ApiBridge.doOpen()` の URI スキーム allowlist**（`file://`/`content://`/`intent:` を弾く）＝信頼境界内で実害小だが別件として保留中。

## 2026-06-13 追記 — 0.8.90 を full/foss release ビルド → **GitHub Release 公開**＋README から変更履歴を撤去（ja/en）

ユーザーが 0.8.90 を実機導入し `z2term`/`z2help` 動作 OK を確認済。残作業として release ビルドと GitHub 公開、README 整理を実施。

- **commit/push は実施不要だった**: 0.8.90 のコード＋docs は前セッションで `ba18acb` に集約され**既に origin/main に push 済**（git status クリーン・ahead/behind 0）。
- **full/foss release ビルド（0.8.90, versionCode 98）**: `scripts/gw.sh :app:assembleFullRelease` / `:app:assembleFossRelease` とも `BUILD SUCCESSFUL`（半並列・フリーズなし。stale 対策で各 `merged_native_libs`/`merged_jni_libs`/`stripped_native_libs`/`outputs/apk/<flavor>/release` を事前 rm）。**release ビルドがコンパイル確認も兼ねる**（前回未了だった 0.8.90 のコンパイル確認はこれで解消）。
  - **検証（全パス）**: ① full=195MB・foss=20.8MB、ともに versionCode 98 ② 署名 `CN=Z2Term, O=ZeroToShip, C=JP`（apksigner） ③ `.so` 同梱＝full に proot/talloc/z2root系＋alpine rootfs、foss は z2root系のみで proot/talloc/alpine 0 ④ **stale でない確証**＝`libz2root.so` の md5 が 0.8.89 backup と完全一致（`e7f3aa77…`）＝0.8.90 は Kotlin のみで native 無変更が正しく反映。[[project_z2root_stale_apk_jnilibs]]
  - **バックアップ**: `/root/z2-apk-backup/app-full-release-0.8.90-ba18acb-z2root.apk`（md5 `2364a1b3…`）/ `app-foss-release-0.8.90-ba18acb.apk`（md5 `89027820…`）＋各 `.md5`。
- **GitHub Release `v0.8.90-alpha` 公開済**: `gh release create` で prerelease として作成→full/foss APK 添付（アセット名 `app-full-release.apk` / `app-foss-release.apk`・state=uploaded）。**当初 prerelease にしてしまい `Latest` が旧 0.8.17 のままだった→ユーザーが Web で prerelease 解除＋Set as latest を実施し解消**。`api.../releases/latest`＝`v0.8.90-alpha`・`prerelease:false` を確認済。**教訓: 既存 alpha は prerelease 無しで出している＝今後も `--prerelease` を付けず `--latest` で作るのが運用に合う**。[[reference_github_release_on_device]]
- **README 整理（commit `a33e73e`・push 済）**: ユーザー指示「変更履歴は README に残さなくて良い・無駄を書かず分かりやすいものだけ」。`## Current version`/`## 現在のバージョン` 配下の **0.8.17 まで遡る Previously 群＋マイルストーン別機能履歴（約260行）を全撤去**し、現行版1行＋**版数非依存の機能一覧**＋ロードマップだけ残す構成へ（en 451→281行 / ja は 0.8.88・0.8.25 残骸も一掃して 275行）。変更履歴は GitHub Releases に集約する方針。Download の古い直リンク（en 0.8.21 / ja 0.8.20）も `v0.8.90-alpha` の full/foss APK へ更新。docs のみ＝版数据え置き。
- **未了/残（実害小・スコープ外）**: z2adb `server_up` の冪等ガードが z2root 下で不発（毎回 nodaemon 再起動の2本目が abort・クライアントは1本目に繋がり flow 成立）。堅牢化案＝`server_up` を `adb devices` の rc 判定へ。[[project_z2adb_selfadb_e2e]]

## 2026-06-13 追記 — `z2help` / `z2term` コマンドを新設（独自 `z2*` 早見表を端末から引ける）0.8.90

- **目的**: ディストロに `z2adb`・`z2version`・`z2-*`・`z2gui`/`z2run` 等の独自ヘルパーを多数注入しているが、端末側に「何が使えるか」を引く手段が無かった。`z2help` で全 `z2*` コマンドの分類済み早見表（先頭にアプリ版数も併記）を表示できるようにした。
- **実装**: 新規 [`Z2HelpScript.kt`](../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt) に `z2helpScript(lang)`（本体は静的テキストを quote 付き heredoc `<<'Z2HELP_EOF'` に格納＝シェル展開なし・外部入力なし。`trimMargin("|")` で終端を行頭に固定）と `z2termAliasScript()`（`exec /usr/local/bin/z2help "$@"`）。`ProotLauncher.ensureZ2HelpScript()` が `/usr/local/bin/z2help` と `/usr/local/bin/z2term` を起動毎に書き直す（proot/z2root 経路＝L259 直後、chroot 経路＝L414 直後の `ensureZ2AdbScript` の隣で呼ぶ）。表示言語は `LocaleHelper.language` に追従。
- **`z2term` はエイリアス（予約）**: 当面 `z2help` の薄いラッパー。`z2term` を将来別用途のコマンドに使いたくなったら `z2termAliasScript` を差し替えるだけ。「`z2term` と打ったら説明が出るか？」というユーザー要望にこれで応える＝同じ早見表が出る。
- **docs**: HANDBOOK §11 早見表（ja/en）に「ヘルプ」節を追加、DESIGN-SPEC（ja/en）に注入一覧＋専用項目を追加、README「現在のバージョン」に 0.8.90 エントリ。
- ⚠️ **実機 e2e 未**（端末で `z2help`/`z2term` を打って一覧が出ること・版数併記の確認は本修正入り APK 導入後）。コンパイル確認は要実施。

## 2026-06-13 追記 — z2adb 本番 e2e **成功**（pair→connect→shell 通過）＋ 不要ファイル大掃除（約6GB回収）

- **z2adb 実機 e2e 完了**: ユーザー端末で `z2adb pair 34693 561083`（Successfully paired）→ `z2adb connect 40291`（connected）→ `z2adb shell`（`bogota:/ $` 到達）。**PC・USB・root 無しで端末自身の adb シェルに入れることを実証**＝0.8.89 `5df2456` の核心（adb サーバを自己 exec させず先行起動しクライアントが繋がる）が本番で成立。旧バグ `adb: execl returned -1` は出ない。
  - **ハマりどころ（重要）**: **ペアポート ≠ 接続ポート**。`pair <ペアポート>` は「ペアリングコードでのペア設定」の使い捨てポート、`connect <接続ポート>` は**ワイヤレスデバッグ メイン画面**の `IP:ポート` の番号（ON/OFF で変わる）。最初ペアポートを connect に使い `Connection refused` で躓いた。ペアは一度通れば以後 `connect`→`shell` だけで可。
  - **既知ギャップ（未修正・実害小）**: `start_server` の `server_up()` は `/proc/net/tcp{,6}` を grep するが、**z2root 下では adb の listen ソケットがどの proc ビュー（global / adb 自身 netns / /proc/net/unix）にも現れず検出できない**＝冪等ガードが不発。毎回 nodaemon を再起動し2本目が `Address already in use` で background abort（握り潰し）。クライアントは1本目に繋がるので flow は成立するが abort が漏れる。堅牢化案＝`server_up` を `adb devices` の rc 判定に変える（コード変更＝版数上げて1コミット）。前セッションの「guard_ok: already listening」実証は pty 混線時の誤読の可能性。[[project_z2adb_selfadb_e2e]]
- **大掃除（約6GB回収・disk 82%→78%）**: ① `/root/z2root_trace.log`（1.1GB・増殖中）を切り詰め＋`/root/.z2root_trace_on` 削除でトレース無効化。② `/root/z2-apk-backup/` の旧 APK 十数本を削除し **最新 full `0.8.89-5ed66c9` ＋ 直近 full/foss `0.8.86-7d9657a` の3本（+md5）だけ残す**（約2GB）。③ `/tmp` のデバッグ残骸 386 件（ビルドログ/テスト .c/.so/バイナリ/各種ダンプ）を削除（約2.1GB）。**稼働中の dbus/X11 ソケット・dropbear・kotlin daemon・GUI ランタイム（`z2gui-*`）・`*.alive`/`*.lck`・`libjansi-*.so` は温存**。リポジトリはクリーンのまま。

## 2026-06-13 追記 — z2term 独自ヘルパー & 特殊オプション 説明書（端末タブで使えるコマンド一覧）

z2term は launch 毎に `/usr/local/bin`（一部 `/usr/local/sbin`）へ独自ヘルパーを再生成して注入する（proot/z2root/chroot 共通。UI 言語に追従、常に最新版で上書き）。distro 非依存で、PATH 上からそのまま叩ける。

### 版数・情報
- **`z2version`** — 実行中アプリの版数・flavor（Full/FOSS）・package・engine（proot/z2root/chroot）・rootfs gen・ゲスト OS・kernel をまとめて表示。`z2version --short` は `0.8.89-alpha (97)` のように版数のみ1行（スクリプト用）。APK とゲスト側の版数不一致を即切り分けられる。

### セルフ adb（PC・USB・root 不要。Android のワイヤレスデバッグへ localhost 接続）
- **`z2adb pair <ペアポート> [6桁コード]`** — ペアリング（設定>開発者向けオプション>ワイヤレスデバッグ>「ペアリングコードによるデバイスのペア設定」のポート＋コード）。
- **`z2adb connect <接続ポート>`** — 接続（ワイヤレスデバッグ メイン画面の `IP:ポート` の番号。**ペアポートとは別**）。
- **`z2adb shell` / `z2adb logcat` / `z2adb install …`** など — 上記以外は素の `adb` へ passthrough（`z2adb <adb 引数...>`）。
- **`z2adb setup`** — adb クライアントを導入（apk=android-tools / apt=adb / pacman=android-tools を自動判定）。`pair`/`connect`/`status` は未導入なら一度だけ自動 setup。
- **`z2adb status`** — `adb devices -l`。
- 宛先は既定 `127.0.0.1`。`host:port` を渡せば上書き、`Z2ADB_HOST` env でも可。

### Android API ブリッジ（端末から本体 Android 機能を呼ぶ）
- **`z2-notify <title> [text]`** — 通知。
- **`z2-toast <message>`** — トースト。
- **`z2-share <text>`** — 共有シートに渡す。
- **`z2-open <url|path>`** — URL/ファイルを既定アプリで開く。
- **`z2-clip get`** / **`z2-clip set [text]`** — クリップボード取得/設定（set は引数無しなら標準入力）。
- **`z2-battery`** — 残量/充電状態を JSON（`{"level":N,"charging":bool}`）で出力。
- **`z2-vibrate [ms]`** — バイブ（既定 200ms）。
- 内部ディスパッチャは `z2api`（`/storage/app/z2api` 経由で本体とやり取り。直接使う想定なし）。

### Linux GUI（Xvnc + openbox。内蔵 VNC クライアントが 127.0.0.1 で接続。外部非公開）
- **`z2gui [start [WxH] | stop | status | install | check]`** — GUI を起動/停止/状態確認/導入。例 `z2gui start 1280x720`。
- **`z2run [GUI アプリ ...]`** — 端末から GUI アプリを起動すると GUI タブを自動で開いてからアプリへバトンタッチ（CUI⇄GUI 連動）。
- **`z2-autogui`** — preexec フックから自動で呼ばれ、GUI アプリ起動を検知して Xvnc を確保（手動利用は不要）。
- 関連 env（z2term が注入）: `Z2_DISPLAY`（ディスプレイ番号）/ `Z2_RFBPORT`（RFB ポート）/ `Z2_NO_TERM=1`（xterm 同時起動抑止）/ `Z2_AUDIO`・`Z2_AUDIO_PORT`（音声）。

### SSH（バックエンドは dropbear。`sshd` 互換ラッパー）
- **`sshd`** — 通常の sshd 同様に振る舞う（`/usr/local/sbin/sshd`）。ポートは `-p` / `-o Port=N` →`/etc/ssh/sshd_config` の `Port`→ 既定 `2222` の順。`-f <config>` / `-D`・`-d`（前景）/ `-t`（設定確認）対応。dropbear 未導入なら自動 install。
- **既定の安全側**: **`127.0.0.1` のみ bind**（LAN/WAN 非公開）・空パス root 禁止・**パスワード認証 全面禁止**（`~/.ssh/authorized_keys` の鍵のみ）。公開したいときだけ `--lan` 引数 or `Z2_SSHD_LAN=1` env で明示有効化（鍵認証必須＋警告）。
- 横断 e2e の入口: 各 distro タブへ `ssh -p 8022(Alpine)/8023(Ubuntu)/8024(Kali) root@localhost`。

### 実行エンジン関連の特殊事項
- **engine** は `z2version` の `engine:` 行で確認（proot / z2root / chroot）。**cmdline の argv[0] は z2root でも `proot ...` になる**ので判定に使わない。`exe`/`comm`（z2root は `libz2root.so`）/`maps`/`TracerPid` で見る。
- **z2root トレース**: `/root/.z2root_trace_on` が在ると `/root/z2root_trace.log` に詳細トレースを吐く（巨大化するので常用しない。止めるにはフラグ削除＋ログ切り詰め）。

## 2026-06-13 追記 — full release 0.8.89 をビルド・検証・バックアップ済（z2adb 本番版入り APK 完成・導入待ち）

ユーザー指示で full release（0.8.89）を再ビルド。**`5df2456` の z2adb 本番版（`start_server` 入り）を含む full release APK が完成**＝これを本体 UI で導入すれば本番 pair→connect→shell の実機 e2e に進める（端末に注入済の旧 z2adb 0.8.88 を置き換えられる）。コード変更なし・ビルドと検証のみなので版数据え置き・コミット不要。

- **ビルド**: `./scripts/gw.sh :app:assembleFullRelease` = BUILD SUCCESSFUL（8m53s, 半並列 nproc/2, exit 0, フリーズなし）。stale `.so` 対策で事前に `merged_native_libs/fullRelease`・`merged_jni_libs/fullRelease`・`stripped_native_libs/fullRelease`・`outputs/apk/full/release` を rm してから実行。
- **検証（全パス）**: ① versionCode **97** / **0.8.89-alpha**（output-metadata.json） ② 署名 `CN=Z2Term, O=ZeroToShip, C=JP`（apksigner verify=過去バックアップと同一鍵＝更新インストール可） ③ `.so` 同梱＝libz2root/libproot/libtalloc/libz2accept/libz2term＋alpine rootfs（full として正常。foss だけが onlyFullFlavor 除外） ④ **stale でない確証**＝全 `.so` が直近 full 0.8.86 backup と **md5 完全一致**。0.8.89 は z2adb の Kotlin 変更のみで `buildZ2rootNative` は UP-TO-DATE＝native 無変更が正しく反映（古い `.so` 混入なし。[[project_z2root_stale_apk_jnilibs]]）。
- **署名鍵の所在（次セッション注意）**: release 鍵 `keystore.properties` と `z2term-release.jks` は **プロジェクトルート直下にある**（git 管理外＝clone には来ないが、この端末には存在）。`ls keystore.properties app/*.jks` のような複合 glob は zsh で no-match だと eval 全体が失敗し「鍵が無い」と誤認するので注意。鍵が無ければ build.gradle:144-146 が debug 鍵 fallback＝別署名になる。
- **バックアップ**: `/root/z2-apk-backup/app-full-release-0.8.89-5ed66c9-z2root.apk`（md5 `62055015aa75caa8e76f5e7dc98c378f`, 195243735 B）。
- **次タスク**: このAPKを本体UIで導入→再起動（注入済 z2adb が 0.8.89 本番版に更新される）→ワイヤレスデバッグ ON → `z2adb pair`→`connect`→`shell` を通す（ユーザー端末操作が必須）。

## 2026-06-13 追記 — `z2adb`: adb サーバ自己 exec 失敗を回避し z2root で実接続（0.8.89）コミット `5df2456`・コア e2e 実証済・本番 pair/connect は次セッション

前項（0.8.88）の z2adb を**実機 e2e**したところ、`z2adb status`/`pair`/`connect` が内部で adb サーバを自動起動する際に `adb: execl returned -1: No such file or directory` で失敗。**真因＝z2root が `/proc/self/exe` を APK 内 `libz2root.so` と返すため、adb クライアントが daemon を自パスへ `execl` して再起動しようとすると ENOENT**（adb 全般の問題で z2adb 固有でない。メモリの `/proc/self/cwd` 逆変換ギャップ＝v60 修正済の `/proc/self/exe` 版に相当）。手元検証で `adb -L tcp:5037 nodaemon server &`（自己 exec を伴わない）なら起動でき `adb devices` が通ることを確認 → これを本体に組み込んだ。

- **修正（`Z2AdbScript.kt`）**: ① `server_up(port)` = `/proc/net/tcp{,6}` を直接 grep し対象ポート（16進・state `0A`=LISTEN）の有無を adb クライアントを起動せず判定。② `start_server` = `ADB_SERVER_SOCKET` のポート（既定 5037）が未 LISTEN のときだけ `(adb -L tcp:$port nodaemon server >/dev/null 2>&1 &)` を background 起動し、最大 6 秒（0.2s×30）LISTEN を待つ。③ `ensure_adb` を「adb 未導入なら install → その後 `start_server`」に変更（従来は has adb で即 return していた）。
- **abort の切り分け（結論）**: 検証中に観測した adb の `Aborted` は **二重 bind**（既に 5037 で listen 中の server があるのに重ねて起動＝`Address already in use` で FATAL）が引き金。setsid/fd リダイレクトは無関係と確定。正規の z2adb は `server_up` 冪等ガードで起動前に検出するので二重 bind に至らず、abort しない。adb は IPv4 `127.0.0.1:5037` のみ listen するが `server_up` は tcp/tcp6 両方を見るので取りこぼさない（＝ガードが効く前提を裏取り済）。本体には setsid は入れていない（サブシェル background のみ）。
- **コア e2e 実証済（手元・同一シェルセッション）**: `start_server` 相当を手で再現し、(a) `listen=NO→YES`（約0.4秒で LISTEN）、(b) **冪等ガード**＝2回目呼び出しは `guard_ok: already listening, no relaunch` で `adb_procs` は 1 のまま（二重 bind の abort なし）、(c) **クライアントが自己 exec せず既存サーバへ接続**＝`adb devices` が `List of devices attached`・rc=0 で旧バグ `execl returned -1` が出ない、を確認。つまり「z2root で adb サーバを自己 exec させず先行起動しクライアントが繋がる」核心は裏取り済。注意: **Bash 呼び出しをまたぐと検証環境が background 子プロセスを刈る**ため、サーバ起動と接続は同一呼び出し内で行う必要がある（実アプリの永続シェルでは起きない）。
- **未了（次タスク）**: ① **コンパイル未検証**（`gw.sh :app:compileFullDebugKotlin` 等で確認推奨）。② **本番 pair→connect→shell**＝端末側で 設定>開発者向けオプション>ワイヤレスデバッグ ON にし「ペア設定」ポート＋6桁コードを使って `z2adb pair`→`connect`→`shell` まで通す。これはユーザーの端末操作が要る（私単独では不可）。なお現在端末に注入済の `/usr/local/bin/z2adb` は **旧版（0.8.88・`start_server` 無し）**なので、本番 z2adb で検証するにはアプリ再ビルド＆再起動が要る（この z2root 端末での重いビルドはフリーズリスクあり）。
- **コミット経緯（次セッションが git 状態を誤解しないため）**: 0.8.89 のコード＋docs（README/DESIGN-SPEC ja+en/build.gradle 96→97/本ファイル）は **1 コミット `5df2456` に集約**済み・**未 push**。作業中 pty 混線でコミット結果が何度も誤読され中間ハッシュ（7d3f9c2/96b6d7b/6692b13）が転々と見えたが、実体は `5df2456` 一つ（最後に `--amend` でメッセージを `docs:` → 正しい `fix(adb):` 形式＋本文＋Co-Authored-By に整形済）。**git 確認は pty 混線回避のため出力をファイルへ書いて Read する運用**が安全。DESIGN-SPEC の英訳（en）は ja+en 同梱ルールに従いこのコミットに最初から含む（別途英訳作業は不要）。
- **検証メモ**: この z2root 端末で adb server を複数ポートに乱立させると pty が混線して端末が不安定になる。切り分けは 1 プロセスずつ・掃除は `pkill -9 -x adb`（comm 完全一致。`pkill -f 'nodaemon server'` は自シェルの cmdline にも当たり自爆するので不可）。

## 2026-06-13 追記 — `z2adb`（セルフ adb）ヘルパー追加（0.8.88）コード＋docs まで（未コミット・未ビルド）

ユーザー要望「この端末は Android 本体だが adb が使えず不便。アプリから adb 同等のことをしたい」への対応。**方式＝LADB 相当のセルフ adb**（PC を繋がず端末が自分自身の adb デーモン＝ワイヤレスデバッグに `localhost` で繋ぐ。root も USB も不要）。PRoot/z2root は TCP を素通しする（dropbear/SSH と同経路）ので localhost に届く＝既存基盤で成立する、と判断して実装。

- **新規**: `app/src/main/java/com/zerotoship/z2term/proot/Z2AdbScript.kt` の `z2adbScript(lang)`。`/usr/local/bin/z2adb` を生成（ja/en 切替）。サブコマンド: `setup`（adb 導入・`detect_pm` で apk=`android-tools`/apt=`adb`/pacman=`android-tools` 自動判定）/ `pair <port> [code]` / `connect <port>` / `status`（`adb devices -l`）/ `help`。それ以外は素の adb へ passthrough（`exec adb "$@"`）。宛先はポートのみなら `Z2ADB_HOST`(既定 127.0.0.1) を補完、`host:port` はそのまま。`pair`/`connect`/`status` は adb 未導入なら一度だけ自動 `setup`。
- **配線**: `ProotLauncher.kt` に `ensureZ2AdbScript(rootfs)` を追加し、**proot/z2root の launch と chroot launch の両経路**で呼ぶ（`ensureZ2ApiScripts` の直後）。launch 毎に上書き＝常に最新。`LocaleHelper.language(context)` で UI 言語に追従。
- **docs**: README(ja/en) 現在地、DESIGN-SPEC(ja/en) §4 ヘルパー節＋版数、HANDBOOK(ja/en) に「§7.5 PC なしで adb を使う」手順、build.gradle.kts 95→96 / 0.8.87→0.8.88-alpha。
- **未了（次タスク）**: ① **コンパイル未検証**（`gw.sh :app:compileFullDebugKotlin` 等で確認推奨。スクリプトは Kotlin の `trimMargin`/heredoc を混在させているので文字列生成の取り違えに注意）。② **実機 e2e 未**（実機でワイヤレスデバッグ ON → `z2adb setup`→`pair`→`connect`→`shell` の一気通貫。proot/z2root 両エンジンで TCP が通るかも実地確認）。③ コミットはユーザー指示で（バージョン上げ済なので「コード＋docs を 1 コミット」）。
- **方針確認**: ユーザーに「z2term の方針として問題ないか」確認された件＝**問題なし**と判断（既存 z2*/z2-* ヘルパー注入パターンと完全同型、root 不要・非侵襲で full/foss 両適合、外部送信なし＝localhost 自己接続のみ。adb 本体は同梱せず distro の公式パッケージを使うので F-Droid 的にも追加同梱物なし）。

## 2026-06-13 追記 — OSS ライセンス告知漏れ補完: BouncyCastle 追加（0.8.87）コミット＋push 済

ユーザー確認「foss 版に残る外部ライセンス表記は F-Droid 的に同梱して問題ないか」への対応。**結論=残存表記はすべて適合**（AndroidX/Compose・Kotlin・JSch・XZ・フォント＝自由ライセンスの Maven 依存またはデータ。full 専用の prebuilt バイナリ群=PRoot/talloc/Alpine rootfs は `onlyFullFlavor=true` で foss から既に除外済＝それが F-Droid のブロッカーだった）。ただし **BouncyCastle (`org.bouncycastle:bcprov-jdk18on:1.84`) が両フレーバー同梱なのに `OssComponents` 一覧から抜けていた告知漏れ**を発見 → 補完した。

- **変更（コミット `bb9dbf3`・push 済 `39a1659..bb9dbf3`）**: `OssComponents.kt` に JSch 直後へ Bouncy Castle エントリ追加（`licenseId="MIT"`＝BouncyCastle License は MIT X11 改変版なので既存 `MIT.txt` 全文を再利用、`onlyFullFlavor` なし＝両フレーバー表示）。`oss_purpose_bouncycastle` を ja/en strings に追加（用途=「JSch の ed25519/curve25519 を有効化する暗号プロバイダ」）。`build.gradle.kts` 94→95 / 0.8.86→0.8.87-alpha。docs（README 0.8.87 エントリ＋DESIGN-SPEC ja/en 版数）更新。
- **ついで修正**: DESIGN-SPEC の古い記述 `JSch 0.2.21 (BouncyCastle 不要)` を実態 `0.2.26 + BouncyCastle 1.84 で ed25519/curve25519 を有効化` に訂正。
- **検証**: `gw.sh :app:compileFossDebugKotlin :app:mergeFossDebugResources` = BUILD SUCCESSFUL（新 string リソース解決確認）。release APK 再ビルドは未（データ/告知のみの変更）。

## 2026-06-13 追記 — 引き継ぎ（このセッションの現在地・次セッション起点）

**コミット済（未 push）**: `2df97a9`(0.8.86 クラッシュロガー本体) → `ec043a8`(横断 e2e 結果の docs) → 本追記の docs コミット。push はユーザー明示時のみ。

- **完了**:
  - **クラッシュロガー（0.8.86）**: `CrashLogger` を `Application.onCreate` 冒頭に設置済。ユーザーが 0.8.86 を実機導入し「何度も消して開いて」も**クラッシュ再現せず**（＝採取待ち。落ちたら `filesDir/crash/crash-last.txt` に残る）。報告症状は「フリーズではなくクラッシュ（落ちる=閉じる）」と確認済＝ハンドラで拾える種別。
  - **full release 0.8.86**: ビルド・release 署名(`CN=Z2Term`)・`.so` 在中確認・バックアップ済（`/root/z2-apk-backup/app-full-release-0.8.86-2df97a9-z2root.apk`, md5 `8513e25c435c5921f03a4261cc061198`）。**ユーザーが導入済**（稼働中のがこれ＝CrashLogger 入り）。
  - **横断 e2e（z2root 0.8.86, Alpine/Ubuntu/Kali）**: 退行 0 件（下節）。0.8.84 の Kali 実機裏取りもクローズ。
- **in-flight / 次セッションの即タスク**:
  1. **foss release ビルド（0.8.86）が進行中**（`assembleFossRelease`, gw.sh=半並列）。完了したら `app/build/outputs/apk/foss/release/app-foss-release.apk` を `unzip -l`+`strings`(proot/talloc/alpine 非同梱・`.so` は z2root 系のみ・versionCode 94) と `apksigner`(CN=Z2Term) で検証 → バックアップ。**注**: 既存 APK は 6/12 の旧 0.8.78（20.8MB）なのでタイムスタンプ/版数で新旧を必ず区別。
  2. **full release も作り直す**（ユーザー指示「フルとフォス両方ビルド」）。full 0.8.86 は既存だが指示どおり再ビルド可。
- **ビルド鉄則（ユーザー再確認・最重要）**: **最大 CPU だと proot・z2root どちらでもフリーズする＝必ず半分（`--max-workers=nproc/2`）**。gw.sh が自動付与。エンジンで差は無いので proot へ逃がす案内はしない。**2 本同時ビルドは CPU 倍がかりでフリーズ条件に入るため逐次**で回す（[[build-normal-parallel]] [[z2root-heavy-build-freezes]] [[proot-z2root-equivalent-just-build]]）。
- **git identity**: この環境は git user 未設定。コミットは `git -c user.name='orgson' -c user.email='270548806+orgsonai@users.noreply.github.com' commit ...` で 1 回ずつ渡す（config は変更しない方針）。
- **横断 e2e の入口**: 各ディストロタブへ `ssh -p 8022(Alpine)/8023(Ubuntu)/8024(Kali) root@localhost`（[[crossdistro-ssh-e2e]]）。

## 2026-06-13 追記 — 横断 e2e: Alpine/Ubuntu/Kali を z2root 0.8.86 で実走（項目7 残＋0.8.84 残検証クローズ）

SSH（`root@localhost` ポート 8022=Alpine3.21 / 8023=Ubuntu24.04 / 8024=Kali Rolling、3 つとも `exe`/`maps`=`libz2root.so`・`z2version --short`=`0.8.86-alpha (94)` で z2root 確定）で `scripts/z2root-cmdtest.sh` を実走。**3 ディストロとも z2root 退行 0 件**。

- **Alpine (musl)**: §11 全✓（trunc-abs / xattr-abs / `name_to_handle_at`=ENOSYS OK-benign）。非ゼロ 2 件は**環境要因**＝① `apk add figlet` が trigger 非致命エラーで exit 1 だが直後の `figlet` は正常動作（パッケージ機能） ② `pip3 install --user wheel` が PEP 668 (externally-managed) でブロック。どちらも z2root 非依存。
- **Ubuntu (glibc)**: §11 全✓、非ゼロ 0 件（`python3 -m venv` の pip/wheel 失敗は `python3-venv` 未導入の環境要因で、末尾 echo によりサマリ上は exit 0）。
- **Kali (glibc)**: 非ゼロ 0 件。dpkg の `python3.13-minimal` setup（byte-compile）が完走。
- **0.8.84 (execve argv 長制限撤廃) を Kali 実機で明示裏取り**: `apt-get install -y python3` が `Setting up python3.13 … / running python rtupdate hooks`（旧来ここの byte-compile が `cannot execute: required file not found` で落ちていた）まで完走。`python3` 実起動 OK(3.13.12)。**大 argv ストレス: argc=400・総 argv ≈12690 バイト（旧 `char blob[8192]` 上限を大幅超過）で `python3 -m py_compile` が exit 0・400 個 .pyc 生成**＝動的確保化が実機で効いている。
- **残**: proot タブ対照は未取得（ユーザー方針で proot は対照値としてのみ・本作業では z2root のみ実走）。claude headless / node / sqlite 等は今回の素 rootfs に未導入で skip 多数（実起動経路の確認は別途、claude 導入済みタブで）。

## 2026-06-13 追記 — 間欠的な起動クラッシュ採取用クラッシュロガー追加（0.8.86）コミット済

ユーザー報告「たまにアプリ立ち上げた瞬間落ちる」の調査。起動経路（`Application.onCreate` → `GuiEventWatcher`/`Z2ApiBridge`/`ClipboardHistoryStore.init` → `MainActivity.onCreate` → `SessionManager.ensureFirst` → `TerminalSession` 生成）を精読したが各所 `runCatching`/`try` で守られ、`settingsFlow` も `initialValue` 付き＝**静的解析では決定的な crash 要因なし**。間欠性ゆえ実トレースが要るが adb 未設定（install は本体 UI のみ）。

- **対処（0.8.86, 新規 `CrashLogger.kt`）**: `Application.onCreate` 冒頭で `Thread.setDefaultUncaughtExceptionHandler` を張る。落ちる直前にスタックトレース（版数/フレーバー/端末/スレッド付き）を `filesDir/crash/crash-<時刻>.txt` ＋ `crash-last.txt` へ**同期書き込み**し、**直前のデフォルトハンドラへ必ずチェーン**（OS のクラッシュ挙動・プロセス終了は不変）。古いログは 20 件で間引き。crash dir 絶対パスは起動時 logcat にも出す。
- **読み方（実機）**: 再現後、端末タブで `cat <filesDir>/crash/crash-last.txt`。`<filesDir>` は full release で `/data/data/com.zerotoship.z2term/files`、foss debug は `/data/data/com.zerotoship.z2term.foss.debug2/files`。
- **残**: 実機で 0.8.86 導入 → 起動クラッシュ再現 → `crash-last.txt` を採取して真因特定。**この APK が無いと採取できない**（実機導入が必須）。

## 2026-06-13 追記 — IME: 結合読みを 1 語コスト基準にし連続確定 run を結合ブロック学習（0.8.85）コミット済

動的ブロック分割学習（0.8.71/0.8.74）の続き。前セッションからの WIP（`KanaKanjiConverter.kt`/`KkcConverter.kt`/新規 `BlockLearningTest.kt`）を JVM ユニットテスト緑（`BlockLearningTest` 2/2・`KkcEvalTest` 4/4）で裏取りしてコミット。

- **`KkcConverter`**: 学習ブロック合成ノードのコストを `UNK_COST * (j-i) - bonus` → **`UNK_COST - bonus`**（1 語基準）へ。辞書外の外来語が辞書分割に勝てず 1 ブロック化しなかった件を解消。
- **`KanaKanjiConverter`（`ComposingState`）**: 同一スプリット run の連続確定を `committedRun` に蓄積し、run が尽きた時（`learnMergedRun`）と一括確定時に **結合読み→結合表層**を `ImeHistoryStore.record`。読み長 2〜`MERGE_MAX_READING_LEN`(6) に限定（長文の丸ごと 1 ブロック化を防止）。`text` 編集・reset で run クリア。
- **`BlockLearningTest`（新規）**: 実 lex/matrix を読み、辞書外読みが未学習では分割・高頻度ボーナスで 1 文節へ収束することを検証。
- **docs**: README / DESIGN-SPEC ja・en §6.2.1 の「動的ブロック分割（学習）」へ 0.8.85 を追記。
- **残**: 実機での体感確認（頻用の塊が実際に繋ぎ止まるか）は APK 導入後。

## 2026-06-13 追記 — z2root: execve 書き換えの argv 長制限を撤廃（0.8.84）／クロスディストロ e2e で発見

クロスディストロ cmdtest e2e（Alpine/Ubuntu/Kali を SSH で稼働、全タブ z2root 0.8.83 をエンジン確認済）の途中、**Kali で `apt-get install python3` が dpkg の byte-compile 段で `cannot execute: required file not found`（execve ENOENT）で失敗**するのを発見。`/usr/bin/python3.13` 単体は起動するのに、`python3.13 -E -S py_compile.py <287ファイル>` だけ落ちる。

- **二分で切り分け**: argv 個数ではなく **argv 総バイト数 ~7.5KB 超で execve が ENOENT(errno=2)**。相対パス(=非変換)でも 8KB 超で落ちる＝パス変換ではなく argv ブロック総量依存。カーネル ARG_MAX は 2MB なので **z2root 内部バッファ起因**と確定。
- **根本原因（`z2root.c` `rewrite_execve`）**: ①固定長 `char blob[8192]` で `blob_sz > 8192` のとき `if (blob_sz <= sizeof(blob))` が偽になり **execve 書き換えを丸ごとスキップ**→path レジスタにゲストパスが残ったまま execve→ホストに存在せず **ENOENT**。②`MAX_ARGS 256` が argv を 256 個で切り捨て（dpkg は 287 個）。dpkg byte-compile（~11KB・287 個）は両方に抵触。
- **修正（0.8.84）**: argv 読み取りを上限なしの動的確保（realloc）に、`blob`/`parts`/`ptrs` を argv サイズに応じた `malloc` に変更し `MAX_ARGS` を撤去。scratch は従来どおり `sp - SCRATCH_OFFSET - total` 直下に置く（growsdown stack を `process_vm_writev` が伸長するため大きい argv でも mapped）。`assembleFullDebug` で `buildZ2rootNative` クリーンビルド確認。
- **クロスディストロ結果**: Alpine・Ubuntu とも **非ゼロ 0 件・§11 全通過**（trunc=1MB ✓ / xattr=ok ✓ / name_to_handle_at は ENOSYS-benign ✓）。Kali は上記 execve バグで python 導入が止まっていた＝**本修正後に再検証要**（新 APK 導入後、Kali で `apt-get install python3` 再試行→§11 まで通すこと）。
- **要・実機検証**: 新 APK（0.8.84）を本体 UI で導入し、Kali で python3 インストール完走＋大 argv exec（例: `python3.13 -E -S py_compile.py <多数>` や大量ファイルの `make`/`grep`）が通ること。

## 2026-06-13 追記 — cmdtest に絶対パス seccomp e2e（§11）を追加し z2root 実機で初回実走（scripts のみ＝版数据え置き・コミット済 `f1514a8`・**push 済**）

0.8.81〜0.8.83 の seccomp/パス変換変更の実機裏取り用に、`scripts/z2root-cmdtest.sh` へ **§11「絶対パス syscall のパス変換」**を追加。既存 §9 の xattr は相対パス（cwd=rootfs 内で元々安全）で新コードを踏まないため、**絶対パス版**で踏む 3 ケースを足した。

- **追加ケース（全て cwd=$WORK の絶対パスで実行）**: ①`truncate(2)`（GNU coreutils の `truncate` は open+ftruncate になりがちなので **python `os.truncate`** で syscall 直叩き）②`setxattr/getxattr` by-path（`setfattr`/`getfattr`、既定 follow）③`name_to_handle_at(264)`（CLI 無いので **python ctypes で syscall 直叩き**、`AT_FDCWD`+`AT_SYMLINK_FOLLOW`）。`open_by_handle_at(265)` は path 無＝非対象。
- **エンジン確認（重要・最初に誤認）**: この dev タブの稼働エンジンは **z2root 0.8.83**（proot ではない）。プロセスの **cmdline は argv[0]="proot ..."** だが、これは z2root の **proot 互換 CLI** にすぎず、`exe`/`comm`/`maps` は全て `libz2root.so`、最上位トレーサ(24092)の `TracerPid=0`・guest 連鎖は全プロセス `comm=libz2root.so` で **proot 介在も二重 ptrace も無し**。⇒ **cmdline の argv[0] でエンジン判定してはいけない**（`exe`/`comm`/`maps`+`TracerPid` で見る）。[[feedback_confirm_engine_first]] / [[project_z2root_engine_detection]]。
- **z2root 実機実走（有効な e2e）**: ①truncate ②xattr とも **exit 0**＝0.8.82 の絶対パス変換が稼働（pre-0.8.82 なら絶対パスがホスト root 直行で ENOENT になるはずが、正しく rootfs 内ファイルを操作＝変換が効いている確証）。③`name_to_handle_at` は **`ENOSYS`(38)**＝**z2root より外側の Android untrusted_app seccomp** が弾いた結果（io_uring と同じ外側依存。z2root 自身は 264 を ENOSYS していないので退行ではない。0.8.83 変換は Android が TRACE を許す文脈でのみ発火）。⇒ §11 判定は **ENOSYS/EOPNOTSUPP/EOVERFLOW/成功を全て exit 0（OK-benign）**、それ以外の errno と python 異常終了（segv/loader 失敗）だけ失敗扱い。
- **全文 e2e 実走（z2root 0.8.83 / Arch）→ 非ゼロ終了 0 件**: `sh scripts/z2root-cmdtest.sh` を本タブ（z2root 0.8.83, `z2version --short`=`0.8.83-alpha (91)` で確認）で通し、claude headless・git clone/gc・cc ビルド・nested ptrace(strace/gdb)・AF_UNIX・sqlite・curl TLS・§11 含め **全コマンド exit 0**。`name_to_handle_at` のみ ENOSYS だが上記のとおり benign。
- **ハーネス偽陽性 3 件を修正（z2root 非依存のノイズ除去・per-command の z2root 修正ではない）**: ①pacman の install 対象 `hello`（Arch 非提供）→ `moreutils`＋`sponge` 実行に変更 ②npm の require 解決を `NODE_PATH="$(npm root -g)"` 付きに（global モジュールは既定 require パス外） ③python multiprocessing は `/dev/shm` 不在時に fail でなく skip（POSIX セマフォ要・rootfs 未マウント＝環境要因）。`sponge` も `/dev/stdout`（パイプ経由 temp+rename が dirname=/dev で失敗）を通常ファイル出力へ。これらで非ゼロ一覧が空に揃った。
- **残**: §11＋全文を full の他ディストリ（Alpine musl・Ubuntu/Kali glibc）と proot タブ対照でも回す。`name_to_handle_at` の変換発火そのものの確認は Android が 264 を TRACE する端末/文脈が要る（本端末では ENOSYS 固定）。項目4（Alpine↔Arch の claude 隔離）/8（IME 実打）e2e は別途。

## 2026-06-12 追記 — z2root: name_to_handle_at のパス変換を追加（0.8.83）コミット済・**push 済**(`e04f550`)

seccomp 監査（§13.4）の残ギャップ `name_to_handle_at`/`open_by_handle_at` を実装。これで **§13 のコード監査ギャップ（io_uring / truncate / xattr / handle）は全て解消・push 済**。

> **次セッションの起点 = 実機 e2e フェーズ**。新規コード実装の残は無い。残るは 0.8.81〜0.8.83 の native 変更を実機で裏取りすること（下記「作業候補」1〜5）と、それが緑になったら foss リリースビルド（6）。e2e は dev 環境では不可（full release アプリ内で動作・APK 導入は本体 UI のみ）＝ユーザーの端末操作が必須。

- **修正（0.8.83, `z2root.c`）**: `name_to_handle_at(264)`（`dirfd, pathname, handle, mnt_id, flags`）を `kTraceSyscallsBase` ＋ `syscall_paths()` に追加。pathname=arg1・dirfd=arg0、**既定は最終 symlink 非追従**、`AT_SYMLINK_FOLLOW`(0x400) 指定時のみ follow（`linkat` 同様）。
- **`open_by_handle_at(265)` は非対象**: path ではなく不透明 `file_handle` を取る＝変換可能なパスが無い。かつ `CAP_DAC_READ_SEARCH` 必須で untrusted_app では EPERM で弾かれるため安全。コードにコメント記載。NDK aarch64 で compile OK（`build-z2root.sh`）。
- **要・実機 e2e**: 絶対パスでの `name_to_handle_at`（例: `open_by_handle_at` ペアを使う稀なツール、`overlayfs`/`fanotify` 系）が rootfs 配下を指すこと。発火は稀。

## 2026-06-12 追記 — z2root: truncate と xattr by-path のパス変換を追加（0.8.82）コミット済・**push 済**(`ef5ef10`)

seccomp 監査（§13.4）で見つけた未変換ギャップ②を実装。

- **背景**: z2root は chroot を使わず ptrace パス変換で封じ込めるため、**トレース対象外の「絶対パスを取る」syscall はホスト root 起点で解決＝封じ込めを素通り**していた。相対パスは cwd(rootfs 内) 経由で安全だが絶対パスが漏れる。
- **修正（0.8.82, `z2root.c`）**: `truncate(45)` と xattr by-path 群（`setxattr`/`lsetxattr`/`getxattr`/`lgetxattr`/`listxattr`/`llistxattr`/`removexattr`/`lremovexattr` = 5/6/8/9/11/12/14/15）を `kTraceSyscallsBase`（トレース対象）＋`syscall_paths()`（パス引数記述子）に追加。path は arg0・dirfd 無し、非 `l` 版=follow / `l*` 版=no-follow。`f*` 版（`ftruncate`/`fsetxattr` 等 fd ベース）は openat 由来の翻訳済み fd を使うため非対象。NDK aarch64 で compile OK（`build-z2root.sh`）。
- **要・実機 e2e**: 絶対パスでの `truncate`/`setfattr`/`getfattr`（xattr）が rootfs 配下を指すこと、`tar`/`rsync -X`/`cp -a` 等に退行が無いこと。`scripts/z2root-cmdtest.sh` に絶対パス xattr/truncate のケースを足して z2root タブで実走するのが裏取り。
- **残（監査ギャップで未対処）**: `name_to_handle_at(264)`/`open_by_handle_at(265)`（発火稀・保留）。

## 2026-06-12 追記 — z2root seccomp: io_uring を自前フィルタで明示 deny（0.8.81）コミット済・**push 済**(`448bfbd`)

seccomp 監査（§ 下記 / `Z2ROOT-BUILD-PARITY-HANDOFF.md §13`）の推奨対処①を実装。

- **背景**: 監査で io_uring(425-427) が z2root 自身のフィルタでは `RET_ALLOW`（素通り）で、安全性が**外側の Android untrusted_app seccomp の SIGSYS → トレーサの ENOSYS 化に依存**していたと判明。io_uring は submission ring 経由で openat/read/write 等を非同期実行し ptrace/seccomp トラップを丸ごと素通りする（パス変換も fakeroot も効かない最危険経路）。
- **修正（0.8.81, `z2root.c` `install_seccomp_filter`）**: BPF に **`SECCOMP_RET_ERRNO(ENOSYS)` 終端 + deny 比較ブロック**を追加し、`kDenySyscalls`=io_uring_setup/enter/register を **z2root 自前で ENOSYS** へ倒す。フィルタは `arch→LD nr→deny 比較→trace 比較→ALLOW/TRACE/DENY` の 3 終端へ拡張。NDK aarch64 で compile OK（`build-z2root.sh`）。
- **挙動**: Android が io_uring を `RET_TRAP` する現行端末では優先順 (TRAP>ERRNO) で従来どおり SIGSYS 経路＝**挙動不変**。Android が弾かないコンテキストでのみ本 ERRNO が効く（防御の多層化）。
- **要・実機 e2e**: 0.8.81 APK 導入後、node/claude 対話起動に退行が無いこと（現行端末は Android TRAP 経路のままなので退行は出ない想定）。`liburing` 直叩きでの ENOSYS フォールバック確認は任意。
- **残（監査で見つけた別ギャップ・未対処）**: `truncate(45)`・xattr by-path(5/6/8/9/11/12/14/15)・`name_to_handle_at`/`open_by_handle_at` が**トレースもパス変換もされず絶対パスでホスト直行**。発火頻度が低いので後回し（§13.4）。

## 2026-06-12 追記 — foss Alpine で zsh 起動失敗 (`execve(/bin/zsh) ENOENT`) を根治（0.8.80）コミット済・**push 済**(`555afd6`)

前セッション宿題「zsh デフォルトのエラー再発」を**実機エラー採取の上で根治**。

- **実機エラー（ユーザー採取）**: `z2root: execve(/data/data/com.zerotoship.z2term.foss.debug2/files/distros/alpine/bin/zsh) failed: No such file or directory [プロセス終了 exitCode=-1]`。z2root が `/bin/zsh` を**そのまま execve**して ENOENT＝`resolveShell` がフォールバックせず要求値を返していた。
- **真因（前セッションの仮説と異なる）**: 前セッションは `DEFAULT_LOGIN_SHELL=/bin/zsh` の `ifBlank` 握り潰しを疑ったが、`resolveShell`(ProotLauncher.kt) は `[requested, fallbackShell, /bin/sh, /bin/bash]` の順でフォールバックする実装で本来 zsh 不在でも倒れるはず。**実際の真因は `shellExists` の存在判定**: `File.exists()` が **絶対 symlink をホストの filesystem root 起点で解決**するため、Alpine minirootfs の `/bin/sh -> /bin/busybox`（絶対リンク）を host:/bin/busybox と誤解して**不在判定**。foss 素 minirootfs はシェルが軒並み busybox への絶対 symlink なので**フォールバック候補が全滅**し、最後の `return requested`(=/bin/zsh) に落ちて execve ENOENT になっていた。dev 環境の `/tmp/alpine/bin/sh -> /bin/busybox` で挙動を確認。
- **修正（0.8.80, `ProotLauncher.kt`）**: `shellExists` を `pathPresent` ヘルパ経由にし、`File.exists()` が false でも `Files.exists(path, NOFOLLOW_LINKS)` で**リンク自体の存在**を見るよう変更。ゲスト名前空間では絶対 symlink が rootfs 内で正しく解決されるので、busybox symlink のシェルを存在として扱う。これで foss Alpine は zsh 不在時に `/bin/sh`(busybox) へ正しくフォールバックして起動する。SHELL env 解決 `resolveLoginShell` も同じ `shellExists` を通るため同時に解消。
- **要・実機 e2e**: 0.8.80 APK 導入後、foss Alpine タブで（zsh 未同梱の素 minirootfs でも）プロンプトが出て起動することを確認。full Alpine（zsh 同梱）は従来どおり zsh で起動。
- **残（宿題から継続）**: 設定 UI は foss Alpine で `/bin/zsh` を選択状態＋「未インストール」警告のまま表示する（起動は sh へフォールバックして成功）。デフォルトをディストロ既定へ委ねる UI 改修は未着手（別途要否判断）。

## 2026-06-12 追記 — foss フレーバー整備（0.8.79: 表示名分離・proot 除外検証・ライセンス整合）コミット＋push 依頼

このセッションの成果と**次セッションへの宿題**。

### 完了した変更（このコミットに含む）
- **foss release の表示名分離**: foss flavor に `manifestPlaceholders["appLabel"] = "Z2Term FOSS"` を付与し、full release とランチャー上で区別できるようにした（debug buildType は `"Z2Term dbg2"` で上書き）。`build.gradle.kts`。
- **proot/talloc/Alpine rootfs を foss APK から完全除外（検証済）**: AGP の sourceSet マージで `src/main/jniLibs` が全 variant へ寄与していたため proot が foss に漏れていた。prebuilt 群を `src/full/jniLibs` へ移し、main には z2root（ソースビルド成果）だけ残す構成に変更。`buildZ2rootNative` の wiring を全 flavor の `merge*JniLibFolders` に拡張。**foss debug APK を `unzip -l` で検証 → `libz2accept.so`/`libz2root.so`/`libz2term.so`/`libc++_shared.so` のみ、proot/talloc/alpine いずれも 0 を確認**。`build-proot.sh`/`build-z2root` 出力先・`.gitignore`・`assets/README.md` も src/full へ追従。
- **ライセンス表示を実態に整合**: `OssComponents.kt` に `onlyFullFlavor` フラグを追加し、PRoot/talloc を foss 一覧から除外（`forCurrentFlavor()` が `IS_FOSS` 時にフィルタ）。foss は z2root（本体 GPL-3.0 に含むソースビルド）なので別エントリ不要。foss で同梱しない物を表示する誤告知を解消。
- **起動時ダウンロード確認**: `TerminalSession.downloadOnStartSpec()` を追加し、配布物が同梱ディストロを持たず未取得なら起動前に `DownloadConfirmDialog` を表示（`TerminalScreen` 内、`HostKeyVerificationDialog` の後）。
- **ビルドは CPU 半分を既定化**: `scripts/gw.sh` が `--max-workers=nproc/2` を自動付与（明示 `--max-workers`/`--no-parallel` は尊重）。`CLAUDE.md` に「ビルド運用」節を追加。
- **DESIGN-SPEC（ja/en）**: foss 行を「proot/talloc prebuilt 非同梱・z2root をソースから」に更新済。版数行も 0.8.79/87 へ。

### ⛔ 次セッションへの宿題（このコミットには**含めない**）
- ~~**zsh デフォルトのエラーが直っていない（再発・要根治）**~~ → **0.8.80 で根治済**（上記 2026-06-12 追記参照）。真因は前セッション仮説の ifBlank 握り潰しではなく `shellExists` の絶対 symlink 誤判定だった。`Files.exists(.., NOFOLLOW_LINKS)` でリンク自体の存在を見るよう修正し、busybox symlink シェルへ正しくフォールバックするようにした。**実機 e2e は未**。
- **APK サイズ ~70MB は foss らしくない**: ユーザー指摘。**ただし観測した 74MB は foss "debug" APK**（R8/proguard 未適用・未 minify）。**foss "release" は 20.9MB**（前項の 2026-06-12 release 記録）。次セッションで release を再ビルドしサイズ実測・必要なら resource shrink / abiSplits 等を検討。debug を foss の最終サイズと誤認しないこと。
- **ライセンス表示の広域レビュー（保留）**: 今回は proot/talloc の foss 除外のみ。`assets/licenses/` 全 48 件と `OssComponents` の対応・SPDX・対応ソース URL の網羅性は未レビュー。

## 2026-06-12 追記 — foss リリースビルド成立（署名込み・検証済）

- **foss release ビルド成功・署名確認**: `app/build/outputs/apk/foss/release/app-foss-release.apk`（**20.9MB**）。`apksigner verify` rc=0 / `CN=Z2Term, O=ZeroToShip, C=JP`（full と同一 release 鍵）、package=`com.zerotoship.z2term.foss`、versionCode 86 / versionName `0.8.78-alpha-foss`。prebuilt 非同梱（full 195MB に対し 20.9MB、proot/z2root/alpine いずれも 0、ライセンス 48 件・`.so` 2 件のみ）。これで当面ゴールだった **foss フレーバーのリリースビルド**（[[project_foss_release_build_gate]]）が署名込みで成立。
- **「foss 未署名」騒ぎの結論**: 途中で観測した「未署名 foss APK」は **phantom ビルド（実際には起動しなかった background gradle）が残した古い成果物**。`build.gradle.kts` の署名は `buildTypes.release.signingConfig = signingConfigs("release")` で **両フレーバー対称**＝設定上の欠陥は無し。クリーン再ビルドで foss も正しく `CN=Z2Term` 署名された。**教訓: background ビルドはログファイルの実在・GradleDaemon プロセス・出力 APK のタイムスタンプで「本当に走ったか」を確認してから成果物を信じる**。
- **ライセンス表記（確認済）**: foss APK は `assets/licenses/THIRD-PARTY-NOTICES.md` + 48 ライセンスファイルを同梱。`LicensesScreen.kt` は `BuildConfig.IS_FOSS` 分岐で「この FOSS ビルドは第三者バイナリを同梱せず実行時 DL する」注記を表示。表記は正しい。
- **残・実機 e2e のみ**: full=z2root 経由で Alpine の ET_EXEC（claude 等）が loader-exec で起動するか / foss=初回起動の distro ダウンロード＋ライセンス画面描画。

## 2026-06-12 追記 — z2root: musl ld.so の ET_EXEC 明示起動不可を根治（0.8.78, `--loader-exec`）コミット済・**push 済**・full release ビルド済

- **真因**: musl の `ld.so` は **動的 ET_EXEC（非PIE）を「コマンドとして明示起動」できず** `Not a valid dynamic program` で落ちる。このため Alpine(musl) では glibc/musl の ET_EXEC バイナリ（claude 本体・`cc` 等）が z2root で起動できなかった（[[project_alpine_glibc_claude]] の「glibc 一式を Alpine に入れて回避」を根本対処に置換）。
- **修正（0.8.78, `z2root.c`）**: `--loader-exec <ld.so> <prog> <argv0> [args...]` 経路を新設。本体と `ld.so` を両方 `mmap`（`map_img`）し、**カーネルが `PT_INTERP` 経由で exec したのと同じ初期スタック/auxv**（`AT_PHDR`/`AT_PHENT`/`AT_PHNUM`=本体 phdr、`AT_ENTRY`=本体エントリ、`AT_BASE`=`ld.so` の load base）を組んで `ld.so` のエントリへ分岐（`load_exec_via_interp`）。musl は「インタプリタとして起動された」と判定し本体を relocation して起動する（proot loader 相当）。
- **振り分け**: `plan_exec` で **interp basename が `ld-musl*` かつ対象が ET_EXEC のときだけ**。glibc `ld.so`（ET_EXEC を明示起動で受ける既存 Arch claude 経路）・PIE は非対象として温存。`use_loader` 無効時は従来経路へフォールバック。
- **cmdtest 修正**: `scripts/z2root-cmdtest.sh` の VCS グループを git 未導入時 skip 化＋末尾 echo による rc マスクを除去し、実 rc を非ゼロ終了一覧へ反映するよう修正。
- **要・実機 e2e**: 本修正入り APK 導入後、Alpine(musl) で claude 本体・`cc` 等の ET_EXEC が z2root タブで起動することを確認。
- **版数メモ**: 0.8.77/versionCode 85 は欠番（84→86）。

## 2026-06-11 追記 — 端末 UX 改善（0.8.76: コピー改行 + クリップボード履歴 + z2version OS + スクロールバー）push 済・ビルド済・バックアップ済

- **(1) コピーの改行修正**: 選択コピー `TerminalBuffer.getRangeText` を、ハード行の行末スペース埋めを除去し、ソフト折り返し行 (`wrapped`) は改行を入れず連結するよう修正（旧: 全行右端まで空白埋めで改行が埋もれ、長押しコピーが空白だらけだった）。
- **(2) クリップボード履歴 + SYSTEM 同期**: `clipboard/ClipboardHistoryStore`（`object`, `filesDir/clipboard_history.json` 永続化, MAX 50 件, dedup/LRU）を追加。`OnPrimaryClipChangedListener`（Application 生存）+ `MainActivity.onResume` 取り込み（前面復帰時）+ 端末コピー経路からの直接 record の 3 経路でシステムクリップボードに同期。UI は 📋 貼付ボタンの**ダブルタップ**で `ui/clipboard/ClipboardHistorySheet`（ModalBottomSheet）を開き選択貼り付け（端末は `pasteText`、GUI は primaryClip + keysym 送出）。
- **(3) z2version に実行中 OS**: `ProotLauncher.ensureVersionScript` でゲストの `/etc/os-release`(PRETTY_NAME) と `uname -srm` から **os / kernel** 行を追加表示。
- **(4) 掴めるスクロールバー**: `TerminalScreen` に `TerminalScrollbar`（端末右端, scrollback>0 かつ非選択時のみ, つまみドラッグで `setScrollOffset`）を追加。
- **実機未検証**: 特にクリップボード履歴のダブルタップとスクロールバーのドラッグは実機確認待ち。

## 2026-06-11 追記 — 0.8.75 push 済 + full release ビルド済・バックアップ済 + ビルドラッパー gw.sh 追加

- **push 済**: `b43e932`(0.8.75-alpha(83) 本体) → `7843b93`(gw.sh/accept4 シム, scripts のみ＝版数据え置き) を origin/main へ push。
- **full release ビルド済**: `bash scripts/gw.sh :app:assembleFullRelease` で `BUILD SUCCESSFUL`。`app/build/outputs/apk/full/release/app-full-release.apk`（195MB, versionCode 83/0.8.75-alpha, release 署名 `CN=Z2Term,O=ZeroToShip,C=JP`, `libz2root.so` 452928B 同梱確認, stale 回避で中間物 rm 後に再ビルド）。
- **バックアップ済**: `/root/z2-apk-backup/app-full-release-0.8.75-7843b93-z2root.apk`（+`.md5` `be49bf88321331549727c1514cba7378`）。
- **ビルド環境の恒久対処（gw.sh）**: この環境は libc `accept()` が ENOSYS を返し Gradle デーモンの accept ループが落ちる。`scripts/gw.sh` が `accept()` 破損時だけ `scripts/accept4-shim.c`(→`SYS_accept4`)を自動ビルド＆ `LD_PRELOAD`。**以後オンデバイスのビルドは `bash scripts/gw.sh <task>` を使う**（素の `./gradlew` 代わり）。詳細は下記「環境・運用メモ」。
- **次セッション**: 0.8.75 APK を本体 UI で導入し、項目4/5 の実機再検証（Alpine で `~/.claude/downloads` に musl 版を入れても Arch の glibc 本体が無傷＝隔離が効く）を実施。

## 2026-06-11 追記 — 項目4 再発の真因（z2root の bind 登録順一致）を特定・修正（0.8.75, 未push）

- **項目4 が 0.8.73 でも z2root で再発**（実機検証）: Alpine で musl 版 claude を DL → **Arch の glibc 本体まで上書きされ両方起動不可**。`.claude/downloads` 隔離オーバーレイが z2root で効いていなかった。
- **真因**: `z2root.c` の `translate_abs`/`host_to_guest` が **bind を登録順の最初一致**で解決。登録順は `/root`(共有HOME) → `/root/.claude/downloads`(隔離) なので、子パスが先に親 `/root` に一致し共有 HOME へ解決され、隔離 bind に届かなかった。proot は最長一致なので効いていた（engine 差）。
- **修正（0.8.75）**: 両変換関数を**最長一致（最も具体的＝guest_len 最長の bind 優先）**へ。スタンドアロン単体テストで `/root/.claude/downloads/claude`→オーバーレイ / `.credentials.json`→共有 / `/etc/*`→rootfs / 逆変換すべて通過。`build-z2root.sh` でコンパイル確認済。
- **暫定回避（修正版導入まで）**: Alpine 側は musl 版を `/root` 外（例 `/opt/claude`）に置き `/usr/local/bin/claude` をそこへ向ける。`/root` 配下に置かなければ共有衝突しない。
- **要・実機再検証**: 0.8.75 APK 導入後、Alpine で `~/.claude/downloads` に musl 版を入れても Arch の glibc 本体が無傷であること（＝隔離が真に効くこと）を確認。Arch 本体は `/root/.claude/claude-glibc.bak` に退避あり。

## 2026-06-11 追記 — 端末の絵文字表示 + 日本語 IME 予測変換の改善（0.8.74, 未push）

- **絵文字表示**: BMP 外 (😀 等) は左セル=高サロゲート・右セル (`wideCont`)=低サロゲートに 2 セル格納されるが、描画/コピーで右セルを捨てて孤立サロゲート＝豆腐(?)になっていた。`TerminalRenderer.glyphAt` で両セルを結合して 1 グリフ描画（カーソル重畳も）。コピー `TerminalBuffer.getRangeText` / 行抽出 `TerminalRow.toText` も低サロゲートを出力するよう修正。Search/UrlFinder は列マッピングの都合と用途上スコープ外（未修正）。
- **IME #1（長文で「絶対使わない組み合わせ」が優先）**: 一括予測確定 `commitFull` を「文全体 1 キー学習」からブロック単位 (`fullPredictionBlocks` の各 `(読み→表層)` + ブロック間 bigram) 学習へ変更。頻用ブロックが別の文でも再利用される。
- **IME #2（漢字単体の学習が効かない）**: 学習ブロックのコスト割引が同じ読みの全表層へ一律に効いて辞書最小コストの別表層が勝っていたのを、ユーザー確定表層だけに効かせるよう `KkcConverter.nbest` を修正。
- **検証**: `testFossDebugUnitTest`（`KkcEvalTest` 回帰ガード / `HistoryRerankerTest` / `CollocationRerankerTest`）BUILD SUCCESSFUL。learnedBlock は JVM テストで null＝eval 値は不変。**実機での実打体感は未確認**。

## 2026-06-11 追記 — 項目7 コマンド群テスト完了・push（`ae201fe`）

- `scripts/z2root-cmdtest.sh` を追加（app 変更なし＝版数据え置き）。詳細は下記「直近で完了したこと」項目7。**push 済**。
- **テスト思想の確定（ユーザー指示）**: 「過去再現の狙い撃ち」ではなく「**今後も壊れやすいコマンドがエラーなく動くことを広く確認**＝コマンド毎の後追い修正をやらない」。`claude --version` は通るのに本体が起動しない等「軽い subset は OK でも実体はダメ」を捕まえる。**cd/ls 等の自明系は入れない**。**どの OS でも同一に動く**（POSIX sh/busybox ash・未導入は skip・pkg mgr 自動判別）ことが要件＝ディストリ横断比較に使う。
- **新たに洗い出した構造的リスク**（コマンドテストでは捕まらない＝seccomp フィルタ側で要監査）: `io_uring`（ptrace/seccomp を丸ごとバイパス＝最危険）/ `statx`・`openat2`（旧 stat/openat だけフックだと変換・fakeroot を素通り）。
- 当面ゴールは **foss フレーバーのリリースビルド**。下記 残検証を通してから着手（[[foss-release-build-gate]]）。

## 2026-06-10 追記 — 項目4 の真因を実機で特定し修正（0.8.73, `b77bd43` push 済）

- **実機検証で z2root/Arch 稼働確認**: `claude --version`=2.1.145 exit=0、エンジン=z2root（親 `z2root --loader-noreloc …`）、distro=archlinux。
- **項目4（Alpine で claude 起動不可・Arch まで巻き込まれる）の真因確定**: Claude Code の native 本体は `~/.claude/downloads/claude`（233MB の glibc ELF, `interpreter /lib/ld-linux-aarch64.so.1`）。`/usr/local/bin/claude` はその symlink。**`.claude` は 0.8.72 の `isolatedHomeSubdirs` に入っておらず共有**だったため、Alpine(musl) で claude を起動→musl 本体を同じ共有パスへ上書き→Arch(glibc) が `Not a valid dynamic program` で起動不可、の相互破壊が起きていた。Alpine 実機エラー: `ld-musl-aarch64.so.1: /root/.claude/downloads/claude: Not a valid dynamic program`。
- **修正（0.8.73, `ProotLauncher.isolatedHomeSubdirs`）**: `.claude/downloads` を隔離対象に追加（`.claude` 直下の `.credentials.json`/設定/projects は共有のまま native 本体だけ distro 別）。ネストパスは proot `-b src:/root/.claude/downloads` / chroot は `mkdir -p`+bind+先行 umount で対応（既存コードがネスト親を作るので追加実装不要）。
- **移行は今回1回だけ**（住所が「共有1個」→「distro別」へ変わるため）。`filesDir`(=overlay) はアプリ更新で消えないので、以降の版上げで再退避は不要。
  - **退避済**: 現 glibc 本体を共有領域へ複製済 → `/root/.claude/claude-glibc.bak`（233MB, md5 `a63743d1cd48e5a79cd722efe4654941`）。`.claude` 直下＝隔離対象外なので 0.8.73 導入後も Arch から見える。
- **APK ビルド済・検証済**: `app/build/outputs/apk/full/release/app-full-release.apk`（195MB）。`aapt2 badging`=versionCode 81/0.8.73-alpha、`apksigner`=CN=Z2Term,O=ZeroToShip,C=JP（release 署名）、classes.dex に `.claude/downloads` 反映確認、同梱 `.so` 全在中・libz2root に `loader-noreloc` マーカー（stale でない）。fullRelease 中間物を rm してから再ビルド。
- **要・実機再検証（次セッション。導入後の手順）**:
  1. 本体 UI で 0.8.73(81) APK を導入。
  2. **Arch（再DL不要）**: `cp -a /root/.claude/claude-glibc.bak /root/.claude/downloads/claude` → `claude --version`（symlink `/usr/local/bin/claude` はそのまま復活）。
  3. **Alpine（musl 版を1回だけ取得）**: 進捗バー付き DL（公式 install と同じ URL 機構、`linux-arm64-musl`）:
     ```sh
     BASE=https://downloads.claude.ai/claude-code-releases
     VER=$(curl -fsSL "$BASE/latest")
     mkdir -p ~/.claude/downloads
     curl -fL --progress-bar -o ~/.claude/downloads/claude "$BASE/$VER/linux-arm64-musl/claude"
     chmod +x ~/.claude/downloads/claude
     ln -sf ~/.claude/downloads/claude /usr/local/bin/claude
     claude --version
     ```
     （`latest` は今 2.1.172。`curl -fsSL https://claude.ai/install.sh | sh` でも可だが無進捗）
  4. **期待結果**: Alpine/Arch 双方で claude 起動可・互いに干渉しない（= 項目4/5 確証）。隔離マーカー: Arch 側に設置済 隔離=`/root/.npm-global/Z2_ISOLATION_MARKER_ARCH.txt`・`/root/.cargo/…`（他distroで ABSENT が正）、共有=`/root/Z2_SHARED_MARKER.txt`（全distroで PRESENT が正）。


> トピック別の深掘りは別ハンドオフへ: z2root ビルド/parity = [`Z2ROOT-BUILD-PARITY-HANDOFF.md`](Z2ROOT-BUILD-PARITY-HANDOFF.md)、IME 設計/評価 = [`IME-PHASE0-HANDOFF.md`](IME-PHASE0-HANDOFF.md) / [`IME-ARCHITECTURE-RESEARCH.md`](IME-ARCHITECTURE-RESEARCH.md) / [`IME-EVAL.md`](IME-EVAL.md)。本書は「今どこ・次なに」の横断インデックス。

## 直近で完了したこと

- **項目7 — z2root コマンド群横断テストスクリプト（`scripts/z2root-cmdtest.sh`）**（未 push / app コード変更なし＝版数据え置き）
  - **思想（ユーザー指示で確定）**: 「過去再現の狙い撃ち」ではなく「**今後も壊れやすいコマンドがエラーなく動くことを広く確認**＝コマンド毎の後追い修正をやらないで済む」回帰スモーク。`claude --version` は通るのに claude 本体が立ち上がらない／git の重い操作がコケる等「軽い subset は OK でも実体はダメ」を捕まえる。**cd/ls/cp 等の自明系は入れない**（信号が薄い）。
  - **どの OS でも同じに動く**のが要件: POSIX sh／busybox ash 互換・**未導入コマンドは fail でなく skip**・パッケージマネージャ自動判別。各ディストリ(Alpine musl↔Arch/Ubuntu glibc)で回して「非ゼロ終了一覧が空」が揃えば OS 差なく健全＝ディストリ同等性の検証にも使う。
  - 全 10 グループ（z2root の難所を踏む特殊系に集中）: ①ランタイム実起動（claude headless と `--version` 対比・node spawn・python venv/mp/ssl・ripgrep）②VCS 重い操作（clone/gc/checkout＝hardlink/pack/rename）③パッケージ管理（apt/apk/dnf/pacman・pip/venv・npm＝fakeroot/fork-exec/symlink）④pty/端末（script/tmux/stty・`/dev/pts`、dropbear ループバックは `RUN_SSHD=1`＝SSH reset 注意）⑤/proc・fakeroot 境界（`readlink /proc/self/cwd`・maps・ps・top）⑥ビルド（cc execve chain＋ld.so reloc）⑦パス変換/symlink canonicalize（cwd・shebang・tar）⑧ディスク/FS（dd・mkfs・parted をファイル相手に。losetup/mount は `RUN_PRIV=1`＝非 root では EPERM が正常）⑨IPC/特殊 syscall（AF_UNIX・FIFO・flock・inotify・xattr・copy_file_range・nested ptrace(strace/gdb)・Go 生 syscall・sqlite3・rsync）⑩名前解決/TLS（getent・curl TLS・nslookup）。
  - フラグ: `SKIP_NET`/`SKIP_BUILD`/`RUN_SSHD`/`RUN_PRIV`。出力は画面＋`/tmp/z2root-cmdtest-<時刻>.log`、末尾に非ゼロ終了一覧。proot タブで同じものを流せば対照ログ（[[feedback_proot_z2root_equivalent_just_build]] に沿いエンジン判定や proot 推奨はしない）。
  - **コマンドで捕まえにくい構造的リスク**（別途 seccomp フィルタ側で監査）: `io_uring`（ptrace/seccomp を丸ごとバイパス＝最危険）・`statx`/`openat2` のフック漏れ。
  - 検証: `sh -n` 構文 OK / dev 環境で `SKIP_NET=1 SKIP_BUILD=1` 実走 rc=0（AF_UNIX の sun_path 変換・FIFO・flock・xattr・copy_file_range・gdb・sqlite まで OK 確認。非ゼロは dev 環境の `/dev/shm` 不在による python multiprocessing 1 件の偽陽性のみ）。**z2root 実機での実走は未**。
- **項目6 — git管理外の同梱物を1コマンドで一括収集（`scripts/build-bundle.sh` 拡張）**（未 push / app コード変更なし＝版数据え置き）
  - 真意の再確認: ユーザーの困りごとは「PC/スマホで *byte 完全一致* の APK が欲しい」ではなく「**git管理外の同梱物の "集め方" が分からず、環境ごとにバラついて別物 APK になる**」こと。版違いは許容。→ 解は「収集手順の統一」。
  - 従来 `build-bundle.sh` は proot/rootfs/fonts の **3 ステップで z2root を呼んでいなかった**（＝clone 後に z2root.so/z2accept.so が揃わない穴）。**`build-z2root.sh` を追加して 4 ステップ化**し、最後に git管理外の全同梱物（proot3・z2root2・fonts3・rootfs1）の **OK/MISS マニフェスト点検**を追加（欠落で `exit 1`）。
  - `SKIP_ROOTFS=1` で fakeroot 不可環境（この開発環境含む）でも rootfs 以外を収集可。ヘッダ docs と README §Setup §1 を「1 コマンド収集」に刷新。
  - 検証: `bash -n` 構文 OK / マニフェスト点検ロジックを現存ファイルで OK 確認。**生成スクリプト実走（network/fakeroot）は未**。
- **0.8.72-alpha(80) — HOME のディストリ別隔離（項目5。項目4 も同時解消見込み）**（`fb27d53`, push 済）
  - `/root` 全体は共有のまま、arch 依存サブディレクトリだけをディストリ別オーバーレイで上書き bind。`isolatedHomeSubdirs` = `.local .cache .npm .npm-global .nvm .cargo .rustup .config`（ユーザー選択の「標準セット」）。
  - `ProotLauncher`: `homeOverlayDir`(`filesDir/home_overlay`) + `isolatedHomeBinds(distroId)` ヘルパ追加。proot は `-b shared_home:/root` の後に各サブ bind を追加。chroot(`chrootBootstrap`)も `mount -o bind $SHOME $RFS/root` の後に各サブを `mkdir -p`+bind、掃除 umount は `root` より先にオーバーレイを lazy umount。
  - **狙い**: musl(Alpine)↔glibc(Arch/Ubuntu/Kali) で HOME 内 native(npm global の node/claude・`~/.cache` のアドオン・nvm の node 本体)が混ざって壊れる問題を根治。→ 項目4(Alpine で claude 起動不可)も同根のため解消見込み。
  - **移行注意**: 既存 `shared_home/<sub>` の中身はオーバーレイに覆われ各ディストリから見えなくなる（消えてはおらず影に入るだけ）。
  - 検証: `compileFossDebugKotlin` 緑（`--max-workers=4`）。**実機実打は未確認**（full release APK を本体 UI 導入後に各ディストリで claude/node が独立することを要確認）。
  - docs 反映済: README / DESIGN-SPEC(ja/en) HOME 隔離節 / 本書。
- **0.8.71-alpha(79) — 日本語予測変換のブロック分割を頻度学習で動的化**（`b697415`）
  - 確定済み `(読み→表層)` の頻度・直近性から `ImeHistoryStore.learnedBlock(読み)` が `(最頻表層, コスト下げ幅)` を返す。
  - `KkcConverter.nbest` のラティスで、2 文字以上の読みが学習ブロックに一致したらノードコストを下げる／辞書外は学習表層で合成ノード(`lc=rc=0`)を追加。`bunsetsu`/`headBunsetsuLen` 経由で自動分割に効く。
  - 強さ: `BLOCK_BASE_BONUS=3000` + count比例1500(上限count4) + 直近1000。カタカナ化ペナルティ(4000)+接続コストを 1〜2 回の確定で上回る → 「こまんど」→「こ」「まんど」の誤分割が確定後は自動で 1 ブロック「コマンド」にまとまる。未学習読みは挙動不変。
  - 検証: `compileFossDebugKotlin` 緑 / `*Kkc*`・`*Reranker*` ユニットテスト緑（`--max-workers=4`）。**実機実打は未確認**（APK を本体 UI 導入後に要確認）。
  - docs 反映済: README / DESIGN-SPEC(ja/en) §6.2.1 / HANDBOOK(ja/en)。
- **0.8.70-alpha(78)**（`afc9af8`）: `z2version` コマンド追加 / 起動時タブを常に新規1つ / 7タップトグルをタップ不可ディレイ化。

## ユーザー要望 8 項目の状態

| # | 要望 | 状態 |
|---|---|---|
| 1 | アプリ版数確認コマンド | ✅ done (`z2version`, 0.8.70) |
| 2 | 起動時タブを 1 つに | ✅ done (常に新規1タブ, 0.8.70) |
| 3 | 7タップトグルをタップ不可ディレイに | ✅ done (3秒タップ不可, 0.8.70) |
| 4 | Alpine で claude 起動不可（簡単なら直す） | 🔧 真因特定（`.claude/downloads` が共有で musl↔glibc が native 本体を上書き合う）→ 0.8.73 で `.claude/downloads` を隔離追加。**APK ビルド済・検証済 / 実機再検証は次セッション**（手順は上記 追記） |
| 5 | 複数ディストリの分離仕様 | ✅ done（HOME の arch 依存サブディレクトリのみディストリ別オーバーレイ bind, 0.8.72 + `.claude/downloads` 0.8.73）。**実機未確認** |
| 6 | git 管理外も含む再現ビルドスクリプト | ✅ done。`scripts/build-bundle.sh` を「git管理外の同梱物を1コマンドで全部収集」に拡張（従来 proot/rootfs/fonts の3つに **z2root を追加** し4ステップ化＋最後に全同梱物の OK/MISS マニフェスト点検で欠落時 exit 1）。`SKIP_ROOTFS=1` で fakeroot 不可環境に対応。docs（README §Setup）反映済。**注**: 真意は byte 完全一致ではなく「収集手順の統一」（版違いは許容） |
| 7 | 大量コマンド群の z2root 横断テストスクリプト | ✅ done。`scripts/z2root-cmdtest.sh`（ゲスト内で直接実行・未導入は skip・末尾に非ゼロ終了一覧・busybox ash 対応の tee 再 exec）。**壊れやすい特殊系に集中**した全 10 グループ（ランタイム実起動/VCS 重操作/パッケージ/pty/proc/ビルド/パス変換/ディスク/IPC・特殊 syscall/名前解決）。`SKIP_NET`/`SKIP_BUILD`/`RUN_SSHD`/`RUN_PRIV`。proot タブで流せば対照ログ。**z2root 0.8.83(Arch) 実機で全文実走 → 非ゼロ終了 0 件**（2026-06-13。§11 絶対パス seccomp e2e 追加・ハーネス偽陽性3件修正後）。残は他ディストリ/proot 対照（詳細は冒頭 2026-06-13 追記） |
| 8 | 予測変換のブロック分け改善（頻度学習） | ✅ done (0.8.71, `b697415`) |

## 次セッションの作業候補（優先順は未確定・要ユーザー指示）

1. **項目5/4 の実機確認（0.8.73 で）**: 本体 UI で **0.8.73(81) の APK をビルド済・検証済**（`app/build/outputs/apk/full/release/app-full-release.apk`、195MB、release 署名 `CN=Z2Term`、versionCode 81、`.claude/downloads` 反映確認、`.so` 全在中・loader-noreloc あり）を導入 → 上の「要・実機再検証」手順（Arch は退避 `.bak` を復元／Alpine は musl 版を進捗 DL）で双方独立起動を確認。
2. **項目8 の実機確認**: 日本語フリックで「こまんど」を 1〜2 回まとめ確定 → 次回自動 1 ブロック化を体感確認。
3. **項目6 の実走確認**: 一度クリーン環境（or `SKIP_ROOTFS=1`）で `bash scripts/build-bundle.sh` を流し、4 ステップ＋マニフェスト点検が緑になることを確認（今回は構文/点検ロジックのみ確認、生成スクリプト実走は未）。
4. ~~**項目7（コマンドテスト）の z2root 実機実走**~~ → **Arch + Alpine/Ubuntu/Kali で完了**（Arch=2026-06-13 z2root 0.8.83 / 他 3=2026-06-13 z2root 0.8.86、いずれも z2root 退行 0 件・§11 含む。0.8.84 の Kali python3 byte-compile/大 argv も実機裏取り済。詳細は冒頭の横断 e2e 追記）。**残**: proot タブ対照（ユーザー方針で対照値としてのみ）／claude・node・sqlite 等を導入済みタブでの実起動経路確認。
5. ~~**seccomp フィルタ監査（コード側）**~~ → **コード監査完了**（2026-06-12, `Z2ROOT-BUILD-PARITY-HANDOFF.md` §13）。結論: `statx`/`openat2` はトレース＆パス変換済で **OK**。`io_uring`(425-427) は z2root 自身は RET_ALLOW だが **Android untrusted_app seccomp が SIGSYS で弾き z2root が ENOSYS に化かして安全な旧経路へフォールバック**＝条件付き安全（Android 依存。自前 deny は未）。監査中に **新ギャップ**を発見: `truncate(45)`・xattr by-path(5/6/8/9/11/12/14/15)・`name_to_handle_at`/`open_by_handle_at` が**トレースもパス変換もされず絶対パスでホスト直行**。推奨: ①io_uring を明示 deny(SECCOMP_RET_ERRNO ENOSYS) → **0.8.81 で実装済**②truncate/xattr by-path を変換追加 → **0.8.82 で実装済**③`name_to_handle_at` を変換追加 → **0.8.83 で実装済**（`open_by_handle_at` は path 無＝非対象）（§13.4）。**コード監査ギャップは解消**。残るは ①②③の実機 e2e（退行無し・絶対パス変換の裏取り）のみ。
6. **上記 A〜C が緑になったら foss リリースビルド**（当面ゴール, [[foss-release-build-gate]]）。

> 段取りの目安: 新規コード実装は無く、ここからは**実機 e2e フェーズ**。1〜4 は「APK を実機導入して検証する」。5 のコード監査は完了済（残は実機裏取り）。これらを通して 6 のリリースへ。
>
> **e2e の前提（重要）**: 0.8.81〜0.8.83 で native(`libz2root.so`)を変更したため、**まず 0.8.83 の full release APK を再ビルド**（`.so` は `unzip`+`strings` で 0.8.83 が同梱されているか確認、fullRelease 中間物を rm してから再ビルド）→ 本体 UI で導入してから e2e を流す。1 つの 0.8.83 APK で項目4/5/7/8 と seccomp 3点（io_uring/truncate・xattr/handle）の e2e をまとめて取れる。seccomp の裏取りは `scripts/z2root-cmdtest.sh` に絶対パス `truncate`/`setfattr`/`getfattr`/`name_to_handle_at` のケースを足して z2root タブで実走し、rootfs 配下を指す＆退行が無いことを確認するのが具体策。

## 環境・運用メモ（毎回踏むので明記）

- **CPU は基本半分**で稼働。gradle は `--max-workers=4`（8 コアの半分）を付ける（[[feedback_build_normal_parallel]]）。
- **proot と z2root は同等扱い**（ユーザー方針）。ビルド前にエンジン判定 → フリーズ警告 → proot 推奨を繰り返さない。頼まれたタブのままビルドする（[[feedback_proot_z2root_equivalent_just_build]]）。過去のフリーズ記録はあるがユーザー判断優先。
- **オンデバイスでの Gradle ビルドは `bash scripts/gw.sh <task>` を使う**（素の `./gradlew` 代わり）。この環境は libc `accept()` が ENOSYS を返し、JDK17 の `sun.nio.ch.Net.accept`→Gradle デーモンの accept ループが落ちて `Could not connect to the Gradle daemon` になる。`gw.sh` は libc `accept()` が壊れている時だけ `scripts/accept4-shim.c`(accept→`SYS_accept4` syscall 橋渡し)を自動ビルド＆ `LD_PRELOAD` する。PC など正常環境では素の `./gradlew` を呼ぶだけで無害。**不用意に `--stop`/`pkill` でデーモンを殺さない**（古い常駐デーモンを使い回していたから動いていただけで、殺すと根本制約が露出する）。
- APK 投入前に **`unzip`+`strings` で `.so` 中身を確認**（`assembleFullRelease` が古い jniLibs を stale 同梱する。再ビルドは fullRelease 中間物を rm してから）。
- 開発環境は **full release アプリの中**で動く＝その APK を消すと作業環境ごと落ちる。install は本体 UI で（pm/cmd/adb 不可）。
- 稼働 `.so` 版数は **`/proc/self/mem` の text md5 照合**が正本（readlink 19B / `Z2ROOT_LOADER_DEBUG` での版数判定は無効）。
- コミット規約（CLAUDE.md）: app 変更は版数 +1 を含めて 1 コミット / 関連 docs を同コミットで更新 / push はユーザー明示時のみ / `--no-verify` 禁止 / 署名鍵・local.properties はコミットしない。

## z2root parity の現状（別ハンドオフの要約ポインタ）

- Alpine(musl) 起動退行(exitCode=-1) は **0.8.69(77)** で根治（bionic 誤判定の `.note.android.ident` 判定 + reloc/pre-bias ゲート）。実機起動はユーザー確認済。
- B-3 hardlink(git clone) e2e は 0.8.67 APK で合格（2026-06-10）。残: 症状(3) `tar` の再現条件特定。詳細は §10/§12（[`Z2ROOT-BUILD-PARITY-HANDOFF.md`](Z2ROOT-BUILD-PARITY-HANDOFF.md)）。
