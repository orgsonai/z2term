# z2term IME 評価フレームワーク

[IME-ARCHITECTURE-RESEARCH.md](IME-ARCHITECTURE-RESEARCH.md) で計画した **Phase 0 (評価基盤)**
の成果物。以降の改善 (Phase 1〜) を再現可能に判定するための土台。

## 概要

- **評価セット**: [`app/src/test/resources/kkc_eval.tsv`](../app/src/test/resources/kkc_eval.tsv)
- **評価ランナー**: [`app/src/test/java/.../KkcEvalTest.kt`](../app/src/test/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcEvalTest.kt)
- **実行**: `sh ./gradlew :app:testFullDebugUnitTest --tests '*KkcEvalTest*'`
- **レポート**: 標準出力 + `app/build/kkc-eval-report.txt`

評価対象は [`KkcConverter.convert(reading)`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcConverter.kt) の戻り値
(Viterbi 単発の最尤変換結果)。`KanaKanjiConverter.convertFlexible` ではなく Kkc コアを直接
測ることで、Phase 1 以降に追加する N-best / リランカー / 学習 等の効果を独立に切り分けられる。

## TSV 形式

```
<reading><TAB><expected><TAB><tag1>,<tag2>,...
```

- `reading`   : ひらがな入力
- `expected`  : 期待出力。**商用 IME (Google 日本語入力 / ATOK 等) の標準出力**を基準にする
                (z2term 現状ではない)。同じ読みに複数の妥当解があるときは最も一般的なものを 1 つ。
- `tags`      : 集計用カテゴリ。複数付与可。
  - `short`/`medium`/`long` — 文節数の目安 (1-2 / 3-4 / 5+)
  - `proper` — 固有名詞含む
  - `katakana` — 外来語/カタカナ含む
  - `homophone` — 同音異義語の選択が要点
  - `verb` — 動詞活用
  - `adj` — 形容詞
  - `particle` — 助詞処理が要点
  - `number` — 数詞・カウンタ
  - `greeting` — 挨拶定型
  - `business` — ビジネス/フォーマル
  - `daily` — 日常会話

行頭 `#` はコメント。空行は無視。

## ベースライン (2026-06-01, commit 08025ac + 評価基盤導入時点)

134 ケース、`KkcConverter.convert` 単発。

| カテゴリ | 正解率 | 内訳 |
|---|---:|---:|
| **OVERALL** | **37.31%** | 50/134 |
| short | 40.22% | 37/92 |
| medium | 45.83% | 11/24 |
| long | 11.11% | 2/18 |
| proper | 0.00% | 0/11 |
| katakana | 70.00% | 7/10 |
| homophone | 33.33% | 4/12 |
| verb | 31.67% | 19/60 |
| adj | 37.50% | 6/16 |
| particle | 42.86% | 6/14 |
| number | 30.00% | 3/10 |
| greeting | 50.00% | 3/6 |
| business | 40.00% | 4/10 |
| daily | 40.00% | 18/45 |

## Phase 1 結果 (2026-06-02, N-best ラティス + リランカー土台)

`KkcConverter.nbest(reading, k)` を追加 (ラティス + 後ろ向き A*)。`segments()`/`convert()`
は不変。新メトリクスを `KkcEvalTest.evaluateNbestCoverage` (`build/kkc-nbest-report.txt`)
で計測。

| メトリクス | 値 | 内訳 |
|---|---:|---:|
| convert() OVERALL (回帰ガード) | 37.31% | 50/134 (ベースライン維持) |
| **N-best TOP1** | **42.54%** | 57/134 |
| **N-best TOP5 cov** | **68.66%** | 92/134 |

- **TOP5 cov − TOP1 = 約 26pt** が「リランカー (Phase 2 以降) で回収できる伸びしろ」。
  正解は大半が上位 5 件のラティス内に存在するが、Viterbi 単発コストでは 1 位に上がらない。
- TOP1 (42.54%) が convert() (37.31%) を上回るのは、ラティスでは未知かなノードを
  lc=rc=0 の通常ノードとして接続コスト込みで扱う一方、`segments()` の UNK は接続コスト 0 で
  透過させる差による (辞書語のみの読みでは両者一致)。convert() 本体は意図的に据え置き。
- 回帰ガード `convertDoesNotRegressBelowBaseline` を追加 (convert() の正解数 < 50 で失敗)。
- **先頭文節正解率は未導入**: 期待値側の文節境界アノテーションが要るため Phase 5 で導入予定
  (本ファイル「既知の制限」参照)。

## Phase 2 結果 (2026-06-02, ユーザ確定 bigram 学習)

`ImeHistoryStore` に bigram テーブル (前確定語→当語) を追加し、Phase 1 の `KkcReranker` を
`KkcContext(prevSurface)` 付きに拡張。`HistoryReranker` が「直前確定→当候補」が学習済み
bigram にあればコストボーナス (1500〜2750) を引いて昇格させる。実機では `ComposingState` の
確定連鎖で `recordBigram`、`convertFlexible`/`fullPrediction` が context 付き `nbest` 経由。

- **固定評価セットでは測定不可**: 履歴が空のため `HistoryReranker` は素通り (IdentityReranker と
  同値)。よって `KkcEvalTest` の数値 (TOP1 42.54% / TOP5 68.66%) は Phase 1 から不変で、回帰ガード
  `convertDoesNotRegressBelowBaseline` も維持。これは本ファイル「既知の制限」(convertFlexible は
  学習履歴依存で再現性が低く評価外) と整合。
- **検証は専用ユニットテスト** `HistoryRerankerTest`:
  1. 学習済み bigram の候補がボーナスでトップへ昇格する
  2. 前語コンテキスト null では Viterbi 順のまま
  3. N-best 経路に組込んだとき、下位候補が context で 1 位へ入れ替わる (end-to-end)
- **次の評価強化**: 学習注入版の評価セット (確定列をシミュレートして bigram を仕込み、2 回目の
  正解率を測る) は将来課題。現状は単体テストで配線の正しさを担保。

## Phase 3 結果 (2026-06-02, カタカナ化抑止 / G4)

失敗系統 #1「不要なカタカナ化」への対処。IPADIC は擬音語/記号的名詞にカタカナ表層を
低コストで持つ (例 `ほん→ホン 3692 < 本 5947`、`ありがとう→アリガトウ 2764 < 有難う 4099`)
ため、Viterbi がそれを最尤に選んでいた。`KkcConverter.loadFromStreams` のロード時に、
**読みの単純カタカナ写し表層** (`surface == hiraganaToKatakana(reading)`) へ生起コスト
ペナルティ `KATAKANA_DUP_PENALTY = 4000` を一律加算する (辞書ビルドは Phase 8 方針で不変)。

正当な外来語を壊さないため、**読みに長音符「ー」を含むものは除外**する
(`コーヒー/データ/メール` など。ネイティブ語の読みに「ー」は出ない)。これがないと
`こーひー→珈琲` のように外来語が異体字漢字に負ける (katakana 70%→60% の回帰)。

| メトリクス | ベースライン | Phase 3 | 差 |
|---|---:|---:|---:|
| **convert() OVERALL** | 37.31% (50) | **43.28% (58)** | **+5.97pt** |
| N-best TOP1 | 42.54% (57) | **49.25% (66)** | +6.71pt |
| N-best TOP5 cov | 68.66% (92) | **71.64% (96)** | +2.98pt |
| katakana (狙い + 守るべき) | 70.00% | **70.00%** | ±0 (回帰なし) |

- **全カテゴリでベースライン以上** (回帰なし)。特に adj 37.50%→56.25%、particle 42.86%→
  57.14%、daily 40.00%→44.44%、verb 31.67%→36.67% と波及効果が大きい。カタカナ化抑止は
  単独カテゴリでなく、文全体の Viterbi 経路を巻き込んでいたため。
- 回帰ガード `convertDoesNotRegressBelowBaseline` (pass>=50) 維持 (現 58)。
- **残るカタカナ失敗** (`あぷり/すまほ/ぶろぐ`) は辞書に語が無く部分読みからの UNK 合成由来で、
  本ペナルティの対象外。語彙再投入 (Phase 8) で対応する範囲。

## Phase 4 結果 (2026-06-02, 共起 / コロケーション rerank / G3)

失敗系統 #2「稀語/異体字の混入」への対処。Viterbi はクラス bigram (品詞接続) しか持たず、
`みずをのむ` で `瑞を飲む` (瑞 6052 < 水 7385) のように稀語が常用語に勝つ。mozc の
`CollocationRewriter` 相当を導入: **内容語の共起 2-gram を [ExistenceFilter] (Bloom)** に持ち、
N-best 段で「隣接する内容語核ペアが共起集合にあればコストボーナス」を与えて昇格させる。

- **共起データ**: 日本語 Wikipedia (`jawiki pages-articles`) を bz2 ストリームで先頭 4 億文字だけ
  展開し ([scripts/build-collocation.sh] + `build_collocation.py`)、「内容語核 (漢字/カタカナ連続)
  × ひらがな glue 1〜4 字 × 直後内容語核」の 2-gram を頻度カット (n≥3) で抽出。**498,776 ペア →
  Bloom 507KB** (`app/src/main/assets/kkc_colloc.bloom`、目標 FP 率 2%、k=6)。形態素解析器に依存せず
  文字種だけで核を切り、推論側 ([CollocationReranker]) も**同一規則**でトークン化するので集合の
  引き当てが一致する。`水＋飲`/`本＋読`/`橋＋渡`/`花＋見` は収録、`瑞＋飲`/`高遠`/`選手＋雨` は非収録
  であることを確認済み (= 過学習でない実データ信号)。

| メトリクス | Phase 3 | Phase 4 | 差 |
|---|---:|---:|---:|
| **N-best TOP1** | 49.25% (66) | **55.22% (74)** | **+5.97pt** |
| N-best TOP5 cov | 71.64% (96) | 71.64% (96) | ±0 (経路集合は不変) |
| convert() OVERALL (回帰ガード) | 43.28% (58) | 43.28% (58) | ±0 (convert は rerank 非経由) |

- TOP1 を +8 件押し上げた。cov が不変なのは「正解はラティス内に在るが Viterbi 単発では 1 位に
  上がらない」候補を rerank が引き上げる仕組みだから (Phase 1 で示した伸びしろの回収)。
- カテゴリ別 TOP1: particle 78.57%、medium 70.83%、daily 60.00%、verb 56.67% など広く上昇。
  共起は同音語カテゴリだけでなく文全体の最尤経路を巻き込むため波及効果が出る。
- **限界**: (1) `みず→水`、`こうえん→公園` のような**単語単独**の頻度誤りは後続内容語が無く共起では
  直せない (頻度プライアが要る範囲)。(2) `飴を食べる` 等、4 億文字の部分抽出に頻度不足で未収録の
  ペアは効かない (CHAR_LIMIT を上げるか MIN_COUNT を下げて再ビルド可能)。(3) 漢字が連続する複合語
  境界 (glue なし) は corpus 側で 1 核に融合するため捕捉しない。これらは Phase 7 (LM) / Phase 8
  (語彙) の範囲。
- 実機配線: `ImeHistoryStore.ensureLoaded` で `CompositeReranker([CollocationReranker,
  HistoryReranker])` を構成 (共起で同音語を整え、ユーザ確定履歴を最終優先)。検証は
  `CollocationRerankerTest` (`水を飲む` 昇格 / 単一核は不変 / filter null 不変 / 単一候補不変)。
  回帰ガード `convertDoesNotRegressBelowBaseline` 維持。

## 失敗パターンの分類

ベースライン失敗 84 件を観察した結果、主な誤りは次の 5 系統に分類できる。括弧内は
[IME-ARCHITECTURE-RESEARCH.md](IME-ARCHITECTURE-RESEARCH.md) のギャップ番号と該当 Phase。

### 1. 不要なカタカナ化 (G4 / Phase 3)
辞書にカタカナ表記エントリがあり Viterbi がそちらを最尤と選ぶ。
- `ありがとう` → `アリガトウ`
- `ほんをよむ` → `ホンを読む`
- `ほっかいどうへ` → `ホッカイドウへ`

→ 文字種別未知語モデル整備 + カタカナエントリのコスト調整で改善。

### 2. 稀語/異体字の混入 (G3 / Phase 4)
コスト差が小さい異表記に Viterbi が引っかかる。
- `みずをのむ` → `瑞を飲む`  (水 vs 瑞)
- `ながいかいだん` → `永い会談`  (長い/階段 vs 永い/会談)
- `せんしゅう` → `選手雨`  (先週 vs 選手+雨)
- `こうえんで` → `高遠で`  (公園 vs 高遠)

→ コロケーション/共起 rerank で「水+を+飲む」型を昇格させる。

### 3. 固有名詞の不在 (G6 / Phase 8)
辞書ビルド時に固有名詞/人名/地名/組織を意図的に除外したため壊滅 (proper: 0%)。
- `とうきょう` → `トウ鏡` / `トウ教徒`
- `やまだたろう` → `山田たろう` (太郎 が無い)
- `なかの` → `泣かノ` / `中野` 候補無し
- `おおさか` → `多さ化`

→ Phase 8 で固有名詞辞書を再投入。Phase 4 (コロケーション) + Phase 7 (LM) を先に
   入れないとコスト調整が破綻するため順序は逆転させない。

### 4. 長文での誤区切り連鎖 (G5 / Phase 5)
1 箇所の誤区切りが後続を巻き込んで崩壊する。
- `とうきょうとに` → `トウ教徒に` (東京/都 → トウ/教徒)
- `きのうのよる` → `機能の夜` (昨日 → 機能)
- `はちじにおきます` → `八時に置きます` (起きる vs 置く)

→ 連文節境界モデル + コロケーションで連鎖崩壊を局所化。

### 5. 表記揺れ (許容範囲 / 評価セット側で対処)
- `かたづける` → `片づける` (期待 `片付ける`)
- `きれい` → `奇麗` (期待 `綺麗`)
- `できる` → `出来る` (期待 `できる`)

→ 評価セット側で複数許容にするか、ケースを除外。今は失敗としてカウントしている
   ため、実質的なギャップは表よりやや小さい (推定 +3〜5pt)。

## 改善判定の運用ルール

- Phase 1 以降の各 PR/コミットで `KkcEvalTest` を実行し、**OVERALL** と
  「Phase が直接狙うカテゴリ」の両方が**ベースラインを下回らない**ことを目視確認。
- Phase 完了時点で本ファイルに結果表を追記 (履歴として残す)。
- 大きな改善 (例: Phase 2 で daily が +10pt) を観測した場合、評価セットが甘い疑い
  があるため、その時点で追加 50-100 件を投入してから再評価する。
- 単純な正解率では捉えられない品質 (候補 1 位の妥当性 / 文節境界の自然さ) は、
  Phase 1 で N-best が取れるようになった時点で「**N-best 内に正解が含まれる率**」
  「**先頭文節正解率**」を追加メトリクスとして導入する。

## 既知の制限

- **テスト評価は `convert()` のみ**: 実 UI で使う `convertFlexible` は学習履歴
  ([ImeHistoryStore]) や予測候補が混ざるが、それらは履歴データに依存して再現性が
  低いため評価から外している。
- **文節アライメント評価が無い**: 期待値が「全文の表層」なので、文節単位のずれは
  全部「文不一致」になる。文節単位の評価には期待値側の文節境界アノテーションが必要で、
  Phase 5 着手時点で導入予定。
- **同音許容候補が単一**: `はしをわたる` の期待は `橋を渡る` だが文脈次第で
  `端を渡る` も妥当。Phase 4 の共起モデル評価時に多解化を検討。
- **134 ケースは小さい**: 計画では 200-500 件。Phase 1-2 着手前に **+100 件** を
  目標に拡張する (特に long と proper を増やす)。

## 拡張の指針

ケース追加時の優先度:

1. **long を倍に**: 現状 18 件。長文崩壊は z2term の主要痛点だが評価不足。
2. **homophone を倍に**: 12 件 → 24 件。Phase 4 の効果測定に必須。
3. **proper を倍に**: 11 件 → 22 件。Phase 8 の効果測定に必須。
4. **negative 系**: 「〜ではない / 〜なかった / 〜ません」 (打消し) は誤変換が
   起きやすいが現状あまり入っていない。
5. **会話/口語**: 「〜じゃない / 〜だよ / 〜なんだけど」 等。

ケース選定時は **Google 日本語入力 (or 同等の OSS mozc)** を基準にし、出力が
1 通りに定まるものを優先する。曖昧な文脈依存ケースは tag に `ambiguous` を
付けて集計から除外できるようにする (現状未実装。必要になったら追加)。
