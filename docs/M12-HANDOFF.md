# M12 ハンドオフ — root chroot 裏機能の確定 / GUI 音声・動画 / IME 強化 / URL 下線 / 三本指スクロール

最終更新: 2026-05-31
ベース: 0.8.3-alpha (versionCode 11) / ブランチ: main / **HEAD = origin/main（push 済み・working tree clean）**

> M11（`docs/M11-HANDOFF.md`）で「未コミット」だった chroot 裏機能はこの M12 で確定（コミット + 実 root 端末で end-to-end 検証）。
> 本書は M11 のコミット `417f664` 以降〜現在（`4f76817`）の作業をまとめる。

## コミット状態（M11 `417f664` 以降）

| Commit | 種別 | 概要 |
|---|---|---|
| `c74dbc9` | feat(M11) | root 端末で chroot 実行する裏機能 + version 0.8.0-alpha bump |
| `296900d` | feat(ime/term) | フリック表記反転・活用辞書・GUI 自動起動・横画面 GUI 他 + かな連打の循環廃止 |
| `3e2e817` | feat(gui) | GUI 動画をソフト描画で正常再生（mpv `vo=x11` 既定 + GL 強制ソフト） |
| `0db8b2e` | feat(gui) | GUI 音声ブリッジ（オプトイン）— PulseAudio→TCP→AudioTrack |
| `3937e89` | fix(gui) | GUI 音声が鳴らない 2 バグを修正（PA 起動方式 + 接続先ポート） |
| `951a511` | fix(emulator) | 折り返し wrapped フラグを継続行→折り返し元の行へ |
| `2e1d9cc` | feat(terminal) | URL/OSC8 リンクのセルに下線を表示 |
| `c7a906b` | fix(chroot) | Ctrl+C / ジョブ制御を有効化（login shell を `setsid -c` で起動） |
| `41a6668` | chore(version) | 0.8.0-alpha (8) → 0.8.1-alpha (9) |
| `fd8f119` | fix(emulator) | OSC タイトルを UTF-8 デコードしてタブ名の文字化けを解消 |
| `28d75dc` | feat(ime) | 日本語キーボードの ⌫ を左右フリックで単語削除/全削除に |
| `4568272` | feat(ui) | ツールバーのボタン順を変更 |
| `d7397c7` | feat(settings) | キャッシュ削除 / レイアウト整理 / トースト連打 / ドラッグ閉じ無効化 |
| `822baef` | fix(settings) | 最上部での更なるプルダウンでシートを閉じられるように |
| `1c1bbd3` | chore(version) | 0.8.1-alpha (9) → 0.8.2-alpha (10) |
| `de797d3` | feat(gui) | RfbClient にホイールスクロール送出を追加 |
| `3efc311` | fix(keyboard)+feat(gui) | キーボードモード一致 / GUI スクロールボタン |
| `3ac98a3` | change(gui) | スクロールボタンを廃止しアプリ内スクロールを三本指に |
| `4f76817` | build(debug) | debug の表示名を「Z2Term debug」に分けて release と共存しやすく |

※ 0.8.2 → 0.8.3-alpha (11) の bump は上記の作業に含めて反映済み（現行 `app/build.gradle.kts` = versionCode 11 / 0.8.3-alpha）。

---

## 1. root chroot 裏機能（確定・実機検証済み）

PRoot に加えて「実 chroot(root)」エンジンを追加した裏機能。表に出さず、root 中級者だけが辿り着ける解放手順を採用。

- **解放**: 設定 → アプリ情報の「バージョン」を **7 回タップ**。`ProotLauncher.probeRootChroot()` がセルフテスト（`su -c id` で uid=0 + `su -c "chroot <rootfs> /bin/sh -c echo"`）。OK のときだけ DataStore `rootChrootUnlocked=true`。
- **エンジン選択**: 解放後に設定へ「実行エンジン (proot / chroot)」が出現（グローバル設定 `executionEngine`、新タブから反映）。
- **実装**: `ProotLauncher.launchChroot()` が `su -c` で bind mount(/dev,/dev/pts,/proc,/sys,/root,/sdcard)→`chroot`→login shell。`ensure*`(z2-*/OSC7/履歴/sshd/gui/z2run) は proot 経路と共通で流用。chroot 起動失敗時は proot へ自動フォールバック（`TerminalSession.startTerminal`）。
- **Ctrl+C / ジョブ制御修正**（`c7a906b`）: su 経由だと制御端末を所有できずジョブ制御が効かなかった → login shell を **`setsid -c` 経由**で起動して修正（proot は元から正常）。
- **検証**: root 化した moto g13（penangf / Android 14 / Magisk）で **end-to-end 検証完了・成功**（SELinux Enforcing 下でも chroot 通る）。Ctrl+C 修正も同端末で実打確認。`full` フレーバー必須（foss は対象外）。

## 2. GUI 動画・音声（実機で開通確認）

- **動画**（`3e2e817`）: GPU 無し端末で mpv の `gpu` 出力が失敗し化け/半分描画になった → **`vo=x11` を既定化 + `LIBGL_ALWAYS_SOFTWARE` でソフト描画強制**。`GuiScript.kt` 経由。
- **音声**（`0db8b2e` + `3937e89`）: **オプトイン**。設定「GUI 音声」トグル ON 時のみ PulseAudio を起動し、`PulseAudio → TCP → AudioTrack` でブリッジ（`service/AudioBridge.kt`）。2 バグ修正後、実機で mpv 動画（rubicon）の音が鳴ることを確認。
  - 注意: PA 起動は `-n` 方式必須、`AudioBridge` の apply 内 `port=0` バグに注意（修正済み）。SMPlayer の半分描画は別課題（対応不要と判断）。

## 3. ターミナル / エミュレータ修正

- **URL 下線**（`2e1d9cc`）: URL / OSC8 ハイパーリンクのセルに下線を表示（タップ可能箇所が見える）。
- **折り返し wrapped フラグ修正**（`951a511`）: 折り返した長い URL の検出で wrapped フラグが逆転していたバグを修正。実機で長 URL のタップ・OSC8・末尾トリム・誤爆なしを確認（S-3 検証）。
- **OSC タイトル UTF-8 デコード**（`fd8f119`）: OSC タイトルを UTF-8 デコードしてタブ名の日本語文字化けを解消。
- **bracketed paste**（S-1 検証）: ON/OFF バイト送出を確認済み。

## 4. キーボード / IME 強化

- **かな連打の循環廃止**（`296900d`）: 同キー連打で「つつ」が「っ」等に化ける循環を廃止（つつ → つつ）。
- **⌫ 左右フリック**（`28d75dc`）: 日本語キーボードの ⌫ を左フリックで単語削除 / 右フリックで全削除に。
- **フリック表記反転・活用辞書**（`296900d`）。
- **キーボードモード一致**（`3efc311`）: GUI と通常端末でキーボードモードを揃える修正。

## 5. GUI 操作 / スクロール

- **ホイールスクロール送出**（`de797d3`）: `RfbClient` に VNC ホイールイベント送出を追加。
- **スクロールボタン廃止 → 三本指スクロール**（`3ac98a3`）: 画面内スクロールはボタンを廃し **三本指ドラッグ**に統一（GUI / 通常端末とも）。※ HANDBOOK の操作表もこれに更新済み。
- **GUI 自動起動・横画面 GUI**（`296900d`）。

## 6. 設定 UI

- **キャッシュ削除 / レイアウト整理 / トースト連打 / ドラッグ閉じ無効化**（`d7397c7`）、**最上部プルダウンで閉じる**（`822baef`）、**ツールバーのボタン順変更**（`4568272`）。

## 7. ビルド / 配布

- **debug 表示名を「Z2Term debug」に分離**（`4f76817`）: release（`com.zerotoship.z2term`）と debug（`.debug`）を端末上で見分けやすく共存。
- 署名 / インストールは M11 と同様: `keystore.properties` の実鍵で release を署名、`adb install -r` で rootfs 温存（`adb uninstall` 厳禁）。

## 8. 次セッション候補

- F-Droid 公開（foss フレーバーは整備済、metadata 提出が残）。
- ローカル/リバースポートフォワーディング（-L / -R）の UI。
- IME 学習履歴のリセット / バックアップ UI。

## 関連メモ (auto-memory)

- [[z2term-state-2026-05-31]]: 現在状態（0.8.3-alpha(11)）と注意点
- [[z2term-chroot-feature]] / [[z2term-chroot-ctrlc]] / [[z2term-g13-root-device]]: chroot 裏機能の実装・Ctrl+C 修正・root 端末での検証
- [[z2term-gui-audio-plan]] / [[z2term-gui-video-audio]]: GUI 音声・動画
- [[z2term-s1-s3-verify]]: bracketed paste / URL・OSC8 の実機検証
- [[z2term-patch2-apply]]: かな連打循環廃止
</content>
</invoke>
