# IME アーキテクチャ調査 — z2term 内蔵 IME を商用 IME に近づけるためのギャップ分析と段階導入プラン

最終更新: 2026-06-01 / 著者: z2term 開発チーム (調査・設計メモ。実装はまだ行っていない)

---

## 1. 目的と範囲

z2term の内蔵かな漢字 IME を、Google 日本語入力 / mozc、ATOK、iOS、MS-IME といった商用 IME の「**長文一括変換**」品質に近づけるため、

- 各 IME の**アルゴリズム / データ構造 / 学習**を一次情報から押さえ、
- z2term 現状との**ギャップを定量・定性で並べ**、
- **効果が大きく独立性が高い順**に段階的に導入するロードマップ

を提示する。コード変更はしない。本書は設計判断の根拠となる「事実ノート + 設計ドラフト」である。

主な一次情報源:

- **mozc**: ソースコード (github.com/google/mozc, /tmp/mozc にローカル clone) と 工藤拓 ほか "Efficient dictionary and language model compression for input method editors" (WTIM 2011)[^kudo2011]
- **MS-IME (Pinyin/日本語 共通基盤)**: Gao & Goodman "Toward a Unified Approach to Statistical Language Modeling for Chinese"[^gao2002], "The Use of Clustering Techniques for Language Modeling"
- **ニューラル IME**: Yao et al. "Real-time Neural-based Input Method" (arXiv:1810.09309)[^yao2018], 奥野 "ニューラルかな漢字変換" (Qiita)[^okuno]
- **ChaIME / 森信介 ら**: TMU 小町研の解説[^chaime] および「あいまいな日本語のかな漢字変換」(SProSym 2008)
- **ATOK**: ジャストシステム公式 (ディープコアエンジン / ハイパーハイブリッドエンジン 2 / クラウド推測変換)[^atok1][^atok2]
- **iOS**: Apple Machine Learning Research[^apple_fm] (公開情報は限定)

---

## 2. z2term 現状サマリ

| 項目 | 現状 |
|---|---|
| 辞書 | IPADIC 2.7.0 派生 `kkc_lex.tsv` (約 24 万語、固有名詞・地名・組織除外)、9.6 MB 非圧縮 TSV をハッシュマップで全展開 |
| 接続コスト | IPADIC `matrix.bin` 由来 `kkc_matrix.bin` (3.3 MB)、品詞×品詞の int16 二次元配列を **`ShortArray` で全展開** |
| 言語モデル | **品詞 bigram のみ**。クラス LM や単語 n-gram は無し。Viterbi の更新式は `cost(j) = cost(i) + matrix[rcat[i], e.lc] + e.cost` |
| デコーダ | 自前 Kotlin Viterbi (`KkcConverter.segments`、O(n²) 走査 × エントリ数)。ビーム/N-best は無し、1-best のみ |
| 未知語 | 1 文字あたり `UNK_COST=17000` の固定コスト。**文字種別未知語モデル無し** |
| 文節 | 「漢字/カタカナ始まり語 + 後続ひらがな」の**素朴ルール** (`bunsetsu()`)。連文節学習・境界モデル無し |
| 学習 | `ImeHistoryStore` で「読み→確定表層」の完全一致/前方一致を候補トップに昇格。**コスト更新・bigram 学習・SCW/PA 等のオンライン学習は無し** |
| 補助語彙 | SKK 辞書 (`z2dict.txt` 5.2 MB) と内蔵活用テーブル `SUPPLEMENT_WORDS` (約 170 語の終止形を活用展開) を別系統で `KanaKanjiConverter.convertFlexible` がマージ |
| 文脈 / 共起 | 無し。同音異義語の文脈解消なし |
| 入力誤り訂正 | 無し (ローマ字打鍵ミス・促音脱字補正なし) |
| 候補生成 | 上記 1-best + 補助マージ。N-best ラティス展開・候補リランキング段は無し |

ファイル: `/root/tmp/app_project/05_z2term/app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcConverter.kt`, `KanaKanjiConverter.kt`。

要するに **「MeCab を Kotlin で素直に走らせた 1-best 形態素解析」 + 「履歴トップ昇格」** という、ChaIME 2008 年版より前の素朴アーキテクチャ。後段 (リランキング・連文節学習・未知語モデル・N-best) が完全に欠落している。

---

## 3. 各 IME のアーキテクチャ

### 3.1 mozc / Google 日本語入力 (一次情報を深掘り)

**全体パイプライン** (`src/converter/`, `src/prediction/`, `src/rewriter/` を実コードで確認)

```
入力文字列
  → Composer (ローマ字→かな変換、TypeCorrectedQuery で打鍵ミス展開)
  → KeyCorrector (誤入力訂正のためのキー射影 — penalty 付き)
  → Lattice 構築
       ├ DictionaryInterface (LOUDS trie 2 本 + token array, 主にSystem/User/Suffix/SingleKanji)
       ├ Segmenter (IsBoundary: l_rid × r_lid のビット表で文節境界事前確率)
       └ AddCharacterTypeBasedNodes (未知語: 文字種ごとのフォールバックノード)
  → ImmutableConverter::Viterbi (CachingConnector で 2048-way キャッシュ)
       └ もしくは PredictionViterbi (同 lid/rid を縮約した高速版、予測用)
  → NBestGenerator (A* — priority_queue<QueueElement{node, fx, gx, structure_gx, w_gx}>)
  → CandidateFilter (POS / SuggestionFilter による刈り)
  → Rewriter chain (collocation / user_segment_history / user_boundary_history /
                    transliteration / variants / date / number / a11y / 約 50 段)
  → Predictor (Dictionary / UserHistory / Realtime / SingleKanji / NumberDecoder 合議)
  → Segments (最終候補リスト)
```

**辞書/語彙構造** [^kudo2011]

- ベース辞書: OSS 版 `data/dictionary_oss/dictionary00-09.txt` の 10 ファイル × 約 12.9 万行 = **約 129 万エントリ** (フィールド: `読み\t左ID\t右ID\tコスト\t表層`)。
- 内部表現: **読み LOUDS trie と表層 LOUDS trie の 2 本**、リンク用に Token Array (POS id, cost, leaf node id)。これにより common-prefix / predictive / **reverse lookup** が O(n) で同居。
- 圧縮: 上記 + 文字列圧縮 (Hiragana/Katakana 1byte エンコード)、Token 圧縮 (POS の頻度バイアスを利用した可変長符号 with rx ライブラリ)、Katakana bit (片仮名は表層 trie から除外しビット 1 つで復元)。
- 結果: **1,345,900 語を 13.3 MB (= 10.4 byte/word)**。素のテキスト 59.1 MB の 22%、Double Array (約 80 MB) の 17%。
- 補助辞書: `aux_dictionary.tsv` (差分追加, 既存語のコスト微調整)、`suffix_dictionary` (語尾活用接続)、`single_kanji_dictionary`、`zip_code_seed`、`emoji`/`emoticon`、`zero_query` (確定後の次語予測辞書)。

**言語モデル** [^kudo2011]

- **クラス bigram LM** `P(W) = Π P(w_i|c_i) P(c_i|c_{i-1})`。クラスは品詞だがアグレッシブに**字句化** (function word、頻出動詞・形容詞は各エントリ自身が独立クラス) しており、**クラス数は 3,019**。
- 接続行列は 3019×3019、**約 86% が 0 確率** (Web スケールで学習しても疎)。これを **8 分岐 succinct tree** に階段状に詰め、bit-vector の rank/select で参照 (図 3 in 論文)。
- サイズ: **2.9 MB** (素 2D 行列 17.4 MB → 0.51 MB tree + 2.4 MB cost table)。
- 量子化: コストは log 確率を **16-bit 整数 (2 byte)** に詰める。
- アクセス高速化: 1024 スロットのキャッシュで lookup を約 1.6 倍 (Table 4: 0.0158→0.0099 sec/sentence)。さらに `CachingConnector` (`src/converter/immutable_converter.cc:774-`) は内側ループで `r.lid` が固定であることを利用、`cache[l.rid]` 2048 way の専用キャッシュを持つ。

**デコーダ詳細** (`immutable_converter.cc`)

- `ViterbiInternal(pos, right_boundary, lattice)` でラティス上を pos ごとに更新。**最尤前ノード `prev` だけ保持**するクラシックな 1-best Viterbi。bigram のみ。
- `Resegment` で算用数字+接尾辞、接頭辞+算用数字などの再分割ルールを適用。
- N-best 列挙は別段の `NBestGenerator` で **A***。優先度付きキュー `Agenda` に `QueueElement{node, next, fx, gx, structure_gx, w_gx}` を積み、`fx` (cost-so-far + admissible heuristic from Viterbi) で展開。同等コスト候補は `kCostDiff = 3453` (≒ log(1/1000)) の枠で許容。
- `CandidateFilter` で重複/品質チェックを通す。
- 予測 (suggestion/prediction) は `PredictionViterbi` という同じ lid/rid を縮約した高速版を別に持つ。

**文節 / 連文節**

- 静的文節境界モデル `Segmenter` (`src/converter/segmenter.h`): `l_table[lid] × r_table[rid]` から `bitarray_data` の 1bit で境界判定 + `prefix_penalty/suffix_penalty` を持つ。
- 文節レベル学習: `rewriter/user_segment_history_rewriter` (ユーザの文節選択の永続履歴で順位を入れ替え)、`rewriter/user_boundary_history_rewriter` (文節境界変更の履歴を学習)。

**未知語**

- `ImmutableConverter::AddCharacterTypeBasedNodes()`: ひらがな・カタカナ・漢字・英数字など**文字種ごとの長さ別ノード**を投入し、Viterbi に飲ませる。コストはタイプごとのモデルから。
- 加えて `value_dictionary` (読みは無いが表層だけ持つ辞書) で表記揺れ・新語をカバー。

**学習 / オンライン**

- `UserHistoryPredictor` (`src/prediction/user_history_predictor.cc`, 2716 行): protobuf シリアライズの LRU、**bigram エントリ** (`HasBigramEntry`, `GetBigramEntryLruOrder` ほか) を明示的に保持。「直前確定 → 次語」の **next_entries** を 6 個まで保持して**ゼロクエリ予測** (確定直後の候補) に使う。
- `UserSegmentHistoryRewriter`, `UserBoundaryHistoryRewriter` で文節単位の学習。
- `UserDictionary` (ユーザ登録語)、`SuggestionFilter` (NG 語フィルタ)。
- ATOK 流の SCW/AROW のような重み更新は mozc OSS には**入っていない** (内部 Google 版は非公開)。

**Rewriter chain (リランキング段)**

`src/rewriter/` 直下に 50 以上のリライタ。代表的なもの:

- `CollocationRewriter` (`collocation.txt` + ExistenceFilter [Bloom 系] で「共起する 2 語」を判定し、共起する候補を昇格)。
- `SymbolRewriter`, `EmojiRewriter`, `EmoticonRewriter`, `DateRewriter`, `NumberDecoder`, `ZipcodeRewriter`, `CalculatorRewriter` (各種特殊変換)。
- `TransliterationRewriter` (カタカナ/全角英数等の変換候補注入)。
- `VariantsRewriter` (字体ゆれ)。
- `EnvironmentalFilterRewriter` (端末で出ない文字を除外)。

**入力誤り訂正**

- `KeyCorrector` (`src/converter/key_corrector.cc`): ローマ字打鍵ミスの仮想キー (`っ` 抜けや `n` 末尾の補完など) を penalty コスト付きで Viterbi に追加。
- モバイル用 typing model: `data/typing/typing_model_{12keys,flick,godan,qwerty_mobile,toggle_flick}-hiragana.tsv` の打鍵→正解 hiragana の置換コスト表 (qwerty 6,417 / flick 102,041 / toggle_flick 108,770 行)。
- `composer/query.h` の `TypeCorrectedQuery` で複数候補を生成し下流に流す。

**訓練データ** [^kudo2011]

- 論文では「Web 上の大量データから統計的に推定」。具体的コーパス名は非公開だが、辞書 1.34M 語・接続行列 3019² の規模が「Web スケール」を示す。
- OSS 版 dictionary00-09.txt はその一部公開で約 1.29M 行。

**サイズ / 速度**

- 辞書 13.3 MB + LM 2.9 MB ≈ **16 MB**。プラスでリライタ各種データ・絵文字・補助辞書で実バイナリは 40-60 MB クラス (Android Gboard 同梱版相当)。
- 変換速度: 論文 Table 4 で約 10 ms/文 (PC・cache 1024)、モバイル実機でも 1 文確定 < 100 ms オーダー。

---

### 3.2 ATOK (Justsystem) — 公開情報の範囲で

[^atok1][^atok2] および公開技術解説 (PC Watch, 日経 xTECH) を統合。**ソースは非公開のため挙動と公式説明から推定**。

| 軸 | 公開されている内容 |
|---|---|
| 辞書 | 基本辞書 + 多数の専門辞書 (医療・法律・関西弁等)。クラウド辞書 232 万語 (ATOK Passport クラウド推測変換)。 |
| 言語モデル | 「ATOK ハイパーハイブリッドエンジン 2」: ルールベース + 統計ハイブリッド。**変換強度学習** (同一語でも文脈で強度が変わる) を持つ。 |
| デコーダ | 連文節最尤探索。N-best UI で複数文節選択。詳細アルゴリズム非公開。 |
| 文節 | 連文節変換と文節区切り変更 (Shift+←/→) を学習・反映。 |
| 未知語 | 専門辞書ロード + 「校正支援」「省入力候補」で実質吸収。 |
| 学習 | **「ATOK ディープコアエンジン」** (2018-, 2022 に v2): ディープラーニングで「ルール化できない日本語の特徴」を抽出して従来エンジンに組み込み、**誤変換 約 30% 削減**を謳う。具体的モデル構造 (RNN? Transformer?) は非公開。 |
| クラウド | クラウド推測変換でジャンル・固有名詞を補完。最近の `ATOK MiRA` (2026/02) で生成 AI 連携。 |
| 入力誤り訂正 | 誤打鍵補正・促音脱字補正・ローマ字スペルミス補正がデフォルト。 |
| 文脈 | 「文脈解析変換」(直前文の語彙傾向を見て同音語を切り替え)。 |

**ATOK は「ルール+統計+小規模 NN」のハイブリッドエンジンで、ユーザ学習 (変換強度) と専門辞書補強で稼ぐ**戦略。アルゴリズム差より**辞書品質・学習粒度・後段リランキング**で勝負している印象。

---

### 3.3 iOS (Apple 日本語入力)

- Apple は IME の内部設計を学術論文の形で公開していない。Apple ML Research のブログから次が示唆される:
  - **オンデバイス**で動く GRU/LSTM 系シーケンスモデル群が他言語のキーボード予測で使われている (`Language Identification from Very Short Strings` ほか)。
  - 2024 以降は **Apple Intelligence の 3B パラメータ on-device 基盤モデル** が一部機能を担う。**重みは 3.7 bit/weight の混合量子化** (2-bit / 4-bit palletization + LoRA アダプタ) で実装。日本語サポート対象。
- **かな漢字変換そのもの**については、長年バックグラウンドで Anthy/SKK 系の構造的アプローチが残存しているとの観察があるが、近年は明らかに統計/NN 寄りに移行している。「ライブ変換」(macOS) は MeCab 系の bigram + ヒューリスティクスベースが起源と推測。
- iOS の連文節品質は**短文では mozc 同等**、**長文 (40 字超) では文節境界誤りが目立つ**という外部評価が大勢。Web 由来語彙の補強が弱い。

**結論**: iOS は商用 4 強の中で**最も内部不透明**だが、観察上は「クラス bigram + ニューラル後段リランキング + ユーザ統計学習」レベルと推測される。アーキテクチャ的に z2term の参考にできる詳細情報は乏しい。

---

### 3.4 MS-IME (Microsoft)

[^gao2002] および "The Use of Clustering Techniques for Language Modeling" (Goodman, Gao 2001)

- 言語モデル: **trigram (n=3)** をベースに **クラスベース n-gram** を組み合わせた**統一フレームワーク** (Pinyin から日本語に流用)。
- スムージング: **Modified Kneser-Ney** (CL 2001 系)。
- デコーダ: 完全な trigram Viterbi (動的計画法、ノード状態 = 直前 2 単語クラス)。N-best は A* 風。
- 学習: 大規模 Web/コーパスから ML 推定。**MSR-IME corpus** (6,000 文、100-best 付き) を公開し re-ranking 研究の標準ベンチに。
- クラスタリング: IBM クラスタリング (Brown clustering) で語クラスを自動誘導 → クラスベース trigram (LM サイズ削減 & 未観測 trigram の救済)。

**MS-IME は「Web スケールの素直な trigram + クラスタリング」の路線**。歴史的にこの設計が ChaIME/mozc にも強く影響している (mozc は class **bigram** で簡素化、MS-IME は class+word **trigram** で精度寄り)。

---

### 3.5 参考 OSS: libkkc / Anthy / ChaIME

- **Anthy**: 連文節変換用に独自の辞書 + コスト + 後段補正。設計年代が古く、Web 由来 LM や大規模クラスタリングは無い。`ut` 辞書追加で実用域に押し上げる文化がある。
- **libkkc** [^libkkc]: Anthy の後継として登場、**N-gram LM (KenLM ベース)** を Marisa-trie + KenLM のオフラインバイナリで読む構成。BCCWJ から学習可能。
- **ChaIME** [^chaime]: 森信介 (京大) ら。Bayes 分解 `P(W|S) ∝ P(W) P(S|W)`、bigram + 大規模コーパス、**辞書を学習データから自動構築 (Web vocabulary)**。mozc 設計の直接の理論的祖先。
- **mozc-devices**, **Mozc UT 辞書**: 辞書補強 (固有名詞 / 新語) の文化的中心。

---

### 3.6 ニューラル系 (mozc 後継候補としての参考)

- **Yao et al. 2018** [^yao2018]: LSTM 言語モデル + Selective Softmax で IME 用 Viterbi/ビーム探索を実時間化。BCCWJ で評価。語彙 50k 規模、softmax 2 桁高速化、92% モデル圧縮、CPU 単独で実用速度に到達。**IME に純ニューラル LM を入れた最初の実証**。
- **奥野 2018** [^okuno]: Mikolov 系 RNN (GRU, hidden=400)、語彙 約 5 万、BCCWJ コア (約 6 万文)、ビーム幅 5。**N-gram に対し文正解率 +2.7pt、予測 +3.8pt**。Train は AWS p2.xlarge 1 時間。
- **Neural Pinyin IME (Y15-1052)**: 中文側で先行。NNLM + ベース統計 IME の rerank ハイブリッドが定石。

**含意**: 「ベースは class bigram Viterbi、N-best (10-50) をニューラル LM で rerank」がコスト/効果のスイートスポット。完全ニューラルデコードはモバイル CPU でもまだ重い。

---

## 4. 横断比較表

| 軸 | mozc | ATOK | iOS | MS-IME | **z2term 現状** |
|---|---|---|---|---|---|
| 辞書サイズ | 1.34M 語 / 13.3 MB (LOUDS×2 + token + 文字列圧縮 + Katakana bit) | 基本+多専門+クラウド 232万語 (非公開構造) | 不明 (おそらく数十万語 + on-device NN) | Web 由来大規模 (publicly: MSR-IME corpus) | **24 万語 / 9.6 MB TSV (非圧縮 HashMap)** |
| 接続行列 / クラス | クラス bigram, 3019 クラス, 86% 疎を succinct tree で 2.9 MB | ハイブリッド (ルール+統計+NN), 強度学習 | 不明 (LSTM/Transformer 系を含む可能性) | trigram + Brown クラスタ | **品詞 bigram, 行列を非圧縮 ShortArray (3.3MB) 展開** |
| デコーダ | 1-best Viterbi (caching connector) + A* で N-best | 連文節最尤 + 候補 UI | 不明 | trigram Viterbi + N-best | **1-best Viterbi のみ, N-best 無し** |
| 文節モデル | 静的 Segmenter (品詞対×bit) + user_segment/boundary_history rewriter | 連文節 + 区切り変更を学習 | 文脈解析寄り | trigram で実質吸収 | **「漢字始まり + 後続かな」の素朴ルール、学習無し** |
| 未知語 | 文字種別フォールバックノード + value dict (表層のみ) | 校正支援+専門辞書 | NN 系で吸収 | クラスタ LM で救済 | **1 文字 17000 固定コストのみ** |
| 学習 | UserHistory bigram (LRU + protobuf), Segment/Boundary History rewriter | 変換強度学習 (オンライン) + クラウド学習 | on-device 学習あり | (商用版で online learning) | **読み→表層完全/前方一致トップ昇格のみ** |
| リランキング後段 | Rewriter chain (collocation/transliteration/variants/date/number/emoji ほか 50+) | 推測変換 + クラウド | 不明 | rerank 研究の標準コーパス公開 | **無し** |
| 入力誤り訂正 | KeyCorrector + typing_model (qwerty/flick/godan/toggle) + TypeCorrectedQuery | あり (誤打鍵・促音脱字) | あり | あり | **無し** |
| 訓練データ規模 | Web スケール | Web + クラウド | 非公開 | Web | **IPADIC の手動推定コスト** |
| エンジンサイズ | 16 MB コア (+ 各種データで 40-60 MB) | 100-200 MB クラス (専門辞書込) | 不明 | 数十 MB | **15 MB クラス (辞書 + 行列)** |
| レイテンシ | 1 文 < 100 ms | 同等 | 同等 | 同等 | **24 万語スキャンでも < 100 ms と推定 (実測要)** |

---

## 5. ギャップ分析 (z2term 現状 vs. 商用 IME)

各項目の「変換品質への影響度」をラベル: 高 / 中 / 低。

| # | ギャップ | 影響度 | 説明 |
|---|---|---|---|
| G1 | **N-best 列挙が無い** | **高** | 1-best のみ。同音異義語の選び直し UI が単純 / リランキング段を入れる余地が無い。**全ての後段改善の前提**。 |
| G2 | **ユーザ確定の bigram (next-word) 学習が無い** | **高** | mozc の `UserHistoryPredictor` 相当。「直前確定 → 次語」のオンライン学習が無いので、ユーザ語彙への適応が極端に弱い。 |
| G3 | **共起・コロケーション後段が無い** | **高** | mozc の `CollocationRewriter` + ExistenceFilter (Bloom 系) 相当。bigram Viterbi だけだと「私はりんごを食べる」と「私はリンゴを食べる」の優劣が決まらない。 |
| G4 | **未知語モデル (文字種別) が無い** | **高** | カタカナ語・新語・外来語が `UNK_COST=17000` でぶった切られ、長文で外来語が混じると一気に崩れる。 |
| G5 | **文節境界モデル (連文節) が素朴** | **高** | 「漢字始まり + 後続かな」ルールでは「東京都/京都市」「今日は/今/日は」のような曖昧境界を解けない。mozc の `Segmenter` 相当を bigram から学習すべき。 |
| G6 | **辞書が IPADIC 240 千語のみ** | 中-高 | 固有名詞・地名・組織を捨てたため Web 由来語彙 (固有名詞・新語) の取りこぼしが大量。mozc 1.34M 語と 5.6 倍差。 |
| G7 | **クラス n-gram (trigram 以上) が無い** | 中 | bigram → class trigram で同音語の文脈識別がぐっと上がる。ただし LM データ要件が大きい。 |
| G8 | **入力誤り訂正なし** | 中 | `KeyCorrector` 相当が無いので、フリック誤打・促音脱字・`n` 末欠落で変換結果が壊れる。モバイル IME としては基本機能。 |
| G9 | **文節 / 文単位の学習 (segment history) が無い** | 中 | mozc の `UserSegmentHistoryRewriter` 相当。同一文節を 2 回目以降は正しく選ぶ機構が無い。 |
| G10 | **辞書フォーマットが非圧縮 TSV + HashMap** | 中 | 起動時に 24 万行を全展開して `HashMap<String, List<Entry>>` を構築。LOUDS / Marisa Trie 化で 1/3 にできて起動も速い。**将来の辞書拡大の前提**。 |
| G11 | **接続行列が非圧縮 ShortArray** | 中 | 3.3 MB ≈ そのまま (品詞数 ~1300 想定で 1300²×2)。クラス数を増やすと急膨張するため、mozc 流 succinct tree 化が望ましい。 |
| G12 | **品詞数が少ない (IPADIC 標準)** | 中 | IPADIC は ~1300 品詞、mozc は字句化で 3019 クラス。クラス粒度が粗いと bigram の識別力が低い。 |
| G13 | **コスト量子化と速度最適化なし** | 低-中 | mozc の `CachingConnector` 2048-way を入れると CPU 時間で数倍。Kotlin 実装ではより効きやすい。 |
| G14 | **絵文字・記号・日付・電話番号の特殊変換 (Rewriter chain)** | 低 | 端末用途では当面不要だが、SNS や日付入力が増えると欲しくなる。 |
| G15 | **訓練コーパスを持っていない** | (土台) | コスト・bigram・共起・典型誤打鍵モデルを自前学習するには日本語コーパスが必須 (BCCWJ コア 6 万文、Wikipedia 抽出、Web crawled など)。 |

---

## 6. 段階的導入プラン

順序は **「効果が大きい × 技術独立 × データ要件が軽い」優先**。各 Phase の目的・追加要素・期待効果・工数感・必要データ・リスクを示す。

### Phase 0 — 評価基盤 (これ無しでは前進できない)

- **目的**: 改善前後を定量比較できる仕組み。
- **追加**: 評価用文セット 200-500 件 (「私の名前は中野です」「今日は天気が良い」「東京都に住んでいます」など)、自動評価スクリプト (文正解率・文節正解率・先頭 1 文節正解率)。mozc の `quality_regression_test_data` 形式を流用可能。
- **期待効果**: 以降の改善を再現可能 + 退行検出。
- **工数**: 2-3 日。
- **データ**: 自前 200 件 + mozc OSS の regression データ流用 (ライセンス確認)。
- **リスク**: 評価データに偏りがあると改善方向を誤る。「短文 / 長文 / 固有名詞含 / 外来語含 / 同音語多発」のサブセット分割を必須化。

### Phase 1 — N-best 列挙 + 簡易リランキング段の足場

- **目的**: ギャップ G1, G3 の前提作り。
- **追加**: 現 Viterbi に**前向き / 後ろ向きの 2-pass** を入れ、ラティス保持→A* で N-best (N=10-30) を取れる構造に変更。簡易リランカー interface (`Reranker { score(Candidate, Context): Int }`) を追加し、現状はコスト恒等関数のみ。
- **期待効果**: それ自体は変換品質に直接効かないが、Phase 2-5 の全てがここに乗る。
- **工数**: 5-7 日。
- **データ**: 不要。
- **リスク**: Kotlin での A* 実装はオブジェクトアロケーションでヒープ圧迫。`IntArray` 配列ベースの軽量ノードプールが必要。

### Phase 2 — ユーザ確定 bigram 学習 (UserHistoryPredictor 相当)

- **目的**: ギャップ G2, G9。
- **追加**: 確定文の (前語表層, 当語表層) bigram を LRU で 5,000-10,000 件保持し、Phase 1 のリランカー段で「直前確定 → 当候補」が学習済 bigram にあればコストに固定ボーナス (例 -1500)。完全一致だけでなく**読み一致 + 表層 N-best 内**でも昇格。
- **期待効果**: 個人語彙への適応が即効。実機評価で同じユーザの同じ文が 2 回目から確実に正解。
- **工数**: 3-5 日。
- **データ**: 不要 (ユーザ入力が学習データ)。
- **リスク**: 履歴に誤確定が混じった時の **un-learn** UI が必要 (mozc の Ctrl+Delete 相当)。

### Phase 3 — 未知語: 文字種別フォールバックモデル

- **目的**: ギャップ G4。
- **追加**: `UNK_COST=17000` 固定を廃止し、`UnkModel` を導入: ひらがな連 / カタカナ連 / 漢字連 / 英数字連の **長さ別コスト表** + 文字種ごとの仮想 POS で接続行列を引く。mozc `AddCharacterTypeBasedNodes` を Kotlin 移植。
- **期待効果**: カタカナ外来語・新語が辞書ミスでも自然な区切りで残るため、長文の崩壊率が大幅低下。
- **工数**: 3-4 日 (コスト調整含む)。
- **データ**: 文字種ごとの典型コストは IPADIC `unk.def` を流用可能。
- **リスク**: コストが緩すぎると未知語が真値より優先される。Phase 0 評価で調整必須。

### Phase 4 — コロケーション / 共起 rerank

- **目的**: ギャップ G3。
- **追加**: 「(動詞|形容詞|名詞) × (助詞 + 直後内容語)」レベルの bigram 共起辞書を **Bloom Filter / Cuckoo Filter** (mozc の `ExistenceFilter` 相当) に詰め、Phase 1 のリランカー段で N-best を再評価。
- **期待効果**: 「リンゴを食べる」「林檎を食べる」のような同音語選択の精度が改善。mozc 公式でも全候補品質に最も効くリライタの 1 つ。
- **工数**: 5-7 日 (共起データの抽出・チューニング含む)。
- **データ**: Wikipedia 日本語ダンプから (動詞・形容詞・名詞, 直前 N 語) の 2-gram を頻度カットして 50-200 万エントリ → Bloom 化で 1-3 MB。
- **リスク**: 共起辞書の偏りがそのまま入力分野バイアスになる。複数ドメイン (Wikipedia + 青空文庫 + Web ニュース) でブレンドが望ましい。

### Phase 5 — 連文節境界モデル (Segmenter 相当)

- **目的**: ギャップ G5。
- **追加**: 現 `bunsetsu()` の手書きルールを撤廃。`(rid, lid)` ペアの境界確率を IPADIC 接続行列 + 訓練データから推定 (mozc は静的 bit テーブル)、Viterbi に文節境界ボーナスとして組み込む。
- **期待効果**: 長文で「東京都/京都市」のような境界が決まる。文節 UI (現状 Tab/方向キーで文節選択) も自然になる。
- **工数**: 5-8 日。
- **データ**: 形態素解析済コーパス (Mainichi/京都コーパス/BCCWJ コア) 1 万文程度。
- **リスク**: 文節境界の学習を間違えると Phase 4 のコロケーションと喧嘩する。同時評価必須。

### Phase 6 — 辞書/行列の succinct 化 (LOUDS + 接続行列圧縮)

- **目的**: ギャップ G10, G11。**Phase 7 以降で辞書を 100 万語級に拡大する前提作り**。
- **追加**: 読み trie + 表層 trie + token array の 2-trie 構成 (mozc 構造を踏襲)、Android アセットに binary build artifact として焼く。接続行列は **8-branch succinct tree** で圧縮。
- **期待効果**: 起動メモリ -50%、辞書サイズ -60-70%、語彙 5-6 倍に拡大しても辞書 + LM 合計 < 20 MB。
- **工数**: 10-15 日 (ビルドツール + ランタイム両方)。
- **データ**: 不要 (構造変換のみ)。
- **リスク**: Kotlin での rank/select 実装の性能。`Long`/`IntArray` ベースの素朴実装でも mozc 比 2-3 倍の lookup time で済むはず。実機ベンチ必須。

### Phase 7 — クラス trigram LM (or word bigram で代用)

- **目的**: ギャップ G7, G12。
- **追加**: mozc 流に**字句化された 3000 クラス + bigram** または **粗い 300 クラス + trigram**。Viterbi のノード状態を「(現ノード, 直前ノード)」ペアに拡張 (動的計画法は標準的)。スムージングは Modified Kneser-Ney。
- **期待効果**: 文脈依存の同音語選択が改善。商用 IME のコア。
- **工数**: 15-25 日 (LM ビルドパイプライン + ランタイム + 評価)。
- **データ**: 形態素解析済 Web コーパス (Wikipedia + Web crawl で 1-10 億語) または BCCWJ + Mainichi + 青空文庫 のブレンド。
- **リスク**: trigram は bigram 比でメモリが急膨張。Phase 6 の succinct 化が前提。データ品質に強く依存。

### Phase 8 — 辞書拡張 (固有名詞 / 新語 / Web 語彙)

- **目的**: ギャップ G6。
- **追加**: mozc UT 系 / NEologd / Wikipedia 見出し由来の固有名詞辞書をマージ。Phase 6 の LOUDS 辞書ビルドに乗せる。
- **期待効果**: 「東京スカイツリー」「Anthropic」「岸田文雄」など固有名詞の取りこぼし大幅減。
- **工数**: 5-10 日 (主にデータクリーニング)。
- **データ**: NEologd (要ライセンス確認)、Wikipedia 見出し抽出、jawiki/jaWordNet など。
- **リスク**: 固有名詞詰め込み過ぎると「きょうの→京野」型の誤変換 (z2term が現状回避している失敗) が再発する。コスト調整必須。

### Phase 9 — 入力誤り訂正 (KeyCorrector + typing model)

- **目的**: ギャップ G8。
- **追加**: mozc の `KeyCorrector` 相当のキー射影 + 端末入力方式 (qwerty / フリック) ごとの typing_model TSV。Composer 段で TypeCorrectedQuery を生成し、ペナルティ込みで Viterbi に投入。
- **期待効果**: モバイルでの実用性向上 (フリック誤打 / 促音脱字)。
- **工数**: 7-10 日。
- **データ**: mozc OSS `data/typing/*.tsv` が直接使える (Apache 2.0)。
- **リスク**: 誤訂正で「打ったとおり」が出ない。設定でオフにできる必要。

### Phase 10 — ニューラル rerank (任意 / 中長期)

- **目的**: 商用 IME 最上段の品質に追いつくため。
- **追加**: Phase 1 の N-best 候補を、量子化済 LSTM/Transformer-tiny で rerank。Yao 2018 流の selective softmax。Android NNAPI / TFLite に乗せる。
- **期待効果**: 同音語・長文の最終 1pt-3pt 改善。
- **工数**: 30 日 +。
- **データ**: 大規模 Web コーパス + 計算リソース。
- **リスク**: モバイル CPU/メモリで実用速度を出すのが難しい。**まず Phase 7 までで「mozc 同等」を作ってから検討**。

---

## 7. モバイル制約 (Android 上の現実解)

### 7.1 現状サイズ参考

- `kkc_lex.tsv`: **9.6 MB** (24 万行・非圧縮 TSV)
- `kkc_matrix.bin`: **3.3 MB** (品詞×品詞 int16 ベタ)
- `z2dict.txt` (SKK): 5.2 MB
- 合計アセット: **約 18 MB**
- 起動時メモリ: TSV を `HashMap<String, List<Entry>>` で全展開 → 推定 30-50 MB (Java オブジェクトヘッダ含む)

### 7.2 商用 IME 級の目安

- **辞書**: 100-150 万語を 13-20 MB (mozc の 10.4 byte/word) に収めるのは LOUDS 化で**実現可能**。Kotlin/JVM 実装は C++ より +30-50% を見込む。
- **接続行列**: クラス数 3000 で 86% 疎なら succinct tree で **3-5 MB**。z2term の現品詞数 (~1300) のままなら 1.5-2 MB に下がる。
- **アセット総量**: **30-50 MB** に収めれば Gboard 同等。z2term は Termux 派生アプリで APK は既に大きいため、+30 MB 程度の追加は許容と判断。
- **メモリ**: ヒープ常駐 100 MB 以内を目標。LOUDS + 接続行列 succinct で **40-60 MB**。
- **起動**: 現状 (24 万 TSV パース) で数百 ms 体感。LOUDS バイナリ mmap なら **< 50 ms** に短縮可能。
- **レイテンシ**: 1 文 (20-40 字) 変換 < 50 ms を目標。CachingConnector + LOUDS で達成可能。
- **電池**: Viterbi はバッチ的に短時間走るだけなので無視できる。ニューラル rerank を入れる時のみ要検証。

### 7.3 アンチパターン

- **辞書 TSV を全行ロード**: 現状方式は将来 100 万語に拡大できない。Phase 6 の優先度が高い理由。
- **コスト行列を `ShortArray` ベタ展開**: クラス数 3000 → 18 MB は OK でも、5000 になると 50 MB で破綻。
- **N-best を毎回フル走査**: A* + 早期終了が必須。

---

## 8. 次の具体的アクション

1. **直近 (今週中)**: Phase 0 を即着手。
   - 既存の M13 以降の長文事例を含む **誤変換ケース集 100-200 件** を `docs/IME-EVAL.md` に追記し、文単位の正解 / 文節単位の正解を併記。
   - 自動評価スクリプト (`scripts/eval-kkc.kt` or Python) を作り、`KkcConverter.convert()` の出力と正解を比較する CI loop を作る。mozc 流の `<reading>\t<expected>\t<labels>` 形式が扱いやすい。

2. **次の 2 週間**: Phase 1 (N-best ラティス + リランカー interface) と Phase 2 (UserHistory bigram) を平行で実装。
   - Phase 2 は単独で効果が出る + 評価指標 (2 回目以降の同文確定) が明確で、ユーザに見える改善になる。
   - Phase 1 は土台なので機能としては可視化されないが、Phase 3-5 の前提。

3. **次の 1 か月**: Phase 3 (未知語モデル) と Phase 4 (コロケーション rerank) で「**外来語混じり長文での崩壊**」と「**同音語誤変換**」を撲滅。ここまでで `mozc に対して 70-80% 程度の体感` を狙う。

4. **その後**: Phase 5 (連文節境界) → Phase 6 (succinct 化) → Phase 7 (trigram) → Phase 8 (辞書拡張)。Phase 6 を Phase 7-8 の前に必ず置く (拡張余地を作るため)。

5. **判断ポイント**: Phase 6 着手時点で **Kotlin LOUDS の性能を実機ベンチ**して、駄目なら JNI で C++ 実装の mozc コンポーネントを引き込む選択肢を検討 (Apache 2.0 でライセンス互換)。

---

## 9. 参考文献

[^kudo2011]: Taku Kudo, Toshiyuki Hanaoka, Jun Mukai, Yusuke Tabata, Hiroyuki Komatsu. "Efficient dictionary and language model compression for input method editors." Proc. WTIM 2011. <https://aclanthology.org/W11-3503.pdf>
[^gao2002]: Jianfeng Gao, Joshua Goodman, Mingjing Li, Kai-Fu Lee. "Toward a unified approach to statistical language modeling for Chinese." ACM Trans. Asian Language Information Processing, 2002. <https://dl.acm.org/doi/10.1145/595576.595578>
[^yao2018]: Jiali Yao, Raphael Shu, Xinjian Li, Katsutoshi Ohtsuki, Hideki Nakayama. "Real-time Neural-based Input Method." arXiv:1810.09309, 2018. <https://arxiv.org/abs/1810.09309>
[^okuno]: 奥野 陽. "ニューラルかな漢字変換." Qiita, 2018. <https://qiita.com/yoh_okuno/items/c4ad13daa48714bdb29e>
[^chaime]: 小町 守 (TMU). "統計的かな漢字変換の仕組み: ChaIME 徹底解説." <https://cl.sd.tmu.ac.jp/~komachi/chaime/statime.html>。および 小町 守, 森 信介, 徳永 拓之. "あいまいな日本語のかな漢字変換." SProSym 2008. <https://cl.sd.tmu.ac.jp/~komachi/papers/sprosym2008-ime.pdf>
[^atok1]: ジャストシステム. "高精度な変換エンジン | ATOK Passport." <https://atok.com/info/features/engine.html>
[^atok2]: ジャストシステム. "「ATOK Passport」Windows/Mac版に「ATOK ディープコアエンジン 2」を搭載." 2022/02/01 プレスリリース。<https://www.justsystems.com/jp/news/j12016.html>
[^apple_fm]: Apple Machine Learning Research. "Updates to Apple's On-Device and Server Foundation Language Models." 2025. <https://machinelearning.apple.com/research/apple-foundation-models-2025-updates>
[^libkkc]: ueno et al. libkkc README and src. <https://github.com/ueno/libkkc>

その他ソース:

- mozc OSS: <https://github.com/google/mozc>
  - 主要参照: `src/converter/immutable_converter.cc` (Viterbi + CachingConnector), `src/converter/nbest_generator.cc` (A* N-best), `src/converter/connector.cc` (接続行列 succinct lookup), `src/converter/segmenter.h` (文節境界), `src/converter/key_corrector.cc` (誤入力訂正), `src/prediction/user_history_predictor.cc` (bigram ユーザ学習), `src/rewriter/collocation_rewriter.cc` (共起), `src/rewriter/user_segment_history_rewriter.h` (文節学習), `src/storage/louds/` (LOUDS trie), `src/data/dictionary_oss/` (辞書ソース 1.29M 行), `src/data/typing/` (typing model TSV)。
- MeCab: <https://github.com/taku910/mecab> (IPADIC + Viterbi の原典)
- IPADIC: <http://taku910.github.io/mecab/dic.html>
- Microsoft Research IME Corpus: <https://www.microsoft.com/en-us/research/publication/microsoft-research-ime-corpus/>
- Goodman & Gao. "The Use of Clustering Techniques for Language Modeling." <https://www.microsoft.com/en-us/research/wp-content/uploads/2017/01/The-Use-of-Clustering-Techniques-for-Language-Modeling.pdf>
- 「Google日本語入力」開発者インタビュー (ITmedia 2009): <https://www.itmedia.co.jp/news/articles/0912/07/news099.html>
- 「Google 日本語入力」開発チーム インタビュー (日経 xTECH 2013): <https://xtech.nikkei.com/it/article/COLUMN/20130226/458943/>
- ATOK 変換強度学習解説 (PC Watch): <https://pc.watch.impress.co.jp/docs/news/1643461.html>
- Wikipedia 「Google 日本語入力」: <https://ja.wikipedia.org/wiki/Google_%E6%97%A5%E6%9C%AC%E8%AA%9E%E5%85%A5%E5%8A%9B>

---

## 付録 A: mozc Viterbi コア抜粋 (immutable_converter.cc:819-)

```cpp
// CachingConnector で内側ループの transition cost lookup を 2048-way キャッシュ。
inline void ViterbiInternal(const Connector& connector, size_t pos,
                            size_t right_boundary, Lattice* lattice) {
  CachingConnector conn(connector);
  for (Node* rnode : lattice->begin_nodes(pos)) {
    if (rnode->end_pos > right_boundary) { rnode->prev = nullptr; continue; }
    conn.ResetCacheIfNecessary(rnode->lid);
    int best_cost = kVeryBigCost;
    Node* best_node = nullptr;
    for (Node* lnode : lattice->end_nodes(pos)) {
      if (lnode->prev == nullptr) continue;
      int cost = lnode->cost + conn.GetTransitionCost(lnode->rid, rnode->lid);
      if (cost < best_cost) { best_cost = cost; best_node = lnode; }
    }
    rnode->prev = best_node;
    rnode->cost = best_cost + rnode->wcost;
  }
}
```

z2term `KkcConverter.segments()` のコア (Kotlin) と**完全に同じ構造**であることに注意。違いは「**z2term は 1-best のみ・キャッシュ無し・後段リランカー無し・ユーザ学習無し**」という外側の欠落である。Viterbi 本体は同じなので、Phase 1-5 の上物を順次追加することで mozc 同等品質に到達できる、というのが本書の中心的主張。
