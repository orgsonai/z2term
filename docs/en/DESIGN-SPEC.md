# Z2Term — Design & Specification

Last updated: 2026-08-25 / Target version: 0.8.399-alpha (versionCode 407)

> This is the technical document covering Z2Term's **detailed design + specification**, aimed at implementers and reviewers.
> For a friendly user-facing guide, see `docs/en/HANDBOOK.md`.
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
11. [l2s constraint and native passthrough](#11-l2s-constraint-and-native-passthrough)
12. [Glossary](#12-glossary)

---

## 1. Overview

**Z2Term** is a custom-built terminal emulator + Linux runtime that runs standalone on Android.

- **No root required**: `forkpty(3)` plus the source-built **z2root** userspace runtime runs Alpine, Ubuntu, Arch, or Kali inside a normal-privilege app.
- **Own terminal emulator**: xterm-compatible VT/ANSI interpretation implemented in Kotlin.
- **Own UI / keyboard**: Jetpack Compose. A custom flick keyboard (Latin + Japanese/katakana + numbers) that can switch with the OS IME.
- **Bidirectional SSH**: from the terminal to the outside (JSch client), and from a PC into the terminal (dropbear server).
- **File integration**: SAF DocumentsProvider lets other apps R/W the rootfs/home; Linux sessions can `cd` into Android shared storage.
- **GUI desktop**: inside the distro, Xvnc + a lightweight WM/app launches and is displayed by the built-in RFB (VNC) client (`gui/` package). Video uses software rendering; audio is an opt-in PulseAudio→TCP→AudioTrack bridge (`AudioBridge`).
- **Execution engine**: **z2root only**. Rooted devices may optionally select a real `chroot` through the hidden setting.
  - **Unlocking the hidden setting**: tap the version row in Settings → App info 7 times. For 3 seconds after the toggle fires the version row is made **untappable**, so rapid taps cannot immediately flip it back (0.8.70; it previously accepted taps and ignored them, which felt broken)
  - **Unlocking chroot**: it only joins the choices when the root self-test (`probeRootChroot`) succeeds. That test can be re-run **not just at the moment of the 7-tap unlock but from the "Enable chroot (check root)" button inside the engine selector** (0.8.106). Previously it ran once at unlock time, so declining the su permission dialog left `rootChrootUnlocked` false and chroot permanently unselectable (re-unlocking required a re-lock followed by another 7 taps — undiscoverable). While it is false, the button and an explanatory note are shown and can be retried any number of times (success unlocks chroot with a toast; only a button-initiated failure toasts the reason)
  - **Distinguishing failures** (0.8.107): `RootProbe.NoRoot` (no su / denied) and `RootProbe.ChrootBlocked(detail)` (root obtained but the chroot itself failed on SELinux, the rootfs, …) are reported separately
  - ⚠️ **Root managers such as Magisk remember a "deny" and from then on return an immediate denial without showing the su dialog again, so the in-app button alone cannot recover** (an app cannot change another app's root grant). The NoRoot toast and note direct the user to set Z2Term back to "allow" in Magisk (0.8.108)
  - **0.8.328 complete migration**: removed the PRoot selector, fallback, prebuilts, and bundled Alpine archive. Both flavors use z2root and runtime rootfs downloads. **0.8.359 dropped the flavors entirely** (below).
  - **z2root trace log** (developer-only, default OFF, `traceLogEnabled`): a toggle inside the same 7-tap unlock. When ON, every z2root syscall is recorded to `shared_home/z2root_trace.log` — useful for diagnosis, but the log grows enormous and fills device storage quickly, so the UI carries a "leave this OFF normally" warning (0.8.105; 0.8.107 reworded it from the self-contradictory "do not use with it left OFF"). It used to be switchable only through the `.z2root_trace_on` sentinel file (still honoured for backwards compatibility)

Supported ABI is **arm64-v8a only**. Minimum Android 10 (API 29), target API 35.

### Distribution

There is **one build**. The applicationId is `com.zerotoship.z2term` and the launcher name is `Z2Term`.
z2root is built from source and the rootfs is never bundled — it is downloaded at runtime.

⚠ **0.8.359 dropped the distribution flavors.** Until then there were two: `full`
(`com.zerotoship.z2term`, Alpine rootfs bundled, ~190MB) and `foss`
(`com.zerotoship.z2term.foss`, runtime download, ~21MB). They were dropped because **the name "full"
read as the better one, so that is what everyone downloaded, while the only real difference was
whether the first download was skipped** (user's call). The surviving applicationId is the one
without a suffix — `.foss` existed solely to let the two live side by side, so it went with them.
⚠ **Anyone on `com.zerotoship.z2term.foss` is a separate app to Android and will not be updated
automatically**; they have to reinstall. (Downloads were still negligible at that point, and merging
the two IDs only gets harder later — hence doing it early.)

`debug` builds add a `.debug2` suffix and show as `Z2Term dbg2`, so they can sit next to a release build.

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
| Linux runtime | z2root | Built from `app/src/main/cpp/z2root` |
| Linux OS | Alpine / Ubuntu / Arch / Kali | Not bundled; official archives are downloaded at runtime |

---

## 3. Overall architecture

### 3.1 Layer structure

```text
+--- UI layer (Compose) -------------------------------------------------+
| MainActivity -> TerminalScreen                                         |
|   TopBar (reorderable) / TabBar / Renderer (Canvas)                    |
|   TerminalInputView (AndroidView: gestures / IME / selection)          |
|   ScrollIndicators                                                     |
|   TerminalKeyboard (custom) / JapaneseFlickKeyboard / SpecialKeyBar    |
|   SettingsSheet / SshProfilesSheet / SnippetsSheet / HostKeyDialog     |
+------------------------------------------------------------------------+
       | writeBytes (input)             ^ emulator buffer (render)
       v                                |
+--- Domain layer -------------------------------------------------------+
| SessionManager --(holds)--> TerminalSession[*]                         |
|   TerminalSession: state machine / readLoop / resize / selection /     |
|                    cwd / label                                         |
|     emulator: TerminalEmulator (VT interpretation, dedicated thread)   |
|     channel : ProcessChannel = LocalPtyChannel | SshChannel            |
+------------------------------------------------------------------------+
       |                                        |
       v (local)                                v (remote)
+--- Execution base (local) ---------------+    +--- Remote (SSH) -------+
| ProotLauncher                            |    | SshChannel (JSch)      |
|   -> PtyProcess (forkpty)                |    |   shell + -L forward   |
|   -> engine (z2root / proot / chroot)    |    +------------------------+
|   -> distro shell                        |
+------------------------------------------+
       | deploy / update
       v
+--- distro / persistence / peripherals ---------------------------------+
| DistroBundle / Spec / Installer / Downloader (assets or DL)            |
| TerminalService (keep-alive) / DocsProvider (SAF)                      |
| AppSettings (DataStore)                                                |
+------------------------------------------------------------------------+
```

### 3.2 Lifecycle and residency design

#### Sessions live independently of the UI

`TerminalSession` lives **independently of the UI** (held by `SessionManager`). PTY/emulator state survives Activity destruction.

emulator state updates are concentrated on a **dedicated single thread** (`z2term-emu-*`); Compose reads via `StateFlow`.

#### Foreground residency and its lock (`TerminalService`, 0.8.143 / 0.8.268)

`TerminalService` (foreground service) handles keep-alive, maintaining the PTY in the background. `AudioBridge` (GUI audio) is handled in the same service family.

While keep-alive is on it holds a `PARTIAL_WAKE_LOCK` (keeps the CPU running) and **nothing else**. It is released on detach (keep-alive off), stop and destroy.

**It honours low-power mode (0.8.269)**: that `PARTIAL_WAKE_LOCK` is not taken when `serversLowPower` is on. Through 0.8.268 this service alone ignored the setting, so turning on low-power mode for resident servers **did not take full effect** — while resident servers ran, two services each held their own copy of the same WakeLock. Holding one of them anyway breaks the promise made to someone who chose battery over reachability, so a single flag covers both. ⚠ The check only happens in `onStartCommand`, so changing the setting must be followed by another `TerminalService.start` to re-evaluate it (the settings toggle does this; the call is idempotent). ⚠ **The toggle moved to Settings -> Automation -> process protection in 0.8.309** (`SettingsSheet`); the re-`start` call has to travel with it.

**No `WifiLock` here (0.8.268)**: 0.8.143 through 0.8.267 also held a `WIFI_MODE_FULL_HIGH_PERF` `WifiLock`. That setting takes the Wi-Fi radio out of power-save (PSM) entirely, keeping it at full power even while the screen is off — which costs battery and generates heat directly. Keeping the radio awake belongs to the side that **accepts inbound connections**, i.e. resident servers (`ServerDaemonService`), which holds that same `WifiLock`. This service only has to keep the interactive session's process alive, so it no longer holds a duplicate.

⚠ Consequently **🔒 alone does not preserve inbound reachability** — resident servers must be running.

⚠⚠ **But holding a `WifiLock` cannot be claimed to preserve reachability either (measured 2026-07-28).** On 0.8.267 — with `TerminalService` and `ServerDaemonService` each holding their own `WIFI_MODE_FULL_HIGH_PERF` lock, two in total — neither ping nor ssh reached the device from outside **right after a Wi-Fi reconnect**. dropbear was still listening and accepted logins from 127.0.0.1, so the server side was blameless and the failure was **below TCP (ARP)**. Reachability returned the instant the device sent anything outbound, which pins the cause to "Wi-Fi power save leaves the device unresponsive to ARP".

✅✅ **Settled on 2026-08-20 (0.8.367); the guess above was right.** Through a 5½-minute disappearance **the device's own log had not a single gap in its 10-second entries** — the CPU, the FGS and the WakeLock were all working and only the radio was gone. ⭐ **Remember this way of measuring**: put a 10-second record on both sides and look for **gaps in the timestamps on the device's log**. Gaps mean the CPU was put to sleep (an app-side problem); no gaps means the radio path (an app fix will not help). That one test splits the two cases. ⚠ **Always run it before acting on a guess.**

`WIFI_MODE_FULL_HIGH_PERF` is **non-functional and is read as `WIFI_MODE_FULL_LOW_LATENCY`**, and `WIFI_MODE_FULL_LOW_LATENCY` is only active while connected to an AP **with the screen on** and **the app in the foreground** — so it is **always inactive for a resident service** (screen off, background). Therefore:

- **Do not treat a `WifiLock` as a guarantee of reachability when making design decisions.** Dropping it from `TerminalService` in 0.8.268 was justified as "no point holding a duplicate of something that isn't working" — reachability was never guaranteed to begin with, so nothing was lost
- ⛔ **Rewriting it to `WIFI_MODE_FULL_LOW_LATENCY` does not fix it either.** It is always inactive under the conditions above, and where it does apply it only burns power with the screen on and the app in front. minSdk is 29, so `HIGH_PERF` may still do something on older devices and the lock is not removed — but it **is not counted on**
- ⭐ **The cure is "speak from the device periodically."** The resident tunnel's keepalive (`TunnelManager.KEEPALIVE_MS` = 10s, 0.8.367) does exactly that: measured **37% unreachable → 1%**. See the resident-tunnel section
- **Without a tunnel, cover it from the device side.** A `z2-when wifi:connect` rule in `~/.z2term` that pings the gateway and the peer once, a few seconds after reconnecting (the device announcing itself brings ARP back). ⚠ But that fires **once, right after a reconnect**, so it does not stop the slow disappearance that comes from staying silent
- To triage while the symptom is live, use `~/.z2term/macros/ssh-diag.sh`

⚠ **With resident servers or the capture services on, the process never dies**, so this service — and its "Z2Term running" notification — stays too. With them off, swiping the app from recents kills the process and the notification goes away. That is why residency notifications appear to multiply once resident servers are in use.

#### Resident servers (`ServerDaemonService` et al., 0.8.147)

Components: `ServerDaemonService` / `ServerDaemonManager` / `ServerSupervisorScript` / `BootReceiver` / `ServerEntry`

A generic mechanism to register any server (sshd/http/smb, …) as a **start command** (`ServerEntry`, stored as JSON in DataStore) and keep it resident independently of the interactive session. The server binaries are the user's to install into the distro; the app only runs the command, restarts it, and manages residency (no server is hard-coded).

**Why a supervisor script**
- Under proot/z2root every process is a child of a single engine process
- So **one supervisor script** is launched headless on the engine (`ProotLauncher.launch(command=/usr/local/bin/z2term-server-supervisor)`) and kept alive
- The supervisor runs each server in an **auto-restart loop** and writes state to `var/lib/z2term-servers/<id>.status` inside the rootfs (the app reads it for the list)

**Job-file model (0.8.198, live reload)**

The script is a **fixed string that bakes in no server definitions**; servers are handed over as files
under `var/lib/z2term-servers/`. A watch loop (every `POLL` seconds, see below) picks up any `*.job` it has
not started yet and spawns a run loop for it.

| File | Written by | Meaning |
|---|---|---|
| `<id>.job` | app | the command to run. **Its existence is the server's definition** — remove it and the server stops and is cleaned up |
| `<id>.want` | app | `1` = run / anything else = stop (per-server on/off) |
| `<id>.status` | supervisor | `state=` / `pid=` / `restarts=` / `last_exit=` / `cmd=` |
| `<id>.log` | supervisor | that server's stdout and stderr |
| `<id>.exits` | supervisor | exit history (`<epoch> <rc>`, last 20 lines) |
| `<id>.jobstamp` | supervisor | marker for when `.job` was last read (0.8.377). Empty file: **only its mtime matters** |

- **Adding, editing and deleting all apply without stopping the supervisor** (`ServerDaemonManager.syncEntries`).
  An addition is picked up within `POLL` seconds, a changed `<id>.job` restarts just that server, and removing
  `<id>.job` makes only that run loop clean up and exit. **Previously the script baked in a run loop for
  every entry known at registration time**, so an entry added later had no loop and required a full
  restart — taking every other server down with it. Fixing that is the point of A3.
- A `.job` is **not rewritten when the content is unchanged**; rewriting would look like "the command
  changed" and restart a server nobody touched.
- On start, `.status` / `.want` / `.claimed` / `.job` are cleaned before being rewritten. A stale
  `.claimed` (the marker saying a run loop was spawned) in particular would mean that id never gets a
  run loop again — a server that **silently never starts**. `.log` and `.exits` are kept, since they
  exist to explain a crash after the fact.

**Watch interval (`POLL` = 5s, 0.8.268)**

The supervisor runs **inside the engine** (proot/z2root), where spawning one external command costs thousands of syscalls through ptrace. **The watch interval therefore translates directly into heat and battery drain.**

Through 0.8.267 it woke every second and spawned `cat` twice (for `.want` and `.job`); `sleep` is itself an external command, so that was **three processes per second per server** — and **stopped servers ran at the same rate**. On device, merely being resident kept the engine at 5–7% CPU continuously and left the phone permanently warm (measured from `utime+stime` deltas in `/proc/<pid>/stat`).

Three fixes:
- Widen the interval to `ServerSupervisorScript.POLL_SECONDS` (5s) and route **every `sleep` in the script through `$POLL`** (except the `sleep 3` before a restart)
- Read `<id>.want` with the **shell built-in `read`** — it is a single line, and this spawns no process. `.job` may be multi-line, so it stays on `cat`
- **Stopped servers no longer rewrite `.status` every cycle** (only when the content changes)

The cost is that per-server on/off, additions, edits, deletions and crash-restarts take up to 5s to apply. A resident server's job is to keep running, not to answer within a second, so battery wins. `ServerSupervisorScriptTest` pins all three ("no hardcoded `sleep`", "`.want` not read via `cat`", "stopped-state write is guarded").

**Nothing spawned per cycle (`RECHECK_CYCLES`, 0.8.377)**

Even after 0.8.268 widened the interval to 5s, **the watching alone burned about 1% of one core, around the clock** (measured on-device over 120s of `utime+stime`: 103 ticks for the supervisor's z2root plus 8 ticks for each of the three shells below it). Per server it spawned, every 5 seconds, two `sleep`s (the watch loop and the run loop) plus one `cat` — **roughly 50,000 execs a day**. Under the engine every exec is stopped by ptrace, and on this device each one also emits an SELinux audit line (**682 of the 737 lines** in a 13-minute logcat capture were exactly that). Meanwhile `ServerDaemonService` holds a WakeLock, so the device cannot drop into deep idle while this goes on.

So the steady state now spawns **no external command at all**:
- **Replace `sleep` with a shell built-in** (bash's loadable, `enable -f /usr/lib/bash/sleep sleep`). ⚠ busybox ash and friends have no `enable`, so **failure must pass silently and fall back to the external `sleep`** — surfacing an error here would stop the supervisor from starting at all, taking every resident server with it.
- **Do not read `<id>.job` every cycle.** Compare the mtimes of `.job` and `<id>.jobstamp` with `test -nt` (a shell built-in) and **re-read with `cat` only when it moved** (`.job` may hold several lines, so the reading itself stays `cat`).  ⚠ **Touch the marker before reading** — marking after reading loses any write that lands in between, permanently.
- ⚠ **Insurance against a miss**: mtimes that land on exactly the same instant read as "unchanged", so every `ServerSupervisorScript.RECHECK_CYCLES` cycles (12 = 60s) the job is re-read unconditionally. Even a miss is caught within `POLL × RECHECK_CYCLES` seconds.

Measured (old and new run side by side for 60s on the same device): **44 ticks → 6 ticks, i.e. 0.73% → 0.10% of one core**. Edits applied without stopping, per-server on/off and cleanup on delete were all re-checked the same way. `ServerSupervisorScriptTest` pins that "only one `cat` reads `.job`", that "`enable` failures are swallowed", and that "the unconditional re-read is still there".

**Per-server on/off (0.8.163)**
- Each run loop watches the `<id>.want` flag (`1` = run)
- When the app rewrites it via `ServerDaemonManager.setWant`, only that one server starts/stops (within `POLL` seconds) without restarting the supervisor (so the other servers keep running)
- The flag's initial value reflects each `ServerEntry.enabled`
- A server-row toggle in the UI applies live via `setWant` while resident, or just persists `enabled` while stopped (applied on next start)

**Observability (0.8.198)**
- **Per-server logs**: stdout and stderr go to `<id>.log`. The UI (▤ on the server row) shows the last
  64 KiB with its size and a "clear log" action. Once past 1 MiB the file is trimmed to its last
  512 KiB **only while that server is not running** — swapping it mid-run would leave the live process
  holding the old inode, and its output would vanish from everywhere.
  The "never rotate" policy (`LogWriter`) is about **logs macros aggregate over time**; server output is
  not analysed that way, and unbounded growth is the bigger harm, so this one has a cap.
- **Restart count and exit code**: a climbing `restarts=` means "starts, dies, repeats". The row shows
  the restart count and the latest `last_exit` (hidden while it is 0).
- `wait` is called **exactly once per child**. Calling it again after a kill returns "no such child" and
  reports an unrelated exit code, making `last_exit` a lie (`ServerSupervisorScriptTest` pins the count).
- `ServerSupervisorScriptTest` runs the generated script through a real **`sh -n`**. The app never sees
  this script executing inside the rootfs, so a breakage only ever surfaces as "the server won't start"
  and is found late (the 0.8.165 incident, and the 0.8.187 `trimMargin` one).

**Residency and stopping**
- Foreground persistence (so the process is not killed) and LAN reachability (WakeLock + WifiLock) are handled by the dedicated `ServerDaemonService`. ⚠ **The `WifiLock` does not guarantee reachability** (measured: it drops at the ARP layer right after a Wi-Fi reconnect — see "Foreground residency and its lock" above)
- **`BootReceiver` (`RECEIVE_BOOT_COMPLETED`) auto-starts the daemon right after boot without opening the app** (only when "auto-start on boot" is on and at least one server is enabled)
- Stopping — via the "Stop servers" notification action or the settings — **kills the supervisor engine = stops all servers at once** (children are reaped together)
- Ports below 1024 cannot be bound by a non-root engine

**Low-power mode (`serversLowPower`, 0.8.148 / 0.8.269 / 0.8.309)**: when on, no WakeLock/WifiLock is held and Doze is allowed (battery over reachability; incoming connections may be delayed/dropped while the screen is off; applies on next start). **Since 0.8.269 the same flag also covers `TerminalService` (the 🔒 keep-alive)** — while resident servers run, two services each hold their own copy of the same WakeLock, so honouring it in only one place left the setting ineffective.

**The toggle moved to Settings -> Automation -> process protection (0.8.309)**: through 0.8.308 it lived in the servers tab (`ServersSheet`) and its text only mentioned incoming connections. ⚠ **It is not a server-only setting** — it covers the 🔒 keep-alive as well, and it changes **how quickly automation reacts**. ⚠ **Nothing holds a WakeLock on automation's behalf** (`HeadlessRun.launch` takes none), so the speed `z2-when` runs at is **borrowed from whatever lock a resident service is holding**. Someone who never opened the servers tab had no way to see that the setting concerned them, so it now sits with the battery-optimization exemption and the phantom-process workaround — the same "let the device sleep, or keep it awake" family. ⛔ **It is not shown in both places** (two copies of one toggle make it impossible to tell which one is in effect). ⛔ **The DataStore key `servers_low_power` does not change** (renaming it would reset everyone's setting to the default). ⚠ Conversely, **with no resident server and no 🔒, flipping this changes nothing at all** (nobody is holding a lock). That is the explanation for "I turned low-power off and it did not get faster"; time triggers using `setAndAllowWhileIdle` drift under Doze for a separate reason.

**Notification presentation (0.8.160)**
- The resident notification uses an `IMPORTANCE_MIN` channel (`z2term_servers_v2`) so it **shows no status-bar icon and collapses to the bottom of the shade** (a foreground service must have a notification, so it cannot be hidden entirely; this favours unobtrusiveness for the server-only case)
- Because the supervisor writes `.status` with a lag, the running count is refreshed **periodically** (`server-notif-refresh`) rather than once at startup — fixing the count getting stuck at 0 and also tracking restarts/crashes
- **Cycle and update condition (0.8.268)**: every 3s for the first minute, every 30s afterwards. On top of that, `notify()` and the widget redraw happen **only when the running count changes**, and `.status` is read once per cycle instead of twice. Through 0.8.267 this ran every 3s and re-issued the notification after two `.status` reads each time (~29,000 times a day); with a WakeLock held the device could not enter Doze, so the CPU woke for every one of them. Re-issuing an unchanged notification changes nothing on screen, so it is skipped

**Self-backgrounding servers (0.8.165)**
- The supervisor treats "the command exited" as "the server died" and restarts it, so a server that daemonizes itself and exits immediately gets restarted every few seconds
- Since the `sshd` wrapper kills any running dropbear on each start, **LAN-exposed SSH could not hold a connection**
- Fix: the generated supervisor script now exports `Z2_SUPERVISED=1`, and the `sshd` wrapper uses it to switch to **foreground mode** (as if `-D` had been passed), so it stays alive as a child of the supervisor and auto-restart works as intended

#### GUI desktop

The **GUI desktop** launches as a separate Activity (`GuiActivity`) and connects to the in-distro Xvnc with the built-in RFB client ([§4.12](#412-gui-desktop-gui)). The execution engine defaults to z2root (0.8.123), with PRoot and chroot (rooted devices) selectable via a hidden setting ([§4.3](#43-proot-execution-prootprootlauncherkt-prootsshdscriptkt)).

### 3.3 Android integration (detection hooks and the macro foundation)

A family of features that make Android-side events usable from the shell. All of them share one design stance:

> **The app provides the hook, the shell provides the logic.**
> The app only detects events and streams them to a known file. No app selection, filtering, retention policy or serving is ever hard-coded.
> The user builds that terminal-side (`tail` / own script / cron / a resident server).
> Default off, fully local, no external transmission.

Everything lands in one of two log files.

| File | Actual path | Contents |
|---|---|---|
| `~/.z2term/notifications.jsonl` | `filesDir/shared_home/.z2term/notifications.jsonl` | Notification detection |
| `~/.z2term/events.jsonl` | `filesDir/shared_home/.z2term/events.jsonl` | System events, time triggers, notification button replies |
| `~/.z2term/when/<id>.rule` | `filesDir/shared_home/.z2term/when/` | `z2-when` automation rules (+ `<id>.log` run logs) |
| `~/.z2term/widget/run.log` | `filesDir/shared_home/.z2term/widget/` | Output of macros started from the home screen widget |

#### Notification detection (`NotificationLogService`, 0.8.149)

Granting the OS "notification access" makes Android auto-bind and keep the `NotificationListenerService` resident (runs without opening the app, and after reboot = a notification-detection daemon). When `notificationCaptureEnabled` is on, each incoming notification is appended **raw** as one JSON per line (ts / time / pkg / app / title / text / category / key). This is the inverse of `z2-notify`. The lock-screen "hide sensitive content" setting only controls lock-screen **drawing** and does not affect listeners. However, **Android 15+ adds a separate restriction**: with "Enhanced notifications (Adaptive Notifications)" on, Android System Intelligence classifies OTP-bearing notifications as **sensitive** and **replaces their body with a placeholder before delivering** to "untrusted" listeners (any app lacking `RECEIVE_SENSITIVE_NOTIFICATIONS` — i.e. all ordinary apps). That permission is reserved for system-signed apps or specific roles and is not granted to ordinary apps, so the workaround is to turn "Enhanced notifications" off (see `MACRO-GUIDE` §5-6). No amount of extraction on our side can un-redact the placeholder.

**Body extraction (`extractBody`, 0.8.185)**: `title` is `EXTRA_TITLE`. Reading only the standard `EXTRA_BIG_TEXT` → `EXTRA_TEXT` for `text` drops **MessagingStyle SMS / one-time passwords** (whose body lives in `EXTRA_MESSAGES` while TEXT is empty), so it scans for the first non-empty field in priority order (**conversations moved to the front in 0.8.358**, see below): **MessagingStyle** (`EXTRA_MESSAGES`) → big text (`EXTRA_BIG_TEXT`) → text (`EXTRA_TEXT`) → **InboxStyle** (`EXTRA_TEXT_LINES`) → sub/info line (`EXTRA_SUB_TEXT` / `EXTRA_INFO_TEXT`) → `tickerText`. A notification with no text in any field (fully custom layout only) cannot be read in principle. The result merges into the existing `text`, so placeholders and log formats are unchanged.

**Conversations come first, and only from where they left off (`freshMessageText`, 0.8.358)**: a chat app **re-posts one notification for several messages that arrived together**, and puts only **the latest one, shortened for display**, in `EXTRA_TEXT`. ⇒ Reading `EXTRA_TEXT` first (as through 0.8.357) meant **whole messages vanished from the log and the surviving one was cut off** — on-device, three of four messages were lost and the fourth ended mid-sentence (another automation app captured all of it, i.e. it was reading `EXTRA_MESSAGES`). ⇒ **`EXTRA_MESSAGES` is consulted first.** ⚠ But a chat app re-sends **the last few messages in full every time**, so writing them as-is repeats the same lines. ⇒ Per `key`, remember **the mark of the last message written** (`messageSig` = timestamp + body; **the timestamp alone is not enough** — several can share one, and some senders leave it at zero) and write **only what follows it**. ⚠ When the mark is not found in the conversation (first time / evicted from the LRU / the sender changed it), **write everything on offer** — repeating beats dropping (and `isDuplicate` still catches some of it). ⚠ **Nothing new returns `null`** and the notification is dropped entirely (an empty string would leave a title-only line). ⚠ `onNotificationRemoved` **does not forget** this mark — forgetting on dismissal would re-record the whole history the next time that conversation posts. ⚠ **`z2-noti list` does not diff** (it passes no `key`): it reads what is on screen right now, so it shows every message the notification carries. ⚠ **Conversations break the "one notification = one line" rule**: that round's new messages are newline-joined into a single line.

**Stripping bidirectional controls (`stripBidi`, 0.8.356)**: the Unicode bidi control characters (`U+200E`, `U+200F`, `U+061C`, the embeddings and overrides `U+202A`–`U+202E`, and the isolates `U+2066`–`U+2069`) are removed from `title` / `text` before anything else sees them — triggers and the log alike. **Why it is needed**: a phone app wraps the caller's number with `BidiFormatter` before putting it in the notification, so what arrives is `U+202A` + number + `U+202C` even though the screen reads `0120-355-565`. Those characters show up **neither on screen nor in the log**, so a macro that checks whether the caller looks like a number (the bundled `unknown-call.sh` strips `0-9+() -` and expects nothing to be left) **decides it is a name and silently does nothing**. On-device this meant not a single incoming call was ever caught, and because `z2-when fired` still records `run`, it failed in the hardest way to read: the rule fires, nothing happens (confirmed from the device log on 2026-08-17). ⚠ **It has to apply to both the trigger path and the log** — doing only one produces "the log shows a number but the rule does not match". ⚠ This is **the one exception to logging notifications verbatim**, and it only drops characters that never render, so nothing readable changes. ⚠ `z2-noti list` goes through the same filter. **Why not fix it in the macro**: `z2-macro install` never overwrites, so an old copy on the device keeps running (the same shape has caused two incidents already). Fixing it in the app means existing macros start working without being reinstalled.

**Output format (`notificationLogFormat`, 0.8.151)**
- `render()` substitutes the template
- Available: placeholders like `{time}` `{app}` `{title}` `{text}`, one-line `{text1}` `{title1}`, and `\n` `\t` escapes
- **Blank = JSONL** (default)
- Fill from a preset (readable / one-line / TSV / JSONL) then edit freely

**Newest at the top (`notificationLogPrepend`)**: when on, each new line is **prepended** to the head of the file (newest first) instead of appended. Since a file has no OS primitive to insert at the head, `LogWriter` reads the existing content and rewrites it. No line cap = all lines kept (0.8.163).

**Deduplication (0.8.165)**
- Android re-posts the same notification many times even when nothing changed (progress updates, ongoing notifications, group summaries), which used to produce many identical lines
- The last content (title + text) per `key` is remembered in a 256-entry LRU and **identical re-posts are not written**
- For apps that recreate the `key`, "same app + same content within 10s" counts as the same notification too
- `onNotificationRemoved` forgets the `key`, so a re-post after the notification was dismissed is logged as a new entry

**Saving on/off (`notificationLogEnabled`, default on, 0.8.165)**: turning it off keeps detection (the resident listener) running but writes nothing to `notifications.jsonl` — for users who only want detection, or who care about storage/privacy.

**Implementation notes**: the service subscribes to `AppSettings.flow` and caches the flag to avoid a DataStore hit per notification; writes are serialized on a single-thread executor.

#### SMS detection (`SmsLogReceiver`, 0.8.186)

Sibling of notification detection. With the OS `RECEIVE_SMS` permission and `smsCaptureEnabled` on, incoming SMS are appended to `~/.z2term/sms.jsonl`, one per line (JSON: ts / time / from / body; template placeholders `{time}` `{ts}` `{from}` `{body}` `{body1}`). Multipart SMS are reassembled by concatenating the part bodies.

**Why it is needed separately from notification detection**: per the notification-detection section above, Android 15+ classifies OTP-bearing notifications as sensitive and hands ordinary listeners a redacted body. **Reading the SMS directly bypasses that redaction and does not depend on lock state**, so one-time passwords come through reliably (same mechanism as an automation app's "SMS received" trigger).

**Why a manifest receiver suffices**: `SMS_RECEIVED` is exempt from the implicit-broadcast restrictions, so unlike system-event detection no resident FG service is needed — a manifest receiver (`android:permission="android.permission.BROADCAST_SMS"`) fires even when the app is not running or the device is locked. On receipt it uses `goAsync()` to move off the main thread, reads settings via `AppSettings.flow.first()`, and writes through `LogWriter`.

**Stays installable on non-telephony devices (0.8.188)**: declaring `RECEIVE_SMS` makes Android implicitly treat `android.hardware.telephony` as **required**, which blocks installation on tablets/ChromeOS (lint flags this as an error, `PermissionImpliesUnsupportedChromeOsHardware`). Since z2term is a terminal and SMS detection is optional, `<uses-feature android:name="android.hardware.telephony" android:required="false" />` is declared explicitly so installation is unaffected (SMS detection simply never fires on such devices).

**Sample**: `z2-macro install otp-sms` installs the version driven by `sms:otp` (0.8.273; before that it was a resident script polling `sms.jsonl` every 2 seconds and extracting 4–8 digits itself — see "Moving the samples off residency onto `z2-when`" below).

#### System event detection (`SystemEventService`, 0.8.152)

Sibling of notification detection, adding "Android → shell" triggers. When `systemEventCaptureEnabled` is on, each event is appended as one JSON per line (ts / time / event, plus level / ssid when applicable). All permission-free.

**Why a foreground service is required**: screen on/off, unlock (USER_PRESENT), battery level changes, charge start/stop, and Wi-Fi connect/disconnect are **not delivered to manifest-declared receivers** under Android 8+'s implicit-broadcast restrictions. So an opt-in foreground service `SystemEventService` (`foregroundServiceType=specialUse`) stays resident and picks them up via **dynamic receivers** registered with `registerReceiver`.

**Values of `{event}`**

| Category | Events |
|---|---|
| Screen / lock | `screen_on` / `screen_off` / `unlocked` |
| Power | `power_connected` / `power_disconnected` / `battery_low` / `battery_okay` |
| Battery level | `battery_level` (when the level crosses a 10% boundary; added in 0.8.156) |
| Network | `wifi_connected` / `wifi_disconnected` |
| Audio out | `headset_plugged` / `headset_unplugged` |
| These 7 added in 0.8.154 | `airplane_on` / `airplane_off` / `ringer_normal` / `ringer_vibrate` / `ringer_silent` and others |

**Output format**: follows the `systemEventLogFormat` template (`{time}` `{ts}` `{event}` `{level}` `{ssid}`, `\n` `\t`, blank = JSONL) via `render()`. When "Newest at the top" (`systemEventLogPrepend`) is on, new lines are prepended via `LogWriter` (0.8.163).

**Other**
- Wi-Fi fires once per connect/disconnect state change (same-state repeats suppressed)
- Wi-Fi SSID is blank without location permission (v1 requests no extra permission, best-effort)
- `BootReceiver` auto-starts it right after boot without opening the app (when enabled)
- Shows an ongoing notification while active

#### Wi-Fi connectivity fix (`SystemEventService.handleWifi`, 0.8.168)

The check moved from `WifiManager.connectionInfo` to **`ConnectivityManager` + `NetworkCapabilities`**.

**Why**: the former returns an invalid value (networkId = -1) on Android 12+ **unless the caller is in the foreground**. With the screen off — exactly when you want the event — it always looked disconnected and `wifi_connected` was missed (reproduced on device while building `z2-state`, which was fixed for the same reason in 0.8.167).

The SSID still comes from `WifiInfo` and stays empty when unavailable.

#### Wi-Fi connect / disconnect were swapped (`SystemEventService.networkCallback`, 0.8.248)

**Symptom**: turning Wi-Fi **off** logged `wifi_connected` and turning it **on** logged `wifi_disconnected`; `z2-when`'s `wifi:connect` / `wifi:disconnect` fired the wrong way round too. Confirmed on device in `events.jsonl` (Wi-Fi still on, yet the last entry is `wifi_disconnected`).

**Cause**: the trigger was `WifiManager.NETWORK_STATE_CHANGED_ACTION`, and `ConnectivityManager.activeNetwork` was read **right there in the receiver**. That broadcast arrives **before the default network switches over**, so just after a disconnect Wi-Fi is still the default (`connected=true`) and just after a connect it is still mobile or undetermined (`connected=false`). The check itself — fixed in 0.8.168 — was correct; only the moment it was read was too early (`z2-state wifi` was always right because it reads when asked). The same cause explains runs of consecutive `wifi_connected` entries: the broadcast fires several times while a connection is being established.

**Fix**: move to `ConnectivityManager.registerDefaultNetworkCallback`. `onCapabilitiesChanged` is called **after the state has settled**, so the mix-up cannot happen. Looking at the default network keeps the check identical to `z2-state wifi`. `onLost` (the default network went away) only means "no longer Wi-Fi" — when another transport takes over, `onCapabilitiesChanged` follows.

⚠ Registering fires `onCapabilitiesChanged` once for the current default network, so `lastWifiConnected` is **seeded with the current value before registering** (otherwise starting the service would look like a connect event — same reasoning as `btCallbackPrimed` for BT audio).

#### Bluetooth audio triggers (`SystemEventService.syncBtAudio`, 0.8.170)

**Background**: wired headsets arrive via `ACTION_HEADSET_PLUG`, but **wireless earbuds have no equivalent broadcast**, so the classic "start playing when I plug in" macro could not be written for them.

**Implementation**: `AudioManager.registerAudioDeviceCallback` watches output devices and fires `bt_audio_connected` / `bt_audio_disconnected` only when A2DP/SCO presence actually changes.

- **No extra permission** is needed (`BLUETOOTH_CONNECT` is only required for device names, which we deliberately do not expose)
- The callback fires once for already-connected devices at registration time, so the first invocation only seeds the state (starting the service must not look like a connection)
- `z2-state` also gained `bt_audio` and battery temperature `temp` (°C)

#### Unlock-failure detection (`PasswordWatchAdmin`, 0.8.171)

A **detection hook** for anti-theft macros like "after N wrong passwords, notify / record location / sound an alarm".

**Implementation**: Android does not hand unlock-failure callbacks to ordinary apps, so the app registers as a **Device Admin declaring only the `watch-login` policy** and forwards `DeviceAdminReceiver.onPasswordFailed` / `onPasswordSucceeded` to events.jsonl.

| Event | Contents |
|---|---|
| `unlock_failed` | `{level}` = `DevicePolicyManager.currentFailedPasswordAttempts` = consecutive failures |
| `unlock_succeeded` | — |

**Safety-first design**
- **No destructive policy (force-lock / wipe-data / reset-password) is declared or exercised** (`device_admin.xml` is `watch-login` only), so activating it cannot let the app lock or wipe the device
- The `unlockWatchEnabled` setting (default OFF) is the master switch for detection; when OFF nothing is written even if the admin is active
- **No action (photo, upload, alarm) is hardcoded** — the user builds the reaction as a macro over events.jsonl

**Constraints**
- Activation happens **from the in-app settings screen via `ACTION_ADD_DEVICE_ADMIN`** (its `EXTRA_DEVICE_ADMIN` is a ComponentName parcelable that can't be built from the shell; when already active the button opens `ACTION_SECURITY_SETTINGS` to deactivate)
- Background camera capture is blocked by Android 9+ restrictions and needs a separate implementation, so this version does detection only
- `EventEmitter.emit` gained a `level` argument

#### Time triggers (`AlarmScheduler` / `AlarmReceiver` / `z2-alarm`, 0.8.167)

Appends an `alarm` event (with `{name}`) to events.jsonl at a given time.

**Why AlarmManager rather than cron**
- "Every morning at 7" used to depend on the distro's cron
- cron has to be installed per distro
- It **does not run at all during Doze**, so in practice it only worked with the screen on
- Going through AlarmManager lets the OS wake the app, so it fires with the screen off

**Permission trade-off**
- `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM` (a user grant) on API31+
- So we use the permission-free `setAndAllowWhileIdle` (Doze-piercing, inexact)
- → We document that **firing may be several minutes late** — acceptable for macros

**Persistence and recovery from reboot**
- Schedules live in `filesDir/alarms.json`
- AlarmManager registrations are lost on reboot and re-registered by `BootReceiver`
- `daily` re-arms for the next day when it fires, `once` is deleted
- A `daily` whose time passed during the reboot is moved to its next occurrence and a stale `once` is dropped (no catch-up firing)

**Other**
- Writing to events.jsonl **does not depend on the "system event detection" setting** — an alarm is something the user set explicitly, independent of which passive events they collect
- Deciding whether `HH:MM` means today or tomorrow needs Calendar, so it happens in Kotlin rather than sh; only relative forms like `in 5m` are converted to an epoch by the shell wrapper

#### Current-state query (`z2-state`, 0.8.167)

**Background**: events.jsonl only reports changes, so `z2-battery` was the only way for a macro to ask about the present.

**What it returns (in one call, without extra permissions)**: screen (`isInteractive`) / lock (`isKeyguardLocked`) / Doze (`isDeviceIdleMode`) / charging + plug type + level (sticky `ACTION_BATTERY_CHANGED`) / Wi-Fi connectivity / SSID / ringer mode / airplane mode / wired headset / media volume

**Output shape**
- A **flat JSON** object (not nested, so sed/grep can pick fields without jq)
- Passing a key returns just the raw value → `[ "$(z2-state charging)" = "true" ]` works

**Wi-Fi connectivity** comes from **`ConnectivityManager` + `NetworkCapabilities`**, not `WifiManager.connectionInfo`: the latter returns an invalid value (networkId=-1) on API31+ unless the caller is in the foreground, which made background queries — exactly what macros do — always look disconnected (confirmed on device). Only the SSID still needs `WifiInfo` and a location permission, so it is empty when unavailable.

#### Starting / stopping resident servers from the CLI (`z2-server`, 0.8.310, F)

**Background (a real failure on device)**: a rule like `z2-when wifi:connect run 'sshd --lan'` leaves the phone **unreachable over ssh while it sleeps**. The daemon a rule starts runs **outside the resident-server frame**: `HeadlessRun.launch` does keep daemons alive (`waitTracees` — without it `sshd --lan` dies together with the engine right after starting), but it **takes no locks and starts no foreground service**.

| What is missing | What happens during sleep | Who does hold it |
|---|---|---|
| WifiLock (`WIFI_MODE_FULL_HIGH_PERF`) | ⚠ **nothing, in fact** (verified in 0.8.367: non-functional, and always inactive while the screen is off — see "Foreground residency and locks") | `ServerDaemonService` **only** |
| WakeLock | the CPU sleeps and `sshd` never accepts | `ServerDaemonService` / `TerminalService` |
| Foreground service | the process is cached and can be killed; `sshd` is a child of proot, so it **goes with it** | same |

⚠ **The 🔒 keep-alive does not cover this.** The WifiLock was **deliberately removed** from `TerminalService` in 0.8.268 (keeping an interactive session alive does not need the radio). That call was right, but it left servers started by automation **in the gap between the two frames**.

⛔ **Rejected: give `HeadlessRun` the locks.** Every single macro would then keep the device awake, undoing the separation made for battery in 0.8.268-269. **The actual hole is that the only way into the frame was the UI** (`ServerDaemonManager.setWant` had exactly one caller, `ServersSheet`), so the fix is to open that door to the CLI.

**Subcommands**

| | What it does |
|---|---|
| `list` | one server per line, TSV (`index / id / state / mark / name`); `*` = enabled, `-` = disabled. ⚠ Same shape as `z2-session list` |
| `start <server>` | enable it and bring it up inside the frame: `setWant` for a single one if the supervisor runs, otherwise `ServerDaemonService.start` for the whole frame |
| `stop <server>` | stop only that one (the rest keep running) |
| `status [<server>]` | flat `name=` `state=` `pid=` `restarts=` `last_exit=` key/values (same reasoning as `z2-state`: usable without jq) |

- **Resolution order: index → id → exact name → name prefix.** ⚠ Deliberately the same order as `resolveSession` (one syntax to learn, not two). ⚠ Names are not unique (the UI does not enforce it), so **an ambiguous match is refused rather than guessed** — picking one silently would leave another server running that you believed you had stopped.
- ⚠ **`start` must enable the entry before starting the service.** The supervisor **refuses to start when nothing is enabled**, so the other order comes up empty and immediately calls `stopSelf`.
- ⚠ **It only starts and stops registered servers.** Accepting a command inline would duplicate the app's list and produce "servers that run but do not appear anywhere".
- ⚠ **With low-power mode on, no locks are taken even after a successful start** (`ServerDaemonService` honours the same `serversLowPower`). That is correct behaviour, but staying silent would recreate "it started and still won't connect", so **`start` prints the warning right there** (the start itself succeeded, so it is not an error).
- ⚠ **Stopping the last server does not tear the frame down**, because a standing tunnel (`TunnelManager`) would go with it. Stopping everything is what [Stop] on the servers tab is for.

Which makes this expressible:

```sh
z2-when wifi:connect    run 'z2-server start sshd'
z2-when wifi:disconnect run 'z2-server stop sshd'
```

#### Driving the app's own tabs (`z2-session`, 0.8.199, A1)

**Background**: every verb in `Z2ApiBridge` was a one-way "shell tells Android to do something", and
**not one of them touched the app's own inside (its tabs)**. There was no way for a shell or a macro to
open another working tab, place a command into a different tab, or grab what is on screen right now.

**Subcommands**

| | What it does |
|---|---|
| `list` | one tab per line as TSV (`index / id / kind / marks / name`); marks are `*`=visible, `!`=busy, `?`=not started, `@`=attached from a shell, `-`=neither |
| `new [name]` | opens one terminal tab, **starts it**, and returns `index\tid` (the handle you then `send` to) |
| `send <target> <text>… [--enter]` | **inserts** text into that tab; only runs it when `--enter` is given |
| `key <target> <key>…` | send **keys** to that tab (`C-c` / `M-x` / `F5` / `Up` …); `--raw` takes bytes (0.8.311) |
| `capture [target] [--all]` | returns that tab's on-screen text (`--all` includes the scrollback) |
| `attach <target>` | **stay connected** to that tab and just type in it (0.8.366); leave with `Ctrl+]`, or with `~.` at the start of a line |
| `close <target>` | closes that tab (never the last one — the same promise as double-tap-to-close in the UI) |

**Safe by default**: `send` **inserts without executing** (no newline appended) — the same promise as
receiving a share (B1, [§5.1.2](#512-receiving-shares-b1-08197)), so no tab ever starts running on its
own. `Z2ApiScriptTest` pins that the helper does not slip in a `--enter` of its own.

**Target resolution** (`resolveSession`) goes **index (1-based) → id → tab name**. The index is column 1
of `list`, which is by far the easiest thing to pass along, so it wins. A name match must be exact,
and a prefix match is accepted **only when it narrows to exactly one tab** — a vague target must never
quietly type into "whichever tab happened to be first".

**Implementation**: text lands through `SessionManager.insertText` (bracketed paste), the entry point
factored out by B1, so A1 really was just adding verbs. Creating/destroying tabs and reading the buffer
go through `runOnMainSync`, putting them on the same thread assumption as drawing.

**`new` starts the tab too** (0.8.203). The screen-side autostart only fires for "the visible tab, if it is IDLE", so **a tab created while the app was closed stayed unstarted until it was opened** — and a following `send` did nothing, because there was no PTY (found on device). To make "open a tab and feed it a command" work from a macro, `new` calls `startTerminal` itself. A distro that would need a first-run download is left alone, so nothing starts a transfer behind the user's back; the on-screen confirmation still handles it.
`list` also gained a **`?` (not started)** mark: sending to an unstarted tab does nothing, and without the mark there is no way to tell why.

**Keys get their own verb** (`key`, 0.8.311). ⚠ `send` goes through `pasteText`, so it is wrapped in
**bracketed paste** (`ESC[200~ … ESC[201~`) and the shell reads it as "the characters `^C` were
pasted" — passing `\x03` to `send` therefore **never raises SIGINT**. `key` writes straight to
`writeBytes`. ⚠ Do not instead teach `send` to skip the wrapping for control codes: what "paste"
means would then depend on the payload, which cannot be explained to anyone.

- **The table lives in `AndroidKeyMapper`** (`keyBytesFor`). ⚠ If the built-in keyboard
  (`mapKeyEvent`) and the CLI emitted different bytes, bugs would reproduce through one path only.
  `KeyBytesForTest` pins that both go through the same table.
- **Arrows are built by the emulator** (DECCKM-dependent). A hard-coded sequence would break arrows
  in any application-cursor-keys program.
- ⛔ **Shift-ed keys such as `C-S-a` are refused rather than sent** (the user's call). A terminal
  folds Shift into the character, so it would be **the very same byte as `C-a`** (`controlByteFor`
  collapsing `a..z` and `A..Z` is exactly that). ⚠ Sending `C-a` silently would make "I sent it and
  nothing happened" untraceable, so the error **also says what to write instead**. The protocols that
  can tell them apart (xterm's modifyOtherKeys, the Kitty keyboard protocol) are not implemented, and
  would need the receiving program to cooperate anyway.
  ⚠ **`S-Tab` is allowed**: the test is not "does it carry Shift" but "**can the terminal tell it
  apart**", and backtab genuinely exists as `ESC [ Z`.
- **`--raw` takes escape notation** (`\xHH` `\e` `\n` `\r` `\t` `\0`). ⚠ Real bytes are not
  accepted as arguments because the request file is "one line = one argument", so a literal newline
  would break the separator (the same reason `z2-icon` folds a drawing into base64).
- ⚠ **Everything is converted before a single byte goes out.** Sending the keys up to a typo and
  then failing would leave nobody able to tell what actually arrived.

**Staying connected** (`attach`, 0.8.366). ⚠ **This is not "make `send` nicer"** — driving a tab one command at a time with `send --enter` then `capture` never tells you when the command finished, and the capture mixes in whatever was on screen before. `attach` is a handle that holds the tab open like ssh, so you can type into **the very tab that is on the phone's screen** (`sshd` gives you a *new* shell, unrelated to any tab). Both sides watch the same PTY, so what you type also appears on the phone.

- **One AF_UNIX socket carries it** (`filesDir/shared_home/.z2term/attach.sock` on the host, `/root/.z2term/attach.sock` from the guest). `z2api` drops a file and polls for an answer every 100 ms, which **cannot serve anything interactive**. z2root translates the path on `bind`/`connect`, so the guest connects using the guest path (proven by the pacman/gpg-agent fix in 0.8.327).
- ⚠⚠ **One socket for the whole app, never one per tab.** `sun_path` holds 107 bytes, and a per-tab path overflows it at **109** (`/data/user/0/com.zerotoship.z2term/files/shared_home/.z2term/attach/<uuid>.sock`); the single socket is 72. **This only fails on a real device**, never on the desktop, so do not change it. The target arrives in the first frame and is resolved by the same `resolveSession` (index → id → tab name).
- ⚠⚠ **Keep a reference to the `LocalSocket` that was bound (0.8.368).** `LocalServerSocket` only **borrows** the fd it is handed, so dropping the bound socket lets GC close that same fd from its finalizer. **The socket file stays on disk while the listen quietly disappears**, so connecting returns `ECONNREFUSED` rather than `ENOENT` — `z2attach` prints "cannot reach the app. Is z2term running?" **while the app is plainly running and the socket file is plainly there**. Worse, it **works right after binding and goes quiet once GC runs**, which points suspicion away from the listener (hit on a real device in 0.8.367). Hold **both** the server and the bound socket, and close both in `stop()`.
- **A dead listener is released, not held (0.8.368).** `start()` returns early while `server != null`, so keeping a finished listener makes the app **never accept again**. When the accept loop ends it releases itself, and `start()` is called from `MainActivity.onResume` as well as `Application.onCreate` — i.e. **reopening the app brings it back**.
- **Frames are `[type 1 byte][length 2 bytes BE][payload]`.** Passing raw bytes leaves **no room to report a size change**, so everything travels in the same envelope: data / size / notice / target. ⚠ **Never split one frame across two writes** — the PTY reader thread and the reply path both write, and interleaving destroys the envelope boundary.
- **The taps already existed.** Output is duplicated in the read loop **at the same place and in the same chunk as the session log (C1)** — rebuilding it elsewhere would let "it showed on screen but never reached the attached side" happen — and input goes through `writeBytes`, the same exit `key` uses, which bypasses bracketed paste so Ctrl+C arrives as Ctrl+C. ⚠ Unlike the log, **alt screen is always forwarded**: the other end is reproducing the screen, so filtering would freeze it the moment a full-screen program starts.
- **The size follows the attached side** (user's choice). ⚠ **While attached, the tab on the phone wraps wrongly and looks broken**; that is the accepted trade, and it returns when the last client leaves. ⚠ **Restoring it requires remembering the size the screen asked for** — the screen's resize is driven by `LaunchedEffect(session.id, rows, cols)` and **never fires again unless rows/cols change**, so nobody re-announces the phone's size on detach.
- **On attach, the current screen is rebuilt with its colours and sent.** ⚠ `getAllText()` is plain text and **drops every colour and attribute**; build it from `TerminalBuffer.getScreenRow` and `SgrAttribute` (32 bits per cell). ⚠ **Always re-emit SGR from `0`** — emitting only differences lets a forgotten attribute bleed down the rest of the screen. The scrollback is not sent (that is `capture --all`'s job).
- **Leave with `Ctrl+]`, or with `~.` at the start of a line** (the latter as in ssh). The check lives in **the client** and swallows it before the app sees it; a literal `~` at line start is `~~`, and a literal `Ctrl+]` is **`~` at line start followed by `Ctrl+]`**.
  - ⚠⚠ **`~.` alone cannot get you out over SSH (0.8.370, hit on a real device).** The ssh client escape is "a `~` at the start of a line" as well, so typing `~.` inside an attach on an SSH session makes **the ssh in front of you eat it and drop the whole SSH session** (not one byte reaches the client). `~~.` works, the same way it does for nested ssh, but that is **making the user carry the workaround**, not fixing it. ⇒ **add a key that does not collide with ssh**. `Ctrl+]` (0x1D) passes straight through every ssh escape handler, so it always reaches the client, however many hops away.
  - ⛔ **The 0.8.366 rule of "take no Ctrl key away" was bent here (0.8.370).** One `Ctrl+]` is taken from full-screen programs, and in exchange **"cannot leave over SSH" is gone**. The key that was taken is still reachable: type `~` at the start of a line, then `Ctrl+]`.
  - ⚠ **It leaves the moment it is pressed** (it does not wait for the next byte). `Ctrl+] Ctrl+]` for a literal is possible, but **a detach key that looks like it did nothing** is the worse failure.
  - ⚠ **The one-line hint is written for the environment it runs in** (`SSH_TTY` / `SSH_CONNECTION` / `SSH_CLIENT`). Offering `~.` over SSH would mean "I typed what it said and my SSH session died".
- **The client is a small native program** (`app/src/main/cpp/z2attach/z2attach.c`): `/bin/sh` cannot put the terminal in raw mode, cannot wait on stdin and a socket at once, and cannot catch `SIGWINCH`. ⚠ Only `lib*.so` names are unpacked into `nativeLibraryDir` at install, so it ships as `libz2attach.so` and is provisioned into the rootfs as `z2attach` (the same trick as `libz2accept.so`). ⚠ **Always restore the terminal, even on an abnormal exit** — leaving it raw is the worst failure mode there is.
- ⛔ **Refusals say why** (the promise `key` set): a GUI tab, a tab that has not started, a tab that already exited, and no such tab each get their own answer. ⚠ **Never start a stopped tab from here** — attaching must not kick off a first-run OS download.
- **While attached the app is held resident** (`AttachHold`), because being killed mid-session from the PC is the worst outcome; it reuses the same `TerminalService` that 🔒 starts. ⚠ **The `keepAliveService` setting is never written** (that would leave the user's setting changed after detaching). ⚠ **Only release what you acquired** — if 🔒 is on or a resident server is running, residency belongs to them.
- `list` gained the **`@` (attached)** mark; without it there is no way to tell from the phone which tab someone is holding from a PC. ⚠ **Marks stack** (`*@` = visible and attached).

**A name given to `new` sticks** (0.8.202). `TerminalSession` carries `labelPinned`; while it is set, the label is **not** overwritten by the OS name (`spec.id`) at startup, by the `android-sh` fallback, by an SSH connection, or by a title the shell emits (OSC 0/2). Without it, the name from `z2-session new build` turned into the OS name moments later during startup, which made naming pointless (found on device).

#### Automation hub (`z2-when` / `WhenManager` / `WhenReceiver`, 0.8.205, A6 stage 1)

**What it does**: auto-runs a Linux script on an Android event (charge / battery / time). Previously `z2-*` split "detect" (write to events.jsonl) from "act" (`z2-session`, …), and the user's own resident script was the glue. `z2-when` owns **declare trigger → app watches → run on fire**, turning the phone into a pocket automation server. It's wiring of existing pieces, not 0→1.

**Rules are plain text**: `~/.z2term/when/<id>.rule` (`filesDir/shared_home/.z2term/when/`), three lines `trigger=` / `run=` / `enabled=` (`settings/WhenRule.kt`; optionally `order=`, the 0.8.263 filters `if=` / `cooldown=` / `between=` / `days=`, and `name=` from 0.8.303 — all covered below). Plain files (not DataStore) so **git sync and backups work** (same idea as the resident-server job files). The CLI (`z2-when`) reads/writes them directly and calls `z2api when-reload` to re-arm time triggers.

**Trigger syntax (stage 1 + cron)**:
- `charge:start` / `charge:stop` — **only works while detection (`SystemEventService`) is on** (receiver moved in 0.8.214; see "No new resident component" below)
- `battery:below=N` / `battery:above=N` — fires the moment the level **crosses** N% (edge-triggered; last level saved in `.battlevel`, first reading only sets the baseline). **Only works while detection is on** (0.8.214)
- `time:daily=HH:MM` (every day) / `time:at=HH:MM` (once at the next HH:MM, then auto-disabled by writing `enabled=0`) / `time:every=Nm|Nh|Ns` (min 1 minute)
- `time:cron='min hour dom month dow'` (0.8.207, stage 2) — 5-field cron. Supports `*` / `*/n` / `a` / `a-b` / `a-b/n` / `a,b,c`; day-of-week 0-7 (0,7 = Sunday). **When both day-of-month and day-of-week are non-`*`, either match fires** (standard cron). Next-fire is computed by the Android-independent `CronSchedule.nextAfter` (concrete-example tested in `CronScheduleTest`); it rides the same AlarmManager path as `daily`/`every` and re-arms on each fire. Must be quoted in the shell since it contains spaces.
- `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<name>` (0.8.208, stage 2) — Wi‑Fi connect / disconnect / connect to a given SSID. Matching is the Android-independent `WhenTriggerMatch.wifi` (concrete-example tested in `WhenTriggerMatchTest`; SSID is case-insensitive, and `ssid=` misses when SSID is empty for lack of location permission). **Like the 10% battery buckets, it only works while detection (`SystemEventService`) is on** — the entry point is the `NetworkCallback` that service registers, so a live process is required. Up to 0.8.248 it was a broadcast, which swapped connect and disconnect (see above). On fire the SSID is passed as `Z2_WHEN_SSID` (safely single-quote-escaped as external text).
- `net:online` / `net:offline` / `net:wifi` / `net:mobile` / `net:ethernet` (0.8.264) — **a usable connection appeared or went away**, or **the link in use switched**. Unlike `wifi:*` it **counts mobile data**: `wifi:disconnect` can only say "Wi‑Fi went away" and cannot tell whether mobile picked it up or the device is genuinely out of range, so "send it once we can reach the network" and "stop when there is no service" were not expressible before. Matching is the Android-independent `WhenTriggerMatch.net` (concrete-example tested in `WhenTriggerMatchTest`). The entry point is the same `NetworkCallback` as `wifi:*`, so it **only works while detection is on**. ⚠ **It compares against the previous state** — that is the crux of this trigger. Switching from Wi‑Fi to mobile does not change the fact that the device is online, so `net:online` must not fire (writing it as "does the current state hold" would run it every time the user walks out of the house). ⚠ It watches **whether traffic actually got through, not whether something connected** (`NET_CAPABILITY_VALIDATED`) — calling a Wi‑Fi that cannot get past a captive portal "online" would make `net:online` useless as the signal for "we can send now". The cost is that the few seconds until validation completes still read as `none`, so it fires **slightly later than the Wi‑Fi icon appears**. VPN is reported honestly as `vpn` (the default network is what it is; we do not resolve it to the link underneath). On fire it passes `Z2_WHEN_NET` (the link now) and `Z2_WHEN_NET_PREV` (the one before). It is also recorded in `events.jsonl` as `net_online` / `net_offline` / `net_<kind>`.
- `share:any` / `share:text` / `share:file` / `share:contains=<part>` / `share:ext=<ext>` (0.8.266) — **something was shared to z2term from another app**. The entry point is 0.8.197's `SharedIntake` (text as-is; files taken into `~/z2term-inbox/` and turned into paths), with **one branch added to it**. Sharing is an app-launch path, so it **does not depend on detection**. Matching is the Android-independent `WhenTriggerMatch.share` (concrete-example tested in `WhenTriggerMatchTest`). ⚠ **The share is still put on the input line as before** — rules are additive. This entry point carries the promise "insert only, never execute", and having one rule silently stop the insertion would break a use people already have; what stays on the input line is just text that was never executed. ⚠ **`contains=` never matches file shares** — for files the body is the path it was taken into, so matching would fire on "the file name happened to contain it", which is not what the author meant (filter on the content of the shared text). Use `ext=` for the file side. ⚠ **Sharing brings z2term to the front** — the share sheet targets an Activity, so that is Android's design, not a choice ("run quietly in the background" is not available here). On fire it passes `Z2_WHEN_SHARE` (the same string that goes on the input line) and `Z2_WHEN_SHARE_KIND` (`text` / `file`).
- `boot` (0.8.264) — **the device finished starting up**. The only trigger without a `:` (writing an empty argument for something that takes none is unnatural, so `WhenRule.kind` reads the whole string as the kind when there is no colon). The entry point is the existing `BootReceiver`, and `BOOT_COMPLETED` **is an exception to the implicit-broadcast restriction**, so a manifest-declared receiver still gets it — making this one of the few triggers that, like time and SMS, **works with detection off**. ⚠ It is **not** run for `LOCKED_BOOT_COMPLETED` (before first unlock): credential-encrypted storage is not open yet, so neither the rule files nor the engine can be read and it would simply fail silently. ⚠ The run is wrapped in `goAsync()` — a broadcast's process can be stopped the moment `onReceive` returns, and for a one-shot run with no service behind it that is the only thing keeping it alive.
- `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` (0.8.209, stage 2) — incoming SMS. Matching and OTP extraction are the Android-independent `WhenTriggerMatch.sms` / `.extractOtp` (concrete-example tested in `WhenTriggerMatchTest`; `from`/`contains` are case-insensitive substrings; OTP is the first run of **4–8 digits not flanked by digits**, so 9+‑digit phone/order numbers are ignored). It piggybacks on the existing `SmsLogReceiver` (the OS starts it on each SMS when `RECEIVE_SMS` is granted, even with the app closed) and is **evaluated independently of the raw-log setting `smsCaptureEnabled`** (works as long as the permission is granted). Reading the SMS directly bypasses Android 15's sensitive-notification redaction (`RECEIVE_SENSITIVE_NOTIFICATIONS`), so the body isn't masked (see the `SmsLogReceiver` note). On fire it passes `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`, plus `Z2_WHEN_OTP` for `otp` (all external input, single-quote-escaped, never `eval`ed — the safety boundary).
- `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far` (0.8.210, stage 2) — shook the device / light crossed N lux / proximity changed to near·far. **Continuous sensor monitoring costs battery**, so per §10-1 it's **opt-in and only works while detection (`SystemEventService`) is on**, and **only the sensors an enabled rule needs are registered** (`WhenManager.sensorKindsNeeded` → `SystemEventService.refreshSensors`; re-armed on rule changes / detection-on, nothing registered when the set is empty = zero battery). Accelerometer at `SENSOR_DELAY_UI` (fast enough for shake), light/proximity at on-change `NORMAL`. Shake uses `ShakeDetector` (**magnitude > 4.0g with a 3s debounce**; `ShakeDetectorTest`. The original 2.7g/1s **fired continuously just from walking with the phone in a pocket** — 255 times in 3.5 hours, with intervals pinned to the debounce, found in on-device testing on 2026-07-24 — so 0.8.214 raised it. Lowering it again requires re-checking on a real device that walking does not fire it); light/proximity use `WhenTriggerMatch.lightSatisfied`/`.proximitySatisfied` fired on the **rising edge of the condition (false→true)** (per-rule in-process state, first reading only baselines; threshold chatter not smoothed = future hysteresis). On fire it passes `Z2_WHEN_SENSOR` (`shake`/`light`/`proximity:near|far`), plus `Z2_WHEN_LUX` for light.
- `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` (0.8.236) — **a notification arrived**. Matching mirrors `sms:*` so there is nothing new to memorise. `pkg=` matches **either the package name or the app's display name** (nobody remembers package names). Passes `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT` / `_CATEGORY`, and `notify:otp` puts the extracted code in `Z2_WHEN_OTP` (same name as `sms:otp`). It works **independently of log saving** (`notificationLogEnabled`) — "don't record them, just trigger on them" is the normal case, and requiring the log would leave notification bodies on disk forever. Re-posts of the same notification (progress updates) are dropped by the dedup check **before** the trigger. Requires notification access.
- `notify:category=<kind>` (0.8.293) — match on the notification's **category** (`Notification.category`): `call` (ringing), `missed_call`, `msg`, `email`, `alarm`, `event`, `progress` … Android's own vocabulary. ⚠ **This one matches exactly** (case-insensitively): a partial match would make `call` fire on `missed_call` (it is a substring), so the two **could not be told apart**. Doing the same with `pkg=` would require knowing the phone app's package name, which differs per device; the category does not. ⚠ It is also **a tool for not adding permissions**: reading the incoming number directly needs `READ_CALL_LOG` (Android 9+) and checking contacts needs `READ_CONTACTS`, and the former is essentially undistributable outside the default phone app. A phone app shows the name for a known contact and the bare number otherwise, so **"is the display a bare number?"** answers "is this caller absent from contacts?" with notification access alone (sample `unknown-call`).
- `file:new=<dir>[,ext=<ext>]` (0.8.235) — **a new file landed in that folder**. Only `CLOSE_WRITE` (write finished) and `MOVED_TO` (written under another name, then renamed) are watched; **`CREATE` is not** — it would catch a still-empty file mid-copy. As with sensors, **only folders an enabled rule names** are watched (`WhenManager.fileDirsNeeded` → `SystemEventService.refreshFileWatchers`), and none at all when there are no such rules. Hidden files (`.pending-xxx` style partials) are always skipped, and the same path is ignored for 5s (both events can arrive for one file). Passes `Z2_WHEN_FILE` (full path) and `Z2_WHEN_DIR`. ⚠ A `FileObserver` only lives as long as the process, so this trigger **requires detection to be on** (it has none of the always-on nature of time or SMS).
- `event:<name>` / `event:<prefix>*` / `event:*` (0.8.226) — **any device event written to `events.jsonl`, picked by name**. Matching lives in `WhenTriggerMatch.event` (exact, trailing-`*` prefix, `*` for all; case and surrounding space ignored, so a hand-typed rule does not silently do nothing).

**Why it was added**: detection already captures 15+ events into `events.jsonl`, yet `z2-when` could only name 6 kinds. Writing "play when earphones go in" meant **the user had to keep a tail-loop macro resident** — making them build, by hand, exactly what §10-1 tells us not to build. This adds **no new resident component and no new permission**; it just lets you hear a bell that was already ringing.

**Two hook points** (note there is not a single "only exit"):
- `SystemEventService.emit` — passive events (screen, unlock, charging, battery, Wi‑Fi, headset, BT audio, airplane, ringer). **Requires detection on.** Called inside the existing single worker thread (`writer`), so rule-file I/O never lands on the receiver's thread.
- `EventEmitter.emit` — **things the user armed themselves** (`alarm` / `notify_action` / `unlock_failed` / `unlock_succeeded`). Since writing those does not depend on the detection toggle, **these triggers work with detection off** too. Dispatched onto a dedicated single thread so the caller (a receiver, or AlarmManager's delivery thread) is never blocked.

**10-second minimum interval** (`WhenManager.EVENT_MIN_INTERVAL_MS`, per-rule in-process state): now that events like `screen_on`/`screen_off` — which recur as often as the user happens to act — can be named, a single rule would otherwise become a firing storm. It is scoped **per rule**, not per trigger (separate rules never throttle each other).

**Env names chosen to avoid a collision**: the event name goes in `Z2_WHEN_EVENT`, the armed identifier (for `alarm` etc.) in `Z2_WHEN_EVENT_NAME`, and a pressed notification button in `Z2_WHEN_ACTION`. **`Z2_WHEN_NAME` still means the rule id** — existing rules keep their meaning.

**The name list lives in the CLI** (`z2-when events`). It is a heredoc, so a broken margin would silently empty it; `Z2ApiScriptTest.whenEventsListsEventNames` actually runs it through `sh` and checks the names come out.

**No new resident component (§10-1 guidance), partly retracted in 0.8.214**:
- Time uses **AlarmManager** (`setAndAllowWhileIdle` — Doze-through, no `SCHEDULE_EXACT_ALARM`; can be a few minutes off). Registrations are lost on reboot, so both `WhenReceiver` (`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`) and `Z2TermApplication.onCreate` re-arm via `WhenManager.reload`. Armed ids are tracked in `.armed` to reliably cancel alarms for removed/disabled rules. **Time triggers are the only ones that still work with no resident service** (an explicit Intent from `AlarmManager` does reach a manifest receiver).
- ⚠ **0.8.205 designed charge/battery around the manifest receiver `WhenReceiver`, assuming those broadcasts were exempt from the implicit-broadcast ban. That was wrong.** `ACTION_POWER_CONNECTED` / `_DISCONNECTED` / `BATTERY_LOW` / `_OKAY` are **not** in the official broadcast-exceptions list (it contains no power or battery broadcast at all), so they never reach a manifest receiver and **`charge:*` never fired once, up to and including 0.8.213** (found in on-device testing on 2026-07-24: `events.jsonl` had `power_connected` while the rule's log file did not exist at all). Time triggers kept working because they are explicit Intents, which is why the e2e passed and this went unnoticed.
- **0.8.214 moves the receiver into `SystemEventService`'s dynamic receiver** (`handlePower` / `handleBatteryLowOkay`). Like wifi / sms / sensor, **`charge:*` and `battery:*` now require detection to be on**. The §10-1 "no new resident component" goal is retracted to that extent.
- Battery thresholds are evaluated on charge changes, `BATTERY_LOW`/`OKAY`, and **every 1% change** (`SystemEventService.handleBatteryLevel`). Up to 0.8.213 they were only evaluated on 10% bucket crossings, so `battery:above=40` did not fire going 40%→44%, and when it did fire it was up to 10% late with a `Z2_WHEN_LEVEL` that disagreed with the real level — contradicting the documented "the moment it crosses". The `battery_level` event in `events.jsonl` still uses 10% buckets (to keep the log small). Edge-triggering dedupes double calls, and `.battlevel` is not rewritten when the level is unchanged.

**Execution**: on fire, runs `sh -lc '<run>'` **headless** on the currently selected distro (`ProotLauncher.launch(command="/bin/sh", extraArgs=["-lc", …])`, same launch+drain pattern as `ServerDaemonManager`). Trigger context is passed via env vars `Z2_WHEN_TRIGGER` / `Z2_WHEN_NAME` / `Z2_WHEN_LEVEL` plus trigger-specific extras (wifi: `Z2_WHEN_SSID`; sms: `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY` / `Z2_WHEN_OTP`; sensor: `Z2_WHEN_SENSOR` / `Z2_WHEN_LUX`; event: `Z2_WHEN_EVENT` / `Z2_WHEN_EVENT_NAME` / `Z2_WHEN_ACTION`) (external input is never spliced into the shell; values are single-quote-escaped with `'\''`). Output is appended to `~/.z2term/when/<id>.log` (cleared before a run once past 128KB). Rule execution always goes through the engine path since `launchChroot` takes no extra args.

**Filters (`if=` / `cooldown=` / `between=` / `days=`, 0.8.263)**: optional fields that say **whether this is a good moment to run**. Where `trigger=` decides *when* a rule fires, these apply **uniformly to every trigger kind**.

**Why**: adding a trigger kind adds one thing; a filter improves **all nine existing kinds** — and needs no new resident component and no new permission. Until now, "only on home Wi‑Fi", "only at night" or "not again for a while" had to be written as an `if` on `z2-state` at the top of the user's own script, and that path recorded the rule as having *run*, so **a skipped run was indistinguishable from a run that did nothing**.

- `if=<cond>[,<cond>…]` — filter on device state at the moment of the fire. Commas are **AND**, a leading `!` negates. The decision is the Android-free `WhenGuard.conditionsMet` (verified by example in `WhenGuardTest`). **Keys are the same vocabulary `z2-state` prints** (`wifi` / `charging` / `screen` / `locked` / `ssid` / `level` / `temp`, …) and values come from **the very same function** via `Z2ApiBridge.stateSnapshot` — a separate implementation would inevitably drift from what the user verified in the terminal. Three forms: truthy (`wifi`), equality (`ssid=Home`, case-insensitive) and numeric comparison (`level<30`). `screen` returns `on`/`off`, so bare `screen` reads `on` as true. **Unknown keys do not match** (preferring a missed fire over a wrong one, as elsewhere), but since that alone would let a typo silently disable a rule, **the CLI validates key names at registration time** (the list exists in `WhenGuard.KNOWN_KEYS` and in the `z2-when` script — extend both).
- `cooldown=<duration>` — do not run again within that time (`30s` / `10m` / `2h`; a bare number means minutes). Unlike `time:every=` it is **not clamped to one minute**: suppressing a burst of `sensor:shake` for a few seconds is a legitimate use. The last run time lives in **`.lastfire` (`id=epoch-millis`)**; `.fired` rotates at 50 entries and therefore **cannot be the source of truth** (a chatty rule would push the last run out of the log).
- `between=HH:MM-HH:MM` — only inside that window. **Start inclusive, end exclusive.** Start > end means it **wraps past midnight** (`22:00-07:00` covers the night).
- `days=mon-fri` / `sat,sun` / `1-5` — only on those days. Numbers follow **cron (0-7, 0 and 7 are Sunday)** so there is nothing new to memorise.
- **A malformed value does not filter** (`between` / `days`) — a typo must not leave a rule that can **never** run. `if=` falls the other way (running without being able to read the state is the riskier side).
- **Evaluated at one place: the entry of `runRule`**, right behind the kill switch, so new triggers cannot forget it. Order is **cheapest first** (`between`/`days` read a clock → `cooldown` reads one file → `if` collects device state), and later checks are skipped once something rejects. **State is read only at the moment of a fire**; no new polling.
- **"Any one of these" is its own field (`if_any=`, 0.8.372)**: `if=` stays AND, `if_any=` is **OR** (`WhenGuard.anyConditionMet`). With both, it reads "all of `if` **and** any one of `if_any`". ⛔ **Mixing `&&` / `||` / `()` into a single expression was rejected** — an expression you cannot read without knowing operator precedence is a liability for a non-programmer, and it stops mapping 1:1 onto the screen's "all / any" toggle. ⚠ **An empty `if_any=` filters nothing** (pinned by `WhenGuardTest`); making it false would stop every existing rule that has no `if_any=`. ⚠ The state snapshot is **taken once and used for both** — collecting it twice would let "all" and "any" look at two different moments of the device.
- **One level of "otherwise" (`else=`, 0.8.372)**: the command to run **instead of** `run` when `if` / `if_any` did not hold. ⚠⚠ **It only applies to `skip:if`**. A run skipped by `between` / `days` / `cooldown` runs **nothing at all** — a notification arriving at 3am from a rule the user explicitly switched off at night is the worst possible surprise. ⚠ **`else` does not consume the cooldown** (what ran was the stand-in; the real command still has not run once). ⚠ **`run` and `else` go through the same `execute()`** — differing environment or log paths would produce "`$Z2_WHEN_TRIGGER` works in run but is empty in else", with no visible cause. ⚠ A ladder (`if` → `elif` → `else`) is **deliberately not offered**: it complicates both the file format and the screen for a case one level already covers.
- **On screen, conditions are assembled by picking (`WhenConditionSpec`, 0.8.373)**: typing `wifi,!screen,level<30` is fine in a terminal, but **typing it into a form leaves a rule that never fires** the moment one letter is off. The string ⇄ structure conversion lives in one Android-free place (`WhenConditionSpecTest`) and the UI only offers dropdowns for key, operator and value. The value field follows the key's kind (boolean / match / number), and ⚠ **changing the key resets the operator and value** (leaving `level<30` while switching to `wifi` would keep a combination the screen cannot express). ⚠ **Saving validates by a build → parse round trip** — a missing value is caught as it is typed rather than becoming a silent mismatch between the form and the real expression.
  - **Each row carries its current value (0.8.374)**: on a real device a rule `if_any=volume>77,volume<5` fired **while the user believed the condition did not hold**. `volume` is not a percentage but the device's step count (0-15 here), so `volume>77` can never hold and `volume<5` held at 0 — correct OR behaviour, but **neither the unit nor the scale appeared anywhere on screen**. ⇒ the editor reads `Z2ApiBridge.stateSnapshot` **once** on open and puts `now: 0 / 15` on each row (`volume` shows its ceiling too). ⚠ Booleans are rendered through `WhenGuard.truthy` — a separate rendering path would eventually print "now: no" for a condition that holds. ⚠ **It does not track changes** (no polling, no extra traffic): building a condition needs a sense of scale, not a live readout.
  - ⚠⚠ **An expression it cannot rebuild is shown untouched** (`parse` returns null → plain text field). `screen=on`, or a rule carrying both `if` and `if_any` (writable from the terminal), must not be reinterpreted by the screen: that would produce **a rule whose behaviour changes just from opening and closing the editor**. Not silently discarding what cannot be represented is the whole reason this conversion is a separate file.
- **The rule you built, shown as one `z2-when` line** (`WhenCommandLine`, 0.8.375): the screen and the CLI read and write the same file (`~/.z2term/when/<id>.rule`), yet **nothing on screen said what the automation you just built is called in the terminal** — what you learn on the screen was unusable in the terminal, and an example read in the terminal gave no clue where it goes on the screen. ⇒ the editor prints `z2-when <trigger> name=… if=… run <cmd>` for exactly what is in the form, **tap to copy** (argument order follows the `z2-when` usage line). ⚠ **The preview and what gets saved come from one and the same `WhenRule`** — built separately they drift, and the line on screen would create a different rule. ⚠ **Pasting it has to make the same rule again**, so the characters the shell would eat (`*` globs, `>` redirects, and `run` takes `"$*"` so the command is quoted as a single argument) are pinned by example (`WhenCommandLineTest`). ⚠ It is **shown, not edited**: changing that line changes nothing, and it stays hidden until the trigger and the command are filled in (a line that would not work if pasted is worse than no line).
- **Adding fields does not break older builds** because `WhenRule.parse` **silently ignores keys it does not know** (pinned by `WhenRuleTest.parse_unknownKeysAreIgnored`). A build that has never heard of `else=` falls back to "do nothing when the condition fails" — the **safe** side.
- **Skips are recorded too** (`.fired` status `skip:if` / `skip:if→else` / `skip:between` / `skip:days` / `skip:cooldown`), for the same reason `status=paused` is recorded: never remove the means to find out why nothing happened. The automation tab shows them in a third colour (`ZtsWarning`).
- **Misspellings are rejected at registration** (`if=` keys, and **the trigger itself** as of 0.8.265). ⚠ **Refusing to register is the only place this can be caught**: one wrong letter still writes the `.rule`, still returns an id, still appears in `z2-when list` — and simply never fires. It is indistinguishable from a correct rule from the outside, and the runtime record (`skip:*`) cannot help because nothing fires and so nothing is recorded. So it is stopped **as it is typed**. Both the kind (`net` / `boot` / `charge` …) and the shape of that kind's argument (`net:online|offline|wifi|mobile|ethernet` and so on) are checked. ⚠ **Only `event:` names are left unchecked** — they keep growing and the list in `z2-when events` is the source of truth, so duplicating it here would mean forgetting one side every time one is added; only non-emptiness is verified. The table must be kept in step with `WhenRule`'s KDoc and `whenHelp` (**three places**).
- **▶ "run now" ignores every filter** (`manual`) — otherwise the way to try a rule out disappears. Same treatment as pausing.
- **No existing rule is rewritten.** `WhenRule.parse` silently ignores unknown keys, so a rule carrying the new fields loads fine on an older build (and vice versa). Rules without filters also look exactly as before — both the screen and `list` add a line only when there is one.

**Kill switch and a record of fires (0.8.227)**: every new trigger increases how often something runs **on its own**, yet there was no single action to stop it all, and nowhere to see what just ran. Added before `event:` (0.8.226) turned that gap into real damage.

- **Pausing is the presence of `~/.z2term/when/.paused`** — a file, not DataStore — so the CLI (`z2-when pause` / `resume`) and the app screen read **one single truth**. It also matches rules already being files.
- **Checked at one place: the entry of `runRule`.** No matter how many trigger kinds get added, none can forget to honour it. **Time triggers keep their AlarmManager registrations** (dropping them would require re-arming on resume and would lose the "next one" of `time:at`); they still fire, and are turned away at the entry.
- **Being held back is recorded too** (`status=paused`). Failing silently would remove the only way to answer "why didn't it run?".
- **`~/.z2term/when/.fired` holds one line per fire** (TSV: time, rule id, trigger, `run|paused|manual`; rotated at 50). Trigger payloads (SSID, SMS body) are never written — this file persists, so external strings must not accumulate in it.
- **▶ "run now" works even while paused** (`runRule(manual=true)`). The kill switch exists to stop things that run *by themselves*; it is not a setting that forbids the user from running their own rule. No trigger-specific env is passed (a made-up value would produce "worked when I tested it, fails for real"); only `Z2_WHEN_MANUAL=1`.

**Automation tab (`ui/settings/WhenRulesSheet`, 0.8.227)**: adds an "Automation" tab to 📜 with the rule list, on/off, run logs, ▶ run-now, **✎ edit (0.8.272)**, delete, the pause switch and recent fires. The same body opens from Settings › resident servers & automation (the two-entry structure `ServersBody` already uses). Shared widgets (`ToggleRow` / `HintBox` / `IconCell` / `PillButton` / `Field`) are `internal` in `ServersSheet` so the look is not written twice.

- **Rules can be named (0.8.303, `name=`).** The list heading used to be the trigger, so three `event:screen_on` rules side by side were **impossible to tell apart** (reported from a device). Rule files gained a `name=` line and the heading is decided in one place, `WhenRule.label` (`name` when set, otherwise `trigger`). **A rule without a name looks exactly as before** (no `name=` line is added to existing rules). Only when a name is set does the trigger get its own small line under the heading, so naming never hides what fires the rule. Recent fires use the name too (the log stores the trigger, so the id is looked up against the current rules and swapped for the name when there is one; entries for deleted rules keep the trigger). **Display only — it changes nothing about when or how a rule runs** (same standing as `order=`). The edit form does not require it: refusing to save over one optional line would be worse than an unnamed rule. ⚠ Newlines are folded to spaces exactly as in `run` (a rule file is one item per line, so a newline in the name would throw away the lines after it).
- **A released row must not jump (0.8.272, `ReorderList` and the Snippets tab).** The drag offset drops a row's worth every time a neighbour is crossed, so on release it is **left part-way**, and assigning 0 closed that gap in a single frame — it read as **"the rows swapped the instant I let go"** (reported from a device). The row now stays in its dragging state while `settlingId` holds it and slides to 0 over `REORDER_SETTLE_MS` (140ms). ⚠ The other half of the problem is that **saving is asynchronous**: taking the outer list back before the write lands makes the new order revert and then re-apply. `pending` (the committed order) is kept, and **while the members are the same and only the order differs, the outer list is ignored**; once the members change (add/delete) the outer list wins. The Snippets tab has its own copy of the drag logic, so **the same fix is applied in both** (rows there are fixed-height, so the implementations were not merged — change both).
- **Rows can be reordered by dragging ≡** (0.8.249, shared with the Servers tab). The order is written as an `order=<n>` line inside each rule file (`WhenManager.reorderRules`). **No separate order file is created** — that would break the rule-file-is-the-source-of-truth policy above. The CLI never writes `order`, and `z2-when on|off` only seds the enabled line, so an order set from the screen survives terminal-side edits. Rules without `order` (added from the terminal) sort last, by id. **Display order only — it does not affect execution or triggers.**
- ⚠ **A `Switch` must specify its OFF colours, not just its ON ones** (0.8.242). Passing only `checked*` to `SwitchDefaults.colors()` leaves the OFF side on the Material3 default (a dark `surfaceVariant`), which **dissolves into this app's dark background — the switch reads as "nothing is there"**. That is what happened to the pause toggle and the per-rule on/off (reported from a device). Always pass the set together: `uncheckedThumbColor = ZtsTextSecondary` / `uncheckedTrackColor = ZtsBgCard` / `uncheckedBorderColor = ZtsBorder` (the same combination the settings screen's `ToggleField` uses).

**Adding and editing rules moved into the UI (0.8.272, `WhenRuleEditForm`).** Up to 0.8.271 the screen was limited to "see, stop and try", leaving authoring to `z2-when` so as not to break §3.3 (source of truth is text, logic lives in the shell). In practice **that boundary caused the accidents**:

- The **full `run` never appeared anywhere on screen** (the list truncates to one line). Checking what a rule actually does meant opening the terminal.
- A **command pasted across two lines was silently cut short and kept failing with a syntax error** (hit on a real device). A rule file is one item per line, so a newline inside `run` throws away everything after it. A `wifi:connect` rule meant to start `sshd --lan` was cut in the middle of its `for` loop and did nothing but write `syntax error: unexpected end of file` to its log on every fire. Neither the CLI nor the screen checked for this.

**The source of truth did not change** — the screen writes the same `~/.z2term/when/<id>.rule` through the one serializer (`WhenRule.serialize()` via `WhenManager.saveRule`). A rule made in the terminal can be edited on screen and vice versa. A second source of truth appears when the app keeps its *own* store, not when two front-ends edit the same file.

- **Triggers are picked from a list** (`WhenTriggerCatalog`). One wrong character produces a rule that sits in the list and never fires, so the options are **ready-to-run complete forms** (`battery:below=20`) — correct from the moment they are picked. The validator mirrors the CLI's case statement in Kotlin (`WhenTriggerCatalogTest.everyOptionIsValid` stops the list and the validator from drifting apart). ⚠ When adding a trigger, change **both this and `z2-when`**.
- **Newlines are folded into spaces as they arrive** (screen and CLI alike). They are repaired rather than rejected, and the repair is reported (`Z2ApiMessages.whenRunJoined`). Wrapped pasting cannot be prevented, so **if it can be pasted it should end up working** (CLI side: `tr '\n\r' '  '`).
- **When `run` points at a single script, its contents are shown** (read-only, `WhenManager.readRunScript`), limited to the shared HOME with `canonicalPath` rejecting anything outside (the same promise as `TailStore.resolve`). **Editing the script stays in the terminal** — adding a script editor is the point at which the app really would start holding the shell's logic. Commands joined with pipes or `&&` are not treated as "a single script" (nothing gets guessed when the answer is ambiguous).

**CLI** (`z2-when`, placed in `/usr/local/bin` each launch by `Z2ApiScript`): `<trigger> [name=… if=… cooldown=… between=… days=…] run <cmd>` to add (the name and the filters go **right after the trigger, before `run`**, so that "everything after `run` is the command" keeps holding; `name=` is 0.8.303 and needs quoting when it contains spaces) / `list` (TSV; notes when paused; appends `[name=… if=…]` when a rule has a name or filters — **no extra column**, so scripts that `cut` the existing TSV keep working) / `events` (names usable with `event:`, 0.8.226) / `pause` / `resume` / `fired [n]` (0.8.227) / `remove <id|all>` (`rm`) / `on|off <id>` / `log <id>`. Ids are `w<epoch><pid>` (0.8.211 switched from a random suffix to the pid to avoid same-second collisions; a counter is appended if the file already exists). Rules created from the screen get **an id of the same shape** (`WhenManager.newRuleId` uses milliseconds in place of the pid — the list should not betray which front-end made a rule). **A newline inside `run` is folded to a space at registration time (0.8.272)**, so a wrapped paste no longer produces a silently truncated command (the repair is reported on stderr). **Stage 2 now covers cron/wifi/sms/sensor** (0.8.207–0.8.210). Later candidates: DST-boundary refinement for `time:cron`, light-threshold hysteresis, etc.

#### Daemons started by a rule were killed the moment the rule finished (`z2root --wait-tracees` / `HeadlessRun`, 0.8.251 + 0.8.253)

**Symptom**: a rule that **starts a daemon** — `z2-when wifi:connect run 'sshd --lan'` — does nothing useful. The run log faithfully records `✅ dropbear listening … on :2222` every time, yet seconds later nothing is listening and the port refuses connections. Running the same thing from a script registered as a resident server works, so it looks like "only automation kills it".

**The cause was in the engine** (`run_tracer` in `z2root.c`). z2root leaves its trace loop **the moment the main tracee (`sh`) exits** — `if (pid == child) alive = 0;` — without waiting for any other tracee. Once the z2root process is gone, `--kill-on-exit` (`PTRACE_O_EXITKILL`) has the kernel **kill every remaining tracee**. When the rule calls `sshd --lan`, dropbear daemonises and genuinely listens (the wrapper only prints `listening` after confirming the pidfile with `kill -0`), and then goes down with the engine the instant `sh` exits. **The log ending on success is not a lie** — it was true at that instant — which is exactly why it misleads. The resident-server path survives because its script keeps running, so the main tracee never exits (the GUI works around the same trap with `while x_running; do sleep 2; done`).

⚠ **Dropping `--kill-on-exit` is not a fix.** z2root installs a seccomp filter in the traced processes, so with no tracer left the filtered syscalls all return `ENOSYS` and the surviving daemon is broken anyway. The only correct answer is to **keep tracing it while it lives**.

**The fix has two halves** (either alone is useless — 0.8.251 fixed only the app side and **was confirmed on-device to still be broken**; 0.8.253 fixed the engine and completed it).

1. **Engine: `--wait-tracees` (0.8.253)**. With the flag, the loop does not exit when the main tracee does; it keeps translating the remaining tracees' syscalls and finishes only when they are all gone and `waitpid` returns `ECHILD`. The exit code still comes from the main tracee (a later-exiting tracee cannot overwrite it). **Only one-shot runs (`HeadlessRun`) pass it** — terminal tabs keep the default so that leaving the shell still ends the tab. ⚠ Never pass it to proot, which fails to start on an unknown option.
2. **App: teardown must not kill (0.8.251)**. EOF means "the foreground script finished", not "everything finished", so teardown is `waitFor()` → `detach()` (**close the fd, send no signals**) rather than `PtyProcess.close()`. For ordinary rules that leave nothing behind, `waitFor` returns immediately and nothing changes. Explicit stops (`HeadlessRun.stop()`) still use `close()` and take the whole tree down — a person asked for it, so the cascade is correct there.

⚠ Never call `detach()` while the root is still alive: closing the master fd makes the kernel send SIGHUP to the terminal's foreground process group, which takes the root down and produces the very cascade being avoided.
⚠ A daemon that survives this way lives **only as long as the app process** (it is a child of the engine). Anything that must stay up for good belongs in a resident server.

**Tab activity marks (0.8.229)**: an inactive tab shows a **small filled square while something runs in it**, and a **`✓` if it finished while you were looking elsewhere**. The judgement (`AppSession.isBusy`) was **already being computed for the close-confirmation dialog**, but nothing surfaced it, so checking meant switching tabs and back.

- **No mark on the active tab.** State display is pointless for what you are looking at, and `✓` means "finished while you weren't watching" — opening the tab is exactly when it stops being news.
- **Never blinks.** Blinking is hostile in a dark room and breaks the quiet look of a terminal. A 4dp square and a 9sp `✓`, nothing more.
- ⚠ **Do not mark tabs that cannot be judged.** `hasForegroundChild` returns a safe **`true`** when there is no way to tell (correct for its original purpose: not breaking TUI scrolling). Wired straight into the UI, that pins a permanent "running" dot on every SSH tab. `ProcessChannel.supportsForegroundChild` (true only for a local PTY) and `AppSession.busyKnown` were added, and the display reads those. The asymmetry matters: for the close dialog, over-confirming is harmless; for a mark, a lie stays on screen.
- Polling happens **once in the tab bar**, not per tab (avoiding 1s × tab-count `tcgetpgrp` calls). The rule is extracted into `nextEndedIds` and pinned by `TabMarkTest` — both "finished but no mark" and "marked but still running" are easy to miss by eye.

**The first three cards (`ui/terminal/IntroCards`, 0.8.231)**: right after install the screen is black with a `#`, and someone who doesn't know Linux stops there. Someone who does know sees "just a terminal" and **never notices what makes Z2Term different — that it can reach Android**. Three cards exist to hand out one "it worked" in the first 90 seconds. (0.8.286 added two reminder cards, making four; 0.8.314 folded those two into a single "open the guide" card and went back to three.)

- The three are "post a notification", "turn on the flashlight", "open the reminder guide": **two about touching Android, one entry point into the guides**, all of them things that answer in one line (nothing that makes you wait comes first).
- **A tap runs it (0.8.314, changed at the user's call.)** Through 0.8.313 a tap only put the command on the input line and the user pressed return. But a card can be tapped **mid-typing**, so pressing one after typing `ls -l` produced a line like `ls -lz2-macro install remind`, and return then ran something nobody meant. Now **`Ctrl-C` (0x03) throws the line away first**, then the command plus a newline goes out (`runGuideCommand`). ⚠ Do not write the command in the same burst as `Ctrl-C`: the tty **flushes the input queue** the moment it sees INTR (the default with `ISIG` and without `NOFLSH`), so the command would be flushed along with it. Leave 150ms.
- The wording shows **the command itself** rather than an explanation — this is not a screen to read, it is a screen to make something happen once.
- **Every card carries its own ✕ (0.8.314)**, so a step you do not want can be dropped **without sending it**. Tapped cards disappear too; when all of them are gone, or the header's ✕ is pressed, [AppSettings.introDone] is set and it **never appears again**.
- ⚠ **They only appear once one Linux OS is installed** (0.8.339, user report). Through 0.8.338 the condition was `introDone` alone, so the three cards also showed up on a device with **no OS at all** — where everyone starts after installing. Nothing is running there, so **a tap runs nothing**, yet **each tapped card still disappeared**, and once all three were gone `introDone` was set and they **never came back, without ever having done anything**. The check is `TerminalSession.hasAnyDistro()` (the same one behind `NeedOsInstall`) and is **re-evaluated whenever the terminal's state changes** — installing an OS from Settings starts the terminal, and that is the "install finished" signal, so the cards appear **right there in that tab, once**. ⚠ **Do not just suppress the display**: the point is to never spend `introDone` on a no-op, so leaving any path where it is set while the cards lose their turn is not a fix. ⚠ Anyone who already has an OS (`introDone=true`) must not get them back. What shows while there is no OS is `NoOsNotice` instead (below).
- Of the 32 proposals this was **the only one that could collide head-on with "don't add modes"**. Hence the spec is fixed up front: **at most three items, never a full-screen wizard, and one line deep in Settings to bring it back** (Maintenance). If a fourth item feels necessary, that is the job of `z2help` and of the guides below.

**Guides (`ui/terminal/GuideCards`, 0.8.314)**: a bundled sample macro has to be installed with `z2-macro install <name>` before it can be used, so **before installing, not even its name is visible**. 0.8.286 seeded a single snippet, `remind.sh help`, as "somewhere to look the syntax up", but **someone who has not installed it only gets "not found"** and no path to the install step (user report). The snippet seed is gone; **every sample now gets a guide made of step cards** instead.

- The entry point is **Settings › Maintenance › Show a guide**. There are nine guides, openable as often as you like.
- ⚠ **Each row is two lines: the macro name plus what it does** (`watch-basic` / `battery-alert` / `daily-report` / `otp-clip` / `otp-sms` / `unknown-call` / `remind` / `rss` / `qr`; 0.8.335–0.8.336, user reports). Up to 0.8.334 the list showed **only descriptions**, and "Starter: react to what happens" said neither what it was for nor **which macro it was about**. 0.8.335 swung to **names only**, and the answer after trying it on-device was that you then **cannot tell what any of them do**. Neither half is enough alone, so the name leads and the description follows. A guide card has only one heading line, so `guideTitle()` folds them into `rss — get notified about new feed items and read them`. ⚠ **That is the only place they are joined** — never write the name into the description string (it would then live in two places).
- ⚠ **A description has to say what the macro actually does** (0.8.337, user report). Pairing it with the name is not enough if the description itself is vague. All three that stalled a reader on-device were about **something you could only learn by running it**: "warn me when the battery is low" never said **at what %** (→ "warn me when the battery drops below a % I pick" — the guide asks for the % and puts it in `z2-when battery:below=`, so the description says so too), "read out a report every morning" never said **what** it reads out (→ "read out battery and connection every morning" — `daily-report.sh` speaks the level and Wi-Fi vs mobile), and "react to charging and headsets" does **not** react right away (→ below).
- ⚠ **`watch-basic` was moved onto `z2-when`** (0.8.338, user's call). Up to 0.8.337 it was a resident script polling the log, and the answer to its lag was to write "up to 15 s to notice" into the description (`POLL`, 15 s by default). But the lag came from **doing the waiting yourself**, and what it waited for — charging and headsets — is expressible with `z2-when`. `event:` runs the rule right where the broadcast arrives (`SystemEventService`), so handing the waiting to the app removes both the lag and the idle battery cost. The guide now registers two rules ("the charging trigger", "the headset trigger") and ends with `Z2_WHEN_EVENT=power_connected sh …` to **pretend charging just started** and check it. ⚠ **Never spread four events over four rules** — fold them with wildcards (`event:power_*` / `event:headset_*`). An automation tab full of the same macro says nothing about what you armed.
- ⚠ **`rss` and `rss-open` were merged into one guide** (0.8.335, user report). With "get notified about new feed items" and "open collected articles one at a time" sitting apart in the list, nothing says they are **two halves of the same subscription**. Collecting and reading are one sequence, so they are listed as one.
- ⚠ **Steps that need a value of your own ask before running** (`GuideStep.askRes`, 0.8.335, user report). Up to 0.8.334, tapping "write the feed URLs you want" registered `https://example.com/feed` **as-is** — being able to run the sample value means **a setting that cannot possibly work goes in silently**. URLs, times, thresholds and the QR payload are taken in a text field and cannot be sent empty (the `%s` in the displayed command is filled in on the spot).
- Look and send path are **the same components** as the intro cards (`GuideCardColumn` / `GuideCardRow`). Card order is step order: tap to run one line, ✕ to drop the ones you do not need.
- ⚠ **Never put translatable text inside a card's command.** Commands do not go through `strings.xml`, so anything language-specific would be sent verbatim in every locale. Keep examples **language-neutral** (`remind.sh list`).
- Cards without a command (turn a setting on, install a prerequisite package) are read-only; tapping one just marks it read and removes it.
- **Chosen from a GUI tab, the guide opens on a terminal tab.** A guide is a sequence of commands, and a GUI has nowhere to type them and nothing to show. Since it crosses screens, exactly one is held in `GuideHost` (an object).
- ⚠ **Never seed a snippet with a command that needs installing first.** Snippets run as soon as they are pressed, so a command with prerequisites only produces an error there. Anything with steps belongs in a guide.

**With no OS installed, nothing nags about downloading (0.8.314)**: the app bundles no rootfs, so the very first launch opened a download confirmation for Alpine. That **pushes one default before the user has chosen where to start**, and someone who wants to begin on Arch has to decline every time. Declining changes nothing, so **the same dialog returns with every new tab** (user report).

- The decision now lives in `TerminalSession.startupPlan()` (`suspend`), which returns one of `Start` / `ConfirmDownload(spec)` / `NeedOsInstall`. On `NeedOsInstall` (`ProotLauncher.hasAnyDistro()` is false) **nothing is installed regardless of the confirm setting**.
- Instead a single non-blocking notice card appears (`ui/terminal/NoOsNotice`). Tapping it opens Settings › Linux environment; ✕ closes it. The dismissal is remembered **only while the app is open** (`NoOsNotice.dismissed`) — reopening a tab does not bring it back, but the next launch does (with no OS the terminal genuinely cannot work, so "never again" is wrong here).
- ⚠ **While there is no OS it cannot be dismissed, and Settings pins the same notice at the top** (0.8.342, user's call). This card is the only thing pointing at "Settings › Linux environment", so **✕ left nothing but a black screen and a `#`** — with no hint anywhere about what to press to get Linux (and since no rootfs is bundled, **everyone starts there**).
  - ⛔ **0.8.340's "keep it dismissable, add 📥 to the toolbar" was withdrawn.** The idea was a 📥 "install an OS" chip shown only while no OS was installed, and the on-device verdict was **"pressing it just opens Settings and I still don't know what to do"**. **Adding an entrance does not help if the user gets lost past it.**
  - **It is undismissable** (passing `null` for `onClose` / `onSkip` makes `GuideCardColumn` / `GuideCardRow` draw no ✕). The card never blocks, so the terminal is still usable with it up. ⚠ **Only things that strand the user may be made undismissable** — the guides (`GuideCards`) and the intro cards (`IntroCards`) keep their ✕ (dismissing those still leaves a usable terminal).
  - **Settings pins the same notice at the top** (`NoOsSettingsNotice`). ⚠ It goes **outside the scrolling area** (directly under `SettingsTopBar`): inside, it disappears the moment you scroll down, which is exactly the "I reached Settings and still can't tell which item" complaint.
  - **Tapping it carries you to the Linux environment section.** ⚠ `SettingsGroup.LINUX` has `defaultOpen=false`, so **`SettingsGroupStore.setOpen(LINUX, true)` runs first** — scrolling to a collapsed group shows a header and nothing else. It then waits for `scrollState.maxValue` to reflect the newly expanded height before animating (scrolling too early stops short because there is not enough scrollable range yet; it gives up after 300ms and moves anyway).
  - The target offset is measured with `onGloballyPositioned` on `SettingsGroupSection(LINUX)` **plus the current scroll offset**, giving a "distance from the top" (`verticalScroll` shifts children by the scroll amount, so without it the value depends on where you happen to be scrolled).
  - **Both disappear once one OS is installed.** The Settings side re-checks `TerminalSession.hasAnyDistro()` whenever the terminal's state changes, so installing with the sheet still open clears it.
- ⚠ **The decision must await the persisted settings.** Through 0.8.313 `downloadOnStartSpec()` read `settingsFlow.value`, which is the `stateIn(Eagerly)` seed = the default snapshot (`distroId=alpine`). Reached before DataStore's first emit, it **judged Alpine instead of the selected OS** — that is exactly "running Arch, yet a new tab sometimes nags about downloading Alpine". `startTerminal` had already been awaiting for the same reason since 0.8.105; only this check was left behind.
- ⚠ **The OS chips in Settings must be pressable even for the selected OS when it is not installed.** The old guard was `id != selected`, but a fresh install starts with the default (Alpine) **selected yet absent**, which made that one OS the only one you could not install. With the automatic nag gone, this is the only entry point.

**Multi-line pastes are shown before they land (0.8.232)**: 📋 inserts the moment you press it, so when the source is a block of code you end up pressing return **without knowing how many lines went in**. Only **when the text contains a newline**, a 48dp bar appears with the line count and the first two lines.

- **The bar is drawn in the accent colour (0.8.255)**. Up to 0.8.254 it used `ZtsBgSecondary` on a `ZtsBorder` outline — **the same dark family as everything around it** — and "paste" was **green text with no button shape**, so people **did not notice it had appeared and moved on without pasting** (reported on-device). It now has a 2dp green border, an **80% green fill (20% transparent)**, a leading 📋 (tying it to the toolbar's 📋), and "paste" as a **filled dark button with green text**. ⚠ A 12% tint was tried first and reported again as **invisible — the terminal showed straight through it** — hence 90% (0.8.256), which read as too heavy and was settled at **80% (0.8.259)**. ⚠ Darkening the fill means inverting the foreground with it: green on green cannot be read, so the count, the preview and the ✕ are all dark, and only "paste" is knocked out in green on dark so it still reads as the primary action. ⚠ **The preview of the pasted text is neither thin nor faded** (0.8.259): drawn at 11sp monospace in regular weight at 70% dark it was reported as **impossible to read**, which defeats the point of showing the content at all. Because the fill lets the terminal through, the faintest foreground is the first to sink, so the preview is **bold at full dark** and its distance from the primary elements (the line count and "paste") is carried by **type size alone**. ⚠ **Its position is unchanged** (same slot as `SearchBar`): moving it would throw away what the user has already learned about where to look, so only the colour and the tap target got stronger.

- ⚠ **Never shown for a single line.** Widening this "for safety" instantly turns **the most frequently pressed button in the app into two taps**. The condition is exactly `text.contains('\n')` — no room to drift.
- The bar leads with the **line count**: here, how many lines are about to land matters more than what they say. Only two lines are previewed (this is not a place to read the whole thing).
- Pasting still **does not execute** (it lands on the input line; bracketed paste is unchanged). Same contract as receiving a share (B1).
- Placement and size match `SearchBar` — two different bars appear at the top of the terminal area, and they must not look unrelated.
- **Picking from clipboard history shows the same bar (0.8.250)**. History (double-tap 📋) pasted the moment you picked a row, so the very same multi-line text skipped the confirmation just because it arrived through a different door. What is risky is that it lands and then runs; where it came from is irrelevant. The test stays the single `text.contains('\n')` used by 📋 — **no per-entry-point rules**. The history sheet closes as you pick, so the bar appears behind it. ⚠ The GUI tab (`GuiScreen`) types via keysym and confirms **neither** 📋 nor history, which is at least consistent — if that changes, change both together.

**While searching, the scrollbar becomes a map (0.8.233)**: search reports a **count** ("3 / 17") but not whether those 17 hits sit near the top or are spread through the buffer, so you end up hammering ∨. Hit positions now appear as ticks on the scrollbar.

- **Nothing is added when not searching** (an empty `matchRows` draws zero ticks). A scrollbar's job is "where am I", not a permanent status area.
- **At most one tick per pixel row** (thinned at 2dp). A grep-like search with hundreds of hits therefore reads as density rather than a solid bar — without the thinning, more hits would mean less information.
- A tick is **tappable** and seeks there. Its hit area is as wide as the thumb and 12dp tall (never make someone aim at a hairline). Where a tick overlaps the thumb, **the thumb wins** (it is placed after), so dragging behaves exactly as before.

**Per-app screen brightness (0.8.234)**: in a dark room, **the brightest thing around is your own app** — green on black at full backlight. Reaching for the OS brightness means forgetting to put it back, and no amount of theming fixes it (it is a brightness problem, not a colour one). **Double-tapping 🔅** opens a bar with a single slider.

- It sets `WindowManager.LayoutParams.screenBrightness`, i.e. **this window only**. Going home restores the OS brightness.
- The default is `BRIGHTNESS_OVERRIDE_NONE` (leave it to the OS); it only applies **once you touch it**, so no setting and no mode is added. A single tap still toggles keep-screen-on (the same "tap = act / double-tap = details" contract as 📋 and ⌨).
- **The level you pick is persisted (0.8.242).** It started out unsaved — a "right now it's too bright" adjustment — but anyone who uses the app in a dark room ended up **dialling in the same value every time they opened it**. It now lives in `AppSettings.screenBrightness` (`Float?`) and the app opens at that brightness.
  - **null = leave it to the OS** still means exactly that after persisting: Reset does not overwrite the value, it **removes the key** (`remove`). We never write `0` and create a "saved at brightness 0" state. For anyone who never touches the slider, nothing changes — so persisting it still adds no mode.
  - It is written **once, when the finger lifts** (`Slider.onValueChangeFinished`). While dragging, a local state drives the window directly so a DataStore round-trip never sits between the thumb and the screen.
  - Brightness is a window-level setting, so it is **shared by terminal and GUI tabs**. `GuiTabScreen` applies the stored value as well, so launching straight into a GUI tab is not left at full backlight (the bar itself is still opened by double-tapping 🔅 on a terminal tab).
- ⚠ Floor of 10%. The worst outcome is a screen too dark to find the way back, so Reset always sits in the bar (without an exit, nobody dares touch the slider). **Persisting makes that floor matter more than before**, since a too-dark value would come back on the next launch.

**Explaining common stumbles (`core/TerminalHints`, 0.8.237)**: the handbook FAQ has the answers, but **the person who is stuck does not read it at that moment**. When a known pattern appears in the output, one line with the next step is shown at the **bottom** of the terminal.

- ⚠ **The terminal output itself is never rewritten.** This adds one line elsewhere; nothing enters the scrollback or the session log. Rewriting output would undermine the one thing a terminal must be trusted for.
- The scan point is **the same single place as logging (⚪)** — inside `readJob`, "the only place everything shown in a tab passes through". PTY chunks arrive in 8KB pieces, so a line can split across them; the previous 256 characters are carried over before matching.
- **Not scanned during alt screen.** Full-screen apps would trigger false positives with whatever text happens to be on their canvas.
- **Exactly four patterns** (ping / ports under 1024 / calling `/usr/sbin/sshd` directly / `/sdcard` invisible). Only stumbles whose answer fits in one line and that people actually hit. False positives make this instantly annoying, and an annoying feature gets deleted outright.
- **"Command not found" is never shown** (removed in 0.8.304). `command not found` is the most ordinary thing that happens in a terminal — it is not even a stumble — and the only advice possible was to list `apk add / apt install / pacman -S`, which is unreadable to someone who does not know which distro they are on. It was not a false positive; it was useless even when it hit.
- **Sixty seconds of silence per hint.** Firing on every `command not found` would turn the app into a nag.
- Settings › Display carries an **off switch** (default on), where someone who finds it intrusive can reach it immediately.
- Matching is an Android-free pure function; `TerminalHintsTest` pins both what must match and **what must not** (normal `PING 8.8.8.8 …` output, or a line you wrote yourself such as `# ping is not available`).

**Creating an SSH client key in the app (`channel/SshKeyGen`, 0.8.238)**: using a client key previously meant **pasting a private-key PEM into a text field**, which is close to impossible to produce on a phone. People stopped there before ever using SSH.

- "Create a key" generates an ed25519 pair, and from the same place you can **copy / share the public key, or add it to this device's sshd** — no more typing `cat … >> ~/.ssh/authorized_keys && chmod 600 …`.
- **The paste field stays.** Create or paste, two choices, no mode switch (anyone who can produce a PEM keeps their path).
- ⚠ **JSch cannot create it.** `KeyPair.genKeyPair(…, ED25519)` generates, but `writePrivateKey` throws `UnsupportedOperationException` — JSch **reads** ed25519 and does not **write** it. Generation therefore uses **BouncyCastle** (already a dependency for SSH) and writes OpenSSH format (`openssh-key-v1`). `SshKeyGenTest` then feeds the result to `KeyPair.load` to prove **JSch can read what we produced** — a mismatch here would surface as "the key was created but nothing connects", the hardest failure to diagnose.
- No passphrase. Requiring one on every connection lengthens the road to "it connects at all"; the private key stays on the device, encrypted by `KeystoreCrypt` as before.
- The public key is **not persisted** (it is only handed over right after creation, and can be re-derived from the private key). `authorized_keys` de-duplicates **on the key body**, so the same key with a different comment is not added twice.

#### Data limit (`service/NetGuard`, 0.8.388; what is counted, 0.8.389)

**What it does**: once **the whole phone's** mobile usage for this period reaches the amount you set, **z2term's own traffic stops**. Running out is usually noticed **after the carrier throttles the line**, and z2term can keep talking quietly over SSH and downloads.

**How far it goes (the judgement call here)**:
- ⚠ **Only z2term's traffic stops.** Other apps keep going. Without root the only way to cut off the whole device is **standing up a VPN and dropping the packets**, which drags in a different weight entirely: a terminal app permanently occupying the device's VPN slot (no other VPN alongside it). **The user chose "stop only z2term".**
- What stops: **new SSH / SFTP / resident-tunnel connections** (checked in the single spot `SshSessionFactory.create` — all three paths go through it, so no new entry point is needed), **SSH sessions already up** (the watcher cuts them), **OS image and GUI package downloads**, and **the APK download for app updates**.
- ⚠ **The update *check* (a few KB) is let through.** Knowing a new version exists is worth having even at the limit; only the download (tens of MB) is stopped.
- ⚠ **Traffic leaving from inside the Linux side (`apk`, `curl`, `git`…) cannot be stopped.** Android gives an app no way to cut off only its own processes. It is **still counted**, so the limit is still reached — and the screen says so outright (without that line, "it did not block anything" reads as a bug).

**What is never stopped**:
- ⚠ **Anything inside your home network** (the user asked for this): `192.168.*`, `10.*`, `172.16-31.*`, `127.*`, `169.254.*`, `fc00::/7`, `fe80::/10`, plus `localhost`, **single-label names**, `.local`, `.lan`, `.home`, `.internal`. **There is no reason to stop a peer that costs zero mobile bytes.** Names that fit none of those are resolved and treated as local if they land on a private address. ⚠ Resolution happens **only once blocking is decided** — doing it on every connect would just slow connections down while nothing is blocked.
- **Nothing stops while on Wi-Fi** (default), and only mobile bytes are counted. Turning that off counts both and stops regardless of the connection. ⚠ "Over the limit but not stopping because you are on Wi-Fi" is shown on screen — silently letting traffic through looks like a setting that does not work.

**The whole phone is what gets counted (fixed in 0.8.389)**: the first cut counted only this app's own UID. ⚠ **What people want to know is "how many GB are left this month", not how many bytes z2term spent** (from the user: "unless it counts the whole of Android, it isn't usable"). **Stopping on your own share has nothing to do with the carrier's cap.** It now reads `querySummaryForDevice` per transport.

- ⚠ **Reading the whole device requires the "usage access" permission** (querying your own UID did not). It cannot be requested with a normal dialog: the state is read through `AppOpsManager` (`hasUsageAccess`) and the settings screen is offered when it is missing (`openUsageAccessSettings`). Without `PACKAGE_USAGE_STATS` declared in the manifest the app **does not even appear in that list**, so it is declared (with the `ProtectedPermissions` lint suppressed).
- ⚠ **The grant only ever changes in system settings**, so it is re-read on `ON_RESUME` (the same treatment as the battery-optimisation exemption).
- ⚠ **No grant, or unreadable, means nothing is stopped** (`measurable = false`) — blocking traffic because the meter is unreadable would be a lockout with no way out. "Not granted" and "granted but unreadable" are **worded differently on screen** (the first carries a button into settings). Silently doing nothing is the worst outcome.
- ⚠ **Two SIMs are counted together.** The subscriber id is unreadable to apps from API 29 on, so they cannot be told apart.
- ⚠ Nothing is measured while the feature is off (the query is not cheap).

**The limit is set by a slider *and* a field (0.8.389)**: the slider steps through 100MB-50GB on **deliberately uneven stops** (even spacing squeezes the 1-5GB range everyone actually uses into a few millimetres). ⚠ **A slider alone cannot land on a contract's number** (4.5GB, 100GB are on no stop; from the user: "a slider alone can't be adjusted"), so a **field taking MB** sits beside it (1MB-1TB). ⚠ When a typed value sits between stops the knob shows the nearest one, but **the number displayed is what was typed** (it comes from the setting, not the knob). ⚠ The field **may be left empty** — filling it in mid-edit would make it impossible to retype.

**The period** starts at 00:00 on the reset day (1-28). Allowing 29-31 would **skip the boundary in exactly the months that lack that day**.

**The watcher**: an inexact repeating alarm every 15 minutes (`setInexactRepeating` — stopping overuse does not need seconds). Over the limit, it cuts outbound SSH and notifies **once per period** (a notice every 15 minutes buries the first one). ⚠ Alarms are forgotten on reboot, so `BootReceiver` and app start both re-arm it.

**Refusal is an exception** (`NetGuard.ensureAllowed`): doing nothing quietly reads as breakage, so **the reason lands on screen as-is** and the user can get to the setting and raise the limit. Disconnects write the reason into that terminal **before** the channel is closed.

`NetGuardTest` pins down what counts as home (`172.15`/`172.32` are not; single-label names are), when the period starts, that Wi-Fi suspends blocking, and that an unreadable meter never blocks.

#### Taking it with you (`backup/BackupManager`, 0.8.239)

**What it does**: writes settings, SSH connections, snippets, `z2-when` rules, macros and — since 0.8.380 — **your theme, tile assignments, icon drawings, dictionaries and what the keyboard has learned** into **one zip**, and restores them on another device. Until now a new phone, a factory reset or a reinstall meant **losing everything**; only once it can be carried does building a real setup feel worth it.

**What is in and what is out**: the rootfs (hundreds of MB), logs and `events.jsonl` are **excluded**. Separating "what a reinstall restores" from "what is lost forever" *is* the design here — mixing them produces a several-hundred-megabyte file that nobody ever makes twice.

**Settings are not copied field by field** (`settings/PrefsPortable`): the DataStore key/value pairs are serialised as-is. There are 60+ settings, and a hand-written mapping would **silently miss every newly added one** — a gap you only discover when changing phones. Types survive as one-character tags (`b`/`i`/`l`/`f`/`s`/`S`).

**Five things kept somewhere else were added (0.8.380).** ⚠ **Carrying the settings brought none of them along**: a custom theme lives in **a different DataStore** from `AppSettings`, tile assignments and icon drawings live in **SharedPreferences** (they are read while the app's process is not alive), and dictionaries and the IME's learning history are files in `filesDir`. Every one of them is built up by hand and none comes back from a reinstall.
- **A SharedPreferences counterpart of the whole-store conversion** (`settings/SharedPrefsPortable`), key-by-key mapping avoided for the same reason as `PrefsPortable`.
- **Restoring is not finished until what is on screen follows.** Tiles need the list synced (`TileStore.syncEnabledTiles`) or a slot **has an assignment but never appears on the edit screen**; icons need the cached bitmaps dropped or **the old drawing keeps showing**; dictionaries and history keep an already-loaded table in front of the file, so both need a `reload` (`UserDictStore.reload` / `ImeHistoryStore.reload`). ⚠ Skip this and "what I restored is not there until I restart" is indistinguishable, from the outside, from a broken restore.
- ⚠ **Home-screen widget assignments are excluded.** They are keyed by `appWidgetId` (a number the launcher hands out on placement), so on the other device they point **at a different widget or at none**. Carrying them needs an "apply to widgets as they are placed again" mechanism, which is a different design from taking a snapshot.

**Secrets (the central judgement)**:
- SSH passwords and private keys are encrypted with the Android Keystore, but **Keystore keys cannot leave the device**, so carrying the ciphertext produces something undecryptable on the other side. Exporting them means decrypting first.
- Therefore secrets are **excluded by default** (only names, hosts and ports travel). Including them **requires a passphrase**, and **no path — in the UI or the API — writes secrets without one** (`BackupManager.export` enforces it with `require`). One remaining path is all it takes for an accident.
- The crypto lives in `backup/BackupCrypt`: PBKDF2WithHmacSHA256 (210,000 iterations) derives a 256-bit key, AES-GCM wraps the payload. A wrong passphrase fails GCM authentication, so **"wrong passphrase" and "corrupted file" need not be distinguished**. `BackupCryptTest` pins that the output is not plaintext, that a wrong passphrase never passes, and that two exports of the same data differ.

**Restoring merges, it does not overwrite**: matching ids are replaced, anything absent from the backup is left alone — restoring an old backup must not delete what you built since. `peek` shows the counts **before** anything is applied. Zip entries containing `/` are dropped (this is a path where a file from someone else is opened, so nothing may escape the target directory).

**The destination is the user's choice** (SAF `CreateDocument`); the app never drops the file somewhere on its own.

**On a schedule (`backup/AutoBackup`, 0.8.386)**: the weakness of taking it with you was that **it only ever happened when you pressed the button** — and a new phone or a wipe arrives on the side that forgot to press it, so "there should be a backup" is the dangerous state. Settings > Maintenance takes an **interval (daily / weekly / monthly), a time, a folder and how many generations to keep**, and writes one file at that time, dropping the oldest beyond the limit.

- ⚠ **Nothing written automatically carries secrets.** Including them needs a passphrase, and automating that means **keeping the passphrase on the device**. The promise above — no path that writes secrets without a passphrase — is not bent for the sake of automation. Create one by hand to take secrets along.
- ⚠ **Only the files it made are tidied up.** They are named `z2term-auto-*`, apart from the hand-made `z2term-backup-*`, so pointing both at the same folder never costs you the one you made by hand. Sorting is **by name** (the name carries `YYYYMMDD-HHMM`, so that is chronological); modification times are not used because some destinations do not report them consistently.
- **Tidy up after writing, never before.** The other way round, a day where the write fails is a day where only the old ones disappear.
- **One alarm at a time**, re-armed on each firing (`ExactAlarm`: exact where allowed, Doze-piercing and inexact otherwise). ⚠ AlarmManager forgets its alarms on reboot, and **the only symptom is that backups stop appearing**, so both `BootReceiver` and app start re-arm it (idempotent). A failed run still arms the next one — failing once and never running again are different things.
- **Only failures are notified.** A success notification every day trains you to ignore it, including on the day it failed. A good day leaves its mark only in "last written" on the settings screen.
- ⚠ The folder is a SAF tree URI held by `takePersistableUriPermission`. Without it, **writing stops working the moment the app restarts, and fails quietly that night**. Access is checked before writing and reported as `err:noaccess` (i.e. "choose the folder again").
- The monthly day is **clamped to 1-28**: allowing 29-31 would **skip exactly the months that lack that day**.
- `AutoBackupScheduleTest` pins down "when is next" (daily / weekly / monthly, with the exact time pushed to the following run) and "what gets deleted" (never a hand-made file; at least one kept even at `keep=0`).

#### Snippet groups (`snippets/SnippetGroup`, 0.8.387)

**What it does**: a **group bar** (`[All] [daily] [git] [+ Group]`) sits above the snippet tab in 📜, and tapping a group shows only what is inside it. **Why**: snippets grow downwards, so **the ones you use most sink out of reach** (from the user: "as they pile up they end up at the bottom and get hard to pick").

**Shelves, not pages.** Pages that cut the list every N entries **move things around every time the count changes**. A shelf you named yourself stays where it is, however much goes into it ("everyday ones, git ones", as the user put it).

- **A snippet holds an id, not a name** (`Snippet.groupId`; empty = ungrouped = only ever listed under "All"). Holding the name would mean rewriting every member whenever a group is renamed, and a crash mid-rewrite would leave **snippets that appear nowhere**.
- ⚠ **Deleting a group never deletes what is in it.** The members go back to ungrouped and show up under "All". Losing the contents while tidying a shelf is the worst outcome, so the delete row says so out loud — without that line the button is too scary to press and shelves just accumulate.
- ⚠ **A reorder made while filtered must not go to `replaceAll`** (`SnippetStore.replaceVisible` / `reorderWithin`): that would **wipe every group that is not on screen**. The new order is poured only into the slots the visible rows occupy, so **reordering inside a group leaves the relative order of everything else untouched**. `SnippetGroupTest` pins this down.
- **Renaming and deleting live behind the `✎` on the open group's chip.** Not a long-press: an invisible gesture is the same as no gesture.
- **A new snippet lands in the open group.** That is what someone who just opened a group expects, and dropping it into "ungrouped" would mean moving it every time. Editing one into a different group **opens that group** — vanishing from the list on save reads as deletion, not as a move.
- **The group field stays out of the editor until at least one group exists.** A picker whose only choice is "ungrouped" pretends to offer a decision it cannot make.
- **Backups carry groups as their own entry** (`snippet_groups.json`). ⚠ Mixing them into the snippet array would make 0.8.386 and earlier unable to read the file. Older backups have no such entry, and everything comes back ungrouped. ⚠ Import only happens **when the entry is present** — writing an empty array would delete the shelves and scatter their contents into ungrouped.

#### History palette (`ui/snippets/ShellHistory`, 0.8.221, B2)

**What it does**: adds a "History" tab to the 📜 tools sheet — **filter past commands and tap one to put it on the input line**. Read-only; it never touches the input or rendering paths.

**No separate history of our own**: the content is **the shell's own history files**. Recording commands a second time inside the app would drift (e.g. `history -c` in the terminal, yet entries survive here).

**There are two history files** (missing this means "no history shows up"):
- `~/.bash_history` — written **after a command finishes** (`PROMPT_COMMAND='history -a'`), one command per line, no timestamps.
- `~/.zsh_history` — written **before execution** (`INC_APPEND_HISTORY`), in the extended format `: <epoch>:<duration>;<cmd>`, with a trailing `\` continuing onto the next line.

Both are merged **newest first**, deduplicated (the timestamped zsh entry wins). zsh's `SHARE_HISTORY` means every tab shares one file, so **per-tab or per-distro splitting has nothing to split** — one flat list is correct. The files grow without bound, so only the **last 256KB** is read, capped at 300 entries. Filtering is case-insensitive and requires **every whitespace-separated term** (`git log` also matches `git --no-pager log`).

**Tapping never runs anything** — it only inserts, matching the safety stance of B1 (share receiving). The parsing is Android-independent and covered by `ShellHistoryTest` (10 cases).

> ⚠ **zsh's history file is "metafied".** zsh writes every byte >= 0x80 as `0x83` followed by `(byte xor 0x20)`, so **reading it as UTF-8 directly always mangles Japanese** (it did, in 0.8.222). 0.8.223 runs it through `ShellHistory.unmetafy` first. The real `.zsh_history` on the test device is invalid UTF-8 as stored and decodes cleanly after the conversion (868 occurrences of 0x83). `.bash_history` is plain UTF-8 and is left alone.

**Only 50 rows are drawn**: the History tab lives inside the sheet's own `verticalScroll`, so a `LazyColumn` **cannot be nested** (same scroll direction). Composing 300 rows at once makes opening the tab sluggish, so 300 are kept in memory but only the first 50 are rendered, with the remaining count shown at the bottom — narrow the filter to reach them. The real `.zsh_history` on the test device held 3912 lines / 3380 commands, so this cap matters in practice.

#### Resident tunnels (`service/TunnelManager`, 0.8.221, A2)

**What it does**: **keeps port forwards alive after the SSH tab is closed**, and adds `-R` (remote → device).

**Why**: today the SSH tab (`channel/SshChannel`) connects, sets up forwards, then opens the shell for the screen — **the forwards and the screen hang off one session**, so closing the tab drops the forwards. And `-R` is **meaningless without residency**: if you must have a tab open on the phone to get in, you did not need to get in remotely.

**No new resident component**: `TunnelManager` holds screen-less JSch sessions and **rides along** with `ServerDaemonService`'s existing residency (FGS notification / WakeLock / WifiLock / `BootReceiver` autostart). Tunnels alone justify residency even with zero resident servers (`BootReceiver` checks for them too).

**The three rules from §6**:
1. **Explicit opt-in**: only profiles with `SshProfile.residentTunnel`. The toggle appears only once at least one forward exists, and its wording changes when a `-R` forward is present ("the remote side can reach into this device").
2. **Only hosts already in known_hosts**: a resident tunnel cannot show a host-key prompt, so an unknown host is **not connected, and the reason is recorded** — never silently trusted. Connect once from the SSH tab first.
3. **Exponential backoff on reconnect**: 5s doubling to a 5-minute ceiling (`TunnelManager.backoffMs`; `TunnelManagerTest` pins the boundaries and overflow). Never hammer a dead link.

**⭐ Keepalive traffic (0.8.367) — the actual cure for "the phone disappears from the LAN"**: a resident tunnel can sit connected without sending anything. SSH does not mind, but **when the device stays silent its Wi-Fi chip enters power save and other machines on the same LAN stop seeing it** (ARP is broadcast, and a sleeping station drops it). The CPU is awake and the FGS and WakeLock are working — only the radio disappears. Measured (screen off, charging, Wi-Fi, resident servers running):

| Traffic from the device | Duration | Share of the time it was unreachable |
|---|---|---|
| none (silent) | 9 min | **37%** |
| one outbound connection every 10s | 19 min | **1%** |

Hence `TunnelManager.KEEPALIVE_MS` = **10s** (`KEEPALIVE_LOW_POWER_MS` = 60s in low-power mode, where the user has asked for battery over reachability). With `serverAliveCountMax` = 3 a genuinely dead link is noticed in 30s and goes to backoff. **Set it before connecting** — JSch copies the value into the socket read timeout at the end of the handshake and sends one keepalive per timeout.

⛔ **A WifiLock cannot fix this** (verified in 0.8.367). `WIFI_MODE_FULL_HIGH_PERF` is non-functional and is read as `WIFI_MODE_FULL_LOW_LATENCY`, which is only active while connected to an AP **with the screen on** and **the app in the foreground** — i.e. never, for a resident service. Switching to `WIFI_MODE_FULL_LOW_LATENCY` does not help. **Only speaking periodically from the device does.**

**A forward that fails is retried in place (0.8.367)**: `-R` **can fail on the first attempt after a reconnect**. The remote sshd keeps the listening port bound for a while after the device drops, so `setPortForwardingR` is rejected with "port already in use". Giving up there would freeze the tunnel in a "connected but the forward is dead" state, so the session is kept and the forward retried every 30s (tearing the session down would take the healthy forwards with it). Forwards that are not up yet are marked `✗` in the status line (`TunnelManager.detailOf`).

**Direction**: `PortForward.reverse`. `setPortForwardingR(bindAddress, remotePort, remoteHost, localPort)` listens on the remote's `bindAddress:remotePort` and connects to `remoteHost:localPort` as seen from the device. The field names date from the `-L`-only era, so **their meaning swaps with the direction** — the `PortForward` KDoc and `describe()` are the reference.

#### Live tail widget (`widget/TailWidgetProvider`, 0.8.217, D2)

**What it does**: shows **the last N lines or the first N lines** of a chosen file on the home screen — "`tail` or `head` on your home screen". A window onto what macros and `z2-when` wrote, `events.jsonl`, or session logs, without opening the app. This is D2 from §10-2.

**Parts**:
- `widget/TailWidgetProvider` — drawing plus the ⟳ / ⚙ taps. Tapping the body opens the app.
- `widget/TailConfigActivity` (`APPWIDGET_CONFIGURE`) — choose the file (type a path or walk the folders).
- `widget/TailStore` — the per-widget file (path relative to `~`) and which end to show (`Mode.TAIL` / `Mode.HEAD`) in SharedPreferences, plus path resolution and directory listing. **The line count is not stored.**
- `widget/TailReader` — taking the last or first N lines. The decision part is Android-independent and covered by `TailReaderTest` (17 cases).

**Never reads the whole file**: neither session logs nor resident-server logs rotate (by design), so they grow without bound. `RandomAccessFile` seeks to **one end** and takes only `MAX_TAIL_BYTES` (16KB), then splits that into lines. The line on **whichever side the window was cut** can be sliced through a multi-byte character, so it is dropped (the first line via `truncatedHead` in tail mode, the last line via `truncatedTail` in head mode) — unless it is the only line, in which case dropping it would leave nothing to show.

**Either end, your choice (0.8.240)**: logs are read from the end, but **a file that is already finished is read from the start** — reports, config files, `z2doctor` output, the kind where what matters is written at the top. With tail only, those files showed nothing but their meaningless last few lines. The config screen offers `end (tail)` / `start (head)` and stores it as `TailStore.Mode` (unset means `TAIL`, i.e. the old behaviour). Which end you are looking at cannot be told from the text itself, so **the footer always says `tail` or `head`** (command names, so they are not translated).

**Choosing the file (reworked in 0.8.220)**: the first version listed up to 60 candidates found by walking `~`, newest first — **too many to choose from**, as on-device feedback put it. Now there are two ways:
- **type the path** in the field at the top (`~/.z2term/events.jsonl`, `.z2term/events.jsonl` or `/root/.z2term/events.jsonl` are all accepted)
- **walk the folders** one level at a time in the list below (directories first, then names; no extension filter)

`TailStore.resolve` **rejects anything outside `~` by canonicalPath comparison**, so this never becomes a window into the app's private data. Save only works when the path is a real file, and otherwise says why (never a button that silently does nothing).

**The line count follows the widget's height (0.8.220, fixed in 0.8.223)**: with a fixed count, **stretching the widget vertically left a gap**. Header, footer and padding are subtracted and the rest divided by one line's height, clamped to 2..30. Resizes come in through `onAppWidgetOptionsChanged`. The manual line-count setting was dropped.

> ⚠ **`OPTION_APPWIDGET_MIN_HEIGHT` is not "the height in portrait".** Android puts the **landscape** height in `MIN_HEIGHT` and the **portrait** height in `MAX_HEIGHT` (widths are the other way round). Up to 0.8.222 this code read `MIN_HEIGHT`, **underestimated the space, and left a gap at the top with log lines missing** (on-device feedback). 0.8.223 picks the value matching the current orientation and measures one line from **real font metrics** instead of assuming 13dp (which also tracks the device's font-scale setting).
>
> ⛔ **Never overestimate.** When the text is taller than the view, `TextView` stops honouring `gravity=bottom` and draws from the top, **cutting off the end — the newest lines**. Always round down.

**Update triggers** (like D1, **no new resident component**):
1. The OS periodic update (30 minutes, the OS floor)
2. A ⟳ tap
3. `TailWidgetProvider.refresh()` — **when a macro or `z2-when` rule finishes** (from `HeadlessRun.launch(onExit = …)`). It does nothing when no such widget is placed, so it costs nothing for people who don't use it.

**Shared with D1**: the background (`widget_bg`), the 40dp icon buttons (`Z2WidgetIconButton`), and the config-screen pieces (`ConfigSelectRow` / `ConfigButton` in `widget/WidgetConfigUi.kt`). Writing the look twice guarantees drift, so it was extracted from D1 while adding D2.

**Text size is selectable (0.8.255, both D1 and D2)**: pick it from the widget's ⚙ (D2: 8–20sp, default 10; D1: 9–20sp, default 11 — each the value hard-coded in the layout up to 0.8.254). `RemoteViews.setTextViewTextSize` overrides the XML. The unit is **SP** so the device's font-scale setting still applies (never DIP).
- **Presets, not a slider.** Nothing is gained by making someone choose between 13 and 14sp; a handful of steps is enough for a widget. The default is labelled "default" so it is easy to get back to.
- **D1 shifts by a delta, not a ratio** (`WidgetStore.scaled`). Its rows start at different sizes (title 13 / status and buttons 11 / footer 10), and a ratio squashes the small rows until the rows no longer balance.
- ⚠ **D2 feeds the same value into its line-count estimate** (`TextPaint.textSize` in `linesFor`). If that drifts from the body text, the last line gets clipped or a gap opens. Bigger text therefore means fewer lines — there is deliberately no separate "lines" setting, because text size and line count are two ways of saying the same thing and exposing both lets you build contradictory combinations.
- ⚠ **Notification text size cannot be set by an app** (Android exposes no API; notifications follow the OS "Display → Font size"). Custom `RemoteViews` could do it, but that abandons the standard look, expansion and action behaviour, and Android 12+ decorates custom views anyway — not worth it.

**No `configuration_optional`** (unlike D1): **there is no way to guess which file to watch**, so the config screen always opens when the widget is placed. The unconfigured state can still happen (the user backs out), and then the body says "tap ⚙ and choose a file".

#### Normalizing the toolbar order (`ToolbarButtons.mergeOrder` / `.normalizeOrder`, 0.8.213)

**The bug**: some toolbar buttons were drawn **twice** (it surfaced when the UI was rebuilt, e.g. after switching the language — only some buttons, and not reproducible on demand).

**Cause**: the stored `toolbarOrder` could end up with **the same id in two places**. On drop, the write fills only the *visible slots* of "the saved order of all ids" with the current on-screen order — but right after toggling a button's visibility in Settings, the `hidden` change may not have reached the on-screen order (`order`) yet, so a **stale list that still contains the just-hidden id** is handed in. Pouring that into the visible slots writes the hidden id into a visible slot too (duplicating it) and drops another id. The value lives in DataStore, so **once corrupted it survived restarts**. The read path (`mergeToolbarOrder`) passed duplicates straight through, so `key(id)` collided and the reordering state broke as well.

**Fix**: the decision logic moved into `ui/terminal/ToolbarButtons` as Android-free pure functions, covered by `ToolbarOrderTest`.
- `mergeOrder(saved, present)` — read path. Collapses duplicates in `saved` before merging with present, so the display is correct even with a corrupted stored value.
- `normalizeOrder(savedCsv, allIds, hiddenIds, shownOrder)` — write path. Drops hidden/unknown ids from the incoming order, then **collapses first-wins and appends anything missing**. The result is guaranteed to contain every id in `allIds` exactly once.
- On read, a stored value with duplicates is **normalized and written back immediately**, so already-broken devices heal themselves (the write updates `savedOrder`, the effect runs once more, and it converges).

#### Home screen widget (`widget/StatusWidgetProvider`, 0.8.212, D1)

**What it does**: puts the **current state** on the home screen (ssh endpoint / resident servers / `z2-when` rules / battery) and, on the bottom row, buttons that run `~/.z2term/macros/*.sh` **in the background without opening the app**. Where `z2-when` is trigger-driven, this is the entry point **a human presses**.

**The state line reads "running / registered" (0.8.224)**: `servers 1/3 · rules 2/5 · battery 87%`. The numerator is what is **live right now** (resident servers in `state=running`; enabled rules), the denominator is what is **registered in the app** (enabled `ServerEntry` entries — the same condition `ServerDaemonManager.start` uses to pick what to launch; the total number of `~/.z2term/when/*.rule`). With the numerator alone, up to 0.8.223, **a `0` gave no clue why** (nothing registered, or the resident service simply not started), and "rules" was **mistaken for the macro buttons right below it** (on-device feedback, 2026-07-25). The three numbers count three different things:

| Shown | Counts | Source of truth |
|---|---|---|
| servers | resident servers | Settings › Resident servers (`ServerEntry`) |
| rules | `z2-when` rules | `~/.z2term/when/*.rule` |
| bottom buttons | macros | `~/.z2term/macros/*.sh` |

**Parts**:
- `widget/StatusWidgetProvider` (`AppWidgetProvider`) — drawing, plus the button/⟳ taps.
- `widget/WidgetConfigActivity` (`APPWIDGET_CONFIGURE`) — picks which macros this widget shows (max 4). On API 31+ it is `configuration_optional`, so the widget can be placed without configuring it (then the first 4 macros in the directory are shown).
- `widget/WidgetStore` — stores the per-widget selection and the "last macro run" in **SharedPreferences**. The widget is drawn and tapped **while the app process may not be alive**, so it needs storage that can be read synchronously, not the coroutine-based DataStore. The macros themselves live in the user's files (`~/.z2term/macros/*.sh`); the widget only references them.

**Tap-to-run path**: the button's `PendingIntent` (a broadcast to ourselves) → `StatusWidgetProvider.onReceive` → `HeadlessRun.launch` → redraw without waiting for completion. **No new resident service is added** (nothing new to blame for battery drain). `HeadlessRun` (`service/HeadlessRun.kt`) was factored out of the `z2-when` rule runner: it centralizes "start `sh -lc` once on the selected distro and drain its output into a log file", so log-size handling and pty draining cannot drift between callers. Output goes to `~/.z2term/widget/run.log` (tail it from the shell to check). Macro names only ever come from real files, but they are still passed to the shell inside single quotes so nothing expands (same safety boundary as `z2-when`).

**Three things trigger an update**:
1. The OS periodic update (`updatePeriodMillis` = 30 min; that is the platform floor, it cannot be tightened)
2. The ⟳ tap on the widget (re-reads immediately)
3. `StatusWidgetProvider.refresh()` from the app — when the number of running resident servers changes (`ServerDaemonService`'s notification loop calls it **only when the count changes**) and from `WhenManager.reload()` (rules added/removed/toggled)

**Drawing constraints**: `RemoteViews` are inflated in the launcher's process, so they **cannot read the Compose dynamic palette (`AppColors`)**. The widget alone keeps fixed ZTS dark colors in `res/values/colors.xml` as `widget_*` and does not follow the selected theme (deliberate). Views cannot be created dynamically either, so **all 4 buttons live in the layout and the spare ones are set to `GONE`**. Reading the state involves file I/O and the settings DataStore, so drawing always happens under `goAsync()` on a separate thread.

**Unique PendingIntents**: both the requestCode (`appWidgetId * 8 + slot`) and the data URI (`z2term://widget/<id>/<slot>`) are made unique per widget × button. If either collides the PendingIntent is reused and one button runs the other's macro.

**Layout rework (0.8.216, from on-device feedback)**:
- **A large empty area was left at the bottom** because every row was `wrap_content` in a vertical stack, so any height beyond the content just stayed blank. The macro button row now takes `layout_weight=1` (height `0dp`) to absorb it, and the buttons themselves are `match_parent` so they grow. **Removing the gap and making the buttons bigger is the same change**, which fixes both "too much empty space" and "the buttons are small".
- **⟳ was untappable** — it was a bare 14sp `TextView` with no touch target. It is now `Z2WidgetIconButton` (40dp square with the `widget_button` background).
- **⚙ (config) added to the header**. Having to go through the launcher's "long-press → settings" was called out as awkward. It opens `WidgetConfigActivity` directly through `PendingIntent.getActivity` with `EXTRA_APPWIDGET_ID` (requestCode `appWidgetId * 8 + 6`).
- **Default size 4x2 → 4x3 cells** (`minHeight` 110dp → 140dp): with a 40dp header, two cells squash the buttons.

**Macro buttons are two lines (0.8.216)**: line 1 is the name with a state marker, line 2 is **when that macro was last started**. `WidgetStore` keeps the start time **per macro** (`run_at_<file name>`); up to 0.8.215 it remembered only one globally, so **running several made it impossible to tell which time belonged to which**. The three states are:
- `■ name` (accent) — running; tap to stop.
- `✓ name` — ran and finished **today**. Added because **a macro that finishes instantly makes `■` vanish at once, which looked like it had been stopped**.
- `name` — has not run today (time shows `––:––`).

The footer now shows **the macro that finished last**, since start times moved onto the buttons.

**`✓` lasts for the day only (0.8.224)**: up to 0.8.223 the `✓` meant "has run at least once" and, because `run_at_<file name>` was never removed, **it stayed forever**. `WidgetConfigActivity.clear` only drops the macro selection, so **removing and re-adding the widget did not clear it either** — wiping the app's data was the only way (on-device feedback, 2026-07-25). Since a button can only show `HH:mm`, a record from another day cannot be read ("is that 07:12 from today or from Tuesday?"), so **the mark and the time reset on their own when the date changes** (`WidgetStore.isSameDay` / `runStartAtToday`, Android-free and covered by `WidgetStoreTest`). The footer's "finished last" follows the same rule. The config screen also gained **"Clear run history"** for clearing it right now (`WidgetStore.clearRunHistory`). It applies **immediately, without waiting for Save** — the point is to clear a mark you are looking at, and deferring it would look like the button did nothing.

**Tap again to stop while running (0.8.215)**: `RemoteViews` has no long-press, so this is a **toggle on the same button rather than a new mode**. While running, the label becomes `■ name` in the accent colour (`widget_accent`) and a tap sends `ACTION_STOP_MACRO` → `HeadlessRun.stop`. "Running" is decided from an **in-process map** in `HeadlessRun` (`name` → `PtyProcess`). If the app process dies its child processes die with it, so **starting from an empty map is correct** (never "shows running when nothing is"). Stopping goes through `PtyProcess.close` (SIGHUP, then SIGKILL after up to 1s), so it must never be called on the broadcast thread — the receiver hands it to a background thread. On exit, `HeadlessRun.launch(onExit = …)` re-renders to clear the `■`.

**Config screen (two fixes in 0.8.215)**:
- **Insets**: `enableEdgeToEdge()` plus `windowInsetsPadding(WindowInsets.systemBars)` on the root. targetSdk 35 (Android 15) forces edge-to-edge, and without this the screen **slid under the status bar and the 3-button navigation bar, where it was neither visible nor tappable** (hit on a real device). Any new `Activity` must follow the same pattern as the existing screens.
- **Macro descriptions**: a list of file names says nothing about what each macro does, so the name (minus `.sh`) now carries a one-line description taken from **the comment at the top of the script** (`WidgetStore.describe`, Android-independent and covered by `WidgetStoreTest`). It skips the shebang and blank lines, and skips a self-referencing line such as `# ~/.z2term/macros/<self>.sh`. For `# <file name> — <description>` the leading file name is dropped (separator `—` / `–` / ` - ` / `:`, and **only when the part before it matches the file's own name** — a prefix like `z2term: …` is kept as part of the description). Truncated at 60 characters.

#### Notification button replies (`NotifyActionReceiver` / `z2-notify -b`, 0.8.169)

**Background**: `z2-*` could only push notifications out — there was no way to get an answer back.

**Implementation**: `-b <label>` adds buttons (up to 3, matching Android's display limit) and pressing one appends `notify_action` to events.jsonl (`{name}` = the notification's identifier, `{action}` = the label pressed). This is what makes **interactive macros** possible: ask, wait for the user, continue.

- Each PendingIntent uses `notificationId * buttons + index` as its requestCode so they stay distinct (sharing a requestCode makes Android reuse the extras, so every button would report the first one's label)
- The notification closes itself once answered
- Writing to events.jsonl is shared with the time trigger through `EventEmitter` (`render` gained an `{action}` field)

#### Bundled macro samples (`Z2MacroScript` / `z2-macro`, 0.8.167)

**Background**: the barrier to macros was never the syntax but **writing the first one from scratch**.

**Implementation**: ten working samples (event basics / battery alert / time trigger / one-time-code copy from notifications / one-time-code copy from SMS / calls from numbers not in the contacts / reminders by notification / feed subscription / opening what was collected / handing something over as a QR code) are placed in the rootfs at `/usr/local/share/z2term/macros/` and copied into `~/.z2term/macros/` by `z2-macro install <name|all>`.

**Folding the device-grown `rss.sh` back into the bundled sample (0.8.334)**: the state column added in
0.8.332 marked the real `rss.sh` as "differs" — and **the copy on the device was the richer one** (an
extension that had never been in the repository; `git log -S IMPORTANT` comes back empty). Pulling it into
the bundled sample means a wiped device does not lose it.

- **`important.txt` (feeds or words that must not get buried)**: the summary body only carries 3 lines, so a
  busy feed updating at the same time pushes the one that mattered out. Matches now get a notification each
  (the app hands out a separate id per notification, so nothing is replaced or dropped). ⚠ Capped at
  `HITMAX` (5) so a too-broad word cannot bury the shade under every article.
- **The URL goes in the notification's name (`-n "rss:<URL>"`)**: the button hands it back as `name` in
  `notify_action`, so the article you pressed is the one that opens. Reading the top of `new.txt` could only
  ever open the newest one, no matter which notification you pressed.
- ⚠ Also the worked example for why "differs" **is not one-directional**. Had 0.8.332 shipped its first
  label ("needs update"), `-f` would have deleted this extension — which is why the label is neutral now.

**Exact when it can be (0.8.333, `ExactAlarm`)**: alarms were placed with `setAndAllowWhileIdle`
(Doze-piercing but inexact), so **a phone left with the screen off fires several minutes to ~15 late**
(in Doze the OS only offers a slot every 9-15 minutes). Investigated after the user asked whether battery
saver causes a large drift.

- ⚠ **An app exempt from battery optimisation is allowed exact alarms without declaring
  `SCHEDULE_EXACT_ALARM`** (the `AlarmManagerService` allow-list exemption; it shows up on a real
  Android 16 device as `exactAllowReason=allow-listed` in `dumpsys alarm`). z2term already asks for that
  exemption for its resident servers, so it can move to exact **without adding a single permission**.
- It asks `canScheduleExactAlarms()` on every placement and falls back when the answer is no.
  ⚠ **The answer is never cached** — the grant can change from Settings at any moment. A
  `SecurityException` from losing it mid-flight is caught and re-placed inexactly: **never drop the
  alarm** (silently vanishing is the worst outcome).
- ⚠ **Fix all three at once.** Three things run on time: `z2-alarm` ([`AlarmScheduler`]),
  `z2-when time:*` ([`WhenManager`]) and the deadline of `z2-screen keepon` ([`ScreenTimeout`]), each
  calling `setAndAllowWhileIdle` on its own. Fixing only `z2-alarm` leaves **repeating reminders — which
  go through `z2-when` — just as late**. Placement now lives in `ExactAlarm` alone.
- **Make it visible from the terminal.** Each entry of `z2-alarm list` gains `exact`. ⚠ It stays an
  array (`z2-alarm list | jq '.[0].at'` keeps working). Without it, a late alarm cannot be told apart
  from a mis-scheduled one.

**Noticing that your copy has fallen behind (0.8.332)**: `install` **never overwrites** (your edits are
yours; that call stands). The price is that when an app update fixes a bundled sample, **the copy on the
device stays silently old**. `remind.sh` really did run two weeks behind, so the fix that made its result
notifications use `-h` (banner) never took effect — from the outside it just looked like "the tile does not
pop up", and the search went to Android's notification settings.

- **Make it visible in the listing.** `z2-macro list` gains a state column (`new` / `same` / `differs`).
  It compares with `cmp`, or `cksum` when there is no `cmp`. ⚠ **With neither, answer "differs"** — claiming
  "identical" is how you never find out that a fixed version exists (exactly the failure above).
- ⚠ **Do not call it "update".** All that is known is that they *differ*; **which one is newer is not**.
  The `rss.sh` on the device carries a feature the bundled one never had (per-item notifications driven by
  `important.txt`, plus a `z2-when` rule that depends on it) — "update" would have talked someone into
  `-f` and deleted it. It shipped as `要更新` first and hit exactly that on real data; the label is now the
  neutral "differs", and "the bundled one has moved on" is gone from the `install` wording too.
- **`install` tells "same" apart from "different".** A flat "already exists (use -f to overwrite)" never says
  whether there is a reason to overwrite. When they differ it prints **`diff` first** and `install -f`
  second (the order is pinned by `Z2MacroScriptTest`), so nobody overwrites unseen.
- **`diff <name>` is new** (left = your copy, right = the bundled one). `-f` also throws away your own edits,
  so whoever is told "these differ" needs a way to check whether overwriting is safe.
- `Z2MacroScriptTest` pins all three states, the `install` wording, which side `diff` puts you on, and the
  exit code of `--help`, by running the real `sh`.

**Moving the samples off residency onto `z2-when` (0.8.273)**: up to 0.8.272 the battery-alert,
daily-report and OTP samples were **resident scripts polling a log every 2 seconds**, and the macro
guide told people to "register it as a resident server if you also want the auto-clear". It surfaced
as battery drain on a real device; measuring it showed **the resident-server engine using 3 seconds
of CPU per minute (~5% continuously)**. Inside the engine a single external command costs thousands
of ptrace-mediated syscalls, and residency also holds a WakeLock/WifiLock that keeps the device out
of Doze.

- **`z2-when` triggers express the same thing** (`battery:below=` / `time:daily=` / `notify:otp` /
  `sms:otp`). For OTPs in particular **the app has already extracted the code into `Z2_WHEN_OTP`**,
  so the body-parsing awk disappears entirely. All four lost their watch loops; idling now costs nothing.
- **`watch-basic` moved onto `z2-when` in 0.8.338 too, so no bundled sample is resident any more**
  (user's call). Up to 0.8.337 it kept its watch loop as the skeleton for "pick up something
  `z2-when` does not have", with `POLL` widened from **2 s to 15 s**. But **the skeleton was picking
  up triggers `z2-when` can express** (charging, headsets), and on-device the polling interval showed
  up as a lagging reaction. ⚠ **The diff-reading technique still lives in MACRO-GUIDE 5-0** — do not
  bring a resident sample back (`diffSetup` / `diffLoop` are gone).
- `GeneratedScriptMarginTest.samples_doNotPoll` pins "**no sample has a watch loop**" and
  `watchBasicSample_reactsThroughWhen` pins "the starter sample branches on `Z2_WHEN_EVENT`".
  **Reverting to the resident shape fails the tests.**
- `samples_areValidPosixShell` was added at the same time, running every generated sample in both
  languages through a real `sh -n` (these are teaching material — shipping one with a syntax error
  breaks someone on their very first macro).

- Install **never overwrites an existing file** (only `-f` does), so user edits survive the per-launch re-provisioning
- `list` shows each script's second-line comment as its description; `show` / `run` / `dir` are also provided
- Comments inside the samples follow the app language (ja/en)
- **Each script declares how it is meant to be run** (`# z2-run: <how>`, 0.8.247). Install prints that line when present and falls back to the old "register under Resident servers" otherwise. ⚠ The unconditional resident hint was actively telling users to **make a run-once script resident** — `rss.sh` exits every time, so the supervisor would restart it and it would poll feeds forever (caught on a device). Keep `# z2-run:` **below the description line (line 2)**; putting it first would make the widget's button description (`WidgetStore.describe`) pick it up instead

**QR codes do not come back as an app feature** (`qr.sh`, 0.8.308): 0.8.219 put an SSH-connection QR on
the status widget and 0.8.220 removed it again — "not needed after all" — together with the in-house
encoder (`QrEncoder` / `ReedSolomon`). ⚠ **It does not return to the app side.** What is actually
wanted is "hand what is in front of me to another device without retyping it", and the distro's
`qrencode` plus image output (Kitty graphics) covers that (nothing bundled, F-Droid clean).
⚠ **A broken QR code still looks like a plausible pattern**, so it cannot be checked by eye; leaving
it to a proven implementation makes the result more trustworthy, not less (the in-house encoder had
to be compared against `qrencode` module by module).

- **A missing requirement prints how to install it in this tab, then stops.** `qrencode` exists in
  every distro but is not installed by default. ⚠ The package name differs per distro (Alpine alone
  calls it `libqrencode-tools`), so the hint follows whichever package manager `command -v` finds.
- **Image by default, `-t` for blocks, `-o` for a PNG.** ⚠ Block characters can leave gaps between
  rows depending on the terminal font — readable to the eye, not to a camera. The other way round,
  terminals that cannot show images (over ssh, say) print the image as gibberish. **Which one is
  right depends on the far end**, so both stay.
- **Long input is split at line breaks** (900 bytes per code, numbered `[1/3]`). ⚠ Lines are never cut
  in the middle, so multi-byte text survives; only a single line too long for one code cannot be sent.
- **The aspect ratio has to be assumed.** Terminals do not report their cell size, so 1:2 is hard-coded
  and `Z2_QR_ASPECT` is left for the environments where that is wrong.
- ⚠ **The `usage()` awk must not stop on a blank line** (it tests `NF`). The help text is long and its
  sections are separated by blank lines, so a stopping version prints only the first two lines — the
  same hole `remind.sh` fell into in 0.8.288.

**Reminders are deliberately not an app feature** (`remind.sh`, 0.8.275): the request was "remind me with a notification, repeating and one-shot, and it has to work with the app closed", and **no reminder screen and no reminder storage were added to the app**. Every part existed: one-shots are `z2-alarm`, repeats are `z2-when time:`, firing is `z2-notify -b`, the reply comes back through `event:notify_action`, and adding one without opening the app is `z2-tile` + `z2-ask`. Only the worked example was missing, which puts it in the same position as `rss.sh`.

- **One-shot and repeating live in different places.** ⚠ Making one-shots rules too would pile up dead entries: `time:at=` disables itself after firing, but **the rule itself stays**. So a one-shot is a `z2-alarm` booking (gone once it rings) with a **single permanent** `event:alarm` rule to catch it. Repeats go the other way — doing them with `z2-alarm` would need one catcher per reminder — so they become `z2-when time:` rules and get the automation tab's toggles and ▶ dry-run for free.
- **The text lives in a file; the notification name (`-n`) carries only an id.** `z2-notify -n <name>` comes back as `Z2_WHEN_EVENT_NAME` on `event:notify_action`, which is what lets **one rule** serve the buttons of both kinds. ⚠ Put the text in the name and the matching breaks the moment it contains a space or an emoji.
- **Exactly two hooks** (`event:alarm` / `event:notify_action`), no matter how many reminders exist. `GeneratedScriptMarginTest.remindSample_splitsOneShotAndRepeating` stops that split from eroding.
- ⚠ **Do not strip a leading zero with `expr`.** `expr "00" + 0` prints `0` but **exits 1**, so the right-hand side of `||` also runs and the cron expression ends up two lines long (it registered as `time:cron=0`: a rule that sits in the list and never fires — hit for real). Write `${v#0}`.

**Feed subscription is deliberately not an app feature** (`rss.sh` / `rss-open.sh`, 0.8.246): the request was for an RSS reader, and **not one line was added to the app**. Scheduled runs (`z2-when time:`), a notification with a button (`z2-notify -b` → `event:notify_action`), opening a browser (`z2-open`) and the live tail widget all existed already; the only thing missing was **a worked example of wiring them together**. A dedicated screen would add another single-purpose page, and feed formats are broken in the wild in ways that would then become **the app's problem to keep fixing**. On the terminal side, one malformed feed costs the user one extra line.

- **"Already read" is line subtraction** (accumulate in `seen.txt`, then `grep -Fxv`) — the same trick as `z2scan`'s baseline diff. **Feed dates and ordering are not trusted**; neither is reliable
- **Parsing is left to python3 (standard library only)**. RSS and Atom vary enough that a `grep`/`sed` parser breaks every time a feed is added; no pip dependency is introduced
- **One dead feed does not stop the rest** — a fetch failure that aborts the run means nothing arrives on a day with poor signal
- **`latest.txt` keeps the URL on the line**, because reading from a widget needs somewhere to go
- ⚠ **Per-line taps in the live tail were not built.** The body is a single `TextView` (RemoteViews cannot grow one view per line), so per-row taps would require converting it to a collection widget (`ListView` + `RemoteViewsService`) — which means rebuilding the "line count from height" and "head/tail" work that landed in 0.8.240. Deferred; instead `rss-open.sh` is assigned to a **status-widget macro button** (already "tap to run a `.sh` under `~/.z2term/macros/`"). Each tap moves one article down the list (`opened.txt` is subtracted, so nothing opens twice)

⚠ **Never write `/` immediately followed by `*` inside a KDoc.** Kotlin **nests block comments**, so writing `*` right after `macros/` opens the comment one level deeper; the closing delimiter then lands short and **the rest of the file is swallowed as a comment** (a flood of `Unresolved reference` plus a syntax error pointing somewhere completely unrelated to the real line). Hit for real while implementing 0.8.246.

**A `trimMargin` margin leak made `z2-macro` unusable (fixed in 0.8.187)**: in the usage block, the raw string already emitted a `|` for the line while each `joinToString` element also prefixed one, so the **first line became `||`**. `trimMargin()` strips only **one** leading `|`, leaving `|  echo 'usage: ...' >&2`; since the shell parses function bodies at parse time, **every subcommand failed with `syntax error near unexpected token '|'`** (so `z2-macro install` never once succeeded — samples could not be installed at all). The fix supplies the `|` from the separator side instead (`joinToString("\n|")`). The regression test `GeneratedScriptMarginTest` pins "no generated line starts with `|`" across all samples and both languages (a leading `|` is always a syntax error in POSIX sh, so it doubles as a soundness check).

#### Detection log: cap removed, growth warning added (`LogWriter`, 0.8.171)

**Policy change**: events.jsonl / notifications.jsonl now **append all history into a single file** (no size-based split/rotation).

0.8.168 rotated to `<name>.1` at 1 MiB keeping one generation, but for macros that "go back and aggregate over the log" a file switching midway makes analysis awkward, so the cap was removed (clean up yourself from the terminal, e.g. `: > ~/.z2term/events.jsonl`).

**Cost caveat**: the "newest at the top" mode reads and rewrites the whole file per entry, so its cost grows linearly as the file grows (prefer the default append-at-end for heavy use).

**Growth warning (`LogSizeWarning` / `LOG_SIZE_WARN_BYTES`, 0.8.172)**
- Shown right under the toggle only when **"Newest at the top" is ON and that log exceeds 10 MiB**, giving the current size (`12.3 MB` form) and what to do (turn it off / `: > <path>` from the terminal)
- Attached to **both** the notification log and the system-event log; each section looks only at its own file size and its own toggle (the path inside the message is that section's too)
- Append-at-end is unaffected by size, so nothing is shown there
- The size is stat'ed once via `remember` when the settings sheet opens (never per recomposition)

**Why 10 MiB**: prepending expands the whole file into a UTF-16 String via `readText` and then builds another one by concatenation — a transient **4–6× the file size in heap** — so depending on the device heap limit (128–512 MB) an `OutOfMemoryError` becomes reachable in the tens of MB; the threshold sits well before that.

**Presentation fix (0.8.173)**: the first cut (0.8.172) used the same 10–11sp secondary styling as the surrounding help text and "did not read as a warning", so it now sits in a **box with a 1px warning-coloured border and a faint warning-coloured background**, with the heading at 14sp bold and the body at 12sp in the primary text colour.

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
  - `--kill-on-exit -0 --link2symlink -r <rootfs> -b /dev -b /proc -b /sys -b <rootfs>/dev/shm:/dev/shm -b <shared_home>:/root`
  - **External storage bind**: `/storage/emulated/0:/sdcard`, `getExternalFilesDir:/storage/app`
  - `-w /root`, env: `HOME=/root TERM=xterm-256color LANG=C.UTF-8 TZ=… PATH=… TMPDIR=/tmp` + history-related env.
  - **`TZ` carries the device time zone in POSIX form** ([`PosixTimeZone`], 0.8.302). ⚠ Until then the distro
    had neither `TZ` nor `/etc/localtime` and was **always UTC**, so `date` disagreed with the device clock.
    Relative delays were unaffected, but **a wall-clock time like `18:30` was scheduled — and listed — off by
    the whole offset** (9 hours in JST; reported as "snooze works but the appointment never fires").
    - ⚠ **A zone name (`Asia/Tokyo`) is not used.** Only a distro carrying `tzdata` could resolve it, and
      without it libc silently falls back to UTC (Alpine ships without `tzdata`). A clock that depends on
      which packages are installed is not acceptable. The POSIX form (`<+09>-9`) needs nothing but libc and
      behaves the same under glibc, musl and busybox.
    - ⚠ **The abbreviation is the numeric `<+09>`, not `JST`.** `TimeZone.getDisplayName` can return
      `GMT+09:00` depending on locale, which POSIX cannot parse — and then **the whole `TZ` is ignored and
      the zone falls back to UTC**.
    - Daylight saving is written as two `,M<month>.<week>.<day>/<time>` rules. ⚠ **The time is the local time
      immediately before the change**, per POSIX, so the `timeDefinition` of `ZoneOffsetTransitionRule`
      (wall / standard / UTC) has to be converted first — copying it across **puts the change an hour out**.
      Rules that cannot be expressed (fixed dates, three or more changes a year) are written as no-DST with
      the current offset (a new tab regenerates it, so the cost is small).
    - `PosixTimeZoneTest` checks both the **shape** of the string and the **offset it resolves to**, against
      the real zone, in summer and in winter.
- **Shared home**: `filesDir/shared_home` is bound to `/root` across all distros (← the real backing of the terminal's `~`).
- **Providing POSIX shared memory `/dev/shm` (0.8.177)**: Android's `/dev` has no `shm`, so exposing the host `/dev` via `-b /dev` still leaves `/dev/shm` missing. A guest-side `mkdir /dev/shm` cannot fix it either, because the target really is the host `/dev` and SELinux rejects it with `EACCES`. In that state `shm_open()` fails with **ENOENT**, and **GUI apps built around shared memory abort themselves during startup**. The typical case is Gecko-based apps, which reach `MOZ_RELEASE_ASSERT(mHandle.IsValid() && mMapping.IsValid())` and die via `MOZ_CRASH()`, leaving the terminal with nothing but an unexplained `segmentation fault` (`--version` and `-h` do not touch shared memory and succeed, which makes this easy to misdiagnose as a loader or library problem). The fix layers a bind backed by **`<rootfs>/dev/shm` after `-b /dev`**. z2root resolves binds by longest match (`translate_abs`), so `/dev/shm` (8 chars) wins over `/dev` (4 chars) while every other device node under `/dev` stays on the host. PRoot treats binds as pure path translation too, so the same argument works there. The backing directory lives at `dev/shm` under the rootfs so that it **points at the same place** as the existing Kitty graphics shm transfer (`KittyHostTransferSource`), which rebases shm names onto `<rootfs>/dev/shm/<name>` — a different name would make the two look at different locations and the transfer would silently miss. The chroot path (hidden feature, requires root) uses real mounts, so it lays a tmpfs directly over `$RFS/dev/shm` and adds it to the umount cleanup list **before** `dev` (being nested, it must be unmounted first).
- **Generating `/etc/machine-id` (`ensureMachineId`, 0.8.177)**: some distro rootfs images ship an **empty** `/etc/machine-id` (0 bytes, `0400`), and in that state dbus cannot start a session bus, failing with "Invalid machine ID". GUI apps that require D-Bus (including those reaching it through the accessibility bus) then emit warnings or lose functionality. The launcher idempotently checks it on every start and writes a systemd-style value (32 hex digits, no hyphens) **only when the file is missing or empty**, leaving existing content untouched so the ID stays stable across sessions. Permissions are restored with `setWritable` before writing, since the rootfs copy may be `0400`.
- **`XDG_RUNTIME_DIR` on the terminal-tab path (0.8.177)**: everything under a GUI tab got it exported by `z2gui`, but **launching a GUI app directly from a terminal tab did not**. Leaving it unset makes Qt/GTK warn and leaves the D-Bus socket location undecided. For `display != null && exportDisplay` (a terminal riding along on `:N`) the launcher passes the same `/tmp/z2gui-xdg-<N>` the GUI uses; for `display == null` (standalone terminal/SSH) it passes `/tmp/z2-xdg`. On the z2gui path (`exportDisplay=false`) it deliberately passes **nothing**: `start_audio` and friends use `${XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-$DISPLAY_NUM}` and thus **prefer an inherited value**, so setting it unconditionally would collapse every display onto one directory and break the per-`:N` PulseAudio separation.
- **Per-distro HOME isolation** (0.8.72; `.claude/downloads` added in 0.8.73; z2root longest-match bind fixed in 0.8.75):
  `/root` as a whole stays shared, but **a few arch-dependent subdirectories are overlaid per-distro**.
  - Covered (`isolatedHomeSubdirs`): `.local` `.cache` `.npm` `.npm-global` `.nvm` `.cargo` `.rustup` `.config` `.claude/downloads`
  - Each `filesDir/home_overlay/<distroId>/<sub>` is bound over `/root/<sub>`, with `shared_home/<sub>` prepared as the mountpoint (the nested path `.claude/downloads` is created parents-and-all via `mkdir -p`)
  - PRoot adds the per-subdir binds *after* `-b <shared_home>:/root`; chroot does the same after `mount -o bind <SHOME> $RFS/root` (and lazy-umounts them *before* `root` on cleanup)
  - **Why**: native content under HOME (the binaries of node-based CLIs installed via npm-global, compiled addons in `~/.cache`, nvm's node itself, …) breaks when mixed across musl (Alpine) ↔ glibc (Arch/Ubuntu/Kali). Splitting it per-distro fixes that at the root
  - The rest of `~/.claude` (auth `.credentials.json`, settings, projects), documents, git repos, … stay directly under `/root` and remain shared
  - **Migration note**: existing contents of `shared_home/<sub>` become shadowed by the overlay (not deleted, just hidden) from each distro's view. Reinstalling the affected CLI once in each distro puts its native binary into that distro's overlay

  **Root cause of item 4 (the recurrence)**: prior versions shared `.claude/downloads`, so Alpine (musl) and Arch (glibc) overwrote the same native binary, leaving both unlaunchable with `Not a valid dynamic program`. 0.8.73 added the overlay bind, but **isolation did not take effect under the z2root engine** and the bug recurred (verified on-device 2026-06-11). The real cause was that z2root's path translation (`translate_abs`/`host_to_guest` in `z2root.c`) resolved binds by **first match in registration order**, so the earlier-registered parent bind `/root` shadowed the child bind `/root/.claude/downloads`. PRoot used longest-match, hence the engine-specific difference. **0.8.75 fixes both translators to longest-match (most specific = longest `guest_len` wins)**, so under z2root too only `.claude/downloads` resolves to the overlay while `.claude/.credentials.json` etc. resolve to the shared HOME.
- `resolveShell`: if the specified shell isn't in the rootfs, falls back to `defaultShell → /bin/sh` (usrmerge aware).
- **The "login shell" setting now applies to every entry point (0.8.165)**: it used to affect only the terminal tab (the `command` the engine execs directly), so **SSH logins and the GUI's inner terminal kept the distro default (bash, …)** — dropbear starts the shell from `/etc/passwd` and the GUI terminal starts `$SHELL`. `launch()`/`launchChroot()` now take `loginShell` and (1) `ensureRootLoginShell` rewrites the 7th field of the `root` line in the rootfs `/etc/passwd` (a `chsh` equivalent; also appends to `/etc/shells`), (2) it is exported as `SHELL` / `Z2_LOGIN_SHELL`, and (3) `z2gui`'s SHELL re-resolution takes `Z2_LOGIN_SHELL` as its first candidate — so terminal tab, SSH and GUI all use the same shell. If the shell is not in the rootfs (e.g. zsh on a bare Ubuntu) the usual fallback applies and `/etc/passwd` is left untouched.
- `isDistroReady`: checks the actual presence of `bin/busybox|bin/bash` etc. + a `.z2term-version` marker (compares `ROOTFS_VERSION` for bundled distros only).
- Idempotently injected on every launch: `ensureShellHistoryConfig` (history rc), `ensureMacroPathConfig` (macro dir on PATH), `ensureSshdWrapper` (`/usr/local/sbin/sshd` = dropbear wrapper), `ensureOsc7CwdConfig` (OSC7 hook for cwd restore), `ensureZ2ApiScripts` (`z2-*` bridge), `ensureZ2AdbScript` (`/usr/local/bin/z2adb`), `ensureZ2HelpScript` (`/usr/local/bin/z2help` + alias `/usr/local/bin/z2term`), `ensureZ2ScanScript` (`/usr/local/bin/z2scan`), GUI/z2run scripts, `ensureVersionScript` (`/usr/local/bin/z2version`).
- **The shell prompt is built from a sample and written to an rc file** (`ShellPrompt`, 0.8.364, user request): a bare rootfs leaves the prompt to the distro (`localhost:~#` on Alpine's ash), and changing it meant writing an rc by hand. Settings now closes the loop: **pick a shell and a sample → the body appears in a box → edit it in place → Apply**. ⚠ **The samples have to be good enough to keep** — a plain `user@host:~$` list just means everyone rewrites them, which defeats the point (user's feedback). They include two-line box-drawing frames, an `❯` that changes colour with the last exit status, Kali's `┌──(%n㉿%m)-[%~]`, and a coloured ribbon. ⚠ **The ribbon separator (powerline `` = U+E0B0) is assembled from an escape inside the rc** (`ARROW_RIGHT=$'\ue0b0'`, or `printf '\356\202\260'` for sh — the user's idea). ⚠ **Never embed the raw glyph in the source**: doing so once made it **silently vanish, leaving the separator empty**. That failure is invisible to the eye, so `ShellPromptTest` pins "no private-use characters in the generated body". ⚠ Assuming "private use area, therefore absent from the fonts" was **wrong**: reading the cmap tables shows **Fira Code and JetBrains Mono both carry it** (only IBM Plex Mono does not). Measure fonts, never guess. Since the value lives in the rc, anyone whose font lacks it can switch the escape to `\u25b6` (▶). ⚠ **It is written to the rootfs rc file rather than held as an app setting** — being able to fix it later with `vi ~/.bashrc` is the point, so the file is the source of truth and the settings box re-reads it every time it opens. Destinations are `~/.ashrc` (sh) / `~/.bashrc` / `~/.zshrc`. ⚠ **Those live in `filesDir/shared_home`, not inside the rootfs** — `launch()` binds `HOME=/root` to the shared home (so every distro shares one HOME), so writing to `distros/<id>/root/` reaches **a file nobody reads**. 0.8.364 did exactly that and broke as "it says written but the prompt never changes" (fixed in 0.8.365). ⚠ A successful write log does not prove it took effect — check the rc's mtime, or the actual prompt in a new tab. ⚠ **Only the region between the markers (`# >>> z2term prompt >>>`) is replaced**; nothing outside is touched (a user's `alias` vanishing silently is an unexplainable bug). ⚠ `appendOnceWithMarker` (history / OSC7 / PATH) **writes once and never again**, so it cannot be reused here — a prompt is re-picked and re-applied, and that helper would make the second apply a no-op. ⚠ **Colour syntax differs per shell**: bash needs `\[ \]` to mark zero-width or **long lines wrap wrongly**; zsh uses `%F{}` (and prints `\[ \]` literally); sh (busybox ash) understands neither and does not expand `\033` inside PS1, so the ESC is built with `printf` into a variable. Mixing them up "works" but breaks the look, so `ShellPromptTest` pins it. ⚠ **The optional right-edge clock never counts the terminal width**: `COLUMNS` may be unset under sh and is not refreshed when the screen rotates, so subtracting from it **always drifts**. Instead `ESC[999C` walks to the right edge (it stops there), `ESC[8D` steps back, and `ESC[u` returns to where the cursor was. zsh is the exception and uses `RPROMPT`, letting zsh measure and retract it as the line grows. In bash the cursor moves and the clock go **inside one `\[ \]` pair** (the cursor comes back, so the real width is zero).
- ⚠ **Opened the door for sh (busybox ash) to read an rc at all (0.8.364)**: a non-login interactive ash reads **only the file `$ENV` points at**. `launch()` set no `ENV`, so **nothing written to an rc took effect under ash** (the existing history / OSC7 blocks only ever reached bash and zsh). Since 0.8.359 moved Alpine's default shell to `/bin/ash`, that is exactly where the prompt needs to land. `ENV=/root/.ashrc` is harmless when the file is absent, and bash/zsh ignore the variable (bash only honours it in POSIX mode).
- **The macro directory (`~/.z2term/macros`) is on PATH out of the box on every OS (0.8.314)**: 0.8.287 appended it to the **end** of the PATH in the env `launch()` passes, but that misses a whole class of entry points — **a login shell rebuilds PATH from scratch in `/etc/profile`**, so over SSH (dropbear), under `su -`, and in the GUI's terminal the appended tail was gone and `remind.sh help` came back `command not found` (the guides and the docs both assume the name alone works). `ensureMacroPathConfig` puts the same setting inside the rootfs: `/etc/profile.d/z2term-path.sh` for login shells (Alpine/Debian/Arch/Kali all source `.sh` files under `profile.d` from `/etc/profile`) plus `/etc/bash.bashrc` and `/etc/zsh/zshrc` for interactive non-login shells that never read profile. ⚠ **Append at the end** (so a same-named command never shadows the OS one) and ⚠ **skip if already present** (a `case` test, so PATH cannot grow no matter how often it is sourced). The directory itself (`shared_home/.z2term/macros`) is created too.
- **`z2version` command (0.8.70)**: from the terminal, `z2version` prints the host app version (`versionName`/`versionCode`/package/execution engine/rootfs generation). It is rewritten on every launch, so it always reflects the *currently running* app — making APK↔guest version mismatches trivial to diagnose. `z2version --short` prints just the version on one line. Installed on all launch paths (proot/z2root/chroot).
- **`z2adb` command (0.8.88, self-adb)**: a helper that connects the device to *its own* adb daemon (Android Wireless debugging) over `localhost`, with no PC, USB, or root. Requires Android 11+ with Settings > Developer options > Wireless debugging enabled. Implemented in [`Z2AdbScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2AdbScript.kt); installed on all launch paths (proot/z2root/chroot).

  | Subcommand | Action |
  |---|---|
  | `z2adb setup` | Installs an adb client into the distro (apk: `android-tools` / apt: `adb` / pacman: `android-tools`, auto-detected via `detect_pm`) |
  | `z2adb pair <port> [code]` | Pairs |
  | `z2adb connect <port>` | Connects |
  | `z2adb shell` / `pm` / `logcat` etc. | Passed straight through to adb |

  - A bare port gets `Z2ADB_HOST` (default `127.0.0.1`) prepended; `host:port` is used as-is
  - Everything except `setup`/`pair`/`connect`/`status`/`help` is delegated straight to adb; `pair`/`connect`/`status` try a one-shot auto-install if adb is missing
  - PRoot/z2root pass TCP through (same path as dropbear), so localhost is reachable

  **Pre-starting the adb server (0.8.89)**: when a client runs and no daemon exists, adb normally restarts itself via `execl(own-path)`, but z2root returns `/proc/self/exe` as the in-APK `libz2root.so`, so that fails with ENOENT (an adb-wide issue; root-fixed in 0.8.111 by rewriting `/proc/self/exe` to the guest view). `ensure_adb` therefore calls `start_server`, which **pre-launches `adb nodaemon server` in the background without any self-exec**. Before launching it checks `/proc/net/tcp{,6}` and skips if the target port (`ADB_SERVER_SOCKET`'s port, default `5037`) is already LISTENing (`0A`) — an **idempotent guard** (`server_up`) that avoids the `Address already in use` abort from a double bind. Subsequent clients attach to the existing server without forking.
- **`z2help` / `z2term` command (0.8.90)**: a help command that prints, from the terminal, a quick reference of the custom `z2*` commands injected into the distro. With no arguments it shows a categorized list of every `z2*` command (version/info, phone features, GUI, connecting, help) with a one-line description, prefixed with the app version (`z2version --short`). The body is entirely static text placed in a quoted heredoc (`<<'Z2HELP_EOF'`) so it is not shell-expanded (no external input). `z2term` ships as a thin alias of `z2help` (`exec /usr/local/bin/z2help "$@"`) — a reserved command; to repurpose `z2term` later, just swap out `z2termAliasScript` in [`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt). The display language follows `LocaleHelper.language`. Installed on all launch paths (proot/z2root/chroot) ([`Z2HelpScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2HelpScript.kt)).
- **Display language of the `z2-*` CLI (0.8.228)**: everything the CLI prints (the leading help comments, usage, messages) now follows `LocaleHelper.language`. `z2help` / `z2scan` / `z2gui` / the sshd wrapper were already localized, but **the `z2-*` bridge helpers (`z2-when` / `z2-notify` / `z2-session` / `z2-alarm`, …) were hard-coded Japanese**, so English mode still printed Japanese. For an app distributed straight from GitHub with an English-first README, that effectively meant "unusable for English speakers".
  - The strings live in [`Z2ApiMessages.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ApiMessages.kt) (`Z2ApiMsg`) and are injected by `z2ApiScripts(lang)`. The point is to **never keep two copies of a script**: duplicated logic drifts when only one side is fixed, and that drift is only visible on the device. Only the wording is duplicated; the control flow is single.
  - Help blocks are stored **ready-made** (leading `#`, trailing newline) and concatenated *outside* `trimMargin()`, so a stray margin `|` cannot survive by construction.
  - The event names printed by `z2-when events` are **not translated** — they are identifiers you type into a rule. Only the descriptions and the notes are.
  - `Z2ApiScriptTest` runs the same checks (`sh -n`, margin leak, shebang) against **both languages**. Adding a branch must not leave room for one side to break silently.
  - `z2gui` already had `GuiScriptStrings` yet **15 lines were still Japanese** (Konsole rebuild, GUI install failure, audio, Qt fallback); they now go through the same mechanism.
  - Kotlin comments stay Japanese — they are for the developer and never reach the terminal.

- **`--help` on every `z2-*` (0.8.331)**: those help blocks sat at the top of each script as `#` comments, and **nothing could print them**. `z2-tile help` answered with a one-line usage, so the long explanation was only readable via `cat $(command -v z2-tile)` — for a feature whose only door is the terminal, that is the same as having no explanation at all (reported by the user).
  - **Add the way to show it, not another copy of it.** A one-liner at the top of each script uses `awk` to take "line 2 up to the first line of code" and strip the `#` (the trick `z2-macro` already used). Duplicating the text into a heredoc would leave the comment and the help to drift apart.
  - ⚠ **Do not stop at blank lines** (test `NF`). Sections are separated by lines that are just `#`, so a version that stops at a blank line prints the first few lines only and still looks like it works (`z2-macro` did exactly that until 0.8.286).
  - **Two spellings, on purpose.** Subcommand-style commands (`z2-tile` / `z2-icon` / `z2-when` / `z2-session`, …) take `-h|--help|help`; the ones that take a **sentence** (`z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-say` / `z2-ask`) take `--help` only, because `z2-toast help` has to keep showing "help". ⚠ No sentence-style command takes `-h`: `z2-notify -h` already means `--high` (banner), and a single exception to a rule is a rule nobody remembers.
  - `z2-toast` / `z2-share` / `z2-open` had no leading comment at all, so `toastHelp` / `shareHelp` / `openHelp` were written for them.
  - `z2help` now ends by saying that `--help` works on every command in the list — the list alone never told anyone where to go next.
  - `Z2ApiScriptTest.everyScriptPrintsItsHelp` compares the `--help` output of **every script in both languages**, line by line, against the leading comment block recomputed on the Kotlin side. "Prints the first few lines" must not pass.
  - **Break a long help into titled blocks (0.8.369)**: the `z2-tile` help ran for 33 lines with no paragraphs and no headings, so the one line you needed could not be found (reported by the user: "really hard to read"). ⇒ **the command list goes first, then blocks separated by `#`-only lines (= blank lines) under the headings "What you assign / What a tap does / Putting it on the panel / The drawing (-i) / e.g."**. Not one line of content was dropped. ⚠ **Line the `…` up by the width after expansion** — `$tiles` becomes a single digit on the Kotlin side, so a list that lines up in the source is crooked in the terminal.

- **`z2-ask` (0.8.267 — ask the person, get the answer back)**: `name=$(z2-ask "Branch name?")`. It asks **through a notification's reply field** (`RemoteInput`). `-t sec` (default 300) / `-H hint` / `-d default`.
  - **Why**: the only way to ask a person was `z2-notify -b <label>`, i.e. **buttons** — they could only answer with a choice you had prepared. Free-form answers ("which branch?", "where should it go?") could not be expressed at all, so macros gave up asking and hard-coded a value.
  - ⚠ **No Activity is shown.** A dialog interrupts whatever the person is doing, and **a macro running in the background (a `z2-when` fire) has no way to come to the front at all**. A notification with a reply field can be answered **from the shade without opening the app**, and it reuses the same "notification + broadcast" entry point as `z2-notify -b` — **no new resident process**.
  - ⚠ **The answer cannot come back as `dispatch`'s return value.** Every other verb answers on the spot; `ask` takes as long as the person takes. So `handleRequestFile` intercepts `ask` **before** dispatch, and the `resp` file is written by [`AskReplyReceiver`] → `completeAsk` once the reply arrives.
  - ⚠ **Dismissing without answering still replies** (the notification's `setDeleteIntent`). Writing nothing would leave the terminal side hanging for the whole timeout. Both cancel and timeout **exit non-zero**, so `ans=$(z2-ask …) || exit 1` expresses "give up if they don't answer" directly.
  - ⚠ **The `PendingIntent` must be `FLAG_MUTABLE`**: `RemoteInput` works by having the OS insert the typed text into the Intent, so `IMMUTABLE` means **the answer never arrives** (the one exception in a codebase that otherwise uses `IMMUTABLE` everywhere). `setAutoCancel` does not fire for replies, so the notification is **cancelled explicitly** (leaving it up would allow a second reply).
  - ⚠ **Only this command waits longer.** The `z2api` dispatcher waits 5s by default; that is now overridable with `Z2API_WAIT` (tenths of a second). Making it long for everything would mean **every** command sits through the full wait whenever the app is not running.
- **`z2-noti` command (0.8.236)**: prints the notifications currently on screen as TSV (key / package / app name / title / body) — and nothing else. Notification detection already existed, but it could only record; there was no way to ask "what is on screen right now" from the shell.
  - ⚠ **Pressing and dismissing are deliberately absent.** The original proposal included a verb to press a notification's buttons, which also means **pressing other apps' pay and send buttons** — the only feature among the 32 proposals whose misfires land outside this app. Per the summariser's call, only the reading half was implemented.
  - `getActiveNotifications()` belongs to `NotificationListenerService`, so it can only be read through the live, OS-bound instance ([`NotificationLogService.activeNotificationsTsv`]). Without the permission or the binding it reports that notification access is not granted.
  - Our own notifications are excluded, and tabs/newlines inside values are folded to spaces so the TSV cannot break.

- **`z2-tile` (0.8.258 — quick-settings tiles)**: put a macro or command on up to twelve **quick-settings** tiles. `z2-tile set <1-12> <macro.sh|command...> [-l label]` / `list` / `clear <1-12|all>`.
  - **Why this and not just the D1 widget**: a home-screen widget means going back to the home screen. Quick settings comes down in two swipes **whatever app you are in**, which makes it **the one entry point that reaches you mid-task** — "bring up sshd for tethering" without leaving the video you are watching.
  - **No new resident anything.** A `TileService` is only bound while the shade is open. Execution goes through the same [`HeadlessRun`] as D1: more entry points, still one execution path.
  - **The deal is the D1 widget button's deal**: tap to run, "on"-looking while running (`Tile.STATE_ACTIVE`), tap again to stop. Entry points do not each get their own feel.
  - **A tap collapses the quick settings panel (0.8.284).** ⚠ While the panel is open, Android does **not** show heads-up notifications (the banner at the top of the screen) — it just stacks them in the shade — and toasts land under the panel too. Tapping `remind.sh ask` from a tile buried the `z2-ask` reply box under the panel, so **you tapped it and could not answer** (reported from the device). ⚠ The only way to collapse the panel is `TileService.startActivityAndCollapse`, which **requires starting an Activity**, so the tap goes through [`TileCollapseActivity`] — a stand-in with no UI that closes itself the moment it opens (translucent theme, `noHistory`, `excludeFromRecents`). Android 14 throws `UnsupportedOperationException` for the `Intent` form, so the `PendingIntent` form is used from there on. ⚠ Toggle-style tiles (torch and friends) collapse the panel as well: rather than making entry points behave differently, **what a tap did is always visible**.
  - **When on and off are separate commands, `--off` puts both on one tile (0.8.261).** `z2-tile set 3 z2-torch on --off z2-torch off`. Tapping alternates between them and the tile **looks on while it is on**. A slot holding only `z2-torch on` runs `on` on every tap — **the tile can never turn it off** — so the off side became writable (the user's proposal).
    - ⚠ **Two bare positional commands cannot work**: `z2-tile set 1 ls -la` would become "on = `ls`, off = `-la`" (joining the remaining arguments into one command is the existing reading). Hence an explicit separator, and like `-l`, **`--off` is picked up wherever it appears**.
    - ⚠ **This green is only what the app remembers** — nothing observes the real state (Android offers no way to read whether the torch is lit). Running `z2-torch off` in the terminal instead leaves the tile showing on. `z2-screen` is special-cased precisely because **the app does hold that state for real**.
    - **Turning off does not kill anything**: it runs the off command that was written down, rather than killing the process from the on side (killing `z2-torch on` does not put the light out). The run keys differ per side too — sharing one would count the on side as "running" the moment the off command starts.
    - **Reassigning a slot drops the remembered on state**, otherwise the first tap on something newly placed starts from the off side.
  - **A command that holds a state shows that state instead (0.8.260).** `z2-screen keepon 1h` writes a setting and **exits immediately**, so "on while running" makes it **light up for an instant and then read as idle, leaving no way to tell whether the screen is still being held** (reported on-device). For a slot holding `z2-screen keepon <duration>`, the "on" look means **"the screen is still being held"** ([`TileStore.isScreenKeepOn`]). The source of truth is the single file `ScreenTimeout` already keeps, so **typing `z2-screen keepon off` in the terminal moves the tile too**. ⚠ `keepon off` and `status` are excluded — one-shot operations with no state, which would read as "an on state that disappears when you press it".
    - **Releasing is handled in-app; holding still runs the command.** Copying how `1h` is read into the tile would leave two places interpreting durations (the sh side owns the conversion to seconds).
    - **The remaining time is appended to the name** (`no sleep 60m`, [`TileStore.labelWithSuffix`]). ⚠ **Some devices never render the subtitle at all** — on the test device (Android 15) a tile is an icon and a name, nothing else, and the "60 min left" that 0.8.260 put in the subtitle **was readable by nobody**. The name is the only surface there is, so the state folds into it, **cutting the name rather than the figure** (before tapping you want "how much longer", not "what is this"). The subtitle is still filled in, so devices that do render it also get "tap to release".
    - ⚠ **It freezes at the moment the shade came down** — a `TileService` is not redrawn while the shade is open, so there is no countdown. Hence nothing finer than minutes ([`TileStore.remaining`], **rounded up**) — rounding down would print "59 min left" immediately after `keepon 1h` and read as short measure.
    - ⚠ **Do not write "green".** The colour of `Tile.STATE_ACTIVE` is **the device's accent colour** and is not the app's to choose (pointed out on-device). The wording is "looks on" throughout.
  - **Unassigned slots stay out of the quick-settings list (0.8.260).** Permanent entries for every slot fill up the edit screen of someone who never uses tiles (reported on-device). Slots cannot be **added** at runtime, but `PackageManager.setComponentEnabledSetting` can **disable them individually**, so the shrinking direction is available at runtime ([`TileStore.syncEnabledTiles`], kept in step by `z2-tile set` / `clear` and at startup). ⚠ **With nothing assigned, not a single tile is listed (0.8.271).** From 0.8.260 to 0.8.270, slot 1 was shown even when unassigned, meaning to leave one place outside the app where the feature could be discovered — but for someone who never uses tiles it was just **an empty slot that could not be removed** (reported on-device). ⚠ Don't impose a discovery slot on people who don't want the feature. `TileStoreTest.onlyAssignedSlotsAreListed` guards against reverting to `n == 1 ||`. ⚠ If a disabled slot was already on the panel, **the OS drops it from the panel too**, and reassigning does not bring it back, so `z2-tile clear` also means "put one tile away".
  - **Assigning a slot asks the OS whether to place it (0.8.355, Android 13+).** ⚠ **The name and icon in the "edit tiles" list come from the manifest** (`z2term 1` … `z2term 12` plus the default drawing), and **Android has no API to change them at runtime**. Once placed, [`Z2TileService.render`] puts the real name and drawing on the tile — which is why the user saw "**when I add a tile, the icon and name are the initial ones and I cannot tell what I just added; moving it into the list makes them correct, but not before that**" (reported on-device). ⭐ `StatusBarManager.requestAddTileService` is **the one entry point that takes a label and an icon as arguments**, so `z2-tile set` calls it right away and the dialog carries **the name and drawing that were just assigned** ([`Z2TileService.requestAdd`]). ⚠ **This does not place anything by itself** — the dialog belongs to the OS and can be declined; **where tiles go is still the user's call** (there is still no API for an app to place its own tiles). ⚠ It **only appears while the app is in the foreground** (`TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND`), so registering from a macro running in the background shows nothing. ⚠ **Only one request at a time** (`…_ERROR_REQUEST_IN_PROGRESS`), so a macro assigning two slots in a row silently loses the second. ⇒ **`z2-tile add <slot>` exists to ask again afterwards.** ⚠ Either failure still leaves **the assignment itself done**, so **`set` does not report whether the question was asked** — presenting that as a failure reads as "the assignment failed too". On Android 12 and older, `add` declines with the reason and points at the edit screen (look for `z2term <slot>`).
  - **Settling the drawing in the same line (`z2-tile set … -i <drawing>`, 0.8.357).** The dialog carries whatever [`IconStore.tileIcon`] returns — **the drawing the slot holds at the moment it asks** — so picking one with `z2-icon` always lands *after* the question, which is **backwards** (as the user put it: "how do I place the tile once I've changed the icon?"). ⇒ `-i` **settles the drawing in the same line as the assignment**, and the dialog shows that drawing. Names come from the `z2-icon sample` list (bundled drawings and your own alike, via [`IconStore.findSample`]). ⚠ **The drawing is resolved before anything is assigned** — an unknown name assigns nothing at all (a slot that is set but wearing the wrong drawing is harder to notice than one that was never set). ⚠ **Apply `-i` before calling [`IconStore.autoAssign`]**: [`IconStore.set`] leaves the "set by hand" marker, which keeps `autoAssign` off that slot — the other order lets the automatic drawing win. ⚠ **A tile already on the panel does not need `-i`**: changing it with `z2-icon` is picked up by [`Z2TileService.render`] right away. `-i` only matters *before* placement (the dialog and the edit screen).
  - ⚠ **Nothing is waved through from the lock screen.** It goes via `TileService.unlockAndRun`, so a locked device is asked to unlock and the command runs only if it is. **There is deliberately no setting for this** — "someone who picked up your phone can fire a command off the shade" is the kind of thing there is no reason to opt into, and the damage lands outside this app (the same line that kept "press" out of `z2-noti`).
  - **Twelve slots (raised from four in 0.8.294).** A tile needs one `TileService` written out in the manifest, and **the number cannot grow at runtime** (Android's rule), so it is twelve classes differing only in their slot number (`Z2Tile1`-`Z2Tile12`).
    - **Raising it was only possible because unassigned slots already stay out of the list** (0.8.260 above). With empty slots absent from the edit screen, **there is no cost to having spares**, and nothing becomes resident either (a `TileService` is bound only while the shade is open). "Four slots run out once you have a few macros" was reported on-device, and the number could be widened without changing the design.
    - ⚠ **Shrinking is not safe.** An assignment left in a slot beyond the new limit falls outside `z2-tile clear`'s range check and **can no longer be removed or tapped**. `TileStore.COUNT`, the manifest `<service>` entries and `Z2TileService.CLASSES` must always agree.
    - The number is **never written into the help text** (`Z2ApiMsg.tiles` reads `TileStore.COUNT`). A literal `1-4` in the help is exactly what stays stale when the count changes.
  - **You never say whether it is a macro or a command.** A name matching a file in `~/.z2term/macros/` is a macro; anything else is a command ([`TileStore.scriptFor`]). A `--macro` flag would make you decide that every single time.
  - **A macro can take arguments (0.8.275 — `TileStore.scriptOf`).** The test changed from "the whole assignment equals a macro name" to **looking at the first word only**. Before that, `remind.sh ask` fell through to the command path the moment an argument was added, and ⚠ **the macro folder is not on PATH**, so it ended in `not found`. **The tile did nothing when tapped** and the failure only reached `~/.z2term/tile/run.log` — indistinguishable from a correct assignment from the outside (hit on-device). Driving one macro through sub-commands is a natural thing to write, so it is let through. Arguments go to the shell as written (`$HOME` and `$(…)` work); only the macro name stays single-quoted (it can only be a real file name, so there is nothing to expand). `TileStoreTest.aMacroKeepsItsArguments` pins it.
  - **A `.sh` that is not in the macro folder is refused at assignment time (0.8.275, in `z2-tile`).** Same thinking as `z2-when`'s spelling check: **stop it when it is written**. Letting it through produces the dead tile above, with no way to trace it. ⚠ The check only covers names that end in `.sh` **and contain no path separator** — a full path (`sh /path/foo.sh`) is passed through as a command as before. `Z2ApiScriptTest.tileSetSeparatesLabelFromCommand` pins both the accepted and the refused shapes through a real `sh`.
  - **The label comes from the first word**, so the same macro on two slots gets **the same name** (`remind.sh ask` and `remind.sh peek` are both `remind`). Use `-l` to tell them apart. ⚠ Arguments are not folded into the name: long names are silently clipped on some devices. With twelve slots, putting the same macro on several of them with different arguments became practical, so `-l` matters more, not less.
  - **`-l` is picked up wherever it appears.** `z2-tile set 2 'z2-screen keepon 1h' -l "no sleep"` is how people naturally write it — parsing only leading options would fold `-l no sleep` into the command and run it with junk arguments forever after. `Z2ApiScriptTest.tileSetSeparatesLabelFromCommand` pins both orders through a real `sh`.
  - **The label is the feature**, so the default is the macro name without its extension, or the command's **first word** (the full command is guaranteed to be cut off at quick-settings width). Capped at 12 characters; `TileStoreTest` holds it.
  - **You do the placing.** An app is not allowed to drop its own tiles into the panel; use the panel's edit (pencil) screen.
  - **No entry point in the settings screen (0.8.271).** From 0.8.258 to 0.8.270, Settings carried a "Quick-settings tiles" section listing the assignments plus an Android 13+ `StatusBarManager.requestAddTileService` button ("shall I add this?"). **The user asked for the whole section to go**, and the `requestAddTileService` call went with it. ⚠ **The feature itself stays** — assigning and listing are done entirely through `z2-tile`, leaving exactly one source of truth on the device side (the same way `~/.z2term/macros/` is for macros). With no settings entry point, this pairs with "nothing listed when nothing is assigned" (above) so the feature stays out of sight for people who don't use it.

- **`z2-icon` (0.8.294 — draw the status-bar and tile icons yourself)**: `z2-icon pick <target>` / `sample [name|target name]` / `edit <target>` / `set <target> [file|-]` / `show <target>` / `clear <target|all>` / `grid [24|48|64]` / `scale <target> <24|48|64>` / `list`. The targets are **`notify`** (one drawing, used by every notification this app puts out) and **slots 1-12** (one drawing each).
  - **Why a dot drawing**: Android **repaints both the status-bar icon and the tile icon in a single colour of its own** (tiles change colour between on and off). ⚠ **There is no colour to choose — only the shape gets through.** A black-and-white grid is therefore the whole of the available expressiveness, and taking a drawing instead of an image means **what you write is what appears** (what `show` prints is what you get), rather than downscaling and thresholding something behind your back.
  - **The grid is 24 / 48 / 64 (0.8.379, [`IconStore.GRIDS`])**. ⚠ **"they end up about 24px across, so 24 is enough" was only ever true of the status bar** — a quick-settings tile is drawn much larger, and there 24 dots read as a staircase (the user's report). Handing the same drawing to two places of different sizes means a single fixed grid always shortchanges one of them.
    - **The grid belongs to the drawing** and is **read back from the drawing itself** ([`IconStore.gridOf`] — the number of lines in the normalised text *is* the grid). Kept anywhere else it can disagree with the drawing, with nothing to say which one is right. Drawings already saved are 24 lines and still read, so **nothing has to be migrated**.
    - **[`IconStore.parse`] picks the smallest grid the ink fits in.** ⚠ Never move a drawing onto a bigger grid by itself — **a bigger grid does not make a bigger icon**, so a 24 drawing dropped onto a 64 grid just **comes out smaller on the tile**.
    - **The bitmap is always 192px square, whatever the grid** ([`IconStore.OUT_PX`] — 24x8 / 48x4 / 64x3). ⚠ **Every grid must divide 192.** One that does not shifts each dot by a pixel, so the finer the drawing the worse it looks (`IconStoreTest.everyGridDividesTheBitmapSize` stops that).
    - **A drawing you already have can be laid out again (`z2-icon scale <target> <grid>`).** Redrawing a 24 drawing at 48 is a redraw in all but name, so there is a way to change the grid **while keeping how it looks** (the drawing keeps its share of the grid). ⚠ Laying one out on a smaller grid drops thin lines (no way back, so only when asked for).
    - **Smoothing happens on the way out (0.8.382; [`IconStore.render`] runs [`IconStore.scale2x`] up to [`IconStore.SMOOTH_GRID`]).** ⚠ **Offering a choice of grid smoothed nobody's icons**: everything already drawn, and all 14 bundled drawings, are 24, so it only ever reached people who ran `z2-icon scale` themselves (the user's words: "doubling the resolution and it is still not smooth — that is a defect"). ⇒ **smooth right before building the bitmap** (24 → 96 / 48 → 96 / 64 → 128). ⚠ Only the output is smoothed — neither the stored drawing nor what `show` prints is touched (what you drew and what `edit` opens must not disagree).
    - ⚠ **A finer grid must never come out rougher** (0.8.383). 0.8.382 fixed the bitmap at 192px, which left **64 drawings unsmoothed** (128 does not divide 192) — so `z2-icon scale`-ing to 64 came out *rougher* than leaving the drawing at 24 (found by the user asking how to get 64 onto a tile). ⇒ the fixed pixel size is gone (192px or 256px; the OS scales to the display size regardless) and **the smoothed grid is always laid out at 2x**. `IconStoreTest.finerGridsNeverComeOutRougher` pins that [`IconStore.smoothedGrid`] is monotonic.
    - **A preview is not folded when it fits (0.8.382).** The CLI measures the terminal with `stty size` and passes it along; a drawing that fits is printed at its own width. ⚠ Folding a 48 drawing into 24 columns **puts the staircase back**, and it reads as "laying it out again changed nothing" (the user's report). When it is folded, `48x48 → 24x24` is appended — folding silently passes the reduced drawing off as the real one. ⚠ When it cannot be measured (a pipe, not a tty) 0 is passed and the default 32 columns applies.
    - **Name lookup also matches a drawing that was laid out again (0.8.382, [`IconStore.nameOf`]).** ⚠ After `z2-icon scale` the normalised text matches nothing, so `z2-icon list` dropped the name to `-` — a slot with "a drawing but no name" **reads as the drawing having been lost** (the user's report). Bundled drawings keep every grid in the reverse table; your own are laid out and compared on the spot.
    - **Laying out again runs Scale2x (EPX) to halve the diagonal steps (0.8.381, [`IconStore.scale2x`]).** ⚠ **Fattening each dot into 2x2 leaves the staircase exactly the same size**, so moving to a finer grid would change nothing about "the tile looks like a staircase" — offering a choice of grid alone smooths nobody, because the 14 bundled drawings and everything already drawn stay at 24. ⇒ while doubling still fits, Scale2x fills in **only the corner where two opposite neighbours agree and differ from the other two**. ⚠ **Flat areas and lone dots always just get thicker**, so a drawing never turns into a different shape (that is the reason to pick this over anything cleverer). ⚠ **Outside the grid reads as the edge value** — reading it as empty eats the outline of a drawing that fills the grid. ⚠ What is left over when doubling does not fit (the 4/3 of 24 → 64) is only laid out dot by dot, so **24 → 48 is the cleanest**. `IconStoreTest.scalingSmoothsDiagonalSteps` pins that diagonal gaps fill in, `scalingLeavesFlatAreasAlone` that flat areas only thicken (bar four corners).
    - **The grid new drawings are made on is `z2-icon grid <grid>`** (24 by default). ⚠ **Drawings already in place are not remade** — changing the grid does not change how a drawing looks, so there is no reason to touch ones nobody asked about.
    - **A preview wider than 32 columns folds 2x2 into one dot** ([`IconStore.preview`]). ⚠ Printed at 64 columns it wraps on a phone screen, which defeats **the entire point of looking at it**. Folding fills a dot **if any of the four is filled** — a majority vote loses one-dot lines completely.
  - **The ink character is not fixed.** `.` ` ` `0` `-` `_` leave a cell empty and **everything else fills it** ([`IconStore.parse`]), so people can draw with whichever character they can see. ⚠ Accepting only `#` would make a drawing done in `*` or `X` **come back empty** — refused for having no ink at all, with no hint as to why.
  - **Blank space is ignored and the drawing is re-centred.** Lines and columns need not add up exactly, and `$(cat)` dropping trailing blank lines stops mattering. ⚠ Conversely **a drawing that is too big is refused** (ink outside the largest grid is an error): trimming it silently delivers **an icon with its edges missing** to the one person who cannot tell why.
  - **The drawing travels as base64.** It is a few hundred bytes containing newlines, and passing it raw breaks the request file's "one line = one argument" ([`Z2ApiBridge`]'s protocol), delivering **a drawing silently truncated after its first line**. `Z2ApiScriptTest.iconSendsTheDrawingAsBase64` pins that a file and stdin deliver the same bytes, through a real `sh`.
  - **Notifications already on screen are swapped in place** ([`refreshActiveNotifications`]). ⚠ Without this the resident notification keeps its old icon **until something rebuilds it**, which for a resident notification is essentially never — so changing the icon looks like it did nothing. `Notification.Builder.recoverBuilder` rebuilds from the live notification, swaps only the icon and re-posts under the same id, so the text, the buttons and the foreground status all survive. ⚠ `setOnlyAlertOnce` is required, or re-posting makes it sound again. Tiles are redrawn the next time the shade opens ([`Z2TileService.requestUpdate`]).
  - ⚠ **Three places cannot be changed** (Android fixes them at install time): the icon in the quick-settings **tile-edit list** (the manifest's `android:icon`), the **file-picker (SAF) root icon**, and the **launcher icon**. Placed tiles and posted notifications do change. All three are named in the help — a place that quietly refuses to change reads as a fault.
  - **A tile picks its icon from what you put on it (0.8.299, [`IconSamples.guess`]).** With twelve slots, "everything on the panel wears the same default icon" became a real problem, so `z2-tile set` fills in a drawing **wherever the name gives it away** (`remind.sh` → a clock, `battery-alert.sh` → a battery, `z2-screen keepon` → a moon). Every bundled macro matches something.
    - ⚠ **A drawing you chose is never overwritten.** A separate mark records which slots this filled in, and only "no drawing yet" and "filled in here before" may be touched ([`IconStore.autoAssign`]). Deciding on **the presence of a drawing alone** would either wipe your own drawing every time `z2-tile set` is re-run, or leave the previous macro's drawing in place forever.
    - ⚠ **With no match, a previously auto-filled drawing is removed.** Swapping a slot to a different macro while its old icon stays makes the tile look like something it no longer is. `z2-tile clear` likewise clears only the auto-filled drawing (yours stays — otherwise reassigning means drawing it again).
    - **There is a way back to automatic** (`z2-icon auto <slot|all>`). Providing only the way out (setting your own drawing) would mean ⚠ **a slot set by hand can never return to automatic** — nothing would be left but `clear` followed by re-running `z2-tile set`. `auto` **does overwrite a drawing you set**: it is only reached when explicitly asked for, so it does not hold back there. `z2-icon list` distinguishes `auto` (picked for you) from `custom` (you set it) — without seeing which is which, there is no way to know the trip back exists.
    - ⚠ **Word order carries meaning.** `battery-alert` matches `battery` before `alert`, `unknown-call` matches `unknown` before `call` — in both cases **the trailing word is the more general one**, so narrower words sit higher.
    - ⚠ **No short words.** `log` matches `login`, `test` matches `latest`, `dir` matches `direct`. **A wrong match is worse than no match** (an icon whose meaning does not fit is harder to read than the plain default). `IconStoreTest.everyBundledMacroGetsAnIcon` pins that every bundled macro gets one, `theGuessDoesNotFireOnUnrelatedWords` that it does not fire on unrelated names.
  - **Bundled drawings are drawn at the finest grid (0.8.384 — all 15 at 64).** ⚠ **Blowing up a drawing made on 24 does not remove the roughness**: an outline cut into 24 steps stays 24 steps however large it is laid out, and [`IconStore.scale2x`] can only halve the size of a step (the user's words: "you only made it bigger, it is just as rough"). 0.8.379–0.8.383 **widened the container without redrawing the contents**, so nothing changed for anyone who used `pick`. ⇒ they are cut again at 64 as circles, arcs and polygons (`qr` alone keeps its original pattern, 21 modules laid out 3 dots each — module corners must not be rounded off by smoothing). `IconStoreTest.everySampleIsDrawnAtTheFinestGrid` pins that they never fall back to 24.
    - **Drawings that arrived automatically are caught up at startup** ([`IconStore.refreshAuto`]). ⚠ Without it, redrawing the bundled set leaves **the slots already in use showing the old one** — "I updated and the tile is the same". ⚠ **Drawings set by hand are never touched** (told apart by the marker [`IconStore.autoAssign`] leaves). ⚠ Nothing is written and no tile is redrawn when the content has not changed (this runs at every startup).
  - **Samples ship with it** ([`IconSamples`], 15 of them). Almost nobody puts the shape they wanted onto a blank grid at the first attempt, so there has to be a way in that **is not a blank page** (the user's proposal). `z2-icon pick <target>` lists them and takes a number, and the chosen drawing can be reworked with `z2-icon edit` immediately — **there is no step between a sample and your own** (both are just text). A sample can be named **by number or by name**, so the one time you pick from the list and every time after do not need different typing. ⚠ The order is the numbering, so **additions go at the end**. `IconStoreTest.everySampleIsAValidDrawing` pins that no broken drawing ships (your own drawing you can fix; a bundled one you cannot).
  - **Your own drawings go in that list too** (0.8.300, `z2-icon save <target> <name>`). A drawing made with `edit` only lived on that one target, so **putting the same drawing on another slot meant drawing it again**. Named and kept, it can be chosen by number or by name exactly like a shipped one — again, **no step between a sample and your own** ([`IconStore.userSampleNames`]; `forget` drops it from the list).
    - ⚠ **The order is the order they were saved in, never sorted by name.** The list is chosen by number, so sorting would slip a newly saved drawing into the middle and **make a number you had memorised point at something else** (the same reason the shipped order is fixed).
    - ⚠ **Digits-only names and names containing spaces are refused**: the first could not be told apart from "number 3", and the second would shift the columns of a TSV list ([`IconStore.normalizeSampleName`]).
    - ⚠ **`forget` only drops it from the list; it does not clear the target.** Tidying the list is no reason for a tile to change how it looks (`clear` is what puts it back).
  - **`z2-icon list` prints the name of the drawing on each target** (0.8.300). ⚠ Before that it only said `custom`, which meant that **with several slots in use you could not tell what was where** (the user's report). The name is found by **matching the normalised text** ([`IconStore.nameOf`]), so ⚠ **two shipped samples with the same shape would report a name you did not set** — `IconStoreTest.noTwoSamplesAreTheSameDrawing` stops the duplicate. When a name does not bring the shape to mind, `z2-icon list -p` prints the drawings (⚠ **only for targets that have one**: padding the list with untouched slots pushes what you wanted off the screen).
  - **The help text is ordered by what you want to do** (0.8.300). ⚠ Eight subcommands followed by a wall of warnings did not answer **"what do I type first"** (the user's report). The most common use (choose from a list, put it on) goes at the top, and the rest is split into "putting one in / keeping your own / how to draw / worth knowing".
  - **Storage is SharedPreferences.** Tiles are read **while the app has no live process**, so DataStore (asynchronous) is unavailable (same reason as [`TileStore`]), and at under 600 bytes a drawing there is no reason to spill to a file. Bitmaps are cached and dropped on `set` / `clear` (a notification reads one every time it is posted).
  - **Dots are scaled up fourfold before being handed over.** A 24px bitmap left to the OS's own scaling **comes out blurred**; scaling by an integer factor keeps the corners square. Ink is **opaque white** — the OS paints its state colour over it, so choosing a colour is meaningless, and on a device that does not repaint, white is what works (both the status bar and the tiles sit on a dark background).
  - **Every place that builds a notification goes through one entry point** (`NotificationCompat.Builder.setZ2SmallIcon`). One surviving bare `setSmallIcon(R.drawable.ic_notification)` leaves **a notification the setting does not reach**, and nothing outside would show which one.
  - ⚠ **That entry point lives in its own file**, apart from [`IconStore`] (`icon/NotificationIcon.kt`). Mixing an `object` and top-level functions in one file makes Android lint (K2 UAST) throw a `ClassCastException` while analysing it, **aborting the whole lint run** (hit for real on AGP 9.1.1). ⚠ Only lint falls over — **compilation and the app itself are fine** — so nothing shows it until lint is run. Splitting the file is the whole fix.
  - **No editing screen in the app** (the user's call). Assigning and drawing are done entirely through `z2-icon`, leaving one source of truth on the device side (the same shape as `z2-tile`). `edit` only opens `$EDITOR`, so **the rectangular selection of a familiar editor works as-is**. ⚠ `nano` / `vim` / `vi` are tried in turn so it still works where `$EDITOR` is unset, and closing without touching anything does not apply anything (opening the wrong thing happens).

- **`z2-screen` (0.8.257 — hold off the OS screen timeout, with a deadline)**: the answer to "I want to watch a long build, so stop the screen turning itself off **for an hour**". `z2-screen keepon 1h` / `keepon off` / `status`.
  - ⚠ **This is not the toolbar's 🔅, and that one is left alone** (the user said so explicitly). 🔅 is `FLAG_KEEP_SCREEN_ON`: it only holds **while the app is on screen**, so it does nothing once you fold the app away. This one writes `Settings.System.SCREEN_OFF_TIMEOUT` — the **OS-wide** setting — so it holds in the background and on the home screen. **They are not merged**: dropping either one takes away a use case the other cannot cover.
  - **A macro cannot do this**, measured: `/system/bin/settings` is visible from the rootfs, but calling it as the app's UID throws `SecurityException` (that binder shell command is for `shell` / `root` only). So it goes the Android way — declare `WRITE_SETTINGS` and write only once the user has explicitly allowed it on the "modify system settings" screen. The way in is Settings › **Screen timeout (z2-screen)**.
  - **Always putting it back is the centre of the design.** A hold left on quietly drains the battery, so there must be no state in which it is held and forgotten.
    - **The duration is mandatory** (`keepon` with no argument stops at usage). An open-ended "never sleep" is never constructible in the first place.
    - **The original value is saved** (`filesDir/screen_timeout.json`). ⚠ A second `keepon` while one is already held **replaces the deadline with the newly given duration and keeps the first original** — re-reading the original here would save "never" as the original, and the deadline would restore nothing. The deadline is replaced rather than only extended so that `keepon 10m` can **shorten** a hold that turned out to be too long; without that, the only way back is to drop it entirely.
    - **The deadline is an `AlarmManager` booking** ([`ScreenTimeoutReceiver`]), so the OS wakes us even if the app was killed. Bookings die on reboot, so `restoreOrReschedule` is called from **both** [`BootReceiver`] and `Z2TermApplication`, and a deadline that passed during the reboot is **written back on the spot**. Two entry points because the cost of missing one is a battery that keeps draining.
    - **24h cap**, so a typo cannot leave the screen on for days.
  - **Whether a hold is active is decided by the saved file**, never by reading `SCREEN_OFF_TIMEOUT` back — that way nothing contradicts itself if the user also touches the value in the system settings app.
  - The value written for "never" is `Int.MAX_VALUE` (~24.8 days). Android has no dedicated infinity here; this is what the stock settings app means by "never".
  - Only the **relative time to seconds** conversion (`1h` / `30m` / `90s` / bare = seconds) lives in sh; the rest is app-side ([`ScreenTimeout`]) — the same split as `z2-alarm in`. Leading zeros (`05m`) are stripped because some `$(())` implementations read "05" as octal. `Z2ApiScriptTest.screenParsesDurations` swaps `z2api` for a stub and pins **the exact arguments that reach the bridge**: get this wrong and "an hour" becomes "a second", which looks held but is not.

- **`z2doctor` command (0.8.230, troubleshooting)**: one command that answers "why isn't it working?". **A different tool from `z2scan self`** — that one hunts for risky settings (security), this one hunts for the reason something does not run. The names are close; do not merge them.
  - Every line is `OK` / `NG` / `--` (unknown or not applicable). **An `NG` always carries the next step, and a check we cannot advise on is simply not shown** (an `NG` with no fix only creates anxiety). **What could not be read is `--`, never counted as `NG`** — not knowing is not a fault.
  - It ends with **a report you can paste as-is**. One command to type, one short report to send: that removes the "it doesn't work" → "what doesn't?" round trip (for a 1–3 h/day project, one support round trip costs a full day).
  - **SSIDs, IPs and host names are never printed**, and the output says so. Adding redaction later guarantees an internal IP ends up in a pasted report.
  - **Two sources, deliberately split**: whatever the shell cannot see in principle (permissions, settings, how many things are resident) comes from `z2api 1 doctor` ([`Z2ApiBridge.doctorRead`](../../app/src/main/java/com/zerotoship/z2term/service/Z2ApiBridge.kt)) as JSON; kernel, free space, sshd and `/sdcard` are probed by the shell. The bridge **does not interpret** — the `NG` rule and the wording live in the CLI.
  - **A row that does not exist on this OS returns `null`, not `false` (0.8.241)**: `storage_all` (`MANAGE_EXTERNAL_STORAGE`) arrived in API 30 and does not exist on API 29, our minSdk. Returning `false` would print an `NG` whose next step ("open Settings and grant it") **cannot be carried out on that device**, so we return `JSONObject.NULL` and let the CLI fall through to `--` (not applicable). Any future API-dependent value gets the same treatment.
  - **How it ended last time** ([`ExitReasons`](../../app/src/main/java/com/zerotoship/z2term/service/ExitReasons.kt), 0.8.376): "the app disappeared again" had no trail to follow from the device. A Java exception lands in `logcat -b crash`, but **when the kernel kills the process for memory (SIGKILL) the app never learns it died** (a native crash leaves a tombstone, owned by the system uid, which the app cannot read either). On top of that, the logcat this app's uid can read **only goes back about a quarter of an hour** on a real device (every exec under the engine emits an SELinux audit line: 682 of 737 lines in a 13-minute capture), so something that happens once every few hours has already scrolled away by the time anyone looks. ⇒ the OS keeps **the last 16 exit reasons** (`ActivityManager.getHistoricalProcessExitReasons`); **every start reads them and copies them into `~/.z2term/exits.jsonl`** (duplicates dropped by timestamp). The diagnostic prints **only the exits worth worrying about**, newest first — updates, user actions and clean self-exits are everyday events, and counting them would bury the real accident under a developer's reinstalls. Each line carries **the reason, the RSS at death and how the process was being treated**, because being reaped as `CACHED` and being killed **while still `FOREGROUND_SERVICE`** (i.e. the resident promise is not being kept) are different stories. ⚠ **It does not diagnose** — only the fact the OS reports; the reading is left to the reader, like every other row. ⚠ Below API 30 the OS keeps no such record, so it stays silent.
  - **Two ways to die, and one of them the OS does not record** (`recordTabKill`, 0.8.378): on a real device both happen — **the app's process dies**, or **only the terminal tab's process tree (engine + shell + children) dies while the app survives** (measured 2026-08-21: the app kept the same pid while the tab's tree and the Gradle JVM disappeared). Both look like "it crashed" to the user, but **the second leaves nothing in `ApplicationExitInfo`** — that records the app's own processes, not the children it `fork`/`exec`s. ⇒ when the PTY exit code is `128 + signal` (`WIFSIGNALED` in `pty_jni.cpp`) we write it to `~/.z2term/exits.jsonl` ourselves, and the diagnostic merges both, newest first, marked `app:` / `tab:` (kept apart they would hide the one thing worth knowing: which came first). ⚠ **It is worthless unless we can tell it from a close we did ourselves** — closing a tab, restarting, switching distro and switching to SSH all end the process with a signal, indistinguishable by exit code. The only evidence is whether `TerminalSession.closeChannel` was used (`selfClosed`), so **every path that tears a channel down must go through that one function**. ⚠ **Free memory is sampled at that instant** (`ActivityManager.MemoryInfo`); after the fact there is no way to know whether memory was tight, and without it you cannot separate "the system reclaimed us" from "we fell over". ⚠ **The tab's name goes in the file but never into the diagnostic** — the report is meant to be pasted, and a user-chosen tab name may well contain a host name. The same fact is shown on screen in one line (`banner_process_killed`: which signal, how many MB were free).
  - ⚠ `Z2DoctorScriptTest` pins, with a real `sh`, that it **runs to the end even with no bridge at all**. A diagnostic is what someone types when nothing else works; if it dies there, they have nothing left.

- **`z2scan` command (0.8.91, vulnerability testing)**: a vulnerability-testing helper scoped to this device / localhost, aligned with z2term's principles (this-device/localhost only, non-invasive, no data sent out, distro official packages only). Two parts. The display language follows `LocaleHelper.language`. Implemented in [`Z2ScanScript.kt`](../../app/src/main/java/com/zerotoship/z2term/proot/Z2ScanScript.kt); installed on all launch paths (proot/z2root/chroot).

  **① Self-check (`z2scan self`)**: no external tools; exits 1 when findings > 0.
  - TCP LISTEN sockets bound to all interfaces (`0.0.0.0`/`::`), read from `/proc/net/tcp{,6}`
  - Risky `sshd_config` settings (PermitEmptyPasswords / PasswordAuthentication / PermitRootLogin yes)
  - Permissions of `~/.ssh` and `authorized_keys`
  - World-writable files in key directories
  - SUID binaries (informational under fake root)
  - Empty or `.` elements in `PATH`

  **② Scanners (`net`/`host`/`cve`)**: thin wrappers that install `nmap`/`lynis`/`trivy`/`grype` once via `ensure_pkg` (`detect_pm` for apk/apt/pacman).
  - `z2scan net` runs nmap `-sT -Pn` (no root) with a **default target of `127.0.0.1`**; a non-local target is refused unless `--allow-remote` is given (plus a warning), structurally preventing unauthorized mass targeting
  - `host` uses lynis (falling back to `self` if absent); `cve` scans the rootfs for known CVEs via trivy/grype when present
  - No scanner is bundled and results stay local (F-Droid compliant, nothing sent out)

  **③ Baseline diff (`self --save` / `diff` / `baseline`, 0.8.243)**: printing the full report every time does not help, because **nobody can spot the difference** in it day after day. Record the current state as the baseline and print **only the lines that changed** from then on.
  - What is compared: the `[WARN]` / `[INFO]` lines plus the indented detail lines hanging off them (file listings). **Headers, `[ OK ]`, the count and blank lines are dropped** — putting anything that changes on every run into the baseline makes it report "changed" every day and therefore useless. `sort -u` removes ordering noise as well.
  - **Exit 1 only when something is new.** Things going away exit 0 — an alert that fires on the day you cleaned something up is an alert people stop reading. `--quiet` makes the output completely empty when nothing changed, so `out=$(z2scan diff --quiet); [ -n "$out" ] && z2-notify …` works as-is (pair it with `z2-when time:daily` for "tell me only what appeared on its own").
  - **No dependency on `diff`** — busybox and GNU differ, and set subtraction over lines (`grep -Fxv -f`) is enough. `self --save` runs the check **only once** (it shells out to `find`, so running it twice is visibly slow).
  - The baseline is **plain text** at `~/.z2term/scan/baseline.txt` (readable as-is, keepable in git). A `# lang:` header records the language so that "everything changed after I switched languages" has a visible reason (the comparison is over the message strings themselves, so it cannot be otherwise); a mismatch prints a warning.
- `launchAndroidSh`: fallback when proot isn't possible (`/system/bin/sh` + minimal mkshrc).

#### Execution engine z2root (hidden feature, no root)

When `executionEngine = "z2root"`, `launch()` swaps the binary for `nativeLibraryDir/libz2root.so` (our own ptrace engine). It accepts a proot-compatible argv subset, so arguments and env carry over unchanged (`PROOT_*`/talloc are ignored by z2root).

If `libz2root.so` is not bundled (`scripts/build-z2root.sh` was not run), startup stops with "engine binary not found" — the proot prebuilts were removed in 0.8.328, so there is nothing to fall back to.

**Guarding against stale build artifacts (0.8.48)**: the z2root/z2accept `.so` files are build artifacts (not in git) and are regenerated by neither `git pull` nor CMake, so fixing `z2root.c` can still ship an **old `.so` inside the APK**. The Gradle task `buildZ2rootNative` runs `scripts/build-z2root.sh` before the jniLibs merge, so an `assemble` alone always regenerates from current sources (zero manual steps). `build-z2root.sh` resolves the NDK path by itself (env vars / `sdk.dir`+`ndk.version` in `local.properties` / `$ANDROID_HOME`). All `merge*JniLibFolders` tasks depend on `buildZ2rootNative` and the output goes to `src/main/jniLibs`. What is fetched at runtime is the rootfs, not z2root, so a `z2root.c` fix always ships inside the APK.

##### Path translation

Hardened to proot parity.

- Canonicalization of symlinks inside paths
- cwd-relative paths made absolute via `/proc/<tid>/cwd`
- `dirfd`-relative paths are left untranslated
- Two-path translation for `renameat2` / `linkat` / `symlinkat`; path translation for `utimensat`
- execve loader substitution and `#!` shebang resolution
- Non-ELF files and non-existent PATH candidates are passed through with a plain execve (no loader), letting the kernel return `ENOENT`/`ENOEXEC`
- **Binds resolve by longest match** (most specific = longest `guest_len` wins), in both `translate_abs` and `host_to_guest` (0.8.75)
- `host_to_guest` has a **pure string-processing fallback** that reconstructs the guest path from the rootfs marker `"/files/distros/<name>/"`, so it still reverses correctly when an OS major upgrade changes the absolute prefix of the data directory (`/data/data` ↔ `/data/user/0`) (0.8.97)

**Scratch write-back (0.8.99–0.8.101)**: translated host paths are written back to a scratch area below the tracee stack via `process_vm_writev`. `SCRATCH_OFFSET` is 16 (immediately below `sp`, i.e. inside the same present page), and `write_tracee_mem` additionally has a **`PTRACE_POKEDATA` fallback**. POKEDATA goes through the kernel's `__access_remote_vm`, which calls `expand_stack()`, so it can reliably write to un-grown lower pages where `process_vm_writev` (GUP, never grows the stack) returns EFAULT.

##### Performance (seccomp and read-free)

**seccomp-bpf (0.8.32)**: instead of trapping every syscall twice with `PTRACE_SYSCALL`, only the syscalls needed for path translation, fakeroot spoofing, getcwd reverse translation and `/proc` spoofing are caught with `SECCOMP_RET_TRACE`; the rest run natively (the same approach as proot). On-device benchmarks: fork/exec ~2.3×, read ~3×, real IO within ~2× of proot, and FS traversal faster than proot.

**Read-free mode (0.8.34, default ON in 0.8.35)**: even after seccomp, `read`/`close` still had to be caught to spoof `/proc/<pid>/status` and `loginuid`, leaving tight small-read loops (`dd bs=1` etc.) about 9× slower than proot. Read-free performs the spoofing at the moment of `openat` instead: the spoofed content is written to a throwaway temp file inside the rootfs and the `openat` path is redirected there (then immediately unlinked — open-then-unlink). Subsequent reads are ordinary file reads, so `read`/`close` leave the seccomp set entirely (native speed). Verified on-device (run-as): `dd bs=1 ×300000` went from ~8.1s to ~0.28s (marginally beating proot's ~0.32s), with status/loginuid spoofing intact and no leftover temp files. `Z2ROOT_NO_READFREE=1` falls back to the old read-tracing path.

##### Spoofing and bridging for compatibility

| Target | Approach | Version |
|---|---|---|
| `ioctl` `TCGETS2`/`TCSETS2`/`TCSETSW2`/`TCSETSF2` | Rewritten to the legacy forms (`TCGETS`/`TCSETS`/…) on entry, because Android rejects TCGETS2 on an app's pty | 0.8.36 |
| AF_UNIX socket `sun_path` | `bind`/`connect` (aarch64 200/203) are traced and `sun_path` rewritten to the real host path inside the rootfs. Abstract sockets (`sun_path[0]=='\0'`) are left alone | 0.8.38 |
| `accept`(202) | Forbidden by Android's untrusted_app seccomp (bionic only uses `accept4`(242)). A tiny libc-independent `LD_PRELOAD` shim `libz2accept.so` (raw `svc`, no library deps) bridges `accept()` to `accept4(...,0)` | 0.8.39 |
| io_uring trio (`io_uring_setup`=425 / `io_uring_enter`=426 / `io_uring_register`=427) | The SIGSYS handler returns **`-ENOSYS`(-38)** instead of 0, so libuv falls back to epoll (all other SIGSYS keep the 0-spoof) | 0.8.49 |
| `SCM_CREDENTIALS` ucred | `sendmsg`(211)/`recvmsg`(212) are traced: the real uid/gid of the process is used when sending, and reset to 0 when receiving. The kernel returns `EPERM` unless the declared uid matches the real/effective/saved uid (or `CAP_SETUID`). `SCM_RIGHTS`/memfd are untouched | 0.8.53 |
| Hard links (`linkat`) | **A real hard link is attempted first**; only when Android refuses with `EACCES`/`EPERM`/`EXDEV` etc. does `copy_for_link` copy `old` to `new` on the tracer side and return success(0). Genuine errors (e.g. `new` already exists = `EEXIST`) are preserved | 0.8.47 |
| File watching (`inotify_add_watch`=27) | The path argument (arg1) is rewritten to its real host path. ⚠ **arg0 is the inotify fd, not a dirfd**, so it is handled as "no dirfd". The final symlink is followed by default, and only left alone when the mask carries `IN_DONT_FOLLOW` (0x02000000). ⚠ **Without this, even directories that exist return `ENOENT`**, and every app that watches files concludes its target is missing (confirmed on-device: KDE's `KDirWatch` reported `inotify failed … No such file or directory` for a directory that was present). `inotify_init1`(26) / `inotify_rm_watch`(28) take no path and are out of scope | 0.8.352 |
| `st_dev`/`st_ino` after a copy-fallback | To satisfy git 2.46+ ("after `link()`, lstat the dest and check it matches src"), values are spoofed by **path correlation**: `linkcopy_record` stores the copy's real host path, and on entry of `newfstatat`/`statx` the stat target's host path is resolved with `host_path_for`; only when `linkcopy_find` matches does exit spoof `st_dev`/`st_ino` (for statx, `stx_ino` + `stx_dev_major/minor`) to the src values | 0.8.58–0.8.64 |

`libz2accept.so` is generated by `scripts/build-z2root.sh` and gitignored. `ProotLauncher` places it at `/usr/local/lib/libz2accept.so` in the rootfs and injects `LD_PRELOAD` (a load failure is non-fatal — ld.so warns and moves on). `__errno_location` is referenced as `__attribute__((weak))` with a NULL guard, so bionic binaries (aapt2 etc.) still start even if LD_PRELOAD leaks to them (0.8.55).

##### The built-in loader (`load_elf_and_jump`)

`plan_exec` inspects the target ELF type and its interp and dispatches to one of three paths.

| Path | Target | Behaviour |
|---|---|---|
| `--loader` | Static PIE (ET_DYN) direct load | Walks `PT_DYNAMIC` and applies `R_AARCH64_RELATIVE`(1027) from RELR/RELA (`DT_RELR`/`DT_ANDROID_RELR`/`DT_RELA`) itself as `*(base+off)=base+addend`, then passes a copy of the phdrs with `base` added to each `p_vaddr` via `AT_PHDR` (satisfying the `load_bias=0` literal assumption in bionic's `__libc_init_mte`/`__bionic_get_tls_segment`, which treat `p_vaddr` as an absolute address). Active only for `ET_DYN && base!=0`; ET_EXEC (`base==0`) passes through (0.8.59) |
| `--loader-noreloc` | Dynamic ELF / dynamic interp | ld.so (`ld-linux-aarch64.so.1` etc.) self-relocates in `_dl_start`, so doing it for them double-adds the load bias. Gated off via `skip_reloc` (0.8.67) |
| `--loader-exec <ld.so> <prog> <argv0> [args...]` | musl `ld.so` × ET_EXEC | musl's `ld.so` cannot be invoked explicitly on an ET_EXEC (non-PIE) binary and dies with `Not a valid dynamic program`. Both the program and `ld.so` are `mmap`ed and **the same initial stack/auxv the kernel would build for a `PT_INTERP` exec** (`AT_PHDR`/`AT_PHENT`/`AT_PHNUM` = the program's phdrs, `AT_ENTRY` = the program entry, `AT_BASE` = the `ld.so` load base) is constructed before branching to the `ld.so` entry (`load_exec_via_interp`/`map_img`). Falls back to the legacy path when `use_loader` is off (0.8.78) |

- The program path handed to `ld.so` / the loader must be the **guest path reversed via `host_to_guest`**. Passing the real host path makes `ld.so`'s own `open()` get translated as a tracee too, so anything under a bind is treated as a guest path, gets the rootfs prefixed, and fails with ENOENT (dynamic: 0.8.37 / static: 0.8.54)
- `--loader-exec` is chosen **only when the interp basename is `ld-musl*` and the target is ET_EXEC**; glibc `ld.so` and PIE binaries are deliberately left on the existing path
- On the dynamic-ELF path, `--argv0`+argv0 is not passed when the interp basename is `linker64`/`linker` (bionic). This device's bionic linker64 does not understand `--argv0` and passes it straight through to the program's argv, making aapt2 mistake it for a path argument (0.8.56)

##### Known limitation

**"Rich" static-PIE binaries that use printf/malloc/pthread/TLS still crash. This cannot be solved in the loader.** A static-PIE with an `__attribute__((constructor))` never prints `CTOR_RAN` and only runs `main`, which pins the cause on **bionic's NDK static-PIE crt (`_start`) not calling `.init_array` constructors** (the non-PIE crt reads `__init_array_start/end` into structors, while the static-PIE `_start_main` only handles `fini` and is missing the init_array setup). Constructors must run after libc init and before `main`, and the loader loses control once it jumps to `_start`, so they cannot be invoked afterwards. The same happens under proot or the kernel — this is an **NDK-specific constraint, not a z2root parity gap**.

##### Showing the actual engine (0.8.44)

The "Execution engine" section of the settings shows an "actual engine for this tab" row. Rather than the settings chip (= the value that will be used next launch), it displays the engine the tab actually started with (`TerminalSession.actualEngine`, i.e. the result of `ProotLauncher.resolveLaunchEngine()` or the chroot path) as a read-only line, so a fallback (z2root not bundled → proot, chroot probe failed → proot) is reported truthfully. The 7-tap toggle on the version row that shows/hides engine selection also gained a **3-second cooldown** so rapid taps cannot immediately flip it back.

<details>
<summary><b>z2root fix history (0.8.30–0.8.101, 29 entries)</b> — current behaviour is above; this records why it got that way</summary>

**0.8.30 first e2e**: verified end-to-end on a real device with Ubuntu 24.04, where `apt install hello` succeeded (`Unpacking`→`Setting up`→`Hello, world!`).

**0.8.32 seccomp-bpf speedup**: moved from trapping every syscall twice to catching only what is needed (see "Performance").

**0.8.34 / 0.8.35 read-free**: fixed tight small-read loops being ~9× slower than proot via open-then-unlink; default ON in 0.8.35 (see "Performance").

**0.8.36 interactive shell dead on glibc distros**: z2root + Arch showed a black screen with no prompt (looked hung). Cause: newer glibc (2.42+) implements `tcgetattr` via `ioctl(TCGETS2)`, but Android denies TCGETS2 on an app's pty (`EACCES`), so `isatty()` failed and bash/zsh decided they were "not a terminal" and started non-interactively (no `PS1`). Alpine (musl) was fine on the older `TCGETS`, and proot rewrites ioctls so it was fine too. Fix: rewrite the TCGETS2 family to legacy on entry (the leading `struct termios` shares its layout with termios2, so at normal baud rates there is no practical loss). Verified on-device: Arch + z2root reaches an interactive `[…]$` prompt and runs commands, with no regression on Alpine (musl).

**0.8.37 executing binaries under a bind mount (dynamic)**: an executable compiled in the home directory (`-b <home>:/root`) could not be run as `./a.out` (dynamic: `error while loading shared libraries: … cannot open shared object file`; static: `z2root loader: open(…): No such file or directory`). Cause: for dynamic ELFs the program path handed to the in-rootfs `ld.so` was the **real host path**, but `ld.so`'s own `open()` is translated as a tracee too, so a host path under a bind was treated as a guest path, got the rootfs prefixed and returned ENOENT (binaries inside the rootfs happened to work because their host path already sits under the rootfs and hit the double-translation guard). Fix: pass the **guest path** reversed via `host_to_guest` (same idea as the `#!` shebang path). Verified on-device: `cd /root && gcc -O2 hello.c -o hello && ./hello` prints `sum(1..100)=5050`, no regression for in-rootfs binaries. Offline gcc installation via `pacman -U` (run-as sits in the SELinux `runas_app` domain where `sendmsg` is blocked, so no network) and a real compile with gcc 16.1.1 were also confirmed.

**0.8.38 GUI (`z2gui`: Xvnc + openbox + terminal) not working**: selecting z2root and starting the GUI left it in a "VNC server never comes up / viewer cannot connect" state. Cause: z2root did not translate `sun_path` for AF_UNIX `bind()`/`connect()`. The X server creates its display socket at `/tmp/.X11-unix/X1`, and passing that through untranslated made the kernel try to create it in the **host's real `/tmp`** (which the app does not have), yielding `ENOENT` (the same hole breaks the dbus and pulseaudio unix sockets). proot translates socket addresses, which is why the GUI worked there. Verified on-device (run-as): `bind()`+`connect()` on `/tmp/.X11-unix/Xtest` succeed and the socket is created **inside the rootfs rather than the host `/tmp`**, with no regression in file path translation.

**0.8.39 GUI actually rendering**: closed the remaining issue from 0.8.38 where Xvnc started but the screen stayed black with "Connection reset". Cause: Alpine's `Xvnc` is musl-built and calls `accept(2)` directly as syscall 202, which Android's untrusted_app seccomp forbids — every VNC connection was killed by SIGSYS, z2root could only swallow it, and the connection never established (`accepted: ::0` then disconnect each time). Substituting `accept`→`accept4` at the SIGSYS site and retrying proved unstable on aarch64 (the syscall is skipped and the pc cannot be rewound cleanly), so the `LD_PRELOAD` shim approach was adopted instead. Verified on-device (untrusted_app, real app): z2root + Alpine + GUI completes the RFB handshake (`accepted: 127.0.0.1::…` / protocol 3.8 / pixel format) and renders an openbox + xterm desktop. This also fixed SSH servers such as dropbear that call `accept`.

**0.8.40 GUI apps segfaulting with X11 `BadAccess`**: Xvnc is now started with `-extension MIT-SHM` to disable the X shared memory extension. When a client attempted MIT-SHM (`X_ShmAttach`), SysV shared memory could not ride along under z2root, the X server returned `BadAccess`, and that asynchronous X error segfaulted the app (under proot `shmget` itself fails, so apps automatically fall back to non-SHM drawing and the issue never surfaced). VNC is a local connection where shared memory buys almost nothing, so disabling the extension outright forces every client onto ordinary `XPutImage` drawing (harmless for the proot engine too). The `z2gui` launcher (`GuiScript.kt`) is rewritten into the rootfs on every launch, so existing distros pick it up on their next GUI start.

**0.8.43 mis-resolution of `/proc/self` and `/proc/thread-self` mid-path**: 0.8.41 only rewrote a leading `/proc/self…` to the tracee pid via `host_path_for()` and missed indirect symlinks. When a guest opened `/proc/net/tcp`, the kernel magic symlink `/proc/net` → `self/net` made `canonicalize_guest()` walk a `self` component mid-path and `readlink` it as the tracer (the z2root parent), resolving to `/proc/<a different host pid>/net/tcp` and failing with `EACCES`. Fix: `canonicalize_guest()` resolves a `self`/`thread-self` component appearing directly under `/proc` to the tracee pid (without `readlink`ing the magic symlink). Confirmed in the dev environment that direct `/proc/self/net/dev` and indirect `/proc/net/dev` resolve identically (the residual `EACCES` there is the outer sandbox restricting per-pid `net/*`, absent on a real device); `id`=root and `/proc/self/comm` resolution are unaffected.

  This was **found while dynamically tracing the still-open SSH-reset investigation**. The reset itself still needs on-device confirmation: the dev-environment failure is a channel-EOF → dropbear closes the PTY master → kernel `SIGHUP` artifact triggered by closing stdin (`</dev/null`); with stdin held open the login shell starts and prints the MOTD, so the PTY path is largely functional. A real interactive `ssh` sends no channel EOF, so the device-side failure is likely a different cause. The `Z2ROOT_TRACE` instrumentation in `z2root.c` is deliberately left in for this kind of on-device tracing.

**0.8.44 actual-engine display**: see "Showing the actual engine" above.

**0.8.47 rewriting `--link2symlink` (fixing git and npm breakage)**: the old implementation turned `linkat(old,new)` into "`new` is a symlink to the guest absolute path of `old`", which broke git's loose-object commit (write `tmp` → `link(tmp,final)` → `unlink(tmp)`): `final` became a **dangling symlink** pointing at the just-deleted `tmp`, and commits failed with `fatal: … is not a valid object` (dpkg was unaffected only because it keeps the original file). npm's global install also unpacks via **hard links** from its cache, which is the likely reason a node-based CLI showed "no logo, no response" — the same dangling breakage corrupting its JS entry point. Fix: real-hard-link-first with copy-fallback (see the table). Where real hard links work the proper shared-inode semantics are kept, and on `/data` where they do not, `new` becomes an independent real file that survives a later `unlink` of `old` — so the general "commit atomically via a link" pattern (git/coreutils/build systems) works uniformly. Verified on-device: `ln orig hard; rm orig; cat hard` keeps the content, and the full `git init`→`add`→`commit`→`log`→`cat-file` cycle succeeds. ⚠️ **Packages installed with `npm install` under the old z2root already have dangling symlinks and must be reinstalled after this fix.**

**0.8.48 structurally preventing stale `libz2root.so`**: see "Guarding against stale build artifacts". This was the real reason the git/npm breakage in 0.8.47 dragged on.

**0.8.49 node-based CLI not starting (io_uring)**: node died immediately with `node: src/unix/core.c:646: uv__close: Assertion 'fd > STDERR_FILENO' failed.` and SIGABRT. Cause: the fakeroot policy of swallowing Android-forbidden syscalls with a **blanket 0 (success)** also applied to `io_uring_setup`(425), so libuv mistook the spoofed `0` for a valid ring fd, kept fd 0 as its backend, and later aborted in `uv__close(0)`. Fix: return `-ENOSYS` for the io_uring trio only (proot never had io_uring, so this simply matches its behaviour). Verified by ssh'ing into an sshd started under the z2root engine (a single-ptrace condition), because a dev shell nesting z2root under proot masks it with double ptrace (first proving with `LD_PRELOAD` that forcing `io_uring_setup` to ENOSYS fixes both node and git). ⚠️ At this point `git clone` still failed with `fatal: hardlink different from source` and was worked around with `git clone --no-hardlinks` (resolved by B-3 in 0.8.58–0.8.64).

**0.8.319 Nothing installs on Arch (a missing `getresuid`)**: a user reported that the GUI install kept failing on the foss build no matter how often they retried. The primary error on the device was `pacman-key needs to be run as root for this operation.` ⚠ **Not GUI-specific** — the clue was that `pacman` itself passed as root (downloads ran) while `pacman-key` (a bash script gated on `EUID != 0`) did not, **within the same run**. Recording `id` alongside the shell's own values on failure settled it:

```
z2diag: id-u=0 id-ur=0 sh-EUID=10576 sh-UID=10576 bash-EUID=10576
```

`id` sees 0, bash sees the real uid. Checking the binaries' dynamic symbols: **`id` uses `getuid`/`geteuid` (174/175) while `bash` uses `getresuid` (148)**. z2root's fakeroot set contained `setresuid`(147)/`setresgid`(149) but **dropped their paired getters, `getresuid`(148)/`getresgid`(150)**. glibc's bash uses `getresuid` for its setuid check, so on **glibc distros (Arch/Ubuntu/Kali) `$UID`/`$EUID` were always the Android app uid**, and every shell script gating on `EUID` refused to run ("must be run as root"). `pacman-key --init` is one such script — with no keyring, an Arch install with `SigLevel = Required` **cannot install anything at all**.

- Fix: trace 148/150 as well and **zero all three outputs (real/effective/saved)** (`fake_getres_on_exit`). ⚠ Unlike `getuid`/`geteuid` these return values **through pointers**, so zeroing the return value alone still leaks the real uid. The output pointers are **captured at entry** — at exit x0 holds the return value and the first argument is gone.
- ⚠ **Add setters and getters as pairs.** The gap arose from enumerating the `set*` calls and dropping the matching `get*` ones, and it is invisible if you only test `getuid`/`geteuid`.
- ⚠ **Always leave the reason behind.** 0.8.316–0.8.318 left nothing but "it failed", costing several device round-trips. A terminal tab's output never reaches logcat, so `z2-pacman-keyring` also writes its reason to a file **in the shared home** (inside the rootfs it would be wiped by the next re-extraction) and `ProotLauncher` drains it into logcat on the next launch.

**0.8.327 gpg-agent becoming non-dumpable blocked Arch keyring initialization**: the remaining `gpg-agent: error binding socket ... No such file or directory` came from gpg-agent protecting secrets with `prctl(PR_SET_DUMPABLE, 0)`. That made z2root's `process_vm_readv` fail with `EPERM` and even `PTRACE_PEEKDATA` with `EIO`, so it could not read the following `bind(2)`'s `sockaddr_un`. Skipping translation then made the guest `/etc/...` bind against the host `/etc/...`, yielding ENOENT. Because this userspace-root engine fundamentally depends on reading and rewriting tracee memory, z2root rewrites only this prctl's argument to 1 and keeps the process dumpable. Verified on-device with the foss build: the ready marker was created, initialization did not rerun on the next launch, and `pacman -Sy --noconfirm` synchronized all core/extra/alarm/aur databases successfully.

**0.8.53 GUI audio silent (already working under proot)**: two causes. (1) PulseAudio's `--daemonize` re-`execve`s `/proc/self/exe` to daemonize, but under z2root that resolves to the launcher (`libz2root.so`) and the daemon never starts ("cannot self execute") → `GuiScript.kt` dropped `--daemonize` in favour of `setsid pulseaudio -n --exit-idle-time=-1 … &` (backgrounded with `setsid`+`&`, stopped via `pactl exit`). (2) PulseAudio clients put their own uid/gid in `SCM_CREDENTIALS` during the `AF_UNIX` handshake, and the kernel returns `EPERM` unless the declared uid matches the real/effective/saved uid; fake_root spoofs uid=0 while the unprivileged app's real uid is non-zero, so the mismatch killed the client with "Connection died" → ucred rewriting (see the table). Verified: audio plays under z2root + GUI, no "Connection died" in `/tmp/z2gui-audio-<display>.log`, and `pactl info` shows `z2sink`.

**0.8.54 static ELFs under a bind mount + self-hosted build**: when starting a static ELF via `--loader`, the loader was given the program's **real host path**, so anything under a bind (the NDK's static clang, …) was treated as a guest path, got the rootfs prefixed, and failed with ENOENT (`z2root loader: open(…/clang-21): No such file`) — the static counterpart of the hole 0.8.37 fixed for dynamic ELFs. Fix: hand the loader the guest path too, just as the dynamic path hands `guest_real` to `ld.so`. Build side: the NDK's clang is a static ELF, so it **cannot be exec'd under the engine shipped before this fix**. `build-z2root.sh` therefore gained an automatic fallback — if the NDK clang cannot exec, it uses the rootfs's dynamic clang as the cross compiler (`--target=aarch64-linux-android29 --sysroot=<NDK sysroot>`) and links the NDK's static libraries/crt **by hand with GNU ld** (the clang driver's automatic link passes the lld-only flag `--use-android-relr-tags`, which GNU ld rejects). A PC build passes the probe (does `clang --version` print "clang version"?) and keeps using the NDK toolchain — behaviour unchanged. Verified: `bash scripts/build-z2root.sh` completes on this z2root terminal and produces `libz2root.so` (static EXEC AArch64, NDK r29, no deps, stripped) and `libz2accept.so` in `jniLibs/arm64-v8a/` — on-device self-hosting of the native part works. (A) the loader fix and (B) the fallback are tightly coupled (without A a self-hosted z2root cannot exec static binaries; without B the `.so` containing A cannot be built on-device).

**0.8.55 making the accept shim bionic-safe**: an on-device build injects `LD_PRELOAD=libz2accept.so` across the whole build so the JVM's (musl) `accept`(202) gets through, but the shim referenced `__errno_location` (the errno accessor of musl/glibc) as a **non-weak unresolved symbol**, so when LD_PRELOAD leaked into the bionic-built aapt2 that AGP launches, aapt2 failed to start with `cannot locate symbol __errno_location` and the build stopped at `processFullReleaseResources` (bionic uses `__errno()` and has no `__errno_location`). Fix: weak + NULL guard (resolves to 0 on bionic = harmless; still sets errno on musl/glibc). Verified: `LD_PRELOAD=libz2accept.so ./gradlew :app:assembleFullRelease` reports `BUILD SUCCESSFUL` (checked under proot at the time, believing "z2root freezes on a heavy full build"), and unzip+readelf confirmed the bundled `libz2accept.so` has a WEAK `__errno_location` and `libz2root.so` is the case-3-fixed NDK r29 static EXEC, in a 69MB release-key-signed APK. An incremental merge cache was found to re-bundle a stale `.so`, so `fullRelease` intermediates were removed and rebuilt (the `buildZ2rootNative` dependency from 0.8.48 alone cannot always force an incremental merge to refresh). Later, 0.8.62 completed on z2root in 16m58s without freezing — so there is no z2root/proot difference on heavy full builds after all.

**0.8.56 two parity gaps: `.l2s` chains and aapt2**: (1) the NDK's `libc++_shared.so` had been turned into a multi-level symlink chain by proot/old-z2root link2symlink (`libc++_shared.so`→`.l2s.…0001`→`.l2s.…0001.000N` = the real file), and the CMake native link failed with `ld.lld: unable to find library -lc++_shared`. Cause: `canonicalize_guest()` always walked a `readlink` result as a "guest path", but link2symlink stores the **real host path** (`.../shared_home/android-sdk/…`), so walking it made `translate_abs` prefix the rootfs twice and return ENOENT. Fix: reverse absolute link targets through `host_to_guest()` before continuing (link targets that do not match pass through, so ordinary absolute symlinks are unaffected). (2) With the CMake gap removed, the AAPT2 daemon start in `processFossDebugResources`/`…ReleaseResources` failed with `error: expected absolute path: "--argv0"`. Cause: aapt2 is an Android aarch64 ELF (interp=`/system/bin/linker64`) and z2root starts dynamic ELFs as `<interp> --argv0 <name> <prog> <args>`, but this device's (Android 12) bionic linker64 — unlike glibc/musl `ld.so` — does not understand `--argv0` and passes it through to the program's argv, so aapt2 mistook it for a path (proven: `/system/bin/linker64 aapt2 version` succeeds while the `--argv0` form gives that error; kotlinc/java on glibc `ld.so` understood it and worked). Fix: skip `--argv0` for bionic interps. ✅ **Both were e2e-verified on z2root after installing the 0.8.56 APK through the app's own UI (2026-06-09)**: the `.l2s` chain (NDK `libc++_shared.so`) opens without materializing a copy and yields the leading ELF magic, and aapt2 runs `version`/`daemon` (`Ready`) without the `--argv0` error.

**0.8.57 truncated `readlinkat` return**: `readlink(2)` on a `.l2s` symlink returned something cut short like `/root/android-sdk/n` (19B). Cause: the tracee sizes its buffer from the link length in `lstat`'s `st_size` (which z2root has already reversed to the guest length = shorter), while the kernel truncates the longer real host path into that buffer, and passing that through `host_to_guest()` shortened it further. Fix: as in proot, on exit z2root re-`readlink`s the target's real host path into a full buffer, converts it, clamps to `bufsiz` and writes it back (the target's host path is stashed in `pid_state.aux_path` on entry; when the host path is undetermined — `dirfd`-relative etc. — it falls back to reading the tracee's buffer). Linkers only `open`, so this does not affect the 0.8.56 build success, but it prepares for tools that handle `.l2s` via `readlink`. ⚠️ **e2e needs confirming after installing an APK containing this fix.**

**0.8.58 → 0.8.62 → 0.8.63 → 0.8.64 the git clone hardlink check (B-3)**: fixed in four stages.
- **0.8.58**: the underlying cause is an OS constraint — Android SELinux (`untrusted_app`) forbids `link(2)` device-wide, so link2symlink always takes the copy-fallback (a different inode) and fails git 2.46+'s "after `link()`, lstat the dest and verify `st_dev`/`st_ino` match src". Implemented recording (src_dev, src_ino, dest_ino) in a small 32-entry ring on copy-fallback and spoofing to the src values on stat exit when dest_ino matches.
- **0.8.62**: probing with C on the running 0.8.61 engine showed all 200 copy-fallbacks produced a different-inode dest and the stat spoof never fired once (0 fakes) — disproving 0.8.58's "compiled, so it probably works" assumption. The real cause was that `linkcopy_record` sampled the inode by `stat()`ing the dest host path **afterwards**, which diverged from the inode the tracee reads via `newfstatat`, so the lookup always missed. Fix: give `copy_for_link` an out-param and sample the dest inode by `fstat()`ing the output fd **immediately after creating the copy** (guaranteeing it is the same object the tracee later sees), and pass `linkcopy_record` by value to eliminate the re-`stat()`.
- **0.8.63**: addressing the startup regression 0.8.62 introduced (the guest — `Arch Linux ARM` — died instantly with `exitCode=-1`). The real trigger was that with linkcopy recording finally succeeding, the stat-spoof hot path in `newfstatat`/`fstat`/`statx` exit — previously skipped while `g_linkcopy_used==0` — became permanently active. Since the match key was **the inode number alone**, an unrelated file stat'ed by init/ld during startup whose inode happened to collide with a recorded dest got its `st_dev`/`st_ino` spoofed to unrelated src values, corrupting startup stats. Tightened the key to **both `(dev, ino)`** (`copy_for_link`'s `fstat` also samples `dest_dev`, and `linkcopy_find` matches on dev+ino; for statx, dev is reconstructed from `stx_dev_major/minor`).
- **0.8.64**: 0.8.63 turned out to be ineffective — the dest is a copy under the rootfs bind, i.e. on the same host `/data` partition as every guest file, so `st_dev` is one fixed value across the whole rootfs and `(dev, ino)` matching was no different from inode-only matching; false spoofs from startup inode collisions continued. Fix: replace inode matching with **path correlation** (see the table). False hits on unrelated files are now impossible by construction. (fd-based `fstat` cannot obtain a path on entry, so it is excluded from inode spoofing and only does uid/gid spoofing; git's hardlink check uses the `lstat`/`newfstatat` path, so B-3 is unaffected.)

**0.8.59 static-PIE relocation and phdr bias**: partially lifted the long-standing "static binaries segfault" limitation (see "The built-in loader"). An in-process harness confirmed a simple static-PIE (`write` only) runs and non-PIE does not regress. ⚠️ Rich static-PIE binaries still crash for a different root cause (see "Known limitation").

**0.8.67 identifying and fixing the real cause of the startup regression**: **the 0.8.62–0.8.64 work around stat spoofing was not the cause of this regression (a misdiagnosis)**. Diagnostic tracing plus a full register dump on SIGSEGV pinned it on the RELATIVE/RELR handling added to `load_elf_and_jump` in 0.8.59 also hitting `ld.so`, which is loaded on the startup path of every dynamic binary. glibc/musl/bionic `ld.so` self-relocate in `_dl_start`, so the loader double-added the load bias, every RELATIVE-relocated pointer became ×2, and `blr x8` (`x8 = real_ptr × 2`) took an instruction-fetch SIGSEGV (conclusive evidence: `pc==si_addr==x8==the real ld.so address ×2`, reproduced across runs). Fix: gate the loader's handling with `skip_reloc` (see "The built-in loader"). The stat spoofing (path correlation) remains, since it is still needed for B-3.

**0.8.78 fixing musl `ld.so` being unable to launch a dynamic ET_EXEC**: on Alpine (musl), ET_EXEC binaries (`cc` etc.) could not start under z2root; resolved by adding the `--loader-exec` path (see "The built-in loader"). ⚠️ **e2e needs confirming after installing an APK containing this fix.**

**0.8.84 exec with a large argv failing with `ENOENT`**: `rewrite_execve` had two limits — (1) the argv concatenation buffer was a fixed `char blob[8192]`, so when `blob_sz>8192` the condition `if (blob_sz<=sizeof(blob))` went false and **the whole rewrite was skipped**, leaving the guest path in the path register and exec'ing it into ENOENT; and (2) argv reading was capped at `MAX_ARGS 256`, truncating everything past the 256th. Found in cross-distro cmdtest e2e where Kali's `apt-get install python3` tripped it during dpkg's byte-compile (`python3.13 -E -S py_compile.py <287 files = ~11KB argv>`) and failed with `cannot execute: required file not found` (bisecting confirmed "total argv bytes over ~7.5KB while below the kernel ARG_MAX of 2MB = an internal z2root buffer"). Fix: read argv into an unbounded dynamic allocation (`realloc`) and `malloc` `blob`/`parts`/`ptrs` sized from the argv, removing `MAX_ARGS` (the scratch stays just below `sp` as before, since `process_vm_writev` grows the growsdown stack, so even a large argv stays mapped). Alpine/Ubuntu cmdtest: zero non-zero exits. ⚠️ **Completing the python install on Kali and on-device e2e for large-argv exec need confirming after installing an APK containing this fix.**

**0.8.95 → 0.8.96 → 0.8.97 unbootable after the OS 15→16 upgrade**: 0.8.95 attempted a fix with two changes — (1) adding `realpath()` to the `host_to_guest` hot path, which made every path translation do an lstat walk and turned the whole system sluggish with input lag, and (2) running `find <rootfs> -type l` on every launch to rescan the rootfs and recreate symlinks — which backfired into erratic startup, keyboard misbehaviour and broken symlinks, so it was **reverted in 0.8.96**. 0.8.97 re-fixed it in a hot-path-independent way: the cause is that the `.l2s` symlinks left by proot `--link2symlink` embed absolute host paths, and an OS major upgrade changed how the data directory's absolute prefix is normalized (`/data/data` ↔ `/data/user/0`), so `host_to_guest`'s direct rootfs/bind comparison stopped matching, stale absolute paths passed through untouched, `translate_abs` prefixed the rootfs twice, and ENOENT left `zsh` and friends unbootable with `cannot open shared object file`. Fix: the rootfs-marker string fallback (see "Path translation"); `realpath` was not used (useless on dangling paths and expensive) and the full `find` scan was dropped. ⚠️ **The OS-upgrade regression itself cannot be reproduced e2e since the device cannot be downgraded. The design is prefix-independent by construction.**

**0.8.99 → 0.8.100 → 0.8.101 plain ELFs failing to start intermittently**: `ls`/`ssh` etc. intermittently died with `cannot open shared object file`. The real cause was not `.l2s` but the **scratch placement for path rewriting**: translated host paths were written back to `sp - SCRATCH_OFFSET(=2048)` below the tracee stack via `process_vm_writev`, but kernel 6.x does not grow the stack for remote writes, so very early in startup (stack low-water ≈ sp) the write crossed into an un-grown lower page, returned EFAULT, and the loader could not open the program/libc (later locale loading succeeds because the stack has grown by then, which is why it split per-run like 5/8). Confirmed by instrumented on-device tracing (`scratch ... wr=-1 errno=14(Bad address)`). **0.8.99/0.8.100**: shrank `SCRATCH_OFFSET` from 2048 to **16** so the scratch sits in the same present page just below `sp`. Frequency dropped sharply (`ls` 8/8 on device), but with `sp` exactly on a page boundary or with long `.so` host paths it still landed on a lower page, leaving an intermittent symptom where plain `ls` (which does not use `sscanf` etc.) worked but zsh's ZLE module `.so` failed to load and line editing broke (even the present-page clamp `scratch_base()` cannot rescue the exact-boundary case). **Root-fixed in 0.8.101**: added the `PTRACE_POKEDATA` fallback to `write_tracee_mem` (see "Path translation"). Verified on a real device in a z2root tab: **`ls` 8/8, `sshd --lan` on the first try, zsh keyboard normal** — closing out the cannot-open / keyboard series (promoting the scratch to a resident mmap proved unnecessary).

</details>

#### Execution engine chroot (hidden feature, requires root)

when `executionEngine = "chroot"`, `launchChroot()` is used.

- **Toggling the selector**: tap the version 7 times to toggle `engineSelectorUnlocked` (works without root). Unlocking sets it `true` (proot / z2root become selectable); if `probeRootChroot()` then passes, `rootChrootUnlocked=true` is also set and chroot joins the options. Tapping 7 more times while unlocked sets it back to `false` and resets `executionEngine` to the default proot, returning to the pre-unlock state (two-way toggle as of 0.8.33).
- `probeRootChroot()`: a self-test of `su -c id` (uid=0) + `su -c "chroot <rootfs> /bin/sh -c echo"`. The result is `RootProbe` (Ok/NoRoot/ChrootBlocked).
- `launchChroot()`: via `su -c`, bind mount (/dev, /dev/pts, /proc, /sys, /root, /sdcard) → `chroot` → login shell. The `ensure*` helpers (z2-*/OSC7/history/sshd/gui/z2run) are shared with the proot path.
- **Ctrl+C / job control**: because the controlling terminal can't be owned via `su`, the login shell is launched **through `setsid -c`** to enable it.
- On chroot launch failure, it auto-falls back to proot (`TerminalSession.startTerminal`). End-to-end verified on a rooted device under SELinux Enforcing (moto g13 / Magisk).

### 4.4 Distro management (`distro/`)

- `DistroBundle`: `ROOTFS_VERSION` (=9), `VERSION_MARKER`, `BUNDLED_DISTRO_ID="alpine"`.
- `DistroSpec`: id / display name / package manager / bundleable / asset name / DL URL or index URL / default shell / approx. DL size.
  - Alpine = bundled (`alpine-minirootfs-aarch64.tgz`, zsh). Ubuntu/Arch/Kali = resolve the latest `rootfs.tar.xz` at runtime from the linuxcontainers index and download (bash).
- `DistroInstaller`: a dependency-free hand-written tar parser (ustar/GNU `L`/PAX `x`/`g`, symlink/hardlink). `decompress` detects gzip/xz by magic bytes.
  - **Zip-Slip protection (0.8.141)**: every write is confined under `outputDir.canonicalFile` (`isWithin`). Because `canonicalFile` resolves symlinks in the existing prefix and normalizes `..`, it rejects both malicious `../` entries and "write-through a planted escaping symlink." The hardlink source (`linkname`) is checked the same way to prevent reading outside the rootfs. Offending entries are skipped while their body is drained with `skipFully` to keep the stream aligned. This blocks a tampered tar from the unpinned Ubuntu/Arch/Kali downloads writing outside the app's private area (the symlink *target itself* is not restricted, since a legitimate rootfs contains many valid out-of-tree (proot-namespace) links — only the write-through is blocked). Regression is guarded by `ZipSlipExtractionTest`, which feeds hand-built tars into the real `extractTar` (4 cases: normal extraction / `../` / write-through symlink / out-of-tree hardlink). `testOptions.unitTests.isReturnDefaultValues=true` makes `android.util.Log` a no-op under the JVM test.
  - `postInstallSetup`: resolv.conf/hosts, `pacman.conf` (disable sandbox/DownloadUser), apt Sandbox::User=root, write the version marker.
  - **Initialize the pacman keyring (`z2-pacman-keyring`, 0.8.316)**: the Arch rootfs comes from the linuxcontainers image (`mirror.archlinuxarm.org`; repos core/extra/alarm/aur), and it **ships no `/etc/pacman.d/gnupg`**. Normally systemd runs `pacman-key --init` on first boot, but **systemd never runs under proot/z2root, so nothing initializes it**. Meanwhile `pacman.conf` says `SigLevel = Required DatabaseOptional`, so **installing anything at all** fails with `error: keyring is not writable` → `error: required key missing from keyring` (the GUI install and `sshd`=dropbear die at the same place; confirmed from a user report and the device log).
    - ⚠ **Do not set `SigLevel = Never`.** That silences the error but leaves the device installing packages without verifying signatures ever again. The cause is a missing keyring, so **create the keyring and break the condition itself**.
    - ⚠ **No network needed.** The image bundles `/usr/share/pacman/keyrings/archlinuxarm.gpg` and `archlinux.gpg`, so `pacman-key --init` + `--populate archlinuxarm archlinux` completes locally (**archlinuxarm first** — the mirror is ALARM, so the archlinux keyring alone does not validate).
    - Two places run it: ① right after the terminal reaches RUNNING (**first** in `TerminalSession.scheduleStartupCommands` — if the user's init command installs a package, the reverse order always fails), and ② `z2gui`'s `install_pkgs` / `clean_pkgs` (someone who started from the GUI has not been through ① yet).
    - The check lives on the host (`ProotLauncher.needsPacmanKeyring`) and only looks at whether `etc/pacman.conf` exists and `etc/pacman.d/gnupg/trustdb.gpg` does not. It resolves without waking the guest, so **a device that is already set up emits no extra command at all**. The script itself is idempotent and exits immediately on distros without pacman.
    - **It runs on screen** (in the terminal, not as a banner). It can take tens of seconds, and waiting in silence is indistinguishable from a hang. Ctrl-C is fine — the next tab retries.
  - Permissions are **owner-only** (`setUnixMode(ownerOnly=true)`). world-writable makes sudo refuse.
- `DistroDownloader`: HTTP DL + SHA256 verification, cached at `cacheDir/distros/<id>-<abi>.tgz` (deleted via `deleteCachedArchive` right after a successful install, so it is almost always empty).
- `RootfsCacheCleaner`: backs the "Clear cache" setting. Android's `cacheDir` is almost always empty, so instead it sweeps the **re-downloadable caches inside the rootfs** — the part that actually consumes storage — by direct file deletion. Targets across every installed OS (`filesDir/distros/<id>`): `var/cache/pacman/pkg`, `var/cache/apt/archives`, `var/cache/apk`, `.cache` of root and each user, plus the whole `cacheDir`. It **never touches `/tmp` (which a running session may hold open), installed packages, settings, or user files**. The confirmation dialog itemizes each "label … size" before deleting (the old one-tap delete is gone).

### 4.5 Terminal emulator (`emulator/`)

- `TerminalEmulator`: processes byte streams with a state machine (Ground/Escape/CSI/OSC/String).
  - Character width: East Asian Width aware (the `ambiguousAsWide` setting makes ambiguous widths 2 cells). Outside the BMP (emoji 😀 / CJK extensions) a surrogate pair is stored across 2 cells — high surrogate in the left cell, low surrogate in the right (`wideCont`). **Rendering (`TerminalRenderer.glyphAt`), selection copy (`getRangeText`) and row text (`toText`) recombine the two cells into a single glyph** (0.8.74). Previously the right cell was dropped and the lone high surrogate was rendered/emitted, producing a tofu (?) box.
  - SGR: bold/underline/inverse/strikethrough, 16/256/RGB (truecolor).
  - DEC modes: alternate screen, cursor keys (DECCKM), **mouse reporting** (X10/Normal/Button/Any × Legacy/SGR/urxvt), **alternate scroll (1007)**.
  - **Leaving the alternate screen resets the text state (0.8.354).** DECRST 1049/1047/47 put **SGR (colors and attributes), the OSC 8 link and mouse reporting** back to their defaults; **only the position (cursor, scroll region) is restored**. ⚠ **xterm's DECRST 1049 restores the SGR from just before the switch (DECRC semantics); this implementation does not.** The on-device failure looked like this: **inside an interactive CLI that keeps drawing on the normal screen** (no alternate screen, history stays in the scrollback), **opening a full-screen editor through that CLI and coming back made everything it printed afterwards underlined** (user report, fixed in 0.8.354). ⚠ **Launching the editor directly does not trigger it** — the CLI must be mid-attribute when the alternate screen is entered. There are two routes and **"default on exit" kills both**: (1) the attribute is saved and restored on the way back (the returning side believes it emitted nothing, so it draws without a leading `\e[0m`), (2) the editor exits without clearing its attributes and nobody resets them. ⚠ **Colors are reset too, not just attributes** — the same route can produce "everything is red", and fixing only the underline would earn the same report twice. ⚠ **The OSC 8 link is cleared as well**: there is no reason for a link to survive a whole-screen swap, and since it is not SGR **neither `\e[0m` nor `reset` can clear it** (leaving a state the user cannot repair). For the same reason **RIS (`reset`) clears the link too.** Forcing mouse reporting off has been the rule since 0.8.124; this is the same reasoning.
  - OSC: 7 (cwd) / 8 (hyperlink) / 10–12 (fg/bg/cursor colour, with `?` query response) / 52 (clipboard) / palette. OSC titles are UTF-8 decoded (prevents mojibake in Japanese tab names).
  - **Cells of URL/OSC8 links are underlined.** Long URLs are detected via a wrapped flag on the originating row (tap to open).
  - bracketed paste (DECSET 2004) supported.
  - **Answering queries (0.8.391)**: DSR 6 (cursor position), **DA1** (`CSI c` -> `CSI ?62;22c`, announcing VT220-class (62) + ANSI colour (22)), the `?` form of OSC 10-12, and Kitty graphics `a=q`. ⚠ **DA1 must always be answered** — libraries behind TUIs usually probe terminal features by sending the feature query first and closing the judgement the moment the DA1 reply arrives (a terminal without the feature silently ignores the feature query, so the always-answered DA1 serves as the deadline). With no reply the judgement never finishes and **the TUI stalls mid-startup until the library times out**. DA2 (`CSI > c`) and XTVERSION (`CSI > q`) are **answered as of 0.8.394** (see below).
  - **CSI dispatch keys on the prefix (`?` / `>` / `<`) (0.8.391)**: dispatching on the final byte alone let three sequences that TUIs send **unconditionally** at startup and exit run as something else. `CSI > 4 ; N m` (XTMODKEYS, which selects modifier-key reporting) applied as SGR and **turned underline on**; `CSI > N u` (kitty keyboard protocol push) and `CSI < u` (its pop) ran as SCORC (restore cursor) and **jumped the cursor**. z2term has none of those features, so `dispatchCsiSecondary` accepts and drops them.
  - `cursorKeyBytes`, `encodeMouseEvent`, `resize` (cursor-aware), scrollback.
- `SearchEngine` (M11): full-text scrollback search. 🔍 → type → ↑↓ to jump between hits. For CJK the highlight position is computed in **cell columns**.
  - The search input is **drawn by hand when the built-in keyboard is used** (`SearchQueryField`). `BasicTextField(readOnly = true)` keeps the OS IME away but also draws **no caret**, which left only append/delete-at-end. Instead a `Text` plus a blinking caret is drawn, with the caret index (`searchCursor`) kept as screen state. Tap → index uses `TextLayoutResult.getOffsetForPosition`; caret x uses `getHorizontalPosition`. **The caret index must be clamped to the length of the text that layout result actually holds** — state (`query`) and the layout result are a frame apart, so clamping with `query.length` crashes with `offset(n) is out of bounds` against a stale empty layout (fixed in 0.8.191). ←→ on the built-in keyboard move the caret (↑ = start, ↓ = end); BS deletes before the caret (surrogate pairs as one). If the term is wider than the field, `horizontalScroll` is nudged to keep the caret visible. **The `Text` carries a 3dp end padding** — `horizontalScroll` clips to the content width, so without it the trailing caret (x = text width) falls outside and **disappears as soon as anything is typed** (fixed in 0.8.192). With the system keyboard the plain `BasicTextField` is used as before (the OS IME draws its own caret).
  - **Text being converted is shown in the search bar too (0.8.275).** Committed text from the built-in keyboard reaches the search term via `ComposingState.onCommit`, but **nothing was drawn until it was committed** — the terminal shows an underlined pre-edit while the search bar sat still, which reads as "the built-in keyboard cannot type Japanese" (reported by the user). `composing.text` is passed to `SearchBar` and drawn **underlined** at the caret (same look and same role as the terminal pre-edit), with the caret **after** it (where the next keystroke lands). ⚠ **Tapping does not move the caret while converting** — the drawn string has uncommitted kana spliced into it, so a tap position cannot be mapped back onto the search term. ⚠ **`composing` is dropped when the search bar opens or closes** — the commit target swaps between the terminal and the search term, so carrying it across leaks kana meant for the terminal into the search term.
- `TerminalScrollbar`: the grabbable scrollbar on the right edge. To follow the finger **from the moment of touch down**, it runs its own `awaitPointerEventScope` loop instead of `detectDragGestures` (which stays idle until touch slop is exceeded). Events are taken on **`PointerEventPass.Initial` and consumed immediately**: if they survive to the Main pass they reach the overlapping `TerminalInputView` (AndroidView), which consumes the changes as "handled", and `drag`/`detectDragGestures` then treats the gesture as stolen and aborts at once. **Read `positionChange()` before calling `consume()`** — it returns `Offset.Zero` for an already-consumed change, so consuming first leaves the thumb frozen (the actual cause of "grabbable but immobile" in 0.8.190/0.8.191; fixed in 0.8.192). The `pointerInput` key is fixed to `Unit`, and changing values (`scrollbackSize`, thumb metrics) are read through `rememberUpdatedState` — putting `scrollbackSize` in the key **recreates the detector on every terminal write and drops the in-flight gesture**. While dragging, the thumb position is held in local state so it does not wait for the `scrollOffset` (StateFlow) → recomposition round trip. The hit area is 32dp wide (+10dp above/below), wider than the 8dp visual.
- `TerminalBuffer`/`TerminalRow`/`TerminalCell`/`SgrAttribute`: cell storage and scrollback.
- `TerminalColors`/`AvailableThemes`: 9 themes (ZTS / Solarized Dark / Dracula / Gruvbox Dark / Nord / Tokyo Night / Catppuccin Mocha / Catppuccin Latte / Monokai).

#### OSC terminators consume both bytes of ST (0.8.361)

An OSC (`ESC ]`) ends with **BEL (`0x07`) or ST (`ESC \` = `0x1B 0x5C`)**. ⚠ **ST is two bytes, so the
`\` must be consumed before returning to GROUND.** `processOsc` treated the ESC alone as the
terminator and returned to GROUND, so **the following `\` was written to the screen as an ordinary
character**.

- Symptom: **running a CLI that emits OSC 8 hyperlinks sprinkles `\` across the screen** — and since
  `currentLink` is still live when that `\` is written, **the stray `\` carries the link too**.
- ⚠ This is also why `AltScreenExitTextStateTest.osc8Link_isClearedOnAltScreenExit` sat red. It looked
  like "the link survives the alt screen", but really **a leaked `\` was sitting at (0,0)** — the
  0.8.354 reset worked all along. (The underline/colour tests in the same class send `\e[0m` *inside*
  the alt screen, so they **pass even if the reset does nothing**, which hid the hole.)
- A broken terminator (`ESC` + non-`\`) follows the same xterm convention as `processString`: cut the
  string there and re-read the byte as ESCAPE (dropping it would swallow the next sequence).
- Regression: three cases in `AltScreenExitTextStateTest` — full ST consumption, BEL terminator, and
  the broken terminator.

#### String-state absorption (0.8.127)

DCS (`ESC P`) / APC (`ESC _`) / PM (`ESC ^`) / SOS (`ESC X`) get a dedicated `State.STRING` that **discards the body until ST (`ESC \`) or BEL**.

**What happens without it**: receiving them in GROUND leaks the body right after the introducer (key=value lists, base64 payloads, `\r` or CSI-like sequences inside the body) onto the screen, causing three symptoms at once:

- Character leakage from image-transfer protocol bodies
- SGR-mouse-like character leakage from misparsing CSI-like sequences inside DCS
- A `\r` in the body being handled as a GROUND CR, throwing the cursor to column 0 mid-render in a TUI

One state fixes all three. An abnormal terminator (ESC + something other than `\`) aborts at that point in xterm fashion and the following byte is reinterpreted as ESCAPE (`StringStateAbsorbTest`).

#### Kitty graphics protocol

APC `ESC _ G <key=value,…> ; <base64 payload> ESC \` is parsed by `KittyGraphicsParser`. Stages 1–10 cover the full scope.

| Stage | Version | Content |
|---|---|---|
| 1 | 0.8.128 | Minimal rendering (`a=T,f=100,t=d` = transmit and display / PNG / direct base64) |
| 2 | 0.8.129 | Four actions (`a=T`/`a=t`/`a=p`/`a=d`) + multi-placement + raw RGB(A) (`f=24`/`f=32`) |
| 3 | 0.8.130 | Query response (`a=q`) + quiet level (`q=0/1/2`) + Z-index (`z=N`) layering |
| 4 | 0.8.131 | Virtual placement (Unicode placeholder `U+10EEEE`) |
| 5 | 0.8.132 | 32-bit image ids (upper 8 bits carried by the underline colour) |
| 6 | 0.8.133 | Animation frame accumulation (`a=f`) |
| 7 | 0.8.134 | Animation playback (frame switching and delay driving) |
| 8 | 0.8.135 | zlib payloads (`o=z`) and query extension |
| 9 | 0.8.136 | file/temp/shm transfer (`t=f`/`t=t`/`t=s`). **opt-in, default OFF** |

**Rendering and lifecycle**
- An image is stored as a `TerminalImage` in `TerminalRow.images` (a `MutableList`) anchored (top-left) at the cursor row, so placements with different `(imageId, placementId)` coexist on the same anchor row. The same pair arriving again **replaces** it (position overwrite)
- The cell count comes from `c=N` / `r=N` when given, otherwise from the bitmap's pixel size divided by the `cellW`/`lineHeight` hints passed in from the Renderer
- Placement advances the cursor by the image's cell width (line breaks are expected from the TUI as `\n`)
- A character write (`setChar`), `clear`, or `resize` (a column shrink putting anchor + width out of range) invalidates **only the placements overlapping those cells**, leaving the others intact
- Row copies (`TerminalRow.copyFrom`) carry images along, so an image survives on the canvas even when shifted a row by e.g. an in-region `DECSTBM` scroll
- `TerminalBuffer` holds an **image cache** (`imageId → Bitmap`): `a=T`/`a=t` register, `a=p` references, and `a=d,d=I`/`a=d,d=i` also drop the entry

**Two-layer drawing by Z-index**: placements with `zIndex < 0` are drawn **below the text** (Pass 2.7 — text stays readable over the image), `zIndex >= 0` **above the text** (Pass 3.5 — icon overlays, speech-bubble effects). Within the same z, insertion order wins (last one on top).

**Unicode placeholder (virtual placement)**: `a=p,U=1` / `a=T,U=1` merely register the image as a grid (`c=N` × `r=N` divisions) in `TerminalBuffer.virtualPlacements` without moving the cursor. The actual draw position is decided by the placeholder cells (`U+10EEEE`) written later in the body, plus up to 3 **combining diacritics** immediately after each (Kitty's fixed 297-element table encodes row / col / the low 8 bits of the placement id). A placeholder cell carries metadata in `TerminalCell.placeholder: PlaceholderRef?`, and the image id is assembled from 24 bits of the fg truecolor (`\e[38;2;R;G;B`) plus the upper 8 bits from the underline colour's R value, for 32 bits total. In Pass 2.7 / Pass 3.5 the Renderer scans the cells of a row, looks each placeholder up via `buffer.getVirtualPlacement` (over `virtualPlacements`), and cuts the tile region (`srcCol/widthCells, srcRow/heightCells`) into a one-cell rectangle via `drawBitmap`'s srcRect→dstRect. Placeholder cells are replaced with a space by `TerminalRow.toText` / `TerminalBuffer.getRangeText` on copy (preventing stray surrogates). Spec: <https://sw.kovidgoyal.net/kitty/graphics-protocol/#unicode-placeholders>

**Security of external file transfer (0.8.136)**: opt-in, default OFF (`AppSettings.kittyExternalFileEnabled`, DataStore key `kitty_external_file_enabled`). Only when it is ON **and** the rootfs resolves does `TerminalSession.applyKittyExternalTransferSetting` inject a `KittyHostTransferSource` into `TerminalEmulator.setKittyExternalTransfer`; turning it back OFF removes it with null (applied dynamically). Defence in depth:

- With the opt-in OFF by default, an unauthorized session is stopped wholesale at the parser level
- file/tempfile rebase a guest absolute path onto `<rootfsRoot>/<guest path>` and an shm name `/<name>` onto `<rootfsRoot>/dev/shm/<name>` — i.e. **confined under the rootfs**
- Path traversal (`/../`) is rejected at the string stage, and `canonicalFile` re-verifies containment under the rootfs as a second gate
- A single read is capped at **16 MiB** (zip-bomb / DoS protection)
- `TempFile` is `delete()`d (unlinked) once read
- An offset beyond the file length or a size over the cap is refused with null

**zlib inflation (`o=z`)**: `inflateZlib(bytes)` expands via `java.util.zip.Inflater`, and `maybeInflate(header, raw)` only interposes when `o=z` is set (an unset `o` passes through). It aborts with `Discard` once 16 MiB is exceeded. The raw RGB(A) size validation (a payload exceeding or falling short of `s` × `v` × `bpp` is `Discard`ed) **also runs after inflation**, so faking `s`/`v` through a compressed payload is equally rejected.

<details>
<summary><b>Kitty graphics implementation history (0.8.128–0.8.136, stages 1–10)</b></summary>

**0.8.128 minimal rendering (stage 1)**: handles a single `a=T,f=100,t=d` plus chunked concatenation (`m=1` continuation with an `m=0`/omitted terminator). `a=d` maps to "erase all images". The image is stored as a `TerminalImage` in `TerminalRow.image` anchored at the cursor row, and the Renderer stretches it into a `widthCells × heightCells` rectangle with `drawBitmap` on the pass that draws the anchor row (between background and text drawing).

**0.8.129 action expansion + multi-placement + raw RGB(A) (stage 2)**: expanded to four actions — `a=T` (transmit and display) / `a=t` (transmit only = cache registration) / `a=p` (put existing image = re-place from the cache elsewhere) / `a=d` (detailed deletion: `d=A` erase all / `d=I,I=N` or `d=i,i=N` per image id / `d=p,i=N,p=N` a specific placement only). Bitmap input expanded to **`f=24` (raw RGB, 3 bytes/px)** and **`f=32` (raw RGBA, 4 bytes/px)**, assembled from the `s=N`/`v=N` pixel dimensions via `Bitmap.createBitmap(IntArray, …, ARGB_8888)` (PNG still goes through `BitmapFactory.decodeByteArray`). For multi-placement, `TerminalRow.image: TerminalImage?` became `images: MutableList<TerminalImage>`, and invalidation was raised to "only the placements overlapping the cell range". Animation / virtual placement / Unicode placeholder / file transfer remained out of scope (`Result.Discard`). `KittyGraphicsParserTest` grew to 12 cases.

**0.8.130 query response + quiet level + Z-index (stage 3)**: answers a TUI's capability probe (`a=q`) with `ESC _ G i=<id> ; OK ESC \` for supported format/transmission combinations, or `ENOTSUPPORTED:<reason>` otherwise, via `output`. Responses are suppressed per the quiet level (`q=0/1/2`: all / errors only / silent). `z=N` is carried through to `TerminalImage.zIndex` for the Renderer's two-layer drawing. 4 cases added to `KittyGraphicsParserTest` for 16.

**0.8.131 virtual placement (stage 4)**: added the path registering a virtual placement via `a=p,U=1` and `a=T,U=1`. Deletion commands (`a=d,d=A`/`d=I`/`d=p`) drop virtual registrations just like ordinary placements. `KittyGraphicsParserTest` grew to 18 cases plus a new `KittyPlaceholderCellTest` with 6.

**0.8.132 32-bit image ids (stage 5)**: the placeholder cells from 0.8.131 could only carry **24 bits via the fg truecolor**, risking id collisions in TUIs juggling many images in one session. Implemented Kitty's route of **carrying the upper 8 bits in the underline colour**. Rather than adding storage to `SgrAttribute`, `TerminalEmulator` keeps `currentUnderlineColor: Int` and `applySgr` parses SGR 58:2:R:G:B (RGB underline) / 58:5:idx (indexed underline) / 59 (reset); SGR 0 (full reset) also drops `currentUnderlineColor` back to `SgrAttribute.DEFAULT`. In `putKittyPlaceholder`, if `isRgb(currentUnderlineColor)` the R value is OR'd in as the upper 8 bits. Underline itself is still not drawn (the attribute exists purely to pass the id). 3 cases added to `KittyPlaceholderCellTest`.

**0.8.133 animation frame accumulation (stage 6)**: receives `a=f` (frame transmit). **Accumulation only — actual drawing came next.** A new `AnimationFrame` (`bitmap` + `delayMs` + `composeMode` + `xOffset` / `yOffset`) was added to `TerminalImage.kt` and `TerminalBuffer` gained `animations: Map<imageId, MutableList<AnimationFrame>>`, appended/read via `addAnimationFrame` / `getAnimationFrames` and cleared in step with `clearAllImages` / `deleteImageById`. `KittyGraphicsParser` gained the action `f` path (`handleFrame`) returning `Result.Frame(...)`. ⚠️ **In the Kitty spec `z=N` means delay (ms) only for `a=f`** (Z-index otherwise), so the parser dispatches per action. `i=N` is required, only `t=d` is accepted, and a failed bitmap assembly yields `Discard`. 3 cases added for 21 total.

**0.8.134 animation playback (stage 7)**: mere accumulation left drawing pinned to frame 0 (the original image in `imageCache`), so nothing animated. `TerminalBuffer` gained a private class `AnimationPlaybackState(currentFrame, lastSwitchMs)` and `animationStates: Map<imageId, AnimationPlaybackState>`. `advanceAnimations(nowMs: Long): Boolean`, called from the Renderer before drawing, runs a simple state machine: "past the current frame's delay → next frame; past the last → loop to frame 0" (frame 0's delay stands in as `frames[0].delayMs`). `currentBitmap(imageId): Bitmap?` returns the current frame while playing and the original otherwise, and `TerminalRenderer.drawImagePlacement` / `drawPlaceholderTiles` consult it before calling `drawBitmap` (falling back to `img.bitmap` / `spec.bitmap`). `addAnimationFrame` `remove`s that imageId's state so a new frame restarts playback from frame 0. Driving happens in a `LaunchedEffect(session.id)` inside `TerminalRenderer`: while `hasActiveAnimations()` is true it syncs with `withFrameMillis`, calls `advanceAnimations`, and bumps `animTick` to trigger recomposition when the state changed. When idle it polls every 100 ms for newly added animations (a negligible `HashMap.isEmpty` cost). New `AnimationPlaybackTest` with 3 cases; the frame-injection path was deferred to on-device verification because bitmap construction does not work in the unit test environment.

**0.8.135 zlib payloads (stage 8)**: up to 0.8.134 the bytes right after base64 decoding were treated as PNG / RGB / RGBA input, so `chafa --format kitty --compress` and TUIs sending large images with `o=z` produced an uninterpretable payload and no image. `handleTransmit` (a=T/t/p) and `handleFrame` (a=f) now always pass the base64-decoded bytes through `maybeInflate`. Values of `o=` other than `o=z` (reserved for future spec) are `null → Discard`. `a=q` also inspects `o=`, returning OK for `o=z` and `ENOTSUPPORTED:o=<x>` otherwise. `KittyGraphicsParserTest` grew to 25 cases. The remaining scope was file/temp/shm transfer only (deferred pending a security review).

**0.8.136 file/temp/shm transfer (stage 9, opt-in)**: until 0.8.135, `t=f`/`t=t`/`t=s` were unconditionally `Discard`ed and `a=q` returned `ENOTSUPPORTED:t=…`. Image-viewer TUIs are designed to send images **as a rootfs file path rather than base64** (inlining a large PNG as base64 is memory- and CPU-hungry), so without this "anything file-based shows nothing". `KittyGraphicsParser` gained `enum TransferKind { File, TempFile, SharedMemory }` and `fun interface ExternalTransferSource { fun read(kind, name, offset, size): ByteArray? }`, exposed through the field `externalTransferSource`. `handleTransmit`/`handleFrame` factored the base64 → inflate logic into `obtainPayloadBytes(header, payloadStr)`: `t=d` still does base64 → maybeInflate, while `t=f`/`t=t`/`t=s` base64-decode a path string, delegate to `source.read(kind, name, O, S)` and apply `maybeInflate` to the result (`O=N` / `S=N`, the spec's offset / size, ride the same path). `a=q` returns OK when a source is attached and `ENOTSUPPORTED:t=…` when not. The host-side implementation is the new `KittyHostTransferSource(rootfsRoot)` (`emulator/KittyHostTransferSource.kt`). ⚠️ `android.util.Base64` is not stubbed in unit tests (so the delegation tests could not run), hence base64 decoding moved to `java.util.Base64.getDecoder()` (available at minSdk 29 = Java 8 equivalent; the Kitty spec uses standard base64, so this is compatible). The settings UI adds a "Kitty graphics: external file transfer" toggle plus a warning under "Experimental / developer" (`settings_kitty_external_file_*` strings, ja/en). Tests: `KittyGraphicsParserTest` grew to 30 cases plus a new `KittyHostTransferSourceTest` with 12 (full read / offset+size / negative size = to end / TempFile auto-unlink / shm rebasing onto `/dev/shm` / `..` rejection / absolute path required / missing file / offset overrun / zero slice / over-cap rejection). **This completes Kitty graphics stages 1–10.** On-device verification is separate.

</details>

#### SGR mouse input (touch → mouse events)

The path that forwards touch gestures to the PTY master as SGR mouse events (`\x1b[<n;col;row>M/m`). **Tap → click is always active while mouse capture is on**; everything else is an opt-in that is OFF by default (`AppSettings.sgrMouseInputEnabled`, DataStore key `sgr_mouse_input_enabled`) — a staged arrangement settled in 0.8.137 → 0.8.138.

| Gesture | Sent | Condition |
|---|---|---|
| One-finger tap | button 0 press+release (`\x1b[<0;col;row M` + `…m`) | **No opt-in needed**; sent whenever `sess.emulator.mouseEnabled` is on |
| One-finger long press | button 2 press+release (right-click equivalent) | Opt-in ON only |
| One-finger drag | button 0 press + repeated button 32 motion + button 0 release | Opt-in ON only. Requires BUTTON_EVENT/ANY_EVENT (NORMAL drops motion, which is safe by existing behaviour) |
| Two-finger swipe | wheel (button 64/65) | Always, regardless of the opt-in |

- `TerminalInputView` keeps the drag state in `sgrMouseDragActive` / `sgrMouseLastCol/Row` and emits motion from `onScroll` only when the cell changes (throttling repeated motion within one cell)
- ACTION_UP/ACTION_CANCEL in `onTouchEvent` always sends a release so the TUI cannot get stuck in a pressed state (if the finger left the view mid-drag, the release uses the last valid cell)
- The helper `isSgrMouseInputActive(sess)` centralizes "opt-in ON **and** the TUI is capturing the mouse via `?1000`/`?1002`/`?1003`/`?1006`"
- With the opt-in ON a one-finger swipe turns into a drag, so `e2.pointerCount == 1` guards it and two-or-more-finger swipes stay on the existing wheel path
- With the opt-in OFF (default) long press and drag remain Z2Term's own gestures (focus / text selection / scrollback swipe)
- Settings UI: the "SGR mouse output (touch → mouse events)" toggle plus a warning in the "Experimental / developer" section (`settings_sgr_mouse_input_*` strings, ja/en). Applied immediately (combine-watched, no restart)
- Spec: <https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#Mouse_Tracking> and "Any-event tracking" / "SGR (1006) mouse" in xterm's `ctlseqs.txt`

<details>
<summary><b>How SGR mouse input got here (0.8.137 → 0.8.138)</b></summary>

**0.8.137 added as an opt-in**: 0.8.116 / 0.8.119 / 0.8.124 / 0.8.126 had delivered **wheel output (button 64/65)**, but there was no path forwarding one-finger tap / long press / drag as SGR mouse events, so TUIs requiring mouse capture (calendar panes, file managers, multi-pane focus switching) sat there doing nothing when tapped. The three gestures were added as an opt-in, OFF by default. Tests: `MouseEncodeTest` grew from 10 to 14 cases (right-click press/release byte sequences pinned / one-finger drag motion pinned to button 32 with an 'M' terminator / NORMAL suppresses motion and returns null / BUTTON_EVENT permits motion), with no regression in the existing 10 wheel / left click / encoding / DECRST cases.

**0.8.138 decoupling tap from the opt-in**: confining `sendMouseClick` under `isSgrMouseInputActive` (opt-in required) in 0.8.137 produced a microregression — with the default OFF, **taps never reached** TUIs that enable mouse capture (0.8.116–0.8.136 sent them based on `mouseEnabled` alone). `TerminalInputView.onSingleTapUp` went back to `sess.emulator.mouseEnabled && sendMouseClick(...)`, restoring "while mouse capture is on, a tap sends an SGR click regardless of the opt-in". Long-press → right-click and one-finger drag → motion stay under the opt-in. The default-OFF behaviour now matches 0.8.116–0.8.136 exactly, and the opt-in adds right-click / drag motion on top.

</details>

#### SGR underline subparameters (`4:n`) (0.8.139)

CSI subparameters (`:`) were treated the same as `;` separators, so `\e[4:3m` from a TUI using styled underlines (curly/double/dotted/dashed) parsed as `[4,3]`.

| Sent | Misinterpreted as |
|---|---|
| `\e[4:3m` | underline + **italic** |
| `\e[4:1m` | underline + **bold** |
| `\e[4:5m` | underline + **blink** |
| `\e[4:0m` (underline off) | **full attribute reset (clearing fg/bg colours)** |

Stray decoration flags lingered, so underlines and friends survived after leaving a TUI that used styled underlines.

Fix: `csiParamIsSub` records whether each parameter was a `:` subparameter or a `;` separator, and `applySgr`'s `4` became "with a subparameter, `0` = underline off and anything else = underline on; always skip the subparameters" (a bare `4` is still a single underline, and styled variants are not visually distinguished). `SgrUnderlineSubparamTest` / `SgrUnderlineAltScreenExitTest` pin the regression.

#### Swipe dispatch while mouse reporting is on

While mouse reporting is on (the TUI asked via `?1000`/`?1006` etc.), `TerminalInputView` dispatches swipes by **screen type, direction, scrollback position and the PTY foreground process**.

| Screen | Finger direction | Condition | Action |
|---|---|---|---|
| **alt screen** | Both | — | wheel to the PTY (finger up = wheel-down = button 65 / finger down = wheel-up = button 64) |
| **primary** | Up (advance) | `scrollOffset == 0` **and** the foreground process is not the shell | Send wheel-down |
| **primary** | Up | `scrollOffset > 0` (browsing history) | Absorbed as scrollback's "back toward the newest", not a wheel |
| **primary** | Down (see the past) | Always | Falls back to scrollback |

**Why each rule exists**
- **Both directions on alt screen (0.8.119)**: alt screen has no scrollback, so a downward swipe falling back to scrollback finds `scrollbackSize == 0` and does nothing — leaving it "only scrollable one way"
- **Foreground process check (0.8.126)**: based on `tcgetpgrp`. Even if `mouseEnabled` is left stale, a shell in the foreground sends no wheel and falls back to scrollback, preventing a stale state from leaking `\e[<...M` onto the prompt
- **Absorbing `scrollOffset > 0` (0.8.116)**: without it, `TerminalSession.writeBytes` on wheel output resets scrollback to 0 — the cause of the jarring "jump straight to the bottom"
- **Primary + down always scrollback**: most reading TUIs are designed to leave wheel-up "to the terminal's scrollback" and ignore it, so only the upward direction needs to reach the TUI

**Notch conversion**: one notch is sent per `MOUSE_WHEEL_STEP_PX (=40px)` of accumulated dy, so a long swipe sends that many lines (on alt it accumulates signed, absorbing direction reversals naturally).

**Fling**: the same branching applies. On primary it is a no-op only when `mouseEnabled && velocityY < 0 && scrollOffset==0`, otherwise it is an inertial scrollback scroll. On alt, `sendMouseWheelRows` converts the inertia into wheel events for the PTY, and **the coordinates keep the finger's cell from where the fling started** (0.8.124 — TUIs with multiple panes decide the target pane from the wheel's (col,row), so a fixed screen-centre coordinate would make an untouched pane scroll during the inertial phase).

##### How to use (Tips) — surfacing the invisible gestures in Settings (0.8.399)

⚠ **Double taps, long presses and flicks show nothing on screen.** The paste history (double-tap 📋),
closing a tab (double tap), reordering (long press + drag), the pads (flick ESC up/down) and
word deletion (flick ⌫ left/right) all have **no visible entry point**, so to anyone who has not
been told, they do not exist. In the user's words: "**nobody can tell**".

- It sits between **Developer and About this app** (`SettingsGroup.TIPS`), **closed by default** —
  the heading and its description already say "there is something here", so there is no need to
  lengthen the top of Settings on every visit.
- One entry = a heading (the gesture) plus a body (what happens). **No settings (toggles) are mixed
  in** — the value is that it reads top to bottom. It reuses the same `Section` treatment as the
  rest of Settings (a bespoke look would read as a different app wedged into the settings page).
- ⛔ **Never document a feature we do not have.** One entry that does not work discredits the whole
  list. ⚠ A "Ctrl+T scrolls" entry was proposed and **turned out not to exist** (a finger moves a
  full-screen TUI through 0.8.393's alternate scroll). **It was left out.**
- The eight entries were picked one by one by the user: toolbar double-tap (**without enumerating
  each button** — "there is a second function" is enough, because one example is all anyone needs
  before trying the rest), closing a tab, reordering tabs, the ESC flicks, the ⌫ flicks, scrolling
  inside a GUI app (two fingers; three while zoomed in, because two fingers pan there),
  `z2` + Tab for the command list plus `--help`, and that an AI can write macros (with reminders,
  RSS and logging unknown callers as examples of **things that are macros rather than app features**).

#### DA2 / XTVERSION — answering "what model and version are you" (0.8.394)

0.8.391 started answering DA1, but **DA2 (`CSI > c`) and XTVERSION (`CSI > q`) were accepted and
dropped**. Both ask "what is this terminal"; plenty of terminals leave them unanswered, so an
implementation that waits forever is rare — but **answering lets the caller settle on "a terminal I
do not know" immediately** instead of waiting out a timeout.

| Query | z2term's reply | Contents |
|---|---|---|
| DA2 `CSI > c` | `CSI > 1 ; <versionCode> ; 0 c` | model = **1 (VT220)** / firmware = versionCode / no ROM cartridge = 0 |
| XTVERSION `CSI > q` | `DCS > \| z2term(<versionName>) ST` | name and version **as text**; free-form, unlike the numeric DA2 |

- ⭐ **Do not impersonate another terminal.** DA2 could claim to be xterm (`Pp = 41`), but then
  **callers talk to us assuming features we do not have**. That is the same trap as 0.8.391, where
  the XTMODKEYS a TUI sends unconditionally was mistaken for SGR and switched underline on:
  **whatever you announce, you get asked for.** Keeping `1 = VT220`, consistent with DA1's `?62`
  (VT220 class), means feature-by-name implementations skip us and fall back to the DA1 judgement.
- **Answered only when the parameter is 0 or omitted** (same as DA1). `CSI > 1 c` and friends are
  other requests and stay unanswered.
- ⚠ **The `<` prefix is never answered.** It shares the dispatch path with `CSI < u` (the kitty
  keyboard protocol pop), so keying on the final byte alone would answer `CSI < c` too.
- The version comes **from `BuildConfig`, passed in by `TerminalSession`**. `TerminalEmulator` only
  takes `versionName` / `versionCode` so the emulator core stays free of Android dependencies and
  its unit tests keep running on a plain JVM.

#### Alternate scroll (DECSET 1007) — swiping an alt screen that has no mouse reporting (0.8.393)

**The symptom**: in a TUI that uses the alternate screen but **never enables mouse reporting** (full-screen pagers, editors, the full-transcript overlay of an interactive CLI, …), **swiping did nothing at all**. Every rule in the previous section assumes `mouseEnabled`; outside it the gesture falls back to scrollback, and the alternate screen has `scrollbackSize == 0`, so nothing happens. Such a TUI has no way to receive a wheel event either, so **unless the terminal provides a scrolling path, a finger cannot move it**.

**The fix**: implement xterm's alternate scroll (DECSET 1007) — **while the alternate screen is active, a wheel event (a swipe here) is sent to the PTY as cursor up/down**.

| Condition | Sent |
|---|---|
| alt screen + `mouseEnabled == false` + `alternateScrollMode == true` | finger down (wanting to see the past) = cursor **up** / finger up = cursor **down** |
| Anything else | Unchanged (wheel output / scrollback fallback) |

- **On by default** (`TerminalEmulator.alternateScrollMode = true`), matching xterm's `alternateScroll` resource set to true — the default most modern terminal emulators pick. The point is to work for the majority of full-screen TUIs, which **never send 1007 at all**; only a TUI that explicitly sends `DECRST 1007` (because it handles the wheel itself) goes back to the old behaviour.
- **TUIs that do send `ESC[?1007h` exist**: an overlay that sends `1049h` and `1007h` together when it opens and prints "↑/↓ to scroll" in its footer. Without this implementation **that overlay alone refuses to move under a finger**.
- **One line = one key**. A cursor key is emitted per `lineHeight` of accumulated dy, so the finger's travel and the TUI's scrolling stay 1:1 (a separate budget from the wheel path's `MOUSE_WHEEL_STEP_PX` = 40px notches). They go out in a single `writeBytes`, capped at `ARROW_SCROLL_MAX_ROWS (=24)` rows per gesture event / fling frame so a fast flick cannot flood the PTY with hundreds of arrows the TUI cannot repaint through.
- **Fling (inertia) takes the same path**: `flingRunnable` now branches three ways — wheel when alt screen + `mouseEnabled`, cursor keys when alternate scroll applies, inertial scrollback otherwise.
- **The cursor-key bytes follow DECCKM** (`cursorKeyBytes`). Full-screen TUIs commonly use application cursor keys (`ESC O A`), and a hard-coded `ESC [ A` would not be recognised as an arrow.
- **Returning to the primary screen restores the default (on)**. A TUI that sends `DECRST 1049` but forgets `DECRST 1007` would otherwise kill swiping for the next TUI that uses the alternate screen (the same reasoning as forcing mouse reporting off in `resetTextStateOnPrimaryReturn`). RIS (`reset`) restores it too.
- **The primary screen never gets this translation**, or every swipe at a shell prompt would recall command history. ⚠ An **interactive CLI that paints a full screen while staying on the primary screen** (no alternate screen; repaints with `ESC[1;1H` + `ESC[J` and pushes history up with `DECSTBM` + `RI`) **never grows the terminal's scrollback in the first place**, so there is nothing for the terminal to scroll back into — the way back is the full-transcript overlay such a CLI provides, which runs on the alternate screen and therefore takes the path above.
- Tests: `AlternateScrollModeTest`, 6 cases (default on / `1007h`-`1007l` toggling / `1049h` + `1007h` applied together / back to primary restores the default / RIS restores the default / `cursorKeyBytes` follows DECCKM).
- Spec: xterm `ctlseqs`, `Ps = 1 0 0 7` (Enable Alternate Scroll Mode).

#### Scroll region (DECSTBM)

Line-feed scrolling (`lineFeed`/IND) performs the normal scroll that pushes the top row into scrollback **only when the region is the whole screen**. With a custom `DECSTBM` region set it **scrolls within the region only**, leaving the fixed rows outside it untouched and out of scrollback (0.8.105).

**The symptom before the fix**: the region was ignored and a full-screen scrollUp was issued, so a TUI keeping a status/command row (line numbers, a ruler) pinned at the bottom via `DECSTBM` had that fixed row pushed up one line per newline — "the line number gets burned into every row". `ScrollRegionLineFeedTest` pins the regression.

`IL`/`DL`/`SU`/`SD`/`RI` already honoured the region.


### 4.6 Domain (`core/`)

- `SessionManager` (object): exposes the list of `TerminalSession` + active via `StateFlow`. `ensureFirst`/`openNew`/`close`/`setActive`/`moveSession` (tab drag reorder). `close` first removes the tab from the UI and runs teardown (PTY/SSH disconnect, GUI=Xvnc stop) in the background to avoid sluggish tab removal.
- `TerminalSession`: state machine `IDLE→INSTALLING→STARTING→RUNNING→EXITED/ERROR`.
  - dedicated emulator dispatcher, PTY read loop, `writeBytes`, resize, `startTerminal`/`switchDistro`/`restart`/`reinstallDistro`/`startSsh`.
  - **Startup distro awaits the persisted value to avoid a race**: `settingsFlow` is `stateIn(Eagerly)` whose initial value is the default Snapshot (`distroId=alpine`), so if `startTerminal` runs before DataStore's first emission lands (right after an app update or device reboot), it would launch the default Alpine instead of the selected OS (the "occasionally Alpine boots" symptom). `startTerminal` now awaits `settings.flow.first()` before choosing the distro, so the selected OS is launched reliably (0.8.105).
  - `StateFlow`: uiState / redrawTick (≈60fps coalescing) / scrollOffset / cellMetrics / selection / cwd / label / settingsFlow.
- `TerminalSelection` / `CellMetrics`: selection range (absolute rows) and 1-cell dimensions.
- `clipboard/ClipboardHistoryStore` (object): the system clipboard holds only one item, so changes are captured into a history (max 50 entries / `filesDir/clipboard_history.json`). Four capture paths: ① `OnPrimaryClipChangedListener` (changes while in the foreground), ② `MainActivity.onResume`, ③ `MainActivity.onWindowFocusChanged(true)`, ④ opening the keyboard's 📋 pad (`KeyboardPad` → `ensureLoaded` + `captureCurrent`). The Android 10+ rule "only the focused app may read the clipboard" is based on **window focus**, and on some devices `onResume` runs before focus is settled and returns nothing — without ③, "copy in another app → come back" is missed. Only the last copy can be recovered if several were made in the background (an OS-level limit). Duplicates are collapsed by `record` (head match / LRU).
  ⚠ **This object is the only history store** (unified in 0.8.313). Until then the keyboard pad had its own identically named object, `ui/terminal/keyboard/ClipboardHistoryStore`, and the two **overwrote the same `filesDir/clipboard_history.json` under different keys (`entries` / `items`)**. Whatever one of them saved looked like an empty file to the other, so **the pad's history started empty after every app restart and never showed anything copied in the terminal** (user report). Add entry points (sheet / pad) freely, but **never add another store**. Loading also accepts the old `items` key so existing history carries over.
  ⚠ **Clips flagged sensitive (`android.content.extra.IS_SENSITIVE`, set by password managers) are captured too, since 0.8.314.** Through 0.8.313 they were dropped outright, which meant **the one thing you most want to paste — a password — was the one thing missing from the history** ("I copied it and it is not there", user report). They are captured and then bounded three ways: ① **they leave the history after 30 seconds** (`SENSITIVE_TTL_MS`) — long enough to paste, short enough not to linger. ② **if the system clipboard still holds that same value, it is cleared too** (`clearPrimaryClip`), because clearing only the history would still leave it pastable elsewhere. ⚠ **If the value changed, leave it alone** — clearing it would steal whatever another app copied since (the same contract as the bundled `otp-clip.sh` sample). Android 10+ only lets the foreground app read the clipboard, so **a failed read also counts as "leave it alone"**. ③ **nothing sensitive is written to disk** — persisting something that expires in 30 seconds would leave it on disk exactly when the app is killed. The history sheet labels such rows "🔒 Sensitive copy — clears itself after 30 seconds" and the keyboard pad prefixes them with 🔒 (a row that vanishes silently looks like a bug).
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
- `PARTIAL_WAKE_LOCK`, notification (`ic_notification` = a transparent-mask **single letter "Z"**, tap to return / stop action). **A small icon cannot use color** (every opaque pixel is tinted the same), so shape is the only thing that distinguishes it. The old two-character "Z2" collapsed into an unreadable blob at the ~24px the status bar gives it; 0.8.196 switched it to the launcher's `>_`, which then shared its silhouette with **other major terminal apps** — with the notification collapsed there was no telling which app it belonged to. So 0.8.200 moved the notification alone to a **single Z**: the diagonal stroke makes its outline fundamentally different from a chevron `>`, and one element survives best at that size. The launcher icon (`>_`) is shown large, so it carries the "this is a terminal" reading. The SAF provider root uses the same icon.

### 4.10 File integration (`saf/Z2TermDocumentsProvider.kt`)

- `DocumentsProvider` (authority `<applicationId>.documents`, `permission=MANAGE_DOCUMENTS`).
- Exposed roots: **home = `shared_home`** (same backing as the terminal's `/root`) + each distro's rootfs (`/`).
- Traversal protection: only under the allowed roots `[shared_home, distros]`. Supports R/W/create/delete/rename.

### 4.11 UI details (`ui/`)

- `terminal/TerminalScreen.kt`: overall layout. TopBar / TabBar / render area / keyboard toggle / keyboard area. `KeyboardMode = CUSTOM | SYSTEM`. In **landscape**, orientation is detected via `LocalView.OnLayoutChangeListener`, switching to a Row layout (`SideKeyboardColumn`) per the `landscapeKeyboardPosition`/`Width`/`Height` settings. `landscapeScaledStyle()` scales keyHeight/font proportionally to landscape height.
  - **Keyboard toggle bar (`KeyboardToggleBar`)**: a 22dp tall strip whose tap shows/hides the keyboard. It sits **above the keyboard** (shared by the terminal and GUI tabs). The `keyboardToggleBar` setting (default on) chooses whether the bar is shown; **when off, no bar is drawn and a double-tap on the ⌨ toolbar button toggles show/hide instead** (0.8.145; single-tap on ⌨ still switches keyboard mode. 0.8.144 briefly moved the bar below the keyboard, but that was awkward, so it was moved back above and the setting + double-tap route was added). The label shows "keyboard" in both states (`▴ Keyboard` / `▾ Keyboard`; the hide state used to be just `▾` at 16dp tall, which clipped the text vertically). `.clickable`'s touch slop (~8dp) alone was not enough — during flick input a finger occasionally grazed the bar and accidentally hid the keyboard — so a custom `pointerInput` gesture is used instead: **if the cumulative movement from `down` exceeds 24dp, `onToggle` is suppressed**, so only a clean tap (< 24dp) toggles (0.8.109; previously, while `.clickable` would not fire past touch slop, short drags could still slip into tap detection and hide the keyboard).
  - **Toolbar (`ReorderableToolbar`)**: 📋 paste / 📜 commands / 💡 screen-on lock / 🔒 background keep-alive / 🔍 search / ⌨ keyboard toggle, drawn from a list of `ToolbarItem`. **Plain tap = the action; long-press drag reorders** (`detectDragGesturesAfterLongPress` + swap on crossing a neighbor's center). A `ToolbarTooltip` Popup shows a short description while held. The order persists in `AppSettings.toolbarOrder` (comma-separated ids), merged with the default via `mergeToolbarOrder` so adding/removing buttons never breaks it. The keep-alive lock defaults to the right of the screen-on lock. The GUI tab (`GuiTopBar`) shares the same `ReorderableToolbar` (no search; 📋/📜 bridge via keysyms).
  - **The 🔒 keep-alive toggle is locked while resident servers run** (0.8.204). While resident servers (`ServerDaemonService`) run, the process stays alive, so turning 🔒 OFF does not end the session (swiping the app from recents doesn't kill the process either). So `ServerDaemonManager.isRunning` is polled every second, and while running 🔒 is **shown ON but dimmed (`ToolbarChip(dimmed=true)`) and cannot be toggled**. Tapping it then opens `ResidentActionDialog` instead of toggling — an escape hatch so you're never trapped by residency: **End session only** (`SessionManager.resetToInitial`; servers keep running) / **Stop everything and quit** (`ServerDaemonService.stop` + `SessionManager.shutdown` + `TerminalService.stop` + `finishAndRemoveTask`, i.e. a task-kill). The lock is scoped to resident servers because the detection FG services (system-event/notification) don't hold a WakeLock — 🔒 still adds its "keep the CPU awake" value there — whereas resident servers hold the same WakeLock/WifiLock, making 🔒 fully redundant. Shared by the terminal tab (`TopBar`) and GUI tab (`GuiTopBar`) via `keepAliveToolbarItem`. **0.8.211 applies the same lock in Settings**: when 🔒 is hidden from the toolbar, the stand-in toggle under Settings › Toolbar is the only way to operate it, and it was not locked — leaving those users unable to end a session while residency was on (found on device). `ToggleField` gained `locked`/`onLockedTap`, polls `ServerDaemonManager.isRunning` every second to dim and disable itself, and opens the same `ResidentActionDialog` on tap (`stopEverythingAndQuit` is now `internal`). **0.8.225 makes "Stop everything and quit" call `SystemEventService.stop` too**: **a single remaining foreground service keeps the process alive**, so with system event capture ON the button did not close the app (found on device). Any new FG service must be added to `stopEverythingAndQuit`. The setting (`systemEventCaptureEnabled`) is left alone, so capture resumes the next time the app is opened — "stop everything now" and "stop capturing" are different intents.
  - **⚙ settings is pinned to the right edge** and takes part in neither reordering nor hiding (0.8.194). It is a single `ToolbarChip` placed outside `ReorderableToolbar`, so its position never moves however the rest is arranged or hidden.
  - **The user picks which buttons appear** (0.8.194). Hidden ids persist in `AppSettings.toolbarHidden` (comma-separated) and are toggled under Settings › Display › **Toolbar**. The button catalog (id / representative icon / description / whether it can be hidden) lives in `ui/terminal/ToolbarButtons.kt` as `CATALOG`, shared by the toolbar and the settings screen. **⚙ has `canHide = false`** — hiding it would leave no way back into settings. **The saved order keeps the ids of hidden buttons too** (`persistOrder`): saving only the visible ones would send a button to the end of the row after hiding and re-showing it.
    Of the hidden buttons, the toggles (🔅 screen-on lock / 🔒 keep-alive) have **no other place to be operated from**, so a switch for each appears inside the same "Toolbar" section while it is hidden. This is also what keeps "new features must not grow everyone's toolbar" workable.
    **A `CATALOG` icon must be the exact glyph the toolbar draws** (for stateful buttons, the OFF side: 🔅 / 🔓 / ⚪). The settings screen works because it lays out the *real* buttons; a different glyph both breaks that correspondence and **puts one thin text-style symbol in a row of colour emoji, so the row stops lining up**. Fixed in 0.8.242 (log ⏺ → ⚪; the toolbar itself draws 🔴 while recording, ⚪ while stopped).
- `terminal/TerminalRenderer.kt`: **per-cell drawText** on a native Canvas (avoids subpixel error accumulation when advance≠cellW). Order: background → selection highlight → text → cursor → selection handles.
  - **A single tap on a tab switches without waiting** (`TabChip`, 0.8.245). ⚠ Passing `onDoubleClick` to `combinedClickable` makes Compose **withhold `onClick` until it is sure a second tap is not coming**. That turns `doubleTapTimeoutMillis` (a device setting, usually 300ms) into **the latency of every tab switch** — a dead interval where pressing does nothing (reported from a device; nothing about the drawing was slow). Replaced with `clickable` plus **our own second-tap detection**, so the first tap calls `setActive` immediately.
    - The clock is `SystemClock.uptimeMillis()` (monotonic); a wall clock can jump on a time sync and misjudge. Once a tap has been consumed as the second one the timestamp resets to 0, so a triple tap does not read as another double.
    - **Restore the pre-tap active tab before closing** (`TabBar.activeBeforeTap`). The first tap has already moved you onto that tab, so closing it straight away makes `SessionManager.close` fall back to **the leftmost tab** — "I only deleted another tab and got thrown somewhere else".
    - ⚠ "Commit on the first tap" only works because selecting a tab is **idempotent**. The toolbar's 📋/🔅/⚪ (`ToolbarChip`) have a side effect on the first tap (paste, lock, start recording), so the same substitution is not available there; removing that latency needs a different design.
- `terminal/input/TerminalInputView.kt` (AndroidView): physical key/OS IME input, gestures (tap/long-press selection/drag scroll/pinch zoom/mouse click emission). Selection is in [§6.5](#65-text-selection-ux).
- `terminal/keyboard/`:
  - `TerminalKeyboard.kt`: 5-row custom keyboard. 3-state Shift, flick, long-press repeat on all keys. **The key background turns bright green when pressed**, and **during a flick the hint in the crossed-threshold direction is bolded + enlarged 1.6×** (the center character stays unchanged).
  - `JapaneseFlickKeyboard.kt`: built-in Japanese/katakana flick. Same press/flick visual feedback.
  - `KeyboardStyle.kt`: COMPACT (44dp) / SPACIOUS (60dp, 4-direction flick). `naturalHeight`. `.copy()` makes a scaled style for landscape.
  - `KeyGestures.kt`: shared gesture for tap + long-press repeat (reports press state to the Composable via the `onPressedChange` callback).
  - `components/SpecialKeyBar.kt`: the special key row for OS IME mode.
- `settings/SettingsSheet.kt` + `SshAccessHelper.kt`: settings page (full screen) + SSH/storage helper.
  - Items are grouped into **8 accordions** (`settings/SettingsGroup.kt`): Display / Keyboard and input / Linux environment / Resident servers and automation / Maintenance / Developer / **How to use (Tips)** / About this app. Declaration order is display order. Open/closed state is persisted by `settings/SettingsGroupStore.kt` under one fixed key per group (`settings_group_open_<id>`), so adding or removing groups never breaks existing state (groups with no stored value fall back to `defaultOpen`). A closed group does not compose its content. The header row carries a **card background plus a 1dp border** (the same treatment as the other tappable cards) so it reads as a tap target, and while the group is open the border and background shift towards the accent colour so the open/closed state is legible from the framing alone (0.8.184; before that it was just text and a ▸/▾ marker, which was hard to tell apart from the surrounding items).
  - **Reset terminal** calls `SessionManager.resetToInitial()`: it **closes every other tab (terminal and GUI) and keeps a single terminal tab**, then reinitialises that one via `TerminalSession.restart()` (= the state right after the first launch). A confirmation dialog is **always** shown regardless of tab count or activity, and a toast reports the result. Settings, resident servers and the rootfs are untouched.
- `ssh/SshProfilesSheet.kt` + `HostKeyVerificationDialog.kt`: SSH profile UI + key verification.
- `sftp/SftpSheet.kt`: SFTP file browser (**full-screen page**). Scrolling the listing downwards collided with the ModalBottomSheet close drag and dismissed the sheet, so it moved to the same separate-page style as the settings page. The back arrow / system back returns to the previous screen. The other sheets (snippets, clipboard history, servers, custom theme) stay ModalBottomSheets since they are meant to be opened briefly.
- `snippets/SnippetsSheet.kt`: tools sheet (toolbar 📜). Tabs switch between **Snippets** (tap a line to insert, reorder/edit), **SSH / SFTP** (`ssh/SshProfilesBody`) and **Servers** (`settings/ServersBody`, the same resident-server manager used by the settings sheet). The SSH tab only appears on terminal tabs, the Servers tab only when a terminal session is available. **The sheet always opens at full height, whichever tab is showing (0.8.252)**: sizing it to its contents made sparse tabs shorter, which **moved the tab bar between tabs and caused mis-taps** — you land on wherever the previous tab's bar used to be. The content Column takes `weight(1f)` so it claims whatever is left; `fillMaxHeight` would overflow by the height of the drag handle above it.
- `components/ReorderList.kt`: **drag-to-reorder for vertical lists** (0.8.249). Brings the Snippets-tab feel (grab ≡, move up/down) to lists whose **rows vary in height**; used by the Servers and Automation tabs. Snippet rows are a fixed height, so a constant pitch was enough; server / rule rows grow with status lines and expanded logs, so every row reports its height via `onSizeChanged` and the swap test uses **the measured height of the neighbouring row**. While a row is held, the outer list never overwrites the order (otherwise rows jump out from under the finger). ⚠ Wrap rows in `key(id)` — without a stable node identity the pointer detaches from the row being dragged. ⚠ Attach the drag to **the handle only**; on the whole row it fights the on/off and log-toggle taps. Persistence is the caller's job (Servers: store the reordered `ServerEntry` list; Automation: `order=` in each rule file).

### 4.12 GUI desktop (`gui/`)

- Inside the distro, launches **Xvnc** (VNC server) + a lightweight WM/app (`proot/GuiScript.kt` idempotently places and launches them; GUI auto-start / landscape support).
- **GUI stack install (`ensure_pkgs`)**: if Xvnc / openbox / the selected terminal are all present, it **starts immediately with no network** (policy: don't update/re-fetch an existing install). **Only when something is missing** does it fetch the missing pieces via `install_pkgs` (apk add / apt install / pacman -S); if it still can't, it fails with a clear message. It runs after the app-side download-confirm gate (`confirmBeforeDownload`) takes consent. Only `clean` wipes the cache and reinstalls (`clean_pkgs`, for corrupted-state recovery).
- `GuiSession`/`GuiActivity`/`GuiScreen`/`GuiViewport`/`GuiInputView`/`GuiKeyMapper`/`GuiEventWatcher` + `gui/rfb/RfbClient.kt` (built-in RFB client). Pairs a terminal tab with a GUI tab with IME linkage.
- **Input**: `GuiInputView` gestures — **2 fingers = pinch (zoom/pan)**, **3-finger vertical move = wheel up/down scroll** (once it becomes 3 fingers, it's treated as scroll until all fingers lift). The old scroll buttons and `RfbClient.scrollWheel` were removed.
- **Video**: because `gpu` output fails on GPU-less devices, mpv plays correctly with **`vo=x11` default + `LIBGL_ALWAYS_SOFTWARE`** software rendering.
- **Audio (`service/AudioBridge.kt`)**: **opt-in** (only when the "GUI audio" setting `guiAudioEnabled` is ON). In-distro PulseAudio (started with the `-n` method) → TCP → bridged to Android `AudioTrack`.
- ⚠ **Leaving for another tab mid-install must not restart the launch decision (0.8.341, reported on-device)**. "The GUI install disappears if I switch tabs" came down to **the screen being thrown away and the launch decision being taken again from scratch**. Switching to a terminal tab makes `TerminalScreen` return early at its `activeSession is GuiSession` branch, so **`GuiTabScreen` leaves the composition entirely** (`guiAreaPx` and `pendingGuiStart` are both `remember(gui.id)`). Coming back re-runs `LaunchedEffect(gui.id)` from the top, and since the install is still in progress the packages are not there yet — so **the "install the GUI?" dialog appears again**. Its "cancel" is `SessionManager.close(gui.id)` = close the tab → `GuiSession.stop()` → `z2gui stop`, so **the install that was running really does get killed**. ⚠ **The install itself never depended on the tab being visible** (it runs in `GuiSession`'s `SupervisorJob + Dispatchers.IO`, and `stop()` is only called by the tab's ✕ and `GuiActivity.onDestroy`), so **no foreground notification is needed to keep it alive**. The fix belongs on the deciding side: while `GuiSession.state` is `STARTING` / `CONNECTING` / `CONNECTED` the `LaunchedEffect` does nothing (the same three states `GuiSession.start()` returns early on). Re-installing after `ERROR` / `STOPPED` works as before. Progress (`z2gui`'s latest output) lives in `GuiSession.message`, which `GuiScreen` collects, so **once the dialog stops covering it, returning shows the run continuing where it is**.
  - ⚠ **Write `GuiTabScreen` assuming it is discarded every time the tab loses focus.** `remember(gui.id)` and `LaunchedEffect(gui.id)` are rebuilt on every return. **A side effect fired without consulting the state the session already holds will restart something that is already running.**
- ⚠ **The GUI terminal can break in the shape of "installed, but no window"** (0.8.343). `ensure_pkgs` decides "installed" from **`has $GUI_TERM_BIN` (does the binary exist)**, so anything that is present but *fails to start* slips straight through and `z2gui` runs to completion with only the desktop up. All the user sees is "the desktop appears but there is no terminal".
  - ⚠ **What is in this bullet was not the cause of the reported symptom** (2026-08-14). The first report — "picking rxvt / Konsole on Alpine leaves no terminal" — pointed at **fonts**, but on-device it split in two: **(A) rxvt, on every OS, does have its window open and simply does not repaint until the screen is tapped** (so: not fonts, not the install — the RFB update path), and **(B) only Konsole on Alpine truly never appears**. ⚠ **"not there" and "there but not repainting" are different bugs** — separate them before chasing either. What landed here stays as the triage aid for (B) and as the asymmetry fix below.
  - **The `apk` server set had no core fonts** (asymmetry fix): `apt` installed `xfonts-base` and `pacman` `xorg-fonts-misc`, but `apk` only had `font-noto ttf-dejavu` (TrueType). **A terminal that defaults to the core font `fixed`** and is started without `-fn` dies right there, so `font-misc-misc font-alias` was added.
  - **urxvt now names an Xft font too** (`-fn xft:monospace:size=11`), for the same reason xterm passes `-fa monospace`: do not let a font package decide whether the terminal lives. ⚠ `TERM_ARGS` is expanded **relying on word splitting**, so never write a value containing spaces.
  - **Terminal packages were split out of the server set** (`TERM_PKGS`). apk / apt / pacman all **fail the whole command if a single name cannot be resolved**, so folding Konsole's Qt6 dependencies into `SRV_PKGS` meant **one wrong name kept tigervnc out too** (= no GUI at all). Split apart, a failed batch is retried with **just the terminal package**, and if that fails too it prints one line (never silently produce a GUI with no terminal).
  - **If it dies right after launch, say why**: three seconds after starting the terminal, if `/tmp/z2gui-term-<N>.log` is non-empty its last 8 lines go to the terminal. **A healthy terminal writes nothing there**, so content is itself the signal (Konsole is the exception — it writes a diagnostic header every time).
  - ⚠ **`GuiScriptSyntaxTest` runs `sh -n` over the generated shell** (all terminals × ja/en = 8 variants). `z2gui` is a string built in Kotlin, so **it can compile fine and still be broken as a shell script — and broken means no GUI at all**.
- ⚠ **"The terminal window is not drawn until you tap" is not the RFB client's fault** (0.8.344, settled by an on-device log). `RfbClient` now logs its first 40 exchanges (`req#N` / `upd#N rects/dirty/bbox`), and on-device that read:
  ```
  21:00:28.350  req#4 incremental=true              ← request sent, now waiting
        …15.8 seconds with zero updates…
  21:00:44.122  upd#4 rects=12 bbox=(0,0)-(828,934) ← the instant the screen was tapped
  ```
  **The request was outstanding the whole time; Xvnc simply had nothing to report.** The rectangle that finally arrived, `(0,0)-(828,934)`, is the terminal window itself — **it was not painted until the tap**. ⚠ So the cause is **on the guest side: an X client that does not draw until some input event reaches it**. RFB is a round trip of "request → hold until something changes → answer once", so **a broken round trip produces no error at all**; without this log you cannot tell "no request went out" from "nothing changed". It stays in because it prints nothing once running steadily.
- ⚠ **z2root's switches only take effect at launch, so there is a hatch for them** (0.8.345). Put `KEY=VALUE` lines in `~/.z2root_env` (shared HOME) and they are passed to z2root as environment variables — `Z2ROOT_NO_READFREE=1` (trace `read` as well), `Z2ROOT_NO_SECCOMP=1`, `Z2ROOT_NO_LOADER=1` and friends can then be tried from a terminal or over ssh **without rebuilding the app**. ⚠ **With no such file, nothing changes** (the default). Delete it when the investigation is over (`Z2ROOT_NO_READFREE=1` traces every `read` and is far too heavy for daily use). Tracing itself is switched on by `~/.z2root_trace_on` (or the "trace log" setting) and lands in `~/z2root_trace.log`.
  - ⚠ **`strace` is not an option**: z2root already ptraces these processes, and a second tracer cannot attach. Looking inside the guest always goes through this trace path.
  - **`Z2ROOT_NO_RECVMSG=1` drops every intervention on `recvmsg`(212)** (0.8.346, investigation only). Under fakeroot, `recvmsg` is the one path that is **always taken to syscall exit so the received `SCM_CREDENTIALS` can be rewritten**, and X server/client traffic goes through it. Whether the cause lies in z2root's intervention can only be settled by **removing the intervention and seeing whether the symptom disappears** (i.e. breaking the condition the cause needs). ⚠ While it is off, AF_UNIX creds show the real uid, so do not leave it on.
  - **With tracing on, every `recvmsg` exit prints one line** (0.8.346): `[z2trc] recvmsg pid= fd= ret= clen= pat=`. `ret` is the byte count (negative means `-errno`; `-11` is `EAGAIN`), `clen` is the `msg_controllen` the kernel wrote back, and `pat` says whether an `SCM_CREDENTIALS` was found and the control buffer written back. The plain `SYS nr=212` lines **carry no return value**, so there was no way to see where an X server that "reads until `EAGAIN`" was being misled.
- ⚠ **When a GUI launched over ssh dies, the X server is not asleep — it is spinning** (0.8.346, confirmed on-device). ⛔ **This section describes the "no window at all" = `accept` symptom**, and **does not apply to the user's "nothing until you tap"** (there the server *is* asleep — see the 0.8.350 entry). ⚠ **At the time the two were believed to be the same thing**, which is why it was written here; always confirm which symptom you are reading about:
  - **`Xvnc` burns 34% CPU and its tracer (z2root) 71%**, sustained (measured as `utime+stime` over 5 seconds), while the **GUI terminal sits at 0%** (parked in `ppoll`). The client is the one that is stuck; the server is busy.
  - Sampling `Xvnc`'s `/proc/<pid>/syscall` 200 times puts the **pc on the same single instruction every time** (libc's `neg w0,w0` → `str w0,[errno]`). That is exactly the **errno store right after a syscall returned an error**, i.e. a failing syscall is being retried as fast as it can be issued.
  - Meanwhile **no new X client connection is answered at all** (`xprop` times out after 10s). An empty `_NET_CLIENT_LIST` therefore means "**not a single client got accepted**", not "the window was never mapped".
  - ⚠ **It reproduces with no RFB client attached**: running `z2gui start` over ssh is enough, so the split can be made **without touching the device screen** (count it with `DISPLAY=:N xprop -root _NET_CLIENT_LIST` returning or not).
- ⭐ **The culprit was `accept(2)` (fixed in 0.8.347)**. The spinning syscall is **`accept`(202)**, and Xvnc's stderr (`/tmp/z2gui-xvnc-<N>.log` — a **different file** from the z2gui script's own output) fills with `_XSERVTransSocketUNIXAccept: accept() failed`. ⚠ Android's untrusted_app seccomp **forbids `accept`(202)** (bionic only ever uses `accept4`), so it is killed with SIGSYS and z2root turns that into `ENOSYS`. **An X server retries `accept` for as long as the listening fd is readable**, so it burns CPU forever and accepts no client at all.
  - The `accept`→`accept4` shim built for exactly this (`z2accept`, via `LD_PRELOAD`) was **not loaded into Xvnc**. The engine does pass `LD_PRELOAD` to z2root, but it **gets dropped by anything that rebuilds the environment** (an ssh login shell, for instance), and `/proc/<Xvnc>/maps` shows no `z2accept` at all.
  - **Three runs of the same procedure, 100% reproducible** (`:7`/`:9` without the shim → `xprop` times out, **232,454** and **229,579** failed accepts, empty `_NET_CLIENT_LIST`; `:8` with the shim → `xprop` answers at once, **0** failures, a window appears).
  - **The fix**: `z2gui` **sets `LD_PRELOAD` itself** (and leaves it alone if it already arrived). That script is the only thing that brings the GUI stack up, so **the shim is loaded no matter which path called it**. ⚠ Relying on an environment variable to propagate breaks again with every new path.
  - ⛔ **0.8.347 nevertheless changed nothing for the user** (2026-08-15, confirmed on-device). ⚠ The mistake was **believing a reproduction that was a different symptom**: launching `z2gui start` over ssh reproduces "**the X server accepts no client at all — no window whatsoever**", which is **not** what the user reports. ⭐ **Before saying "reproduced", check that the symptom you are looking at is the symptom that was reported.** The "the server is spinning, not asleep" note above likewise belongs to **this accept symptom only** — in the real one the server **is** asleep (below).
- ⛔⛔ **The "EPOLLET is the cause" claim below was retracted in 0.8.351.** The 0.8.350 change itself **works exactly as intended** — on-device, `/proc/<Xvnc>/fdinfo/<epfd>` went from `events: 80000019` to **`events: 19`**. **The symptom persisted anyway**, so `EPOLLET` was not the cause. ⭐ With level-triggered registrations *and* everyone asleep, **no fd holds a single unread byte**, which rules out a missed notification by construction. The real cause is the 0.8.351 entry below. ⛔ **"Harmless" was itself overturned in 0.8.392** — the transformation that was left in place **broke async runtimes that assume edge-triggered epoll**, so it is now **off by default** (see the 0.8.392 sub-entry below). **Read the rest of this entry as a record of what was measured at the time.**
- ⭐⭐ ~~**The real symptom (no terminal window until you tap) is a missed edge-triggered epoll notification (`EPOLLET`)**~~ (0.8.350, **retracted**). Measured with the GUI tab open and untouched:
  - **Xvnc, the terminal and the window manager all burn 0 CPU ticks** (`utime+stime` over 5 seconds), park in `do_epoll_wait`/`do_sys_poll`, and 200 samples of `/proc/<pid>/syscall` land on **`epoll_pwait` every single time**. **Nobody is spinning — everyone is asleep waiting for a notification that never comes.**
  - **`accept() failed` count is 0** (so this is *not* what 0.8.347 fixed), and **the X server is alive and answers a brand-new client immediately** (`xprop` returns `rc=0`).
  - ⭐ **`/proc/<Xvnc>/fdinfo/<epfd>` shows the terminal's connection registered with `events: 80000019`** (`0x80000000` = `EPOLLET`). Its socket inode is the **twin** (consecutive) of the terminal's own fd inode, which identifies the connection without guessing.
  - **`_NET_CLIENT_LIST` is empty** — no window has been created yet. ⚠ The screen is **black with only the mouse cursor**, so the accurate description is **"there is no window yet"**, not "the window is stale" (the user's "it's showing" means the GUI tab itself). **Replace the words of a symptom with a measurement.**
  - **Tapping delivers the window's rectangle instantly**: `upd#4 rects=12 bbox=(0,0)-(828,934)` arrives **4m58s** after `req#4`. ⚠ **Connecting a new client does not help** (running `xprop` adds no updates at all) — **a missed fd is never re-checked**.
  - **Why it happens**: Xorg registers X client connections **with `EPOLLET`, on the assumption that it drains each one until `EAGAIN`**. Under ptrace that drain can break, and **an edge-triggered fd that misses one notification never gets another**. A tap works because input → server → client → client writes back means **fresh data arrives, the edge fires again**, and everything queued behind it is processed at once. ⚠ This is **a timing problem, not a terminal-specific one**, so the fix helps every GUI app.
  - **The fix (0.8.350)**: **z2root strips `EPOLLET` from `epoll_ctl`(21)**, turning those registrations level-triggered. Level-triggered means **as long as unread data remains, the notification repeats**, so a miss is impossible by construction. Xorg reads until `EAGAIN` anyway, so it stays correct (only marginally slower for the extra wakeups). ⚠ **`EPOLLONESHOT` (1<<30) is left alone** — it means "disarm after one notification" and the caller re-arms with `MOD`, so stripping it would cause duplicate notifications instead. ⚠ `epoll_ctl` is called **only when an fd is registered** (orders of magnitude rarer than the `read`/`recvmsg` on every message), so adding it to the traced set costs practically nothing. ~~**On by default**; set `Z2ROOT_KEEP_EPOLLET=1` in `~/.z2root_env` to restore the old behaviour~~ → **off by default since 0.8.392** (next item).
  - ⛔⛔⛔ **Off by default in 0.8.392 — the transformation was creating a separate symptom of its own.** After 0.8.351 showed it was not the cause it was kept as "harmless", but it **breaks async runtimes that assume edge-triggered epoll (Rust tokio/mio and friends)**. Those carry readiness around on the premise that "once you are told readable, draining to `EAGAIN` is your job", so forcing level-triggered makes **`epoll_wait` return immediately over and over until the fd is drained**: the reactor spins, burns CPU, and the input handling that should run behind it never gets there. Measured on-device (2026-08-24, a TUI freezing on its selection screen, moto g66j / Arch):
    - The main thread at **`Δcpu=192 ticks/2s` (~100% CPU) with `syscall=running`** (spinning in user space); workers likewise.
    - `/proc/<pid>/fdinfo/<epfd>` showed **the terminal input fd as `events: 2019`** (`EPOLLET` stripped).
    - **Five Enter keypresses produced 0 bytes of output** — the screen paints but no key does anything. To the user this reads as "the TUI freezes once the choices appear".
    - With the stripping disabled the registration stays `events: 80002019` and **the same TUI is fully operable**, confirmed on-device. (In a direct measurement, one unread byte makes a level-triggered `epoll_wait` return **200,000 times in half a second**.)
    - ⭐ **Level-triggered is only safe for programs written to drain until `EAGAIN`** (Xorg is). **It was never a transformation to apply uniformly to every process under ptrace.**
    - The escape hatch survives under a new name: `Z2ROOT_DROP_EPOLLET=1` in `~/.z2root_env` strips `EPOLLET` as before (`Z2ROOT_KEEP_EPOLLET` is gone). With the default off, `epoll_ctl` also leaves the seccomp filter, **removing one whole class of stops**.
- ⭐⭐⭐ **The real cause (0.8.351, confirmed on-device): the terminal's window existed all along — the window manager was simply not managing it.** The trigger is that `z2gui` **started the terminal immediately after openbox, without waiting for it**.
  - **What the measurements showed** (GUI tab open, zero taps):
    - **X, the terminal and the window manager were each individually healthy.** Xvnc's `epoll_pwait` had a **timeout argument of 599,448 ms ≈ 600 s** — X's default screensaver timeout — meaning **it had no other work queued at all**. The terminal (urxvt) sat on a timeout of roughly **17 days**, i.e. fully idle. ⭐ **`/proc/<pid>/syscall` exposes the arguments, so it tells you not just what a process waits on but how long it still intends to wait.** That is what separates "wedged" from "idle".
    - **The terminal had finished starting up**: it created window `0x200009`, passed `WINDOWID` to a forked child shell, and still read and drew whatever was written to its pty (flooding the pty moved its CPU). **Its stderr was 0 bytes** — not one error.
    - ⭐ **Yet `_NET_CLIENT_LIST` was empty and that window had no `WM_STATE`** — openbox was not managing it (never mapped). openbox itself was fine: it advertised `_NET_WM_NAME = "Openbox"`, published the full EWMH set, and responded to `openbox --reconfigure`.
    - ⭐⭐ **Giving X one new event flushed the backlog and the window appeared.** Starting a second terminal over ssh turned `_NET_CLIENT_LIST` into `0x200009, 0x60000a` — **the stuck first window came up together with it**.
  - **Why it looked like "tap to reveal"**: a tap merely delivers an input event to X, which is one such "new event". ⛔ **Tapping is incidental.** Changing the root colour with `xsetroot` updates the phone's screen **with no tap at all** (verified on-device). ⚠ **The wording of the symptom was itself wrong** — updates were arriving normally; the only thing missing was the terminal's window.
  - **Mechanism**: openbox takes `SubstructureRedirect` at startup and then sweeps up pre-existing windows. If the terminal calls `XMapWindow` during that sweep, **the MapRequest lands in openbox's (Xlib-side) queue without being processed**, and openbox then blocks in `ppoll` with nothing left unread on the socket. **A request parked in that queue is not looked at again until some other event arrives.** Everything runs slower under ptrace, which makes the terminal's map far more likely to land in exactly that gap.
  - **The fix (0.8.351)**: `z2gui` **waits for openbox to come up before starting the terminal**. Xvnc always had a readiness wait; **only openbox lacked one**. As a backstop it also fires `openbox --reconfigure` once after the terminal starts, which drains any MapRequest left in the queue. ⚠ **Do not use `xprop` as the nudge** — it is not in any distro's install set, and **merely connecting to X does not wake openbox** (on-device, repeated `xprop` calls never brought the window up). The nudge has to be **an event openbox itself selected for**, and `openbox` is guaranteed present because it is part of the GUI stack.
- ⛔ **Konsole cannot be selected on Alpine (0.8.353)**. Alpine's konsole **always segfaults in the last stage of building its window** (confirmed on-device, 2026-08-15). ⚠ **This is not a missing-install problem**: `ldd` reports zero `not found`, Qt's `libqxcb-egl-integration.so` is present, dbus is present, every dependency in `apk info -R konsole` is installed, and every file in `apk info -L konsole` exists. **GL is healthy too** — once the DRI drivers are in, the log says `qt.qpa.gl: Xcb EGL gl-integration successfully initialized` and EGL enumerates 9 configurations. Fonts are fine (`fc-match monospace` returns Noto Sans Mono; Qt sees 24 families). Locale, `qmlcache`, cache contamination, konsole's own plugins, the image-format plugins, lazy symbol binding and stack size were all ruled out as well. ⭐ **xterm, rxvt-unicode and LXTerminal all work on the same display**, and **KDE itself initializes fine** (`konsole --list-profiles` exits normally). ⇒ Treated as a defect in Alpine's konsole itself, so the combination is simply not allowed to exist.
  - **How it is blocked**: in `GuiTerminal.isUnsupported(terminalId, distroId)`, so the condition does not get scattered across the UI. ⭐ **Refusing is not the whole answer** — coming from the distro side, one tap runs **"switch to xterm and open"** (no round trip through Settings; the same thinking as leaving a way back in `NoOsNoticeCard`). Coming from the terminal side, it explains and does not apply the selection. ⚠ Order matters on switch: **set the terminal first, then switch the distro** — the other way round, the first launch after the switch still runs Konsole.
  - ⚠ **Being selectable was the real harm**: the GUI tab just went black with no explanation anywhere. ⭐ **A combination known not to work is better blocked than warned about.**
  - ⚠ **`gdb` cannot chase this**. z2root is already the tracer, so `PTRACE_ATTACH` fails with `Operation not permitted` (one tracer per process). **Nothing lands in logcat or a tombstone either**, because z2root receives the SIGSEGV before debuggerd would. Chasing it further means adding diagnostics inside z2root.
- **The GUI never opens on its own (auto-launch removed in 0.8.254)**. A preexec hook in interactive shells (bash `DEBUG` trap / zsh `add-zsh-hook preexec`) used to pass the command about to run to `z2-autogui`, which **opened the GUI tab whenever it judged the binary to be a GUI app**. The test was "does it link `libX11` / `libxcb` / GTK / Qt", and **a CUI app that merely talks to X for clipboard support trips it every time** (reported on-device: opening a text editor pops the GUI tab). That steals the screen from someone who is only using the CUI, so the mechanism was **removed rather than made cleverer**, and no on/off setting was added — **there is no reason to offer a choice about a feature that misfires**. The GUI opens exactly two ways: you open the GUI tab, or you type `z2run <app>`. ⚠ The hook was written into the rootfs rc files, so **not installing it any more would leave it in place on existing setups**. `ProotLauncher.removeAutoGuiHook` strips the marked block on every launch and deletes `/usr/local/bin/z2-autogui`, leaving lines the user wrote alone.

### 4.13 Android API bridge (`Z2ApiBridge` / `Z2ApiScript`)

- Commands that drive Android features from the terminal. Macros are built as "trigger (register it with `z2-when`; diff events.jsonl for what `z2-when` does not cover) → logic (shell) → action (`z2-*`)". [See `docs/en/MACRO-GUIDE.md` for how to write them](MACRO-GUIDE.md).

  | Command | Purpose |
  |---|---|
  | `z2-notify` | Post a notification (`-b` reply buttons / `-c` copy button) |
  | `z2-toast` | Toast message |
  | `z2-share` / `z2-open` | Share / open a URL or file |
  | `z2-clip (set/get)` | Clipboard (**writable only while in front**, see below) |
  | `z2-battery` | Battery state |
  | `z2-vibrate` | Vibration |
  | `z2-say` | TTS speech |
  | `z2-torch` | Flashlight on/off/toggle (0.8.153) |
  | `z2-media` | Media playback control |
  | `z2-volume` | Media volume |
  | `z2-intent` | Generic Intent dispatch (0.8.154) |
  | `z2-sensor` | One-shot light/accelerometer/proximity read as JSON (0.8.156) |
  | `z2-state` | Current state in one call (0.8.167) |
  | `z2-alarm` | Time trigger (0.8.167) |
  | `z2-macro` | Install the bundled macro samples (0.8.167) |
  | `z2-session` | **Drives the app's own tabs** (0.8.199, A1) |
  | `z2-server` | **Starts / stops a registered resident server** (0.8.310, F) |

  - **Banner notifications from `z2-notify` (0.8.163)**: with `-h`/`--high`/`--banner` it posts through a separate `IMPORTANCE_HIGH` channel (`z2term_api_high`) with `PRIORITY_HIGH`, giving a **heads-up banner at the top of the screen**. The default channel (`z2term_api`) was created as `IMPORTANCE_DEFAULT` and its importance cannot be raised afterwards (an Android rule), hence a separate channel id for banners
  - **A macro running in the background cannot write the clipboard → the `z2-notify -c` copy button (0.8.335)**: since Android 10, `setPrimaryClip` is **refused for anything other than the focused app (and the input method in use)**. The refusal raises nothing — it only logs `E ClipboardService: Denying clipboard access to …, application is not in focus` — so **from the terminal side it looks like it worked**. Macros triggered by a call, an SMS or a notification are by their nature always in the background, so `z2-clip set` fell silently and `unknown-call` never delivered the number (device report, 2026-08-13).
    - The fix is to provide **one path that can be relied on**: `z2-notify -c <text>` adds a "Copy" button; pressing it starts `service/ClipCopyActivity` (translucent, `noHistory`, `excludeFromRecents`), which **takes focus for that instant**, writes, and closes. Starting an activity from a notification action is a user gesture, so background-activity-launch limits do not apply either.
    - ⚠ **Write in `onWindowFocusChanged(true)`**. At `onCreate` / `onResume` the window does not hold focus yet and hits the same refusal.
    - ⚠ **Verify by reading it back** (`setPrimaryClip` → compare `primaryClip`). A refusal has neither a return value nor an exception, so there is no other way to know. The bundled samples (`otp-clip` / `otp-sms`) use that read-back to choose between "write directly while in front" and "hand it to the button".
    - ⚠ **No toast of our own on Android 13+** (the OS shows its own copy confirmation; two would be redundant).
    - ⚠ Android shows at most three notification buttons. A "Copy" button costs one of them, leaving fewer `-b` reply buttons.
  - **`z2-say`**: speaks via the device's standard TTS (engine init is async, so utterances are queued until it is ready)
  - **`z2-torch`**: controlled via `CameraManager.setTorchMode` (no permission needed) and returns the resulting torch state
  - **`z2-media` / `z2-volume`**: the former dispatches media keys via `AudioManager.dispatchMediaKeyEvent`, the latter operates `STREAM_MUSIC` (returning current/max)
  - **`z2-intent`**: an `am start`-style set of flags (`-a/-d/-t/-p/-n/-f/--es/--ez/--ei/--broadcast/--service`) builds an arbitrary Intent and calls startActivity/broadcast/startService — a single command covering launching apps, opening settings screens, setting alarms, sharing, and more (none of it needs a permission; whatever the target requires is separate)
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
   → [tee to SessionLogger when the terminal log is on]
   → redrawTick/StateFlow notification → TerminalRenderer redraws the Canvas
```

### 5.1.1 Terminal log (toolbar ⚪, 0.8.195)

Keeps writing what the tab shows to a text file. There is **exactly one tap point**:
`SessionLogger.append` sits in `TerminalSession.startReadLoop` **right after** `emulator.processBytes`
(the only place everything shown in a tab passes through; only app-generated messages such as
`writeBanner` take another path). It goes *after* processBytes because **whether the alt screen was
entered can only be decided once that chunk has been processed**.

- **Threading**: `append` only queues the bytes on `SessionLogger`'s single-threaded executor, so the
  emulator thread that serializes drawing is never blocked. Flush runs every 500 ms, so an OS kill
  loses only that tail.
- **Every line can be timestamped (0.8.256, off by default)**. The format is **fixed width**: `[yyyy-MM-dd HH:mm:ss] `, always 22 characters, so the body always starts in the same column — a ragged prefix would undo the point of putting it there. The date is **complete down to the second** so a recording that crosses midnight never leaves you asking which day an 08:42 belongs to.
  - ⚠ **Never applied to raw logs.** Staying byte-for-byte is the whole reason raw exists; one added byte makes it useless as bug-report material. The settings sheet disables the toggle (dimmed, so the reason is guessable) while raw is on.
  - The clock is read **once per chunk** — a chunk arrives at a single instant. "Next byte starts a line" is **carried across chunks**, so a line split at a chunk boundary is neither stamped twice nor missed. Only ASCII is inserted, so it can never land inside a UTF-8 sequence.
  - It is applied **after** masking (`stampIfNeeded(maskIfNeeded(...))`); stamping first would feed the timestamp into the masker's line detection.
- **No rotation** (same user policy as `service/LogWriter.kt`). Instead the current size is published
  as `TerminalSession.LogState.bytes` once a second — the unbounded growth is never silent.
- **Plain-text conversion** (`PlainTextFilter`, the default): escape sequences (CSI / OSC / DCS /
  charset designation) and meaningless C0 bytes are dropped. Dropping alone does not produce readable
  output, so **each screen line is rebuilt**:
  - **`\r` only returns to the start of the line; it does not clear it.** Following characters
    overwrite that line. A download progress bar (`50%\r75%\r100%\n`) therefore leaves one final line
    instead of thousands, and ordinary `\r\n` line endings fall out of the same rule.
  - **`\b` steps back one character.** Tabs are kept.
  - Lines are assembled **per code point** (via `Utf8Decoder`), so `\r` overwriting on a line that
    contains Japanese never splits a multi-byte character, and UTF-8 cut across chunks carries over.
  - A line longer than 8192 characters is flushed without waiting for a newline (so output that never
    emits one cannot grow the line buffer forever).
- **Nothing is written while the alt screen is active** (default). A full-screen TUI paints by
  rebuilding the screen, so flattening it yields no meaningful text and only inflates the file. The
  `sessionLogAltScreen` setting can turn it on anyway.
- **Auto-start** (`sessionLogAutoStart`, default off, 0.8.243): removes "I went to look it up and it
  was never recorded". The check lives at **one place, the entry of `startReadLoop`** — the local,
  android-sh fallback and SSH paths all pass through it, so adding a start path cannot forget it.
  - ⚠ **The setting is re-read from DataStore (`settings.flow.first()`), not from `settingsFlow`.**
    Right after a cold start the first DataStore emission has not arrived and `settingsFlow` still
    holds the default (off), which fails in the worst possible shape: **only the very first tab —
    the one you most wanted — goes unrecorded.** The snapshot that was read is handed to
    `startLogging(snapshot)`, because the destination and file name would fall back to the defaults
    for the same reason — re-reading only the flag would just move the bug to "the first tab lands in
    the default folder".
  - Recording itself stays per-tab and is never persisted (always off after a restart), but **this
    setting is persisted** — auto-start is an intent about every session, not a state of one tab.
- **Masking** (`core/SecretMasker`, `sessionLogMaskSecrets`, **default on**, 0.8.243): key- and
  token-shaped text is replaced with `[z2term:masked]` just before it is written.
  - **A typed password is not the target.** `sudo` and friends turn echo off, so the password never
    reaches the screen or the PTY output and never enters the log at all. What actually leaks is the
    secret that **was displayed**: `name=value` pairs like `TOKEN=…`, and PEM blocks that were `cat`ed
    or pasted. Those two are hit with high precision.
  - ⚠ **Not misfiring outranks catching everything.** Treating any 6-digit number as a one-time code,
    or blanking every long base64 string, riddles `ls` output and build logs with holes and ends with
    the user switching the whole feature off. For the same reason: **only one value is masked** (going
    to end-of-line would eat the `&& echo done` in `TOKEN=x && echo done`), **bare `pass` is not a
    keyword** (it matches `Passed 12 tests`), and **short attached flags like `-p<value>` are left
    alone** (indistinguishable from `tar -pxvf`). The decision is made on **the name, never on what
    the value looks like**.
  - Applied **per completed line**. Cutting mid-line would let the second half of a secret through, so
    while masking is on the last line is held until a newline arrives (the file is read afterwards, so
    this costs nothing, and `close` always drains it).
  - **It applies to the raw log too** — the bug-report log is the one most likely to be handed to
    someone, so it must not be the hole. Bytes round-trip through **ISO-8859-1** so that anything not
    masked stays bit-identical (reading as UTF-8 would turn invalid bytes into `?` and it would no
    longer be a raw log).
  - ⚠ **It is not complete.** A secret in a bespoke format goes straight through. The UI and handbook
    must say so, or "masking is on" gets read as "this is safe". Verified by `SecretMaskerTest`, where
    the not-misfiring cases are the point.
- **Destination** is `filesDir/shared_home/<sessionLogDir>` (`~/z2term-log/` as seen from the shell).
  Being under home, it is reachable from the terminal, from file managers, and from other apps via the
  SAF provider. The name comes from `sessionLogNameTemplate` (`{date}` / `{tab}`) and
  `sessionLogTimeFormat`; with append off, an existing name gets `-2`, `-3`, … so **nothing is
  overwritten**. A broken date format falls back to the default rather than failing to record.
- **Recording is per-tab state and is not persisted** — reopening the app always leaves it off. Since
  whatever appears on screen goes straight into the file, a recording must never be left running by
  accident. Closing a tab (`shutdown`) calls `stopLogging` to flush what is buffered.
- **UI**: the toolbar log button **short tap = start/stop**, **double tap = the detail
  sheet** (`ui/log/SessionLogSheet.kt`); long-press is already taken by reordering. The icon is
  **🔴 while recording / ⚪ when idle** (record-button convention, so state is obvious at a glance; 0.8.206;
  previously always ⏺ with only a green highlight). The sheet switches
  destination, file name, date format, whether to include earlier output, append vs. new file, alt
  screen and raw mode, and previews the next file name. It states plainly that whatever is displayed
  is recorded as-is.
- **`{tab}` sanitization for the file name** (`TerminalSession.resolveLogFile`): when turning the tab
  name into a filename-safe form, **Unicode letters (e.g. Japanese) are kept**; only path separators,
  reserved symbols, control chars and whitespace become `_`. It used to replace "everything that isn't
  ASCII alnum" with `_`, so a Japanese-titled tab produced an **all-underscore filename** (e.g.
  `2026-07-24_0941-____________________.txt`) (fixed in 0.8.206; runs of `_` are collapsed, leading/
  trailing `_/./-` trimmed, and it falls back to `term` if empty).

### 5.1.2 Receiving shares (B1, 0.8.197)

The way text and files reach z2term from another app's share sheet. It **inserts, never executes**
(no newline is appended, so the content just sits on the input line and the user decides).

```
another app "Share" → ACTION_SEND / ACTION_SEND_MULTIPLE
   → MainActivity.handleShareIntent (onCreate / onNewIntent)
   → SharedIntake.textFrom (IO)      … text as-is; files copied into ~/z2term-inbox/
   → SessionManager.insertText       … pick the target tab, then pasteText (bracketed paste)
```

- **`SessionManager.insertText(text, sessionId?)` is *the* entry point for "outside → terminal"**.
  A1 (`z2-session send`) is meant to ride on it, which is why B1 factors it out first. The target is
  chosen as "explicit id → active tab → (if that is a GUI tab) the first terminal tab", and if it is
  not the active tab, **the app switches to it** — text never lands somewhere invisible. The clipboard
  is left alone, so sharing does not shuffle the copy history.
- **Files are taken in as real files.** A share URI is a temporary reference held by the other app and
  is unreachable from the shell, so it is copied into `shared_home/z2term-inbox/` before its path is
  usable by `less` or `python`. The name comes from the sender's `DISPLAY_NAME` with **path separators
  and anything meaningful inside double quotes (`"` `\` `$` `` ` `` `!`) plus control characters
  stripped** (no writing outside the inbox via `../`, and the pasted path cannot be reinterpreted by
  the shell). Name clashes get `-2`, `-3`, … rather than overwriting. The cap is 512 MiB.
- **Shape of the pasted path**: a plain name stays `~/z2term-inbox/foo.txt`. If it contains spaces or
  symbols it becomes **`"$HOME/..."`** — quoting as `"~/..."` would stop `~` from expanding and yield
  "no such file". Multiple files are joined with spaces, ready to use as command arguments.
- **`MainActivity` uses `launchMode="singleTask"`.** Tabs are something the app holds in one screen,
  so a share must not stack another activity that "back" would reveal. Double insertion from the same
  intent is prevented by a marker put on the intent itself (`EXTRA_SHARE_HANDLED`), which also covers
  `onCreate` running again after a rotation.

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
- **Long-press repeat**: numbers / arrows / space / letter keys / **⏎** repeat while held (first 400ms→55ms). ⌫ is 500ms→60ms, with left/right flick = Ctrl+W / Ctrl+U. Modifier keys don't repeat. ⏎ carries the repeat in all three places — the Latin layout, the kana flick layout, and `SpecialKeyBar` shown with the system keyboard (0.8.193; wiring only one of them makes it "work on some keyboards only"). On the kana flick layout the first press commits the pending composition, and the rest send newlines.
- **ALT / META**: both are the same modifier that prefixes the next key with ESC (Meta). ⚠ **META was removed in 0.8.281** and its seat became the entry point for the paste / emoji pad (`PadKey`, below); the Meta modifier now lives only on ALT in Row 5 (the two keys always did the same thing). It applies to `emitChar`/`emitSpecial` **and to `emitCursor`** — arrows used to drop the modifier, so ALT+arrow was just a plain arrow (fixed in 0.8.193). Since the arrow bytes depend on DECCKM and are built by the terminal, ESC is sent on its own first, followed by the arrow.
- **Cycling through faces (`KeyboardFace`, 0.8.305)**: the left end of the bottom row (the seat "あ" used to occupy) is the **face-switch key**, and pressing it moves to the next face. ⚠ **Its label names the face you are going to, not the one you are on** (`あ` / `ABC` / `12`) — with two faces "the other one" needed no label, but with three there is nothing else to tell you where the key leads. The TopBar "あ" → switches the OS IME (a separate path).
  - The faces are **kana (`KANA`) / Latin (`ASCII`) / numbers (`NUMBER`)**. ⛔ **No new switch key is added** — that is the whole point: faces are a swap, not an addition, so **the number of keys on screen does not change** when a face is added.
  - The cycle is **the configured order ∩ the faces available here** (`KeyboardFace.available`). Kana is available only when the app language is Japanese, numbers only when the setting is on. ⚠ **ASCII always survives** — drop both and there would be no face left at all.
  - ⚠ **If the current face falls out of the cycle, fall back to the first one** (`KeyboardFace.next`). This happens right after a settings change, and getting stuck there would leave the switch key doing nothing.
  - **The order is a choice of two** (`あ → A → 12` / `あ → 12 → A`). ⚠ **For three faces there are exactly two cycles up to rotation**, so those two exhaust every possibility (`A → 12 → あ` is the first one rotated — the same cycle). **A drag-to-reorder UI would not offer anything more**, so there is none. With only two faces (English, or numbers off) the notion of an order does not apply, and the setting is not shown at all.
  - Moving between faces **commits any pending kana first** (nothing is carried across a face change).
- **Faces with no switch key (English locale ∧ numbers off)**: with Latin as the only face there is no switch key, so SPACIOUS drops ⇧/CTRL down one row to fill the gap at the home-row start. COMPACT has no left key on the home row to begin with, so that row is unchanged; instead, **from 0.8.397 the CTRL at the right end of the top bar becomes the paste / emoji entry point and CTRL moves down to the left of Row 5** (`PadKey`, below). ⚠ The test is not "is this Japanese?" but "**does the switch key take a seat on this face?**". Turn numbers on in an English locale and the switch key takes its seat, so the arrangement goes back to the Japanese one.
- **Paste / emoji on the Latin layout (`PadKey`, 0.8.281)**: the Latin layout had no entry point for either, so using it as the everyday keyboard in an English locale meant **no emoji and no paste** (the Japanese locale reaches both from the kana layout). ⚠ **There is no room for another key**, so the seats of keys that were **already duplicates** are used. ⚠ **The entry point only appears on a face with no switch key** (0.8.305); where the switch key does take that seat, the entry points live on the **kana face and the number face (both: ESC flick up = paste / flick down = emoji; aligned in 0.8.348)** instead — so one entry point survives in every language and every setting:
  - SPACIOUS = the old **META** at the left of Row 3 (the same modifier as ALT in Row 5).
  - COMPACT = **the right end of the top bar (0.8.397)**. ⚠ Up to 0.8.396 this too was the old **CTRL** at the left of Row 5 (the seat the face-switch key occupies), which left **two CTRL keys — one in the top bar, one in Row 5 — while the paste entry point sat in the bottom corner**. The top-bar CTRL and the Row 5 entry point are simply **swapped**: the top bar sits next to ESC/TAB/⇧ and is easy to reach, and CTRL drops back to one key in Row 5. ⚠ Because it is a swap and not an addition, **exactly one CTRL seat survives on either face** (where the switch key does sit, the top bar keeps CTRL and Row 5 keeps the switch key).
  - **Tap = paste, flick up = paste, flick down = emoji (0.8.397).** ⚠ **Same up/down assignment as ESC on the kana and number faces** — up to 0.8.396 flick up meant emoji, so **the same two pads were reached by opposite directions depending on the face** (on ESC, up is paste and down is emoji). ⚠ Flick up maps to paste rather than emoji so that it matches the tap: **drifting upwards does not change where you land**. ⚠ The key draws 📋 at the top edge, 😀 at the bottom edge and ↕ in the middle, so **the key itself explains the entry point** — the lesson from the ESC up-flick on the Japanese layout, which went unused because it was invisible. The middle changed from 📋 to ↕ to **show first that there is somewhere to go up and down**; the tap target is covered by the 📋 on the top edge.
  - ⚠ **The Latin ESC gets the same up/down flicks (`SilentEscKey`, 0.8.362, user request).** `PadKey`'s seat **only frees up on a face without a switch key**, so **in a Japanese locale the Latin face had no entry point at all** (you could go back to the kana face to open it, but that means hopping between faces mid-word). A flick on ESC costs no seat and gives the **same finger movement** as the kana and number faces. ⚠ **No marks and no popup (user's call).** The kana ESC prints hints on its top and bottom edges, but that face is already a grid of keys carrying extra glyphs, so they fit in; the Latin face is a grid of plain keys and marks there **change the look of the face**. Discoverability is handled in the HANDBOOK instead, and the key's appearance stays put. ⚠ **It behaves identically in an English locale** — keying the ESC behaviour off whether `PadKey` exists would mean **the same face responds to a different finger movement depending on the device language**.
  - While the pad is open the layer is swapped wholesale, **keeping only the bottom row of function keys (× ⌫ space ⏎ ← →)**. ⚠ Unlike the Japanese layout it does not keep the edge columns: with 10 columns they are too narrow to be a finger target. ⌫ is not replaced by "close", same as the Japanese layout (you would lose the ability to delete right after pasting).

#### 6.1.1 The numbers-only face (0.8.305)

- **Why it exists**: the flick face has no digits at all, and Row 1 of the Latin face (`ESC 1..0 ⌫`) puts ten keys side by side, which is fiddly. Typing **runs of digits** — port numbers, IP addresses, `chmod 755` — is common in a terminal, and that case deserves large keys.
- ⛔ **Row 1 of the Latin face is not reused.** The point is large keys, so a **3 × 4 keypad** goes into the same 5-column × 4-row grid as the kana face, giving each key the same area as a kana key.
- Layout (the edge columns **keep the roles they have on the kana face**, so the fingers travel the same way across a face change):

  ```
  ESC       1  2  3   ⌫
  ◀/▼       4  5  6   ▶/▲
  😀/␣      7  8  9   -//
  switch    .  0  :   ⏎
  ```

- **Exactly four symbols** (`.` `:` `-` `/`), limited to what gets typed alongside digits in a terminal (addresses, ports, times, paths, options). ⚠ **Load it up and it becomes a symbol face, blunting the point** — the Latin face's `?#` is there when a full set of symbols is wanted. `-` and `/` are stacked (`JpEdgeStack`) in the seat the kana face gives to 変換, which is free here since there is nothing to convert.
- Digits and symbols go out through **the same exit as a commit** (`ComposingState.commitExternalText`). ⚠ Sending them as bytes (`onBytes`) would let a newline or symbol be reinterpreted as `performEditorAction` and friends while running as the OS input method. Any pending kana is committed first, so nothing is reordered when arriving straight from the kana face.
- **Setting** (`keyboardNumberFace`, on by default): turn it off and the cycle is `あ → A → あ` as before — **neither the keys nor where the switch key leads differ from 0.8.304**.
- ⚠ **It is off by default** (flipped in 0.8.348). A third face makes the switch cycle longer, and it is only in the way for anyone who does not type runs of digits. Whoever wants it turns it on under ⚙Settings › Keyboard.
- **The pads open exactly as they do on the kana face** (0.8.348): **ESC flick up = paste / flick down = emoji**. ⚠ **Up to 0.8.347 the ESC key here was tap-only, so there was no way at all to open paste.** The finger motion learned on the kana face simply did nothing, which reads as **"the clipboard won't open"**. **Never let one face open a pad differently from another.**
- **`␣` takes the whole edge column** (0.8.349). ⚠ It used to be split in two to seat a 😀 key, which left **the most frequently pressed key at half height**. **The kana face had already reverted this in 0.8.306 for exactly that reason; only the number face kept the split.** Emoji still open from the ESC flick, so removing the 😀 seat costs no entry point.
- ⛔ **The left edge column (ESC / ◀▼ / `␣` / face switch) goes through shared building blocks** (`JpEdgeArrows` / `JpEdgeSpace` / `JpEdgeSwitch` / `JpEscKey`, 0.8.349). **Rebuilding the same skeleton per face is what let both bugs above survive on one face only.** Deciding the seat sizes in one place makes divergence structurally impossible. ⚠ **The right edge column is not shared** — the roles differ (kana has 変換, the number face has two symbols). **Share only what is genuinely the same.** ⚠ This **matters most in an English locale**: there the switch key takes the left of Row 5 on the Latin face, displacing the entry point that used to sit there, and putting one on the number face keeps an entry point available in every language.

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
  (right, up). ▼▲ `flush()` then send terminal cursor up/down. ◀▶ move the **composing cursor**
  while composing (§6.2.1), sending terminal cursor left/right only when empty. Space / convert stay
  full-height in Row 3 (kept easy to press).
- Flick rules: tap = あ row / left = い / up = う / right = え / down = お.
- **Dakuten key (小゛゜)**: cycles the previous kana through dakuten→handakuten→small→original (the cycle table is hiragana-based). Repeated kana aren't cycled but stack naturally ("つつ" doesn't become "っ").
- **⌫**: flick left = delete word (Ctrl+W) / flick right = delete to line start (Ctrl+U).
- The long vowel `ー` is the right flick of `わ`. Katakana has no dedicated key; it's chosen from katakana candidates in the candidate bar (§6.2.1).

#### 6.2.1 Kana-kanji conversion (`KanaKanjiConverter` / `ComposingState`)

A best-effort conversion that binary-searches an SKK dictionary (`assets/z2dict.txt`, ~160k lines) + conjugation completion for common verbs/adjectives. The candidate bar (`CandidateBar`) updates on every keystroke.

- **Candidate generation (`convertFlexible`)**: learning history (exact match) → learning history (prefix match = predictive conversion) → whole-sentence best conversion (`nbest`) → **single words whose reading matches exactly (`KkcConverter.wordsFor`)** → exact match (`convert`) / okurigana conjugation (`okuriForms`) → prefix-match prediction (`predict`). Raw kana / katakana always remain as confirmed candidates.
- **Common-word conjugation supplement** (`SUPPLEMENT_WORDS`): the source dictionary carries almost no verb/adjective dictionary forms or conjugations (`あく /悪/灰汁/…/` holds nouns only), so **the only conjugations that convert are those of words listed in the built-in common-word table**. ⚠ **Drop one entry and that verb's entire conjugation becomes unconvertible.** 0.8.360 added 開く ("to open", intransitive) — 開ける/閉める/閉まる/閉じる were all present while **the intransitive 開く was missing, so あかない → 開かない never appeared** (user report). Both readings go in (`あく` / `ひらく`; the source dictionary's `ひらく` only has `啓`). ⚠ **Never add just one side of a transitive/intransitive pair.** Regression: `VerbConjugationCandidateTest`. ⚠ `mergeDict` puts supplement candidates **ahead of the source dictionary**, so an added word becomes the first candidate for that reading (`あき` shows `開き` before `秋` — the same existing behaviour that puts `付き` before `月` for `つき`).
- **Candidate budget** (`DEFAULT_CANDIDATE_LIMIT`=48, 0.8.298): all sources — learning history (4 exact), predictive conversion (6 prefix), whole-sentence conversion (6) and the bundled dictionary — compete for one budget on a first-come basis. ⚠ A small budget means that the more learning a device accumulates, the more the lower sources get pushed out, leaving **words the dictionary has but the user cannot convert to** (with 16 slots, とく never reached 説く / 解く / 溶く — and it does reach them on an empty history, which is why it was easy to miss). The candidate bar scrolls horizontally, so a wider budget costs nothing; it is now 48. ⚠ **Prefix completions get their own smaller cap** (`PREFIX_PREDICT_LIMIT`=12) — completions (仕事 → 仕事相手 / 仕事唄 …) are unbounded, and letting them take the extra room would bury the conversions of the reading actually typed.
- **Single words matching the whole reading** (`KkcConverter.wordsFor`, 0.8.297; moved outside the budget in 0.8.298): every lexicon entry whose reading equals the input is added to the candidates, **ordered by word cost**. ⚠ `nbest` only takes the top 6 whole-sentence paths, so single-word candidates for the same reading could sink below the cut and **never appear in the candidate bar at all** (とく offered neither 説く / 解く / 溶く; rare single kanji from z2dict filled the 16 slots instead. Same for きく → 聞く/効く/聴く and みる → 診る/観る). A word the dictionary has but the user can never pick is a hole in the conversion, so exact-reading words are pulled in directly. For sentence-length readings the lexicon has no entry, so it returns nothing and sentence conversion is unchanged. ⚠ **This stage alone does not compete for `limit`** (0.8.298): inside the budget it was pushed out on devices with grown learning history, which is exactly why 0.8.297 still showed no 説く on a real device. It is spliced in right after the whole-sentence group and duplicates are dropped when the list is assembled. Locked down by `ExactWordCandidateTest` (both with an empty history and with a history that saturates the budget).
- **Symbol ateji suppression** (`SYMBOL_READING_PENALTY`): IPADIC gives single-mora hiragana readings a low-cost symbol surface (と→＆ 3177, に→２, ご→５, …), so the symbol outranks the plain kana/kanji. `loadFromStreams` adds a large penalty to any entry whose reading is a single hiragana **and** whose surface is symbols only (no kana, no kanji), dropping it to the bottom (it stays in the candidate list). Same idea as the existing `KATAKANA_DUP_PENALTY` (over-katakanization) and `KANA_PREFERRED` (kanji-izing auxiliary verbs / formal nouns).
- **Learning history** (`ImeHistoryStore`): ranks confirmed words by frequency and recency (last 7 days) and surfaces them near the top.
  - ⚠ **The tables are immutable and updated by replacement (copy-on-write)** (0.8.296). Recording (`record` / `recordBigram`) runs in IO coroutines while lookups (`historyFor` / `predictHistoryWithReading` / `bigramBonus` / `learnedBlock`) run **on the UI thread for every keystroke**. While a mutable `HashMap` was shared, a record that put a new reading made the UI-side iteration throw `ConcurrentModificationException` and **took the whole app down** — the actual path being "tap a candidate → `commit` calls `record` → `refreshPredict` immediately walks the history to build candidates for the remaining kana". ⚠ That trailing `refreshPredict` only runs when ◀ has left a tail behind, which is why it looked like a crash specific to "type → ◀ → tap a candidate" (it is not specific to the input method either; the terminal's built-in keyboard takes the same path). Readers only ever see a finished table, so the table cannot change under an iteration by construction. ⚠ **Do not paper over it with try/catch** — a broken read would still be possible. Locked down by `ImeHistoryConcurrencyTest` (records and reads concurrently, expects zero exceptions).
- **User dictionary** (`UserDictStore`, 0.8.280): mixes in a user-supplied **SKK-format text file** (`reading /candidate1/candidate2/`). The bundled dictionaries only carry common words, so personal names, company names and private abbreviations **do not appear until learning catches up**; a file makes them available from the first keystroke.
  - **Stored at `filesDir/user_dict/<name>`**: the file chosen in settings is **copied in**, not referenced — SAF grants are not guaranteed across processes, and deleting the original would take the dictionary with it. ⚠ Add/remove works **per file**, so the user can tell where each word came from.
  - **Validation on import**: if no words parse, nothing is saved (malformed files never end up in the list). Over 8MB is refused. The total word cap is 300,000. The outcome — success / too large / no entries / failed — is always reported in a Toast; swallowing it silently leaves "I imported it but conversion did not change" unexplainable.
  - **Two layouts are accepted** (0.8.282): **SKK format** (`reading /candidate1/candidate2/`) and a **table format** (`reading→word→part-of-speech→note`, separated by tabs, ideographic spaces, or two or more spaces) — what kana-kanji dictionary tools export. ⚠ 0.8.280 only looked for the SKK format and **could not read a single word out of a table-format file**. They are told apart by **whether the second column starts with `/`**, so an SKK file written with tabs still parses. ⚠ **Column 3 (part of speech) and beyond are unused** — using them would require mapping onto IPADIC context IDs, and a sloppy mapping breaks the connection costs. A single space is never a column separator (it would be indistinguishable from the SKK format).
  - ⚠ **Only lines whose reading is hiragana** are taken. SKK okuri-ari headwords (`おくr /送/`) need conjugation handling, so they are dropped (taking them would surface "おくr" as a reading). The `;annotation` part of a candidate is stripped.
  - Line parsing is pinned by `UserDictParseTest` (11 cases). ⚠ Users bring files written in more than one way; add a case here whenever another layout is supported.
  - ⚠ **UTF-8 and EUC-JP are both handled**: decode strictly as UTF-8 (`CodingErrorAction.REPORT`) and retry as EUC-JP on failure. A lenient decode would quietly import the EUC-JP dictionaries that are common in the wild as a pile of mojibake.
  - **Three paths into the candidates**: ① exact match (`lookup`) sits right after the learning history in `convertFlexible` (words the user registered outrank the bundled dictionary and Viterbi, but not the history, which reflects what was actually picked); ② prefix match (`predictWithReading`) feeds the prediction stage; ③ `KkcConverter.userDictBlock` adds a synthesised lattice node. ⚠ Without ③ you get a dictionary that works for single words but never inside a sentence. The `BLOCK_BONUS=4000` discount must exceed the katakana penalty (4000), or the registered surface loses to "leave it in katakana".
  - ⚠ **`learnedBlock` is consulted first**: when a reading exists in both, the surface with a track record wins. Otherwise one added line would override a well-worn conversion.
- **Predictive conversion (prefix match over learning history)**: learned phrases whose reading starts with what was typed are surfaced above whole-sentence conversion (`ImeHistoryStore.predictHistoryWithReading` / the prefix-match stage of `convertFlexible`) — genuine predictive conversion that filters "phrases you habitually type" from a partial reading. **When a predicted candidate is confirmed, it is learned under the phrase's actual reading, not the typed prefix**: `ComposingState.commit` reverse-looks-up surface → actual reading via `KkcConverter.predictionReadingMap` and uses it as the `ImeHistoryStore.record` key. This keeps invalid prefix-only history keys out, and the prediction stays reusable under the same reading next time.
- **Bunsetsu-split synthesis (`segment`)**: joins a content word (longest dictionary match) + following particles/okurigana into one bunsetsu (e.g. きょうの → 今日の). **Particles** (の/は/が…) and **sentence-ending auxiliaries** (でしょう/ました/です…) have single-kanji entries (野/葉/増田…), so they are **left in kana** (`PARTICLES` / `AUX_KANA`). Returned when there is ≥1 dictionary-hit bunsetsu ∧ it contains kanji.
- **Leading block and cursor** (`cursor`) (reworked in 0.8.157): while composing, `cursor` (0..length) is the insertion caret and also the leading-block boundary. Candidates are the conversion (`convertFlexible`) of the leading block (`text[0..cursor]` = `splitHead`). Moving the cursor with ◀▶ (`moveCursorLeft`/`moveCursorRight`) grows/shrinks the leading block and the candidates follow (you can reach the line start, 0). Right after typing the cursor sits at the end (= the whole raw kana is the leading block); press ◀ to shrink it for partial conversion. Tapping a candidate / ⏎ (`commitRaw`) confirms the leading block, then the rest (`splitTail`) stays in composing with the cursor at its end; it exits at 0 remaining. The convert key (`convert`) cycles the leading block's candidates.
- **Bunsetsu boundary** (`KkcConverter.bunsetsu`): when splitting a sentence into bunsetsu for the batch-prediction breakdown (`fullPredictionBlocks`) or merged-block learning, it uses the **exact lattice shortest path (`nbest` #1)** (0.8.29): the position-DP `segments` keeps only a single right-context and could mis-split depending on connection costs.
- **Dynamic block segmentation (learned)** (0.8.71): block boundaries are not fixed by dictionary cost alone — they are learned from how often the user confirms a given reading-block. `ImeHistoryStore.learnedBlock(reading)` returns `(top surface, cost reduction)` for confirmed `(reading → surface)` pairs (wired into `KkcConverter.learnedBlock`); during `nbest` lattice construction, any reading ≥2 chars matching a learned block has its node cost reduced (`BLOCK_BASE_BONUS=3000` + `count`-scaled `BLOCK_COUNT_STEP=1500` (capped at count 4) + recent `BLOCK_RECENT_BONUS=1000`). This overcomes the katakana penalty + connection costs after 1–2 confirmations, so a mis-split frequent reading auto-merges into a single block from then on. Even readings absent from the dictionary get a synthesized node (`lc=rc=0`) from the learned surface (unlearned readings behave unchanged). **The cost reduction applies only to the surface the user actually confirmed** (0.8.74), because applying it uniformly to every surface of the reading let the dictionary-cheapest surface win and the user's chosen kanji was not reflected. **Score merged readings at one-word cost and learn consecutively-confirmed runs as merged blocks** (0.8.85): the learned-block synthesized node cost uses a one-word `UNK_COST` baseline instead of length-scaled unknown kana, and `ComposingState` accumulates consecutive confirmations within one split run into `committedRun`, recording the merged reading → merged surface when the run ends (`learnMergedRun`) and on batch confirm (bounded to reading length 2…`MERGE_MAX_READING_LEN`=6).
- **Frequency first — words you use rise inside sentences too** (`ImeHistoryStore.unigramBonus` / `KkcConverter.unigramBonus`, 0.8.398): the frequency and recency of confirmed `(reading → surface)` pairs is applied to **every single node of the `nbest` lattice**. ⚠ **Without this, no amount of use moves a word's rank inside a sentence** — the learning history (`historyFor`) is only consulted on an **exact reading match**, so a long sentence never hits it, and learned blocks (`learnedBlock`) exist to hold chunks together and only apply to readings of **2 characters or more**. In practice 「きょうはあめがふるひだ」 stayed at **「教は雨が降る日だ」** no matter how often 「きょう→今日」 was confirmed (user report: "even in long sentences, the kanji I use over and over do not come first"). The reduction is `UNIGRAM_BASE_BONUS=1200` + `count`-scaled `UNIGRAM_COUNT_STEP=800` (capped at count 8) + recent `UNIGRAM_RECENT_BONUS=500`, staged so that **frequency clears the bundled dictionary's walls one by one** (measured in `UnigramLearningTest`): count=1 clears 教→**今日**, count=3 clears 噺→**話** and とき→**時**, count=8 clears the **kana-preferred penalty (`KANA_PREFERRED_PENALTY`=4000)** for もの→**物**. ⚠ The point is that a kana-preferred default is **not overturned by a single confirmation**. ⚠ It never exceeds learned blocks (max 8500) — if one word's frequency outweighed chunk cohesion, a learned segmentation would fall apart. ⚠ **It is not added on nodes where a learned block already applies** (double-dipping overshoots, letting one confirmation repaint unrelated parts of the sentence).
- **Single-character confirmations are learned too (kanji and katakana only)** (`ImeHistoryStore.isLearnableWord`, 0.8.398): `MIN_WORD_LEN=2` used to **drop every single-character confirmation**. ⭐ Long sentences lean on exactly those **single kanji** (時 / 事 / 物 / 方 …), so the frequency-first change above would have had no data to work with. Single hiragana, symbols and alphanumerics are still not learned (particles filling the history would only block the front of the candidate bar).
- **Whole-sentence batch prediction** (`fullPrediction`): when a tail remains during split, a single "whole-sentence" candidate is shown as a light-green pill, concatenating the leading block's top candidate + the Viterbi 1-best of the remaining kana. Tapping it (`commitFull`) confirms the whole sentence at once. **When `cursor` moves with ◀▶, it is rebuilt via `refreshPredict` to follow the boundary change** (0.8.16). The remaining-kana Viterbi uses the leading surface as context for bigram re-ranking. **Batch confirmation learns per block** (0.8.74): `fullPredictionBlocks` (leading block + `bunsetsu(tail)`) is kept, and `commitFull` learns each block's `(reading → surface)` plus the bigram between adjacent blocks. If the breakdown is inconsistent with `full`, it falls back to a single whole-sentence entry.
- **Head pill shows the whole raw kana** (0.8.157): the head pill was merged from a two-part "green pill of the leading block (`splitHead`) + a separate tail label" into **one continuous pill showing the whole raw kana as typed**. The leading block (before the cursor) is drawn in a strong color and the remaining kana (`splitTail`) dimmed, with a caret (a thin bar in the inverted background color) at the cursor to mark the current leading-block range (= how far you've typed). Tapping calls `commitRaw` (confirms the leading block as raw and advances).
- **Mid-string editing at the caret** (`cursor`) (0.8.157): the edit position is an independent field `cursor` (0..length) and **◀▶ are unified to cursor movement**. Kana/symbols insert at the cursor (`insertAtCursor`), ⌫ (`backspace`) deletes the char before the cursor, and `小゛゜` targets the char before the cursor (`charBeforeCaret`). **You can reach the line start (0), and mid-string fixes work uniformly regardless of sentence length.** Previously the edit position rode on `splitHeadLen` (min 1, auto-split-derived), so it "couldn't reach the line start" and "only worked in long sentences where auto-split kicked in". The old `autoSplit`/`caretEditMode` flags and the "auto-split to the leading bunsetsu on keystroke" behavior were dropped; right after typing the cursor sits at the end (leading block = whole).
- **Reconversion**: right after confirming (composing empty), the convert key = "reconvert" returns the last confirmation to its reading (`restoreLastCommit`).
- **Key background**: during composing, the ◀▶ / convert key backgrounds stay quiet (not green); green is only for the convert key as a "reconvert" hint.

#### 6.2.2 Emoji pad / paste pad (`KeyboardPad`, 0.8.278)

Closes the gap that opened the moment the built-in keyboard started being used **outside the app**
(§6.9). Inside the terminal the toolbar 📋 and copy/paste were enough; as an everyday keyboard in
other apps it was a keyboard that **cannot type emoji and cannot paste**. ⚠ **Japanese keyboard
only** — the Latin layer is terminal-oriented and we do not want to give up keys there (in the
English locale the kana layer is not offered at all, so the pads are unavailable there).

⚠ **There is no room for new keys**, so this is a **layer swap** (the same shape as `あ` for kana and
`?#` for symbols). Only the middle 3 columns (the 12 kana keys) become the pad; **the edge columns
(⌫ ⏎ ␣ ◀▶) stay**, so deleting what you just pasted or hitting return still works. ⚠ Do not turn ⌫
into "close" — you would not be able to delete while the pad is open.

- **Both entry points are flicks on ESC** (0.8.306): **flick up** = paste, **flick down** = emoji.
  ⚠ From 0.8.278 to 0.8.305 emoji had its own 😀 key in the top half of the `␣` column, which left
  **the most-pressed key on the face at half the height of an already narrow edge column**. `␣` is
  whole again and emoji moved to the free direction of the flick paste was already using — the key
  count and the size of `␣` are back to what they were before 0.8.278, and both entries sit together.
  The pad keeps its 😀 / 📋 tabs, so **either entry reaches both**. Closing is the × at the top left
  (ESC is not on screen while the pad is open, so "press the key you entered with" cannot work here).
- **Making the flicks discoverable** (0.8.279 / 0.8.306): the finger movement is invisible, so —
  exactly like the kana keys (`JpFlickKey`), which always show where each flick goes —
  **a dim 📋 sits at the top edge of the ESC key and a dim 😀 at the bottom edge**. On top of that,
  **holding ESC for 300ms** floats `JpEscHintPopup` with "▲📋 / ▼😀" right above the key.
  ⚠ The one-glyph `FlickCommitPopup` cannot be reused: with two destinations the hint has to show
  **the up/down arrangement itself**. ⚠ A plain tap shows nothing — ESC is one of the most-pressed
  keys in a terminal, and popping something up on every press would be in the way. The popup
  disappears as soon as the flick resolves.
- **Emoji** (`EmojiCatalog`): 8 categories, built from **a hand-picked table plus whole code-point
  blocks** (no attempt at full coverage — thousands of glyphs cannot be browsed).
  ⚠ **A table copied one glyph at a time always has holes in it.** 13 of the 80 characters in
  U+1F600–U+1F64F were missing (😌 U+1F60C, 😝 U+1F61D, the cat faces U+1F638–U+1F640 …), and
  **only the person trying to type one could ever notice** (the user's report). 0.8.301 appends
  the blocks for faces (U+1F600–U+1F644), gestures (U+1F645–U+1F64F), hands (U+1F446–U+1F450) and
  animals (U+1F400–U+1F43E), and `EmojiCatalogTest` pins every code point in those ranges.
  ⚠ **The hand-picked table goes first, the blocks after**: the other way round pushes the ones
  people actually use dozens of glyphs down, which is the one real cost of a wider table.
  ⚠ **Code points that need a variation selector stay out of the ranges** (U+1F43F 🐿 renders as a
  monochrome symbol without U+FE0F, so the animal block stops at U+1F43E). ⚠ **Glyphs the device font lacks are not shown**
  (`Paint.hasGlyph`, filtered once). That keeps tofu (□) out of what people send, and new OS versions
  add emoji without touching the table. The first tab is **most recently used**
  (`RecentEmojiStore` to `filesDir/emoji_recent.json`, 48 entries) — real usage concentrates on ~20.
- **Paste** (`clipboard/ClipboardHistoryStore` to `filesDir/clipboard_history.json`, 50 entries —
  **the same single store** the terminal's copy and the 📋 history sheet use. ⚠ **Never give the pad
  its own store**: until 0.8.313 an identically named object fought over the same file with a
  different schema and the pad's history was wiped every time):
  ⚠ **Continuous watching is impossible** — since Android 10 only the foreground app or the current
  input method may read the clipboard. Capture therefore happens **once when the pad opens**, plus
  changes while it stays open (`OnPrimaryClipChangedListener`), which
  reads as "**copy first, then open the keyboard**" from the outside. That ordering needs saying, so
  the empty pad says it. ⚠ Clips flagged sensitive by password managers
  (`android.content.extra.IS_SENSITIVE`) are stored too since 0.8.314, prefixed with 🔒 and
  **cleared automatically after 30 seconds** (§4.6 `ClipboardHistoryStore`).
  ✕ deletes one entry, 🗑 clears all.
- **Pasting closes the pad** (0.8.395): tapping a row inserts the text and returns to `PadMode.NONE`, i.e. back to the keys. ⚠ Before this the pad stayed open and **the × in the top-left had to be pressed after every paste** (the user's report). ⚠ **The emoji pad does not close** — typing several emoji in a row is normal. The line between closing and staying open is **"is this something you keep doing?"**, not entry-point or visual symmetry. ⚠ The behaviour lives in one place inside the pad (`ClipboardPane` -> `onMode(PadMode.NONE)`): there are three call sites (kana / latin / number faces) and per-face copies would leave one behind.
- **Exit** (`ComposingState.commitExternalText`): emoji and pasted text go out through **the same
  path as a confirmation**. ⚠ Sent as bytes (`onBytes`) they would be re-read by the input method,
  turning newlines into `performEditorAction` (= running the search in a single-line field, §6.9).
  Pending kana is confirmed first, and neither is learned or reconvertible (there is no reading).

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

- Multiple tabs (**long-press → drag left/right to reorder**, double-tap to close. **A close-confirm dialog is shown only when a child process is running in the foreground of that tab** — to prevent an accidental tap from discarding work; if the login shell is in the foreground it closes immediately as before. The check compares PTY master `tcgetpgrp` against the **idle-prompt foreground pgid (measured once at startup)**. 0.8.157 compared it against `shellPid`, but `shellPid` is the forkpty child = the **engine (proot/z2root) process pid**, a different pgid from the guest login shell, so it never matched → **always flagged "busy"**; switching to the measured baseline fixes it. 0.8.157, fixed 0.8.160), pinch font zoom (8–32sp), scroll + a ↓ to return to latest, snippets, live theme/font preview.
- Settings (`SettingsSheet`): in 0.8.14, dropped the old bottom sheet stacking from below and now shows as a **full-screen "separate page"** (back arrow ← at top + system-back support).

### 6.9 Offering the built-in keyboard as an OS input method (`Z2ImeService`, 0.8.276)

**What it does**: registers the built-in keyboard as an Android **input method**. Once the user enables
and picks it in the OS list, **the app's own text fields** (snippets, SSH profiles, SFTP, settings,
widget configuration…) **and other apps** all get the same keyboard and the same Japanese conversion
as the terminal.

**Why it was needed**: the built-in keyboard was written as `TerminalKeyboard(onBytes = …)`, a part
that **sends bytes to the terminal**, with no connection to `TextField`. ⚠ Inside one app, the
terminal had its own conversion while touching any text field swapped in the OS keyboard. From the
user's side that reads as "**the built-in keyboard cannot type inside the app**" — a missing feature.

**Replacing each field with hand-drawn input was rejected.** The search bar (`SearchQueryField`) is
that approach, and that single field needed tap-to-move-caret, deleting surrogate pairs as two code
units, and clamping to the layout's own length to avoid a crash. Spreading that over 20 fields would
⚠ **rebuild text selection, copy/paste, caret movement and autofill from scratch**, trading the OS's
quality for our own bugs. As an input method, **the OS keyboard switcher becomes the "built-in vs
system" switch**, so the user's request — "the system keyboard only when I switch to it" — comes
from the platform rather than from our own plumbing.

**Nothing is drawn twice**: the same [`TerminalKeyboard`] + [`CandidateBar`] and the same
[`ComposingState`] / [`KkcConverter`] / [`ImeHistoryStore`] (learning is shared, so a word learned in
the terminal shows up in a text field). ⚠ **Do not fork the look or the candidate behaviour here** —
it would stop looking like the same keyboard and there would be two places to fix. `CandidateBar` and
`scaledKeyboardStyle` were widened from private to internal for this (the height setting applies to
both, so the keyboard does not change size when you switch).

**Only the exit differs** ([`ImeKeyTranslator`]): terminal-bound bytes become `InputConnection` calls.

| What the keyboard emits | What it means in a text field |
|---|---|
| Printable characters (UTF-8) | `commitText` (runs are merged into one call) |
| `0x7F` / `0x08` (⌫) | **a `KEYCODE_DEL` key event**. ⚠ `deleteSurroundingText` does **not** delete a selection |
| `0x17` / `0x15` (⌫ flicks) | delete word / delete to line start, measured against `getTextBeforeCursor` (same counting as `readline`'s `unix-word-rubout`). ⚠ **Against z2term's own terminal they are sent as Ctrl+W / Ctrl+U key events instead** (0.8.312; see below) |
| `0x0D` (⏎) | newline in a multi-line field; otherwise **the action the field asks for** (`performEditorAction`) |
| `0x09` (TAB) | `KEYCODE_TAB` (next field) |
| `0x1B` (ESC, and the ALT prefix) | **dropped**. ALT+key inserts just the character |
| Other control codes (Ctrl+key) | **dropped** |

⚠ **Terminal-only bytes must not reach a text field**: an invisible character slips in and you find
out **after saving**. ⚠ The ⌫ flicks are the exception, kept as delete operations — a gesture that
works in the terminal and does nothing in a text field breaks the illusion of one keyboard.
`ImeKeyTranslatorTest` pins the table.

**Pre-edit is left to the platform**: changes to `composing.text` are mirrored with
`setComposingText` / `finishComposingText` (the terminal and the search bar draw their own underline
because they are not real text fields; here the OS draws it). Commits go through
`ComposingState.onCommit` → `commitText`. ⚠ `commitText` **replaces** the composing region, so an
earlier `setComposingText` does not double up.

⚠ **Dropping a pending composition needs `setComposingText("")` first** (0.8.312). `finishComposingText`
"leaves the text as-is and only removes the styling", so calling it alone **commits the kana you meant
to throw away**. `composing.text` goes empty in two cases: (1) the composition was **dropped** (⌫ flicks
and friends), and (2) right after a commit through `commitText`. Replacing with an empty string first
keeps (1) from turning into a commit; in (2) there is no composing region left, so it is a no-op.
⚠ This also covers `composing.reset()` in `onStartInputView` / `onFinishInputView` (drop the pending
kana when the field changes) — until this fix, **kana meant for the previous field landed committed in
the next one**.

⚠ **"Count, then delete" cannot work against the terminal** (0.8.312). `TerminalInputView` holds no
editable, so `getTextBeforeCursor` is always empty and word / line delete measure 0 = **nothing
happens**. The terminal therefore marks itself in `EditorInfo.privateImeOptions`
(`TerminalInputView.TERMINAL_IME_OPTION`), and only when the IME sees that mark does it send
**Ctrl+W / Ctrl+U as `KeyEvent`s** (`sendDownUpKeyEvents` cannot carry modifiers, so the events are
built by hand with `KeyCharacterMap.VIRTUAL_KEYBOARD`). The terminal turns them back into `0x17` /
`0x15` in `AndroidKeyMapper.mapKeyEvent` and writes them to the PTY, so **the shell decides how far to
delete** — the same result as typing on the built-in keyboard. ⚠ Never send those key events to an
unmarked field: some apps bind Ctrl+W and friends to something else.

**Implementation notes**:
- ⚠ **`InputMethodService` is not a `LifecycleOwner`.** `ComposeView` looks up three owners
  (lifecycle / ViewModelStore / SavedStateRegistry) from the view tree, so the service implements
  them and attaches them with `setViewTree*Owner`. Without it the input view crashes on first show.
- ⚠ **Attach the owners to the input-method window's `decorView`, not only to the `ComposeView`**
  (fixed in 0.8.277). When creating its composition, Compose goes
  `AbstractComposeView.resolveParentCompositionContext()` → `windowRecomposer` and calls
  `findViewTreeLifecycleOwner()` **from the root of the window**. An input-method window is a `Dialog`,
  so that root sits under the decorView (`parentPanel`) and owners set on the `ComposeView` are never
  found. 0.8.276 therefore threw `IllegalStateException: ViewTreeLifecycleOwner not found` as an
  **uncaught main-thread exception**: picking the keyboard **killed the whole app**, terminal sessions
  included. The window is recreated on rotation, so attach in both `onCreateInputView` and
  `onStartInputView`.
- **`ComposingState` belongs to the service.** The input view is recreated on configuration changes,
  so keeping it in Compose would drop kana mid-word. ⚠ **Drop it when the field changes**
  (`onStartInputView`) — carrying it over commits kana meant for the previous field into the next one
  (the same shape of bug as the terminal/search-bar one).
- **The terminal screen is unchanged.** Even with the IME enabled, terminal tabs keep the in-app
  keyboard (that path can pass control codes and modifiers through as they are).
- ⚠ **Enabling and picking are the user's to do** (an OS rule). The app only opens
  `Settings.ACTION_INPUT_METHOD_SETTINGS` and `showInputMethodPicker()` from its settings screen.
- **Three display languages — System / Japanese / English, defaulting to the system** (`LocaleHelper`, 0.8.363, user request).
  ⚠ Through 0.8.362 the default was **pinned to `ja`**, so the app came up in Japanese on any phone —
  leaving a screen you cannot read until you find the setting. ⚠ **The stored value and the effective
  language are separate**: `languageSetting` returns `system`/`ja`/`en`, while `language` always returns
  **`ja` or `en`**. There are 20+ callers of `language` and nearly all of them just test `== LANG_JA`
  (kana face, `z2-*` message language, IME checks), so feeding `system` through would tip **all of them**
  to the "not Japanese" side. The resolution stays inside `LocaleHelper`; only two values leave it.
  ⚠ **`Locale.getDefault()` cannot be used** to read the phone language — `wrap` calls
  `Locale.setDefault`, so once an app language has been applied the process default is overwritten and
  "System" **sticks to whatever was picked last**. `Resources.getSystem()` bypasses the app
  Configuration and stays clean. ⚠ Even on "System" the context is wrapped explicitly with the resolved
  `ja`/`en` (returning `base` untouched would leave the previous `Locale.setDefault` in the process).
- **The settings live in the "Keyboard and input" group** (0.8.277). In 0.8.276 they sat under
  "Resident servers and automation", where nobody looking for keyboard settings would find them.
  For the same reason the old "Input and language" group (IME learning history / language) was folded
  into Keyboard — **everything about typing in one place**.
  ⚠ **The group is named "Keyboard, input and language"** (0.8.279). After the merge the name gave no
  hint that the display-language switch lives there, so people could not find it. The Japanese title
  carries the English word "Language" alongside, so **someone who cannot read Japanese can still get
  to the language switch**.
- ⚠ **The input view keeps clear of the navigation bar** (0.8.279). Android 15 (targetSdk 35) extends
  the input-method window to the edges of the screen too, so without handling it the bottom row
  (← ↓ ↑ → ⏎) hides **behind the 3-button navigation bar** and presses land on Back/Home instead.
  The bottom of the **`tappableElement`** inset, received via
  `ViewCompat.setOnApplyWindowInsetsListener`, becomes bottom padding on the input view and lifts the
  keyboard clear. ⚠ It reads `tappableElement` rather than `navigationBars` because **gesture-navigation
  devices report 0 there** — no wasted gap on devices without a bar. Some paths recreate the window
  without delivering the listener, so `onStartInputView` re-reads it from `rootWindowInsets`.
- ⚠ **The candidate-bar seat is always reserved so the input-view height never moves; the seat is
  transparent and is subtracted from the insets** (0.8.292). [CandidateBar] is 0-height when idle and
  grows to `CandidateBarHeight` (76dp) when conversion starts. In the IME this changes the input-view
  height, which **resizes the IME window** at that moment. For the few frames before the new window
  frame reaches the side that dispatches touches (the system), taps are mapped against the **old
  frame**, so the key **76dp above the one actually touched** fires. ⚠ The drift happens **only at the
  instant the bar appears** — once it is up, taps are exact again (the new frame has landed). The cause
  is therefore the **resize transient itself**, and no amount of after-the-fact coordinate correction
  can remove it. The terminal screen never showed this because there the candidate bar rides a
  bottom-anchored `Column` and the keyboard doesn't move.
  - **Fix**: reserve the seat unconditionally with `Box(height = CandidateBarHeight)` so the
    input-view height does not change with conversion — no resize, hence no transient. The seat is
    **not** made language-dependent, so switching Japanese⇄English doesn't move the height either.
  - **Keeping the seat invisible**: the seat has **no background**, so while the bar is hidden it is
    see-through and the app below shows through. `onComputeInsets` then subtracts the seat from
    `contentTopInsets` / `visibleTopInsets`, declaring that "only the keyboard's top edge downwards is
    the IME". The target app is not pushed up by the seat, and taps above the keyboard pass through to
    it (default `TOUCHABLE_INSETS_VISIBLE`). ⚠ Insets are **not** the window size, so this reporting
    moves the window by 0px — it composes cleanly with the fixed height.
  - The `CandidateBarHeight` constant is shared with `CandidateBar` so the real height and the reserved
    amount can't drift apart.
  - **Verification**: on a device, confirm the input-view height (`View.onSizeChanged`) does not change
    across the bar appearing, and that `rawY - getLocationOnScreen()[1]` equals `MotionEvent.y`.
- **The last face used (ASCII / Japanese flick) is remembered and reopened (0.8.295)**. Every press of
  「あ」/ABC is written to `AppSettings.imeJapaneseMode` (DataStore key `ime_japanese_mode`) and handed
  back as `TerminalKeyboard(initialJapaneseMode = …)` when the input view is composed. ⚠ **Only the IME
  remembers; the terminal screen's built-in keyboard always starts on the ASCII face** — people start
  typing ASCII in a terminal and Japanese in other apps, so one shared setting is always wrong for one
  of them. `TerminalKeyboard` itself never persists the face (defaults `initialJapaneseMode = false` /
  `onJapaneseModeChange = {}`), so **which side remembers is visible from the call site alone**.
  ⚠ The face is only restored while `showJapaneseKeyboard` is true (in English there is no 「あ」 key,
  so opening on the Japanese face would make no sense).
- **A reopened keyboard starts from a clean state (0.8.307)**. ⚠ The input view is **reused, not
  destroyed, when the window closes**, so Compose's `remember` survives a close. Closing the keyboard
  with a pad (emoji / paste) open therefore **reopened it still showing the pad** — the kana keys are
  nowhere to be seen, which reads as broken. `onWindowHidden` bumps a key (`keyboardSession`) and
  `key(keyboardSession) { TerminalKeyboard(…) }` rebuilds the subtree. ⚠ It is bumped **when the
  window hides**, not in `onStartInputView` — that one also fires when **the keyboard stays up and
  only the field changes**, which would drop the face and the modifiers mid-sentence.
  ⚠ This is where "only the face is persistent" pays off: the face comes back from `initialFace`
  (`ime_face` in the settings), and only **transient** state — the pad, ⇧/CTRL/ALT, `?#` — is dropped.

**Candidates for the next stage**: adapting to `EditorInfo.inputType` (digits only for numeric
fields, no learning in password fields), a key to hand back to the OS keyboard, and the globe key via
`supportsSwitchingToNextInputMethod`. Stage 1 covers "**every field in the app can be typed with the
built-in keyboard**".

---

## 7. Settings

| Item | Key | Default | Range/options |
|---|---|---|---|
| Theme | themeName | "ZTS Theme" | 9 options |
| Font | fontId | "monospace" | System / IBM Plex / JetBrains / Fira Code |
| Font size | fontSizeSp | 13 | 4–32 |
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
| GUI clean-install armed | cleanInstallGuiArmed | false | reinstall GUI packages on the next GUI tab; consumed at startup and reset to false |
| Confirm before download | confirmBeforeDownload | true | true/false |
| Keep-alive service | keepAliveService | true | true/false (toggled from the toolbar 🔒 lock; **a switch also appears under Settings › Toolbar while the button is hidden** (0.8.194). **While resident servers run, 🔒 is dimmed and locked** and tapping it opens the quit dialog (0.8.204)) |
| Screen-on lock | keepScreenOn | false | true/false (toggled from the toolbar 💡; **persisted and restored on next launch** (0.8.144); from Settings › Toolbar while hidden (0.8.194)) |
| Keyboard toggle bar | keyboardToggleBar | true | true/false (on = a toggle bar above the keyboard; off = no bar, double-tap the ⌨ button to toggle (0.8.145)) |
| Special key bar | specialKeyBar | true | true/false (whether `SpecialKeyBar` / `GuiSpecialKeyBar` (ESC, TAB, CTRL, arrows…) is shown while the OS keyboard is in use. Off hides it; the built-in keyboard is unaffected (0.8.279)) |
| Toolbar order | toolbarOrder | "" (default order) | comma-separated ids; updated by long-press drag; keeps hidden ids too |
| Toolbar hidden | toolbarHidden | "" (all shown) | comma-separated ids; tapped under Settings › Toolbar. ⚙ cannot be listed (0.8.194) |
| Terminal log destination | sessionLogDir | "z2term-log" | relative to home (~) (0.8.195) |
| Terminal log file name | sessionLogNameTemplate | "{date}-{tab}.txt" | `{date}` / `{tab}` expanded; path separators become `_` |
| Terminal log date format | sessionLogTimeFormat | "yyyy-MM-dd_HHmm" | `SimpleDateFormat` pattern; a broken one falls back to the default rather than blocking |
| Terminal log include earlier | sessionLogIncludeScrollback | false | on = write the screen + scrollback first when starting |
| Terminal log append | sessionLogAppend | false | off = a new file each time (`-2`, `-3` on a clash) |
| Terminal log raw | sessionLogRaw | false | on = keep escape sequences (for bug reports) |
| Terminal log alt screen | sessionLogAltScreen | false | on = also record while the alt screen is active |
| Terminal log auto-start | sessionLogAutoStart | false | on = start recording as soon as a new tab connects (0.8.243) |
| Terminal log masking | sessionLogMaskSecrets | **true** | replaces key/token-shaped text with `[z2term:masked]` (0.8.243); not complete |
| Execution engine (hidden) | executionEngine | "z2root" | proot / z2root / chroot (chroot only when root is unlocked) |
| Engine selector unlock (hidden) | engineSelectorUnlocked | false | toggled by tapping the version 7 times (no root needed; locking resets engine to z2root) |
| chroot unlock flag (hidden) | rootChrootUnlocked | false | true when the 7-tap root self-test passes |
| Language | (dedicated SharedPrefs `z2term_locale`) | OS default | ja / en |
| Notification detection | notificationCaptureEnabled | false | true/false (also needs the OS notification-access grant) |
| Save notification log | notificationLogEnabled | true | false = detect only, write nothing to the file |
| Notification log format | notificationLogFormat | "" (= JSONL) | template such as `{time}{app}{title}{text}` |
| Prepend notification log | notificationLogPrepend | false | true puts new entries at the head (warns past 10 MiB) |
| System event detection | systemEventCaptureEnabled | false | screen/lock/charge/battery/Wi-Fi/BT audio |
| Event log format | systemEventLogFormat | "" (= JSONL) | `{time}{event}{level}{ssid}` |
| Prepend event log | systemEventLogPrepend | false | true puts new entries at the head |
| SMS detection | smsCaptureEnabled | false | true/false (needs the SMS permission; reads the message body directly rather than via notifications) |
| SMS log format | smsLogFormat | "" (= JSONL) | a template such as `{time}{from}{body}` |
| SMS log prepend | smsLogPrepend | false | true puts new entries at the head of the file (warning past 10 MiB) |
| Watch unlock failures | unlockWatchEnabled | false | also needs the device-admin (watch-login) grant |
| Resident server entries | serverEntries | "" | definitions of the servers to keep resident (JSON) |
| Start servers on boot | serversAutostartOnBoot | false | start resident servers at device boot (`BootReceiver`) |
| Resident server low power | serversLowPower | false | true/false |
| Kitty external file transfer | kittyExternalFileEnabled | false | experimental; opt-in for `t=f`/`t=t`/`t=s` |
| SGR mouse input | sgrMouseInputEnabled | false | experimental |
| Detect external SD | externalStorageEnabled | false | on = detect physical volumes and bind them |
| Android host bind | androidHostBindEnabled | false | experimental; exposes `/system` and `/apex` |
| Trace log | traceLogEnabled | false | for developers |

`noInstallTimeout` (disable install timeout), `cleanInstallGuiArmed` (GUI clean re-deploy flag), etc. are also kept in DataStore (`z2term_settings`). SSH profiles are saved as JSON in a separate DataStore (`z2term_ssh`).

**Reset settings** (action): the "Reset settings" button at the end of the settings screen (between App info and Licenses) (`danger` style) shows a confirmation and then calls `AppSettings.resetToDefaults()` (which `clear()`s the `z2term_settings` DataStore). Clearing every key returns all values above, the hidden unlock flags, saved servers, toolbar order and log settings to their defaults (the execution engine goes back to the default z2root). The rootfs (installed OS), user files and language (separate SharedPrefs `z2term_locale`) are untouched.

**Check for updates** (action, 0.8.290): a button placed directly under the version row in the App info section. Only when tapped does `UpdateChecker.check()` (`update/UpdateChecker.kt`) issue a single GET to the GitHub Releases API (`/releases/latest`) and compare the `tag_name`'s major.minor.patch numerically against `BuildConfig.VERSION_NAME`. **Merely opening settings touches no network; there is no automatic check, no launch-time check and no background traffic** (the network is contacted only on the user's tap). If a newer version exists it shows the number and "Open the release page" opens `html_url` via `ACTION_VIEW`. No setting is persisted (a transient state re-fetched on each tap). Version parsing (`numbersOf`) takes only the first three numbers so `-alpha` and the digit-bearing commit hashes of old tags never leak into the comparison.

**Going all the way to the install (`z2-update` and "Download and install", 0.8.371)**: 0.8.290 said the app would **deliberately omit in-app self-update (download+install)** to stay F-Droid-clean. **That was reversed here** (reported by the user: "this is a terminal app, and updating is the one thing still done by hand"). ⚠ **F-Droid compatibility is kept by not using the feature on builds that came from a store, not by lacking the feature** — [`UpdateInstaller.isManagedByStore`] reads `getInstallSourceInfo` (`getInstallerPackageName` below API 30) and **refuses before it even checks** when the installer was `org.fdroid*` / `com.android.vending` / `com.aurora.store*`. Replacing that copy is the store's job, and the comparison itself (hard-wired to GitHub Releases) does not line up with it. ⚠ **`REQUEST_INSTALL_PACKAGES` is declared**, so a submission to F-Droid has to explain that permission (read this before submitting).
  - ⛔ **Never call it "automatic".** Android always shows its own confirmation for an app replacing itself (no exception short of device owner or root). What this does is get you **to that screen**; the last tap is always a person's. Calling it automatic makes someone who never tapped believe they are up to date.
  - **The Settings button and `z2-update` go through one [`UpdateFlow.run`].** Conditions or ordering written on only one side turn into "it installs from the terminal but not from Settings", with no visible reason. Only the wording lives on the caller side (strings.xml for the GUI, `Z2ApiMsg` for the CLI).
  - ⚠ **Check "Install unknown apps" before downloading** ([`UpdateInstaller.canInstall`]). Going ahead without it ends with 20MB fetched and **no prompt appearing**, and nothing on screen says what is missing.
  - ⚠ **Do not drop `STATUS_PENDING_USER_ACTION`** ([`UpdateStatusReceiver`]). The OS only hands the confirmation over **as an Intent attached to the result**; it never shows it itself. Until that is received and `startActivity`d (`NEW_TASK` is required — `z2-update` gets run from the far end of an SSH session), it looks like the button did nothing. The PendingIntent must be **`FLAG_MUTABLE` on API 31+** (an immutable one comes back with nothing attached).
  - ⚠ **Clean up in two places.** The app is killed as it is replaced, so "delete it once installed" cannot be relied on. Delete it if the result arrives, **and again on the next start** ([`Z2TermApplication`] → [`UpdateInstaller.cleanupDownloads`]). Only `z2term-*.apk` and `*.apk.part` are deleted — the download folder can be `/sdcard/Download`, where other people's files live. ⚠ **The downloaded file is named here**, not taken from the release: a name that drifts falls outside the cleanup and stays behind forever.
  - ⚠ **Download into `.part` and rename at the end.** A truncated file under the final name would be mistaken for "already downloaded" and fed to the installer. The size the release declares is checked as well.
  - ⚠ **Handled on a thread of its own** (`Z2ApiBridge.updateWorker`), not the bridge's single worker: a download of tens of seconds there would make `z2-notify` and `z2-session` look frozen. The CLI side waits up to `Z2API_WAIT=3000` (300s) — the default 5s exists so no command waits forever on a stopped app, and a download lives outside that.
  - **Where it lands and whether it is deleted are settings** (`updateDownloadDir` / `updateKeepApk`, `z2-update --dir` / `--keep`), defaulting to inside the app and deleted after the update.

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
| ACCESS_WIFI_STATE | Wi-Fi state and SSID for system-event detection (`SystemEventService`; the SSID is blank without location permission) |
| VIBRATE | `z2-vibrate` (Android bridge) and detection-event notifications |
| RECEIVE_BOOT_COMPLETED | start resident servers at device boot when "start on boot" is on (`BootReceiver`; also handles `LOCKED_BOOT_COMPLETED`). `z2-when` time triggers are also re-armed on boot / app update (`WhenReceiver`) |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | one-tap request to be exempt from battery optimization so keep-alive is not killed (`BatteryGuard`) |
| (protected broadcasts) | `z2-when` receives `ACTION_POWER_CONNECTED`/`_DISCONNECTED`/`BATTERY_LOW`/`_OKAY` to run charge/battery triggers (no permission declaration needed; external apps can't send them). **Since 0.8.214 these land in the detection service `SystemEventService`'s dynamic receiver**, not a manifest receiver (see "Automation hub" above) |

---

## 9. Build / bundled assets

```bash
bash scripts/build-bundle.sh          # generate all bundled assets at once
# individually: build-proot.sh / build-alpine-rootfs.sh aarch64 / fetch-fonts.sh
sh scripts/z2root-cmdtest.sh          # cross-test fragile commands that hit z2root's hard paths (10 groups; skips missing cmds; trailing non-zero summary. SKIP_NET/SKIP_BUILD/RUN_SSHD/RUN_PRIV)
bash scripts/gw.sh :app:assembleDebug   # use this on-device (see below)
./gradlew :app:assembleDebug          # APK (rootfs excluded, runtime DL)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Bundled: `src/main/jniLibs/arm64-v8a/{libz2root,libz2accept}.so` (built from source), `assets/fonts/*.ttf`.
- The rootfs is not bundled; it is fetched at startup via `DistroSpec.ALPINE`'s official CDN URL + SHA-256. There are no third-party native prebuilts (proot/talloc etc. are F-Droid non-compliant), so the engine is z2root built from bundled source.
- **The rootfs in assets uses the `.tgz` extension** (with `.tar.gz`, aapt decompresses and renames it).
- **`useLegacyPackaging=true` is required** (so the `.so` files that get execve'd are placed as real files in nativeLibraryDir).
- **Build through `scripts/gw.sh` when building on-device (aarch64, under proot/z2root)**: in that environment libc's `accept()` returns ENOSYS, and because JDK17's `sun.nio.ch.Net.accept` calls libc `accept()`, the Gradle daemon's TCP IPC dies and the build fails with "Could not connect to the Gradle daemon". `gw.sh` `LD_PRELOAD`s an `accept4` shim (`scripts/accept4-shim.c`) **only where `accept()` is ENOSYS**, and otherwise just calls `./gradlew` (so it does not disturb multi-device use). The shim leaking into aapt2 (bionic) causes a different failure, `libc.so.6 not found`, so the aapt2 wrapper strips `LD_PRELOAD`. Run `bash scripts/gw.sh help` to check whether the shim is applied.
- When the post-extraction setup (`DistroInstaller.postInstallSetup`) changes: bump `DistroBundle.ROOTFS_VERSION` by +1 (users auto-redeploy by swapping the APK).
- **Keep lint at zero warnings** (`bash scripts/gw.sh :app:lintFullDebug`; reached in 0.8.190). A failing `Build & Lint` in CI causes the release job triggered by a tag push to be skipped, so passing lint is a precondition for releasing. Silencing is split into three tiers: `lint { disable }` in `app/build.gradle.kts` for checks that are **permanently meaningless here**, `<ignore path>` in `app/lint.xml` for **one specific location**, and `@Suppress`/`@SuppressLint`/`tools:ignore` with a reason comment at **the deliberate site itself**. Never bulk-move a check into `disable` and kill detection everywhere else.

---

## 10. Known constraints and design pitfalls

### 10.1 Unfixable constraints

**PRoot kernel-privilege constraints (unfixable)**: even appearing as root, `ip`/`nmap -sS`/`ping`/privileged-port bind are unavailable. Alternatives include `nmap -sT`. OpenSSH sshd also breaks privsep, so dropbear is used.

**SysV shared memory (`shmget`) returns ENOSYS (kernel-level, unfixable from the app)**: Android kernels are built without `CONFIG_SYSVIPC`, so `shmget`/`shmat` fail with "Function not implemented". This is **a separate mechanism from POSIX shared memory (`shm_open` = `/dev/shm`)**, which the 0.8.177 bind made available; this one remains. The practical effect is that the X11 **MIT-SHM extension** is unusable, so GUI drawing falls back to transferring pixels over the server socket and is correspondingly slower. Major toolkits detect MIT-SHM availability and fall back automatically, so the usual outcome is "works but slower"; a small number of apps that assume the extension exists may render incorrectly. Where that happens, disable MIT-SHM in the app's own settings.

**Updating the app stops the resident servers (comes from Android; not avoidable app-side)**: replacing the APK makes Android kill that app's process, taking `ServerDaemonService` and the supervisor with it. Update while `sshd` is up and **it is down afterwards**. The "auto-start on boot" setting only fires from `BootReceiver` (device boot completed), so **it does not bring them back after an update**. Restarting them by hand after each update is the current story (confirmed on device during the 0.8.203 checks). Automatic recovery would need a path that starts them on `ACTION_MY_PACKAGE_REPLACED` (not implemented).

### 10.2 Significant bugs already fixed

**Gecko-based GUI apps: the content process cannot find a font under its own sandbox (fixed in 0.8.179; verified on device 2026-07-20)**: the parent (chrome UI) rendered correctly, but **only the content process** died via `MOZ_CRASH` with `unable to find a usable font (%.220s)`, leaving the pane that renders message/HTML content blank. It is easy to confuse with the `/dev/shm` issue (0.8.177), but it is **a separate problem**: shared memory all succeeds (`shm_open` is fine).

The root cause is **z2root's tracer swallowing SIGSYS**. The tracer never delivered SIGSYS to the child for syscalls forbidden by Android's untrusted_app seccomp policy; it forged a return value (`ENOSYS`, or 0 for the privilege-related ones) on the spot instead. Gecko's content sandbox, however, installs its own seccomp filter that turns `openat` and friends into `SECCOMP_RET_TRAP` and **handles the resulting SIGSYS itself, forwarding the request to its file broker**. Seccomp filters are evaluated together and the more severe action wins, so TRAP beats z2root's TRACE: every open in the content process became a SIGSYS, and the tracer swallowed it and returned ENOSYS — meaning **not a single font file could be opened**. This explains every observation: no repro with the sandbox off, still crashing at `security.sandbox.content.level` 1 (the filter is installed even there) and only fixed at 0, the parent being unaffected (no filter), and the crash persisting with the auxiliary processes disabled.

The fix **distinguishes where the SIGSYS came from** and delivers it to the app's handler when it originates from a guest-installed filter. The test is `siginfo`'s `si_errno` (= `SECCOMP_RET_DATA`): Android's filter traps with data 0, whereas a guest filter carries a non-zero trap id (Gecko's `Trap()` numbers them from 1). Android-originated SIGSYS is handled exactly as before, so that path is unchanged. `Z2ROOT_NO_SIGSYS_DELIVER=1` restores the old behaviour for bisecting. Verified on device (Thunderbird / z2root): the one `exited on signal 11` plus `unable to find a usable font` that reproduced every time before the fix is gone. The previous workaround, `MOZ_DISABLE_CONTENT_SANDBOX=1`, still works (documented in the HANDBOOK FAQ). The app does not inject this environment variable by default (silently removing one of an app's own defense layers is left to the user).

### 10.3 Easy pitfalls (recurrence prevention)

- **Do not follow lint advice blindly**: merging `mipmap-anydpi-v26` into `mipmap-anydpi` as lint suggests ("minSdk is 29, so `-v26` is unnecessary") **breaks the build — `mipmap/ic_launcher` no longer resolves** (it departs from the standard adaptive-icon layout). Breaking the launcher icon to silence one warning is not worth it, so `-v26` stays and `app/lint.xml` excludes `ObsoleteSdkInt` **for that folder only** (the check stays active everywhere else).
- **Verify every unused resource before deleting it**: lint cannot follow name-based lookups (`getIdentifier`) or references made through themes, so deleting `UnusedResources` hits blindly crashes at runtime. Search the whole tree for the resource name and confirm nothing in Kotlin, in other `res/` XML, or in `AndroidManifest.xml` refers to it before removing it (strings go from both ja and en).

- The terminal's `/root` is **`filesDir/shared_home`**, not `distros/<distro>/root`. SAF/external-storage bind are based on this too.
- Typing a multi-line script directly into the terminal causes **zsh to misexecute `#` comments / break on continuation prompts** → write it to a file and run with `sh`.
- Restarting dropbear without killing it gives "Address already in use".
- `GestureDetector` **doesn't send onScroll after onLongPress** → long-press selection uses raw MOTION_MOVE.
- When `ScaleGestureDetector`'s **quick scale (single-finger double-tap + drag to zoom) is enabled**, the single-finger DOWN gets absorbed into the internal double-tap watch and `GestureDetector.onLongPress` fires intermittently (a symptom that only recovers after a two-finger pinch). This app only uses two-finger pinch, so it's turned OFF with `isQuickScaleEnabled = false` (0.8.16).
- Realtime PTY input with Compose `BasicTextField` breaks IME sync → `TerminalInputView` + a custom InputConnection.
- Calling `requestFocus` in an AndroidView factory makes the IME pop up on its own.
- Mozc ignores `FORCE_ASCII` (ASCII input isn't guaranteed with a Japanese IME).
- **Inline pre-commit composition for the system (OS) keyboard** (0.8.206): `onCreateInputConnection`'s `inputType` was changed from **`TYPE_NULL` to `TYPE_CLASS_TEXT` (+`TYPE_TEXT_FLAG_NO_SUGGESTIONS`)** and `IME_FLAG_FORCE_ASCII` dropped. With `TYPE_NULL` many IMEs don't compose, so Japanese/predictive input **didn't show inline before confirming**. `TerminalInputConnection.setComposingText` no longer sends composing text to the PTY; it passes it via `onComposingChanged` into `TerminalRenderer.composingText` (same path as the in-app keyboard's `composing.text`) to render inline at the cursor. On `commitText` / `finishComposingText` it writes to the PTY and clears the inline text. **IME behavior differences on device (whether `NO_SUGGESTIONS` still allows CJK composition; handling of unconfirmed text on `finishComposingText`) need device verification.**
- Batching an SGR run into one drawText causes cursor drift → per-cell drawText.
- Writing `*/` (e.g. `*.tgz`) inside KDoc closes the comment early.
- `setUnixMode` must be owner-only (world-writable makes sudo refuse).
- A fixed `/bin/sh` in proot launch runs busybox ash and loses zsh features → `resolveShell`.
- **The chroot engine can't own the controlling terminal via `su`, so Ctrl+C/job control don't work** → launch the login shell through `setsid -c`.
- **GUI video**: mpv's `gpu` output garbles / half-renders on GPU-less devices → `vo=x11` default + `LIBGL_ALWAYS_SOFTWARE`.
- **GUI audio**: PulseAudio must start with the `-n` method or it conflicts with existing config. Passing `AudioBridge`'s target port as 0 yields silence (specify the default port explicitly). **Under z2root**: `--daemonize` fails because it re-execs `/proc/self/exe` (= the launcher) → background it with `setsid …&`. The AF_UNIX `SCM_CREDENTIALS` handshake gets `EPERM` from the kernel when fake-root reports uid 0 → z2root rewrites the `sendmsg`/`recvmsg` (211/212) ucred to the real uid (0.8.53).
- **Wrapped URL detection**: the wrapped flag goes on the "wrap-origin row", not the "continuation row" (reversed, long URLs become untappable).
- **Never write a raw ESC (0x1b) in a test** (0.8.354): spell it out, as in `private val ESC = "\u001b"`. A raw byte is invisible in an editor, so **nobody notices when it goes missing**. `SgrUnderlineAltScreenExitTest` had held an **empty** `ESC` since 0.8.139, which means the test for "no underline survives leaving the alternate screen" **had been passing without feeding a single control sequence** (found while investigating 0.8.354). ⚠ A test that passes while verifying nothing is harder to spot than one that fails.

### 10.4 Per-version fix record (0.8.110–0.8.139)

The **current behaviour** of Kitty graphics, SGR mouse input and swipe dispatch is organized in [§4.5](#45-terminal-emulator-emulator). What follows is kept as the per-version record (where the two differ in wording, §4.5 is the newer one).

<details>
<summary><b>Fix record for 0.8.110–0.8.139 (28 entries)</b></summary>

- **Correct SGR underline subparameters (`4:n`) (0.8.139)**: `processCsi` flattened CSI `:` separators into `csiParams` exactly like `;`, so a TUI sending styled underlines via `\e[4:3m` (curly) parsed as `[4,3]` and applied the subparameter value as a separate SGR (`4:3`→underline+italic, `4:1`→underline+bold, `4:5`→underline+blink). `\e[4:0m` (underline off) processed the `0` in `[4,0]` as a full reset, wiping fg/bg colours too. The stray flags lingered, leaving underlines (etc.) behind after TUIs that lean on styled underlines. Fix: the parser now tracks `csiParamIsSub: MutableList<Boolean>` plus `csiPendingSub`, marking any parameter terminated by `:` as a subparameter of the previous one (`;`-separated → false). The `4` branch of `applySgr` checks the following parameter: a subparameter `0` clears `FLAG_UNDERLINE`, non-zero sets it, and a `while` loop skips consecutive subparameters so they are never misread as standalone SGR codes (`4` alone is still a single underline). Styled variants (1=single/2=double/3=curly/4=dotted/5=dashed) all render as a plain underline. The 38/48/58 extended-colour paths keep their positional reads, no regression. Tests: new `SgrUnderlineSubparamTest` (4 cases: `4:3` sets no italic / `4:5` sets no blink / `4:1` sets no bold / `4:0` keeps colour and clears only underline) + `SgrUnderlineAltScreenExitTest` (1 case: after `?1049h`→`4:3m`→`?1049l`, normal text on the restored screen has no underline). Spec: <https://sw.kovidgoyal.net/kitty/underlines/>, xterm `ctlseqs.txt` "Set/Reset Text Attributes" subparameter notation.
- **SGR mouse input: decouple tap → click from the opt-in (0.8.138)**: 0.8.137 placed the `sendMouseClick` gate behind `isSgrMouseInputActive(sess)` (= opt-in `sgrMouseInputEnabled` ON **and** mouse capture). Because the opt-in is OFF by default, taps stopped reaching mouse-capture TUIs at all — a regression versus 0.8.116〜0.8.136, where `mouseEnabled` alone was enough for the tap-to-click forward. `TerminalInputView.onSingleTapUp` is now back to `sess.emulator.mouseEnabled && sendMouseClick(e.x, e.y, sess)`, so any TUI in capture mode receives `\x1b[<0;col;row M` + `\x1b[<0;col;row m` on tap regardless of the opt-in. Long-press → right click (button 2) and one-finger drag → motion (button 32) remain under the opt-in: with it OFF (default) the behaviour is "tap → TUI click, long-press / drag → Z2Term selection / scrollback / wheel" — the 0.8.116〜0.8.136 baseline; turning the opt-in ON additionally enables right click and drag motion. `MouseEncodeTest` (14 cases) keeps passing; tap → click reuses the same encode path that has been in place since 0.8.116, so no new tests were needed.
- **SGR mouse input (touch → mouse events) opt-in (0.8.137)**: 0.8.116〜0.8.126 already routed **wheel events (button 64/65)** through SGR mouse including alt-screen inertia, but the **single tap / long press / one-finger drag** legs of the protocol — used by every mouse-capture TUI (calendar/date pickers, file managers, multi-pane focus switching, body caret) — had never been wired up. Stage 11 fills that gap behind a default-OFF opt-in (`AppSettings.sgrMouseInputEnabled`, DataStore key `sgr_mouse_input_enabled`): (1) **single tap → button 0** press+release (`\x1b[<0;col;row M` + `\x1b[<0;col;row m`), (2) **long press → button 2** press+release (right click), (3) **one-finger drag → button 0 press + button 32 motion bursts + button 0 release** (motion gated by the existing rule: NORMAL drops motion and returns null; BUTTON_EVENT / ANY_EVENT pass it through). `TerminalInputView` carries `sgrMouseDragActive` and the last-emitted cell so `onScroll` only emits a motion when the cell actually changes (rate-limit), and `onTouchEvent` always finalises with a button 0 release on ACTION_UP/ACTION_CANCEL — when the finger leaves the view mid-drag the release uses the last valid cell so the TUI never sees a stuck press. A single helper `isSgrMouseInputActive(sess)` decides "opt-in ON **and** TUI has `?1000`/`?1002`/`?1003`/`?1006` capture enabled" in one place. With the opt-in OFF (default) every tap / long press / one-finger drag remains a Z2Term-side action (focus, IME, text selection, scrollback swipe), so 0.8.136 behaviour is preserved exactly. Two-finger swipe → wheel (button 64/65) is unaffected — when the opt-in is ON the new drag path guards on `e2.pointerCount == 1`, so two-finger swipes fall through to the existing wheel route. UI lives in the Experimental / developer section as a new toggle plus active-state warning (`settings_sgr_mouse_input_*` strings, ja/en) directly under the Kitty external-file toggle; the setting is honoured immediately via the existing `combine` watcher. Tests: `MouseEncodeTest` grows 10 → 14 (right-click press/release byte stream, drag motion pinned to button 32 + 'M' terminator, NORMAL suppresses motion → null, BUTTON_EVENT allows motion). Existing wheel / left-click / encoding / DECRST coverage stays green. Out of scope for now: bracketed paste (`?2004`), focus in/out (`?1004`) — handled separately when needed. Spec: <https://invisible-island.net/xterm/ctlseqs/ctlseqs.html#Mouse_Tracking>, xterm `ctlseqs.txt` ("Any-event tracking" / "SGR (1006) mouse").
- **Kitty graphics file/temp/shm transfer opt-in (`t=f`/`t=t`/`t=s`) (0.8.136)**: through 0.8.135 the parser dropped every `t=f`/`t=t`/`t=s` request into `Discard` and answered `a=q` with `ENOTSUPPORTED:t=…`. Image-viewer and document-preview TUIs typically send images by **rootfs path, not base64** (inlining a large PNG costs CPU/memory), so without this route those TUIs render nothing. Stage 10 adds the path as an **opt-in (off by default)** that preserves the security boundary. Design: `KittyGraphicsParser` gains `enum TransferKind { File, TempFile, SharedMemory }` and `fun interface ExternalTransferSource { fun read(kind, name, offset, size): ByteArray? }`, exposed via a parser field `externalTransferSource: ExternalTransferSource? = null`. The base64+inflate logic in `handleTransmit` / `handleFrame` is factored into a shared `obtainPayloadBytes(header, payloadStr)`: `t=d` stays as base64 → maybeInflate, while `t=f`/`t=t`/`t=s` base64-decodes the path/name, hands it to `source.read(kind, name, O, S)`, then runs the result back through `maybeInflate`. Kitty's `O=N` (offset) / `S=N` (size) flow through the same call so subrange reads work. `a=q` (query) is extended: when a source is attached, `t=f`/`t=t`/`t=s` reply `OK`; without one they still report `ENOTSUPPORTED:t=…`. Because `android.util.Base64` is not stubbed in the unit-test runtime (so delegation assertions never fired), the parser's base64 decode switched to `java.util.Base64.getDecoder()` (available since Java 8 ≡ minSdk 29; Kitty's spec uses standard base64 so this is interoperable). The host-side implementation lives in new `KittyHostTransferSource(rootfsRoot: File)`: file/tempfile rebases the guest absolute path onto `<rootfsRoot>/<guest path>`; shm names `/<name>` rebase onto `<rootfsRoot>/dev/shm/<name>`. Multiple defenses are layered: (1) path traversal (`/../`) is rejected at the string level, (2) `canonicalFile` verifies the final path is still under `rootfsRoot`, (3) each read is capped at **16 MiB** (same cap as zlib inflate, zip-bomb / DoS), (4) `TransferKind.TempFile` calls `delete()` after reading (Kitty's "the terminal owns and removes it" semantics), (5) file/tempfile require absolute paths and shm names cannot contain slashes. `O`/`S` are honoured; offsets past EOF or sizes over the cap return null. Wiring: `AppSettings.kittyExternalFileEnabled: Boolean = false` (DataStore key `kitty_external_file_enabled`); `TerminalSession.applyKittyExternalTransferSetting` injects `KittyHostTransferSource` into `TerminalEmulator.setKittyExternalTransfer` only when the opt-in is ON and the rootfs is resolvable, nulling it otherwise so toggling at runtime is reflected immediately via the `combine` watcher. `SettingsSheet` adds a toggle plus an active-state warning in the "Experimental / developer" section (`settings_kitty_external_file_*` strings, ja/en). Security: opt-in OFF is the default, so unauthorized sessions are halted at the parser level; even with the opt-in ON the host source layers (a) confines paths under `rootfsRoot`, (b) caps each read at 16 MiB, (c) rejects `..`, and (d) auto-unlinks `TempFile`. Tests: `KittyGraphicsParserTest` grows to 30 cases (no-source Discard; File/TempFile/SharedMemory delegation with offset+size; query OK when a source is attached and ENOTSUPPORTED when not; frame transfer delegation). New `KittyHostTransferSourceTest` adds 12 cases (whole-file read, offset+size slicing, negative-size = read-to-EOF, TempFile auto-unlink, shm `/dev/shm` rebasing, `..` traversal rejection, absolute-path requirement, empty-name rejection, missing file, offset past EOF, zero slice returns empty, oversized size rejected). End-to-end visual confirmation (image-viewer TUI actually renders the file it asked for) is left for on-device testing. Remaining scope: none — the main Kitty graphics protocol surface (stages 1–10) is complete.
- **Kitty graphics zlib payload (`o=z`) + query extension (0.8.135)**: through 0.8.134 the parser fed the base64-decoded bytes straight into PNG / raw RGB / raw RGBA decoders. Any TUI that turned on `o=z` (Kitty's zlib compression option) — `chafa --format kitty --compress`, larger image viewers — would send an unintelligible payload and end up with no image. Stage 9 closes that gap. New `inflateZlib(bytes)` + `maybeInflate(header, raw)` helpers in `KittyGraphicsParser` run the post-base64 bytes through `java.util.zip.Inflater`, with a 16 MiB inflated-output cap as a zip-bomb guard (over the cap → null → `Discard`). Both `handleTransmit` (a=T/t/p) and `handleFrame` (a=f) now route every payload through `maybeInflate` before bitmap construction; any unknown `o=` value other than `z` also returns null → `Discard` instead of silently being treated as raw (safer default for future spec additions). `a=q` (query) is extended to look at `o=` as well: `o=z` reports `OK`, any other value reports `ENOTSUPPORTED:o=<value>`, so capability-probing TUIs can switch on zlib at startup. The existing raw-RGB / RGBA size validation (`s × v × bpp` must match payload length, otherwise `Discard`) from 0.8.129 still runs after the inflate, so a zlib-wrapped payload that lies about `s` / `v` is rejected on the same grounds. Tests: `KittyGraphicsParserTest` grows to 25 cases with `transmitWithMalformedZlibDiscards` (non-zlib bytes under `o=z` → `Discard`), `queryWithUnknownCompressionReturnsError` (e.g. `o=q` → `ENOTSUPPORTED:o=q`), and `queryWithZlibCompressionReturnsOk` (`o=z` → `OK`). Validating the inflated bitmap is still on-device only because `Bitmap.createBitmap` / `BitmapFactory.decodeByteArray` return null without Robolectric. Still out of scope: `t=f` / `t=t` / `t=s` (file / temp / shm transmission), which need the z2root path-translation and shm-permission story sorted out — kept deferred.
- **Kitty graphics animation playback (0.8.134)**: 0.8.133 wired `a=f` onto the receive path but the renderer always returned frame 0, so any "GIF/APNG preview" TUI saw a still image after the first frame. Stage 8 wires in real frame switching + delay-driven playback. `TerminalBuffer` keeps an `AnimationPlaybackState(currentFrame, lastSwitchMs)` per image id; before each draw the renderer calls `advanceAnimations(nowMs)`, which lazily initializes `(currentFrame=0, lastSwitchMs=nowMs)` and advances by `(currentFrame + 1) % (1 + frames.size)` whenever `nowMs - lastSwitchMs >= currentFrameDelay`, wrapping to frame 0 at the end. Frame 0 has no separate delay in the Kitty spec, so it reuses `frames[0].delayMs` — this gives "head returns at the same tempo as the first frame," which matches `chafa --format kitty` and most animation TUIs. `currentBitmap(imageId): Bitmap?` returns `imageCache[imageId]` for frame 0 / uninitialized and `animations[imageId][currentFrame - 1].bitmap` for later frames. `addAnimationFrame` drops the corresponding `animationStates` entry so a freshly-arrived frame restarts playback from frame 0 (avoids mid-cycle visual skips when frames stream in). Renderer integration is minimal: `drawImagePlacement` and `drawPlaceholderTiles` now read `buf.currentBitmap(imageId) ?: <source>` and pass that to `drawBitmap` — so non-animated images and `imageId == 0` paths stay byte-for-byte identical. The driver lives inside `TerminalRenderer`'s composable: a `LaunchedEffect(session.id)` runs `withFrameMillis` whenever `hasActiveAnimations()` is true and bumps a local `animTick` (`mutableIntStateOf`) whenever `advanceAnimations` reports a change, which Compose treats as a recomposition signal for the `Canvas` block. When idle (no animations), the loop falls back to `delay(100)` and re-checks — `HashMap.isEmpty` cost is negligible. Cleanup paths (`clearAllImages` for `a=d,d=A`, `deleteImageById` for `a=d,d=I`/`d=i`) now extend to `animationStates` so playback never lingers on deleted images. Tests: a new `AnimationPlaybackTest` locks down 3 state-machine invariants (`hasActiveAnimationsIsFalseInitially`, `advanceReturnsFalseWhenNoAnimations`, `currentBitmapForUnknownIdReturnsNull`); driving real frames still requires `android.graphics.Bitmap`, which is null in the unit-test environment, so playback fidelity (frame order, delays, wrap-around, multi-id concurrency) is validated on device. Still out of scope: `o=z` zlib compression (stage 9), file/temp/shm transmission.
- **Kitty graphics animation frame accumulation (0.8.133)**: 0.8.131–0.8.132 brought "register, identify, place, delete, expand id to 32-bit," so stage 7 puts Kitty's animation `a=f` (frame transmit) **on the receive path** while leaving playback for stage 8. Without this, any image viewer / GIF preview TUI that lays an animation down as "send frame 0 via `a=T`, then keep adding subsequent frames via `a=f`" would see only the still frame 0 because the parser dropped `a=f` into `Discard`. A new `AnimationFrame` (bitmap, `delayMs`, `composeMode`, `xOffset`, `yOffset`) is added to `TerminalImage.kt`; `TerminalBuffer` gains `animations: MutableMap<Int, MutableList<AnimationFrame>>` alongside the existing image cache and virtual-placement table. `addAnimationFrame` / `getAnimationFrames` append and read, while `clearAllImages` and `deleteImageById` extend their cleanup to `animations` in lockstep with `imageCache` / `virtualPlacements`. `KittyGraphicsParser` adds a new `handleFrame` branch returning `Result.Frame(imageId, bitmap, delayMs, composeMode, xOffset, yOffset, frameIndex, quietLevel)`. The parser splits `z=` interpretation by action: `if (action == "f") 0 else (header["z"]?.toIntOrNull() ?: 0)`, because **Kitty's spec uses `z=N` as delay-ms when the action is `f`, and as Z-index otherwise**. Validation: `i=N` required (id 0 → `Discard`), `t=d` only (file/temp/shm → `Discard`), bitmap-build failure (raw RGB null / PNG decode null) → `Discard`. `TerminalEmulator` simply dispatches `Result.Frame` to `buffer.addAnimationFrame`; rendering still shows frame 0 (the cached source image) so on-screen behavior is unchanged in this step. Frame switching / delay-driven playback is wired in 0.8.134 (stage 8) via `Choreographer` or `Handler`. `KittyGraphicsParserTest` gains 3 cases: `frameWithoutImageIdDiscards` (id 0 → `Discard`), `frameWithoutPayloadDiscards` (empty payload → `Discard`), `frameWithFileTransmissionDiscards` (`t=f` → `Discard`). Detailed `Frame` field validation (delay/compose/offset) is deferred to on-device testing because `Bitmap.createBitmap` / `BitmapFactory.decodeByteArray` return null without Robolectric. Still out of scope: animation playback (stage 8), `o=z` zlib compression, file/temp/shm transmission.
- **Kitty graphics image id 32-bit extension (0.8.132)**: 0.8.131's Unicode placeholder route only pulled the image id out of the foreground truecolor, so any session that drove more than 24 bits' worth of distinct images would start to collide. Kitty's spec stores the **high 8 bits in the cell's underline color**, so this bump wires that in. A new `currentUnderlineColor: Int` state is added to `TerminalEmulator` (separate from `currentFg`/`currentBg` so `SgrAttribute` does not need to grow) and `applySgr` learns SGR 58:2:R:G:B (RGB underline), 58:5:idx (indexed underline), and 59 (reset); SGR 0 (full reset) also resets the underline color. `putKittyPlaceholder` ORs the R byte of `currentUnderlineColor` into bits 24–31 of the image id when the underline color is in RGB form (`(id32high shl 24) or id24`); when no underline color is active, behavior stays bit-for-bit identical to 0.8.131. Underline rendering itself is intentionally still unimplemented (the underline color is consumed only to relay the placeholder high byte), so `TerminalCell`'s shape does not change. `KittyPlaceholderCellTest` adds 3 cases (`underlineColorAddsUpperEightBitsOfImageId`, `sgr59ResetsUnderlineColorSoImageIdStays24bit`, `sgrResetClearsUnderlineColorToo`) for 9 total. Still out of scope: animation frames (`a=a`), file/temp/shm transmission, and the `o=z` compression option.
- **Kitty graphics virtual placement (Unicode placeholder) (0.8.131)**: 0.8.130 covered "register / draw / re-place / delete / Z-index layering," so this bump adds **deferred placement through Unicode placeholders**. Many TUIs (image viewers, document renderers, anything with frequent thumbnails) separate "register the bitmap" from "decide where it goes": they first send `\e_Ga=T,U=1,i=N,f=100,t=d,…\e\\` to register the image as a **virtual placement** (cursor stays put), then write `U+10EEEE` + combining diacritics in the body to say "this cell holds tile (row, col) of image N." Without this route, the terminal answers `a=q` with `OK` and then shows nothing — the bitmap is received but never anchored. New `KittyGraphicsParser.Result.VirtualPut` (for `a=p,U=1`) and a `unicodePlaceholder` flag on `Result.Transmit` (for `a=T,U=1`) route the registration into `TerminalBuffer.virtualPlacements: Map<imageId, VirtualPlacementSpec>` (bitmap + grid columns/rows + Z-index + placement id). On the cell side, `TerminalCell` gains `placeholder: PlaceholderRef?` (image id + srcRow + srcCol + placementIdLow). `TerminalEmulator.putCodepoint` is extended to: (a) detect `U+10EEEE`, write a single-cell placeholder via `putKittyPlaceholder`, and pull the image id (24 bits) out of the current truecolor fg (`\e[38;2;R;G;B`) as `(R<<16)|(G<<8)|B`; (b) consume up to 3 combining diacritics — sourced from Kitty's fixed 297-entry table (`KittyPlaceholder.DIACRITICS`, binary-searched) — and overwrite `srcRow` / `srcCol` / `placementIdLow` in order via `applyPlaceholderDiacritic`. Normal characters (`putChar` / `putWideChar` / `putSurrogatePair`) and any non-diacritic codepoint release the stage so further combining marks are treated as ordinary text. The renderer's existing two-layer pass (z<0 in Pass 2.7, z>=0 in Pass 3.5) now calls a new `drawPlaceholderTiles` helper that walks each row's cells and, for any cell with a `placeholder`, looks up the spec and slices the source bitmap's `(srcCol / widthCells, srcRow / heightCells)` tile into a 1-cell `drawBitmap` srcRect→dstRect (cells without a registered spec are simply skipped — they remain empty until the bitmap arrives). Delete commands (`a=d,d=A`/`d=I`/`d=p`) drop the virtual-placement table alongside regular placements. Placeholder cells are substituted with a space in `TerminalRow.toText` / `TerminalBuffer.getRangeText` to prevent stray surrogates in copies; `setChar`/`clear`/`setClearedWith`/`copyFrom` always reset the placeholder ref, so "drawing text on top erases the tile" works as expected. A new `KittyPlaceholder.kt` holds the 297-entry diacritic table and the `PlaceholderRef` data class. `KittyGraphicsParserTest` grows to 18 cases (`VirtualPut` routing + regression-guarding regular `Put`); a new `KittyPlaceholderCellTest` adds 6 cell-level cases. Still out of scope: animation frames (`a=a`) and file/temp/shm transmission (`t=f`/`t=t`/`t=s`).
- **Kitty graphics query response / quiet level / Z-index layering (0.8.130)**: 0.8.129 covered "transmit / cache / place / delete." This bump opens the **reply path for capability probes (`a=q`)** and gives image placements a real **two-layer stacking model**. Many TUIs start by sending `\e_Gi=N,a=q,t=d,f=N,s=1,v=1;\e\\` and treat the response (`\e_Gi=N;OK\e\\` or `ENOTSUPPORTED:...`) as the "is Kitty graphics here?" probe; without that reply, they silently fall back to ASCII art and never draw an image. A new `KittyGraphicsParser.Result.Query` is wired through `TerminalEmulator`, which sends `ESC _ G [i=N] ; <message> ESC \` back via the existing `output` callback. The Kitty quiet level (`q=0/1/2`) maps directly: q=0 sends everything, q=1 errors only, q=2 stays silent. Z-index (`z=N`) is threaded through both `Transmit` and `Put` into `TerminalImage.zIndex`. The Renderer now walks each row's image list twice: **Pass 2.7** draws `zIndex < 0` placements above the cell background but below the text, and **Pass 3.5** draws `zIndex >= 0` placements above the text. Same-Z order stays insertion-ordered (last wins). This is what enables "subtitled thumbnails," "icon overlays on top of text," and bubble-style placements to render the way TUIs intend. `KittyGraphicsParserTest` gains query OK / query ENOTSUPPORTED (transmission mismatch) / query quiet propagation / Put-with-z propagation cases, reaching 16. Actual Bitmap rendering still requires on-device validation since the unit-test stub has no `BitmapFactory`.
- **Kitty graphics multi-placement / detailed delete / raw RGB(A) (0.8.129)**: 0.8.128 only wired the minimum (`a=T,f=100,t=d` and a wipe-all `a=d`). This bump takes the next step. `KittyGraphicsParser.Result` is restructured into 7 variants: `Transmit` (with a `display` flag to fold `a=T` vs `a=t`) / `Put` (cache-backed re-placement for `a=p`) / `DeleteAll` / `DeleteImage` / `DeletePlacement` / `Continue` / `Discard`. The delete sub-actions `d=A` / `d=I` / `d=i` / `d=p` are routed per Kitty spec, and image ids are read from either uppercase `I=N` or lowercase `i=N` (the free/keep difference is absorbed in cache management). Input formats grow to **`f=24` (raw RGB, 3 bytes/px)** and **`f=32` (raw RGBA, 4 bytes/px)**: the parser uses `s=N` / `v=N` for pixel dimensions and assembles an `IntArray` → `Bitmap.createBitmap(…, ARGB_8888)` (PNG still goes through `BitmapFactory.decodeByteArray`). For multi-placement, `TerminalRow.image: TerminalImage?` is replaced by `images: MutableList<TerminalImage>`, so the same anchor row can hold multiple placements with distinct `(imageId, placementId)` pairs in parallel. Hit-tested invalidation (`setChar` / `clear` / `resize`) is sharpened to "remove only placements whose footprint overlaps the column," leaving siblings intact. `TerminalBuffer` gains an image cache (`imageId → Bitmap`) plus `deleteImageById` / `deletePlacement`; `a=T`/`a=t` populate it, `a=p` reads from it, `a=d` clears it. Re-using the same `(imageId, placementId)` on the same anchor row **replaces** the existing placement (position update). `KittyGraphicsParserTest` grows to 12 cases (`Continue`, `DeleteAll`, `DeleteImage(I=42)`, `DeletePlacement(i=7,p=3)`, `Put(i=11,p=2,c=4,r=2)`, raw RGB without `s,v` → `Discard`, etc.). Animation, virtual placement, and file/temp/shm transmission stay out of scope.
- **Minimal Kitty graphics rendering (0.8.128)**: 0.8.127 stopped at "absorb the APC body so the screen no longer gets polluted." This bump takes the next step: parse the APC body as the Kitty graphics protocol and actually draw the image. Staging keeps side-effects local: (1) stop the bleed (done in 0.8.127), (2) minimal render (this bump), (3) multi-placement / animation / virtual placement later. Scope of this bump: `a=T,f=100,t=d` (transmit-and-display / PNG / direct base64), both single-shot and `m=1` → `m=0`/omitted chunk concatenation. `a=d` is mapped to wipe-all. Parsing lives in `KittyGraphicsParser` (key=value parser + base64 concat + `BitmapFactory.decodeByteArray`). The image is stored as a `TerminalImage` on the anchor row's `image` property (`TerminalRow`), and the Renderer draws it with `drawBitmap` over the `widthCells × heightCells` rectangle between the background pass and the text pass (new Pass 2.7). Cell footprint comes from explicit `c=N` / `r=N` if present, otherwise from the Bitmap pixel size divided by the per-cell hint propagated through `TerminalEmulator.setCellMetricsHint` (Renderer pushes its current `cellW` / `lineHeight` whenever they change). The cursor advances horizontally by `widthCells` (vertical advance is left to the TUI's own `\n`). Writing into image cells via `setChar`, `clear`, or a shrinking `resize` invalidates the image (`TerminalRow.image = null`) so drawing text on top "erases" the image as expected. `TerminalRow.copyFrom` carries `image` along, so a `DECSTBM` in-region scroll moves the image one row rather than dropping it. Multi-placement / per-id deletion / animation / virtual placement / file transmission / raw `f=24` / `f=32` are intentionally out of scope and fall to `Result.Discard` (added in later bumps). `KittyGraphicsParserTest` locks down `Continue` / `ClearAll` / `Discard` / `reset` across 9 cases (`Image` requires a working `BitmapFactory`, so the rendering path is validated on-device).
- **Absorb DCS/APC/PM/SOS bodies (0.8.127)**: image-transfer protocols, DCS replies, and other "string-class" escape sequences sent by TUIs used to land in `processEscape`'s `else` arm — the start byte (`P`/`_`/`X`/`^`) was logged and discarded, but the body that followed (key=value parameters, base64 payloads, embedded `\r`, CSI-lookalike runs) was then received in GROUND and written straight to the screen. This single gap was reported as three different bugs: image-transfer body glyphs leaking onto the screen, "SGR mouse-style" stray bytes when the CSI-lookalike run inside a DCS was misparsed, and the cursor jumping to column 0 mid-render because `\r` inside the body hit GROUND's CR handler. Fix adds `State.STRING`; `processEscape` routes `P`/`_`/`X`/`^` into it, and `processString` discards the body until **BEL (0x07) or ST (`ESC \`)**, then returns to GROUND. Malformed terminators (`ESC` followed by anything other than `\`) follow xterm semantics: stop the string state at that point and re-interpret the byte under ESCAPE. `StringStateAbsorbTest` locks down five cases: APC + image-style payload, DCS + CSI-lookalike, PM/SOS + BEL, embedded CR/LF inside the body, and malformed terminator recovery. This bump only stops the screen pollution — actual image rendering (Kitty graphics etc.) is staged for a later bump.
- **Primary-screen swipes are gated by the PTY foreground process (0.8.126)**: The 0.8.125 "drop the primary-screen wheel branch entirely" pass killed the SGR leak but also broke scrolling for TUIs that legitimately use mouse reporting on the primary screen. The emulator state alone cannot tell "wheel-mode TUI is currently active" from "wheel-mode TUI exited but forgot to disable mouse", so the gate moves to the PTY layer: **`tcgetpgrp(master_fd)`** is queried per-swipe; wheel is forwarded only when the foreground process group is **not** the shell PID (i.e., a child process owns the tty). Plumbing: new `pty_jni.cpp::nativeForegroundPgid(fd)`, `PtyProcess.foregroundPgid()`, default-`true` `ProcessChannel.hasForegroundChild` (so SSH and other remote channels that cannot answer fall back to the old behaviour), `LocalPtyChannel.hasForegroundChild` (originally `fg >= 0 && fg != shellPid`; but under proot/z2root `shellPid` is the engine pid — a different pgid from the guest shell — so it was always true; 0.8.160 changes it to compare against the **measured idle-prompt pgid**), and `TerminalSession.hasForegroundChild`. `TerminalInputView.onScroll`'s primary branch ANDs `sess.hasForegroundChild` into the existing `mouseEnabled && atBottom && distanceY > 0` gate. While a TUI runs (child foreground) wheel reaches it; once the shell is foreground again, the `\e[<…M` leak is suppressed even when `mouseEnabled` is still set. The 0.8.124 DECRST 1049/1047/47 auto-OFF stays as a complementary defence for transports that cannot answer foreground-pgid queries.
- **Force mouse reporting OFF on alt-screen exit + carry fling start coordinates (0.8.124)**: Two fixes in one bump. (1) TUIs that send DECRST 1049 (`rmcup`) on exit but forget DECRST 1000/1006 (mouse off) leave `emulator.mouseEnabled = true` after returning to the primary shell. Subsequent swipes hit the primary-screen branch of `TerminalInputView.onScroll` (`mouseEnabled && atBottom`), which leaked wheel events (`\e[<…M`) into the PTY as literal prompt input — readline ate the `\e[<` prefix and the parameter+`M` tail showed up at the shell prompt. Fix: in `TerminalEmulator.kt` the DECRST 1049 / 1047 / 47 branches (alt → primary) now force `mouseProtocol = MouseProtocol.OFF`. xterm's spec leaves mouse mode orthogonal to the alt-screen state, but in practice the damage is concentrated on the primary shell's readline, so cleaning up stale mouse mode on alt exit is the pragmatic choice. Well-behaved TUIs that emit DECRST 1006/1000 themselves end up doing harmless double cleanup — behaviour unchanged. `MouseEncodeTest` gains regressions for DECRST 1049/1047/47 → OFF. (2) On multi-pane alt-screen TUIs, fast-swiping inside a focused pane scrolled the focused pane during the swipe but then started scrolling a different pane (the one sitting at the screen centre) the instant the fling kicked in. Root cause: `flingRunnable` called `sendMouseWheelRows` with hard-coded screen-centre coordinates (`rows/2`, `cols/2`), so every inertial wheel event landed on the centre cell and the TUI routed the scroll to whichever pane was under that coordinate. The swipe phase was fine because `sendMouseWheelFromSwipe(e2.x, e2.y, ...)` passes the finger position through. Fix: `onFling` stashes `e2.x` / `e2.y` into `flingPxX` / `flingPxY`, the `flingRunnable` forwards them to `sendMouseWheelRows`, and `sendMouseWheelRows` runs `pixelToAbsCell` to derive the cell — falling back to screen centre only when the pixel is unset (`-1f`) or off the view. Also resets `mouseWheelAccumDy` at fling start so a previous swipe's leftover fraction cannot bleed into the inertial direction.
- **Full flavor's default execution engine switched to z2root (0.8.123)**: previously the full flavor's `executionEngine` default was `ENGINE_PROOT`, so a fresh full install started in PRoot on first launch. The foss flavor ships no PRoot prebuilt, so `ProotLauncher` already coerces foss to z2root via `BuildConfig.IS_FOSS`, which meant the two flavors diverged on first launch. In day-to-day maintenance z2root has been the primary engine for a while, and the inline `AppSettings` comment ("default is proot") had drifted out of date. Fix: change both the `AppSettings.Snapshot.executionEngine` data-class default and the DataStore fallback in `flow.map { ... }` from `ENGINE_PROOT` to `ENGINE_Z2ROOT`. Existing users who explicitly picked PRoot keep that value (the key is stored, no regression); only brand-new installs and post-reset states start on z2root. The hidden engine selector (7-tap unlock) still lets the user switch to PRoot/chroot. Docs / README "default PRoot" wording is realigned to z2root.
- **Loanword dictionary extended with languages / tools / OSes / syntax (0.8.122)**: the 0.8.121 seed covered ~200 Git/shell/build/network/UI words but missed `ぱいそん → python`, prompting a feedback pass. Added languages (python/ruby/java/javascript/typescript/kotlin/go/rust/swift/php/perl/scala/dart/lua/haskell/clojure/elixir/csharp/cpp), tools (nodejs/npm/yarn/pnpm/pip/gem/cargo/gradle/maven/bazel/make/cmake/docker/kubernetes/k8s/terraform/ansible), editors (vim/neovim/emacs/vscode), OSes/distros (linux/ubuntu/alpine/kali/arch/debian/fedora/windows/mac/android/ios), and code-syntax keywords (print/return/else/break/continue/try/catch/throw/finally/namespace/public/private/protected/static/abstract/interface/inherit/override/annotation). Total ~310 entries. The pipeline is unchanged (`LOANWORD_ENTRIES` → `buildLoanwords()` → three-way `mergeDict` in `ensureLoaded`).
- **Built-in English-loanword entries for the IME (0.8.121)**: the SKK dictionary has no entries mapping katakana-origin hiragana readings (`こみっと`, `ぷっしゅ`, `おーけー`, …) to plain English spellings (`commit`, `push`, `ok`, …), so `convert("こみっと")` returned nothing and the user had to switch keyboards every time they wanted to type an English word in the terminal. `KanaKanjiConverter` now ships a `LOANWORD_ENTRIES` table (~200 frequent Git/shell/build/network/UI loanwords; all candidates are lowercase English) and a `buildLoanwords()` builder, and `ensureLoaded` merges them in three stages: `mergeDict(mergeDict(base, buildSupplement()), buildLoanwords())`. `mergeDict` already places the `extra` candidates first on a head collision, so the loanword spellings appear before any hiragana dictionary candidates. `convertFlexible`'s N-best pipeline is untouched — the entries flow alongside the learning history, and after the user picks a spelling once, [`ImeHistoryStore`] promotes it to the top on subsequent inputs. Short hiragana headwords that collide with native Japanese words (`ぼたん` → `button` vs `牡丹`, `たぶ` → `tab` vs `他部`) are intentionally excluded to avoid a UX regression; only unambiguous katakana-origin loanwords are seeded.
- **New-tab PTY rows/cols no longer drift out of sync with the canvas (0.8.120)**: opening a new tab left a strip of empty rows between the terminal and the keyboard ("not really the bottom") and long lines failed to wrap at the screen edge, scrolling off-screen instead. `TerminalRenderer`'s `BoxWithConstraints` synced the PTY size with `LaunchedEffect(rows, cols) { delay(120); session.onResize(rows, cols) }`, but the keys were just `(rows, cols)` with no `session.id`. When a new tab opened at the **same display dimensions** as the previous one (typical on a single device), the lambda was not re-evaluated against the new session, so the new PTY stayed at the default 24×80. Pinching changed `fontSize` → `lineHeight` → `rows/cols`, which finally bumped the key and made `onResize` fire — the well-known "pinching fixes it" symptom. (The neighbouring `updateCellMetrics` LaunchedEffect had previously been patched with `session.id`; the resize one was missed.) Fix: include `session.id` in the key so the effect always re-runs on tab switch. The 120ms debounce against pinch storms is preserved.
- **Alt-screen swipes are forwarded as wheel events in both directions (0.8.119)**: the 0.8.115 / 0.8.116 routing assumed "downward swipe (= look back) falls back to scrollback", but that assumes a primary screen with scrollback and a reader-style TUI. **Alt-screen TUIs** switch with `?1049h` / `?47h`, set `buffer.primaryActive = false`, and have a permanent scrollback size of 0. Downward swipes fell into the scrollback fallback and did nothing — the user report was "touch slide only scrolls down, not up". Fix: `onScroll` computes `isAltScreen = !buffer.primaryActive` and, on the alt screen, calls `sendMouseWheelFromSwipe` regardless of the sign of `distanceY`. `sendMouseWheelFromSwipe` now accumulates a **signed** `mouseWheelAccumDy`; positive notches emit wheel-down (button 65) and negative notches emit wheel-up (button 64), so a mid-swipe direction reversal cancels naturally. `onFling` skips its guard (`velocityY < 0 && scrollOffset == 0` → no-op) on the alt screen, and the `flingRunnable` calls a new helper `sendMouseWheelRows(delta)` when alt + mouseEnabled, turning each inertial frame into a wheel notch on the PTY so alt-screen TUIs retain an inertial feel. Primary-screen behaviour is unchanged — the 0.8.115 / 0.8.116 routing is preserved verbatim.
- **z2root: try_subst_proc_open now resolves dirfd-relative openat (0.8.118)**: Real-device verification of 0.8.117 found that procps-ng `pgrep`/`pidof`/`ps -o comm`/`top` were still displaying `libz2root.so` even though `cat /proc/<pid>/stat` showed the properly compacted `(bash)`. Root cause: procps-ng's `readproctab2` does `opendir("/proc")` and then issues `openat(dirfd, "<pid>/stat", ...)` against that dirfd — a relative-path openat. `try_subst_proc_open` only ran `proc_open_kind` against the literal pathname argument and required it to start with `/proc/`, so the relative path slipped through, the temp substitution never fired, and the original `(libz2root.so)` was served straight from the host /proc. Fix: at the entry of `try_subst_proc_open`, when the path is relative and `dirfd != AT_FDCWD`, `readlink(/proc/<self_pid>/fd/<dirfd>)` is used to recover the host real path the dirfd points to, then a combined `<dirpath>/<raw>` is fed into `proc_open_kind`. Non-/proc dirfds are passed through. When the substitution succeeds and the original was a dirfd-relative openat, `regs[0]` is overwritten with `AT_FDCWD` so the temp absolute path is opened directly (avoids `/proc/<pid>/fd/AT_FDCWD` style misinterpretation).
- **z2root: parenthesised comm in /proc/<pid>/stat and the Name line in /proc/<pid>/status are now compacted by left-shift when shortened (0.8.117)**: 0.8.112/0.8.113 rewrote the kernel-leaked `libz2root.so` in `/proc/<pid>/{stat,status}` to the argv0 basename (e.g. `bash`), but to preserve offsets of trailing fields (stat=`state ppid …`, status=`Uid: Gid: …`) it used **length-preserving padding with trailing spaces** (`(bash        )` / `Name:\tbash         \n`). Real-device verification over SSH found that **procps-ng `pgrep bash` / `pidof bash` returned no match and `top` COMMAND column was rendered as `libz2root+`** — procps-ng compares comm with the trailing spaces verbatim, so `bash<spaces>` does not equal `bash`. Fix: `fake_stat_comm` / `fake_status_name` now **left-shift the bytes after the closing `)` (stat) / after the line terminator (status) via `memmove` to compact the buffer** when the new name is shorter, and return the new length. The two call sites pass the new length through (`try_subst_proc_open` updates the readfree temp-file write size as `total`; `fake_proc_on_read` updates `regs[0]` = read return value after `write_tracee_mem`). For status, `fake_status_name` is now called **before** `fake_status_buf` so the Uid/Gid/Cap*/Groups length-preserving rewrites operate on the already-compacted buffer.
- **Upward swipes during scrollback are absorbed by scrollback instead of wheel events (0.8.116)**: 0.8.115 forwarded every upward swipe as wheel-down while `mouseEnabled`. But `TerminalSession.writeBytes` starts with `_scrollOffset.value = 0` ("reset scrollback so typing is always visible"), so even a single wheel byte sent while the user was reading old scrollback lines would **snap the view to the latest line in one jump** — the user report "downward scroll jumps straight to the bottom; once I scroll up I can only go back to the bottom". The fix adds `scrollOffset == 0` to the wheel-send gate in `onScroll`. While `scrollOffset > 0` the upward direction stays on the existing scrollback path (`scrollAccumDy` → `sess.scrollBy(-rowDelta)`), and the wheel path engages only after scrollback reaches the bottom. Fling follows the same gate (`mouseEnabled && velocityY < 0 && scrollOffset == 0` → no-op), so upward flings during scrollback now have inertial scroll again. `writeBytes` itself is left alone because key/typing paths legitimately need the reset.
- **Swipes split by direction; downward falls back to scrollback (0.8.115)**: 0.8.114 forwarded every direction as a wheel event, but many reader-style TUIs **deliberately ignore wheel-up (`evScrollUp`-style handler) and let the terminal scrollback handle "look back"**, so the previous behaviour left the upward swipe doing nothing. `onScroll` now calls `sendMouseWheelFromSwipe` only when `distanceY > 0` (finger going up = "advance") and sends wheel-down. When `distanceY < 0` (finger going down = "look back") it falls back to the existing scrollback path. `onFling` is no-op only on `velocityY < 0` (upward fling); downward flings still drive scrollback inertial scroll. `sendMouseWheelFromSwipe` is simplified to wheel-down only (fixed button, positive notches). [`MouseEncodeTest`](../../app/src/test/java/com/zerotoship/z2term/emulator/MouseEncodeTest.kt) is added to pin SGR/URXVT/LEGACY output (leading ESC, button, terminator) and DECSET `?1000`/`?1006` state transitions as regression guards.
- **Swipes turn into wheel events when mouse reporting is on (0.8.114)**: fixes "tap-scroll does not advance pages" in TUIs that opt into SGR mouse reporting. Even when the TUI requested mouse reporting via `?1000h` / `?1006h`, `TerminalInputView.onScroll` ignored that and always operated the scrollback (`scrollOffset`), so wheel notches never reached the TUI. The fix branches in `onScroll` on `emulator.mouseEnabled` and calls `sendMouseWheelFromSwipe`, which produces `encodeMouseEvent(button = 64/65)` and writes the bytes to the PTY via `sess.writeBytes()`. The scroll is quantized in 40px steps (`MOUSE_WHEEL_STEP_PX`) and the remainder carries over to the next event (same accumulator scheme as `scrollAccumDy`), so a long swipe produces `abs(dy) / stepPx` notches. `onFling` becomes a no-op while `mouseEnabled` is true so inertial scroll cannot accidentally page through content or drive the scrollback. Mouse click delivery (`sendMouseClick`) is unchanged. Behaviour on tabs where `mouseEnabled = false` is fully preserved.
- **z2root `/proc/<pid>/stat` field 2 also rewritten to the argv0 basename (0.8.113)**: 0.8.112 fixed `comm` and `status:Name`, but busybox/procps `ps` reads `/proc/<pid>/stat` in one shot for speed, and field 2 `(<comm>)` was still leaking the kernel-set `(libz2root.so)` (so `ps -ef` showed `{libz2root.so} <real argv>` with a stale label). A new `PROC_FD_STAT` kind covers `/proc/<pid>/stat` and `/proc/<pid>/task/<tid>/stat` (the global `/proc/stat` is excluded). `fake_stat_comm` rewrites the parenthesized field length-preservingly, using the last `") "` on the line as the right boundary (the comm may contain `(`/`)`).
- **z2root `/proc/<pid>/cmdline`, `comm`, and `status:Name` restored from the loader leak (0.8.112)**: due to Android's W^X, z2root has to `execve(libz2root.so)` and route through a loader wrapper (`z2root --loader-noreloc <ld.so> <ld.so> --argv0 <argv0> <prog> ...`), so the kernel records the wrapper argv into `/proc/<pid>/cmdline` and `libz2root.so` into `comm`/`status:Name`. As a result `ps -ef` / `pgrep <name>` / `pidof` / `top` break across the whole guest (proot escapes this because it relies on PT_INTERP through the rootfs `ld.so`, so the kernel records the original argv). **Fix**: at execve intercept time, the original argv (and basename of guest_prog) is recorded per-tracee. Two new PROC_FD kinds (`CMDLINE`/`COMM`) feed into the existing openat-time temp substitution path (readfree default). The `status:Name` line is rewritten length-preservingly to the argv0 basename via a new `fake_status_name` next to `fake_status_buf`. fork/clone inherits the recording from the parent and a successful execve overwrites it. The non-readfree (`Z2ROOT_NO_READFREE=1`) `fake_proc_on_read` covers the same kinds (with `regs[0]` adjustment because cmdline/comm can change length).
- **z2root `/proc/self/exe` rewritten to the guest view (0.8.111)**: the kernel's `/proc/<tid>/exe` symlink points at `libz2root.so` (or our own loader) because of how execve is staged, so guests that `readlink("/proc/self/exe")` got the host path and `open("/proc/self/exe")` failed with `ENOENT`. **Symptom**: Go's runtime can't open `/proc/self/exe` for libbacktrace during startup and panics immediately with `libbacktrace could not find executable to open` (both `go version` and `go build` fail to run). The same path breaks adb's `execl(own-path)` family and `--daemonize` self re-exec. proot hijacked these long ago — only z2root regressed. **Fix**: at execve(at) / bootstrap exec time we record the guest-side absolute program path per-tracee, and `host_path_for` substitutes it whenever a path resolves to `/proc/<own pid>/exe`; the `readlinkat` exit returns the same. fork/clone inherits the recording from the parent. Same approach as the earlier `/proc/self/cwd` reverse-translation (0.8.60, which fixed Claude Code's startup).

</details>

## 11. l2s constraint and native passthrough

> **Filed 2026-06-22; same day Phase 1 corrected the root cause from "rename(2) not POSIX-atomic" to "link2symlink × quarantine cleanup"**.

Z2Term exposes `/root` over **l2s (link2symlink overlay)**, inherited from proot's `--link2symlink`, which fakes hardlink semantics on Android's app FS where `link(2)` is forbidden. The real layout is a multi-step symlink chain such as `pack-<sha>.pack → .l2s.tmp_pack_XXXX → .l2s.tmp_pack_XXXX.0001` (chain end `.0001` holds the real data). The `-rw-` shown by `ls -la` and `[ -L ]` checks are not reliable indicators.

**Engine differences matter**: proot still creates new `.l2s` chains via link2symlink. z2root since 0.8.47 changed `linkat` to "try real link, copy on failure", so it **never creates `.l2s` chains anymore**. The symptom below is specific to proot receivers.

### 11.1 Symptom: git push to a proot-side bare repo always breaks

A `git push` to a bare repo hosted under proot fails with:

```
error: unpack should have generated <sha>, but I can't find it!
remote rejected master -> master (bad pack)
```

- **Software-agnostic**: Gitea / Forgejo / GitLab all call `git receive-pack` internally — same failure.
- **Protocol-agnostic**: SSH and HTTPS both break.
- **Settings cannot fix it**: `core.fsync=all` / `core.fsyncMethod=fsync` / `receive.unpackLimit=1` (which routes through unpack instead of index-pack but still goes through the same link path) are all ineffective.
- **z2root receivers do NOT break** (verified 2026-06-22).

### 11.2 Root cause: proot's `link()` emulation points into the quarantine

`git receive-pack` writes objects into a quarantine dir (`objects/tmp_objdir-incoming-*`), then on successful validation **migrates them out one by one via `link()`** into `objects/<aa>/<sha>`, and finally rmtrees the entire quarantine dir. Quarantine cannot be disabled.

Under `--link2symlink`, proot turns `link(src, dst)` into:

1. Place `src`'s content at `<dst dir>/.l2s.tmp_<name>_<rand>0001` (the chain end real file).
2. Make `dst` a **symlink pointing at the absolute path of that chain end**.

When this emulation runs during the migrate step, `objects/<aa>/<sha>` becomes **a symlink pointing into the quarantine dir's `.l2s.tmp_*`**. The follow-up quarantine rmtree erases the target, leaving `objects/<aa>/<sha>` **dangling**. The next read from receive-pack itself (the validation read-back) is when `unpack should have generated …, but I can't find it!` fires.

= **"rename(2) not POSIX-atomic" was a misdiagnosis**. The real combination is `link()`-via-link2symlink + quarantine cleanup.

### 11.3 What NOT to do

Do not bulk-delete `.l2s.tmp_*` as "garbage". **These are the actual data** (the chain end `.0001` is the real payload). The only safe sweep is `find -xtype l -delete` (delete only fully dangling symlinks).

### 11.4 Current operational workarounds

- **Switch the receiver to z2root engine** (fastest fix, verified this session). z2root's modern linkat never creates `.l2s` chains, so the failure mode is structurally impossible.
- **Keep the receiver outside l2s**: PC as canonical repo server (already adopted).
- **Pre-push hook that bypasses quarantine**: install objects directly via `pack-objects → index-pack → update-ref` (already in place).

### 11.5 Direction for a real fix

| Option | Approach | Assessment |
|---|---|---|
| **A** (old) | Make z2root `rename(2)` POSIX-atomic | **Shelved**. The root cause is in proot's `link()` emulation, not in rename, so this would not fix it. |
| **B** | Provide a native passthrough area (`/var/lib/native`) that bypasses l2s | Rejected by the user as "useless if it doesn't save `~/foo/.git`". |
| **C** | Toggle proot's `--link2symlink` off via a hidden setting (`ProotLauncher.kt` L306) | OFF mode leaks EACCES into dpkg/apt-style hardlink-dependent software (same as pre-0.8.47 z2root). Cheap to implement. |
| **D** | Fork proot prebuilt and patch link2symlink to skip emulation when the destination dir name matches `tmp_objdir-*`, honoring git's quarantine semantics | Localized fix that honors git's intent. Cost of maintaining a third-party prebuilt fork. |
| **Ops** | Document "receivers always run on z2root, proot tabs are clients only" in HANDBOOK | **Lowest cost**. The engine boundary cleanly separates working / broken behavior. |

### 11.6 Related

- The separate l2s problem where `.l2s` symlinks embed host absolute paths and go stale across Android OS major upgrades.
- The `z2root: ...` bullets scattered at the tail of §10 (0.8.43–0.8.118) cover individual syscall-translation bugs — a separate layer from this issue.

---

## 12. Glossary

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
