# GUI 完全再構築ハンドオフ

最終更新: 2026-05-18
状態: **GUI 全削除済み、未実装。コア (PRoot/SSH/emulator) は健在。**

## 背景

M1〜M6 で構築した Compose ベース GUI (TerminalScreen / TerminalRenderer /
TerminalViewModel / InputBar / SettingsSheet / SshProfilesSheet 等) は、
複数イテレーションで以下が積み重なって UX が破綻した:

- 入力レイヤ: Compose `BasicTextField` で realtime PTY 入力を実装した結果、
  日本語 IME と画面状態が同期しなくなり、改行混入・列ズレが大量発生
- 入力レイヤを `AndroidView` + `BaseInputConnection` に作り替えた版でも、
  カーソル位置と入力文字数が一致しない症状が残った
- IME 開閉でターミナルプロンプトが画面外にクリップされる
- 「最新行に張り付き」「手動スクロール時の固定」の挙動が直感に合わない
- 全体として TopBar / TabBar / InputBar / SpecialKeyBar が UX 上の役割を
  正しく分担できておらず、Konsole 等の確立されたターミナル UX と乖離

部分修正で押し戻すたびに別の症状が出るため、**GUI を全削除して仕様から
書き直す**方針に転換。本ドキュメントが新規実装時の唯一の参照仕様。

## 削除済み (再実装で復元する対象)

```
app/src/main/java/com/zerotoship/z2term/
├── MainActivity.kt              ← プレースホルダに置換済 (未実装表示)
└── ui/
    ├── terminal/                ← 全削除
    │   ├── TerminalScreen.kt
    │   ├── TerminalRenderer.kt
    │   ├── TerminalViewModel.kt
    │   ├── PhysicalKeyMapper.kt
    │   └── input/
    │       ├── TerminalInputView.kt
    │       ├── TerminalInputConnection.kt
    │       └── AndroidKeyMapper.kt
    ├── settings/SettingsSheet.kt    ← 削除
    ├── ssh/                          ← 削除
    │   ├── SshProfilesSheet.kt
    │   └── HostKeyVerificationDialog.kt
    └── components/                   ← 削除
```

## 保持資産 (再利用前提)

### コア層
- `emulator/` — VT100/xterm 互換エミュレータ (CSI/OSC/SGR/UTF-8/EAW 完備)
- `channel/` — LocalPty / SSH (JSch) / known_hosts / Keystore 暗号化
- `core/SessionManager.kt`, `core/TerminalSession.kt` — タブ複数化、PTY
  ループ、redrawTick coalescing、scrollback delta による視点固定
- `proot/ProotLauncher.kt` — Termux 非依存 PRoot 起動、ENV/PS1 補正
- `distro/` — assets / runtime DL の両対応 distro 配置
- `service/TerminalService.kt` — フォアグラウンドサービス
- `settings/AppSettings.kt` — DataStore Preferences
- `pty/PtyProcess.kt` — JNI PTY (forkpty + setsid + ioctl)

### デザイントークン (ZTS spec)
- `ui/theme/Color.kt` — ZTS グリーン (#22C55E) + 黒系背景 + ANSI 16 色
- `ui/theme/Theme.kt` — Material3 theme wrapper
- `ui/theme/Type.kt` — タイポグラフィ
- `ui/theme/TerminalFonts.kt` — JetBrains Mono / IBM Plex / 等の同梱フォント

## 再構築の要件

### A. UX: Konsole 風

KDE Konsole (https://konsole.kde.org/) の使用感を Android タブレット/
スマートフォンに移植する。最重要項目:

1. **タブ複数化**
   - 上部にタブストリップ。タブ毎に独立した PTY セッション。
   - 新規タブ追加 (+ ボタン)、閉じる、ドラッグ並び替え、長押しでリネーム。
   - タブのアイコン / カラーで「ローカル」「SSH」「PRoot 上のディストロ」を視覚区別。

2. **プロファイル**
   - 接続先設定 (SSH ホスト、ローカルシェル、ディストロ選択、init コマンド、
     色テーマ、フォントサイズ等) を保存して再利用。
   - 起動時に最後のセッションを復元するオプション。

3. **スクロールバック閲覧**
   - 指で上にスワイプ = スクロールバック表示。下にスワイプ = 戻る。
   - 慣性スクロール対応。
   - スクロール中は自動追従を解除、最下端ボタンを薄く表示。
   - PTY が出力しても視点固定 (現状コアの scrollback delta ロジックを使う)。

4. **ピンチでフォント拡縮**、2 本指縦パンでスクロールバック。

5. **テキスト選択 + コピー**
   - 長押しで開始、ドラッグで範囲、フローティングアクションバーでコピー。
   - ダブルタップで単語選択、トリプルタップで行選択 (Konsole と同じ)。
   - OSC 8 ハイパーリンクはタップで開く (長押し選択と排他)。

6. **特殊キーバー (ソフトキー)**
   - 画面下端、IME 表示時は IME の直上に押し上げる。
   - 必須: ESC / TAB / Ctrl / Alt / 矢印 / Enter / Ctrl+C・D・L・Z 等。
   - 折り畳み式 (▸ 展開で F1-F12 / Home / End / PgUp/PgDn)。
   - 「Ctrl 押下中」をトグルできる sticky モディファイア。
   - 物理 BT キーボード接続時は自動で非表示にしてもよい。

7. **検索**
   - スクロールバック内をインクリメンタル検索、一致をハイライト + 次へ/前へ。

8. **クリップボード**
   - OSC 52 でリモート側 → ローカルクリップボードへの貢納。
   - 全文コピー、選択コピー、ペースト (右上アクション or 右クリック相当)。

9. **設定画面**
   - 色テーマ (ZTS Dark / Solarized / Tango / 任意自作)
   - フォント (同梱 + システム)
   - フォントサイズ (8〜32sp、ピンチでもリアルタイム連動)
   - scrollback 行数 (1000〜100000)
   - East Asian ambiguous 全角扱い
   - distro 切替 (Alpine / Ubuntu 等)
   - 初回起動コマンド
   - SSH プロファイル管理 (CRUD + テスト接続)
   - known_hosts 管理 (一覧 + 削除)

10. **ホスト鍵検証ダイアログ**
    - 新規ホストの fingerprint + 種別 (ed25519/rsa/...) を表示
    - 「信頼して保存」「今回限り接続」「キャンセル」の 3 択
    - 鍵変更検出時は警告色 + 強い確認文言

11. **キーボードショートカット** (物理キー接続時)
    - Ctrl+Shift+T: 新規タブ
    - Ctrl+Shift+W: タブを閉じる
    - Ctrl+Shift+C / V: コピー / ペースト
    - Ctrl+Shift+F: 検索
    - Ctrl+PgUp / PgDn: タブ切替

### B. デザイン: ZTS 仕様

`ui/theme/Color.kt` のトークン:

| 用途 | カラー |
|------|--------|
| ZTS グリーン (アクセント) | `#22C55E` |
| ZTS グリーン bright | `#4ADE80` |
| ZTS グリーン dim | `#16A34A` |
| 背景 primary | `#0A0A0A` (ほぼ黒) |
| 背景 secondary (パネル/タブ) | `#171717` |
| 背景 card | `#1F1F1F` |
| 罫線 | `#2A2A2A` |
| テキスト primary | `#FAFAFA` |
| テキスト secondary | `#A3A3A3` |
| テキスト tertiary | `#737373` |
| ステータス error | `#EF4444` |
| ステータス warning | `#F59E0B` |
| ステータス info | `#3B82F6` |
| ANSI 16色 | `Ansi*` 定数群 |

レイアウト原則:
- **エッジ to エッジ**、TopBar は半透明 or 薄い罫線で本体と区切る
- 角丸 8dp が標準 (タブ、チップ、ボタン、ダイアログ)
- 罫線は常に 1dp、`ZtsBorder`
- アクセント色は ZTS グリーン 1 系統のみ。重要度に応じて bright/dim
- 等幅フォントは `ui/theme/TerminalFonts.kt` の同梱フォントを使う

### C. 入力レイヤ (最重要 / 過去 8 イテレーション失敗の罠)

これは絶対に踏むな:
- ❌ `BasicTextField` で realtime PTY 入力
- ❌ `TextFieldValue` の `composition` を解釈
- ❌ `KeyboardActions.onSend/onGo/onDone/onNext/onSearch` を複数同時定義
- ❌ フィールドを `""` に強制リセット
- ❌ `KeyboardOptions.imeAction` を Send/Go/Done で気軽に切替

正しい設計:
- `AndroidView` 内に独自 `View` を埋める
- `onCheckIsTextEditor` = true、`onCreateInputConnection` で
  `BaseInputConnection(this, fullEditor = true)` 派生を返す
- `outAttrs.inputType = InputType.TYPE_NULL` (アプリ側はテキストを管理しない宣言)
- `outAttrs.imeOptions` に `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN | IME_ACTION_NONE`
- InputConnection override は Termux 方式:
  - `setComposingText`: `super` 委譲 (Editable に preedit を保持)
  - `commitText`: `super` で Editable 確定 → 全内容を PTY 送出 → `editable.clear()`
  - `finishComposingText`: 同上
  - `deleteSurroundingText`: `super` + 0x7F を beforeLength 回送出
  - `sendKeyEvent`: Android KeyEvent → 自前マッパで PTY バイト
- 物理キーは `View.onKeyDown` で同じマッパに流す
- Compose 側からのタップは `inputView.requestFocus() + imm.showSoftInput()` で起動

### D. レンダリング (描画の罠)

- TerminalRenderer は `BoxWithConstraints` で利用領域 → rows/cols を逆算 → `session.onResize()`
- **過渡状態の最下端固定**: `emulator.resize` は非同期。`buffer.rows != canvasRows`
  の数フレームでも「最新行が常に canvas 下端」になるように、絶対行ベースで描画:
  ```
  bottomAbsRow = scrollbackSize + buffer.rows - 1 - scrollOffset
  topAbsRow    = bottomAbsRow - canvasRows + 1
  ```
- カーソルも絶対行で計算、画面外なら描画しない
- 同属性連続セルは 1 回の `drawText` でまとめる (パフォーマンス)
- 全角文字は `cell.wideCont` をスキップして 1 文字 → 2 セル幅で描画
- スクロールバー: 履歴 > 0 なら右端に 3px 幅、現在位置インジケータ

### E. ライフサイクル / バック / IME

- `windowSoftInputMode = adjustResize` のまま (Manifest は既にこの設定)
- IME 表示で content が再 layout → onResize が走る → emulator.resize 非同期発火
- 戻るキー: スクロールバック中なら最下端に戻る、それ以外はアプリ終了確認
- アプリ復帰時にコアの redrawTick が増えていれば再描画

## コア API リファレンス (UI 実装者向け)

```kotlin
// セッション
SessionManager.ensureFirst(application)         // 起動時 1 度
SessionManager.openNew(context): TerminalSession
SessionManager.setActive(id: String)
SessionManager.close(id: String)
SessionManager.sessions: StateFlow<List<TerminalSession>>
SessionManager.activeId: StateFlow<String?>

// セッション操作 (TerminalSession)
session.startTerminal(distro: DistroSpec? = null)
session.startSsh(profile: SshProfile)
session.writeBytes(bytes: ByteArray)            // PTY 入力。scrollOffset=0 に自動 reset
session.onResize(rows: Int, cols: Int)
session.scrollBy(delta: Int)                    // 正 = 上方向 (過去)
session.setScrollOffset(offset: Int)
session.jumpToBottom()
session.clearOutput()
session.restart()
session.shutdown()
session.copyAllToClipboard() // ※ ViewModel 側のヘルパに移植要

// 観測
session.uiState: StateFlow<UiState>            // state + mode
session.redrawTick: StateFlow<Int>             // 描画リクエスト
session.scrollOffset: StateFlow<Int>           // 0=最下端、>0=スクロール中
session.toastEvents: SharedFlow<String>
session.settingsFlow: StateFlow<AppSettings.Snapshot>
session.label: StateFlow<String>
session.cwd: StateFlow<String>                  // OSC 7 で更新

// エミュレータバッファ (描画ループから読む)
session.emulator.buffer.rows / columns / scrollbackSize / totalRows
session.emulator.buffer.getRow(absRowIndex): TerminalRow
session.emulator.cursorRow / cursorCol / cursorVisible
session.emulator.colors                        // テーマカラー
session.emulator.cursorKeyBytes(CursorKey)     // 矢印キーのバイト列

// 特殊キー → bytes 変換 (旧 SpecialKey enum を再実装する場合)
//   ENTER 0x0D, TAB 0x09, ESC 0x1B, BS 0x7F,
//   Ctrl+A=0x01, Ctrl+C=0x03, Ctrl+D=0x04, ...
//   矢印は emulator.cursorKeyBytes() でモード依存
//   F1-F12 / Home / End / PgUp / PgDn は CSI / SS3 系
```

## 注意 (再構築時の Gotcha)

`memory/gotcha_*.md` で蓄積済み:
- AGP 9.1.1 + Kotlin: `kotlin-android` プラグイン併用不可、`kotlinOptions` 廃止
- JNI: `@JvmStatic external` in companion は外側クラス名で export
- KDoc: `/*` を含む文字列が KDoc 内に書けない (回避: ` *.ttf` → `*.ttf`)
- Compose `BasicTextField` realtime IME 不可 (上記 C 参照)

## 受入基準 (再構築完了時に通すべき)

- [ ] アプリ起動 → 最初のセッションが Alpine もしくは sh で立ち上がる
- [ ] `ls -la` を打って Enter → 1 行で `$ ls -la` 表示、出力がスクロール
- [ ] 日本語 IME で「あいうえお」を変換確定 → 1 度だけ送られる
- [ ] BS / TAB / ESC / 矢印 / Ctrl+C 全部動く
- [ ] 物理 BT キーボードでショートカット動作
- [ ] タブを 3 つ作って各々独立に動作、切替時に画面が再構築される
- [ ] スクロールバック上端まで遡って下に戻れる、最下層ボタンで一発復帰
- [ ] PTY 出力中に手動スクロール → 視点固定、新規行は背後で増える
- [ ] IME 開閉でプロンプトが画面外にクリップされない
- [ ] SSH プロファイル作成 → 公開鍵生成 → 接続成功
- [ ] ホスト鍵検証ダイアログが新規接続時に出る、保存後は再表示されない
- [ ] OSC 7 で cwd 反映、OSC 8 リンクがタップで開く、OSC 52 がペーストボードへ
- [ ] ピンチでフォント拡縮、設定画面でも反映
- [ ] テーマ切替が即時適用

## 補足: 旧設計の反省点 (繰り返さない)

1. **入力レイヤを Compose 標準 widget に乗せた** → 同期破綻
2. **ボトム入力ボックスを画面下に設置した** → IME と二段重ねで UX 不良
3. **「リアルタイム送信」と「Enter で送信」のトグル機能** → ユーザーの心理モデルが分裂
4. **TopAppBar に Copy/Paste/Clear/Settings/Restart/SSH を全部詰めた** → タップ目標が小さく事故多発
5. **JumpToBottom ボタンを `scrollOffset > 0` だけで出現させた** → ユーザーは「いつ消えるか」を学習できない
6. **テーマ切替を即時適用するが、選択中であることのプレビューがない** → 試行錯誤しづらい
7. **エラーバナーとプロンプト出力を同じ ANSI 文字流に混ぜた** → 区別がつかない

Konsole は「タブ」「ターミナル領域」「設定はメニューバー奥」「ステータス
バー」の明快な役割分担で UX が成立している。Z2Term もこの分業を踏襲する。
