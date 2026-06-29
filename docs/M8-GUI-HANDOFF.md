# M8 ハンドオフ — Linux GUI 起動（内蔵 VNC クライアント方式）

最終更新: 2026-05-26
バージョン: M8-1 + M8-2 + **M8-3 (入力) 実装・実機検証済** + **M8-5 (UI統一/distro対応) 実装・実機検証済 (2026-05-25, Alpine=debug / Arch=release 両方)**。ベースは 0.7.0-alpha (versionCode 7)。
**状態: 表示方式は「内蔵 VNC(RFB) クライアント + Compose Canvas 描画」。M8-1〜M8-4 すべて ✅ 実機 (arm64, Android 15) で動作確認済み。M8-5 で GUI のツールバー維持・キーボード端末統一・選択 OS 起動・ターミナル選択を追加 (✅ 主要項目を実機検証済 2026-05-25)。M8-6 (GUI 操作性改善) は T1〜T9 すべて実装。M8-7・M8-8 (下記) で GUI 操作性・同梱コマンド・スニペット・zsh 起動を改善 — ✅ 実機検証済 (release, 2026-05-26)。**

---

## 📋 M8-8 同梱コマンド・スニペット・zsh・GUI 位置 — ✅ 実機検証済 (2026-05-26)

### 主な変更
1. **同梱 Alpine にコマンド追加 (汎用性)**: `scripts/alpine-packages.txt` に Tier 3.6 として **zip / unzip / openssl / bind-tools(dig・nslookup・host) / file / diffutils(diff・cmp) / patch / bc** を追加。`scripts/build-alpine-rootfs.sh` で rootfs 再生成 (tgz 44.6MB→49MB)。`ROOTFS_VERSION` を 6→7 に bump して既存 Alpine も再展開。※ tgz は `.gitignore` 済みのビルド生成物（正本は alpine-packages.txt、各自が build スクリプトで生成）。
2. **CMD スニペット改修** (`ui/snippets/SnippetsSheet.kt`, `snippets/Snippet.kt`):
   - 「おすすめ投入 小/中/大」プリセットを**撤去**。
   - 初回のみサンプル `ls -la --color=auto` を**シード** (`SnippetStore.ensureSeeded`, `SEEDED` フラグで一度きり・削除しても復活せず・既存があれば上書きしない)。
   - 並べ替えを **↑↓ ボタン → ≡ ハンドルのドラッグ**に変更。`SnippetStore.replaceAll` で確定保存。Column + `key(s.id)` でノード identity を固定し、掴んだ行にポインタが追従。固定行高 `SNIPPET_ROW_HEIGHT=52dp` でピッチ計算。`reorder` は撤去。
3. **🔴 zsh 起動不能バグ修正** (`distro/DistroInstaller.kt`): 同梱 Alpine の `/bin/zsh` は tgz 内で**ハードリンク** (`./bin/zsh`→`./bin/zsh-5.9`)。tar typeflag '1' の処理が **copyTo 後に mode を設定しておらず** `-rw-------` (実行ビット無し) になり "Permission denied" で起動不能だった。→ コピー後に `setUnixMode(outFile, mode)` を追加。`ROOTFS_VERSION` 7→8 で再展開。**ホスト tar は正しく再現するので tgz を展開しても気付けない**点に注意 (実機の展開後パーミッションで確認すること)。任意 distro の hardlink 実行ファイル全般に効く修正。
   - ※ ログインシェル設定 (`AppSettings.loginShell`) が `/bin/bash` だと当然 bash。zsh を使うには設定で `/bin/zsh` を選ぶ (全 distro 共通・未導入 distro は自動で bash フォールバック)。
4. **GUI ターミナルを全て 0,0 に** (`proot/GuiScript.kt`): 端末ごとに -geometry の書式が違う (xterm/urxvt 対応, konsole/lxterminal 別系統) ため、**openbox に強制配置ルール**を持たせた。`/tmp/z2-openbox-rc.xml` に `<application class="*"><position force="yes"><x>0</x><y>0</y>` を書き `openbox --config-file` で起動。実機で **konsole が左上 0,0** に出ることを確認 (以前は中央)。

### 次の人向けメモ
- **同梱 rootfs を変えたら `ROOTFS_VERSION` を必ず bump** (既存端末の再展開トリガ)。tgz 再生成は `FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64` (host に curl/tar/gzip/fakeroot 必要)。
- hardlink 由来の実行ビット落ちは**実機の展開後**でしか分からない: alpine 端末で `ls -la /bin/zsh` が `-rwx` か、`/bin/zsh -c echo` が通るかを見る ([[z2term-adb-drive-via-storage-app]] の手法)。
- 「コマンドが入ってるのに起動しない (Permission denied)」を見たら **hardlink + mode 欠落**をまず疑う。

---

## 📋 M8-7 GUI 操作性の追加改善 — ✅ 実機検証済 (2026-05-25)

M8-6 後の追加ユーザー要望をまとめて実装。**release (archlinux) 実機で検証済**（konsole クリーン導入成功 / xterm 0,0 小窓 / 独自 python(Rubicon) GUI 起動 / Ctrl+L クリア）。

### 主な変更
1. **クリーンインストールを「切替セクションのチェック」に統一**。設定下部の「ディストロ再展開／クリーン再インストール」ボタンは廃止。
   - ディストロ section：チェック ON で OS を選ぶ → その OS を rootfs+DL キャッシュごと入れ直し (`TerminalSession.cleanInstallDistro(id)`)。同梱 Alpine は通信なしで APK 同梱物から再展開、非同梱は再 DL（確認ダイアログあり）。
   - GUI のターミナル section：チェック ON で GUI タブを開く → GUI パッケージをキャッシュごと入れ直し。**起動時に必ずチェックを外す**（`setCleanInstallGuiArmed(false)` を消化時に呼ぶ。フラグは DataStore 永続）。
2. **GUI クリーンチェックで開くと IDLE のまま固まるバグを修正**。`LaunchedEffect(gui.id, guiAreaPx)` がサイズ確定の数フレーム中に suspend (特にクリーン時の DataStore 書込) を挟んで毎回キャンセルされ、起動もダイアログも走らなかった。→ **key を `gui.id` だけにし、サイズは `snapshotFlow { guiAreaPx }.first { ... }` で待つ**方式に変更（`startTriggered` 廃止）。`TerminalScreen.kt`。
3. **GUI パッケージ導入失敗の救済 = stale ロック除去**。pacman の `/var/lib/pacman/db.lck`（前回の途中失敗で残る）が居座ると以降 `pacman` が `unable to lock database` で**永久に失敗**。`z2gui` の `clear_pm_locks()` を install/clean 両方で実行し pacman の `db.lck`・apt の各 lock を削除（apt clean は `dpkg --configure -a` も）。**これで konsole が一度失敗しても入れ直せる**（実機で 67 パッケージ DL→導入→起動を確認）。`proot/GuiScript.kt`。
4. **GUI のダウンロード進捗を可視化**。`GuiSession.drainPty` が接続前の z2gui 最新出力行を `_message` に流す → GuiScreen に apk/pacman の取得・導入ログが出る（パッケージマネージャは％を出さないため進捗バーでなく行表示）。
5. **GUI レイアウト刷新**：
   - **全幅**：GUI 領域（枠内）の実寸 px を `onSizeChanged` で測り、`実寸/倍率` を Xvnc 解像度にする → 中央フィットで左右黒帯なし。
   - **緑の枠線**で GUI 範囲を明示（`Box.border(2.dp, ZtsGreen)`）。
   - **キーボードは折りたたみ可オーバーレイ**（端末タブと同一。GUI に上乗せで解像度不変、▾ で畳める）。一度「廃止」したが「キーボードが無い」と要望が来たので**復活**。
   - **xterm は左上 0,0 に控えめサイズ**（画面の約 60% 幅 × 45% 高さ）で起動。大きくは WM の最大化で。`z2gui` の `start_x`。
6. **GUI 表示倍率設定**（`guiMagnification` 0.5×〜3.0×, 既定 1.5×）。<1.0 は仮想画面を高解像度化して縮小表示＝細かく・広く。1.0=等倍、>1.0=大きく。`AppSettings` + 設定スライダー。
7. **設定の「ペースト」「画面クリア」ボタン削除**（画面クリアは **Ctrl+L** で可。端末で実機確認済）。「端末リセット」のみ残す。

### 主ファイル
`ui/terminal/TerminalScreen.kt`（GuiTabScreen の起動ロジック=snapshotFlow / 枠線 / キーボード復活 / GuiTopBar）、`proot/GuiScript.kt`（z2gui: clean / clear_pm_locks / xterm geometry）、`gui/GuiSession.kt`（start(clean) / 進捗 message）、`core/TerminalSession.kt`（cleanInstallDistro / setGuiMagnification / setCleanInstallGuiArmed）、`settings/AppSettings.kt`（guiMagnification / cleanInstallGuiArmed）、`ui/settings/SettingsSheet.kt`（クリーンチェック×2 / 倍率スライダー / ボタン整理）。

### 次の人向けメモ（落とし穴）
- **実機で GUI を再検証するときは `z2gui stop` で前回 Xvnc を必ず止める**（生きていると `x_alive` ガードで旧セッションに再接続され新設定が反映されない）。あるいは GUI タブを ×（ダブルタップ）で閉じる＝`runGuiStop` で kill される。
- **入力が独自 View に届かない**ため adb 検証は `/storage/app`（proot 内バインド＝アプリ外部 files、権限不要・adb 読み書き可）にスクリプトを置き、独自キーボードを `input tap` で `sh /storage/app/g` と打って実行、出力をファイルに吐いて `adb shell cat` で読む（履歴 ↑ キーで再実行が楽）。端末タブで `export DISPLAY=:1` すれば GUI の Xvnc に X クライアントを出せる。
- **konsole は KDE 一式（67 パッケージ）を引く**＝重い。軽さ優先なら xterm/urxvt/lxterminal。

---

## 📋 M8-6 GUI/アプリ全体の操作性改善 — ユーザー要望 (2026-05-25)

ユーザーからの改善要望 9 件 (T1〜T6=GUI 中心、T7〜T9=アプリ全体)。

**進捗 (2026-05-25):**
- ✅ **実装済 (コンパイル確認のみ・実機未検証)**: T1 (GUI ペースト/スニペット) / T2 (表示クリップ) / T3・T4・T5 (ジェスチャ再設計＝二度押し右クリック) / T6 (GUI→Android クリップボード) / T8 (タブ ダブルタップで閉じる) / T9 (画面消灯ロック強化)。ジェスチャ系 (T3/T4/T5) は `GuiInputView` で一括再設計。T1+T6 で双方向クリップボード。
  - **ユーザー決定 (2026-05-25)**: T4 の「単タップ長押し右クリックを廃止してよいか」→ **廃止し「二度押し」方式 (ダブルタップ＋2 回目保持＝右クリック) に統一**。
  - ⚠ **ピンチ/ダブルタップ/タブ ダブルタップ/クリップボード/画面消灯はマルチタッチや実機環境依存で adb 自動検証できない**。次の人は実機で手操作確認を (`z2gui stop` してから GUI タブを開き直すこと)。
- ✅ **T7 も実装** (外部 DL 前の確認ダイアログ + 設定「ダウンロード前に確認」ON/OFF)。これで M8-6 (T1〜T9) は**全項目コンパイル完了**。残るは**実機検証**のみ。

各タスクの現状・原因・方針・主ファイルは以下。

### 参考: アプリの初期状態と、フル機能に必要なダウンロード (ユーザー質問への回答)
- **初回 (オフラインで使えるもの)**: **Alpine Linux のフル CLI 環境**。`assets/alpine-minirootfs-aarch64.tgz` (44MB) を APK 同梱 → `DistroInstaller` が初回起動で展開 (ネット不要)。Alpine は tier0〜4 を事前導入済 (zsh/sudo/curl/wget/git/vim/tmux/htop/jq/rsync/mosh/locales + **dropbear**=PC→端末 SSH)。さらに 独自キーボード・かな漢字変換 (z2dict.txt 同梱 5MB)・テーマ/フォント・SSH/SFTP クライアント・スニペット も全てオフラインで動く。
- **フル機能にダウンロードが要るもの**: ① **GUI (Linux デスクトップアプリ)** = 初回 GUI タブで GUI パッケージ (tigervnc/openbox/端末/フォント) を distro の pkg マネージャで導入 (apk/apt/pacman, 数十〜数百 MB)。② **他 distro** = Ubuntu(約90MB)/Arch Linux ARM/Kali を設定の切替で初回 DL (Wi-Fi 推奨, Alpine のみ同梱)。③ **重い GUI 端末** = konsole(KDE 数百 MB)/lxterminal(GTK)/urxvt を選択時に導入。④ 任意の CLI パッケージは端末内で apk/apt/pacman。

### ✅ T1. GUI で 📋ペースト / CMD スニペットを有効化 — 実装済 (実機未検証)
- **実装**: `GuiTopBar` の 📋/CMD を有効化。📋=Android クリップボードのテキストを `GuiKeyMapper.sendText(gui.rfb, text)` で keysym 送出 (GUI へタイプ)。CMD=`SnippetsSheet` を開き、選択文字列を同じく `sendText`。`SnippetsSheet` は `onRun:(String)->Unit` なので無改修で再利用。
- **主ファイル**: `ui/terminal/TerminalScreen.kt` (`GuiTopBar` の onPaste/onOpenSnippets + `GuiTabScreen` の SnippetsSheet 配線)。T6 と対で双方向クリップボードになる。

### ✅ T2. ピンチズームが TabBar/TopBar に被さる → タブ内だけで拡大 (表示クリップ) — 実装済 (実機未検証)
- **実装**: `gui/GuiScreen.kt` のルート Box に **`Modifier.clipToBounds()`** を付与。ズーム/パンで Bitmap が領域外へ描かれてもクリップされ、上の TabBar/TopBar へはみ出さない。FB 解像度・入力座標 (`toFb`) は不変。
- **検証**: コンパイル ✅ / ピンチズームでのはみ出し無しは実機で要確認。

### ✅ T3. ピンチ中に誤って右クリックになる — 実装済 (実機未検証)
- **原因 (元)**: 1 本指 DOWN で長押しタイマー開始 → 2 本指目で `onTouchEvent` が早期 return → 長押しがキャンセルされず `onLongPress`→右クリック発火。
- **実装**: T4 と一体で対応。① `GestureDetector` の `setIsLongpressEnabled(false)` で**長押しタイマーをそもそも動かさない** (`onLongPress` 自体を廃止)。② 2 本指へ移行した瞬間に gesture へ `ACTION_CANCEL` を流し、保留中の右クリック判定タイマー (後述) も破棄。
- **主ファイル**: `gui/GuiInputView.kt`。

### ✅ T4. 右クリックのジェスチャ再設計 (二度押し) — 実装済 (実機未検証)
- **ユーザー決定**: 単タップ長押しの右クリックは**完全に廃止**。単タップ=左クリックは維持。
- **実装した挙動**: ダブルタップして 2 回目の指を…
  - **動かさず保持 (`RIGHT_HOLD_MS=350ms`) → 右クリック (メニュー)** + 触覚フィードバック。
  - **`touchSlop` を超えて動かす → 左ドラッグ** (押下保持で移動。ウィンドウ移動/選択)。
  - **すぐ離す → 左クリック** (= 1 回目の単タップと合わせてダブルクリック)。
  - 判定は `onDoubleTap` で「保留 (`pendingRightClick`)」にし、`postDelayed` タイマー + `onTouchEvent` の MOVE/UP で分岐する自前ロジック。
- **主ファイル**: `gui/GuiInputView.kt` (`onDoubleTap` / `rightClickRunnable` / `onTouchEvent` の MOVE・UP / 2 本指分岐)。

### ✅ T5. ダブルタップドラッグがすぐ外れる — 実装済 (実機未検証)
- **原因 (元)**: ドラッグ保持中に `onLongPress` が発火し button=0 を送って左ドラッグが解除されていた。
- **実装**: T4 で `onLongPress` を廃止したので解消。ドラッグの解放は**全指 UP / 2 本指移行のときだけ**。
- **主ファイル**: `gui/GuiInputView.kt`。

### ✅ T6. GUI(xterm) の右クリックコピー → Android クリップボードへ — 実装済 (実機未検証)
- **実装**: `RfbClient` に `onServerCutText:(String)->Unit?` を追加。`handleServerCutText` で本文 (RFB は Latin-1) を上限 256KB まで読み取りコールバック。`GuiSession` がそれを受け、メインスレッドで Android `ClipboardManager.setPrimaryClip` へ格納。
- **⚠ 要実機確認**: xterm の選択は既定 PRIMARY。VNC cut-text が CLIPBOARD/PRIMARY のどちらを拾うかはサーバ依存 (TigerVNC は両対応のことが多い)。拾えない場合は xterm 起動に `-selection` 等を検討。
- **主ファイル**: `gui/rfb/RfbClient.kt`、`gui/GuiSession.kt`。T1(逆方向ペースト)と対で双方向クリップボードになった。

### ✅ T7. 外部ダウンロード前に確認ダイアログ + 設定で ON/OFF — 実装済 (実機未検証)
- **実装**:
  - 設定 **`confirmBeforeDownload: Boolean` (既定 ON)** を追加 (`AppSettings` + `TerminalSession.setConfirmBeforeDownload`)。設定シートにトグル「ダウンロード前に確認」。
  - 共通ダイアログ **`ui/components/DownloadConfirmDialog.kt`** (新規, AlertDialog)。
  - ① **distro 切替**: 非同梱かつ未展開 (`distros/<id>/bin` 無し) のときだけ確認。サイズは `DistroSpec.approxDownload`。OK で `switchDistro`。(`SettingsSheet.kt`)
  - ② **GUI パッケージ**: GUI タブ起動前に rootfs のバイナリ有無 (`guiPackagesInstalled`: Xvnc/Xtigervnc + openbox + 選択端末) で未導入判定 → 未導入&確認 ON ならダイアログ → OK で `gui.start`、やめる→タブを閉じる。(`TerminalScreen.kt` GuiTabScreen)
  - z2gui に **`check` サブコマンド**追加 (`GUI_INSTALLED`/`GUI_MISSING` を出力。端末/手動用。app 側のゲートは上記の Android ファイル判定で実施)。
  - OFF なら従来どおり無確認で取得。
- **⚠ 注意/未検証**: ① の判定は `distros/<id>/bin` の有無。② の判定はバイナリ実在 (`usr/bin` or `bin`)。distro クリーン再インストール後は GUI パッケージが消えるので次回 GUI 起動で再び確認が出る (想定どおり)。実機でダイアログ表示/キャンセル挙動は要確認。
- **主ファイル**: `settings/AppSettings.kt`、`core/TerminalSession.kt`、`ui/components/DownloadConfirmDialog.kt`(新)、`ui/settings/SettingsSheet.kt`、`ui/terminal/TerminalScreen.kt`、`proot/GuiScript.kt`。
- 関連: memory [[no-unsanctioned-downloads]] (勝手に DL しない方針) の UI 実装。

### ✅ T8. タブ削除を × ボタン → タブのダブルタップに — 実装済 (実機未検証)
- **実装**: `TabChip` を `combinedClickable(onClick=アクティブ化, onDoubleClick=閉じる)` に変更し、`×` ボタンは**撤去**。単タップ=切替・ダブルタップ=閉じる。最後の 1 枚 (`canClose=false`) はダブルタップでも閉じない。`TabBar` は端末/GUI 両画面共通なので両方に効く。
- **検証**: コンパイル ✅ / ダブルタップで閉じる・単タップで切替は実機で要確認。
- **主ファイル**: `ui/terminal/TerminalScreen.kt` (`TabChip`)。

### ✅ T9. 画面消灯ロック (💡) でも長時間後に画面が消える — 実装済 (実機未検証)
- **原因 (元)**: 端末画面 (`TerminalScreen`) と GUI 画面 (`GuiTabScreen`) で `keepScreenOn` state が別々の `remember` だったため、タブ種別を跨ぐと新画面側が初期値 false で `rootView.keepScreenOn=false` にしてフラグを落としていた。さらに View フラグだけでは OEM 省電力で不足の可能性。
- **実装 (①+②)**: ① keepScreenOn を**単一状態 `ScreenAwake.enabled`** (file-private object) にし、端末/GUI 両画面が同じ状態を読む → 画面跨ぎでも維持。② 適用は `activity.window.addFlags/clearFlags(FLAG_KEEP_SCREEN_ON)` の**ウィンドウ直付け** (Activity が取れなければ `View.keepScreenOn` へフォールバック)。既定 OFF・プロセス再起動でリセットは不変。
- **⚠ 要実機確認**: 「長時間後に消灯」が直ったかは実機で長時間放置して確認。なお消えるなら③部分 WakeLock 併用 (電池注意) が次の手。
- **主ファイル**: `ui/terminal/TerminalScreen.kt` (`ScreenAwake` / `applyKeepScreenOn` / `findActivity`)。

### 参考: 「クリーン再インストール」の挙動 (ユーザー質問への回答)
- distro の再展開 (`DistroInstaller.install`) は `files/distros/<id>` を **`deleteRecursively()` してから再展開** (`DistroInstaller.kt:53-57`)。→ **その distro に apk/apt/pacman で入れた物 (GUI パッケージ含む) は全削除され、素のクリーンな rootfs に戻る**。
- ただし **`/root` ホームは別バインド (`files/shared_home`) なので削除されない** = dotfiles・スクリプト・履歴・GUI 設定は残る。
- distro は id ごとに独立 (`distros/alpine` / `distros/archlinux` …)。1 つ再展開しても他は無傷。同梱 Alpine は `ROOTFS_VERSION` 更新時に自動再展開 (この時もシステムはクリーン化、`/root` は保持)。

---

## 🛠 M8-5 GUI の UI 統一・distro 対応・ターミナル選択 (2026-05-24 実装 / ✅ 2026-05-25 実機検証済)

ユーザー要望への対応。**実装は 2026-05-24、2026-05-25 に実機 (moto g66j 5G / Android 15 / arm64) で主要項目を検証済（下記「実機検証結果」）。**

| 要望 | 対応 | 主なファイル |
|---|---|---|
| GUI 時に上ツールバーが消える → **残す** | `GuiTabScreen` を端末画面と同じ枠組みに (TopBar + タブバー + キーボード)。`GuiTopBar` を追加。 | `ui/terminal/TerminalScreen.kt` |
| **使えないボタンはグレーアウト** | `TopBarIconButton(enabled=…)` を追加。GUI では 📋貼付 / CMD を `enabled=false`、💡/⌨切替/⚙ は有効。⚙ は端末タブが無いと無効。 | 同上 |
| **キーボードを GUI も CUI も同じに (フル対応)** | GUI でも端末の独自キーボード(CUSTOM) / 特殊キーバー(SYSTEM) をそのまま使用。押下の**バイト列を keysym へ橋渡し**して RFB へ送る。かな漢字変換の確定も keysym 化。 | `gui/GuiKeyMapper.kt` (`sendBytes`/`sendText`/`sendCtrlCombo`/`keysymForCursor`)、`TerminalScreen.kt` (`GuiSpecialKeyBar`) |
| **キーボード表示で GUI 解像度を変えない (上乗せ)** | キーボードはコンテンツ Box に**オーバーレイ** (BottomStart)。Box は常にフル高 → フィット不変。GUI 画面は `systemBars` inset のみ (ime を含めない)。SYSTEM 時の OS IME は overlay 側 `imePadding()` で特殊キーバーを持ち上げる。 | `TerminalScreen.kt`、`gui/GuiScreen.kt` (FAB 廃止・IME 表示を mode 連動に) |
| **選択中の OS で GUI を起動** | `GuiSession.start` が `AppSettings` から `distroId` を読み、その distro で `z2gui` を起動 (従来 alpine 固定)。`z2gui` を **distro 非依存**化 (apk/apt-get/pacman 自動判定、Xvnc/Xtigervnc 両対応)。未展開 distro は「先に端末タブで起動を」と案内 (勝手に DL しない)。 | `gui/GuiSession.kt`、`proot/GuiScript.kt` |
| **xterm 以外のターミナル選択** | `GuiTerminal` enum (xterm / rxvt-unicode(urxvt) / lxterminal / konsole)。設定に「GUI のターミナル」を追加。`z2gui` が選択端末を起動・未導入なら自動導入。 | `proot/GuiTerminal.kt`(新)、`settings/AppSettings.kt`、`ui/settings/SettingsSheet.kt`、`proot/ProotLauncher.kt` |

### keysym 橋渡しの仕様 (`GuiKeyMapper.sendBytes`)
独自キーボード/フリックは `onBytes` で **VT バイト列**を吐くので、GUI(RFB) 用に変換する:
- 先頭 `ESC(0x1B)` + 後続 = **Alt 修飾** → `Alt_L` 押下のまま後続を再帰送出
- 単独 `ESC` / `Tab(0x09)` / `Enter(CR/LF)` / `BackSpace(BS/DEL)` → 専用 keysym
- `0x01–0x1A` = **Ctrl+英字** → `Control_L` 修飾付きでその英字 (`0x01→'a'`)
- それ以外 = 印字 UTF-8 → コードポイントごとに keysym (かなは `0x01000000+cp` の Unicode keysym)

### ✅ 実機検証結果 (2026-05-25, moto g66j 5G `ZY32LNFX2B` / Android 15 / arm64, debug クリーン再インストール)
- GUI タブで **TopBar 維持**・**📋/CMD グレーアウト**・💡/あ/⚙ 有効 を確認。GUI タブ × で閉じると TopBar が端末用 (📋/CMD 復活) に戻る。
- **選択中 distro (alpine) で GUI 起動**。GUI パッケージ `apk add`(87 pkg) → Xvnc 起動 → `RFB connected 1080x2400` → ZRLE デコード → openbox+**xterm** 描画。base Alpine は APK 同梱アセットから展開 (ネット不要)、GUI パッケージのみ `apk add`。
- **キーボードオーバーレイ**: キーボード表示でも RFB は 1080x2400 のまま (解像度不変) を確認。
- **keysym 橋渡し**: 独自キーボード(CUSTOM) で `ls`+Enter が xterm で実行されることを確認 (バイト列→keysym→RFB 経路 OK)。相対マウスでカーソル移動→タップでクリック・フォーカス (xterm タイトルバー青) も確認。
- ライフサイクル: GUI タブ × で `z2gui stop` 実行 → `Xvnc died` → `/proc/net/tcp` の 5901 消滅 (リークなし)・端末タブへ復帰。

### ✅ 非 Alpine (Arch Linux ARM) 実機検証 + 修正 (2026-05-25, release で確認)
release ビルド (普段使い・distro=archlinux) で GUI を起動し、非 Alpine 経路 (pacman) を検証。**2 件のバグを発見・修正・再検証済**:

1. **接続タイムアウト (繋がらない直接原因)**: `GuiSession.connectWithRetry` の待ち時間が 60s 固定で、Arch の pacman 初回導入 (154 pkg + post-install ≈ 2.5 分) が間に合わず ERROR (「Xvnc に接続できません」) になっていた。Xvnc 自体は正常 (ログに `Listening … port 5901`)。Alpine は apk が十数秒で 60s 窓内だった。
   → **修正 (`gui/GuiSession.kt`)**: 待ち時間を 300s に延長。さらに z2gui の PTY が閉じた (= z2gui 終了 = 導入失敗) ら即 fail する `ptyClosed` 監視を追加 (最大時間まで無駄に待たない)。待機メッセージも「初回はパッケージ取得で数分」に変更。
2. **xterm が黒画面 (端末が起動しない)**: xterm が UTF-8 で要求するコアフォント `-misc-fixed-…-iso10646-1` が Arch で読めず起動失敗 (`xterm: cannot load font`)。`xorg-fonts-misc` 追加や Xvnc `-fp` 追加では解決せず (その Unicode 版が無い)。
   → **修正 (`proot/GuiScript.kt`)**: xterm を **Xft (TrueType) フォントで起動** (`xterm -fa monospace -fs 11`)。fontconfig の `monospace` (= ttf-dejavu/noto) を使い、コアフォント `fixed` 依存を完全回避 (distro 非依存)。`-fp` 案は不発だったため撤去。フォントパッケージ (xorg-fonts-misc / xfonts-base) は他のコアフォント利用アプリ向けの保険として残置。

**再検証 (2026-05-25):** release(Arch) で GUI 起動 → 接続成功 → **xterm 表示** (Xft monospace)。debug(Alpine) でも xterm 表示を確認し**非回帰**。

### ⚠ まだ未検証 / 要注意 (次の人へ)
- **xterm 以外の端末 (urxvt/lxterminal/konsole) の実起動は未検証**（検証は xterm のみ）。urxvt もコアフォント依存なので Arch で同様問題が出る可能性 (xterm は -fa で対策済)。lxterminal(GTK)/konsole(Qt) は fontconfig 系なので恐らく問題なし。
- **apt 系 (Ubuntu/Debian) は未検証**。`tigervnc-standalone-server` が `Xvnc`/`Xtigervnc` どちらを出すか、`xfonts-base` で足りるかは実機で要確認 (pacman=Arch と apk=Alpine は検証済)。
- **konsole** は KDE 系で初回導入が大きい (数百 MB)。memory [[no-unsanctioned-downloads]] の通り、初回 GUI 起動 = `apk/apt/pacman add` が走るので回線注意。
- かな/Unicode keysym を X アプリが受けるかは環境依存。CUSTOM の ASCII 入力は検証済だが、**かな確定は未検証**。
- SYSTEM モードの OS IME + 特殊キーバーの重なり (imePadding と systemBars の二重 inset) は実機で見た目調整の余地あり (CUSTOM のみ検証)。
- **stale Xvnc の再入**: 設定 (フォント等) を変えても、前回の Xvnc が生き残っていると `start_x` の `x_alive` ガードで古いセッションに再接続され新設定が反映されない。検証時は `z2gui stop` で確実に落としてから開き直すこと。

> 目的: Alpine(PRoot) の中で X アプリ（ファイラ / エディタ / 軽量ブラウザ等）を起動し、
> その画面を z2term 内に表示・操作できるようにする。
> M1-HANDOFF:50 の「WebView / X11 / VNC タブ → M6」で構想され見送られていた機能の再開。

---

## ✅ M8-4 タブ統合 実装・実機検証済 (2026-05-24)

GUI を**正式タブ**として端末タブと並べて開けるようにした（暫定 `GuiActivity` 入口を卒業）。

| ファイル | 役割 |
|---|---|
| `core/AppSession.kt`（新規） | タブに並べられるセッションの共通 IF（`id` / `label: StateFlow<String>` / `shutdown()`）。`TerminalSession` と `GuiSession` が実装。タブバーはこの型だけ見てチップを描く。 |
| `core/SessionManager.kt` | 保持を `List<AppSession>` 化（端末/GUI 混在）。`openNewGui()` を追加。`close()` は共通 `shutdown()` を呼ぶ。 |
| `core/TerminalSession.kt` | `AppSession` 実装（`id`/`label`/`shutdown` に `override`）。挙動は不変。 |
| `gui/GuiSession.kt` | `AppSession` 実装（`id`/`label="GUI"` を追加、`shutdown()=stop()`）。**`stop()` の Xvnc リーク修正**（下記）。 |
| `ui/terminal/TerminalScreen.kt` | アクティブが `GuiSession` なら `GuiTabScreen`（タブバー + `GuiScreen`）へ早期 return。端末側の既存コードはそのまま（`active` を `TerminalSession` に絞るだけ）。タブバーに **🖥 ボタン**（端末用「+」の隣）を追加し GUI タブを新規作成。 |
| `AndroidManifest.xml` | `GuiActivity` を `exported=false` 化（adb am start 含む外部起動を遮断。debug 専用ツールとして残置）。 |

**UI（ユーザー選択）:** GUI タブの新規作成は「`+`(端末) の隣に専用 🖥 ボタンを並べる」方式。解像度は画面実サイズ。タブ切替で離れても `GuiSession` は SessionManager が保持し続け、GUI は動き続ける（停止はタブを × で閉じたときのみ）。

### ⚠️ M8-4 で直したバグ: GUI タブを閉じても Xvnc が 5901 で生き残る（リーク）
`PtyProcess.close()` は proot に **SIGHUP** を送るだけ。proot は ptrace トレーサなので、シグナルで死ぬと**カーネルがトレース対象を自動デタッチ**し、`--kill-on-exit` が効かず Xvnc が生き残る（さらに M8-3 で GUI を `setsid` 分離済み）。

**修正（`GuiSession.stop()`）:** PTY を閉じる前に、**別 proot で `z2gui stop` を流して Xvnc/openbox/xterm を明示 kill**（`/proc` は proot に実体バインドされ全 proot が同一 uid なので、別インスタンスからでも pid 走査で kill できる＝GuiScript の `stop_x`）。EOF まで読んで完了待ち。実機で close→`/proc/net/tcp` の 5901 消滅を確認（リークなし）。

**実機検証 (ZY32LNFX2B / Android15):** 🖥 で GUI タブ作成 → `RFB connected 1080x2400` → openbox+xterm 描画。端末タブ⇄GUI タブ切替で双方維持（切替中も 5901 は LISTEN+ESTABLISHED 継続）。GUI タブ × で閉じ → 5901 消滅・端末タブへ自動復帰・アプリ無事。

### ✅ M8-4 追加実装 (2026-05-24): ZRLE / 差分描画 / ズーム・パン・回転 / 相対マウス
- **ZRLE デコード** (`rfb/RfbClient.kt`): タイル 64x64・持続 zlib ストリーム・raw/solid/packed palette/plain RLE/palette RLE・CPIXEL 3byte。SetEncodings を ZRLE→CopyRect→Raw に変更。実機で TigerVNC が ZRLE 送出→正常描画を確認（ログ「ZRLE デコード稼働中」）。Tight は未対応（loopback なので不要と判断）。
- **差分 setPixels** (`rfb/RfbClient.kt`): `pushFrame` を全画面転送から更新矩形の外接範囲だけに変更。
- **ズーム/パン・回転** (`gui/GuiViewport.kt` 新規 / `GuiScreen.kt` / `GuiInputView.kt`): 「固定+ズーム」方式。`GuiViewport`(scale/panX/panY) を GuiScreen(描画) と GuiInputView(入力座標) で共有。ピンチ=ズーム(centroid 固定, 最大5倍)、ズーム中の2本指=パン(画面外クランプ)、等倍時の2本指=ホイール(従来維持)。回転は MainActivity の configChanges で再生成されず、新サイズへ再フィット＋ズーム/パン状態は GuiSession が保持。
- **相対マウス (トラックパッド式)** (`gui/GuiInputView.kt`): ユーザー要望でタッチ位置の絶対指定→相対移動に変更。仮想カーソル(FB座標)を保持し、1本指移動で相対移動(1:1)、タップ=現在位置で左クリック、長押し=右クリック、ダブルタップ+移動=左押下保持ドラッグ(ウィンドウ移動/選択)。実機で「スワイプ位置へジャンプせず現在位置から相対移動」を確認。

> ⚠️ ピンチズーム/パン/2本指ホイールは**マルチタッチ**のため adb `input` で自動検証できず、**実機の手操作で確認が必要**（相対マウス・タップ・1本指移動・タブ操作は検証済）。

### M8-4 残り / 今後
- Tight エンコーディング（必要なら）。
- GUI タブ解像度を画面実サイズ固定にしている（コンテンツ領域実測にすると letterbox が減る）。
- 回転時に Xvnc 自体を作り直す案（landscape アプリ向け。現状は固定+ズーム）。

---

## ✅ M8-3 実装・実機検証済 (2026-05-24)

GUI を**操作**できるようになった（クリック・文字入力・ホイール）。コミット `8029d49`（ローカルのみ・push なし）。

| ファイル | 役割 |
|---|---|
| `gui/GuiInputView.kt`（新規） | Compose Canvas の上に重ねる透明入力 View。タップ=左クリック / 長押し=右 / 1本指ドラッグ=左押下移動 / 2本指上下=ホイール。ソフト IME は `BaseInputConnection.commitText`→keysym、物理/注入キーは `onKeyDown/Up`→keysym。表示↔FB 座標変換は `GuiScreen` のフィット計算と一致。 |
| `gui/GuiKeyMapper.kt`（新規） | Android `KeyEvent`/コードポイント → X11 keysym 変換表（Latin-1 直対応 + 矢印/Enter/BS/Tab/修飾など）。端末用のバイト送出とは独立した keysym 経路。 |
| `gui/rfb/RfbClient.kt` | `sendPointerEvent`(type 5) / `sendKeyEvent`(type 4) を追加。送信は単一スレッド+`writeLock` で直列化（受信ループの FBUR と混線させない）。 |
| `gui/GuiScreen.kt` | CONNECTED 時に `GuiInputView` を重ね、⌨ FAB でソフトキーボードをトグル。 |
| `gui/GuiSession.kt` | 接続待ちを `connectWithRetry` に変更（捨てソケットをやめ本物の RFB 接続を ConnectException の間だけリトライ → 1→0 で Xvnc を落とさない）。 |

**実機検証 (ZY32LNFX2B / Android15):** GuiActivity 起動 → RFB 接続維持（EOF なし）→ タップで xterm にフォーカス（タイトルバーが青）→ keyevent 注入で `ls` 入力・実行（`~ #` の実シェル）。Xvnc/openbox/xterm が安定生存。

### ⚠️ M8-3 最大の罠: Xvnc が接続直後に SIGTERM で即死していた（真因と修正）
当初 `setsid`/`-noreset`/PTY-SIGHUP を疑ったが**全部ハズレ**。strace（proot 下でも `-e trace=kill,tgkill` は動く。`-o file` はバッファで未フラッシュなので **stderr→PTY→drainPty→logcat にライブ出力**させて捕獲）で犯人を特定:

- **`ProotLauncher.launch` が `SHELL=$resolvedCommand` を渡す**（ProotLauncher.kt:140）。GUI 起動は `command="/usr/local/bin/z2gui"` なので **`SHELL=/usr/local/bin/z2gui`** になる。
- **xterm が `$SHELL`(=z2gui) を起動** → `z2gui start` が再帰 → 再帰側 `start_x` の `stop_x` が**動作中の Xvnc を kill**。

**修正（`proot/GuiScript.kt` 内で完結）:**
1. z2gui 冒頭で **`SHELL` を実体シェル（`/bin/bash|ash|sh`）に上書き** → 再帰起動を断つ（真の修正）
2. `start_x` に**再入ガード** `x_alive()`（生存 Xvnc があれば `stop_x` せず `exec $SHELL`。stale ソケットだけなら従来通り掃除）= 安全網
3. GUI 配下を `setsid + </dev/null` で launcher の制御端末から分離 + `while x_running; do sleep 2; done` で proot を維持

> ✅ **根本対応済 (2026-05-24)**: 地雷だった `ProotLauncher.kt:140`（command が shell でないと SHELL が壊れる）を恒久対応。`resolveLoginShell()` を追加し、**command がシェルでなければ SHELL を実体シェル（fallbackShell → /bin/bash → /bin/ash → /bin/sh）へ振り替える**ようにした（`KNOWN_SHELLS` で判定）。端末起動（command が sh/bash/zsh）は従来通りで回帰なし。z2gui 側の SHELL 上書き（GuiScript.kt）は安全網として残置。
> 📌 `adb shell input text` は GuiInputView にルートされない（Android IME 経路）。実機の文字入力検証は `input keyevent` か実ソフトキーボードで行う。

### M8-4 の着手ポイント（次の人向け）
- ~~**タブ統合**: `GuiActivity` 暫定入口（debug のみ）を廃し SessionManager に GUI セッション種別を追加 / exported=false 化~~ → ✅ 対応済 (2026-05-24, 本書冒頭「M8-4 タブ統合」参照)。
- **エンコーディング**: ZRLE/Tight（`java.util.zip.Inflater`）。現状 Raw + CopyRect のみ。
- **ズーム/パン・回転**: 端末の `TerminalInputView.kt` のジェスチャ流用。回転で Xvnc 作り直す or 固定+ズーム。再接続。
- 既知の小改善: 現状フレーム毎に全画面 `setPixels`。重ければ更新矩形だけにする。
- ~~上記「ProotLauncher の SHELL 罠」の恒久対応~~ → ✅ 対応済 (2026-05-24, `resolveLoginShell`)。

---

## ✅ M8-2 実装・実機検証済 (2026-05-23 セッション2)

新パッケージ `com.zerotoship.z2term.gui` を追加。**表示のみ**（入力は M8-3）。

| ファイル | 役割 |
|---|---|
| `gui/rfb/RfbClient.kt` | RFB 3.8 クライアント。None 認証 → ServerInit → `SetPixelFormat`(32bpp LE truecolor=ARGB_8888互換) → `SetEncodings`(Raw+CopyRect) → `FramebufferUpdateRequest` ループ。矩形を IntArray→Bitmap へ。再描画は `redraw` StateFlow（端末 redrawTick 方式）。受信は `run()` を IO で回す。 |
| `gui/GuiSession.kt` | ライフサイクル。`ProotLauncher.launch(command="/usr/local/bin/z2gui", extraArgs=["start","WxH"])` → 5901 を待つ → RfbClient 接続 → 受信ループ起動。`stop()` は `pty.close()`(→proot 終了→kill-on-exit で Xvnc 停止)。 |
| `gui/GuiScreen.kt` | Bitmap を Compose `Canvas`(`nativeCanvas.drawBitmap`) でアスペクト比保持フィット表示。`frameLock` で setPixels/描画を直列化。 |
| `gui/GuiActivity.kt` | **M8-2 検証用の暫定単独 Activity**（debug のみ。release は即 finish）。`adb am start` で端末 UI を触らず表示確認できる。正式なタブ統合は M8-4。 |

`ProotLauncher.launch` に `extraArgs: List<String>` を追加（command は単一トークンなので引数付き起動用）。

**実機検証 (ZY32LNFX2B / arm64 / Android15):** `am start -n …/.gui.GuiActivity --ei width 1280 --ei height 720` →
ログ `RfbClient: RFB connected: 1280x720 'root@localhost'` → スクリーンショットに **openbox の黒ルート + xterm の白窓 + X カーソル** が描画。BACK → `onDestroy` → `stop()` で Xvnc/openbox 消滅・5901 解放を `/proc` 走査と `/proc/net/tcp` で確認（リークなし）。デコードエラーなし。

### M8-3 以降の着手ポイント（次の人向け）
- **入力 (M8-3)**: RFB PointerEvent(type 5: buttonMask+x,y) と KeyEvent(type 4: keysym down/up) を `RfbClient` に送信メソッド追加（writer は mutex で直列化）。表示座標→FB 座標の逆変換が要る（`GuiScreen` のフィット計算を共有化）。タップ=左クリック / 長押し or 2本指=右 / スクロール=btn4/5。キーは `Char→keysym` 表（Latin-1 直対応 + 矢印/Enter/BS/Tab/修飾）。端末用キーボードはバイト送出なので GUI 用に keysym 経路を別に。
- **M8-4**: ZRLE/Tight、ピンチ/パン、解像度・回転で Xvnc 作り直し or 固定+ズーム、再接続、**タブ統合**（`GuiActivity` 暫定入口を廃し SessionManager に GUI セッション種別を追加 / exported=false 化）。
- 既知の小改善: 現状フレーム毎に全画面 `setPixels`。重ければ更新矩形だけ `setPixels(buffer,…,x,y,w,h)` にする。

---

## 進捗・引き継ぎ (2026-05-23 セッション)

### このセッションでやったこと（すべてコミット済 / push なし、ブランチ main）

| commit | 内容 |
|---|---|
| `4cce382` | fix(emulator): **ピンチで画面外の文字が消える問題を修正**（行=拡大時 scrollback から復帰 / 列=縮小時に行末セルを保持）。`TerminalRow.kt` `TerminalBuffer.kt` `TerminalEmulator.kt` |
| `f27f8e2` | build: **JDK 17 を gradle.properties に固定**（JDK 26 だと AGP の JdkImageTransform/jlink が失敗）。`org.gradle.java.home` |
| `4a25b0c` | docs(M8): 本設計書を追加 |
| `df179ca` | feat(keyboard): **かな漢字変換 (内蔵 IME) [WIP]**（既存の作業中ぶんをまとめて確定。実機未検証） |
| `592a3ea` | feat(M8): **z2gui ランチャ配置 (M8-1)** |

> ⚠️ **ピンチ修正と IME はまだ実機未確認**。次の担当者はまず実機で回帰確認を。
> ⚠️ ビルドは **JDK 17 必須**（pinned 済み）。`./gradlew :app:assembleFossDebug` / `installFossDebug` でそのまま通る。release は `assembleFossRelease`（`keystore.properties` で本番署名）。

### M8-1 でできたこと（実装の現状）
- `proot/GuiScript.kt`（新規）: `z2gui` スクリプト本体。`start [WxH]` / `stop` / `status` / `install`。
  `Xvnc :1`（`-SecurityTypes None -localhost`、RFB **127.0.0.1:5901 限定**）+ `openbox` + `xterm`。
  未導入なら `apk add tigervnc openbox xterm font-noto ttf-dejavu` を自動実行。
- `proot/ProotLauncher.kt`: `ensureGuiScript()` を追加し、launch 毎に `/usr/local/bin/z2gui` を配置
  （`ensureSshdWrapper` と同方式）。
- 検証: Kotlin コンパイル緑、生成シェルを `sh -n` で構文 OK。**実機での Xvnc 起動も確認済み（上記「✅ 関門突破」参照）**。
- 定数: `Z2TERM_VNC_PORT=5901` / `Z2TERM_VNC_DISPLAY=1` / `Z2TERM_GUI_PACKAGES`（GuiScript.kt）。

### ✅ 関門突破: 実機で Xvnc 起動確認 (2026-05-23, arm64 / Android 15)
案B全体の前提＝**Xvnc が PRoot/Alpine で動くか** → **動いた**。方式見直しは不要、M8-2 へ進める。

検証結果（実機 ZY32LNFX2B。adb の run-as から ProotLauncher と同じ env/args で proot を起動し z2gui を実行）:
- `z2gui install`: tigervnc/openbox/xterm/mesa など 87 パッケージ導入成功（433 MiB）。**apk add は PRoot で動く。**
- `z2gui start 1280x720`: `Xvnc TigerVNC 1.13.1` 起動 → ログに `vncext: VNC extension running!` /
  `Listening for VNC connections on local interface(s), port 5901` / `created VNC server for screen 0`。
- X サーバが実応答: `xmodmap -display :1 -pm` が modifier map を取得（実 X クライアントが接続成功）。
- WM 接続: openbox が X に接続（`Obt-Message: Xinerama extension is not present` は無害）。
- 既知の無害ログ: `[mi] mieq: warning: overriding existing handler ...`（TigerVNC 既知。致命でない）。
- `z2gui status` / `z2gui stop` のライフサイクルも実機で確認（start がブロック→stop で解放）。

> 補足: PRoot は ptrace ベース。`/dev/shm`・`MIT-SHM`・shared memory 周りで将来 X が落ちる場合は
> Xvnc 起動オプション（`-noreset` 等）や rootfs の `/dev/shm` 用意を調整する（今回は問題なし）。

### ⚠️ ゲート検証中に直したバグ: `z2gui start` が即終了して Xvnc が道連れに死ぬ
`start_x` の末尾に `wait` が無く、`z2gui start` が起動直後に return していた。M8-2 は
`launch(command="/usr/local/bin/z2gui start WxH")` で起動する想定なので、return すると proot が終了し
`--kill-on-exit` で Xvnc も即殺され、RfbClient が接続する前に消える。→ `start_x` 末尾に `wait` を追加し、
Xvnc/WM が生きている間ブロックするよう修正（`z2gui stop` で殺されるか proot 終了まで待機）。`GuiScript.kt`。
設計書「2-2」の例（116 行目に `wait`）に合わせた形。実機で start(ブロック)→stop(解放) を確認済み。

### M8-2 以降の着手ポイント（次の人向け）
- 新パッケージ `com.zerotoship.z2term.gui` を作る。
- `gui/rfb/RfbClient.kt`: RFB 3.8。None 認証 → ServerInit → `SetPixelFormat`(ARGB_8888 互換) →
  `SetEncodings`(まず Raw + CopyRect) → `FramebufferUpdateRequest(incremental)` ループ →
  矩形を `Bitmap` の IntArray へ。受信は IO コルーチン、再描画は StateFlow（`TerminalSession.redrawTick` を踏襲）。接続先 `127.0.0.1:5901`。
- `gui/GuiSession.kt`: `ProotLauncher.launch(command="/usr/local/bin/z2gui start <W>x<H>")` で起動 →
  5901 を待って RfbClient 接続 → 停止は `z2gui stop`。
- `gui/GuiScreen.kt`: Bitmap を Compose Canvas に描画。ピンチ/パンは `ui/terminal/input/TerminalInputView.kt` を参考に。
- 入力は M8-3（ポインタ=PointerEvent / キー=keysym。端末用キーボードはバイト送出なので GUI 用に keysym 経路を別途）。
- 詳細な段階は本書「4. 段階プラン」、再利用元は「6. 参考」を参照。

---

## 0. 大前提（PRoot 環境の制約）

- PRoot は **root 権限もハードウェアフレームバッファ(DRM/KMS)も使えない**。
  → 物理ディスプレイへ直接描く X サーバは不可。**仮想 X サーバ + 画面転送**の構成にする。
- GPU アクセラレーションは基本使えない → **ソフト描画 (llvmpipe)**。軽い GUI 向け。
- ネットワーク名前空間は分離されない → **`127.0.0.1` のループバックは Android 側からも届く**
  (VNC を localhost で立てれば z2term から TCP 接続できる)。
- GUI 一式は **APK に同梱しない**。必要時に `apk add` で取得（既存の「非同梱 distro は DL」方針と同じ。APK サイズを増やさない）。

---

## 1. 全体アーキテクチャ

```
[Alpine rootfs]                                   [z2term (Kotlin)]
 Xvnc :1  ──  openbox(WM)  ──  GUIアプリ
    │  フレームバッファ (メモリ上)
    └── RFB プロトコル (TCP 127.0.0.1:5901) ──▶ RfbClient ──▶ Bitmap ──▶ Compose Canvas
              ◀── 入力 (PointerEvent / KeyEvent) ──
```

- **RFB** = VNC が使う画面転送プロトコル。
- loopback 限定なので **認証なし**で安全に運用する（`-SecurityTypes None -localhost`）。
  外部 NIC には一切 bind しない。

---

## 2. rootfs 側（オンデマンド `apk add`）

### 2-1. パッケージ
| 用途 | パッケージ | 備考 |
|---|---|---|
| 仮想 X + VNC サーバ | `tigervnc` | `Xvnc` を提供（X サーバと VNC が一体で楽） |
| ウィンドウマネージャ | `openbox` | 軽量（数 MB）。初期はこれ。DE は後述 |
| 動作確認用アプリ | `xterm` | まず「何か映る」を確認するため |
| フォント | `font-noto` `ttf-dejavu` | 日本語/英字。文字化け回避 |
| (後) GL アプリ用 | `mesa-dri-gallium` | llvmpipe ソフト GL |
| (後) リッチ DE | `xfce4` 等 | 数百 MB。任意。初期は入れない |

### 2-2. 起動スクリプト `/usr/local/bin/z2gui`
`ProotLauncher.ensureSshdWrapper()` と同じ要領で launch 毎に配置（idempotent）。

```sh
#!/bin/sh
# 引数: 幅 高さ (z2term から渡す)
W="${1:-1280}"; H="${2:-720}"
export DISPLAY=:1
# 既存の :1 が残っていたら掃除
vncserver -kill :1 2>/dev/null
rm -f /tmp/.X11-unix/X1 /tmp/.X1-lock 2>/dev/null
Xvnc :1 -geometry "${W}x${H}" -depth 24 \
     -SecurityTypes None -localhost -rfbport 5901 &
# WM とランチャ
sleep 0.5
openbox &
xterm &        # 初期の自動起動アプリ（後で設定可能に）
wait
```

- `/tmp/.X11-unix` が要るので **`/tmp` 書き込み可**を確認（rootfs 内 `/tmp`、TMPDIR 既設）。
- 解像度は z2term の画面サイズ／設定から `W H` で渡す。

### 2-3. インストーラ
端末セッションで以下を実行し、完了マーカー（例 `~/.z2gui-installed`）を置く:
```sh
apk update && apk add tigervnc openbox xterm font-noto ttf-dejavu
```
- 進捗は通常どおり端末に出る。失敗（ネット無し等）はそのまま見える。
- z2term 側に「GUI 環境をインストール（〜MB DL）」ボタンを置き、このコマンドを流す。

---

## 3. z2term 側（新パッケージ `com.zerotoship.z2term.gui`）

### 3-1. `rfb/RfbClient.kt` — RFB 3.8 クライアント
- **ハンドシェイク**: ProtocolVersion 交換 → Security=None(type 1) → ClientInit(shared) / ServerInit(FB 幅高/PixelFormat/名前)
- **`SetPixelFormat`**: 32bpp・Android `ARGB_8888` に合う並びを要求 → ピクセル変換コスト削減
- **`SetEncodings`**: 第一段階は **Raw + CopyRect**（loopback なので帯域より実装の単純さ優先）。
  第二段階で **ZRLE / Tight**（`java.util.zip.Inflater` で zlib 展開可）を追加
- **更新ループ**: `FramebufferUpdateRequest(incremental)` → 返ってくる矩形を `Bitmap` の
  `IntArray` に書き込み → 変更通知
- **スレッド**: 受信は IO コルーチン。描画通知は端末の `redrawTick` と同じく StateFlow で
  Compose に伝える（既存パターン流用）。送信（入力）は writer を mutex で直列化
- **接続先**: `127.0.0.1:5901` 固定（loopback）

### 3-2. `GuiScreen.kt` / レンダラ
- `Bitmap` を Compose `Canvas`（または `Image`）に描画
- **ピンチでズーム / ドラッグでパン**（端末の `ui/terminal/input/TerminalInputView.kt` の
  ScaleGestureDetector・ジェスチャ実装を参考に流用）
- 表示座標 ↔ フレームバッファ座標の変換器を持つ（入力で必須）

### 3-3. 入力
- **ポインタ**（RFB PointerEvent / type 5: buttonMask + x,y）
  - タップ = 左クリック / ドラッグ = ボタン押下移動 / 長押し or 2 本指 = 右クリック /
    スクロール = ホイール(btn 4/5)
  - まずは「触った位置 = 絶対座標」方式（トラックパッド方式は後で任意）
- **キーボード**（RFB KeyEvent / type 4: keysym down/up）
  - 既存ソフトキーボードを再利用しつつ、**バイトでなく X keysym を送る**
  - `Char→keysym`（Latin-1 はほぼ直対応）+ 矢印 / Enter / BS / Tab / 修飾キー等の表が必要
  - ※ 端末用キーボードは「バイト列送出」なので、GUI 用に keysym 送出の経路を別に作る

### 3-4. `GuiSession.kt` — ライフサイクル
- `z2gui <W> <H>` を（隠し PTY/exec で）起動 → 5901 が開くのを待つ → `RfbClient` 接続
- 起動は `proot/ProotLauncher.launch(command = "/usr/local/bin/z2gui ...")` を流用
- 停止: `RfbClient` 切断 + `vncserver -kill :1`（スクリプトに stop サブコマンドを足す）
- 状態（未インストール / 起動中 / 接続済 / エラー）を UI に出す

### 3-5. タブ統合・設定
- 既存タブ機構に **「GUI」タブ種別**を追加（端末タブと並列に持てる）
- 設定: 解像度 / DPI、WM か DE か、自動起動アプリ、スケーリング方式

---

## 4. 段階プラン（マイルストーン）

| Phase | 内容 | 完了条件（実機で確認） |
|---|---|---|
| **M8-1** ✅ | rootfs インストーラ + `z2gui` 起動スクリプト | 実機で `Xvnc`+openbox 起動確認済み |
| **M8-2** ✅ | `RfbClient` ハンドシェイク + Raw/CopyRect デコード → Bitmap。**表示のみ**（入力なし） | 実機で**リモート画面が映る**ことを確認済み |
| **M8-3** ✅ | ポインタ + keysym キーボード | 実機で**操作できる**（クリック=フォーカス・xterm へ文字入力）を確認済み |
| **M8-4** ✅(主要) | ✅ タブ統合 / 停止・クリーンアップ(Xvnc リーク修正) / ZRLE / 差分描画 / ズーム・パン・回転 / 相対マウス。残: Tight(任意)・回転で Xvnc 作り直し(任意) | 実用レベル |

> 推奨は **M8-1 から**。一番リスクが低く、「GUI が rootfs 内で立ち上がる」ところまで
> z2term 改修なしで単独確認できる。

---

## 5. 主な判断ポイント / リスク

- **エンコーディング**: Raw → ZRLE の二段で十分か（loopback なら Raw でも体感は悪くない想定）
- **右クリック / スクロールのジェスチャ割り当て**（タッチ UX）。長押し=右 か 2 本指=右 か
- **キーボード**: keysym 表の整備が地味に効く。既存の日本語フリック等は端末用なので GUI へは別経路
- **GUI の置き場所**: 新タブ種別にするか、端末セッションのモード切替にするか
- **解像度と回転**: 端末回転で Xvnc を作り直すか、固定解像度をパン/ズームで見るか
- **クリーンアップ**: アプリ kill 時に Xvnc が残らないよう `--kill-on-exit`（PRoot 既設）+ stop 経路

---

## 6. 参考（既存コードの再利用元）

| 用途 | 既存ファイル |
|---|---|
| PRoot 起動・rootfs へのスクリプト配置 | `app/src/main/java/com/zerotoship/z2term/proot/ProotLauncher.kt` |
| ピンチ/ドラッグ等のジェスチャ | `app/src/main/java/com/zerotoship/z2term/ui/terminal/input/TerminalInputView.kt` |
| StateFlow 駆動の再描画パターン | `app/src/main/java/com/zerotoship/z2term/ui/terminal/TerminalRenderer.kt` / `core/TerminalSession.kt` |
| TCP ソケット接続（参考） | `app/src/main/java/com/zerotoship/z2term/channel/SshChannel.kt` |
| 非同梱リソースの provisioning 思想 | `app/src/main/java/com/zerotoship/z2term/distro/DistroDownloader.kt` |
