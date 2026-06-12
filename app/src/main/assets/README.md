# Z2Term assets ディレクトリ

このディレクトリには **同梱用** ディストロ rootfs と、ターミナル用フォントを配置します。

## ⚠️ 自動生成: `scripts/build-bundle.sh` を使ってください

M7 同梱方針への移行に伴い、Alpine rootfs は **`scripts/build-bundle.sh`** で
カスタマイズ済みのものを APK に同梱します。手動配置は推奨されません。

```bash
bash scripts/build-bundle.sh        # PRoot + Alpine 両方
# 出力 (full フレーバー専用。foss は同梱せず実行時 DL):
#   app/src/full/jniLibs/arm64-v8a/libproot.so       (Termux 由来)
#   app/src/full/jniLibs/arm64-v8a/libproot_loader.so
#   app/src/full/assets/alpine-minirootfs-aarch64.tgz  (zsh/bash/openssh/screen 込)
```

## 同梱パッケージ一覧

`scripts/alpine-packages.txt` を参照。M7 既定は Tier 0+1+2 の 32 パッケージ
(約 12MB 圧縮)。zsh / bash / openssh / screen に加え coreutils / findutils /
grep / sed / gawk / less / shadow / procps-ng まで含む。

## ファイル名規約

`DistroInstaller.kt` (DistroSpec.ALPINE) は以下を期待:

- `alpine-minirootfs-aarch64.tgz`  (arm64-v8a 用、必須)
- `alpine-minirootfs-armv7.tgz`     (armv7 用、ABI 32bit ビルド時)

**`.tar.gz` ではなく `.tgz`** にしているのは、aapt が `.tar.gz` を「すでに圧縮済み
コンテンツ」と判定して assets 内で自動解凍 → `.tar` にリネーム保存してしまうため。
ランタイムから `assets.open("...tar.gz")` が見つからない事故を避けるため
`.tgz` (aapt の特別扱い対象外) を使う。

## 手動 DL する場合 (FOSS 配布用)

`DistroDownloader` がランタイムで取得する場合の URL は以下:

| ファイル名 | アーキテクチャ | 入手元 |
|---|---|---|
| `alpine-minirootfs-aarch64.tgz` | arm64-v8a | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/ |
| `alpine-minirootfs-armv7.tgz`    | armeabi-v7a | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/ |

公式 `alpine-minirootfs-X.tar.gz` を取得して `.tgz` にリネームすると最小構成
(zsh など無し) で動く。`build-bundle.sh` 経由ならパッケージ込で生成される。

## サイズ目安

| 構成 | 圧縮 | 展開後 |
|---|---|---|
| Alpine minirootfs (公式そのまま) | 約 3MB | 約 7MB |
| Alpine + Tier 0+1+2 (Z2Term 既定) | 約 12MB | 約 40MB |
| Alpine + Tier 0..4 (フル) | 約 35MB | 約 110MB |

## カスタムフォント

`assets/fonts/` ディレクトリに TTF/OTF を置くと設定画面で選択可能。
詳細は `app/src/main/java/com/zerotoship/z2term/ui/theme/TerminalFonts.kt`。

| ファイル名 | 入手元 |
|---|---|
| `IBMPlexMono-Regular.ttf` | https://github.com/IBM/plex/tree/master/IBM-Plex-Mono/fonts/complete/ttf |
| `JetBrainsMono-Regular.ttf` | https://github.com/JetBrains/JetBrainsMono/releases |
| `FiraCode-Regular.ttf` | https://github.com/tonsky/FiraCode/releases |

未配置のフォント候補は設定画面でグレーアウトされ、選択しても System Monospace に
フォールバックします。
