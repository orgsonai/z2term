# M4 ハンドオフドキュメント

最終更新: 2026-05-17
バージョン: 0.4.0-alpha (M4: マルチセッション + 国際化対応、完了)
**状態: 完了 — 次フェーズは M5 (配布パイプライン / SSH クライアント等)**

## このドキュメントの目的

Z2Term Milestone 4「マルチセッション + 国際化対応」完了時点のスナップショット。
M5 担当者 (配布 / リモート接続 / ジェスチャ拡張 など) への引き継ぎ。

## M4 スコープと達成状況

- [x] East Asian Width (CJK / 絵文字を 2 セル幅で描画)
- [x] マルチタブ / 複数同時セッション (SessionManager 拡張 + タブバー UI)
- [x] IME 連動入力改善 (TextFieldValue ベース、リアルタイムモード)
- [x] カスタムフォント (assets/fonts 動的読込)
- [x] WakeLock (フォアグラウンドサービス稼働中だけ partial 保持)

M4 で着手しなかった (M5 へ持ち越し):

- [ ] F-Droid / Play Store 配布パイプライン
- [ ] SSH クライアント機能 (リモートシェル)
- [ ] 起動時自動コマンド実行 (init スクリプト)
- [ ] ピンチでフォントサイズ変更等のジェスチャ拡張
- [ ] East Asian Width のアンビギュアス (Ambiguous) 幅切替設定
- [ ] OSC 4 / 10 / 11 / 52 等の追加対応

## アーキテクチャ概略 (M4 版)

```
┌────────────────────────────────────────────────┐
│              TerminalScreen (Compose)            │
│  ┌──────────────────────────────────────────┐    │
│  │ TabBar — sessions / activeId             │    │
│  ├──────────────────────────────────────────┤    │
│  │ key(activeSession.id) {                   │    │
│  │   TerminalRenderer (Canvas)               │    │
│  │     + onKeyEvent + 長押し選択             │    │
│  │     + fontFamily ← assets/fonts          │    │
│  │ }                                         │    │
│  ├──────────────────────────────────────────┤    │
│  │ SpecialKeyBar + InputBar (⚡ realtime)    │    │
│  └──────────────────────────────────────────┘    │
└─────────────────┬──────────────────────────────┘
                  │ flatMapLatest で active flow を購読
        TerminalViewModel
                  │
        ┌─────────┴────────────────┐
        ▼                          ▼
SessionManager.sessions      TerminalService
   ├ List<TerminalSession>      (foreground + WakeLock)
   ├ activeId                       │
   └ open/close/setActive           │
        │                            │
        ▼                            │
   TerminalSession (複数) ←──────────┘
       ├ id / label (StateFlow)
       ├ emulator (TerminalEmulator)
       ├ pty (PtyProcess via JNI)
       └ settings flow (DataStore)
```

### ライフサイクル

- **SessionManager**: プロセスシングルトン。`sessions` リストと `activeId` を持つ
- **TerminalSession**: 各タブの状態 + IO ループ。`SupervisorJob + Main` の独自スコープ
- **TerminalService**: foreground + WakeLock。`SessionManager.shutdown()` で全タブを終了
- **TerminalViewModel**: Activity ライフサイクル。`activeSession` を `combine` で導出、
  内部フローを `flatMapLatest` で再公開
- **MainActivity**: 再生成されても VM が `SessionManager.ensureFirst()` で既存セッションへ即接続

セッション終了は通知の「停止」のみが正規ルート。

## エミュレータの新仕様 (M4 で追加)

### East Asian Width

- `EastAsianWidth.isWide(cp: Int): Boolean` — UTR #11 W/F の主要範囲
- `TerminalCell.wideCont: Boolean` — 右半分セル印
- `TerminalEmulator.putWideChar / putSurrogatePair` で 2 セル消費
- `TerminalRow.toText` / `TerminalBuffer.getRangeText` / Renderer の `extractText`
  すべて `wideCont` セルを文字抽出から除外

ambiguous width (例: 罫線素片) は narrow 扱い。設定での切替は M5 で検討。

## UI の新仕様

### タブバー

`TabBar` Composable (TerminalScreen.kt 内) が `SessionManager.sessions` を購読し、
タブチップ + 新規追加ボタンを表示。

- アクティブタブは `ZtsGreen` ボーダー + 強調色
- ラベルは `TerminalSession.label` flow を購読 (起動完了で `alpine`/`ubuntu`/`sh` へ自動更新)
- 2 つ以上開いている時のみ × ボタン表示
- 最後の 1 つを閉じると `TerminalService.stop()` で前面サービスも停止

### リアルタイム入力モード

`InputBar` を `TextFieldValue` ベースへ書換。

- ⚡ アイコンで toggle (rememberSaveable で永続)
- OFF: 従来通り "入力 → Send で改行込み送出"
- ON:
  - composition (IME 確定前) は preedit として表示維持
  - composition が解消され text が確定したら、自動で `sendInput(text)` + フィールドクリア
  - Send ボタンは非表示 (IME action も Default に切替)

### カスタムフォント

`TerminalFontOptions` に `MONOSPACE / IBM_PLEX_MONO / JETBRAINS_MONO / FIRA_CODE`。

`rememberTerminalFontFamily(option)` が:

1. `assetFile == null` → `FontFamily.Monospace`
2. `assets/fonts/<file>` が存在 → `Typeface.createFromAsset` で読込
3. 例外時 → `FontFamily.Monospace` にフォールバック

設定画面は `TerminalFontOptions.isAvailable()` でグレーアウト判定。

## WakeLock

`TerminalService` が `PowerManager.PARTIAL_WAKE_LOCK` を保持。

- 取得: `startForegroundInternal()` 末尾 (`setReferenceCounted(false)`)
- 解放: `stopSessionAndSelf` / `onDestroy`
- 上限: 8 時間 (`MAX_WAKELOCK_MILLIS`)、超過で自動解放

長時間稼働でも Doze によって kill されにくくなる一方、過剰使用は電池消費に直結。
M5 で「アイドル時に解放」「ユーザ ON/OFF」等の細かい制御を検討。

## ファイル構造 (M4 完了時点)

```
app/src/main/java/com/zerotoship/z2term/
├── Z2TermApplication.kt
├── MainActivity.kt
├── core/
│   ├── SessionManager.kt           … sessions リスト + activeId (M4 拡張)
│   └── TerminalSession.kt          … id/label を追加 (M4)
├── distro/
│   └── DistroInstaller.kt
├── emulator/
│   ├── EastAsianWidth.kt           ← M4 で新設
│   ├── TerminalBuffer.kt
│   ├── TerminalCell.kt             … wideCont フラグ追加 (M4)
│   ├── TerminalColors.kt
│   ├── TerminalEmulator.kt         … putWideChar / putSurrogatePair (M4)
│   ├── TerminalRow.kt
│   └── Utf8Decoder.kt
├── proot/
│   └── ProotLauncher.kt
├── pty/
│   └── PtyProcess.kt
├── service/
│   └── TerminalService.kt          … WakeLock 追加 (M4)
├── settings/
│   └── AppSettings.kt              … fontId キー追加 (M4)
└── ui/
    ├── settings/
    │   └── SettingsSheet.kt        … FontSection 追加 (M4)
    ├── terminal/
    │   ├── PhysicalKeyMapper.kt
    │   ├── TerminalRenderer.kt
    │   ├── TerminalScreen.kt       … TabBar / 実時間入力 (M4)
    │   └── TerminalViewModel.kt    … flatMapLatest 化 (M4)
    └── theme/
        ├── Color.kt
        ├── TerminalFonts.kt        ← M4 で新設
        ├── Theme.kt
        └── Type.kt
```

## 公開準備に向けた事前整理

将来の Play Store / F-Droid 配布に向け、現状の制約と作業項目を列挙。

### 必須対応

| 項目 | 内容 |
|---|---|
| アプリ署名 | Release キーストアを生成 (`keytool -genkey -keystore z2term.keystore`)。`build.gradle.kts` の `signingConfigs` に登録。`keystore.properties` は `.gitignore` 済み。|
| ProGuard ルール | 現状 `isMinifyEnabled = false`。Release で最適化する場合はクラス保持ルールが必要 (`PtyProcess` / `TerminalSession` 等の reflection 利用箇所)。|
| プライバシーポリシー | Play 配布には必須。ローカル PTY のみで通信はしないため "no data collection" 旨で OK。|
| ターゲット SDK | Play は targetSdk 34 以上必須 (2025-08 以降)。現状 35 で OK。|
| 多言語化 | `res/values-ja/strings.xml` は M1 で存在。`values/strings.xml` (英語) との同期確認が必要。|

### F-Droid 向け

- リポジトリのソースのみでビルド可能であること
- Reproducible build のために dependency 固定 (libs.versions.toml で OK)
- assets の rootfs / proot は同梱できないため、F-Droid 版は assets なしバリアントを別 build flavor で提供する案
- メタデータ: `metadata/com.zerotoship.z2term.yml` の作成 (F-Droid Data リポジトリへの PR)

### Play Store 向け

- アイコン 512×512 PNG (既存 mipmap-anydpi-v26 から書き出し)
- スクリーンショット (端末別 16:9 / 9:16 各 2 枚以上)
- 説明文 (短: 80 文字、長: 4000 文字)
- foreground service の利用説明 (Play Policy 2024: foregroundServiceType=specialUse の理由をデベロッパー登録時に説明)

## 既知の制約 / 注意事項

1. **EAW Ambiguous** — 全角／半角どちらでもよい文字 (罫線等) は narrow 扱い。日本語ロケールでは
   本来 wide だが、ユーザ設定として切替対応は未着手
2. **マルチタブの同時 IO** — タブ数増えると read loop が増える。各 4KB バッファ × タブ数。
   実用上は 4 タブまでで動作確認 (推奨)
3. **WakeLock** — 8 時間で自動解放。長時間バックグラウンド作業中に再前面化すると release →
   再 acquire になる (短時間の中断あり)
4. **カスタムフォント** — assets に置いた TTF はビルド時に APK に含まれる。OTF も同じ扱いだが
   `createFromAsset` の対応次第。可変フォント (vf) は非サポート
5. **realtime mode の Backspace** — 入力欄が空の状態で物理 Backspace は onKeyEvent 経由で
   PTY へ届く (DEL 0x7F)。ソフトキーボードの場合は事前に何か文字を入れる必要あり

## M5 へ向けた優先タスク (推奨)

1. **配布パイプライン構築**
   - Release signing 整備、CI で `bundleRelease` 自動化 (GitHub Actions)
   - F-Droid 用 flavor を別 buildType に分割
2. **SSH クライアント機能**
   - JSch / sshd-android などのライブラリ選定
   - 認証情報 (鍵 / パスワード) を Android Keystore で安全に保持
   - PtyProcess 抽象を Local / Remote に二分
3. **OSC 拡張**
   - OSC 4 (palette set) / 10/11 (default fg/bg) / 52 (clipboard) を実装
   - tmux や neovim の高度機能が動くようになる
4. **ジェスチャ拡張**
   - ピンチでフォントサイズ変更 (`Modifier.pointerInput` + transformGesture)
   - 二本指スクロールで端末スクロール vs スクロールバック分離
5. **East Asian Width 切替**
   - 設定に "Ambiguous は wide 扱い (CJK ロケール向け)" トグルを追加
   - `EastAsianWidth.isWide` を `isWide(cp, ambiguousAsWide: Boolean)` に拡張

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha | 2026-05-16 | M2 実用ターミナル化完了 |
| 0.3.0-alpha | 2026-05-17 | M3 常駐ターミナル化完了 |
| 0.4.0-alpha | 2026-05-17 | M4 マルチセッション + 国際化対応完了 |
