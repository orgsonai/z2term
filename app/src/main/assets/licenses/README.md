# OSS ライセンス全文

このディレクトリには、同梱 OSS のライセンス全文を `.txt` で配置します。通常は SPDX ID 名を
使い、著作権者固有の原文がある場合は `OssComponent.licenseAsset` で専用ファイルを指定します。
アプリの設定 →「OSS ライセンス / 対応ソース」画面から UTF-8 で読み出して表示します。

⚠ **`legal/OssComponents.kt` に項目を足したら、対応する全文ファイルもここに置くこと。**
`.txt` が無いライセンス ID については、アプリが「公式 URL を参照してください」のフォールバック
文言を表示します（ビルドは通るので、抜けても気付けません）。GPL 等の **コピーレフトの全文同梱は
法的要件** に近いので、公開ビルド前には必ず配置すること。

## 必要なファイル

| ファイル名 | 取得元 |
|---|---|
| `GPL-3.0.txt` | https://www.gnu.org/licenses/gpl-3.0.txt |
| `GPL-2.0.txt` | https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt （同梱辞書 `z2dict.txt` = SKK-JISYO.L 用） |
| `Apache-2.0.txt` | https://www.apache.org/licenses/LICENSE-2.0.txt |
| `MIT.txt` | https://spdx.org/licenses/MIT.txt |
| `BSD-2-Clause.txt` | https://spdx.org/licenses/BSD-2-Clause.txt |
| `BSD-3-Clause.txt` | https://spdx.org/licenses/BSD-3-Clause.txt |
| `OFL-1.1.txt` | https://openfontlicense.org/documents/OFL.txt |
| `0BSD.txt` | https://spdx.org/licenses/0BSD.txt |
| `CC-BY-SA-4.0.txt` | https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt （共起データ `kkc_colloc.bloom` の元 = 日本語版ウィキペディア用） |
| `IPADIC-NAIST.txt` | https://raw.githubusercontent.com/taku910/mecab/master/mecab-ipadic/COPYING （SPDX に ID が無いため原文をそのまま。ICOT Free Software の条件も含む） |
| `Bouncy-Castle-MIT.txt` | Bouncy Castle 1.85.2 同梱原文 |
| `MBassador-MIT.txt` | MBassador 1.3.2 公式 LICENSE |
| `SLF4J-MIT.txt` | SLF4J 2.0.18 公式 LICENSE.txt |
| `JZlib-BSD-3-Clause.txt` | JSch 同梱 `META-INF/LICENSE.JZlib.txt` |
| `jBCrypt-ISC.txt` | JSch 同梱 `META-INF/LICENSE.jBCrypt.txt` |

## 一括取得スクリプト (リポジトリルートで実行)

```sh
cd app/src/main/assets/licenses
for spec in \
  "GPL-3.0         https://www.gnu.org/licenses/gpl-3.0.txt" \
  "GPL-2.0         https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt" \
  "Apache-2.0      https://www.apache.org/licenses/LICENSE-2.0.txt" \
  "MIT             https://spdx.org/licenses/MIT.txt" \
  "BSD-2-Clause    https://spdx.org/licenses/BSD-2-Clause.txt" \
  "BSD-3-Clause    https://spdx.org/licenses/BSD-3-Clause.txt" \
  "OFL-1.1         https://openfontlicense.org/documents/OFL.txt" \
  "0BSD            https://spdx.org/licenses/0BSD.txt" \
  "CC-BY-SA-4.0    https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt" \
  "IPADIC-NAIST    https://raw.githubusercontent.com/taku910/mecab/master/mecab-ipadic/COPYING"; do
  name=${spec%% *}
  url=${spec##* }
  curl -fsSL "$url" -o "${name}.txt" && echo "OK ${name}.txt"
done
```

## 置かないもの

rootfs（Alpine / Ubuntu / Arch / Kali）とその中のパッケージ（tigervnc, dropbear, vim, zsh …）は
**APK に同梱せず初回起動時に公式配布物を取得する**ので、こちらの告知対象ではありません。
0.8.328 で proot(GPL-2.0) / talloc(LGPL-3.0) を自前の z2root（本体 GPL-3.0）へ置き換えた時点で、
`Vim.txt` / `Zsh.txt` も不要になったため 0.8.473 で削除しました。
