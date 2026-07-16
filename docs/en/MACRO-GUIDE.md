# z2term Macro Guide (z2term-macro)

**How to build phone-automation "macros" using nothing but the z2term terminal.**
It is a manual you can read and write by hand, and at the same time a **machine-readable
reference you can feed whole to an AI** — then just say "I want to …" and it generates the macro.

> Target version: 0.8.154-alpha and later / 日本語版: `docs/ja/MACRO-GUIDE.md`
> Everything here is **non-root, fully local, no external transmission**. No hard-permission features are included.

---

## 1. The model (three stages)

z2term macros follow the same "**trigger → decide → action**" shape as MacroDroid and friends.

| Stage | Direction | In z2term |
|---|---|---|
| **Trigger** | Android → shell | System events appended to `~/.z2term/events.jsonl` (charge/screen/lock/Wi‑Fi/headset/airplane/ringer…). Notifications go to `~/.z2term/notifications.jsonl` |
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

---

## 3. Trigger reference (events.jsonl)

- Location: `~/.z2term/events.jsonl` (one JSON per line, append-only).
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
| `airplane_on` / `airplane_off` | Airplane mode on / off | — |
| `ringer_normal` / `ringer_vibrate` / `ringer_silent` | Ringer mode change | — |

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
| `z2-notify` | `z2-notify "title" "text"` / `z2-notify "text"` | Post a notification | — |
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

### 5-1. Minimal: watch events and react

Follow events.jsonl with `tail -F` and branch per line (`-n0` = don't replay history, watch from now).

```sh
#!/bin/sh
# ~/.z2term/macros/watch.sh
tail -n0 -F ~/.z2term/events.jsonl 2>/dev/null | while IFS= read -r line; do
  ev=$(printf '%s' "$line" | jq -r '.event' 2>/dev/null)   # no jq? see section 6
  case "$ev" in
    power_connected)   z2-say "Charging started" ;;
    headset_plugged)   z2-media play ;;
    headset_unplugged) z2-media pause ;;
    screen_off)        : # example: do nothing
  esac
done
```

### 5-2. Use the fields (battery level, SSID)

```sh
tail -n0 -F ~/.z2term/events.jsonl | while IFS= read -r line; do
  ev=$(printf '%s' "$line"    | jq -r '.event')
  level=$(printf '%s' "$line" | jq -r '.level // empty')
  ssid=$(printf '%s' "$line"  | jq -r '.ssid  // empty')
  if [ "$ev" = "battery_low" ]; then
    z2-notify "Battery" "Only ${level}% left"
  fi
  if [ "$ev" = "wifi_connected" ] && [ "$ssid" = "home" ]; then
    z2-volume 60% ; z2-toast "Home Wi‑Fi: volume restored"
  fi
done
```

### 5-3. Make it resident

Register a **start command** in `⚙ Settings → Resident servers` (e.g. `sh ~/.z2term/macros/watch.sh`).
Turn on "auto-start on boot" and it runs without opening the app and after a reboot. For a quick
test you can just run `sh ~/.z2term/macros/watch.sh &` in the terminal.

### 5-4. Time / recurring (no trigger needed)

For "every morning at 7" and the like, skip events and use cron. In `crontab -e`, add
`0 7 * * * z2-say "Good morning. Check today's schedule"` (installing cron depends on the distro).

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
> - The trigger must watch `~/.z2term/events.jsonl` with `tail -n0 -F` (don't replay history).
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
