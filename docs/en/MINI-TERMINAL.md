# Mini terminal in an edge panel

Target: 0.8.617-alpha (versionCode 625)

In panel settings, choose Add item → Text input → Connect to a shell. To add it to an existing panel through the CLI:

```sh
z2-edge set PANEL_ID:terminal type=terminal label=Terminal
```

Enter a command in the bottom input row and press Run or the keyboard action key. An accepted run clears the previous result and displays stdout and stderr as they arrive. Copy copies the result; Clear clears only the display. Stop ends the current session and its ordinary jobs; the next run starts a new session.

## History and snippets

In the input field, **Up** recalls older commands and **Down** returns toward newer commands, then restores the unfinished draft. This reads the same `~/.bash_history` and `~/.zsh_history` as the regular history list. No separate history file is created, and these files are not written by the mini terminal. Commands accepted in this opening are also available at the front of its in-memory history. Accepting a run clears the input; Up recalls it. History selection never executes a command. Multi-line entries and entries over 16,384 characters are skipped in this one-line input.

Use **≡ (Snippets)** in the existing action row to show the app's snippet list in place of the result. It uses the same entries, order, groups and storage as the main app. Tap to insert, then press **Run**. **+ New** registers the current input; edits and deletions also appear in the main app. Input-form snippets ask for values before insertion; file values accept typed paths. Use a regular terminal for multi-line snippets.

Closing the list restores the previous result. The tabs and input keep their positions, with no extra action row.

## Session lifetime

- Commands in the same opening share a shell, including `cd`, variables and functions.
- Reopening starts a fresh shell in its home directory. Input, output, directory and temporary variables are not restored. Files written by commands remain normally.
- Outside taps, focus loss, hiding the keyboard and Back keep the panel open. Use **× (Close)**. With tabs, it stays at the right of the tab row on every page; neither tabs nor Close move when switching. Without tabs or a panel title, it shares the action row instead of taking a separate row. The panel receives outside touches; a long press opens settings and ends the current session.
- Changing tabs, panels or settings; screen off; panel reload or recreation after rotation; and stopping the service also end the current session.
- Commands use the selected local Linux environment, or Android sh if Linux is not installed. Existing terminal and SSH tabs are independent. As with other panel commands, Linux runs through the execution engine.

## Keeping a job after closing

Ordinary foreground and background jobs stop when the session ends. Adding `&` alone does not preserve them. Daemonized children are also stopped when their origin can be identified.

Explicitly keep a job using commands installed in your environment:

```sh
nohup sh -c 'sleep 600' > "$HOME/edge-job.log" 2>&1 &
tmux new-session -d -s edge-job 'sleep 600'
screen -DmS edge-job sh -c 'sleep 600'
```

Processes that ignore hangup, such as `nohup`, and detached `tmux` / `screen` servers and their children are preserved. Use the respective management/stop commands from a regular terminal. This does not guarantee survival after app process death or termination by Android.

## Input and display

An empty display name hides the heading, internal ID and heading space. A page containing only a terminal anchors its input at the bottom and gives the remaining height to output. Help text is no longer permanently displayed below it.

Drag vertically inside the result to read all retained output. Horizontal flicks over results switch tabs; vertical drags scroll results. Once a vertical scroll starts, sideways movement does not switch tabs. Long presses remain available for text selection. The result is plain text, bounded to the latest 65,536 characters with a truncation indicator. Output continues to drain after the display limit. ANSI colours and screen controls are omitted. Background output appears as it arrives.

Input accepts one line of up to 16,384 characters. Command stdin is empty, so interactive programs and full-screen TUIs belong in a regular terminal tab. Use `tmux` / `screen` in this panel to start detached jobs.

No external libraries, SDKs, models or license files were added.
