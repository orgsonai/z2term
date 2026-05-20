# PC から z2term の Alpine へ SSH 接続する手順

z2term には Alpine + **dropbear** (軽量 SSH サーバ) が同梱されているので、Android 端末上の
z2term を PC から SSH ログイン / rsync / scp して使えます。

> ⚠️ **なぜ OpenSSH の `sshd` ではなく dropbear か**
> OpenSSH `sshd` は権限分離 (privsep) で実 UID を本当に落とせることを要求しますが、
> PRoot 環境では実 UID はアプリ UID のままなので `permanently_set_uid: was able to
> restore old egid` となり接続が即 reset されます。dropbear は PRoot 下でも問題なく
> 動くため、z2term はこちらを使います。

## 1. z2term 側 (Android) の準備

最も簡単なのは **設定 (⚙) → 「PC からの SSH 接続」→「sshd 起動」ボタン**です。
これは内部で下記と同等の dropbear セットアップを実行します。手動でやる場合は以下。

### 1.1 root にパスワードを設定 (パスワード認証する場合)

```sh
passwd
# 新しいパスワードを 2 回入力
```

PRoot 経由なので「root」は仮想 root です。Android の実 UID はアプリ UID のままで
特権を持ちません (空パスワードでは dropbear が接続を拒否します)。

### 1.2 dropbear のホスト鍵生成 + 起動

特権ポート (1〜1023) は Android kernel が拒否するので `2222` を使います。

```sh
mkdir -p /etc/dropbear
# ホスト鍵 (初回のみ)。-R を付ければ起動時に自動生成もされる
dropbearkey -t ed25519 -f /etc/dropbear/dropbear_ed25519_host_key
dropbearkey -t rsa -s 2048 -f /etc/dropbear/dropbear_rsa_host_key

# 起動 (バックグラウンド常駐、root ログイン・パスワード認証は既定で許可)
dropbear -p 2222 -R -E 2>/tmp/dropbear.log

# 確認
ps -ef | grep dropbear
```

### 1.3 端末の IP アドレスを確認

`ip a` は PRoot では netlink 制約で動きません。**設定の「PC からの SSH 接続」に
端末 IPv4 が自動表示**されるのでそれを使うのが確実です。シェルからは:

```sh
cat /proc/net/fib_trie | grep -oE '192\.168\.[0-9]+\.[0-9]+' | sort -u
```

PRoot は host ネットワーク名前空間を共有するので、Android が見ている IP が
そのまま使えます (Android 設定 → Wi-Fi 詳細でも確認可)。

## 2. PC 側からの接続

PC と Android が **同じ Wi-Fi (LAN)** にいることを確認してから:

```sh
# ログイン
ssh -p 2222 root@<Android の IP>          # 例: ssh -p 2222 root@192.168.1.42

# ファイルを端末へ送る (scp はポート大文字 -P に注意)
scp -P 2222 ./file root@<IP>:/root/
rsync -av -e 'ssh -p 2222' ./dir/ root@<IP>:/root/dir/

# 端末から取り出す
rsync -av -e 'ssh -p 2222' root@<IP>:/root/dir/ ./dir/
```

初回は host 鍵の信頼確認が出るので `yes` で進め、1.1 のパスワードを入力します。

### 2.1 公開鍵認証にする (推奨)

毎回パスワード入力を避けたい場合:

```sh
# PC 側 (鍵がまだなら作成)
ssh-keygen -t ed25519

# 公開鍵を z2term へコピー
ssh-copy-id -p 2222 root@<Android IP>
# あるいは端末側で /root/.ssh/authorized_keys に手で貼り付け (パーミッション 600)
```

dropbear は `~/.ssh/authorized_keys` を読むので、これだけで鍵認証になります。

## 3. 注意事項・トラブルシュート

### Android が dropbear を kill してしまう

- z2term は **フォアグラウンドサービス** で常駐するため、アプリ通知が出ている
  間は OS から殺されません (設定の「バックグラウンド常駐」ON)。
- 念のため省電力ホワイトリストに z2term を追加: 設定 → アプリ → z2term →
  バッテリー → 「最適化しない」。

### LAN にいる別 PC から見つからない

- Wi-Fi の AP が「クライアント分離 (AP isolation)」を有効にしていると同一 LAN でも
  到達不可。ルーター設定を確認。
- ファイアウォール (PC 側) で 2222 をブロックしていないか確認。

### Termux と違って 22 番ポートが使えない

特権ポート (1〜1023) への bind は Android kernel が拒否します (`CAP_NET_BIND_SERVICE`
が無い)。`2222` 等の 1024 以上のポートを使ってください。

### `rsync` / `scp` が動かない

- 端末側に `rsync` (同梱済) が必要。`scp` は **`-P` (大文字)** でポート指定。
- dropbear は SFTP サブシステムを持たないため、新しめの OpenSSH の `scp`
  (既定 SFTP) が失敗する場合は `scp -O` (旧 SCP プロトコル) を使うか `rsync` を推奨。
