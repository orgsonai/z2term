# `z2-ui`: letting an AI operate Android apps

Status: **stage 1 implemented in 1.0.30, stage 2 in 1.0.31, stage 3 in 1.0.32** (§10). Only the virtual display (§8) remains to be investigated.

## 1. Purpose

Make z2term an API that sits between an AI and Android apps. Using only terminal commands, an AI can read the screens of apps that offer no API of their own, tap, enter text and check the result.

- The AI repeats "look → decide → make one move → look at the result". Each call is therefore short, and output is machine-readable JSON.
- The apps do not need to cooperate. Only the screen information Android exposes through accessibility (AccessibilityService) is used.
- Any AI works: one running inside z2term, one on a PC over SSH, and later AIs that speak MCP (a common protocol for connecting AIs and tools) all use the same commands.

## 2. What was missing

The existing `z2-action` is built for "a person writes steps, saves them and replays them many times". For an AI it lacked the following (checked in the code).

| Item | `z2-action` | What an AI needs |
|---|---|---|
| Reading the screen | `inspect PACKAGE` returns only `id` / `text` / `desc`; no position, kind, tappability or hierarchy | Position, kind, whether it can be tapped or scrolled, checked state |
| Foreground app | You must already know the foreground package to read it | A way to ask what is in front now |
| Input fields | Input and password fields are excluded together with their content | Where input fields are, and their content except passwords |
| Operation | Only replaying a saved definition; no way to make a single move right away | Immediate, one move at a time |
| Text entry | No instruction | Entering text into a field |
| Images | `z2-shot` expects you to draw around an area | Save one image without interaction |
| Safety | Steps are written by a person, so the accessibility permission is enough | Permission, approval, stopping and a log for the AI |

## 3. Overview

```
AI (inside z2term / over SSH / MCP)
   │  z2-ui look / tap / type … (JSON on stdout)
   ▼
z2api bridge (the existing file hand-off)
   ▼
AI operation desk (new) ── permission, approval, stop and log decided here, in one place
   ▼
Accessibility service "z2term Android actions" (existing)
   ▼
Foreground Android app
```

- No new permission is needed; the existing accessibility service is used.
- `AndroidUiLook` reads and operates the screen. Like `AndroidUiElements` (z2-action) it checks the foreground window and package and does not enter content embedded from other apps. The differences: it reads input fields (except passwords) and returns position, kind and available operations. Capturing uses the same `takeScreenshot` as `RegionShot` and respects Android's capture interval by sharing its timestamp with the edge bar's automatic colouring.
- `z2-action` is unchanged. Saved, replayable steps stay with `z2-action`; the AI's one-move-at-a-time work goes through `z2-ui`. The two never run at the same time (while one is busy, the other answers `busy`).

## 4. Commands

Every output is one line of JSON. Success exits with 0, failure with non-zero. Failures still print `{"ok":false,"error":"<code>","message":"…"}`, so the AI can choose its next move by reason.

### 4.1 Looking

| Command | Returns | Stage |
|---|---|---|
| `z2-ui front` | Foreground package, app name, that app's level, screen size, rotation, whether locked | |
| `z2-ui look` | Elements on the foreground screen (example below). `--all` includes meaningless containers too | |
| `z2-ui look --tree` | The same with parent/child structure (`children` inside `tree`). Numbers match the flat list, so `#N` still works. Omitted containers are skipped and elements hang on the nearest listed ancestor | |
| `z2-ui look --in N` | Read only inside element N of the last `look`, so a busy screen can be read part by part within the 512-element limit | |
| `z2-ui shot [--scale 0.1..1] [--jpeg] [--stable [MS]]` | `--stable` waits until the screen stops moving (two consecutive frames nearly identical, at most MS, default 3000) before capturing (1.0.36). Save only the foreground app window to `/root/.z2term/ui/shot.png` (or `shot.jpg` with `--jpeg`, faster to write and smaller) and return `path`, image `size`, on-screen `box` and `scale`. Image (x, y) is screen (box.x + x / scale, box.y + y / scale). Everything outside the window is cut off so the notification shade and other apps are not captured. Each capture overwrites the same file; files are deleted after two minutes without calls and on stop (MCP deletes them as soon as the image is in the reply, 1.0.37) | |
| `z2-ui status` | Permission state (on/off, stopped, paused, default level, per-app levels, seconds left on approve-for, apps that are always refused) | |

Use `z2-app list` for launchable apps (there is no `z2-ui apps`). `--all`, `--tree` and `--in N` can be combined.

Example `look` output:

```json
{"ok":true,"screen":"s41","package":"org.example.app","app":"Example","size":[1080,2400],"rotation":0,
 "elements":[
  {"n":1,"role":"button","text":"Next","id":"org.example.app:id/next","box":[820,2210,220,120],"can":["tap"]},
  {"n":2,"role":"input","hint":"Search","value":"","box":[60,180,960,110],"can":["tap","type","enter"]},
  {"n":3,"role":"list","box":[0,320,1080,1800],"can":["scroll"]},
  {"n":4,"role":"check","text":"Receive notifications","checked":true,"box":[60,520,960,100],"can":["tap"]}
 ]}
```

- `screen` identifies the screen; every `look` gets a new one.
- `n` is an element number that is valid only within that `look`.
- `role` is one of `button` / `text` / `input` / `password` / `check` / `switch` / `list` / `image` / `other`, derived from the Android class name and the available operations.
- `box` is `[x, y, width, height]` in screen pixels.
- `can` lists the operations Android allows on the element. If the element itself is not tappable but a parent within eight levels is, `tap` is listed (the tap goes to that parent). Fields without a value (such as `desc`) are omitted. Disabled elements carry `"enabled":false`.
- Meaningless containers (no text, description or operation) are omitted to keep the output small; `--all` includes everything.
- Input fields report position, hint and content. **Password fields report only their kind and position, never their content.**
- Limits are 512 elements, 3000 nodes and 1.5 seconds per screen; beyond that `error:"too_many"` / `"too_large"` is returned; narrow the read with `look --in <n>` (the node and time limits stay the same).

### 4.2 Operating

Elements are addressed in one of these ways.

| Form | Meaning |
|---|---|
| `#2` | Element number from the last `look` |
| `text=Next` / `desc=Back` / `id=…` | Exact match, the same as `z2-action` |
| `@540,1200` | Coordinates in pixels; a last resort for screens without elements |

| Command | Action | Stage |
|---|---|---|
| `z2-ui tap <element>` | Tap | |
| `z2-ui long <element> [--hold MS]` | Long-press. With coordinates (`@X,Y`), `--hold` sets how long the finger stays down (100–10000 ms, default 600, 1.0.37) | |
| `z2-ui type <element> "text" [--append]` | Replace the field's content (`--append` adds to the end); up to 4096 characters | |
| `z2-ui enter <element>` | Press the field's Enter action (search, send…); focuses the field first | |
| `z2-ui key back\|home\|recents` | Android global action. `back` acts inside the foreground app, so it counts as an operation (subject to levels and approval) | |
| `z2-ui open <package\|URI>` | Open an app or a specific screen. A package can be opened at `look` or above; a URI can make the target app do something, so it counts as an operation. Returning to z2term itself is always allowed | |
| `z2-ui wait <text=\|desc=\|id=> [MS]` | Wait until the element appears (default 5000, max 30000). `#N` and coordinates are not accepted | |
| `z2-ui wait-change [MS]` | Wait until the screen changes from the last `look` | |
| `z2-ui scroll <element> up\|down\|left\|right` | Scroll by one screen, using the directional scroll of the element or an ancestor within eight levels (falling back to backward/forward). Coordinates are not accepted (use `swipe`) | |
| `z2-ui swipe X1 Y1 X2 Y2 [MS]` | Swipe by coordinates (default 300, 50–3000 ms); both points must be inside the foreground app window | |
| `z2-ui drag X1,Y1 X2,Y2 … [--step MS]` | One gesture that moves through every point without lifting (1.0.34). 2–256 points, 20–1000 ms per segment (default 100), 30 s at most; constant speed along the path. For screens where lifting the finger ends the move (puzzles, drawing) | |

**Screen after the operation (1.0.36)**: add `--shot` to an operation (`tap` / `long` / `type` / `enter` / `key` / `open` / `scroll` / `swipe` / `drag` / `noti press`) and z2-ui waits until the screen stops moving (up to 3 s, or `--settle MS` up to 10 s), captures the foreground app window and returns it under `shot` (JPEG at scale 0.5). Over MCP use `"shot": true`; the image comes with the result. The AI no longer needs a separate capture after every tap, so it thinks once per step instead of twice.

**Capture while the finger is down (1.0.37)**: add `--peek MS` to `long @X,Y` / `swipe` / `drag` and z2-ui captures the foreground app window MS after the finger touches, **before it lifts**, and returns it under `peek` (JPEG at scale 0.5; over MCP `peek_ms`). Things shown only during a touch (such as the remaining move time) are gone once the finger lifts. `held` true means the finger was still down when the capture finished. Keep MS shorter than the gesture and leave about 300 ms after it, because a capture can wait up to a third of a second for Android's interval limit. It can be combined with `--shot`.

Coordinate operations (`tap @X,Y` / `long @X,Y` / `swipe` / `drag`) return as soon as the gesture ends and carry no `changed` (1.0.34): screens operated by coordinates (games and so on) are a single drawn surface where changes cannot be detected, so watching would cost a full second every time. Check the result with `shot`.

`scroll` and `swipe` count as operations too (subject to levels and approval): some apps mark items as read just by scrolling, which would break the promise that Read only changes nothing.

There is no `key shade`: the notification shade (system UI) is never available to the AI, so opening it would let the AI do nothing.

**Rules that prevent acting on the wrong element:**

- Before an operation on `#n`, the element from the last `look` is compared with what is on screen now. If the kind, text, ID or position differs, nothing is sent and `error:"stale"` is returned. The AI can continue after another `look`.
- If the element changes right before the tap, nothing is tapped. Operations are never resent or silently turned into coordinate taps, the same as `z2-action`.
- A successful operation returns `{"ok":true,"changed":true|false}`. `changed` tells whether the screen changed within one second; it does not guarantee that a transition finished, so the AI follows with `wait` or `look` when needed.

### 4.3 Passwords and protected screens

- `type` never targets password fields (`error:"password"`). A person enters credentials.
- On screens that forbid capture (banking apps and so on, Android `FLAG_SECURE`), Android returns the image blacked out. `shot` needs Android 11 or later (`error:"unsupported"` before that).

## 5. Safety

Because the AI can tap any app, every `z2-ui` decision is made at the desk (§3), in one place. No form of `z2-ui` skips it.

⚠ **This keeps an AI from widening its reach or tapping the wrong thing by mistake; it cannot stop an AI that deliberately takes another route.** The AI has a shell, so it can also reach `z2-action` (coordinate macros), `cmd` and other tools besides `z2-ui`. If your AI has a setting for which commands it may run, allowing only `z2-ui` there makes this reliable. On the z2term side, only the following is guaranteed:

- While a `z2-ui` operation is being sent (including while waiting for approval), `z2-action` cannot start, so the approval banner cannot be tapped by coordinates.
- Notification buttons ("Allow", "Resume" and so on) are accepted only from notifications carrying an in-process token. A broadcast the AI sends under the same uid does not count as a press.

### 5.1 Off by default

Until **Allow AI to control apps** is turned on in commands › automation › **AI**, every `z2-ui` command except `status` returns `error:"disabled"`. This switch is separate from the `z2-action` permission.

### 5.2 Per-app levels

Set them under **Per-app permission** in commands › automation › AI. Apps added with "+ Add app" start at `ask`. As commands:

```
z2-ui allow org.example.notes auto
z2-ui allow org.example.shop ask
z2-ui allow '*' look              # apps not in the list
z2-ui allow org.example.notes default   # remove from the list
```

`z2-ui allow` without arguments returns the same JSON as `z2-ui status`.

| Level | Read | Operate |
|---|---|---|
| `off` | ✕ | ✕ |
| `look` | ○ | ✕ (`error:"read_only"`) |
| `ask` | ○ | Each move approved from a notification ("Allow" / "Deny") |
| `auto` | ○ | Runs as is |

- Apps not in the list follow the `*` row, which defaults to `look`.
- **z2term itself, Android settings, installers and the notification shade / quick settings (system UI) are always `off`**, so the AI cannot change these permissions or the accessibility settings, or open the shade and press an approval button itself.
- **Permissions are stored inside the app, out of the terminal's reach.** The AI runs as root in the terminal, so a file under `~/.z2term/` could be rewritten by the AI itself. This is an exception to the "text files are the source of truth" policy ([AUTOMATION-ROADMAP](AUTOMATION-ROADMAP.md)).
- `z2-ui allow <package> <level>` can change permissions too, but **widening** changes (such as `look` → `ask`) take effect only after a person approves them from a notification. Narrowing changes apply immediately.

**Approve for a while:** the `ask` notification has **"Allow for N min"** in addition to "Allow" and "Deny". Pressing it lets operations on that app continue without asking for N minutes.

- N is set under commands › automation › AI › **Approve-for duration**: 1–60 minutes, default 5. The button shows the configured duration (e.g. "Allow for 10 min").
- The grant is per app; another app asks again.
- While a grant is active, the running notification shows the time left (e.g. "No approval needed · 4 min left"). Pause and Stop cancel it.
- Lengthening the duration, like `z2-ui allow`, needs approval from a notification.
- Buttons such as "Send" or "Buy" are not detected by their words: a word list cannot be complete. Put risky apps at `ask` instead.

The `ask` notification says what will happen (e.g. "Tap 'Place order' in org.example.shop", "Enter '…' into the search field"). It waits at most 60 seconds and then returns `error:"denied"`.

### 5.3 Interrupting and instructing the AI

While the AI works, the target app is in front and z2term is behind it (§8), so a person cannot type into the AI's tab. **The running notification is the channel to the AI.**

While operations continue, the notification "AI is controlling an app (org.example.app)" is shown with three actions.

| Notification action | Effect | What the AI receives |
|---|---|---|
| **Pause** | Stops the current move and holds further operations | The next `z2-ui` call returns `{"ok":false,"error":"paused"}`; operations keep returning it until "Resume" is pressed. `look` still works |
| **Send instruction** (reply field) | Stops the current move and hands the text to the AI | The next call returns `{"ok":false,"error":"interrupted","message":"<text>"}` once; later calls are normal again |
| **Stop** | Stops the current move and turns "AI app control" off | `{"ok":false,"error":"stopped"}` until a person turns it on again in settings |

- The AI calls `z2-ui` every few seconds, so the interruption arrives as the reply to one of those calls; the AI needs no special mechanism. `z2-ui --help` tells it: "on `interrupted`, read `message` as a new instruction from the user; on `stopped`, end the task".
- The reply arrives on the next call, so a few seconds may pass while the AI is thinking. A move in progress still stops as soon as the button is pressed.
- Instructions are also written to `~/.z2term/events.jsonl` as `ui_interrupt`, so macros and `z2-when` can react.
- Pause, instruct and stop are also available as `z2-ui pause` / `z2-ui say "text"` / `z2-ui stop`; put them on an edge panel or a tile to use them without opening the notification.
- ⚠ **There is no resume command.** Only the notification's "Resume" and the settings tab's "Resume" resume, so the AI cannot lift a pause by itself.
- Turning the screen off or locking it stops the current move (same as `z2-action`).

### 5.4 Log

`~/.z2term/ui/log.jsonl` gets one line per call (time, app, operation, target, result). Text entered with `type` is not recorded, only its length; `look` contents are not recorded either.

## 6. Connecting an AI

| Connection | Usage |
|---|---|
| AI inside z2term | Run `z2-ui …` as shell commands |
| AI on a PC | `ssh <phone> z2-ui …` |
| MCP-capable AI | `z2-ui mcp` speaks MCP over stdin/stdout and exposes §4 as tools. On a PC, register `ssh <phone> z2-ui mcp` as a (stdio) MCP server |

- MCP only exchanges JSON line by line, so it is written without external libraries; no license was added.
- Tools: `front` / `look` / `shot` / `tap` / `long` / `type` / `enter` / `key` / `open` / `wait` / `wait_change` / `scroll` / `swipe` / `notes` / `write_notes` / `mark` / `save` / `notifications` / `press_notification` / `status` / `guide`. They do the same as the CLI and go through the same checks, approvals and log. Pause, instruct, stop and permission changes are not tools, because they are for people.
- `shot` also returns the image, so AIs that read images can look at it directly. It defaults to scale 0.5 to keep the conversation small.
- **The guide for AIs is `z2-ui --help` (also with no arguments; 1.0.33).** z2-ui is a command for AIs, so its help is not a short note for people but a detailed guide for the AI to read (in English): every command's arguments and limits, the JSON fields, what to do for each error code, and rules of conduct (put `interrupted` first, never repeat a refused operation, never work around permissions, irreversible actions only on the user's explicit request). AIs try `--help` on unknown commands first, so they find it even when their instruction file says nothing.
- People get the same text, so you can always check what the AI is told; to read it in Japanese, ask the AI to summarise `z2-ui --help`. This document stays the source of truth; the guide is written from it for AIs. Adding a command without updating the guide fails a test (`UiBridgeGuideTest`).
- Over MCP the same guide is sent as the connection `instructions` and through the `guide` tool.

## 7. How the AI learns an app

The AI can start without a manual, just as a person using an app for the first time reads button labels, descriptions and kinds. Feeling the way every time is slow and error-prone, though, so **what was learned is kept for next time**.

| Stage | Mechanism |
|---|---|
| First time | The AI repeats `look` → operate → `look`, exploring as it goes |
| Notes | The AI writes what it learned with `z2-ui note <package> text` (`-` reads stdin) to `~/.z2term/ui/notes/<package>.md` (e.g. "search is the magnifier at top right, `id=…/search`", "settings: ⋮ → Settings"). The first line records the app version at that time. Read them with `z2-ui notes <package>`. `look` reports in `notes` whether notes exist (`exists`) and match the current version (`current`) |
| Saved steps | Run `z2-ui mark`, operate, then `z2-ui save NAME` to save the operations that succeeded in between as a `z2-action` definition, replayed from tiles, panels or `z2-when` without the AI |

- Notes and saved steps are plain text that people can read and fix.
- When the app is updated (its version changes), `look` says the notes are for an older version; the AI uses them while double-checking.
- Resource IDs (`id=`) survive app updates better than labels; the MCP instructions ask the AI to prefer IDs in notes.

**Rules for saving steps (`save`):**

- Element numbers mean nothing at replay time, so an element pressed by `#N` is replaced by its resource ID, then its label, then its description, then its coordinates. Truncated labels (ending in "…") do not match the real label and are not used.
- Between operations, `wait` waits the interval that actually passed (up to 2 seconds).
- `drag` is saved as the screen-action step Drag through points (`drag px`) and replays at the same speed (1.0.35).
- **A range that includes an operation z2-action cannot do (typing, Enter, scrolling, waiting for a change, opening a URI, notification buttons) is refused** (`unsupported_step`). Dropping it silently would save a different sequence with a step missing. Run `mark` again and record without it.
- An existing macro with the same name is never overwritten (`exists`), so the AI cannot replace macros you wrote.
- The record lives in memory only; it is cleared when z2term stops or by the notification's Stop. Read-only calls (`look` and so on) are not recorded.

## 8. Operating in the background

**The basic shape: z2term behind, the target app in front.**

```
Screen (front): target app ← you can watch the AI tapping
Behind        : z2term (the AI's tab, the z2-ui desk, accessibility) keeps running
Notification  : "AI is controlling an app" + Pause / Send instruction / Stop (§5.3)
```

- z2term and the AI inside it keep running in the background. `z2-ui open` brings the target app to the front, and the AI operates it from there. Split screen is not used.
- You can watch the AI's taps on the target app. Step in through the notification (§5.3).
- When the work ends, z2term does not come back to the front by itself, so the screen you were looking at is not switched without asking. To return, the AI opens z2term with `z2-ui open`.

**An app cannot be operated while it stays in the background.** Accessibility can read and tap only windows currently shown on screen; Android does not draw apps in the background, so their buttons do not exist. This is a limit of Android itself. For operations that need no screen, use:

| Means | Scope |
|---|---|
| Intents, sharing, URIs (part of `z2-ui open`) | Only what the app accepts |
| Notification buttons ("Reply", "Mark as read"…) | Only what the notification offers. `z2-ui noti list` lists notifications from apps at `look` or above; `z2-ui noti press <key> <label> [reply]` presses a button. Pressing counts as an operation (subject to levels and approval). z2term's own notifications (approvals and so on) are never listed |
| Media control (`z2-media`) | Playback only |
| Virtual display (rooted devices only) | Launch the target app on an invisible virtual display and operate it there. Launching apps on another display needs root; to be investigated |

The virtual display is left for investigation in stage 3 or later.

## 9. Limits

- On screens that expose no elements (games, apps that draw everything themselves), the AI can only read an image from `shot` and operate by coordinates with `tap @X,Y` and `swipe`; accuracy depends on the AI's image reading.
- WebView (browser screens embedded in apps) exposes elements differently depending on the app and the OS version.
- On Google Play, using accessibility for automation requires a declaration and review. F-Droid builds are not affected.
- One `look` stays within the existing 1.5-second search budget. Narrow busy screens with `--in`.
- The `z2-ui` safety measures apply as long as the AI goes through `z2-ui` (start of §5).

## 10. Stages

| Stage | Contents |
|---|---|
| Stage 1 (implemented in 1.0.30) | `front` / `look` / `status` / `tap` / `long` / `type` / `enter` / `key` / `open` / `wait` / `wait-change` / `allow`, all safety measures in §5, the log |
| Stage 2 (implemented in 1.0.31) | `shot` / `scroll` / `swipe` / `look --tree` / `look --in` |
| Stage 3 (implemented in 1.0.32) | `z2-ui mcp`, notes (§7), `mark` / `save`, pressing notification buttons (§8) |

## 11. Decisions (2026-10-18)

- The default for apps not in the list (`*`) is `look` (read only).
- `look` returns the content of input fields (except password fields).
- The command is named `z2-ui`.
- During operation z2term is behind and the target app in front; split screen is not used.
- The `ask` notification has "Allow for N min"; N is set to 1–60 minutes (default 5).
