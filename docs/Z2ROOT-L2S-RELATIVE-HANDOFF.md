# Z2ROOT-L2S-RELATIVE-HANDOFF

proot `--link2symlink` が残す `.l2s` symlink の「ホスト絶対パス保持」を根治し、OS メジャーアップ
（例 Android 15→16）でも z2root が壊れないようにするための調査引き継ぎ。**次セッションで根本対処に着手する。**

> 別件の l2s 制約「rename(2) が POSIX atomic を満たさず git の receive-pack 等が壊れる」は
> `docs/ja/DESIGN-SPEC.md` §11（英訳 `docs/en/DESIGN-SPEC.md`）に独立した設計予約として整理済み。本書はそれとは別問題。

## 0. TL;DR（次にやること）

- **現状（0.8.97-alpha・versionCode 105・コミット `7842ef6`・push 済み）= runtime の応急修正は入っている。**
  `host_to_guest`（z2root.c 〜L244-300）に `"/files/distros/<name>/"` マーカーからゲストパスを復元する
  **純粋文字列 fallback** を追加済み。prefix に依らず stale `.l2s` を逆変換でき、健全環境では非発火（退行無）。
  → **実害（zsh が `cannot open shared object file` で起動不能）は runtime で止まる想定。**
- **根本対処の本題＝「`.l2s` がそもそもホスト絶対パスを抱えない／stale 化しない」状態にする。** 候補は §3。
- **🛑 0.8.95 の自爆を繰り返さない（[[feedback-...]] / docs/ja DESIGN-SPEC §z2root 参照）**:
  1. `host_to_guest`/`canonicalize_guest` の**パス変換ホットパスに `realpath()` 等の syscall walk を入れない**
     （全変換が lstat 連打で激重→入力遅延＝キーボード異常）。`realpath` は dangling な stale パスには
     そもそも効かない。
  2. **起動毎に `find <rootfs> -type l` で rootfs 全走査＋symlink 再作成をしない**（起動ブロック／タイムアウト
     で起動不定、誤解決で symlink 破壊→claude 不起動）。
- **検証制約**: ユーザーは **OS ダウングレード不可**＝15→16 退行そのものの e2e 再現は不可。
  → **合成テスト**で代替する（§4）。

## 1. 真因（確定）

proot `--link2symlink` は、ハードリンクを symlink で擬装する際、作成時点の**ホスト絶対パス**を symlink の
リンク先に焼き込む（例 `/data/data/com.zerotoship.z2term/files/distros/archlinux/usr/bin/.l2s.zsh0001.0002`、
あるいは apk/pacman の hardlink dedup で `.l2s..apk.*`）。Android OS のメジャーアップで data ディレクトリの
絶対 prefix 正規化（`/data/data` ↔ `/data/user/0` 等）が変わると、`.l2s` が抱える prefix が現在の rootfs と
食い違い、`host_to_guest` の rootfs/bind 直接照合が外れて stale 絶対パスを素通し → `translate_abs` が rootfs を
二重前置 → ENOENT → ローダが `.l2s` 本体を開けず `cannot open shared object file`（実機 Arch で zsh が該当）。

## 2. 誰が `.l2s` を作るのか（重要・難易度を左右する）

- **現行 z2root は `.l2s` を作らない。** z2root 自前の link2symlink（`z2root.c` 〜L1418 `copy_for_link`）は
  「実ハードリンク試行→失敗時はトレーサがコピー」方式。旧実装（L1420 のコメント）だけが symlink 化していた。
  → 純 z2root 運用（foss は proot 非搭載で z2root 必須）では**新たな stale `.l2s` は増えない**。
- **問題の `.l2s` は proot か旧 z2root が残したレガシー。** rootfs を proot 下で構築/パッケージ導入した履歴か、
  full フレーバーで proot エンジンを使った履歴で生成されたもの。
- **proot はソースビルドではない**。`scripts/build-proot.sh` は **Termux のプリビルド .deb**
  （`proot 5.1.107.77` / `libtalloc 2.4.3`）を取得して同梱（L34-37）。スクリプト冒頭（L9）に
  「当初は proot-me/proot のソースをクロスビルドする方針だったが…」とあり、**proot 本体の link2symlink.c を
  patch するにはソースビルドへの切替が必要**。

## 3. 根本対処の選択肢（次セッションで方針決定）

- **(A) 既存 `.l2s` の安全な一回限り移行（推奨候補）**: rootfs 内の「ホスト絶対パスを指す `.l2s` symlink」だけを
  **相対 symlink へ書き換える**。ただし 0.8.95 の `repairStaleL2sSymlinks` の轍を踏まないこと＝
  - 起動クリティカルパスでブロックしない（バックグラウンド／初回のみ・冪等マーカー）。
  - 全走査 `find` ではなく対象を絞る（`.l2s.*` 命名・絶対 `/data/` ターゲットのみ・既に相対なら skip）。
  - 解決失敗時は触らない（誤解決で壊さない）。
  - 置き場所候補: `ProotLauncher.kt`（ただし非同期・非ブロッキングで）。
- **(B) runtime fallback を恒久解とする**: 0.8.97 の `/files/distros` 復元は prefix 非依存で、z2root はもう
  `.l2s` を作らない。よって**移行せずとも実害は止まる**可能性が高い。まず「(B) で十分か」を評価し、
  不要な (A) を入れない判断もあり得る（過剰実装回避）。
- **(C) proot 側を相対パス保存に patch**: proot をソースビルドへ戻し link2symlink を相対化。重い
  （Termux プリビルド運用からの離脱・F-Droid 適合や版追従の再設計）。foss は proot 非搭載なので費用対効果は要検討。

→ **おそらく (B) で実害は足り、必要なら (A) を最小・非ブロッキングで足す**のが筋。(C) は最後の手段。

## 4. 検証方針（OS ダウングレード不可の代替）

- **合成テスト**: テスト用 rootfs に「絶対パス prefix が現在の rootfs と異なる `.l2s` symlink」を仕込み
  （例 `/data/user/0/...` 形でわざとズラす）、z2root 経由で `open`/exec が通る（= `host_to_guest` の
  `/files/distros` fallback で復元される）ことを確認。開発環境は bionic 実行可・Alpine 直接実行で native 検証可
  （[[project-z2term-devenv-bionic]]）。SSH 横断 e2e は ssh -p 8022/8023/8024（[[reference-crossdistro-ssh-e2e]]）。
- (A) を入れる場合は、移行後に `.l2s` が相対化され、OS prefix を変えても解決が prefix 非依存になることを
  合成 rootfs で確認。

## 5. 主要アンカー（ファイル:行はビルド前に要再確認）

- `app/src/main/cpp/z2root/z2root.c`
  - `host_to_guest`（〜L244-300）= 0.8.97 の runtime fallback（`/files/distros` 復元）。
  - `canonicalize_guest`（〜L280-365）= symlink walk 中に絶対リンク先を `host_to_guest` で逆変換する箇所。
  - `copy_for_link` / link2symlink エミュ（〜L1418-1540）= 現行 z2root は `.l2s` を作らずコピーする実装。
- `scripts/build-proot.sh`（L34-37）= proot は Termux プリビルド 5.1.107.77。
- `app/src/main/java/com/zerotoship/z2term/proot/ProotLauncher.kt` = (A) の移行を置くなら**非同期・非ブロッキング**で。
- 経緯: `docs/ja/DESIGN-SPEC.md` / `docs/en/DESIGN-SPEC.md` の z2root 段落末尾（0.8.95 自爆→0.8.96 撤回→0.8.97 再修正）。

## 6. 履歴

- 0.8.95-alpha(103) `9f2905d`: 15→16 退行を直そうとして自爆（`realpath` をホットパスに＋起動毎 `find` 全走査＋
  symlink 再作成）→ 起動不定・キーボード異常・symlink 破壊。
- 0.8.96-alpha(104) `5124aa8`: 上記2変更を撤回し 0.8.94 挙動へ復帰。
- 0.8.97-alpha(105) `7842ef6`: ホットパス非依存の安全版で再修正（`/files/distros` 復元 fallback のみ）。
  NDK ビルドでコンパイル確認済み。foss/full release ビルド＋GitHub Release は本セッションで実施予定。

## 7. 次セッション更新（0.8.97 実機で z2root なお不可・根本原因仮説の訂正）

**結論: §1 の根本原因仮説（`.l2s` がホスト絶対パスを抱え OS 15→16 で stale 化）は実機ディスク実態と食い違う。**
**0.8.97 を入れた実機でも z2root は `ls` 一発で `cannot open shared object file`。0.8.97 の fallback は発火していない。**
本セッションは proot タブ内からの調査で、z2root の生エラー捕捉には未到達（手段ブロックは下記）。**コードはまだ触っていない。**

### 7.1 確定した事実（実機 0.8.97 / Full / Arch Linux ARM / rootfs gen 9）
- `z2version` = `z2term 0.8.97-alpha (105) / engine: z2root`。**ユーザーは確かに z2root 0.8.97 を走らせている。**
- ユーザーが「ls と打っただけ」で貼った出力には **3 つの別問題が混在**している。分離して扱うこと:
  1. **（致命・z2root のみ）** `/usr/bin/ls: ... cannot open shared object file`。
  2. **（警告・無害）** `ERROR: ld.so: object '/usr/local/lib/libz2accept.so' from LD_PRELOAD cannot be preloaded ... ignored`
     ＝ アプリが z2root 起動時に accept4 シムを意図的に LD_PRELOAD する設計（ProotLauncher
     `z2acceptShimGuestPath = "/usr/local/lib/libz2accept.so"`）。開けなければ ld.so が ignore してシェルは動く
     （ユーザー出力の 3 回目で実際に ls の出力が出ている）。**z2root 本体バグではない**が、z2root が当該 guest パスを
     preload 時に開けないことの傍証ではある。ノイズで誤解を招くので将来は静音化検討。
  3. **（別系統の重大バグ）** `ls` と打って端末に `llslsls` と出る＝**入力重複**、かつ複数起動の出力が入り混じる＝**pty 混線**。
     致命の ls エラーとは独立。これ単体でも「使い物にならない」体感を作る。要・別建て調査。
- **同一 rootfs・同一 `ls` バイナリが proot では正常**。本セッションの調査シェルは **proot タブ**で、`ls` OK・
  `ldd /usr/bin/ls` は ld/libc/libcap すべて解決。**＝ファイル破損ではなく z2root の経路だけで起きるバグ**。
  （proot タブと z2root タブは同時に併存しうる。`z2version` の engine 表記は shared_home 共有マーカー由来で
  「最後に起動したタブ」を映すため、proot タブで叩いても z2root と出ることがある＝表記に注意。）

### 7.2 ディスク上の `.l2s` 実態（§1 仮説の反証）
- `.l2s` symlink は **ホスト絶対パスではなくゲスト絶対パス**を指す。例 `/usr/bin/.l2s.zsh0001 -> /usr/bin/.l2s.zsh0001.0002`。
  最終実体は通常の regular file（943608B）。`/usr/bin/.l2s.*` 多数が同形式で**ゲスト内で整合**している。
- さらに **`ls`・`zsh` は symlink ですらない普通の ELF**（`ls`: `-rwx------ ... ls`、`interpreter /lib/ld-linux-aarch64.so.1`）。
- → `host_to_guest`（z2root.c L248-297）の `/files/distros` 復元 fallback（L283-293）は「ホスト絶対パス＋`/files/distros/<name>/`」
  を手掛かりに復元する実装で、**復元すべきホスト絶対パスがそもそも無い**。よって**この fallback はここでは決して発火しない**。
  「0.8.97 でも z2root が直らない」のはこれで筋が通る。**真の失敗は別系統**であり、平 ELF（`ls`）が z2root で
  `cannot open shared object` になる経路を**生 z2root で捕捉**しないと特定できない。

### 7.3 再現ブロッカー（なぜ本セッションで生捕捉できなかったか）
- **proot タブ内からは z2root エンジンに届かない**: native libs（`libz2root.so` / `libz2accept.so` / `libproot.so`）は
  `nativeLibraryDir = /data/app/~~qdUQ5u0mlEBFBgyJO9Iajg==/com.zerotoship.z2term-2HB-po4C2dJT4IOAt5-0Gg==/lib/arm64`
  にあり、**proot の bind 集合外**（`/data/app` は未バインド）。proot 内では `No such file or directory`。
- z2root を **proot の中で nested 起動すると二重 ptrace でマスク**される（[[project-z2root-ssh-reset-repro]]）。
- **SSH e2e ポート 8022/8023/8024 は現在クローズ**（dropbear/sshd 未起動）。z2root エンジンの生プロセスも無し
  （走っているのは proot `libproot.so` のみ）。
- `z2adb`（セルフ adb）は今 `List of devices attached`（空）＝未ペア。ホスト側シェルへ抜けるには pair→connect が要る
  （ワイヤレスデバッグのペアコード必要。[[project-z2adb-selfadb-e2e]]: pair ポート≠接続ポート）。

### 7.4 次セッションの最短手順（生 z2root 捕捉）
**コードを触る前に必ず生捕捉する。** どちらか:
- **(a) 実機の z2root タブで直接**（最短・user 操作1回）: z2root タブを開き、以下をそのまま実行して出力を貼ってもらう:
  `echo $LD_PRELOAD; ls -l /usr/bin/ls; ldd /usr/bin/ls; head -5 /proc/self/maps; strace -f -e trace=openat,execve ls / 2>&1 | head -40`。
  「どの open が ENOENT か（interpreter `/lib/ld-linux-aarch64.so.1` か、libc か、binary 自身か）」を確定する。
- **(b) z2adb でホスト側シェルへ抜けて直接 nested でない z2root を起動**: `z2adb pair <port> <code>`→`z2adb connect <port>`→
  `z2adb shell`。ホスト（proot 外）から `libz2root.so` を **§7.5 のテンプレ argv** で起動し `ls /` を再現。
  これなら二重 ptrace を避けられる。

### 7.5 直接起動テンプレ（捕捉した実 proot argv・engine を差し替えるだけ）
本セッションで `/proc/<proot>/cmdline` から採取した実コマンド（これの `libproot.so`→`libz2root.so`、末尾 `/bin/zsh`→`ls /` に変える）:
```
proot --kill-on-exit -0 --link2symlink \
  -r /data/user/0/com.zerotoship.z2term/files/distros/archlinux \
  -b /dev -b /proc -b /sys \
  -b /data/user/0/com.zerotoship.z2term/files/shared_home:/root \
  -b .../home_overlay/archlinux/.local:/root/.local  (以下 .cache/.npm/.npm-global/.nvm/.cargo/.rustup/.config/.claude/downloads) \
  -b /storage/emulated/0:/sdcard -b /storage/emulated/0/Android/data/com.zerotoship.z2term/files:/storage/app \
  -b /storage/E773-5EAE:/storage/E773-5EAE -b /storage/E773-5EAE:/sdcard_ext -b /system -b /apex -w /root /bin/zsh
```
z2root 起動時は ProotLauncher 同様 `LD_PRELOAD=/usr/local/lib/libz2accept.so` 相当も付く（z2rootEnv, ProotLauncher.kt L159）。

### 7.6 コードアンカー（要再確認・行はビルド前提）
- `app/src/main/cpp/z2root/z2root.c`: `host_to_guest` L248-297（**発火しない** `/files/distros` fallback L283-293）/
  `canonicalize_guest` L305- （絶対リンク先を host_to_guest で逆変換 L368-369・`.l2s` チェーン walk）/
  exec 経路の host_to_guest L773・L798。
- `app/src/main/java/com/zerotoship/z2term/proot/ProotLauncher.kt`: `z2rootBinary`=libz2root.so(L92) /
  `z2acceptShim`(L104) / `z2acceptShimGuestPath`=/usr/local/lib/libz2accept.so(L107) /
  `useZ2root` 判定(L214-219, Full は libz2root.so 同梱有無で proot fallback) / `z2rootEnv` LD_PRELOAD(L159)。

### 7.7 やってはいけないこと
- **§1 の古い仮説（ホスト絶対 stale prefix）前提で z2root.c を patch しない**。実態と合っていない。
- 0.8.95 の轍（ホットパス `realpath`・起動毎 `find` 全走査・symlink 再作成）を踏まない。
- proot を「直し方」として勧めない。z2root parity が目的（[[feedback-z2root-parity-is-the-goal]]）。proot は対照値のみ。

---

## 8. 2026-06-15 続き（直列で再現確定・instrumented APK 投入待ち）

### 8.1 確定した切り分け
- **直列ループでも 5/8 失敗**（z2root タブ実機）:
  ```
  for i in 1..8; do ls / >/dev/null 2>/tmp/e$i; echo "run$i rc=$?"; done
  → run1 127 / run2 0 / run3 0 / run4 127 / run5 0 / run6 127 / run7 127 / run8 127
  ```
  → **並行性・pty 混線は無関係。`ls` プロセス単体で間欠失敗**。エラーは毎回
  `/usr/bin/ls: error while loading shared libraries: /usr/bin/ls: cannot open shared object file`
  ＝ ld.so が本体 `/usr/bin/ls` を開く `openat` の変換が間欠失敗 → ENOENT を ld.so が報告。
- §7 までの2大仮説は本セッションで dev 直接テストにより**両方棄却済み**（詳細メモリ
  [[project-z2root-scratch-stackgrow-kernel612]]）: (a) kernel6.12 で scratch 書込が EFAULT 説
  → `process_vm_writev` は sp-16KB まで成功し棄却。(b) core パス変換破損説 → dev で実 ls/bash/
  link2symlink/並行を NDK ビルド z2root + 最小 glibc rootfs で再現せず全成功＝健全。
  ＝**真因は実機固有条件下のみ。机上推論を止め、実機 instrumentation で生捕捉する段階**。

### 8.2 入れた instrumentation（**未コミット・診断用スキャフォールド**）
`maybe_rewrite_path`（z2root.c）に既存 `Z2ROOT_TRACE` 機構（`g_trc`/`g_trc_on`、sentinel 無しなら
ゼロ負荷）相乗りで2行追加:
- 変換結果: `[z2trc] xlat pid= nr= guest='...' rc=<host_path_for 戻り> host='...'`（L1619 直後）
- scratch 書込: `[z2trc] scratch pid= sp= base= off= len= wr= errno=...`（L1633 ループ内）
作業ツリー状態（未コミット）: `M app/src/main/cpp/z2root/z2root.c`（instrumentation）+ `M app/build.gradle.kts`（版数 bump）。HEAD=842d8ac。
**版数 0.8.97-alpha(105) → 0.8.98-alpha(106) へ bump 済み**（実機で新 instrumented ビルドが動いているか版数で判別するため）。コミットは真因確定後に行う。

### 8.3 ビルド済み診断 APK
`scripts/gw.sh :app:assembleFullRelease`（半分並列で 1分6秒・BUILD SUCCESSFUL）。
成果物 `app/build/outputs/apk/full/release/app-full-release.apk`（release 署名 = 現行へ in-place 更新可）。
stale 対策に fullRelease 中間物を rm 後ビルド。APK 内 `.so` に `xlat`/`scratch` 文字列存在を確認済
（strings で 2 件）＝ instrumented binary が確かに同梱（[[project-z2root-stale-apk-jnilibs]] 準拠）。

### 8.4 実機での取得手順（次セッション or ユーザー）
1. 上記 APK をインストール（UI 上書き更新）。
2. trace ON: z2root セッションで `touch ~/.z2root_trace_on`（`~`=shared_home、guest `/root` に bind。
   有効化は `ProotLauncher.z2rootEnv` L165、ログ `~/z2root_trace.log`）。
3. **engine を起動し直す**（`Z2ROOT_TRACE` は engine 起動時に一度だけ読まれる）。
   新ルート: ユーザーが **`ssh -p 2222 root@localhost` で z2root に直接 SSH** できる安定シェルを用意済
   （nested 二重 ptrace を避けられる。sshd ホスト engine が sentinel 後に立っている必要あり）。
4. 再現: `: > ~/z2root_trace.log; for i in 1..8; do ls / >/dev/null 2>&1; echo run$i rc=$?; done`
5. 抽出: `grep -nE "xlat|scratch|/usr/bin/ls|ld-linux" ~/z2root_trace.log | tail -80`

### 8.5 ログの読み方（失敗した `ls` の `openat(/usr/bin/ls)` 行で判定）
- `xlat ... rc=<非0>` → 変換 skip でゲストパス素通し ＝ `host_path_for`/`canonicalize_guest` 側。
- `xlat ... rc=0 host=...` だが `scratch ... wr=-1 errno=14(Bad address)` → scratch 書込 EFAULT ＝ kernel/stack 側
  （§8.1(a) の dev テストと矛盾するので、その場合は実機固有のスタック条件を要追加調査）。
- 両方正常で ls 落ち → ld.so 内のさらに下流（loader が渡す引数/auxv 等）。
真因が確定したら、§7.7 の禁則（ホットパス重処理・proot 推奨）を守って修正 → 版数 bump + docs 更新 + 1 コミット。
コードアンカー: `maybe_rewrite_path` L1589-1641（scratch 書込 L1630/L1635）/ `host_path_for` L398（L402 host prefix で -1）/
`canonicalize_guest` L305 / `write_tracee_mem` L180。

---

## 9. 2026-06-15 真因確定＋修正（0.8.99-alpha・versionCode 107）

### 9.1 実機 instrumented trace で真因確定（**§8.5(b) を採択／§8.1(a) を訂正**）
0.8.98-alpha(106) の trace を z2root タブ直走で取得。失敗 run を単独切り出し（毎回 log を空にして
最初の rc≠0 で break）したところ、全失敗 pid で**同形の scratch 書込 EFAULT**:
```
scratch pid=27508 sp=0x7fd08c66a0 base=0x7fd08c5e50 off=0 len=68 wr=-1 errno=14(Bad address)
```
- `base` は `sp - 0x850(=2128)` ＝ **sp を含む present ページ境界 `0x..6000` を越えて、未 grow の下位
  ページ `0x..5xxx` に落ちている**。`process_vm_writev` は**リモート書込でスタックを grow しない**
  （kernel 6.x）ので EFAULT。変換済み host パスを tracee に書き戻せず、ローダが本体/loader/libc を
  開けず `cannot open shared object file`。
- **間欠性の説明**: 失敗は**プロセス起動最初期**（ld.so が本体/libc を開く、スタック low-water≒sp で
  下位ページ未 grow）に集中。後段の locale 読込（スタック伸長済）は `wr=0(ok)` で通る。だから
  `5/8` のように run 単位で割れる。
- **§8.1(a) の dev 棄却は誤り**: dev の最小 glibc rootfs では起動が浅く下位ページが既に present に
  なりやすく EFAULT を踏まなかっただけ。実機は起動初期のスタック条件で踏む。`scratch wr=-1 errno=14`
  ＝ kernel/stack 側、で正しかった。
- なお trace に出る `xlat guest='.../ld-linux-aarch64.so.1' rc=-1 host=''` は host_path_for の
  「既に host prefix＝書き換え不要」(-1) であり**無害**。crash の主因ではない（red herring）。

### 9.2 修正（**1 行＝6 箇所に効く**）
`z2root.c` の `#define SCRATCH_OFFSET 2048` → **`16`**（L565 付近）。scratch を **sp 直下＝sp と同じ
present ページ内**に置く。起動初期でも present floor は sp のページなので確実に書ける。SCRATCH_OFFSET
を使う 6 箇所（L849/L874/L1401/L1508/L1582/L1634）全てに一括で効く。長いパスで 1 ページに収まらない
時のみ下位ページに掛かるが、その場合は write_tracee_mem 失敗→呼び出し側がレジスタ据え置き（安全側）。
§7.7 の禁則（ホットパス realpath・起動毎 find 全走査・proot 推奨）には抵触しない（定数変更のみ）。

### 9.3 残タスク（次セッション）
- **実機 e2e 検証**: 0.8.99-alpha(107) を入れ、z2root タブで
  `for i in 1 2 3 4 5 6 7 8; do ls / >/dev/null 2>&1; echo run$i rc=$?; done` が **8/8 rc=0** になること。
  `ssh`/`zsh-syntax-highlighting` の `.so` open も復活するはず。
- 検証 OK なら **診断 instrumentation（§8.2 の xlat/scratch fprintf）を残すか撤去するか判断**。
  `g_trc_on` ゲートで通常ゼロ負荷なので残置でも可。
- §7.1-3 の**別系統バグ**（入力重複 `llslsls`／pty 混線／LD_PRELOAD libz2accept 静音化）は**未対応**。
  ls の cannot-open とは独立。別建てで。

---

## 10. 2026-06-16 ビルドが端末ごと落ちる件と再開手順（次セッション向け）

修正コミットは済（`4381df7` fix(z2root) … 0.8.99-alpha(107)）。**だが 0.8.99 の APK がまだ作れていない**。
fullRelease ビルドが**端末（＝dev 環境を内包する full release アプリ自身）を 3 回連続で OOM kill**した。

> ⚠️ 2026-06-16 訂正（ユーザー指摘）: **この OOM は z2root 固有ではない。claude 経由の dev 環境は
> 「ずっと proot タブ」で動いており、proot セッションでの fullRelease ビルドが落ちている。**
> さらに **OS15 では同じビルドが通っていた＝OS15→16 の退行**。よって §10.1 を「メモリ要因＝想定内」と
> 片付けるのは誤り。真因（OS16 で何が変わったか）は未確定で、kill 主体（kernel OOM killer か Android LMKD か）を
> **ホスト側 logcat/dmesg で採取**してから判断する（アプリ内からは PSI/dmesg/logcat が SELinux で遮断）。
> 観測事実: 4K ページ・MemFree 205MB・MemAvailable 3.6Gi・zram swap 5.8Gi(4.5Gi 空)・memory cgroup v1
> `/dev/memcg/apps/uid_10268`(ハード上限読めず＝LMKD 管理の公算)。

### 10.1 真因（メモリ）
- dev 環境は full release アプリの**中**で動く（[[project-z2term-devenv-inside-app]]）。
- 実機 RAM 7.4Gi / 空き ~1.7Gi のところへ `gradle.properties` の daemon ヒープ `-Xmx4096m` 予約 +
  fullRelease の **R8 minify**（`isMinifyEnabled=true`）が重なり overcommit → アプリごと kill。
- 「今まで出来ていた」のは daemon が warm／release 以外だった等の差。再起動を繰り返し cold な今は踏む。
- ⚠️ **当初 CMake cold recompile が主犯と誤診**したが、`cxx` 無傷でも落ちた＝主因は R8 のメモリ。
  ただし `cxx` を消すと別途 cold ninja(8コア) で確実に落ちるので**どちらも回避要**。

### 10.2 回避設定（現状＝作業ツリーに未コミットで入れてある）
- `gradle.properties`: `-Xmx4096m→2048m`、`org.gradle.parallel=true→false`。**この変更はコミットしない**
  （ワークアラウンド。PC ビルドでは 4096m のままで良い）。
- ビルドコマンドに `--no-daemon`（終了後 JVM がヒープを抱え続けない）`--max-workers=1` を付ける。

### 10.3 再開手順
```bash
cd /root/tmp/app_project/05_z2term
pgrep -fl java                          # 既存ビルドが生きていないか確認（二重起動回避）
./scripts/build-z2root.sh               # .so を最新ソース(SCRATCH_OFFSET=16)から再生成（軽量・必須）
./scripts/gw.sh :app:assembleFullRelease --no-daemon --max-workers=1
```
- ⚠️ `app/build/intermediates/cxx` は**消さない**（消すと cold CMake/ninja で端末が落ちる）。
- `.so` は gitignore 対象（`app/src/main/jniLibs/arm64-v8a/libz2root.so`）。中身は `SCRATCH_OFFSET=16` 修正済。
- なお落ち続けるなら **PC ビルド**（[[project-multi-device-git-sync]] の git 同期で 4381df7 を pull）が最も確実。

### 10.4 ビルド成功後（本来の残タスク＝§9.3）
- 0.8.99-alpha(107) を本体 UI で install → z2root タブで
  `for i in 1 2 3 4 5 6 7 8; do ls / >/dev/null 2>&1; echo run$i rc=$?; done` が **8/8 rc=0** を確認。
- `ssh -p 2222` は ssh 自身が同バグを踏むので捕捉ルートに使わない（z2root タブで直接）。
- OK なら診断 instrumentation を残すか撤去か判断（`g_trc_on` ゲートでゼロ負荷＝残置可）。

---

## 11. 2026-06-16 続き（0.8.99 でも cannot-open が間欠残存→present ページ境界クランプで再修正 0.8.100-alpha(108)）

### 11.1 0.8.99 実機結果（ユーザー報告）
- **クラッシュせず起動するようになった**（§9 の SCRATCH_OFFSET=16 で頻度は激減）。
- **だが cannot-open は間欠で残存**。`sshd --lan`（dropbear）が初回 `cannot open shared object file` → 再実行で成功、の形。
  ＝ §9 の修正は「頻度を下げた」だけで根治していない。
- 併せて **入力重複 `llslsls`（キーボードバグ）は不変**。これは別系統（§7.1-3 / §9.3）で本セッション未着手。

### 11.2 0.8.99 がなお取りこぼす理由（確定）
`base = (sp - SCRATCH_OFFSET - total) & ~15` は、`total`（書き込むパス等の総バイト）が
**sp の present ページ内オフセット（`sp & (pagesize-1)`）より大きい**と、依然 base が**未 grow の下位ページ**へ落ちる。
- 0.8.94 までの `SCRATCH_OFFSET=2048` は base が常に ~2KB 下＝ほぼ毎回下位ページ＝高頻度失敗（実機 5/8）。
- 0.8.99 の `=16` で base ≈ sp-(16+total)。total が短ければ同一ページに収まるが、**dropbear が開く長い `.so`
  ホストパス**（`/data/user/0/com.zerotoship.z2term/files/distros/archlinux/usr/lib/...` で 100B 超）や、
  **sp がたまたまページ下端付近**に来た run では境界を越え、`write_tracee_mem` が EFAULT →
  ゲストパス素通し → ld.so が `.so` を開けず cannot-open。だから「初回失敗→再実行で成功」の間欠になる。

### 11.3 0.8.100 の修正（present ページ境界クランプ）
`z2root.c` に `scratch_base(sp, total)` ヘルパーを新設（SCRATCH_OFFSET define 直後）。
- 既定は従来どおり `sp - SCRATCH_OFFSET - total`。
- ただし base が **sp を含む present ページ境界 `floor = sp & ~(pagesize-1)` を割り**、かつ
  `total` が present ページに収まる（`sp - floor >= total`）なら **base を floor へ引き上げる**。
  → process_vm_writev が grow できない下位ページを踏まず、確実に書ける位置（present ページ最下端〜sp）へ寄せる。
- `total` が present ページに収まらない場合のみ従来同様 base が下位ページに落ちる＝`write_tracee_mem` 失敗
  →呼び出し側がレジスタ据え置き（ゲストパスのまま＝安全側）。実害は PATH が 1 ページ超の極端時のみ（稀）。
- `g_pagesize` グローバルを `main` で `sysconf(_SC_PAGESIZE)` から設定（4K/16K 端末差・fork した tracer も継承）。
- scratch を使う **6 箇所**（passthrough exec / execve argv blob / readfree temp openat / linkat / sockaddr / 汎用パス変換）を
  すべて `scratch_base()` 経由に統一。§7.7 の禁則（ホットパス realpath・起動毎 find 全走査・proot 推奨）には抵触しない。
- 診断 instrumentation（§8.2 の xlat/scratch fprintf, `g_trc_on` ゲート）は**残置**（再 trace 用、ゼロ負荷）。
- アンカー: `scratch_base` 定義＝SCRATCH_OFFSET(L571) 直後 / `g_pagesize` 設定＝main 冒頭 / 使用 6 箇所は
  `grep -n scratch_base z2root.c`。

### 11.4 残タスク（次セッション）
- **実機 e2e**: 0.8.100-alpha(108) を入れ、z2root タブで
  `for i in 1 2 3 4 5 6 7 8; do ls / >/dev/null 2>&1; echo run$i rc=$?; done` が **8/8 rc=0**、
  かつ **`sshd --lan` が一発で起動**することを確認。ユーザーは PC ローカルでビルド可
  （claude 経由＝full release アプリ内 dev は §10.1 の OOM で不可。ビルドは PC で）。
- なお残るなら trace ON（§8.4）で `scratch ... wr=-1` がまだ出る run の `base`/`floor`/`total` を見る。
- **入力重複 `llslsls`（z2root でキーボードが使えない）= cannot-open と同一原因と確定（2026-06-16）。**
  - 訂正の経緯（§7.1-3 の「別系統バグ」は誤り）: ① **z2root 固有**で proot タブは正常 → 入力経路
    （`TerminalInputView`/IME/`TerminalSession.writeBytes`、proot と同一コード）は**無実**。② **OS15 では問題なし
    ＝OS15→16 退行**。③ **z2root タブで `sh` は正常入力・`zsh` だけ崩れる**。④ **タブごとに稀に成功・殆ど失敗で
    開くたびに違う＝プロセス起動ごとの間欠**。
  - 機序: zsh は対話起動時に ZLE モジュール `.so`（`zsh/zle` 等）を `openat` でロード。その open のパス書換 scratch が
    起動初期に EFAULT（§9/§11 の本体バグ）→ ゲストパス素通し → ENOENT で**モジュール未ロード＝行編集破壊**。
    `sh` はモジュール非使用で無傷。`.so` が運よく読めたタブだけ正常。
  - → **0.8.100-alpha(108) の present ページ境界クランプ（§11.3）で cannot-open とキーボードが同時に直る見込み。**
    残課題: クランプでも `total > (sp & (pagesize-1))` の起動（4K で確率 ~3%）は依然失敗しうる。テストで「稀に壊れる
    タブ」が残るなら **per-tracee で mmap した常駐 scratch ページへ書く方式**（初回トラップで mmap 注入し pid ごとに
    アドレス保持）に格上げして 100% 化する。まずは 0.8.100 で十分か検証してから（過剰実装回避）。
- `libz2accept.so` LD_PRELOAD の `cannot be preloaded ... ignored` 警告は無害だがノイズ。静音化は別途。

---

## 12. 2026-06-16 続き（0.8.100 でなお間欠残存→POKEDATA フォールバックで根治 0.8.101-alpha(109)）

### 12.1 0.8.100 実機結果（ユーザー報告 + trace）
- `ls / ×8` は **8/8 rc=0**（§11 の present ページ境界クランプで素の短命プロセスは安定）。`sshd --lan` も毎回成功。
- **だがキーボード（zsh 行編集）が「たまに駄目」**＝ §11.4 が予告した残ケースが実機で確定。
- trace（`grep -E "wr=-1" ~/z2root_trace.log`）で **scratch 書込 EFAULT が継続**しているのを直接確認。代表 run:
  ```
  scratch pid=859   sp=0x7fd601f000 base=0x7fd601efa0 len=68 wr=-1 errno=14   # sp & 0xfff == 0（ページ境界丁度）
  scratch pid=30604 sp=0x7fe3610030 base=0x7fe360ffc0 len=86 wr=-1 errno=14   # sp の page 内 offset(0x30) < total
  ```
  → `total`（パス長）が `sp & (pagesize-1)`（sp の present ページ内オフセット）より大きい起動で base が下位ページに落ちる。
  特に **sp がページ境界丁度（offset 0）の run は floor==sp で present ページに 1 バイトも置けず、どんなクランプでも救済不能**＝ present ページ境界クランプの原理的限界。

### 12.2 0.8.101 の修正（POKEDATA フォールバック・mmap 注入は不要）
§11.4 は「per-tracee で mmap 注入した常駐 scratch ページ」を 100% 解として予告していたが、**kernel の書込経路の差でもっと単純に根治できる**:
- `process_vm_writev`（`write_tracee_mem`, z2root.c L180）は GUP 経路で**スタックを grow しない** → 未 grow 下位ページで EFAULT。
- **`PTRACE_POKEDATA`** は kernel の `__access_remote_vm` 経由で、vma が無ければ **`expand_stack()` を呼んでスタックを grow** してから書く。→ 同じ下位ページに POKEDATA なら確実に書ける（sp 境界丁度も解消）。

実装（z2root.c）:
- `poke_write_tracee_mem(pid, addr, buf, len)` を新設＝word 境界で PEEKDATA→マージ→POKEDATA。端ワードは既存内容を保全。
- `write_tracee_mem` を「`process_vm_writev` 成功なら 0、失敗（部分書込含む）なら `poke_write_tracee_mem` へフォールバック」に変更。**scratch を使う 6 経路すべてに自動で効く**（呼び出し側は無改変）。
- `scratch_base` のページ境界クランプ（§11.3）は「速い経路のヒット率最適化」として**残置**（POKEDATA は遅いので外れた時だけ）。
- mmap 注入（syscall 化けの入れ子・per-pid ライフサイクル・メインループ改変＝高リスク）は**採らない**。§7.7 の禁則にも非抵触（定数・I/O 経路のみ）。
- 診断 instrumentation（§8.2）は残置。trace では POKEDATA 成功時 `write_tracee_mem` が 0 を返すので `scratch ... wr=0` になる（書込自体は OK）。
- アンカー: `poke_write_tracee_mem` / `write_tracee_mem`（z2root.c L180 付近）。NDK r29 でコンパイル確認済（`./scripts/build-z2root.sh` OK）。**APK は未ビルド（dev 環境は §10.1 の OOM で full ビルド不可＝PC でビルド）。**

### 12.3 実機検証（2026-06-16・**解決確定**）
- 0.8.101-alpha(109) をビルド→install→z2root タブで検証し、**キーボード入力・その他の不安定箇所すべて問題なし**（ユーザー報告）。
  - §9〜§11 の間欠 `cannot open shared object file`、および §11.4 で同一原因と確定した **zsh キーボード崩れ（`llslsls`）が両方とも解消**。
  - → **POKEDATA フォールバックがこの kernel(6.12) で expand_stack 経由でスタックを grow して書けることを実機で実証**＝§11.4 の mmap 常駐 scratch 格上げは**不要**（採らずに済んだ＝過剰実装回避）。
  - これで §1 から続いた z2root の cannot-open / キーボード一連は**クローズ**。

### 12.4 残（任意・小）
- 診断 instrumentation（§8.2 の xlat/scratch fprintf）は `g_trc_on` ゲートでゼロ負荷のため**残置**で可。撤去するなら別コミットで。
- `libz2accept.so` LD_PRELOAD の `cannot be preloaded ... ignored` 警告は無害だがノイズ。静音化は別途（任意）。

### 12.5 ローカルビルドの stale 検証手順（2026-06-16・この環境固有の落とし穴）
- 増分ビルドが `BUILD SUCCESSFUL in 38s` / 大半 `UP-TO-DATE` / `Configuration cache entry reused` で終わるのは**正常**（前回成果物の再利用）。
  「速い＝中身が古い」ではない。だが §10.4（stale jniLibs）の前科があるので、**APK 内 .so の中身照合で確定**するのが正道。
- **照合は `.text` セクションの sha256 比較が決定的**（APK 内は strip 済・source は unstripped でサイズは違うが `.text` は同一になる）:
  ```sh
  unzip -p app/build/outputs/apk/full/release/app-full-release.apk lib/arm64-v8a/libz2root.so > /tmp/apk.so
  cp app/src/main/jniLibs/arm64-v8a/libz2root.so /tmp/src.so
  objcopy -O binary --only-section=.text /tmp/apk.so /tmp/apk.text   # ← ディストロ側のネイティブ aarch64 objcopy
  objcopy -O binary --only-section=.text /tmp/src.so /tmp/src.text
  sha256sum /tmp/apk.text /tmp/src.text   # 一致すれば APK は source .so と同一＝not stale
  ```
- ⚠️ **NDK の `llvm-objcopy`/`llvm-nm` 等（x86-64 ホストバイナリ）は z2root タブ下では動かない**
  （`z2root loader: open(.../ld-linux-x86-64.so.2): No such file` で落ちる＝x86-64 ローダ不在）。
  → stale 検証では **必ずディストロのネイティブ aarch64 binutils（`/usr/sbin/objcopy` 等）を使う**。
  （box64 経由なら NDK ツールも動くが、検証には不要。）
- `poke_write_tracee_mem` は static で -O2 によりインライン化され得るため `nm` で名前が出ないことがある＝**シンボル有無での判定は不可**。`.text` ハッシュ一致で代替する。
- source .so が POKEDATA 版である根拠: `grep -n PTRACE_POKEDATA z2root.c`（L180 付近に存在）＋ `.so` の mtime が `z2root.c` より新しい（`build-z2root.sh` が編集後に走った証拠）。
- 2026-06-16 実績: full APK の `.text` sha256 = source .so の `.text` sha256（完全一致）＝ full release は **not stale を確定**。foss release は直列ビルド中（完了後に同手順で照合）。
