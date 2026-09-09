# Android action macros

Working version 0.8.571-alpha / versionCode 579. **Build and device behavior not yet verified.**

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
| `z2-action screen` | Print `screen=WIDTHxHEIGHT@ROTATION`, where rotation is 0/1/2/3 |
| `z2-action history` | Read up to 256 run-start, step-start and finish records as JSON |

Only one macro runs at a time; new requests do not replace it. The process retains results for the latest 32 runs. History persists at `/root/.z2term/actions/.history.jsonl`; after process exit, consult `history`. Runs are never automatically resumed or retried.

Interrupting `run` with Ctrl+C requests cancellation of its own run ID. Interrupting a standalone `wait` does not cancel the run. The execution notification also offers Stop macro. A touch already dispatched to Android may continue for up to three seconds after a stop request; no subsequent steps are sent.

## Definition syntax

Headers precede the body. Use one step per line. Blank lines and lines starting with `#` are ignored.

| Syntax | Meaning |
|---|---|
| `version=1` | Required format version |
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

Limits: 64 steps and 64KiB per macro, 64 saved definitions, and names of 1–64 letters, digits, underscores or hyphens. Panels and handles are temporarily hidden during execution. Unsaved panel edits must be saved or cancelled before a macro can start.

## Existing automation

- Shell macros can call `z2-action run demo`. Continue defining triggers and conditions through `z2-when` and shell code.
- Example: `z2-when charge:start if=screen run 'z2-action run demo'`. The macro fails if it cannot confirm the required display state.
- Panel items and handle commands can call `z2-action start demo`. Named action macros also appear in the item editor's macro picker.
- A tile can use `z2-tile set 1 'z2-action start demo' --off 'z2-action stop' -l Actions`. This tile toggle remembers its own state and does not automatically track macro completion; consult `status` or the execution notification.

GUI step editing and coordinate selection, operation capture, repetition, branching, nested named macros and UI-element waits remain future work. Existing shell macros can already call action macros conditionally. See the [roadmap](AUTOMATION-ROADMAP.md).

Android gesture completion and capability handling follow the [official AccessibilityService specification](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).
