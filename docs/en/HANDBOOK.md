# Z2Term — Getting Started Handbook

This is a friendly guide for **first-time** Z2Term users.
The deeper technical details live separately in `docs/en/DESIGN-SPEC.md`.
(日本語版: `docs/ja/HANDBOOK.md`)

---

## 1. What is Z2Term?

**A terminal app that runs real Linux inside your Android phone.**

- Think of the "black screen (terminal)" from a PC, now living on your phone.
- Inside, a Linux distro such as **Alpine / Ubuntu / Arch / Kali** is running.
- **No rooting (no modifying your device) required.** Just install it like any normal app.
- You can install software with `apt` or `pacman`, and use `git`, `vim`, `python`, and more.

> What it can't do: because of how phones work, `ping` and some low-level network features are unavailable
> (you can work around them with alternatives like `nmap -sT`).

---

## 2. Installing

1. Put the APK file (`app-foss-debug.apk`) on your phone.
2. Allow "Install from unknown sources" and install it.
3. Open the app.

On first launch, **Alpine Linux is set up automatically** (wait a moment and a `#` prompt appears).
That's all the setup you need.

> ℹ️ The `foss` build (for F-Droid / sideload) does not bundle Alpine itself, so it **needs a network connection on the first launch only** (it auto-downloads from the official site). The `full` build bundles it, so it starts instantly even offline.

---

## 3. Reading the screen

```
┌───────────────────────────────────────────────┐
│ hostname [mode]   📋 📜 🔅 🔒 🔍 ⌨ ⏺ ⚙     │ ← top bar (toolbar)
├───────────────────────────────────────────────┤
│ [ archlinux ] [ + ] [ 🖥 ]                      │ ← tabs (switch between terminals / GUIs)
├───────────────────────────────────────────────┤
│                                                │
│        the terminal itself (where text shows)  │
│                                                │
├───────────────────────────────────────────────┤
│ ▾  (tap to open/close; drags over 24dp do nothing) │
├───────────────────────────────────────────────┤
│            keyboard                            │
└───────────────────────────────────────────────┘
```

**Toolbar buttons (left to right)**

| Button | What it does |
|---|---|
| 📋 | Paste text from the clipboard (**double-tap to open the clipboard history** and pick from it) |
| 📜 | Command list (tap a frequently used command to type it) + **SSH connect / SFTP** + **Servers** (manage resident servers) (switch with tabs) |
| 🔅 | Screen-on lock (when ON, the screen won't auto-dim; the icon changes to 💡 while ON) |
| 🔒 | Background keep-alive (while ON, the terminal keeps running even if you close the screen; 🔒 = ON, 🔓 = OFF) |
| 🔍 | Search the on-screen text (jump back/forward with ↑↓; **tap in the input field to move the caret** and fix a typo in the middle) |
| ⌨ | Switch between the phone's standard keyboard ⇄ the in-app keyboard |
| ⏺ | Record a terminal log (tap to start, tap again to stop; **lit while recording**; **double-tap for the details**) |
| ⚙ | Settings (**always the rightmost**; it never moves when you reorder, and cannot be removed) |

> **Reorder the buttons**: **long-press** a toolbar button to **drag it left/right and reorder**. A short description pops up above the button while held. The order is remembered.

> **You can remove buttons you don't use**: ⚙ Settings → "Display" → **Toolbar** lets you tap to choose which buttons appear. The lit ones are shown. The order of a removed button is remembered, so bringing it back puts it where it was.
> ⚙ Settings is **always at the far right** — it never moves and cannot be removed.
> If you remove 🔅 screen-on lock or 🔒 background keep-alive, there would be nowhere left to toggle them, so **a switch appears in that same "Toolbar" section**.

---

## 4. Using the keyboard

Z2Term comes with its **own in-app keyboard**.

### Latin (ASCII) keyboard
- Tap letter keys normally.
- **⇧ (shift)**: tap once = next single letter uppercase / tap again = always uppercase / tap again = release.
- **Flick down (swipe a key downward) = uppercase.** e.g. flick `q` down to get `Q`.
- **Flick up / left / right = symbols** (the small green characters are the hints).
- **Long-press to repeat**: letters, numbers, arrows, space, and `⏎` (return) repeat while held. `⌫` (delete) also repeats on long-press.
- Press `CTRL` or `ALT` then a letter → shortcuts like Ctrl+C. `ALT` (and `META` on the English layout, which does the same thing) **prefixes the next key with ESC**, which drives bash/zsh line editing: `ALT`+`.` inserts the last argument of the previous command, `ALT`+`b`/`f` moves the cursor by words, `ALT`+`d` deletes the word to the right. `ALT`+arrow keys are sent with the same ESC prefix.
- **The key background turns bright green when pressed** (so you can see what you touched).
- During a flick, once your finger passes the threshold, **the small hint character for the destination is enlarged and highlighted**, so you can confirm where it will land before lifting your finger.

### Landscape keyboard
- In settings, **"Keyboard position (landscape)"** lets you choose **left / bottom / right** (in portrait it's always at the bottom).
- The **"Side keyboard width (landscape)"** slider adjusts the width when docked to a side, and the **"Keyboard height (landscape)"** slider adjusts the overall height. Bigger keys are easier to press; smaller keys give you more screen — your trade-off.

### Japanese / kana-kanji conversion
- Press the **"あ" key** on the left side of the keyboard to switch to the built-in **Japanese flick keyboard**.
- Flick rules: **tap = あ / left = い / up = う / right = え / down = お** (same as the common 12-key phone layout).
- **Cursor keys ◀ ▶ ▼ ▲**: just **below** ◀ ▶ are **▼** (left = down) and **▲** (right = up) — all four ◀▶▼▲ are the same size. When you're not typing, they move the terminal cursor (walk command history, move within the line). **While typing Japanese, ◀ ▶ move the composing cursor** and can reach the line start.
- **The "小゛゜" key** changes the previous character like `か→が→か`, `は→ば→ぱ→は`, `つ→づ→っ→つ`.
- While typing (unconfirmed), a **candidate bar** appears at the top so you can convert to kanji.
  - **The left end of the candidate bar shows your whole raw kana as typed.** Everything before the cursor (the thin bar) is the "leading chunk" being converted — drawn strong, the rest dim.
  - With the **"変換" (convert) key**, convert the leading chunk (press repeatedly to cycle candidates). Tap a candidate or press ⏎ to confirm, and it advances to the rest.
  - Right after typing a **long sentence** the cursor sits at the **end** (the whole thing is the leading chunk). **Move ◀ to shrink the cursor leftward and that prefix becomes the conversion target**, changing the candidates (particles and endings like "です・ました" stay in kana).
  - The **light-green chunk** in the candidate bar is the "whole-sentence" conversion (e.g. 明日の天気はいかがでしょうか). Tap it to confirm the entire sentence at once. Moving the cursor with ◀ ▶ rebuilds it to match.
  - **Fix a typo in the middle**: move the cursor (the thin bar) to the spot with ◀ ▶, and you can **insert kana there or delete the char before it with ⌫** (the "小゛゜" key targets that position too). The cursor can reach the line start.
  - **Predictive conversion from what you've typed**: as you start typing a reading, previously confirmed phrases whose reading begins with it appear at the head of the candidate bar. For example, typing お surfaces phrases you confirmed before such as お願いします / 概ね as predictions you can tap directly. When you pick a prediction it is learned under its actual reading, so it keeps showing up under the same reading next time.
  - **Chunk boundaries are learned too**: e.g. if こまんど first splits into 「こ」「まんど」, merge it into 「こまんど」 with ◀ ▶ and confirm — from the next time it's auto-recognized as a single chunk (コマンド). The more often you use a reading-chunk, the higher its priority.
  - For **katakana**, tap the katakana candidate in the candidate bar.
- The "ABC" key returns to the Latin keyboard.
- **Flicking the ⌫ (delete) key**: flick left to **delete the previous word at once**, flick right to **delete the entire line being typed**.
- Note: this is a simple dictionary-based conversion, so it isn't as smart as Gboard — but words you use are learned and start appearing near the top.

### When you want the phone's standard keyboard
- Tap the **"あ" button in the toolbar** to switch to your usual phone keyboard (Gboard, etc.).

---

## 5. Common operations

| What you want | How |
|---|---|
| Copy text | **Long-press** the screen → drag with your finger to select → "Copy" button (trailing blanks are trimmed and each row gets a **line break**) |
| Magnify while selecting | While selecting, a **magnifier** appears above your finger |
| Select beyond the screen | While selecting, move your **finger to the top/bottom edge** → it auto-scrolls so you can keep selecting |
| Paste | The **📋** button in the toolbar. **Double-tap** it to open the **clipboard history** and pick a past copy to paste (pasting never rewrites the system clipboard, so it won't "copy what you just pasted") |
| Use text copied in another app | Just come back to this app — **the clipboard content at that moment is added to the history** (pick it from the 📋 double-tap). Android only lets an app read the clipboard **while it is in the foreground**, so copies made while this app was in the background are picked up as a single entry when you return |
| Scroll up/down | Drag with one finger. You can also **grab the scrollbar on the right edge** (it follows your finger from the moment you touch it). Use **↓** at the bottom-right to return to the latest |
| Make text bigger/smaller | **Pinch** with two fingers (spread/squeeze) |
| Add a terminal | The tab **+** (terminal) / **🖥** (GUI desktop) |
| Remove a terminal | **Double-tap** that tab (the last remaining one won't be removed). **If something is running in that tab, a confirmation dialog appears** (to prevent accidental removal while you are working). If nothing is running it closes right away |
| Reorder tabs | **Long-press** a tab then **drag** left/right (you can move it edge to edge in one gesture) |
| See tab info | **Long-press** a tab to pop up its name and the **engine it's running on** (PRoot / z2root / chroot / Android sh, or GUI for GUI tabs) — no need to open Settings; it shares the same long-press as reorder |
| Check the app version | Type **`z2version`** in the terminal to print the running app's version, execution engine, the **running OS (distro) and kernel**, etc. (`z2version --short` for just the version) |
| Past commands | The **↑ key** (history persists even after restarting the app) |
| Record the terminal | Tap **⏺** in the toolbar once to start (the button lights up), tap again to stop. The file lands in `~/z2term-log/`, so `less ~/z2term-log/<name>` reads it directly. **Double-tap** to change the destination, file name, date format, and so on |
| Send text or a file from another app | In that app choose **Share** → pick **Z2Term**. Text arrives as-is; a file is taken into `~/z2term-inbox/` and **its path** is placed on the input line. It is **only inserted, never run**, so finish the command yourself and press ⏎ |

> When you launch the app it **always opens a single terminal tab** (previously open tabs are not auto-restored).

> **About the terminal log (⏺)**
> - Recording is **per tab**, and always returns to off when you reopen the app (so nothing keeps recording by accident).
> - What you get is an **ordinary text file**. Colors and screen-control codes are stripped, and progress output that rewrites one line (`50% → 75% → 100%`) leaves **only its final state** as a single line.
> - **Whatever appears on screen goes in as-is.** Keys, tokens and one-time codes that were displayed are recorded too (a password you only type never appears on screen, so it is not recorded). The button always stays lit while recording, so you can see it at a glance. A log you no longer want is a normal file — delete it with `rm`.
> - `~/z2term-log/` is **visible to other apps** (it is treated like the rest of your home). As with any other file under home, don't keep there what you don't want seen.
> - Full-screen apps (the ones that paint by redrawing the screen) are not recorded by default, because flattening them does not produce readable text.

---

## 6. Installing software (packages)

It depends on which Linux is running inside.

| Distro | Example command |
|---|---|
| Alpine | `apk add git vim` |
| Ubuntu / Kali | `apt update && apt install git vim` |
| Arch | `pacman -Sy git vim` |

You can switch distros in **⚙ Settings → Distro** (the first time triggers a download).

---

## 6.5. Running `claude` (Claude Code)

The FOSS build runs inside a custom ptrace-based engine (z2root). The `claude` distribution
comes in a **musl** flavor and a **glibc** flavor, and the musl one cannot start under z2root
(musl's ld.so cannot launch a non-PIE executable). So install and use the **glibc** flavor.

Run the following **in a glibc-based distro tab (e.g. Arch)** — it will not install on a
musl-only distro such as Alpine.

```sh
# Remove the existing (possibly musl) binary so re-install re-detects the flavor
rm -f ~/.claude/downloads/claude

# Official installer (on Arch it picks the glibc build automatically)
curl -fsSL https://claude.ai/install.sh -o /tmp/ci.sh && bash /tmp/ci.sh

# Put it on PATH (it installs into ~/.local/bin)
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc

# Pin it so the auto-updater doesn't swap in a musl build
echo 'export DISABLE_AUTOUPDATER=1' >> ~/.bashrc
```

Reopen the tab (or `source ~/.bashrc`), then just run `claude`.

> Notes
> - This setup is needed **once per tab (distro)** only. After that just run `claude`.
> - If a current version is already installed the installer **skips the download**, so when you
>   want to swap the flavor, run the `rm -f` above first.
> - The actual files live in `~/.local/share/claude/versions/<version>`, and `~/.local/bin/claude`
>   is a shortcut (symlink) to them.

---

## 7. Connecting to Z2Term from a PC (SSH server)

You can turn your phone into an SSH server and log in from a PC.

1. Run `passwd` in the terminal to **set the root password** (first time only).
2. Type **`sshd`** in the terminal. → The SSH server (dropbear) starts.
   - The Settings (⚙) → "Start sshd" button does the same thing.
3. Connect from the PC to the IP shown:
   ```
   ssh -p 2222 root@<phone's IP>
   ```

**Changing the port**
- Edit `Port` in `/etc/ssh/sshd_config`, and `sshd` will start on that port.
- For a temporary change, you can also specify it like `sshd -p 50000`.
- Note: the port number must be **1024 or higher** (lower ports are blocked by phone restrictions).

**Keeping it running**
- Register `sshd` (or `sshd --lan` to expose it to the LAN) under ⚙ Settings → "Resident servers" and it keeps running without opening the app.
- When started as a resident server it automatically runs in **foreground (stay-alive) mode** even without `-D` (0.8.165; before that it was restarted every few seconds, so connections were refused or dropped shortly after connecting).

> Tip: `/usr/sbin/sshd` (OpenSSH) does not work with this app's mechanism. **Always type `sshd`**
> (a lightweight dropbear runs underneath).

---

## 7.5. Using `adb` without a PC (`z2adb`)

You can run `adb` against this phone itself — no PC, no USB. `z2adb` helps with this.

1. **Enable Wireless debugging**: Settings → Developer options → turn on "Wireless debugging" (Android 11+).
   - If Developer options is hidden, tap "Settings → About phone → Build number" 7 times to reveal it.
2. In the terminal run **`z2adb setup`** to install `adb` (auto-detected for your distro).
3. Open **"Pair device with pairing code"** under Wireless debugging, and use the shown
   **port** and **6-digit code**:
   ```
   z2adb pair <port> <code>
   ```
4. Use the **connect port** shown directly under the "Wireless debugging" screen:
   ```
   z2adb connect <port>
   ```
   The first time, an "Allow this device?" prompt appears on screen — **allow** it.
5. Once connected, it's just regular `adb`:
   ```
   z2adb shell        # shell into your own phone
   z2adb logcat       # view logs
   z2adb status       # list connected devices (adb devices -l)
   ```

> Tip: the ports change every time. If you type `z2adb pair` / `z2adb connect` with **just a number**,
> you'll be asked for the port on the spot. Anything you'd write as `adb …` works as `z2adb …` too —
> `z2adb` passes it straight through to `adb` (`z2adb shell` ≈ `adb shell`).

---

## 8. Exchanging files

### Viewing inside Z2Term from another app (file manager)
- The same place as the terminal's `~` (home) is visible from the phone's "Files" app as the **Z2Term home**.
- Files you create in the terminal are visible from the file manager, and vice versa.

### From the terminal to the phone's shared storage
- In the terminal, **`cd /sdcard`** moves you to the phone's shared storage (photos, downloads, etc.).
- The first time needs permission: **⚙ Settings → "Allow all storage"** button → turn it ON on the permission screen.
- A dedicated folder usable without permission is **`/storage/app`**.

---

## 9. What you can do in Settings (⚙)

Pressing ⚙ opens the **settings page (full screen)**. Go back with the **←** at the top-left or your phone's back button.

Settings are split into **8 groups** (Display / Keyboard / Input and language / Linux environment / Background and automation / Maintenance / Developer / About this app), and **tapping a heading opens or closes it**. The open/closed state is **remembered even after you close the app**, so you can keep the groups you use often expanded.

| Setting | Description |
|---|---|
| Theme | Color scheme (9 options) |
| Font | Display typeface (4 options, with preview) |
| Font size | 4–32 (also changeable by pinching) |
| Scrollback lines | How many lines you can scroll back through |
| Distro | Alpine / Ubuntu / Arch / Kali |
| Login shell | zsh / bash / sh — **the same shell is used for the terminal tab, SSH logins and the GUI's inner terminal** (the distro's `/etc/passwd` login shell is updated too). If the chosen shell is not installed in that distro, the default shell is used as before |
| Keyboard style | Simple / 4-direction flick |
| Keyboard position (landscape) | Left / bottom / right — effective only in landscape |
| Side keyboard width (landscape) | Slider 280–700 dp |
| Keyboard height (landscape / portrait) | Slider 200–500 dp (remembered separately per orientation) |
| GUI audio | Play sound (video, etc.) in the GUI (desktop) — only when ON |
| Language / 言語 | Japanese / English (switches instantly) |
| Disable install timeout | Wait for OS / GUI downloads to finish completely |
| Confirm before downloading | Show a confirmation dialog before fetching a distro / GUI |
| SSH connection helper | Steps for connecting from a PC, with the IP shown |
| Storage access | Permission to use `/sdcard` |
| Reset terminal | Returns the app to **the state it had when first opened**. Only one terminal tab is left (other terminal tabs and GUI tabs are closed), and that terminal goes back to its initial state: running programs are terminated, screen and scrollback cleared. Tapping it opens a confirmation. **Saved servers, settings, snippets and the OS itself (installed packages and files you made) are not removed** |
| Clear cache | Sweeps the package/build caches that pile up inside the OS (pacman, apt, apk, `~/.cache`, …) plus the app's temp files. Tapping it opens a confirmation that **itemizes what and how much** will be deleted. Installed packages, settings and files you made are not removed |
| Resident servers | Register any server (sshd / http / smb, …) as a **start command** and keep it running in the background. Turn on "auto-start on boot" and it **launches right after the device boots — without opening the app**. Stop them all from the "Stop servers" notification action or this screen. See below |
| Notification detection | Grant the OS "notification access" and turn it on, and incoming notifications are appended to `~/.z2term/notifications.jsonl` (a generic hook). **The output format is fully customizable** (a template of `{time}` `{app}` `{title}` `{text}` … ; presets: readable / one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file instead of appending at the end. That mode reads and rewrites the whole file per entry, so **once the log passes 10MB the settings screen shows a warning** (turn it off before it gets slow, or trim the file from the terminal). What you record / filter / serve is **up to you on the terminal side** (e.g. `tail -f`, or serve it with a resident server). Since the side new entries arrive on changes with the append direction, **the "command to read it" shown in settings follows that setting** (`tail -f` when newest is at the bottom, `watch -n 1 head -n 20 …` when newest is at the top). Turn **"Save notification log"** off to keep detecting without writing anything to the file (detection only). A notification that is re-posted many times is **logged only once**. Default off, fully local |
| SMS detection | Turn it on and **grant the SMS permission**, and incoming SMS are appended to `~/.z2term/sms.jsonl` (fields: `time` `from` `body`; format customizable). **Vs. notification detection**: Android 15+ **redacts OTP-bearing notifications** before handing them to ordinary apps, so SMS OTPs may not be readable via notifications (same for MacroDroid etc.). SMS detection reads the **SMS body directly**, bypassing the redaction, and works **even while locked**. For auto-copy, register `z2-macro install otp-sms.sh` as a resident server. Non-SMS OTPs (e.g. authenticator-app notifications) are out of scope. Default off, fully local |
| System event detection | Turn it on and screen on/off, unlock, charge start/stop, battery low/okay, Wi-Fi connect/disconnect and **Bluetooth earbuds connect/disconnect** are appended to `~/.z2term/events.jsonl` (a generic automation trigger; sibling of notification detection). **The output format is customizable** (`{time}` `{event}` `{level}` `{ssid}`; presets: one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file (in that mode, **once the log passes 10MB the settings screen shows a warning**). Build automations like "when battery drops below 20%…" or "when charging starts…" **terminal-side** (e.g. a script reading `tail -f ~/.z2term/events.jsonl`; **the "command to read it" shown in settings follows the append direction**). Default off, fully local, shows an ongoing notification while active (Wi-Fi SSID is blank without location permission). **The log has no size cap** (it keeps appending into one file; clean it up from the terminal, e.g. `: > ~/.z2term/events.jsonl`) |
| Unlock-failure detection | With this on **plus device admin activated**, lock-screen unlock **failures/successes** are recorded to `~/.z2term/events.jsonl` as `unlock_failed` (`level` = consecutive failure count) / `unlock_succeeded`. It's the **detection hook for anti-theft macros** like "after N wrong passwords, notify / record location / sound an alarm". No photo or upload is built in — you build the reaction as a macro. Device admin is used only to watch failure counts (`watch-login`); it **does not lock or wipe** your device remotely. Default off, fully local |
| Reset settings | Returns **every setting to its defaults** — theme, font, keyboard, execution engine (back to the default **z2root**), saved servers, unlocked hidden features and so on. Tapping it opens a confirmation (cannot be undone). **The OS itself (installed packages and files you made) is not removed** |

### Resident servers (run without opening the app)

Open ⚙ Settings → **Resident servers** → "Manage servers", or the **Servers** tab of the toolbar's 📜, to keep any server running (both show the same screen).

1. Tap "**+ New**". Picking a preset (SSH / HTTP / SMB / FTP / VNC) fills in a start command (edit it freely).
2. Install the server itself (`sshd` / `smbd`, …) into that OS beforehand from the terminal — the app does not bundle them.
3. Tap "**Start**" to launch all servers now. Each row shows its state (`running`, …) and is **auto-restarted** if it exits.
   - **You can add, edit and delete servers while they stay resident** (applied within a few seconds). Previously, running something you had just added meant restarting everything — taking the other servers down with it.
   - The **▤ button** on a row shows what that server printed (its log). When something won't work, the reason is usually there. "Clear log" empties it at any time.
   - If a server keeps dying and restarting, the row shows "**restarted N× / last exit code**". A number that keeps climbing means it fails to start every time — open ▤ and read the log.
4. Turn on "**Auto-start on boot**" and the servers **come up automatically after the device boots, without opening the app**.
5. To stop, use the "**Stop servers**" notification action or "**Stop**" on this screen (stops them all at once).

Note: ports below 1024 (e.g. 80) cannot be opened without root — use a high port (e.g. 8080).
Note: excluding the app from battery optimization makes it less likely to be killed in the background (link inside ⚙ Settings).
Note: if battery use bothers you, turn on "**Low-power mode**" — it lets the device sleep deeply while the screen is off to save battery, but incoming connections may be delayed or dropped during that time (battery over reachability).

---

## 10. Troubleshooting (FAQ)

**Q. `sshd` won't work / the port is wrong**
→ Type `sshd` (not the full path `/usr/sbin/sshd`). Set the port via `Port` in `/etc/ssh/sshd_config`, or `sshd -p <number>`. Use 1024 or higher.

**Q. I can't type Japanese**
→ Switch to Japanese flick with the "あ" key on the left of the keyboard. Typing kana shows the candidate bar, so you can convert to kanji with the "変換" key or by tapping a candidate (long sentences are predicted automatically per chunk). If you want smarter conversion, switch to the phone's standard keyboard (Gboard, etc.) with the "⌨" toolbar button.

**Q. I can't select text well / can't select to the edge**
→ Long-press first, then drag with your finger. To reach the edge, move your finger toward the top/bottom edge of the screen and it auto-scrolls. Drag near the end of the selection to change its range.

**Q. The terminal stops when I close the app**
→ Turn ON the **🔒 (background keep-alive)** button on the toolbar (🔒 = ON).

**Q. I can't see anything with `cd /sdcard`**
→ Turn ON the permission with "Allow all storage" in ⚙ Settings.

**Q. `ping` doesn't work**
→ It's not possible due to how phones work. Use `curl` for connectivity checks, and `nmap -sT` for port scans.

**Q. A GUI app prints only `segmentation fault` and won't start**
→ **Fixed in 0.8.177.** Update the app, then close and reopen that OS's tab. The cause was that the place for shared memory (`/dev/shm`) is not provided on phones by default, so GUI apps built around it (mail clients, browsers, and the like) shut themselves down partway through startup. No reason is printed, which makes it look like a plain crash. If it still happens after updating, check that it exists with `ls -d /dev/shm` and let the developer know.

**Q. Only the message/page area of a GUI app is blank, or a child process keeps dying**
→ **Fixed in 0.8.179.** Update the app, then close and reopen that OS's tab.

This used to happen with apps built on a web rendering engine (Gecko-based). The app's own frame and settings screens drew fine, but **the child process that renders content** died, leaving the content pane blank (`unable to find a usable font`). It was an interaction between that engine's own sandbox and z2term, and **the fix keeps the app's defenses intact**.

If you still see it after updating, you can work around it by turning that sandbox off when launching the app:

```sh
MOZ_DISABLE_CONTENT_SANDBOX=1 <app>
```

Note that this **removes one layer of the app's own defenses**, so weigh that risk when opening HTML mail from untrusted senders or unfamiliar sites. It is meant as a stopgap rather than a permanent setting — please report the symptom if you need it.

**Q. GUI drawing feels sluggish**
→ Phone kernels have no SysV shared memory (`shmget`), so X11's fast drawing path (MIT-SHM) is unavailable. That is a platform limitation and cannot be fixed here. Most apps switch to another method automatically, so the result is "works, just a bit slower". If some app renders incorrectly, try turning MIT-SHM (shared memory) off in that app's own settings.

---

## 11. Z2Term's own commands (quick reference, `z2*`)

These are "Z2Term-only" commands that Z2Term automatically installs into every distro. Just type them in the terminal (they're on your PATH, so the location doesn't matter). They are rewritten to the latest version every time the app launches.

### Version / info
| Command | What it does |
|---|---|
| `z2version` | Shows the running app's version, execution engine, OS (distro) and kernel. `z2version --short` prints just the version on one line |

### Call phone features
| Command | What it does |
|---|---|
| `z2-notify [-h] [-n name] [-b label]... "title" "text"` | Post a notification (text optional; `-h` shows a pop-up banner). **`-b` adds reply buttons** (up to 3); pressing one appends a `notify_action` line to `events.jsonl` so a macro can read the answer |
| `z2-toast "message"` | Toast (short message at the bottom of the screen) |
| `z2-share "text"` | Hand text to Android's share sheet |
| `z2-open <URL or path>` | Open a URL or file in the default app |
| `z2-clip get` / `z2-clip set [text]` | Get / set the clipboard (set reads stdin if no argument) |
| `z2-battery` | Show battery level / charging state (JSON) |
| `z2-vibrate [ms]` | Vibrate (default 200ms) |
| `z2-say <text>` | Speak text via the device's text-to-speech (reads stdin if no argument) |
| `z2-torch [on\|off\|toggle]` | Turn the flashlight on/off/toggle (default toggle; prints the resulting state) |
| `z2-media [playpause\|play\|pause\|next\|previous\|stop]` | Control media playback (default playpause) |
| `z2-volume <up\|down\|mute\|unmute\|N\|N%>` | Adjust media volume (prints the resulting current/max) |
| `z2-sensor [light\|accel\|proximity]` | Read a sensor once as JSON (light/accelerometer/proximity; default light) |
| `z2-state [key]` | **Current device state** as JSON; with a key, just that value (`screen` `locked` `idle` `charging` `plug` `level` `temp` `wifi` `ssid` `ringer` `airplane` `headset` `bt_audio` `volume` `volume_max`). E.g. `[ "$(z2-state charging)" = "true" ]` |
| `z2-alarm at\|daily HH:MM [name]` | **Time trigger**: writes an `alarm` event into `events.jsonl` at that time (`in 5m` / `list` / `cancel <id\|name\|all>` too). Unlike cron it fires during Doze (may be a few minutes late) |
| `z2-macro list\|install <name>` | **Bundled macro samples** into `~/.z2term/macros/` (`show` / `run` / `dir` too) — a starting point for your first macro |
| `z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] …` | Fire an arbitrary Android Intent (launch apps, open settings, set alarms, … see `docs/en/MACRO-GUIDE.md`) |

> Combine "trigger (event detection) → decide (shell) → action (z2-*)" to automate your phone (macros). See **`docs/en/MACRO-GUIDE.md`** for how — you can also feed it to an AI and have it generate the macro for you.

### Graphical (GUI) apps
| Command | What it does |
|---|---|
| `z2gui start [WxH]` / `stop` / `status` | Start / stop / status of the Linux desktop (e.g. `z2gui start 1280x720`) |
| `z2run <GUI app>` | Launching a GUI app also opens the GUI tab automatically |

### Connecting
| Command | What it does |
|---|---|
| `z2adb …` | `adb` to this phone itself (no PC) → see **§7.5** |
| `sshd` | Start an SSH server → see **§7** (defaults to "this device only" + key auth only) |

### Security (vulnerability testing)
| Command | What it does |
|---|---|
| `z2scan self` | Self-check this device/localhost (open ports, sshd config, SSH key perms, world-writable/SUID, PATH). No external tools |
| `z2scan setup` | Install scanners (`nmap`/`lynis`) from your distro's official packages |
| `z2scan net [--allow-remote] [target]` | `nmap` TCP scan. Target defaults to `127.0.0.1`. A non-local target requires `--allow-remote` + a warning |
| `z2scan host` | Host audit via `lynis` (falls back to `self` if absent) |
| `z2scan cve` | Known-CVE scan of the rootfs via `trivy`/`grype` if present |

> Note: results stay local (nothing is sent out). **Only scan systems you are explicitly authorized to test.**

### Help
| Command | What it does |
|---|---|
| `z2help` | Prints this `z2*` quick reference in the terminal (with the app version at the top) |
| `z2term` | For now an alias of `z2help` (a reserved command that prints the same list). The name is reserved so `z2term` can be repurposed later |

> Note: the execution engine (proot / z2root / chroot) is shown on the `engine:` line of `z2version`.

---

## 12. Friendly glossary

| Term | Meaning |
|---|---|
| Terminal | A screen where you operate a computer by typing text commands |
| Linux distro | A flavor of Linux (Alpine / Ubuntu, etc.) |
| Shell | The program that accepts commands (zsh / bash) |
| Package | Software you add on (git / vim, etc.) |
| SSH | A mechanism for connecting securely to another computer |
| Flick | An input gesture that "swipes" a key up/down/left/right |
| Home (~) | Your working folder |

---

If you have trouble or "I wish it worked like this" feedback, please tell the developer.
If you want to know the deeper internals, see `docs/en/DESIGN-SPEC.md`.
