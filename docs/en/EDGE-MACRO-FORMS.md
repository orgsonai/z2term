# Edge macros with arguments

Target: 0.8.616-alpha (versionCode 624)

In panel settings → Items, choose **＋ Macro form** to add an argument box, an action and a result box. Edit the action to select a saved macro or enter a script invocation. A new generic form has no command until you configure it.

Each component is an independent item. Reorder items to place them and set visible rows (1–20) for inputs and results. Vertical, horizontal and grid layouts work; arbitrary XY placement is not provided.

Empty display names omit their heading and heading space. Drag vertically inside result boxes to read all retained output; flick horizontally to switch tabs. Each new result starts at the top. With tabs, **× (Close)** stays at the right of the tab row on every page. Without tabs or a panel title, Close shares an action row.

## Bind arguments and results

- **Argument box**: free text, a choice or a fixed value. Set its initial value, required flag and visible rows. Separate choices with `|`.
- **Macro with arguments**: select input boxes in argument order: `$1`, `$2`, `$3`, etc. Visual order does not affect argument order. Choose a result box, or leave it empty to show results below the action.
- **Result box**: an accepted run clears previous output and shows running, completed or error status. Stop, Copy and Clear controls are provided. Clear also stops an active command. Concurrent writes to the same box are refused.

The argument picker appends a box ID. Edit the comma-separated IDs to remove or reorder bindings. References stay within the same panel. Missing or retyped boxes are reported before launching.

Values are literal arguments: quotes, whitespace, newlines and `$()` are not evaluated as shell code. Configure `run` as a script or command invocation that accepts appended arguments. Put pipelines or multiple statements in a separate script.

```sh
z2-edge set PANEL:text type=argument label=Text rows=3 required=on order=10
z2-edge set PANEL:language type=argument label=Language argument-kind=choice 'choices=ja|en' default=ja order=20
z2-edge set PANEL:go type=macro label=Run 'run=sh "$HOME/example.sh"' args=text,language result=output order=30
z2-edge set PANEL:output type=result label=Result rows=6 order=40
```

Limits: 16 arguments, 16,384 characters per text input, 64 KiB total UTF-8 arguments and 64 KiB displayed output. The default timeout is 30 seconds; `timeout=1..300` changes it. Standard output appears at completion; failures also report standard error. Forms are not interactive terminals.

Panels containing forms stay open on outside taps and Back. Use **Close**. Closing, switching tabs, reloading or turning off the screen stops form commands and discards inputs and results. Reopening restores configured defaults. Stale completions cannot overwrite a new opening. Existing `type=run` actions retain their previous lifetime.

With edge panels enabled, neither the main app screen nor a normal terminal tab needs to be open. Commands use the configured local environment.

The panel receives outside touches. A short tap keeps it open; a long press opens settings. Entering settings stops running form commands and discards input and results.

Existing `type=input` items send standard input. Argument boxes pass values as command arguments.
