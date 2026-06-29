# Z2ROOT-BUILD-PARITY-HANDOFF

z2root エンジン上で `assembleFullRelease` 等の重いビルドを proot 同等以上に通すための調査引き継ぎ。

> **🛑 最重要（18セッション目・2026-06-10・0.8.69-alpha(77)）**: `Alpine Linux`（musl）が z2root で起動即死（`exitCode=-1`）する退行を**真に**根治。0.8.67/0.8.68 は症状の一部しか塞いでいなかった。**真因は `ld-musl-aarch64.so.1` が PT_INTERP を持たない（自身がインタプリタ）ため `plan_exec` で case 2(動的)を外れ case 3(静的扱い・`skip_reloc=0`)に落ち、loader が RELATIVE/RELR 肩代わり＋phdr バイアスを「自己 relocation する musl ld.so」に誤適用していたこと**。実機トレースで `open(ld-musl)` 直後 `SIGSEGV si_addr=0x132c0`（=base 0）を確認。musl `dlstart.c` は `base = 実行時&_DYNAMIC − AT_PHDR内 PT_DYNAMIC.p_vaddr` で base を求めるため、phdr バイアスすると二重算入で base=0。glibc は GOT 相対で AT_PHDR 非依存ゆえ無傷（Arch/Kali 正常）。**修正(0.8.69)**: `.note.android.ident`(owner `Android`) で bionic ELF を判別し、肩代わり＋バイアスを `apply_loader_reloc = (!skip_reloc && is_bionic)` でゲート＝bionic static-PIE のみ救済、glibc/musl は自己 relocation に委ねる。`build-z2root.sh` 成功（md5 `a5ffa741…`）。**✅ コミット済み `9dcddf9`・full APK 再ビルド＋バックアップ済み**（APK 同梱 `libz2root.so`=`511ab30dac4e991e2629c22db348519d`・452800B＝stale でないことを unzip+md5 で確認・旧 `0c8c34aa` と相違）。**⏳ 残りは Alpine 実機 e2e のみ（次段）**。**この退行を version 違い/stale APK/stat/readlink/phdr バイアス単体で追わない**＝真因は case 3 の bionic 誤判定。詳細は §10「0.8.69 で Alpine(musl) 起動退行を真に根治」。
>
> （17セッション目・0.8.68-alpha(76)）: phdr バイアスを `!skip_reloc` でゲートしたが、case 3 に落ちる ld-musl には効かず Alpine 退行は継続。`116c538`。0.8.69 で訂正・上記参照。

> **（16セッション目・2026-06-10・0.8.67-alpha(75)）**: `Arch Linux ARM` 起動退行（`exitCode=-1`）の**真因はローダの二重 relocation**だった。**0.8.62〜0.8.64 の stat 偽装をめぐる一連の「修正」は誤診で、この退行とは無関係**（それらは B-3 git clone hardlink 用の別系統で、それ自体は有効）。真因＝0.8.59 で入れたローダの RELATIVE/RELR 肩代わりが、自己 relocate する `ld.so` にも当たって load bias 二重加算（全ポインタ ×2）→ SIGSEGV。0.8.67 で `skip_reloc`（動的経路=ld.so は肩代わり OFF・静的直接ロードは ON 維持）により根治。詳細は §10「0.8.67 で起動退行を真に根治」。**✅ 2026-06-10 実機 e2e 完了**: 0.8.67 APK を本体 UI 導入後、z2root で `Arch Linux ARM` 正常起動を確認（起動退行解消）。同セッションで **B-3 git clone hardlink も e2e 合格**（`git clone` が `--no-hardlinks` 無しで成功・`fsck` clean・`ln` 実 hardlink 退行なし）。

作業日: 2026-06-10（16セッション目=**0.8.62〜0.8.64 の起動退行修正は誤診と判明・真因はローダ二重 relocation・0.8.67 で根治**。15セッション目=**0.8.63 でも直らなかった起動退行を 0.8.64 で実際に修正**＝`(dev,ino)` 照合は dev 不識別で無効＝stat 偽装をパス相関化。14セッション目=**0.8.62 が招いた z2root 起動退行を 0.8.63 で修正試行**＝dormant だった inode-only stat 偽装の覚醒。13セッション目=稼働版数 0.8.61 確定・readlink 切り分け・**B-3 再修正 0.8.62**。9セッション目=B-6 追加。8セッション目=B-4/B-5 着手）/ **0.8.64-alpha(72)**（B-3 退行の真の修正＝stat 偽装の照合を inode/`(dev,ino)`→**コピー先ホスト実パス相関**へ。§10「0.8.64 で起動退行を実際に修正」）。**Option B 2件（.l2s open / aapt2 --argv0）は z2root 実機で e2e 検証完了。** 残るは 0.8.57 readlink 修正＋**0.8.62〜0.8.64 B-3**＋0.8.59 static-PIE 修正＋0.8.60 B-6（apk.static）の実機 e2e（次回インストール後）。

## 0. TL;DR（次にやること）

**🆕 最新状態（18セッション目=Alpine(musl) 起動退行を真に根治・2026-06-10）:** 詳細は §10「0.8.69 で Alpine(musl) 起動退行を真に根治」。
- **🛑 真因＝case 3 への誤分類＋bionic 救済の誤適用**: `ld-musl-aarch64.so.1` は PT_INTERP を持たない（自身がインタプリタ）ため `plan_exec` の `read_elf_interp` が 0 を返し case 2(動的)を外れ、**case 3(静的扱い・`skip_reloc=0`)に落ちる**。そこで loader が bionic static-PIE 用の RELATIVE/RELR 肩代わり＋phdr バイアスを「自己 relocation する musl ld.so」に誤適用 → 二重 relocation ＆ musl の base 自己算出（`base = 実行時&_DYNAMIC − AT_PHDR内 PT_DYNAMIC.p_vaddr`）が二重算入で 0 → SIGSEGV。実機トレースで `open(ld-musl)` 直後 `si_addr=0x132c0`(base 0) を確認。glibc は GOT 相対で AT_PHDR 非依存ゆえ無傷。
- **✅ コード修正済み＋コミット済み（0.8.69-alpha・versionCode 77・コミット `9dcddf9`）**: `z2root.c` `load_elf_and_jump` で PT_NOTE を走査し `.note.android.ident`(owner `Android`) の有無で `is_bionic` を判定、`apply_loader_reloc = (!skip_reloc && is_bionic)` を RELATIVE/RELR 肩代わりと phdr バイアスの**両ゲート**に使用。bionic ELF のみ救済、glibc/musl は note 無し→肩代わり一切なしで自己 relocation に委ねる。判別子は実機 `readelf -n` で `libz2root.so`=Android note 有・`ld-musl`=無 を確認。docs（README / DESIGN-SPEC ja+en / 本 §0・§10）反映済。
- **✅ full APK 再ビルド＋バックアップ済み（2026-06-10）**: `build-z2root.sh` 成功（md5 `a5ffa741…`）→ full release ビルド（box64 aapt2 override・`--max-workers=4`）。**APK 同梱 `lib/arm64-v8a/libz2root.so`=`511ab30dac4e991e2629c22db348519d`・452800B**（unzip+md5 で確認＝**stale でない**・旧 stripped `0c8c34aa` と相違）。`~/z2-apk-backup/app-full-release-0.8.69-9dcddf9-z2root.apk`（195207231B・apk md5 `7822dcd4…`）にバックアップ。
  - ⚠️ **stale APK の罠を回避済み（[[project_z2root_stale_apk_jnilibs]]）**: 当初ビルドは `merged_jni_libs` が UP-TO-DATE のまま古い `.so`(`0c8c34aa`) を供給し APK が stale 化した。**`merged_native_libs`/`stripped_native_libs` だけでなく上流 `merged_jni_libs` も rm** し、`--no-build-cache` で再ビルドして解消。再ビルド時は `merged_jni_libs`+`merged_native_libs`+`stripped_native_libs`+`cxx`+出力 APK を全て消すこと。
- **🔜 残り**: **Alpine 実機 e2e のみ**＝0.8.69 APK を本体 UI で導入し z2root で `Alpine Linux`(musl) 起動を確認（`exitCode=-1` 解消）。glibc(Arch/Kali) は無傷を維持しているはずなので併せて regression 確認。
  - ⚠️ **ビルド環境の罠（[[project-build-aapt2-box64-binfmt]]）**: aapt2 は x86-64 で box64 経由。セッション再開で `binfmt_misc` 登録が消え（再 mount は ENOSYS）、gradle が `CANNOT LINK EXECUTABLE "/system/bin/linker64"` / `Daemon startup failed` で `processResources` 失敗する。box64 ラッパーを `-Pandroid.aapt2FromMavenOverride=/root/.local/box64-aapt2/aapt2` で差し替えて回避（tracked file は触らない）。
- **📌 訂正**: 17セッション目の「棚上げ＝case 3 直接 exec は本退行に無関係」は**誤り**だった。まさにその case 3 直接 exec(ld-musl)が真因。

---

**（16セッション目=起動退行の真因確定と根治・2026-06-10）:** 詳細は §10「0.8.67 で起動退行を真に根治」。
- **🛑 真因確定＝ローダの二重 relocation（stat 偽装ではない）**: `🐠Arch Linux ARM を起動中…`→`exitCode=-1` の即死は、0.8.59 で `load_elf_and_jump` に入れた RELATIVE/RELR 肩代わりが、全動的バイナリの起動経路でロードされる `ld.so`(`ld-linux-aarch64.so.1`) にも当たり、自己 relocate する ld.so の load bias を二重加算→全 RELATIVE ポインタ ×2→`blr x8`(x8=実値×2) で SIGSEGV していた。**0.8.62〜0.8.64 の stat 偽装修正はこの退行の真因ではなかった（誤診。ただし B-3 git clone hardlink 用としては有効なので残置）**。決定的証拠＝SIGSEGV 全レジスタダンプで `pc==si_addr==x8==実 ld.so アドレス×2`（`0xf8789f5640==0x7c3c4fab20×2`、別 run も一致）。
- **✅ コード修正済み（0.8.67-alpha・versionCode 75・未コミット）**: `z2root.c` のローダ肩代わりを `skip_reloc` でゲート。`plan_exec` の動的 ELF/動的 interp 経路（loader 対象＝ld.so）は `wrap_with_loader(...,1)`→`--loader-noreloc` で肩代わり抑止、静的 PIE 直接ロードのみ `--loader`（0.8.59 維持）。`load_elf_and_jump` に `skip_reloc` 引数追加、`loader_main`/`main` が新トークン受理。子 argv は不変。
- **✅ native `.so` ビルド済み・検証済み**: `bash scripts/build-z2root.sh` 成功、`strings libz2root.so | grep loader-noreloc`＝2件。docs 反映済み（README / DESIGN-SPEC ja+en / 本 §0・§10・§B-5）。
- **✅ 完了（2026-06-10）＝full APK 再ビルド→導入→e2e**: 0.8.67-alpha(75) を full release ビルド（中間物 rm 後・通常並列・release 署名 `CN=Z2Term`・APK 同梱 `libz2root.so` に `loader-noreloc`×2 確認＝stale でない）。本体 UI 導入後、z2root で `Arch Linux ARM` 正常起動＝**起動退行（0.8.62〜0.8.66）解消**。**B-3 git clone hardlink も同セッションで e2e 合格**（§10「0.8.64 で起動退行を実際に修正」/ §12.6 チェックリスト合格）。
  - ⚠️ **stale jniLibs の罠**（[[project_z2root_stale_apk_jnilibs]]）: 再ビルド前に `rm -rf app/build/intermediates/{merged_jni_libs,merged_native_libs,stripped_native_libs}/fullRelease` ＋出力 APK。コマンド例 `./gradlew :app:assembleFullRelease --no-configuration-cache --rerun-tasks`。生成後、APK 同梱 `libz2root.so` に `loader-noreloc` 文字列が在ることを unzip+strings で必ず確認（stale でないこと）。
  - **残る e2e**: 0.8.57 readlink（このゲストに `.l2s` チェーン不在＝§12.5 無影響項目）/ 0.8.59 static-PIE（§B-5 ハーネス検証済）/ 0.8.60 B-6 apk.static（Arch ゲストに apk.static 不在）はいずれも当ゲスト環境にテスト対象が無く live 検証不可。Alpine ゲスト or `.l2s` 生成環境で別途。
  - 重い full ビルドは z2root でも proot でも可（[[project_z2root_heavy_build_freezes]] は当初仮説で、0.8.62 を z2root 上 16m58s フリーズ無し完走の実績あり＝§0 既存記述）。
- **診断ログの後始末（要判断）**: 真因ハント用に `z2root.c` へ入れた診断（`Z2ROOT_TRACE` 配下の RL-rewrite ダンプ・SIGSEGV 全レジスタダンプ）は `g_trc_on` ゲートで本番無害。残置で可だが、コミット前に「保持/削除」を決める。
- **コミット**: ユーザー指示後に「バージョン上げ済み＋docs 反映済み」を 1 コミットで（CLAUDE.md 準拠）。署名鍵/`local.properties` は除外。

**最新状態（13セッション目=稼働版数の確定と readlink 切り分け・2026-06-09）:** 詳細は §12。
- **稼働 `.so` は 0.8.61 で確定**。`/proc/self/mem` から稼働 `libz2root.so` の text セグメント（vaddr `0x218000`-`0x26c000`, file off `0x18000`, len `0x54000`）を dd で抜き、APK 同梱 0.8.61 成果物の同 file offset 範囲と **md5 バイト完全一致**（両者 `9b1ac58ffcaae0bc3c719385a2bc822b`）。z2root の case-3 ローダは `libz2root.so` を tracee 自身にも `0x200000` へマップするため、`-b /data/app` 無し（§12.3 で md5 照合不能とした件）でも稼働 text を読める。エンジンは z2root 確定（tracer comm=`libz2root.so` PID15518 / cmdline `proot … -0 --link2symlink -r …/distros/archlinux …`）。
- **⛔ §12 の「3症状＝旧版(≤0.8.56)」は撤回。** 12セッション目の「stale .so 仮説」（[[project_z2root_engine_stale_process]]）は**否定**された。今回 PID は別プロセス(15518)で再起動済だが症状(1)(2)は出続け、かつ text は 0.8.61 一致＝**旧プロセス保持ではない**。
- **症状(1) readlink 19B は版数の証拠にならない（§12.2 テスト#1 無効）**。`readlink` を追って判明:
  - 19B 切れは GNU coreutils の **bare `readlink`（areadlink 経路）固有**で、host→guest 変換でパスが縮む symlink なら起こる。**`/proc/self/cwd`（magic link）でも 19B 再現**＝`.l2s` 固有でも版数固有でもない。
  - 同じ symlink を **大バッファで読む python/perl/C(bufsiz≥182)・`readlink -f` は full(137B)** を返す＝**§9 修正は機能している**。普通の（guest 格納）symlink は coreutils でも full。
  - **ビルド無影響**: `.l2s` チェーンを `open`/`read`/`stat -L` で辿れ ELF マジック取得・実体 9290184B 到達。リンカの `-lc++_shared` は open ベースで 19B 切れと無関係。
  - 機構: bare `readlink` は glibc 内部で readlinkat を呼ぶ（LD_PRELOAD 捕捉不可と確認）。z2root exit(`z2root.c:873`)が変換後 guest 結果を tracee bufsiz へ clamp し、内部 bufsiz が小さいと先頭19文字 `/root/android-sdk/n` に切れる。根は **symlink target の格納形式**: 現行作成の symlink は guest パス格納（st_size=guest 長, 変換不要）、レガシー `.l2s` は旧 proot/z2root が**ホストパス格納**（st_size=182=host 長）。z2root は symlink の st_size を変換しない（該当コード無し）。
- **症状(2) `git clone` B-3 は切り分け完了＝0.8.58 修正が構造的に未発火と確定（200/200 別 inode・0 fake）。0.8.62 で再修正**（旧 `linkcopy_record` の再 `stat()` を `copy_for_link` 内の生成直後 fd `fstat()` へ置換）。詳細は §10 末尾／§12.4(2)。
- **🛑 0.8.62 は z2root 起動退行を招いた → 0.8.63 は直せず → 0.8.64 で実際に修正**: 0.8.62 導入後、z2root でゲスト（`Arch Linux ARM`）が起動直後に `exitCode=-1` で即死。真因＝linkcopy の記録が初めて成功した結果、それまで dormant だった stat 偽装ホットパス（`newfstatat`/`fstat`/`statx` exit）が常時 ON になり、その照合が **inode 番号だけ**だったため起動中の無関係ファイルの inode 衝突で `st_dev`/`st_ino` を誤偽装していた。0.8.63 は照合キーを `(dev, ino)` 両方へ厳格化したが、**dest は rootfs bind 配下＝ゲスト全ファイルと同じ host `/data` パーティション上で `st_dev` が全域一定＝dev 不識別**のため無効で、ユーザー報告どおり 0.8.63 でも即死継続。**0.8.64 で stat 偽装を「対象パス==コピー先のときだけ偽装」するパス相関方式へ置換**して根治（誤ヒットは原理的に起きない。fd ベース `fstat` は inode 偽装対象外。B-3 偽装は維持）。詳細は §10「0.8.64 で起動退行を実際に修正」。
- **✅ 0.8.62 APK ビルド完了（z2root 上・`BUILD SUCCESSFUL in 16m58s`・`--no-daemon --no-parallel --max-workers=2`・フリーズ無し）。** `app/build/outputs/apk/full/release/app-full-release.apk`（195MB・release 署名）。同梱 `libz2root.so` が今回修正入りであることを `strings` で確認済（旧版に無い `[z2trc] linkcopy REC/FIND/FIND statx` 文字列が3種とも存在＝stale 同梱でない）。source jniLibs（未 strip）md5 `f976871…`／APK 同梱（strip 済）md5 `541016b…`。**※この 0.8.62 APK は起動退行版（0.8.63 も未修正）。導入は 0.8.64 を再ビルドしてから。**
- **🔜 次の最優先＝0.8.64 APK を proot で full ビルド → 本体 UI で再インストール → z2root で (1) `Arch Linux ARM` 正常起動、(2) B-3 git clone hardlink 検証（§12.6）を e2e 確認。** git clone がローカルリポ間で hardlink 検証を通れば B-3 完了。
- **次点**: 残る症状(3) `tar` hard link を §12.4(3) のとおり再現条件から切り分ける。版数照合は `/proc/self/mem` text md5 を正本とする（§12.3 改訂）。
- **トレースは後付け不可**（切り分けは C プローブ等で代替した）: tracer は起動時に `Z2ROOT_TRACE` を1回 `getenv` するだけ（`z2root.c:884` `trc_init`・main で1回）。`~/.z2root_trace_on` を置いて**エンジン再起動**しないと有効化されず、二重 ptrace でネスト起動も不可。実機トレースが要るなら sentinel を置いてユーザーが UI から z2term を再起動する必要がある。
- ⚠️ **版数の罠（従来注意・有効）**: `Z2ROOT_LOADER_DEBUG=1` の loader 出力は **0.8.25(`3118c22`) からある古い機能**で新版の証拠にならない。
- 検証できた範囲: ビルドツール `gcc`/`node`/`npm 11.16.0`/`python3` は z2root 上で動作。apk.static(B-6) 判定は未達（同梱 Alpine の `sbin/apk` は dynamic PIE(INTERP=musl) で ArchLinux rootfs では INTERP 不在で起動不可。B-6 には static 非PIE ET_EXEC バイナリが要る）。

**（履歴）最新状態（8セッション目=B-4/B-5 着手時点・2026-06-09）:**
- **B-4（SSH 認証直後 reset / `chmod(/dev/pts)` EPERM 疑い）= 既に修正済み・実機検証済み**。修正は `z2root.c:957`（fchmodat 系 52/53/54/55/151/152/159 を fakeroot で EPERM→0 偽装）に現存。実機 v54 で 2026-06-07L に PTY 疎通成功を確認済み（[[project-z2root-ssh-reset-repro]]）。**残はヘッドレス不可能な実機再確認のみ**＝コード作業は無い。
- **B-5（静的バイナリ segfault）= ローダ 2 修正を 0.8.59 で z2root.c へ移植済み・第3クラッシュは NDK 固有制約と確定**。詳細は §11。要点:
  - 真因＝**static-PIE（ET_DYN）**。bionic NDK の static-PIE crt は (a) 自己 relocation を行わず、(b) `__libc_init_mte`/`__bionic_get_tls_segment` が load_bias=0 を即値仮定（phdr p_vaddr を絶対アドレス扱い=ET_EXEC 前提）。static 非PIE（ET_EXEC）と NDK clang/lld 自身（ET_EXEC）は元々動く。
  - ローダ側 2 修正で **単純 static-PIE と static 非PIE が両方動く（退行なし）**: ① `load_elf_and_jump` が RELR/RELA の `R_AARCH64_RELATIVE`(1027) を自前適用、② `AT_PHDR` に p_vaddr を base で事前バイアスした phdr コピーを渡す。**0.8.59 で `z2root.c` へ移植済み・コンパイル＆ハーネス再検証済み**（実機 e2e は APK 導入後）。
  - **第3クラッシュ＝NDK 固有制約と確定（ローダ解決不能・parity gap ではない）**: printf/malloc/pthread/TLS を使う「リッチな」static-PIE（`/tmp/t2_pie`）が `__strchr_aarch64` で `x2=NULL` segfault。**IRELATIVE 仮説は否定**。真因は **bionic NDK の static-PIE crt が `.init_array` コンストラクタを呼ばない**ため atrace 用グローバル（name ポインタ）が NULL のまま `strchr(NULL)`。constructor 付きソースを PIE/非PIE で対照し、非PIE のみ `CTOR_RAN` が出ることで裏取り済み。コンストラクタは `main` 前に走る必要がありローダは jump 後に制御を失う＝後追い不可で、proot/カーネルでも同結果。
  - 検証ハーネス（**`/tmp` は揮発注意・要再生成**）: `/tmp/test_loader.c`（動的 EXE としてビルドし `load_elf_and_jump` を in-process 検証＝proot 干渉回避）、テスト対象 `t_exec`/`t3_exec`(static非PIE)・`t_pie`(単純static-PIE)・`t2_pie`(リッチstatic-PIE)・`t3_pie`(constructor付きstatic-PIE)。z2root.c の `load_elf_and_jump` と同一ロジック。
- **C（IME Viterbi 強化）/ full build parity 通し確認 は今回スコープ外**（ユーザー指示は B-4/B-5）。

**（履歴）7セッション目=既知バグ B クローズ時点:**
- **B-3（git clone の `fatal: hardlink different from source`）を 0.8.58 で修正（z2root.c, コンパイル済み・e2e 未）**: 真因＝Android SELinux untrusted_app が `link(2)` を端末全域で禁止 → link2symlink が常に copy-fallback（別 inode 生成）→ git 2.46+ が `link()` 後に dest を lstat し src と `st_dev/st_ino` 比較 → 不一致で fatal。修正＝copy-fallback の (src_dev, src_ino, dest_ino) を小リングに記録し、stat 系（newfstatat=79/fstat=80/statx=291）exit で dest_ino 一致時に `st_dev/st_ino`（statx は `stx_ino`＋`stx_dev_major/minor`）を src 値へ偽装。一致したら即エビクト（`g_linkcopy_used--`）で偽装窓を最小化、`g_linkcopy_used==0` 時は hot path を素通り。`ln`/`npm`/`tar` 等は実 link 成功時 fallback しないので無影響。詳細は §10。

**（履歴）6セッション目=z2root 実機 e2e クローズ時点:**
- **0.8.56 APK を本体 UI でインストール → z2root エンジン上で e2e 検証完了（2026-06-09）。** エンジン確定: tracer exe = `libz2root.so`（comm も同じ）＝直接（非ネスト）実行。
  1. **`.l2s` open**: NDK `libc++_shared.so`（2段 `.l2s` symlink → 実 ELF 9.2MB）を **cp 実体化なしで open** でき先頭 ELF マジック `7f 45 4c 46` 取得。Option B（`canonicalize_guest` の host_to_guest 逆変換）成立。
  2. **aapt2 `--argv0`**: `aapt2 version` 成功（`aapt 2.19`, exit=0）かつ `aapt2 daemon` 起動成功（`Ready`→`Exiting daemon`, exit=0）＝gradle の resources タスクが叩く daemon 経路。Option B（bionic linker `--argv0` 抑止）成立。
- **検証中に発見した別バグ＝0.8.57 で修正（z2root.c, コンパイル済み・e2e 未）**: `readlink .l2s` が `/root/android-sdk/n`（19B）に切り詰められる。真因＝tracee が `lstat st_size`（z2root がゲスト長 182 に逆変換済み＝短い）でバッファ確保 → カーネルがホスト実パス（長い）を切り詰めて書込 → `host_to_guest()` で更に短縮（19B）。修正＝exit で z2root 自身が対象 symlink のホスト実パスを full バッファで `readlink` し直してから変換・`bufsiz` クランプ（entry で `pid_state.aux_path` に控える / `dirfd` 相対は従来 fallback）。**リンカは open するだけなのでビルド成立には無関係**（=0.8.56 検証結果は有効）。詳細は §9。
- **版数**: 0.8.58-alpha(66) へ bump 済み（`app/build.gradle.kts:98-99`）。docs 反映済み: DESIGN-SPEC ja/en §4.3＋ヘッダ、README。**作業ツリー未コミット**（このセッションで 1 コミット予定）。
- **次にやること**: 0.8.58 APK をビルド（重い full は proot で／§0「proot…」手順）→ 本体 UI インストール → z2root で (1) `readlink .l2s` が full パスを返すか（0.8.57）、(2) `git clone` がローカルリポで hardlink 検証を通るか（0.8.58 B-3）を e2e 確認。

**（履歴）5セッション目=proot クローズ時点:**
- **proot で `assembleFullRelease` 成功**（`BUILD SUCCESSFUL in 10m 9s`、`buildZ2rootNative` で `.so` 再生成）。事前に fullRelease 中間物（merged/stripped native libs・jniLibs・outputs/apk/full/release）を rm 済み。
- **APK 検証 OK**（unzip+readelf+strings）: `libz2root.so`=EXEC/AArch64 **static**（strip 後 448768 / not-strip 2193968）+ `linker64` マーカー（§8 B2 修正入り）+ case-3 loader 経路文字列あり。`libz2accept.so`=`__errno_location` **WEAK UND** + `accept` export（0.8.55 修正）。同梱物（libproot/libtalloc/alpine rootfs 49MB/fonts）全在席。**release 署名**（APK Signing Block v2/v3 検出、`z2term-release.jks`、debug 誤署名なし）。
- **バックアップ**: `~/z2-apk-backup/app-full-release-0.8.56-proot.apk`（md5 `7418685f2f64c5d5650456fd11125f03`、`.md5` 併置）。
- **コミット済み**: 0.8.56-alpha(64) でコード（z2root.c）＋docs＋bump を 1 コミット。push 未実行。
- **残課題＝e2e 検証のみ（本体 UI インストール後）**: 二重 ptrace でネスト起動不可のため、本体 UI で z2root エンジンに切替え→`assembleFullRelease`（最低限 `processFossDebugResources` 相当）が `--argv0` エラー無く aapt2 を起動できること、および `.l2s` チェーン（NDK libc++_shared.so）を cp 実体化なしで open 解決できることを確認。これが Option B 2件の最終確認。

**（履歴）4セッション目クローズ時点（これを読む人＝proot セッションへ）:**
- z2root.c に **parity gap 修正を2件適用・コンパイル検証済み**（`bash scripts/build-z2root.sh` が約3sで static EXEC AArch64 NDK r29 の `libz2root.so`+`libz2accept.so` を生成）。詳細は §8。
  1. **`.l2s` host-path 修正（Option B 恒久版）**: `canonicalize_guest()`（z2root.c:316 付近）で絶対リンク先を `host_to_guest()` で逆変換してから walk。レガシー `.l2s` チェーンがホスト実パスを格納するため二重 rootfs 前置で ENOENT になっていた件の恒久対処。
  2. **aapt2 `--argv0` 修正**: `plan_exec()` 動的 ELF 経路（z2root.c:656 付近）で interp basename が `linker64`/`linker`（bionic）のときだけ `--argv0`+argv0 を渡さない。Android 12 の bionic linker64 は `--argv0` 非対応で aapt2 がパス引数と誤認していた件。`/system/bin/linker64 aapt2 version` 成功・`--argv0` 付き失敗で実証済み。
- **版数を 0.8.56-alpha(64) へ bump 済み**（`app/build.gradle.kts:98-99`）。関連 docs 反映済み: `docs/ja/DESIGN-SPEC.md`（line3 ヘッダ＋§4.3 に 0.8.56 追記）/ `docs/en/DESIGN-SPEC.md`（同）/ `README.md`（line33 を 0.8.56 blurb 化・0.8.55 は "Previously" へ降格）。HANDBOOK は版数表記なしのため対象外。
- **作業ツリーは未コミット**。コミットは proot セッションでビルド成功を確認してから（CLAUDE.md: app/ 変更を含むコミットは bump 済み版数で 1 コミット・docs 同梱）。

**proot セッションでやること（このために切替えた）:**
1. `assembleFullRelease` を実行（重い full ビルドでも z2root と proot は同等＝どちらも端末は固まらない。0.8.62 を z2root 上で `BUILD SUCCESSFUL in 16m58s`・フリーズ無しで完走した実績あり＝§0）。`buildZ2rootNative` で `.so` が再生成されるが、stale 同梱の前例（§1, project memory）に従い念のため fullRelease 中間物を rm してから。`JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleFullRelease --offline --console=plain`。
2. 生成 APK を unzip+readelf で検証: `libz2root.so` が **case-3 + 今回の Option B 2件入り**最新ビルドであること（古い `.so` が混ざっていないか md5/strings 確認）。
3. 検証 OK なら 0.8.56(64) で **1 コミット**（コード＋docs＋bump をまとめて）。push はユーザー指示まで禁止。
4. **e2e 検証は APK 本体 UI インストール後**: 二重 ptrace でネスト起動不可＝ヘッドレス検証不可のため、本体 UI で z2root エンジンに切替え→`assembleFullRelease`（または最低限 `processFossDebugResources` 相当）が `--argv0` エラー無く aapt2 を起動できることを確認。これが今回の修正の最終確認。

**（参考）旧 TL;DR 進展（4セッション目）: §7 の `.l2s` 壁を Option A で外したら、その先に「z2root が aapt2 を `--argv0` 漏れで起動できない」第2の gap が現れ、根本原因を実証・z2root.c に修正済み。** 詳細は §8。

**4セッション目で確定した事実:**
- エンジン確定: トレーサ `/proc/<pid>/exe = libz2root.so`、proot 互換 CLI で `--link2symlink` を自前実装。`/root` は `-b .../shared_home:/root` の bind。
- `.l2s` チェーンはレガシー資産（現行 z2root の `copy_for_link`(z2root.c:1206) は linkat をコピー化するだけで `.l2s` を新規生成しない）。**Option A の materialize は z2root 上でも `cp`(linkat 非使用=非 intercept) なら持続する**（前セッションの「再変換される」は proot/z2root ビュー混同が原因）。
- libc++_shared.so を cp 実体化 → CMake/native リンク段は z2root で通過（`configureCMakeDebug` がエラー無く実行）。
- 次の壁 = aapt2: `error: expected absolute path: "--argv0"`（§8）。**proot でも同条件で再現**するが、proot full release は §1 で成功実績があるため flavor/タスク差の可能性も要確認。

**進展（3セッション目で判明）: z2root の重いビルドは「フリーズ/overhead」が本質ではなかった。`.l2s`(link2symlink) チェーンを open で辿れないのが第1の壁だった。** 詳細は §7。

確認できた parity ポジティブ材料（すべて z2root 下・エンジン確認済み）:
- `bash scripts/build-z2root.sh` が **FALLBACK 無しで完走（約2.6s）** ＝ NDK 静的 clang を直接 exec できる＝ case-3 自己ホスト成立（item 3 クリア）。
- `assembleFossDebug` の **kotlinc がフリーズ無しで完走（約2m10s）**。重い JVM タスクも z2root で通る。
- 詰まるのは **ネイティブ CMake の C++ リンクだけ**: `ld.lld: error: unable to find library -lc++_shared`。

**次にやること（おすすめ = Option A: アンブロックして parity gap 探索を続行）:**
1. NDK の `libc++_shared.so` を実体化（§7 のコマンド）してリンクの壁を外す。
2. `assembleFossDebug` を再実行し、**次の z2root parity gap を炙り出す**。通れば軽い順に重くしていく（§5）。
3. 恒久対処（Option B）は §7 の z2root.c symlink-open/readlink 修正。Option A はあくまで「先に進んで他の gap も見つける」ための一時アンブロックで、これだけでは parity 完成ではない。

**過去セッションの未完事項（引き続き有効）:**
- 0.8.55 APK の本体 UI インストール（`~/z2-apk-backup/app-full-release-0.8.55-f258210-proot.apk`、または `app/build/outputs/apk/full/release/app-full-release.apk`）。`pm`/`adb` はこの環境から届かないので UI 操作。
- ~~**item 4（音声の実機最終確認）**~~: **ユーザーが実機 UI で音声 OK を確認済み（2026-06-09）**。z2root + GUI で発音する。クローズ。
- エンジン判定: `uid=0`/`PROOT_* unset`/`/usr/sbin/clang` だけでは proot と z2root を区別できない。z2root 固有挙動の検証は必ず**実機 UI でエンジンを確認してから**。

## 1. コミット済み（前セッションの「未コミット」を解消）＋ proot full ビルド成功

- **コミット完了**: `HEAD=f258210` = `fix(foss): z2root 配下 GUI 音声無音+静的ELF(bind配下)exec不可+z2accept errno を修正… 0.8.55-alpha(63)`。
  - `e1b97b4`(0.8.52) の上に **1 コミット**。0.8.53/54/55 を集約。author `orgson <270548806+orgsonai@users.noreply.github.com>`（config 未書換）。
  - 11 ファイル（コード 9 + 未追跡 HANDOFF 2 件＝ユーザー判断で同梱）、397 挿入/26 削除。`--no-verify` 不使用・push 未実行・working tree clean。
- **proot 下で full release ビルド成功（このセッションの主成果）**:
  - `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:clean :app:assembleFullRelease --offline --console=plain` → **`BUILD SUCCESSFUL in 21m 32s`**。proot なので `LD_PRELOAD` 不要・フリーズ無し。
  - 成果物: `app/build/outputs/apk/full/release/app-full-release.apk` 69MB・**release 署名**（`z2term-release.jks`、`keystore.properties` はルートにあり）。
  - **APK 内容検証**（stale 同梱の教訓に従い unzip+readelf）: `libz2accept.so`=`__errno_location` **WEAK UND** + `accept` エクスポート（0.8.55 修正）。`libz2root.so`=NDK r29 **static EXEC** stripped（元 not-strip 2193840→APK strip 後 448640）、`z2root loader: %s(%s): %s` 等 loader 経路文字列あり＝**case-3 入り最新ビルド**。同梱物 rootfs 49MB / fonts 3 / proot 一式すべて在り。
  - **バックアップ**: `~/z2-apk-backup/app-full-release-0.8.55-f258210-proot.apk`（md5 `295ffaf326c3fa29170bca9372f1c105`、`.md5` 併置）。`/tmp` は揮発するので永続場所へ退避。z2root 版が出たら md5 比較に使う。
- **未検証（次セッション）**: z2root エンジン下での item 3（NDK 静的 clang 直叩き）と item 1（z2root full ビルド）＝§0 の手順。今回の NDK clang exec 成功は **proot 上なので (A) の検証にはならない**点に注意。

### （履歴）前セッションの git 復旧
- 旧 z2root の link2symlink が git object を壊し（`fatal: bad object HEAD`）、`/tmp/z2git-recovery-backup/` に退避 → `e1b97b4` へ ref を戻して working tree から再構築した。現行ソースは 0.8.47 で「実ハードリンク優先→コピー fallback」へ修正済みで**現行コードの回帰ではない**。`git fsck` エラー 0 で復旧済み。

## 2. 結論（ビルドは現状 proot 同等か）

**ビルドは z2root でも通る＝proot と同等。** full ビルドは proot で `BUILD SUCCESSFUL` 確認済みだが、**z2root 上でも 0.8.62 を `BUILD SUCCESSFUL in 16m58s`・フリーズ無しで完走した実績がある（§0）＝重い full ビルドで z2root と proot に差は無い**。当初「z2root はフリーズする」とした記述（§3/§4 の overhead/フリーズ仮説）は本質ではなく、実体の壁は `.l2s` open / aapt2 `--argv0` 等の parity gap だった（§7/§8 で解消済み）。どちらのエンジンでビルドしても良い。

## 3. 第一容疑：トレースログが ON（除去可能な overhead）

- `app/src/main/java/com/zerotoship/z2term/proot/ProotLauncher.kt:128-131`: **shared home に `.z2root_trace_on` が存在するとき**だけ `Z2ROOT_TRACE=<sharedHome>/z2root_trace.log` を env 注入する。
- `app/src/main/cpp/z2root/z2root.c:843` `trc_init()` が `getenv("Z2ROOT_TRACE")` で有効化。メインループ（`z2root.c:1772` 以降）が **seccomp トラップ毎に `fprintf(g_trc, ...)`**（パス文字列整形込み）を実行。
- **証拠**: `~/.z2root_trace_on`=`1` が存在し `~/z2root_trace.log` が調査中も増え続け 90MB 超。直近 20 万行の syscall 内訳は `statx`(291) が 50% 超、`openat`(56)・`fstat`(80) が続く＝重いファイル走査の度にログ write が発生。
- **対処**: ビルド時は `rm ~/.z2root_trace_on` してエンジン再起動。proot にはこの経路が無いので、これだけで体感差がかなり縮む見込み。トレース自体は実機デバッグ用に意図的残置（§4.3 0.8.43）なので削除はせず「ビルド時 OFF」運用で良い。

## 4. 本質的 overhead（トレース OFF でも残る分）

- fakeroot(`-0`) は uid=0 偽装のため `z2root.c:861` `fake_root_on_exit()` で **`statx`(291)/`fstat`(80)/`newfstatat`(79) を exit で全トラップし st_uid/st_gid を 0 に上書き**する。重い gradle/kotlinc/aapt2/NDK は stat 系を膨大に発行＝seccomp トラップ（entry+exit の 2 stop ＋ `get_regs`/`set_regs` ＋ `write_tracee_mem`）が爆発する。これは proot も原理的に同じ。
- proot が通って z2root が固まる差の候補（要計測）:
  1. トレースログ ON（§3、最有力・除去可能）。
  2. 1 tracer スレッドへ全 tracee の syscall stop が直列化＝高並列ビルドでの待ち。
  3. `get_regs`/`set_regs`（PTRACE_GETREGSET）や `write_tracee_mem` の 1 syscall あたりコストが proot 実装より重い可能性。
- **計測方針**: §0-1 でトレース OFF にした上で、軽い順に切り分け（§5）。`time` と syscall 数の比で proot 比を出す。stat 系の偽装を「entry で seccomp 継続させ exit だけ捕捉」できているか、無駄な entry trap が無いか seccomp フィルタ（`z2root.c` の trace 対象 syscall 配列 ~1499 付近）を確認。

## 5. 切り分け手順（フルビルドを避けて段階的に）

z2root セッション内でフルビルドは回さないこと（§0-3）。軽い順に:
1. `bash scripts/build-z2root.sh`（z2root ネイティブ部のみ。0.8.54 でオンデバイス自己ホスト対応済み）を z2root 下で `time` 計測 → proot 下と比較。
2. `./gradlew :app:assembleFossDebug` 等の軽いタスク、もしくは `--dry-run`／単一モジュールで段階的に重くする。
3. フルビルド検証が必要なら ssh で z2term に入り（`docs/SSH-INTO-Z2TERM.md`）、proot ではなく z2root エンジンのセッションで回す＝作業端末を巻き込まない。

## 6. 参考

- 仕様正本: `docs/ja/DESIGN-SPEC.md` §4.3（z2root 変更履歴 0.8.30〜0.8.55）。
- オンデバイスビルド: `docs/ON-DEVICE-BUILD.md`、リリース: `docs/RELEASE.md`。
- 関連ハンドオフ: `docs/FOSS-PURE-HANDOFF.md`、`docs/Z2ROOT-AUDIO-HANDOFF.md`。
- git 復旧バックアップ: `/tmp/z2git-recovery-backup/`（壊れた ref 値の控え）。

## 7. 真の壁: z2root が `.l2s`(link2symlink) チェーンを open で辿れない（3セッション目で特定）

§3/§4 の「トレース overhead/フリーズ」は本質ではなかった。z2root 下で `assembleFossDebug` を回すと kotlinc まで完走し、**唯一ネイティブ CMake のリンク段で**こけた:

```
ld.lld: error: unable to find library -lc++_shared
```

### 真因
NDK の `libc++_shared.so` が link2symlink チェーンになっており、z2root はこれを open で辿れない:

```
libc++_shared.so            -> .../.l2s.libc++_shared.so0001      (182B symlink)
.l2s.libc++_shared.so0001   -> .../.l2s.libc++_shared.so0001.0004 (187B symlink)
.l2s.libc++_shared.so0001.0004                                    (9.2MB 実 ELF)
```

- 場所: `/root/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/`。
- **SDK/NDK 全体でこの 1 チェーンだけ**（`find /root/android-sdk -name '.l2s.*'` ＝ 上記 2 件のみ）。link2symlink は旧 z2root エンジンが過去にハードリンク非対応 fs 上で生成したもの。
- **再現する直接証拠（2026-06-09 z2root 下で確認）**: `ls -la libc++_shared.so` はリンク先を完全表示（182B）できるのに、`readlink libc++_shared.so` は **`/root/android-sdk/n`（19B）に切り詰められて返る**。z2root の readlink 結果書き換え（`z2root.c:811` 付近 `rewrite_readlink_result`）が host→guest 変換後の長さ計算をミスり、バッファを途中で切っている疑いが濃厚。リンカが短縮パスを open → ENOENT → `-lc++_shared` 解決失敗、という連鎖。**フリーズや overhead は無関係**。

### Option A（おすすめ・一時アンブロック）: 実体を materialize して先へ進む
リンクの壁を外し、**次の z2root parity gap を探索し続ける**のが目的（これ単体では parity 完成ではない）:

```
cd /root/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android
cp --remove-destination .l2s.libc++_shared.so0001.0004 libc++_shared.so
cd /root/tmp/app_project/05_z2term
time JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleFossDebug --offline --console=plain
```

通れば §5 の順で段階的に重くし、新たな gap を記録する。

### Option B（恒久対処）: z2root.c の symlink-open/readlink を直す
real parity にはこちらが本丸。z2root が `.l2s` チェーン（多段 symlink）を open/readlink で正しく辿れるようにする。調査の起点:

- `z2root.c:811-837` `rewrite_readlink_result`: `host_to_guest` 後の `glen`/`bufsiz` クランプとバッファ write が切り詰めを起こしていないか。`/root/android-sdk/n`=19B = `/root/android-sdk/`(18)+`n`(1) という切れ方を再現・検証。
- `z2root.c:477` 付近（`case 78: readlinkat 最終は辿らない`）と openat の guest_to_host 最終要素のシンボリックリンク解決。
- トレースで確証を取る:

```
Z2ROOT_TRACE=/root/z2sym.log od -An -N4 -tx1 /root/android-sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so 2>/dev/null
grep -iE 'readlink|openat|libc\+\+_shared|\.l2s' /root/z2sym.log | head -40
```

※トレース ON 時は `~/.z2root_trace_on` か `Z2ROOT_TRACE` env。重いビルドでは OFF に戻す（§3）。

### この壁を踏まえた parity 評価の更新
§2 の「z2root はフリーズするから未 parity」は古い。`.l2s` 壁は Option A で外せる。ただしその先に §8 の aapt2 gap がある。

## 8. 第2の壁: aapt2 が bionic linker の `--argv0` 非対応で起動できない（4セッション目で特定・修正）

§7 の libc++_shared を cp 実体化したら CMake は通過し、次に **`processFossDebugResources` の AAPT2 daemon 起動が失敗**した:

```
AAPT2 35.0.0 Daemon #0: Unexpected error output: error: expected absolute path: "--argv0"
AAPT2 35.0.0 Daemon #0: Daemon startup failed
```

### 真因（実証済み）
aapt2(`build-tools/36.0.0/aapt2`) は **Android ネイティブの aarch64 ELF**（interp=`/system/bin/linker64`、NDK r26b ビルド。x86_64 ではない＝box64 経由でもない）。z2root は動的 ELF を `<interp> --argv0 <name> <prog> <args>`(z2root.c:657) で起動するが、**この端末(Android 12)の bionic linker64 は glibc/musl の ld.so と違い `--argv0` を解さず、実プログラムの argv へ素通しする**。直接証拠（z2root 下）:

```
aapt2 version                              → error: expected absolute path: "--argv0"
/system/bin/linker64 --argv0 X aapt2 version → 同じエラー（bionic が --argv0 を解さない）
/system/bin/linker64 aapt2 version          → 成功（Android Asset Packaging Tool 2.19 と表示）
```

kotlinc/java(glibc ld.so) は `--argv0` を解すので通っていた。aapt2 だけが bionic linker 経由なので daemon 起動で即死していた。

### 修正（適用済み・要 e2e 検証）
`z2root.c` `plan_exec` の動的 ELF 経路で、interp basename が `linker64`/`linker`(bionic) のときだけ `--argv0`+argv0 を **push しない**。bionic では argv0 が実プログラムパスになるが Android ツールは argv0 を見ないので実害なし。`bash scripts/build-z2root.sh` でコンパイル通過。
- **未検証**: 二重 ptrace でネスト起動できず（新エンジンを z2root 内で走らせると起動しない）、e2e 検証は **full APK 再ビルド＋本体 UI インストールが必要**。重い full ビルドは z2root を固めるので proot で回す（[[project_z2root_heavy_build_freezes]]）。
- コミット時は app/ 配下変更につき **versionCode/versionName を上げ、DESIGN-SPEC §4.3 等 docs を同コミットで更新**（CLAUDE.md ルール）。

### 残課題の整理（parity の全体像 / 4セッション目クローズ時点）
1. **§7 `.l2s` の恒久対処（Option B）**: **適用済み**。`canonicalize_guest()`（z2root.c:316 付近）で絶対リンク先を `host_to_guest()` 逆変換してから walk。cp 実体化(Option A)は本修正入り APK をインストールするまでの一時アンブロックで、恒久路は本修正。コンパイル検証済み・e2e 未検証。
2. **§8 aapt2 `--argv0`**: 適用済み・コンパイル検証済み・e2e 未検証。
3. 版数は **0.8.56-alpha(64) へ bump 済み**、docs（DESIGN-SPEC ja/en §4.3・README）反映済み。**作業ツリーは未コミット**＝proot セッションで `assembleFullRelease` 成功を確認してから 1 コミット（§0「proot セッションでやること」）。
4. これらを通した先に更なる gap が無いか、§5 の順で full ビルドまで通して確認する。

## 9. readlinkat 戻り値の切り詰め修正（6セッション目・0.8.57）

§7/§8 の Option B 2件を 0.8.56 APK で e2e 検証中（z2root 実機、§0 TL;DR 参照）、`.l2s` の **open は成功する**一方で `readlink .l2s` が切り詰められる別バグを確認した。

### 症状
```
readlink <NDK>/libc++_shared.so → /root/android-sdk/n   (19B に切れる)
file/od/stat は全パス(182B)を正しく取得・open も ELF マジック取得
lstat st_size = 182 (= ゲスト長, z2root が逆変換済み)
```

### 真因
1. tracee（coreutils `readlink` 等）は `lstat` の `st_size`=182（z2root がゲスト長へ逆変換した短い値）でバッファを確保し `readlinkat(bufsiz≈183)` を発行。
2. カーネルは on-disk のリンク先＝**ホスト実パス**（`/data/.../shared_home/android-sdk/.../.l2s…`＝182 より長い）を bufsiz=183 で**切り詰めて**書き込む（先頭 `/data/.../shared_home/android-sdk/n` まで）。
3. z2root の `rewrite_readlink_result()` がその切れたホストパスを `host_to_guest()` → `/root/android-sdk/n`（19B）。tracee に 19B を書き戻し ret=19。
- 要は「ゲスト長で確保したバッファにホスト長を入れる」段で既に欠落しており、従来実装は tracee バッファ（切れた値）を読んでいたため復元不能だった。

### 修正（z2root.c, コンパイル済み・e2e 未）
proot 同様、**exit でトレーサ（z2root）自身が full バッファで readlink し直す**:
- entry（`handle_syscall_entry` nr==78）: 対象 symlink のホスト実パスを `host_path_for(deref=0)` で求め `pid_state.aux_path` に控える。
- exit（`rewrite_readlink_result`）: `aux_path` があれば `readlink(aux_path, host, PATH_MAX)` で**全体**を読み直し → `host_to_guest()` → `bufsiz` クランプで書き戻し。`aux_path` 未確定（`dirfd` 相対等）は従来の tracee バッファ読みにフォールバック。
- `aux_path` フィールドを `struct pid_state` に追加（`PATH_MAX_Z`、MAP_CAP=256）。

### 影響と検証
- **ビルドへの影響なし**: リンカ/ld.so は symlink を `open`（カーネルがパス解決）するだけで `readlink` の戻り値長に依存しない＝0.8.56 のビルド成立検証は有効なまま。
- e2e 未: 実行中エンジンは旧 0.8.56 のため、本修正の確認は 0.8.57 APK を本体 UI へインストール後（`readlink <NDK>/libc++_shared.so` が full パスを返すこと）。

## 10. git clone の hardlink 検証対策（7セッション目・0.8.58 / B-3）

### 症状
ローカルパス間の `git clone`（例 `git clone /path/repo repo2`）が `fatal: failed to create link ...: hardlink different from source` で失敗する。

### 真因
- Android の SELinux ポリシー（`untrusted_app` ドメイン）は `link(2)`/`linkat(2)` を **端末全域で禁止**（z2root のバグではなく OS 制約）。
- そのため z2root の link2symlink emulation は常に **copy-fallback**（実体コピー）になり、dest は src と **別 inode** を持つ。
- git 2.46+ は `link()` 成功後に dest を `lstat` し、src と `st_dev`/`st_ino` が一致するか検証する。copy-fallback では不一致 → `hardlink different from source` で fatal。

### 修正（copy-fallback dest の stat inode 偽装）
- `linkat_exit` で copy-fallback が成立した直後、`linkcopy_record(src_host, dest_host)` が src の `(st_dev, st_ino)` と dest の `st_ino` を小リング（`LINKCOPY_CACHE=32`）に記録。
- `fake_root_on_exit` の stat 系ハンドラ（newfstatat=79 / fstat=80 / statx=291）exit で、tracee が読み出した結果バッファの inode を読み、記録済み `dest_ino` と一致したら：
  - struct stat: `st_dev@0`/`st_ino@8` を src 値へ上書き。
  - statx: `stx_ino@32` を src ino へ、`stx_dev_major@128`/`stx_dev_minor@132` を src dev の major/minor へ上書き。
- **一致時は即エビクト**（`e->used=0; g_linkcopy_used--`）で偽装が効く窓を最小化。`g_linkcopy_used==0` の間は stat hot path で照合自体を素通り（性能影響なし）。

### 影響と検証
- **`ln`/`npm`/`tar` 等への退行なし**: 実 `link()` が成功する経路は copy-fallback に入らず記録もしないため偽装対象外。fallback が起きるのは link 禁止由来のエラー（EACCES/EPERM/EXDEV 等）に限る。
- e2e 未: 実行中エンジンは旧 0.8.56 のため、本修正の確認は 0.8.58 APK を本体 UI へインストール後（ローカル `git clone` が成功すること）。

### 0.8.58 修正の構造的バグと 0.8.62 での堅牢化（13セッション目・2026-06-09・B-3 再修正）
- **発見**: 稼働 0.8.61 エンジン上で C プローブにより切り分けたところ、linkat copy-fallback 200 件中 **200 件すべてが別 inode の dest を生成し、stat 偽装は一度も発火しなかった（0 fake）**。git clone は再現性をもって `fatal: hardlink different from source` で失敗。これは §10「修正は現存しおそらく機能する」という仮定を**否定**する。タイミング/リング・エビクション（0/200 は確率でなく構造的）・版数差（z2root.c は 418ed67↔HEAD 同一・稼働 text md5 一致）・syscall 誤り（生 `SYS_newfstatat` でも失敗）・seccomp 非トレース（79/291 は `kTraceSyscallsBase` に在る）はいずれも否定。
- **真因**: 旧 `linkcopy_record(src_host, dest_host)` は**ホストパスを後から `stat()` し直して** inode を採取していた。copy 直後の dest を**別途 stat する**ため、tracee が `newfstatat` で読む inode と記録 inode がずれ得る（経路差・キャッシュ差で不一致）。結果 `dest_ino` 照合が常に miss し偽装が効かない。
- **修正（0.8.62 / fstat ベース捕捉）**: `copy_for_link` を out-param 付きシグネチャ（`out_src_dev/out_src_ino/out_dst_ino`）へ変更し、**コピー生成直後の出力 fd を `fstat()`** して dest inode を確定採取（tracee が後で見る inode と同一実体を保証）。`linkcopy_record(src_dev, src_ino, dest_ino)` も値渡しへ変更し、再 `stat()` を排除。`g_trc_on` 時に `linkcopy REC`/`linkcopy FIND ... HIT|miss` をトレース出力して次 e2e で発火を確認する。
- **e2e 未（次 APK 導入後）**: コンパイル確認のみ済（`bash scripts/build-z2root.sh` 成功）。実機確認＝0.8.62 APK 導入後にローカル `git clone` が hardlink 検証を通ること＋（trace 有効時）`linkcopy FIND ... HIT` が出ること。

### 0.8.62 が招いた z2root 起動退行と 0.8.63 での dev+ino 厳格化（14セッション目・2026-06-09・B-3 退行修正）
- **症状**: 0.8.62 APK を本体 UI で導入後、z2root へ切替えるとゲスト（`Arch Linux ARM`）が **起動直後に `[プロセス終了 exitCode=-1]` で即死**。0.8.61 は同じ z2root 上ビルドで稼働していた（`/proc/self/mem` text 照合で確定済）。0.8.61→0.8.62 のコード差分は z2root.c の linkcopy 1 コミットのみ。
- **ビルド無罪を確認**: proot で `build-z2root.sh` を再実行した `.so` が installed の z2root ビルド版と **md5 完全一致（f976871・バイト単位同一）**＝ビルドは再現可能で installed バイナリは健全。原因はソース（linkcopy コミット）と確定。
- **真因（dormant 経路の覚醒）**: 0.8.61 までは `linkcopy_record` が dest を再 `stat()` していて記録が常に失敗 → `g_linkcopy_used==0` のまま → `newfstatat`/`fstat`/`statx` exit の stat 偽装ホットパスは `if (g_linkcopy_used && …)` で**一度も発火していなかった**（＝B-3 機能が実質 OFF。だから 0.8.61 は無傷）。0.8.62 で fstat 捕捉により記録が**成功**するようになった結果、このホットパスが**初めて常時 ON** に。そして照合キーが **inode 番号だけ**（`linkcopy_find` が `dest_ino==ino` のみ・dev/パス不問。コメントは「別 fs の inode 衝突リスクは無視できる」としていたが誤り）。Android `untrusted_app` は `link(2)` を全域禁止＝ゲストのハードリンクは全部 copy-fallback して記録されるため、起動中に init/ld が stat した**無関係なファイル**の inode 番号がたまたま記録済み dest と衝突すると、その `st_dev`/`st_ino` が無縁の src 値へ偽装され、ゲストの起動時 stat が壊れて即死。
- **修正（0.8.63 / dev+ino 厳格化）**: 照合キーを dest の **`(dev, ino)` 両方**へ厳格化。生成直後の実体は host の `(dev, ino)` が一意なので誤ヒットが事実上消える。具体的には `struct linkcopy_ent` に `dest_dev` を追加、`copy_for_link` の生成直後 `fstat` で `dest_dev` も採取（`out_dst_dev` を追加）、`linkcopy_record` は dest_dev も受け取り記録、`linkcopy_find(dev, ino)` を dev+ino 一致へ変更。stat ハンドラ側は `newfstatat`/`fstat`（79/80）で `st_dev`@off0 も読んで照合、`statx`（291）は `stx_dev_major`@128/`stx_dev_minor`@132 から `makedev` で dev を復元して照合。B-3 の hardlink 偽装（dest を一度だけ src の identity に見せる）は維持。
- **e2e 未（次 APK 導入後）**: コンパイル確認のみ済（`build-z2root.sh` 成功・新 `.so` md5 `7551ae7`・トレース文字列 `dest(dev= ino=)` 反映）。実機確認＝(1) z2root で `Arch Linux ARM` が正常起動すること、(2) §12.6 の B-3 git clone hardlink 検証が通ること。

### 0.8.64 で起動退行を実際に修正（15セッション目・2026-06-10・パス相関化）
- **症状（再）**: ユーザーが **0.8.63 APK を導入しても変わらず**、z2root で `🐠Arch Linux ARM を起動中…` のあと `[プロセス終了 exitCode=-1]` で即死。0.8.63 の修正は効いていなかった。
- **0.8.63 が効かなかった理由（dev 不識別）**: 照合キーに足した `st_dev` には**識別力がゼロ**だった。dest は `copy_for_link` が rootfs bind 配下（`-b <home>:/root` 等）にコピー生成する＝**ゲストの全ファイルと同じ host `/data` パーティション上**に作られるため、`st_dev` は rootfs 全域で同一の固定値。よって `(dev, ino)` 照合は実質 inode 単独照合と変わらず、起動中に init/ld が stat した無関係ファイルの inode 衝突がそのまま残り、`st_ino` を無縁の src 値へ誤偽装してゲストを殺し続けていた。
- **修正（0.8.64 / パス相関）**: inode 照合を捨て、**stat 対象のパスがコピー先と一致したときだけ偽装**するパス相関方式へ置換。
  - `struct linkcopy_ent` の照合キーを `dest_dev`/`dest_ino` → `char dest_host[PATH_MAX_Z]`（コピー先ホスト実パス）に変更。`linkcopy_record(src_dev, src_ino, dest_host)` は `linkat_exit` が持つ `st->link_newhost` をそのまま記録（`copy_for_link` の `out_dst_*` 採取は不要になり削除）。
  - `copy_for_link` の返り値を **0=通常ファイルコピー（記録対象）/ 1=symlink 再生成（git 検証対象外＝記録不要）/ -1=失敗** に変更し、`linkat_exit` は `rc==0` のときだけ記録。
  - `handle_syscall_entry` の `newfstatat`(79)/`statx`(291) で、`g_linkcopy_used>0` のとき対象パスを `read_tracee_str`→`host_path_for`(deref=0) で解決し `linkcopy_find_by_path` で照合、一致添字を `pid_state.linkcopy_hit`（-1=非該当）に控える。
  - `fake_root_on_exit(pid, nr, buf, lc_idx)` は `lc_idx>=0` のときだけ `st_dev`/`st_ino`（statx は `stx_ino`+`stx_dev_major/minor`）を `g_linkcopy[lc_idx]` の src 値へ偽装してエビクト。**fd ベースの `fstat`(80) は entry でパスを取れない＝常に lc_idx=-1 で inode 偽装せず**（uid/gid 偽装は従来どおり）。git の hardlink 検証は `lstat`=`newfstatat` 経路を使うため B-3 に影響なし。
  - 副産物: inode 読みが消えたため未使用化した `read_tracee_mem` を削除。
- **誤ヒットが原理的に起きない**: 「まさにコピーした dest のパスを stat したとき」だけ偽装するので、起動中の無関係ファイルは（inode が衝突しても）パスが違えば絶対に偽装されない。
- **✅ e2e 合格（2026-06-10・0.8.67 APK）**: z2root で (1) `Arch Linux ARM` 正常起動、(2) §12.6 の B-3 git clone hardlink 検証が通ること（`git clone /tmp/r-src /tmp/r-dst` が `--no-hardlinks` 無しで成功・`fsck` clean・`ln` 退行なし）を確認。`build-z2root.sh` 成功・警告ゼロ。

### 0.8.69 で Alpine(musl) 起動退行を**真に**根治（18セッション目・2026-06-10・bionic 誤判定）
- **症状**: 0.8.68 を導入しても **Alpine（musl）だけ** `Alpine Linux を起動中…` → `[プロセス終了 exitCode=-1]` で即死が継続。0.8.67（ld.so 二重 reloc）も 0.8.68（phdr バイアス）も**症状の一部しか塞いでいなかった**。
- **決定的トレース**: `shared_home/.z2root_trace_on` を置いて実機トレース（`/root/z2root_trace.log`）。クラッシュ直前に loader が `open`(nr=56) するのは `.../alpine/lib/ld-musl-aarch64.so.1` **本体**、直後に `SIGSEGV si_addr=0x132c0`（= link-time vaddr を生で触っている = **base が 0**）。
- **真因（case 3 への誤分類 → bionic 救済の誤適用）**:
  - musl 公式 `ldso/dlstart.c` の base 算出は `base = aux[AT_BASE]; if(!base){ AT_PHDR を走査し PT_DYNAMIC を見つけ base = 実行時&_DYNAMIC − ph->p_vaddr; }`（**PT_PHDR ではなく PT_DYNAMIC**。前 §の「PT_PHDR.p_vaddr から逆算」は不正確だった）。
  - **`ld-musl` は PT_INTERP を持たない**（自身がインタプリタ）。そのため `plan_exec` の `read_elf_interp` が 0 を返し case 2（動的）を外れ、**case 3（静的扱い）に落ちて `skip_reloc=0`** になっていた（= ld.so を「静的 PIE プログラム」と誤分類）。
  - case 3 で loader が **RELATIVE/RELR 肩代わり＋phdr バイアス**を適用 → musl の自己 relocation と二重化。さらに phdr バイアスで `PT_DYNAMIC.p_vaddr` が `+base` 済みになり `base = dynv − (base + 0xbfd68) = 0` → 全ポインタがずれて SIGSEGV。
  - **これは前 §の「棚上げ・本退行には無関係」と切り捨てた case 3 直接 exec 経路そのもの**だった。ディストロ起動は「busybox(動的, case 2)」だけでなく、loader 直接ロード対象の `ld-musl` 自身が case 3 に落ちることを見落としていた。
- **修正（0.8.69）**: 肩代わりを **bionic ELF に限定**。`load_elf_and_jump` で **PT_NOTE を走査し `.note.android.ident`（owner `Android`・Android NDK 出力のみ保持）**で bionic 判定 → `int apply_loader_reloc = (!skip_reloc && is_bionic);` を導入し、RELATIVE/RELR 肩代わり（旧 `!skip_reloc` 条件）と phdr バイアス（旧 `!skip_reloc` 条件）の**両方**を `apply_loader_reloc` ゲートへ変更。
  - **bionic static-PIE（case 3・note 有り）**: 従来どおり肩代わり＋バイアス（0.8.59 の救済を維持）。
  - **glibc/musl の ld.so・static-PIE（case 2/3・note 無し）**: 肩代わり一切なし → 自前で正しく自己 relocation＆base 算出。
- **判別子の実機確認**: `readelf -n` で `libz2root.so`（bionic NDK 製）= `.note.android.ident` owner `Android`（namesz=8=`"Android\0"`）**有り**、`ld-musl-aarch64.so.1` = `.note.gnu.build-id` のみで Android note **無し**。
- **検証**: `bash scripts/build-z2root.sh` 成功（`libz2root.so` md5 `a5ffa741…`）。コミット `9dcddf9`（versionCode 77・release 署名 `CN=Z2Term`）。
- **✅ full APK 再ビルド＋バックアップ完了（2026-06-10）**: full release ビルド（box64 aapt2 override・R8 メモリピーク回避で `--max-workers=4`）。**APK 同梱 `lib/arm64-v8a/libz2root.so` md5 `511ab30dac4e991e2629c22db348519d`・452800B**（unzip+md5 で確認＝stale でない・旧 stripped `0c8c34aa` と相違）。`~/z2-apk-backup/app-full-release-0.8.69-9dcddf9-z2root.apk`（195207231B・apk md5 `7822dcd4…`）。
  - ⚠️ **stale APK の罠（[[project_z2root_stale_apk_jnilibs]]）**: 初回ビルドで APK 同梱 `.so` が 0.8.68 と byte 一致（`0c8c34aa`）＝stale 化。原因は `merged_native_libs`/`stripped_native_libs` は消したが**上流 `merged_jni_libs` が UP-TO-DATE のまま古い `.so` を供給**していたこと。`merged_jni_libs`+`merged_native_libs`+`stripped_native_libs`+`cxx`+出力 APK を全て rm し source `.so` を touch、`--no-build-cache` で再ビルドして解消。**今後の再ビルドは必ず `merged_jni_libs` まで消す**。
- **🔜 残り＝Alpine 実機 e2e のみ（次段）**: 0.8.69 APK を本体 UI で導入 → z2root で `Alpine Linux`(musl) 起動を確認（`exitCode=-1` 解消）。glibc(Arch/Kali) 無傷を併せて regression 確認。aapt2 回避は [[project-build-aapt2-box64-binfmt]]。
- **教訓**: この退行を **version 違い / stale APK / stat / readlink / phdr バイアス単体**で追わない。真因は「自己 relocation する glibc/musl 系 ELF を bionic static-PIE と誤判定して肩代わりしていたこと」。判別は `.note.android.ident` の有無で確定。

### 0.8.68 で Alpine(musl) 起動退行を根治（17セッション目・2026-06-10・phdr バイアス未ゲート）
> ⚠️ **後日訂正（0.8.69）**: 本節の「真因＝phdr バイアスが case 2 で当たる」「busybox は case 2 経由」「棚上げ項目は本退行と無関係」は**いずれも不正確**。真因は `ld-musl` 自身が PT_INTERP 非保持で case 3 に落ちる誤分類。上の「0.8.69 で…真に根治」を参照。
- **症状**: 0.8.67 で Arch/Kali（glibc）は正常起動するようになったが、**Alpine（musl）だけ** `Alpine Linux を起動中…` → `[プロセス終了 exitCode=-1]` で即死。Kali 正常・Ubuntu 未インストールで未確認。
- **切り分け**: Alpine の `/bin/busybox`（init/sh の実体）は**動的 ELF**＝`plan_exec` の case 2 で musl `ld.so`(`ld-musl-aarch64.so.1`) をロードする経路。0.8.67 の二重 relocation 修正で RELATIVE/RELR 肩代わり（行2296）は `!skip_reloc` ゲート済みのため、case 2(`skip_reloc=1`) では発火しない。だが `skip_reloc=1` で micro-repro しても SIGSEGV（`RUN_EXIT=139`）が再現 → relocation 以外の ET_DYN 専用処理が残存。
- **真因（phdr バイアスが `skip_reloc` 未ゲート）**: `load_elf_and_jump` 末尾に、bionic static 起動の bias=0 仮定を満たすため「ET_DYN なら各 phdr の `p_vaddr += base` した**バイアス済みコピー**を AT_PHDR に渡す」処理がある。これが `e_type==ET_DYN && base!=0` 条件のみで、**`skip_reloc` でゲートされていなかった**。**musl ld.so は `_dl_start` で AT_PHDR の `PT_PHDR.p_vaddr` から自身の load base を逆算する**（`base = AT_PHDR - PT_PHDR.p_vaddr` 相当）。事前バイアス済み phdr（`p_vaddr` が既に `+base`）を渡すと逆算結果が `base` 二重算入になり、自己 relocate 後の全ポインタがずれて SIGSEGV。
  - **なぜ glibc では出なかったか**: glibc ld.so は GOT 相対の自己ブートストラップ（`_dl_relocate_static_pie` / `elf_machine_load_address` が GOT[0] 差分から base を求める）で **AT_PHDR の p_vaddr に依存しない**。そのため事前バイアスされた phdr を渡されても base 算出が狂わず、Arch/Kali は無傷だった。
- **修正（0.8.68）**: phdr バイアスの条件を `if (e_type==ET_DYN && base!=0)` → `if (!skip_reloc && e_type==ET_DYN && base!=0)` に変更。これで:
  - **case 3（静的 ELF 直接ロード・`skip_reloc=0`）**: bionic static-PIE のため従来どおりバイアス適用（bias=0 仮定を満たす）。
  - **case 2（ld.so 経由・`skip_reloc=1`）**: バイアスせず素の phdr を AT_PHDR に渡す → musl/glibc どちらの ld.so も自前で正しく base 算出。
  - 行2296 の RELATIVE/RELR 肩代わりは 0.8.67 で既に `!skip_reloc` ゲート済。case 2 に残っていた ET_DYN 専用処理はこの phdr バイアスのみだったので、両方ゲートで case 2 は「素の ELF をマップして AT_* を素直に渡すだけ」に揃った。
- **検証**: `bash scripts/build-z2root.sh` 成功。micro-repro（glibc static-PIE）・macro-repro（Alpine busybox via musl ld.so）双方で修正前 `RUN_EXIT=139` を確認済。full APK 再ビルド成功（versionCode 76・release 署名 `CN=Z2Term`・APK 同梱 `.so` は修正後 CMake 出力＝stale でないと確認）・コミット `116c538`・`~/z2-apk-backup/…-0.8.68-116c538-z2root.apk` にバックアップ。**⏳ Alpine 実機 e2e のみ未**＝APK 導入後に起動確認。aapt2 ビルド障害の回避は [[project-build-aapt2-box64-binfmt]]（§0 参照）。
- **既知の別件（棚上げ）**: case 3 で glibc/musl の static-PIE を**直接 exec** する経路は `skip_reloc=0` のまま＝RELATIVE 二重適用＋phdr バイアスが残る。ただしディストロ起動は動的バイナリ(case 2)経由なので本退行には無関係。修正には「bionic static-PIE か glibc/musl static-PIE か」の判別（自己 relocate の有無検出）が要るため次段へ繰り越し。

### 0.8.67 で起動退行を**真に**根治（16セッション目・2026-06-10・ローダ二重 relocation）
- **🛑 重大訂正**: **0.8.62〜0.8.64 の stat 偽装をめぐる一連の修正は、この起動退行の真因ではなかった**（誤診）。0.8.64 を導入しても `🐠Arch Linux ARM を起動中…` → `[プロセス終了 exitCode=-1]` が継続。診断ログ（`Z2ROOT_TRACE`）＋ SIGSEGV 時の全レジスタダンプを入れて再トレースし、真因を確定した。
- **真因（ローダの二重 relocation）**: 起動退行は **0.8.59 で `load_elf_and_jump` に入れたローダ側 RELATIVE/RELR 自前適用（§B-5 の修正①）が、全動的バイナリの起動経路で発火する `ld.so`(`ld-linux-aarch64.so.1`) にも適用されていた**ことが原因。`plan_exec` の動的 ELF 経路（case 2）は loader 対象に **ld.so 本体**を渡すが、glibc/musl/bionic の ld.so は `_dl_start` で**自分自身を self-relocate** する。そこへローダが RELATIVE を肩代わり適用すると load bias が**二重加算**され、RELATIVE で再配置された全ポインタが `実値×2` になる。エントリ後 ld.so が `blr x8`（`x8`=関数ポインタ×2＝ワイルドアドレス）で命令フェッチ SIGSEGV。
  - **決定的証拠**: SIGSEGV の `pc == si_addr == x8 == 0xf8789f5640`。これは正当な ld.so アドレス `0x7c3c4fab20` のちょうど **2 倍**。別 run でも `0xdfe1927640 == 0x6ff0c93b20 × 2`。`lr`/`x30` ほか全レジスタが ld.so マップ域（`0x7c3c5_____`）。stat/readlink/linkat とは無関係＝0.8.62〜0.8.64 が触っていた箇所と別系統。
  - なぜ 0.8.59 で気付かれなかったか: 0.8.59 の検証は in-process ハーネスで **単純 static-PIE / 非PIE のみ**を通し、ld.so 経由の動的バイナリ起動を実機で踏んでいなかった。RELATIVE 適用が ld.so にも当たることを見落とした。
- **修正（0.8.67 / 自己再配置 ELF はローダ肩代わりを抑止）**: ローダの RELATIVE/RELR 肩代わり（§B-5 修正①）を **`skip_reloc` でゲート**。`load_elf_and_jump(path, argv, envp, skip_reloc)` に引数追加し、`skip_reloc` のとき relocation ブロックをスキップ。`plan_exec` は loader 対象が「自己 relocate する ELF か」を知っているので信号を渡す:
  - **動的 ELF 経路（case 2）＝ld.so 本体をロード → `skip_reloc=1`**（ld.so は self-relocate）。
  - **shebang 経路でインタプリタが動的（`idyn==1`）→ `skip_reloc=1`**（同上）。
  - **静的 ELF 直接ロード（case 3）→ `skip_reloc=0`**（0.8.59 の単純 static-PIE 対応＝自己 relocate しない bionic static-PIE のため肩代わり維持）。
  - 信号の通し方は `wrap_with_loader(cfg, plan, skip_reloc)` が argv 先頭の subcommand トークンを `--loader`（肩代わり ON＝従来）/ `--loader-noreloc`（肩代わり OFF）で切替え。`loader_main`/`main` の両方が新トークンを受理。子 argv は不変（位置引数を増やさない）。
  - biased phdr（§B-5 修正②）は `skip_reloc` に関係なく従来どおり（二重加算と無関係・退行なし）。
- **✅ e2e 合格（2026-06-10・0.8.67 APK）**: z2root で `Arch Linux ARM` 正常起動を確認＝0.8.62〜0.8.66 の起動退行は解消。`build-z2root.sh` 成功・`loader-noreloc` 文字列を `.so` で確認。

## 11. B-4 / B-5（8セッション目・2026-06-09）

ユーザー指示「B-4・B-5 を進める」。B-4=SSH reset 実機確認（chmod(/dev/pts) EPERM 疑い）、B-5=静的バイナリ segfault。

### B-4: SSH 認証直後 reset（= 既に解決済み）
- **修正は現存**: `z2root.c:957` の fakeroot exit ハンドラが `case 151/152/159/54/55/52/53`（chmod/fchmod/fchmodat/chown 系）の失敗戻り値を 0 へ偽装し `[z2trc] FAKE chmod nr=%ld ret=%ld->0` を出す。dropbear が PTY 確保時に `chmod(/dev/pts/N,0620)` を呼び untrusted_app で EPERM → これを 0 偽装してセッション存続させるのが修正の本体。
- **実機検証済み**: [[project-z2root-ssh-reset-repro]] のとおり 2026-06-07L に正しく再ビルドした v54 で `ssh -tt -p 2222 root@127.0.0.1` が PTY 疎通成功（uid=0・EXIT=0）。`dbsrv.log`=`Pubkey auth succeeded`→正常切断、`z2root_trace.log` に `SYS nr=53`→`FAKE chmod ret=-13->0` 発火を確認済み。
- **残作業**: ヘッドレス（二重 ptrace 環境）では再現不可。新 APK インストール後の実機 UI での再確認のみ＝**コード変更は不要**。判定法は memory 参照。

### B-5: 静的バイナリ segfault（真因特定・ローダ 2 修正を 0.8.59 で z2root.c へ移植済み・第3クラッシュは NDK 固有制約と確定）

**真因 = static-PIE（ET_DYN）特有**。3 分類で挙動が分かれる:
| バイナリ種別 | 例 | z2root ローダでの挙動 |
|---|---|---|
| static 非PIE (ET_EXEC) | `/tmp/t_exec`, `/tmp/t3_exec`, NDK clang/lld | 元から動く（p_vaddr が絶対=base 0）。constructor も走る。**ただし PT_PHDR を持つ前提**（NDK 製は持つ。musl 製は持たず → B-6 で別途修正） |
| 単純 static-PIE (ET_DYN) | `/tmp/t_pie` (write のみ) | 修正前 crash → **下記 2 修正で動く（0.8.59 で移植済み）** |
| リッチ static-PIE (ET_DYN) | `/tmp/t2_pie` (printf/malloc/pthread/TLS) | 2 修正後も **第3クラッシュ＝NDK 固有制約で解決不能**（下記） |

bionic NDK の static-PIE crt(`_start`) は (1) 自己 relocation を行わない、(2) `__libc_init_mte`/`__bionic_get_tls_segment` が load_bias を即値 0 と仮定し phdr の p_vaddr を絶対アドレスとして扱う（ET_EXEC 前提）。よって ET_DYN を base!=0 でロードすると未 relocate ポインタや 0 番地近傍アクセスで落ちる。ld.so/proot loader 相当の下準備をローダ側で肩代わりする必要がある。

**検証ハーネス**（proot 干渉を避けるため `--loader` 直叩きでなく in-process で検証）:
- `/tmp/test_loader.c` を **動的 EXE** としてビルド（bionic linker64 が正常起動）。`main` が引数の静的 ELF を匿名 PROT_EXEC へマップし、`z2root.c` の `load_elf_and_jump` と同等の手順で auxv/stack を組んで `br entry`。SIGSEGV/SIGBUS ハンドラ（sigaltstack）で si_addr/pc/sp/code を出力。**z2root.c の `load_elf_and_jump` と同一ロジック**（2 修正込み）なので、ハーネスで通る＝z2root.c でも通る。
- `⚠️ /tmp は揮発` → 次セッションは `test_loader.c` とテストバイナリを作り直す。`t.c`→t_exec/t_pie、`t2.c`→t2_pie（printf+fopen+strerror+errno+pthread+TLS）、`t3.c`→t3_pie/t3_exec（`__attribute__((constructor))` で `CTOR_RAN` 出力＋`main` で `MAIN_RAN` 出力。PIE/非PIE をリンクフラグだけ変えてビルドし constructor 実行有無を対照）。

**ローダ修正 2 点（0.8.59 で `z2root.c` の `load_elf_and_jump` へ移植済み）。単純 static-PIE と static 非PIE を両立・退行なし**:
1. **RELR/RELA RELATIVE relocation の自前適用**（`z2root.c` の `close(fd)` 直後）: ET_DYN かつ base!=0 のとき、PT_DYNAMIC を辿って `DT_RELR`(36)/`DT_ANDROID_RELR`(0x6fffe000) と `DT_RELA`(7) を読み、`R_AARCH64_RELATIVE`(1027) を `*(base+r_offset)=base+r_addend` で適用。RELR は LSB=0 を address word、LSB=1 を 63bit bitmap として展開。
   - **⚠️ 0.8.67 で `skip_reloc` ゲート追加（必須）**: この肩代わりは**自己 relocate しない** static-PIE（bionic）専用。`ld.so` のように自己 relocate する ELF に当てると二重加算で SIGSEGV する（§10「0.8.67 で起動退行を真に根治」）。`plan_exec` の動的 ELF / 動的 interp 経路は `skip_reloc=1`（`--loader-noreloc`）でこのブロックを抑止し、静的直接ロードのみ `skip_reloc=0`（`--loader`）で適用する。
2. **biased phdr を AT_PHDR に渡す**（`phdr_mem` 算出直後）: ET_DYN かつ base!=0 のとき、phdr のコピーを作り各 entry の p_vaddr に base を加算（`p_vaddr+=base`）、その配列を `AT_PHDR` に渡す。bionic の note/TLS 走査（load_bias=0 仮定）を成立させる。
- ハーネス再検証（2026-06-09）: `t_exec`/`t3_exec`(非PIE) と `t_pie`(単純 static-PIE) は exit=0（退行なし）。

**第3クラッシュ＝NDK 固有制約と確定（ローダでは解決不能・parity gap ではない）**:
- 症状: `/tmp/t2_pie`（printf/malloc/errno/pthread/TLS）が `SEGV si_addr=0x0 pc=<base+0x66b70> code=1`。逆アセンブルすると `__strchr_aarch64` の `ld1 {v1.16b,v2.16b},[x2],#32` で `x2`(文字列ポインタ)=NULL。
- **確定診断**: IRELATIVE 仮説は**否定**。t2_pie/t3_pie の reloc は RELR/RELA の `R_AARCH64_RELATIVE` のみで `.rela.plt`/`.rela.iplt`（IRELATIVE）は無い。コールチェーンは `strchr(NULL)` ← `GetPropertyInfoIndexes` ← `GetPropAreaForName` ← `SystemProperties::Find` ← `should_trace()`(atrace) ← `pthread_create` ← `main`。atrace 用 `CachedProperty` の name ポインタ（`g_debug_atrace_tags_enableflags`、.bss）が NULL なのは、**それを実行時に設定するコンストラクタが走っていない**ため。
- **裏取り**: `__attribute__((constructor))` を仕込んだ同一ソースを PIE/非PIE でビルドして対照すると、**非PIE は `CTOR_RAN`+`MAIN_RAN`、static-PIE は `MAIN_RAN` のみ**（コンストラクタ未実行）。逆アセンブルでも、動く非PIE crt の `_start_main` は preinit/init/fini の3配列境界を読み structors にセットするのに対し、落ちる static-PIE crt の `_start_main` は `fini` しか処理せず init_array セットアップ命令が欠落（`__init_array_start/end` シンボルも PIE 版には未定義）。canonical な `DT_INIT_ARRAY`(40B) は ELF に存在するのに crt が一度も参照しない。
- **なぜローダで直せないか**: コンストラクタは libc 初期化後・`main` 前に走る必要があり、ローダは `_start` へ jump 後に制御を失う（後追い呼び出し不可）。PIE バイナリには init_array を呼ぶコード経路自体が無く GOT パッチでも復旧不能。カーネルも static-PIE を relocate しない＝**Android では単純 static-PIE は外部 relocator 無しに成立せず、proot/カーネルでも同じ結果**。よって z2root 固有の parity gap ではない。
- **残課題（実機 e2e）**: 0.8.59 の 2 修正（単純 static-PIE 起動）は本修正入り APK を本体 UI へ導入後に実機確認が必要（ハーネスは in-process 検証で実機相当だが二重 ptrace 回避のため）。リッチ static-PIE（claude code 含む大半の実バイナリは動的 ELF なので無関係）の第3クラッシュは仕様上の制約として確定。

### B-6: PT_PHDR を持たない static 非PIE（ET_EXEC）の AT_PHDR バグ（9セッション目・2026-06-09・0.8.60 予定）

- **発端**: 同梱 Alpine の推奨パッケージ拡張のため `scripts/build-alpine-rootfs.sh` を実行→**`apk.static`（Alpine の静的 musl、aarch64 ET_EXEC）が z2root 下で即 segfault**。エンジンは `/proc/self/maps` の `libz2root.so` と `TracerPid` で **z2root と確定**（[[feedback_confirm_engine_first]]）。
- **真因（2点、どちらも「static 非PIE は元から動く」の前提=PT_PHDR ありが崩れたケース）**:
  1. **AT_PHDR にファイルオフセットを渡していた**: `apk.static` は program header が **LOAD/LOAD/NOTE/GNU_STACK/GNU_RELRO の5本のみで PT_PHDR を持たない**。旧 `load_elf_and_jump` は `has_pt_phdr` が偽のとき `phdr_mem = base + e_phoff` とし、ET_EXEC（first LOAD が `p_offset=0 / p_vaddr=0x400000`）では `e_phoff=0x40` をそのまま絶対アドレス `0x40` として AT_PHDR に渡していた。musl 起動が phdr 走査で 0x40 を触り segfault。NDK 製 ET_EXEC は PT_PHDR を持つため露見しなかった盲点。
  2. **ローダ自身の bionic heap を MAP_FIXED で上書き**: `libz2root.so` は `0x200000` に置かれ、その heap が `0x276000-0x488000`。`apk.static` の LOAD1 は `0x400000` 固定で、`MAP_FIXED` がローダの heap（`0x400000-0x488000`）を破壊する。jump 前に `malloc` を使う（旧デバッグ `fprintf`）と壊れた arena を触って crash。非デバッグ経路は jump まで malloc を使わないので #1 さえ直れば動作可（jump 後はその領域を対象プログラムが所有）。
- **修正（`z2root.c` の `load_elf_and_jump`）**:
  1. PT_PHDR が無い場合、**`e_phoff` を含む PT_LOAD を探し `p_vaddr + (e_phoff - p_offset)` で仮想アドレスへ変換**して AT_PHDR に渡す（`base + e_phoff` フォールバックは first LOAD が offset0/vaddr0 のときのみ正）。ET_DYN の biased phdr 経路は不変。
  2. jump 直前の 2 つのデバッグ出力を **`snprintf`(stack) + `write(2)`** に変更し malloc-free 化（heap 上書き後でも壊れない）。
- **既知の限界（未対応）**: 静的 ET_EXEC の LOAD 範囲が**ローダのコード（`0x200000-0x276000`）と重なる**バイナリは MAP_FIXED で自分自身を壊す。`apk.static` は `0x400000` 起点でコードと衝突しないため動く。根本対策はローダを高位アドレスへ単独リンクする（proot loader 方式）ことだが今回は範囲外。
- **残課題（実機 e2e）**: 本修正入り APK を本体 UI 導入後、z2root 上で `Z2ROOT_LOADER_DEBUG=1 apk.static --version` が起動することを確認。Alpine rootfs 再生成（[[ALPINE-BUNDLE-HANDOFF]]）と同じ次ビルドサイクルで合流。

## 12. 稼働版数の確定と readlink 切り分け（12〜13セッション目・2026-06-09）

要約は §0 TL;DR。**結論＝稼働 `.so` は 0.8.61（text バイト一致で確定）。12セッション目の「0.8.56 以下／stale .so」結論は撤回。** 症状(1) readlink 19B は版数とも `.l2s` とも無関係の GNU bare readlink 表示 quirk でビルド無影響。残る症状(2)(3) を個別に切り分けるのが次手。

### 12.1 エンジン確認（必ず最初・[[feedback_confirm_engine_first]]）
```
grep TracerPid /proc/self/status            # 非0
T=$(grep TracerPid /proc/self/status|awk '{print $2}'); cat /proc/$T/comm   # libz2root.so
tr '\0' ' ' < /proc/$T/cmdline              # proot … -0 --link2symlink -r …/distros/archlinux …
```
- uid=0/PROOT_* env だけでは proot と区別不可。tracer comm=`libz2root.so` で z2root 確定。

### 12.2 版数判定＝`/proc/self/mem` text md5 照合（正本。旧「機能3点」は無効）
**⛔ 旧テスト「readlink .l2s が 19B なら旧版」は無効**（0.8.61 でも 19B が出る＝§12.5 参照）。`Z2ROOT_LOADER_DEBUG` も版数判定に使うな（0.8.25 からある）。版数は稼働 `.so` の text バイトで直接照合する:
```
# z2root の case-3 ローダは libz2root.so を tracee 自身にも 0x200000 へマップする。
# map_files は読めないが /proc/self/mem から text を抜けばファイル相当のバイトになる
# (static 非PIE 固定 base=relocation 無し)。
grep libz2root /proc/self/maps               # r-xp 行: 例 vaddr 0x218000, file off 0x18000
START=$((0x218000)); FOFF=$((0x18000)); LEN=$((0x54000))   # r-xp の len は maps から
dd if=/proc/self/mem of=/tmp/run_text.bin bs=4096 iflag=skip_bytes,count_bytes skip=$START count=$LEN
unzip -o -j <APK> 'lib/arm64-v8a/libz2root.so' -d /tmp/sochk
dd if=/tmp/sochk/libz2root.so of=/tmp/art_text.bin bs=4096 iflag=skip_bytes,count_bytes skip=$FOFF count=$LEN
md5sum /tmp/run_text.bin /tmp/art_text.bin    # 一致=その版が稼働
```
- 0.8.61 成果物: text md5 `9b1ac58ffcaae0bc3c719385a2bc822b` / ファイル全体 md5 `15ac3275c9c0bfb083da2893923abb8c`(size 448768)。
- 13セッション目に上記で稼働 text=0.8.61 を確定（一致）。

### 12.3 （旧 12.3 解消）稼働 `.so` の照合は §12.2 で可能
- `-b /data/app` が無く `/proc/<tracer>/exe` の host パスは guest から open 不能だが、§12.2 の `/proc/self/mem` 経由で照合できる（bind 追加は不要）。

### 12.4 次手
1. **z2term 再起動は不要**（版数は 0.8.61 確定）。
2. 残る症状を §12.5 と同じ手法で個別に切り分ける:
   - **(2) `git clone` の `fatal: hardlink different from source`**（B-3 §10）: **切り分け完了＝0.8.58 修正は構造的に未発火（200/200 別 inode・0 fake）と確定**。真因は旧 `linkcopy_record` がホストパスを再 `stat()` して dest inode を採取し tracee の見る inode とずれていたこと。**0.8.62 で `copy_for_link` 内の生成直後 fd を `fstat()` して dest inode を確定採取する方式へ堅牢化**（§10 末尾）。コンパイル確認済・実機 e2e は次 APK 導入後。
   - **(3) `tar` hard link**: 13セッション目の再実測では `ln`(hardlink) も `tar x` も copy-fallback で通り `Permission denied` は出なかった（12セッション目とは差）。再現条件を要特定。
3. 実機トレースが要るなら `~/.z2root_trace_on` を置いて**ユーザーが UI から z2term を再起動**（tracer は起動時に1回しか `Z2ROOT_TRACE` を読まない・ネスト起動不可、§0 参照）。

### 12.5 症状(1) readlink 19B の切り分け詳細（13セッション目）
GNU bare `readlink <NDK>/libc++_shared.so` が `/root/android-sdk/n`(19B) を返す件を `readlink` を追って切り分けた結果:
- **bufsiz 依存（C で readlinkat を直接実測）**: bufsiz 19→19B `/root/android-sdk/n` / bufsiz 66→21B / **bufsiz≥182→full 137B**（正しい guest パス `…/.l2s.libc++_shared.so0002`）。
- **大バッファ手段は全て full**: `python os.readlink`・perl・C(bufsiz≥182)・`readlink -f` は 137B 完全＝**§9 修正は機能している**。普通の（guest 格納）symlink は coreutils でも full（対照実験で確認）。
- **版数・`.l2s` 固有ではない**: host→guest 変換で縮む symlink 全般で起こり、**`/proc/self/cwd`（magic link）でも 19B** 再現。
- **ビルド無影響**: `.l2s` チェーンを `open`/`read`/`stat -L` で辿れ ELF マジック取得・実体 9290184B 到達。`-lc++_shared` は open ベース。
- **機構**: bare `readlink` は glibc 内部で readlinkat を呼ぶ（LD_PRELOAD で捕捉不可と確認＝シムの readlinkat override に届かない）。z2root exit(`z2root.c:873`)が変換後 guest 結果を tracee bufsiz へ clamp し、内部 bufsiz が小さいと先頭19文字に切れる。根は **symlink target の格納形式**: 現行作成 symlink は guest パス格納（st_size=guest 長・変換不要）、レガシー `.l2s` は旧 proot/z2root が**ホストパス格納**（st_size=182=host 長, 実測）。z2root は symlink の st_size を guest 長へ変換しない（該当コード無し）。
- **対処要否**: ビルドにも claude/Bun（大バッファ・`/proc/self/cwd` は §9 で full）にも実害なし＝**今は対処不要**。完全 parity を狙うなら exit の clamp 廃止＋symlink st_size の guest 変換が筋だが、別タスク。

### 12.6 B-3 実機 e2e チェックリスト（✅ 2026-06-10・0.8.67 APK で合格）
**結果（2026-06-10）**: 0.8.67-alpha(75) APK を z2root エンジンで実行し、step 1 基本 e2e に合格。`git clone /tmp/r-src /tmp/r-dst` が `--no-hardlinks` 無しで `hardlink different from source` を出さず完了（CLONE_EXIT=0）、`git -C /tmp/r-dst fsck --full`=clean（exit 0）、`ln a b` 実 hardlink は同一 inode で成功（退行なし）。step 2 trace 裏取りは未実施（基本 e2e 合格のため任意）。

**前提**: `app/build/outputs/apk/full/release/app-full-release.apk`（0.8.67-alpha(75)）を本体 UI でインストール → 設定で実行エンジン = **z2root** に切替え（「このタブの実エンジン」行が z2root を示すこと。0.8.44）。版数確認は §12.2 の `/proc/self/mem` text md5 を正本とする（設定の版数表示でも可）。

1. **基本 e2e（hardlink 検証が通るか）**: z2root タブで
   ```sh
   cd /root && rm -rf /tmp/r-src /tmp/r-dst
   git init -q /tmp/r-src && cd /tmp/r-src && git config user.email a@b && git config user.name a \
     && for i in $(seq 1 50); do echo $i > f$i; done && git add -A && git commit -qm x
   git clone /tmp/r-src /tmp/r-dst   # ← --no-hardlinks を付けずに通れば B-3 解消
   ```
   - **成功条件**: `fatal: failed to create link …: hardlink different from source` が**出ず** clone 完了。`git -C /tmp/r-dst log --oneline` と `git -C /tmp/r-dst fsck` が clean。
   - **失敗時**: 旧来どおり fatal が出るなら下の trace 経路で REC/FIND を見る。

2. **trace で発火確認（任意・確実な裏取り）**: hardlink copy-fallback と偽装の発火を直接見る。
   - `touch ~/.z2root_trace_on` を置いて**ユーザーが UI から z2term を再起動**（tracer は起動時に1回だけ `Z2ROOT_TRACE` を読む・§0 / §12.4-3）。
   - 再起動後に上の `git clone` を実行 → `~/z2root_trace.log`（または `$Z2ROOT_TRACE` 出力先）に:
     - `[z2trc] linkcopy REC src(dev=… ino=…) dest_ino=… used=N`（copy-fallback が成立し記録された）
     - `[z2trc] linkcopy FIND nr=79 ino=… used=N -> HIT`（git の `newfstatat` が dest を stat し**偽装が当たった**）
   - **REC は出るが FIND が常に `miss`** なら、fstat で採った dest_ino と git の見る inode がまだずれている＝`copy_for_link` の `fstat` 対象 fd と git が開く実体の差を要再調査（temp 経由 open-then-unlink 等の干渉を疑う）。
   - 検証後は `rm ~/.z2root_trace_on` で trace を戻す。

3. **退行確認**: `ln a b`（実 link 成功経路）/ `npm`（あれば）/ `tar x` が従来どおり動くこと＝偽装は copy-fallback 経路限定で副作用が無いこと。

4. **完了したら**: §10「0.8.62 / fstat 修正」と本節を「e2e 検証済（日付）」へ更新し、§0 の B-3 を完了に倒す。残るは症状(3) `tar`（§12.4-3）。

## 13. seccomp フィルタ監査（2026-06-12・コードレビュー / 実機実走は別途）

引き継ぎ宿題「z2root の seccomp フィルタが `io_uring`・`statx`・`openat2` を捕捉しているか」のコード監査結果。対象は `app/src/main/cpp/z2root/z2root.c`。

### 13.1 封じ込めモデル（前提）
- **z2root.so は chroot/mount namespace を一切使わず、純粋に ptrace パス変換で封じ込める**（grep で `chroot(`/`pivot_root`/`unshare(`/`CLONE_NEWNS` いずれも z2root.c に無し。chroot は別エンジン `ProotLauncher.chrootBootstrap` 側で、z2root.so の経路とは別）。
- 子は `run_child` で `chdir(host_cwd)`（rootfs 内）してから exec。よって **相対パスは rootfs 内に解決され安全**だが、**トレース対象外 syscall の「絶対パス」はカーネルがホスト root 起点で解決＝封じ込めを素通り**する。これが監査の判定軸（=「絶対パスを持つ syscall がトレース＆パス変換されているか」）。
- seccomp フィルタ（`install_seccomp_filter`, z2root.c:1709）の終端 action は **RET_TRACE（リスト該当）と RET_ALLOW（残り全部）の 2 つだけ**。明示 deny（RET_ERRNO/RET_KILL）は持たない。

### 13.2 結論サマリ
| syscall | トレース? | パス変換? | 判定 |
|---|---|---|---|
| `statx`(291) | ✅ (kTraceSyscallsBase) | ✅ (syscall_paths) | **OK**（懸念解消） |
| `openat2`(437) | ✅ | ✅ | **OK**（懸念解消） |
| `io_uring_*`(425-427) | ❌ RET_ALLOW | — | **条件付き安全**（下記 13.3） |
| `truncate`(45) | ❌ | ❌ | **ギャップ**（絶対パスでホスト直行） |
| xattr by path (5/6/8/9/11/12/14/15) | ❌ | ❌ | **ギャップ**（同上） |
| `name_to_handle_at`(264) | ✅ 0.8.83 | ✅ 0.8.83 | **対処済**（path arg1 変換） |
| `open_by_handle_at`(265) | — | — | 非対象（path 無＝handle・CAP 必須で EPERM） |

### 13.3 io_uring（最重要懸念）の実態
- z2root **自身のフィルタは io_uring を RET_ALLOW**（=カーネルへ素通り）にしている。単体では「io_uring を捕捉していない」。
- ただし実運用では安全に倒れている。理由: **Android の untrusted_app seccomp が io_uring を SIGSYS で弾き**、z2root のトレーサが SIGSYS を捕まえて **非特権 syscall は一律 `-ENOSYS` に化かす**（z2root.c:2148-2164, コメントは 2130-2147）。これで libuv/node 等は io_uring 経路を諦め、トレース対象の旧 syscall（epoll/openat 等）へフォールバックする。→ **結果的にパス変換を経由する安全な経路に戻る**。メモリ `project_z2root_seccomp_statx_verified` の「io_uring のみ ENOSYS で安全」と一致。
- **残リスク（防御の多層性の欠如）**: 安全性が **外側の Android フィルタに依存**しており、z2root 自前では担保していない。Android のポリシーが io_uring を許す将来版・別コンテキストでは RET_ALLOW のまま素通りし、パス変換/fakeroot を全面バイパスし得る。
- **✅ 対処済み（0.8.81-alpha(89)・2026-06-12）**: `install_seccomp_filter`(z2root.c) の BPF に **`SECCOMP_RET_ERRNO(ENOSYS)` 終端と deny 比較ブロック**を追加し、`kDenySyscalls`=io_uring_setup/enter/register(425-427) を **z2root 自前で ENOSYS** へ倒すようにした。フィルタ構成は `[arch]→[LD nr]→[deny 比較 D 個→DENY]→[trace 比較 C 個→TRACE]→ALLOW/TRACE/DENY` の 3 終端へ拡張。NDK aarch64 ビルドで compile OK（`build-z2root.sh`）。
  - **挙動**: Android が io_uring を `RET_TRAP` する現行端末では action 優先順 (RET_TRAP>RET_ERRNO) で **Android の TRAP が勝つ＝従来どおり SIGSYS→トレーサ ENOSYS 化の経路**を通り挙動不変。Android がトラップしないコンテキストでのみ本 ERRNO が効き、SIGSYS を経ずに直接 ENOSYS を返す。どちらの経路でもゲストには ENOSYS が見え、安全な旧 syscall へフォールバックする。
  - **要・実機 e2e**: 0.8.81 APK 導入後、`liburing` 直叩きの C プログラム or io_uring 利用ツールが ENOSYS に倒れて旧経路で動くこと、node/claude 対話起動に退行が無いことを確認（現行端末では Android TRAP 経路のままなので退行は出ない想定）。

### 13.4 監査中に新たに見つかったパス変換ギャップ
io_uring とは別に、**トレースもパス変換もされない「絶対パスを取る」syscall** が存在する（aarch64 は非 `*at` 系が中心的に漏れる）:
- **✅ `truncate(45)`（0.8.82 で対処）**: `truncate("/abs/path", len)` が untranslated → ホスト直行していた。多くのツールは `open()+ftruncate(fd)`（fd は traced openat 由来で変換済み）を使うので発火は稀だが、絶対パス直叩きは封じ込めを破る。`kTraceSyscallsBase`＋`syscall_paths()`(`{0,-1,1,...}` path arg0/follow) に追加。`ftruncate(46)` は fd ベースなので非対象。
- **✅ xattr by path（0.8.82 で対処）**（`setxattr`/`lsetxattr`/`getxattr`/`lgetxattr`/`listxattr`/`llistxattr`/`removexattr`/`lremovexattr` = 5/6/8/9/11/12/14/15）: `tar`/`rsync`/`cp -a` 等が使う。相対パスなら cwd 経由で rootfs 内に解決され無害だが、絶対パスはホスト直行していた。トレース＆パス変換を追加（非 `l` 版=follow / `l*` 版=no-follow、いずれも path arg0・dirfd 無し）。`f*xattr`（fd 版 7/10/13/16）は openat 由来 fd なので影響なし＝非対象。
- **✅ `name_to_handle_at(264)`（0.8.83 で対処）**: `name_to_handle_at(dirfd, pathname, handle, mnt_id, flags)` の絶対 pathname が untranslated → ホスト直行していた。`kTraceSyscallsBase`＋`syscall_paths()` に追加（pathname=arg1・dirfd=arg0、既定 no-follow / `AT_SYMLINK_FOLLOW`=0x400 で follow＝`linkat` 同様）。`overlayfs`/`fanotify`/`open_by_handle_at` ペアを使う稀なツールが対象。
- **`open_by_handle_at(265)`（非対象）**: 第2引数は不透明な `file_handle` で**変換可能なパスを持たない**。かつ `CAP_DAC_READ_SEARCH` 必須＝untrusted_app では EPERM で弾かれるため、ハンドル詐称による脱出も成立しない。`name_to_handle_at` を変換すればハンドルは翻訳済みパス由来になる。コードにコメント記載。
- 特権系（`mount(40)`/`umount2(39)`/`pivot_root(41)`/`chroot(51)`/`swapon/off`）も未変換だが、非 root では EPERM になり実害は出にくい（fakeroot で root を装っても実カーネルが EPERM）。

**対処状況**: io_uring 明示 deny（13.3, 0.8.81）→ truncate/xattr by-path 変換（本節, 0.8.82）→ `name_to_handle_at` 変換（本節, 0.8.83）の順で実装し、**コード監査ギャップは解消**（`open_by_handle_at` は path 無＝非対象）。NDK aarch64 で compile OK。**いずれも実機 e2e は未**（絶対パス xattr/truncate/handle が rootfs 配下を指すか・退行が無いかの裏取りは `scripts/z2root-cmdtest.sh` 拡張＋実走で）。

### 13.5 検証ステータスと次手
- 本節は **静的コード監査のみ**。実機での挙動裏取り（`scripts/z2root-cmdtest.sh` の io_uring/xattr/truncate 系を z2root タブで実走し、絶対パス xattr が rootfs 配下に効くか・io_uring が ENOSYS に倒れるか）は未実施。
- io_uring の ENOSYS フォールバックは過去セッションで claude(node) 対話起動の文脈で観測済み（§ メモリ参照）だが、`liburing` 直叩きの C プログラムでの確認は未。
- 実装に着手する場合: 13.3 の io_uring 明示 deny（低リスク・効果大）を先に。13.4 のパス変換追加は cmdtest で発火条件を作ってから（絶対パス xattr/truncate のテストケース追加）。いずれも native 変更＝版数 +1・rootfs 同梱 .so 再ビルド・実機 e2e が必要。
