# z2root 引き継ぎ（チャット引き継ぎ） — オンデバイス自己ホストビルド + 静的ELF loader 修正

## 【済】コミット完了（2セッション目）→ 続きは `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §0

**この md の「残作業＝1コミット」は完了済み**: `HEAD=f258210` = 0.8.55-alpha(63)（`e1b97b4` の上に 1 コミット集約、HANDOFF 2 件同梱、push 未）。
proot 下で `assembleFullRelease` も `BUILD SUCCESSFUL`（69MB・release 署名、APK バックアップ済み）。
**次にやること（z2root でのインストール後検証 / item 3・1・4）は `Z2ROOT-BUILD-PARITY-HANDOFF.md` §0 に集約**。本ファイルは音声修正(56db0ed)と (A)/(B) の技術詳細リファレンスとして残す。以下は当時の記録（一部は上記で更新済み）。

---

## 【旧・最重要・2026-06-08 セッション追記】コミット直前で区切り。次チャットは「1コミットするだけ」

**いまの状態（コミットすれば完了）:**
- 作業ツリーは **最終 0.8.55-alpha(versionCode 63) 状態に完全復元済み**。`git status` =
  追跡9ファイル M（`.github/workflows/build.yml` / `README.md` / `app/build.gradle.kts` /
  `app/src/main/cpp/z2accept/z2accept.c` / `app/src/main/cpp/z2root/z2root.c` /
  `app/src/main/java/.../proot/GuiScript.kt` / `docs/en/DESIGN-SPEC.md` / `docs/ja/DESIGN-SPEC.md` /
  `scripts/build-z2root.sh`）＋ 未追跡2件（この2つの HANDOFF md）。
- 0.8.53/0.8.54/0.8.55 の**全変更が1つの作業ツリーに入っている**（版数=63/0.8.55、docs も 0.8.55 反映済み）。
  検証済み: z2root.c case-3=`host_to_guest`入り(688)・SCM_CREDS hunk有・z2accept=`__errno_location` weak(22)・
  build-z2root.sh fallback(80-87)・GuiScript=`setsid pulseaudio`(406)。README Current=0.8.55/Prev=0.8.54,0.8.53。

**⚠️ 前回引き継ぎの訂正:** 前セッションで「commit 61(9c84b30)/62(6a8f9f8) 成功」と報告されていたが**誤り**。
実際は **HEAD=e1b97b4(0.8.52) のままでコミットは1つも作られていなかった**（git 破損復旧の影響か、
途中の作業ツリーが 0.8.53 分だけに巻き戻っていた）。退避パッチから最終状態を再構築して現在地に至る。

**ユーザー決定の変更:** 当初「3コミット再構成」を選択 → **トークン消費が大きいため「1コミット集約」に変更**。
3分割は z2root.c の hunk 分割＋版数/docs の段階往復で Edit/perl が大量に走るのが浪費の正体だった。

**残作業（次チャットがやること＝これだけ）:**
1. 現状の作業ツリーをそのまま **1コミット**。CLAUDE.md 厳守（版数は既に 63/0.8.55・docs 反映済みなので追加編集不要）。
   author: `git -c user.name=orgson -c user.email=270548806+orgsonai@users.noreply.github.com commit`（config は書き換えない）。
   `git add` はファイル明示。`*.jks`/`keystore.properties`/`local.properties` は add しない。`--no-verify` 禁止。**push 禁止**。
   コミットメッセージ案:
   `fix(foss): z2root 配下 GUI 音声無音+静的ELF(bind配下)exec不可+z2accept errno を修正しオンデバイス自己ホストビルドを成立 0.8.55-alpha(63)`
   （0.8.53 音声 / 0.8.54 静的ELF+build-z2root.sh 自己ホスト / 0.8.55 z2accept weak errno を集約）。
   未追跡の HANDOFF 2件を同コミットに含めるかはユーザー確認（既定は含めず未追跡のまま）。
2. コミット後の実機検証（下の「残っていること」3/4 と同じ）: full release ビルド→install→音声/claude 確認。

**退避（揮発注意・`/tmp` 配下）:** `/tmp/z2patch/all_final.patch`＝最終全変更（`git apply` で復元可）、
`/tmp/z2patch/worktree_before_reset.patch`＝途中の 0.8.53 だけ状態。作業ツリーが既に最終状態なので通常は不要。

---

最終更新: 2026-06-08 / ベース: 0.8.55-alpha(63) / ブランチ: main
> **この環境で続行・ビルド・検証する**前提（ユーザ強い要望: 「この環境でビルドできないと意味ない。proot レベルを目指してる」）。
>
> **【2026-06-08 追記・残作業 item 1 完了】** オンデバイス `assembleFullRelease` が proot エンジン下で
> `BUILD SUCCESSFUL`（69MB・release 鍵署名・同梱 `.so` は case-3 + weak-errno の現ソース由来を unzip+readelf で確認）。
> 過程で2点を解消し 0.8.54(62)→0.8.55(63) を追加コミット: (1) `z2accept.c` の `__errno_location` が
> 非 weak 未解決のため `LD_PRELOAD` 漏れで bionic 製 aapt2 が起動失敗→**weak+NULL ガード化**。
> (2) merge 増分キャッシュが旧 `.so` を stale 同梱→**fullRelease 中間物を rm して再ビルド**。
> ⚠️ **z2root エンジン下で重い full ビルドを回すと端末がフリーズする**（ptrace 監督が大量 fork/exec に追いつかない）。
> 重いビルドは **proot へ切替えてから**。残りは実機での音声最終確認とブートストラップ確認（item 3/4）。

## いま何が終わっていて何が残っているか（最重要）

### 終わったこと
1. **GUI 音声修正はコミット済み（`56db0ed`, 0.8.53-alpha(61)）。** SCM_CREDENTIALS sendmsg EPERM /
   PulseAudio `--daemonize` 自己 re-exec の2真因を修正。docs（README / DESIGN-SPEC ja+en）反映済み。
   CI の同梱物欠落ビルド失敗修正（`.github/workflows/build.yml`）も同コミットに同梱済み。
   → **音声修正そのものの作業は完了**。残るのは実機での最終動作確認のみ（下記）。
2. **【今回の新規・本命】z2root が「この環境でビルド不可」だった2つの真因を特定し両方を解消した（未コミット）。**
   - (A) **静的 ELF loader バグ**（`app/src/main/cpp/z2root/z2root.c`, `plan_exec` の case 3）:
     静的 ELF を `--loader` で起動する際、loader へ **ホスト実パス**を渡していた。loader 自身の
     `open()` も tracee として傍受・パス変換されるため、bind 配下（例 `-b <home>:/root` 下の
     NDK 静的 clang）で「ゲストパス扱い→rootfs/二重変換」され ENOENT（`z2root loader:
     open(...linux-x86_64/bin/clang-21): No such file`）。
     → **修正: `host_to_guest()` でゲストパスへ逆変換してから loader へ渡す**（動的 ELF 経路が
     ld.so に guest_real を渡すのと同じ理屈）。これで bind 配下の静的バイナリも正しく map できる。
   - (B) **ビルドツールチェーンの自己ホスト化**（`scripts/build-z2root.sh`）:
     NDK の clang（clang-21）は静的 ELF なので、上記 (A) 未適用の現行エンジン下では exec 不可。
     → **`build-z2root.sh` に自動フォールバックを追加**: NDK clang が exec できなければ
     **exec 可能な rootfs の動的 clang(clang-22) をクロスコンパイラ**として使い、`--target=
     aarch64-linux-android29 --sysroot=<NDK sysroot>` でコンパイル、**GNU ld(/usr/sbin/ld) で
     NDK の静的ライブラリ/crt を手動リンク**（clang ドライバの自動リンクは lld 専用フラグ
     `--use-android-relr-tags` を渡し GNU ld が拒否するため使わない）。
     **PC ビルドは probe（`clang --version` が "clang version" を出すか）を通過して従来どおり
     NDK ツールチェーンを使う＝挙動不変。**

   **検証済み（この z2root term 上で実行）**: `bash scripts/build-z2root.sh` が完走し
   - `libz2root.so` = 438600 byte / ELF AArch64 **EXEC**(非PIE) / statically linked /
     "for Android 29, built by NDK r29" / 依存(NEEDED)なし / stripped（= (A) の case-3 修正入り）
   - `libz2accept.so` = 5056 byte / `accept` を T エクスポート / 依存なし / stripped
   を `app/src/main/jniLibs/arm64-v8a/` に生成。**ネイティブ部分のオンデバイス自己ホストビルドは成立**。

### 残っていること（次チャットの作業）
1. **Gradle 本体のオンデバイスビルド確認**:
   - Gradle daemon の "Connection refused" は **accept(202) seccomp** が真因。
     **`LD_PRELOAD=/usr/local/lib/libz2accept.so` を付ければ JVM の accept が通る**（Java NIO で実証済み）。
   - **実証済み（BUILD SUCCESSFUL）**: `LD_PRELOAD=/usr/local/lib/libz2accept.so
     JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew --offline --console=plain help` が
     **`BUILD SUCCESSFUL in 2m24s`**（daemon 起動＋`:app` 構成成功。"Connection refused" 解消＝シムが効いている。
     ログ: `/tmp/gradle-help.log`）。**= Gradle 本体はオンデバイスで動く**ことが確定。
     次は `:app:buildZ2rootNative`（= 上記 build-z2root.sh を呼ぶ）→ `assembleFullDebug` を試す。
   - 注意: full フレーバは `verifyFullBundledArtifacts`/`verifyBundledFonts` が走るため、
     fonts/PRoot/rootfs の同梱物が無いと停止する。無ければ `scripts/fetch-fonts.sh` /
     `build-proot.sh` / `build-alpine-rootfs.sh` を先に流す（CI と同じ）。debug の軽い検証だけなら
     `:app:buildZ2rootNative` 単体で native 自己ホストの確認になる。
2. **コミット（CLAUDE.md 厳守）**: 今回の未コミット差分 = `z2root.c`(case-3) + `scripts/build-z2root.sh`。
   `z2root.c` は `app/` 配下なので **versionCode 61→62・versionName 0.8.53→0.8.54 へ bump 必須**、
   かつ関連 docs（README / DESIGN-SPEC ja+en の版数ヘッダと z2root 履歴・落とし穴）を更新してから **1 コミット**。
   コミット案: `fix(foss): z2root の静的ELF(bind配下)exec不可を修正＋build-z2root.sh をオンデバイス自己ホスト化(NDK clang不可時に rootfs clang+GNU ld へ自動fallback) 0.8.54-alpha(62)`
   - **(A) と (B) は密結合（A が無いと自己ホストした z2root は静的バイナリを exec できない／B が無いと A 入り .so をオンデバイスで作れない）なので 1 コミットが妥当。** push はユーザ指示まで禁止。
3. **ブートストラップの注意（重要）**: (A) の case-3 修正は **再ビルド+インストール後の APK にしか効かない**。
   現行インストール済みエンジンは (A) 未適用なので、いま動いているシェルから NDK 静的 clang は依然 exec 不可。
   だから (B) のフォールバックで「現行エンジン下でも .so を作れる」ことが要。**(A) 入り APK を一度
   インストールすれば、以後 z2root は NDK clang(静的)も直接 exec できるようになり完全自己ホストになる見込み**。
   次チャットでは「(A)+(B) 入り APK をビルド→インストール→ そのエンジン下で NDK 直叩きが通るか」を確認すると締まる。
4. **音声の実機最終確認（56db0ed の検証）**: z2root エンジン + GUI 起動で音が出る。
   `/tmp/z2gui-audio-<display>.log` に "Connection died" が出ない。`pactl info` が通り `z2sink` が見える。proot 回帰なし。

## 環境メモ（この term の実体）
- 現在 **z2root エンジン下**で uid=0（fake_root）。`/usr/sbin/clang`=clang-22(musl,動的,exec可), `/usr/sbin/ld`=GNU ld 2.46(aarch64対応), `/usr/sbin/strip` あり。`ld.lld`/`lld` は **無い**（だから GNU ld を使う）。
- NDK: `/root/android-sdk/ndk/29.0.14206865`（local.properties: sdk.dir=/root/android-sdk, ndk.version=29.0.14206865）。
  `prebuilt/linux-aarch64` と `linux-x86_64` の clang-21 は **同一の aarch64 静的 ELF**（aarch64 ホスト向けに x86_64 ディレクトリにも aarch64 を入れた構成）。build-z2root.sh は HOST_TAG=linux-x86_64 を使う。
- sysroot static libs/crt（`libc.a` `crtbegin_static.o` `crtend_android.o` 等）と
  `libclang_rt.builtins-aarch64-android.a`(`.../lib/clang/21/lib/linux/`) は揃っている。
- Java17 OpenJDK at `/usr/lib/jvm/java-17-openjdk`。`java`=`/usr/sbin/java`。
- accept シム guest 配置: `/usr/local/lib/libz2accept.so`（ProotLauncher.ensureAcceptShim が置く。現状 2384 byte の旧ビルド・gradle 用途には十分）。

## build-z2root.sh フォールバックの中身（参考: 既に実装・適用済み）
- probe: `"${CC}" --version 2>/dev/null | grep -q 'clang version'` が偽なら `FALLBACK=1`
  （loader エラーでも exit code は 0 になり得るため exit code では判定しない）。
- 上書き可能 env: `Z2ROOT_HOST_CC` / `Z2ROOT_HOST_LD` / `Z2ROOT_HOST_STRIP`。
- z2root(static): `SYS_CC -c` → `SYS_LD -EL -static -no-pie --hash-style=gnu -z noexecstack
  -z max-page-size=4096` で crtbegin_static.o + obj + (--start-group -lc -lm -ldl --end-group) +
  builtins + crtend_android.o をリンク → strip。
- z2accept(shared): `SYS_CC -c` → `SYS_LD -EL -shared -soname libz2accept.so --hash-style=gnu
  -z noexecstack -z max-page-size=4096`（max-page-size を 4096 にしないと 64KiB に膨らむ）→ strip。

## 適用済み音声修正（56db0ed・参考）
- `z2root.c`: fake_root 配下で `sendmsg(211)`/`recvmsg(212)` をトレースし `SCM_CREDENTIALS` の uid/gid を書換
  （送信=実 uid/gid、受信=0）。`struct config` に `real_uid/real_gid`、`main()` で `getuid()/getgid()` 保持。
- `GuiScript.kt`(`start_audio`): `pulseaudio -n --daemonize=yes …` → `setsid pulseaudio -n
  --exit-idle-time=-1 … </dev/null >/dev/null 2>&1 &`（`--daemonize` 廃止＝自己re-exec回避）。停止は `pactl exit`。

## git
- `.git.corrupt.bak/`（開始時の破損 .git 退避・未追跡）は**コミット前に削除**（`git add` はファイル明示）。
- 過去のコミット author: `orgson <270548806+orgsonai@users.noreply.github.com>`。git config 未設定なら
  `git -c user.name=... -c user.email=... commit`（config は書き換えない＝CLAUDE.md）。
