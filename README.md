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

**0.8.42-alpha (versionCode 50) — three fixes. (1) z2root SSH logins (dropbear) no longer reset right after auth. Following the 0.8.41 `ttyname` fix, dropbear got further but still dropped the connection ("Connection reset by peer" / "Broken pipe") immediately after a successful key auth: while opening the SSH PTY it `chmod`s the slave (`chmod(/dev/pts/N, 0620)`), but an untrusted_app can't change the mode of a host-owned pts node, so the call returned `EPERM` and dropbear aborted the session. Fix: under fakeroot (`-0`), also fake `fchmod(52)` / `fchmodat(53)` `EPERM`→success (joining the existing `set*id`/`chown` fakes) — root never sees the `chmod` fail, so the session proceeds. (2) Terminal long-press magnifier is now positioned with the 4-arg `Magnifier.show()` at a fixed offset above the finger, instead of the OEM-default placement that landed inconsistently and was often hidden under the finger. (3) Long-press text selection works on a freshly opened tab without first pinch-zooming: the `LaunchedEffect` that publishes cell metrics now keys on `session.id`, so a new tab of the same dimensions re-runs it instead of leaving `cellMetrics` at its zero default (which made `pixelToAbsCell()` return null and aborted selection). SSH end-to-end confirmation still needs a real device — the dev environment is itself inside a proot sandbox, so z2root's ptrace becomes a double-ptrace and the PTY path doesn't reproduce. (`claude` hangs and GUI audio under z2root remain under investigation and need device-side logs.)**

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
