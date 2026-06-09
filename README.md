# Z2Term — Zero 2 Terminal

**English** ・ [日本語](README.ja.md)

**Z2Term** is a custom-built terminal app that runs on Android.
It runs several Linux distributions (Alpine / Ubuntu / Arch / Kali) via PRoot,
and lets you install any command through package managers like `pacman` / `apt`.

> The 5th project of the Zero to Ship initiative.
> Blog: https://zero-to-ship-app.vercel.app

## Screenshots

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/cui-terminal.png" width="280" alt="CUI: Alpine terminal with the custom keyboard"><br><sub>CUI — Alpine terminal + custom keyboard</sub></td>
    <td align="center" width="50%"><img src="docs/images/gui-thunderbird.png" width="280" alt="GUI: Thunderbird running on Xvnc"><br><sub>GUI — Thunderbird running on Xvnc</sub></td>
  </tr>
</table>

## Download

**You can download the latest APK directly from GitHub Releases** (no build needed):

- Go to the latest: **<https://github.com/orgsonai/z2term/releases/latest>**
- 0.8.21-alpha direct link: [z2term-0.8.21-alpha.apk](https://github.com/orgsonai/z2term/releases/download/v0.8.21-alpha/z2term-0.8.21-alpha.apk)

Tap the APK on your Android device → allow "Install from unknown sources" to install.
(The `full` flavor bundles prebuilts, so the APK works standalone. Not distributed on Google Play.)

## Current version

**0.8.63-alpha (versionCode 71) — fixes a startup regression introduced by 0.8.62: z2root could no longer launch a guest (`Arch Linux ARM` died immediately with `exitCode=-1`).** Making the B-3 `linkcopy` record finally *succeed* (0.8.62) inadvertently woke a previously-dormant code path: the `newfstatat`/`fstat`/`statx` exit handlers spoof a copied dest's `st_dev`/`st_ino` to the source's, but matched on **inode number alone**. With recording now live (`g_linkcopy_used > 0`), any guest file whose inode number happened to collide with a recorded dest got its identity falsified — corrupting early boot stats and killing the guest. On Android `untrusted_app`, `link(2)` is denied everywhere, so every guest hardlink copy-falls-back and records, making collisions likely during boot. Fix: the match key is now the dest's full `(dev, ino)` (a live host file's `(dev, ino)` is unique), so unrelated files no longer false-positive. B-3 hardlink spoofing is preserved. Compile-verified; e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10.

Previously (0.8.62-alpha, versionCode 70): re-fixed B-3 (`git clone` hardlink spoof) after finding the 0.8.58 fix never engaged on the running 0.8.61 engine — `copy_for_link` now `fstat()`s the just-created output fd to capture the dest inode at creation, and `linkcopy_record` takes those values by argument instead of re-stat'ing. (This re-fix is what exposed the 0.8.63 startup regression above.)

Previously (0.8.61-alpha, versionCode 69): expands the bundled Alpine rootfs with modern CLI / networking / dev toolchains (`ROOTFS_VERSION` 8 → 9, auto re-extracted on app update). New packages: Tier 5 modern CLI (`ripgrep`, `fd`, `fzf`, `bat`, `eza`), Tier 5.5 network diagnostics (`netcat-openbsd`, `socat`, `mtr`), and Tier 6 dev languages / build toolchain (`python3`, `py3-pip`, `nodejs`, `npm`, `build-base`). The bundled `full`-flavor `.tgz` grows to ~168 MiB (503 MiB unpacked, 260 packages); the `foss` flavor is unaffected on disk but downloads more at runtime. Also hardens `scripts/build-alpine-rootfs.sh` for regeneration under proot's `link2symlink`: (1) host-arch auto-detection for `apk-tools-static` (was x86_64-only), (2) direct exec fallback when `fakeroot` can't use SysV IPC, (3) temp-file package-list read (no `/dev/fd` process substitution), (4) `rm -rf` retry — `link2symlink` turns apk's hardlinks into `.l2s..apk.*` (host-absolute symlinks + `.000N` payload) that fail a single-pass `rm -rf` with "Directory not empty", and (5) `tar --exclude='*/.l2s.*'` so packing skips those ELOOP scaffolds (real binaries stay via the shared inode; on a normal host `.l2s.*` doesn't exist, so all of this is a no-op).

Previously (0.8.60-alpha, versionCode 68): fixes the self-loader (`load_elf_and_jump`) for static non-PIE (ET_EXEC) binaries that lack a `PT_PHDR`, e.g. Alpine's static-musl `apk.static`. Discovered while regenerating the bundled Alpine rootfs: `apk.static` segfaulted instantly under z2root. Two causes, both edge cases of "non-PIE static already works" (which silently assumed a `PT_PHDR` is present, as NDK-built ET_EXEC has): (1) with no `PT_PHDR`, the loader passed `AT_PHDR = base + e_phoff`, i.e. the *file offset* `0x40` as an absolute address, so musl's startup dereferenced `0x40` while walking phdrs and crashed (NDK ET_EXEC carries `PT_PHDR`, hiding this). Fix: when `PT_PHDR` is absent, find the `PT_LOAD` containing `e_phoff` and translate to a virtual address `p_vaddr + (e_phoff - p_offset)` (the `base + e_phoff` fallback is only correct when the first LOAD is at offset 0 / vaddr 0). (2) `libz2root.so` sits at `0x200000` with its heap at `0x276000-0x488000`, and `apk.static`'s LOAD1 is fixed at `0x400000`, so the loader's `MAP_FIXED` overwrites its own bionic heap — any `malloc` before the jump (the old debug `fprintf`) then crashes. The two debug prints are now `snprintf`(stack)+`write(2)`, malloc-free; the non-debug path uses no malloc before the jump, so it is unaffected. Known limit (out of scope): a static ET_EXEC whose LOAD range overlaps the loader's *code* (`0x200000-0x276000`) would still clobber itself — `apk.static` at `0x400000` does not. ⚠️ e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11 (B-6).

Previously (0.8.59-alpha, versionCode 67): adds static-PIE (ET_DYN) support to the self-loader (`load_elf_and_jump`). NDK static binaries come in two flavors: ET_EXEC (non-PIE, already worked) and ET_DYN (static-PIE), which is relocated by neither the kernel nor an interpreter, and whose bionic NDK crt does not self-relocate — so loading it at `base!=0` crashes on unrelocated pointers or on `__libc_init_mte`/`__bionic_get_tls_segment` assuming `load_bias=0`. Fix: the loader now (1) walks `PT_DYNAMIC` and applies RELR/RELA `R_AARCH64_RELATIVE`(1027) as `*(base+off)=base+addend`, and (2) passes `AT_PHDR` a phdr copy with `base` added to each `p_vaddr`. Both run only when `ET_DYN && base!=0`, so non-PIE static binaries and the NDK clang/lld themselves don't regress. Verified in an in-process loader harness: a simple static-PIE (`write` only) runs; non-PIE is unaffected. ⚠️ A "rich" static-PIE (printf/malloc/pthread/TLS) still crashes due to a separate **NDK-specific constraint the loader cannot fix**: the bionic NDK static-PIE crt (`_start`) never calls `.init_array` constructors (a constructor-tagged static-PIE runs `main` only, no `CTOR_RAN`), and constructors must run before `main` while the loader loses control at the jump — this behaves identically under proot/the kernel, so it is **not a z2root parity gap**. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11.

Previously (0.8.58-alpha, versionCode 66): fixes B-3 — local `git clone` failing with `fatal: hardlink different from source`. Root cause is an OS constraint, not a z2root bug: Android SELinux (`untrusted_app`) bans `link(2)` device-wide, so z2root's link2symlink emulation always copy-falls-back, producing a dest with a *different* inode; git 2.46+ then lstats the dest after `link()` and rejects it because `st_dev`/`st_ino` don't match the source. Fix: on a successful copy-fallback, `linkat_exit` records `(src_dev, src_ino, dest_ino)` in a small ring (`LINKCOPY_CACHE=32`); at stat-family exit (`newfstatat`=79 / `fstat`=80 / `statx`=291), when the result's inode matches a recorded `dest_ino`, z2root fakes `st_dev`/`st_ino` (statx: `stx_ino` + `stx_dev_major/minor`) to the source values so git's verification passes. The entry is evicted on first match to keep the spoof window minimal, and the hot path is skipped entirely while no entries are live. Real `link()` paths never fall back, so `ln`/`npm`/`tar` are unaffected. ⚠️ e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10.

Previously (0.8.57-alpha, versionCode 65): fixes a readlinkat return-value truncation bug, and confirms the 0.8.56 z2root build-parity fixes by on-device e2e. `readlink(2)` on a `.l2s` (or similar) symlink returned a truncated path, e.g. `/root/android-sdk/n` (19B). Root cause: the tracee sizes its buffer from the link's `lstat` `st_size` (which z2root reverse-translates to the shorter *guest* length), but the kernel writes the longer *host* real path into that buffer (truncated), and `host_to_guest()` then shortened it further. Fix: like proot, at syscall exit z2root re-`readlink`s the target symlink's host real path itself into a full buffer, then `host_to_guest()`-converts and writes back clamped to `bufsiz` (the target host path is captured at entry into `pid_state.aux_path`; a `dirfd`-relative path with no determinable host path falls back to the old tracee-buffer read). The linker only `open`s, so this didn't block the 0.8.56 build, but it hardens tools that handle `.l2s` chains via `readlink`. The 0.8.56 fixes below were e2e-verified on the z2root engine (2026-06-09): the `.l2s` chain (NDK `libc++_shared.so`) opens without cp materialization, and aapt2 starts with no `--argv0` error in both `version` and `daemon` modes. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §7/§8.

Previously (0.8.56-alpha, versionCode 64): fixes two parity gaps that blocked `assembleFullRelease` on-device (z2term building itself). (1) Legacy `--link2symlink` (`.l2s`) chains couldn't be followed on open: the NDK `libc++_shared.so` had been turned into a multi-level symlink by proot/old-z2root link2symlink, so CMake's native link failed with `ld.lld: unable to find library -lc++_shared`. Root cause: `canonicalize_guest()` always walked a `readlink` target as a *guest* path, but link2symlink stores it as a *host* real path, so it got the rootfs prefix double-applied → `ENOENT`. Fix: reverse-translate an absolute link target via `host_to_guest()` before continuing (ordinary absolute symlinks unaffected). (2) The Android-native aapt2 couldn't start: the AAPT2 daemon failed with `error: expected absolute path: "--argv0"`. Root cause: aapt2 is an Android aarch64 ELF (interp `/system/bin/linker64`), and z2root launches a dynamic ELF as `<interp> --argv0 <name> <prog> <args>`, but this device's bionic linker64 — unlike glibc/musl ld.so — does not understand `--argv0` and passes it straight to argv, so aapt2 mistook it for a path. Fix: in `plan_exec()`'s dynamic-ELF path, skip `--argv0`+argv0 only when the interp basename is `linker64`/`linker` (bionic; Android tools don't read argv0). Both verified on-device (see 0.8.57 above).

Previously (0.8.55-alpha, versionCode 63): makes the `libz2accept.so` LD_PRELOAD shim bionic-safe so a full release APK can be built on-device (z2term building itself). An on-device build injects `LD_PRELOAD=libz2accept.so` so the JVM's seccomp-blocked `accept(2)` (202) gets through, but the shim referenced `__errno_location` (musl/glibc's errno cell) as a non-weak undefined symbol. When that `LD_PRELOAD` leaked into the bionic `aapt2` that the Android Gradle plugin spawns, aapt2 failed to start (`cannot locate symbol __errno_location`, since bionic uses `__errno()`), stopping the build at `processFullReleaseResources`. Fix: declare `__errno_location` `__attribute__((weak))` with a NULL guard, so it loads even when unresolved (resolves to 0 = harmless under bionic, still sets errno under musl/glibc). Verified under the proot engine: `./gradlew :app:assembleFullRelease` reaches `BUILD SUCCESSFUL` and the resulting release-signed APK bundles the fresh shim (WEAK `__errno_location`) and the case-3 `libz2root.so` (verified via unzip + readelf). The on-device full self-host build (native engine + APK assembly) now works end to end.

Previously (0.8.54-alpha, versionCode 62): fixes a static ELF inside a bind mount failing to exec on-device, and makes `scripts/build-z2root.sh` self-host on-device (z2term building its own native engine). Two coupled parts. (A) When launching a static ELF via `--loader`, z2root passed the loader the program's *host* path, but the loader's own `open()` is also traced and translated, so a binary under a bind (`-b <home>:/root`, e.g. the NDK static clang) was treated as a guest path, got the rootfs prefix, and `ENOENT`'d (`z2root loader: open(…/clang-21): No such file`) — the static analog of the dynamic-ELF hole fixed in 0.8.37. Fix: pass the loader the *guest* path (reverse-translated via `host_to_guest`), just as the dynamic path passes `ld.so` its `guest_real`, so static binaries map for both rootfs and bind locations. (B) The NDK clang is itself a static ELF, so under the currently-installed engine (before an APK with this fix lands) it can't be exec'd; `build-z2root.sh` now falls back to an exec'able dynamic rootfs clang as the cross compiler (`--target=aarch64-linux-android29 --sysroot=<NDK sysroot>`) and links the NDK static libs/crt by hand with GNU ld. PC builds pass the `clang --version` probe and use the NDK toolchain unchanged. Verified on a z2root term: `bash scripts/build-z2root.sh` produces `libz2root.so` (static EXEC AArch64, NDK r29, no deps) and `libz2accept.so`. A/B are tightly coupled (without A a self-hosted z2root can't exec static binaries; without B you can't build the A-bearing `.so` on-device).

Previously (0.8.53-alpha, versionCode 61): fixes GUI audio being silent under the z2root engine (it already worked under PRoot). Two root causes. (1) PulseAudio's `--daemonize` detaches by re-`execve`ing `/proc/self/exe`, which under z2root resolves to the launcher (`libz2root.so`) and fails with "cannot self execute", so the daemon never comes up. The GUI start script now launches it foreground-style with `setsid pulseaudio -n --exit-idle-time=-1 … &` (no `--daemonize`, backgrounded via `setsid`+`&`), and stops it with `pactl exit`. (2) The PulseAudio client's `AF_UNIX` handshake sends its credentials with `SCM_CREDENTIALS`, but the kernel only accepts a declared `uid` equal to the real/effective/saved uid (or with `CAP_SETUID`); z2root's fake-root reports uid 0 while the unprivileged app's real uid is non-zero, so `sendmsg(2)` returns `EPERM` and the client dies with "Connection died". z2root now traces `sendmsg(211)`/`recvmsg(212)` under fake-root and rewrites the `SCM_CREDENTIALS` ucred — outbound to the process's real uid/gid, inbound back to 0 — so the kernel accepts the message while the rootfs still sees root. `SCM_RIGHTS`/memfd passing is untouched (it already worked). Verify: z2root + GUI now plays audio, `/tmp/z2gui-audio-<display>.log` shows no "Connection died", and `pactl info` lists `z2sink`.

Previously (0.8.52-alpha, versionCode 60): fixes Claude Code (and any Bun/Node tool) failing to start under the z2root engine, and keeps the tab long-press popup on-screen. z2root now reverse-translates the result of `readlinkat` on the magic symlinks `/proc/self/cwd`, `/proc/<pid>/cwd`, `/proc/self/exe` and `root`. `getcwd(2)` was already reverse-translated, but Claude Code's Bun runtime resolves its working directory via `readlink(/proc/self/cwd)`, which returned the untranslated host path (`/data/data/.../files/shared_home`); Claude then judged that directory "does not exist" inside the rootfs and exited immediately with no logo or prompt — exactly the "claude won't launch" symptom. The same `host_to_guest()` mapping used for `getcwd` now applies to readlink results (proot-equivalent behavior). UI: the tab long-press info popup is now clamped fully within the window via a custom `PopupPositionProvider`, so it no longer runs off-screen for tabs near the edges.

Previously (0.8.51-alpha, versionCode 59): guards against shipping a broken APK when git-ignored bundled assets go missing, plus a tab-info popup. The programming fonts (`assets/fonts/*.ttf`), PRoot binaries and the Alpine rootfs are all git-ignored (fetched/built by `scripts/`), so a fresh clone or a `clean` can silently wipe them and the build would still succeed — producing an APK with no fonts (the UI quietly falls back to system monospace) or no rootfs. New Gradle checks `verifyBundledFonts` (all flavors) and `verifyFullBundledArtifacts` (`full` only) run before the asset/jniLibs merge and fail the build with the exact regeneration command if anything is missing (escape hatch: `-PallowMissingBundledAssets=true` downgrades to a warning). `scripts/fetch-fonts.sh` now pulls JetBrains Mono / Fira Code from their release zips (the per-weight TTF is gone from `master`). UI: long-pressing a tab now shows a popup with the tab name and the engine it is actually running on (PRoot / z2root / chroot / Android sh, or GUI), so you no longer need to open Settings to check — it coexists with the existing long-press-drag reorder. Also includes a z2root SIGSYS refinement (only privilege-class blocked syscalls fake success `0`; others return `-ENOSYS`).**

Previously (0.8.50-alpha, versionCode 58): changes the `debug` build's `applicationIdSuffix` from `.debug` to `.debug2` (launcher label `Z2Term dbg2`). A stale prior `.debug` install signed with a different debug key kept causing a signature-conflict on install that survived uninstall + reboot (orphaned package/data record), and `pm`/`adb` aren't reachable from the in-app dev environment to force-clear it. Moving to a fresh package id sidesteps the conflict entirely. Release/foss flavors are unaffected. No behavior change versus 0.8.49.**

Previously (0.8.49-alpha, versionCode 57): fixes `claude code` (node) failing to start under the z2root engine. node aborted at launch with `node: src/unix/core.c:646: uv__close: Assertion 'fd > STDERR_FILENO' failed.` + SIGABRT. Root cause: z2root's SIGSYS handler faked *every* seccomp-blocked syscall as a success (return 0) — the fakeroot strategy — and that also applied to `io_uring_setup` (425). libuv read the faked `0` as a valid io_uring ring fd, kept fd 0 as its backend, and later called `uv__close(0)`, which aborts on any fd ≤ STDERR_FILENO. Fix: the SIGSYS handler now returns `-ENOSYS` for the three io_uring syscalls (425/426/427) instead of 0, so libuv sees io_uring as unimplemented and falls back to epoll (proot never had io_uring either, which is why it worked there). All other blocked syscalls still fake success as before. Verified by SSHing into a z2root-hosted sshd (single-ptrace real conditions; nesting under proot masks the bug): node now runs. Note: `git clone` using hard links is a separate, still-open issue — use `git clone --no-hardlinks` for now.

Previously (0.8.48-alpha, versionCode 56): structurally prevents the "stale `libz2root.so`" class of bug. The z2root/z2accept `.so` files are build artifacts (git-ignored) and were never regenerated by `git pull` or CMake, so an outdated `.so` kept getting bundled into the APK even after `z2root.c` was fixed in git — exactly what made the previous git/npm breakage so hard to track down. A new Gradle task `buildZ2rootNative` now runs `scripts/build-z2root.sh` automatically before the `full` flavor's jniLibs merge, so `./gradlew assembleFull*` always regenerates the `.so` from the current source (zero manual steps). `build-z2root.sh` self-resolves the NDK (env vars / `local.properties` `sdk.dir`+`ndk.version` / `$ANDROID_HOME`). The `foss` flavor is excluded by design (it downloads the distro at runtime). No behavior change to z2root itself versus 0.8.47.

Previously (0.8.47-alpha, versionCode 55): fixes git, npm, and copy commands breaking under the z2root engine. The `--link2symlink` hard-link emulation used to turn `link(old,new)` into "`new` is a symlink to `old`", which broke git's loose-object finalization (write `tmp` → `link(tmp,final)` → `unlink(tmp)`): `final` ended up a dangling symlink to the just-deleted `tmp`, so commits failed with `fatal: … is not a valid object`. Because npm's global install expands packages via hard links from its cache, this is also the likely reason `claude code` showed no logo / no response (its bundled JS became broken links). Now z2root tries a real hard link first and only falls back to copying `old` → `new` (in the tracer) when Android's app-internal filesystem rejects `link()` (`EACCES`/`EPERM`/`EXDEV`/…), so `new` survives a later `unlink(old)`. Verified on-device: `ln orig hard; rm orig; cat hard` keeps the data, and a full `git init`→`add`→`commit`→`log`→`cat-file` cycle succeeds. ⚠️ Packages already `npm install`ed under the old z2root must be reinstalled (their files are dangling symlinks).

Previously (0.8.44-alpha, versionCode 52): Settings now shows the engine each tab is *actually* running on — a read-only "This tab is running on: PRoot / z2root / chroot / Android sh" row sourced from the real launch result (`TerminalSession.actualEngine` via `ProotLauncher.resolveLaunchEngine()`), not the selector chip, so it stays honest on fallback. The 7-tap engine-selector toggle gained a 3-second cooldown so rapid tapping can't immediately flip it back.

Previously (0.8.43-alpha, versionCode 51): z2root `/proc/self` / `/proc/thread-self` *mid-path* mis-resolution fix. 0.8.41 fixed only a *leading* `/proc/self…` (rewritten to `/proc/<tracee-pid>` in `host_path_for()`), but an *indirect* symlink still slipped through: when a guest opens `/proc/net/tcp`, the kernel symlink `/proc/net` → `self/net` makes `canonicalize_guest()` walk a `self` component mid-path, and z2root `readlink`ed it as the *tracer* (z2root's parent), resolving to `/proc/<wrong-host-pid>/net/tcp` → `EACCES`. Fix: `canonicalize_guest()` now resolves a `self` / `thread-self` component encountered directly under `/proc` to the tracee pid (instead of `readlink`ing the magic symlink), matching the leading-path rewrite. Verified in the dev environment: a direct `/proc/self/net/dev` read and the indirect `/proc/net/dev` read now resolve identically (the residual `EACCES` there is the outer sandbox restricting per-pid `net/*`, not present on a real device); `id`=root and `/proc/self/comm` resolution are unaffected. Found while dynamically tracing the still-open SSH-reset investigation. SSH-reset status (sharpened, **still needs a real device**): the dev-environment failure is a channel-EOF → dropbear closes the PTY master → kernel `SIGHUP` artifact triggered by `</dev/null` (closing stdin); with stdin held open the login shell starts and prints the MOTD, so the PTY path is largely functional. A real interactive `ssh` (terminal stdin, no EOF) shouldn't hit this path, so the device-side interactive failure is a different cause that the dev environment (no mount privileges, double-ptrace) can't reproduce — the next step is an on-device `Z2ROOT_TRACE` capture of an interactive login. The `Z2ROOT_TRACE` instrumentation in `z2root.c` is intentionally kept for that on-device trace.

Previously (0.8.42-alpha, versionCode 50): three fixes. (1) z2root SSH logins (dropbear) no longer reset right after auth — under fakeroot (`-0`), `fchmod(52)` / `fchmodat(53)` `EPERM`→success is faked so dropbear's `chmod(/dev/pts/N, 0620)` on the SSH PTY slave doesn't abort the session. (2) Terminal long-press magnifier is positioned with the 4-arg `Magnifier.show()` at a fixed offset above the finger. (3) Long-press text selection works on a freshly opened tab without first pinch-zooming (the cell-metrics `LaunchedEffect` now keys on `session.id`).

Previously (0.8.41-alpha, versionCode 49): z2root SSH logins (dropbear) no longer hang with no prompt. `ssh -p 2222 root@device` used to freeze: dropbear establishes the SSH PTY via musl `openpty()` → `ttyname()`, which walks `/proc/self/fd/N`, but z2root's `host_path_for()` / `canonicalize_guest()` resolved `/proc/self` and `/proc/thread-self` (magic symlinks) against the *tracer* (z2root's parent) pid instead of the tracee — so `/proc/self/fd` pointed at the tracer's fds, `ttyname()` failed, and dropbear reset the connection with `ttyname fails for openpty device`. Fix: rewrite a leading `/proc/self` / `/proc/thread-self` to `/proc/<tracee-pid>` before canonicalize (matching the read-free path's `resolve_proc_self`; threads and processes share fd/cwd/root tables, so tid works for both).

Previously (0.8.40-alpha, versionCode 48): GUI apps (e.g. mpv) no longer crash under the z2root engine. Playing a video with `mpv` would print an X11 `BadAccess` and segfault: the x11 video output tries MIT-SHM (X shared-memory image transfer, `X_ShmAttach`), but under z2root the SysV shared-memory segment can't be co-attached, so the X server returns `BadAccess` and the resulting async X error crashes mpv. (Under proot this never surfaced — `shmget` itself fails there and the client auto-falls back to non-SHM rendering.) Fix: start `Xvnc` with `-extension MIT-SHM`, disabling the shared-memory extension entirely so every client reliably falls back to plain `XPutImage`. VNC is a local connection where shared memory buys almost nothing, and the change is harmless for the proot engine too. The `z2gui` launcher is rewritten into the rootfs on every launch, so existing distros pick it up on the next GUI start. (Audio remains the opt-in PulseAudio→TCP bridge; the `[ao/*]` errors when it's off are non-fatal — the video plays silently.)

Previously (0.8.39-alpha, versionCode 47): the Linux GUI (`z2gui`: Xvnc + openbox + a terminal) actually displays under the z2root engine, end to end. After 0.8.38 let the X server start, the VNC screen still stayed black ("Connection reset"): Alpine's `Xvnc` is musl-built and calls `accept(2)` as syscall 202, but Android's untrusted_app seccomp forbids `accept` (bionic only ever uses `accept4`, so 202 isn't allowlisted) — every VNC connection hit a `SIGSYS`. Fix: a tiny libc-agnostic `LD_PRELOAD` shim (`libz2accept.so`) that rewrites `accept()` → `accept4(...,0)`, preloaded into every guest process. Verified on-device (untrusted_app): the full RFB handshake completes and the openbox desktop with an xterm renders. This also unblocks z2root SSH servers (dropbear) that `accept`.

Previously (0.8.38-alpha, versionCode 46): the Linux GUI's VNC server (`Xvnc`) can start under the z2root engine. Picking z2root and starting the GUI used to fail — the VNC server wouldn't come up — because z2root didn't translate the paths in `bind()`/`connect()` for AF_UNIX sockets. The X server creates its display socket at `/tmp/.X11-unix/X1`, but z2root passed that path through untranslated, so the kernel tried to create it at the *host's* real `/tmp` (which doesn't exist for an app) and it `ENOENT`'d; the same gap broke dbus / pulseaudio unix sockets. (PRoot translates socket addresses, which is why the GUI worked there.) Fix: z2root now traces `bind`/`connect` and rewrites the `sun_path` of pathname AF_UNIX sockets to the in-rootfs host path (abstract sockets — `sun_path[0]=='\0'` — are left alone, since they're namespace names, not files, and already work over the shared loopback). Verified on-device: the unix socket appears inside the rootfs (not on the host `/tmp`), Xvnc starts, and openbox/xterm connect.

Previously (0.8.37-alpha, versionCode 45): z2root can now run a binary that lives in a bind-mounted directory (e.g. your home `/root`), so the natural `gcc hello.c -o hello && ./hello` workflow works under the hidden engine. Previously, running a freshly compiled (or any) executable from `/root` failed with `error while loading shared libraries: … cannot open shared object file` (and static binaries with `z2root loader: open(…): No such file or directory`). Root cause: for a dynamic ELF, z2root handed the *host* path of the program to the in-rootfs `ld.so`, but `ld.so`'s own `open()` is traced and gets path-translated — and a host path under a bind (`-b <home>:/root`) is treated as a guest path, so the rootfs prefix is prepended and it `ENOENT`s. (Binaries inside the rootfs proper happened to work because their host path is already under the rootfs and skips double-translation.) Fix: pass `ld.so` the *guest* path of the program (reverse-translated via `host_to_guest`), matching what the shebang path already did, so it resolves correctly for both rootfs and bind-mounted locations. Verified on-device: `cd /root && gcc -O2 hello.c -o hello && ./hello` prints its output (`sum(1..100)=5050`), with no regression on in-rootfs binaries. (Static binaries still segfault under the self-loader — a separate, known limitation.)

Previously (0.8.36-alpha, versionCode 44): z2root now works with glibc distros (Arch / Ubuntu) under the hidden engine. Picking z2root + Arch Linux used to just show a blank screen with no prompt ("frozen"): the shell was actually running, but had started in *non-interactive* mode (no `PS1`), so nothing was drawn. Root cause: modern glibc (2.42+) implements `tcgetattr` via the `TCGETS2` terminal ioctl, which Android denies (`EACCES`) for an app's pty — so `isatty()` failed and bash/zsh decided "this isn't a terminal" and skipped the interactive prompt. (Alpine wasn't affected because musl uses the older `TCGETS`; PRoot wasn't affected because it rewrites the ioctl.) Fix: z2root now traces `ioctl` and rewrites the `TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` requests to their legacy `TCGETS`/`TCSETS`/… equivalents at syscall entry (the leading `struct termios` layout is identical, so normal baud rates work unchanged). Verified on-device: Arch + z2root reaches an interactive `[…]$` prompt and runs commands; Alpine (musl) unaffected.

Previously (0.8.35-alpha, versionCode 43): z2root's "read-free" `/proc` faking is now on by default. The remaining slow path after the seccomp work was that `read` (and `close`) still had to be traced just so `/proc/<pid>/status` and `loginuid` reads could be rewritten on the fly — so a tight loop of tiny reads (e.g. `dd bs=1`) was still ~9x slower than PRoot. Now the faking is done once, at `openat` time: the faked content is written to a throwaway file inside the rootfs and the `openat` path is swapped to it (the temp is unlinked right after, classic open-then-unlink), so subsequent reads hit a plain file and `read`/`close` are dropped from the seccomp trace set entirely (native speed). Verified on-device: `dd bs=1 count=300000` dropped from ~8.1s to ~0.28s, edging out PRoot (~0.32s), with `/proc/self/status`/`loginuid` faking intact and no temp files left behind. Introduced opt-in in 0.8.34; set `Z2ROOT_NO_READFREE=1` to fall back to the old read-tracing path.

Previously (0.8.33-alpha, versionCode 41): the hidden execution-engine selector is now a two-way toggle. Tapping the version 7 times used to only *unlock* it, with no way back; now 7 more taps while unlocked hides it again and resets the engine to the default PRoot, returning to the pre-unlock state. The version row stays tappable in both states, with a tap countdown.

Previously (0.8.32-alpha, versionCode 40): z2root is now much faster, via a seccomp-bpf filter. It used to trap *every* syscall twice (`PTRACE_SYSCALL` entry+exit), so fork/exec-heavy and read/write-heavy work (interactive shells, pipes, `apt`) was 20–25x slower than PRoot. Now a seccomp-bpf filter traces only the syscalls that need path translation / fakeroot faking / getcwd reverse / `/proc` faking (`SECCOMP_RET_TRACE`); everything else runs natively in the kernel (same trick PRoot uses). On-device benchmarks (vs PRoot): fork/exec ~2.3x faster than the old z2root, reads ~3x faster, real-world IO within ~2x of PRoot, and filesystem walks (`find`) faster than PRoot. Verified end to end in the real app (interactive shell, `id`=root, `apt install` completes, status/cap/loginuid faking intact). Falls back to the old all-syscall tracing if the filter can't be installed (or with `Z2ROOT_NO_SECCOMP=1`).**

Previously (0.8.31-alpha, versionCode 39): z2root `/proc/<pid>/status` faking now also covers the capability lines and `loginuid`, so the root illusion is internally consistent. `cat /proc/self/status` used to show `Uid: 0` but `CapEff: 0000000000000000` (no capabilities), an obvious tell that the process is not really root. Now `CapPrm` / `CapEff` / `CapBnd` are rewritten to the full capability set (`000001ffffffffff`) — length-preserving — while `CapInh` / `CapAmb` stay `0` as a real root login shell shows. The separate `/proc/<pid>/loginuid` file (which can leak the host audit login uid) is also faked to `0` by zero-filling its digits (leading zeros parse to `0`). The per-pid proc-fd tracking was generalized to carry a kind (status vs loginuid) so the right rewrite is applied on `read()`.

Previously (0.8.30-alpha, versionCode 38): z2root now survives a real `apt install`. Installing `hello` on Ubuntu 24.04 under the z2root engine (no root, `untrusted_app`) succeeds end to end (`Unpacking` → `Setting up` → the binary runs and prints `Hello, world!`), verified on-device. Two fixes: (1) `execvp` passthrough — `dpkg` runs `execvp`, which `execve`s each PATH candidate in turn and moves on at `ENOENT`; z2root used to wrap even non-existent candidates (e.g. `/usr/local/sbin/locale`) with the loader, so the loader `_exit(127)`'d on open failure and `execvp` couldn't try the next candidate (the `dpkg-split exit 127` failure). Now non-ELF / unopenable targets skip the loader and let the kernel return `ENOENT`/`ENOEXEC` (path register still host-translated). (2) `utimensat(88)` path translation — `dpkg` sets the file mtime via `utimensat`, which wasn't in the path-translation table, so it hit the host's non-existent `/usr/bin/hello` and returned `ENOENT` (`error setting timestamps`). Added it with `fchmodat`-style flag handling.

Previously (0.8.29-alpha, versionCode 37): keyboard & toolbar polish. (1) Japanese conversion no longer over-segments common phrases: typing `おねがいします` used to auto-block at `おねが` (a wrong 1-best Viterbi split) and hide the right answer; the auto-split now follows the exact N-best lattice path, so `お願いします` is the top candidate as expected. (2) The built-in Japanese keyboard now has cursor `▼` (left, down) / `▲` (right, up) keys directly under `◀` / `▶`, half a row each so all four arrows are the same size; the `変換` / space keys stay full-height and easy to press. (3) The settings page now returns when you tap anywhere on its top bar (not just the back arrow). (4) The background-keep-alive toggle moved from Settings to a 🔒 lock button on the toolbar. (5) Toolbar buttons are reorderable by long-press drag, with a short description popup while held; the new keep-alive lock defaults to the right of the screen-on lock.

Previously (0.8.28-alpha, versionCode 36): z2root added `/proc/<pid>/status` faking for `fakeroot -0` — intercepting `read()` on the status files and rewriting `Uid:` / `Gid:` to `0` and blanking `Groups:`, length-preserving, consistent with the existing `getuid`/`getgid`/`getgroups` fakery (verified on-device).

Previously (0.8.27-alpha, versionCode 35): z2root fixed `pwd` / relative `ls .` leaking the host cwd path inside the real app — an `untrusted_app`'s mount namespace resolves `/data/user/0/<pkg>` to `/data/data/<pkg>`, so after `chdir` the kernel's `getcwd` returns the `/data/data` canonical form while the bind host (from `context.filesDir`) was the `/data/user/0` form, and the reverse-translation lookup missed. Fix: `realpath()` the rootfs and every bind host at startup so they match what `/proc/<pid>/cwd` / `getcwd` report.

Previously (0.8.26-alpha, versionCode 34): z2root first booted an interactive Alpine shell inside the real app (non-root, `untrusted_app`) via the `nativeLibraryDir`-resident in-house ELF loader — `-static` build (not `-static-pie`), eating the initial `SIGSTOP` of `TRACEFORK`-born children, suppressing seccomp `SIGSYS`, and honoring job-control group-stops. The loader maps the rootfs `ld-musl` into anonymous executable memory and jumps to it, so it never `execve`s files in the app's own data dir (which `untrusted_app` forbids under W^X / SELinux).

Milestones 7–12 implemented SFTP, GUI (Xvnc+VNC), multiple GUI tabs, IME learning, English UI, landscape keyboard, scrollback search, session restore, a chroot engine for rooted devices, and GUI audio/video playback. 0.8.4 strengthened Japanese kana-kanji conversion for long sentences. Since 0.8.5, small UI/keyboard improvements and various fixes have continued.

### Added/changed in 0.8.5–0.8.17

- **Auto-fetch the GUI stack when missing** (0.8.17): fixed the 🖥 launch failing every time when the GUI terminal (or Xvnc/openbox) wasn't installed. If installed, it still launches instantly with no network; only when something is missing does it fetch the missing pieces (after the consent dialog when download confirmation is ON).
- **META key at the left of the English keyboard** (0.8.17): filled the gap that was left to the left of `a` in the English UI's custom keyboard (4-direction flick) with a META key (= the same ESC-prefix modifier as Alt). The Japanese keyboard and the simple layout are unchanged.
- **More reliable long-press selection** (0.8.16): turned OFF `ScaleGestureDetector`'s quick scale (single-finger double-tap + drag to zoom). When enabled, long-press fired intermittently (recovering only after a pinch). Two-finger pinch still works.
- **Batch prediction follows the block boundary** (0.8.16): when you move the leading block's boundary with ◀ ▶ during split conversion, the candidate bar's light-green "whole-sentence" pill (batch prediction) is rebuilt as "leading block's top candidate + best of the remaining kana" and re-flows.
- **Unified Japanese keyboard font** (0.8.15): whichever you pick — "Simple" or "4-direction flick" — kana characters are sized consistently to the 4-direction flick baseline (height-based scaling is kept).
- **Drag to reorder tabs** (0.8.14): long-press a tab → drag left/right to reorder CUI/GUI tabs. Movable edge to edge in one gesture. Close a tab by double-tapping (teardown runs in the background so it disappears instantly).
- **Settings as a full-screen page** (0.8.14): replaced the old bottom sheet stacking from below with a "separate page" (back arrow + system-back support).
- **Removed automatic `cd` injection at launch** (0.8.13): to avoid unintended working-directory moves on session restore, restored tabs also launch at the shell's default cwd.
- **Toolbar snippets moved to the 📜 tab** (0.8.12): merged snippets and **SSH connect / SFTP** into one sheet via tabs; removed the SSH profile item from settings.
- **Fixed SSH ed25519 public-key auth** (0.8.11): resolved `Auth fail publickey` on Android by adding BouncyCastle.
- **Larger main keys + tidied style names** (0.8.7–0.8.10): bigger fonts for qwerty/number keys, special-key notation `C-C`→`^C`, style names changed to "Simple / 4-direction flick".
- **CI fix** (0.8.6): removed the absolute `java.home` from `gradle.properties` + fixed lint errors.
- **Keyboard/candidate bar improvements + IME enhancements** (0.8.5): 2-row candidate bar, settings scroll fix, N-best multiple candidates, common-word additions.

### Added in 0.8.4 (M13)

- **Automatic block splitting for long Japanese sentences**: typing a long sentence predicts from the leading chunk without pressing the convert key. Each block is confirmed and it auto-advances to the next.
- **Whole-sentence batch prediction**: presents a "whole-sentence" candidate (each block converted and concatenated) as a light-green pill in the candidate bar; tap to confirm at once (unused bunsetsu-rearrangement candidates were removed).
- **External storage (SD card) recognition** (opt-in): binds `/storage/XXXX-XXXX` into proot/chroot.
- **Android host bind (experimental)**: binds `/system` `/apex` to open up things like on-device builds.
- **Stopped highlighting the ◀▶ / convert key backgrounds while composing** (quieter display).

### Added in M11–M12

- **Scrollback search** (`SearchEngine.kt`): 🔍 → type → ↑↓ to jump back/forward; highlight position computed in CJK cell columns.
- **Session restore** (`SessionStore.kt`): restores tab layout + cwd after an OS kill and restart (cwd captured via OSC7).
- **Android API bridge**: from the terminal, `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip` / `z2-battery` / `z2-vibrate`.
- **root chroot hidden feature** (full flavor, requires root): tap the version 7 times to unlock the "execution engine (proot / chroot)". Verified on a real rooted device.
- **GUI video/audio**: mpv plays correctly with software rendering; a **GUI audio bridge** (PulseAudio→TCP→AudioTrack, opt-in).
- **Underline on URL/OSC8 links**, fixed detection of wrapped long URLs.
- **Three-finger scroll**: removed on-screen scroll buttons in favor of three-finger drag.
- **Japanese IME enhancements**: ⌫ left/right flick for word/whole delete, flipped flick labels, a conjugation dictionary, removed kana repeat-cycling.
- **OSC title UTF-8 decode** to fix mojibake in Japanese tab names.

### Added in M10

- **Konsole-on-Arch launch fix** (4-stage fallback → reconstructs from local cache only)
- **Landscape keyboard position** selectable from left / bottom / right, with **width/height sliders** too
- **Background highlight on press + enlarged flick-direction hints** so you can see "what you pressed / where it'll go"
- **Fully offline normal 🖥 launch**: doesn't hit the network except for clean install
- **In-app language switch** (Japanese / English) + many UI translations
- **Disable install timeout** toggle
- **IME learning history** ranks prediction by frequency and recency (last 7 days)

### Main additions in M7–M9

- **SFTP file transfer** (M7)
- **GUI (Xvnc + built-in RFB client) + Linux desktop launch** (M8)
- **Run multiple GUIs in parallel tabs** + IME linkage + pairing terminal tabs with GUI tabs (M8-4–6)
- **In-app theme editor** + **OSC 4/10/11/12** support (M9)
- **Okurigana conjugation** + **flexible kanji conversion** (M9)

### Features in M6 and earlier

- **SSH public-key auth** + encrypting secret fields with the Android Keystore (AES-256/GCM) (M6)
- **known_hosts persistence**: fingerprint confirmation dialog on first connect, MITM detection (M6)
- **OSC 7 (cwd)** / **OSC 8 (hyperlinks)** (M6)
- **FOSS build flavor**: license-notice minimized (Alpine rootfs excluded → runtime download), SHA-256 verification in `DistroDownloader` (M6)

- SSH basics / pinch / OSC 4/10/11/12/52 / EAW Ambiguous / distribution pipeline (M5)
- East Asian Width / multi-tab / IME linkage / custom fonts / WakeLock (M4)
- Alternate screen / foreground service / physical keyboard / range selection /
  multi-distro (M3)
- VT100/xterm emulator / 6 themes / scrollback / UTF-8 (M2)
- PRoot + Alpine PoC (M1)

### Not yet supported / under consideration

- Local port forwarding (-L) / reverse forwarding (-R)
- mosh protocol support (UDP-based)
- Reverse DNS / stronger IPv6 connection retry
- Fully self-contained proot replacement to drop all third-party native notices (FOSS-PURE phase 2)
- IME learning-history reset UI / backup

## Build requirements

| Item | Version |
|---|---|
| Android Studio | Ladybug 2024.3.1 or later |
| AGP | 9.1.1 |
| Kotlin | 2.2.10 (bundled with AGP) |
| Gradle | 9.3.1 |
| NDK | 27.0+ |
| CMake | 3.22.1+ |
| Min SDK | 29 (Android 10) |
| Target SDK | 35 (Android 15) |

## Setup

### 1. Place the dependency binaries

Before building, you must place the following manually (not included in the repository):

**Alpine rootfs** → `app/src/main/assets/`
- `alpine-minirootfs-aarch64.tar.gz`
- `alpine-minirootfs-armv7.tar.gz`

Details: [app/src/main/assets/README.md](app/src/main/assets/README.md)

**PRoot binaries** → `app/src/main/jniLibs/`
- `arm64-v8a/libproot.so` (and `libproot_loader.so`)
- `armeabi-v7a/libproot.so` (and `libproot_loader.so`)

Details: [app/src/main/jniLibs/README.md](app/src/main/jniLibs/README.md)

### 2. Build

```bash
# Debug APK
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### 3. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
z2term/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                  ← place the Alpine rootfs here
│       ├── cpp/                     ← JNI native code
│       │   ├── CMakeLists.txt
│       │   └── pty_jni.cpp
│       ├── java/com/zerotoship/z2term/
│       │   ├── Z2TermApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── channel/             ← ProcessChannel / SshChannel (M5)
│       │   ├── core/                ← TerminalSession + SessionManager
│       │   ├── pty/                 ← PTY abstraction
│       │   ├── proot/               ← PRoot launch
│       │   ├── distro/              ← rootfs deployment (Alpine + Ubuntu)
│       │   ├── emulator/            ← VT100/xterm emulator core
│       │   ├── settings/            ← DataStore persistence
│       │   ├── service/             ← TerminalService / AudioBridge (foreground + WakeLock)
│       │   ├── gui/                  ← GUI (Xvnc + built-in RFB client / GuiSession)
│       │   ├── saf/                  ← SAF DocumentsProvider
│       │   └── ui/
│       │       ├── theme/           ← ZTS Theme + custom fonts
│       │       ├── settings/        ← settings UI
│       │       ├── ssh/             ← SSH profile UI (M5)
│       │       └── terminal/        ← terminal UI + Renderer + key mapper
│       ├── jniLibs/                 ← place the proot binaries here
│       └── res/                     ← resources
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── ja/                        ← Japanese documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification (technical)
│   │   └── HANDBOOK.md            ← user handbook
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   └── HANDBOOK.md            ← getting started handbook
│   ├── images/                    ← screenshots etc. (shared)
│   ├── RELEASE.md                 ← release steps
│   ├── SSH-INTO-Z2TERM.md
│   ├── GUI-REWRITE-HANDOFF.md
│   └── M1-HANDOFF.md … M13-HANDOFF.md  ← milestone handoffs
├── metadata/                     ← F-Droid metadata
└── .github/workflows/build.yml   ← CI (builds both full + foss)
```

## Build variants

| Flavor | Purpose | Bundled content |
|---|---|---|
| `full` | Internal / Play Store distribution | includes assets and prebuilt binaries (you must place them); offline first run |
| `foss` | License-notice minimized | **Alpine rootfs excluded** → downloaded at runtime by `DistroDownloader` (SHA-256 verified). proot/talloc stay bundled (W^X requires execve from `nativeLibraryDir`), so their GPL-2.0/LGPL-3.0 notices remain. No offline first run. |

```bash
./gradlew assembleFullDebug   # normal development
./gradlew assembleFossDebug   # license-minimized flavor (rootfs excluded, runtime DL)
```

## Smoke-test flow

1. Build & install with neither the proot binaries nor the Alpine rootfs
   → it should fall back to Android `/system/bin/sh`
   → try `ls /system/bin` etc. to confirm it works

2. Place the Alpine rootfs in assets, then build & install
   → it should fall back with a "PRoot binary not found" warning

3. Place the proot binaries in jniLibs too, then build & install
   → Alpine Linux should start
   → try `apk update && apk add zsh`

## License

The license of the app itself (`app/src/main/java/com/zerotoship/z2term/**`) is **GPL-3.0**.
Copyright (c) 2026 Zero to Ship. Corresponding source (GPL v3 §6): <https://github.com/orgsonai/z2term> (full text in the root `LICENSE`).
For the licenses of the bundled binaries, rootfs, fonts, etc., see "Bundled OSS and corresponding source" below.

## Bundled OSS and corresponding source (GPL/LGPL distribution requirements)

The `full` flavor APK includes the following prebuilts. The **corresponding source** for each
is obtainable from the URLs below (for GPL v2 §3 / GPL v3 §6 / LGPL v3 §4).

| Bundled item | License | How to get the corresponding source |
|---|---|---|
| `libproot.so` / `libproot_loader.so` | GPL-2.0 | [termux/proot](https://github.com/termux/proot) / see the Termux package version downloaded by `scripts/build-proot.sh` |
| `libtalloc.so` | LGPL-3.0 | [Samba talloc](https://gitlab.com/samba-team/samba/-/tree/master/lib/talloc) / same as above |
| each package in `alpine-minirootfs-*.tgz` | individual (GPL-2.0 / GPL-3.0 / MIT / BSD, etc.) | [Alpine aports](https://gitlab.alpinelinux.org/alpine/aports) — look up each package name in `scripts/alpine-packages.txt` |
| Fira Code / IBM Plex Mono / JetBrains Mono | OFL-1.1 | [tonsky/FiraCode](https://github.com/tonsky/FiraCode) / [IBM/plex](https://github.com/IBM/plex) / [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |

From the settings screen → "OSS licenses / corresponding source", you can also browse/show this in-app
(license full texts are placed in `assets/licenses/`).

### How to obtain the corresponding source (examples)

```sh
# Get the PRoot-equivalent source (Termux package)
git clone https://github.com/termux/proot.git

# Get the talloc-equivalent source
git clone https://gitlab.com/samba-team/samba.git
ls samba/lib/talloc

# Source of bash etc. in the Alpine rootfs
curl -O https://gitlab.alpinelinux.org/alpine/aports/-/archive/master/aports-master.tar.gz
```

For which versions z2term fetches at build time, see `PROOT_VER_AARCH64` / `ALPINE_VERSION` in
`scripts/build-proot.sh` / `scripts/build-alpine-rootfs.sh`.

## Distribution policy

| Channel | Flavor | Status |
|---|---|---|
| **GitHub Releases / direct APK** | `full` (prebuilts bundled) | Primary channel. The APK works standalone |
| **F-Droid** | `foss` (rootfs excluded) | **Not targeted** (runtime download is allowed). The `foss` flavor exists to minimize third-party license notices, not for F-Droid. proot/talloc remain bundled, so full reproducible-build compliance is out of scope |
| **Google Play** | — | proot's execution of external code likely conflicts with DPA §4.4, so **no distribution planned** |

## Default behavior of the SSH server (sshd)

The in-terminal `sshd` command starts by default with **127.0.0.1-only bind + key auth only**
(dropbear wrapper, `SshdScript.kt`). To expose it on LAN/WAN, explicitly:

```sh
sshd --lan          # bind all NICs; refuses to start if ~/.ssh/authorized_keys is empty
Z2_SSHD_LAN=1 sshd  # also works via env
```

## Related

- [Zero to Ship Project](https://github.com/orgsonai)
- [Termux](https://github.com/termux/termux-app) - reference implementation
- [PRoot](https://proot-me.github.io/) - userland chroot
- [Alpine Linux](https://alpinelinux.org/) - main distro
