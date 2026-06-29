# M2 ハンドオフドキュメント

最終更新: 2026-05-16
バージョン: 0.2.0-alpha (M2: 実用ターミナル化、完了)
**状態: 完了 — 次フェーズは M3**

## このドキュメントの目的

Z2Term Milestone 2「実用ターミナル」完了時点でのスナップショット。
M3 (バックグラウンド維持・代替スクリーン・マルチタブ など) を担当する次フェーズへの引き継ぎ。

## M2 スコープと達成状況

- [x] エミュレータコア (TerminalEmulator / Buffer / Row / Cell / Colors)
- [x] VT100 / xterm 主要エスケープシーケンス対応 (色・カーソル・スクロール領域)
- [x] テーマ 6 種類 (ZTS / Solarized / Dracula / Gruvbox / Nord / Tokyo Night)
- [x] Compose Canvas による独自レンダラ (`TerminalRenderer`)
- [x] TerminalViewModel を新エミュレータに接続
- [x] TerminalScreen を新描画に置換
- [x] 動的端末サイズ (BoxWithConstraints + フォントメトリクス)
- [x] スクロールバック UI (縦ドラッグ + 右端インジケータ + 最下部 FAB)
- [x] 特殊キーバー強化 (Ctrl 系 / Home / End / PgUp/Dn / F1〜F12)
- [x] 設定画面 (テーマ・フォントサイズ・スクロールバック行数、DataStore 永続化)
- [x] コピー & ペースト (全文コピー、クリップボードペースト)
- [x] UTF-8 ストリーミングデコード (日本語・絵文字対応)

未着手 (M3 以降に持ち越し):

- [ ] 代替スクリーン (DECSET 1049 / 47 / 1047) の本実装 — vim/htop の終了時画面復帰
- [ ] 範囲選択モード (タップ&ドラッグでセル選択)
- [ ] IME / 物理キーボードからの直接入力 (現状は入力欄経由)
- [ ] マルチタブ / セッション切替
- [ ] バックグラウンド維持 (foreground service)

## ファイル構造 (M2 完了時点)

```
app/src/main/java/com/zerotoship/z2term/
├── Z2TermApplication.kt
├── MainActivity.kt
├── distro/
│   └── DistroInstaller.kt
├── emulator/                  ← M2 で新設
│   ├── TerminalBuffer.kt       … スクリーン + スクロールバック (リングバッファ)
│   ├── TerminalCell.kt         … セル + SGR 属性ビットパック
│   ├── TerminalColors.kt       … 配色テーブル + 6 テーマ
│   ├── TerminalEmulator.kt     … ANSI パーサ + 状態機械
│   ├── TerminalRow.kt          … 1 行のセル配列
│   └── Utf8Decoder.kt          … バイトストリームの UTF-8 デコーダ
├── proot/
│   └── ProotLauncher.kt
├── pty/
│   └── PtyProcess.kt
├── settings/                  ← M2 で新設
│   └── AppSettings.kt          … DataStore Preferences ラッパー
└── ui/
    ├── settings/              ← M2 で新設
    │   └── SettingsSheet.kt    … ModalBottomSheet 形式の設定 UI
    ├── terminal/
    │   ├── TerminalRenderer.kt … Compose Canvas で TerminalBuffer を描画 (M2 新設)
    │   ├── TerminalScreen.kt   … 画面構成 (TopBar + Canvas + 特殊キー + 入力欄)
    │   └── TerminalViewModel.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## アーキテクチャ概略

```
┌──────────────────────────────────────────┐
│           TerminalScreen (Compose)         │
│  ┌──────────────────────────────────────┐  │
│  │  TerminalRenderer (Canvas)            │  │
│  │     ← reads TerminalEmulator buffer    │  │
│  └──────────────────────────────────────┘  │
│                ▲                            │
│                │ redrawTick / scrollOffset   │
└────────────────┼────────────────────────────┘
                 │
        TerminalViewModel
          ├ TerminalEmulator (state)
          ├ AppSettings (DataStore Flow)
          └ PtyProcess (JNI)
                 │
            forkpty() ─→ /bin/sh (Alpine via PRoot) or /system/bin/sh
```

データ駆動:

1. PTY からバイトを `readLoop` で受信
2. `emulator.processBytes(buf, len)` でバッファ更新
3. `_redrawTick.update { it + 1 }` で recomposition を発火
4. `TerminalRenderer` が最新バッファを Canvas に描画

## SGR 属性ビットパック

```
32bit:
  bit 0-23:  色値 (RGB 24bit or インデックス)
  bit 24-29: 装飾フラグ (bold/italic/underline/blink/inverse/strike)
  bit 30-31: フォーマット (default=0 / indexed=1 / RGB=2)
```

`TerminalRenderer` は同じ fg/bg を持つセルが連続するスパンを 1 回の `drawText`
にまとめて描画する。

## UTF-8 デコード

`Utf8Decoder` は 1 バイトずつ受け取りながら 2/3/4 バイト UTF-8 を組み立てる
状態機械。PTY からの partial read を跨いで継続可能。
- 不正バイトや overlong 表現は U+FFFD で通知
- BMP 範囲外 (4 バイト UTF-8) はサロゲートペアで 2 セル消費

ESC バイトを見ると `Utf8Decoder.reset()` が呼ばれ、エスケープ解析が UTF-8 解析
を上書きしないようにする。

## 設定永続化 (DataStore)

`AppSettings` (`settings/AppSettings.kt`) で以下を永続化:

| キー | 型 | 既定値 | 範囲 |
|---|---|---|---|
| `theme_name` | String | `"ZTS Theme"` | `AvailableThemes` の `name` |
| `font_size_sp` | Float | `13.0` | 8〜32 |
| `scrollback_lines` | Int | `5000` | 500〜50000 |

`TerminalViewModel` は `settingsFlow.collect` で監視し、テーマ変更を
`emulator.colors.applyTheme()`、スクロールバック上限を
`emulator.buffer.scrollbackCapacity` に即時反映する。

## クリップボード連携

- **全文コピー**: TopBar のコピーアイコン → `buffer.getAllText(includeScrollback=true)`
  を `ClipboardManager.setPrimaryClip` でコピー。完了は Toast で通知。
- **ペースト**: TopBar のペーストアイコン → `ClipboardManager.primaryClip` の先頭テキストを
  そのまま PTY に write (UTF-8)。
- 通知は `MutableSharedFlow<String>` (`toastEvents`) で発火し、`TerminalScreen` 側で
  `Toast.makeText` する。

## 既知の制約

1. **代替スクリーン未対応** — vim/htop の終了後に画面が消えない。M3 で
   `TerminalBuffer` を 2 つ持って切替実装予定。
2. **範囲選択未対応** — 現状は全文コピーのみ。長押し → ドラッグでのセル選択 UI を
   M3 で追加予定。
3. **物理キーボード未対応** — Bluetooth キーボード等からの直接入力には未対応。
   入力欄経由か特殊キーバー経由のみ。
4. **CJK の幅** — 全角文字も 1 セル幅扱い。本来は East Asian Width で 2 セル
   消費すべき。M3 以降で対応検討。
5. **bidi (双方向テキスト)** — 非対応 (Termux も基本非対応)。

## M3 へ向けた優先タスク (推奨)

1. **代替スクリーン本実装** — `TerminalBuffer` を `screen` / `altScreen` で持ち、
   `?1049 h/l` で切替。スクロールバックは通常スクリーン側に残す。
2. **バックグラウンド維持 (foreground service)** — `service/` パッケージを新設し、
   PtyProcess を Service にホストして UI から切り離す。
3. **マルチタブ / マルチセッション** — `TerminalViewModel` を複数持てるよう
   `SessionRegistry` を導入。タブバーを Top に追加。
4. **マルチディストロ** — `DistroInstaller` を Ubuntu / Arch / Kali へ拡張。
   設定画面にディストロ切替を追加。
5. **物理キーボード対応** — `Modifier.onKeyEvent` で物理キーを拾い、PTY に
   投げる。Termux 実装が参考になる。

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha-WIP | 2026-05-16 | M2 エミュレータコア完成 (UI 統合は未着手) |
| 0.2.0-alpha | 2026-05-16 | M2 完了 (Renderer/設定/クリップボード/UTF-8 全て実装) |
