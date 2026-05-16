# Z2Term assets ディレクトリ

このディレクトリには Alpine Linux の rootfs アーカイブを配置します。

## 必要なファイル

| ファイル名 | アーキテクチャ | 入手元 |
|---|---|---|
| `alpine-minirootfs-aarch64.tar.gz` | arm64-v8a (64bit ARM) | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/ |
| `alpine-minirootfs-armv7.tar.gz` | armeabi-v7a (32bit ARM) | https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/ |

## ダウンロード手順

最新バージョンの URL は Alpine 公式サイト (https://alpinelinux.org/downloads/) で確認してください。

```bash
# 64bit ARM 用（最新版を確認して URL を調整）
wget -O alpine-minirootfs-aarch64.tar.gz \
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz

# 32bit ARM 用
wget -O alpine-minirootfs-armv7.tar.gz \
  https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/armv7/alpine-minirootfs-3.21.0-armv7.tar.gz
```

## SHA256 検証（推奨）

各バージョンのチェックサムは Alpine 公式から取得して検証してください。

```bash
# チェックサムファイルを取得
wget https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.0-aarch64.tar.gz.sha256

# 検証
sha256sum -c alpine-minirootfs-3.21.0-aarch64.tar.gz.sha256
```

## サイズの目安

- 圧縮済み: 約 3MB
- 展開後: 約 7-8MB

APK 内には両アーキテクチャ分を入れるので、合計約 6MB ほど増えます。

## ファイル名について

DistroInstaller.kt は以下のファイル名を期待しています。リネームしてから配置してください:

- `alpine-minirootfs-aarch64.tar.gz` (バージョン番号を含めない)
- `alpine-minirootfs-armv7.tar.gz` (同上)

バージョン管理は別途行います（rootfs ZIP 内の `/etc/alpine-release` を参照すれば確認可能）。
