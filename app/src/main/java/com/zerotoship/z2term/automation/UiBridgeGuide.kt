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
    val VERBS = listOf("front", "look", "status", "shot", "tap", "long", "type", "enter", "key", "open", "wait", "wait-change",
        "scroll", "swipe", "notes", "note", "mark", "save", "noti", "allow", "pause", "say", "stop", "mcp")

    val TEXT = """
# z2-ui guide for AI agents

You are reading the guide for z2-ui, the command that lets you read and operate Android apps on the user's phone
through z2term (a terminal app). This guide is written for you, the AI. Follow it exactly.

## 1. Where you are and how to call it

- Inside z2term on the phone: run `z2-ui ...` as a shell command.
- From a PC: run `ssh <phone> z2-ui ...`. The user tells you the host name.
- As an MCP server: the same operations are tools (section 13). You received this guide as the server instructions.
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
3. Before working in an app, run `z2-ui notes <package>` and read your notes if they exist (section 10).

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
| `z2-ui long <target>` | Long-press. |
| `z2-ui type <target> <text> [--append]` | Replace the input's content with text (up to 4096 characters). `--append` adds to the end. Not for password fields (`password`). `not_input` if the element is not an input. |
| `z2-ui enter <target>` | Press the keyboard's Enter/Search/Send action of an input. Focuses it first. |
| `z2-ui key back` / `home` / `recents` | Global keys. `back` counts as an operation on the foreground app. |
| `z2-ui open <package>` | Bring an app to the front (allowed at level look). Opening z2term itself is always allowed. |
| `z2-ui open <uri>` | Open a URI in the app that handles it; counts as an operation. `ambiguous` if several apps could open it: open by package instead. |
| `z2-ui scroll <target> up/down/left/right` | Scroll the list containing the element by one page. `not_scrollable` if none. Counts as an operation. |
| `z2-ui swipe X1 Y1 X2 Y2 [MS]` | Swipe in pixels, 50 to 3000 ms (default 300). Both points inside the foreground app window (`outside`). |
| `z2-ui wait <selector> [MS]` | Wait until an element matching text=/desc=/id= appears, 1 to 30000 ms (default 5000). `not_found` on timeout. |
| `z2-ui wait-change [MS]` | Wait until the screen differs from the latest look. `unchanged` on timeout. |

Operation results: `{"ok":true,"changed":true|false}`.

## 8. Screens without elements: shot

`z2-ui shot [--scale S]` (S from 0.1 to 1, default 1; the MCP tool defaults to 0.5).
- Saves only the foreground app window to `/root/.z2term/ui/shot.png`. The status bar, notification shade and other apps are cut off.
- Returns `path`, image `size`, `box` (the window on screen) and `scale`.
- Convert image coordinates to screen coordinates: screen_x = box[0] + image_x / scale, screen_y = box[1] + image_y / scale.
  Then use `z2-ui tap @screen_x,screen_y` or `swipe`.
- Screens that forbid capture come back black. Requires Android 11 (`unsupported` before).
- Prefer look whenever it returns usable elements; shot is slower and coordinates break when the layout moves.

## 9. Interruptions from the user (highest priority)

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

## 10. Notes per app (your memory across sessions)

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

## 11. Saving a routine as a macro

- `z2-ui mark` starts a recording. Then operate. `z2-ui save <name>` saves the successful operations since mark as a
  z2-action macro (`/root/.z2term/actions/<name>.actions`) that the user can run from tiles and panels without you.
- `#N` targets are rewritten as id, then text, then desc, then coordinates. Waits between steps keep the real
  intervals (up to 2 s).
- Refused with `unsupported_step` if the range includes type, enter, scroll, wait-change, open with a URI or noti press,
  because z2-action cannot replay them. Mark again and record a range without them.
- `exists`: a macro with that name already exists. Choose another name; never ask to overwrite the user's macros.
- `nothing_recorded`: nothing succeeded since mark. Names: 1 to 64 letters, digits, `_` or `-`. At most 64 steps (`too_long`).
- Only save when the user wants a reusable routine.

## 12. Notifications

- `z2-ui noti list`: notifications of apps at level look or above, each with `key`, `package`, `app`, `title`, `text`
  and `actions` (`label`, `reply` = whether the button takes text).
- `z2-ui noti press <key> <label> [reply]`: press a button; give reply text for buttons with a reply field.
  Counts as an operation of the app that posted the notification. `stale` if the notification changed: list again.
- `no_notification_access`: the user has not granted notification access to z2term. Ask the user.
- z2term's own notifications (including approvals) are never listed. You cannot and must not approve your own requests.

## 13. MCP

- Start: `z2-ui mcp` (stdio). From a PC, register `ssh <phone> z2-ui mcp` as a stdio MCP server.
- Tools: front, look (all, tree, in), shot (scale; also returns the image), tap, long, enter (target), type (target, text,
  append), key (name), open (target), wait (selector, ms), wait_change (ms), scroll (target, direction),
  swipe (x1, y1, x2, y2, ms), notes (package), write_notes (package, text), mark, save (name), notifications,
  press_notification (key, label, reply), status, guide.
- Each tool result is the same JSON as the CLI, as text. `isError` is true when `ok` is false.
- Pause, instruction, stop and permission changes are not tools; they belong to the user.

## 14. Conduct

- Do only what the user asked. Before irreversible actions (sending messages, posting, purchases, deleting,
  changing account settings), make sure the user explicitly asked for that exact action. If in doubt, ask first.
- Use the minimum number of operations. Look before every operation; do not chain operations blindly.
- Never enter passwords or one-time codes. If a login is needed, ask the user to do it, then continue.
- Do not read notifications or messages beyond what the task needs, and do not copy private content elsewhere.
- When you finish, tell the user what you did in which app, and anything you could not do.
- The log at `/root/.z2term/ui/log.jsonl` records your calls (typed text is recorded only as a length). The user can read it.

## 15. Error codes

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
| unsupported_step / exists / nothing_recorded / too_long | save could not be done | see section 11 |
| no_notification_access | notification access missing | ask the user |
| bad_args | wrong command or arguments | read this guide and fix the call |
| no_response | z2term did not answer | check that z2term is running; ask the user |
""".trim()
}
