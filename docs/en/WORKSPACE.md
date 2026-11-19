# Display placement and Android input/output

0.8.643-alpha (versionCode 651). The app supplies session presentation, input routing, document selection and selected-text exchange. Users keep transformations and other task-specific behavior in commands/macros. No new Android library or third-party code is bundled.

## Split sessions (tab groups)

Tabs you want side by side go into a **group** (1.0.146). A group of two or more tabs takes one entry in the tab bar (with a count, such as `Work ·3`); tapping it stacks its tabs top to bottom (or side by side). A one-tab group is shown as a group too, so its pane title can be ticked for broadcast (1.0.159).

**Making groups**

- Groups are arranged in the **group editor** (the single place since 1.0.162). Open it with **long-press GUI (🖥) → Edit groups…**, the **📡 that slides out of + / 🖥**, or "Edit groups…" in a pane title's long-press menu. It is the same screen whichever way you open it.
- The editor lists every group on the left and the tabs in no group on the right. Tap a tab on the right to put it in the group framed in green; tap a tab in a group to send it back. Long-press a tab in a group and drag to reorder it, or let go without moving to rename it. Long-press a group's name to rename or delete it. "Top / bottom" and "Left / right" under the name choose how its tabs are arranged (1.0.160; top / bottom at first). A tab made with "+ New tab" starts at once (long-press to pick an OS).
- **Long-press the GUI (🖥) button next to +**, choose **Top / bottom** or **Left / right** and a tab: it joins the current tab's group (or the two become a new group).

**Using the panes**

1. Tap a pane to focus it; the first tap only changes focus. A terminal pane that is not focused still scrolls its scrollback when dragged up or down (a flick carries on); focus does not move and nothing is sent to the terminal. A full-screen program's screen has no scrollback, so it does not move (1.0.22). The green border identifies the input target.
2. Drag a divider to resize. Only the two panes beside it change, and neither shrinks out of sight.
3. **Long-press a pane title and drag** it onto another pane to move that tab (1.0.177). **Let go near an edge (the outer quarter) to put it on that side**; let go in the middle to swap the two. Rows and columns can be mixed, for example two side by side on top and one below. Before you let go, the place it will take is shaded green. Each divider can still be dragged. **Tabs in the tab bar can be dragged onto a pane the same way** — the tab joins the group on screen on that side (on a single tab not in a group, the two become a new group). Choosing "Vertical" / "Horizontal" again in the editor puts them back in one row or column. **Let go without moving** for a menu: rename, leave the group, close tab, edit groups.
4. The **expand button** at the right end of a title, or a **double-tap on the title**, shows only that pane. Press it again to return to the group.
5. The toolbar **⚪** turns the log on or off for the whole group (1.0.155). While it is on, a **⚪ / 🔴** appears left of the expand button; pressing it switches that pane alone between recording (🔴) and paused (⚪). Resuming from pause writes on to the same file. The file is created when there is first something to write.
6. Long-press a group's entry and let go to rename or ungroup it (the direction is chosen in the editor). A tab's long-press menu only renames it (1.0.162). **Long-press GUI → One pane** also ungroups the current tab's group.

How many tabs one group holds is set in **⚙ Settings → Tabs per group**: 4 by default, up to as many as fit with each pane keeping at least 4 rows with the keyboard open (at most 8).

A short press still opens GUI. **Settings → Tips** also explains the gestures. Grouped panes omit the GUI-specific outer frame and padding and keep their dimensions when focus changes between GUI and terminal. Keyboard visibility is shared. The built-in keyboard's conversion candidates appear at the bottom of the whole stack, just above the keyboard, whichever pane has focus (1.0.20).

When group names are long, **pinch with two fingers** on the tab bar to widen or narrow every tab and group at once (1.0.148).

Selecting a tab that is in no group shows that tab alone; the group stays, and selecting its entry returns to the pane last focused. Closing a grouped tab leaves the rest stacked. **Double-tap** a group in the tab bar to close the whole group, all its tabs (1.0.163; you are asked first if any of them is busy). Placement does not terminate sessions, and groups are retained for the current app process only.

## Typing into several tabs at once (broadcast)

Sends the same input to every ticked tab **across groups** (1.0.147; across groups since 1.0.156). Groups only split tabs that do not fit on one screen, so ticked tabs receive whichever group they are in. Tabs in no group never receive.

1. **Slide left starting on + / 🖥** at the right end of the tab bar. The 📡 hidden to their right slides in with your finger (1.0.151; it hides when pressed or after 4 seconds).
2. 📡 opens the group editor. Arrange the groups (to only arrange them, just close it) and press **📡 Start broadcast**. The editor opened from long-press GUI → "Edit groups…" is the same (1.0.162). The terminal tabs of every group are ticked.
3. A **📡 OFF / 📡 ON ·3** switch stays in the tab bar: tap it for ON / OFF. It starts OFF (change this with **⚙ Settings → Start broadcasting ON**).
4. Use the tick box at the left of each pane title to choose which tabs receive.

**Who receives**

- ON, typing **in a ticked tab** → every ticked tab, in every group
- Typing **in an unticked tab** → that tab alone
- OFF → the tab you type in alone

When more than one tab receives, the receiving panes are framed in the warning colour (the theme's yellow).

**Sent / not sent**

- Sent: text and keys from the built-in keyboard, toolbar keys, pasting (also from the clipboard history), snippets, voice input, input from the Android keyboard and a physical keyboard
- Sent: what you type from a `z2-session attach` client (a PC, for example; 1.0.161)
- Not sent: text still being converted (committed text is sent), scrolling, taps and mouse, the search box, `z2-session send` / `key`, automation rules
- Cursor keys and pastes are rebuilt for each receiving tab's own state, so a tab running a full-screen program and a shell tab get the same key.
- Tabs that have not started and GUI tabs receive nothing.
- The confirmation for multi-line pastes shows how many tabs receive it.

To stop, slide left on + / 🖥 again and press **📡 Stop**, or use **long-press GUI → 📡 Stop** (1.0.162, so it can be stopped without knowing about the slide).

**From a command** (1.0.149): `z2-session group new work 1 2 3` makes a group and `z2-session group send work 'uptime' --enter` types the same command into every terminal tab of it and runs it. This goes to the named group regardless of the on-screen ON/OFF and ticks. See `z2-session --help`.

## External display

Connect a screen that Android exposes as an independent presentation display. Select its name under **Long-press GUI** for the tab you want to move. Devices offering mirroring alone cannot provide this feature.

Keep that tab active on the phone to use its keyboard; for GUI tabs, the phone surface becomes a relative touchpad. Switch to another tab to work in a separate session on the phone. **Return to phone**, or disconnecting the display, restores the phone presentation without ending the session.

Only one display controls a session's dimensions. Remote VNC keeps the server's real desktop size; local VNC and RDP use their existing resizing support. External presentation closes while the app is in the background and returns with the app. Locked sessions are hidden on both displays.

## Direct local GUI display (experimental)

Enable **Settings → GUI → Direct local GUI display**, then open a new GUI tab. Running tabs keep their original backend. Existing terminal shells also keep their original backend preference, so open a new terminal after changing this option when launching GUI commands from a terminal.

The existing Linux package installation path supplies Xvfb and x11vnc (`xorg-server-xvfb` on Arch, `xvfb` on Alpine/Debian families). First use follows the existing download confirmation setting. These packages are not bundled into the APK.

The app reads Xvfb's XWD framebuffer and draws it on Android. x11vnc runs with `-nofb`; the existing RFB client handles only keys, pointer input and text clipboard. It never requests pixel updates.

Resolution is set from the starting viewport and GUI magnification, limited to 4096 per side and 8,388,608 pixels. Rotation and pane resizing scale the image without changing the desktop resolution. Visible images are sampled at up to 20 times per second; hidden or stopped views suspend reads. This is not GPU acceleration or zero-copy rendering, and no speed or battery improvement is claimed without device measurements. The conventional backend remains the default; remote VNC/RDP retain their existing paths.

## Android document selection from a command

```sh
file=$(z2-file pick 'text/*') || exit
cat "$file"
z2-file save "$HOME/result.txt" text/plain
z2-file --help
```

The default MIME type is `*/*` for `pick` and `application/octet-stream` for `save`. Picked files are copied under `$HOME/z2term-inbox/`, independent of the original. Remove imported copies when no longer needed. Cloud documents are available when their provider is registered with Android.

Document URIs are never presented as Linux file paths. `save` creates a new document through the system picker; Android handles filename collisions. Maximum 512 MiB, five minutes including selection and transfer, one outstanding picker. Cancellation and failure exit nonzero. The Android shell works without Linux installed. Run from the foreground terminal; Android's background activity restrictions still apply.

## Process selected text in another app

Register a command in **Command list → Snippets** and enable **Offer for selected text**. For example, `tr '[:lower:]' '[:upper:]'` uppercases letters. User macros and existing input forms are supported.

Select text in another app, choose **Process with z2term** in its selection menu, select your command, review the output, then choose **Replace selection**. The source app must support Android `ACTION_PROCESS_TEXT`. Read-only sources offer preview/copy but no replacement button.

The original text is supplied on **stdin**, never evaluated as shell code. **stdout** is the result, preserving trailing newlines and empty output. The limit is 60 seconds and 64 KiB each for input/output; NUL input is rejected. Forms use existing safe argument expansion. Failure/cancellation never replaces the original. Opening the activity executes nothing. Commands control their own network access and storage behavior.

## Validation scope

Desktop release/instrumentation APK builds and 73 related unit tests passed; lintDebug reported zero errors.

Desktop tests cover placement, binary transfer/cancellation/limits, CLI quoting and cleanup, literal selected text and empty output, XWD headers, and RFB input without image requests. A temporary real Xvfb/x11vnc desktop verified pixel colors, pointer movement, and absence of framebuffer requests.

The release APK was installed over the existing app with data retained. Startup and the selected-text processing screen were verified on Android. Six instrumentation tests passed in the separate debug package, covering document import, cancellation cleanup, snippet compatibility and existing sharing behavior.

In 0.8.643-alpha, 14 related instrumentation tests passed in the separate debug package. An in-memory GUI transport verified pane bounds on every drawn frame during focus changes across 12 orientation, split and keyboard combinations. Keyboard visibility retention and a touch long-press opening the layout dialog also passed.

Split operation with real desktop servers, physical external display connection/removal, complete document picker flows, replacement in another app, and Xvfb startup/performance on Android remain unverified.
