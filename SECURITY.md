# Security Policy

[日本語は下にあります](#セキュリティ方針)

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

1. **Preferred: GitHub private vulnerability reporting.**
   Open the repository's **Security** tab and choose **Report a vulnerability**.
   Only the maintainer can see the report.
2. **If you cannot use GitHub:** email **orgson352@gmail.com**
   with a subject starting with `[z2term security]`.

Please include, as far as you can:

- the z2term version (Settings › About this app, or `z2-doctor`) and the Android version
- what an attacker needs (another app installed on the phone, the same Wi-Fi, USB access, …)
- steps to reproduce, and what happens

z2term is maintained by one person. Reports are read and answered as soon as possible,
but there is no guaranteed response time. Please give a reasonable time for a fix to ship
before you disclose details publicly. A fixed release will mention the problem in the
[changelog](docs/en/CHANGELOG.md).

## Supported versions

Only the **latest release** receives security fixes. Please check that a problem still
happens there before reporting it.

## Scope

In scope: the z2term app itself, including the `z2-*` commands it installs into the
Linux environment.

Out of scope:

- programs and packages you install inside the Linux environment
- problems that need a rooted phone, or someone already holding your unlocked phone
- exposure you turned on yourself (for example starting `sshd --lan`, or a server command
  that listens on all interfaces) — but a default that is unsafe without a clear warning
  *is* in scope

## How z2term protects you

z2term is a terminal that can also drive Android, so it can hold powerful permissions.
These are the defaults it is built around:

- **Sensitive features are off until you turn them on.** SMS detection, notification
  capture, accessibility (app control) and usage access each need your explicit choice in
  Settings and/or the Android permission screen.
- **The shell-to-app bridge is private.** The `z2-*` commands hand requests to the app
  through an app-private directory that other apps, USB and the document picker cannot
  reach, and every request must carry a token that is regenerated each time the app starts
  (since 1.0.48).
- **The built-in SSH server listens on the phone only** (`127.0.0.1`) and accepts keys only;
  password login is disabled. Opening it to the network needs `--lan` and a registered
  `~/.ssh/authorized_keys`.
- **SSH connections check host keys** and ask before trusting a new host. Saved passwords
  and private keys are encrypted with the Android Keystore (AES-GCM).
- **Backups leave secrets out by default.** Including them requires a passphrase
  (PBKDF2-HMAC-SHA256, 210,000 iterations).
- **Direct sharing over the LAN** uses a random 256-bit link that expires.
- **Updates** come only over HTTPS from GitHub Releases, and Android checks that the new APK
  is signed with the same key. In-app updating is disabled for copies installed from F-Droid.
- **No analytics, advertising or crash-reporting SDKs.** The app connects to the network
  only for what you use or start (update checks, OS image downloads, SSH, file servers, …).

Good to know:

- Logs written by opt-in features (`~/.z2term/sms.jsonl`, `notifications.jsonl`,
  `events.jsonl`) are plain text in the Linux home. Every program you run in the Linux
  environment can read them. Turn the feature off or clear the file if you do not need it.
- Anything you run in the terminal runs with z2term's permissions. Only run scripts you trust.

---

## セキュリティ方針

## 脆弱性の報告

セキュリティの問題は、**公開の Issue に書かないでください**。

1. **おすすめ: GitHub の非公開報告。**
   リポジトリの **Security** タブから **Report a vulnerability** を選んでください。
   報告は開発者にしか見えません。
2. **GitHub を使えない場合:** **orgson352@gmail.com** へメールしてください。
   件名は `[z2term security]` で始めてください。

分かる範囲で、次の内容を書いてください。

- z2term の版（設定 › このアプリについて、または `z2-doctor`）と Android の版
- 攻撃する側に何が要るか（同じスマホに入れた別のアプリ、同じ Wi-Fi、USB 接続など）
- 再現する手順と、起きること

z2term は個人で開発しています。報告はできるだけ早く読んで返信しますが、返信までの期間は
約束できません。詳しい内容を公開するのは、修正版が出るまで待ってください。
修正した版では、[更新履歴](docs/ja/CHANGELOG.md)で問題に触れます。

## 対象の版

セキュリティの修正は**最新の版**にだけ入れます。報告の前に、最新の版でも起きるか確かめてください。

## 対象の範囲

対象: z2term アプリ本体と、アプリが Linux 環境へ入れる `z2-*` コマンド。

対象外:

- Linux 環境の中で利用者が入れたプログラムやパッケージ
- root 化したスマホが前提のもの、ロックを解除したスマホを他人がすでに持っている状況
- 利用者が自分で外へ開いたもの（`sshd --lan` で起動した、すべての宛先で待ち受けるサーバーを
  起動した、など）。ただし、はっきりした警告なしに危険な既定値になっているものは対象です。

## z2term の守り方

z2term は Android も操作できるターミナルなので、強い権限を持つことがあります。
既定では次のように作っています。

- **危ない機能は、利用者が入れるまで動きません。** SMS 検知・通知の記録・ユーザー補助
  （アプリ操作）・使用状況へのアクセスは、それぞれ設定や Android の許可画面で利用者が選んだときだけ動きます。
- **シェルとアプリの受け渡し口は非公開です。** `z2-*` コマンドは、他のアプリ・USB・
  ファイル選択画面から届かないアプリ専用の場所を通して頼みごとを渡します。頼みごとには、
  アプリを起動するたびに作り直す合言葉が必要です（1.0.48 から）。
- **内蔵の SSH サーバーはスマホの中だけで待ち受けます**（`127.0.0.1`）。鍵でのみ入れて、
  パスワードでは入れません。外へ開くには `--lan` と `~/.ssh/authorized_keys` への鍵の登録が要ります。
- **SSH 接続では相手の鍵を確かめ**、初めての相手は信用する前に確認します。保存したパスワードと
  秘密鍵は Android Keystore で暗号化します（AES-GCM）。
- **バックアップには、既定で秘密を入れません。** 入れるときは合言葉が必須です
  （PBKDF2-HMAC-SHA256、210,000 回）。
- **LAN での直接共有**は、推測できない 256 ビットのリンクを使い、期限が来ると切れます。
- **更新**は GitHub Releases から HTTPS でだけ取り、新しい APK が同じ鍵で署名されているかを
  Android が確かめます。F-Droid から入れた場合、アプリ内の更新は使えません。
- **利用状況の収集・広告・クラッシュ報告の SDK は入れていません。** 通信するのは、利用者が使う
  機能や始めた操作のときだけです（更新の確認、OS イメージの取得、SSH、ファイルサーバーなど）。

知っておいてほしいこと:

- 利用者が入れた機能の記録（`~/.z2term/sms.jsonl`・`notifications.jsonl`・`events.jsonl`）は、
  Linux のホームに平文で残ります。Linux 環境で動かすプログラムはすべて読めます。
  要らなければ機能を切るか、ファイルを空にしてください。
- ターミナルで動かしたものは、z2term の権限で動きます。信頼できるスクリプトだけを動かしてください。
