#!/usr/bin/env python3
"""Run inside the guest after installing the rebuilt engine. No compilation needed.

Each limit change is isolated in a child; the invoking terminal keeps its limits.
Tests low-limit exec, get/set round trips, thread visibility, fork inheritance,
hard-limit enforcement and an actual allocation refusal.
"""
import mmap
import os
import resource
import subprocess
import sys
import threading

LIMIT = 512 * 1024 * 1024


def check_child():
    resource.setrlimit(resource.RLIMIT_AS, (LIMIT, LIMIT))
    assert resource.getrlimit(resource.RLIMIT_AS) == (LIMIT, LIMIT)
    seen = []
    thread = threading.Thread(target=lambda: seen.append(resource.getrlimit(resource.RLIMIT_AS)))
    thread.start()
    thread.join()
    assert seen == [(LIMIT, LIMIT)], seen
    previous = resource.prlimit(0, resource.RLIMIT_AS, (LIMIT // 2, LIMIT))
    assert previous == (LIMIT, LIMIT), previous
    resource.setrlimit(resource.RLIMIT_AS, (LIMIT, LIMIT))
    try:
        resource.setrlimit(resource.RLIMIT_AS, (LIMIT, LIMIT * 2))
    except (OSError, ValueError):
        pass
    else:
        raise AssertionError("raising a lowered hard limit unexpectedly succeeded")
    try:
        mapping = mmap.mmap(-1, LIMIT * 2)
    except (OSError, MemoryError):
        pass
    else:
        mapping.close()
        raise AssertionError("allocation exceeded the guest limit")
    subprocess.run(["/bin/true"], check=True, timeout=10)
    # A fresh executable must see the guest limit, not the Android-adjusted value.
    subprocess.run(
        [sys.executable, "-c", "import resource; assert resource.getrlimit(resource.RLIMIT_AS) == (%d, %d)" % (LIMIT, LIMIT)],
        check=True, timeout=10,
    )


if __name__ == "__main__":
    if "--child" in sys.argv:
        check_child()
    else:
        before = resource.getrlimit(resource.RLIMIT_AS)
        result = subprocess.run([sys.executable, os.path.abspath(__file__), "--child"], timeout=30)
        assert resource.getrlimit(resource.RLIMIT_AS) == before
        if result.returncode:
            raise SystemExit("FAIL: limited child exited with %s" % result.returncode)
        print("PASS: RLIMIT_AS round trips, inheritance, exec and allocation enforcement")
