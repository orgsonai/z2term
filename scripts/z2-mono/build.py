#!/usr/bin/env python3
"""Z2 Mono — 英字・数字・記号を線の定義から自動で描いて TTF にする。

字の形は「線の通り道」(折れ線と円弧) だけで定義し、同じ太さ・同じ丸みで肉付けする。
既存フォントの輪郭は一切使っていない。

使い方:  build.py <出力.ttf> [太さ]
"""
import math
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import LineString, Point
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

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({ord(ch): names[ch] for ch in G})
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
