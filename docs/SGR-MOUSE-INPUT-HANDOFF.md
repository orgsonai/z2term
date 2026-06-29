# SGR mouse 入力 (タッチ→マウスイベント変換) 実装依頼

## ⚠ 優先度: 最優先 (Kitty graphics 等より先決)

mouse capture を要求する TUI 系アプリ (日付選択 pane を持つ日記/カレンダー系、 ファイラ系、 副 pane フォーカス切替を伴うもの 等) は **マウス/タップ操作が無いとそもそも何も動かせない**。 具体的な操作例:

- 別の日付/別ノードに移動する (日付選択 pane や tree の上の **タップ**)
- フォーカス枠を切り替える (複数 pane 間の **タップ**)
- 副 pane のリスト項目を開く (関連エントリ pane / 履歴 pane の **タップ**)
- 添付/結果一覧をスクロール / 選択 (リストの **タップ + スワイプ**)
- 本文/編集領域の任意位置にカーソルを置く (Body 内の **タップ**)
- 本文を上下に流す (**二本指スワイプ**)

これらが **全部死んでいる** 状態。 キーボードショートカット (Ctrl+G で goto、 矢印で移動、 Tab で枠切替) は一部代替できるが、 Z2Term 上のソフトキーボードで Ctrl 修飾を出すこと自体が面倒なため、 実用上は「TUI として使えない」状態になる。

一方で Kitty graphics (画像表示) は「添付のプレビューが見られるかどうか」のオプション機能で、 無くても操作はできる。 **順序として SGR mouse 入力 → Kitty graphics の方が正しかった**。 今からでも最優先で着手したい。

## 概要

対象 TUI 側でマウス操作 (リスト/カレンダー上のクリック / フォーカス枠切替 / 項目選択 / ホイールスクロール / Body カーソル位置決め) は実装済みだが、 Z2Term 上では一切反応しない。 原因は **Android タッチイベントを SGR mouse プロトコル (`?1006`) シーケンスとして TUI の stdin に流す経路が実装されていない** ため。 これを追加する依頼。

Kitty graphics protocol の `t=f/t=t/t=s` 対応 (commit `3377d39`, 0.8.136) と同じ「opt-in トグル」で段階導入できると安全。

## 前回の経緯整理 (誤読防止)

以前のやり取りで「端末は SGR `?1006`/`?1015` を honor し SGR 形式で送出」と返答済みだが、 実機検証で `\x1B[<n;x;yM` が文字漏れすることを TUI 側 termios dump + `libc::read` probe で確認している。

現状の整理:
- **物理マウスを繋いだ場合の挙動は未検証** — 前回の返答で OK だったのはこのパスのみだった可能性が高い。
- **画面タップは TUI に届かない** — Android のタッチイベントを `\x1b[<...M` に変換する経路自体が無い。
- 「文字漏れ」と「届かない」が同居しているのは、 SGR mouse を **enable した状態 (TUI 側が `?1006h` を送った後) で物理タップが来ると、 SGR シーケンスではなく生の Android タッチが意図せず stdin に何か (画面のテキスト選択や別経路の出力) を流している** のが疑われる。

つまり本依頼の本筋は **「画面タップを SGR mouse シーケンスに変換して PTY に書き出す機能の新規追加」** で、 前回依頼の延長線ではなく新規ピース。

## 期待動作

TUI 側が `\x1b[?1006h \x1b[?1002h \x1b[?1000h` (SGR mouse + button-event tracking + normal mouse) を送って mouse capture を有効化した状態で、 ユーザが画面をタップ / ドラッグ / スクロールしたら、 以下のバイト列が PTY の master に書き込まれて TUI の stdin から読める。

### シーケンス仕様 (DEC SGR `?1006`)

```
押下 (press)  : ESC [ < <button> ; <col> ; <row> M     ※終端は大文字 M
離す (release): ESC [ < <button> ; <col> ; <row> m     ※終端は小文字 m
```

- `<col>` / `<row>` は **1-origin** (左上 = `1;1`)
- `<button>` は下記表のとおり

| ジェスチャ | button | terminator | 備考 |
|---|---|---|---|
| シングルタップ (1指) | 0 | M → m (連続発行) | 左クリック相当。 down と up を両方送る |
| ロングタップ (1指長押し) | 2 | M → m | 右クリック相当 (オプション。 未対応でも可) |
| ドラッグ (1指 押下移動) | 32 | M | 移動中の押下継続。 同じ button + 終端 M を座標違いで連発 |
| 二本指スワイプ↑ (or ピンチ縮小) | 64 | M | ホイール上 |
| 二本指スワイプ↓ (or ピンチ拡大) | 65 | M | ホイール下 |
| 二本指タップ | (任意) | — | 未対応でも可 |

例 (タップ → 即離し、 画面の col=20, row=10 セル):
```
\x1b[<0;20;10M\x1b[<0;20;10m
```

例 (二本指↓スワイプ 3 連発、 col=40, row=15):
```
\x1b[<65;40;15M\x1b[<65;40;15M\x1b[<65;40;15M
```

### セル座標への変換

タッチの物理 px 座標を、 現在のフォントメトリクスで **セル座標** に変換してから送る:

```
col = floor(touch_x_px / cell_width_px)  + 1
row = floor(touch_y_px / cell_height_px) + 1
```

`SIGWINCH` (画面回転 / フォントサイズ変更) で再計算が必要。 Z2Term は既にセル単位のレンダリングを持っているのでメトリクスは流用可能。

### 有効化シーケンスの honor

TUI が以下を送ったら mouse capture **有効**:
- `\x1b[?1000h` — normal mouse (press/release のみ)
- `\x1b[?1002h` — button-event tracking (press/drag/release)
- `\x1b[?1003h` — any-event tracking (motion 含む。 主要 TUI 群は使わないが対応 nice-to-have)
- `\x1b[?1006h` — SGR encoding (これが無い場合は legacy `\x1b[Mxxx` 3 バイト形式。 主要 TUI 群は SGR 前提でいい)

TUI が以下を送ったら **無効**:
- `\x1b[?1000l \x1b[?1002l \x1b[?1003l \x1b[?1006l`

無効中はタップイベントを TUI に流さず、 Z2Term 自身のテキスト選択 / メニュー操作に使ってよい。

## 設計上の要望

### 1. opt-in トグル (Kitty graphics と同じ運用)

「設定 → 実験的 / 開発者向け → **SGR mouse 送出 (タッチ→マウスイベント変換)**」トグルを追加。 既定 **OFF** (現在の挙動)、 有効時に説明文:

> 画面タップを TUI 側のマウスイベントに変換して送信します。 一部の TUI (カレンダー選択や ファイラのクリック等) で必要。 長押しや二本指スワイプは右クリック / ホイールスクロールとして扱われます。 OFF 時は従来通りタップは Z2Term 自身の操作 (テキスト選択など) に使われます。

トグルは即時反映 (再起動不要)。 Kitty graphics トグルと同じ層に並べてよい。

### 2. TUI 無効化中はパススルー

`?1000l` 等で TUI 側が mouse capture を無効化したら、 Z2Term の元来のタップ動作 (テキスト選択 / 長押しメニュー等) に戻すこと。 常時 SGR 送出にすると bash や `less` ですら通常のテキスト選択ができなくなる。

### 3. 排他: Z2Term の UI を握るジェスチャ

- 画面端からのスワイプ (アプリ側のサイドメニューやキーボード呼び出し用) は引き続き Z2Term が握ってよい。 TUI に送る必要はない。
- 二本指長押し or 三本指タップなどに **「TUI mouse 強制 OFF」のエスケープハンドル** を残しておくと、 TUI が freeze したときに mouse capture から抜けられて便利。

### 4. 座標オーバーフロー

SGR は座標を 10 進 ASCII で送るので、 横 999 / 縦 999 まで安全。 Z2Term の最大セル数で問題なし。

## TUI 側の準備状況 (依頼元)

- mouse event を受け取る経路は実装済 (主要 TUI ライブラリ側の `handle_mouse` 相当)。
- `EnableMouseCapture` は alt screen 突入時に送信、 環境変数 (例: `<TUI>_NO_MOUSE=1` 形式) で抑止可能 (Z2Term 用フォールバック)。
- リストやカレンダー上のクリック、 副 pane の行選択、 本文の caret 位置決め、 ホイールスクロール (各 pane) すべて実装済。
- 今回 Z2Term 側で SGR mouse 送出が動けば、 抑止フラグ抜きで上記すべてがそのまま動く。

## 動作確認方法

### 1. 端末側の単体確認

Z2Term の任意のシェルで以下を実行 (TUI 起動なし):

```sh
printf '\x1b[?1006h\x1b[?1002h\x1b[?1000h'   # mouse capture 有効
cat | od -An -c -tx1                          # tap して観測
```

画面タップで `033 [ < 0 ; <col> ; <row> M ...` が見えれば OK。 `Ctrl-C` で抜けたあと:

```sh
printf '\x1b[?1000l\x1b[?1002l\x1b[?1006l'   # mouse capture 無効に戻す
```

### 2. 対象 TUI での確認

mouse event ログをファイルに出すモード (TUI 側のデバッグオプション) で起動し、 カレンダー / リスト上の位置をタップして以下が出れば成功:

```
Mouse(kind: Down(Left), column: <c>, row: <r>, ...)
Mouse(kind: Up(Left),   column: <c>, row: <r>, ...)
```

そのまま TUI 側の選択状態がタップした位置に切り替わるはず。

二本指↓スワイプで:

```
Mouse(kind: ScrollDown, column: ..., row: ..., ...)
```

が連発され、 フォーカスされた pane のスクロールが効けば OK。

## 参考実装

- Termux (Android terminal) の SGR mouse 実装: `TerminalEmulator.sendMouseEvent` 系。 Termux/termux-app の MouseTracker / TerminalView を参照。
- xterm の `ctlseqs.txt` の "Any-event tracking" / "SGR (1006) mouse" 節。
- TUI ライブラリ側 parser: `parse_sgr_mouse` 相当 (Rust 系なら crossterm の `event/sys/unix/parse.rs`)。

## 想定スコープ外 (今回は不要)

- bracketed paste (`?2004`) — テキスト貼り付け検出。 別マターで必要になったら別依頼。
- focus in/out reporting (`?1004`) — alt-tab 検出。 同上。
- urxvt 1015 形式 — SGR 1006 だけで十分 (主要 parser も 1006 優先)。

## Z2Term 側の現状 (調査の起点)

- **出力 (TUI → screen)** 方向の SGR mouse 漏れは 0.8.127 で DCS/APC/PM/SOS を `State.STRING` で吸収して止めた。
- **マウス/ホイール送出** は wheel 部分 (`button = 64/65`) のみ実装あり: `TerminalInputView` で alt screen / primary screen の方向と scrollback 位置で振り分け、 `mouseEnabled` 中だけ `\x1b[<64;col;row>M` / `\x1b[<65;col;row>M` を送る (0.8.116 / 0.8.119 / 0.8.124 / 0.8.126)。
- **シングルタップ / ロングタップ / 1 指ドラッグ** を `button 0` / `button 2` / `button 32` の SGR mouse として PTY master に書き出す経路は **未実装**。 本依頼の本筋はこの 3 種の追加。
- `mouseEnabled` フラグ自体は `?1000`/`?1002`/`?1003`/`?1006` の DEC private mode を受けて立つはずなので、 既存パーサに合流する形で送出ロジックを追記すれば筋。
