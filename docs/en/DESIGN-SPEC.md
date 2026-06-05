# Z2Term — Design & Specification

Last updated: 2026-06-05 / Target version: 0.8.22-alpha (versionCode 30)

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
- **Execution engine**: PRoot by default. A hidden setting (tap the version 7×) can switch to **"z2root", our own no-root ptrace engine** (experimental). On rooted devices, when the root self-test passes, a **"real chroot" engine** also becomes selectable (`su`-based bind mounts + `chroot`; `executionEngine`).

Supported ABI is **arm64-v8a only**. Minimum Android 10 (API 29), target API 35.

### Distribution flavors

| Flavor | applicationId | Purpose |
|---|---|---|
| `full` | `com.zerotoship.z2term` | Normal distribution (rootfs/proot bundled; offline first run) |
| `foss` | `com.zerotoship.z2term.foss` | Minimizes third-party license notices. Alpine rootfs excluded from the APK and downloaded at runtime (proot/talloc stay bundled; no offline first run) |

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
| SSH client | JSch (mwiede fork) | 0.2.21 (BouncyCastle not required) |
| Decompression | org.tukaani:xz | 1.10 (the downloaded distro's `.tar.xz`). gzip is JDK standard |
| Linux runtime | PRoot + libtalloc | `.so` bundled in jniLibs (from a Termux build) |
| Bundled OS | Alpine Linux ARM minirootfs | full bundles `.tgz` under `src/full/assets`. foss excludes it and downloads from the official CDN at runtime |

---

## 3. Overall architecture

```
┌───────────────────────────── UI layer (Compose) ─────────────────────────┐
│ MainActivity → TerminalScreen                                              │
│  ├ TopBar (paste/📋/🔌/あ(IME)/⚙) ├ TabBar ├ TerminalRenderer(Canvas)      │
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
- The **GUI desktop** launches as a separate Activity (`GuiActivity`) and connects to the in-distro Xvnc with the built-in RFB client ([§4.12](#412-gui-desktop-gui)). The execution engine defaults to PRoot, with z2root (no-root, experimental) and chroot (rooted devices) selectable via a hidden setting ([§4.3](#43-proot-execution-prootprootlauncherkt-prootsshdscriptkt)).

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

- The binary is `nativeLibraryDir/libproot.so` (+ `libproot_loader.so`). `libtalloc.so` is extracted as `libtalloc.so.2` per its SONAME and put on `LD_LIBRARY_PATH`.
- `launch(distroId, command, rows, cols, fallbackShell)` assembles proot arguments and calls `PtyProcess.create`:
  - `--kill-on-exit -0 --link2symlink -r <rootfs> -b /dev -b /proc -b /sys -b <shared_home>:/root`
  - **External storage bind**: `/storage/emulated/0:/sdcard`, `getExternalFilesDir:/storage/app`
  - `-w /root`, env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 PATH=… TMPDIR=/tmp` + history-related env.
- **Shared home**: `filesDir/shared_home` is bound to `/root` across all distros (← the real backing of the terminal's `~`).
- `resolveShell`: if the specified shell isn't in the rootfs, falls back to `defaultShell → /bin/sh` (usrmerge aware).
- `isDistroReady`: checks the actual presence of `bin/busybox|bin/bash` etc. + a `.z2term-version` marker (compares `ROOTFS_VERSION` for bundled distros only).
- Idempotently injected on every launch: `ensureShellHistoryConfig` (history rc), `ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear wrapper), `ensureOsc7CwdConfig` (OSC7 hook for cwd restore), `ensureZ2ApiScripts` (`z2-*` bridge), GUI/z2run scripts.
- `launchAndroidSh`: fallback when proot isn't possible (`/system/bin/sh` + minimal mkshrc).

**Execution engine z2root (hidden feature, no root, experimental)**: when `executionEngine = "z2root"`, `launch()` swaps the binary to `nativeLibraryDir/libz2root.so` (our own ptrace engine). It accepts a proot-compatible argv subset, so the args/env are reused as-is (`PROOT_*`/talloc are ignored by z2root). If `libz2root.so` is not bundled (`scripts/build-z2root.sh` not run), it falls back to PRoot. Path translation is hardened to be proot-equivalent (canonicalization of in-path symlinks / absolutizing cwd-relative paths via `/proc/<tid>/cwd` / leaving `dirfd`-relative paths untranslated / two-pass translation for `renameat2`/`linkat`/`symlinkat` / execve loader swap and `#!` shebang resolution). This is the concrete deliverable of phase 2 (zeroing FOSS third-party notices); see `docs/FOSS-PURE-HANDOFF.md` §5.

**Execution engine chroot (hidden feature, requires root)**: when `executionEngine = "chroot"`, `launchChroot()` is used.

- **Unlocking the selector**: tap the version 7 times → `engineSelectorUnlocked=true` (works without root; proot / z2root become selectable). If `probeRootChroot()` then passes, `rootChrootUnlocked=true` is also set and chroot joins the options.
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
  - Character width: East Asian Width aware (the `ambiguousAsWide` setting makes ambiguous width 2 cells). Surrogate pairs supported.
  - SGR: bold/underline/inverse/strikethrough, 16/256/RGB (truecolor).
  - DEC modes: alternate screen, cursor keys (DECCKM), **mouse reporting** (X10/Normal/Button/Any × Legacy/SGR/urxvt).
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
  - `StateFlow`: uiState / redrawTick (≈60fps coalescing) / scrollOffset / cellMetrics / selection / cwd / label / settingsFlow.
- `TerminalSelection` / `CellMetrics`: selection range (absolute rows) and 1-cell dimensions.
- `SessionStore`/`SessionManager` (M11): saves tab layout `{id,label,distro,cwd}` + activeId to DataStore, restoring the tab layout (including order) after an OS kill and restart (GUI tabs excluded). Each tab launches with a new PTY. **cwd is captured via OSC7** (`ensureOsc7CwdConfig` makes bash/zsh emit OSC7 in the prompt hook), but **automatic `cd <cwd>` injection on launch was removed in 0.8.13** (to avoid unintended moves, restored tabs also launch at the shell's default cwd). "Stop from the notification" saves empty and does not restore.

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
  ESC  あ   か  さ   ⌫
  ◀   た   な  は   ▶
  ␣    ま   や  ら   変換
  ABC  小゛゜ わ  、。  ⏎    ← ABC = back to Latin
  ```
- Flick rules: tap = あ row / left = い / up = う / right = え / down = お.
- **Dakuten key (小゛゜)**: cycles the previous kana through dakuten→handakuten→small→original (the cycle table is hiragana-based). Repeated kana aren't cycled but stack naturally ("つつ" doesn't become "っ").
- **⌫**: flick left = delete word (Ctrl+W) / flick right = delete to line start (Ctrl+U).
- The long vowel `ー` is the right flick of `わ`. Katakana has no dedicated key; it's chosen from katakana candidates in the candidate bar (§6.2.1).

#### 6.2.1 Kana-kanji conversion (`KanaKanjiConverter` / `ComposingState`)

A best-effort conversion that binary-searches an SKK dictionary (`assets/z2dict.txt`, ~160k lines) + conjugation completion for common verbs/adjectives. The candidate bar (`CandidateBar`) updates on every keystroke.

- **Candidate generation (`convertFlexible`)**: learning history (exact match) → exact match (`convert`) → okurigana conjugation (`okuriForms`) → bunsetsu-split synthesis (`segment`) → learning history (prefix match) → prefix-match prediction (`predict`). Raw kana / katakana always remain as confirmed candidates.
- **Learning history** (`ImeHistoryStore`): ranks confirmed words by frequency and recency (last 7 days) and surfaces them near the top.
- **Bunsetsu-split synthesis (`segment`)**: joins a content word (longest dictionary match) + following particles/okurigana into one bunsetsu (e.g. きょうの → 今日の). **Particles** (の/は/が…) and **sentence-ending auxiliaries** (でしょう/ました/です…) have single-kanji entries (野/葉/増田…), so they are **left in kana** (`PARTICLES` / `AUX_KANA`). Returned when there is ≥1 dictionary-hit bunsetsu ∧ it contains kanji.
- **Split conversion**: the convert key (or ◀▶) focuses the leading bunsetsu (`autoSplitHeadLen` = takes the content word + following particle as a bunsetsu). ◀▶ expands/shrinks the block range; tapping a candidate / ⏎ confirms and auto-advances to the next block. Repeating the convert key cycles candidates.
- **Automatic block splitting for long sentences**: long sentences whose dictionary block splits into ≥2 bunsetsu auto-split to the leading bunsetsu and predict per block without pressing the convert key (decided by `segmentParts`). e.g. あしたのてんきは… → 明日の / 天気は / …. A single in-progress word isn't split.
- **Whole-sentence batch prediction** (`fullPrediction`): when a tail remains during split, a single "whole-sentence" candidate is shown as a light-green pill, concatenating the **leading block's top candidate (= head of `candidates`) + the Viterbi 1-best of the remaining kana**. Tapping it (`commitFull`) confirms the whole sentence at once. **When `splitHeadLen` moves with ◀▶, it is rebuilt via `refreshPredict`, re-flowing to follow the boundary change** (0.8.16). The remaining-kana Viterbi uses the leading surface form as context for bigram re-ranking. Note: the old "Viterbi 1-best over the whole reading (boundary-independent)" didn't move the light-green text with ◀▶, so it was replaced in 0.8.16. The old "bunsetsu rearrangement variations (`multiSegmentVariants`)" produced mostly unused candidates and were removed in 0.8.4.
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
| Keep-alive service | keepAliveService | true | true/false |
| Execution engine (hidden) | executionEngine | "proot" | proot / z2root / chroot (chroot only when root is unlocked) |
| Engine selector unlock (hidden) | engineSelectorUnlocked | false | true after tapping the version 7 times (no root needed) |
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
./gradlew :app:assembleFullDebug      # APK (full = rootfs bundled)
./gradlew :app:assembleFossDebug      # APK (foss = rootfs excluded, runtime DL)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

- full bundle: `jniLibs/arm64-v8a/{libproot,libproot_loader,libtalloc}.so` (both flavors), `src/full/assets/alpine-minirootfs-aarch64.tgz` (full only), `assets/fonts/*.ttf` (shared).
- foss excludes the rootfs and fetches it at startup via `DistroSpec.ALPINE`'s official CDN URL + SHA-256 (`DistroSpec.bundledInApk` returns false). proot/talloc must stay bundled due to the W^X constraint.
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
- **GUI audio**: PulseAudio must start with the `-n` method or it conflicts with existing config. Passing `AudioBridge`'s target port as 0 yields silence (specify the default port explicitly).
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
