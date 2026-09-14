"""Real form submission and download checks using a temporary WebDriver profile."""
import hashlib
import json
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.request


class BrowserReceiver:
    def __init__(self, directory, browser=None, driver="chromedriver"):
        self.directory = Path(directory).resolve()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.downloads = self.directory / "downloads"
        self.downloads.mkdir()
        self.profile = tempfile.TemporaryDirectory(prefix="z2term-share-browser-")
        self.process = None
        self.session = None
        self.log = (self.directory / "webdriver.log").open("w")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        self.base = f"http://127.0.0.1:{port}"
        try:
            self.process = subprocess.Popen([driver, f"--port={port}"], stdout=self.log,
                                            stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 10
            while True:
                try:
                    self.api("/status")
                    break
                except OSError:
                    if self.process.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError("WebDriver failed to start")
                    time.sleep(.1)
            options = {
                "binary": browser or shutil.which("chromium") or shutil.which("google-chrome"),
                "args": ["--headless=new", "--disable-dev-shm-usage", "--no-first-run",
                         "--disable-background-networking", "--lang=en-US",
                         "--user-data-dir=" + self.profile.name],
                "prefs": {"download.default_directory": str(self.downloads),
                          "download.prompt_for_download": False, "intl.accept_languages": "en-US,en"},
            }
            assert options["binary"], "Install a browser or pass --browser-bin"
            self.session = self.api("/session", {"capabilities": {"alwaysMatch": {
                "browserName": "chrome", "goog:chromeOptions": options,
                "goog:loggingPrefs": {"performance": "ALL"}}}})["sessionId"]
        except BaseException:
            self.close()
            raise

    def api(self, path, value=None, method=None):
        data = None if value is None else json.dumps(value).encode()
        request = urllib.request.Request(self.base + path, data=data,
            method=method or ("POST" if data is not None else "GET"),
            headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=45) as response:
            return json.load(response)["value"]

    def command(self, path, value=None):
        return self.api("/session/" + self.session + path, value)

    def open(self, url):
        self.command("/url", {"url": url})

    def click(self, selector):
        element = self.command("/element", {"using": "css selector", "value": selector})
        self.command("/element/" + element["element-6066-11e4-a52e-4f735466cecf"] + "/click", {})

    def receive(self, expected):
        # Use the actual HTML button. Supplying Origin or Cookie ourselves hides browser bugs.
        self.command("/log", {"type": "performance"})
        self.click("button[type=submit]")
        deadline = time.monotonic() + 60
        while True:
            files = [p for p in self.downloads.iterdir() if p.is_file() and p.suffix != ".crdownload"]
            if len(files) == 1 and files[0].stat().st_size == len(expected):
                actual = files[0].read_bytes()
                assert actual == expected, "Browser download differs from the generated source"
                break
            if time.monotonic() >= deadline:
                text = self.command("/execute/sync", {"script": "return document.body.innerText", "args": []})
                raise AssertionError("Browser did not save the file: " + text[:500])
            time.sleep(.2)
        events = [json.loads(entry["message"])["message"]
                  for entry in self.command("/log", {"type": "performance"})]
        posts = [event["params"]["request"] for event in events
                 if event["method"] == "Network.requestWillBeSent"
                 and event["params"]["request"]["method"] == "POST"]
        assert posts, "The browser did not submit a form"
        (self.directory / "download-events.json").write_text(json.dumps(events, indent=2))
        return {"bytes": len(actual), "sha256": hashlib.sha256(actual).hexdigest()}

    def close(self):
        if self.session:
            try:
                self.api("/session/" + self.session, method="DELETE")
            except Exception:
                pass
            self.session = None
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
            self.process = None
        self.log.close()
        self.profile.cleanup()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
