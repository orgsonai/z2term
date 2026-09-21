#!/bin/sh
# Generic z2-view example: save text and a category using a macro-defined form.
# Copy this file to $HOME/.z2term/macros/view-form.sh, then run it with sh.
set -eu
DIR="$HOME/.z2term/view-form"
mkdir -p "$DIR"
case ${1:-view} in
  view) ;;
  save)
    [ "$#" -eq 3 ] || exit 1
    case $3 in personal|work) ;; *) exit 1;; esac
    printf '%s\n' "$2" > "$DIR/text.txt"
    printf '%s\n' "$3" > "$DIR/category.txt"
    ;;
  clear) rm -f "$DIR/text.txt" "$DIR/category.txt" ;;
  *) exit 1 ;;
esac
tmp=$(mktemp -d "$DIR/.page-XXXXXXXX")
trap 'rm -rf "$tmp"' 0
trap 'exit 1' 1 2 15
cat > "$tmp/page.html" <<'HTML'
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{padding:12px;font:16px/1.5 sans-serif;background:var(--z2-bg);color:var(--z2-fg)}pre{white-space:pre-wrap;overflow-wrap:anywhere}a{display:inline-block;padding:12px;color:var(--z2-accent)}</style>
</head><body><h1>Text and category</h1><pre>
HTML
for source in "$DIR/category.txt" "$DIR/text.txt"; do
  [ -f "$source" ] || continue
  awk '{ gsub(/&/,"\\&amp;"); gsub(/</,"\\&lt;"); gsub(/>/,"\\&gt;"); print }' "$source" >> "$tmp/page.html"
done
printf '%s\n' '</pre><a href="z2-action:clear">Clear saved text</a></body></html>' >> "$tmp/page.html"
cat > "$tmp/controls.json" <<'JSON'
{
  "handler": "view-form.sh",
  "refresh": ["view"],
  "actions": [
    {
      "id": "save", "label": "Edit", "toolbar": true, "args": ["save"],
      "fields": [
        {"label": "Text", "type": "text", "required": true},
        {"label": "Category", "type": "choice", "default": "personal", "choices": [
          {"value": "personal", "label": "Personal"},
          {"value": "work", "label": "Work"}
        ]}
      ]
    },
    {"id": "clear", "label": "Clear", "args": ["clear"], "confirm": "Clear the saved text?"}
  ]
}
JSON
z2-view --controls "$tmp/controls.json" "$tmp/page.html" "Text and category"
