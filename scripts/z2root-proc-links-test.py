#!/usr/bin/env python3
"""Guest regression checks: no build, downloads or external services.

Run in a fresh session after updating the engine. Access denied by the kernel
is reported as SKIP for namespace/map_files; a fabricated ENOENT is a failure.
"""
import errno
import mmap
import os
from pathlib import Path
import select
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import threading


class Skip(Exception):
    pass


def fd_paths(fd):
    return (
        f"/proc/self/fd/{fd}",
        f"/proc/thread-self/fd/{fd}",
        f"/proc/{os.getpid()}/fd/{fd}",
        f"/proc/{os.getpid()}/task/{threading.get_native_id()}/fd/{fd}",
        f"/dev/fd/{fd}",
    )


def pipe_reopen(index):
    r, w = os.pipe()
    try:
        os.write(w, b"pipe-ok\n")
        path = fd_paths(r)[index]
        assert os.readlink(path).startswith("pipe:[")
        assert stat.S_ISFIFO(os.stat(path).st_mode)
        with open(path, "rb", buffering=0) as stream:
            assert stream.read(8) == b"pipe-ok\n"
    finally:
        os.close(r)
        os.close(w)


def deleted_file():
    with tempfile.TemporaryDirectory(prefix="z2-proc-") as directory:
        path = Path(directory) / "file"
        path.write_bytes(b"open-after-unlink")
        with path.open("rb") as original:
            path.unlink()
            for link in fd_paths(original.fileno()):
                with open(link, "rb") as reopened:
                    assert reopened.read() == b"open-after-unlink"


def memfd():
    if not hasattr(os, "memfd_create"):
        raise Skip("memfd_create unavailable")
    fd = os.memfd_create("z2-proc-test")
    try:
        os.write(fd, b"anonymous-file")
        for link in fd_paths(fd):
            with open(link, "rb") as stream:
                assert stream.read() == b"anonymous-file"
    finally:
        os.close(fd)


def non_file_objects():
    with socket.socket() as sock, select.epoll() as epoll:
        for fd in (sock.fileno(), epoll.fileno()):
            expected = os.fstat(fd)
            for link in fd_paths(fd):
                observed = os.stat(link)
                assert observed.st_mode == expected.st_mode
                assert observed.st_ino == expected.st_ino
        try:
            fd = os.open(fd_paths(sock.fileno())[0], os.O_RDONLY)
        except OSError as error:
            assert error.errno == errno.ENXIO, error
        else:
            os.close(fd)
            raise AssertionError("socket reopen unexpectedly succeeded")


def pipe_suffix():
    r, w = os.pipe()
    try:
        for suffix in ("/", "/child", "/.."):
            try:
                os.stat(f"/proc/self/fd/{r}{suffix}")
            except OSError as error:
                assert error.errno == errno.ENOTDIR, error
            else:
                raise AssertionError("pipe treated as directory: " + suffix)
    finally:
        os.close(r)
        os.close(w)


def directory_and_symlinks():
    with tempfile.TemporaryDirectory(prefix="z2-proc-") as directory:
        root = Path(directory)
        target = root / "pipe:[123]"
        target.write_bytes(b"regular-file")
        (root / "relative").symlink_to(target.name)
        (root / "absolute").symlink_to(target)
        (root / "dangling").symlink_to("absent")
        fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY)
        try:
            for link in fd_paths(fd):
                assert os.stat(link).st_ino == os.fstat(fd).st_ino
                for name in (target.name, "relative", "absolute"):
                    assert (Path(link) / name).read_bytes() == b"regular-file"
            assert not (root / "dangling").exists()
            assert (root / "dangling").is_symlink()
        finally:
            os.close(fd)


def deleted_cwd():
    original = os.open(".", os.O_RDONLY | os.O_DIRECTORY)
    directory = tempfile.mkdtemp(prefix="z2-proc-cwd-")
    try:
        os.chdir(directory)
        os.rmdir(directory)
        fd = os.open("/proc/self/cwd", os.O_RDONLY | os.O_DIRECTORY)
        os.close(fd)
        assert os.stat(".").st_ino == os.stat("/proc/self/cwd").st_ino
    finally:
        os.fchdir(original)
        os.close(original)
        if os.path.isdir(directory):
            os.rmdir(directory)


def namespace():
    try:
        fd = os.open("/proc/self/ns/mnt", os.O_RDONLY)
    except PermissionError as error:
        raise Skip(str(error))
    else:
        os.close(fd)


def mapped_file():
    with tempfile.TemporaryDirectory(prefix="z2-proc-map-") as directory:
        path = Path(directory) / "mapped"
        path.write_bytes(b"mapped-file" + b"\0" * 4096)
        with path.open("rb") as file, mmap.mmap(file.fileno(), 0, access=mmap.ACCESS_READ):
            try:
                entries = [p for p in Path("/proc/self/map_files").iterdir()
                           if os.readlink(p) == str(path)]
                assert entries, "mapping not found"
                path.unlink()
                with entries[0].open("rb") as stream:
                    assert stream.read(11) == b"mapped-file"
            except PermissionError as error:
                raise Skip(str(error))


def root_and_exe():
    assert Path("/proc/self/root/etc/os-release").read_bytes() == Path("/etc/os-release").read_bytes()
    assert os.stat("/proc/self/exe").st_size == os.stat(sys.executable).st_size


def indirect_exe():
    with tempfile.TemporaryDirectory(prefix="z2-proc-exe-") as directory:
        alias = Path(directory) / "exe"
        alias.symlink_to("/proc/self/exe")
        assert alias.stat().st_size == os.stat(sys.executable).st_size


def shell_case(command, expected):
    if not shutil.which("bash"):
        raise Skip("bash unavailable")
    result = subprocess.run(
        ["bash", "--noprofile", "--norc", "-c", command],
        capture_output=True, text=True, timeout=15,
        env={**os.environ, "BASH_ENV": "/dev/null"})
    assert result.returncode == 0, result.stderr.strip()
    assert result.stdout == expected, repr(result.stdout)


def interpreter_source():
    if not shutil.which("awk"):
        raise Skip("awk unavailable")
    shell_case("awk -f <(printf '%s\\n' 'BEGIN { print ARGV[1]; exit }') -- fd-ok", "fd-ok\n")


if __name__ == "__main__":
    checks = [(f"pipe via {path}", lambda i=i: pipe_reopen(i))
              for i, path in enumerate(fd_paths(0))]
    checks += [
        ("deleted file", deleted_file), ("anonymous memory file", memfd),
        ("socket and anonymous inode", non_file_objects),
        ("pipe is not a directory", pipe_suffix),
        ("directory fd and ordinary symlinks", directory_and_symlinks),
        ("deleted cwd", deleted_cwd), ("namespace", namespace),
        ("deleted mapped file", mapped_file), ("guest root/exe", root_and_exe),
        ("indirect guest exe", indirect_exe),
        ("Bash process substitution", lambda: shell_case(
            "cat <(printf 'substitution-ok\\n')", "substitution-ok\n")),
        ("interpreter source via process substitution", interpreter_source)]
    failures = skips = 0
    for label, check in checks:
        try:
            check()
        except Skip as error:
            skips += 1
            print(f"SKIP: {label}: {error}")
        except (OSError, AssertionError, subprocess.SubprocessError) as error:
            failures += 1
            print(f"FAIL: {label}: {error}")
        else:
            print(f"PASS: {label}")
    print(f"{len(checks) - failures - skips} passed, {failures} failed, {skips} skipped")
    raise SystemExit(1 if failures else 0)
