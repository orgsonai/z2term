# Z2ROOT-GIT-RECEIVE-HANDOFF

A 案 (l2s rename(2) を atomic 化) の Phase 1 = z2term 上で `git push` 受け側破綻の再現と機構特定。

## TL;DR

- **2026-06-22 (z2root dev env)**: 6 シナリオ全 exit 0・新規 `.l2s` ゼロで**再現せず**。
- **2026-06-22 (proot タブ・SSH 越し追試)**: `unpack should have generated <sha>, but I can't find it!` が **小 push (3 obj) でも 150 ファイル push でも必発**。**真因確定**。
- **真因は「`rename(2)` 非 atomic」ではなく「proot の `link()` emulation (link2symlink) が quarantine 内の `.l2s.tmp_*` を指す symlink を `objects/<aa>/<sha>` に作る → quarantine ディレクトリが receive-pack 終了処理で削除されると、その symlink が dangling になる」**。
- → **z2root 側に修正を入れる必要はない**。z2root の modern linkat (実 link→失敗時コピー fallback) はそもそも `.l2s` を作らないため本症状は構造的に起きない。**修正対象は proot 側、または運用上は「受け側を z2root エンジンに切り替える」で回避可能**。当初の A 案 (z2root の `rename(2)` atomic 化) は誤診で、適用しても直らない。

## 観測環境

- 日時: 2026-06-22
- 実行エンジン: **z2root 確定** (`/proc/self/maps` に `/data/app/.../libz2root.so`、`TracerPid` 非 0)
- z2term 版数: 0.8.118-alpha (versionCode 126)
- git: 2.54.0
- 作業ディレクトリ: `/root/tmp/git-receive-test/` (本リポと分離)

## 検証シナリオと結果

| # | シナリオ | 受け側状態 (push前) | 結果 |
|---|---|---|---|
| 1 | fresh bare に小さな push (5 ファイル / 7 obj) | `.l2s` ゼロ・objects 空 | **exit 0**。loose 7 個。`.l2s` 生成ゼロ |
| 2 | 1 と同じ bare に bulk 150 ファイル追加 push | 1 の続き | **exit 0**。`pack-*.pack` 1 本生成 (`GIT_QUARANTINE_PATH=tmp_objdir-incoming-…` 経由)。`.l2s` ゼロ |
| 3 | 同じ bare に追加 commit を re-push | 2 の続き | **exit 0** |
| 4 | `git commit --amend && git push --force` | 3 の続き | **exit 0** (refs/heads/master の atomic rename 経路) |
| 5 | プロジェクト全 (166 loose + 2 pack + 416 `.l2s`) を fresh bare に `--all` push | 空 bare | **exit 0**。8MB の pack 1 本に consolidate、`refs/heads/main = 1006430`、fsck 無エラー、`.l2s` 生成ゼロ |
| 6 | fresh repo に `git init` → `add` → `commit` → `clone` | n/a | **すべて成功**。`.l2s` 生成ゼロ、symlink ゼロ |

`GIT_TRACE=1` で確認: シナリオ 2 で `index-pack` が `GIT_QUARANTINE_PATH=…tmp_objdir-incoming-nWcHGM` 経由でパックを書き、その後 `objects/pack/` への atomic rename が成立している。**quarantine 経路は確実に通っているのに破綻が起きていない**。

## プロジェクト `.git` の `.l2s` 検査

- 総数 **416 個** (`.l2s.tmp_obj_*` および対応 `.0001`)
- **dangling 0 / 有 backing 416** (`find -xtype l` で dangling は出ない)
- タイムスタンプ範囲: **2026-06-08 〜 2026-06-19**
- 実 git object (40-hex hash 名) と**同じディレクトリ内で並存**しているが、これらは git の object として参照されない別名のファイル ⇒ **孤児 chain**

例: `.git/objects/23/`
```
27ea78ee5849179d83ffac3467cb40e22c37dc      ← 実 git object (loose)
2f78cbe1f4f28e5b244e0ccc14d475414d8bac      ← 実 git object (loose)
.l2s.tmp_obj_w6y0cx0001                     ← 孤児 (symlink, backing 有)
.l2s.tmp_obj_w6y0cx0001.0001                ← その実体ファイル
```

孤児が git 操作を壊していない (`git log` / `git push` 全部 exit 0、fsck エラーなし) ことを確認済み。

## 新規 `.l2s` 生成経路の調査

現行 z2root の `app/src/main/cpp/z2root/z2root.c`:
- L614 `rename`/`renameat2`: 単純パス翻訳のみ。`.l2s` 命名生成なし。
- L1813〜 `linkat`(0.8.47+ 新方式): 実 `link()` 試行 → 失敗時はコピー fallback。**`.l2s` 命名は使わない**。
- L300〜 `host_to_guest` / L406〜 `canonicalize_guest`: 既存 `.l2s` を**読む**互換のみ (新規生成しない)。

シナリオ 1〜6 すべてで `.l2s` の新規生成ゼロ ⇒ **現行 z2root は `.l2s` を作らない**ことを実証。

プロジェクトの 416 個は、過去の proot 使用 (本リポでは `assembleFullRelease` 等のビルド時に proot タブを使用した履歴あり) または 0.8.46 以前の旧 z2root が残したものと推定。詳細な記録は `docs/Z2ROOT-L2S-RELATIVE-HANDOFF.md` 参照。

## 追加: dst に `.l2s` を seed した push (2026-06-22 同日)

Phase 1 の (a) 仮説をピンポイントで突くため、受け側 bare に手動で `.l2s` chain を seed して push:

### S1: pack 領域に偽 `.l2s` chain を seed

`bare-seeded.git/objects/pack/` に偽 hash の `.l2s` chain (`pack-aaaa…aaaa.pack → .l2s.tmp_pack_seedA0001`) を置いた状態で push:

- 結果: **push 成功 (exit 0)**。git は新しい pack を**別 hash 名** (`pack-c8ccd…`) で並べ、seed した偽 chain は無傷で残った。
- 教訓: pack/loose object は**hash 名前空間**で動くので、real push の rename target が既存 `.l2s` chain と**名前衝突する経路が原理的に存在しない**。同名なら同内容＝重複書込みは no-op で rename 自体が走らない。

### S2: refs を `.l2s` chain で seed

`bare-refs.git/refs/heads/master` を `.l2s.tmp_obj_seedref0001` への symlink、中身を有効な SHA にして push:

- 結果: **push が `fatal: bad object refs/heads/master` で reject**。
- ただしこれは rename 失敗ではなく、git が `refs/heads/master = symlink` を **`symlinkRef` (symbolic ref)** と解釈し、symlink target 名 (`.l2s.tmp_obj_seedref0001`) を「参照先 ref 名」として扱って `badRefName` で弾いたもの。
- 教訓: refs は link2symlink の対象になることがそもそも無い (link2symlink は hardlink 用)。「refs が `.l2s` chain になっている」という状態は実運用で発生しない。

### S1/S2 から導かれる構造的結論

real-world の push 経路で z2root の `rename(2)` が**既存の `.l2s` chain を dst に持つ**シナリオは:

| 対象 | 衝突可能性 |
|---|---|
| pack の `.pack`/`.idx`/`.rev` | hash 名衝突 = 同内容、よって rename しない |
| loose object (`objects/<aa>/<bbbb…>`) | 同上 |
| refs (`refs/heads/*`) | 元から link2symlink の対象外 |
| quarantine→objdir の移行ファイル | hash 名衝突 = 同上 |

**= 実運用で rename がぶつかる経路が無い**。よって「dst l2s chain の rename 非 atomic」というメカニズムは現行 z2root の git push 受け側で観測しようがない。

## (a)(b)(c) の判定

| 候補 | 状態 |
|---|---|
| (a) 残存レガシー `.l2s` が dst にあって rename が壊れる | **否定**。S1 で pack 領域に `.l2s` chain を seed しても push は成功し chain も無傷。git の hash 名前空間構造上、real push で名前衝突する経路自体が存在しない |
| (b) 現行 z2root に `.l2s` 風生成残存経路がある | **否定**。fresh 操作で生成ゼロを確認 |
| (c) rename 以外の機構 | **未確定**。ユーザー観測条件 (SSH/Gitea 等) が未再現のため除外できない |

## 2026-06-22 追試: proot タブ受け側で必発再現を確認

ユーザー協力で proot タブの SSH (`ssh -p 2222 root@127.0.0.1`) に到達できたため、SSH 越し push を proot 受け側で実走:

### setup

- proot タブ: `/proc/self/maps` に `libproot_loader.so` 確定。
- bare 受け側: `/root/git-push-test/bare.git` を `git init --bare` で新規作成 (`.l2s` ゼロから開始)。
- push 元: 本セッション (z2root dev env) の `/root/tmp/git-receive-test2/src` (3 ファイル commit)。

### 再現結果

| シナリオ | エラー | 受け側残骸 |
|---|---|---|
| 3 ファイル push (loose object 路) | `error: unpack should have generated a851e3c20ee0…, but I can't find it!` → `bad pack` reject | `objects/{a8,a1,89,b7,93}/<sha>` が **5 個全部 dangling symlink** (`tmp_objdir-incoming-OIDhpD/<aa>/.l2s.tmp_obj_*` を指す)。`.l2s.tmp_*` 自体は quarantine ごと削除済みで存在せず |
| 150 ファイル push (index-pack 路) | `error: unpack should have generated 13b9ccd9933…, but I can't find it!` | pack 3 ファイル (`pack-7467d193…idx/pack/rev`) は **正常に install**。ただし**残った loose-object 3 個が同じく dangling symlink**。pack 経路 (rename) は OK・loose 経路 (link2symlink) のみ壊れる |

両ケースで「`unpack should have generated …, but I can't find it`」がユーザー報告と完全一致。

### 真因 (確定)

proot は `--link2symlink` 下で `link(src, dst)` を以下に擬装する:

1. `src` の内容を `<dst dir>/.l2s.tmp_<name>_<rand>0001` (chain 末端の実体) に置く
2. `dst` を `chain 末端の絶対パス` への symlink にする

受け側 `git receive-pack` の処理シーケンス:

1. 隔離一時 dir `objects/tmp_objdir-incoming-XYZ/` 配下に loose object を書く (※proot 上ではこれが既に link2symlink chain の形で書かれることがある)
2. 検証 OK → quarantine→objdir への migrate を **`link(quarantine/<aa>/<sha>, objects/<aa>/<sha>)`** で 1 つずつ移す
3. proot がこの `link()` を「`objects/<aa>/<sha>` を `quarantine/<aa>/.l2s.tmp_obj_*` への symlink」に化かす
4. migrate 完了後、git は **quarantine dir 全体を rmtree** で削除 (`tmp_objdir-incoming-XYZ` が消える)
5. → `objects/<aa>/<sha>` の symlink target が消え、**dangling 化**
6. その直後の receive-pack 自身の検証 read で `unpack should have generated …, but I can't find it` 発火

= **`rename(2)` 非 atomic は誤診**。実際は `link()`-via-link2symlink + quarantine cleanup の組合せ。

### 差し戻し: A 案・B 案の位置

- **A 案 (z2root rename(2) atomic 化)**: 適用しても直らない。proot 側の `link()` 経路で破綻するため。**棚上げ**。
- **B 案 (native passthrough 領域)**: 領域を切れば link2symlink を通らず動くが、ユーザーが `~/foo/.git` を救わないと意味無いと却下済み。
- **新候補 (P 案)**: proot prebuilt 自体を改修するか、proot の `--link2symlink` を外して dpkg 等の影響を受け入れる/別擬装する。本リポでは proot は third-party prebuilt を同梱 (`scripts/build-proot.sh` で AUTO_LATEST、Termux 系)。改修コストは非自明。
- **運用回避 (最即効)**: **受け側を z2root エンジンに切り替える**。z2root の modern linkat (0.8.47+) は `.l2s` chain を作らず実 link or 実コピーで済ますため、本症状は構造的に起きない (z2root 上 fresh bare に push する 6 シナリオで実証済み)。

### 既存の pre-push 経由 quarantine バイパス (ユーザー導入済み)

ユーザーが既に入れている「pre-push フックで `pack-objects → index-pack → update-ref` を直接設置・quarantine を通さない」運用は、まさにこの link2symlink+quarantine cleanup を**回避するため**に効く。新しい知見と整合的。

## 次の選択肢

(a)(b)(c) のうち真因は **「proot の link2symlink × receive-pack quarantine cleanup」** に確定した。z2root 側のコードに非がないことが分かったため、次の打ち手は以下:

1. **運用即効**: 受け側にする tab のエンジンを z2root に切り替える (設定→実行エンジン)。本セッションの dev env が示すとおり同一 SSH 経路で動作するはず。z2root tab 起動 + SSH server 立てで実証可能。
2. **z2root tab を git ホストにする運用文書化**: `docs/HANDBOOK.md` 等に「自端末を git サーバにするなら z2root エンジン」を明記。
3. **proot の `link2symlink` を ユーザー選択で OFF 化**: `ProotLauncher.kt` L306 の `--link2symlink` を裏設定でトグルにする。OFF にすると dpkg/apt 等の hardlink 依存ソフトで EACCES が透ける副作用あり (= 0.8.47 以前の z2root が抱えていた問題と同じ)。
4. **proot prebuilt 改修**: link2symlink emulation 内で「dst の dir name が `tmp_objdir-*` パターンを含むなら hardlink 失敗を素通させる」など、git の quarantine semantics を尊重する patch を当てる。third-party prebuilt なので fork 維持コスト発生。

短期的には **(1) 運用回避** + **(2) 文書化** が圧倒的に低コスト。(3)(4) はユーザー判断。

## 関連

- 真因解析の元: `docs/ja/DESIGN-SPEC.md` §11 (commit `1006430`) ⇒ §11 の「真因 = l2s rename(2) 非 atomic」記述は**誤診と判明したため要更新**
- 別件の OS アップ起因 stale `.l2s`: `docs/Z2ROOT-L2S-RELATIVE-HANDOFF.md`
- 復旧手順: `project_git_link2symlink_recovery` (memory)
- Plan (棚上げ): `/root/.claude/plans/starry-enchanting-treehouse.md`

