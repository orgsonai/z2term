# QR tools and phone-hosted file sharing

0.8.597-alpha; build unverified. Camera use, HTTPS, receiving from another network and screen-off continuity are not device-verified.

## Read and display QR codes

Open **Command sheet → QR at the top-left** to read camera frames or images. Choose a result when an image contains multiple QRs. Camera permission is requested on demand and is unnecessary for images. Images are processed locally; Linux and a separate barcode app are unnecessary.

The fixed header places QR on the left, the drag handle in the center, and Close on the right. The QR entry remains available while scrolling and from every tab.

**Receive from another app**: Share a photo or screenshot to **z2term — QR** to decode its image. Camera/QR apps can also share decoded URLs or text to this target. If the source app has no sharing action, copy and paste the result into the QR screen. This is separate from the existing z2term target for terminal input and file intake.

A request accepts up to 8 images or 4,096 text characters. Images take priority over captions or accompanying URLs; multiple QR results are offered for selection. Temporary URI read permission is used. Images are not copied to the inbox, and decoded content is not passed to share automation rules. Processing starts after app unlock and never opens URLs, inserts commands or executes automatically.

Supported SSH and named-command QR links can also open the review screen through another camera app's Open action. HTTP/HTTPS URLs use the source app's Share action.

Review content before choosing an action. Scanning alone never executes commands or connects.

| Content | Actions |
|---|---|
| HTTP / HTTPS URL | Open in browser, copy, show QR |
| SSH endpoint | Save host, port and username as a new profile |
| Short single-line command | Insert into terminal, save snippet, add to edge, generate named command QR |
| Other text | Copy, show QR. Multiline/control-character content cannot be inserted |

Insertion does not send Enter. Review before executing. Edge imports create a new hidden panel; enable its handle in panel management. Existing settings are not overwritten. Referenced scripts, images, note contents and entire panels are not bundled.

Saved SSH profiles and single-line snippets have **QR** buttons. Ordinary edge run-button editors also offer **Create command QR**. State buttons, toggles and app-launch configurations are excluded.

SSH uses `ssh://demo@host.example:2222`, with an optional username and brackets for IPv6. Passwords, private keys, initialization commands, jump hosts and forwarding settings are excluded. Edit authentication/routing after importing and verify the host key when connecting.

Named commands use `z2term://command?name=...&text=...` with UTF-8 URL encoding. Unknown/duplicate fields and commands containing line breaks or control characters are rejected.

Generation accepts up to 2,000 UTF-8 bytes; the content field accepts 4,096 characters. Shorten content if generation fails.

## Share a file

When the sending phone is reachable from the Internet, recipients on another network need only a browser, without an app or account. No transfer cloud, relay service or Bluetooth is used.

Open **Command list → Servers → Share a file from this device**.

The default is **Automatic**.

1. Optionally choose 5, 15 or 60 minutes (default: 15).
2. Press **Choose a file and start sharing** for one file, or **Choose a folder and start sharing** for a folder, then select it.
3. The app obtains an address on the current Wi-Fi/mobile connection and starts its share server on a free port. Once the sending snapshot is ready, it displays the URL and QR. Send the link or a QR image to the remote recipient.

Automatic uses **HTTP without encryption**, with a notice on screen. Switch to **Manual** for HTTPS or an existing router port-forwarding configuration.

Manual setup:

1. Enter the **Public URL** recipients will open, such as `https://share.example:8443`, without a path.
2. Choose an unused **Listening port** from 1024 to 65535. The router's public port may differ.
3. Choose a 5-, 15- or 60-minute lifetime.
4. For HTTPS, select a PKCS#12 (.p12/.pfx) containing the matching hostname certificate, private key and certificate chain, and enter its password. A browser-trusted certificate avoids recipient setup. Certificates are not obtained automatically.
5. For HTTP, explicitly acknowledge that the file and URL are unencrypted.
6. **Choose file** or **Choose a folder → Start sharing and show QR**. The QR and link appear after the sending snapshot is ready. Send a link or QR image to a remote recipient.

Recipients open the QR/link. Folder shares show the hierarchy: open a folder, then select a file. Parent links return as far as the selected root. Review the filename and size and press **Receive and save** to download that file. Browsing directories alone sends no file contents. The receiving page supports Japanese and English.

The entire selected folder is prepared before the URL appears. Empty folders and files with the same basename in different folders remain distinct. Changes to originals after sharing starts do not change the copies being served. Recipients save individual files to their browser download location; the folder hierarchy is for navigation, not bulk saving.

Only the manually entered public URL and listening port are saved. Automatically obtained addresses and ports never overwrite manual settings. Select certificates per share; passwords are not persisted. Choose one file or one folder per share, up to 1 GiB total. Folders allow 10,000 entries including the root and 64 levels below it. A same-size snapshot and at least 16 MiB of additional free space are required. Unreadable entries, incomplete listings or exceeded limits abort preparation and remove partial copies. Folder selection is limited to locations allowed by the Android picker.

Closing the screen keeps sharing active; return through the notification. Keep the phone online until saving finishes. Stop, expiry and the app's stop-everything action close the share server and active transfers and delete the snapshot. Stopping a share leaves other servers running. Switching between Wi-Fi and mobile data or disconnecting also stops the share, as does losing an automatically selected address. Select the file or folder again to create a new URL. Shares never restart after process death or reboot. A snapshot left by forced termination is removed at the next start and never republished.

## Public URL requirements

Starting a share does not remove ingress restrictions. Automatic reads addresses assigned to the Android default network and does not switch from Wi-Fi to mobile data itself. It prefers a public IPv4 assigned to the device, then native global IPv6, excluding local, carrier-shared, special-purpose and unusable addresses. It neither calls an external IP-lookup service nor changes router settings through UPnP.

The same requirements apply on Wi-Fi. If the phone only has a private IPv4 behind the router, a public URL cannot be generated automatically. Global IPv6 still needs router ingress permission. With no candidate, the app explains the reason and points to changing networks or manually configuring an already prepared route.

- **IPv4** needs a public address and, where applicable, TCP forwarding and firewall permission. Carrier NAT may prevent publication even after configuring a home router.
- **IPv6** needs a global address on the phone and inbound permission. An IPv6-only URL also requires IPv6 at the receiving end.
- **Hostnames** must resolve to the correct public address. The manual listener prefers dual IPv6/IPv4, falling back to IPv4 when necessary unless the URL explicitly specifies IPv6.
- An existing HTML server cannot occupy the same port. Automatic lets the OS assign a free port. For Manual, route traffic to the specified listening port.

The unverified status means the phone is listening. Page visits do not distinguish local and external visitors. Test from another network. Transmitted bytes include retries and do not prove saving completed.

Anyone holding the link can receive during its lifetime. A 256-bit random token, per-file consent cookies and cache suppression are used; arbitrary device paths are never exposed. Numeric route IDs resolve only inside the selection manifest. Parent links never escape the selected root, and consent for one file does not enable downloads of another. Stalled connections close after roughly 30–35 seconds. HTTPS uses TLS 1.2/1.3.

## Verification

Unit test sources cover rejection before consent, downloads after consent, ranges, empty files, invalid paths/origins, expiry/stopping, URL normalization and invalid QR content. Additional regression sources cover address selection, IPv6 URL formatting, consent at the actual bound port, closing the listener and existing connections, and leaving a separate server running. Folder regression sources cover hierarchy/parent links, empty directories, duplicate basenames, individual downloads and consent isolation, invalid routes, source changes after copying, aggregate limits, and cleanup after read failures or cancellation. An Android test also covers separate tree and exact certificate permission handoff for folder shares over manual HTTPS. Builds and tests have not been run on the phone.

Device checks should cover camera/images (Japanese content, multiple QRs, denied permission), app-lock return and no execution on scanning alone. Test HTTP/HTTPS from another network, compare saved and source hashes, verify stop/expiry, and check screen-off transfers, rotation and notification return. Automatic checks also cover starting through file or folder selection alone, stopping on Wi-Fi/mobile changes, the no-public-address message and preservation of manual settings. HTTPS tests need a certificate that passes ordinary browser trust and hostname verification.

External-sharing verification: Six Android test cases cover image streams, ClipData-only input, multiple images, decoded text, invalid URIs and oversized input. They have not been run. Device checks also include share-target labels, locked entry, rotation without repeated processing, and sharing again to an existing receiver.
