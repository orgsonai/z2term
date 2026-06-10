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
│ hostname [mode]   📋 📜 🔅 🔒 🔍 ⌨ ⚙       │ ← top bar (toolbar)
├───────────────────────────────────────────────┤
│ [ archlinux ] [ + ] [ 🖥 ]                      │ ← tabs (switch between terminals / GUIs)
├───────────────────────────────────────────────┤
│                                                │
│        the terminal itself (where text shows)  │
│                                                │
├───────────────────────────────────────────────┤
│ ▾  (tap to open/close the keyboard)            │
├───────────────────────────────────────────────┤
│            keyboard                            │
└───────────────────────────────────────────────┘
```

**Toolbar buttons (left to right)**

| Button | What it does |
|---|---|
| 📋 | Paste text from the clipboard |
| 📜 | Command list (tap a frequently used command to type it) + **SSH connect / SFTP** (switch with tabs) |
| 🔅 | Screen-on lock (when ON, the screen won't auto-dim; the icon changes to 💡 while ON) |
| 🔒 | Background keep-alive (while ON, the terminal keeps running even if you close the screen; 🔒 = ON, 🔓 = OFF) |
| 🔍 | Search the on-screen text (jump back/forward with ↑↓) |
| ⌨ | Switch between the phone's standard keyboard ⇄ the in-app keyboard |
| ⚙ | Settings |

> **Reorder the buttons**: **long-press** a toolbar button to **drag it left/right and reorder**. A short description pops up above the button while held. The order is remembered.

---

## 4. Using the keyboard

Z2Term comes with its **own in-app keyboard**.

### Latin (ASCII) keyboard
- Tap letter keys normally.
- **⇧ (shift)**: tap once = next single letter uppercase / tap again = always uppercase / tap again = release.
- **Flick down (swipe a key downward) = uppercase.** e.g. flick `q` down to get `Q`.
- **Flick up / left / right = symbols** (the small green characters are the hints).
- **Long-press to repeat**: letters, numbers, arrows, and space repeat while held. `⌫` (delete) also repeats on long-press.
- Press `CTRL` or `ALT` then a letter → shortcuts like Ctrl+C.
- **The key background turns bright green when pressed** (so you can see what you touched).
- During a flick, once your finger passes the threshold, **the small hint character for the destination is enlarged and highlighted**, so you can confirm where it will land before lifting your finger.

### Landscape keyboard
- In settings, **"Keyboard position (landscape)"** lets you choose **left / bottom / right** (in portrait it's always at the bottom).
- The **"Side keyboard width (landscape)"** slider adjusts the width when docked to a side, and the **"Keyboard height (landscape)"** slider adjusts the overall height. Bigger keys are easier to press; smaller keys give you more screen — your trade-off.

### Japanese / kana-kanji conversion
- Press the **"あ" key** on the left side of the keyboard to switch to the built-in **Japanese flick keyboard**.
- Flick rules: **tap = あ / left = い / up = う / right = え / down = お** (same as the common 12-key phone layout).
- **Cursor up/down keys**: just **below** ◀ ▶ are **▼** (left = down) and **▲** (right = up) — all four ◀▶▼▲ are the same size. Use them to walk command history or move the cursor.
- **The "小゛゜" key** changes the previous character like `か→が→か`, `は→ば→ぱ→は`, `つ→づ→っ→つ`.
- While typing (unconfirmed), a **candidate bar** appears at the top so you can convert to kanji.
  - With the **"変換" (convert) key** or **◀ ▶**, convert from the leading chunk (block). Tap a candidate or press ⏎ to confirm, and it automatically advances to the next chunk.
  - When you type a **long sentence**, predictions appear **automatically per chunk** without pressing "変換" (e.g. あしたのてんきは… → 明日の / 天気は / …; particles and endings like "です・ました" stay in kana).
  - The **light-green chunk** in the candidate bar is the "whole-sentence" conversion (e.g. 明日の天気はいかがでしょうか). Tap it to confirm the entire sentence at once. **Moving the boundary of the leading chunk with ◀ ▶ also rebuilds the light-green chunk to match the new boundary.**
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
| Copy text | **Long-press** the screen → drag with your finger to select → "Copy" button |
| Magnify while selecting | While selecting, a **magnifier** appears above your finger |
| Select beyond the screen | While selecting, move your **finger to the top/bottom edge** → it auto-scrolls so you can keep selecting |
| Paste | The **paste** button in the toolbar |
| Scroll up/down | Drag with one finger. Use **↓** at the bottom-right to return to the latest |
| Make text bigger/smaller | **Pinch** with two fingers (spread/squeeze) |
| Add a terminal | The tab **+** (terminal) / **🖥** (GUI desktop) |
| Remove a terminal | **Double-tap** that tab (the last remaining one won't be removed) |
| Reorder tabs | **Long-press** a tab then **drag** left/right (you can move it edge to edge in one gesture) |
| See tab info | **Long-press** a tab to pop up its name and the **engine it's running on** (PRoot / z2root / chroot / Android sh, or GUI for GUI tabs) — no need to open Settings; it shares the same long-press as reorder |
| Check the app version | Type **`z2version`** in the terminal to print the running app's version, execution engine, etc. (`z2version --short` for just the version) |
| Past commands | The **↑ key** (history persists even after restarting the app) |

> When you launch the app it **always opens a single terminal tab** (previously open tabs are not auto-restored).

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

> Tip: `/usr/sbin/sshd` (OpenSSH) does not work with this app's mechanism. **Always type `sshd`**
> (a lightweight dropbear runs underneath).

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

| Setting | Description |
|---|---|
| Theme | Color scheme (9 options) |
| Font | Display typeface (4 options, with preview) |
| Font size | 8–32 (also changeable by pinching) |
| Scrollback lines | How many lines you can scroll back through |
| Distro | Alpine / Ubuntu / Arch / Kali |
| Login shell | zsh / bash / sh |
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

---

## 11. Friendly glossary

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
