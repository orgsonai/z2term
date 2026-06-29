# M7 ハンドオフドキュメント

最終更新: 2026-05-22
バージョン: 0.7.0-alpha (versionCode 7。M7: 機能ほぼ完成、本番署名済み release ビルド可、SSH/SFTP 実機確認済)
**状態: GUI Phase 3 + OS 同梱 + 設定画面 + フォント同梱 完了。2026-05-21 改善 + 2026-05-22(その2) の SSH/SFTP は実機確認済。2026-05-22 に画面消灯ロック + 本番署名鍵 + アプリ全体テーマ化 + 独自テーマ + SFTP UI を追加し、0.7.0-alpha を本番鍵で release ビルド済。残: BT キーボード検証、新規 UI 群 (テーマ全体化/独自テーマ/シート修正) の実機確認、本番鍵 release の実機インストール確認 (release は別パッケージ `…foss`、debug `…foss.debug` と共存)**

> ⚠️ **次の担当者へ最優先の注意**: 本番署名鍵 `z2term-release.jks` と `keystore.properties` が生成済み (リポジトリルート直下、`.gitignore` 済みで Git 未追跡)。**この 2 ファイルはバックアップ必須** — 失うと既存インストールへのアプリ更新が二度とできなくなる。詳細は下記「2026-05-22 セッション (署名鍵)」。
>
> ✅ **2026-05-22(その2) で実機確認済**: アプリ→PC への **SSH シェル接続 + SFTP** が動作 (公開鍵認証、`orgson@192.168.10.10:65152`)。鍵設定は [[ref-ssh-app-key]] / `docs/` のメモ参照。
>
> 🆕 **2026-05-22 セッション(その2)で実装 (885f4a1〜c875cd0)**: ①アプリ全体テーマ化 (Zts* 色を選択テーマ由来の動的パレット `AppColors` に) + **ユーザー独自テーマ作成** ②シート誤クローズ修正 (最上部限定スワイプ + ハンドルタップ + BackHandler) ③シェル未インストール表記 ④TopBar (貼→📋 / コマンド一覧→CMD) ⑤日本語KB両端列を狭く ⑥**SFTP UI** (設定→「SSH/SFTP プロファイル」→各行 "SFTP") ⑦SSH「接続」を稼働中タブからも可能に (`connectSsh`)。詳細は下記「2026-05-22 セッション (その2)」。
>
> 📦 **release ビルド**: `./gradlew :app:assembleFossRelease --no-configuration-cache` で本番鍵署名 (`app/build/outputs/apk/foss/release/app-foss-release.apk`、約 50.6MB、cert SHA-256 `40461461…aef25d`、v2 scheme)。release は applicationId `com.zerotoship.z2term.foss`、debug は `…foss.debug` と**別パッケージ**なので共存可 (初回はそのまま `adb install -r`)。データは別管理なので SSH プロファイル等は入れ直し。以前 debug 鍵フォールバックで署名した `…foss` が入っている場合のみ署名違い → `adb uninstall com.zerotoship.z2term.foss`。
> ✅ 2026-05-21 セッションの変更 (889a150〜f4a38cd) はユーザー実機で確認済 (新 JP/カタカナ配列、`sshd` ラッパー、選択の端到達/自動スクロール、通知アイコン、履歴永続化、SAF 共有、外部ストレージ cd、全キー連打 — すべて問題なし)。

## 2026-05-22 セッション (その2: テーマ全体化 / シート修正 / SFTP UI) — コミット済 (885f4a1〜e40a457)

ユーザー要望に沿って修正 3 + UI 微調整 2 + SFTP UI を実装。すべて `assembleFossDebug` 緑。**実機未確認**。

### 1. アプリ全体テーマ化 (885f4a1)
- 旧: テーマ変更はターミナルの文字/背景のみ。新: **アプリ UI 全域 (TopBar/タブ/各シート/キーボード) が追従**。
- `ui/theme/AppPalette.kt` (新規): `AppColors` (Compose snapshot state) を選択テーマの fg/bg/ANSI から導出 (`applyFrom`)。カード/枠線=背景を前景へ寄せ、副次テキスト=前景を背景へ寄せ、アクセント=テーマの green 系。背景輝度で `isLight` 判定。
- `ui/theme/Color.kt`: `Zts*` 色を **定数→`AppColors` を読む getter** 化 (使用箇所 ~350 を無改変で動的化)。
- `ui/theme/Theme.kt`: `Z2TermTheme` は `AppColors` から動的に colorScheme を生成 (ライト背景は `lightColorScheme` + ステータスバーアイコン濃色)。`darkTheme` 引数と固定 `ZtsDarkColorScheme` は廃止。
- 駆動: `TerminalScreen` の `LaunchedEffect(settings.themeName, customTheme)` で `AppColors.applyFrom(resolveTheme(...))`。global state なのでルートの Z2TermTheme まで再コンポーズ追従。
- **ユーザー独自テーマ作成 (d97c294)**: `settings/CustomThemeStore` (DataStore に 1 件永続化 + プロセス共有 StateFlow) + `emulator/TerminalColors` の `toJson`/`terminalThemeFromJson`/`resolveTheme(name, custom)` + `ui/settings/CustomThemeSheet` (19 色 #RRGGBB + 名前、ライブプレビュー)。設定「テーマ」一覧に独自テーマを追加表示 + 「独自テーマを作成/編集…」。`TerminalSession` は settingsFlow と CustomThemeStore.theme を combine し編集を即反映。緑=アクセント/背景/前景がアプリ全体へ。

### 2. シート誤クローズ修正 (885f4a1)
- 対象: 設定 / スニペット / SSH の 3 ModalBottomSheet。スクロール途中の下スワイプで誤って閉じる問題。
- `ui/components/Z2TermDragHandle.kt` (新規): **ハンドル行全幅タップで閉じる**共通部品。
- 各シート: `confirmValueChange` で **Hidden 遷移は最上部 (scrollState/listState が先頭) または forceClose のときだけ許可**。`BackHandler` で戻るキーは常に閉じる。`closeSheet` = forceClose + アニメ hide。
- ⚠️ 副作用: スクロール途中はスクリム/スワイプで閉じない (= 要望「TOP まで行ってから」通り)。ハンドルタップ・戻るは常に有効。

### 3. シェル未インストール表記 (885f4a1)
- `SettingsSheet` ログインシェル: 現ディストロ rootfs (`distros/<id>/<shell>`) を `File.exists()` で判定。未インストールは `(未インストール)` ラベル + 選択中なら ⚠ 注意文。rootfs 未展開時は判定しない。

### 4. TopBar ボタン整理 + 日本語KB両端列 (3e7ab8e)
- TopBar: 貼付 `貼`→`📋`、コマンド一覧 `📋`→`CMD` テキスト (`TerminalScreen.TopBar`)。
- `JapaneseFlickKeyboard`: 両端列 (機能キー) を `weight=JP_EDGE_WEIGHT(0.7)`、中央かな 3 列を `1f` で広く。全行同一配分で列は整列維持。`JpKey` に `weight` 引数追加。

### 5. SFTP UI (e40a457) ★新機能
- **起動**: 設定 → 「SSH / SFTP プロファイル…」(`onOpenSsh`) → `SshProfilesSheet` → 各プロファイル行の **"SFTP"** ボタン (`onSftp`) → `SftpSheet`。TopBar は不変。
- `channel/SshSessionFactory.kt` (新規): SSH セッション構築 (認証/known_hosts/`VerifyingUserInfo`) を共通化。`SshChannel` (シェル) と `SftpClient` が共有。**`SshChannel` から重複コードを除去**。
- `channel/SftpClient.kt` (新規): JSch `ChannelSftp` ラッパー。`connect`/`list`/`download(→OutputStream)`/`upload(InputStream→)`/`mkdir`/`rename`/`rm`/`rmdir` を suspend (Dispatchers.IO)。`resolve(base, name)` でパス結合 (`..` 対応)。
- `ui/sftp/SftpSheet.kt` (新規): モーダルのファイルブラウザ。フォルダタップで移動、`..`/↑上へ/⟳更新、各行 ⋮ で DL/名前変更/削除、下部にアップロード/新規フォルダ。DL は SAF `CreateDocument`、UL は `OpenDocument`、結果は Toast。接続/一覧は状態表示。閉じ挙動は 3 シートと同方式。
- ProGuard: `-keep class com.jcraft.jsch.** { *; }` が既にあり ChannelSftp も保持 → release 可。
- `接続`(シェル) は **`connectSsh` (c875cd0)** で稼働中タブからも可能 (restart 同様に画面クリア + state リセット後 startSsh)。現タブを SSH に置き換える挙動 (ローカルシェルは終了、残したいなら新規タブで)。
- ✅ **実機確認済 (2026-05-22)**: 公開鍵認証で `orgson@192.168.10.10:65152` へ SSH シェル + SFTP 接続成功。鍵は RSA4096/PEM (mwiede jsch は eddsa 依存無で ed25519 不安定なため RSA 必須)。設定詳細は [[ref-ssh-app-key]]。秘密鍵欄は multiline 対応済だが、コピペ時の行頭空白混入に注意 (ファイルから直接コピーする)。

## 2026-05-22 セッション (署名鍵) (画面消灯ロック + 本番署名鍵) — コミット済 (6425bb4)

### 1. 画面消灯ロック (常時点灯) トグルを追加 (`ui/terminal/TerminalScreen.kt`)
- ユーザー要望: **TopBar の 🔌SSH ボタン (外向き SSH 接続シート起動) を廃止**し、同じ位置に
  **画面消灯ロック ON/OFF トグル (💡=ON / 🔅=OFF)** を追加。
- 仕組み: Compose ルート View (`LocalView.current`) の `keepScreenOn` を `LaunchedEffect`
  でトグルするだけ。`FLAG_KEEP_SCREEN_ON` 相当。**追加権限不要**、フォアグラウンド中のみ
  有効、CPU は握らないので WakeLock より安全。
- 既定 **OFF** (放置でのバッテリ消費回避、アプリ再起動でリセット)。ON 中は緑ハイライト
  (既存の「あ」キーボード切替ボタンと同じ流儀の `KeepScreenOnButton`)。
- TopBar の並び (左→右): **貼 / 📋 / 💡 / あ / ⚙**。
- `SshProfilesSheet.kt` は**ファイルを温存**し、TopBar からの呼び出し (`onOpenSsh` /
  `sshSheetOpen` / import) のみ撤去。外向き SSH 接続シートを再び使いたければ TopBar に
  ボタンを戻すだけ。**PC→端末の sshd 起動 (設定の SSH ヘルパー) や端末 `sshd` コマンドは不変**。

### 2. 本番リリース署名鍵を生成 (リポジトリルート直下)
- `keytool` で本番鍵を作成。`app/build.gradle.kts` の signingConfig は既存のまま
  (`keystore.properties` があれば本番署名、無ければ debug 鍵フォールバック)。
- 生成物 (**どちらも `.gitignore` 済み・Git 未追跡・パーミッション 600**):
  - `z2term-release.jks` — **PKCS12 / RSA 4096bit / 有効期限 2053-10-06** (validity 10000日)
  - `keystore.properties` — `storeFile` は**絶対パス**、`storePassword`/`keyPassword` は
    **同一値** (PKCS12 はストア鍵と鍵パスワードを分離できない仕様)。
- 証明書: `CN=Z2Term, O=ZeroToShip, C=JP` / SHA-256 指紋
  `40461461c2b181144ad6521e61aa43925bb5ca1a69e5947f3374d02034aef25d`
  (Play Console / API キー制限の登録に使用)。
- `assembleFossRelease` を本番鍵で再ビルド・apksigner で署名者検証済 (release 約 52.9MB)。
- ⚠️ **罠**: `keytool` の既定ストア形式は **PKCS12** で、`-storepass` と `-keypass` を
  別値にすると鍵が読めず `Get Key failed: Given final block not properly padded` で
  packageRelease が失敗する。**両者同一**にすること (`-storetype PKCS12` 明示 + 同一パスワード)。
- ⚠️ **罠**: signingConfig は **構成 (configuration) 時**に `keystore.properties` を読むため、
  鍵を後から置いた直後の build は構成キャッシュが古く debug 鍵にフォールバックすることがある。
  一度 `--no-configuration-cache` を付けて再構成すれば以降は本番署名される。
- ⚠️ **最重要**: `z2term-release.jks` + `keystore.properties` の 2 ファイルを**リポジトリ外に
  バックアップ必須**。両方失うと同一署名での更新が不可能になり、既存ユーザーは更新できなくなる
  (別 applicationId で新規配布し直すしかない)。パスワードは会話ログには出さず properties 内のみ。

## 2026-05-21 セッション (入力 / SSH / ファイル系の改善) — コミット済 (889a150〜f4a38cd)

ユーザー実機フィードバックに沿って 4 ラウンドで対応。すべて `./gradlew assembleFossDebug` 緑。
**新規ファイル**: `ui/terminal/keyboard/JapaneseFlickKeyboard.kt`, `ui/terminal/keyboard/KeyGestures.kt`, `proot/SshdScript.kt`

### 1. ツールバー順変更 (`TerminalScreen.kt`)
- TopBar アイコンを左→右で **貼付 / 📋コマンド一覧 / 🔌SSH / あキーボード切替 / ⚙設定** に並べ替え。

### 2. 独自(ASCII)キーボードのフリック改善 (`TerminalKeyboard.kt`)
- 英字キーの **下フリック = そのローマ字の大文字** (compact/spacious 両方)。4 方向フリックの **下=数字を廃止**。下フリックの緑ヒントは非表示。
- **全キー長押し連打**: 数字・矢印・space・英字(フリックキー) を長押しで連打 (`KeyGestures.detectTapWithRepeat`, 初回 400ms→55ms)。修飾キー(Shift/Ctrl/Alt/記号/ESC/⏎)は対象外。⌫ は従来通り。
- `pointerInput` 採用に伴い callback を `rememberUpdatedState` で包み、**現在のセッション/修飾状態へ常に入力が向く**ようにした (タブ切替後の誤送信も予防)。

### 3. 内蔵 日本語フリックキーボード (`JapaneseFlickKeyboard.kt`, 新規)
- Row3 左の旧 ⌨(システム IME 切替) を廃止し、**「あ」キーで内蔵かなフリックへ切替**。標準 12 キー配列、各かなキーに 4 方向フリックヒント(灰)を表示。漢字変換は無し (辞書エンジンは範囲外)。
- 配列 (5列×4行、領域高さ充填):
  ```
  ESC  あ   か  さ   ⌫
  ◀   た   な  は   ▶
  カナ ま   や  ら   ␣      ← ま行の左 = ひら⇄カタカナ切替
  ABC  小゛゜ わ  、。  ⏎     ← 左に ABC(英字へ)、小゛゜/わ/、。、ー キーは廃止(ーは わ→右フリック)
  ```
- **カタカナモード**: 「カナ」キーで かな⇄カタカナ。出力も表示(中央+ヒント)もカタカナ。ひら↔カタは `+0x60` の `toKana/toHira`。濁点キー(小゛゜)は直前かなを 濁点→半濁点→小書き循環 (DEL+再送、循環表 `CYCLE_GROUPS` はひらがな基準)。
- TopBar の「あ」(OS IME 切替) は別機能として併存。

### 4. SSH (dropbear) を堅牢化 (`proot/SshdScript.kt` 新規, `ProotLauncher.kt`, `SshAccessHelper.kt`)
- OpenSSH `/usr/sbin/sshd` は **proot で privsep 破綻 + 新 OpenSSH は sshd_config の `UsePrivilegeSeparation` で起動不可**。→ dropbear に統一。
- **端末で `sshd` と打つだけで dropbear 起動**: ProotLauncher が起動毎に `/usr/local/sbin/sshd` へ wrapper を配置 (PATH 優先)。スクリプト本体は `dropbearBootstrapScript()` に集約 (設定ボタンも同一)。
- スクリプトは **rootfs にファイル化して `sh` で実行** (端末への複数行打鍵は zsh が `#` コメントを誤実行/`cursh>` で崩れるため)。
- **既存 dropbear を確実に停止**してから起動 (pkill → pidof → pidfile → `/proc/[0-9]*/comm` 走査の 4 段)。これが無いと「Address already in use」で失敗。
- 未導入なら pacman/apt/apk/dnf/zypper で自動 install。起動判定は pidfile のプロセス生存、失敗時はログを端末表示。

### 5. コマンド履歴の永続化 (`ProotLauncher.kt`)
- proot は終了時 SIGKILL で履歴が書かれなかった。→ 起動毎に rc を流し込む (`ensureShellHistoryConfig`)。bash: `/etc/bash.bashrc` に `histappend`+`PROMPT_COMMAND='history -a'`、zsh: `/etc/zsh/zshrc` に `INC_APPEND_HISTORY` 等。env にも `HISTSIZE/PROMPT_COMMAND` 等。1 コマンド毎に追記 → 再起動後も ↑ で履歴。既存 distro にも後付けで効く。

### 6. SAF とアプリのフォルダ共有 + 外部ストレージ cd (`Z2TermDocumentsProvider.kt`, `ProotLauncher.kt`, Manifest, `SshAccessHelper.kt`)
- **重要**: 端末の `/root` は `distros/<distro>/root` でなく **`filesDir/shared_home`** (proot が `-b shared_home:/root`)。SAF が前者を「ホーム」公開していたのでズレていた → **SAF ホーム = shared_home** に統一。各 distro は rootfs 全体(`/`)も公開。許可ルート `[shared_home, distros]`。
- proot に **`/storage/emulated/0 → /sdcard`** と **`getExternalFilesDir → /storage/app`** をバインド (`cd /sdcard` 可)。`/sdcard` は MANAGE_EXTERNAL_STORAGE 必要 → Manifest 追加 + 設定の `StorageAccessHelper` から許可画面へ。`/storage/app` は権限不要。

### 7. テキスト選択 UX 改善 (`TerminalInputView.kt`, `TerminalRenderer.kt`)
- **罠**: `GestureDetector` は `onLongPress` 後 `onScroll` を送らない (`mInLongPress`)。→ `touchMode != NONE` のとき detectors を介さず **生 MOTION_MOVE で選択追従**。
- ハンドル当たり判定を拡大 (行高×2.2/最低96px、近い端を選択。左端 col0 でも掴める)。**末端付近ドラッグで範囲変更**可。
- **拡大鏡**: 選択中、端末描画 View(AndroidComposeView) を `android.widget.Magnifier` で指の上に表示。
- **端で自動スクロール**: 検知ゾーン 行高×2.5/最低80px。上端→過去(`scrollBy(+1)`)、下端→最新(`scrollBy(-1)`) を 45ms 毎、選択を画面外まで伸ばせる。

### 8. 通知(常駐)アイコン修正 (`res/drawable/ic_notification.xml`)
- 「2」のベクターパスが歪んで「3」に見えていた → ブロック体の正しい「2」(上バー→右上縦→中バー→左下縦→下バー)に。

### このセッションでユーザー実機が確認済 / 未確認
- 確認済(ユーザー報告): 旧 JP は「壊滅的」→再設計、dropbear は「port in use/zsh崩れ」→修正、長押し選択は「いい感じ」だが端まで届かない→修正、アイコン z3→修正。
- **✅ 確認済 (2026-05-22 ユーザー報告: 全項目問題なし)**: 新 JP/カタカナ配列、`sshd` ラッパー実行、選択の端到達/拡大鏡/自動スクロール、通知アイコン反映、履歴永続化、SAF 共有(ファイラー実ブラウズ)、`cd /sdcard`(権限付与後)、全キー連打。

## 実機検証ログ (2026-05-20, moto g66j 5G / Android 15 / arm64-v8a)

`adb install` した foss debug で以下を確認済 (✅):
- 旧 rootfs v3 → v5 自動再展開 (7263 entries)、クラッシュ/例外なし
- **sudo world-writable 修正**: `/etc/sudo.conf` が `-rw-------`、`sudo whoami` → `root`
  (以前の "world writable" エラー解消)
- **mosh**: `mosh-client` が PRoot 下で起動、UTF-8 locale チェックも通過
  (usage 表示、"needs a UTF-8 native locale" 出ず) ※実 UDP 接続は未検証
- 追加バイナリ存在: mosh/mosh-client/mosh-server, git, curl, rsync, perl, htop, jq, tmux
- **SAF DocumentsProvider** 登録確認 (authority `<pkg>.documents`、DOCUMENTS_PROVIDER)
  ※ ファイラーからの実ブラウズは未検証 (要 Files アプリ操作)
- 起動バナー ANSI 色付け、TopBar「貼」ボタン、ステータスバー Z2 アイコン
- 設定: ステータスバー inset + 縦スクロール、distro チップ (DL サイズ注記)、
  常駐トグル、アプリ情報 (version/flavor/package/rootfs世代/distro=os-release)

**実機でまだ未検証 (要追加作業):**
- 本番鍵署名 + 実機インストール (鍵が秘密情報)
- BT 物理キーボード (#8 sticky)、ジェスチャ閾値 (#9)
- mosh 実 UDP 接続、Ubuntu/Arch/Kali の実 DL→起動、SAF ファイラー実ブラウズ
- xterm-mouse / OSC 11 query の実挙動

## ⭐ ここまでで動く範囲

- 起動 → Alpine が初回 (or 更新) 自動展開 → zsh プロンプト
- 独自 5 行キーボード (3 状態 Shift / フリック / 長押し連打) or OS IME に切替
- タブ複数化、長押し選択 + ハンドル + コピー、ピンチでフォントズーム
- 設定パネル: テーマ/フォント (実プレビュー)/スクロールバック行数/ディストロ/EAW/init コマンド/シェル/キーボードスタイル + SSH 接続ヘルパー
- SSH プロファイル + ポート転送 (-L) 編集 UI、ホスト鍵検証ダイアログ
- PC から Z2Term へ SSH 可能 (sshd 2222 ワンタップ起動)
- APK サイズ 約 82MB (PRoot + Alpine 12MB + プログラミングフォント 700KB)

## このドキュメントの目的

このドキュメントは次セッション担当者への引き継ぎ。
M7 のスコープがほぼ達成された段階のスナップショット。

## 2026-05-20 までに完了したこと (M7 全期間)

### コア機能 (commit a968287 / edc7286 / 8b63318)
- SSH `-L` ローカルポート転送 (JSch `setPortForwardingL`)
- emulator スレッド分離 (`emulator-dispatcher`)
- redraw コアレッシング (~60fps 上限)
- scrollback delta tracking (手動スクロール中の視点固定)
- android-sh fallback の mkshrc 注入 (旧仕様、現在は PRoot 必須なので fallback は警告のみ)

### GUI 全面書き直し (commit 8b63318)
- ui/ 配下を全削除して再構築
- 独自キーボード + OS IME トグル
- cursor-anchor 描画 (fresh shell カーソル不可視を回避)
- TerminalBuffer.resize cursor-aware (プロンプト消失を回避)

### Phase 3 (タブ等) — 未コミット作業フォルダ
- 5 列キー (3 状態 Shift / 上 (compact) or 4 方向 (spacious) フリック / 長押し連打)
- `KeyboardStyle.COMPACT` / `KeyboardStyle.SPACIOUS` (`naturalHeight` ベースで領域自動拡張)
- ⌫ の左右フリックで Ctrl+W / Ctrl+U (隠し機能、ヒント非表示)
- BS タップ → 単発 / 長押し → 500ms 後 60ms 間隔で連打
- セル単位 drawText で **カーソル位置ドリフトを解消** (フォント advance ≠ cellW のサブピクセル誤差累積)
- タブバー (TopBar 下)、`SessionManager` 観測式
- キーボード ↔ ターミナル間のスプリッタ (タップ折畳 / ドラッグ高さ調整)
- ピンチ (ScaleGestureDetector) でフォントサイズ即時変更
- 1 本指ドラッグでスクロール、最下端 ↓ ボタン
- 長押し選択 → 緑ハンドル → 「コピー」フローティングボタン
- 設定シート (ModalBottomSheet): テーマ/フォント/フォントサイズ/scrollback/ディストロ/EAW/init コマンド/シェル/キーボードスタイル
- テーマ chip に 6 色プレビュー、フォント chip に実フォントで `Aa Bb 0Oo 1Il` サンプル
- SSH 接続ヘルパー: 端末 IPv4 自動表示 + sshd 起動スクリプト送信 + ssh コマンドコピー
- SSH プロファイル UI (`SshProfilesSheet`): リスト / 編集 / 接続 / 削除、認証はパスワード or 公開鍵 (PasswordVisualTransformation)
- ポート転送 (-L) リスト編集 (1 プロファイルにつき複数可)
- ホスト鍵検証ダイアログ (`HostKeyVerifier.flow` 観察、blocking resolve)

### OS 同梱 (M7 最重要)
- PRoot バイナリ (Termux 公式 deb `proot_5.1.107-71_aarch64.deb`)
- `libtalloc.so.2` 同梱 (Termux 公式 deb `libtalloc_2.4.3_aarch64.deb`)
  - jniLibs は `libtalloc.so` で配置 → 起動時に `filesDir/proot-libs/libtalloc.so.2` へ SONAME 通りコピー
  - `LD_LIBRARY_PATH=$filesDir/proot-libs` を proot 起動時 env に注入
- Alpine 3.21 rootfs を apk-tools-static + fakeroot で構築 (~12MB)
- 同梱パッケージ 34 個 (Tier 0+1+2: alpine-base / busybox / bash / zsh / openssh / screen / coreutils / findutils / grep / sed / gawk / less / shadow / procps-ng / sudo / which …)
- assets には `.tgz` 拡張子で配置 (aapt の `.tar.gz` 自動展開を回避)
- `useLegacyPackaging = true` で PRoot を実ファイル展開 (nativeLibraryDir に execve 可能なファイルを置く)
- ABI は `arm64-v8a` のみ (32bit 対応外)

### 自動化スクリプト
- `scripts/build-bundle.sh` — マスタースクリプト (PRoot + Alpine rootfs + フォント)
- `scripts/build-proot.sh` — Termux deb から PRoot + libtalloc 取得
- `scripts/build-alpine-rootfs.sh` — apk-tools-static + fakeroot + パッケージ追加 + post-install (resolv.conf, profile.d, .bashrc, .zshrc, /etc/shells, /etc/passwd の root シェル → zsh)
- `scripts/fetch-fonts.sh` — IBM Plex Mono / JetBrains Mono / Fira Code (各 Regular) を assets/fonts/ に取得
- `scripts/alpine-packages.txt` — Tier 0+1+2 パッケージリスト

### 自動更新
- `DistroBundle.ROOTFS_VERSION` で世代管理 (現在 3)
- 起動時に `<rootfs>/.z2term-version` を比較、古ければ自動再展開
- バナー文言: 初回 vs 更新で表示分岐

### 修正済の罠 (メモリ参照)
- `[[gotcha-aapt-targz-rename]]` — assets の `.tar.gz` は `.tgz` で同梱
- `[[gotcha-uselegacy-packaging]]` — execve したい .so は legacy packaging 必須 + Termux proot は libtalloc も同梱必須
- `[[gotcha-text-drift-sgr-run]]` — SGR run drawText でカーソルズレ、セル単位描画必須
- `[[gotcha-kdoc-nested-comment]]` — KDoc 内に `*/` を含むと早閉じ (今回 `assets/*.tgz` を実演で踏んだ)
- `bin/sh は /bin/busybox への絶対 symlink` — isDistroReady は `bin/busybox` を見る (broken link 経由で常に false 化を回避)

## アーキテクチャ概略 (現在)

```
MainActivity
└── TerminalScreen
    ├── TopBar (label / mode / cwd / 🔌 SSH / ⚙ Settings / Aあ IME 切替 / state)
    ├── TabBar (横スクロール、各タブに × 、末尾に +)
    ├── Box(weight=1f)
    │   ├── TerminalRenderer  ← cursor-anchor + セル単位 drawText + 選択ハイライト + ハンドル
    │   ├── TerminalInputView (AndroidView)  ← ScaleGestureDetector + GestureDetector
    │   └── ScrollIndicators (↓ジャンプ + コピーフローティング)
    ├── SplitterBar (タップ折畳 / ドラッグ高さ調整)
    ├── Keyboard area (CUSTOM=独自 / SYSTEM=SpecialKeyBar wrap-content)
    ├── SettingsSheet (Modal)
    │   └── SshAccessHelper
    ├── SshProfilesSheet (Modal) ← リスト + 編集 (PortForwardSection)
    └── HostKeyVerificationDialog (Dialog、常駐)

TerminalSession
├── emulator (TerminalEmulator)  ← 専用シリアル executor
│   + cursor-aware resize / scrollback delta
├── ProcessChannel ← LocalPtyChannel | SshChannel(+ -L 転送 / UserInfo)
├── redrawTick (16ms coalesced)
├── scrollOffset / cellMetrics / selection / cwd / label (StateFlow)
└── settingsFlow

ProotLauncher
├── isDistroReady → bin/busybox + .z2term-version 比較
├── ensureProotLibs → filesDir/proot-libs/libtalloc.so.2 を SONAME 通り配置
└── launch → LD_LIBRARY_PATH 設定して proot exec
```

## 残作業 (推奨順)

### Tier A — 配布前にやるべき

1. **実機 BT キーボード検証** — 物理キーボード接続テスト (ショートカット、Esc/Tab/Arrow)
2. ~~リリースビルド + 署名~~ ✅ 完了 (2026-05-22): 本番署名鍵 `z2term-release.jks` +
   `keystore.properties` をルート直下に生成 (PKCS12/RSA4096/2053まで、`.gitignore`済)。
   **0.7.0-alpha (versionCode 7) を `assembleFossRelease --no-configuration-cache` で本番鍵署名済**
   (約 50.6MB、cert SHA-256 `40461461…aef25d`、v2 scheme 検証OK)。`keystore.properties` 未設定なら
   debug 鍵フォールバック。手順は `docs/RELEASE.md`、テンプレ `keystore.properties.example`。
   ※ **鍵 2 ファイルはバックアップ必須** (詳細は「2026-05-22 セッション」)。
   ※ **本番鍵 release の実機インストール確認はまだ** (release `…foss` は debug `…foss.debug` と
     別パッケージで共存可。初回はそのまま install -r。SSH プロファイル等は入れ直し)。
3. ~~R8 / ProGuard~~ ✅ 完了: `app/proguard-rules.pro` に JNI/JSch/Compose/Coroutines/
   DataStore/xz/SAF/data class の keep 整備済み。release ビルド緑。
4. ~~AndroidManifest~~ ✅ 完了 (2026-05-20): `largeHeap=true` 追加 (大型 rootfs 操作の余裕)、
   `networkSecurityConfig` 追加 (cleartext 全面禁止 + system CA のみ)。`extractNativeLibs` は
   foss で true を確認済 (useLegacyPackaging=true 由来、proot execve に必須)。
   ※ 本番鍵での実機インストール確認は要 (今は debug 署名)
   ※ full フレーバーの fullDebug は extractNativeLibs=false の旧 intermediate 痕跡あり。
     foss しか使わないなら無視可だが、full を使うなら要再確認

### Tier B — UX 仕上げ

5. ~~アプリ情報セクション~~ ✅ 完了 (2026-05-20): 設定末尾に version/build/flavor/
   applicationId/ROOTFS_VERSION/distro (os-release PRETTY_NAME) を表示
6. ~~より多くのテーマ~~ ✅ 完了: Catppuccin Mocha / Catppuccin Latte (ライト) / Monokai を追加
   (計 9 テーマ、TerminalColors.kt の AvailableThemes)
7. ~~起動バナーの整理~~ ✅ 完了: writeBanner に ANSI 色付け (✓緑/✗赤/⚠黄/進行シアン)。
   CR+LF は元から対応済
8. **物理キーボード Ctrl/Alt の sticky 解除タイミング** — 実機検証必須
9. **ジェスチャ閾値の調整** — 端末の DPI 差で誤動作する場合 (実機検証必須)

### Tier C — 追加機能

10. ~~SFTP UI~~ ✅ 完了 (2026-05-22, e40a457): `channel/SftpClient` (ChannelSftp ラッパー) +
    `ui/sftp/SftpSheet` (ファイラー、DL/UL は SAF 連携)。設定→「SSH/SFTP プロファイル」→
    各行 "SFTP"。**実機での実 SFTP 接続検証は要**。
11. ~~mosh~~ ✅ 完了 (2026-05-20): Alpine に mosh パッケージ同梱 + LC_ALL=C.UTF-8。
    PRoot は非特権 UDP を通すため mosh-client が動く (raw socket の ip/ping とは別)。
    `mosh user@host` で利用。実機 UDP 接続確認は要。perl 依存で APK 117MB に増大。
12. ~~OSC 11 query / xterm-mouse~~ ✅ 完了 (2026-05-20)
13. ~~追加パッケージ Tier 3+~~ ✅ 完了 (curl/wget/git/nano/vim/tmux/htop/jq/rsync/tree 等)
14. ~~ディストロ追加~~ ✅ 完了 (2026-05-20): Ubuntu 24.04 / Arch Linux ARM / Kali を
    **ランタイムDL方式** で追加 (同梱しない)。DistroSpec.bundled=false + downloadUrlArm64。
    Kali は .tar.xz 配布 → org.tukaani:xz 依存追加し DistroInstaller がマジックバイトで
    gzip/xz 自動判定。設定でディストロ選択 → switchDistro で DL→展開→再起動。
    ⚠️ DL URL は upstream 都合で変わるため要メンテ (DistroSpec.kt 内)。
15. **Bluetooth/USB シリアル** — ターミナルとして USB-シリアル変換チップを使う

## ファイル構成 (重要なところだけ)

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── README.md
│   ├── alpine-minirootfs-aarch64.tgz   (12MB、build-bundle.sh で生成)
│   └── fonts/
│       ├── FiraCode-Regular.ttf
│       ├── IBMPlexMono-Regular.ttf
│       └── JetBrainsMono-Regular.ttf
├── jniLibs/arm64-v8a/
│   ├── libproot.so
│   ├── libproot_loader.so
│   └── libtalloc.so
└── java/com/zerotoship/z2term/
    ├── MainActivity.kt
    ├── Z2TermApplication.kt
    ├── channel/
    │   ├── KeystoreCrypt.kt
    │   ├── KnownHosts.kt        ← HostKeyVerifier object も同居
    │   ├── KnownHostsHolder.kt
    │   ├── LocalPtyChannel.kt
    │   ├── ProcessChannel.kt
    │   ├── SshChannel.kt
    │   └── SshProfile.kt        ← SshProfileStore も同居
    ├── core/
    │   ├── SessionManager.kt
    │   ├── TerminalSelection.kt ← CellMetrics + TerminalSelection
    │   └── TerminalSession.kt   ← reinstallDistro 追加
    ├── distro/
    │   ├── DistroBundle.kt      ← ROOTFS_VERSION + VERSION_MARKER
    │   ├── DistroDownloader.kt
    │   └── DistroInstaller.kt   ← postInstallSetup で version マーカー書き込み
    ├── emulator/                … (変更なし、cursor-aware resize は M7 で済)
    ├── proot/ProotLauncher.kt   ← ensureProotLibs + isDistroReady 修正
    ├── pty/PtyProcess.kt
    ├── service/TerminalService.kt
    ├── settings/AppSettings.kt  ← loginShell / keyboardStyleId 追加
    └── ui/
        ├── settings/
        │   ├── SettingsSheet.kt          ← Theme/Font プレビュー chip 込
        │   └── SshAccessHelper.kt        ← 端末 IP 検出 + sshd 起動ボタン
        ├── ssh/
        │   ├── HostKeyVerificationDialog.kt
        │   └── SshProfilesSheet.kt       ← PortForwardSection 込
        ├── terminal/
        │   ├── TerminalRenderer.kt
        │   ├── TerminalScreen.kt         ← TabBar / SplitterBar / ScrollIndicators
        │   ├── components/SpecialKeyBar.kt
        │   ├── input/
        │   │   ├── AndroidKeyMapper.kt
        │   │   └── TerminalInputView.kt  ← Gesture + Pinch
        │   └── keyboard/
        │       ├── KeyboardStyle.kt      ← COMPACT/SPACIOUS + FlickMap + ShiftState
        │       └── TerminalKeyboard.kt
        └── theme/                        … (変更なし、Color/TerminalFonts/Theme/Type)
```

## ビルド手順 (引き継ぎ用最短)

```bash
# 1. 同梱物 (PRoot + Alpine + フォント) を一括生成
bash scripts/build-bundle.sh

# 2. APK ビルド
./gradlew :app:assembleFossDebug

# 3. インストール
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

PRoot / Alpine 構成を変えたいとき:
- `scripts/alpine-packages.txt` を編集 (追加/削除)
- `app/src/main/java/com/zerotoship/z2term/distro/DistroBundle.kt` の `ROOTFS_VERSION` を +1
- `FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64` で再生成
- `./gradlew :app:assembleFossDebug`

ユーザーは APK 入れ替えだけで自動更新展開される。

## 罠リスト (新規追加分は **★**)

- ❌ `Compose BasicTextField` で realtime PTY 入力 (IME 同期破綻)
- ❌ `factory = { ...; v.requestFocus() }` で OS IME を意図せず自動表示
- ❌ `buffer.resize` で上端を無条件に scrollback push (プロンプト消失)
- ❌ renderer で `bottomAbsRow = buf.rows - 1` 固定 (fresh shell カーソル不可視)
- ❌ `csi("3~")` のような ESC 抜けバイト列 (Read 上は ESC が見えない)
- ❌ Mozc に `FORCE_ASCII` で全角化を防ごうとする (尊重されない)
- ❌ assets に `.tar.gz` 拡張子で置く → aapt が `.tar` に解凍リネーム ★
- ❌ `useLegacyPackaging = false` で実行バイナリ同梱 → nativeLibraryDir に実体無し ★
- ❌ Termux PRoot を libtalloc 抜きで同梱 → "CANNOT LINK EXECUTABLE" ★
- ❌ SGR run まとめて `drawText` → サブピクセル誤差累積でカーソルズレ ★
- ❌ `bin/sh` (絶対 symlink) で isDistroReady 判定 → 常に false で毎回再展開 ★
- ❌ KDoc 内に `*/` を含む文字列 → コメント早閉じ (assets/*.tgz の `*/` で実演踏み) ★
- ❌ proot launch で固定 `/bin/sh` → busybox ash が走り zsh の機能使えない ★
- ❌ 端末の `/root` を `distros/<distro>/root` と思い込む → 実体は `filesDir/shared_home` ★(0521)
- ❌ 複数行スクリプトを端末に直接打鍵 → zsh が `#` コメントを実行/`cursh>` で崩壊。ファイル化して `sh` 実行する ★(0521)
- ❌ dropbear を kill せず再起動 → "Address already in use"。pkill/pidof/pidfile/`/proc` 走査で確実に停止 ★(0521)
- ❌ OpenSSH `/usr/sbin/sshd` を proot で使う → privsep 破綻 + `UsePrivilegeSeparation` で起動不可。dropbear 一択 ★(0521)
- ❌ `GestureDetector` の `onScroll` で長押し選択ドラッグを追従 → onLongPress 後は送られない。生 MOTION_MOVE で処理 ★(0521)
- ❌ proot を SIGKILL 前提で履歴を期待 → bash `PROMPT_COMMAND='history -a'` 等で 1 コマンド毎に追記 ★(0521)
- ❌ keytool 既定 PKCS12 で store/key パスワードを別値にする → 鍵が読めず `Get Key failed: ... not properly padded` で packageRelease 失敗。同一にする ★(0522)
- ❌ 鍵を後から置いても構成キャッシュが古いと release が debug 署名にフォールバック → 一度 `--no-configuration-cache` で再構成 ★(0522)

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha | 2026-05-16 | M2 実用ターミナル化完了 |
| 0.3.0-alpha | 2026-05-17 | M3 常駐ターミナル化完了 |
| 0.4.0-alpha | 2026-05-17 | M4 マルチセッション + 国際化対応完了 |
| 0.5.0-alpha | 2026-05-17 | M5 SSH + ジェスチャ + 配布準備完了 |
| 0.6.0-alpha | 2026-05-17 | M6 SSH 強化 + FOSS フレーバー + リンク対応完了 |
| (進行中) | 2026-05-19 | M7 GUI 全削除 → 独自キーボード + IME トグル + 描画修正、SSH -L、emulator スレッド分離 |
| (進行中) | 2026-05-19 | GUI Phase 3 (タブ/スプリッタ/選択/ピンチ/フリック) 完了 |
| (進行中) | 2026-05-19 | OS 同梱 (PRoot + Alpine 32 pkg) 完了、APK 102MB |
| (進行中) | 2026-05-19 | 設定シート + SSH プロファイル UI + ホスト鍵検証ダイアログ |
| (進行中) | 2026-05-20 | useLegacyPackaging=true、libtalloc 同梱、起動ループ修正、zsh 既定化 |
| (進行中) | 2026-05-20 | キーボードリファクタ (3 状態 Shift / 4 方向フリック / スタイル切替) |
| (進行中) | 2026-05-20 | rootfs 自動更新マーカー、sudo/which 追加、ROOTFS_VERSION=3 |
| (進行中) | 2026-05-20 | SSH 接続ヘルパー、ポート転送 UI、テーマ/フォントプレビュー、3 フォント同梱 |
| 889a150 | 2026-05-21 | キーボード刷新 (ツールバー順/下フリック大文字/全キー連打/日本語・カタカナ) |
| 6c04662 | 2026-05-21 | テキスト選択UX (掴み拡大/拡大鏡/端で自動スクロール) |
| 4865703 | 2026-05-21 | SSH を dropbear 統一 + `sshd` コマンド + コマンド履歴永続化 |
| 1174433 | 2026-05-21 | SAF=shared_home 共有 + 外部ストレージ `/sdcard` cd |
| f4a38cd | 2026-05-21 | 通知(常駐)アイコンを Z2 に修正 |
| 6425bb4 | 2026-05-22 | TopBar 🔌SSH→画面消灯ロック(💡)置換 + 本番署名鍵生成 (実機項目すべて確認済) |
