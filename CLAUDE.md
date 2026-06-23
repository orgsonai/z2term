# CLAUDE.md — z2term リポ ルール

このファイルは **Claude Code** が z2term 本体リポで作業するときのルール。
仕様の正本は `docs/ja/DESIGN-SPEC.md`（英訳: `docs/en/DESIGN-SPEC.md`）。

## コミット運用

- ⛔ **アプリ更新を含むコミットは、コミットする前に必ずバージョンを上げる。**
  - `app/build.gradle.kts` の `versionCode`（+1）と `versionName` を上げてから commit する。
  - 「先にバージョンを上げる → それを含めて 1 コミット」の順。バージョン据え置きでコミットしない。
  - **例外: docs/README/CLAUDE.md などドキュメントのみで `app/` 配下（コード・リソース・ビルド設定）の変更を含まないコミットはバージョンを上げない。**
- ⛔ **コミット前には関連 docs を更新してから 1 コミットにまとめる。**
  - 対象: `README.md` / `docs/ja/DESIGN-SPEC.md`（+ 英訳 `docs/en/DESIGN-SPEC.md`）/ `docs/ja/HANDBOOK.md`（+ 英訳 `docs/en/HANDBOOK.md`）/ 該当する `docs/Mxx-HANDOFF.md` 等で、今回の変更（仕様/挙動/版数/UI/コマンド/設定）に触れている箇所。
  - 版数を上げる場合は版数表記のある docs（README / DESIGN-SPEC / HANDBOOK 等）も新版数へ揃える。
  - 「コード修正 → docs 更新 → まとめて 1 コミット」の順。docs 反映漏れに気付いた後追いコミットを増やさない。
  - 該当箇所が無い純粋な内部修正（リファクタ等）は docs 更新スキップで可。スキップ時は理由を一言コミット本文に書く。
- `git push` はユーザーの明示指示があるまで禁止。
- `--no-verify` は禁止（フック失敗は原因を直す）。
- 署名鍵（`*.jks`, `keystore.properties`）, `local.properties` はコミットしない。

## 文書 / コード コメント

- ⛔ **コード・docs・コミットメッセージに具体的なローカルアプリ名 (TUI / CLI など) を記載しない。**
  - 公開リポジトリのため、ユーザー個人がローカルで使うアプリの名前を出すと外部に出せなくなる。
  - 振る舞いの記述は **仕様で書く**。例: 「alt screen を使う TUI」「マウスレポート ON の TUI」「複数ペインを持つ TUI」のように動作で定義し、固有名詞を避ける。
  - 例外的に「対応プロトコル / 規格 / OSS フレームワーク」(SGR, DECRST, xterm, ncurses, readline, SSH 等) と「広く一般的な開発ターゲット」(bash, zsh, sh, ssh, less 等の POSIX 系標準コマンド) は名指ししてよい。それ以外のアプリは原則名前を出さない。
  - 既存記述を更新するときも、もし旧版に固有名詞が残っていたら同じコミットで仕様ベースに置換する。

## ビルド運用

- ⛔ **ビルドは CPU を論理コアの「半分」だけ使う。** 端末の他作業/発熱を確保するため。
  - `scripts/gw.sh` 経由でビルドすれば、`nproc` の半分を `--max-workers` に自動設定する（端末ごとのコア数に追従）。
  - 直接 `./gradlew` を叩く場合も `--max-workers=$(($(nproc)/2))` を付ける。
  - 明示的に `--max-workers` / `--no-parallel` を渡した場合はそれを尊重する（gw.sh は上書きしない）。
