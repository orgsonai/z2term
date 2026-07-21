# z2term Macro Guide (z2term-macro)

**How to build phone-automation "macros" using nothing but the z2term terminal.**
It is a manual you can read and write by hand, and at the same time a **machine-readable
reference you can feed whole to an AI** — then just say "I want to …" and it generates the macro.

> Target version: 0.8.167-alpha and later / 日本語版: `docs/ja/MACRO-GUIDE.md`
> Everything here is **non-root, fully local, no external transmission**. No hard-permission features are included.

---

## 1. The model (three stages)

z2term macros follow the same "**trigger → decide → action**" shape as MacroDroid and friends.

| Stage | Direction | In z2term |
|---|---|---|
| **Trigger** | Android → shell | System events appended to `~/.z2term/events.jsonl` (charge/screen/lock/Wi‑Fi/headset/airplane/ringer…). Notifications go to `~/.z2term/notifications.jsonl`, SMS to `~/.z2term/sms.jsonl` (for OTPs, SMS detection bypasses redaction — see 5-7). **Time** triggers come from `z2-alarm`, which writes `alarm` into the same events.jsonl |
| **Decide (logic)** | shell | Read the log lines and branch (`if`, time, counts, state files…). Plain sh/awk/jq, anything goes |
| **Action** | shell → Android | `z2-*` commands drive the Android side (notify/speak/volume/torch/fire an Intent…) |

**A macro is simply "a shell script that watches the event log and fires actions when conditions match."**

---

## 2. One-time setup

1. **Enable triggers**: app ⚙ Settings →
   - Turn on "**System event detection**" (events.jsonl starts filling).
   - If you use notifications too, turn on "**Notification detection**" (and grant the OS "notification access").
2. **To keep it resident**: ⚙ Settings → "**Resident servers**" → register your macro script's start command; it then runs without opening the app and after reboot (also turn on "auto-start on boot").
3. Handy tool: install `jq` (JSON parsing). e.g. Alpine `apk add jq` / Debian-family `apt install jq`.
4. **If you would rather not start from a blank file**: `z2-macro list` shows the bundled samples and
   `z2-macro install <name>` copies one into `~/.z2term/macros/` (`z2-macro install all` for every one).
   Edit them freely — install never overwrites an existing file, so your edits are safe (`-f` forces it).

---

## 3. Trigger reference (events.jsonl)

- Location: `~/.z2term/events.jsonl` (one JSON per line, append-only).
- **No size cap**: the file keeps appending all history into one file, so you can go back and aggregate over the whole log in one place.
  If size becomes a concern, truncate it yourself from the terminal (e.g. `: > ~/.z2term/events.jsonl`). Note the "newest at the top" mode rewrites the whole file per entry, so the default (append at the end) is lighter for heavy use. If you stay on prepend and the log passes 10MB, the settings screen shows a warning with the size and what to do.
- Default fields: `ts` (epoch ms, integer), `time` (ISO8601 string), `event` (kind), and sometimes `level` (battery %), `ssid` (Wi‑Fi name).
- The output format is templatable in Settings, but **for macros keep the default JSONL** — it's the easiest to parse.

### Event kinds (values of `event`)

| event | Meaning | Extra fields |
|---|---|---|
| `screen_on` / `screen_off` | Screen on / off | — |
| `unlocked` | Unlocked (after auth) | — |
| `power_connected` / `power_disconnected` | Charging started / stopped | `level` |
| `battery_low` / `battery_okay` | Battery low / recovered | `level` |
| `battery_level` | Level crossed a 10% boundary | `level` |
| `wifi_connected` / `wifi_disconnected` | Wi‑Fi connected / disconnected | `ssid` (blank without location permission) |
| `headset_plugged` / `headset_unplugged` | Wired headset plugged / unplugged | — |
| `bt_audio_connected` / `bt_audio_disconnected` | **Bluetooth audio** (earbuds etc.) connected / disconnected | — |
| `airplane_on` / `airplane_off` | Airplane mode on / off | — |
| `ringer_normal` / `ringer_vibrate` / `ringer_silent` | Ringer mode change | — |
| `alarm` | **A time scheduled with `z2-alarm`** came around | `name` (the name you gave `z2-alarm`) |
| `notify_action` | **A button added with `z2-notify -b` was pressed** | `name` (the notification's name), `action` (the label pressed) |
| `unlock_failed` / `unlock_succeeded` | Lock-screen unlock **failed / succeeded** (anti-theft; needs ⚙ Settings "Watch unlock failures" ON + device admin activated). **PIN / pattern / password only** — never fires for fingerprint or face (use `unlocked` as the "cleared" signal) | `level` on `unlock_failed` (consecutive failure count) |

Example lines:
```json
{"ts":1752620719000,"time":"2026-07-16T10:25:19+09:00","event":"power_connected","level":42}
{"ts":1752620750000,"time":"2026-07-16T10:26:00+09:00","event":"wifi_connected","ssid":"home"}
```

### Notification triggers (notifications.jsonl)

- Location: `~/.z2term/notifications.jsonl`. Fields: `ts` `time` `pkg` (package) `app` (app label) `title` `text` `category` `key`.
- Use it as the starting point for "when a notification from a certain app arrives…".

---

## 4. Action reference (z2-* commands)

Run them from the terminal and the app performs the Android side. **All permission-free** (the callee of `z2-intent` may need its own permissions).

| Command | Usage | What it does | Returns |
|---|---|---|---|
| `z2-notify` | `z2-notify [-h] [-n name] [-b label]... "title" "text"` | Post a notification (`-h` shows a banner, `-b` adds a **reply button**) | — |
| `z2-toast` | `z2-toast "message"` | Short on-screen message | — |
| `z2-say` | `z2-say "text to speak"` (stdin if no arg) | Speak via TTS | — |
| `z2-torch` | `z2-torch on\|off\|toggle` (default toggle) | Flashlight | `on`/`off` |
| `z2-vibrate` | `z2-vibrate [ms]` (default 200) | Vibrate | — |
| `z2-media` | `z2-media playpause\|play\|pause\|next\|previous\|stop` (default playpause) | Send a media key | — |
| `z2-volume` | `z2-volume up\|down\|mute\|unmute\|N\|N%` | Media volume | `current/max` |
| `z2-sensor` | `z2-sensor light\|accel\|proximity` | Read a sensor once | light`{"lux":F}` / proximity`{"distance":F}` / accel`{"x":F,"y":F,"z":F}` |
| `z2-clip` | `z2-clip get` / `z2-clip set [text]` | Clipboard get/set | content on get |
| `z2-battery` | `z2-battery` | Battery state | `{"level":N,"charging":bool}` |
| `z2-share` | `z2-share "text"` | Share sheet | — |
| `z2-open` | `z2-open <URL\|path>` | Open in the default app | — |
| `z2-intent` | see below | Fire an arbitrary Intent | — |
| `z2-state` | `z2-state [key]` | **Current device state** (see below) | JSON, or the raw value for a key |
| `z2-alarm` | `z2-alarm at\|daily HH:MM [name]` etc. | Set a **time trigger** (see below) | JSON of the schedule |
| `z2-macro` | `z2-macro list\|install\|show\|run\|dir` | Manage the bundled samples | — |

### `z2-notify -b` (get an answer back = interactive macros)

Adding `-b label` puts buttons on the notification. Pressing one appends

```json
{"event":"notify_action","name":"confirm","action":"yes"}
```

to `events.jsonl`, so a macro can **ask a question, wait for your button, and carry on**.
Up to 3 buttons (Android's display limit). `-n name` tells one question's answers from another's.

```sh
z2-notify -h -n cleanup -b yes -b later "Clean up?" "This deletes temporary files"
```

```sh
# the side waiting for an answer (change handle() from the 5-0 skeleton; register under Resident servers)
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *notify_action*) ;; *) continue ;; esac
    case "$rec" in *cleanup*) ;; *) continue ;; esac
    case "$rec" in
      *yes*)   rm -rf ~/tmp/* && z2-toast "cleaned" ;;
      *later*) z2-alarm in 1h cleanup ;;   # ask again in an hour
    esac
  done
}
```

The notification closes itself once a button is pressed. Ignoring it simply does nothing.

### `z2-state` (ask for the current state)

events.jsonl only tells you about *changes*. When a macro needs to branch on how things are
**right now**, use `z2-state`. With no argument it returns everything as JSON; with a key it
returns just that value, so it drops straight into a shell test. **No extra permissions needed.**

| Key | Value |
|---|---|
| `screen` | `on` / `off` |
| `locked` | `true` / `false` (lock screen showing) |
| `idle` | `true` / `false` (in Doze / power-saving sleep) |
| `charging` | `true` / `false` |
| `plug` | `ac` / `usb` / `wireless` / `none` |
| `level` | Battery percentage (integer) |
| `wifi` | `true` / `false` (connected over Wi‑Fi) |
| `ssid` | Wi‑Fi name (empty without location permission) |
| `ringer` | `normal` / `vibrate` / `silent` |
| `airplane` | `true` / `false` |
| `headset` | `true` / `false` (wired headset/headphones) |
| `bt_audio` | `true` / `false` (Bluetooth audio connected) |
| `temp` | Battery temperature in °C (decimal); `-1` when unavailable |
| `volume` / `volume_max` | Current / maximum media volume |

```sh
z2-state                                  # everything as JSON
[ "$(z2-state charging)" = "true" ] && echo charging
[ "$(z2-state screen)" = "off" ] && z2-notify "only notify while the screen is off"
```

### `z2-alarm` (run on a schedule)

A time trigger for things like "every morning at 7". When the time comes, one line
`{"event":"alarm","name":"…"}` is appended to `events.jsonl`, so you consume it like any other event.

```sh
z2-alarm at 07:00 morning      # once at the next 07:00 (tomorrow if today has passed)
z2-alarm daily 07:00 morning   # every day at 07:00
z2-alarm in 5m tea             # once, 5 minutes from now (30s / 2h also work)
z2-alarm list                  # list what is scheduled
z2-alarm cancel morning        # cancel by name (id or all also work)
```

- **Versus cron**: cron stops running once Android enters power-saving sleep (Doze). `z2-alarm`
  asks the OS to wake the app, so it fires with the screen off too.
- It uses the battery-friendly alarm API, so **firing can be a few minutes late** — not suitable
  when you need second-level accuracy.
- Schedules survive a reboot (the app re-registers them on boot).
- The name exists so one macro can tell its alarms apart; branch on `name` in your script.

### `z2-intent` (the workhorse action)

Builds an arbitrary Android Intent with `am start`-style flags and does `startActivity` by default.
**This single command expresses launching apps, opening settings screens, setting alarms, map searches, sharing, and more.**

```
z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
          [--es KEY VAL] [--ez KEY true|false] [--ei KEY N]
          [--broadcast | --service]
```

| Flag | Meaning |
|---|---|
| `-a`, `--action` | Intent action (e.g. `android.intent.action.VIEW`). A leading non-flag arg is also treated as the action |
| `-d`, `--data` | Data URI (e.g. `https://…`, `tel:…`, `geo:…`) |
| `-t`, `--type` | MIME type |
| `-p`, `--package` | Restrict to a target package |
| `-n`, `--component` | Explicit component `package/class` |
| `-f`, `--flags` | Intent flags (integer) |
| `--es K V` | String extra |
| `--ez K true\|false` | Boolean extra |
| `--ei K N` | Integer extra |
| `--broadcast` / `--service` | Use sendBroadcast / startService instead of startActivity |

Examples:
```sh
z2-intent -a android.intent.action.VIEW -d "https://example.com"   # open a URL
z2-intent -a android.intent.action.DIAL -d "tel:0123456789"        # prefill the dialer
z2-intent -a android.intent.action.VIEW -d "geo:0,0?q=Tokyo Station" # search on a map
z2-intent -a android.settings.WIFI_SETTINGS                        # open Wi‑Fi settings
z2-intent -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR 7 \
          --ei android.intent.extra.alarm.MINUTES 0                # set a 07:00 alarm
```

---

## 5. Writing a macro (templates)

### 5-0. How to read the log (the skeleton every macro uses)

**Read this first.** Every example below assumes this skeleton.

Do not follow the log with `tail -F`. The log format is yours to change, and switching to
**"newest first" makes it prepend — new entries never reach the end of the file**. `tail -F` only
watches the end, so under that setting it picks up nothing, ever. A multi-line template also breaks
the "one line = one record" assumption.

So diff against a **snapshot of the previous read** instead. Whether the new bytes landed before or
after the old content tells you the direction, and passing the diff along as one blob handles
multi-line templates.

```sh
#!/bin/sh
# The shared skeleton. Change LOG, the work-file tag, and handle().
POLL=2                                    # how often to poll the log (seconds)
LOG=$HOME/.z2term/events.jsonl
SNAP=$HOME/.z2term/.mymacro.snap
WORK=$HOME/.z2term/.mymacro.work

[ -f "$LOG" ] || : > "$LOG"

# The new chunk arrives here. See 5-1 onward for how to inspect it.
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in
      *power_connected*) z2-toast "Charging started" ;;
    esac
  done
}

# The first pass only records a baseline, so existing entries never fire.
cp "$LOG" "$SNAP" 2>/dev/null || : > "$SNAP"

while :; do
  sleep "$POLL"
  [ -f "$LOG" ] || continue

  cn=$(wc -c < "$LOG"  2>/dev/null || echo 0)
  pn=$(wc -c < "$SNAP" 2>/dev/null || echo 0)
  [ "$cn" = "$pn" ] && continue           # same size = nothing new

  new=''
  if [ "$cn" -gt "$pn" ] && [ "$pn" -eq 0 ]; then
    # Previously empty = all of it is new. (The startup baseline keeps old entries from firing.)
    new=$(cat "$LOG")
  elif [ "$cn" -gt "$pn" ]; then
    grew=$((cn - pn))
    head -c "$pn" "$LOG" > "$WORK" 2>/dev/null
    if cmp -s "$WORK" "$SNAP"; then
      new=$(tail -c "$grew" "$LOG")  # starts with the old content -> appended (newest last)
    else
      tail -c "$pn" "$LOG" > "$WORK" 2>/dev/null
      if cmp -s "$WORK" "$SNAP"; then
        new=$(head -c "$grew" "$LOG")  # ends with the old content -> prepended (newest first)
      fi
      # Neither = rewritten/cleaned. Just re-baseline without firing.
    fi
  fi
  # cn < pn (truncated) also just re-baselines.

  cp "$LOG" "$SNAP" 2>/dev/null
  [ -n "$new" ] && handle "$new"
done
```

**This is where format dependence splits**:

- **Matching on event names is format-independent.** `{event}` renders as `power_connected`
  verbatim in both JSON and a template, so `case "$rec" in *power_connected*)` always works.
  All four bundled samples are written this way.
- **Values** (battery level, etc.) come from `z2-state`, which never touches the log. Prefer this.
- **Parsing a log field** (like `ssid`, which `z2-state` doesn't expose) is the one part that
  depends on the format you chose. Write it against the default JSONL and adjust if you change it.

**Limit**: records arriving within the same `POLL` cycle are delivered as one blob.

### 5-1. Minimal: watch events and react

Change only `handle()` from the 5-0 skeleton (matching on event names, so format-independent).

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in
      *power_connected*)   z2-say "Charging started" ;;
      *headset_plugged*)   z2-media play ;;
      *headset_unplugged*) z2-media pause ;;
      *screen_off*)        : ;;  # example: do nothing
    esac
  done
}
```

### 5-2. Use the fields (battery level, SSID)

**Try `z2-state` first** — it never reads the log, so the format cannot matter.

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *battery_low*) ;; *) continue ;; esac
    z2-notify "Battery" "Only $(z2-state level)% left"
  done
}
```

Only reach into the log for what `z2-state` doesn't expose, like `ssid`. **This part depends on the
format**, so it assumes the default JSONL (adjust the extraction if you change it).

```sh
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *wifi_connected*) ;; *) continue ;; esac
    ssid=$(printf '%s' "$rec" | jq -r '.ssid // empty' 2>/dev/null)   # sed version in section 6
    [ "$ssid" = "home" ] || continue
    z2-volume 60% ; z2-toast "Home Wi‑Fi: volume restored"
  done
}
```

### 5-3. Make it resident

Register a **start command** in `⚙ Settings → Resident servers` (e.g. `sh ~/.z2term/macros/watch.sh`).
Turn on "auto-start on boot" and it runs without opening the app and after a reboot. For a quick
test you can just run `sh ~/.z2term/macros/watch.sh &` in the terminal.

### 5-4. Time / recurring

For "every morning at 7" and the like, use **`z2-alarm`**. When the time comes one `alarm` line is
appended to `events.jsonl`, so you read it exactly like any other event.

```sh
z2-alarm daily 07:00 morning     # set it once (it survives a reboot)
```

```sh
# the watcher (change handle() from the 5-0 skeleton; register under Resident servers)
# Fire only when both 'alarm' and the name given to z2-alarm ('morning') show up.
# A multi-line template splits them across lines, so check the chunk as a whole.
handle() {
  case "$1" in *alarm*) ;; *) return ;; esac
  case "$1" in *morning*) ;; *) return ;; esac
  z2-say "Good morning. Battery is $(z2-state level) percent"
}
```

The distro's cron works too, but **cron stops once Android enters power-saving sleep (Doze)**.
Use `z2-alarm` when it has to run with the screen off (at the cost of firing a few minutes late).

### 5-5. Worked example: notify + log location after N failed unlocks (anti-theft)

Turn on ⚙ Settings "Watch unlock failures" and activate **device admin** as prompted; unlock
failures then arrive as `unlock_failed` (`level` = consecutive failure count). React from the 3rd:

```sh
# the watcher (change handle() from the 5-0 skeleton; register under Resident servers)
# The count is the level field, so this assumes the default JSONL (adjust if you change the format).
handle() {
  printf '%s\n' "$1" | while IFS= read -r rec; do
    case "$rec" in *unlock_failed*) ;; *) continue ;; esac
    n=$(printf '%s' "$rec" | jq -r '.level // 0' 2>/dev/null)
    [ "$n" -ge 3 ] 2>/dev/null || continue
    ts=$(date '+%F %T')
    z2-notify -h "Unlock failed ${n}x" "$ts"        # push to your other device too
    echo "$ts unlock_failed x$n" >> ~/theft.log      # keep a record
    # e.g. exfiltrate to your home server (needs an ssh key)
    # scp ~/theft.log backup:/srv/ 2>/dev/null
  done
}
```

⚠ **`unlock_failed` / `unlock_succeeded` only fire for PIN, pattern and password attempts.**
Android does not report biometric attempts to device admins, so **unlocking with a fingerprint produces no
`unlock_succeeded`**. If you build "sound an alarm on failures, stop once unlocked" and wait for
`unlock_succeeded`, it will never stop when the phone is unlocked by fingerprint. **Use `unlocked`
(`USER_PRESENT`, fired on every successful unlock including biometrics) as the stop signal** — it is emitted
whenever "System event detection" is on.

What gets captured or sent is **up to your macro** (the app builds in no camera capture — background
photos are blocked by Android and need separate root/dedicated tooling). Combine with a location tool
in your distro or an API for coordinates. **Device admin is used only to watch the failure count; it never locks or wipes your device.**

### 5-6. Worked example: auto-copy an SMS one-time code and clear it

A practical macro that puts the **one-time code (OTP / verification number)** from a notification (SMS, etc.)
onto the clipboard, then clears it after a delay — but only if it hasn't changed. It combines the notification
trigger, `z2-clip`, and self-cleanup (a subshell + `sleep`), using nothing outside this guide.

**Key points** (reusable patterns):
- **Filter by keyword** (verification / code / OTP …) so ordinary messages and phone numbers aren't picked up.
- Extract **one 4–8 digit number** only; for `123-456` style, strip separators first.
- **Independent of both log format and write direction** (see "why not read it line by line" below). Change the
  template however you like, or flip to "newest first", and the macro keeps working unchanged.
- **Auto-clear** runs only when the current clipboard still equals what was copied (if you copied something else
  in the meantime, it's kept). The clipboard is shared and readable by other apps, so clearing after paste is safer.

**Why not read it line by line** (the heart of this macro):

**You choose the log format.** A naive "one line = one notification" reader that follows the tail with `tail -F`
breaks in two ways:

- With a **multi-line template** (a format containing `\n`), the title and the body land on separate lines, so
  the keyword and the code are never on the same line.
- With **prepending** (Settings -> "newest first"), new entries never reach the end of the file, so `tail -F`
  picks up nothing, ever.

So it diffs against a **snapshot of the previous read** instead. Whether the new bytes landed before or after the
old content tells you the write direction, and the diff is scanned **as one blob** rather than split into lines,
so multi-line templates are handled naturally.

One more thing: the freer the format, the more "digits that look like a code" creep in. Timestamps, epochs
(`{ts}`), notification ids (`{key}` looks like `0|com.example|2847|null|10268`) and package names (`{pkg}`) are
stripped first, and the code is then chosen **by its position relative to the keyword**. A "first number wins"
approach mistakes the notification id for the code as soon as the template includes `{key}`.

```sh
#!/bin/sh
# ~/.z2term/macros/otp-clip.sh
# Auto-copy a one-time code (4-8 digits) from a notification, then clear it after TTL seconds.
# Independent of both log format and write direction.
# Setup: Settings -> "Notification capture" ON + grant the OS "Notification access".
# Resident: Settings -> Resident servers -> register  sh ~/.z2term/macros/otp-clip.sh

TTL=60                                    # seconds before the copy is cleared
KEYWORDS='verification|verify|code|otp|one[- ]?time|passcode|認証|確認|コード'

POLL=2                                    # how often to poll the log (seconds)
LOG=$HOME/.z2term/notifications.jsonl
SNAP=$HOME/.z2term/.otp-clip.snap
WORK=$HOME/.z2term/.otp-clip.work

[ -f "$LOG" ] || : > "$LOG"

# After TTL seconds, blank the clipboard only if it still holds the copied value.
schedule_clear() {
  code=$1
  ( sleep "$TTL"
    cur=$(z2-clip get 2>/dev/null)
    if [ "$cur" = "$code" ]; then
      z2-clip set ""
      z2-toast "Cleared the copied code"
    fi
  ) &
}

handle() {
  raw=$1

  # Drop things that look like a code but are not: dates / times / 9+ digit runs (epochs) /
  # tokens containing '|' (notification id from {key}) / dotted ids ({pkg}). Then join "123-456".
  scan=$(printf '%s' "$raw" | sed \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}[T ][0-9:+-]*/ /g' \
    -e 's/[0-9]\{4\}-[0-9]\{2\}-[0-9]\{2\}/ /g' \
    -e 's/[0-9]\{1,2\}:[0-9]\{2\}\(:[0-9]\{2\}\)\?/ /g' \
    -e 's/[0-9]\{9,\}/ /g' \
    -e 's/[^ ]*|[^ ]*/ /g' \
    -e 's/[A-Za-z0-9_]\{1,\}\.[A-Za-z0-9_.]\{1,\}/ /g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g' \
    -e 's/\([0-9]\)-\([0-9]\)/\1\2/g')

  # Prefer digits right after the keyword, else the nearest ones before it.
  # With a free-form template, metadata digits sit nearby, so position is what disambiguates.
  # Always take maximal digit runs so a long run never yields a partial match.
  code=$(printf '%s' "$scan" | awk -v kw="$KEYWORDS" '
    function firstcode(s,   r) {
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) return r
        s = substr(s, RSTART + RLENGTH)
      }
      return ""
    }
    function lastcode(s,   r, best) {
      best = ""
      while (match(s, /[0-9]+/)) {
        r = substr(s, RSTART, RLENGTH)
        if (length(r) >= 4 && length(r) <= 8) best = r
        s = substr(s, RSTART + RLENGTH)
      }
      return best
    }
    { buf = buf " " $0 }                    # treat multi-line records as one blob
    END {
      if (!match(tolower(buf), kw)) exit       # no keyword = not an auth notification
      # RSTART/RLENGTH are awk globals that the match() calls below clobber, so save them first.
      ks = RSTART; kl = RLENGTH
      c = firstcode(substr(buf, ks + kl))      # prefer what follows the keyword
      if (c == "") c = lastcode(substr(buf, 1, ks - 1))   # otherwise the nearest digits before it
      if (c != "") print c
    }')
  [ -z "$code" ] && return

  z2-clip set "$code"
  z2-toast "Copied code: ${code}"
  schedule_clear "$code"
}

# The first pass only records a baseline, so existing entries never fire.
cp "$LOG" "$SNAP" 2>/dev/null || : > "$SNAP"

while :; do
  sleep "$POLL"
  [ -f "$LOG" ] || continue

  cn=$(wc -c < "$LOG"  2>/dev/null || echo 0)
  pn=$(wc -c < "$SNAP" 2>/dev/null || echo 0)
  [ "$cn" = "$pn" ] && continue           # same size = nothing new

  new=''
  if [ "$cn" -gt "$pn" ] && [ "$pn" -eq 0 ]; then
    # Previously empty = all of it is new. (The startup baseline keeps old entries from firing.)
    new=$(cat "$LOG")
  elif [ "$cn" -gt "$pn" ]; then
    grew=$((cn - pn))
    head -c "$pn" "$LOG" > "$WORK" 2>/dev/null
    if cmp -s "$WORK" "$SNAP"; then
      new=$(tail -c "$grew" "$LOG")  # starts with the old content -> appended (newest last)
    else
      tail -c "$pn" "$LOG" > "$WORK" 2>/dev/null
      if cmp -s "$WORK" "$SNAP"; then
        new=$(head -c "$grew" "$LOG")  # ends with the old content -> prepended (newest first)
      fi
      # Neither = rewritten/cleaned. Just re-baseline without firing.
    fi
  fi
  # cn < pn (truncated) also just re-baselines.

  cp "$LOG" "$SNAP" 2>/dev/null
  [ -n "$new" ] && handle "$new"
done
```

The only knobs are `TTL` (seconds until clearing), `POLL` (how fast it reacts) and `KEYWORDS` (add terms for
more services). The code lands on the clipboard within `POLL` seconds of arriving, so you just paste it.

**Limits**: two notifications arriving within one `POLL` cycle are treated as a single blob (rare for auth
codes, but a real gap).

**Important on Android 15+ (OTP bodies get redacted)**: with "**Enhanced notifications**" (a.k.a. "Adaptive
Notifications") on, Android System Intelligence classifies notifications containing an OTP as **sensitive** and
replaces their body with a placeholder (e.g. "Sensitive content hidden") **before** handing it to
**"untrusted" notification listeners — which every ordinary app is**. Even with notification access fully
granted, only the OTP body is withheld (the notification still arrives, so a row appears, but `text` is the
placeholder). Two ways around it:
- **For SMS OTPs, use "SMS detection" instead (recommended)** → see 5-7 below. It reads the SMS directly, fully
  bypassing this redaction, and works even while locked. The most reliable option, independent of OEM settings.
- Turn **`Settings → Notifications → Enhanced notifications` OFF** (works on Pixel etc.; some OEMs lack this
  toggle or it has no effect; also disables the OS OTP-autofill suggestions).
- Become a **"trusted" listener** holding `RECEIVE_SENSITIVE_NOTIFICATIONS`. That is reserved for system-signed
  apps or specific roles (companion watch/glasses, home, …); ordinary apps aren't granted it automatically and
  must declare it and grant it via adb (may be rejected depending on the OEM).

Once un-redacted, the only thing that still cannot be read is a notification whose body lives solely in a fully
custom layout, with no text in the title, text, or message fields (a few apps). SMS one-time codes normally sit
in the MessagingStyle body, captured from 0.8.185 on.

### 5-7. Copy an SMS one-time code reliably via "SMS detection" (bypasses redaction)

On Android 15+, SMS OTPs delivered as notifications are redacted (above), so notification detection may not see
them (the same is true for notification-based triggers in MacroDroid etc.). z2term therefore has **SMS detection**
that reads the SMS body directly — never going through the sensitive-notification redaction or lock state.

- Setup: `⚙Settings → SMS detection` **ON**, then **grant the SMS permission** in the prompt.
- Log: `~/.z2term/sms.jsonl` (fields: `ts` `time` `from` `body`).
- Auto-copy: `z2-macro install otp-sms.sh` installs the SMS variant of 5-6 (reads `sms.jsonl`, extracts 4–8
  digits). Register `sh ~/.z2term/macros/otp-sms.sh` under `⚙Settings → Resident servers` to copy OTPs even while
  locked.

OTPs that are **not SMS** (e.g. authenticator-app notifications) are out of scope for this route (use notification
detection plus the workarounds above).

---

## 6. Parsing without jq (pure POSIX)

Where `jq` isn't available, extract fields with sed/grep (each JSONL line is a single record, so it's easy).

```sh
# get the event value
ev=$(printf '%s' "$line" | sed -n 's/.*"event":"\([^"]*\)".*/\1/p')
# get level (numeric)
level=$(printf '%s' "$line" | sed -n 's/.*"level":\([0-9]*\).*/\1/p')
```

---

## 7. Let an AI write the macro

Feed this guide to an AI, then hand it the instruction below plus what you want, and it produces a
ready-to-run macro. **Example instruction (copy-paste):**

> You are a z2term macro generator. Using only the spec in this `MACRO-GUIDE.md`, output a **single
> POSIX sh script** that satisfies the request below. Constraints:
> - Watch the log with the skeleton from "5-0. How to read the log" (never `tail -F` — it breaks
>   under the prepend setting). Change only `LOG`, the work-file tag, and `handle()`.
> - Branch by matching event names (`case "$rec" in *power_connected*)`) — that is format-independent.
> - Take values from `z2-state` whenever it exposes them; parse log fields only for what it doesn't.
> - Use only the event kinds and fields listed in "3. Trigger reference".
> - Use only the `z2-*` actions listed in "4. Action reference" (never invent features).
> - Prefer `jq` for JSON parsing and also include a sed fallback for when it's missing.
> - Add comments for any dependency install (jq, …) and for registering it as a Resident server.
> - Keep it to one self-contained file with a short comment on each branch.
>
> What I want: "__describe it in natural language__" (e.g. when charging starts set volume to 30% and
> say "charging", when unplugged restore volume to 70%).

The trick is to explicitly say **stay within this guide** so the AI won't reach for features that don't exist.

---

## 8. Troubleshooting

- **events.jsonl doesn't grow** → is "System event detection" on in ⚙ Settings? An ongoing notification shows while active.
- **`ssid` is blank** → reading the SSID needs location permission (v1 doesn't request it, so it can be blank). Connect/disconnect detection still works.
- **`z2-*: cannot write request (storage perm?)`** → check the app's storage permission.
- **`z2-media` does nothing** → there must be a recently-playing media app (it only sends the key).
- **`z2-torch` errors** → devices without a flash can't use it.
- **Resident macro dies** → exclude the app from battery optimization and check the resident-server settings. With low-power mode on, reactions can lag while the screen is off.
