package com.zerotoship.z2term.automation

/**
 * AI 専用のガイド。`z2-ui --help` (引数なしも) がこれを出し、MCP では instructions と `guide` 道具で渡す。
 * z2-ui は AI のための命令なので、人向けの短い説明は持たない (人は docs/ja/AI-BRIDGE.md を読む。
 * 利用者の判断、2026-10-18)。人が打っても同じ文面が出るので、AI に何を教えているかを確かめられる。
 *
 * ⚠ **仕様の正本は人向けの docs/ja/AI-BRIDGE.md。** これはそこから AI 向けに書き起こしたもの。
 * 英語なのは AI がいちばん取り違えにくく、どの AI でも読めるため (利用者の判断、2026-10-18)。
 * コマンドを足したら、ここにも書く (書き忘れは UiBridgeGuideTest が落とす)。
 * ⚠ スクリプトの先頭コメントとしてそのまま書き出す (Z2UiScript)。Kotlin の生文字列なので `$` を書かないこと。
 */
internal object UiBridgeGuide {
    /** ガイドに必ず載っている CLI の命令。UiBridgeGuideTest が照らし合わせる。 */
    val VERBS = listOf("front", "look", "status", "shot", "colors", "seq", "tap", "long", "type", "enter", "key", "open", "wait", "wait-change",
        "scroll", "swipe", "drag", "notes", "note", "mark", "save", "noti", "allow", "pause", "say", "stop", "mcp")

    val TEXT = """
# z2-ui guide for AI agents

You are reading the guide for z2-ui, the command that lets you read and operate Android apps on the user's phone
through z2term (a terminal app). This guide is written for you, the AI. Follow it exactly.

## 1. Where you are and how to call it

- Inside z2term on the phone: run `z2-ui ...` as a shell command.
- From a PC: run `ssh <phone> z2-ui ...`. The user tells you the host name.
- As an MCP server: the same operations are tools (section 15). You received this guide as the server instructions.
- This guide is what `z2-ui --help` prints. It is the complete reference; you do not need any other document.
- Every call prints exactly one line of JSON on stdout. Success: `{"ok":true,...}`, exit status 0.
  Failure: `{"ok":false,"error":"<code>","message":"..."}`, non-zero exit status. Always parse the JSON;
  decide your next step from `error`, not from the message text.
- Calls are sequential. Do not run two z2-ui calls at the same time.

## 2. Before you start

1. Run `z2-ui status`. Check `enabled` (must be true), `stopped` (must be false), `accessibility` (must be true).
   - `enabled` false or error `disabled`: the user must turn on "Allow AI to control apps" in z2term
     (commands, automation, AI tab). Ask the user. You cannot turn it on yourself.
   - `accessibility` false or error `no_accessibility`: the user must enable the accessibility service
     "z2term Android actions" (`z2-key permission` opens the screen). Ask the user.
2. Look at `default`, `apps` and `protected` in the status to know which apps you may read or operate.
3. Before working in an app, run `z2-ui notes <package>` and read your notes if they exist (section 12).

## 3. Permission levels (decided by the user, never by you)

Each app has a level. Apps not listed use `default` (normally `look`).

| level | read (look, shot, wait, notes) | operate (tap, type, key back, scroll, swipe, open URI, noti press) |
|---|---|---|
| off | refused: `not_allowed` | refused: `not_allowed` |
| look | allowed | refused: `read_only` |
| ask | allowed | each operation waits until the user approves it in a notification (up to 60 s) |
| auto | allowed | runs immediately |

- `protected` apps (z2term itself, Android settings, installers, the notification shade) are always off: `protected`.
- With `ask`, the call blocks while the user decides. The notification shows exactly what you are doing,
  e.g. "Tap 'Send' in Example". The user may press "Allow for N min" so later operations in that app run without asking.
- `denied`: the user said no (or did not answer in 60 s). Do NOT repeat the same operation. Tell the user what you
  wanted to do and why, and ask how to continue.
- `z2-ui allow <package> <level>` asks the user to change a level. Widening needs the user's approval in a notification.
  Only use it when the user asked you to, or after explaining why you need it.
- NEVER work around these rules: do not use z2-action, z2-key, `cmd`, `input`, adb or any other tool to operate an app
  that z2-ui refused. Do not open Android settings to change permissions. The levels are the user's decision.

## 4. The basic loop

1. `z2-ui open <package>` to bring the app to the front (or work on whatever is already in front; `z2-ui front` tells you).
2. `z2-ui look` to read the screen.
3. Choose ONE operation and run it, addressing the element by number: `z2-ui tap '#3'`.
4. Read the result. `changed` true means the screen changed within one second; it does not mean the next screen
   finished loading. Use `z2-ui wait <selector>` or `z2-ui wait-change` when you expect something specific.
5. `z2-ui look` again before the next operation. Element numbers are only valid for the latest look.

Quote `#N` in shells (`'#3'`), because `#` starts a comment in sh.

## 5. Reading the screen: look

`z2-ui look [--all] [--tree] [--in N]`

Output fields:
- `screen`: id of this look. `package`, `app`: the foreground app. `app_version`: its version.
- `notes`: `{"exists":bool,"current":bool}` — whether you have notes and whether they were written for this version.
- `size` `[w,h]` and `rotation` of the display.
- `elements`: list of objects:
  - `n`: number to use as `#n`.
  - `role`: button, text, input, password, check, switch, list, image, other.
  - `text`, `desc` (content description), `id` (resource id `package:id/name`), `hint` (inputs), `value` (input content),
    `checked` (check/switch). Fields without a value are omitted. `"enabled":false` means the element is disabled.
  - `box`: `[x, y, width, height]` in screen pixels.
  - `can`: operations that will work: tap, long, type, enter, scroll. A text without its own click handler still lists
    `tap` if a parent within eight levels is clickable; the tap goes to that parent.
- Containers without text, description or operation are omitted. `--all` includes them.
- `--tree` returns `tree` (elements with `children`) instead of `elements`. Numbers are the same as in the flat list.
- `--in N` reads only inside element N of the previous look. Use it when look fails with `too_many` (over 512 elements)
  or when you only care about one area. Limits: 3000 nodes and 1.5 s per read (`too_large`).
- Password fields appear with role `password` and position only. Their content is never shown and cannot be typed.

## 6. Addressing elements

- `#N`: element number from the latest look. Preferred.
- `text=Exact text`, `desc=Exact description`, `id=package:id/name`: exact match (trimmed, case-sensitive), searched now.
  For input fields, `text=` matches the hint, not the typed content. If several elements match: `ambiguous` — use `#N`.
  Pass the whole selector as one argument: `z2-ui tap 'text=Sign in'`.
- `@X,Y`: screen pixel coordinates, only for tap and long. Last resort for screens without elements (games, canvases).

Safety checks you will meet:
- `stale`: the element numbered N is no longer the same (text, id, kind or position changed). Nothing was sent. Look again.
- `no_look`: you used `#N` without a look on this screen. Look first.
- `changed`: the foreground app changed while working. Look again.
- Operations are sent once and never retried automatically. If you are unsure whether it worked, look.

## 7. Operating

| command | notes |
|---|---|
| `z2-ui tap <target>` | Tap. |
| `z2-ui long <target> [--hold MS]` | Long-press. With `@X,Y`, `--hold` sets how long the finger stays down, 100 to 10000 ms (default 600). |
| `z2-ui type <target> <text> [--append]` | Replace the input's content with text (up to 4096 characters). `--append` adds to the end. Not for password fields (`password`). `not_input` if the element is not an input. |
| `z2-ui enter <target>` | Press the keyboard's Enter/Search/Send action of an input. Focuses it first. Requires Android 11 (`unsupported` before). |
| `z2-ui key back` / `home` / `recents` | Global keys. `back` counts as an operation on the foreground app. |
| `z2-ui open <package>` | Bring an app to the front (allowed at level look). Opening z2term itself is always allowed. |
| `z2-ui open <uri>` | Open a URI in the app that handles it; counts as an operation. `ambiguous` if several apps could open it: open by package instead. |
| `z2-ui scroll <target> up/down/left/right` | Scroll the list containing the element by one page. `not_scrollable` if none. Counts as an operation. |
| `z2-ui swipe X1 Y1 X2 Y2 [MS]` | Swipe in pixels, 50 to 3000 ms (default 300). Both points inside the foreground app window (`outside`). |
| `z2-ui drag X1,Y1 X2,Y2 ... [--step MS]` | Touch the first point and move through every point without lifting, as ONE gesture. 2 to 256 points, `--step` 20 to 1000 ms per segment (default 100), whole drag at most 30000 ms. Speed is constant along the path. For puzzle games, drawing, and anything that ends when the finger lifts. |
| `z2-ui wait <selector> [MS]` | Wait until an element matching text=/desc=/id= appears, 1 to 30000 ms (default 5000). `not_found` on timeout. |
| `z2-ui wait-change [MS]` | Wait until the screen differs from the latest look. `unchanged` on timeout. |

Add `--shot` to any operation (tap, long, type, enter, key, open, scroll, swipe, drag, noti press) to get the screen
after it in the same reply: z2-ui waits until the screen stops moving (at most 3 s, or `--settle MS` up to 10000),
captures the foreground app window as JPEG at scale 0.5 and puts the shot fields (path, size, box, scale, `stable`,
`waited_ms`) under `shot`. Over MCP pass `"shot": true` (and `settle_ms`); the image is returned with the result.
This replaces the separate shot call after every operation and halves the number of turns you need.
`--box X,Y,W,H` and `--if-changed` (section 8) also apply to this shot (`--box` also to `--peek`); over MCP pass
`box` and `if_changed`. `shot.unchanged` true means the operation changed nothing visible in that area.

Add `--peek MS` to `long @X,Y`, `swipe` or `drag` to see the screen WHILE the finger is still down: z2-ui captures
MS after the finger touches (JPEG, scale 0.5) and puts the shot fields under `peek`, with `held` true when the finger
was still down after the capture. Use it for things shown only during a touch (a move timer, a drag preview, a menu
that closes on release). MS must be shorter than the gesture; leave at least 300 ms after it, because a capture can
wait up to a third of a second for Android's limit. `held` false means the image may show the screen after release:
use a smaller MS or a longer gesture. `--peek` and `--shot` can be combined (MCP: `peek_ms`).

Operation results: `{"ok":true,"changed":true|false}` for element operations. Coordinate operations (`tap @X,Y`,
`long @X,Y`, `swipe`, `drag`) return `{"ok":true}` without `changed` and return as soon as the gesture ends:
screens operated by coordinates are usually a single drawn surface where changes cannot be detected, so waiting
would only add a second. Check the result with `shot`.

## 8. Screens without elements: shot

`z2-ui shot [--scale S] [--jpeg] [--stable [MS]] [--box X,Y,W,H] [--if-changed]` (S from 0.1 to 1, default 1; the MCP
tool defaults to 0.5 and JPEG).
- Saves only the foreground app window to `/root/.z2term/ui/shot.png` (or `shot.jpg` with `--jpeg`, which is faster to
  write and smaller). The status bar, notification shade and other apps are cut off.
- Shots can be taken about three times per second (Android's limit); a shot right after another waits for that gap.
- `--stable [MS]` waits until the screen stops moving (two consecutive frames nearly identical, at most MS, default 3000)
  and saves that frame. `stable` tells whether it settled; `waited_ms` how long it took. Use it instead of taking
  several shots while an animation plays. Screens that keep moving slightly (sparkles, idle animations) count as
  settled; screens that keep moving a lot return after MS with `stable` false.
- Returns `path`, image `size`, `box` (the captured area on screen) and `scale`.
- `--box X,Y,W,H` captures only that part of the window (screen pixels; cut to the window). A smaller image is much
  faster for you to read: once you know where the part you need is (a board, a dialog, a counter), capture only it.
  The coordinate formula below stays the same, because `box` is the captured area.
- `--if-changed`: if the capture is the same as the last image saved with the same file name, area and scale,
  z2-ui writes nothing and returns `{"ok":true,"unchanged":true,"box":[...]}` with no `path` (over MCP: no image).
  Your previous image is still correct; do not read the file again. The comparison is strict (a single changed digit
  counts as a change), so `unchanged` true reliably means nothing visible changed. Use it when polling a screen or
  after an operation that may have done nothing.
- Convert image coordinates to screen coordinates: screen_x = box[0] + image_x / scale, screen_y = box[1] + image_y / scale.
  Then use `z2-ui tap @screen_x,screen_y` or `swipe`.
- Screens that forbid capture come back black. Requires Android 11 (`unsupported` before).
- Prefer look whenever it returns usable elements; shot is slower and coordinates break when the layout moves.
- Each shot overwrites the previous file. Read it right after the call: shot files are deleted when z2-ui has not
  been called for two minutes and when the user stops AI operation. Over MCP the file is deleted as soon as the
  image is in the reply.

## 9. Faster: colors and seq (any app)

Reading an image takes you far longer than reading a few words, and every call costs you one round of thinking.
Two commands cut both:

`z2-ui colors X,Y [X,Y ...] [--radius R] [--stable [MS]] [--hex]`
`z2-ui colors --grid X0,Y0 DX,DY COLSxROWS [--radius R] [--stable [MS]] [--hex]`
- Returns colour names as text, without saving an image: black, gray, white, red, orange, brown, yellow, green,
  cyan, blue, purple, pink. Each point is the median over a square of radius R pixels (default 4, 0 to 20), so a
  highlight or an edge does not decide it.
- Points: `colors` is a list of `{x, y, name, hex}`. Grid: X0,Y0 is the centre of the top-left cell and DX,DY the
  distance between cells (may be negative); `grid` is one string per row, left to right, top row first,
  e.g. `["red blue green blue pink yellow", ...]`. `--hex` adds `#rrggbb` per cell. At most 400 points.
- `--stable` waits until the screen stops moving first (as `shot --stable`). Same permission as shot (level look).
- Use it whenever the state you need is a colour at a known place: board cells, a status lamp, a toggle drawn
  without an element, whether a progress mark has gone. Take ONE shot to learn the layout (cell centres, where
  the lamp is), write it in your notes, then use colors instead of shots. Names are coarse; when two things share
  a name, use `--hex`.

`z2-ui seq STEP -- STEP -- ...`
- Runs several z2-ui steps in one call, in order, without coming back to you between them. Each STEP is a normal
  command without `z2-ui`: `z2-ui seq tap '#3' -- wait 'text=Done' -- tap 'text=OK' --shot`.
- `sleep MS` (1 to 10000) waits inside a seq. Allowed steps: front, look, shot, colors, tap, long, type, enter, key,
  open, scroll, swipe, drag, wait, wait-change, noti, sleep. At most 32 steps.
- Stops at the first failing step. The reply is `{"ok":true,"steps":[...]}` with each step's own result and `verb`;
  on failure `ok` is false, `error` is the step's error code, and `failed_step` tells which step (1-based).
- Images (shot, `--shot`, `--peek`) only in the LAST step (each image overwrites the previous file).
- `#N` refers to the look before the seq unless a look step comes earlier in the same seq.
- Every step is checked and logged exactly as if called alone: approvals with `ask`, pause, instructions and stop
  still apply, and `mark`/`save` record the steps. A seq stops starting new steps after 60 s (`timeout`).
- Use it when you already know the next steps: filling a form (type, type, tap), walking through known dialogs,
  a fixed path through menus, or "do the move, wait, read the board" in a game:
  `z2-ui seq drag ... -- colors --grid ... --stable`.

## 10. Speed, games and time limits

Your own thinking time between calls is the slowest part, and z2-ui cannot shorten it. Each call itself takes about
0.2 s. So the number of calls you make, and how much you must read, decide the speed:
- Send known steps together with `seq`, and read colours with `colors` instead of images (section 9).
- Use ONE call per step: the operation with `--shot` (MCP: `"shot": true`). Do not follow each tap with a separate shot.
- When you need to wait for an animation or a dialog, use one `shot --stable` instead of repeated shots.
- When the next taps are already known (for example several OK buttons at the same place in a tutorial), you may send
  them without looking in between, but take a `--shot` on the last one and check it.
Design your moves so that time limits do not depend on your thinking time:
- Decide the whole move first, then send it in one call. Do not split a continuous move into several swipes:
  lifting the finger usually ends the move.
- In many puzzle games the timer starts when the finger touches the board. Take the `shot`, plan the full path
  while nothing is touched, then send one `drag` with every point of the path. The path then runs in
  step x (points - 1) ms regardless of how long you thought.
- Use `--step` between 60 and 150 ms for board games: too fast may be ignored by the game, too slow may hit its timer.
  For a board, compute each cell centre from the shot once (box, scale and the grid) and reuse it.
- Use `shot --jpeg --scale 0.5` (or smaller) when you only need to recognise colours and positions, and `--box` to
  capture only the area you need. Add `--if-changed` so an unchanged screen costs you no image.
- To read something that appears only while touching (for example the remaining move time), add `--peek MS` to the
  `drag` or `long @X,Y` (section 7). Do not release and shoot afterwards: it is gone by then.
- Coordinate operations do not wait for screen changes (section 7), so a following `shot` shows the result as soon as
  the game has drawn it; if the game animates, take the shot after a short pause or take it again.
- Set expectations with the user: games that need reactions within a second are not playable through this interface.
- The user must set the game to level `auto`; with `ask` every move waits for an approval.

## 11. Interruptions from the user (highest priority)

While you work, the target app is in front and the user cannot type into your terminal. They control you through a
notification. You learn about it as the reply to your next call:

- `interrupted`: `message` is a NEW INSTRUCTION FROM THE USER. Stop what you were doing, read it, and follow it.
  It overrides your current plan. It is delivered once.
- `paused`: the user paused you. Operations are refused until they press Resume; look still works.
  Do not loop rapidly. Wait (for example 10 to 30 seconds), check `z2-ui status` (`paused`), or stop and tell the user
  you are waiting. You cannot resume yourself.
- `stopped`: the user stopped AI operation. End the task now, do not call operations again, and report what you did
  and what is left. Only the user can turn it back on.
- `locked`: the screen is off or locked. Ask the user to unlock; do not try to unlock it yourself.
- `busy`: another z2-ui operation or a z2-action macro is running. Wait a moment and retry once.
- `z2-ui pause`, `z2-ui say <text>` and `z2-ui stop` exist for the user (tiles and panels do the same as the
  notification). Do not call them yourself.

## 12. Notes per app (your memory across sessions)

- `z2-ui notes <package>`: returns `exists`, `text`, `noted_version` and `app_version`.
- `z2-ui note <package> <text>` or `z2-ui note <package> -` (text from stdin): replaces your notes, up to 64 KiB.
  The app version is recorded automatically.
- Read notes before working in an app. If `notes.current` is false (the app was updated), use them but verify each step.
- Write notes after you learn something reusable. Keep them short and factual. Prefer resource ids over labels,
  because labels change with language and updates. Suggested structure:
  - Screens: how to reach each screen, e.g. "Search: tap id=org.example.app:id/search (magnifier, top right)".
  - Elements: ids or stable selectors for important buttons and fields.
  - Pitfalls: dialogs, delays, things that need wait.
- Never write secrets (passwords, codes, personal data the user did not ask you to keep) into notes.

## 13. Saving a routine as a macro

- `z2-ui mark` starts a recording. Then operate. `z2-ui save <name>` saves the successful operations since mark as a
  z2-action macro (`/root/.z2term/actions/<name>.actions`) that the user can run from tiles and panels without you.
- `#N` targets are rewritten as id, then text, then desc, then coordinates. Waits between steps keep the real
  intervals (up to 2 s).
- `drag` is saved as the macro step "drag" and replays at the same speed.
- Refused with `unsupported_step` if the range includes type, enter, scroll, wait-change, open with a URI or noti press,
  because z2-action cannot replay them. Mark again and record a range without them.
- `exists`: a macro with that name already exists. Choose another name; never ask to overwrite the user's macros.
- `nothing_recorded`: nothing succeeded since mark. Names: 1 to 64 letters, digits, `_` or `-`. At most 64 steps (`too_long`).
- Only save when the user wants a reusable routine.

## 14. Notifications

- `z2-ui noti list`: notifications of apps at level look or above, each with `key`, `package`, `app`, `title`, `text`
  and `actions` (`label`, `reply` = whether the button takes text).
- `z2-ui noti press <key> <label> [reply]`: press a button; give reply text for buttons with a reply field.
  Counts as an operation of the app that posted the notification. `stale` if the notification changed: list again.
- `no_notification_access`: the user has not granted notification access to z2term. Ask the user.
- z2term's own notifications (including approvals) are never listed. You cannot and must not approve your own requests.

## 15. MCP

- Start: `z2-ui mcp` (stdio). From a PC, register `ssh <phone> z2-ui mcp` as a stdio MCP server.
- Tools: front, look (all, tree, in), shot (scale, format, stable, box, if_changed; also returns the image), colors (points or grid
  {origin, step, cols, rows}, radius, hex, stable), seq (steps as [["tap", "@540,700"], ["sleep", "500"], ...]; the image of
  a last shot step is returned), tap, long, enter (target), type (target, text,
  append), key (name), open (target), wait (selector, ms), wait_change (ms), scroll (target, direction),
  swipe (x1, y1, x2, y2, ms), drag (points as [[x, y], ...], step), notes (package), write_notes (package, text), mark, save (name), notifications,
  press_notification (key, label, reply), status, guide.
- Each tool result is the same JSON as the CLI, as text. `isError` is true when `ok` is false.
- Operation tools (tap, long, type, enter, key, open, scroll, swipe, drag) accept `shot` (boolean), `settle_ms`, `box`
  ([x, y, w, h]) and `if_changed`:
  with `shot` true the result also contains the screen after the operation, as an image.
- long, swipe and drag accept `peek_ms`: the screen while the finger is down, as an image placed before the `shot`
  image. long also accepts `hold_ms` for `@X,Y`.
- Pause, instruction, stop and permission changes are not tools; they belong to the user.

## 16. Conduct

- Do only what the user asked. Before irreversible actions (sending messages, posting, purchases, deleting,
  changing account settings), make sure the user explicitly asked for that exact action. If in doubt, ask first.
- Use the minimum number of operations. Look before every operation; do not chain operations blindly.
- Never enter passwords or one-time codes. If a login is needed, ask the user to do it, then continue.
- Do not read notifications or messages beyond what the task needs, and do not copy private content elsewhere.
- When you finish, tell the user what you did in which app, and anything you could not do.
- The log at `/root/.z2term/ui/log.jsonl` records your calls (typed text is recorded only as a length). The user can read it.

## 17. Error codes

| error | meaning | what to do |
|---|---|---|
| disabled | AI operation is off | ask the user to turn it on |
| stopped | the user pressed Stop | end the task and report |
| paused | the user paused you | wait; you cannot resume |
| interrupted | new instruction in `message` | follow the message |
| no_accessibility | accessibility service off | ask the user |
| not_allowed / protected / read_only | level forbids it | do not work around; ask the user if needed |
| denied | approval refused or timed out | do not repeat; ask the user |
| locked | screen off or locked | ask the user to unlock |
| busy | another operation or macro is running | wait and retry once |
| no_window | no readable foreground app | open an app or look again |
| stale / no_look / changed | your view of the screen is outdated | look again |
| not_found / ambiguous | selector found nothing / several | look and use `#N` |
| not_tappable / not_input / not_scrollable / disabled_element | the element cannot do that | pick another element |
| password | target is a password field | ask the user to type it |
| outside | coordinates outside the app window | recompute from look or shot |
| too_many / too_large | screen too big to read at once | use `look --in N` |
| rejected | Android refused the operation | look and try another way |
| unsupported / capture_failed | feature not available or capture failed | use look instead of shot |
| unsupported_step / exists / nothing_recorded / too_long | save could not be done | see section 13 |
| no_notification_access | notification access missing | ask the user |
| timeout | a seq ran over 60 s; later steps were not run | look, then continue with fewer steps |
| bad_args | wrong command or arguments | read this guide and fix the call |
| no_response | z2term did not answer | check that z2term is running; ask the user |
""".trim()
}
