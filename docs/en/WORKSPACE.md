# Display placement and Android input/output

0.8.643-alpha (versionCode 651). The app supplies session presentation, input routing, document selection and selected-text exchange. Users keep transformations and other task-specific behavior in commands/macros. No new Android library or third-party code is bundled.

## Split sessions

Open two terminal, local GUI, VNC or RDP tabs. In the tab strip, **long-press the GUI (🖥) button next to +**, choose **Top / bottom** or **Left / right**, then the other tab. Tap a pane to focus it; the first tap only changes focus. The green border identifies the input target. Drag the divider to adjust the ratio. **Long-press GUI → One pane** restores one view.

A short press still opens GUI. **Settings → Tips** also explains the gesture. Split panes omit the GUI-specific outer frame and padding and keep their dimensions when focus changes between GUI and terminal. Keyboard visibility is shared.

Selecting another tab replaces the focused pane. Closing either pane returns to a single active session. Placement does not terminate sessions and is retained for the current app process only.

## External display

Connect a screen that Android exposes as an independent presentation display. Select its name under **Long-press GUI** for the tab you want to move. Devices offering mirroring alone cannot provide this feature.

Keep that tab active on the phone to use its keyboard; for GUI tabs, the phone surface becomes a relative touchpad. Switch to another tab to work in a separate session on the phone. **Return to phone**, or disconnecting the display, restores the phone presentation without ending the session.

Only one display controls a session's dimensions. Remote VNC keeps the server's real desktop size; local VNC and RDP use their existing resizing support. External presentation closes while the app is in the background and returns with the app. Locked sessions are hidden on both displays.

## Direct local GUI display (experimental)

Enable **Settings → GUI → Direct local GUI display**, then open a new GUI tab. Running tabs keep their original backend. Existing terminal shells also keep their original backend preference, so open a new terminal after changing this option when launching GUI commands from a terminal.

The existing Linux package installation path supplies Xvfb and x11vnc (`xorg-server-xvfb` on Arch, `xvfb` on Alpine/Debian families). First use follows the existing download confirmation setting. These packages are not bundled into the APK.

The app reads Xvfb's XWD framebuffer and draws it on Android. x11vnc runs with `-nofb`; the existing RFB client handles only keys, pointer input and text clipboard. It never requests pixel updates.

Resolution is set from the starting viewport and GUI magnification, limited to 4096 per side and 8,388,608 pixels. Rotation and pane resizing scale the image without changing the desktop resolution. Visible images are sampled at up to 20 times per second; hidden or stopped views suspend reads. This is not GPU acceleration or zero-copy rendering, and no speed or battery improvement is claimed without device measurements. The conventional backend remains the default; remote VNC/RDP retain their existing paths.

## Android document selection from a command

```sh
file=$(z2-file pick 'text/*') || exit
cat "$file"
z2-file save "$HOME/result.txt" text/plain
z2-file --help
```

The default MIME type is `*/*` for `pick` and `application/octet-stream` for `save`. Picked files are copied under `$HOME/z2term-inbox/`, independent of the original. Remove imported copies when no longer needed. Cloud documents are available when their provider is registered with Android.

Document URIs are never presented as Linux file paths. `save` creates a new document through the system picker; Android handles filename collisions. Maximum 512 MiB, five minutes including selection and transfer, one outstanding picker. Cancellation and failure exit nonzero. The Android shell works without Linux installed. Run from the foreground terminal; Android's background activity restrictions still apply.

## Process selected text in another app

Register a command in **Command list → Snippets** and enable **Offer for selected text**. For example, `tr '[:lower:]' '[:upper:]'` uppercases letters. User macros and existing input forms are supported.

Select text in another app, choose **Process with z2term** in its selection menu, select your command, review the output, then choose **Replace selection**. The source app must support Android `ACTION_PROCESS_TEXT`. Read-only sources offer preview/copy but no replacement button.

The original text is supplied on **stdin**, never evaluated as shell code. **stdout** is the result, preserving trailing newlines and empty output. The limit is 60 seconds and 64 KiB each for input/output; NUL input is rejected. Forms use existing safe argument expansion. Failure/cancellation never replaces the original. Opening the activity executes nothing. Commands control their own network access and storage behavior.

## Validation scope

Desktop release/instrumentation APK builds and 73 related unit tests passed; lintDebug reported zero errors.

Desktop tests cover placement, binary transfer/cancellation/limits, CLI quoting and cleanup, literal selected text and empty output, XWD headers, and RFB input without image requests. A temporary real Xvfb/x11vnc desktop verified pixel colors, pointer movement, and absence of framebuffer requests.

The release APK was installed over the existing app with data retained. Startup and the selected-text processing screen were verified on Android. Six instrumentation tests passed in the separate debug package, covering document import, cancellation cleanup, snippet compatibility and existing sharing behavior.

In 0.8.643-alpha, 14 related instrumentation tests passed in the separate debug package. An in-memory GUI transport verified pane bounds on every drawn frame during focus changes across 12 orientation, split and keyboard combinations. Keyboard visibility retention and a touch long-press opening the layout dialog also passed.

Split operation with real desktop servers, physical external display connection/removal, complete document picker flows, replacement in another app, and Xvfb startup/performance on Android remain unverified.
