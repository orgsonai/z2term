# Android action macros

Working version 0.8.592-alpha / versionCode 600. **Build and unit tests verified; end-to-end device checks remain incomplete.**

`z2-action` stores named text definitions and runs them through one shared runtime from the CLI, shell macros, panels, tiles and `z2-when`. `z2-macro actions` is an alias. Panels need not be enabled. Execution requires enabling z2term Android actions through `z2-key permission`.

## Save and edit

Write this example to a UTF-8 file, replace `org.example.app` and the coordinates for the intended app, then save with `z2-action save demo /root/demo.actions`. `z2-app list` lists launchable packages.

```text
version=1
timeout=30
screen=current
launch org.example.app
wait 800
tap percent 50 40
wait 300
long-press px 100 300 700
swipe percent 50 75 50 25 450
scroll -600 2000 50 50
command z2-toast "Done"
```

`save NAME -` reads standard input. All steps are validated before replacing an existing definition. Invalid input leaves the saved macro intact. `screen=current` expands to the display size and rotation at save time.

Definitions live in the shared home at `/root/.z2term/actions/NAME.actions`. Use `show NAME`, edit the text, and save it again. Direct file edits are also revalidated before execution. A run uses a snapshot of its definition; editing or deleting the saved file affects subsequent runs.

## Create and edit through the GUI (0.8.574)

Open Command list → Automation → Action automation. The list and editor appear directly inside the tab (0.8.586). Panels need not be enabled.

1. Choose New and enter a name using letters, digits, underscores or hyphens.
2. Choose Add step, select Launch app and pick the target application. Add a wait to allow its screen to become ready.
3. Add taps, holds, swipes, timed scrolling or other steps. Tap a row to edit its numbers or command; use the ⋮ menu to reorder. Target directives appear as rows too: moving one changes which app subsequent actions address. Target directives do not count toward execution progress.
4. Switch to Text to edit the complete definition. Comments, blank lines and untouched shell commands are preserved. Repair unsupported lines in text mode. More than 256 source rows open in text mode.
5. Save uses the same validation as the CLI. Changing the name saves a separate copy and leaves the original intact. Existing names and definitions changed or deleted through the CLI while editing are protected from accidental replacement. Reopen a conflicting definition or save under another name.

Each row ends with ▶ to run, Duplicate, ✎ to edit and ✕ to delete (0.8.603). It shows execution state, step progress, a Stop button and up to 32 recent start/branch/end records. Macros run from this screen should start by launching their target app as needed. Rotation retains the name, definition and current step draft.

Closing, going back or switching tabs checks for unsaved edits. Swipe dismissal is blocked while editing; use Close to confirm discarding changes.

### Pick screen coordinates only (0.8.579)

Choose Pick coordinates while editing a tap, hold or swipe. z2term moves the tools task containing the editor to the background and immediately enters coordinate selection. No launch or target step is required, and no other app is launched automatically. Enable z2term Android actions in Accessibility settings first.

- Tap a point or draw from the start to the end of a swipe. Selection touches do not operate the underlying screen. A swipe captures straight-line endpoints only; edit its duration separately.
- Use Navigate to operate the screen normally and reach another screen. Press Pick coordinates in the floating controls to resume selection.
- Draw again to adjust, then choose Apply to return to the numeric fields. Controls can move between the top and bottom; Cancel returns to editing inside the original Action automation tab. Selection does not check the app package or restrict points to a target window.
- Coordinates return in the selected unit (px / percent), together with the screen dimensions and rotation. If existing screen metadata differs, review other coordinates before explicitly changing the screen setting.
- Selection cancels on screen off, lock, geometry/rotation changes, accessibility disconnection or after two minutes. Selection and macro execution are mutually exclusive; edge panels are temporarily hidden.

Only the selected values return to the step draft; picking adds no launch, wait or target directives. It does not record operations, capture images or read UI elements. Saving and executing a macro still requires an operation target. Execution checks that the app is focused and that points are inside its window, above the keyboard. Numeric input through the CLI uses the same definition format.

Settings visibility and scroll position return after backgrounding or unlocking the app. The action editor also retains its draft, current step and scroll position. Settings and editor contents remain hidden while locked. Build and device behavior not yet verified.

## Repetition, conditions and reuse (0.8.573)

Control instructions require `version=2`. Existing `version=1` definitions remain supported.

For example, save this helper as `pause`:

```text
version=1
timeout=5
wait 800
```

Save the following under another name to call the helper on iterations where the condition holds:

```text
version=2
timeout=30
repeat 3
  if charging,level>20
    call pause
  else
    wait 300
  end
end
```

- `repeat N ... end` runs 1–10000 times, awaiting each iteration. `repeat forever` continues until cancellation, failure, timeout or the execution limit.
- `if CONDITION ... else ... end` evaluates current state and runs only the selected branch. `else` is optional. Inside a loop the condition is evaluated each time. Empty blocks and explicitly empty else branches are rejected.
- `call NAME` runs a saved macro and waits for it to return. Each invocation applies the child's own timeout alongside the remaining parent deadline. Failure, cancellation or timeout at any level ends the whole run.

Saving checks syntax and condition expressions. A helper may be created later: existence and cycle checks occur at start. Every referenced definition and its screen geometry is validated, including unused branches, before dispatching any action. Definitions are frozen for that run; subsequent edits or deletions affect future runs.

A block inherits the outer `target` / `launch` target; `else` and `end` restore that outer target. Called macros specify their own targets. Restoring the definition's target does not switch the foreground app. Add an explicit `launch` and `wait` after returning if needed.

### Condition expressions

Commas mean AND; a leading `!` negates a term. There are no parentheses, OR or quoted escapes. A condition accepts at most 16 terms.

| Type | Keys | Syntax |
|---|---|---|
| Boolean | `screen locked idle charging wifi airplane headset bt_audio` | `charging`, `!wifi`, `screen=true`; accepts true/on/yes/1 and false/off/no/0 |
| Number | `level temp volume volume_max` | `level>20`, `temp<40`, `volume=0`; operators = / < / > |
| Text | `ssid ringer plug foreground` | `ringer=silent`, `foreground=org.example.app`; operator = |

State uses the existing `z2-state` snapshot. `foreground` is the focused window's package metadata; no UI-element content is read. Text comparisons ignore case except for package names. An evaluated value that is missing, blank or the numeric unavailable sentinel `-1` fails the run instead of choosing else. AND evaluates left to right and skips remaining terms once false is known.

### GUI, limits and progress

Blocks use a hierarchical form. Add step at each group end offers a step, repeat or condition; headings edit counts/expressions and child rows edit steps. The ⋮ menu moves within a group or to another group end, duplicates or deletes the whole item. Blocks retain else/end delimiters and leading body comments; self-nesting moves are rejected. An if menu adds or removes its else branch. Refill empty bodies before saving. Saved calls have a name picker. Adding control/element instructions selects version=2. Switch between text and forms; malformed structure or more than 256 source rows requires text editing.

Each definition allows 64 source instructions, 64KiB and eight block levels. The instruction count includes repeat, if, call and both branches, excluding target, else and end. Calls allow eight levels including the root, with at most 16 combined call/block frames and 64 referenced definitions. One run executes at most 10000 instructions. Loops are interpreted without materializing their expansion; control instructions and iteration boundaries yield to accept stop requests.

For programs with repeat, if or call, `status` / `history` report JSON `null` for `total`; `step` counts all visited instructions including control flow. `action_macro` identifies the current definition, `path` gives its one-based nested instruction position (with yes/no segments for branches), `iteration` is the innermost loop's iteration, and `call_depth` counts the root as one. Conditions produce `state=branch` records with `detail=true/false`. The GUI shows location, iteration and branch outcomes.

Step and branch history is persisted in batches up to 250ms apart; start and finish records are flushed immediately. Forced process termination can lose the pending batch.

## UI-element targeting and waits (0.8.574)

Version 2 can target elements that an app exposes through Accessibility.

```text
version=2
timeout=30
launch org.example.app
wait-ui 5000 text=Ready
click id=org.example.app:id/next
wait-ui 5000 desc=More options
long-click desc=More options
```

| Instruction | Behavior |
|---|---|
| `click SELECTOR` | Click once |
| `long-click SELECTOR` | Long-click once |
| `wait-ui MS SELECTOR` | Wait for visibility for 1–30000ms |
| `z2-action inspect PACKAGE` | Return focused target-app elements as JSON; a CLI command, not a macro instruction |

Choose one of `text=Continue`, `desc=More options` or `id=org.example.app:id/next`. Text and descriptions match exactly after trimming outer whitespace, with case preserved. Values contain 1–256 characters. Interior spaces, equals signs and quotes are literal; there is no shell expansion, regex or substring matching.

Elements use the preceding target or launch directive and existing block/call target rules. New element-only macros need no screen metadata. The GUI adds geometry for coordinate steps; clear the screen field to remove it. Retained metadata must match, and screen off, lock or rotation during a run still cancels execution.

wait-ui includes waiting for the target app to become focused. Switching away after initially observing it cancels the run. Missing elements or stale transition information cause another lookup after 250ms. The earliest step, parent or child deadline wins. A disabled but visible button satisfies a visibility wait; action availability is checked when clicking.

Ambiguity, a wrong target or exceeded search bounds prevents actions. A label without the requested action uses its nearest actionable ancestor within the target window, up to eight ancestors. Elements are rechecked before dispatch; rejection ends execution. Clicks are sent once with no retry or coordinate fallback. Dispatch success does not guarantee transition completion; follow it with the next screen's wait-ui.

### Choose over the target app

Choose on target app opens the app. Navigate to the desired screen, press Read elements and select text, description or an ID to return to the draft. Selection does not activate the element. Refresh, move the controls or cancel as needed. As with coordinates, two minutes elapsed, geometry changes, screen off, lock or disconnection ends selection; selection and execution are mutually exclusive.

Only explicit element instructions, selection and inspection read the focused target window. Editable/password subtrees and foreign-package embedded content are excluded. Inspection lists are not saved in execution history; chosen selectors are saved as macro text. The Accessibility description explains this scope.

Lookups allow 1024 nodes and a 1.5-second traversal budget; inspection returns at most 256 entries. Requests are serialized and separated from the GUI and normal CLI worker. An Android provider query already waiting for a response may not stop immediately, but a cancelled lookup cannot dispatch actions. Use coordinates where useful elements are not exposed.

API references: [AccessibilityNodeInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo), [FLAG_REPORT_VIEW_IDS](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REPORT_VIEW_IDS).

## Commands

| Command | Behavior |
|---|---|
| `z2-action list` | List saved names |
| `z2-action show NAME` | Print a saved definition |
| `z2-action save NAME FILE` | Validate and save; FILE may be `-` for stdin |
| `z2-action delete NAME` | Delete a definition |
| `z2-action run NAME` | Wait for completion; exit 0 on success, nonzero on failure, cancellation or timeout |
| `z2-action start NAME` | Request execution and return a run ID |
| `z2-action wait RUN_ID` | Wait for that run |
| `z2-action status [RUN_ID]` | Read JSON state, step index and result |
| `z2-action stop [RUN_ID]` | Stop remaining steps; omitting the ID stops the current run |
| `z2-action inspect PACKAGE` | Inspect visible text, descriptions and IDs in the focused target app as JSON |
| `z2-action screen` | Print `screen=WIDTHxHEIGHT@ROTATION`, where rotation is 0/1/2/3 |
| `z2-action history` | Read up to 256 run-start, step-start, branch and finish records as JSON |

Only one macro runs at a time; new requests do not replace it. The process retains results for the latest 32 runs. History persists at `/root/.z2term/actions/.history.jsonl`; after process exit, consult `history`. Runs are never automatically resumed or retried.

Interrupting `run` with Ctrl+C requests cancellation of its own run ID. Interrupting a standalone `wait` does not cancel the run. The execution notification also offers Stop macro. A touch already dispatched to Android may continue for up to three seconds after a stop request; no subsequent steps are sent.

## Definition syntax

Headers precede the body. Use one step per line. Blank lines and lines starting with `#` are ignored.

| Syntax | Meaning |
|---|---|
| `version=1` / `version=2` | Required format version; controls require 2 |
| `timeout=30` | Whole-run timeout, 1–300 seconds; default 30 |
| `screen=current` | Save display geometry; required for coordinates/scroll |
| `screen=1080x2400@0` | Explicit display geometry |
| `target PACKAGE` | Set the target for subsequent coordinates/scroll without launching it |
| `launch PACKAGE` | Request app launch and update the target |
| `wait MS` | Wait 0–30000 milliseconds |
| `key NAME` | back/home/recents/shade/quicksettings/screenshot/split |
| `tap UNIT X Y` | An 80ms tap |
| `long-press UNIT X Y MS` | Hold for 500–3000ms |
| `swipe UNIT X1 Y1 X2 Y2 MS` | Straight swipe lasting 1–3000ms |
| `scroll SPEED MS X Y` | Timed auto-scroll: signed 50–40000dp/s for 1–30000ms, X/Y at 10–90% of the target window |
| `command SHELL_TEXT` | Run through the existing Linux execution path and wait for exit |

Coordinate UNIT is explicitly `px` or `percent`, measured from the full display's top left. Percent coordinates range from 0 to 100. Only the `scroll` position uses target-window percentages. Negative scroll speed moves content forward; positive moves backward. Actual motion depends on the app.

Tap/swipe endpoints must be inside the visible target application window, excluding the keyboard. A mismatched display size/rotation, wrong focused app, screen off/lock, or Accessibility disconnect stops execution. If the target cannot yet be identified after enabling Accessibility, switch to that app before running.

Each step waits for completion: shell exit code or Android's gesture callback. Rejection, cancellation or a missing callback prevents successors. Timed scrolling also waits for its final dispatched stroke to end. `launch` completes when the request is accepted, so insert an explicit `wait` for screen preparation.

Limits: 64 source instructions and 64KiB per macro, 64 saved definitions, and names of 1–64 letters, digits, underscores or hyphens. Panels and handles are temporarily hidden during execution. Unsaved panel edits must be saved or cancelled before a macro can start.

## Existing automation

- Shell macros can call `z2-action run demo`. Continue defining start triggers and conditions through `z2-when` and shell code.
- Example: `z2-when charge:start if=screen run 'z2-action run demo'`. The macro fails if it cannot confirm the required display state.
- Panel items and handle commands can call `z2-action start demo`. Named action macros also appear in the item editor's macro picker.
- A tile can use `z2-tile set 1 'z2-action start demo' --off 'z2-action stop' -l Actions`. This tile toggle remembers its own state and does not automatically track macro completion; consult `status` or the execution notification.

Operation capture, recording and image recognition remain separate investigations. See the [roadmap](AUTOMATION-ROADMAP.md).

Android gesture completion and capability handling follow the [official AccessibilityService specification](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).
