# Android action macro roadmap

Principle (2026-09-10): build broad Android automation that combines triggers, conditions and multiple actions into reusable saved macros. Commands and text remain authoritative. GUI editing, coordinate selection and previews are helpers; no capability should require GUI-only configuration. Shell macros and `z2-when` provide existing integration, while bars, tiles and gestures call the same macros.

## First stage implemented (0.8.571-alpha / 579)

**Build and device behavior not yet verified.** Added named text definitions with save/show/delete; `z2-action run/start/wait/status/stop/history`; coordinate taps, holds and swipes; waits, app launch, Android global actions, shell commands and timed auto-scroll. `z2-macro actions` provides the same entry point.

The shared runtime allows one run, waits for completion, handles cancellation/deadlines, offers a stop notification and records step starts and run outcomes. Saved display geometry and operation targets are checked; panels are temporarily hidden. See the [format and usage](ACTION-MACROS.md).

Handle gestures, action sequences and variable-speed scrolling from 0.8.570 remain available. They can invoke shared macros through the CLI.

## Second stage implemented (0.8.572-alpha / 580)

**Action macro GUI (0.8.572)**: Settings → Automation opens the list, creation, step editing, ordering, duplication, execution, stopping and history. Pick tap/hold points or swipe endpoints over the target app and return them as pixels or percentages. Selection consumes touch input and ends on cancellation, screen changes, screen off, disconnection or after two minutes. GUI text editing shares definitions with the CLI, with unsaved-change confirmation and stale-save detection. The screen respects app lock. Build and device behavior not yet verified.

## Third stage implemented (0.8.573-alpha / 581)

**Action macro repetition, branching and reuse (0.8.573)**: version=2 adds counted/infinite loops, conditions based on device state or the focused package, and calls to saved macros. The complete call graph is validated and frozen before execution; cycles are rejected. Root and child deadlines, cancellation and a 10000-instruction run limit remain shared. The GUI offers loop/branch templates and a saved-macro picker; blocks are edited as text. Progress includes location, iteration and branch outcomes. See [Android action macros](ACTION-MACROS.md). Build and device behavior not yet verified.

## Fourth stage implemented (0.8.574-alpha / 582)

**UI elements and block editing (0.8.574)**: Click/long-click by visible text, description or resource ID, and wait for elements with deadlines. Choose selectors over the target app or inspect through the CLI. Explicit element requests read only the target window, excluding editable/password fields. The hierarchical GUI adds children, moves across groups, duplicates/removes complete blocks and manages else branches. Text, comments and line endings are retained, with shared cancellation and deadlines. See [usage](ACTION-MACROS.md). Build and device behavior not verified; added tests not run.

## Verification after implementation

After a user-run build, check element waits immediately after launch, ambiguous matches, cancellation during lookup, returning selected elements to drafts, and moves across repeats/branches. The agent has not run a phone build.

## Remaining investigations

Operation capture, full-screen recording and image recognition need separate implementation and data-scope investigation. Coordinate selection consumes its touches; element requests inspect only the explicitly targeted window.
