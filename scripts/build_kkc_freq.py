#!/usr/bin/env python3
"""かな漢字変換の頻度補正データ (kkc_freq.tsv) を作る。

    build_kkc_freq.py extract <文字数の上限> <出力.txt>      標準入力のウィキペディア XML から文を取り出す
    build_kkc_freq.py count   <文.txt> <出力.tsv> [並列数]   文を単語に分けて (読み, 表層, 品詞 ID) ごとに数える
    build_kkc_freq.py build   <数.tsv> <kkc_lex.tsv> <出力>  数から補正データを作る

なぜ要るか: kkc_lex.tsv (IPADIC) の単語コストは「文章を単語に区切る」ためのもので、同じ読みの
語を選び分けるのには向かない (かみ: 加味 < 髪 < 神 < 紙、ひと: ひと < 人)。実際の文章で
どの語が多いかを数え、少ない語を後ろへ回す。

補正の決め方 (build):
  - 比べるのは**同じ読み・同じ品詞 ID (左文脈 ID)** の語どうしだけ。品詞をまたぐと接続コストとの
    釣り合いが崩れ、別の品詞の語まで巻き込んで壊れる (試して確かめた)。
  - その中でいちばん多い語 (全部ひらがなの語は除いて選ぶ) を基準にし、それより少ない語のコストを
    「基準の語の元コスト + K * ln(基準の数 / その語の数)」まで**引き上げる**。上限は CAP。
  - **どの語も安くはしない。** 安くすると、その読みの最安コストが下がり、別の品詞の正しい語に
    勝ってしまう (こうしん: 更新 → 後身、さいきん: 最近 → 細菌 になった)。
  - 基準の語が MIN_TOP 回未満しか出ない読みは、数が足りないので触らない。

出力は `読み<TAB>表層<TAB>品詞ID<TAB>足すコスト` を読み順に並べたもの。KkcConverter が読み込み時に足す。
count には mecab-python3 と ipadic (どちらも pip) が要る。アプリには入らない。
"""
import collections
import math
import re
import sys

K = 700          # 数の比 (自然対数) 1 あたりのコスト
SMOOTH = 0.5     # 0 回の語を扱うために数へ足す値
CAP = 4000       # 引き上げ幅の上限
MIN_TOP = 5      # 基準の語がこれ未満なら、その読み・品詞は触らない


def extract(limit, out_path):
    link = re.compile(r'\[\[(?:[^\]|]*\|)?([^\]]*)\]\]')
    tmpl = re.compile(r'\{\{[^{}]*\}\}')
    tag = re.compile(r'<[^>]*>|&lt;.*?&gt;|&[a-z]+;')
    quote = re.compile(r"'{2,}")
    n = 0
    with open(out_path, 'w', encoding='utf-8') as out:
        for raw in sys.stdin.buffer:
            line = raw.decode('utf-8', 'ignore').strip()
            if '。' not in line or line[:1] in '<{|!*#:=[':
                continue
            for _ in range(2):
                line = tmpl.sub('', line)
            line = quote.sub('', tag.sub('', link.sub(r'\1', line)))
            for s in line.split('。'):
                s = s.strip()
                if 8 <= len(s) <= 200 and '{' not in s and '[' not in s and '|' not in s:
                    out.write(s + '\n')
                    n += len(s)
            if n >= limit:
                break
    sys.stderr.write(f'[extract] chars={n}\n')


def to_hira(s):
    return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)


def count_part(args):
    path, start, step = args
    import MeCab
    import ipadic
    tagger = MeCab.Tagger(ipadic.MECAB_ARGS)
    tagger.parse('')
    cnt = collections.Counter()
    with open(path, encoding='utf-8') as f:
        for i, line in enumerate(f):
            if i % step != start:
                continue
            node = tagger.parseToNode(line.strip())
            while node:
                if node.surface:
                    feat = node.feature.split(',')
                    if len(feat) > 7 and feat[7] != '*':
                        cnt[(to_hira(feat[7]), node.surface, node.lcAttr)] += 1
                node = node.next
    return cnt


def count(text_path, out_path, workers):
    import multiprocessing
    with multiprocessing.Pool(workers) as pool:
        parts = pool.map(count_part, [(text_path, i, workers) for i in range(workers)])
    total = collections.Counter()
    for p in parts:
        total.update(p)
    with open(out_path, 'w', encoding='utf-8') as out:
        for (r, s, lc), n in sorted(total.items()):
            if '\t' not in r and '\t' not in s:
                out.write(f'{r}\t{s}\t{lc}\t{n}\n')
    sys.stderr.write(f'[count] tokens={sum(total.values())} types={len(total)}\n')


def all_hira(s):
    return all('ぁ' <= c <= 'ゖ' or c == 'ー' for c in s)


def build(count_path, lex_path, out_path):
    cnt = {}
    with open(count_path, encoding='utf-8') as f:
        for line in f:
            r, s, lc, n = line.rstrip('\n').split('\t')
            cnt[(r, s, int(lc))] = int(n)
    groups = collections.defaultdict(list)      # (読み, 品詞 ID) -> [(表層, 元コスト)]
    with open(lex_path, encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) < 5:
                continue
            try:
                groups[(p[0], int(p[2]))].append((p[1], int(p[4])))
            except ValueError:
                continue
    rows = {}
    for (r, lc), entries in groups.items():
        if len(entries) < 2:
            continue
        cand = [(cnt.get((r, s, lc), 0), -cost) for s, cost in entries if not all_hira(s)]
        if not cand:
            continue
        top, neg_cost = max(cand)
        if top < MIN_TOP:
            continue
        for s, cost in entries:
            n = cnt.get((r, s, lc), 0)
            raised = -neg_cost + min(CAP, K * (math.log(top + SMOOTH) - math.log(n + SMOOTH)))
            delta = int(max(cost, raised)) - cost
            if delta > 0:
                # 同じ (読み, 表層, 品詞 ID) が複数行あるときは、行ごとの元コストで幅が変わる。
                # 読み込み側は 3 つ組で引くので、小さいほう (控えめなほう) を採る。
                key = (r, s, lc)
                rows[key] = min(rows.get(key, delta), delta)
    with open(out_path, 'w', encoding='utf-8') as out:
        out.write('# かな漢字変換の頻度補正 (読み, 表層, 品詞 ID, 足すコスト)。scripts/build-kkc-freq.sh が作る。\n')
        out.write('# 日本語版ウィキペディア (CC BY-SA 4.0) の文章で語を数えた結果から作った。原文は含まない。\n')
        for (r, s, lc), d in sorted(rows.items()):
            out.write(f'{r}\t{s}\t{lc}\t{d}\n')
    sys.stderr.write(f'[build] rows={len(rows)}\n')


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'extract':
        extract(int(sys.argv[2]), sys.argv[3])
    elif cmd == 'count':
        count(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 2)
    elif cmd == 'build':
        build(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        sys.exit(__doc__)
