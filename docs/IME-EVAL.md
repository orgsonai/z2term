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
