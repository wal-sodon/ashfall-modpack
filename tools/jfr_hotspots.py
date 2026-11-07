#!/usr/bin/env python3
"""Rank server-thread hotspots in a Java Flight Recorder file.

Why: the zombie load benchmark (ZombieLoadBenchmark gametest) records JFR, and we need to know where the server
tick goes per phase, split into Ashfall code, vanilla pathfinding/AI and other mods. `jfr print --json` gives every
jdk.ExecutionSample with its full stack; this script aggregates them by method (self and inclusive), by class
and by "owner" bucket, optionally only inside the wall-clock windows the benchmark CSV records per phase.

Usage:
  jfr_hotspots.py bench.jfr [--csv bench.csv] [--phase noisy_80] [--thread "Server thread"] [--top 25]

Inclusive counts every sample in which a method appears at least once (recursion counted once), so percentages
can add up to more than 100. Self counts only the top frame.
"""
import argparse
import csv
import json
import os
import shutil
import subprocess
import sys
from collections import Counter
from datetime import datetime


def owner(frame_type: str) -> str:
	"""Bucket a declaring class into who owns the code."""
	t = frame_type
	if t.startswith("xyz.toesinthesand.ashfall"):
		return "ashfall"
	if t.startswith("net.minecraft.world.level.pathfinder") or t.startswith("net.minecraft.world.entity.ai.navigation"):
		return "vanilla-pathfinding"
	if t.startswith("net.minecraft.world.entity.ai"):
		return "vanilla-ai"
	if t.startswith("net.caffeinemc") or t.startswith("me.jellysquid"):
		return "lithium"
	if "zbb" in t.lower() or t.startswith("com.zombiesbreakandbuild") or t.startswith("dev.zbb"):
		return "zbb"
	if t.startswith("net.minecraft"):
		return "vanilla-other"
	if t.startswith("java.") or t.startswith("jdk.") or t.startswith("sun."):
		return "jdk"
	if t.startswith("net.fabricmc") or t.startswith("org.spongepowered") or t.startswith("com.llamalad7"):
		return "fabric/mixin"
	return "other:" + ".".join(t.split(".")[:3])


def parse_time(s: str) -> float:
	# JFR json times look like 2026-10-26T10:11:12.345678901+01:00 (nanoseconds); trim to micros for fromisoformat.
	if "." in s:
		head, rest = s.split(".", 1)
		frac = ""
		i = 0
		while i < len(rest) and rest[i].isdigit():
			frac += rest[i]
			i += 1
		tz = rest[i:]
		s = head + "." + frac[:6].ljust(6, "0") + tz
	if s.endswith("Z"):
		s = s[:-1] + "+00:00"
	return datetime.fromisoformat(s).timestamp() * 1000.0


def type_name(f) -> str:
	# jfr --json prints internal names (net/minecraft/...); dots make the package buckets and output readable
	return f["method"]["type"]["name"].replace("/", ".")


def frame_name(f) -> str:
	return type_name(f) + "." + f["method"]["name"]


def load_samples(jfr_path: str):
	# --stack-depth: jfr print shows only 5 frames per stack by default, which makes "inclusive" meaningless.
	# jfr ships with the JDK; prefer JAVA_HOME (the macOS /usr/bin stubs have no jfr), then PATH.
	jfr = os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "jfr")
	if not os.path.exists(jfr):
		jfr = shutil.which("jfr") or "jfr"
	out = subprocess.run([jfr, "print", "--json", "--stack-depth", "256", "--events", "jdk.ExecutionSample", jfr_path],
						 check=True, capture_output=True, text=True).stdout
	data = json.loads(out)
	events = data["recording"]["events"]
	for e in events:
		v = e["values"]
		thread = (v.get("sampledThread") or {}).get("javaName") or (v.get("sampledThread") or {}).get("osName") or "?"
		frames = (v.get("stackTrace") or {}).get("frames") or []
		yield parse_time(v["startTime"]), thread, frames, (v.get("stackTrace") or {}).get("truncated", False)


def main():
	ap = argparse.ArgumentParser()
	ap.add_argument("jfr")
	ap.add_argument("--csv", help="benchmark CSV with start_epoch_ms/end_epoch_ms per phase")
	ap.add_argument("--phase", action="append", help="phase name(s) to keep (needs --csv); default: all phases")
	ap.add_argument("--thread", default="Server thread", help="substring of the thread name to keep")
	ap.add_argument("--top", type=int, default=25)
	ap.add_argument("--clock-offset", type=float, default=0.0,
					help="ms added to JFR timestamps before matching windows; JFR's clock ran ~200 ms ahead of "
						 "System.currentTimeMillis in our runs, which matters for single-tick windows (pass -200)")
	ap.add_argument("--json-out", help="write the ranking as JSON here")
	args = ap.parse_args()

	windows = []
	if args.csv:
		with open(args.csv) as fh:
			for row in csv.DictReader(fh):
				name = row.get("phase") or row.get("segment")
				if not args.phase or name in args.phase:
					windows.append((name, float(row["start_epoch_ms"]), float(row["end_epoch_ms"])))

	incl = Counter()
	self_ = Counter()
	cls_incl = Counter()
	own_self = Counter()
	own_incl = Counter()
	threads = Counter()
	total = 0
	truncated = 0
	for t, thread, frames, trunc in load_samples(args.jfr):
		threads[thread] += 1
		if args.thread not in thread:
			continue
		if windows and not any(a <= t + args.clock_offset <= b for _, a, b in windows):
			continue
		if not frames:
			continue
		total += 1
		truncated += 1 if trunc else 0
		names = [frame_name(f) for f in frames]
		self_[names[0]] += 1
		own_self[owner(type_name(frames[0]))] += 1
		for n in set(names):
			incl[n] += 1
		for c in {type_name(f) for f in frames}:
			cls_incl[c] += 1
		for o in {owner(type_name(f)) for f in frames}:
			own_incl[o] += 1

	if total == 0:
		print("no samples matched; threads seen:", threads.most_common(10), file=sys.stderr)
		sys.exit(1)

	def pct(n):
		return 100.0 * n / total

	print(f"samples: {total} on '{args.thread}'" + (f" in phases {[w[0] for w in windows]}" if windows else "")
		  + f" (truncated stacks: {truncated})")
	print("\n== owner buckets (self %, inclusive %) ==")
	for o, n in own_self.most_common():
		print(f"{pct(n):6.1f}  {pct(own_incl[o]):6.1f}  {o}")
	print(f"\n== top {args.top} methods by inclusive % ==")
	for n, c in incl.most_common(args.top):
		print(f"{pct(c):6.1f}  self {pct(self_[n]):5.1f}  {n}")
	print(f"\n== top {args.top} methods by self % ==")
	for n, c in self_.most_common(args.top):
		print(f"{pct(c):6.1f}  {n}")
	print("\n== ashfall methods by inclusive % ==")
	for n, c in incl.most_common():
		if n.startswith("xyz.toesinthesand.ashfall") and pct(c) >= 0.2:
			print(f"{pct(c):6.1f}  self {pct(self_[n]):5.1f}  {n}")
	print("\n== non-vanilla, non-JDK classes by inclusive % ==")
	for c, n in cls_incl.most_common():
		if not (c.startswith("net.minecraft") or c.startswith("java.") or c.startswith("jdk.") or c.startswith("sun.")
				or c.startswith("it.unimi") or c.startswith("com.google") or c.startswith("com.mojang")) and pct(n) >= 0.5:
			print(f"{pct(n):6.1f}  {c}")
	if args.json_out:
		with open(args.json_out, "w") as fh:
			json.dump({"samples": total, "owners_self": own_self, "owners_incl": own_incl,
					   "inclusive": incl.most_common(200), "self": self_.most_common(200)}, fh, indent=1)


if __name__ == "__main__":
	main()
