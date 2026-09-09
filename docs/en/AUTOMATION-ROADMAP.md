# Android action macro roadmap

Principle (2026-09-10): build broad Android automation that combines triggers, conditions and multiple actions into reusable saved macros. Commands and text remain authoritative. GUI editing, coordinate selection and previews are helpers; no capability should require GUI-only configuration. Shell macros and `z2-when` provide existing integration, while bars, tiles and gestures call the same macros.

## First stage implemented (0.8.571-alpha / 579)

**Build and device behavior not yet verified.** Added named text definitions with save/show/delete; `z2-action run/start/wait/status/stop/history`; coordinate taps, holds and swipes; waits, app launch, Android global actions, shell commands and timed auto-scroll. `z2-macro actions` provides the same entry point.

The shared runtime allows one run, waits for completion, handles cancellation/deadlines, offers a stop notification and records step starts and run outcomes. Saved display geometry and operation targets are checked; panels are temporarily hidden. See the [format and usage](ACTION-MACROS.md).

Handle gestures, action sequences and variable-speed scrolling from 0.8.570 remain available. They can invoke shared macros through the CLI.

## Next stages

| Priority | Extension | Scope |
|---|---|---|
| 1 | Coordinate selection and step editing | Pick points/paths over other screens; numeric input, insertion, ordering and waits; write back to the same text |
| 2 | Repetition, branching and reuse | Counted/infinite loops, conditions and nested named macros; preserve cancellation, deadlines and history |
| 3 | UI-element targeting | Match text, descriptions or IDs, wait for elements and use bounded retries |
| Investigation | Operation capture | Capture and edit operations; investigate full-screen recording and image recognition separately |

Coordinate capture must consume the selection touch, offer cancellation and record display size/rotation. Percentages alone do not accommodate app layout changes. Future UI-element reading must be distinguished from the current use of window metadata.

Related: [Design specification](DESIGN-SPEC.md), [Handbook](HANDBOOK.md), [Android gesture dispatch](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).
