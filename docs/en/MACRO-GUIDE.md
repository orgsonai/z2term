# z2term Macro Guide (z2term-macro)

**How to build phone-automation "macros" using nothing but the z2term terminal.**
It is a manual you can read and write by hand, and at the same time a **machine-readable
reference you can feed whole to an AI** — then just say "I want to …" and it generates the macro.

> Target version: 0.8.287-alpha and later / 日本語版: `docs/ja/MACRO-GUIDE.md`
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

**Try A first.** The app does the waiting, so **you run no resident script at all**. B is the escape
hatch for what A cannot express (5-5, where the decision needs the contents of the log).

⚠ **B costs battery just by waiting.** A resident script runs inside the engine (proot/z2root), where
starting a single external command costs thousands of ptrace-mediated syscalls. Polling a log every
2 seconds keeps **the engine burning a few percent of CPU with nothing happening**, and residency
also keeps the device out of Doze — visible as battery drain (measured: ~3 seconds of CPU per
minute). **Do not write in style B what style A can express.**

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
   **Rules can be named** (0.8.303: the first field of the form; from the terminal,
   `z2-when <trigger> name='<name>' run <command>`). A named rule uses that name as its heading in
   the list, so several rules on the same trigger stay apart. Empty means the trigger is the heading.
   ⚠ **A command is one line.** A rule file holds one item per line, so a newline throws away
   everything after it (since 0.8.272 newlines are folded into spaces on registration, but for
   anything long it is safer to **put it in a script file and register that path**).
3. **To keep something resident** (only for style B): ⚙ Settings → "**Resident servers**" → register
   your script's start command; it then runs without opening the app and after reboot (also turn on
   "auto-start on boot").
4. Handy tool: install `jq` (JSON parsing). e.g. Alpine `apk add jq` / Debian-family `apt install jq`.
5. **If you would rather not start from a blank file**: `z2-macro list` shows the 10 bundled samples and
   `z2-macro install <name>` copies one into `~/.z2term/macros/` (`z2-macro install all` for every one).
   ⚠ To keep your edits, **`install` never overwrites**. An app update that fixes a sample therefore does
   not reach a copy you already have: when `z2-macro list` marks one `differs`, read `z2-macro diff <name>`
   (yours on the left) and then, if the bundled one really is the one you want, `z2-macro install -f <name>`.
   ⚠ **`differs` is not "out of date"** — your copy can be the one that is ahead, so never run `-f` unseen.
   ⚠ **The macro directory is on PATH** (since 0.8.287; appended, so it never shadows an OS command),
   so what you install runs by name: `remind.sh 30m pills`. ⚠ **Tabs opened before 0.8.287 carry the old
   PATH** — open a new tab or run `export PATH=$HOME/.z2term/macros:$PATH`. ⚠ **An ssh session is
   different**: it does not even have `/usr/local/bin`, so `z2-*` needs a full path there.
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
| `time:every=Nm` / `time:every=Nh` / `time:every=Ns` | Every N minutes / hours / seconds (1 minute floor; anything shorter is rounded up) | — |
| `time:cron='min hour dom month dow'` | A cron expression (dow 0-7 / 0,7 = Sunday). It has spaces, so **quote it** | — |
| `wifi:connect` / `wifi:disconnect` / `wifi:ssid=<name>` | Wi‑Fi connected / disconnected / joined that SSID | detection ON |
| `net:online` / `net:offline` | a usable connection appeared / went away (mobile counts too) | detection ON |
| `net:wifi` / `net:mobile` / `net:ethernet` | the link in use **switched to that one** | detection ON |
| `share:any` / `share:text` / `share:file` | something was shared to z2term from another app | — |
| `share:contains=<part>` / `share:ext=<ext>` | filter on the shared text / the file extension | — |
| `boot` | the device finished starting up (no `:`) | — |
| `sms:any` / `sms:from=<substr>` / `sms:contains=<substr>` / `sms:otp` | An SMS arrived | SMS detection |
| `sensor:shake` / `sensor:light>N` / `sensor:light<N` / `sensor:proximity=near\|far` | Shaken / light crossed N lux / proximity changed | detection ON |
| `file:new=<dir>[,ext=<ext>]` | **A new file landed in that folder** (after the write finishes) | detection ON |
| `notify:any` / `notify:otp` / `notify:pkg=<part>` / `notify:title=<part>` / `notify:contains=<part>` | A notification arrived (`pkg=` matches the package name *or* the app label) | notification access |
| `notify:category=<kind>` | A notification of that **category** arrived (`call` = ringing / `missed_call` / `msg` `email` `alarm` `event` `progress` … — Android's own vocabulary). ⚠ This one matches **exactly** (a partial match would make `call` fire on `missed_call`, so the two could not be told apart). Lets you catch calls without knowing the phone app's package name | notification access |
| `event:<name>` / `event:<prefix>*` / `event:*` | Any device event, **by name** (same names as 3-B; `z2-when events` lists them) | depends on the name |

⚠ **A misspelled trigger will not register** (0.8.265). If it did, you would get a rule that sits in the list and never runs, with no way to find out why. Only `event:` names are left unchecked (the list from `z2-when events` is the source of truth).

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
| `Z2_WHEN_NOTI_PKG` / `_APP` / `_TITLE` / `_TEXT` / `_CATEGORY` | Package / app label / title / body / category (`notify:`) |
| `Z2_WHEN_EVENT` | Event name (`event:`) |
| `Z2_WHEN_EVENT_NAME` | The identifier you armed it with (`event:alarm` / `event:notify_action`) |
| `Z2_WHEN_ACTION` | The button label that was pressed (`event:notify_action`) |

#### ⚠ The shape of what `share:` hands you (get this wrong and it always fails)

For `share:file`, `Z2_WHEN_SHARE` is **not a path — it is a string meant to be pasted into a shell**.
`[ -f "$Z2_WHEN_SHARE" ]` is **always false**, and nothing about it shows up in `z2-when log`
(the `else` branch just runs, quietly). Three things to know:

- Files are taken into `~/z2term-inbox/`, and **`~` is left unexpanded**. Turn it into `$HOME/`
  before touching it.
- A name containing spaces arrives as `"$HOME/z2term-inbox/foo bar.jpg"` — **quoted**.
- **Several files are separated by spaces.** Do not write for exactly one.

Splitting it into one path per line without `eval` (paste this as it is):

```sh
# Turn Z2_WHEN_SHARE into one path per line (~ and $HOME expanded, ready to use).
split_paths() {
  printf '%s' "$1" | awk -v home="$HOME" '
    function emit(p) {
      if (length(p) == 0) return
      sub(/^~\//, home "/", p)        # expand ~/...
      sub(/^\$HOME\//, home "/", p)   # and "$HOME/..." the same way
      print p
    }
    {
      s = $0
      while (length(s) > 0) {
        if (substr(s, 1, 1) == " ") { s = substr(s, 2); continue }
        if (substr(s, 1, 1) == "\"") {           # one quoted entry
          e = index(substr(s, 2), "\"")
          emit(substr(s, 2, e - 1)); s = substr(s, e + 2)
        } else {                                  # one bare entry
          e = index(s, " ")
          if (e == 0) { emit(s); s = "" } else { emit(substr(s, 1, e - 1)); s = substr(s, e) }
        }
      }
    }'
}

split_paths "$Z2_WHEN_SHARE" | while IFS= read -r p; do
  [ -f "$p" ] || continue
  echo "got: $p"
done
```

For `share:text` it is just the text, so you can use it directly (`Z2_WHEN_SHARE_KIND` tells you
which of the two you got).

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

> This table is a summary. **Add `--help` to any command for the long explanation** (e.g. `z2-tile --help`, since 0.8.331).
> The ones that take subcommands (`z2-tile` / `z2-icon` / `z2-when` / `z2-session`) also answer to `-h` and `help`.
> ⚠ The ones that take a sentence (`z2-notify` / `z2-toast` / `z2-share` / `z2-open` / `z2-say` / `z2-ask`) take
> **`--help` only** — `z2-toast help` has to keep showing "help".

| Command | Usage | What it does | Returns |
|---|---|---|---|
| `z2-notify` | `z2-notify [-h] [-n name] [-c text] [-b label]... "title" "text"` | Post a notification (`-h` shows a banner, `-b` adds a **reply button**, `-c` adds a **"Copy" button**) | — |
| `z2-ask` | `z2-ask [-t sec] [-H hint] [-d default] "question"` | **Ask via a notification reply field** (answerable without opening the app). Dismissing or timing out exits non-zero | the answer |
| `z2-toast` | `z2-toast "message"` | Short on-screen message | — |
| `z2-say` | `z2-say "text to speak"` (stdin if no arg) | Speak via TTS | — |
| `z2-torch` | `z2-torch on\|off\|toggle` (default toggle) | Flashlight | `on`/`off` |
| `z2-vibrate` | `z2-vibrate [ms]` (default 200) | Vibrate | — |
| `z2-media` | `z2-media playpause\|play\|pause\|next\|previous\|stop` (default playpause) | Send a media key | — |
| `z2-volume` | `z2-volume up\|down\|mute\|unmute\|N\|N%` | Media volume | `current/max` |
| `z2-sensor` | `z2-sensor light\|accel\|proximity` | Read a sensor once | light`{"lux":F}` / proximity`{"distance":F}` / accel`{"x":F,"y":F,"z":F}` |
| `z2-clip` | `z2-clip get` / `z2-clip set [text]` | Clipboard get/set. ⚠ **Writable only while z2term is in front** (or is the input method in use) — an Android 10+ rule; from a macro running in the background it is dropped silently, so use the `z2-notify -c` copy button there (0.8.335) | content on get |
| `z2-battery` | `z2-battery` | Battery state | `{"level":N,"charging":bool}` |
| `z2-share` | `z2-share "text"` | Share sheet | — |
| `z2-open` | `z2-open <URL\|path>` | Open in the default app | — |
| `z2-intent` | see below | Fire an arbitrary Intent | — |
| `z2-state` | `z2-state [key]` | **Current device state** (see below) | JSON, or the raw value for a key |
| `z2-screen` | `z2-screen keepon 1h` / `keepon off` / `status` | **Hold off the automatic screen timeout, with a deadline** (below) | state JSON |
| `z2-tile` | `z2-tile set <1-12> <macro\|command>` etc. | Assign it to a **quick-settings tile** (below) | TSV of all 12 slots |
| `z2-icon` | `z2-icon pick <notify\|1-12>` etc. | Draw the **status-bar / tile icons** yourself (below) | a preview of the drawing |
| `z2-alarm` | `z2-alarm at\|daily HH:MM [name]` etc. | Set a **time trigger** (see below) | JSON of the schedule |
| `z2-when` | `z2-when <trigger> run <command>` etc. | **Register a trigger** (→ 3-A, and below) | the rule id |
| `z2-noti` | `z2-noti list` | Read **the notifications on screen right now** (read-only, see below) | TSV |
| `z2-session` | `z2-session list\|new\|send\|capture\|attach\|close` | Drive **the app's own tabs** (see below) | TSV / index |
| `z2-server` | `z2-server list\|start\|stop\|status <server>` | Start / stop **a registered resident server** (see below) | TSV |
| `z2-macro` | `z2-macro list\|install\|diff\|show\|run\|dir` | Manage the bundled samples (`list` marks each `new` / `same` / `differs`) | — |

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
z2-tile list                                     # all 12 slots (slot / label / command; '-' = empty)
z2-tile clear 2                                  # empty a slot (clear all works too)
```

- What you assign is either the **file name of a macro** in `~/.z2term/macros/` or a **command** to
  run as typed. **Which one it is comes from the name** — there is no `--macro` flag to get wrong.
  Only the **first word** decides, so **a macro can take arguments**:

  ```sh
  z2-tile set 2 'remind.sh ask'  -l remind    # macro + arguments
  z2-tile set 3 'remind.sh peek' -l list
  ```

  - Two slots running the same macro end up with **the same label** (it comes from the first word),
    so give them `-l`.
  - ⚠ A name ending in `.sh` that is **not** in `~/.z2term/macros/` is **rejected when you assign it**.
    Letting it through would make it a command, and the macro folder **is not on PATH** — the tile
    would do nothing at all, with the reason only in `~/.z2term/tile/run.log`.
  - Scripts outside the macro folder go in with a **full path** (`sh /path/to/foo.sh args`).
- **Tap to run, tap again to stop.** The tile is green while it runs (same deal as the widget buttons).
- The run gets **`Z2_TILE`** (the slot number) in its environment, plus `Z2_TILE_MACRO` for a macro,
  so the same macro can sit on several slots and branch on which one was pressed.
- ⚠ **A locked device is not waved through.** Android asks to unlock first, and the command runs only
  if it is unlocked. That is not a setting: it stops someone who picked up your phone firing a command
  straight off the shade.
- ⚠ **There are exactly 12 slots.** Tiles are declared in the manifest and cannot grow at runtime
  (raised from 4 in 0.8.294 — an unassigned slot is not listed anywhere, so spares cost nothing).
- ⚠ **You place the tiles yourself**, from the pencil/edit screen of the quick settings panel; Android
  does not let an app put its own tiles there.
- **With nothing assigned, no tile is listed at all** (0.8.271). Only the slots you assign show up in
  the quick settings list.
- The run log is `~/.z2term/tile/run.log`, kept apart from the widget's `~/.z2term/widget/run.log`.
  **When a tap seems to do nothing, look there first** — failures never reach the screen.


### `z2-icon` (put your own drawing on the icons)

The notification icon in the status bar and the quick-settings tile icons can be replaced with
**a drawing of your own** (0.8.294). Tiles get **one drawing per slot**. The grid is
**24 / 48 / 64** across (0.8.379; 24 unless you change it).

```sh
z2-icon pick 1                 # choose one of the built-in drawings for slot 1
z2-icon sample                 # list the built-in drawings (14 of them)
z2-icon sample bell            # print one of them
z2-icon sample notify bell     # put the bell on the notification icon
z2-icon edit 1                 # draw it in $EDITOR (saving applies it)
z2-icon show 1                 # print the current drawing
z2-icon clear notify           # back to the built-in icon (clear all works too)
z2-icon list                   # which ones you have changed (4th column: its grid)
z2-icon grid 64                # draw new ones on a 64x64 grid from now on
z2-icon scale 1 64             # lay slot 1 out on a 64x64 grid (it looks the same)
```

The targets are **`notify`** (one drawing for every notification this app puts out) and
**slots `1`-`12`**.

A drawing is a grid of characters. `.` `(space)` `0` `-` `_` leave a cell empty and **anything else
fills it in**, so draw with whichever character you find easiest to see. Blank space around the
drawing is ignored and it gets centred, so you need not fill every line exactly.

**When a tile looks like a staircase, raise the grid** (0.8.379). The status bar shows these about
24px across, so 24 dots are plenty there — but **a tile is drawn much larger**, and there 24 dots
show as steps.

```sh
z2-icon grid 64                # the grid new drawings are made on (24 / 48 / 64; 24 by default)
z2-icon grid                   # what it is now
z2-icon scale 1 64             # lay the drawing on slot 1 out on a 64x64 grid
```

`scale` **does not change how it looks** (the drawing keeps the same share of the grid). All it
adds is room to draw finer, so run it and then round off the corners with `z2-icon edit`.
⚠ Laying a drawing out on a smaller grid **drops thin lines** (there is no way back).
⚠ **A bigger grid does not make a bigger icon.** Fill the grid, or the drawing comes out smaller
than the one it replaced.

```sh
cat > /tmp/dot.txt <<'EOF'
....##....
...####...
..######..
.########.
....##....
....##....
EOF
z2-icon set 2 /tmp/dot.txt     # from a file
printf '..##..\n.####.\n..##..\n' | z2-icon set 3 -   # from stdin
```

- ⚠ **There is no colour.** Android **repaints these icons in a single colour of its own** (tiles
  change colour between on and off), so the only thing you decide is **the shape**.
- ⚠ **The status bar shows them about 24px across.** There, detail finer than 24 dots is lost —
  treat what `show` prints as what will appear (a 48 or 64 drawing is printed with two cells
  folded into one character, so the line does not wrap on a phone screen).
- ⚠ **A drawing that is too big is refused**, rather than quietly clipped: clipping would deliver an
  icon with its edges missing to the one person who cannot tell why.
- ⚠ **Three things cannot be changed** (Android fixes them at install time): the icon in the
  quick-settings **edit** screen (where you drag the tile from), the **file-picker root icon**, and
  the **launcher icon**. Placed tiles and posted notifications do change.
- **A tile picks its own drawing.** When you put something on a slot with `z2-tile set`, a
  matching drawing is filled in wherever the name gives it away (`remind.sh` gets a clock,
  `battery-alert.sh` a battery, `z2-screen keepon` a moon). Every bundled macro matches one.
  - ⚠ **A drawing you set is never lost.** Only "no drawing yet" and "filled in here before"
    are touched; once you set one with `z2-icon`, that slot is left alone.
  - **To go back to automatic, `z2-icon auto <slot>`** (`all` for every slot). That one **does
    overwrite your own drawing** and re-picks. `z2-icon list` shows which is which
    (`auto` = picked for you, `custom` = you set it, `-` = still the built-in icon).
  - Names that give nothing away get nothing (they keep the built-in icon).
- The built-in drawings are **just text** as well: `pick` one, then open it with `z2-icon edit` and
  rework it into your own.
- **A drawing of your own can be named and kept in the list** (0.8.300).

  ```sh
  z2-icon edit 1                 # draw your own on slot 1
  z2-icon save 1 my-face         # name it and add it to the list
  z2-icon sample 5 my-face       # put the same drawing on slot 5
  z2-icon sample                 # the list (builtin = shipped / mine = yours)
  z2-icon forget my-face         # drop it from the list (what is on a slot stays)
  ```

  A drawing in the list can be chosen by number or by name **exactly like a shipped one**
  (it appears in `pick` too). ⚠ **Names cannot contain spaces** (the list is TSV, so a space
  would shift the columns). ⚠ **A name cannot be digits only** either — the list is also
  chosen by number, so a drawing called `3` could not be told apart from "number 3".
- **`z2-icon list` tells you which drawing is on which slot** (0.8.300 prints **the name**;
  before that it only said `custom`, which was no help once several slots were in use).
  When the name does not bring the shape to mind, **`z2-icon list -p`** prints the drawings.

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
- **Whether it fires on the minute is in `z2-alarm list` as `exact`** (0.8.333).
  - `"exact":true` … it fires on the minute.
  - `"exact":false` … in Doze the OS only offers a slot every 9-15 minutes, so **a phone left with
    the screen off can be that late**. Battery saver pushes it the same way.
  - What flips it to `true` is **Settings › Apps › Z2Term › Battery › Unrestricted** (battery
    optimisation off). ⚠ The app **never asks for the "alarms & reminders" permission** — Android
    grants exact alarms to apps exempt from battery optimisation, so no extra permission is needed.
    Where it is not exempt, scheduling quietly falls back to the inexact API; nothing is dropped.
- The same applies to `z2-when time:` and the deadline of `z2-screen keepon` (one place decides).
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
z2-when notify:otp run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'
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

# Stay connected (0.8.366). After this you just type into that tab as usual.
z2-session attach 2                  # leave with ~. at the start of a line (as in ssh)
```

- Marks in `list`: `*` = the tab on screen / `!` = something is running / `?` = not started yet / `-` = other.
- `<tab>` can be **the index, an id, or a tab name**; `.` or omitted means the tab on screen.
- **`send` only types — it does not run anything.** Add `--enter` when you do want it executed
  (so nothing starts running behind your back).
- A name given with `new <name>` is **pinned**: neither the distro name nor the shell's title overwrites it.
- **Keys go through `z2-session key`** (0.8.311). ⚠ `send` is a paste, so passing `\x03` to it
  **does not make Ctrl+C** — you just get the characters `^C`.

```sh
z2-session key 2 C-c              # Ctrl+C (interrupt what is running)
z2-session key 2 M-x              # Alt+x
z2-session key 2 F5 Up Home       # specials, several at a time
z2-session key 2 --raw '\x1b[A'   # anything else, as bytes (\xHH \e \n \r \t \0)
```

  - Modifiers are `C-` (Ctrl) and `M-` (Meta = Alt); they stack, as in `C-M-a`.
  - Specials: `Up` `Down` `Left` `Right` `Home` `End` `PgUp` `PgDn` `Ins` `Del` `Tab` `S-Tab`
    `Enter` `Esc` `Space` `BS` `F1`–`F12` (case does not matter).
  - ⚠ **Shift-ed keys such as `C-S-a` are refused.** A terminal cannot tell Shift apart, so it would
    be exactly the same as `C-a`. ⚠ Sending it silently would leave "I sent it and nothing happened"
    unexplainable, so it stops and tells you what to write instead (`S-Tab` does go through — that
    one the terminal can distinguish).
  - ⚠ One bad name and **nothing at all is sent** (no half-delivered burst of keys).


### `z2-server` (start / stop a resident server)

Start or stop a server you registered in the app (📜 → Servers) from the terminal or from a rule.

```sh
z2-server list                 # registered servers (index / id / state / mark / name, TSV)
z2-server start sshd           # run that one as a resident server
z2-server stop sshd            # stop just that one (the others keep running)
z2-server status sshd          # state, pid, restarts, last exit code
```

⚠ **This exists to fix "the server my rule started is unreachable".**
A daemon started straight from a rule (`sshd --lan`, say) runs **outside the resident-server frame**.
Outside it nothing keeps the device awake — no WakeLock, no WifiLock, no foreground service — so
**once the screen goes off the radio and the CPU sleep and it stops answering**. Starting it through
`z2-server start` puts it inside that frame.

```sh
z2-when wifi:connect    run 'z2-server start sshd'
z2-when wifi:disconnect run 'z2-server stop sshd'
```

- `<server>` can be **the index from `list`, an id, or the name you gave it in the app**.
  ⚠ If a name matches more than one, it refuses rather than guessing (use the id).
- Only **registered** servers can be started (this never registers a new one — do that in the app).
- Starting and stopping also **persists as the enabled/disabled state** (the same switch as in the app).
- ⚠ **With low-power mode on, no locks are taken even after a start** (that setting chose battery on
  purpose, so this is correct). `z2-server start` says so when it happens. The setting lives under
  ⚙ Settings → Automation → Background process protection.
- ⚠ Stopping the last one **does not tear the residency down** (that would take a standing tunnel with
  it). Use [Stop] on the servers tab to stop everything.

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

### 5-Z. The environment a macro runs in (know this first)

A script started by `z2-when` runs like this (measured):

```
SHELL=/bin/sh   HOME=/root   TERM=xterm-256color
LANG=C.UTF-8 (and every LC_*, so cut -c and friends count characters, not bytes)
PATH=…:/root/.z2term/macros:…   ← the macro folder is on PATH
TZ=<+09>-9                      ← the device's time zone (since 0.8.302)
```

- **`date` agrees with the device clock** (0.8.302). ⚠ **Before that it was always UTC**,
  so while relative delays (`in 30m`) were fine, **anything working with a wall-clock time
  like `18:30` was off by the whole zone offset**. Re-create anything you scheduled with an
  older build.
- `TZ` holds a **POSIX specification rather than a zone name** (`<+09>-9`), so that it works
  the same on a distro without `tzdata`. Where daylight saving applies, the switch-over rules
  are included too: `<-05>5<-04>4,M3.2.0/2,M11.1.0/2`.

- **Anything you push into the background outlives the script.** A child started with `&` or
  `nohup` is not killed when the macro that started it exits (this is what `sshd --lan` needs).
  ⚠ That means **a slow download belongs in the background**: waiting for it in the foreground
  blocks every share or event for as long as it takes.
- **Bundled macros can be called as building blocks.** From your own macro,
  `sh ~/.z2term/macros/remind.sh tomorrow 09:00 meeting` saves you from rewriting the time
  handling and the notification (see 5-9).
- **`~/.z2term/macros/` is on `PATH`** (since 0.8.287), so anything in there can be called by name
  (`remind.sh …`). ⚠ Scripts **outside** that folder need a full path.

⚠ **`at` / `atd` / `systemd` timers do not work here.** The distro has no PID 1, so `systemd` never
starts, and `atd` is not installed. Writing "once at this time" with `at`, the way you would on any
other Linux, means **nothing happens and no error is printed**. `cron` runs but is stopped by Doze.
**For anything time-based use `z2-when time:` or `z2-alarm`, and nothing else.**

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
z2-when notify:otp    run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'
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

⚠ **Do not shorten `POLL`.** Every pass starts a `sleep` and a `wc`, and inside the engine those two
alone cost thousands of ptrace-mediated syscalls. At a 2-second interval that **burns a few percent
of CPU while nothing is happening**, which is why the default is now 15 seconds (0.8.273; it used to
be 2). If you need second-level reactions, check first whether `z2-when` already has that trigger.

```sh
#!/bin/sh
# The shared skeleton. Change LOG, the work-file tag, and handle().
POLL=15                                   # how often to poll the log (seconds) = the longest a reaction can lag. Do NOT shorten (below)
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
  ⚠ Every bundled sample **moved onto `z2-when` in 0.8.338**, so this shape (polling the log
  yourself) is for triggers `z2-when` does not have.
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
| **Resident** (keeps running) | Register a start command in `⚙ Settings → Resident servers` (e.g. `sh ~/.z2term/macros/watch.sh`). Turn on "auto-start on boot" and it runs without opening the app and after a reboot | Anything built on the 5-0 skeleton (it has a watch loop; no bundled sample does) |
| **One-shot** (runs once and exits) | Register it with `z2-when` / assign it to a widget button / run it by hand | Anything shaped like 5-A ("just the work") |

⚠ **Never register a one-shot script as a resident server.** The supervisor treats "it exited" as
"it died" and restarts it, so it **runs again every time it finishes** (a feed reader would fetch
forever).

⚠ **Residency costs battery even when nothing happens.** With even one resident server, the app
holds a WakeLock and a WifiLock and keeps the device out of Doze. On top of that, every `sleep` and
`wc` a watch loop starts costs thousands of ptrace-mediated syscalls inside the engine
(proot/z2root). On a device running a single 2-second watcher the measurement was **3 seconds of CPU
per minute (~5% continuously)**. **Always ask first whether a `z2-when` trigger can express it** —
if it can, you need no resident script at all (0.8.273 moved the battery, daily-report and OTP
samples onto `z2-when`, and 0.8.338 the starter one, so **no bundled sample is resident**). Residency is genuinely needed only when you are picking
up something `z2-when` does not have (the 5-0 skeleton).

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

### 5-6. Worked example: auto-copy a one-time code and clear it

Copy the **one-time code (OTP)** out of a notification or an SMS into the clipboard, then clear it
after a while — but only if the clipboard still holds that same value.

**The app does the extraction for you.** `notify:otp` / `sms:otp` pull the code out of the body and
hand it over in `$Z2_WHEN_OTP`, so all you write is "put it in" and "take it out".
**No resident script is involved** (`z2-macro install otp-clip` / `otp-sms` gives you the file below).

```sh
# ~/.z2term/macros/otp-clip.sh
# Setup: Settings -> "Notification detection" ON + grant OS notification access
# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh

TTL=60                                    # seconds before the copy is cleared (direct writes only)

# The app already extracted the code. Do nothing when it could not.
code=$Z2_WHEN_OTP
[ -n "$code" ] || exit 0

# Try to put it in directly. This only lands while you are **looking at the app**:
# Android 10+ lets only the app in front write to the clipboard, so a run in the
# background is dropped silently. Read it back to see whether it landed.
z2-clip set "$code" 2>/dev/null
if [ "$(z2-clip get 2>/dev/null)" != "$code" ]; then
  # It did not land = you are not looking at the app. Hand it over through the
  # notification's "Copy" button instead (pressing it brings the app to the front).
  # ⚠ What goes in that way is not cleared by TTL below: clearing also needs the front.
  z2-notify -h -c "$code" "One-time code" "$code"
  exit 0
fi
z2-toast "Copied code: ${code}"

# After TTL, clear the clipboard only if it still holds the code we copied
# (anything copied since then is left alone).
sleep "$TTL"
[ "$(z2-clip get 2>/dev/null)" = "$code" ] || exit 0
z2-clip set ""
z2-toast "Cleared the copied code"
```

Registering it is one line.

```sh
z2-when notify:otp run ~/.z2term/macros/otp-clip.sh
```

⚠ **A macro running in the background cannot stop at `z2-clip set`** (0.8.335): since Android 10,
**an app that is not in front cannot write the clipboard**. Codes almost always arrive while you
are looking at some other app, so always add the `z2-notify -c` copy button (pressing it brings
z2term to the front for that instant, so it always lands). ⚠ While **z2term is the input method
(IME) you are using on this device**, the OS makes an exception and background writes do work —
the read-back above absorbs that difference too.

> 💡 **If you do not need the auto-clear, the whole thing is one line** (a direct `z2-clip set`
> only lands while you are looking at the app, so keep the button when it matters).
>
> ```sh
> z2-when notify:otp run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'
> ```

⚠ **On Android 15+ a code delivered by notification may be redacted** (sensitive-notification
protection; other automation apps hit the same wall). When the code arrives by SMS, use
**`sms:otp` (5-7)** — it reads the body directly, so neither redaction nor the lock screen matters.

⚠ The script stays alive during the `sleep` (a run started by `z2-when` keeps going until it
finishes, as long as the app is alive). Keep TTL short — a TTL of hours leaves a process sitting
around for hours.

**When you do want to parse the body yourself** (a trigger `z2-when` does not have, an unusual code
format, …), you end up reading `notifications.jsonl` / `sms.jsonl` with the 5-0 skeleton. The parts
that actually matter there:

- **Delete the digits that only look like codes, first** — dates (`2026-07-29T01:23`), clock times
  (`01:23`), anything 9+ digits (epochs), tokens containing `|` (notification ids), dotted
  identifiers (package names). Only then take what is 4–8 digits long.
- **Choose by position** — not "the first number found". Prefer what follows a keyword
  (`code`, `otp`, `verification`, …), and fall back to the nearest number *before* it. Matching
  from the start picks up the notification id embedded in a template.
- **Squeeze `-` out** — `123-456` has to become `123456` before you count digits.


### 5-7. Copy an SMS one-time code reliably via "SMS detection" (bypasses redaction)

On Android 15+ an **SMS OTP delivered as a notification can be redacted**, so notification detection
never sees it (the same is true of other automation apps' notification triggers). That is why z2term
has "SMS detection", which **reads the SMS body itself** — redaction and the lock screen do not apply.

- Setup: `⚙ Settings → SMS detection` **ON**, and allow receiving SMS in the dialog that appears
- Recorded to: `~/.z2term/sms.jsonl` (fields: `ts` `time` `from` `body`)
- **Shortest form** (`sms:otp` does the extraction; it works whether or not logging is on):

  ```sh
  z2-when sms:otp run 'z2-notify -h -c "$Z2_WHEN_OTP" "One-time code" "$Z2_WHEN_OTP"'
  ```

  ⚠ `z2-clip set` is not called directly here because **an app that is not in front cannot write
  the clipboard** (Android 10+). An SMS almost always arrives while you are doing something else,
  so the code is handed over through the `-c` copy button (see 5-6).

- **When you want the auto-clear too**, use the same file as 5-6 and just swap the trigger.
  `z2-macro install otp-sms` puts it in place, so registering it is all that is left:

  ```sh
  z2-when sms:otp run ~/.z2term/macros/otp-sms.sh
  ```

  ⚠ **Do not register it as a resident server** (which is what this guide said up to 0.8.273).
  `sms:otp` does the waiting, so residency buys nothing and **costs battery while idle** (→ 5-3).

An OTP that is **not** an SMS (an authenticator app's notification, say) is outside this path — that
is what `notify:otp` in 5-6 is for.


### 5-8. Worked example: subscribe to feeds (how the parts fit together)

The two scripts installed by `z2-macro install rss rss-open` build "poll → keep only what is new →
notify → open in the browser → list it on the home screen" **without adding a single screen to the
app**. Read them as a worked example of how the generic parts connect.

| What it does | The part doing it |
|---|---|
| Polling | `z2-when time:every=30m run ~/.z2term/macros/rss.sh` |
| Keeping only what is new | **Subtract** `seen.txt` (`grep -Fxv`). Feed dates and ordering are not trusted |
| Telling you | `z2-notify -b Open` (a notification with a button) |
| Not missing one | One word per line in `~/.z2term/rss/important.txt`; each match gets **its own notification** (0.8.334 — the summary body only carries 3 lines, so a busy feed pushes it out) |
| Handling the button | `z2-when event:notify_action run 'case "$Z2_WHEN_EVENT_NAME" in rss:*) z2-open "${Z2_WHEN_EVENT_NAME#rss:}" ;; esac'` (the URL rides in the notification's name, so the article you pressed opens) |
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


### 5-9. Worked example: remind yourself with a notification (one-shot vs repeating)

The single script `z2-macro install remind` brings in is **a reminder that fires with the app closed**.
The point is that one-shot and repeating reminders live in different places; the same split applies
to plenty of other jobs.

```sh
sh ~/.z2term/macros/remind.sh setup    # once, up front (registers the hooks and the tiles)

remind.sh 30m take pills               # once, 30 minutes from now
remind.sh 18:30 take out the bins      # once, at the next 18:30
remind.sh daily 07:00 weigh in         # every day
remind.sh weekday 09:00 standup        # Mon-Fri
remind.sh weekly tue 20:00 recycling   # that weekday only
remind.sh list / remind.sh del 2       # list / cancel
```

| What it does | Which parts |
|---|---|
| One-shot | `z2-alarm in 30m r<id>` / `z2-alarm at 18:30 r<id>` (a booking that disappears once it fires) |
| Repeating | `z2-when time:daily=07:00` / `time:cron='0 20 * * 2'` (a rule that stays) |
| Fire it | `z2-notify -h -n r<id> -b Done -b +10min -b +1h` |

**You can call this macro from your own.** Neither the time handling nor the notification has to be
written again, so "something happened, put a reminder in" becomes one line.

```sh
# e.g. set a reminder from text that was shared to you (inside your own macro)
sh ~/.z2term/macros/remind.sh tomorrow 09:00 "meeting"
sh ~/.z2term/macros/remind.sh 30m "laundry"
```

⚠ **Pass the parts separately** (`tomorrow 09:00 meeting`): a whole natural sentence is not parsed.
If you are feeding it sentences (from a share, say), ⚠ **do not turn text without a time into a
reminder** — a note silently becoming an appointment is worse than no reminder at all.
| The button reply | `z2-when event:notify_action` (`Z2_WHEN_EVENT_NAME` gives back the `-n` name) |
| Catch the alarm | `z2-when event:alarm` — **one rule, permanently**, no matter how many reminders you add |
| Add one without opening the app | a quick-settings tile → `z2-ask` (asks "what?" and "when?" in a notification reply box) |

- **Why not put one-shots in `z2-when`**: `time:at=` disables itself after firing, but **the rule
  itself stays**, so dead rules pile up in the automation tab. A `z2-alarm` booking is gone once it rings.
- **Why not do repeating ones with `z2-alarm`**: `z2-alarm` only writes an `alarm` line into
  `events.jsonl`, so something has to pick it up. `z2-when time:` runs **your command directly** (→ 3-A).
- **The text lives in a file; the notification name only carries an id** (`~/.z2term/remind/<id>.txt`).
  Put the text in the name and the matching breaks the moment it contains a space or an emoji.
- **More ways to write it** (0.8.286): `monthly 15 09:00` / `yearly 07/30 19:00` map onto the **day and
  month fields of cron** (`0 9 15 * *` / `0 19 30 7 *`). One-shot `07/30 19:00` / `2030 07/30 19:00` /
  `203007301900` / `07301900` are turned into a day difference and then a delay. ⚠ Leap years mean the
  civil-date-to-day-number conversion is done here (`days_from_civil`); `date -d "2026-07-30"` is not
  dependable on busybox. ⚠ Only the **day difference** is used, with the time left to `day_epoch`, which
  cancels out the timezone. ⚠ **`day_epoch` reads `date` only once and derives H:M:S from it** (0.8.289):
  reading `now` and the hour/minute/second from separate `date` calls could straddle a second boundary
  under load and round **18:30 down to 18:29** (surfaced on CI). ⚠ A writable-but-nonexistent date (`2/30`) rolls over into the next month,
  so it is converted back and checked.
- **The short "every" form** (0.8.286): `every 19:00` -> daily, `every wed 19:00` -> weekly,
  `every 15 19:00` -> monthly, `every 07/30 19:00` -> yearly. ⚠ Only **the shape of the next word**
  decides. The word count does not change (one word swapped for another), so the `USED` argument count
  still holds.
- **Removing from the list tile** (0.8.286): `peek` carries a single **[Delete]** button; pressing it
  asks for a number with `z2-ask` and hands it to `del`. ⚠ **Keep the numbers in the list** (`peek` used
  to strip them) — they are what you point at. ⚠ Pressing a button closes the original notification, so
  the list is posted again before asking.
- **Day-based wording is folded into a delay** (0.8.285). `tomorrow` / `Nd` cannot be expressed with
  `z2-alarm at`, which only takes "the next HH:MM" and **cannot carry a date**. Today's midnight is
  derived, the days and time added, and the result handed to `z2-alarm in <sec>s`. ⚠ `date -d "tomorrow"`
  does not exist in busybox. ⚠ A day is added as 86400s, so a DST switch day can be an hour off.
  ⚠ The list stores the **real date** (`07/31 18:30`), not "tomorrow", which would read wrong once the
  date rolls over.
- **An unreadable "when" is refused before booking** (0.8.283). Not only the format (`HH:MM`) but the
  **range** (00:00-23:59) and the delay being a whole number (`1.5h` is rejected). ⚠ Letting those
  through breaks `$((num*3600))`, or parks a reminder that never rings in the list wearing a
  "scheduled" face. The reason is returned to the caller in `WHY`.
- **The tile path (`remind.sh ask`) always answers with a notification** (0.8.283). Nobody is looking at
  a terminal there, so exiting to stderr reads as "I tapped it and nothing happened" (the reason only
  reached the tile's `run.log`). Unreadable input is asked again up to three times with the reason
  attached (the previous answer is put back via `z2-ask -d`); a successful one shows the plan and text.
- Firing can be **a few minutes late** (the booking is battery-friendly and Doze-aware). Not for
  anything that needs to be on time to the second.

### 5-10. Worked example: capture calls from numbers not in your contacts

When someone who is not in your contacts calls, put their number in a notification with a
**"Copy" button, one press away from the clipboard**. Whether you want to call back, look the number
up, or block it, you no longer have to copy it down by hand.
(`z2-macro install unknown-call` gives you exactly what is below.)

⚠ **Why it waits for a press instead of copying by itself.** Since Android 10, **an app that is not
in front cannot write the clipboard**. During a call the app in front is the phone app, so a
`z2-clip set` here is dropped by the OS — silently, which is what makes it so easy to miss:

```
E ClipboardService: Denying clipboard access to com.zerotoship.z2term,
  application is not in focus nor is it a system service for user 0
```

The "Copy" button added by `z2-notify -c <text>` **brings z2term to the front for that instant**, so
it never hits that limit (0.8.335).

**How "not in contacts" is decided.** A phone app shows the **name** for someone in your contacts and
the **bare number** for someone who is not. So if **the notification shows a number**, that caller is
not in your contacts.

⚠ This shape exists to keep **z2term free of phone-related permissions**. Checking the contacts
database directly needs `READ_CONTACTS` plus `READ_CALL_LOG` (required since Android 9 just to learn
the incoming number), and the latter is essentially undistributable unless you are the default phone
app. Reading what the notification shows gives the same answer, so **no permission is added** — the
notification access you already granted is enough.

```sh
# ~/.z2term/macros/unknown-call.sh
# Setup: Settings -> "Notification detection" ON + grant OS notification access
# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh

# Is it a bare number? Strip every character a phone number may use (digits + - ( ) space);
# if **nothing is left** and 7-15 digits remain, treat it as a number.
#   -> Letters mixed in = a name = someone in your contacts, so do nothing.
#   -> "Unknown"/"Private number" also fall out here.
# ⚠ Do not write this as case [!...]: the ) inside the pattern is read as the case separator.
is_number() {
  [ -z "$(printf '%s' "$1" | tr -d '0-9+() -')" ] || return 1
  digits=$(printf '%s' "$1" | tr -cd '0-9')
  [ ${#digits} -ge 7 ] && [ ${#digits} -le 15 ]
}

# The caller is usually the title, but some phone apps put it in the text. Check both.
num=""
for s in "$Z2_WHEN_NOTI_TITLE" "$Z2_WHEN_NOTI_TEXT"; do
  if is_number "$s"; then num="$s"; break; fi
done

# A name was shown = in contacts. Do nothing.
[ -n "$num" ] || exit 0

# The category tells which one fired.
case "$Z2_WHEN_NOTI_CATEGORY" in
  missed_call) what="Missed call" ;;
  *)           what="Incoming call" ;;
esac

# Hand the number over through the notification's "Copy" button (-c). Calling z2-clip set here
# would not land: Android 10+ only lets the app in front write to the clipboard, and during a
# call that is the phone app.
z2-notify -h -c "$num" "${what}: number not in contacts" "$num"
```

That is the whole registration:

```sh
z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh
```

**To capture missed calls too**, register a second rule with the other category (the same script is
fine).

```sh
z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh
```

⚠ **`notify:category=` is used so you do not need the phone app's package name.** `notify:pkg=` would
work too, but the phone app differs per device (Google's, the vendor's, or a dialer you installed).
Call notifications carry Android's own `call` category, so matching on that is device-independent.

⚠ **`cooldown=20s` is there on purpose.** A ringing call updates its notification several times (call
duration and so on), so without it one call runs the macro repeatedly.

⚠ **Withheld numbers are not captured** (there is no number to capture). If you want to know anyway,
add a branch that fires `z2-notify` when `is_number` fails.

⚠ The test only looks at **what is displayed**, so if the phone app shows something of its own
("Suspected spam", say), that wins and is treated as a name — nothing happens.

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
> - **Schedule only with `z2-when time:` or `z2-alarm`** (`at` / `atd` / `systemd` do not exist
>   here; `cron` is stopped by Doze).
> - **Never use `Z2_WHEN_SHARE` as a path.** Follow "3-A ⚠ The shape of what `share:` hands you":
>   turn it into `$HOME/` and **expect more than one file**.
> - Bundled macros in `~/.z2term/macros/` **may be called as building blocks**
>   (e.g. `sh ~/.z2term/macros/remind.sh tomorrow 09:00 thing`).
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
- **The battery started draining faster** → **is a resident server running?** (📜 → Servers tab). Even one
  keeps the device out of Doze, and every `sleep` and `wc` a watch loop starts costs thousands of
  ptrace-mediated syscalls inside the engine (a single 2-second watcher measured **3 seconds of CPU per
  minute**). ① Can that macro be expressed as a `z2-when` trigger? (then you need no resident script at
  all → section 1, style A) ② If residency really is needed, widen `POLL` (15 s or more) ③ `⚙ Settings → Automation →
  Background process protection → Low-power mode` stops the app from holding the WakeLock/WifiLock (at the cost of slower reactions
  while the screen is off).
- **A tile does nothing when tapped** → the reason is in `~/.z2term/tile/run.log` (failures never
  reach the screen). `command not found` means you assigned something outside the macro folder by
  name — use a full path.
- **events.jsonl doesn't grow** → is "System event detection" on in ⚙ Settings? An ongoing notification shows while active.
- **`ssid` is blank** → reading the SSID needs location permission (v1 doesn't request it, so it can be blank). Connect/disconnect detection still works.
- **`z2-*: cannot write request (storage perm?)`** → check the app's storage permission.
- **`z2-media` does nothing** → there must be a recently-playing media app (it only sends the key).
- **`z2-torch` errors** → devices without a flash can't use it.
- **Resident macro dies** → exclude the app from battery optimization and check the resident-server settings. With low-power mode on, reactions can lag while the screen is off.
