# z2term のシリアルのタブで打った文字を、Pico を挿した PC へキーボードとして打ち込む。
#
#   スマホ (z2term) ─USB─ USB-TTL 変換器 ─ジャンパー─ Pico の UART ─USB─ PC   (SOURCE = "uart")
#   スマホ (z2term) ─Wi-Fi─ Pico W / Pico 2 W ─USB─ PC                          (SOURCE = "wifi")
#
# Pico は PC からはふつうの USB キーボードに見えるので、PC 側には何も入れなくてよい
# (BIOS の画面・ログイン前の画面でも打てる)。
#
# 用意 (MicroPython 1.25 以降、Pico 2 W / Pico W / Pico / Pico 2):
#   mpremote mip install usb-device-hid
#   mpremote cp main.py :main.py
# 配線 (変換器は 3.3V にする。VCC はつながない):
#   変換器 TXD → Pico GP1 (UART0 RX)
#   変換器 RXD → Pico GP0 (UART0 TX)   打った文字をスマホの画面へ返す。無くても打てる
#   変換器 GND → Pico GND
# z2term: 接続先の「新規」→「シリアル」、速さ 115200。
#
# SOURCE = "wifi" なら配線は要らない。SSID / PASSWORD を入れ、z2term では「シリアル」→
# 「ネットワーク (TCP)」で Pico のアドレス (起動時に REPL へ出る) と PORT へつなぐ。
# ⚠ 通信は暗号化されず、つないだ人が PC に何でも打てる。信頼できるネットワークでだけ使い、ALLOW で相手を絞る。

import time
from machine import UART, Pin
import usb.device
from usb.device.hid import HIDInterface

# PC のキーボードの配列。日本語キーボードの PC なら "jis"、英語キーボードなら "us"。
LAYOUT = "jis"
# 受け取り口。"uart" = USB-TTL 変換器から (上の配線)。"wifi" = Wi-Fi で受ける (Pico W / Pico 2 W)。
SOURCE = "uart"
BAUD = 115200
# SOURCE = "wifi" のときだけ使う。
SSID = "your-wifi"
PASSWORD = "your-password"
PORT = 2000
# つないでよいスマホのアドレス。空なら誰でも。例: ("192.168.1.20",)
ALLOW = ()
# 1 キーを押して離すまでの間 (ミリ秒)。取りこぼす PC では大きくする。
KEY_GAP_MS = 4

# --- USB キーボード ---------------------------------------------------------

# ふつうの 8 バイトのキーボード。キーの番号は 0〜255 を通す (日本語配列の ¥ と ろ は 0x89 / 0x87 で、
# よくある 0〜101 までの記述では PC に届かないため)。
_DESC = bytes((
    0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
    0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
    0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
    0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
    0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
    0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x05, 0x07, 0x19, 0x00, 0x2A, 0xFF, 0x00, 0x81, 0x00,
    0xC0,
))

CTRL, SHIFT, ALT = 0x01, 0x02, 0x04


class Keyboard(HIDInterface):
    def __init__(self):
        super().__init__(_DESC, set_report_buf=bytearray(1), protocol=1, interface_str="z2term keyboard")
        # ⚠ 押す・離すで別の入れ物を使う。送っている途中の入れ物を書き換えると、PC に違うキーが届く。
        self._down = bytearray(8)
        self._up = bytes(8)

    def tap(self, mod, code):
        d = self._down
        d[0] = mod
        d[2] = code
        self.send_report(d, 200)
        time.sleep_ms(KEY_GAP_MS)
        self.send_report(self._up, 200)
        time.sleep_ms(KEY_GAP_MS)


# --- 文字 → キー --------------------------------------------------------------

ENTER, ESC, BACKSPACE, TAB, SPACE = 0x28, 0x29, 0x2A, 0x2B, 0x2C
F1 = 0x3A
INSERT, HOME, PGUP, DELETE, END, PGDN = 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E
RIGHT, LEFT, DOWN, UP = 0x4F, 0x50, 0x51, 0x52
# 日本語配列の「半角/全角」(英語配列の ` と同じ位置)。
ZENKAKU_HANKAKU = 0x35


def _table(rows):
    t = {}
    for code, plain, shifted in rows:
        if plain:
            t[plain] = (0, code)
        if shifted:
            t[shifted] = (SHIFT, code)
    return t


_US = _table((
    (0x1E, "1", "!"), (0x1F, "2", "@"), (0x20, "3", "#"), (0x21, "4", "$"), (0x22, "5", "%"),
    (0x23, "6", "^"), (0x24, "7", "&"), (0x25, "8", "*"), (0x26, "9", "("), (0x27, "0", ")"),
    (0x2D, "-", "_"), (0x2E, "=", "+"), (0x2F, "[", "{"), (0x30, "]", "}"), (0x31, "\\", "|"),
    (0x33, ";", ":"), (0x34, "'", '"'), (0x35, "`", "~"), (0x36, ",", "<"), (0x37, ".", ">"),
    (0x38, "/", "?"),
))

_JIS = _table((
    (0x1E, "1", "!"), (0x1F, "2", '"'), (0x20, "3", "#"), (0x21, "4", "$"), (0x22, "5", "%"),
    (0x23, "6", "&"), (0x24, "7", "'"), (0x25, "8", "("), (0x26, "9", ")"), (0x27, "0", None),
    (0x2D, "-", "="), (0x2E, "^", "~"), (0x89, None, "|"), (0x2F, "@", "`"), (0x30, "[", "{"),
    (0x32, "]", "}"), (0x33, ";", "+"), (0x34, ":", "*"), (0x36, ",", "<"), (0x37, ".", ">"),
    (0x38, "/", "?"), (0x87, "\\", "_"),
))

SYMBOLS = _JIS if LAYOUT == "jis" else _US


def key_of(ch):
    """1 文字 → (修飾, キー)。打てない文字は None。"""
    if "a" <= ch <= "z":
        return 0, 0x04 + ord(ch) - ord("a")
    if "A" <= ch <= "Z":
        return SHIFT, 0x04 + ord(ch) - ord("A")
    if ch == " ":
        return 0, SPACE
    return SYMBOLS.get(ch)


# ESC [ n ~ の n → キー
_TILDE = {1: HOME, 2: INSERT, 3: DELETE, 4: END, 5: PGUP, 6: PGDN, 7: HOME, 8: END,
          11: F1, 12: F1 + 1, 13: F1 + 2, 14: F1 + 3, 15: F1 + 4,
          17: F1 + 5, 18: F1 + 6, 19: F1 + 7, 20: F1 + 8, 21: F1 + 9, 23: F1 + 10, 24: F1 + 11}
# ESC [ x / ESC O x の x → キー
_FINAL = {"A": UP, "B": DOWN, "C": RIGHT, "D": LEFT, "H": HOME, "F": END,
          "P": F1, "Q": F1 + 1, "R": F1 + 2, "S": F1 + 3}


def _mods(param):
    """xterm の修飾の番号 (2 = Shift, 3 = Alt, 5 = Ctrl …) → USB の修飾。"""
    m = (param - 1) if param > 1 else 0
    return (SHIFT if m & 1 else 0) | (ALT if m & 2 else 0) | (CTRL if m & 4 else 0)


# --- 受け取りと打ち込み --------------------------------------------------------

kbd = Keyboard()
# builtin_driver=True: MicroPython の REPL (USB シリアル) も残す。PC から直せるように。
usb.device.get().init(kbd, builtin_driver=True)


class UartSource:
    def __init__(self):
        self.uart = UART(0, baudrate=BAUD, tx=Pin(0), rx=Pin(1), rxbuf=4096)

    def available(self):
        return self.uart.any()

    def take(self):
        return self.uart.read(1)[0]

    def write(self, text):
        self.uart.write(text)


class WifiSource:
    """TCP で 1 台だけ受ける。新しくつないだら古い方を切る (examples/pico-wifi-serial と同じ)。"""

    def __init__(self):
        import network
        import socket
        self.wlan = network.WLAN(network.STA_IF)
        self.join()
        self.server = socket.socket()
        self.server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server.bind(("0.0.0.0", PORT))
        self.server.listen(1)
        self.server.setblocking(False)
        self.client = None
        self.pending = b""

    def join(self):
        self.wlan.active(True)
        # 省電力を切る (入れたままだと、打ってから PC に出るまでが遅れる)。
        self.wlan.config(pm=0xA11140)
        self.wlan.connect(SSID, PASSWORD)
        while not self.wlan.isconnected():
            time.sleep_ms(250)
        print("z2term keyboard:", self.wlan.ifconfig()[0], "port", PORT)

    def drop(self):
        if self.client is not None:
            self.client.close()
            self.client = None
        self.pending = b""

    def available(self):
        if not self.wlan.isconnected():
            self.drop()
            self.join()
        try:
            conn, addr = self.server.accept()
        except OSError:
            conn = None
        if conn is not None:
            if ALLOW and addr[0] not in ALLOW:
                conn.close()
            else:
                self.drop()
                conn.setblocking(False)
                self.client = conn
                self.write("\r\nz2term keyboard ready (" + LAYOUT + ")\r\n")
        if self.client is not None and not self.pending:
            try:
                data = self.client.recv(512)
            except OSError:
                data = None
            if data == b"":
                self.drop()
            elif data:
                self.pending = data
        return len(self.pending)

    def take(self):
        b = self.pending[0]
        self.pending = self.pending[1:]
        return b

    def write(self, text):
        if self.client is None:
            return
        try:
            self.client.send(text.encode())
        except OSError:
            self.drop()


source = WifiSource() if SOURCE == "wifi" else UartSource()


def echo(text):
    source.write(text)


def read_byte(wait_ms):
    """wait_ms 待っても来なければ None。"""
    deadline = time.ticks_add(time.ticks_ms(), wait_ms)
    while not source.available():
        if time.ticks_diff(deadline, time.ticks_ms()) <= 0:
            return None
        time.sleep_ms(1)
    return source.take()


def escape():
    """ESC の後ろを読む。単独の ESC はそのまま Esc キー。"""
    b = read_byte(30)
    if b is None:
        kbd.tap(0, ESC)
        return
    c = chr(b)
    if c not in "[O":
        # Alt + 文字 (ESC に続けて送ってくる端末の書き方)
        k = key_of(c)
        if k:
            kbd.tap(k[0] | ALT, k[1])
        return
    params = ""
    while True:
        b = read_byte(30)
        if b is None:
            return
        c = chr(b)
        if c.isdigit() or c == ";":
            params += c
            continue
        break
    nums = [int(p) for p in params.split(";") if p]
    mod = _mods(nums[1]) if len(nums) > 1 else 0
    if c == "~" and nums:
        code = _TILDE.get(nums[0])
    else:
        code = _FINAL.get(c)
    if code:
        kbd.tap(mod, code)


def main():
    echo("\r\nz2term keyboard ready (" + LAYOUT + ")\r\n")
    last = 0
    while True:
        b = read_byte(1000)
        if b is None:
            continue
        if b == 0x1B:
            escape()
        elif b == 0x0D:
            kbd.tap(0, ENTER)
            echo("\r\n")
        elif b == 0x0A:
            # 貼り付けの改行。直前が CR なら同じ 1 回の Enter。
            if last != 0x0D:
                kbd.tap(0, ENTER)
                echo("\r\n")
        elif b == 0x09:
            kbd.tap(0, TAB)
        elif b in (0x7F, 0x08):
            kbd.tap(0, BACKSPACE)
            echo("\b \b")
        elif b == 0x00:
            # Ctrl+Space (端末では NUL) → PC の日本語入力の切り替え。端末にはこのキーを送る形が無いため、ここで当てる。
            # 日本語配列なら「半角/全角」、英語配列なら Ctrl+Space (多くの日本語入力の切り替え)。
            if LAYOUT == "jis":
                kbd.tap(0, ZENKAKU_HANKAKU)
            else:
                kbd.tap(CTRL, SPACE)
        elif b < 0x20:
            # Ctrl + 英字 (Ctrl+C は 0x03)
            if 1 <= b <= 26:
                kbd.tap(CTRL, 0x04 + b - 1)
        elif b < 0x80:
            k = key_of(chr(b))
            if k:
                kbd.tap(k[0], k[1])
                echo(chr(b))
        # 0x80 以上 (日本語などの UTF-8) は打てないので捨てる。
        last = b


main()
