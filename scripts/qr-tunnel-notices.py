#!/usr/bin/env python3
"""Collect notices from the exact Go modules used by the Android connector."""
import json
from pathlib import Path
import subprocess
import sys

source = Path(sys.argv[1]).read_text()
decoder = json.JSONDecoder()
modules = {}
while source.strip():
    package, end = decoder.raw_decode(source.lstrip())
    source = source.lstrip()[end:]
    module = package.get("Module")
    if module:
        module = module.get("Replace", module)
        modules[module["Path"]] = (module.get("Version", "2026.9.1"), Path(module["Dir"]))
    elif '/src/vendor/' in package.get('Dir', ''):
        directory = Path(package['Dir'])
        for parent in (directory, *directory.parents):
            if '/src/vendor/' not in str(parent):
                break
            if any(p.is_file() and p.name.upper().startswith('LICENSE') for p in parent.iterdir()):
                modules['Go vendor: ' + str(parent).split('/src/vendor/', 1)[1]] = ('bundled', parent)
                break
modules["Go runtime"] = (subprocess.check_output(["go", "version"], text=True).strip(),
                         Path(subprocess.check_output(["go", "env", "GOROOT"], text=True).strip()))
notices = ["Android QR tunnel: built from https://github.com/cloudflare/cloudflared/tree/2026.9.1\n"]
for name, (version, directory) in sorted(modules.items()):
    files = sorted(p for p in directory.iterdir() if p.is_file() and
                   p.name.upper().startswith(("LICENSE", "COPYING", "NOTICE", "AUTHORS", "PATENTS")))
    if name == "Go runtime" and not files:
        # Distribution packages can move the runtime notice out of GOROOT.
        notice = Path('/usr/share/licenses/go/LICENSE')
        if notice.is_file():
            files = [notice]
    if not files:
        raise SystemExit(f"License notice missing for {name} at {directory}")
    notices.append(f"\n{'=' * 70}\n{name} {version}\n")
    for file in files:
        notices.append(f"\n{file.name}\n{file.read_text(errors='replace')}\n")
Path(sys.argv[2]).write_text("".join(notices))
