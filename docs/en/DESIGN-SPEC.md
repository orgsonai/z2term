# Z2Term — Design & Specification

Last updated: 2026-06-21 / Target version: 0.8.115-alpha (versionCode 123)

> This is the technical document covering Z2Term's **detailed design + specification**, aimed at implementers and reviewers.
> For a friendly user-facing guide, see `docs/en/HANDBOOK.md`.
> For session-to-session progress handoffs, see `docs/M12-HANDOFF.md` (latest) and the various `M*-HANDOFF.md`.
> 日本語版: `docs/ja/DESIGN-SPEC.md`.

---

## Table of contents

1. [Overview](#1-overview)
2. [Tech stack](#2-tech-stack)
3. [Overall architecture](#3-overall-architecture)
4. [Per-layer detailed design](#4-per-layer-detailed-design)
5. [Key data flows](#5-key-data-flows)
6. [Feature specification](#6-feature-specification)
7. [Settings](#7-settings)
8. [Permissions](#8-permissions)
9. [Build / bundled assets](#9-build--bundled-assets)
10. [Known constraints and design pitfalls](#10-known-constraints-and-design-pitfalls)
11. [Glossary](#11-glossary)

---

## 1. Overview

**Z2Term** is a custom-built terminal emulator + Linux runtime that runs standalone on Android.

- **No root required**: using `forkpty(3)` + **PRoot** (userspace chroot/bind emulation), it deploys and runs a Linux distro (Alpine / Ubuntu / Arch / Kali) inside a normal-privilege app.
- **Own terminal emulator**: xterm-compatible VT/ANSI interpretation implemented in Kotlin.
- **Own UI / keyboard**: Jetpack Compose. A custom flick keyboard (Latin + Japanese/katakana) that can switch with the OS IME.
- **Bidirectional SSH**: from the terminal to the outside (JSch client), and from a PC into the terminal (dropbear server).
- **File integration**: SAF DocumentsProvider lets other apps R/W the rootfs/home; from inside proot you can `cd` into Android shared storage.
- **GUI desktop**: inside the distro, Xvnc + a lightweight WM/app launches and is displayed by the built-in RFB (VNC) client (`gui/` package). Video uses software rendering; audio is an opt-in PulseAudio→TCP→AudioTrack bridge (`AudioBridge`).
- **Execution engine**: PRoot by default. A hidden setting (tap the version row 7×) can switch to **"z2root", our own no-root ptrace engine**. On rooted devices, when the root self-test passes, a **"real chroot" engine** also becomes selectable (`su`-based bind mounts + `chroot`; `executionEngine`). The root self-test (`probeRootChroot`) can be re-run **not only at the moment of the 7-tap unlock but also via an "Enable chroot (check root)" button inside the engine selector** (0.8.106). Previously it ran only once at unlock, so denying the su prompt left `rootChrootUnlocked` false and chroot permanently unselectable (re-running required a double 7-tap lock→unlock that users never discovered). While `rootChrootUnlocked` is false, that button and a hint are shown so root can be retried from the button (success unlocks chroot with a toast; a failure is toasted only when triggered from the button). Failures distinguish `RootProbe.NoRoot` (su missing/denied) from `RootProbe.ChrootBlocked(detail)` (root works but the chroot exec failed, e.g. SELinux/rootfs) (0.8.107). **However, root managers like Magisk remember a "Deny" and then return denial immediately without re-prompting, so the in-app button alone cannot recover** (an app cannot change another app's root grant); the NoRoot toast/hint therefore tells the user to set Z2Term back to "Grant" in Magisk (0.8.108). For 3s after the toggle fires, the version row becomes **non-tappable** to prevent rapid re-toggling (0.8.70; previously taps were accepted but ignored, which felt unnatural). **foss bundles no proot prebuilt and always runs z2root**, so the engine selector does not show the PRoot chip (only z2root / chroot when root-unlocked; 0.8.93; previously PRoot was selectable but silently fell back to z2root). The same 7-tap unlock area also hosts a **z2root trace-log ON/OFF toggle** (developer-only, default OFF, `traceLogEnabled`). When ON, every z2root syscall is logged to `shared_home/z2root_trace.log` — useful for diagnostics but the log grows huge and quickly fills device storage, so the UI carries a "keep it OFF in normal use" warning (0.8.105; warning text reworded in 0.8.107 from the self-contradictory "leave OFF and do not use"; previously only the `.z2root_trace_on` sentinel file could toggle it; the sentinel still works for backward compatibility).

Supported ABI is **arm64-v8a only**. Minimum Android 10 (API 29), target API 35.

### Distribution flavors

| Flavor | applicationId | Purpose |
|---|---|---|
| `full` | `com.zerotoship.z2term` | Normal distribution (rootfs/proot bundled; offline first run) |
| `foss` | `com.zerotoship.z2term.foss` | F-Droid compliant. Third-party prebuilts (proot/talloc) and the Alpine rootfs are excluded from the APK; the execution engine is z2root built from bundled source, and the rootfs is downloaded at runtime (no offline first run) |

`debug` builds additionally carry a `.debug` suffix.

---

## 2. Tech stack

| Category | Choice | Version/notes |
|---|---|---|
| Language | Kotlin | 2.2.10 |
| Build | AGP | 9.1.1 (cannot be combined with the `kotlin-android` plugin) |
| UI | Jetpack Compose | BOM 2025.01.00 + Material3 |
| Native | C++ (forkpty JNI) | NDK 28, CMake 3.22.1, `c++_shared`, android-29 |
| Persistence | DataStore Preferences | 1.1.2 (settings / SSH profiles) |
| SSH client | JSch (mwiede fork) | 0.2.26 (+ BouncyCastle 1.84 enables ed25519/curve25519) |
| Decompression | org.tukaani:xz | 1.10 (the downloaded distro's `.tar.xz`). gzip is JDK standard |
| Linux runtime | PRoot + libtalloc + libandroid-shmem | `.so` bundled in jniLibs (from a Termux build) |
| Bundled OS | Alpine Linux ARM minirootfs | full bundles `.tgz` under `src/full/assets`. foss excludes it and downloads from the official CDN at runtime |

---

## 3. Overall architecture

```
┌───────────────────────────── UI layer (Compose) ─────────────────────────┐
│ MainActivity → TerminalScreen                                              │
│  ├ TopBar (📋/📜/💡/🔒keep-alive/🔍/⌨/⚙ reorderable) ├ TabBar ├ Renderer    │
│  ├ TerminalInputView(AndroidView: gestures/IME/selection) ├ ScrollIndic.  │
│  ├ TerminalKeyboard(custom) / JapaneseFlickKeyboard / SpecialKeyBar       │
│  └ SettingsSheet / SshProfilesSheet / SnippetsSheet / HostKeyDialog        │
└───────────────────────────────────────────────────────────────────────────┘
                 │ writeBytes(input)            ▲ emulator buffer(render)
                 ▼                               │
┌──────────────────────────── Domain layer ─────────────────────────────────┐
│ SessionManager ─holds→ TerminalSession[*]                                  │
│   TerminalSession: state machine / readLoop / resize / selection / cwd / label │
│     ├ emulator: TerminalEmulator (VT interpretation, dedicated 1 thread)   │
│     └ channel: ProcessChannel = LocalPtyChannel | SshChannel              │
└───────────────────────────────────────────────────────────────────────────┘
                 │                                       │
                 ▼ (local)                               ▼ (remote)
┌──────── Execution base ──────┐                  ┌──────── SSH ────────┐
│ ProotLauncher                │                  │ SshChannel (JSch)    │
│  → PtyProcess (forkpty)      │                  │  shell + -L forward  │
│    → proot → distro shell    │                  └──────────────────────┘
└──────────────────────────────┘
        │ deploy/update
        ▼
┌──────── distro / persistence ─┐  ┌─ Service ─┐  ┌─ SAF ─┐  ┌─ Settings ─┐
│ DistroBundle/Spec/Installer/  │  │ Terminal  │  │ Docs  │  │ AppSet     │
│ Downloader (assets / DL)      │  │ Service   │  │Provider│ │ tings      │
└───────────────────────────────┘  └───────────┘  └────────┘ └────────────┘
```

**Lifecycle design points**:
- `TerminalSession` lives **independently of the UI** (held by `SessionManager`). PTY/emulator state survives Activity destruction.
- `TerminalService` (foreground service) handles keep-alive, maintaining the PTY in the background. `AudioBridge` (GUI audio) is handled in the same service family.
- emulator state updates are concentrated on a **dedicated single thread** (`z2term-emu-*`); Compose reads via `StateFlow`.
- The **GUI desktop** launches as a separate Activity (`GuiActivity`) and connects to the in-distro Xvnc with the built-in RFB client ([§4.12](#412-gui-desktop-gui)). The execution engine defaults to PRoot, with z2root (no-root) and chroot (rooted devices) selectable via a hidden setting ([§4.3](#43-proot-execution-prootprootlauncherkt-prootsshdscriptkt)).

---

## 4. Per-layer detailed design

### 4.1 Native (`cpp/pty_jni.cpp`, `libz2term`)

- Creates a pseudo-terminal (PTY) with `forkpty(3)` and `execve()`s in the child. Present in Bionic libc since API 21+.
- JNI surface: `nativeCreate(command, args, env, cwd, rows, cols) → (fd<<32 | pid)`, `nativeResize(fd, rows, cols)` (`TIOCSWINSZ`), signal sending, `waitpid`.
- In the child, the controlling terminal is established with `setsid` / `TIOCSCTTY`.

### 4.2 PTY wrapper (`pty/PtyProcess.kt`)

- Builds a `FileDescriptor` from `nativeCreate`'s return value and exposes `reader` (FileInputStream) / `writer` (FileOutputStream).
- `resize(rows,cols)` / `sendSignal` / `close` / `waitFor`.
- **JNI symbol note**: placing `@JvmStatic external` in a companion exports it under the outer class name (CMake/JNI naming convention).

### 4.3 PRoot execution (`proot/ProotLauncher.kt`, `proot/SshdScript.kt`)

- The binary is `nativeLibraryDir/libproot.so` (+ `libproot_loader.so`). `libtalloc.so` is extracted as `libtalloc.so.2` per its SONAME and put on `LD_LIBRARY_PATH`. Newer Termux proot also links `libandroid-shmem.so` (SysV shared memory), so it is extracted into the same `proot-libs` and put on the path too (without it proot dies immediately with `library "libandroid-shmem.so" not found`).
- `launch(distroId, command, rows, cols, fallbackShell)` assembles proot arguments and calls `PtyProcess.create`:
  - `--kill-on-exit -0 --link2symlink -r <rootfs> -b /dev -b /proc -b /sys -b <shared_home>:/root`
  - **External storage bind**: `/storage/emulated/0:/sdcard`, `getExternalFilesDir:/storage/app`
  - `-w /root`, env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 PATH=… TMPDIR=/tmp` + history-related env.
- **Shared home**: `filesDir/shared_home` is bound to `/root` across all distros (← the real backing of the terminal's `~`).
- **Per-distro HOME isolation (0.8.72; `.claude/downloads` added in 0.8.73; z2root longest-match bind fixed in 0.8.75)**: `/root` as a whole stays shared, but **a few arch-dependent subdirectories are overlaid per-distro** (`isolatedHomeSubdirs` = `.local .cache .npm .npm-global .nvm .cargo .rustup .config .claude/downloads`). Each `filesDir/home_overlay/<distroId>/<sub>` is bound over `/root/<sub>` (with `shared_home/<sub>` prepared as the mountpoint; the nested path `.claude/downloads` is created parents-and-all via `mkdir -p`). PRoot adds the per-subdir binds *after* `-b <shared_home>:/root`; chroot does the same after `mount -o bind <SHOME> $RFS/root` (and lazy-umounts them *before* `root` on cleanup). **Why**: native content under HOME (npm-global's node/claude binaries, **Claude Code's own binary at `~/.claude/downloads/claude`**, compiled addons in `~/.cache`, nvm's node itself, …) breaks when mixed across musl (Alpine) ↔ glibc (Arch/Ubuntu/Kali). **Root cause of item 4**: prior versions shared `.claude/downloads`, so Alpine (musl) and Arch (glibc) overwrote the same native binary, leaving both unlaunchable with `Not a valid dynamic program`. 0.8.73 added the overlay bind, but **isolation did not take effect under the z2root engine** and the bug recurred (verified on-device 2026-06-11). The real cause was that z2root's path translation (`translate_abs`/`host_to_guest` in `z2root.c`) resolved binds by **first match in registration order**, so the earlier-registered parent bind `/root` shadowed the child bind `/root/.claude/downloads`. PRoot used longest-match, hence the engine-specific difference. **0.8.75 fixes both translators to longest-match (most specific = longest `guest_len` wins)**, so under z2root too only `.claude/downloads` resolves to the overlay while `.claude/.credentials.json` etc. resolve to the shared HOME. The rest of `~/.claude` (auth `.credentials.json`, settings, projects), documents, git repos, … stay directly under `/root` and remain shared. **Migration note**: existing contents of `shared_home/<sub>` become shadowed by the overlay (not deleted, just hidden) from each distro's view — reinstall `claude` in each distro so its native binary lands in that distro's overlay.
- `resolveShell`: if the specified shell isn't in the rootfs, falls back to `defaultShell → /bin/sh` (usrmerge aware).
- `isDistroReady`: checks the actual presence of `bin/busybox|bin/bash` etc. + a `.z2term-version` marker (compares `ROOTFS_VERSION` for bundled distros only).
- Idempotently injected on every launch: `ensureShellHistoryConfig` (history rc), `ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear wrapper), `ensureOsc7CwdConfig` (OSC7 hook for cwd restore), `ensureZ2ApiScripts` (`z2-*` bridge), `ensureZ2AdbScript` (`/usr/local/bin/z2adb`), `ensureZ2HelpScript` (`/usr/local/bin/z2help` + alias `/usr/local/bin/z2term`), `ensureZ2ScanScript` (`/usr/local/bin/z2scan`), GUI/z2run scripts, `ensureVersionScript` (`/usr/local/bin/z2version`).
- **`z2version` command (0.8.70)**: from the terminal, `z2version` prints the host app version (`versionName`/`versionCode`/flavor/package/execution engine/rootfs generation). It is rewritten on every launch, so it always reflects the *currently running* app — making APK↔guest version mismatches trivial to diagnose. `z2version --short` prints just the version on one line. Installed on all launch paths (proot/z2root/chroot).
- **`z2adb` command (0.8.88, self-adb)**: a helper that connects the device to *its own* adb daemon (Android Wireless debugging) over `localhost`, with no PC, USB, or root (LADB-style). Requires Android 11+ with Settings > Developer options > Wireless debugging enabled. `z2adb setup` installs an adb client into the distro (apk: `android-tools` / apt: `adb` / pacman: `android-tools`, auto-detected via `detect_pm`), `z2adb pair <port> [code]` pairs, `z2adb connect <port>` connects, and afterwards `z2adb shell` / `pm` / `logcat` etc. are passed through. A bare port gets `Z2ADB_HOST` (default `127.0.0.1`) prepended; `host:port` is used as-is. Everything except `setup`/`pair`/`connect`/`status`/`help` is delegated straight to adb; `pair`/`connect`/`status` try a one-shot auto-install if adb is missing. PRoot/z2root pass TCP through (same path as dropbear), so localhost is reachable. Installed on all launch paths (proot/z2root/chroot) ([`Z2AdbScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2AdbScript.kt)). **Pre-starting the adb server (0.8.89)**: when a client runs and no daemon exists, adb normally restarts itself via `execl(own-path)`, but z2root returns `/proc/self/exe` as the in-APK `libz2root.so`, so that fails with ENOENT (an adb-wide issue; root-fixed in 0.8.111 by rewriting `/proc/self/exe` to the guest view, see below). `ensure_adb` therefore calls `start_server`, which **pre-launches `adb nodaemon server` in the background without any self-exec**. Before launching it checks `/proc/net/tcp{,6}` and skips if the target port (`ADB_SERVER_SOCKET`'s port, default `5037`) is already LISTENing (`0A`) — an **idempotent guard** (`server_up`) that avoids the `Address already in use` abort from a double bind. Subsequent clients attach to the existing server without forking.
- **`z2help` / `z2term` command (0.8.90)**: a help command that prints, from the terminal, a quick reference of the custom `z2*` commands injected into the distro. With no arguments it shows a categorized list of every `z2*` command (version/info, phone features, GUI, connecting, help) with a one-line description, prefixed with the app version (`z2version --short`). The body is entirely static text placed in a quoted heredoc (`<<'Z2HELP_EOF'`) so it is not shell-expanded (no external input). `z2term` ships as a thin alias of `z2help` (`exec /usr/local/bin/z2help "$@"`) — a reserved command; to repurpose `z2term` later, just swap out `z2termAliasScript` in [`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt). The display language follows `LocaleHelper.language`. Installed on all launch paths (proot/z2root/chroot) ([`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt)).
- **`z2scan` command (0.8.91, vulnerability testing)**: a vulnerability-testing helper scoped to this device / localhost, aligned with z2term's principles (this-device/localhost only, non-invasive, no data sent out, distro official packages only). Two parts. ① **Self-check** (`z2scan self`): no external tools — detects TCP LISTEN sockets bound to all interfaces (`0.0.0.0`/`::`) from `/proc/net/tcp{,6}`, risky `sshd_config` settings (PermitEmptyPasswords/PasswordAuthentication/PermitRootLogin yes), permissions of `~/.ssh` and `authorized_keys`, world-writable files in key directories, SUID binaries (informational under fake root), and empty/`.` elements in `PATH`; exits 1 when findings > 0. ② **Scanners** (`net`/`host`/`cve`): thin wrappers that install `nmap`/`lynis`/`trivy`/`grype` once via `ensure_pkg` (`detect_pm` for apk/apt/pacman). `z2scan net` runs nmap `-sT -Pn` (no root) with a **default target of `127.0.0.1`**; a non-local target is refused unless `--allow-remote` is given (plus a warning), structurally preventing unauthorized mass targeting. `host` uses lynis (falling back to `self` if absent); `cve` scans the rootfs for known CVEs via trivy/grype when present. No scanner is bundled and results stay local (F-Droid compliant, nothing sent out). The display language follows `LocaleHelper.language`. Installed on all launch paths (proot/z2root/chroot) ([`Z2ScanScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ScanScript.kt)).
- `launchAndroidSh`: fallback when proot isn't possible (`/system/bin/sh` + minimal mkshrc).

**Execution engine z2root (hidden feature, no root)**: when `executionEngine = "z2root"`, `launch()` swaps the binary to `nativeLibraryDir/libz2root.so` (our own ptrace engine). It accepts a proot-compatible argv subset, so the args/env are reused as-is (`PROOT_*`/talloc are ignored by z2root). If `libz2root.so` is not bundled (`scripts/build-z2root.sh` not run), it falls back to PRoot (**full only**; foss has no proot, so z2root is mandatory and a missing binary stops with "engine binary not found"). Path translation is hardened to be proot-equivalent (canonicalization of in-path symlinks / absolutizing cwd-relative paths via `/proc/<tid>/cwd` / leaving `dirfd`-relative paths untranslated / two-pass translation for `renameat2`/`linkat`/`symlinkat` / `utimensat` path translation / execve loader swap and `#!` shebang resolution / passthrough for non-ELF / non-existent PATH candidates, which skip the loader and let the kernel return `ENOENT`/`ENOEXEC` from a plain `execve`). Verified end to end on-device: `apt install hello` on Ubuntu 24.04 (`Unpacking` → `Setting up` → running the binary prints `Hello, world!`) (0.8.30). 0.8.32 adds a **seccomp-bpf speedup**: instead of trapping every syscall twice via `PTRACE_SYSCALL`, only the syscalls that need path translation / fakeroot faking / getcwd reverse / /proc faking are caught via `SECCOMP_RET_TRACE`, the rest run natively (same approach as proot). On-device benchmarks: ~2.3x faster fork/exec, ~3x faster reads, real-world IO within ~2x of proot, and filesystem walks faster than proot. 0.8.34 introduces **read-free `/proc` faking** and 0.8.35 makes it the default: even after the seccomp work, `read`/`close` still had to be traced just to rewrite `/proc/<pid>/status` and `loginuid` reads, so a tight loop of tiny reads (`dd bs=1`) stayed ~9x slower than proot. Now the faking happens once at `openat` time — the faked content is written to a throwaway temp file inside the rootfs and the `openat` path is swapped to it (unlinked immediately, open-then-unlink) — so later reads hit a plain file and `read`/`close` are dropped from the seccomp trace set entirely (native speed). Verified on-device (run-as): `dd bs=1 ×300000` fell from ~8.1s to ~0.28s (just beating proot's ~0.32s), with status/loginuid faking intact and no temp files left behind. Set `Z2ROOT_NO_READFREE=1` to fall back to the old read-tracing path. 0.8.36 fixes **interactive shells failing to start on glibc distros (Arch/Ubuntu)**: z2root + Arch showed a blank screen with no prompt ("frozen"). The shell was actually running but had started *non-interactive* (no `PS1`): modern glibc (2.42+) implements `tcgetattr` via the `TCGETS2` terminal ioctl, which Android denies (`EACCES`) on an app's pty, so `isatty()` failed and bash/zsh skipped the interactive prompt. (Alpine was fine because musl uses the older `TCGETS`; proot was fine because it rewrites the ioctl.) Fix: z2root now traces `ioctl` and rewrites `TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` to their legacy `TCGETS`/`TCSETS`/… counterparts at syscall entry (the leading `struct termios` layout is identical, so normal baud rates are unaffected). Verified on-device: Arch + z2root reaches an interactive `[…]$` prompt and runs commands; Alpine (musl) shows no regression. 0.8.37 fixes **directly executing a binary inside a bind-mounted directory** (e.g. compiling in your home `/root` and running `./a.out`), which failed with `error while loading shared libraries: … cannot open shared object file` (static binaries: `z2root loader: open(…): No such file or directory`). Root cause: for a dynamic ELF, z2root passed the in-rootfs `ld.so` the program's *host* path, but `ld.so`'s own `open()` is traced and path-translated — a host path under a bind (`-b <home>:/root`) is treated as a guest path and gets the rootfs prefix, so it `ENOENT`s (binaries inside the rootfs happened to work because their host path is already under the rootfs and skips double-translation). Fix: pass `ld.so` the program's *guest* path (reverse-translated via `host_to_guest`), like the shebang path already did, so it resolves for both rootfs and bind locations. Verified on-device: `cd /root && gcc -O2 hello.c -o hello && ./hello` prints `sum(1..100)=5050`, no regression on in-rootfs binaries; gcc 16.1.1 installed offline via `pacman -U` (run-as can't reach the network — SELinux `runas_app` blocks `sendmsg`). Static binaries still segfault under the self-loader (separate, known limitation). This is the concrete deliverable of phase 2 (zeroing FOSS third-party notices); see `docs/FOSS-PURE-HANDOFF.md` §5. 0.8.38 fixes the **Linux GUI (`z2gui`: Xvnc + openbox + a terminal) not working under z2root**: picking z2root and starting the GUI failed (the VNC server wouldn't come up / the viewer couldn't connect) because z2root didn't translate the `sun_path` in `bind()`/`connect()` for AF_UNIX sockets. The X server creates its display socket at `/tmp/.X11-unix/X1`, but z2root passed that path through untranslated, so the kernel tried to create it under the *host's* real `/tmp` (which doesn't exist for an app) and it `ENOENT`'d (the same gap broke dbus / pulseaudio unix sockets). PRoot translates socket addresses, which is why the GUI worked there. Fix: z2root now traces `bind`/`connect` (aarch64 200/203) and rewrites the `sun_path` of pathname AF_UNIX sockets to the in-rootfs host path (abstract sockets — `sun_path[0]=='\0'` — are left untouched, since they're namespace names rather than files and already work over the shared loopback). Verified on-device (run-as): a unix-socket `bind()`+`connect()` at `/tmp/.X11-unix/Xtest` succeeds and the socket appears inside the rootfs (not on the host `/tmp`), with no regression on file path translation. 0.8.39 gets the **GUI to actually render under z2root** end to end (after 0.8.38 let Xvnc start, the VNC screen still stayed black with "Connection reset"): Alpine's `Xvnc` is musl-built and calls `accept(2)` as syscall 202, but Android's untrusted_app seccomp forbids `accept` (bionic only uses `accept4`(242), so 202 isn't allowlisted) — every VNC connection hit a `SIGSYS` and z2root could only fake it away, so the server never got a usable client socket (each came in as "accepted: ::0" and was dropped). Re-running the trapped syscall as `accept4` from the `SIGSYS` stop is unreliable on aarch64 (the syscall is skipped and the PC can't be cleanly rewound), so the fix is a tiny libc-agnostic `LD_PRELOAD` shim (`libz2accept.so`, raw `svc`, no libc deps) that rewrites `accept()` → `accept4(...,0)`; z2root preloads it for every guest process (`ProotLauncher` drops it at `/usr/local/lib/libz2accept.so` in the rootfs and injects `LD_PRELOAD`; the shim is built by `scripts/build-z2root.sh` and gitignored; a load failure is non-fatal — ld.so warns and ignores it). Verified on-device in the real app (untrusted_app): z2root + Alpine + GUI negotiates the full RFB handshake (`accepted: 127.0.0.1::…`, protocol 3.8, pixel format) and renders the openbox desktop with an xterm. This also unblocks z2root SSH servers (dropbear) that `accept`. 0.8.40 fixes **GUI apps (e.g. mpv) hitting an X11 `BadAccess` and segfaulting under z2root**: Xvnc is now started with `-extension MIT-SHM`, disabling the X shared-memory extension. When a client tries MIT-SHM (`X_ShmAttach`), the SysV shared-memory segment can't be co-attached under z2root, so the X server returns `BadAccess` and the resulting async X error crashes mpv (under proot this never surfaced because `shmget` itself fails and the client auto-falls back to non-SHM rendering). VNC is a local connection where shared memory buys almost nothing, so disabling the extension makes every client reliably fall back to plain `XPutImage` (harmless for the proot engine too). The `z2gui` launcher (`GuiScript.kt`) is rewritten into the rootfs on every launch, so existing distros pick it up on the next GUI start. 0.8.43 fixes **`/proc/self` / `/proc/thread-self` *mid-path* mis-resolution**: 0.8.41 only fixed a *leading* `/proc/self…` (rewritten to `/proc/<tracee-pid>` in `host_path_for()`), but an *indirect* symlink still slipped through. When a guest opens `/proc/net/tcp`, the kernel symlink `/proc/net` → `self/net` makes `canonicalize_guest()` walk a `self` component mid-path, and z2root `readlink`ed it as the *tracer* (z2root's parent), resolving to `/proc/<wrong-host-pid>/net/tcp` → `EACCES`. Fix: `canonicalize_guest()` now resolves a `self` / `thread-self` component encountered directly under `/proc` to the tracee pid (instead of `readlink`ing the magic symlink), matching the leading-path rewrite. Verified in the dev environment that a direct `/proc/self/net/dev` read and the indirect `/proc/net/dev` read now resolve identically (the residual `EACCES` there is the outer sandbox restricting per-pid `net/*`, absent on a real device); `id`=root and `/proc/self/comm` resolution are unaffected. Found while dynamically tracing the still-open SSH-reset investigation. The reset itself still needs on-device confirmation: the dev-environment failure is a channel-EOF → dropbear closes the PTY master → kernel `SIGHUP` artifact triggered by closing stdin (`</dev/null`); with stdin held open the login shell starts and prints the MOTD, so the PTY path is largely functional. A real interactive `ssh` sends no channel EOF, so the device-side failure is likely a different cause the dev environment (no mount privileges, double-ptrace) can't reproduce. The `Z2ROOT_TRACE` instrumentation in `z2root.c` is intentionally kept for that on-device trace. 0.8.44 adds a **"This tab is running on" row to the Settings "Execution engine" section** — a read-only line that shows the engine the tab *actually* launched with (`TerminalSession.actualEngine`, from `ProotLauncher.resolveLaunchEngine()` or the chroot path) rather than the selector chip (the next-launch choice), so it stays honest when the choice falls back (z2root not bundled → proot, chroot probe fails → proot). The **7-tap version-row toggle** that shows/hides the engine selector also gets a **3-second cooldown** after it fires, so rapid tapping can't immediately flip it back. 0.8.47 **reworks `--link2symlink` (hard-link `linkat` emulation), which had been breaking git, npm, and copy commands under z2root**: the old implementation turned `linkat(old,new)` into "`new` is a symlink to `old`'s guest absolute path", but that broke git's loose-object finalization (write `tmp` → `link(tmp,final)` → `unlink(tmp)`): `final` became a **dangling symlink** to the just-removed `tmp`, so commits failed with `fatal: … is not a valid object` (dpkg only escaped because its source file survives). npm's global install also expands packages via **hard links** from its cache, so `claude code` "showing no logo / no response" was likely the same dangling breakage (its bundled JS becomes broken links). Fix: **try a real hard link first, and only when Android's app-internal FS rejects `link()` with `EACCES`/`EPERM`/`EXDEV`/etc. does the tracer copy `old` to `new` and report success (0)** (linkat is translated to host paths and run at entry; the exit stage inspects the return value and falls back to a copy). Where real hard links work the original shared-inode semantics are preserved; where they don't (on `/data`) `new` becomes an independent regular file, so it survives a later `unlink(old)` — the generic "atomically finalize via link" pattern (git/coreutils/build tools) works uniformly. Genuine errors (e.g. `new` already exists → `EEXIST`) are preserved. Verified on-device: `ln orig hard; rm orig; cat hard` keeps `hard` as a regular file with its contents, and the full `git init`→`add`→`commit`→`log`→`cat-file` cycle succeeds. ⚠️ **Packages already `npm install`ed under the old z2root have dangling-symlink files and must be reinstalled after this fix.** 0.8.49 fixes **`claude code` (node) failing to start under z2root**: node aborted right after launch with `node: src/unix/core.c:646: uv__close: Assertion 'fd > STDERR_FILENO' failed.` + SIGABRT. Root cause: z2root's SIGSYS handler fakes *every* seccomp-blocked syscall as success (return 0) — the fakeroot strategy — and that also applied to `io_uring_setup` (425), so libuv read the faked `0` as a valid io_uring ring fd, kept fd 0 as its backend, and later called `uv__close(0)`, which aborts on any fd ≤ STDERR_FILENO. Fix: the SIGSYS handler now returns `-ENOSYS` (-38) instead of 0 for the three io_uring syscalls (`io_uring_setup`=425 / `io_uring_enter`=426 / `io_uring_register`=427), so libuv sees io_uring as unimplemented and falls back to epoll (proot never had io_uring either, which is why it worked there); all other blocked syscalls still fake success. Verified by SSHing into a z2root-hosted sshd (single-ptrace real conditions; nesting z2root under the proot dev shell masks the bug via double-ptrace) — node now runs. (An `LD_PRELOAD` shim forcing `io_uring_setup` to `ENOSYS` was used to confirm the cause before patching z2root itself.) ⚠️ A separate, still-open issue: `git clone` using hard links fails with `fatal: hardlink different from source` (Android denies `link()` → z2root's copy fallback changes the inode → git's inode check fails); use `git clone --no-hardlinks` for now. 0.8.53 fixes **GUI audio being silent under z2root** (it already worked under PRoot). Two root causes. (1) PulseAudio's `--daemonize` detaches by re-`execve`ing `/proc/self/exe`, which under z2root resolves to the launcher (`libz2root.so`) and fails with "cannot self execute", so the daemon never starts. The GUI start script (`GuiScript.kt`) drops `--daemonize` and instead runs `setsid pulseaudio -n --exit-idle-time=-1 … &` (backgrounded via `setsid`+`&`, stopped with `pactl exit`). (2) The PulseAudio client's `AF_UNIX` handshake sends its uid/gid via `SCM_CREDENTIALS`, but the kernel only accepts a declared uid equal to the real/effective/saved uid (or with `CAP_SETUID`); z2root's fake-root reports uid 0 while the unprivileged app's real uid is non-zero, so `sendmsg(2)` returns `EPERM` and the client dies with "Connection died". Fix: z2root traces `sendmsg`(211)/`recvmsg`(212) under fake-root and rewrites the `SCM_CREDENTIALS` ucred — outbound to the process's real uid/gid, inbound back to 0 — so the kernel accepts the message while the rootfs still sees root. `SCM_RIGHTS`/memfd passing is untouched (it already worked). Verify: z2root + GUI plays audio, `/tmp/z2gui-audio-<display>.log` shows no "Connection died", and `pactl info` lists `z2sink`. 0.8.54 fixes **a static ELF inside a bind mount failing to exec on-device (z2term self-building itself)** and makes **`scripts/build-z2root.sh` capable of an on-device self-hosted build**. Root cause: when launching a static ELF via `--loader`, z2root passed the loader the program's *host* path, but the loader's own `open()` is also traced and translated, so a binary under a bind (`-b <home>:/root`, e.g. the NDK static clang) was treated as a guest path and got the rootfs prefix → `ENOENT` (`z2root loader: open(…/clang-21): No such file`) — the static analog of the dynamic-ELF hole fixed in 0.8.37. Fix: pass the loader the *guest* path (reverse-translated via `host_to_guest`), exactly as the dynamic path passes `ld.so` its `guest_real`, so static binaries map correctly for both rootfs and bind locations. Build side: the NDK clang is itself a static ELF, so **under the currently-installed engine (before an APK with this fix lands) it can't be exec'd**. So `build-z2root.sh` gains an automatic fallback: if the NDK clang can't exec, it uses an exec'able dynamic rootfs clang as the cross compiler (`--target=aarch64-linux-android29 --sysroot=<NDK sysroot>`) and **links the NDK static libs/crt by hand with GNU ld** (the clang driver's auto-link is avoided because it passes the lld-only `--use-android-relr-tags`, which GNU ld rejects). PC builds pass the probe (`clang --version` prints "clang version") and use the NDK toolchain as before — behavior unchanged. Verified on this z2root term: `bash scripts/build-z2root.sh` runs to completion and produces `libz2root.so` (static EXEC AArch64, NDK r29, no deps, stripped) and `libz2accept.so` in `jniLibs/arm64-v8a/`, i.e. the native part now self-hosts on-device. The (A) loader fix and (B) fallback are tightly coupled (without A a self-hosted z2root can't exec static binaries; without B you can't build the A-bearing `.so` on-device). 0.8.55 makes the accept shim `libz2accept.so` **bionic-safe so `assembleFullRelease` can run on-device (z2term building itself)**. An on-device build injects `LD_PRELOAD=libz2accept.so` across the whole build so the JVM's `accept`(202) (musl) gets through, but the shim referenced `__errno_location` (musl/glibc's errno cell) as a *non-weak* undefined symbol, so when that `LD_PRELOAD` leaked into the **bionic aapt2 that AGP spawns**, aapt2 failed to start with `cannot locate symbol __errno_location` and the build stopped at `processFullReleaseResources` (bionic uses `__errno()` and has no `__errno_location`). Fix: declare `__errno_location` `__attribute__((weak))` with a NULL guard, so it loads even when unresolved (resolves to 0 = harmless under bionic, still sets errno under musl/glibc). Verified under the proot engine (at the time z2root was believed to freeze the terminal under a heavy full build, so it was verified on proot; later, 0.8.62 was built on z2root itself in 16m58s with no freeze — heavy full builds are equivalent on z2root and proot, so either engine works): `LD_PRELOAD=libz2accept.so ./gradlew :app:assembleFullRelease` reaches `BUILD SUCCESSFUL`, and the resulting APK (69 MB, release-key signed) bundles a `libz2accept.so` with a WEAK `__errno_location` and a `libz2root.so` that is the case-3 NDK r29 static EXEC build (verified via unzip + readelf). Note: the incremental merge cache stale-bundled the old `.so`, so the fullRelease intermediates had to be removed and rebuilt (the 0.8.48 `buildZ2rootNative` dependency alone doesn't always force the incremental merge to refresh). 0.8.56 fixes **two parity gaps that blocked `assembleFullRelease` on-device (z2term building itself)**. (1) **Legacy `--link2symlink` (`.l2s`) chains couldn't be followed on open**: the NDK `libc++_shared.so` had been turned into a multi-level symlink by proot/old-z2root link2symlink (`libc++_shared.so` → `.l2s.…0001` → `.l2s.…0001.000N` = the real file), so CMake's native link failed with `ld.lld: unable to find library -lc++_shared`. Root cause: `canonicalize_guest()` always walks a `readlink` target as a *guest* path, but link2symlink stores the target as a *host* real path (`.../shared_home/android-sdk/…`), so walking it as-is made `translate_abs` double-prefix the rootfs → `ENOENT`. Fix: in `canonicalize_guest()`, reverse-translate an absolute link target via `host_to_guest()` before continuing (targets that don't map are passed through, so ordinary absolute symlinks are unaffected). (2) **The Android-native aapt2 couldn't start**: with the CMake gap removed, the next failure was the AAPT2 daemon in `processFossDebugResources`/`…ReleaseResources` failing with `error: expected absolute path: "--argv0"`. Root cause: aapt2 is an Android aarch64 ELF (interp `/system/bin/linker64`), and z2root launches a dynamic ELF as `<interp> --argv0 <name> <prog> <args>`, but **this device's (Android 12) bionic linker64 — unlike glibc/musl ld.so — does not understand `--argv0`** and passes it straight through to the program's argv, so aapt2 mistook `--argv0` for a path argument (proven: `/system/bin/linker64 aapt2 version` succeeds, the `--argv0` form gives the same error; kotlinc/java on glibc ld.so understood `--argv0` and worked). Fix: in `plan_exec()`'s dynamic-ELF path, only when the interp basename is `linker64`/`linker` (bionic) do *not* pass `--argv0`+argv0 (under bionic argv0 becomes the real program path, but Android tools don't read argv0, so it's harmless). ✅ **Both were e2e-verified on the z2root engine after installing the 0.8.56 APK via the app UI (2026-06-09)**: the `.l2s` chain (NDK `libc++_shared.so`) opens without cp materialization (ELF magic at the head), and aapt2 starts with no `--argv0` error in both `version` and `daemon` (`Ready`) modes. Full history in `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §7/§8. 0.8.57 fixes a **readlinkat return-value truncation bug**: `readlink(2)` on a `.l2s` (or similar) symlink returned a path cut short, e.g. `/root/android-sdk/n` (19B). Root cause: the tracee sizes its buffer from the link's `lstat` `st_size` (which z2root reverse-translates to the shorter *guest* length), but the kernel writes the longer *host* real path into that buffer (truncated), and `host_to_guest()` then shortened it further. Fix: like proot, at syscall exit z2root re-`readlink`s the target symlink's host real path itself into a full buffer, then `host_to_guest()`-converts and writes back clamped to `bufsiz` (the target host path is captured at entry into `pid_state.aux_path`; when the host path is undetermined — e.g. `dirfd`-relative — it falls back to reading the tracee buffer as before). The linker only `open`s, so this does not affect the 0.8.56 build success, but it hardens tools that handle `.l2s` chains via `readlink`. ⚠️ **e2e verification of the 0.8.57 readlink fix itself requires installing an APK with this fix via the app UI. 0.8.58 fixes B-3 — local `git clone` failing with `fatal: hardlink different from source`. Root cause is an OS constraint: Android SELinux (`untrusted_app`) bans `link(2)` device-wide, so link2symlink always copy-falls-back (a different inode), and git 2.46+ (which lstats the dest after `link()` and checks `st_dev`/`st_ino` match the source) rejects it. Fix: on a successful copy-fallback, record (src_dev, src_ino, dest_ino) in a small ring (32 entries); at stat-family exit (`newfstatat`/`fstat`/`statx`), when the result's inode matches a recorded dest_ino, fake `st_dev`/`st_ino` (statx: `stx_ino` + `stx_dev_major/minor`) to the source values. The entry is evicted on first match to minimize the spoof window, and the hot path is skipped entirely while no entries are live. Real `link()` paths never fall back, so `ln`/`npm`/`tar` are unaffected. e2e needs an APK with this fix installed.** 0.8.59 adds **static-PIE (ET_DYN) support to the self-loader (`load_elf_and_jump`)** via relocation application and phdr biasing (partly addressing the long-standing "static binaries segfault" limitation). NDK static binaries come in two flavors — ET_EXEC (non-PIE) and ET_DYN (static-PIE); the latter is relocated by neither the kernel nor an interpreter, and the bionic NDK static-PIE crt does not self-relocate either, so loading it at `base!=0` crashes on unrelocated pointer dereferences or on `__libc_init_mte`/`__bionic_get_tls_segment` assuming `load_bias=0` (treating phdr `p_vaddr` as an absolute address = ET_EXEC assumption). Fix: the loader does the ld.so/proot-loader prep itself — (1) walk `PT_DYNAMIC` and apply RELR/RELA (`DT_RELR`/`DT_ANDROID_RELR`/`DT_RELA`) `R_AARCH64_RELATIVE`(1027) as `*(base+off)=base+addend`, and (2) pass `AT_PHDR` a phdr copy with `base` added to each `p_vaddr`, satisfying bionic's `bias=0` assumption. Both run only when `ET_DYN && base!=0`; ET_EXEC (`base==0`) is untouched = no regression for non-PIE static binaries or the NDK clang/lld themselves. Verified with an in-process loader harness that a simple static-PIE (`write` only) runs and a non-PIE one does not regress. ⚠️ **However, a "rich" static-PIE that uses printf/malloc/pthread/TLS still crashes due to a separate root constraint that the loader cannot fix.** A static-PIE with an `__attribute__((constructor))` runs `main` only (no `CTOR_RAN`), proving that **bionic's NDK static-PIE crt (`_start`) never calls `.init_array` constructors** (the non-PIE crt reads `__init_array_start/end` and sets up structors, but the static-PIE crt's `_start_main` only handles `fini` and is missing the init_array setup). Constructors must run after libc init and before `main`, but the loader loses control once it jumps to `_start`, so they can't be called after the fact — this is an **NDK-specific constraint (not a z2root parity gap)** that would behave identically under proot/the kernel. Full detail in `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §11. 0.8.62 **re-fixes B-3 (the git-clone hardlink spoof) after finding the 0.8.58 fix never engaged on the running 0.8.61 engine**: C probes on the live engine showed all 200 linkat copy-fallbacks producing a different-inode dest with the stat-spoof firing zero times — disproving the 0.8.58 "compiled, probably works" assumption. Root cause: the old `linkcopy_record` re-`stat()`'d the dest's host path *after the fact* to capture its inode, which could diverge from the inode the tracee reads via `newfstatat`, so the match always missed. Fix: `copy_for_link` now `fstat()`s the just-created output fd to capture the dest inode at creation time (guaranteeing it equals the entity the tracee later sees), and `linkcopy_record` takes (src_dev, src_ino, dest_ino) by argument instead of re-stat'ing. Compile-verified; e2e (git clone passing hardlink verification) needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10. 0.8.63 **fixes a startup regression that 0.8.62 introduced** — the guest (`Arch Linux ARM`) died immediately with `exitCode=-1`. Root cause: by making the linkcopy record finally *succeed*, 0.8.62 turned on a previously-dormant path. The `newfstatat`/`fstat`/`statx` exit handlers (skipped while `g_linkcopy_used==0`) spoof a copied dest's `st_dev`/`st_ino` to the source's, but the match keyed on **inode number alone** (the comment's "cross-fs inode collision is negligible" was wrong). On Android `untrusted_app`, `link(2)` is denied everywhere, so every guest hardlink copy-falls-back and records; during boot, an unrelated file stat'd by init/ld whose inode number happened to collide with a recorded dest got its `st_dev`/`st_ino` falsified, corrupting early-boot stats and killing the guest. Fix: the match key is now the dest's full `(dev, ino)` (a freshly-created entity has a unique host `(dev, ino)`) — `copy_for_link`'s `fstat` also captures `dest_dev`, `linkcopy_find` matches on dev+ino, and statx reconstructs dev from `stx_dev_major/minor`. B-3 hardlink spoofing is preserved. Compile-verified; e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10. 0.8.64 **actually fixes the same startup regression that 0.8.63 failed to resolve** (the guest still died with `exitCode=-1`). 0.8.63's `(dev, ino)` tightening was ineffective: the copied dest lives in the rootfs bind — on the *same host `/data` partition* as every other guest file — so `st_dev` is a constant across the whole rootfs and `(dev, ino)` reduces to inode-only, leaving the boot-time inode collision intact. Fix: replace inode matching with **path correlation**. `linkcopy_record` records the copy's destination *host path*; at `newfstatat`/`statx` *entry* z2root resolves the stat target's host path via `host_path_for`, and only when it equals a recorded dest does the *exit* handler spoof `st_dev`/`st_ino` (statx: `stx_ino` + `stx_dev_major/minor`) to the source's. False positives on unrelated files are now structurally impossible (`fstat`-by-fd can't be path-correlated at entry, so it only fakes uid/gid, not inode — git's hardlink check uses the `lstat`/`newfstatat` path, so B-3 is unaffected). B-3 hardlink spoofing is preserved. Compile-verified; e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10. 0.8.67 **root-causes and truly fixes the startup regression** — and establishes that **the 0.8.62–0.8.64 stat-spoof work was a misdiagnosis** of it (that work is real but fixes B-3 git-clone hardlinks, unrelated to the startup death). A diagnostic trace plus a full register dump on SIGSEGV pinned the real cause: the loader-side `R_AARCH64_RELATIVE`/RELR application added in 0.8.59 (`load_elf_and_jump`) was also applied to **`ld-linux-aarch64.so.1`**, which the dynamic-binary launch path loads for every guest program. glibc/musl/bionic `ld.so` *self-relocates* in `_dl_start`, so the loader added the load bias a second time, doubling every RELATIVE-relocated pointer → `blr x8` with `x8 = real_ptr × 2` → instruction-fetch SIGSEGV (decisive evidence: `pc == si_addr == x8 == a valid ld.so address × 2`, matching across runs). Fix: gate the loader's relocation behind `skip_reloc` — `plan_exec`'s dynamic-ELF / dynamic-interp paths (whose loader target is the self-relocating `ld.so`) pass `--loader-noreloc` to suppress it, while the static-PIE direct-load path keeps `--loader` and applies it as in 0.8.59. The stat-spoof (path correlation) stays in place since it is valid for B-3. Compile-verified; e2e needs an APK with this fix installed via the app UI. See `docs/Z2ROOT-BUILD-PARITY-HANDOFF.md` §10 ("0.8.67 で起動退行を真に根治"). 0.8.78 **root-fixes musl `ld.so`'s inability to explicitly launch a dynamic ET_EXEC**: musl's `ld.so` refuses to launch an ET_EXEC (non-PIE) "as a command" and dies with `Not a valid dynamic program`, so under Alpine (musl) glibc/musl ET_EXEC binaries (the claude binary, `cc`, …) couldn't start under z2root. A new `--loader-exec <ld.so> <prog> <argv0> [args...]` path `mmap`s both the program and `ld.so` and builds **the same initial stack/auxv the kernel would have set up when exec'ing via `PT_INTERP`** (`AT_PHDR`/`AT_PHENT`/`AT_PHNUM` = the program's phdr, `AT_ENTRY` = the program entry, `AT_BASE` = `ld.so`'s load base), then branches to `ld.so`'s entry (`load_exec_via_interp`/`map_img`). musl then treats itself as "launched as the interpreter" and relocates+starts the program (proot-loader equivalent). The routing happens in `plan_exec` **only when the interp basename is `ld-musl*` and the target is ET_EXEC**; glibc `ld.so` (which accepts explicit ET_EXEC launch — the existing Arch claude path) and PIE are left untouched. Falls back to the legacy path when `use_loader` is off. ⚠️ **e2e needs an APK with this fix installed via the app UI.** 0.8.84 **fixes a regression where an exec with a large argv failed with `ENOENT`**: `rewrite_execve` reads the original argv from the tracee and rebuilds `[target][argv blob][pointer array]` below the guest stack, swapping the path/argv registers, but it had two limits — (1) the argv concatenation buffer was a fixed `char blob[8192]`, so when `blob_sz>8192` the `if (blob_sz<=sizeof(blob))` guard was false and the **rewrite was skipped entirely**, leaving the guest path in the path register → `execve` `ENOENT`; and (2) `MAX_ARGS 256` truncated argv past 256 entries. Found via cross-distro cmdtest e2e: Kali's `apt-get install python3` failed at the dpkg byte-compile step (`python3.13 -E -S py_compile.py <287 files = ~11 KB argv>`) with `cannot execute: required file not found`; bisection pinned it to total argv bytes > ~7.5 KB (with kernel ARG_MAX at 2 MB = a z2root-internal buffer). Fix: read argv with no cap via dynamic growth (`realloc`), and `malloc` `blob`/`parts`/`ptrs` sized to the argv (`MAX_ARGS` removed); the scratch still sits just below `sp` (the growsdown stack is extended by `process_vm_writev`, so a large argv stays mapped). Alpine/Ubuntu cmdtest are clean (zero non-zero exits). ⚠️ **On-device e2e — Kali python install completing and a large-argv exec — needs an APK with this fix installed via the app UI.** 0.8.95 tried to "fix" a **regression where z2root fails to launch after an Android OS major upgrade (15→16)** but self-destructed with two changes: (1) adding `realpath()` to the `host_to_guest` hot path, triggering an lstat walk on every path translation (whole thing crawled, input lagged), and (2) a per-launch `find <rootfs> -type l` full rootfs scan plus symlink recreation — launches became flaky, the keyboard misbehaved, and symlinks got corrupted; reverted in 0.8.96. 0.8.97 **re-fixes it with a hot-path-free, safe version**: the root cause is that the `.l2s` symlinks left by proot --link2symlink carry absolute host paths, and an OS major upgrade changes the absolute-prefix normalization of the data dir (`/data/data` ↔ `/data/user/0`, etc.), so `host_to_guest`'s direct rootfs/bind match misses, the stale absolute path passes through, `translate_abs` double-prefixes the rootfs → ENOENT, and `zsh` etc. fail to start with `cannot open shared object file`. We add a **pure-string fallback** to `host_to_guest` that recovers the guest path from the rootfs marker `"/files/distros/<name>/"`, making the reverse mapping prefix-independent (no `realpath`, which is useless on dangling paths and expensive; no full `find` scan). ⚠️ **On-device OS downgrade is impossible, so the OS-upgrade regression itself cannot be reproduced e2e; the design recovers it prefix-independently by construction.** 0.8.99 **fixes plain ELFs (`ls`/`ssh`, etc.) intermittently failing to start under z2root with `cannot open shared object file`**: the cause is not `.l2s` but the **path-rewrite scratch placement**. Translated host paths were written back into the tracee at `sp - SCRATCH_OFFSET(=2048)` below the stack via `process_vm_writev`, but kernel 6.x does not grow the stack for a remote write, so during the earliest startup (stack low-water ≈ sp, the page just below sp not yet grown) the write crossed the boundary of sp's present page into an un-grown lower page → EFAULT → the rewrite couldn't be stored → the loader failed to open the program/libc. Later locale reads succeed (the stack has grown by then), which is why it split per-run (e.g. 5/8). Confirmed via on-device instrumented trace (`scratch ... wr=-1 errno=14(Bad address)`). Fix: shrink `SCRATCH_OFFSET` 2048 → **16** so the scratch sits **just below sp, within the same present page** (even at startup the present floor is sp's page, so the write always lands). One change covers all 6 scratch-using paths. 0.8.100 cut the frequency sharply (on-device `ls` 8/8), but when `sp` lands exactly on a page boundary or the `.so` host path is long, the scratch still falls into a lower page; plain `ls` passes but zsh fails to load its ZLE module `.so`, leaving line-editing broken (an intermittent keyboard symptom). A present-page-boundary clamp (`scratch_base()`) cannot help the exact-boundary case. **0.8.101 fixes it for real**: `write_tracee_mem` gets a **`PTRACE_POKEDATA` fallback**. POKEDATA goes through the kernel's `__access_remote_vm`, which calls `expand_stack()` to grow the stack before writing, so it reliably writes even the un-grown lower page where `process_vm_writev` (GUP, no grow) EFAULTs — including the previously unsalvageable exact-boundary `sp`. The `scratch_base` clamp is kept as an optimization to maximize the fast path (`process_vm_writev`) hit rate. Verified on-device in a z2root tab: **`ls` 8/8, `sshd --lan` on the first try, zsh keyboard working (0.8.101)** — this closes the cannot-open / keyboard saga that ran from §1 (the mmap-resident-scratch upgrade turned out unnecessary).

**Execution engine chroot (hidden feature, requires root)**: when `executionEngine = "chroot"`, `launchChroot()` is used.

- **Toggling the selector**: tap the version 7 times to toggle `engineSelectorUnlocked` (works without root). Unlocking sets it `true` (proot / z2root become selectable); if `probeRootChroot()` then passes, `rootChrootUnlocked=true` is also set and chroot joins the options. Tapping 7 more times while unlocked sets it back to `false` and resets `executionEngine` to the default proot, returning to the pre-unlock state (two-way toggle as of 0.8.33).
- `probeRootChroot()`: a self-test of `su -c id` (uid=0) + `su -c "chroot <rootfs> /bin/sh -c echo"`. The result is `RootProbe` (Ok/NoRoot/ChrootBlocked).
- `launchChroot()`: via `su -c`, bind mount (/dev, /dev/pts, /proc, /sys, /root, /sdcard) → `chroot` → login shell. The `ensure*` helpers (z2-*/OSC7/history/sshd/gui/z2run) are shared with the proot path.
- **Ctrl+C / job control**: because the controlling terminal can't be owned via `su`, the login shell is launched **through `setsid -c`** to enable it.
- On chroot launch failure, it auto-falls back to proot (`TerminalSession.startTerminal`). End-to-end verified on a rooted device under SELinux Enforcing (moto g13 / Magisk). `full` flavor only.

### 4.4 Distro management (`distro/`)

- `DistroBundle`: `ROOTFS_VERSION` (=6), `VERSION_MARKER`, `BUNDLED_DISTRO_ID="alpine"`.
- `DistroSpec`: id / display name / package manager / bundleable / asset name / DL URL or index URL / default shell / approx. DL size.
  - Alpine = bundled (`alpine-minirootfs-aarch64.tgz`, zsh). Ubuntu/Arch/Kali = resolve the latest `rootfs.tar.xz` at runtime from the linuxcontainers index and download (bash).
- `DistroInstaller`: a dependency-free hand-written tar parser (ustar/GNU `L`/PAX `x`/`g`, symlink/hardlink). `decompress` detects gzip/xz by magic bytes.
  - `postInstallSetup`: resolv.conf/hosts, `pacman.conf` (disable sandbox/DownloadUser), apt Sandbox::User=root, write the version marker.
  - Permissions are **owner-only** (`setUnixMode(ownerOnly=true)`). world-writable makes sudo refuse.
- `DistroDownloader`: HTTP DL + SHA256 verification, cached at `cacheDir/distros/<id>-<abi>.tgz`.

### 4.5 Terminal emulator (`emulator/`)

- `TerminalEmulator`: processes byte streams with a state machine (Ground/Escape/CSI/OSC…).
  - Character width: East Asian Width aware (the `ambiguousAsWide` setting makes ambiguous width 2 cells). Non-BMP characters (emoji 😀 / CJK extensions) are stored across 2 cells as a surrogate pair — high surrogate in the left cell, low surrogate in the right (`wideCont`) cell. **Rendering (`TerminalRenderer.glyphAt`), selection copy (`getRangeText`), and row text (`toText`) recombine both cells into a single glyph** (0.8.74). Previously the right cell was dropped and the lone high surrogate was drawn/emitted, producing a tofu (?) box.
  - SGR: bold/underline/inverse/strikethrough, 16/256/RGB (truecolor).
  - DEC modes: alternate screen, cursor keys (DECCKM), **mouse reporting** (X10/Normal/Button/Any × Legacy/SGR/urxvt). While mouse reporting is ON (the TUI enabled `?1000` / `?1006` etc.), swipes inside `TerminalInputView` are **split by direction**: **finger going up (= "advance")** is sent as wheel-down (button 65) via `encodeMouseEvent`, while **finger going down (= "look back")** falls back to scrollback control (0.8.115; many reader TUIs deliberately ignore wheel-up and "leave it to the terminal scrollback", so the app only needs to forward upward swipes). One notch is sent every time the cumulative finger dy crosses `MOUSE_WHEEL_STEP_PX (=40px)`, so a long swipe produces a multi-line scroll. Flings are no-op only on the upward direction (already advanced by per-onScroll wheel notches); downward flings still drive scrollback inertial scroll.
  - **Scroll region (DECSTBM)**: line-feed scrolling (`lineFeed`/IND) only pushes the top line into scrollback when the region spans the whole screen; when `DECSTBM` sets a custom region it **scrolls within the region only**, leaving lines outside it untouched and not pushing to scrollback (0.8.105; previously it ignored the region and always called the full-screen scrollUp, so when vim etc. pinned a bottom status/command line — line numbers / ruler — and kept inserting newlines, that fixed line was pushed up one row each time, producing the "line numbers burned into every row" bug. Locked down by `ScrollRegionLineFeedTest`). `IL`/`DL`/`SU`/`SD`/`RI` already respected the region.
  - OSC: 7 (cwd) / 8 (hyperlink) / 10–12 (fg/bg/cursor color, replies to `?` queries) / 52 (clipboard) / palette. OSC titles are UTF-8 decoded (prevents mojibake in Japanese tab names).
  - **Cells of URL/OSC8 links are underlined.** Long URLs carry a wrapped flag on their wrap-origin row for detection (tap to open).
  - bracketed paste (DECSET 2004) supported.
  - `cursorKeyBytes`, `encodeMouseEvent`, `resize` (cursor-aware), scrollback.
- `SearchEngine` (M11): full-text scrollback search. 🔍 → type → ↑↓ to jump back/forward. For CJK, the highlight position is computed in **cell columns**.
- `TerminalBuffer`/`TerminalRow`/`TerminalCell`/`SgrAttribute`: cell storage and scrollback.
- `TerminalColors`/`AvailableThemes`: 9 themes (ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night / Catppuccin Mocha / Catppuccin Latte / Monokai).

### 4.6 Domain (`core/`)

- `SessionManager` (object): exposes the list of `TerminalSession` + active via `StateFlow`. `ensureFirst`/`openNew`/`close`/`setActive`/`moveSession` (tab drag reorder). `close` first removes the tab from the UI and runs teardown (PTY/SSH disconnect, GUI=Xvnc stop) in the background to avoid sluggish tab removal.
- `TerminalSession`: state machine `IDLE→INSTALLING→STARTING→RUNNING→EXITED/ERROR`.
  - dedicated emulator dispatcher, PTY read loop, `writeBytes`, resize, `startTerminal`/`switchDistro`/`restart`/`reinstallDistro`/`startSsh`.
  - **Startup distro awaits the persisted value to avoid a race**: `settingsFlow` is `stateIn(Eagerly)` whose initial value is the default Snapshot (`distroId=alpine`), so if `startTerminal` runs before DataStore's first emission lands (right after an app update or device reboot), it would launch the default Alpine instead of the selected OS (the "occasionally Alpine boots" symptom). `startTerminal` now awaits `settings.flow.first()` before choosing the distro, so the selected OS is launched reliably (0.8.105).
  - `StateFlow`: uiState / redrawTick (≈60fps coalescing) / scrollOffset / cellMetrics / selection / cwd / label / settingsFlow.
- `TerminalSelection` / `CellMetrics`: selection range (absolute rows) and 1-cell dimensions.
- `SessionStore`/`SessionManager` (M11): saves tab layout `{id,label,distro,cwd}` + activeId to DataStore (write-only). **0.8.70 disables startup restore**: to avoid multiple tabs reopening on every launch, `ensureFirst` always opens just one fresh tab (user request). `save` is kept for a future restore UI / debugging but has no read-back path. **cwd is captured via OSC7** (`ensureOsc7CwdConfig` makes bash/zsh emit OSC7 in the prompt hook).

### 4.7 Communication channels (`channel/`)

- `ProcessChannel` (interface): `reader`/`writer`/`isAlive`/`exitCode`/`resize`/`close`.
- `LocalPtyChannel`: wraps PtyProcess (local proot).
- `SshChannel`: remote connection via JSch. `shell` channel + `-L` local port forwarding, host key verification (`KnownHosts`/`HostKeyVerificationDialog`), keys encrypted with the Keystore (`KeystoreCrypt`).
- `SshProfile`/`PortForward`: persisted as JSON in DataStore (`z2term_ssh`).

### 4.8 Settings (`settings/AppSettings.kt`)

- Exposes DataStore (`z2term_settings`) as a `Snapshot` data class + `Flow`. Each setter is suspend.
- Items are in [§7](#7-settings).

### 4.9 Keep-alive service (`service/TerminalService.kt`)

- `foregroundServiceType=specialUse`. `start`/`detach` (only drops keep-alive, keeps the session)/`stop` (terminate all).
- `PARTIAL_WAKE_LOCK`, notification (`ic_notification` = transparent Z2 icon, tap to return / stop action).

### 4.10 File integration (`saf/Z2TermDocumentsProvider.kt`)

- `DocumentsProvider` (authority `<applicationId>.documents`, `permission=MANAGE_DOCUMENTS`).
- Exposed roots: **home = `shared_home`** (same backing as the terminal's `/root`) + each distro's rootfs (`/`).
- Traversal protection: only under the allowed roots `[shared_home, distros]`. Supports R/W/create/delete/rename.

### 4.11 UI details (`ui/`)

- `terminal/TerminalScreen.kt`: overall layout. TopBar / TabBar / render area / keyboard toggle / keyboard area. `KeyboardMode = CUSTOM | SYSTEM`. In **landscape**, orientation is detected via `LocalView.OnLayoutChangeListener`, switching to a Row layout (`SideKeyboardColumn`) per the `landscapeKeyboardPosition`/`Width`/`Height` settings. `landscapeScaledStyle()` scales keyHeight/font proportionally to landscape height.
  - **Keyboard toggle bar (`KeyboardToggleBar`)**: a 16dp tall strip whose tap shows/hides the keyboard. `.clickable`'s touch slop (~8dp) alone was not enough — during flick input a finger occasionally grazed the bar and accidentally hid the keyboard — so a custom `pointerInput` gesture is used instead: **if the cumulative movement from `down` exceeds 24dp, `onToggle` is suppressed**, so only a clean tap (< 24dp) toggles (0.8.109; previously, while `.clickable` would not fire past touch slop, short drags could still slip into tap detection and hide the keyboard).
  - **Toolbar (`ReorderableToolbar`)**: 📋 paste / 📜 commands / 💡 screen-on lock / 🔒 background keep-alive / 🔍 search / ⌨ keyboard toggle / ⚙ settings, drawn from a list of `ToolbarItem`. **Plain tap = the action; long-press drag reorders** (`detectDragGesturesAfterLongPress` + swap on crossing a neighbor's center). A `ToolbarTooltip` Popup shows a short description while held. The order persists in `AppSettings.toolbarOrder` (comma-separated ids), merged with the default via `mergeToolbarOrder` so adding/removing buttons never breaks it. The keep-alive lock defaults to the right of the screen-on lock. The GUI tab (`GuiTopBar`) shares the same `ReorderableToolbar` (no search; 📋/📜 bridge via keysyms).
- `terminal/TerminalRenderer.kt`: **per-cell drawText** on a native Canvas (avoids subpixel error accumulation when advance≠cellW). Order: background → selection highlight → text → cursor → selection handles.
- `terminal/input/TerminalInputView.kt` (AndroidView): physical key/OS IME input, gestures (tap/long-press selection/drag scroll/pinch zoom/mouse click emission). Selection is in [§6.5](#65-text-selection-ux).
- `terminal/keyboard/`:
  - `TerminalKeyboard.kt`: 5-row custom keyboard. 3-state Shift, flick, long-press repeat on all keys. **The key background turns bright green when pressed**, and **during a flick the hint in the crossed-threshold direction is bolded + enlarged 1.6×** (the center character stays unchanged).
  - `JapaneseFlickKeyboard.kt`: built-in Japanese/katakana flick. Same press/flick visual feedback.
  - `KeyboardStyle.kt`: COMPACT (44dp) / SPACIOUS (60dp, 4-direction flick). `naturalHeight`. `.copy()` makes a scaled style for landscape.
  - `KeyGestures.kt`: shared gesture for tap + long-press repeat (reports press state to the Composable via the `onPressedChange` callback).
  - `components/SpecialKeyBar.kt`: the special key row for OS IME mode.
- `settings/SettingsSheet.kt` + `SshAccessHelper.kt`: settings modal + SSH/storage helper.
- `ssh/SshProfilesSheet.kt` + `HostKeyVerificationDialog.kt`: SSH profile UI + key verification.
- `snippets/SnippetsSheet.kt`: command snippets (tap a line to insert, reorder/edit).

### 4.12 GUI desktop (`gui/`)

- Inside the distro, launches **Xvnc** (VNC server) + a lightweight WM/app (`proot/GuiScript.kt` idempotently places and launches them; GUI auto-start / landscape support).
- **GUI stack install (`ensure_pkgs`)**: if Xvnc / openbox / the selected terminal are all present, it **starts immediately with no network** (policy: don't update/re-fetch an existing install). **Only when something is missing** does it fetch the missing pieces via `install_pkgs` (apk add / apt install / pacman -S); if it still can't, it fails with a clear message. It runs after the app-side download-confirm gate (`confirmBeforeDownload`) takes consent. Only `clean` wipes the cache and reinstalls (`clean_pkgs`, for corrupted-state recovery).
- `GuiSession`/`GuiActivity`/`GuiScreen`/`GuiViewport`/`GuiInputView`/`GuiKeyMapper`/`GuiEventWatcher` + `gui/rfb/RfbClient.kt` (built-in RFB client). Pairs a terminal tab with a GUI tab with IME linkage.
- **Input**: `GuiInputView` gestures — **2 fingers = pinch (zoom/pan)**, **3-finger vertical move = wheel up/down scroll** (once it becomes 3 fingers, it's treated as scroll until all fingers lift). The old scroll buttons and `RfbClient.scrollWheel` were removed.
- **Video**: because `gpu` output fails on GPU-less devices, mpv plays correctly with **`vo=x11` default + `LIBGL_ALWAYS_SOFTWARE`** software rendering.
- **Audio (`service/AudioBridge.kt`)**: **opt-in** (only when the "GUI audio" setting `guiAudioEnabled` is ON). In-distro PulseAudio (started with the `-n` method) → TCP → bridged to Android `AudioTrack`.

### 4.13 Android API bridge (`Z2ApiBridge` / `Z2ApiScript`)

- A set of commands to invoke Android features from the terminal: `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip (set/get)` / `z2-battery` / `z2-vibrate`.
- `ProotLauncher.ensureZ2ApiScripts` writes them to `/usr/local/bin` on every launch. req/resp watch `getExternalFilesDir/z2api` with a `FileObserver`, args are base64, atomic rename.

---

## 5. Key data flows

### 5.1 Input → output

```
key/IME/flick → onBytes(ByteArray)
   → TerminalSession.writeBytes → channel.writer (PTY/SSH)
   → distro shell processes it → stdout
   → readLoop (IO) reads channel.reader
   → emulator.processBytes (dedicated thread) → buffer update
   → redrawTick/StateFlow notification → TerminalRenderer redraws the Canvas
```

### 5.2 Startup sequence

```
MainActivity.onCreate → SessionManager.ensureFirst → setContent(TerminalScreen)
TerminalScreen: if active is IDLE, startTerminal()
  → isProotAvailable? → isDistroReady? (if missing/old, deploy via DistroInstaller; non-bundled DL first)
  → ProotLauncher.launch (inject history rc + sshd wrapper, bind shared_home/sdcard)
  → LocalPtyChannel → RUNNING → readLoop starts → send initCommand
On failure: fall back to launchAndroidSh
```

---

## 6. Feature specification

### 6.1 Custom keyboard (ASCII)

- Layout (5 rows): `ESC 1..0 ⌫` / `TAB q..p` / `あ a..l ⏎` / `⇧ z..m,./` / `CTRL ?# ALT SPACE ←↓↑→`.
- **Shift**: 3-state cycle OFF → ONESHOT → LOCKED. **CTRL/ALT/symbol (?#)**: toggle.
- **Flick**: on letter keys, **flick down = uppercase Latin**. Up/left/right = symbols (green hints; flick down has no hint). COMPACT has up + down, SPACIOUS has 4 directions + down.
- **Long-press repeat**: numbers / arrows / space / letter keys repeat while held (first 400ms→55ms). ⌫ is 500ms→60ms, with left/right flick = Ctrl+W / Ctrl+U. Modifier keys don't repeat.
- The "あ" key → switches to the built-in Japanese flick. The TopBar "あ" → switches the OS IME (a separate path).
- **English locale (`showJapaneseKeyboard=false`)**: with no "あ" key, SPACIOUS drops ⇧/CTRL down one row and puts a **META key** (= the same ESC-prefix modifier as Alt) at the left of `a`, removing the gap at the home-row start. Row 5 left is CTRL. COMPACT has no left key on the home row to begin with, so it is unchanged.

### 6.2 Japanese flick keyboard

- Standard 12-key layout (5 columns × 4 rows, with hints):
  ```
  ESC      あ   か  さ   ⌫
  ◀/▼     た   な  は   ▶/▲
  ␣       ま   や  ら   変換
  ABC      小゛゜ わ  、。  ⏎    ← ABC = back to Latin
  ```
  Row 2's edges stack the cursor keys directly under the left/right keys, half a row each
  (`JpEdgeStack`, 1:1), so ◀ ▶ ▼ ▲ are all the same size: ▼ under ◀ (left, down), ▲ under ▶
  (right, up). They `flush()` then send cursor up/down. Space / convert stay full-height in Row 3
  (kept easy to press).
- Flick rules: tap = あ row / left = い / up = う / right = え / down = お.
- **Dakuten key (小゛゜)**: cycles the previous kana through dakuten→handakuten→small→original (the cycle table is hiragana-based). Repeated kana aren't cycled but stack naturally ("つつ" doesn't become "っ").
- **⌫**: flick left = delete word (Ctrl+W) / flick right = delete to line start (Ctrl+U).
- The long vowel `ー` is the right flick of `わ`. Katakana has no dedicated key; it's chosen from katakana candidates in the candidate bar (§6.2.1).

#### 6.2.1 Kana-kanji conversion (`KanaKanjiConverter` / `ComposingState`)

A best-effort conversion that binary-searches an SKK dictionary (`assets/z2dict.txt`, ~160k lines) + conjugation completion for common verbs/adjectives. The candidate bar (`CandidateBar`) updates on every keystroke.

- **Candidate generation (`convertFlexible`)**: learning history (exact match) → learning history (prefix match = predictive conversion) → whole-sentence best conversion (`nbest`) → exact match (`convert`) / okurigana conjugation (`okuriForms`) → prefix-match prediction (`predict`). Raw kana / katakana always remain as confirmed candidates.
- **Learning history** (`ImeHistoryStore`): ranks confirmed words by frequency and recency (last 7 days) and surfaces them near the top.
- **Predictive conversion (prefix match over learning history)**: learned phrases whose reading starts with what was typed are surfaced above whole-sentence conversion (`ImeHistoryStore.predictHistoryWithReading` / the prefix-match stage of `convertFlexible`) — genuine predictive conversion that filters "phrases you habitually type" from a partial reading. **When a predicted candidate is confirmed, it is learned under the phrase's actual reading, not the typed prefix**: `ComposingState.commit` reverse-looks-up surface → actual reading via `KkcConverter.predictionReadingMap` and uses it as the `ImeHistoryStore.record` key. This keeps invalid prefix-only history keys out, and the prediction stays reusable under the same reading next time.
- **Bunsetsu-split synthesis (`segment`)**: joins a content word (longest dictionary match) + following particles/okurigana into one bunsetsu (e.g. きょうの → 今日の). **Particles** (の/は/が…) and **sentence-ending auxiliaries** (でしょう/ました/です…) have single-kanji entries (野/葉/増田…), so they are **left in kana** (`PARTICLES` / `AUX_KANA`). Returned when there is ≥1 dictionary-hit bunsetsu ∧ it contains kanji.
- **Split conversion**: the convert key (or ◀▶) focuses the leading bunsetsu (`autoSplitHeadLen` = takes the content word + following particle as a bunsetsu). ◀▶ expands/shrinks the block range; tapping a candidate / ⏎ confirms and auto-advances to the next block. Repeating the convert key cycles candidates.
- **Automatic block splitting for long sentences**: long sentences that split into ≥2 bunsetsu auto-split to the leading bunsetsu and predict per block without pressing the convert key (decided by `KkcConverter.bunsetsu`). A single in-progress word isn't split. **The bunsetsu boundary uses the exact lattice shortest path (`nbest` #1)** (0.8.29): the position-DP `segments` keeps only a single right-context and could mis-split depending on connection costs, so it was switched to the `nbest` #1 split.
- **Dynamic block segmentation (learned)** (0.8.71): block boundaries are not fixed by dictionary cost alone — they are learned from how often the user confirms a given reading-block. `ImeHistoryStore.learnedBlock(reading)` returns `(top surface, cost reduction)` for confirmed `(reading → surface)` pairs (wired into `KkcConverter.learnedBlock`); during `nbest` lattice construction, any reading ≥2 chars matching a learned block has its node cost reduced (`BLOCK_BASE_BONUS=3000` + `count`-scaled `BLOCK_COUNT_STEP=1500` (capped at count 4) + recent `BLOCK_RECENT_BONUS=1000`). This overcomes the katakana penalty + connection costs after 1–2 confirmations, so a mis-split frequent reading auto-merges into a single block from then on. Even readings absent from the dictionary get a synthesized node (`lc=rc=0`) from the learned surface (unlearned readings behave unchanged). **The cost reduction applies only to the surface the user actually confirmed** (0.8.74), because applying it uniformly to every surface of the reading let the dictionary-cheapest surface win and the user's chosen kanji was not reflected. **Score merged readings at one-word cost and learn consecutively-confirmed runs as merged blocks** (0.8.85): the learned-block synthesized node cost uses a one-word `UNK_COST` baseline instead of length-scaled unknown kana, and `ComposingState` accumulates consecutive confirmations within one split run into `committedRun`, recording the merged reading → merged surface when the run ends (`learnMergedRun`) and on batch confirm (bounded to reading length 2…`MERGE_MAX_READING_LEN`=6).
- **Whole-sentence batch prediction** (`fullPrediction`): when a tail remains during split, a single "whole-sentence" candidate is shown as a light-green pill, concatenating the leading block's top candidate + the Viterbi 1-best of the remaining kana. Tapping it (`commitFull`) confirms the whole sentence at once. **When `splitHeadLen` moves with ◀▶, it is rebuilt via `refreshPredict` to follow the boundary change** (0.8.16). The remaining-kana Viterbi uses the leading surface as context for bigram re-ranking. **Batch confirmation learns per block** (0.8.74): `fullPredictionBlocks` (leading block + `bunsetsu(tail)`) is kept, and `commitFull` learns each block's `(reading → surface)` plus the bigram between adjacent blocks. If the breakdown is inconsistent with `full`, it falls back to a single whole-sentence entry.
- **Reconversion**: right after confirming (composing empty), the convert key = "reconvert" returns the last confirmation to its reading (`restoreLastCommit`).
- **Key background**: during composing, the ◀▶ / convert key backgrounds stay quiet (not green); green is only for the convert key as a "reconvert" hint.

### 6.3 SSH (terminal → outside)

- `SshProfilesSheet` edits host/port/user/auth (password or private key + passphrase)/initCommand/`-L` forwarding.
- On connect, `SessionManager.openNew` + `startSsh(profile)`. The host key is confirmed in `HostKeyVerificationDialog` (saved to `KnownHosts`).

### 6.4 SSH server (PC → terminal) — dropbear

- **OpenSSH `/usr/sbin/sshd` doesn't work under proot** (privsep breaks + new OpenSSH won't start with `UsePrivilegeSeparation`). → uses **dropbear**.
- In the terminal, **`sshd`** = the `/usr/local/sbin/sshd` wrapper (placed by ProotLauncher on every launch, PATH priority). `dropbearBootstrapScript` is the body.
  - Port priority: `-p` / `-o Port=N` arg → `Port` in `/etc/ssh/sshd_config` → default 2222.
  - Supports `-f <config>` / `-D` (foreground) / `-t` (config check). Warns that privileged ports (<1024) can't be bound under proot.
  - If dropbear isn't installed, auto-installs via pacman/apt/apk/dnf/zypper. An existing dropbear is reliably stopped via pkill→pidof→pidfile→`/proc` scan.
- The "Start sshd" button in settings also runs `sshd`. The displayed `ssh -p <port>` reflects the sshd_config Port.

### 6.5 Text selection UX

- Long-press starts selection → drag to extend. **`GestureDetector` doesn't send onScroll after onLongPress**, so while `touchMode != NONE` it follows raw `MOTION_MOVE` without going through the detectors.
- Handle hit area enlarged (row height × 2.2 / min 96px, picks the nearest end, grabbable even at the left edge). **Dragging near an end changes the range.**
- **Magnifier**: during selection, the terminal render View is shown above the finger via `android.widget.Magnifier`.
- **Edge auto-scroll**: detection zone row height × 2.5 / min 80px. Top edge → past / bottom edge → latest, scrolling every 45ms while extending the selection off-screen.
- During selection, a floating "Copy" button; tap to clear the selection.

### 6.6 Command history persistence

- proot is SIGKILL'd on exit so history isn't written → rc/env injected on every launch. bash: `histappend` + `PROMPT_COMMAND='history -a'`; zsh: `INC_APPEND_HISTORY`/`SHARE_HISTORY`. Appends per command, so ↑ recalls history after restart.

### 6.7 File sharing / external storage

- SAF home = `shared_home` (matches the terminal's `/root`). Each distro's rootfs (`/`) is also exposed.
- From inside proot, `cd /sdcard` reaches Android shared storage (needs all-files-access permission); `/storage/app` is the app-private area (no permission needed).

### 6.8 Other UI

- Multiple tabs (**long-press → drag left/right to reorder**, double-tap to close), pinch font zoom (8–32sp), scroll + a ↓ to return to latest, snippets, live theme/font preview.
- Settings (`SettingsSheet`): in 0.8.14, dropped the old bottom sheet stacking from below and now shows as a **full-screen "separate page"** (back arrow ← at top + system-back support).

---

## 7. Settings

| Item | Key | Default | Range/options |
|---|---|---|---|
| Theme | themeName | "ZTS Theme" | 9 options |
| Font | fontId | "monospace" | System / IBM Plex / JetBrains / Fira Code |
| Font size | fontSizeSp | 13 | 8–32 |
| Scrollback lines | scrollbackLines | 5000 | 500–50000 |
| Distro | distroId | "alpine" | alpine / ubuntu / archlinux / kali |
| Ambiguous as wide | ambiguousAsWide | false | true/false |
| Initial command | initCommand | "" | any |
| Login shell | loginShell | "/bin/zsh" | /bin/zsh, /bin/bash, /bin/sh |
| Keyboard style | keyboardStyleId | "spacious" | compact / spacious |
| Keyboard mode | keyboardMode | "custom" | custom / system |
| Landscape keyboard position | landscapeKeyboardPosition | "bottom" | left / bottom / right |
| Landscape side KB width | landscapeKeyboardWidthDp | 420 | 280–700 dp |
| Landscape keyboard height | landscapeKeyboardHeightDp | 320 | 200–500 dp |
| Portrait keyboard height | portraitKeyboardHeightDp | 320 | 200–500 dp |
| GUI terminal | guiTerminalId | "xterm" | terminal launched inside the GUI |
| GUI audio | guiAudioEnabled | false | true/false (opt-in PulseAudio bridge) |
| GUI magnification | guiMagnification | 1.5 | 0.5–3.0 |
| Confirm before download | confirmBeforeDownload | true | true/false |
| Keep-alive service | keepAliveService | true | true/false (**toggled from the toolbar 🔒 lock, not the settings page**) |
| Toolbar order | toolbarOrder | "" (default order) | comma-separated ids; updated by long-press drag |
| Execution engine (hidden) | executionEngine | "proot" | proot / z2root / chroot (chroot only when root is unlocked) |
| Engine selector unlock (hidden) | engineSelectorUnlocked | false | toggled by tapping the version 7 times (no root needed; locking resets engine to proot) |
| chroot unlock flag (hidden) | rootChrootUnlocked | false | true when the 7-tap root self-test passes |
| Language | (dedicated SharedPrefs `z2term_locale`) | OS default | ja / en |

`noInstallTimeout` (disable install timeout), `cleanInstallGuiArmed` (GUI clean re-deploy flag), etc. are also kept in DataStore (`z2term_settings`). SSH profiles are saved as JSON in a separate DataStore (`z2term_ssh`).

---

## 8. Permissions

| Permission | Purpose |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | distro DL, SSH, package fetch |
| FOREGROUND_SERVICE(_SPECIAL_USE) | keep-alive terminal |
| POST_NOTIFICATIONS | keep-alive notification (Android 13+) |
| WAKE_LOCK | background maintenance |
| MANAGE_EXTERNAL_STORAGE | R/W to all shared storage via `cd /sdcard` (granted from settings) |
| READ/WRITE_EXTERNAL_STORAGE (maxSdk) | for old APIs (`requestLegacyExternalStorage`) |

---

## 9. Build / bundled assets

```bash
bash scripts/build-bundle.sh          # generate all bundled assets at once
# individually: build-proot.sh / build-alpine-rootfs.sh aarch64 / fetch-fonts.sh
sh scripts/z2root-cmdtest.sh          # cross-test fragile commands that hit z2root's hard paths (10 groups; skips missing cmds; trailing non-zero summary. SKIP_NET/SKIP_BUILD/RUN_SSHD/RUN_PRIV)
./gradlew :app:assembleFullDebug      # APK (full = rootfs bundled)
./gradlew :app:assembleFossDebug      # APK (foss = rootfs excluded, runtime DL)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

- full bundle: `src/full/jniLibs/arm64-v8a/{libproot,libproot_loader,libtalloc,libandroid-shmem}.so` (full flavor only), `src/full/assets/alpine-minirootfs-aarch64.tgz` (full only), `assets/fonts/*.ttf` (shared).
- foss excludes the rootfs and fetches it at startup via `DistroSpec.ALPINE`'s official CDN URL + SHA-256 (`DistroSpec.bundledInApk` returns false). foss also excludes the proot/talloc prebuilts (F-Droid non-compliant) and runs on z2root built from bundled source instead.
- **The rootfs in assets uses the `.tgz` extension** (with `.tar.gz`, aapt decompresses and renames it).
- **`useLegacyPackaging=true` is required** (so the `.so` files that get execve'd are placed as real files in nativeLibraryDir).
- When the rootfs composition changes: edit `scripts/alpine-packages.txt` → bump `DistroBundle.ROOTFS_VERSION` by +1 → `FORCE=1 build-alpine-rootfs.sh` → assemble (users auto-redeploy by swapping the APK).

---

## 10. Known constraints and design pitfalls

**PRoot kernel-privilege constraints (unfixable)**: even appearing as root, `ip`/`nmap -sS`/`ping`/privileged-port bind are unavailable. Alternatives include `nmap -sT`. OpenSSH sshd also breaks privsep, so dropbear is used.

**Easy pitfalls (recurrence prevention)**:
- The terminal's `/root` is **`filesDir/shared_home`**, not `distros/<distro>/root`. SAF/external-storage bind are based on this too.
- Typing a multi-line script directly into the terminal causes **zsh to misexecute `#` comments / break on continuation prompts** → write it to a file and run with `sh`.
- Restarting dropbear without killing it gives "Address already in use".
- `GestureDetector` **doesn't send onScroll after onLongPress** → long-press selection uses raw MOTION_MOVE.
- When `ScaleGestureDetector`'s **quick scale (single-finger double-tap + drag to zoom) is enabled**, the single-finger DOWN gets absorbed into the internal double-tap watch and `GestureDetector.onLongPress` fires intermittently (a symptom that only recovers after a two-finger pinch). This app only uses two-finger pinch, so it's turned OFF with `isQuickScaleEnabled = false` (0.8.16).
- Realtime PTY input with Compose `BasicTextField` breaks IME sync → `TerminalInputView` + a custom InputConnection.
- Calling `requestFocus` in an AndroidView factory makes the IME pop up on its own.
- Mozc ignores `FORCE_ASCII` (ASCII input isn't guaranteed with a Japanese IME).
- Batching an SGR run into one drawText causes cursor drift → per-cell drawText.
- Writing `*/` (e.g. `*.tgz`) inside KDoc closes the comment early.
- `setUnixMode` must be owner-only (world-writable makes sudo refuse).
- A fixed `/bin/sh` in proot launch runs busybox ash and loses zsh features → `resolveShell`.
- **The chroot engine can't own the controlling terminal via `su`, so Ctrl+C/job control don't work** → launch the login shell through `setsid -c`.
- **GUI video**: mpv's `gpu` output garbles / half-renders on GPU-less devices → `vo=x11` default + `LIBGL_ALWAYS_SOFTWARE`.
- **GUI audio**: PulseAudio must start with the `-n` method or it conflicts with existing config. Passing `AudioBridge`'s target port as 0 yields silence (specify the default port explicitly). **Under z2root**: `--daemonize` fails because it re-execs `/proc/self/exe` (= the launcher) → background it with `setsid …&`. The AF_UNIX `SCM_CREDENTIALS` handshake gets `EPERM` from the kernel when fake-root reports uid 0 → z2root rewrites the `sendmsg`/`recvmsg` (211/212) ucred to the real uid (0.8.53).
- **Swipes split by direction; downward falls back to scrollback (0.8.115)**: 0.8.114 forwarded every direction as a wheel event, but most reader TUIs (nvlg/less etc.) **deliberately ignore wheel-up (`evScrollUp`) and let the terminal scrollback handle "look back"**, so the previous behaviour left the upward swipe doing nothing. `onScroll` now calls `sendMouseWheelFromSwipe` only when `distanceY > 0` (finger going up = "advance") and sends wheel-down. When `distanceY < 0` (finger going down = "look back") it falls back to the existing scrollback path. `onFling` is no-op only on `velocityY < 0` (upward fling); downward flings still drive scrollback inertial scroll. `sendMouseWheelFromSwipe` is simplified to wheel-down only (fixed button, positive notches). [`MouseEncodeTest`](../../app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt) is added to pin SGR/URXVT/LEGACY output (leading ESC, button, terminator) and DECSET `?1000`/`?1006` state transitions as regression guards.
- **Swipes turn into wheel events when mouse reporting is on (0.8.114)**: fixes "tap-scroll does not advance pages" in TUIs that opt into SGR mouse reporting. Even when the TUI requested mouse reporting via `?1000h` / `?1006h`, `TerminalInputView.onScroll` ignored that and always operated the scrollback (`scrollOffset`), so wheel notches never reached the TUI. The fix branches in `onScroll` on `emulator.mouseEnabled` and calls `sendMouseWheelFromSwipe`, which produces `encodeMouseEvent(button = 64/65)` and writes the bytes to the PTY via `sess.writeBytes()`. The scroll is quantized in 40px steps (`MOUSE_WHEEL_STEP_PX`) and the remainder carries over to the next event (same accumulator scheme as `scrollAccumDy`), so a long swipe produces `abs(dy) / stepPx` notches. `onFling` becomes a no-op while `mouseEnabled` is true so inertial scroll cannot accidentally page through content or drive the scrollback. Mouse click delivery (`sendMouseClick`) is unchanged. Behaviour on tabs where `mouseEnabled = false` is fully preserved.
- **z2root `/proc/<pid>/stat` field 2 also rewritten to the argv0 basename (0.8.113)**: 0.8.112 fixed `comm` and `status:Name`, but busybox/procps `ps` reads `/proc/<pid>/stat` in one shot for speed, and field 2 `(<comm>)` was still leaking the kernel-set `(libz2root.so)` (so `ps -ef` showed `{libz2root.so} <real argv>` with a stale label). A new `PROC_FD_STAT` kind covers `/proc/<pid>/stat` and `/proc/<pid>/task/<tid>/stat` (the global `/proc/stat` is excluded). `fake_stat_comm` rewrites the parenthesized field length-preservingly, using the last `") "` on the line as the right boundary (the comm may contain `(`/`)`).
- **z2root `/proc/<pid>/cmdline`, `comm`, and `status:Name` restored from the loader leak (0.8.112)**: due to Android's W^X, z2root has to `execve(libz2root.so)` and route through a loader wrapper (`z2root --loader-noreloc <ld.so> <ld.so> --argv0 <argv0> <prog> ...`), so the kernel records the wrapper argv into `/proc/<pid>/cmdline` and `libz2root.so` into `comm`/`status:Name`. As a result `ps -ef` / `pgrep <name>` / `pidof` / `top` break across the whole guest (proot escapes this because it relies on PT_INTERP through the rootfs `ld.so`, so the kernel records the original argv). **Fix**: at execve intercept time, the original argv (and basename of guest_prog) is recorded per-tracee. Two new PROC_FD kinds (`CMDLINE`/`COMM`) feed into the existing openat-time temp substitution path (readfree default). The `status:Name` line is rewritten length-preservingly to the argv0 basename via a new `fake_status_name` next to `fake_status_buf`. fork/clone inherits the recording from the parent and a successful execve overwrites it. The non-readfree (`Z2ROOT_NO_READFREE=1`) `fake_proc_on_read` covers the same kinds (with `regs[0]` adjustment because cmdline/comm can change length).
- **z2root `/proc/self/exe` rewritten to the guest view (0.8.111)**: the kernel's `/proc/<tid>/exe` symlink points at `libz2root.so` (or our own loader) because of how execve is staged, so guests that `readlink("/proc/self/exe")` got the host path and `open("/proc/self/exe")` failed with `ENOENT`. **Symptom**: Go's runtime can't open `/proc/self/exe` for libbacktrace during startup and panics immediately with `libbacktrace could not find executable to open` (both `go version` and `go build` fail to run). The same path breaks adb's `execl(own-path)` family and `--daemonize` self re-exec. proot hijacked these long ago — only z2root regressed. **Fix**: at execve(at) / bootstrap exec time we record the guest-side absolute program path per-tracee, and `host_path_for` substitutes it whenever a path resolves to `/proc/<own pid>/exe`; the `readlinkat` exit returns the same. fork/clone inherits the recording from the parent. Same approach as the earlier `/proc/self/cwd` reverse-translation (0.8.60, which fixed Claude Code's startup).
- **Wrapped URL detection**: the wrapped flag goes on the "wrap-origin row", not the "continuation row" (reversed, long URLs become untappable).

---

## 11. Glossary

| Term | Meaning |
|---|---|
| PRoot | A userspace tool that achieves chroot/bind/fakeroot without root |
| rootfs | The complete root filesystem of a Linux distro |
| PTY | Pseudo-terminal. The I/O path between app ↔ shell |
| forkpty | A libc function that forks while creating a PTY |
| SAF | Storage Access Framework. Android's mechanism for opening files from other apps |
| dropbear | A lightweight SSH server/client that runs under proot |
| SGR | Select Graphic Rendition. ANSI control for text color/decoration |
| EAW | East Asian Width. The wide/narrow character-width classification |
| Shared home | `filesDir/shared_home`. The single `/root` backing shared across all distros |
