# M11 ハンドオフ — スクロールバック検索 / セッション復元 / Android API ブリッジ / 日本語IME強化 / cwd復元修正 / root chroot 裏機能

最終更新: 2026-05-31（chroot 確定を反映）
ベース: 0.8.0-alpha (versionCode 8) / ブランチ: main / **以降の続きは `docs/M12-HANDOFF.md`（現行 0.8.3-alpha / push 済み）。**

> 当初このハンドオフは chroot 裏機能を「未コミット・未検証」として書いた。**chroot はその後 `c74dbc9` でコミットし、root 化端末で end-to-end 検証済み**（詳細は M12）。下表・本文の「未コミット」表記は当時のもの。

## コミット状態

| Commit | 種別 | 概要 | 状態 |
|---|---|---|---|
| `417f664` | feat(M11) | スクロールバック検索 / セッション復元 / Android API ブリッジ / 日本語IME強化 (+cwd OSC7 修正) | コミット済 |
| `c74dbc9` | feat | root 端末で chroot 実行する裏機能 + version 0.8.0 bump | **コミット済（M12 で確定・実機検証済）** |

---

## 1. M11 機能 (`417f664`・実機確認済み)

### #1 スクロールバック検索 — `SearchEngine.kt`
🔍 → 文字入力 → ↑↓ で前後ジャンプ。全角(CJK)は **セル列** でハイライト位置を計算（`TerminalScreen.kt:217` で再計算）。
実機OK: 件数カウント(8/8)・↑↓ジャンプ・全角行(`あいうえおXXX`)のハイライト位置。

### #2 セッション復元 — `SessionStore.kt` / `SessionManager.kt`
タブ構成 `{id,label,distro,cwd}` + activeId を DataStore に保存し、OS kill 後の再起動で復元。GUI タブは対象外。各タブは新規 PTY で起動し `cd <cwd>` をベストエフォートで流す。
- **cwd は OSC7 でのみ捕捉**（`TerminalEmulator.handleOscCwd` → `TerminalSession._cwd`）。archlinux のシェルが `cd` で OSC7 を出さず当初 cwd が復元しなかった → `ProotLauncher.ensureOsc7CwdConfig()` で **bash の PROMPT_COMMAND / zsh の precmd に OSC7 (`\033]7;file://host$PWD\a`) を吐くフックを注入**して修正（履歴設定と別マーカーで既存 rootfs にも後付け）。実機で通常 `cd /tmp` → kill → 復元で `pwd=/tmp` を確認。
- 「通知の停止」(`ACTION_STOP` → `SessionManager.shutdown()` → 空保存) では復元しない（コード確認のみ。silent な常駐通知が shade に出ずボタン実タップは未実施）。

### #3 Android API ブリッジ — `Z2ApiBridge.kt` / `Z2ApiScript.kt`
`z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip (set/get)` / `z2-battery` / `z2-vibrate`。FileObserver でファイル監視（req/resp は `getExternalFilesDir/z2api`）、base64 で引数受け渡し、atomic rename。`ProotLauncher.ensureZ2ApiScripts` が launch 毎に `/usr/local/bin` へ書き出す。
実機OK: notify(dumpsys 確認)・toast・vibrate・clip 往復(`hi`)・battery JSON。

### 日本語IME強化（キーボード系）
スプリット変換 / 候補サイクル / かな濁点循環 / 再変換 / 学習履歴UI(`ImeHistorySheet.kt`) / フリックポップアップ。

### 各種修正（M11 内）
キーボード高さ 縦/横 別管理 + 向きで自動切替、OS データ削除 UI、インストールのタイムアウト撤廃、既定キーボードを spacious。

---

## 2. root chroot 裏機能（→ `c74dbc9` でコミット済・実機検証済。詳細は M12）

PRoot に加えて「実 chroot(root)」エンジンを追加した裏機能。表に出さず、root 中級者だけが辿り着ける解放手順を採用。

- **解放**: 設定 → アプリ情報の「バージョン」を **7回タップ**（Android 開発者モードと同作法）。`ProotLauncher.probeRootChroot()` がセルフテスト（`su -c id` で uid=0 + `su -c "chroot <rootfs> /bin/sh -c echo"`）。OK のときだけ DataStore `rootChrootUnlocked=true`。
- **エンジン選択**: 解放後に設定へ「実行エンジン (proot / chroot)」が出現（グローバル設定、新タブから反映）。
- **実装**: `ProotLauncher.launchChroot()` が `su -c` で bind mount(/dev,/dev/pts,/proc,/sys,/root,/sdcard)→`chroot`→login shell。`ensure*`(z2-*/OSC7/履歴/sshd/gui/z2run) は proot 経路と共通で流用。`RootProbe`(Ok/NoRoot/ChrootBlocked)。chroot 起動失敗時は proot へ自動フォールバック（`TerminalSession.startTerminal`）。
- **検証**: 未 root 端末では NoRoot 経路のみ確認。その後 **root 化した moto g13（Magisk）で実 chroot を end-to-end 検証・成功**（SELinux Enforcing 下でも通る）。Ctrl+C/ジョブ制御は `c7a906b` で login shell を `setsid -c` 経由にして修正・実機確認済み（M12）。

---

## 3. 変更ファイル

- **M11** (`417f664`): 新規 `SearchEngine.kt` / `SessionStore.kt` / `Z2ApiScript.kt` / `Z2ApiBridge.kt` / `ImeHistorySheet.kt` + 変更 `SessionManager` / `TerminalSession` / `TerminalRenderer` / `TerminalScreen` / `ProotLauncher` / `AppSettings` / `SettingsSheet` / `Z2TermApplication` / keyboard 系 / `AndroidManifest` / `strings.xml`・`values-ja`。
- **chroot**(未コミット): `AppSettings.kt`(rootChrootUnlocked/executionEngine + ENGINE_PROOT/CHROOT)、`ProotLauncher.kt`(launchChroot/probeRootChroot/resolveSu/chrootBootstrap/RootProbe)、`TerminalSession.kt`(エンジン分岐 + setter)、`SettingsSheet.kt`(7タップ解放 + エンジンセレクタ)、`strings.xml`・`values-ja`、`build.gradle.kts`(versionCode 8 / 0.8.0-alpha)。

## 4. ビルド / 署名 / インストール（重要）

- `./gradlew :app:assembleFullDebug :app:assembleFullRelease`（JDK 17）。
- 署名: ルートの `keystore.properties` の `storeFile` が実在しない旧パス(`app_project/z2term/...`)を指していたので **実在の `./z2term-release.jks`（alias `z2term`, SHA-256 `40:46:14:61…`）に修正済み**（gitignore 対象・未コミット）。この鍵は端末の既存 release 署名と一致するので、release は `adb install -r` で **rootfs(archlinux) を温存**したまま上書きできる。`adb uninstall` は rootfs を失うため厳禁。
- 端末: moto g66j 5G（Android 15・**未 root**・SELinux Enforcing・/data は nosuid,nodev,exec可）。release `com.zerotoship.z2term`(0.8.0) と debug `com.zerotoship.z2term.debug`(0.8.0-debug) 両方インストール済み。

## 5. 次セッション（→ ほぼ M12 で消化済み）

- ~~chroot + version bump + 本ハンドオフのコミット~~ → `c74dbc9` でコミット済。
- ~~root 端末で実 chroot を検証~~ → moto g13(Magisk) で検証済。`probeRootChroot` が `CHROOT_BLOCKED` を返す端末では proot のまま。
- （任意）zsh を使う distro で OSC7 フックの実走、停止通知ボタンの実タップ確認。
- 続きの作業ログは `docs/M12-HANDOFF.md`。

## 関連メモ (auto-memory)

- [[z2term-chroot-feature]]: chroot 裏機能の実装と検証状態
- [[z2term-patch-handoff]]: M11 機能の実機検証結果と cwd 修正
- [[repo-identity]]: ここは z2term 本体の独立リポ（編集可）
- [[device-verification-preference]]: adb 接続時は実機で機能を実走確認（uninstall 禁止）
