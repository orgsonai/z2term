# IME Phase 0 ハンドオフ — 評価基盤の導入

最終更新: 2026-06-01
ベース: commit `08025ac` (Viterbi/IPADIC 刷新 [WIP]) の続き
ブランチ: main

> 本セッションでは Phase 0（評価基盤）を完了。Phase 1〜は次セッション以降で進める。
> 全体方針と段階導入プランは [`IME-ARCHITECTURE-RESEARCH.md`](IME-ARCHITECTURE-RESEARCH.md)、
> 評価運用は [`IME-EVAL.md`](IME-EVAL.md) を参照。

## 背景

直近コミット `08025ac` で IME を SKK 最長一致 → IPADIC コスト+品詞接続行列+Viterbi
（mecab-ipadic 2.7.0 由来）へ刷新した。しかし

- 実機実打検証が未済（WIP の理由）
- 「メジャー IME と肩を並べる」までに何が足りないか不明

の 2 点が残っていた。本セッションでは

1. **メジャー IME (mozc / ATOK / iOS / MS-IME) のアーキテクチャを mozc ソース等の一次資料から深掘り**
2. **段階導入プラン（Phase 0〜10）を策定**
3. **Phase 0 = 評価基盤を実装** (以降の改善を再現可能に判定するため)

を行った。実機検証は Phase 0 の改善判定ループに代替されるため省略可（必要なら Phase 1〜と並行で実施）。

## このセッションのコミット

| Commit | 種別 | 概要 |
|---|---|---|
| （本ハンドオフのコミット） | docs+test | IME 設計研究 / 評価セット 134 ケース / JVM 評価ランナー / KkcConverter の JVM テスト可能化 |

## 1. 設計研究 — `docs/IME-ARCHITECTURE-RESEARCH.md` (451 行, 40 KB)

mozc ソース (`immutable_converter.cc`, `NBestGenerator`, LOUDS dictionary 等)、
WTIM 2011 (Kudo et al.) 論文 PDF、ATOK 公式リリース、Gao+2002 など一次資料からまとめた。

### 重要な発見

1. **mozc の Viterbi 本体は z2term `KkcConverter.segments()` とほぼ同構造**（付録 A で対比）。
   z2term に欠けているのは Viterbi 本体ではなく**外側のレイヤー**:
   - N-best ラティス + リランカー段
   - ユーザ確定 bigram のオンライン学習 (`UserHistoryPredictor` 相当)
   - コロケーション / 共起 rerank
   - 未知語の文字種別モデル
   - 連文節境界の独立モデル
   - 50+ 段の Rewriter chain
   - KeyCorrector (打鍵ミス補正)

2. **mozc は trigram ではなく字句化 3019 クラスの bigram**。z2term は IPADIC ~1300 品詞のままでも
   字句化粒度を上げれば bigram のままで戦える。

3. **mozc の辞書は 134 万語を 13.3 MB に LOUDS×2 + token + Katakana bit で圧縮**。
   z2term の HashMap 全展開（推定 30-50MB ヒープ）は将来拡張のボトルネック。

### 段階導入プラン (要点のみ — 詳細は研究ドキュメント §6)

| Phase | 内容 | 工数 | 依存 | 体感効果 |
|---|---|---|---|---|
| **0** | 評価基盤 | 2-3 日 | — | 判定可能化 ✅ 完了 |
| **1** | N-best ラティス + リランカー interface | 5-7 日 | 0 | 土台のみ |
| **2** | UserHistory bigram 学習 | 3-5 日 | 1 | 単独で体感大 |
| **3** | 未知語: 文字種別モデル | 3-4 日 | 1 | カタカナ崩壊抑止 |
| **4** | コロケーション/共起 rerank | 5-7 日 | 1 | 同音語精度 |
| **5** | 連文節境界モデル | 5-8 日 | 1 | 長文の境界自然化 |
| **6** | LOUDS succinct 化 | 10-15 日 | — | 拡張余地確保 |
| **7** | クラス bigram/trigram LM | 15-25 日 | 6 | 商用 IME のコア |
| **8** | 固有名詞/新語辞書拡張 | 5-10 日 | 4, 7 | proper の救済 |
| **9** | KeyCorrector + typing model | 7-10 日 | 1 | モバイル誤打補正 |
| **10** | ニューラル rerank (任意) | 30+ 日 | 7 | 最後の数 pt |

研究ドキュメントの推奨パス:
- 直近: Phase 2 を単独優先（単独で体感改善 + ユーザに見える）
- Phase 1 と Phase 2 は順序を問わない（Phase 1 は土台、Phase 2 は単独で動く）
- 1 か月で Phase 3-4 まで到達できれば **mozc 比 70-80% 体感** が現実的目標

## 2. 評価基盤 — Phase 0 成果物

### 2-1. 評価データ `app/src/test/resources/kkc_eval.tsv`

134 ケース、13 カテゴリタグ付き。形式:
```
<reading>\t<expected>\t<tag1>,<tag2>,...
```

**期待値は商用 IME（Google 日本語入力等）の標準出力を基準**にする (z2term 現状ではない)。
これにより失敗 = ギャップとして可視化される。詳細形式は `IME-EVAL.md` §「TSV 形式」。

### 2-2. JVM 評価ランナー `app/src/test/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcEvalTest.kt`

JUnit 4 のユニットテスト 1 個。
- `kkc_eval.tsv` を読む
- `KkcConverter.convert(reading)` で変換 (Viterbi 単発、`convertFlexible` ではない)
- 全体正解率 + カテゴリ別正解率 + 失敗ケース上位 40 件をコンソール + `app/build/kkc-eval-report.txt` に出力
- **回帰検出は意図的に未設定**（Phase 0 はベースライン記録が目的、回帰しきい値は Phase 1 着手時に別 @Test で導入予定）

実行:
```sh
sh ./gradlew :app:testFullDebugUnitTest --tests '*KkcEvalTest*'
```
> 注: `gradlew` は本リポの未コミット差分でモードが落ちており、`sh` 起動が必要。

### 2-3. `KkcConverter` の JVM テスト可能化

`ensureLoaded(Context)` のコアを `loadFromStreams(InputStream, BufferedReader)` に抽出。
Android Context 無しでロード可能になり、JVM ユニットテストから直接呼べる。
既存 `ensureLoaded(Context)` の挙動は変えていない（実装委譲のみ）。

```kotlin
// テスト側の使い方
matrix.inputStream().use { ms ->
    lex.bufferedReader(Charsets.UTF_8).use { lr ->
        KkcConverter.loadFromStreams(ms, lr)
    }
}
```

### 2-4. ベースライン (`commit 08025ac` + 評価基盤導入時点)

| カテゴリ | 正解率 | 内訳 |
|---|---:|---:|
| **OVERALL** | **37.31%** | 50/134 |
| short | 40.22% | 37/92 |
| medium | 45.83% | 11/24 |
| long | **11.11%** | 2/18 |
| proper | **0.00%** | 0/11 |
| katakana | 70.00% | 7/10 |
| homophone | 33.33% | 4/12 |
| verb | 31.67% | 19/60 |
| adj | 37.50% | 6/16 |
| particle | 42.86% | 6/14 |
| number | 30.00% | 3/10 |
| greeting | 50.00% | 3/6 |
| business | 40.00% | 4/10 |
| daily | 40.00% | 18/45 |

失敗 84 件は 5 系統に分類済み（`IME-EVAL.md` §「失敗パターンの分類」）。
各系統に対応する Phase が割り当てられている。

## 3. 次セッションの作業候補（優先順）

ユーザは「次のセッションで進めます」とのこと。以下から選択:

1. **Phase 2 (UserHistory bigram 学習) を着手** — 推奨。単独で体感改善 + 既存 `ImeHistoryStore` の拡張で済む可能性
2. **Phase 1 (N-best ラティス + リランカー interface) を着手** — Phase 3-5 全ての土台。本体直接効果は無いが必須
3. **評価セット拡張** — Phase 2-4 着手前に long / proper / homophone を各倍に増やすと判定精度が上がる（IME-EVAL.md §「拡張の指針」）
4. **Phase 1 と Phase 2 を平行で着手** — 研究ドキュメントの推奨パス

### Phase 1 / Phase 2 を始める前のチェック

- `KkcEvalTest` を一度実行してベースライン (37.31%) が再現することを確認
- Phase 2 着手なら `ImeHistoryStore.kt` を読んで現状の学習機構を把握
- Phase 1 着手なら `KkcConverter.segments()` を「ラティス + 後ろ向きパスで N-best 取得」に拡張する設計を検討（A* / 簡易 K-shortest paths のどちらかを選ぶ）

### 注意点

- **既存挙動を壊さない**: Phase 1 で `segments()` 自体の API は維持し、N-best は新メソッド `nbest(reading, k)` を追加する形にすると安全
- **回帰判定**: Phase 1 以降は `KkcEvalTest` に「OVERALL が現状ベースラインを下回ったら失敗」のしきい値テスト (`evaluateNoRegression`) を追加する
- **辞書ビルド方針**: Phase 8 まで触らない予定。固有名詞除外は意図的（コスト調整できる前に入れると「きょうの→京野」型が再発する）

## 4. 触っていないもの（意図的）

- `KanaKanjiConverter` / `ComposingState` / `TerminalScreen` の挙動 — Phase 0 の範囲外
- 実機 APK ビルド — Phase 0 は JVM ユニットテストで完結
- `gradlew` / `scripts/*.sh` のモード落ち — Phase 0 と無関係の既存差分
- 既存 SKK 辞書 (z2dict.txt) と SUPPLEMENT_WORDS — Phase 8 で扱う

## 5. 参考リンク

- 全体方針: [`docs/IME-ARCHITECTURE-RESEARCH.md`](IME-ARCHITECTURE-RESEARCH.md)
- 評価運用: [`docs/IME-EVAL.md`](IME-EVAL.md)
- 評価データ: [`app/src/test/resources/kkc_eval.tsv`](../app/src/test/resources/kkc_eval.tsv)
- 評価ランナー: [`app/src/test/java/.../keyboard/KkcEvalTest.kt`](../app/src/test/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcEvalTest.kt)
- 現行 Kkc: [`app/src/main/java/.../keyboard/KkcConverter.kt`](../app/src/main/java/com/zerotoship/z2term/ui/terminal/keyboard/KkcConverter.kt)
- 直近コミット: `08025ac` (Viterbi/IPADIC 刷新 [WIP]) → 本ハンドオフのコミット
