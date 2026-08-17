# Z2Term — Zero 2 Terminal

**English** ・ [日本語](README.ja.md)

[![Release](https://img.shields.io/github/v/release/orgsonai/z2term?include_prereleases&label=release)](https://github.com/orgsonai/z2term/releases/latest)
[![License](https://img.shields.io/github/license/orgsonai/z2term)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84)

### A terminal for Android that is also a way to drive Android.

Z2Term runs Alpine / Ubuntu / Arch / Kali **without root**, opens a **Linux desktop** in a tab,
and lets the shell reach the phone itself — read a notification, ask you a question in the
notification shade, or run a script when the battery, the network or an incoming SMS changes.

- **No root, no PC, no setup script.** Install one APK and you have a working distribution with `apk` / `apt` / `pacman`. The default execution engine (`z2root`) is a ptrace-based userspace implementation written for this app.
- **The shell can talk to Android.** Around 20 `z2-*` helpers (notifications, clipboard, sensors, torch, intents, alarms, wireless `adb` to the device itself) plus `z2-when` — an automation hub that runs scripts on device events and keeps working after a reboot without the app being opened.
- **Usable in Japanese.** A built-in kana-kanji IME with prediction and learning, which can also be turned on as an OS-wide input method.

> The 5th project of the Zero to Ship initiative.

## Screenshots

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/images/cui-terminal.png" width="280" alt="CUI: Alpine terminal with the custom keyboard"><br><sub>CUI — Alpine terminal + custom keyboard</sub></td>
    <td align="center" width="50%"><img src="docs/images/gui-thunderbird.png" width="280" alt="GUI: Thunderbird running on Xvnc"><br><sub>GUI — Thunderbird running on Xvnc</sub></td>
  </tr>
</table>

## Why another terminal app?

Android already has a mature terminal ecosystem, and Termux is the right answer if what you
want is the largest package repository and the most battle-tested tooling. Z2Term is built
around a different question: **what if the terminal were a first-class way to operate the
phone, and everything came in one APK?**

| | Z2Term | Termux + its add-ons |
|---|---|---|
| Full Linux distribution | Alpine / Ubuntu / Arch / Kali on z2root | `proot-distro` package installs one |
| Linux GUI | Built-in GUI tab (Xvnc + an RFB client inside the app), with audio and video | A separate X11 or VNC viewer app |
| Drive Android from the shell | Built in — ~20 `z2-*` helpers | A separate companion app |
| Event-driven automation | Built in — `z2-when` (charging, battery level, time / cron, Wi-Fi, connectivity, boot, share, SMS, sensors, notifications, new files) with an Automation tab, logs and a kill switch | A separate companion app, usually paired with a third-party automation app |
| Japanese input | Built-in IME (conversion, prediction, learning), also selectable as the OS input method | The OS keyboard |
| SSH / SFTP client and `sshd` | Built in, keys held by the Android Keystore | Install the packages yourself |
| Distribution | GitHub Releases (this repo) | F-Droid and its own repository |

Both are GPL-3.0 and neither collects telemetry. If you already have a Termux setup you are
happy with, Z2Term's reason to exist is the second, third and fourth rows of that table.

## Download

**You can download the latest APK directly from GitHub Releases** (no build needed):

- Go to the latest: **<https://github.com/orgsonai/z2term/releases/latest>**

Every release ships two APKs.

| File | Contents | Which to pick |
|---|---|---|
| `app-foss-release.apk` | No prebuilts (~21MB) | **The recommended one.** Nine times smaller, and each update downloads ~21MB instead of ~190MB — handy on metered data or a slow link. It also bundles no third-party prebuilts (fewer license notices). No OS is bundled, so **the first launch asks you to pick one in Settings › Linux environment** (0.8.314; Alpine is fetched from the official CDN and verified by SHA-256, and updates never re-download it). |
| `app-full-release.apk` | Same payload as foss | Kept so existing full users can update under the same package ID. |

Same app either way; the feature sets are identical. The only difference is **whether an OS is
downloaded once on first launch**, so if you have a connection that first time, `foss` costs you
nothing (and it lets you **choose which OS to start from**, at the cost of one extra tap). Note that automatic updates (below) still fetch the whole APK each time — there are no
delta updates outside Google Play — so the size gap keeps paying off on every update.

Tap the APK on your Android device → allow "Install from unknown sources" to install.
(Not distributed on Google Play.)

### Keeping it updated

Pick whichever fits:

- **In-app check** — *Settings → App info → Check for updates* asks GitHub for the latest release
  **only when you tap the button** (nothing is checked automatically, and no network is touched until
  then). If a newer version exists it shows the number and opens the release page for you; downloading
  and installing the APK stays a manual step.
- **Manual** — download the newer APK from Releases and tap it (installs over the top; your data stays).
- **Automatic** — add `https://github.com/orgsonai/z2term` to
  [Obtainium](https://github.com/ImranR98/Obtainium). It watches these Releases and updates the app
  with one tap when a new version appears — no app store involved. With the recommended `foss` APK,
  each such update is only ~21MB.

## Current version

**0.8.357-alpha (versionCode 365).** The latest APKs and the full release history live on **[GitHub Releases](https://github.com/orgsonai/z2term/releases)**.

## Features

- **Terminal emulator** — VT100 / xterm, 256-color and true color, 9 themes, scrollback with search, UTF-8 and East Asian Width, alternate screen, OSC 4 / 7 / 8 / 10 / 11 / 12 / 52.
- **Linux distributions without root** — Alpine / Ubuntu / Arch / Kali on a userspace engine (z2root by default; see below). Install anything with `apk` / `apt` / `pacman`.
- **Execution engine** — fully migrated to z2root for non-root use; rooted devices may optionally use the hidden chroot path.
- **Multi-tab** — CUI and GUI tabs, drag to reorder, long-press a tab to see the engine it runs on. An inactive tab shows a small dot while something is running in it, and a ✓ when it finished while you were looking elsewhere.
- **Linux GUI** — Xvnc + openbox with a built-in RFB client; `z2gui` starts a desktop and `z2run <app>` launches a GUI app (opening the GUI tab for you), with audio and video.
- **SSH / SFTP** — public-key auth (**create an ed25519 key in the app, then copy/share the public key or add it to this device's sshd**; secrets encrypted by the Android Keystore), known_hosts confirmation, file transfer, port forwarding in both directions (`-L` / `-R`) that can **keep running after the SSH tab is closed**, and a built-in `sshd` (dropbear) that binds to localhost only by default.
- **English / Japanese throughout** — the in-app UI *and* the `z2-*` command-line helpers follow the language setting, so the help text, usage lines and messages you get in the terminal are localized too.
- **Japanese IME** — Viterbi kana-kanji conversion, prediction, frequency/recency learning, and a custom on-screen keyboard. It can also be **offered as an OS input method**, so once enabled the same keyboard and conversion work in the app's own text fields and in other apps (switching is the OS keyboard switcher). **Your own words can be added from a file** (SKK format: `reading /candidate/`), so names and private abbreviations convert from the first keystroke.
- **Android bridge** — call host features from the shell: `z2-noti` (read the notifications on screen; read-only) / `z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-clip` / `z2-battery` / `z2-vibrate` / `z2-say` / `z2-torch` / `z2-media` / `z2-volume` / `z2-sensor` / `z2-intent` / `z2-state` / `z2-screen` (stop the screen turning off by itself, for a while) / `z2-tile` (put a macro on a quick-settings tile; 12 slots) / `z2-icon` (draw the status-bar and tile icons yourself) / `z2-alarm` / `z2-macro` / `z2-session` (drives the app's own tabs) / `z2-server` (start/stop a registered resident server).
- **It can ask you things** — `name=$(z2-ask "Branch name?")` takes the answer from a **notification reply field** (answerable from the shade without opening the app; no answer means a non-zero exit, so "give up" is expressible).
- **Automation hub** — `z2-when <trigger> run <cmd>` auto-runs a script on Android events: charge start/stop, battery crossing a level, a time (daily / once / every N / cron), Wi‑Fi connect/disconnect, **a usable connection appearing/going away or the link in use switching (`net:online` / `net:mobile` — mobile data counts too)**, **the device booting (`boot`)**, **something being shared to it from another app (`share:ext=pdf` …)**, an incoming SMS (incl. OTP code extraction), a sensor (shake / light threshold / proximity), **an arriving notification (`notify:otp` extracts the code; independent of whether notifications are logged)**, **any device event by name (`event:headset_plugged` and ~20 more; `z2-when events` lists them)**, or **a new file appearing in a folder (`file:new=…`)**. Rules can be **narrowed with filters** (`if=ssid=Home` / `cooldown=1h` / `between=22:00-07:00` / `days=mon-fri` — they work the same for every trigger, and skipped runs stay in the log as `skip:`). Rules are plain text under `~/.z2term/when/` (git-syncable) and survive reboots without opening the app. The **Automation tab** (📜) lists them with on/off toggles, run logs and a **▶ run-now** that skips the trigger, plus a **kill switch that pauses every rule** and a list of recent fires (`z2-when pause` / `resume` / `fired` in the terminal).
- **Resident servers** — register any start command under *Settings → Resident servers* and it keeps running in the background **without the app being open**: host a small web server, a sync daemon or a bot from the phone. The Status widget shows how many are up, and registered servers are restarted on boot.
- **Self adb** — `z2adb` connects to the device's own wireless debugging over localhost. No PC, USB, or root.
- **Built-in help** — `z2help` (or `z2term`) prints a categorized cheat sheet of every `z2*` helper, and every command explains itself with `--help` (e.g. `z2-tile --help`); `z2version` shows the app version and the engine the tab is really running on.
- **`z2doctor`** — one command that answers "why isn't it working?": version, engine, free space, every permission the app needs, detection and automation state. **Each `NG` line carries the next step**, and the end is a short report you can paste into an issue (`--share` / `--clip`). SSIDs, IPs and host names are left out on purpose.
- **Vulnerability testing** — `z2scan self` audits this device/localhost (open ports, sshd config, SSH key perms, world-writable/SUID, PATH) with no external tools; `z2scan net/host/cve` wrap nmap/lynis/trivy on localhost (a remote target requires explicit opt-in). Results stay local.
- **Terminal log** — tap ⚪ in the toolbar once to start writing what the tab shows to a text file, tap again to stop. Files land in `~/z2term-log/`, so they open straight from the shell or from other apps. By default colors and screen control codes are stripped so the result reads as plain text.
- **Home screen widgets** — *Status & launcher*: shows the state as running/registered counts (ssh endpoint / resident servers / automation rules / battery) and runs a macro you picked **in the background with one tap, without opening the app**; tap a running macro again to stop it. *Live tail*: keeps the last or the first lines of any file under `~` on your home screen (`tail` or `head`, your choice).
- **Take it with you** — bundle settings, SSH connections, snippets, automation rules and macros into one file and restore them on another device. The OS image is excluded. **SSH secrets are left out by default; including them requires a passphrase.**
- **First-run cards** — three small cards on the first launch (post a notification / flashlight / let a PC connect). Tapping one **puts the command on the input line — it never runs by itself**; they disappear once tapped and never return.
- **Receive from Share** — pick z2term in another app's share sheet and the text (or, for files, a path under `~/z2term-inbox/`) is **inserted** on the terminal's input line — never executed.
- **Tidy toolbar** — choose which buttons appear from settings (⚙ settings stays pinned to the right edge); long-press and drag to reorder.
- **FOSS flavor (the recommended download)** — bundles no third-party prebuilts (~21MB); the distribution is downloaded at first launch and verified by SHA-256.

### Not yet supported / under consideration

- mosh protocol support (UDP-based)
- Reverse DNS / stronger IPv6 connection retry
- Fully self-contained z2root engine with no third-party native prebuilts
- IME learning-history export / backup (the reset UI is implemented)

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

### 1. Collect the git-ignored bundled artifacts (one command)

Several artifacts are bundled into the APK but **kept out of git** (built/fetched by `scripts/`), so a fresh `clone` or a `clean` has none of them. Collect them all with a single master script — this is the only way to gather them, so every machine (PC or phone) ends up with the same set rather than each assembling a different mix:

```bash
bash scripts/build-bundle.sh
```

It runs the two generators and verifies the common payload:

1. `build-z2root.sh` → `libz2root.so` / `libz2accept.so` (needs an NDK)
2. `fetch-fonts.sh` → `IBMPlexMono` / `JetBrainsMono` / `FiraCode` `-Regular.ttf`

A final manifest step prints `OK` / `MISS` per artifact. Linux rootfs archives are downloaded at runtime and are never APK build inputs.

Per-artifact details: [app/src/main/assets/README.md](app/src/main/assets/README.md) · [app/src/main/jniLibs/README.md](app/src/main/jniLibs/README.md)

### 2. Build

```bash
./gradlew assembleFossRelease
# Output: app/build/outputs/apk/foss/release/app-foss-release.apk

./gradlew assembleFullRelease
# Output: app/build/outputs/apk/full/release/app-full-release.apk
```

(No signing key required for forks — `build.gradle.kts` falls back to the debug key when `keystore.properties` is absent.)

### 3. Install

```bash
adb install -r app/build/outputs/apk/foss/release/app-foss-release.apk
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
│       │   ├── proot/               ← Linux launch (legacy package name)
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
│       ├── jniLibs/                 ← generated z2root binaries
│       └── res/                     ← resources
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── docs/
│   ├── ja/                        ← Japanese documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification (technical)
│   │   ├── HANDBOOK.md            ← user handbook
│   │   └── MACRO-GUIDE.md         ← phone-automation macro guide (AI-friendly)
│   ├── en/                        ← English documentation
│   │   ├── DESIGN-SPEC.md         ← design & specification
│   │   ├── HANDBOOK.md            ← getting started handbook
│   │   └── MACRO-GUIDE.md         ← phone-automation macro guide (AI-friendly)
│   ├── images/                    ← screenshots etc. (shared)
│   ├── RELEASE.md                 ← release steps
│   └── SSH-INTO-Z2TERM.md
├── metadata/                     ← F-Droid metadata
└── .github/workflows/build.yml   ← CI (builds both full + foss)
```

## Build variants

| Flavor | Purpose | Bundled content |
|---|---|---|
| `foss` | **The distribution default (recommended)** | z2root; rootfs downloaded at runtime |
| `full` | Existing-install upgrade compatibility | Same payload as `foss`; rootfs downloaded at runtime |

Only `foss` carries the `.foss` `applicationId` suffix (`com.zerotoship.z2term.foss`), so both can be installed side by side.
⚠ **Both show the same launcher name, "Z2Term"** (0.8.315 — the distribution flavor does not belong in the app's name).
With both installed the name no longer tells them apart; use `z2version` or the version in app info instead
(`foss` ends in `-foss`). Only debug builds keep a separate name (`Z2Term dbg2`).

```bash
./gradlew assembleFossDebug
./gradlew assembleFullDebug   # identical payload, different applicationId
```

## Smoke-test flow

1. Build and install either flavor; select an OS and complete the runtime download.
2. Confirm `z2version` reports `engine : z2root`, then test the distro package manager.

### z2root command-group test (`scripts/z2root-cmdtest.sh`)

A regression smoke test for confirming that **fragile commands keep working
going forward** on the z2root engine — focused on the hard paths (ptrace/seccomp,
fakeroot fakery, path translation, `/proc` fakery, pty, heavy fork/exec, ld.so
reloc), not trivial `cd`/`ls`. The point is to catch a systemic z2root regression
as "many commands fail at once" instead of discovering each broken command later.
Run it inside a started guest (any distro) on the z2root tab:

```sh
sh scripts/z2root-cmdtest.sh              # standard (incl. network/build)
SKIP_NET=1   sh scripts/z2root-cmdtest.sh # skip network/package steps
SKIP_BUILD=1 sh scripts/z2root-cmdtest.sh # skip cc compile etc.
RUN_SSHD=1   sh scripts/z2root-cmdtest.sh # dropbear loopback ssh (may reset the session under z2root!)
RUN_PRIV=1   sh scripts/z2root-cmdtest.sh # also run truly-root ops (losetup/mount); EPERM is normal on non-root z2root
```

It is POSIX `sh` / busybox-ash compatible and **skips missing commands rather than
failing**, so it runs identically across distros — run it on each guest and a
healthy engine yields an empty "non-zero exit" summary everywhere. 10 groups:
① runtime real-launch (`claude` headless vs `--version`, node spawn, python
venv/multiprocessing/ssl, ripgrep), ② heavy VCS (git clone/gc/checkout =
hardlink/pack/rename), ③ package managers (apt/apk/dnf/pacman, pip/venv, npm =
fakeroot/fork-exec/symlink), ④ pty/terminal (script/tmux/stty, `/dev/pts`,
optional dropbear loopback), ⑤ `/proc`/fakeroot boundary, ⑥ build (cc execve
chain + ld.so reloc), ⑦ path translation / symlink canonicalization, ⑧ disk/FS
(dd, mkfs, parted on file images; root ops behind `RUN_PRIV`), ⑨ IPC / special
syscalls (AF_UNIX, FIFO, flock, inotify, xattr, copy_file_range, nested ptrace
via strace/gdb, Go raw syscalls, sqlite3, rsync), ⑩ name resolution / TLS
(getent, curl TLS, nslookup). Output goes to the screen and
`/tmp/z2root-cmdtest-<timestamp>.log`; a trailing summary lists any non-zero
exits.

Note: `io_uring` (bypasses ptrace/seccomp entirely) and `statx`/`openat2` hook
gaps can't be caught by command tests — verify those at the seccomp-filter level.

## License

The license of the app itself (`app/src/main/java/com/zerotoship/z2term/**`) is **GPL-3.0**.
Copyright (c) 2026 Zero to Ship. Corresponding source (GPL v3 §6): <https://github.com/orgsonai/z2term> (full text in the root `LICENSE`).
Bundled third-party notices are available from Settings → OSS licenses.

## Bundled OSS and corresponding source

| Bundled item | License | How to get the corresponding source |
|---|---|---|
| Fira Code / IBM Plex Mono / JetBrains Mono | OFL-1.1 | [tonsky/FiraCode](https://github.com/tonsky/FiraCode) / [IBM/plex](https://github.com/IBM/plex) / [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |

From the settings screen → "OSS licenses / corresponding source", you can also browse/show this in-app
(license full texts are placed in `assets/licenses/`).

## Distribution policy

| Channel | Flavor | Status |
|---|---|---|
| **GitHub Releases / direct APK** | `foss` (**recommended**) / `full` | Primary channel; payloads are identical |
| **F-Droid** | `foss` | Runtime-downloaded rootfs; fully source-built engine |
| **Google Play** | — | No distribution planned |

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
- z2root — source-built userspace Linux engine included in this repository
- [Alpine Linux](https://alpinelinux.org/) - main distro
