# z2term マクロガイド（z2term-macro）

**スマホの操作を自動化する「マクロ」を、z2term のターミナルだけで組むための仕様書。**
人間が読んでそのまま書ける説明書であると同時に、**AI に丸ごと読ませて「〜したい」と頼めば
マクロ用のスクリプトを生成できる**ように、機械可読なリファレンスを兼ねています。

> 対象バージョン: 0.8.167-alpha 以降 / 英語版: `docs/en/MACRO-GUIDE.md`
> すべて **非 root・完全ローカル・外部送信なし**。難しい権限が要る機能は含みません。

---

## 1. 考え方（3 段構成）

z2term のマクロは MacroDroid 等と同じ「**トリガー → 判断 → アクション**」で組み立てます。

| 段 | 誰が | z2term では |
|---|---|---|
| **トリガー** | Android → シェル | システムイベントを `~/.z2term/events.jsonl` に追記（充電/画面/ロック/Wi‑Fi/ヘッドセット/機内/マナー等）。通知は `~/.z2term/notifications.jsonl`。**時刻**は `z2-alarm` が同じ events.jsonl に `alarm` を書く |
| **判断（ロジック）** | シェル | ログ行を読んで条件分岐（`if`・時刻・回数・状態ファイル…）。ふつうの sh/awk/jq で自由に。**今の状態**は `z2-state` で聞ける |
| **アクション** | シェル → Android | `z2-*` コマンドで Android 側を操作（通知/読み上げ/音量/ライト/Intent 発火…） |

**マクロの実体＝「イベントログを監視して、条件が合ったらアクションを叩くシェルスクリプト」**です。

---

## 2. 使う前の準備（1 回だけ）

1. **トリガーを有効化**: アプリの ⚙設定 →
   - 「**システムイベント検知**」を ON（events.jsonl が書かれ始める）。
   - 通知も使うなら「**通知検知**」を ON（＋OS の「通知アクセス」を許可）。
2. **常駐させたいとき**: ⚙設定 → 「**常駐サーバー**」にマクロ用スクリプトの起動コマンドを登録すると、
   アプリを開かなくても・端末再起動後も動きます（「起動時に自動で常駐」も ON に）。
3. 便利ツール: `jq`（JSON 解析）を入れておくと楽。例: Alpine `apk add jq` / Debian系 `apt install jq`。
4. **白紙から書きたくないとき**: `z2-macro list` で同梱サンプルを見て、`z2-macro install <名前>` で
   `~/.z2term/macros/` に置けます（`z2-macro install all` で全部）。中身は自由に書き換えて構いません。
   既にあるファイルは上書きしないので、編集したものが消えることはありません（`-f` で明示的に上書き）。

---

## 3. トリガー・リファレンス（events.jsonl）

- 場所: `~/.z2term/events.jsonl`（1 行 = 1 イベントの JSON。追記のみ）。
- **大きさの上限はありません**（1 本に全履歴を追記し続けます）。過去に遡って集計したいとき 1 ファイルで済むようにするためです。
  容量が気になったら、ターミナルで自分で切り詰めてください（例: `: > ~/.z2term/events.jsonl`）。なお「新しいものを先頭に」モードは 1 件ごとにファイル全体を書き直すので、大量常用では既定の末尾追記が軽いです。先頭追記のままログが 10MB を超えると、設定画面にサイズと対処の注意が出ます。
- 既定フィールド: `ts`（エポック ミリ秒・整数）, `time`（ISO8601 文字列）, `event`（種別）, 一部で `level`（電池残量 %）, `ssid`（Wi‑Fi 名）。
- 出力形式は設定でテンプレート化できますが、**マクロ用途では既定の JSONL のまま**が扱いやすいです。

### イベント種別（`event` の値）

| event | 意味 | 追加フィールド |
|---|---|---|
| `screen_on` / `screen_off` | 画面 点灯 / 消灯 | — |
| `unlocked` | ロック解除（本人確認後） | — |
| `power_connected` / `power_disconnected` | 充電 開始 / 停止 | `level` |
| `battery_low` / `battery_okay` | 電池残量 低下 / 回復 | `level` |
| `battery_level` | 残量が 10% 刻みの境界を跨いだとき | `level` |
| `wifi_connected` / `wifi_disconnected` | Wi‑Fi 接続 / 切断 | `ssid`（位置情報権限が無いと空） |
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

### 通知トリガー（notifications.jsonl）

- 場所: `~/.z2term/notifications.jsonl`。フィールド: `ts` `time` `pkg`（パッケージ名）`app`（アプリ名）`title` `text` `category` `key`。
- 例: 「特定アプリの通知が来たら…」の起点に使えます。

---

## 4. アクション・リファレンス（z2-* コマンド）

端末から叩くと、アプリが Android 側を代行します。**すべて権限不要**（`z2-intent` の呼び先が求める権限は別）。

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
| `z2-alarm` | `z2-alarm at\|daily HH:MM [名前]` ほか | **時刻トリガー**を仕掛ける（下記） | 予約内容 JSON |
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
# 返事を待つ側（常駐サーバーに登録する）
tail -n0 -F ~/.z2term/events.jsonl | while IFS= read -r line; do
  ev=$(printf '%s' "$line"     | jq -r '.event')
  name=$(printf '%s' "$line"   | jq -r '.name   // empty')
  action=$(printf '%s' "$line" | jq -r '.action // empty')
  [ "$ev" = "notify_action" ] && [ "$name" = "cleanup" ] || continue
  case "$action" in
    はい)   rm -rf ~/tmp/* && z2-toast "掃除しました" ;;
    あとで) z2-alarm in 1h cleanup ;;   # 1時間後にもう一度きく
  esac
done
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

### 5-1. 最小形：イベントを監視して反応する

`tail -F` で events.jsonl を追い、行ごとに条件分岐します（`-n0` で過去分を読まず今から監視）。

```sh
#!/bin/sh
# ~/.z2term/macros/watch.sh
tail -n0 -F ~/.z2term/events.jsonl 2>/dev/null | while IFS= read -r line; do
  ev=$(printf '%s' "$line" | jq -r '.event' 2>/dev/null)   # jq が無ければ 6章の grep 版
  case "$ev" in
    power_connected)   z2-say "充電を開始しました" ;;
    headset_plugged)   z2-media play ;;
    headset_unplugged) z2-media pause ;;
    screen_off)        : # 何もしない例
  esac
done
```

### 5-2. フィールドを使う（電池残量・SSID）

```sh
tail -n0 -F ~/.z2term/events.jsonl | while IFS= read -r line; do
  ev=$(printf '%s' "$line"    | jq -r '.event')
  level=$(printf '%s' "$line" | jq -r '.level // empty')
  ssid=$(printf '%s' "$line"  | jq -r '.ssid  // empty')
  if [ "$ev" = "battery_low" ]; then
    z2-notify "電池注意" "残り ${level}% です"
  fi
  if [ "$ev" = "wifi_connected" ] && [ "$ssid" = "home" ]; then
    z2-volume 60% ; z2-toast "自宅 Wi‑Fi・音量を戻しました"
  fi
done
```

### 5-3. 常駐させる

`⚙設定 → 常駐サーバー` に **起動コマンド**として登録します（例: `sh ~/.z2term/macros/watch.sh`）。
「起動時に自動で常駐」も ON にすれば、アプリを開かず・再起動後も動きます。手元で試すだけなら
ターミナルでそのまま `sh ~/.z2term/macros/watch.sh &` でも可。

### 5-4. 時刻・繰り返し

「毎朝 7 時に」等は **`z2-alarm`** を使います。時刻になると `events.jsonl` に `alarm` が 1 行増えるので、
他のイベントとまったく同じ書き方で拾えます。

```sh
z2-alarm daily 07:00 morning     # 1 回仕掛けるだけ（再起動しても残ります）
```

```sh
# 監視側（常駐サーバーに登録する）
tail -n0 -F ~/.z2term/events.jsonl | while IFS= read -r line; do
  ev=$(printf '%s' "$line"   | jq -r '.event')
  name=$(printf '%s' "$line" | jq -r '.name // empty')
  [ "$ev" = "alarm" ] && [ "$name" = "morning" ] || continue
  z2-say "おはようございます。電池は $(z2-state level) パーセントです"
done
```

distro の cron でも書けますが、**Android が省電力の眠り（Doze）に入ると cron は止まります**。
画面を消していても確実に動かしたいなら `z2-alarm` を使ってください
（そのかわり発火が数分ずれることがあります）。

### 5-5. 実例：ロック解除を N 回失敗したら通知＋位置を記録（盗難対策）

⚙設定「ロック解除の失敗監視」を ON にし、案内に従って**端末管理者**を有効化すると、
解除の失敗が `unlock_failed`（`level` = 連続失敗回数）として流れます。3 回目から反応する例:

```sh
# 監視側（常駐サーバーに登録する）
tail -n0 -F ~/.z2term/events.jsonl | while IFS= read -r line; do
  ev=$(printf '%s' "$line"    | jq -r '.event')
  n=$(printf '%s' "$line"     | jq -r '.level // 0')
  [ "$ev" = "unlock_failed" ] && [ "$n" -ge 3 ] || continue
  ts=$(date '+%F %T')
  z2-notify -h "解除失敗 ${n} 回" "$ts"          # 手元の別端末にも届くよう通知
  echo "$ts unlock_failed x$n" >> ~/theft.log     # 記録を残す
  # 例: 自宅サーバーへ即退避（要 ssh 鍵）
  # scp ~/theft.log backup:/srv/ 2>/dev/null
done
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
# ログ形式・追記方向のどちらにも依存しない。
# 準備: ⚙設定 →「通知検知」ON ＋ OS の「通知アクセス」許可
# 常駐: ⚙設定 → 常駐サーバー に  sh ~/.z2term/macros/otp-clip.sh  を登録

TTL=60                                    # コピーから何秒でクリアするか
POLL=2                                    # 通知ログを見に行く間隔(秒)
KEYWORDS='認証|確認|ワンタイム|コード|パスワード|code|otp|verification|verify|one[- ]?time'

NOTIF=$HOME/.z2term/notifications.jsonl
SNAP=$HOME/.z2term/.otp-clip.snap
WORK=$HOME/.z2term/.otp-clip.work

[ -f "$NOTIF" ] || : > "$NOTIF"

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
  [ -z "$raw" ] && return

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
cp "$NOTIF" "$SNAP" 2>/dev/null || : > "$SNAP"

while :; do
  sleep "$POLL"
  [ -f "$NOTIF" ] || continue

  cn=$(wc -c < "$NOTIF" 2>/dev/null || echo 0)
  pn=$(wc -c < "$SNAP"  2>/dev/null || echo 0)
  [ "$cn" = "$pn" ] && continue           # サイズが同じなら変化なしとみなす

  new=''
  if [ "$cn" -gt "$pn" ] && [ "$pn" -eq 0 ]; then
    # 直前が空 = 全体が新着。(起動時に必ず基準を取るので過去ログの誤発火にはならない)
    new=$(cat "$NOTIF")
  elif [ "$cn" -gt "$pn" ]; then
    diff=$((cn - pn))
    head -c "$pn" "$NOTIF" > "$WORK" 2>/dev/null
    if cmp -s "$WORK" "$SNAP"; then
      new=$(tail -c "$diff" "$NOTIF")  # 前回内容で「始まる」→ 末尾追記(新着が下)
    else
      tail -c "$pn" "$NOTIF" > "$WORK" 2>/dev/null
      if cmp -s "$WORK" "$SNAP"; then
        new=$(head -c "$diff" "$NOTIF")  # 前回内容で「終わる」→ 先頭追記(新着が上)
      fi
      # どちらでもない = 書き換え/掃除。基準を貼り直すだけで発火しない。
    fi
  fi
  # cn < pn (truncate された) も基準の貼り直しだけ。

  cp "$NOTIF" "$SNAP" 2>/dev/null
  handle "$new"
done
```

調整は先頭の `TTL`（クリアまでの秒数）・`POLL`（反応の速さ）・`KEYWORDS`（対応語を追加）だけ。
届いてから最大 `POLL` 秒でコードだけ入るので、入力欄で貼り付けるだけで済みます。

**制約**: 1 回の `POLL` 周期内に通知が 2 件届くと 1 つの塊として扱われます（認証通知が連続する場面は
稀ですが、原理的な穴です）。また通知の本文が OS 側で伏せられている場合（ロック画面で
「プライベートな通知内容は表示されません」と出る設定）は、コードがそもそもログに届きません。

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
> **POSIX sh スクリプト 1 本**を出力してください。制約:
> - トリガーは `~/.z2term/events.jsonl` を `tail -n0 -F` で監視する（過去分は読まない）。
> - イベント種別・フィールドは本ガイド「3. トリガー・リファレンス」にある名前だけを使う。
> - アクションは本ガイド「4. アクション・リファレンス」の `z2-*` だけを使う（存在しない機能は使わない）。
> - JSON 解析は `jq` を第一候補にし、無い場合の sed フォールバックも併記する。
> - 依存パッケージ（jq 等）の導入コマンドと、常駐サーバーへの登録手順をコメントで添える。
> - スクリプトは 1 ファイルで完結させ、各分岐に日本語コメントを付ける。
>
> やりたいこと: 「__ここに自然言語で要望__」（例: 充電を始めたら音量を 3 割にして「充電中」と読み上げ、
> 外したら音量を 7 割に戻す）

AI が「無い機能」を使わないよう、**必ず本ガイドの範囲内で**と明示するのがコツです。

---

## 8. トラブルシュート

- **events.jsonl が増えない** → ⚙設定「システムイベント検知」が ON か。稼働中は常駐通知が出ます。
- **`ssid` が空** → SSID の取得には位置情報権限が要ります（v1 は要求しないので空になることがあります）。接続/切断の検知自体は動きます。
- **`z2-*: cannot write request (storage perm?)`** → アプリのストレージ権限を確認。
- **`z2-media` が効かない** → 直前に再生していたメディアアプリが無いと反応しません（キーを送るだけのため）。
- **`z2-torch` がエラー** → フラッシュ非搭載端末では使えません。
- **常駐が落ちる** → 電池最適化の除外や、常駐サーバーの設定を確認。省電力モード ON 中は画面消灯中の反応が遅れることがあります。
