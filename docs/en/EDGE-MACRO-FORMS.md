# Edge macro forms and translation

Target: 0.8.612-alpha (versionCode 620)

In panel settings → Items, choose **＋ Macro form** to add an argument box, an action and a result box. Edit the action to select a saved macro or enter a script invocation. A new generic form has no command until you configure it.

Each component is an independent item. Reorder items to place them and set visible rows (1–20) for inputs and results. Vertical, horizontal and grid layouts work; arbitrary XY placement is not provided.

Empty display names omit their heading and heading space. Drag inside result boxes to read all retained output, including translations. Each new result starts at the top. With tabs, **× (Close)** stays at the right of the tab row on every page. Without tabs or a panel title, Close shares an action row.

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

## Translation form

Choose **＋ Translation form** to add text, target language, source language, an action and a result box. Source `auto` requests detection; the default target is `ja`. Edit the choices or change an input to free text for other languages.

This creates only the app-owned sample `~/.z2term/macros/translate.sh`, preserving any existing file. **No translation CLI, SDK or model is bundled or installed automatically.** Users install a translation CLI with the `trans` interface in the local Linux environment themselves. If missing, the result box explains the required setup. Translation text is sent to an external service under that service’s terms. Connectivity or service changes may prevent translation.

For terminal use, open a local tab after updating the APK, then install the sample:

```sh
z2-macro install translate
sh ~/.z2term/macros/translate.sh "Hello" ja auto
sh ~/.z2term/macros/translate.sh "こんにちは" en ja
```

Arguments are **text, target language, source language**. Use `translate.sh -- "text" ja auto` for text such as `--help`. Inputs beginning with `file://`, `http://` or `https://` are refused so the external command cannot treat the text as a file or web page. Its exit status is preserved.

Translation uses the same generic form and ordinary shell macro mechanism as any other task. Existing `type=input` items continue to send standard input; they are separate from these multiple-argument forms.
