# z2term マクロガイド（z2term-macro）

**スマホの操作を自動化する「マクロ」を、z2term のターミナルだけで組むための仕様書。**
人間が読んでそのまま書ける説明書であると同時に、**AI に丸ごと読ませて「〜したい」と頼めば
マクロ用のスクリプトを生成できる**ように、機械可読なリファレンスを兼ねています。

> 対象バージョン: 0.8.154-alpha 以降 / 英語版: `docs/en/MACRO-GUIDE.md`
> すべて **非 root・完全ローカル・外部送信なし**。難しい権限が要る機能は含みません。

---

## 1. 考え方（3 段構成）

z2term のマクロは MacroDroid 等と同じ「**トリガー → 判断 → アクション**」で組み立てます。

| 段 | 誰が | z2term では |
|---|---|---|
| **トリガー** | Android → シェル | システムイベントを `~/.z2term/events.jsonl` に追記（充電/画面/ロック/Wi‑Fi/ヘッドセット/機内/マナー等）。通知は `~/.z2term/notifications.jsonl` |
| **判断（ロジック）** | シェル | ログ行を読んで条件分岐（`if`・時刻・回数・状態ファイル…）。ふつうの sh/awk/jq で自由に |
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

---

## 3. トリガー・リファレンス（events.jsonl）

- 場所: `~/.z2term/events.jsonl`（1 行 = 1 イベントの JSON。追記のみ）。
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
| `airplane_on` / `airplane_off` | 機内モード ON / OFF | — |
| `ringer_normal` / `ringer_vibrate` / `ringer_silent` | マナーモード 切替 | — |

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
| `z2-notify` | `z2-notify "タイトル" "本文"` / `z2-notify "本文"` | 通知を出す | — |
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

### 5-4. 時刻・繰り返し（トリガー不要のもの）

「毎朝 7 時に」等は events を使わず cron で。`crontab -e` に
`0 7 * * * z2-say "おはよう。今日の予定を確認して"` のように書きます（cron の導入は distro ごと）。

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
