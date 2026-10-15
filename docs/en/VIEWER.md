# Macro-defined views and forms

Available in 0.8.640-alpha (versionCode 648). The app provides local HTML display, generic forms, and dispatch to a registered macro. Fetching feeds, managing articles, storing reminders, interpreting schedules, and scheduling or deleting notifications belong to macros. A new user macro does not require an app change.

Validation (0.8.641): desktop `assembleRelease`, 133 related unit tests and `lintDebug` (zero errors) passed. Seven Android device tests covering touch routing, presentation and generic controls passed. Touch tests dispatch events to a constructed view hierarchy; manual finger interaction with the actual panel has not been checked.

## Display a page in an edge panel

1. Tap ⚙ in the edge panel. If it is hidden, leave the panel open and long-press the space outside it.
2. Open Layout → Add custom slot (macro / command).
3. Choose Display, then View (z2-view). In 0.8.640, the component is called Text display.
4. Enter a label and a command that produces HTML and calls `z2-view`. With the updated macros installed, use `rss.sh view` or `remind.sh view`. For your own prepared HTML, use `z2-view "$HOME/page.html"`.
5. Choose Manual only or Automatic in View refresh mode. Under Display, you can hide Refresh and Expand individually. Set the automatic interval or timeout in Advanced settings if needed, then Save → Done. In manual mode, press Refresh the first time.

Since 0.8.641, the editor includes Choosing a component, guidance for the selected behavior, and field examples. Buttons, entries, choices, switches and lists are covered along with views. Use example only fills an empty draft field; it does not save or execute it.

In the item editor choose **Display → View (z2-view)** and set a command that produces HTML and calls `z2-view`. Its `z2-view` displays in that item instead of opening an Activity. Manual mode displays the cached snapshot without running a command on open; press Refresh the first time. Only automatic mode runs the producer on open.

```sh
z2-edge panel reading handle=bar side=right width=90% height=65% fit=fixed close=on
z2-edge set reading:page type=view 'run=z2-view "$HOME/page.html"'
z2-edge on
```

Run `z2-edge permission` first if overlay permission is missing. A single view fills the remaining panel height; views can also be combined with other items. Existing item width/height settings apply.

To update an existing view item from elsewhere without opening the panel:

```sh
z2-view --edge reading:page "$HOME/page.html" "Title"
```

**Expand** opens the same display and controls in the standalone viewer. Scroll positions survive reopening in the same process. Snapshots are independent per item.

`view-refresh=auto every=30` runs the producer on open and every 30 seconds while visible. With `view-refresh=manual` (the default since 0.8.641), opening, switching tabs and elapsed intervals do not run the command, even if an interval is saved. Switching tabs, closing, or turning off the screen stops display jobs and timers.

- Opening/periodic updates in automatic mode run the item's `run=` command, suitable for rendering cached data.
- Manual **Refresh** invokes the control definition's `refresh` operation if present, otherwise `run=`, otherwise rereads the snapshot.
- Failures retain the previous page and show an error.

Under Display, Show Refresh button and Show Expand button control those buttons individually. Macro-defined actions remain available. If there are no visible buttons, the toolbar leaves no empty row. External page pushes and action responses still appear in manual mode with Refresh hidden.

| Item setting | Values and default |
|---|---|
| `view-refresh` | `manual` (default) / `auto` |
| `view-refresh-button` | `on` (default) / `off` |
| `view-expand-button` | `on` (default) / `off` |
| `view-find-button` | `on` / `off` (default). The Find in page button (1.0.15) |
| `view-text-size` | `50`–`300` (percent, default `100`). Text size; a pinch writes it (1.0.16). Also set under the item's Display (1.0.18) |
| `view-pinch` | `text` (default) / `page`. Whether a pinch sizes the text or zooms the whole page (1.0.18) |
| `every` | Automatic mode only. Empty or 0 means on open only; 5–86400 seconds also enables periodic refresh |

Swipe the tab strip horizontally to scroll its labels without selecting a different tab. Swipe horizontally on an HTML view to change tabs, and vertically to scroll the page. A vertical start or long press will not turn into a tab change.

## Read from the terminal (standard input and plain text)

Since 1.0.15 a command's output can be read without writing an HTML file first.

```sh
ls -l | z2-view                     # piped, no arguments
ps -ef | z2-view - "Processes"      # - is standard input; the second argument is the title
z2-view notes.txt                   # a plain text file
```

- Input whose first visible character is not `<` is treated as plain text. It is shown wrapped in a monospace font with terminal colour codes (escape sequences) removed. Every character is escaped, so nothing is read as a tag.
- Files named `.html`, `.htm` or `.xhtml` are always pages, whatever they contain.
- `--text` (always text) and `--html` (always a page) override the automatic choice.
- The limit is 4 MB, as for pages, and the text wrapped as a page must also fit in 4 MB. Bytes that are not valid UTF-8 are shown as replacement characters.

## Find in page

Since 1.0.15 a word can be found in the page being shown. Find opens an entry above the page; each keystroke highlights the matches and shows the position and total (such as `3/12`). ▲ and ▼ move between matches and ✕ closes. The keyboard's search key also moves to the next match.

- The full-screen view (opened from the terminal with `z2-view`, or with Expand) always shows Find. Since 1.0.19, `--no-find` hides it when opening from the terminal (`--no-refresh` does the same for Refresh).
- Edge panel view items do not show it by default. Turn on Show Find button under the item's Display, or set `view-find-button=on`.
- A page that arrives while finding is searched again for the same word. Opening a form closes Find.

## Text size (pinch)

Since 1.0.16, pinching the page with two fingers changes its text size (50–300%). The page is not magnified as a picture: the text grows or shrinks and re-wraps to the width, so nothing runs off the side.

- The full-screen view opened from the terminal with `z2-view` (including Expand) keeps one size and uses it for the next page too.
- Each edge panel view item keeps its own size (`view-text-size`).

Pages that set their text size in `px` scale by the same ratio.

### Zoom the whole page, or set the text size (1.0.18)

A pinch can zoom the **page itself** instead of the text. This suits pages that break when re-wrapped, such as pictures and tables. A zoomed page is wider than the screen, so move it up, down and sideways with a finger.

- For the screen opened from the terminal, choose with options to `z2-view`, each time it opens:

  ```sh
  z2-view --pinch page page.html          # a pinch zooms the whole page
  z2-view --text-size 130 page.html       # open this page with the text at 130%
  ```

  `--pinch text` (the default) behaves as in 1.0.16. `--text-size` takes 50–300 and opens only this page at that size (without it, the remembered size is used). Neither applies when the page goes to a panel (`--edge`, or a command inside a panel); the item's settings decide there.
- For an edge panel view item, choose Pinch (Size the text / Zoom the whole page) and Text size (%) under the item's Display (`view-pinch=page`, `view-text-size=130`). Expand to full screen keeps the item's pinch behaviour.
- The text size still applies while pinching zooms the page. The page zoom is not remembered (each opening starts fitted to the width).
- In a panel, a sideways move pans a zoomed page while it can still move that way; at its edge the move changes tabs.

## Default page colours (1.0.19)

When a page sets no text colour, the text follows the terminal theme's foreground (links use the theme's accent). Before, the text stayed black and could not be read on a dark theme. A `color` the page sets still wins. A page that sets only a light background should set its text colour too (on a dark theme it would otherwise get light text on a light background).

## Hiding the full-screen buttons (1.0.19)

The screen opened from the terminal shows Refresh and Find from the start. Hide what you do not need with `z2-view` options:

```sh
z2-view --no-refresh page.html            # no Refresh
z2-view --no-refresh --no-find page.html  # neither
```

Edge panel view items still choose under the item's Display.

## Define forms and actions in a macro

Explicitly pass a separate JSON file alongside HTML:

```sh
z2-view --controls "$HOME/controls.json" "$HOME/page.html" "My view"
```

```json
{
  "handler": "example.sh",
  "refresh": ["view"],
  "actions": [
    {
      "id": "save", "label": "Save", "toolbar": true, "args": ["save"],
      "fields": [
        {"label": "Text", "type": "text"},
        {"label": "Date and time", "type": "datetime"},
        {"label": "Category", "type": "choice", "choices": [
          {"value": "personal", "label": "Personal"},
          {"value": "work", "label": "Work"}
        ]}
      ]
    },
    {"id": "remove", "label": "Delete", "args": ["remove"], "param": true, "confirm": "Delete this item?"}
  ]
}
```

`handler` names an installed `.sh` file in `$HOME/.z2term/macros/`; paths and shell expressions are rejected. Saving the example passes `save`, text, date/time and category as **separate positional arguments**. Spaces, newlines and quotes remain data. The macro must also quote values, such as `"$2"`, and avoid `eval`.

| Property | Meaning |
|---|---|
| `refresh` | Optional fixed arguments for manual refresh |
| `actions[].id` | Unique action ID, 1–80 ASCII letters, digits, `_` or `-` |
| `label` | Display label |
| `args` | Fixed arguments, followed by field values in field order |
| `toolbar` | Show a button above the page when true |
| `confirm` | Optional confirmation before execution |
| `param` | When true, a page link supplies one value (below). Cannot be combined with `toolbar` (1.0.15) |
| `fields` | Optional generic input form; absent means execute without a form |
| `type=text` | Free text, optional `default` |
| `type=multiline` | Free text over several lines; starts four lines tall (1.0.15) |
| `type=number` | A number. Shows the numeric keyboard and refuses to send anything not shaped like `-12` or `3.5` (1.0.15) |
| `type=switch` | An on/off switch passing `on` or `off`; `default` `on` starts it on (1.0.15) |
| `type=date` | Date only, as `yyyyMMdd` (1.0.15) |
| `type=time` | Time only, as `HHmm`. Initial value defaults to one hour later (1.0.15) |
| `type=datetime` | Native date/time selection; local time as `yyyyMMddHHmm`. Initial value defaults to one hour later |
| `type=choice` | Display each choice's `label`, pass its `value`; optional `default` |
| `required` | True by default; false allows an empty field |

HTML may link to an already registered action: `<a href="z2-action:save">Save</a>`. Without a separate definition, such links execute nothing.

An action with `"param": true` is called with one value after a `/` in the link (1.0.15). A list with a Delete link on every row then needs one action instead of one per row (a page holds at most 500 actions).

```html
<a href="z2-action:remove/123">Delete</a>
<a href="z2-action:remove/456">Delete</a>
```

The example runs `example.sh remove 123`: the value follows the fixed arguments as **one argument**, and any form fields follow it. Values are 1–4096 characters. Percent-encode `#`, `%`, `?` and spaces (`%23` and so on); the app decodes them before passing the value. A `param` action linked without a value, or an ordinary action linked with one, runs nothing. Each action has one confirmation, so rows that need their own confirmation text still register an action per row.

After an operation, calling `z2-view` again replaces the same view in either host. Normally macros should leave the supplied `Z2_VIEW_SESSION` and `Z2_VIEW_TARGET` unchanged. Explicit `--edge` redirects to the specified item.

A view accepts one running operation at a time. Errors retain form inputs. Incoming pages wait until the form closes; leaving an unsubmitted form asks before discarding it. **Submitted operations may finish after the panel closes**, but their reply cannot reopen it. Item `timeout=1..300` controls execution time (default 30 seconds). An operation may already have saved data before timing out, so macro authors should account for retries.

[`examples/view-form.sh`](../../examples/view-form.sh) is a complete generic example that saves and clears text and a category. Copy it to `$HOME/.z2term/macros/view-form.sh` and execute it.

## Display and execution boundaries

- JavaScript, network loads, and WebView file/content access remain disabled. Embed images as `data:` URIs.
- Explicit HTTP(S) taps open an external browser.
- Only tapped action IDs in the current separate definition are dispatched. HTML cannot supply shell command strings.
- Macro authors must escape external text in HTML and encode JSON strings correctly.
- HTML must be UTF-8, at most 4 MiB; controls at most 256 KiB. Up to 500 actions, 32 fixed arguments per action, 8 fields, and 32 choices per field. File transfer is separate from the 64 KiB command-output limit.
- No additional dependency, web server, or purpose-specific app database is needed.

## Bundled macro examples

After updating the APK, reopen a local terminal tab. Installed macro copies are never overwritten automatically. Check `z2-macro diff rss` and `z2-macro diff remind`. If you have no local edits, update using `z2-macro install -f rss` and `z2-macro install -f remind`; otherwise merge your changes.

```sh
z2-edge panel reading handle=bar side=right width=90% height=65% fit=fixed tabbar=on close=on
z2-edge set reading:page type=view 'run=rss.sh view' timeout=120
z2-edge tab reading reminders "Reminders"
z2-edge set reminders:page type=view 'run=remind.sh view' view-refresh=auto every=30
z2-edge on
```

RSS displays saved articles immediately and fetches feeds only for actions from inside the list screen — Refresh, and Articles from the subscriptions screen (1.0.14; the pane's Refresh polls from its first press). Article links open the external browser. Its existing Python 3 standard-library prerequisite remains unchanged; only thumbnail images (chosen under Settings) need Pillow. Subscriptions, Archive and Settings are links inside the page, so the pane's toolbar holds only Refresh and Expand.

Use Subscriptions in the article view to add, change or remove feed URLs. Changes apply on the next poll; collected articles are kept. Returning with Articles fetches the new subscriptions. `rss.sh feeds` also opens the subscription list.

In 1.0.15 the per-article Delete, the subscription Delete and the archive's month links each became one value-carrying (`param`) action. Article deletes used to be added newest first up to the action limit (500); now every article in the list has one. The two Settings entries are switches (`type=switch`).

The reminder macro generates HTML and form JSON with `sh` and `awk`. Add specifies content (a multiline field since 1.0.15), date/time and repetition, with optional text scheduling that overrides the date and repetition fields. Weekly repetition uses the selected date's weekday. Scheduled, repeating and notified entries are distinct; notified does not mean completed. Row deletion uses a stable ID and cancels pending alarms, repeating rules and snoozes before removing data. Failed cancellation retains the data. Adding registers the necessary event hooks but does not place tiles. `remind.sh view` provides the same controls in the standalone viewer.
