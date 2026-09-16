# File sharing, combined receipts and command input

Applies to 0.8.613-alpha (versionCode 621).

## Send files to another app

Run in a local terminal tab:

```sh
z2-share --file /sdcard/Download/report.pdf
z2-share --file /sdcard/Download/photo.png /sdcard/Download/report.pdf
```

Choose a destination in Android's share sheet. This sends file contents, not path text. Specify 1–32 regular files readable by the current shell, up to 512 MiB combined. Archive directories first. Transfer remote SSH files to the device before sharing.

The app creates private cached copies and grants read access to their content URIs. Changing the originals does not change those copies. Copies older than 24 hours are removed on the next share; Android may also clear the cache. The receiving app should save anything it needs to retain. A successful command means the chooser opened, not that the destination finished sending.

`z2-share "text"` still shares text. Use `z2-share -- "--file"` to share text resembling an option.

## Receive text and attachments together

In another app, choose the normal **z2term** share target. Each receipt keeps text, subject and attachments under `~/z2term-inbox/receipt-ID/`. URLs included in the text are preserved. Text or sender details omitted by the originating app are not inferred.

Up to 32 attachments are accepted, with a 512 MiB limit per file. If any attachment fails, the whole import fails and its partial files are removed. Successful receipts are not automatically deleted.

Without registered actions, text is inserted when present; a file-only share inserts quoted paths. Attachments accompanying text are still saved. With registered actions, the app shows the text, filenames and available commands. You can choose one, insert the original content or close the dialog while keeping the saved receipt. Review the inserted command and press Enter to execute it. Existing `z2-when share:` rules still run independently.

### Register a processing command

Open the command list → **Snippets** → New or Edit. Give the command a label and enable **Offer after receiving a share**. For example:

```sh
cat "$Z2_SHARE_MANIFEST"
```

The selected command receives `Z2_SHARE_TEXT` (original body) and `Z2_SHARE_MANIFEST` (absolute receipt JSON path). Incoming values remain data. The receipt format is:

```json
{
  "version": 1,
  "id": "receipt-ID",
  "receivedAt": 1789488000000,
  "text": "Shared text and URL",
  "subject": "Subject",
  "files": [
    { "name": "report.pdf", "path": "z2term-inbox/receipt-ID/report.pdf", "size": 1234 }
  ]
}
```

`receivedAt` is Unix time in milliseconds. File paths are relative to the shared shell HOME; prepend `$HOME` when opening them. Imported files stay on the device and are not transferred to an active SSH destination.

Automatic `share:` rules additionally receive `Z2_WHEN_SHARE_MANIFEST` (relative to HOME) and `Z2_WHEN_SHARE_TEXT`. A combined receipt sets `Z2_WHEN_SHARE_KIND=mixed` and matches both text and file conditions. Each `share:any` rule runs once per receipt.

## Fill snippet arguments using a form

Enable **Ask for inputs before inserting** in the snippet editor. Add placeholders to its command:

| Placeholder | Input |
|---|---|
| `{{name}}` / `{{name:text=default}}` | Text |
| `{{count:number=3}}` | Number |
| `{{format:choice=png\|jpg}}` | Choice |
| `{{file:file}}` | Path and document picker |

For example, save `wc -l {{file:file}}`. Selecting it opens a file field and a command preview before insertion. The document picker copies the selected file into the shared HOME. When used as a share action, a single attachment fills the file field; multiple attachments can be selected from a list.

Up to 16 distinct fields are supported. Repeated identical definitions reuse one value. Placeholders must occupy complete shell words outside quotes: use `--name {{name}}`, not `"{{name}}"` or `--name={{name}}`.

The preview uses `sh -c` with positional arguments, so quotes, newlines and `$(...)` entered as values are not inserted into executable source. The registered command receives those values as arguments. Existing snippets with the form disabled continue to insert their literal contents, including any `{{...}}` text.
