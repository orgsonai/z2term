# OSS ライセンス全文

このディレクトリには、同梱 OSS の **SPDX ID 名** で各ライセンスの全文を `.txt` で配置します。
アプリの設定 →「OSS ライセンス / 対応ソース」画面でタップしたとき、`<SPDX-ID>.txt` を
UTF-8 で読み出して表示します。

## 必要なファイル

以下を **このディレクトリに配置** してください。プレースホルダ (`*.placeholder`) が同梱されている
ものは、適用時に下記コマンドで公式 URL から取得し、`.txt` にリネームしてください。

| ファイル名 | 取得元 |
|---|---|
| `GPL-3.0.txt` | https://www.gnu.org/licenses/gpl-3.0.txt |
| `LGPL-2.1.txt` | https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt |
| `Apache-2.0.txt` | https://www.apache.org/licenses/LICENSE-2.0.txt |
| `MIT.txt` | https://spdx.org/licenses/MIT.txt |
| `BSD-2-Clause.txt` | https://spdx.org/licenses/BSD-2-Clause.txt |
| `BSD-3-Clause.txt` | https://spdx.org/licenses/BSD-3-Clause.txt |
| `OFL-1.1.txt` | https://openfontlicense.org/documents/OFL.txt |
| `0BSD.txt` | https://spdx.org/licenses/0BSD.txt |
| `Vim.txt` | https://spdx.org/licenses/Vim.txt |
| `Zsh.txt` | https://spdx.org/licenses/Zsh.txt |

## 一括取得スクリプト (リポジトリルートで実行)

```sh
cd app/src/main/assets/licenses
for spec in \
  "GPL-3.0       https://www.gnu.org/licenses/gpl-3.0.txt" \
  "Apache-2.0    https://www.apache.org/licenses/LICENSE-2.0.txt" \
  "MIT           https://spdx.org/licenses/MIT.txt" \
  "BSD-2-Clause  https://spdx.org/licenses/BSD-2-Clause.txt" \
  "BSD-3-Clause  https://spdx.org/licenses/BSD-3-Clause.txt" \
  "OFL-1.1       https://openfontlicense.org/documents/OFL.txt" \
  "0BSD          https://spdx.org/licenses/0BSD.txt" \
  "Vim           https://spdx.org/licenses/Vim.txt" \
  "Zsh           https://spdx.org/licenses/Zsh.txt"; do
  name=${spec%% *}
  url=${spec##* }
  curl -fsSL "$url" -o "${name}.txt" && echo "OK ${name}.txt"
done
```

`.txt` が無いライセンス ID については、アプリが「公式 URL を参照してください」のフォールバック
文言を表示します（ビルドは通る）。GPL/LGPL 等の **コピーレフトの全文同梱は法的要件** に近い
ので、公開ビルド前には必ず配置すること。
