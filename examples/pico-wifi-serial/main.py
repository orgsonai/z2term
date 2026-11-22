# Pico W / Pico 2 W の UART を Wi-Fi 越しに素通しにする中継器。
#
#   スマホ (z2term) ─Wi-Fi─ Pico 2 W ─線 3 本─ 相手の UART (ルーターのコンソール、ほかの基板 …)
#
# z2term では接続先の「新規」→「シリアル」→「ネットワーク (TCP)」で、Pico のアドレスと PORT を入れる。
# 届いたバイトも送るバイトも手を加えない (ser2net の raw と同じ)。同時につなげるのは 1 台で、
# 新しくつないだら古い方を切る (スマホ側が黙って切れたままでも、つなぎ直せるように)。
#
# ⚠ 通信は暗号化されない。家の中など信頼できるネットワークでだけ使う。ALLOW で相手を絞れる。
#
# 配線 (相手が 3.3V の UART のとき。5V や RS-232 の相手には直接つながない):
#   Pico GP0 (UART0 TX) → 相手の RX
#   Pico GP1 (UART0 RX) → 相手の TX
#   Pico GND            → 相手の GND
# 書き込み: mpremote cp main.py :main.py

import time
import network
import select
import socket
from machine import UART, Pin

SSID = "your-wifi"
PASSWORD = "your-password"
# 決まったアドレスにするなら ("192.168.1.50", "255.255.255.0", "192.168.1.1", "192.168.1.1")。None ならルーター任せ。
STATIC = None
PORT = 2000
# 相手の UART の速さ。相手 (機器) に合わせる。
BAUD = 115200
# つないでよいスマホのアドレス。空なら誰でも。例: ("192.168.1.20",)
ALLOW = ()

led = Pin("LED", Pin.OUT)
uart = UART(0, baudrate=BAUD, tx=Pin(0), rx=Pin(1), rxbuf=4096, txbuf=1024)
wlan = network.WLAN(network.STA_IF)


def join_wifi():
    wlan.active(True)
    # 省電力を切る (入れたままだと、打ってから返事が出るまでが遅れる)。
    wlan.config(pm=0xA11140)
    if STATIC:
        wlan.ifconfig(STATIC)
    wlan.connect(SSID, PASSWORD)
    while not wlan.isconnected():
        led.toggle()
        time.sleep_ms(250)
    led.off()
    print("wifi-serial:", wlan.ifconfig()[0], "port", PORT)


def send_all(sock, data):
    view = memoryview(data)
    deadline = time.ticks_add(time.ticks_ms(), 2000)
    while view:
        try:
            n = sock.send(view)
            if n:
                view = view[n:]
        except OSError as e:
            if e.args[0] not in (11, 110):  # EAGAIN / ETIMEDOUT: 送り先が詰まっている
                raise
        if time.ticks_diff(deadline, time.ticks_ms()) <= 0:
            raise OSError(110)


def main():
    join_wifi()
    server = socket.socket()
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", PORT))
    server.listen(1)
    server.setblocking(False)
    poller = select.poll()
    poller.register(server, select.POLLIN)
    client = None

    def drop():
        nonlocal client
        if client is not None:
            try:
                poller.unregister(client)
            except Exception:
                pass
            client.close()
            client = None
            led.off()

    while True:
        if not wlan.isconnected():
            drop()
            join_wifi()
        for entry in poller.poll(5):
            obj, ev = entry[0], entry[1]
            if obj is server:
                try:
                    conn, addr = server.accept()
                except OSError:
                    continue
                if ALLOW and addr[0] not in ALLOW:
                    conn.close()
                    continue
                drop()
                client = conn
                client.setblocking(False)
                poller.register(client, select.POLLIN)
                led.on()
            elif client is not None and obj is client:
                if ev & (select.POLLHUP | select.POLLERR):
                    drop()
                    continue
                try:
                    data = client.recv(512)
                except OSError:
                    continue
                if not data:
                    drop()
                    continue
                uart.write(data)
        n = uart.any()
        if n:
            data = uart.read(n)
            # つないでいない間に相手が出した分は捨てる (溜めて流すと、つないだ瞬間に古い出力が流れる)。
            if client is not None and data:
                try:
                    send_all(client, data)
                except OSError:
                    drop()


main()
