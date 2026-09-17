# Edge panel components and behaviors

Target: 0.8.617-alpha (versionCode 625)

The two creation entries are Add app and Add item. Add item lets you select a component and its behavior. There are no separate note, terminal, translation or macro-form creation buttons.

| Component | Behaviors |
|---|---|
| Button | Execute a command, state button |
| Text display | Receive output, fixed value, display command output |
| Text input | Provide a value, read/save a file, send input to a command, connect to a shell |
| Choice | Provide the selected value |
| Switch | Read and change a state |
| List | Run a command with the selected value |

Existing definitions are retained without automatic conversion. Notes edit as Text input → Read and save a file; mini terminals edit as Text input → Connect to a shell. Existing on-disk types, including `note` and `terminal`, remain supported.

## Connecting inputs and outputs

Value inputs support defaults, required values and a line count. Choices use `|`-separated values. A fixed value is a Text display with the fixed-value behavior.

Configure a button with a command and input sources in argument order, independently of their visual order. Standard input can reference another item as well. Sources include editable values, choices, fixed values, file inputs, command output and previous results.

Add a Text display with Receive command output and select it as the output destination. Without a destination, the output setting selects no display, an inline display, a toast or a notification. Existing `type=macro` items keep their default inline output.

Result displays provide Stop, Copy and Clear. Concurrent commands targeting the same result are rejected. Inputs are captured before clearing a result, so a previous result can also be the input for its next run. References are limited to the same panel; missing or incompatible sources and destinations fail before launching.

Values, including quotes, newlines and shell substitutions, are passed literally. Arguments are appended to the configured command; put compound commands in a script before binding arguments.

```sh
z2-edge set PANEL:source type=argument label=Input rows=3 order=10
z2-edge set PANEL:run type=run label=Run run=cat stdin=source result=output order=20
z2-edge set PANEL:output type=result label=Output rows=6 order=30
```

Up to 16 arguments share a 64 KiB UTF-8 limit. Standard input and output each have a 64 KiB limit. Ordinary text inputs accept 16,384 characters. The timeout defaults to 30 seconds and accepts `timeout=1..300`. Output appears on completion; use the shell connection behavior for streaming output and sessions.

## Individual size and placement

Item size and position accepts a width and height in dp or percent. Percentage width uses the row or column; percentage height uses the panel. Empty values retain automatic sizing. Explicit heights create a scrollable viewport so controls remain accessible. Items also support start, center or end alignment within their row.

Panel appearance offers vertical, horizontal, grid and free arrangement. Free arrangement uses each item's `at=X%,Y%`: 0% aligns to the top/left and 100% to the bottom/right, accounting for the item size. Items without coordinates remain in sequence. Coordinates are entered numerically; dragging continues to change item order.

```sh
z2-edge panel PANEL flow=free
z2-edge set PANEL:source width=50% height=160 'at=0%,0%'
z2-edge set PANEL:output width=50% height=240 'at=100%,100%'
```

## Saving and closing

Panels with connected inputs and outputs close through the close button. Closing, switching tabs, reloading, screen-off and entering settings cancel connected commands and clear temporary inputs and results. File inputs save their contents. Ordinary execution buttons without bindings keep their existing lifetime.

Long-press outside the panel to open settings. Commands execute in the configured local environment without opening the app screen or an ordinary terminal tab.
