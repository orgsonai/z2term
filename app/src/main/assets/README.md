# Z2Term assets ディレクトリ

このディレクトリにはディストロ rootfs アーカイブ + ターミナル用フォントを
配置します。M3 以降は複数ディストロ、M4 以降はカスタムフォントに対応。

## 必要なファイル

### Alpine Linux (デフォルト)

| ファイル名 | アーキテクチャ | 入手元 |
|---|---|---|
| `alpine-minirootfs-aarch64.tar.gz` | arm64-v8a (64bit ARM) | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/ |
| `alpine-minirootfs-armv7.tar.gz` | armeabi-v7a (32bit ARM) | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/ |

### Ubuntu (オプション)

| ファイル名 | アーキテクチャ | 入手元 |
|---|---|---|
| `ubuntu-minirootfs-aarch64.tar.gz` | arm64-v8a | https://cloud-images.ubuntu.com/minimal/releases/noble/release/ |
| `ubuntu-minirootfs-armv7.tar.gz` | armeabi-v7a | 同上 (armhf) |

Ubuntu 公式 cloud-image の `*-arm64-root.tar.xz` を取得し、`tar.gz` に再圧縮して配置します:

```bash
# 例: 24.04 LTS noble の arm64 base
curl -o ubuntu.tar.xz \
  https://cloud-images.ubuntu.com/minimal/releases/noble/release/ubuntu-24.04-minimal-cloudimg-arm64-root.tar.xz
xz -d ubuntu.tar.xz
gzip ubuntu.tar
mv ubuntu.tar.gz ubuntu-minirootfs-aarch64.tar.gz
```

## ダウンロード手順 (Alpine)

```bash
wget -O alpine-minirootfs-aarch64.tar.gz \
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz

wget -O alpine-minirootfs-armv7.tar.gz \
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/alpine-minirootfs-3.21.0-armv7.tar.gz
```

## SHA256 検証 (推奨)

```bash
wget https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz.sha256
sha256sum -c alpine-minirootfs-3.21.0-aarch64.tar.gz.sha256
```

## サイズの目安

| ディストロ | 圧縮済み | 展開後 |
|---|---|---|
| Alpine | 約 3MB | 約 7-8MB |
| Ubuntu | 約 30MB | 約 80MB |

両ディストロ + 両アーキテクチャ全部入れると APK サイズが ~70MB 膨らみます。
必要なものだけ配置することを推奨。

## ファイル名規約

`DistroInstaller.kt` は以下のファイル名を期待しています。バージョン番号は含めず、
固定名にリネームしてから配置してください:

- `alpine-minirootfs-aarch64.tar.gz`
- `alpine-minirootfs-armv7.tar.gz`
- `ubuntu-minirootfs-aarch64.tar.gz`
- `ubuntu-minirootfs-armv7.tar.gz`

バージョン管理は別途、ディストロ内の `/etc/os-release` で確認してください。

## カスタムフォント (M4)

`assets/fonts/` ディレクトリにフォント TTF/OTF ファイルを配置すると、設定画面の
フォントセクションで選択可能になります。

| ファイル名 | 入手元 |
|---|---|
| `IBMPlexMono-Regular.ttf` | https://github.com/IBM/plex/tree/master/IBM-Plex-Mono/fonts/complete/ttf |
| `JetBrainsMono-Regular.ttf` | https://github.com/JetBrains/JetBrainsMono/releases |
| `FiraCode-Regular.ttf` | https://github.com/tonsky/FiraCode/releases |

未配置のフォント候補は設定画面でグレーアウトされ、選択しても System Monospace に
フォールバックします。

```bash
mkdir -p app/src/main/assets/fonts
curl -L -o app/src/main/assets/fonts/IBMPlexMono-Regular.ttf \
  https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf
```
