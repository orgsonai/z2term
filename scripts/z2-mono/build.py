#!/usr/bin/env python3
"""Z2 Mono — 英字・数字・記号を線の定義から自動で描いて TTF にする。

字の形は「線の通り道」(折れ線と円弧) だけで定義し、同じ太さ・同じ丸みで肉付けする。
ブロック要素 (▀ █ ▛ など)・罫線 (─ │ ┌ など)・図形 (● ○ ■ ▶ など) は、1 文字のマス目を基準に描く。
既存フォントの輪郭は一切使っていない。

使い方:  build.py <出力.ttf> [太さ]
"""
import math
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import LineString, Point, Polygon, box as rect
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

UPM = 1000
ADV = 600           # 1 文字の送り幅 (等幅)
ASCENT, DESCENT = 800, -220   # 行の上端・下端 (ここへ収まるように描く)

STROKE = float(sys.argv[2]) if len(sys.argv) > 2 else 84.0
H = STROKE / 2

# 線の通り道の座標 (線の中心)。外形はここから H だけ外へ出る。
XL, XR, CX = 115, 485, 300
CAP = 700 - H        # 大文字・数字の上
BASE = 0 + H         # ベースライン
XH = 500 - H         # 小文字の上
ASC = 742 - H        # b d h k l の上
DESC = -200 + H      # g j p q y の下
MID = 350            # 大文字の中段
OV = 8               # 丸い字を少しはみ出させる量 (同じ高さに見せるため)
LC_CY = (XH + BASE) / 2
LC_RY = (XH - BASE) / 2 + OV
DOT = 62


def arc(cx, cy, rx, ry, a0, a1, step=6):
    """楕円弧。角度は度、a0 > a1 なら時計回り。"""
    n = max(2, int(abs(a1 - a0) / step) + 1)
    return [
        (cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / (n - 1))),
         cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / (n - 1))))
        for i in range(n)
    ]


def ell(cx, cy, rx, ry):
    return arc(cx, cy, rx, ry, 0, 360)


def mirror(paths):
    return [[(2 * CX - x, y) for x, y in p] for p in paths]


def lc_bowl(cx=CX, rx=185):
    return ell(cx, LC_CY, rx, LC_RY)


def arch(x0, x1, top=XH + OV, spring=None):
    """n / h / m の肩。x0 の縦線から立ち上がって x1 の縦線でベースラインへ降りる。"""
    rx = (x1 - x0) / 2
    spring = spring if spring is not None else top - 190
    return [(x0, spring)] + arc(x0 + rx, spring, rx, top - spring, 180, 0) + [(x1, BASE)]


S_UPPER = arc(300, 504, 165, 162, 25, 270) + arc(300, 188, 180, 154, 90, -155)
COMMA = [(322, 40), (255, -118)]

# 字ごとの定義: (線のリスト, 点のリスト, 太さの倍率)
G = {}


def g(ch, paths, dots=(), scale=1.0):
    G[ch] = (paths, list(dots), scale)


g(' ', [])
# ---- 大文字 ----
g('A', [[(XL - 12, BASE), (CX, CAP), (XR + 12, BASE)], [(170, 215), (430, 215)]])
g('B', [[(XL, BASE), (XL, CAP)],
        [(XL, CAP), (320, CAP)] + arc(320, (CAP + 360) / 2, 140, (CAP - 360) / 2, 90, -90) + [(XL, 360)],
        [(XL, 360), (335, 360)] + arc(335, (360 + BASE) / 2, 150, (360 - BASE) / 2, 90, -90) + [(XL, BASE)]])
g('C', [arc(312, MID, 197, MID - BASE + OV, 48, 312)])
g('D', [[(XL, BASE), (XL, CAP), (285, CAP)] + arc(285, MID, 200, MID - BASE, 90, -90) + [(XL, BASE)]])
g('E', [[(XR - 15, CAP), (XL, CAP), (XL, BASE), (XR - 15, BASE)], [(XL, MID), (430, MID)]])
g('F', [[(XR - 15, CAP), (XL, CAP), (XL, BASE)], [(XL, MID), (430, MID)]])
g('G', [arc(307, MID, 192, MID - BASE + OV, 48, 338) + [(XR, 320), (320, 320)]])
g('H', [[(XL, BASE), (XL, CAP)], [(XR, BASE), (XR, CAP)], [(XL, MID), (XR, MID)]])
g('I', [[(CX, BASE), (CX, CAP)], [(165, CAP), (435, CAP)], [(165, BASE), (435, BASE)]])
g('J', [[(215, CAP), (440, CAP), (440, 200)] + arc(290, 200, 150, 200 - BASE + OV, 0, -155)])
g('K', [[(XL, BASE), (XL, CAP)], [(478, CAP), (XL, 290)], [(232, 408), (492, BASE)]])
g('L', [[(XL + 15, CAP), (XL + 15, BASE), (XR, BASE)]])
g('M', [[(XL - 18, BASE), (XL - 18, CAP), (CX, 290), (XR + 18, CAP), (XR + 18, BASE)]])
g('N', [[(XL, BASE), (XL, CAP), (XR, BASE), (XR, CAP)]])
g('O', [ell(CX, MID, 190, MID - BASE + OV)])
g('P', [[(XL, BASE), (XL, CAP), (325, CAP)] + arc(325, (CAP + 300) / 2, 160, (CAP - 300) / 2, 90, -90) + [(XL, 300)]])
g('Q', [ell(CX, MID, 190, MID - BASE + OV), [(335, 150), (505, -55)]])
g('R', [[(XL, BASE), (XL, CAP), (325, CAP)] + arc(325, (CAP + 310) / 2, 155, (CAP - 310) / 2, 90, -90) + [(XL, 310)],
        [(300, 310), (490, BASE)]])
g('S', [S_UPPER])
g('T', [[(XL - 18, CAP), (XR + 18, CAP)], [(CX, CAP), (CX, BASE)]])
g('U', [[(XL, CAP), (XL, 230)] + arc(CX, 230, 185, 230 - BASE + OV, 180, 360) + [(XR, CAP)]])
g('V', [[(XL - 15, CAP), (CX, BASE), (XR + 15, CAP)]])
g('W', [[(XL - 28, CAP), (192, BASE), (CX, 440), (408, BASE), (XR + 28, CAP)]], scale=0.94)
g('X', [[(XL, CAP), (XR, BASE)], [(XR, CAP), (XL, BASE)]])
g('Y', [[(XL - 15, CAP), (CX, 320), (XR + 15, CAP)], [(CX, 320), (CX, BASE)]])
g('Z', [[(XL, CAP), (XR, CAP), (XL, BASE), (XR, BASE)]])

# ---- 小文字 ----
g('a', [arc(300, 330, 180, XH + OV - 330, 165, 0) + [(XR - 5, BASE)],
        [(XR - 5, 275), (270, 275)] + arc(270, (275 + BASE - OV) / 2, 150, (275 - BASE + OV) / 2, 90, 270) + [(330, BASE - OV)]
        + arc(330, 180, 150, 180 - BASE + OV, 270, 330)])
g('b', [[(XL, ASC), (XL, BASE)], lc_bowl()])
g('c', [arc(312, LC_CY, 197, LC_RY, 50, 310)])
g('d', [[(XR, ASC), (XR, BASE)], lc_bowl()])
g('e', [[(122, LC_CY), (XR, LC_CY)] + arc(CX, LC_CY, 185, LC_RY, 0, 318)])
g('f', [[(275, BASE), (275, 570)] + arc(400, 570, 125, ASC - 570, 180, 55), [(140, XH), (450, XH)]])
g('g', [lc_bowl(), [(XR, XH), (XR, -15)] + arc(CX, -15, 185, -15 - DESC, 0, -150)])
g('h', [[(XL, ASC), (XL, BASE)], arch(XL, XR)])
g('i', [[(185, XH), (CX, XH), (CX, BASE)], [(160, BASE), (445, BASE)]], dots=[(295, 665)])
g('j', [[(205, XH), (365, XH), (365, -20)] + arc(210, -20, 155, -20 - DESC, 0, -140)], dots=[(360, 665)])
g('k', [[(XL + 10, ASC), (XL + 10, BASE)], [(468, XH), (XL + 10, 190)], [(252, 288), (488, BASE)]])
g('l', [[(165, ASC), (285, ASC), (285, 165)] + arc(410, 165, 125, 165 - BASE, 180, 270) + [(485, BASE)]])
g('m', [[(88, XH), (88, BASE)], arch(88, CX, spring=385), arch(CX, 512, spring=385)], scale=0.88)
g('n', [[(XL, XH), (XL, BASE)], arch(XL, XR)])
g('o', [lc_bowl()])
g('p', [[(XL, XH), (XL, DESC)], lc_bowl()])
g('q', [[(XR, XH), (XR, DESC)], lc_bowl()])
g('r', [[(120, XH), (215, XH), (215, BASE)], [(215, 330)] + arc(365, 330, 150, XH + OV - 330, 180, 35),
        [(110, BASE), (350, BASE)]])
g('s', [arc(300, 358, 150, XH + OV - 358, 28, 270) + arc(300, 142, 165, 142 - BASE + OV, 90, -155)])
g('t', [[(255, 650), (255, 165)] + arc(380, 165, 125, 165 - BASE, 180, 270) + [(470, BASE)], [(125, XH), (455, XH)]])
g('u', [[(XL, XH), (XL, 225)] + arc(CX, 225, 185, 225 - BASE + OV, 180, 360), [(XR, XH), (XR, BASE)]])
g('v', [[(XL - 5, XH), (CX, BASE), (XR + 5, XH)]])
g('w', [[(XL - 28, XH), (198, BASE), (CX, 340), (402, BASE), (XR + 28, XH)]], scale=0.94)
g('x', [[(XL, XH), (XR, BASE)], [(XR, XH), (XL, BASE)]])
g('y', [[(XL - 5, XH), (CX, 30)], [(XR + 5, XH), (215, DESC)]])
g('z', [[(XL, XH), (XR, XH), (XL, BASE), (XR, BASE)]])

# ---- 数字 ----
g('0', [ell(CX, MID, 180, MID - BASE + OV), [(222, 215), (378, 485)]])
g('1', [[(160, 520), (CX, CAP), (CX, BASE)], [(150, BASE), (455, BASE)]])
g('2', [arc(300, 478, 172, CAP + OV - 478, 162, -42) + [(XL, BASE), (XR, BASE)]])
g('3', [arc(288, 502, 165, CAP + OV - 502, 152, -90) + arc(292, 186, 185, 186 - BASE + OV, 90, -152)])
g('4', [[(405, BASE), (405, CAP), (XL - 15, 205), (XR + 20, 205)]])
g('5', [[(455, CAP), (155, CAP), (137, 385)] + arc(288, 226, 187, 226 - BASE + OV, 128, -152)])
g('6', [ell(300, 226, 182, 226 - BASE + OV), arc(482, 226, 364, 440, 97, 180)])
g('7', [[(XL, CAP), (XR, CAP), (245, BASE)]])
g('8', [ell(300, 510, 152, CAP + OV - 510), ell(300, 192, 182, 192 - BASE + OV)])
g('9', [ell(300, 474, 182, CAP + OV - 474), arc(118, 474, 364, 440, 277, 360)])

# ---- 記号 ----
g('!', [[(CX, CAP), (CX, 235)]], dots=[(CX, 58)])
g('"', [[(222, ASC), (222, 530)], [(378, ASC), (378, 530)]])
g('#', [[(232, 640), (188, 60)], [(412, 640), (368, 60)], [(105, 460), (505, 460)], [(95, 240), (495, 240)]], scale=0.85)
g('$', [arc(300, 480, 160, 140, 25, 270) + arc(300, 206, 175, 134, 90, -155), [(CX, 750), (CX, -50)]], scale=0.9)
g('%', [ell(185, 540, 80, 108), ell(415, 160, 80, 108), [(478, 660), (122, 40)]], scale=0.8)
g('&', [[(492, BASE), (205, 455)] + arc(300, 524, 116, CAP + OV - 524, 216, -36)
        + arc(290, 200, 172, 200 - BASE + OV, 138, 392)], scale=0.92)
g("'", [[(CX, ASC), (CX, 530)]])
g('(', [arc(545, 270, 265, 500, 118, 242)])
g(')', mirror([arc(545, 270, 265, 500, 118, 242)]))
g('*', [[(300, 590), (300, 250)], [(153, 505), (447, 335)], [(153, 335), (447, 505)]], scale=0.9)
g('+', [[(CX, 120), (CX, 480)], [(120, 300), (480, 300)]])
g(',', [COMMA], dots=[(300, 58)])
g('-', [[(140, 300), (460, 300)]])
g('.', [], dots=[(300, 58)])
g('/', [[(472, 740), (128, -140)]])
g(':', [], dots=[(300, 58), (300, 410)])
g(';', [COMMA], dots=[(300, 58), (300, 410)])
g('<', [[(465, 530), (135, 300), (465, 70)]])
g('=', [[(125, 395), (475, 395)], [(125, 205), (475, 205)]])
g('>', mirror([[(465, 530), (135, 300), (465, 70)]]))
g('?', [arc(300, 500, 165, CAP + OV - 500, 165, -55) + [(300, 300), (300, 225)]], dots=[(300, 58)])
g('@', [[(425, 430), (425, 185)] + arc(466, 185, 41, 45, 180, 360) + arc(300, 280, 207, 392, 0, 312),
        ell(328, 300, 97, 135)], scale=0.8)
g('[', [[(410, 730), (245, 730), (245, -180), (410, -180)]])
g('\\', mirror([[(472, 740), (128, -140)]]))
g(']', mirror([[(410, 730), (245, 730), (245, -180), (410, -180)]]))
g('^', [[(150, 400), (300, 690), (450, 400)]])
g('_', [[(40, -140), (560, -140)]])
g('`', [[(225, 745), (345, 615)]])
BRACE = ([(425, 730), (360, 725)] + arc(365, 660, 60, 65, 100, 180) + [(305, 360)]
         + arc(245, 345, 60, 70, 0, -80) + [(200, 275)] + arc(245, 205, 60, 70, 80, 0)
         + [(305, -110)] + arc(365, -110, 60, 65, 180, 260) + [(425, -180)])
g('{', [BRACE])
g('|', [[(300, 760), (300, -200)]])
g('}', mirror([BRACE]))
g('~', [[(x, 300 + 68 * math.sin(2 * math.pi * (x - 120) / 360)) for x in range(120, 481, 12)]])


# ---- ブロック要素 (U+2580〜U+259F) ----
# ここから下 (ブロック要素・罫線・図形) は BLOCKS に「符号位置 -> 形のリスト」で入れる。
# ブロック要素は線ではなく、マス目 (横 0〜ADV、縦 DESCENT〜ASCENT) を分割した四角で描く。
# 隣のマスの四角とすき間なくつながるよう、マスの端に接する辺は BLEED だけ外へ出す。
BLEED = 10
CELL_H = ASCENT - DESCENT
BLOCKS = {}


def cell(x0, y0, x1, y1):
    """マス目の中の四角。座標は 0〜1 の割合 (左下が 0,0)。"""
    def edge(v, lo, size):
        return lo - BLEED if v <= 0 else lo + size + BLEED if v >= 1 else lo + size * v
    return rect(edge(x0, 0, ADV), edge(y0, DESCENT, CELL_H), edge(x1, 0, ADV), edge(y1, DESCENT, CELL_H))


def shade(keep, cols=12, rows=20):
    """濃淡。マス目を cols x rows の小さな四角に分け、keep(列, 行) が真のものだけ塗る。"""
    w, h = ADV / cols, CELL_H / rows
    return [rect(c * w, DESCENT + r * h, (c + 1) * w, DESCENT + (r + 1) * h)
            for c in range(cols) for r in range(rows) if keep(c, r)]


BLOCKS[0x2580] = [cell(0, 1 / 2, 1, 1)]                       # 上半分
for i in range(1, 9):                                          # 下 1/8 〜 全面
    BLOCKS[0x2580 + i] = [cell(0, 0, 1, i / 8)]
for i in range(1, 8):                                          # 左 7/8 〜 左 1/8
    BLOCKS[0x2588 + i] = [cell(0, 0, 1 - i / 8, 1)]
BLOCKS[0x2590] = [cell(1 / 2, 0, 1, 1)]                       # 右半分
BLOCKS[0x2591] = shade(lambda c, r: c % 2 == 0 and r % 2 == 0)        # 薄い
BLOCKS[0x2592] = shade(lambda c, r: (c + r) % 2 == 0)                 # 中間
BLOCKS[0x2593] = shade(lambda c, r: not (c % 2 == 1 and r % 2 == 1))  # 濃い
BLOCKS[0x2594] = [cell(0, 7 / 8, 1, 1)]                       # 上 1/8
BLOCKS[0x2595] = [cell(7 / 8, 0, 1, 1)]                       # 右 1/8
UL, UR = cell(0, 1 / 2, 1 / 2, 1), cell(1 / 2, 1 / 2, 1, 1)   # 4 分割: 左上・右上
LL, LR = cell(0, 0, 1 / 2, 1 / 2), cell(1 / 2, 0, 1, 1 / 2)   #          左下・右下
for i, quads in enumerate([[LL], [LR], [UL], [UL, LL, LR], [UL, LR], [UL, UR, LL],
                           [UL, UR, LR], [UR], [UR, LL], [UR, LL, LR]]):
    BLOCKS[0x2596 + i] = quads


# ---- 罫線 (U+2500〜U+257F) ----
# マス目の中心から上下左右へ伸びる「腕」の組み合わせで描く。腕はマスの端まで届き、隣のマスの線とつながる。
BCX, BCY = ADV / 2, (ASCENT + DESCENT) / 2    # マス目の中心
LIGHT = STROKE * 0.8 / 2    # 細い線の太さの半分
HEAVY = LIGHT * 2           # 太い線の太さの半分
GAP = 80                    # 二重線: マスの中心から、それぞれの線の中心まで
DIRS = {'u': (0, 1), 'd': (0, -1), 'l': (-1, 0), 'r': (1, 0)}
REACH = {'u': ASCENT - BCY, 'd': BCY - DESCENT, 'l': BCX, 'r': ADV - BCX}
SIDES = {'u': ('l', 'r', 'd'), 'd': ('l', 'r', 'u'), 'l': ('u', 'd', 'r'), 'r': ('u', 'd', 'l')}  # 両脇と反対側
HALF = {0: 0, 1: LIGHT, 2: HEAVY, 3: LIGHT}


def arm_rect(a, start, side, off, half):
    """腕 1 本ぶんの四角。向き a へ start から端まで。side の側へ off だけずらす (二重線用)。"""
    ax, ay = DIRS[a]
    ox, oy = DIRS[side]
    end = REACH[a] + BLEED
    x0, y0 = BCX + ax * start + ox * off, BCY + ay * start + oy * off
    x1, y1 = BCX + ax * end + ox * off, BCY + ay * end + oy * off
    if ax:
        return rect(min(x0, x1), y0 - half, max(x0, x1), y0 + half)
    return rect(x0 - half, min(y0, y1), x0 + half, max(y0, y1))


def box_lines(code):
    """code は上・下・左・右の腕の種類 (0 なし / 1 細い / 2 太い / 3 二重)。"""
    w = dict(zip('udlr', map(int, code)))
    shapes = []
    for a, kind in w.items():
        if not kind:
            continue
        p, q, o = SIDES[a]
        plain = -max(HALF[w[p]], HALF[w[q]])     # 脇の線の太さぶんだけ中心を越えて、角を埋める
        if kind == 3:
            for near, far in ((p, q), (q, p)):
                if w[near] == 3:                 # 内側の角
                    start = GAP - LIGHT
                elif w[far] == 3:                # 外側の角
                    start = -GAP - LIGHT
                else:
                    start = 0 if w[o] else plain
                shapes.append(arm_rect(a, start, near, GAP, LIGHT))
        else:
            doubles = (w[p] == 3) + (w[q] == 3)
            if doubles and w[o]:                 # 二重線を貫く
                start = 0
            elif doubles == 2:                   # 二重線の手前の線で止まる
                start = GAP - LIGHT
            elif doubles == 1:                   # 二重線の角: 奥の線まで届く
                start = -GAP - LIGHT
            else:
                start = plain
            shapes.append(arm_rect(a, start, a, 0, HALF[kind]))
    return shapes


def dashes(n, vertical, half):
    """破線。マスを n 等分し、それぞれの中央に線を置く (隣のマスとも同じ間隔で続く)。"""
    seg = (CELL_H if vertical else ADV) / n
    gap = seg * 0.3
    out = []
    for i in range(n):
        a, b = i * seg + gap / 2, (i + 1) * seg - gap / 2
        out.append(rect(BCX - half, DESCENT + a, BCX + half, DESCENT + b) if vertical
                   else rect(a, BCY - half, b, BCY + half))
    return out


BOX = {
    0x2500: '0011', 0x2501: '0022', 0x2502: '1100', 0x2503: '2200',
    0x250C: '0101', 0x250D: '0102', 0x250E: '0201', 0x250F: '0202',
    0x2510: '0110', 0x2511: '0120', 0x2512: '0210', 0x2513: '0220',
    0x2514: '1001', 0x2515: '1002', 0x2516: '2001', 0x2517: '2002',
    0x2518: '1010', 0x2519: '1020', 0x251A: '2010', 0x251B: '2020',
    0x251C: '1101', 0x251D: '1102', 0x251E: '2101', 0x251F: '1201',
    0x2520: '2201', 0x2521: '2102', 0x2522: '1202', 0x2523: '2202',
    0x2524: '1110', 0x2525: '1120', 0x2526: '2110', 0x2527: '1210',
    0x2528: '2210', 0x2529: '2120', 0x252A: '1220', 0x252B: '2220',
    0x252C: '0111', 0x252D: '0121', 0x252E: '0112', 0x252F: '0122',
    0x2530: '0211', 0x2531: '0221', 0x2532: '0212', 0x2533: '0222',
    0x2534: '1011', 0x2535: '1021', 0x2536: '1012', 0x2537: '1022',
    0x2538: '2011', 0x2539: '2021', 0x253A: '2012', 0x253B: '2022',
    0x253C: '1111', 0x253D: '1121', 0x253E: '1112', 0x253F: '1122',
    0x2540: '2111', 0x2541: '1211', 0x2542: '2211', 0x2543: '2121',
    0x2544: '2112', 0x2545: '1221', 0x2546: '1212', 0x2547: '2122',
    0x2548: '1222', 0x2549: '2221', 0x254A: '2212', 0x254B: '2222',
    0x2550: '0033', 0x2551: '3300',
    0x2552: '0103', 0x2553: '0301', 0x2554: '0303',
    0x2555: '0130', 0x2556: '0310', 0x2557: '0330',
    0x2558: '1003', 0x2559: '3001', 0x255A: '3003',
    0x255B: '1030', 0x255C: '3010', 0x255D: '3030',
    0x255E: '1103', 0x255F: '3301', 0x2560: '3303',
    0x2561: '1130', 0x2562: '3310', 0x2563: '3330',
    0x2564: '0133', 0x2565: '0311', 0x2566: '0333',
    0x2567: '1033', 0x2568: '3011', 0x2569: '3033',
    0x256A: '1133', 0x256B: '3311', 0x256C: '3333',
    0x2574: '0010', 0x2575: '1000', 0x2576: '0001', 0x2577: '0100',
    0x2578: '0020', 0x2579: '2000', 0x257A: '0002', 0x257B: '0200',
    0x257C: '0012', 0x257D: '1200', 0x257E: '0021', 0x257F: '2100',
}
for cp, code in BOX.items():
    BLOCKS[cp] = box_lines(code)
for cp, (n, vertical, half) in {
        0x2504: (3, False, LIGHT), 0x2505: (3, False, HEAVY), 0x2506: (3, True, LIGHT), 0x2507: (3, True, HEAVY),
        0x2508: (4, False, LIGHT), 0x2509: (4, False, HEAVY), 0x250A: (4, True, LIGHT), 0x250B: (4, True, HEAVY),
        0x254C: (2, False, LIGHT), 0x254D: (2, False, HEAVY), 0x254E: (2, True, LIGHT), 0x254F: (2, True, HEAVY)}.items():
    BLOCKS[cp] = dashes(n, vertical, half)

# 丸い角。下から来て右へ曲がる形を 1 つ書き、左右・上下に返して 4 つにする。
ROUND = 170
CORNER = ([(BCX, DESCENT - BLEED), (BCX, BCY - ROUND)] + arc(BCX + ROUND, BCY - ROUND, ROUND, ROUND, 180, 90)
          + [(ADV + BLEED, BCY)])


def flip(path, x=False, y=False):
    return [((2 * BCX - px) if x else px, (2 * BCY - py) if y else py) for px, py in path]


for cp, path in {0x256D: CORNER, 0x256E: flip(CORNER, x=True),
                 0x256F: flip(CORNER, x=True, y=True), 0x2570: flip(CORNER, y=True)}.items():
    BLOCKS[cp] = [LineString(path).buffer(LIGHT, quad_segs=6, cap_style='flat', join_style='round')]

# 斜めの線。マスの角から角へ引き、はみ出たぶんはマスの形で切り落とす (斜め隣のマスとつながる)。
CELL_BOX = rect(-BLEED, DESCENT - BLEED, ADV + BLEED, ASCENT + BLEED)


def diagonal(rising):
    dx, dy = ADV, CELL_H if rising else -CELL_H
    line = LineString([(BCX - dx, BCY - dy), (BCX + dx, BCY + dy)])
    return line.buffer(LIGHT, cap_style='flat').intersection(CELL_BOX)


BLOCKS[0x2571] = [diagonal(True)]
BLOCKS[0x2572] = [diagonal(False)]
BLOCKS[0x2573] = [diagonal(True), diagonal(False)]

# ---- 図形 (U+25A0〜U+25FF の主なもの) ----
# 1 マスの幅をほぼいっぱいに使う大きさで、英字の中ほどの高さ (GCY) を中心に置く。◯ だけは少しはみ出す。白抜きは黒い形の内側をくり抜く。
GCY = 330
EDGE = STROKE * 0.85        # 白抜きの線の太さ
THIN = 56                   # 小さい図形の白抜きの線の太さ


def white(shape, width=EDGE):
    return shape.difference(shape.buffer(-width, join_style='mitre'))


def square(w, h=None):
    h = h or w
    return rect(BCX - w / 2, GCY - h / 2, BCX + w / 2, GCY + h / 2)


def disc(r):
    return Point(BCX, GCY).buffer(r, quad_segs=16)


def poly(points):
    return Polygon([(BCX + x, GCY + y) for x, y in points])


def tri(d, a, b):
    """三角。d は尖る向き、a は底辺の半分、b は高さの半分。"""
    pts = [(-a, -b), (a, -b), (0, b)]
    turn = {'u': lambda x, y: (x, y), 'd': lambda x, y: (x, -y), 'r': lambda x, y: (y, x), 'l': lambda x, y: (-y, x)}[d]
    return poly([turn(x, y) for x, y in pts])


def half_plane(side):
    far = 3000
    return {'l': rect(-far, -far, BCX, far), 'r': rect(BCX, -far, far, far),
            'd': rect(-far, -far, far, GCY), 'u': rect(-far, GCY, far, far)}[side]


BIG, SMALL, POINTER = (290, 285), (185, 180), (195, 290)
DIAMOND = [(0, 310), (290, 0), (0, -310), (-290, 0)]
SHAPES = {
    0x25A0: square(560), 0x25A1: white(square(560)),
    0x25AA: square(300), 0x25AB: white(square(300), THIN),
    0x25AC: square(580, 260), 0x25AD: white(square(580, 260), THIN),
    0x25AE: square(300, 680), 0x25AF: white(square(300, 680), THIN),
    0x25C6: poly(DIAMOND), 0x25C7: white(poly(DIAMOND)),
    0x25C8: unary_union([white(poly(DIAMOND), THIN), poly([(x * 0.42, y * 0.42) for x, y in DIAMOND])]),
    0x25C9: unary_union([white(disc(280), THIN), disc(130)]),
    0x25CA: white(poly([(0, 340), (210, 0), (0, -340), (-210, 0)]), THIN),
    0x25CB: white(disc(280)),
    0x25CC: unary_union([Point(BCX + 250 * math.cos(math.radians(a)), GCY + 250 * math.sin(math.radians(a))).buffer(40, quad_segs=6)
                         for a in range(0, 360, 45)]),
    0x25CE: unary_union([white(disc(280), THIN), white(disc(150), THIN)]),
    0x25CF: disc(280),
    0x25D6: disc(280).intersection(half_plane('l')), 0x25D7: disc(280).intersection(half_plane('r')),
    0x25E2: poly([(-280, -280), (280, -280), (280, 280)]), 0x25E3: poly([(-280, -280), (280, -280), (-280, 280)]),
    0x25E4: poly([(-280, 280), (280, 280), (-280, -280)]), 0x25E5: poly([(-280, 280), (280, 280), (280, -280)]),
    0x25E6: white(disc(140), THIN),
    0x25EF: white(disc(330)),
    0x25FB: white(square(460), THIN), 0x25FC: square(460),
    0x25FD: white(square(370), THIN), 0x25FE: square(370),
    # 再生・一時停止の印 (端末のアプリが状態の表示に使う)
    0x23F5: tri('r', 230, 225),
    0x23F8: unary_union([rect(BCX - 200, GCY - 240, BCX - 60, GCY + 240), rect(BCX + 60, GCY - 240, BCX + 200, GCY + 240)]),
}
for i, side in enumerate('lrdu'):               # 半分だけ塗った丸
    SHAPES[0x25D0 + i] = unary_union([white(disc(280), THIN), disc(280).intersection(half_plane(side))])
for i, d in enumerate('urdl'):                   # 三角: 上・右・下・左の順に、黒/白・小さい黒/白・(横向きは) 細長い黒/白
    base = (0x25B2, 0x25B6, 0x25BC, 0x25C0)[i]
    SHAPES[base], SHAPES[base + 1] = tri(d, *BIG), white(tri(d, *BIG))
    SHAPES[base + 2], SHAPES[base + 3] = tri(d, *SMALL), white(tri(d, *SMALL), THIN)
    if d in 'rl':
        SHAPES[base + 4], SHAPES[base + 5] = tri(d, *POINTER), white(tri(d, *POINTER), THIN)
for cp, shape in SHAPES.items():
    BLOCKS[cp] = [shape]


def block_outline(shapes):
    merged = unary_union(shapes)
    return [g for g in getattr(merged, 'geoms', [merged]) if g.geom_type == 'Polygon' and not g.is_empty]


def outline(paths, dots, scale):
    half = H * scale
    shapes = [LineString(p).buffer(half, quad_segs=6, cap_style='round', join_style='round')
              for p in paths if len(p) > 1]
    shapes += [Point(x, y).buffer(DOT * (1.0 if scale == 1.0 else max(scale, 0.9)), quad_segs=8) for x, y in dots]
    if not shapes:
        return []
    merged = unary_union(shapes).simplify(0.8)
    return list(merged.geoms) if merged.geom_type == 'MultiPolygon' else [merged]


def draw(polys):
    pen = TTGlyphPen(None)
    for poly in polys:
        poly = orient(poly, sign=-1.0)   # TrueType: 外周は時計回り、穴は反時計回り
        for ring in [poly.exterior, *poly.interiors]:
            pts = [(round(x), round(y)) for x, y in ring.coords[:-1]]
            pen.moveTo(pts[0])
            for pt in pts[1:]:
                pen.lineTo(pt)
            pen.closePath()
    return pen.glyph()


def notdef():
    pen = TTGlyphPen(None)
    for box in ([(100, 0), (100, 700), (500, 700), (500, 0)], [(160, 60), (440, 60), (440, 640), (160, 640)]):
        pen.moveTo(box[0])
        for pt in box[1:]:
            pen.lineTo(pt)
        pen.closePath()
    return pen.glyph()


def main(out):
    names = {ch: ('space' if ch == ' ' else 'uni%04X' % ord(ch)) for ch in G}
    order = ['.notdef'] + [names[ch] for ch in G]
    glyphs = {'.notdef': notdef()}
    for ch, (paths, dots, scale) in G.items():
        glyphs[names[ch]] = draw(outline(paths, dots, scale))
    cmap = {ord(ch): names[ch] for ch in G}
    for cp, shapes in BLOCKS.items():
        name = 'uni%04X' % cp
        order.append(name)
        cmap[cp] = name
        glyphs[name] = draw(block_outline(shapes))

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    glyf = fb.font['glyf']
    fb.setupHorizontalMetrics({n: (ADV, glyf[n].xMin if glyf[n].numberOfContours else 0) for n in order})
    fb.setupHorizontalHeader(ascent=ASCENT, descent=DESCENT)
    fb.setupNameTable({
        'familyName': 'Z2 Mono', 'styleName': 'Regular',
        'copyright': 'Copyright (c) 2026 Zero to Ship',
        'licenseDescription': 'This Font Software is licensed under the SIL Open Font License, Version 1.1.',
        'licenseInfoURL': 'https://openfontlicense.org',
    })
    fb.setupOS2(sTypoAscender=ASCENT, sTypoDescender=DESCENT, sTypoLineGap=0,
                usWinAscent=ASCENT, usWinDescent=-DESCENT, xAvgCharWidth=ADV,
                sxHeight=500, sCapHeight=700)
    fb.setupPost(isFixedPitch=1)
    # 作った日時を埋め込まない。同じスクリプトからは、いつ作っても同じファイルになる。
    fb.font['head'].created = fb.font['head'].modified = 0
    fb.font.recalcTimestamp = False
    fb.save(out)
    pts = sum(len(glyf[n].coordinates) for n in order if glyf[n].numberOfContours > 0)
    print(f'{out}: {len(order) - 1} glyphs, {pts} points, stroke {STROKE:g}')


if __name__ == '__main__':
    main(sys.argv[1])
