#!/usr/bin/env python3
"""共起 2-gram 抽出 + Bloom フィルタ生成 (build-collocation.sh から呼ばれる)。

stdin = 展開済み Wikipedia テキスト (bzcat の出力)。
内容語核 (漢字/カタカナ連続) を切り出し、ひらがな glue 1〜4 文字をはさんで隣接する
核ペアを数え、頻度カット後に Bloom フィルタへ詰めて OUT に書く。

Bloom フィルタ形式 (リトルエンディアン):
    magic  : "KCB1" (4 bytes)
    m_bits : uint32  ビット数
    k      : uint32  ハッシュ本数
    n      : uint32  登録ペア数
    bits   : ceil(m_bits/8) bytes

ハッシュ: FNV-1a 64bit を 1 回計算し、上下 32bit を 2 ハッシュとみなす二重ハッシュ
(Kirsch-Mitzenmacher)。Kotlin 側 ExistenceFilter と完全一致させること。
"""
import os
import sys
import math
import struct

CHAR_LIMIT = int(os.environ.get("CHAR_LIMIT", "120000000"))
MIN_COUNT = int(os.environ.get("MIN_COUNT", "4"))
FP_RATE = float(os.environ.get("FP_RATE", "0.02"))
OUT = os.environ["OUT"]
PRUNE_CAP = int(os.environ.get("PRUNE_CAP", "6000000"))
MAX_GLUE = 4  # 核間に許すひらがな glue の最大長

FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASK64 = 0xFFFFFFFFFFFFFFFF


def fnv1a64(data: bytes) -> int:
    h = FNV_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV_PRIME) & MASK64
    return h


def is_content(ch: str) -> bool:
    o = ord(ch)
    return (
        0x4E00 <= o <= 0x9FFF   # CJK 統合漢字
        or 0x3400 <= o <= 0x4DBF  # 拡張A
        or 0x30A1 <= o <= 0x30FA  # カタカナ
        or o == 0x30FC            # 長音符 ー
        or o == 0x3005            # 々
    )


def is_hira(ch: str) -> bool:
    o = ord(ch)
    return 0x3041 <= o <= 0x3096 or o == 0x3094  # ゔ


def main() -> None:
    counter: dict[str, int] = {}
    total_chars = 0
    stop = False
    stdin = sys.stdin.buffer

    for raw in stdin:
        try:
            line = raw.decode("utf-8", "ignore")
        except Exception:
            continue
        total_chars += len(line)

        prev = None      # 直前の内容語核 (ペア左)
        cur = []         # 構築中の核
        glue = 0         # prev からのひらがな glue 長 (-1 = 連鎖断)
        for ch in line:
            if is_content(ch):
                cur.append(ch)
            else:
                if cur:
                    core = "".join(cur)
                    cur = []
                    if prev is not None and 1 <= glue <= MAX_GLUE:
                        key = prev + "\t" + core
                        counter[key] = counter.get(key, 0) + 1
                    prev = core
                    glue = 0
                if is_hira(ch):
                    if prev is not None and glue >= 0:
                        glue += 1
                        if glue > MAX_GLUE:
                            prev = None
                            glue = -1
                else:
                    # 約物/英数/空白 = 文節境界
                    prev = None
                    glue = -1
        # 行末に核が残っていても次語が無いのでペアにはしない

        if len(counter) > PRUNE_CAP:
            counter = {k: v for k, v in counter.items() if v > 1}
            sys.stderr.write(f"[prune] size->{len(counter)} chars={total_chars}\n")
            sys.stderr.flush()

        if total_chars >= CHAR_LIMIT:
            stop = True
            break

    # 頻度カット
    kept = [k for k, v in counter.items() if v >= MIN_COUNT]
    n = len(kept)
    sys.stderr.write(
        f"[extract] chars={total_chars} stopped={stop} distinct={len(counter)} kept(n>={MIN_COUNT})={n}\n"
    )
    if n == 0:
        sys.exit("[error] no pairs extracted")

    # Bloom パラメータ
    m_bits = int(math.ceil(-n * math.log(FP_RATE) / (math.log(2) ** 2)))
    m_bits = ((m_bits + 7) // 8) * 8  # 8 の倍数に丸める
    k = max(1, min(16, int(round(m_bits / n * math.log(2)))))
    bits = bytearray(m_bits // 8)

    for key in kept:
        h = fnv1a64(key.encode("utf-8"))
        a = h & 0xFFFFFFFF
        b = (h >> 32) & 0xFFFFFFFF
        if b == 0:
            b = 1
        for i in range(k):
            pos = (a + i * b) % m_bits
            bits[pos >> 3] |= (1 << (pos & 7))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(b"KCB1")
        f.write(struct.pack("<III", m_bits, k, n))
        f.write(bytes(bits))

    sys.stderr.write(
        f"[bloom] n={n} m_bits={m_bits} ({m_bits // 8} bytes) k={k} "
        f"file={os.path.getsize(OUT)} bytes\n"
    )


if __name__ == "__main__":
    main()
