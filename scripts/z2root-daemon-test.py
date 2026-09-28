#!/usr/bin/env python3
"""Guest checks for process start times and long Unix socket paths.

Run in a fresh terminal after installing the updated engine. No build, network
service, package changes or external Python dependencies are required.
"""
import calendar
import errno
import os
from pathlib import Path
import shutil
import socket
import stat
import subprocess
import tempfile
import time


def boot_time():
    def read_btime(fd):
        with os.fdopen(fd) as stream:
            values = dict(line.split(maxsplit=1) for line in stream if line.strip())
        return int(values["btime"])

    # Keep both descriptors open across another substitution, then read twice.
    first_fd = os.open("/proc/stat", os.O_RDONLY)
    try:
        directory = os.open("/proc", os.O_RDONLY | os.O_DIRECTORY)
        try:
            second_fd = os.open("stat", os.O_RDONLY, dir_fd=directory)
        finally:
            os.close(directory)
        second = read_btime(second_fd)
        snapshot = os.fstat(first_fd)
        if snapshot.st_dev != os.stat("/proc").st_dev:
            assert snapshot.st_nlink == 0, "temporary snapshot was not unlinked"
        first = read_btime(os.dup(first_fd))
        os.lseek(first_fd, 0, os.SEEK_SET)
        assert read_btime(os.dup(first_fd)) == first
    finally:
        os.close(first_fd)
    expected = (time.time_ns() - time.clock_gettime_ns(time.CLOCK_BOOTTIME)) // 10**9
    assert abs(first - expected) <= 1, (first, expected)
    assert abs(second - first) <= 1, (first, second)


def process_identity():
    values = dict(line.split(":", 1) for line in Path("/proc/self/status").read_text().splitlines())
    uids = [int(x) for x in values["Uid"].split()]
    gids = [int(x) for x in values["Gid"].split()]
    assert uids[:2] == [os.getuid(), os.geteuid()], uids
    assert gids[:2] == [os.getgid(), os.getegid()], gids


def process_start():
    ps = shutil.which("ps")
    assert ps, "ps is required"
    env = dict(os.environ, LC_ALL="C", TZ="UTC")
    fields = Path("/proc/self/stat").read_text().rsplit(") ", 1)[1].split()
    ticks = int(fields[19])
    boot = (time.time_ns() - time.clock_gettime_ns(time.CLOCK_BOOTTIME)) // 10**9
    expected = boot + ticks // os.sysconf("SC_CLK_TCK")
    observed = []
    for _ in range(2):
        result = subprocess.run(
            [ps, "-p", str(os.getpid()), "-o", "stat=", "-o", "lstart="],
            capture_output=True, text=True, env=env, timeout=5,
        )
        assert result.returncode == 0, result.stderr
        _, started = result.stdout.strip().split(maxsplit=1)
        stamp = calendar.timegm(time.strptime(started, "%a %b %d %H:%M:%S %Y"))
        assert abs(stamp - expected) <= 1, (stamp, expected)
        observed.append(stamp)
    assert observed[0] == observed[1], observed


def stream():
    with tempfile.TemporaryDirectory(prefix="z2d-", dir="/tmp") as directory:
        path = str(Path(directory) / ("a" * 64))
        assert len(os.fsencode(path)) < 108
        with socket.socket(socket.AF_UNIX) as server, socket.socket(socket.AF_UNIX) as client:
            server.settimeout(3)
            client.settimeout(3)
            server.bind(path)
            assert stat.S_ISSOCK(os.lstat(path).st_mode)
            assert os.stat(server.getsockname()).st_ino == os.stat(path).st_ino
            server.listen(1)
            client.connect(path)
            with server.accept()[0] as peer:
                peer.settimeout(3)
                client.sendall(b"request")
                assert peer.recv(7) == b"request"
                peer.sendall(b"reply")
                assert client.recv(5) == b"reply"


def socket_symlinks():
    with tempfile.TemporaryDirectory(prefix="z2d-", dir="/tmp") as directory:
        path = Path(directory) / ("f" * 64)
        alias = Path(directory) / "endpoint.sock"
        with socket.socket(socket.AF_UNIX) as server:
            server.settimeout(3)
            server.bind(str(path))
            server.listen(1)
            for target in (str(path), path.name):
                alias.symlink_to(target)
                # bind must preserve an existing link; only connect follows it.
                with socket.socket(socket.AF_UNIX) as occupied:
                    try:
                        occupied.bind(str(alias))
                    except OSError as error:
                        assert error.errno == errno.EADDRINUSE, error
                    else:
                        raise AssertionError("bound over an existing symlink")
                assert os.readlink(alias) == target
                with socket.socket(socket.AF_UNIX) as client:
                    client.settimeout(3)
                    client.connect(str(alias))
                    with server.accept()[0] as peer:
                        peer.settimeout(3)
                        peer.sendall(b"linked")
                        assert client.recv(6) == b"linked"
                alias.unlink()


def datagram():
    with tempfile.TemporaryDirectory(prefix="z2d-", dir="/tmp") as directory:
        path_a = str(Path(directory) / ("b" * 64))
        path_b = str(Path(directory) / ("c" * 64))
        with socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM) as a, \
                socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM) as b:
            a.settimeout(3)
            b.settimeout(3)
            a.bind(path_a)
            b.bind(path_b)
            # connect is the translated syscall; sendto uses an already bound peer address.
            a.connect(path_b)
            a.send(b"ping")
            data, address = b.recvfrom(16)
            assert data == b"ping"
            b.connect(address)
            b.send(b"pong")
            assert a.recv(16) == b"pong"


def reuse_directory():
    with tempfile.TemporaryDirectory(prefix="z2d-", dir="/tmp") as directory:
        for index in range(70):
            path = Path(directory) / f"{index:064x}"
            with socket.socket(socket.AF_UNIX) as server:
                server.bind(str(path))
            path.unlink()


def ordinary_socket_errors():
    with tempfile.TemporaryDirectory(prefix="z2d-", dir="/tmp") as directory:
        missing = str(Path(directory) / ("d" * 64))
        with socket.socket(socket.AF_UNIX) as client:
            try:
                client.connect(missing)
            except OSError as error:
                assert error.errno == errno.ENOENT, error
            else:
                raise AssertionError("connected to absent endpoint")
        occupied = Path(directory) / ("e" * 64)
        occupied.write_text("keep")
        with socket.socket(socket.AF_UNIX) as server:
            try:
                server.bind(str(occupied))
            except OSError as error:
                assert error.errno == errno.EADDRINUSE, error
            else:
                raise AssertionError("overwrote an existing file")
        assert occupied.read_text() == "keep"


if __name__ == "__main__":
    failed = 0
    for check in (boot_time, process_identity, process_start, stream, socket_symlinks, datagram,
                  reuse_directory, ordinary_socket_errors):
        try:
            check()
        except Exception as error:
            failed += 1
            print(f"FAIL {check.__name__}: {error}")
        else:
            print(f"PASS {check.__name__}")
    raise SystemExit(bool(failed))
