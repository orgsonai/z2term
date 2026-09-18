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

1. Put the APK file (`z2term-0.8.630-alpha.apk`) on your phone.
2. Allow "Install from unknown sources" and install it.
3. Open the app.

**Linux is optional (0.8.594).** The first launch opens the Android system shell. Run `z2help` to see available commands. Notifications, torch, automation and edge panels work without installing Linux. When you need Linux tools, use the notice to open Settings › Linux environment and choose an OS to download. Tapping the pinned Settings notice scrolls to that section.

> ⭐ **Alpine opening neither a terminal nor a screen is fixed in 0.8.508** (it had been there since 0.8.425). The piece that bridges USB devices called into something Alpine's foundation (musl) does not provide, so **everything you ran inside Alpine stopped before it started**. Ubuntu / Arch / Kali were never affected.
**Once one Linux OS is installed**, **three small cards** appear above the terminal (post a notification / turn on the flashlight / open the reminder guide) — the first time only. (Before 0.8.339 they also appeared before an OS was installed, where tapping them did nothing, so they now wait until the install is done.) **Tapping one runs that line as it stands** — anything half-typed is thrown away with `Ctrl-C` first, so nothing mixes in. The ✕ on the right drops a card you do not want. Once all three are gone they never come back. To see them again: Settings > Maintenance > "Show the intro again".
That's all the setup you need.

> ℹ️ **See the release page for the APK size.** The Linux OS is not bundled into the APK or downloaded again during app updates. **Installing Linux requires a network connection** to download and verify the OS from its official site. Android integration commands need no OS download. ⚠ Up to 0.8.358 there was also a ~190MB build with the OS bundled (`full`); **0.8.359 dropped it and ships one build only** (it made you choose without offering anything beyond skipping that first download).

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

> **Reorder the buttons**: **long-press** a toolbar button to **drag it left/right and reorder**. A short description pops up above the button while held. The order is remembered. ⭐ **⚙ Settings › Display › Toolbar reorders them the same way** (0.8.509): long-press a chip there and drag it left/right. **Hold it against either edge and the row scrolls by itself**, so you can carry a button past what fits on screen in one go. That list holds **the buttons of both terminal and GUI tabs**, so you can set the GUI side without opening a GUI tab. The order is shared by both kinds of tab.

> **You can remove buttons you don't use**: ⚙ Settings → "Display" → **Toolbar** lets you tap to choose which buttons appear. The lit ones are shown. The order of a removed button is remembered, so bringing it back puts it where it was.
> ⚙ Settings is **always at the far right** — it never moves and cannot be removed.
> If you remove 🔅 screen-on lock or 🔒 background keep-alive, there would be nowhere left to toggle them, so **a switch appears in that same "Toolbar" section**.

---

## 4. Using the keyboard

Z2Term comes with its **own in-app keyboard**.

### Latin (ASCII) keyboard
- Tap letter keys normally.
- **⇧ (shift)**: tap once = Shift for the next key (uppercase for letters) / tap again = keep Shift held / tap again = release.
- **Flick down (swipe a key downward) = uppercase.** e.g. flick `q` down to get `Q`.
- **Flick up / left / right = symbols** (the small green characters are the hints).
- **Long-press to repeat**: letters, numbers, arrows, space, and `⏎` (return) repeat while held. `⌫` (delete) also repeats on long-press. Version 0.8.618 restores repeat flags for preset letters, uppercase letters and symbols. Saved custom layouts are retained.
- Press `CTRL`, `ALT` or ⇧ before the target key to send a modified key. Letter shortcuts such as `Alt+b` and `Alt+f` keep their existing encoding. Arrows, Home/End, Insert/Delete, PageUp/PageDown and F1–F12 carry combinations of Ctrl, Alt and Shift; Shift+Tab sends backtab. The built-in keyboard, physical keyboard, custom layouts and special-key bar share the terminal key table. Ctrl, Alt and one-shot Shift clear after the key is sent. The receiving application determines the action.
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
  - **One confirmation is enough for a boundary to stick** (0.8.493): ⚠ **some chunks previously never changed no matter how often you confirmed the same split.** They were being remembered, but where the dictionary happened to segment that reading cheaply, learning could not outweigh it — which is why the same phrasing had to be re-split with ◀ ▶ every single time. **Now one confirmation takes effect from the next time.** A chunk you have learned is reused even in sentences that share the beginning and differ later (a different ending on the same phrasing).
  - ⚠ **A learned chunk will not cut into the middle of another word**: after learning a short chunk, other words starting with that same reading still convert as before whenever the dictionary gets them right as a single word. Pushing learning harder would **float unusable conversions to the top**, so it is tuned to the strongest setting that avoids that.
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
- Tap the **⌨ button in the toolbar** to switch to your usual phone keyboard (Gboard, etc.).
- With the phone's keyboard, **the text you're composing (before you confirm) now appears inline at the terminal cursor**. You can see the in-progress state of Japanese conversion or predictive input, instead of characters only showing up after you confirm.

### When an external keyboard is connected (0.8.523)
- Plug in a Bluetooth or USB keyboard and **the in-app keyboard folds away by itself**, giving the screen back to the terminal.
- **You never lose the ability to type.** Keys from the attached keyboard go straight to the terminal (or to the GUI on a GUI tab).
- **To bring it back, double-tap ⌨** (or use the toggle bar above the keyboard). It stays out until the next time a keyboard is plugged in or unplugged.
- **Unplug the keyboard and it comes back on its own.**
- **Japanese works too** (0.8.529): type romaji on the external keyboard and it converts, using the same dictionary and the same learning as the on-screen keyboard.
  - **Toggle**: the **half/full-width** key (or **Shift + Space** if your keyboard has none). The kana, henkan and muhenkan keys work as well. ⚠ It **starts in ascii** — the first thing you type in a terminal is usually a command.
  - **How to type**: romaji → **Space to convert** → Space again for the next candidate → **Enter to commit**. Backspace steps back one character, **Esc drops what you were composing**, and ← → move the boundary being converted.
  - **Either romanization works** (`shi` and `si` both give し).
  - **The toolbar's ⌨ turns into "あ" while kana mode is on** (0.8.546). ⚠ Up to 0.8.545 the marker was drawn in the bottom-left corner of the screen, where it **overlapped the terminal's own text**; it now lives on the toolbar. No keyboard artwork is drawn (the keys are already under your fingers).
  - **The kana you are still composing appears inline at the terminal cursor** (0.8.546), so you can watch the conversion instead of seeing the characters only once they are committed.

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
| Send text or a file from another app | In that app choose **Share** → pick **Z2Term**. Text and attachments are saved together under `~/z2term-inbox/receipt-ID/`. Registered actions appear as choices; otherwise the body is inserted when present, or file paths for a file-only share. [Details](SHARE-WORKFLOW.md). It is **only inserted, never run**, so finish the command yourself and press ⏎ |

> When you launch the app it **always opens a single terminal tab** (previously open tabs are not auto-restored).

> **About the terminal log (⚪)**
> - Recording is **per tab**, and always returns to off when you reopen the app (so nothing keeps recording by accident). **Turn on "Record new tabs automatically"** and every tab you open is recorded from the start, so there is no ⚪ left unpressed (that setting is remembered and applies to tabs opened from then on).
> - What you get is an **ordinary text file**. Colors and screen-control codes are stripped, and progress output that rewrites one line (`50% → 75% → 100%`) leaves **only its final state** as a single line.
> - **Whatever appears on screen goes in as-is.** Keys, tokens and one-time codes that were displayed are recorded too (a password you only type never appears on screen, so it is not recorded). The button always stays lit while recording, so you can see it at a glance. A log you no longer want is a normal file — delete it with `rm`.
> - **"Timestamp every line"** (off by default) prefixes each line with a fixed-width `[2026-07-27 08:42:13] `, so you can trace when things happened. ⚠ It is **not applied to raw logs**, which must stay byte-for-byte.
> - **"Mask keys and tokens" is on by default.** It replaces name=value pairs such as `TOKEN=…`, the body of a pasted private key, and fixed-shape tokens like `ghp_` / `AKIA` with `[z2term:masked]`. ⚠ **It is not complete.** Only clearly recognisable shapes are covered; a secret in your own format stays in. **Always read a log before handing it to someone.**
> - `~/z2term-log/` is **visible to other apps** (it is treated like the rest of your home). As with any other file under home, don't keep there what you don't want seen.
> - Full-screen apps (the ones that paint by redrawing the screen) are not recorded by default, because flattening them does not produce readable text.

0.8.567 also fixes terminal history for TUIs that scroll text above a fixed input row. Text pushed off the screen top is retained and can be read with the existing swipe gesture. Rows already discarded before this fix are not recovered.

### Hints for common stumbles

When you hit one of the usual walls — `ping` not working, a port below 1024, `/sdcard` looking empty — **one line with the next step** appears at the bottom of the screen (it fades after a few seconds, or tap it to dismiss).
The terminal output itself is **never modified**; this is just an extra line elsewhere.
If you find it noisy, turn off Settings > Display > "Explain common stumbles".

### Showing a guide (0.8.314)

Settings > Maintenance > **"Show a guide"** puts the steps for using a bundled sample macro on cards above the terminal.

- The order they appear in is the order to follow. **Tapping a card runs that one line** (anything half-typed is thrown away with `Ctrl-C` first, so nothing mixes in).
- **The ✕ on the right drops a step you do not need** without sending it. When every card is gone the guide closes.
- Cards without a command (turn a setting on, install a prerequisite package) are just to read; tapping one removes it.
- Each row is two lines: **the macro's name and what it does**. Available: `watch-basic` (react to charging and headsets) / `battery-alert` (warn me when the battery drops below a % I pick) / `daily-report` (read out battery and connection every morning) / `otp-clip` (copy one-time codes from notifications) / `otp-sms` (copy one-time codes from SMS) / `unknown-call` (copy phone numbers from call notifications) / `remind` (remind me with a notification) / `rss` (get notified about new feed items and read them) / `qr` (hand something over as a QR code) / `md` (read Markdown).
- `edge-workspace` creates one sample panel with Notepad, Translation and Terminal tabs. The guide includes manual translation-command installation and bundled macro setup. [Steps](EDGE-MACRO-FORMS.md#one-sample-with-three-tabs).
- **A step that needs a value of yours asks first** (feed URL, polling interval, time of day, battery threshold, the text for a QR). It will not send an empty answer — this keeps the example values from being registered as they are.
- ⚠ **`watch-basic` registers two triggers** (`event:power_*` for charging, `event:headset_*` for headsets). The app does the waiting, so it runs the moment you plug or unplug — no resident server needed. The last step is `Z2_WHEN_EVENT=power_connected sh …`, which **pretends charging just started** so you can check it.
- Chosen from a GUI tab, the guide opens **after switching to a terminal tab** (it needs somewhere to type).

### Stopping when you have used too much data (0.8.388)

Command list > Servers > **"Data limit"** stops **z2term's traffic** once **the whole phone's** usage for this period reaches the amount you set. You choose the limit, the day counting restarts, and whether Wi-Fi is counted.

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

#### Reach a server through jump hosts (0.8.494)

**Servers you cannot dial directly are reachable now.** A destination's editor gained a
**"Jump hosts (-J)"** section. **+ Add** creates one hop (host, port, user, authentication);
press it again for a second hop, a third, and so on — **there is no limit on the number of
hops** (this is exactly what `ssh -J gate,inner` does).

Once there is at least one hop, **the route is shown on one line** underneath:

```
Route: ubuntu@gate.example.com:22 → admin@10.0.0.5:22 → me@target:22
```

⭐ **Reuse a connection you already saved.** **Import** at the top of a hop lists your saved
destinations; picking one copies its host, user and key or password into that hop.
⚠ **It copies, it does not reference** — deleting the connection you imported from will not
break the jump host, but if you change that connection's password you have to update the hop too.

Jump hosts apply to **the SSH shell, SFTP, every FTP / SMB / WebDAV / VNC / RDP service attached
to that destination, and resident tunnels** alike. You set them up in one place only.

⚠ **Each hop asks you to confirm its host key the first time** (once per hop). The confirmation
screen names the machine the key belongs to.

⚠ **Before using a resident tunnel through jump hosts, connect once to every hop.** A resident
tunnel cannot show the confirmation screen, so a single unapproved hop stops it from starting
(the reason appears in the list).

In an SSH destination’s editor you can add **FTP, SMB, WebDAV, VNC and RDP** services. After saving, each gets its own button outside SSH/SFTP and opens either the shared file browser or a screen tab (VNC / RDP). The default is a local port forward through that SSH destination. Set the service port and, optionally, a local port; leaving the local side blank chooses a free port automatically. FTP passive data ports are forwarded automatically for each transfer. Clearing “SSH port forwarding” connects directly to the SSH destination’s host, not the service-specific host, and first warns that SSH encryption will be lost. WebDAV supports HTTP/HTTPS; SMB supports SMB2/3 with SMB1 disabled. Plain `http://` WebDAV works too (0.8.452; before that the app blocked every cleartext HTTP request, so choosing HTTP always ended in “failed to list”).

**Connection QR button (0.8.604)**: QR is at the end of the action row, after SSH, SFTP and any added services.

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
host is set up not to redirect audio. ⚠ **Nothing played before 0.8.491** — this side was telling the
peer it wanted no audio, and was missing part of what a client must declare to be sent any. That was
never a problem on the remote end. Sound drops out when the link is congested, but **the screen and
your input never stall for it** — audio is never waited on.

**A whole folder can be handed over** (0.8.494). Separately from copying files one at a time,
**a folder on the phone can be opened directly from the far desktop.**

Turn on **"Share a folder"** under RDP in 📜 → Connections → **✎ (Edit)**.

- **Shared folder on this device** — leave it blank to use `/sdcard/Download/z2term`
  (**the same place files you receive land in**). Type a path only if you want somewhere else.
- **Share name** — the name shown on the far side. Blank means `z2term`.

On Windows it opens as **`\\tsclient\z2term`** — paste that into Explorer's address bar, or open
it under "This PC". **The far side can read, create, change and delete files there**, so anything
you want back on the phone just needs to be dragged into that folder.

⚠ **It is off by default.** The folder is visible to the far side only while it is on.
**Point it at a folder you are willing to hand over** — nothing outside that folder is visible.

**Files travel by copy and paste too** (0.8.482), though the two directions differ slightly.

- **Phone → peer**: press the **📎 button** in the GUI toolbar, pick your files (several at once is
  fine), then paste (Ctrl+V) on the remote screen. They arrive as an ordinary file copy.
  ⭐ **The 📎 button is new in 0.8.484.** It used to be "copy on the phone, then paste", but
  **many Android file managers keep a "copy" inside their own app**, where nothing else can see it.
  📎 always gets the files through.
  ⚠ The button appears **on RDP screens only** (VNC shares text alone). You can hide it under
  ⚙ Settings › Display › Toolbar.
- **Peer → phone**: copying a file on the remote screen shows **a small "📎 name and N more
  [Receive]" strip along the bottom of the screen**. ⭐ **Nothing is transferred until you tap it**, so
  copying a file to move it around on the remote machine sends nothing to the phone; ignore the strip
  and it goes away, or **close it right away with the ✕ next to [Receive]** (0.8.485). Tapping [Receive] **saves into the `z2term` folder inside Downloads** — you are
  never asked where to put it. ⭐ It also goes onto the phone's clipboard, so **apps that accept a
  pasted file take it directly**; apps that do not can still open it from their own file picker under
  Downloads → z2term.

⚠ Whole folders are not sent (only the files inside). ⚠ A transfer that breaks is deleted rather than
left half-written. ⚠ Files received while the app is in the background are still saved, but they do
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
- Register `sshd` (or `sshd --lan` to expose it to the LAN) under Command list → Servers and it keeps running without opening the app.
- When started as a resident server it automatically runs in **foreground (stay-alive) mode** even without `-D` (0.8.165; before that it was restarted every few seconds, so connections were refused or dropped shortly after connecting).

> Tip: `/usr/sbin/sshd` (OpenSSH) does not work with this app's mechanism. **Always type `sshd`**
> (a lightweight dropbear runs underneath).

---

### Keeping a tunnel running (get in from outside, bring a service here)

Editing a host in 📜 → "SSH / SFTP" lets you add **port forwards**, in one of three kinds.

⚠ **Up to 0.8.493, opening the connection from an SSH tab installed `-R` as `-L`** (fixed in
0.8.494). Turning **Resident** on always did the right thing, so anyone using these as resident
tunnels was unaffected.


| Kind | What it does | Example |
|---|---|---|
| **-L** | brings **a remote service here** | view your home PC's web server at `127.0.0.1:8080` on the phone |
| **-R** | lets **the remote reach this device** | ssh into the phone from your home server while you are out |
| **-D** | **goes out through the remote** (SOCKS, 0.8.524) | browse the wider internet from the phone over your home line |

**Using -D (SOCKS)**

Unlike `-L`, **no destination is fixed up front**. You only set the listener; **the caller names a
destination per connection**. Pick **-D** on the left of the row and enter `127.0.0.1` : `1080` as
the listener — that is all (the `→` row disappears, because having no destination is what `-D` is).

Then point anything that speaks SOCKS at `127.0.0.1:1080`:

```sh
curl --socks5-hostname 127.0.0.1:1080 https://example.com
```

- **Names are resolved on the far side** too (that is what the `hostname` in `--socks5-hostname`
  asks for), so names that only exist over there just work.
- Browsers, `git` — anything that takes a SOCKS proxy works the same way.
- ⚠ Listening on `0.0.0.0` makes it usable **from every machine on the same Wi-Fi**. That is an
  open door for anyone, so turn it on only while you need it.

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

- ⚠ With low-power mode on (Command list → Servers) the greeting stretches to
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

Settings are split into **9 groups** (Display / "Keyboard, input / Language" / Linux environment / Permissions and notifications / App lock / Maintenance / Developer / How to use (Tips) / About this app), and **tapping a heading opens or closes it**. The open/closed state is **remembered even after you close the app**, so you can keep the groups you use often expanded.

> **How to use (Tips)** (0.8.399): double taps, long presses, flicks — the gestures that **show nothing on screen** are collected here. That toolbar buttons have a second function, how to close and reorder tabs, the ESC and ⌫ flicks, scrolling inside a GUI app, that `z2gui clean` reinstalls the GUI when the desktop stops coming up (0.8.519), that typing `z2term` lists the built-in commands, and that an AI can write your macros. These are things you would never run into by accident, so open it once.

**Action automation look (0.8.603)**: Command list → Automation → Action automation now looks like the Automation rules tab: monospace headings and text, bordered rows, bordered buttons and hint boxes. Each row ends with ▶ to run, Duplicate, ✎ to edit and ✕ to delete. Confirmation, step editing and picker dialogs use the app's colours. The floating bar shown while picking coordinates is unchanged. The Action automation / Automation rules subtab labels are monospace too.

**Notes and action macros (0.8.586)**: Double-tap an edge-panel note to begin editing; a single tap leaves the keyboard closed. Command list → Automation → Action automation directly contains the macro list, creation, editing, duplication, running, stopping and history. Switching tabs, closing or going back confirms unsaved changes. Coordinate picking returns to the editor inside the original tab. Build and device behavior not yet verified.

**Command list tabs (0.8.583, 0.8.584)**: Top tabs return to equal-width rounded buttons, with a green border and tinted background when selected and 12sp monospace labels on one line. The Action automation / Automation rules subtabs retain the newer underline design. The fixed top position and restored tab selection remain available.

**Management consolidation (0.8.581)**: Manage resident servers and automation in the Command list tool. Servers contains definitions, start/stop, startup on boot, low-power mode and the data limit. Automation switches between Action automation and Automation rules. From 0.8.586, action automation also handles creation, editing, execution, stopping and history directly inside its tab, alongside inline rule editing.

The former settings group is now Permissions and notifications, with Permissions and Notifications and logs tabs. Permissions includes notification posting and reading, Accessibility, overlays, shared storage, SMS, battery exemption, system settings, usage access, APK installation, device administration, IME and attached USB devices. Root rechecking is available after unlocking the developer engine controls. Folder-specific grants remain with the feature that selects the folder.

Notification, SMS, event and unlock-failure detection and log formats remain in Notifications and logs. Enabled detection with a missing permission links to Permissions. State refreshes after returning from Android settings or a permission result; USB refreshes only while visible. Each button requests its own permission.

| Setting | Description |
|---|---|
| Theme | Color scheme (9 options) |
| Font | Display typeface (4 options, with preview) |
| Font size | 4–32 (also changeable by pinching) |
| Scrollback lines | How many lines you can scroll back through |
| Toolbar | **Choose which buttons appear above the terminal.** The real buttons are laid out; tap to remove or bring one back. A removed button's position is remembered. ⚙ is always rightmost and cannot be removed. If you remove 🔅 screen-on lock or 🔒 keep-alive, a switch for it appears in this section |
| Toolbar / tab position (landscape) | **Left / Right** — which edge the two vertical columns stand on in landscape (0.8.433; left by default) |
| Distro | Alpine / Ubuntu / Arch / Kali |
| Keyboard style | Simple / 4-direction flick |
| Keyboard faces | Built-in **Japanese, letters and numbers**, plus every saved custom layout, each have their own ON/OFF switch (0.8.413). Move any face up or down to freely choose the order used by the next-face key. At least one face always remains enabled. |
| Key layout (your own) | Duplicate the built-in **letter, Japanese or number face**, or any selected custom layout, then edit the copy. The editor is a fixed page inside Settings, with the same full-width back bar, system-bar spacing and always-visible Save footer (0.8.413). A tap selects one key; turn on Multiple selection only when applying width, appearance or gestures to several keys. Width accepts intermediate decimals such as `1.` and uses the same ticked 0.1-step slider as Settings. Each custom layout is an independent face whose use and next-face position are set above. Invalid structures cannot be saved, a missing escape action is warned, and layouts **travel with settings export/import**. |
| User dictionary | **Add your own words from a file** (0.8.280). "Choose a dictionary file" picks a text file on the phone and its words start appearing in conversion straight away. One word per line, in **either of two layouts** (0.8.282): `reading /candidate1/candidate2/` (the SKK dictionary format, e.g. `ずぃーとぅーたーむ /Z2Term/z2term/`) or `reading<TAB>word<TAB>part-of-speech` (what dictionary tools export, e.g. `あいぎょう→愛楽→名詞`; a fourth note column is fine). Lines starting with `;` or `#` are treated as notes and skipped. ⚠ **Write readings in hiragana.** UTF-8 and EUC-JP files are both read. Imported files are listed so you can remove one when you no longer want it. ⚠ Up to 8MB per file. If no words could be read you are told immediately, so a format mistake is not silent |
| Special key bar (with the OS keyboard) | Whether the **ESC, TAB, CTRL and arrow keys** appear above your phone's own keyboard while it is selected (0.8.279). Turn it off and they are not shown. The built-in keyboard never had them, so it is unaffected |
| Japanese IME learning history | The phrases the converter has learned. Search and delete them one by one, or clear them all |
| Built-in keyboard elsewhere | Settings → Permissions and notifications → Permissions → Enable the keyboard opens Android input-method settings. Use Switch keyboard to select it. Layouts, size and dictionaries remain under Keyboard settings. |
| Overlap with the 3-button bar | When used as an OS input method, the bottom row of the keyboard **overlapped the 3-button navigation bar (back / home / recents) and could not be pressed**; fixed in 0.8.279. The keyboard is now lifted by the height of the bar. Devices on gesture navigation get no extra gap |
| Which face it opens on (ASCII / Japanese) | **Only when used as an OS input method does it reopen on the face you last used** (0.8.295). Switch to the Japanese flick face with 「あ」 and close it, and the next time it opens in another app it is still on the Japanese face (press ABC and the next one is ASCII again). ⚠ **The built-in keyboard on the terminal screen always starts on ASCII, as before** — people start typing ASCII in a terminal and Japanese in other apps, so only the other-apps side remembers. There is no setting to turn this on (each switch is remembered automatically) |
| Keyboard position (landscape) | Left / bottom / right — effective only in landscape |
| Side keyboard width (landscape) | Slider 280–700 dp |
| Keyboard width (bottom) | Slider 40–100% of the screen width (100% fills it, less centres the keyboard; portrait and landscape are remembered separately, 0.8.431) |
| Keyboard height (landscape / portrait) | Slider 200–500 dp (remembered separately per orientation) |
| GUI audio | Play sound (video, etc.) in the GUI (desktop) — only when ON |
| GUI | Configure audio and display magnification. No terminal is installed or launched automatically; open installed GUI applications from ☰ |
| Language | **This label stays in English in every locale. The collapsed group heading also includes "Language", so the setting remains recognizable after changing languages.** **System** (default) / Japanese / English / Simplified Chinese / Traditional Chinese / Spanish / Korean (switches instantly; Simplified since 0.8.424, Traditional since 0.8.426, Spanish since 0.8.517, Korean since 0.8.520). ⚠ The default follows **the phone's own language setting** (0.8.363 — before that the app started in Japanese whatever the phone was set to). Picking one pins that language regardless of the phone. **The terminal follows too** — the help and messages of the `z2-*` commands use the same setting. ⚠ **Anything not translated yet appears in English** (0.8.422; before that it stayed in Japanese). The Japanese flick keyboard is offered only in Japanese (other languages get the ASCII and numeric faces). ⚠ **No Chinese/Japanese conversion engine and no Hangul composer are bundled for other languages** — switch to your OS input method for that |
| Disable install timeout | Wait for OS / GUI downloads to finish completely |
| Confirm before downloading | Show a confirmation dialog before fetching a distro / GUI |
| SSH access from a PC | Steps for connecting from a PC, with the IP shown |
| Shared storage access | Configure it under Permissions and notifications → Permissions → Shared storage. Android 11+ checks all-files access; Android 10 checks the runtime storage permissions. |
| External storage (SD card) | When on, an inserted SD card is made visible from inside the OS (`/sdcard_ext`) |
| Background process protection | Battery exemption and phantom-process guidance are in Permissions and notifications → Permissions. Low-power mode is in Command list → Servers. |
| Reset terminal | Returns the app to **the state it had when first opened**. Only one terminal tab is left (other terminal tabs and GUI tabs are closed), and that terminal goes back to its initial state: running programs are terminated, screen and scrollback cleared. Tapping it opens a confirmation. **Saved servers, settings, snippets and the OS itself (installed packages and files you made) are not removed** |
| Clear cache | Sweeps the package/build caches that pile up inside the OS (pacman, apt, apk, `~/.cache`, …) plus the app's temp files. Tapping it opens a confirmation that **itemizes what and how much** will be deleted. Installed packages, settings and files you made are not removed |
| Delete OS data | The selected OS can also be deleted after confirmation in Delete OS data. Its terminal, GUI and background processes stop before removing its rootfs, OS-specific home directories and downloaded images. Shared home and other OS data are kept. Affected tabs move to a remaining OS, or to the Android shell after the last OS is removed. Deletion is refused while startup or installation is in progress; active root chroot processes or mounts must be stopped and unmounted first. Failures appear in Settings and the diagnostic log. |
| Notification detection | Grant the OS "notification access" and turn it on, and incoming notifications are appended to `~/.z2term/notifications.jsonl` (a generic hook). **The output format is fully customizable** (a template of `{time}` `{app}` `{title}` `{text}` … ; presets: readable / one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file instead of appending at the end. That mode reads and rewrites the whole file per entry, so **once the log passes 10MB the settings screen shows a warning** (turn it off before it gets slow, or trim the file from the terminal). What you record / filter / serve is **up to you on the terminal side** (e.g. `tail -f`, or serve it with a resident server). Since the side new entries arrive on changes with the append direction, **the "command to read it" shown in settings follows that setting** (`tail -f` when newest is at the bottom, `watch -n 1 head -n 20 …` when newest is at the top). Turn **"Save notification log"** off to keep detecting without writing anything to the file (detection only). **0.8.585**: Read delivered conversation/history Bundles directly, avoiding the platform decoder's 1,024-unit truncation. Notification-level timestamp changes do not identify new messages. Copies across plain, conversation and inbox payloads deduplicate within 10 seconds, while actual message times, senders and extra occurrences preserve identical new messages. A later full body is appended once as a correction. Generic new-message notices are saved when that is the only supplied content. Unpublished text, sender/OS truncation and redaction cannot be recovered. Deduplication uses bounded process-local caches; without identities, identical new text and reposts cannot always be distinguished. Default off, fully local. Release build and lint pass; all 28 notification unit tests pass. The APK for two device tests compiles but has not run. The user confirmed improved deduplication on 2026-09-12; truncated bodies and generic notices during bursts remain unresolved |
| SMS detection | Turn it on and **grant the SMS permission**, and incoming SMS are appended to `~/.z2term/sms.jsonl` (fields: `time` `from` `body`; format customizable). **Vs. notification detection**: Android 15+ **redacts OTP-bearing notifications** before handing them to ordinary apps, so SMS OTPs may not be readable via notifications (same for MacroDroid etc.). SMS detection reads the **SMS body directly**, bypassing the redaction, and works **even while locked**. For auto-copy, register `z2-macro install otp-sms.sh` as a resident server. Non-SMS OTPs (e.g. authenticator-app notifications) are out of scope. Default off, fully local |
| System event detection | Turn it on and screen on/off, unlock, charge start/stop, battery low/okay, Wi-Fi connect/disconnect and **Bluetooth earbuds connect/disconnect** are appended to `~/.z2term/events.jsonl` (a generic automation trigger; sibling of notification detection). **The output format is customizable** (`{time}` `{event}` `{level}` `{ssid}`; presets: one-line / TSV / JSONL). Turn on **"Newest at the top"** to prepend new entries to the head of the file (in that mode, **once the log passes 10MB the settings screen shows a warning**). Build automations like "when battery drops below 20%…" or "when charging starts…" **terminal-side** (e.g. a script reading `tail -f ~/.z2term/events.jsonl`; **the "command to read it" shown in settings follows the append direction**). Default off, fully local, shows an ongoing notification while active (Wi-Fi SSID is blank without location permission). **The log has no size cap** (it keeps appending into one file; clean it up from the terminal, e.g. `: > ~/.z2term/events.jsonl`) |
| Unlock-failure detection | With this on **plus device admin activated**, lock-screen unlock **failures/successes** are recorded to `~/.z2term/events.jsonl` as `unlock_failed` (`level` = consecutive failure count) / `unlock_succeeded`. It's the **detection hook for anti-theft macros** like "after N wrong passwords, notify / record location / sound an alarm". No photo or upload is built in — you build the reaction as a macro. Device admin is used only to watch failure counts (`watch-login`); it **does not lock or wipe** your device remotely. Default off, fully local |
| App lock | **Asks who you are before the screen appears** (0.8.421). Fingerprint or face, and **your screen lock (PIN/pattern) works too** — so a wet finger or no enrolled biometrics never locks you out. ⚠ **It cannot be turned on if the phone has no screen lock set** (there would be nothing to check against, and the app could never be opened again). Choose when it locks: **at once / after 30s / after 1 min / after 5 min / on launch only** (default: after 30s, so short round trips like copying from another app don't ask). **Starting the app always locks, whatever you choose here.** While you are away it also hides the **thumbnail in the recent-apps switcher**. ⛔ **It hides the screen, nothing more** — sessions, resident servers and `z2-session attach` keep running while locked (stopping them would break anything left working overnight). It is for handing the phone to someone without them seeing the screen, not for keeping intruders out |
| Reset settings | Returns **every setting to its defaults** — theme, font, keyboard, execution engine (back to the default **z2root**), saved servers, unlocked hidden features and so on. Tapping it opens a confirmation (cannot be undone). **The OS itself (installed packages and files you made) is not removed** |

### Resident servers (run without opening the app)

Open Command list → Servers to register, edit, start and stop resident servers.

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
Low-power mode is in Command list → Servers. It affects resident servers and terminal keep-alive; reaction and reachability while the screen is off may be slower.

Note: **0.8.268 fixes battery drain caused by residency alone.** Until then the servers were checked once a second, and each check spawned several small processes inside the device — including for servers that were not even running. That added up and kept the phone permanently warm, so the **check interval is now 5 seconds** and the residency notification is only rewritten **when the running count changes**. In exchange, turning a server on/off — and adding, editing or deleting one — takes up to **5 seconds** to apply.

#### Ending the terminal while resident servers are running

While resident servers run, the app stays in the background, so **swiping it away from recents will not close the terminal**. During this time the toolbar's **🔒 is dimmed and can't be toggled** (turning it OFF would do nothing).

**Tap the dimmed 🔒** to choose one of two things:

- **End session only**: returns the terminal to a **freshly opened state** (running programs end; screen and history are cleared). **Resident servers keep running.**
- **Stop everything and quit**: **stops the resident servers, system event capture and the terminal, then closes the app** (a reliable way to quit, since swiping won't). Your settings are unchanged, so capture resumes the next time you open the app.

Pick "End session only" for a clean slate, or "Stop everything and quit" to stop it all.

---

**Startup race fix (0.8.596)**: A theme initialization crash found after the device update is addressed by constructing the shared palette during Application startup and applying the asynchronously loaded theme on the main thread. This prevents composition from racing with the first creation of palette state.

**QR tools (0.8.597)**: The QR button at the top-left of the command sheet reads camera frames or images and lets you review a URL, save an SSH endpoint or import a short command. Share images or decoded text from another app to “z2term — QR” to open the same review screen. Automation places Rules on the left and Action automation on the right; Rules remains selected by default.

**0.8.600-alpha (versionCode 608)**: QR tools remain available. File sharing now requires a self-hosted relay, with a small “Share files through a relay” entry at the bottom of the Servers tab. Choose a saved SSH profile and configure your own public HTTPS origin and server-side loopback port. Automatic third-party relays and direct device-address sharing have been removed. The QR appears after the public URL passes a health check. Stop, expiry or network change closes the share connections and removes the sending copy. See [usage and server setup](QR-TOOLS.md).

**QR entry and sharing screens (0.8.601)**: Open QR from the Snippets or Connections tab header, next to “+ New”. The top of the command sheet is only the drag handle, which closes it when tapped. QR tools and Share files through a relay now match the command sheet, and the relay entry at the bottom of the Servers tab is a row with a chevron. See 9.6 for the first edge panel and its guide.

**Open what you scan (0.8.602)**: After a scan, one Open button matches the content. For a LINE login QR, it opens LINE's confirmation screen. Phone numbers open the dialer, mail and SMS open a compose screen, Wi-Fi opens the connection screen, and contacts and events open an add screen. Nothing opens by itself. Open QR tools from Settings › QR tools. `z2-qr` in the terminal opens them with the camera scanning, so you can put it on a quick-settings tile (`z2-tile set 1 z2-qr`) or an edge panel.

**QR history (0.8.603)**: Scanned content stays in a history below QR tools. Tap an entry to show it in the content field again; long-press to pin or delete it. Clear all keeps pinned entries. Up to 50 unpinned entries are kept, newest first. Content made with Show QR is not added. History stays on this device only and includes content such as Wi-Fi passwords.

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
| `servers 1/3` | Resident servers | Command list › Servers |
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

## 9.6. Floating edge panels

**Sample panel and guide (0.8.601, 0.8.603)**: Turning panels on does not create a panel. Settings › Maintenance › Show a guide › “edge-panel” lets you tap through the overlay permission, the accessibility service, creating the sample panel and turning the panel on. The sample is an app list opened from a bar on the right edge, listing the z2term, browser, camera, phone, messages and settings apps found on this device. Its guide card shows the exact `z2-edge panel` and `z2-edge set` line it sends. Swipe the bar inward to open it. Add apps with the panel's “+”. Scrolling by swiping the bar up and down requires the “z2term Android actions” accessibility service. With the panel open, long-press outside the menu to open settings. 0.8.601–0.8.602 created the sample automatically when panels were turned on with none defined.

**ON/OFF buttons (0.8.589)**: In the panel editor, edit an application, macro or run item and enable **Show ON/OFF**. ON uses a thicker coloured border and background; OFF uses a subtle border. No ON/OFF text or check marks are added to the menu, including icon-only layouts. The optional OFF command defaults to the same command. A state query should return `on/off`, `true/false` or `1/0`. Without a query, successful toggles are remembered; external changes and application exits are not detected. Failed or timed-out commands do not flip the state. State survives reopening and app restart, and command-definition changes invalidate it. Queries run on opening, after successful actions and at the configured refresh interval. Macros can also report state with `z2-edge state PANEL:ITEM on` / `off`.

**State synchronization and automatic OFF (0.8.591)**: Choose **State source** below Show ON/OFF. Automatic follows the actual state of directly assigned `z2-torch` and `z2-screen keepon DURATION` commands. Torch observations include changes made through Quick Settings and other applications. Screen keep-on follows expiration, cancellation and deadline changes, including when the panel was closed. API operations inside macros also record the last supported feature as their source. Select Flashlight or Prevent screen sleep explicitly to synchronize before the first run or when a macro controls several features. ON while running; OFF on exit clears the state on completion, failure, timeout or stop; tapping a running button stops it. For detached background work or custom work started elsewhere, configure a state query and refresh interval. An explicit state query takes priority. No state text or check marks are added to menus.

**Bar colour (0.8.587)**: Panel editor → **Appearance → Handle → Bar colour** offers Automatic, White and Black. Automatic samples the background just inward of the bar about once per second, choosing black over light backgrounds and white over dark backgrounds. A thin opposite-colour outline remains visible. This requires Android 11+ and z2term Android actions accessibility, enabled through Settings → Permissions and notifications. When sampling is unavailable the outline remains; fixed-colour bars do not request sampling. Images are never saved or sent. Sampling stops when the screen is off or locked or the handles are removed, and pauses while the panel is open or handles are suspended for action macros.

**Note colours (0.8.587)**: **Items → Edit note** provides Note background colour and Note text colour. Choose a swatch or enter #RRGGBB, then save to apply to both viewing and editing. Automatic clears the override. A custom background with automatic text chooses contrasting white or black. Ruled lines and the cursor follow the text colour. Changes use the existing Save, Cancel and unsaved-change confirmation, preserving the note text and undo/redo history. Build and device behavior not yet verified.

**Adding items and switching tabs (unreleased)**: “+” opens item editing with choices to add an app or a custom macro/command slot. Custom slots can select saved macros like tiles or accept commands directly. The icon field offers a preview list of bundled and saved z2-icon images. Add and settings controls remain 48dp tall and adapt to 24–32dp widths so both fit side by side in narrow panels. Swipe across a normal menu to switch tabs: left/right for vertical and grid layouts, up/down for horizontal layouts (left/up advances, right/down goes back). No initial tab tap is required; the first and last tabs do not wrap. Input editing, long presses and scrolling along the item layout retain their behavior. Tap the bar to open its menu, then **long-press outside the menu** to open bar editing; Settings → Tips also describes this shortcut.

**Named Android action macros (0.8.571)**: Save text definitions through the CLI and run coordinate taps, holds, swipes, waits, app launches, shell commands and timed scrolling through one runtime. Execution provides one active run, completion tracking, cancellation, deadlines and history. Panels, tiles, existing macros and z2-when call the same definitions. See [Android action macros](ACTION-MACROS.md) for syntax, limits and examples. Build and device behavior not yet verified.

**Action-macro GUI appearance (0.8.578)**: the list is one row per macro - tap the name to edit, with run, duplicate and delete at its right. Whatever is running, and the Stop button, sit together in one bordered block, and above it is "Android action permission" (nothing runs without it). Steps line their numbers up in a left column and print the line itself in a fixed pitch, with repeat and branch bodies shown by a left rule and an indent. The colours are the same ones the edge-panel editor uses, built from your terminal theme.

**Action automation on the foreground screen**: Coordinate, scrolling and UI-element steps can be saved without an app target. GUI Run moves z2term to the background and waits for another app to settle before starting. Each step resolves the foreground app at its start and holds the target during the action. Use `target PACKAGE` / `launch PACKAGE` for a specific app and `target current` to return to the foreground screen. See [Android action macros](ACTION-MACROS.md). Build and device behavior not yet verified.

**0.8.630-alpha (versionCode 638) — build unverified**: Making a QR code no longer needs `qrencode` installed. The encoder (ZXing) is already in the app for the QR tools screen, so the terminal can now call the same one (`z2-qr encode`). `z2-qr encode "text"` writes a PNG (by default `~/.z2term/qr/qr.png`) and prints where it went; `-t` draws it right there with block characters instead. `-p` is the size to aim for in pixels and `-m` the quiet zone in modules. The bundled `qr.sh` keeps using `qrencode` when it is installed and falls back to the app when it is not, **so there is nothing to reinstall every time a tab (distro) is rebuilt.** ⚠ Where the app is out of reach (over `ssh`), `qrencode` is still required.

**0.8.629-alpha (versionCode 637) — build unverified**: A new bundled sample macro, `md.sh`, reads Markdown here on the terminal (the eleventh). `md.sh README.md` lays out headings, lists, tables, code and quotes on the screen; links stay tappable and pictures appear in place inside a tab (`z2-img`). `md.sh -v README.md` opens it in the reading screen (`z2-view`) added in 0.8.628. **Nothing to install** — `sh` and `awk` are enough. Lines wrap to the width of the screen and never start with a closing punctuation mark; a table too wide for the screen breaks into "heading: value" lines; and since this terminal does not draw italics, `*emphasis*` is underlined instead (as man does). Install it with `z2-macro install md`.

**0.8.628-alpha (versionCode 636) — build unverified**: New `z2-view` reads a page you built on the terminal **inside the app** — no server, no browser. `rss.sh` now writes such a page on every poll (the user's report: "following RSS through notifications is hard to read"). Notifications stay as they were, with a second button: Open sends that article to the browser, List opens everything collected, grouped by site, with an Open button on each article. From the terminal it is `rss.sh view`. The page is shown with JavaScript and network loads switched off, and follows the terminal's colours.

**0.8.627-alpha (versionCode 635) — build unverified**: Edge-panel scrolling no longer touches the screen by default. It used to send a real swipe, which an app cannot tell apart from your finger, so on screens where swiping does something that action ran instead, and a stroke across a keyboard could type (the user's report: "the gesture types characters by itself — it is dangerous"). ⇒ it now asks the scrollable view directly and only falls back to a swipe where the app offers nothing scrollable. Appearance → Gestures → How to scroll offers **Automatic / Do not touch the screen / Send a swipe** (`scroll-how=auto|node|swipe` from the CLI). The scroll target is also picked from the window you are touching: a freeform window may hold no input focus, which left it unscrollable even while in front.

**0.8.626-alpha (versionCode 634) — build unverified**: Two changes to the drawings `z2-icon` ships. (1) `sync` is redrawn as **two arrows forming a loop** — the old one did not read as anything in particular (the user's report: "sync has turned into a mark that makes no sense"). (2) **A camera drawing, `camera`, joins the list** (16 bundled now). A tile whose command contains `shot`, `photo` or `camera` gets it automatically (it is looked at before `moon`, because `screenshot` contains `screen`).

**0.8.625-alpha (versionCode 633) — build unverified**: Four fixes to `z2-shot`. (1) While you draw, the hint and shape controls at the foot are hidden so they do not sit on top of what you are framing; they return when you lift your finger. (2) The frequent "could not capture the screen" failure is fixed: automatic bar colouring samples the screen about once a second, and capturing right after that was refused by Android's minimum interval between screenshots, so the capture now waits out the remainder. (3) A Circle shape joins Free, Rectangle and Oval, using the shorter side of the drag as its diameter. (4) Dragging the image in the preview moves what is cropped.

**0.8.624-alpha (versionCode 632) — build unverified**: The + and gear buttons can now be placed at the top or the foot of a panel (Position of + and gear in panel settings, or `tools-place=top|bottom` from the CLI). Left automatic they behave as before: in the navigation row when a tab switcher is present, otherwise at the foot. A panel set to `tabbar=off` also no longer shows a tab switcher when it has child tabs — adding a single tab used to force the switcher into view and pull both buttons up with it.

**0.8.623-alpha (versionCode 631) — build unverified**: New `z2-shot` captures only the part you draw around. Called from an edge panel button, it hides the panel and its handle, takes one screen image, and lets you draw on that still picture. Free, rectangle and oval shapes are available, and lifting your finger confirms the outline (Redo draws it again). You then choose whether the outside is transparent or keeps the original background, and Save (to Pictures/z2term) or Share. The panel, its handle and the selection UI never appear in the saved image. Needs Android 11 or later and the z2term Android actions permission.

**0.8.620-alpha (versionCode 628) — build unverified**: The edge item editor is split into Component and behavior, Display, Command, Value and Connections, and blocks the chosen component does not use are hidden. Advanced settings and item size and position fold at the foot. Edit turns into Close while the editor is open and closes it again, asking first if there are unsaved changes. Manage › Remove panel or tab now uses checkboxes, so several panels and tabs can be deleted at once; ticking a panel deletes its tabs too. The guide card list scrolls, and a swipe no longer sends a card’s command. Japanese labels now use パネル consistently.

**0.8.619-alpha (versionCode 627) — build unverified**: Edge mini terminals retain shells, output and drafts across closing, tab changes and reloads. Output appends; ↻ resets manually. Outside taps and Back end active typing first and otherwise close the panel. Sample handles default to 30% opacity. Guide commands now send bulk paste and a final Enter in one ordered write.

**0.8.618-alpha (versionCode 626) — build unverified**: Panel bindings now show item names and support reordering and disconnecting. Buttons without a name or icon show Run. Result Stop/Copy/Clear controls are hidden by default and can be enabled in advanced settings. The single `edge-workspace` guide creates one panel with Notepad, Translation and Terminal tabs; translation requires a separately installed command and the bundled macro. Restores long-press repeat for letters, uppercase letters and symbols.

**0.8.617-alpha (versionCode 625) — build unverified**: Creation uses Add app and Add item. Select components and behaviors independently, bind arguments, standard input and output displays, and configure per-item dimensions, alignment and free placement. Existing items, files and scripts are retained. See [components and behaviors](EDGE-MACRO-FORMS.md).

**0.8.615-alpha (versionCode 623) — build unverified**: Retains the 0.8.614 integration and corrects the unit-test syntax used by CI. Validation and distribution run on GitHub CI.

**0.8.614-alpha (versionCode 622) — build unverified**: The Linux engine now handles fd, cwd, namespace and memory-map magic links without interpreting their descriptions as filenames. This targets pipes, anonymous files, unlinked open files and working directories, and indirect executable references. Translation forms and mini terminals also receive outside long presses to open settings. Outside taps and Back keep these panels open; the panel captures outside touches. When the translation CLI is missing, the wrapper prints manual installation commands for the current distribution. Also fixes the translation macro icon mapping and Japanese weekday/clock parsing in byte-oriented shells and locales. Verification on the updated device is pending.

**0.8.613-alpha (versionCode 621)**: Horizontal flicks over terminal and translation results switch tabs while vertical drags still scroll output. Up/Down in the mini terminal recall the regular shell history. The action-row ≡ button opens existing snippets for selection, registration, editing and deletion, using the same data as the main app. Selection inserts a command; Run executes it.

**0.8.612-alpha (versionCode 620)**: Edge-panel tabs and Close keep the same position across pages, without switching window geometry. Mini-terminal input sits at the bottom; blank labels omit headings. Terminal and translation results scroll inside their result boxes, and the persistent help text is removed. Items can be deleted directly from the list, preserving its scroll position.

Use Delete on each item row and confirm there; opening the item editor is unnecessary. Deletion preserves the list scroll position and keeps note files. Panels with tabs share their configured height so content length cannot shift the tab row.

**0.8.611-alpha (versionCode 619)**: Edge panels support independent argument boxes, macro actions and result boxes. Bind text, choices or fixed values in argument order; clear, copy or stop results. A translation template uses the same form mechanism. Users install the translation CLI themselves; no SDK, model or automatic download is added to the APK. [Usage](EDGE-MACRO-FORMS.md).

**0.8.610-alpha (versionCode 618)**: Edge panels now offer a mini terminal with one-line commands and live output cleared before each run. Directory and environment changes last only for the current opening; closing also stops ordinary jobs. Explicit `nohup` jobs and detached `tmux` / `screen` sessions can continue. Outside taps and Back keep the panel open; use its Close button. No external dependencies were added. [Usage](MINI-TERMINAL.md).

**0.8.609-alpha (versionCode 617)**: The reminder macro accepts a weekday for a single upcoming reminder, including Japanese input such as `土曜日の夜九時` (Saturday at 21:00). Japanese morning/night expressions, kanji numerals and full-width digits are supported. A later time on the same weekday means today; a time already reached means next week. [Syntax and updating an installed macro](MACRO-GUIDE.md).

**0.8.608-alpha (versionCode 616)**: `z2-share --file` sends file contents through Android's share sheet. Incoming text and attachments are saved as one receipt, with an optional choice of registered snippets. Snippets can request text, numbers, choices and files, preview the command, then insert it for execution with Enter. [Sharing and command input guide](SHARE-WORKFLOW.md).

**0.8.607-alpha (versionCode 615)**: The call macro now describes its actual purpose: a notification to copy a phone number. It includes saved and unsaved callers whenever the title or body contains a bare number. The existing name `unknown-call` and automation rules remain compatible. Installed copies are not updated automatically: after updating the app, inspect `z2-macro diff unknown-call` and, if you have no custom edits to preserve, replace the copy with `z2-macro install -f unknown-call`.

**0.8.606-alpha (versionCode 614)**: Added Korean, Spanish, Simplified Chinese and Traditional Chinese strings for foreground-screen automation, recording, gestures and placement, fixing missing-translation lint errors. Updated the public pages to the current version.

**0.8.605-alpha (versionCode 613) — build not verified**: Action automation can record a whole sequence of taps, swipes and two-finger touches until stopped. Choose a recording surface or live recording through root. Only leading/trailing idle time is trimmed; pauses and timed paths are retained. Manual acceleration/deceleration, double tap, pinch in/out and two-finger swipe are also available. Place saved macros in Quick Settings tiles or edge panels. Device behavior is not verified. [Details](ACTION-MACROS.md).

**Action macro GUI (0.8.572)**: Command list → Automation → Action automation opens the list, creation, step editing, ordering, duplication, execution, stopping and history. Pick tap/hold points or swipe endpoints on the screen and return them as pixels or percentages. Since 0.8.579, picking requires no app launch or target directive. Selection consumes touch input and ends on cancellation, screen changes, screen off, disconnection or after two minutes. GUI text editing shares definitions with the CLI, with unsaved-change confirmation and stale-save detection. The screen respects app lock. Build and device behavior not yet verified.

**Coordinate-only picking and settings restoration (0.8.579)**: Pick coordinates moves the existing settings and editor tasks to the background and immediately starts coordinate selection. No launch or target step is required. Navigate allows normal interaction before resuming selection on the desired screen. Picking checks no target window and returns only numbers to the draft; saving allows unspecified targets and execution retains window checks. Settings visibility and scroll position return after backgrounding or unlocking, and the locked editor no longer overwrites its retained scroll position. Build and device behavior not yet verified.

**Action macro repetition, branching and reuse (0.8.573)**: version=2 adds counted/infinite loops, conditions based on device state or the focused package, and calls to saved macros. The complete call graph is validated and frozen before execution; cycles are rejected. Root and child deadlines, cancellation and a 10000-instruction run limit remain shared. The GUI offers loop/branch templates and a saved-macro picker; hierarchical block forms are added in 0.8.574. Progress includes location, iteration and branch outcomes. See [Android action macros](ACTION-MACROS.md). Build and device behavior not yet verified.

**UI elements and block editing (0.8.574)**: Click/long-click by visible text, description or resource ID, and wait for elements with deadlines. Choose selectors over the target app or inspect through the CLI. Explicit element requests read only the target window, excluding editable/password fields. The hierarchical GUI adds children, moves across groups, duplicates/removes complete blocks and manages else branches. Text, comments and line endings are retained, with shared cancellation and deadlines. See [usage](ACTION-MACROS.md). Build and device behavior not verified; added tests not run.

**Shared actions and gestures (unreleased)**: Appearance → Gestures assigns an ordered action list to tap, double tap, swipe up/down/inward/outward. Add, remove and move actions in the GUI; an empty list disables a gesture. Hold remains reserved for editing/relocation. Assigned directions take precedence over immediate button dragging; hold to relocate instead. Appearance previews never execute actions.

Actions include toggling this panel, Back, Home, Recents, notifications, launching an installed app selected from a list, waiting, commands, single up/down swipes, variable/fixed auto-scroll, stop, faster, slower and reverse. For example, compose “Launch app → Wait → Swipe up once”. Commands wait for exit and single swipes wait for Android completion before advancing. Failure/cancellation stops the remaining actions. Launch completion means the launch request was accepted; add an explicit delay for screen readiness. Use wait-ui in named action macros to wait for UI elements.

Limits: 16 actions, 0–30000ms per wait, 60000ms total waits, 30 seconds per command and three minutes per sequence. A new handle touch, screen-off, rotation, reload or service shutdown cancels the sequence. A new sequence cancels its predecessor; late callbacks cannot restart cancelled work. Starting continuous auto-scroll advances to the next action once started; scrolling may continue after the sequence finishes.

For left-edge variable scrolling, select a left-side bar and assign “Speed follows displacement” to up/down swipes. Release to start: up advances and down goes back. Set the slide distance to maximum speed with `gesture-range`, 32–2000dp (default 160), also available in appearance gesture settings. Speed increases linearly with displacement beyond touch slop, without a 5% minimum, reaching maximum at the configured distance. For example, `z2-edge panel ID gesture-range=640` with maximum 40000 gives 10000 at 160dp and 40000 at 640dp. A trigger without displacement starts forward at the configured speed. Maximum/fixed speed is 50–40000dp/s (default 600, nominal injected speed). Faster/slower multiply/divide speed by 1.5 and reverse changes its sign; adjusted speed is bounded to 2.5–40000dp/s. Touching a running handle pauses scrolling; releasing a bound swipe applies the adjustment. Taps always stop, so assign adjustment actions to swipes.

Scroll X/Y positions are percentages within the focused app window (10–90, default 50). Paths are shifted to stay inside the window. Single swipes use the same position. Only window ID, bounds and focus are read; UI nodes are not searched. Missing targets prevent startup; focus/size changes stop subsequent strokes, and adjustments must retain the original target. Accessibility is required.

With a keyboard open, scrolling is restricted to the terminal viewport excluding the built-in keyboard, and to window bounds above the Android IME. A change in that area stops playback. `z2-key permission` opens the service details or falls back to general Accessibility settings if the device denies that screen.

A stop tap does not open the handle. Touches outside the handle stop scrolling both during and between strokes. Only synthetic gesture events are ignored; physical touch notifications are processed immediately. Outside taps may reach the underlying app. A dispatched stroke (normally up to 120ms; up to 301ms with legacy 100ms sampling) and target-app inertia may remain. Higher speeds shorten strokes but retain at least three intermediate MOVE samples based on Android’s sampling interval, avoiding DOWN/UP-only gestures that behave like taps. Configured speed is a target; actual speed is constrained by viewport height, display refresh and the target app.

Fields: `actions-tap`, `actions-double-tap`, `actions-up`, `actions-down`, `actions-inward`, `actions-outward`. Separate actions with `|`; arguments use `type:UTF-8-form-encoded-value`. Examples: `actions-double-tap=launch:org.example.app|wait:500|swipe-up`, `actions-up=scroll-variable`. Encoding preserves pipes, plus signs and newlines in commands. The GUI handles encoding automatically; each list is limited to 16KiB. Unknown actions and invalid arguments are rejected. Empty explicitly disables a binding; absent keys inherit legacy `open`/`run`/`gesture-*` behavior. `gesture-speed` remains the speed setting and `scroll-x`/`scroll-y` set position. CLI and GUI share `panel.conf`.

Since 0.8.568, the edge-panel ongoing notification does not contribute to the app icon count or notification dot. The next service start after updating migrates to a new channel with badging disabled, preserving the old channel’s notification preferences, including blocked status and importance. The running indicator and stop action remain available in the notification shade.

From 0.8.562, panels show only their items by default. Empty panels have no message or controls. Existing definitions are retained and use the new defaults.
**Hold panel whitespace to open settings. Hold a handle for 300ms, then release without moving to open settings; drag to move it as before. A tap-to-open button moves as soon as you drag it (0.8.565).** Done or Back returns to the panel, asking before discarding unsaved edits (0.8.567). If the keyboard is visible, Back hides only the keyboard and keeps settings open.
Settings use a separate screen independent of panel width. Opening settings does not execute item commands or periodic readers.
From 0.8.566, the editor has Items, Appearance and Manage pages. Items show icons and names; tap a row or Edit to change it. Move items with the up/down arrows or hold and drag. App launch mode and label are in the basic form; commands and polling options are under Advanced settings. Removing an item requires confirmation in its editor and preserves note files.
Appearance groups size/position, icons/layout, title/controls and handles into collapsible sections. The size diagram and Save/Cancel buttons stay visible while the form scrolls; handle changes preview on the actual handles. While the IME is visible, the diagram collapses and fields scroll in the remaining space above the keyboard. Only Save writes definitions. Cancel, close and page/tab changes ask before discarding unsaved edits; screen-off and external reload still discard drafts. Items/Appearance/Manage and panel tabs replace content within the same overlay window, without exposing the app behind it (0.8.567). Manage contains names, panel/tab creation and deletion.
From 0.8.577 the settings screen and the panel take their colours from the app palette built out of your terminal theme (the accent is the brand green), so changing the theme moves the panel with it.
From 0.8.577 the **+** in the normal menu goes straight to the app list instead of opening settings. So you can tell it from the gear beside it, + is accented and bold and the gear stays quiet. Adding a custom slot is still gear → Items.
From 0.8.575 the editor's **look** is rebuilt; what it can do is the same. Items/Appearance/Manage sit at the top (the current page is underlined), with the panel and tab chips below them. Anything you can press is filled or outlined, and a button you cannot press dims. Collapsible headings carry `▾`/`▸`, and a group inside a group is shown by a left rule and an indent. Items is one row per item - icon, name, kind, Edit and the move arrows - and the long explanations moved below the list. ⚠ **The panel itself (the normal menu) looks exactly as before.**
From 0.8.620 the item editor is **split into blocks** (Component and behavior, Display, Command, Value, Connections); blocks the chosen component does not use are not shown, and Advanced settings and size/position fold at the bottom. **Edit opens it, and the same button - now Close - closes it** (tapping the row does too; unsaved changes are confirmed first). **To delete panels, open Manage › Remove panel or tab, tick what to delete** and press Delete selected. Ticking a panel deletes its tabs as well; ticking only a tab deletes just that tab. Several panels can be ticked at once.

**Save panel settings as commands (0.8.595)**: The edge panel Manage page now displays recreation commands with a Copy commands button. It exports saved settings and items as `z2-edge` commands. Selecting a parent includes its tabs and their order; selecting a child preserves existing parent settings and other tabs. Note contents, referenced scripts and images need separate backups. Select the panel/tab on Manage, then use Copy commands to save the text. A missing parent is created with its saved appearance; a child is appended to an existing parent. An already registered tab keeps its position. Reusing an ID adds or updates the specified settings; existing items and unspecified settings remain. Unsaved edits, note history, ON/OFF state and transient display values are excluded. Displaying or copying commands does not run them or enable panels.

Enable “Show + app button” under Appearance → Title, tabs and buttons to add apps directly from the normal menu. Cancelling app selection returns to that menu. Hold and drag a run item to reorder it; drop into the first/second half of a target to place it before/after (vertical halves in a column, horizontal halves in a row or grid). Targets are outlined and dragging at an edge scrolls. Dropping outside leaves order unchanged. Whitespace long-press still opens settings (0.8.567).

Menus with child tabs allow direct selection in normal use. When the tab strip is hidden, tap the current tab name at the top to choose a tab (0.8.567).

Presentation fields belong to the parent panel and apply to all its tabs. Settings have no separate persistent state.
Every presentation setting below is also writable with `z2-edge panel ID key=value ...`; `z2-edge get ID` reads saved fields.

| Setting | Field / command |
|---|---|
| Title, close, add and settings controls | `title` / `close` / `add` / `settings` = `on\|off` (default off) |
| Tab bar | `tabbar=off\|on\|auto` (default off; auto shows it when child tabs exist. **off keeps the switcher hidden even with child tabs** — 0.8.624) |
| + and gear position | `tools-place=top\|bottom` (default automatic: the navigation row when a tab switcher exists, otherwise the foot. Setting it pins the side regardless of tabs — 0.8.624) |
| Item labels | `labels=on\|off` (omitted/empty follows tab layout; body text and results remain visible) |
| Width and height | `width` / `height` (dp or %, default 360dp / 72%) |
| Height sizing | `fit=content\|fixed` (default content; an empty panel keeps a 48dp touch area within its height limit) |
| Placement | `place=handle\|left\|right\|top\|bottom\|center` (default handle) |
| Custom position | `at=X%,Y%` (0–100, overrides place; empty clears it; percentage of space remaining after panel size) |
| Arrangement | `flow=vertical\|horizontal\|grid\|free` (omitted/empty follows tab layout) |
| Grid columns and icon size | `columns=auto` or 1–16; `icon-size=16..192` dp (default 40) |
| Handle shape, position, activation | `handle` / `side` / `offset` / `x` / `y` / `size` / `length` / `alpha` / `open` (also via `z2-edge handle`) |
| How to scroll | `scroll-how=auto\|node\|swipe` (default auto: ask the scrollable view, swipe only where the app offers nothing. node never touches the screen; swipe always sends one. 0.8.627) |
| Bar colour | `bar-color=auto\|white\|black` (default auto; also `z2-edge handle ID bar --bar-color white`) |
| Add, name and order tabs | `z2-edge tab PARENT ID LABEL`, `panel ID label=Name`, `panel PARENT tabs=a,b` |
| Add/delete panels | `panel ID label=Name handle=bar side=right` / `delete ID` |
| Add/edit/delete/reorder items | `set ID:item key=value ...` / `remove ID:item` / `set ID:item order=N` |
| App launch mode / add note | Item `run=z2-intent -p PACKAGE --window MODE` / `type=note` |
| Macro forms | `type=argument/macro/result`; see [arguments, results and translation](EDGE-MACRO-FORMS.md). |
| Mini terminal | Item `type=terminal`. Retained across closing; reset manually with ↻; see [lifetime and controls](MINI-TERMINAL.md). |
| Note background / text colour | Item `note-background` / `note-color` = `#RRGGBB` (omitted/empty means automatic; writable with `z2-edge set ID:memo`) |

Optional add/settings controls are icons at the end. Tab/note addition and item editing live in settings. Run items can also be reordered directly in the menu.
Vertical, horizontal, grid and free layouts are available; horizontal rows scroll sideways. Automatic grid columns follow icon size; an explicit flow applies to all item types.
`layout=grid|list` remains the per-tab default. With flow omitted, grid arranges run items only and leaves other types in rows below.

An icon-only vertical bar can be configured with these commands or the corresponding settings:

```sh
z2-edge panel apps width=64 height=60% fit=fixed place=right flow=vertical labels=off
z2-edge set apps:a1 type=run 'run=z2-intent -p PACKAGE --window full'
z2-edge handle apps bar --side right --size 4 --length 20
```

Set panel size with `z2-edge panel main width=80% height=60%`. A `%` suffix means display percentage; plain numbers mean dp.
With fit=content, height is a maximum: short content shrinks the panel and overflow scrolls vertically. Tapping outside closes it without tapping the app behind it.

Bars default to 6dp wide and 6% of screen height. `--size` is clamped to 2–48dp for bars and 32–96dp for buttons.
The minimum rendered length is 8dp. `--alpha 0.05..1` controls opacity.
`--open swipe|tap|both` chooses activation; defaults are swipe for bars and tap for buttons.
Hold a bar for 300ms to move it. A tap-to-open button (`open=tap`, the button default) needs no hold: drag it and it moves (0.8.565). Buttons set to `open=swipe|both` still need the hold, because dragging would collide with opening. Bar hit areas are at least 24dp wide, independent of visible thickness.
While held, a bar fills its hit area at full opacity and shows dots with haptic feedback. A line previews the target edge.
Release snaps to the nearest left/right edge and saves `side`/`offset`; cancellation restores position and appearance.
Rotation recalculates the available area. Top/bottom snapping is not supported.

`z2-edge delete ID` removes that panel and its items, returning its ID and item count. Other panels remain.
An open deleted panel closes and its handle disappears. Use `remove ID:item` to delete a single item.

App addition offers Full screen (normal launch), Freeform, Split screen, and Ask every time.
The choice is saved in `run=z2-intent -p PACKAGE --window full|freeform|split|ask` and can also be edited through the CLI.

Add item and Edit support all six item types. Blank optional fields clear their settings. Cancel discards input; saving an item changed externally is rejected.
Screen off hides panels and handles. Wake and unlock notifications trigger another check of unlock state before restoring handles. Screen off and service shutdown cancel waiting. Transient drawing failures no longer unregister wake notifications.

Panel and tab deletion requires confirmation within Settings. Notes are saved before deletion; local note files are removed with the panel, while external note files and child tabs are retained. Deleting the last panel disables panels.
Enabling panels when none exist creates a Main bar on the right and opens its empty panel. Existing panels are preserved.
Panel settings supports renaming the current panel or tab, adding a parent panel, moving child tabs earlier or later, and opening another parent panel. Explicit save and move actions write names and tab order to the definitions.

Thin bars draw their entire background at the configured width while retaining a touch target of at least 24dp.
Freeform launch behavior can be adjusted with options after `z2-intent --window freeform` (0.8.563). Edit the item command in panel settings or set the same `run` field through `z2-edge set ID:item 'run=...'`.

| Option | Launch behavior |
|---|---|
| `--reuse-task` | Allow task reuse (the default since 0.8.566; kept for saved commands) |
| `--os-bounds` | Let the OS choose position and size (the default since 0.8.566; kept for compatibility) |
| `--bounds-only` | For comparison, request inner 80% bounds without the private window-mode key |
| `--no-scale` | Skip Motorola's scaled-freeform request for this launch |

Since 0.8.566, the default clears the multiple-task flag and requests only the window mode, leaving existing tasks and remembered bounds to Android. Launches no longer submit inner 80% bounds every time. `--reuse-task --os-bounds` can be combined. Combining `--bounds-only` with `--os-bounds`, or using these options outside freeform mode, is rejected.
These switches allow comparison with the device’s standard launch path. Whether they resolve layout changes during navigation or restore window decoration still requires device verification. They do not change OS settings.

Freeform position, size, movement, resizing and decoration belong to the OS. Since 0.8.569, Motorola devices receive a vendor flag requesting the standard scaled window with inheritance across activity/task launches by default. “Do not scale down” in the item editor, or `--no-scale` in the CLI, keeps the previous window-mode-only behavior per item. The flag applies only to normal freeform requests, not `--bounds-only`. Other manufacturers receive no extra request from this setting. No overlay is added to track external window borders. Split brings the terminal to the foreground and launches a new task beside it.
Reported differences include OS-standard windows scaling the entire content while this launch path only narrows the viewport, followed by full-size content returning inside the window when moving it after navigation. Bounds-only launches can show the same problem; adjusting initial bounds is not considered a fix.
No external app or root is used. Freeform requests the non-public AOSP Bundle key `android.activity.windowingMode=5`, whose support depends on the OS. `ActivityOptions.setLaunchBounds` is used only for explicit `--bounds-only` requests. Split requests `FLAG_ACTIVITY_LAUNCH_ADJACENT`.
The Motorola extension is the non-public Bundle key `key_moto_flags` with `0x100000` (inheritable global freeform). Its value, task classification and inheritance were checked in the Android 16 framework implementation; support on future OS versions or other models is not guaranteed. No external service, signature permission or hidden method invocation is used.
Freeform fails if not enabled on the device. Entering split screen is supported from Android 12L; earlier versions require an existing split session.
The OS controls the final mode and reuse of existing tasks. Full screen means ordinary launch, not forced maximization of an existing window.

Use “+ Tab” in settings or `z2-edge tab main work Work`. Each tab is a panel referenced by the parent’s `tabs=work,home`.
The parent’s own items form the first tab; child handles are hidden. Up to 64 panels, 64 items each.
Nested, cyclic, and multiple-parent references are rejected. `list` marks children as `tab:parentID`.
Deleting a child detaches its reference; deleting a parent preserves child panels.

In settings, hold an item name and drag before/after another item to save every item’s `order`. Dropping outside leaves the order unchanged. Settings also expose item deletion and app launch-mode selection.

Choose Add item → Text input → Read and save a file to create a persistent editor. It retains the existing `type=note` format and supports Undo and Redo.
A blank note label (including whitespace only) hides the internal ID, icon and heading row so the body starts at the top (0.8.582).
The note item settings include Ruled lines (off by default, `note-lines=on|off`), including wrapped lines.
While editing, icon buttons for Undo, Redo, smaller text and larger text appear below the body. Text size is 10–32sp (default 16sp, `note-size`), saved per note item and applied to both reading and editing.
Undo and redo history is saved across saves, panel closure and app restarts: up to 100 history entries per text file, with a combined 256 KiB budget for past and future text (fewer entries for long notes). History lives in the panel's `.note-history/` directory (the shared home's `.z2term/edge/.note-history/` for external text files). External text changes or damaged history prevent stale history from being used. A history-only save failure keeps the already saved document intact.
Text defaults to `~/.z2term/edge/panelID/itemID.txt`; `file=~/memo.txt` selects another shared-home file.
Relative paths use the shared home; absolute paths must be accessible to the Android app and are not translated from guest-only paths.
UTF-8, up to 64 KiB. Closing, Back, tab switching, screen-off, close/off save edits; changes also save every 10 seconds while open.
External changes and write failures preserve edits in `.recovery` instead of overwriting the original and display a message.
Removing an item preserves its text file. Deleting a panel first saves edits, then deletes its internal text files; external `file` targets remain.
Notes do not accept live-value `push`; edit the file and reopen the panel.

`z2-edge toggle` toggles the service; `z2-edge open main --toggle` toggles the panel.
A tile assigned the single command `z2-edge toggle` displays the actual enabled state. Enabling requires an unlocked screen.
“+ App” in settings or the optional “+” on the panel shows an icon/name list with a search field matching names and packages. Selecting saves the app; rotation preserves the query. `z2-app pick` returns the selected package on stdout;
cancellation or a 120-second timeout fails. Other APIs remain available while selecting. When calling it from a panel command, set `timeout=130` or longer.

Accessibility is separate from overlay permission. `z2-key permission` opens service details with a fallback
to the service list. `z2-key app-info` opens App info for allowing restricted settings. The user operates the switch.
`z2-key status` distinguishes OS `enabled` from service `connected`. Simple `z2-key` items show a setup prompt
when disconnected; arbitrary shell scripts are not inspected. Check this status if actions fail after an APK update.
`z2-key split` attempts the OS action even when absent from the action list and reports rejection. App launch modes use the public Android requests described above.

An edge bar or draggable button opens your own controls while you use other apps. Commands provide
the contents: display text, toggle a state, choose a list entry or enter text.

Run `z2-edge permission` and allow Android to display z2term over other apps. Return to the terminal
and create a panel with a clock and a Home button:

```sh
z2-edge handle main button --at 85%,60% --label Controls
z2-edge panel main width=80% height=60%
z2-edge set main:clock type=text label=Clock 'run=date' every=30 order=1
z2-edge set main:home label=Home 'run=z2-key home' order=2
z2-edge on
```

Drag the floating button to move it (no hold with the default `open=tap`); its position is saved. Switch to a bar with
`z2-edge handle main bar --side right --offset 30% --length 6% --size 6 --open swipe` and swipe inward to open it.
Close with an outside tap, Back, or the optional Close button. Stop everything with `z2-edge off` or Stop panels in the notification.

For Back, Recents and other global actions, run `z2-key permission` and enable “z2term Android actions”.
If Android restricts the switch, allow restricted settings from z2term App info first. `z2-key status`
lists the actions the device provides. Auto-scroll uses focused-window metadata. Explicit element macros, selection and inspection also read target-window text, descriptions and IDs, excluding editable/password fields.

To capture only part of the screen, use `z2-shot`. Set an item to `run=z2-shot`: pressing it hides the panel and its handle, takes one screen image, and lets you draw on that still picture. `z2-shot free` (the default) traces freehand, `z2-shot rect` drags out a rectangle and `z2-shot oval` an ellipse. **Lifting your finger confirms the outline**; Redo draws it again. Then choose whether the outside is transparent or keeps the original background, and press Save to write a PNG into `Pictures/z2term`, or Share to hand it to another app. **The panel, its handle and the selection UI never appear in the image** — they are hidden before the capture, and you draw on the still picture afterwards. Needs Android 11 or later and the permission from `z2-key permission`.

Use `z2-app list` to find an application package, then set an item to `run=z2-intent -p PACKAGE`.
It automatically uses the application label and full-color icon; explicit `label=` and `icon=` override them.
`z2-app icon PACKAGE -o ~/app.png` also exports that icon as PNG.

```sh
# A display updated by another command or automation
z2-edge set main:message type=text label=Status
z2-edge push main:message 'Finished'
z2-edge badge main 'Done'

# Send entered text to a file, adding a newline in the shell
z2-edge set main:note type=input label=Note 'run={ cat; printf "\n"; } >> ~/notes.txt'

# Lists use label<TAB>value; the selected value reaches on-select as $1
z2-edge set main:pick type=list label=Actions 'run=printf "Home\thome\nRecents\trecents\n"' 'on-select=z2-key "$1"'
```

For `type=toggle`, `run=` changes the state and `state=` reads on/off, true/false or 1/0.
`z2-edge state main:ITEM on` updates the displayed state from outside. Use `out=panel` to show an action's
result in place or `out=notify` to receive it after closing the panel.

Text/toggle/list refresh on opening. `every=30` adds a 30-second interval only while the panel is open.
Closing, screen-off and reload stop readers; explicit actions finish unless you request `off`.
Closing, reload and rotation discard unsent input. Command timeout defaults to 30 seconds; `timeout=` allows up to 300.
For event updates, register a command invoking `z2-edge push` with the existing `z2-when`.

`z2-edge get ID` returns saved panel settings; `z2-edge get ID:item` returns saved item settings, both as `key=value` lines. Omitted defaults, comments and live display values are not included. Reading does not change definitions or require panels to be enabled.

Definitions live in `~/.z2term/edge/`. Run `z2-edge reload` after editing files directly. Copy the folder
to back it up; the current settings export does not include it. Pushed values and badges reset on app restart.
See `z2-edge --help` for types and options. Other-app freeform windows and launching apps into split screen
are not supported.

---

## 10. Troubleshooting (FAQ)

**Q. `sshd` won't work / the port is wrong**
→ Type `sshd` (not the full path `/usr/sbin/sshd`). Set the port via `Port` in `/etc/ssh/sshd_config`, or `sshd -p <number>`. Use 1024 or higher.

**Q. I can't type Japanese**
→ Switch to Japanese flick with the "あ" key on the left of the keyboard. Typing kana shows the candidate bar, so you can convert to kanji with the "変換" key or by tapping a candidate (long sentences are predicted automatically per chunk). If you want smarter conversion, switch to the phone's standard keyboard (Gboard, etc.) with the "⌨" toolbar button.

**Q. I can't select text well / can't select to the edge**
→ Long-press first, then drag with your finger. **If one word is all you need, double-tap is quicker** (0.8.420). To reach the edge, move your finger toward the top/bottom edge of the screen and it auto-scrolls. Drag near the end of the selection to change its range.

**Q. My resident servers (sshd, …) were stopped after updating the app**
→ **That is expected.** Replacing the app makes Android shut it down once, which takes the resident servers with it. "Auto-start on boot" applies when the **phone** boots, so it does not bring them back after an app update. After updating, open ⚙ Settings → "Permissions and notifications" → "Resident servers" → "Manage servers" → **Start** again.

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

### Use commands without Linux

Run `z2help` for the supported scope. Helpers include `z2-notify`, `z2-battery`, `z2-torch`, `z2-state`, `z2-key`, `z2-action`, `z2-when`, `z2-edge` and `z2-tile`, within Android's available features and permissions.

```sh
z2-battery
z2-notify "Ready"
z2-when charge:start run 'z2-notify "Charging started"'
```

Notification permission and system event capture for charging triggers remain necessary. To create a panel, run the first command, grant overlay permission in Android, then continue:

```sh
z2-edge permission
z2-edge handle quick button --at 85%,60%
z2-edge set quick:light type=run 'run=z2-torch toggle' label=Light
z2-edge on
```

Run shell macros with `sh "$HOME/.z2term/macros/name.sh"`, using the system shell and Android utilities. Linux packages, GUI, Linux servers, `z2-macro` sample installation, `z2-audio` and `z2-img` require Linux.

Settings and rules stay in the shared home and carry over when Linux is installed. Use `$HOME` / `~/` for portable file paths. Installing an OS also switches background commands to the selected Linux environment, so commands depending on Android-only absolute paths or utilities may need adjustment.


**Play terminal audio (0.8.588)**: Open a new terminal tab and run `z2-audio install` once. Prefix the original command with `z2-audio run`, for example `z2-audio run python3 /root/player.py /root/movie.mp4 --audio`. To use the original commands inside a shell, start `z2-audio run zsh`. Its dedicated audio connection ends with the command. No GUI setting is required. Player volume options and Android media volume remain available. `z2-audio -h` / `--help` explains usage. Requires a PulseAudio-compatible player in a local Linux terminal.

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
| `z2-share --file /sdcard/Download/report.pdf` | Share file contents with another app; multiple files supported. [Sharing and input forms](SHARE-WORKFLOW.md) |
| `z2-open <URL or path>` | Open a URL or file in the default app |
| `z2-view <file.html> [title]` | **Read a page you made here, inside z2term** (0.8.628). No server and no browser. JavaScript and network loads are off, and only `http(s)` links leave for your usual browser. The phone's theme arrives as CSS variables (`--z2-bg` `--z2-bg2` `--z2-fg` `--z2-dim` `--z2-line` `--z2-accent`), so a page written with them follows the terminal colours. Up to 4 MB; embed pictures as `data:` URIs (remote ones are not fetched) |
| `z2-qr` | Scan a QR code with the camera and show one Open button for its content |
| `z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]` | **Make a QR code** (0.8.630). Nothing to install — the app encodes it, the same way the QR tools screen does. With no TEXT it reads standard input. A PNG by default (`~/.z2term/qr/qr.png`), and the path is printed; `-t` draws it here with block characters instead. `-p` is the size to aim for in pixels (default 600), `-m` the quiet zone in modules (default 4). Up to 2000 bytes |
| `z2-img [-w COLS] [-r ROWS] [--clear] <file>...` | **Draw a picture in the terminal** (0.8.495). PNG / JPEG / WebP / GIF / BMP. Pass `-` to read one image from stdin (`curl -s <url> \| z2-img -`). By default it **fits the terminal width**; `-w` (columns) and `-r` (rows) set it explicitly. `--clear` removes every picture drawn so far. Given several files, it prints each name on its own line before the picture. ⚠ **Pictures only appear in a z2term tab, or in a terminal that speaks the kitty graphics protocol.** Over `ssh` or inside a pager you just get gibberish. ⚠ By default it **only writes to a terminal** — down a pipe or into a file the bytes are indistinguishable from garbage — so pass `-f` if you really mean it. ⚠ The aspect ratio assumes a cell is twice as tall as it is wide; if it looks squashed, tune it with `Z2_IMG_ASPECT=0.45 z2-img photo.jpg` (smaller = taller). ⚠ **Large photos are subsampled while decoding** (4 megapixels max). Only a few hundred pixels ever reach the screen, so nothing looks different, but the original resolution is not kept in memory |
| `z2-clip get` / `z2-clip set [text]` | Get / set the clipboard (set reads stdin if no argument). ⚠ **Writing only works while you are looking at z2term** (or while z2term is the input method you use) — since Android 10 a `set` from a macro running in the background is dropped silently. For macros triggered by calls, SMS or notifications, use the `z2-notify -c` copy button instead (0.8.335) |
| `z2-battery` | Show battery level / charging state (JSON) |
| `z2-vibrate [ms]` | Vibrate (default 200ms) |
| `z2-say <text>` | Speak text via the device's text-to-speech (reads stdin if no argument) |
| `z2-torch [on\|off\|toggle\|status]` | Turn the flashlight on/off/toggle (default toggle; prints the resulting state) |
| `z2-media [playpause\|play\|pause\|next\|previous\|stop]` | Control media playback (default playpause) |
| `z2-volume <up\|down\|mute\|unmute\|N\|N%>` | Adjust media volume (prints the resulting current/max) |
| `z2-audio install` / `z2-audio run COMMAND...` | Play Linux command audio on Android without a GUI; release its connection on exit |
| `z2-sensor [light\|accel\|proximity]` | Read a sensor once as JSON (light/accelerometer/proximity; default light) |
| `z2-state [key]` | **Current device state** as JSON; with a key, just that value (`screen` `locked` `idle` `charging` `plug` `level` `temp` `wifi` `ssid` `ringer` `airplane` `headset` `bt_audio` `volume` `volume_max`). E.g. `[ "$(z2-state charging)" = "true" ]` |
| `z2-screen keepon <1h\|30m\|90s>` | **Stop the screen turning off by itself, for that long** (`keepon off` puts it back early, `status` shows what is left). It changes the OS-wide screen timeout, so **it holds with the app in the background**. ⚠ Not the toolbar's 🔅, which only lasts while the app is on screen. The original value is always written back at the deadline (even if the app is killed or the device reboots). Max 24h in one go. **Needs "modify system settings"** (Settings › screen timeout) |
| `z2-tile set <1-12> <macro.sh\|command>` | **Put a macro or command on the quick-settings panel** (`list` / `clear <1-12\|all>`, `-l` for the label). Tap to run, tap again to stop (same deal as the widget buttons). The tile looks "on" while it runs (the colour comes from the OS). A locked device is asked to unlock first. ⚠ **There are exactly 12 slots** (raised from 4 in 0.8.294; Android fixes the number at build time, so there are spares). **A slot you have not assigned anything to stays out of the quick-settings list** (since 0.8.271, **with nothing assigned no tile is listed at all**; before that slot 1 remained as a signpost). **Assigning a slot asks you right there whether to put it on the quick settings panel** (0.8.355, Android 13+). The dialog carries **the name and icon you just assigned**, so you are not guessing on the edit screen. Say no and nothing is placed. ⚠ It only appears while z2term is in front, and **only one can be asked at a time** (a macro assigning two slots only asks about the first) — use **`z2-tile add <slot>`** for the rest. **`-i <drawing>` settles the drawing that dialog shows, in the same line** (0.8.357; e.g. `z2-tile set 4 backup.sh -l backup -i sync`). Names come from the `z2-icon sample` list. ⚠ An unknown name **assigns nothing at all** (a tile placed with the wrong drawing is harder to spot). ⚠ **A tile already on the panel does not need it** — `z2-icon` changes those on the spot; `-i` only matters *before* placement. ⚠ **Placing a tile is still your call** (Android does not let an app put its own tiles there). On the edit screen, look for **`z2term <slot number>`**: ⚠ **that list shows neither the name nor the icon you assigned** (Android gives no way to change them at runtime), so use `z2-tile list` to see which number is which. ⚠ `clear`-ing a slot also **removes that tile from the panel** (reassigning means placing it again). **A slot holding `z2-screen keepon` is special**: the "on" look means "the screen is still being held", **the time left is appended to the name** (e.g. "no sleep 60m"), and tapping releases it (the figure is read when the panel opens and does not tick while it is open). When turning something off is its own command, **`--off` puts both on one tile** (`z2-tile set 3 z2-torch on --off z2-torch off -l torch`): taps alternate between them and the tile looks "on" while it is on. Ordinary command pairs remember their toggle state. Direct `z2-torch` assignments follow actual Android observations, including external changes. **A macro can take arguments** (`z2-tile set 2 'remind.sh ask' -l remind`; 0.8.275 — the **first word** decides whether it is a macro). Two slots on the same macro get the same label, so give them `-l`. ⚠ A name ending in `.sh` that is not in `~/.z2term/macros/` is **rejected when you assign it** (letting it through would make it a command, which is not found, and the tile would do nothing). Scripts outside the macro folder go in with a full path. **When a tap seems to do nothing, look at `~/.z2term/tile/run.log`** — failures never reach the screen |
| `z2-icon pick <notify\|1-12>` | **Put your own drawing on the status-bar and tile icons** (0.8.294). **A tile fills its own icon in when the assigned name gives it away** (0.8.299 — `remind.sh` gets a clock, `battery-alert.sh` a battery). Anything you set yourself wins and is left alone from then on. **To go back to automatic, `z2-icon auto <slot\|all>`** (it overwrites your drawing and re-picks). **`z2-icon list` tells you which drawing is on which slot, by name** (0.8.300 — `auto` means it was picked for you, `custom` that you set it; `z2-icon list -p` prints the drawings as well). Pick one from the list by number (`z2-icon sample` lists them; `z2-icon sample <target> <name>` sets one directly). **A drawing of your own can be named and kept in that list** (0.8.300): `z2-icon save <target> <name>` adds it, after which it can be chosen by number or name exactly like a shipped one, so you can put the same drawing on another slot. `z2-icon forget <name>` drops it from the list — whatever you already put on a target stays where it is. To draw your own, `z2-icon edit <target>` opens `$EDITOR` on the grid — save it and it applies at once (`.` ` ` `0` `-` `_` leave a cell empty and **anything else fills it**, so draw with whatever you can see). Blank space around the drawing is ignored and it gets centred, so you need not fill every line. **The grid is 24 / 48 / 64 across** (0.8.379). The status bar shows these about 24px across, so 24 dots are plenty there — but **a tile is drawn much larger**, and there 24 dots show as steps. `z2-icon grid <24\|48\|64>` sets the grid new drawings are made on (24 by default; with no argument it prints the current one), and `z2-icon scale <target> <24\|48\|64>` lays a drawing you already have out on another grid. **The outline is smoothed on the way out** (0.8.382), so a drawing made on 24 dots still comes out smooth on a tile and the grid is not something to worry about day to day (`z2-icon show` prints the drawing as you drew it; the tile is smoother). `grid` and `scale` are for when **you** want to draw finer: `scale` halves the diagonal steps as it lays the drawing out, so what you have becomes the base to draw on. ⚠ **24 → 48 is the cleanest** (exactly double; 24 → 64 smooths up to 48 and lays the rest out, so some steps remain). ⚠ Flat areas and lone dots are only made thicker (apart from four corners being rounded, the shape you drew is untouched). ⚠ Laying one out on a smaller grid **drops thin lines**. ⚠ **A bigger grid does not make a bigger icon** — fill the grid, or the drawing comes out smaller than the one it replaced. From a file it is `z2-icon set <target> <file>` (`-` for stdin), `z2-icon show <target>` prints the current one, and `z2-icon clear <target\|all>` puts the built-in icon back. The targets are **`notify`** (one drawing for every notification this app puts out) and **slots 1-12** (one drawing each). ⚠ **There is no colour** — Android repaints these icons in a single colour of its own (tiles change colour between on and off), so only the shape is yours. ⚠ The status bar shows them about 24px across, so there detail finer than 24 dots is lost (what `show` prints is what appears; a 48 or 64 drawing is printed with two cells folded into one character so the line does not wrap on a phone). Which slot is on which grid is the 4th column of `z2-icon list`. ⚠ **Three things cannot be changed**: the icon in the quick-settings **edit** screen (where you drag the tile from), the **file-picker root icon**, and the **launcher icon** — Android fixes those at install time. Placed tiles and posted notifications do change |
| `z2-alarm at\|daily HH:MM [name]` | **Time trigger**: writes an `alarm` event into `events.jsonl` at that time (`in 5m` / `list` / `cancel <id\|name\|all>` too). Unlike cron it fires during Doze. **Whether it lands on the minute is in `z2-alarm list` as `exact`** (0.8.333): `true` means on the minute, `false` means Doze only offers a slot every 9-15 minutes, so a phone left with the screen off can be that late (battery saver too). What flips it to `true` is **Settings › Apps › Z2Term › Battery › Unrestricted** (battery optimisation off). ⚠ The app never asks for the "alarms & reminders" permission — Android grants exact alarms to apps exempt from battery optimisation, and where it is not exempt scheduling quietly falls back (nothing is dropped). The same applies to `z2-when time:` and the deadline of `z2-screen keepon`. ⚠ **Before 0.8.302 the clock inside the distro was fixed to UTC**, so a wall-clock time like `18:30` was scheduled off by the zone offset (9 hours in Japan). From 0.8.302 it follows the device clock — **re-create anything you scheduled with an older build** |
| `z2-session list\|new\|send\|capture\|attach\|close` | **Drives this app's own tabs.** `list` shows them (index, id, name, marks: `*`=visible, `!`=busy, `?`=not started, `@`=attached), `new [name]` adds one, `send <target> "text"` **only inserts** into that tab (add `--enter` to actually run it), `capture [target]` pulls the on-screen text, `close <target>` closes it. `<target>` is the index from `list`, an id, or a tab name. E.g. ``n=$(z2-session new build \| cut -f1); z2-session send "$n" 'make -j2' --enter`` |
| `z2-session key <tab> <key>...` | **Send keys to that tab** (0.8.311). `z2-session key 2 C-c` for Ctrl+C, `M-x` for Alt+x, plus specials like `F5` `Up` `Home`; several at once as `key 2 F5 Up Home`. Anything else goes as bytes: `key 2 --raw '\x1b[A'`. ⚠ **`send` cannot deliver Ctrl+C** — it is a paste, so you only get the characters `^C`. ⚠ Shift-modified character keys such as `C-S-a` are **refused**: a terminal cannot tell Shift apart, so it would be the same as `C-a` (function keys such as `S-Tab`, `S-Up` and `C-M-F5` remain distinguishable) |
| `z2-session attach <tab>` | **Stay connected to that tab** (0.8.366). After `z2-session attach 2` you just type into it like any terminal (Ctrl+C works, so do full-screen programs). **Leave with `Ctrl+]`** (any time). `~.` at the start of a line works too (as in ssh; type `~~` for a literal `~` there). ⚠ **Over SSH, use `Ctrl+]`** (0.8.370) — `~.` is eaten by the ssh in front of you and **drops the SSH session**. To send a literal `Ctrl+]` to the tab, type `~` at the start of a line and then `Ctrl+]`. ⚠ While attached the tab follows **your** window size — on the phone it wraps wrongly and looks broken, but it goes back when you leave. ⚠ **The tab on the phone stays live**, so what you type shows up there too. ⚠ A notification appears while you are attached (it keeps the app from being killed mid-session). ⛔ **You cannot attach a tab to the one you are typing in** (0.8.419) — its own output would come back as input forever with no way to stop it, so it is refused with a message. Attaching back to A from inside an A→B attach is refused for the same reason |
| `z2-usb list` / `z2-usb allow [number]` | **Use a USB device connected to the phone from Linux** (0.8.425). Run `list`, then `allow`, and approve Android's permission sheet. The number is optional when there is only one device. An ordinary USB-A-to-USB-C adapter works if it carries **data** and the phone supports USB Host/OTG. Permission lasts until the device is unplugged; run `allow` again after reconnecting it. ⚠ This covers dynamically linked programs on z2root that use ordinary `open` / libusb. Statically linked programs and programs issuing the `openat` system call directly bypass the shim and are not covered |
| `z2-server list\|start\|stop\|status <server>` | Start / stop **a resident server you registered**. ⚠ A daemon started straight from a rule runs **outside the residency frame**, so it stops answering once the screen is off; starting it here puts it inside. `<server>` is the index from `list`, an id, or the name from the app. E.g. `z2-when wifi:connect run 'z2-server start sshd'` |
| `z2-when <trigger> run <cmd>` | **Automation hub.** Auto-run a command on charge / battery / time / device events (see "Automation hub" below). Also `list` / `remove <id\|all>` / `on\|off <id>` / `log <id>`. **To narrow it down**: `if=` (all of them) / `if_any=` (any one of them, 0.8.372); **to do something else when it does not match**: `else=` (0.8.372). E.g. `z2-when charge:start run ~/.z2term/macros/backup.sh` |
| `z2-macro list\|install <name>` | **Bundled macro samples** into `~/.z2term/macros/` (`diff` / `show` / `run` / `dir` too) — a starting point for your first macro. **`list` shows the state of each one** (`new` / `same` / `differs`; 0.8.332). ⚠ `install` **never overwrites** (your edits are yours). That means a fixed sample never reaches a copy you already have, so `install` tells "the same thing is already installed" apart from "yours differs from the bundled one", and in the latter case points at `z2-macro diff <name>` (look first) and `z2-macro install -f <name>` (replace with the bundled one). ⚠ **`differs` does not mean "out of date"** — your copy can be the one that is ahead (an extension never folded back into the app), so always read the `diff` before you use `-f`. Bundled: `watch-basic` / `battery-alert` / `daily-report` / `otp-clip` / `otp-sms` / `unknown-call` / `remind` / `rss` / `rss-open` / `qr` / `md`. On install it also tells you **how that script is meant to be run** (drive it with `z2-when` / assign it to a widget button / register it as a resident server). ⚠ **No bundled sample belongs in a resident server** (0.8.338; they all run once and exit from `z2-when` or a button, so residency both restarts them every time they finish and burns battery while idle) |
| `z2-update [--check] [--keep] [--dir <folder>]` | **Replace z2term itself with a newer version** (0.8.371). It checks GitHub Releases, and if there is a newer one, downloads the APK and takes you to **the install screen**. ⚠ **The last tap is yours** — Android has no way for an app to replace itself silently. ⚠ The first time it needs "Install unknown apps" (it says so if it is missing). `--check` only looks, `--keep` leaves the APK behind, `--dir` changes where it lands (by default it goes inside the app and is deleted once the update goes through). Settings > App info has the same button and the same two settings. ⚠ **Installed from F-Droid or a store? It refuses** — update it there. e.g. `z2-when time:daily=03:00 run 'z2-update'` |
| `z2-intent [-a ACTION] [-d URI] [-p PKG] [-n PKG/CLS] …` | Fire an arbitrary Android Intent (launch apps, open settings, set alarms, … see `docs/en/MACRO-GUIDE.md`) |


**Bell and heart correction (0.8.590)**: The bundled icons now join their rounded and straight sections smoothly. Assigned, unedited stock drawings also use the corrected shapes after updating, including resized 24/48/64-cell versions. Drawings you have edited keep their existing shapes.

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

**Read them as a page** (0.8.628)

```sh
sh ~/.z2term/macros/rss.sh view
```

Everything collected becomes **one page grouped by site**, opened inside z2term — no server and no browser. Each article carries an Open button that takes you to the original site. The notification gains a second button as well: Open for that article, List for this page. Note that opening a screen from a notification button needs the "display over other apps" permission (the same one the edge panel uses). The page is shown with JavaScript and network loads switched off.

⚠ **If you already installed `rss.sh`, install it again**: look at `z2-macro diff rss.sh` first, then `z2-macro install -f rss.sh` (your own edits go too).

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
# Since 0.8.630 qrencode is not needed (the app encodes it). Install it once per tab only
# where the app is out of reach, such as over ssh:
#   apk add libqrencode-tools      # Alpine (Arch: pacman -S qrencode / Ubuntu, Kali: apt install qrencode)

qr.sh "https://example.com"          # encode a string and draw it
qr.sh -f notes.txt                   # encode the contents of a file
z2-clip get | qr.sh                  # encode what you just copied
qr.sh -o ~/qr.png "text"             # save a PNG (nothing is drawn)
qr.sh -t "text"                      # print blocks instead of an image
qr.sh -h                             # the full help
```

- **The app does the encoding** (0.8.630). Inside a tab of z2term there is nothing to install. If `qrencode` is there it is used instead, so nothing changes for you. ⚠ Where the app is out of reach (over `ssh`), `qrencode` is still needed; only when neither is there does the script print the install command for that tab and stop.
- ⚠ **The image only shows inside a tab of this app.** On a terminal that cannot show images (over `ssh`, say) you get gibberish, so use `-t` there.
- ⚠ **To scan with a camera, prefer the image or the PNG.** Blocks (`-t`) can leave gaps between rows depending on the font: readable to you, not to the camera.
- Long input is **split into several codes at line breaks**, numbered `[1/3]`. Scan them in order.
- If it looks squashed, adjust the ratio: `Z2_QR_ASPECT=0.45 qr.sh "text"` (smaller = taller; default 0.5).

### Read Markdown (0.8.629)

A sample that lays out `README.md` or your own notes **so they can be read here, on the terminal**.
Nothing to install: `sh` and `awk` are enough.

```sh
z2-macro install md                  # install it

md.sh README.md                      # render it here, in this terminal
md.sh -v README.md                   # open it inside the app (z2-view)
md.sh README.md | less -R            # a long document, a screenful at a time (-R keeps colours)
cat notes.md | md.sh                 # read from standard input
md.sh -o page.html README.md         # write the HTML and stop (nothing opens)
md.sh -h                             # the full help
```

- Headings, paragraphs, lists (nested, numbered, `[ ]`/`[x]`), quotes, code, tables, rules, links, pictures and front matter (`---`) are handled.
- **Links can be tapped** (OSC 8). A terminal without it (over `ssh`, say) shows them as text.
- **Pictures appear in place** (`z2-img`). ⚠ Only inside a tab of this app; anywhere else you get the caption and the path.
- **Lines wrap to the width of the screen** and never start with a closing punctuation mark. Pick the width with `-w 60`.
- **A table too wide for the screen breaks into "heading: value" lines**, so it still reads on a phone.
- **`*emphasis*` is underlined.** This terminal does not draw italics, so otherwise it would look like plain text (`man` does the same).
- `-v` embeds pictures that are on this device into the page (1.5 MB each at most). ⚠ The reading screen fetches nothing from the network, so `http(s)` pictures stay as their caption.
- ⚠ **Setext headings (`===` / `---`) and reference links (`[text][1]`) come out as they are written.**
- If you have rules and arrows set to full width (Settings → ambiguous width), run `Z2_MD_AMBIWIDTH=2 md.sh README.md` so the table columns line up.

### Automation hub (`z2-when`)

Auto-run a command **when you start charging / the battery drops / a set time arrives**. No need to `tail` events yourself — you just **declare** the rule and it runs, even with the app closed and across reboots.
**However, every trigger except time, SMS and notifications (charging, battery, Wi‑Fi, sensors, new files, device events) only works while "detection" is on** (Settings › permissions and notifications): Android does not deliver those events to an app that isn't resident. SMS needs the receive-SMS permission, and `notify:` needs notification access.

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
  - `charge:start` / `charge:stop` — charging started / stopped. **Only works while "detection" is on** (Settings › permissions and notifications)
  - `battery:below=N` / `battery:above=N` — the moment the level **crosses** N% (e.g. drops under 20). Also **only works while "detection" is on**
  - `time:daily=HH:MM` (every day) / `time:at=HH:MM` (once at the next HH:MM, then auto-disabled) / `time:every=30m`·`2h` (fixed interval, min 1 minute)
  - `time:cron='min hour dom month dow'` — a cron expression for finer control (e.g. `'0 3 * * *'` daily at 03:00 / `'*/15 * * * *'` every 15 min / `'0 9 * * 1-5'` weekdays at 09:00). Day-of-week is 0–7 (0 and 7 are Sunday). **Always quote it** since it contains spaces.
  - `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<name>` — when Wi‑Fi connects / disconnects / joins a given network. **These Wi‑Fi triggers only work while "detection" is on** (Settings › permissions and notifications). Using the network name (SSID) also needs location permission. Inside the command, `Z2_WHEN_SSID` holds the connected network's name.
  - `net:online` / `net:offline` — when **a connection that works appears or goes away** (added in 0.8.264). `net:wifi` / `net:mobile` / `net:ethernet` fire when **the link in use switches to that one**. Unlike the Wi‑Fi triggers these **count mobile data** — "Wi‑Fi went away" alone doesn't tell you whether mobile picked it up or you're out of range. "Send everything once we're back online" and "stop when there's no service" go here. **Only works while "detection" is on.** Inside the command you get `Z2_WHEN_NET` (the link now) and `Z2_WHEN_NET_PREV` (the one before). ⚠ Going from Wi‑Fi to mobile doesn't change the fact that you're online, so `net:online` **does not fire there** (it looks only at what changed). ⚠ It waits until traffic really gets through, so it fires **a few seconds after the Wi‑Fi icon appears** (a connection you can't actually use isn't called "online").
  - `share:any` / `share:text` / `share:file` / `share:contains=<part>` / `share:ext=<ext>` — when **something is shared to z2term from another app** (added in 0.8.266). E.g. share a URL from the browser and have it downloaded in the background, or `z2-when share:ext=pdf run ~/.z2term/macros/pdf.sh`. **Works even with "detection" off.** Inside the command you get `Z2_WHEN_SHARE` (the text, or the path the file was taken into) and `Z2_WHEN_SHARE_KIND` (`text` or `file`). ⚠ **For a file, `Z2_WHEN_SHARE` is not a path but a string meant to be pasted into a shell** (`~` is left unexpanded, names with spaces come quoted, and several files are separated by spaces). `[ -f "$Z2_WHEN_SHARE" ]` is always false. `docs/en/MACRO-GUIDE.md` has a ready-made splitter under "⚠ The shape of what `share:` hands you". ⚠ What was shared **still goes on the input line as before** (writing a rule does not change that). ⚠ Sharing **opens z2term** — the share sheet targets a screen, so Android works that way.
  - `boot` — when **the device starts up** (added in 0.8.264; no `:`). E.g. `z2-when boot run 'sshd --lan'`. Like time and SMS, it **works even with "detection" off**. ⚠ It waits for start-up to complete after the first unlock (before that the app's files can't be read yet).
  - `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` — when an SMS arrives (any / sender matches / body contains / body has an OTP-looking code). **Needs SMS receive permission** (grant it via Settings › "SMS detection"). Inside the command you get `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY`, and for `sms:otp` the extracted code in `Z2_WHEN_OTP`. Reading SMS directly avoids Android 15's OTP redaction.
  - `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near` / `sensor:proximity=far` — when you shake the device / ambient light (lux) crosses N up or down / the proximity sensor goes near or far. **These sensor triggers only work while "detection" is on**. Sensors cost battery, so only the sensors your rules use are turned on (none run if you don't use them). `shake` only reacts to a **firm shake** (deliberately set high so that walking around doesn't trigger it), and at most once every 3 seconds. Inside the command, `Z2_WHEN_SENSOR` names the sensor and `Z2_WHEN_LUX` holds the light level (for light).
  - `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` — **when a notification arrives** (added in 0.8.236). Handy for confirmation codes that don't come by SMS (email, authenticator apps), e.g. `z2-when notify:otp run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'` (you take it from the notification's "Copy" button — it runs in the background, so `z2-clip set` would not land). The command gets `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT`. Needs **notification access** (Settings > Permissions and notifications > Permissions > Read notifications), and works even with notification logging turned off. ⚠ Things like phone numbers can arrive **wrapped in characters that never appear on screen** (Android adds them to pin down the reading order). z2term **strips them before handing the text over** (0.8.356), so a macro that recognises `$Z2_WHEN_NOTI_TITLE` by its shape as a number just works. ⚠ **Before 0.8.356 incoming numbers hit exactly this and were never caught** (the bundled `unknown-call.sh` read them as names; you do not need to reinstall the macro).
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
| `z2menu list` | List the GUI applications actually installed (name, command, description) |

> **The GUI tab never opens by itself (0.8.254).** There used to be a hook that opened it as soon as you ran something that looked like a GUI app, but **a text editor that merely talks to X for clipboard support tripped it too**, so it was removed. The GUI opens only when **you open the GUI tab yourself** or **you type `z2run`**.

> **Long-press an empty part of the desktop and a menu appears (0.8.498).** "Applications" lists **only what is actually installed**, so every entry really starts something, and "Windows" brings an open window back to the front. ⚠ Up to 0.8.497 that menu — along with moving windows, resizing them and Alt+Tab — **did not work at all**, because z2term was replacing openbox's whole configuration.
>
> ⭐ **KDE applications not remembering their settings is fixed** (found in 0.8.498, solved in 0.8.500). Inside an Android app the mechanism Qt (which KDE is built on) uses to save files did not work, so **not one byte of settings or cache survived** — the same reason a double-tap on a file picked no application. 0.8.500 steers Qt away from that mechanism, so settings persist and double-tap associations resolve. ⚠ This applies when the execution engine is z2root.

> **Press ☰ for a list of the applications you have** (0.8.499). Tap ☰ in the GUI toolbar and the GUI applications **actually installed** in that OS are listed; pick one and it starts (bringing the GUI up with it if it is not running yet). When there are many, the field at the top filters them. ⚠ Only entries that **really start something** are listed — applications you do not have never appear. ⭐ **The list is read once per app launch** (0.8.509); after that it opens instantly from what was remembered. **When you have just installed something, press ⟳ Refresh at the top right** to read it again. You can hide the button under ⚙ Settings › Display › Toolbar.
>
> ⭐ **The list coming up empty is fixed in 0.8.502.** The applications were always there; the app was dropping the list on the way in.

### Connecting
| Command | What it does |
|---|---|
| `z2-noti list` | **List the notifications on screen right now** (app, title, body). Read-only — it cannot press or dismiss them |
| `z2-noti trace start [package]` / `z2-noti trace dump` / `z2-noti trace stop` | **Temporary diagnostics for missing notification text (0.8.588)**. Enable capture, start, reproduce, then dump/stop. Records field types and UTF-16 lengths in memory for five minutes, keeping at most 128 events; returns JSON without text values or automatic file writes. Start clears results; process exit loses them. Missing source content cannot be reconstructed. Build and test-code compilation verified; device tests not run |
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
