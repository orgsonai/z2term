# QR tools and self-hosted relay setup

Applies to 0.8.611-alpha (versionCode 619).

## Read and display QR codes

Open **Settings › QR tools › Open QR tools** to read camera frames or images. `z2-qr` in the terminal opens QR tools with the camera scanning; assign it to a quick-settings tile (`z2-tile set 1 z2-qr`) or an edge panel. Choose a result when an image contains multiple QRs. Camera permission is requested on demand and is unnecessary for images. Images are processed locally; Linux and a separate barcode app are unnecessary.

The entry sat at the top of the command sheet in 0.8.597 and in the Snippets and Connections headers in 0.8.601; 0.8.602 moved it to Settings and `z2-qr`. The top of the command sheet is only the drag handle, which closes the sheet when tapped.

**History (0.8.603)**: Scanned content stays in a history below QR tools. Tap an entry to show it in the content field again; long-press to pin or delete it. Clear all keeps pinned entries. Up to 50 unpinned entries are kept, newest first. Only content read from the camera, images or other apps' shares is recorded; Show QR content and entries shown again are not added. History stays on this device only and includes content such as Wi-Fi passwords. It is hidden while the app is locked.

**Receive from another app**: Share a photo or screenshot to **z2term — QR** to decode its image. Camera/QR apps can also share decoded URLs or text to this target. If the source app has no sharing action, copy and paste the result into the QR screen. This is separate from the existing z2term target for terminal input and file intake.

A request accepts up to 8 images or 4,096 text characters. Images take priority over captions or accompanying URLs; multiple QR results are offered for selection. Temporary URI read permission is used. Images are not copied to the inbox, and decoded content is not passed to share automation rules. Processing starts after app unlock and never opens URLs, inserts commands or executes automatically.

Supported SSH and named-command QR links can also open the review screen through another camera app's Open action. HTTP/HTTPS URLs use the source app's Share action.

Review content before choosing an action. Scanning alone never executes commands or connects. Openable content opens in its app only when you press its Open button (0.8.602).

| Content | Actions |
|---|---|
| HTTP / HTTPS URL | Open (in an app that accepts it, such as a LINE login URL; otherwise a browser), copy, show QR |
| App links (`//` schemes such as `line://`, or `geo:` and similar) | Open in an app that accepts the link, copy, show QR |
| Phone number (`tel:`) | Open the dialer (does not call) |
| Email (`mailto:`, MATMSG) | Compose email |
| SMS (`sms:`, `smsto:`, SMSTO) | Compose SMS |
| Wi-Fi (`WIFI:`) | Connect to Wi-Fi (Android 11+ for WPA/WPA2, WPA3 and open networks; Android 10, WEP and others open Wi-Fi settings and copy the password) |
| Contact (vCard, MECARD) | Add to contacts (review in the add screen before saving) |
| Event (iCalendar VEVENT) | Add to calendar (review in the add screen before saving) |
| SSH endpoint | Save host, port and username as a new profile |
| Short single-line command | Insert into terminal, save snippet, add to edge, generate named command QR |
| Other text | Copy, show QR. Multiline/control-character content cannot be inserted |

`javascript:`, `file:`, `content:`, `data:`, `intent:` and similar links never open. `word:value` text without `//` (for example `Order:123`) stays text. The screen reports when no app can open the content.

Insertion does not send Enter. Review before executing. Edge imports create a new hidden panel; enable its handle in panel management. Existing settings are not overwritten. Referenced scripts, images, note contents and entire panels are not bundled.

Saved SSH profiles and single-line snippets have **QR** buttons. Connection buttons appear in this order: SSH, SFTP, added services, then QR at the end. Ordinary edge run-button editors also offer **Create command QR**. State buttons, toggles and app-launch configurations are excluded.

SSH uses `ssh://demo@host.example:2222`, with an optional username and brackets for IPv6. Passwords, private keys, initialization commands, jump hosts and forwarding settings are excluded. Edit authentication/routing after importing and verify the host key when connecting.

Named commands use `z2term://command?name=...&text=...` with UTF-8 URL encoding. Unknown/duplicate fields and commands containing line breaks or control characters are rejected.

Generation accepts up to 2,000 UTF-8 bytes; the content field accepts 4,096 characters. Shorten content if generation fails.

## Sharing through your own relay

Open **Command list → bottom of Servers → Share files through a relay**. This optional feature requires your own server. There is no automatic third-party relay or direct device-address sharing.

You need an SSH server reachable from the phone and a public HTTPS origin that proxies to an internal port on that server. SSH alone does not issue a browser URL. The following example terminates HTTPS on the same server.

```text
Recipient browser → HTTPS → Your reverse proxy
                                ↓ 127.0.0.1:8080
                           SSH remote forwarding
                                ↓
                      Android loopback share server
```

### Prepare the server

1. Point a public hostname such as `share.example` at your server, provision a certificate trusted by browsers and Android, and allow inbound HTTPS.
2. Permit remote forwarding for the SSH account. Choose an unused internal port from 1024 to 65535; this example uses `127.0.0.1:8080`.
3. Proxy HTTPS requests under `/share/` to that port.

Example SSH server configuration, replacing `share-user` with the intended account. Adapt it to existing rules: applying this to a shared account also affects forwarding for its other uses. Validate configuration before reloading.

```text
Match User share-user
    AllowTcpForwarding remote
    GatewayPorts no
    PermitListen 127.0.0.1:8080
```

Also check `DisableForwarding` and forwarding restrictions in `authorized_keys`. Jump hosts need permission to forward to the next SSH endpoint. See the [OpenSSH configuration reference](https://man.openbsd.org/sshd_config#AllowTcpForwarding).

Example nginx HTTPS virtual host, with your actual certificate paths. If HTTPS already exists, add the `/share/` location to its configuration.

```nginx
server {
    listen 443 ssl;
    server_name share.example;
    ssl_certificate /etc/ssl/share/fullchain.pem;
    ssl_certificate_key /etc/ssl/share/private.key;
    access_log off;

    location /share/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 90s;
    }
    location / { return 404; }
}
```

Do not append a path to `proxy_pass`: preserve the incoming `/share/...` path. Avoid logging sharing URLs. Preserve Origin, Cookie, Set-Cookie and the app’s Referrer-Policy response. Validate the configuration before reloading. See the [nginx proxy_pass reference](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass). While sharing is stopped, there is no upstream listener for this route.

### Configure the app and receive files

1. Save the server in the SSH tab and connect once to verify its host key. Sharing rejects unknown or changed keys without automatic approval.
2. Select that profile in “Share files through a relay”. Its credentials and jump hosts are reused; init commands, ordinary forwarding rules and resident tunnels are not applied.
3. Enter the public HTTPS origin, such as `https://share.example`, without a path, and the server-side internal port, such as `8080`. Save settings. The public HTTPS port is independent of this internal port.
4. Choose 5, 15 or 60 minutes, then a file or folder. A QR and link appear after snapshot preparation, SSH forwarding and the public health check succeed. The lifetime starts at that point.
5. Recipients open the link in a browser, choose a file, and press “Receive and save”. Folder sharing lets them browse the hierarchy and save individual files.

No inbound connection to the phone or automatic router configuration is required. Your server terminates HTTPS and handles the file traffic. A successful probe from the phone does not prove reachability from every recipient network; also test your public URL from another connection.

Relay preferences store the profile ID, public origin and internal port. Credentials remain in the existing SSH settings. Saving settings does not connect; only starting a share does. Sharing never reconnects or republishes automatically after failure or app startup.

### Selection limits and stopping

Share one file or one folder at a time, up to 1 GiB total, 10,000 entries including the root and 64 levels below it. Free space must cover the selection plus 16 MiB. Read failures and exceeded limits abort before publication and delete partial copies. Empty folders and duplicate basenames in separate folders remain distinct. Later source changes do not alter the sending copy.

Anyone with the link can receive during the sharing period. A 256-bit random token, per-file confirmation cookies and cache suppression protect the selection. Routes cannot expose arbitrary device paths or navigate above the selected root. Confirmation for one file cannot authorize another. Stalled connections close after about 30–35 seconds.

Sharing continues after closing its screen; return through the notification. Keep the device online until reception finishes. Stop, expiry or network change/disconnection closes that share’s SSH relay, listener and active transfers and deletes its copy. Other servers remain unaffected. Connection setup is bounded to about two minutes. Failed health checks hide the QR and stop sharing if recovery fails. Copies left by process death are deleted on the next share start and never republished.

## Verification

Verification for 0.8.600 (2026-09-14): desktop debug, release and Android test APK builds passed, with 1,172 unit tests passing, two skipped and zero lint errors. A temporary loopback SSH server and HTTPS proxy on the PC received Android SSH remote forwarding through a USB test route. A real PC browser saved 1 MiB with an identical SHA-256. Range requests, pre-consent and invalid-Origin rejection, and disconnection after stopping passed. Unknown host keys and an occupied remote port were rejected. Test clients trusted only the fixture certificate; production TLS verification was unchanged. This does not establish reachability through a user’s public Internet deployment.

Saving SSH host keys no longer Base64-encodes JSch’s already encoded key a second time. If a previously saved key fails verification, reconnect from the SSH tab and verify its fingerprint against the server before confirming. Sharing never approves unknown or changed keys.

Unit tests cover configuration constraints, consent, ranges, hierarchy, snapshot limits and cleanup, expiry and stop. Android tests verify selection grants and relay-setting delivery. `Referrer-Policy: same-origin` is retained so real browser form POSTs send a valid Origin.

To test your public deployment, install the debug and Android test APKs and prepare a browser with a matching WebDriver on the PC. Save and verify the SSH profile in the debug app and configure the HTTPS proxy. Pass `--serial`, `--profile` (saved SSH profile ID), `--origin` (HTTPS origin) and `--remote-port` to `scripts/test-share-relay.py`. Keep the phone on cellular and the PC on a separate connection. A destination is required. The test shares generated 1 MiB fixtures and verifies single files, folders, hashes, actual browser downloads and stopping. Reachability through a user’s public deployment remains unverified until this test runs against that deployment.
