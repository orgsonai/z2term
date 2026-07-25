# Z2Term 設計書 兼 仕様書

最終更新: 2026-07-25 / 対象バージョン: 0.8.235-alpha (versionCode 243)

> 本書は Z2Term の **詳細設計 + 仕様** をまとめた技術文書。実装担当・レビュー担当向け。
> 利用者向けのやさしい説明は `docs/ja/HANDBOOK.md` を参照。
> English version: `docs/en/DESIGN-SPEC.md`.

---

## 目次

1. [概要](#1-概要)
2. [技術スタック](#2-技術スタック)
3. [全体アーキテクチャ](#3-全体アーキテクチャ)
4. [レイヤ別 詳細設計](#4-レイヤ別-詳細設計)
5. [主要データフロー](#5-主要データフロー)
6. [機能仕様](#6-機能仕様)
7. [設定項目](#7-設定項目)
8. [パーミッション](#8-パーミッション)
9. [ビルド / 同梱物](#9-ビルド--同梱物)
10. [既知の制約と設計上の罠](#10-既知の制約と設計上の罠)
11. [l2s 制約と native passthrough](#11-l2s-制約と-native-passthrough)
12. [用語集](#12-用語集)

---

## 1. 概要

**Z2Term** は Android 単体で動く独自実装のターミナルエミュレータ + Linux 実行環境。

- **root 不要**: `forkpty(3)` + **PRoot** (ユーザー空間の chroot/bind エミュレーション) で、
  通常権限のアプリ内に Linux ディストロ (Alpine / Ubuntu / Arch / Kali) を展開して動かす。
- **自前のターミナルエミュレータ**: xterm 互換の VT/ANSI 解釈を Kotlin で実装。
- **自前の UI / キーボード**: Jetpack Compose。独自フリックキーボード (英字 + 日本語/カタカナ) と OS IME を切替可能。
- **SSH 両方向**: 端末から外部へ (JSch クライアント)、PC から端末へ (dropbear サーバ)。
- **ファイル連携**: SAF DocumentsProvider で他アプリから rootfs/ホームを R/W、proot 内から Android 共有ストレージへ `cd`。
- **GUI デスクトップ**: distro 内で Xvnc + 軽量 WM/アプリを起動し、内蔵 RFB(VNC) クライアントで表示（`gui/` パッケージ）。動画はソフト描画、音声はオプトインで PulseAudio→TCP→AudioTrack ブリッジ（`AudioBridge`）。
- **実行エンジン**: 既定は **z2root** (0.8.123 で full フレーバーも proot から z2root へ切替。foss は元から z2root 専用)。裏設定で full は **PRoot** にも、root 端末では **「実 chroot」** (`su` 経由 bind mount + `chroot`) にも切替できる (`executionEngine`)。
  - **裏設定の解放**: 設定 → アプリ情報のバージョン行を 7 タップ。トグル発火後 3 秒はバージョン行を**タップ不可**にして連打による即時再トグルを防ぐ (0.8.70。従来はタップを受けるが無視で不自然だった)
  - **chroot の解放**: root セルフテスト (`probeRootChroot`) の成功時のみ選択肢に加わる。このテストは **7 タップ解放の瞬間だけでなく、エンジン選択内の「chroot を有効化 (root を確認)」ボタンからも再実行できる** (0.8.106)。従来は解放時に 1 度だけ走り、su 許可ダイアログを拒否すると `rootChrootUnlocked` が false のまま二度と chroot を選べなくなっていた (再解放には再ロック→再解放の二重 7 タップが必要で気付けなかった)。false の間はこのボタンと案内文を表示し、何度でも再試行できる (成功で chroot 解放＋トースト、ボタン経由の失敗時のみ理由をトースト)
  - **失敗の切り分け** (0.8.107): `RootProbe.NoRoot` (su 無し/拒否) と `RootProbe.ChrootBlocked(detail)` (root は取れたが chroot 実行が SELinux/rootfs 等で失敗) を区別して表示する
  - ⚠️ **Magisk 等の root 管理アプリは一度「拒否」を記憶すると以後 su 許可ダイアログを再表示せず即拒否を返すため、アプリ内ボタンだけでは復帰できない** (アプリから他アプリの root 権限は変更不可)。この場合 Magisk 側で Z2Term の root を「許可」に戻す必要がある旨を NoRoot トースト/案内文で誘導する (0.8.108)
  - **foss には PRoot チップを出さない** (0.8.93): foss は proot prebuilt を同梱せず常に z2root 実走のため、選択肢は z2root と (root 解放時の) chroot のみ。従来は選べても z2root に倒れる見せかけだった
  - **z2root トレースログ** (開発者用・既定 OFF・`traceLogEnabled`): 同じ 7 タップ解放枠内のトグル。ON で z2root の全 syscall を `shared_home/z2root_trace.log` へ記録する＝障害調査用だが、ログが膨大で端末容量をすぐ圧迫するため UI に「普段は OFF のままにする」警告を添える (0.8.105。0.8.107 で警告文を「OFF のまま使用しない」という矛盾表現から非矛盾表現へ修正)。従来は `.z2root_trace_on` sentinel ファイルでしか切替できなかった (sentinel も後方互換で有効)

対応 ABI は **arm64-v8a のみ**。最低 Android 10 (API 29)、ターゲット API 35。

### 配布フレーバー

| フレーバー | applicationId | 用途 |
|---|---|---|
| `full` | `com.zerotoship.z2term` | 通常配布 (rootfs/proot 同梱・初回オフライン起動可) |
| `foss` | `com.zerotoship.z2term.foss` | F-Droid 適合。third-party prebuilt (proot/talloc) と Alpine rootfs を APK から外し、実行エンジンは同梱ソースからビルドする z2root、rootfs は起動時 DL (初回オフライン起動不可) |

`debug` ビルドは更に `.debug` サフィックスが付く。

---

## 2. 技術スタック

| 分類 | 採用 | 版/補足 |
|---|---|---|
| 言語 | Kotlin | 2.2.10 |
| ビルド | AGP | 9.1.1 (※ `kotlin-android` プラグイン併用不可) |
| UI | Jetpack Compose | BOM 2025.01.00 + Material3 |
| ネイティブ | C++ (forkpty JNI) | NDK 28、CMake 3.22.1、`c++_shared`、android-29 |
| 永続化 | DataStore Preferences | 1.1.2 (設定 / SSH プロファイル) |
| SSH クライアント | JSch (mwiede fork) | 0.2.26 (+ BouncyCastle 1.84 で ed25519/curve25519 を有効化) |
| 解凍 | org.tukaani:xz | 1.10 (DL distro の `.tar.xz`)。gzip は JDK 標準 |
| Linux 実行 | PRoot + libtalloc + libandroid-shmem | jniLibs に `.so` 同梱 (Termux ビルド由来) |
| 同梱 OS | Alpine Linux ARM minirootfs | full は `src/full/assets` に `.tgz` 同梱。foss は非同梱で公式 CDN から起動時 DL |

---

## 3. 全体アーキテクチャ

### 3.1 レイヤ構成

```text
+--- UI 層 (Compose) ----------------------------------------------------+
| MainActivity -> TerminalScreen                                         |
|   TopBar (ボタン並べ替え可) / TabBar / Renderer (Canvas)               |
|   TerminalInputView (AndroidView: ジェスチャ / IME / 選択)             |
|   ScrollIndicators                                                     |
|   TerminalKeyboard (独自) / JapaneseFlickKeyboard / SpecialKeyBar      |
|   SettingsSheet / SshProfilesSheet / SnippetsSheet / HostKeyDialog     |
+------------------------------------------------------------------------+
       | writeBytes (入力)              ^ emulator buffer (描画)
       v                                |
+--- ドメイン層 ---------------------------------------------------------+
| SessionManager --(保持)--> TerminalSession[*]                          |
|   TerminalSession: 状態機械 / readLoop / resize / 選択 / cwd / label   |
|     emulator: TerminalEmulator (VT 解釈・専用 1 スレッド)              |
|     channel : ProcessChannel = LocalPtyChannel | SshChannel            |
+------------------------------------------------------------------------+
       |                                        |
       v (ローカル)                             v (リモート)
+--- 実行基盤 (ローカル) ------------------+    +--- リモート (SSH) -----+
| ProotLauncher                            |    | SshChannel (JSch)      |
|   -> PtyProcess (forkpty)                |    |   shell + -L 転送      |
|   -> エンジン (z2root / proot / chroot)  |    +------------------------+
|   -> distro shell                        |
+------------------------------------------+
       | 展開 / 更新
       v
+--- distro / 永続 / 周辺 -----------------------------------------------+
| DistroBundle / Spec / Installer / Downloader (assets または DL)        |
| TerminalService (常駐) / DocsProvider (SAF)                            |
| AppSettings (DataStore)                                                |
+------------------------------------------------------------------------+
```

### 3.2 ライフサイクルと常駐設計

#### セッションは UI から独立して生きる

`TerminalSession` は **UI から独立**して生存する（`SessionManager` が保持）。Activity が破棄されても PTY と emulator の状態を維持する。

emulator の状態更新は**専用シングルスレッド**（`z2term-emu-*`）に集約し、Compose は `StateFlow` 経由で読む。

#### フォアグラウンド常駐と 2 種類のロック（`TerminalService`、0.8.143）

`TerminalService`（フォアグラウンドサービス）が常駐化を担い、バックグラウンドでも PTY を維持する。`AudioBridge`（GUI 音声）も同サービス系で扱う。

常駐中は 2 つのロックを握る。

| ロック | 目的 |
|---|---|
| `PARTIAL_WAKE_LOCK` | CPU を止めない |
| `WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`) | 画面消灯・アイドル中も Wi-Fi 無線を省電力 (PSM) に落とさない |

**`WifiLock` が必要な理由**: これが無いと端末上の sshd 等への LAN 着信が届かず、「立てたのに繋がらない / Wi-Fi を繋ぎ直すと直る」症状が出る。

常駐 OFF（detach）・停止・破棄で両ロックを解放する。

#### 常駐サーバー（`ServerDaemonService` ほか、0.8.147）

構成要素: `ServerDaemonService` / `ServerDaemonManager` / `ServerSupervisorScript` / `BootReceiver` / `ServerEntry`

任意のサーバー（sshd/http/smb 等）を**起動コマンド**として登録し（`ServerEntry`、DataStore に JSON 保存）、対話セッションとは独立して常駐させる汎用機構。サーバー本体はユーザーが distro に導入する前提で、アプリはコマンド実行と再起動・常駐管理だけを行う（特定サーバーは非ハードコード）。

**supervisor 方式を採る理由**
- proot/z2root では全プロセスが 1 本のエンジンプロセスの子になる
- そこで **supervisor スクリプト 1 本**をエンジン上で headless 起動し（`ProotLauncher.launch(command=/usr/local/bin/z2term-server-supervisor)`）、生かし続ける
- supervisor は各サーバーを **auto-restart ループ**付きで起動し、稼働状態を rootfs 内 `var/lib/z2term-servers/<id>.status` に書き出す（アプリが読んで一覧に反映）

**ジョブファイル方式（0.8.198・無停止リロード）**

スクリプトは**サーバー定義を焼き込まない固定文字列**で、サーバーは `var/lib/z2term-servers/` 配下の
ファイルで渡す。supervisor は監視ループ（2 秒周期）で `*.job` を拾い、まだ動かしていないものがあれば
run ループを起こす。

| ファイル | 書く側 | 意味 |
|---|---|---|
| `<id>.job` | アプリ | 実行するコマンド本文。**これが在ることがサーバーの定義**。消すと止まって片付く |
| `<id>.want` | アプリ | `1`=起動 / それ以外=停止（個別 ON/OFF） |
| `<id>.status` | supervisor | `state=` / `pid=` / `restarts=` / `last_exit=` / `cmd=` |
| `<id>.log` | supervisor | そのサーバーの標準出力・標準エラー |
| `<id>.exits` | supervisor | 終了の履歴（`<epoch> <rc>` を直近 20 行） |

- **追加・編集・削除のどれも supervisor 全体を止めずに反映される**（`ServerDaemonManager.syncEntries`）。
  追加は 2 秒以内に拾われ、`<id>.job` の中身が変わればそのサーバーだけ再起動し、`<id>.job` を消せば
  その run ループだけが片付いて抜ける。**従来は「登録時点の全エントリの run ループを焼き込んだ 1 本の sh」**
  だったため、起動後に追加したエントリには対応するループが無く、反映に全体再起動＝他サーバーの
  巻き添え停止が必要だった（この欠陥の解消が A3 の主目的）。
- `.job` は**中身が同じなら書き直さない**。書き直すと supervisor が「コマンドが変わった」と見なし、
  触っていないサーバーまで再起動してしまう。
- 起動時は `.status` / `.want` / `.claimed` / `.job` を掃除してから書き直す。とくに `.claimed`（run ループを
  起こした印）が残っていると、その id の run ループが二度と起こされず**黙って起動しない**状態になる。
  `.log` と `.exits` は残す（落ちた理由を後から見るためのもの）。

**個別 ON/OFF（0.8.163）**
- 各 run ループは `<id>.want` フラグ（`1`=起動）を監視する
- アプリが `ServerDaemonManager.setWant` で書き換えると、supervisor を再起動せず（＝他サーバーを止めず）にその 1 本だけを起動/停止する（約 1 秒で反映）
- フラグ初期値は各 `ServerEntry.enabled` を反映
- UI のサーバー行トグルは、稼働中なら `setWant` で即時反映、停止中は `enabled` の永続化のみ（次回起動時に反映）

**観測手段（0.8.198）**
- **サーバーごとのログ**: 標準出力・標準エラーを `<id>.log` に落とす。UI（サーバー行の ▤）で末尾 64KiB を
  表示し、サイズ表示と「ログを消す」を添える。ログが 1MiB を超えていたら**そのサーバーが動いていない
  瞬間にだけ**後半 512KiB へ切り詰める（実行中に差し替えると、走っているプロセスの fd が古い実体を
  掴んだままになり以後の出力がどこにも現れない）。
  「ローテーションしない」という既存方針（`LogWriter`）は**マクロが過去に遡って集計するログ**の話で、
  解析対象でないサーバー出力は青天井の方が実害が大きいため、こちらは上限を持たせる。
- **再起動回数と終了コード**: `restarts=` が増え続けていれば「起動しては落ちる」を繰り返していると分かる。
  UI は再起動回数と直近の `last_exit` を行に出す（0 回のときは出さない）。
- `wait` は**子 1 回につき 1 回だけ**呼ぶ。kill 後にもう一度呼ぶと「そんな子は居ない」で無関係な終了コードを
  拾い、`last_exit` が嘘になる（`ServerSupervisorScriptTest` が回数を固定している）。
- 生成スクリプトは `ServerSupervisorScriptTest` が **実際の `sh -n` に通して構文検証**する。アプリからは
  中身が見えないまま rootfs で実行されるので、壊れていても「サーバーが起動しない」としか現れず
  発覚が遅れるため（0.8.165 の事故、0.8.187 の `trimMargin` 事故）。

**常駐と停止**
- 前面維持（プロセス被 kill 防止）と LAN 到達性（WakeLock + WifiLock）は専用フォアグラウンドサービス `ServerDaemonService` が担う
- `BootReceiver`（`RECEIVE_BOOT_COMPLETED`）で端末起動直後にアプリを開かず自動常駐（設定「起動時に自動で常駐」ON かつ enabled サーバーがある時のみ）
- 停止は通知「サーバー停止」または設定から、**supervisor エンジンを kill = 全サーバー一括停止**（子プロセスがまとめて終了）
- 1024 未満ポートは非 root エンジンで bind 不可

**省電力モード（`serversLowPower`、0.8.148）**: ON のとき WakeLock/WifiLock を握らず Doze を許す（電池優先。画面消灯中の着信は遅延・取りこぼしうる。次回起動から反映）。

**常駐通知の見せ方（0.8.160）**
- `IMPORTANCE_MIN` チャンネル（`z2term_servers_v2`）で出し、**ステータスバーにアイコンを出さず通知シェード最下部へ畳む**（フォアグラウンドサービスは通知必須で完全非表示は不可のため、サーバー常駐のみのときの目立たなさを優先）
- 稼働数は supervisor の `.status` 書き込みラグがあるため、起動直後の 1 回きりでなく**数秒周期で通知を更新**する（`server-notif-refresh`）。0 のまま固まる不具合と、再起動/クラッシュ追従を両立

**自己背景化するサーバーの扱い（0.8.165）**
- supervisor は「コマンドが終了した＝落ちた」と見なして再起動するため、自分をバックグラウンドへ逃がして即 exit するサーバーは数秒周期で再起動され続ける
- `sshd` ラッパーはその再起動のたび既存 dropbear を kill するため、**LAN 公開しても接続が張れない / 数秒で切れる**症状になっていた
- 対策: supervisor が生成スクリプト冒頭で `Z2_SUPERVISED=1` を export し、`sshd` ラッパーはこれを見て `-D` 相当の**前景常駐**へ自動的に切り替える（= supervisor の子として生き続け、auto-restart も正しく効く）

#### GUI デスクトップ

**GUI デスクトップ**は別 Activity（`GuiActivity`）として起動し、distro 内 Xvnc に内蔵 RFB クライアントで接続する（[§4.12](#412-gui-デスクトップ-gui)）。実行エンジンは z2root 既定（0.8.123）、裏設定で PRoot、root 端末ではさらに chroot に切替可（[§4.3](#43-proot-実行-prootprootlauncherkt-prootsshdscriptkt)）。

### 3.3 Android 連携（検知入口とマクロ基盤）

Android 側の出来事をシェルから扱えるようにするための機能群。設計方針は全機能で共通:

> **接続点はアプリ・ロジックはシェル。**
> アプリは「検知して所定のファイルに流す」だけを行い、抽出・フィルタ・保存方針・配信は一切ハードコードしない。
> ユーザーがターミナル側（`tail` / 自作スクリプト / cron / 常駐サーバー）で自由に組む。
> 既定 OFF・完全ローカル・外部送信なし。

流し先は 2 本のログファイル。

| ファイル | 実体 | 内容 |
|---|---|---|
| `~/.z2term/notifications.jsonl` | `filesDir/shared_home/.z2term/notifications.jsonl` | 通知検知 |
| `~/.z2term/events.jsonl` | `filesDir/shared_home/.z2term/events.jsonl` | システムイベント・時刻トリガー・通知ボタン応答 |
| `~/.z2term/when/<id>.rule` | `filesDir/shared_home/.z2term/when/` | `z2-when` の自動化ルール（+ `<id>.log` 実行ログ）|
| `~/.z2term/widget/run.log` | `filesDir/shared_home/.z2term/widget/` | ホーム画面ウィジェットから実行したマクロの出力 |

#### 通知検知（`NotificationLogService`、0.8.149）

OS の「通知アクセス」許可を与えると Android が `NotificationListenerService` を自動でバインド・常駐させる（アプリを開かず・再起動後も動く＝通知検知デーモン）。設定 `notificationCaptureEnabled` が ON のとき、受け取った通知を**生のまま** 1 行 1 通知で追記する（JSON: ts / time / pkg / app / title / text / category / key）。`z2-notify` の逆向きの機能。ロック画面の「機密性の高い内容を隠す」設定はロック画面の**描画**だけを制御し、リスナーには影響しない。ただし **Android 15 以降は別レイヤーの制限がある**: 「高度な通知（Adaptive Notifications）」が ON だと Android System Intelligence が OTP を含む通知を**機微**と判定し、`RECEIVE_SENSITIVE_NOTIFICATIONS` を持たない "信頼されていない" リスナー（一般アプリはすべてこれ）には**本文を伏せ字に置換してから渡す**。この権限は system 署名か特定ロール向けで通常アプリには付与されないため、回避策は「高度な通知」を OFF にすること（`MACRO-GUIDE` §5-6 参照）。z2term 側の抽出をいくら広げても伏せ字は外せない。

**本文の抽出（`extractBody`、0.8.185）**: `title` は `EXTRA_TITLE`。`text` は標準の `EXTRA_BIG_TEXT` → `EXTRA_TEXT` だけだと **MessagingStyle の SMS / ワンタイムパスワード**（本文が `EXTRA_MESSAGES` に入り TEXT は空）を取りこぼすため、中身のある最初のフィールドを優先順に走査する: 展開本文（`EXTRA_BIG_TEXT`）→ 本文（`EXTRA_TEXT`）→ **MessagingStyle**（`EXTRA_MESSAGES` の各メッセージ本文を改行連結）→ **InboxStyle**（`EXTRA_TEXT_LINES`）→ 補助行（`EXTRA_SUB_TEXT` / `EXTRA_INFO_TEXT`）→ `tickerText`。どのフィールドにも文字が無い（完全カスタム表示のみの）通知は原理的に拾えない。抽出結果は既存の `text` に合流するのでプレースホルダやログ形式は無変更。

**出力フォーマット（`notificationLogFormat`、0.8.151）**
- `render()` がテンプレートを置換する
- 使えるもの: `{time}` `{app}` `{title}` `{text}` 等のプレースホルダ、`{text1}` `{title1}`（改行→空白の 1 行化）、`\n` `\t` エスケープ
- **空文字なら JSONL**（既定）
- プリセット（読みやすい / 1 行 / TSV / JSONL）から埋めて自由編集できる

**新しいものを先頭に（`notificationLogPrepend`）**: ON のとき末尾追記でなく**先頭追記**（新着が上）。ファイルは先頭に 1 行差し込む OS 機能が無いため、既存内容を読んで書き直す（`LogWriter`）。上限行なし = 全行保持（0.8.163）。

**重複排除（0.8.165）**
- Android は同じ通知を内容が変わらなくても何度も再 post する（進捗更新・常駐通知の再掲・グループ集約）ため、そのまま書くと同一行が大量に並ぶ
- `key` ごとの最終内容（title + text）を LRU 256 件で覚え、**同一なら書かない**
- `key` を作り直すアプリ向けに「同一アプリ・同一内容が 10 秒以内」も同一とみなす
- `onNotificationRemoved` で `key` を忘れるので、通知が消えた後の再掲は新しい 1 行として記録する

**保存の ON/OFF（`notificationLogEnabled`、既定 ON、0.8.165）**: OFF にすると、検知（リスナー常駐）は続けたまま `notifications.jsonl` へは一切書かない（検知だけ使いたい / 保存容量やプライバシーを優先したい場合）。

**実装メモ**: 設定フラグは Service が `AppSettings.flow` を購読してキャッシュし、通知ごとの DataStore アクセスを避ける。書込みは単一スレッド executor で直列化。

#### SMS 受信検知（`SmsLogReceiver`、0.8.186）

通知検知の姉妹機能。OS の `RECEIVE_SMS` 許可 + 設定 `smsCaptureEnabled` が ON のとき、受信 SMS を `~/.z2term/sms.jsonl` へ 1 通 1 行で追記する（JSON: ts / time / from / body。テンプレートは `{time}` `{ts}` `{from}` `{body}` `{body1}`）。マルチパート SMS は part の本文を連結して 1 通に戻す。

**なぜ通知検知と別に要るのか**: [通知検知（`NotificationLogService`、0.8.149）](#通知検知notificationlogservice08149) の項のとおり、Android 15+ は OTP を含む通知を機微判定し、一般アプリの通知リスナーには本文を伏せ字にして渡す。**SMS を直接読むこの経路はその伏せ字を通らず、ロック状態にも依存しない**ため、ワンタイムパスワードを確実に取れる（自動化アプリの「SMS 受信」トリガーと同じ）。

**なぜ manifest レシーバでよいか**: `SMS_RECEIVED` は暗黙ブロードキャスト制限の対象外。よってシステムイベント検知のような常駐 FG サービスは不要で、manifest 宣言のレシーバ（`android:permission="android.permission.BROADCAST_SMS"`）で**アプリ未起動・ロック中でも**起動できる。受信時は `goAsync()` で背景スレッドに逃がし、`AppSettings.flow.first()` で設定を読んで `LogWriter` で書く。

**非電話端末でも入れる（0.8.188）**: `RECEIVE_SMS` を宣言すると Android は暗黙に `android.hardware.telephony` を**必須**とみなし、タブレット/ChromeOS 等からインストール不可になる（lint `PermissionImpliesUnsupportedChromeOsHardware` がエラーで検出する）。z2term はターミナルであり SMS 検知は任意機能なので、`<uses-feature android:name="android.hardware.telephony" android:required="false" />` を明示して従来どおり入るようにする（その端末では SMS 検知が発火しないだけ）。

**サンプル**: `z2-macro install otp-sms.sh` で otp-clip.sh の SMS 版（`sms.jsonl` を見て 4〜8 桁を抽出）が入る。

#### システムイベント検知（`SystemEventService`、0.8.152）

通知検知の姉妹機能で「Android → シェル」向きのトリガーを増やす段。設定 `systemEventCaptureEnabled` が ON のとき、拾ったイベントを 1 行 1 イベントで追記する（JSON: ts / time / event と、該当時のみ level / ssid）。すべて権限不要。

**なぜフォアグラウンドサービスが要るか**: 画面 ON/OFF・ロック解除（USER_PRESENT）・電池残量変化・充電開始/停止・Wi-Fi 接続/切断などは Android 8+ の**暗黙ブロードキャスト制限**で manifest 宣言のレシーバでは配信されない。そこで opt-in のフォアグラウンドサービス `SystemEventService`（`foregroundServiceType=specialUse`）を常駐させ、その中で `registerReceiver` した**動的レシーバ**で拾う。

**`{event}` の値**

| 分類 | イベント |
|---|---|
| 画面・ロック | `screen_on` / `screen_off` / `unlocked` |
| 電源 | `power_connected` / `power_disconnected` / `battery_low` / `battery_okay` |
| 電池残量 | `battery_level`（残量が 10% 刻みの境界を跨いだとき、0.8.156 追加） |
| ネットワーク | `wifi_connected` / `wifi_disconnected` |
| 音声出力 | `headset_plugged` / `headset_unplugged` |
| 以下 7 種は 0.8.154 追加 | `airplane_on` / `airplane_off` / `ringer_normal` / `ringer_vibrate` / `ringer_silent` ほか |

**出力フォーマット**: `systemEventLogFormat` テンプレート（`{time}` `{ts}` `{event}` `{level}` `{ssid}`、`\n` `\t`、空 = JSONL）を `render()` が置換。「新しいものを先頭に」（`systemEventLogPrepend`）で先頭追記（`LogWriter`、0.8.163）。

**その他**
- Wi-Fi は接続/切断の状態変化のみ 1 回発火（同一状態の連続は抑制）
- Wi-Fi の SSID は位置情報権限が無いと空になる（v1 では権限要求せず best-effort）
- `BootReceiver` で端末起動直後にアプリを開かず自動常駐（設定 ON のとき）
- 稼働中は常駐通知を表示する

#### Wi-Fi 接続判定の修正（`SystemEventService.handleWifi`、0.8.168）

判定を `WifiManager.connectionInfo` から **`ConnectivityManager` + `NetworkCapabilities`** へ変更した。

**理由**: 前者は Android 12+ で**呼び出し元がフォアグラウンドでないと無効値（networkId = -1）**を返す。画面消灯中というまさにイベントを拾いたい場面で常に未接続に見え、`wifi_connected` を取りこぼしていた（`z2-state` 実装時に実機で再現し、0.8.167 で同じ理由により先に修正済み）。

SSID の取得だけは `WifiInfo` 経由のままで、取れなければ従来どおり空文字。

#### Bluetooth オーディオのトリガー（`SystemEventService.syncBtAudio`、0.8.170）

**背景**: 有線は `ACTION_HEADSET_PLUG` で拾えるが、**ワイヤレスイヤホンには相当するブロードキャストが無い**ため「イヤホンを繋いだら再生」という定番マクロが無線では書けなかった。

**実装**: `AudioManager.registerAudioDeviceCallback` で出力デバイスの増減を監視し、A2DP/SCO の有無が変化したときだけ `bt_audio_connected` / `bt_audio_disconnected` を発火する。

- **追加権限は不要**（`BLUETOOTH_CONNECT` が要るのはデバイス名の取得で、名前は出さない方針）
- 登録直後に既存デバイスぶんのコールバックが 1 度来る仕様のため、初回は現状の取り込みだけ行い発火しない（サービス起動を接続と誤検知しない）
- `z2-state` にも `bt_audio` と電池温度 `temp`（℃）を追加

#### ロック解除の失敗監視（`PasswordWatchAdmin`、0.8.171）

「パスワードを N 回間違えたら通知 / 位置記録 / 警報」という盗難対策マクロを組めるようにする**検知入口**。

**実装**: Android は通常アプリにロック解除失敗のコールバックを渡さないため、**端末管理者（Device Admin）として `watch-login` ポリシーだけを宣言**し、`DeviceAdminReceiver.onPasswordFailed` / `onPasswordSucceeded` を受けて events.jsonl へ書く。

| イベント | 内容 |
|---|---|
| `unlock_failed` | `{level}` = `DevicePolicyManager.currentFailedPasswordAttempts` = 連続失敗回数 |
| `unlock_succeeded` | — |

**安全側の設計**
- **破壊的ポリシー（force-lock / wipe-data / reset-password）は宣言も行使もしない**（`device_admin.xml` は `watch-login` のみ）＝有効化してもアプリは端末をロック / 初期化できない
- 設定 `unlockWatchEnabled`（既定 OFF）を検知の主スイッチとし、OFF のときは管理者が有効でも書かない
- **撮影・送信・警報などのアクションは一切ハードコードしない**。ユーザーが events.jsonl を見るマクロで組む

**制約**
- 管理者の有効化は `EXTRA_DEVICE_ADMIN` が ComponentName parcelable でシェルからは組めないため、**アプリ内の設定画面から `ACTION_ADD_DEVICE_ADMIN` を起動**する（有効化済みなら `ACTION_SECURITY_SETTINGS` で無効化へ導く）
- バックグラウンドからのカメラ撮影は Android 9+ の制約で別実装が要るため本版では扱わない（検知のみ）
- `EventEmitter.emit` に `level` 引数を追加

#### 時刻トリガー（`AlarmScheduler` / `AlarmReceiver` / `z2-alarm`、0.8.167）

指定時刻に events.jsonl へ `alarm` イベント（`{name}` 付き）を追記する。

**なぜ cron でなく AlarmManager か**
- 従来「毎朝 7 時に」は distro の cron 頼みだった
- cron の導入が distro ごとに要る
- **Doze 中は cron 自体が動かない**ため、実質的に画面点灯中しか効かなかった
- AlarmManager 経由なら OS がアプリを起こすので画面消灯中も発火する

**権限とのトレードオフ**
- `setExactAndAllowWhileIdle` は API31+ で `SCHEDULE_EXACT_ALARM`（ユーザー許可）が要る
- そこで権限が不要な `setAndAllowWhileIdle`（Doze 貫通・不正確）を採用した
- → **発火は数分ずれうる**ことを仕様として明示する（マクロ用途では許容）

**永続化と再起動からの復帰**
- 予約は `filesDir/alarms.json` に保存
- 再起動で消える AlarmManager の登録を `BootReceiver` で貼り直す
- `daily` は発火時に翌日へ再セット、`once` は発火後に削除
- 再起動中に時刻を過ぎた `daily` は次回へ送り、過ぎた `once` は捨てる（後追い発火をしない）

**その他**
- events.jsonl への書き込みは**設定「システムイベント検知」の ON/OFF に依存しない**（ユーザーが明示的に仕掛けたものなので、受動的イベントの取捨とは独立）
- `HH:MM` の「今日か明日か」判定は Calendar が要るので sh でなく Kotlin 側で行い、`in 5m` のような相対指定だけ sh が epoch へ直して渡す

#### 現在状態の取得（`z2-state`、0.8.167）

**背景**: events.jsonl は変化の瞬間しか流れないため、マクロが「今どうなっているか」で分岐する手段が `z2-battery` しか無かった。

**取れるもの（追加権限なしで 1 回にまとめて返す）**: 画面（`isInteractive`）/ ロック（`isKeyguardLocked`）/ Doze（`isDeviceIdleMode`）/ 充電と plug 種別と残量（sticky `ACTION_BATTERY_CHANGED`）/ Wi-Fi 接続 / SSID / マナーモード / 機内モード / 有線ヘッドセット / メディア音量

**出力の作り**
- 入れ子にせず**フラットな JSON**（jq 無しの sed/grep でも拾えるように）
- 引数にキーを渡すと生値だけを返す → `[ "$(z2-state charging)" = "true" ]` と書ける

**Wi-Fi 接続判定**: `WifiManager.connectionInfo` ではなく **`ConnectivityManager` + `NetworkCapabilities`** を使う。前者は API31+ で呼び出し元がフォアグラウンドでないと無効値（networkId=-1）を返し、マクロが多用するバックグラウンドからの問い合わせで常に未接続に見えるため（実機で確認）。SSID だけは `WifiInfo` 経由でしか取れず位置情報権限が要るので、取れないときは空文字。

#### アプリ自身のタブを操る（`z2-session`、0.8.199・A1）

**背景**: `Z2ApiBridge` の動詞はすべて「端末から Android を叩く」片道で、**アプリの内側（タブ）に触れる動詞が 1 つも無かった**。シェルやマクロから「作業用のタブをもう 1 枚開く」「別のタブへコマンドを置く」「今の画面を取り出す」ができない。

**サブコマンド**

| | 動作 |
|---|---|
| `list` | 1 行 1 タブの TSV（`番号 / id / 種別 / 印 / 名前`）。印は `*`=表示中 `!`=動作中 `?`=未起動 `-`=その他 |
| `new [名前]` | 端末タブを 1 枚開き、**起動まで済ませて** `番号\tid` を返す（続けて `send` する材料になる） |
| `send <先> <文字列>… [--enter]` | そのタブに文字を**入れる**。`--enter` を明示したときだけ実行する |
| `capture [先] [--all]` | そのタブの画面テキストを返す（`--all` で遡れる分も） |
| `close <先>` | そのタブを閉じる（最後の 1 枚は閉じない＝UI のダブルタップ削除と同じ約束） |

**安全側の既定**: `send` は**入れるだけで実行しない**（改行を付けない）。共有の受け取り（B1・[§5.1.2](#512-共有の受け取り-b10.8.197)）と同じ約束で、他のタブが勝手に走り出す状態を作らない。`Z2ApiScriptTest` が「ヘルパー側が `--enter` を足していない」ことを固定している。

**入れ先の指定**（`resolveSession`）は **番号（1 始まり）→ id → タブ名** の順。`list` の 1 列目をそのまま使えるのが実用上いちばん楽なので番号を第一に扱う。タブ名は完全一致を優先し、前方一致は**1 件に絞れるときだけ**採用する（複数に当たる指定で「たまたま先頭のタブ」に文字が入る事故を作らない）。

**実装**: 文字を入れる終端は B1 で切り出した `SessionManager.insertText`（bracketed paste 対応）。A1 側は動詞を足すだけで済んでいる。タブの生成・破棄・バッファ読み出しは `runOnMainSync` で Main に寄せる（描画側と同じ前提に乗せる）。

**`new` は起動まで済ませる**（0.8.203）。画面側の自動起動は「表示中のタブが IDLE なら起動」という条件なので、**アプリを開いていない間に作ったタブは開くまで起動せず**、続けて `send` しても PTY が無く何も起きなかった（実機で確認）。マクロから「タブを開いてコマンドを流す」を成立させるため、`new` の中で `startTerminal` まで呼ぶ。ただし初回ダウンロードが要る distro（foss の Alpine 等）は勝手に通信を始めず、画面を開いたときの確認に委ねる。
あわせて `list` の印に **`?`（未起動）** を足した。未起動のタブへ送っても何も起きないので、印が無いと「送ったのに動かない」理由が分からない。

**`new <名前>` の名前は固定する**（0.8.202）。`TerminalSession` に `labelPinned` を持たせ、true の間は**起動時の OS 名（`spec.id`）・`android-sh` フォールバック・SSH 接続・シェルが出すタイトル（OSC 0/2）のどれでも上書きしない**。これが無いと `z2-session new build` で付けた名前が直後の起動で OS 名に化け、名前を指定した意味が無くなる（実機で確認した）。

#### 自動化ハブ（`z2-when` / `WhenManager` / `WhenReceiver`、0.8.205・A6 stage 1）

**何ができるか**: Android 側の出来事（充電・電池・時刻）を**きっかけに Linux スクリプトを自動実行**する。これまで `z2-*` は「検知（events.jsonl へ書く）」と「実行（`z2-session` 等）」が別々で、両者を繋ぐのはユーザーが書く常駐スクリプトだった。`z2-when` は**トリガー宣言 → アプリが監視 → 発火時に実行**までを担い、スマホを「ポケットの中の自動化サーバー」にする。0→1 ではなく既存資産の“配線”。

**ルールはテキスト**: `~/.z2term/when/<id>.rule`（`filesDir/shared_home/.z2term/when/`）。`trigger=` / `run=` / `enabled=` の 3 行（`settings/WhenRule.kt`）。DataStore でなくプレーンファイルにするのは **git 同期・バックアップが効く**ため（常駐サーバーのジョブファイルと同じ思想）。CLI（`z2-when`）が直接読み書きし、変更後に `z2api when-reload` で時刻トリガーを貼り直させる。

**トリガー書式（stage 1 + cron）**:
- `charge:start` / `charge:stop` … 充電の開始 / 停止。**検知（`SystemEventService`）が ON のときだけ働く**（0.8.214 で受け口を変更。理由は下記「常駐を増やさない設計」）
- `battery:below=N` / `battery:above=N` … 残量が N% を下/上へ**跨いだ瞬間**（エッジ判定。直近残量を `.battlevel` に保存し、初回は基準設定のみ）。**検知が ON のときだけ働く**（0.8.214）
- `time:daily=HH:MM`（毎日）/ `time:at=HH:MM`（次の HH:MM に 1 回。発火後は `enabled=0` に自動で書き戻す）/ `time:every=Nm|Nh|Ns`（N ごと・最短 1 分）
- `time:cron='分 時 日 月 曜日'`（0.8.207・stage 2）… 5 フィールドの cron 式。`*` / `*/n` / `a` / `a-b` / `a-b/n` / `a,b,c` に対応。曜日は 0-7（0,7 が日曜）。**日と曜日がどちらも `*` でない場合はどちらか一致で発火**（標準 cron の仕様）。次回発火の算出は Android 非依存の `CronSchedule.nextAfter`（`CronScheduleTest` で具体例検証）。`daily`/`every` と同じ AlarmManager 経路に載り、発火のたびに次回を貼り直す。空白を含むのでシェルではクォート必須。
- `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<名前>`（0.8.208・stage 2）… Wi‑Fi の接続 / 切断 / 指定 SSID への接続。判定は Android 非依存の `WhenTriggerMatch.wifi`（`WhenTriggerMatchTest` で具体例検証。SSID は大小文字無視、位置情報権限が無く SSID が空なら `ssid=` は取りこぼす）。**電池の 10% 刻みと同じく検知（`SystemEventService`）が ON のときだけ働く**（Wi‑Fi の接続変化は暗黙ブロードキャスト制限の対象で、manifest レシーバでは拾えず動的レシーバ＝検知 FG サービスが要る）。発火時は SSID を `Z2_WHEN_SSID` で渡す（外部文字列なので単一引用符へ安全にエスケープ）。
- `sms:any` / `sms:from=<部分>` / `sms:contains=<部分>` / `sms:otp`（0.8.209・stage 2）… 着信 SMS。判定と OTP 抽出は Android 非依存の `WhenTriggerMatch.sms` / `.extractOtp`（`WhenTriggerMatchTest` で具体例検証。`from`/`contains` は部分一致・大小文字無視。OTP は**前後が数字でない 4〜8 桁**の先頭で、9 桁以上の電話番号/注文番号は拾わない）。既存の `SmsLogReceiver`（`RECEIVE_SMS` 許可で OS が着信ごとに起動＝アプリ未起動でも動く）に相乗りし、**生ログ設定 `smsCaptureEnabled` とは独立に評価する**（許可さえあれば動く）。SMS 本文は Android 15 の機微通知伏せ字（`RECEIVE_SENSITIVE_NOTIFICATIONS`）を通らない直読み経路なので伏せ字化されない（既存 `SmsLogReceiver` の解説参照）。発火時は `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`、`otp` のときは `Z2_WHEN_OTP` を渡す（いずれも外部入力なので単一引用符へ安全にエスケープ・`eval` させない安全境界）。
- `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far`（0.8.210・stage 2）… 端末を振った / 照度が N lux を跨いだ / 近接が near・far へ変化。**継続センサー監視は電池を食う**ので §10-1 の指針どおり **opt-in・検知（`SystemEventService`）が ON のときだけ**働き、しかも**該当ルールがあるセンサーだけ登録する**（`WhenManager.sensorKindsNeeded` → `SystemEventService.refreshSensors`。ルール増減や検知 ON で貼り直し、要求集合が空なら 1 つも登録しない＝電池ゼロ）。加速度は shake 検出に十分な `SENSOR_DELAY_UI`、照度/近接は on-change の `NORMAL`。shake 判定は `ShakeDetector`（合成加速度が **4.0g 超＋3 秒 debounce**・`ShakeDetectorTest`。当初の 2.7g／1 秒では**ポケットに入れて歩いているだけで連続発火**した＝2026-07-24 の実機検証で 3.5 時間に 255 回・発火間隔が debounce に張り付く形で判明したため 0.8.214 で引き上げ。下げるときは歩行で誤発火しないか実機確認が要る）、照度/近接は `WhenTriggerMatch.lightSatisfied`/`.proximitySatisfied` を**条件成立の立ち上がり（false→true）**で発火（rule 単位のプロセス内メモリ・初回は基準のみ。しきい値付近のばたつきは未吸収＝将来ヒステリシス可）。発火時は `Z2_WHEN_SENSOR`（`shake`/`light`/`proximity:near|far`）、light は `Z2_WHEN_LUX` も渡す。
- `file:new=<フォルダ>[,ext=<拡張子>]`（0.8.235）… そのフォルダに**新しいファイルが降ってきた**とき。見るのは `CLOSE_WRITE`（書き込み完了）と `MOVED_TO`（別名で書いてから rename する書き方）だけで、**`CREATE` は見ない** — コピー途中の空ファイルを掴んでしまうため。センサーと同じく**該当ルールがあるフォルダだけ**を監視し（`WhenManager.fileDirsNeeded` → `SystemEventService.refreshFileWatchers`）、1 件も無ければ 1 つも張らない。隠しファイル（`.pending-xxx` のような書きかけ）は常に除外する。同じパスは 5 秒間は二重に拾わない（`CLOSE_WRITE` と `MOVED_TO` が両方来ることがある）。`Z2_WHEN_FILE`（フルパス）と `Z2_WHEN_DIR` を渡す。⚠ `FileObserver` はプロセスが生きている間だけなので、**検知 ON が前提**（時刻や SMS のような常時性は無い）。
- `event:<名前>` / `event:<接頭辞>*` / `event:*`（0.8.226）… **`events.jsonl` に書かれる端末イベントを名前で拾う**。判定は `WhenTriggerMatch.event`（完全一致・末尾 `*` の前方一致・`*` で全件。大小文字と前後空白は無視＝手書きの打ち間違いで黙って動かないのを避ける）。

**なぜ足したか**: 検知はもう 15 種以上を拾って `events.jsonl` に書いているのに、`z2-when` から名前で指せるのは 6 kind だけだった。「イヤホンを挿したら再生」を書くには**ユーザーが自分で tail ループのマクロを常駐させる**しかなく、§10-1 の「常駐を増やさない」に一番反した状態を本人に作らせていた。**新しい常駐も新しい権限も増やさず**、既に鳴っている鈴を聞けるようにしただけの追加。

**フックは 2 か所**（「唯一の出口」は 1 つではない点に注意）:
- `SystemEventService.emit` … 受動的なイベント（画面・解錠・充電・電池・Wi‑Fi・ヘッドセット・BT 音声・機内・マナー）。**検知 ON が前提**。既存の単一ワーカースレッド（`writer`）の中で呼ぶので、ルール読み込みのファイル I/O をレシーバのスレッドへ持ち込まない。
- `EventEmitter.emit` … **ユーザーが自分で仕掛けたもの**（`alarm` / `notify_action` / `unlock_failed` / `unlock_succeeded`）。記録が検知 ON/OFF に依存しないので、こちらのトリガーも**検知 OFF で働く**。呼び元（レシーバ・AlarmManager の配信スレッド）を塞がないよう専用の単一スレッドへ逃がす。

**最小実行間隔 10 秒**（`WhenManager.EVENT_MIN_INTERVAL_MS`・rule 単位のプロセス内メモリ）: `screen_on`/`screen_off` のように**人の操作しだいで何度でも来る**イベントを名前で拾えるようにした以上、これが無いとルール 1 本で発火の嵐になる。トリガー別ではなく**ルール別**に効かせる（別々のルールは互いを抑制しない）。

**env の衝突を避けた**: イベント名は `Z2_WHEN_EVENT`、`alarm` 等の識別名は `Z2_WHEN_EVENT_NAME`、通知ボタンのラベルは `Z2_WHEN_ACTION`。**`Z2_WHEN_NAME` はルール id のまま**にしてある（既存ルールの意味を変えない）。

**名前の一覧は CLI が持つ**（`z2-when events`）。ヒアドキュメントなので崩れると黙って空になるため、`Z2ApiScriptTest.whenEventsListsEventNames` が実際に `sh` で走らせて中身が出ることまで見る。

**常駐を増やさない設計（§10-1 の指針）と、その一部撤回（0.8.214）**:
- 時刻は **AlarmManager**（`setAndAllowWhileIdle`＝Doze 貫通・`SCHEDULE_EXACT_ALARM` 不要。数分ずれることがある）。予約は再起動で消えるので `WhenReceiver`（`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`）と `Z2TermApplication.onCreate` の両方で `WhenManager.reload` が貼り直す。武装済み id は `.armed` に記録して、消えた/無効化されたルールの予約を確実に解除する。**時刻トリガーだけは常駐なしで動く**（`AlarmManager` からの明示 Intent は manifest レシーバに届くため）。
- ⚠ **充電・電池は 0.8.205 で「manifest レシーバ `WhenReceiver` で常駐なしに拾える」と設計したが、これは誤りだった。** `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` は Android 8+ の暗黙ブロードキャスト制限の**例外ではない**（公式の broadcast-exceptions 一覧に電源・電池系は 1 つも無い）。そのため manifest レシーバには届かず、**0.8.213 まで `charge:*` は一度も発火していなかった**（2026-07-24 の実機検証で判明。イベント自体は `events.jsonl` に `power_connected` として記録されているのにルールのログが 1 行も無い、という形で切り分けた）。時刻トリガーは明示 Intent なので動いており、e2e が通っていたため長く気付けなかった。
- **0.8.214 で受け口を `SystemEventService` の動的レシーバへ移した**（`handlePower` / `handleBatteryLowOkay`）。wifi / sms / sensor と同じく **`charge:*` / `battery:*` も「検知 ON」が前提**になる。§10-1 の「常駐を増やさず回す」はこの範囲で撤回。
- 電池しきい値は充電の抜き差し・`BATTERY_LOW`/`OKAY` に加えて、**残量が 1% 変わるたび**に `WhenManager.onBatteryChanged` を呼ぶ（`SystemEventService.handleBatteryLevel`）。0.8.213 までは 10% 刻みの境界でしか評価せず、`battery:above=40` が 40%→44% で発火しない・発火しても最大 10% 遅れて `Z2_WHEN_LEVEL` が実値とズレる、という「跨いだ瞬間」の説明と食い違う状態だった。`events.jsonl` の `battery_level` イベントは従来どおり 10% 刻み（ログを汚さないため）。エッジ判定なので二重に呼ばれても跨いだ瞬間しか発火せず、前回値と同じなら `.battlevel` の書き戻しもしない。

**実行**: 発火すると「そのとき選ばれている distro」で `sh -lc '<run>'` を **headless 起動**（`ProotLauncher.launch(command="/bin/sh", extraArgs=["-lc", …])`。`ServerDaemonManager` と同じ launch + drain パターン）。トリガー情報は環境変数 `Z2_WHEN_TRIGGER` / `Z2_WHEN_NAME` / `Z2_WHEN_LEVEL` と、トリガー固有の追加 env（wifi: `Z2_WHEN_SSID` / sms: `Z2_WHEN_SMS_FROM`・`Z2_WHEN_SMS_BODY`・`Z2_WHEN_OTP` / sensor: `Z2_WHEN_SENSOR`・`Z2_WHEN_LUX` / event: `Z2_WHEN_EVENT`・`Z2_WHEN_EVENT_NAME`・`Z2_WHEN_ACTION`）で渡す（外部入力をシェルへ文字列展開しない安全境界。値は単一引用符へ `'\''` エスケープ）。出力は `~/.z2term/when/<id>.log` へ追記（128KB を超えたら実行前に空にする）。root chroot モードでも `launchChroot` は追加引数を取らないため、ルール実行はエンジン経路に統一している。

**キルスイッチと発火の記録（0.8.227）**: トリガーが増えるほど**裏で勝手に走る回数**が増えるのに、暴走したときに全部止める 1 操作も、「さっき何が走ったか」を見る場所も無かった。`event:`（0.8.226）でその落差が実害になる前に足す。

- **一時停止は `~/.z2term/when/.paused` の有無**（DataStore ではなくファイル）。CLI（`z2-when pause` / `resume`）とアプリの画面が**同じ 1 つの真実**を見るため。ルールがファイルなのとも揃う。
- **判定は `runRule` の入口 1 か所**。トリガーを何種類増やしても止め忘れが起きない。**時刻トリガーの AlarmManager 予約は解除しない**（捨てると再開時の貼り直しが要り、`time:at` の「次の 1 回」も失われる）。発火はしても入口で弾く。
- **止めたことも記録する**（`status=paused`）。黙って動かないと「なぜ動かないのか」を探す手段が無くなる。
- **`~/.z2term/when/.fired` に 1 行 1 発火**（TSV: 時刻・rule id・トリガー・`run|paused|manual`、直近 50 件でローテート）。トリガーの値（SSID・SMS 本文）は書かない — 記録は残るものなので、外部由来の文字列を貯めない。
- **▶「いま試す」は一時停止中でも動く**（`runRule(manual=true)`）。キルスイッチは「勝手に走るもの」を止めるためのもので、人が押した実行まで禁じる設定ではない。トリガー固有の env は渡さない（作り物の値で「試したら動いたのに本番で動かない」を作らないため）。`Z2_WHEN_MANUAL=1` だけ入る。

**自動化タブ（`ui/settings/WhenRulesSheet`、0.8.227）**: 📜 に「自動化」タブを足し、一覧・ON/OFF・実行ログ・▶試す・削除・一時停止・直近の発火をまとめる。設定 › 常駐サーバー・自動化からも同じ中身を開ける（`ServersBody` と同じ 2 経路の作り）。部品（`ToggleRow` / `HintBox` / `IconCell` / `PillButton`）は `ServersSheet` から `internal` で共有し、見た目を 2 か所に書かない。

**ルールの新規作成と編集は画面に載せない**。正本は `~/.z2term/when/<id>.rule` のテキストで、ロジックはシェル側という設計（§3.3「接続点はアプリ・ロジックはシェル」）を崩さないため。GUI で全部書かせようとした瞬間に二重管理になる。画面は**見る・止める・試す**だけに留め、作るのは `z2-when` に任せる。

**CLI**（`z2-when`。`Z2ApiScript` が launch 毎に `/usr/local/bin` へ配置）: `<trigger> run <cmd>` で登録 / `list`（TSV。一時停止中は先頭に注記）/ `events`（`event:` に使える名前の一覧・0.8.226）/ `pause` / `resume` / `fired [n]`（0.8.227）/ `remove <id|all>`（`rm`）/ `on|off <id>` / `log <id>`。id は `w<epoch><pid>`（同一秒の衝突を避けるため 0.8.211 で乱数から pid へ変更。既存があれば連番を付す）。**stage2 は cron/wifi/sms/sensor まで実装済み**（0.8.207〜0.8.210）。以降の候補は `time:cron` の DST 跨ぎ精緻化や照度ヒステリシス等の作り込み。

**タブの状態表示（0.8.229）**: 見ていないタブに**動作中は小さな塗り四角、見ていない間に終わっていれば `✓`**を出す。判定 (`AppSession.isBusy`) は**閉じる確認のためにもう計算されていた**のに、タブからは何も見えず「切り替えて確かめる」往復が要っていた。持っている情報を出すだけの追加。

- **アクティブなタブには印を出さない**。見ているものに状態表示は要らないし、`✓` は「開いたら役目が終わる」印なので、開いた時点で消えるのが自然。
- **点滅させない**。暗所で目障りになるうえ、ターミナルの静かな見た目を壊す。4dp の四角と 9sp の `✓` だけ。
- ⚠ **判定できないタブに印を出してはいけない**。`hasForegroundChild` は判定手段が無いとき**安全側の `true`** を返す（マウスレポート漏れ対策としてはそれが正しい）。そのまま表示に使うと **SSH タブに永久に「動作中」が点く**。`ProcessChannel.supportsForegroundChild`（ローカル PTY だけ true）と `AppSession.busyKnown` を足し、表示側はこちらを見る。閉じる確認は「多めに確認する」で済むが、表示は嘘が出っぱなしになるという非対称がある。
- ポーリングは**タブバーで 1 回だけ**まとめて回す（1 秒 × タブ数の `tcgetpgrp` を避ける）。判定は `nextEndedIds` に切り出して `TabMarkTest` が固定する（「終わったのに出ない」「終わっていないのに `✓`」はどちらも気付きにくい）。

**初回ガイド「最初の 3 枚」（`ui/terminal/IntroCards`、0.8.231）**: 入れた直後の画面は黒地に `#` だけで、Linux を知らない人はそこで止まる。知っている人にも「ふつうの端末」に見えて、**Z2Term の差（= Android を触れること）に気付かないまま終わる**。最初の 90 秒で「うごいた」を 1 回配るための 3 枚。

- 中身は「通知を出す」「ライトを点ける」「PC からつなぐ」。**Android を触れること 2 枚 + PC から入れること 1 枚**で、どれも 1 行で結果が出るものだけにする（待たされるものを最初に置かない）。
- **勝手に実行しない**。タップで**入力行に入るだけ**で、⏎ は人が押す（共有受け取り B1 と同じ作法）。文言も「説明」ではなく**打つコマンドそのもの**を見せる — 読ませる画面ではなく、1 回動かしてもらう画面なので。
- **触った枚は消える**。3 枚とも消えるか ✕ を押したら [AppSettings.introDone] を立てて**二度と出さない**。
- ⚠ **32 件の提案で唯一「モードを増やすな」と正面衝突しうる案**だった。だから仕様を先に固めてある: **項目は 3 つまで・全画面ウィザードにしない・復活は設定の奥に 1 行**（メンテナンス）。4 枚目を足したくなったら、それは `z2help` の仕事。ここを緩めると 5 枚 6 枚と増える理由が毎回生まれ、二度と閉じられなくなる。

**複数行の貼り付けだけ、貼る前に見せる（0.8.232）**: 📋 は押した瞬間に入るので、コピー元がコードのかたまりだと**何行入ったのか分からないまま** ⏎ を押すことになる。**改行を含むときだけ** 44dp の帯を出し、行数と先頭 2 行を見せてから貼る。

- ⚠ **1 行の貼り付けには絶対に出さない**。ここを「安全のため」と広げた瞬間、**このアプリで一番よく押すボタンが 2 タップ**になって台無しになる。判定は `text.contains('\n')` の 1 行だけで、迷う余地を残さない。
- 帯の先頭は**行数**。この場面でいちばん効く情報は中身ではなく「何行入るか」。中身は最大 2 行だけ覗かせる（全文を見せる場所ではない）。
- 貼っても**実行はしない**（入力行に入るだけ・bracketed paste は従来どおり）。共有の受け取り（B1）と同じ作法。
- 置き方と寸法は `SearchBar` に揃える（端末領域の上端に出る帯が 2 種類あるので、別々の見た目にしない）。

**検索中だけ、スクロールバーが地図になる（0.8.233）**: 検索は「3 / 17」と**件数**は出すが、17 件が上に固まっているのか全体に散っているのかが分からず、∨ を連打することになっていた。ヒットの絶対行をスクロールバーの目盛りとして出す。

- **検索していないときは何も足さない**（`matchRows` が空なら 1 本も描かない）。スクロールバーの役目は「どこを見ているか」で、常時なにか出す場所ではない。
- **同じ画素行には 1 本しか描かない**（2dp 単位で間引く）。`grep` 的な検索で数百件ヒットしても帯にならず、「濃さ」で分布が読める程度に留まる。この間引きが無いと、ヒットが多いほど情報量がゼロに近づく。
- 目盛りは**タップでその位置へ飛ぶ**。当たり判定はつまみと同じ幅・高さ 12dp（細い線を狙わせない）。つまみと重なる範囲は**つまみが上**（後に置いてある）なので、掴む操作は今までどおり。

**この画面だけの明るさ（0.8.234）**: 暗い部屋で開くと、**いちばん眩しいのが黒地に緑文字の自分のアプリ**という状況になる。OS の明るさを下げに行くと戻すのを忘れるし、テーマを増やしても解決しない（配色ではなく明るさの問題）。🔅 の**ダブルタップ**でスライダー 1 本の帯を出す。

- 効くのは `WindowManager.LayoutParams.screenBrightness` = **この Window だけ**。ホームに戻れば OS の明るさに戻る。
- 既定は `BRIGHTNESS_OVERRIDE_NONE`（OS に任せる）で、**触ったときだけ効く**。だから設定項目もモードも増えない。単タップは今までどおり画面消灯ロック（📋 や ⌨ と同じ「単タップ=動作 / ダブルタップ=詳細」の作法）。
- **設定に保存しない**。「いまこの場が眩しい」ための一時的な調整で、次に開いたときまで持ち越すものではない。
- ⚠ 下限 10%。真っ暗にして「戻す」も押せなくなるのが最悪の結末なので、そこには落ちないようにする。「戻す」は帯の中に常に置く（出口が無いと怖くて触れない）。

#### 履歴パレット（`ui/snippets/ShellHistory`、0.8.221・B2）

**何ができるか**: 📜 ツールシートに「履歴」タブを足し、**端末で実行した過去コマンドを絞り込んでタップで入力行に入れる**。読み取り専用で、入力・描画の経路には一切触らない。

**独自の履歴を持たない**: 中身は**シェルの履歴ファイルそのもの**。アプリ側でコマンドを二重に記録すると、端末で `history -c` したのに残る等のズレが出る。

**履歴ファイルは 2 本ある**（この事実を外すと「履歴が出ない」になる）:
- `~/.bash_history` … `PROMPT_COMMAND='history -a'` で**コマンド終了後**に 1 行 1 コマンド。時刻を持たない。
- `~/.zsh_history` … `INC_APPEND_HISTORY` で**実行前**に載る。`: <epoch>:<duration>;<cmd>` の拡張形式で、行末 `\` で次行へ続く（複数行コマンド）。

両方を**新しい順**にマージし、同じコマンドは 1 つに畳む（時刻を持つ zsh 側を優先）。zsh は `SHARE_HISTORY` で全タブが 1 本を共有するので、**タブ別・ディストロ別の出し分けは実体を持たない**（フラット 1 本でよい）。ファイルは青天井に育つので**末尾 256KB だけ**読み、最大 300 件。絞り込みは大小文字を無視し、**空白区切りの語をすべて含む**ものを残す（`git log` で `git --no-pager log` も拾う）。

**タップしても実行はしない**（入力行に入るだけ）。B1（共有受け取り）と揃えた安全側の作法。解析部分は Android 非依存で `ShellHistoryTest`（10 ケース）。

> ⚠ **zsh の履歴ファイルは "metafy" されている。** zsh は 0x80 以上のバイトを `0x83` + `(元のバイト xor 0x20)` の 2 バイトにして書くので、**そのまま UTF-8 として読むと日本語が必ず化ける**（0.8.222 で実際に化けた）。0.8.223 で `ShellHistory.unmetafy` を通すようにした。実機の `.zsh_history` は生のままでは UTF-8 として不正で、この変換後に全体が正しく UTF-8 になることを確認済み（0x83 が 868 個）。`.bash_history` は素の UTF-8 なので変換しない。

**描くのは 50 件まで**: 履歴タブはシート全体の `verticalScroll` の中にあるので、**同じ向きの `LazyColumn` を入れ子にできない**。300 件を一度に組み立てるとタブを開くのが重くなるため、保持は 300 件・描画は先頭 50 件にして、残りは絞り込みで辿ってもらう（残件数を末尾に出す）。実機の `.zsh_history` は 3912 行 → 3380 コマンドあったので、この上限は実データで必要。

#### 常駐トンネル（`service/TunnelManager`、0.8.221・A2）

**何ができるか**: **SSH タブを閉じてもポート転送を生かし続ける**。あわせて `-R`（リモート → 端末）を追加した。

**なぜ要るか**: 現状の SSH タブ（`channel/SshChannel`）は「接続 → 転送を張る → 画面用の shell を開く」の順で、**転送と画面が 1 本のセッションにぶら下がっている**。だからタブを閉じると転送も消える。`-R` は**常駐しないと意味を成さない**（入りたい時に端末側でタブを開いている必要があるなら、そもそも外から入る必要が無い）。

**常駐を新規に作らない**: 画面を持たない JSch セッションを `TunnelManager` が持ち、`ServerDaemonService` の常駐枠（FGS 通知 / WakeLock / WifiLock / `BootReceiver` 自動起動）に**相乗り**する。常駐サーバーが 0 本でもトンネルだけで常駐してよい（`BootReceiver` もトンネルの有無を見る）。

**守っていること**（§6 の 3 条件）:
1. **明示 opt-in**: `SshProfile.residentTunnel` が true のものだけ。UI では転送を 1 つ以上作ったときにだけトグルが出る。`-R` を含むときは「接続先からこの端末へ入れる状態になる」と文言を変える。
2. **known_hosts 登録済みのホストだけ**: 常駐中はホスト鍵の確認ダイアログを出せないので、未知のホストは**張らずに理由を残す**（黙って信用しない）。先に SSH タブで 1 度繋いで承認してもらう。
3. **指数バックオフで再接続**: 5 秒から倍々にして 5 分で頭打ち（`TunnelManager.backoffMs`・`TunnelManagerTest` が境界とオーバーフローを押さえる）。回線が落ちている間に総当たりで撃たない。

**`-R` の向き**: `PortForward.reverse` で切り替える。`setPortForwardingR(bindAddress, remotePort, remoteHost, localPort)` = リモートの `bindAddress:remotePort` で待ち受け、端末から見た `remoteHost:localPort` へ繋ぐ。フィールド名は `-L` 時代のままなので、**意味が向きで入れ替わる**点に注意（`PortForward` の KDoc と `describe()` が正本）。

#### ライブ tail ウィジェット（`widget/TailWidgetProvider`、0.8.217・D2）

**何ができるか**: 選んだファイルの**末尾 N 行**をホーム画面に出す。「ホーム画面で `tail`」。マクロや `z2-when` が書いたログ・`events.jsonl`・セッション記録を、端末を開かずに眺めるための窓。§10-2 の D2。

**構成**:
- `widget/TailWidgetProvider`… 描画と ⟳ / ⚙ のタップ受け。本文タップでアプリを開く。
- `widget/TailConfigActivity`（`APPWIDGET_CONFIGURE`）… 見るファイルを決める（パス入力 / フォルダを辿る）。
- `widget/TailStore`… ウィジェットごとの「見るファイル（`~` からの相対パス）」を SharedPreferences に保存し、パス解決とディレクトリ走査も持つ。**行数は保存しない**。
- `widget/TailReader`… 末尾 N 行の切り出し。判断部分は Android 非依存で `TailReaderTest`（8 ケース）。

**全部読まない**: セッションログも常駐サーバーのログも**ローテーションしない**方針（青天井）なので、`RandomAccessFile` で末尾から `MAX_TAIL_BYTES`（16KB）だけ切り出し、その中で行に割る。途中のバイトから読むと**先頭行がマルチバイト文字の途中で切れる**ので、その 1 行は捨てる（`truncatedHead`。ただし 1 行しか無いときは捨てると何も出せないので残す）。

**ファイルの選び方（0.8.220 で作り直し）**: 当初は `~` 配下を機械的に走査して**更新の新しい順に 60 件**並べていたが、**数が多すぎて選べない**と実機で指摘された。いまは
- 上の欄に**パスを直接打つ**（`~/.z2term/events.jsonl` / `.z2term/events.jsonl` / `/root/.z2term/events.jsonl` のどれでも受ける）
- 下の一覧で**フォルダを 1 階層ずつ辿って選ぶ**（フォルダが先・名前順。拡張子で絞らない）

の 2 通り。`TailStore.resolve` は **`~` の外を canonicalPath 比較で弾く**（アプリの内部データを覗ける口にしない）。保存ボタンは実ファイルを指しているときだけ効き、そうでなければ理由をその場に出す（押しても何も起きない状態を作らない）。

**行数はウィジェットの高さから決める（0.8.220、0.8.223 で修正）**: 固定にしていた頃は**縦に伸ばすと下に隙間ができた**。ヘッダー・フッター・余白を引いた残りを 1 行の高さで割る（2〜30 行に丸める）。リサイズは `onAppWidgetOptionsChanged` で拾って描き直す。設定から行数を選ばせるのはやめた。

> ⚠ **`OPTION_APPWIDGET_MIN_HEIGHT` は「縦画面での高さ」ではない。** Android は `MIN_HEIGHT` に**横画面での高さ**、`MAX_HEIGHT` に**縦画面での高さ**を入れる（幅は逆で MIN が縦画面）。0.8.222 まで `MIN_HEIGHT` を見ていたため**実際より小さく見積もり、上に隙間が空いてログが入りきらなかった**（実機フィードバック）。0.8.223 で向きに応じて選ぶようにし、1 行の高さも 13dp 決め打ちから**実フォント metrics の実測**へ変えた（端末のフォントスケールにも追従する）。
>
> ⛔ **多めに見積もってはいけない。** `TextView` は中身が高さを超えると `gravity=bottom` が効かなくなり、**上詰めで描かれて末尾＝最新の行が切れる**。必ず切り捨てる。

**更新のきっかけ**（D1 と同じく**常駐は増やさない**）:
1. OS の定期更新（30 分。OS 側の下限）
2. ⟳ タップ
3. `TailWidgetProvider.refresh()` — **マクロや `z2-when` ルールの実行が終わったとき**（`HeadlessRun.launch(onExit = …)` から）。置かれていなければ何もしないので、使っていない人には一切のコストが無い。

**D1 と共有しているもの**: 土台（`widget_bg`）・40dp 角のアイコンボタン（`Z2WidgetIconButton`）・設定画面の部品（`widget/WidgetConfigUi.kt` の `ConfigSelectRow` / `ConfigButton`）。見た目を 2 か所に書くと必ずズレるので、D2 を足すときに D1 から切り出した。

**`configuration_optional` を付けない**（D1 とはここが違う）: **どのファイルを見るかは推測しようがない**ので、置いた直後に必ず設定画面を出す。それでも未設定の状態は起こり得る（設定を途中でやめた等）ので、その場合は「⚙ を押してファイルを選んでください」と本文に出す。

#### ツールバーの並び順の正規化（`ToolbarButtons.mergeOrder` / `.normalizeOrder`、0.8.213）

**不具合**: 一部のボタンがツールバーに **2 個ずつ描かれる**ことがあった（言語切替などで画面を作り直したときに表面化。全部ではなく一部だけ・再現が安定しない）。

**原因**: 保存値 `toolbarOrder` に**同じ id が 2 か所入った状態**が書かれ得た。並べ替え確定時の書き込みは「保存値の全 id の並び」の**表示スロットだけを今の表示順で埋め直す**方式だが、設定で「隠す/出す」を切り替えた直後は、`hidden` の変更が表示順（`order`）へ反映される前の**古い並び**（隠したはずの id を含む）が渡ることがある。それを表示スロットへそのまま流し込むと、隠した id が可視スロットにも書かれて二重になり、別の id が 1 つ落ちる。保存値は DataStore に残るため、**一度壊れると再起動しても直らない**。読む側の `mergeToolbarOrder` も重複をそのまま通していたので、`key(id)` が重複して並べ替えの状態まで壊れていた。

**修正**: 判断部分を `ui/terminal/ToolbarButtons` へ集約し、Android 非依存の純ロジックとして `ToolbarOrderTest` で押さえた。
- `mergeOrder(saved, present)`… 読む側。`saved` の重複を畳んでから present とマージする（壊れた保存値でも表示は必ず正しくなる）。
- `normalizeOrder(savedCsv, allIds, hiddenIds, shownOrder)`… 書く側。埋め込む表示順から隠し済み・未知の id を落とし、埋め終わりに**先勝ちで畳んで欠けた id を末尾に補う**。戻り値は `allIds` がちょうど 1 回ずつ現れることを保証する。
- 加えて、読み込み時に保存値の重複を検出したら**その場で正規化して書き戻す**（既に壊れている端末を自己修復させる。書き戻しで `savedOrder` が変わり同じ効果が 1 回だけ回って収束する）。

#### ホーム画面ウィジェット（`widget/StatusWidgetProvider`、0.8.212・D1）

**何ができるか**: ホーム画面に**いまの状態**（ssh の接続先 / 常駐サーバー / `z2-when` ルール / 電池残量）を出し、下段に並べたボタンで `~/.z2term/macros/*.sh` を**アプリを開かずバックグラウンド実行**する。`z2-when`（トリガー駆動）に対して、こちらは**人が押して起こす**入口。

**状態行は「稼働 / 登録」の分数（0.8.224）**: `常駐 1/3 · 自動化 2/5 · 電池 87%`。分子は**いま動いている**もの（常駐サーバーは `state=running` の本数、自動化は enabled なルール数）、分母は**アプリ側に登録してあるもの**（有効な `ServerEntry` の件数 = `ServerDaemonManager.start` が起動対象にする条件と同じ、`~/.z2term/when/*.rule` の総数）。分子だけだった 0.8.223 までは **`0` の理由（登録が無いのか・常駐を止めているのか）が読めず**、さらに「自動化」が**すぐ下に並ぶマクロボタンの数と混同された**（実機フィードバック 2026-07-25）。3 つの数はそれぞれ別のものを指す:

| 表示 | 数えるもの | 正本 |
|---|---|---|
| 常駐 | 常駐サーバー | 設定 › 常駐サーバー（`ServerEntry`） |
| 自動化 | `z2-when` ルール | `~/.z2term/when/*.rule` |
| 下段のボタン | マクロ | `~/.z2term/macros/*.sh` |

**構成**:
- `widget/StatusWidgetProvider`（`AppWidgetProvider`）… 描画と、ボタン/⟳ のタップ受け。
- `widget/WidgetConfigActivity`（`APPWIDGET_CONFIGURE`）… そのウィジェットに並べるマクロを選ぶ（最大 4）。API 31+ は `configuration_optional` を付けてあるので設定せずに置いてもよい（その場合はマクロディレクトリの先頭 4 件が並ぶ）。
- `widget/WidgetStore`… ウィジェットごとの選択と「直近に走らせたマクロ」を **SharedPreferences** に保存する。ウィジェットは**アプリのプロセスが生きていない状態**で描画・タップされるので、非同期前提の DataStore ではなく同期で読める SharedPreferences を使う。マクロ本体はユーザーのファイル（`~/.z2term/macros/*.sh`）が正本で、ウィジェットは参照するだけ。

**タップ実行の経路**: ボタンの `PendingIntent`（自分宛のブロードキャスト）→ `StatusWidgetProvider.onReceive` → `HeadlessRun.launch` → 完了を待たず再描画。**新しい常駐サービスは足さない**（電池要因を増やさない）。`HeadlessRun`（`service/HeadlessRun.kt`）は `z2-when` のルール実行から切り出した共通経路で、「選択中の distro で `sh -lc` を 1 回起動し、出力をログへ流し切る」処理を 1 本化している（呼び元が違っても肥大対策と pty の drain がズレない）。実行ログは `~/.z2term/widget/run.log`（端末から `tail` して確かめられる）。マクロ名は実在ファイルからしか来ないが、シェルへは単一引用符で渡して展開させない（`z2-when` と同じ安全境界）。

**更新のきっかけは 3 つ**:
1. OS の定期更新（`updatePeriodMillis` = 30 分。OS 側の下限なのでこれ以上は詰められない）
2. ウィジェットの ⟳ タップ（その場で読み直す）
3. アプリ側からの `StatusWidgetProvider.refresh()` — 常駐サーバーの稼働数が変わったとき（`ServerDaemonService` の通知更新ループが**数が変わった時だけ**叩く）と、`WhenManager.reload()`（ルールの増減・on/off）のとき

**描画の制約**: `RemoteViews` はランチャーのプロセスで描かれるため **Compose の動的パレット（`AppColors`）を読めない**。ウィジェットだけは ZTS ダークパレットの固定色を `res/values/colors.xml` の `widget_*` に持ち、選択中テーマには追従しない（意図的）。ボタンは View を動的に生やせないので**4 個ぶんをレイアウトに置いて余りを `GONE`** にする。読み取りはファイル I/O と設定 DataStore を含むので、描画は必ず `goAsync()` + 別スレッドで行う。

**PendingIntent の一意化**: requestCode（`appWidgetId * 8 + スロット`）と data（`z2term://widget/<id>/<slot>`）の両方をウィジェット×ボタンで一意にする。どちらかが同じだと PendingIntent が使い回され、別のボタンが前のマクロを走らせる。

**レイアウトの作り直し（0.8.216・実機フィードバック）**:
- **下に大きな空白が残っていた**のは、全段が `wrap_content` の縦積みで、ウィジェットの枠が中身より高いと余りがそのまま残る作りだったため。**マクロボタンの行に `layout_weight=1`**（高さ `0dp`）を持たせて余りを吸わせ、ボタン自身は `match_parent` で縦に伸ばす。**空白が消えると同時にボタンが大きくなって押しやすくなる**ので、「隙間が多い」と「ボタンが小さい」を 1 つの変更で直せる。
- **⟳ が押せなかった**のは 14sp の `TextView` にタップ領域が無かったため。`Z2WidgetIconButton`（40dp 角・`widget_button` 背景）にした。
- **⚙（設定）をヘッダーに追加**。ランチャーの「長押し → 設定」を辿らないと設定へ行けないのは使いにくい、という指摘。`PendingIntent.getActivity` で `WidgetConfigActivity` を `EXTRA_APPWIDGET_ID` 付きで直接開く（requestCode は `appWidgetId * 8 + 6`）。
- **既定サイズを 4x2 → 4x3 マス**（`minHeight` 110dp → 140dp）。ヘッダーが 40dp になったぶん、2 マスではボタンが潰れる。

**マクロボタンは 2 行（0.8.216）**: 1 行目が状態つきの名前、2 行目が**そのマクロを最後に開始した時刻**。
`WidgetStore` は開始時刻を**マクロごと**に持つ（`run_at_<ファイル名>`）。0.8.215 までは全体で 1 件しか覚えておらず、
**複数走らせるとどれがいつのものか分からなかった**。1 行目の印は 3 状態:
- `■ 名前`（アクセント色）… 実行中。タップで停止。
- `✓ 名前` … **今日**走って終わっている。**すぐ終わるマクロで `■` が一瞬で消えるのが「勝手に停止された」ように見える**という指摘への対応で、正常終了だと分かるようにした。
- `名前` … 今日はまだ走っていない（時刻は `––:––`）。

フッターは「直近に**終わった**マクロと時刻」に変えた（開始時刻はボタン側に出るようになったので、終了を伝える役に回す）。

**`✓` は当日限り（0.8.224）**: 0.8.223 までの `✓` は「一度でも実行した」印で、`run_at_<ファイル名>` が消えないため**永久に付いたままだった**。`WidgetConfigActivity.clear` はマクロ選択しか消さないので**ウィジェットを置き直しても消えず**、アプリのデータ削除しか手が無かった（実機フィードバック 2026-07-25）。ボタンの時刻は `HH:mm` しか出せない以上、日をまたいだ記録は「その 07:12 がいつのものか」読めないので、**日付が変われば自動で無印へ戻す**（`WidgetStore.isSameDay` / `runStartAtToday`・Android 非依存で `WidgetStoreTest` が押さえる）。フッターの「最後に終わった」も同じ扱い。加えて設定画面に**「実行履歴をリセット」**を置き、いま消したいときの出口にした（`WidgetStore.clearRunHistory`）。リセットは「保存」を待たず**その場で効く** — 見えている印を消すのが目的なので、保存まで何も起きないと押せていないように見える。

**実行中はもう一度タップで停止（0.8.215）**: `RemoteViews` に長押しは無いので、**モードを増やさず同じボタンのトグル**にした。実行中はラベルが `■ 名前` になりアクセント色（`widget_accent`）で、タップすると `ACTION_STOP_MACRO` → `HeadlessRun.stop`。実行中かどうかは `HeadlessRun` が持つ**プロセス内のマップ**（`name` → `PtyProcess`）で判定する。アプリのプロセスが死ねば起動した子プロセスも道連れなので、**マップが空から始まるのは正しい**（「動いていないのに動いている表示」にならない）。停止は `PtyProcess.close`（SIGHUP → 最大 1 秒待って SIGKILL）なのでブロードキャスト受信スレッドから直接呼ばず、必ず別スレッドへ逃がす。終了時は `HeadlessRun.launch(onExit = …)` から再描画して `■` を戻す。

**設定画面（0.8.215 で 2 件修正）**:
- **インセット**: `enableEdgeToEdge()` ＋ ルートに `windowInsetsPadding(WindowInsets.systemBars)`。targetSdk 35（Android 15）は edge-to-edge が強制なので、これが無いと**ステータスバーと 3 ボタンナビの下に潜り込んで見えず、操作もできない**（実機で発生）。新しい `Activity` を足すときは既存画面と同じこの書き方に必ず揃えること。
- **マクロの説明**: ファイル名だけ並べても何のマクロか分からないので、`.sh` を落とした名前の下に**スクリプト先頭のコメント**を 1 行説明として出す（`WidgetStore.describe`・Android 非依存で `WidgetStoreTest` が押さえる）。シェバンと空行は飛ばし、`# ~/.z2term/macros/<自分>.sh` のような自己言及行も飛ばす。`# <ファイル名> — <説明>` の形なら頭のファイル名を落とす（区切りが `—` / `–` / ` - ` / `:` のとき、かつ**その手前が自分のファイル名と一致するときだけ**。`z2term: …` のような接頭辞は説明の一部として残す）。60 文字で切り詰め。

#### 通知ボタンによる応答（`NotifyActionReceiver` / `z2-notify -b`、0.8.169）

**背景**: `z2-*` は通知を出すだけの一方通行で、ユーザーの返事を受け取る手段が無かった。

**実装**: `-b <ラベル>` で通知にボタン（Android の表示上限に合わせて最大 3 つ）を付け、押されたら events.jsonl へ `notify_action` を書く（`{name}` = 通知に付けた識別名、`{action}` = 押されたラベル）。これで「マクロが問いかける → ユーザーが答える → 続きを実行する」という**対話型マクロ**が組める。

- PendingIntent は `通知 ID × ボタン数 + index` を requestCode にして一意化する（同じ requestCode だと extras が使い回され、別のボタンを押しても前の値が飛ぶ）
- 押した通知は返事が済んだ状態なので自動で閉じる
- events.jsonl への書き込みは時刻トリガーと共通の `EventEmitter` に集約した（`render` に `{action}` を追加）

#### マクロのサンプル同梱（`Z2MacroScript` / `z2-macro`、0.8.167）

**背景**: マクロは書き方より**最初の 1 本を白紙から書くこと**が壁だった。

**実装**: 動くサンプル 5 本（イベント入門 / 電池アラート / 時刻トリガー / 通知内 OTP 自動コピー / SMS の OTP 自動コピー）を rootfs の `/usr/local/share/z2term/macros/` に配置し、`z2-macro install <名前|all>` で `~/.z2term/macros/` へ展開する。

- install は**既存ファイルを上書きしない**（`-f` のときだけ上書き）ので、ユーザーが編集したものが launch 毎の再配置で消えない
- `list` は各スクリプトの 2 行目コメントを説明として並べる。`show` / `run` / `dir` も持つ
- サンプル本文のコメントはアプリ言語（ja/en）に追従する

**`trimMargin` マージン漏れで `z2-macro` が起動不能だった（0.8.187 で修正）**: usage 部で raw string 側が既に `|` を出している行に対し `joinToString` の各要素にも `|` を付けていたため、**1 行目だけ `||`** になった。`trimMargin()` は行頭の `|` を **1 個だけ**剥がすので `|  echo 'usage: ...' >&2` が残り、シェルは関数定義もパース時に読むため **どのサブコマンドでも `syntax error near unexpected token '|'` で起動不能**だった（`z2-macro install` が一度も成功しない = サンプルを導入できない）。修正は行の区切り側で `|` を供給する（`joinToString("\n|")`）形に変更。回帰テスト `GeneratedScriptMarginTest` で「生成物のどの行も `|` で始まらない」を全サンプル・両言語について固定した（行頭 `|` は POSIX sh では常に構文エラーなので健全性判定にそのまま使える）。

#### 検知ログの上限撤廃と肥大の注意表示（`LogWriter`、0.8.171）

**方針変更**: events.jsonl / notifications.jsonl は **1 本に全履歴を追記し続ける**（サイズ上限での分割・退避をしない）。

0.8.168 では 1 MiB で `<名前>.1` へ退避して 1 世代残していたが、マクロが「過去に遡って集計する」用途では途中でファイルが切り替わると解析が面倒になるため、上限を撤廃した（掃除はユーザーがターミナル側で `: > ~/.z2term/events.jsonl` 等）。

**コスト上の注意**: 「新しいものを先頭に」モードは 1 件ごとにファイル全体を読み書きするため、肥大するとコストが線形に増える（大量常用は既定の末尾追記を推奨）。

**注意表示（`LogSizeWarning` / `LOG_SIZE_WARN_BYTES`、0.8.172）**
- **「新しいものを先頭に」が ON かつ当該ログが 10 MiB 超**のときだけ、設定画面のトグル直下に現在サイズ（`12.3 MB` 形式）と対処（OFF にする / ターミナルで `: > <パス>`）を出す
- 通知ログ / システムイベントログの**両方**に付き、各セクションは自分のログのサイズと自分のトグルだけを見る（警告文中のパスもそのセクションのもの）
- 末尾追記はサイズの影響を受けないので出さない
- サイズは設定シートを開いた時点で `remember` して 1 回 stat するだけ（毎コンポーズでは触らない）

**なぜ 10 MiB か**: 先頭追記が `readText` で全文を UTF-16 の String に展開し、さらに連結でもう 1 本作るため**瞬間的にファイルサイズの 4〜6 倍のヒープ**を使う。端末のヒープ上限（128〜512 MB）次第では数十 MB で `OutOfMemoryError` に達しうるため、その手前で気付ける値として選んだ。

**見た目の改善（0.8.173）**: 初版（0.8.172）は周囲の補助テキストと同じ 10〜11sp・secondary 色で「注意に見えない」ため、**警告色 1px 枠 + 淡い警告色背景のボックス**に入れ、見出し 14sp 太字・本文 12sp 本文色に拡大した。

---

## 4. レイヤ別 詳細設計

### 4.1 ネイティブ (`cpp/pty_jni.cpp`, `libz2term`)

- `forkpty(3)` で擬似端末 (PTY) を作り、子プロセスで `execve()`。Bionic libc に API 21+ で存在。
- JNI 公開: `nativeCreate(command, args, env, cwd, rows, cols) → (fd<<32 | pid)`、`nativeResize(fd, rows, cols)` (`TIOCSWINSZ`)、シグナル送出、`waitpid`。
- 子では `setsid` / `TIOCSCTTY` で制御端末を確立。

### 4.2 PTY ラッパー (`pty/PtyProcess.kt`)

- `nativeCreate` の戻り値から `FileDescriptor` を生成し、`reader`(FileInputStream)/`writer`(FileOutputStream) を公開。
- `resize(rows,cols)` / `sendSignal` / `close` / `waitFor`。
- **JNI シンボル注意**: `@JvmStatic external` を companion に置くと外側クラス名で export される (CMake/JNI 命名規約)。

### 4.3 PRoot 実行 (`proot/ProotLauncher.kt`, `proot/SshdScript.kt`)

- バイナリは `nativeLibraryDir/libproot.so` (+ `libproot_loader.so`)。`libtalloc.so` を SONAME 通り `libtalloc.so.2` に展開し `LD_LIBRARY_PATH` に通す。新しい Termux proot は `libandroid-shmem.so`(SysV 共有メモリ)にもリンクされるため、これも同じ `proot-libs` に展開して通す(不在だと `library "libandroid-shmem.so" not found` で proot が即落ちする)。
- `launch(distroId, command, rows, cols, fallbackShell)` が proot 引数を組み立てて `PtyProcess.create`:
  - `--kill-on-exit -0 --link2symlink -r <rootfs> -b /dev -b /proc -b /sys -b <rootfs>/dev/shm:/dev/shm -b <shared_home>:/root`
  - **外部ストレージ bind**: `/storage/emulated/0:/sdcard`、`getExternalFilesDir:/storage/app`
  - `-w /root`、env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 PATH=… TMPDIR=/tmp` + 履歴系 env。
- **共有ホーム**: `filesDir/shared_home` を全 distro 共通で `/root` にバインド (← 端末の `~` の実体)。
- **POSIX 共有メモリ `/dev/shm` の提供 (0.8.177)**: Android の `/dev` には `shm` が無く、`-b /dev` でホストの `/dev` を見せるだけでは `/dev/shm` が存在しない。ゲスト側から `mkdir /dev/shm` しても実体はホストの `/dev` なので SELinux に阻まれて `EACCES` になり、自力では作れない。この状態だと `shm_open()` が **ENOENT** で失敗し、**共有メモリを前提に組まれた GUI アプリが起動時に自ら異常終了する**。典型は Gecko 系で、`MOZ_RELEASE_ASSERT(mHandle.IsValid() && mMapping.IsValid())` に到達して `MOZ_CRASH()` で落ちるため、端末には理由の出ない `segmentation fault` だけが残る (`--version` や `-h` は共有メモリを使わないので成功してしまい、ローダやライブラリの問題と誤診しやすい)。対策として **`<rootfs>/dev/shm` を実体に持つ bind を `-b /dev` の後ろに重ねる**。z2root の bind 解決は最長一致なので (`translate_abs`)、`/dev/shm` (8 文字) が `/dev` (4 文字) に優先して選ばれ、`/dev` 配下の他のデバイスノードはホストのまま維持される。proot も bind は純粋なパス変換なので同じ引数で効く。実体を rootfs 配下の `dev/shm` に置いたのは、Kitty graphics の shm 転送 (`KittyHostTransferSource`) が shm 名を `<rootfs>/dev/shm/<name>` に rebase する既存仕様と**同じ場所を指させる**ため (別名にすると両者が別の場所を見て転送が空振りする)。chroot 経路 (裏機能・要 root) は実マウントなので、`$RFS/dev/shm` に tmpfs を直接被せ、umount 掃除リストにも `dev` より**前**に入れる (入れ子なので先に剥がす必要がある)。
- **`/etc/machine-id` の生成 (`ensureMachineId`, 0.8.177)**: ディストロの rootfs には**空の** `/etc/machine-id` が入っていることがあり (0 バイト・`0400`)、その状態では dbus が "Invalid machine ID" でセッションバスを起動できない。D-Bus を要求する GUI アプリ (アクセシビリティバス経由のものを含む) が警告や機能欠落を起こすため、起動毎に冪等で確認し、**空またはファイルが無いときだけ** systemd と同じ形式 (ハイフン無し 32 桁 hex) を書き込む。中身があるときは触らない (端末を跨いで ID が変わらないようにする)。書き込み前に `setWritable` で権限を戻す (rootfs 側が `0400` で置かれていることがあるため)。
- **端末タブ経路の `XDG_RUNTIME_DIR` (0.8.177)**: GUI タブ配下は `z2gui` が export していたが、**端末タブから直接 GUI アプリを起動する経路には無かった**。未設定だと Qt/GTK が警告を出し、D-Bus の socket 置き場も決まらない。`display != null && exportDisplay`(端末から `:N` へ相乗り) では GUI と同じ `/tmp/z2gui-xdg-<N>` を、`display == null`(端末/SSH 単独) では `/tmp/z2-xdg` を渡す。**z2gui 経由 (`exportDisplay=false`) では敢えて渡さない**: `start_audio` 等が `${XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-$DISPLAY_NUM}` と**継承値を優先**するため、ここで一律に入れると全ディスプレイが同じディレクトリに集約され、`:N` 毎の PulseAudio 分離が壊れる。
- **HOME のディストリ別隔離** (0.8.72、`.claude/downloads` を 0.8.73 で追加、z2root の最長一致 bind を 0.8.75 で修正):
  `/root` 全体は共有のままにしつつ、**arch 依存物が入る一部サブディレクトリだけをディストリ別オーバーレイで上書き bind** する。
  - 対象 (`isolatedHomeSubdirs`): `.local` `.cache` `.npm` `.npm-global` `.nvm` `.cargo` `.rustup` `.config` `.claude/downloads`
  - `filesDir/home_overlay/<distroId>/<sub>` を `/root/<sub>` に重ね bind し、`shared_home/<sub>` はマウントポイントとして用意する (ネストパス `.claude/downloads` も `mkdir -p` で親ごと作成)
  - proot は `-b <shared_home>:/root` の後に各サブディレクトリ bind を重ね、chroot も `mount -o bind <SHOME> $RFS/root` の後に同様に重ねる (掃除時は `root` より先に lazy umount)
  - **狙い**: musl(Alpine) ↔ glibc(Arch/Ubuntu/Kali) で HOME 内の native (npm global で入れた node 製 CLI の本体・`~/.cache` のコンパイル済みアドオン・nvm の node 本体等) が混ざって壊れる問題を、ディストリ別に分けて根治する
  - `.claude` 直下の認証 (`.credentials.json`)・設定・projects、書類・git リポジトリ等の通常ファイルは `/root` 直下のまま共有される
  - **移行注意**: 既存 `shared_home/<sub>` の中身はオーバーレイに覆われて各ディストリからは見えなくなる (消えてはおらず影に入るだけ)。各ディストリで該当 CLI を一度入れ直すと native 本体が各オーバーレイに収まる

  **項目4 (再発) の真因**: 旧版は `.claude/downloads` が共有だったため、Alpine(musl) と Arch(glibc) が同じ native 本体を上書き合い `Not a valid dynamic program` で双方起動不可になっていた。0.8.73 でオーバーレイ bind を足したが、**z2root エンジンでは隔離が効かず再発した** (2026-06-11 実機検証)。真因は z2root のパス変換 (`z2root.c` の `translate_abs`/`host_to_guest`) が **bind を登録順の最初一致で解決**しており、先に登録される親 bind `/root` が子 bind `/root/.claude/downloads` を覆い隠していたこと。proot は最長一致なので効いていた engine 差。**0.8.75 で両変換関数を最長一致 (最も具体的 = guest_len 最長の bind 優先) に修正**し、z2root でも `.claude/downloads` だけがオーバーレイへ、`.claude/.credentials.json` 等は共有 HOME へ正しく解決されるようにした。
- `resolveShell`: 指定シェルが rootfs に無ければ `defaultShell → /bin/sh` にフォールバック (usrmerge 考慮)。
- **設定「ログインシェル」を全入口へ適用 (0.8.165)**: 従来は端末タブ (エンジンが直接 exec する `command`) にしか効かず、**SSH ログインと GUI 内ターミナルは distro 既定 (bash 等) のまま**だった (dropbear は `/etc/passwd` の shell を、GUI 内ターミナルは `$SHELL` を起動するため)。`launch()`/`launchChroot()` に `loginShell` を渡し、(1) `ensureRootLoginShell` が rootfs の `/etc/passwd` の root 行 7 番目のフィールドを設定値へ書き換える (= `chsh` 相当。`/etc/shells` にも追記)、(2) env `SHELL` / `Z2_LOGIN_SHELL` に流す、(3) `z2gui` の SHELL 張り直しが `Z2_LOGIN_SHELL` を最優先候補にする、の 3 点で端末タブ・SSH・GUI が同じシェルになる。rootfs に無いシェル (Ubuntu 素の zsh 等) を指定した場合は従来どおりフォールバックし、passwd は書き換えない。
- `isDistroReady`: `bin/busybox|bin/bash` 等の実体 + `.z2term-version` マーカー (同梱 distro のみ `ROOTFS_VERSION` 比較)。
- 起動毎に冪等で注入: `ensureShellHistoryConfig` (履歴 rc)、`ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear ラッパー)、`ensureOsc7CwdConfig` (cwd 復元用 OSC7 フック)、`ensureZ2ApiScripts` (`z2-*` ブリッジ)、`ensureZ2AdbScript` (`/usr/local/bin/z2adb`)、`ensureZ2HelpScript` (`/usr/local/bin/z2help` + エイリアス `/usr/local/bin/z2term`)、`ensureZ2ScanScript` (`/usr/local/bin/z2scan`)、GUI/z2run スクリプト、`ensureVersionScript` (`/usr/local/bin/z2version`)。
- **`z2version` コマンド (0.8.70)**: 端末から `z2version` でアプリ本体の版数 (`versionName`/`versionCode`/flavor/package/実行エンジン/rootfs 世代) を確認できる。launch 毎に書き直すので「今走っているアプリ」の版数が出る＝APK とゲストの版数不一致を即切り分け。`z2version --short` は版数 1 行のみ。proot/z2root/chroot の全起動経路に配置。
- **`z2adb` コマンド (0.8.88・セルフ adb)**: PC を繋がず、端末が**自分自身**の adb デーモン (Android のワイヤレスデバッグ) に `localhost` で繋ぐヘルパー (root も USB も不要)。前提は Android 11+ の開発者オプション → ワイヤレスデバッグ ON。実装は [`Z2AdbScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2AdbScript.kt)。proot/z2root/chroot の全起動経路に配置。

  | サブコマンド | 動作 |
  |---|---|
  | `z2adb setup` | distro に adb クライアントを導入 (apk: `android-tools` / apt: `adb` / pacman: `android-tools` を `detect_pm` で自動判定) |
  | `z2adb pair <ポート> [6桁コード]` | ペアリング |
  | `z2adb connect <ポート>` | 接続 |
  | `z2adb shell` / `pm` / `logcat` 等 | 素の adb へ passthrough |

  - 宛先はポートのみなら `Z2ADB_HOST` (既定 `127.0.0.1`) を補い、`host:port` ならそのまま使う
  - `setup`/`pair`/`connect`/`status`/`help` 以外は素の adb へ委譲し、`pair`/`connect`/`status` は adb 未導入時に一度だけ自動導入を試みる
  - PRoot/z2root は TCP を素通しする (dropbear と同経路) ため localhost に到達する

  **adb サーバの先行起動 (0.8.89)**: adb は通常クライアント実行時に daemon が無ければ**自身を `execl(自パス)` で再起動**するが、z2root は `/proc/self/exe` を APK 内 `libz2root.so` と返すため ENOENT で失敗する (adb 全般の問題。0.8.111 で z2root 側の `/proc/self/exe` をゲスト視点へ書き換えて根治)。そこで `ensure_adb` が `start_server` を呼び、**自己 exec を伴わない `adb nodaemon server` を background で先行起動**する。起動前に `/proc/net/tcp{,6}` を見て対象ポート (`ADB_SERVER_SOCKET` のポート・既定 `5037`) が既に LISTEN (`0A`) なら立てない**冪等ガード** (`server_up`) を持ち、二重 bind による `Address already in use` の abort を避ける。以降のクライアントは fork せず既存サーバに繋がる。
- **`z2help` / `z2term` コマンド (0.8.90)**: ディストロに注入する独自 `z2*` コマンドの早見表を端末から引けるヘルプ。引数なしで全 `z2*` コマンドの分類済み一覧 (版数・情報／スマホ機能／GUI／つなぐ／ヘルプ) ＋一行説明を表示し、先頭にアプリ版数 (`z2version --short`) を併記する。本体は全て静的テキストで、quote 付き heredoc (`<<'Z2HELP_EOF'`) に入れるためシェル展開されない (外部入力なし)。`z2term` は当面 `z2help` の薄いエイリアス (`exec /usr/local/bin/z2help "$@"`) として同梱する予約コマンドで、将来 `z2term` を別用途に使いたくなったら [`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt) の `z2termAliasScript` を差し替えればよい。表示言語は `LocaleHelper.language` に追従。proot/z2root/chroot の全起動経路に配置 ([`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt))。
- **`z2-*` CLI の表示言語 (0.8.228)**: 端末に出る文言 (先頭のヘルプコメント・usage・メッセージ) を `LocaleHelper.language` に追従させる。`z2help` / `z2scan` / `z2gui` / `sshd` ラッパー等は先に対応していたが、**`z2-*` ブリッジ群 (`z2-when` / `z2-notify` / `z2-session` / `z2-alarm` …) だけが日本語ベタ書き**で、英語モードでも和文が出ていた。GitHub 直配布で README も英語が主なので、ここだけ日本語なのは実質「英語話者には使えない」に等しい。
  - 文言は [`Z2ApiMessages.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ApiMessages.kt) (`Z2ApiMsg`) に日英で持ち、`z2ApiScripts(lang)` が差し込む。**スクリプト全体を 2 セット持たない**のが要点 — ロジックを二重化すると片方だけ直して挙動がズレ、しかも端末でしか気付けない。持つのは文言だけで、制御フローは言語に関係なく 1 つ。
  - ヘルプは行頭 `#`・末尾改行つきの**完成形**で持ち、`trimMargin()` の**外**で連結する (マージン `|` の剥がし漏れを構造的に起こさない)。
  - `z2-when events` のイベント名は**訳さない** (ルールに書く識別子なので)。訳すのは説明と注記だけ。
  - `Z2ApiScriptTest` は**日英どちらの生成物にも**同じ検証 (`sh -n`・マージン剥がれ・シェバン) を掛ける。分岐を増やした以上、片方だけ壊れる余地を残さない。
  - `z2gui` は `GuiScriptStrings` を持ちながら**一部のメッセージが日本語のまま**だった (Konsole 再構成・GUI 導入失敗・音声まわり・Qt fallback の計 15 行)。同じ仕組みへ寄せた。
  - 対象外: `z2-autogui` は preexec フックから呼ばれる内部ヘルパーで**ユーザーに出す文言が無い** (日本語は実装コメントのみ)。Kotlin のコメントも同様に日本語のまま (開発者向けで端末には出ない)。

- **`z2doctor` コマンド (0.8.230・トラブル切り分け)**: 「動きません」を 1 コマンドで切り分ける自己診断。**`z2scan self` とは用途が別**で、あちらは「危ない設定を探す」(セキュリティ)、こちらは「動かない理由を探す」。名前が近いので混ぜないこと。
  - 各行は `OK` / `NG` / `--`（不明・該当なし）の 3 状態。**`NG` には必ず次の一手を 1 行付け、書けない項目は最初から出さない**（直し方の分からない `NG` は不安にさせるだけ）。**取れなかったものは `--` で、`NG` として数えない** — 分からないことを異常に格上げしない。
  - 末尾に**そのまま貼れる報告文**。相手が打つのは 1 コマンド、返ってくるのは短い報告、という形にすると「動きません」→「何が？」の往復が消える（サポートの往復 1 回は、1 日 1〜3 時間の開発では丸 1 日に相当する）。
  - **SSID・IP・ホスト名は出さない**。伏せていることを画面にも明記する。伏せ字を後付けにすると、報告文に社内 IP や SSID が混ざる事故が必ず起きる。
  - **情報源を 2 つに分ける**: 許可の有無・設定・常駐の数のように**シェルからは原理的に見えない**ものは `z2api 1 doctor`（[`Z2ApiBridge.doctorRead`](../../app/src/main/java/com/zerotoship/z2term/service/Z2ApiBridge.kt)）が JSON で返し、kernel・空き容量・sshd・`/sdcard` はシェル側で調べる。ブリッジ側は**値の解釈をしない**（`NG` の判定と文言は CLI に置く）。
  - ⚠ **ブリッジが無い状態でも最後まで走り切ること**を `Z2DoctorScriptTest` が実際の `sh` で固定する。診断は困っている人が最後に打つものなので、そこで落ちると打つ手が無くなる。

- **`z2scan` コマンド (0.8.91・脆弱性試験)**: 自端末/localhost 限定の脆弱性試験ヘルパー。z2term の哲学 (自端末・localhost 限定・非侵襲・外部送信なし・distro 公式パッケージのみ) に沿わせた 2 本立て。表示言語は `LocaleHelper.language` に追従。実装は [`Z2ScanScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ScanScript.kt)。proot/z2root/chroot の全起動経路に配置。

  **① 自己診断 (`z2scan self`)**: 外部ツール不要。検出件数 > 0 で exit 1。
  - `/proc/net/tcp{,6}` から全インタフェース待ち受け (`0.0.0.0`/`::`) の TCP LISTEN を検出
  - `sshd_config` の危険設定 (PermitEmptyPasswords / PasswordAuthentication / PermitRootLogin yes)
  - `~/.ssh` と `authorized_keys` のパーミッション
  - 主要ディレクトリの world-writable ファイル
  - SUID バイナリ (擬似 root 下なので参考表示)
  - `PATH` の空要素 / `.` 混入

  **② スキャナ (`net`/`host`/`cve`)**: distro 公式の `nmap`/`lynis`/`trivy`/`grype` を `ensure_pkg` (`detect_pm` で apk/apt/pacman 判定) で一度だけ導入して叩く薄いラッパー。
  - `z2scan net` の nmap は `-sT -Pn` (root 不要)・**既定対象 `127.0.0.1`**。localhost 以外の対象は `--allow-remote` の明示＋警告が無いと拒否する (無許可のマス標的化を構造的に防ぐ)
  - `host` は lynis (無ければ `self` へフォールバック)、`cve` は trivy/grype があれば rootfs の既知 CVE をスキャン
  - スキャナ本体は同梱せず・結果はローカル出力のみ (F-Droid 適合・外部送信なし)
- `launchAndroidSh`: proot 不可時のフォールバック (`/system/bin/sh` + 最小 mkshrc)。

#### 実行エンジン z2root (裏機能・非 root)

`executionEngine = "z2root"` のとき、`launch()` がバイナリを `nativeLibraryDir/libz2root.so` (自前 ptrace エンジン) に差し替える。proot 互換 argv subset を受けるので引数・env はそのまま流用する (`PROOT_*`/talloc は z2root が無視)。

`libz2root.so` 未同梱 (`scripts/build-z2root.sh` 未実行) の場合は proot へフォールバックする (**full のみ**。foss は proot を持たないため z2root が必須で、欠落時は engine binary not found で停止)。

**ビルド成果物の stale 対策 (0.8.48)**: z2root/z2accept の `.so` はビルド成果物 (git 管理外) で `git pull` や CMake では再生成されないため、`z2root.c` を直しても古い `.so` が APK に同梱され続ける事故が起きる。Gradle タスク `buildZ2rootNative` が `full` フレーバーの jniLibs マージ前に `scripts/build-z2root.sh` を自動実行するので、`./gradlew assembleFull*` だけで常に現ソースから再生成される (手動手順ゼロ)。`build-z2root.sh` は NDK パスを自己解決する (環境変数 / `local.properties` の `sdk.dir`+`ndk.version` / `$ANDROID_HOME`)。`foss` は実行時 DL のため対象外。

##### パス変換

proot 相当に強化済み。

- パス内 symlink の canonicalize
- `/proc/<tid>/cwd` による cwd 相対パスの絶対化
- `dirfd` 相対は非変換
- `renameat2` / `linkat` / `symlinkat` の 2 パス変換、`utimensat` のパス変換
- execve ローダ差し替え・`#!` シバン解決
- 非 ELF・存在しない PATH 候補は loader を噛ませず素の execve でカーネルに `ENOENT`/`ENOEXEC` を返させる passthrough
- **bind の解決は最長一致** (最も具体的 = guest_len 最長の bind 優先)。`translate_abs` / `host_to_guest` の両方 (0.8.75)
- `host_to_guest` は rootfs マーカー `"/files/distros/<name>/"` からゲストパスを復元する**純粋文字列処理の fallback** を持ち、OS メジャーアップ等でデータディレクトリの絶対 prefix が変わっても (`/data/data` ↔ `/data/user/0`) 逆変換できる (0.8.97)

**スクラッチ領域の書き戻し (0.8.99〜0.8.101)**: 変換済み host パスは tracee スタック下のスクラッチへ `process_vm_writev` で書き戻す。`SCRATCH_OFFSET` は 16 (sp 直下＝同一 present ページ内) に置き、さらに `write_tracee_mem` が **`PTRACE_POKEDATA` フォールバック**を持つ。POKEDATA はカーネルの `__access_remote_vm` 経由で `expand_stack()` を呼ぶため、`process_vm_writev` (GUP・スタックを grow しない) が EFAULT する未 grow 下位ページにも確実に書ける。

##### 性能 (seccomp 化と read 非トレース化)

**seccomp-bpf 化 (0.8.32)**: 従来は `PTRACE_SYSCALL` で全 syscall を 2 回トラップしていたのを、パス変換・fakeroot 偽装・getcwd 逆変換・`/proc` 偽装に必要な syscall だけ `SECCOMP_RET_TRACE` で捕捉し、残りはネイティブ実行にした (proot と同方式)。実機ベンチで fork/exec 約 2.3 倍・read 約 3 倍、実 IO は proot の約 2 倍以内、FS 走査は proot より高速。

**read 非トレース化 (0.8.34、0.8.35 で既定 ON)**: seccomp 化後も `/proc/<pid>/status`・`loginuid` の偽装のために `read`/`close` を捕捉し続けるコストが残り、小 read 連打 (`dd bs=1` 等) が proot 比約 9 倍だった。read-free では偽装を `openat` の瞬間に行う＝偽装済み内容を rootfs 内の使い捨て temp に書き出し `openat` のパスをそこへ差し替える (直後に unlink＝open-then-unlink)。以後の read は通常ファイルへの読み取りなので `read`/`close` を seccomp 対象から外せる (ネイティブ速)。実機検証 (run-as) で `dd bs=1 ×300000` が約 8.1s → 約 0.28s (proot 約 0.32s をわずかに上回る)、status/loginuid 偽装は維持・temp 残骸なしを確認。`Z2ROOT_NO_READFREE=1` で旧 read トレース経路へフォールバック可。

##### 互換のための偽装・橋渡し

| 対象 | 方式 | 版 |
|---|---|---|
| `ioctl` の `TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` | entry で legacy (`TCGETS`/`TCSETS`/…) へ書き換え。Android がアプリの pty への TCGETS2 を拒否するため | 0.8.36 |
| AF_UNIX ソケットの `sun_path` | `bind`/`connect` (aarch64 200/203) をトレースし rootfs 内のホスト実パスへ書き換え。abstract ソケット (`sun_path[0]=='\0'`) は触らない | 0.8.38 |
| `accept`(202) | Android の untrusted_app seccomp が禁止 (bionic は `accept4`(242) しか使わない)。libc 非依存の極小 `LD_PRELOAD` シム `libz2accept.so` (生 `svc`・依存ライブラリ無し) で `accept()` を `accept4(...,0)` へ橋渡し | 0.8.39 |
| io_uring 3 番号 (`io_uring_setup`=425 / `io_uring_enter`=426 / `io_uring_register`=427) | SIGSYS ハンドラが 0 でなく **`-ENOSYS`(-38)** を返し libuv を epoll へフォールバックさせる (他の SIGSYS は従来どおり 0 偽装) | 0.8.49 |
| `SCM_CREDENTIALS` の ucred | `sendmsg`(211)/`recvmsg`(212) をトレースし、送信時はプロセスの実 uid/gid へ、受信時は 0 へ戻す。カーネルは申告 uid が実/実効/保存 uid のいずれか (または `CAP_SETUID`) と一致しないと `EPERM` を返すため。`SCM_RIGHTS`/memfd は無変更 | 0.8.53 |
| ハードリンク (`linkat`) | **まず実ハードリンクを試し**、Android が `EACCES`/`EPERM`/`EXDEV` 等で拒否したときだけトレーサ側で `copy_for_link` が `old` を `new` へコピーして成功(0)を返す。`new` が既に存在する (本来 `EEXIST`) 等の本物のエラーは保持 | 0.8.47 |
| copy-fallback 後の `st_dev`/`st_ino` | git 2.46+ の「`link()` 後に dest を lstat し src と一致検証」を通すため、**パス相関**で偽装する (`linkcopy_record` がコピー先のホスト実パスを記録し、`newfstatat`/`statx` の entry で stat 対象のホストパスを `host_path_for` で解決して `linkcopy_find` が一致を見たときだけ exit で `st_dev`/`st_ino`、statx は `stx_ino`＋`stx_dev_major/minor` を src 値へ偽装) | 0.8.58〜0.8.64 |

`libz2accept.so` は `scripts/build-z2root.sh` が生成し gitignore される。`ProotLauncher` が rootfs の `/usr/local/lib/libz2accept.so` へ配置し `LD_PRELOAD` を env 注入する (読み込み失敗は ld.so が警告して無視する非致命)。`__errno_location` は `__attribute__((weak))` + NULL ガードで参照するため、bionic 製バイナリ (aapt2 等) に LD_PRELOAD が漏れても起動失敗しない (0.8.55)。

##### 自前ローダ (`load_elf_and_jump`)

`plan_exec` が対象 ELF の種別と interp を見て 3 経路を振り分ける。

| 経路 | 対象 | 内容 |
|---|---|---|
| `--loader` | 静的 PIE (ET_DYN) 直接ロード | `PT_DYNAMIC` を辿って RELR/RELA (`DT_RELR`/`DT_ANDROID_RELR`/`DT_RELA`) の `R_AARCH64_RELATIVE`(1027) を `*(base+off)=base+addend` で自前適用し、phdr のコピーの各 `p_vaddr` に `base` を加算した配列を `AT_PHDR` に渡す (bionic の `__libc_init_mte`/`__bionic_get_tls_segment` が持つ `load_bias=0` 即値仮定＝phdr の `p_vaddr` を絶対アドレス扱いする前提を成立させる)。`ET_DYN && base!=0` のときだけ動作し ET_EXEC (`base==0`) は素通り (0.8.59) |
| `--loader-noreloc` | 動的 ELF / 動的 interp | ld.so (`ld-linux-aarch64.so.1` 等) は `_dl_start` で自己 relocate するため、ローダが肩代わりすると load bias が二重加算される。`skip_reloc` でゲートして抑止する (0.8.67) |
| `--loader-exec <ld.so> <prog> <argv0> [args...]` | musl `ld.so` × ET_EXEC | musl の `ld.so` は ET_EXEC (非PIE) を明示起動できず `Not a valid dynamic program` で落ちる。本体と `ld.so` を両方 `mmap` し、**カーネルが `PT_INTERP` 経由で exec したのと同じ初期スタック/auxv** (`AT_PHDR`/`AT_PHENT`/`AT_PHNUM`=本体の phdr、`AT_ENTRY`=本体エントリ、`AT_BASE`=`ld.so` の load base) を組んで `ld.so` のエントリへ分岐する (`load_exec_via_interp`/`map_img`)。`use_loader` 無効時は従来経路へフォールバック (0.8.78) |

- `ld.so` / loader に渡すプログラムパスは、**`host_to_guest` で逆変換したゲストパス**にする。ホスト実パスを渡すと `ld.so` 自身の `open()` も tracee として翻訳され、bind 配下が「ゲストパス扱い→rootfs 前置」されて ENOENT になる (動的 0.8.37 / 静的 0.8.54)
- `--loader-exec` の振り分けは **interp basename が `ld-musl*` かつ対象が ET_EXEC のときだけ**。glibc `ld.so` や PIE は非対象として温存する
- 動的 ELF 経路で interp basename が `linker64`/`linker` (bionic) のときは `--argv0`+argv0 を渡さない。この端末の bionic linker64 は `--argv0` を解さず実プログラムの argv へ素通しするため、aapt2 が `--argv0` をパス引数と誤認する (0.8.56)

##### 既知の制限

**printf/malloc/pthread/TLS を使う「リッチな」static-PIE は依然 crash する。ローダでは解決不能。** `__attribute__((constructor))` を仕込んだ static-PIE では `CTOR_RAN` が出ず `main` のみ実行されることから、**bionic NDK の static-PIE crt (`_start`) が `.init_array` コンストラクタを呼ばない**のが真因 (非PIE crt は `__init_array_start/end` を読み structors にセットするが、static-PIE crt の `_start_main` は `fini` しか処理せず init_array セットアップ命令が欠落)。コンストラクタは libc 初期化後・`main` 前に走る必要があり、ローダは `_start` へ jump 後に制御を失う＝後追い呼び出し不可。proot/カーネルでも同じ結果になる **NDK 固有の制約 (z2root の parity gap ではない)**。

##### 実エンジンの表示 (0.8.44)

設定の「実行エンジン」セクションに「このタブの実エンジン」行を出す。設定チップ (＝次に起動する選択値) ではなく、そのタブが実際に起動したエンジン (`TerminalSession.actualEngine`。`ProotLauncher.resolveLaunchEngine()` か chroot 経路の結果) を表示する読み取り専用行で、選択が倒れたとき (z2root 未同梱→proot、chroot プローブ失敗→proot) も実態を正しく示す。併せて、エンジン選択を表示/非表示するバージョン行 7 タップのトグルに **3 秒のクールダウン**を入れ、連打で即座に逆方向へ戻らないようにした。

<details>
<summary><b>z2root 修正履歴 (0.8.30〜0.8.101・29 件)</b> — 現仕様は上記。ここは「なぜそうなったか」の記録</summary>

**0.8.30 初期の e2e 成立**: 実機 Ubuntu 24.04 で `apt install hello` が end-to-end 成功 (`Unpacking`→`Setting up`→`Hello, world!` 実行) まで確認。

**0.8.32 seccomp-bpf による高速化**: 全 syscall 2 回トラップから、必要な syscall だけ `SECCOMP_RET_TRACE` で捕捉する方式へ (詳細は上記「性能」)。

**0.8.34 / 0.8.35 read 非トレース化**: 小 read 連打が proot 比約 9 倍だったのを open-then-unlink 方式で解消し、0.8.35 で既定 ON (詳細は上記「性能」)。

**0.8.36 glibc distro の対話シェルが起動しない**: z2root + Arch で画面が真っ黒・プロンプト無し (固まって見える) になっていた。原因＝新しい glibc(2.42+) の `tcgetattr` が `ioctl(TCGETS2)` を使うが Android はアプリの pty への TCGETS2 を拒否 (`EACCES`) するため `isatty()` が失敗し、bash/zsh が「端末でない」と判断して非対話起動 (`PS1` 無し) になっていた (musl の Alpine は旧 `TCGETS` で無事、proot は ioctl を書き換えるので無事)。修正＝`TCGETS2` 系を entry で legacy へ書き換え (先頭の `struct termios` 部分は termios2 と同レイアウトで通常 baud では実害なし)。実機検証＝Arch + z2root で対話 `[…]$` プロンプトに到達しコマンド実行を確認、Alpine(musl) は回帰なし。

**0.8.37 bind マウント配下のバイナリ直接実行 (動的)**: ホーム (`-b <home>:/root`) でコンパイルした実行ファイルを `./a.out` で動かせなかった (動的は `error while loading shared libraries: … cannot open shared object file`、静的は `z2root loader: open(…): No such file or directory`)。原因＝動的 ELF のとき rootfs 内 `ld.so` に渡すプログラムパスを**ホスト実パス**にしていたが、`ld.so` 自身の `open()` も tracee として翻訳されるため、bind 配下のホストパスが「ゲストパス扱い」され rootfs を前置されて ENOENT になっていた (rootfs 配下のバイナリは host パスがそのまま rootfs 配下で二重変換抑止に当たり偶然動いていた)。修正＝`ld.so` には `host_to_guest` で逆変換した**ゲストパス**を渡す (`#!` シバン経路と同じ思想)。実機検証＝`cd /root && gcc -O2 hello.c -o hello && ./hello` が `sum(1..100)=5050` を出力、rootfs 内バイナリは回帰なし。`pacman -U` でのオフライン gcc 導入 (run-as は SELinux `runas_app` ドメインで `sendmsg` が遮断されネット不可のため) と gcc 16.1.1 での実コンパイルも確認。

**0.8.38 GUI (`z2gui`: Xvnc + openbox + 端末) が動かない**: z2root を選んで GUI を起動すると「VNC サーバが立たない／ビューアが接続できない」状態だった。原因＝z2root が AF_UNIX ソケットの `bind()`/`connect()` の `sun_path` を翻訳していなかったこと。X サーバはディスプレイソケットを `/tmp/.X11-unix/X1` に作るが、無変換で通すためカーネルが**ホストの実 `/tmp`** (アプリには存在しない) へ作ろうとして `ENOENT` になっていた (同じ穴で dbus / pulseaudio の unix ソケットも壊れる)。proot はソケットアドレスを翻訳するので GUI が動いていた。実機検証 (run-as)＝`/tmp/.X11-unix/Xtest` への `bind()`+`connect()` が成功し、ソケットが**ホストの `/tmp` ではなく rootfs 内**に作られることを確認、ファイルパス翻訳に回帰なし。

**0.8.39 GUI が実際に描画するところまで到達**: 0.8.38 で Xvnc は起動するが画面が真っ黒・"Connection reset" のままだった残課題を解消。原因＝Alpine の `Xvnc` は musl 製で `accept(2)` を syscall 202 で直接呼ぶが、Android の untrusted_app seccomp は `accept`(202) を禁止→VNC 接続のたびに SIGSYS で弾かれ、z2root が握り潰すしかなく接続が成立しない (毎回 `accepted: ::0` で切断)。SIGSYS 地点で `accept`→`accept4` に差し替えて再実行する手は aarch64 では不安定 (syscall がスキップされ pc を綺麗に巻き戻せない) だったため、`LD_PRELOAD` シム方式を採用。実機 (untrusted_app・実アプリ) 検証＝z2root + Alpine + GUI で RFB ハンドシェイク完走 (`accepted: 127.0.0.1::…`／protocol 3.8／pixel format) し openbox + xterm のデスクトップが描画。dropbear 等 `accept` する SSH サーバも併せて解消。

**0.8.40 GUI アプリが X11 `BadAccess` で segfault**: Xvnc を `-extension MIT-SHM` 付きで起動し X 共有メモリ拡張を無効化した。クライアントが MIT-SHM (`X_ShmAttach`) を試みると、z2root では SysV 共有メモリの相乗りが通らず X サーバが `BadAccess` を返し、その非同期 X エラーでアプリが segfault していた (proot では `shmget` 自体が失敗してアプリ側が自動で非 SHM 描画にフォールバックするため顕在化しなかった)。VNC はローカル接続で共有メモリの利点がほぼ無いため、拡張ごと無効化して全クライアントを確実に通常描画 (`XPutImage`) へ落とす (proot エンジンにも無害)。`z2gui` ランチャ (`GuiScript.kt`) は起動毎に rootfs へ書き直されるので既存 distro にも次回 GUI 起動から反映される。

**0.8.43 `/proc/self`・`/proc/thread-self` の中間パス誤解決**: 0.8.41 は先頭の `/proc/self…` だけを `host_path_for()` で tracee pid へ書き換えていたが、間接 symlink が抜けていた。ゲストが `/proc/net/tcp` を開くと、カーネルの magic symlink `/proc/net` → `self/net` により `canonicalize_guest()` がパス途中で `self` 成分を walk し、それをトレーサ (z2root 親) として `readlink` するため `/proc/<別ホスト pid>/net/tcp` に解決され `EACCES` になっていた。修正＝`canonicalize_guest()` が `/proc` 直下に現れた `self`／`thread-self` 成分を (magic symlink を `readlink` せず) tracee pid へ解決する。開発環境で直接 `/proc/self/net/dev` と間接 `/proc/net/dev` が同一解決になることを確認 (残る `EACCES` は外側サンドボックスが per-pid `net/*` を制限するためで実機では出ない)。`id`=root と `/proc/self/comm` の解決には影響なし。

  この修正は **SSH 認証直後リセットの調査中の動的トレースで発見**した。リセット自体は実機検証が必要＝開発環境の失敗は stdin クローズ (`</dev/null`) による channel EOF で dropbear が PTY master を close → カーネル `SIGHUP` するアーティファクトで、stdin を開けばログインシェルは起動し MOTD まで出る＝PTY 経路は概ね機能している。実機の対話 ssh は channel EOF を出さないため別要因の疑い。`z2root.c` の `Z2ROOT_TRACE` 計測はこの実機トレース用に意図的に残置。

**0.8.44 実エンジン表示の追加**: 上記「実エンジンの表示」参照。

**0.8.47 `--link2symlink` を作り直し (git・npm 破壊の修正)**: 旧実装は `linkat(old,new)` を「`new` を `old` のゲスト絶対パスへの symlink」に化かしていたが、これは git の loose object 確定 (`tmp` に書く→`link(tmp,final)`→`unlink(tmp)`) で `final` が直後に消える `tmp` を指す**dangling symlink** になり「`fatal: … is not a valid object`」でコミットが壊れた (dpkg は元ファイルが残るので無害だっただけ)。npm の global install もキャッシュからの**ハードリンク**で展開するため、node 製 CLI が「ロゴも出ず無反応」だったのも同じ dangling 化 (本体 JS が壊れる) が有力。修正＝実ハードリンク優先 + copy-fallback (上記表)。実ハードリンクが通る環境では本来の共有 inode 意味論を保ち、通らない `/data` 上でも `new` が独立した実ファイルになるので `old` を後で `unlink` しても残る＝「リンクで原子的に確定」する汎用パターン (git/coreutils/ビルド系) が一様に動く。実機検証＝`ln orig hard; rm orig; cat hard` が中身を保持し、`git init`→`add`→`commit`→`log`→`cat-file` の全サイクルが成功。⚠️**旧 z2root で `npm install` 済みのパッケージは既に dangling symlink 化しているため、本修正後に再インストールが必要**。

**0.8.48 stale `libz2root.so` 事故の構造的防止**: 上記「ビルド成果物の stale 対策」参照。0.8.47 の git/npm 破壊が長引いた真因がこれだった。

**0.8.49 node 製 CLI が起動しない (io_uring)**: node が起動直後に `node: src/unix/core.c:646: uv__close: Assertion 'fd > STDERR_FILENO' failed.` ＋ SIGABRT で落ちていた。原因＝SIGSYS ハンドラが禁止 syscall を**一律 0 (成功偽装)** で握り潰す fakeroot 方針が `io_uring_setup`(425) にも適用され、libuv が偽装された `0` を有効な ring fd と誤認→fd 0 をバックエンドとして保持→`uv__close(0)` で abort。修正＝io_uring 3 番号だけ `-ENOSYS` を返す (proot は元から io_uring 不可なので動いていた＝同じ状態に揃える)。検証＝dev シェルは proot 配下で z2root をネストすると二重 ptrace でマスクされるため、z2root エンジンで立てた sshd へ ssh (単一 ptrace の実条件) で再現・修正確認 (LD_PRELOAD で `io_uring_setup` を強制 ENOSYS にすると node も git も治ることを実証してから本体修正)。⚠️ この時点ではハードリンク方式の `git clone` が `fatal: hardlink different from source` で失敗する件が残っており、当面 `git clone --no-hardlinks` で回避していた (0.8.58〜0.8.64 の B-3 で解消)。

**0.8.53 GUI 音声が無音 (proot では動作済み)**: 原因は 2 つ。(1) PulseAudio の `--daemonize` は detach 時に `/proc/self/exe` を re-`execve` して自己 daemon 化するが、z2root では `/proc/self/exe` がランチャ (`libz2root.so`) に解決され「cannot self execute」で daemon が起動しない → `GuiScript.kt` を `--daemonize` 廃止＝`setsid pulseaudio -n --exit-idle-time=-1 … &` へ変更 (停止は `pactl exit`)。(2) PulseAudio クライアントは `AF_UNIX` ハンドシェイクで `SCM_CREDENTIALS` に自分の uid/gid を載せて `sendmsg` するが、カーネルは申告 uid が実/実効/保存 uid のいずれかと一致しないと `EPERM` を返す。fake_root は uid=0 を偽装する一方で非特権アプリの実 uid は非 0 のため不一致→クライアントが "Connection died" で死ぬ → ucred 書き換え (上記表)。検証＝z2root + GUI で音が出る・`/tmp/z2gui-audio-<display>.log` に "Connection died" が出ない・`pactl info` で `z2sink` が見える。

**0.8.54 bind 配下の静的 ELF が exec できない + セルフホストビルド対応**: 静的 ELF を `--loader` で起動する際、loader にプログラムの**ホスト実パス**を渡していたため、bind 配下 (NDK 静的 clang 等) が「ゲストパス扱い→rootfs 前置」され ENOENT (`z2root loader: open(…/clang-21): No such file`) になっていた (0.8.37 が動的 ELF で直したのと同じ穴の静的版)。修正＝動的 ELF 経路が `ld.so` に `guest_real` を渡すのと同じく、loader にもゲストパスを渡す＝rootfs/bind の両方で静的バイナリを正しく map できる。ビルド側＝NDK の clang は静的 ELF なので**この修正を含む APK を入れる前の現行エンジン下では exec 不可**。そこで `build-z2root.sh` に自動フォールバックを追加＝NDK clang が exec できなければ rootfs の動的 clang をクロスコンパイラに使い (`--target=aarch64-linux-android29 --sysroot=<NDK sysroot>`)、NDK の静的ライブラリ/crt を **GNU ld で手動リンク**する (clang ドライバの自動リンクは lld 専用フラグ `--use-android-relr-tags` を渡し GNU ld が拒否するため使わない)。PC ビルドは probe を通過し従来どおり NDK ツールチェーンを使う＝挙動不変。検証＝この z2root term 上で `bash scripts/build-z2root.sh` が完走し `libz2root.so`・`libz2accept.so` を生成＝ネイティブ部分のオンデバイス自己ホストビルドが成立。(A) ローダ修正と (B) フォールバックは密結合。

**0.8.55 accept シムを bionic 安全化**: オンデバイスビルドでは JVM (musl) の `accept`(202) を通すためビルド全体に `LD_PRELOAD=libz2accept.so` を注入するが、シムが `__errno_location` を**非 weak の未解決シンボル**として参照していたため、AGP が起動する bionic 製 aapt2 に LD_PRELOAD が漏れると `cannot locate symbol __errno_location` で起動失敗し `processFullReleaseResources` で停止していた。修正＝weak + NULL ガード。検証＝`LD_PRELOAD=libz2accept.so ./gradlew :app:assembleFullRelease` が `BUILD SUCCESSFUL` (当時は「z2root は重い full ビルドでフリーズする」と見て proot で検証した)、生成 APK (69MB・release 鍵署名) の同梱 `libz2accept.so` が WEAK `__errno_location`・`libz2root.so` が case-3 修正入り NDK r29 静的 EXEC であることを unzip+readelf で確認。なお merge の増分キャッシュが旧 `.so` を stale 同梱する事象に当たったため `fullRelease` 中間物を rm して再ビルドした (0.8.48 の `buildZ2rootNative` 依存だけでは増分 merge を強制更新できない場合がある)。後に 0.8.62 を z2root 上で 16m58s・フリーズ無しで完走＝重い full ビルドで z2root と proot に差は無いと判明。

**0.8.56 `.l2s` チェーンと aapt2 の 2 つの parity gap**: (1) NDK の `libc++_shared.so` が link2symlink で多段 symlink 化されており、CMake のネイティブリンクが `ld.lld: unable to find library -lc++_shared` で失敗。原因＝`canonicalize_guest()` が `readlink` で得たリンク先を常に「ゲストパス」として walk するが、link2symlink が格納するリンク先は**ホスト実パス** (`.../shared_home/android-sdk/…`) のため rootfs を二重前置して ENOENT。修正＝絶対リンク先を `host_to_guest()` で逆変換してから継続する。(2) CMake gap を外すと次に `processFossDebugResources`/`…ReleaseResources` の AAPT2 daemon 起動が `error: expected absolute path: "--argv0"` で失敗。原因＝aapt2 は Android の aarch64 ELF (interp=`/system/bin/linker64`) で、z2root は動的 ELF を `<interp> --argv0 <name> <prog> <args>` で起動するが、この端末 (Android 12) の bionic linker64 は glibc/musl の ld.so と違い `--argv0` を解さず実プログラムの argv へ素通しするため aapt2 が `--argv0` をパス引数と誤認していた (`/system/bin/linker64 aapt2 version` は成功、`--argv0` 付きは同エラー、と実証。kotlinc/java＝glibc ld.so は `--argv0` を解すので通っていた)。修正＝bionic interp のときは `--argv0` を渡さない。✅**2 件とも 0.8.56 APK を本体 UI でインストールし z2root 上で e2e 検証済み (2026-06-09)**。

**0.8.57 `readlinkat` 戻り値の切り詰め**: `.l2s` 等の symlink を `readlink(2)` すると `/root/android-sdk/n` (19B) のように途中で切れていた。原因＝tracee はリンク長 `lstat` の `st_size` (z2root がゲスト長に逆変換済み＝短い) でバッファを確保するのに、カーネルはホスト実パス (長い) をそのバッファへ切り詰めて書き込み、それを `host_to_guest()` するとさらに短くなっていた。修正＝proot 同様、exit で z2root 自身が対象 symlink のホスト実パスを full バッファで `readlink` し直してから変換し `bufsiz` でクランプして書き戻す (entry で対象のホスト実パスを `pid_state.aux_path` に控える。`dirfd` 相対などホストパス未確定時は従来の tracee バッファ読みにフォールバック)。リンカは open するだけなので 0.8.56 のビルド成立には影響しないが、`.l2s` 系を `readlink` 依存で扱うツールへの備え。⚠️**e2e は本修正入り APK 導入後に確認が必要**。

**0.8.58 → 0.8.62 → 0.8.63 → 0.8.64 git clone の hardlink 検証 (B-3)**: 段階的に 4 回直した項目。
- **0.8.58**: 真因は Android SELinux (`untrusted_app`) が `link(2)` を端末全域で禁止する OS 制約で、link2symlink が常に copy-fallback (別 inode) になり、git 2.46+ の「`link()` 後に dest を lstat し src と `st_dev`/`st_ino` 一致を検証」に落ちる点。copy-fallback 成立時に (src_dev, src_ino, dest_ino) を小リング (32件) へ記録し、stat 系 exit で dest_ino 一致時に src 値へ偽装する方式を実装。
- **0.8.62**: 稼働 0.8.61 上で C プローブにより切り分けた結果、copy-fallback 200 件すべてで stat 偽装が一度も発火しない (0 fake) ことが判明＝0.8.58 の「コンパイル済だがおそらく動く」仮定を否定。真因は `linkcopy_record` が dest のホストパスを**後から `stat()` し直して** inode を採取しており、tracee が読む inode とずれて照合が常に miss していたこと。修正＝`copy_for_link` を out-param 付きへ変更し、コピー生成直後の出力 fd を `fstat()` して dest inode を確定採取 (tracee が後で見る実体と同一を保証) ＋ `linkcopy_record` を値渡し化して再 `stat()` を排除。
- **0.8.63**: 0.8.62 が招いた起動退行 (ゲスト＝`Arch Linux ARM` が起動直後に `exitCode=-1` で即死) への対応。真因＝0.8.62 で linkcopy の記録が**初めて成功するようになった**結果、それまで `g_linkcopy_used==0` で素通りしていた stat 偽装ホットパスが常時 ON になったこと。照合キーが **inode 番号だけ**だったため、起動中に init/ld が stat した無関係なファイルの inode がたまたま記録済み dest と衝突すると無縁の src 値へ偽装され、ゲストの起動時 stat が壊れていた。`(dev, ino)` 両方へ厳格化 (`copy_for_link` の `fstat` で `dest_dev` も採取し `linkcopy_find` を dev+ino 一致に変更)。
- **0.8.64**: 0.8.63 は無効だった＝dest はコピーで rootfs bind 配下＝ゲスト全ファイルと同じ host `/data` パーティション上に作られるため `st_dev` は rootfs 全域で同一の固定値で、`(dev, ino)` 照合は実質 inode 単独照合と変わらなかった。修正＝inode 照合を**パス相関**へ置換 (上記表)。fd ベースの `fstat` は entry でパスを取れないため inode 偽装の対象外＝uid/gid 偽装のみ (git の hardlink 検証は `lstat`/`newfstatat` 経路を使うため B-3 に影響なし)。

**0.8.59 static-PIE の relocation 適用と phdr バイアス**: 従来から続く「静的バイナリが segfault する」既知制限の一部解消 (詳細は上記「自前ローダ」)。in-process 検証ハーネスで単純 static-PIE (`write` のみ) が動くこと・非PIE が回帰しないことを確認。⚠️リッチな static-PIE は別の根本制約で依然 crash (上記「既知の制限」)。

**0.8.67 起動退行の真因を確定し根治**: **0.8.62〜0.8.64 の stat 偽装をめぐる修正はこの退行の真因ではなかった (誤診)**。診断トレース＋SIGSEGV 全レジスタダンプで真因を特定＝0.8.59 で `load_elf_and_jump` に入れた RELATIVE/RELR 肩代わりが、全動的バイナリの起動経路でロードされる `ld.so` にも当たっていたこと。ld.so は `_dl_start` で自己 relocate するため、ローダが load bias を二重加算し RELATIVE 再配置の全ポインタが ×2 になって `blr x8` で命令フェッチ SIGSEGV していた (決定的証拠＝`pc==si_addr==x8==実 ld.so アドレス×2`、別 run でも一致)。修正＝`skip_reloc` でゲート (上記「自前ローダ」)。stat 偽装 (パス相関) 自体は B-3 用として有効なので残置。

**0.8.78 musl `ld.so` の動的 ET_EXEC 明示起動不可を根治**: Alpine(musl) で ET_EXEC バイナリ (`cc` 等) が起動できなかったのを `--loader-exec` 経路の新設で解消 (上記「自前ローダ」)。⚠️**実機 e2e は本修正入り APK 導入後に確認が必要**。

**0.8.84 大きい argv を渡す exec が `ENOENT` で失敗**: `rewrite_execve` が (1) argv 連結バッファが固定長 `char blob[8192]` で `blob_sz>8192` のとき `if (blob_sz<=sizeof(blob))` が偽になり**書き換えを丸ごとスキップ**→path レジスタにゲストパスが残ったまま execve され ENOENT、(2) argv 読み取り上限 `MAX_ARGS 256` で 256 個目以降を切り捨て、の二重制限を持っていた。クロスディストロ cmdtest e2e で Kali の `apt-get install python3` が dpkg の byte-compile (`python3.13 -E -S py_compile.py <287ファイル＝~11KB argv>`) で踏んで `cannot execute: required file not found` 失敗するのを発見 (二分で「argv 総バイト ~7.5KB 超・カーネル ARG_MAX 2MB 以下＝z2root 内部バッファ起因」と確定)。修正＝argv 読み取りを上限なしの動的確保 (`realloc`) に、`blob`/`parts`/`ptrs` を argv サイズ依存の `malloc` にして `MAX_ARGS` を撤去 (scratch は従来どおり `sp` 直下＝growsdown stack を `process_vm_writev` が伸長するため大 argv でも mapped)。Alpine/Ubuntu の cmdtest は非ゼロ 0 件。⚠️**Kali での python 導入完走＋大 argv exec の実機 e2e は本修正入り APK 導入後に確認が必要**。

**0.8.95 → 0.8.96 → 0.8.97 OS 15→16 アップグレード後に起動不能**: 0.8.95 で (1) `host_to_guest` のホットパスに `realpath()` を足し全パス変換に lstat walk を発生させ全体が激重・入力遅延化、(2) 起動毎に `find <rootfs> -type l` で rootfs 全走査＋symlink 再作成、の 2 変更で起動が不定・キーボード異常・symlink 破壊と自爆したため **0.8.96 で撤回**。0.8.97 でホットパス非依存の安全版で再修正＝原因は、proot `--link2symlink` が残す `.l2s` symlink がホスト絶対パスを抱えるところ、OS メジャーアップで data ディレクトリの絶対 prefix 正規化 (`/data/data` ↔ `/data/user/0` 等) が変わり、`host_to_guest` の rootfs/bind 直接照合が外れ stale 絶対パスを素通し→`translate_abs` が rootfs を二重前置→ENOENT となり `zsh` 等が `cannot open shared object file` で起動不能になっていた。修正＝rootfs マーカーからの純粋文字列 fallback (上記「パス変換」)。⚠️**実機 OS ダウングレード不可のため当該 OS アップ退行そのものの e2e 再現は不可。論理上 prefix 非依存で救済される設計。**

**0.8.99 → 0.8.100 → 0.8.101 素の ELF が間欠的に起動失敗**: `ls`/`ssh` 等が間欠的に `cannot open shared object file` で落ちる。真因は `.l2s` ではなく**パス書き換え用スクラッチ配置**＝変換済み host パスを tracee スタック下 `sp - SCRATCH_OFFSET(=2048)` へ `process_vm_writev` で書き戻していたが、kernel 6.x はリモート書込でスタックを grow しないため、起動最初期 (スタック low-water≒sp) に未 grow 下位ページへ書こうとして EFAULT→ローダが本体/libc を開けず起動不能になっていた (後段の locale 読込はスタック伸長済で成功するため run 単位で 5/8 のように割れる間欠性になる)。実機 instrumented trace (`scratch ... wr=-1 errno=14(Bad address)`) で確定。**0.8.99/0.8.100**＝`SCRATCH_OFFSET` を 2048→**16** に縮め sp 直下の同一 present ページ内へ置く。頻度は激減 (実機 `ls` 8/8) したが、`sp` がページ境界丁度や長い `.so` ホストパスでは依然下位ページへ落ち、`sscanf` 等を使わない素の `ls` は通っても zsh の ZLE モジュール `.so` がロードできずキーボード行編集が壊れる間欠症状が残った (`scratch_base()` のクランプでも `sp` 境界丁度は救済不能)。**0.8.101 で根治**＝`write_tracee_mem` に `PTRACE_POKEDATA` フォールバックを追加 (上記「パス変換」)。実機 z2root タブで **`ls` 8/8・`sshd --lan` 一発・zsh キーボード正常を確認済み**＝cannot-open / キーボード一連はクローズ (mmap 常駐 scratch への格上げは不要だった)。

</details>

#### 実行エンジン chroot (裏機能・要 root)

`executionEngine = "chroot"` のとき `launchChroot()` を使う。

- **エンジン選択の解放/解除（トグル）**: 設定のバージョンを 7 回タップで `engineSelectorUnlocked` をトグルする（非 root でも可）。解放時は `true`（proot / z2root が選べる）になり、続けて `probeRootChroot()` のセルフテストが成功した場合のみ `rootChrootUnlocked=true` となり chroot も選択肢に加わる。解放済みの状態でさらに 7 回タップすると `false` に戻し、同時に `executionEngine` を既定の proot へリセットして「表示前の状態」へ復帰する（0.8.33 で双方向トグル化）。
- `probeRootChroot()`: `su -c id`(uid=0) + `su -c "chroot <rootfs> /bin/sh -c echo"` のセルフテスト。結果は `RootProbe`(Ok/NoRoot/ChrootBlocked)。
- `launchChroot()`: `su -c` で bind mount(/dev,/dev/pts,/proc,/sys,/root,/sdcard) → `chroot` → login shell。`ensure*`(z2-*/OSC7/履歴/sshd/gui/z2run) は proot 経路と共通で流用。
- **Ctrl+C / ジョブ制御**: su 経由だと制御端末を所有できないため、login shell を **`setsid -c` 経由**で起動して有効化。
- chroot 起動失敗時は proot へ自動フォールバック（`TerminalSession.startTerminal`）。SELinux Enforcing 下の root 端末(moto g13/Magisk)で end-to-end 検証済み。`full` フレーバー専用。

### 4.4 ディストロ管理 (`distro/`)

- `DistroBundle`: `ROOTFS_VERSION`(=9)、`VERSION_MARKER`、`BUNDLED_DISTRO_ID="alpine"`。
- `DistroSpec`: id/表示名/パッケージマネージャ/同梱可否/asset 名/DL URL or index URL/既定シェル/DL サイズ目安。
  - Alpine = 同梱 (`alpine-minirootfs-aarch64.tgz`, zsh)。Ubuntu/Arch/Kali = linuxcontainers の index から最新 `rootfs.tar.xz` を実行時解決して DL (bash)。
- `DistroInstaller`: 依存無しの手書き tar パーサ (ustar/GNU `L`/PAX `x`/`g`、symlink/hardlink)。`decompress` がマジックバイトで gzip/xz 判定。
  - **Zip-Slip 対策 (0.8.141)**: 全展開先を `outputDir.canonicalFile` 配下に封じ込める (`isWithin`)。`canonicalFile` が既存プレフィックスの symlink を解決し `..` を正規化するため、悪意ある `../` エントリと「親に仕込んだ脱出 symlink を辿る write-through」の双方を弾く。ハードリンク元 (`linkname`) も同判定で rootfs 外読み出しを防ぐ。逸脱エントリは本体を `skipFully` で読み飛ばしつつストリーム整合を保ってスキップ。SHA 未固定で DL する Ubuntu/Arch/Kali の汚染 tar でアプリ領域外へ書き込まれるのを防ぐ (symlink の *ターゲット自体* は正 rootfs に多数ある正当な域外 (proot 名前空間内) リンクを壊さないよう制限しない — 危険なのは経由書き込みで、そちらを封じる)。手組み tar を実 `extractTar` に流す `ZipSlipExtractionTest` (正常展開 / `../` / write-through symlink / 域外 hardlink の 4 ケース) で回帰を防ぐ。JVM テストで `android.util.Log` を no-op 化するため `testOptions.unitTests.isReturnDefaultValues=true`。
  - `postInstallSetup`: resolv.conf/hosts、`pacman.conf` (sandbox/DownloadUser 無効化)、apt の Sandbox::User=root、version マーカー書込。
  - パーミッションは **owner-only** (`setUnixMode(ownerOnly=true)`)。world-writable だと sudo が拒否する。
- `DistroDownloader`: HTTP DL + SHA256 検証、`cacheDir/distros/<id>-<abi>.tgz` にキャッシュ (インストール成功直後に `deleteCachedArchive` で消すため常時ほぼ空)。
- `RootfsCacheCleaner`: 設定「キャッシュ削除」の実体。Android の `cacheDir` はほぼ空なので、実際に容量を食う **rootfs 内の再取得可能キャッシュ** を直接ファイル削除で掃除する。対象は全インストール OS (`filesDir/distros/<id>`) の `var/cache/pacman/pkg`・`var/cache/apt/archives`・`var/cache/apk`・`root`/各ユーザの `.cache`、および `cacheDir` 全体。**稼働中セッションが握る恐れのある `/tmp` やパッケージ本体・設定・ユーザファイルには触れない**。確認ダイアログで「項目名 … サイズ」を 1 件ずつ列挙してから削除する (ワンタップ即削除を廃止)。

### 4.5 ターミナルエミュレータ (`emulator/`)

- `TerminalEmulator`: バイト列を状態機械 (Ground/Escape/CSI/OSC/String) で処理。
  - 文字幅: East Asian Width 対応 (`ambiguousAsWide` 設定で曖昧幅を 2 セル化)。BMP 外 (絵文字 😀 / CJK 拡張) はサロゲートペアを左セル=高サロゲート・右セル (`wideCont`)=低サロゲートに分けて 2 セル格納する。**描画 (`TerminalRenderer.glyphAt`)・選択コピー (`getRangeText`)・行テキスト (`toText`) では左右セルを結合して 1 グリフとして扱う** (0.8.74)。以前は右セルを捨てて高サロゲート単独を描画/出力し、孤立サロゲート＝豆腐(?)になっていた。
  - SGR: 太字/下線/反転/取消線、16/256/RGB(truecolor)。
  - DEC モード: 代替画面、カーソルキー (DECCKM)、**マウスレポート** (X10/Normal/Button/Any × Legacy/SGR/urxvt)。
  - OSC: 7(cwd)/8(hyperlink)/10-12(前景/背景/カーソル色、`?` で query 応答)/52(クリップボード)/palette。OSC タイトルは UTF-8 デコード（日本語タブ名の文字化け防止）。
  - **URL/OSC8 リンクのセルに下線表示**。長い URL は折り返し元の行に wrapped フラグを持たせて検出（タップで開く）。
  - bracketed paste (DECSET 2004) 対応。
  - `cursorKeyBytes`, `encodeMouseEvent`, `resize`(cursor-aware), scrollback。
- `SearchEngine` (M11): スクロールバック全文検索。🔍 → 文字入力 → ↑↓ で前後ジャンプ。CJK は **セル列**でハイライト位置を計算。
  - 検索バーの入力欄は**内蔵キーボード時だけ自前描画** (`SearchQueryField`)。`BasicTextField(readOnly=true)` は OS IME を出さない代わりに**キャレットも出ない**ため、末尾の追記/削除しかできなかった。表示 (`Text`) + 点滅キャレットを自前で描き、キャレット位置 (`searchCursor`) を画面側の状態として持つ。タップ位置→文字位置は `TextLayoutResult.getOffsetForPosition`、キャレット x は `getHorizontalPosition`。**キャレット位置は必ず「そのレイアウト結果が実際に持つ文字列長」でクランプする** — 状態 (`query`) の更新とレイアウト結果の更新には 1 フレームのずれがあり、`query.length` で丸めると空レイアウトに対して `offset(n) is out of bounds` で落ちる (0.8.191 で修正)。内蔵キーボードの ←→ でキャレット移動 (↑=先頭 / ↓=末尾)、BS はキャレット直前を削除 (サロゲートペアは 2 code unit まとめて)。語が枠を超えたらキャレットが見える位置まで `horizontalScroll` を寄せる。**`Text` の末尾に 3dp の余白を入れる** — `horizontalScroll` は内容幅でクリップするので、余白が無いと末尾のキャレット (x = テキスト幅) がはみ出して**文字を打った瞬間に消える** (0.8.192 で修正)。システムキーボード時は従来どおり `BasicTextField` (OS IME 側がキャレットを描く)。
- `TerminalScrollbar`: 端末右端の掴めるスクロールバー。**タッチした瞬間から指に追従**させるため、`detectDragGestures` (タッチスロープ超過まで無反応) ではなく `awaitPointerEventScope` の自前ループで扱う。イベントは **`PointerEventPass.Initial` で受けて即 `consume`** する: Main パスまで残すと下に重なる `TerminalInputView` (AndroidView) に配られ、View 側が「処理した」として change を consume するため、`drag`/`detectDragGestures` は「他に取られた」と判断して即中断する。**移動量 `positionChange()` は `consume()` する前に読むこと** — consume 済みの change に対しては `Offset.Zero` を返す仕様なので、先に consume するとつまみが 1px も動かない (0.8.190/0.8.191 の「掴めるが動かない」の真因。0.8.192 で修正)。`pointerInput` の key は `Unit` 固定で、変化する値 (`scrollbackSize` / つまみ寸法) は `rememberUpdatedState` 経由で読む — key に `scrollbackSize` を入れると**端末出力のたびに検出器が作り直され、掴んだ指が外れる**。ドラッグ中のつまみ位置はローカル state に持ち、`scrollOffset` (StateFlow) → recomposition の往復を待たずに描く。当たり判定は見た目 (幅 8dp) より広い 32dp × 上下 +10dp。
- `TerminalBuffer`/`TerminalRow`/`TerminalCell`/`SgrAttribute`: セル格納とスクロールバック。
- `TerminalColors`/`AvailableThemes`: 9 テーマ (ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night / Catppuccin Mocha / Catppuccin Latte / Monokai)。

#### 文字列系シーケンスの吸収 (0.8.127)

DCS (`ESC P`) / APC (`ESC _`) / PM (`ESC ^`) / SOS (`ESC X`) は本文を **ST (`ESC \`) または BEL まで読み捨てる**専用状態 `State.STRING` を持つ。

**これが無いと何が起きるか**: 未対応のまま GROUND で受けると、開始バイト直後の本文 (key=value 並び・base64 payload・本文中の `\r` や CSI 風並び) がそのまま画面に流出し、以下 3 症状が同時に起きていた。

- 画像転送プロトコル本文の文字漏れ
- DCS 内 CSI 風並びの誤解釈による SGR mouse 風文字漏れ
- 本文中 `\r` が GROUND の CR として処理され、TUI 描画中に cursor が行頭へ飛ぶ

3 症状をひとつの状態追加で同時に止める。異常終端 (ESC + 非 `\`) は xterm 流儀でその時点で打ち切り、続くバイトを ESCAPE として再解釈する (`StringStateAbsorbTest`)。

#### Kitty graphics プロトコル

APC `ESC _ G <key=value,…> ; <base64 payload> ESC \` を `KittyGraphicsParser` で解釈する。段階 1〜10 で全スコープが揃っている。

| 段階 | 版 | 内容 |
|---|---|---|
| 1 | 0.8.128 | 最小描画 (`a=T,f=100,t=d` = transmit and display / PNG / direct base64) |
| 2 | 0.8.129 | アクション 4 種 (`a=T`/`a=t`/`a=p`/`a=d`) + 多 placement + 生 RGB(A) (`f=24`/`f=32`) |
| 3 | 0.8.130 | query 応答 (`a=q`) + quiet level (`q=0/1/2`) + Z-index (`z=N`) レイヤリング |
| 4 | 0.8.131 | Virtual placement (Unicode placeholder `U+10EEEE`) |
| 5 | 0.8.132 | image id の 32bit 拡張 (上位 8bit を underline color で受ける) |
| 6 | 0.8.133 | Animation frame の蓄積 (`a=f`) |
| 7 | 0.8.134 | Animation の再生 (frame 切替と delay 駆動) |
| 8 | 0.8.135 | zlib 圧縮入力 (`o=z`) と query 拡張 |
| 9 | 0.8.136 | file/temp/shm 転送 (`t=f`/`t=t`/`t=s`)。**opt-in・既定 OFF** |

**描画とライフサイクル**
- 画像はカーソル行を **anchor** (top-left) とする `TerminalImage` として `TerminalRow.images` (`MutableList`) に格納し、同一 anchor 行に異なる `(imageId, placementId)` の placement を並列保持できる。同じ組が再度来たら**置換** (位置上書き)
- 画像セル数は `c=N` / `r=N` があればそれ、無ければ Bitmap のピクセル数を Renderer から渡された `cellW`/`lineHeight` のヒントで割って自動算出
- 配置と同時にカーソルは画像の幅セルぶん右へ進める (改行は TUI が `\n` で送る前提)
- 文字書込み (`setChar`) / `clear` / `resize` (列幅縮小で anchor + width が範囲外) が起きると、**セル範囲に被さる placement だけ**を除いて invalidate する (他の placement は残す)
- 行コピー (`TerminalRow.copyFrom`) では画像も一緒に運ぶので、`DECSTBM` 領域内スクロール等で画像が一行ずれてもキャンバスに残る
- `TerminalBuffer` に**画像キャッシュ** (`imageId → Bitmap`) を持ち、`a=T`/`a=t` で登録、`a=p` で参照、`a=d,d=I`/`a=d,d=i` でエントリも削除する

**Z-index による 2 層描画**: `zIndex < 0` の placement は**テキストの下層** (Pass 2.7、文字を画像の上に読みやすく重ねる)、`zIndex >= 0` は**テキストの上層** (Pass 3.5、アイコン重ね・吹き出し風)。同 z 内の順序は追加順 (= 後勝ち)。

**Unicode placeholder (virtual placement)**: `a=p,U=1` / `a=T,U=1` は image を grid (`c=N` × `r=N` 分割) として `TerminalBuffer.virtualPlacements` に登録するだけで cursor を動かさない。実際の描画位置は本文に書かれる placeholder セル (`U+10EEEE`) と、直後に並ぶ最大 3 個の **combining diacritic** (Kitty 固定 297 要素表で row / col / placement id 下位 8bit をエンコード) で決まる。placeholder セルは `TerminalCell.placeholder: PlaceholderRef?` にメタを持ち、image id は fg truecolor (`\e[38;2;R;G;B`) から 24bit ＋ underline color の R 値から上位 8bit で計 32bit を組む。Renderer は Pass 2.7 / Pass 3.5 で row 内のセルを走査し、placeholder ごとに `virtualPlacements` を逆引きしてタイル領域 (`srcCol/widthCells, srcRow/heightCells`) を `drawBitmap` の srcRect→dstRect で 1 セル矩形へ切り出す。placeholder セルは `TerminalRow.toText` / `TerminalBuffer.getRangeText` でコピー時に空白へ置換する (孤立サロゲートの混入防止)。仕様: <https://sw.kovidgoyal.net/kitty/graphics-protocol/#unicode-placeholders>

**外部ファイル転送のセキュリティ (0.8.136)**: 既定 OFF の opt-in (`AppSettings.kittyExternalFileEnabled`、DataStore key `kitty_external_file_enabled`)。ON かつ rootfs 解決可能のときだけ `KittyHostTransferSource` を `TerminalEmulator.setKittyExternalTransfer` に注入する (OFF へ戻せば null で外す動的反映)。多層防御:

- opt-in OFF が既定なので未許可セッションは parser レベルで丸ごと止まる
- file/tempfile はゲスト絶対パスを `<rootfsRoot>/<guest path>` に、shm 名 `/<name>` は `<rootfsRoot>/dev/shm/<name>` に rebase＝**rootfs 配下に限定**
- path traversal (`/../`) は文字列段階で reject し、`canonicalFile` で rootfs 配下に収まることも 2 段目で再確認
- 1 回の読込上限 **16 MiB** (zip-bomb / DoS 対策)
- `TempFile` は読了後 `delete()` で unlink
- ファイル長を超える offset / 上限超過 size は null で拒否

**zlib 展開 (`o=z`)**: `inflateZlib(bytes)` が `java.util.zip.Inflater` で展開し、`maybeInflate(header, raw)` が `o=z` のときだけ挟む (`o` 未指定は透過)。16 MiB を越えた時点で打ち切り `Discard`。生 RGB(A) のサイズ検証 (`s` × `v` × `bpp` を超える/不足する payload は `Discard`) が**展開後にも走る**ので、「圧縮された payload で `s`/`v` をごまかす」攻撃にも耐える。

<details>
<summary><b>Kitty graphics 実装の経緯 (0.8.128〜0.8.136・段階 1〜10)</b></summary>

**0.8.128 最小描画 (段階 1)**: `a=T,f=100,t=d` の単発と `m=1` 連続 + `m=0`/省略 終端のチャンク連結に対応。`a=d` は全画像消去にマップ。画像はカーソル行を anchor とする `TerminalImage` として `TerminalRow.image` に格納し、Renderer は anchor 行を描く回で `widthCells × heightCells` の矩形に `drawBitmap` で伸縮描画する (背景描画と文字描画の間)。

**0.8.129 アクション拡張 + 多 placement + 生 RGB(A) (段階 2)**: アクション別 4 種 `a=T` (transmit and display) / `a=t` (transmit only＝キャッシュ登録のみ) / `a=p` (put existing image＝キャッシュ参照で別位置に再配置) / `a=d` (詳細削除: `d=A` 全消去 / `d=I,I=N` または `d=i,i=N` で image id 単位 / `d=p,i=N,p=N` で特定 placement のみ) に拡張。Bitmap 入力を **`f=24` (生 RGB, 3 bytes/px)** と **`f=32` (生 RGBA, 4 bytes/px)** へ拡張し、`s=N`/`v=N` のピクセル幅高から `Bitmap.createBitmap(IntArray, …, ARGB_8888)` で組み立てる (PNG は引き続き `BitmapFactory.decodeByteArray`)。多 placement 対応として `TerminalRow.image: TerminalImage?` を `images: MutableList<TerminalImage>` へ変更。invalidate を「セル範囲に被さる placement だけ」の精度に上げた。animation / virtual placement / Unicode placeholder / file 転送は引き続き範囲外 (`Result.Discard`)。`KittyGraphicsParserTest` を 12 ケースへ拡張。

**0.8.130 query 応答 + quiet level + Z-index (段階 3)**: TUI 側のケイパビリティ確認 (`a=q`) に対し、サポートしている format/transmission の組み合わせなら `ESC _ G i=<id> ; OK ESC \`、未対応なら `ENOTSUPPORTED:<reason>` を `output` 経由で返す。quiet level (`q=0/1/2`) に従って応答を抑制 (q=0 全部・q=1 エラーのみ・q=2 無音)。`z=N` を `TerminalImage.zIndex` まで通し Renderer の 2 層描画へ。`KittyGraphicsParserTest` に 4 ケース追加して 16 ケース。

**0.8.131 Virtual placement (段階 4)**: `a=p,U=1` および `a=T,U=1` で virtual placement を登録する経路を追加。削除コマンド (`a=d,d=A`/`d=I`/`d=p`) は通常 placement と同じく仮想 placement 登録も消す。`KittyGraphicsParserTest` を 18 ケースへ拡張 + 新規 `KittyPlaceholderCellTest` 6 ケース。

**0.8.132 image id の 32bit 拡張 (段階 5)**: 0.8.131 の placeholder セルは image id を **fg truecolor から 24bit** までしか取れず、多数の画像を同一セッションで扱う TUI で id 衝突が起こり得た。Kitty 仕様の**上位 8bit を underline color で渡す**経路に対応。`SgrAttribute` に格納場所は増やさず `TerminalEmulator` 側に `currentUnderlineColor: Int` を持ち、SGR 58:2:R:G:B (RGB underline) / 58:5:idx (indexed underline) / 59 (reset) / 0 (full reset) を `applySgr` でパース。`putKittyPlaceholder` で `isRgb(currentUnderlineColor)` なら R 値を上位 8bit として OR する。underline 自体の描画は引き続き行わない (id 受け渡し専用)。`KittyPlaceholderCellTest` に 3 ケース追加。

**0.8.133 Animation frame の蓄積 (段階 6)**: `a=f` (frame transmit) を受領するところまで。**蓄積のみで実描画は次段**。新規 `AnimationFrame` (`bitmap` + `delayMs` + `composeMode` + `xOffset` / `yOffset`) を `TerminalImage.kt` に追加し、`TerminalBuffer` に `animations: Map<imageId, MutableList<AnimationFrame>>` を新設。`addAnimationFrame` / `getAnimationFrames` で追記・取得し、`clearAllImages` / `deleteImageById` で連動削除。`KittyGraphicsParser` は action `f` 経路 (`handleFrame`) を新設し `Result.Frame(...)` を返す。⚠️ **Kitty 仕様では `a=f` のときだけ `z=N` が delay (ms) を意味する** (それ以外は Z-index) ため parser でアクション別に振り分ける。`i=N` 必須・`t=d` のみ・Bitmap 組立失敗時は `Discard`。3 ケース追加して計 21 ケース。

**0.8.134 Animation の再生 (段階 7)**: 0.8.133 の蓄積だけでは描画が常に frame 0 (= `imageCache` の原画像) でアニメーションが動かなかった。`TerminalBuffer` に private class `AnimationPlaybackState(currentFrame, lastSwitchMs)` と `animationStates: Map<imageId, AnimationPlaybackState>` を新設。描画前に Renderer から呼ぶ `advanceAnimations(nowMs: Long): Boolean` で「現在 frame の delay を超えたら次 frame へ、末尾の次は frame 0 へループ」する単純な state machine を回す (frame 0 の delay は `frames[0].delayMs` で代用)。`currentBitmap(imageId): Bitmap?` が「再生中なら現在 frame、それ以外は原画像」を返し、`drawImagePlacement` / `drawPlaceholderTiles` がこれを引いてから `drawBitmap` に渡す (引けなければ `img.bitmap` / `spec.bitmap` フォールバック)。`addAnimationFrame` で該当 imageId の state を `remove` する (新フレームが来たら frame 0 から再生し直し)。駆動は `TerminalRenderer` 内の `LaunchedEffect(session.id)` で、`hasActiveAnimations()` が true の間だけ `withFrameMillis` で同期して `advanceAnimations` を呼び、state が変わったら `animTick` を bump して recomposition を引く。アイドル時は 100ms ごとに polling (HashMap.isEmpty で無視できる cost)。新規 `AnimationPlaybackTest` 3 ケース。frame 投入経路は Bitmap 構築が unit test 環境で動かないため実機検証へ繰り越し。

**0.8.135 zlib 圧縮入力 (段階 8)**: 0.8.134 までは base64 デコード直後をそのまま PNG / RGB / RGBA 入力として扱っていたため、`chafa --format kitty --compress` や大きい画像を送る TUI が `o=z` を有効にすると payload が解釈不能で画像が表示されなかった。`handleTransmit` (a=T/t/p) と `handleFrame` (a=f) で base64 デコード後に必ず `maybeInflate` を通す。`o=z` 以外の `o=` 値 (将来仕様用) は `null → Discard`。`a=q` も `o=` を見るよう拡張し `o=z` は OK、それ以外は `ENOTSUPPORTED:o=<x>`。`KittyGraphicsParserTest` を 25 ケースへ拡張。残スコープは file/temp/shm 転送のみ (security 要検討で当面保留) だった。

**0.8.136 file/temp/shm 転送 (段階 9・opt-in)**: 0.8.135 まで `t=f`/`t=t`/`t=s` は一律 `Discard`、`a=q` も `ENOTSUPPORTED:t=…` を返していた。image viewer 系の TUI は **base64 ではなく rootfs 上のファイルパス**で画像を送る設計 (大きい PNG の base64 inline はメモリ/CPU が嵩むため) で、これに乗らないと「ファイルベースで送る系は何も出ない」状態だった。`KittyGraphicsParser` に `enum TransferKind { File, TempFile, SharedMemory }` と `fun interface ExternalTransferSource { fun read(kind, name, offset, size): ByteArray? }` を追加し、フィールド `externalTransferSource` で射出口を持つ。`handleTransmit`/`handleFrame` は base64 → inflate のロジックを `obtainPayloadBytes(header, payloadStr)` に括り出し、`t=d` は従来通り base64 → maybeInflate、`t=f`/`t=t`/`t=s` は base64 でパス文字列を取り出して `source.read(kind, name, O, S)` に委譲し戻り値に `maybeInflate` を適用する形に統一 (`O=N` / `S=N` = Kitty 仕様の offset / size もこの経路で渡る)。`a=q` も source が注入済みなら OK、未注入なら `ENOTSUPPORTED:t=…`。ホスト側実装は新規 `KittyHostTransferSource(rootfsRoot)` (`emulator/KittyHostTransferSource.kt`)。⚠️ `android.util.Base64` は unit test で stub されない (= 委譲確認テストが動かない) ため、base64 デコードを `java.util.Base64.getDecoder()` に切替えた (minSdk 29 = Java 8 同等で利用可、Kitty 仕様は標準 base64 なので互換)。設定 UI は「実験的 / 開発者向け」セクションに「Kitty graphics: 外部ファイル転送」トグル + 警告文 (`settings_kitty_external_file_*` strings, ja/en)。テスト: `KittyGraphicsParserTest` を 30 ケースへ拡張 + 新規 `KittyHostTransferSourceTest` 12 ケース (read 全長/offset+size/負 size=末尾/TempFile 自動 unlink/shm の `/dev/shm` rebase/`..` 拒否/絶対パス必須/未存在/offset 超過/0 slice/上限超過拒否)。**これで Kitty graphics 段階 1〜10 が揃う**。実機検証は別途。

</details>

#### SGR mouse 入力 (タップ→マウスイベント変換)

タッチ操作を SGR mouse (`\x1b[<n;col;row>M/m`) として PTY master へ流す経路。**mouse capture 中のタップ→click は常時有効**、それ以外は既定 OFF の opt-in (`AppSettings.sgrMouseInputEnabled`、DataStore key `sgr_mouse_input_enabled`) という段階構成になっている (0.8.137 → 0.8.138 で確定)。

| 操作 | 送出 | 条件 |
|---|---|---|
| 1 指タップ | button 0 の press+release (`\x1b[<0;col;row M` + `…m`) | **opt-in 不要**。`sess.emulator.mouseEnabled` が ON なら送る |
| 1 指長押し | button 2 の press+release (右クリック相当) | opt-in ON のときだけ |
| 1 指ドラッグ | button 0 press + button 32 motion 連発 + button 0 release | opt-in ON のときだけ。BUTTON_EVENT/ANY_EVENT 必須 (NORMAL は motion を捨てる既存仕様で安全) |
| 2 指スワイプ | wheel (button 64/65) | opt-in に関係なく従来通り |

- `TerminalInputView` が `sgrMouseDragActive` / `sgrMouseLastCol/Row` の drag 状態を持ち、`onScroll` でセル変化時のみ motion を発行する (同セル内の連続 motion の流量制御)
- `onTouchEvent` の ACTION_UP/ACTION_CANCEL で必ず release を送り、TUI 側の press 状態 stuck を防ぐ (drag 中に view 外へ抜けた場合は最後の有効セル位置で release)
- ヘルパ `isSgrMouseInputActive(sess)` が「opt-in ON かつ TUI が `?1000`/`?1002`/`?1003`/`?1006` で mouse capture 中」を一元判定する
- opt-in ON では 1 指 swipe が drag に振り替わるため `e2.pointerCount == 1` でガードし、2 指以上の swipe は既存 wheel 経路へ流す
- opt-in OFF (既定) では長押し/ドラッグは Z2Term 自身の操作 (フォーカス / テキスト選択 / scrollback スワイプ) に使う
- 設定 UI: 「実験的 / 開発者向け」セクションの「SGR mouse 送出 (タッチ→マウスイベント変換)」トグル + 警告文 (`settings_sgr_mouse_input_*` strings, ja/en)。反映は即時 (combine 監視・再起動不要)
- 仕様: <https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#Mouse_Tracking> および xterm `ctlseqs.txt` の "Any-event tracking" / "SGR (1006) mouse"

<details>
<summary><b>SGR mouse 入力の経緯 (0.8.137 → 0.8.138)</b></summary>

**0.8.137 opt-in として追加**: 0.8.116 / 0.8.119 / 0.8.124 / 0.8.126 で**ホイール送出 (button 64/65)** までは入っていたが、1 指タップ / ロングタップ / 1 指ドラッグを SGR mouse として流す経路は未実装で、mouse capture を要求する TUI (カレンダー pane / ファイラ / 複数 pane フォーカス切替) が「タップしても何も起きない」状態だった。3 種の送出を既定 OFF の opt-in として追加。テスト: `MouseEncodeTest` を 10 → 14 ケースへ拡張 (右クリック press/release のバイト列固定 / 1 指ドラッグ motion の button 32 + 'M' 終端固定 / NORMAL は motion を抑止して null / BUTTON_EVENT で motion 許可)。既存の wheel / left click / 各 encoding / DECRST 連動の 10 ケースは退行なし。

**0.8.138 タップだけ opt-in から切り離し**: 0.8.137 で `sendMouseClick` の発火条件を `isSgrMouseInputActive` (opt-in 必須) 配下に閉じ込めた結果、既定 OFF だと mouse capture を有効化する TUI で**タップが届かない** microregression が出た (0.8.116〜0.8.136 では `mouseEnabled` だけで自動送出していた)。`TerminalInputView.onSingleTapUp` の判定を `sess.emulator.mouseEnabled && sendMouseClick(...)` に戻し、「mouse capture 中はタップ→SGR click を opt-in 関係なく送る」を復活。ロングタップ→右クリックと 1 指 drag→motion は opt-in 配下に残す。これで OFF (既定) の挙動が 0.8.116〜0.8.136 と同じベースになり、opt-in ON で右クリック / drag motion が追加される段階構成として整理された。

</details>

#### SGR underline サブパラメータ (`4:n`) の解釈 (0.8.139)

CSI パラメータの `:` 区切り (サブパラメータ) を `;` 区切りと同一視していたため、styled underline (波線/二重/点線/破線) を使う TUI が送る `\e[4:3m` を `[4,3]` と解釈していた。

| 送られたもの | 誤った解釈 |
|---|---|
| `\e[4:3m` | 下線 + **イタリック** |
| `\e[4:1m` | 下線 + **ボールド** |
| `\e[4:5m` | 下線 + **点滅** |
| `\e[4:0m` (下線オフ) | **全属性リセット (前景/背景色まで消去)** |

余計な装飾フラグが居残り、styled underline を使う TUI を抜けたあと下線などが残る症状になっていた。

修正＝`csiParamIsSub` を追加して各パラメータが `:` サブパラメータか `;` 区切りかを記録し、`applySgr` の `4` を「サブパラメータ付きなら `0`=下線オフ・それ以外=下線オン、サブパラメータは必ず読み飛ばす」に修正 (`4` 単体は従来どおり単線下線、styled 種別は描画上区別せず一律下線)。`SgrUnderlineSubparamTest` / `SgrUnderlineAltScreenExitTest` で回帰を固定。

#### マウスレポート ON 時のスワイプ振り分け

マウスレポートが ON (`?1000`/`?1006` 等で TUI 側が要求) の間、`TerminalInputView` のスワイプを**画面種別・方向・scrollback 位置・PTY 前景プロセス**で振り分ける。

| 画面 | 指の方向 | 条件 | 動作 |
|---|---|---|---|
| **alt screen** | 両方向 | — | PTY へ wheel (指を上=wheel-down=button 65 / 指を下=wheel-up=button 64) |
| **primary** | 上 (次へ進めたい) | `scrollOffset == 0` かつ**前景プロセスがシェル以外** | wheel-down を送る |
| **primary** | 上 | `scrollOffset > 0` (過去ログを見ている途中) | wheel ではなく scrollback の「最新側へ戻る」操作として吸収 |
| **primary** | 下 (過去を見たい) | 常に | scrollback 操作にフォールバック |

**それぞれの理由**
- **alt screen が両方向 (0.8.119)**: alt screen は scrollback が無いので、下方向スワイプが scrollback フォールバックに落ちると `scrollbackSize == 0` ゆえ無反応となり「下にしかスクロールできない」状態になる
- **前景プロセス判定 (0.8.126)**: `tcgetpgrp` ベース。`mouseEnabled` が stale で残っていてもシェル前景なら wheel を流さず scrollback へ倒す。stale 状態がプロンプトに `\e[<...M` を流出させる症状を防ぐ
- **`scrollOffset > 0` を吸収 (0.8.116)**: これをしないと wheel 送信時の `TerminalSession.writeBytes` が scrollback を 0 にリセット＝「いきなり最下端へジャンプ」する違和感の原因になる
- **primary の下方向は常に scrollback**: 多くの読み物 TUI が wheel-up を「端末 scrollback に任せる」設計で無視するため、上向きのみ TUI へ届ければよい

**ノッチ換算**: 指の累積 dy が `MOUSE_WHEEL_STEP_PX (=40px)` を超えるごとに 1 ノッチ送り、長いスワイプはその回数ぶんの多行送りになる (alt では符号付きで蓄積し方向反転も自然に吸収)。

**フリング**: 同じ条件分岐で、primary では `mouseEnabled && velocityY < 0 && scrollOffset==0` のときだけ no-op、それ以外は scrollback 慣性スクロール。alt では `sendMouseWheelRows` で慣性ぶんも PTY へ wheel 変換し、**座標はフリング開始位置の指のセルをそのまま継承する** (0.8.124。複数ペインを持つ TUI が wheel の (col,row) で対象ペインを判定する設計のため、画面中央固定だと触れていないペインが慣性段階で勝手にスクロールする副作用が出る)。

#### スクロール領域 (DECSTBM)

改行スクロール (`lineFeed`/IND) は、**領域が画面全体のときだけ**最上行を scrollback へ送る通常スクロールを行う。`DECSTBM` でカスタム領域が設定されているときは**領域内だけをスクロール**し、領域外の固定行は動かさない・scrollback にも送らない (0.8.105)。

**修正前の症状**: 領域を無視して全画面 scrollUp を呼んでいたため、下部のステータス/コマンド行 (行番号・ルーラ表示) を `DECSTBM` で固定したまま改行を続ける TUI で、固定行が毎回 1 行ずつ押し上げられ「毎行に行番号が焼き付く」不具合になっていた。`ScrollRegionLineFeedTest` で回帰を固定。

`IL`/`DL`/`SU`/`SD`/`RI` は元から領域対応済み。

### 4.6 ドメイン (`core/`)

- `SessionManager` (object): `TerminalSession` のリスト + active を `StateFlow` で公開。`ensureFirst`/`openNew`/`close`/`setActive`/`moveSession`（タブのドラッグ並べ替え）。`close` は先に UI からタブを外し、停止処理 (PTY/SSH 切断・GUI=Xvnc 停止) は裏で実行してタブ消去のもたつきを防ぐ。
- `TerminalSession`: 状態機械 `IDLE→INSTALLING→STARTING→RUNNING→EXITED/ERROR`。
  - emulator 専用 dispatcher、PTY 読みループ、`writeBytes`、resize、`startTerminal`/`switchDistro`/`restart`/`reinstallDistro`/`startSsh`。
  - **起動 distro はレース回避のため永続値を await**: `settingsFlow` は `stateIn(Eagerly)` の初期値が既定 Snapshot (`distroId=alpine`) なので、アプリ更新・端末再起動直後など DataStore の初回 emit が届く前に `startTerminal` が走ると、選択中の OS ではなく既定 Alpine で起動してしまうレースがあった（「希に Alpine が立ち上がる」現象）。`startTerminal` 内で `settings.flow.first()` を await してから distro を決定し、確実に選択中の OS を起動する（0.8.105）。
  - `StateFlow`: uiState / redrawTick(≈60fps コアレッシング) / scrollOffset / cellMetrics / selection / cwd / label / settingsFlow。
- `TerminalSelection` / `CellMetrics`: 選択範囲 (絶対行) と 1 セル寸法。
- `clipboard/ClipboardHistoryStore` (object): システムクリップボードは 1 件しか持てないので、変化を拾って履歴 (最大 50 件 / `filesDir/clipboard_history.json`) に貯める。取り込み経路は 3 つ: ①`OnPrimaryClipChangedListener` (前面中の変化)、②`MainActivity.onResume`、③`MainActivity.onWindowFocusChanged(true)`。Android 10+ の「クリップボードを読めるのはフォーカスのあるアプリだけ」制限は**ウィンドウフォーカス基準**で、`onResume` の時点ではまだフォーカスが確定せず空が返る端末があるため、③が無いと「他アプリでコピー → 戻る」を取りこぼす。裏で複数回コピーされても拾えるのは最後の 1 件だけ (OS の仕様上の限界)。重複は `record` が先頭一致/LRU で潰す。
- `SessionStore`/`SessionManager` (M11): タブ構成 `{id,label,distro,cwd}` + activeId を DataStore に保存する（書き込みのみ）。**0.8.70 で起動時の自動復元を無効化**＝起動の度に複数タブが開く挙動を避けるため、`ensureFirst` は常に新規 1 タブだけを開く（ユーザー要望）。`save` は将来の復元 UI / デバッグ用に残すが読み戻し経路は持たない。**cwd は OSC7 で捕捉**（`ensureOsc7CwdConfig` が bash/zsh のプロンプトフックで OSC7 を吐かせる）。

### 4.7 通信チャネル (`channel/`)

- `ProcessChannel` (interface): `reader`/`writer`/`isAlive`/`exitCode`/`resize`/`close`。
- `LocalPtyChannel`: PtyProcess をラップ (ローカル proot)。
- `SshChannel`: JSch でリモート接続。`shell` チャネル + `-L` ローカルポート転送、host key 検証 (`KnownHosts`/`HostKeyVerificationDialog`)、鍵は Keystore で暗号化 (`KeystoreCrypt`)。
- `SshProfile`/`PortForward`: DataStore (`z2term_ssh`) に JSON 永続化。

### 4.8 設定 (`settings/AppSettings.kt`)

- DataStore (`z2term_settings`) を `Snapshot` データクラス + `Flow` で公開。各 setter は suspend。
- 項目は[§7](#7-設定項目)。

### 4.9 常駐サービス (`service/TerminalService.kt`)

- `foregroundServiceType=specialUse`。`start`/`detach`(常駐解除のみ・セッション維持)/`stop`(全終了)。
- `PARTIAL_WAKE_LOCK`、通知 (`ic_notification` = 透過マスクの **「Z」1 文字**、タップで復帰 / 停止アクション)。**小アイコンは色が使えない (不透明部分が一律にティントされる) ので、形でしか区別できない**。「Z2」の 2 文字はステータスバーの実表示 (24px 前後) で潰れて読めず、0.8.196 でランチャーと同じ `>_` にしたところ**他の主要ターミナルアプリと同じシルエット**になり、通知を畳んだ状態でどちらのアプリか見分けられなかった。そこで 0.8.200 で通知だけ **Z 1 文字**へ。斜めのストロークが主役なので山括弧 `>` と輪郭が根本的に違い、要素が 1 つだけなので小さい表示に強い。ランチャーアイコン (`>_`) は大きく表示されるため、そちらがターミナルらしさを担う。SAF プロバイダのルートアイコンにも同じものを使う。

### 4.10 ファイル連携 (`saf/Z2TermDocumentsProvider.kt`)

- `DocumentsProvider` (authority `<applicationId>.documents`、`permission=MANAGE_DOCUMENTS`)。
- 公開ルート: **ホーム = `shared_home`** (端末の `/root` と同一実体) + 各 distro の rootfs(`/`)。
- traversal 防止: 許可ルート `[shared_home, distros]` 配下のみ。R/W/作成/削除/リネーム対応。

### 4.11 UI 詳細 (`ui/`)

- `terminal/TerminalScreen.kt`: 全体レイアウト。TopBar / TabBar / 描画領域 / キーボードトグル / キーボード領域。`KeyboardMode = CUSTOM | SYSTEM`。**横画面**は `LocalView.OnLayoutChangeListener` で向きを検知し、`landscapeKeyboardPosition`/`Width`/`Height` 設定に従って Row レイアウト (`SideKeyboardColumn`) に切替。`landscapeScaledStyle()` で keyHeight/font が横画面高さに比例拡縮。
  - **キーボードトグルバー (`KeyboardToggleBar`)**: タップでキーボード表示/非表示を切り替える 22dp 高の細いバー。**キーボードの上に配置**（端末タブ・GUI タブ共通）。設定 `keyboardToggleBar`（既定 ON）で表示/非表示を選べ、**OFF にするとバーを出さず ⌨ ツールバーボタンのダブルタップで表示/非表示を切り替える**（0.8.145。単タップ=キーボード切替は従来どおり。0.8.144 で一時キーボードの下へ移したが使いにくく上へ戻し、代わりに設定＋ダブルタップ方式を追加）。ラベルは表示/非表示どちらの状態でも「キーボード」を出す（`▴ キーボード` / `▾ キーボード`。従来は非表示側が `▾` のみ＋16dp 高で文字が縦に見切れていた）。`.clickable` の touch slop (約 8dp) だけではフリック入力中に指がバーへ掠めて誤って非表示が発動することがあったため、自前の `pointerInput` ジェスチャで **down からの累積移動が 24dp を超えたら onToggle を抑制**し、純粋なタップ（24dp 未満）でのみトグルするように変更（0.8.109。従来は touch slop 越えで `.clickable` が発火しないものの、短いドラッグが偶発的にタップ判定に流れて非表示になっていた）。
  - **ツールバー (`ReorderableToolbar`)**: 📋貼付 / 📜コマンド / 💡画面消灯ロック / 🔒バックグラウンド常駐 / 🔍検索 / ⌨キーボード切替 を `ToolbarItem` のリストで描く。**通常タップ=動作、長押しドラッグで並べ替え** (`detectDragGesturesAfterLongPress` + 隣との中心越えで `order` 入替)。長押し中は `ToolbarTooltip` で簡易説明を Popup 表示。並びは `AppSettings.toolbarOrder` (カンマ区切り id) に永続化し、`mergeToolbarOrder` で既存順とマージするのでボタン追加/削除でも壊れない。🔒常駐は既定で 💡 の右。GUI タブ (`GuiTopBar`) も同 `ReorderableToolbar` を共有 (検索なし・📋/📜 は keysym 橋渡し)。
  - **🔒 常駐トグルは常駐サーバー稼働中はロックする** (0.8.204)。常駐サーバー (`ServerDaemonService`) が動いている間はプロセスが生き続けるため、🔒 を OFF にしてもセッションは消えない (最近履歴からスワイプしてもプロセスは死なない)。そこで `ServerDaemonManager.isRunning` を 1 秒周期でポーリングし、稼働中は 🔒 を **ON 表示のまま薄く (`ToolbarChip(dimmed=true)`) してトグル不可**にする。この間タップするとトグルの代わりに `ResidentActionDialog` を開き、「常駐に閉じ込められない」ための出口を出す — **セッションだけ終了** (`SessionManager.resetToInitial`。常駐サーバーはそのまま) / **全部停止して終了** (`ServerDaemonService.stop` + `SessionManager.shutdown` + `TerminalService.stop` + `finishAndRemoveTask`＝タスクキル相当)。ロック条件を常駐サーバーに限るのは、検知系 FG サービス (システムイベント/通知) は WakeLock を握らず 🔒 の「CPU を起こし続ける」独自価値が残るのに対し、常駐サーバーは同じ WakeLock/WifiLock を握るため 🔒 が完全に無意味になるから。端末タブ (`TopBar`)・GUI タブ (`GuiTopBar`) 共通 (`keepAliveToolbarItem`)。**0.8.211 で設定側にも同じロックを入れた**: ツールバーから🔒を隠していると設定 › ツールバーの代替トグルが唯一の操作口になるが、そこがロック対象外だったため「常駐中はセッションを終了できない」状態が残っていた (実機で指摘)。`ToggleField` に `locked`/`onLockedTap` を足し、`ServerDaemonManager.isRunning` の 1 秒ポーリングで薄く+トグル不可にし、タップで同じ `ResidentActionDialog` を開く (`stopEverythingAndQuit` は `internal` へ)。**0.8.225 で「全部停止して終了」が `SystemEventService.stop` も呼ぶようにした**: **フォアグラウンドサービスは 1 つでも残っているとプロセスが死なない**ため、システムイベント検知を ON にしていると押してもアプリが閉じなかった (実機で指摘)。FG サービスを増やしたら必ず `stopEverythingAndQuit` へ足すこと。設定 (`systemEventCaptureEnabled`) は触らないので、次にアプリを開けば検知は再開する (「今回は全部止めたい」と「検知をやめたい」は別の意思なので、設定を書き換えない)。
  - **⚙設定は並べ替えにも非表示指定にも入れず、ツールバーの右端に固定**する (0.8.194)。`ReorderableToolbar` の外に `ToolbarChip` を 1 個直接置く形で、他をどう並べ替えても・どれだけ隠しても位置が動かない。
  - **出すボタンをユーザーが選べる** (0.8.194)。非表示 id は `AppSettings.toolbarHidden` (カンマ区切り) に永続化し、設定 › 表示 › **ツールバー**で切り替える。ボタンの一覧 (id / 代表アイコン / 説明 / 隠せるか) は `ui/terminal/ToolbarButtons.kt` の `CATALOG` に集約し、表示側と設定画面で同じ定義を共有する。**⚙ は `canHide = false`** — 隠せると設定画面へ戻る手段が無くなるため。**並べ替えの保存値には隠しているボタンの id も残す** (`persistOrder`): 表示中のものだけを保存すると、隠して出し直したときに末尾へ飛んでしまう。
    隠されたボタンのうちトグル系 (🔅 画面消灯ロック / 🔒 常駐) は**ツールバー以外に操作する場所が無い**ので、隠しているときだけ同じ「ツールバー」セクション内にトグルを出す。機能追加でボタンが増えても各自の画面は増やさない、という方針の受け皿でもある。
- `terminal/TerminalRenderer.kt`: ネイティブ Canvas に **セル単位 drawText** (advance≠cellW のサブピクセル誤差累積を回避)。背景→選択ハイライト→文字→カーソル→選択ハンドルの順。
- `terminal/input/TerminalInputView.kt` (AndroidView): 物理キー/OS IME 入力、ジェスチャ (タップ/長押し選択/ドラッグスクロール/ピンチ拡縮/マウスクリック送出)。選択は[§6.5](#65-テキスト選択-ux)。
- `terminal/keyboard/`:
  - `TerminalKeyboard.kt`: 5 行独自キーボード。3 状態 Shift、フリック、全キー長押し連打。**押下時にキー背景を明るい緑に**、**フリック中はしきい値超えた方向のヒントを太字 + 1.6 倍拡大** (中央文字は不変)。
  - `JapaneseFlickKeyboard.kt`: 内蔵 日本語/カタカナ フリック。同じプレス/フリック視覚フィードバック。
  - `KeyboardStyle.kt`: COMPACT(44dp) / SPACIOUS(60dp、4 方向フリック)。`naturalHeight`。`.copy()` で横画面用に拡縮済 style を作る。
  - `KeyGestures.kt`: タップ + 長押し連打の共通ジェスチャ (`onPressedChange` コールバックで press 状態を Composable に伝える)。
  - `components/SpecialKeyBar.kt`: OS IME 時の特殊キー列。
- `settings/SettingsSheet.kt` + `SshAccessHelper.kt`: 設定ページ (全画面) + SSH/ストレージ ヘルパー。
  - 項目は **8 グループのアコーディオン** (`settings/SettingsGroup.kt`) に束ねる: 表示 / キーボード / 入力・言語 / Linux 環境 / 常駐サーバー・自動化 / メンテナンス / 開発者向け / このアプリについて。宣言順が表示順。開閉状態は `settings/SettingsGroupStore.kt` が `settings_group_open_<id>` の固定キー 1 本ずつで DataStore に永続化する (グループを増減しても既存の状態が壊れない。保存が無いグループは `defaultOpen` にフォールバック)。閉じている間は中身を composition しない。見出し行は「タップできる場所」だと分かるように**カード背景 + 1dp の枠**（他のタップ可能カードと同じ意匠）を付け、**開いている間は枠と背景をアクセント寄り**にして開閉状態も色で読めるようにする (0.8.184。それ以前は文字と ▸/▾ だけで、周囲の項目と見分けが付きにくかった)。
  - **端末リセット**は `SessionManager.resetToInitial()` を呼び、**端末タブ 1 つだけを残して他タブ (端末・GUI) を全部閉じ**、残した 1 つを `TerminalSession.restart()` で初期化する (= アプリ初回起動時の状態)。タブ数や動作中かに関わらず**常に**確認ダイアログを挟み、実行後はトーストで結果を出す。設定値・常駐サーバー・rootfs には触れない。
- `ssh/SshProfilesSheet.kt` + `HostKeyVerificationDialog.kt`: SSH プロファイル UI + 鍵検証。
- `sftp/SftpSheet.kt`: SFTP ファイルブラウザ (**全画面ページ**)。一覧の下方向スクロールが ModalBottomSheet の「閉じる」ドラッグと競合して勝手に閉じるため、設定ページと同じ別ページ方式に変更した。戻る矢印 / システムバックで前の画面へ戻る。他のシート (スニペット・クリップボード履歴・サーバー・独自テーマ) は 「一時的に開く」用途なので ModalBottomSheet のまま。
- `snippets/SnippetsSheet.kt`: ツールシート (ツールバー 📜)。タブで **スニペット** (1 行タップで挿入、並替/編集) / **SSH・SFTP** (`ssh/SshProfilesBody`) / **サーバー** (`settings/ServersBody` = 常駐サーバー管理を設定シートと共有) を切替える。SSH タブは端末タブのみ、サーバータブは端末セッションがあるときだけ出る。

### 4.12 GUI デスクトップ (`gui/`)

- distro 内で **Xvnc**(VNC サーバ) + 軽量 WM/アプリを起動（`proot/GuiScript.kt` が冪等で配置・起動。GUI 自動起動 / 横画面対応）。
- **GUI 一式の導入 (`ensure_pkgs`)**: Xvnc / openbox / 選択ターミナルが揃っていれば**無通信で即起動**（導入済みを毎回 update/再取得しないポリシー）。**未導入のときだけ**不足分を `install_pkgs`（apk add / apt install / pacman -S）で取得し、取れなければ明確に案内して失敗する。app 側 (`TerminalScreen`) のダウンロード確認ゲート (`confirmBeforeDownload`) が同意を取ってから走る。`clean` 指定時のみ cache を消して入れ直す (`clean_pkgs`、破損状態の救済)。
- `GuiSession`/`GuiActivity`/`GuiScreen`/`GuiViewport`/`GuiInputView`/`GuiKeyMapper`/`GuiEventWatcher` + `gui/rfb/RfbClient.kt`(内蔵 RFB クライアント)。端末タブと GUI タブをペアリングし IME 連動。
- **入力**: `GuiInputView` のジェスチャ — **2 本指 = ピンチ(ズーム/パン)**、**3 本指縦移動 = ホイール上/下スクロール**（一度 3 本指になったら全指が離れるまでスクロール扱い）。旧スクロールボタンと `RfbClient.scrollWheel` は撤去。
- **動画**: GPU 無し端末で `gpu` 出力が失敗するため、mpv を **`vo=x11` 既定 + `LIBGL_ALWAYS_SOFTWARE`** でソフト描画させて正常再生。
- **音声 (`service/AudioBridge.kt`)**: **オプトイン**（設定「GUI 音声」`guiAudioEnabled` ON 時のみ）。distro 内 PulseAudio(`-n` 方式で起動) → TCP → Android `AudioTrack` でブリッジ。

### 4.13 Android API ブリッジ (`Z2ApiBridge` / `Z2ApiScript`)

- 端末から Android 機能を叩くコマンド群。マクロは「トリガー (events.jsonl を tail) → ロジック (シェル) → アクション (`z2-*`)」で組む。[マクロの書き方は `docs/ja/MACRO-GUIDE.md`](MACRO-GUIDE.md) を参照。

  | コマンド | 内容 |
  |---|---|
  | `z2-notify` | 通知を出す |
  | `z2-toast` | トースト表示 |
  | `z2-share` / `z2-open` | 共有 / URL・ファイルを開く |
  | `z2-clip (set/get)` | クリップボード |
  | `z2-battery` | 電池状態 |
  | `z2-vibrate` | バイブレーション |
  | `z2-say` | TTS 読み上げ |
  | `z2-torch` | フラッシュライト on/off/toggle (0.8.153) |
  | `z2-media` | メディア再生制御 |
  | `z2-volume` | メディア音量 |
  | `z2-intent` | 汎用 Intent 発火 (0.8.154) |
  | `z2-sensor` | 照度/加速度/近接を 1 回読み JSON 返し (0.8.156) |
  | `z2-state` | 現在状態を 1 回で返す (0.8.167) |
  | `z2-alarm` | 時刻トリガー (0.8.167) |
  | `z2-macro` | マクロのサンプル導入 (0.8.167) |
  | `z2-session` | **アプリ自身のタブを操る** (0.8.199・A1) |

  - **`z2-notify` のバナー表示 (0.8.163)**: `-h`/`--high`/`--banner` フラグ付きのとき `IMPORTANCE_HIGH` の別チャンネル (`z2term_api_high`) + `PRIORITY_HIGH` で**画面上部にバナー (ヘッドアップ) 表示**する。既定チャンネル (`z2term_api`) は `IMPORTANCE_DEFAULT` で作成済みのため後から重要度を上げられず (Android 仕様)、バナー用に別 ID のチャンネルを分けている
  - **`z2-say`**: 端末標準 TTS で読み上げ (エンジン初期化は非同期のため準備完了までキュー)
  - **`z2-torch`**: `CameraManager.setTorchMode` (権限不要) で制御し結果の点灯状態を返す
  - **`z2-media` / `z2-volume`**: 前者は `AudioManager.dispatchMediaKeyEvent` でメディアキーを流し、後者は `STREAM_MUSIC` を操作 (結果 current/max を返す)
  - **`z2-intent`**: `am start` 風のフラグ (`-a/-d/-t/-p/-n/-f/--es/--ez/--ei/--broadcast/--service`) で任意の Intent を組んで startActivity/broadcast/startService する汎用アクション。これ 1 本でアプリ起動・設定画面表示・アラーム設定・共有等を網羅する (いずれも権限不要。呼び先が要求する権限は別)
- `ProotLauncher.ensureZ2ApiScripts` が launch 毎に `/usr/local/bin` へ書き出す。req/resp は `getExternalFilesDir/z2api` を `FileObserver` で監視、引数は base64、atomic rename。

---

## 5. 主要データフロー

### 5.1 入力 → 出力

```
キー/IME/フリック → onBytes(ByteArray)
   → TerminalSession.writeBytes → channel.writer (PTY/SSH)
   → distro shell が処理 → 標準出力
   → readLoop (IO) が channel.reader を読む
   → emulator.processBytes (専用スレッド) → buffer 更新
   → [端末ログが ON なら SessionLogger へ tee]
   → redrawTick/StateFlow 通知 → TerminalRenderer が Canvas 再描画
```

### 5.1.1 端末ログ (ツールバー ⏺・0.8.195)

画面に出た内容をテキストファイルに書き続ける機能。**分岐点は 1 か所だけ**で、
`TerminalSession.startReadLoop` の `emulator.processBytes` の**直後**に `SessionLogger.append` を置く
(タブに出るものが必ず通る唯一の場所。`writeBanner` 等のアプリ内部生成メッセージだけは別経路)。
processBytes の「後」なのは、**alt screen に入ったかどうかがその塊を処理した後でないと判定できない**ため。

- **スレッド**: `append` はバイト列を `SessionLogger` の単一スレッド executor に積むだけで、
  描画を直列化しているエミュレータスレッドをブロックしない。flush は 500ms 周期。
  アプリが OS に殺されても失うのは末尾のこの分だけ。
- **ローテーションしない** (`service/LogWriter.kt` と同じユーザー方針)。代わりに現在のサイズを
  `TerminalSession.LogState.bytes` として 1 秒ごとに出し、青天井なのを黙って進めない。
- **プレーンテキスト化** (`PlainTextFilter`、既定): エスケープシーケンス (CSI / OSC / DCS / 文字集合指定) と
  意味を持たない C0 を捨てる。単に捨てるだけでは読めるものにならないので、**画面の 1 行を組み立て直す**:
  - **`\r` は行頭に戻るだけで内容を消さない**。以後の文字はその行を上書きする。これでダウンロードの
    進捗表示 (`50%\r75%\r100%\n`) が数千行に膨れず、最後の状態だけが 1 行として残る。
    `\r\n` (ふつうの改行) も同じ規則で正しく処理される。
  - **`\b` は 1 文字戻る**。タブは残す。
  - 組み立ては**コードポイント単位** (`Utf8Decoder` 経由)。日本語混じりの行で `\r` 上書きが起きても
    マルチバイト文字が割れない。塊の途中で UTF-8 が切れても次回へ持ち越す。
  - 1 行が 8192 文字を超えたら改行を待たずに吐き出す (改行の来ない出力で行バッファが無限に伸びるのを防ぐ)。
- **alt screen 中は書かない** (既定)。全画面 TUI は画面を組み立て直しながら描くので、平坦なテキストにしても
  意味のある内容にならずファイルだけが膨れる。設定 `sessionLogAltScreen` で書くようにもできる。
- **保存先**は `filesDir/shared_home/<sessionLogDir>` (= 端末から見た `~/z2term-log/`)。ホーム配下なので
  端末からもファイラーからも (SAF プロバイダ経由で他アプリからも) すぐ触れる。ファイル名は
  `sessionLogNameTemplate` (`{date}` / `{tab}`) と `sessionLogTimeFormat` から作り、追記 OFF で同名が既に
  あれば `-2` `-3` … を足して**上書きしない**。日時書式が壊れていても既定書式に落として記録は始める
  (設定ミスで機能ごと死なせない)。
- **記録の ON/OFF はタブごとの状態で、永続化しない**。アプリを開き直すと必ず OFF になる。画面に出たものが
  そのまま入る機能なので、意図せず記録が続いている状態を作らない。タブを閉じるとき (`shutdown`) は
  `stopLogging` で書き残しを吐き出す。
- **UI**: ツールバーのログボタンの**短押し = 開始/停止**、**ダブルタップ = 詳細設定シート**
  (`ui/log/SessionLogSheet.kt`)。アイコンは**記録中 🔴 / 停止中 ⚪**（録画ボタンの慣習で状態が一目で分かる。0.8.206。以前は常時 ⏺ で緑ハイライトのみ）。長押しは並べ替えで埋まっているので使えない。設定シートでは保存先・
  ファイル名・日時書式・過去分を含めるか・追記か新規か・alt screen・生ログを切り替え、次に作られる
  ファイル名をプレビューする。「画面に出たものはそのまま入る」旨をシート内に明記する。
- **ファイル名の `{tab}` サニタイズ** (`TerminalSession.resolveLogFile`): タブ名をファイル名に使える形へ直すとき、**日本語などの Unicode 文字は残し**、パス区切り・予約記号・制御文字・空白だけを `_` にする。以前は「ASCII 英数字以外を全部 `_`」にしていたため、日本語タイトルのタブが**下線だらけのファイル名**（例: `2026-07-24_0941-____________________.txt`）になっていた（0.8.206 で修正。連続 `_` は 1 つに畳み、前後の `_/./-` を削り、空になれば `term`）。

### 5.1.2 共有の受け取り (B1・0.8.197)

他アプリの共有シートから z2term へテキスト / ファイルを渡す入口。**入れるだけで実行しない**
(改行を付けないので入力行に置かれるだけ。走らせるかどうかは必ずユーザーが決める)。

```
他アプリ「共有」→ ACTION_SEND / ACTION_SEND_MULTIPLE
   → MainActivity.handleShareIntent (onCreate / onNewIntent)
   → SharedIntake.textFrom (IO)      … テキストはそのまま / ファイルは ~/z2term-inbox/ へコピー
   → SessionManager.insertText       … 入れ先を決めて pasteText (bracketed paste)
```

- **`SessionManager.insertText(text, sessionId?)` が「外から端末へ文字を入れる」共通の入口**。
  A1 (`z2-session send`) もここに乗せる想定で、B1 の側から先に切り出してある。
  入れ先は「id 指定 → アクティブタブ → (GUI タブなら) 最初の端末タブ」の順で決め、
  アクティブでなければ**そのタブへ切り替える** (見えない所に文字が入った状態を作らない)。
  クリップボードは書き換えない (共有しただけでコピー履歴が積み替わらないように)。
- **ファイルは実体を取り込む**。共有 URI は他アプリが握る一時的な参照でシェルからは触れないため、
  `shared_home/z2term-inbox/` にコピーして初めて `less` や `python` に渡せる。
  ファイル名は共有元が名乗る `DISPLAY_NAME` を使い、**パス区切りと、ダブルクォート内でも意味を持つ
  文字 (`"` `\` `$` `` ` `` `!`) と制御文字を落とす** (`../` で置き場の外に書かせない +
  貼ったパスがシェルで解釈されない)。同名は `-2` `-3` … を足して上書きしない。上限 512MiB。
- **貼るパスの形**: 素直な名前なら `~/z2term-inbox/foo.txt` のまま。スペース等を含むときは
  **`"$HOME/..."` 形式**にする — `"~/..."` とクォートすると `~` が展開されず「そんなファイルは無い」
  になるため。複数ファイルは空白区切りで並ぶので、そのままコマンドの引数として使える。
- **`MainActivity` は `launchMode="singleTask"`**。タブは 1 画面で持つものなので、共有のたびに
  Activity が積み上がって「戻る」で古い画面が出る状態を作らない。同じ Intent での二重挿入は
  Intent 自身に付ける印 (`EXTRA_SHARE_HANDLED`) で防ぐ (画面回転で `onCreate` が走り直しても入らない)。

### 5.2 起動シーケンス

```
MainActivity.onCreate → SessionManager.ensureFirst → setContent(TerminalScreen)
TerminalScreen: active が IDLE なら startTerminal()
  → isProotAvailable? → isDistroReady? (未/旧なら DistroInstaller で展開、非同梱は先に DL)
  → ProotLauncher.launch (履歴 rc + sshd ラッパー注入、shared_home/sdcard bind)
  → LocalPtyChannel → RUNNING → readLoop 開始 → initCommand 送出
失敗時: launchAndroidSh にフォールバック
```

---

## 6. 機能仕様

### 6.1 独自キーボード (ASCII)

- レイアウト (5 行): `ESC 1..0 ⌫` / `TAB q..p` / `あ a..l ⏎` / `⇧ z..m,./` / `CTRL ?# ALT SPACE ←↓↑→`。
- **Shift**: OFF → ONESHOT → LOCKED の 3 状態循環。**CTRL/ALT/記号(?#)**: トグル。
- **フリック**: 英字キーの **下フリック = ローマ字大文字**。上/左右 = 記号 (緑ヒント表示、下フリックはヒント無し)。COMPACT は上 + 下、SPACIOUS は 4 方向 + 下。
- **長押し連打**: 数字 / 矢印 / space / 英字キー / **⏎** は押しっぱなしで連打 (初回 400ms→55ms)。⌫ は 500ms→60ms、左右フリックで Ctrl+W / Ctrl+U。修飾キーは連打対象外。⏎ は英字レイアウト・かなフリック・システムキーボード時の `SpecialKeyBar` の 3 か所すべてに入れる (0.8.193。1 か所だけだと「キーボードによって効かない」になる)。かなフリックの ⏎ は 1 回目が未確定文字の確定に使われ、以降は改行が連続で送られる。
- **ALT / META**: どちらも同じ「次のキーに ESC を前置する」修飾 (Meta)。英語レイアウトのみ META を Row 3 左に出す。`emitChar`/`emitSpecial` に加えて **`emitCursor` にも適用する** — 以前は矢印だけ修飾が捨てられ、ALT+矢印がただの矢印になっていた (0.8.193 で修正)。矢印のバイト列は DECCKM 依存で端末側が組むため、ESC を単独で先に送ってから矢印を送る。
- 「あ」キー → 内蔵 日本語フリックへ切替。TopBar「あ」 → OS IME 切替 (別系統)。
- **英語ロケール (`showJapaneseKeyboard=false`)**: 「あ」キーが無いぶん SPACIOUS では ⇧/CTRL を 1 段下げ、`a` の左端を **META キー** (= Alt と同じ ESC プレフィックス修飾) にして a 行頭の空きをなくす。Row 5 左は CTRL。COMPACT は元々ホーム行に左キーが無いため変更なし。

### 6.2 日本語 フリックキーボード

- 標準 12 キー配列 (5 列 × 4 行、ヒント表示):
  ```
  ESC      あ   か  さ   ⌫
  ◀/▼     た   な  は   ▶/▲
  ␣       ま   や  ら   変換
  ABC      小゛゜ わ  、。  ⏎    ← ABC=英字へ
  ```
  Row 2 の両端は左右キーの真下に上下キーを半行ずつ積み (`JpEdgeStack`、1:1)、◀ ▶ ▼ ▲ を
  全て同じサイズに揃える。◀ の下 (左) に ▼ (下)、▶ の下 (右) に ▲ (上)。▼▲ は `flush()` 後に端末
  カーソル上下を送出。◀▶ は composing 中は**入力カーソル移動** (§6.2.1)、空のときだけ端末カーソル
  左右を送出。スペース/変換は Row 3 で 1 行のまま (押しやすさ優先)。
- フリック規約: タップ=あ段 / 左=い / 上=う / 右=え / 下=お。
- **濁点キー (小゛゜)**: 直前のかなを 濁点→半濁点→小書き→元 に循環 (循環表はひらがな基準)。かなの連打は循環させず素直に重ねる (「つつ」が「っ」にならない)。
- **⌫**: 左フリック=単語削除 (Ctrl+W) / 右フリック=行頭まで削除 (Ctrl+U)。
- 長音 `ー` は `わ` の右フリック。カタカナは専用キーを廃止し、候補バーのカタカナ候補で選ぶ (§6.2.1)。

#### 6.2.1 かな漢字変換 (`KanaKanjiConverter` / `ComposingState`)

SKK 辞書 (`assets/z2dict.txt` 約16万行) + 常用動詞/形容詞の活用補完を二分探索で引く best-effort 変換。打鍵ごとに候補バー (`CandidateBar`) を更新する。

- **候補生成 (`convertFlexible`)**: 学習履歴(完全一致) → 学習履歴(前方一致＝予測変換) → 文まるごと最尤変換(`nbest`) → 完全一致(`convert`)/送り仮名活用(`okuriForms`) → 前方一致予測(`predict`)。生かな・カタカナは常に確定候補として残す。
- **記号当て字の抑制** (`SYMBOL_READING_PENALTY`): IPADIC は 1 文字ひらがな読みに記号表層を低コストで持つため (と→＆ 3177・に→２・ご→５ 等) 素のかな/漢字より上位に出てしまう。読みが 1 文字ひらがな ∧ 表層が記号のみ (仮名・漢字を含まない) のエントリへ `loadFromStreams` で大きめのペナルティを足し最下位へ落とす (候補一覧には残る)。既存の `KATAKANA_DUP_PENALTY`(過剰カタカナ化抑止) / `KANA_PREFERRED`(補助動詞・形式名詞の漢字化抑止) と同じ流儀。
- **学習履歴** (`ImeHistoryStore`): 確定語を頻度・直近 7 日でランキングし上位に出す。
- **予測変換 (学習履歴の前方一致)**: 打った読みで始まる学習済みの語句を、文まるごと変換より先に候補上位へ出す (`ImeHistoryStore.predictHistoryWithReading` / `convertFlexible` の前方一致段)。打ちかけの読みで「打ち慣れた語句」を絞り込んで提示する本来の予測変換。**予測候補を確定したときは、打った接頭辞ではなく語句の実際の読みで学習する**: `ComposingState.commit` が `KkcConverter.predictionReadingMap` で表層→実際の読みを逆引きし、`ImeHistoryStore.record` の見出しに使う。接頭辞だけの不正な学習見出しが履歴に入らず、次回以降も同じ読みで予測が再利用される。
- **文節分割合成 (`segment`)**: 内容語(最長辞書一致) + 後続の助詞/送り仮名を 1 文節として連結 (例: きょうの → 今日の)。**助詞** (の/は/が…) と**文末助動詞** (でしょう/ました/です…) は単漢字エントリ (野/葉/増田…) を持つため**かなのまま残す** (`PARTICLES` / `AUX_KANA`)。辞書ヒット 1 文節以上 ∧ 漢字を含むときに返す。
- **先頭ブロックとカーソル** (`cursor`) (0.8.157 で刷新): composing 中は `cursor` (0..length) が挿入カーソルであり、同時に「先頭ブロックの境目」でもある。候補は先頭ブロック (`text[0..cursor]` = `splitHead`) を `convertFlexible` で変換したもの。◀▶ (`moveCursorLeft`/`moveCursorRight`) でカーソルを動かすと先頭ブロックが伸縮し候補が追従する (行頭 0 まで到達可)。打鍵直後はカーソルが末尾にあり (= 打った生かな全体が先頭ブロック)、◀ で縮めて部分変換する。候補タップ/⏎ (`commitRaw`) で先頭ブロックを確定 → 残り (`splitTail`) を composing に据えて末尾カーソルで続行、残り 0 で抜ける。変換キー (`convert`) は先頭ブロックの候補をサイクルする。
- **文節境界** (`KkcConverter.bunsetsu`): 一括予測の内訳 (`fullPredictionBlocks`) や結合ブロック学習で文を文節に割るときは、**正確なラティス最短経路 (`nbest` 1 位) の分割**を使う (0.8.29)。位置 DP の `segments` は単一右文脈しか持たず接続コスト次第で誤分割していたため。
- **動的ブロック分割 (学習)** (0.8.71): ブロック境界を辞書コストだけで固定せず、ユーザーが確定した読みブロックの頻度で学習する。確定済み `(読み→表層)` を `ImeHistoryStore.learnedBlock(読み)` が `(最頻表層, コスト下げ幅)` で返し (`KkcConverter.learnedBlock` に配線)、`nbest` のラティス構築で 2 文字以上の読みが学習ブロックに一致したらノードコストを下げる (`BLOCK_BASE_BONUS=3000` + `count` 比例 `BLOCK_COUNT_STEP=1500`(上限 count4) + 直近 `BLOCK_RECENT_BONUS=1000`)。これでカタカナ化ペナルティ+接続コストを 1〜2 回の確定で上回り、誤分割していた頻用読みが次回以降 1 ブロックへ自動でまとまる。辞書に無い読みでも学習表層で合成ノード (`lc=rc=0`) を足す (未学習読みは挙動不変)。**コスト割引はユーザーが実際に確定した表層だけに効かせる** (0.8.74)。同読みの全表層へ一律に掛けると辞書最小コストの別表層が勝ち、選んだ漢字が反映されなかったため。**結合読みを 1 語コスト基準にし、連続確定 run を結合ブロックとして学習する** (0.8.85): 学習ブロックの合成ノードコストを長さ比例の未知かなではなく 1 語分の `UNK_COST` 基準にし、`ComposingState` が同一スプリット run の連続確定を `committedRun` に貯めて run 終了時 (`learnMergedRun`) と一括確定時に結合読み→結合表層を記録する (読み長 2〜`MERGE_MAX_READING_LEN`=6 に限定)。
- **文まるごと一括予測** (`fullPrediction`): カーソルより後ろ (tail) が残るとき (`0 < cursor < length`)、先頭ブロックの最尤候補 + 残りかなの Viterbi 1-best を連結した「文まるごと」候補を候補バーに薄緑ピルで 1 つ出す。タップ (`commitFull`) で全文を一括確定。◀▶ で `cursor` が動くと `refreshPredict` 経由で再構築され境界変更に追従する (0.8.16)。残りかなの Viterbi は先頭表層を文脈に bigram リランクを通す。**一括確定の学習はブロック単位** (0.8.74): `fullPredictionBlocks` (先頭ブロック + `bunsetsu(tail)`) を控え、`commitFull` で各ブロックの `(読み→表層)` と隣接ブロック間 bigram を学習する。内訳が `full` と不整合なときは文全体 1 エントリへフォールバック。
- **候補バー先頭ピルの生かな全体表示** (0.8.157): 先頭ピルは「先頭ブロック (`splitHead`) の緑ピル + 別の tail ラベル」の 2 分割から、**打った生かな全体を 1 つの連続ピル**へまとめた。先頭ブロック (カーソルより前) を濃色・残りかな (`splitTail`) を薄色にし、カーソル位置に caret (地色反転の細バー) を挟んで「今の先頭ブロック範囲＝どこまで打ったか」を示す。タップは `commitRaw` (先頭ブロックを生確定して次へ)。
- **カーソル位置での途中編集** (`cursor`) (0.8.157): 編集位置を独立フィールド `cursor` (0..length) に持ち、**◀▶ = カーソル移動に統一**した。かな/記号は `insertAtCursor` でカーソル位置へ挿入、⌫ (`backspace`) はカーソル直前を削除、`小゛゜` はカーソル直前が対象 (`charBeforeCaret`)。**行頭 (0) まで移動でき、長文/短文を問わず一様に途中修正できる**。以前は編集位置を `splitHeadLen` (最小 1・自動分割由来) に相乗りさせていたため「行頭へ行けない」「自動分割が効く長文でしか編集できない」不具合があった。旧 `autoSplit`／`caretEditMode` フラグと「打鍵で自動的に先頭文節へスプリット」する挙動は廃止し、打鍵直後はカーソル末尾 (先頭ブロック = 全体) に統一した。
- **再変換**: 確定直後 (composing 空) に変換キー=「再変換」で直前確定を読みに戻す (`restoreLastCommit`)。
- **キー背景**: 未確定中は ◀▶・変換キーの背景を緑にせず静かに保つ (緑は「再変換」ヒント時の変換キーのみ)。

### 6.3 SSH (端末 → 外部)

- `SshProfilesSheet` で host/port/user/認証 (パスワード or 秘密鍵+パスフレーズ)/initCommand/`-L` 転送を編集。
- 接続時は `SessionManager.openNew` + `startSsh(profile)`。host key は `HostKeyVerificationDialog` で確認 (`KnownHosts` に保存)。

### 6.4 SSH サーバ (PC → 端末) ※dropbear

- **OpenSSH `/usr/sbin/sshd` は proot 不可** (privsep 破綻 + 新 OpenSSH は `UsePrivilegeSeparation` で起動不可)。→ **dropbear** を使用。
- 端末で **`sshd`** = `/usr/local/sbin/sshd` ラッパー (ProotLauncher が毎起動配置、PATH 優先)。`dropbearBootstrapScript` が本体。
  - ポート優先順: `-p` / `-o Port=N` 引数 → `/etc/ssh/sshd_config` の `Port` → 既定 2222。
  - `-f <config>` / `-D`(前景) / `-t`(設定確認) 対応。特権ポート(<1024)は proot で bind 不可を警告。
  - dropbear 未導入なら pacman/apt/apk/dnf/zypper で自動 install。既存 dropbear は pkill→pidof→pidfile→`/proc` 走査で確実停止。
- 設定の「sshd 起動」ボタンも `sshd` を実行。表示の `ssh -p <port>` は sshd_config の Port を反映。

### 6.5 テキスト選択 UX

- 長押しで選択開始 → ドラッグで範囲拡張。**`GestureDetector` は onLongPress 後 onScroll を送らない**ため、`touchMode != NONE` の間は detectors を介さず生 `MOTION_MOVE` で追従。
- ハンドル当たり判定を拡大 (行高×2.2 / 最低 96px、近い端を選択、左端でも掴める)。**末端付近ドラッグで範囲変更**。
- **拡大鏡**: 選択中、端末描画 View を `android.widget.Magnifier` で指の上に表示。
- **端で自動スクロール**: 検知ゾーン 行高×2.5 / 最低 80px。上端→過去 / 下端→最新を 45ms 毎にスクロールしつつ選択を画面外まで伸ばす。
- 選択中「コピー」フローティングボタン、タップで選択解除。

### 6.6 コマンド履歴の永続化

- proot は終了時 SIGKILL で履歴が書かれない → 起動毎に rc/env を注入。bash: `histappend` + `PROMPT_COMMAND='history -a'`、zsh: `INC_APPEND_HISTORY`/`SHARE_HISTORY`。1 コマンド毎に追記し、再起動後も ↑ で履歴を辿れる。

### 6.7 ファイル共有 / 外部ストレージ

- SAF ホーム = `shared_home` (端末の `/root` と一致)。各 distro の rootfs(`/`) も公開。
- proot 内から `cd /sdcard` で Android 共有ストレージ (要 全ファイルアクセス許可)、`/storage/app` はアプリ専用領域 (権限不要)。

### 6.8 その他 UI

- タブ複数化（**長押し→左右ドラッグで並べ替え**、ダブルタップで閉じる。**タブ内で子プロセスが前景実行中のときだけ削除確認ダイアログを挟む**＝作業中の誤タップ防止。前景がログインシェルなら従来どおり即閉じる。判定は PTY master の `tcgetpgrp` を **プロンプト待機時の前景 pgid（起動直後に実測して確定する基準値）** と比較する。0.8.157 では `shellPid` と比較していたが、`shellPid` は forkpty の子＝**エンジン(proot/z2root)プロセスの pid** でありゲストのログインシェルとは別 pgid のため、アイドル時でも常に不一致→**常に「動作中」誤判定**する回帰があった。実測基準に改め解消 (0.8.160)）、ピンチでフォント拡縮 (8–32sp)、スクロール + 最新へ戻る ↓、スニペット、テーマ/フォント実プレビュー。
- 設定 (`SettingsSheet`): 0.8.14 で従来の下から重なるボトムシートをやめ、**全画面の「別ページ」**として表示（上部に戻る矢印 ← + システムバック対応）。

---

## 7. 設定項目

| 項目 | キー | 既定 | 範囲/候補 |
|---|---|---|---|
| テーマ | themeName | "ZTS Theme" | 9 種 |
| フォント | fontId | "monospace" | System / IBM Plex / JetBrains / Fira Code |
| フォントサイズ | fontSizeSp | 13 | 4–32 |
| スクロールバック行数 | scrollbackLines | 5000 | 500–50000 |
| ディストロ | distroId | "alpine" | alpine / ubuntu / archlinux / kali |
| 曖昧幅を全角 | ambiguousAsWide | false | true/false |
| 初期コマンド | initCommand | "" | 任意 |
| ログインシェル | loginShell | "/bin/zsh" | /bin/zsh, /bin/bash, /bin/sh |
| キーボードスタイル | keyboardStyleId | "spacious" | compact / spacious |
| キーボードモード | keyboardMode | "custom" | custom / system |
| 横画面キーボード位置 | landscapeKeyboardPosition | "bottom" | left / bottom / right |
| 横画面サイドKB幅 | landscapeKeyboardWidthDp | 420 | 280–700 dp |
| 横画面キーボード高さ | landscapeKeyboardHeightDp | 320 | 200–500 dp |
| 縦画面キーボード高さ | portraitKeyboardHeightDp | 320 | 200–500 dp |
| GUI ターミナル | guiTerminalId | "xterm" | GUI 内で起動するターミナル |
| GUI 音声 | guiAudioEnabled | false | true/false（オプトイン PulseAudio ブリッジ） |
| GUI 拡大率 | guiMagnification | 1.5 | 0.5–3.0 |
| GUI クリーンインストール予約 | cleanInstallGuiArmed | false | 次に開く GUI タブで入れ直す。起動時に消化して false へ戻る |
| ダウンロード前確認 | confirmBeforeDownload | true | true/false |
| 常駐サービス | keepAliveService | true | true/false（ツールバーの 🔒 ロックで ON/OFF。**ツールバーから隠しているときだけ設定 › ツールバーにもトグルが出る** (0.8.194)。**常駐サーバー稼働中は 🔒 が薄くロックされトグル不可**になり、タップで終了ダイアログが出る (0.8.204)） |
| 画面消灯ロック | keepScreenOn | false | true/false（ツールバーの 💡 で ON/OFF。**永続化して次回起動時に復元** (0.8.144)。隠しているときは設定 › ツールバーから (0.8.194)） |
| キーボード表示バー | keyboardToggleBar | true | true/false（ON=キーボード上にトグルバー。OFF=バー無しで ⌨ ボタンのダブルタップ切替 (0.8.145)） |
| ツールバー並び順 | toolbarOrder | ""（既定順） | カンマ区切り id。長押しドラッグで更新。隠しているボタンの id も残す |
| ツールバー非表示 | toolbarHidden | ""（全部出す） | カンマ区切り id。設定 › ツールバーでタップ切替。⚙ は指定できない (0.8.194) |
| 端末ログ 保存先 | sessionLogDir | "z2term-log" | ホーム (~) からの相対パス (0.8.195) |
| 端末ログ ファイル名 | sessionLogNameTemplate | "{date}-{tab}.txt" | `{date}` / `{tab}` を展開。パス区切り等は `_` に置換 |
| 端末ログ 日時書式 | sessionLogTimeFormat | "yyyy-MM-dd_HHmm" | `SimpleDateFormat` パターン。壊れていても既定に落として記録は始める |
| 端末ログ 過去分を含める | sessionLogIncludeScrollback | false | ON で開始時に画面 + スクロールバックを先に書く |
| 端末ログ 追記 | sessionLogAppend | false | OFF は毎回新規（同名なら `-2` `-3`） |
| 端末ログ 生ログ | sessionLogRaw | false | ON でエスケープをそのまま残す（不具合報告用） |
| 端末ログ 全画面も記録 | sessionLogAltScreen | false | ON で alt screen 中も書く |
| 実行エンジン (裏設定) | executionEngine | "z2root" | proot / z2root / chroot（chroot は root 解放時のみ） |
| エンジン選択解放 (裏設定) | engineSelectorUnlocked | false | バージョン 7 回タップでトグル（root 不要・解除時は z2root へリセット） |
| chroot 解放フラグ (裏設定) | rootChrootUnlocked | false | 7 タップ時の root セルフテスト成功で true |
| 言語 | (専用 SharedPrefs `z2term_locale`) | OS 既定 | ja / en |
| 通知検知 | notificationCaptureEnabled | false | true/false（OS の通知アクセス許可が別途必要） |
| 通知ログを保存 | notificationLogEnabled | true | false なら検知だけ行いファイルに書かない |
| 通知ログ形式 | notificationLogFormat | "" (= JSONL) | `{time}{app}{title}{text}` 等のテンプレート |
| 通知ログを先頭に追記 | notificationLogPrepend | false | true で新着がファイル先頭（10 MiB 超で注意表示） |
| システムイベント検知 | systemEventCaptureEnabled | false | 画面/ロック/充電/電池/Wi-Fi/BT オーディオ |
| イベントログ形式 | systemEventLogFormat | "" (= JSONL) | `{time}{event}{level}{ssid}` |
| イベントログを先頭に追記 | systemEventLogPrepend | false | true で新着がファイル先頭 |
| SMS 検知 | smsCaptureEnabled | false | true/false（SMS 受信権限が別途必要。通知経由と違い本文を直接読む） |
| SMS ログ形式 | smsLogFormat | "" (= JSONL) | `{time}{from}{body}` 等のテンプレート |
| SMS ログを先頭に追記 | smsLogPrepend | false | true で新着がファイル先頭（10 MiB 超で注意表示） |
| 解除失敗を検知 | unlockWatchEnabled | false | 端末管理者 (watch-login) の有効化が別途必要 |
| 常駐サーバー登録 | serverEntries | "" | 常駐させるサーバーの定義 (JSON) |
| 起動時に自動で常駐 | serversAutostartOnBoot | false | 端末起動で常駐サーバーを開始 (`BootReceiver`) |
| 常駐サーバー省電力 | serversLowPower | false | true/false |
| Kitty 外部ファイル転送 | kittyExternalFileEnabled | false | 実験的。`t=f`/`t=t`/`t=s` の opt-in |
| SGR マウス入力 | sgrMouseInputEnabled | false | 実験的 |
| 外部 SD を認識 | externalStorageEnabled | false | ON で物理ボリュームを検出し bind |
| Android ホスト bind | androidHostBindEnabled | false | 実験的。`/system` `/apex` を晒す |
| トレースログ | traceLogEnabled | false | 開発者向け |

`noInstallTimeout`（インストールタイムアウト無効化）・`cleanInstallGuiArmed`（GUI クリーン再展開フラグ）等も DataStore (`z2term_settings`) に保持。SSH プロファイルは別 DataStore (`z2term_ssh`) に JSON で保存。

**設定を初期化**（アクション）: 設定末尾（アプリ情報とライセンスの間）の「設定を初期化」ボタン（`danger` 表示）は、確認ダイアログを挟んで `AppSettings.resetToDefaults()`（DataStore `z2term_settings` を `clear()`）を呼ぶ。全キーが消えるので上表の各値・裏設定の解放フラグ・常駐サーバー定義・ツールバー並び順・各種ログ設定がすべて既定へ戻る（実行エンジンも既定 z2root に戻る）。rootfs（インストール済み OS）・ユーザファイル・言語（別 SharedPrefs `z2term_locale`）には触れない。

---

## 8. パーミッション

| 権限 | 用途 |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | distro DL、SSH、パッケージ取得 |
| FOREGROUND_SERVICE(_SPECIAL_USE) | 常駐ターミナル |
| POST_NOTIFICATIONS | 常駐通知 (Android 13+) |
| WAKE_LOCK | バックグラウンド維持 |
| MANAGE_EXTERNAL_STORAGE | `cd /sdcard` で共有ストレージ全体へ R/W (設定から許可導線) |
| READ/WRITE_EXTERNAL_STORAGE (maxSdk) | 旧 API 用 (`requestLegacyExternalStorage`) |
| ACCESS_WIFI_STATE | システムイベント検知の Wi-Fi 判定と SSID 取得 (`SystemEventService`。SSID は位置情報権限が無いと空) |
| VIBRATE | `z2-vibrate` (Android ブリッジ) と検知イベントの通知 |
| RECEIVE_BOOT_COMPLETED | 設定「起動時に自動で常駐」が ON のとき、端末起動で常駐サーバーを立ち上げる (`BootReceiver`。`LOCKED_BOOT_COMPLETED` も受ける)。`z2-when` の時刻トリガーも端末起動・アプリ更新で貼り直す (`WhenReceiver`) |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 常駐が OS に停止されないよう電池最適化の除外をワンタップで要求 (`BatteryGuard`) |
| (システム保護ブロードキャスト) | `z2-when` の充電/電池トリガー用に `ACTION_POWER_CONNECTED`/`_DISCONNECTED`/`BATTERY_LOW`/`_OKAY` を受ける (権限宣言は不要・外部からは送れない)。**0.8.214 から受け口は検知サービス `SystemEventService` の動的レシーバ**（manifest レシーバでは届かないため。上記「自動化ハブ」参照） |

---

## 9. ビルド / 同梱物

```bash
bash scripts/build-bundle.sh          # 同梱物一括生成
# 個別: build-proot.sh / build-alpine-rootfs.sh aarch64 / fetch-fonts.sh
sh scripts/z2root-cmdtest.sh          # z2root の難所を踏む壊れやすいコマンド群を横断テスト(全10グループ・未導入はskip・末尾に非ゼロ一覧。SKIP_NET/SKIP_BUILD/RUN_SSHD/RUN_PRIV)
bash scripts/gw.sh :app:assembleFullDebug   # オンデバイスはこちら (下記)
./gradlew :app:assembleFullDebug      # APK (full = rootfs 同梱)
./gradlew :app:assembleFossDebug      # APK (foss = rootfs 非同梱・起動時 DL)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

- full の同梱: `src/full/jniLibs/arm64-v8a/{libproot,libproot_loader,libtalloc,libandroid-shmem}.so`(full フレーバー専用)、`src/full/assets/alpine-minirootfs-aarch64.tgz`(full のみ)、`assets/fonts/*.ttf`(共通)。
- foss は rootfs を含めず、`DistroSpec.ALPINE` の公式 CDN URL + SHA-256 で起動時に取得 (`DistroSpec.bundledInApk` が false)。proot/talloc prebuilt は F-Droid 非適合のため foss から除外し、実行エンジンは同梱ソースからビルドする z2root を使う。
- **assets の rootfs は `.tgz` 拡張子**で置く (`.tar.gz` だと aapt が解凍リネームする)。
- **`useLegacyPackaging=true` 必須** (execve する .so を nativeLibraryDir に実体配置するため)。
- **オンデバイス (aarch64・proot/z2root 下) では `scripts/gw.sh` 経由でビルドする**: この環境は libc の `accept()` が ENOSYS を返し、JDK17 の `sun.nio.ch.Net.accept` が libc `accept()` を呼ぶため Gradle デーモンの TCP IPC が落ちて "Could not connect to the Gradle daemon" でビルド不能になる。`gw.sh` は **`accept()` が ENOSYS の環境でだけ** `accept4` シム (`scripts/accept4-shim.c`) を `LD_PRELOAD` して `./gradlew` を呼ぶ (PC など正常な環境では素通しなのでマルチデバイス運用を壊さない)。シムが aapt2 (bionic) に継承されると `libc.so.6 not found` で別の失敗になるため、aapt2 ラッパー側で `LD_PRELOAD` を外している。`bash scripts/gw.sh help` で適用の有無を確認できる。
- rootfs 構成変更時: `scripts/alpine-packages.txt` 編集 → `DistroBundle.ROOTFS_VERSION` を +1 → `FORCE=1 build-alpine-rootfs.sh` → assemble (利用者は APK 入替で自動再展開)。
- **lint は警告 0 を維持する** (`bash scripts/gw.sh :app:lintFullDebug`、0.8.190 で達成)。CI の `Build & Lint` が落ちるとタグ push で走るリリースジョブが skip されるため、lint を通すことがリリースの前提になっている。黙らせ方は 3 段階に分ける: **恒常的に無意味な検査**だけ `app/build.gradle.kts` の `lint { disable }`、**特定の場所だけ外したいもの**は `app/lint.xml` の `<ignore path>`、**意図的な個別箇所**は現場に `@Suppress`/`@SuppressLint`/`tools:ignore` と理由コメント。一律に `disable` へ入れて他の場所の検出まで殺さない。

---

## 10. 既知の制約と設計上の罠

### 10.1 修正不能な制約

**PRoot のカーネル特権制約 (修正不能)**: root に見えても `ip`/`nmap -sS`/`ping`/特権ポート bind は不可。代替は `nmap -sT` 等。OpenSSH sshd も privsep 破綻のため dropbear を使う。

**SysV 共有メモリ (`shmget`) が ENOSYS (カーネル由来・アプリ側では修正不能)**: Android のカーネルは `CONFIG_SYSVIPC` を落としているため、`shmget`/`shmat` が "Function not implemented" で失敗する。**POSIX 共有メモリ (`shm_open` = `/dev/shm`) とは別系統**で、そちらは 0.8.177 の bind で使えるようになったがこちらは残る。影響は X11 の **MIT-SHM 拡張**が使えないこと (GUI の描画がサーバ経由のソケット転送になり、その分遅い)。主要ツールキットは MIT-SHM の可否を検出して自動でフォールバックするので通常は「動くが遅い」で済むが、拡張の存在を前提に握り決め打ちする少数のアプリは表示が壊れうる。回避したい場合はアプリ側の設定で MIT-SHM を切る。

**アプリを更新すると常駐サーバーが止まる (Android 由来・アプリ側では回避不能)**: APK を入れ替えると Android がそのアプリのプロセスを終了させるため、`ServerDaemonService` ごと supervisor が落ちる。`sshd` を上げたまま更新すると**更新後は止まっている**。設定「起動時に自動で常駐」は `BootReceiver`（端末の起動完了）でしか発火しないので、**更新では復帰しない**。更新のたびに手で起動し直すのが現状の運用（0.8.203 の実機検証で確認）。自動復帰させるなら `ACTION_MY_PACKAGE_REPLACED` を受けて起動する経路が要る（未実装）。

### 10.2 修正済みの重大な不具合

**Gecko 系 GUI アプリのコンテンツプロセスが自分のサンドボックス下でフォントを見つけられない (0.8.179 で修正・2026-07-20 に実機検証済み)**: 親プロセス (chrome UI) は正常に描画されるが、**中身を描くコンテンツプロセスだけ**が `unable to find a usable font (%.220s)` の `MOZ_CRASH` で落ち、本文/HTML を描くペインが空白になっていた。`/dev/shm` の件 (0.8.177) と紛らわしいが**別問題**で、共有メモリは全て成功している (`shm_open` は正常)。

真因は **z2root のトレーサによる SIGSYS の握り潰し**。トレーサは Android の untrusted_app seccomp が禁ずる syscall の SIGSYS を子へ配送せず、その場で戻り値を `ENOSYS`(または権限系なら 0) に化かして握り潰していた。一方 Gecko のコンテンツサンドボックスは自前の seccomp フィルタを入れ、`openat` 等を `SECCOMP_RET_TRAP` にして**自分の SIGSYS ハンドラで受けてファイルブローカーへ委譲する**設計。seccomp フィルタは重畳評価され「より重い action が勝つ」ため TRAP が z2root の TRACE に勝ち、コンテンツプロセスの open は全て SIGSYS になる。それをトレーサが握り潰して ENOSYS を返していた＝**フォントファイルが 1 つも開けない**。サンドボックスを切ると再現しないこと、`security.sandbox.content.level` が 1 でも落ち 0 でのみ解消すること (level 1 でもフィルタ自体は入る)、親プロセスは無事なこと (親にはフィルタが無い)、補助プロセスを無効にしても再現すること、いずれもこれで説明がつく。

修正は SIGSYS の**出所を切り分けて**、ゲスト自前のフィルタ由来なら握り潰さずアプリのハンドラへ配送する。判定は `siginfo` の `si_errno` (= `SECCOMP_RET_DATA`): Android のフィルタは data 0 で TRAP するのに対し、ゲスト自前のフィルタは 0 以外の trap id を載せる (Gecko の `Trap()` は 1 起点の連番)。Android 由来の SIGSYS の扱いは従来どおりで挙動不変。切り分け用に `Z2ROOT_NO_SIGSYS_DELIVER=1` で従来動作へ戻せる。実機 (Thunderbird / z2root) で修正前は必ず 1 件出ていた `exited on signal 11` と `unable to find a usable font` が、修正後は 0 件になることを確認済み。回避策だった `MOZ_DISABLE_CONTENT_SANDBOX=1` は引き続き有効 (HANDBOOK の FAQ に記載)。アプリ側でこの環境変数を既定で注入することはしていない (アプリ自身の防御層を黙って外す判断はユーザーに委ねる)。

### 10.3 踏みやすい罠 (再発防止)

- **lint の助言を鵜呑みにしない**: `mipmap-anydpi-v26` を「minSdk 29 なので `-v26` は不要」の指摘どおり `mipmap-anydpi` へ統合すると、**`mipmap/ic_launcher` が解決できずビルドが壊れる**（アダプティブアイコンの標準配置から外れる）。警告1件のために起動アイコンを壊す価値はないので `-v26` のまま残し、`app/lint.xml` で**このフォルダに限って** `ObsoleteSdkInt` を除外する（他の場所では検出を効かせ続ける）。
- **未使用リソースは 1 件ずつ裏取りしてから消す**: lint は名前解決 (`getIdentifier`) やテーマ経由の参照を追えないので、`UnusedResources` を鵜呑みに消すと実行時に落ちる。リソース名で全文検索し、Kotlin 側・`res/` 内の他 XML・`AndroidManifest.xml` のいずれからも参照が無いことを確認してから消す（文字列は ja/en 両方から）。

- **画面が無いとタブは起動しない**（0.8.203 で `z2-session new` 側は対処済み）。起動は `TerminalScreen` の `LaunchedEffect` が「表示中のタブが IDLE なら `startTerminal()`」という形で駆動している。**アプリ外からタブを作る経路を足すときは、その場で `startTerminal()` まで呼ぶこと**。呼ばないと「タブはできるが PTY が無く、送った文字がどこにも届かない」という分かりにくい状態になる。
- **タブ名は放っておくと上書きされる**（0.8.202 で `labelPinned` を追加）。起動時の OS 名・`android-sh` フォールバック・SSH 接続・シェルのタイトル (OSC 0/2) が順に `_label` を書く。**名前を指定する機能を足すときは pinned を立てる**。
- 端末の `/root` は `distros/<distro>/root` でなく **`filesDir/shared_home`**。SAF/外部ストレージ bind もこれ基準。
- 複数行スクリプトを端末に直接打鍵すると **zsh が `#` コメントを誤実行/継続プロンプトで崩れる** → ファイル化して `sh` 実行。
- dropbear を kill せず再起動すると "Address already in use"。
- `GestureDetector` は **onLongPress 後 onScroll を送らない** → 長押し選択は生 MOTION_MOVE で。
- `ScaleGestureDetector` の **quick scale (1本指ダブルタップ+ドラッグでズーム) が有効**だと、単指 DOWN が内部の double-tap 監視に取り込まれて `GestureDetector.onLongPress` が間欠的に発火しなくなる（2本指ピンチ後にだけ直る症状）。本アプリは 2 本指ピンチのみ使うので `isQuickScaleEnabled = false` で OFF にする (0.8.16)。
- Compose `BasicTextField` で realtime PTY 入力は IME 同期破綻 → `TerminalInputView` + 自前 InputConnection。
- AndroidView の factory で `requestFocus` すると IME が勝手に出る。
- Mozc は `FORCE_ASCII` を無視する (日本語 IME で ASCII 入力は保証されない)。
- **システム(OS)キーボードの確定前インライン表示** (0.8.206): `onCreateInputConnection` の `inputType` を **`TYPE_NULL` から `TYPE_CLASS_TEXT`（+`TYPE_TEXT_FLAG_NO_SUGGESTIONS`）へ**変え、`IME_FLAG_FORCE_ASCII` を外した。`TYPE_NULL` だと多くの IME が変換 (composing) を行わず、日本語・予測入力の**確定前が端末に出なかった**。`TerminalInputConnection.setComposingText` は変換中テキストを PTY へ送らず `onComposingChanged` で画面へ渡し、`TerminalRenderer.composingText`（内蔵キーボードの `composing.text` と同じ描画経路）へ載せてカーソル位置にインライン表示する。確定 (`commitText`) / 変換終了 (`finishComposingText`) で PTY へ書いてインライン表示を消す。**実機での IME 挙動差（`NO_SUGGESTIONS` と CJK 変換の両立、`finishComposingText` 時の未確定文字の扱い）は要デバイス検証**。
- SGR run まとめ drawText でカーソルズレ → セル単位 drawText。
- KDoc 内に `*/`(例: `*.tgz`) を書くとコメント早閉じ。
- `setUnixMode` は owner-only 必須 (world-writable だと sudo 拒否)。
- proot launch で固定 `/bin/sh` だと busybox ash が走り zsh 機能が使えない → `resolveShell`。
- **chroot エンジンは su 経由だと制御端末を所有できず Ctrl+C/ジョブ制御が効かない** → login shell を `setsid -c` 経由で起動。
- **GUI 動画**: GPU 無し端末で mpv の `gpu` 出力は化け/半分描画になる → `vo=x11` 既定 + `LIBGL_ALWAYS_SOFTWARE`。
- **GUI 音声**: PulseAudio は `-n` 方式で起動しないと既存設定と競合。`AudioBridge` の接続先 port を 0 のまま渡すと無音（既定ポートを明示）。**z2root 配下では** `--daemonize` が `/proc/self/exe`（=ランチャ）の自己 re-exec で失敗する→`setsid …&` で背景化。AF_UNIX の `SCM_CREDENTIALS` は fake_root の uid=0 だとカーネルが `sendmsg` を `EPERM`→z2root が `sendmsg`/`recvmsg`(211/212) の ucred を実 uid へ書換（0.8.53）。
- **折り返し URL の検出**: wrapped フラグは「継続行」でなく「折り返し元の行」に持たせる（逆だと長 URL がタップできない）。

### 10.4 版ごとの修正記録 (0.8.110〜0.8.139)

Kitty graphics・SGR mouse・スワイプ振り分けの**現仕様**は [§4.5](#45-ターミナルエミュレータ-emulator) に整理してある。ここは版ごとの記録として残す (両者で表現が異なる場合は §4.5 が新しい)。

<details>
<summary><b>0.8.110〜0.8.139 の修正記録 (28 件)</b></summary>

- **SGR underline サブパラメータ (`4:n`) の正しい解釈 (0.8.139)**: `processCsi` が CSI の `:` 区切りを `;` 区切りと完全に同一視して `csiParams` に平坦化していたため、 styled underline を使う TUI が送る `\e[4:3m` (波線) を `[4,3]` と解釈し underline に加えてサブパラメータ値を別 SGR として誤適用していた (`4:3`→下線+イタリック、 `4:1`→下線+ボールド、 `4:5`→下線+点滅)。 さらに `\e[4:0m` (下線オフ) は `[4,0]` の `0` を全リセットとして処理し前景/背景色まで消していた。 結果として余計な装飾フラグが居残り、 styled underline を多用する TUI のあとに下線等が残留する。 修正: パーサに `csiParamIsSub: MutableList<Boolean>` と `csiPendingSub` を追加し、 `:` で確定したパラメータを「直前パラメータのサブパラメータ」として印付ける (`;` 区切りは false)。 `applySgr` の `4` を分岐し、 直後がサブパラメータなら `0`=`FLAG_UNDERLINE` クリア・非 0=セットとして扱い、 連続するサブパラメータは `while` で読み飛ばして別 SGR と誤解釈しない (`4` 単体は従来どおり単線下線)。 styled 種別 (1=単線/2=二重/3=波線/4=点線/5=破線) は描画上は一律下線として扱う。 38/48/58 の拡張色は従来の位置ベース読取りのままで退行なし。 テスト: 新規 `SgrUnderlineSubparamTest` (4 ケース: `4:3` が italic を立てない / `4:5` が blink を立てない / `4:1` が bold を立てない / `4:0` が色を保持して下線だけ消す) + `SgrUnderlineAltScreenExitTest` (1 ケース: `?1049h`→`4:3m`→`?1049l` 復帰後に通常テキストへ下線が残らない)。 仕様: <https://sw.kovidgoyal.net/kitty/underlines/>、 xterm `ctlseqs.txt` の "Set/Reset Text Attributes" のサブパラメータ表記。
- **SGR mouse 入力 タップ→click の opt-in 切り離し (0.8.138)**: 0.8.137 で `sendMouseClick` の発火条件を `isSgrMouseInputActive(sess)` (= opt-in `sgrMouseInputEnabled` ON + mouse capture 中) 配下に閉じ込めた結果、 既定 OFF の状態だと **mouse capture を有効化する TUI のタップが届かなくなる** microregression が出た (0.8.116〜0.8.136 は `mouseEnabled` だけで自動送出していたので、 既存ユーザーの体感では明確な退行)。 `TerminalInputView.onSingleTapUp` の判定を `sess.emulator.mouseEnabled && sendMouseClick(e.x, e.y, sess)` に戻して、 「mouse capture 中はタップ→SGR click (button 0 press+release) を opt-in 関係なく送る」を復活させた。 ロングタップ→右クリック (button 2) と 1 指 drag→motion (button 32) は引き続き opt-in (`AppSettings.sgrMouseInputEnabled`) 配下に残す: opt-in OFF (既定) の挙動は「タップ→TUI click + 長押し/1 指 drag は Z2Term 自身の選択 / scrollback / wheel」 という 0.8.136 と同じベースになり、 opt-in ON で右クリック / drag motion が追加で動くという段階構成になる。 既存テスト (`MouseEncodeTest` 14 ケース) は退行なし、 タップ→click は 0.8.116 以降ずっと encode 経路を共有しているので追加テスト無し。
- **SGR mouse 入力 (タップ→マウスイベント) opt-in (0.8.137)**: 0.8.116〜0.8.126 で SGR mouse の **wheel 送出 (button 64/65)** と alt screen での慣性 wheel 送出までは入れていたが、 **1 指タップ / ロングタップ / 1 指ドラッグ** を SGR (`\x1b[<n;col;row>M/m`) として PTY master に書き出す経路は未実装で、 mouse capture を要求する TUI 系 (日付選択 pane / ファイラ / 副 pane フォーカス切替 / 本文 caret 位置決め) が **タップで何も起きない** 状態だった。 既定 OFF の opt-in (`AppSettings.sgrMouseInputEnabled`, DataStore key `sgr_mouse_input_enabled`) として 3 種を追加: (1) **1 指タップ → button 0** の press+release (`\x1b[<0;col;row M` + `\x1b[<0;col;row m`)、 (2) **1 指長押し → button 2** の press+release (右クリック相当)、 (3) **1 指ドラッグ → button 0 press + button 32 motion 連発 + button 0 release** (motion は BUTTON_EVENT/ANY_EVENT 必須、 NORMAL は既存仕様で motion を捨てるので安全)。 `TerminalInputView` に `sgrMouseDragActive` / `sgrMouseLastCol/Row` の drag 状態を新設し、 `onScroll` でセル変化時のみ motion を発行 (同セル内連続 motion の流量制御)、 `onTouchEvent` の ACTION_UP/ACTION_CANCEL で必ず button 0 release を送って TUI 側の press 状態 stuck を防ぐ (drag 中に view 外へ抜けた場合は最後の有効セル位置で release)。 ヘルパ `isSgrMouseInputActive(sess)` で 「opt-in ON かつ `?1000`/`?1002`/`?1003`/`?1006` で mouse capture 中」を一元判定。 opt-in OFF (既定) ではタップ / 長押し / 1 指ドラッグはすべて Z2Term 自身の操作 (フォーカス / IME / テキスト選択 / scrollback スワイプ) に使われ、 0.8.136 までの挙動を完全保持する。 二本指スワイプ→wheel (button 64/65) は opt-in に関係なく従来通り送出する (1 指 swipe を wheel として扱う既存の alt screen 経路も温存)。 opt-in ON 中は 1 指 swipe が drag に振り替わるため `e2.pointerCount == 1` でガードして 2 指以上の swipe は既存 wheel 経路に流す。 設定 UI: 「設定 → 実験的 / 開発者向け」セクションに「SGR mouse 送出 (タッチ→マウスイベント変換)」トグル + ON 時の警告文 (`settings_sgr_mouse_input_*` strings, ja/en) を Kitty 外部ファイルトグルの直下に追加。 反映は即時 (combine 監視・再起動不要)。 テスト: `MouseEncodeTest` を 10 → 14 ケースへ拡張 (右クリック press/release のバイト列固定 / 1 指ドラッグ motion の button 32 + 'M' 終端固定 / NORMAL は motion を抑止して null / BUTTON_EVENT は motion 許可)。 既存の wheel / left click / 各 encoding / DECRST 連動 10 ケースは退行なし。 残スコープ: bracketed paste (`?2004`) と focus in/out (`?1004`) は別マター (今回は未対応)。 仕様: <https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#Mouse_Tracking>、 xterm `ctlseqs.txt` の "Any-event tracking" / "SGR (1006) mouse"。
- **Kitty graphics の file/temp/shm 転送 opt-in (`t=f`/`t=t`/`t=s`) (0.8.136)**: 0.8.135 までは file/temp/shm 経路を一律 `Discard`、 `a=q` でも `ENOTSUPPORTED:t=…` を返していた。 image viewer / 文書プレビュー系の TUI は **base64 ペイロードではなくファイルパス** で画像を渡す設計 (`a=T,t=f,f=100;<base64(path)>`) が主流で (大きい PNG を base64 で inline すると CPU/メモリが嵩むため)、 この経路を持たないと「ファイルベースで送る系の TUI は何も描かれない」状態。 段階 10 として **既定 OFF の opt-in 経路** を入れて、 セキュリティを保ったまま受けられるようにする。 設計: `KittyGraphicsParser` に `enum TransferKind { File, TempFile, SharedMemory }` と `fun interface ExternalTransferSource { fun read(kind, name, offset, size): ByteArray? }` を導入し、 parser フィールド `externalTransferSource: ExternalTransferSource? = null` で外部 I/O への射出口を持つ。 `handleTransmit` / `handleFrame` の base64 → inflate ロジックを共通ヘルパ `obtainPayloadBytes(header, payloadStr)` に括り出し、 `t=d` は base64 → maybeInflate、 `t=f`/`t=t`/`t=s` は base64 でパス文字列を取り出して `source.read(kind, name, O, S)` に委譲、 戻り値に対して `maybeInflate` を一様に適用する形に正規化。 Kitty 仕様の `O=N` (offset) / `S=N` (size) もこの経路で source に渡る。 `a=q` (query) も拡張し、 source 注入済みなら `t=f`/`t=t`/`t=s` を `OK` で受け、 未注入なら `ENOTSUPPORTED:t=…` を返す (TUI が capability で経路を選べるようになる)。 unit test 環境で委譲が走らない (`android.util.Base64` が Robolectric なしで stub されていない) のを避けるため、 parser の base64 デコードを `java.util.Base64.getDecoder()` に切替えた (minSdk 29 = Java 8 同等で利用可、 Kitty 仕様は標準 base64 なので互換)。 ホスト側実装は新規 `KittyHostTransferSource(rootfsRoot: File)`: file/tempfile はゲスト絶対パスを `<rootfsRoot>/<guest path>` に rebase、 shm 名 `/<name>` は `<rootfsRoot>/dev/shm/<name>` に rebase。 セキュリティ多層化として、 (1) path traversal (`/../` 含む文字列) を入力段で reject、 (2) `canonicalFile` で **最終パスが rootfsRoot 配下に収まること** を再確認、 (3) 1 回の読込上限 16 MiB (zip-bomb / DoS 対策、 zlib 展開上限と同じ閾値)、 (4) `TransferKind.TempFile` は読了後 `delete()` で即 unlink、 (5) file/tempfile は絶対パス必須・shm 名にスラッシュ禁止、 を入れる。 `O`/`S` の offset/size 指定にも対応し、 ファイル長を超える offset / 上限超過 size は null で拒否。 設定経路: `AppSettings.kittyExternalFileEnabled: Boolean = false` (DataStore key `kitty_external_file_enabled`) を新設、 `TerminalSession.applyKittyExternalTransferSetting` で opt-in が ON かつ rootfs が解決可能なときだけ `KittyHostTransferSource` を `TerminalEmulator.setKittyExternalTransfer` に注入し、 OFF へ戻れば null で外す (combine 監視で動的反映)。 `SettingsSheet` の「実験的 / 開発者向け」セクションに「Kitty graphics: 外部ファイル転送」トグル + 有効時の警告文を追加 (`settings_kitty_external_file_*` strings, ja/en)。 セキュリティ評価: opt-in OFF が既定で未許可セッションは parser レベルで完全停止、 ON でも (a) rootfs 配下に限定、 (b) 16 MiB 上限、 (c) `..` 拒否、 (d) `TempFile` 自動 unlink で多層化される。 テスト: `KittyGraphicsParserTest` を 30 ケースへ拡張: `externalTransferIsDiscardedWhenSourceNotAttached` (source 未注入で Discard) / `externalTransferFileDelegatesPathToSource` (path/offset/size の委譲確認) / `externalTransferTempFileDelegatesAsTempKind` / `externalTransferShmDelegatesAsSharedMemoryKind` / `queryFileTransferReturnsOkWhenSourceAttached` / `queryShmTransferReturnsErrorWithoutSource` / `frameFileTransferDelegatesToSource`。 新規 `KittyHostTransferSourceTest` 12 ケース: 全長読込 / offset+size slice / 負 size = 末尾 / TempFile 自動 unlink / shm の `/dev/shm` rebase / `..` path traversal 拒否 / 相対パス拒否 / empty name 拒否 / 未存在ファイル null / offset がファイル長超過で null / size 0 で空配列 / 上限超過 size で null。 これで Kitty graphics protocol の主要スコープ (段階 1〜10) が一通り揃う。 ファイル経由の Bitmap 組立検証は実機検証へ繰り越し (Bitmap が unit test 環境で動かないため、 image viewer 系 TUI で実際にファイル送信されたとき正しく表示されるか)。 残スコープ: なし。
- **Kitty graphics の zlib 圧縮入力 (`o=z`) と query 拡張 (0.8.135)**: 0.8.134 までは base64 デコード後の生バイトをそのまま PNG / RGB / RGBA 入力として扱っていたため、 `chafa --format kitty --compress` や **png 圧縮を素通ししたい TUI** が `o=z` (Kitty 仕様の zlib 圧縮指定) を有効にすると payload が解釈不能で画像が出ない状態だった。 段階 9 で `o=z` の inflate 経路を追加。 新規ヘルパ `inflateZlib(bytes)`/`maybeInflate(header, raw)` を `KittyGraphicsParser` に追加し、 `java.util.zip.Inflater` で展開。 zip-bomb 対策として展開出力が 16 MiB を越えたら途中で打ち切って null を返し呼び元で `Discard`。 `handleTransmit` (a=T/t/p) と `handleFrame` (a=f) で base64 デコード後に必ず `maybeInflate` を通すよう変更し、 `o=z` 以外の値 (将来仕様向け) も null → `Discard` で安全側に倒す。 `a=q` (query) も `o=` を見るよう拡張: `o=z` は OK 応答、 それ以外は `ENOTSUPPORTED:o=<value>` を返す (TUI が capability 判定で zlib 経路を選べるようになる)。 生 RGB(A) のサイズ検証 (`s` × `v` × `bpp` を超える/不足する payload は `Discard`) は 0.8.129 で既に入っていて、 圧縮展開後にも同じ検査が走るので、 「圧縮 payload で `s`/`v` を偽装する」経路にも従来同様の堅牢性が得られる。 `KittyGraphicsParserTest` を 25 ケースへ拡張: `transmitWithMalformedZlibDiscards` (zlib magic を欠く payload → `Discard`)、 `queryWithUnknownCompressionReturnsError` (`o=q` 等の未対応値 → ENOTSUPPORTED 応答)、 `queryWithZlibCompressionReturnsOk` (`o=z` → OK)。 zlib 展開後の Bitmap 組立検証は実機検証へ繰り越し (Bitmap が unit test 環境で動かないため)。 残スコープ: file/temp/shm 転送 (`t=f`/`t=t`/`t=s`) のみ、 これは z2root 環境下のファイルアクセス権限・SHM 経路の semantics 検討が必要なため当面保留。
- **Kitty graphics の Animation 再生 (0.8.134)**: 0.8.133 で `a=f` の **蓄積** までは入れたが、 描画は常に frame 0 (= `imageCache` の原画像) を返し続けていて、 ユーザー目線では「アニメは送ったが動かない」状態だった。 段階 8 として実際の **frame 切替** と **delay 駆動再生** を入れる。 `TerminalBuffer` 内に `AnimationPlaybackState(currentFrame, lastSwitchMs)` を private class で持ち、 `animationStates: MutableMap<Int, AnimationPlaybackState>` で per-imageId 管理する。 描画前に Renderer 側から呼ぶ `advanceAnimations(nowMs): Boolean` が state machine の本体: state 未初期化なら frame 0 / `lastSwitchMs = nowMs` で作り、 `nowMs - lastSwitchMs >= 現在 frame の delay` を満たしたら次 frame に進めて (`(currentFrame + 1) % (1 + frames.size)`)、 `lastSwitchMs` を更新して true を返す。 frame 0 の delay は仕様外なので `frames[0].delayMs` を流用 (Kitty TUI は frame 0 の delay を別途指定しないため、 これで「最初の frame の delay と同じテンポで頭に戻る」挙動になる)。 frame 取得は `currentBitmap(imageId): Bitmap?` が「state 未初期化 / `currentFrame == 0` → `imageCache[imageId]`、 `currentFrame >= 1` → `animations[imageId][currentFrame - 1].bitmap`」を返す。 `addAnimationFrame` を呼んだ時点で該当 imageId の `animationStates` を削除し、 新フレーム到着で頭から再生し直す挙動にする (途中 frame で「コマ送り」が崩れないため)。 Renderer 側は `drawImagePlacement` / `drawPlaceholderTiles` で `buf.currentBitmap(imageId) ?: img.bitmap` (or `?: spec.bitmap`) と差し替えるだけで、 引けなければ従来通り source bitmap を描く (= imageId=0 や animation 無しの placement は退行しない)。 再生 driver は `TerminalRenderer` の Composable に `LaunchedEffect(session.id)` を新設し、 `hasActiveAnimations()` が true の間だけ `withFrameMillis` でフレーム同期し、 `advanceAnimations` が true を返したら local `animTick` を `mutableIntStateOf` で bump して Canvas 描画を recomposition で再走させる。 アイドル時は `delay(100)` で「アニメが新規 push された?」を軽くポーリング (HashMap.isEmpty チェックのみで cost 無視できる)。 `clearAllImages` (`a=d,d=A`) と `deleteImageById` (`a=d,d=I`/`d=i`) は `animationStates` も `animations` と連動して clean up する。 検証: 新規 `AnimationPlaybackTest` を 3 ケース追加 (`hasActiveAnimationsIsFalseInitially` / `advanceReturnsFalseWhenNoAnimations` / `currentBitmapForUnknownIdReturnsNull`)。 frame を実投入する経路は `android.graphics.Bitmap` が unit test 環境 (Robolectric 未導入) で構築できないため、 実機検証 (`chafa --format kitty --animation` 等の出力が動くか) へ繰り越し。 残スコープ: `o=z` zlib 圧縮入力 (段階 9) と file/temp/shm 転送 (security 要検討)。
- **Kitty graphics の Animation frame 蓄積 (0.8.133)**: 0.8.131〜0.8.132 で「画像を置く・id を識別・32bit 化」までは通したので、 段階 7 として Kitty animation protocol の **frame 蓄積** を入れる。 段階 7 では受領・蓄積のみで実再生は段階 8。 多くの画像表示 TUI / GIF プレビュー TUI は `a=T` で frame 0 (= 原画像) を送ったあと、 同じ image id に対し `a=f` を繰り返して 2 枚目以降のフレームを足し込む設計のため、 `a=f` を `Discard` のままにすると frame 0 だけが固まって表示され続け、 アニメーションが「静止画として表示される」状態になる。 新規 `AnimationFrame` (`bitmap` + `delayMs` + `composeMode` + `xOffset` / `yOffset`) を `TerminalImage.kt` に追加し、 `TerminalBuffer` に `animations: MutableMap<Int, MutableList<AnimationFrame>>` を新設。 `addAnimationFrame` / `getAnimationFrames` で追記・取得し、 `clearAllImages` (`a=d,d=A`) と `deleteImageById` (`a=d,d=I`/`d=i`) では imageCache / virtualPlacements に揃えて連動削除する。 `KittyGraphicsParser` に action `f` の経路 (`handleFrame`) を新設し、 `Result.Frame(imageId, bitmap, delayMs, composeMode, xOffset, yOffset, frameIndex, quietLevel)` を返す。 Kitty 仕様で **`a=f` のときだけ `z=N` は Z-index ではなく delay (ms)** を意味する (既定 40ms)。 parser でアクション別に振り分け、 `if (action == "f") 0 else (header["z"]?.toIntOrNull() ?: 0)` で Z-index 経路と切り分ける。 `i=N` 必須 (0 は `Discard`)、 `t=d` のみ (file/temp/shm は `Discard`)、 Bitmap 組立失敗 (`buildRawBitmap` null / PNG decode 失敗) も `Discard`。 `TerminalEmulator` は `Result.Frame` を `buffer.addAnimationFrame` にディスパッチするだけで、 frame 0 (= `imageCache` の原画像) は従来どおり描画される。 frame 切替・delay 駆動再生は段階 8 (0.8.134 想定) で `Choreographer` または `Handler` を絡めて入れる。 `KittyGraphicsParserTest` に 3 ケース追加: `frameWithoutImageIdDiscards` (imageId 0 で `Discard`) / `frameWithoutPayloadDiscards` (payload 空で `Discard`) / `frameWithFileTransmissionDiscards` (`t=f` で `Discard`)。 Frame の delay/compose/offset の中身検証は Bitmap 組立が unit test 環境で動かない (`Bitmap.createBitmap` / `BitmapFactory.decodeByteArray` が null) ため実機検証へ繰り越し。 残スコープ: animation 再生 (段階 8) / `o=z` zlib 圧縮 / file/temp/shm 転送 (security 要検討)。
- **Kitty graphics の image id 32bit 拡張 (0.8.132)**: 0.8.131 で導入した Unicode placeholder セルの image id 抽出は fg truecolor の **24bit のみ** で、 多数画像を扱う TUI で id 衝突を踏み得る状態だった。 Kitty 仕様は「上位 8bit を underline color の R 値で受け渡す」設計のため対応。 `TerminalEmulator` 側に `currentUnderlineColor: Int` 状態を新設し、 `applySgr` に SGR 58:2:R:G:B (RGB underline) / 58:5:idx (indexed underline) / 59 (reset) を追加。 SGR 0 (全リセット) でも `currentUnderlineColor` を `SgrAttribute.DEFAULT` に戻す。 `putKittyPlaceholder` で `isRgb(currentUnderlineColor)` のときに R 値を上位 8bit として `(id32high shl 24) or id24` で OR し、 `PlaceholderRef.imageId` に 32bit を詰める。 underline 自体の描画は本実装では行わない (placeholder の id 受け渡し専用) ため `TerminalCell` の構造は据え置き。 `KittyPlaceholderCellTest` に 3 ケース追加 (`underlineColorAddsUpperEightBitsOfImageId` / `sgr59ResetsUnderlineColorSoImageIdStays24bit` / `sgrResetClearsUnderlineColorToo`) で計 9 ケース。 残スコープ (animation frames / file/temp/shm 転送 / 圧縮 `o=z`) は引き続き保留。
- **Kitty graphics の Virtual placement (Unicode placeholder) を実装 (0.8.131)**: 0.8.130 までで「query 応答 + 描画 + 多 placement + 削除 + Z-index 2 層」までは通したので、ここで **Unicode placeholder 経由の遅延配置** を入れる。 多くの TUI (画像ビューア / 文書描画 / image preview を多用するもの) は、 image bitmap の登録と 描画位置の指定を分離する設計で、 まず `\e_Ga=T,U=1,i=N,f=100,t=d,…\e\\` で画像本体を **virtual placement** として登録 (cursor は動かさず登録だけ)、 続いて本文中に `U+10EEEE` (Kitty 仕様の placeholder 文字) + combining diacritic で「画像のどのタイルをどのセルに置くか」を 1 セル単位で書く。 これに対応しないと「query は OK を返したのに画像が一切出ない」状態になる (画像本体は受け取れているのに置き場が決まらない)。 `KittyGraphicsParser.Result.VirtualPut` を新設 (`a=p,U=1`) し、 `Transmit` には `unicodePlaceholder: Boolean` を追加 (`a=T,U=1`)。 emulator は `TerminalBuffer.virtualPlacements: Map<imageId, VirtualPlacementSpec>` に「画像 bitmap + grid 列数 / 行数 + Z-index + placement id」を登録する。 placeholder セル側は `TerminalCell.placeholder: PlaceholderRef?` (image id + srcRow + srcCol + placementIdLow) で持つ。 `TerminalEmulator.putCodepoint` を拡張して: (a) `U+10EEEE` を検知したら専用の `putKittyPlaceholder` で 1 セル幅で書き、 直前 SGR の **truecolor fg** (`\e[38;2;R;G;B`) を `(R<<16)|(G<<8)|B` で image id 24bit として `PlaceholderRef.imageId` に詰める。 (b) 直後に来る最大 3 個の combining diacritic (Kitty 固定の 297 要素表、`KittyPlaceholder.DIACRITICS` を `binarySearch` で逆引き) を読み、 1 番目=srcRow / 2 番目=srcCol / 3 番目=placementIdLow を順に上書きする (`applyPlaceholderDiacritic`)。 通常文字 (`putChar` / `putWideChar` / `putSurrogatePair`) や非 diacritic コードポイントが入ると stage を解除し、 以降の combining mark は通常テキスト扱い。 `TerminalRenderer` の Pass 2.7 (z<0) / Pass 3.5 (z>=0) は image placement のループに加えて新ヘルパ `drawPlaceholderTiles` を呼び、 行内のセルを走査して `cell.placeholder` を引いたら `buffer.getVirtualPlacement(imageId)` から spec を引き、 bitmap の `(srcCol / widthCells, srcRow / heightCells)` タイル領域を `drawBitmap` の srcRect→dstRect で 1 セル矩形に切り出す (spec 未登録のセルは描画スキップで「画像が来るまで空き」)。 削除コマンド (`a=d,d=A`/`d=I`/`d=p`) は通常 placement と同じく仮想 placement 登録も消す。 placeholder セルはコピー時の文字化け回避のため [`TerminalRow.toText`] と [`TerminalBuffer.getRangeText`] で空白に置換する。 セル上書き (`setChar` / `clear` / `setClearedWith` / `copyFrom`) では placeholder ref を必ずリセットして「画像セルに文字を書けば消える」を維持。 新規 `KittyPlaceholder.kt` に Kitty 仕様の 297 要素 diacritic 表 + `PlaceholderRef` data class を置く。 `KittyGraphicsParserTest` を 18 ケースへ (`VirtualPut` 判定 + 通常 `Put` 退行防止)、 新規 `KittyPlaceholderCellTest` 6 ケース (imageId 抽出 / srcRow+srcCol+placementIdLow 更新 / 通常文字後の stage 解除 / 連続 placeholder の独立 / toText の空白置換 / セル上書きで ref クリア) を追加。 残スコープ: Animation frames (`a=a`) と file/temp/shm 転送 (`t=f`/`t=t`/`t=s`) は引き続き保留。
- **Kitty graphics に query 応答 / quiet level / Z-index レイヤリングを追加 (0.8.130)**: 0.8.129 までで「画像を出す・消す・並べる」までは通したので、ここで **TUI 側からのケイパビリティ確認 (`a=q`)** に応答する経路を作り、 placement の重ね順を **2 層 (テキスト上 / テキスト下)** にする。 多くの TUI は起動時に `\e_Gi=N,a=q,t=d,f=N,s=1,v=1;\e\\` を投げて、応答 (`\e_Gi=N;OK\e\\` または `ENOTSUPPORTED:...`) で「この端末は Kitty graphics 対応か」を判定する設計のため、応答路がないと ASCII art フォールバックに落ちて画像が出ない。 `KittyGraphicsParser.Result.Query` を新設し、`TerminalEmulator` から既存の `output` コールバック経由で `ESC _ G [i=N] ; <message> ESC \` を返す。 quiet level (`q=0/1/2`) は Kitty 仕様どおり q=0 全部 / q=1 エラーのみ / q=2 無音。 Z-index (`z=N`) は `Transmit` / `Put` の両方に通し、`TerminalImage.zIndex` まで持ち回る。 Renderer 側は image ループを 2 段に分けて、`zIndex < 0` は **Pass 2.7 (背景の上、テキストの下)**、`zIndex >= 0` は **Pass 3.5 (テキストの上)** で描く。 同 Z 内は追加順 = 後勝ち。 これで「字幕付きサムネ」「アイコン重ね」「吹き出し風 placement」など TUI 側の表現が直接出るようになる。 `KittyGraphicsParserTest` に query 成功 (OK 応答) / query エラー (ENOTSUPPORTED で transmission 不一致) / query quiet level 伝搬 / Put の z 伝搬 の 4 ケースを追加して 16 ケース。 image 描画自体は引き続き unit test 環境では Bitmap 化が動かないので実機検証。
- **Kitty graphics に多 placement / 詳細削除 / 生 RGB(A) を追加 (0.8.129)**: 0.8.128 で最小描画 (`a=T,f=100,t=d` 単発 / `a=d` 全消去) まで通したので、次はアクション分岐と入力形式を本格化する。 `KittyGraphicsParser.Result` を `Transmit` (display フラグで `a=T`/`a=t` を区別) / `Put` (`a=p` のキャッシュ参照配置) / `DeleteAll` / `DeleteImage` / `DeletePlacement` / `Continue` / `Discard` の 7 種に再構成。 削除サブ `d=A`/`d=I`/`d=i`/`d=p` を Kitty 仕様どおりに振り分け、image id は大文字 `I=N` / 小文字 `i=N` のどちらでも引けるようにした (free/keep の差はキャッシュ管理の細部で吸収)。 入力形式は `f=24` (生 RGB, 3 bytes/px) と `f=32` (生 RGBA, 4 bytes/px) を追加。 `s=N`/`v=N` で受け取ったピクセル幅高から IntArray を組んで `Bitmap.createBitmap(…, ARGB_8888)` で生成する (PNG は引き続き `BitmapFactory.decodeByteArray`)。 多 placement 対応として [`TerminalRow.image: TerminalImage?`] を `images: MutableList<TerminalImage>` に変更し、同一 anchor 行に異なる `(imageId, placementId)` の placement を **並列保持** できるようにした。 ヒット時 invalidate (`setChar`/`clear`/`resize`) は「セル範囲に被さる placement のみ除去」の精度に上げ、他 placement は残す。 [`TerminalBuffer`] に画像キャッシュ (`imageId → Bitmap`) と `deleteImageById` / `deletePlacement` を追加し、`a=T`/`a=t` で登録、`a=p` で取り出し、`a=d` 系で削除する。 同一 anchor 行に同 `(imageId, placementId)` が再到着した場合は **置換** (位置上書き) 動作。 `KittyGraphicsParserTest` を 12 ケースに拡張 (`Continue` / `DeleteAll` / `DeleteImage(I=42)` / `DeletePlacement(i=7,p=3)` / `Put(i=11,p=2,c=4,r=2)` / `f=24 で s,v 欠落 → Discard` 他)。 animation / virtual placement / file 転送 (`t=f`/`t=t`/`t=s`) は引き続き範囲外。
- **Kitty graphics の最小描画を実装 (0.8.128)**: 0.8.127 で APC 本文を `State.STRING` で吸収して画面汚染を止めたところまでだった部分を、APC 本文を Kitty graphics protocol として解釈して **画像を描く** ところまで進める。 段階分けの目的は (1) 画面汚染を止める (0.8.127 で完了) (2) 最小描画 (本コミット) (3) 多 placement / animation / virtual placement の順で副作用を局所化する。 本コミットの対応範囲は `a=T,f=100,t=d` (transmit-and-display / PNG / direct base64) の単発と `m=1` 連続 + `m=0`/省略 終端のチャンク連結。 `a=d` を全消去にマップ。 解析は `KittyGraphicsParser` (key=value parser + base64 連結 + `BitmapFactory.decodeByteArray`)、画像は `TerminalImage` として anchor 行 (top-left のセルがある `TerminalRow`) の `image` プロパティに保持し、Renderer は anchor 行を描く回で `widthCells × heightCells` 矩形に `drawBitmap` で伸縮描画する (背景描画と文字描画の間の Pass 2.7 として挿入)。 画像セル数は `c=N`/`r=N` 指定があればそれを使い、なければ Bitmap のピクセル数を `TerminalEmulator.setCellMetricsHint` (Renderer から `cellW`/`lineHeight` を渡す経路を新設) で割って自動算出 (最低 1 セル)。 cursor は画像幅セルぶん右へ進める (改行は TUI が `\n` で送る前提)。 画像領域に文字書込み / `clear` / 範囲外になる `resize` が起きると `TerminalRow.image = null` で自動 invalidate して「画像に上書きすれば消える」直感に揃える。 `TerminalRow.copyFrom` でも `image` を引き継ぐので `DECSTBM` 領域内スクロールで画像がずれず残る。 多 placement / image id 別削除 / animation / virtual placement / file 転送 / 生 RGB(A) (`f=24`/`f=32`) は本コミットの範囲外で `Result.Discard` (段階 3 以降で順次追加)。 `KittyGraphicsParserTest` で `Continue` / `ClearAll` / `Discard` / `reset` の 9 ケースを固定 (`Image` は Bitmap 化が unit test 環境で動かないため実機検証)。
- **DCS/APC/PM/SOS の本文吸収を追加 (0.8.127)**: TUI が送る画像転送プロトコル / DCS 応答 / その他の「文字列系」エスケープシーケンスを `processEscape` の `else` に落としていたため、開始 1 バイト (`P`/`_`/`X`/`^`) を捨てたあとの本文 (key=value, base64 payload, 本文中の `\r` や `[<…M` 風並び) が GROUND 状態で受信され、画面に文字として書かれていた。これが 3 つの別症状として観察されていた: 画像転送本文の文字漏れ、DCS 内 CSI 風並びを CSI として誤解釈した「SGR mouse 風」の不可解な文字漏れ、本文に混ざった `\r` が GROUND の CR ハンドラを叩いて TUI 描画中に cursor が突然行頭へ飛ぶ。修正は `State.STRING` を新設し、`processEscape` で `P`/`_`/`X`/`^` を受けたら遷移、`processString` で **BEL (0x07) もしくは ST (`ESC \`)** まで本文を読み捨て、終端後 GROUND へ戻す。異常終端 (`ESC` + 非 `\`) は xterm 流儀で文字列扱いを打ち切り、続くバイトを ESCAPE で再解釈する。`StringStateAbsorbTest` で APC + 画像 payload, DCS + CSI 風並び, PM/SOS + BEL, 本文中 CR/LF, 異常終端の 5 ケースを固定。本コミット時点では画像描画は実装しておらず「画面汚染を止める」段階で、Kitty graphics 等の実描画は別バンプで段階的に追加予定。
- **primary 画面のスワイプを PTY 前景プロセス判定でゲート (0.8.126)**: 0.8.125 で primary の wheel 送信分岐を全廃したところ、primary 画面でマウスレポートを使う TUI のスクロールも止まる退行を踏んだ。原因と対策の整理: マウスレポート ON のまま戻ってきた stale 状態と「primary で正規にマウスレポート利用中」の区別がエミュレータ状態だけでは付かないため、`PtyProcess` 経由で **`tcgetpgrp(master_fd)`** を取り、前景プロセスグループがシェル PID と一致するときは wheel を送らず scrollback へ倒し、子プロセスが前景のときだけ wheel を送る。実装は `pty_jni.cpp::nativeForegroundPgid(fd)`、`PtyProcess.foregroundPgid()`、`ProcessChannel.hasForegroundChild` (デフォルト `true` で SSH 等のリモートチャンネルは判定不能扱い → 従来挙動)、`LocalPtyChannel.hasForegroundChild`（当初 `fg >= 0 && fg != shellPid`。ただし proot/z2root では `shellPid`＝エンジン pid でゲストシェルと別 pgid のため常に true になる欠陥があり、0.8.160 で**アイドル時の前景 pgid を基準に実測比較**する方式へ修正）、`TerminalSession.hasForegroundChild` を追加し、`TerminalInputView.onScroll` の primary 分岐の発火条件に `sess.hasForegroundChild` を AND する。これで「TUI 動作中 (子プロセス前景)」では wheel が届きスクロール可能、「TUI 終了後 (シェル前景)」では `mouseEnabled` が stale でも wheel を送らず scrollback リーク (`\e[<...M` がプロンプトに流出する症状) を防げる。0.8.124 の DECRST 1049/1047/47 auto-OFF はそのまま残し、シェル前景判定が取れない経路 (リモート PTY) の二重防御として併用。
- **alt screen 終了時のマウスレポート OFF 強制とフリング座標継承 (0.8.124)**: 2 件まとめての修正。(1) DECRST 1049 (rmcup) だけ送って DECRST 1000/1006 (マウス OFF) を送り忘れる TUI が exit すると、`emulator.mouseEnabled = true` のまま primary に戻り、スワイプが `TerminalInputView.onScroll` の primary 分岐 (`mouseEnabled && atBottom`) で wheel イベントを PTY に流出させ、`\e[<...M` がプロンプトにリテラル入力される問題があった。修正は `TerminalEmulator.kt` の DECRST 1049/1047/47 (alt→primary) の各分岐で `mouseProtocol = MouseProtocol.OFF` を強制し、stale 状態を端末側で掃除する (xterm 仕様では mouse mode は alt screen 状態と独立だが、現実には rmcup 経由で抜ける TUI がマウスを切り忘れるケースが多く、被害は primary シェルの readline 破壊に集中するため救う方が実用的)。自分で DECRST 1006/1000 を送る TUI は二重掃除になるだけで挙動不変。`MouseEncodeTest` に DECRST 1049/1047/47 → OFF を固定するテストを追加。(2) 複数ペインを持つ alt-screen TUI でフォーカス枠を高速スワイプすると、慣性段階で別ペイン (画面中央に重なる位置のペイン) が勝手にスクロールする問題があった。原因は `flingRunnable` が `sendMouseWheelRows` に渡す (col,row) を画面中央 (`rows/2, cols/2`) で固定していたため、wheel が常に画面中央のセルに届き受信側はそのセル下のペインをスクロールしていた (スワイプ中は `sendMouseWheelFromSwipe(e2.x, e2.y, ...)` が指の位置を渡すので正しい)。修正は `onFling` で `e2.x`/`e2.y` を `flingPxX`/`flingPxY` に保存し、`flingRunnable` がそれを `sendMouseWheelRows` に渡す。`sendMouseWheelRows` 内で `pixelToAbsCell` でセルへ落とし、null (view 外/未設定の `-1f`) の場合のみ従来の画面中央へフォールバック。あわせて `onFling` で `mouseWheelAccumDy` をリセットし、連続フリングの端数持ち越しを断つ。
- **full フレーバーの既定実行エンジンを z2root に変更 (0.8.123)**: 従来は full の `executionEngine` 既定が `ENGINE_PROOT` で、新規ユーザーは初回起動時に proot で立ち上がっていた。foss は元々 proot prebuilt を持たないため `ProotLauncher` 側で `BuildConfig.IS_FOSS` のとき強制的に z2root へ倒す実装になっており、full ↔ foss で初回挙動が分かれていた。実際の運用では z2root の方がメンテナンス上の主軸で、`AppSettings` のコメント (「`executionEngine` の既定は proot」) も実態とズレていた。修正は `AppSettings.Snapshot.executionEngine` の既定値と、DataStore 未設定時のフォールバックをそれぞれ `ENGINE_PROOT` → `ENGINE_Z2ROOT` に変更。既存ユーザーで明示的に proot を選んでいる場合は KEY_ENGINE が保存されているのでそのまま (= 退行なし)、新規インストールとリセット直後のみ z2root から始まる。エンジン選択 (7 タップ解放) で proot/chroot へ切替する経路は従来どおり。docs / README の「既定は PRoot」表現も z2root へ揃えた。
- **IME ローン語辞書に言語/ツール/OS/構文を追加 (0.8.122)**: 0.8.121 の初版は Git/Shell/Build/Network/UI まわり約 200 語のみだったが、`ぱいそん→python` が出ないという指摘で言語名・ツール・OS・コード構文を追加。総勢 ~310 語に拡張。追加カテゴリ: 言語 (python/ruby/java/javascript/typescript/kotlin/go/rust/swift/php/perl/scala/dart/lua/haskell/clojure/elixir/csharp/cpp)、ツール (nodejs/npm/yarn/pnpm/pip/gem/cargo/gradle/maven/bazel/make/cmake/docker/kubernetes/k8s/terraform/ansible)、エディタ (vim/neovim/emacs/vscode)、OS/ディストロ (linux/ubuntu/alpine/kali/arch/debian/fedora/windows/mac/android/ios)、コード構文 (print/return/else/break/continue/try/catch/throw/finally/namespace/public/private/protected/static/abstract/interface/inherit/override/annotation)。仕組みは 0.8.121 と同じ (`LOANWORD_ENTRIES` → `buildLoanwords()` → `ensureLoaded` の 3 段マージ)。
- **IME に英単語ローン語の内蔵辞書を追加 (0.8.121)**: SKK 辞書はカタカナ外来語の hiragana 読み (こみっと / ぷっしゅ / おーけー …) を英単語綴り (commit / push / ok …) へ落とすエントリを持たないため、`convert("こみっと")` が空で、ユーザーが端末で英単語を打つたびに英字キーボードへ切替える必要があった。`KanaKanjiConverter` に内蔵テーブル `LOANWORD_ENTRIES` (Git/Shell/Build/Network/UI まわりで頻出する ~200 語、候補は英語小文字のみ) と `buildLoanwords()` を新設し、`ensureLoaded` で `mergeDict(mergeDict(base, buildSupplement()), buildLoanwords())` のように 3 段マージする。`mergeDict` は同見出しで衝突したとき extra 側の候補を先頭に置く挙動で既に作ってあるので、ローン語が辞書 hiragana 候補より先に並ぶ。`convertFlexible` の N-best 経路を変えず学習履歴と並列で出すため、一度ユーザーが選んだ綴りは [`ImeHistoryStore`] により次回以降ランキング上位に上がる。日本語固有語との衝突を避けるため、`ぼたん→button`/`たぶ→tab` のような短い hiragana は採用せず、確実なカタカナ語のみ収録。
- **新タブの PTY rows/cols が画面と未同期になる退行を修正 (0.8.120)**: 新規タブを開いた直後、キーボードと画面末端の間に「空行ぶんの隙間」が出て、長行が画面端で折り返さず画面外へはみ出すユーザー報告。`TerminalRenderer` の `BoxWithConstraints` 内で `LaunchedEffect(rows, cols) { delay(120); session.onResize(rows, cols) }` が PTY サイズを同期していたが、キーが `(rows, cols)` だけで `session.id` を含んでいなかったため、**同寸の新規タブへ切り替えたときラムダが再評価されず**、新セッションの PTY が初期値 (24×80) のまま残っていた。ピンチで `fontSize` が変わると `rows/cols` のキーが変わって `onResize` が再走するので「ピンチすると直る」現象になっていた (`updateCellMetrics` 側は 0.8 系のどこかで `session.id` を足して同じ退行を解消済みだったが、`onResize` の方は取り残し)。修正は `LaunchedEffect(session.id, rows, cols)` に変更し、タブ切替で必ず再走させる。120ms デバウンスはピンチ連打の防護として維持。
- **alt screen のスワイプを両方向 PTY ホイールへ送る (0.8.119)**: 0.8.115/0.8.116 の振り分けは「下方向 (=過去を見たい) は scrollback フォールバック」を前提にしていたが、これは scrollback を持つ primary 画面 + 読み物 TUI を念頭にしたもの。**alt screen TUI** は `?1049h`/`?47h` で代替画面に切り替わり `buffer.primaryActive == false` となるため scrollback サイズが常に 0 で、下方向スワイプが scrollback フォールバックに落ちると無反応 (「タッチスライドでスクロールが下にしか行かずに上に行けない」というユーザー報告)。修正は `onScroll` で `isAltScreen = !buffer.primaryActive` を判定し、alt screen のときは distanceY の符号を問わず `sendMouseWheelFromSwipe` を呼ぶ。`sendMouseWheelFromSwipe` は符号付き `mouseWheelAccumDy` で蓄積し、正ノッチで wheel-down (button 65)、負ノッチで wheel-up (button 64) を送る (途中の方向反転は蓄積で自然に打ち消し)。`onFling` も同様に alt screen のときは方向ガード (`velocityY < 0 && scrollOffset==0` の no-op) をスキップし、`flingRunnable` 内で alt + mouseEnabled なら新ヘルパ `sendMouseWheelRows(delta)` を呼んで慣性ぶんも PTY ホイールに変換し、alt-screen TUI でも慣性スクロール感が残る。primary 画面の挙動は変更なし (0.8.115/0.8.116 の振り分けを完全保持)。
- **z2root: try_subst_proc_open に dirfd 解決を追加 (0.8.118)**: 0.8.117 の検証で **procps-ng の `pgrep`/`pidof`/`ps -o comm`/`top` が依然として `libz2root.so` を表示**することを発見。原因は procps-ng の `readproctab2` が `opendir("/proc")` で取った dirfd に対し `openat(dirfd, "<pid>/stat", ...)` のような**相対パス openat**で /proc 配下を読むのに対し、`try_subst_proc_open` が pathname 引数の絶対 `/proc/` 始まりだけを `proc_open_kind` で判定していたため、相対パス経路では非対象扱いで temp 差し替えが発火せず、元の `(libz2root.so)` がそのまま流れていた (`cat /proc/<pid>/stat` のような絶対パス openat だけは正しく `(bash)` に書き換わっていた=見え方が経路で割れる原因)。修正は `try_subst_proc_open` 冒頭で、`raw[0] != '/'` かつ `dirfd != AT_FDCWD` のときに `/proc/<self_pid>/fd/<dirfd>` の readlink で dirfd の指すホスト実パスを取り、`<dirpath>/<raw>` に正規化してから `proc_open_kind` 判定する。dirpath が `/proc` 配下でない場合は素通し。差し替え成功時に元が dirfd 相対だった場合は `regs[0]` を `AT_FDCWD` に倒し、tmp の絶対パスが procfd 経由と認識されないようにする (`/proc/<pid>/fd/AT_FDCWD` などの誤解釈防止)。
- **z2root: /proc/<pid>/stat の括弧 comm と /proc/<pid>/status の Name 行を短縮時に左シフトで詰める (0.8.117)**: 0.8.112/0.8.113 で `/proc/<pid>/{stat,status}` の comm/Name を argv0 basename (例: `bash`) に書き換える実装を入れたが、後続フィールド (stat=`state ppid …` / status=`Uid: Gid: …`) のオフセットを崩さないため**長さ保存で末尾空白パディング** (`(bash        )` / `Name:\tbash         \n`) していた。SSH 経由の実機検証で **procps-ng の `pgrep bash` / `pidof bash` がヒットしない・`top` の COMMAND 列が `libz2root+` のように先頭から表示幅で切られる**現象を発見。procps-ng は comm を末尾空白付きで完全一致比較するため `bash<spaces>` と `bash` が一致しなかった。修正は `fake_stat_comm` / `fake_status_name` を**新 name が元より短い場合は閉じ `)` 以降 (stat) / 次行 (status) を `memmove` で左シフト**してバッファ全体を短縮する版に変更し、戻り値で新しい長さを返す。呼び出し元 2 経路 (readfree の `try_subst_proc_open` は temp 書き出し長を `total` で更新、非 readfree の `fake_proc_on_read` は `write_tracee_mem` 後に `regs[0]` (read 戻り値) を更新) で長さ変化を反映。status は `fake_status_name` を `fake_status_buf` より**先に**呼ぶ順番に直し、Uid/Gid/Cap*/Groups の length 保存書換が短縮後のバッファに対して走るようにした。
- **scrollback 中の上方向スワイプを「最新側へ戻る」操作として吸収 (0.8.116)**: 0.8.115 は `mouseEnabled` 中の上方向スワイプを常に wheel-down として PTY へ送っていた。ただし `TerminalSession.writeBytes` は「typing 結果が見えるよう scrollback をリセット」する設計で先頭に `_scrollOffset.value = 0` を含むため、scrollback で過去ログを読んでいる途中に少しでも wheel-down を送ると **scrollback が一気に 0 にリセット＝視点が最下端へジャンプ**してしまい、「下スクロール (=次へ) で勝手に一番下まで飛ぶ／一度上に行くともう最下端しか見られない」というユーザー報告につながった。修正は `onScroll` の wheel 経路の発火条件に **`scrollOffset == 0`** を AND 追加。scrollback > 0 のときは上方向も既存の scrollback ロジック (scrollAccumDy で吸収して `sess.scrollBy(-rowDelta)`) に倒し、最下端に近づいたら自動で wheel 経路へ移行する。フリングも同じ条件分岐 (`mouseEnabled && velocityY < 0 && scrollOffset == 0` のときだけ no-op) で、scrollback 中の最新方向フリングは慣性スクロールが効くようになる。`writeBytes` のリセット動作自体は他の入力経路 (キー入力等) で必要なので触らない。
- **スワイプを方向で振り分け・下方向は scrollback フォールバック (0.8.115)**: 0.8.114 で全方向のスワイプを wheel イベントに変換していたが、多くの読み物系 TUI は wheel-up (`evScrollUp` 相当) を**意図的に無視して端末の scrollback に任せる**設計のため、上方向スワイプ＝過去を見たい操作で「何も起こらない」状態になっていた。修正は `onScroll` で `distanceY > 0` (指が上 = 次へ) のみ `sendMouseWheelFromSwipe` を通し wheel-down を PTY 送信、`distanceY < 0` (指が下 = 過去) は従来の scrollback ロジックへフォールバック。`onFling` も `velocityY < 0` (上振り) のみ no-op、`velocityY > 0` (下振り) は scrollback 慣性スクロールを許可。`sendMouseWheelFromSwipe` も wheel-down 専用に簡略化 (button 固定、notch は正のみ)。あわせて [`MouseEncodeTest`](../../app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt) を新設し SGR/URXVT/LEGACY 各エンコーディングの出力 (先頭 ESC・button・terminator) と DECSET `?1000`/`?1006` の状態遷移を回帰固定。
- **マウスレポート ON のときスワイプを TUI 側ホイールへ送る (0.8.114)**: SGR マウスレポート対応の TUI でタップスクロールが反応しない症状の対応。TUI が `?1000h`/`?1006h` でマウスレポートを要求していても、`TerminalInputView.onScroll` のスワイプはそれを見ず scrollback 操作（`scrollOffset` 加減）に倒れていたため、TUI 側にホイールが届かずページが進まなかった。修正は `onScroll` で `emulator.mouseEnabled` を見て分岐し、`sendMouseWheelFromSwipe` で `encodeMouseEvent(button=64/65)` を生成して `sess.writeBytes()` で PTY へ流す。1 ノッチ = 40px (`MOUSE_WHEEL_STEP_PX`) で量子化し端数は次イベントへ繰越（既存の `scrollAccumDy` と同じ累積方式）、長いスワイプは abs(dy)/stepPx 回ぶん多行送りになる。あわせて `onFling` を `mouseEnabled` 中は no-op に＝慣性で勝手に scrollback を走らせない。クリック送信 (`sendMouseClick`) は従来通り維持。`mouseEnabled = false` の通常タブでは既存挙動を完全に保つ。
- **z2root の `/proc/<pid>/stat` field 2 も argv0 basename へ (0.8.113)**: 0.8.112 で `comm` と `status:Name` は直したが、busybox/procps 系の `ps` は速度のため `/proc/<pid>/stat` を 1 ファイル一気読みする経路を使い、その field 2 `(<comm>)` がカーネル設定のまま `(libz2root.so)` で漏れていた(結果 `ps -ef` 表示が `{libz2root.so} <実 argv>` の形でラベルが残る)。`PROC_FD_STAT` を追加し、`/proc/<pid>/stat` と `/proc/<pid>/task/<tid>/stat`(全体統計 `/proc/stat` は除外)を `fake_stat_comm` で length 保存書換。`comm` 内に `(`/`)` を含み得るため右端 `") "` を境界に使う。
- **z2root の `/proc/<pid>/cmdline`・`comm`・`status:Name` をローダ漏れから復元 (0.8.112)**: z2root は Android の W^X 制約上 `execve(libz2root.so)` でローダラッパー(`z2root --loader-noreloc <ld.so> <ld.so> --argv0 <argv0> <prog> ...`)を通すため、カーネルが `/proc/<pid>/cmdline` にラッパー argv を、`comm`/`status:Name` に `libz2root.so` を記録してしまう。結果 `ps -ef` / `pgrep <name>` / `pidof` / `top` がゲスト全プロセスで壊れる(proot は ld.so 経由経路で argv が原型保持されるため起きない)。**修正**: execve 傍受時に元の argv(と guest_prog basename)を per-tracee に控え、`/proc/<pid>/cmdline` / `/comm` 用に PROC_FD 種別を 2 つ追加して openat-time temp 差し替え(readfree 既定)に乗せた。`/proc/<pid>/status` の `Name:` 行は length 保存で argv0 basename へ in-place 書換(`fake_status_buf` の隣に `fake_status_name` を追加)。fork/clone は親の控えを子へ継承、execve 成功時に上書き。非 readfree(`Z2ROOT_NO_READFREE=1`)経路の `fake_proc_on_read` も同分岐に対応(cmdline/comm は長さが変わるため `regs[0]` も併せて調整)。
- **z2root の `/proc/self/exe` をゲスト視点へ書換 (0.8.111)**: `/proc/<tid>/exe` のカーネル symlink は execve 経路上 `libz2root.so`（または自前ローダ）を指すため、ゲストが `readlink("/proc/self/exe")` でホスト実パスを掴み、`open("/proc/self/exe")` も `ENOENT` で失敗していた。**症状**: Go ランタイムが起動段階で libbacktrace 用に `/proc/self/exe` を開けず `libbacktrace could not find executable to open` で即 panic（`go version` / `go build` 双方が走らない）。同じ経路で adb の `execl(自パス)` 系統や `--daemonize` 自己 re-exec も壊れる。proot は同等の hijack を持っていたため起きず、z2root のみの劣化。**修正**: execve(at)/ブートストラップ exec のタイミングでゲスト視点の絶対プログラムパスを per-tracee に控え、`host_path_for` の `/proc/<own pid>/exe` 検出時にそのパスへ差し替え、`readlinkat` exit で同じく返す。fork/clone は親の控えを子へ継承。`/proc/self/cwd` の逆変換（旧 0.8.60 で claude code の起動不能を直したもの）と同思想の追加対応。

</details>

## 11. l2s 制約と native passthrough

> **2026-06-22 起票・同日 Phase 1 で真因を「rename(2) 非 atomic」から「link2symlink × quarantine cleanup」へ訂正**。

Z2Term は `/root` 配下を **l2s（link2symlink overlay）** で見せている。proot の `--link2symlink` 由来で、`link(2)` を許さない Android アプリ FS 上で hardlink 意味論を擬装するための仕組み。実体は `pack-<sha>.pack → .l2s.tmp_pack_XXXX → .l2s.tmp_pack_XXXX.0001`（chain 末端 `.0001` が本物のデータ）のような多段 symlink 群で、`ls -la` の `-rw-` 表示や `[ -L ]` テストは当てにならない。

**エンジン差**: proot は現在も link2symlink で `.l2s` chain を新規生成する。z2root は 0.8.47 以降 `linkat` を「実 link 試行 → 失敗時コピー fallback」に変えており、**`.l2s` chain を新規生成しない**。本症状は proot 受け側固有。

### 11.1 症状: proot 受け側で git push が必ず壊れる

proot エンジンの tab に bare repo を置いて push を受けると、以下のエラーで壊れる:

```
error: unpack should have generated <sha>, but I can't find it!
remote rejected master -> master (bad pack)
```

- **ソフト無関係**: Gitea / Forgejo / GitLab も内部で `git receive-pack` を呼ぶので同症状。
- **プロトコル無関係**: SSH / HTTPS どちらでも壊れる。
- **設定で直らない**: `core.fsync=all` / `core.fsyncMethod=fsync` / `receive.unpackLimit=1`（unpack 経路にしても同じ link 経路を通る）はいずれも無効。
- **z2root 受け側では起きない**（2026-06-22 実証済み）。

### 11.2 真因: proot の `link()` emulation が quarantine 内 chain を指す

`git receive-pack` は隔離一時 dir `objects/tmp_objdir-incoming-*`（quarantine）にオブジェクトを書き、検証 OK 後に quarantine → `objects/<aa>/<sha>` への **migrate を `link()` で 1 つずつ行う**。最後に quarantine dir 全体を rmtree で削除する（git 内部の挙動・無効化設定なし）。

proot は `--link2symlink` 下で `link(src, dst)` を次の通り化かす:

1. `src` の内容を `<dst dir>/.l2s.tmp_<name>_<rand>0001` (chain 末端の実体) に置く
2. `dst` を **chain 末端の絶対パスへの symlink** にする

quarantine からの migrate でこの emulation が走ると、 `objects/<aa>/<sha>` は **quarantine 内の `.l2s.tmp_*` を指す symlink** になる。直後の quarantine rmtree で target が消え、`objects/<aa>/<sha>` は **dangling 化** → receive-pack 自身が検証のため読み戻す時に `unpack should have generated …, but I can't find it!` 発火。

= **`rename(2)` 非 atomic は誤診**。実際は `link()`-via-link2symlink + quarantine cleanup の組合せ。

### 11.3 やってはいけないこと

`.l2s.tmp_*` をゴミとして一括削除しない。**これらはデータ本体**（chain 末端 `.0001` が実体）。掃除してよいのは `find -xtype l -delete`（完全に dangling な symlink のみ）。

### 11.4 現状の運用回避

- **受け側を z2root エンジンに切り替える**（最即効・本セッションで実証済み）。z2root の modern linkat は `.l2s` chain を作らないので本症状は構造的に起きない。
- **受け側を l2s 外に置く**: PC をリポジトリサーバ化（採用済み）。
- **pre-push フックで quarantine をバイパス**: `pack-objects → index-pack → update-ref` を直接設置（導入済み）。

### 11.5 根治設計の方針

| 案 | 内容 | 評価 |
|---|---|---|
| **A 案** (旧) | z2root の `rename(2)` を atomic 化 | **棚上げ**。真因が rename 側ではなく proot link() 側と判明したため適用しても直らない。 |
| **B 案** | l2s をバイパスする native 領域 (`/var/lib/native`) を z2root が提供 | ユーザー判断で「`~/foo/.git` を救わないと意味無い」と却下済み。 |
| **C 案** | proot 起動時の `--link2symlink` を裏設定で OFF にする (`ProotLauncher.kt` L306) | OFF にすると dpkg/apt 等の hardlink 依存ソフトで EACCES が透ける副作用あり (0.8.47 以前の z2root と同様)。実装は軽量。 |
| **D 案** | proot prebuilt を fork して link2symlink の `dst dir` が `tmp_objdir-*` パターンを含むとき hardlink 失敗を素通させる patch を当てる | git の quarantine semantics を尊重する局所修正。third-party prebuilt の fork 維持コスト発生。 |
| **運用** | 「受け側 = z2root」「proot タブは利用専用」と運用ルール化＋ HANDBOOK 明記 | **最低コスト**。本件は engine 差で完全に区別できる。 |

### 11.6 関連

- `.l2s` がホスト絶対パスを抱え OS メジャーアップで stale 化する別問題がある。
- §10 末尾に散在する「z2root: ...」群（0.8.43〜0.8.118）は個別 syscall の翻訳バグで、本件とは別レイヤ。

---

## 12. 用語集

| 用語 | 意味 |
|---|---|
| PRoot | root 無しで chroot/bind/fakeroot を実現するユーザー空間ツール |
| rootfs | Linux ディストロのルートファイルシステム一式 |
| PTY | 擬似端末。アプリ ↔ シェル間の入出力経路 |
| forkpty | PTY を作りつつ fork する libc 関数 |
| SAF | Storage Access Framework。他アプリからファイルを開く Android の仕組み |
| dropbear | 軽量 SSH サーバ/クライアント。proot でも動く |
| SGR | Select Graphic Rendition。文字色/装飾の ANSI 制御 |
| EAW | East Asian Width。全角/半角の文字幅区分 |
| 共有ホーム | `filesDir/shared_home`。全 distro 共通の `/root` 実体 |
```
