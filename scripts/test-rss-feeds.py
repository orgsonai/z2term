#!/usr/bin/env python3
"""Exercise the bundled RSS shell/Python without an Android build or network."""
import fcntl
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/zerotoship/z2term/proot/Z2RssFeeds.kt"


def render_helper(lang="ja"):
    source = SOURCE.read_text()
    locale = re.escape(lang) + r" = " if lang in ("ja", "en") else '"' + re.escape(lang) + r'" to '
    label_source = re.search(locale + r"listOf\((.*?)\n        \)", source, re.S).group(1)
    labels = [json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', label_source)]
    assert len(labels) == 15
    template = source.split('    return """\n', 1)[1].split('\n""".trimIndent()', 1)[0]
    return (template.replace('${d}', '$')
            .replace('$labels', " ".join(map(shlex.quote, labels)))
            .replace('$quotedTitle', shlex.quote(labels[0])))


class FeedTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="rss-feeds-")
        self.data = Path(self.temp.name)
        self.feeds = self.data / "feeds.txt"
        self.script = self.data / "rss.sh"
        self.write_script()

    def tearDown(self):
        self.temp.cleanup()

    def write_script(self, lang="ja"):
        # No HOME override; redirect only the helper's explicit data directory.
        self.script.write_text('''#!/bin/sh
DIR=$1
shift
FEEDS="$DIR/feeds.txt"
z2-view() {
  test "$1" = --controls || return 9
  cp "$2" "$DIR/controls.json" || return 1
  cp "$3" "$DIR/page.html"
}
''' + render_helper(lang) + '\nmanage_feeds "$@"\n')

    def invoke(self, *args, ok=True):
        result = subprocess.run(["sh", str(self.script), str(self.data), *args],
                                capture_output=True, text=True, timeout=5)
        if ok:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertFalse(list(self.data.glob(".feeds-????????")))
        else:
            self.assertNotEqual(result.returncode, 0)
        return result

    def controls(self):
        content = (self.data / "controls.json").read_bytes()
        self.assertLessEqual(len(content), 256 * 1024)
        return json.loads(content)

    def key(self, url):
        return hashlib.sha256(url.encode()).hexdigest()

    def test_empty_view_and_all_languages(self):
        for lang in ("ja", "en", "zh-CN", "zh-TW", "es", "ko"):
            with self.subTest(lang=lang):
                self.write_script(lang)
                subprocess.run(["sh", "-n", str(self.script)], check=True, timeout=5)
                self.invoke("feeds")
                self.assertFalse(self.feeds.exists())
                c = self.controls()
                self.assertEqual(c["handler"], "rss.sh")
                self.assertEqual(c["actions"][0]["args"], ["feed-add"])
                self.assertEqual(c["actions"][1]["args"], ["view"])
                self.assertIn("</html>", (self.data / "page.html").read_text())

    def test_add_edit_remove_preserve_comments_and_history(self):
        a, b = "https://a.test/rss", "https://b.test/atom"
        self.feeds.write_text("# my feeds\n\n" + a)
        history = self.data / "articles.tsv"
        history.write_text("0\thttps://a.test/1\told article\n")
        self.invoke("feed-add", b)
        self.assertEqual(self.feeds.read_text(), "# my feeds\n\n" + a + "\n" + b + "\n")
        self.invoke("feed-edit", self.key(a), "https://new.test/feed?a=1&b=2")
        self.invoke("feed-remove", self.key(b))
        self.assertEqual(self.feeds.read_text(), "# my feeds\n\nhttps://new.test/feed?a=1&b=2\n")
        self.assertEqual(history.read_text(), "0\thttps://a.test/1\told article\n")

    def test_duplicates_and_stale_forms_cannot_change_another_feed(self):
        a, b = "https://a.test/rss", "https://b.test/rss"
        self.feeds.write_text(a + "\n" + b + "\n")
        before = self.feeds.read_bytes()
        self.invoke("feed-add", a, ok=False)
        self.invoke("feed-edit", self.key(b), a, ok=False)
        self.assertEqual(self.feeds.read_bytes(), before)
        self.invoke("feed-remove", self.key(a))
        self.invoke("feed-edit", self.key(a), "https://c.test/rss", ok=False)
        self.invoke("feed-remove", self.key(a), ok=False)
        self.assertEqual(self.feeds.read_text(), b + "\n")

    def test_invalid_urls_do_not_write(self):
        self.feeds.write_text("# keep\n")
        for bad in ("", "file:///tmp/x", "javascript:bad", "https://", "https://a.test/\nhttps://b.test",
                    " https://a.test", "https://a.test/\tfoo", "https://u:p@a.test/feed",
                    "https://a.test:bad/rss", "https://a.test\\evil/feed", "https://a.test/" + "a" * 2048):
            with self.subTest(url=bad[:60]):
                self.invoke("feed-add", bad, ok=False)
                self.assertEqual(self.feeds.read_text(), "# keep\n")

    def test_html_escaping_and_shell_arguments_remain_literal(self):
        url = 'https://a.test/<script>&q="x"&literal=$(id);`id`'
        self.invoke("feed-add", url)
        self.assertEqual(self.feeds.read_text(), url + "\n")
        page = (self.data / "page.html").read_text()
        self.assertNotIn("<script>", page)
        self.assertIn("&lt;script&gt;", page)
        action = next(a for a in self.controls()["actions"] if a["id"] == "edit-0")
        self.assertEqual(action["fields"][0]["default"], url)
        self.invoke(*action["args"], "https://b.test/rss")
        self.assertEqual(self.feeds.read_text(), "https://b.test/rss\n")

    def test_existing_duplicate_rows_can_be_removed_together(self):
        url = "https://a.test/rss"
        self.feeds.write_text(url + "\n# note\n" + url + "\n")
        self.invoke("feeds")
        self.assertEqual(len(self.controls()["actions"]), 4)
        self.invoke("feed-remove", self.key(url))
        self.assertEqual(self.feeds.read_text(), "# note\n")

    def test_pagination_controls_stay_bounded_and_ids_stay_stable(self):
        urls = [f"https://{i}.test/" + "あ" * 2000 for i in range(45)]
        self.feeds.write_text("\n".join(urls) + "\n")
        self.invoke("feeds")
        c = self.controls()
        self.assertLess(len(c["actions"]), 500)
        self.assertTrue(any(a["id"] == "next" for a in c["actions"]))
        self.invoke("feeds", "2")
        c = self.controls()
        self.assertEqual(c["refresh"], ["feeds", "2"])
        self.assertFalse(any(a["id"] == "next" for a in c["actions"]))
        remove = next(a for a in c["actions"] if a["id"] == "remove-0")
        self.assertEqual(remove["args"], ["feed-remove", self.key(urls[40])])
        self.invoke("feeds", "99999999")
        self.assertEqual(self.controls()["refresh"], ["feeds", "2"])
        self.invoke("feeds", "-1", ok=False)

    def test_concurrent_writer_fails_without_overwriting(self):
        self.feeds.write_text("# keep\n")
        with open(self.data / ".feeds.lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.invoke("feed-add", "https://a.test/rss", ok=False)
        self.assertEqual(self.feeds.read_text(), "# keep\n")
        self.invoke("feed-add", "https://a.test/rss")


if __name__ == "__main__":
    unittest.main(verbosity=2)
