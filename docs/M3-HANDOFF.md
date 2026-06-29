# M3 ハンドオフドキュメント

最終更新: 2026-05-17
バージョン: 0.3.0-alpha (M3: 常駐ターミナル化、完了)
**状態: 完了 — 次フェーズは M4 (マルチセッション / IME / フォント等)**

## このドキュメントの目的

Z2Term Milestone 3「常駐ターミナル化」完了時点のスナップショット。
M4 担当者 (マルチタブ / IME 改善 / カスタムフォント / 公開準備など) への
引き継ぎ。

## M3 スコープと達成状況

- [x] 代替スクリーン (DECSET 1049 / 47 / 1047) の完全実装
- [x] 範囲選択モード (長押し → ドラッグ → コピー/キャンセル)
- [x] 物理キーボード対応 (`Modifier.onKeyEvent`)
- [x] フォアグラウンドサービスによるバックグラウンド維持
- [x] TerminalSession を ViewModel から切り出してシングルトン化
- [x] マルチディストロ (Alpine + Ubuntu) と設定画面切替

M3 で着手しなかった (M4 へ持ち越し):

- [ ] マルチタブ / 複数同時セッション
- [ ] East Asian Width (全角文字 2 セル)
- [ ] IME 連動の入力欄改善 (差分検出 → 1 文字単位送信)
- [ ] カスタムフォント同梱 (IBM Plex Mono など)
- [ ] WakeLock / 省電力モード制御
- [ ] 配布 (Play Store / F-Droid)

## アーキテクチャ概略 (M3 版)

```
┌──────────────────────────────────────────────┐
│           TerminalScreen (Compose)             │
│  ┌──────────────────────────────────────────┐  │
│  │  TerminalRenderer (Canvas)                │  │
│  │    + Modifier.onKeyEvent  (物理キー)       │  │
│  │    + detectDragGesturesAfterLongPress      │  │
│  │      (範囲選択)                            │  │
│  └──────────────────────────────────────────┘  │
│                ▲                                │
│                │ 各 StateFlow を再公開            │
└────────────────┼────────────────────────────────┘
                 │
       TerminalViewModel (薄いラッパー)
                 │
       ┌─────────┴─────────────────────┐
       ▼                               ▼
SessionManager (object)        TerminalService
       │                       (foreground notification)
       ▼                               │
TerminalSession ←──────────────────────┘
   ├ TerminalEmulator (state)
   ├ AppSettings (DataStore Flow)
   └ PtyProcess (JNI)
        │
   forkpty() ─→ /bin/sh in chroot via PRoot
```

### ライフサイクル設計

- **SessionManager**: アプリプロセス生存期間でシングルトン
- **TerminalSession**: SessionManager が保持する間、SupervisorJob + Main scope で動作
- **TerminalService**: foreground 通知を表示し、OS による回収から SessionManager を守る
- **TerminalViewModel**: Activity と一緒に生まれ/死ぬ。セッションは破棄しない
- **MainActivity**: 再生成されても VM が新たに SessionManager.get(app) を取り、即座にセッションへ再アタッチ

セッション終了の唯一の正規ルート:

1. 通知の「停止」アクション → `TerminalService.ACTION_STOP` →
   `SessionManager.shutdown()` → PTY 終了 + Service 停止

## エミュレータの拡張ポイント (M3 で追加)

### 代替スクリーン

`TerminalBuffer` が `primary`/`alternate` の二面を持ち、`screen` 変数で
アクティブ側を指す。`primaryActive` フラグで判別。

切替操作:

| 操作 | 効果 |
|---|---|
| `?1049 h` | 現在の cursor + SGR + scroll region を退避、alt をクリアして切替 |
| `?1049 l` | primary に戻し退避値を全復元 |
| `?1047 h/l` | alt クリア切替 / primary 戻り (退避なし) |
| `?47 h/l` | alt にクリアなし切替 / primary 戻り |

`TerminalEmulator` 内に専用退避フィールド (`altSaved*`) を持つ。

### 範囲文字列抽出

`TerminalBuffer.getRangeText(startRow, startCol, endRow, endCol)` を追加。
スクロールバックを含む絶対座標 `0..totalRows-1` を受け付ける。

## 物理キーマッピング (`ui/terminal/PhysicalKeyMapper.kt`)

| キー | 出力 |
|---|---|
| Enter / NumPadEnter | `0x0D` |
| Tab | `0x09` (Shift+Tab は `CSI Z`) |
| Escape | `0x1B` |
| Backspace | `0x7F` |
| Delete | `CSI 3 ~` |
| 矢印 | `SS3 A/B/C/D` |
| Home/End | `CSI H` / `CSI F` |
| PgUp/PgDn | `CSI 5~` / `CSI 6~` |
| F1〜F4 | `SS3 P/Q/R/S` |
| F5〜F12 | `CSI 15~` 〜 `CSI 24~` |
| Ctrl + a〜z | `0x01`〜`0x1A` |
| Ctrl + [ \ ] ^ _ / Space | `0x1B 0x1C 0x1D 0x1E 0x1F 0x00` |
| Alt + 文字 | `ESC + 文字` (meta sends escape) |

`utf16CodePoint` を UTF-8 にして送出するので CJK や記号もそのまま通る。

## フォアグラウンドサービス

- 通知チャンネル `z2term_session` (IMPORTANCE_LOW)
- `foregroundServiceType="specialUse"` 宣言
- 通知タップ: MainActivity を `SINGLE_TOP|CLEAR_TOP` で再表示
- 通知の「停止」: `ACTION_STOP` → `SessionManager.shutdown` → サービス停止

`AndroidManifest.xml` に下記権限とサービス宣言:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".service.TerminalService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="terminal_session_persistence" />
</service>
```

## マルチディストロ

`DistroSpec` (`distro/DistroInstaller.kt`):

| id | displayName | assetArm64 | assetArm | pm |
|---|---|---|---|---|
| `alpine` | Alpine Linux | `alpine-minirootfs-aarch64.tar.gz` | `alpine-minirootfs-armv7.tar.gz` | `apk add` |
| `ubuntu` | Ubuntu | `ubuntu-minirootfs-aarch64.tar.gz` | `ubuntu-minirootfs-armv7.tar.gz` | `apt install` |

設定: `AppSettings.Snapshot.distroId` (DataStore キー `distro_id`、既定 `"alpine"`)
切替操作: 設定画面 → ディストロ → 再起動で反映。

## ファイル構造 (M3 完了時点)

```
app/src/main/java/com/zerotoship/z2term/
├── Z2TermApplication.kt
├── MainActivity.kt
├── core/                          ← M3 で新設
│   ├── SessionManager.kt           … object シングルトン
│   └── TerminalSession.kt          … 状態 + PtyProcess + IO ループ所有
├── distro/
│   └── DistroInstaller.kt          … DistroSpec で多ディストロ対応 (M3 拡張)
├── emulator/
│   ├── TerminalBuffer.kt           … Primary + Alternate (M3 拡張)
│   ├── TerminalCell.kt
│   ├── TerminalColors.kt
│   ├── TerminalEmulator.kt         … ?1049/1047/47 本実装 (M3)
│   ├── TerminalRow.kt
│   └── Utf8Decoder.kt
├── proot/
│   └── ProotLauncher.kt
├── pty/
│   └── PtyProcess.kt
├── service/                       ← M3 で新設
│   └── TerminalService.kt
├── settings/
│   └── AppSettings.kt              … distroId キー追加 (M3)
└── ui/
    ├── settings/
    │   └── SettingsSheet.kt        … ディストロセクション追加 (M3)
    ├── terminal/
    │   ├── PhysicalKeyMapper.kt    … M3 で新設
    │   ├── TerminalRenderer.kt     … 選択オーバーレイ + onCharSizeChanged (M3)
    │   ├── TerminalScreen.kt
    │   └── TerminalViewModel.kt    … 薄いラッパー化 (M3)
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## 既知の制約 / 注意事項

1. **CJK 幅** ・全角文字も 1 セル幅扱い。半角等幅で詰めるため表示が崩れる。
2. **IME 連動** ・現状は不可視の入力欄経由で送信。日本語入力中の差分送出は未実装。
3. **scrollback 共有** ・Alt 画面中はスクロールバックが「凍結」される設計。
   `?1049` で primary に戻ると以前のスクロールバックが復活。
4. **WakeLock** ・宣言済みだが取得していない。長時間バックグラウンドで CPU 使用すると
   端末によっては Doze で kill されうる。M4 でガード追加検討。
5. **Ubuntu rootfs** ・assets に配置が必要。未配置で選択すると展開失敗 → Android sh
   フォールバック。

## M4 へ向けた優先タスク (推奨)

1. **マルチタブ / 複数セッション**
   ・SessionManager を `Map<SessionId, TerminalSession>` に変える
   ・タブバー UI、各タブごとに RowState などを保持
   ・サービスは合計 1 つで複数セッション分の通知をまとめる
2. **East Asian Width 対応** ・CJK を 2 セル幅で書く。`putChar` で `isWide(cp)` を判定し、
   2 セル分カーソルを進める。Renderer 側も連続セルとして扱う。
3. **IME 連動入力**
   ・現状の入力欄を改善するか、Termux 風の不可視 TextField + 差分送出 + key event
     を併用する方式に
4. **カスタムフォント同梱** ・`assets/fonts/IBMPlexMono-Regular.ttf` を入れて
   `FontFamily(Font(R.font.ibm_plex_mono))` を `TerminalFontFamily` の差し替え候補に
5. **公開準備** ・F-Droid のメタデータ、Play Store 登録、署名鍵管理

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha | 2026-05-16 | M2 実用ターミナル化完了 |
| 0.3.0-alpha | 2026-05-17 | M3 常駐ターミナル化完了 |
