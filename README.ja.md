# Z2Term — Zero 2 Terminal

[English](README.md) ・ **日本語**

[![Release](https://img.shields.io/github/v/release/orgsonai/z2term?include_prereleases&label=release)](https://github.com/orgsonai/z2term/releases/latest)
[![License](https://img.shields.io/github/license/orgsonai/z2term)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84)

### Android のターミナルであり、Android を操作する手段でもあるアプリ。

**Z2Term** (ズィートゥーターム) は Alpine / Ubuntu / Arch / Kali を **root 不要**で動かし、
タブの中に **Linux のデスクトップ**を開き、シェルから端末そのものに手を伸ばせます —
通知を読む、通知シェードの返信欄であなたに質問する、充電・ネットワーク・受信 SMS の変化で
スクリプトを走らせる、といったことができます。

- **root も PC もセットアップスクリプトも不要。** APK を 1 つ入れれば `apk` / `apt` / `pacman` が使えるディストロがそのまま動きます。既定の実行エンジン (`z2root`) は本アプリ用に書いた ptrace ベースのユーザ空間実装です。
- **シェルから Android に話しかけられる。** 約 20 個の `z2-*` コマンド（通知・クリップボード・センサー・ライト・Intent・アラーム・自端末への無線 `adb`）に加え、端末のイベントでスクリプトを起動する自動化ハブ `z2-when` を内蔵。**アプリを開かなくても、再起動をまたいで動き続けます。**
- **日本語で使える。** かな漢字変換・予測・学習を備えた IME を内蔵し、**OS の入力メソッドとして有効化**して他アプリでも使えます。

> Zero to Ship プロジェクトの第5作目です。

## スクリーンショット

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/cui-terminal.png" width="280" alt="CUI: Alpine 端末と独自キーボード"><br><sub>CUI — Alpine 端末 + 独自キーボード</sub></td>
    <td align="center" width="50%"><img src="docs/images/gui-thunderbird.png" width="280" alt="GUI: Xvnc 上で動く Thunderbird"><br><sub>GUI — Xvnc 上で動く Thunderbird</sub></td>
  </tr>
</table>

## なぜもう一つターミナルアプリを作るのか

Android のターミナルには既に成熟した選択肢があり、**最大のパッケージ資産と実績**が欲しいなら
Termux が正解です。Z2Term は別の問いから作られています —
**ターミナルが「端末を操作する正面の手段」になり、しかも APK 1 つで完結したらどうなるか。**

| | Z2Term | Termux + アドオン群 |
|---|---|---|
| Linux ディストロ | z2root で Alpine / Ubuntu / Arch / Kali を導入可 | `proot-distro` パッケージで導入 |
| Linux GUI | **アプリ内に GUI タブ**（Xvnc + 内蔵 RFB クライアント）。音声・動画つき | 別途 X11 / VNC ビューアアプリ |
| シェルから Android を操作 | **内蔵**（約 20 個の `z2-*`） | 別アプリ（コンパニオン）が必要 |
| イベント駆動の自動化 | **内蔵**（`z2-when`: 充電・電池残量・時刻/cron・Wi-Fi・回線・起動・共有・SMS・センサー・通知・ファイル追加）。自動化タブ・実行ログ・一括停止つき | 別アプリ + 多くは市販の自動化アプリと併用 |
| 日本語入力 | **IME 内蔵**（変換・予測・学習）。OS の入力メソッドとしても選べる | OS のキーボード |
| SSH / SFTP クライアントと `sshd` | 内蔵。鍵は Android Keystore が保持 | 自分でパッケージを入れる |
| 配布 | GitHub Releases（本リポジトリ） | F-Droid と独自リポジトリ |

どちらも GPL-3.0 で、どちらもテレメトリを取りません。既に満足している Termux 環境がある人にとって、
Z2Term の存在理由は上の表の**2・3・4 行目**です。

## ダウンロード

**最新の APK は GitHub Releases から直接ダウンロードできます**（ビルド不要）:

- 最新版に飛ぶ: **<https://github.com/orgsonai/z2term/releases/latest>**

各リリースには APK が 2 つ付きます。

| ファイル | 中身 | 選び方 |
|---|---|---|
| `app-foss-release.apk` | z2root 単独版 | **おすすめ。** 初回は ⚙設定 › Linux環境 から使いたい OS を選んで取得します。 |
| `app-full-release.apk` | foss と同じ payload | 既存の full 利用者が同じ package ID のまま更新するために残しています。 |

0.8.328 以降は両方ともコード・native library・assets・機能が同じです。違いは package ID と版名の
`-foss` 接尾辞だけです。OS はどちらも初回に公式配布元から取得します。

Android 端末で APK をタップ → 「提供元不明のアプリ」のインストールを許可するとインストールできます。
（Google Play では配布していません）

### アップデートの方法

合うものを選んでください。

- **アプリ内で確認** — *設定 → アプリ情報 → 更新を確認*。**ボタンを押したときだけ** GitHub の最新版に
  問い合わせます（自動チェックは一切せず、押すまでネットワークにも触れません）。新版があれば版数を表示し、
  リリースページを開きます。APK のダウンロードとインストールは手動のままです。
- **手動** — Releases から新しい APK を落としてタップ（上書きインストールされ、データは残ります）。
- **自動** — [Obtainium](https://github.com/ImranR98/Obtainium) に
  `https://github.com/orgsonai/z2term` を登録します。Releases を監視し、新版が出ると**ワンタップで更新**（ストア不要）。
  おすすめの `foss` を選んでいれば、その更新は毎回わずか約 21MB です。

## 現在のバージョン

**0.8.332-alpha (versionCode 340).** 最新の APK と全リリース履歴は **[GitHub Releases](https://github.com/orgsonai/z2term/releases)** にあります。

## 機能

- **ターミナルエミュレータ** — VT100 / xterm、256色・トゥルーカラー、9 テーマ、検索付きスクロールバック、UTF-8 と East Asian Width、代替スクリーン、OSC 4 / 7 / 8 / 10 / 11 / 12 / 52。
- **root 不要の Linux ディストロ** — ユーザ空間エンジン（既定は z2root。下の「実行エンジン」参照）で Alpine / Ubuntu / Arch / Kali を動かし、`apk` / `apt` / `pacman` で何でも導入。
- **実行エンジン** — 非 root は z2root に完全移行。root 端末では裏機能の chroot も選択可能。
- **マルチタブ** — CUI / GUI タブ、ドラッグで並べ替え、タブ長押しで実行エンジンを確認。**見ていないタブで何か動いていれば小さな点**、**見ていない間に終わっていれば ✓** が付く。
- **Linux GUI** — Xvnc + openbox と内蔵 RFB クライアント。`z2gui` でデスクトップを起動し、`z2run <アプリ>` で GUI アプリを起動（GUI タブも自動で開く）。音声・動画つき。
- **SSH / SFTP** — 公開鍵認証（**アプリ内で ed25519 鍵を作れて、公開鍵はその場でコピー/共有/この端末の sshd に登録**。秘匿フィールドは Android Keystore で暗号化）、known_hosts 確認、ファイル転送、**両方向のポート転送 (`-L` / `-R`) と、SSH タブを閉じても生き続ける常駐トンネル**、既定で localhost のみ bind する内蔵 `sshd`（dropbear）。
- **日英どちらでも** — アプリの画面だけでなく **`z2-*` コマンドの表示も言語設定に追従**します（ヘルプ・usage・メッセージまで）。
- **日本語 IME** — Viterbi かな漢字変換、予測、頻度/新しさ学習、独自オンスクリーンキーボード。**OS の入力方法としても出せる**ので、有効にすればアプリ内の入力欄でも他アプリでも同じキーボード・同じ変換で打てます（切り替えは OS のキーボード切替）。**自分の語をファイルで足せます**（SKK 形式の `よみ /候補/`）ので、人名や自分だけの略語も最初から変換に出せます。
- **Android ブリッジ** — 端末から本体機能を呼ぶ: `z2-noti`（いま出ている通知を読む・読むだけ）/ `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip` / `z2-battery` / `z2-vibrate` / `z2-say` / `z2-torch` / `z2-media` / `z2-volume` / `z2-sensor` / `z2-intent` / `z2-state` / `z2-screen`（その時間だけ画面が自分で消えないようにする）/ `z2-tile`（クイック設定タイルにマクロを載せる・12 枠）/ `z2-icon`（ステータスバーとタイルのアイコンをドット絵で描き替える）/ `z2-alarm` / `z2-macro` / `z2-session`（アプリ自身のタブを操る）/ `z2-server`（登録済みの常駐サーバーを起こす・落とす）。
- **人に聞ける** — `name=$(z2-ask "ブランチ名は?")` で**通知の返信欄**から答えを受け取れる（アプリを開かずシェードのまま。答えなければ非ゼロ終了なので「諦める」も書ける）。
- **自動化ハブ** — `z2-when <トリガー> run <コマンド>` で、Android 側の出来事をきっかけにスクリプトを自動実行: 充電の開始/停止、電池が一定値を跨いだとき、時刻（毎日 / 1 回 / N ごと / cron）、Wi‑Fi の接続/切断、**回線が通じた/途切れた・使う回線が切り替わった（`net:online` / `net:mobile` — モバイル回線も見る）**、**端末の起動（`boot`）**、**他アプリから共有されたとき（`share:ext=pdf` など）**、SMS 受信（OTP コード抽出つき）、センサー（振る / 照度しきい値 / 近接）、**通知が届いたとき（`notify:otp` は OTP コード抽出つき。ログ保存とは独立）**、**端末イベントを名前で指定（`event:headset_plugged` など約20種。`z2-when events` で一覧）**、**フォルダに新しいファイルが来たとき（`file:new=…`）**。**条件で絞り込める**（`if=ssid=Home` / `cooldown=1h` / `between=22:00-07:00` / `days=mon-fri` — どのトリガーにも同じように効き、見送った分も `skip:` として記録に残る）。ルールは `~/.z2term/when/` 配下のテキスト（git 同期可）で、アプリを開かなくても・再起動をまたいでも働く。**📜 の「自動化」タブ**で一覧・ON/OFF・実行ログ・きっかけを待たない「▶ いま試す」ができ、**全ルールの一時停止（キルスイッチ）と直近の発火**もここで見られる（端末からは `z2-when pause` / `resume` / `fired`）。
- **常駐サーバー** — 任意の起動コマンドを *設定 → 常駐サーバー*に登録すると、**アプリを開かなくても**バックグラウンドで動き続ける。小さな Web サーバー・同期デーモン・bot をスマホで常駐させられる。稼働数はホーム画面の状態ウィジェットに出て、登録したサーバーは端末の起動時に自動で復帰する。
- **セルフ adb** — `z2adb` で端末自身のワイヤレスデバッグへ localhost 接続。PC・USB・root すべて不要。
- **内蔵ヘルプ** — `z2help`（または `z2term`）で全 `z2*` ヘルパーの分類済み早見表を表示。各コマンドは `--help` で詳しい説明を自分で出します（例: `z2-tile --help`）。`z2version` でアプリ版数とタブが実際に動いているエンジンを確認。
- **`z2doctor`** — 「動きません」を 1 コマンドで切り分ける自己診断。版数・実行エンジン・空き容量・必要な許可・検知と自動化の状態を一覧し、**`NG` の行には必ず次の一手**が付きます。最後に**そのまま貼れる報告文**（`--share` / `--clip`）。SSID・IP・ホスト名は意図的に伏せます。
- **脆弱性試験** — `z2scan self` が自端末/localhost を自己診断（公開ポート・sshd 設定・SSH 鍵の権限・world-writable/SUID・PATH）。外部ツール不要。`z2scan net/host/cve` は localhost に nmap/lynis/trivy をかける薄いラッパー（外部対象は明示許可制）。結果はローカルに留まります。
- **端末ログ** — ツールバーの ⏺ を 1 回押すとそのタブの表示内容をテキストファイルに記録し、もう 1 回で停止。保存先は `~/z2term-log/` なので端末からも他アプリからもそのまま開ける。既定は色や画面制御を落とした読めるテキスト。
- **ホーム画面ウィジェット 2 種** — *状態＋ランチャー*: 状態（ssh の接続先 / 常駐サーバー / 自動化ルール / 電池）を「稼働数 / 登録数」で出し、選んだマクロをタップで**アプリを開かずバックグラウンド実行**（実行中のマクロはもう一度タップで停止）。*ライブ tail*: `~` の下のファイルの末尾または先頭をホーム画面に出し続けます（`tail` / `head` を選べます）。
- **持ち出し / 引き継ぎ** — 設定・SSH 接続先・スニペット・自動化ルール・マクロを 1 ファイルにまとめて別の端末へ。OS 本体は含めません。**SSH の秘密は既定で含めず、含めるときは合言葉が必須**です。
- **はじめの 3 枚** — 初回だけ小さなカードが 3 枚出ます（通知を出す／ライトを点ける／PC からつなぐ）。タップすると**入力欄に入るだけで、勝手には実行しません**。触ったら消え、二度と出ません。
- **共有からの受け取り** — 他アプリの「共有」で z2term を選ぶと、テキストはそのまま、ファイルは `~/z2term-inbox/` に取り込んでパスを、端末の入力行に**入れるだけ**（実行はしない）。
- **ツールバーの整理** — 出すボタンを設定で選べる（⚙ 設定は常に右端固定）。長押しドラッグで並べ替え。
- **FOSS フレーバー（おすすめの配布物）** — 第三者 prebuilt を一切同梱せず（約 21MB）、初回起動時にディストロを DL して SHA-256 で検証。

### 未対応 / 今後の検討

- mosh プロトコル対応 (UDP ベース)
- リバース DNS / IPv6 接続のリトライ強化
- z2root自前実装に完全移行し、第三者native prebuiltを同梱しない
- IME 学習履歴のエクスポート / バックアップ（リセット UI は実装済み）

## ビルド要件

| 項目 | バージョン |
|---|---|
| Android Studio | Ladybug 2024.3.1 以上 |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 (AGP 内蔵) |
| Gradle | 9.3.1 |
| NDK | 27.0+ |
| CMake | 3.22.1+ |
| 最小 SDK | 29 (Android 10) |
| ターゲット SDK | 35 (Android 15) |

## セットアップ

### 1. 同梱物を 1 コマンドで揃える

APK に同梱されるが **git 管理外**の生成物がいくつかあります（`scripts/` が生成・取得）。
clone や clean の直後には存在しないので、マスタースクリプトでまとめて揃えます。集め方をこれ 1 本に
統一してあるので、PC でもスマホでも同じ同梱物セットになります（環境ごとに別物の APK ができる事故を防ぐため）。

```bash
bash scripts/build-bundle.sh
```

2 つの生成スクリプトを順に実行し、最後に欠落がないか点検します。

1. `build-z2root.sh` → `libz2root.so` / `libz2accept.so`（NDK が必要）
2. `fetch-fonts.sh` → `IBMPlexMono` / `JetBrainsMono` / `FiraCode` の `-Regular.ttf`

最後の点検で共通同梱物の `OK` / `MISS` を表示します。rootfsは両フレーバーとも実行時取得です。

同梱物ごとの詳細: [app/src/main/assets/README.md](app/src/main/assets/README.md) · [app/src/main/jniLibs/README.md](app/src/main/jniLibs/README.md)

### 2. ビルド

```bash
./gradlew assembleFossRelease
# 出力: app/build/outputs/apk/foss/release/app-foss-release.apk

./gradlew assembleFullRelease
# 出力: app/build/outputs/apk/full/release/app-full-release.apk
```

(fork 側で署名鍵が無くても OK — `build.gradle.kts` は `keystore.properties` 不在時 debug 鍵にフォールバックします)

### 3. インストール

```bash
adb install -r app/build/outputs/apk/foss/release/app-foss-release.apk
```

## プロジェクト構造

```
z2term/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                  ← Alpine rootfs を配置
│       ├── cpp/                     ← JNI ネイティブコード
│       │   ├── CMakeLists.txt
│       │   └── pty_jni.cpp
│       ├── java/com/zerotoship/z2term/
│       │   ├── Z2TermApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── channel/             ← ProcessChannel / SshChannel (M5)
│       │   ├── core/                ← TerminalSession + SessionManager
│       │   ├── pty/                 ← PTY 抽象化
│       │   ├── proot/               ← Linux起動（legacy package名）
│       │   ├── distro/              ← rootfs 展開 (Alpine + Ubuntu)
│       │   ├── emulator/            ← VT100/xterm エミュレータコア
│       │   ├── settings/            ← DataStore 永続化
│       │   ├── service/             ← TerminalService / AudioBridge (foreground + WakeLock)
│       │   ├── gui/                  ← GUI (Xvnc + 内蔵 RFB クライアント / GuiSession)
│       │   ├── saf/                  ← SAF DocumentsProvider
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme + カスタムフォント
│       │       ├── settings/        ← 設定 UI
│       │       ├── ssh/             ← SSH プロファイル UI (M5)
│       │       └── terminal/        ← ターミナル UI + Renderer + キーマッパー
│       ├── jniLibs/                 ← z2root生成物
│       └── res/                     ← リソース
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── ja/                        ← 日本語ドキュメント
│   │   ├── DESIGN-SPEC.md         ← 設計書 兼 仕様書（技術文書）
│   │   └── HANDBOOK.md            ← 利用者向けハンドブック
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   └── HANDBOOK.md            ← getting started handbook
│   ├── images/                    ← スクリーンショット等（共通）
│   ├── RELEASE.md                 ← リリース手順
│   └── SSH-INTO-Z2TERM.md
├── metadata/                     ← F-Droid メタデータ
└── .github/workflows/build.yml   ← CI (full + foss 両ビルド)
```

## ビルドバリアント

| Flavor | 用途 | 同梱内容 |
|---|---|---|
| `foss` | **配布の既定（おすすめ）** | z2root。rootfs は初回取得 |
| `full` | 既存 full 利用者の更新互換 | foss と同じ payload。rootfs は初回取得 |

`applicationId` は `foss` だけ `.foss` が付く（`com.zerotoship.z2term.foss`）ので、両方を同時に入れられます。
⚠ **ランチャーの表示名はどちらも「Z2Term」**です（0.8.315。名前に配布形態を出さない方針）。両方入れたときは
名前で見分けが付かないので、`z2version` かアプリ情報の版数（`foss` は末尾が `-foss`）で判別してください。
debug ビルドだけは別名（`Z2Term dbg2`）のままです。

```bash
./gradlew assembleFossDebug
./gradlew assembleFullDebug   # applicationId以外は同じpayload
```

## 動作確認の流れ

1. どちらかのフレーバーをビルド・インストールし、OSを選んで初回取得を完了する。
2. `z2version` が `engine : z2root` を示すことと、各パッケージマネージャーの動作を確認する。

### z2root コマンド群テスト（`scripts/z2root-cmdtest.sh`）

「今後も *壊れやすいコマンド* がエラーなく動く」ことを確認する回帰スモーク。
z2root の難所（ptrace/seccomp・fakeroot 偽装・パス変換・/proc 偽装・pty・大量
fork/exec・ld.so reloc）を踏むコマンドに絞り、cd/ls のような自明系は入れない。
狙いは「systemic な退行を *多数のコマンドが一斉に落ちる* 形で一発検知し、コマンド
ごとの後追い修正をやらないで済む」こと。z2root タブのゲスト内でそのまま実行:

```sh
sh scripts/z2root-cmdtest.sh              # 標準（ネット/ビルド込み）
SKIP_NET=1   sh scripts/z2root-cmdtest.sh # ネット/パッケージ系をスキップ
SKIP_BUILD=1 sh scripts/z2root-cmdtest.sh # cc コンパイル等をスキップ
RUN_SSHD=1   sh scripts/z2root-cmdtest.sh # dropbear ループバック ssh（z2root 単独だとセッションが落ちる可能性）
RUN_PRIV=1   sh scripts/z2root-cmdtest.sh # losetup/mount など真に root が要る操作も実行（非 root では EPERM が正常）
```

POSIX sh／busybox ash 互換で、**未導入コマンドは fail でなく skip** するので
どのディストリでも同じに走る＝各ゲストで回して「非ゼロ終了一覧が空」になれば
OS 差なく健全、と読める。10 グループ: ①ランタイム実起動（claude headless と
`--version` の対比・node spawn・python venv/mp/ssl・ripgrep）②VCS 重い操作
（clone/gc/checkout＝hardlink/pack/rename）③パッケージ管理（apt/apk/dnf/pacman・
pip/venv・npm）④pty/端末（script/tmux/stty・`/dev/pts`・任意で dropbear）
⑤/proc・fakeroot 境界 ⑥ビルド（cc execve chain＋ld.so reloc）⑦パス変換/symlink
canonicalize ⑧ディスク/FS（dd・mkfs・parted をファイル相手に。root 系は
`RUN_PRIV`）⑨IPC/特殊 syscall（AF_UNIX・FIFO・flock・inotify・xattr・
copy_file_range・nested ptrace(strace/gdb)・Go 生 syscall・sqlite3・rsync）
⑩名前解決/TLS（getent・curl TLS・nslookup）。出力は画面と
`/tmp/z2root-cmdtest-<時刻>.log`、末尾に非ゼロ終了一覧。

注: `io_uring`（ptrace/seccomp を丸ごとバイパス）や `statx`/`openat2` のフック漏れ
はコマンドテストでは捕まらない＝seccomp フィルタ側で確認すること。

## ライセンス

本アプリ本体 (`app/src/main/java/com/zerotoship/z2term/**`) のライセンスは **GPL-3.0** です。
Copyright (c) 2026 Zero to Ship。対応ソース（GPL v3 §6）: <https://github.com/orgsonai/z2term>（ルートの `LICENSE` に全文）。
同梱する第三者コンポーネントは設定画面の「OSSライセンス」で確認できます。

## 同梱 OSS と対応ソース（GPL/LGPL 頒布要件）

| 同梱物 | ライセンス | 対応ソース取得方法 |
|---|---|---|
| Fira Code / IBM Plex Mono / JetBrains Mono | OFL-1.1 | [tonsky/FiraCode](https://github.com/tonsky/FiraCode) / [IBM/plex](https://github.com/IBM/plex) / [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |

設定画面 →「OSS ライセンス / 対応ソース」から、上記情報をアプリ内でも一覧/全文表示できます
（`assets/licenses/` にライセンス全文を配置）。

## 配布方針

| チャネル | フレーバー | 状況 |
|---|---|---|
| **GitHub Releases / 直接 APK 配布** | `foss`（**おすすめ**）/ `full` | 主たる配布経路。fullはAlpine rootfs同梱、fossは初回取得 |
| **F-Droid** | `foss` | rootfs実行時取得・エンジンはソースビルド |
| **Google Play** | — | 配布予定なし |

## SSH サーバ (sshd) の既定挙動

端末内 `sshd` コマンドは既定で **127.0.0.1 限定 bind + 鍵認証のみ** で起動します
（dropbear wrapper、`SshdScript.kt`）。LAN/WAN 公開する場合は明示的に:

```sh
sshd --lan          # 全 NIC bind、~/.ssh/authorized_keys が空だと起動拒否
Z2_SSHD_LAN=1 sshd  # env でも可
```

## 関連

- [Zero to Ship Project](https://github.com/orgsonai)
- [Termux](https://github.com/termux/termux-app) - 参考実装
- z2root — 本リポジトリでソースビルドするユーザー空間Linuxエンジン
- [Alpine Linux](https://alpinelinux.org/) - メインディストロ
