# Z2Term 設計書 兼 仕様書

最終更新: 2026-09-05 / 対象バージョン: 0.8.511-alpha (versionCode 519)

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

- **root 不要**: `forkpty(3)` + **z2root** (自前のユーザー空間chroot/bindエミュレーション) で、
  通常権限のアプリ内に Linux ディストロ (Alpine / Ubuntu / Arch / Kali) を展開して動かす。
- **自前のターミナルエミュレータ**: xterm 互換の VT/ANSI 解釈を Kotlin で実装。
- **自前の UI / キーボード**: Jetpack Compose。独自フリックキーボード (英字 + 日本語/カタカナ + 数字) と OS IME を切替可能。
- **SSH 両方向**: 端末から外部へ (JSch クライアント)、PC から端末へ (dropbear サーバ)。
- **ファイル連携**: SAF DocumentsProviderで他アプリからrootfs/ホームをR/Wし、Linux内からAndroid共有ストレージへ`cd`。
- **GUI デスクトップ**: distro 内で Xvnc + 軽量 WM/アプリを起動し、内蔵 RFB(VNC) クライアントで表示（`gui/` パッケージ）。動画はソフト描画、音声はオプトインで PulseAudio→TCP→AudioTrack ブリッジ（`AudioBridge`）。
- **実行エンジン**: **z2rootのみ**。root端末では裏設定から**実chroot** (`su`経由bind mount + `chroot`) も選べる。
  - **裏設定の解放**: 設定 → アプリ情報のバージョン行を 7 タップ。トグル発火後 3 秒はバージョン行を**タップ不可**にして連打による即時再トグルを防ぐ (0.8.70。従来はタップを受けるが無視で不自然だった)
  - **chroot の解放**: root セルフテスト (`probeRootChroot`) の成功時のみ選択肢に加わる。このテストは **7 タップ解放の瞬間だけでなく、エンジン選択内の「chroot を有効化 (root を確認)」ボタンからも再実行できる** (0.8.106)。従来は解放時に 1 度だけ走り、su 許可ダイアログを拒否すると `rootChrootUnlocked` が false のまま二度と chroot を選べなくなっていた (再解放には再ロック→再解放の二重 7 タップが必要で気付けなかった)。false の間はこのボタンと案内文を表示し、何度でも再試行できる (成功で chroot 解放＋トースト、ボタン経由の失敗時のみ理由をトースト)
  - **失敗の切り分け** (0.8.107): `RootProbe.NoRoot` (su 無し/拒否) と `RootProbe.ChrootBlocked(detail)` (root は取れたが chroot 実行が SELinux/rootfs 等で失敗) を区別して表示する
  - ⚠️ **Magisk 等の root 管理アプリは一度「拒否」を記憶すると以後 su 許可ダイアログを再表示せず即拒否を返すため、アプリ内ボタンだけでは復帰できない** (アプリから他アプリの root 権限は変更不可)。この場合 Magisk 側で Z2Term の root を「許可」に戻す必要がある旨を NoRoot トースト/案内文で誘導する (0.8.108)
  - **0.8.328完全移行**: PRoot選択肢・fallback・prebuiltを削除し、full/fossともz2rootを使う。0.8.330でfullのAlpine同梱を復元。**0.8.359でfullフレーバーごと廃止**（下記）。
  - **z2root トレースログ** (開発者用・既定 OFF・`traceLogEnabled`): 同じ 7 タップ解放枠内のトグル。ON で z2root の全 syscall を `shared_home/z2root_trace.log` へ記録する＝障害調査用だが、ログが膨大で端末容量をすぐ圧迫するため UI に「普段は OFF のままにする」警告を添える (0.8.105。0.8.107 で警告文を「OFF のまま使用しない」という矛盾表現から非矛盾表現へ修正)。従来は `.z2root_trace_on` sentinel ファイルでしか切替できなかった (sentinel も後方互換で有効)

対応 ABI は **arm64-v8a のみ**。最低 Android 10 (API 29)、ターゲット API 35。

### 配布

配布は **1 種類だけ**。applicationId は `com.zerotoship.z2term`、ランチャー表示名は `Z2Term`。
z2root をソースからビルドし、rootfs は APK に同梱せず実行時に取得する。

⚠ **0.8.359 で配布フレーバーを廃止した**。それまでは `full`（`com.zerotoship.z2term`・Alpine rootfs 同梱・約 190MB）と
`foss`（`com.zerotoship.z2term.foss`・実行時取得・約 21MB）の 2 本立てだった。廃止の理由は
**「full」という名前が上位版に読まれて全員がそちらを落とし、しかも実際の違いは初回ダウンロードが
省けるかどうかだけだった**こと（利用者の判断）。残す applicationId はサフィックスの無い方にした
（`.foss` サフィックスは full と同居させるためだけに存在していたので、同居する相手が消えれば不要）。
⚠ **`com.zerotoship.z2term.foss` を入れている利用者は別アプリ扱いになるので自動更新に乗らない**。
入れ直しが要る（廃止時点でダウンロード数がごく少なく、後になるほど統合が効かなくなるため早期に判断した）。

`debug` ビルドは `.debug2` サフィックスが付き、表示名も `Z2Term dbg2` になるので release と共存できる。

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
| Linux 実行 | z2root | `app/src/main/cpp/z2root`から全flavorでビルド |
| Linux OS | Alpine / Ubuntu / Arch / Kali | APK非同梱。公式配布物を実行時取得 |

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

#### フォアグラウンド常駐とロック（`TerminalService`、0.8.143 / 0.8.268）

`TerminalService`（フォアグラウンドサービス）が常駐化を担い、バックグラウンドでも PTY を維持する。`AudioBridge`（GUI 音声）も同サービス系で扱う。

常駐中に握るのは `PARTIAL_WAKE_LOCK`（CPU を止めない）**だけ**。常駐 OFF（detach）・停止・破棄で解放する。

**省電力モードに従う（0.8.269）**: その `PARTIAL_WAKE_LOCK` も `serversLowPower` が ON なら握らない。0.8.268 まではこのサービスだけ設定を見ておらず、常駐サーバーを省電力モードにしても**こちらが握り続けるので設定が効き切らなかった**（常駐サーバー稼働中は 2 つのサービスが同じ WakeLock を 1 本ずつ持つ）。「電池を採る」と決めた人に対して片方だけ握り続けるのは約束を破っているので、フラグは 1 つで共有する。⚠ 判定は `onStartCommand` でしか行わないので、設定を変えたら `TerminalService.start` を呼び直して再判定させる（設定のトグルがそうしている。呼び直しは idempotent）。⚠ **トグルの置き場は 0.8.309 で ⚙設定 → 自動化 → プロセス保護へ移した**（`SettingsSheet`）。移設先へ呼び直しを持っていくのを忘れないこと。

**`WifiLock` はここでは握らない（0.8.268）**: 0.8.143〜0.8.267 は `WIFI_MODE_FULL_HIGH_PERF` の `WifiLock` も握っていた。これは Wi-Fi 無線の省電力 (PSM) を完全に止める指定で、画面消灯中も無線がフルパワーのままになり、電池と発熱に直接効く。無線を起こしたままにする役目があるのは**外から着信を受ける側**＝常駐サーバー（`ServerDaemonService`）で、そちらが同じ `WifiLock` を持っている。このサービスの役目は対話セッションのプロセスを生かすことだけなので、二重に握らない。

⚠ したがって**外部からの到達性は 🔒 では保たれない**（常駐サーバー側を動かす必要がある）。

⚠⚠ **ただし `WifiLock` を握れば到達性が保たれる、とは書けない（2026-07-28 実測）。** 0.8.267（`TerminalService` と `ServerDaemonService` が `WIFI_MODE_FULL_HIGH_PERF` を **1 本ずつ計 2 本**握っている状態）で、**Wi-Fi 再接続の直後に外部から ping も ssh も通らない**症状が実機で出た。このとき dropbear は listen を続けていて 127.0.0.1 からは入れる＝サーバー側は無罪で、**TCP 以前（ARP）で失敗**している。端末側から外向きに 1 回通信した瞬間に復活したので、原因は「Wi-Fi 省電力で端末が ARP に応答しない」でほぼ確定。

✅✅ **2026-08-20 に決着した（0.8.367）。上の推定は当たっていた。** 5 分半の消失中、**端末自身のログには 10 秒ごとの穴が 1 つも空かなかった**（＝ CPU も FGS も WakeLock も効いていて、消えているのは電波だけ）。⭐ **この測り方を覚えておくこと**: 両側に 10 秒ごとの記録を置き、**端末側のログに時刻の穴が空くか**を見る。穴が空いていれば CPU ごと寝ている＝アプリ側の話、空いていなければ無線の経路の話。二択がこれだけで割れる。⚠ **推測で対処を打つ前に必ずこれを回す。**

`WIFI_MODE_FULL_HIGH_PERF` は **非機能化していて `WIFI_MODE_FULL_LOW_LATENCY` に読み替えられる**。そしてその `WIFI_MODE_FULL_LOW_LATENCY` は「AP に接続中」「**画面が点いている**」「**アプリが前面**」が揃ったときだけ有効なので、**常駐サービスの用途（画面消灯・バックグラウンド）では常に無効**。したがって:

- **`WifiLock` を到達性の担保として設計判断の根拠にしない。** 0.8.268 で `TerminalService` 側を外したのは「効いていないものを二重に握る意味がない」からで、外したことで到達性が下がったわけではない（元から保証されていなかった）
- ⛔ **`WIFI_MODE_FULL_LOW_LATENCY` へ書き換えても直らない。** 上の条件で常に無効なうえ、画面 ON + 前面のときだけ電力を食う方へ倒れる。minSdk 29 の古い端末では `HIGH_PERF` がまだ効く可能性があるので握るのはやめないが、**数に入れない**
- ⭐ **根治は「端末側から定期的に喋る」**。常駐トンネルの keepalive（`TunnelManager.KEEPALIVE_MS` = 10 秒、0.8.367）がその役をする。実測で**届かない率 37% → 1%**。詳しくは「常駐トンネル」の節
- **トンネルを使わない運用は端末側で補う。** `~/.z2term` の `z2-when wifi:connect` に「再接続の数秒後にゲートウェイと相手機へ `ping` を 1 回投げる」ルールを置く形（端末が自分から名乗ると ARP が復活する）。⚠ ただしこれは**再接続の直後 1 回だけ**なので、黙り続けて消えていく方は防げない
- 症状が出ている最中の切り分けは `~/.z2term/macros/ssh-diag.sh`

⚠ **常駐サーバー / 検知系を ON にしているとプロセスが死なない**ため、このサービスも通知（「Z2Term 稼働中」）ごと残り続ける。OFF のときは最近履歴からのスワイプでプロセスごと消えるので通知も消える — 「常駐サーバーを使い始めたら常駐通知が増えた」ように見えるのはこのため。

#### 常駐サーバー（`ServerDaemonService` ほか、0.8.147）

構成要素: `ServerDaemonService` / `ServerDaemonManager` / `ServerSupervisorScript` / `BootReceiver` / `ServerEntry`

任意のサーバー（sshd/http/smb 等）を**起動コマンド**として登録し（`ServerEntry`、DataStore に JSON 保存）、対話セッションとは独立して常駐させる汎用機構。サーバー本体はユーザーが distro に導入する前提で、アプリはコマンド実行と再起動・常駐管理だけを行う（特定サーバーは非ハードコード）。

**supervisor 方式を採る理由**
- proot/z2root では全プロセスが 1 本のエンジンプロセスの子になる
- そこで **supervisor スクリプト 1 本**をエンジン上で headless 起動し（`ProotLauncher.launch(command=/usr/local/bin/z2term-server-supervisor)`）、生かし続ける
- supervisor は各サーバーを **auto-restart ループ**付きで起動し、稼働状態を rootfs 内 `var/lib/z2term-servers/<id>.status` に書き出す（アプリが読んで一覧に反映）

**ジョブファイル方式（0.8.198・無停止リロード）**

スクリプトは**サーバー定義を焼き込まない固定文字列**で、サーバーは `var/lib/z2term-servers/` 配下の
ファイルで渡す。supervisor は監視ループ（`POLL` 秒周期。下記）で `*.job` を拾い、まだ動かしていないものがあれば
run ループを起こす。

| ファイル | 書く側 | 意味 |
|---|---|---|
| `<id>.job` | アプリ | 実行するコマンド本文。**これが在ることがサーバーの定義**。消すと止まって片付く |
| `<id>.want` | アプリ | `1`=起動 / それ以外=停止（個別 ON/OFF） |
| `<id>.status` | supervisor | `state=` / `pid=` / `restarts=` / `last_exit=` / `cmd=` |
| `<id>.log` | supervisor | そのサーバーの標準出力・標準エラー |
| `<id>.exits` | supervisor | 終了の履歴（`<epoch> <rc>` を直近 20 行） |
| `<id>.jobstamp` | supervisor | `.job` を最後に読んだ印（0.8.377）。中身は空で、**mtime だけ**に意味がある |

- **追加・編集・削除のどれも supervisor 全体を止めずに反映される**（`ServerDaemonManager.syncEntries`）。
  追加は `POLL` 秒以内に拾われ、`<id>.job` の中身が変わればそのサーバーだけ再起動し、`<id>.job` を消せば
  その run ループだけが片付いて抜ける。**従来は「登録時点の全エントリの run ループを焼き込んだ 1 本の sh」**
  だったため、起動後に追加したエントリには対応するループが無く、反映に全体再起動＝他サーバーの
  巻き添え停止が必要だった（この欠陥の解消が A3 の主目的）。
- `.job` は**中身が同じなら書き直さない**。書き直すと supervisor が「コマンドが変わった」と見なし、
  触っていないサーバーまで再起動してしまう。
- 起動時は `.status` / `.want` / `.claimed` / `.job` を掃除してから書き直す。とくに `.claimed`（run ループを
  起こした印）が残っていると、その id の run ループが二度と起こされず**黙って起動しない**状態になる。
  `.log` と `.exits` は残す（落ちた理由を後から見るためのもの）。

**見張りの間隔（`POLL`＝5 秒、0.8.268）**

supervisor は**エンジン（proot/z2root）の中**で動く。エンジン下では外部コマンドを 1 回起こすだけで ptrace 越しに数千 syscall になるため、**見張りの間隔がそのまま発熱と電池に効く**。

0.8.267 までは 1 秒周期で `cat` を 2 回起こしており（`.want` と `.job`）、`sleep` 自身も外部コマンドなので**サーバー 1 本あたり毎秒 3 プロセス**、しかも**停止中のサーバーも同じ頻度**で回っていた。実機で常駐しているだけでエンジンが CPU を 5〜7% 使い続け、端末が常時温かい状態になっていた（実機で計測。`/proc/<pid>/stat` の utime+stime 差分）。

対策は 3 つ:
- 見張りの間隔を `ServerSupervisorScript.POLL_SECONDS`（5 秒）へ広げ、**スクリプト内の `sleep` は必ず `$POLL` 経由**にする（再起動前の `sleep 3` を除く）
- `<id>.want` は 1 行しかないので**シェル組み込みの `read`** で読む（プロセスを起こさない）。`.job` は複数行になりうるので `cat` のまま
- **停止中のサーバーは `.status` を毎周期書き直さない**（中身が変わったときだけ書く）

代償として、個別 ON/OFF・追加・編集・削除の反映と、落ちたサーバーの再起動が最大 5 秒遅れる。常駐サーバーは「動き続けること」が仕事で秒単位の応答は要らないので、電池を採る。`ServerSupervisorScriptTest` が「`sleep` のハードコードが無い」「`.want` を `cat` で読んでいない」「停止中の書き込みにガードがある」を回帰テストで固定している。

**1 周期あたりのプロセス生成をゼロにする（`RECHECK_CYCLES`、0.8.377）**

0.8.268 で間隔を 5 秒へ広げてもなお、**見張りだけで 1 コアの約 1% を 24 時間焼き続けていた**（実機で 120 秒間の `utime+stime` を測定: supervisor の z2root が 103 ticks、配下のシェル 3 本が各 8 ticks）。サーバー 1 本につき 5 秒ごとに `sleep` 2 回（監視ループと run ループ）＋ `cat` 1 回＝**1 日およそ 5 万回**の exec を起こしていたため。エンジン下の exec は ptrace でひとつ残らず止められるうえ、この端末では 1 回ごとに SELinux の監査行まで出る（13 分間の logcat 737 行のうち **682 行**がこれだった）。しかも `ServerDaemonService` が WakeLock を握っているので、その間ずっと端末は深い休止へ入れない。

そこで**定常状態では外部コマンドを 1 つも起こさない**ようにした:
- **`sleep` をシェル組み込みへ差し替える**（bash の loadable を `enable -f /usr/lib/bash/sleep sleep`）。⚠ busybox ash 等には `enable` が無いので、**失敗は黙って素通りさせ、従来どおり外部 `sleep` で動かす** — ここでエラーを表に出すと supervisor ごと起動せず、常駐サーバーが全滅する。
- **`<id>.job` を毎周期読まない**。`test -nt`（シェル組み込み）で `.job` と `<id>.jobstamp` の mtime を比べ、**動いたときだけ `cat` で読み直す**（`.job` は複数行になりうるので、読み方自体は `cat` のまま）。⚠ **印を先に新しくしてから読む** — 読んでから印を付けると、その間に書き換えられた分を二度と拾えない。
- ⚠ **取りこぼしの保険**: mtime が完全に並ぶと「変わっていない」と誤るので、`ServerSupervisorScript.RECHECK_CYCLES`（12 周期＝60 秒）に 1 回は無条件で読み直す。誤っても最長 `POLL × RECHECK_CYCLES` 秒で必ず追いつく。

実測（同じ端末で新旧を同時に 60 秒走らせた差分）: **44 ticks → 6 ticks ＝ 1 コアの 0.73% → 0.10%**。編集の無停止反映・個別 ON/OFF・削除の片付けが同じように動くことも同じ手順で確認した。`ServerSupervisorScriptTest` が「`.job` を読む `cat` は 1 か所だけ」「`enable` の失敗を握りつぶす」「無条件の読み直しがある」を固定する。

**個別 ON/OFF（0.8.163）**
- 各 run ループは `<id>.want` フラグ（`1`=起動）を監視する
- アプリが `ServerDaemonManager.setWant` で書き換えると、supervisor を再起動せず（＝他サーバーを止めず）にその 1 本だけを起動/停止する（`POLL` 秒以内に反映）
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
- 前面維持（プロセス被 kill 防止）と LAN 到達性（WakeLock + WifiLock）は専用フォアグラウンドサービス `ServerDaemonService` が担う。⚠ **`WifiLock` は到達性を保証しない**（Wi-Fi 再接続直後に ARP で落ちる実測あり。上の「フォアグラウンド常駐とロック」を参照）
- `BootReceiver`（`RECEIVE_BOOT_COMPLETED`）で端末起動直後にアプリを開かず自動常駐（設定「起動時に自動で常駐」ON かつ enabled サーバーがある時のみ）
- 停止は通知「サーバー停止」または設定から、**supervisor エンジンを kill = 全サーバー一括停止**（子プロセスがまとめて終了）
- 1024 未満ポートは非 root エンジンで bind 不可

**省電力モード（`serversLowPower`、0.8.148 / 0.8.269 / 0.8.309）**: ON のとき WakeLock/WifiLock を握らず Doze を許す（電池優先。画面消灯中の着信は遅延・取りこぼしうる。次回起動から反映）。**0.8.269 から `TerminalService`（🔒 常駐）にも同じフラグが効く** — 常駐サーバー稼働中は 2 つのサービスが同じ WakeLock を 1 本ずつ持つため、片方だけ従っても設定が効き切らなかった。

**置き場を ⚙設定 → 自動化 → プロセス保護へ移した（0.8.309）**: 0.8.308 までトグルは 📜 → サーバータブ（`ServersSheet`）にあり、文言も「外部からの着信が遅延・取りこぼす」だけだった。⚠ **これはサーバーだけの設定ではない** — 🔒 常駐にも効き、さらに**自動化の反応の速さも変わる**。⚠ **自動化専用の WakeLock はどこにも無い**（`HeadlessRun.launch` はロックを取らない）ので、`z2-when` が動く速さは**常駐側が握っているロックへの相乗り**で決まる。サーバーを使っていない人からは自分に関係のある設定に見えなかったので、電池最適化の除外・phantom process 対策と同じ「端末に眠らせるか / 起こしておくか」の並びへ移す。⛔ **両方に出さない**（同じトグルが 2 か所にあると、どちらが効いているのか分からなくなる）。⛔ **DataStore キー `servers_low_power` は変えない**（変えると既存ユーザーの設定が既定値へ戻る）。⚠ **逆に、常駐サーバーも 🔒 も動いていないときはこの設定を変えても何も起きない**（誰もロックを握っていないため）。「省電力を OFF にしたのに速くならない」の説明はここで、時刻トリガーの `setAndAllowWhileIdle` による Doze 中のズレも別の要因として残る。

**常駐通知の見せ方（0.8.160）**
- `IMPORTANCE_MIN` チャンネル（`z2term_servers_v2`）で出し、**ステータスバーにアイコンを出さず通知シェード最下部へ畳む**（フォアグラウンドサービスは通知必須で完全非表示は不可のため、サーバー常駐のみのときの目立たなさを優先）
- 稼働数は supervisor の `.status` 書き込みラグがあるため、起動直後の 1 回きりでなく**周期的に通知を更新**する（`server-notif-refresh`）。0 のまま固まる不具合と、再起動/クラッシュ追従を両立
- **周期と更新条件（0.8.268）**: 起動から 1 分は 3 秒周期、その後は 30 秒周期。さらに**稼働数が変わったときだけ** `notify()` とウィジェット再描画を行い、`.status` の読み取りも 1 周期 1 回に減らした。0.8.267 までは 3 秒周期で毎回 `.status` を 2 回読んで通知を出し直しており（1 日約 29,000 回）、WakeLock を握っているため端末が Doze へ入れず、その都度 CPU が起きていた。中身の変わらない通知を出し直しても見た目は変わらないので出さない

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

**本文の抽出（`extractBody`、0.8.185）**: `title` は `EXTRA_TITLE`。`text` は標準の `EXTRA_BIG_TEXT` → `EXTRA_TEXT` だけだと **MessagingStyle の SMS / ワンタイムパスワード**（本文が `EXTRA_MESSAGES` に入り TEXT は空）を取りこぼすため、中身のある最初のフィールドを優先順に走査する（**0.8.358 で会話を先頭へ移した**。下記）: **MessagingStyle**（`EXTRA_MESSAGES`）→ 展開本文（`EXTRA_BIG_TEXT`）→ 本文（`EXTRA_TEXT`）→ **InboxStyle**（`EXTRA_TEXT_LINES`）→ 補助行（`EXTRA_SUB_TEXT` / `EXTRA_INFO_TEXT`）→ `tickerText`。どのフィールドにも文字が無い（完全カスタム表示のみの）通知は原理的に拾えない。抽出結果は既存の `text` に合流するのでプレースホルダやログ形式は無変更。

**会話は最優先＋前回の続きから（`freshMessageText`、0.8.358）**: 会話アプリは**続けて届いた何通かをまとめて 1 回だけ通知し直す**うえ、`EXTRA_TEXT` には**最新の 1 通を表示用に短くしたもの**しか入れない。⇒ `EXTRA_TEXT` を先に見ていた 0.8.357 までは、**途中の何通かが記録から丸ごと抜け落ち、残った 1 通も途中で切れる**という壊れ方をしていた（実機で 4 通中 3 通が消え、残る 1 通も末尾が欠けた。他の自動化アプリでは全文が取れていた＝ `EXTRA_MESSAGES` を読んでいたということ）。⇒ **`EXTRA_MESSAGES` を最優先で見る**。⚠ ただし会話アプリは通知のたびに**直近の数通をまるごと**載せてくるので、そのまま書くと同じ発言が何度も並ぶ。⇒ `key` ごとに**最後に書き出した発言の印**（`messageSig` = 時刻 + 本文。**時刻だけでは足りない** — 同時刻に複数届くことも、時刻を持たない送り手もいる）を覚え、**その次から**だけを書く。⚠ 印が会話の中に見つからないとき（初回 / LRU からあふれた / 送り手が印を変えた）は**載っているものを全部書く** — 取りこぼすより重複するほうがまし（重複の一部は `isDuplicate` でも落ちる）。⚠ **新着ゼロは `null`** を返して**その通知ごと捨てる**（空文字にすると題名だけの行が残る）。⚠ `onNotificationRemoved` でこの印は**忘れない** — 通知を払っただけで忘れると、次に同じ会話の通知が来たときに履歴がまるごと再記録される。⚠ **`z2-noti list` は差分にしない**（`key` を渡さない）。「いま出ている通知を読む」コマンドなので、会話は載っているものを全部見せる。⚠ **会話だけは「1 通知 = 1 行」の単位が違う**: その回の新着ぶん全部が改行で連なって 1 行になる。

**双方向制御文字を落とす（`stripBidi`、0.8.356）**: 取り出した `title` / `text` から、Unicode の双方向テキスト制御文字（`U+200E` `U+200F` `U+061C`、埋め込みと上書き `U+202A`〜`U+202E`、分離 `U+2066`〜`U+2069`）を落としてから、トリガー判定にもログにも渡す。**なぜ要るか**: 電話アプリは電話番号を `BidiFormatter` で包んで通知に出すため、表示は `0120-355-565` でも実体は `U+202A` + 番号 + `U+202C` で届く。**画面にもログにも見えない**ので、番号の形かどうかを見るマクロ（同梱の `unknown-call.sh` は `tr -d '0-9+() -'` で「何も残らない」ことを見る）が**名前と誤判定して黙って何もしない**。実機では着信を 1 件も拾えておらず、しかも `z2-when fired` には `run` と残るため「動いているのに何も起きない」という一番読みにくい壊れ方をしていた（2026-08-17 に実機のログから確定）。⚠ **トリガーとログの両方に効かせる**こと（片方だけだと「ログでは番号なのにルールが一致しない」という食い違いが起きる）。⚠ **「生のまま記録する」方針の唯一の例外**だが、落とすのは**表示に出ない**文字だけなので読める中身は一字も変わらない。⚠ `z2-noti list` も同じ処理を通す。**マクロ側で直さない**理由: 端末のマクロは `install` が上書きしないため古いコピーが残り続ける（同じ形で 2 度事故を起こしている）。アプリ側で落とせば、既存のマクロを入れ直さなくても直る。

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

**サンプル**: `z2-macro install otp-sms` で `sms:otp` から起こされる版が入る（0.8.273。それ以前は `sms.jsonl` を 2 秒ごとに見張って自分で 4〜8 桁を抽出する常駐スクリプトだった → 下記「サンプルを常駐から `z2-when` へ寄せる」）。

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

#### Wi-Fi の接続 / 切断が入れ替わっていたのを直す（`SystemEventService.networkCallback`、0.8.248）

**症状**: Wi-Fi を**切ると** `wifi_connected`、**繋ぐと** `wifi_disconnected` が記録される。`z2-when` の `wifi:connect` / `wifi:disconnect` も同じく逆に発火する。実機の `events.jsonl` で確認（Wi-Fi が ON のまま最後の記録が `wifi_disconnected` になる）。

**原因**: きっかけが `WifiManager.NETWORK_STATE_CHANGED_ACTION` で、受け取った**その場で** `ConnectivityManager.activeNetwork` を読んでいた。このブロードキャストは**既定ネットワークが切り替わる前**に飛ぶため、切断直後はまだ Wi-Fi が見えて `connected=true`、接続直後はまだモバイル（または未確定）のままで `connected=false` になる。0.8.168 で直した判定式そのものは正しく、**読むタイミングだけが早すぎた**（`z2-state wifi` が常に正しかったのは、聞かれた時点で読むから）。接続の過程でこのブロードキャストが何度も飛ぶため、`wifi_connected` が連続して並ぶ現象も同じ原因。

**対策**: `ConnectivityManager.registerDefaultNetworkCallback` に寄せる。`onCapabilitiesChanged` は**状態が確定してから**呼ばれるので、この取り違えが原理的に起きない。既定ネットワークを見るのは `z2-state wifi` と判定を揃えるため。`onLost`（既定ネットワークが消えた）は「Wi-Fi ではなくなった」だけを見る — 別の回線へ切り替わる場合は続けて `onCapabilitiesChanged` が来る。

⚠ 登録直後に「今の既定ネットワーク」で `onCapabilitiesChanged` が 1 度呼ばれるので、**登録前に `lastWifiConnected` を現在値で埋めておく**（サービスの起動を接続イベントと誤検知しないため。BT オーディオの `btCallbackPrimed` と同じ考え方）。

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

#### 常駐サーバーを CLI から起こす / 落とす（`z2-server`、0.8.310・F）

**背景（実機で出た実害）**: `z2-when wifi:connect run 'sshd --lan'` のようなルールを書いても、**スリープ中は ssh でつながらない**。調べると、ルールから起こしたデーモンは**常駐サーバーの枠の外**で動いていた。`HeadlessRun.launch` は `waitTracees` で**デーモンを生き残らせる**仕組みは持つ（それが無いと `sshd --lan` は起動直後にエンジンごと消える）が、**ロックもフォアグラウンドサービスも取らない**。

| 付かないもの | スリープ中に何が起きるか | 持っているのは |
|---|---|---|
| WifiLock（`WIFI_MODE_FULL_HIGH_PERF`） | ⚠ **実は何も変わらない**（0.8.367 に裏取り。非機能化していて画面消灯中は常に無効。§「フォアグラウンド常駐とロック」参照） | `ServerDaemonService` **だけ** |
| WakeLock | CPU が眠り、`sshd` の accept が動かない | `ServerDaemonService` / `TerminalService` |
| フォアグラウンドサービス | プロセスが cached 扱いで kill されうる。`sshd` は proot の子なので**道連れ** | 同上 |

⚠ **🔒 バックグラウンド常駐では埋まらない。** WifiLock は 0.8.268 で `TerminalService` から**意図的に外した**（対話セッションを生かすのに無線は要らない、という切り分け）。判断としては正しいが、自動化から起こしたサーバーは**どちらの枠にも入らない隙間**に落ちていた。

⛔ **却下した案: `HeadlessRun` 側にロックを持たせる。** マクロ 1 本ごとに端末が眠らなくなり、0.8.268-269 で電池のためにやった切り分けを巻き戻すことになる。**穴は「枠へ入れる経路が画面にしか無い」こと**（`ServerDaemonManager.setWant` の呼び元は `ServersSheet` だけだった）なので、そこへの入口を CLI に開けるのが筋。

**サブコマンド**

| | 動作 |
|---|---|
| `list` | 1 行 1 サーバーの TSV（`番号 / id / 状態 / 印 / 名前`）。印は `*`=有効 `-`=無効。⚠ 書式は `z2-session list` に揃える |
| `start <サーバー>` | 登録を有効にして枠の中で起こす。稼働中なら `setWant` で 1 本だけ、止まっていれば `ServerDaemonService.start` で枠ごと |
| `stop <サーバー>` | その 1 本だけ止める（他は動いたまま） |
| `status [<サーバー>]` | `name=` `state=` `pid=` `restarts=` `last_exit=` … のフラットな key=value（`z2-state` と同じ理由で jq 無しでも拾える） |

- **指定は 番号 → id → 名前（完全一致）→ 名前（前方一致）**。⚠ `resolveSession` と**同じ順序**にしてある（指定の書き方を 2 つ覚えさせない）。⚠ 名前は一意ではない（画面が重複を止めていない）ので、**複数に当たったら選ばずに断る** — 勝手にどれかを起こすと、止めたつもりの別のサーバーが動き続ける。
- ⚠ **`start` は「登録を有効にする」→「サービスを起こす」の順**でなければならない。supervisor は **enabled が 1 件も無ければ起動しない**ので、逆順だと空振りして即 `stopSelf` される。
- ⚠ **登録済みのものを起こす / 落とすだけ**にする。コマンドをその場で渡して常駐させる形にすると画面の一覧と二重管理になり、「画面に無いのに動いているサーバー」ができる。
- ⚠ **省電力モード ON では起動してもロックを握らない**（`ServerDaemonService` は同じ `serversLowPower` に従う）。挙動としては正しいが黙っていると「起動したのにつながらない」の再来なので、**`start` の出力でその場で警告する**（起動自体は成功しているので失敗にはしない）。
- ⚠ **`stop` で最後の 1 本を落としても枠は畳まない。** 常駐トンネル（`TunnelManager`）だけが残っている状態まで巻き添えにするため。全部止めるのは画面の [停止] の仕事。

これで書けるようになるのがこれ:

```sh
z2-when wifi:connect    run 'z2-server start sshd'
z2-when wifi:disconnect run 'z2-server stop sshd'
```

#### アプリ自身のタブを操る（`z2-session`、0.8.199・A1）

**背景**: `Z2ApiBridge` の動詞はすべて「端末から Android を叩く」片道で、**アプリの内側（タブ）に触れる動詞が 1 つも無かった**。シェルやマクロから「作業用のタブをもう 1 枚開く」「別のタブへコマンドを置く」「今の画面を取り出す」ができない。

**サブコマンド**

| | 動作 |
|---|---|
| `list` | 1 行 1 タブの TSV（`番号 / id / 種別 / 印 / 名前`）。印は `*`=表示中 `!`=動作中 `?`=未起動 `-`=その他 |
| `new [名前]` | 端末タブを 1 枚開き、**起動まで済ませて** `番号\tid` を返す（続けて `send` する材料になる） |
| `send <先> <文字列>… [--enter]` | そのタブに文字を**入れる**。`--enter` を明示したときだけ実行する |
| `key <先> <キー>…` | そのタブに**キー**を送る（`C-c` / `M-x` / `F5` / `Up` …）。`--raw` でバイト列も（0.8.311） |
| `capture [先] [--all]` | そのタブの画面テキストを返す（`--all` で遡れる分も） |
| `attach <先>` | そのタブに**繋ぎっぱなし**にして普通に打つ（0.8.366）。抜けるのは `Ctrl+]`、または行頭の `~.` |
| `close <先>` | そのタブを閉じる（最後の 1 枚は閉じない＝UI のダブルタップ削除と同じ約束） |

**安全側の既定**: `send` は**入れるだけで実行しない**（改行を付けない）。共有の受け取り（B1・[§5.1.2](#512-共有の受け取り-b10.8.197)）と同じ約束で、他のタブが勝手に走り出す状態を作らない。`Z2ApiScriptTest` が「ヘルパー側が `--enter` を足していない」ことを固定している。

**入れ先の指定**（`resolveSession`）は **番号（1 始まり）→ id → タブ名** の順。`list` の 1 列目をそのまま使えるのが実用上いちばん楽なので番号を第一に扱う。タブ名は完全一致を優先し、前方一致は**1 件に絞れるときだけ**採用する（複数に当たる指定で「たまたま先頭のタブ」に文字が入る事故を作らない）。

**実装**: 文字を入れる終端は B1 で切り出した `SessionManager.insertText`（bracketed paste 対応）。A1 側は動詞を足すだけで済んでいる。タブの生成・破棄・バッファ読み出しは `runOnMainSync` で Main に寄せる（描画側と同じ前提に乗せる）。

**キーを送るのは別の動詞にする**（`key`、0.8.311）。⚠ `send` は `pasteText` を通るので
**bracketed paste（`ESC[200~ … ESC[201~`）で囲まれる**。シェルはそれを「^C という文字が
貼られた」と読むので、`send` に `\x03` を渡しても **SIGINT にならない**。`key` は
`writeBytes` へ直に書く。⚠ `send` 側を「制御コードなら囲まない」と作り分けないこと —
貼り付けの意味が入力内容によって変わり、説明できない挙動になる。

- **変換表は `AndroidKeyMapper` に置く**（`keyBytesFor`）。⚠ 内蔵キーボード（`mapKeyEvent`）と
  CLI で送るバイトが違うと、**片方でしか再現しない不具合**ができる。`KeyBytesForTest` が
  「同じ表を引いていること」まで固定する。
- **矢印だけは emulator に組ませる**（DECCKM 依存）。固定のバイト列を返すと application cursor
  keys のアプリで矢印が効かなくなる。
- ⛔ **`C-S-a` のような Shift 付きは送らずに断る**（ユーザーが選択）。端末では Shift が文字に
  畳み込まれ、`C-a` と**同じ 1 バイト**になって区別できない（`controlByteFor` が `a..z` と
  `A..Z` を同じ値に潰しているのがそれ）。⚠ 黙って `C-a` を送ると「送ったはずなのに効かない」の
  原因が追えない。**代わりに何と書けばよいか**まで返す。区別する規格（xterm の modifyOtherKeys /
  Kitty keyboard protocol）は未実装で、入れても受け手側の対応が要る。
  ⚠ ただし **`S-Tab` は通す** — 断る基準は「Shift が付くか」ではなく「**端末が区別できるか**」で、
  backtab は `ESC [ Z` として実在する。
- **`--raw` はエスケープ表記で受ける**（`\xHH` `\e` `\n` `\r` `\t` `\0`）。⚠ 実バイトを引数で
  受け取らないのは、リクエストファイルが「1 行 = 1 引数」なので**生の改行で区切りが壊れる**ため
  （`z2-icon` が絵を base64 に畳んでいるのと同じ事情）。
- ⚠ **1 バイトも送る前に全部変換する**。途中で名前を間違えていたとき、そこまでのキーだけが
  届いた状態にすると何が起きたのか分からなくなる。

**`new` は起動まで済ませる**（0.8.203）。画面側の自動起動は「表示中のタブが IDLE なら起動」という条件なので、**アプリを開いていない間に作ったタブは開くまで起動せず**、続けて `send` しても PTY が無く何も起きなかった（実機で確認）。マクロから「タブを開いてコマンドを流す」を成立させるため、`new` の中で `startTerminal` まで呼ぶ。ただし初回ダウンロードが要る distro は勝手に通信を始めず、画面を開いたときの確認に委ねる。
あわせて `list` の印に **`?`（未起動）** を足した。未起動のタブへ送っても何も起きないので、印が無いと「送ったのに動かない」理由が分からない。

**繋ぎっぱなしにする**（`attach`、0.8.366）。⚠ **`send` を便利にする方向ではない** — 1 コマンドごとに `send --enter` して `capture` する使い方は、いつ終わったか分からず前の画面も混ざる。`attach` はタブを ssh のように掴んで離さない口で、**スマホの画面に出ているタブそのもの**を外から打てる（`sshd` で入るのは新しいシェルで、タブとは無関係）。同じ PTY を両側から見るので、打った文字はスマホの画面にも出る。

- **通り道は AF_UNIX ソケット 1 本**（ホスト側 `filesDir/shared_home/.z2term/attach.sock`、ゲストからは `/root/.z2term/attach.sock`）。`z2api` は「ファイルを置いて 0.1 秒ごとに返事を見る」作りなので**即時性の要る用途には使えない**。z2root が `bind`/`connect` のパスを翻訳するので、ゲストのパスのまま繋がる（実績は 0.8.327 の pacman/gpg-agent）。
- ⚠⚠ **受付はタブごとに切らず 1 本にする。** `sun_path` は 107 バイトまでで、タブごとに id（UUID 36 文字）を挟むと **109 バイトになって溢れる**（`/data/user/0/com.zerotoship.z2term/files/shared_home/.z2term/attach/<uuid>.sock`）。受付 1 本なら 72 バイト。**開発機では気付けず実機でだけ繋がらない**類なので、ここは崩さない。繋ぎ先は最初のフレームで受け取り、解決は `resolveSession`（番号 → id → タブ名）を**そのまま使い回す**。
- ⚠⚠ **bind した `LocalSocket` を手放さない（0.8.368）。** `LocalServerSocket` は渡された fd を**借りるだけ**なので、bind した側の参照を捨てると GC がその finalize で同じ fd を閉じる。**ソケットのファイルは残ったまま listen だけが消える**ため、繋ぎに行くと「無い」（`ENOENT`）ではなく **`ECONNREFUSED`** が返り、`z2attach` の口では「アプリに届かない。z2term は動いていますか」と出る。**アプリは現に動いていて、ソケットのファイルも見えている**という一番紛らわしい形になるうえ、**張った直後は繋がり、GC が回った頃に黙る**ので疑いが受付側に向きにくい（0.8.367 の実機で踏んだ）。受付と bind した側の**両方を持ち続け**、`stop()` では両方閉じる。
- **落ちた受付は握らずに手放す（0.8.368）。** `start()` は `server != null` で早々に戻るので、accept が終わったのに掴んだままだと**二度と受け付けないアプリ**になる。accept のループを抜けたらその場で手放し、`Application.onCreate` に加えて `MainActivity.onResume` からも `start()` を呼ぶ（＝**アプリを開き直せば戻る**入口を用意する）。
- **フレームは `[種類 1 byte][長さ 2 byte BE][中身]`**。生バイトの素通しだと**広さの変更を伝える隙間が無い**ため、全部を同じ封筒に入れる。種類は データ / 広さ / お知らせ / 繋ぎ先 の 4 つ。⚠ **1 フレームを分割して書かない** — PTY 読み取りスレッドと返事とで同時に書きうるので、混ざると封筒の境目が壊れる。
- **出口と入口は既存のものを使う**。出力は `TerminalSession` の読み取りループで**端末ログ（C1）と同じ場所・同じ塊**を複製し（別の所で作り直すと「画面には出たのに繋いだ先には来ない」ズレができる）、入力は `writeBytes`（`key` と同じ出口＝bracketed paste を通らないので Ctrl+C がそのまま届く）。⚠ ログと違い **alt screen でも必ず流す**（相手は画面を再現しているので、間引くと画面を使うソフトに入った瞬間に固まる）。
- **広さは繋いだ側に合わせる**（ユーザー判断）。⚠ **繋いでいる間スマホ側のタブは折り返しが合わず崩れて見える**。承知のうえの選択で、最後の 1 人が抜ければ戻る。⚠ **戻すには「画面側が要求していた広さ」を覚えておく必要がある** — 画面側の resize は `LaunchedEffect(session.id, rows, cols)` が駆動していて**行×列が変わらない限り二度と走らない**ので、抜けた瞬間に誰も教え直してくれない。
- **繋いだ瞬間に今の画面を色ごと組み直して送る**。⚠ `getAllText()` は平文なので**色と装飾が全部落ちる**。`TerminalBuffer.getScreenRow` と `SgrAttribute`（セルごと 32bit）から組む。⚠ **SGR は必ず `0` から組み直す** — 差分だけ出すと消し忘れた装飾が以降の行へ尾を引く。遡れる分は送らない（`capture --all` の仕事）。
- **抜けるのは `Ctrl+]`、または行頭の `~.`**（後者は ssh と同じ）。判定は**繋いだ側**でやり、アプリに届く前に食う。行頭の `~` そのものは `~~`、`Ctrl+]` そのものは**行頭の `~` に続けて `Ctrl+]`**。
  - ⚠⚠ **`~.` だけでは SSH 越しに抜けられない（0.8.370・実機で指摘）。** ssh クライアントのエスケープも「行頭の `~`」なので、SSH でログインした先で attach して `~.` を打つと**手前の ssh が先に食って SSH ごと切れる**（内側へは 1 バイトも届かない）。ssh 多段と同じ `~~.` で抜けられはするが、それは**覚え方の押し付け**であって直したことにならない。⇒ **ssh と衝突しないキーを併設する**。`Ctrl+]`（0x1D）は ssh のエスケープ処理を素通りするので、何段越しでも必ず繋いだ側へ届く。
  - ⛔ **「Ctrl キーを奪わない」という 0.8.366 の方針は、ここで曲げた（0.8.370）。** 画面を使うソフトから `Ctrl+]` を 1 つ取り上げる代わりに、**SSH 越しに抜けられないほう**を潰す。取り上げた 1 つは**行頭の `~` に続けて打てば送れる**ので、完全には塞いでいない。
  - ⚠ **押した瞬間に抜ける**（次の 1 文字を待たない）。`Ctrl+] Ctrl+]` でリテラルにする作りも採れるが、**押したのに抜けないように見える**ほうが害が大きい。
  - ⚠ **案内の 1 行は環境で書き分ける**（`SSH_TTY` / `SSH_CONNECTION` / `SSH_CLIENT` のどれかがあれば SSH 越し）。SSH 越しで `~.` を案内すると「書いてあるとおり打ったら SSH ごと切れた」になる。
- **繋ぐ側は小さなネイティブ**（`app/src/main/cpp/z2attach/z2attach.c`）。`/bin/sh` では端末を raw にできず、標準入力とソケットを同時に待てず、`SIGWINCH` も受け取れない。⚠ **jniLibs の `lib*.so` 名でしか APK 導入時に展開されない**ので `libz2attach.so` として運び、rootfs へは `z2attach` として配る（`libz2accept.so` と同じ流儀）。⚠ 異常終了でも**必ず端末を元へ戻す**（戻し忘れると「抜けたあと自分の端末が壊れたまま」という一番たちの悪い壊れ方になる）。
- ⛔ **断るときは理由を言う**（`key` と同じ約束）。GUI タブ / まだ起動していない / もう終わっている / そんなタブは無い、をそれぞれ言い分ける。⚠ **未起動のタブをこちらから勝手に起こさない** — 繋いだつもりが OS の初回ダウンロードを始める、を作らない。
- ⛔ **輪になる繋ぎ方を断る（0.8.419）。** 自分が打っているタブへ繋ぐと、**そのタブの出力がそのタブの出力として書き戻され続けて止まらない**（`z2attach` の標準出力＝そのタブの PTY なので、送った先から必ず戻ってくる）。人が止める手立てが無いので**繋がせない**のが唯一の答え。
  - **呼んだ側がどのタブかを知る手段は環境変数しかない。** 起動時に `Z2_SESSION_ID=<タブ id>` を入れ（`ProotLauncher.launch` / `launchChroot`。chroot は `env -i` で組むので明示的に足す）、`z2attach` が最初のフレームで**繋ぎ先＋改行＋自分の id** として渡す。id が無ければ改行ごと省くので、**どのタブにも属さない呼び出し**（SSH ログイン・自動化）はこれまでどおり通る。
  - ⚠ **SSH ログインへ漏らさない。** タブから手で `sshd` を起こすと dropbear の子がこの値を受け継ぎ、**別の端末から繋いだ人がそのタブへ attach できなくなる**（自分自身と誤判定される）。sshd ラッパーの先頭で `unset Z2_SESSION_ID` する。常駐サーバー経由の sshd は `HeadlessRun` 起動なので元から空。
  - **遠回りの輪も断る。** A から B へ繋いだ状態でその中から A へ繋ぐと、A の出力が B へ、B の出力が A へ流れて同じ暴走になる。`AttachServer` が「今どのタブがどのタブへ繋いでいるか」だけを持ち（`links`）、繋ぎ先から辿って自分へ戻るなら断る。⚠ **同じ組を 2 本繋げる**ことがあるので集合ではなく並びで持つ（集合だと 1 本外しただけで見張りが緩む）。⚠ 抜けたときに必ず外す — 残すと**もう繋がっていない相手のせいで断られる**ようになり、アプリを立ち上げ直すまで直らない。
- **繋いでいる間は常駐枠に入れる**（`AttachHold`）。PC から繋いだまま作業していて落とされるのが一番困るため、🔒 と同じ `TerminalService` を使う。⚠ **設定値 `keepAliveService` は書き換えない**（触ると抜けたあともユーザーの設定が変わったままになる）。⚠ **下ろしてよいのは自分が起こしたぶんだけ** — 🔒 が ON か常駐サーバーが動いているなら、そちらの都合で常駐しているので触らない。
- `list` の印に **`@`（外から繋がっている）** を足した。印が無いと、PC から掴まれているタブがスマホ側から見分けられない。⚠ **印は重なる**（表示中かつ繋がっていれば `*@`）。

**`new <名前>` の名前は固定する**（0.8.202）。`TerminalSession` に `labelPinned` を持たせ、true の間は**起動時の OS 名（`spec.id`）・`android-sh` フォールバック・SSH 接続・シェルが出すタイトル（OSC 0/2）のどれでも上書きしない**。これが無いと `z2-session new build` で付けた名前が直後の起動で OS 名に化け、名前を指定した意味が無くなる（実機で確認した）。

#### Android USB Host の fd を Linux へ渡す（`z2-usb`、0.8.425）

Android のアプリ UID は `/dev/bus/usb/...` を直接 `open` できないが、利用者が機器ごとのシステム許可を押せば `UsbManager.openDevice()` は通常の usbfs fd を返す。そこで `z2-usb list` / `allow [番号|パス|VID:PID]` を許可の入口にし、`UsbFdBroker` が Android 側で開いた fd を **abstract AF_UNIX socket + `SCM_RIGHTS`** で同じ UID の Linux プロセスへ渡す。`usbip` やカーネルの `vhci-hcd` は使わない。

- ソケット名は `z2term-usb-v1-<uid>`。接続後も `peerCredentials.uid == Process.myUid()` を検証し、他 UID へ許可済み fd を渡さない。要求は 1 接続 1 行の `OPEN /dev/bus/usb/BBB/DDD` に限定し、正規表現外のパスを断る。
- z2root 起動時に libc 非依存の `libz2usb.so` を `LD_PRELOAD` する。`open` / `open64` / `openat` 系で絶対 usbfs パスだけをブローカーへ回し、それ以外は生の `openat` syscall へ流す。これにより通常の libusb 利用側を変更しない。
- Android の許可は機器を抜くまで。挿し直したら再度 `z2-usb allow` が必要。USB Host/OTG とデータ線があれば通常の USB-A → USB-C 変換やハブでよく、形状だけ Type-C で充電専用の変換は使えない。
- ⛔ `LD_PRELOAD` の境界なので、静的リンクされた実行ファイルと libc の関数を通さず syscall を直接発行する実装は対象外。これはネットワークの「ポート転送」ではなく、接続された USB 機器の fd を透過的に橋渡しする機能。
- ⛔⛔ **このシムから「実行先の libc に無いシンボル」を 1 つでも引かないこと（0.8.508 で修正）。** `LD_PRELOAD` は端末の `sh` から `apk` まで**その rootfs で起動する全プロセス**に載るので、解決できないシンボルが 1 つあるだけで**あらゆるコマンドが exec 前に死ぬ**。0.8.425〜0.8.507 の `libz2usb.so` は bionic の `CMSG_NXTHDR()` を使っていたが、あれはマクロではなく **libc の関数 `__cmsg_nxthdr()` へ展開される**。musl にこの関数は無いので、**Alpine では端末も GUI も何ひとつ起動しなかった**（`Error relocating /usr/local/lib/libz2usb.so: __cmsg_nxthdr: symbol not found`）。glibc 系（Ubuntu / Arch / Kali）は素通りするため、**その 2 つで試している限り気付けない**。次の cmsg は `z2_cmsg_next()` で自前に辿る。⭐ シムに手を入れたら `nm -D --undefined-only` を見て、`weak` か **どの libc にもある標準シンボル**（`memcpy` 等）だけであることを確かめること。
- 実機スパイクでは Android が開いた fd を fork 後の別プロセスへ `SCM_RIGHTS` で渡し、`USBDEVFS_CONNECTINFO` が成功することを確認。本実装でも `libz2usb.so` から `/dev/bus/usb/001/002` を通常の `open` として開き、`UsbFdBroker` の fd 送信まで確認した。

#### 自動化ハブ（`z2-when` / `WhenManager` / `WhenReceiver`、0.8.205・A6 stage 1）

**何ができるか**: Android 側の出来事（充電・電池・時刻）を**きっかけに Linux スクリプトを自動実行**する。これまで `z2-*` は「検知（events.jsonl へ書く）」と「実行（`z2-session` 等）」が別々で、両者を繋ぐのはユーザーが書く常駐スクリプトだった。`z2-when` は**トリガー宣言 → アプリが監視 → 発火時に実行**までを担い、スマホを「ポケットの中の自動化サーバー」にする。0→1 ではなく既存資産の“配線”。

**ルールはテキスト**: `~/.z2term/when/<id>.rule`（`filesDir/shared_home/.z2term/when/`）。`trigger=` / `run=` / `enabled=` の 3 行（`settings/WhenRule.kt`。任意で `order=`、0.8.263 の絞り込み `if=` / `cooldown=` / `between=` / `days=`、0.8.303 の `name=` → いずれも後述）。DataStore でなくプレーンファイルにするのは **git 同期・バックアップが効く**ため（常駐サーバーのジョブファイルと同じ思想）。CLI（`z2-when`）が直接読み書きし、変更後に `z2api when-reload` で時刻トリガーを貼り直させる。

**トリガー書式（stage 1 + cron）**:
- `charge:start` / `charge:stop` … 充電の開始 / 停止。**検知（`SystemEventService`）が ON のときだけ働く**（0.8.214 で受け口を変更。理由は下記「常駐を増やさない設計」）
- `battery:below=N` / `battery:above=N` … 残量が N% を下/上へ**跨いだ瞬間**（エッジ判定。直近残量を `.battlevel` に保存し、初回は基準設定のみ）。**検知が ON のときだけ働く**（0.8.214）
- `time:daily=HH:MM`（毎日）/ `time:at=HH:MM`（次の HH:MM に 1 回。発火後は `enabled=0` に自動で書き戻す）/ `time:every=Nm|Nh|Ns`（N ごと・最短 1 分）
- `time:cron='分 時 日 月 曜日'`（0.8.207・stage 2）… 5 フィールドの cron 式。`*` / `*/n` / `a` / `a-b` / `a-b/n` / `a,b,c` に対応。曜日は 0-7（0,7 が日曜）。**日と曜日がどちらも `*` でない場合はどちらか一致で発火**（標準 cron の仕様）。次回発火の算出は Android 非依存の `CronSchedule.nextAfter`（`CronScheduleTest` で具体例検証）。`daily`/`every` と同じ AlarmManager 経路に載り、発火のたびに次回を貼り直す。空白を含むのでシェルではクォート必須。
- `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<名前>`（0.8.208・stage 2）… Wi‑Fi の接続 / 切断 / 指定 SSID への接続。判定は Android 非依存の `WhenTriggerMatch.wifi`（`WhenTriggerMatchTest` で具体例検証。SSID は大小文字無視、位置情報権限が無く SSID が空なら `ssid=` は取りこぼす）。**電池の 10% 刻みと同じく検知（`SystemEventService`）が ON のときだけ働く**（受け口は同サービスが登録する `NetworkCallback`＝生きたプロセスが要る。0.8.248 まではブロードキャストだったが、接続 / 切断が入れ替わるため差し替えた → 前掲）。発火時は SSID を `Z2_WHEN_SSID` で渡す（外部文字列なので単一引用符へ安全にエスケープ）。
- `net:online` / `net:offline` / `net:wifi` / `net:mobile` / `net:ethernet`（0.8.264）… **回線が通じた / 途切れた**、または**使う回線が切り替わった**とき。`wifi:*` との違いは**モバイル回線も見る**こと — `wifi:disconnect` は「Wi‑Fi が切れた」までしか言えず、そのあとモバイルで通信できているのか本当に圏外なのかを区別できないので、「通信できるようになったら送る」「圏外になったら止める」は今まで書けなかった。判定は Android 非依存の `WhenTriggerMatch.net`（`WhenTriggerMatchTest` で具体例検証）。受け口は `wifi:*` と同じ `NetworkCallback`＝**検知が ON のときだけ働く**。⚠ **前の状態と比べて判定する**のがこのトリガーの肝で、Wi‑Fi からモバイルへ替わっても「通信できる」ことは変わらないため `net:online` は発火させない（「今の状態を満たすか」で書くと移動のたびに走る）。⚠ **繋がったことではなく通ったこと**を見る（`NET_CAPABILITY_VALIDATED`）— 認証画面の先へ出られない Wi‑Fi を「オンライン」と呼ぶと `net:online` が「送れるようになった」の合図として使えない。その代わり検証が終わるまでの数秒は `none` のままなので、**Wi‑Fi のアイコンが立つより少し遅れる**。VPN は既定回線として見えるものを素直に `vpn` と答える（下の実回線に読み替えない）。発火時は `Z2_WHEN_NET`（今の回線）と `Z2_WHEN_NET_PREV`（直前の回線）を渡す。`events.jsonl` にも `net_online` / `net_offline` / `net_<種別>` として残る。
- `share:any` / `share:text` / `share:file` / `share:contains=<部分>` / `share:ext=<拡張子>`（0.8.266）… **他アプリの共有シートから z2term へ送られたとき**。受け口は 0.8.197 の `SharedIntake`（テキストはそのまま、ファイルは `~/z2term-inbox/` へ取り込んでからパスにする）で、**そこに分岐を 1 本足しただけ**。共有はアプリの起動経路なので**検知には依存しない**。判定は Android 非依存の `WhenTriggerMatch.share`（`WhenTriggerMatchTest` で具体例検証）。⚠ **端末への挿入は今までどおり行う**（ルールは足し算）。共有は「入れるだけ・実行しない」と約束してある入口なので、ルールを 1 本書いたら挿入が黙って止まる、では既にある使い方を壊す。入力行に残るのは実行されていないただの文字列なので害が無い。⚠ **`contains=` はファイル共有には当たらない** — ファイルのときの本文は取り込み先のパスなので、当たると「ファイル名にたまたま含まれていた」で発火し、書いた人の意図（共有された文章の中身で絞る）とズレる。ファイル側は `ext=` で絞る。⚠ **共有すると z2term が前面に出る** — 共有シートの宛先は Activity なので、これは Android の作りであって選択ではない（「裏で静かに走る」にはできない）。発火時は `Z2_WHEN_SHARE`（端末に入るのと同じ文字列）と `Z2_WHEN_SHARE_KIND`（`text` / `file`）を渡す。
- `boot`（0.8.264）… **端末の起動が終わったとき**。`:` を持たない唯一のトリガー（引数を取らないものに空の引数を書かせないため、`WhenRule.kind` は `:` が無ければ全体を種別として読む）。受け口は既存の `BootReceiver` で、`BOOT_COMPLETED` は**暗黙ブロードキャスト制限の例外**なので manifest 宣言のまま確実に届く＝**時刻・SMS と並んで検知 OFF でも動く**数少ないトリガー。⚠ `LOCKED_BOOT_COMPLETED`（ロック解除前）では**動かさない** — 資格情報で暗号化された領域がまだ開いておらず、ルールファイルもエンジンも読めないので黙って失敗するだけになる。⚠ 実行は `goAsync()` で包む。ブロードキャストは `onReceive` を抜けた瞬間にプロセスごと止められうるため、常駐サービスを持たない一度きりの実行にとってはこれが唯一の生命線。
- `sms:any` / `sms:from=<部分>` / `sms:contains=<部分>` / `sms:otp`（0.8.209・stage 2）… 着信 SMS。判定と OTP 抽出は Android 非依存の `WhenTriggerMatch.sms` / `.extractOtp`（`WhenTriggerMatchTest` で具体例検証。`from`/`contains` は部分一致・大小文字無視。OTP は**前後が数字でない 4〜8 桁**の先頭で、9 桁以上の電話番号/注文番号は拾わない）。既存の `SmsLogReceiver`（`RECEIVE_SMS` 許可で OS が着信ごとに起動＝アプリ未起動でも動く）に相乗りし、**生ログ設定 `smsCaptureEnabled` とは独立に評価する**（許可さえあれば動く）。SMS 本文は Android 15 の機微通知伏せ字（`RECEIVE_SENSITIVE_NOTIFICATIONS`）を通らない直読み経路なので伏せ字化されない（既存 `SmsLogReceiver` の解説参照）。発火時は `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`、`otp` のときは `Z2_WHEN_OTP` を渡す（いずれも外部入力なので単一引用符へ安全にエスケープ・`eval` させない安全境界）。
- `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far`（0.8.210・stage 2）… 端末を振った / 照度が N lux を跨いだ / 近接が near・far へ変化。**継続センサー監視は電池を食う**ので §10-1 の指針どおり **opt-in・検知（`SystemEventService`）が ON のときだけ**働き、しかも**該当ルールがあるセンサーだけ登録する**（`WhenManager.sensorKindsNeeded` → `SystemEventService.refreshSensors`。ルール増減や検知 ON で貼り直し、要求集合が空なら 1 つも登録しない＝電池ゼロ）。加速度は shake 検出に十分な `SENSOR_DELAY_UI`、照度/近接は on-change の `NORMAL`。shake 判定は `ShakeDetector`（合成加速度が **4.0g 超＋3 秒 debounce**・`ShakeDetectorTest`。当初の 2.7g／1 秒では**ポケットに入れて歩いているだけで連続発火**した＝2026-07-24 の実機検証で 3.5 時間に 255 回・発火間隔が debounce に張り付く形で判明したため 0.8.214 で引き上げ。下げるときは歩行で誤発火しないか実機確認が要る）、照度/近接は `WhenTriggerMatch.lightSatisfied`/`.proximitySatisfied` を**条件成立の立ち上がり（false→true）**で発火（rule 単位のプロセス内メモリ・初回は基準のみ。しきい値付近のばたつきは未吸収＝将来ヒステリシス可）。発火時は `Z2_WHEN_SENSOR`（`shake`/`light`/`proximity:near|far`）、light は `Z2_WHEN_LUX` も渡す。
- `notify:any` / `notify:otp` / `notify:pkg=<部分>` / `notify:title=<部分>` / `notify:contains=<部分>`（0.8.236）… **通知が届いたとき**。判定は `sms:*` と同じ考え方に揃えてある（覚えることを増やさない）。`pkg=` は**パッケージ名でもアプリ表示名でも**当たる（パッケージ名は覚えていないことが多い）。`Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT` / `_CATEGORY` を渡し、`notify:otp` では抽出コードを `Z2_WHEN_OTP`（`sms:otp` と同名）に入れる。**ログ保存（`notificationLogEnabled`）とは独立**に働く — 「記録はしないがトリガーには使いたい」が普通の使い方で、記録を必須にすると通知本文がずっとファイルに残る。同じ通知の再掲（進捗更新など）は**トリガーの前に**重複判定で落とす。通知アクセスの許可が前提。
- `notify:category=<種別>`（0.8.293）… 通知の**種別**（`Notification.category`）で拾う。`call` = 着信中 / `missed_call` = 不在着信 / `msg` `email` `alarm` `event` `progress` など、Android が決めた語彙。⚠ **ここだけ完全一致**（大小は無視）にしてある — 部分一致にすると `call` が `missed_call` の部分文字列なので「着信のとき」と書いたルールが不在着信でも動き、**両者を書き分けられなくなる**。`pkg=` で同じことをやろうとすると電話アプリのパッケージ名（端末ごとに違う）を知っている必要があるが、種別なら端末を選ばない。⚠ **これは権限を増やさないための道具**でもある: 着信番号を直に得るには `READ_CALL_LOG`（Android 9+）、電話帳の照合には `READ_CONTACTS` が要り、前者は既定の電話アプリ以外まず配布できない。電話アプリは電話帳にいる相手なら名前を、いなければ番号そのものを通知に出すので、**表示が番号の形か**を見れば「電話帳に無い相手か」は通知アクセスだけで判定できる（サンプル `unknown-call`）。
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

**絞り込み（`if=` / `cooldown=` / `between=` / `days=`、0.8.263）**: ルールに**「動いていい状況か」**を足す任意項目。`trigger=` が「いつ動くか」を決めるのに対し、こちらは**どのトリガーにも同じように効く**。

**なぜ足したか**: トリガーを 1 種類足しても増えるのは 1 つだけだが、絞り込みは**既存 9 種すべて**に効く。しかも新しい常駐も新しい権限も要らない。これまで「自宅 Wi‑Fi のときだけ」「夜だけ」「連続で走らせない」を書くには**ユーザー側のスクリプト先頭に `z2-state` を見る `if` を毎回書く**しかなく、しかもそれは `.fired` に「走った」として残るため**弾かれたのか実行して何もしなかったのか区別が付かなかった**。

- `if=<条件>[,<条件>…]` … 発火した瞬間の端末の状態で絞る。カンマは **AND**、頭の `!` で否定。判定は Android 非依存の `WhenGuard.conditionsMet`（`WhenGuardTest` で具体例検証）。**キーは `z2-state` が返すものと同じ語彙**（`wifi` / `charging` / `screen` / `locked` / `ssid` / `level` / `temp` …）で、値も `Z2ApiBridge.stateSnapshot` 経由で**`z2-state` と同じ関数**から取る — 別実装で判定すると端末で確かめた値とルールの挙動が必ずズレる。書き方は真偽（`wifi`）・一致（`ssid=Home`・大小文字無視）・数値比較（`level<30`）の 3 つ。`screen` だけは `on`/`off` が返るので、裸で書いたときは `on` を真として読む。**知らないキーは不成立**（誤発火より取りこぼしを選ぶ既存方針）だが、それだけだと打ち間違いで黙って動かなくなるので **CLI の登録時にキー名を検査して弾く**（一覧は `WhenGuard.KNOWN_KEYS` と `z2-when` スクリプトの 2 か所にある。増やすときは両方）。
- `cooldown=<時間>` … 前回の実行からこの時間は再実行しない（`30s` / `10m` / `2h`・単位省略で分）。`time:every=` と違って**最短 1 分に切り上げない** — `sensor:shake` の連打を数秒だけ抑えたい、という使い方に意味があるため。最後に実行した時刻は **`.lastfire`（`id=エポックミリ秒`）**で持つ。`.fired` は 50 件でローテートするので**判定の根拠にはできない**（発火の多いルールが混ざると直前の実行が記録から押し出される）。
- `between=HH:MM-HH:MM` … その時間帯だけ。**開始を含み終了を含まない**。開始 > 終了は**日跨ぎ**（`22:00-07:00` は夜通し）。
- `days=mon-fri` / `sat,sun` / `1-5` … その曜日だけ。数字は **cron と同じ 0-7（0,7 が日曜）**で、覚えることを増やさない。
- **書式が壊れていたら絞らない**（`between` / `days`）。時間帯の書き間違いで**ルールが永久に動かない**状態を作らないため。`if=` だけは逆に不成立側へ倒す（状態が読めないまま実行する方が危ない）。
- **判定は `runRule` の入口 1 か所**で、キルスイッチのすぐ後ろ。トリガーを増やしても効かせ忘れが起きない。評価は**安い順**（時計を見るだけの `between`/`days` → ファイル 1 つの `cooldown` → 端末の状態を集める `if`）で、手前で弾ければ後ろは評価しない。**状態を読むのは発火の瞬間だけ**で常時監視は増やさない。
- **「どれか満たす」を別項目にする（`if_any=`、0.8.372）**: `if=` は今までどおり AND、`if_any=` が **OR**（判定は `WhenGuard.anyConditionMet`）。両方あれば「`if` を全部満たし、**かつ** `if_any` のどれか」。⛔ **1 つの式に `&&` `||` `()` を混ぜる案は採らなかった** — 優先順位を覚えないと読めない式は未経験の利用者にとって負債でしかなく、画面の「すべて満たす / どれか満たす」とも 1:1 で対応しなくなる。⚠ **`if_any=` が空なら絞らない**（`WhenGuardTest` が固定）。ここを false にすると `if_any=` を書いていない既存ルールが全部止まる。⚠ 状態のスナップショットは **1 回だけ取って両方に使う** — 別々に集めると「全部満たす」と「どれか満たす」が違う瞬間の端末を見る。
- **「そうでないとき」を 1 段だけ持つ（`else=`、0.8.372）**: `if` / `if_any` に合わなかったとき、`run` の代わりに走らせるコマンド。⚠⚠ **効くのは `skip:if` のときだけ**。`between` / `days` / `cooldown` で見送ったときは **else も動かさない** — 「夜は動かないはずのルール」から夜中に通知が飛ぶのは、条件を書いた本人にとって一番の驚きになる。⚠ **cooldown は else では消費しない**（走ったのは代わりの方で、本命はまだ 1 度も走っていない）。⚠ **`run` と `else` は同じ `execute()` を通す** — 環境変数の渡し方やログの置き場が片方だけ違うと「本命では使えた `$Z2_WHEN_TRIGGER` が else では空」という、再現条件の見えない食い違いになる。⚠ 階段（`if` → `elif` → `else`）は**採らない**: ファイル形式と画面の両方が複雑になる割に、1 段で足りる場面がほとんど。
- **画面では条件を「選ぶだけ」で組む（`WhenConditionSpec`、0.8.373）**: 端末なら `wifi,!screen,level<30` と書けばよいが、**画面で同じものを打たせると 1 文字違いで「一覧に並ぶのに一生動かないルール」**になる。文字列 ⇄ 構造の変換を Android 非依存の 1 か所に置き（`WhenConditionSpecTest`）、UI はキー・演算子・値のプルダウンを出すだけにする。キーの型（真偽 / 一致 / 数値）で入力欄を出し分け、⚠ **キーを選び直したら演算子と値も既定へ戻す**（`level<30` のまま `wifi` へ変えると、画面では表せない組み合わせが残る）。⚠ **保存の前に build → parse の往復で検証する** — 値の入れ忘れが「見た目と実際の式のズレ」になるのを、書いた瞬間に止める。
  - **条件行に「今の値」を添える（0.8.374）**: 実機で `if_any=volume>77,volume<5` というルールが作られ、**条件に合っていないつもりなのに動いた**（利用者の指摘）。`volume` は % ではなく**端末ごとのステップ数**（この端末は 0〜15）で、`volume>77` は永久に成立せず、`volume<5` が 0 で成立していた＝ OR として正しい動作だったが、**画面には単位もスケールもどこにも出ていなかった**。⇒ 編集画面を開いた時点で `Z2ApiBridge.stateSnapshot` を **1 回だけ**読み、各行に `今: 0 / 15` のように添える（`volume` は上限も一緒に出す）。⚠ 真偽の読み方は `WhenGuard.truthy` を通す — 表示だけ別実装にすると「今: いいえ」と出ているのに条件は成立する、という最悪のズレになる。⚠ **追従はしない**（常駐も通信も増やさない）。条件を組むのに要るのは「いくつを境にするか」の目安であって秒単位の現在値ではない。
  - ⚠⚠ **組み立て直せない式は触らずにそのまま見せる**（`parse` が null → テキスト欄）。`screen=on` のような端末では有効な書き方や、`if` と `if_any` の両方を持つルール（端末からは書ける）を画面が勝手に解釈すると、**開いて閉じただけで動きの変わるルール**ができる。表せないものを黙って捨てないことが、この変換を別ファイルに切り出した理由。
- **画面で組んだルールを `z2-when` の 1 行で見せる（`WhenCommandLine`、0.8.375）**: 画面と CLI は同じファイル（`~/.z2term/when/<id>.rule`）を読み書きしているのに、**画面で組んだ自動化が端末では何というコマンドになるのかどこにも出ていなかった**＝画面で覚えたことが端末で使えず、端末で読んだ例を画面のどこに入れるのかも分からない。⇒ 編集画面の下に、いま入っている内容そのままの `z2-when <トリガー> name=… if=… run <コマンド>` を出し、**タップでコピー**できるようにする（並びは `z2-when` の usage と同じ）。⚠ **保存する中身とプレビューは同じ 1 つの `WhenRule` から作る** — 別々に組むと「画面に出ているコマンド」と「保存されるルール」がズレて、コピーした 1 行が別のルールになる。⚠ **貼れば同じルールがもう 1 本できる**ことが責任なので、シェルに食われる文字（`*` は glob、`>` はリダイレクト、`run` の後ろは `"$*"` なのでコマンドは 1 引数にまとめる）の扱いを具体例で固定する（`WhenCommandLineTest`）。⚠ **表示だけ**で、この行を編集してもルールは変わらない。トリガーか実行コマンドが空のときは出さない（貼っても動かない行を見せる方が混乱する）。
- **新しい項目を足しても古い版が壊れない**のは、`WhenRule.parse` が**知らないキーを黙って無視する**から（`WhenRuleTest.parse_unknownKeysAreIgnored` が固定）。`else=` を知らない版では「条件に合わないとき何もしない」に落ちる＝**安全側**に倒れる。
- **弾いたことも記録する**（`.fired` の status が `skip:if` / `skip:if→else` / `skip:between` / `skip:days` / `skip:cooldown`）。`status=paused` を残しているのと同じ理由で、「なぜ動かないのか」を探す手段を消さない。自動化タブでは実行とも一時停止とも違う色（`ZtsWarning`）で出す。
- **▶「いま試す」は絞り込みを無視する**（`manual`）。条件で動かないと「試して確かめる」手段そのものが無くなる。一時停止と同じ扱い。
- **既存ルールは 1 行も書き換わらない**。`WhenRule.parse` が知らないキーを黙って無視する作りなので、新しい項目が付いたルールを古い版のアプリで読んでも壊れない（逆も同じ）。付いていないルールの見え方も変えない（画面も CLI の `list` も、付いているときだけ 1 行足す）。
- **綴り違いは登録の時点で弾く**（`if=` のキー、および**トリガーそのもの**・0.8.265）。⚠ **`z2-when` は登録を断らないと事故が見えない** — 綴りが 1 文字違っても `.rule` は書かれ、id も返り、`z2-when list` にも普通に並び、そして**一度も発火しない**。外から見て正しいルールと区別が付かないので、実行時の記録（`skip:*`）では拾えない種類の失敗になる（そもそも発火しないので記録も残らない）。だから**書いた瞬間に止める**。種別（`net` / `boot` / `charge` …）と、種別ごとの引数の形（`net:online|offline|wifi|mobile|ethernet` など）の両方を見る。⚠ **`event:` の名前だけは中身を見ない** — 名前は増え続け、正本は `z2-when events` の一覧側にあるので、ここで二重管理にすると足すたびに片方を忘れる。空でないことだけ確かめる。検査表は `WhenRule` の KDoc と `whenHelp` と揃えること（**3 か所ある**）。

**キルスイッチと発火の記録（0.8.227）**: トリガーが増えるほど**裏で勝手に走る回数**が増えるのに、暴走したときに全部止める 1 操作も、「さっき何が走ったか」を見る場所も無かった。`event:`（0.8.226）でその落差が実害になる前に足す。

- **一時停止は `~/.z2term/when/.paused` の有無**（DataStore ではなくファイル）。CLI（`z2-when pause` / `resume`）とアプリの画面が**同じ 1 つの真実**を見るため。ルールがファイルなのとも揃う。
- **判定は `runRule` の入口 1 か所**。トリガーを何種類増やしても止め忘れが起きない。**時刻トリガーの AlarmManager 予約は解除しない**（捨てると再開時の貼り直しが要り、`time:at` の「次の 1 回」も失われる）。発火はしても入口で弾く。
- **止めたことも記録する**（`status=paused`）。黙って動かないと「なぜ動かないのか」を探す手段が無くなる。
- **`~/.z2term/when/.fired` に 1 行 1 発火**（TSV: 時刻・rule id・トリガー・`run|paused|manual`、直近 50 件でローテート）。トリガーの値（SSID・SMS 本文）は書かない — 記録は残るものなので、外部由来の文字列を貯めない。
- **▶「いま試す」は一時停止中でも動く**（`runRule(manual=true)`）。キルスイッチは「勝手に走るもの」を止めるためのもので、人が押した実行まで禁じる設定ではない。トリガー固有の env は渡さない（作り物の値で「試したら動いたのに本番で動かない」を作らないため）。`Z2_WHEN_MANUAL=1` だけ入る。

**自動化タブ（`ui/settings/WhenRulesSheet`、0.8.227）**: 📜 に「自動化」タブを足し、一覧・ON/OFF・実行ログ・▶試す・**✎ 編集（0.8.272）**・削除・一時停止・直近の発火をまとめる。設定 › 常駐サーバー・自動化からも同じ中身を開ける（`ServersBody` と同じ 2 経路の作り）。部品（`ToggleRow` / `HintBox` / `IconCell` / `PillButton` / `Field`）は `ServersSheet` から `internal` で共有し、見た目を 2 か所に書かない。

- **ルールに名前を付けられる（0.8.303。`name=`）**。一覧の見出しはトリガーだったので、`event:screen_on` のルールが 3 本並ぶと**どれが何の自動化なのか区別が付かなかった**（実機で指摘）。ルールファイルに `name=` を足し、見出しは `WhenRule.label`（= `name` があればそれ、無ければ `trigger`）1 か所で決める。**未記入のルールの見え方は今までどおり**（既存のルールに `name=` 行は付かない）。名前を付けたときだけ見出しの下にトリガーを 1 行出す（名前でトリガーを消してしまわないため）。直近の発火も名前で出す（記録に残っているのはトリガーなので、id で今のルールを引いて名前があれば差し替える。消したルールの記録はトリガーのまま）。**表示だけの項目で、発火にも実行にも一切影響しない**（`order=` と同じ扱い）。編集フォームでは必須にしない — 1 行足すために保存が止まる方が嫌われる。⚠ `run` と同じく**改行は空白へ潰す**（ルールファイルは 1 行 1 項目なので、名前に改行が入ると後ろの行が捨てられる）。
- **指を離したときに行が飛ばないようにする（0.8.272。`ReorderList` / スニペットタブ共通）**。掴んでいる間の指追従オフセットは「隣を跨ぐたびに 1 行ぶん引く」ので、離した時点では**半端に残っている**。そこへ 0 を代入すると残りぶんが 1 フレームで詰まり、**離した瞬間にパッと入れ替わった**ように見えていた（実機で指摘）。離したあとも `settlingId` の間は掴んでいるときと同じ扱いにして、`REORDER_SETTLE_MS`（140ms）かけて 0 まで滑らせる。⚠ もう 1 つの原因は**保存が非同期**なこと — 書き込みが反映される前に外の一覧を取り込むと、並べ替えたものが一度戻ってからまた入れ替わる。`pending`（commit した並び）を持ち、**顔ぶれが同じで並びだけ違う間は取り込まない**。顔ぶれが変われば（追加・削除）外を優先する。スニペットタブは並べ替えを独自に持っているので**同じ直しを両方に当ててある**（行が固定高で作りが違うため統合はしていない。直すときは両方見ること）。
- **1 回のドラッグイベントで跨いだぶんだけ入れ替える（0.8.510）**。判定を `if` で 1 回きりにしていたので、少し勢いよく動かすと**指だけ先へ行って順番が追いつかない** — 「一気に運べない・1 個ずつしか動かない」という形で出る（ボタンが 10 個並ぶ設定画面のツールバーで顕著）。ドラッグのイベントは指の動きより粗い間隔で届き、1 回に 2 個ぶん以上の移動が乗るため。`swapWhilePossible` で跨いでいる間ずっと回す。⚠ 実測サイズ 0 の項目では回さない（`pitch <= spacingPx` を弾く。回すと止まらない）。
- **端に寄せている間は自動でスクロールする（0.8.510。`rememberReorderState(scrollState = …)`）**。指は画面の中でしか動かせないので、これが無いと**見えている範囲より先へは運べない**（設定画面のツールバーは 10 個並び、一度に見えるのは 6 個ほど）。掴んだ項目の見かけの中心が端から 1 項目ぶんに入ったら、そちらへ 1 フレーム 1/5 項目ぶん流す。⚠ **スクロールしたぶんは指追従オフセットにも足すこと。** 指は止まっているのに内容だけが流れるので、足さないと掴んだ項目が指から置き去りになる。足せば「指の下に留まったまま、下を流れていく列との相対位置が変わる」= そのぶん順番が進む、という自然な動きになる。位置は `onPlaced` の `positionInParent()` で控える（`graphicsLayer` の移動を含まない**配置上の**位置なので、見かけの位置はそれ + オフセット）。`scrollState` を渡さなければ従来どおり自動スクロールしない。
- **≡ を掴んで並べ替えできる**（0.8.249。サーバータブと共通）。並びは各ルールファイルの `order=<n>` 行に書く（`WhenManager.reorderRules`）。**並び順専用のファイルは作らない** — ルールファイルが正本という上の方針を崩さないため。CLI は `order` を書かないが、`z2-when on|off` は enabled 行だけを sed で書き換えるので、画面で決めた並びは端末から操作しても消えない。`order` を持たないルール（端末から足したもの）は id 順で末尾に並ぶ。**表示順だけで、実行にもトリガーにも影響しない。**
- ⚠ **`Switch` は ON 側だけでなく OFF 側の色も必ず指定する**（0.8.242）。`SwitchDefaults.colors()` に `checked*` しか渡さないと OFF 側は Material3 の既定配色（暗い `surfaceVariant`）のままになり、**暗い背景のこのアプリではスイッチが背景に溶けて「そこに何も無い」ように見える**。一時停止トグルとルールの ON/OFF がこれで見えなくなっていた（実機で指摘）。`uncheckedThumbColor = ZtsTextSecondary` / `uncheckedTrackColor = ZtsBgCard` / `uncheckedBorderColor = ZtsBorder` を揃って渡すこと（設定画面の `ToggleField` と同じ組み合わせ）。

**ルールの追加と編集を画面に載せた（0.8.272。`WhenRuleEditForm`）**。0.8.271 までは「見る・止める・試す」だけに留め、作るのは `z2-when` に任せていた（正本はテキスト・ロジックはシェル側という §3.3 の設計を崩さないため）。しかし実際に使うと、**この線引き自体が事故を生んだ**:

- `run` の**全文が画面のどこにも出ない**（一覧は 1 行で省略）。「このルールが何をするのか」を確かめるのに端末を開くしかなかった。
- **折り返して貼り付けたコマンドが途中で切れ、黙って構文エラーになり続けた**（実機で発生）。ルールファイルは 1 行 1 項目なので、`run` に改行が入ると 2 行目以降が捨てられる。`wifi:connect` で `sshd --lan` を起こすルールが `for` の途中で切れており、発火のたびに `syntax error: unexpected end of file` をログへ書くだけになっていた。CLI も画面もこれを検査していなかった。

**正本は変えていない** — 画面が書くのも同じ `~/.z2term/when/<id>.rule` で、書式も `WhenRule.serialize()` の 1 本きり（`WhenManager.saveRule`）。端末で作ったものを画面で直せるし、その逆もできる。二重管理になるのは「アプリ側に別の保存場所を持ったとき」であって、**同じファイルを両方から編集する分にはならない**。

- **きっかけは候補から選ぶ**（`WhenTriggerCatalog`）。綴りが 1 文字違うだけで「一覧に並ぶのに一生発火しない」ルールになるので、**そのまま入れて動く完成形**（`battery:below=20`）を候補に並べ、選んだ直後から正しい状態にする。検査は CLI の case 文と同じ判定を Kotlin 側にも持つ（`WhenTriggerCatalogTest.everyOptionIsValid` が候補と検査の食い違いを止める）。⚠ 一覧を増やすときは **CLI の `z2-when` と両方**直すこと。
- **改行は入った瞬間に空白へ潰す**（画面・CLI の両方）。弾くのではなく直して通し、直したことだけ伝える（`Z2ApiMessages.whenRunJoined`）。折り返して貼るのは避けられない以上、**貼れてしまうなら動く形に直す**のが正しい（CLI 側は `tr '\n\r' '  '`）。
- **`run` がスクリプト 1 本を指しているときは中身も出す**（読み取り専用・`WhenManager.readRunScript`）。読めるのは共有 HOME の下だけで、`canonicalPath` で外を弾く（`TailStore.resolve` と同じ約束）。**直すのは端末のまま**にしてある — スクリプトエディタまで載せ始めると、こんどこそシェル側のロジックをアプリが抱えることになる。パイプや `&&` で他のコマンドと繋がっているものは「1 本のスクリプト」ではないので拾わない（どれを出すか決められないものを勝手に決めない）。

**CLI**（`z2-when`。`Z2ApiScript` が launch 毎に `/usr/local/bin` へ配置）: `<trigger> [name=… if=… cooldown=… between=… days=…] run <cmd>` で登録（名前と絞り込みは**トリガーの直後・`run` より前**に置く。`run` の後ろは全部コマンド、という今までの読み方を変えないため。`name=` は 0.8.303。空白を含むならクォートが要る）/ `list`（TSV。一時停止中は先頭に注記。名前と絞り込みが付いていれば末尾に `[name=… if=…]`。**カラムは増やさない** — 既存の TSV を `cut` で読んでいる手元のスクリプトを壊さないため）/ `events`（`event:` に使える名前の一覧・0.8.226）/ `pause` / `resume` / `fired [n]`（0.8.227）/ `remove <id|all>`（`rm`）/ `on|off <id>` / `log <id>`。id は `w<epoch><pid>`（同一秒の衝突を避けるため 0.8.211 で乱数から pid へ変更。既存があれば連番を付す）。画面から作ったものも**同じ見た目の id**にする（`WhenManager.newRuleId` は pid の代わりにミリ秒を使う。一覧でどちらが作ったか分からない方がよい）。**`run` に改行が入っていたら空白へ直して登録する（0.8.272）** — 折り返して貼り付けたコマンドが黙って途中で切れるのを止めるため（直したことは stderr に出す）。**stage2 は cron/wifi/sms/sensor まで実装済み**（0.8.207〜0.8.210）。以降の候補は `time:cron` の DST 跨ぎ精緻化や照度ヒステリシス等の作り込み。

#### ルールが起こしたデーモンが即座に殺されていたのを直す（`z2root --wait-tracees` / `HeadlessRun`、0.8.251 + 0.8.253）

**症状**: `z2-when wifi:connect run 'sshd --lan'` のように**デーモンを起こすルール**が働かない。実行ログには `✅ dropbear listening … on :2222` が毎回残るのに、直後には誰も listen しておらず外から繋がらない（`Connection refused`）。同じことを常駐サーバーに登録したスクリプトからやると正しく生き残るため、「自動化のときだけ死ぬ」形で出る。

**原因はエンジン側にあった**（`z2root.c` の `run_tracer`）。z2root は **メインの tracee（`sh`）が終わった時点でトレースループを抜ける** — `if (pid == child) alive = 0;` — 他の tracee が生きていても待たない。そして z2root プロセスが終了すると、`--kill-on-exit`（`PTRACE_O_EXITKILL`）によってカーネルが**残りの tracee を皆殺しにする**。ルールが `sshd --lan` を呼ぶと dropbear はデーモン化して本当に listen し（ラッパーは pidfile を `kill -0` で確かめてから `listening` を出す）、`sh` が exit した瞬間にエンジンごと消える。**ログが「起動できた」で終わっているのはその時点では本当に起動できていたため**で、ログを疑うと原因に辿り着けない。常駐サーバー経由が無事なのは、スクリプトが回り続けてメインの tracee が終わらないから（GUI が `while x_running; do sleep 2; done` でブロックさせているのも同じ回避）。

⚠ **`--kill-on-exit` を外す案は解にならない**。z2root は対象プロセスに seccomp フィルタを仕掛けるので、トレーサが居なくなると該当 syscall が軒並み `ENOSYS` を返し、生き残ったデーモンが壊れる。**トレースを続けたまま生かす**しかない。

**対策は 2 段**（片方だけでは効かない。0.8.251 でアプリ側だけ直して**実機で直っていないことを確認**し、0.8.253 でエンジン側を直して完結した）。

1. **エンジン: `--wait-tracees`（0.8.253）**。渡されたときはメインの tracee が終わってもループを抜けず、残った tracee の syscall を翻訳し続け、全員が居なくなって `waitpid` が `ECHILD` を返した時点で終わる。終了コードはメインのものを使う（後から終わる tracee に上書きされない）。**単発実行（`HeadlessRun`）でだけ渡す** — 端末タブは既定のままで「シェルを抜けたらタブが終わる」を変えない。⚠ proot には渡さない（未知オプションで起動に失敗する）。
2. **アプリ: 後始末で kill しない（0.8.251）**。EOF は「前景スクリプトが終わった」だけで「配下が終わった」ではないので、`PtyProcess.close()` ではなく `waitFor()` → `detach()`（**シグナルを送らず fd を閉じるだけ**）にする。背景に何も残さない普通のルールでは `waitFor` は即座に返るので挙動は変わらない。明示的に止める `HeadlessRun.stop()` は従来どおり `close()` で丸ごと畳む（人が止めたのだから道連れが正しい）。

⚠ `detach()` を**ルートが生きているうちに呼んではいけない**。マスタ fd を閉じるとカーネルが端末のフォアグラウンドプロセスグループへ SIGHUP を送り、結局ルートごと落ちて同じ道連れになる。
⚠ こうして生き残るデーモンは**アプリのプロセスが生きている間だけ**生きる（エンジンの子であるため）。恒久的に上げ続けたいものは常駐サーバーに登録する。

**タブの状態表示（0.8.229）**: 見ていないタブに**動作中は小さな塗り四角、見ていない間に終わっていれば `✓`**を出す。判定 (`AppSession.isBusy`) は**閉じる確認のためにもう計算されていた**のに、タブからは何も見えず「切り替えて確かめる」往復が要っていた。持っている情報を出すだけの追加。

- **アクティブなタブには印を出さない**。見ているものに状態表示は要らないし、`✓` は「開いたら役目が終わる」印なので、開いた時点で消えるのが自然。
- **点滅させない**。暗所で目障りになるうえ、ターミナルの静かな見た目を壊す。4dp の四角と 9sp の `✓` だけ。
- ⚠ **判定できないタブに印を出してはいけない**。`hasForegroundChild` は判定手段が無いとき**安全側の `true`** を返す（マウスレポート漏れ対策としてはそれが正しい）。そのまま表示に使うと **SSH タブに永久に「動作中」が点く**。`ProcessChannel.supportsForegroundChild`（ローカル PTY だけ true）と `AppSession.busyKnown` を足し、表示側はこちらを見る。閉じる確認は「多めに確認する」で済むが、表示は嘘が出っぱなしになるという非対称がある。
- ポーリングは**タブバーで 1 回だけ**まとめて回す（1 秒 × タブ数の `tcgetpgrp` を避ける）。判定は `nextEndedIds` に切り出して `TabMarkTest` が固定する（「終わったのに出ない」「終わっていないのに `✓`」はどちらも気付きにくい）。

**初回ガイド「最初の 3 枚」（`ui/terminal/IntroCards`、0.8.231）**: 入れた直後の画面は黒地に `#` だけで、Linux を知らない人はそこで止まる。知っている人にも「ふつうの端末」に見えて、**Z2Term の差（= Android を触れること）に気付かないまま終わる**。最初の 90 秒で「うごいた」を 1 回配るための 3 枚（0.8.286 でリマインドの 2 枚を足して 4 枚になったが、0.8.314 でその 2 枚を「案内を開く」1 枚に畳んで 3 枚へ戻した）。

- 中身は「通知を出す」「ライトを点ける」「リマインドの案内を開く」。**Android を触れること 2 枚 + 案内への入口 1 枚**で、どれも 1 行で結果が出るものだけにする（待たされるものを最初に置かない）。
- **タップしたら実行する（0.8.314・利用者の判断で変更）**。0.8.313 までは「入力行に入るだけで ⏎ は人が押す」作法だったが、案内は**打ちかけの途中でも押せてしまう**ため、`ls -l` を打った後に押すと `ls -lz2-macro install remind` のような行ができ、⏎ で意図しない行が走る。**先に `Ctrl-C`（0x03）で行を捨ててから**コマンド＋改行を送る形にした（`runGuideCommand`）。⚠ `Ctrl-C` の直後に続けて書かないこと — tty は INTR を受けた時点で**入力待ち行列を捨てる**（`ISIG` かつ `NOFLSH` 無しの既定動作）ので、同じ塊で送るとコマンドまで一緒に消える。150ms 空ける。
- 文言は「説明」ではなく**打つコマンドそのもの**を見せる — 読ませる画面ではなく、1 回動かしてもらう画面なので。
- **カードごとに ✕ が付く（0.8.314）**。要らない手順を**送らずに**消せる。触った枚も消え、全部消えるか見出しの ✕ を押したら [AppSettings.introDone] を立てて**二度と出さない**。
- ⚠ **出すのは Linux の OS が 1 つ入ってから**（0.8.339・利用者の指摘）。0.8.338 までは `introDone` だけを見ていたので、**OS が 1 つも無い端末（入れた直後は全員そこから始まる）にも 3 枚が出ていた**。その状態では端末が動いていないので**押しても何も走らない**のに、**押した枚から消え**、3 枚とも消えると `introDone` が立って**一度も動かないまま二度と出ない**。判定は `TerminalSession.hasAnyDistro()`（`NeedOsInstall` と同じもの）で、**端末の状態が変わるたびに見直す** — ⚙設定 から OS を入れると起動が始まるので、それが「入れ終わった合図」になり、**そのタブでそのまま 1 回出る**。⚠ **表示を止めるだけにしないこと**。`introDone` を空振りで消費させないのが要点で、出番を失う経路を残すと直したことにならない。⚠ 既に OS がある人（`introDone=true`）には出戻らせない。OS が無い間に出るのは `NoOsNotice` の方（下記）。
- ⚠ **32 件の提案で唯一「モードを増やすな」と正面衝突しうる案**だった。だから仕様を先に固めてある: **項目は 3 つまで・全画面ウィザードにしない・復活は設定の奥に 1 行**（メンテナンス）。4 枚目を足したくなったら、それは `z2help` と案内（下記）の仕事。ここを緩めると 5 枚 6 枚と増える理由が毎回生まれ、二度と閉じられなくなる。

**案内（`ui/terminal/GuideCards`、0.8.314）**: 同梱サンプルのマクロは `z2-macro install <名前>` で入れてから使うものなので、**入れる前は名前すら見えない**。0.8.286 では「書き方の一覧をすぐ開ける場所」としてスニペットに `remind.sh help` を 1 件シードしていたが、**入れていない人が押すと「見つからない」と出るだけ**で、そこから入れ方に辿り着けなかった（利用者の指摘）。スニペットのシードを撤去し、**どのサンプルも手順のカードで出す**形に置き換えた。

- 入口は **⚙設定 › メンテナンス › 「案内を表示」**。9 本の案内があり、何度でも出せる。
- ⚠ **一覧は「マクロの名前 + 何をするか」の 2 行**（`watch-basic` / `battery-alert` / `daily-report` / `otp-clip` / `otp-sms` / `unknown-call` / `remind` / `rss` / `qr`。0.8.335〜0.8.336・利用者の指摘）。0.8.334 までは**説明文だけ**を並べていたが、「入門: できごとに反応する」が何をしたいものか読めず、**どのマクロの話かも分からなかった**。0.8.335 で**名前だけ**に振り切ったところ、今度は**何をするものか分からない**と言われた（実機で確認しての判断）。片方では足りないので、名前を主・説明を添えて出す。案内カードの見出しは 1 行しか無いので `guideTitle()` で `rss — フィードの新着を通知して、1 本ずつ読む` の形に畳む。⚠ **繋ぎ方はその 1 か所だけ**にして、`strings.xml` の説明文に名前を書き込まない（二重管理になる）。
- ⚠ **説明は「何をするか」を言い切る**（0.8.337・利用者の指摘）。名前と並べても、説明が曖昧なら結局分からない。実機で読んで詰まったのは 3 つとも**動かしてみないと分からない部分**だった: 「電池が減ったら知らせる」は**何 %** でなのか（→「電池が決めた % を切ったら知らせる」。案内の途中で % を聞いて `z2-when battery:below=` に入れているので、説明もそう言う）、「毎朝きまった時刻に読み上げる」は**何を**読み上げるのか（→「毎朝きまった時刻に電池と接続を読み上げる」。`daily-report.sh` が喋るのは残量と Wi-Fi / モバイルの別）、「充電やイヤホンの抜き差しに反応する」は**すぐ反応しない**（→ 下記）。
- ⚠ **`watch-basic` を `z2-when` で待ち受ける形に変えた**（0.8.338・利用者の判断）。0.8.337 まではログを見に行く常駐スクリプトで、**反応が 10 秒近く遅れる**のを説明に書いて済ませていた（`POLL` = 既定 15 秒）。だが遅れの正体は「自分で待っている」ことで、しかも**拾っていたのは `z2-when` で書けるきっかけ**（充電・イヤホン）だった。`event:` はブロードキャストを受けたその場でルールを走らせる（`SystemEventService`）ので、待ち受けをアプリ側へ寄せると遅れも待機中の電池消費も消える。案内は「充電のきっかけ」「イヤホンのきっかけ」の 2 本を登録し、最後に `Z2_WHEN_EVENT=power_connected sh …` で**充電したことにして**動かして確かめる形にした。⚠ **4 つのイベントに 4 本のルールを並べない** — ワイルドカード（`event:power_*` / `event:headset_*`）で 2 本にまとめる。同じマクロで自動化タブが埋まると、何を仕掛けたのか読めなくなる。
- ⚠ **`rss` と `rss-open` を 1 本にまとめた**（0.8.335・利用者の指摘）。「フィードの新着を通知する」と「集めた記事を 1 本ずつ開く」が一覧に別々に並んでいても、**同じ 1 つの購読の話**だとは読めない。集めるのと読むのは続きなので、続けて並べる。
- ⚠ **自分の値が要る手順は、実行する前に聞く**（`GuideStep.askRes`・0.8.335・利用者の指摘）。0.8.334 までは「読みたいフィードの URL を書く」を押すと `https://example.com/feed` が**そのまま登録された** — 見本の値のまま実行できてしまうと、**動くはずのない設定が黙って入る**。URL・時刻・しきい値・QR にする文字列は入力欄で受け取り、空のままでは送れない（見せるコマンドの `%s` にはその場で値が入る）。
- 見た目・送り方は初回ガイドと**同じ部品**（`GuideCardColumn` / `GuideCardRow`）。並べる順がそのまま手順の順で、タップで 1 行ずつ実行、要らない行は ✕ で捨てる。
- ⚠ **カードのコマンドに翻訳が要る文字を入れないこと**。ここは `strings.xml` を通らないので、本文に日本語を混ぜると英語環境でも日本語のコマンドが送られる。例示は `remind.sh list` のように**言語に依らない形**で書く。
- コマンドを持たないカード（⚙設定 を触る手順・前提パッケージの案内）は読むだけ。タップすると「読んだ」として消える。
- **GUI タブから選んだときは端末タブへ移ってから出す**。案内はコマンドの手順なので、GUI の上に出しても打つ場所も結果も無い。画面をまたぐので `GuideHost`（object）に 1 つだけ持つ。
- ⚠ **スニペットに「入れてから使うコマンド」を置かない**。スニペットは押せばそのまま走る場所なので、前提のあるコマンドを置くとエラーが出るだけになる。手順が要るものは案内の仕事。

**OS が 1 つも無いときは、ダウンロードを催促しない（0.8.314）**: rootfs を同梱しないので、初回起動でいきなり Alpine のダウンロード確認ダイアログが出ていた。これは**「どの OS から始めるか」を選ぶ前に既定の 1 本を押し付ける**形で、「Arch から始めたい」人は毎回断ることになる。しかも断っても状態は変わらないので、**タブを開くたびに同じダイアログが出る**（利用者の指摘）。

- 判断は `TerminalSession.startupPlan()`（`suspend`）に集約し、`Start` / `ConfirmDownload(spec)` / `NeedOsInstall` の 3 通りを返す。`NeedOsInstall`（`ProotLauncher.hasAnyDistro()` が false）のときは**確認 ON/OFF に関わらず勝手に入れない**。
- 代わりに画面を塞がない案内カード（`ui/terminal/NoOsNotice`）を 1 枚出す。押すと ⚙設定 › Linux環境 が開き、✕ で消せる。消した記憶は**アプリを開いている間だけ**（`NoOsNotice.dismissed`）＝タブを開き直しても出戻らないが、次に開くと出る（OS が無ければ端末は本当に使えないので「二度と出ない」にはしない）。
- ⚠ **OS が無い間は消せない。⚙設定 の中でも上部に固定する**（0.8.342・利用者の判断）。このカードが「⚙設定 › Linux環境」を教える唯一の口なので、**✕ で消すと黒い画面と `#` だけが残り、何を押せば Linux が入るのか画面のどこにも出ていない**状態になっていた（rootfs を同梱しないので**全員がこの状態から始まる**）。
  - ⛔ **0.8.340 の「消せるまま + ツールバーに 📥」は撤回した**。OS が 1 つも無い間だけ 📥「OS を入れる」を出す案だったが、実機で**「押しても設定画面が開くだけで、何をすればいいのか分からない」**と指摘された。**入口を増やしても、その先で迷うなら解決していない。**
  - **消せなくする**（`GuideCardColumn` / `GuideCardRow` の `onClose` / `onSkip` に `null` を渡すと ✕ を描かない）。塞がないカードなので、消せなくても端末は触れる。⚠ **消せなくしてよいのは「消すと詰む」ものだけ** — 手順の案内（`GuideCards`）と はじめの案内（`IntroCards`）には ✕ を残す（あちらは消しても端末が使える）。
  - **⚙設定 の上部にも同じ案内を固定する**（`NoOsSettingsNotice`）。⚠ 置くのは**スクロール領域の外**（`SettingsTopBar` の直下）。中に入れると下へスクロールした時点で消え、「設定画面まで来たのにどの項目か分からない」に戻る。
  - **押したら Linux環境 のセクションまで運ぶ**。⚠ `SettingsGroup.LINUX` は `defaultOpen=false` なので、**先に `SettingsGroupStore.setOpen(LINUX, true)` で開いてからスクロールする** — 開かずに運ぶと見出しだけが見えて中身が無い。開いた分の高さがレイアウトに反映されるまで `scrollState.maxValue` を待ってから動かす（反映前だとスクロール可能量が足りず途中で止まる。待てないときは 300ms で諦めてそのまま動かす）。
  - 飛び先の座標は `SettingsGroupSection(LINUX)` の `onGloballyPositioned` で測り、**そのときのスクロール量を足して**「先頭からの距離」にする（`verticalScroll` は子の位置をスクロール分ずらすため、足さないとスクロール状態によって値が変わる）。
  - **どちらも OS が 1 つ入れば出ない**。設定側は端末の状態が変わるたびに `TerminalSession.hasAnyDistro()` を見直すので、シートを開いたまま入れても消える。
- ⚠ **判定は永続値を await してから行う**。0.8.313 までの `downloadOnStartSpec()` は `settingsFlow.value` を見ていたが、これは `stateIn(Eagerly)` の初期値＝既定 Snapshot（`distroId=alpine`）なので、DataStore の初回 emit が届く前に通ると**選択中の OS ではなく Alpine の判定になる**。「Arch で使っているのに、新しいタブを開くとタイミングによって Alpine のダウンロードを催促される」の正体がこれで、`startTerminal` が同じ理由で既に await していた（0.8.105）のに、催促の判定だけ取り残されていた。
- ⚠ **⚙設定の OS チップは「選択中でも未導入なら押せる」**必要がある。従来は `id != 選択中` で弾いていたが、初回は**既定（Alpine）が選択済みなのに未導入**という状態から始まるため、そのままだと**その OS だけ入れられない**。自動催促をやめた分、ここが唯一の入口になる。

**複数行の貼り付けだけ、貼る前に見せる（0.8.232）**: 📋 は押した瞬間に入るので、コピー元がコードのかたまりだと**何行入ったのか分からないまま** ⏎ を押すことになる。**改行を含むときだけ** 48dp の帯を出し、行数と先頭 2 行を見せてから貼る。

- **帯をアクセント色で強調する（0.8.255）**。0.8.254 までは背景 `ZtsBgSecondary` ＋枠 `ZtsBorder` で**周囲と同系の暗色**、「貼る」も**緑の文字だけ**だったため、**出ていることに気付けず貼らずに進んでしまう**（実機報告）。枠を緑 2dp、**背景を緑 80%（透明度 20%）**、先頭に 📋（ツールバーの 📋 と結び付ける）、「貼る」を**暗い地に緑文字の塗りつぶしボタン**にした。⚠ 最初は緑 12% で敷いたが、**端末の文字が透けて帯に見えない**と再度指摘されて 90% まで上げ（0.8.256）、濃すぎたので **80% に戻した**（0.8.259）。⚠ 地を濃くしたら前景も一緒に反転させること — 緑地に緑文字では読めないので、行数・本文・✕ はすべて暗色、「貼る」だけ暗い地に緑文字で抜いて主役だと分かるようにしている。⚠ **中身のプレビューは細字にも薄くもしない**（0.8.259）。11sp の等幅を細字＋暗色 70% で敷いたら**何を貼るのか読めない**と指摘された。地が透けるぶん薄い前景から先に沈むので、プレビューは**太字＋暗色そのまま**にし、主役（行数と「貼る」）との差は**文字の大きさだけ**で付ける。⚠ **出る場所は変えない**（`SearchBar` と同じ位置）— 動かすと「どこに出るか」の慣れが無駄になるので、強めるのは色と押しやすさだけに留める。

- ⚠ **1 行の貼り付けには絶対に出さない**。ここを「安全のため」と広げた瞬間、**このアプリで一番よく押すボタンが 2 タップ**になって台無しになる。判定は `text.contains('\n')` の 1 行だけで、迷う余地を残さない。
- 帯の先頭は**行数**。この場面でいちばん効く情報は中身ではなく「何行入るか」。中身は最大 2 行だけ覗かせる（全文を見せる場所ではない）。
- 貼っても**実行はしない**（入力行に入るだけ・bracketed paste は従来どおり）。共有の受け取り（B1）と同じ作法。
- 置き方と寸法は `SearchBar` に揃える（端末領域の上端に出る帯が 2 種類あるので、別々の見た目にしない）。
- **クリップボード履歴から選んだときも同じ帯を出す（0.8.250）**。履歴（📋 ダブルタップ）は選んだ瞬間に貼っていて、**同じ複数行でも入口が違うだけで確認が無い**状態だった。危ないのは「入るとそのまま実行される」ことで、どの入口から来たかは関係ない。判定は 📋 と同じ `text.contains('\n')` の 1 か所きりにして、**入口ごとに違う基準を作らない**。履歴シートは選択と同時に閉じるので、帯はその後ろに出る。⚠ GUI タブ（`GuiScreen`）は 📋 も履歴も keysym 橋渡しで**どちらも確認を出さない**まま揃えてある — 片方だけ足すとちぐはぐになるので、足すなら両方同時に。

**検索中だけ、スクロールバーが地図になる（0.8.233）**: 検索は「3 / 17」と**件数**は出すが、17 件が上に固まっているのか全体に散っているのかが分からず、∨ を連打することになっていた。ヒットの絶対行をスクロールバーの目盛りとして出す。

- **検索していないときは何も足さない**（`matchRows` が空なら 1 本も描かない）。スクロールバーの役目は「どこを見ているか」で、常時なにか出す場所ではない。
- **同じ画素行には 1 本しか描かない**（2dp 単位で間引く）。`grep` 的な検索で数百件ヒットしても帯にならず、「濃さ」で分布が読める程度に留まる。この間引きが無いと、ヒットが多いほど情報量がゼロに近づく。
- 目盛りは**タップでその位置へ飛ぶ**。当たり判定はつまみと同じ幅・高さ 12dp（細い線を狙わせない）。つまみと重なる範囲は**つまみが上**（後に置いてある）なので、掴む操作は今までどおり。

**この画面だけの明るさ（0.8.234）**: 暗い部屋で開くと、**いちばん眩しいのが黒地に緑文字の自分のアプリ**という状況になる。OS の明るさを下げに行くと戻すのを忘れるし、テーマを増やしても解決しない（配色ではなく明るさの問題）。🔅 の**ダブルタップ**でスライダー 1 本の帯を出す。

- 効くのは `WindowManager.LayoutParams.screenBrightness` = **この Window だけ**。ホームに戻れば OS の明るさに戻る。
- 既定は `BRIGHTNESS_OVERRIDE_NONE`（OS に任せる）で、**触ったときだけ効く**。だから設定項目もモードも増えない。単タップは今までどおり画面消灯ロック（📋 や ⌨ と同じ「単タップ=動作 / ダブルタップ=詳細」の作法）。
- **決めた値は設定に残る（0.8.242）**。当初は「いまこの場が眩しい」ための一時的な調整として保存しない仕様にしたが、暗い部屋で使う人は**アプリを開くたびに同じ値へ合わせ直す**ことになっていた。`AppSettings.screenBrightness`（`Float?`）に保存し、次に開いたときは最初からその明るさで出る。
  - **null = OS に任せる**は保存後も同じ意味で、「戻す」は値を上書きせず**キーごと消す**（`remove`）。`0` を書いて「明るさ 0 で保存済み」を作らない。触らない人には今までどおり OS 任せのままなので、保存してもモードは増えない。
  - 保存は**指を離したとき 1 回だけ**（`Slider.onValueChangeFinished`）。ドラッグ中の値はローカル状態で先に窓へ当て、DataStore の往復をつまみの動きに挟まない。
  - 明るさは Window に効く設定なので**端末タブと GUI タブで共通**。GUI タブを開いたまま起動した場合にも保存値が当たるよう、`GuiTabScreen` でも同じ適用を行う（帯を出す口は端末タブの 🔅 ダブルタップのまま）。
- ⚠ 下限 10%。真っ暗にして「戻す」も押せなくなるのが最悪の結末なので、そこには落ちないようにする。「戻す」は帯の中に常に置く（出口が無いと怖くて触れない）。**保存するようになった分、この下限は前より効く**（暗すぎる値のまま次回も開くため）。

**つまずきの言い換え（`core/TerminalHints`、0.8.237）**: ハンドブックの FAQ に答えは書いてあるが、**詰まっている本人はそのとき読まない**。既知のパターンが出力に現れたら、端末の**下端**に「次の一手」を 1 行だけ出す。

- ⚠ **端末の出力そのものは絶対に書き換えない**。別の場所に 1 行足すだけで、スクロールバックにも端末ログにも残らない。書き換えは端末アプリとしての信用に直結する。
- 走査点は**ログ (⚪) と同じ 1 か所**（`readJob` の中・「タブに出るものが必ず通る唯一の場所」）。PTY のチャンクは 8KB 単位なので 1 行が境目で割れることがあり、直前の末尾 256 文字を持ち越してから見る。
- **alt screen 中は見ない**。全画面アプリの描画にたまたま含まれる文字列で誤爆するため。
- **パターンは 4 つだけ**（ping / 1024 未満のポート / `/usr/sbin/sshd` 直叩き / `/sdcard` が見えない）。「答えが 1 行で書けて、実際によく詰まる」ものに絞る。誤爆すると一気にうっとうしくなり、機能ごと消されることになる。
- **「コマンドが無い」は出さない**（0.8.304 で撤去）。`command not found` は端末で最も普通に起きる出来事で詰まりですらなく、案内も 3 ディストロのコマンドを並べるしかない（自分がどれを使っているか分からない人には読めない）。誤爆ではなく、**当たっても役に立たない**パターンだった。
- **同じヒントは 60 秒沈黙**。`command not found` を連打したときに毎回出ると、お節介なアプリになる。
- 設定 › 表示 に **OFF スイッチ**（既定 ON）。うっとうしいと感じた人がすぐ切れる場所に置く。
- 判定は Android 非依存の純関数で、`TerminalHintsTest` が**当たるべきもの**と**当たってはいけないもの**（`PING 8.8.8.8 …` の正常出力、`# ping is not available` のような自分で書いた文字列）の両方を固定する。

**SSH クライアント鍵をアプリで作る（`channel/SshKeyGen`、0.8.238）**: これまでクライアント鍵を使うには**秘密鍵の PEM をテキスト欄に貼る**しかなく、スマホでそれを用意するのはほぼ無理だった。SSH を使い始める前にそこで止まる。

- 「鍵を作る」→ ed25519 を生成し、その場で**公開鍵をコピー / 共有 / この端末の sshd に登録**まで行ける。`cat … >> ~/.ssh/authorized_keys && chmod 600 …` の手打ちが不要になる。
- **既存の貼り付け欄は残す**。作る／貼る の 2 択で、モード分けはしない（PEM を用意できる人はこれまでどおり）。秘密鍵本文は既定で伏せ字にし、「表示 / 隠す」を明示したときだけ見せる（0.8.457）。編集画面を開いただけで PEM 全体を画面へ出さない。
- ⚠ **JSch では作れない。** `KeyPair.genKeyPair(…, ED25519)` は生成できるが、`writePrivateKey` が `UnsupportedOperationException` を投げる（JSch は ed25519 の**読み込みには対応、書き出しには未対応**）。SSH のために既に入っている **BouncyCastle** で生成し、OpenSSH 形式（`openssh-key-v1`）で書き出す。**作った鍵を JSch が読めること**を `SshKeyGenTest` が実際に `KeyPair.load` して確認する — ここが食い違うと「作れたのに繋がらない」という一番切り分けにくい失敗になる。
- パスフレーズは付けない。付けると接続のたびに入力を求めることになり、「まず繋がる」までの距離が伸びる。秘密鍵は従来どおり `KeystoreCrypt` で暗号化して端末内に留まる。
- 公開鍵は**保存しない**（渡すのは作った直後だけで、秘密鍵からいつでも作り直せる）。`authorized_keys` へは**鍵本体で重複判定**して二重登録しない（コメントだけ違う同じ鍵を弾く）。

#### 通信量の上限（`service/NetGuard`、0.8.388 / 数える対象は 0.8.389）

**何ができるか**: **今期にスマホ全体が使った**モバイル通信量が決めた量に達したら、**z2term 自身の通信を止める**。使いすぎに気付くのはたいてい**回線が絞られてから**で、z2term は SSH やダウンロードで黙って通信し続けられる。

**どこまで止まるか（この機能で一番の判断）**:
- ⚠ **止まるのは z2term の通信だけ**。ほかのアプリのモバイル通信は止まらない。root なしで端末全体を止める手段は **VPN を張ってパケットを捨てる**ことしかなく、それは「ターミナルアプリが端末の VPN 枠を常時占有する」（＝ほかの VPN と併用できない）という別の重さを持ち込む。**利用者の判断で「z2term の通信だけ止める」を選んだ**。
- 止める対象は **SSH / SFTP / 常駐トンネルの新しい接続**（`SshSessionFactory.create` の 1 か所で見る。3 経路ともここを通るので入口を増やさずに済む）、**すでに繋がっている SSH**（見張りが切る）、**OS イメージ・GUI パッケージのダウンロード**、**アプリ更新の APK ダウンロード**。
- ⚠ **アプリ更新の「確認」（数 KB）は通す**。上限に達している間も新しい版が出ていることは知れた方がいい。止めるのは落とすところ（数十 MB）だけ。
- ⚠ **端末（Linux）の中から出ていく通信（`apk` / `curl` / `git` など）は止められない**。アプリが自分のプロセスの通信だけを選んで止める手段を Android は持たない。**数には入る**ので上限に達したことは分かる。この一点は画面にも書く（書かないと「遮断されない＝壊れている」と読まれる）。

**止めないもの**:
- ⚠ **家の中への接続は止めない**（利用者の要望）。`192.168.*` / `10.*` / `172.16-31.*` / `127.*` / `169.254.*` / `fc00::/7` / `fe80::/10` と、`localhost` ・**ドットを含まない一語の名前**・`.local` `.lan` `.home` `.internal`。**モバイル通信を 1 バイトも使わない相手を止める理由がない**。名前で書かれた相手は引いてみて、私設アドレスならローカル扱いにする。⚠ 名前解決は**止めると決まってから**行う（止めていない間に毎回引くと接続が遅くなるだけ）。
- **Wi-Fi につながっている間は止めない**（既定）。数えるのもモバイルぶんだけ。OFF にすると Wi-Fi ぶんも合算して数え、つながり方に関係なく止める。⚠ 超えていても Wi-Fi なので止めていない、という状態は画面に出す（黙って通ると設定が効いていないように見える）。

**数えるのは端末全体（0.8.389 で直した）**: 最初は自分の UID ぶんだけを数えていたが、⚠ **利用者が知りたいのは「今月あと何 GB 使えるか」であって、そのうち z2term が何バイト使ったかではない**（利用者の指摘: 「アンドロイド全体の通信で計算されていないとユーザーは使いにくい」）。**自分のぶんだけで止めても契約の上限とは何の関係もない**。`querySummaryForDevice` で回線ごとの合計を読む形に変えた。

- ⚠ **端末全体を読むには「使用状況へのアクセス」の許可が要る**（自分の UID を聞くだけなら要らなかった）。普通の権限のように求めるダイアログを出せないもので、`AppOpsManager` で状態を見て（`hasUsageAccess`）、無ければ設定画面へ案内する（`openUsageAccessSettings`）。manifest に `PACKAGE_USAGE_STATS` を宣言していないと**その一覧にすら出ない**ので宣言も要る（lint は `ProtectedPermissions` を抑止）。
- ⚠ **許可はシステム設定でしか変わらない**ので、設定画面へ戻ってきたとき（`ON_RESUME`）に見直す（電池最適化の除外と同じ扱い）。
- ⚠ **許可が無い / 読めないときは止めない**（`measurable = false`）— 測れないことを理由に通信を止めると、直しようのない締め出しになる。「許可が無い」と「許可はあるが読めない」は**画面で書き分ける**（前者には設定を開くボタンを添える）。黙って効かないのが最悪。
- ⚠ **SIM を 2 枚挿していると合算になる**。回線の識別子は API 29 以降アプリから読めないため、選り分けようがない。
- ⚠ OFF のときは測りに行かない（問い合わせは軽くない）。

**上限はつまみと欄の両方で決める（0.8.389）**: つまみは 100MB〜50GB を**等間隔でない段**で刻む（等間隔にすると、よく使う 1〜5GB のあたりが数ミリ幅になって合わせられない）。⚠ **つまみだけでは契約の数字に合わせられない**（4.5GB や 100GB はどの段にも無い。利用者の指摘: 「スライダーだけだと調整ができない」）ので、**MB を打てる欄を添える**（1MB〜1TB）。⚠ 打った値がどの段にも無いとき、つまみは近い段を指すが、**表示する数字は打ったとおり**にする（つまみの位置ではなく設定値を出す）。⚠ 欄は**空のままも許す** — 消してから打ち直す間に勝手な値が入ると、打ち直せない欄になる。

**期間**: 締め日（1-28）の 0:00 から数える。29-31 を許すと**その日が無い月だけ区切りが飛ぶ**。

**見張り**: 15 分ごとの不正確な繰り返しアラーム（`setInexactRepeating`。使いすぎを止めるのに秒は要らない）。超えていたら外向きの SSH を切り、**同じ期間に 1 回だけ**通知する（15 分ごとに通知が積み上がると、肝心の 1 通目ごと読まれなくなる）。⚠ 予約は端末再起動で消えるので、`BootReceiver` とアプリ起動の 2 か所から置き直す。

**断り方は例外**（`NetGuard.ensureAllowed`）。黙って何もしない形にすると「壊れている」としか見えないので、**理由の文がそのまま画面に出る**ようにして、設定を開いて上限を上げるところまで自分でたどり着けるようにする。切断のときも**先にその画面へ理由を書いてから**畳む。

`NetGuardTest` が「家の中かどうか」（`172.15`/`172.32` を外部と見分けること・一語の名前）、「いつから数え直すか」、「Wi-Fi のときは止めないこと」、「測れないときは止めないこと」を固定する。

#### 持ち出し / 引き継ぎ（`backup/BackupManager`、0.8.239）

**何ができるか**: 設定・SSH 接続先・スニペット・`z2-when` ルール・マクロ・**自作テーマ・タイルの割り当て・アイコンのドット絵・ユーザー辞書・IME の学習履歴**（0.8.380）を **1 つの zip** に書き出し、別の端末で戻す。機種変・初期化・入れ直しで**全部消える**のが今までで、持ち出せると分かって初めて腰を据えて積み上げられる。

**含めるものと含めないもの**: rootfs（数百 MB）・ログ・`events.jsonl` は**含めない**。「入れ直せば戻るもの」と「二度と戻らないもの」を分けるのがこの機能の設計そのもので、ここを混ぜると毎回数百 MB のファイルができて誰も使わなくなる。

**設定はフィールドを書き写さない**（`settings/PrefsPortable`）: DataStore の key-value をそのまま JSON にする。設定は 60 以上あり、1 つずつ書き写す方式にすると**項目を足すたびに持ち出しから漏れ**、しかも漏れたことは機種変のときにしか分からない。型は 1 文字のタグ（`b`/`i`/`l`/`f`/`s`/`S`）で保つ。

**置き場がばらばらなものを 5 つ足した（0.8.380）**。⚠ **設定を運ぶだけでは 1 つも付いてこなかった** — 自作テーマは `AppSettings` とは**別の DataStore**、タイルの割り当てとアイコンは（アプリのプロセスが生きていない状態で読まれるため）**SharedPreferences**、ユーザー辞書と IME の学習履歴は `filesDir` のファイル。どれも手で積み上げるもので、入れ直しでは戻らない。
- **SharedPreferences 版の丸ごと変換を作る**（`settings/SharedPrefsPortable`）。`PrefsPortable` と同じ理由でキーを書き写さない。
- **戻したら「いま出ているもの」へ効かせるところまでが取り込み**: タイルは一覧の同期（`TileStore.syncEnabledTiles`）まで通さないと**中身はあるのに編集画面に出てこない枠**ができ、アイコンは使い回しの Bitmap を捨てないと**戻したのに前の絵が出続ける**。辞書と学習履歴は読み込み済みの表が手前に残るので `reload` が要る（`UserDictStore.reload` / `ImeHistoryStore.reload`）。⚠ ここを省くと「戻したはずのものが次の起動まで出てこない」になり、**壊れているのか自分の操作ミスなのか利用者には区別できない**。
- ⚠ **ホーム画面ウィジェットの割り当ては含めない**。保存のキーが `appWidgetId`（端末が置いたときに配る番号）なので、移した先では**別のウィジェットを指すか、どれも指さない**。運ぶには「置き直したウィジェットへ順に当てる」仕組みが要り、それは持ち出しとは別の設計になる。

**秘密の扱い（この機能で一番の判断）**:
- SSH のパスワードと秘密鍵は Android Keystore で暗号化されているが、**Keystore の鍵は端末から出せない**ので、暗号化済みのまま運んでも移した先で復号できない。持ち出すには一度平文に戻すしかない。
- そこで **既定では秘密を含めない**（接続先の名前・ホスト・ポートだけ運ぶ）。含めるときは**合言葉が必須**で、**合言葉なしで秘密を書き出す経路は画面にも API にも作らない**（`BackupManager.export` が `require` で弾く）。1 つでも残せば、そこから事故る。
- 暗号は `backup/BackupCrypt`: PBKDF2WithHmacSHA256（210,000 回）で 256bit 鍵を導き、AES-GCM で包む。合言葉違いは GCM の認証が落ちるので、**「合言葉が違う」と「壊れている」を区別せず**同じく弾ける。`BackupCryptTest` が「素通しでないこと」「合言葉違いが通らないこと」「毎回別の暗号文になること」を固定する。
- **format 2 は manifest 以外を全部暗号化する（0.8.449）**。0.8.448 までは `ssh.enc` だけを包み、`settings.json`・スニペット・マクロ・ルール・IME 学習履歴などは ZIP 内で平文だった。しかし利用者が書いたトークンや合言葉、入力履歴はどこにでも入り得るため、「秘密のファイル」を列挙する判断自体を廃止した。外側の ZIP は、合言葉なしで件数を見せるための `manifest.json` と、内側の ZIP 全体を AES-GCM で包んだ `payload.enc` の 2 件だけ。format 1 は取り込み互換を残し、旧 `ssh.enc` も読める。
- **暗号化するかは「合言葉があるか」だけで決める（0.8.452）**。0.8.451 までは `includeSecrets` が暗号化のスイッチを兼ねていたため、秘密を含めない書き出し（＝定期バックアップの全部）は合言葉を持てず、必ず平文 format 1 になっていた。**秘密を含めないことと中身を読まれないことは別**で、スニペット・マクロ・自動化ルール・キーボードの学習には利用者が書いたトークンがそのまま入る。`Options.passphrase` が空でなければ format 2 で書く（`includeSecrets` は「SSH の秘密も入れるか」だけを意味し、そちらは従来どおり合言葉必須）。manifest の `hasSecrets` と `encrypted` も別々に出す。
- **復号・内側 ZIP の展開を、設定を 1 件でも適用する前に完了させる**。旧実装は設定等を適用してから `ssh.enc` を復号していたため、合言葉違いで「設定だけ戻り、SSH は戻らない」という部分適用になり得た。format 1 の旧バックアップも SSH の復号を先へ移し、間違い・破損時は何も変えない。`BackupArchiveTest` が全データの平文不在、全項目の往復、誤った合言葉の拒否、format 1 互換を固定する。

**取り込みは上書きではなく追加・更新**: 同じ id のものは置き換え、バックアップに無いものはそのまま残す。古いバックアップを戻したときに、新しく作ったものが消えないようにするため。適用前に `peek` で件数を出し、**中身を見せてから**押させる。zip の名前に `/` を含むものは捨てる（他人から受け取ったファイルを開く口なので、書き出し先がディレクトリの外へ出ないようにする）。

**保存先はユーザーが選ぶ**（SAF の `CreateDocument`）。アプリが勝手にどこかへ置かない。

**決まった日時に自動で積む（`backup/AutoBackup`、0.8.386）**: 手で押したときにしか残らないのが持ち出しの弱点だった — 機種変も初期化も**押し忘れた側**で起きるので、「作ってあるはず」が一番危ない。⚙設定 › メンテナンスで**間隔（毎日 / 毎週 / 毎月）・時刻・保存フォルダ・残す世代数**を決めると、その時刻に 1 本書き出して古いものから消す。

- ⚠ **自動で書き出すものに秘密は含めない**。自動化すると**合言葉を端末に置く**ことになるので、含めてしまうと「合言葉なしで秘密を出す経路は作らない」という上の約束が、端末ごと取られたときに意味を失う。秘密ごと運ぶときは手で 1 本作る。
- **それでも中身は暗号化できる（0.8.452）**。⚙設定に合言葉の欄を置き、決めてあれば秘密を含めないまま format 2 で書く。**「SSH の秘密が入っていないから平文でよい」は成り立たない** — 定期バックアップに入るスニペット・マクロ・自動化ルール・キーボードの学習は、利用者が書いたものがそのまま載る。空のままなら従来どおり平文 format 1 で積むので、**決めなければ挙動は変わらない**。保存済みの合言葉は既定で伏せ字にし、「表示」を押した間だけ見せる（0.8.457）。手動の書き出し / 取り込み欄も同じ扱いにする。
- ⚠ **合言葉は `KeystoreCrypt` で包んで DataStore へ置き、持ち出しには載せない**（`AppSettings.EXPORT_EXCLUDE`）。載せると**そのファイルを開ける合言葉が、そのファイルの中に入る**（平文 format 1 のバックアップならそのまま読める）。取り込みでも書き換えない — 別の端末の合言葉で上書きすると、**この端末では開けないファイルを積み始める**。⚠ その代わり、設定を戻しても合言葉は移らないので、新しい端末では入れ直す。
- ⚠ **世代整理の対象は自動で作ったものだけ**。名前の頭を `z2term-auto-` にして、手で作った `z2term-backup-*` と分ける（同じフォルダを選んでも、手で作った 1 本は消えない）。並べ替えは**名前の辞書順**（`YYYYMMDD-HHMM` を含むので時系列と一致する）。保存先によっては更新日時が取れない・揃わないため、日時では並べない。
- **書き終えてから世代整理をする**。逆にすると、書き出しに失敗した日に古いものだけ消えて**手元が減る**。
- **予約は 1 本だけ**で、発火のたびに次を置く（`ExactAlarm`: 置けるなら正確・駄目なら Doze 貫通の不正確）。⚠ AlarmManager の予約は端末再起動で消え、**消えたことは「バックアップが増えない」という形でしか表に出ない**ので、`BootReceiver` とアプリ起動時の 2 か所から貼り直す（何度呼んでも同じ状態になる）。失敗した回でも次は置く（1 回書けなかったことと、以後ずっと回らないことは別）。
- **失敗したときだけ通知する**。毎日成功の通知が出ると読まなくなり、**失敗したその日も読み飛ばす**。うまくいった日は設定画面の「最後の書き出し」にだけ残す。
- ⚠ フォルダは SAF の tree URI を `takePersistableUriPermission` で保つ。取り忘れると**アプリを再起動した時点で書けなくなり、その日の夜中に静かに失敗する**。書けなくなっていることは実行前に見て、`err:noaccess`（＝選び直しの案内）として出す。
- 毎月の日付は **1-28 に丸める**。29-31 を許すと**その日が無い月だけ飛ぶ**。
- `AutoBackupScheduleTest` が「次はいつか」（毎日 / 毎週 / 毎月・時刻ちょうどは次回へ送る）と「どれを消すか」（手で作ったものを消さない・`keep=0` でも 1 本残す）を固定する。

#### スニペットのグループ（`snippets/SnippetGroup`、0.8.387）

**何ができるか**: 📜 のスニペットタブの上に**グループの帯**（`[すべて] [日常] [git] [+ グループ]`）を出し、押したグループの中だけを並べる。**なぜ要るか**: スニペットは増えるほど下へ伸び、**よく使うものほど下に埋まる**（利用者の指摘: 「量が増えると下の方に行ってしまい選択するのが難しくなってくる」）。

**ページではなく棚にする**。増えた順に機械的に区切るページだと、**どこに何があるかが増減のたびに変わる**。自分で名前を付けた棚なら、中身が増えても場所は動かない（利用者の言葉で言えば「日常系のスニペットとかgit管理系とか」）。

- **スニペットは名前ではなく id を持つ**（`Snippet.groupId`。空 = 未分類 = 「すべて」にだけ出る）。名前で持つと、グループ名を直すたびに中身を全部書き換えることになり、書き換えの途中で落ちると**どこにも出てこないスニペット**が残る。
- ⚠ **グループを消しても中のスニペットは消さない**。未分類へ戻して「すべて」に出す。棚を片付けたつもりで中身ごと失うのが一番困るので、削除の欄にもその一文を書く（書いていないと怖くて押せず、棚が増え続ける）。
- ⚠ **絞り込み中の並べ替えを `replaceAll` に渡さない**（`SnippetStore.replaceVisible` / `reorderWithin`）。渡すと**出ていないグループがまるごと消える**。見えている行が占めている位置にだけ新しい並びを流し込むので、**絞って並べ替えても、出ていないものの前後関係は変わらない**。`SnippetGroupTest` がこれを固定する。
- **名前の変更と削除の入口は、開いているグループのチップの `✎`**。長押しのような隠し操作にはしない（見えないものは無いのと同じ）。
- **新規は開いているグループへ入る**。開いてから「+ 新規」を押した人にとってはそれが自然で、未分類へ落ちると毎回移し替える羽目になる。編集で別のグループへ移したときは**移した先を開く** — 保存した途端に一覧から消えると、消えたのか移ったのか分からない。
- **グループを 1 つも作っていない人には、編集画面のグループ欄を出さない**。選べる先が「未分類」しかない欄は、置き場所を選べるように見えて何も決められない。
- **持ち出しには別のエントリで入れる**（`snippet_groups.json`）。⚠ スニペット本体の配列に混ぜると 0.8.386 までの版が読めなくなる。古いバックアップにこのファイルは無く、その場合は全部が未分類のまま戻る。⚠ 取り込みは**ファイルがあるときだけ**行う（空配列で上書きすると、いまの棚が消えて中身だけが未分類に散らばる）。

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

#### 踏み台（`channel/JumpProxy`、0.8.494）

**何ができるか**: 接続先 1 件につき**踏み台を何段でも**挟める（`ssh -J a,b,c` と同じ）。手前から順に繋ぎ、最後の段の中から本来の接続先へ出る。**シェル・SFTP・その接続先にぶら下がる FTP / SMB / WebDAV / VNC / RDP・常駐トンネルのすべてが同じ経路を通る** — 入口が `SshSessionFactory` の 1 つしかないため、対応を足す場所も 1 つで済んだ。

**⭐ `-L` で中継しない**: 素直に思いつくのは「手前のセッションに `-L` を張って `127.0.0.1:<空きポート>` へ次の SSH を繋ぐ」形だが、これは 2 つ同時に踏む。

1. ⛔ **known_hosts が `127.0.0.1` で記録される。** この画面はホスト鍵を必ず確認させる作りなので、**別の踏み台の先も同じ名前**になって鍵のすり替わりを見分けられなくなる。
2. 端末上の**他のアプリからもその待ち受けポートへ入れる**。

⇒ JSch の `Proxy` を実装し（`JumpProxy`）、手前のセッションに `direct-tcpip` を 1 本開いてその中で次の SSH を話す。OpenSSH の `ProxyJump` と同じ形で、**端末側に待ち受けポートを開かない**。`Session` のホスト名は本来の宛先のまま残るので、known_hosts も鍵確認ダイアログも段ごとに正しい相手を指す。

**⚠ 鍵は段ごとに別の `JSch` へ登録する**: JSch の identity は**インスタンス全体で共有され、どのセッションでも順に試される**。1 つにまとめると踏み台の鍵を本来の接続先へ差し出し、`Too many authentication failures` で切られる。`hostKeyRepository` だけは共有する（known_hosts は 1 本）。

**⚠ `getSocket()` は null**: JSch は生のソケットが無くても動く（`connect` の `setSoTimeout` も `setTimeout` も null を見て飛ばす。0.2.26 のバイトコードで確認）。ただし**ソケットが無い段は読み取りタイムアウトが効かない**＝ keepalive が鳴らない。⇒ 常駐トンネルの生存確認は `SshLink.enableKeepAlive` が**全段に入れ**、実際に効くソケットを持つ 1 段目が経路の死を検知する（1 段目が切れれば奥も道連れに落ちるので、再接続はそれで回る）。

**⭐ 参照ではなく実体を持つ**: 踏み台 1 段（`SshHop`）は自分の宛先と認証をそのまま持つ。「登録済みの接続先を指す」形にしなかったのは、**指した先を消した瞬間に壊れる**のと、A が B を経由し B が A を経由する**輪を作れてしまう**ため。代わりに編集画面の「取り込み」で登録済みの内容を**コピー**して埋める（`SshProfilesSheet.toJumpHost`）。⚠ 持ち出しで「秘密を含めない」を選んだときは、**踏み台の秘密も落とす**（`SshProfileStore.exportRaw`。ここを忘れると踏み台のパスワードだけがファイルに残る）。

**⚠ 通信量の上限は 1 段目で判定する**: 端末が実際に電波を使って繋ぐ相手は 1 段目であり、本来の接続先は踏み台の中＝相手側の回線で解決される。`NetGuard.ensureAllowed` に渡すのは `jumpHosts.first()`（踏み台が無ければ接続先そのもの）。

**⚠ 常駐トンネルは全段の known_hosts を見る**: 途中の 1 段でも未承認だと、そこで必ず鍵確認ダイアログ待ちになって固まる。`TunnelManager.isKnownHost` は踏み台と接続先の**全部**が登録済みのときだけ張る。

**ポート転送の向きの取りこぼしを直した（0.8.494）**: `SshChannel` は `PortForward.reverse` を見ずに**常に `-L`** を張っていた（`-R` を書いた接続先を SSH タブから開くと向きが黙って逆になる）。常駐トンネルだけが正しく分岐していたため、張り方が 2 か所にあること自体が原因。⇒ `channel/PortForwarding` に 1 本化して両方が同じ道を通るようにした。

#### 常駐トンネル（`service/TunnelManager`、0.8.221・A2）

**何ができるか**: **SSH タブを閉じてもポート転送を生かし続ける**。あわせて `-R`（リモート → 端末）を追加した。

**なぜ要るか**: 現状の SSH タブ（`channel/SshChannel`）は「接続 → 転送を張る → 画面用の shell を開く」の順で、**転送と画面が 1 本のセッションにぶら下がっている**。だからタブを閉じると転送も消える。`-R` は**常駐しないと意味を成さない**（入りたい時に端末側でタブを開いている必要があるなら、そもそも外から入る必要が無い）。

**常駐を新規に作らない**: 画面を持たない JSch セッションを `TunnelManager` が持ち、`ServerDaemonService` の常駐枠（FGS 通知 / WakeLock / WifiLock / `BootReceiver` 自動起動）に**相乗り**する。常駐サーバーが 0 本でもトンネルだけで常駐してよい（`BootReceiver` もトンネルの有無を見る）。

**守っていること**（§6 の 3 条件）:
1. **明示 opt-in**: `SshProfile.residentTunnel` が true のものだけ。UI では転送を 1 つ以上作ったときにだけトグルが出る。`-R` を含むときは「接続先からこの端末へ入れる状態になる」と文言を変える。
2. **known_hosts 登録済みのホストだけ**: 常駐中はホスト鍵の確認ダイアログを出せないので、未知のホストは**張らずに理由を残す**（黙って信用しない）。先に SSH タブで 1 度繋いで承認してもらう。
3. **指数バックオフで再接続**: 5 秒から倍々にして 5 分で頭打ち（`TunnelManager.backoffMs`・`TunnelManagerTest` が境界とオーバーフローを押さえる）。回線が落ちている間に総当たりで撃たない。

**⭐ keepalive を流す（0.8.367）— 「スマホが LAN から消える」の根治**: 常駐トンネルは繋いだあと何も流さない状態になりうる。SSH は黙っていても切れないので一見それで良いのだが、**端末が黙ると無線チップが省電力へ入り、同じ LAN の他機から見えなくなる**（ARP はブロードキャストなので、省電力に入った子機が取りこぼす）。CPU は起きていて FGS も WakeLock も効いているのに、電波だけが消える。実測（画面消灯・充電中・Wi-Fi・常駐サーバー稼働中）:

| 端末側の送信 | 期間 | 他機から届かなかった率 |
|---|---|---|
| 無し（黙っている） | 9 分 | **37%** |
| 10 秒ごとに外へ 1 本 | 19 分 | **1%** |

そこで `TunnelManager.KEEPALIVE_MS` = **10 秒**（省電力モードのときは `KEEPALIVE_LOW_POWER_MS` = 60 秒。電池優先の意思表示なので到達性は諦める）。`serverAliveCountMax` は 3 なので、本当に切れていれば 30 秒で気付いてバックオフへ回る。**繋ぐ前に入れること**（JSch は接続の最後にこの値をソケットの読み取りタイムアウトへ写し、時間切れのたびに keepalive を 1 本送る）。

⛔ **WifiLock では直せない**（0.8.367 に裏取り）。`WIFI_MODE_FULL_HIGH_PERF` は非機能化して `WIFI_MODE_FULL_LOW_LATENCY` に読み替えられ、そちらは「AP に接続中」「**画面が点いている**」「**アプリが前面**」が揃ったときだけ有効 ＝ 常駐サービスの用途では常に無効。`WIFI_MODE_FULL_LOW_LATENCY` へ変えても直らない。**端末側から定期的に喋ることだけが効く。**

**張れなかった転送は畳まずに張り直す（0.8.367）**: `-R` は**繋ぎ直した直後の 1 回が失敗しうる**。端末側が落ちても接続先の sshd はしばらく待ち受けポートを握ったままなので、`setPortForwardingR` が「そのポートは使用中」で弾かれる。ここで諦めると**繋がっているのに転送だけ死んだ**状態が固定するため、セッションは畳まずに 30 秒ごとに張り直しへ回す（畳むと生きている他の転送まで巻き添えになる）。張れていない転送は一覧で `✗` が付く（`TunnelManager.detailOf`）。

**`-R` の向き**: `PortForward.reverse` で切り替える。`setPortForwardingR(bindAddress, remotePort, remoteHost, localPort)` = リモートの `bindAddress:remotePort` で待ち受け、端末から見た `remoteHost:localPort` へ繋ぐ。フィールド名は `-L` 時代のままなので、**意味が向きで入れ替わる**点に注意（`PortForward` の KDoc と `describe()` が正本）。

#### ライブ tail ウィジェット（`widget/TailWidgetProvider`、0.8.217・D2）

**何ができるか**: 選んだファイルの**末尾 N 行 / 先頭 N 行**をホーム画面に出す。「ホーム画面で `tail` / `head`」。マクロや `z2-when` が書いたログ・`events.jsonl`・セッション記録を、端末を開かずに眺めるための窓。§10-2 の D2。

**構成**:
- `widget/TailWidgetProvider`… 描画と ⟳ / ⚙ のタップ受け。本文タップでアプリを開く。
- `widget/TailConfigActivity`（`APPWIDGET_CONFIGURE`）… 見るファイルを決める（パス入力 / フォルダを辿る）。
- `widget/TailStore`… ウィジェットごとの「見るファイル（`~` からの相対パス）」と「どちら側を見るか」（`Mode.TAIL` / `Mode.HEAD`）を SharedPreferences に保存し、パス解決とディレクトリ走査も持つ。**行数は保存しない**。
- `widget/TailReader`… 末尾 N 行 / 先頭 N 行の切り出し。判断部分は Android 非依存で `TailReaderTest`（17 ケース）。

**全部読まない**: セッションログも常駐サーバーのログも**ローテーションしない**方針（青天井）なので、`RandomAccessFile` で**片側から** `MAX_TAIL_BYTES`（16KB）だけ切り出し、その中で行に割る。切り出した窓の**外側に続きがある側の 1 行**はマルチバイト文字の途中で切れているので捨てる（末尾側なら `truncatedHead` で先頭行、先頭側なら `truncatedTail` で最終行。ただし 1 行しか無いときは捨てると何も出せないので残す）。

**末尾 / 先頭を選べる（0.8.240）**: ログは末尾を追うものだが、**書き終わったファイルは先頭が知りたい**（レポート・設定ファイル・`z2doctor` の出力など、大事なことが頭に書いてある種類）。末尾しか出せないと、そういうファイルは「最後の数行」＝ほぼ意味のない末尾しか見えない。設定画面で `末尾 (tail)` / `先頭 (head)` を選び、`TailStore.Mode` として保存する（未設定は `TAIL`＝従来どおり）。どちら側を見ているかは本文からは判別できないので、**フッターに必ず `tail` / `head` と出す**（コマンド名そのものなので訳さない）。

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

**文字の大きさを選べる（0.8.255・D1 / D2 とも）**: ウィジェットの ⚙ から選ぶ（D2 は 8〜20sp・既定 10 / D1 は 9〜20sp・既定 11 = それぞれ 0.8.254 までのレイアウト固定値）。`RemoteViews.setTextViewTextSize` で XML の値を上書きする。単位は **SP**（端末のフォントスケール設定にも従わせるため。DIP にしない）。
- **スライダーではなくプリセットを並べる**。1sp 刻みで迷わせる価値が無く、ウィジェットの文字は数段階あれば足りる。既定値には「既定」と出して、戻したくなったときに迷わせない。
- **D1 は倍率ではなく差分で動かす**（`WidgetStore.scaled`）。行ごとに元のサイズが違う（見出し 13 / 状態・ボタン 11 / フッター 10）ので、倍率だと小さい行だけ潰れて釣り合いが崩れる。
- ⚠ **D2 は行数の見積もりにも同じ値を使う**（`linesFor` の `TextPaint.textSize`）。本文とズレると末尾が切れる / 隙間が空く。大きくすれば入る行数は減る — 行数を別設定にしないのは、「文字の大きさ」と「入る行数」は同じことを 2 通りで言っているだけで、両方いじれると矛盾した組み合わせを作れてしまうため。
- ⚠ **通知の文字サイズはアプリから変えられない**（Android に API が無い。OS の「表示 → フォントサイズ」に従う）。自前 `RemoteViews` で組めば可能だが、標準の見た目・展開・アクションの作法から外れ、Android 12+ ではどのみちシステム側の装飾が被さるので採らない。

**`configuration_optional` を付けない**（D1 とはここが違う）: **どのファイルを見るかは推測しようがない**ので、置いた直後に必ず設定画面を出す。それでも未設定の状態は起こり得る（設定を途中でやめた等）ので、その場合は「⚙ を押してファイルを選んでください」と本文に出す。

#### ツールバーの並び順の正規化（`ToolbarButtons.mergeOrder` / `.normalizeOrder`、0.8.213）

**不具合**: 一部のボタンがツールバーに **2 個ずつ描かれる**ことがあった（言語切替などで画面を作り直したときに表面化。全部ではなく一部だけ・再現が安定しない）。

**原因**: 保存値 `toolbarOrder` に**同じ id が 2 か所入った状態**が書かれ得た。並べ替え確定時の書き込みは「保存値の全 id の並び」の**表示スロットだけを今の表示順で埋め直す**方式だが、設定で「隠す/出す」を切り替えた直後は、`hidden` の変更が表示順（`order`）へ反映される前の**古い並び**（隠したはずの id を含む）が渡ることがある。それを表示スロットへそのまま流し込むと、隠した id が可視スロットにも書かれて二重になり、別の id が 1 つ落ちる。保存値は DataStore に残るため、**一度壊れると再起動しても直らない**。読む側の `mergeToolbarOrder` も重複をそのまま通していたので、`key(id)` が重複して並べ替えの状態まで壊れていた。

**修正**: 判断部分を `ui/terminal/ToolbarButtons` へ集約し、Android 非依存の純ロジックとして `ToolbarOrderTest` で押さえた。
- `mergeOrder(saved, present)`… 読む側。`saved` の重複を畳んでから present とマージする（壊れた保存値でも表示は必ず正しくなる）。
- `normalizeOrder(savedCsv, allIds, hiddenIds, shownOrder)`… 書く側。埋め込む表示順から隠し済み・未知の id を落とし、埋め終わりに**先勝ちで畳んで欠けた id を末尾に補う**。戻り値は `allIds` がちょうど 1 回ずつ現れることを保証する。
- 加えて、読み込み時に保存値の重複を検出したら**その場で正規化して書き戻す**（既に壊れている端末を自己修復させる。書き戻しで `savedOrder` が変わり同じ効果が 1 回だけ回って収束する）。

**0.8.509 の直し（タブを切り替えるたびに並びが変わる / 一瞬並び替わる）**:
- ⭐ **保存値から「今いるタブに無いボタン」を落とさない。** `toolbarOrder` は端末タブと GUI タブで **1 本を共有**しているのに、書き込みの基準を `mergeOrder(saved, allIds)`（= 今のタブに在るボタンだけ）にしていたため、端末で 1 回並べ替えるだけで ☰ / 🖱 / 📎 の位置が保存値から消えていた（GUI で並べ替えれば 🔍 / ⚪ が消える）。消えた側は次に開いたとき既定順で末尾に補われるので、**タブを行き来するたびにアイコンの並びが変わる**という形で出る。基準を「保存値 + まだ保存値に無い id」に変え、埋め直すのは `allIds` に在る位置だけにした（`ToolbarOrderTest.otherTabButtonsSurviveAReorder` / `.reorderingOnOneTabDoesNotShuffleTheOther` が固定する）。
- ⭐ **並びはコンポジションのその場で決める**（`ReorderableToolbar`）。空の `mutableStateListOf` を `LaunchedEffect` で埋めていたので、タブを切り替えた**最初の 1 フレームはボタンが 0 個**で、次のフレームで全部並んでいた。CLI → GUI の切り替えで毎回「アイコンが一瞬並び替わる」と見えていたのはこれ。
- ⭐ **掴む・入れ替える・保存が返るまで自分の並びを保つ、は `ReorderList` の `ReorderState` に一本化した。** 縦の一覧（常駐サーバー / 自動化）と同じ実装を使えるよう、`vertical = false`（幅と `translationX` で同じ判定）と、ハンドルを置けないものを長押しで掴む `reorderLongPressHandle` を足した。⛔ **同じ挙動をツールバー側にもう一度書かないこと** — 「離した直後に元へ戻ってからまた入れ替わる」の類は、片方だけ直すと必ず再発する。
- ⭐ **設定 › 表示 › ツールバーでも並べ替えられる**（要望）。チップを長押ししてから左右にドラッグする（`ToolbarVisibilityRow`）。ここには端末タブと GUI タブ**両方のボタン**が出るので、どちらのタブを開いていなくても既定の並びを決められる。⚙ は右端固定なので、ここでも列の最後に置いたまま動かさない。

**0.8.510 の直し（0.8.509 では消えていなかった「一瞬並び替わる」）**:
- ⭐⭐ **真因は GUI タブの設定購読の初期値だった。** GUI タブは `TerminalSession` を持たないので `AppSettings.flow`（DataStore の Flow）を直接購読しているが、`collectAsState(initial = AppSettings.Snapshot())` と**既定値**から始めていた。DataStore の最初の emit までは数フレームあるので、**GUI タブへ切り替えた直後だけ `toolbarOrder = ""`** で描かれ、実値が届いた瞬間に保存した並びへ入れ替わる。0.8.509 で直した 2 点（保存値から他タブのボタンを落とさない・並びをその場で決める）はどちらも**正しい保存値が来ている前提**なので、これは残っていた。`AppSettings.lastKnown`（最後に読めた `Snapshot` を companion に控える）を初期値にして、1 フレーム目から実値で描く。⚠ **書き込みには使わない** — 正本は DataStore で、これはその写し。アプリ起動後 1 度も読めていない間だけ既定値になる（それは避けようがない）。
- ⚠ **同じ形の初期値ちらつきは、DataStore を直接購読している画面すべてに起こり得る。** `stateIn(Eagerly)` の初期値が既定 `Snapshot` であることに起因する罠（0.8.314 の distro 判定など）と根は同じ。**「まだ読めていない値」を既定値で埋めて描かない。**

**0.8.510 の直し（並べ替えが 1 個ずつしか動かない）**: → `ReorderList` の項を参照。

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

**実装**: 動くサンプル 10 本（イベント入門 / 電池アラート / 時刻トリガー / 通知内 OTP 自動コピー / SMS の OTP 自動コピー / 電話帳に無い番号からの着信 / 通知リマインド / フィード購読 / 集めた記事を開く / QR にして渡す）を rootfs の `/usr/local/share/z2term/macros/` に配置し、`z2-macro install <名前|all>` で `~/.z2term/macros/` へ展開する。

**端末で育った `rss.sh` の拡張を同梱版へ取り込む（0.8.334）**: 0.8.332 で `z2-macro list` に
状態を出したところ、実機の `rss.sh` が「差分あり」で、**端末側の方が機能が多い**ことが分かった
（リポジトリには一度も入っていない拡張。`git log -S IMPORTANT` が空）。同梱版へ引き上げて、
端末を初期化しても失われない状態にする。

- **`important.txt`（見逃したくないフィード / 語）**: まとめ通知の本文には 3 件しか載らないので、
  流量の多いフィードが同時に更新されると**大事な 1 本が押し出される**。当たった記事は 1 件ずつ
  別の通知にする（通知 id はアプリ側で個別に振られるので、分ければ上書きも省略もされない）。
  ⚠ 個別通知は `HITMAX`（5 件）まで — 語の書き方を誤って全記事が当たったときに通知シェードを
  埋め尽くさないため。
- **通知の名前に URL を入れる（`-n "rss:<URL>"`）**: ボタンを押すと `notify_action` の `name` として
  そのまま返るので、通知が何枚出ていても**押した記事**が開く。`new.txt` の先頭を読むやり方では、
  通知が複数あるとき常に最新の 1 本しか開けなかった。
- ⚠ **「差分あり」は片方向ではない**ことの実例でもある。0.8.332 で最初 `要更新` と表示していたら、
  この拡張は `-f` で消えていた（そのため中立な語へ直した → 上記）。

**時刻トリガーを「置けるなら正確に」へ（0.8.333・`ExactAlarm`）**: 発火は `setAndAllowWhileIdle`
（Doze 貫通・不正確）で置いており、**画面を消して放置していると数分〜15 分遅れる**（Doze 中は発火の
機会が概ね 9〜15 分に 1 回）。利用者から「省電力だと大幅な時差が出る？」と聞かれて調べた。

- ⚠ **電池の最適化を除外しているアプリは、`SCHEDULE_EXACT_ALARM` を宣言しなくても正確なアラームを
  許される**（`AlarmManagerService` の allow-list 免除。実機 Android 16 の `dumpsys alarm` に
  `exactAllowReason=allow-listed` として現れる）。z2term は常駐サーバーのために最適化除外を
  お願いしているので、**manifest に権限を 1 つも足さずに**正確側へ寄せられる。
- 置くたびに `canScheduleExactAlarms()` で聞き、駄目なら不正確側へ落ちる。⚠ **調べた結果を覚えない** —
  許可は設定からいつでも変わる。聞いた直後に剥がされた場合の `SecurityException` も受け止めて
  不正確側で置き直す（**予定そのものを落とさない**のが最優先。置けないまま黙って消えるのが最悪）。
- ⚠ **3 系統をまとめて直す**。時刻で動くものは `z2-alarm`（[`AlarmScheduler`]）・`z2-when time:*`
  （[`WhenManager`]）・`z2-screen keepon` の期限（[`ScreenTimeout`]）の 3 つあり、それぞれが個別に
  `setAndAllowWhileIdle` を呼んでいた。`z2-alarm` だけ直しても**繰り返しのリマインドは `z2-when`
  を通る**ので遅れたままになる。置き方を `ExactAlarm` 1 か所へ寄せた。
- **端末から見えるようにする**。`z2-alarm list` の各件に `exact` を足した。⚠ 器は配列のまま
  （`z2-alarm list | jq '.[0].at'` のような手元の書き方を壊さない）。遅れた日に「そもそも正確側で
  動いていたのか」を確かめられないと、原因が Doze なのか予約ミスなのか切り分けられない。

**入れたコピーが古いことに気付けるようにする（0.8.332）**: `install` は**既存を上書きしない**
（ユーザーの書き換えを守るため。この判断は変えない）。その代償として、アプリを更新して同梱版が
直っても**端末のコピーは黙って古いまま**になる。実際 `remind.sh` は 2 週間ぶん古いコピーのまま使われ、
同梱版で直したはずの「結果の通知を `-h`（バナー）で出す」が効いていなかった — 利用者からは
「タイルを押してもポップアップしない」としか見えず、Android の通知設定を疑う形になった。

- **一覧の時点で分かるようにする**。`z2-macro list` に状態列（`未導入` / `同じ` / `差分あり`）を足す。
  比較は `cmp`、無ければ `cksum`。⚠ **どちらも無いときは「違う」と答える** — 「同じ」と嘘をつくと、
  直った同梱版があるのに一生気付けない（今回の壊れ方そのもの）。
- ⚠ **「要更新」と書かない**。分かるのは*違う*ことだけで、**どちらが新しいかは分からない**。
  実機の `rss.sh` は同梱版に無い機能（`important.txt` による個別通知＋それ前提の `z2-when` ルール）を
  持っており、「要更新」に釣られて `-f` を打つと消える。最初 `要更新` で実装して実データで踏んだので、
  中立な `差分あり` に直した。`install` の文言からも「同梱版が新しくなっています」を外してある。
- **`install` は「同じ」と「違う」を言い分ける**。一律「既にあります (上書きするには -f)」だと、
  上書きしてよい理由があるのかどうかが分からない。違うときは **`diff` を先に**、`install -f` を後に
  出す（順序も `Z2MacroScriptTest` で固定）。見ずに上書きさせないため。
- **`diff <名前>` を足す**（左＝端末のコピー / 右＝同梱版）。`-f` は自分の書き換えも消すので、
  「違う」と言われた人が**上書きしてよいか自分で確かめられる**手段が要る。
- `Z2MacroScriptTest` が、状態の 3 通り・`install` の言い分け・`diff` の左右・`--help` の終了コードを
  実際の `sh` で固定する。

**サンプルを常駐から `z2-when` へ寄せる（0.8.273）**: 0.8.272 まで、電池アラート・日報・OTP の 3 系統は
**ログを 2 秒ごとに見張る常駐スクリプト**で、マクロガイドも「自動クリアまで欲しいなら常駐サーバーに
登録」と案内していた。実機で電池の減りとして表に出たので測ったところ、**常駐サーバーのエンジンが
60 秒あたり CPU 3 秒（＝常時 5% 前後）**を使っていた。エンジン下では外部コマンドを 1 回起こすだけで
ptrace 越しに数千 syscall になるうえ、常駐中は WakeLock/WifiLock で Doze にも入れない。

- **同じことが `z2-when` のトリガーで書ける**（`battery:below=` / `time:daily=` / `notify:otp` / `sms:otp`）。
  特に OTP は**アプリ側が抽出まで済ませて `Z2_WHEN_OTP` に入れている**ので、本文を解析する awk ごと不要になる。
  4 本とも監視ループが消え、待っている間のコストがゼロになった。
- **`watch-basic` も 0.8.338 で `z2-when` へ寄せ、同梱サンプルから常駐が無くなった**（利用者の判断）。
  0.8.337 までは「`z2-when` に無いきっかけを自分で拾う」ための雛形として監視ループを残し、`POLL` を
  **2 秒 → 15 秒**へ広げていた。しかし**あの雛形が拾っていたのは `z2-when` で書けるきっかけ**（充電・
  イヤホン）で、見に行く間隔ぶん反応が遅れることが実機で問題になった。⚠ **教材としての差分読みは
  MACRO-GUIDE 5-0 に残してある** — 同梱サンプルに常駐版を戻さない（`diffSetup` / `diffLoop` も撤去済み）。
- `GeneratedScriptMarginTest.samples_doNotPoll` が「**どのサンプルも監視ループを持たない**」を、
  `watchBasicSample_reactsThroughWhen` が「入門サンプルは `Z2_WHEN_EVENT` で分岐する」を固定する。
  **常駐版へ戻す変更はテストで止まる。**
- 併せて `samples_areValidPosixShell` を足し、生成した全サンプル・両言語を実際の `sh -n` に通すようにした
  （サンプルは教材なので、配ったものが構文エラーだと最初の 1 本で詰む）。

- install は**既存ファイルを上書きしない**（`-f` のときだけ上書き）ので、ユーザーが編集したものが launch 毎の再配置で消えない
- `list` は各スクリプトの 2 行目コメントを説明として並べる。`show` / `run` / `dir` も持つ
- サンプル本文のコメントはアプリ言語（ja/en）に追従する
- **動かし方はスクリプト自身が宣言する**（`# z2-run: <動かし方>`、0.8.247）。install はその行があればそれを出し、無ければ従来どおり「常駐サーバーに登録」を出す。⚠ 一律に常駐を案内していたため、**使い切りのスクリプトを常駐させてしまう**案内が出ていた（`rss.sh` は終了するたび supervisor が再起動するので、延々とフィードを取りに行くことになる。実機で発覚）。`# z2-run:` は**説明行（2 行目）より後ろ**に置くこと — 先頭に置くとウィジェットのボタン説明（`WidgetStore.describe`）がそれを拾ってしまう

**QR を「アプリの機能」に戻さない（`qr.sh`、0.8.308）**: 0.8.219 で状態ウィジェットに SSH 接続 QR を
載せ、0.8.220 に「やはり要らない」の判断で自前エンコーダ（`QrEncoder` / `ReedSolomon`）ごと撤去した
経緯がある。⚠ **アプリ側へ復活させない** — 欲しいのは「いま手元にあるものを打ち直さずに別の端末へ
渡す」ことで、それは distro の `qrencode` と画像表示（Kitty graphics）の組み合わせで足りる
（同梱物ゼロ・F-Droid 適合）。⚠ **QR は壊れていても「それらしい模様」が出て目視では検証できない**ので、
実績のある実装に任せる方が結果も確かになる（自前実装のときは `qrencode` と 1 マスずつ突き合わせていた）。

- **前提が欠けたら、このタブでの入れ方を出して止まる**。`qrencode` はどの distro にもあるが既定では
  入っていない。⚠ パッケージ名が distro ごとに違う（Alpine だけ `libqrencode-tools`）ので、
  `command -v` で見えたパッケージマネージャに合わせて出す。
- **既定は絵、`-t` で文字、`-o` で PNG**。⚠ ブロック文字は端末のフォント次第で行間に隙間が出て、
  目で読めてもカメラが読み取れないことがある。逆に画像を出せない端末（ssh で入った先など）では
  絵が意味不明な文字列として流れる。**どちらが要るかは相手の端末次第**なので両方残す。
- **長い入力は行の区切りで分ける**（900 バイトで切り、`[1/3]` と番号を振る）。⚠ 行の途中では切らない
  （日本語が混じっても壊れない）。1 行が長すぎて 1 枚に入らないときだけは QR にできない。
- **縦横比は仮定するしかない**。端末は 1 セルの大きさを教えてくれないので 1:2 と決め打ちし、
  合わない環境向けに `Z2_QR_ASPECT` を残す。
- ⚠ **`usage()` の awk は空行で止めない**（`NF` で判定）。説明が長く、段落を空行で区切ってあるので、
  止める作りだと冒頭 2 行しか出ない（`remind.sh` が 0.8.288 で踏んだのと同じ穴）。

**リマインダーを「アプリの機能」にしない（`remind.sh`、0.8.275）**: 「通知でリマインドしたい・繰り返しも単発も・アプリを閉じていても」という要望に対して、**アプリ側には予定の画面もデータも足していない**。部品は全部あった — 単発は `z2-alarm`、繰り返しは `z2-when time:`、鳴らすのは `z2-notify -b`、返事は `event:notify_action`、アプリを開かず足すのは `z2-tile` + `z2-ask`。足りないのは繋ぎ方の見本だけで、`rss.sh` と同じ立ち位置になる。

- **単発と繰り返しで置き場を分ける**。⚠ 単発まで `z2-when` にすると、`time:at=` は発火後に自動で無効化されるが**ルール自体は残る**ので、使うほど死んだルールが自動化タブに溜まる。だから単発は `z2-alarm` の予約（鳴れば消える）にし、拾い役の `event:alarm` を**常設 1 本**だけ置く。逆に繰り返しを `z2-alarm` で組むと拾う側を予定の数だけ書くことになるので、そちらは `z2-when time:` のルールにして自動化タブに並べる（ON/OFF も ▶ での試し打ちもタダで付いてくる）。
- **本文はファイルに置き、通知の名前（`-n`）には id だけ入れる**。`z2-notify -n <名前>` が `event:notify_action` の `Z2_WHEN_EVENT_NAME` にそのまま返るので、これで単発・繰り返しどちらのボタンも**受け口 1 本**で受けられる。⚠ 名前に本文を入れると、空白や絵文字が混ざった瞬間に突き合わせが壊れる。
- **受け口は 2 本で固定**（`event:alarm` / `event:notify_action`）。予定を何件足しても `z2-when` のルールは増えない。`GeneratedScriptMarginTest.remindSample_splitsOneShotAndRepeating` が、この分担が崩れる変更を止める。
- ⚠ **`expr` で先頭 0 を落とさない**。`expr "00" + 0` は値 `0` を返しつつ**終了コード 1** を返すので、`|| echo` の右側まで走って cron 式が 2 行に化ける（`time:cron=0` として登録され、一覧に並ぶのに一生発火しないルールになった。実際に踏んだ）。`${v#0}` で書く。

**フィード購読を「アプリの機能」にしない（`rss.sh` / `rss-open.sh`、0.8.246）**: RSS リーダーが欲しいという要望に対して、**アプリ側には 1 行も足していない**。定期実行（`z2-when time:`）・通知とボタン（`z2-notify -b` → `event:notify_action`）・ブラウザで開く（`z2-open`）・ライブ tail ウィジェット、という汎用部品が既に全部あり、足りないのは**繋ぎ方の見本**だけだったため。画面を作ると「用途限定の 1 枚」が増えるうえ、フィードの形式は現場ごとに崩れているので**アプリが直し続ける対象**になる。ターミナル側なら崩れた 1 本のためにユーザーが 1 行足せば済む。

- **既読は「見た行を引き算する」**（`seen.txt` に貯めて `grep -Fxv`）。`z2scan` のベースライン差分と同じで、**フィードの日付や並び順を信用しない** — どちらも当てにならない
- **解析は python3（標準ライブラリのみ）に任せる**。RSS と Atom は形が揺れるので `grep`/`sed` で切るとフィードを 1 本増やすたびに壊れる。pip 依存は増やさない
- **1 本落ちても他は続ける**。取得失敗で全体が止まると、電波の悪い日に何も届かなくなる
- **`latest.txt` の行に URL を残す**。ウィジェットから読むときに参照先が要る
- ⚠ **ライブ tail の「行ごとのタップ」は作らなかった**。本文は 1 つの `TextView` に流し込む作りで（RemoteViews は行数ぶんの View を生やせない）、行を個別に押させるには一覧ウィジェット（`ListView` + `RemoteViewsService`）への作り替えが要る。0.8.240 で入れた「高さから行数を決める」「先頭/末尾を選ぶ」を組み直すことになるので見送り、代わりに**状態ウィジェットのマクロボタン**（既に「タップで `~/.z2term/macros/` 配下の `.sh` を実行」）に `rss-open.sh` を割り当てる形にした。押すたびに次の 1 本へ進む（`opened.txt` を引き算するので同じ記事が二度開かない）

⚠ **KDoc に `/` と `*` を並べて書かないこと**。Kotlin は**ブロックコメントが入れ子になる**ので、`macros/` の直後に `*` を書いた時点でコメントが 1 段深く開き、閉じ側がずれて**以降のコードが丸ごとコメントに飲まれる**（`Unresolved reference` が大量に出て、原因の行とは全く別の場所に構文エラーが出る）。0.8.246 の実装中に実際に踏んだ。

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
  - `-w /root`、env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 TZ=… PATH=… TMPDIR=/tmp` + 履歴系 env。
  - **`TZ` は端末のタイムゾーンを POSIX 形式で渡す ([`PosixTimeZone`]・0.8.302)**。⚠ それまで distro の中は
    `TZ` も `/etc/localtime` も無く **常に UTC** で、`date` の返す時刻が端末と食い違っていた。相対指定は
    差分なのでずれないが、**`18:30` のような絶対時刻は時差ぶんずれて予約され、一覧も同じだけずれる**
    (JST で 9 時間。利用者の報告 —「スヌーズは正しいのに予定だけ来ない」という形で出る)。
    - ⚠ **ゾーン名 (`Asia/Tokyo`) を渡さない**。解決できるのは **tzdata が入っている distro だけ**で、
      無ければ libc は黙って UTC へ落ちる (Alpine の `tzdata` は既定で入っていない)。パッケージの有無で
      時計が狂うのは避ける。POSIX 形式 (`<+09>-9`) なら libc だけで解釈でき、glibc / musl / busybox の
      いずれでも同じに効く。
    - ⚠ **略称は `JST` ではなく `<+09>` の数字表記**。`TimeZone.getDisplayName` はロケール次第で
      `GMT+09:00` のような POSIX では読めない文字列を返し、そうなると **`TZ` 全体が無視されて UTC へ落ちる**。
    - 夏時間は `,M<月>.<週>.<曜日>/<時刻>` を 2 本書く。⚠ **時刻は「切り替え直前のその土地の時刻」**で
      書く決まりなので、`ZoneOffsetTransitionRule` の `timeDefinition` (壁時計 / 標準時 / UTC) を見て
      直してから書く — そのまま書き写すと**切り替えが 1 時間ずれる**。表せない規則 (日付固定・年 3 回以上)
      は夏時間なしとして今のオフセットだけ書く (次にタブを開けば作り直されるので実害が小さい)。
    - `PosixTimeZoneTest` が、生成した文字列の**形**と、それが**指すオフセット**の両方を、夏と冬の
      両方で本物のゾーンと突き合わせる。
- **共有ホーム**: `filesDir/shared_home` を全 distro 共通で `/root` にバインド (← 端末の `~` の実体)。
- **シェル設定は OS 側を尊重する (0.8.502)**: アプリ独自の「ログインシェル」と「シェルのプロンプト」を廃止。端末タブと GUI 内ターミナルは rootfs の `/etc/passwd` にある root のシェルを使い、アプリは `/etc/passwd` / `/etc/shells` を書き換えない。プロンプトも `~/.ashrc` / `~/.bashrc` / `~/.zshrc` などで自由に管理する。旧版が rc へ書いた z2term ブロックは、既存環境を勝手に変えないため自動削除しない。
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
- 起動毎に冪等で注入: `ensureShellHistoryConfig` (履歴 rc)、`ensureMacroPathConfig` (マクロ置き場の PATH)、`ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear ラッパー)、`ensureOsc7CwdConfig` (cwd 復元用 OSC7 フック)、`ensureZ2ApiScripts` (`z2-*` ブリッジ)、`ensureZ2AdbScript` (`/usr/local/bin/z2adb`)、`ensureZ2HelpScript` (`/usr/local/bin/z2help` + エイリアス `/usr/local/bin/z2term`)、`ensureZ2ScanScript` (`/usr/local/bin/z2scan`)、GUI/z2run スクリプト、`ensureVersionScript` (`/usr/local/bin/z2version`)。
- **シェルのプロンプトは見本から作って rc へ書く (`ShellPrompt`・0.8.364・要望)**: 素の rootfs のプロンプトは distro 任せ (Alpine の ash なら `localhost:~#`) で、変えるには自分で rc を書くしかなかった。設定画面で **シェルと見本を選ぶ → 中身がボックスに出る → その場で直せる → 適用** までを閉じる。⚠ **見本は「そのまま使える出来のもの」を置く** — 素っ気ない `user@host:~$` を並べても結局みんな自分で書き直すことになり、見本を置いた意味が無い (利用者の指摘)。罫線の 2 段組み・終了ステータスで色が変わる `❯`・Kali の `┌──(%n㉿%m)-[%~]`・背景色の帯まで含める。⚠ **帯の区切り (powerline の `` = U+E0B0) は rc の中でエスケープから組み立てる** (`ARROW_RIGHT=$'\ue0b0'`、sh は `printf '\356\202\260'`・利用者の案)。⚠ **実文字をソースへ埋めない** — 一度そうしたところ経路の途中で**黙って落ちて区切りが空になった**。目で見て分かる壊れ方をしないので `ShellPromptTest` で「生成物に私用領域の字を入れない」ことを固定する。⚠ 「私用領域だから同梱フォントに無い」は**誤り**で、cmap を実測すると **Fira Code と JetBrains Mono は持っている** (IBM Plex Mono だけ持たない)。フォントの中身は推測せず実測する。値は rc にあるので、出ないフォントの人は `\u25b6` (▶) 等へ書き換えればよい。⚠ **アプリの設定として抱え込まず rootfs の rc ファイルに書く** — 後から `vi ~/.bashrc` で直せることに意味があるので、真実は常にファイル側にあり、設定画面は開くたびに rc から読み直す。書き込み先は `~/.ashrc` (sh) / `~/.bashrc` / `~/.zshrc`。⚠ **その実体は rootfs の中ではなく `filesDir/shared_home`** — `launch()` は `HOME=/root` を**共有ホームに bind する** (全ディストロで同じ HOME を使うため) ので、`distros/<id>/root/` へ書いても**誰も読まない**。0.8.364 で実際にそこへ書いてしまい、**「書き込みました」と出るのにプロンプトが一切変わらない**という壊れ方をした (0.8.365 で修正)。⚠ 書き込み成功のログだけでは「効いた」ことにならない。**rc の mtime か、新しいタブでの実際の表示**で確かめる。⚠ **目印 (`# >>> z2term prompt >>>`) で囲んだ部分の差し替え**で、外は 1 文字も触らない (利用者の `alias` が黙って消えると原因の分からない事故になる)。⚠ `appendOnceWithMarker` (履歴 / OSC7 / PATH) は**一度書いたら触らない**作りなので流用できない — プロンプトは選び直して何度でも適用するものなので、同じ作法だと 2 回目が効かない。⚠ **色の書き方はシェルで違う**: bash は `\[ \]` で幅を持たないと教えないと**長い行の折り返しがずれる**、zsh は `%F{}` (`\[ \]` はそのまま表示される)、sh (busybox ash) はどちらも解さないうえ PS1 内の `\033` を展開しないので **ESC を `printf` で作って変数に入れる**。取り違えは「動くが見た目だけ壊れる」ので `ShellPromptTest` で固定する。⚠ **右端の時刻 (任意・要望) は端末の幅を数えない**: `COLUMNS` は sh では設定されないことがあり、あっても画面を回したときに更新されないので、幅から引く作りは**必ずズレる**。`ESC[999C` で右端まで動いて (端で止まる) `ESC[8D` で戻り、書いたら `ESC[u` で元の位置へ帰る。zsh だけは `RPROMPT` に任せる — zsh が幅を数えて寄せ、行が伸びれば自分で引っ込めるので確実。bash では**カーソル移動と時刻をまとめて `\[ \]` に入れる** (帰ってくるので実際の幅は 0)。
- ⚠ **sh (busybox ash) が rc を読む口を開けた (0.8.364)**: ash は非ログインの対話シェルでは **`$ENV` が指すファイルしか読まない**。`launch()` の env に `ENV=/root/.ashrc` が無かったため、**ash では rc に何を書いても効かない**状態だった (既存の履歴 / OSC7 設定も bash/zsh にしか届いていない)。0.8.359 で Alpine の既定シェルを `/bin/ash` に寄せたので、**プロンプトが一番効いてほしい相手がまさにここ**。ファイルが無ければ何も起きないので無害で、bash/zsh はこの変数を見ない (bash は POSIX モードのみ)。
- **マクロ置き場 (`~/.z2term/macros`) はどの OS でも最初から PATH に入っている (0.8.314)**: 0.8.287 で `launch()` が渡す env の PATH **末尾**に足したが、それだけでは足りない経路があった — **ログインシェルは `/etc/profile` で PATH を丸ごと組み立て直す**ので、SSH ログイン (dropbear)・`su -`・GUI 内ターミナルでは足したはずの末尾が消え、`remind.sh help` が `command not found` になる (案内も docs も「名前で打てる」前提で書いてある)。`ensureMacroPathConfig` が rootfs 側にも同じ設定を置く: `/etc/profile.d/z2term-path.sh` (ログインシェル。Alpine/Debian/Arch/Kali いずれも `/etc/profile` が `profile.d` 配下の `.sh` を読む) と `/etc/bash.bashrc` / `/etc/zsh/zshrc` (profile を読まない非ログインの対話シェル)。⚠ **末尾に足す** (同名のコマンドがあったとき OS 側を覆わないため)、⚠ **既に入っていれば足さない** (`case` で判定＝何度読まれても PATH が伸びない)。置き場そのもの (`shared_home/.z2term/macros`) も作る。
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
- **`z2-*` の `--help` (0.8.331)**: 上記のヘルプ本文は各スクリプトの先頭に `#` コメントとして入っているのに、**それを表示する手段が無かった**。`z2-tile help` は 1 行の usage を返して終わり、詳しい説明は `cat $(command -v z2-tile)` でしか読めない — 端末しか入口が無い機能でこれは実質「説明が無い」に等しい (利用者の指摘)。
  - **本文は増やさず、出す手段だけを足す**。`awk` で「2 行目から最初のコード行まで」を拾って `#` を剥がす 1 行を各スクリプトの先頭に置く (`z2-macro` が先に使っていた手)。ヘルプ本文を heredoc へ複製すると、コメントと本文が二重管理になり片方だけ古くなる。
  - ⚠ **空行では止めない** (`NF` で判定)。見出しの塊を `#` だけの行で区切ってあるので、空行で止める作りだと冒頭数行しか出ずに「動いている」ように見える (`z2-macro` が 0.8.286 まで実際そうだった)。
  - **受け付ける綴りは 2 通り**。サブコマンド式 (`z2-tile` / `z2-icon` / `z2-when` / `z2-session` …) は `-h|--help|help`、**文章を引数に取るもの** (`z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-say` / `z2-ask`) は `--help` のみ。`z2-toast help` は「help と表示する」が正しく、ヘルプに化けたら黙って動作が変わる。⚠ `-h` はどの文章型にも付けない — `z2-notify -h` が既に `--high` (バナー) で、揃えられないものを 1 つだけ例外にすると覚えられない。
  - `z2-toast` / `z2-share` / `z2-open` にはそもそも先頭コメントが無かったので、`toastHelp` / `shareHelp` / `openHelp` を新設した。
  - `z2help` の末尾に「各コマンドに `--help` を付けると詳しい説明が出る」を明記。一覧だけ見て終わる人に次の一手を渡す。
  - `Z2ApiScriptTest.everyScriptPrintsItsHelp` が、日英**全スクリプト**について `--help` の出力を Kotlin 側で数え直した先頭コメントと**1 行ずつ突き合わせる**。「冒頭だけ出て合格」を作らないため。
  - Kotlin のコメントは日本語のまま (開発者向けで端末には出ない)。
  - **長いヘルプは見出しで塊に分ける (0.8.369)**: `z2-tile` のヘルプは 33 行が段落も見出しも無く続いていて、必要な 1 行を探せなかった (利用者の指摘: 「かなり見にくい」)。⇒ **コマンド一覧を先頭に置き、`#` だけの行 (= 空行) で区切って「割り当てるもの / 押したときの動き / パネルに置く / アイコン (-i) / 例」の見出しを付ける**。中身は 1 行も削っていない。⚠ **一覧の `…` は展開後の幅で揃える** — `$tiles` は Kotlin 側で 1 桁の数字に化けるので、ソース上で桁を揃えると端末ではずれる。

- **`z2-ask` コマンド (0.8.267・人に聞いて答えを受け取る)**: `name=$(z2-ask "ブランチ名は?")`。**通知の返信欄 (`RemoteInput`) で聞く**。`-t 秒`（既定 300）/ `-H ヒント` / `-d 既定値`。
  - **なぜ足したか**: 人へ問いかける手段は `z2-notify -b <ラベル>` の**ボタン**しか無く、**用意した選択肢からしか答えられなかった**。「どのブランチへ？」「保存先は？」のような**自由入力**は選択肢では書けず、マクロは聞くのを諦めて決め打ちにするしかなかった。
  - ⚠ **画面 (Activity) を出さない。** ダイアログにすると今やっている作業を中断させるうえ、**裏で走っているマクロ（`z2-when` の発火など）には前面へ出る手段が無い**。返信欄つき通知なら**アプリを開かずシェードのまま**答えられて、受け口も `z2-notify -b` と同じ「通知 + ブロードキャスト」＝**新しい常駐は増えない**。
  - ⚠ **応答は `dispatch` の戻り値では返せない。** 他の動詞は「呼ばれた → その場で答える」だが、`ask` は人が入力するまで何分でもかかる。そこで `handleRequestFile` が `ask` だけ**先に捌き**、`resp` を書くのは返事が届いた [`AskReplyReceiver`] → `completeAsk`。
  - ⚠ **答えずに消したときも必ず返す**（通知の `setDeleteIntent`）。何も返さないと端末側が待ち時間いっぱい黙って固まる。キャンセルと時間切れはどちらも**非ゼロ終了**にしてあるので、`ans=$(z2-ask …) || exit 1` と**「答えなければ諦める」がそのまま書ける**。
  - ⚠ **`PendingIntent` は `FLAG_MUTABLE`**。`RemoteInput` は OS が入力内容を Intent へ差し込む仕組みなので、`IMMUTABLE` だと**入力が届かない**（他は `IMMUTABLE` で揃えてある中でここだけ例外）。返信後は `setAutoCancel` が効かないので**明示的に通知を閉じる**（残ると二度目の返信ができてしまう）。
  - ⚠ **待ち時間はこのコマンドだけ伸ばす**。`z2api` ディスパッチャの応答待ちは既定 5 秒で、`Z2API_WAIT`（0.1 秒単位）で上書きできるようにした。一律に長くすると、アプリが止まっているときの「応答が来ない」に**どのコマンドも延々と付き合う**ことになる。
- **`z2-noti` コマンド (0.8.236)**: いま出ている通知を TSV (key / パッケージ / アプリ名 / タイトル / 本文) で返す**だけ**のコマンド。通知検知は既にあったが、できるのは記録だけで、シェルから「いま何が出ているか」を見る手段が無かった。
  - ⚠ **「押す」「消す」は意図的に提供しない。** 元の提案 (中毒家案) には通知のボタンを押す動詞が含まれていたが、それは**他アプリの決済ボタンや送信ボタンも押せる**ということで、**誤爆の実害がこのアプリの外に出る**。32 件の提案で唯一その性質を持つ機能なので、まとめ役の判断どおり読む側だけを実装した。
  - `getActiveNotifications()` は `NotificationListenerService` のメソッドなので、OS が bind した稼働中インスタンス経由でしか読めない ([`NotificationLogService.activeNotificationsTsv`])。未許可・未 bind なら「通知アクセスが許可されていません」を返す。
  - 自分自身の通知は除外し、値の中のタブと改行は空白へ寄せる (TSV を壊さない)。

- **`z2-tile` コマンド (0.8.258・クイック設定タイル)**: マクロ / コマンドを**クイック設定パネル**へ 12 枠まで置ける。`z2-tile set <1-12> <マクロ.sh|コマンド...> [-l 表示名]` / `list` / `clear <1-12|all>`。
  - **なぜ D1 ウィジェットと別に要るか**: ホーム画面ウィジェットは「ホーム画面へ戻る」必要がある。クイック設定は**どのアプリを開いていても** 2 スワイプで出るので、**別のことをしている最中に届く唯一の入口**になる。動画を見ながら「テザリング用の sshd を上げる」がアプリ切り替えなしで届く。
  - **常駐は増やさない**。`TileService` はシェードを開いている間しか OS にバインドされない。実行は D1 と同じ [`HeadlessRun`] を通す（入口が増えても実行経路は 1 本）。
  - **約束は D1 ウィジェットのボタンと同じ**: 押すと実行、実行中は ON の見た目 (`Tile.STATE_ACTIVE`)、もう一度押すと停止。入口ごとに違う操作感を作らない。
  - **押したらクイック設定パネルを畳む（0.8.284）**。⚠ パネルが開いている間、Android は**ヘッドアップ通知（画面上部のバナー）を出さずシェードに積むだけ**で、トーストも同じくパネルの下に隠れる。`remind.sh ask` をタイルから押すと `z2-ask` の返信欄がパネルの下に潜り、**押したのに答えられない**状態になっていた（実機で指摘）。⚠ パネルを畳む口は `TileService.startActivityAndCollapse` しか無く、**Activity を起こすことが条件**なので、画面を持たず開いた瞬間に自分を閉じる踏み台 [`TileCollapseActivity`]（透明テーマ・`noHistory`・`excludeFromRecents`）を通す。Android 14 から `Intent` を渡す形は `UnsupportedOperationException` を投げるので、版で `PendingIntent` 版と分ける。⚠ トグル系（ライト等）でもパネルは畳まれる — 入口ごとに挙動を変えず、**押した結果が必ず見える**方を選ぶ。
  - **入 / 切が別コマンドのものは `--off` で 1 枚にまとめる（0.8.261）**。`z2-tile set 3 z2-torch on --off z2-torch off`。押すたびに交互に走り、**入の間だけ ON の見た目**。`z2-torch on` を割り当てただけの枠は「押すたびに `on` が走る」＝**タイルからは消せない**ので、消す側を書けるようにした（利用者の提案）。
    - ⚠ **引数を 2 つ並べるだけの形にはできない**。`z2-tile set 1 ls -la` が「入 = `ls` / 切 = `-la`」に化ける（残りの引数を 1 つのコマンドに繋ぐのが元からの読み方）。だから区切りを明示させる。`-l` と同じく **`--off` はどこに書いてもよい**。
    - ⚠ **この緑はアプリが覚えているだけ**で、実態を見に行っていない（点いている光を Android から読む方法は無い）。端末から直接 `z2-torch off` を打つとタイルだけ入のまま残る。`z2-screen` を別扱いにしているのは、**あちらだけはアプリが実態を持っている**ため。
    - **切るときは止めない**。走っているプロセスを殺すのではなく、書かれた切るコマンドを走らせる（`z2-torch on` のプロセスを殺しても光は消えない）。実行キーも入 / 切で分ける — 同じキーだと、切るコマンドを走らせた瞬間に入のほうを「実行中」と数える。
    - **割り当て直したら「入」の記憶は捨てる**。持ち越すと、別のものを載せた 1 回目が切るほうから始まる。
  - **状態を持つコマンドは、その状態を ON の見た目にする（0.8.260）**。`z2-screen keepon 1h` は設定を書いて**すぐ終わる**ので、「実行中だけ ON」で描くと**押した瞬間しか点かず、シェードを開き直すと掛かっているのか切れているのか分からない**（実機で指摘）。割り当てが `z2-screen keepon <時間>` の枠だけは、ON の見た目が**「消灯しないが掛かっている間」**を指す（[`TileStore.isScreenKeepOn`]）。正本は `ScreenTimeout` が持つ保存ファイル 1 つなので、**端末から `z2-screen keepon off` を打ってもタイルが揃う**。⚠ `keepon off` と `status` は対象外 — 状態を持たない一度きりの操作なので、ON にすると「押すと消える ON」になる。
    - **外すのはアプリ内で完結させ、掛けるのは今までどおりコマンドを走らせる**。`1h` の読み方をタイル側へ書き写すと、端末の `z2-screen` と 2 か所で解釈がずれる（秒への変換は sh 側が正本）。
    - **残り時間は名前の後ろに足す**（`消灯しない 60分`・[`TileStore.labelWithSuffix`]）。⚠ **副題 (`Tile.subtitle`) は機種によっては一切表示されない** — 実機（Android 15）ではアイコンと名前しか出ず、0.8.260 で副題に置いた「残り 60 分」は**誰にも読めなかった**。出せる場所が名前しか無いので、状態はそこへ畳み込む。**名前を削ってでも残りは残す**（押す前に知りたいのは「これは何か」ではなく「あとどれだけか」）。副題も引き続き埋めておく（出る機種ではそちらに「押すと解除」まで出る）。
    - ⚠ **シェードを下ろした時点の値で止まる** — `TileService` は開いている間に描き直されないので、秒読みはできない。だから分より細かくは出さず（[`TileStore.remaining`]・**切り上げ**）、`keepon 1h` の直後に「残り 59 分」と出て短く見えることも避ける。
    - ⚠ **「緑」と書かない**。`Tile.STATE_ACTIVE` の色は**端末のアクセント色**で、アプリからは選べない（実機で「緑ではない」と指摘された）。文言は「ON の見た目」に統一する。
  - **割り当ての無い枠はクイック設定の一覧に出さない（0.8.260）**。枠数ぶん常に並ぶと、タイルを使わない人の編集画面が z2term で埋まる（実機で指摘）。枠は manifest 決め打ちで**増やせない**が、`PackageManager.setComponentEnabledSetting` で**個別に無効化はできる**ので、減らす方向だけ実行中に効かせる（[`TileStore.syncEnabledTiles`]・`z2-tile set` / `clear` と起動時に揃える）。⚠ **1 つも割り当てが無ければ 1 枚も出さない（0.8.271）**。0.8.260〜0.8.270 は「枠 1 だけは割り当てが無くても出す」としていた（機能に気付ける場所をアプリの外に 1 つ残す意図）が、**使わない人には空の枠が消せずに残り続ける**だけだった（実機で指摘）。⚠ 気付いてもらうための枠を、使わない人に押し付けない。`TileStoreTest.onlyAssignedSlotsAreListed` が `n == 1 ||` への逆戻りを止める。⚠ 無効にした枠が既にパネルへ並んでいたら **OS がパネルからも外す**。もう一度割り当てても自動では戻らないので、`z2-tile clear` は「タイルを 1 枚片付ける」操作でもある。
  - **割り当てたら「パネルに置きますか」を OS に聞かせる（0.8.355・Android 13 以降）**。⚠ **「タイル編集」の一覧に出る名前とアイコンは manifest 決め打ち**（`z2term 1`〜`z2term 12` と既定の絵）で、**実行中に差し替える API が Android に無い**。並べた後は [`Z2TileService.render`] が本当の名前と絵を載せるので、利用者からは「**タイルを追加したときはアイコンも名前も初期状態で、何を追加したのか分からない。並べてみると正しくなるが、そこまでしないと分からない**」と見えていた（実機で指摘）。⭐ **`StatusBarManager.requestAddTileService` はラベルとアイコンを引数で渡せる唯一の口**なので、`z2-tile set` の直後にこれを呼び、**割り当てた名前と絵が乗ったダイアログ**で聞く（[`Z2TileService.requestAdd`]）。⚠ **勝手に置くのではない** — 出るのは OS のダイアログで、断れる。**置く場所を決めるのは利用者**という約束は変えない（アプリが自分でタイルを並べる API は今も無い）。⚠ **前面にいないと出ない**（`TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND`）ため、裏で走るマクロからの登録では出ない。⚠ **同時に 2 つは頼めない**（`…_ERROR_REQUEST_IN_PROGRESS`）ため、2 枠まとめて登録するマクロでは 2 つめが黙って落ちる。⇒ **`z2-tile add <枠>` を用意して後から聞き直せるようにする**。⚠ どちらの失敗でも**割り当て自体は済んでいる**ので、**`set` は聞けたかどうかを返さない** — 聞けなかったことを失敗として見せると「割り当てにも失敗した」と読める。Android 12 以前は `add` が理由を言って断り、編集画面での探し方（目印は `z2term <枠番号>`）を案内する。
  - **絵まで 1 行で決める（`z2-tile set … -i <絵の名前>`、0.8.357）**。ダイアログに乗る絵は [`IconStore.tileIcon`] が返すもの＝**聞く時点で枠に入っている絵**なので、`z2-icon` で選び直すのは必ず「聞かれた後」になり、**順番が逆**になる（利用者の指摘: 「アイコン変えてからどうやってタイル設置するの？」）。⇒ `-i` を付けると**割り当てと同じ 1 行で絵が確定**し、その絵でダイアログが出る。名前は `z2-icon sample` の一覧（同梱の絵と自分で保存した絵の両方。[`IconStore.findSample`]）。⚠ **絵は割り当てより先に引く** — 無い名前なら**何も割り当てずに断る**（割り当てだけ済んで絵が違う状態は、何も起きていない状態より気付きにくい）。⚠ **`-i` を入れてから [`IconStore.autoAssign`] を呼ぶ**こと。[`IconStore.set`] は「手で入れた」印を残すので `autoAssign` はその枠に触らない。逆順にすると `-i` が自動の絵に負ける。⚠ **既に並べてあるタイルには `-i` は要らない** — `z2-icon` で変えれば [`Z2TileService.render`] が読み直して即反映される。`-i` が効くのは**置く前**（ダイアログと編集画面）だけ。
  - ⚠ **ロック画面から素通しで走らせない**。`TileService.unlockAndRun` を通すので、ロック中は OS が解除を求め、解除できたときだけ走る。**設定での ON/OFF は作らない** — 拾った人がシェードからコマンドを撃てる状態は「選べるようにしても選ぶ理由が無い」類で、誤爆の実害がアプリの外に出る（`z2-noti` に「押す」を作らなかったのと同じ線引き）。
  - **枠は 12（0.8.294 に 4 から拡張）**。タイルは manifest に `TileService` を 1 個ずつ書く必要があり、**実行中に増やせない**（Android の仕様）。実装は枠番号だけが違う 12 クラス (`Z2Tile1`〜`Z2Tile12`)。
    - **増やせたのは「割り当ての無い枠を一覧から消す」が先にあったから**（上記 0.8.260）。空き枠が編集画面に並ばない以上、**多めに用意することの実害が無い**。常駐も増えない（`TileService` はシェードを開いている間しかバインドされない）。「マクロが増えると 4 枠では足りない」という実機からの指摘に対し、作りを変えずに数だけ広げられた。
    - ⚠ **減らす方向は安全でない**。減らした先の枠に残った割り当ては `z2-tile clear` の範囲検査から外れ、**消すことも押すこともできなくなる**。`TileStore.COUNT` / manifest の `<service>` / `Z2TileService.CLASSES` の 3 つは必ず揃える。
    - 数を文言へ**書き写さない**（`Z2ApiMsg.tiles` が `TileStore.COUNT` を読む）。ヘルプに `1-4` と直書きしていると、増やしたときにそこだけ古いまま残る。
  - **マクロかコマンドかを選ばせない**。`~/.z2term/macros/` にある名前と一致すればマクロ、それ以外はそのままコマンド（[`TileStore.scriptFor`]）。`--macro` のようなフラグを足すと、打つ側が毎回「これはどっちか」を考えることになる。
  - **マクロは引数を取れる（0.8.275・`TileStore.scriptOf`）**。判定を「割り当て全体がマクロ名と一致」から**先頭の語だけを見る**形へ変えた。それまでは `remind.sh ask` のように**引数を 1 つ付けた瞬間にコマンド扱いへ落ち**、⚠ **マクロ置き場は PATH に入っていない**ので `not found` で終わっていた。**タイルは押しても無反応**で、失敗は `~/.z2term/tile/run.log` にしか出ない ＝ 外から見て正しい割り当てと区別が付かない壊れ方（実機で踏んだ）。1 本のマクロをサブコマンドで使い分ける書き方は自然に出てくるので、そちらを通す。引数はシェルへ素通し（`$HOME` や `$(…)` が効く）、マクロ名だけは従来どおり単一引用符で囲む（実在ファイル名しか来ないので展開させる理由がない）。`TileStoreTest.aMacroKeepsItsArguments` が固定する。
  - **置き場に無い `.sh` は登録時に断る（0.8.275・`z2-tile` 側）**。`z2-when` の綴り検査と同じ思想で、**書いた瞬間に止める**。通してしまうと上記の「無反応なタイル」になり、原因が追えない。⚠ 判定は「`.sh` で終わり、かつパス区切りを含まない」名前だけ — フルパス指定 (`sh /path/foo.sh`) は素直にコマンドとして通す。`Z2ApiScriptTest.tileSetSeparatesLabelFromCommand` が、通る形と断る形の両方を実際の `sh` で押さえる。
  - **表示名は先頭の語から作る**ので、同じマクロを 2 枠に置くと**同じ名前**になる（`remind.sh ask` も `remind.sh peek` も `remind`）。使い分けは `-l`。⚠ 引数まで名前に畳み込まない — 長い名前は機種によって黙って切れる。枠が 12 になって「同じマクロを引数違いで並べる」使い方が現実的になったぶん、`-l` を付ける場面はむしろ増えた。
  - **表示名は `-l` がどこにあっても拾う**。`z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない` のように**後ろに足したくなる**もので、頭だけ見る作りだと `-l 消灯しない` がコマンドに混ざって毎回おかしな引数付きで走る。`Z2ApiScriptTest.tileSetSeparatesLabelFromCommand` が実際の `sh` で前後どちらも同じに読めることを固定する。
  - **名前が機能そのもの**なので、省略時はマクロなら拡張子を落とした名前、コマンドなら**先頭の語**にする（全文はクイック設定の幅で必ず切れる）。上限 12 文字。`TileStoreTest` が押さえる。
  - **並べるのは利用者**。アプリが勝手にタイルを配置することは OS が禁じている。クイック設定パネルの編集（鉛筆）から並べてもらう。
  - **設定画面に窓口を作らない（0.8.271）**。0.8.258〜0.8.270 は設定に「クイック設定タイル」の節を置き、割り当ての一覧と Android 13+ の `StatusBarManager.requestAddTileService`（「追加しますか」を OS に頼む）ボタンを出していた。**利用者の判断で節ごと撤去**（`requestAddTileService` の呼び出しも削除）。⚠ **機能は残っている** — 割り当ても一覧も `z2-tile` で完結し、正本は端末側 1 つだけになった（`~/.z2term/macros/` が正本のマクロと同じ考え方）。設定画面に窓口が無くなったぶん、**未割り当てなら枠を 1 つも出さない**（上記）と揃って「使わない人の目に入らない」が徹底される。

- **`z2-icon` コマンド (0.8.294・ステータスバーとタイルのアイコンを描き替える)**: `z2-icon pick <対象>` / `sample [名前|対象 名前]` / `edit <対象>` / `set <対象> [ファイル|-]` / `show <対象>` / `clear <対象|all>` / `grid [24|48|64]` / `scale <対象> <24|48|64>` / `list`。対象は **`notify`（このアプリが出す通知すべてに使う 1 枚）** と **枠 1〜12（タイルは枠ごとに別の絵）**。
  - **なぜドット絵で受けるか**: ステータスバーのアイコンもタイルのアイコンも、**OS が単色で塗り直す**（タイルは入 / 切で色が変わる）。⚠ **色を選ぶ余地は最初から無く、指定できるのは形だけ**。だから白黒のマス目がそのまま表現力の上限になる。画像を受け取って縮小・二値化するより、**書いたものと出るものが一致する**方を採った（`show` で見えるものがそのまま出る）。
  - **一辺は 24 / 48 / 64 から選ぶ（0.8.379・[`IconStore.GRIDS`]）**。⚠ **「実表示は 24px 前後だから 24 で足りる」はステータスバーだけの話だった** — クイック設定のタイルはもっと大きく描かれ、そこでは 24 の点がそのまま階段に見える（利用者の指摘）。表示の大きさが違う 2 か所へ同じ絵を配る以上、一辺を 1 つに決め打ちすると必ずどちらかが損をする。
    - **一辺は絵 1 枚ごとの持ちもの**で、**保存された絵そのものから読む**（[`IconStore.gridOf`]・正規形の行数がそのまま一辺）。別に持つと絵と食い違ったときにどちらが正なのか決められない。既存の絵は 24 行のまま読めるので、**移行が要らない**。
    - **[`IconStore.parse`] は塗った範囲が収まる最小の一辺を選ぶ**。⚠ 大きい一辺へ勝手に移さないこと — **一辺を上げても絵は大きくならない**ので、24 の絵を 64 のマス目へ入れると**タイルの中で小さくなる**だけになる。
    - **Bitmap は一辺によらず 192px 角へ揃える**（[`IconStore.OUT_PX`]・24x8 / 48x4 / 64x3）。⚠ **どの一辺も 192 を割り切ること**。割り切れない一辺を混ぜると点の幅が 1px ずつずれ、細かく描いた絵ほどかえって乱れる（`IconStoreTest.everyGridDividesTheBitmapSize` が止める）。
    - **いまある絵は敷き直せる（`z2-icon scale <対象> <一辺>`）**。24 で描いた絵を 48 で描き直すのは事実上の描き直しなので、**見た目（マス目に対する絵の割合）を保ったまま**一辺だけ変える道を用意する。⚠ 小さい一辺へ敷き直すと細い線は落ちる（戻せないので、利用者が選んだときだけ）。
    - **出す直前に均す（0.8.382・[`IconStore.render`] が [`IconStore.SMOOTH_GRID`] まで [`IconStore.scale2x`] を通す）**。⚠ **一辺を選べるようにしただけでは、誰のアイコンも滑らかにならなかった** — 手元の絵も同梱の 14 種も 24 で描かれていて、`z2-icon scale` を自分で打った人にしか効かない（利用者の指摘: 「解像度倍にしてるのに滑らかになってないのは欠陥」）。⇒ **Bitmap を作る直前に均す**（24 → 96 / 48 → 96 / 64 → 128 相当）。⚠ 均すのは**表示だけ**で、保存してある絵も `show` が出す絵も触らない（描いたものと `edit` で開くものが食い違ってはいけない）。
    - ⚠ **細かい一辺ほど、出すときも細かいこと**（0.8.383）。0.8.382 は出来上がりの Bitmap を 192px に固定していたため、**64 の絵だけ均されなかった**（128 が 192 を割り切らない）。結果、`z2-icon scale` で 64 にすると**24 のまま置くより粗く出る**という逆転が起きていた（利用者の「64 でタイルに適用するのはどうするの」から発覚）。⇒ px 数の固定をやめ（192px か 256px。OS はどのみち表示の大きさへ縮める）、**均した一辺を常に 2 倍で敷く**。`IconStoreTest.finerGridsNeverComeOutRougher` が [`IconStore.smoothedGrid`] の単調性を固定する。
    - **プレビューは入りきるなら畳まない（0.8.382）**。CLI が `stty size` で桁数を測って渡し、入る絵はそのままの桁数で出す。⚠ 48 の絵を機械的に 24 桁へ畳むと、**均した斜めが元の階段に戻って見え**、「敷き直しても何も変わらない」ように映る（利用者の報告）。畳んだときは `48x48 → 24x24` と添える — 黙って畳むと、出ている絵が本物だと思わせてしまう。⚠ 測れないとき（パイプ・tty でない）は 0 を渡して既定の 32 桁に落とす。
    - **名前の逆引きは敷き直した絵も引き当てる（0.8.382・[`IconStore.nameOf`]）**。⚠ `z2-icon scale` を通すと正規形テキストが一字も一致せず、`z2-icon list` が名前を `-` に落としていた — 「絵はあるのに名前が無い」枠は、**入れた絵が消えたように見える**（利用者の報告）。同梱の絵は各一辺の敷き直しを逆引き表に持ち、自分の絵は照合のたびに敷き直して比べる。
    - **敷き直しは Scale2x (EPX) で斜めの段を割る（0.8.381・[`IconStore.scale2x`]）**。⚠ **点をただ 2x2 に太らせるだけでは、細かいマス目へ移しても階段は同じ大きさで残る** — それでは「タイルがかくかくして見える」ことが何も変わらない（一辺を選べるようにしただけでは、同梱の 14 種も手元の絵も 24 のままなので、実際には誰も滑らかにならない）。⇒ 2 倍にできるあいだは Scale2x を通し、**向かい合う 2 辺が同じで残りと違う角だけ**を隣の値で埋める。⚠ **平らなところと孤立した点は必ずそのまま太る**ので、描いた形が勝手に別物にならない（ドット絵拡大にこれを選ぶ理由がそこにある）。⚠ **枠の外は自分自身とみなす** — 外を「空」として読むと、マス目いっぱいに描いた絵の外周が削れる。⚠ 2 倍にならない端数（24 → 64 の 4/3 倍ぶん）は近い点を拾って敷き直すだけなので、**24 → 48 がいちばんきれいに決まる**。`IconStoreTest.scalingSmoothsDiagonalSteps` が「斜めの隙間が埋まること」、`scalingLeavesFlatAreasAlone` が「平らなところは角 4 つ以外そのまま太ること」を固定する。
    - **これから描く絵の一辺は `z2-icon grid <一辺>`**（既定 24）。⚠ **もう入っている絵は作り直さない** — 一辺を変えても絵の見た目は変わらないので、頼まれていない絵にまで触る理由が無い。
    - **プレビューは 32 桁を超えたら 2x2 を 1 点へ畳む**（[`IconStore.preview`]）。⚠ 64 桁のまま出すと携帯の画面幅で折り返し、**形を確かめるという目的そのもの**が果たせない。畳むときは**どれか 1 つでも塗ってあれば塗る** — 多数決にすると 1 点幅の線が丸ごと消える。
  - **塗る字を決め打ちにしない**。空きマスは `.` ` ` `0` `-` `_` で、**それ以外の文字はすべて塗り**（[`IconStore.parse`]）。描く人が見分けやすい字を選べる。⚠ 「`#` だけが塗り」にすると、`*` や `X` で描いた絵が**黙って空になる**（1 点も塗られていないと断られるので、何が悪いのか分からない）。
  - **余白は無視して中央へ置き直す**。行や桁をきっちり合わせなくてよく、`$(cat)` が末尾の空行を落とすことも気にしなくて済む。⚠ 逆に**大きすぎる絵は弾く**（塗った範囲が一辺の最大を超えたらエラー）。黙って切り詰めると、描いた本人にだけ**端の欠けたアイコン**が届き、何が起きたか分からない。
  - **絵は base64 にして渡す**。ドット絵は改行を含む数百バイトの塊で、生のまま引数に載せるとリクエストファイルの「1 行 = 1 引数」（[`Z2ApiBridge`] のプロトコル）が壊れ、**2 行目から先が黙って落ちた絵**が届く。`Z2ApiScriptTest.iconSendsTheDrawingAsBase64` が、ファイル経由と標準入力経由の両方で同じものが渡ることを実際の `sh` で押さえる。
  - **いま出ている通知はその場で差し替える**（[`refreshActiveNotifications`]）。⚠ これが無いと、常駐通知は**次に作り直されるまで古いアイコンのまま**になる — 常駐は普段作り直されないので、差し替えたのに何も起きないように見える。`Notification.Builder.recoverBuilder` で出ている通知から組み立て直し、アイコンだけ替えて同じ ID で出し直すので、本文もボタンも常駐の扱いも残る。⚠ `setOnlyAlertOnce` を付ける（付けないと出し直しでもう一度鳴る）。タイル側は次にシェードが開かれたときに描き直させる（[`Z2TileService.requestUpdate`]）。
  - ⚠ **差し替えられない場所が 3 つある**（Android が導入時に固定するため）: クイック設定の**「タイル編集」一覧**に出るアイコン（manifest の `android:icon`）、**ファイル選択 (SAF) のルートアイコン**、**ランチャーアイコン**。並べた後のタイルと実際に出る通知は差し替わる。ヘルプにこの 3 つを明記する — 「変えたのに変わらない場所」を黙っていると故障に見える。
  - **タイルに置いた中身から絵を自動で当てる（0.8.299・[`IconSamples.guess`]）**。枠が 12 になって「並べるほど既定のアイコンばかりで見分けが付かない」が現実の問題になったので、`z2-tile set` の時点で**名前から分かる範囲だけ**絵を入れる（`remind.sh` → 時計、`battery-alert.sh` → 電池、`z2-screen keepon` → 月）。同梱マクロは全部当たる。
    - ⚠ **自分で決めた絵は絶対に上書きしない**。自動で入れた枠にだけ印を持ち、触ってよいのは「まだ絵が無い枠」と「前にここが自動で入れた枠」だけ（[`IconStore.autoAssign`]）。印を持たず**絵の有無だけ**で判断すると、`z2-tile set` を打ち直すたびに**自分で描いた絵が消える**か、逆に**前のマクロの絵が残り続ける**かのどちらかになる。
    - ⚠ **合う絵が無ければ、前に自動で入れた絵は消す**。枠の中身を別のマクロへ替えたのに前の絵だけ残ると、タイルの見た目が中身と食い違う。`z2-tile clear` でも自動の絵だけ片付ける（手で描いた絵は残す — 置き直すたびに描き直しになるため）。
    - **自動へ戻す道を用意する（`z2-icon auto <枠|all>`）**。自動を断る手段（`z2-icon` で好きな絵を入れる）だけを作ると、⚠ **一度手で入れた枠を二度と自動に戻せない**（`clear` してから `z2-tile set` を打ち直す、という遠回りしか残らない）。`auto` は**手で入れた絵も上書きする** — 明示的に頼まれたときだけ通る道なので、そこで遠慮しない。`z2-icon list` は `auto`（自動で付いた）と `custom`（自分で入れた）を出し分ける — どちらなのかが見えないと、戻せること自体に気付けない。
    - ⚠ **語の並び順が意味を持つ**。`battery-alert` は `battery` が `alert` より先で電池、`unknown-call` は `unknown` が `call` より先で注意 — どちらも**後ろの語のほうが一般的**なので、狭い意味の語を上に置く。
    - ⚠ **短い語を入れない**。`log` は `login` に、`test` は `latest` に、`dir` は `direct` に当たる。**当たらないより、間違って当たるほうが悪い**（意味の合わない絵が黙って付く方が、既定のアイコンより分かりにくい）。`IconStoreTest.everyBundledMacroGetsAnIcon` が同梱マクロ全部に絵が付くことを、`theGuessDoesNotFireOnUnrelatedWords` が誤爆しないことを固定する。
  - **同梱の絵はいちばん細かい一辺で描き起こす（0.8.384・15 種すべて 64）**。⚠ **24 で描いた絵を機械的に拡大しても粗さは消えない** — 元が 24 段の輪郭なら、いくら大きく敷いても段は 24 のままで、[`IconStore.scale2x`] が均せるのは段の大きさだけ（利用者の指摘: 「サイズを大きくしただけで荒いことは変わらない」）。0.8.379〜0.8.383 は**入れ物だけ広げて中身を描き直していなかった**ので、`pick` で入れた人には何も変わって見えなかった。⇒ 円・弧・多角形として 64 で刻み直す（`qr` だけは 21 モジュールを 3 ドットずつ敷いた元の模様のまま — 均しでモジュールの角が丸まってはいけない）。`IconStoreTest.everySampleIsDrawnAtTheFinestGrid` が 24 へ戻らないことを固定する。
    - **自動で入った絵は起動時に追いつかせる**（[`IconStore.refreshAuto`]）。⚠ これが無いと、絵を描き直しても**すでに置いてある枠だけ前のまま**になり、「更新したのにタイルは変わらない」になる。⚠ **手で入れた絵には触らない**（[`IconStore.autoAssign`] の印で見分ける）。⚠ 中身が変わっていないときは書き込みも再描画も起こさない（起動のたびに走るため）。
  - **サンプルを同梱する**（[`IconSamples`]・15 種）。マス目を前にして最初から思いどおりの形を置ける人はほとんどおらず、**白紙から描き始めさせない**入口が要る（利用者の提案）。`z2-icon pick <対象>` が一覧を出して番号で選ばせ、選んだ絵はそのまま `z2-icon edit` で描き直せる — **サンプルと自作の間に段差を作らない**（どちらもただのテキスト）。サンプルは**番号でも名前でも**指せる（一覧を見て選んだ直後の 1 回と、2 回目からの打ち方を食い違わせない）。⚠ 並び順が番号なので**追加は末尾へ**。`IconStoreTest.everySampleIsAValidDrawing` が、壊れた絵を配らないことを固定する（自分で描いた絵は直せるが、同梱のものは直しようがない）。
  - **自分の絵も一覧に入れる（0.8.300・`z2-icon save <対象> <名前>`）**。`edit` で描いた絵はその対象にしか残らず、**別の枠へ同じ絵を入れたければ描き直すしかなかった**。名前を付けて残せれば同梱の絵とまったく同じように番号でも名前でも選べる — ここでも**同梱と自作の間に段差を作らない**（[`IconStore.userSampleNames`]・`forget` で一覧から下げる）。
    - ⚠ **並びは登録順で、名前順に並べ替えない**。一覧は番号で選ぶので、名前順にすると新しく保存した絵が途中へ割り込み、**前に覚えた番号が別の絵を指す**ようになる（同梱の並びを固定してあるのと同じ理由）。
    - ⚠ **数字だけの名前と、空白を含む名前を断る**。前者は「3 番」と「3 という名前」のどちらを指すか決められなくなるため、後者は一覧が TSV なので列がずれるため（[`IconStore.normalizeSampleName`]）。
    - ⚠ **`forget` は一覧から下げるだけで、入れてある絵は消さない**。一覧の整理をしただけでタイルの見た目が変わるのは筋が違う（戻すのは `clear`）。
  - **`z2-icon list` は入っている絵の名前まで出す（0.8.300）**。⚠ それまでは `custom` としか出ず、**枠を何枚も使うとどこに何が入っているのか分からなかった**（利用者の指摘）。逆引きは**正規形テキストの一致**で行う（[`IconStore.nameOf`]）ので、⚠ **同じ形の同梱サンプルが 2 つあると入れたときと違う名前が出る** — `IconStoreTest.noTwoSamplesAreTheSameDrawing` が重複を止める。名前で形を思い出せないときのために `z2-icon list -p` が絵そのものを並べる（⚠ **入れてある対象だけ**。既定のままの枠まで並べると見たいものが画面の外へ流れる）。
  - **ヘルプは「やりたいこと順」に並べる（0.8.300）**。⚠ サブコマンド 8 つを並べてから注意書きを続ける形では、**まず何を打てばよいかが読み取れなかった**（利用者の指摘）。いちばん多い用途（一覧から選んで入れる）を先頭に置き、残りを「入れる / 自分の絵を残す / 描き方 / 注意」へ分ける。
  - **保存は SharedPreferences**。タイルは**アプリのプロセスが生きていない状態**で読まれるので DataStore（非同期）は使えない（[`TileStore`] と同じ理由）。1 枚 600 バイト弱なのでファイルに逃がす必要も無い。Bitmap は作った物を使い回し、`set` / `clear` で捨てる（通知は 1 回出すたびに読まれる）。
  - **点は 4 倍に引き伸ばしてから渡す**。24px の Bitmap をそのまま渡すと OS 側の拡大で**にじむ**ので、こちらで整数倍にして点の角を保つ。塗る点は**不透明な白** — OS がここへ状態の色を被せるので色を決めても意味が無く、被せない機種では白のまま出る（ステータスバーもタイルも暗い背景なので白で成り立つ）。
  - **通知を作る場所は必ず 1 つの入口を通す**（`NotificationCompat.Builder.setZ2SmallIcon`）。素の `setSmallIcon(R.drawable.ic_notification)` が 1 か所でも残ると**そこだけ差し替えの効かない通知**になり、どれが漏れているか外から分からない。
  - ⚠ **その入口は [`IconStore`] と別ファイルに置く**（`icon/NotificationIcon.kt`）。`object` とトップレベル関数を 1 ファイルへ混ぜると、Android lint (K2 UAST) が**そのファイルの解析中に `ClassCastException` を投げて lint 全体が中断する**（AGP 9.1.1 で実際に踏んだ）。⚠ 落ちるのは lint だけで**コンパイルも実行も通る**ので、lint を回すまで気付けない。置き場を分けるだけで避けられる。
  - **アプリ側に編集画面を作らない**（利用者の判断）。割り当ても編集も `z2-icon` で完結し、正本は端末側 1 つだけ（`z2-tile` と同じ考え方）。`edit` は `$EDITOR` を開くだけで、**使い慣れたエディタの矩形選択がそのまま使える**。⚠ `$EDITOR` が未設定の端末でも動くよう `nano` / `vim` / `vi` を順に探す。触らずに閉じたときは反映まで走らせない（エディタを開き間違えただけのことがある）。

- **`z2-screen` コマンド (0.8.257・OS の自動画面消灯を期限つきで止める)**: 「長いビルドを眺めていたいので **1 時間だけ**自動消灯を止めたい」への回答。`z2-screen keepon 1h` / `keepon off` / `status`。
  - ⚠ **ツールバーの 🔅 とは別の機能**で、あちらには一切触らない (利用者の明言)。🔅 は `FLAG_KEEP_SCREEN_ON` で「**アプリを開いている間**消えない」だけなので、畳んだら効かない。ここで扱うのは `Settings.System.SCREEN_OFF_TIMEOUT`＝**OS 全体の設定**で、アプリを畳んでもホームに戻っても効く。**用途が違うので統合しない** — 片方を消すともう片方の使い方ができなくなる。
  - **マクロだけでは実現できない**ことを実測で確認済み: rootfs から `/system/bin/settings` は見えるが、アプリ UID から叩くと `SecurityException` (この binder シェルコマンドは `shell` / `root` 専用)。なので Android の作法どおり `WRITE_SETTINGS` を宣言し、利用者が「システム設定の変更」画面で明示的に許可したときだけ書き換える。許可の入口は設定 › **画面の自動消灯 (z2-screen)**。
  - **必ず元へ戻すのがこの機能の設計の中心**。「消灯しない」を掛けっぱなしにすると電池が静かに溶けるので、掛けたまま忘れられる状態を作らない。
    - **時間を必須にする** (`keepon` に引数が無ければ usage で終わる)。期限の無い「消灯しない」は最初から作らせない。
    - **元の値を保存する** (`filesDir/screen_timeout.json`)。⚠ 既に掛かっているときに `keepon` を重ねたら**期限は後から打った時間で置き換え、元の値は最初のものを保ち続ける** — ここで元の値を読み直すと「消えない」を元の値として保存してしまい、期限が来ても戻らなくなる。期限のほうを置き換えにするのは、`keepon 10m` と打ち直したときに**縮められない**と「掛けすぎたので短くする」ができなくなるため。
    - **期限は `AlarmManager` で予約する** ([`ScreenTimeoutReceiver`])。アプリが落ちても OS が起こす。予約は端末再起動で消えるので、[`BootReceiver`] と `Z2TermApplication` の**両方**から `restoreOrReschedule` を呼び、再起動中に期限を過ぎていたら**その場で書き戻す**。入口を 2 つ持つのは、取りこぼしの代償が「電池が減り続ける」で大きいため。
    - **上限 24h**。打ち間違いで何日も点きっぱなしにしない。
  - **「掛かっているか」の正本は保存ファイルの有無**で、`SCREEN_OFF_TIMEOUT` の現在値では判定しない。利用者が設定アプリ側で値を触っても矛盾しないようにする。
  - 「消えない」に書き込む値は `Int.MAX_VALUE` (約 24.8 日)。Android に無限を表す専用値は無く、OS 標準の設定アプリが「なし」に使うのと同じ考え方。
  - 相対時間 (`1h` / `30m` / `90s` / 単位なし＝秒) の**秒への変換だけ sh 側**で行い、あとはアプリ側 ([`ScreenTimeout`]) が持つ (`z2-alarm in` と同じ分担)。先頭 0 (`05m`) を落とすのは `$(())` が "05" を 8 進と解釈する実装があるため。`Z2ApiScriptTest.screenParsesDurations` が `z2api` をスタブに差し替えて**ブリッジへ渡る引数そのもの**を固定する — ここを間違えると「1 時間のつもりが 1 秒」で、掛かったように見えて掛かっていない。

- **`z2doctor` コマンド (0.8.230・トラブル切り分け)**: 「動きません」を 1 コマンドで切り分ける自己診断。**`z2scan self` とは用途が別**で、あちらは「危ない設定を探す」(セキュリティ)、こちらは「動かない理由を探す」。名前が近いので混ぜないこと。
  - 各行は `OK` / `NG` / `--`（不明・該当なし）の 3 状態。**`NG` には必ず次の一手を 1 行付け、書けない項目は最初から出さない**（直し方の分からない `NG` は不安にさせるだけ）。**取れなかったものは `--` で、`NG` として数えない** — 分からないことを異常に格上げしない。
  - 末尾に**そのまま貼れる報告文**。相手が打つのは 1 コマンド、返ってくるのは短い報告、という形にすると「動きません」→「何が？」の往復が消える（サポートの往復 1 回は、1 日 1〜3 時間の開発では丸 1 日に相当する）。
  - **SSID・IP・ホスト名は出さない**。伏せていることを画面にも明記する。伏せ字を後付けにすると、報告文に社内 IP や SSID が混ざる事故が必ず起きる。
  - **情報源を 2 つに分ける**: 許可の有無・設定・常駐の数のように**シェルからは原理的に見えない**ものは `z2api 1 doctor`（[`Z2ApiBridge.doctorRead`](../../app/src/main/java/com/zerotoship/z2term/service/Z2ApiBridge.kt)）が JSON で返し、kernel・空き容量・sshd・`/sdcard` はシェル側で調べる。ブリッジ側は**値の解釈をしない**（`NG` の判定と文言は CLI に置く）。
  - **前回までの終了を出す（[`ExitReasons`](../../app/src/main/java/com/zerotoship/z2term/service/ExitReasons.kt)、0.8.376）**: 「アプリがいつの間にか消えている」は、これまで端末側から原因を辿る手段が無かった。Java の例外なら `logcat -b crash` に残るが、**メモリ不足でカーネルに殺された（SIGKILL）場合はアプリは自分が死んだことすら知らない**まま消える（ネイティブのクラッシュも tombstone は system uid の管轄で読めない）。しかも実機ではこのアプリの uid から読める logcat が**十数分ぶんしか残らない**（エンジン下の exec が SELinux の監査行を大量に出すため。13 分の logcat 737 行のうち 682 行がこれだった）＝数時間に 1 回の事象は、気付いて見に行った時にはもう流れている。⇒ OS が持っている**直近 16 回ぶんの死因**（`ActivityManager.getHistoricalProcessExitReasons`）を**起動のたびに読み、`~/.z2term/exits.jsonl` へ写す**（重複は時刻で弾く）。診断には**気にすべき終了だけ**を新しい順に出す — 入れ替え・ユーザー操作・自分から終了は日常なので数えない（開発中の入れ替えで欄が埋まると本当の事故が埋もれる）。1 行に**理由・死んだ時の RSS・そのときの扱われ方**を並べるのは、`CACHED` で片付けられたのか**`FOREGROUND_SERVICE` のまま殺された**のか（＝常駐が守れていない）で話が別だから。⚠ **原因を判定しない** — 出すのは OS が言っている事実だけで、解釈は読む人に任せる（他の欄と同じ約束）。⚠ API 30 未満は OS 側に記録が無いので静かに何もしない。
  - **落ち方は 2 通りあり、片方は OS に残らない（`recordTabKill`、0.8.378）**: 実機で観測すると、**アプリのプロセスが死ぬ**場合と、**端末タブのプロセスツリー（エンジン＋シェル＋その子）だけが死んでアプリは生き残る**場合の両方がある（2026-08-21 の実測: アプリは同じ pid のまま、タブの木と Gradle の JVM だけが消えた）。利用者にはどちらも「落ちた」に見えるが、**後者は `ApplicationExitInfo` に残らない** — あれはアプリのプロセスの記録で、`fork`/`exec` で起こした子は数えないため。⇒ PTY の終了コードが `128 + シグナル番号`（`pty_jni.cpp` の `WIFSIGNALED`）だったものを、こちら側で `~/.z2term/exits.jsonl` へ書く。診断では `app:` / `tab:` の印で混ぜて時刻順に出す（別々に出すと「どちらが先に起きたか」という一番知りたいことが分からない）。⚠ **自分で畳んだときと区別できないと意味が無い** — タブを閉じる・再起動・distro 切替・SSH へ切替はどれもシグナルで終わるので終了コードでは見分けられない。`TerminalSession.closeChannel` を通ったか（`selfClosed`）だけが根拠なので、**チャネルを畳む経路は必ずそこ 1 か所を通す**。⚠ **空きメモリはその瞬間に採る**（`ActivityManager.MemoryInfo`）。後から見ても「そのとき逼迫していたか」は分からず、それが無いと OS に落とされたのか自分で落ちたのかを切り分けられない。⚠ **タブの名前はファイルには残すが診断には出さない** — 報告文はそのまま貼るものなので、利用者が付けた名前にホスト名が入っている可能性を排除できない。画面にも同じことを 1 行で出す（`banner_process_killed`: 何のシグナルで・空きが何 MB だったか）。
  - ⚠ **ブリッジが無い状態でも最後まで走り切ること**を `Z2DoctorScriptTest` が実際の `sh` で固定する。診断は困っている人が最後に打つものなので、そこで落ちると打つ手が無くなる。
  - **その OS に無い項目は `false` ではなく `null` を返す（0.8.241）**: `storage_all`（`MANAGE_EXTERNAL_STORAGE`）は API 30 で入った権限で、minSdk の API 29 には存在しない。`false` を返すと「設定を開いて許可してください」という**その端末では実行できない次の一手**が `NG` として出てしまうので、`JSONObject.NULL` を返して CLI 側の `--`（該当なし）に落とす。API 依存の値を足すときは同じ扱いにすること。

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

  **③ ベースライン差分 (`self --save` / `diff` / `baseline`、0.8.243)**: 毎回フルレポートを出しても、**人はその差に気付けない** (毎日読める量ではない)。今の状態を「基準」として保存し、次からは**変わった行だけ**を出す。
  - 比べるのは `[WARN]` / `[INFO]` の行と、その下にぶら下がる字下げ行 (ファイル名の列挙)。**見出し・`[ OK ]`・件数・空行は落とす** — 実行のたびに変わるものを基準に入れると、毎回「変化あり」になって役に立たない。`sort -u` で並び順の揺れも消す。
  - **増えたものがあるときだけ exit 1**。減っただけなら 0 — 片付けたその日に通知が鳴ると、鳴っても見なくなる。`--quiet` は無変化のとき出力を完全にゼロにするので、`out=$(z2scan diff --quiet); [ -n "$out" ] && z2-notify …` がそのまま書ける (`z2-when time:daily` と組んで「勝手に増えたものだけ通知」)。
  - **`diff` コマンドに依存しない**。busybox / GNU で挙動が割れるため、行集合の引き算 (`grep -Fxv -f`) で足りる範囲に留める。`self --save` は診断を **1 回しか流さない** (`find` が走るので 2 回流すと体感で遅い)。
  - 基準は `~/.z2term/scan/baseline.txt` に**テキストで**置く (そのまま読める・git で持ち歩ける)。ヘッダに `# lang:` を残し、**表示言語を変えた後は全部変化として出る**理由が読み手に分かるようにする (メッセージ文字列そのものを比べているので当然そうなる)。言語が食い違うときは警告を出す。
- `launchAndroidSh`: proot 不可時のフォールバック (`/system/bin/sh` + 最小 mkshrc)。

#### 実行エンジン z2root (裏機能・非 root)

`executionEngine = "z2root"` のとき、`launch()` がバイナリを `nativeLibraryDir/libz2root.so` (自前 ptrace エンジン) に差し替える。proot 互換 argv subset を受けるので引数・env はそのまま流用する (`PROOT_*`/talloc は z2root が無視)。

`libz2root.so` 未同梱 (`scripts/build-z2root.sh` 未実行) の場合は engine binary not found で停止する (proot prebuilt は 0.8.328 で削除済みなので、フォールバック先が無い)。

**tracee ごとの rootfs (0.8.416 / パスの扱いは 0.8.417)**: `execve` の envp に `Z2ROOT_ROOTFS=<パス>` があると、そのプロセスと以降の子孫が **その rootfs を "/" として見る**。空文字を渡すと既定 (`-r` で指定したもの) に戻る。コンテナ相当の「別環境に入る」を、入れ子起動 (10.1 により不可) ではなくこの形で実現している。⭐ **値は「いま居る環境から見えているパス」で書く。** ゲスト内のプロセスは自分のホスト実パスを知りようがない (HOME は rootfs 配下ではなく `shared_home` へ bind しているように、rootfs との機械的な連結では出せない) ので、z2root が `host_path_for` でホスト実パスへ変換してから採る。⚠ **逆にホスト実パスを直接書くのは仕様外** — いまの rootfs 配下を指す場合だけ変換不要として素通しするが、bind 先など rootfs の外を指すホストパスはゲストパスとして変換されるので通らない。⚠ 0.8.416 はホスト実パスしか受け付けず、そのため `z2c run` が `No such file or directory` で通らなかった。

- 実装: `struct pid_state` に `rootfs_idx` (rootfs テーブル `g_rootfs_tab` の添字。文字列を直に持つと `PATH_MAX_Z` × `MAP_CAP` で 1MB 増えるため添字にした) を持たせ、`waitpid` ループの先頭で `rootfs_select(pid)` が実効 rootfs を選ぶ。パス変換は `translate_abs` / `host_to_guest` / `host_path_for` の 3 関数に集約されているので、そこが `EFF_ROOTFS()` を見るだけで全 syscall に効く (`struct config` は 1 個のまま = 157 箇所の `cfg` を触らずに済む)。
- 継承: `fork`/`clone` で親から引き継ぐ (コンテナの中で fork した子は同じコンテナに居る)。`execve` の envp で明示されたときだけ切り替わる。
- 適用順: `record_exec_argv` の直後 = `plan_exec` の**前**に効かせる。exec するプログラム自体を新しい rootfs から解決する必要があるため。
- bind (`-b`) は切り替えても有効なまま。`/dev`・`/proc`・`/sys` は別環境でも要るので、これが望ましい。
- 分けられるのは**ファイルシステムだけ**。namespace が無いのでプロセス表もネットワークも共有で、ptrace 方式ゆえ tracee 側から迂回できる。**環境の分離であってセキュリティ隔離ではない**。
- 上限は `MAX_ROOTFS` = 16 環境。
- 使う側は `scripts/z2c run <イメージ>` (9 章)。

**ビルド成果物の stale 対策 (0.8.48)**: z2root/z2accept の `.so` はビルド成果物 (git 管理外) で `git pull` や CMake では再生成されないため、`z2root.c` を直しても古い `.so` が APK に同梱され続ける事故が起きる。Gradle タスク `buildZ2rootNative` が jniLibs マージ前に `scripts/build-z2root.sh` を自動実行するので、`./gradlew assemble*` だけで常に現ソースから再生成される (手動手順ゼロ)。`build-z2root.sh` は NDK パスを自己解決する (環境変数 / `local.properties` の `sdk.dir`+`ndk.version` / `$ANDROID_HOME`)。`merge*JniLibFolders` すべてが `buildZ2rootNative` に依存し、`src/main/jniLibs` へ出す。実行時に取得するのは rootfs であって z2root ではないので、`z2root.c` の修正は常に APK 側に載る。

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
| ファイル監視 (`inotify_add_watch`=27) | パス引数 (arg1) をホスト実パスへ書き換える。⚠ **arg0 は inotify fd であって dirfd ではない**ので dirfd 無しとして扱う。既定は最終 symlink を辿り、mask に `IN_DONT_FOLLOW`(0x02000000) があるときだけ辿らない。⚠ **これが抜けていると実在するディレクトリでも必ず `ENOENT`** になり、ファイル監視を使うアプリが軒並み「対象が無い」と誤認する (実機で確認: KDE の `KDirWatch` が既存ディレクトリに対して `inotify failed … No such file or directory` を出していた)。`inotify_init1`(26)/`inotify_rm_watch`(28) は path を取らないので非対象 | 0.8.352 |
| copy-fallback 後の `st_dev`/`st_ino` | git 2.46+ の「`link()` 後に dest を lstat し src と一致検証」を通すため、**パス相関**で偽装する (`linkcopy_record` がコピー先のホスト実パスを記録し、`newfstatat`/`statx` の entry で stat 対象のホストパスを `host_path_for` で解決して `linkcopy_find` が一致を見たときだけ exit で `st_dev`/`st_ino`、statx は `stx_ino`＋`stx_dev_major/minor` を src 値へ偽装) | 0.8.58〜0.8.64 |

`libz2accept.so` は `scripts/build-z2root.sh` が生成し gitignore される。`ProotLauncher` が rootfs の `/usr/local/lib/libz2accept.so` へ配置し `LD_PRELOAD` を env 注入する (読み込み失敗は ld.so が警告して無視する非致命)。`__errno_location` は `__attribute__((weak))` + NULL ガードで参照するため、bionic 製バイナリ (aapt2 等) に LD_PRELOAD が漏れても起動失敗しない (0.8.55)。 ⚠ **別アーキのプロセスへ漏れると致命的**: この `.so` は rootfs の ABI (aarch64) に合わせたもので、エミュレータ越しに x86_64 のバイナリを動かすと ld が `Error relocating …: unsupported` を出して止まる。bionic 漏れと違い weak シンボル化では救えない (シンボルの問題ではなく機械語の問題) ため、**別アーキを起動する経路では `env -u LD_PRELOAD` で明示的に外す** (`scripts/z2c` はそうしている)。

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

**0.8.319 Arch でパッケージが一切入らない (`getresuid` の抜け)**: 利用者から「foss で GUI インストールが何度やっても失敗する」の報告。実機ログの一次エラーは `pacman-key needs to be run as root for this operation.` だった。⚠ **GUI 固有ではない** — `pacman` 本体は root として通る (ダウンロードは走る) のに、`pacman-key` (bash スクリプト・`EUID != 0` で弾く) だけが弾かれる、という**同じ実行の中での食い違い**が手掛かりだった。失敗時に `id` と shell の値を並べて残すようにして確定:

```
z2diag: id-u=0 id-ur=0 sh-EUID=10576 sh-UID=10576 bash-EUID=10576
```

`id` は 0、bash は実 uid。バイナリの動的シンボルを見ると **`id` は `getuid`/`geteuid`(174/175) を、`bash` は `getresuid`(148) を使う**。z2root の fakeroot 対象は `setresuid`(147)/`setresgid`(149) を含みながら、**対になる getter の `getresuid`(148)/`getresgid`(150) だけを落としていた**。glibc の bash は setuid 判定に `getresuid` を使うため、**glibc 系 distro (Arch/Ubuntu/Kali) では `$UID`/`$EUID` が常に Android のアプリ uid**で、`EUID` を見る shell スクリプトが軒並み「root で実行してください」で止まっていた (`pacman-key --init` はその 1 例で、鍵束が作れない → `SigLevel = Required` の Arch では**何一つインストールできない**)。

- 修正: 148/150 をトレース対象に足し、**出力先の real/effective/saved 3 つを 0 に書き換える** (`fake_getres_on_exit`)。⚠ `getuid`/`geteuid` と違い**戻り値ではなくポインタ渡し**なので、戻り値だけ 0 にしても実 uid が漏れる。出力先ポインタは **entry で控える** — exit では x0 が戻り値に潰れていて第 1 引数を読めない。
- ⚠ **setter と getter は対で入れる。** この抜けは「set 系を列挙して get 系の対を落とす」形で入り込み、`getuid`/`geteuid` だけ見ていると偽装が効いているように見えるので気付けない。
- ⚠ **失敗の理由を必ず残す。** 0.8.316〜0.8.318 は「失敗しました」の一行しか残らず、原因の特定に実機を何往復もした。端末タブの出力は logcat に流れないので、`z2-pacman-keyring` は理由を**共有ホーム側**のファイルにも書き (rootfs 内に置くと再展開で消える)、`ProotLauncher` が次の起動で logcat へ出して消す。

**0.8.327 Arch の鍵束初期化を阻んだ gpg-agent の非 dumpable 化**: 残っていた `gpg-agent: error binding socket ... No such file or directory` は、gpg-agent が秘密情報保護のため `prctl(PR_SET_DUMPABLE, 0)` を呼ぶことで、z2root の `process_vm_readv` が `EPERM`、`PTRACE_PEEKDATA` も `EIO` になり、直後の `bind(2)` の `sockaddr_un` を読めなくなるのが真因だった。パス翻訳が飛ぶとゲストの `/etc/...` をホストの `/etc/...` に bind して ENOENT になる。z2root は tracee メモリを読み書きして成立する userspace root なので、この prctl だけ引数を 1 へ書き換えて dumpable を維持する。実機 foss 版で鍵束完了マーカー作成、次回起動で再実行なし、`pacman -Sy --noconfirm` が core/extra/alarm/aur の全 DB を正常同期することを確認済み。

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
- chroot 起動失敗時は proot へ自動フォールバック（`TerminalSession.startTerminal`）。SELinux Enforcing 下の root 端末(moto g13/Magisk)で end-to-end 検証済み。

### 4.4 ディストロ管理 (`distro/`)

- `DistroBundle`: `ROOTFS_VERSION`(=9)、`VERSION_MARKER`、`BUNDLED_DISTRO_ID="alpine"`。
- `DistroSpec`: id/表示名/パッケージマネージャ/同梱可否/asset 名/DL URL or index URL/既定シェル/DL サイズ目安。
  - Alpine = 同梱 (`alpine-minirootfs-aarch64.tgz`, zsh)。Ubuntu/Arch/Kali = linuxcontainers の index から最新 `rootfs.tar.xz` を実行時解決して DL (bash)。
- `DistroInstaller`: 依存無しの手書き tar パーサ (ustar/GNU `L`/PAX `x`/`g`、symlink/hardlink)。`decompress` がマジックバイトで gzip/xz 判定。
  - **Zip-Slip 対策 (0.8.141)**: 全展開先を `outputDir.canonicalFile` 配下に封じ込める (`isWithin`)。`canonicalFile` が既存プレフィックスの symlink を解決し `..` を正規化するため、悪意ある `../` エントリと「親に仕込んだ脱出 symlink を辿る write-through」の双方を弾く。ハードリンク元 (`linkname`) も同判定で rootfs 外読み出しを防ぐ。逸脱エントリは本体を `skipFully` で読み飛ばしつつストリーム整合を保ってスキップ。SHA 未固定で DL する Ubuntu/Arch/Kali の汚染 tar でアプリ領域外へ書き込まれるのを防ぐ (symlink の *ターゲット自体* は正 rootfs に多数ある正当な域外 (proot 名前空間内) リンクを壊さないよう制限しない — 危険なのは経由書き込みで、そちらを封じる)。手組み tar を実 `extractTar` に流す `ZipSlipExtractionTest` (正常展開 / `../` / write-through symlink / 域外 hardlink の 4 ケース) で回帰を防ぐ。JVM テストで `android.util.Log` を no-op 化するため `testOptions.unitTests.isReturnDefaultValues=true`。
  - `postInstallSetup`: resolv.conf/hosts、`pacman.conf` (sandbox/DownloadUser 無効化)、apt の Sandbox::User=root、version マーカー書込。
  - **pacman の鍵束を初期化する (`z2-pacman-keyring`、0.8.316)**: Arch の rootfs は linuxcontainers のイメージ (`mirror.archlinuxarm.org` / repo は core・extra・alarm・aur) から取るが、**`/etc/pacman.d/gnupg` が入っていない**。通常は systemd の初回起動で `pacman-key --init` が走る前提で、**proot/z2root では systemd が動かないので誰も初期化しない**。一方 `pacman.conf` は `SigLevel = Required DatabaseOptional` なので、**パッケージを入れようとすると何をしても** `error: keyring is not writable` → `error: required key missing from keyring` で失敗する（GUI 導入も `sshd`=dropbear も同じ所で止まる。利用者の報告・実機ログで確定）。
    - ⚠ **`SigLevel = Never` にはしない。** エラーは消えるが、以後この端末は署名を検証せずにパッケージを入れ続けることになる。原因は「鍵束が無い」ことなので、**鍵束を作って条件そのものを壊す**。
    - ⚠ **通信しない。** イメージに `/usr/share/pacman/keyrings/archlinuxarm.gpg` と `archlinux.gpg` が同梱されているので、`pacman-key --init` + `--populate archlinuxarm archlinux` はローカルで完結する（**archlinuxarm を先に**書くこと — ミラーが ALARM なので archlinux の鍵だけでは検証が通らない）。
    - 走らせる場所は 2 つ。①端末が RUNNING になった直後（`TerminalSession.scheduleStartupCommands` の**先頭**。利用者の初期化コマンドがパッケージ導入だった場合、順番が逆だと必ず失敗するため）②`z2gui` の `install_pkgs` / `clean_pkgs`（GUI から先に始めた人はまだ①を通っていない）。
    - 判定はホスト側 (`ProotLauncher.needsPacmanKeyring`) で `etc/pacman.conf` の有無と `etc/pacman.d/gnupg/trustdb.gpg` の有無だけを見る。ゲストを起こさずに決まるので、**済んでいる端末では余計なコマンドが 1 行も出ない**。スクリプト自体も冪等で、pacman が無い distro では即 exit する。
    - **画面の上で走らせる**（バナーではなく端末に出す）。数十秒かかることがあり、黙って待たせると「固まった」と区別が付かない。Ctrl-C で止めても次に開いたときにやり直す。
  - パーミッションは **owner-only** (`setUnixMode(ownerOnly=true)`)。world-writable だと sudo が拒否する。
- `DistroDownloader`: HTTP DL + SHA256 検証、`cacheDir/distros/<id>-<abi>.tgz` にキャッシュ (インストール成功直後に `deleteCachedArchive` で消すため常時ほぼ空)。
- `RootfsCacheCleaner`: 設定「キャッシュ削除」の実体。Android の `cacheDir` はほぼ空なので、実際に容量を食う **rootfs 内の再取得可能キャッシュ** を直接ファイル削除で掃除する。対象は全インストール OS (`filesDir/distros/<id>`) の `var/cache/pacman/pkg`・`var/cache/apt/archives`・`var/cache/apk`・`root`/各ユーザの `.cache`、および `cacheDir` 全体。**稼働中セッションが握る恐れのある `/tmp` やパッケージ本体・設定・ユーザファイルには触れない**。確認ダイアログで「項目名 … サイズ」を 1 件ずつ列挙してから削除する (ワンタップ即削除を廃止)。

### 4.5 ターミナルエミュレータ (`emulator/`)

- `TerminalEmulator`: バイト列を状態機械 (Ground/Escape/CSI/OSC/String) で処理。
  - 文字幅: East Asian Width 対応 (`ambiguousAsWide` 設定で曖昧幅を 2 セル化)。BMP 外 (絵文字 😀 / CJK 拡張) はサロゲートペアを左セル=高サロゲート・右セル (`wideCont`)=低サロゲートに分けて 2 セル格納する。**描画 (`TerminalRenderer.glyphAt`)・選択コピー (`getRangeText`)・行テキスト (`toText`) では左右セルを結合して 1 グリフとして扱う** (0.8.74)。以前は右セルを捨てて高サロゲート単独を描画/出力し、孤立サロゲート＝豆腐(?)になっていた。
  - SGR: 太字/下線/反転/取消線、16/256/RGB(truecolor)。
  - DEC モード: 代替画面、カーソルキー (DECCKM)、**マウスレポート** (X10/Normal/Button/Any × Legacy/SGR/urxvt)、**alternate scroll (1007)**。
  - **代替画面から通常画面へ戻るときは、文字の状態を既定へ倒す (0.8.354)**。DECRST 1049/1047/47 で **SGR (色・装飾) / OSC 8 のリンク / マウスレポート**を既定に戻し、**戻すのは位置 (カーソル・スクロール領域) だけ**にする。⚠ **xterm の DECRST 1049 は DECRC 相当で「代替画面へ入る直前の SGR」を復元する**が、本実装は復元しない。実機の壊れ方がこうだったため: **通常画面で描き続ける対話型 CLI**（代替画面を使わず履歴をスクロールバックに残す作り）**の中から、その CLI の機能で全画面エディタを起こして戻ると、以降の出力が全部下線になる**（利用者報告・0.8.354 で修正）。⚠ **エディタを直に起動して終了しても起きない** — CLI が装飾を出している最中に代替画面へ入るのが条件。経路は 2 つあり **どちらも「抜けたら既定」で消える**: ① 装飾が退避され戻るときに復元される（戻った側は「自分は出していない」つもりなので `\e[0m` を挟まずに描く）、② エディタが装飾を消さずに終了しても誰も戻さない。⚠ **装飾だけでなく色も戻す** — 同じ経路で「全部赤い」も起こり得るのに下線だけ直すと同じ報告をもう一度受ける。⚠ **OSC 8 のリンクも切る** — 画面がまるごと入れ替わるのにリンクだけ跨いで生き残る理由が無く、しかも **SGR ではないので `\e[0m` でも `reset` でも消せない**（利用者に直す手が無い状態が残る）。同じ理由で **RIS (`reset`) でもリンクを切る**。マウスレポートを OFF に倒すのは 0.8.124 からの既存判断で、考え方は同じ。
  - OSC: 7(cwd)/8(hyperlink)/10-12(前景/背景/カーソル色、`?` で query 応答)/52(クリップボード)/palette。OSC タイトルは UTF-8 デコード（日本語タブ名の文字化け防止）。
  - **URL/OSC8 リンクのセルに下線表示**。長い URL は折り返し元の行に wrapped フラグを持たせて検出（タップで開く）。
  - bracketed paste (DECSET 2004) 対応。
  - **問い合わせへの応答 (0.8.391)**: DSR 6 (カーソル位置) / **DA1** (`CSI c` -> `CSI ?62;22c` = VT220 相当 (62) + ANSI color (22) を名乗る) / OSC 10-12 の `?` / Kitty graphics の `a=q`。⚠ **DA1 は必ず返す** — TUI の土台になっているライブラリは端末機能の判定を「機能の問い合わせを先に投げ、DA1 の応答が返った時点で打ち切る」形で書くことが多く (未対応の端末は機能の問い合わせを黙って無視するので、必ず答えが返る DA1 を締め切りに使う)、応答しないと判定が終わらず**ライブラリのタイムアウトまで TUI が起動途中で待たされる**。DA2 (`CSI > c`) と XTVERSION (`CSI > q`) にも **0.8.394 から応答する**（下記）。
  - **CSI はプレフィックス (`?` / `>` / `<`) で振り分ける (0.8.391)**: 終端文字だけで振り分けると、TUI が起動時と終了時に**無条件で送る**次の 3 つが別の意味で実行される。`CSI > 4 ; N m` (XTMODKEYS = 修飾キー報告の指定) が SGR として適用されて**下線が点く**、`CSI > N u` (kitty keyboard protocol の push) と `CSI < u` (同 pop) が SCORC (カーソル復元) として実行されて**カーソルが飛ぶ**。z2term はこれらの機能を持たないので、`dispatchCsiSecondary` で受けて捨てるのが正しい。
  - `cursorKeyBytes`, `encodeMouseEvent`, `resize`(cursor-aware), scrollback。
- `SearchEngine` (M11): スクロールバック全文検索。🔍 → 文字入力 → ↑↓ で前後ジャンプ。CJK は **セル列**でハイライト位置を計算。
  - 検索バーの入力欄は**内蔵キーボード時だけ自前描画** (`SearchQueryField`)。`BasicTextField(readOnly=true)` は OS IME を出さない代わりに**キャレットも出ない**ため、末尾の追記/削除しかできなかった。表示 (`Text`) + 点滅キャレットを自前で描き、キャレット位置 (`searchCursor`) を画面側の状態として持つ。タップ位置→文字位置は `TextLayoutResult.getOffsetForPosition`、キャレット x は `getHorizontalPosition`。**キャレット位置は必ず「そのレイアウト結果が実際に持つ文字列長」でクランプする** — 状態 (`query`) の更新とレイアウト結果の更新には 1 フレームのずれがあり、`query.length` で丸めると空レイアウトに対して `offset(n) is out of bounds` で落ちる (0.8.191 で修正)。内蔵キーボードの ←→ でキャレット移動 (↑=先頭 / ↓=末尾)、BS はキャレット直前を削除 (サロゲートペアは 2 code unit まとめて)。語が枠を超えたらキャレットが見える位置まで `horizontalScroll` を寄せる。**`Text` の末尾に 3dp の余白を入れる** — `horizontalScroll` は内容幅でクリップするので、余白が無いと末尾のキャレット (x = テキスト幅) がはみ出して**文字を打った瞬間に消える** (0.8.192 で修正)。システムキーボード時は従来どおり `BasicTextField` (OS IME 側がキャレットを描く)。
  - **変換中のかなを検索バーにも出す (0.8.275)**。内蔵キーボードの確定文字は `ComposingState.onCommit` から検索語へ入るが、**確定するまでは何も描いていなかった** — 端末では下線付きのプリエディットが見えるのに検索バーだけ無反応で、「内蔵キーボードでは日本語が打てない」ようにしか見えない（利用者の指摘）。`composing.text` を `SearchBar` へ渡し、キャレット位置に**下線付き**で挟んで描く（端末側のプリエディットと同じ見た目・同じ位置付け）。キャレットは変換中の**後ろ**に置く（次の打鍵が入る位置）。⚠ **変換中はタップでキャレットを動かさない** — 描いている文字列に確定前のかなが挟まっているので、タップ位置から検索語側の位置を出しても合わない。⚠ **検索バーの開閉で `composing` を捨てる** — 確定先が端末と検索語で入れ替わるので、跨いで持ち越すと端末へ打っていたかなが検索語に紛れ込む。
- `TerminalScrollbar`: 端末右端の掴めるスクロールバー。**タッチした瞬間から指に追従**させるため、`detectDragGestures` (タッチスロープ超過まで無反応) ではなく `awaitPointerEventScope` の自前ループで扱う。イベントは **`PointerEventPass.Initial` で受けて即 `consume`** する: Main パスまで残すと下に重なる `TerminalInputView` (AndroidView) に配られ、View 側が「処理した」として change を consume するため、`drag`/`detectDragGestures` は「他に取られた」と判断して即中断する。**移動量 `positionChange()` は `consume()` する前に読むこと** — consume 済みの change に対しては `Offset.Zero` を返す仕様なので、先に consume するとつまみが 1px も動かない (0.8.190/0.8.191 の「掴めるが動かない」の真因。0.8.192 で修正)。`pointerInput` の key は `Unit` 固定で、変化する値 (`scrollbackSize` / つまみ寸法) は `rememberUpdatedState` 経由で読む — key に `scrollbackSize` を入れると**端末出力のたびに検出器が作り直され、掴んだ指が外れる**。ドラッグ中のつまみ位置はローカル state に持ち、`scrollOffset` (StateFlow) → recomposition の往復を待たずに描く。当たり判定は見た目 (幅 8dp) より広い 32dp × 上下 +10dp。
- `TerminalBuffer`/`TerminalRow`/`TerminalCell`/`SgrAttribute`: セル格納とスクロールバック。
- `TerminalColors`/`AvailableThemes`: 9 テーマ (ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night / Catppuccin Mocha / Catppuccin Latte / Monokai)。

#### OSC の終端は ST の 2 バイト目まで消費する (0.8.361)

OSC (`ESC ]`) の終端は **BEL (`0x07`) か ST (`ESC \` = `0x1B 0x5C`)**。⚠ **ST は 2 バイトなので、
`\` まで消費してから GROUND へ戻す**。`processOsc` は ESC を見た時点で終端扱いにして GROUND へ
戻していたため、**続く `\` が通常の文字として画面に書かれていた**。

- 症状: **OSC 8 でリンクを張る CLI を動かすと画面に `\` が散る**。しかもその `\` を書く時点では
  `currentLink` が生きているので、**漏れた `\` のセルにリンクが付く**。
- ⚠ この取りこぼしは `AltScreenExitTextStateTest.osc8Link_isClearedOnAltScreenExit` を
  **赤いまま放置していた真因**でもある。「代替画面を跨いでリンクが残る」ように見えていたが、
  実際は **(0,0) に漏れた `\` が居座っていた**だけで、0.8.354 のリセット自体は効いていた
  (同クラスの下線・色のテストは代替画面の中で `\e[0m` を送る書き方だったので、
  **リセットが動かなくても通ってしまい**、穴を隠していた)。
- 終端不正 (`ESC` + 非 `\`) は `processString` と同じ xterm 流儀で、その場で打ち切り続くバイトを
  ESCAPE として再解釈する (捨てると後続のシーケンスが 1 つ消える)。
- 回帰は `AltScreenExitTextStateTest` の ST 完全消費 / BEL 終端 / 終端不正の 3 ケースで固定。

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
- 配置と同時にカーソルは画像の幅セルぶん右へ進める (改行は TUI が `\n` で送る前提)。kitty の `C=1` (cursor movement policy) と同じ動きで、`C=` は読まずに常にこう振る舞う
- 文字書込み (`setChar`) / `clear` / `resize` (列幅縮小で anchor + width が範囲外) が起きると、**セル範囲に被さる placement だけ**を除いて invalidate する (他の placement は残す)
- 行コピー (`TerminalRow.copyFrom`) では画像も一緒に運ぶので、`DECSTBM` 領域内スクロール等で画像が一行ずれてもキャンバスに残る
- `TerminalBuffer` に**画像キャッシュ** (`imageId → Bitmap`) を持ち、`a=T`/`a=t` で登録、`a=p` で参照、`a=d,d=I`/`a=d,d=i` でエントリも削除する

**Z-index による 2 層描画**: `zIndex < 0` の placement は**テキストの下層** (Pass 2.7、文字を画像の上に読みやすく重ねる)、`zIndex >= 0` は**テキストの上層** (Pass 3.5、アイコン重ね・吹き出し風)。同 z 内の順序は追加順 (= 後勝ち)。

**Unicode placeholder (virtual placement)**: `a=p,U=1` / `a=T,U=1` は image を grid (`c=N` × `r=N` 分割) として `TerminalBuffer.virtualPlacements` に登録するだけで cursor を動かさない。実際の描画位置は本文に書かれる placeholder セル (`U+10EEEE`) と、直後に並ぶ最大 3 個の **combining diacritic** (Kitty 固定 297 要素表で row / col / placement id 下位 8bit をエンコード) で決まる。placeholder セルは `TerminalCell.placeholder: PlaceholderRef?` にメタを持ち、image id は fg truecolor (`\e[38;2;R;G;B`) から 24bit ＋ underline color の R 値から上位 8bit で計 32bit を組む。Renderer は Pass 2.7 / Pass 3.5 で row 内のセルを走査し、placeholder ごとに `virtualPlacements` を逆引きしてタイル領域 (`srcCol/widthCells, srcRow/heightCells`) を `drawBitmap` の srcRect→dstRect で 1 セル矩形へ切り出す。placeholder セルは `TerminalRow.toText` / `TerminalBuffer.getRangeText` でコピー時に空白へ置換する (孤立サロゲートの混入防止)。仕様: <https://sw.kovidgoyal.net/kitty/graphics-protocol/#unicode-placeholders>

**`z2-img` — 画像を出すコマンド (0.8.495)**: 端末に絵を出す手段は `KittyGraphicsParser` が既に持っていたのに、**それを使うコマンドが無かった**ため「z2term では画像が見られない」状態だった。⭐ **アプリ側には 1 行も足していない** (`qr.sh` が先に同じ出し方をしている)。`Z2ApiScript.img` は純粋なシェルスクリプトで、(1) ヘッダだけ読んで画素数を測り (PNG=IHDR / JPEG=SOF 走査 / GIF / BMP / WebP の VP8・VP8L・VP8X)、(2) 端末の桁数に収まる `c=`/`r=` を決め、(3) base64 を 4096 バイトずつ `ESC _ G a=T,f=100,…` で流す、だけをする。⚠ **`c=`/`r=` は必ず両方渡す**。省けばエミュレータが実セル寸法から正しく自動算出するが、こちらは「絵の下へ何行送るか」が決められなくなる (カーソルは幅ぶん右へ進むだけで行は動かない仕様)。⚠ **`C=1` (cursor movement policy) も必ず送る (0.8.497)**。z2term は元から行を動かさないので自前では要らないが、`ssh` の先の kitty 流儀の端末は**カーソルを絵の最終行まで動かす**ため、付けないと後続の改行がまるごと余白になり、絵が画面の外へ流れる。`qr.sh` の `show_inline` も同じ (**片方を直すときは両方を揃える**)。⚠ 縦横比はセルの実寸を知らないので **1:2 の仮定** (`Z2_IMG_ASPECT`、既定 0.5) で出す。端末に px/セルを問い合わせる手段 (`CSI 16 t`) は実装していない。⚠ **既定では tty にしか書かない** — パイプの先に APC を流すと、受け側には壊れたバイト列としか見えず、画像だった手掛かりが残らない (`-f` で通せる)。⚠ 対応端末かどうかは**外から判別できない** (`ssh` の先が kitty 対応かは分からない) ので、実行時に止めるのではなくヘルプと docs で先に伝える方針にした (`qr.sh` の `-t` と同じ立場)。

**復号時の画素上限 (0.8.495)**: `KittyGraphicsParser.decodeImage` は `inJustDecodeBounds` で寸法だけ先に読み、`MAX_DECODED_PIXELS` (400 万画素) を越えるものは `inSampleSize` で間引いて復号する。スマホのカメラで撮った 12MP の写真は ARGB_8888 で約 50MB あり、`imageCache` は原画像を持ち続けるので、数枚出しただけでアプリが落ちる。⚠ **拒否ではなく間引き**にする — 画面に出るのはたかだか数百 px なので見た目は変わらないが、拒否すると「写真は出せない」という別の欠落になる。⚠ **セル数は間引く前の画素数から出す** (`Decoded.srcWidth` / `srcHeight`)。間引いた値で数えると、`c=`/`r=` を省いて送ってきた相手の絵が勝手に小さくなる。

**チャンク連結が効いていなかった (0.8.496)**: `TerminalEmulator` が APC の開始ごとに `KittyGraphicsParser.reset()` を呼んでいたため、`m=1` で続くチャンクが来るたびに**蓄積した payload とヘッダが消えていた**。チャンクは APC を 1 個ずつ分けて送る形なので、開始のたびに全部消すと最後の断片しか残らない。`a=` も失われて既定の `T` になり、断片は PNG として復号できず `q=2` で静かに捨てられる — つまり **1 個の APC (4096 バイト) に収まらない画像は 1 枚も出なかった**。⚠ `qr.sh` の QR は 1 チャンクに収まるので動いており、そこだけ見て「出せている」と思われていた。⇒ 本文バッファだけを空にする `beginSequence()` を足し、シーケンス開始ではそちらを呼ぶ。`reset()` は蓄積ごと捨てる用途で残す。⚠ **パーサ単体のテストは連結を通っていた** (`reset()` を挟まないため)。壊れていたのは呼び出し側なので、`KittyGraphicsParserTest.beginSequenceKeepsHeaderAndPayloadAcrossChunks` は**エミュレータと同じ順序** (開始ごとに `beginSequence()`) で回帰を固定する。

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

##### 使い方 (Tips) — 見えない操作を設定で見せる (0.8.399)

⚠ **ダブルタップ・長押し・フリックは、画面に何も出ない。** 貼り付け履歴 (📋 のダブルタップ)、
タブを閉じる (ダブルタップ)、並べ替え (長押し + ドラッグ)、パッド (ESC の上下フリック)、
単語削除 (⌫ の左右フリック) は**どれも入口が見えない**ので、知らない人には存在しないのと同じ。
利用者の言葉では「**誰もわからない**」。

- 置き場は設定の **「開発者向け」と「このアプリについて」の間** (`SettingsGroup.TIPS`)。既定は
  **閉じた状態** — 見出しと説明で「何かある」ことは伝わるので、毎回開いて設定の上を長くしない。
- 1 件 = 「操作」の見出し + 「何が起きるか」の本文。**設定 (トグル) を混ぜない** — 読み物として
  上から読めることに価値がある。見た目は他の設定と同じ `Section` を使う (Tips だけ別の意匠に
  すると設定の中に別のアプリが挟まったように見える)。
- ⛔ **持っていない機能を書かない。** 1 つでも「書いてあるのに効かない」があると Tips 全体が
  信用されなくなる。⚠ 実際、`Ctrl+T` でスクロールという話が候補に挙がったが**実装に無かった**
  (全画面 TUI を指で動かすのは 0.8.393 の alternate scroll)。**載せていない。**
- 載せる 8 件は利用者が 1 件ずつ選んだ: ツールバーのダブルタップ (**個々のボタンを列挙せず
  「もう一段ある」とだけ書く** — 1 つ分かれば残りは自分で試す、という判断) / タブを閉じる /
  タブの並べ替え / ESC の上下フリック / ⌫ の左右フリック / GUI 内のスクロール (2 本指。
  拡大中は 2 本指がパンになるので 3 本指) / `z2term` でコマンド一覧と `--help` (0.8.401。当初は「`z2` + Tab で補完」と書いていたが、⚠ **補完はシェルと補完設定に依存する**ので、確実に一覧が出る既存コマンドへ変えた。`z2term` は `z2help` の薄いエイリアスとして `ProotLauncher.ensureZ2HelpScript` が launch 毎に配置している) /
  マクロは AI に書いてもらえる (リマインダー・RSS・知らない番号の記録など、**アプリの機能では
  なくマクロとして作るもの**の例を添える)。

#### DA2 / XTVERSION — 「型と版は何か」に答える (0.8.394)

0.8.391 で DA1 に答えるようにしたが、**DA2 (`CSI > c`) と XTVERSION (`CSI > q`) は受けて捨てていた**。
どちらも「この端末は何者か」を尋ねるもので、答えない端末も普通にあるため待ち続ける実装は稀だが、
**答えれば「知らない端末」と即断してもらえる**ぶん判定が早く終わる。

| 問い合わせ | z2term の応答 | 中身 |
|---|---|---|
| DA2 `CSI > c` | `CSI > 1 ; <versionCode> ; 0 c` | 型 = **1 (VT220)** / ファームウェア版 = versionCode / ROM カートリッジ無し = 0 |
| XTVERSION `CSI > q` | `DCS > \| z2term(<versionName>) ST` | 名前と版を**文字列で**。DA2 が数値なのに対しこちらは自由書式 |

- ⭐ **他の端末を騙らない。** DA2 で xterm を名乗る (`Pp = 41`) 手もあるが、**持っていない機能を
  前提に話しかけられる**ので採らない。0.8.391 で踏んだ「TUI が無条件に送る XTMODKEYS を
  取り違えて下線が点く」と同じ筋で、**名乗った機能は要求される**。DA1 の `?62`(VT220 相当) と
  揃えて **1 = VT220** にしておけば、名前で機能を決める実装からは素通りされて判定は DA1 に戻る。
- **パラメータが 0 か省略のときだけ答える**（DA1 と同じ）。`CSI > 1 c` のような別用途には答えない。
- ⚠ **`<` プレフィックスには答えない。** `CSI < u` (kitty keyboard protocol の pop) と同じ経路に
  載っているので、終端文字だけで判断すると `CSI < c` にまで応答してしまう。
- 版数は **`BuildConfig` から渡す**（`TerminalSession`）。エミュレータ本体を Android に
  依存させないため、`TerminalEmulator` は `versionName` / `versionCode` を受け取るだけにする
  （ユニットテストが JVM だけで回る作りを崩さない）。

#### alternate scroll (DECSET 1007) — マウスレポート無しの代替画面をスワイプで動かす (0.8.393)

**症状**: 代替画面を使うが**マウスレポートは有効化しない** TUI (全画面の pager / エディタ / 対話型 CLI の全文表示 overlay など) では、**スワイプが完全に無反応**だった。前節の振り分けはすべて `mouseEnabled` が前提で、そこから外れると scrollback フォールバックに落ちるが、代替画面は `scrollbackSize == 0` なので何も起きない。この種の TUI はホイールを受け取る手段を持たないので、**端末側がスクロール手段を用意しない限り指では動かせない**。

**直し方**: xterm の alternate scroll (DECSET 1007) を実装した。**代替画面表示中のホイール (本アプリではスワイプ) を、カーソルキー上下として PTY へ送る**。

| 条件 | 送出 |
|---|---|
| 代替画面 + `mouseEnabled == false` + `alternateScrollMode == true` | 指を下 (過去を見たい) = カーソルキー **上** / 指を上 = カーソルキー **下** |
| 上記以外 | 従来どおり (wheel 送出 / scrollback フォールバック) |

- **既定 ON** (`TerminalEmulator.alternateScrollMode = true`)。xterm の `alternateScroll` リソースを true にした状態に相当し、現代のターミナルエミュレータの多くが同じ既定を採る。1007 を**送ってこない**全画面 TUI (大半がそう) でも効かせるための判断で、明示的に `DECRST 1007` を送る TUI (ホイールを自前で扱うもの) だけが従来動作に戻る。
- **`ESC[?1007h` を明示的に送る TUI が実在する**。代替画面の overlay を開くときに `1049h` と `1007h` をまとめて送り、フッタに「↑/↓ でスクロール」と出すような作りで、この実装が無いと**その overlay だけスワイプで動かない**。
- **行数換算は 1 行 = 1 個**。指の累積 dy が `lineHeight` を超えるごとにカーソルキーを 1 個送るので、指の移動量と TUI 側のスクロール量が 1:1 になる (wheel 経路の `MOUSE_WHEEL_STEP_PX` = 40px ノッチ換算とは別勘定)。まとめて 1 回の `writeBytes` で送り、1 イベント / 1 フリングフレームあたり `ARROW_SCROLL_MAX_ROWS (=24)` 行で頭打ちにする (勢いよく振ったときに矢印キーが数百個流れ込んで描画が追いつかなくなるのを防ぐ)。
- **フリング (慣性) も同じ経路**。`flingRunnable` が「代替画面 + `mouseEnabled` なら wheel」「そうでなく alternate scroll が有効ならカーソルキー」「どちらでもなければ scrollback 慣性」の 3 分岐になる。
- **カーソルキーのバイト列は DECCKM に追従する** (`cursorKeyBytes`)。全画面 TUI は application cursor keys (`ESC O A`) を使うものが多く、`ESC [ A` 固定だと矢印として認識されない。
- **代替画面から通常画面へ戻るときは既定 (ON) へ戻す**。`DECRST 1049` だけ送って `DECRST 1007` を送り忘れる TUI が居ると、次に代替画面を使う TUI でスワイプが死ぬため (`resetTextStateOnPrimaryReturn` でマウスレポートを OFF に倒すのと同じ考え方)。RIS (`reset`) でも既定へ戻す。
- **通常画面ではこの読み替えを行わない**。シェルのプロンプトでスワイプするたびにコマンド履歴が呼び出されてしまうため。⚠ **通常画面のまま全画面相当の描画をする対話型 CLI** (代替画面を使わず、`ESC[1;1H` + `ESC[J` で毎回描き直し、`DECSTBM` + `RI` で履歴を画面上部へ押し込む作り) は**そもそも端末の scrollback を育てない**ので、端末側からは遡れない — その種の CLI が自前で持つ全文表示 overlay を開いてもらうことになる (そこは代替画面なので上の経路で動く)。
- テスト: `AlternateScrollModeTest` 6 ケース (既定 ON / `1007h`・`1007l` の切替 / `1049h` + `1007h` の同時適用 / 通常画面復帰で既定へ / RIS で既定へ / `cursorKeyBytes` の DECCKM 追従)。
- 仕様: xterm `ctlseqs` の `Ps = 1 0 0 7` (Enable Alternate Scroll Mode)。

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
- `clipboard/ClipboardHistoryStore` (object): システムクリップボードは 1 件しか持てないので、変化を拾って履歴 (最大 50 件 / `filesDir/clipboard_history.json`) に貯める。取り込み経路は 4 つ: ①`OnPrimaryClipChangedListener` (前面中の変化)、②`MainActivity.onResume`、③`MainActivity.onWindowFocusChanged(true)`、④キーボードの 📋 パッドを開いたとき (`KeyboardPad` → `ensureLoaded` + `captureCurrent`)。Android 10+ の「クリップボードを読めるのはフォーカスのあるアプリだけ」制限は**ウィンドウフォーカス基準**で、`onResume` の時点ではまだフォーカスが確定せず空が返る端末があるため、③が無いと「他アプリでコピー → 戻る」を取りこぼす。裏で複数回コピーされても拾えるのは最後の 1 件だけ (OS の仕様上の限界)。重複は `record` が先頭一致/LRU で潰す。
  ⚠ **履歴の実体はこの object ただ 1 つ** (0.8.313 で 1 本化)。それまではキーボードのパッド用に `ui/terminal/keyboard/ClipboardHistoryStore` という同名の別 object があり、**同じ `filesDir/clipboard_history.json` を別のキー (`entries` / `items`) で丸ごと上書きし合っていた**。片方が保存すると相手からは「中身の無いファイル」に見えるため、**パッドの履歴はアプリを起動し直すたびに空から始まり、端末でコピーした内容も現れなかった** (利用者の報告)。入口 (シート / パッド) は増やしてよいが、**ストアは増やさない**。読み込みは旧 `items` キーも拾って既存の履歴を引き継ぐ。
  ⚠ **機微印 (`android.content.extra.IS_SENSITIVE`) の付いたクリップも取り込む (0.8.314)。** 0.8.313 までは丸ごと捨てていたが、その結果**いちばん貼り付けたいもの (パスワード) だけが履歴に出ず**、「コピーしたのに入っていない」状態になっていた (利用者の指摘)。取り込んだうえで 3 つで縛る: ①**30 秒 (`SENSITIVE_TTL_MS`) で履歴から自動的に消える** — 貼るには足りて、放置はされない長さ。②**同じ値が OS のクリップボードにまだ載っていれば、そこからも消す** (`clearPrimaryClip`)。履歴だけ消しても他アプリから貼れてしまうため。⚠ **値が変わっていたら触らない** — 後から別のアプリがコピーしたものを奪うことになる (同梱サンプル `otp-clip.sh` と同じ作法)。Android 10+ は前面でないとクリップボードを読めないので、**読めなかったときも触らない**側に倒す。③**ディスクに書かない** — 30 秒で消えるものを永続化すると、アプリを殺した瞬間だけ残るという最悪の形になる。UI では履歴シートに「🔒 機微なコピー — 30 秒で自動的に消えます」、キーボードのパッドでは行頭の 🔒 で示す (黙って消えると壊れて見えるため)。
- `SessionStore`/`SessionManager` (M11): タブ構成 `{id,label,distro,cwd}` + activeId を DataStore に保存する（書き込みのみ）。**0.8.70 で起動時の自動復元を無効化**＝起動の度に複数タブが開く挙動を避けるため、`ensureFirst` は常に新規 1 タブだけを開く（ユーザー要望）。`save` は将来の復元 UI / デバッグ用に残すが読み戻し経路は持たない。**cwd は OSC7 で捕捉**（`ensureOsc7CwdConfig` が bash/zsh のプロンプトフックで OSC7 を吐かせる）。

### 4.7 通信チャネル (`channel/`)

- `ProcessChannel` (interface): `reader`/`writer`/`isAlive`/`exitCode`/`resize`/`close`。
- `LocalPtyChannel`: PtyProcess をラップ (ローカル proot)。
- `SshChannel`: JSch でリモート接続。`shell` チャネル + ポート転送、host key 検証 (`KnownHosts`/`HostKeyVerificationDialog`)、鍵は Keystore で暗号化 (`KeystoreCrypt`)。
- `SshSessionFactory` / `SshLink`: 認証・known_hosts・踏み台をまとめて 1 本の経路にする入口。**シェル・SFTP・サービス経路・常駐トンネルはすべてここを通る**（通信量の上限もこの 1 か所で見る）。`SshLink` は経路まるごとを持ち、`close()` で**奥から順に**畳む（手前を先に切ると奥のセッションが宙に浮く）。
- `SshHop` / `JumpProxy`: 踏み台（`ssh -J`）。詳細は §6.3 の「踏み台」。
- `PortForwarding`: `-L` / `-R` を実際に張る 1 か所。`SshChannel` と `TunnelManager` が共有する。
- `SshProfile`/`PortForward`/`SshHop`: DataStore (`z2term_ssh`) に JSON 永続化。

### 4.8 設定 (`settings/AppSettings.kt`)

- DataStore (`z2term_settings`) を `Snapshot` データクラス + `Flow` で公開。各 setter は suspend。
- 項目は[§7](#7-設定項目)。

### 4.9 常駐サービス (`service/TerminalService.kt`)

- `foregroundServiceType=specialUse`。`start`/`detach`(常駐解除のみ・セッション維持)/`stop`(全終了)。
- `PARTIAL_WAKE_LOCK`、通知 (`ic_notification` = 透過マスクの **`>_`**、タップで復帰 / 停止アクション)。**小アイコンは色が使えない (不透明部分が一律にティントされる) ので、形でしか区別できない**。「Z2」の 2 文字はステータスバーの実表示 (24px 前後) で潰れて読めず、0.8.196 でランチャーと同じ `>_` にしたところ**他の主要ターミナルアプリと同じシルエット**になったため、0.8.200 でいったん **Z 1 文字**へ寄せた。**0.8.511 で `>_` へ戻した (利用者の要望)** — 見分けやすさより「何のアプリか一目で分かる」を取る判断で、ランチャーと同じ記号に揃える。⚠ **山括弧を塗りパスで描かないこと**。先端が痩せてこの大きさでは潰れる。線幅 3.2 + 丸い端の**線**で描き、要素は 2 つだけ・端の余白は 4 前後空ける。SAF プロバイダのルートアイコンとタイルにも同じものを使う。

### 4.10 ファイル連携 (`saf/Z2TermDocumentsProvider.kt`)

- `DocumentsProvider` (authority `<applicationId>.documents`、`permission=MANAGE_DOCUMENTS`)。
- 公開ルート: **ホーム = `shared_home`** (端末の `/root` と同一実体) + 各 distro の rootfs(`/`)。
- traversal 防止: 許可ルート `[shared_home, distros]` 配下のみ。R/W/作成/削除/リネーム対応。

### 4.11 UI 詳細 (`ui/`)

- `terminal/TerminalScreen.kt`: 全体レイアウト。TopBar / TabBar / 描画領域 / キーボードトグル / キーボード領域。`KeyboardMode = CUSTOM | SYSTEM`。**横画面**は `LocalView.OnLayoutChangeListener` で向きを検知し、`landscapeKeyboardPosition`/`Width`/`Height` 設定に従って Row レイアウト (`SideKeyboardColumn`) に切替。`landscapeScaledStyle()` で keyHeight/font が横画面高さに比例拡縮。
  - **キーボードトグルバー (`KeyboardToggleBar`)**: タップでキーボード表示/非表示を切り替える 22dp 高の細いバー。**キーボードの上に配置**（端末タブ・GUI タブ共通）。設定 `keyboardToggleBar`（既定 ON）で表示/非表示を選べ、**OFF にするとバーを出さず ⌨ ツールバーボタンのダブルタップで表示/非表示を切り替える**（0.8.145。単タップ=キーボード切替は従来どおり。0.8.144 で一時キーボードの下へ移したが使いにくく上へ戻し、代わりに設定＋ダブルタップ方式を追加）。ラベルは表示/非表示どちらの状態でも「キーボード」を出す（`▴ キーボード` / `▾ キーボード`。従来は非表示側が `▾` のみ＋16dp 高で文字が縦に見切れていた）。`.clickable` の touch slop (約 8dp) だけではフリック入力中に指がバーへ掠めて誤って非表示が発動することがあったため、自前の `pointerInput` ジェスチャで **down からの累積移動が 24dp を超えたら onToggle を抑制**し、純粋なタップ（24dp 未満）でのみトグルするように変更（0.8.109。従来は touch slop 越えで `.clickable` が発火しないものの、短いドラッグが偶発的にタップ判定に流れて非表示になっていた）。
  - **ツールバー ⌨ の 3 段操作（0.8.428）**: 単タップ=キーボード方式、ダブルタップ=表示/非表示、**トリプルタップ=その場のサイズ調整帯**。`multiTapClickable` は 3 回目の待ち時間が終わるまで 1/2 回の動作を確定しないため、トリプルの途中で方式切替や収納が起きない。調整帯は現在の向きの高さ、横画面サイド配置では幅を既存設定へ書き戻す。`keyboardToggleBar=false` でも使える。⭐ **0.8.430 で「使い方のヒント」にも載せた** — ⌨ の 3 段目は**画面のどこにも出ていない**ので、知らなければ一生見つからない（他のヒントと同じ理由でここに置く）。
  - **サイズ調整帯は高さと幅の 2 本にする（0.8.431・利用者の指摘）**: ⛔ **横画面のサイド配置では幅しか出していなかった。** 高さの設定 (`landscapeKeyboardHeightDp`) は前からサイド配置にも効いていたのに、**この帯からは届かなかった**だけ。下配置には幅の設定自体が無かった。⇒ **帯は必ず高さ・幅の 2 軸を出す** (`SizeAxis` のリストを受ける形に変更)。下配置の幅は新設した `portraitKeyboardWidthPercent` / `landscapeBottomKeyboardWidthPercent`。⭐ **幅だけ dp ではなく %。** 画面幅は機種で違うので dp だと上限が機種依存になり、「いっぱいに広げた」が値から分からない。100% = 従来どおり画面いっぱいで、狭めると中央寄せになる。⚠ **縦と横で別々に持つ** — 高さと同じ理由（横は画面が倍近く広く、同じ % でも指の届き方が別物）。⚠ 帯は**キーボードの外**に置く（下記）。
  - **調整スライダーを枠の中に置かない（0.8.431・利用者の指摘）**: GUI タブの横画面ではキーボード帯を**緑枠の内側のオーバーレイ**として描いていたので、サイズ調整帯もその中に居た。⇒ **スライダーを動かすと枠の寸法が変わり、その中に居るスライダー自身の幅まで変わって掴み直しになる。** ⛔ **サイズを変える道具を、そのサイズで寸法が決まる入れ物に入れてはいけない。** ⇒ `GuiKeyboardPanel` から帯を外に出し、**タブ画面の一番下・画面幅いっぱい**（枠の外）に置く。端末タブは元から枠の外なので置き場は変えない。⚠ 下配置キーボードを % で狭められるようになったので、**帯を「キーボードの中」に入れるのも同じ理由で不可**（幅を動かすと帯が伸び縮みする）。
  - **GUI キーボード配置（0.8.428）**: **縦画面の独自キーボードは緑枠の外の兄弟に置き**、GUI 枠を残り高さに縮める。キーボード高さが枠高さを超えても Compose の制約で下段が欠けない。`onSizeChanged` が縮んだ枠内寸を測るので、ローカル GUI の解像度も従来の再ネゴ経路で追従する。**横画面は GUI が潰れるため従来の枠内オーバーレイを維持**する。OS IME も従来どおりオーバーレイ。
  - **ツールバー (`ReorderableToolbar`)**: 📋貼付 / 📜コマンド / 💡画面消灯ロック / 🔒バックグラウンド常駐 / 🔍検索 / ⌨キーボード切替 を `ToolbarItem` のリストで描く。**通常タップ=動作、長押しドラッグで並べ替え** (`detectDragGesturesAfterLongPress` + 隣との中心越えで `order` 入替)。長押し中は `ToolbarTooltip` で簡易説明を Popup 表示。並びは `AppSettings.toolbarOrder` (カンマ区切り id) に永続化し、`mergeToolbarOrder` で既存順とマージするのでボタン追加/削除でも壊れない。🔒常駐は既定で 💡 の右。GUI タブ (`GuiTopBar`) も同 `ReorderableToolbar` を共有 (検索なし・📋/📜 は keysym 橋渡し)。
  - **🔒 常駐トグルは常駐サーバー稼働中はロックする** (0.8.204)。常駐サーバー (`ServerDaemonService`) が動いている間はプロセスが生き続けるため、🔒 を OFF にしてもセッションは消えない (最近履歴からスワイプしてもプロセスは死なない)。そこで `ServerDaemonManager.isRunning` を 1 秒周期でポーリングし、稼働中は 🔒 を **ON 表示のまま薄く (`ToolbarChip(dimmed=true)`) してトグル不可**にする。この間タップするとトグルの代わりに `ResidentActionDialog` を開き、「常駐に閉じ込められない」ための出口を出す — **セッションだけ終了** (`SessionManager.resetToInitial`。常駐サーバーはそのまま) / **全部停止して終了** (`ServerDaemonService.stop` + `SessionManager.shutdown` + `TerminalService.stop` + `finishAndRemoveTask`＝タスクキル相当)。ロック条件を常駐サーバーに限るのは、検知系 FG サービス (システムイベント/通知) は WakeLock を握らず 🔒 の「CPU を起こし続ける」独自価値が残るのに対し、常駐サーバーは同じ WakeLock/WifiLock を握るため 🔒 が完全に無意味になるから。端末タブ (`TopBar`)・GUI タブ (`GuiTopBar`) 共通 (`keepAliveToolbarItem`)。**0.8.211 で設定側にも同じロックを入れた**: ツールバーから🔒を隠していると設定 › ツールバーの代替トグルが唯一の操作口になるが、そこがロック対象外だったため「常駐中はセッションを終了できない」状態が残っていた (実機で指摘)。`ToggleField` に `locked`/`onLockedTap` を足し、`ServerDaemonManager.isRunning` の 1 秒ポーリングで薄く+トグル不可にし、タップで同じ `ResidentActionDialog` を開く (`stopEverythingAndQuit` は `internal` へ)。**0.8.225 で「全部停止して終了」が `SystemEventService.stop` も呼ぶようにした**: **フォアグラウンドサービスは 1 つでも残っているとプロセスが死なない**ため、システムイベント検知を ON にしていると押してもアプリが閉じなかった (実機で指摘)。FG サービスを増やしたら必ず `stopEverythingAndQuit` へ足すこと。設定 (`systemEventCaptureEnabled`) は触らないので、次にアプリを開けば検知は再開する (「今回は全部止めたい」と「検知をやめたい」は別の意思なので、設定を書き換えない)。
  - **⚙設定は並べ替えにも非表示指定にも入れず、ツールバーの右端に固定**する (0.8.194)。`ReorderableToolbar` の外に `ToolbarChip` を 1 個直接置く形で、他をどう並べ替えても・どれだけ隠しても位置が動かない。
  - **横画面はツールバーとタブを縦レールへ (`TabScaffold`・0.8.431・§12-7)**: ⛔ **縦でも横でも上に 2 段積んでいた。** 横画面は画面高さが 350〜400dp しか無く、**48dp + 約 40dp = 高さの 1/4** をここで失う（キーボードを出すと本体がほとんど残らない）。⇒ **横画面では幅 76dp の縦レールにして左右どちらかへ寄せる** — 横は幅が余って高さが足りないので、余っている方から取る。⭐ **どちら側かは設定を増やさず `landscapeKeyboardPosition` から決める**（キーボードが左ならレールは右。両方が同じ側に来ると片側だけ重くなる）。⚠ **並べ替えは軸を差し替えるだけにする** — `ReorderableToolbar` / `TabBar` / `TabChip` に `vertical` を足し、実測サイズは**主軸ぶんだけ**覚え (`it.height` / `it.width`)、ドラッグ量も `amount.y` / `amount.x` を選ぶ。入れ替えの式（隣の中心を越えたか）は軸に依らないので**そのまま**使える。⚠ **タブ名はレール幅に入らない**ので縦では 6 字で切る。全名と実行エンジンは**長押しのポップアップ**で読める（切り詰めの逃げ道を必ず残す）。⚠ **`ToolbarTooltip` は画面内へクランプする位置決めに変えた** — 中央揃えのままだと、画面端に居るレールのチップから出した説明が画面外へ出て読めない。⚠ ⚙ はレールでも**ツールバーの末尾に固定**（横並びのときの右端と同じ約束。並べ替えにも非表示指定にも入れない）。
  - **レールは 1 列ではなく 2 列にする（0.8.433・利用者の指摘）**: ⛔ **0.8.432 はツールバーとタブを 1 列に縦積みしていた。** 「縦画面では 2 段なのに横画面だけ 1 段」で、**境目が分からず狭い**。⇒ **縦画面の 2 段をそのまま 90 度倒した 2 列**にする（ツールバー列 48dp + タブ列 44dp）。列ごとに枠線を引いて境目を出す。⭐ **並びは縦画面と同じ関係を保つ** — 本体から遠い側がツールバー、本体に接する側がタブ（縦画面の 上=ツールバー / その下=タブ）。⭐ **タブ名は縦書き**（1 文字ずつ改行して積む）。⛔ **回転させない** — 首を傾けないと読めず、日本語のタブ名が寝る。等幅フォントなので 1 字ずつ折ると字送りが揃う。8 字を超えたら「…」を積んで打ち切り、全名は長押しのポップアップで読む。⚠ **縦書きは高さを食う**ので、字数の上限は「一度に見えるタブの枚数」の上限でもある。⭐ **左右は設定で選ぶ（`landscapeRailPosition`・既定は左）** — 0.8.432 では `landscapeKeyboardPosition` から導いていたが、**キーボードを下に置く人にはレールの側を決める手段が無かった**。⚠ 設定は「表示 › ツールバー」に置く（キーボードの設定ではないので、キーボード群に混ぜない）。⚠ **タブ列の下端に置く `+` / `🖥` は縦に積む**（0.8.434。44dp の列に横 2 個は入らず、2 個目の GUI ボタンが列からはみ出して見えなかった）。
  - **出すボタンをユーザーが選べる** (0.8.194)。非表示 id は `AppSettings.toolbarHidden` (カンマ区切り) に永続化し、設定 › 表示 › **ツールバー**で切り替える。ボタンの一覧 (id / 代表アイコン / 説明 / 隠せるか) は `ui/terminal/ToolbarButtons.kt` の `CATALOG` に集約し、表示側と設定画面で同じ定義を共有する。**⚙ は `canHide = false`** — 隠せると設定画面へ戻る手段が無くなるため。**並べ替えの保存値には隠しているボタンの id も残す** (`persistOrder`): 表示中のものだけを保存すると、隠して出し直したときに末尾へ飛んでしまう。
    隠されたボタンのうちトグル系 (🔅 画面消灯ロック / 🔒 常駐) は**ツールバー以外に操作する場所が無い**ので、隠しているときだけ同じ「ツールバー」セクション内にトグルを出す。機能追加でボタンが増えても各自の画面は増やさない、という方針の受け皿でもある。
    **`CATALOG` の代表アイコンは、ツールバーが実際に描く字をそのまま置く**（状態で変わるものは OFF 側 = 🔅 / 🔓 / ⚪）。設定画面は「実物のボタンが並ぶ」ことが分かりやすさの根拠なので、別の字を置くと現物と一致しなくなるうえ、**色付き絵文字の列に 1 つだけ字形の細い記号が混じって揃わなく見える**。0.8.242 で ⏺ を実際に描いている ⚪ に直した（ログ ⏺→⚪。ツールバー側は録画中 🔴 / 停止中 ⚪）。
- `terminal/TerminalRenderer.kt`: ネイティブ Canvas に **セル単位 drawText** (advance≠cellW のサブピクセル誤差累積を回避)。背景→選択ハイライト→文字→カーソル→選択ハンドルの順。
  - **タブの単タップは待たずに切り替える** (`TabChip`、0.8.245)。⚠ `combinedClickable` に `onDoubleClick` を渡すと、Compose は**「2 回目が来ないこと」を確かめるまで `onClick` を出さない**。つまり `doubleTapTimeoutMillis` (端末の設定。多くは 300ms) が**そのままタブ切替の待ち時間**になり、「押しても何も起きない時間」が毎回挟まる (実機で指摘。描画が重いわけではない)。`clickable` + **自前の 2 回目判定**に替え、1 回目で即 `setActive` する。
    - 判定の時計は `SystemClock.uptimeMillis()` (単調増加)。壁時計だと時刻合わせで飛んで誤判定する。2 回目として使ったら 0 に戻し、3 連打が続けて「2 回目」にならないようにする。
    - **閉じる前に「タップ直前のアクティブ」へ戻す** (`TabBar.activeBeforeTap`)。1 回目で既にそのタブへ移っているため、そのまま閉じると `SessionManager.close` が**左端のタブ**を選び、「別のタブを消しただけなのに関係ない所へ飛ぶ」ことになる。
    - ⚠ この「1 回目で確定」はタブ選択が**何度やっても同じ結果になる操作**だから成立する。ツールバー (`ToolbarChip`) の 📋/🔅/⚪ は 1 回目に副作用がある (貼る・ロックする・録り始める) ので、同じ置き換えはできない。あちらの待ち時間を消すなら別の設計が要る。
- `terminal/input/TerminalInputView.kt` (AndroidView): 物理キー/OS IME 入力、ジェスチャ (タップ/長押し選択/ドラッグスクロール/ピンチ拡縮/マウスクリック送出)。選択は[§6.5](#65-テキスト選択-ux)。
- `terminal/keyboard/`:
  - `TerminalKeyboard.kt`: 5 行独自キーボード。3 状態 Shift、フリック、全キー長押し連打。**押下時にキー背景を明るい緑に**、**フリック中はしきい値超えた方向のヒントを太字 + 1.6 倍拡大** (中央文字は不変)。
  - `JapaneseFlickKeyboard.kt`: 内蔵 日本語/カタカナ フリック。同じプレス/フリック視覚フィードバック。
  - `KeyboardStyle.kt`: COMPACT(44dp) / SPACIOUS(60dp、4 方向フリック)。`naturalHeight`。`.copy()` で横画面用に拡縮済 style を作る。
  - `KeyGestures.kt`: タップ + 長押し連打の共通ジェスチャ (`onPressedChange` コールバックで press 状態を Composable に伝える)。
  - `components/SpecialKeyBar.kt`: OS IME 時の特殊キー列。
- `settings/SettingsSheet.kt` + `SshAccessHelper.kt`: 設定ページ (全画面) + SSH/ストレージ ヘルパー。
  - 項目は **8 グループのアコーディオン** (`settings/SettingsGroup.kt`) に束ねる: 表示 / キーボード・入力 / Linux 環境 / 常駐サーバー・自動化 / メンテナンス / 開発者向け / **使い方 (Tips)** / このアプリについて。宣言順が表示順。開閉状態は `settings/SettingsGroupStore.kt` が `settings_group_open_<id>` の固定キー 1 本ずつで DataStore に永続化する (グループを増減しても既存の状態が壊れない。保存が無いグループは `defaultOpen` にフォールバック)。閉じている間は中身を composition しない。見出し行は「タップできる場所」だと分かるように**カード背景 + 1dp の枠**（他のタップ可能カードと同じ意匠）を付け、**開いている間は枠と背景をアクセント寄り**にして開閉状態も色で読めるようにする (0.8.184。それ以前は文字と ▸/▾ だけで、周囲の項目と見分けが付きにくかった)。
  - **端末リセット**は `SessionManager.resetToInitial()` を呼び、**端末タブ 1 つだけを残して他タブ (端末・GUI) を全部閉じ**、残した 1 つを `TerminalSession.restart()` で初期化する (= アプリ初回起動時の状態)。タブ数や動作中かに関わらず**常に**確認ダイアログを挟み、実行後はトーストで結果を出す。設定値・常駐サーバー・rootfs には触れない。
- `ssh/SshProfilesSheet.kt` + `HostKeyVerificationDialog.kt`: SSH 接続先と、それに紐づく FTP / SMB / WebDAV / VNC / RDP サービスの UI + SSH 鍵検証。各サービスは既定で SSH ローカルポート転送を使い、ローカルポート未指定時は空きポートを自動取得する。転送を外した場合は暗号化されない旨を警告し、サービス固有ホストではなく親 SSH 接続先のホストへ直接接続する。**踏み台（`-J`）の編集もここ**（段の追加・登録済み接続先からの取り込み・経路 1 行プレビュー。0.8.494）。RDP サービスには**フォルダ共有**のトグルと共有フォルダ / 共有名の欄が付く（既定 OFF の明示 opt-in）。
- `sftp/SftpSheet.kt`: `RemoteFs` を使う SFTP / FTP / SMB / WebDAV 共通ファイルブラウザ (**全画面ページ**)。WebDAV は通常のTLS証明書検証を行う OkHttp、SMB は SMB1 を扱わない SMBJ を使う。Android の戻るボタンと左上矢印は親フォルダへ移動し、ルートでだけ接続終了を確認する。一覧の下方向スクロールが ModalBottomSheet の「閉じる」ドラッグと競合して勝手に閉じるため、設定ページと同じ別ページ方式に変更した。
  - **端末側ファイルを同じ画面に出す (0.8.474)**: 上部のタブで「サーバー側 / この端末」を切り替える。端末側は Android の **SAF ツリー権限** (`sftp/SafFileTree.kt`) で一覧し、選んだフォルダは永続権限として記憶するので、**アップロードのたびにシステムのファイル選択画面へ飛ばされない**。⚠ Uri は外部ストレージ固有のパスへ変換せず、provider が返した `documentId` のまま辿る (パスを持たない provider でも同じ経路で動かすため)。ファイルとフォルダを再帰的に双方向転送する。
  - **プレビュー (0.8.474、画像全画面化 0.8.479)**: リモート側・端末側とも、テキストと画像をその場で開ける。**画像はファイル画面の小さなダイアログへ押し込まず全画面で表示**し、画面全体を使ってピンチ拡大・ドラッグ移動できる。テキストは選択・スクロールできるダイアログのまま。読み込みは**必ず上限付き** (テキスト 2MB / 画像 24MB、画素は 16M を超えたら `inSampleSize` で縮小)。⚠ 上限も種類の判定も無しに開くと、リモートの巨大ファイルを 1 回踏んだだけで転送とデコードに引きずられて画面が戻らなくなる。対応しない種類は開かず「テキストと画像だけプレビューできます」と伝える。
- `snippets/SnippetsSheet.kt`: ツールシート (ツールバー 📜)。タブで **スニペット** (1 行タップで挿入、並替/編集) / **SSH・SFTP** (`ssh/SshProfilesBody`) / **サーバー** (`settings/ServersBody` = 常駐サーバー管理を設定シートと共有) を切替える。SSH タブは端末タブのみ、サーバータブは端末セッションがあるときだけ出る。**シートはどのタブでも全高で開く（0.8.252）**: 中身の量に任せると項目の少ないタブでシートが縮み、**タブバーの位置がタブごとに動いて誤タップになる**（切り替えた先で、前のタブのタブバーがあった場所を押してしまう）。中身の Column に `weight(1f)` を与えて残り全部を取らせる — `fillMaxHeight` ではないのは、上のドラッグハンドルの分だけはみ出すため。
- `components/ReorderList.kt`: **縦リストのドラッグ並べ替え** (0.8.249)。スニペットタブの操作感 (≡ を掴んで上下) を、**行の高さが可変な一覧**でも使えるようにした共通部品で、サーバータブと自動化タブが使う。スニペットは行が固定高なのでピッチを定数で持てたが、サーバー / ルールの行は状態表示やログの開閉で高さが変わるため、各行が `onSizeChanged` で高さを報告し、入れ替えの判定に**隣の行の実測高さ**を使う。掴んでいる間は外側の一覧で並びを上書きしない (指の下から行が飛ばないように)。⚠ 行には `key(id)` を付けること — ノード identity が固定されないと掴んだ行からポインタが外れる。⛔⛔ **`key(id)` は繰り返しの直下に置くこと**（0.8.511）。`if (…) { key(id) { … } }` のように条件分岐を外側に挟むと、**key による移動はその分岐グループの中でしか起きない**ので、順序が変わるたびに項目が破棄されて作り直される。作り直された瞬間に進行中の `pointerInput` が消えるため、**1 個入れ替えたところでドラッグが切れて終わる**（「一気に運べない・1 個ずつしか動かない」の正体。設定画面のツールバーで踏んだ）。⚠ 0.8.510 で入れた連続入れ替えと自動スクロールは**ジェスチャが続いていること**が前提なので、これがあると**どちらも効かず、症状は 1 mm も変わらない**。並べ替えが「1 個で止まる」ときは、まず速さや閾値ではなく **key の位置**を疑う。⚠ ドラッグは**ハンドルだけ**に付ける (行全体だと ON/OFF やログ開閉のタップと競合する)。永続化は呼び出し側の責任 (サーバー = `ServerEntry` の並びをそのまま保存、自動化 = 各ルールファイルの `order=`)。

### 4.12 GUI デスクトップ (`gui/`)

- distro 内で **Xvnc**(VNC サーバ) + 軽量 WM/アプリを起動（`proot/GuiScript.kt` が冪等で配置・起動。GUI 自動起動 / 横画面対応）。
- **GUI 一式の導入 (`ensure_pkgs`)**: Xvnc / openbox / D-Bus を GUI 基盤とし、Xvnc / openbox / `dbus-daemon` が揃っていれば**無通信で即起動**。**GUI 端末は自動導入も自動起動もしない (0.8.505)**。デスクトップはアプリ表示面とし、導入済みの GUI アプリを ☰ / `z2menu` から直接起動する。**未導入の基盤があるときだけ** `install_pkgs`（apk add / apt install / pacman -S）で取得する。app 側 (`TerminalScreen`) のダウンロード確認ゲートが同意を取ってから走り、`clean` のときだけ cache から入れ直す。
- **Xvnc 描画互換 (0.8.505)**: Xvnc で MIT-SHM を無効にするだけでなく、Qt に `QT_XCB_NO_MITSHM=1`、GTK に `GDK_RENDERING=image`、両者に X11 backend を明示する。
- **gThumb / SMPlayer 互換 (0.8.506)**: gThumb 3.12.10 は最初の SVG アイコンを読む際、glycin が Android のアプリ sandbox 内で bubblewrap の Linux namespace sandbox を入れ子にしようとして失敗し、強制終了していた。z2term は gThumb 専用 wrapper からだけ `libz2glycin.so` を `LD_PRELOAD` し、gdk-pixbuf が用意する「外側で sandbox 済み」の公式経路へ切り替える。シムは gThumb プロセスだけに限定され、他のアプリ、root chroot、`bwrap` 本体には干渉しない。SMPlayer は mpv を `--no-config` 付きで起動するため、`/etc/mpv/mpv.conf` だけでは足りない。実際の設定はディストリ別 `.config` overlay にあり、Qt は `General` group を `[%General]` として保存するため、他の全設定を保ったまま overlay 内の `[%General] driver\vo=x11` / `[performance] hwdec=no` だけを補正する。
- **GUI のクリーンインストール設定を廃止 (0.8.507)**: GUI は導入済みアプリを開く表示面であり、設定画面に破壊的な再導入スイッチを常設しない。設定値・次回起動予約・専用確認ダイアログも削除し、GUI タブは常に通常起動する。低レベルの復旧手段 `z2gui clean` は端末コマンドとしてのみ残す。
- `GuiSession`/`GuiActivity`/`GuiScreen`/`GuiViewport`/`GuiInputView`/`GuiKeyMapper`/`GuiEventWatcher` + `gui/RemoteDesktopClient.kt`（描画・入力の共通境界）+ `gui/rfb/RfbClient.kt`（内蔵 RFB 実装）。端末タブと GUI タブをペアリングし IME 連動。`GuiSession`・Compose 描画・入力 View・GUI キーボードは `RfbClient` 型を直接要求せず、同じ境界へ RDP 実装を差せる（0.8.450）。**接続先そのものは `gui/RemoteTarget.kt`**（`VncTarget` / `RdpTarget` が実装）で表し、`GuiSession` は `remote.createClient()` を呼ぶだけでプロトコルを知らない（0.8.459）。
- **RDP（0.8.450〜0.8.492）**: `gui/rdp/` は X.224/TLS、CredSSP/NTLMv2、T.124 GCC・T.125 MCS、Client Info、license、Demand/Confirm Active、connection finalization、slow-path の従来型 Bitmap Update 受信、**Graphics Pipeline（RDPGFX）**までを**外部ライブラリ無し**で実装（MD4 / RC4 / NTLM も自前）。`RdpClient` は 15/16/24bpp の非圧縮・Interleaved RLE 更新を複数矩形と画面外クリップ込みで ARGB framebuffer へ展開し、dirty 領域の redraw を通知する。**0.8.459 で接続 UI を付け、SSH 接続先のサービスとして `[RDP]` から開けるようにした**（→ §6.3.1）。CLIPRDR のテキスト共有に加え、**0.8.476 で slow-path のマウス・キーボード入力**、**0.8.480 で動的 resize**（→ 下の「Display Control」）にも対応した。Fast-Path、Surface Commands、Bitmap Codecs、個別の描画 Order、32bpp RDP 6.0 圧縮は**従来型の capability では 1 つも広告しない**（`orderSupport[32]` 全ゼロ・General cap の extraFlags = 0。**実装していないものは受け取らないと宣言する**のであって、来たものを握り潰すのではない）。
  - ⚠ **受信の診断ログは「種類ごとに 1 度だけ」**（0.8.480）。接続シーケンスの切り分けには効いたが、画面が出たあとは**更新のたびに流れて他が読めなくなる**。⇒ 消さずに、`ActiveSession` が**接続ごとに**「初めて見た種類」を覚える（RDPGFX の command / codec と同じ数え方）。⚠ 抑止を `object` 側に置くと、2 本目のタブや繋ぎ直しで 1 行も出なくなる。
- **RDPGFX（0.8.477）**: ⛔ **Windows 11 は RDPGFX を使えない相手へ従来型 Bitmap Update を送らない。** GFX を無効にした `xfreerdp3` でも同じ無音になることを実測して確かめた（接続も finalization も通り、画面 PDU だけが 1 バイトも来ない）。そこで GCC の CS_NET で **`drdynvc`** を要求し（`RdpDynamicChannel`）、DVC の capability / create を経て `Microsoft::Windows::RDS::Graphics` を開き、**RDPGFX**（`RdpGfx`）で画面を受け取る。GCC CS_CORE では 32bpp と `RNS_UD_CS_SUPPORT_DYNVC_GFX_PROTOCOL` を宣言する（⚠ **GFX を宣言しながら 32bpp を省くと主張が食い違う**）。CAPVERSION_8 の thin-client capability だけを広告し、surface の作成・削除・出力への割り付け、frame acknowledgement、solid fill、surface 間コピー、surface cache、32bpp 非圧縮、**ClearCodec**（`RdpClearCodec`）に対応する。受信データは RDP 8.0 bulk 圧縮（`Rdp8Bulk`）で包まれているので展開器も自前で持つ（送信側は FreeRDP と同じく非圧縮のまま送る）。
  - ⛔ **未対応の command / codec で例外を投げない。** RDPGFX の PDU は長さで区切られているので読み飛ばせる。投げると受信ループごと落ちて**描ける部分まで消える**。何が来たかは 1 度だけログに残し、次に書く decoder を推測ではなく実測で決める。
  - ⛔ **仮想チャネルの `CHANNEL_FLAG_SHOW_PROTOCOL` は `CHANNEL_OPTION_SHOW_PROTOCOL` を宣言した channel にだけ立てる**（[MS-RDPBCGR] 2.2.6.1）。宣言していない `drdynvc` に立てると、Windows は Channel PDU Header 8 バイトごとデータとして受け取り**黙り込む**（0.8.477・実 Windows で判明。Graphics DVC は開くのに capability 応答が返ってこない形で出た）。分割サイズも同様に**サーバーが広告した VCChunkSize** に従う（こちらの広告値ではない）。
- **RemoteFX（0.8.478）**: ⭐ **実 Windows 11 が RDPGFX で選んだのは RemoteFX（CAVIDEO, codec 0x3）と非圧縮（0x0）だった**（0.8.477 で実測。推測ではなく届いた codec を数えて決めた）。`RdpRemoteFx` は 64x64 タイルを **RLGR1/RLGR3 展開 → 差分復元（最下位帯のみ）→ 逆量子化 → 3 段の逆ウェーブレット → YCbCr→RGB** の順に戻し、`TS_RFX_REGION` の矩形で切り抜いて surface へ貼る。⭐ **これで実 Windows の画面が出た**（0.8.478）。
  - 単体テストは**手で組んだ RLGR ビット列**（零 4032 個 + 値 72 を 1 記号で符号化）で、連長の積み上げ・差分・逆ウェーブレット 3 段・色変換を**通しで**固定してある。係数が空なら中間グレーになることも合わせて押さえた。
- **Display Control（0.8.480）**: 端末を回す・分割の幅を変えると、`Microsoft::Windows::RDS::DisplayControl` DVC（`RdpDisplayControl`）で Monitor Layout を送り、**相手にセッションを作り直させる**。応じた相手は RDPGFX の Reset Graphics を送ってくるので、`RdpClient.publishGraphicsFrame` が新しい大きさで framebuffer を確保し直す（既存の経路にそのまま乗る）。
  - ⭐ **「解像度を変えてよいか」はプロトコル名ではなく相手ごとの性質**として持つ（`RemoteDesktopClient.ownsDesktopSize`）。RDP は接続のたびに**こちら専用のセッション**を作らせるので変えてよく、RFB で覗きに行く先は**もう立っている実画面**なので変えない。`GuiSession.requestResize` はこの印だけを見る（呼び出し側にプロトコル名を書かない）。
  - ⚠ **CAPS を受け取るまで Monitor Layout を送らない**（[MS-RDPEDISP] 1.3.1 の順番）。何面まで・どれだけの面積まで許すかを知らないうちに投げても受け付けられる保証がない。⇒ **caps 前の要求は最後の 1 つだけ保留**し、caps が来た時点で送る（回転が続いても落ち着いた大きさだけを要求する）。
  - 要求する大きさは接続時と**同じ** `RdpTarget.fitDesktopSize`（横長・4 の倍数・640〜4096）を通したうえで、[MS-RDPEDISP] 2.2.2.2.1 の条件（**幅は偶数**・200〜8192・CAPS が広告した面積上限）で弾く。⚠ **同じ大きさは再送しない** — 1 回ごとにセッションが作り直され、画面がいったん消えて戻るため。
  - ⛔ **DVC のハンドラを錠の中から呼ばない。** 受信スレッドは「`RdpDynamicChannel` の錠 → `RdpDisplayControl` の錠」の順で入るので、送信側が逆順に取ると回転と画面更新が重なったときに両方止まる。⇒ **錠の中では PDU を組み立てるだけにして、送信は錠の外**で行う。
- **音（rdpsnd・0.8.481〜0.8.492）**: 静的仮想チャネル `rdpsnd`（`RdpSound`）で [MS-RDPEA] を話し、相手の音を端末のスピーカーで鳴らす（`RdpAudioSink`）。
  - ⚠⚠ **1 通に PDU が 2 つ以上入っていることがある（0.8.487）**。CLIPRDR で実測した挙動と同じで、先頭 1 つだけ読んで残りを捨てると、**Training を取りこぼして相手が音を送り始めない**という詰まり方をする。⇒ 端から順に切り出す。⛔ **ただし WaveInfo を読んだらそこで止める** — 続きの生データは PDU ヘッダを持たないので、同じ通に残りがあっても PDU として読んではいけない。
  - ⛔⛔⛔ **こちらが「音は要らない」と宣言していた（0.8.488・実機ログで判明）**。Client Info PDU の flags を数字の or で並べており、その中に `INFO_NOAUDIOPLAYBACK`（0x00080000）が紛れていた。相手はそのとおりに動くので、**`rdpsnd` にも `AUDIO_PLAYBACK_DVC` にも 1 通も流れてこない**（チャネルだけが開いた状態になり、相手側の設定を疑いたくなる）。⇒ そのビットを外し、**flags は必ず名前で書く**。⭐ **音のリダイレクトは既定で有効**なので、「要らない」と言わないことがそのまま「鳴らしてくれ」になる（`INFO_REMOTECONSOLEAUDIO` も立てない — あれは相手側で鳴らさせる指定）。⚠ **相手に音声デバイスが無くても関係ない** — RDP セッションには仮想の「リモート オーディオ」が作られる。⚠ 音を実装した 0.8.481 の時点ではこのビットに誰も気付けなかった。**数字の羅列は、後から読む人が意味を確かめる手段を持たない。**
  - ⛔⛔⛔ **音を鳴らすには `rdpdr` を開く必要がある（0.8.491・FreeRDP との比較で判明）**。相手は**デバイスのリダイレクトができるクライアントにしか音声を回さない**。FreeRDP も `/sound` を指定すると `rdpdr` を一緒に載せる。⇒ `gui/rdp/RdpDeviceRedirection.kt` で [MS-RDPEFS] の名乗りを実装した。⚠ **チャネルを開くだけでは足りない**: 相手は Server Announce → Client Announce Reply → Client Name → Server Capability → Client Capability → Device List の往復が終わるまで先へ進まない。⚠ Client ID Confirm は Capability の前後どちらでも来るので、**順番を決め打ちしない**（実測）。
  - **フォルダ共有を切ってあるときは、渡すデバイスは 0 件**（`ioCode1` = 0、`SpecialTypeDeviceCap` = 0、Device List Announce は `DeviceCount` = 0）。ドライブもプリンタもスマートカードも渡さない — **そのときここを開くのは音のためだけ**である。
  - **フォルダ共有（0.8.494・`gui/rdp/RdpDrive.kt`）**: 接続先の RDP サービスで ON にすると、端末の 1 フォルダを `\\tsclient\<共有名>` として**読み書き**させる。⚠ **既定 OFF の明示 opt-in**（知らないうちに端末のフォルダが相手から書き換えられる状態にしない）。既定の置き場は**クリップボードで受け取ったファイルと同じ** `Download/z2term`（`RdpShareDefaults` が `ClipboardFileTransfer.FOLDER` を参照する。戻す先が 2 か所に分かれると探すことになる）。⭐ **保存先を毎回選ばせない** — 変えたい人だけパスを書く。
    - **相手は普通のファイルシステムだと思って話しかけてくる**。開く / 読む / 書く / 一覧 / 情報 / 名前の変更 / 削除 / 容量 の IRP が 1 つずつ届き、1 つずつ答える（`IRP_MJ_CREATE` … `IRP_MJ_DIRECTORY_CONTROL`）。⇒ ここは**小さなファイルサーバー**であって、まとめて転送する仕組みではない。
    - ⛔⛔ **共有フォルダの外へは 1 バイトも出さない**。`..` を混ぜた道を弾くだけでは足りず、**実体（`canonicalFile`）を解決してから共有フォルダの下かどうかを見る** — 共有フォルダの中に外を指すシンボリックリンクが置かれている場合があるため。名前の変更先にも同じ検査を通す（`RdpDrive.resolve`。`RdpDriveTest` が両方を押さえる）。
    - ⛔⛔ **ファイル I/O を受信スレッドでしない**。IRP は RDP の受信ループから届くので、ここでディスクを待つと**画面・入力・音がまとめて止まる**（CLIPRDR の取り寄せと同じ約束）。⇒ `rdp-drive` の 1 本へ回し、答えができてから送る。順番を保つために**スレッドは 1 本**にする。
    - ⚠ **ドライブを名乗り忘れると IRP が 1 つも来ない**。Capability で `ioCode1` = `0x0000FFFF`（[MS-RDPEFS] が定める決め打ちの値）と `CAP_DRIVE_TYPE` を出して初めて、デバイス一覧に出したフォルダが使われる。共有していないときは従来どおり `ioCode1` = 0 のまま。
    - ⚠⚠ **[MS-FSCC] の表どおりに詰めると相手が読み違える**。`FILE_BOTH_DIR_INFORMATION` の `Reserved`(1) / `FILE_FS_VOLUME_INFORMATION` の `Reserved`(1) / `FILE_BASIC_INFORMATION` と `FILE_STANDARD_INFORMATION` の末尾は**入れない**（93 / 17 / 36 / 22 バイト）。FreeRDP も同じ判断をしており、実機での相互接続はこちらが正しい。
    - ⚠ **フォルダの変更通知（`IRP_MN_NOTIFY_CHANGE_DIRECTORY`）には答えない**。本物のファイルシステムは「何か変わるまで」返事を保留する。ここで失敗を返すと Explorer がフォルダを開いた直後にエラーを出すので、**黙って握る**（FreeRDP も同じ）。
    - ⚠ **空き容量を返す**。Explorer は残量を見てコピーを止めるので、`FILE_FS_SIZE_INFORMATION` に端末の実際の空きを載せる（0 を返し続けると「容量不足」で失敗する）。
    - ⚠ **共有名は ASCII に倒す**（`RdpDeviceRedirection.asciiShareName`）。デバイス一覧の名前は ASCII で載るので、日本語のフォルダ名をそのまま出すと壊れる。`PreferredDosName` は 7 文字 + NUL に収める。**中身のファイル名は UTF-16 なので日本語のまま通る。**
    - ⚠ **タブを閉じたら開きっぱなしのファイルを畳む**。相手が閉じずに切断することがあるので、`RdpClient.close` / `closeTransport` から `RdpDeviceRedirection.close()` → `RdpDrive.close()` を必ず通す。
  - ⛔⛔⛔ **回線の速さを名乗らないと、音から先に切られる（0.8.490・FreeRDP との比較で判明）**。Client Core Data の `connectionType` を 0 のまま置き、`RNS_UD_CS_VALID_CONNECTION_TYPE`（0x0020）も立てていなかった。**このビットが無いと `connectionType` はそもそも読まれない**（[MS-RDPBCGR] 2.2.1.3.2）ので、相手からは「不明＝遅い回線」に見え、**音声のリダイレクトが丸ごと無効**になる（mstsc の「エクスペリエンス」でモデムを選んだのと同じ状態）。⇒ ビットを立てて `CONNECTION_TYPE_LAN` を名乗る。⭐ **これは速さの申告ではなく、相手が機能を削るかどうかの判断材料**である。⚠ 症状は「`rdpsnd` チャネルは開くのに 1 通も来ない」で、相手側の設定を疑いたくなる出方をする。**同じ相手に FreeRDP で繋いで名乗りを並べる**まで分からなかった（FreeRDP: `earlyCapabilityFlags=0x0fb2` / z2term: `0x0900`）。⚠ **相手は `AUDIO_PLAYBACK_DVC`（動的チャネル）も使う** — FreeRDP で繋ぐと相手はそちらを開く。⭐ ただし**こちらが動的チャネルの受け口を出さなければ静的 `rdpsnd` に落としてくる**ので、静的だけの実装で足りる（0.8.491 の実測: 相手が挙げた 30 形式のうち 16bit PCM が 1 つあり、44.1kHz ステレオで鳴った）。
  - ⚠ **静的チャネルの優先度を名乗る（0.8.489）**。`rdpsnd` の channel options に `CHANNEL_OPTION_PRI_MED` を足した（FreeRDP / mstsc はどちらも付けている）。仕様上は無くても通るはずだが、**音だけ 1 通も来ない**状態を追う間は、広く使われている実装と違うところを残さない。
  - ⚠ **名乗った flags は実際に出た値をログに残す（0.8.489）**。「外したはずのビットが本当に外れているか」は、ソースを読んでも確かめたことにならない（動いているのは R8 を通った APK）。
  - ⚠ **音が出ないとき、どちら側の話かを切り分けられるようにしておく（0.8.487・0.8.492）**。チャネルに 1 通目が届いた時点と、**種類ごとに最初の 1 通だけ**をログに残す。⚠ 無音の間、相手は Training（`0x06`・本体 4 バイト）を数秒おきに送り続ける（接続確認）。毎回記録すると **logcat が流れて他の調査ができなくなる**ので、同じ種類は繰り返さない。**チャネルは開いているのに 1 通も来ない**なら相手がリダイレクトしていない側の話で、こちらの実装では直せない。
  - ⭐ **宣言する形式が相手の送ってくる形式を決める**（RemoteFX と同じ）。相手が挙げた中から **16bit PCM だけ**を選び、しかも **1 つだけ**返す。1 つに絞れば以後の `wFormatNo` は 0 に決まり、途中で形式が変わることもない ⇒ **展開器を持たずに済む**。⚠ 相手が PCM を 1 つも挙げなければ音は出ない（接続は壊さない）。ADPCM / AAC の展開が要るかは**実際に何が挙がったかをログで見てから**決める。
  - ⚠⚠ **WaveInfo の続きには PDU ヘッダが無い。** RDP 5 以来の形では音の本体が次の 1 通に続き、その先頭 4 バイトは捨てる。この状態を持っていないと**音データを PDU として読もうとして壊れる**。RDP 8 の Wave2 なら 1 通に収まるので、`wVersion` は 8.0 を宣言する。⚠ どちらの形でも **Wave Confirm を返す**（返さないと相手は次を送らず、数百 ms で音が止まる）。
  - ⛔⛔ **受信スレッドで `AudioTrack.write` を呼ばない。** バッファが空くまで待つので、**音が詰まった瞬間に画面と入力まで止まる**。⇒ 専用スレッドと有限のキューを挟み、**溢れたら古い音を捨てる**（音が飛ぶのは許せるが、画面が止まるのは許せない）。
  - static virtual channel の分割復元は cliprdr と同じ手順なので `RdpChannelReassembler` に出した（⚠ DVC の分割は**別階層**で、その中身をさらに `RdpDynamicChannel` が組み立てる）。
  - ⚠ **中間値は 16bit で丸める。** 32bit のまま持つと桁が溢れる場面で出力が食い違う。
  - ⚠ 等倍表示の画面合成は**行単位のコピー**にしてある。2400x1080 を毎フレーム舐めるので、1 画素ずつ拡大率を割り算すると重い。
- **入力**: `GuiInputView` のジェスチャ — **2 本指 = ピンチ(ズーム/パン)**、**3 本指縦移動 = ホイール上/下スクロール**（一度 3 本指になったら全指が離れるまでスクロール扱い）。旧スクロールボタンと `RfbClient.scrollWheel` は撤去。
- **動画**: GPU 無し端末で `gpu` 出力が失敗するため、mpv を **`vo=x11` 既定 + `LIBGL_ALWAYS_SOFTWARE`** でソフト描画させて正常再生。
- **音声 (`service/AudioBridge.kt`)**: **オプトイン**（設定「GUI 音声」`guiAudioEnabled` ON 時のみ）。distro 内 PulseAudio(`-n` 方式で起動) → TCP → Android `AudioTrack` でブリッジ。
- ⭐ **openbox の設定は「差し替え」ではなく「上書き」にする（0.8.498）**。0.8.497 まで `z2gui` は `/tmp/z2-openbox-rc-<N>.xml` に**窓の位置固定だけを書いた 8 行の rc.xml** を作り、`openbox --config-file` で渡していた。⛔ **openbox はキー割り当てもマウス操作もメニューもプログラムに内蔵しておらず、全部 rc.xml のデータでしかない。** 743 行ある distro 既定（実機の値）を 8 行で置き換えた時点で、`<keyboard>`（Alt+Tab 等）・`<mouse>`（タイトルバーのボタン、リサイズ）・`<menu>`（デスクトップ右クリック → `root-menu`）が**まとめて消えていた**。右クリックメニューは **GUI の中でアプリを起こす唯一の入口**なので、これが無いと「端末しか出せないデスクトップ」になる（利用者の「コンソールしか出ない」の正体）。⇒ 既定（`~/.config/openbox/rc.xml` → `/etc/xdg/openbox/rc.xml` の順に探す）を awk で加工して、**メニューの指し先と窓の位置の 2 点だけ**を差し替える。
  - ⚠ **`<file>` は全部落として 1 つだけ入れ直す。** openbox は複数のメニューファイルを読める仕様なので、1 つ目だけ差し替えると既定の固定一覧が残る。
  - ⚠ **位置固定は `<applications>` の末尾に足す。** openbox は一致するルールを順に適用して後のものが勝つので、既定の個別ルールより前に置くと効かない。
  - 既定が無い distro / 加工に失敗したときだけ、従来の最小構成へ落ちる（この経路では窓の移動もリサイズもできない。あくまで最後の砦）。
- **`z2menu` — 入っているアプリだけを出すメニュー（0.8.498）**: 既定 `menu.xml` は distro が用意した**固定の一覧**で、その環境に**入っていないアプリが大量に並ぶ**（押しても何も起きない項目ばかりのメニューは、無いより分かりにくい）。`Z2MenuScript.kt` が `/usr/local/bin/z2menu` を置き、openbox の **pipe menu**（メニューを開くたびに実行して、返した XML をそのままメニューにする仕組み）としてこれを呼ぶ。`~/.local/share/applications` → `/usr/local/share/applications` → `/usr/share/applications` の順に `.desktop` を読み、`Type=Application` かつ `NoDisplay`/`Hidden` でなく、`TryExec` と `Exec` の先頭語が **PATH に実在するものだけ**を出す。`z2menu list` は同じ一覧を TSV（名前・コマンド・説明・端末フラグ・分類）で返す。
  - ⚠ **`Exec` のフィールドコード（`%f %F %u %U %d %D %n %N %i %c %k %v %m`）は除去する。** 残すと、引数を取らない起動でアプリが `%U` という名前のファイルを開こうとする。`%%` は本物の `%` なので、先に印へ逃がして最後に戻す。
  - `Terminal=true` の項目は一覧から除外する (0.8.505)。GUI 基盤は特定の端末を要求しない。
  - 項目が **20 を超えたときだけ**分類（freedesktop の主分類に丸める）のサブメニューへ分ける。少ないうちから階層にすると、指で辿る手数が増えるだけになる。
  - 同じファイル名（desktop id）が複数のディレクトリにあるときは**先に読んだ方**を採る（利用者が `~/.local/share` に置いた分が distro 既定に勝つ）。
  - ⛔ **awk は POSIX の範囲で書くこと。** busybox awk には `ENDFILE` も配列の全消しも無い。ファイルの切れ目は `FNR==1` で見て最後の 1 件を `END` で出し、値は連想配列ではなく**スカラー変数**に持つ。⚠ `sh -n` は awk の中身までは見ない（`GuiScriptSyntaxTest`）ので、awk を変えたときは実機で `z2menu list` を叩いて確かめる。
  - 窓の一覧は openbox 内蔵の `client-list-menu` をそのまま使う。⚠ `wmctrl` / `xdotool` は**どの distro でも導入対象に入っていない**ので、窓を数えるために依存を増やさない。
- **D-Bus セッションと `XDG_RUNTIME_DIR` を、端末の種類に関係なく用意する（0.8.498）**: 0.8.497 までは**選んだ GUI 内ターミナルが konsole のときだけ**立てていた。そのため右クリックメニューや別タブの `z2run` から起こしたアプリには渡らず、**補助プロセスを別プロセスとして起こす作り**のもの（KIO 等）が軒並み起動に失敗していた（ファイル管理系でサムネイル・ゴミ箱・接続機器の一覧がまとめて出ない）。⇒ `start_session_bus` を `start_audio` の後・**openbox より前**に呼ぶ。右クリックメニューから起こしたアプリは openbox の環境をそのまま継ぐので、ここで export した分が全部渡る。
  - ⭐ **アドレスは `XDG_RUNTIME_DIR` 配下の決め打ちのパスにする。** `dbus-launch` は起動のたびにアドレスが変わるので、別タブの `z2run` から相乗りできない。`dbus-daemon` を先に試し、無い環境でだけ `dbus-launch` へ落ちる。どちらの場合も `$XDG_RUNTIME_DIR/dbus-address` に控えを書き、`z2run` がそれを読んで同じバスへ繋ぐ（`z2gui stop` で控えごと消す）。
  - ⚠ **`dbus-daemon --print-pid` が取るのは「ファイルディスクリプタ番号」で、パスではない。** 0.8.497 までここへパスを渡していて、`Invalid file descriptor` で**必ず失敗**していた（`dbus-launch` を先に試す順序だったので表に出ていなかった）。pid は `--print-pid` を引数無しで使い、**stdout をファイルへ向けて**受ける。
  - `is_gui_proc` に `dbus-daemon` を足した。PIDFILE には前から控えていたのに**種別の表に無かった**ので、`z2gui stop` で止まらずに残っていた。
- ⭐⭐ **「GUI が二度と開かない」「☰ から起こしたアプリが出てこない」の真因（0.8.504 で修正）**: **X が生きているかの判定が z2root エンジンで必ず外れていた**。
  - ⛔ **z2root 配下ではゲストの `/proc/<pid>/comm` が全部 `libz2root.so` になる**（実体名は出ない）。`x_alive` / `is_gui_proc` は comm を `Xvnc` / `openbox` / `xterm` と比べていたので**必ず false**。結果、`z2gui stop` は 1 つも kill できず **Xvnc が残り**、残った Xvnc がディスプレイを掴んだままなので 次の起動が `Cannot establish any listening sockets` で落ちる。⭐ **代わりに `environ` の `DISPLAY=:N` で見分ける** （実測: z2root 配下の Xvnc は `comm=libz2root.so` / `environ` に `DISPLAY=:12` を持つ）。chroot エンジンでは comm が実体名になるので、従来の名前一致も残す。
  - ⚠ **ソケットファイルが在ることは「X が動いている」ことを意味しない。** GUI は `--kill-on-exit`（SIGKILL）で落ちるので `/tmp/.X11-unix/X<N>` と `/tmp/.X<N>-lock` が**そのまま残る**。残骸を見た `z2run` は「GUI は動いている」と判断して z2gui を起こさずアプリを `exec` するため、アプリは `Cannot open display` で即死する（☰ から選んでも何も出てこない、の正体。利用者の端末には**1 週間前のソケット**が残っていた）。
  - ⭐ **掃除はアプリ側（`ProotLauncher.cleanStaleXSockets`）でやる。** ⛔ **生死を `/proc` で判定しないこと** — comm が使えないうえ、**別インスタンスの pid はそもそも見えない**。`LocalSocket` で**ソケットへ実際に繋いでみる**のが唯一確実で、それができるのはアプリ側だけ。繋がらなければソケット・lock・pids をまとめて消す。
- **アプリ台帳は無いときだけ作る（0.8.498）**: `ensure_desktop_db` が `mimeinfo.cache` の無いディレクトリにだけ `update-desktop-database` をかける。⛔ **毎回は走らせない** — `.desktop` を全部読み直すので起動が目に見えて遅くなる。
- ⭐ **☰ アプリ一覧（0.8.499）— GUI の中にアプリを起こす「常設の入口」**: 右クリックメニューが戻っても、それは**押し方を知らないと出ない入口**でしかない（デスクトップの何もないところを長押し）。利用者の要望は **Windows のスタートボタンにあたる常設のボタン**だったので、GUI タブのツールバーに `ToolbarButtons.APPS`（☰・`guiOnly = true`）を足し、`ui/gui/GuiAppsSheet.kt` のシートに一覧を出す。行をタップすると `GuiSession.launchApp` が `z2run` 経由で起こす。
  - ⭐ **一覧はアプリを起動してから最初の 1 回だけ取る**（0.8.509・要望）。`GuiAppCatalog` が distro ごとに結果を持ち、2 回目からはそこから即返す（`GuiSession.refreshApps(force = false)`）。開くたびに `z2menu list` を起こしていたころは、proot の起動と `.desktop` の読み取りで**毎回 1 秒前後、空のシートを見せていた**。取り直しはシート右上の「更新」を押したときだけ（`refreshApps(force = true)`）。⚠ **空はしまわない** — 空になるのは GUI 未導入 / rootfs 未展開 / 取得失敗のときで、導入し終えたあとも空を返し続けると「入れたのに一覧に無い」から抜け出せなくなる。
  - ⭐ **一覧の中身は `z2menu list` が返す TSV をそのまま使う**（`gui/GuiAppCatalog.kt`）。⛔ **`.desktop` のパースを Kotlin 側で書き直さないこと。** 何を出すか（`Type` / `NoDisplay` / `Hidden` / `TryExec` / `Exec` の実体が PATH に在るか / フィールドコードの除去）は z2menu が決めており、**同じ規則を 2 か所に持つと必ず片方だけ直されて食い違う**（右クリックメニューと ☰ で並ぶものが違う、という形で出る）。
  - ⚠ **PTY 越しに読むので改行は CRLF**（termios の `ONLCR`）。`\r` を落としてから TAB で割る。列が足りない行は捨てる（proot が何か 1 行出しても一覧が壊れないように）。
  - ⚠ **`z2menu list` を無期限に待たない**（20 秒で打ち切る）。返らないと ☰ を押しただけでシートが永久に「読み込み中」になる。数十個の `.desktop` を読むだけなので、実測では 1 秒に満たない。⛔ **打ち切りを `withTimeoutOrNull` で書かないこと（0.8.502 で修正）。** 中身は PTY からの**ブロッキング read** でキャンセルを一切見ないので、時間が来ても read が返るまで何も起きない（20 秒の打ち切りは効いていなかった）。**PTY を閉じれば read は必ず失敗して返る**ので、打ち切りは別コルーチンの見張り役が `close()` することで行う。
  - ⭐⭐ **☰ に 1 件も出なかった原因は `readBytes()`（0.8.499〜0.8.501 → 0.8.502 で修正）。** PTY は**ゲスト側が終わると master の read が `EIO` で落ちる** — EOF が戻り値ではなく**例外**として出る。`InputStream.readBytes()` はその例外をそのまま投げるので、**それまでに読めていた TSV ごと捨てられ、一覧は必ず空になる**。端末から `z2menu list` を叩けば 20 行返るのにアプリ側だけ空、という形で出た。⛔ **PTY を `readBytes()` で一気に読まないこと。** 読めた分を貯めながら進み、例外は正常終了として扱う（`GuiSession.drainPty` / `HeadlessRun` と同じ形）。
  - ⚠ **`read` の `IFS` に TAB を渡して列へ割らないこと（0.8.502 で修正）。** TAB は **IFS の「空白」**なので**連続した TAB が 1 つに畳まれる**。`Comment=` の無い `.desktop`（かなり多い）で列が 1 つずつ手前へずれ、説明の欄に端末フラグの `0` が出て分類が空になっていた。`z2menu` の `list_apps` は**行をそのまま持ち**、判定に要る 2 列（コマンド・端末フラグ）だけを前から剥がして取る。
  - ⚠ **開くたびに取り直す。** パッケージを入れた直後に出ないと「入れたのに一覧に無い」で詰まる。
  - ⛔ **起こしたアプリの PTY を閉じないこと。** proot は `--kill-on-exit` なので、ルートの PTY を閉じると**配下の GUI アプリごと殺される**（`setsid` しても proot の管理下からは逃げられない）。`GuiSession` が `appPtys` に持ち続け、タブを閉じる (`stop`) ときにまとめて閉じる。
  - ⭐⭐ **選んでも何も起きなかった原因は `DISPLAY` が無いこと（0.8.499〜0.8.502 → 0.8.503 で修正）。** `ProotLauncher.launch` に `display` を渡すだけでは **`Z2_DISPLAY` しか入らず `DISPLAY=:N` は入らない**（`exportDisplay = true` が要る）。`z2run` は最後に `exec` するだけで **DISPLAY を自分では立てない**ので、起こしたアプリは X に繋げず即死していた（一覧は出るのに押しても何も出てこない、という形で出る）。⛔ **`z2gui` 本体と、`:N` へ相乗りする側を取り違えないこと。** `exportDisplay = false` が要るのは `z2gui` 自身の起動だけ（`z2gui stop` の environ 走査が自分を巻き込むため）で、端末タブの `z2run` も ☰ から起こすアプリも**相乗りする側**なので `true` が正しい。
  - ⚠ **`Exec` は語で割らずシェルに渡す**（`sh -c "exec z2run <Exec>"`）。`.desktop` の `Exec` は引用符を含むことがあり、空白で割ると壊れる。openbox の `<execute>` も同じくシェルに渡している。
  - `Terminal=true` は上記のとおり除外。`Terminal=false` の GUI アプリは、端末種別に関係なくそのまま起動する。
  - ⭐ **却下した案: GUI の中にパネルを立てる（lxpanel 等）。** 見た目は本物のデスクトップになるが、**PC 用の寸法のパネルがスマホの画面に出るのでボタンが指より小さい**。加えて GTK 依存で数十 MB のパッケージ追加と、distro ごとの設定ファイルを z2term が抱えることになる。ネイティブのシートなら追加パッケージ 0 でどの distro でも同じように出て、指で押せる大きさになり、ツールバーの並べ替え・非表示の仕組みにもそのまま乗る。
  - ⚠ **窓の一覧はシートに入れていない。** X の窓一覧（`_NET_CLIENT_LIST`）を読むには `wmctrl` / `xdotool` が要るが、**どの distro でも導入対象に入っていない**。依存を増やす代わりに、openbox 内蔵の `client-list-menu`（デスクトップ長押し →「窓」）に任せ、シートの末尾でその場所を案内する。
- ⭐⭐ **Qt6 製のアプリが設定を 1 バイトも保存できなかった件（0.8.498 で切り分け → 0.8.500 で解決）**。Qt の `QSaveFile` / `QTemporaryFile` は Linux では `O_TMPFILE` で名前の無いファイルを作り、`linkat(AT_FDCWD, "/proc/self/fd/<fd>", …, AT_SYMLINK_FOLLOW)` で最後に名前を付ける。⛔ **Android のアプリプロセスは capability を 1 つも持たない**（`/proc/self/status` の `CapEff = 0`）ため、この `linkat` が `ENOENT` で必ず失敗する（`AT_EMPTY_PATH` を使う形も `EACCES`）。設定・キャッシュ・アプリ台帳のどれも書けず、**「何で開くか」を KDE 系の台帳から引くファイル管理系ではダブルタップが無反応になっていた**。
  - ⚠ **`O_TMPFILE` で開くところまでは成功する**ので、症状は「書き込み権限が無い」ようには見えない（同じディレクトリへ `touch` も `rename` も通り、ディスクも 41 GB 空いている）。実測（Qt 6.11.2）では `kwriteconfig6` が**終了コード 0 を返しながらファイルを 1 つも作らず**、`kbuildsycoca6` も無言で終わってキャッシュを 1 つも残さなかった。Qt が出すのは「Disk full?」「No such file or directory」「`QLocalServer::listen: Name error`」で、**どれも真の原因を指していない**。
  - ⭐ **直し方（0.8.500）: `LD_PRELOAD` シムで `O_TMPFILE` の `open` を断る。** Qt は `O_TMPFILE` が使えなければ**名前付きの一時ファイル方式へフォールバックする**ので、それだけで全部書けるようになる。⚠ **この前提は先に実測で確かめた** — `O_TMPFILE` を受け付けない場所（`/sdcard` は `EOPNOTSUPP`）を `HOME` にすると、シム無しでも `kwriteconfig6` が設定を書き `kbuildsycoca6` が 300KB の台帳を作った。**シムを書く前にここを確認すること**（フォールバックが無ければシムを入れても直らない）。
  - ⛔ **新しい `.so` を足さず、`z2usb.c` に入れた。** `LD_PRELOAD` のシンボル解決は**先に見つけた 1 つが勝つ**ので、`open`/`openat` を横取りする `.so` を 2 枚重ねると**後ろの 1 枚は丸ごと死ぬ**（`libz2usb.so` が既に `open` 系を全部フックしている）。⭐ **open 系に用がある処理は、別のシムを足さず必ずここへ足すこと。** ファイル名は USB 由来だが、役割は「open/openat を預かるシム」に広がっている（冒頭のコメントに明記した）。
  - ⚠ **返す errno は `EOPNOTSUPP`(95) 以外にしないこと。** これは「このファイルシステムは `O_TMPFILE` を持たない」の意味で、Qt / glibc はこれを見てフォールバックする。`EPERM` / `EACCES` にすると「書く権限が無い」と解釈され、**フォールバックせずそのまま失敗する**実装がある。
  - ⚠ **フラグから `O_TMPFILE` を落として普通の `open` に化かしてはいけない。** 渡されているのはディレクトリなので `EISDIR` になり、呼び出し側からは「一時ファイルが作れない」ではなく「変な失敗」に見える。
  - **実測（実機・シム有効）**: これまで 1 バイトも書けなかった `dolphinrc`(113B) / `user-places.xbel`(2467B) / `dolphinstaterc`(544B) がすべて作られ、アプリ台帳も 305KB 生成された。⭐ **起動時のエラーログが 40 行以上から 0 行になった。**
  - ⚠ **効くのは z2root エンジンだけ**（`LD_PRELOAD` を積むのが `ProotLauncher` の z2root 専用 env のため）。PRoot エンジンは syscall を自前で横取りするので事情が異なる（未検証）。
  - ⚠ **GLib 系は元から影響を受けていない**（`update-desktop-database` は正常に `mimeinfo.cache` を作っていた）。同じ症状を見たら、まず**どちらの系統のアプリか**で切り分ける。
- ⚠ **導入の途中で別のタブへ移っても、戻ってきたときに起動をやり直さない（0.8.341・実機報告）**。「GUI のインストール中に別のタブへ移ると導入が消える」の正体は、**画面が捨てられて起動判断からやり直していた**こと。端末タブへ移ると `TerminalScreen` は `activeSession is GuiSession` の分岐で早期 return するので **`GuiTabScreen` は composition ごと消える**（`guiAreaPx` も `pendingGuiStart` も `remember(gui.id)`）。戻ると `LaunchedEffect(gui.id)` が最初から走り直し、まだ導入中＝パッケージ未導入なので**「GUI を入れますか」の確認ダイアログが再び出る**。しかもその「やめる」は `SessionManager.close(gui.id)` ＝ タブを閉じる → `GuiSession.stop()` → `z2gui stop` なので、**走っていた導入が本当に殺される**。⚠ **導入そのものはタブの表示に依存していない**（`GuiSession` の `SupervisorJob + Dispatchers.IO` で回り、`stop()` を呼ぶのはタブ ✕ と `GuiActivity.onDestroy` だけ）ので、**前面通知で常駐させる必要は無い**。直すのは判断する側で、`GuiSession.state` が `STARTING` / `CONNECTING` / `CONNECTED` のときは `LaunchedEffect` が何もしない（= `GuiSession.start()` が早期 return するのと同じ 3 状態）。`ERROR` / `STOPPED` から戻ったときに入れ直せるのは今までどおり。進捗（`z2gui` の最新出力）は `GuiSession.message` に載っていて `GuiScreen` が購読しているので、**ダイアログが被らなくなれば戻った時点の続きがそのまま見える**。
  - ⚠ **`GuiTabScreen` はタブを離れるたびに捨てられる前提で書くこと**。`remember(gui.id)` / `LaunchedEffect(gui.id)` は戻るたびに作り直される。**セッションが持っている状態を見ずに副作用を起こすと、走っているものをやり直させる**。
- ⚠ **過去の GUI 内ターミナルの記録（0.8.343、0.8.505 で自動導入・起動を廃止）**。`ensure_pkgs` は **`has $GUI_TERM_BIN`（バイナリの有無）**で導入を判定していたので、**入っているが起動に失敗する**ものはここをすり抜け、`z2gui` は最後まで進んで GUI だけが立っていた。
  - ⚠ **この節の変更は、実機で報告された症状の原因ではなかった**（2026-08-14）。最初は「Alpine で rxvt / Konsole を選ぶと端末が出ない」という報告から**フォントを疑った**が、実機で確かめると症状は 2 つに分かれた: **(A) rxvt はどの OS でも「窓は開いていて、画面をタップするまで表示が更新されない」**（＝ フォントでも導入でもなく、RFB の画面更新側）、**(B) Alpine の Konsole だけが本当に出ない**。⚠ **「出ない」と「出ているが描き変わらない」は別の話**として切り分けること。ここに入れた変更は (B) の切り分けと、下記の**非対称の是正**として残してある。
  - **`apk` のサーバ一式にコアフォントが無かった**（非対称の是正）: `apt` には `xfonts-base`、`pacman` には `xorg-fonts-misc` を入れていたのに、`apk` だけ `font-noto ttf-dejavu`（TrueType のみ）だった。**コアフォント `fixed` を既定で使う端末**を `-fn` 無しで起動すればここで死ぬので、`font-misc-misc font-alias` を追加した。
  - **urxvt にも Xft を明示**（`-fn xft:monospace:size=11`）。xterm が `-fa monospace` でコアフォント依存を切っているのと同じ理由で、フォントパッケージの有無に生死を預けない。⚠ `TERM_ARGS` は**語分割前提で展開する**ので、空白を含む書き方をしないこと。
  - **端末パッケージをサーバ一式から分けた**（`TERM_PKGS`）。apk / apt / pacman は**解決できない名前が 1 つ混じるとコマンド全体が失敗する**ので、Konsole の Qt6 依存を `SRV_PKGS` に混ぜていた従来の作りでは、**名前が 1 つ違うだけで tigervnc まで入らない**（= GUI がまるごと立たない）。分けたうえで、まとめて失敗したら**端末本体だけでもう一度**試し、それも駄目なら 1 行出す（黙って端末の無い GUI にしない）。
  - **起動直後に死んだら理由を出す**: 端末を起こして 3 秒後、`/tmp/z2gui-term-<N>.log` が空でなければ末尾 8 行を端末へ流す。**正常に動いている端末はここへ何も書かない**ので、中身があること自体が異常の印になる（Konsole だけは診断を先頭に書くので常に出る）。
  - ⚠ **生成シェルの構文は `GuiScriptSyntaxTest` が `sh -n` で見る**（全端末 × 日英の 8 通り）。`z2gui` は Kotlin から生成する文字列なので、**Kotlin がコンパイルできてもシェルとして壊れていることがあり、壊れると GUI が一切立たない**。
- ⚠ **「タップするまで端末の窓が描かれない」は RFB クライアントの側ではない**（0.8.344・実機ログで確定）。`RfbClient` に**接続直後 40 件だけ**の診断ログ（`req#N` / `upd#N rects/dirty/bbox`）を入れて実機で取ったところ、こう出た:
  ```
  21:00:28.350  req#4 incremental=true          ← 要求は送って待っている
        …15.8 秒、更新ゼロ…
  21:00:44.122  upd#4 rects=12 bbox=(0,0)-(828,934)  ← 画面をタップした瞬間
  ```
  **要求は保留されたまま生きていて、Xvnc が「変化なし」と判断して何も返していなかった**。届いた矩形 `(0,0)-(828,934)` は端末の窓そのもので、**タップして初めて描かれた**ことを示す。⚠ したがって原因は**ゲスト側（X クライアントが入力イベントを受けるまで描画しない）**にある。RFB は「要求 → 変化があるまで保留 → 1 回返す」の往復なので、**往復が途切れても何のエラーも出ない**。この診断ログが無いと「要求が出ていない」のか「変化が無い」のかを区別できない — 定常状態では 1 行も出さないので入れたままにしてある。
- ⚠ **z2root のスイッチは「起動時にしか効かない」ので、口を 1 つ開けてある**（0.8.345）。`~/.z2root_env`（共有 HOME）に `KEY=VALUE` を 1 行ずつ書いておくと、その環境変数が z2root へ渡る。`Z2ROOT_NO_READFREE=1`（read もトレース対象にする）・`Z2ROOT_NO_SECCOMP=1`・`Z2ROOT_NO_LOADER=1` のような**調べるためのスイッチ**を、アプリを作り直さずに端末や ssh から試せる。⚠ **既定ではファイルが無いので何も起きない**。調査が終わったら消すこと（`Z2ROOT_NO_READFREE=1` は read を全部トレースするので実用には重い）。トレース自体の ON は `~/.z2root_trace_on`（または設定の「トレースログ」）で、出力先は `~/z2root_trace.log`。
  - ⚠ **`strace` は使えない**。z2root が既に ptrace しているので、同じプロセスへ二重に attach できない。ゲストの中を見るときは必ずこのトレース経路を使う。
  - **`Z2ROOT_NO_RECVMSG=1` で `recvmsg`(212) への介入を丸ごとやめられる**（0.8.346・調査専用）。fakeroot 配下では `recvmsg` が「**必ず exit まで取って受信 `SCM_CREDENTIALS` を書き換える**」唯一の経路で、X サーバとクライアントの通信もここを通る。原因が z2root の介入側にあるかどうかは、**介入を外して症状が消えるか**でしか決まらない（＝原因の成立条件を壊す確認）。⚠ 外している間は AF_UNIX の cred が実 uid のまま見えるので常用しない。
  - **トレース ON のときは `recvmsg` の exit で 1 行出す**（0.8.346）: `[z2trc] recvmsg pid= fd= ret= clen= pat=`。`ret` は受信バイト数（負なら `-errno`・`-11`=`EAGAIN`）、`clen` はカーネルが書き戻した `msg_controllen`、`pat` は `SCM_CREDENTIALS` を見つけて制御バッファを書き戻したか。`SYS nr=212` の行だけでは**戻り値が見えない**ので、「`EAGAIN` が返るまで読む」設計の X サーバがどこで判断を誤っているのかを追えなかった。
- ⚠ **ssh から起こした GUI が死ぬときの X サーバは、寝ているのではなく回っている**（0.8.346・実機で確定）。⛔ **この節は「窓がまるごと出ない」= `accept` 症状の話**で、**利用者の「タップするまで窓が出ない」には当てはまらない**（そちらでは X は**寝ている**。0.8.350 の節を見ること）。⚠ **当時は 2 つを同じものだと思い込んでここに書いた**ので、読むときは症状を必ず確かめること:
  - **`Xvnc` が CPU を 34%、そのトレーサ (z2root) が 71% 使い続けている**（5 秒間の `utime+stime` で計測）。一方 **GUI 端末側は 0%**（`ppoll` で待機）＝ **止まっているのは端末で、X サーバは何かを回している**。
  - `Xvnc` の `/proc/<pid>/syscall` を 200 回サンプルすると、**pc が常に同じ 1 命令**（libc の `neg w0,w0` → `str w0,[errno]`）だった。これは **syscall がエラーを返した直後の errno 設定**そのもので、**失敗する syscall を高速に呼び直し続けている**ことを意味する。
  - この間、**新しい X クライアント（`xprop` 等）の接続にも一切応答しない**（10 秒でタイムアウト）。`_NET_CLIENT_LIST` が空なのは「窓が map されていない」ではなく「**クライアントが 1 つも受け付けられていない**」ため。
  - ⚠ **RFB クライアント（z2term 側）を繋がなくても再現する**。ssh から `z2gui start` を叩くだけで同じ状態になるので、**実機の画面を触らずに切り分けられる**（`DISPLAY=:N xprop -root _NET_CLIENT_LIST` が返るかどうかで数えられる）。
- ⭐ **原因は `accept(2)` だった（0.8.347 で修正）**。回っていた syscall は **`accept`(202)** で、Xvnc の stderr（`/tmp/z2gui-xvnc-<N>.log`・z2gui スクリプトの出力とは**別ファイル**）に `_XSERVTransSocketUNIXAccept: accept() failed` が積もっていた。⚠ Android の untrusted_app seccomp は **`accept`(202) を禁じている**（bionic は `accept4` しか使わない）ので SIGSYS で弾かれ、z2root はそれを `ENOSYS` に化かす。**X サーバは listen fd が readable な限り accept をやり直す**作りなので、そこで**接続を 1 つも受け付けないまま CPU を焼き続ける**。
  - これを塞ぐための `accept`→`accept4` シム（`z2accept`・`LD_PRELOAD`）は前からあるが、**Xvnc に載っていなかった**。エンジンは `LD_PRELOAD` を z2root へ渡しているのに、**環境変数を作り直す経路（ssh のログインシェル経由など）で落ちる**ためで、`/proc/<Xvnc>/maps` に `z2accept` が 1 つも出ない。
  - **同じ手順で 3 回測って再現率 100%**（`:7`/`:9` = シムなし → `xprop` タイムアウト・accept 失敗 **232,454 回** と **229,579 回**・`_NET_CLIENT_LIST` 空 / `:8` = シムあり → `xprop` 即答・失敗 **0 回**・窓が出る）。
  - **直し方**: `z2gui` が**自分で `LD_PRELOAD` を立てる**（渡ってきていればそのまま使う）。GUI 一式を起こすのはこのスクリプトだけなので、**どの経路から呼ばれてもシムが載る**。⚠ 環境変数の伝播に頼る形は、経路が 1 つ増えるたびに壊れる。
  - ⛔ **ただし 0.8.347 は利用者の症状には一ミリも効かなかった**（2026-08-15・実機で確認）。⚠ **症状が違うのに「再現した」と思い込んだ**のが原因。ssh から `z2gui start` を叩いて見ていたのは「**X が接続を 1 つも受け付けない＝窓がまるごと出ない**」現象で、利用者の症状とは**別物**だった。⭐ **「再現した」と言う前に、利用者の言う症状と自分が見ている症状が同じか照合する。** 上の「X サーバは寝ているのではなく回っている」も、**この accept 症状のときの話**であって本症状には当てはまらない（本症状では逆に**寝ている**。下記）。
- ⛔⛔ **以下の「EPOLLET が原因」は 0.8.351 で棄却された。** 0.8.350 の介入自体は**完全に効いている**（実機で `/proc/<Xvnc>/fdinfo/<epfd>` が `events: 80000019` → **`events: 19`** に変わったことを確認）。**それでも症状は出た**ので、`EPOLLET` は原因ではなかった。⭐ **レベルトリガなのに全員が寝ている＝どの fd にも読むべきデータが 1 バイトも無い**ということなので、そもそも「取りこぼし」ではなかったと分かる。真因は下の 0.8.351 の項。⛔ **さらに「害が無い」も 0.8.392 で覆った** — 残していたこの変換自体が**エッジトリガ前提の非同期ランタイムを壊していた**ので、**既定 OFF** になった（下の 0.8.392 の子項目）。**以下は当時の測定記録として読むこと。**
- ⭐⭐ ~~**本症状（タップするまで端末の窓が出ない）の原因は epoll のエッジトリガ (`EPOLLET`) の取りこぼしだった**~~（0.8.350・**棄却済み**）。GUI タブを開いて放置した状態を測ると:
  - **Xvnc・端末・ウィンドウマネージャの 3 つとも CPU 0 ticks**（`utime+stime` の 5 秒差分）・`wchan` は `do_epoll_wait`/`do_sys_poll`・`/proc/<pid>/syscall` の 200 サンプルが**全部 `epoll_pwait`**。＝ **誰も回していない。全員が「通知が来ない」と思って寝ている。**
  - **`accept() failed` は 0 件**（＝ 0.8.347 で直した症状ではない）。**X サーバは生きていて新しいクライアントの接続には即答する**（`xprop` が `rc=0`）。
  - ⭐ **`/proc/<Xvnc>/fdinfo/<epfd>` を見ると、端末との接続だけが `events: 80000019`（`0x80000000` = `EPOLLET`）**。その fd の socket inode は**端末側の fd の inode と対**（連番）になっているので、どの接続かを取り違えずに特定できる。
  - **`_NET_CLIENT_LIST` は空**＝窓はまだ 1 つも作られていない。⚠ 画面は**真っ黒でマウスカーソルだけ**なので、**「窓は出ているが更新されない」ではなく「窓がまだ無い」が正しい**（利用者の言う「出ている」は GUI タブ自体のこと）。**症状の言葉を測定で置き換えること。**
  - **タップした瞬間に窓ぶんの矩形が届く**: `req#4` の **4 分 58 秒後**に `upd#4 rects=12 bbox=(0,0)-(828,934)`。⚠ **新しいクライアントを 1 つ繋いでも動かない**（`xprop` を打っても更新は 1 件も増えない）＝ **取りこぼした fd は再チェックされない**。
  - **なぜ起きるか**: Xorg は X クライアント接続を **`EPOLLET` で登録し「`EAGAIN` が返るまで読み切る」前提**で動く。ptrace 配下ではその読み切りが崩れることがあり、**エッジトリガは一度取りこぼすと二度と通知を出さない**。タップで直るのは、入力イベント → X が端末へ配送 → 端末が X へ書く → **新しいデータ到着でエッジが立つ** → 溜まっていた要求がまとめて流れる、という連鎖が回るため。⚠ **端末の種類の問題ではなくタイミングの問題**なので、GUI 全般に効く。
  - **直し方（0.8.350）**: **z2root が `epoll_ctl`(21) の `EPOLLET` を落とす**（レベルトリガへ倒す）。レベルトリガなら**まだ読めるデータがある限り毎回通知される**ので取りこぼしが**原理的に**起きない。Xorg は `EAGAIN` まで読む作りなのでレベルトリガでも正しく動く（通知の回数が増える分だけ僅かに遅くなるだけ）。⚠ **`EPOLLONESHOT`(1<<30) は落とさない** — あちらは「1 回通知したら無効化する」前提で呼び出し側が毎回 `MOD` し直す設計なので、落とすと逆に多重通知で壊れる。⚠ `epoll_ctl` は **fd の登録時にしか呼ばれない**（通信のたびに呼ばれる `read`/`recvmsg` とは桁が違う）ので、トレース対象に足しても速度への影響はほぼ無い。~~**既定 ON**で、従来動作へ戻すには `~/.z2root_env` に `Z2ROOT_KEEP_EPOLLET=1`~~ → **0.8.392 で既定 OFF**（次項）。
  - ⛔⛔⛔ **0.8.392 で既定 OFF へ。この変換自体が別の症状を作っていた。** 0.8.351 で「原因ではない」と分かった後も「害が無い」として残していたが、**エッジトリガ前提の非同期ランタイム（Rust の tokio/mio 等）を壊す**。ああいう実装は「一度 readable と言われたら `EAGAIN` まで読み切る責任は自分にある」前提で readiness を持ち回るので、レベルトリガに倒されると**読み切るまで `epoll_wait` が即座に返り続け**、reactor が空転して CPU を焼き、その裏で回るはずの入力処理まで進まなくなる。実機で測った状態（2026-08-24・TUI が選択肢の画面で固まる・moto g66j / Arch）:
    - 主スレッドが **`Δcpu=192 tick/2s`（≒ CPU 100%）・`syscall=running`**（ユーザー空間で空転）。ワーカーも同様。
    - `/proc/<pid>/fdinfo/<epfd>` で **端末の入力 fd が `events: 2019`**（`EPOLLET` が落ちている）。
    - **Enter を 5 回送っても出力 0 バイト** = 画面は描けているのにキーが一切効かない。利用者からは「TUI を開いて選択肢が出るとフリーズする」に見える。
    - 落とさない設定に変えると `events: 80002019` のまま通り、**同じ TUI が普通に操作できる**ことを実機で確認。（データを 1 つ置いて `epoll_wait` を回す実測では、レベルトリガだと **0.5 秒で 20 万回**返る）
    - ⭐ **レベルトリガで安全なのは「`EAGAIN` まで読み切る」設計のプログラムだけ**（Xorg はそう書かれている）。**ptrace 配下の全プロセスへ一律に掛けてよい変換ではなかった。**
    - 切り分けの口は名前を変えて残す: `~/.z2root_env` に `Z2ROOT_DROP_EPOLLET=1` で従来どおり落とせる（`Z2ROOT_KEEP_EPOLLET` は廃止）。既定 OFF なので `epoll_ctl` は seccomp フィルタからも外れ、**停止が 1 種類まるごと無くなる**。
- ⭐⭐⭐ **真因（0.8.351・実機で確定）: 端末の窓は出来ているのに、ウィンドウマネージャがそれを「管理」していなかった。** `z2gui` が **openbox を起こした直後に、待ち合わせをせずに端末を起こしていた**のが引き金。
  - **測って分かったこと**（GUI タブを開いて放置・タップ 0 回）:
    - **X も端末もウィンドウマネージャも、個別には全部健全だった。** Xvnc の `epoll_pwait` の **timeout 引数が 599,448ms ≒ 600 秒**（X のスクリーンセーバー既定値）＝ **控えている仕事が他に無い**。端末（urxvt）も **timeout が約 17 日**＝完全に暇。⭐ **`/proc/<pid>/syscall` は引数まで読めるので、「何を待っているか」ではなく「あとどれだけ待つつもりか」が分かる。** これが「詰まっている」と「暇をしている」を分ける決め手になる。
    - **端末は起動を完走している**: 窓 `0x200009` を作り、`WINDOWID` を渡して子シェルを fork し、pty に流し込めば読んで描く（大量に書き込むと CPU が動く）。**stderr は 0 バイト**＝エラーは 1 行も出していない。
    - ⭐ **なのに `_NET_CLIENT_LIST` は空で、その窓の `WM_STATE` は `not found`** ＝ **openbox がその窓を管理していない**（＝ map されていない）。openbox 自体は正常で、`_NET_WM_NAME = "Openbox"` を名乗り EWMH 一式を宣言し、`openbox --reconfigure` にも反応する。
    - ⭐⭐ **X に新しい動きを 1 つ与えると、溜まっていた分がまとめて処理されて窓が出る**。ssh から 2 つめの端末を起こしたら `_NET_CLIENT_LIST` が `0x200009, 0x60000a` になった ＝ **固まっていた 1 つめも一緒に出た**。
  - **なぜ「タップすると出る」に見えたか**: タップは X に入力イベントを届けるので、上記の「新しい動き」の 1 つになるだけ。⛔ **タップは本質ではない。** `xsetroot` で背景色を変えるだけでも、**タップせずに画面は更新される**（実機で確認）。⚠ **「タップするまで表示が更新されない」という症状の言葉自体が誤りだった。** 更新は普通に届いていて、**出ていなかったのは端末の窓だけ**。
  - **なぜ起きるか**: openbox は起動時に `SubstructureRedirect` を取ってから既存の窓を拾い集める。その最中に端末が `XMapWindow` すると、**MapRequest は openbox の（Xlib 内部の）待ち行列に入るだけで処理されず**、openbox は socket に読み残しが無いまま `ppoll` で寝てしまう。**待ち行列に残った要求は、次に別のイベントが来るまで誰も見に行かない。** ptrace 配下は全体が遅いので、端末の map がちょうどこの隙間に着弾しやすい。
  - **直し方（0.8.351）**: `z2gui` が **openbox の起動を待ってから端末を起こす**。Xvnc には元から起動待ちがあったのに **openbox だけ待ち合わせが無かった**。加えて**保険**として、端末を起こした後に `openbox --reconfigure` を 1 回だけ撃つ（待ち行列に残った MapRequest がここで処理される）。⚠ **突く手段に `xprop` を使わないこと** — どの distro でも導入対象に入っていないうえ、**X に接続するだけでは openbox は起きない**（実機で `xprop` を何度叩いても窓は出なかった）。**openbox 自身が選んでいるイベントを送る**必要があり、`openbox` は GUI 一式に必ず含まれるので確実に手元にある。
- ⛔ **Alpine では Konsole を選べない（0.8.353）**。Alpine の konsole は**窓を作る最後の段階で必ず segfault する**（2026-08-15 に実機で確認）。⚠ **導入不足ではない**: 共有ライブラリの `not found` は 0 件、Qt の `libqxcb-egl-integration.so` あり、dbus あり、`apk info -R konsole` の依存も欠落 0、`apk info -L konsole` のファイルも欠落 0。**GL も健全**で、ドライバ導入後は `qt.qpa.gl: Xcb EGL gl-integration successfully initialized` と出て EGL が 9 個の設定を列挙する。フォントも正常（`fc-match monospace` が Noto Sans Mono を返し、Qt からは 24 ファミリ見える）。ロケール・`qmlcache`・キャッシュ汚染・konsole 自身のプラグイン・画像プラグイン・遅延シンボル解決・スタックサイズも全部外れ。⭐ **同じ画面で xterm / rxvt-unicode / LXTerminal は動く**し、**KDE の初期化自体は通る**（`konsole --list-profiles` は正常終了する）。⇒ **Alpine の konsole そのものの問題**として扱い、組み合わせの成立を止める。
  - **止め方**: `GuiTerminal.isUnsupported(terminalId, distroId)` に置く（UI に条件を散らさない）。⭐ **断るだけで終わらせない** — Alpine を選ぼうとした側からは **「xterm に切り替えて開く」を 1 タップ**で実行できる（設定を往復させないため。`NoOsNoticeCard` で戻り道を残したのと同じ考え方）。逆向き（Alpine のまま Konsole を選ぶ）は説明して選択を適用しない。⚠ 切替の順序は **端末を先に確定 → distro を切替**。逆だと切替後の初回起動が Konsole のまま走る。
  - ⚠ **選べてしまうことが一番の害だった**: GUI タブが理由も出ないまま真っ黒になり、原因がどこにも出ない。⭐ **動かないと分かっている組み合わせは、警告ではなく成立させないほうがよい。**
  - ⚠ **`gdb` では追えない**。z2root が tracer なので `PTRACE_ATTACH` が `Operation not permitted` で弾かれる（1 プロセスに tracer は 1 つ）。**logcat / tombstone にも残らない**（SIGSEGV を z2root が先に受け取るので debuggerd まで届かない）。追うなら z2root 側に診断を足すことになる。
- **GUI は自分から開かない（0.8.254 で自動連動を廃止）**。以前は interactive shell の preexec フック（bash = DEBUG トラップ / zsh = `add-zsh-hook preexec`）が実行前のコマンドを `z2-autogui` に渡し、**GUI バイナリと判定したら GUI タブを自動で開いて**いた。判定は「`libX11` / `libxcb` / GTK / Qt にリンクしているか」だったが、**クリップボード連携のために X を張るだけの CUI アプリが必ず引っかかる**（実機報告: テキストエディタを開いただけで GUI タブが出る）。CUI を使っているだけの人の画面を奪うので、**判定を賢くする方向ではなく仕掛けごと畳んだ**。設定での ON/OFF も足さない — **誤爆する機能を選べるようにしても選ぶ理由が無い**。GUI を開く道は「GUI タブを自分で開く」か「`z2run <アプリ>` と明示的に打つ」の 2 つだけ。⚠ フックは rootfs の rc に書き込み済みなので、**入れるのをやめるだけでは既存環境に残る**。`ProotLauncher.removeAutoGuiHook` が launch 毎にマーカー行ごと取り除き、`/usr/local/bin/z2-autogui` も消す（ユーザーが自分で書いた行は触らない）。

- **リモート VNC (A1・0.8.418)**: `GuiSession(remote = VncTarget(host, port, password, name))` で、**z2gui も proot も起動せずに外の VNC サーバへ繋ぐだけ**のタブになる。描画・入力・キーボード・クリップボード・ズーム/パンはローカル GUI と同じ `RfbClient` / `GuiScreen` / `GuiInputView` の上に乗るので、**新しく作ったのは「接続先を差し替える口」と「VNC 認証」だけ**。
  - **ローカルとの違いは 3 つだけ**: ①Linux 側を起動しない（`connectWithRetry` で粘らず、繋がらなければその場で理由を出す）②`requestResize` を送らない（**相手の実画面の解像度を勝手に変えてしまう**ため。枠に収めるのは中央フィットとズーム/パン）③音声ブリッジを張らない。
  - **ディスプレイ番号を消費しない**（`display = 0`）。Xvnc を立てないので `:N` を取る理由が無く、端末タブ・ローカル GUI（1 以上）と衝突しない。同じ相手へ 2 枚開くのも自由（前面化しない）。
  - **認証は None (1) と VNC 認証 (2)**（`gui/rfb/VncAuth.kt`）。VeNCrypt (19)/TLS は未対応で、**名指しで「未対応」と伝える**（黙って失敗させない）。⚠ **DES 鍵は各バイトのビット順を反転する**のが RFB の癖で、ここを飛ばすと「パスワードは合っているのに毎回拒否される」になる。⭐ ソケットに触らない部分（方式の選択・鍵・応答）だけを `VncAuth` に切り出してあり、`VncAuthTest` が **openssl で作った期待値**と突き合わせる。
  - **ProtocolVersion は 3.8 / 3.7 / 3.3 に合わせられる**。リモートには 3.3 しか話さないサーバが居るため。Apple の "RFB 003.889" のような独自版は 3.8 として扱う。⭐ **VNC でない相手に繋いだ場合もここで分かる**（sshd に繋ぐと "SSH-2.0-Open" が読めるので、そのまま理由に出す）。
  - ⚠ **`SecurityResult` を読むのは「3.8」か「VNC 認証のとき」だけ**。3.3/3.7 の None では何も来ないので、読みに行くと次のメッセージを食って画面が出ない。
  - **繋ぎ直しに備えて `connect()` は前の残骸を畳む**（ソケットを閉じ、**ZRLE の `Inflater` を `reset`**）。zlib ストリームは接続ごとに最初からなので、使い回すと前の接続の続きとして展開して画面が壊れる。
  - **カーソルとポインター操作（0.8.427）**: `GuiSession` が `GuiCursor` を持ち、`GuiInputView`（入力）と `GuiScreen`（描画）が共有する。サーバがカーソルを framebuffer に含めなくても自前の矢印を必ず描き、タブ切替・View 再生成・再接続でも位置を保持する（初回だけ中央、画面縮小時は範囲内へ丸める）。既定は指の移動量で動かす**相対モード**。GUI の 📜 をダブルタップすると、触れた framebuffer 座標へ直接動く**絶対モード**と切り替わり、絶対モード中は矢印の根元に緑の輪を描く。📜 の単タップは従来どおりスニペットを開く。
  - **ダブルタップ操作（0.8.427）**: 2 回目を動かさず 350 ms 以上保持して**離した時点**で右クリック、時間前に離せばダブルクリック、距離が touch slop を超えれば左ドラッグにする。350 ms のタイマーは触覚通知だけを出し、押している途中では右クリックを確定しないため、狙いを定めてから動かしてもドラッグへ移れる。
  - **右クリックを待たせない（0.8.429・利用者の指摘）**: ⛔ **「350 ms 保持してから離す」をやめた。**「どれくらい待てばいいのか分からない」という指摘で、待ち時間を触覚で知らせても**離す判断が残る**ことが難しさの正体だった。⇒ **150 ms 経ったらタイマーがその場で右クリックを送る**（指を離すのを待たない）。⚠ **左ドラッグはこの 150 ms より前に動き始める必要がある** — 縮めるほど右クリックは楽になりドラッグは難しくなるトレードオフなので、値は 1 か所（`RIGHT_HOLD_MS`）に置く。⚠ **タイマーより先に `ACTION_UP` が届くことがある**ので、UP 側でも経過時間で判定して取りこぼさない。⚠ 触覚は「送った」合図として**送出と同じ場所**に置く（先に鳴らすと、メニューが出ないときに何が起きたのか分からない）。
  - **長押しで右クリック（0.8.431・利用者の要望）**: 右クリックへの道が「ダブルタップして 2 回目を保持」しか無く、**メニューを出すという一番よく使う操作が遠かった**。⇒ **1 本指を動かさず [`GuiCursor.HOLD_MS`] = 150 ms 保持したら、指を離すのを待たずに右クリックを送る**（ダブルタップ側と同じ「時間が来たら送る」形）。単タップ = 左クリック、ダブルタップ = ダブルクリックは**変えない**。⭐ **押している間は待ち時間を見せる** — `GuiCursor.holdStart` に `uptimeMillis` を立て、`GuiScreen` が矢印の先端に **150 ms で一周する緑の弧**を描く（`withFrameMillis` のループは押している間だけ回す）。⚠ **時間の定数は `GuiCursor.HOLD_MS` 1 か所**。入力側と描画側で別々に持つと「輪が閉じたのに押されない」がすぐ出る。ダブルタップ側の `RIGHT_HOLD_MS` もこれを指す — **同じ「保持したら右クリック」で待ち時間が違うと体で覚えられない**。⚠ **輪の大きさは画面密度で決め、表示倍率に掛けない**（縮小表示で指の下に隠れる）。⚠ **M8-6 T3/T4/T5 で一度廃止した経路なので、干渉は入口で断つ**: ①ダブルタップの 2 回目は `pendingRightClick` が立っているので長押しを始めない（`gesture.onTouchEvent` が先に走るのでこの判定が効く）②2 本指になったら捨てる ③touch slop を超えたら捨てて普通のカーソル移動にする ④**成立したら同じタッチの `onSingleTapUp` を握り潰す** — `setIsLongpressEnabled(false)` にしてあるので、GestureDetector は**何秒押していてもタップとして UP を通す**。ここを塞がないと右クリックの直後に左クリックが出る。
  - **🖱 でカーソルモードを切り替える（0.8.431・利用者の指摘）**: 相対/絶対の切替が **📜 のダブルタップ**に隠れていた。⛔ **画面のどこにも出ておらず、しかも「コマンド一覧」と意味が繋がらない** ⇒ 誰も辿り着けない。⇒ **`ToolbarButtons.POINTER_MODE`（🖱）を新設**し、GUI タブのツールバーに 1 個のボタンとして出す。点灯 = 絶対モード。⭐ **`guiOnly = true` を `ToolbarButtonSpec` に足した**（既存の `terminalOnly` の逆）。端末タブには意味が無いので出さない。⚠ **`canHide = true`**（設定 › 表示 › ツールバーで消せる）— §0-B の約束。⚠ GUI タブはボタンが 5 個しか無く**枠が空いている**ので、隠し操作をやめてボタンにする余地がここにあった。
  - **輪はタップの時間を越えてから出す / 保持は 300ms へ（0.8.433・利用者の指摘）**: ⛔ **指を置いた瞬間に輪を出していた。** タップでもドラッグでも一瞬光って「うざい」。⭐ **タップと長押しは別の操作なので、見た目も分けなければならない** — 押した瞬間に長押しの表示を始めると、**まだタップかもしれない指に長押しの顔を見せている**ことになる。⇒ `GuiCursor.HOLD_RING_DELAY_MS` = 150ms（Android のタップ判定 `ViewConfiguration.getTapTimeout()` = 100ms を確実に超える値）だけ待ってから輪を描き始め、残り 150ms で一周させる。⚠ **タイマーは 2 本になる**（輪を出す / 右クリックを送る）ので、取り消しは**両方**外す。⚠ 描画側は「輪を出し始めた時刻」を受け取るので、進み具合の分母は `HOLD_MS` ではなく `HOLD_RING_MS`。⭐ **保持は 150ms → 300ms**（利用者の指定）。ダブルタップ側の `RIGHT_HOLD_MS` も同じ値を指しているので一緒に伸び、**ダブルタップからドラッグを始める余裕が倍**になる（0.8.429 で「短くするほどドラッグが難しくなる」と書いたトレードオフの、逆方向への揺り戻し）。
  - **マウスポインターの字は Unicode に無い（0.8.433 → 0.8.434 で 🖱 へ差し戻し）**: 「🖱 は**マウス本体**の絵で、切り替える対象（画面上のカーソル）と一致しない」という指摘は正しい。⇒ `↖` (U+2196 + VS16) を試したが、**方向を指す矢印であってポインターの絵ではない**ため実機で却下。⛔ **矢印カーソルの絵文字は Unicode に存在しない**（🖱 U+1F5B1 はマウス本体、🖰/🖯 は端末のフォントに無い）。⇒ **🖱 に戻す。** ⭐ **ツールバーは絵文字を「文字」として描く作り**なので、選べるのはフォントに在る字だけ — ここを変えたければ**アイコンを字ではなくベクタ画像で描けるようにする**改修が要る（`ToolbarChip` と設定画面の `CATALOG` プレビューの両方）。⚠ 字を変えるときは **`CATALOG` の代表アイコンとツールバーが実際に描く字を同時に**（片方だけだと設定画面とツールバーで字形が食い違う）。
  - **ダブルタップ側の右クリックを外す（0.8.435・利用者の報告「ダブルタップ判定でドラッグできない」）**: ⛔ **時間で確定する判定が、あとから来るドラッグを食っていた。** ダブルタップの 2 回目を押している間、`RIGHT_HOLD_MS` 経過で右クリックを送って `pendingRightClick` を落としていたので、**その後に指を動かしても左ドラッグに入れない**（MOVE がただのカーソル移動へ落ちる）。⇒ **ダブルタップの 2 回目は「動かす＝ドラッグ / 離す＝クリック」だけを見る。タイマーは張らない。** ⭐ **待ち時間が無くなったので、押したまま狙いを定めてから動かせる**（0.8.427 が狙っていた挙動に、遠回りして戻った）。⭐ **右クリックは 1 本指の長押し (0.8.431) に一本化**する — 同じ操作に道が 2 本あり、そのうち片方が別の操作 (ドラッグ) を壊していたなら、**壊している方を消すのが筋**。⚠ これで `RIGHT_HOLD_MS` / `rightClickRunnable` / `rightClickFired` は不要になった (`rightClickFired` は**どこからも読まれていない死んだ状態**だった)。⭐ **教訓: 「時間が来たら自動で送る」は、その操作が終点のときだけ安全。** 続きがある操作 (ドラッグの入口) に置くと、待っただけで先へ進めなくなる。
  - **1 回目のタップのクリックを保留する（0.8.436・利用者の報告「ダブルタップ後わずかに停止してドラッグしないと正しく動作しない」）**: 0.8.435 でタイマーを外してもまだ直らなかった。⛔ **原因はこちらの判定ではなく、相手に届く順番だった。** 1 回目のタップで**実際に左クリックを送っている**ので、続けてドラッグに入ると相手側には「クリック → 至近距離ですぐ押下」= **ダブルクリックしてから引きずった**と見える（間に一瞬止めると、相手のダブルクリック判定の時間から外れて正しくドラッグになる ⇒ 利用者の「停止すれば動く」と一致）。⇒ **タップの左クリックを `doubleTapMs` (= `ViewConfiguration.getDoubleTapTimeout()`) だけ保留する**: ①2 回目の指が来たら**捨てる**（ドラッグかダブルクリックのどちらかになる）②関係ないタップが来たら**その場で送る**（取りこぼさない）③時間切れなら送る。⭐ **ダブルクリックは 2 回目を離した時に「左を 2 連打」**して作る（1 回目を捨てているため）。⚠ **代償はクリックが `doubleTapMs` 遅れること。** 相手のダブルクリック判定より短い間隔で「クリック → 押下」を出さない方法は他に無い。⛔ **位置をずらして誤魔化さない** — 相手側のしきい値 (GTK/Qt で 5px 程度) と表示倍率に依存するので、条件が揃えば再発する。⭐ **カーソルの追従は遅れない**（遅れるのはボタンを押す瞬間だけ）。⚠ **判定を `GestureDetector` から自前へ移した** — 「クリックを保留する窓」と「ダブルタップと認める窓」は**同じ 1 つの時間**でなければならず、2 つの仕組みが別々に持つとずれる。`onDoubleTap` が**1 回目の DOWN の座標**を渡してくる癖 (AOSP の作り) からも解放される。
  - **カーソルが 2 個見える問題（0.8.431・利用者の指摘）**: 原因は **`SetEncodings` にカーソル擬似エンコーディングを載せていなかった**こと。RFB では**クライアントが Cursor (-239) / XCursor (-240) を要求しない限り、サーバはポインタを framebuffer に焼き込んで送る**。こちらは自前の矢印を必ず重ねるので、相手のポインタと並んで 2 個になる。⇒ **要求に加える**（対応サーバは焼き込みをやめ、形だけを擬似矩形で送ってくる）。⚠ **要求したら読み飛ばす責任が生まれる** — `Cursor` は `w*h*4 + ((w+7)/8)*h` byte、`XCursor` は `6 + ((w+7)/8)*h*2` byte。1 バイトでもずれるとストリームが壊れて画面が止まる。`w*h == 0` は「カーソル無し」で本体は付いてこない。⚠ **これらは描画矩形ではない**ので、更新範囲 (バウンディング) の計算からも外す。⭐ **形は使わず、自前の矢印を描き続ける** — 相手の 24px カーソルは縮小表示だと指で狙えない大きさになるので、画面密度で決めた矢印のほうが携帯では使える。要求を無視するサーバでは今までどおり焼き込まれた 1 個と矢印が並ぶが、それは要求を足す前と同じ状態で退行ではない。
  - **リモート日本語入力キー（0.8.427）**: キー配列の `NamedKey` に「半/全」「変換」「無変換」「かな」「英数」を追加する。端末タブでは no-op、GUI タブでは対応する X11 keysym (`Zenkaku_Hankaku` / `Henkan` / `Muhenkan` / `Hiragana_Katakana` / `Eisu_toggle`) を直接送る。物理キーボードの同名 Android keyCode も同じ keysym へ変換する。相手の入力方式が半角/全角ではなく `Ctrl+Space` / `Super+Space` を使う場合は、既存の修飾つき一撃をキー配列へ置く。

  - **長押し成立を 500ms へ遅らせる（0.8.438・利用者の報告「ポインターをゆっくり動かすだけで長押しになる」）**: 300ms では、意図的にゆっくり動かした指がまだ `touchSlop` 内にいるうちに長押しタイマーが成立し得た。⇒ `GuiCursor.HOLD_MS` を 300ms から標準的な長押しに近い **500ms** へ、リング開始を 150ms から **250ms** へ延ばす。`touchSlop` を越えれば従来どおり両タイマーを即座に取り消すので、変えるのは「右クリックを確定する前に、ゆっくりした移動が移動として確定できる猶予」だけ。

- **クリップボードを相手と共有する (0.8.475)**: GUI タブが前面の間、Android でコピーされた本文を相手へ送り、相手でコピーされた本文を Android へ取り込む。送る口は `RemoteDesktopClient.sendClipboardText` で、対応しない実装のために「何もしない」既定を持たせてある。
  ⚠ **エコーループを止める仕掛けが要る。** リモート → Android の `setPrimaryClip` でも `OnPrimaryClipChangedListener` は発火するので、素直に繋ぐと受け取った本文をそのまま相手へ送り返し続ける。`GuiSession.syncAndroidClipboardToRemote` は**自分が直前に入れた 1 回だけ**送り返さない。
  - **VNC (RFB)**: `ClientCutText` (type 6)。classic RFB の文字集合は Latin-1 だが、現行の TigerVNC / x11vnc は UTF-8 の本文も受け付ける。⇒ **Latin-1 に収まる本文だけ Latin-1、収まらない本文は UTF-8** で送る。一律 Latin-1 にすると日本語が `?` に潰れる。
  - **RDP (CLIPRDR)**: GCC `CS_NET` で静的仮想チャネル `cliprdr` を要求し、MCS のチャネル routing (`RdpActivation.channelData` / `RdpMcs.sendVirtualChannel`) を敷いたうえで [MS-RDPECLIP] の**テキストとファイル**を話す (`gui/rdp/RdpCliprdr.kt`)。宣言するのは `CF_UNICODETEXT` と `FileGroupDescriptorW` だけで、HTML や画像形式は宣言しない (受け取れないものを宣言すると相手が送ってくる)。16KB を超える本文は `CHANNEL_FLAG_FIRST` / `CHANNEL_FLAG_LAST` で分割して送る (`RdpChannelReassembler`)。
  - **クリップボードのファイル (0.8.482)**: `CB_STREAM_FILECLIP_ENABLED` + `CB_FILECLIP_NO_FILE_PATHS` で**パスではなく中身**をやり取りする (こちらのファイルシステムを相手に見せずに済む)。⛔ **取り寄せの途中で待たない** — 受信ループから呼ばれるので「要求して応答を待つ」と書くと RDP 全体が止まる。⇒ **応答が来たら次を要求する**状態機械にする。
    - ⛔⛔ **コピーしただけで中身を取り寄せない (0.8.483)**。相手側のコピーは**相手の中だけで完結することも多い** (別フォルダへ移すだけ等)。そのたびに端末へ落としていたら、通信も置き場も浪費する。⇒ **一覧 (`FILEDESCRIPTORW`・1 件 592 バイト) だけを受け取って「何が来ているか」を見せ、中身は利用者が受け取ると決めたときだけ**取りに行く (`RdpCliprdr.receiveOfferedFiles`)。⭐ **RDP は元々この作法**で、こちらのファイルを渡すときも相手は**貼り付けた瞬間に初めて**中身を要求してくる。受け取り口は GUI 画面の下端の細い帯 (`GuiScreen.ClipboardFileBar`) で、無視すればそのまま消える (操作を増やさない)。
    - **📎 でスマホのファイルを選んで渡す (0.8.484)**。⛔ **system clipboard に載るのを待たない** — Android の「コピー」は**ファイル管理アプリの中だけで完結し、`content://` を system clipboard に載せない実装が多い**ので、`ClipData` を見張るだけでは渡す手段が無い端末が出る。⇒ GUI ツールバーに 📎 (`ToolbarButtons.CLIPBOARD_FILE`・**GUI タブ限定**) を置き、`OpenMultipleDocuments` で選ばれた URI をそのまま `ClipboardFiles.Source` にする (`ClipboardFileTransfer.fromUris`)。⭐ ボタンを出すのは `RemoteDesktopClient.supportsClipboardFiles` が true の実装だけ (VNC には出ない)。⚠ **サイズを返さない provider がある** — `FILEDESCRIPTORW` には大きさが要るので、`OpenableColumns.SIZE` が無ければ `openAssetFileDescriptor` の length で測る。取れなければその 1 件を諦める (0 と偽らない)。
    - ⛔⛔ **相手が要求したバイト数を丸めない (0.8.484)**。`FILECONTENTS_RANGE` の応答を**受信側の刻み `CHUNK_BYTES` (64 KiB) で `coerceIn` していた**。Windows は 1 回に 64 KiB より大きい範囲を要求し、**要求より短い成功応答を EOF と解釈する**ので、大きいファイルは途中で切れたまま貼り付けが終わる。⇒ **要求された長さのまま返す**。⭐ 刻んでよいのは**こちらが取りに行くときの刻み**だけで、相手の要求の刻みではない (向きが違う)。⚠ 無制限に読ませないため `MAX_OUTGOING_CHUNK_BYTES` (4 MiB) を上限にし、それを超える要求・**位置がファイルの外**・flags 不正 (`SIZE` と `RANGE` の同時指定等) は `CB_RESPONSE_FAIL` を返す。⛔ **ただし「残りが要求より少ない」は断る理由にならない** — ここを FAIL にしていたため最後の端数だけ落ちていた (→ 0.8.486)。
    - ⛔ **clipboard の 1 通で RDP を切らない (0.8.484)**。CLIPRDR は画面・入力・音とは**独立した任意チャネル**なのに、`acceptChannelChunk` の例外が受信ループごと落として**デスクトップまで切れて**いた。⇒ `acceptChannelChunkSafely` で捕まえ、`RdpChannelReassembler` を reset して**分割途中を捨て、進行中の受け取りだけ失敗として畳む** (`abortIncoming`)。⭐ 次の正しい PDU から何事も無く再開する。
    - ⛔⛔ **一覧だけ差し出しても相手は貼り付けられない (0.8.485・利用者の報告「エラーで転送できませんでした」)**。Format List に `FileGroupDescriptorW` しか載せていなかった。Windows は **「名前と大きさ (`FileGroupDescriptorW`)」と「中身 (`FileContents`)」が並んで**初めてクリップボードをファイルとして扱うので、片方だけだと貼り付けの瞬間に中身の出しどころが無く、相手側でエラーになる。⇒ **こちらがファイルを差し出すときは 2 つ並べて announce する**。⚠ 中身そのものは Format Data Request では渡さない (`FILECONTENTS_REQUEST` で来る)。名前を並べるのは「その形式で出せる」と名乗るためである。
    - ⛔⛔ **後で取りに行くならロックする (0.8.485・利用者の報告「コピーしてローカルへもだめ」)**。［受け取る］を押されるまで中身を要求しない作りにした (0.8.483) のに、相手には何も伝えていなかった。**Format List を出した相手は、こちらが黙っていればそのデータを手放してよい** ([MS-RDPECLIP] Lock Clipboard Data)。⇒ **一覧を見せている間は `CB_LOCK_CLIPDATA` で保持を頼み、`FILECONTENTS_REQUEST` に `clipDataId` を付ける** (24 → 28 バイト)。受け取り終わり・一覧の入れ替え・✕ で閉じたとき・切断のいずれでも `CB_UNLOCK_CLIPDATA` で必ず外す (外し忘れると相手が抱えたままになる)。⚠ **`CB_CAN_LOCK_CLIPDATA` を宣言していない相手へは送らない** — 相手の General Capability Set を読んで決める (それまで `CB_CLIP_CAPS` は捨てていた)。
    - **受け取り口は ✕ で閉じられる (0.8.485)**。放っておいても消えるが、画面の下端を占める間は邪魔になる。⭐ **閉じたらロックも外す** — 受け取らないと決めたものを相手に抱えさせ続けない。
    - ⛔⛔ **末尾の端数を断らない (0.8.486・実機の記録)**。0.8.484 で入れた範囲チェックが `残り < 要求` を「範囲外」として `CB_RESPONSE_FAIL` にしていた。相手は最後まで**同じ大きさ (実測 256 KiB) で要求してくる**ので、25 MB のファイルなら 96 回成功したあと最後の 1 回だけ落ちて、転送全体が失敗する。⇒ **残っている分だけ返す**。⭐ **要求より短い成功応答こそが EOF の伝え方**であって、こちらから刻んではいけないのは**ファイルの途中**の話である (0.8.484 と混同しない)。位置そのものがファイルの外にあるときだけ断る。
    - ⛔⛔ **1 通に PDU が 2 つ以上入っている (0.8.486・実機の記録「invalid CLIPRDR message length」)**。相手は複数の PDU をまとめて 1 回のチャネル書き込みで送ってよい。先頭 1 つだけを見て「宣言された長さと全体の長さが違う」と判断していたため、**正しい応答を取りこぼしたうえ通ごと捨てて**いた (受け取りが必ず失敗していた原因)。⇒ **端から順に切り出して 1 つずつ処理する**。⚠ 切り出しに失敗したときは、先頭 32 バイトを 16 進で残す (どの PDU で崩れたかが分からないと切り分けられない)。
    - ⭐ **ファイルがあればテキストより優先する。** Windows はファイルをコピーすると**その置き場を指す文字列も一緒に announce する**ので、テキストを先に取ると中身の代わりにパスが届く。⚠ `FileGroupDescriptorW` は登録形式なので **id が相手ごとに違う**。名前で引く。
    - ⚠ フォルダ (`FILE_ATTRIBUTE_DIRECTORY`) は飛ばし、名前は葉だけにする (フォルダごとコピーされると `sub\name` の形で届く)。⚠ **空の応答は「もう出せない」**の意味なので、足りていなければ未完了として畳む (書きかけを残さない)。
    - **置き場は「ダウンロード / z2term」** (`clipboard/ClipboardFileTransfer.kt`・`MediaStore`)。⭐ **保存先を選ばせない**: 毎回選ばせると渡すたびに手が止まり、どこへ入れたかも分からなくなる。ダウンロードなら他のアプリのファイル選択からそのまま見え、権限も要らない。保存できたものは Android のクリップボードにも `content://` で載せる (対応するアプリならそのまま貼り付けられる)。⚠ **書き終わるまで `IS_PENDING`** で隠し、途中で切れたものは消す。
    - ⛔ **保存の I/O を受信スレッドでしない** (音と同じ理由)。`ClipboardFiles.Sink` の実装が 1 本のスレッドへ流す。
- **GUI タブでも「接続先」と明るさを使う (0.8.475)**: 📜 の接続先タブは「GUI タブに SSH の概念は無い」として隠していたが、**GUI を見ている最中に別のホストへ繋ぎたい場面は普通にある**。GUI タブからも SSH 接続 / SFTP / サービス起動を開けるようにし、🔅 のダブルタップも端末タブと同じ明るさ帯を出す。`showSshTab` の意味は「呼び出し元が接続先機能を提供できない特殊画面だけが false にする」へ変えた。

### 4.13 Android API ブリッジ (`Z2ApiBridge` / `Z2ApiScript`)

- 端末から Android 機能を叩くコマンド群。マクロは「トリガー (`z2-when` に登録。無いきっかけは events.jsonl の差分監視) → ロジック (シェル) → アクション (`z2-*`)」で組む。[マクロの書き方は `docs/ja/MACRO-GUIDE.md`](MACRO-GUIDE.md) を参照。

  | コマンド | 内容 |
  |---|---|
  | `z2-notify` | 通知を出す (`-b` 返事ボタン / `-c` コピーボタン) |
  | `z2-toast` | トースト表示 |
  | `z2-share` / `z2-open` | 共有 / URL・ファイルを開く |
  | `z2-img` | 端末に画像を描く (kitty graphics・0.8.495) |
  | `z2-clip (set/get)` | クリップボード (**前面のときだけ書ける**。下記) |
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
  | `z2-server` | **登録済みの常駐サーバーを起こす / 落とす** (0.8.310・F) |

  - **`z2-notify` のバナー表示 (0.8.163)**: `-h`/`--high`/`--banner` フラグ付きのとき `IMPORTANCE_HIGH` の別チャンネル (`z2term_api_high`) + `PRIORITY_HIGH` で**画面上部にバナー (ヘッドアップ) 表示**する。既定チャンネル (`z2term_api`) は `IMPORTANCE_DEFAULT` で作成済みのため後から重要度を上げられず (Android 仕様)、バナー用に別 ID のチャンネルを分けている
  - **裏で走るマクロはクリップボードに書けない → `z2-notify -c` の「コピー」ボタン (0.8.335)**: Android 10 以降、**フォーカスを持つアプリ（と選択中の入力方法）以外は `setPrimaryClip` を拒否される**。拒否は例外にならず `E ClipboardService: Denying clipboard access to …, application is not in focus` が出るだけなので、**端末側からは成功したように見える**。着信・SMS・通知をきっかけに走るマクロは性質上いつも裏にいるため、`z2-clip set` は静かに落ち、`unknown-call` は「番号が入らない」状態だった（実機報告 2026-08-13）。
    - 直し方は**当てになる道を 1 本用意する**こと: `z2-notify -c <文字列>` が通知に「コピー」ボタンを足し、押すと `service/ClipCopyActivity`（透明・`noHistory`・`excludeFromRecents`）が起きて**その瞬間だけフォーカスを取り**、書いて閉じる。通知アクションからの Activity 起動は人の操作なのでバックグラウンド起動制限にも当たらない。
    - ⚠ **書くのは `onWindowFocusChanged(true)` で**。`onCreate` / `onResume` の時点ではまだウィンドウがフォーカスを取っておらず、同じ拒否に遭う。
    - ⚠ **書けたかは読み返して確かめる**（`setPrimaryClip` → `primaryClip` の照合）。拒否されても戻り値も例外も無いため、これ以外に成否を知る方法がない。同梱サンプル（`otp-clip` / `otp-sms`）はこの読み返しで「前面なら直に入れる / 裏ならボタンに回す」を切り替える。
    - ⚠ **Android 13+ では自前のトーストを出さない**（OS がコピーの確認 UI を出すため二重になる）。
    - ⚠ ボタン枠は Android の仕様で 3 つまで。「コピー」を出したぶん `-b` の返事ボタンは 1 つ減る。
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

### 5.1.1 端末ログ (ツールバー ⚪・0.8.195)

画面に出た内容をテキストファイルに書き続ける機能。**分岐点は 1 か所だけ**で、
`TerminalSession.startReadLoop` の `emulator.processBytes` の**直後**に `SessionLogger.append` を置く
(タブに出るものが必ず通る唯一の場所。`writeBanner` 等のアプリ内部生成メッセージだけは別経路)。
processBytes の「後」なのは、**alt screen に入ったかどうかがその塊を処理した後でないと判定できない**ため。

- **スレッド**: `append` はバイト列を `SessionLogger` の単一スレッド executor に積むだけで、
  描画を直列化しているエミュレータスレッドをブロックしない。flush は 500ms 周期。

- **各行の先頭に日時を付けられる（0.8.256・既定 OFF）**。書式は **固定長**の `[yyyy-MM-dd HH:mm:ss] `（常に 22 文字）。桁が揺れると本文の開始位置がズレて、行頭に付けた意味が薄れる。**年から秒まで完全な日付**にするのは、日をまたぐ記録で「その 08:42 はいつのものか」を後から読めなくしないため。
  - ⚠ **生ログ (raw) には付けない**。バイト列がそのまま残ることが生ログの存在意義で、1 バイト足しただけで不具合報告の材料にならなくなる。設定シートでも生ログが ON の間はトグルを押せなくしてある（理由を察せるよう薄く出す）。
  - 時刻は**チャンク単位で 1 回**求める（同じ塊は同じ瞬間に届く）。改行の直後を「次の行頭」として**チャンクをまたいで持ち越す**ので、1 行が境目で割れても二重に付いたり付き損ねたりしない。挿入するのは ASCII だけなので UTF-8 の途中に割り込んで文字を壊さない。
  - 伏せ字の**後**に差し込む（`stampIfNeeded(maskIfNeeded(...))`）。先に付けると伏せ字の行判定に時刻が混ざる。
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
- **自動開始** (`sessionLogAutoStart`、既定 OFF、0.8.243): 「あとから見返そうとしたら録っていなかった」を無くす設定。
  判定は **`startReadLoop` の入口 1 か所**に置く — ローカル / android-sh フォールバック / SSH のどの起動経路も
  必ず通るので、経路を足したときに付け忘れが起きない。
  - ⚠ **設定は `settingsFlow` ではなく DataStore から読み直す** (`settings.flow.first()`)。アプリの起動直後は
    DataStore の初回 emit が間に合わず `settingsFlow` がまだ既定値 (OFF) のことがあり、そのままだと
    **いちばん録りたい 1 本目のタブだけ録れない**という形で外れる。読み直した snapshot は
    `startLogging(snapshot)` へそのまま渡す — 保存先やファイル名も同じ理由で既定に落ちるので、
    フラグだけ読み直しても「1 本目だけ既定の場所に出る」形で残る。
  - 記録の ON/OFF 自体はタブごとの状態で永続化しない (アプリ再起動で必ず OFF) が、**この設定は永続化する** —
    自動開始は「毎回そうしたい」という意思であって、そのタブだけの一時的な状態ではない。
- **伏せ字** (`core/SecretMasker`、`sessionLogMaskSecrets`、**既定 ON**、0.8.243): 書く直前に鍵・トークンらしき
  部分を `[z2term:masked]` に置き換える。
  - **打ったパスワードは対象ではない**。`sudo` 等は端末が echo を止めるので、そもそも画面にも PTY 出力にも
    現れず、ログにも入らない。実際に漏れるのは「**画面に出た**秘密」= `TOKEN=…` の `名前=値` と、
    `cat` した / 貼り付けた PEM ブロックの 2 つが圧倒的に多く、そこを高い精度で潰す。
  - ⚠ **誤爆しないことを精度より優先する**。6 桁の数字を確認コードとみなす / 長い base64 を全部潰す、といった
    規則は `ls` の出力やビルドログを穴だらけにして、最後は「読めないから機能ごと切る」に行き着く。
    同じ理由で **潰すのは値 1 つ分だけ** (行末まで潰すと `TOKEN=x && echo done` の後半まで消える)、
    **`pass` 単体は対象にしない** (`Passed 12 tests` に当たる)、**`-p値` の短いフラグは扱わない**
    (`tar -pxvf` と区別が付かない)。判定は**値の見た目ではなく名前**で行う。
  - **完成した行の単位**で当てる。行の途中で切ると秘密の後半が素通りするため、ON のときは改行が来るまで
    最後の 1 行を保持する (ファイルは後から読むものなので実害は無く、`close` で必ず吐き出す)。
  - **生ログ (raw) にも効かせる**。外に出す機会がいちばん多いのが不具合報告用のログなので、そこに穴を空けない。
    バイト列の往復は **ISO-8859-1** で行い、伏せ字に当たらなかった部分のバイトが 1 ビットも変わらないようにする
    (UTF-8 で読むと不正バイトが `?` に化けて生ログでなくなる)。
  - ⚠ **完全ではない**。独自形式の秘密は素通りする。UI とハンドブックに必ずその旨を書き、
    「伏せ字 ON = 安全」と受け取られないようにする。検証は `SecretMaskerTest` (誤爆しないことの確認が本命)。
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
- **フリック中の見え方はかな面と同じ** (`FlickCommitPopup`・0.8.405): 押下中はキー直上に
  「今このまま離すと確定する 1 文字」を緑地・黒文字で大きく出す。⚠ **それまでは英字面だけ
  「キー上のヒントを 1.6 倍に強調する」作りで、面によって見え方が違っていた** (利用者指摘:
  「統一性がない」)。かな面と**同じ Composable** を使う (`JapaneseFlickKeyboard` の
  `FlickCommitPopup` を `internal` にして共有)。⚠ **片方だけ直さないこと。**
  キー上の常時ヒントは据え置き (かな面は薄白 `hintColor`、英字面は緑 `ZtsGreenBright`)。
  ⚠ ただし**押下中だけ英字面のヒントを黒にする** (0.8.406)。押すとキーの背景が
  `ZtsGreenBright` になるので、緑のままだと**押している間ヒントが背景に溶けて消える**
  (利用者指摘)。かな面が `fg.copy(alpha = 0.6f)` と前景色から作っているのと同じ理屈。
- **長押し連打**: 数字 / 矢印 / space / 英字キー / **⏎** は押しっぱなしで連打 (初回 400ms→55ms)。⌫ は 500ms→60ms、左右フリックで Ctrl+W / Ctrl+U。修飾キーは連打対象外。⏎ は英字レイアウト・かなフリック・システムキーボード時の `SpecialKeyBar` の 3 か所すべてに入れる (0.8.193。1 か所だけだと「キーボードによって効かない」になる)。かなフリックの ⏎ は 1 回目が未確定文字の確定に使われ、以降は改行が連続で送られる。
- **ALT / META**: どちらも同じ「次のキーに ESC を前置する」修飾 (Meta)。⚠ **0.8.281 で META キーは廃止**し、その席を貼り付け / 絵文字パッドの入口 (`PadKey`) にした (下記)。Meta 修飾は Row 5 の ALT に一本化される (元々**同じ働きの重複キー**だった)。`emitChar`/`emitSpecial` に加えて **`emitCursor` にも適用する** — 以前は矢印だけ修飾が捨てられ、ALT+矢印がただの矢印になっていた (0.8.193 で修正)。矢印のバイト列は DECCKM 依存で端末側が組むため、ESC を単独で先に送ってから矢印を送る。
- **面 (`KeyboardFace`) の巡回 (0.8.305)**: 最下段の左端 (従来「あ」が座っていた席) が**面の切替キー**で、押すと次の面へ移る。⚠ **ラベルは「いま居る面」ではなく「押すと行く面」** (`あ` / `ABC` / `12`) — 2 面のときは「もう片方へ」しか無く区別が要らなかったが、3 面あるとラベル以外に行き先が分からない。TopBar「あ」 → OS IME 切替 (別系統)。
  - 面は **かな (`KANA`) / 英字 (`ASCII`) / 数字 (`NUMBER`)** の 3 つ。⛔ **切替キーを新設しない**のが要点で、面が増えても**画面に見えるキーの数は変わらない** (面は差し替えであって追加ではない)。
  - 巡回は「**設定の巡回順 ∩ いま出せる面**」で回す (`KeyboardFace.available`)。かな面はアプリの言語が日本語のときだけ、数字面は設定 ON のときだけ出せる (⚠ **数字面の既定は OFF**・0.8.348)。⚠ **英字面は必ず残す** — 両方外れると面が 1 つも無くなる。
  - ⚠ **いま出ている面が巡回から外れたら先頭へ戻す** (`KeyboardFace.next`)。設定を変えた直後に起こり、そこで詰まると切替キーが効かなくなる。
  - **巡回順は設定で 2 択** (`あ → A → 12` / `あ → 12 → A`)。⚠ **3 面の巡回順は回転を除いて 2 通りしかない**ので、この 2 つで全部を尽くしている (`A → 12 → あ` は 1 つ目を回しただけの同じ巡回)。**並べ替え UI を作っても選べるものは増えない**ため作らない。面が 2 つの状況 (英語 / 数字面 OFF) では巡回順という考えが成り立たないので、設定そのものを出さない。
  - 面を移るとき**打ちかけのかなは先に確定する** (面をまたいで持ち越さない)。
- **切替キーが無い面 (英語ロケール ∧ 数字面 OFF)**: 面が英字だけなので切替キーを出さず、SPACIOUS では ⇧/CTRL を 1 段下げて a 行頭の空きを埋める。COMPACT はホーム行に左キーが無いのでそこは変えず、代わりに **0.8.397 から上部バー右端の CTRL が貼り付け / 絵文字の入口に替わり、CTRL は Row 5 左へ下りる** (下記 `PadKey`)。⚠ 判定は「日本語かどうか」ではなく「**切替キーがこの面に座るか**」。英語でも数字面を ON にすれば切替キーが座るので、配置は日本語と同じに戻る。
- **英字面の貼り付け / 絵文字 (`PadKey`・0.8.281)**: 英字面には絵文字も貼り付けも入口が無く、英語ロケールで常用すると**打てない / 貼れない**キーボードだった (日本語ロケールは「あ」面から入れる)。⚠ **キーを増やす隙間は無い**ので、**元から重複していたキーの席**を使う。⚠ **入口を出すのは切替キーが座らない面だけ** (0.8.305)。切替キーが座るときはその席を切替キーが使うので、入口は**かな面と数字面 (どちらも ESC の上フリック = 貼り付け / 下フリック = 絵文字・0.8.348 で揃えた)** の方にある — どの言語・どの設定でも入口が 1 つは残る:
  - SPACIOUS = Row 3 左の旧 **META** (Row 5 の ALT と同じ修飾だった)
  - COMPACT = **上部バーの右端 (0.8.397)**。⚠ 0.8.396 まではここも Row 5 左 (面の切替キーが座る位置) の旧 **CTRL** だったが、その形は **CTRL が上部バーと Row 5 に 2 つ並び、貼り付けの入口だけが最下段の隅**にあった。上部バーの CTRL と Row 5 の入口を**そのまま交換する** — 上部バーは ESC/TAB/⇧ と並ぶ列で押しやすく、CTRL は Row 5 の 1 つに減る。⚠ 交換であって増減ではないので、**どちらの面でも CTRL の席は必ず 1 つ**残る (切替キーが座る面では上部バーが CTRL のまま・Row 5 左が切替キー)。
  - **タップ = 貼り付け / 上フリック = 貼り付け / 下フリック = 絵文字 (0.8.397)**。⚠ **かな面・数字面の ESC と上下を揃える** — 0.8.396 までは「上フリック = 絵文字」で、**同じ 2 つのパッドなのに面によって上下が逆**だった (ESC は上が貼り付け・下が絵文字)。⚠ 上フリックを絵文字ではなく**貼り付けに割り当てる**のは、タップの行き先と揃えて**上へずれても目的地が変わらない**ようにするため。⚠ キーは上端に 📋・下端に 😀・中央に ↕ を描き、**表示そのものを入口の説明にする** (日本語面の ESC 上フリックが「見えない入口」で使われなかった反省)。中央を 📋 から ↕ に変えたのは、**上下に行き先があること自体を先に見せる**ため (タップの行き先は上端の 📋 が兼ねる)。
  - ⚠ **英字面の ESC にも上下フリックを足す (`SilentEscKey`・0.8.362・要望)**。`PadKey` の席は**切替キーが座らない面にしか空かない**ので、**日本語ロケールの英字面には入口が 1 つも無かった** (かな面へ戻れば開けるが、英字を打っている最中に面を往復することになる)。ESC のフリックなら席を増やさずに済み、かな面・数字面と**同じ指の動き**で通る。⚠ **印もポップアップも出さない (利用者の判断)**。かな面の ESC は上下端にヒントを出すが、あちらは記号を載せたキーが並ぶ面なので馴染む。英字面は素のキーが並ぶ見た目で、ここに印を足すと**面の印象が変わる**。周知は HANDBOOK に寄せ、表示は据え置く。⚠ **英語ロケールでも同じに効かせる** — `PadKey` があるかどうかで ESC の挙動を変えると、**同じ面なのに端末の言語で指の動きが変わる**ことになる。
  - パッド表示中は**面をまるごと差し替え、最下段 1 行だけ機能キー (× ⌫ space ⏎ ← →) を残す**。⚠ 日本語面のように両端の列を残さないのは、10 列あって縁が細く、指の的として小さすぎるため。⌫ を「閉じる」に置き換えないのは日本語面と同じ (貼った直後に消せなくなる)。

#### 6.1.1 数字だけの面 (0.8.305)

- **なぜ要るか**: フリック面には数字が 1 つも無く、英字面の Row 1 (`ESC 1〜0 ⌫`) は横に 10 個並ぶので指が細かい。ポート番号・IP・`chmod 755` のように**数字だけを続けて打つ場面**が端末では多く、そこだけ大きなキーで打てる面が要る。
- ⛔ **英字面の Row 1 を流用しない**。狙いが「大きいキー」なので、かな面と同じ 5 列 × 4 行のマス目に**テンキー 3 列 × 4 段**を置き、1 キーの面積をかなと同じにする。
- 配列 (両端の列は**かな面と役割まで揃える** — 面を移っても指の運びが変わらない):

  ```
  ESC      1  2  3   ⌫
  ◀/▼      4  5  6   ▶/▲
  ␣        7  8  9   -//
  面切替   .  0  :   ⏎
  ```

- ⚠ **既定は OFF** (0.8.348 で反転)。面が 3 つあると切替の巡回が長くなり、数字を続けて打たない人には邪魔にしかならない。要る人が ⚙設定 › キーボード で ON にする。
- **パッドの開き方はかな面と同じ** (0.8.348): **ESC の上フリック = 貼り付け / 下フリック = 絵文字**。
  ⚠ **0.8.347 まで数字面の ESC はタップのみで、貼り付けを開く手が 1 つも無かった**。かな面で覚えた「ESC を上へ」がここでは効かないので、利用者からは**「クリップボードが開かない」**としか見えない。**面ごとに開き方を変えないこと**。
- **`␣` は縁 1 列をまるごと使う** (0.8.349)。⚠ ここを上下に割って 😀 を載せていたが、**一番よく打つ `␣` が半分**になって打ちにくい。**かな面は 0.8.306 に同じ理由で戻していたのに、数字面だけ割ったまま残っていた**。絵文字は ESC の下フリックで開くので、😀 の席を外しても入口は減らない。
- ⛔ **左端の列 (ESC / ◀▼ / `␣` / 面切替) は共有部品を通す** (`JpEdgeArrows` / `JpEdgeSpace` / `JpEdgeSwitch` / `JpEscKey`・0.8.349)。**同じ骨格なのに面ごとに組み直していたのが、上の 2 件が片方だけ直らなかった原因**。席の大きさを 1 か所で決めれば、片方だけズレることが構造的に起きない。⚠ **右端の列は役割が違う** (かな面は「変換」、数字面は記号 2 つ) ので共有しない — **揃っているものだけを揃える**。

- **記号は 4 つだけ** (`.` `:` `-` `/`)。端末で数字と一緒に打つもの (IP・ポート・時刻・パス・オプション) に絞る。⚠ **載せすぎると「記号面」になって狙いがぼける** — 記号を一通り打ちたいときは英字面の `?#` がある。`-` `/` はかな面で「変換」が座る席に 2 段積みで置く (`JpEdgeStack`。数字面に変換は無いので空いている)。
- 数字・記号は**確定と同じ出口** (`ComposingState.commitExternalText`) を通す。⚠ バイト列 (`onBytes`) で送ると、OS の入力メソッドとして使っているときに改行や記号が `performEditorAction` 等へ読み替えられる。打ちかけのかなが残っていれば先に確定されるので、かな面から来た直後でも順序が入れ替わらない。
- **設定** (`keyboardNumberFace`・既定 ON): OFF なら巡回は `あ → A → あ` の従来どおりで、**キーの見た目も切替キーの行き先も 0.8.304 と変わらない**。
- パッド (絵文字 / 貼り付け) の入口 (😀) を持つ。⚠ これは**英語ロケールで本命**になる — 英語では切替キーが英字面の Row 5 左を使うため、そこにあった入口が消えるが、数字面に置いておけばどの言語でも入口が残る。

#### 6.1.0 キーマップのカスタム — レイアウト定義 (0.8.402・段階 1a)

⚠ **この段階ではまだ誰も使っていない。** 描画 (`TerminalKeyboard`) は 5 段べた書きのままで、
画面は 1 ドットも変えていない。**表現できることをテストで固めてから描画を移す**
（描画ごと差し替えると、壊れたときに「モデルが悪いのか描画が悪いのか」を切り分けられない）。

**設計の芯は「特別扱いをなくす」**: いまは ESC / ⌫ / 面切替がそれぞれ専用の Composable で、
隠し機能もその中に直接書いてある。すべてのキーを同じ形で表せば、「ESC の上下フリック」も
「⌫ の左右フリック」も**利用者が後から作れるただの割り当て**になる。

`ui/terminal/keyboard/KeyLayout.kt`。層は 5 つ:

1. **器**: `KeyLayout` → `KeyRow` → `KeySlot` → `SlotContent`（キー 1 つ or 分割）
2. **イベント**: `KeyGesture` = `tap / 上下左右 / 長押し / ダブルタップ`。⭐ **「1 フリック・
   2 フリック・3 フリック」という区分はモデルに無い** — 表のどこを埋めたかでしかなく、
   「2 フリックは左右か上下のみ」等の制約は**エディタ側の入力補助**に降格する
3. **アクション**: `KeyAction` の**列**。`Text` / `Named`（矢印・F1-12。⚠ DECCKM で送るバイト列が
   変わるので**バイト列を焼き込まず ID で持つ**）/ `Chord`（`Ctrl+C` 等 = 補助バー相当）/ `Raw`
   （名前を持たない列の逃げ道）/ `Modifier` / `Layer` / `App` / `Snippet` / `Macro`。
   ⭐ 列なので `Ctrl+A` → `d` のような 2 手を 1 キーに書ける
4. **レイヤー**: `KeyDef.layers`（⇧ / 記号 / 自作の Fn）。⚠ **差分ではなく丸ごと差し替え** —
   差分だと「どこが継承でどこが上書きか」がエディタで見えず、直したつもりが直らない
5. **面**: `KeyLayout` を複数持って巡回する（呼出し側の仕事）

**横幅の再配分** (`KeyRow.weights`): 段の予算 = 枠の数。`KeyWidth.Fixed`（均等 1 枠の何倍か）の
合計を引いた残りを `KeyWidth.Auto` の数で割る。⭐ これで要望どおり「1 つ固定 → 残りが均等に
再配分 → もう 1 つ固定 → また残りだけ再配分（**固定したキーは動かない**）」が成立する。
⚠ 固定が予算を食い尽くしても Auto を 0 にしない (`MIN_WEIGHT`) — 0 にすると**押せないキーが
段に居座る**（消えるわけではないので利用者には壊れて見える）。

**枠の分割** (`SlotContent.Split`): ⭐ **向き自由（縦 / 横）で深さ 2 まで**。利用者の指摘
「**上下左右キーは 1 つのキーの半分**」がこれで表せる（縦 2 分割 → 各段を横 2 分割 = 田の字）。
⛔ 3 段目は許さない (`MAX_SPLIT_DEPTH`) — 指で目的の区画に辿り着けなくなる。⛔ 2 次元グリッド
(rowSpan/colSpan) も採らない: スマホの指で編集できず、段 + 分割なら「段を選ぶ → 区画を選ぶ」の
2 手で必ず届く。

**逃げ場** (`KeyLayout.hasEscapeHatch`): 面切替 / 設定 / キーボードを閉じる のどれかを持たない
レイアウトは `validate()` が弾く。⛔ 無いまま適用すると**設定へ戻る手段がキーボードから消える**
（`KeyboardFace` が「ASCII 面は必ず残す」、ツールバーの ⚙ が「隠せない」としているのと同じ理由）。

回帰は `KeyLayoutTest`（再配分・分割の深さ・逃げ場・アクション列・レイヤー）で固定。

**段階 1b（0.8.403）: いまの英字面をプリセットにした** (`AsciiKeyLayouts.kt`)。
`asciiKeyLayout(compact, hasFaceKey, symbols, fourWayFlick)` がいまの配列を組み立て、
`AsciiKeyLayoutTest` が「**いまの画面と同じか**」を固定する（描画はまだ移していない）。
ラベルとフリックの表 (`AsciiKeys`) は `TerminalKeyboard` と**共有する** — 二重に持つと、
片方だけ直したときにこのテストが「一致」と嘘をつく。

⚠ **実装して分かって、設計を 3 つ直した**:

- ⭐ **記号面 (`?#`) はレイヤーでは表せない。** レイヤーは姿の差し替えなので**枠の数を変えられない**
  が、記号面は Row 4 が **10 個 → 8 個**に減る (`?§°¥€£~…`)。枠が変わるものは**別レイアウト**に
  するしかない。⇧ は枠が変わらないので予定どおりレイヤーで表す。
- ⭐ **0.8.410 まではプリセットの幅をすべて `KeyWidth.Fixed` にしていた。** 段階 1b 時点の
  `ESC 1.4 + 英字 1.0×10 + ⌫ 1.4` を保つためだった。0.8.411 でこの旧サイズを意図して改め、
  入力キーは `Auto`、機能キーだけ指定幅にした（下記）。
- **`validate()` から「逃げ場」チェックを外した。** 端末画面ではツールバーの ⚙ が**キーボードの
  外**にあり、現に「面が英字だけ」のときの既定の配列は面の切替キーを持たない（席は CTRL が
  埋める）。必須にすると**いまの配列自体が弾かれる**。⛔ ただし OS の入力メソッドとして出して
  いるときはツールバーが無いので、エディタで保存するときの警告としては残す (`hasEscapeHatch`)。


**段階 1c（0.8.404）: 描画がレイアウト定義を見るようになった。** `TerminalKeyboard` の 5 段
べた書きを消し、**並び・幅・ラベル・フリック先を `asciiKeyLayout(...)` から引く**形にした。
⚠ **見た目は 1 ドットも変えていない** — キーの描画そのもの（`BasicKey` / `FlickKey` /
`ShiftKey` / `SilentEscKey` / `BackspaceKey` / `PadKey` / `SpaceKey`）は据え置きで、
`LayoutKey` が `KeyDef` を見てどれを使うか振り分ける。⭐ **次の段階でこの振り分けごと無くす**
（部品を 1 つの汎用キーに統合すれば「ESC だけ / ⌫ だけ」という特別扱いが本当に消え、利用者が
同じものを作れるようになる）。

- **アクションの実行** (`runActions`): ⚠ **タップとフリックで経路が違う**のをそのまま保つ —
  タップは ⇧/CTRL/ALT を適用し (`emitChar`)、フリックは文字をそのまま送る (`emitFlick`)。
  `Chord`（`Ctrl+W` / `Ctrl+U`）は `AndroidKeyMapper.controlByteFor` で組む。
- **⇧ はレイヤー**: `shift != OFF ∧ ¬記号面` のとき `KeyDef.onLayer("shift")` の姿を描く。
- **記号面はレイアウトごと差し替え**: `?#` の `KeyAction.Layer("sym")` が `sym` を立て、
  `asciiKeyLayout` が組み直す（枠の数が変わるのでレイヤーでは表せない・段階 1b 参照）。
- **文字サイズは役割で持つ** (`KeyFontRole`): SMALL（`keyFontSp-3`。ESC/TAB/CTRL/ALT/`?#`）/
  NORMAL（`keyFontSp`。⏎・矢印・面切替）/ MAIN（`mainKeyFontSp`。英字・数字）。実寸は
  `KeyboardStyle` が持つので、キーボードの大きさ設定で全部が一緒に伸び縮みする。
- ⚠ **数字と英字の振り分けに `KeyDef.repeatable` を使っている**のは**いまの部品の都合**。
  数字は連打を `BasicKey` に任せ、英字は `FlickKey` が自前で連打する。⚠ 記号面は
  フリックが無いが、それでも `FlickKey` で描く — `BasicKey` とは中央テキストの行間と 1dp の
  余白が違い、寄せると**記号面だけ字がずれる**。統合の段階でこの分岐ごと消える。
- ⚠ **枠の分割はまだ描けない**（いまの配列は使っていない）。縦割りの中では `RowScope` が
  使えず、いまの部品（`RowScope` 拡張）をそのまま置けないため。統合の段階で対応する。

**段階 1d（0.8.407）: キー部品を 1 つに統合し、枠の分割を描けるようにした。** それまで
ESC / ⌫ / ⇧ / 貼り付け / スペース / 文字キーは**それぞれ専用の Composable** で、隠し機能も
その中に直接書いてあった。⭐ **`KeyCell` 1 つに統合したことで、利用者が同じものを `KeyDef`
だけで作れる**ようになった（= カスタム配列の土台が揃った）。⭐ **`LayoutSlot` が分割を再帰で
描く**ので、「上下左右キーは 1 つのキーの半分」（利用者）が実際に置けるようになった。

見た目と手触りの違いは、すべて `KeyDef` のフィールドで表す:

- `hintGestures` … **方向ごと**にヒントを出すか（英字は上・左右、貼り付けは上下、ESC・⌫ は無し）
- `flickOnRelease` … ⚠ **文字キーは指を離したとき**に確定（途中で方向を変えられる・ポップアップが出る）、
  **ESC・⌫・貼り付けはしきい値を超えた瞬間**に発火（行き先を出さない隠し操作なので迷う余地が無い）
- `pressFeedback` … 押している間に背景を明るくするか（`space` は変えない）
- `highlighted` … 押していなくても目立たせる（パッドの `×`）
- `labelTone` / `fontRole` / `repeatable` / `repeatInitialMs` / `repeatIntervalMs`
  （⚠ ⌫ だけ連打の出だしが 500ms と遅い。速いと消しすぎる）

⚠ **パッドを開いている間の下段も同じ仕組みで描く**（`asciiPadRow`）。⚠ ⇧ は押下で背景を
変えない代わりに 3 状態（OFF / 1 回だけ / 固定）を色で見せる — CTRL・ALT が `active` で緑に
なるのと同じ筋で、`ShiftState` が 3 値なぶん色が 1 つ多い。

⚠ **実機での見た目確認はまだ**（この版は未投入）。

**段階 2（0.8.408）: 配列を保存して選べるようにした。** プリセットを複製して名前を付け、
面ごとに切り替えられる。⚠ **キーの中身を編集する画面（エディタ）はまだ無い**（次の段階）。

- **保存先は設定の DataStore の文字列キー 2 本** — `keyboard_layouts`（配列の束・JSON）と
  `keyboard_layout_active_id`（いま使っている id・空 = 既定）。⭐ **`PrefsPortable` が
  DataStore をキーごと丸ごと運ぶので、設定の持ち出し / 取り込みに自動で乗る**（項目を足す
  たびにバックアップ側へ書き写す方式ではない）。
- **JSON の読み書きは 2 段**。⚠ **JVM のユニットテストでは `org.json` が動かない**
  （`android.jar` はスタブで、`unitTests.isReturnDefaultValues = true` により `put()` は黙って
  何もしない）ため、そこにコーデックを直接書くと**往復テストが書けない**。そこで
  `KeyLayoutCodec`（`KeyLayout` ⇄ `Map` / `List` だけの木・Android に触らない）と
  `KeyLayoutJson`（木 ⇄ JSON 文字列・`org.json` に触るのはここだけ）に分けた。
- **書き方は「既定値を書かない」**。レイアウトは「AI に書いてもらった JSON を貼る」を正規の
  入口にするので（§ カスタムの設計）、人が読める短さが機能のうち。ふつうのキーは
  `{"label":"a","bind":{"tap":[{"t":"text","s":"a"}]}}` だけになる。
- ⚠ **知らない値は 1 つだけ落として読み進む**。未知のジェスチャ名・アクション種別・列挙の id が
  来ても残りは読む。新しい版で書いた JSON を古い版が開いたとき、**レイアウトごと消える**のが
  一番困る。⚠ 生バイト（`Raw`）は 16 進で運ぶ — JSON の文字列に生の制御文字を入れると、
  エディタやクリップボードを 1 度通っただけで壊れる。
- ⭐ **複製では `Fixed(1.0)` を `Auto` へ読み替える**（`asTemplate`）。元は段階 1b の全幅固定
  プリセットを編集用に直す処理だった。0.8.411 から入力キーは既に `Auto` だが、固定 1.0 の
  面切替キーや旧版で保存した配列のために変換を残す。1.4（ESC/⌫/CTRL）、0.8（`?#`・ALT）、
  2.0（space）のような意図した指定幅はそのまま残す。
- **効くのは英字面の素の姿だけ**。⚠ **記号面（`?#`）はプリセットのまま** — 枠の数が
  10 → 8 と変わるのでレイヤーでは表せず、別の 1 枚が要る（段階 1b）。自分の記号面を持てる
  ようにするのはエディタと一緒。⚠ 端末画面と OS の入力メソッドの**両方に同じ配列を渡す**
  （片方だけ既定に戻ると「アプリの中と外で別のキーボード」になる）。
- ⚠ **読めなければ黙って既定へ戻す**（`activeKeyLayout` が null を返す）。束に無い id が
  選ばれている状況は、別の端末で作った設定を戻したときに普通に起きる。ここで例外にしたり
  空のレイアウトを返したりすると、**キーボードが 1 枚も出ない端末**ができる。
- **キーボードスタイル（シンプル / 4 方向フリック）は今までどおり**。⚠ キー高さ・文字の
  大きさ・段間は `KeyboardStyle` が持ち続け、レイアウトは**並び・幅・割り当てだけ**を持つ。
  二重に持たせると、キーボードの大きさ設定が配列ごとに効いたり効かなかったりする。
- ⚠ **段の数が違う配列は 1 段の高さを割り直す**。キーボードの席は `style.naturalHeight` で
  固定してあり、その高さは「シンプル = 6 段 / 4 方向フリック = 5 段」を前提に `keyHeight` から
  作られている。シンプルのときに複製した 6 段の配列を 4 方向フリックで使うと、そのままでは
  **席からはみ出して端末の画面にかぶる**。段の数が一致するプリセットでは何も変わらない。
- ⚠ **複製した時点の「面の切替キーの有無」が固まる**（プリセットは面の数から毎回決めている）。
  表示言語が英語 ∧ 数字面 OFF のときに複製すると切替キーの無い配列になり、あとで数字面を
  ON にしても数字面へ行けない。端末画面ではツールバーの ⚙ から既定へ戻せる。エディタで
  切替キーを置けるようにするのが本筋なので、段階 3 の保存時警告（`hasEscapeHatch`）で拾う。

**段階 3（0.8.409）: 1 枚の中身を JSON で編集できるようにした。** 保存済みの配列に
「中身を編集（JSON）」を出し、`KeyLayoutJson.toPrettyJsonString` で**同じコーデックの木**を
字下げして開く。段・枠・幅・縦横の分割・7 種のジェスチャ・アクション列・レイヤーまで、
モデルが持つ全項目を欠落なく編集できる。⭐ JSON は補助的な書き出しではなく、**AI に雛形を
書いてもらって貼る正規の入口**でもある。項目を絞ったフォームを先に作ると、そのフォームが
知らない新しいアクションを「開いて保存しただけ」で落とすため、全量を往復できる入口を先にした。

- **入力中は実際の配列を変えない。** 読めない途中状態を DataStore へ流すと、編集に使っている
  キーボード自身が消える。`fromJsonString` と `KeyLayout.validate()` の両方を通ったときだけ保存可。
- **id は編集対象のものへ固定する。** JSON の `id` を変えても元の id で保存する。id は名前では
  なく選択状態から参照される identity なので、変えると保存した瞬間に「束に無い id」になり
  既定へ戻ってしまう。名前と中身は JSON の値を採る。
- ⚠ **逃げ場が無ければ拒否ではなく警告する。** `hasEscapeHatch()` が false のときは保存前に、
  OS の入力方法には端末ツールバーが無く抜けられない可能性を明示する。端末だけで使う意図も
  あるため保存自体は選べる（段階 1b で `validate()` から外した判断を守る）。
- **閉じる前に未保存変更を確認する。** 下方向ドラッグも含め、壊れた JSON や作りかけを黙って
  捨てない。保存値は従来どおり短い JSON のまま、字下げは画面に出す 1 枚だけへ限定する。

**段階 4（0.8.410）: 配列を見た形のまま編集できる GUI を追加した。** 「中身を編集」はまず
配列のプレビューを出し、キーをタップするとその 1 つのラベル・外枠幅・見た目・ジェスチャの
アクション列を編集する。分割内の区画も再帰で個別に選べる。段 / 外枠の追加・削除・左右移動、
縦横分割（深さ 2 まで）と選択区画だけを残す分割解除もここから行える。

- ⭐ **GUI と JSON は 1 つの下書き**。別々の状態を同期するのではなく、GUI の 1 操作ごとに
  `KeyLayoutJson.toPrettyJsonString` で同じ `source` へ戻す。JSON へ切り替えるとその結果が見え、
  JSON から GUI へ戻すと parse 済みの同じ 1 枚が出る。「GUI では直したのに JSON 保存で元へ
  戻った」という二重正本を作らない。
- **場所は `KeyCellPath(row, slot, parts)` で指す。** `parts` が分割を辿る番号列なので、田の字の
  右下まで曖昧さなく届く。置換・幅変更・追加・削除・移動・分割は Android に触らない
  `KeyLayoutEditing.kt` に置き、無効な path は no-op、最後の 1 段 / 1 枠は消さない。回帰は
  `KeyLayoutEditingTest`（入れ子の 1 キーだけを変更、深さ 3 拒否、2 分割の片側削除で畳む等）。
- **7 ジェスチャすべてがアクション列を持つ。** 各列は追加・削除・上下移動でき、`Text` /
  `Named` / `Chord` / `Raw` / `Modifier` / `Layer` / `App` / `Snippet` / `Macro` の全種を画面で作れる。
  `Chord` は修飾を最低 1 つ残し、送り先を text / named から選ぶ。`Raw` は偶数桁の 16 進だけを
  反映し、入力途中の奇数桁で既存バイトを壊さない。
- ⚠ **GUI が触らない値は必ず保持する。** ラベルを直すときも `KeyDef.copy` で、repeat timing や
  layers を落とさない。レイヤーそのものの編集は JSON に残す（存在時は名前と案内を表示）。
- **構造削除はまだ本体へ反映されない下書き上の操作**で、最後に Save が必要。誤って押しても
  Cancel で全部戻せるため、各キー削除ごとの確認ダイアログは重ねない。シートを閉じるときの
  未保存変更確認は段階 3 と共通。

**既定の英字 / 記号サイズ（0.8.411）。** 既定の4方向英字面を指定比率へ変更した。ESC / ⌫ /
CTRL = 1.4、TAB / Enter = 1.2、Shift = 1.3、面切替 = 1.0、`?#` / ALT = 0.8、space = 2.0。
数字・英字・句読点・矢印は `Auto` とし、各段で機能キーを除いた残りを均等に分ける。記号面は
4段目が8キーなので別の1枚のままだが、同じ行・役割別の幅規則で組み立てる。`?#` を押しても
修飾キーやspaceだけ旧サイズへ戻らない。ラベル・フリック先・動作は変更していない。

**固定画面・自由な面構成の配列エディタ（0.8.413）。** エディタは別ダイアログでなく既存の
設定ウィンドウ内の固定ページとし、バー全体の戻る操作とシステムバー余白を共有する。保存と
キャンセルはスクロール外へ固定する。保存配列は対象種類 `face` を持ち、省略した旧JSONは
`ascii` として互換を保つ。内蔵の英字・日本語・数字と各自作配列は独立した面として個別に
ON/OFFでき、`next_face` の順番も自由に入れ替えられる。選択は単体を既定とし、明示トグルを
ONにした間だけ複数選択を蓄積する。幅の0.2〜5.0スライダーは設定画面と同じ意匠・0.1刻みとし、
数値下書きは選択キーに紐づけるため入力途中の `1.` を正規化で消さない。フリック確定補助は
別Popupウィンドウでなく同じComposeツリーのキー外へ描き、重なった周辺キーのタッチを遮らない。

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

⚠ **同梱している変換データ 3 種はいずれも他者の著作物の派生物**で、それぞれ告知義務がある (0.8.473 で表示を補完)。出所・著作権表示・ライセンス全文の場所は [`assets/KKC-DICT-NOTICE.txt`](../../app/src/main/assets/KKC-DICT-NOTICE.txt) に一本化し、`legal/OssComponents.kt` から設定 →「OSS ライセンス」にも出す。

| データ | 原本 | ライセンス |
|---|---|---|
| `z2dict.txt` | SKK-JISYO.L の送り仮名なし部 (見出し 159,794 件は原本と同一。注釈を除き UTF-8 化) | GPL-2.0-or-later |
| `kkc_lex.tsv` / `kkc_matrix.bin` | mecab-ipadic 2.7.0-20070801 | NAIST ライセンス |
| `kkc_colloc.bloom` | 日本語版ウィキペディアの共起 2-gram を Bloom フィルタ化 | CC BY-SA 4.0 |

`z2dict.txt` の著作権表示は**ファイル先頭にそのまま残してある** (ローダーは `;` 始まりの行を読み飛ばす)。⛔ **辞書を差し替えるときは表示も必ず一緒に直すこと** — コードと違ってデータは「借り物である」ことが見えにくく、告知が抜けたまま頒布すると条件違反になる。

- **候補生成 (`convertFlexible`)**: 学習履歴(完全一致) → 学習履歴(前方一致＝予測変換) → 文まるごと最尤変換(`nbest`) → **読み完全一致の 1 語候補(`KkcConverter.wordsFor`)** → 完全一致(`convert`)/送り仮名活用(`okuriForms`) → 前方一致予測(`predict`)。生かな・カタカナは常に確定候補として残す。
- **常用語の活用補完** (`SUPPLEMENT_WORDS`): 元辞書は動詞・形容詞の終止形も活用形もほぼ持たない (`あく /悪/灰汁/…/` のように名詞しか無い) ので、**変換できる活用形は内蔵の常用語テーブルに載っている語だけ**になる。⚠ **表から 1 語落ちると、その動詞の活用形が丸ごと変換不能になる**。0.8.360 で「開く」を追加 — 「開ける/閉める/閉まる/閉じる」は揃っているのに**自動詞の「開く」だけが無く、あかない → 開かない が一度も候補に出なかった** (利用者の指摘)。読みは `あく` / `ひらく` の両方を入れる (元辞書の `ひらく` は `啓` しか持たない)。⚠ 対になる自動詞/他動詞は**片側だけ足さない**。回帰は `VerbConjugationCandidateTest` で固定。⚠ 補完候補は `mergeDict` で**元辞書より前に置く**ので、追加した語はその読みの第一候補になる (`あき` は `秋` より `開き` が先に出る。`つき` → `付き` が `月` より先なのと同じ既存の性質)。
- **候補の枠** (`DEFAULT_CANDIDATE_LIMIT`=48・0.8.298): 候補は 1 つの枠を学習履歴 (完全一致 4) ・予測変換 (前方一致 6)・文まるごと変換 (6)・同梱辞書が**先着で取り合う**。⚠ 枠が小さいと、使い込んで学習が育つほど下位の源が押し出され、**辞書が持っているのに変換できない語**ができる (16 枠だった頃は「とく」で 説く/解く/溶く が最後まで出なかった。履歴が空だと出るため気付きにくい)。候補バーは横スクロールするので枠を広げて失うものは無く、48 まで広げた。⚠ **前方一致の補完だけは別枠** (`PREFIX_PREDICT_LIMIT`=12) — 補完 (仕事 → 仕事相手/仕事唄…) は際限なく作れるので、広げたぶんが全部補完で埋まると「打った読みそのものの変換」が埋もれる。
- **読み完全一致の 1 語候補** (`KkcConverter.wordsFor`・0.8.297、枠の外へ 0.8.298): 読み全体に一致する語彙エントリを**単語コスト昇順**で候補へ足す。⚠ `nbest` は「文としての最尤経路」を上から 6 本しか採らないため、同じ読みの 1 語候補が順位争いに埋もれて**候補一覧に一度も現れない**ことがあった (とく → 説く/解く/溶く が出ず、代わりに z2dict の稀な単漢字 慝/悳/涜/犢… が 16 枠を埋めていた。きく → 聞く/効く/聴く、みる → 診る/観る も同様)。辞書に持っている語が順位の都合で選べないのは変換の穴なので、完全一致の 1 語はここから直接引く。読みが文 (辞書に無い長さ) のときは空を返すので文変換の挙動は変わらない。⚠ **この段だけ `limit` を取り合わせない** (0.8.298): 枠内で先着にすると学習が育った端末で押し出され、実際 0.8.297 でも実機では 説く が出なかった。文まるごと変換 (3) の直後へ差し込み、重複は合成時に落とす。回帰は `ExactWordCandidateTest` (履歴が空の場合と、枠を上限まで食う履歴がある場合の両方) で固定。
- **記号当て字の抑制** (`SYMBOL_READING_PENALTY`): IPADIC は 1 文字ひらがな読みに記号表層を低コストで持つため (と→＆ 3177・に→２・ご→５ 等) 素のかな/漢字より上位に出てしまう。読みが 1 文字ひらがな ∧ 表層が記号のみ (仮名・漢字を含まない) のエントリへ `loadFromStreams` で大きめのペナルティを足し最下位へ落とす (候補一覧には残る)。既存の `KATAKANA_DUP_PENALTY`(過剰カタカナ化抑止) / `KANA_PREFERRED`(補助動詞・形式名詞の漢字化抑止) と同じ流儀。
- **学習履歴** (`ImeHistoryStore`): 確定語を頻度・直近 7 日でランキングし上位に出す。
  - ⚠ **表は不変オブジェクトで持ち、更新は差し替え (copy-on-write)** (0.8.296)。記録 (`record` / `recordBigram`) は IO スレッドのコルーチン、参照 (`historyFor` / `predictHistoryWithReading` / `bigramBonus` / `learnedBlock`) は**変換のたびに UI スレッド**から走る。可変 `HashMap` を共有していた頃は、記録が新しい読みを put した瞬間に UI 側の走査が `ConcurrentModificationException` を投げて**アプリごと落ちた** — 実際の踏み方は「候補をタップ → `commit` が `record` → 直後に残りかなの候補を作る `refreshPredict` が履歴を走査」。⚠ ◀ でカーソルを動かして**残りかな (tail) を作ったときだけ**確定後に `refreshPredict` が走るため、「打つ → ◀ → 候補タップ」でだけ落ちるように見えた (入力メソッド専用の不具合ではなく、端末画面の内蔵キーボードでも同じ経路を通る)。読み手が触るのは常に完成した表なので、走査中に中身が変わることが構造的に起こらない。⚠ **例外を握りつぶす形 (try/catch) で塞がない** — 壊れた読み出しの可能性が残る。回帰は `ImeHistoryConcurrencyTest` (記録しながら参照し続けて例外 0) で固定。
- **ユーザー辞書** (`UserDictStore`・0.8.280): 利用者が持ち込んだ **SKK 形式のテキスト** (`よみ /候補1/候補2/`) を候補に混ぜる。同梱辞書は一般語しか持たないので、人名・社名・自分だけの略語は**学習が育つまで出てこない**。ファイルで渡せば最初から出せる。
  - **置き場は `filesDir/user_dict/<名前>`**。設定画面で選んだファイルを**コピーして持つ** — SAF の権限はプロセスを跨いで保証されず、元ファイルを消されたら辞書ごと消えるため。⚠ **ファイル単位**で足す/消すので、どの語がどこから来たのか利用者が追える。
  - **取り込み時の検証**: 1 語も取れなければ**保存しない** (形式違いのファイルが一覧に並ばない)。8MB 超は断る。全体の語数上限は 30 万語。結果は成功/大きすぎ/語なし/失敗を Toast で必ず返す — 黙って無視すると「入れたのに変換が変わらない」原因が追えない。
  - **書き方は 2 つ受ける** (0.8.282): **SKK 形式** (`よみ /候補1/候補2/`) と、**表形式** (`よみ→表記→品詞→注釈`。タブ / 全角スペース / 2 個以上の半角スペース区切り) — かな漢字変換の辞書ツールが書き出す形。⚠ 0.8.280 は SKK 形式しか見ておらず、**表形式の辞書を 1 語も読めなかった**。見分けは **2 列目が `/` で始まるか**で行い、タブ区切りで書かれた SKK 形式も取りこぼさない。⚠ **3 列目 (品詞) 以降は使わない** — 活かすには IPADIC の文脈 ID へ対応付ける必要があり、雑に混ぜると接続コストが壊れる。1 個の半角スペースは区切りにしない (SKK 形式と見分けが付かなくなる)。
  - ⚠ **読みがひらがなの行だけ**採る。SKK の送り仮名あり見出し (`おくr /送/`) は活用処理が要るので捨てる (採ると「おくr」という読みが候補に出る)。候補の `;注釈` は落とす。
  - 行解析は `UserDictParseTest` (11 ケース) で固定する。⚠ 利用者が持ち込むファイルは**書き方が 1 つではない**ので、対応形式を増やしたらここにケースを足す。
  - ⚠ **文字コードは UTF-8 / EUC-JP 両対応**。UTF-8 で**厳密デコード** (`CodingErrorAction.REPORT`) し、壊れていれば EUC-JP で読み直す。置換文字を許すデコードだと、配布 SKK 辞書に多い EUC-JP が「文字化けした語の山」として静かに入る。
  - **候補への効かせ方は 3 経路**: ① 完全一致 (`lookup`) を `convertFlexible` の学習履歴の次に置く (自分で登録した語なので同梱辞書・Viterbi より前。ただし「実際に選んだ実績」である学習履歴には勝たせない) / ② 前方一致 (`predictWithReading`) を予測変換段へ / ③ `KkcConverter.userDictBlock` へ配線してラティスに合成ノードを足す。⚠ ③ が無いと「単語では出るのに文の中では出ない」辞書になる。コスト下げ幅 `BLOCK_BONUS=4000` はカタカナ化ペナルティ (4000) を越える値 — 越えないと登録表層が「カタカナのまま」に負ける。
  - ⚠ **学習ブロック (`learnedBlock`) を先に見る**。同じ読みに両方あるなら、選ばれた実績のある表層を優先する。これが無いと辞書に 1 行足しただけで打ち慣れた変換が上書きされる。
- **予測変換 (学習履歴の前方一致)**: 打った読みで始まる学習済みの語句を、文まるごと変換より先に候補上位へ出す (`ImeHistoryStore.predictHistoryWithReading` / `convertFlexible` の前方一致段)。打ちかけの読みで「打ち慣れた語句」を絞り込んで提示する本来の予測変換。**予測候補を確定したときは、打った接頭辞ではなく語句の実際の読みで学習する**: `ComposingState.commit` が `KkcConverter.predictionReadingMap` で表層→実際の読みを逆引きし、`ImeHistoryStore.record` の見出しに使う。接頭辞だけの不正な学習見出しが履歴に入らず、次回以降も同じ読みで予測が再利用される。
- **文節分割合成 (`segment`)**: 内容語(最長辞書一致) + 後続の助詞/送り仮名を 1 文節として連結 (例: きょうの → 今日の)。**助詞** (の/は/が…) と**文末助動詞** (でしょう/ました/です…) は単漢字エントリ (野/葉/増田…) を持つため**かなのまま残す** (`PARTICLES` / `AUX_KANA`)。辞書ヒット 1 文節以上 ∧ 漢字を含むときに返す。
- **先頭ブロックとカーソル** (`cursor`) (0.8.157 で刷新): composing 中は `cursor` (0..length) が挿入カーソルであり、同時に「先頭ブロックの境目」でもある。候補は先頭ブロック (`text[0..cursor]` = `splitHead`) を `convertFlexible` で変換したもの。◀▶ (`moveCursorLeft`/`moveCursorRight`) でカーソルを動かすと先頭ブロックが伸縮し候補が追従する (行頭 0 まで到達可)。打鍵直後はカーソルが末尾にあり (= 打った生かな全体が先頭ブロック)、◀ で縮めて部分変換する。候補タップ/⏎ (`commitRaw`) で先頭ブロックを確定 → 残り (`splitTail`) を composing に据えて末尾カーソルで続行、残り 0 で抜ける。変換キー (`convert`) は先頭ブロックの候補をサイクルする。
- **文節境界** (`KkcConverter.bunsetsu`): 一括予測の内訳 (`fullPredictionBlocks`) や結合ブロック学習で文を文節に割るときは、**正確なラティス最短経路 (`nbest` 1 位) の分割**を使う (0.8.29)。位置 DP の `segments` は単一右文脈しか持たず接続コスト次第で誤分割していたため。
- **動的ブロック分割 (学習)** (0.8.71): ブロック境界を辞書コストだけで固定せず、ユーザーが確定した読みブロックの頻度で学習する。確定済み `(読み→表層)` を `ImeHistoryStore.learnedBlock(読み)` が `(最頻表層, コスト下げ幅)` で返し (`KkcConverter.learnedBlock` に配線)、`nbest` のラティス構築で 2 文字以上の読みが学習ブロックに一致したらノードコストを下げる (`BLOCK_BASE_BONUS=3000` + `count` 比例 `BLOCK_COUNT_STEP=1500`(上限 count4) + 直近 `BLOCK_RECENT_BONUS=1000`)。これでカタカナ化ペナルティ+接続コストを 1〜2 回の確定で上回り、誤分割していた頻用読みが次回以降 1 ブロックへ自動でまとまる。辞書に無い読みでも学習表層で合成ノード (`lc=rc=0`) を足す (未学習読みは挙動不変)。**コスト割引はユーザーが実際に確定した表層だけに効かせる** (0.8.74)。同読みの全表層へ一律に掛けると辞書最小コストの別表層が勝ち、選んだ漢字が反映されなかったため。**結合読みを 1 語コスト基準にし、連続確定 run を結合ブロックとして学習する** (0.8.85): 学習ブロックの合成ノードコストを長さ比例の未知かなではなく 1 語分の `UNK_COST` 基準にし、`ComposingState` が同一スプリット run の連続確定を `committedRun` に貯めて run 終了時 (`learnMergedRun`) と一括確定時に結合読み→結合表層を記録する (読み長 2〜`MERGE_MAX_READING_LEN`=6 に限定)。
  - ⚠ **合成ノードのコスト基準を `UNK_COST` から切り離した (0.8.493)。** それまでは `UNK_COST(17000) − ボーナス` で組んでいたため、ボーナスが上限 (8500) でもノードコストが 8500 残り、**辞書がその区間を安く分割できる読み (実測: 経路の総コスト 6000 台) には何度確定しても永久に勝てなかった**。学習はしているのに区切りが変わらず、同じ言い回しを毎回手で区切り直す必要があったのはこれが原因 (利用者報告)。学習ブロックは「利用者がその区切りでその表層を選んだ」実績そのものなので、未知語の推定コストではなく**辞書の一般語と同程度**の `LEARNED_BLOCK_COST=9000` を基準にし、そこからボーナスを引く (count=1 で 6000〜5000、count=2 で 4500)。
  - ⚠ **下限 `LEARNED_BLOCK_MIN_COST=4000` を置く。これ以上安くしてはいけない。** 合成ノードは `lc=rc=0` の BOS/EOS 文脈近似で**接続の妥当性を評価できず、どこへでも安く繋がる**。下げすぎると、学習した塊がその読みを部分文字列として含む**別の語の途中**にまで割り込み、辞書が 1 語で正しく変換していた読みを軒並み壊す (実測: 4 文字の塊を 1 つ学習しただけで、その塊を先頭に含む別語が崩れた)。実測した安全域は**実効コスト 4000〜6000** — 4000 未満は割り込み、7000 以上は学習が効かない。使い込んでも 4000 で頭打ちにし、**誤変換を増やさない範囲で最強**に張り付かせる。回帰は `BlockLearningTest` が「1 回の確定で塊がまとまること」と「別語の途中へ割り込まないこと」の両方で固定している。
  - ⚠ **「学習の区切りで全文を組み立てて候補に足す」経路は採らなかった (0.8.493 で試して撤去)。** 学習済みの塊を貪欲に最長一致で切り出すと文脈を見ないため、残った区間が意味を成さない読みでも無理に変換し、**使いようのない候補を 1 位に押し上げる**。区切りの学習はラティス (コスト) 側だけで完結させ、接続コストによる妥当性判断を必ず通す。
- **頻度優先 — 使う語ほど文の中でも前に出す** (`ImeHistoryStore.unigramBonus` / `KkcConverter.unigramBonus`・0.8.398): 確定した `(読み → 表層)` の頻度・直近性を **`nbest` のラティスのノード 1 つずつ**に効かせる。⚠ **これが無いと、語 1 つをどれだけ使い込んでも文の中では順位が 1 ミリも変わらない** — 学習履歴 (`historyFor`) は**読みの完全一致**でしか引かないので長文では一度も引かれず、学習ブロック (`learnedBlock`) は塊の繋ぎ止めが目的で**2 文字以上の読み限定**だった。実際「きょうはあめがふるひだ」は「きょう→今日」を何度確定しても **1 位が「教は雨が降る日だ」のまま**だった (利用者指摘: 「長文変換でも何度も使う漢字が前に来ない」)。下げ幅は `UNIGRAM_BASE_BONUS=1200` + `count` 比例 `UNIGRAM_COUNT_STEP=800` (上限 count 8) + 直近 `UNIGRAM_RECENT_BONUS=500`。**同梱辞書が持つ「壁」を頻度で順に越える**段階になっている (実測は `UnigramLearningTest`): count=1 で 教→**今日**、count=3 で 噺→**話** / とき→**時**、count=8 で **平仮名優先ペナルティ (`KANA_PREFERRED_PENALTY`=4000) を越えて** もの→**物**。⚠ 平仮名で書くのが普通な語の既定を **1 回の確定では壊さない**のが狙い。⚠ 学習ブロック (最大 8500) は越えさせない — 語 1 つの頻度が塊の繋ぎ止めより強くなると、覚えたはずの分割が崩れる。⚠ **学習ブロックが効くノードには足さない** (二重掛けで効きすぎ、1 回の確定が文中の別の場所まで塗り替える)。
- **1 文字の確定も学習する (漢字・カタカナのみ)** (`ImeHistoryStore.isLearnableWord`・0.8.398): 従来は `MIN_WORD_LEN=2` で **1 文字を一律に捨てていた**。⭐ 長文で効いてくるのはまさに **1 文字の漢字** (時 / 事 / 物 / 方 …) なので、上の頻度優先を入れても学習側にデータが無ければ効かない。単打のひらがな・記号・英数字は従来どおり覚えない (助詞で履歴が埋まると候補の先頭を塞ぐだけ)。
- **文まるごと一括予測** (`fullPrediction`): カーソルより後ろ (tail) が残るとき (`0 < cursor < length`)、先頭ブロックの最尤候補 + 残りかなの Viterbi 1-best を連結した「文まるごと」候補を候補バーに薄緑ピルで 1 つ出す。タップ (`commitFull`) で全文を一括確定。◀▶ で `cursor` が動くと `refreshPredict` 経由で再構築され境界変更に追従する (0.8.16)。残りかなの Viterbi は先頭表層を文脈に bigram リランクを通す。**一括確定の学習はブロック単位** (0.8.74): `fullPredictionBlocks` (先頭ブロック + `bunsetsu(tail)`) を控え、`commitFull` で各ブロックの `(読み→表層)` と隣接ブロック間 bigram を学習する。内訳が `full` と不整合なときは文全体 1 エントリへフォールバック。
- **候補バー先頭ピルの生かな全体表示** (0.8.157): 先頭ピルは「先頭ブロック (`splitHead`) の緑ピル + 別の tail ラベル」の 2 分割から、**打った生かな全体を 1 つの連続ピル**へまとめた。先頭ブロック (カーソルより前) を濃色・残りかな (`splitTail`) を薄色にし、カーソル位置に caret (地色反転の細バー) を挟んで「今の先頭ブロック範囲＝どこまで打ったか」を示す。タップは `commitRaw` (先頭ブロックを生確定して次へ)。
- **カーソル位置での途中編集** (`cursor`) (0.8.157): 編集位置を独立フィールド `cursor` (0..length) に持ち、**◀▶ = カーソル移動に統一**した。かな/記号は `insertAtCursor` でカーソル位置へ挿入、⌫ (`backspace`) はカーソル直前を削除、`小゛゜` はカーソル直前が対象 (`charBeforeCaret`)。**行頭 (0) まで移動でき、長文/短文を問わず一様に途中修正できる**。以前は編集位置を `splitHeadLen` (最小 1・自動分割由来) に相乗りさせていたため「行頭へ行けない」「自動分割が効く長文でしか編集できない」不具合があった。旧 `autoSplit`／`caretEditMode` フラグと「打鍵で自動的に先頭文節へスプリット」する挙動は廃止し、打鍵直後はカーソル末尾 (先頭ブロック = 全体) に統一した。
- **再変換**: 確定直後 (composing 空) に変換キー=「再変換」で直前確定を読みに戻す (`restoreLastCommit`)。
- **キー背景**: 未確定中は ◀▶・変換キーの背景を緑にせず静かに保つ (緑は「再変換」ヒント時の変換キーのみ)。

#### 6.2.2 絵文字パッド / 貼り付けパッド (`KeyboardPad`・0.8.278)

内蔵キーボードを**アプリの外でも使う**ようになった (§6.9) 途端に空いた穴を塞ぐ。端末の中なら
ツールバーの 📋 やコピペで足りていたが、他アプリで常用キーボードにすると **絵文字が打てない /
貼り付けができない**キーボードになる。⚠ **日本語キーボードにだけ載せる** — 英字面は端末寄りの
性格が強く、そちらのキーを削りたくないため (英語ロケールではかな面自体が出ないので使えない)。

**⚠ 新しいキーを置く隙間が無い**ので、**面の差し替え**にする (`あ` でかな面、`?#` で記号面へ
切り替えるのと同じ形)。中央 3 列 (かな 12 キー) だけがパッドになり、**両端の列 (⌫ ⏎ ␣ ◀▶) は
残る**ので、貼った直後に消す・改行するがそのまま続けられる。⚠ ⌫ を「閉じる」に置き換えない
(パッドを開いている間に文字を消せなくなる)。

- **入口は ESC の上下フリックに揃える** (0.8.306): **上フリック** = 貼り付け、**下フリック** = 絵文字。
  ⚠ 0.8.278〜0.8.305 は絵文字だけ `␣` の列を上下に割った上段 (😀 キー) に置いていたが、
  **一番よく打つ `␣` が縁 1 列の半分**になって打ちにくかった。`␣` を丸ごと戻し、絵文字は
  既に貼り付けが使っていた ESC のフリックの**空いている向き**へ移す — キーの数も `␣` の大きさも
  0.8.277 以前に戻り、入口は 1 か所にまとまる。パッド上部の 😀 / 📋 タブは残すので、
  **どちらから入っても両方へ行ける**。閉じるのは左上の × (入口の ESC はパッド表示中に画面へ
  出ないので、「入った同じキーで出る」トグルはこの面では使えない)。
- **フリックできると分かるようにする** (0.8.279 / 0.8.306): 指の動きは見えないので、
  かなキー (`JpFlickKey`) がフリック先を常時出しているのと同じ作法で
  **ESC キーの上端に 📋・下端に 😀 を薄く出す**。さらに **押しっぱなし 300ms** でキーの真上に
  `JpEscHintPopup` が「▲📋 / ▼😀」を浮かべる。⚠ 1 文字の `FlickCommitPopup` は使えない —
  行き先が 2 つあるので**上下の並びそのもの**で示す必要がある。
  ⚠ タップでは出さない — ESC は端末で最も打つキーの 1 つで、押すたびに何か浮くと邪魔になる。
  フリックが決まった時点でポップアップは消える。
- **絵文字** (`EmojiCatalog`): 8 カテゴリ。**よく打つ字を手で選んだ表 + 連番ブロック**で、全収録は狙わない (数千字を並べても探せない)。
  ⚠ **1 字ずつ書き写す表は必ず抜ける**。実際 U+1F600–U+1F64F の 80 字のうち **13 字** (😌 U+1F60C・😝 U+1F61D・猫の顔 U+1F638–U+1F640 など) が抜けており、**打とうとした本人にしか気付けない**形で落ちていた (利用者の報告)。0.8.301 で顔 (U+1F600–U+1F644)・人のしぐさ (U+1F645–U+1F64F)・手 (U+1F446–U+1F450)・動物 (U+1F400–U+1F43E) を**ブロックごと**足し、`EmojiCatalogTest` が範囲の全字を固定する。⚠ **足す順は「手で選んだ表 → ブロック」**。逆にするとよく打つ字が数十字ぶん後ろへ押し出されて探せなくなる (表を広く持って困るのはそこだけ)。⚠ **異体字セレクタが要る字は範囲に入れない** (U+1F43F 🐿 は U+FE0F 無しだと白黒の記号で出るので U+1F43E で止める)。
  ⚠ **端末フォントが持たない字は出さない** (`Paint.hasGlyph` で 1 度だけふるいに掛ける)。□ (豆腐) を
  打たせないためで、OS が新しくなれば表を触らずに増える。先頭タブは**最近使った順**
  (`RecentEmojiStore` → `filesDir/emoji_recent.json`、48 字) — 実際に使う絵文字は 20 字ほどに偏る。
- **貼り付け** (`clipboard/ClipboardHistoryStore` → `filesDir/clipboard_history.json`、50 件。
  端末のコピーも 📋 履歴シートも**同じ 1 つのストア**を見る。⚠ **パッド用に別ストアを作らない** —
  0.8.313 まで同名の別 object が同じファイルを別スキーマで奪い合い、パッドの履歴が毎回消えていた):
  ⚠ **常時監視はできない** — Android 10 以降、クリップボードを読めるのはフォアグラウンドのアプリか
  現在の入力メソッドだけ。よって取り込みは**パッドを開いたときの 1 回**と、開いている間の変化
  (`OnPrimaryClipChangedListener`)。利用者から見ると
  「**コピーしてからキーボードを開くと履歴に入る**」。この順序は説明が要るので空のパッドに明記する。
  ⚠ パスワードマネージャ等が付ける機微印 (`android.content.extra.IS_SENSITIVE`) のクリップも
  0.8.314 から取り込む。行頭に 🔒 を付け、**30 秒で自動的に消える** (§4.6 `ClipboardHistoryStore`)。
  1 件ずつ ✕ で削除、🗑 で全消去。
- **貼ったらパッドを閉じる** (0.8.395): 行をタップして貼り付けた時点で `PadMode.NONE` へ戻し、キーの面に戻す。⚠ それまでは開いたままで、**貼るたびに左上の × を押さないとキーへ戻れなかった** (利用者の指摘)。⚠ **絵文字パッドは閉じない** — 続けて何字も打つのが普通だから。閉じる / 閉じないの分かれ目は **「続けて打つ操作か」** で決める (入口や見た目の対称性ではない)。⚠ 実装はパッド側 (`ClipboardPane` → `onMode(PadMode.NONE)`) に 1 か所だけ置く — 呼び出し元はかな面 / 英字面 / 数字面の 3 つあり、面ごとに書くとどれかが取り残される。
- **出口** (`ComposingState.commitExternalText`): 絵文字も貼り付けも**確定と同じ経路**を通す。
  ⚠ バイト列 (`onBytes`) で送ると、入力メソッド側で改行が `performEditorAction` (1 行欄では検索実行)
  へ読み替えられてしまう (§6.9 の表)。打ちかけのかなが残っていれば先に確定してから出し、
  読みが無いので学習・再変換の対象にはしない。

### 6.3 接続先 (端末 → 外部)

- 永続化レコード名は移行互換のため `SshProfile` のままだが、各レコードに `protocol = SSH | WEBDAV | SMB` を持つ。方式が無い旧DataStore/バックアップJSONと未知の値はSSHへフォールバックする。`remotePath` はSMB共有名、`domain` は任意の認証ドメインで、3方式ともKeystore暗号化される `password` を共用する。
- `SshProfilesSheet` は共通接続先一覧。SSHではhost/port/user/認証（パスワード or 秘密鍵+パスフレーズ）/initCommand/転送、WebDAVではベースURLとBasic認証、SMBではhost/port/共有名/domain/認証を編集する。端末・VNC・常駐トンネル操作はSSH行だけに出す。
- **ホスト単体の欄は DNS / IPv4 / IPv6 を受け付ける（0.8.457）**。`HostAddress` が接続前に任意の外側の角括弧を外し、authority を作るときだけ `[2001:db8::1]:22` に戻す。SSH/JSch・ポート転送・SMB・WebDAV の Host ヘッダー・VNC 表示・known_hosts のキーで同じ規則を使う（IPv6 のコロンとポート区切りを曖昧にしない）。公開 IPv6 はドットが無いという理由だけで NetGuard の「一語の LAN 名」除外へ入れてはならない。
- ファイル画面との境界は `RemoteFs`（一覧・ストリームupload/download・mkdir・rename・delete）。`RemoteFsFactory` がSFTP・直接WebDAV・直接SMBを選ぶので、WebDAV/SMBはSSHサーバーに依存しない。WebDAVは通常のTLS証明書検証、SMBはSMB2/3だけを許可してSMB1を拒否する。
- SSHシェル接続時は `SessionManager.openNew` + `startSsh(profile)`。host key は `HostKeyVerificationDialog` で確認 (`KnownHosts` に保存)。
- ⚠ **平文 HTTP を禁止しない (0.8.452)**: WebDAV は `http://` の相手が普通にあるうえ、SSH 転送越しでも `http://<host>:<転送ポート>` になる。アプリ全体で cleartext を禁じていた頃は、OkHttp が接続を張る前に例外を投げ、**サーバーへ 1 バイトも出さないまま「一覧取得に失敗」で終わっていた** (相手側のログにも痕跡が残らない)。平文で繋ぐ危険は接続先の編集画面が警告するので、判断は利用者に委ねる。⚠ **アプリ自身が取りに行くものだけは HTTPS を強制する** — 更新確認と APK は `network_security_config.xml` の domain-config、rootfs は `DistroDownloader.requireHttps` が URL の scheme を検証する (ホストを列挙しないので配布元が増えても取りこぼさない)。
- **一覧から消すときだけ確認を挟む (0.8.452)**: 接続先・サービス・ポート転送の ✕ / 削除はいずれも編集ボタンの隣にあり、ミスタップがそのまま鍵やパスワードごとの消失になる。共通の `ConfirmDialog` で 1 度止め、消える対象の名前を出す。⚠ **CLI からの削除には確認を挟まない** — コマンドは打った時点で明示的で、対話の余地がない。常駐サーバー (`ServersBody`) と自動化ルール (`WhenRulesBody`) の ✕ も同じ扱い。
- **緑 (accent) は「そのカードの主アクション」だけに付ける (0.8.452)**: 接続だけが緑で、SFTP と各サービスは枠線のみ。以前は VNC にも緑が付いており、種類による色分けとも選択状態とも読めた。

### 6.3.1 リモートの画面 (VNC / RDP・A1・0.8.418／0.8.459)

- **接続先を 3 か所に登録させない。** `SshProfile` の 1 件から「シェル (接続) / ファイル (SFTP) / **画面 (VNC・RDP)**」で入る。**タブもボタンも増やしていない**（行の中で 2 段に分けただけ）。画面のサービスは FTP / SMB / WebDAV と同じ `RemoteService` に相乗りし、`RemoteServiceProtocol.opensDesktopTab` の印だけで「GUI タブか、ファイル画面か」が決まる（呼び出し側にプロトコル名を並べない）。
- **[VNC] / [RDP] は `RemoteServiceConnector.desktopTarget()` → `SessionManager.openRemoteDesktop()`** → リモートモードの `GuiSession`（→ 4.12）。既定では**同じ SSH を踏み台にした一時ポートフォワード**（`ServiceRoute`）を通り、タブを閉じると転送も消える。「SSH ポートフォワード」を外すと SSH 接続先ホストへ直通する（暗号化が無くなる旨の確認つき）。
- **既定ポートは 5901**（`VncTarget.DEFAULT_PORT`）。画面 `:N` は `5900+N`。Windows / macOS のデスクトップ共有は`:0` = 5900 が多い。
- ⭐ **相手が `127.0.0.1` でしか待っていないときは、アプリに機能を足さずに済む** — 端末タブで`ssh -L 5901:localhost:5901 <host>` を張り、接続先を `127.0.0.1` にすればトンネル越しに映る（**Linux 側で埋まるものはアプリに入れない**方針どおり）。
- **失敗は型で見分けて案内に訳す**（`GuiSession.remoteFailureMessage`）。パスワード未設定 / 違う / 未対応方式 /待ち受けていない / 応答なし をそれぞれ別の文にする。⚠ 例外のメッセージをそのまま出さない —画面の真ん中に出る**唯一の説明**なので、`java.net.ConnectException: …` では何を直せばよいか伝わらない。
- ⚠ **VNC のパスワードは 8 文字までしか効かない**（RFB の仕様。9 文字目以降は捨てられる）。保存時は SSH の秘密と同じく Keystore で暗号化し、設定の持ち出しでは「秘密を含めない」選択で落ちる。

**RDP 固有の要点（0.8.459〜0.8.472）**

- **ネットワークレベル認証 (NLA) だけを話す。** CredSSP + NTLMv2 なのでユーザー名とパスワードが要る（ドメインは任意。SMB と同じ `RemoteService.domain` に入れる）。相手が NLA を選ばなかったときは `RdpNlaUnsupportedException` で**名指しの案内**を出す — 打ち間違いと「相手の設定がそもそも違う」は直し方が別なので、同じ「接続に失敗しました」に混ぜない。
- ⭐ **画面の大きさはこちらが決める。** RFB は**もう立っている画面を後から覗きに行く**のでサーバーの解像度をそのまま受け取るが、RDP は**接続のたびに新しいセッションを作らせる**ので、要求しなければ相手の既定（1024x768 等）になる。`RdpTarget.fitDesktopSize` が端末の画面 px から**長辺を幅にした横長・4 の倍数・640〜4096** を出す（GUI タブは横で使う／半端な端の帯を作らない／相手が拒否する大きさを出さない）。⚠ VNC の `requestResize` を送らない理由（相手の実画面を勝手に変えてしまう）とは**別の話**で、矛盾していない。**0.8.480 からは接続後も追従する**: 端末を回すと Display Control で同じ丸め方の大きさを要求し、相手がセッションを作り直す（→ §4 の「Display Control」）。⚠ 作り直しの間は画面がいったん消えて戻るので、**同じ大きさの再送はしない**。
- **証明書は相手ごとに 1 度だけ確かめて覚える**（`RdpCertificateTrust`・SSH の known_hosts と同じ考え方）。RDP の証明書は自己署名が普通でシステム CA では検証できず、かといって全部受け入れると中間者に気付けない。⇒ **初回だけ SHA-256 指紋を見せて決めてもらい、次からは一致を要求する**（変わっていたらもう一度出す）。⚠⚠ **覚える名前は SSH 転送を通す前の本来の宛先**（`RdpTarget.trustKey`）。転送中の `127.0.0.1:<毎回変わるポート>` で覚えると、**次の接続で必ず「初めての相手」になり、確認が確認でなくなる**。確認のダイアログは SSH のホスト鍵と同じ `HostKeyVerifier` に相乗りし、見出しだけ差し替える（利用者が覚える画面を増やさない）。
- ⚠⚠ **Windows が自動生成する RDP 証明書では TLS 1.3 / ECDHE の握手が通らない（0.8.460・実機で判明）。** その証明書の `keyUsage` は **`keyEncipherment, dataEncipherment` だけで `digitalSignature` が無い**が、TLS 1.3 と ECDHE では**サーバーが証明書の鍵で署名する**。Android の TLS 実装はこれを規格違反として `KEY_USAGE_BIT_INCORRECT` で拒否する。⚠ **PC 側の OpenSSL は同じ矛盾を見逃す**ため、**同じ相手へ PC からは繋がるのに端末からだけ繋がらない**という、一番疑いにくい形で出る（切り分けには「PC で繋がるか」ではなく**証明書の `keyUsage` を見る**）。⇒ **証明書に `digitalSignature` が無いと分かったときだけ**、署名を要求しない **RSA 鍵交換（TLS 1.2）**へ絞って 1 度だけ握手をやり直す（`RdpTlsTransport.signingIsForbidden` → `restrictToRsaKeyExchange`）。⛔ **常に絞ってはいけない** — まともな証明書の相手まで前方秘匿性を失う。⛔ **例外メッセージの文字列では判定しない** — 判断の根拠は相手が出した証明書そのものに置く。⭐ 鍵交換が RSA になっても**認証情報は守られる**: CredSSP は NTLM のセッション鍵で TLS の公開鍵をバインドする（`CredSspBinding`）ので、中間者がいれば公開鍵が一致せず検出できる。
- ⚠⚠⚠ **緩い実装だけで確かめても、本番の相手には通らない（0.8.461〜0.8.472・実 Windows で連続して判明）。** 端末内の検証台（`scripts/rdp-testbed.sh`）は寛容なので**全部通ってしまい**、実 Windows で初めて 7 つの欠陥が順に出た。⭐ **プロトコル実装は「1 つの相手で通った」を根拠にしない。**
  - **licensing は来ないことがある** — ライセンスの要らない相手は License Error PDU を送らず Demand Active へ進む。「必ず来る」と書くと一切繋がらない。
  - **security header の有無は「長さ」で見分ける** — TLS では通常の PDU に security header が付かない。⛔ フラグのビットで見分けると、Share Control PDU の totalLength がたまたま `SEC_LICENSE_PKT` (0x80) を含んだときに画面更新を licensing と誤読する（`isShareControlPdu`）。
  - **Client Info は最後まで書く** — `TS_EXTENDED_INFO_PACKET` を clientAddress で止めると Windows が `errorInfo 0x1118`（SECURITYDATATOOSHORT9）で断る。タイムゾーン 172 バイトまで必要。
  - **`uncompressedLength` を正しさの判定に使わない** — 「PDU 全体の長さ」を入れる実装と「本体だけ」を入れる実装があり、どちらかに合わせるともう一方を必ず弾く。
  - **`errorInfo = 0` は「エラー無し」** — Windows は接続が落ち着いた直後にこれを 1 つ送る。値を見ずに投げると**繋がった瞬間に必ず切れる**。
  - **サーバーの苦情を捨てない** — Set Error Info は接続シーケンス・finalization・受信ループの**3 か所すべて**で拾う。1 か所でも読み飛ばすと、断られた理由が消えて「理由の無い EOF」にしか見えなくなる。
  - **必須の capability を省かない** — `orderFlags` の `ZEROBOUNDSDELTASSUPPORT` は描画 Order を 1 つも使わなくても立てる決まりで、Offscreen Bitmap Cache も「対応しない」と明示して送る。集合から抜くと必須欠けとみなす相手がいる。
  - **主張を食い違わせない** — `RNS_UD_CS_WANT_32BPP_SESSION` を立てながら「24/16/15bpp しか受け取れない」と言うと、サーバーは送る形式を決められない。
- ⛔⛔ **解決（0.8.477〜0.8.478）: 接続は成立するのに画面が 1 バイトも来なかった。** 症状は「NLA → MCS → activate → finalization まで完走し、`2400x1080` で `CONNECTED` になり、Save Session Info（`pduType2=0x26`）まで受け取るのに、その後は `readTpkt` が 1 バイトも読めないまま止まる」（fast-path なら TPKT ヘッダ検査で例外になるので fast-path でもなく、`refreshRectSupport=1` で全画面を要求しても変わらなかった）。⇒ 原因は**こちらの受信側の欠陥ではなく、Windows 11 が RDPGFX を使えない相手へ従来型 Bitmap Update を送らないこと**で、`drdynvc` + RDPGFX + RemoteFX を実装して画面が出た（→ 上の「RDPGFX」「RemoteFX」）。⭐ **これを決めたのは推測ではなく `xfreerdp3` の TRACE との突き合わせ**（`scripts/rdp-testbed.sh trace` を接続先へ向ける。要・資格情報）。**GFX を無効にした `xfreerdp3` でも同じ無音になる**ことを実測して、送ってこない側の性質だと確定させた。詳細は `99_private/HANDOFF/z2term/RDP-HANDOFF.md`。
- **slow-path 入力**: `RdpInput` が共通 GUI 入力を TS_INPUT_PDU_DATA へ変換する。pointer の「現在のボタン状態」は直前との差から Move / Press / Release に分け、ホイールは符号つき 9-bit rotation で送る。機能・修飾キーと Ctrl/Alt/Meta shortcut は scancode、通常文字は接続先の配列に依存しない Unicode Keyboard Event にする。送信は `RdpClient` の単一 sender thread に退避し、CLIPRDR と同じ `RdpTlsTransport.writeLock` で直列化する。

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
- **ダブルタップで単語を選ぶ（0.8.420・`WordFinder`）。** 長押し→なぞるより速く、パス 1 つ・ホスト名 1 つを掴む操作がこれだけで済む。
  - ⭐ **端末の単語は文章の単語ではない。** 掴みたいのはパス・ホスト名・ハッシュ・オプションなので、英数字に加えて **`@-./_~`** を単語の一部として扱う（xterm 系の既定と同じ集合）。`/usr/local/bin/z2attach` も `root@192.168.10.20` も `~/.bashrc` も 1 つになる。⚠ **`:` は入れない** — `src/main.kt:42:` から**ファイル名だけ**取りたい場面の方が多い。
  - **折り返し（wrapped）は跨ぐ。** 幅が狭いほど 1 つのパスが 2〜3 行に割れるので、跨がないと**一番効いてほしい場面で効かない**。前後それぞれ 16 行までを上限にする。
  - **漢字・かなは `BreakIterator` に切れ目を決めさせる。** 日本語には空白の切れ目が無いので、同じ規則で伸ばすと**その行の日本語が丸ごと**選ばれる。⚠ Android の `BreakIterator` は ICU の辞書を使うので実機では語単位に切れるが、**単体テストの JVM では 1 文字ずつになる**（規則だけで辞書が無い）。テストは「行まるごとにならないこと」で縛り、切れ目そのものは縛らない。
  - **全角で終わるときは右半分のセルまで選択に含める**（取れる文字列は変わらないが、含めないと帯が文字の途中で切れて見える）。
  - ⛔ **マウスを読んでいる TUI からは奪わない。** その手の TUI は自前でダブルクリックを解釈する（単タップは既に PTY へ送っている）。過去を遡っている間（scrollback）は PTY へ届かないので、そのときは選択に使う。
  - ⚠ **リンクを開くのを `onSingleTapUp` → `onSingleTapConfirmed` へ移した。** 押した瞬間に開く作りのままだと、**ダブルタップの 1 度目でブラウザが起動する**（URL の上は単語選択が一番効いてほしい場所）。遅れるのはリンクを開くときだけで、フォーカスと IME は従来どおり即時。開いてよいタップかどうか（フォーカス目的の初回タップでない / 選択解除のタップでない / マウスへ送ったタップでない）は `onSingleTapUp` で決めて `linkTapCandidate` で渡す。

### 6.6 コマンド履歴の永続化

- proot は終了時 SIGKILL で履歴が書かれない → 起動毎に rc/env を注入。bash: `histappend` + `PROMPT_COMMAND='history -a'`、zsh: `INC_APPEND_HISTORY`/`SHARE_HISTORY`。1 コマンド毎に追記し、再起動後も ↑ で履歴を辿れる。

### 6.7 ファイル共有 / 外部ストレージ

- SAF ホーム = `shared_home` (端末の `/root` と一致)。各 distro の rootfs(`/`) も公開。
- proot 内から `cd /sdcard` で Android 共有ストレージ (要 全ファイルアクセス許可)、`/storage/app` はアプリ専用領域 (権限不要)。

### 6.8 その他 UI

- タブ複数化（**長押し→左右ドラッグで並べ替え**、ダブルタップで閉じる。**タブ内で子プロセスが前景実行中のときだけ削除確認ダイアログを挟む**＝作業中の誤タップ防止。前景がログインシェルなら従来どおり即閉じる。判定は PTY master の `tcgetpgrp` を **プロンプト待機時の前景 pgid（起動直後に実測して確定する基準値）** と比較する。0.8.157 では `shellPid` と比較していたが、`shellPid` は forkpty の子＝**エンジン(proot/z2root)プロセスの pid** でありゲストのログインシェルとは別 pgid のため、アイドル時でも常に不一致→**常に「動作中」誤判定**する回帰があった。実測基準に改め解消 (0.8.160)）、ピンチでフォント拡縮 (8–32sp)、スクロール + 最新へ戻る ↓、スニペット、テーマ/フォント実プレビュー。
- 設定 (`SettingsSheet`): 0.8.14 で従来の下から重なるボトムシートをやめ、**全画面の「別ページ」**として表示（上部に戻る矢印 ← + システムバック対応）。

### 6.8.2 多言語化の土台 (B1・0.8.422)

**日英の 2 値をやめ、言語コードで文言を選ぶ形にした。** 挙動は変えていない（生成物は 1 バイトも変わらない）。
狙いは 1 つで、**3 言語目を置く場所を作ること**。

⚠ **文言は 2 系統ある。** 片方だけ訳しても揃わない。

| | 置き場 | 量 | 持ち方 |
|---|---|---|---|
| アプリ画面 | `res/values[-<言語>]/strings.xml` | 1,041 件 | Android 標準 |
| 端末に出る文言 | `proot/*.kt` の `t(en = …, ja = …)` | 346 件 | `CliText`（rootfs へ書き出すシェルスクリプトの中身なので res では持てない） |

- ⭐ **名簿は [`AppLanguages`](../../app/src/main/java/com/zerotoship/z2term/settings/AppLanguages.kt) 1 か所。** ここに 1 行足すと、設定画面の選択肢・端末に出る文言の選択・`Locale` の適用がまとめてその言語を知る。言語を増やす手順もこのファイルの先頭に書いてある。
- ⛔ **「英語ではない = 日本語」と書かない。** 0.8.421 までの `val ja = lang != "en"` は、**3 言語目を選んだ利用者に日本語を出す**書き方だった（`z2-macro` と `pacman-keyring` が実際にそう）。落とし先は必ず英語。`CliTextTest` が**生成物のレベルで**これを縛る（訳の無い言語で組んだスクリプトが英語版と 1 バイトも違わないこと）。
- **`LocaleHelper.language` は実在する言語コードを返す**（従来は `ja`/`en` の 2 値）。`== LANG_JA` で見ている箇所は**日本語固有の機能**の判定（かな面の有無・IME のかな入力）であって、文言の出し分けではない。
- **端末ロケールの照合は書き方（script サブタグ）まで見る。** ⚠ **簡体字と繁体字は別の言語として扱う** — 中身が違うので、片方しか無いときにもう片方へ流すと読めない字が並ぶ。Android は `zh-CN` とだけ言うことも `zh-Hans-CN` と言うこともあるので、どちらでも同じ答えへ行き着かせる（香港・マカオは `Hant` として来るので国コードを増やさなくても拾える）。**名簿に載せる前からテストで縛ってある**（`AppLanguagesTest`）。
- **訳し忘れの守りは 3 つ。** ①`app/build.gradle.kts` の lint で `MissingTranslation` / `ExtraTranslation` を明示的に error にし、CI (`lintDebug`) で落とす（**res だけ**）②`bash scripts/i18n-status.sh` が両系統の埋まり具合を数え、`--missing <言語>` で未訳キーを英語の原文つきで出す ③**端末に出る文言は `--check` が落とす**（下記・0.8.423）。⚠ **多言語化は一度の作業ではなく毎回の税** — 文言は版ごとに増えるので、埋める道具と落とす守りをセットで持つ。
- ⚠ **res に `values-<言語>/` を作った時点で lint が全未訳を数え上げる**ので、res は**全部埋めきってから足す**。端末に出る文言の方は途中でも通る（英語で出る）ので、res → CLI の順が楽。

#### 端末に出る文言が静かに腐るのを止める（0.8.423）

⚠ **lint が守るのは res だけ。** 端末に出る文言は未訳でも英語が出て**何も壊れない**ため、
新しい機能を足すたびに、訳した言語の `z2-*` の表示だけが少しずつ英語へ戻っていく
（画面は中国語なのに `z2-notify --help` は英語、という形）。res と違って **CI も緑のまま通る**ので、
気付く手段が実機しか無い。⚠ **これは 2 言語目を入れた直後から始まる**。

- **`AppLanguages.Entry` の `cliComplete` が「この言語は訳しきった」という宣言。** 印の付いた言語は `bash scripts/i18n-status.sh --check` が端末に出る文言 100% を要求し、欠けていれば終了コード 1 で落ちる。⛔ **訳しきる前に付けない** — 付けた瞬間に落ちる。訳の途中は `false` のままでよい（未訳は英語で出るのでアプリは壊れない）。
- **CI で効かせるのはユニットテスト** (`CliTranslationCheckTest`)。CI が回すのは `lintDebug` / `assembleDebug` / `testDebugUnitTest` の 3 つなので、**テストに載せるのが workflow を触らずに守りを足す一番安い道**。⭐ **数え方は `scripts/i18n_status.py` に一本化し、Kotlin 側では判定しない** — 同じ数え上げを 2 つ持つと、いずれ食い違って「表では 98% なのにテストは通る」という一番たちの悪い形になる。テストは同じ道具を呼んで終了コードを見るだけ。
- ⚠ **`python3` が無い環境ではテストをスキップする**（`GuiScriptSyntaxTest` が `sh` に対してしているのと同じ扱い）。python3 はビルドの必須要件ではないので、入っていないことを理由に開発機のテストは落とさない。CI (ubuntu-latest) には入っているので **push すれば必ず検査される**。
- **`--check` は res を見ない。** そちらは lint が error で守っており、二重に判断すると**どちらの言い分を直せばよいのか分からなくなる**。

#### 簡体字中国語を追加（B3 の 1 言語目・0.8.424）

**res 1,041 件と端末に出る文言 337 件を訳しきり、`cliComplete` を付けた 3 言語目。**
`AppLanguages.ALL` に 1 行、`res/values-zh-rCN/`、`t(…)` への `"zh-CN" to …` の 3 つで、
**使う側のコードは 1 行も変えていない**（0.8.422 で作った口がそのまま使えることの実証でもある）。

- ⭐ **en/ja の生成物が 1 バイトも変わらないことを機械的に確かめた。** 全スクリプトを言語ごとにファイルへ書き出し、`t(…)` へ変わり値を足す前後で `diff -r` を取っている。`pick()` は変わり値を先に見て、無ければ `lang == "ja"` で分けるだけなので、`"zh-CN"` の行が en/ja の結果に触れることは**構造上ありえない**が、345 か所を機械で書き換える以上は現物で確かめる。
- ⚠ **「まだ載っていない言語」をテストの前提に使わない。** `CliTextTest` が訳の無い言語として `"zh-CN"` を使っていたため、簡体字を載せた時点で前提が崩れた。ISO 639-1 で割り当てられない `zz` に替えてある。`AppLanguagesTest.resolve` も同じ理由で直した。
- **数え上げはコメントを飛ばす**（0.8.423 の後で判明）。`// 3 言語目は t(en = …, ja = …) の後ろへ` のような**書き方の注記**まで文言として数えており、総数が 8 件多く出ていた。印を付けた言語は 100% を要求されるので、そのままでは**訳しても届かない**。
- **`z2scan` の基準ファイルは言語ごと**。診断結果を文字列で比べる作りなので、言語を変えると全項目が「変化した」に見える。`z2scan` は基準に言語コードを書き込み、違えば取り直しを促す（この言語コードも `t(…)` の 1 つとして `zh-CN` を持たせてある）。
- ⛔ **中国語の入力方式は入れない。** 拼音・注音から漢字を選ぶ変換エンジンが要り、日本語 IME と同規模の仕事になる。端末に打つのはコマンド（ASCII）なので、漢字は OS の入力方法へ回す。**UI と文言だけを訳し、キーボードは対応しないと明記する**（README / HANDBOOK の両方に書いた）。
- ⚠ **端末フォントに CJK は無い。** 同梱の JetBrains Mono / Fira Code / IBM Plex Mono は CJK を持たず、システムフォントへ落ちて表示される（日本語で既にそうなので新規の問題ではない）。⛔ CJK フォントの同梱は「21MB・第三者 prebuilt を持たない」方針と正面衝突するので採らない。

#### 繁体字中国語を追加（B3 の 2 言語目・0.8.426）

**res 1,041 件と端末に出る文言 346 件を訳しきり、`cliComplete` を付けた 4 言語目。**
簡体字で通した道がそのまま使え、**使う側のコードはまた 1 行も変えていない**（触ったのは名簿の 1 行・
`res/values-zh-rTW/`・`t(…)` への `"zh-TW" to …` だけ）。

- ⛔ **繁体字は簡体字の字を置き換えたものではない。** 字体だけ変えると大陸の言い回しがそのまま残る。台湾で通じる語へ直した: 软件→軟體 / 文件→檔案 / 程序→程式 / 进程→行程 / 默认→預設 / 界面→介面 / 数据→資料 / 服务器→伺服器 / 软件包→套件 / 屏幕→螢幕 / 鼠标→滑鼠 / 用户→使用者 / 缓存→快取 / 保存→儲存 / 宏→巨集 / 磁贴→圖塊 / 密钥→金鑰 / 短信→簡訊 / 检测→偵測 / 会话→工作階段 / 终端→終端機 …。
- ⚠ **同じ語が 2 つの意味で使われている場所が罠。** `应用` は名詞（App = 應用程式）と動詞（apply = 套用）の両方で出ており、機械的に置き換えると「常駐服務從下次啟動開始應用程式」のような文になる。`运行时`（runtime = 執行環境 ／「実行しているとき」= 執行時）と `对象`（object = 物件 ／ 対象 = 對象）も同じで、**どちらも文脈を見て分けた**。
- ⚠ **語の置き換えは左から最長一致で当てる。** 語ごとに順番に置き換えると語の境界をまたいで食う — `打开发布页面` が `开发` に先に食われて「打開發布」になった。あわせて、そのままで正しい語（`控制` `公里` `表示` …）は**単字変換から守る**（守らないと `制` の既定が `製` なので「控製」になる）。
- ⭐ **en / ja / zh-CN の生成物が 1 バイトも変わらないことを現物で確かめた**（簡体字のときと同じ手順。全スクリプトを言語ごとにファイルへ書き出して前後で `diff -r`）。zh-TW 側は 45 本中 44 本が英語版と別物になった（残る 1 本は文言を持たない内部用ディスパッチャ）。
- **名簿では `zh-CN` の後ろに置く。** 地域の指定が無い素の `zh` は、名簿で先に置いた方（簡体字）へ行く。`zh-Hant-*` は script サブタグで拾うので、香港・マカオもここへ来る（`AppLanguages.SCRIPT_ALIASES`）。
- **`z2scan` の基準ファイルに書く言語コード**にも `zh-TW` を持たせた（これも `t(…)` の 1 つ）。忘れると「言語を変えた瞬間に全項目が変化扱い」になる。
- ⛔ **繁体字の入力方式も入れない**（簡体字と同じ理由）。しかも入力方式が 1 つではない（台湾は注音、香港は倉頡・速成）ので、なおさら本体では持たない。

**内蔵キーボードとの関係**: 表示言語と入力方式は**別の話**として切り離す。かな面はアプリの言語が日本語のときだけ既定で有効（`legacyKanaAvailable`）で、他の言語では英字面 + 数字面になる。⛔ **中国語の変換エンジン（拼音・注音）は持たない** — 端末に打つのはコマンド（ASCII）で、漢字を打つ場面は OS の入力方法に切り替えれば済む。内蔵キーボードは「OS の入力方法を使わずに端末を打つための道具」であって、言語ごとの IME を抱える器ではない。⭐ **アクセント付きの字（`ñ` `á` `¿` 等）はキー配列の話**で、既存のフリック機構（キーごとに上下左右へ割り当てる）でそのまま賄える。

⚠ **「内蔵キーボードを他でも使う」（OS の入力方法・0.8.276）では話が変わる。** 端末画面の中では自動修正はむしろ**害**（`ls -la` を勝手に直されては困る）なので ASCII 面に予測を付けないのが正しいが、OS の入力方法として使うなら相手はチャットやメールで、**その言語で普通に文章が打てないと使い物にならない**。

- **英語**: 変換が要らない言語なので**今のまま成立する**。
- **スペイン語**: 必要なのは字（`ñ` とアクセント付き母音・`¿` `¡`）だけで、**変換エンジンは要らない**。配列を 1 枚足せば入力方法としても成立する。
- **中国語（簡体・繁体）**: ⛔ **成立しない。** 拼音・注音から漢字を選ぶ変換が要り、しかも繁体字は入力方式が 1 つではない（台湾は注音、香港は倉頡・速成）。**UI とメッセージの翻訳だけを行い、キーボードは中国語入力に対応しないと明記する。**
- ⚠ 「予測変換」はどの言語にもあるが**中身が違う**: 日本語・中国語は**変換**（読み → 別の文字体系。無いと打てない）、韓国語は字母の**組み立て**、ラテン文字の言語は**予測・自動修正**（無くても打てる）。**必須なのは前の 2 つだけ**で、ここを一緒くたにすると「全言語に IME が要る」と見積もりを誤る。

### 6.8.1 アプリロック (A2・0.8.421)

画面を出す前に**端末の本人確認**を挟む (`security/AppLock.kt` + `ui/lock/LockScreen.kt`)。既定 OFF。

- ⛔ **守れるのは画面だけ。** ロック中も**セッション・常駐サーバー・`z2-session attach` は動き続ける**。止めると自動化と常駐が壊れ、「ロックを掛けたら朝までのビルドが死んでいた」になる。⚠ **この線引きを設定画面と HANDBOOK の両方に書く** — 書かないと「ロックしたのだから中も守られる」と読まれる。
- ⭐ **依存を増やしていない。** `androidx.biometric` は入れず、OS の `android.hardware.biometrics.BiometricPrompt` (API 28+ / 下限 29) をそのまま使う。同梱物を増やさない方針と F-Droid 提出に沿う。代わりに版差 (API 30 で `setAllowedAuthenticators` へ移行、それ以前は `setDeviceCredentialAllowed`) を自分で吸う。
- ⛔ **指紋だけに絞らない。** 指が濡れている / センサーが壊れた / 生体を登録していない、のどれでも締め出しになる。`DEVICE_CREDENTIAL` (画面ロックの PIN・パターン) を許す。⭐ **許すと「キャンセル」ボタンが不要になる** — 生体と画面ロックの両方を指定した状態で `setNegativeButton` を付けると `build()` が例外を投げるので、作りも単純になる。
- ⛔ **本人確認できない端末では ON にさせない** (`isAvailable`)。ON にできてしまうと**二度と開けないアプリ**が 1 つ増える。API 30+ は `BiometricManager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)`、API 29 は方式を指定して問い合わせられないので `KeyguardManager.isDeviceSecure` で見る。
- **状態は 3 つ (`UNKNOWN` / `LOCKED` / `UNLOCKED`)。** ⚠ **設定を読めていない間を「掛かっていない」に倒さない** — DataStore は非同期なので、倒すと**読めるまでの数フレーム端末の中身が見える**（履歴画面の縮小画像として残ることもある）。`UNKNOWN` の間は端末画面を**組み立てない**。
- **掛かる条件は設定から選ぶ**（利用者の指示）。すぐ / 30秒後 / 1分後 / 5分後 / 起動時だけ。既定は 30 秒 — ⭐ **他アプリへ 1 往復する操作を邪魔しない**ため（ブラウザからコピーして戻る・通知を見て戻る）。⚠ **起動時は猶予に関係なく必ず掛ける**（猶予は「戻ってきたとき」の話）。
- ⚠ **画面回転・言語切替では「離れた」ことにしない。** Activity の作り直しも `onStop` を通るので、`isChangingConfigurations` で弾かないと**猶予「すぐ」のとき画面を回すたびにロックが掛かる**。
- ⚠ **使っている最中に掛け直さない。** 設定を ON にした瞬間にロック画面が出ると、いじった本人が閉め出される。ON は次に離れて戻ってから効かせ、OFF はその場で解く。
- **本人確認は状態の変化で出す** (`repeatOnLifecycle(RESUMED)` + `AppLock.state` の購読)。⚠ **`onResume` では出せない** — 冷えた起動では `onResume` の時点でまだ `UNKNOWN` なので、**冷えた起動でだけ自動で出ない**という形で抜ける。⚠ **断られたら出し直さない**（`unlockFailed`）— 押してもいないのに端末側のロックアウトまで進む。押し直しはロック画面のボタンから。
- **離れている間だけ `FLAG_SECURE`。** ⭐ 履歴画面 (最近使ったアプリ) の縮小画像に中身を残さないため — ロックを掛けても切り替え画面にさっきの画面が出ているなら隠せていない。⚠ **常時立てない**（使っている最中のスクリーンショットまで撮れなくなり、端末アプリではそれが邪魔になる）。⚠ 立つのは `onPause` なので、縮小画像を撮る時点との前後関係は端末実装に依存する。
- **設定は独立したグループ** (`SettingsGroup.APP_LOCK`)。⚠ **見出しをそのまま機能の名前にしてある** — 「セキュリティ」のような入れ物にすると中に 1 つしかないのに開けるまで分からず、既存のどのグループへ入れても**探す人が最後に開く場所**になる。

### 6.9 内蔵キーボードを OS の入力メソッドとして出す (`Z2ImeService`・0.8.276)

**何ができるか**: 内蔵キーボードを Android の**入力方法 (IME)** として登録する。ユーザーが OS の一覧で
有効にして選ぶと、**アプリ内の入力欄 (スニペット・SSH プロファイル・SFTP・設定・ウィジェット設定…)
でも他アプリでも**、端末と同じキーボード・同じかな漢字変換で打てる。

**なぜ要ったか**: 内蔵キーボードは `TerminalKeyboard(onBytes = …)` という形で**端末へバイトを送る
専用部品**として作られていて、`TextField` とは繋がっていなかった。⚠ 同じアプリの中なのに、端末では
自前の変換が使えて、入力欄に触れた瞬間に OS のキーボードへ変わる。利用者から見れば
「**アプリ内では内蔵キーボードで打てない**」であり、これは機能の欠落として体験される。

**入力欄ごとに自前描画へ差し替える道は採らない。** 検索バー (§ 検索バーの `SearchQueryField`) が
その方式で、あれ 1 つのために「タップでキャレット移動」「サロゲートペアを 2 code unit で消す」
「レイアウト結果の長さでクランプしないと落ちる」といった作り込みが要った。同じことを 20 箇所へ
広げると、⚠ **文字選択・コピペ・カーソル移動・オートフィルまで全部作り直す**ことになり、
OS が持っている品質を捨てて自前の不具合に置き換えるだけになる。IME にすれば
**OS のキーボード切替がそのまま「内蔵 / システム」の切替**になり、利用者の要望どおり
「切り替えたときだけシステムキーボード」が**アプリの作りではなく OS の仕組みとして**手に入る。

**同じ部品しか使わない**: 描画は端末と同じ [`TerminalKeyboard`] + [`CandidateBar`]、変換も同じ
[`ComposingState`] / [`KkcConverter`] / [`ImeHistoryStore`] (学習履歴も共通なので、端末で覚えた語が
入力欄でも出る)。⚠ **見た目や候補の出方をここで作り分けない** — 同じキーボードに見えなくなるうえ、
直す場所が 2 つになる。そのため `CandidateBar` と `scaledKeyboardStyle` は private から internal へ
広げた (キーボード高さの設定も共通に効く = 切り替えるたびに背丈が変わらない)。

**違うのは出口だけ** ([`ImeKeyTranslator`]): 端末向けのバイト列を `InputConnection` の操作へ読み替える。

| 内蔵キーボードが出すもの | 入力欄での意味 |
|---|---|
| 印字可能文字 (UTF-8) | `commitText` (連続分は 1 回にまとめる) |
| `0x7F` / `0x08` (⌫) | **`KEYCODE_DEL` のキーイベント**。⚠ `deleteSurroundingText` だと**範囲選択中に消えない** |
| `0x17` / `0x15` (⌫ の左右フリック) | 単語削除 / 行頭まで削除。`getTextBeforeCursor` を見て長さを出す (`readline` の `unix-word-rubout` と同じ数え方)。⚠ **相手が z2term の端末のときだけは Ctrl+W / Ctrl+U のキーイベントをそのまま送る** (0.8.312。下記) |
| `0x0D` (⏎) | 複数行の欄なら改行、1 行の欄なら**その欄が求めている動作** (`performEditorAction`) |
| `0x09` (TAB) | `KEYCODE_TAB` (次の欄へ) |
| `0x1B` (ESC・ALT の前置) | **捨てる**。ALT+文字 は文字だけが入る |
| その他の制御コード (Ctrl+文字) | **捨てる** |

⚠ **端末専用のものを入力欄へ流さない**。制御コードをそのまま入れると見えない 1 文字が紛れ込み、
**保存してから気付く**種類の事故になる。⚠ ただし ⌫ のフリックだけは削除操作として活かす —
端末で使い慣れた指の動きが入力欄で無反応だと、同じキーボードに見えない。
`ImeKeyTranslatorTest` が読み替えの表を固定する。

**変換中は OS の仕組みに任せる**: `composing.text` の変化を `setComposingText` / `finishComposingText`
へ流すだけ (端末と検索バーでは自前で下線を描いているが、相手が本物の入力欄ならプリエディットは
OS が描く)。確定は `ComposingState.onCommit` から `commitText`。⚠ `commitText` は composing 領域を
**置き換える**仕様なので、先に `setComposingText` を出していても二重に入らない。

⚠ **打ちかけを捨てるときは `setComposingText("")` を先に出す** (0.8.312)。`finishComposingText` は
「今の変換中を**そのまま残して**装飾だけ外す」メソッドなので、これだけ呼ぶと**捨てたはずのかなが
確定されて入力欄に残る**。`composing.text` が空になるのは (1) ⌫ の左右フリック等で打ちかけを
**捨てた**とき、(2) 確定して `commitText` した後、の 2 つで、(1) を確定に化けさせないために空文字で
置き換えてから終える。(2) では `commitText` の時点で変換中が無いので空の置き換えは何もしない。
⚠ これは `onStartInputView` / `onFinishInputView` の `composing.reset()` (入力欄が変わったら打ちかけを
捨てる) にも効く — 直すまでは**前の欄のかなが次の欄へ確定されて入る**経路が残っていた。

⚠ **端末が相手のときは「数えて消す」が成り立たない** (0.8.312)。`TerminalInputView` は編集中の
文字列 (editable) を持たないので `getTextBeforeCursor` が常に空になり、単語削除 / 行削除は長さ 0 =
**何も起きない**。端末側は `EditorInfo.privateImeOptions` に印 (`TerminalInputView.TERMINAL_IME_OPTION`)
を載せ、入力メソッドはそれを見たときだけ **Ctrl+W / Ctrl+U を `KeyEvent` として送る**
(`sendDownUpKeyEvents` は修飾を載せられないので `KeyEvent` を自分で組み、`KeyCharacterMap.VIRTUAL_KEYBOARD`
を使う)。端末は `AndroidKeyMapper.mapKeyEvent` でこれを `0x17` / `0x15` に直して PTY へ流すので、
**どこまで消すかは shell が決める** = 内蔵キーボードで打っているときと同じ結果になる。
⚠ 印が無い入力欄へこのキーイベントを送らないこと — アプリによっては Ctrl+W 等に別の割り当てがある。

**実装上の注意**:
- ⚠ **`InputMethodService` は `LifecycleOwner` ではない**。`ComposeView` は lifecycle /
  ViewModelStore / SavedStateRegistry の 3 オーナーを view tree から探すので、サービス自身が
  実装して `setViewTree*Owner` で載せる。無いと入力ビューを出した瞬間に落ちる。
- ⚠ **オーナーは `ComposeView` だけでなく入力メソッド窓の `decorView` にも載せる** (0.8.277 で修正)。
  Compose は composition を作るとき `AbstractComposeView.resolveParentCompositionContext()` →
  `windowRecomposer` と辿り、**窓の根から** `findViewTreeLifecycleOwner()` を呼ぶ。入力メソッドの窓は
  `Dialog` なので根は decorView 配下 (`parentPanel`) にあり、`ComposeView` に付けただけでは見つからない。
  0.8.276 はこれで `IllegalStateException: ViewTreeLifecycleOwner not found` を**メインスレッドの
  未捕捉例外**として上げ、キーボードを選んだ瞬間に**アプリごと落ちていた** (端末セッションも道連れ)。
  窓は画面回転などで作り直されるため、`onCreateInputView` と `onStartInputView` の両方で載せ直す。
- **`ComposingState` はサービスが持つ**。入力ビューは設定変更などで作り直されるので、Compose 側に
  持たせると打っている途中のかなが消える。⚠ **入力欄が変わったら捨てる** (`onStartInputView`) —
  持ち越すと前の欄へ打っていたかなが次の欄に確定されて入る (端末と検索バーで踏んだのと同じ形)。
- **端末画面は変わらない**。IME を有効にしても、端末タブはこれまでどおりアプリの中に描く
  キーボードを使う (PTY へバイトを送る経路の方が、制御コードも修飾キーもそのまま通せる)。
- ⚠ **有効化と選択はユーザーの操作でしかできない** (OS の決まり)。アプリ側は設定画面から
  `Settings.ACTION_INPUT_METHOD_SETTINGS` と `showInputMethodPicker()` を開くところまで。
- **設定の置き場は「キーボード・入力」グループ** (0.8.277)。0.8.276 では「常駐サーバー・自動化」の
  下にあり、キーボードの設定を探した人が見つけられなかった。同じ理由で旧「入力・言語」グループ
  (IME 学習履歴 / 言語) もキーボードへ統合した — **打つときの設定を 1 か所にまとめる**。
  ⚠ **グループ名は「キーボード・入力・言語 / Language」** (0.8.279)。統合した結果、表示言語
  (English 切替) がここにあることが名前から読めず、探せない人が出た。英単語 "Language" を
  併記するのは、**日本語が読めない利用者でも言語切替に辿り着ける**ようにするため。
- **表示言語は「端末に合わせる」＋対応言語の一覧、既定は端末** (`LocaleHelper`・0.8.363・要望。0.8.424 の 简体中文、0.8.426 の 繁體中文 が加わって 5 択)。
  ⚠ 0.8.362 までは **`ja` 固定が既定**で、日本語以外の端末に入れても日本語で立ち上がっていた
  (設定を開いて切り替えるまで読めない画面が続く)。⚠ **保存値と実効言語を分ける** —
  `languageSetting` が `system`/`ja`/`en` を返し、`language` は**常に `ja` か `en`** を返す。
  `language` の呼び出しは 20 か所以上あり、ほぼ全てが `== LANG_JA` で日本語かどうかだけを見ている
  (かな面の有無・`z2-*` の表示言語・IME の判定) ので、そこへ `system` を流すと**全部が
  「日本語ではない」側へ倒れる**。解決は `LocaleHelper` の中で終わらせ、外へは 2 値しか出さない。
  ⚠ 端末の言語を読むのに **`Locale.getDefault()` は使えない** — `wrap` が `Locale.setDefault` を
  呼ぶため、一度アプリ側の言語を適用するとプロセス内の既定が上書きされ、「端末に合わせる」が
  **直前に選んでいた言語に張り付く**。`Resources.getSystem()` はアプリの Configuration を
  通さないので、ここだけは汚れない。⚠ 「端末に合わせる」でも**解決後の `ja`/`en` で明示的に wrap する**
  (base をそのまま返すと、前に適用した `Locale.setDefault` がプロセスに残る)。
- ⚠ **入力ビューはナビゲーションバーを避ける** (0.8.279)。Android 15 (targetSdk 35) は
  入力メソッドの窓も画面の端まで広げるので、何もしないとキーボードの最下段 (← ↓ ↑ → ⏎) が
  **3 ボタンナビゲーションバーの裏**に潜り、押しても「戻る」等になって届かない。
  `ViewCompat.setOnApplyWindowInsetsListener` で受けた **`tappableElement`** の下端ぶんを
  入力ビューの下パディングにして持ち上げる。⚠ `navigationBars` ではなく `tappableElement` を
  見るのは、**ジェスチャー操作の端末では 0 が返る**ため — バーの無い端末に余計な隙間を作らない。
  窓の作り直しでリスナーが来ない経路があるので `onStartInputView` でも `rootWindowInsets` から
  読み直す。
- ⚠ **候補バーの席を常に確保して入力ビューの高さを動かさない。席は透明で insets からも外す** (0.8.292)。
  候補バー ([CandidateBar]) は非変換時に高さ 0、変換開始で `CandidateBarHeight` (76dp) に
  なる。IME ではこれが入力ビューの高さを変え、その瞬間に **入力メソッドの窓がリサイズ**される。
  新しい窓枠がタップを配る側 (システム) へ伝わるまでの数フレームだけ、タップは**古い窓枠**を
  基準に座標へ直され、**実際に触った位置より 76dp 上のキー**が反応する。⚠ ズレるのは
  **候補バーが出る瞬間だけ**で、出てしまえばズレない (窓枠が伝わり終えている) — つまり
  リサイズの**過渡期そのもの**が原因であり、座標を後から補正する類の対処では消せない。
  端末画面では候補バーが下端アンカーの `Column` に乗りキーボードが動かないので出なかった。
  - **修正**: `Box(height = CandidateBarHeight)` で席を**常に確保**し、変換の有無で入力ビューの
    高さが変わらないようにする ＝ リサイズが起きない ＝ 過渡期が存在しない。席は言語で出し分け
    **しない** (日本語⇄英語の切替でも高さを動かさないため)。
  - **席を見せない工夫**: 席には背景を**塗らない**ので、候補バーが出ていない間は透けて下の
    アプリが見える。さらに `onComputeInsets` で席のぶんを `contentTopInsets` /
    `visibleTopInsets` から差し引き、「キーボードの上端から下だけが入力メソッド」と伝える。
    相手アプリは席のぶん押し上げられず、席の上のタップは相手アプリへ通る
    (既定の `TOUCHABLE_INSETS_VISIBLE`)。⚠ insets は**窓の大きさではない**ので、この出し分けで
    窓は 1px も動かない — 高さ固定とは両立する。
  - 定数 `CandidateBarHeight` は `CandidateBar` と共有し、実高さと確保量がズレないようにする。
- **最後に使っていた面 (英字 / 日本語フリック) を覚えて次回その面で開く (0.8.295)**。
  「あ」/ABC を押すたびに `AppSettings.imeJapaneseMode` (DataStore key `ime_japanese_mode`) へ
  保存し、入力ビューを組むとき `TerminalKeyboard(initialJapaneseMode = …)` として渡す。
  ⚠ **覚えるのは入力メソッドだけで、端末画面の内蔵キーボードは常に英字面から始まる** —
  端末は英字で、他アプリは日本語で打ち始めることが多く、1 つの設定を共有すると必ずどちらかが
  外れる。`TerminalKeyboard` 自身は面を保存しない (既定 `initialJapaneseMode = false` /
  `onJapaneseModeChange = {}`) ので、**どちらの側が覚えるかは呼出し側だけを見れば分かる**。
  ⚠ 復元は `showJapaneseKeyboard` が true のときだけ (English 表示では「あ」キーが無いので、
  日本語面で開いても筋が通らない)。
- **開き直したキーボードは素の状態から出す (0.8.307)**。⚠ 入力ビューは窓を閉じても
  **壊されずに使い回される**ので、Compose の `remember` は閉じただけでは捨てられない。
  結果、パッド (絵文字 / 貼り付け) を開いたまま閉じると**次に開いてもパッドのまま**出ていた
  (かなキーが見当たらない = 壊れて見える)。`onWindowHidden` で鍵 (`keyboardSession`) を進め、
  `key(keyboardSession) { TerminalKeyboard(…) }` でサブツリーごと作り直す。
  ⚠ 進めるのは**窓が隠れたとき**であって `onStartInputView` ではない — そちらは
  **キーボードが出たまま入力欄が変わっただけ**でも呼ばれるので、打っている最中に面や修飾が飛ぶ。
  ⚠ **持ち越すのは面だけ**という切り分けがここで効く: 面は設定 (`ime_face`) に書いてあるので
  作り直しても `initialFace` から戻り、パッド・⇧/CTRL/ALT・`?#` のような**一時状態だけ**が落ちる。
  - **検証**: 候補バーの出現前後で入力ビューの高さ (`View.onSizeChanged`) が変わらないこと、
    タップの `rawY - getLocationOnScreen()[1]` と `MotionEvent.y` が一致することを実機で確認する。

**次段の候補**: 入力欄の種別 (`EditorInfo.inputType`) に合わせた出し分け (数値欄では数字段だけ、
パスワード欄では学習しない)、⌨ から OS のキーボードへ戻る導線、`supportsSwitchingToNextInputMethod`
の地球儀キー。stage 1 は「**アプリ内のどの欄でも内蔵キーボードで打てる**」までを範囲とする。

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
| キーボードスタイル | keyboardStyleId | "spacious" | compact / spacious |
| キーボードモード | keyboardMode | "custom" | custom / system |
| 横画面キーボード位置 | landscapeKeyboardPosition | "bottom" | left / bottom / right |
| 横画面サイドKB幅 | landscapeKeyboardWidthDp | 420 | 280–700 dp |
| 横画面キーボード高さ | landscapeKeyboardHeightDp | 320 | 200–500 dp |
| 縦画面キーボード高さ | portraitKeyboardHeightDp | 320 | 200–500 dp |
| GUI 音声 | guiAudioEnabled | false | true/false（オプトイン PulseAudio ブリッジ） |
| GUI 拡大率 | guiMagnification | 1.5 | 0.5–3.0 |
| ダウンロード前確認 | confirmBeforeDownload | true | true/false |
| 常駐サービス | keepAliveService | true | true/false（ツールバーの 🔒 ロックで ON/OFF。**ツールバーから隠しているときだけ設定 › ツールバーにもトグルが出る** (0.8.194)。**常駐サーバー稼働中は 🔒 が薄くロックされトグル不可**になり、タップで終了ダイアログが出る (0.8.204)） |
| 画面消灯ロック | keepScreenOn | false | true/false（ツールバーの 💡 で ON/OFF。**永続化して次回起動時に復元** (0.8.144)。隠しているときは設定 › ツールバーから (0.8.194)） |
| キーボード表示バー | keyboardToggleBar | true | true/false（ON=キーボード上にトグルバー。OFF=バー無しで ⌨ ボタンのダブルタップ切替 (0.8.145)） |
| 補助キーバー | specialKeyBar | true | true/false（OS のキーボード使用時に `SpecialKeyBar` / `GuiSpecialKeyBar`（ESC・TAB・CTRL・矢印…）を出すか。OFF で出さない。内蔵キーボードには影響しない (0.8.279)） |
| ツールバー並び順 | toolbarOrder | ""（既定順） | カンマ区切り id。長押しドラッグで更新。隠しているボタンの id も残す |
| ツールバー非表示 | toolbarHidden | ""（全部出す） | カンマ区切り id。設定 › ツールバーでタップ切替。⚙ は指定できない (0.8.194) |
| 端末ログ 保存先 | sessionLogDir | "z2term-log" | ホーム (~) からの相対パス (0.8.195) |
| 端末ログ ファイル名 | sessionLogNameTemplate | "{date}-{tab}.txt" | `{date}` / `{tab}` を展開。パス区切り等は `_` に置換 |
| 端末ログ 日時書式 | sessionLogTimeFormat | "yyyy-MM-dd_HHmm" | `SimpleDateFormat` パターン。壊れていても既定に落として記録は始める |
| 端末ログ 過去分を含める | sessionLogIncludeScrollback | false | ON で開始時に画面 + スクロールバックを先に書く |
| 端末ログ 追記 | sessionLogAppend | false | OFF は毎回新規（同名なら `-2` `-3`） |
| 端末ログ 生ログ | sessionLogRaw | false | ON でエスケープをそのまま残す（不具合報告用） |
| 端末ログ 全画面も記録 | sessionLogAltScreen | false | ON で alt screen 中も書く |
| 端末ログ 自動開始 | sessionLogAutoStart | false | ON で新しいタブが繋がった時点から記録 (0.8.243) |
| 端末ログ 伏せ字 | sessionLogMaskSecrets | **true** | 鍵・トークンらしき部分を `[z2term:masked]` に (0.8.243)。完全ではない |
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

`noInstallTimeout`（インストールタイムアウト無効化）等も DataStore (`z2term_settings`) に保持。SSH プロファイルは別 DataStore (`z2term_ssh`) に JSON で保存。

**設定を初期化**（アクション）: 設定末尾（アプリ情報とライセンスの間）の「設定を初期化」ボタン（`danger` 表示）は、確認ダイアログを挟んで `AppSettings.resetToDefaults()`（DataStore `z2term_settings` を `clear()`）を呼ぶ。全キーが消えるので上表の各値・裏設定の解放フラグ・常駐サーバー定義・ツールバー並び順・各種ログ設定がすべて既定へ戻る（実行エンジンも既定 z2root に戻る）。rootfs（インストール済み OS）・ユーザファイル・言語（別 SharedPrefs `z2term_locale`）には触れない。

**更新を確認**（アクション、0.8.290）: アプリ情報セクションのバージョン行直下に置く「更新を確認」ボタン。押した瞬間だけ `UpdateChecker.check()`（`update/UpdateChecker.kt`）が GitHub Releases API（`/releases/latest`）へ 1 回 GET し、`tag_name` の major.minor.patch を `BuildConfig.VERSION_NAME` と数値比較する。**設定を開いただけでは通信せず、自動チェック・起動時チェック・バックグラウンド通信はしない**（ユーザーが押したときだけ通信する方針）。新版があれば版数を表示し「リリースページを開く」で `html_url` を `ACTION_VIEW` で開く。設定は保持しない（押すたびに問い合わせる一過性の状態）。判定の網羅は `numbersOf` の先頭 3 数値抽出で、`-alpha` や過去タグの commit ハッシュ（数字混じり）を比較に混ぜない。

**入れ替えまでやる（`z2-update` と「ダウンロードしてインストール」、0.8.371）**: 0.8.290 では「**アプリ内自己更新（DL+インストール）は入れない**（F-Droid 適合）」としていたが、**ここで方針を変えた**（利用者の要望: 「せっかくのターミナルアプリなのに、更新だけ手で APK を取ってくるのはおかしい」）。⚠ **F-Droid 適合は「機能を持たない」ことではなく「配布元から入った版では使わない」ことで守る** — [`UpdateInstaller.isManagedByStore`] が `getInstallSourceInfo`（API 30 未満は `getInstallerPackageName`）を見て、`org.fdroid*` / `com.android.vending` / `com.aurora.store*` から入った版では**確認より先に断る**。その版を入れ替えるのは配布元の仕事で、版の比較（GitHub Releases 決め打ち）自体が噛み合わない。⚠ **`REQUEST_INSTALL_PACKAGES` は宣言する**ので、F-Droid へ出すときはこの権限の説明が要る（申請前に必ず読み合わせること）。
  - ⛔ **「自動更新」と書かない**。Android は自分自身の入れ替えに**必ず OS の確認画面**を挟む（端末オーナーか root 以外に例外は無い）。できるのは**確認画面が出るところまで**で、最後の 1 タップは必ず人が押す。押さないと入らないものを「自動」と呼ぶと、押し忘れた人が入ったつもりで古い版を使い続ける。
  - **⚙設定のボタンと `z2-update` は [`UpdateFlow.run`] 1 本を通る**。片方にだけ条件や順番を書くと「端末からは入るのに設定からは入らない」という再現条件の見えない食い違いになる。文言だけを呼び出し側（GUI は strings.xml、CLI は `Z2ApiMsg`）が持つ。
  - ⚠ **落とす前に「不明なアプリのインストール」を見る**（[`UpdateInstaller.canInstall`]）。許可が無いまま進めると 20MB 落とした末に**確認画面が出ないだけ**で終わり、何が足りないのか画面のどこにも出ない。
  - ⚠ **`STATUS_PENDING_USER_ACTION` を捨てない**（[`UpdateStatusReceiver`]）。OS は確認画面を**結果に添えた Intent** としてよこすだけで、自分では出さない。受けて `startActivity`（`NEW_TASK` 必須 — `z2-update` は SSH の向こうから叩かれる）するまで「押したのに何も起きない」に見える。PendingIntent は **API 31+ で `FLAG_MUTABLE`**（不変にすると添えられた Intent を取り出せない）。
  - ⚠ **後片付けは二重にする**。入れ替えの瞬間に自分が落とされるので「入り終わったら消す」は当てにできない。受け取れたら消し、**次の起動でも消す**（[`Z2TermApplication`] → [`UpdateInstaller.cleanupDownloads`]）。消す対象は `z2term-*.apk` と `*.apk.part` だけ（保存先を `/sdcard/Download` にもできるため、人のファイルに触らない）。⚠ **落とすファイル名はこちらで決める**（リリース側の名前を使わない）— 名前が揺れると掃除の網から外れて消し残る。
  - ⚠ **ダウンロードは `.part` に書いて最後に rename**。途中で切れたものが正しい名前で残ると、壊れた APK を「もうある」と誤認して入れにいく。リリースが申告するバイト数と照合する。
  - ⚠ **`z2api` の worker とは別スレッドで捌く**（`Z2ApiBridge.updateWorker`）。ブリッジの worker は 1 本なので、数十秒のダウンロードを載せると `z2-notify` も `z2-session` も止まって見える。CLI 側は `Z2API_WAIT=3000`（300 秒）まで待つ — 既定の 5 秒は「アプリが止まっているときに延々と付き合わない」ための値で、ダウンロードはその外側。
  - **落とし先と自動削除は設定に置く**（`updateDownloadDir` / `updateKeepApk`、`z2-update --dir` / `--keep`）。既定はアプリ内 + 入れ替え後に削除。

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
# 個別: build-z2root.sh / fetch-fonts.sh
sh scripts/z2root-cmdtest.sh          # z2root の難所を踏む壊れやすいコマンド群を横断テスト(全10グループ・未導入はskip・末尾に非ゼロ一覧。SKIP_NET/SKIP_BUILD/RUN_SSHD/RUN_PRIV)
bash scripts/z2c pull alpine          # OCI イメージを取得して rootfs 化 (取得と展開のみ・隔離起動は未対応。10.1 参照)
bash scripts/gw.sh :app:assembleDebug   # オンデバイスはこちら (下記)
./gradlew :app:assembleDebug          # APK (rootfs は非同梱・起動時 DL)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- 同梱物: `src/main/jniLibs/arm64-v8a/{libz2root,libz2accept,libz2attach}.so`(ソースからビルド)、`assets/fonts/*.ttf`。
- rootfs は APK に含めず、`DistroSpec.ALPINE` の公式 CDN URL + SHA-256 で起動時に取得する。第三者 native prebuilt (proot/talloc 等) は F-Droid 非適合なので持たず、実行エンジンは同梱ソースからビルドする z2root だけ。
- **F-Droid のビルドでは署名設定が機械的に削除される (0.8.414)。** ビルドサーバーは `signingConfigs { ... }` ブロックと `signingConfig = <空白を含まない式>` の行を消してから `assembleRelease` する (署名は F-Droid 自身が行うため)。そのため **`release { }` の中で `?:` を使って 2 行に跨いで書いてはいけない** — 1 行目だけが消えて `?:` の行が孤立し、Kotlin の構文エラーでビルドが落ちる。解決は `buildTypes` の外の `val releaseSigningConfig` で行い、`release` の中は 1 行の代入にしておく。提出手順とその他の適合条件 (scanignore・NDK の渡し方) は `docs/FDROID.md`。
- **`useLegacyPackaging=true` 必須** (execve する .so を nativeLibraryDir に実体配置するため)。
- **`scripts/z2c` (OCI イメージ取得)**: OCI/Docker レジストリ (Docker Hub・ghcr.io 等) から arm64 のイメージを取得し、レイヤを whiteout (`.wh.*`) と opaque (`.wh..wh..opq`) の規約どおりに合成して rootfs を作る。認証トークンとマニフェスト取得は HTTPS GET のみで特権を要さず、レイヤは digest 単位で `~/.z2c/cache` に共有キャッシュする (別イメージが同じ下層を共有する)。サブコマンドは `pull` / `ls` / `verify` / `run` / `path` / `sh` / `rm`。`run` は `Z2ROOT_ROOTFS` を付けて exec することで rootfs を切り替えて起動する (= コンテナに入る。4 章「tracee ごとの rootfs」。**実際に通るのは 0.8.417 以降** — 0.8.416 の z2root はホスト実パスしか受け付けず、z2c が渡すゲスト視点のパスと噛み合わなかった)。イメージの `entrypoint`/`cmd`/`env` を尊重し、コマンドは rootfs 内の PATH から引く (外側の PATH で引くとホスト側のコマンドが起動してしまうため)。別アーキのイメージは `run` できない (z2root がエミュレータを挟まないため) ので `sh` を使う。**取得と展開までで、隔離起動は未対応** (10.1 の入れ子制約による)。`verify` は rootfs 内のローダー (`ld-musl-*` / `ld-linux-*`) を直接呼んで中のバイナリを実行し、合成結果が使える状態かを確かめる。`sh` も同じ経路なので **rootfs は切り替わらない** (絶対パスは外側を指す) — 中身の確認用と割り切る。 **別アーキのイメージも扱える** (`z2c pull --arch amd64 …`、保存名に `_amd64` が付く)。その場合 `verify`/`sh` はローダー直呼びではなくエミュレータ (`qemu-x86_64` 等。`qemu-user` パッケージ) を `-L <rootfs>` 付きで呼ぶ (`-L` が rootfs を sysroot にするのでローダーの明示は不要)。実測では native の約 11 倍遅い (busybox の算術ループ 2 万回で 0.10 秒 → 1.09 秒)。**実行時は `LD_PRELOAD` を 外す**必要がある — 理由は 4 章の z2accept の項。
- **オンデバイス (aarch64・proot/z2root 下) では `scripts/gw.sh` 経由でビルドする**: この環境は libc の `accept()` が ENOSYS を返し、JDK17 の `sun.nio.ch.Net.accept` が libc `accept()` を呼ぶため Gradle デーモンの TCP IPC が落ちて "Could not connect to the Gradle daemon" でビルド不能になる。`gw.sh` は **`accept()` が ENOSYS の環境でだけ** `accept4` シム (`scripts/accept4-shim.c`) を `LD_PRELOAD` して `./gradlew` を呼ぶ (PC など正常な環境では素通しなのでマルチデバイス運用を壊さない)。シムが aapt2 (bionic) に継承されると `libc.so.6 not found` で別の失敗になるため、aapt2 ラッパー側で `LD_PRELOAD` を外している。`bash scripts/gw.sh help` で適用の有無を確認できる。
- 展開後の初期設定 (`DistroInstaller.postInstallSetup`) を変えたら `DistroBundle.ROOTFS_VERSION` を +1 する (利用者は APK 入替で自動再展開)。
- **lint は警告 0 を維持する** (`bash scripts/gw.sh :app:lintDebug`、0.8.190 で達成)。CI の `Build & Lint` が落ちるとタグ push で走るリリースジョブが skip されるため、lint を通すことがリリースの前提になっている。黙らせ方は 3 段階に分ける: **恒常的に無意味な検査**だけ `app/build.gradle.kts` の `lint { disable }`、**特定の場所だけ外したいもの**は `app/lint.xml` の `<ignore path>`、**意図的な個別箇所**は現場に `@Suppress`/`@SuppressLint`/`tools:ignore` と理由コメント。一律に `disable` へ入れて他の場所の検出まで殺さない。

---

## 10. 既知の制約と設計上の罠

### 10.1 修正不能な制約

**PRoot のカーネル特権制約 (修正不能)**: root に見えても `ip`/`nmap -sS`/`ping`/特権ポート bind は不可。代替は `nmap -sT` 等。OpenSSH sshd も privsep 破綻のため dropbear を使う。

**z2root の入れ子起動が不可 (現状の実装制約)**: z2root の下でもう 1 つ z2root を起動すると、引数解析に到達する前に SIGSEGV で落ちる (rootfs 指定の有無・`-r /` でも同じ)。外側 z2root は execve をフックして自前ローダー経由で対象を map するが、z2root 自身は **static PIE** のためこの経路で展開できない。`Z2ROOT_NO_LOADER=1` は内側プロセスの挙動しか変えないので回避にならない。帰結として「シェルから別 rootfs のコンテナを起動する」形は取れない。複数 rootfs を同時に扱うなら、入れ子ではなく **1 つの z2root が tracee ごとにrootfs を持つ** 形にするしかない。**0.8.416 でそうした** — 下の 「tracee ごとの rootfs」を参照。

**コンテナのカーネル隔離は不可 (カーネル由来・修正不能)**: `unshare` は EINVAL (namespace 不可)、`/sys/fs/cgroup` は書き込み不可、overlayfs も netfilter も使えず `CapEff` は 0。したがってコンテナのデーモンは動かない。**一方でイメージの配布形式 (レジストリ API と tar.gz レイヤ) は特権を要さない**ので、取得・合成・rootfs 化までは `scripts/z2c` で成立する。得られるのは「環境の分離」であって「セキュリティ隔離」ではない (ptrace 方式のため tracee 側から迂回できる)。

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
- **テストに生の ESC (0x1b) を書かない** (0.8.354): `private val ESC = "\u001b"` のように**エスケープで書く**。生のバイトは編集画面で見えないので、**抜けても誰も気付かない**。実際 `SgrUnderlineAltScreenExitTest` の `ESC` は**空文字列のまま 0.8.139 から放置**されており、「代替画面を抜けたら下線が残らない」テストは**制御シーケンスを 1 つも流さずに通り続けていた**（0.8.354 の調査で発覚）。⚠ **通っているテストが何も検証していない**という壊れ方は、失敗するテストより見つけにくい。

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
