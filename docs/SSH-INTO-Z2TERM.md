# PC から z2term の Alpine へ SSH 接続する手順

z2term には Alpine + `openssh-server` が同梱されているので、Android 端末上の
z2term を PC から SSH ログインして使えます。

## 1. z2term 側 (Android) の準備

z2term を起動して Alpine に入った状態で、以下を順に実行します。

### 1.1 root にパスワードを設定

```sh
passwd
# 新しいパスワードを 2 回入力
```

PRoot 経由なので「root」は仮想 root です。Android の実 UID はアプリ UID のままで
特権を持ちません。

### 1.2 sshd の準備

```sh
# 起動に必要な空ディレクトリ
mkdir -p /var/empty /run

# ホスト鍵を生成 (初回のみ)
ssh-keygen -A

# 設定を編集 (任意): /etc/ssh/sshd_config
#   PermitRootLogin yes
#   PasswordAuthentication yes
# が必要。Alpine 既定は "prohibit-password" のため明示で yes にする。
sed -i \
    -e 's/^#*PermitRootLogin.*/PermitRootLogin yes/' \
    -e 's/^#*PasswordAuthentication.*/PasswordAuthentication yes/' \
    /etc/ssh/sshd_config
```

### 1.3 sshd 起動

特権ポート (1〜1023) は Android の kernel が拒否するので、`2222` 等を使います。

```sh
# フォアグラウンド起動 (ログがその場に出る、Ctrl+C で停止)
/usr/sbin/sshd -D -p 2222 -e

# あるいはバックグラウンドで起動
/usr/sbin/sshd -p 2222 -E /tmp/sshd.log
```

別タブで起動状態を確認:

```sh
ss -ltn 2>/dev/null | grep 2222 || netstat -ltn | grep 2222
ps -ef | grep sshd
```

### 1.4 端末の IP アドレスを確認

```sh
# Alpine 内
ip -4 addr show | grep inet
# あるいは
ifconfig 2>/dev/null | grep 'inet '
```

`wlan0` 等の Wi-Fi インタフェースに付いた IP (`192.168.x.x` など) を控えます。
PRoot は host ネットワーク名前空間を共有するので、Android が見ている IP が
そのまま使えます。

> 💡 確認できない場合は Android 設定 → ネットワーク → Wi-Fi の詳細から IP を確認。

## 2. PC 側からの接続

PC と Android が **同じ Wi-Fi (LAN)** にいることを確認してから:

```sh
ssh -p 2222 root@<Android の IP>
# 例: ssh -p 2222 root@192.168.1.42
```

初回は host 鍵の信頼確認が出るので `yes` で進めます。続けて 1.1 で設定したパスワード
を入力すると Alpine のシェルに入ります。

### 2.1 公開鍵認証にする (推奨)

毎回パスワード入力を避けたい場合:

```sh
# PC 側 (鍵がまだなら作成)
ssh-keygen -t ed25519

# 公開鍵を z2term へコピー
ssh-copy-id -p 2222 root@<Android IP>
# あるいは手動で /root/.ssh/authorized_keys に貼り付け
```

その上で `/etc/ssh/sshd_config` を:

```
PermitRootLogin prohibit-password    # パスワード禁止、公開鍵のみ
PasswordAuthentication no
```

に変更して `sshd` を再起動するとよりセキュアです。

## 3. 注意事項・トラブルシュート

### Android が sshd を kill してしまう

- Android はバックグラウンドアプリの省電力対象。
- z2term は **フォアグラウンドサービス** で常駐するため、アプリ通知が出ている
  間は OS から殺されない設計。
- 念のため省電力ホワイトリストに z2term を追加: 設定 → アプリ → z2term →
  バッテリー → 「最適化しない」。

### LAN にいる別 PC から見つからない

- Wi-Fi の AP が「クライアント分離」を有効にしていると同一 LAN でも到達不可。
  ルーター設定を確認。
- ファイアウォール (PC 側) で 2222 をブロックしていないか確認。

### Termux と違って 22 番ポートが使えない

- Android の kernel は非特権プロセスが 1024 未満をリッスンするのを拒否します。
- 任意の > 1024 ポート (2222, 8022, 8888 等) を使ってください。

### スマホがスリープに入ると切れる

- Wi-Fi sleep policy で接続が切れることがある。
- 設定 → Wi-Fi → 詳細設定 → 「スリープ時も Wi-Fi 接続を維持」を ON に。
- もしくは `tmux` / `screen` を間に挟んで再接続時に復元できるようにする。

## 4. SCP / SFTP

`sshd` が動いていれば PC から `scp` / `sftp` でファイル転送できます:

```sh
# PC から z2term へファイルを送る
scp -P 2222 ./hoge.txt root@<Android IP>:/root/

# 取り出す
scp -P 2222 root@<Android IP>:/root/result.log ./

# 対話 SFTP
sftp -P 2222 root@<Android IP>
```

## 5. ポート転送で外部から (上級)

LAN 外から接続したい場合は以下のいずれか:

- ルーターでポートフォワード (2222 → Android IP:2222) を設定
- Tailscale / WireGuard 等で VPN で繋ぐ
- `ssh -R` で外部 VPS にリバーストンネル

これらは LAN 構成依存なので個別に検討してください。
