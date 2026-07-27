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
The first time, **three small cards** appear above the terminal. Tapping one only **puts a command on the input line**, so press the return key to run it (post a notification / turn on the flashlight / let a PC connect). Tap all three, or press the close button, and they never come back. To see them again: Settings > Maintenance > "Show the intro again".
That's all the setup you need.

> ℹ️ The `foss` build (for F-Droid / sideload) does not bundle Alpine itself, so it **needs a network connection on the first launch only** (it auto-downloads from the official site). The `full` build bundles it, so it starts instantly even offline.

---

## 3. Reading the screen

```
┌───────────────────────────────────────────────┐
│ hostname [mode]   📋 📜 🔅 🔒 🔍 ⌨ 🔴 ⚙     │ ← top bar (toolbar)
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
| 📜 | Command list (tap a frequently used command to type it) + **History** (filter past commands and insert one) + **SSH connect / SFTP** + **Servers** (manage resident servers) + **Automation** (list `z2-when` rules, toggle them, read run logs, pause) (switch with tabs) |
| 🔅 | Screen-on lock (when ON, the screen won't auto-dim; the icon changes to 💡 while ON). **Double-tap for a slider that dims this app only** (for dark rooms; going home restores it, and Reset clears it any time). **The level you pick is remembered, so the app opens at that brightness next time** (press Reset to go back to normal) |
| 🔒 | Background keep-alive (while ON, the terminal keeps running even if you close the screen; 🔒 = ON, 🔓 = OFF). **While resident servers are running, 🔒 is dimmed and can't be toggled** (the servers already keep the app alive, so turning it OFF here would do nothing). Tapping 🔒 in that state opens a screen to choose **"End session only" / "Stop everything and quit"** (see below) |
| 🔍 | Search the on-screen text (jump back/forward with ↑↓; **while searching, the scrollbar shows a tick for every hit** so you can see where they cluster — tap a tick to jump there; **tap in the input field to move the caret** and fix a typo in the middle) |
| ⌨ | Switch between the phone's standard keyboard ⇄ the in-app keyboard. Even with the phone's keyboard, **the text being composed (before you confirm) shows inline at the terminal cursor** |
| 🔴 / ⚪ | Record a terminal log (tap to start, tap again to stop; **🔴 while recording, ⚪ when idle**; **double-tap for the details**) |
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
- With the phone's keyboard, **the text you're composing (before you confirm) now appears inline at the terminal cursor**. You can see the in-progress state of Japanese conversion or predictive input, instead of characters only showing up after you confirm.

---

## 5. Common operations

| What you want | How |
|---|---|
| Copy text | **Long-press** the screen → drag with your finger to select → "Copy" button (trailing blanks are trimmed and each row gets a **line break**) |
| Magnify while selecting | While selecting, a **magnifier** appears above your finger |
| Select beyond the screen | While selecting, move your **finger to the top/bottom edge** → it auto-scrolls so you can keep selecting |
| Paste | The **📋** button in the toolbar. **When the text has line breaks**, a bar shows "3 lines …" first so you can look before pressing Paste (a single line still goes straight in). **Double-tap** it to open the **clipboard history** and pick a past copy to paste (pasting never rewrites the system clipboard, so it won't "copy what you just pasted"). **Picks from the history that contain line breaks get the same confirmation bar** (0.8.250) |
| Use text copied in another app | Just come back to this app — **the clipboard content at that moment is added to the history** (pick it from the 📋 double-tap). Android only lets an app read the clipboard **while it is in the foreground**, so copies made while this app was in the background are picked up as a single entry when you return |
| Scroll up/down | Drag with one finger. You can also **grab the scrollbar on the right edge** (it follows your finger from the moment you touch it). Use **↓** at the bottom-right to return to the latest |
| Make text bigger/smaller | **Pinch** with two fingers (spread/squeeze) |
| Add a terminal | The tab **+** (terminal) / **🖥** (GUI desktop) |
| Remove a terminal | **Double-tap** that tab (the last remaining one won't be removed). **If something is running in that tab, a confirmation dialog appears** (to prevent accidental removal while you are working). If nothing is running it closes right away |
| Reorder tabs | **Long-press** a tab then **drag** left/right (you can move it edge to edge in one gesture) |
| Tell which tabs are working | An inactive tab gets **a small dot** next to its name while something runs in it, and a **`✓`** if it finished while you were elsewhere (it clears when you open that tab). The tab you're on never gets a mark |
| See tab info | **Long-press** a tab to pop up its name and the **engine it's running on** (PRoot / z2root / chroot / Android sh, or GUI for GUI tabs) — no need to open Settings; it shares the same long-press as reorder |
| Check the app version | Type **`z2version`** in the terminal to print the running app's version, execution engine, the **running OS (distro) and kernel**, etc. (`z2version --short` for just the version) |
| Past commands | The **↑ key** (history persists even after restarting the app) |
| Record the terminal | Tap **⚪** in the toolbar once to start (the button lights up), tap again to stop. The file lands in `~/z2term-log/`, so `less ~/z2term-log/<name>` reads it directly. **Double-tap** to change the destination, file name, date format, and so on |
| Send text or a file from another app | In that app choose **Share** → pick **Z2Term**. Text arrives as-is; a file is taken into `~/z2term-inbox/` and **its path** is placed on the input line. It is **only inserted, never run**, so finish the command yourself and press ⏎ |

> When you launch the app it **always opens a single terminal tab** (previously open tabs are not auto-restored).

> **About the terminal log (⚪)**
> - Recording is **per tab**, and always returns to off when you reopen the app (so nothing keeps recording by accident). **Turn on "Record new tabs automatically"** and every tab you open is recorded from the start, so there is no ⚪ left unpressed (that setting is remembered and applies to tabs opened from then on).
> - What you get is an **ordinary text file**. Colors and screen-control codes are stripped, and progress output that rewrites one line (`50% → 75% → 100%`) leaves **only its final state** as a single line.
> - **Whatever appears on screen goes in as-is.** Keys, tokens and one-time codes that were displayed are recorded too (a password you only type never appears on screen, so it is not recorded). The button always stays lit while recording, so you can see it at a glance. A log you no longer want is a normal file — delete it with `rm`.
> - **"Timestamp every line"** (off by default) prefixes each line with a fixed-width `[2026-07-27 08:42:13] `, so you can trace when things happened. ⚠ It is **not applied to raw logs**, which must stay byte-for-byte.
> - **"Mask keys and tokens" is on by default.** It replaces name=value pairs such as `TOKEN=…`, the body of a pasted private key, and fixed-shape tokens like `ghp_` / `AKIA` with `[z2term:masked]`. ⚠ **It is not complete.** Only clearly recognisable shapes are covered; a secret in your own format stays in. **Always read a log before handing it to someone.**
> - `~/z2term-log/` is **visible to other apps** (it is treated like the rest of your home). As with any other file under home, don't keep there what you don't want seen.
> - Full-screen apps (the ones that paint by redrawing the screen) are not recorded by default, because flattening them does not produce readable text.

### Hints for common stumbles

When you hit one of the usual walls — `ping` not working, a port below 1024, `/sdcard` looking empty — **one line with the next step** appears at the bottom of the screen (it fades after a few seconds, or tap it to dismiss).
The terminal output itself is **never modified**; this is just an extra line elsewhere.
If you find it noisy, turn off Settings > Display > "Explain common stumbles".

### Taking your setup with you (for a new phone or a reinstall)

Settings > Maintenance > **"Take it with you"** writes your current setup to a single file.

- Included: **settings, SSH connections, snippets, automation rules, macros**
- Not included: **the OS image** (hundreds of MB — a reinstall brings it back) and logs
- **SSH passwords and keys are left out by default.** Tick the box to include them and you will be asked for a **passphrase** (without it the backup cannot be restored, so pick something you will remember).
- When restoring, you see **what and how many** will be added before deciding. Nothing you already have is deleted; only matching items are replaced.

### Creating an SSH key in the app

In 📜 > "SSH / SFTP", add a connection and set auth to public key: a **"Create a key (ed25519)"** button appears.
Press it and the key is made, with **copy / share / add to this device's sshd** right there.
Give the **public** key to whoever runs the server you connect to (the private key never leaves this device).
The field for pasting your own private key is still there.

### Reusing a command you typed before (History)

**📜 → the "History" tab** lists the commands you ran in the terminal, **newest first**.

- Type in the field at the top to filter. **Space-separated words all have to match**
  (`git log` also finds `git --no-pager log`).
- Tapping a command only **puts it on the input line** — it is not run. Edit it, then press ⏎.
- The content is the shell's own history (`~/.bash_history` and `~/.zsh_history`), so it matches
  what `history` shows in the terminal. The app does not keep a separate copy.

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

### Keeping a tunnel running (get in from outside, bring a service here)

Editing a host in 📜 → "SSH / SFTP" lets you add **port forwards**, in one of two directions.

| Direction | What it does | Example |
|---|---|---|
| **-L** | brings **a remote service here** | view your home PC's web server at `127.0.0.1:8080` on the phone |
| **-R** | lets **the remote reach this device** | ssh into the phone from your home server while you are out |

Once at least one forward exists, a **"Keep this tunnel running"** toggle appears. With it on, the
**forwards survive closing the SSH tab** (they are treated like resident servers and come back after a reboot).

- **`-R` only makes sense together with residency** — if you need a tab open on the phone to get in,
  you did not need remote access in the first place.
- **Connect once from the SSH tab first so the host key is trusted.** A resident tunnel cannot show a
  confirmation dialog, so it **refuses to connect** to an unknown host rather than trusting it silently.
- If the link drops it reconnects on its own after 5s, 10s, … up to 5 minutes.
- ⚠ `-R` makes this device reachable from the other end. Turn it on only when you need it.

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

Settings are split into **8 groups** (Display / Keyboard / Input and language / Linux environment / Resident servers and automation / Maintenance / Developer / About this app), and **tapping a heading opens or closes it**. The open/closed state is **remembered even after you close the app**, so you can keep the groups you use often expanded.

| Setting | Description |
|---|---|
| Theme | Color scheme (9 options) |
| Font | Display typeface (4 options, with preview) |
| Font size | 4–32 (also changeable by pinching) |
| Scrollback lines | How many lines you can scroll back through |
| Toolbar | **Choose which buttons appear above the terminal.** The real buttons are laid out; tap to remove or bring one back. A removed button's position is remembered. ⚙ is always rightmost and cannot be removed. If you remove 🔅 screen-on lock or 🔒 keep-alive, a switch for it appears in this section |
| Distro | Alpine / Ubuntu / Arch / Kali |
| Login shell | zsh / bash / sh — **the same shell is used for the terminal tab, SSH logins and the GUI's inner terminal** (the distro's `/etc/passwd` login shell is updated too). If the chosen shell is not installed in that distro, the default shell is used as before |
| Keyboard style | Simple / 4-direction flick |
| Japanese IME learning history | The phrases the converter has learned. Search and delete them one by one, or clear them all |
| Keyboard position (landscape) | Left / bottom / right — effective only in landscape |
| Side keyboard width (landscape) | Slider 280–700 dp |
| Keyboard height (landscape / portrait) | Slider 200–500 dp (remembered separately per orientation) |
| GUI audio | Play sound (video, etc.) in the GUI (desktop) — only when ON |
| GUI terminal | Pick which terminal app is used inside the GUI desktop |
| Language / 言語 | Japanese / English (switches instantly) |
| Disable install timeout | Wait for OS / GUI downloads to finish completely |
| Confirm before downloading | Show a confirmation dialog before fetching a distro / GUI |
| SSH connection helper | Steps for connecting from a PC, with the IP shown |
| Storage access | Permission to use `/sdcard` |
| External storage (SD card) | When on, an inserted SD card is made visible from inside the OS (`/sdcard_ext`) |
| Background process protection | A way into the battery-optimisation exemption, plus instructions for turning off phantom process killing with `adb`. This is what keeps long background work alive |
| Reset terminal | Returns the app to **the state it had when first opened**. Only one terminal tab is left (other terminal tabs and GUI tabs are closed), and that terminal goes back to its initial state: running programs are terminated, screen and scrollback cleared. Tapping it opens a confirmation. **Saved servers, settings, snippets and the OS itself (installed packages and files you made) are not removed** |
| Clear cache | Sweeps the package/build caches that pile up inside the OS (pacman, apt, apk, `~/.cache`, …) plus the app's temp files. Tapping it opens a confirmation that **itemizes what and how much** will be deleted. Installed packages, settings and files you made are not removed |
| Delete OS data | Removes an installed OS (Alpine / Ubuntu / Arch / Kali) entirely to free storage. Tapping it opens a confirmation |
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
   - **Grab ≡ and drag a row up or down to reorder** the list (the same gesture as snippets). The order is remembered, and running servers keep running while you rearrange them.
4. Turn on "**Auto-start on boot**" and the servers **come up automatically after the device boots, without opening the app**.
5. To stop, use the "**Stop servers**" notification action or "**Stop**" on this screen (stops them all at once).

Note: ports below 1024 (e.g. 80) cannot be opened without root — use a high port (e.g. 8080).
Note: excluding the app from battery optimization makes it less likely to be killed in the background (link inside ⚙ Settings).
Note: if battery use bothers you, turn on "**Low-power mode**" — it lets the device sleep deeply while the screen is off to save battery, but incoming connections may be delayed or dropped during that time (battery over reachability).

#### Ending the terminal while resident servers are running

While resident servers run, the app stays in the background, so **swiping it away from recents will not close the terminal**. During this time the toolbar's **🔒 is dimmed and can't be toggled** (turning it OFF would do nothing).

**Tap the dimmed 🔒** to choose one of two things:

- **End session only**: returns the terminal to a **freshly opened state** (running programs end; screen and history are cleared). **Resident servers keep running.**
- **Stop everything and quit**: **stops the resident servers, system event capture and the terminal, then closes the app** (a reliable way to quit, since swiping won't). Your settings are unchanged, so capture resumes the next time you open the app.

Pick "End session only" for a clean slate, or "Stop everything and quit" to stop it all.

---

## 9.5. Home screen widget (use it without opening the app)

Puts Z2Term's **current state** on your home screen and runs **your favourite macros with a single tap**.
The app does not open — you stay on the home screen while the macro runs in the background.

**Adding it**

1. Long-press an empty spot on the home screen → "Widgets"
2. Pick **Z2Term status** from the list and drop it on the home screen
3. The settings screen opens right away — **tick the macros you want and press Save** (up to 4)

If you have no macros yet, run `z2-macro install all` in the terminal first
(everything under `~/.z2term/macros/` becomes selectable). Your own `.sh` files
dropped into that folder show up the same way.

**What it shows**

| Where | What |
|---|---|
| 1st line | `ssh -p 2222 root@192.168.x.x` — the command to get into this device from a PC. **Green means `sshd` is running** (grey = just the address) |
| 2nd line | `servers 1/3 · rules 2/5 · battery 87%` (see the table below) |
| Top right | **⚙** — pick which macros to show / **⟳** — refresh right now |
| Buttons | The macros you picked. A tap runs one in the background. Line 1 is the name, **line 2 is when that macro was started today** |
| Button marker | `■` = running (green) / `✓` = ran and finished today / no marker = has not run today |
| Bottom line | The macro that **finished** last today, and when |

**What the numbers on the 2nd line mean**

`1/3` reads "**how many are live now / how many are registered**". The three numbers count
**three different things**:

| Shown | Counts | Where you add them |
|---|---|---|
| `servers 1/3` | Resident servers | Settings › Resident servers & automation › Servers |
| `rules 2/5` | Automation rules | `z2-when` in the terminal (files in `~/.z2term/when/`) |
| The buttons below | Macros | `z2-macro` in the terminal (files in `~/.z2term/macros/`) |

So **`rules` has nothing to do with the number of buttons below it**. Adding four macro buttons does
not change `rules`, and `servers 0/3` means "three are registered, none are running right now" —
which is what you see when the resident servers have not been started.

**When it looks like it started and stopped straight away**

The macro may simply have **finished instantly** (one that only does an `echo`, for example).
If `■` (running) is gone and `✓` is there, it was not stopped — it **completed normally**.
The bottom line also says "✓ name finished at HH:MM". A macro that keeps running keeps its `■`.

**Stopping a macro**

**Tap a running button again to stop it** (the `■` marks it as running). That makes it possible to
stop long-running macros — the ones that keep watching for events — straight from the home screen.
Note that **quitting the app also ends any macro started from the widget**.

**Tips**

- The macro list in the config screen shows **a description under each macro name**, read from the
  comment at the top of the script. When writing your own `.sh`, put `# what this macro does` on the
  line after the shebang (`#!/bin/sh`) and it will show up here.
- Tap **⟳ at the top right to refresh right now**. Otherwise it updates every 30 minutes
  (Android does not allow anything shorter). It also redraws itself when resident servers
  start/stop or when you add, remove or toggle a `z2-when` rule.
- Tap **the Z2Term title at the top left to open the app**.
- Macro output is kept in `~/.z2term/widget/run.log`. If something does not work,
  run `tail ~/.z2term/widget/run.log` in the terminal.
- To change which macros are shown, **tap ⚙ at the top right of the widget** (long-pressing the
  widget to open its settings still works too).
- **The `✓` and the time clear when the date changes** (the button goes back to no marker the next
  day). A button can only show `HH:MM`, so a mark left over from yesterday would be unreadable.
  To clear them right now, use **⚙ → "Clear run history"**. Your macros are not touched.
- **To get more macros into that list**, put your own `.sh` in `~/.z2term/macros/` in the terminal,
  or run `z2-macro install all`, then reopen ⚙.

### The other widget: live tail

Keeps the **end or the start of a file** on your home screen, so you can see what a macro wrote or
when a `z2-when` rule fired **without opening the app**.

1. Long-press an empty spot on the home screen → "Widgets"
2. Pick **Z2Term live tail** and place it
3. The config screen opens — choose **the file** and **which end of it**, then Save

- There are two ways to choose it:
  - **type the path** in the field at the top (e.g. `~/.z2term/events.jsonl`)
  - **tap through the folders** in the list below and tap the file you want
- **Pick the end (tail) or the start (head)** (0.8.240).
  - **end** — for a log that keeps growing, where new lines are added at the bottom.
  - **start** — for a file already written, like a report or a config file where **what matters is at the top**.
  - The line under the text always says which one you are looking at: `tail` or `head`.
- **How many lines are shown follows the widget's size** — stretch it taller for more lines.
- The usual ones are:
  - `.z2term/widget/run.log` — output of macros started from the widget
  - `.z2term/events.jsonl` — detected events (screen on/off, charging, Wi‑Fi …)
  - `.z2term/when/<id>.log` — what that automation rule ran
- **⟳** re-reads right now. Besides the 30-minute automatic refresh, it also redraws itself
  **whenever a macro or an automation rule finishes**.
- **⚙** changes which file is shown.
- It only ever reads **part of the end you chose**, so it stays fast however large the file grows.

---

## 10. Troubleshooting (FAQ)

**Q. `sshd` won't work / the port is wrong**
→ Type `sshd` (not the full path `/usr/sbin/sshd`). Set the port via `Port` in `/etc/ssh/sshd_config`, or `sshd -p <number>`. Use 1024 or higher.

**Q. I can't type Japanese**
→ Switch to Japanese flick with the "あ" key on the left of the keyboard. Typing kana shows the candidate bar, so you can convert to kanji with the "変換" key or by tapping a candidate (long sentences are predicted automatically per chunk). If you want smarter conversion, switch to the phone's standard keyboard (Gboard, etc.) with the "⌨" toolbar button.

**Q. I can't select text well / can't select to the edge**
→ Long-press first, then drag with your finger. To reach the edge, move your finger toward the top/bottom edge of the screen and it auto-scrolls. Drag near the end of the selection to change its range.

**Q. My resident servers (sshd, …) were stopped after updating the app**
→ **That is expected.** Replacing the app makes Android shut it down once, which takes the resident servers with it. "Auto-start on boot" applies when the **phone** boots, so it does not bring them back after an app update. After updating, open ⚙ Settings → "Resident servers and automation" → "Resident servers" → "Manage servers" → **Start** again.

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
| `z2-screen keepon <1h\|30m\|90s>` | **Stop the screen turning off by itself, for that long** (`keepon off` puts it back early, `status` shows what is left). It changes the OS-wide screen timeout, so **it holds with the app in the background**. ⚠ Not the toolbar's 🔅, which only lasts while the app is on screen. The original value is always written back at the deadline (even if the app is killed or the device reboots). Max 24h in one go. **Needs "modify system settings"** (Settings › screen timeout) |
| `z2-tile set <1-4> <macro.sh\|command>` | **Put a macro or command on the quick-settings panel** (`list` / `clear <1-4\|all>`, `-l` for the label). Tap to run, tap again to stop (same deal as the widget buttons). The tile is green while it runs. A locked device is asked to unlock first. ⚠ **There are exactly 4 slots** (Android fixes the number at build time). ⚠ **You place the tiles yourself**, from the pencil/edit screen of the quick settings panel — Android does not let an app put its own tiles there (on Android 13+, Settings › Quick-settings tiles can ask for you) |
| `z2-alarm at\|daily HH:MM [name]` | **Time trigger**: writes an `alarm` event into `events.jsonl` at that time (`in 5m` / `list` / `cancel <id\|name\|all>` too). Unlike cron it fires during Doze (may be a few minutes late) |
| `z2-session list\|new\|send\|capture\|close` | **Drives this app's own tabs.** `list` shows them (index, id, name, marks: `*`=visible, `!`=busy, `?`=not started), `new [name]` adds one, `send <target> "text"` **only inserts** into that tab (add `--enter` to actually run it), `capture [target]` pulls the on-screen text, `close <target>` closes it. `<target>` is the index from `list`, an id, or a tab name. E.g. ``n=$(z2-session new build \| cut -f1); z2-session send "$n" 'make -j2' --enter`` |
| `z2-when <trigger> run <cmd>` | **Automation hub.** Auto-run a command on charge / battery / time / device events (see "Automation hub" below). Also `list` / `remove <id\|all>` / `on\|off <id>` / `log <id>`. E.g. `z2-when charge:start run ~/.z2term/macros/backup.sh` |
| `z2-macro list\|install <name>` | **Bundled macro samples** into `~/.z2term/macros/` (`show` / `run` / `dir` too) — a starting point for your first macro. Bundled: `watch-basic` / `battery-alert` / `daily-report` / `otp-clip` / `otp-sms` / `rss` / `rss-open`. On install it also tells you **how that script is meant to be run** (register it as a resident server / drive it with `z2-when` / assign it to a widget button). ⚠ **Never register a one-shot script (`rss` / `rss-open`) as a resident server** — it would be restarted every time it finishes |
| `z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] …` | Fire an arbitrary Android Intent (launch apps, open settings, set alarms, … see `docs/en/MACRO-GUIDE.md`) |

> Combine "trigger (event detection) → decide (shell) → action (z2-*)" to automate your phone (macros). See **`docs/en/MACRO-GUIDE.md`** for how — you can also feed it to an AI and have it generate the macro for you.

### Subscribing to feeds (RSS / Atom)

**There is no RSS reader in the app.** You build one out of parts that already exist: scheduled runs, notifications, opening a browser, and widgets. The upside is that you can rewrite any of it when your needs differ.

```sh
z2-macro install rss rss-open        # the collecting side and the opening side
python3 -V || apk add python3        # parsing needs python3 (Debian: apt-get install -y python3 / Arch: pacman -S python)
```

**1. List the feeds you want** — one URL per line in `~/.z2term/rss/feeds.txt` (lines starting with `#` are ignored).

**2. Poll them on a schedule**

```sh
z2-when time:every=30m run ~/.z2term/macros/rss.sh
```

A notification ("N new") appears only when something is new. **Do not go below 30 minutes** — polling costs battery.

**Read what was collected as a list** (0.8.255)

```sh
sh ~/.z2term/macros/rss.sh list       # 20 items
sh ~/.z2term/macros/rss.sh list 50    # pick a count
```

One article per line, as `[ 1] Article title  (zenn.dev)`. **Tap the title to open the article** — the URL is not printed, because long URLs wrap and tangle with the titles until the list is unreadable. Piped or redirected, it falls back to plain text with the URLs shown.

**3. Let the notification's button open the newest item** (optional)

```sh
z2-when event:notify_action run '[ "$Z2_WHEN_EVENT_NAME" = rss ] && z2-open "$(head -1 ~/.z2term/rss/new.txt | cut -f1)"'
```

**4. Read from a widget** (optional)

- Point a **live tail widget** at `~/.z2term/rss/latest.txt` in **"start (head)"** mode and the newest articles sit at the top
- Assign `rss-open` to a button on the **status widget**, and each tap opens **the next article down the list** (it remembers what it opened, so nothing opens twice)

Everything it produces is plain text.

| File | Contents |
|---|---|
| `~/.z2term/rss/feeds.txt` | The feed URLs you want (you write this) |
| `~/.z2term/rss/latest.txt` | "title  URL", newest first. This is the one you read |
| `~/.z2term/rss/new.txt` | Only what the last poll added |
| `~/.z2term/rss/seen.txt` | Articles already seen (used to decide what is new) |
| `~/.z2term/rss/opened.txt` | Articles `rss-open` has opened |

Delete them all to start over. One dead feed does not stop the others, and broken XML is skipped silently.

### Automation hub (`z2-when`)

Auto-run a command **when you start charging / the battery drops / a set time arrives**. No need to `tail` events yourself — you just **declare** the rule and it runs, even with the app closed and across reboots.
**However, every trigger except time, SMS and notifications (charging, battery, Wi‑Fi, sensors, new files, device events) only works while "detection" is on** (Settings › keep-alive & automation): Android does not deliver those events to an app that isn't resident. SMS needs the receive-SMS permission, and `notify:` needs notification access.

**Rules may start servers too** (e.g. `z2-when wifi:connect run 'sshd --lan'`). A server started that way **keeps running** after the rule itself finishes (fixed in 0.8.253 — before that it was taken down the instant the run ended, so the log said "listening" while nothing answered). ⚠ It only survives **while the app is alive**; register anything that must stay up permanently as a **resident server**.

How to register (just line up a trigger and a command):

```sh
z2-when charge:start        run ~/.z2term/macros/backup.sh   # back up when charging starts
z2-when battery:below=20    run "z2-notify -h 'Battery under 20%'"
z2-when time:daily=03:00    run ~/.z2term/macros/nightly.sh  # every day at 03:00
z2-when time:cron='0 9 * * 1-5' run ~/.z2term/macros/weekday.sh  # weekdays at 09:00
z2-when wifi:ssid=home       run ~/.z2term/macros/expose-lan.sh # when joining home Wi‑Fi
z2-when sms:otp              run 'echo "$Z2_WHEN_OTP" | z2-clip'  # copy an incoming OTP to the clipboard
z2-when sensor:shake         run ~/.z2term/macros/panic.sh        # when you shake the device
z2-when event:headset_plugged run ~/.z2term/macros/play.sh        # when wired earphones go in
z2-when 'event:ringer_*'      run 'z2-toast "$Z2_WHEN_EVENT"'     # on any ringer-mode change
```

- **Trigger types**
  - `charge:start` / `charge:stop` — charging started / stopped. **Only works while "detection" is on** (Settings › keep-alive & automation)
  - `battery:below=N` / `battery:above=N` — the moment the level **crosses** N% (e.g. drops under 20). Also **only works while "detection" is on**
  - `time:daily=HH:MM` (every day) / `time:at=HH:MM` (once at the next HH:MM, then auto-disabled) / `time:every=30m`·`2h` (fixed interval, min 1 minute)
  - `time:cron='min hour dom month dow'` — a cron expression for finer control (e.g. `'0 3 * * *'` daily at 03:00 / `'*/15 * * * *'` every 15 min / `'0 9 * * 1-5'` weekdays at 09:00). Day-of-week is 0–7 (0 and 7 are Sunday). **Always quote it** since it contains spaces.
  - `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<name>` — when Wi‑Fi connects / disconnects / joins a given network. **These Wi‑Fi triggers only work while "detection" is on** (Settings › keep-alive & automation). Using the network name (SSID) also needs location permission. Inside the command, `Z2_WHEN_SSID` holds the connected network's name.
  - `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` — when an SMS arrives (any / sender matches / body contains / body has an OTP-looking code). **Needs SMS receive permission** (grant it via Settings › "SMS detection"). Inside the command you get `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`, and for `sms:otp` the extracted code in `Z2_WHEN_OTP`. Reading SMS directly avoids Android 15's OTP redaction.
  - `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far` — when you shake the device / ambient light (lux) crosses N up or down / the proximity sensor goes near or far. **These sensor triggers only work while "detection" is on**. Sensors cost battery, so only the sensors your rules use are turned on (none run if you don't use them). `shake` only reacts to a **firm shake** (deliberately set high so that walking around doesn't trigger it), and at most once every 3 seconds. Inside the command, `Z2_WHEN_SENSOR` names the sensor and `Z2_WHEN_LUX` holds the light level (for light).
  - `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` — **when a notification arrives** (added in 0.8.236). Handy for confirmation codes that don't come by SMS (email, authenticator apps), e.g. `z2-when notify:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'`. The command gets `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT`. Needs **notification access** (Settings > resident servers & automation > notification detection), and works even with notification logging turned off.
  - `file:new=<dir>` / `file:new=<dir>,ext=<ext>` — **when a new file lands in that folder** (added in 0.8.235; e.g. `z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh`). The command gets `Z2_WHEN_FILE` (full path) and `Z2_WHEN_DIR`. It fires **after the write finishes**, so it never grabs a half-copied file. Needs **"detection" on**.
  - `event:<name>` — **any device event, by name** (added in 0.8.226). Run **`z2-when events`** to list the names (~20: `screen_on`, `unlocked`, `headset_plugged`, `bt_audio_connected`, `ringer_silent`, `airplane_on`, `alarm`, `notify_action`, …). A trailing `*` makes it a prefix match (`event:ringer_*`), and `event:*` matches everything. Inside the command, `Z2_WHEN_EVENT` holds the event name.
    **The same rule will not fire twice within 10 seconds** (some events, like `screen_on`, happen often).
    Passive events (screen, charging, Wi‑Fi, …) need **"detection" on**, but `alarm` (set with `z2-alarm`) and `notify_action` (a notification button) **work with detection off**.
- **See and stop them from the app** (0.8.227): 📜 → the **Automation** tab lists your rules. Each row has an on/off switch, **▶ to run it once without waiting for the trigger**, **▤ for its run log**, and ✕ to delete. The **Pause automatic runs** switch at the top stops **every** rule from firing (nothing is deleted, and ▶ still works). Below the list, **recent fires** show what ran — and what was held back (`paused`), so a rule that seems dead is easy to explain. **Grab ≡ and drag** to reorder the list (0.8.249; the order is remembered and is **display order only** — it changes neither when rules run nor what triggers them).
  The same things work from the terminal: `z2-when pause` / `z2-when resume` / `z2-when fired`.
- **List / remove / toggle**: `z2-when list` / `z2-when events` (names usable with `event:`) / `z2-when remove <id>` (`all` for everything) / `z2-when on <id>` `z2-when off <id>` / `z2-when log <id>` (see the run log)
- Inside the command you can use `Z2_WHEN_TRIGGER` (which trigger fired), `Z2_WHEN_LEVEL` (battery level then), `Z2_WHEN_SSID` (the network for a wifi trigger), `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY` / `Z2_WHEN_OTP` (for sms triggers), `Z2_WHEN_SENSOR` / `Z2_WHEN_LUX` (for sensor triggers), and `Z2_WHEN_EVENT` (for event triggers; `alarm` and `notify_action` also set `Z2_WHEN_EVENT_NAME` / `Z2_WHEN_ACTION`) as env vars.
- Rules live as text under `~/.z2term/when/`, so you can **sync/back them up with git**.
- Time triggers use a battery-friendly mechanism (Doze-through AlarmManager), so **firing can be a few minutes off**. The `wifi` / `sms` / `sensor` / `cron` triggers are all available.

### Graphical (GUI) apps
| Command | What it does |
|---|---|
| `z2gui start [WxH]` / `stop` / `status` | Start / stop / status of the Linux desktop (e.g. `z2gui start 1280x720`) |
| `z2run <GUI app>` | Launch a GUI app and open the GUI tab with it |

> **The GUI tab never opens by itself (0.8.254).** There used to be a hook that opened it as soon as you ran something that looked like a GUI app, but **a text editor that merely talks to X for clipboard support tripped it too**, so it was removed. The GUI opens only when **you open the GUI tab yourself** or **you type `z2run`**.

### Connecting
| Command | What it does |
|---|---|
| `z2-noti list` | **List the notifications on screen right now** (app, title, body). Read-only — it cannot press or dismiss them |
| `z2doctor` | **Self-check for when something doesn't work.** Lists version, permissions, detection and automation state; every `NG` line tells you what to do. `z2doctor --clip` copies a report (SSIDs and IPs are left out) |
| `z2adb …` | `adb` to this phone itself (no PC) → see **§7.5** |
| `sshd` | Start an SSH server → see **§7** (defaults to "this device only" + key auth only) |

### Security (vulnerability testing)
| Command | What it does |
|---|---|
| `z2scan self [--save]` | Self-check this device/localhost (open ports, sshd config, SSH key perms, world-writable/SUID, PATH). No external tools. **`--save` also records the result as the baseline** |
| `z2scan diff [--quiet]` | Re-run the self-check and print **only what changed** since the baseline: `+` is new, `-` is gone. **Exit code 1 only when something is new** (things going away exit 0 — no need to be told). `--quiet` prints nothing at all when nothing changed |
| `z2scan baseline [clear]` | Show the saved baseline (`clear` deletes it) |
| `z2scan setup` | Install scanners (`nmap`/`lynis`) from your distro's official packages |
| `z2scan net [--allow-remote] [target]` | `nmap` TCP scan. Target defaults to `127.0.0.1`. A non-local target requires `--allow-remote` + a warning |
| `z2scan host` | Host audit via `lynis` (falls back to `self` if absent) |
| `z2scan cve` | Known-CVE scan of the rootfs via `trivy`/`grype` if present |

> Note: results stay local (nothing is sent out). **Only scan systems you are explicitly authorized to test.**

**Daily watch (tell me only what changed)**

Nobody reads a full report every day, so record "the way it is now" as the baseline and get told **only on days when something is new**.

```sh
z2scan self --save                      # once: make the current state the baseline
z2-when time:daily=03:00 run 'out=$(z2scan diff --quiet); [ -n "$out" ] && z2-notify -h "z2scan: something changed" "$out"'
```

`--quiet` prints nothing on a quiet day, so the notification only fires when `$out` has content.
If the change was you (you opened that port on purpose), re-record with `z2scan self --save`.

> The baseline is plain text at `~/.z2term/scan/baseline.txt` — the `[WARN]` / `[INFO]` lines of the report, so you can read it directly and keep it in git. Changing the app's language changes the strings themselves, so the baseline has to be re-recorded (you get a warning when that happens).

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
