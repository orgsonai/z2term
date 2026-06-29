# M6 ハンドオフドキュメント

最終更新: 2026-05-17
バージョン: 0.6.0-alpha (M6: SSH 強化 + FOSS フレーバー + リンク対応、完了)
**状態: 完了 — 次フェーズは M7 (ポート転送 / SFTP / mosh など)**

## このドキュメントの目的

Z2Term Milestone 6「SSH 強化 + FOSS フレーバー + リンク対応」完了時点のスナップショット。
M7 担当者 (ポート転送 / SFTP / mosh / セッション分離) への引き継ぎ。

## M6 スコープと達成状況

- [x] SSH 公開鍵認証 (PEM テキスト + 任意 passphrase)
- [x] Android Keystore (AES-256/GCM) で機密フィールド暗号化
- [x] known_hosts 永続化 + ホスト鍵検証ダイアログ
- [x] OSC 7 (current working directory)
- [x] OSC 8 (hyperlinks) — 下線描画 + タップで Intent.ACTION_VIEW
- [x] 起動時 init コマンド (グローバル + プロファイル別)
- [x] FOSS ビルドフレーバー + ランタイムディストロダウンロード

M6 で着手しなかった (M7 へ持ち越し):

- [ ] ローカルポート転送 (-L) / リバース (-R)
- [ ] SFTP ファイル転送 UI
- [ ] mosh プロトコル対応 (UDP ベース)
- [ ] タブごとに完全分離したセッション (現状は emulator は共有してないが、scrollback は session 内)
- [ ] OSC 11 query (背景色読み取り、tmux のテーマ追従)
- [ ] xterm-mouse プロトコル (vim マウス操作)

## アーキテクチャ概略 (M6 版)

```
┌──────────────────────────────────────────────────┐
│              TerminalScreen (Compose)              │
│  ┌────────────────────────────────────────────┐    │
│  │ TabBar — sessions / activeId                │    │
│  ├────────────────────────────────────────────┤    │
│  │ TerminalRenderer (Canvas)                   │    │
│  │   + onKeyEvent                              │    │
│  │   + detectTransformGestures (pinch / pan)   │    │
│  │   + detectTapGestures (OSC 8 リンクオープン) │    │
│  │   + 長押し選択                              │    │
│  ├────────────────────────────────────────────┤    │
│  │ SpecialKeyBar + InputBar (⚡ realtime)       │    │
│  ├────────────────────────────────────────────┤    │
│  │ HostKeyVerificationDialog (active 時のみ)    │    │
│  │ SshProfilesSheet / SettingsSheet             │    │
│  └────────────────────────────────────────────┘    │
└────────────────────┬─────────────────────────────┘
                     │
        TerminalViewModel
                     │
        ┌────────────┴─────────────┐
        ▼                          ▼
SessionManager.sessions       TerminalService (FG + WakeLock)
        │                          │
        ▼                          │
   TerminalSession ←───────────────┘
        ├ emulator (TerminalEmulator)
        │   + cwdSetter / titleSetter / clipboardWriter
        │   + OSC 4 / 7 / 8 / 10/11/12 / 52
        ├ ProcessChannel ← LocalPtyChannel | SshChannel
        ├ cwd / label (StateFlow)
        └ settings (DataStore Flow + init command)
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
       PtyProcess (JNI)     JSch.Session
                                  │
                       KnownHostsHolder (singleton)
                       ├ DataStoreHostKeyRepository
                       └ HostKeyVerifier (UI bridge)
```

## SSH セキュリティ層 (M6 で追加)

### Android Keystore 暗号化

`channel/KeystoreCrypt.kt`:
- alias `z2term_ssh_v1` で AES-256/GCM 鍵を生成 (ハードウェアバックド優先)
- 出力: `"ENC:" + base64(iv || ciphertext)`
- 旧版で書かれた平文値は prefix 無しのままなので、`decrypt()` がそのまま返す
- 鍵がデバイス紛失/factory reset で消えた場合は `IllegalStateException`

`SshProfile`:
- `password / privateKey / keyPassphrase` の 3 つを `toJson()` 時に暗号化、
  `fromJson()` で復号
- in-memory では平文。プロセス内のみで扱う

### known_hosts

`channel/KnownHosts.kt`:
- `KnownHostsStore` (DataStore Preferences、JSON Array)
- `DataStoreHostKeyRepository` が JSch.HostKeyRepository を実装
  - ConcurrentHashMap で同期 check、add は非同期で DataStore に反映
- `HostKeyVerifier` シングルトン:
  - JSch ワーカースレッドからの `requestVerify(prompt)` は CompletableFuture で UI 応答待ち
  - Compose 側は `HostKeyVerifier.flow` を購読してダイアログを出す
  - 「信頼して接続」で `resolve(true)` → JSch 側で `repository.add()` が呼ばれる

`SshChannel.connect`:
- `StrictHostKeyChecking=ask` に切替
- `VerifyingUserInfo` がパスフレーズ・パスワードを自動回答、未知ホスト時のみ UI 経由

## OSC 7 / OSC 8

| OSC | 意味 | 動作 |
|---|---|---|
| OSC 7 ; file://host/path | CWD 通知 | `session.cwd: StateFlow<String>` を更新 |
| OSC 8 ; params ; URI | リンク開始 | `currentLink = URI` |
| OSC 8 ; ; | リンク終了 | `currentLink = null` |

`TerminalCell.link: String?` を追加 (1 セルあたり nullable reference 1 つ = 8 byte)。
`Renderer` がリンク付きセルにシアン色の自動下線を引き、`TerminalScreen` の
`detectTapGestures` でセル座標 → リンク URI を解決し、`Intent.ACTION_VIEW` で
他アプリへ渡す。

## 起動時 init コマンド

- `AppSettings.initCommand` — グローバル設定
- `SshProfile.initCommand` — プロファイル別 (空ならグローバルへ fallback)
- TerminalSession が RUNNING に遷移してから 400ms 後に `writeBytes(cmd + "\n")`
- 例: `tmux attach || tmux new -s main` で接続即セッション復帰

## FOSS フレーバー

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("full") { buildConfigField("boolean", "IS_FOSS", "false") }
    create("foss") {
        applicationIdSuffix = ".foss"
        versionNameSuffix = "-foss"
        buildConfigField("boolean", "IS_FOSS", "true")
    }
}
```

`DistroInstaller.install` の優先順位:
1. `DistroDownloader.resolveLocalArchive(spec, abi)` でキャッシュ済み tar.gz
2. `context.assets.open(...)` で APK 同梱 (full 用)

`DistroDownloader`:
- HTTPS のみ、`User-Agent: z2term/<id>`
- Flow<Progress> で Started → Downloading × N → Verifying → Completed/Failed
- 256KB ごとに Downloading イベント発火 (UI 進捗バー連動の余地)
- SHA-256 検証はオプション (`expectedSha256` を渡せば自動)
- 既定: Alpine 3.21 公式 dl-cdn URL を内蔵、Ubuntu は xz 圧縮のため手動配置のまま

## ファイル構造 (M6 完了時点、M5 からの差分のみ)

```
app/src/main/java/com/zerotoship/z2term/
├── channel/
│   ├── KeystoreCrypt.kt           ← M6 で新設 (Android Keystore AES-GCM)
│   ├── KnownHosts.kt              ← M6 で新設 (store + repo + verifier)
│   ├── KnownHostsHolder.kt        ← M6 で新設 (singleton)
│   ├── LocalPtyChannel.kt
│   ├── ProcessChannel.kt
│   ├── SshChannel.kt              … VerifyingUserInfo / 鍵認証 (M6)
│   └── SshProfile.kt              … authType / privateKey / passphrase / initCommand (M6)
├── distro/
│   ├── DistroDownloader.kt        ← M6 で新設
│   └── DistroInstaller.kt         … cached → assets の fallback (M6)
├── settings/
│   └── AppSettings.kt             … initCommand キー (M6)
└── ui/
    ├── settings/SettingsSheet.kt   … InitCommandSection (M6)
    ├── ssh/
    │   ├── HostKeyVerificationDialog.kt   ← M6 で新設
    │   └── SshProfilesSheet.kt    … 認証タイプ切替 + initCmd 欄 (M6)
    └── terminal/
        └── TerminalScreen.kt      … tap link + HostKeyVerificationDialog (M6)
```

## 既知の制約 / 注意事項

1. **Keystore 鍵紛失** — Factory reset や Keystore corruption で AES 鍵が消えると、
   既存プロファイルの password/privateKey は復号失敗。fromJson は空文字を返すので
   接続不可になるが、プロファイル自体は残る (再入力で復活)。
2. **known_hosts UI 一回限り** — 同じホストで複数の鍵タイプを使い分けるサーバーは
   現状 1 鍵タイプしか保存しない (実際は jsch.HostKeyRepository が複数管理可能)。
3. **OSC 8 link memory** — 全セルに `link: String?` を持たせるので、5000 行 ×
   80 列 × 8 byte = 約 3MB のオーバーヘッド (大半 null だが reference 領域は確保)。
4. **OSC 7 cwd の用途** — `session.cwd` を取得できるが UI で表示はしていない。
   M7 でタブラベルやステータスバー反映を検討。
5. **FOSS フレーバーのテスト未** — F-Droid の reproducible build で実際に通るかは
   未検証。F-Droid Data リポジトリへ PR する前に `fdroid build` の手元検証推奨。
6. **DistroDownloader の Ubuntu** — 公式は xz 圧縮なので、現状は Alpine のみ
   自動 URL。Ubuntu は assets/README.md の手動手順に従う。

## M7 へ向けた優先タスク (推奨)

1. **ポートフォワーディング (-L)**
   - `SshChannel` に `addLocalPortForward(local, remote_host, remote_port)` を追加
   - JSch の `Session.setPortForwardingL` を呼ぶだけ
   - UI: SSH プロファイル内に「ポート転送」リストを追加
2. **SFTP ファイル転送**
   - `ChannelSftp` を別接続として開く
   - ファイラー UI で push/pull、進捗表示
   - クリップボードを介さない大容量転送に必要
3. **mosh プロトコル**
   - JSch だけでは無理。`mosh-client` バイナリを foreign code として同梱する案
   - もしくは Kotlin 実装の `mosh-protocol` を書くか、`libmoshlib` ラッパーか
4. **OSC 11 query**
   - tmux 等が背景色を問い合わせる ("OSC 11 ; ? BEL")
   - emulator から `]11;rgb:RR/GG/BB` で応答すれば自動テーマ追従可能
5. **xterm mouse**
   - `?1000 / 1002 / 1006` モードで Mouse 座標を CSI で送る
   - vim/htop でマウスクリックが動くようになる

## 変更履歴

| 版 | 日付 | 内容 |
|---|---|---|
| 0.1.0-alpha | 2026-05-15 | M1 PoC 完成 |
| 0.2.0-alpha | 2026-05-16 | M2 実用ターミナル化完了 |
| 0.3.0-alpha | 2026-05-17 | M3 常駐ターミナル化完了 |
| 0.4.0-alpha | 2026-05-17 | M4 マルチセッション + 国際化対応完了 |
| 0.5.0-alpha | 2026-05-17 | M5 SSH + ジェスチャ + 配布準備完了 |
| 0.6.0-alpha | 2026-05-17 | M6 SSH 強化 + FOSS フレーバー + リンク対応完了 |
