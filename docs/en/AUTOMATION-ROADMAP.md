# Android action macro proposal

Core direction (confirmed 2026-09-10): Recording, repetition and coordinate actions are examples, not the limit of the automation scope. Commands and text are the source of truth: triggers, conditions and actions must be creatable, editable, storable and executable through the CLI. GUI controls assist coordinate picking and inspection; no feature should require GUI-only configuration. Integrate with shell macros and `z2-when`, with bars, tiles and gestures invoking shared macros. New automation features remain outside this delivery.


2026-09-10 delivery scope: Operation capture, finite/infinite replay, coordinate-based step editing, and picking coordinates over any app for pasting are recorded requirements. At the user’s request, they are deferred; this round covers fixes and verification of the existing implementation only.



2026-09-10 scope correction: The goal is reusable Android operation macros combining triggers, conditions and multiple actions. Handle gesture bindings are one entry point; the current action lists do not complete that goal. Integrate with existing automation and shell macros, with auto-scroll as one shared action.

Major missing pieces include shared storage/invocation of named operation macros, arbitrary coordinate taps/holds/swipes, GUI editing of waits/repeats/branches, and execution history. A “Pick coordinates” control should capture the next point over any app for pasting into an editor, while retaining direct numeric input. Picking must consume the touch, record screen size/orientation, and offer cancellation. These remain implementation work.

2026-09-09 update: implemented shared action sequences, six handle gestures, launch/wait/single-swipe sequencing, and auto-scroll speed/reversal/position controls (unreleased; action lists built and unit-tested, with partial device verification). Arbitrary coordinate recording and UI-element waits remain unimplemented.

2026-09-08. These extensions and new commands are proposals, not implemented features.
Reuse existing z2-when triggers/guards, shell macros and edge panels as entry points.

| Priority | Extension | Result |
|---|---|---|
| 1 | Coordinate actions | Tap, hold and swipe with explicit coordinates and durations |
| 1 | Execution control | Await completion, stop on cancellation/timeout, emergency stop and per-step history |
| 2 | Gesture bindings | Separate commands for handle double taps and directional swipes |
| 2 | Coordinate picker/editor | Select points/paths on screen, edit waits/repeats and save a macro |
| 3 | UI element actions | Select by text, description or view ID; wait for an element |
| 3 | Branches | Conditions on foreground app, element presence and action results; bounded retries |

Build coordinate actions together with execution control first: launch app, wait, tap a point, swipe.
Later replace fixed sleeps with waiting for a specific UI element. Coordinates explicitly use pixels from
the top-left screen origin or screen percentages. Save recording dimensions and orientation; stop on
rotation or target-app mismatch. Percentages alone do not handle application layout changes.
Hide interfering panels during playback, allow one execution and provide a notification stop action.

Android AccessibilityService supports coordinate gestures through dispatchGesture(), enabled by the
canPerformGestures capability. Await its completion/cancellation callback. Coordinate playback does not
require reading screen contents; finding UI elements would require that additional capability.

Start gesture detection on the handle's own touch area. This is separate from recording every touch
across other apps. Accessibility onGesture() requires touch exploration, which changes normal touch
interaction. A point/path picker is therefore the practical first recording interface.

Provide a visual step editor with ordering and conditions, saving back to text shared with CLI macros.
Full touch recording, image recognition and split app launch remain separate feasibility work.

Sources: [Gesture dispatch](https://developer.android.com/guide/topics/ui/accessibility/service),
[AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService),
[touch exploration configuration](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo).
