# ALPINE-BUNDLE-HANDOFF

同梱 Alpine の「おすすめコマンド」を見直し・追加してから、z2root 上でのビルド確認に進むためのタスク引き継ぎ。
作業方針メモ: 2026-06-09（既知 z2root バグ B クローズ後）。

## 0. TL;DR（次にやること）— 10セッション目（2026-06-09）

**rootfs 再生成・版数 bump 完了。残るは full APK 再ビルド → 本体 UI 導入 → e2e のみ。**

10セッション目で完了（0.8.61-alpha / versionCode 69 / ROOTFS_VERSION 9 のコミットに同梱）:
- proot 上で `FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64` を end-to-end 成功（260 パッケージ・503MiB 展開・`.tgz` 168MiB）。生成物は `app/src/full/assets/alpine-minirootfs-aarch64.tgz` へ移動（F-Droid 運用。main に残すと foss が誤同梱＋full sourceSet の二重 srcDir 衝突になる）。
- `DistroBundle.ROOTFS_VERSION` 8 → 9、`versionCode` 68 → 69、`versionName` 0.8.61-alpha。
- `build-alpine-rootfs.sh` を proot の link2symlink 下でも回るよう堅牢化（下記 §0.1）。

### 0.1 build-alpine-rootfs.sh に入れた link2symlink 対策（重要・再発防止）

proot は hardlink 非対応 FS 上で `--link2symlink` を使い、apk の hardlink dedup を
`.l2s..apk.*`（host 絶対パスを指す symlink ＋ `.000N` 実体）に変換する。これが2か所で壊れた:
1. **`rm -rf` が "Directory not empty" で1パス失敗** → 2パス目で消える（再帰順序の問題）。
   対策: 1回目は失敗握り潰し＋2回目で確実に削除（通常 host は1回目で消え2回目 no-op）。
2. **`tar` が `.l2s.` symlink で ELOOP** → `--exclude='*/.l2s.*' --exclude='.l2s.*'` で除外。
   被参照の正規バイナリは inode 共有で残り、GNU tar が除外 first-link の代わりに次 link を
   実体として保存するため欠落しない（zsh→zsh-5.9 の hardlink 化を実地確認）。
- 併せて host arch 自動検出（apk-tools-static の x86_64 固定を解消）・fakeroot 不可時の直接実行・
  process substitution(`/dev/fd`)→temp ファイル化 も実施（Android ベース環境の制約対応）。

旧 TL;DR: **おすすめコマンドは確定済み**（下記）。`scripts/alpine-packages.txt` に追記済み。残るは **proot で rootfs 再生成 → ROOTFS_VERSION bump → full APK 再ビルド → 本体 UI 導入 → e2e**。

確定した追加パッケージ（`scripts/alpine-packages.txt` に追記済み・未コミット）:
- Tier 5 モダン CLI: `ripgrep`, `fd`, `fzf`, `bat`, `eza`（neovim は見送り。vim/nano 同梱済み）
- Tier 5.5 ネット診断: `netcat-openbsd`, `socat`, `mtr`
- Tier 6 開発: `python3`, `py3-pip`, `nodejs`, `npm`, `build-base`（ユーザー判断: バランス重視＋node/build-base 同梱）
- ※`bc` は既に Tier 3.6 同梱のため追加不要。

⚠️ **再生成は proot で行うこと（z2root では不可）**。理由: `build-alpine-rootfs.sh` は `apk.static`（静的 musl ET_EXEC）を実行するが、これが z2root の自前ローダで segfault していた（B-6＝`docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11 で真因特定・`z2root.c` 修正済み。ただし**実機 e2e 前**＝この環境のインストール済み APK は未修正なので、当面 proot で回す）。重い full ビルドも proot 推奨（[[project_z2root_heavy_build_freezes]]）。

手順:
1. proot エンジンへ切替（本体 UI）。同一チェックアウトなので未コミットのスクリプト変更は保持される。
2. `FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64` で `app/src/main/assets/alpine-minirootfs-aarch64.tar.gz` を再生成。
   - **本セッションで `build-alpine-rootfs.sh` に移植性修正を入れ済み（未コミット）**: ① process substitution(`/dev/fd`)→temp ファイル、② root/IPC 非対応時に fakeroot を介さず直接実行、③ apk-tools-static を**ホスト arch 自動検出**（x86_64 固定→aarch64 対応）。proot(aarch64) でも動く。
3. `DistroBundle.ROOTFS_VERSION`（現在 8）を +1（既存端末へ新パッケージを再展開させる。マーカー `.z2term-version` 比較）。
4. APK サイズ増を確認（full のみ同梱。foss は CDN DL）。node/build-base で +60MB 超見込み。
5. コミット（CLAUDE.md）: `scripts/alpine-packages.txt`＋`scripts/build-alpine-rootfs.sh`＋再生成 `.tar.gz`＋`DistroBundle.kt`(ROOTFS_VERSION)＋versionCode/versionName bump＋本 docs を **1 コミット**。
6. e2e: B-6（`apk.static` 起動）と各既知バグの実機確認を同じ APK 導入サイクルで合流。

> 注: 本セッションで **z2root.c の B-6 修正＋版数 0.8.60-alpha(68)＋README/DESIGN-SPEC/parity handoff 更新**は別コミット（loader 修正）として用意済み。Alpine 同梱拡張はその次のコミット（proot 再生成後）。

## 1. 背景・現状

- 同梱 Alpine rootfs は**カスタムビルド**。素の minirootfs ではなく、`scripts/build-alpine-rootfs.sh` が `scripts/alpine-packages.txt` のパッケージを `apk.static`＋fakeroot で `--root` に展開し、`.tgz` にして assets へ置く。
- 現在のリスト（`scripts/alpine-packages.txt`、Tier 構成）:
  - **Tier 0 コア**: alpine-base/baselayout/keys, apk-tools, busybox(+suid), musl(+utils), ca-certificates, libssl/crypto, ncurses(+terminfo), readline, zlib。
  - **Tier 1 シェル/SSH**: bash(+completion), zsh(+vcs), openssh-client/server, screen。
  - **Tier 2 実用**: coreutils, findutils, grep, sed, gawk, less, shadow, procps-ng, sudo, which。
  - **Tier 3 開発/運用**: curl, wget, git, vim, nano, tmux, htop, jq, rsync, tree, iproute2, tar, gzip, xz。
  - **Tier 3.6 追加ユーティリティ**: zip/unzip, openssl, bind-tools, file, diffutils, patch, bc。
  - **Tier 3.5 dropbear**（proot で sshd が privsep 破綻するため SSH サーバはこちら）。
  - **Tier 4 mosh**（+musl-locales/-lang）。
  - **Tier 5 モダン CLI（9セッション目で追加）**: ripgrep, fd, fzf, bat, eza。
  - **Tier 5.5 ネット診断（同）**: netcat-openbsd, socat, mtr。
  - **Tier 6 開発（同）**: python3, py3-pip, nodejs, npm, build-base。
- フレーバー差: `full` は assets に `.tgz` 同梱、`foss` は同梱せず公式 CDN（`alpine-3.21.0`, SHA-256 固定）から DL（`DistroSpec.ALPINE`, `DistroInstaller.kt:471`）。`effectivelyBundled` で分岐。
- 展開と post-install は `DistroInstaller.kt`（tar 手書き展開／resolv.conf・hosts・apt/pacman 調整／バージョンマーカー書込）。

## 2. 決定事項（9セッション目・2026-06-09 でユーザーと確定）

- **狙い**: 「両方バランス良く」＝モダン CLI（初手体験）＋軽量～中量の開発ツールを同梱。
- **確定した追加**: Tier 5 / 5.5 / 6（上記 §1 の通り）。neovim は見送り（vim/nano で代替）。bc は既存。
- **サイズ判断**: nodejs/npm・build-base は「サイズ大だが同梱する」とユーザー決定。full APK は +60MB 超見込み。foss はサイズ非依存だが実行時 DL 量が増える。
- **制約**: `ROOTFS_VERSION` bump 漏れに注意（漏れると既存端末に新パッケージが入らない）。再生成は proot で（z2root では `apk.static` が動かない＝B-6。§0 参照）。

## 3. 再生成・反映手順（確定後）

```
# 1) リスト編集
$EDITOR scripts/alpine-packages.txt
# 2) rootfs 再生成（FORCE=1 必須。host に fakeroot/curl/tar/gzip 必要）
FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64
# 3) DistroBundle.ROOTFS_VERSION を +1
# 4) full APK 再ビルド（重いので proot で）→ サイズ確認 → 本体 UI で再インストール
```

コミット時の注意（CLAUDE.md）: `app/` 配下（DistroBundle.kt の `ROOTFS_VERSION`）を含むので **versionCode/versionName を上げ、関連 docs（README/DESIGN-SPEC §同梱パッケージ記述があれば）を同コミットで更新して 1 コミット**。⚠️ **`.tgz` は git-ignore なのでコミットされない**（ビルド時に script が再生成し APK へ同梱する build artifact）。リポジトリ側の正本は `scripts/alpine-packages.txt` ＋ `ROOTFS_VERSION`。端末への新パッケージ反映は `ROOTFS_VERSION` bump がトリガ（`.tgz` の中身が git に出ないため bump 漏れに特に注意）。

## 4. B（既知 z2root バグ）の状況＝このタスクの後段

- B-1〜B-5 はコード上クローズ。直近 0.8.59 で B-5（静的 ELF segfault）のローダ 2 修正（static-PIE 向け RELR/RELA RELATIVE 適用＋biased phdr）を `z2root.c` の `load_elf_and_jump` へ移植済み。第3クラッシュ（リッチ static-PIE）は **NDK 固有制約**（bionic static-PIE crt が `.init_array` を呼ばない）と確定＝ローダ解決不能・parity gap ではない。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11。
- **残るは実機 e2e のみ**: 0.8.57 readlink / 0.8.58 B-3 git clone / 0.8.59 static-PIE 起動 の各修正を、本修正入り APK を本体 UI へ導入後に z2root エンジン上で確認。
- 本タスク（Alpine 同梱コマンド追加）と z2root ビルド確認は、どちらも「次の full APK ビルド＋本体 UI インストール」で同時に検証できる＝まとめて 1 サイクルにすると効率的。

## 6. z2root ビルド確認の引き継ぎ（次セッション・最重要）

10セッション目の成果（コミット `418ed67` / 0.8.61-alpha vc69 / ROOTFS_VERSION 9）まで完了。
**11セッション目（2026-06-09）で z2root 上での full APK ビルドが完走**＝次は本体 UI で導入してオンデバイス e2e。状態と手順を以下に固定する。

### 6.0 11セッション目の成果（2026-06-09）
- **z2root エンジン上で `assembleFullRelease` を完走**（`BUILD SUCCESSFUL in 9m41s`・フリーズ無し）。proot 不使用＝parity の前進。
  - 実績コマンド: `./gradlew :app:assembleFullRelease --no-daemon --no-parallel --max-workers=2`（省メモリ・低並列）。フリーズの主因はメモリ逼迫スワップ thrash で、フラグで回避できると実証（[[project_z2root_heavy_build_freezes]] 更新済）。
  - 検証済み APK: `app/build/outputs/apk/full/release/app-full-release.apk`（195MB）。
    - 同梱 rootfs `assets/alpine-minirootfs-aarch64.tgz` = `175,649,800` バイト＝ソースと byte 一致（Tier 5/5.5/6・ROOTFS_VERSION 9）。
    - jniLibs 全同梱: `libz2root.so`/`libz2accept.so`/`libproot.so`/`libproot_loader.so`/`libtalloc.so`/`libz2term.so`。
    - 署名 = `CN=Z2Term, O=ZeroToShip, C=JP`（release 鍵）→ 既存 release インストールへ上書き可。
- CI（GitHub Actions Build & Lint）の `build-proot.sh` 失敗を別途修正・push 済（コミット `d6dcbcf`）。Termux pool がピン版 proot を消す→404 HTML を `ar` が弾く件を、.deb 検証＋最新版フォールバックで解消。

### 6.1 現状（前提）
- 新 rootfs（Tier 5/5.5/6 入り）は `app/src/full/assets/alpine-minirootfs-aarch64.tgz` に配置済み。**ただし git-ignore なので clone/clean 後は消える** → その場合は `FORCE=1 bash scripts/build-alpine-rootfs.sh aarch64` を再実行 → 生成物を `src/main/assets`→`src/full/assets` へ移動（§6.4）。今の作業ツリーには存在する。
- z2root の `apk.static` 起動修正（B-6＝loader の PT_PHDR 無し ET_EXEC 対応）は **0.8.60(`f9bb9dd`) に既に入っている**＝上記 APK で z2root 上でも `apk.static` が動くはず（未 e2e）。
- ⚠️ **インストールは本体 UI 経由のみ**（この環境は full release アプリの中で動く＝`pm`/adb 不可。[[project_z2term_devenv_inside_app]]）。今セッションでユーザーがこの APK を本体 UI から導入予定。

### 6.2 APK ビルドの注意（重要）
- **full APK の重いビルドも z2root で通すのが目的**（[[feedback_z2root_parity_is_the_goal]]）。proot へ逃がさない＝parity を進めない。11セッション目で `--no-daemon --no-parallel --max-workers=2` の省メモリ・低並列フラグで完走を実証（§6.0）。フリーズしたら heap/並列度をさらに下げて z2root 上で再試行（[[project_z2root_heavy_build_freezes]]）。
- ビルド前に `assembleFullRelease` の stale jniLibs/assets 同梱に注意（[[project_z2root_stale_apk_jnilibs]]）。再ビルドは fullRelease の assets 中間物を `rm` してから。投入前に `unzip`+`strings` で `.so`/rootfs 中身を確認（§6.0 で実施済）。

### 6.3 z2root e2e チェックリスト（本体 UI で 0.8.61 導入 → エンジン z2root に切替後）
まず**実行エンジンが本当に z2root か確認**してから（uid/env だけでは proot と区別不可。[[feedback_confirm_engine_first]]）:
1. **ROOTFS_VERSION 9 の自動再展開**: 旧端末で起動時に新パッケージが入るか。`command -v rg fd fzf bat eza node npm python3 gcc nc socat mtr` が全部通る。
2. **B-6 apk.static 起動**: z2root 上で `apk --version` / `apk add <小さなpkg>` が segfault せず動く（0.8.60 loader 修正の実機確認）。
3. **ビルドツール確認（新 Tier 6）**: z2root 上で実際にビルドが通るか＝今回の主目的。
   - `gcc`: `printf '#include <stdio.h>\nint main(){puts("ok");}' > /tmp/h.c && gcc /tmp/h.c -o /tmp/h && /tmp/h`
   - `node`/`npm`: `node -e 'console.log(1+1)'`、可能なら `npm i <小pkg>`（npm の hardlink 展開は z2root の link2symlink 経路を踏む＝0.8.47 で修正済みだが再確認価値あり）。
   - `python3`/`pip`: `python3 -c 'print(1+1)'`、C 拡張なし pkg を `pip install`。
4. **既存 z2root 修正の回帰確認**（同じ導入サイクルで合流）: 0.8.57 readlink / 0.8.58 B-3 ローカル git clone / 0.8.59 static-PIE 起動。詳細は `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11。
- ⚠️ **proot を勧めない**。z2root 上で通すこと自体が目的（[[feedback_z2root_parity_is_the_goal]]）。proot は APK ビルドと対照値のみ。
- ⚠️ 報告症状そのものを出荷物で検証（代用しない。[[feedback_verify_exact_symptom]]）。

### 6.4 アセット配置ルール（再掲）
build スクリプトは `src/main/assets/alpine-minirootfs-aarch64.tgz` に出力（`.tgz` 固定＝aapt の `.tar.gz` 自動解凍回避）。F-Droid 運用で **`src/full/assets/` へ移動必須**（main に残すと foss が誤同梱＋full sourceSet が両 srcDir を読んで二重定義で衝突）。両ディレクトリとも git-ignore。

## 5. 参考

- パッケージリスト: `scripts/alpine-packages.txt`
- rootfs ビルド: `scripts/build-alpine-rootfs.sh`（環境変数 `ALPINE_VERSION`/`ALPINE_BRANCH`/`PKG_LIST`/`FORCE`）
- 展開/post-install: `app/src/main/java/com/zerotoship/z2term/distro/DistroInstaller.kt`
- 同梱バージョン管理: `app/src/main/java/com/zerotoship/z2term/distro/DistroBundle.kt`（`ROOTFS_VERSION`）
- ビルド parity / z2root 残作業: `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md`
