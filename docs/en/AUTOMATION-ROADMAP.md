# Android action macro proposal

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
