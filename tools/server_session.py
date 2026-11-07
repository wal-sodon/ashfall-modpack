#!/usr/bin/env python3
"""Run a scripted dedicated-server session (load tests, compatibility checks).

Script lines (one per line, '#' comments allowed):
  until <regex>     wait until a server log line matches
  wait <seconds>    pause
  cmd <command>     send a console command (no leading slash)
The session ends with `stop`. Every log line is echoed; lines matching --quiet are suppressed.

Usage: python3 -I tools/server_session.py <server-dir> <script-file> [--jar fabric-server-launch.jar] [--xmx 6G]
"""
import argparse
import queue
import re
import subprocess
import sys
import threading
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("script", type=Path)
    parser.add_argument("--jar", default="fabric-server-launch.jar")
    parser.add_argument("--xmx", default="6G")
    parser.add_argument("--quiet", default=r"Unpacking|Can't keep up|moved too quickly")
    parser.add_argument("--timeout", type=int, default=900, help="seconds to wait for any single 'until'")
    args = parser.parse_args()

    quiet = re.compile(args.quiet)
    proc = subprocess.Popen(["java", f"-Xmx{args.xmx}", "-jar", args.jar, "--nogui"], cwd=args.server_dir,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    lines = queue.Queue()

    def pump():
        for line in proc.stdout:
            if not quiet.search(line):
                print(line, end="", flush=True)
            lines.put(line)
        lines.put(None)

    threading.Thread(target=pump, daemon=True).start()

    def send(command):
        print(f">>> {command}", flush=True)
        proc.stdin.write(command + "\n")
        proc.stdin.flush()

    def until(pattern):
        regex = re.compile(pattern)
        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                sys.exit(f"server exited while waiting for /{pattern}/")
            if regex.search(line):
                return
        sys.exit(f"timed out waiting for /{pattern}/")

    try:
        for raw in args.script.read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            verb, _, rest = line.partition(" ")
            if verb == "until":
                until(rest)
            elif verb == "wait":
                time.sleep(float(rest))
            elif verb == "cmd":
                send(rest)
            else:
                sys.exit(f"bad script line: {raw}")
    finally:
        if proc.poll() is None:
            send("stop")
            try:
                proc.wait(timeout=120)
            except subprocess.TimeoutExpired:
                proc.kill()
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
