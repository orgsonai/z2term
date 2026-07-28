# z2term Macro Guide (z2term-macro)

**How to build phone-automation "macros" using nothing but the z2term terminal.**
It is a manual you can read and write by hand, and at the same time a **machine-readable
reference you can feed whole to an AI** — then just say "I want to …" and it generates the macro.

> Target version: 0.8.247-alpha and later / 日本語版: `docs/ja/MACRO-GUIDE.md`
> Everything here is **non-root, fully local, no external transmission**. No hard-permission features are included.

---

## 1. The model (three stages)

z2term macros follow the same "**trigger → decide → action**" shape as MacroDroid and friends.

| Stage | Direction | In z2term |
|---|---|---|
| **Trigger** | Android → shell | **Register what should wake you with `z2-when`** (charge/battery/time/Wi‑Fi/SMS/sensor/new file/notification/device event). Your command runs once, only when the condition matches. The same things are also appended to `~/.z2term/events.jsonl`, so **you can watch the log yourself instead** (notifications go to `~/.z2term/notifications.jsonl`, SMS to `~/.z2term/sms.jsonl`; for OTPs, SMS detection bypasses redaction — see 5-7) |
| **Decide (logic)** | shell | Branch on the condition (`if`, time, counts, state files…). Plain sh/awk/jq, anything goes. **Current state** comes from `z2-state` |
| **Action** | shell → Android | `z2-*` commands drive the Android side (notify/speak/volume/torch/fire an Intent…) |

### Two ways to receive a trigger

| | When to use it | What you write |
|---|---|---|
| **A. Let `z2-when` do it** (the default; since 0.8.205) | Whenever a registrable trigger covers your case | A short script with **only the work in it** (runs once and exits) |
| **B. Watch the log yourself** (the 5-0 skeleton) | Triggers `z2-when` does not have, or decisions that combine several events | A resident script that keeps diffing the log |

**Try A first.** The app does the waiting, so **you run no resident script at all** and it costs no
battery. B is the escape hatch for what A cannot express (5-5 / 5-6 / 5-7 in this guide, where the
decision needs the contents of the log).

**A macro is simply "a shell script that checks a condition and fires actions when the trigger arrives."**

---

## 2. One-time setup

1. **Enable triggers**: app ⚙ Settings → "**Resident servers & automation**" →
   - Turn on "**System event detection**". `z2-when`'s `charge:` / `battery:` / `wifi:` /
     `sensor:` / `file:` / `event:` and the `events.jsonl` log all depend on it.
   - If you use notifications too, turn on "**Notification detection**" (and grant the OS
     "notification access"). That is what `notify:` needs.
   - If you use SMS, turn on "**SMS detection**" (and allow receiving SMS). That is what `sms:` needs.
   - **Only the `time:` family works with detection off** (the OS alarm wakes it up).
2. **Register a trigger**: `z2-when <trigger> run <command>` (→ section 4). Everything you register
   also shows up under 📜 → the "**Automation**" tab, where you can toggle rules, **▶ run one once
   without waiting for the trigger**, read its run log, pause everything, and see recent fires.
   **You can also create and edit them there** (0.8.272: "+ New" picks a trigger from a list, **✎**
   opens an existing rule's command and filters). Terminal and screen write **the same files**
   (`~/.z2term/when/*.rule`), so you can move between them freely.
   ⚠ **A command is one line.** A rule file holds one item per line, so a newline throws away
   everything after it (since 0.8.272 newlines are folded into spaces on registration, but for
   anything long it is safer to **put it in a script file and register that path**).
3. **To keep something resident** (only for style B): ⚙ Settings → "**Resident servers**" → register
   your script's start command; it then runs without opening the app and after reboot (also turn on
   "auto-start on boot").
4. Handy tool: install `jq` (JSON parsing). e.g. Alpine `apk add jq` / Debian-family `apt install jq`.
5. **If you would rather not start from a blank file**: `z2-macro list` shows the 7 bundled samples and
   `z2-macro install <name>` copies one into `~/.z2term/macros/` (`z2-macro install all` for every one).
   Edit them freely — install never overwrites an existing file, so your edits are safe (`-f` forces it).
   On install it also tells you **how that script is meant to be run** (register it as a resident
   server / drive it with `z2-when` / assign it to a widget button).

---

## 3. Trigger reference

### 3-A. Triggers you can register with `z2-when`

Register with `z2-when <trigger> run <command>`. When the condition matches, **your command runs
exactly once** (no resident script involved).

| Trigger | When | Needs |
|---|---|---|
| `charge:start` / `charge:stop` | Charging started / stopped | detection ON |
| `battery:below=N` / `battery:above=N` | The level **crossed** N% downward / upward | detection ON |
| `time:daily=HH:MM` | Every day at HH:MM | — |
| `time:at=HH:MM` | Once, at the next HH:MM | — |
| `time:every=Nm` / `time:every=Nh` | Every N minutes / hours | — |
| `time:cron='min hour dom month dow'` | A cron expression (dow 0-7 / 0,7 = Sunday). It has spaces, so **quote it** | — |
| `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<name>` | Wi‑Fi connected / disconnected / joined that SSID | detection ON |
| `net:online` / `net:offline` | a usable connection appeared / went away (mobile counts too) | detection ON |
| `net:wifi` / `net:mobile` / `net:ethernet` | the link in use **switched to that one** | detection ON |
| `share:any` / `share:text` / `share:file` | something was shared to z2term from another app | — |
| `share:contains=<part>` / `share:ext=<ext>` | filter on the shared text / the file extension | — |
| `boot` | the device finished starting up (no `:`) | — |

⚠ **A misspelled trigger will not register** (0.8.265). If it did, you would get a rule that sits in the list and never runs, with no way to find out why. Only `event:` names are left unchecked (the list from `z2-when events` is the source of truth).

| `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` | An SMS arrived | SMS detection |
| `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near\|far` | Shaken / light crossed N lux / proximity changed | detection ON |
| `file:new=<dir>[,ext=<ext>]` | **A new file landed in that folder** (after the write finishes) | detection ON |
| `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` | A notification arrived (`pkg=` matches the package name *or* the app label) | notification access |
| `event:<name>` / `event:<prefix>*` / `event:*` | Any device event, **by name** (same names as 3-B; `z2-when events` lists them) | depends on the name |

The command that fires gets **what happened** in its environment.

| Variable | Holds |
|---|---|
| `Z2_WHEN_TRIGGER` | The trigger string you registered (all triggers) |
| `Z2_WHEN_LEVEL` | Battery % (`charge:` / `battery:`) |
| `Z2_WHEN_SSID` | Wi‑Fi name (`wifi:`) |
| `Z2_WHEN_NET` / `Z2_WHEN_NET_PREV` | the link now / the one before (`net:`; `wifi` `mobile` `ethernet` `vpn` `other` `none`) |
| `Z2_WHEN_SHARE` / `Z2_WHEN_SHARE_KIND` | the shared string / its kind `text`·`file` (`share:`) |
| `Z2_WHEN_SMS_FROM` / `Z2_WHEN_SMS_BODY` | Sender / body (`sms:`) |
| `Z2_WHEN_OTP` | The extracted one-time code (`sms:otp` / `notify:otp`) |
| `Z2_WHEN_SENSOR` / `Z2_WHEN_LUX` | `shake`/`light`/`proximity:near\|far` / illuminance (`sensor:`) |
| `Z2_WHEN_FILE` / `Z2_WHEN_DIR` | Full path of the new file / its folder (`file:`) |
| `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT` | Package / app label / title / body (`notify:`) |
| `Z2_WHEN_EVENT` | Event name (`event:`) |
| `Z2_WHEN_EVENT_NAME` | The identifier you armed it with (`event:alarm` / `event:notify_action`) |
| `Z2_WHEN_ACTION` | The button label that was pressed (`event:notify_action`) |

```sh
z2-when charge:start run ~/.z2term/macros/backup.sh
z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
z2-when event:headset_plugged run ~/.z2term/macros/play.sh
z2-when 'event:ringer_*' run 'z2-toast "ringer: $Z2_WHEN_EVENT"'
z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
```

**Filters** (0.8.263 — put them right after the trigger, before `run`; they work the same for every kind of trigger):

| Written as | Meaning |
|---|---|
| `if=wifi,!screen` | Only when the device is in that state (commas are "and", `!` negates). The conditions are what `z2-state` shows (`wifi` `charging` `screen` `locked` `headset` `airplane` / `ssid=Home` `ringer=silent` / `level<30` `temp>40`) |
| `cooldown=1h` | Do not run again for that long (`30s` / `10m` / `2h`; a bare number means minutes) |
| `between=22:00-07:00` | Only inside that window (it may wrap past midnight) |
| `days=mon-fri` | Only on those days (`sat,sun`, or cron-style numbers like `1-5`) |

```sh
# Only when charging starts on the home network, at most once an hour
z2-when charge:start if=ssid=Home cooldown=1h run ~/.z2term/macros/backup.sh
# Weeknights, and only while the screen is off
z2-when time:every=30m if=!screen between=22:00-07:00 days=mon-fri run ~/.z2term/macros/nightly.sh
```

Skipped runs stay in `z2-when fired` as `skip:if` / `skip:cooldown` / `skip:between` / `skip:days`, so **you can tell why nothing happened**. Compared with writing your own `z2-state` check at the top of the script, this is the part that differs: the record distinguishes "held back" from "ran and did nothing".

- Strings that came from outside (SSID, SMS body, notification text, file names) are **passed in
  safely quoted**. Quote them on your side too (`"$Z2_WHEN_SMS_BODY"`) and never feed them to `eval`.
- **The same rule will not fire twice within 10 seconds** (events like `screen_on` come often).
- The command runs on **the distro you have selected**.

### 3-B. The event log (events.jsonl)

This is what you read when you watch the log yourself (style B / the 5-0 skeleton).
`z2-when`'s `event:` uses exactly the names in this table.

- Location: `~/.z2term/events.jsonl` (one JSON per line, append-only).
- **No size cap**: the file keeps appending all history into one file, so you can go back and aggregate over the whole log in one place.
  If size becomes a concern, truncate it yourself from the terminal (e.g. `: > ~/.z2term/events.jsonl`). Note the "newest at the top" mode rewrites the whole file per entry, so the default (append at the end) is lighter for heavy use. If you stay on prepend and the log passes 10MB, the settings screen shows a warning with the size and what to do.
- Default fields: `ts` (epoch ms, integer), `time` (ISO8601 string), `event` (kind), and sometimes `level` (battery %), `ssid` (Wi‑Fi name).
- The output format is templatable in Settings, but **for macros keep the default JSONL** — it's the easiest to parse.

#### Event kinds (values of `event`)

| event | Meaning | Extra fields |
|---|---|---|
| `screen_on` / `screen_off` | Screen on / off | — |
| `unlocked` | Unlocked (after auth) | — |
| `power_connected` / `power_disconnected` | Charging started / stopped | `level` |
| `battery_low` / `battery_okay` | Battery low / recovered | `level` |
| `battery_level` | Level crossed a 10% boundary | `level` |
| `wifi_connected` / `wifi_disconnected` | Wi‑Fi connected / disconnected | `ssid` (blank without location permission) |
| `net_online` / `net_offline` | a usable connection appeared / went away | — |
| `net_wifi` / `net_mobile` / `net_ethernet` | the link in use switched | — |
| `boot` | the device started up (recorded even with detection off) | — |
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

#### The notification log (notifications.jsonl)

- Location: `~/.z2term/notifications.jsonl`. Fields: `ts` `time` `pkg` (package) `app` (app label) `title` `text` `category` `key`.
- Use it as the starting point for "when a notification from a certain app arrives…" (`z2-when notify:pkg=<part>` does the same thing without logging anything).
- Logging is **independent** of `z2-when notify:`, so "do not record them, but do use them as a trigger" is exactly what you get by leaving the log off.

---

## 4. Action reference (z2-* commands)

Run them from the terminal and the app performs the Android side. **All permission-free**
(the callee of `z2-intent` may need its own permissions, and each `z2-when` trigger has its own
prerequisite → 3-A).

| Command | Usage | What it does | Returns |
|---|---|---|---|
| `z2-notify` | `z2-notify [-h] [-n name] [-b label]... "title" "text"` | Post a notification (`-h` shows a banner, `-b` adds a **reply button**) | — |
| `z2-ask` | `z2-ask [-t sec] [-H hint] [-d default] "question"` | **Ask via a notification reply field** (answerable without opening the app). Dismissing or timing out exits non-zero | the answer |
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
| `z2-screen` | `z2-screen keepon 1h` / `keepon off` / `status` | **Hold off the automatic screen timeout, with a deadline** (below) | state JSON |
| `z2-tile` | `z2-tile set <1-4> <macro\|command>` etc. | Assign it to a **quick-settings tile** (below) | TSV of all 4 slots |
| `z2-alarm` | `z2-alarm at\|daily HH:MM [name]` etc. | Set a **time trigger** (see below) | JSON of the schedule |
| `z2-when` | `z2-when <trigger> run <command>` etc. | **Register a trigger** (→ 3-A, and below) | the rule id |
| `z2-noti` | `z2-noti list` | Read **the notifications on screen right now** (read-only, see below) | TSV |
| `z2-session` | `z2-session list\|new\|send\|capture\|close` | Drive **the app's own tabs** (see below) | TSV / index |
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

### `z2-screen` (keep the screen awake — with a deadline)

For "I want to watch this long build, so stop the screen turning itself off **for an hour**".

```sh
z2-screen keepon 1h        # no automatic screen-off for an hour (30m / 90s / 90 work too)
z2-screen status           # is it held, and how many seconds are left (JSON)
z2-screen keepon off       # put it back now, without waiting for the deadline
```

- It changes the **OS-wide** setting (screen timeout), so it **holds with the app in the background**.
- ⚠ This is **not** the toolbar's 🔅. That one only lasts while the app is on screen; the two do not
  affect each other. Pick whichever matches what you are doing.
- **The original value always comes back.** It is saved when you take the hold, and the deadline is
  an OS alarm, so it is restored even if the app is killed or the device reboots. There is no way to
  leave it held and quietly drain the battery.
- Max **24h** in one go (so a typo cannot leave the screen on for days).
- **Needs "modify system settings".** Allow it under Settings › **Screen timeout (z2-screen)**.
  Without it, `z2-screen` says so and **does nothing**.
  (Calling `settings put` from the shell is not an option — the app's UID is refused.)

Combine it with `z2-when` for things like "no screen-off for two hours once charging starts":

```sh
z2-when charge:start run 'z2-screen keepon 2h'
z2-when charge:stop  run 'z2-screen keepon off'
```

### `z2-tile` (put it on the quick-settings panel)

A home-screen widget means going back to the home screen. Quick settings comes down in two swipes
**whatever app you are in**, so it is the one entry point that reaches you mid-task.

```sh
z2-tile set 1 backup.sh                          # a macro on slot 1
z2-tile set 2 'z2-screen keepon 1h' -l "no sleep"  # a command, with a label
z2-tile list                                     # all 4 slots (slot / label / command; '-' = empty)
z2-tile clear 2                                  # empty a slot (clear all works too)
```

- What you assign is either the **file name of a macro** in `~/.z2term/macros/` or a **command** to
  run as typed. **Which one it is comes from the name** — there is no `--macro` flag to get wrong.
- **Tap to run, tap again to stop.** The tile is green while it runs (same deal as the widget buttons).
- The run gets **`Z2_TILE`** (the slot number) in its environment, plus `Z2_TILE_MACRO` for a macro,
  so the same macro can sit on several slots and branch on which one was pressed.
- ⚠ **A locked device is not waved through.** Android asks to unlock first, and the command runs only
  if it is unlocked. That is not a setting: it stops someone who picked up your phone firing a command
  straight off the shade.
- ⚠ **There are exactly 4 slots.** Tiles are declared in the manifest and cannot grow at runtime.
- ⚠ **You place the tiles yourself**, from the pencil/edit screen of the quick settings panel; Android
  does not let an app put its own tiles there.
- **With nothing assigned, no tile is listed at all** (0.8.271). Only the slots you assign show up in
  the quick settings list.
- The run log is `~/.z2term/tile/run.log`, kept apart from the widget's `~/.z2term/widget/run.log`.

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

**`z2-alarm` vs `z2-when time:`**: both fire on a clock, but `z2-alarm` only **writes one `alarm`
line into `events.jsonl`** — something else (a resident script) still has to pick it up.
`z2-when time:daily=07:00 run <command>` runs **the command itself**, so no watcher is needed.
**Use `z2-when` for anything new.** `z2-alarm` is for cases where you already have a resident script,
or where you want "the alarm went off" recorded in the log as well.

### `z2-when` (register a trigger)

**The triggers you can register and the variables you get are in 3-A.** This section covers managing them.

```sh
z2-when battery:below=20 run 'z2-say "battery is getting low"'
z2-when time:daily=07:00 run ~/.z2term/macros/morning.sh
z2-when notify:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
```

| Command | What it does |
|---|---|
| `z2-when list` | Registered rules (id / on\|off / trigger / `->` / command, TSV) |
| `z2-when events` | The names you can put in `event:` |
| `z2-when log <id>` | **That rule's run log** (tail). Start here when nothing happens |
| `z2-when fired [n]` | Recent fires (time / id / trigger / `run`\|`paused`) |
| `z2-when on <id>` / `off <id>` | Enable / disable one rule |
| `z2-when pause` / `resume` | Stop / resume **every** rule (nothing is deleted) |
| `z2-when remove <id\|all>` | Delete (`rm` works too) |

- Rules also appear under 📜 → the "**Automation**" tab. **▶ runs one without waiting for its
  trigger**, which is how you shake out mistakes in the script itself.
- If something runs away, `z2-when pause` (or "Pause automatic runs" in the Automation tab).
  **No trigger fires anything** while paused, the rules stay, and ▶ still works.
- Registered commands run through `sh -lc` (inside the selected distro) each time they fire.
  **Wrap the whole command in single quotes when you use `Z2_WHEN_*`**
  (`run 'z2-toast "$Z2_WHEN_EVENT"'`). With double quotes, **the shell you are registering from
  expands it first** and an empty string gets stored.

### `z2-noti` (read the notifications on screen)

```sh
z2-noti list        # key / package / app label / title / text, as TSV
```

- Needs notification access (⚙ Settings → Resident servers & automation → Notification detection).
- **Read-only.** Pressing or dismissing other apps' notification buttons is deliberately not offered
  (it would also press their pay and send buttons).
- To react the moment one arrives, use `z2-when notify:*` (3-A). `z2-noti` is for counting or
  searching **what is there right now**.

### `z2-session` (drive the app's own tabs)

A macro can open a terminal tab in z2term itself and type into it.

```sh
z2-session list                      # tabs (index / id / kind / mark / name, TSV)
n=$(z2-session new build | cut -f1)  # open one tab, take its index
z2-session send "$n" 'make -j2' --enter
z2-session capture "$n" --all        # grab that tab's screen (--all includes scrollback)
z2-session close "$n"                # close it (never the last one)
```

- Marks in `list`: `*` = the tab on screen / `!` = something is running / `?` = not started yet / `-` = other.
- `<tab>` can be **the index, an id, or a tab name**; `.` or omitted means the tab on screen.
- **`send` only types — it does not run anything.** Add `--enter` when you do want it executed
  (so nothing starts running behind your back).
- A name given with `new <name>` is **pinned**: neither the distro name nor the shell's title overwrites it.

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

**See whether 5-A covers your case first.** If it does, the script is a few lines of "the work" and
nothing else. You only need the watching skeleton from 5-0 for triggers `z2-when` does not have, and
for decisions that read the contents of the log (5-5 / 5-6 / 5-7).

### 5-A. Let `z2-when` do it (start here)

Write the work, then register the trigger.

```sh
#!/bin/sh
# ~/.z2term/macros/lowbat.sh — just say something when the battery gets low
z2-say "battery is at $(z2-state level) percent"
z2-notify "Battery low" "$(z2-state level)% left"
```

```sh
chmod +x ~/.z2term/macros/lowbat.sh
z2-when battery:below=20 run ~/.z2term/macros/lowbat.sh
z2-when list                       # check that it registered
```

**No watch loop, no resident server.** The app does the waiting and runs this script once when the
condition matches.

Short ones need no file at all:

```sh
z2-when charge:start  run 'z2-volume 30%'
z2-when charge:stop   run 'z2-volume 70%'
z2-when wifi:ssid=home run 'z2-toast "home Wi-Fi"'
z2-when notify:otp    run 'echo "$Z2_WHEN_OTP" | z2-clip set'
```

**When nothing happens**, walk this list:

1. `z2-when list` — is it registered, and is it `off`?
2. 📜 → Automation tab → **▶** — run it once without waiting (mistakes in the script surface here)
3. `z2-when log <id>` — the output and errors from the run
4. `z2-when fired` — did it fire at all? (`paused` means automatic runs are paused)

### 5-0. How to read the log (the skeleton for watching it yourself)

**The examples in 5-5 / 5-6 / 5-7 assume this skeleton.**

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
  The **five log-watching samples** (`watch-basic` / `battery-alert` / `daily-report` / `otp-clip` /
  `otp-sms`) are written this way.
- **Values** (battery level, etc.) come from `z2-state`, which never touches the log. Prefer this.
- **Parsing a log field** (like `ssid`, which `z2-state` doesn't expose) is the one part that
  depends on the format you chose. Write it against the default JSONL and adjust if you change it.

**Limit**: records arriving within the same `POLL` cycle are delivered as one blob.

### 5-1. Minimal: watch events and react

Change only `handle()` from the 5-0 skeleton (matching on event names, so format-independent).

> 💡 One event with one reaction is shorter as `z2-when event:<name> run <command>`. You want this
> shape when you **handle several events in one script**, or when the decision depends on
> **what happened earlier**.

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

### 5-3. How to run it (resident vs. one-shot)

A script is run in one of **two ways**, and **mixing them up causes real trouble**.

| Kind | How you run it | What is in it |
|---|---|---|
| **Resident** (keeps running) | Register a start command in `⚙ Settings → Resident servers` (e.g. `sh ~/.z2term/macros/watch.sh`). Turn on "auto-start on boot" and it runs without opening the app and after a reboot | Anything built on the 5-0 skeleton (it has a watch loop) |
| **One-shot** (runs once and exits) | Register it with `z2-when` / assign it to a widget button / run it by hand | Anything shaped like 5-A ("just the work") |

⚠ **Never register a one-shot script as a resident server.** The supervisor treats "it exited" as
"it died" and restarts it, so it **runs again every time it finishes** (a feed reader would fetch
forever).

For a quick test, just run it in the terminal: `sh ~/.z2term/macros/watch.sh &` (resident) or
`sh ~/.z2term/macros/lowbat.sh` (one-shot).

**Your own scripts can declare how they are meant to be run** (0.8.247 and later).

```sh
#!/bin/sh
# rss.sh — fetch feeds and notify only what is new     <- line 2 is the description in z2-macro list
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
```

When `# z2-run:` is present, `z2-macro install` prints that line as the script's instructions
(without it, it prints the "register it under Resident servers" advice). Put it **after the
description line (line 2)**.

### 5-4. Time / recurring

**`z2-when time:`** is the shortest. The command runs directly when the time comes, so no watcher
is involved.

```sh
z2-when time:daily=07:00 run ~/.z2term/macros/morning.sh   # every morning at 7
z2-when time:every=30m   run ~/.z2term/macros/rss.sh       # every 30 minutes
z2-when time:cron='0 3 * * 1-5' run ~/.z2term/macros/nightly.sh  # 3:00 on weekdays
```

`time:cron=` takes a five-field cron expression (`*` / `*/n` / `a` / `a-b` / `a-b/n` / `a,b,c`;
day-of-week 0-7 with 0 and 7 both Sunday). **It contains spaces, so it must be quoted.**

If you already have a resident script, or you want "the alarm went off" in the log as well, use
**`z2-alarm`**. When the time comes one `alarm` line is appended to `events.jsonl`, so you read it
exactly like any other event.

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
Use `z2-when time:` or `z2-alarm` when it has to run with the screen off (both ask the OS to wake
the app, at the cost of firing a few minutes late). Note that `time:cron=` only **borrows the cron
syntax** — the distro's cron is not what runs it.

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

> 💡 **Copying alone is one line** (`notify:otp` does the extraction and hands it to you in `Z2_WHEN_OTP`):
>
> ```sh
> z2-when notify:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
> ```
>
> What follows is the version that **also clears the clipboard**, and at the same time a worked example of
> **parsing a log yourself**. The extraction ideas here — strip the metadata numbers first, pick by distance
> from the keyword — carry over to any other log you have to read.

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
- **The shortest form** (`sms:otp` does the extraction, and works whether or not the log is on):

  ```sh
  z2-when sms:otp run 'echo "$Z2_WHEN_OTP" | z2-clip set'
  ```

- When you also want the auto-clear: `z2-macro install otp-sms.sh` installs the SMS variant of 5-6 (reads
  `sms.jsonl`, extracts 4–8 digits). Register `sh ~/.z2term/macros/otp-sms.sh` under
  `⚙Settings → Resident servers` to copy OTPs even while locked.

OTPs that are **not SMS** (e.g. authenticator-app notifications) are out of scope for this route (use notification
detection plus the workarounds above).

### 5-8. Worked example: subscribe to feeds (how the parts fit together)

The two scripts installed by `z2-macro install rss rss-open` build "poll → keep only what is new →
notify → open in the browser → list it on the home screen" **without adding a single screen to the
app**. Read them as a worked example of how the generic parts connect.

| What it does | The part doing it |
|---|---|
| Polling | `z2-when time:every=30m run ~/.z2term/macros/rss.sh` |
| Keeping only what is new | **Subtract** `seen.txt` (`grep -Fxv`). Feed dates and ordering are not trusted |
| Telling you | `z2-notify -b Open` (a notification with a button) |
| Handling the button | `z2-when event:notify_action run '[ "$Z2_WHEN_EVENT_NAME" = rss ] && z2-open "$(head -1 ~/.z2term/rss/new.txt \| cut -f1)"'` |
| Opening it | `z2-open <URL>` |
| Browsing the list | A live-tail widget on `~/.z2term/rss/latest.txt` in **"start (head)"** mode |
| Opening the next one | A status-widget button assigned to `rss-open` (it subtracts `opened.txt`, so nothing opens twice) |

- **Both are one-shot** (5-3). **Do not register them as resident servers.**
- Parsing uses python3's standard library only (no pip). One failing feed does not stop the others,
  and broken XML is skipped silently.
- Polling costs battery, so **do not go below 30 minutes**.
- For the setup steps, see "Subscribe to feeds (RSS / Atom)" in `docs/en/HANDBOOK.md`.

**"Subtract to find what is new" is a standard trick.** When the other side's dates and ordering
cannot be trusted, remembering what you already saw and subtracting it is enough (the same idea as
`z2scan`'s baseline diff).

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
> POSIX sh script** and **the command that runs it**, satisfying the request below. Constraints:
> - **First check whether "3-A. Triggers you can register with `z2-when`" can express it.** If it can,
>   put **only the work** in the script (no watch loop) and add the `z2-when <trigger> run <path>`
>   registration command below it.
> - Only when `z2-when` has no such trigger, or the decision combines several events, use the
>   skeleton from "5-0. How to read the log" (never `tail -F` — it breaks under the prepend setting).
>   Change only `LOG`, the work-file tag, and `handle()`.
> - Branch by matching event names (`case "$rec" in *power_connected*)`) — that is format-independent.
> - Take values from `z2-state` whenever it exposes them; parse log fields only for what it doesn't.
> - Use only the trigger names, event names and fields listed in "3. Trigger reference".
> - Use only the `z2-*` actions listed in "4. Action reference" (never invent features).
> - When using `Z2_WHEN_*`, wrap `z2-when ... run '...'` in **single quotes** so it is not expanded
>   at registration time.
> - Prefer `jq` for JSON parsing and also include a sed fallback for when it's missing.
> - Add comments for any dependency install (jq, …).
> - Line 2 of the script is a one-line description; the next line is `# z2-run: <how to run it>`
>   (say "register `sh <path>` under Resident servers" only if it really is resident — never make a
>   one-shot script resident).
> - Keep it to one self-contained file with a short comment on each branch.
>
> What I want: "__describe it in natural language__" (e.g. when charging starts set volume to 30% and
> say "charging", when unplugged restore volume to 70%).

The trick is to explicitly say **stay within this guide** so the AI won't reach for features that don't exist.

---

## 8. Troubleshooting

- **A `z2-when` rule never runs** → (1) `z2-when list` — is it registered, is it `off`? (2) `z2-when fired` —
  does it say `paused`? (`z2-when resume`) (3) does the trigger's **prerequisite** hold (the "Needs" column in
  3-A)? `charge:` / `battery:` / `wifi:` / `sensor:` / `file:` / `event:` all need "System event detection" on.
  (4) 📜 → Automation tab → **▶** to run it once, which separates "the trigger never arrived" from
  "the script fails".
- **It fires but nothing happens** → `z2-when log <id>` holds the command's output and errors. If you registered
  `Z2_WHEN_*` inside **double quotes**, it was expanded at registration time and stored empty (→ section 4).
- **It only runs once even though the trigger keeps coming** → **the same rule will not fire twice within 10 seconds**.
- **`file:new=` misses files** → it only works while detection is on, and fires **after the write finishes**
  (so nothing during a copy, and hidden files never count).
- **An installed macro runs forever** → did you register a one-shot script as a resident server? (→ 5-3)
- **events.jsonl doesn't grow** → is "System event detection" on in ⚙ Settings? An ongoing notification shows while active.
- **`ssid` is blank** → reading the SSID needs location permission (v1 doesn't request it, so it can be blank). Connect/disconnect detection still works.
- **`z2-*: cannot write request (storage perm?)`** → check the app's storage permission.
- **`z2-media` does nothing** → there must be a recently-playing media app (it only sends the key).
- **`z2-torch` errors** → devices without a flash can't use it.
- **Resident macro dies** → exclude the app from battery optimization and check the resident-server settings. With low-power mode on, reactions can lag while the screen is off.
