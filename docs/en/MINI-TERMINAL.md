# Mini terminal in an edge panel

Target: 0.8.620-alpha (versionCode 628)

In panel settings, choose Add item → Text input → Connect to a shell. To add it to an existing panel through the CLI:

```sh
z2-edge set PANEL_ID:terminal type=terminal label=Terminal
```

Enter a command in the bottom input row and press Run or the keyboard action key. Each run appends stdout and stderr to the existing output. Copy copies the result. Stop ends the shell and ordinary jobs while retaining output. **↻ (Refresh session)** manually resets the shell, output and draft; the next run starts a fresh shell. A stopped shell is never automatically replaced.

## History and snippets

In the input field, **Up** recalls older commands and **Down** returns toward newer commands, then restores the unfinished draft. This reads the same `~/.bash_history` and `~/.zsh_history` as the regular history list. No separate history file is created, and these files are not written by the mini terminal. Commands accepted by this panel item, including across reopening, are also available at the front of its in-memory history. Accepting a run clears the input; Up recalls it. History selection never executes a command. Multi-line entries and entries over 16,384 characters are skipped in this one-line input.

Use **≡ (Snippets)** in the existing action row to show the app's snippet list in place of the result. It uses the same entries, order, groups and storage as the main app. Tap to insert, then press **Run**. **+ New** registers the current input; edits and deletions also appear in the main app. Input-form snippets ask for values before insertion; file values accept typed paths. Use a regular terminal for multi-line snippets.

Closing the list restores the previous result. The tabs and input keep their positions, with no extra action row.

## Session lifetime

- Each item retains its shell, working directory, variables, functions, running commands, output and unfinished input.
- Closing; changing tabs, panels or settings; screen-off; reload; and rotation keep the shell running. Output continues to drain while hidden, and reopening shows the same state.
- While typing, outside taps and Back first dismiss the keyboard and input focus. Otherwise, they close the panel. **× (Close)** also works during typing. Hiding the keyboard alone keeps the panel open. Long-press outside to open settings.
- With tabs, Close stays at the right of the tab row. Without tabs or a panel title, it shares the action row.
- Manual refresh, Stop, deleting or changing the item type, turning panels off, service shutdown and Stop all end the shell. State is not restored after app process death.
- Commands use the selected local Linux environment, or Android sh if Linux is not installed. Existing terminal and SSH tabs are independent. As with other panel commands, Linux runs through the execution engine.

## Stopping jobs

Simply closing the panel preserves ordinary foreground and background jobs. Ending a session through manual refresh, Stop or turning panels off stops ordinary jobs and detached children whose origin can be identified. Explicitly hangup-ignoring processes and detached terminal servers and their children retain their previous behavior. Survival after app process death or termination by Android is not guaranteed.

## Input and display

An empty display name hides the heading, internal ID and heading space. A page containing only a terminal anchors its input at the bottom and gives the remaining height to output. Help text is no longer permanently displayed below it.

Drag vertically inside the result to read all retained output. Horizontal flicks over results switch tabs; vertical drags scroll results. Once a vertical scroll starts, sideways movement does not switch tabs. Long presses remain available for text selection. The result is plain text, bounded to the latest 65,536 characters with a truncation indicator. Output continues to drain after the display limit. ANSI colours and screen controls are omitted. Background output appears as it arrives.

Input accepts one line of up to 16,384 characters. Command stdin is empty, so interactive programs and full-screen TUIs belong in a regular terminal tab. Terminal multiplexers can be started in detached mode from this panel.

No external libraries, SDKs, models or license files were added.
