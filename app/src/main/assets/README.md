# Z2Term assets

APK に同梱するアセットを配置します。Linux rootfs はAPKへ同梱せず、
選択したOSの公式配布物を初回起動時にダウンロードします。

## 自動生成

```bash
bash scripts/build-bundle.sh        # z2root + fonts
```

## フォント

`assets/fonts/` の 3 つは git に入っています（1.0.4 から。いずれも SIL Open Font License 1.1）。
新しい版へ入れ替えるときは `FORCE=1 bash scripts/fetch-fonts.sh`。

| ファイル名 | 入手元 |
|---|---|
| `IBMPlexMono-Regular.ttf` | https://github.com/IBM/plex |
| `JetBrainsMono-Regular.ttf` | https://github.com/JetBrains/JetBrainsMono |
| `FiraCode-Regular.ttf` | https://github.com/tonsky/FiraCode |

未配置の候補はグレーアウトされ、System Monospaceへフォールバックします。
