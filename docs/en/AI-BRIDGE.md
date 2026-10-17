# `z2-ui`: letting an AI operate Android apps

Status: **stage 1 implemented in 1.0.30** (§10). Stages 2 and 3 are not implemented yet. Rows whose "Stage" column is empty are available in stage 1.

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
- `AndroidUiLook` reads and operates the screen. Like `AndroidUiElements` (z2-action) it checks the foreground window and package and does not enter content embedded from other apps. The differences: it reads input fields (except passwords) and returns position, kind and available operations. Capturing (stage 2) will use the same `takeScreenshot` as `RegionShot`.
- `z2-action` is unchanged. Saved, replayable steps stay with `z2-action`; the AI's one-move-at-a-time work goes through `z2-ui`. The two never run at the same time (while one is busy, the other answers `busy`).

## 4. Commands

Every output is one line of JSON. Success exits with 0, failure with non-zero. Failures still print `{"ok":false,"error":"<code>","message":"…"}`, so the AI can choose its next move by reason.

### 4.1 Looking

| Command | Returns | Stage |
|---|---|---|
| `z2-ui front` | Foreground package, app name, that app's level, screen size, rotation, whether locked | |
| `z2-ui look` | Elements on the foreground screen (example below). `--all` includes meaningless containers too | |
| `z2-ui status` | Permission state (on/off, stopped, paused, default level, per-app levels, seconds left on approve-for, apps that are always refused) | |
| `z2-ui look --tree` | The same with parent/child structure | 2 |
| `z2-ui shot [FILE]` | Save the screen as PNG and return the path; default `~/.z2term/ui/shot.png`; `--scale 0.5` shrinks it | 2 |
| `z2-ui apps` | Launchable apps (use `z2-app list` for now) | 2 |

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
- Limits are 512 elements, 3000 nodes and 1.5 seconds per screen; beyond that `error:"too_many"` / `"too_large"` is returned. Narrowing with `look --in <n>` is stage 2.

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
| `z2-ui long <element>` | Long-press | |
| `z2-ui type <element> "text" [--append]` | Replace the field's content (`--append` adds to the end); up to 4096 characters | |
| `z2-ui enter <element>` | Press the field's Enter action (search, send…); focuses the field first | |
| `z2-ui key back\|home\|recents` | Android global action. `back` acts inside the foreground app, so it counts as an operation (subject to levels and approval) | |
| `z2-ui open <package\|URI>` | Open an app or a specific screen. A package can be opened at `look` or above; a URI can make the target app do something, so it counts as an operation. Returning to z2term itself is always allowed | |
| `z2-ui wait <text=\|desc=\|id=> [MS]` | Wait until the element appears (default 5000, max 30000). `#N` and coordinates are not accepted | |
| `z2-ui wait-change [MS]` | Wait until the screen changes from the last `look` | |
| `z2-ui scroll <element> up\|down\|left\|right` | Scroll by one screen | 2 |
| `z2-ui swipe X1 Y1 X2 Y2 [MS]` | Swipe by coordinates | 2 |

There is no `key shade`: the notification shade (system UI) is never available to the AI, so opening it would let the AI do nothing.

**Rules that prevent acting on the wrong element:**

- Before an operation on `#n`, the element from the last `look` is compared with what is on screen now. If the kind, text, ID or position differs, nothing is sent and `error:"stale"` is returned. The AI can continue after another `look`.
- If the element changes right before the tap, nothing is tapped. Operations are never resent or silently turned into coordinate taps, the same as `z2-action`.
- A successful operation returns `{"ok":true,"changed":true|false}`. `changed` tells whether the screen changed within one second; it does not guarantee that a transition finished, so the AI follows with `wait` or `look` when needed.

### 4.3 Passwords and protected screens

- `type` never targets password fields (`error:"password"`). A person enters credentials.
- On screens that forbid capture (banking apps and so on, Android `FLAG_SECURE`), `shot` (stage 2) will return `error:"secure"`.

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
| MCP-capable AI (stage 3) | `z2-ui mcp` speaks MCP over stdin/stdout and exposes §4 as tools. On a PC, register `ssh <phone> z2-ui mcp` as an MCP server |

- MCP only exchanges JSON line by line, so it will be written without external libraries; no new license is expected.
- Guidance for the AI ("`look` first, operate by number, `look` again on `stale`", and the meaning of each error) is in `z2-ui --help` (stage 1), meant to be read by the AI first.

## 7. How the AI learns an app

The AI can start without a manual, just as a person using an app for the first time reads button labels, descriptions and kinds. Feeling the way every time is slow and error-prone, though, so **what was learned is kept for next time**.

| Stage | Mechanism |
|---|---|
| First time | The AI repeats `look` → operate → `look`, exploring as it goes |
| Notes | The AI writes what it learned to `~/.z2term/ui/notes/<package>.md` (e.g. "search is the magnifier at top right, `id=…/search`", "settings: ⋮ → Settings"). `z2-ui look` reports whether notes exist and the app's version |
| Saved steps | A sequence that has become routine is saved as a `z2-action` definition with `z2-ui save NAME`, then replayed from tiles, panels or `z2-when` without the AI |

- Notes and saved steps are plain text that people can read and fix.
- When the app is updated (its version changes), `look` says the notes are for an older version; the AI uses them while double-checking.
- Resource IDs (`id=`) survive app updates better than labels; `z2-ui --help` asks the AI to prefer IDs in notes.

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
| Notification buttons ("Reply", "Mark as read"…) | Only what the notification offers; a "press" for `z2-noti` will be added (new) |
| Media control (`z2-media`) | Playback only |
| Virtual display (rooted devices only) | Launch the target app on an invisible virtual display and operate it there. Launching apps on another display needs root; to be investigated |

The virtual display is left for investigation in stage 3 or later.

## 9. Limits

- On screens that expose no elements (games, apps that draw everything themselves), the AI can only read an image from `shot` and operate by coordinates; accuracy depends on the AI's image reading.
- WebView (browser screens embedded in apps) exposes elements differently depending on the app and the OS version.
- On Google Play, using accessibility for automation requires a declaration and review. F-Droid builds are not affected.
- One `look` stays within the existing 1.5-second search budget. Narrowing busy screens with `--in` is stage 2.
- The `z2-ui` safety measures apply as long as the AI goes through `z2-ui` (start of §5).

## 10. Stages

| Stage | Contents |
|---|---|
| Stage 1 (implemented in 1.0.30) | `front` / `look` / `status` / `tap` / `long` / `type` / `enter` / `key` / `open` / `wait` / `wait-change` / `allow`, all safety measures in §5, the log |
| Stage 2 | `shot` / `scroll` / `swipe` / `look --tree` / `look --in` |
| Stage 3 | `z2-ui mcp`, notes (§7), `z2-ui save`, pressing notification buttons (§8) |

## 11. Decisions (2026-10-18)

- The default for apps not in the list (`*`) is `look` (read only).
- `look` returns the content of input fields (except password fields).
- The command is named `z2-ui`.
- During operation z2term is behind and the target app in front; split screen is not used.
- The `ask` notification has "Allow for N min"; N is set to 1–60 minutes (default 5).
