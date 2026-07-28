# z2term マクロガイド（z2term-macro）

**スマホの操作を自動化する「マクロ」を、z2term のターミナルだけで組むための仕様書。**
人間が読んでそのまま書ける説明書であると同時に、**AI に丸ごと読ませて「〜したい」と頼めば
マクロ用のスクリプトを生成できる**ように、機械可読なリファレンスを兼ねています。

> 対象バージョン: 0.8.247-alpha 以降 / 英語版: `docs/en/MACRO-GUIDE.md`
> すべて **非 root・完全ローカル・外部送信なし**。難しい権限が要る機能は含みません。

---

## 1. 考え方（3 段構成）

z2term のマクロは MacroDroid 等と同じ「**トリガー → 判断 → アクション**」で組み立てます。

| 段 | 誰が | z2term では |
|---|---|---|
| **トリガー** | Android → シェル | **`z2-when` にきっかけを登録する**（充電/電池/時刻/Wi‑Fi/SMS/センサー/新しいファイル/通知/端末イベント）。条件が合ったときだけスクリプトが 1 回走ります。同じ出来事は `~/.z2term/events.jsonl` にも追記されるので、**自分でログを監視して拾うこともできます**（通知は `~/.z2term/notifications.jsonl`、SMS は `~/.z2term/sms.jsonl`。OTP は伏せ字を迂回できる SMS 検知が確実。5-7 参照） |
| **判断（ロジック）** | シェル | 条件分岐（`if`・時刻・回数・状態ファイル…）。ふつうの sh/awk/jq で自由に。**今の状態**は `z2-state` で聞ける |
| **アクション** | シェル → Android | `z2-*` コマンドで Android 側を操作（通知/読み上げ/音量/ライト/Intent 発火…） |

### トリガーの受け取り方は 2 通り

| | 使う場面 | 書くもの |
|---|---|---|
| **A. `z2-when` に任せる**（既定。0.8.205 以降） | 登録できるきっかけで足りるとき | **やることだけ**を書いた短いスクリプト（1 回走って終わる） |
| **B. 自分でログを監視する**（5-0 の雛形） | `z2-when` に無いきっかけ／複数の出来事を組み合わせて判断したいとき | ログの差分を見続ける常駐スクリプト |

**まず A を試してください。** アプリ側が待ち受けるので、**常駐スクリプトを 1 本も動かさずに済み**、
電池も食いません。B は「A で表現できないとき」の逃げ道です（このガイドの 5-5 / 5-6 のように、
ログの中身まで見て判断する例がそれにあたります）。

**マクロの実体＝「きっかけが来たときに、条件を見てアクションを叩くシェルスクリプト」**です。

---

## 2. 使う前の準備（1 回だけ）

1. **トリガーを有効化**: アプリの ⚙設定 → 「**常駐サーバー・自動化**」 →
   - 「**システムイベント検知**」を ON。`z2-when` の `charge:` / `battery:` / `wifi:` /
     `sensor:` / `file:` / `event:` と、`events.jsonl` への記録はこれが前提です。
   - 通知も使うなら「**通知検知**」を ON（＋OS の「通知アクセス」を許可）。`notify:` の前提です。
   - SMS を使うなら「**SMS 検知**」を ON（＋SMS 受信の許可）。`sms:` の前提です。
   - **`time:` 系だけは検知 OFF でも動きます**（OS のアラームで起こしてもらうため）。
2. **きっかけを登録する**: `z2-when <トリガー> run <コマンド>`（→ 4 章）。登録したものは
   📜 → 「**自動化**」タブにも並び、ON/OFF・**▶ できっかけを待たずに 1 回試す**・実行ログ・
   一時停止・直近の発火がそこから見られます。
3. **常駐させたいとき**（1 章 B の書き方をするとき）: ⚙設定 → 「**常駐サーバー**」に起動コマンドを登録すると、
   アプリを開かなくても・端末再起動後も動きます（「起動時に自動で常駐」も ON に）。
4. 便利ツール: `jq`（JSON 解析）を入れておくと楽。例: Alpine `apk add jq` / Debian系 `apt install jq`。
5. **白紙から書きたくないとき**: `z2-macro list` で同梱サンプル 7 本を見て、`z2-macro install <名前>` で
   `~/.z2term/macros/` に置けます（`z2-macro install all` で全部）。中身は自由に書き換えて構いません。
   既にあるファイルは上書きしないので、編集したものが消えることはありません（`-f` で明示的に上書き）。
   導入すると**そのスクリプトの動かし方**（常駐サーバーに登録する／`z2-when` で回す／ウィジェットの
   ボタンに割り当てる）が併せて表示されます。

---

## 3. トリガー・リファレンス

### 3-A. `z2-when` に登録できるきっかけ

`z2-when <トリガー> run <コマンド>` で登録します。条件が合うと**コマンドが 1 回だけ**走ります
（常駐スクリプトは要りません）。

| トリガー | いつ | 前提 |
|---|---|---|
| `charge:start` / `charge:stop` | 充電の 開始 / 停止 | 検知 ON |
| `battery:below=N` / `battery:above=N` | 残量が N% を **下/上へ跨いだ瞬間** | 検知 ON |
| `time:daily=HH:MM` | 毎日 HH:MM | — |
| `time:at=HH:MM` | 次の HH:MM に 1 回 | — |
| `time:every=Nm` / `time:every=Nh` | N 分 / N 時間ごと | — |
| `time:cron='分 時 日 月 曜日'` | cron 式（曜日 0-7 / 0,7 = 日曜）。空白を含むので**要クォート** | — |
| `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<名前>` | Wi‑Fi の 接続 / 切断 / 指定 SSID への接続 | 検知 ON |
| `net:online` / `net:offline` | 通信できる回線が できた / 無くなった（モバイル回線も見る） | 検知 ON |
| `net:wifi` / `net:mobile` / `net:ethernet` | 使う回線が**それへ切り替わった** | 検知 ON |
| `boot` | 端末の起動が終わった（`:` は付けない） | — |
| `sms:any` / `sms:from=<部分>` / `sms:contains=<部分>` / `sms:otp` | SMS が届いた | SMS 検知 |
| `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near\|far` | 振った / 照度が N lux を跨いだ / 近接が変化 | 検知 ON |
| `file:new=<フォルダ>[,ext=<拡張子>]` | そのフォルダに**新しいファイルが来た**（書き込みが終わってから） | 検知 ON |
| `notify:any` / `notify:otp` / `notify:pkg=<部分>` / `notify:title=<部分>` / `notify:contains=<部分>` | 通知が届いた（`pkg=` はパッケージ名でもアプリ表示名でも当たる） | 通知アクセス |
| `event:<名前>` / `event:<接頭辞>*` / `event:*` | 端末イベントを**名前で**拾う（名前は 3-B の表と同じ。`z2-when events` で一覧） | 名前による |

発火したコマンドには、**何が起きたか**が環境変数で渡ります。

| 変数 | 入るもの |
|---|---|
| `Z2_WHEN_TRIGGER` | 登録したトリガー文字列（全トリガー共通） |
| `Z2_WHEN_LEVEL` | 電池残量 %（`charge:` / `battery:`） |
| `Z2_WHEN_SSID` | Wi‑Fi 名（`wifi:`） |
| `Z2_WHEN_NET` / `Z2_WHEN_NET_PREV` | 今の回線 / 直前の回線（`net:`。`wifi` `mobile` `ethernet` `vpn` `other` `none`） |
| `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY` | 送信元 / 本文（`sms:`） |
| `Z2_WHEN_OTP` | 抽出したワンタイムコード（`sms:otp` / `notify:otp`） |
| `Z2_WHEN_SENSOR` / `Z2_WHEN_LUX` | `shake`/`light`/`proximity:near\|far` / 照度（`sensor:`） |
| `Z2_WHEN_FILE` / `Z2_WHEN_DIR` | 増えたファイルのフルパス / そのフォルダ（`file:`） |
| `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT` | 通知のパッケージ名 / アプリ名 / 題名 / 本文（`notify:`） |
| `Z2_WHEN_EVENT` | イベント名（`event:`） |
| `Z2_WHEN_EVENT_NAME` | 仕掛けたときに付けた識別名（`event:alarm` / `event:notify_action`） |
| `Z2_WHEN_ACTION` | 押されたボタンのラベル（`event:notify_action`） |

```sh
z2-when charge:start run ~/.z2term/macros/backup.sh
z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
z2-when event:headset_plugged run ~/.z2term/macros/play.sh
z2-when 'event:ringer_*' run 'z2-toast "マナーモード: $Z2_WHEN_EVENT"'
z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
```

**条件で絞る**（0.8.263。きっかけの直後・`run` より前に置きます。どのきっかけにも同じように効きます）:

| 書き方 | 意味 |
|---|---|
| `if=wifi,!screen` | そのときの端末の状態で絞る（カンマは「かつ」・`!` は否定）。条件は `z2-state` で見られる項目（`wifi` `charging` `screen` `locked` `headset` `airplane` / `ssid=Home` `ringer=silent` / `level<30` `temp>40`）|
| `cooldown=1h` | 前に実行してからこの時間は動かさない（`30s` / `10m` / `2h`・単位省略で分）|
| `between=22:00-07:00` | その時間帯だけ（日をまたいでも書けます）|
| `days=mon-fri` | その曜日だけ（`sat,sun` や cron と同じ数字 `1-5` も可）|

```sh
# 自宅の Wi-Fi で充電を始めたときだけ、1 時間に 1 回まで
z2-when charge:start if=ssid=Home cooldown=1h run ~/.z2term/macros/backup.sh
# 平日の夜、画面が消えているときだけ
z2-when time:every=30m if=!screen between=22:00-07:00 days=mon-fri run ~/.z2term/macros/nightly.sh
```

絞り込みで見送ったものも `z2-when fired` に `skip:if` / `skip:cooldown` / `skip:between` / `skip:days` として残るので、**動かない理由が追えます**。スクリプトの先頭に自分で `z2-state` を見る `if` を書くのに比べて、こちらは「弾かれた」と「実行したが何もしなかった」を**記録の上で区別できる**のが違いです。

- 外から来た文字列（SSID・SMS 本文・通知本文・ファイル名）は**安全にクォートして渡されます**。
  受け取る側でも `"$Z2_WHEN_SMS_BODY"` のように必ず引用符で囲み、`eval` に渡さないでください。
- **同じルールは 10 秒以内に続けて発火しません**（`screen_on` のように数の多いイベント対策）。
- コマンドは**選択中の distro** で走ります。

### 3-B. イベントログ（events.jsonl）

自分でログを監視する書き方（1 章 B / 5-0 の雛形）を選んだときに読む先です。
`z2-when` の `event:` が拾う名前もこの表と同じです。

- 場所: `~/.z2term/events.jsonl`（1 行 = 1 イベントの JSON。追記のみ）。
- **大きさの上限はありません**（1 本に全履歴を追記し続けます）。過去に遡って集計したいとき 1 ファイルで済むようにするためです。
  容量が気になったら、ターミナルで自分で切り詰めてください（例: `: > ~/.z2term/events.jsonl`）。なお「新しいものを先頭に」モードは 1 件ごとにファイル全体を書き直すので、大量常用では既定の末尾追記が軽いです。先頭追記のままログが 10MB を超えると、設定画面にサイズと対処の注意が出ます。
- 既定フィールド: `ts`（エポック ミリ秒・整数）, `time`（ISO8601 文字列）, `event`（種別）, 一部で `level`（電池残量 %）, `ssid`（Wi‑Fi 名）。
- 出力形式は設定でテンプレート化できますが、**マクロ用途では既定の JSONL のまま**が扱いやすいです。

#### イベント種別（`event` の値）

| event | 意味 | 追加フィールド |
|---|---|---|
| `screen_on` / `screen_off` | 画面 点灯 / 消灯 | — |
| `unlocked` | ロック解除（本人確認後） | — |
| `power_connected` / `power_disconnected` | 充電 開始 / 停止 | `level` |
| `battery_low` / `battery_okay` | 電池残量 低下 / 回復 | `level` |
| `battery_level` | 残量が 10% 刻みの境界を跨いだとき | `level` |
| `wifi_connected` / `wifi_disconnected` | Wi‑Fi 接続 / 切断 | `ssid`（位置情報権限が無いと空） |
| `net_online` / `net_offline` | 通信できる回線が できた / 無くなった | — |
| `net_wifi` / `net_mobile` / `net_ethernet` | 使う回線が切り替わった | — |
| `boot` | 端末が起動した（検知 OFF でも出る） | — |
| `headset_plugged` / `headset_unplugged` | 有線ヘッドセット 抜き差し | — |
| `bt_audio_connected` / `bt_audio_disconnected` | **Bluetooth オーディオ**（イヤホン等）の 接続 / 切断 | — |
| `airplane_on` / `airplane_off` | 機内モード ON / OFF | — |
| `ringer_normal` / `ringer_vibrate` / `ringer_silent` | マナーモード 切替 | — |
| `alarm` | **`z2-alarm` で仕掛けた時刻**になった | `name`（`z2-alarm` に付けた名前） |
| `notify_action` | **`z2-notify -b` で付けたボタンが押された** | `name`（通知に付けた名前）, `action`（押されたラベル） |
| `unlock_failed` / `unlock_succeeded` | ロック画面の解除に**失敗 / 成功**（盗難対策向け。⚙設定「ロック解除の失敗監視」ON ＋ 端末管理者の有効化が必要）。**PIN・パターン・パスワードのみ**で、指紋/顔認証では発火しない（→ 解除の合図には `unlocked` を使う） | `unlock_failed` に `level`（連続失敗回数） |

行の例:
```json
{"ts":1752620719000,"time":"2026-07-16T10:25:19+09:00","event":"power_connected","level":42}
{"ts":1752620750000,"time":"2026-07-16T10:26:00+09:00","event":"wifi_connected","ssid":"home"}
```

#### 通知ログ（notifications.jsonl）

- 場所: `~/.z2term/notifications.jsonl`。フィールド: `ts` `time` `pkg`（パッケージ名）`app`（アプリ名）`title` `text` `category` `key`。
- 例: 「特定アプリの通知が来たら…」の起点に使えます（`z2-when notify:pkg=<部分>` なら記録なしでも同じことができます）。
- 記録の ON/OFF は `z2-when notify:` と**独立**です。「記録はしないが、きっかけには使いたい」がそのまま書けます。

---

## 4. アクション・リファレンス（z2-* コマンド）

端末から叩くと、アプリが Android 側を代行します。**すべて権限不要**
（`z2-intent` の呼び先が求める権限と、`z2-when` のトリガーごとの前提 → 3-A は別です）。

| コマンド | 使い方 | 動作 | 戻り値 |
|---|---|---|---|
| `z2-notify` | `z2-notify [-h] [-n 名前] [-b ラベル]... "タイトル" "本文"` | 通知を出す（`-h` でバナー表示、`-b` で**返事のボタン**を付ける） | — |
| `z2-toast` | `z2-toast "メッセージ"` | 画面下に短いメッセージ | — |
| `z2-say` | `z2-say "読み上げる文"`（引数なしで標準入力） | TTS で読み上げ | — |
| `z2-torch` | `z2-torch on\|off\|toggle`（既定 toggle） | フラッシュライト | `on`/`off` |
| `z2-vibrate` | `z2-vibrate [ミリ秒]`（既定 200） | バイブ | — |
| `z2-media` | `z2-media playpause\|play\|pause\|next\|previous\|stop`（既定 playpause） | メディア再生キー送出 | — |
| `z2-volume` | `z2-volume up\|down\|mute\|unmute\|N\|N%` | メディア音量 | `current/max` |
| `z2-sensor` | `z2-sensor light\|accel\|proximity` | センサーを 1 回読む | light`{"lux":F}` / proximity`{"distance":F}` / accel`{"x":F,"y":F,"z":F}` |
| `z2-clip` | `z2-clip get` / `z2-clip set [文字]` | クリップボード取得/設定 | get 時に内容 |
| `z2-battery` | `z2-battery` | 電池状態 | `{"level":N,"charging":bool}` |
| `z2-share` | `z2-share "テキスト"` | 共有メニューへ | — |
| `z2-open` | `z2-open <URL\|パス>` | 既定アプリで開く | — |
| `z2-intent` | 下記参照 | 任意の Intent を発火 | — |
| `z2-state` | `z2-state [キー]` | **今の端末の状態**を返す（下記） | JSON / キー指定ならその値 |
| `z2-screen` | `z2-screen keepon 1h` / `keepon off` / `status` | **画面の自動消灯を期限つきで止める**（下記） | 状態 JSON |
| `z2-tile` | `z2-tile set <1-4> <マクロ\|コマンド>` ほか | **クイック設定タイル**に割り当てる（下記） | 4 枠の TSV |
| `z2-alarm` | `z2-alarm at\|daily HH:MM [名前]` ほか | **時刻トリガー**を仕掛ける（下記） | 予約内容 JSON |
| `z2-when` | `z2-when <トリガー> run <コマンド>` ほか | **きっかけを登録**する（→ 3-A・下記） | 登録した id |
| `z2-noti` | `z2-noti list` | **いま出ている通知**を読む（読むだけ・下記） | TSV |
| `z2-session` | `z2-session list\|new\|send\|capture\|close` | **アプリ自身のタブ**を操る（下記） | TSV / 番号 |
| `z2-macro` | `z2-macro list\|install\|show\|run\|dir` | 同梱サンプルの管理 | — |

### `z2-notify -b`（返事を受け取る＝対話型マクロ）

`-b ラベル` を付けると通知にボタンが出ます。押すと `events.jsonl` に

```json
{"event":"notify_action","name":"confirm","action":"はい"}
```

が 1 行増えるので、**マクロが問いかけて、あなたがボタンで答え、続きが動く**という組み方ができます。
ボタンは最大 3 つ（Android の表示上限）。`-n 名前` はどの問いかけへの返事かを区別するための名前です。

```sh
z2-notify -h -n cleanup -b はい -b あとで "掃除しますか?" "一時ファイルを消します"
```

```sh
# 返事を待つ側（5-0 の雛形の handle() を書き換える。常駐サーバーに登録する）
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *notify_action*) ;; *) continue ;; esac
    case "$rec" in *cleanup*) ;; *) continue ;; esac
    case "$rec" in
      *はい*)   rm -rf ~/tmp/* && z2-toast "掃除しました" ;;
      *あとで*) z2-alarm in 1h cleanup ;;   # 1時間後にもう一度きく
    esac
  done
}
```

押された通知は自動で閉じます。返事をしなければ何も起きません（通知を消せばそれで終わりです）。

### `z2-state`（今の状態を聞く）

events.jsonl は「変化した瞬間」しか流れてきません。「**今**どうなっているか」で分岐したいときは
`z2-state` を使います。引数なしでまとめて JSON、キーを付けるとその値だけを返すので、
そのまま条件式に書けます。**追加の権限は要りません。**

| キー | 値 |
|---|---|
| `screen` | `on` / `off`（画面が点いているか） |
| `locked` | `true` / `false`（ロック画面が出ているか） |
| `idle` | `true` / `false`（Doze＝省電力の眠りに入っているか） |
| `charging` | `true` / `false` |
| `plug` | `ac` / `usb` / `wireless` / `none` |
| `level` | 電池残量 %（整数） |
| `wifi` | `true` / `false`（Wi‑Fi でつながっているか） |
| `ssid` | Wi‑Fi 名（位置情報権限が無いと空） |
| `ringer` | `normal` / `vibrate` / `silent` |
| `airplane` | `true` / `false` |
| `headset` | `true` / `false`（有線ヘッドセット/ヘッドホン） |
| `bt_audio` | `true` / `false`（Bluetooth オーディオが繋がっているか） |
| `temp` | 電池温度（℃・小数）。取れないときは `-1` |
| `volume` / `volume_max` | メディア音量の現在値 / 最大値 |

```sh
z2-state                                  # 全部まとめて JSON
[ "$(z2-state charging)" = "true" ] && echo 充電中
[ "$(z2-state screen)" = "off" ] && z2-notify "画面が消えているときだけ通知"
```

### `z2-screen`（画面を消させない — 時間を決めて）

「長いビルドの様子を眺めていたいので、**1 時間だけ**自動消灯を止めたい」ための道具です。

```sh
z2-screen keepon 1h        # 1 時間、画面が自分で消えないようにする（30m / 90s / 90 も可）
z2-screen status           # 掛かっているか・残り何秒か（JSON）
z2-screen keepon off       # 期限を待たずに今すぐ元へ戻す
```

- **OS 全体の設定**（画面消灯までの時間）を書き換えるので、**アプリを畳んでいても効きます**。
- ⚠ ツールバーの **🔅 とは別物**です。🔅 は「アプリを開いている間だけ消えない」もので、
  こちらとは互いに影響しません。用途で使い分けてください。
- **元の値は必ず書き戻します。** 掛けた時点で元の値を保存し、期限は OS のアラームで予約するので、
  アプリが落ちても・端末を再起動しても戻ります。掛けっぱなしで電池が溶ける状態を作りません。
- 一度に掛けられるのは **24h まで**（打ち間違いで何日も点きっぱなしにならないように）。
- **「システム設定の変更」の許可が要ります。** 設定 › **画面の自動消灯（z2-screen）** から許可して
  ください。許可が無いときは `z2-screen` がその旨を出して**何もしません**。
  （端末から `settings put` を叩く手は使えません。アプリの権限では拒否されます）

`z2-when` と組み合わせれば「充電を始めたら 2 時間消さない」のような使い方もできます。

```sh
z2-when charge:start run 'z2-screen keepon 2h'
z2-when charge:stop  run 'z2-screen keepon off'
```

### `z2-tile`（クイック設定パネルに置く）

ホーム画面ウィジェットは「ホーム画面に戻る」必要がありますが、クイック設定は**どのアプリを開いていても**
2 スワイプで出ます。別のことをしている最中に届く入口です。

```sh
z2-tile set 1 backup.sh                        # マクロを 1 番の枠へ
z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない   # コマンドと表示名
z2-tile list                                   # 4 枠すべて（枠 / 表示名 / コマンド。'-' は空き）
z2-tile clear 2                                # 割り当てを消す（clear all も）
```

- 割り当てるのは `~/.z2term/macros/` にある**マクロのファイル名**か、そのまま走らせる
  **コマンド**のどちらでもかまいません。**名前を見て自動で判別します**（`--macro` のような
  指定は要りません）。
- **押すと実行、もう一度押すと停止。** 実行中はタイルが緑になります（ウィジェットのボタンと同じ約束）。
- 実行時、環境変数 **`Z2_TILE`** に枠番号が入ります（マクロなら `Z2_TILE_MACRO` も）。同じマクロを
  複数の枠に置いて、中で分岐することもできます。
- ⚠ **ロック中は素通ししません。** Android が先に解除を求め、解除できたときだけ走ります。
  拾った人がシェードからコマンドを 1 発撃てる状態を作らないためで、設定で切り替えることはできません。
- ⚠ **枠はちょうど 4 つ。** タイルは manifest で個数を決め打ちする必要があり、実行中に増やせません。
- ⚠ **タイルを並べるのはご自身です。** クイック設定パネルの鉛筆（編集）から追加してください。
  アプリが勝手に置くことは Android が禁じています（Android 13 以降なら、設定 › **クイック設定タイル**
  の「クイック設定に追加」から OS に頼めます）。
- 実行ログは `~/.z2term/tile/run.log`（ウィジェットの `~/.z2term/widget/run.log` とは別）。

### `z2-alarm`（時刻で動かす）

「毎朝 7 時に」のような時刻トリガーです。指定時刻になると `events.jsonl` に
`{"event":"alarm","name":"…"}` が 1 行増えるので、あとは他のイベントと同じように拾えます。

```sh
z2-alarm at 07:00 morning      # 次の 07:00 に 1 回（今日を過ぎていれば明日）
z2-alarm daily 07:00 morning   # 毎日 07:00
z2-alarm in 5m tea             # 5 分後に 1 回（30s / 2h なども可）
z2-alarm list                  # 予約の一覧
z2-alarm cancel morning        # 名前で取り消し（id でも all でも可）
```

- **cron との違い**: cron は Android が省電力の眠り（Doze）に入ると動きません。`z2-alarm` は
  OS のアラームで起こしてもらうので、画面が消えていても動きます。
- ただし省電力を優先する仕組みを使っているので、**発火が数分ずれることがあります**
  （秒単位の正確さが要る用途には向きません）。
- 端末を再起動しても予約は残ります（アプリが起動時に貼り直します）。
- 名前は「どの用途のアラームか」を区別するためのものです。マクロ側で `name` を見て分岐します。

**`z2-alarm` と `z2-when time:` の使い分け**: どちらも時刻で動かせますが、`z2-alarm` は
**`events.jsonl` に `alarm` を 1 行書くだけ**で、拾う側（常駐スクリプト）が別に要ります。
`z2-when time:daily=07:00 run <コマンド>` なら**そのコマンドを直接**走らせるので、監視役が要りません。
**新しく書くなら `z2-when` を使ってください。** `z2-alarm` は既に常駐スクリプトで組んでいる場合や、
「アラームが鳴ったこと」もログに残したい場合に使います。

### `z2-when`（きっかけを登録する）

**登録できるきっかけと、渡される環境変数は 3-A の表を参照してください。** ここでは管理のしかたを書きます。

```sh
z2-when battery:below=20 run 'z2-say "電池が減っています"'
z2-when time:daily=07:00 run ~/.z2term/macros/morning.sh
z2-when notify:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
```

| コマンド | 動作 |
|---|---|
| `z2-when list` | 登録一覧（id / on\|off / トリガー / `->` / コマンド の TSV） |
| `z2-when events` | `event:` に書ける名前の一覧 |
| `z2-when log <id>` | **そのルールの実行ログ**（末尾）。動かないときはまずここ |
| `z2-when fired [n]` | 直近の発火（時刻 / id / トリガー / `run`\|`paused`） |
| `z2-when on <id>` / `off <id>` | 1 本ずつ 有効 / 無効 |
| `z2-when pause` / `resume` | **全部まとめて**一時停止 / 再開（ルールは消えない） |
| `z2-when remove <id\|all>` | 削除（`rm` でも可） |

- 登録した内容は 📜 → 「**自動化**」タブにも出ます。**▶ を押すときっかけを待たずに 1 回試せる**ので、
  スクリプト側の間違いはここで潰せます。
- 暴走したと思ったら `z2-when pause`（または自動化タブの「自動実行を一時停止」）。**どのきっかけが
  来ても実行しなくなります**が、ルールは消えず、▶ で自分から試すことはできます。
- 登録したコマンドは、発火のたびに `sh -lc` で（選択中の distro の中で）実行されます。
  **`Z2_WHEN_*` を使うときはコマンド全体を単一引用符で囲んでください**
  （`run 'z2-toast "$Z2_WHEN_EVENT"'`）。二重引用符で書くと、**登録するときのシェルが先に展開**して
  しまい、空文字が登録されます。

### `z2-noti`（いま出ている通知を読む）

```sh
z2-noti list        # key / パッケージ / アプリ名 / タイトル / 本文 の TSV
```

- 通知アクセスの許可が要ります（⚙設定 → 常駐サーバー・自動化 → 通知検知）。
- **読むだけです。** 通知のボタンを「押す」「消す」は意図的に用意していません
  （他アプリの決済・送信ボタンまで押せてしまうため）。
- 「届いた瞬間」に反応したいなら `z2-when notify:*`（3-A）。`z2-noti` は「**今**出ているものを
  数える・探す」用途です。

### `z2-session`（自分のタブを操る）

マクロから z2term 自身の端末タブを開いて、コマンドを流し込めます。

```sh
z2-session list                      # タブ一覧（番号 / id / 種別 / 印 / 名前 の TSV）
n=$(z2-session new build | cut -f1)  # タブを 1 枚開いて番号を受け取る
z2-session send "$n" 'make -j2' --enter
z2-session capture "$n" --all        # そのタブの画面を取り出す（--all は遡れる分も）
z2-session close "$n"                # 閉じる（最後の 1 枚は閉じない）
```

- `list` の印: `*` = 表示中のタブ / `!` = 何か動作中 / `?` = まだ起動していない / `-` = それ以外。
- `<先>` は **番号 / id / タブ名**のどれでもよく、`.` か省略で今表示しているタブです。
- **`send` は文字を入れるだけで実行しません。** 実行まで進めるときだけ `--enter` を付けます
  （勝手に走り出さないようにするためです）。
- `new <名前>` で付けた名前は**固定**されます（起動時の OS 名やシェルのタイトルで上書きされません）。

### `z2-intent`（汎用アクションの要）

`am start` に似たフラグで任意の Android Intent を組み立て、既定で `startActivity` します。
**これ 1 本でアプリ起動・設定画面表示・アラーム設定・地図検索・共有など**を表現できます。

```
z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
          [--es KEY VAL] [--ez KEY true|false] [--ei KEY N]
          [--broadcast | --service]
```

| フラグ | 意味 |
|---|---|
| `-a`, `--action` | Intent アクション（例 `android.intent.action.VIEW`）。先頭の非フラグ引数も action 扱い |
| `-d`, `--data` | データ URI（例 `https://…`, `tel:…`, `geo:…`） |
| `-t`, `--type` | MIME タイプ |
| `-p`, `--package` | 送り先パッケージを限定 |
| `-n`, `--component` | コンポーネント指定 `パッケージ名/クラス名` |
| `-f`, `--flags` | Intent フラグ（整数） |
| `--es K V` | 文字列 extra |
| `--ez K true\|false` | 真偽 extra |
| `--ei K N` | 整数 extra |
| `--broadcast` / `--service` | startActivity の代わりに sendBroadcast / startService |

例:
```sh
z2-intent -a android.intent.action.VIEW -d "https://example.com"   # URL を開く
z2-intent -a android.intent.action.DIAL -d "tel:0123456789"        # 電話アプリに番号入力
z2-intent -a android.intent.action.VIEW -d "geo:0,0?q=東京駅"       # 地図で検索
z2-intent -a android.settings.WIFI_SETTINGS                        # Wi‑Fi 設定画面
z2-intent -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 7 \
          --ei android.intent.extra.alarm.MINUTES 0                # 07:00 のアラーム設定
```

---

## 5. マクロの書き方（テンプレート）

**まず 5-A で書けないか確認してください。** 書ければ、スクリプトは「やること」だけの数行で済みます。
5-0 以降の監視の雛形が要るのは、`z2-when` に無いきっかけを使うときと、ログの中身まで見て
判断したいとき（5-5 / 5-6 / 5-7）だけです。

### 5-A. `z2-when` に任せる（まずこちら）

やることだけを書いて、きっかけを登録します。

```sh
#!/bin/sh
# ~/.z2term/macros/lowbat.sh ─ 電池が減ったら知らせるだけ
z2-say "電池が $(z2-state level) パーセントです"
z2-notify "電池が少なくなりました" "残り $(z2-state level)%"
```

```sh
chmod +x ~/.z2term/macros/lowbat.sh
z2-when battery:below=20 run ~/.z2term/macros/lowbat.sh
z2-when list                       # 登録できたか確認
```

**監視ループも、常駐サーバーへの登録も要りません。** アプリ側が待ち受けて、条件が合ったときだけ
このスクリプトを 1 回走らせます。

短いものはファイルにせず直接書けます。

```sh
z2-when charge:start  run 'z2-volume 30%'
z2-when charge:stop   run 'z2-volume 70%'
z2-when wifi:ssid=home run 'z2-toast "自宅 Wi-Fi"'
z2-when notify:otp    run 'echo "$Z2_WHEN_OTP" | z2-clip set'
```

**うまく動かないとき**は、この順で見てください。

1. `z2-when list` — 登録されているか、`off` になっていないか
2. 📜 → 自動化タブの **▶** — きっかけを待たずに 1 回試す（スクリプト側の間違いはここで出ます）
3. `z2-when log <id>` — 実行したときの出力とエラー
4. `z2-when fired` — そもそも発火したか（`paused` と出ていたら一時停止中）

### 5-0. ログの読み方（自分で監視するときの雛形）

**5-5 / 5-6 / 5-7 の例はこの雛形を前提にしています。**

ログは `tail -F` で追ってはいけません。⚙設定でログ形式を自由に変えられるうえ、
**「新着を上」に切り替えると先頭追記になり、ファイル末尾に新着が来なくなる**ためです。
`tail -F` は末尾しか見ないので、その設定では永久に何も拾いません。複数行テンプレートでは
1 レコードが複数行に割れるので、「1 行 = 1 レコード」という前提も崩れます。

そこで**前回スナップショットとの差分**を読みます。差分が前回内容の前後どちらに付いたかで
追記方向が分かり、差分を塊のまま渡せば複数行テンプレートも扱えます。

```sh
#!/bin/sh
# 全マクロ共通の雛形。LOG と tag（作業ファイル名）と handle() を書き換えて使う。
POLL=2                                    # ログを見に行く間隔(秒)
LOG=$HOME/.z2term/events.jsonl
SNAP=$HOME/.z2term/.mymacro.snap
WORK=$HOME/.z2term/.mymacro.work

[ -f "$LOG" ] || : > "$LOG"

# ここに「新着の塊」が渡ってくる。中身の見方は次の 5-1 以降を参照。
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in
      *power_connected*) z2-toast "充電を開始しました" ;;
    esac
  done
}

# 初回は「今ある分」を既読の基準にするだけで、過去ログには反応しない。
cp "$LOG" "$SNAP" 2>/dev/null || : > "$SNAP"

while :; do
  sleep "$POLL"
  [ -f "$LOG" ] || continue

  cn=$(wc -c < "$LOG"  2>/dev/null || echo 0)
  pn=$(wc -c < "$SNAP" 2>/dev/null || echo 0)
  [ "$cn" = "$pn" ] && continue           # サイズが同じなら変化なしとみなす

  new=''
  if [ "$cn" -gt "$pn" ] && [ "$pn" -eq 0 ]; then
    # 直前が空 = 全体が新着。(起動時に必ず基準を取るので過去ログの誤発火にはならない)
    new=$(cat "$LOG")
  elif [ "$cn" -gt "$pn" ]; then
    grew=$((cn - pn))
    head -c "$pn" "$LOG" > "$WORK" 2>/dev/null
    if cmp -s "$WORK" "$SNAP"; then
      new=$(tail -c "$grew" "$LOG")  # 前回内容で「始まる」→ 末尾追記(新着が下)
    else
      tail -c "$pn" "$LOG" > "$WORK" 2>/dev/null
      if cmp -s "$WORK" "$SNAP"; then
        new=$(head -c "$grew" "$LOG")  # 前回内容で「終わる」→ 先頭追記(新着が上)
      fi
      # どちらでもない = 書き換え/掃除。基準を貼り直すだけで発火しない。
    fi
  fi
  # cn < pn (truncate された) も基準の貼り直しだけ。

  cp "$LOG" "$SNAP" 2>/dev/null
  [ -n "$new" ] && handle "$new"
done
```

**形式への依存はここで分かれます**:

- **イベント名・名前で照合する**のは形式非依存です。`{event}` は JSON でもテンプレートでも
  `power_connected` のようにそのまま出るので、`case "$rec" in *power_connected*)` で拾えます。
  同梱サンプルのうち**ログを監視する 5 本**（`watch-basic` / `battery-alert` / `daily-report` /
  `otp-clip` / `otp-sms`）はこの方針で書いてあります。
- **値そのもの**（電池残量など）は `z2-state` で取れば形式に一切依存しません。こちらを優先してください。
- **ログのフィールドを解析する**場合（`ssid` など `z2-state` に無いもの）だけは、
  あなたが選んだ形式に依存します。既定の JSONL 前提で書き、形式を変えたら抽出も直してください。

**制約**: 1 回の `POLL` 周期内に届いた複数レコードは 1 つの塊として渡ります。

### 5-1. 最小形：イベントを監視して反応する

5-0 の雛形の `handle()` だけを書き換えます（イベント名での照合なので形式非依存）。

> 💡 1 つのイベントに 1 つの反応を返すだけなら `z2-when event:<名前> run <コマンド>` の方が短く済みます。
> この形が要るのは、**複数のイベントをまとめて 1 本で扱いたい**ときや、
> **前に何が起きたかを覚えておいて判断したい**ときです。

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in
      *power_connected*)   z2-say "充電を開始しました" ;;
      *headset_plugged*)   z2-media play ;;
      *headset_unplugged*) z2-media pause ;;
      *screen_off*)        : ;;  # 何もしない例
    esac
  done
}
```

### 5-2. フィールドを使う（電池残量・SSID）

値は**まず `z2-state` を試してください**。ログを解析しないので形式に依存しません。

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *battery_low*) ;; *) continue ;; esac
    z2-notify "電池注意" "残り $(z2-state level)% です"
  done
}
```

`ssid` のように `z2-state` に無いものだけ、ログから取ります。**ここは形式に依存する**ので、
既定の JSONL 前提です（形式を変えたら抽出も直してください）。

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *wifi_connected*) ;; *) continue ;; esac
    ssid=$(printf '%s' "$rec" | jq -r '.ssid // empty' 2>/dev/null)   # 6章に sed 版
    [ "$ssid" = "home" ] || continue
    z2-volume 60% ; z2-toast "自宅 Wi‑Fi・音量を戻しました"
  done
}
```

### 5-3. 動かし方（常駐させる / 使い切りにする）

スクリプトには **2 通りの動かし方**があり、**取り違えると事故になります**。

| 型 | どう動かすか | 中身 |
|---|---|---|
| **常駐**（ずっと動き続ける） | `⚙設定 → 常駐サーバー` に起動コマンドを登録（例: `sh ~/.z2term/macros/watch.sh`）。「起動時に自動で常駐」も ON にすれば、アプリを開かず・再起動後も動く | 5-0 の雛形を使うもの（監視ループがある） |
| **使い切り**（1 回走って終わる） | `z2-when` に登録する／ウィジェットのボタンに割り当てる／手で実行する | 5-A のように「やることだけ」書いたもの |

⚠ **使い切りのスクリプトを常駐サーバーに登録しないでください。** 常駐サーバーは「終了した＝落ちた」と
みなして再起動をかけるので、**終わるたびに走り直します**（フィード購読なら延々と取りに行き続けます）。

手元で試すだけなら、ターミナルでそのまま `sh ~/.z2term/macros/watch.sh &`（常駐型）／
`sh ~/.z2term/macros/lowbat.sh`（使い切り）で動きます。

**自作のスクリプトには動かし方を 1 行書いておけます**（0.8.247 以降）。

```sh
#!/bin/sh
# rss.sh — フィードを見に行って新着だけ通知する    ← 2 行目は z2-macro list に出る説明
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
```

`# z2-run:` があると、`z2-macro install` がそのスクリプトの案内としてこの行を出します
（無ければ「常駐サーバーに登録してください」を出します）。**説明行（2 行目）より後ろ**に置いてください。

### 5-4. 時刻・繰り返し

**`z2-when time:`** が一番短く書けます。時刻になったらコマンドが直接走るので、監視役が要りません。

```sh
z2-when time:daily=07:00 run ~/.z2term/macros/morning.sh   # 毎朝 7 時
z2-when time:every=30m   run ~/.z2term/macros/rss.sh       # 30 分ごと
z2-when time:cron='0 3 * * 1-5' run ~/.z2term/macros/nightly.sh  # 平日の 3:00
```

`time:cron=` は 5 フィールドの cron 式（`*` / `*/n` / `a` / `a-b` / `a-b/n` / `a,b,c`。曜日は 0-7 で
0 と 7 が日曜）。**空白を含むのでクォートが要ります。**

すでに常駐スクリプトで組んでいる場合や、「アラームが鳴ったこと」もログに残したい場合は **`z2-alarm`** を
使います。時刻になると `events.jsonl` に `alarm` が 1 行増えるので、他のイベントとまったく同じ書き方で
拾えます。

```sh
z2-alarm daily 07:00 morning     # 1 回仕掛けるだけ（再起動しても残ります）
```

```sh
# 監視側（5-0 の雛形の handle() を書き換える。常駐サーバーに登録する）
# alarm と、z2-alarm に付けた名前 (morning) の両方が新着に出たときだけ動く。
# 複数行テンプレートだと 2 つが別々の行に出るので、行ごとではなく塊のまま見る。
handle() {
  case "$1" in *alarm*) ;; *) return ;; esac
  case "$1" in *morning*) ;; *) return ;; esac
  z2-say "おはようございます。電池は $(z2-state level) パーセントです"
}
```

distro の cron でも書けますが、**Android が省電力の眠り（Doze）に入ると cron は止まります**。
画面を消していても確実に動かしたいなら `z2-when time:` か `z2-alarm` を使ってください
（どちらも OS のアラームで起こしてもらう作りです。そのかわり発火が数分ずれることがあります）。
なお `time:cron=` は**書き方だけ** cron 式を借りたもので、動かしているのは distro の cron ではありません。

### 5-5. 実例：ロック解除を N 回失敗したら通知＋位置を記録（盗難対策）

⚙設定「ロック解除の失敗監視」を ON にし、案内に従って**端末管理者**を有効化すると、
解除の失敗が `unlock_failed`（`level` = 連続失敗回数）として流れます。3 回目から反応する例:

```sh
# 監視側（5-0 の雛形の handle() を書き換える。常駐サーバーに登録する）
# 失敗回数は level フィールドなので、ここは既定の JSONL 前提（形式を変えたら抽出も直す）。
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *unlock_failed*) ;; *) continue ;; esac
    n=$(printf '%s' "$rec" | jq -r '.level // 0' 2>/dev/null)
    [ "$n" -ge 3 ] 2>/dev/null || continue
    ts=$(date '+%F %T')
    z2-notify -h "解除失敗 ${n} 回" "$ts"          # 手元の別端末にも届くよう通知
    echo "$ts unlock_failed x$n" >> ~/theft.log     # 記録を残す
    # 例: 自宅サーバーへ即退避（要 ssh 鍵）
    # scp ~/theft.log backup:/srv/ 2>/dev/null
  done
}
```

⚠ **`unlock_failed` / `unlock_succeeded` は PIN・パターン・パスワードの失敗/成功でしか発火しません。**
指紋や顔認証は Android が端末管理者に通知しない仕組みのため、**指紋で解除しても `unlock_succeeded` は流れません**。
「失敗したら鳴らして、解除できたら止める」を組むときに成功側を `unlock_succeeded` で待つと、指紋で解除した
場合に止まりません。**止める合図には `unlocked`（`USER_PRESENT`。生体認証を含むあらゆる解除で必ず発火）を使ってください**
（`unlocked` は「システムイベント検知」が ON なら流れます）。

撮影や送信の中身は**あなたのマクロ次第**です（このアプリはカメラ撮影を組み込みません。バックグラウンド
撮影は Android の制約で別途 root/専用アプリが要ります）。位置は位置情報権限のある distro ツールや
`z2-state`／API と組み合わせてください。**端末管理者は失敗回数の監視だけに使い、遠隔ロック/初期化はしません。**

### 5-6. 実例：SMS のワンタイムコードを自動コピー＆自動クリア

通知（SMS 等）に含まれる**ワンタイムコード（OTP・認証番号）**を自動でクリップボードへ入れ、
一定秒後に「値が変わっていなければ」自動で消す実用マクロです。通知トリガー・`z2-clip`・
自動後始末（サブシェル＋`sleep`）を組み合わせた、このガイドの機能だけで完結する例です。

> 💡 **コピーするだけなら 1 行で済みます**（`notify:otp` は抽出まで済ませて `Z2_WHEN_OTP` に入れます）。
>
> ```sh
> z2-when notify:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
> ```
>
> 以下は**自動クリアまで付ける**場合の実装であり、同時に**ログを自分で解析する書き方の実例**でもあります。
> 「メタ情報の数字を先に消す」「キーワードからの位置で選ぶ」といった抽出の考え方は、
> 他のログを扱うときにもそのまま使えます。

**ポイント**（そのまま流用できる定石）:
- **キーワードで絞る**（認証/確認/コード/OTP…）→ 普通のメッセージや電話番号を拾わない。
- 抽出は **4〜8 桁の数字 1 つ**に限定。`123-456` 形式は区切りを除いてから拾う。
- **ログ形式にも追記方向にも依存しない**（下の「なぜ行で読まないか」を参照）。設定でテンプレートを
  自由に変えても、「新着を上」に切り替えても、マクロ側は無変更で動く。
- **自動クリア**はコピー時の値と一致するときだけ実行（途中で別物をコピーしていたら残す）。
  クリップボードは他アプリからも読める共有領域なので、貼り付け後に消えるほうが安全。

**なぜ行で読まないか**（ここがこのマクロの肝）:

通知ログは**あなたが形式を自由に決められる**。素朴に「1 行 = 1 通知」と読み、`tail -F` で末尾を
追う実装は、次の 2 つで破綻する。

- **複数行テンプレート**（`\n` を含む形式）では題名と本文が別々の行に割れ、キーワードとコードが
  同じ行に揃わない。
- **先頭追記**（⚙設定の「新着を上」）ではファイルの**末尾に新着が来ない**ので、`tail -F` は
  永久に何も拾わない。

そこで**前回スナップショットとの差分**を見る。差分が前回内容の前後どちらに付いたかで追記方向を
自動判別でき、差分は行に割らず**塊のまま**走査するので複数行テンプレートでもまたいで拾える。

もう一点、形式が自由なほど「コードに見えるが違う数字」が紛れ込む。日時・エポック（`{ts}`）・
通知 ID（`{key}` は `0|com.example|2847|null|10268` のような形）・パッケージ名（`{pkg}`）を先に
除去し、さらに**キーワードからの位置**でコードを選ぶ。「最初に見つかった数字」方式だと、
`{key}` を含むテンプレートで通知 ID をコードと取り違える。

```sh
#!/bin/sh
# ~/.z2term/macros/otp-clip.sh
# 通知内のワンタイムコード(4〜8桁)を自動でクリップボードにコピーし、TTL 秒後に自動クリア。
# ログ形式・追記方向のどちらにも依存しない（読み取り部は 5-0 の雛形と同じ）。
# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可
# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/otp-clip.sh  を登録

TTL=60                                    # コピーから何秒でクリアするか
KEYWORDS='認証|確認|ワンタイム|コード|パスワード|code|otp|verification|verify|one[- ]?time'
POLL=2                                    # ログを見に行く間隔(秒)
LOG=$HOME/.z2term/notifications.jsonl
SNAP=$HOME/.z2term/.otp-clip.snap
WORK=$HOME/.z2term/.otp-clip.work

[ -f "$LOG" ] || : > "$LOG"

# TTL 秒後、クリップボードがコピー時の値のままなら空にする(別物をコピーしていたら残す)。
schedule_clear() {
  code=$1
  ( sleep "$TTL"
    cur=$(z2-clip get 2>/dev/null)
    if [ "$cur" = "$code" ]; then
      z2-clip set ""
      z2-toast "コピーしたコードをクリアしました"
    fi
  ) &
}

handle() {
  raw=$1

  # コードに見えるが違うものを先に消す: 日時 / 時刻 / 9 桁以上(エポック等) /
  # '|' を含むトークン ({key} の通知 ID) / ドット区切り識別子 ({pkg})。最後に "123-456" を詰める。
  scan=$(printf '%s' "$raw" | sed \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}[T ][0-9:+-]*/ /g' \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}/ /g' \
    -e 's/[0-9]\{1,2\}:[0-9]\{2\}\(:[0-9]\{2\}\)\?/ /g' \
    -e 's/[0-9]\{9,\}/ /g' \
    -e 's/[^ ]*|[^ ]*/ /g' \
    -e 's/[A-Za-z0-9_]\{1,\}\.[A-Za-z0-9_.]\{1,\}/ /g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g')

  # 「最初に見つかった数字」ではなくキーワードの直後を優先し、無ければ直前の最も近い数字。
  # 自由な形式では前後にメタ情報の数字が混ざるため、位置で選ばないと取り違える。
  # 数字列は必ず最大長で切り出し、長い数字列の一部を切り取らない。
  code=$(printf '%s' "$scan" | awk -v kw="$KEYWORDS" '
    function firstcode(s,   r) {
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) return r
        s = substr(s, RSTART + RLENGTH)
      }
      return ""
    }
    function lastcode(s,   r, best) {
      best = ""
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) best = r
        s = substr(s, RSTART + RLENGTH)
      }
      return best
    }
    { buf = buf " " $0 }                    # 複数行でも 1 つの塊として扱う
    END {
      if (!match(tolower(buf), kw)) exit       # キーワード無し = 認証通知ではない
      # RSTART/RLENGTH は awk の組み込みグローバルで、下の match() に壊されるため先に退避する。
      ks = RSTART; kl = RLENGTH
      c = firstcode(substr(buf, ks + kl))      # キーワードの直後を優先
      if (c == "") c = lastcode(substr(buf, 1, ks - 1))   # 無ければ直前の最も近い数字
      if (c != "") print c
    }')
  [ -z "$code" ] && return

  z2-clip set "$code"
  z2-toast "コードをコピー: ${code}"
  schedule_clear "$code"
}

# 初回は「今ある分」を既読の基準にするだけで、過去ログには反応しない。
cp "$LOG" "$SNAP" 2>/dev/null || : > "$SNAP"

while :; do
  sleep "$POLL"
  [ -f "$LOG" ] || continue

  cn=$(wc -c < "$LOG"  2>/dev/null || echo 0)
  pn=$(wc -c < "$SNAP" 2>/dev/null || echo 0)
  [ "$cn" = "$pn" ] && continue           # サイズが同じなら変化なしとみなす

  new=''
  if [ "$cn" -gt "$pn" ] && [ "$pn" -eq 0 ]; then
    # 直前が空 = 全体が新着。(起動時に必ず基準を取るので過去ログの誤発火にはならない)
    new=$(cat "$LOG")
  elif [ "$cn" -gt "$pn" ]; then
    grew=$((cn - pn))
    head -c "$pn" "$LOG" > "$WORK" 2>/dev/null
    if cmp -s "$WORK" "$SNAP"; then
      new=$(tail -c "$grew" "$LOG")  # 前回内容で「始まる」→ 末尾追記(新着が下)
    else
      tail -c "$pn" "$LOG" > "$WORK" 2>/dev/null
      if cmp -s "$WORK" "$SNAP"; then
        new=$(head -c "$grew" "$LOG")  # 前回内容で「終わる」→ 先頭追記(新着が上)
      fi
      # どちらでもない = 書き換え/掃除。基準を貼り直すだけで発火しない。
    fi
  fi
  # cn < pn (truncate された) も基準の貼り直しだけ。

  cp "$LOG" "$SNAP" 2>/dev/null
  [ -n "$new" ] && handle "$new"
done
```

調整は先頭の `TTL`（クリアまでの秒数）・`POLL`（反応の速さ）・`KEYWORDS`（対応語を追加）だけ。
届いてから最大 `POLL` 秒でコードだけ入るので、入力欄で貼り付けるだけで済みます。

**制約**: 1 回の `POLL` 周期内に通知が 2 件届くと 1 つの塊として扱われます（認証通知が連続する場面は
稀ですが、原理的な穴です）。

**Android 15 以降の重要な注意（OTP が伏せ字になる）**: 「**高度な通知**（機種により「通知の補助機能」
「Adaptive Notifications」）」が ON だと、Android System Intelligence が OTP を含む通知を**機微**と判定し、
**"信頼されていない" 通知リスナー（一般アプリはすべてこれ）には本文を伏せ字**（`プライベートな通知内容は
表示されません` 等）に置き換えてから渡します。**通知アクセスを全許可していても** OTP の本文だけ届きません
（通知そのものは受け取れるので行は出るが `text` が伏せ字になる）。回避策は次のどちらか。
- **SMS の OTP なら「SMS 検知」を使う（推奨）** → 下の 5-7 参照。SMS を**直接**読むので、この伏せ字を
  完全に迂回でき、ロック中でも取れる。機種の設定に依存しない一番確実な方法。
- **`設定 → 通知 → 高度な通知` を OFF** にする（Pixel 等では有効。ただし機種によりこのトグルが無い／効かない。OTP 自動入力候補等の OS 機能も止まる）
- `RECEIVE_SENSITIVE_NOTIFICATIONS` 権限を持つ **"信頼された" リスナー**にする。これは system 署名か特定ロール
  （companion watch/glasses・ホーム等）向けで、通常アプリには自動付与されず、宣言のうえ adb 付与が要る（機種依存で不発もある）

なお伏せ字が外れた後は、本文がタイトル・本文・メッセージのどのフィールドにも入らない**完全カスタム表示だけ**の
通知（ごく一部のアプリ）を除き拾えます。SMS のワンタイムコードは MessagingStyle の本文に入るのが普通で、
0.8.185 以降はこれも取得します。

### 5-7. 実例：SMS のワンタイムコードを「SMS 検知」で確実にコピー（伏せ字を迂回）

Android 15+ では上記のとおり **SMS の OTP は通知経由だと伏せ字**になり、通知検知では取れないことがあります
（マクロドロイド等の通知トリガーでも同じ）。そこで z2term は **SMS を直接読む**「SMS 検知」を持っています。
これは通知ではなく SMS 本文そのものを読むため、**機微通知の伏せ字もロック状態も一切通りません**。

- 準備: `⚙設定 → SMS 検知` を **ON** ＋ 出る許可ダイアログで **SMS 受信を許可**
- 記録先: `~/.z2term/sms.jsonl`（フィールド: `ts` `time` `from` `body`）
- **一番短い書き方**（`sms:otp` が抽出まで済ませます。記録の ON/OFF とは独立に動きます）:

  ```sh
  z2-when sms:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
  ```

- 自動クリアまで欲しいとき: `z2-macro install otp-sms.sh` で 5-6 の SMS 版が入る（`sms.jsonl` を見て
  4〜8 桁を抽出）。`⚙設定 → 常駐サーバー` に `sh ~/.z2term/macros/otp-sms.sh` を登録すれば、
  ロック中でも OTP をコピーできる。

認証アプリの通知など **SMS でない OTP** はこの経路の対象外です（その場合は通知検知＋上の回避策になります）。

### 5-8. 実例：フィードを購読する（部品の組み合わせ方）

`z2-macro install rss rss-open` で入る 2 本は、**アプリに専用の画面を 1 枚も足さずに**
「定期実行 → 新着だけ拾う → 通知 → ブラウザで開く → ウィジェットで一覧」を組んだ例です。
自分のマクロで何をどう繋げられるかの見本として読めます。

| やること | 使っている部品 |
|---|---|
| 定期実行 | `z2-when time:every=30m run ~/.z2term/macros/rss.sh` |
| 新着だけ拾う | `seen.txt` を**引き算**（`grep -Fxv`）。フィードの日付や並び順は当てにしない |
| 知らせる | `z2-notify -b 開く`（ボタン付き通知） |
| ボタンの返事 | `z2-when event:notify_action run '[ "$Z2_WHEN_EVENT_NAME" = rss ] && z2-open "$(head -1 ~/.z2term/rss/new.txt \| cut -f1)"'` |
| 開く | `z2-open <URL>` |
| 一覧を眺める | ライブ tail ウィジェットで `~/.z2term/rss/latest.txt` を「**先頭 (head)**」表示 |
| 次の 1 本を開く | 状態ウィジェットのボタンに `rss-open` を割り当て（`opened.txt` を引き算するので同じ記事を二度開かない） |

- **どちらも使い切り**です（5-3）。**常駐サーバーには登録しないでください。**
- 解析は python3 の標準ライブラリだけ（pip 不要）。1 本落ちても他のフィードは続き、壊れた XML は黙って飛ばします。
- 取りに行くほど電池を食うので **30 分より短くしない**でください。
- 設定手順は `docs/ja/HANDBOOK.md` の「フィードを購読する（RSS / Atom）」を参照。

**「引き算で新着を出す」は定石です。** 相手が出す日付や並び順が当てにならないものを扱うとき、
「前に見た分を覚えておいて引く」だけで新着が出ます（`z2scan` のベースライン差分と同じ考え方）。

---

## 6. jq が無いときの解析（純 POSIX）

`jq` を入れられない環境では、フィールド抽出を sed/grep で代替できます（JSONL は 1 行 1 レコードなので簡単）。

```sh
# event の値を取り出す
ev=$(printf '%s' "$line" | sed -n 's/.*"event":"\([^"]*\)".*/\1/p')
# level（数値）を取り出す
level=$(printf '%s' "$line" | sed -n 's/.*"level":\([0-9]*\).*/\1/p')
```

---

## 7. AI にマクロを作ってもらう

このガイドを AI に読み込ませ、下の指示文（プロンプト）と「やりたいこと」を渡すと、そのまま動く
マクロスクリプトを生成できます。**AI への指示文の例**（コピペ用）:

> あなたは z2term マクロ生成器です。この `MACRO-GUIDE.md` の仕様だけを使い、次の要望を満たす
> **POSIX sh スクリプト 1 本**と、**それを動かすためのコマンド**を出力してください。制約:
> - **まず「3-A. `z2-when` に登録できるきっかけ」で表現できないか検討する。** 表現できるなら、
>   スクリプトには**やることだけ**を書き（監視ループを書かない）、末尾に `z2-when <トリガー> run <パス>`
>   の登録コマンドを添える。
> - `z2-when` に無いきっかけや、複数の出来事を組み合わせて判断する場合だけ、「5-0. ログの読み方」の
>   雛形をそのまま使う（`tail -F` は使わない。先頭追記の設定で動かなくなるため）。書き換えるのは
>   `LOG` / 作業ファイル名 / `handle()` だけ。
> - 分岐はイベント名での照合（`case "$rec" in *power_connected*)`）を優先する。ログ形式に依存しないため。
> - 値は `z2-state` で取れるものは `z2-state` を使う。ログのフィールド解析は `z2-state` に無いものだけ。
> - トリガー名・イベント名・フィールドは本ガイド「3. トリガー・リファレンス」にある名前だけを使う。
> - アクションは本ガイド「4. アクション・リファレンス」の `z2-*` だけを使う（存在しない機能は使わない）。
> - `Z2_WHEN_*` を使う場合、`z2-when ... run '...'` は**単一引用符**で囲む（登録時に展開させない）。
> - JSON 解析が要る場合は `jq` を第一候補にし、無い場合の sed フォールバックも併記する。
> - 依存パッケージ（jq 等）の導入コマンドをコメントで添える。
> - スクリプトの 2 行目に「何をするものか」の説明コメント、その次の行に `# z2-run: <動かし方>` を書く
>   （常駐が要るものだけ「常駐サーバーに `sh <パス>` を登録」と書く。使い切りを常駐させてはいけない）。
> - スクリプトは 1 ファイルで完結させ、各分岐に日本語コメントを付ける。
>
> やりたいこと: 「__ここに自然言語で要望__」（例: 充電を始めたら音量を 3 割にして「充電中」と読み上げ、
> 外したら音量を 7 割に戻す）

AI が「無い機能」を使わないよう、**必ず本ガイドの範囲内で**と明示するのがコツです。

---

## 8. トラブルシュート

- **`z2-when` のルールが動かない** → ① `z2-when list` で `off` になっていないか ② `z2-when fired` に
  `paused` と出ていないか（`z2-when resume` で再開）③ **そのトリガーの前提**（3-A の「前提」列）を
  満たしているか — `charge:` / `battery:` / `wifi:` / `sensor:` / `file:` / `event:` は
  「システムイベント検知」が ON でないと動きません ④ 📜 → 自動化タブの **▶** で 1 回試して、
  「きっかけが来ていない」のか「スクリプトが失敗している」のかを切り分ける。
- **発火しているのに何も起きない** → `z2-when log <id>` にコマンドの出力とエラーが残ります。
  `Z2_WHEN_*` を**二重引用符**で登録していると、登録時に展開されて空になります（→ 4 章）。
- **同じきっかけが続いても 1 回しか動かない** → 同じルールは **10 秒以内に続けて発火しません**。
- **`file:new=` が拾わない** → 検知が ON の間だけ働きます。**書き込みが終わってから**発火するので、
  コピー途中では動きません（隠しファイルも対象外）。
- **入れたマクロが延々と走り続ける** → 使い切りのスクリプトを常駐サーバーに登録していませんか（→ 5-3）。
- **events.jsonl が増えない** → ⚙設定「システムイベント検知」が ON か。稼働中は常駐通知が出ます。
- **`ssid` が空** → SSID の取得には位置情報権限が要ります（v1 は要求しないので空になることがあります）。接続/切断の検知自体は動きます。
- **`z2-*: cannot write request (storage perm?)`** → アプリのストレージ権限を確認。
- **`z2-media` が効かない** → 直前に再生していたメディアアプリが無いと反応しません（キーを送るだけのため）。
- **`z2-torch` がエラー** → フラッシュ非搭載端末では使えません。
- **常駐が落ちる** → 電池最適化の除外や、常駐サーバーの設定を確認。省電力モード ON 中は画面消灯中の反応が遅れることがあります。
