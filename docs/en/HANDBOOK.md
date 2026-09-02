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

1. Put the APK file (`z2term-0.8.439-alpha.apk`) on your phone.
2. Allow "Install from unknown sources" and install it.
3. Open the app.

The app bundles no Linux OS, so the first launch shows a single notice: "No Linux OS is installed yet" (0.8.314). Tap it to open Settings and **pick the OS you want** (Alpine / Ubuntu / Arch / Kali …; any of them is a fine place to start). ⚠ **The notice cannot be dismissed until an OS is installed** (0.8.342 — dismissing it left nothing on screen saying what to press to get Linux). **Settings pins the same notice at the top, and tapping it carries you to the "Linux environment" section.** Both go away once one OS is in.
**Once one Linux OS is installed**, **three small cards** appear above the terminal (post a notification / turn on the flashlight / open the reminder guide) — the first time only. (Before 0.8.339 they also appeared before an OS was installed, where tapping them did nothing, so they now wait until the install is done.) **Tapping one runs that line as it stands** — anything half-typed is thrown away with `Ctrl-C` first, so nothing mixes in. The ✕ on the right drops a card you do not want. Once all three are gone they never come back. To see them again: Settings > Maintenance > "Show the intro again".
That's all the setup you need.

> ℹ️ **The APK is ~21MB.** It does not bundle the Linux OS itself, which keeps it small — and keeps every update that small too. In exchange it **needs a network connection on the first launch only** (it auto-downloads from the official site and verifies the SHA-256). ⚠ Up to 0.8.358 there was also a ~190MB build with the OS bundled (`full`); **0.8.359 dropped it and ships one build only** (it made you choose without offering anything beyond skipping that first download).

### Checking whether a newer version is out

Settings > App info > **"Check for updates"** compares your version with the latest on GitHub. It talks to the network **only when you tap it** (there is no automatic check). If a newer version exists it shows the number.

**You can update right there (0.8.371).** The **"Download and install"** button that follows fetches the APK and takes you to the install screen. ⚠ **The final "install?" tap is always yours** — Android does not let an app replace itself silently. ⚠ **The first time**, z2term needs "Install unknown apps" (a button appears if it is missing).

The terminal has the same thing: **`z2-update`** (`--check` to only look, `--keep` to leave the APK behind, `--dir <folder>` to change where it lands). `z2-when time:daily=03:00 run 'z2-update'` looks every night (it still asks you).

The downloaded APK is **deleted once the update goes through** (and on the next start, if the app was killed mid-install). Keep it with "Keep the .apk after updating" in Settings, or `--keep`; the download folder is next to it.

⚠ **Installed from F-Droid or another store? Then it refuses** — that copy is theirs to replace, so update it there. **"Open the release page"** still works as before.

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
| 📜 | Command list (tap a frequently used command to type it) + **History** (filter past commands and insert one) + **SSH connect / SFTP** + **Servers** (manage resident servers) + **Automation** (list `z2-when` rules, **add / edit** them, toggle them, read run logs, pause) (switch with tabs) |
| 🔅 | Screen-on lock (when ON, the screen won't auto-dim; the icon changes to 💡 while ON). **Double-tap for a slider that dims this app only** (for dark rooms; going home restores it, and Reset clears it any time). **The level you pick is remembered, so the app opens at that brightness next time** (press Reset to go back to normal) |
| 🔒 | Background keep-alive (while ON, the terminal keeps running even if you close the screen; 🔒 = ON, 🔓 = OFF). **While resident servers are running, 🔒 is dimmed and can't be toggled** (the servers already keep the app alive, so turning it OFF here would do nothing). Tapping 🔒 in that state opens a screen to choose **"End session only" / "Stop everything and quit"** (see below). Note: **keeping the device reachable from outside (ssh, etc.) is the job of "resident servers"**, not 🔒 (0.8.268 — 🔒 used to keep Wi-Fi at full power too, which cost a lot of battery, so it no longer does). ⚠ Even with resident servers running, **the device can be unreachable from outside right after rejoining Wi-Fi** (power save stops it answering "where I am"). This is Android's behaviour and the app cannot prevent it. **Any single outbound packet from the device fixes it**, so run `ping -c 1 <router IP>`, or put the same thing on a `z2-when wifi:connect` rule to have it recover automatically |
| 🔍 | Search the on-screen text (jump back/forward with ↑↓; **while searching, the scrollbar shows a tick for every hit** so you can see where they cluster — tap a tick to jump there; **tap in the input field to move the caret** and fix a typo in the middle). **You can search in Japanese without leaving the built-in keyboard** — text being converted appears underlined in the field and lands in the search term once you commit it (0.8.275; before that nothing changed on screen until you committed, so it looked as if typing did not work). With the OS keyboard selected the field behaves as an ordinary OS text field, as before) |
| ⌨ | Switch between the phone's standard keyboard ⇄ the in-app keyboard. **Double-tap to show/hide it; triple-tap for the size slider** (0.8.428; **two sliders — height and width** since 0.8.431). Even with the phone's keyboard, **the text being composed (before you confirm) shows inline at the terminal cursor** |
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
- Press `CTRL` or `ALT` then a letter → shortcuts like Ctrl+C. `ALT` **prefixes the next key with ESC**, which drives bash/zsh line editing: `ALT`+`.` inserts the last argument of the previous command, `ALT`+`b`/`f` moves the cursor by words, `ALT`+`d` deletes the word to the right. `ALT`+arrow keys are sent with the same ESC prefix. ⚠ The `META` key that used to sit on the English layout did exactly the same job as `ALT`, so in 0.8.281 it **became the paste / emoji key** (below). Use `ALT` for the Meta modifier.
- **Paste and emoji (English display, 0.8.281)**: the key drawn with ↕ **pastes on tap** and **opens emoji when flicked down** (the small 📋 above it and 😀 below it are the cues; flicking up also pastes, so drifting upwards does not change where you land). ⚠ **Up and down match the ESC flicks** (aligned in 0.8.397 — before that, flicking up opened emoji, so the directions were reversed from one face to the next). It sits left of `a` on the 4-direction flick style, and at **the right end of the top row (right of ESC / TAB / ⇧)** on the simple style (0.8.397 — it used to be at the bottom left, but CTRL appeared both above and below, so the two swapped places). While open, the keys are replaced by the pad and **only the bottom row (× ⌫ space ⏎ ← →) stays**, so you can delete or start a new line right after pasting. Close it with × or by pressing the same key again. The 😀 / 📋 tabs at the top of the pad switch between emoji and paste.
- **The key background turns bright green when pressed** (so you can see what you touched).
- **While you flick, the character you would get by letting go now appears in a large green square just above the key** (0.8.405). It swaps as you move, so you can check before lifting your finger. ⚠ This is now **exactly what the Japanese keyboard does** — before, only the alphabet face enlarged its own small hint, so the two faces behaved differently. The small hints printed on the key (green here, faint white on the Japanese face) are unchanged.

### Landscape keyboard
- In settings, **"Keyboard position (landscape)"** lets you choose **left / bottom / right** (in portrait it's always at the bottom).
- The **"Side keyboard width (landscape)"** slider adjusts the width when docked to a side, and the **"Keyboard height (landscape)"** slider adjusts the overall height. Bigger keys are easier to press; smaller keys give you more screen — your trade-off.
- **A bottom-docked keyboard can be narrowed too**, with "Keyboard width (bottom)" (0.8.431; 100% fills the screen, less centres it. Portrait and landscape remember their own value).

### Landscape stands the toolbar and tabs on their side (0.8.431; two columns since 0.8.433)

In landscape, **the toolbar and tab bar that used to stack on top move into two vertical columns on the
left or right edge**. A landscape screen has height to spare nowhere and width to spare everywhere, so
the space is taken from the side that has it — **roughly 90dp of height goes straight back to the
terminal / GUI**.

- **It is the portrait two-row layout turned 90°** (0.8.433; before that both shared one column and the
  boundary between toolbar and tabs was invisible). **The outer column is the toolbar, the one touching
  the content is the tabs** — the same relation as "toolbar on top, tabs below" in portrait. Each column
  is framed.
- **Pick the edge under ⚙ Settings › Display › Toolbar ("Left / Right")** (0.8.433; left by default).
- **Tab names are written vertically** (0.8.433), with a "…" after 8 characters.
  **Long-press a tab to read its full name and engine.**
- **Reordering is unchanged** — long-press and drag (up and down now).

> To adjust it without opening Settings, **triple-tap ⌨ in the toolbar**. **Two sliders — height and width** — let you resize while watching the keyboard (0.8.431; before that only one of them was offered, and a side-docked landscape keyboard could only change its width). This works even when the keyboard toggle bar is hidden (0.8.428). It is also listed under ⚙ Settings → **Tips** (0.8.430).

### Switching keyboard "faces" (0.8.305)

Pressing the **bottom-left key** swaps the whole set of keys. We call each set a **face**.

| Face | What is on it |
|---|---|
| **あ** | Japanese flick (kana) — only when the app language is Japanese |
| **A** | Latin (qwerty) |
| **12** | Numbers only (a keypad) |

⚠ **The key shows where it takes you, not where you are.** If it reads `12`, pressing it gives you the number face.

The **number face (0.8.305)** is for typing **runs of digits** — port numbers, IP addresses, `chmod 755`. The Latin face has a row of digits along the top, but ten keys side by side is fiddly; on the number face they are as big as kana keys. `.` `:` `-` `/` sit there too, so something like `192.168.10.20:2222` can be typed without leaving the face.

Under Settings › **Keyboard style**:

- Turn **"Show the number face"** off and you are back to two faces, **あ → A → あ**, looking exactly as it did before.
- **"Face switching order"** offers **あ → A → 12** and **あ → 12 → A**. ⚠ **Those two are all there is.** The faces cycle round, so `A → 12 → あ` is the very same rotation as the first one. (The setting only appears when the number face is on and the app language is Japanese — with two faces there is no order to speak of.)

### Japanese / kana-kanji conversion
- When the bottom-left key reads **"あ"**, press it to switch to the built-in **Japanese flick keyboard**.
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
  - **Words you use often come first inside sentences too** (0.8.398): typing 「きょうはあめがふるひだ」 used to offer **「教は雨が降る日だ」** first, and picking 今日 never changed that inside a sentence. How often and how recently you used a word now counts towards whole-sentence conversion, so your own words rise as you use them. It is staged: some change **after one pick** (今日), some **after a few** (話, 時), and words normally written in kana (「もの」 → 「物」) only **after repeated use**. ⚠ That last part is deliberate — a word you normally write in kana will not turn into kanji just because you picked it once.
  - **Single kanji are learned too** (0.8.398): single-character confirmations were not learned before, so **the kanji you use most could stay at the back**. Single kanji and katakana are now learned (single hiragana and symbols such as 「の」「、」 still are not — they would fill the front of the candidate bar with particles).
  - **Words that share the reading are listed properly** (0.8.297 / 0.8.298): typing とく now also offers **説く / 解く / 溶く** next to 得 and 特 (likewise きく → 聞く / 効く / 聴く, みる → 見る / 診る / 観る). Before this, some words the dictionary knows never showed up in the candidate bar at all, so there was no way to pick them. Pick one once and it is learned and moves up next time.
  - **More candidates** (0.8.298): the bar used to stop at 16 candidates, and ⚠ **the more you used it, the more the learned entries pushed the rest out until some words could no longer be converted at all** (that is why とく offered no 説く). The cap is now 48, and conversions of the exact reading you typed sit **outside that cap**, so they show up no matter how much learning has piled up. The candidate bar **scrolls to the right**.
  - For **katakana**, tap the katakana candidate in the candidate bar.
- The **face-switch key** at the bottom left moves to the next face (`ABC` for Latin, `12` for numbers).
- **Flicking the ⌫ (delete) key**: flick left to **delete the previous word at once**, flick right to **delete the entire line being typed**. ⚠ It now behaves the same **while the built-in keyboard is used as your OS input method** (fixed in 0.8.312). Two things used to go wrong there: **flicking mid-conversion committed the pending kana instead of dropping it**, and **on the terminal screen the flicks did nothing at all**.
- **Typing emoji (moved in 0.8.306)**: **flick down on ESC** to turn the kana keys into an **emoji pad**. Pick a category from the tabs, scroll, and tap to insert. ⚠ **The leftmost tab is most recently used**, so from the second time on you pick from there. The × at the top left goes back to the kana keys. **Faces and animals are there as whole Unicode blocks**, so an emoji you expect to find is not missing (0.8.301 — the table used to be hand-picked, and 13 glyphs including 😌 had fallen out). ⚠ Up to 0.8.305 the top half of the space key was a 😀 key, which left **the most-pressed key at half its size**; space is whole again and emoji moved onto the ESC flick.
- **Pasting what you copied (0.8.278)**: **flick up on the ESC key** and the same area becomes a **paste pad**. ⚠ So that the flicks are discoverable, **a small 📋 sits above the ESC label and a small 😀 below it** (0.8.279 / 0.8.306 — the same meaning as the flick characters printed in the corners of the kana keys). **Hold ESC down** and "▲📋 ▼😀" floats right above the key; flick up or down from there. Copied text is listed newest first; tap to insert it. ⚠ **Pasting closes the pad and takes you back to the keys** (0.8.395 — before that it stayed open and the × in the top-left had to be pressed after every paste). The emoji pad stays open, since emoji are usually typed several in a row. ✕ removes one entry, 🗑 clears all. ⚠ Entries are captured when you **copy first, then open the keyboard** (an Android rule: a keyboard may only read the clipboard while it is up). Clips marked as sensitive by password managers are never kept.
- **The same flicks work on the `A` (latin) face (0.8.362)**: on the latin face too, **flick up on ESC to paste, down for emoji** — the same finger movement as the kana and number faces. ⚠ **The latin ESC shows no 📋 / 😀 marks** (that face is a grid of plain keys and the marks would change its look), and holding it pops nothing up, so **the movement is the only thing to remember**. ⚠ Before this there was **no way at all** to open the paste pad from the latin face: the seat the entry key needs is taken by the face-switch key in Japanese.
- ⚠ While either pad is showing, **⌫ ⏎ ␣ ◀ ▶ still work**, so you can delete what you just pasted or hit return. The 😀 / 📋 tabs at the top of the pad switch between emoji and paste.
- **Pads close with the keyboard (0.8.307)**: close the keyboard with emoji or paste still open and **the next time it opens you get the usual kana keys**. ⚠ Until now it came back exactly as you left it, so a reopened keyboard could be showing the emoji pad with no kana keys in sight. The **face (kana / ASCII / numbers) is still remembered** as before.
- Note: this is a simple dictionary-based conversion, so it isn't as smart as Gboard — but words you use are learned and start appearing near the top.

### When you want the phone's standard keyboard
- Tap the **"あ" button in the toolbar** to switch to your usual phone keyboard (Gboard, etc.).
- With the phone's keyboard, **the text you're composing (before you confirm) now appears inline at the terminal cursor**. You can see the in-progress state of Japanese conversion or predictive input, instead of characters only showing up after you confirm.

---

## 5. Common operations

| What you want | How |
|---|---|
| Copy text | **Long-press** the screen → drag with your finger to select → "Copy" button (trailing blanks are trimmed and each row gets a **line break**) |
| Select just one word | **Double-tap** the screen (0.8.420). `/usr/local/bin/z2attach`, `root@192.168.10.20` and `~/.bashrc` come out **whole** (even when the line wrapped in the middle of them). `src/main.kt:42:` stops at the `:`, so you get the file name alone. Japanese is cut at word boundaries. Drag the ends afterwards to widen the selection. ⚠ On top of an app that reads the mouse (one you can operate by tapping), the double-tap goes to that app instead — while you are scrolled back through history it selects as usual |
| Magnify while selecting | While selecting, a **magnifier** appears above your finger |
| Select beyond the screen | While selecting, move your **finger to the top/bottom edge** → it auto-scrolls so you can keep selecting |
| Paste | The **📋** button in the toolbar. **When the text has line breaks**, a bar shows "3 lines …" first so you can look before pressing Paste (a single line still goes straight in). **Double-tap** it to open the **clipboard history** and pick a past copy to paste (pasting never rewrites the system clipboard, so it won't "copy what you just pasted"). **Picks from the history that contain line breaks get the same confirmation bar** (0.8.250). **Sensitive copies (the ones shown as dots), e.g. from a password manager, now land in the history too** (0.8.314). ⚠ Those rows alone are marked **🔒 and clear themselves after 30 seconds** — if the phone's clipboard still holds the same value at that point, it is emptied too (if you have copied something else since, it is left alone) |
| Use text copied in another app | Just come back to this app — **the clipboard content at that moment is added to the history** (pick it from the 📋 double-tap). Android only lets an app read the clipboard **while it is in the foreground**, so copies made while this app was in the background are picked up as a single entry when you return |
| Scroll up/down | Drag with one finger. You can also **grab the scrollbar on the right edge** (it follows your finger from the moment you touch it). Use **↓** at the bottom-right to return to the latest. ⚠ **While a full-screen app is open** (a pager, an editor, a "full transcript" view, …) there is no terminal-side history to go back into, so your drag is **delivered to that app as ↑ / ↓** (0.8.393 — before that, a finger did nothing at all in full-screen apps) |
| Make text bigger/smaller | **Pinch** with two fingers (spread/squeeze) |
| Add a terminal | The tab **+** (terminal) / **🖥** (GUI desktop) |
| Look at another tab while the GUI installs | **Go ahead.** The install keeps running in the background, and coming back picks the display up where it is (0.8.341. Before that, returning asked "install the GUI?" a second time, and answering "cancel" there **took the running install down with it**) |
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

### Showing a guide (0.8.314)

Settings > Maintenance > **"Show a guide"** puts the steps for using a bundled sample macro on cards above the terminal.

- The order they appear in is the order to follow. **Tapping a card runs that one line** (anything half-typed is thrown away with `Ctrl-C` first, so nothing mixes in).
- **The ✕ on the right drops a step you do not need** without sending it. When every card is gone the guide closes.
- Cards without a command (turn a setting on, install a prerequisite package) are just to read; tapping one removes it.
- Each row is two lines: **the macro's name and what it does**. Available: `watch-basic` (react to charging and headsets) / `battery-alert` (warn me when the battery drops below a % I pick) / `daily-report` (read out battery and connection every morning) / `otp-clip` (copy one-time codes from notifications) / `otp-sms` (copy one-time codes from SMS) / `unknown-call` (note calls from numbers I do not have) / `remind` (remind me with a notification) / `rss` (get notified about new feed items and read them) / `qr` (hand something over as a QR code).
- **A step that needs a value of yours asks first** (feed URL, polling interval, time of day, battery threshold, the text for a QR). It will not send an empty answer — this keeps the example values from being registered as they are.
- ⚠ **`watch-basic` registers two triggers** (`event:power_*` for charging, `event:headset_*` for headsets). The app does the waiting, so it runs the moment you plug or unplug — no resident server needed. The last step is `Z2_WHEN_EVENT=power_connected sh …`, which **pretends charging just started** so you can check it.
- Chosen from a GUI tab, the guide opens **after switching to a terminal tab** (it needs somewhere to type).

### Stopping when you have used too much data (0.8.388)

Settings > Resident servers & automation > **"Data limit"** stops **z2term's traffic** once **the whole phone's** usage for this period reaches the amount you set. You choose the limit, the day counting restarts, and whether Wi-Fi is counted.

⚠ **One permission is needed, once.** Reading the whole phone's usage requires "usage access". Tap **"Open usage access settings"** in that section and switch z2term on in the list. **Nothing is stopped until you do** (the screen says so in red).

- **The limit takes a slider or a number.** The slider is for eyeballing it; the **"Or type it exactly (MB)" field** lets you match your contract (4500 MB and the like).

- What stops: **SSH connections, and downloads of the OS image, GUI packages and app updates**. SSH sessions already running are cut too (the reason is written into that terminal first).
- ⚠ **Only z2term stops.** Other apps keep working — an app can only cut off the whole device by occupying the VPN slot, and z2term does not go there.
- ⚠ **Your home network is never cut off.** `192.168.x.x`, `10.x.x.x`, `localhost`, names like `nas.local` stay reachable even at the limit (they cost no mobile data).
- **Nothing stops while you are on Wi-Fi** (default), and only mobile data is counted. Turn "Leave Wi-Fi out of it" off to count both and stop regardless of the connection.
- ⚠ **Traffic leaving from inside the Linux side (`apk`, `curl`, `git`…) cannot be stopped** — Android gives an app no way to cut off only its own processes. It **is counted**, so the limit is still reached.
- ⚠ Some devices will not report usage even once access is granted, and then **nothing is stopped** (blocking on an unreadable meter would leave you with no way out). The screen says which case you are in.
- ⚠ With two SIMs the figure **covers both** (an app cannot tell the lines apart).
- Reaching the limit notifies you **once per period**. Counting restarts on the day you chose.

### Taking your setup with you (for a new phone or a reinstall)

Settings > Maintenance > **"Take it with you"** writes your current setup to a single file.

- Included: **settings, SSH connections, snippets, automation rules, macros**
- Not included: **the OS image** (hundreds of MB — a reinstall brings it back) and logs
- In it: **settings, SSH connections, snippets, automation rules and macros**, plus **your theme, tile assignments, icon drawings, dictionaries and what the keyboard has learned** (0.8.380). Out of it: the **OS image** (hundreds of MB — a reinstall brings it back), logs, and **home-screen widget assignments** (they are keyed by the number the launcher hands out, so restoring them would point at a different widget).
- **SSH passwords and keys are left out by default.** Tick the box to include them and you will be asked for a **passphrase** (without it the backup cannot be restored, so pick something you will remember). In that case **all data except the manifest used to show the item counts is encrypted**, including snippets, macros, automation rules and keyboard learning (0.8.449).
- When restoring, you see **what and how many** will be added before deciding. Nothing you already have is deleted; only matching items are replaced.

**It can also make one on a schedule** (0.8.386). On the same screen, turn on **"On a schedule"** and pick a **folder, an interval (daily / weekly / monthly), a time and how many generations to keep**.

- The newest few are kept and older ones go automatically. ⚠ **Only the ones it made are removed** — a backup you created by hand survives even in the same folder.
- ⚠ **SSH passwords and keys are not included.** Including them automatically would mean keeping the passphrase that opens them on the device. To take secrets with you, create one by hand as before.
- **Set a passphrase and the files it writes are encrypted** (0.8.452). SSH secrets still stay out, but snippets, macros, automation rules and what the keyboard learned go in as you wrote them — worth doing if the folder syncs to the cloud. ⚠ Forget it and they cannot be restored. ⚠ The passphrase is **never stored inside the backup**, so enter it again on a new device. Left empty, nothing is encrypted, exactly as before. The field is masked by default; use Show only when you need to check it.
- A good day passes quietly; **only a failure is notified** (a daily "it worked" notice trains you to skip the day it did not). When it last succeeded is shown on the same screen.
- **"Back up now"** writes one on the spot, so you can check the setup without waiting for the middle of the night.
- ⚠ Choosing a different folder, or revoking access on the device, stops the writing. You will be notified — **pick the folder again** when that happens.

### Creating an SSH key in the app

In 📜 > **Connections**, add an SSH connection and set auth to public key: a **"Create a key (ed25519)"** button appears.
Press it and the key is made, with **copy / share / add to this device's sshd** right there.
Give the **public** key to whoever runs the server you connect to (the private key never leaves this device).
The field for pasting your own private key is still there. Its contents are masked by default and can be toggled with Show/Hide.

The host field accepts DNS names, IPv4 and IPv6 literals (with or without surrounding brackets); IPv6 is displayed as `[address]:port` so the port is unambiguous.

In an SSH destination’s editor you can add **FTP, SMB, WebDAV, VNC and RDP** services. After saving, each gets its own button outside SSH/SFTP and opens either the shared file browser or a screen tab (VNC / RDP). The default is a local port forward through that SSH destination. Set the service port and, optionally, a local port; leaving the local side blank chooses a free port automatically. FTP passive data ports are forwarded automatically for each transfer. Clearing “SSH port forwarding” connects directly to the SSH destination’s host, not the service-specific host, and first warns that SSH encryption will be lost. WebDAV supports HTTP/HTTPS; SMB supports SMB2/3 with SMB1 disabled. Plain `http://` WebDAV works too (0.8.452; before that the app blocked every cleartext HTTP request, so choosing HTTP always ended in “failed to list”).
⚠ **Delete on a destination, and the ✕ on a service or a port forward, now ask first** (0.8.452). Each sits right next to Edit, and a mistap used to take the host, user and password with it. Deleting from the terminal (`z2-ssh` and friends) is not intercepted — a typed command is explicit already.

In the SFTP / FTP / SMB / WebDAV file screen, both Android Back and the top-left arrow move up one folder. At the root, they ask before closing the connection and returning to the terminal. Tapping an image opens a **full-screen preview** that supports pinch zoom and drag pan (0.8.479); text previews remain selectable and scrollable.

**The same screen can also show the files on this device** (0.8.474). The tabs at the top switch between "Server" and "This device". Start by picking one folder on the device with "Choose a folder". **That choice is remembered, so uploads no longer send you out to the system file picker every time.** You can send a single file or a whole folder. Tap a name to look inside text files and images right there (files that are too large, and other kinds of files, do not open).

### Open a remote machine's screen (VNC, 0.8.418)

Every SSH host in 📜 → **Connections** has a **[VNC]** button. It opens **that server's desktop in a new
tab** — the machine over there, not the Linux inside the app.

Before using it, fill in two fields under **✎ (Edit)** for the host:

- **VNC port** — display `:1` is **5901**, `:2` is 5902 (`5900 + number`). "Screen sharing" on
  Windows and macOS is usually **5900**.
- **VNC password** — **not the SSH password**; it is the one set on the VNC side.
  ⚠ **Only the first 8 characters count** (that is how VNC works). Leave it empty for a server that
  asks for no password.

Once it is up, it behaves like the app's own GUI tab: two fingers to zoom and pan, three fingers to
scroll, and the same keyboard.

### Open a Windows desktop (RDP, 0.8.459)

**[RDP]** sits in the same place as VNC. In 📜 → **Connections** → **✎ (Edit)** → "Services via this SSH
host", press **+ RDP** and the destination gains an RDP button.

Three things go in:

- **Service host** — the address of the machine **as seen from the SSH server**. If it is the machine
  you SSH into, leave it as `localhost`.
- **User / password** — the login on the far side (**RDP requires them**).
- **Domain** — only for a machine joined to a domain. Leave it blank otherwise.

**Leave the port at 3389 and leave SSH port forwarding on.** Pressing the button connects over SSH
first and opens the desktop through that tunnel, so the desktop never has to be reachable from
outside — being able to SSH in is enough.

**The first time, a dialog shows the fingerprint of the host's certificate.** It works like an SSH host
key: press "Trust and connect" and it is remembered. It stays quiet after that, and only asks again if
the fingerprint ever changes.

⚠ **The far side must require Network Level Authentication.** On Windows, turn on the Network Level
Authentication option for Remote Desktop. If it is off, you get a message that says exactly that
rather than a bare "could not connect".

⚠ **RDP is still being built.** **0.8.478 puts a Windows screen on the phone** (the RDP 8 graphics
pipeline plus RemoteFX). The mouse and keyboard work too (0.8.476). **Rotating the phone or resizing
the split rebuilds the remote desktop at that size** (0.8.480); ⚠ the screen goes blank for a moment
while it is rebuilt. VNC deliberately does not do this: there you are looking at **a real screen that
is already running**, and resizing it would change someone else's environment. An RDP connection
creates its own session every time, so nothing is left behind on the peer.

**The peer's audio plays on the phone** (0.8.481), with nothing to configure. ⚠ It stays silent if the
host is set up not to redirect audio. Sound drops out when the link is congested, but **the screen and
your input never stall for it** — audio is never waited on.

**Files travel by copy and paste too** (0.8.482), though the two directions differ slightly.

- **Phone → peer**: copy a file on the phone, then paste (Ctrl+V) on the remote screen. It arrives as
  an ordinary file copy.
- **Peer → phone**: copying a file on the remote screen **saves it into the `z2term` folder inside
  Downloads** — you are never asked where to put it. ⭐ It also goes onto the phone's clipboard, so
  **apps that accept a pasted file take it directly**; apps that do not can still open it from their
  own file picker under Downloads → z2term.

⚠ Whole folders are not sent (only the files inside). ⚠ A transfer that breaks is deleted rather than
left half-written. ⚠ Files that arrive while the app is in the background are still saved, but they do
not reach the clipboard — Android only lets the foreground app write to it.

**Copied text travels both ways** (0.8.475). While you are looking at a VNC or RDP screen, text you copy on the phone reaches the far side, and text copied there arrives on the phone. Japanese text survives the trip.

**📜 → Connections works from the GUI screen too** (0.8.475). It used to be hidden, so reaching another server meant going back to a terminal tab first. Double-tapping 🔅 for the brightness bar works there as well now.

- **The white arrow is the cursor you control** (0.8.427). Its position survives leaving the tab and reconnecting. **It is the only arrow on screen** (0.8.431; before that the pointer drawn by the server sat next to it and you saw **two**).
- The default is **relative mode**: the cursor moves by the distance your finger moves. Press **🖱 in the GUI toolbar** to switch to **absolute mode**, where the cursor jumps to the place you touch (0.8.431; 🖱 lights up in absolute mode, and a green ring appears at the arrow root). ⚠ Through 0.8.430 this lived on a **double-tap of 📜**, which is visible nowhere on screen and has nothing to do with a command list, so it is now **a button of its own, shown only on GUI tabs** (never on terminal tabs; hide it under ⚙ Settings › Display › Toolbar).
- **A single tap is a left click** and **a double tap is a double click**, as before.
- **A right click is a press and hold** (0.8.431). Keep your finger still and **a green ring sweeps around the arrow tip; the right click fires the moment it closes** (**0.5 s**, with no need to lift your finger; 0.8.438). The ring itself waits 0.25 s before appearing, so slow pointer movement is less likely to be mistaken for a hold. Move while the ring is running and it is cancelled, leaving you with plain cursor movement.
- ⚠ **Tapping or dragging shows no ring**. **The ring only appears once the touch is clearly not a tap** (after 0.25 s) and then closes over the next 0.25 s (0.8.438) — it does not flash on every tap.
- To **drag** (move a window, select a range), **double-tap and keep moving**. ⭐ **Move straight away — no pause needed** (0.8.436). ⭐ **And no time limit** (0.8.435): holding the second tap still never ends the decision, so you can take aim first. ⚠ **Holding the second tap for a right click is gone** — the press-and-hold covers it, and it was getting in the way of dragging.
- ⚠ **A single tap's left click reaches the server 0.3 s after you lift** (0.8.436). Sent any sooner, a drag that follows looks to the server like **a double click that was then dragged** (through 0.8.435 you had to pause after a double tap for a drag to work). ⭐ **The on-screen cursor still follows your finger instantly** — only the button press waits. The next tap flushes it immediately, so tapping in quick succession never loses a click.
- To switch the remote Japanese input method, place **Half/Full** (or Convert, Non-convert, Kana or Eisu) anywhere in the custom key-layout editor. If the remote uses `Ctrl+Space` or `Super+Space`, place that modified key instead.

- ⚠ **The server decides the size of the screen.** z2term never resizes it to fit your frame — it may
  well be a screen somebody is sitting in front of. If it looks small, pinch to zoom.
- ⚠ **A server that only listens to itself will not accept you** (the default on most Linux boxes).
  Open a terminal tab, run `ssh -L 5901:localhost:5901 <host>` to forward it, and point the host
  field at **`127.0.0.1`**.
- When it fails, the middle of the screen tells you **what to fix** (nothing listening / wrong
  password / a password is needed / an unsupported method).

### Grouping the commands you use most (0.8.387)

A **group bar** sits at the top of the **snippet tab in 📜**.

- **`+ Group`** makes a shelf ("daily", "git", …); tapping it lists only the snippets on that shelf. **"All"** brings everything back.
- Which shelf a snippet sits on is chosen in the **Group field inside ✎ (edit)**. With a group open, "+ New" creates the snippet **already on that shelf**.
- **Tap the name of the open shelf again** (it carries a `✎`) to rename or delete it. ⚠ **Deleting a shelf never deletes the snippets on it** — they go back to "Ungrouped" and stay listed under "All".
- You can still **reorder with ≡** while a shelf is open. ⚠ Doing so leaves the order of snippets on other shelves untouched.
- Shelves travel in the backup, so a new phone gets the same shelves back.

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

**The first time you open Arch**, the tab prints "🔑 Setting up the pacman keyring" and takes tens of seconds (0.8.316).
That builds the keys used to verify package signatures; it happens once and needs no network. Let it finish
(Ctrl-C is fine — the next tab retries). ⚠ Until it completes, `pacman` fails to install **anything** with
`error: required key missing from keyring` (the GUI install and `sshd` stop for the same reason).

---

## 6.5. Running `claude` (Claude Code)

z2term runs inside a custom ptrace-based engine (z2root). The `claude` distribution
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

**What to type (making your home PC able to reach the phone)**

1. First **add your PC as a connection** (host = the PC's IP, port = 22, user = your login on the PC).
   ⚠ These are **not** the phone's details: this feature has **the phone dial out to the PC**.
2. Connect once normally and accept the host key (a resident tunnel cannot show that prompt).
3. Edit that connection → **"+ Add"** under port forwarding → pick **-R** on the left and fill in:

   | Field | Value | What it means |
   |---|---|---|
   | on that PC | `127.0.0.1` : `65152` | the entrance appears on the PC |
   | to here | `127.0.0.1` : `65152` | whatever arrives there goes to this device's `sshd` |

   `65152` is **this device's `sshd` port** (whatever you passed to `sshd -p`). Both sides read
   `127.0.0.1` because it means "from the PC itself" and "into the phone itself" —
   ⚠ **neither is the other machine's IP**.
4. Turn the toggle below on and save.

From the PC you then go in via **the PC's own `127.0.0.1`** (no need to find the phone's IP):

```sh
ssh -p 65152 root@127.0.0.1
```

⚠ If you used to connect to `192.168.x.x`, the PC's `known_hosts` treats this as **a different
host**, so it asks once.

Once at least one forward exists, a **"Keep this tunnel running"** toggle appears. With it on, the
**forwards survive closing the SSH tab** (they are treated like resident servers and come back after a reboot).

- **`-R` only makes sense together with residency** — if you need a tab open on the phone to get in,
  you did not need remote access in the first place.
- **Connect once from the SSH tab first so the host key is trusted.** A resident tunnel cannot show a
  confirmation dialog, so it **refuses to connect** to an unknown host rather than trusting it silently.
- If the link drops it reconnects on its own after 5s, 10s, … up to 5 minutes.
- ⚠ `-R` makes this device reachable from the other end. Turn it on only when you need it.

**⭐ A side effect worth having on its own: one resident tunnel stops the phone from
"disappearing" off your Wi-Fi (0.8.367).**

A phone that **sends nothing for a while lets its radio drop into power save, and other machines
stop seeing it**. Even with `sshd` resident, `ssh` or `git push` from your PC fails with "no route
to host" and then fixes itself a few minutes later — that is this. **It is not an app bug but the
Wi-Fi chip's power saving**; the CPU is awake the whole time, even with the screen off.

A resident tunnel **sends a small greeting (a keepalive) every 10 seconds**, so the phone never goes
quiet. Measured: the share of time it was unreachable went from **37% to 1%**. It does not matter
where the tunnel points (whatever host you already use is fine).

- ⚠ With low-power mode on (Settings → Automation → process protection) the greeting stretches to
  **60 seconds**. That setting asks for battery over reachability, so expect the effect to fade.
- ⚠ If a `-R` forward is refused because the port is still in use, the forward is shown with a `✗`.
  **This is common right after a reconnect** — the other end has not released the old port yet. It
  is retried **every 30 seconds**, so the `✗` clears itself if you wait.

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

Settings are split into **9 groups** (Display / Keyboard and input / Linux environment / Resident servers and automation / App lock / Maintenance / Developer / How to use (Tips) / About this app), and **tapping a heading opens or closes it**. The open/closed state is **remembered even after you close the app**, so you can keep the groups you use often expanded.

> **How to use (Tips)** (0.8.399): double taps, long presses, flicks — the gestures that **show nothing on screen** are collected here. That toolbar buttons have a second function, how to close and reorder tabs, the ESC and ⌫ flicks, scrolling inside a GUI app, that typing `z2term` lists the built-in commands, and that an AI can write your macros. These are things you would never run into by accident, so open it once.

| Setting | Description |
|---|---|
| Theme | Color scheme (9 options) |
| Font | Display typeface (4 options, with preview) |
| Font size | 4–32 (also changeable by pinching) |
| Scrollback lines | How many lines you can scroll back through |
| Toolbar | **Choose which buttons appear above the terminal.** The real buttons are laid out; tap to remove or bring one back. A removed button's position is remembered. ⚙ is always rightmost and cannot be removed. If you remove 🔅 screen-on lock or 🔒 keep-alive, a switch for it appears in this section |
| Toolbar / tab position (landscape) | **Left / Right** — which edge the two vertical columns stand on in landscape (0.8.433; left by default) |
| Distro | Alpine / Ubuntu / Arch / Kali |
| Login shell | zsh / bash / sh — **the same shell is used for the terminal tab, SSH logins and the GUI's inner terminal** (the distro's `/etc/passwd` login shell is updated too). If the chosen shell is not installed in that distro, the default shell is used as before. **It starts out as bash** (0.8.400; it used to be zsh, but the bundled OS ships no zsh, so you ended up in a shell you never chose) |
| Shell prompt | **Pick a sample, tweak it on the spot, write it to the config file** (0.8.364). The first row picks the shell (sh / bash / zsh), the second picks a sample (sign only / user@host / arrow / rounded / square / Kali / ribbon), and **the box below fills in with what will be written**. Edit it freely — colours, order, whatever — then press Apply. The destination depends on the shell: `~/.ashrc` (sh) / `~/.bashrc` / `~/.zshrc`, and **the name shown above the box is exactly where it goes**. ⚠ **Only the part between the z2term markers is written**, so your own `alias` and `export` lines stay. Opening it with `vi ~/.bashrc` from the terminal shows the same thing, and editing it there is fine too (the settings box picks that up next time you open it). ⚠ **New tabs pick it up** — tabs already open keep the old prompt. Remove takes out just the marked part and restores the distro default. The samples are meant to be **usable as they are** (two-line box-drawing frames, an `❯` that turns red when the last command failed, Kali's `┌──(user㉿host)-[~]`, a coloured ribbon joined by a wedge). The wedge is powerline's `` (U+E0B0), built inside the rc itself (`ARROW_RIGHT=$'\ue0b0'`, or `printf` for sh) so no raw glyph ever sits in the file. ⚠ **Fira Code and JetBrains Mono both carry it** (verified by reading the font tables); only IBM Plex Mono does not, so if you use that font and see a square, change the escape to `\u25b6` (▶) in the box. **Clock at the right edge** adds a dimmed timestamp to the right of any sample, so scrolling back shows when each command was typed. ⚠ It **does not count the terminal width**, so rotating or splitting the screen never misaligns it |
| Keyboard style | Simple / 4-direction flick |
| Keyboard faces | Built-in **Japanese, letters and numbers**, plus every saved custom layout, each have their own ON/OFF switch (0.8.413). Move any face up or down to freely choose the order used by the next-face key. At least one face always remains enabled. |
| Key layout (your own) | Duplicate the built-in **letter, Japanese or number face**, or any selected custom layout, then edit the copy. The editor is a fixed page inside Settings, with the same full-width back bar, system-bar spacing and always-visible Save footer (0.8.413). A tap selects one key; turn on Multiple selection only when applying width, appearance or gestures to several keys. Width accepts intermediate decimals such as `1.` and uses the same ticked 0.1-step slider as Settings. Each custom layout is an independent face whose use and next-face position are set above. Invalid structures cannot be saved, a missing escape action is warned, and layouts **travel with settings export/import**. |
| User dictionary | **Add your own words from a file** (0.8.280). "Choose a dictionary file" picks a text file on the phone and its words start appearing in conversion straight away. One word per line, in **either of two layouts** (0.8.282): `reading /candidate1/candidate2/` (the SKK dictionary format, e.g. `ずーたーむ /Z2Term/z2term/`) or `reading<TAB>word<TAB>part-of-speech` (what dictionary tools export, e.g. `あいぎょう→愛楽→名詞`; a fourth note column is fine). Lines starting with `;` or `#` are treated as notes and skipped. ⚠ **Write readings in hiragana.** UTF-8 and EUC-JP files are both read. Imported files are listed so you can remove one when you no longer want it. ⚠ Up to 8MB per file. If no words could be read you are told immediately, so a format mistake is not silent |
| Special key bar (with the OS keyboard) | Whether the **ESC, TAB, CTRL and arrow keys** appear above your phone's own keyboard while it is selected (0.8.279). Turn it off and they are not shown. The built-in keyboard never had them, so it is unaffected |
| Japanese IME learning history | The phrases the converter has learned. Search and delete them one by one, or clear them all |
| Built-in keyboard elsewhere | **Offer the built-in keyboard to the rest of the system as an input method** (0.8.276). Once enabled, the app's own text fields (snippets, SSH, SFTP, settings) and other apps all get **the same keyboard and the same Japanese conversion**. "Turn it on in Android settings" → enable z2term keyboard in the list → "Switch keyboard" to pick it. ⚠ **Enabling and picking are yours to do** — Android does not let an app switch the keyboard on your behalf. The terminal itself is unaffected: it keeps using the keyboard drawn inside the app. This setting lives in the **"Keyboard, input and language" group** (moved there in 0.8.277; it used to sit under "Resident servers and automation", where it was hard to find. 0.8.279 added "language" to the group name, since the display-language switch is in the same group) |
| Overlap with the 3-button bar | When used as an OS input method, the bottom row of the keyboard **overlapped the 3-button navigation bar (back / home / recents) and could not be pressed**; fixed in 0.8.279. The keyboard is now lifted by the height of the bar. Devices on gesture navigation get no extra gap |
| Which face it opens on (ASCII / Japanese) | **Only when used as an OS input method does it reopen on the face you last used** (0.8.295). Switch to the Japanese flick face with 「あ」 and close it, and the next time it opens in another app it is still on the Japanese face (press ABC and the next one is ASCII again). ⚠ **The built-in keyboard on the terminal screen always starts on ASCII, as before** — people start typing ASCII in a terminal and Japanese in other apps, so only the other-apps side remembers. There is no setting to turn this on (each switch is remembered automatically) |
| Keyboard position (landscape) | Left / bottom / right — effective only in landscape |
| Side keyboard width (landscape) | Slider 280–700 dp |
| Keyboard width (bottom) | Slider 40–100% of the screen width (100% fills it, less centres the keyboard; portrait and landscape are remembered separately, 0.8.431) |
| Keyboard height (landscape / portrait) | Slider 200–500 dp (remembered separately per orientation) |
| GUI audio | Play sound (video, etc.) in the GUI (desktop) — only when ON |
| GUI terminal | Pick which terminal app is used inside the GUI desktop |
| Language / 言語 | **System** (default) / Japanese / English / Simplified Chinese / Traditional Chinese (switches instantly; Simplified since 0.8.424, Traditional since 0.8.426). ⚠ The default follows **the phone's own language setting** (0.8.363 — before that the app started in Japanese whatever the phone was set to). Picking one pins that language regardless of the phone. **The terminal follows too** — the help and messages of the `z2-*` commands use the same setting. ⚠ **Anything not translated yet appears in English** (0.8.422; before that it stayed in Japanese). The Japanese flick keyboard is offered only in Japanese (other languages get the ASCII and numeric faces). ⚠ **No Chinese/Japanese conversion engine is bundled for other languages** — switch to your OS input method for that |
| Disable install timeout | Wait for OS / GUI downloads to finish completely |
| Confirm before downloading | Show a confirmation dialog before fetching a distro / GUI |
| SSH connection helper | Steps for connecting from a PC, with the IP shown |
| Storage access | Permission to use `/sdcard` |
| External storage (SD card) | When on, an inserted SD card is made visible from inside the OS (`/sdcard_ext`) |
| Background process protection | A way into the battery-optimisation exemption, plus instructions for turning off phantom process killing with `adb`. This is what keeps long background work alive. **Low-power mode lives here too** (moved from the servers tab in 0.8.309) |
| Reset terminal | Returns the app to **the state it had when first opened**. Only one terminal tab is left (other terminal tabs and GUI tabs are closed), and that terminal goes back to its initial state: running programs are terminated, screen and scrollback cleared. Tapping it opens a confirmation. **Saved servers, settings, snippets and the OS itself (installed packages and files you made) are not removed** |
| Clear cache | Sweeps the package/build caches that pile up inside the OS (pacman, apt, apk, `~/.cache`, …) plus the app's temp files. Tapping it opens a confirmation that **itemizes what and how much** will be deleted. Installed packages, settings and files you made are not removed |
| Delete OS data | Removes an installed OS (Alpine / Ubuntu / Arch / Kali) entirely to free storage. Tapping it opens a confirmation |
| Resident servers | Register any server (sshd / http / smb, …) as a **start command** and keep it running in the background. Turn on "auto-start on boot" and it **launches right after the device boots — without opening the app**. Stop them all from the "Stop servers" notification action or this screen. See below |
| Notification detection | Grant the OS "notification access" and turn it on, and incoming notifications are appended to `~/.z2term/notifications.jsonl` (a generic hook). **The output format is fully customizable** (a template of `{time}` `{app}` `{title}` `{text}` … ; presets: readable / one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file instead of appending at the end. That mode reads and rewrites the whole file per entry, so **once the log passes 10MB the settings screen shows a warning** (turn it off before it gets slow, or trim the file from the terminal). What you record / filter / serve is **up to you on the terminal side** (e.g. `tail -f`, or serve it with a resident server). Since the side new entries arrive on changes with the append direction, **the "command to read it" shown in settings follows that setting** (`tail -f` when newest is at the bottom, `watch -n 1 head -n 20 …` when newest is at the top). Turn **"Save notification log"** off to keep detecting without writing anything to the file (detection only). A notification that is re-posted many times is **logged only once**. ⚠ **Where several messages arrive together in one notification** (chats do this), **every new one in that round is logged** (0.8.358; before that only the last one, shortened, survived — **messages went missing and the survivor was cut off**). The same message is never logged twice: it picks up where it left off. Default off, fully local |
| SMS detection | Turn it on and **grant the SMS permission**, and incoming SMS are appended to `~/.z2term/sms.jsonl` (fields: `time` `from` `body`; format customizable). **Vs. notification detection**: Android 15+ **redacts OTP-bearing notifications** before handing them to ordinary apps, so SMS OTPs may not be readable via notifications (same for MacroDroid etc.). SMS detection reads the **SMS body directly**, bypassing the redaction, and works **even while locked**. For auto-copy, register `z2-macro install otp-sms.sh` as a resident server. Non-SMS OTPs (e.g. authenticator-app notifications) are out of scope. Default off, fully local |
| System event detection | Turn it on and screen on/off, unlock, charge start/stop, battery low/okay, Wi-Fi connect/disconnect and **Bluetooth earbuds connect/disconnect** are appended to `~/.z2term/events.jsonl` (a generic automation trigger; sibling of notification detection). **The output format is customizable** (`{time}` `{event}` `{level}` `{ssid}`; presets: one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file (in that mode, **once the log passes 10MB the settings screen shows a warning**). Build automations like "when battery drops below 20%…" or "when charging starts…" **terminal-side** (e.g. a script reading `tail -f ~/.z2term/events.jsonl`; **the "command to read it" shown in settings follows the append direction**). Default off, fully local, shows an ongoing notification while active (Wi-Fi SSID is blank without location permission). **The log has no size cap** (it keeps appending into one file; clean it up from the terminal, e.g. `: > ~/.z2term/events.jsonl`) |
| Unlock-failure detection | With this on **plus device admin activated**, lock-screen unlock **failures/successes** are recorded to `~/.z2term/events.jsonl` as `unlock_failed` (`level` = consecutive failure count) / `unlock_succeeded`. It's the **detection hook for anti-theft macros** like "after N wrong passwords, notify / record location / sound an alarm". No photo or upload is built in — you build the reaction as a macro. Device admin is used only to watch failure counts (`watch-login`); it **does not lock or wipe** your device remotely. Default off, fully local |
| App lock | **Asks who you are before the screen appears** (0.8.421). Fingerprint or face, and **your screen lock (PIN/pattern) works too** — so a wet finger or no enrolled biometrics never locks you out. ⚠ **It cannot be turned on if the phone has no screen lock set** (there would be nothing to check against, and the app could never be opened again). Choose when it locks: **at once / after 30s / after 1 min / after 5 min / on launch only** (default: after 30s, so short round trips like copying from another app don't ask). **Starting the app always locks, whatever you choose here.** While you are away it also hides the **thumbnail in the recent-apps switcher**. ⛔ **It hides the screen, nothing more** — sessions, resident servers and `z2-session attach` keep running while locked (stopping them would break anything left working overnight). It is for handing the phone to someone without them seeing the screen, not for keeping intruders out |
| Reset settings | Returns **every setting to its defaults** — theme, font, keyboard, execution engine (back to the default **z2root**), saved servers, unlocked hidden features and so on. Tapping it opens a confirmation (cannot be undone). **The OS itself (installed packages and files you made) is not removed** |

### Resident servers (run without opening the app)

Open ⚙ Settings → **Resident servers** → "Manage servers", or the **Servers** tab of the toolbar's 📜, to keep any server running (both show the same screen).

1. Tap "**+ New**". Picking a preset (SSH / HTTP / SMB / FTP / VNC) fills in a start command (edit it freely).
2. Install the server itself (`sshd` / `smbd`, …) into that OS beforehand from the terminal — the app does not bundle them.
3. Tap "**Start**" to launch all servers now. Each row shows its state (`running`, …) and is **auto-restarted** if it exits.
   - **You can add, edit and delete servers while they stay resident** (applied within a few seconds). Previously, running something you had just added meant restarting everything — taking the other servers down with it. ⚠ **Deleting with ✕ asks for confirmation** (0.8.452).
   - The **▤ button** on a row shows what that server printed (its log). When something won't work, the reason is usually there. "Clear log" empties it at any time.
   - If a server keeps dying and restarting, the row shows "**restarted N× / last exit code**". A number that keeps climbing means it fails to start every time — open ▤ and read the log.
   - **Grab ≡ and drag a row up or down to reorder** the list (the same gesture as snippets). The order is remembered, and running servers keep running while you rearrange them.
4. Turn on "**Auto-start on boot**" and the servers **come up automatically after the device boots, without opening the app**.
5. To stop, use the "**Stop servers**" notification action or "**Stop**" on this screen (stops them all at once).

Note: ports below 1024 (e.g. 80) cannot be opened without root — use a high port (e.g. 8080).
Note: excluding the app from battery optimization makes it less likely to be killed in the background (link inside ⚙ Settings).
Note: if battery use bothers you, turn on "**Low-power mode**". ⚠ **You will find it under ⚙ Settings -> Automation -> Background process protection** (moved from the servers tab in 0.8.309). It lets the device sleep deeply while the screen is off to save battery, but incoming connections may be delayed or dropped during that time (battery over reachability). **Since 0.8.269 the same setting also covers the toolbar's 🔒 background keep-alive** (until then, 🔒 kept the device awake even with resident servers in low-power mode). ⚠ **It also decides how quickly automation (`z2-when`) reacts** — automation never wakes the device itself; it rides on whatever a resident server or 🔒 is already keeping awake. ⚠ Conversely, with neither of those running, this setting changes nothing either way.

Note: **0.8.268 fixes battery drain caused by residency alone.** Until then the servers were checked once a second, and each check spawned several small processes inside the device — including for servers that were not even running. That added up and kept the phone permanently warm, so the **check interval is now 5 seconds** and the residency notification is only rewritten **when the running count changes**. In exchange, turning a server on/off — and adding, editing or deleting one — takes up to **5 seconds** to apply.

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
→ Long-press first, then drag with your finger. **If one word is all you need, double-tap is quicker** (0.8.420). To reach the edge, move your finger toward the top/bottom edge of the screen and it auto-scrolls. Drag near the end of the selection to change its range.

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
| `z2-notify [-h] [-n name] [-c text] [-b label]... "title" "text"` | Post a notification (text optional; `-h` shows a pop-up banner). **`-b` adds reply buttons** (up to 3); pressing one appends a `notify_action` line to `events.jsonl` so a macro can read the answer. **`-c <text>` adds a "Copy" button** (0.8.335) that puts that text on the clipboard. ⚠ For a macro running in the background this is **the only reliable way to hand something to the clipboard**: since Android 10 an app that is not in front cannot write it, so `z2-clip set` is dropped silently |
| `z2-ask [-t sec] [-H hint] [-d default] "question"` | **Ask a question and get the answer** (0.8.267). It arrives as a notification with a **reply field**, so it can be answered from the shade without opening the app. The answer goes to stdout: `name=$(z2-ask "Branch name?")`. Dismissing it, or the timeout (5 min by default), **fails without printing anything**, so `|| exit` expresses "give up if there is no answer". If you only need a choice from a list, `z2-notify -b` fits better |
| `z2-toast "message"` | Toast (short message at the bottom of the screen) |
| `z2-share "text"` | Hand text to Android's share sheet |
| `z2-open <URL or path>` | Open a URL or file in the default app |
| `z2-clip get` / `z2-clip set [text]` | Get / set the clipboard (set reads stdin if no argument). ⚠ **Writing only works while you are looking at z2term** (or while z2term is the input method you use) — since Android 10 a `set` from a macro running in the background is dropped silently. For macros triggered by calls, SMS or notifications, use the `z2-notify -c` copy button instead (0.8.335) |
| `z2-battery` | Show battery level / charging state (JSON) |
| `z2-vibrate [ms]` | Vibrate (default 200ms) |
| `z2-say <text>` | Speak text via the device's text-to-speech (reads stdin if no argument) |
| `z2-torch [on\|off\|toggle]` | Turn the flashlight on/off/toggle (default toggle; prints the resulting state) |
| `z2-media [playpause\|play\|pause\|next\|previous\|stop]` | Control media playback (default playpause) |
| `z2-volume <up\|down\|mute\|unmute\|N\|N%>` | Adjust media volume (prints the resulting current/max) |
| `z2-sensor [light\|accel\|proximity]` | Read a sensor once as JSON (light/accelerometer/proximity; default light) |
| `z2-state [key]` | **Current device state** as JSON; with a key, just that value (`screen` `locked` `idle` `charging` `plug` `level` `temp` `wifi` `ssid` `ringer` `airplane` `headset` `bt_audio` `volume` `volume_max`). E.g. `[ "$(z2-state charging)" = "true" ]` |
| `z2-screen keepon <1h\|30m\|90s>` | **Stop the screen turning off by itself, for that long** (`keepon off` puts it back early, `status` shows what is left). It changes the OS-wide screen timeout, so **it holds with the app in the background**. ⚠ Not the toolbar's 🔅, which only lasts while the app is on screen. The original value is always written back at the deadline (even if the app is killed or the device reboots). Max 24h in one go. **Needs "modify system settings"** (Settings › screen timeout) |
| `z2-tile set <1-12> <macro.sh\|command>` | **Put a macro or command on the quick-settings panel** (`list` / `clear <1-12\|all>`, `-l` for the label). Tap to run, tap again to stop (same deal as the widget buttons). The tile looks "on" while it runs (the colour comes from the OS). A locked device is asked to unlock first. ⚠ **There are exactly 12 slots** (raised from 4 in 0.8.294; Android fixes the number at build time, so there are spares). **A slot you have not assigned anything to stays out of the quick-settings list** (since 0.8.271, **with nothing assigned no tile is listed at all**; before that slot 1 remained as a signpost). **Assigning a slot asks you right there whether to put it on the quick settings panel** (0.8.355, Android 13+). The dialog carries **the name and icon you just assigned**, so you are not guessing on the edit screen. Say no and nothing is placed. ⚠ It only appears while z2term is in front, and **only one can be asked at a time** (a macro assigning two slots only asks about the first) — use **`z2-tile add <slot>`** for the rest. **`-i <drawing>` settles the drawing that dialog shows, in the same line** (0.8.357; e.g. `z2-tile set 4 backup.sh -l backup -i sync`). Names come from the `z2-icon sample` list. ⚠ An unknown name **assigns nothing at all** (a tile placed with the wrong drawing is harder to spot). ⚠ **A tile already on the panel does not need it** — `z2-icon` changes those on the spot; `-i` only matters *before* placement. ⚠ **Placing a tile is still your call** (Android does not let an app put its own tiles there). On the edit screen, look for **`z2term <slot number>`**: ⚠ **that list shows neither the name nor the icon you assigned** (Android gives no way to change them at runtime), so use `z2-tile list` to see which number is which. ⚠ `clear`-ing a slot also **removes that tile from the panel** (reassigning means placing it again). **A slot holding `z2-screen keepon` is special**: the "on" look means "the screen is still being held", **the time left is appended to the name** (e.g. "no sleep 60m"), and tapping releases it (the figure is read when the panel opens and does not tick while it is open). When turning something off is its own command, **`--off` puts both on one tile** (`z2-tile set 3 z2-torch on --off z2-torch off -l torch`): taps alternate between them and the tile looks "on" while it is on. ⚠ That on/off is **only what the app remembers**, so running `z2-torch off` in the terminal instead leaves the tile showing on. **A macro can take arguments** (`z2-tile set 2 'remind.sh ask' -l remind`; 0.8.275 — the **first word** decides whether it is a macro). Two slots on the same macro get the same label, so give them `-l`. ⚠ A name ending in `.sh` that is not in `~/.z2term/macros/` is **rejected when you assign it** (letting it through would make it a command, which is not found, and the tile would do nothing). Scripts outside the macro folder go in with a full path. **When a tap seems to do nothing, look at `~/.z2term/tile/run.log`** — failures never reach the screen |
| `z2-icon pick <notify\|1-12>` | **Put your own drawing on the status-bar and tile icons** (0.8.294). **A tile fills its own icon in when the assigned name gives it away** (0.8.299 — `remind.sh` gets a clock, `battery-alert.sh` a battery). Anything you set yourself wins and is left alone from then on. **To go back to automatic, `z2-icon auto <slot\|all>`** (it overwrites your drawing and re-picks). **`z2-icon list` tells you which drawing is on which slot, by name** (0.8.300 — `auto` means it was picked for you, `custom` that you set it; `z2-icon list -p` prints the drawings as well). Pick one from the list by number (`z2-icon sample` lists them; `z2-icon sample <target> <name>` sets one directly). **A drawing of your own can be named and kept in that list** (0.8.300): `z2-icon save <target> <name>` adds it, after which it can be chosen by number or name exactly like a shipped one, so you can put the same drawing on another slot. `z2-icon forget <name>` drops it from the list — whatever you already put on a target stays where it is. To draw your own, `z2-icon edit <target>` opens `$EDITOR` on the grid — save it and it applies at once (`.` ` ` `0` `-` `_` leave a cell empty and **anything else fills it**, so draw with whatever you can see). Blank space around the drawing is ignored and it gets centred, so you need not fill every line. **The grid is 24 / 48 / 64 across** (0.8.379). The status bar shows these about 24px across, so 24 dots are plenty there — but **a tile is drawn much larger**, and there 24 dots show as steps. `z2-icon grid <24\|48\|64>` sets the grid new drawings are made on (24 by default; with no argument it prints the current one), and `z2-icon scale <target> <24\|48\|64>` lays a drawing you already have out on another grid. **The outline is smoothed on the way out** (0.8.382), so a drawing made on 24 dots still comes out smooth on a tile and the grid is not something to worry about day to day (`z2-icon show` prints the drawing as you drew it; the tile is smoother). `grid` and `scale` are for when **you** want to draw finer: `scale` halves the diagonal steps as it lays the drawing out, so what you have becomes the base to draw on. ⚠ **24 → 48 is the cleanest** (exactly double; 24 → 64 smooths up to 48 and lays the rest out, so some steps remain). ⚠ Flat areas and lone dots are only made thicker (apart from four corners being rounded, the shape you drew is untouched). ⚠ Laying one out on a smaller grid **drops thin lines**. ⚠ **A bigger grid does not make a bigger icon** — fill the grid, or the drawing comes out smaller than the one it replaced. From a file it is `z2-icon set <target> <file>` (`-` for stdin), `z2-icon show <target>` prints the current one, and `z2-icon clear <target\|all>` puts the built-in icon back. The targets are **`notify`** (one drawing for every notification this app puts out) and **slots 1-12** (one drawing each). ⚠ **There is no colour** — Android repaints these icons in a single colour of its own (tiles change colour between on and off), so only the shape is yours. ⚠ The status bar shows them about 24px across, so there detail finer than 24 dots is lost (what `show` prints is what appears; a 48 or 64 drawing is printed with two cells folded into one character so the line does not wrap on a phone). Which slot is on which grid is the 4th column of `z2-icon list`. ⚠ **Three things cannot be changed**: the icon in the quick-settings **edit** screen (where you drag the tile from), the **file-picker root icon**, and the **launcher icon** — Android fixes those at install time. Placed tiles and posted notifications do change |
| `z2-alarm at\|daily HH:MM [name]` | **Time trigger**: writes an `alarm` event into `events.jsonl` at that time (`in 5m` / `list` / `cancel <id\|name\|all>` too). Unlike cron it fires during Doze. **Whether it lands on the minute is in `z2-alarm list` as `exact`** (0.8.333): `true` means on the minute, `false` means Doze only offers a slot every 9-15 minutes, so a phone left with the screen off can be that late (battery saver too). What flips it to `true` is **Settings › Apps › Z2Term › Battery › Unrestricted** (battery optimisation off). ⚠ The app never asks for the "alarms & reminders" permission — Android grants exact alarms to apps exempt from battery optimisation, and where it is not exempt scheduling quietly falls back (nothing is dropped). The same applies to `z2-when time:` and the deadline of `z2-screen keepon`. ⚠ **Before 0.8.302 the clock inside the distro was fixed to UTC**, so a wall-clock time like `18:30` was scheduled off by the zone offset (9 hours in Japan). From 0.8.302 it follows the device clock — **re-create anything you scheduled with an older build** |
| `z2-session list\|new\|send\|capture\|attach\|close` | **Drives this app's own tabs.** `list` shows them (index, id, name, marks: `*`=visible, `!`=busy, `?`=not started, `@`=attached), `new [name]` adds one, `send <target> "text"` **only inserts** into that tab (add `--enter` to actually run it), `capture [target]` pulls the on-screen text, `close <target>` closes it. `<target>` is the index from `list`, an id, or a tab name. E.g. ``n=$(z2-session new build \| cut -f1); z2-session send "$n" 'make -j2' --enter`` |
| `z2-session key <tab> <key>...` | **Send keys to that tab** (0.8.311). `z2-session key 2 C-c` for Ctrl+C, `M-x` for Alt+x, plus specials like `F5` `Up` `Home`; several at once as `key 2 F5 Up Home`. Anything else goes as bytes: `key 2 --raw '\x1b[A'`. ⚠ **`send` cannot deliver Ctrl+C** — it is a paste, so you only get the characters `^C`. ⚠ Shift-ed keys such as `C-S-a` are **refused**: a terminal cannot tell Shift apart, so it would be the same as `C-a` (`S-Tab` is fine — that one it can tell apart) |
| `z2-session attach <tab>` | **Stay connected to that tab** (0.8.366). After `z2-session attach 2` you just type into it like any terminal (Ctrl+C works, so do full-screen programs). **Leave with `Ctrl+]`** (any time). `~.` at the start of a line works too (as in ssh; type `~~` for a literal `~` there). ⚠ **Over SSH, use `Ctrl+]`** (0.8.370) — `~.` is eaten by the ssh in front of you and **drops the SSH session**. To send a literal `Ctrl+]` to the tab, type `~` at the start of a line and then `Ctrl+]`. ⚠ While attached the tab follows **your** window size — on the phone it wraps wrongly and looks broken, but it goes back when you leave. ⚠ **The tab on the phone stays live**, so what you type shows up there too. ⚠ A notification appears while you are attached (it keeps the app from being killed mid-session). ⛔ **You cannot attach a tab to the one you are typing in** (0.8.419) — its own output would come back as input forever with no way to stop it, so it is refused with a message. Attaching back to A from inside an A→B attach is refused for the same reason |
| `z2-usb list` / `z2-usb allow [number]` | **Use a USB device connected to the phone from Linux** (0.8.425). Run `list`, then `allow`, and approve Android's permission sheet. The number is optional when there is only one device. An ordinary USB-A-to-USB-C adapter works if it carries **data** and the phone supports USB Host/OTG. Permission lasts until the device is unplugged; run `allow` again after reconnecting it. ⚠ This covers dynamically linked programs on z2root that use ordinary `open` / libusb. Statically linked programs and programs issuing the `openat` system call directly bypass the shim and are not covered |
| `z2-server list\|start\|stop\|status <server>` | Start / stop **a resident server you registered**. ⚠ A daemon started straight from a rule runs **outside the residency frame**, so it stops answering once the screen is off; starting it here puts it inside. `<server>` is the index from `list`, an id, or the name from the app. E.g. `z2-when wifi:connect run 'z2-server start sshd'` |
| `z2-when <trigger> run <cmd>` | **Automation hub.** Auto-run a command on charge / battery / time / device events (see "Automation hub" below). Also `list` / `remove <id\|all>` / `on\|off <id>` / `log <id>`. **To narrow it down**: `if=` (all of them) / `if_any=` (any one of them, 0.8.372); **to do something else when it does not match**: `else=` (0.8.372). E.g. `z2-when charge:start run ~/.z2term/macros/backup.sh` |
| `z2-macro list\|install <name>` | **Bundled macro samples** into `~/.z2term/macros/` (`diff` / `show` / `run` / `dir` too) — a starting point for your first macro. **`list` shows the state of each one** (`new` / `same` / `differs`; 0.8.332). ⚠ `install` **never overwrites** (your edits are yours). That means a fixed sample never reaches a copy you already have, so `install` tells "the same thing is already installed" apart from "yours differs from the bundled one", and in the latter case points at `z2-macro diff <name>` (look first) and `z2-macro install -f <name>` (replace with the bundled one). ⚠ **`differs` does not mean "out of date"** — your copy can be the one that is ahead (an extension never folded back into the app), so always read the `diff` before you use `-f`. Bundled: `watch-basic` / `battery-alert` / `daily-report` / `otp-clip` / `otp-sms` / `unknown-call` / `remind` / `rss` / `rss-open` / `qr`. On install it also tells you **how that script is meant to be run** (drive it with `z2-when` / assign it to a widget button / register it as a resident server). ⚠ **No bundled sample belongs in a resident server** (0.8.338; they all run once and exit from `z2-when` or a button, so residency both restarts them every time they finish and burns battery while idle) |
| `z2-update [--check] [--keep] [--dir <folder>]` | **Replace z2term itself with a newer version** (0.8.371). It checks GitHub Releases, and if there is a newer one, downloads the APK and takes you to **the install screen**. ⚠ **The last tap is yours** — Android has no way for an app to replace itself silently. ⚠ The first time it needs "Install unknown apps" (it says so if it is missing). `--check` only looks, `--keep` leaves the APK behind, `--dir` changes where it lands (by default it goes inside the app and is deleted once the update goes through). Settings > App info has the same button and the same two settings. ⚠ **Installed from F-Droid or a store? It refuses** — update it there. e.g. `z2-when time:daily=03:00 run 'z2-update'` |
| `z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] …` | Fire an arbitrary Android Intent (launch apps, open settings, set alarms, … see `docs/en/MACRO-GUIDE.md`) |

> Combine "trigger (event detection) → decide (shell) → action (z2-*)" to automate your phone (macros). See **`docs/en/MACRO-GUIDE.md`** for how — you can also feed it to an AI and have it generate the macro for you.

### Get reminders as notifications

**There is no calendar in the app.** Instead a bundled sample (`remind`) wires together the time
triggers, notifications and tiles that already exist into **a reminder that fires with the app
closed** (0.8.275).

```sh
z2-macro install remind                  # install it
sh ~/.z2term/macros/remind.sh setup      # once, up front (registers the hooks and the tiles)
```

**Add one**

```sh
remind.sh 30m take pills               # once, 30 minutes from now (90s / 2h too)
remind.sh 18:30 take out the bins      # once, at the next 18:30 (tomorrow if it passed)
remind.sh 07/30 19:00 fireworks         # month/day (next year if it passed)
remind.sh 2030 07/30 19:00 the day      # with a year
remind.sh 203007301900 the day          # digits only (07301900 = MMDDHHMM too)
remind.sh daily 07:00 weigh in          # every day
remind.sh weekday 09:00 standup         # Mon-Fri
remind.sh weekly tue 20:00 recycling    # that weekday only
remind.sh monthly 25 10:00 rent         # that day of the month
remind.sh yearly 07/30 19:00 birthday   # that month and day
remind.sh every 19:00                   # "every" alone works: this is daily
remind.sh every wed 19:00               #   weekly / every 15 19:00 -> monthly
remind.sh every 07/30 19:00             #   yearly (the next word decides)
```

**Forgot the syntax?** `remind.sh help` prints all of it. **To walk it from the install step, use Settings > Maintenance > "Show a guide" > "Remind me with a notification"** (0.8.314): step cards appear above the terminal and tapping one runs that line.
⚠ Through 0.8.313 `remind.sh help` was also seeded as a 📜 snippet, but **without the macro installed it only printed "not found"**, so it was removed in favour of the guide above.
⚠ **If you get `remind.sh: command not found`, that tab still has the old PATH** (0.8.314 puts the macro
directory on PATH out of the box on every OS, including SSH logins and the GUI's terminal). **Open a new tab**, or run `export PATH=$HOME/.z2term/macros:$PATH` in that tab.

**See and cancel**: `remind.sh list` (⏰ = one-shot / 🔁 = repeating / ✔ = already fired) and
`remind.sh del 2` (`del all` clears everything). ⚠ **You can also remove one from the "list" tile**
(0.8.286): tap it, the list appears as a notification, press **[Delete]** and answer with the number
(or `all`). Repeating ones also appear under 📜 → the
"Automation" tab, where you can toggle them or ▶ run one to try it.

**When it fires**: the notification carries **[Done] [+10min] [+1h]**, so you can snooze from the
shade with one tap. Pressing Done on a repeating reminder does not cancel tomorrow's.

**Add one without opening the app**: `setup` prepares two quick-settings tiles ("remind" and "list").
⚠ **You place them yourself**, from the pencil/edit screen of the quick settings panel. ⚠ **Tapping a
tile closes the quick settings panel** (0.8.284): while it is open, the question would be buried
underneath it and you could not answer. Tapping
"remind" asks "Remind you about what?" and "When?" in a notification reply box.
⚠ **If "When?" is written in a way it cannot read, it says why and asks again** (0.8.283). Your previous
answer stays in the reply box, so you only have to fix it. After three tries it gives up and tells you
"Not set" (before this it just ended quietly, so the tap looked like it did nothing).
**A successful one is announced too**, as an "⏰ Reminder set" notification with the plan and the text.

- **Days work too** (0.8.285): `tomorrow` and `Nd` (`3d`). **Leave the time out and it keeps the current
  time of day** (no invented default like 9am). ⚠ The list shows the **real date** (`07/31 18:30`) —
  keeping "tomorrow" would read wrong once the date rolls over.
- **Anything written the wrong way is not scheduled** (0.8.283): `18:70` (out of range), `daily` with no
  time, `1.5h` (not a whole number), an unknown weekday. You get the reason, in the terminal too.
- Firing can be **a few minutes late** (the booking follows the battery-saving Doze schedule). Not for
  anything that needs to be on time to the second.
- Reminders survive a reboot.
- For how it is built, see "5-9. Worked example: remind yourself with a notification" in
  `docs/en/MACRO-GUIDE.md`.

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

**3. Give the ones you must not miss their own notification** (optional, 0.8.334)

Put one feed or word per line in `~/.z2term/rss/important.txt` (part of a URL or part of a title both work). Anything matching gets **a notification of its own**.

```sh
echo 'example.org' >> ~/.z2term/rss/important.txt
```

The summary notification only carries 3 lines in its body, so a busy feed updating at the same time pushes the one that mattered out. Splitting it off keeps it visible. ⚠ At most **5 per run**, so a too-broad word cannot bury the shade under every article.

**4. Let the notification's button open that very article** (optional)

```sh
z2-when event:notify_action run 'case "$Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${Z2_WHEN_EVENT_NAME#rss:}" ;; esac'
```

The URL is in the notification's name, so the article you pressed is the one that opens — however many notifications are on screen.

**5. Read from a widget** (optional)

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

### Hand something to another device as a QR code (0.8.308)

A sample for passing a long URL or a config **to another device without retyping it**: draw the code on screen and let the other device's camera read it.

```sh
z2-macro install qr                  # install it
apk add libqrencode-tools            # Alpine (Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode)

qr.sh "https://example.com"          # encode a string and draw it
qr.sh -f notes.txt                   # encode the contents of a file
z2-clip get | qr.sh                  # encode what you just copied
qr.sh -o ~/qr.png "text"             # save a PNG (nothing is drawn)
qr.sh -t "text"                      # print blocks instead of an image
qr.sh -h                             # the full help
```

- ⚠ **You install `qrencode` yourself** (once per tab). If it is missing, the script prints the install command for that tab and stops.
- ⚠ **The image only shows inside a tab of this app.** On a terminal that cannot show images (over `ssh`, say) you get gibberish, so use `-t` there.
- ⚠ **To scan with a camera, prefer the image or the PNG.** Blocks (`-t`) can leave gaps between rows depending on the font: readable to you, not to the camera.
- Long input is **split into several codes at line breaks**, numbered `[1/3]`. Scan them in order.
- If it looks squashed, adjust the ratio: `Z2_QR_ASPECT=0.45 qr.sh "text"` (smaller = taller; default 0.5).

### Automation hub (`z2-when`)

Auto-run a command **when you start charging / the battery drops / a set time arrives**. No need to `tail` events yourself — you just **declare** the rule and it runs, even with the app closed and across reboots.
**However, every trigger except time, SMS and notifications (charging, battery, Wi‑Fi, sensors, new files, device events) only works while "detection" is on** (Settings › keep-alive & automation): Android does not deliver those events to an app that isn't resident. SMS needs the receive-SMS permission, and `notify:` needs notification access.

**Rules may start servers too** (e.g. `z2-when wifi:connect run 'sshd --lan'`). A server started that way **keeps running** after the rule itself finishes (fixed in 0.8.253 — before that it was taken down the instant the run ended, so the log said "listening" while nothing answered). ⚠ It only survives **while the app is alive**; register anything that must stay up permanently as a **resident server**.

**You can also build them on screen** (0.8.272). In 📜 → the "Automation" tab, **+ New** lets you pick a trigger from a list and type the command. Tap **✎** on an existing rule to see the full command and its filters (`if` / `cooldown` / `between` / `days`) and edit them. When the command points at a single script, **its contents are shown too** (edit the script itself in the terminal). Rules created with `z2-when` in the terminal can be edited the same way — both read the same files (`~/.z2term/when/*.rule`).

**Conditions are built by picking, not typing (0.8.373).** Under "When to run it", choose **all of them / any one of them** and add rows with **+ Add a condition**. The item (`connected to Wi-Fi` / `battery level (%)` / `Wi-Fi network name`, …) comes from a dropdown, so a typo can no longer leave you with a rule that never fires. Boolean items take "yes / no", name items "is / is not", number items "more than / less than" plus a value. **Every row shows its current value** (0.8.374). ⚠ **`volume` is not a percentage** — it is the device's own step count (0-15 on many phones), and without this you can write `volume > 77`, a condition that can **never** hold (hit on a real device). With `now: 0 / 15` on the row, picking a threshold is obvious. `battery level` shows `now: 74`, `Wi-Fi network name` shows what you are on right now (`now: (none)` when off). It is read once when the editor opens — no polling, no extra traffic. Right below, **"When it does not match"** takes a command to run instead (empty = do nothing). ⚠ **A condition written in the terminal that the screen cannot represent** (`screen=on`, or a rule carrying both `if` and `if_any`) is **shown as text** — the screen must never reinterpret and rewrite what you wrote.

**What you built, also as a terminal command (0.8.375).** At the bottom of the editor, **"The same rule as a terminal command"** shows the `z2-when` line for exactly what is in the form, rewritten as you pick things. Tap it to copy: pasting it in the terminal makes the same rule again (handy for putting the same automation on a second device). ⚠ It is **shown, not edited** — changing that line changes nothing — and it stays hidden until the trigger and the command are filled in.

**Rules can be named** (0.8.303). "Name" is the first field of the form. Give a rule a name and it becomes the heading in the list (the trigger stays underneath in small type), and recent fires use it too — so several rules on the same trigger, say `event:screen_on`, are no longer indistinguishable. **Leave it empty and the trigger is the heading, as before.** The name is display only; it changes nothing about when a rule runs. From the terminal, put it right after the trigger and before `run`: `z2-when time:daily=07:00 name='Morning report' run ~/.z2term/macros/report.sh` (quote it if it contains spaces).

⚠ **A command is always one line.** A rule file holds one item per line, so **a newline throws away everything after it** — a command pasted across two lines gets cut short and the rule stops working. Since 0.8.272 both the screen and `z2-when` **fold newlines into spaces**, so this no longer bites, but for anything long it is safer to **put it in a script file and register that path**.

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
  - `net:online` / `net:offline` — when **a connection that works appears or goes away** (added in 0.8.264). `net:wifi` / `net:mobile` / `net:ethernet` fire when **the link in use switches to that one**. Unlike the Wi‑Fi triggers these **count mobile data** — "Wi‑Fi went away" alone doesn't tell you whether mobile picked it up or you're out of range. "Send everything once we're back online" and "stop when there's no service" go here. **Only works while "detection" is on.** Inside the command you get `Z2_WHEN_NET` (the link now) and `Z2_WHEN_NET_PREV` (the one before). ⚠ Going from Wi‑Fi to mobile doesn't change the fact that you're online, so `net:online` **does not fire there** (it looks only at what changed). ⚠ It waits until traffic really gets through, so it fires **a few seconds after the Wi‑Fi icon appears** (a connection you can't actually use isn't called "online").
  - `share:any` / `share:text` / `share:file` / `share:contains=<part>` / `share:ext=<ext>` — when **something is shared to z2term from another app** (added in 0.8.266). E.g. share a URL from the browser and have it downloaded in the background, or `z2-when share:ext=pdf run ~/.z2term/macros/pdf.sh`. **Works even with "detection" off.** Inside the command you get `Z2_WHEN_SHARE` (the text, or the path the file was taken into) and `Z2_WHEN_SHARE_KIND` (`text` or `file`). ⚠ **For a file, `Z2_WHEN_SHARE` is not a path but a string meant to be pasted into a shell** (`~` is left unexpanded, names with spaces come quoted, and several files are separated by spaces). `[ -f "$Z2_WHEN_SHARE" ]` is always false. `docs/en/MACRO-GUIDE.md` has a ready-made splitter under "⚠ The shape of what `share:` hands you". ⚠ What was shared **still goes on the input line as before** (writing a rule does not change that). ⚠ Sharing **opens z2term** — the share sheet targets a screen, so Android works that way.
  - `boot` — when **the device starts up** (added in 0.8.264; no `:`). E.g. `z2-when boot run 'sshd --lan'`. Like time and SMS, it **works even with "detection" off**. ⚠ It waits for start-up to complete after the first unlock (before that the app's files can't be read yet).
  - `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` — when an SMS arrives (any / sender matches / body contains / body has an OTP-looking code). **Needs SMS receive permission** (grant it via Settings › "SMS detection"). Inside the command you get `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`, and for `sms:otp` the extracted code in `Z2_WHEN_OTP`. Reading SMS directly avoids Android 15's OTP redaction.
  - `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far` — when you shake the device / ambient light (lux) crosses N up or down / the proximity sensor goes near or far. **These sensor triggers only work while "detection" is on**. Sensors cost battery, so only the sensors your rules use are turned on (none run if you don't use them). `shake` only reacts to a **firm shake** (deliberately set high so that walking around doesn't trigger it), and at most once every 3 seconds. Inside the command, `Z2_WHEN_SENSOR` names the sensor and `Z2_WHEN_LUX` holds the light level (for light).
  - `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` — **when a notification arrives** (added in 0.8.236). Handy for confirmation codes that don't come by SMS (email, authenticator apps), e.g. `z2-when notify:otp run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'` (you take it from the notification's "Copy" button — it runs in the background, so `z2-clip set` would not land). The command gets `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT`. Needs **notification access** (Settings > resident servers & automation > notification detection), and works even with notification logging turned off. ⚠ Things like phone numbers can arrive **wrapped in characters that never appear on screen** (Android adds them to pin down the reading order). z2term **strips them before handing the text over** (0.8.356), so a macro that recognises `$Z2_WHEN_NOTI_TITLE` by its shape as a number just works. ⚠ **Before 0.8.356 incoming numbers hit exactly this and were never caught** (the bundled `unknown-call.sh` read them as names; you do not need to reinstall the macro).
  - `file:new=<dir>` / `file:new=<dir>,ext=<ext>` — **when a new file lands in that folder** (added in 0.8.235; e.g. `z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh`). The command gets `Z2_WHEN_FILE` (full path) and `Z2_WHEN_DIR`. It fires **after the write finishes**, so it never grabs a half-copied file. Needs **"detection" on**.
  - `event:<name>` — **any device event, by name** (added in 0.8.226). Run **`z2-when events`** to list the names (~20: `screen_on`, `unlocked`, `headset_plugged`, `bt_audio_connected`, `ringer_silent`, `airplane_on`, `alarm`, `notify_action`, …). A trailing `*` makes it a prefix match (`event:ringer_*`), and `event:*` matches everything. Inside the command, `Z2_WHEN_EVENT` holds the event name.
    **The same rule will not fire twice within 10 seconds** (some events, like `screen_on`, happen often).
    Passive events (screen, charging, Wi‑Fi, …) need **"detection" on**, but `alarm` (set with `z2-alarm`) and `notify_action` (a notification button) **work with detection off**.
- **Typos are caught right away** (0.8.265): misspell a trigger — `z2-when net:onlien …` — and it **errors out instead of registering**. If it registered you would get a rule that sits in the list and never runs, with nothing to explain why.
- **Narrow a rule down with filters** (0.8.263): put them **right after the trigger** (before `run`) and they work the same way for **every** kind of trigger. They combine.
  - `if=<cond>` … only run when the **device is in that state**. Commas mean "and", a leading `!` negates. The conditions are exactly what `z2-state` shows (`wifi` `charging` `screen` `locked` `headset` `bt_audio` `airplane` `idle` / `ssid=` `ringer=` `plug=` / `level<30` `temp>40` `volume>0`). E.g. `if=wifi,!screen` (on Wi‑Fi with the screen off) / `if=ssid=Home` (only on your home network) / `if=level<30`.
  - `if_any=<cond>` … run when **any one of them** holds (0.8.372). The comma means "or"; the conditions themselves are the same as `if=`. Written together with `if=`, it reads "**all of `if`, and any one of `if_any`**". E.g. `if=charging if_any=wifi,ssid=Home` (charging, and on Wi-Fi or at home).
  - `else=<cmd>` … run this **instead** when the condition did not hold (0.8.372). E.g. `else='z2-notify "skipped: no Wi-Fi"'`. ⚠ **Only `if` / `if_any` reach it.** A run skipped by `between` (window), `days` or `cooldown` runs **nothing at all** — a rule you switched off for the night must not send you a notification at 3am.
  - `cooldown=<duration>` … **do not run again for that long** (`30s` / `10m` / `2h`; a bare number means minutes). Useful for triggers that come in bursts, like `sensor:shake`.
  - `between=HH:MM-HH:MM` … only **inside that window**. It may **wrap past midnight** (`22:00-07:00`); the start time is included, the end time is not.
  - `days=mon-fri` … only on **those days**. Lists work (`sat,sun`) and so do cron-style numbers (`1-5`, where 0 and 7 are Sunday).

  ```sh
  # Back up when charging starts on the home network — at most once an hour
  z2-when charge:start if=ssid=Home cooldown=1h run ~/.z2term/macros/backup.sh
  # Weeknights only, and only while the screen is off
  z2-when time:every=30m if=!screen between=22:00-07:00 days=mon-fri run ~/.z2term/macros/nightly.sh
  # 7am daily: sync on Wi-Fi or at home, otherwise just say so
  z2-when time:daily=07:00 if_any=wifi,ssid=Home else='z2-notify "skipped"' run ~/.z2term/macros/sync.sh
  ```

  **Skipped runs are recorded too** — `z2-when fired` and the app's recent-fires list show `skip:if` / `skip:cooldown` / `skip:between` / `skip:days` (and `skip:if→else` when the stand-in ran instead), so instead of "it didn't run" you get **why** it didn't run. The **▶ (run once) button ignores filters** — it is there to try the rule out.
- **See and stop them from the app** (0.8.227): 📜 → the **Automation** tab lists your rules. Each row has an on/off switch, **▶ to run it once without waiting for the trigger**, **▤ for its run log**, and ✕ to delete (which asks for confirmation since 0.8.452). The **Pause automatic runs** switch at the top stops **every** rule from firing (nothing is deleted, and ▶ still works). Below the list, **recent fires** show what ran — and what was held back (`paused`), so a rule that seems dead is easy to explain. **Grab ≡ and drag** to reorder the list (0.8.249; the order is remembered and is **display order only** — it changes neither when rules run nor what triggers them).
  The same things work from the terminal: `z2-when pause` / `z2-when resume` / `z2-when fired`.
- **List / remove / toggle**: `z2-when list` / `z2-when events` (names usable with `event:`) / `z2-when remove <id>` (`all` for everything) / `z2-when on <id>` `z2-when off <id>` / `z2-when log <id>` (see the run log)
- Inside the command you can use `Z2_WHEN_TRIGGER` (which trigger fired), `Z2_WHEN_LEVEL` (battery level then), `Z2_WHEN_SSID` (the network for a wifi trigger), `Z2_WHEN_NET` / `Z2_WHEN_NET_PREV` (the link now / before, for a net trigger), `Z2_WHEN_SHARE` / `Z2_WHEN_SHARE_KIND` (for a share trigger), `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY` / `Z2_WHEN_OTP` (for sms triggers), `Z2_WHEN_SENSOR` / `Z2_WHEN_LUX` (for sensor triggers), and `Z2_WHEN_EVENT` (for event triggers; `alarm` and `notify_action` also set `Z2_WHEN_EVENT_NAME` / `Z2_WHEN_ACTION`) as env vars.
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
| `z2doctor` | **Self-check for when something doesn't work.** Lists version, permissions, detection and automation state; every `NG` line tells you what to do. It also shows **how things ended last time** (why they went away, 0.8.376), newest first: `app:` is the app itself, `tab:` is a terminal tab killed from outside (0.8.378), with the free memory at that moment (full history in `~/.z2term/exits.jsonl`). `z2doctor --clip` copies a report (SSIDs and IPs are left out) |
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
| `<command> --help` | **The long explanation for that command** (e.g. `z2-tile --help`, `z2-icon --help`). Available on every `z2-*` since 0.8.331. Subcommand-style ones (`z2-tile` / `z2-icon` / `z2-when` / `z2-session` …) also answer to `-h` and `help`. ⚠ The ones that take a sentence (`z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-say` / `z2-ask`) take **`--help` only** — `z2-toast help` has to keep showing "help". `-h` on `z2-notify` still means "banner" |

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
