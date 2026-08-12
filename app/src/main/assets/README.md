# Z2Term assets

`full` / `foss` 共通のアセットを配置します。Linux rootfs はAPKへ同梱せず、
選択したOSの公式配布物を初回起動時にダウンロードします。

## 自動生成

```bash
bash scripts/build-bundle.sh        # z2root + fonts
```

## カスタムフォント

`assets/fonts/` にTTF/OTFを置くと設定画面で選択できます。

| ファイル名 | 入手元 |
|---|---|
| `IBMPlexMono-Regular.ttf` | https://github.com/IBM/plex |
| `JetBrainsMono-Regular.ttf` | https://github.com/JetBrains/JetBrainsMono |
| `FiraCode-Regular.ttf` | https://github.com/tonsky/FiraCode |

未配置の候補はグレーアウトされ、System Monospaceへフォールバックします。
