#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""z2mon - a small system monitor for the z2term Linux desktop (GUI tab).

This is the window shown in the README screenshot. It is an example script,
not part of the app. It works on any distro z2term can install
(Arch, Debian/Ubuntu, Alpine).

Install what it needs (once):

  Arch           pacman -S python python-pillow tk
  Debian/Ubuntu  apt install python3 python3-pil python3-tk
  Alpine         apk add python3 py3-pillow python3-tkinter

Run it:

  z2run python3 z2mon.py           open the GUI tab and show the monitor
  z2run python3 z2mon.py --clock   also open a small clock window
  python3 z2mon.py --png out.png   draw one frame to a PNG (no X needed)
  python3 z2mon.py --png out.png --sim   draw with made-up values (layout check)

Press Esc in the window to quit.

Inside an Android app sandbox, /proc/stat, /proc/uptime, /proc/loadavg,
/proc/net/dev, /sys/class/thermal and /sys/class/power_supply are not
readable (Permission denied). So this uses only what is always readable:
/proc/meminfo, /proc/<pid>/stat, cpufreq and statvfs. Values that cannot be
read are not shown as "0".

  - CPU LOAD is the CPU used by processes in this Linux environment only,
    not the whole phone (Android does not let apps see that).
  - BATTERY comes from z2-battery; when it is unavailable the tile shows
    the number of cores instead.
"""

import argparse
import base64
import glob
import io
import json
import math
import os
import re
import shutil
import socket
import subprocess
import sys
import time

# ---------------------------------------------------------------- dependencies

INSTALL = {
    "pacman": {"pil": "pacman -S python-pillow", "tk": "pacman -S tk",
               "font": "pacman -S ttf-dejavu"},
    "apt-get": {"pil": "apt install python3-pil", "tk": "apt install python3-tk",
                "font": "apt install fonts-dejavu-core"},
    "apk": {"pil": "apk add py3-pillow", "tk": "apk add python3-tkinter",
            "font": "apk add font-dejavu"},
}


def missing(what, label):
    """Exit with the install command for this distro."""
    for pm, cmds in INSTALL.items():
        if shutil.which(pm):
            hint = cmds[what]
            break
    else:
        hint = " / ".join("%s: %s" % (pm, c[what]) for pm, c in INSTALL.items())
    sys.exit("z2mon: %s is not installed.\n  install it with: %s" % (label, hint))


try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    missing("pil", "Pillow (Python imaging library)")

# ---------------------------------------------------------------- look

BG = (10, 13, 11)
FG = (219, 228, 221)
DIM = (111, 125, 117)
LINE = (27, 35, 30)
TRACK = (22, 29, 25)
ACCENT = (34, 197, 94)      # same accent as the site
FILLED = (15, 42, 26)       # area under the line graphs
TEAL = (94, 234, 212)
WARN = (217, 164, 65)
CRIT = (224, 82, 82)

BASE_W = 1080               # layout is done at this width and scaled to the window
HIST = 90                   # points kept in the line graphs
HZ = float(os.sysconf("SC_CLK_TCK")) if hasattr(os, "sysconf") else 100.0

# The site logo SVG (viewBox 0 0 35 5) laid out as dots
LOGO = [
    "#####.#####.#####.#####.####..#...#",
    "...#......#...#...#.....#...#.##.##",
    "..#...#####...#...####..####..#.#.#",
    ".#....#.......#...#.....#..#..#...#",
    "#####.#####...#...#####.#...#.#...#",
]

# File names are the same on every distro; only the folder differs
# (Arch: TTF/, Debian: truetype/dejavu/, Alpine: dejavu/), so search all of them.
MONO = ["DejaVuSansMono.ttf", "LiberationMono-Regular.ttf", "NotoSansMono-Regular.ttf"]
MONO_B = ["DejaVuSansMono-Bold.ttf", "LiberationMono-Bold.ttf", "NotoSansMono-Bold.ttf"]

_font_path = {}
_font_cache = {}


def _find_font(bold):
    if bold in _font_path:
        return _font_path[bold]
    path = None
    for name in (MONO_B if bold else MONO):
        hit = glob.glob("/usr/share/fonts/**/" + name, recursive=True)
        if hit:
            path = hit[0]
            break
    if path is None and shutil.which("fc-match"):   # any other monospace font
        try:
            out = subprocess.run(["fc-match", "-f", "%{file}",
                                  "monospace:bold" if bold else "monospace"],
                                 capture_output=True, text=True, timeout=4).stdout.strip()
            if out.lower().endswith((".ttf", ".otf")) and os.path.exists(out):
                path = out
        except Exception:
            pass
    _font_path[bold] = path
    return path


def font(size, bold=False):
    key = (size, bold)
    if key in _font_cache:
        return _font_cache[key]
    path = _find_font(bold)
    if path:
        f = ImageFont.truetype(path, size)
    else:
        try:
            f = ImageFont.load_default(size)       # Pillow 10.1+ has a scalable one
        except TypeError:
            missing("font", "a monospace font (DejaVu)")
    _font_cache[key] = f
    return f


def level_color(pct):
    return ACCENT if pct < 75 else (WARN if pct < 90 else CRIT)


# ---------------------------------------------------------------- measuring

def _read(path, default=""):
    try:
        with open(path) as fp:
            return fp.read()
    except OSError:
        return default


def _read_int(path):
    raw = _read(path).strip()
    return int(raw) if raw.lstrip("-").isdigit() else None


def battery():
    """(level %, charging) or None. /sys/class/power_supply is not readable, so ask z2-battery."""
    exe = shutil.which("z2-battery") or "/usr/local/bin/z2-battery"
    if not os.path.exists(exe):
        return None
    try:
        out = subprocess.run([exe], capture_output=True, timeout=4, text=True).stdout
        d = json.loads(out)
        return int(d["level"]), bool(d.get("charging"))
    except Exception:
        return None                       # not available here: just leave it out


class Probe:
    """Looks only at what is readable inside the sandbox. Keeps the previous sample."""

    def __init__(self, sim=False):
        self.sim = sim
        self.t0 = time.time()
        self.prev = None                 # (total jiffies, time)
        self.hist_use = []               # CPU use (sum of processes in this environment)
        self.hist_clk = []               # average clock in MHz
        self.cpus = self._cpu_list()
        self.batt = None
        self.batt_t = 0.0
        self.maxf = [m for _, m in self.cpus_freq()] or [2600.0]
        if sim:
            for i in range(HIST):
                x = i / 7.0
                self.hist_use.append(max(0.4, 9 + 7.5 * math.sin(x) + 4 * math.sin(x * 2.7 + 1)
                                         + 2.5 * math.sin(x * 6.1)))
                self.hist_clk.append(1180 + 520 * math.sin(x * 1.3) + 260 * math.sin(x * 3.1))

    # -- cores -------------------------------------------------------
    @staticmethod
    def _cpu_list():
        out = []
        for d in glob.glob("/sys/devices/system/cpu/cpu[0-9]*/cpufreq"):
            m = re.search(r"/cpu(\d+)/cpufreq$", d)
            if m:
                out.append((int(m.group(1)), d))
        return [d for _, d in sorted(out)][:16]

    def cpus_freq(self):
        """[(current MHz or None, max MHz)]. A core that is switched off gives None."""
        if self.sim:
            base = [700, 2000, 1250, 2000, 500, None, 1650, 1550]
            mx = [2000] * 6 + [2600] * 2
            t = time.time() * 1.3
            return [(None if c is None else max(400, min(m, c + 260 * math.sin(t + i))), m)
                    for i, (c, m) in enumerate(zip(base, mx))]
        out = []
        for d in self.cpus:
            cur = _read_int(d + "/scaling_cur_freq")
            mx = _read_int(d + "/cpuinfo_max_freq") or _read_int(d + "/scaling_max_freq") or 0
            out.append((cur / 1000.0 if cur else None, (mx or 0) / 1000.0))
        return out

    # -- CPU use (sum of the processes running in this environment) --
    def cpu_usage(self):
        pids = [d for d in os.listdir("/proc") if d.isdigit()]
        jiff = 0
        for pid in pids:
            raw = _read("/proc/%s/stat" % pid)
            if not raw:
                continue
            f = raw.rsplit(") ", 1)[-1].split()
            if len(f) > 12:
                try:
                    jiff += int(f[11]) + int(f[12])       # utime + stime
                except ValueError:
                    pass
        now = time.time()
        pj, pt = self.prev or (jiff, now - 1.0)
        self.prev = (jiff, now)
        dt = max(0.05, now - pt)
        ncpu = max(1, len(self.cpus) or os.cpu_count() or 1)
        pct = 100.0 * (jiff - pj) / HZ / dt / ncpu
        return max(0.0, min(100.0, pct)), len(pids)

    # -- memory ------------------------------------------------------
    def mem(self):
        if self.sim:
            return dict(total=7.43, used=3.57, sw_total=5.57, sw_used=1.31)
        m = {}
        for row in _read("/proc/meminfo").splitlines():
            k, _, v = row.partition(":")
            parts = v.split()
            if parts:
                m[k] = int(parts[0]) / 1048576.0
        total = m.get("MemTotal", 0.0)
        avail = m.get("MemAvailable", m.get("MemFree", 0.0))
        swt = m.get("SwapTotal", 0.0)
        return dict(total=total, used=max(0.0, total - avail),
                    sw_total=swt, sw_used=max(0.0, swt - m.get("SwapFree", 0.0)))

    def disk(self):
        if self.sim:
            return 73.8, 109.6
        try:
            st = os.statvfs("/")
            return ((st.f_blocks - st.f_bfree) * st.f_frsize / 1073741824.0,
                    st.f_blocks * st.f_frsize / 1073741824.0)
        except OSError:
            return 0.0, 0.0

    def battery(self):
        """Not worth a subprocess every second, so keep it for 5 seconds."""
        if self.sim:
            return (71, False)
        now = time.time()
        if now - self.batt_t > 5.0:
            self.batt_t, self.batt = now, battery()
        return self.batt

    def sample(self):
        freqs = self.cpus_freq()
        live = [c for c, _ in freqs if c]
        clk = sum(live) / len(live) if live else 0.0
        if self.sim:
            x = (time.time() - self.t0) / 1.6
            use = max(0.4, 9 + 7.5 * math.sin(x) + 4 * math.sin(x * 2.7) + 2.5 * math.sin(x * 6.1))
            procs = 19
        else:
            use, procs = self.cpu_usage()
        self.hist_use.append(use)
        self.hist_clk.append(clk)
        del self.hist_use[:-HIST]
        del self.hist_clk[:-HIST]
        du, dt = self.disk()
        return dict(use=use, clk=clk, freqs=freqs, procs=procs,
                    hist_use=list(self.hist_use), hist_clk=list(self.hist_clk),
                    mem=self.mem(), disk_used=du, disk_total=dt,
                    session=time.time() - self.t0, batt=self.battery(),
                    cores=len(freqs), maxclk=max([m for _, m in freqs] or [0]))


def sysinfo():
    rel = {}
    for row in _read("/etc/os-release").splitlines():
        k, _, v = row.partition("=")
        rel[k] = v.strip('"')
    u = os.uname()
    name = rel.get("PRETTY_NAME") or rel.get("NAME") or u.sysname
    return dict(distro=name.replace(" Linux", "").strip(), arch=u.machine,
                kernel=u.release.split("-")[0], host=socket.gethostname())


def hms(sec):
    sec = int(sec)
    return "%d:%02d:%02d" % (sec // 3600, sec % 3600 // 60, sec % 60)


def ghz(mhz):
    return "%.2f GHz" % (mhz / 1000.0)


# ---------------------------------------------------------------- drawing parts

class Canvas:
    """Laid out at BASE_W and scaled to the real size."""

    def __init__(self, w, h, content):
        self.w, self.h = w, h
        self.s = min(w / float(BASE_W), h / float(content))
        self.vh = h / self.s           # bottom edge in layout units
        self.img = Image.new("RGB", (w, h), BG)
        self.d = ImageDraw.Draw(self.img)

    def u(self, v):
        return v * self.s

    def text(self, x, y, s, size, color=FG, bold=False, anchor="la", track=0.0):
        f = font(int(round(self.u(size))), bold)
        if not track:
            self.d.text((self.u(x), self.u(y)), s, font=f, fill=color, anchor=anchor)
            return
        cx = self.u(x)
        for ch in s:
            self.d.text((cx, self.u(y)), ch, font=f, fill=color, anchor=anchor)
            cx += self.d.textlength(ch, font=f) + self.u(track)

    def width_of(self, s, size, bold=False, track=0.0):
        f = font(int(round(self.u(size))), bold)
        return (self.d.textlength(s, font=f) + self.u(track) * max(0, len(s) - 1)) / self.s

    def rule(self, x, y, w, color=LINE):
        self.d.rectangle([self.u(x), self.u(y), self.u(x + w), self.u(y) + 1], fill=color)

    def box(self, x, y, w, h, color):
        self.d.rectangle([self.u(x), self.u(y), self.u(x + w) - 1, self.u(y + h) - 1], fill=color)

    def frame(self, x, y, w, h, color=LINE):
        self.d.rectangle([self.u(x), self.u(y), self.u(x + w) - 1, self.u(y + h) - 1],
                         outline=color, width=max(1, int(self.s)))

    def segbar(self, x, y, w, h, pct, color, gap=3):
        seg = max(10, int(w / 22))
        cw = (w - gap * (seg - 1)) / seg
        on = max(0.0, pct) / 100.0 * seg
        for i in range(seg):
            if i + 1 <= on:
                c = color
            elif i < on:
                c = tuple(int(a * 0.45 + b * 0.55) for a, b in zip(color, TRACK))
            else:
                c = TRACK
            self.box(x + i * (cw + gap), y, cw, h, c)

    def logo(self, x, y, cell):
        for j, row in enumerate(LOGO):
            for i, ch in enumerate(row):
                if ch == "#":
                    self.box(x + i * cell, y + j * cell, cell - 1, cell - 1, ACCENT)


def graph(c, x, y, w, h, values, ymax, color=ACCENT, fmt=None):
    c.box(x, y, w, h, (13, 17, 14))
    for g in (0.25, 0.5, 0.75):
        c.rule(x, y + h * (1 - g), w, LINE)
    if len(values) >= 2:
        step = w / (HIST - 1.0)
        off = HIST - len(values)
        pts = [(c.u(x + (off + i) * step), c.u(y + h * (1 - min(v, ymax) / ymax)))
               for i, v in enumerate(values)]
        c.d.polygon(pts + [(pts[-1][0], c.u(y + h)), (pts[0][0], c.u(y + h))], fill=FILLED)
        c.d.line(pts, fill=color, width=max(2, int(round(c.u(2)))), joint="curve")
        r = c.u(4)
        c.d.ellipse([pts[-1][0] - r, pts[-1][1] - r, pts[-1][0] + r, pts[-1][1] + r], fill=color)
    c.frame(x, y, w, h, LINE)
    if fmt:                                   # labels go on top of the line
        for g in (0.25, 0.5, 0.75):
            c.text(x + w - 6, y + h * (1 - g) - 17, fmt(ymax * g), 15, DIM, anchor="ra")


def tile(c, x, y, w, h, label, value, unit="", color=FG):
    c.frame(x, y, w, h)
    c.text(x + 16, y + 14, label, 15, DIM, track=2.4)
    c.text(x + 16, y + h - 20, value, 33, color, bold=True, anchor="ls")
    if unit:
        c.text(x + 16 + c.width_of(value, 33, True) + 8, y + h - 20, unit, 17, DIM, anchor="ls")


def head(c, x, y, w, title, right, note=None):
    c.text(x, y, title, 19, ACCENT, bold=True, track=3.0)
    if note:
        c.text(x + c.width_of(title, 19, True, 3.0) + 16, y, note, 19, DIM)
    if right:
        c.text(x + w, y, right, 19, FG, anchor="ra")


def core_rows(c, x, y, w, freqs, bh, gap):
    """Current clock per core. A core that is off says "off" (not a fake 0%)."""
    lab_w, val_w = 76, 178
    for i, (cur, mx) in enumerate(freqs):
        by = y + i * (bh + gap)
        c.text(x, by + bh / 2.0, "cpu%d" % i, 17, DIM, anchor="lm")
        pct = 100.0 * cur / mx if (cur and mx) else 0.0
        c.segbar(x + lab_w, by, w - lab_w - val_w, bh, pct, ACCENT if cur else TRACK)
        if cur:
            c.text(x + w, by + bh / 2.0, "%4d / %d MHz" % (round(cur), round(mx)),
                   17, FG, anchor="rm")
        else:
            c.text(x + w, by + bh / 2.0, "off", 17, DIM, anchor="rm")
    return y + len(freqs) * (bh + gap)


def header(c, info, pad, cell=8):
    right = BASE_W - pad
    c.logo(pad, 42, cell)
    c.text(pad + 35 * cell + 24, 44, "z2mon", 22, DIM)
    c.text(right, 36, time.strftime("%H:%M:%S"), 48, FG, bold=True, anchor="ra")
    c.text(pad, 96, "%s  ·  %s  ·  %d cores  ·  Linux %s  ·  Xvnc + openbox" % (
        info["distro"], info["arch"], info["cores"], info["kernel"]), 19, DIM)
    c.rule(pad, 132, BASE_W - pad * 2)


def footer(c, w, h, st, info, pad, y):
    foot = max(y + 40, c.vh - 66)
    c.rule(pad, foot, BASE_W - pad * 2)
    c.text(pad, foot + 16, "%s  ·  display %s  ·  %dx%d" % (
        info["host"], os.environ.get("DISPLAY", "-"), w, h), 17, DIM)
    c.text(BASE_W - pad, foot + 16, time.strftime("%Y-%m-%d"), 17, DIM, anchor="ra")


def tiles_of(st):
    rest = [("CLOCK max", "%.1f" % (st["maxclk"] / 1000.0), "GHz", FG),
            ("PROCS", str(st["procs"]), "", FG),
            ("SESSION", hms(st["session"]), "", FG)]
    if st["batt"]:
        level, charging = st["batt"]
        first = ("BATTERY" + ("  chg" if charging else ""), str(level), "%",
                 ACCENT if charging else (CRIT if level < 15 else FG))
    else:
        first = ("CORES", str(st["cores"]), "", FG)
    return [first] + rest


# ---------------------------------------------------------------- layout

def use_scale(hist):
    """CPU use is often a few %. Let the top of the graph follow it."""
    return max(8.0, math.ceil(max(hist or [0]) * 1.35 / 5.0) * 5.0)


def render_wide(w, h, st, info):
    """Two columns, for wide to square windows (the GUI tab is often about 1026x999)."""
    c = Canvas(w, h, 660 + len(st["freqs"]) * 42)
    pad, gut = 40, 34
    colw = (BASE_W - pad * 2 - gut) / 2.0
    lx, rx = pad, pad + colw + gut
    header(c, info, pad)

    # left: clock
    y = 158
    head(c, lx, y, colw, "CPU CLOCK", ghz(st["clk"]), "avg")
    graph(c, lx, y + 32, colw, 250, st["hist_clk"], max(st["maxclk"], 1),
          ACCENT, lambda v: "%d" % v)
    ey = core_rows(c, lx, y + 316, colw, st["freqs"], 28, 16)

    # right: use -> memory -> tiles
    y2 = 158
    head(c, rx, y2, colw, "CPU LOAD", "%.1f%%" % st["use"], "this session")
    graph(c, rx, y2 + 32, colw, 250, st["hist_use"], use_scale(st["hist_use"]),
          TEAL, lambda v: "%d" % v)
    y2 += 316
    m = st["mem"]
    for title, used, total in (("MEMORY", m["used"], m["total"]),
                               ("SWAP", m["sw_used"], m["sw_total"])):
        if total <= 0:
            continue
        pct = 100.0 * used / total
        head(c, rx, y2, colw, title, "%.2f / %.2f GB" % (used, total), "%.0f%%" % pct)
        c.segbar(rx, y2 + 28, colw, 26, pct, level_color(pct))
        y2 += 78

    tw, th, tg = (colw - 14) / 2.0, 104, 14
    for i, (lab, val, unit, col) in enumerate(tiles_of(st)):
        tile(c, rx + (i % 2) * (tw + tg), y2 + 8 + (i // 2) * (th + tg), tw, th,
             lab, val, unit, col)
    y2 += 8 + th * 2 + tg

    # bottom left: storage
    if st["disk_total"] > 0:
        pct = 100.0 * st["disk_used"] / st["disk_total"]
        head(c, lx, ey + 26, colw, "STORAGE",
             "%.1f / %.1f GB" % (st["disk_used"], st["disk_total"]), "%.0f%%" % pct)
        c.segbar(lx, ey + 54, colw, 26, pct, level_color(pct))
        ey += 100

    footer(c, w, h, st, info, pad, max(ey, y2))
    return c.img


def render_tall(w, h, st, info):
    """One column, for tall windows (phone held upright)."""
    n = len(st["freqs"])
    content = 1180 + n * 42
    c = Canvas(w, h, content)
    pad = 42
    inner = BASE_W - pad * 2
    header(c, info, pad)

    y = 162
    head(c, pad, y, inner, "CPU CLOCK", ghz(st["clk"]), "avg")
    graph(c, pad, y + 34, inner, 230, st["hist_clk"], max(st["maxclk"], 1),
          ACCENT, lambda v: "%d" % v)
    y = core_rows(c, pad, y + 292, inner, st["freqs"], 28, 14) + 18
    c.rule(pad, y, inner)

    y += 28
    head(c, pad, y, inner, "CPU LOAD", "%.1f%%" % st["use"], "this session")
    graph(c, pad, y + 34, inner, 190, st["hist_use"], use_scale(st["hist_use"]),
          TEAL, lambda v: "%d" % v)
    y += 244
    c.rule(pad, y, inner)

    y += 28
    m = st["mem"]
    for title, used, total in (("MEMORY", m["used"], m["total"]),
                               ("SWAP", m["sw_used"], m["sw_total"])):
        if total <= 0:
            continue
        pct = 100.0 * used / total
        head(c, pad, y, inner, title, "%.2f / %.2f GB" % (used, total), "%.0f%%" % pct)
        c.segbar(pad, y + 30, inner, 28, pct, level_color(pct))
        y += 84

    tw, th, tg = (inner - 3 * 14) / 4.0, 110, 14
    for i, (lab, val, unit, col) in enumerate(tiles_of(st)):
        tile(c, pad + i * (tw + tg), y, tw, th, lab, val, unit, col)
    y += th + 32

    if st["disk_total"] > 0:
        pct = 100.0 * st["disk_used"] / st["disk_total"]
        head(c, pad, y, inner, "STORAGE",
             "%.1f / %.1f GB" % (st["disk_used"], st["disk_total"]), "%.0f%%" % pct)
        c.segbar(pad, y + 30, inner, 28, pct, level_color(pct))
        y += 70

    footer(c, w, h, st, info, pad, y)
    return c.img


def render(w, h, st, info):
    return render_tall(w, h, st, info) if h / float(w) >= 1.25 else render_wide(w, h, st, info)


def render_clock(w, h, st, info):
    c = Canvas(w, h, 300)
    c.s = min(w / 520.0, h / 300.0)
    c.vh = h / c.s
    c.text(260, 44, time.strftime("%H:%M:%S"), 96, FG, bold=True, anchor="ma")
    c.text(260, 172, time.strftime("%Y-%m-%d  %a"), 22, DIM, anchor="ma")
    c.rule(28, 218, 464)
    c.text(260, 238, "%s   ·   %s   ·   %d cores" % (
        ghz(st["clk"]), hms(st["session"]), st["cores"]), 19, ACCENT, anchor="ma")
    return c.img


# ---------------------------------------------------------------- output

def to_photo(tk, img):
    buf = io.BytesIO()
    img.save(buf, "PNG", compress_level=1)
    return tk.PhotoImage(data=base64.b64encode(buf.getvalue()))


def run_tk(args, probe, info):
    try:
        import tkinter as tk
    except ImportError:
        missing("tk", "tkinter (Tk for Python)")

    try:
        root = tk.Tk()
    except tk.TclError as e:
        sys.exit("z2mon: cannot open a window (%s).\n"
                 "  run it from a terminal tab with: z2run python3 %s" % (e, sys.argv[0]))
    root.title("z2mon")
    root.configure(bg="#0a0d0b")
    if args.size:
        w, h = (int(v) for v in args.size.lower().split("x"))
    else:                                   # leave room for the openbox frame
        w = root.winfo_screenwidth() - 12
        h = root.winfo_screenheight() - 44
    root.geometry("%dx%d+4+4" % (w, h))
    main = tk.Label(root, bd=0, highlightthickness=0, bg="#0a0d0b")
    main.pack(fill="both", expand=True)

    subl = None
    if args.clock:
        sub = tk.Toplevel(root)
        sub.title("z2clock")
        sub.configure(bg="#0a0d0b")
        sub.geometry("520x300+%d+%d" % (max(8, w - 560), max(80, h - 380)))
        subl = tk.Label(sub, bd=0, highlightthickness=0, bg="#0a0d0b")
        subl.pack(fill="both", expand=True)

    def tick():
        st = probe.sample()
        main.photo = to_photo(tk, render(w, h, st, info))
        main.config(image=main.photo)
        if subl is not None:
            subl.photo = to_photo(tk, render_clock(520, 300, st, info))
            subl.config(image=subl.photo)
        root.after(int(args.interval * 1000), tick)

    root.bind("<Escape>", lambda _e: root.destroy())
    tick()
    root.mainloop()


def main():
    ap = argparse.ArgumentParser(description="A small system monitor for the z2term GUI tab")
    ap.add_argument("--png", metavar="OUT", help="draw one frame and save it as a PNG")
    ap.add_argument("--size", metavar="WxH", help="drawing size (default: the whole screen)")
    ap.add_argument("--clock", action="store_true", help="also open a clock window")
    ap.add_argument("--sim", action="store_true", help="draw made-up values (layout check)")
    ap.add_argument("--interval", type=float, default=1.0, metavar="SEC",
                    help="update interval; the graphs keep 90 points, so 0.5 fills them in 45 s")
    args = ap.parse_args()

    probe = Probe(sim=args.sim)
    info = sysinfo()
    info["cores"] = max(1, len(probe.cpus)) if not args.sim else 8
    if args.png:
        w, h = (int(v) for v in (args.size or "1026x999").lower().split("x"))
        if not args.sim:
            probe.sample()
            time.sleep(0.4)
        st = probe.sample()
        render(w, h, st, info).save(args.png)
        if args.clock:
            render_clock(520, 300, st, info).save(args.png.replace(".png", "-clock.png"))
        print(args.png)
        return
    run_tk(args, probe, info)


if __name__ == "__main__":
    main()
