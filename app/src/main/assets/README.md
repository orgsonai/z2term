# Z2Term assets

APK に同梱するアセットを配置します。Linux rootfs はAPKへ同梱せず、
選択したOSの公式配布物を初回起動時にダウンロードします。

## 自動生成

```bash
bash scripts/build-bundle.sh        # z2root + fonts
```

## フォント

`assets/fonts/` のフォントは git に入っています（1.0.4 から。いずれも SIL Open Font License 1.1）。
外から取る 3 つを新しい版へ入れ替えるときは `FORCE=1 bash scripts/fetch-fonts.sh`。
`Z2Mono-Regular.ttf` は取るものではなく、`scripts/z2-mono/build.py` と組で入れ替えます。

| ファイル名 | 入手元 |
|---|---|
| `IBMPlexMono-Regular.ttf` | https://github.com/IBM/plex |
| `JetBrainsMono-Regular.ttf` | https://github.com/JetBrains/JetBrainsMono |
| `FiraCode-Regular.ttf` | https://github.com/tonsky/FiraCode |
| `Z2Mono-Regular.ttf` | 自作（1.0.5 から）。`scripts/z2-mono/build.py` から生成 |

未配置の候補はグレーアウトされ、System Monospaceへフォールバックします。
