# CLAUDE.md — z2term リポ ルール

このファイルは **Claude Code** が z2term 本体リポで作業するときのルール。
仕様の正本は `docs/DESIGN-SPEC.md`。

## コミット運用

- ⛔ **アプリ更新を含むコミットは、コミットする前に必ずバージョンを上げる。**
  - `app/build.gradle.kts` の `versionCode`（+1）と `versionName` を上げてから commit する。
  - 「先にバージョンを上げる → それを含めて 1 コミット」の順。バージョン据え置きでコミットしない。
  - **例外: docs/README/CLAUDE.md などドキュメントのみで `app/` 配下（コード・リソース・ビルド設定）の変更を含まないコミットはバージョンを上げない。**
- `git push` はユーザーの明示指示があるまで禁止。
- `--no-verify` は禁止（フック失敗は原因を直す）。
- 署名鍵（`*.jks`, `keystore.properties`）, `local.properties` はコミットしない。
