# M5 ハンドオフドキュメント

最終更新: 2026-05-17
バージョン: 0.5.0-alpha (M5: SSH + ジェスチャ + 配布準備、完了)
**状態: 完了 — 次フェーズは M6 (SSH 強化 / FOSS バリアント / OSC 7・8 等)**

## このドキュメントの目的

Z2Term Milestone 5「SSH + ジェスチャ + 配布準備」完了時点のスナップショット。
M6 担当者 (公開鍵認証 / known_hosts / FOSS ビルド / OSC 拡張 / ポート転送) への引き継ぎ。

## M5 スコープと達成状況

- [x] OSC 4 / 10 / 11 / 12 / 52 の本実装 (palette / default / clipboard)
- [x] East Asian Width の Ambiguous 切替設定
- [x] ピンチでフォントサイズ + 二本指縦パンでスクロール
- [x] SSH クライアント基礎 (JSch、パスワード認証、プロファイル管理 UI)
- [x] 配布パイプライン (GitHub Actions CI / Release signing / ProGuard / F-Droid metadata)

M5 で着手しなかった (M6 へ持ち越し):

- [ ] SSH 公開鍵認証 + Android Keystore でのパスワード暗号化
- [ ] known_hosts 永続化 + ホスト鍵検証 UI
- [ ] FOSS ビルドフレーバー (assets 抜き F-Droid 版)
- [ ] OSC 7 (current dir) / OSC 8 (hyperlinks)
- [ ] ポートフォワーディング (-L / -R)
- [ ] 起動時自動コマンド (init script)

## アーキテクチャ概略 (M5 版)

```
┌─────────────────────────────────────────────────┐
│              TerminalScreen (Compose)             │
│  ┌────────────────────────────────────────────┐   │
│  │ TabBar — sessions / activeId                │   │
│  ├────────────────────────────────────────────┤   │
│  │ TerminalRenderer (Canvas)                   │   │
│  │   + onKeyEvent                              │   │
│  │   + detectTransformGestures (pinch / pan)   │   │
│  │   + 長押し選択                              │   │
│  ├────────────────────────────────────────────┤   │
│  │ SpecialKeyBar + InputBar (⚡ realtime)       │   │
│  └────────────────────────────────────────────┘   │
│        SshProfilesSheet  /  SettingsSheet         │
└──────────────────┬────────────────────────────────┘
                   │
        TerminalViewModel
                   │
        ┌──────────┴───────────────────┐
        ▼                              ▼
SessionManager.sessions          TerminalService
        │                       (foreground + WakeLock)
        ▼                               │
   TerminalSession ←───────────────────┘
       ├ emulator (TerminalEmulator)
       ├ ProcessChannel ← LocalPtyChannel / SshChannel (M5)
       └ settings (DataStore Flow)
                  │
        ┌─────────┴────────┐
        ▼                   ▼
   PtyProcess (JNI)    JSch.Session
```

### 通信チャンネル抽象 (M5 で追加)

`channel/ProcessChannel.kt` を中心に、

| 実装 | 用途 |
|---|---|
| `LocalPtyChannel` | `PtyProcess` を包む。forkpty 経由のローカル PRoot シェル |
| `SshChannel` | JSch (mwiede fork) で xterm-256color shell を開く |

TerminalSession は `ProcessChannel?` を持ち、readLoop はインターフェース越しに動作。
両者の使い分けは `startTerminal()` (Local) と `startSsh(profile)` (Remote) で分岐。

### SSH プロファイル

`SshProfile` (id / name / host / port / user / password) を `SshProfileStore`
(DataStore + JSONArray シリアライズ) で永続化。

セキュリティ警告 (M5 時点):
- パスワードは **平文** で保存
- `StrictHostKeyChecking=no`
- known_hosts 永続化なし
- 公開鍵認証なし

→ いずれも M6 で対応予定。production で運用するなら現状は避けること。

## OSC 拡張 (M5)

| コード | 動作 | 実装場所 |
|---|---|---|
| OSC 0/1/2 | ウィンドウタイトル → `session.label` を 20 文字で更新 |
| OSC 4 ; idx ; spec | パレット色を設定 (`colors.setColor`) |
| OSC 10 ; spec | 既定前景色 |
| OSC 11 ; spec | 既定背景色 |
| OSC 12 ; spec | カーソル色 |
| OSC 52 ; sel ; payload | base64 payload をクリップボードに書込み (security: query は noop) |

`ColorSpec.parse(spec)`: xterm の `rgb:RR/GG/BB`, `rgb:RRRR/GGGG/BBBB`, `#RRGGBB`,
`#RRRRGGGGBBBB` をすべて 8bit ARGB に正規化。

## EAW Ambiguous 切替

`EastAsianWidth.isWide(cp, ambiguousAsWide)`:
- 既定 `false`: UTR #11 の W/F だけを 2 セル幅
- `true`: 罫線 (`0x2500-0x257F`)、矢印、囲み英数字、ブロック要素、私用領域も 2 セル幅

設定キー: `AppSettings.ambiguousAsWide` (DataStore boolean、既定 false)。
日本語/中国語/韓国語ロケールでは ON を推奨。

## ジェスチャ

`detectTransformGestures(panZoomLock = false)` を Modifier チェインに追加。

- **ピンチ**: `zoom != 1` のとき `fontSize *= zoom`、変化量 0.5sp 以上で DataStore に commit
- **二本指縦パン**: `|pan.y|` が 1 行分 (`charHeightPx`) 以上なら `scrollBy(lines)`
- 単指は従来通り (長押し → 選択 / ドラッグ → スクロール)

## 配布パイプライン

### GitHub Actions (`.github/workflows/build.yml`)

| ジョブ | トリガー | 出力 |
|---|---|---|
| `build` | push/PR | lintDebug + assembleDebug + testDebugUnitTest + APK artifact |
| `release` | tag `v*` push | bundleRelease (要 keystore secrets) |

必要な GitHub Actions Secrets:
- `RELEASE_KEYSTORE_BASE64` (`base64 < release.keystore`)
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

### Release Signing

`app/build.gradle.kts` がルート `keystore.properties` を見て signingConfig 生成:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=z2term
keyPassword=...
```

ファイルが無ければ debug 鍵にフォールバック (ローカル試用向け)。

### ProGuard ルール

- PtyProcess JNI 表面を保持 (reflection で createFileDescriptor 等)
- JSch + JZlib は内部 reflection 多用 → keep + dontwarn
- Compose / DataStore / Coroutines flow も保持
- SshProfile / DistroSpec の data class メンバを保持 (JSON シリアライズ)

### F-Droid メタデータ

`metadata/` ディレクトリ:
- `com.zerotoship.z2term.yml` — F-Droid Data 形式
- `en-US/short_description.txt` / `full_description.txt`
- `en-US/changelogs/<versionCode>.txt`

F-Droid のリポジトリ (`fdroiddata` repo) に PR する際は、このディレクトリを
コピーすれば良い。ただし現状の `app/src/main/assets/` に rootfs を同梱する
構造のままだと F-Droid のビルドポリシーに違反するため、M6 で FOSS フレーバー
(`flavor: foss { ... }` で assets と JSch を分離) を作成予定。

## ファイル構造 (M5 完了時点)

```
app/src/main/java/com/zerotoship/z2term/
├── Z2TermApplication.kt
├── MainActivity.kt
├── channel/                       ← M5 で新設
│   ├── LocalPtyChannel.kt          … PtyProcess のラッパー
│   ├── ProcessChannel.kt           … 共通インターフェース
│   ├── SshChannel.kt               … JSch ベース SSH
│   └── SshProfile.kt               … プロファイル + DataStore ストア
├── core/
│   ├── SessionManager.kt
│   └── TerminalSession.kt          … ProcessChannel ベースに改修 (M5)
├── distro/
│   └── DistroInstaller.kt
├── emulator/
│   ├── ColorSpec.kt                ← M5 で新設 (xterm color spec parser)
│   ├── EastAsianWidth.kt           … ambiguousAsWide パラメータ追加 (M5)
│   ├── TerminalBuffer.kt
│   ├── TerminalCell.kt
│   ├── TerminalColors.kt           … setDefaultForeground 等追加 (M5)
│   ├── TerminalEmulator.kt         … OSC 4/10/11/12/52 + clipboardWriter (M5)
│   ├── TerminalRow.kt
│   └── Utf8Decoder.kt
├── proot/
│   └── ProotLauncher.kt
├── pty/
│   └── PtyProcess.kt
├── service/
│   └── TerminalService.kt
├── settings/
│   └── AppSettings.kt              … ambiguousAsWide キー追加 (M5)
└── ui/
    ├── settings/
    │   └── SettingsSheet.kt        … AmbiguousWidthSection 追加 (M5)
    ├── ssh/                       ← M5 で新設
    │   └── SshProfilesSheet.kt     … プロファイル CRUD + 接続
    ├── terminal/
    │   ├── PhysicalKeyMapper.kt
    │   ├── TerminalRenderer.kt
    │   ├── TerminalScreen.kt       … pinch / 2finger / SSH icon (M5)
    │   └── TerminalViewModel.kt    … openSshSession 等 (M5)
    └── theme/
        ├── Color.kt
        ├── TerminalFonts.kt
        ├── Theme.kt
        └── Type.kt
```

## 既知の制約 / 注意事項

1. **SSH パスワード** — 平文 DataStore 保存。production 利用には危険。M6 で Keystore
2. **ホスト鍵検証なし** — MITM 検知不可。production では known_hosts 厳格化必須
3. **JSch ライブラリ追加** — APK サイズが ~250KB 増。FOSS バリアントでも残す予定
4. **F-Droid 不適合** — `app/src/main/assets/*.tar.gz` (rootfs) は F-Droid の
   prebuilt 規約違反。M6 で foss フレーバーで除外
5. **JSch の鍵交換アルゴリズム** — mwiede fork は modern ciphers 対応だが、古い
   OpenSSH (5.x) との互換性検証は未実施
6. **EAW Ambiguous の範囲** — UTR #11 完全照合ではなく代表的範囲のみ。perfect な
   端末 (mlterm / Konsole) より雑

## M6 へ向けた優先タスク (推奨)

1. **SSH セキュリティ強化**
   - Android Keystore でのパスワード暗号化保管
   - 公開鍵認証 (ed25519 / RSA) — JSch の `addIdentity` API
   - known_hosts 永続化 + 初回接続時の確認 UI
2. **FOSS ビルドバリアント**
   - `productFlavors { foss { ... } default { ... } }` を build.gradle に追加
   - foss は `app/src/main/assets/*.tar.gz` を除外、起動時に URL からダウンロード or 手動配置
3. **OSC 7 / 8 の対応**
   - OSC 7 (`file://host/path`) は current directory 通知。tab 名や status に活用
   - OSC 8 (hyperlinks) は URL を `escape ] 8 ; ; URL escape \\ text escape ] 8 ; ; escape \\` で囲む
     リンク領域として描画、タップで Intent.ACTION_VIEW 起動
4. **ポートフォワーディング**
   - SshChannel に `addLocalPortForward(local, remote_host, remote_port)` を追加
   - 設定 UI で listening port を管理
5. **起動時 init スクリプト**
   - DataStore に `init_command` (TerminalSession 起動後に流す文字列) を追加
   - "tmux attach || tmux new" のような起動を自動化

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha | 2026-05-16 | M2 実用ターミナル化完了 |
| 0.3.0-alpha | 2026-05-17 | M3 常駐ターミナル化完了 |
| 0.4.0-alpha | 2026-05-17 | M4 マルチセッション + 国際化対応完了 |
| 0.5.0-alpha | 2026-05-17 | M5 SSH + ジェスチャ + 配布準備完了 |
