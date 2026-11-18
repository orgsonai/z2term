#!/usr/bin/env python3
"""かな漢字変換の頻度補正データ (kkc_freq.tsv) と、文の中で使う地名・人名 (kkc_names.tsv) を作る。

    build_kkc_freq.py extract <文字数の上限> <出力.txt>      標準入力のウィキペディア XML から文を取り出す
    build_kkc_freq.py count   <文.txt> <出力.tsv> [並列数] [固有名詞の出力.tsv] [補完用の語の出力.tsv] [語の続きの出力.tsv]
                                                             文を単語に分けて (読み, 表層, 品詞 ID) ごとに数える
    build_kkc_freq.py build   <数.tsv> <kkc_lex.tsv> <出力>  数から補正データを作る
    build_kkc_freq.py names   <固有名詞.tsv> <kkc_lex.tsv> <出力>  よく出る地名・人名を選ぶ
    build_kkc_freq.py predict <補完用の語.tsv> <固有名詞.tsv> <出力>  打ちかけの補完に使う、よく使われる語の表を作る
    build_kkc_freq.py next    <語の続き.tsv> <出力>  確定した語の次に出す語の表を作る

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

地名・人名 (names): kkc_lex.tsv は固有名詞を外してあるので、文の中では 東京・大阪・田中 が変換できない
(とうきょうへいきます → 当供へ行きます)。IPADIC の固有名詞のうち、文章に NAME_MIN 回以上出るものだけを
kkc_lex.tsv と同じ形 (読み・表層・左文脈 ID・右文脈 ID・単語コスト) で書き出す。ID とコストは IPADIC の
値そのまま (MeCab が返したもの)。回数で絞るのは、珍しい名前がふつうの語を押しのけないようにするため。
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
NAME_MIN = 300   # 地名・人名はこの回数以上出るものだけ採る (6000 万字あたり)
PREDICT_MIN = 10     # 補完に出す語はこの回数以上
PREDICT_MAX = 50000  # 補完の表の行数の上限 (多い順に採る)
PREDICT_MIN_LEN = 2  # 補完は読み 1 文字から出すので、表に要るのは読み 2 文字以上の語
FORM_MIN = 60        # 活用した形を作る語は、言い切りの形がこの回数以上出るものだけ
FORM_BASE_MAX = 400  # 活用した形の回数は、元の語の回数をここで頭打ちにして計算する (ある → あります が ありがとう を押しのけないように)
SPOKEN_COUNT = 100   # SPOKEN_WORDS の回数
NEXT_KEY_MIN = 30    # 続きの表に載せる語は、この回数以上続きが数えられたものだけ
NEXT_MIN = 6         # 続きの語はこの回数以上
NEXT_SHARE = 0.02    # 続きの語は、その語の続き全体のこの割合以上
NEXT_TOP = 5         # 1 語につき載せる続きの数
NEXT_MAX = 30000     # 続きの表の行数の上限 (多い順に採る)
NEXT_CHUNK_LEN = 10  # 語のまとまりはこの文字数まで
PREDICT_NAME_WEIGHT = 0.1   # 地名・人名は回数をこの割合に減らして並べる (百科事典では多すぎるため)


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
    names = collections.Counter()
    words = collections.Counter()
    pairs = collections.Counter()
    for i, line in enumerate(open(path, encoding='utf-8')):
            if i % step != start:
                continue
            if i % 400000 == start:      # 1 回しか出ていない組を捨てて、メモリを抑える
                pairs = collections.Counter({k: v for k, v in pairs.items() if v > 1})
            node = tagger.parseToNode(line.strip())
            sentence = []
            while node:
                if node.surface:
                    feat = node.feature.split(',')
                    sentence.append((node.surface, feat))
                    if len(feat) > 7 and feat[7] != '*':
                        reading = to_hira(feat[7])
                        cnt[(reading, node.surface, node.lcAttr)] += 1
                        if feat[0] == '名詞' and feat[1] == '固有名詞':
                            names[(reading, node.surface, node.lcAttr, node.rcAttr, node.wcost)] += 1
                        if completes(feat, node.surface):
                            words[(reading, node.surface, word_kind(feat), feat[4])] += 1
                node = node.next
            count_pairs(sentence, pairs)
    return cnt, names, words, pairs


COMPLETE_NOUNS = {'一般', 'サ変接続', '形容動詞語幹', '副詞可能', '固有名詞', 'ナイ形容詞語幹'}   # 問題・申し訳 は最後のもの


def completes(feat, surface):
    """打ちかけの補完に出してよい語か。単独で 1 語として確定できるものだけ。"""
    if feat[0] == '名詞':
        return feat[1] in COMPLETE_NOUNS
    if feat[0] in ('副詞', '感動詞', '接続詞', '連体詞'):
        return True
    if feat[0] in ('動詞', '形容詞'):      # 活用語は言い切りの形だけ (行き・行っ は補完にしない)
        return feat[1] == '自立' and len(feat) > 6 and feat[6] == surface
    return False


def word_kind(feat):
    """活用した形を作るための語の種類。v = 動詞、a = 形容詞、s = サ変名詞 (確認 → 確認します)、o = その他。"""
    if feat[0] == '動詞':
        return 'v'
    if feat[0] == '形容詞':
        return 'a'
    if feat[0] == '名詞' and feat[1] == 'サ変接続':
        return 's'
    return 'o'


def attaches(feat):
    """前の語に付いて 1 つのまとまりになる語か (助詞・助動詞・補助の動詞・接尾語)。"""
    return (feat[0] in ('助詞', '助動詞') or (feat[0] == '動詞' and feat[1] in ('非自立', '接尾'))
            or (feat[0] == '名詞' and feat[1] in ('接尾', '非自立')))


def count_pairs(sentence, pairs):
    """語 → 付く語 (今日 → は、あり → ません) と、まとまり → 次のまとまり (今日は → いい) を数える。

    記号・数字・英字で文の流れを切る。
    """
    chunks = []                                  # [自立語, 付いた語]
    for surface, feat in sentence + [('', ['記号'])]:
        if feat[0] == '記号' or not surface or not all(is_word_char(c) for c in surface):
            flush_chunks(chunks, pairs)
            chunks = []
        elif attaches(feat) and chunks:
            chunks[-1][1] += surface
        else:
            chunks.append([surface, ''])


def flush_chunks(chunks, pairs):
    for i, (head, tail) in enumerate(chunks):
        if tail and len(tail) <= NEXT_CHUNK_LEN:
            pairs[f'{head}\t{tail}'] += 1
        if i + 1 < len(chunks):
            whole, following = head + tail, ''.join(chunks[i + 1])
            if len(whole) <= NEXT_CHUNK_LEN and len(following) <= NEXT_CHUNK_LEN:
                pairs[f'{whole}\t{following}'] += 1


def count(text_path, out_path, workers, names_path=None, words_path=None, next_path=None):
    import multiprocessing
    with multiprocessing.Pool(workers) as pool:
        parts = pool.map(count_part, [(text_path, i, workers) for i in range(workers)])
    total = collections.Counter()
    names = collections.Counter()
    words = collections.Counter()
    pairs = collections.Counter()
    for p, n, w, b in parts:
        total.update(p)
        names.update(n)
        words.update(w)
        pairs.update(b)
    if next_path:
        with open(next_path, 'w', encoding='utf-8') as out:
            for k, n in sorted(pairs.items()):
                out.write(f'{k}\t{n}\n')
    if words_path:
        with open(words_path, 'w', encoding='utf-8') as out:
            for (r, s, kind, ctype), n in sorted(words.items()):
                if '\t' not in r and '\t' not in s:
                    out.write(f'{r}\t{s}\t{n}\t{kind}\t{ctype}\n')
    if names_path:
        with open(names_path, 'w', encoding='utf-8') as out:
            for (r, s, lc, rc, cost), n in sorted(names.items()):
                if '\t' not in r and '\t' not in s:
                    out.write(f'{r}\t{s}\t{lc}\t{rc}\t{cost}\t{n}\n')
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


def build_names(names_path, lex_path, out_path):
    have = set()                                 # kkc_lex.tsv に既にある (読み, 表層, 左文脈 ID)
    with open(lex_path, encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) >= 5:
                have.add((p[0], p[1], p[2]))
    rows = []
    with open(names_path, encoding='utf-8') as f:
        for line in f:
            r, s, lc, rc, cost, n = line.rstrip('\n').split('\t')
            # 1 文字の読みと、読みそのままの表層 (ひらがなの名前) は採らない。
            if int(n) < NAME_MIN or len(r) < 2 or s == r or (r, s, lc) in have:
                continue
            rows.append((r, s, int(lc), int(rc), int(cost)))
    with open(out_path, 'w', encoding='utf-8') as out:
        out.write('# 文の中で使う地名・人名 (読み, 表層, 左文脈 ID, 右文脈 ID, 単語コスト)。scripts/build-kkc-freq.sh が作る。\n')
        out.write('# 中身は IPADIC (mecab-ipadic 2.7.0-20070801) の固有名詞。よく出るものだけを選んである。\n')
        for r, s, lc, rc, cost in sorted(rows):
            out.write(f'{r}\t{s}\t{lc}\t{rc}\t{cost}\n')
    sys.stderr.write(f'[names] rows={len(rows)}\n')


def is_word_char(c):
    o = ord(c)
    return (0x3041 <= o <= 0x3096 or 0x30A1 <= o <= 0x30FA or o in (0x30FC, 0x3005)
            or 0x4E00 <= o <= 0x9FFF or 0x3400 <= o <= 0x4DBF)


# 文章にほとんど出ない話し言葉 (読み, 表層)。自作の短い一覧。
SPOKEN_WORDS = [
    ('ごめんなさい', 'ごめんなさい'), ('すみません', 'すみません'), ('こんにちは', 'こんにちは'),
    ('こんばんは', 'こんばんは'), ('さようなら', 'さようなら'), ('おはよう', 'おはよう'),
    ('おやすみ', 'おやすみ'), ('もしもし', 'もしもし'), ('なるほど', 'なるほど'),
    ('やっぱり', 'やっぱり'), ('ちょっと', 'ちょっと'), ('たぶん', '多分'),
]


# 五段活用の語尾: 活用の種類 -> (連用形, て形, た形, 未然形)
GODAN = {
    '五段・カ行イ音便': ('き', 'いて', 'いた', 'か'),
    '五段・カ行促音便': ('き', 'って', 'った', 'か'),
    '五段・カ行促音便ユク': ('き', 'って', 'った', 'か'),
    '五段・ガ行': ('ぎ', 'いで', 'いだ', 'が'),
    '五段・サ行': ('し', 'して', 'した', 'さ'),
    '五段・タ行': ('ち', 'って', 'った', 'た'),
    '五段・ナ行': ('に', 'んで', 'んだ', 'な'),
    '五段・バ行': ('び', 'んで', 'んだ', 'ば'),
    '五段・マ行': ('み', 'んで', 'んだ', 'ま'),
    '五段・ラ行': ('り', 'って', 'った', 'ら'),
    '五段・ラ行特殊': ('い', 'って', 'った', 'ら'),
    '五段・ワ行促音便': ('い', 'って', 'った', 'わ'),
}


def verb_stems(r, s, ctype):
    """動詞の (読みの語幹, 表層の語幹, 連用形, て形, た形, 未然形)。作れない活用は None。"""
    if ctype in GODAN:
        return (r[:-1], s[:-1]) + GODAN[ctype] if s[-1] == r[-1] else None
    if ctype == '一段' and s[-1] == 'る' and r[-1] == 'る':
        return (r[:-1], s[:-1], '', 'て', 'た', '')
    if ctype in ('サ変・スル', 'サ変・−スル') and s.endswith('する') and r.endswith('する'):
        return (r[:-2], s[:-2], 'し', 'して', 'した', 'し')
    return None


def conjugated_forms(r, s, n, kind, ctype):
    """よく使う活用した形 (読み, 表層, 回数)。回数は言い切りの形の回数に割合を掛けたもの。"""
    if kind == 'v':
        stems = verb_stems(r, s, ctype)
        if not stems:
            return []
        rs, ss, renyo, te, ta, mizen = stems
        forms = [(renyo + 'ます', .25), (renyo + 'ました', .2), (renyo + 'ません', .1), (renyo + 'ましょう', .05),
                 (renyo + 'たい', .05), (te, .3), (te + 'います', .1), (ta, .3)]
        if r != 'ある':                          # あらない とは言わない
            forms.append((mizen + 'ない', .15))
    elif kind == 'a' and s[-1] == 'い' and r[-1] == 'い':
        rs, ss = r[:-1], s[:-1]
        forms = [('かった', .3), ('く', .3), ('くて', .2), ('くない', .1), ('いです', .1)]
    elif kind == 's':
        rs, ss = r, s
        forms = [('します', .2), ('しました', .15), ('して', .25), ('した', .2), ('してください', .05), ('しています', .08)]
    else:
        return []
    return [(rs + tail, ss + tail, int(min(n, FORM_BASE_MAX) * rate)) for tail, rate in forms]


def build_predict(words_path, names_path, out_path):
    """補完用の表 (kkc_predict.tsv)。読み・表層・回数を読み順に並べる。

    打った読みで始まる語を多い順に出すためのもの。単独で 1 語として確定できる語
    (名詞・副詞・感動詞・接続詞・連体詞と、動詞・形容詞の言い切りの形) だけを数えてある。
    記号・英数字を含む表層と、読みが PREDICT_MIN_LEN 文字未満の語は採らない。
    動詞・形容詞・サ変名詞は、よく使う活用した形も作って足す (分かる → 分かりました)。文章には
    丁寧な言い方がほとんど出ないので、数えるのではなく活用の規則で作る。
    地名・人名は回数を PREDICT_NAME_WEIGHT 倍にして並べる。そのままだと あり → 有馬、
    いそ → 磯部、すこ → スコットランド が、ふだんの語より上に来る。
    """
    proper = collections.Counter()               # (読み, 表層) -> 固有名詞として出た回数
    with open(names_path, encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            proper[(p[0], p[1])] += int(p[5])
    best = {}
    def add(r, s, n):
        if n >= PREDICT_MIN and len(r) >= PREDICT_MIN_LEN and n > best.get((r, s), 0):
            best[(r, s)] = n
    with open(words_path, encoding='utf-8') as f:
        for line in f:
            r, s, n, kind, ctype = line.rstrip('\n').split('\t')
            n = int(n)
            name_n = min(n, proper.get((r, s), 0))
            n = int((n - name_n) + name_n * PREDICT_NAME_WEIGHT)
            if not all('ぁ' <= c <= 'ゖ' or c == 'ー' for c in r) or not all(is_word_char(c) for c in s):
                continue
            add(r, s, n)
            if n >= FORM_MIN:
                for fr, fs, fn in conjugated_forms(r, s, n, kind, ctype):
                    add(fr, fs, fn)
    for r, s in SPOKEN_WORDS:
        add(r, s, SPOKEN_COUNT)
    rows = [(n, r, s) for (r, s), n in best.items()]
    rows.sort(key=lambda x: (-x[0], x[1], x[2]))
    rows = rows[:PREDICT_MAX]
    with open(out_path, 'w', encoding='utf-8') as out:
        out.write('# 打ちかけの補完に使う、よく使われる語 (読み, 表層, 回数)。scripts/build-kkc-freq.sh が作る。\n')
        out.write('# 日本語版ウィキペディア (CC BY-SA 4.0) の文章で語を数えた結果から作った。原文は含まない。\n')
        for n, r, s in sorted(rows, key=lambda x: (x[1], -x[0], x[2])):
            out.write(f'{r}\t{s}\t{n}\n')
    sys.stderr.write(f'[predict] rows={len(rows)}\n')


def build_next(pairs_path, out_path):
    """確定した語の次に出す語の表 (kkc_next.tsv)。`語<TAB>続き1<TAB>続き2…` を語の順に並べる。

    続きはひらがなだけの語に限る (今日 → の・では、お願い → します)。百科事典の文章なので、漢字や
    カタカナの続きは記事の話題に引きずられる (ありがとう → 作品名、天気 → 図)。
    入力は語の順に並んでいるので、語ごとに区切って読む (全部をメモリに載せない)。
    """
    rows = []
    def flush(head, tails, total):
        if head is None or total < NEXT_KEY_MIN:
            return
        tails.sort(key=lambda x: (-x[0], x[1]))
        kept = [t for n, t in tails if n >= NEXT_MIN and n >= total * NEXT_SHARE][:NEXT_TOP]
        if kept:
            rows.append((total, head, kept))
    current, tails, total = None, [], 0
    with open(pairs_path, encoding='utf-8') as f:
        for line in f:
            head, tail, n = line.rstrip('\n').split('\t')
            if head != current:
                flush(current, tails, total)
                current, tails, total = head, [], 0
            total += int(n)
            if all('ぁ' <= c <= 'ゖ' for c in tail):
                tails.append((int(n), tail))
    flush(current, tails, total)
    rows.sort(key=lambda x: (-x[0], x[1]))
    rows = rows[:NEXT_MAX]
    with open(out_path, 'w', encoding='utf-8') as out:
        out.write('# 確定した語の次に出す語 (語, 続き…)。scripts/build-kkc-freq.sh が作る。\n')
        out.write('# 日本語版ウィキペディア (CC BY-SA 4.0) の文章で語の並びを数えた結果から作った。原文は含まない。\n')
        for _, head, kept in sorted(rows, key=lambda x: x[1]):
            out.write(head + '\t' + '\t'.join(kept) + '\n')
    sys.stderr.write(f'[next] rows={len(rows)}\n')


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'extract':
        extract(int(sys.argv[2]), sys.argv[3])
    elif cmd == 'count':
        count(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 2,
              sys.argv[5] if len(sys.argv) > 5 else None, sys.argv[6] if len(sys.argv) > 6 else None,
              sys.argv[7] if len(sys.argv) > 7 else None)
    elif cmd == 'build':
        build(sys.argv[2], sys.argv[3], sys.argv[4])
    elif cmd == 'names':
        build_names(sys.argv[2], sys.argv[3], sys.argv[4])
    elif cmd == 'predict':
        build_predict(sys.argv[2], sys.argv[3], sys.argv[4])
    elif cmd == 'next':
        build_next(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)
