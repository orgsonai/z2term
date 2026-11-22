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

Open Command list → Automation → Screen actions. The list and editor appear directly inside the tab (0.8.586). Panels need not be enabled.

1. Choose New and enter a name using letters, digits, underscores or hyphens.
2. To operate the foreground screen, add the desired actions directly. To use a specific app, add Launch app, choose the application and add a wait for its screen to become ready.
3. Add taps, holds, swipes, timed scrolling or other steps. Tap a row to edit its numbers or command; use the ⋮ menu to reorder. Target directives appear as rows too: moving one changes which app subsequent actions address. Target directives do not count toward execution progress.
4. Switch to Text to edit the complete definition. Comments, blank lines and untouched shell commands are preserved. Repair unsupported lines in text mode. More than 256 source rows open in text mode.
   Text mode shows comments in grey and a leading `repeat` `if` `else` `end` `call` in the accent colour. **# Comment** comments out the selected lines (or the line with the cursor) and pressing it again restores them: if every selected line is already a comment the `#` is removed, otherwise `# ` is added after the indentation. Editing a step through the form keeps the comment at the end of its line (1.0.181).
5. Save uses the same validation as the CLI. Changing the name saves a separate copy and leaves the original intact. Existing names and definitions changed or deleted through the CLI while editing are protected from accidental replacement. Reopen a conflicting definition or save under another name.

Each row ends with ▶ to run, Duplicate, ✎ to edit and ✕ to delete (0.8.603). Reorder above the list turns each row into ↑ ↓; every press saves the order, and Done returns to the usual row (1.0.100). The order is kept in `/root/.z2term/actions/.order`, and `z2-action list` follows it; macros not yet arranged follow in name order. It shows execution state, step progress, a Stop button and up to 32 recent start/branch/end records. GUI runs that use the foreground screen move z2term to the background and wait for the other app before starting. Rotation retains the name, definition and current step draft.

Closing, going back or switching tabs checks for unsaved edits. Swipe dismissal is blocked while editing; use Close to confirm discarding changes.

### Record freehand actions (0.8.605)

Choose **Add step → Record freehand actions**, then select one of two modes:

- **Record only** captures touches on a transparent recording surface without sending them to the app underneath.
- **Record while operating apps (root)** reads touch input through `su` and Android `getevent`, without grabbing the device. Root permission is required. Live recording requires one direct Type-B touchscreen; multiple touchscreens and denied device access stop setup. Coordinates use device axis ranges and screen rotation, so devices with custom calibration need device testing.

Tap, double-tap, hold, swipe, pinch or use two fingers without choosing an action type. Lift your fingers and choose **Stop and apply**. Only idle time before the first touch and after the final release is removed. Pauses between actions, holds, paths, speed changes and contact down/up times are saved. Live mode also excludes touches on the recording controls.

Recording stores touch coordinates and time. It does not capture physical keys or the meaning of entered text. Playback still requires touches to stay inside the target window and outside the keyboard; add Android key steps manually for system actions.

The result appears as one **Recorded actions (play once)** group. Its text uses `repeat 1`, `target current`, timed `touch` steps and intervening `wait` steps. Add any desired leading/trailing delays outside the group. The editor updates saved screen geometry and raises the macro timeout if needed for the recording. The macro still requires **Save**.

Record within two minutes, with each touch episode lasting at most 30 seconds and using at most two fingers. Lift both fingers before adding another contact to a two-finger gesture. Recording allows up to 32 episodes, subject to the existing 64-step and 64 KiB definition limits. Cancel, screen-off, lock, geometry changes or accessibility disconnection end capture.

Playback uses the recorded timestamps and releases without adding an endpoint pause. Android input sampling, scheduling and the target app's momentum can affect the observed result. When a second finger joins later, Android requires the first contact's initial path up to that join to use constant speed along the path. Both contacts' down/up times and subsequent speed changes are retained. Stopping suppresses later movement and releases after already queued segments. Usually about 120 ms is queued; the initial phase before a later second-finger join is an exception.

**Build and device behavior are not verified.**

### Manual gestures and placement (0.8.605)

Step types now include **Double tap**, **Pinch in**, **Pinch out** and **Two-finger swipe**. Two-finger swipe means simultaneous fingers. Pinches use a center and initial/final horizontal spacing; two-finger swipes use the first finger's endpoints and the second finger's X/Y offset. Coordinate selection captures both paths and timing and changes the step to recorded paths.

Straight swipes, pinches and two-finger swipes offer **Constant speed**, **Accelerate and flick** and **Decelerate**. Accelerate and flick speeds up to the endpoint and releases immediately. Ordinary straight-swipe coordinate selection also fills in the measured duration (1–3000 ms).

Choose **Place** beside a saved macro to select a Quick Settings slot or an existing edge panel. Replacing an assigned tile requires confirmation; panel placement creates a new run item. Add the tile through Quick Settings editing if it is not visible. An existing panel app-launch item's Run field also offers action macros.

Placement stores `z2-action start NAME`. An exact standalone reference in a tile or a normal panel run item with no output closes the shade/panel and waits for the foreground app to remain stable for about 500 ms. These entry points can also operate z2term itself. If no foreground screen is confirmed within five seconds, playback ends without sending actions. Compound shell commands and items with output/state keep their shell behavior.

**Bar to choose and run (1.0.102)**: `z2-action pick` shows a bar over the current foreground app for choosing a macro and running it. Put it on an edge panel or a tile and one icon covers every macro (the item editor's macro list offers it as "Action macro: choose and run"). Open the list with "name ▾" (in the order set by Reorder in the Screen actions tab), run with ▶, or close with ×. The last choice is remembered. With an empty icon field the item shows its own drawing (built-in "macro", 1.0.104). ▶ starts the run like a placed `z2-action start`, once the foreground screen has settled for about 500 ms. **～** left of ▶ turns human-like input on or off before the run starts (1.0.196; accent colour when on). It starts from the chosen macro's `human=` (on when it is written), and on uses that strength (5 when none is written). The choice applies to that one run only and does not change the macro. The bar then turns into the stop bar (even when "Show a stop button over other apps" is off) and closes when the macro ends; both bars share one position. With "Keep the bar after the macro ends" on in the Screen actions tab (1.0.196, off by default), it comes back as this bar instead, with the macro that ended selected (also for runs that showed the stop bar on their own). Only a real finger can press it. With a macro already running, or no macro saved, it is not shown and an error is returned.

**Build and device behavior are not verified.**

### Drag through points (1.0.35)

New step type **Drag through points** (`drag`): the finger moves through the chosen points in order without lifting, spending the same time on each segment. It is for screens where lifting the finger ends the move (board puzzles, drawing and so on).

- **Choosing points**: press Pick coordinates and tap the points on screen in order. Each lift adds a point, shown with its number and a line. Undo removes the last one; Apply works once there are at least 2. Nothing is sent to the app behind. You can also type `X,Y` pairs separated by spaces in the points field.
- **Speed**: set Time per segment (20–1000 ms). The whole move takes time per segment × (points − 1), shown in the editor.
- **Limits**: 2–256 points, 30 seconds at most.
- Compared with recorded paths: a recorded path replays your finger's movement and speed as traced; Drag through points places points exactly and moves at a constant pace.
- Operations made with AI app control (`z2-ui drag`) and saved with `z2-ui save` become this step.

### Run on the foreground screen without choosing an app

Taps, holds, swipes, scrolling and UI-element steps can be saved without an app target. For example:

```text
version=1
screen=current
swipe percent 50 75 50 25 450
```

GUI Run moves z2term to the background when the macro or a called macro contains foreground-screen actions. It waits for another app to remain focused for about 500 ms before starting. If no stable foreground app is identified within five seconds, no actions are sent. Stop also cancels this startup wait.

The foreground app is resolved at the start of each action and held throughout that gesture, scroll or element wait. Changing apps during an action stops execution. Add Wait steps where screen transitions need time. Geometry, target-window, keyboard, screen-off and lock checks still apply.

Use `target PACKAGE` or `launch PACKAGE` for an explicit app. Add `target current` to return to foreground-screen actions. The Set target app form also has a Foreground screen at execution button.

CLI, panel and tile runs also accept unspecified targets. These entry points do not automatically move z2term to the background; for CLI runs, add an initial wait and switch to the desired screen.

Build and device behavior not yet verified.

### Pick screen coordinates only (0.8.579)

Choose Pick coordinates while editing a tap, hold or swipe. z2term moves the tools task containing the editor to the background and immediately enters coordinate selection. No launch or target step is required, and no other app is launched automatically. Enable z2term Android actions in Accessibility settings first.

- Tap a point or draw from the start to the end of a swipe. Selection touches do not operate the underlying screen. A straight swipe captures its endpoints and measured duration (1–3000 ms). Use freehand recording to retain a whole sequence and its changing speed.
- Use Navigate to operate the screen normally and reach another screen. Press Pick coordinates in the floating controls to resume selection.
- Draw again to adjust, then choose Apply to return to the numeric fields. Controls can move between the top and bottom; Cancel returns to editing inside the original Screen actions tab. Selection does not check the app package or restrict points to a target window.
- Coordinates return in the selected unit (px / percent), together with the screen dimensions and rotation. If existing screen metadata differs, review other coordinates before explicitly changing the screen setting.
- Selection cancels on screen off, lock, geometry/rotation changes, accessibility disconnection or after two minutes. Selection and macro execution are mutually exclusive; edge panels are temporarily hidden.

Only the selected values return to the step draft; picking adds no launch, wait or target directives. This picker captures one gesture; use freehand recording for a whole sequence. It does not capture images or read UI elements. Macros can be saved without a target; each such step resolves the foreground app when it starts. Execution checks that the app is focused and that points are inside its window, above the keyboard. Numeric input through the CLI uses the same definition format.

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
- `repeat until CONDITION ... end` (1.0.188) checks the condition **before each pass** and leaves once it holds (if it already holds, the body never runs). `repeat 10 until CONDITION` means at most 10 passes, ending early when it holds. Conditions are written as for `if`; the check is not counted as an instruction.
- `break` (1.0.188) leaves the innermost `repeat` at once (also from inside an `if`). It cannot appear outside a `repeat`.
- `if CONDITION ... else ... end` evaluates current state and runs only the selected branch. `else` is optional. Inside a loop the condition is evaluated each time. Empty blocks and explicitly empty else branches are rejected.
- `call NAME` runs a saved macro and waits for it to return. Each invocation applies the child's own timeout alongside the remaining parent deadline. Failure, cancellation or timeout at any level ends the whole run (a failure can be caught with `try`, below).
- `set NAME VALUE` / `add NAME NUMBER` (1.0.192) set or increase a variable. See "Count with variables" below.
- `read-colors NAME X,Y ...` (1.0.200) reads the colours of many points from one capture into a variable. See "Read many screen colours at once" below.
- `call NAME VALUE ...` (1.0.191) calls with values. See "Pass values to a call" below.
- `try ... else ... end` (1.0.190): when a step inside fails, the rest is skipped, the `else` steps run, and the run continues after `end`. `else` is optional; without it the failure is dropped and the run continues after `end`. Without a failure the `else` steps do not run. See "A way out when something fails" below.

Saving checks syntax and condition expressions. A helper may be created later: existence and cycle checks occur at start. Every referenced definition and its screen geometry is validated, including unused branches, before dispatching any action. Definitions are frozen for that run; subsequent edits or deletions affect future runs.

A block inherits the outer `target` / `launch` target; `else` and `end` restore that outer target. Called macros do not inherit the caller target; unspecified targets use the foreground app at each step. Restoring the definition's target does not switch the foreground app. Add an explicit `launch` and `wait` after returning if needed.

### Count with variables (1.0.192)

Decisions such as "stop after 10 successes" or "tell me after 3 failures in a row" need no shell.

```text
version=2
repeat until var ok>9          # until 10 successes
  try
    call one-round
    add ok 1
    set miss 0
  else
    add miss 1
    if var miss>2
      command z2-notify "3 failures in a row ($Z2_VAR_ok successes)"
      break
    end
  end
end
```

- `set NAME VALUE`: the value is the rest of the line (spaces allowed, up to 256 characters). `add NAME NUMBER` adds a whole number (negative subtracts); it fails when the current value is not a whole number (`try` can catch that).
- Names use letters, digits and `_` (not starting with a digit), up to 32 characters; at most 32 variables per run.
- Variables start **empty on every run** and are shared with called macros (`call` and `watch` handlers). An unset variable counts as `0`.
- Condition terms `var NAME=VALUE` / `var NAME<NUMBER` / `var NAME>NUMBER` (`!` negates). `=` compares as numbers when both sides are numbers (`4` equals `4.0`), otherwise as exact, case-sensitive text. `<` and `>` fail when the variable is not a number. They work in `if`, `repeat until`, `wait-until` and `watch`.
- Shell steps (`command`, `run-output`, `command` conditions) see them as environment variables `Z2_VAR_NAME`. `run-output` may print `set` / `add` to bring a program's result back into a variable.
- `set` and `add` count as instructions.

### Read many screen colours at once (1.0.200)

When you need the colours of many points, such as a game board, read them from one capture with `read-colors` and decide in the shell.

```text
version=2
screen=current
read-colors row1 100,1516 277,1516 454,1516 631,1516 808,1516 985,1516
run-output python3 ~/solve.py "$Z2_VAR_row1"     # prints the steps to run (drag …) from those colours
```

- `read-colors NAME X,Y ...` reads the colours of the points (px) from **one capture** and stores each point's `RRGGBB` (lower-case hex), in the order written and separated by spaces, in variable `NAME`. The example gives `ff744b 41a4ff …`.
- Up to 32 points at a time (a 6×5 board fits in one). It needs `screen=`, and the points must be inside the saved screen. Points are read like colour conditions (the median around the point). It fails when the target app (`target` / `launch`, or the app in front) is not in front, or when a point is under the stop bar.
- ⚠ A colour condition `color X,Y=…` takes **one capture per term**, close to a second each (Android's minimum capture interval). For many points, gather them into one `read-colors`.
- It is a variable, so shell steps read it as `$Z2_VAR_NAME` (the limits on count and length are those in "Count with variables" above). A `var NAME=…` condition can also compare the whole value.

### Pass values to a call (1.0.191)

One macro can serve steps that differ only in coordinates or text. Save this as `tap-at`:

```text
version=2
screen=current
tap px $1 $2          # tap the coordinates passed in
wait-ui 3000 text=$3
command z2-notify "$3 was tapped"   # the shell gets the values as its own $3
```

The caller writes the values after the name, separated by spaces: `call tap-at 540 1200 OK`.

- Up to 9 values of at most 256 characters each. Values on a `call` line cannot contain spaces (values from `z2-action run` can).
- In the called macro, `$1` to `$9` **outside shell text** become the values and `$#` their count. A `$n` without a value fails.
- Shell text (`command` and `run-output` lines, `command` condition terms) is left alone; the values reach the shell as its own positional parameters `$1` `$2` …, so shell quoting applies as usual.
- Received values can be passed on (`call outer $1`). A `$1` in `run-output` output is replaced with the current values.
- Each call is a separate definition checked and frozen with its values **before the run starts** (a `tap px $1 $2` outside the screen is refused before the first action). Saving checks `$n` with stand-in values, so lines that only make sense with a value (such as `launch $1`) can still be saved.
- `z2-action run NAME VALUE ...` / `z2-action start NAME VALUE ...` pass values to the first macro.
- ⚠ From 1.0.191, `$` followed by a digit or `#` outside shell text is a value. To find text such as `$5`, use a partial match like `text~=` instead.
- `watch` handlers take no values.
- The form for "Call saved macro" has a Values field.

### A way out when something fails (try, 1.0.190)

Keeps one missing element in a long loop from ending the whole macro.

```text
version=2
repeat forever
  try
    wait-ui 3000 text=Claim reward
    click text=Claim reward
  else
    key back          # when it did not appear, go back and carry on
  end
end
```

- Caught: failures of a step itself — no element or more than one, a `wait-ui` / `wait-until` / `wait-while` time-out, a shell (`command`, `run-output`, a `command` condition) exiting non-zero or timing out, coordinates outside the screen or a cancelled touch, unavailable condition state, and failures inside a called macro (`call` or a `watch` handler).
- Not caught: stopping (notification, stop bar, `z2-action stop`, screen off), pausing, the run's and called macros' time limits, the instruction limit (10000), the screen turning off, locking or changing size or orientation, accessibility disconnecting, and operating z2term itself while AI operation is paused. Any next step would fail the same way, so the run ends there.
- The innermost `try` catches. A failure inside its `else` goes to the enclosing `try` (or fails the run). A failure inside a macro called from the `try` is caught too, and the rest of that macro is abandoned.
- A caught failure is recorded in the history as `state=caught` with the reason in `detail`; the GUI history shows "Failure caught; continuing".
- `try` counts as an instruction (`else` and `end` do not). `path` inserts `try` for the body and `else` for the failure side (e.g. `3.try.2`, `3.else.1`).
- The GUI's Add step offers "Add try (steps on failure)", and the ⋮ menu adds or removes the steps on failure.

### Condition expressions

Commas mean AND, `|` means OR and a leading `!` negates a term. AND binds tighter, so `a,b|c` is "a and b" or "c" (1.0.182). There are no parentheses or quoted escapes, so a `ui` value cannot contain `,` or `|` (a `command` term is the exception). A condition accepts at most 16 terms.

| Type | Keys | Syntax |
|---|---|---|
| Boolean | `screen locked idle charging wifi airplane headset bt_audio` | `charging`, `!wifi`, `screen=true`; accepts true/on/yes/1 and false/off/no/0 |
| Number | `level temp volume volume_max` | `level>20`, `temp<40`, `volume=0`; operators = / < / > |
| Text | `ssid ringer plug foreground` | `ringer=silent`, `foreground=org.example.app`; operator = |
| Screen element | `ui` | `ui text=OK`, `ui desc=Menu`, `ui id=…`, partial `ui text~=left`; same selectors as `click` / `wait-ui`; any number of matches counts as present |
| Screen colour | `color` | `color 540,1200=green`, `color 540,1200=#33aa55`; screen px. Names are the 12 of z2-ui `colors` (black gray white red orange brown yellow green cyan blue purple pink); `#RRGGBB` matches when red, green and blue are each within 24. Needs `screen=` |
| Variable | `var` | `var count>3`, `var name=OK`. A macro variable (1.0.192); unset counts as 0 |
| Shell | `command` | `command test -f /root/flag`; true on exit status 0. It reads the rest as shell, so it is always the last term (`,` `|` `#` go to the shell) |

State uses the existing `z2-state` snapshot. `foreground` is the focused window's package metadata. Text comparisons ignore case except for package names. An evaluated value that is missing, blank or the numeric unavailable sentinel `-1` fails the run instead of choosing else. Terms are evaluated left to right and skipped once the answer is known (a false term moves on to the next OR group; a true group ends it).

`ui` and `color` read the screen of the same target app as the surrounding steps (set by `target` / `launch`, otherwise the foreground app). When that app is not in front they count as absent. A colour point under the stop bar cannot be read correctly, so that fails the run (move the bar). Colours need Android 11 or later. A shell term runs where `command` steps run; a timeout or a failure to start fails the run.

⚠ Reading the screen does not use the per-app permissions of AI app control (z2-ui). z2-action could already operate any app without them, so restricting only reading would protect nothing. When an AI with a shell uses z2term, let it run only `z2-ui` if your AI lets you choose.

### Wait for a condition (1.0.182)

```text
version=2
screen=current
wait-until 10000 ui text=Done|ui text=OK   # until either appears, at most 10 s
wait-while 0 color 540,1200=gray           # while it is grey (0 = up to the macro's own limit)
```

- `wait-until MS CONDITION` waits until the condition holds; `wait-while MS CONDITION` waits while it holds.
- It checks every 250 ms (colours about every 1/3 s, the capture interval). MS is 0–300000; 0 has no limit of its own and stops with the macro's `timeout` or a stop. Timing out fails the macro.
- Conditions are written as for `if`. Pausing cancels the wait and resuming starts it again; an `if` condition is checked again too.

For example, to notify when a confirmation appears and wait until it is answered, save this as `ask-user` and `call ask-user` where needed:

```text
version=2
timeout=0
command z2-notify "A confirmation appeared; please choose"
wait-until 0 !ui text=Yes,!ui text=No   # until both are gone
```

### Interrupt with a macro (watch, 1.0.183)

```text
version=2
timeout=0
watch ui text=Yes|ui text=No call ask-user   # whenever the confirmation shows, call ask-user
repeat forever
  tap percent 50 70
  wait 1500
end
```

- `watch CONDITION call NAME`: from the moment the line runs **until the block it is written in ends** (the whole macro at top level, the loop inside a `repeat`), the condition is checked every 300 ms. When it holds, macro NAME runs and the macro then continues where it was.
- It interrupts at step boundaries. `wait` / `wait-until` / `wait-while` / `wait-ui` and the check of an `if` condition are cut short and start again from the beginning afterwards. Taps, swipes, app launches and shell commands finish first.
- While the called macro runs, no watch interrupts again. If the condition still holds after it returns, it is called again (make the called macro clear the condition).
- Conditions are written as for `if`, but `command` is not allowed (they are checked repeatedly). The called macro is checked and frozen before the run like `call` and counts toward call depth. Nothing is checked while paused.

### Human-like input (human=on, 1.0.184)

With `human=on` in the headers, finger operations and waits come closer to a person's finger. What each step means (where to press, from where to where) stays the same.

**Strength (1.0.186; up to 20 since 1.0.187)**: `human=1`..`human=20` chooses how much it varies. Larger numbers give larger offsets, more bending, rounder corners, more hand drift and more variation in speed and timing (proportional to the number). `human=on` is 5; `off` and `0` mean none. The figures below are for strength 2; at 10 they are about five times larger, at 20 ten times. At high strength a drag passes off its points (about 20 px at 10, 40 px at 20). Strength 11 and above is large movement that may miss its targets (time order and staying on screen are still kept).

- Taps, long presses and double taps: the point moves a little each time (within 0.3% of the screen's short side, about 3 px at 1080 px wide; about 8 px at strength 5 and 32 px at 20). Taps last 60–110 ms, long presses vary by ±8% and the double-tap gap by ±15%.
- Swipes: the line bows slightly (up to 5% of its length, 48 px at most), starts and stops slowly with a little variation on the way. The start and the end each move separately, within the same radius as taps; the whole duration varies by ±8%. This takes precedence over accelerate/decelerate.
- Drags (`drag`, 1.0.185): the finger passes close to every given point. Each point moves separately, within the same radius as taps (1.0.203; before, the whole shape moved together, so every point was reached exactly). **The finger speeds up from rest and slows to a stop at the end** (1.0.203). Each segment bows (6% of its length, 12 px at most), eases a little near the points (55–75% of the segment's average speed, never stopping) and varies its speed within the segment. Corners are rounded by averaging 4 px of path on either side. Segment times vary by ±20% with the same total.
- Swipes and drags also get slow hand drift (1 px) and fine tremor (0.5 px).
- Recorded touches: the shape stays, the position moves slightly, and with one finger each segment's time varies by ±20% (same total). Two-finger gestures move together.
- `type` (1.0.193): the text grows one character at a time with a gap between characters (about 110 ms, about 220 ms after a space or punctuation; ±37% at strength 5, growing with strength up to ±90%). Text longer than 256 characters goes in at once. After a pause it is typed again from the start (it replaces the content, so nothing doubles).
- `wait`: ±10% (up to ±300 ms).
- Moved points stay inside the foreground app's window. Scrolling (`scroll`) and element actions (`click` and so on) are unchanged.

**Show the trail of operations (1.0.185)**: with "Show the trail of operations" on in the screen-actions tab, a running macro's presses and traced lines are drawn on screen in the same green as AI app control. The line grows along the path actually sent (human-like movement, two fingers and timing included) and fades one second after the finger lifts. The drawing cannot be touched, so it never gets in the way, and it is removed before a colour condition is read. Off by default.

**Choose what varies (1.0.203)**: the `jitter=` header picks what is varied. All three are the default, the same as leaving the header out.

| Value | What varies |
|---|---|
| `speed` | Speed: press length, double-tap gap, `wait`, typing gaps, and the duration and pace of swipes and drags |
| `path` | Line: the bow of swipes and drags, rounded corners, hand drift and tremor |
| `aim` | Landing point: where each tap, start, end and drag point lands (the radius grows with the strength above) |
| `none` | Nothing varies |

List them with commas, as in `jitter=speed,path`. **Whatever is chosen, a move speeds up from rest and slows to a stop** (a finger never starts or stops at full speed). In the form editor a "What to vary" row appears while human-like input is on, with a switch for each of the three.

**Keep one step exact (1.0.203)**: add `exact` to the end of a coordinate step (`tap`, `long-press`, `double-tap`, `swipe`, `drag`, `pinch-in` / `pinch-out`, `swipe-two`, `touch`, `swipe-path`) and only that step's landing points stay exact (speed and line still vary). Example: `tap px 540 1200 exact`. Use it for places that must not be missed, such as a confirm button. In the form it is "Exact landing points" in the step editor.

**See which macro is running (1.0.206)**: while `call` has entered another macro, the stop bar and the notification put the path to the macro whose step is running in front of the progress, as in "› pad-round › pad-battle" (the first macro's name is on the bar's upper line).

While running, **～** on the stop bar turns it off and on (accent colour when on; turning it back on restores the strength), from the next step. The strength and what varies come from the first macro's header; `human=` and `jitter=` in called macros are not used. In the form editor, tap the "Human-like input" row to enter a strength from 0 to 20.

### Routines (call only, 1.0.203)

For a macro that is only run from other macros with `call`, write `routine=on` in its headers. Routines are left out of the bar that picks and runs a macro and out of the edge panel and tile item pickers, so macros you never pick directly do not crowd those lists. In the screen-actions tab, **routines sit as a tree under the macros that `call` them** (1.0.206). Tap ▸ at the left of a macro, or its name (1.0.207), to open the routines it calls, and open theirs the same way. A routine called from several places appears under each. Only routines no macro calls are folded under "**Unused routines (count)**" at the bottom. While arranging, the list is flat and routines are grouped together. Calls whose name comes from `$1` and the like are not followed. `call` runs them as before, and ▶ in the list or `z2-action start` still runs one alone for testing. In the form editor, the "Routine (call only)" switch sets it.

### Clean up when stopped (cleanup=, 1.0.205)

The `cleanup=` header holds a shell command run when the macro stops before its end. Use it to make sure, for example, that a server the macro started is stopped when you stop the macro.

```
version=2
timeout=0
cleanup=z2-server stop web
command z2-server start web
call main-loop
```

- It runs when the macro is stopped (the stop bar, "Stop macro" in the notification, `z2-action stop`, or turning the screen off), when a step fails, and on a timeout. **It does not run when the macro finishes** (put what should follow a finished run at the end of the macro).
- It is a shell command, not steps, so **it runs with the screen off too**. To run another macro's steps, call `z2-action start NAME` from the command, but steps need the screen on.
- The command gets the macro's variables (`Z2_VAR_NAME`) and call values (`$1`…), plus `Z2_ACTION_NAME` (the macro), `Z2_ACTION_STATE` (`cancelled` / `failed` / `timeout`) and `Z2_ACTION_ERROR` (why it stopped).
- Only the first macro's `cleanup=` counts (called macros' are not used). It is limited to 60 seconds and its result goes into the history as `cleanup`. The next macro can start while it runs.
- ⚠ It does not run when the app itself crashes or is replaced by an update.
- In the form editor, set it with the "Run when stopped" row; leaving it empty removes the header.

### Run steps a program decides (run-output, 1.0.183)

```text
version=2
screen=current
run-output python3 /root/plan.py   # read the board and print the moves
```

- `run-output SHELL`: runs the command where `command` steps run, checks the lines it prints on standard output as steps and executes them in place, then moves on to the next line.
- The output inherits this macro's screen (`screen=`) and the target app of that line. It cannot contain headers (`version=` and so on) and `call` / `run-output` / `watch` are refused (called macros are frozen before the run). Comments and blank lines are fine; empty output does nothing.
- Output is limited to 64 KiB and 64 instructions. A non-zero exit status fails the run without using the output. After a pause, the command runs again on resume.
- A trailing `#` is part of the command and goes to the shell.

### GUI, limits and progress

Blocks use a hierarchical form. Add step at each group end offers a step, repeat or condition; headings edit counts/expressions and child rows edit steps. The ⋮ menu moves within a group or to another group end, duplicates or deletes the whole item. Blocks retain else/end delimiters and leading body comments; self-nesting moves are rejected. An if menu adds or removes its else branch; a try menu adds or removes its steps on failure (1.0.190). Refill empty bodies before saving. Saved calls have a name picker. Adding control/element instructions selects version=2. Switch between text and forms; malformed structure or more than 256 source rows requires text editing.

Each definition allows 64 source instructions, 64KiB and eight block levels. The instruction count includes repeat, if, try, call and both branches, excluding target, else and end. Calls allow eight levels including the root, with at most 16 combined call/block frames and 64 referenced definitions. One run executes at most 10000 instructions (no limit when the macro started first has `timeout=0`). Loops are interpreted without materializing their expansion; control instructions and iteration boundaries yield to accept stop requests.

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
| `tap ui SELECTOR` | Tap the element's centre with a finger (1.0.189) |
| `type TEXT` / `enter` | Replace the content of the focused input field / press the enter key (1.0.189) |
| `watch CONDITION call NAME` | Watch the condition until the block ends and call NAME when it holds |
| `run-output SHELL` | Check the steps the command prints and execute them in place |
| `wait-until MS CONDITION` / `wait-while MS CONDITION` | Wait until / while the condition holds, 0–300000ms (0 = up to the whole limit) |
| `z2-action inspect PACKAGE` | Return focused target-app elements as JSON; a CLI command, not a macro instruction |

Choose one of `text=Continue`, `desc=More options` or `id=org.example.app:id/next`. Text and descriptions match exactly after trimming outer whitespace, with case preserved. Values contain 1–256 characters. Interior spaces, equals signs and quotes are literal; there is no shell expansion or regex.

**Tap an element with a finger (1.0.189)**: `tap ui SELECTOR` finds the element and taps its centre with a coordinate tap. `click` presses through Android's button mechanism, which does nothing where nothing is marked as pressable, as in games and web pages; `tap ui` works there. Input fields can be targets too: `text=` is compared with the hint shown in the field (typed content is never read; password fields are excluded). Human-like input, the trail and stop-bar avoidance work as for a coordinate `tap`. It fails when no element or more than one matches.

**Type text (1.0.189)**:

```text
version=2
tap ui text=Search      # tap the field so it takes focus
wait 300
type weather in Tokyo   # replaces the content (a # goes in as text)
enter                   # the keyboard's enter key
```

- `type TEXT` replaces the content of the input field that has focus (the one the keyboard is for). `type` alone clears it. A trailing `#` goes in as text. Up to 4096 characters at once.
- `enter` presses the keyboard's enter key (search, send and so on). Android 11 or later.
- It fails when no input field has focus, when the field is outside the target app, or when it is a password field.

**Partial match (1.0.188)**: `text~=left` / `desc~=More` match when the text or description contains that part (for labels whose numbers change, such as "3 left"). Not for `id`. `click` / `long-click` fail when two or more elements match, so pick a part no other element shares. AI app control (z2-ui) accepts the same form for its targets.

Elements use the preceding target or launch directive and existing block/call target rules. With no target or with target current, each step resolves the foreground app at its start and holds that target through any wait. New element-only macros need no screen metadata. The GUI adds geometry for coordinate steps; clear the screen field to remove it. Retained metadata must match, and screen off, lock or rotation during a run still cancels execution.

wait-ui includes waiting for the target app to become focused. Switching away after initially observing it cancels the run. The moment in between two screens of the same app (the new screen is focused before it is known which app it belongs to) is not treated as switching away; the wait continues (0.9.32; until then `wait-ui` right after `click` stopped with "Target application changed"). Missing elements or stale transition information cause another lookup after 250ms. The earliest step, parent or child deadline wins. A disabled but visible button satisfies a visibility wait; action availability is checked when clicking.

Ambiguity, a wrong target or exceeded search bounds prevents actions. A label without the requested action uses its nearest actionable ancestor within the target window, up to eight ancestors. Elements are rechecked before dispatch; rejection ends execution. Clicks are sent once with no retry or coordinate fallback. Dispatch success does not guarantee transition completion; follow it with the next screen's wait-ui.

### Choose over the target app

Choose on target app opens an explicitly selected app. With no target or with target current, it moves z2term to the background so you can select from the foreground screen. Navigate to the desired screen, press Read elements and select text, description or an ID to return to the draft. Selection does not activate the element. Refresh, move the controls or cancel as needed. As with coordinates, two minutes elapsed, geometry changes, screen off, lock or disconnection ends selection; selection and execution are mutually exclusive.

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
| `z2-action run NAME [VALUE...]` | Wait for completion; exit 0 on success, nonzero on failure, cancellation or timeout |
| `z2-action start NAME [VALUE...]` | Request execution and return a run ID |
| `z2-action pick` | Show the bar for choosing a macro and running it over the foreground app (1.0.102) |
| `z2-action wait RUN_ID` | Wait for that run |
| `z2-action status [RUN_ID]` | Read JSON state, step index and result |
| `z2-action stop [RUN_ID]` | Stop remaining steps; omitting the ID stops the current run |
| `z2-action pause [RUN_ID]` | Pause (1.0.97). The step in progress is sent again from its start on resume. Returns the status JSON |
| `z2-action resume [RUN_ID]` | Resume (1.0.97) once a foreground screen other than z2term has settled for about 500 ms; if none appears within 5 seconds the run stays paused. It answers once the outcome is known (1.0.101): state `running` with exit status 0, or `paused` (or the final state if the run was stopped meanwhile) with a non-zero exit. Typed from z2term itself, switch to the screen to operate in the meantime |
| `z2-action inspect PACKAGE` | Inspect visible text, descriptions and IDs in the focused target app as JSON |
| `z2-action screen` | Print `screen=WIDTHxHEIGHT@ROTATION`, where rotation is 0/1/2/3 |
| `z2-action history` | Read up to 256 run-start, step-start, branch and finish records as JSON |

Only one macro runs at a time; new requests do not replace it. The process retains results for the latest 32 runs. History persists at `/root/.z2term/actions/.history.jsonl`; after process exit, consult `history`. Runs are never automatically resumed or retried.

Interrupting `run` with Ctrl+C requests cancellation of its own run ID. Interrupting a standalone `wait` does not cancel the run. The execution notification also offers Stop macro, turning the screen off (power button) stops the run, and an optional stop bar can sit over the target app. Turn the bar on with “Show a stop button over other apps” in the Screen actions tab (1.0.95, off by default). It is an overlay window of z2term’s accessibility service, so foreground-app checks and element lookups ignore it. On the bar, ‖ pauses and resumes, ～ turns human-like input on or off and ■ stops (the setting's description says so too, 1.0.196). It shows each enclosing repeat’s pass (outermost first; `repeat forever` shows the pass alone) and the step number inside the current block. It can be dragged, and the next run reuses its position. Before a coordinate gesture whose points fall on the bar (plus 8dp around it), or a scroll that does place a finger and whose line (60% of the target window height, ±48dp) crosses it (1.0.100; before that the whole column was checked, so on a phone the bar hid wherever it was; a scroll asked of the view directly places no finger and never hides it), the bar becomes untouchable (the finger passes through to the app below) and faint; the gesture is sent 100 ms later, and the bar returns to normal 700 ms after the gesture ends (1.0.113; it used to disappear). When its text grows, the bar first moves to where it fits on the screen (1.0.113). The shift is worked out from where you placed the bar each time, so it returns there when the text gets shorter (1.0.116). Progress wider than the screen scrolls to show all of it (1.0.115). Gestures that miss the bar are not delayed. Synthetic input that reaches the bar neither moves nor stops it.

**While AI operation is held (1.0.103)**: while AI app control (z2-ui) is paused or stopped, steps that tap, swipe, scroll, send a key or operate an element on z2term's own screen fail (other apps work as usual), so the AI cannot make a macro press z2term's Resume and lift the hold itself. After Stop, this lasts until AI operation is turned on again.

**Pause and resume (1.0.96)**: ‖ on the bar, Pause in the notification and Pause in the Screen actions tab hold the run; ▶ / Resume continue it. When a system window such as the notification shade comes to the front and takes input focus, the run pauses instead of failing. The finger that pulls the shade is a real touch, so it first cancels a gesture in flight and hides the foreground app; a step failure is therefore settled only after 400 ms, and becomes a pause if the shade has come up by then. Pausing cancels the step in flight, and resuming sends that step again from its start (a touch already delivered cannot be taken back, so it is not continued midway). The root and called-macro time limits stop while paused. Resume closes the shade (Android 12 and later) and, as at the start, waits for a foreground screen other than z2term to stay steady for about 500 ms; if none is confirmed within five seconds the run stays paused. `status` reports state `paused`, and history records `paused` / `resumed`. Screen off, rotation and stop still apply while paused. An ordinary touch already dispatched to Android may continue for up to three seconds after a stop request. Timed paths release after queued segments; the initial phase before a later second-finger join can be longer, as described above. No subsequent steps are sent.

## Definition syntax

Headers precede the body. Use one step per line. Blank lines and lines starting with `#` are ignored.

A comment can also follow a step (1.0.181). It starts at a `#` that **follows whitespace and is itself followed by whitespace or the end of the line**, and runs to the end of the line. A `#` followed directly by other characters, as in `text=#5`, is part of the step. `command` lines are left alone and reach the shell as written, `#` included (the shell reads its own comments). Headers may carry one too, and it is kept when `screen=current` is replaced by the saved geometry.

```
version=2   # needed for repeat
timeout=60
repeat 3    # three rounds
  click text=#5   # the element whose text is "#5"
  wait 800
end
```

| Syntax | Meaning |
|---|---|
| `version=1` / `version=2` | Required format version; controls require 2 |
| `timeout=30` | Whole-run timeout, 0–300 seconds; default 30; `0` means no limit |
| `screen=current` | Save display geometry; required for coordinates/scroll |
| `screen=1080x2400@0` | Explicit display geometry |
| `human=0`..`human=20` / `on` / `off` | Strength of human-like input (default off; `on` = 5); see "Human-like input" below |
| `jitter=speed,path,aim` / `none` | What human-like input varies (default all three; 1.0.203) |
| `routine=on` / `off` | A macro only run with `call` (left out of the run bar and edge panel pickers; 1.0.203) |
| `cleanup=SHELL` | Shell command run when the macro is stopped, fails or times out (1.0.205); see "Clean up when stopped" below |
| `target PACKAGE` | Set the target app for subsequent coordinates, scrolling and UI elements without launching it |
| `target current` | Use the foreground app at the start of each subsequent step; the default when no target is specified |
| `launch PACKAGE` | Launch the app and wait until it is in front (up to 10 seconds, 1.0.111); also updates the target |
| `wait MS` | Wait 0–30000 milliseconds |
| `key NAME` | back/home/recents/shade/quicksettings/screenshot/split |
| `tap UNIT X Y` | An 80ms tap |
| `long-press UNIT X Y MS` | Hold for 500–3000ms |
| `swipe UNIT X1 Y1 X2 Y2 MS [EASING]` | Straight swipe lasting 1–3000 ms, constant speed by default |
| `double-tap UNIT X Y GAP` | Two 80 ms taps; release-to-next-touch gap is 40–300 ms |
| `pinch-in` / `pinch-out UNIT CX CY START END MS [EASING]` | Center and initial/final horizontal finger spacing |
| `swipe-two UNIT X1 Y1 X2 Y2 DX DY MS [EASING]` | Simultaneous swipe; second finger is offset by DX/DY |
| `touch UNIT X,Y,MS ...` | Recorded touch; optional second track separated by `\|`, with its own down/up times; version 2 |
| `swipe-path` / `swipe-two-path UNIT X,Y,MS ...` | Recorded paths; two tracks require matching timestamps; version 2 |
| `drag UNIT MS X,Y X,Y ...` | Move through the points in order without lifting; MS (20–1000) per segment, 2–256 points, 30 s at most (1.0.35) |
| `scroll SPEED MS X Y` | Timed auto-scroll: signed 50–40000dp/s for 1–30000ms, X/Y at 10–90% of the target window |
| `command SHELL_TEXT` | Run through the existing Linux execution path and wait for exit |

Coordinate UNIT is explicitly `px` or `percent`, measured from the full display's top left. Percent coordinates range from 0 to 100. Only the `scroll` position uses target-window percentages. Negative scroll speed moves content forward; positive moves backward. Actual motion depends on the app.

EASING is `linear`, `accelerate` or `decelerate`. Manual pinch and two-finger swipe durations are 1–3000 ms. Recorded paths use 2–2048 points per finger, with increasing elapsed times up to 30000 ms. The first finger starts at zero; `touch` allows the second to start later while overlapping the first.

All points of both touch paths must be inside the visible target application window, excluding the keyboard. A mismatched display size/rotation, wrong focused app, screen off/lock, or Accessibility disconnect stops execution. If the target cannot yet be identified after enabling Accessibility, switch to that app before running.

Each step waits for completion: shell exit code or Android's gesture callback. Rejection, cancellation or a missing callback prevents successors. Timed scrolling also waits for its final dispatched stroke to end. `launch` waits until the launched app is in front before the next step (1.0.111). It used to complete when the request was accepted, so on a slow start the next step took the screen behind as its target and stopped with "Target application changed" once the app arrived. If the app is not in front within 10 seconds, the step fails. To wait for the app's content to settle after it is in front, keep using `wait` or `wait-ui`.

Limits: 64 source instructions and 64KiB per macro, 64 saved definitions, and names of 1–64 letters, digits, underscores or hyphens. Panels and handles are temporarily hidden during execution. Unsaved panel edits must be saved or cancelled before a macro can start.

## Existing automation

- Shell macros can call `z2-action run demo`. Continue defining start triggers and conditions through `z2-when` and shell code.
- Example: `z2-when charge:start if=screen run 'z2-action run demo'`. The macro fails if it cannot confirm the required display state.
- Panel items and handle commands can call `z2-action start demo`. Named action macros also appear in the item editor's macro picker.
- A tile can use `z2-tile set 1 'z2-action start demo' --off 'z2-action stop' -l Actions`. This tile toggle remembers its own state and does not automatically track macro completion; consult `status` or the execution notification.

Touch-sequence capture is implemented in 0.8.605. Video recording and image recognition remain separate investigations. See the [roadmap](AUTOMATION-ROADMAP.md).

Android gesture completion and capability handling follow the [official AccessibilityService specification](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).
