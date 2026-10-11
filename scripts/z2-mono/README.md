# Z2 Mono の生成スクリプト

`app/src/main/assets/fonts/Z2Mono-Regular.ttf` は、このディレクトリの `build.py` から作ったものです。
字の形を線の通り道（折れ線と円弧）で書き、同じ太さ・同じ丸みで肉付けして TTF にします。
既存フォントの輪郭は使っていません。ライセンスは SIL Open Font License 1.1 です。

```sh
python3 -m venv /tmp/z2mono-venv
/tmp/z2mono-venv/bin/pip install -r scripts/z2-mono/requirements.txt
/tmp/z2mono-venv/bin/python scripts/z2-mono/build.py /tmp/Z2Mono-Regular.ttf
cmp /tmp/Z2Mono-Regular.ttf app/src/main/assets/fonts/Z2Mono-Regular.ttf
```

TTF に作った日時を埋め込まないので、同じ版の道具からは同じファイルができます。
アプリのビルドではこのスクリプトを実行せず、できあがった TTF を使います。
TTF とスクリプトは必ず組で入れ替えます。
