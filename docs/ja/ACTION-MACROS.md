# Android操作マクロ

作業版 0.8.571-alpha / versionCode 579。**ビルド未検証・実機未確認**。

`z2-action` は、名前付きの操作手順をテキストで保存し、CLI・シェルマクロ・パネル・タイル・`z2-when` から共通実行します。`z2-macro actions` も同じ入口です。パネルの有効化は不要ですが、実行には `z2-key permission` から「z2term Android操作」を有効にする必要があります。

## 保存と編集

以下を任意のUTF-8ファイルへ書き、`z2-action save demo /root/demo.actions` で保存します。`org.example.app` と座標は対象アプリに合わせて変更してください。`z2-app list` で起動可能なパッケージを確認できます。

```text
version=1
timeout=30
screen=current
launch org.example.app
wait 800
tap percent 50 40
wait 300
long-press px 100 300 700
swipe percent 50 75 50 25 450
scroll -600 2000 50 50
command z2-toast "完了"
```

`save NAME -` は標準入力を読みます。保存時に全手順を検査し、成功した場合だけ同名定義を置き換えます。不正な入力は既存の定義を変更しません。`screen=current` は保存時の実画面サイズ・向きへ展開されます。

保存先は共有ホームの `/root/.z2term/actions/NAME.actions` です。`show NAME` で定義を取得して編集し、もう一度 `save` してください。直接ファイルを編集した場合も実行前に再検査します。実行中の定義は開始時の内容で固定され、編集・削除は次回から反映されます。

## コマンド

| コマンド | 動作 |
|---|---|
| `z2-action list` | 保存名を一覧表示 |
| `z2-action show NAME` | 保存済み定義を表示 |
| `z2-action save NAME FILE` | 検査して保存。FILEが `-` なら標準入力 |
| `z2-action delete NAME` | 定義を削除 |
| `z2-action run NAME` | 完了まで待つ。成功は終了コード0、失敗・停止・タイムアウトは非0 |
| `z2-action start NAME` | 実行を要求し、実行IDを返す |
| `z2-action wait RUN_ID` | 指定した実行の完了待ち |
| `z2-action status [RUN_ID]` | JSONで状態・手順番号・結果を取得 |
| `z2-action stop [RUN_ID]` | 残りの手順を停止。ID省略時は現在の実行 |
| `z2-action screen` | `screen=幅x高さ@向き` を取得。向きは0/1/2/3 |
| `z2-action history` | 最近256件までの開始・手順開始・終了記録をJSONで取得 |

同時実行は1本です。別の実行要求で既存の実行を置き換えることはありません。終了結果はプロセス内で直近32実行を保持し、履歴は `/root/.z2term/actions/.history.jsonl` に残します。プロセス終了後は `history` で確認してください。処理の再開・再試行は自動では行いません。

`run` をCtrl+C等で中断すると、その実行IDだけに停止を要求します。`wait` の中断だけでは実行を止めません。通知の「マクロを停止」からも止められます。Androidに送信済みの操作は終了まで残るため、停止が届いた時点で進行中のタッチが最大3秒続く場合があります。

## 定義の書式

ヘッダーの後に1行1手順を書きます。空行と `#` で始まる行は無視します。

| 書式 | 意味 |
|---|---|
| `version=1` | 必須の書式番号 |
| `timeout=30` | 全体の制限時間。秒、1〜300、既定30 |
| `screen=current` | 保存時の画面情報。座標操作・スクロールでは必須 |
| `screen=1080x2400@0` | 画面情報を直接指定 |
| `target PACKAGE` | 後続の座標操作・スクロールが対象とするアプリ。アプリは起動しない |
| `launch PACKAGE` | アプリを起動要求し、後続の対象も切り替える |
| `wait MS` | 0〜30000ミリ秒待つ |
| `key NAME` | back/home/recents/shade/quicksettings/screenshot/split |
| `tap UNIT X Y` | 80ミリ秒のタップ |
| `long-press UNIT X Y MS` | 500〜3000ミリ秒の長押し |
| `swipe UNIT X1 Y1 X2 Y2 MS` | 1〜3000ミリ秒の直線スワイプ |
| `scroll SPEED MS X Y` | 時間指定の自動スクロール。符号付き50〜40000dp/秒、1〜30000ミリ秒。X/Yは対象窓内の10〜90% |
| `command SHELL_TEXT` | 既存Linux実行経路でシェルコマンドを実行し、終了を待つ |

座標のUNITは `px` または `percent` を明示します。画面左上が原点で、百分率は画面全体の0〜100です。`scroll` の位置だけは対象窓内の百分率です。負のスクロール速度で内容を先へ、正で戻る方向へ動かします。実際の移動量は対象アプリに依存します。

タップとスワイプは、対象アプリの可視ウィンドウ内で、キーボードを除いた領域に限定します。画面寸法・向きが保存値と違う場合、対象アプリが前面にない場合、消灯・ロック・ユーザー補助切断では停止します。ユーザー補助を有効にした直後など対象アプリを識別できない場合は、そのアプリへ切り替えてから実行してください。

操作が完了してから次の手順へ進みます。コマンドは終了コード、タッチはAndroidの完了通知を確認します。取消・拒否・完了通知の欠落では後続を実行しません。時間指定スクロールは最後の送信済みスワイプが終了するまで待ちます。`launch` は起動要求の受付までなので、画面が準備できるまでの `wait` を明示してください。

マクロは最大64手順・64KiB、保存数は64件、名前は英数字・`_`・`-` の1〜64文字です。実行中はパネルと取っ手を一時非表示にします。未保存のパネル編集がある場合は実行を断り、保存または取消を先に行います。

## 既存の自動化から呼ぶ

- シェルマクロでは `z2-action run demo` を1行として呼べます。トリガーと条件は引き続き `z2-when` やシェルで作成します。
- 例: `z2-when charge:start if=screen run 'z2-action run demo'`。対象の画面状態を確認できない場合、マクロは失敗として終了します。
- 取っ手のコマンドやパネル項目には `z2-action start demo` を設定できます。項目設定のマクロ一覧からも選択できます。
- タイルには `z2-tile set 1 'z2-action start demo' --off 'z2-action stop' -l 操作` のように開始・停止を割り当てられます。この入切表示はタイル側の記憶であり、マクロの完了状態との自動同期は行いません。`status` と実行通知で確認できます。

現在の段階には、GUIによる手順編集・座標取得、操作キャプチャー、反復、分岐、名前付きマクロの入れ子、UI要素の出現待ちは含みません。既存のシェルマクロから条件付きで呼び出すことはできます。次の拡張は[ロードマップ](AUTOMATION-ROADMAP.md)を参照してください。

Android側の完了通知と能力設定は[AccessibilityService公式仕様](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler))に基づきます。
