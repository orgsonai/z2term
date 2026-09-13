#!/usr/bin/env python3
"""Desktop-only checks for the APK command entry point; no Android SDK required."""
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    if Path("/system/bin/sh").exists():
        raise SystemExit("Run this compiler-based check on a desktop, not Android.")
    source = Path(__file__).resolve().parents[1] / "app/src/main/cpp/z2android/z2android.c"
    with tempfile.TemporaryDirectory(prefix="z2android-test-") as temp:
        root = Path(temp)
        binary = root / "libz2android.so"
        subprocess.run(["cc", "-std=c11", "-Wall", "-Wextra", "-Werror",
                        '-DZ2_ANDROID_SHELL="/bin/sh"', str(source), "-o", str(binary)], check=True)
        scripts = root / "scripts ' with spaces"
        scripts.mkdir()
        command = root / "z2-probe"
        command.symlink_to(binary)
        (scripts / command.name).write_text('printf "%s\\n" "$#" "$1" "$2"; cat; exit 7\n')
        env = dict(os.environ, Z2_ANDROID_SCRIPTS=str(scripts), PATH=f"{root}:{os.environ['PATH']}")
        value = "日本語 '\" $(exit 99)\nnext"
        result = subprocess.run(["sh", "-c", 'z2-probe "$1" "$2"', "probe", value, ""],
                                input="stdin\n", text=True, capture_output=True, env=env, timeout=5)
        assert result.returncode == 7, result
        assert result.stdout == f"2\n{value}\n\nstdin\n", result
        assert result.stderr == "", result
        for directory in ("", "relative/path"):
            result = subprocess.run([str(command)], env=dict(env, Z2_ANDROID_SCRIPTS=directory),
                                    capture_output=True, timeout=5)
            assert result.returncode == 126, result
        bad_name = root / "unrelated"
        bad_name.symlink_to(binary)
        result = subprocess.run([str(bad_name)], env=env, capture_output=True, timeout=5)
        assert result.returncode == 126, result
    print("PASS: native command entry preserves arguments, stdin and exit status; invalid setup fails")


if __name__ == "__main__":
    main()
