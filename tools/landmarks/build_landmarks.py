#!/usr/bin/env python3
"""Generates Ashfall's landmark buildings as Lost Cities assets (plan section 1.6, first batch).

Writes modules/salvage/src/main/resources/data/ashfall/lostcities/{parts/landmarks,buildings,multibuildings}.
The predefined cities that pin each landmark to its district chunk are hand-written JSON next to them.

Usage: python3 -I tools/landmarks/build_landmarks.py [--plan NAME] [--blockstates DIR]
  --plan NAME       print ASCII floor plans of one landmark instead of writing files
  --blockstates DIR assets/minecraft/blockstates of the 26.3 client jar, to validate block ids/properties

Every run also checks each landmark's sample rooms are reachable on foot from its entrance.
"""
import argparse
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lcgen import AIR, Grid, b, emit, plan, reachable, validate  # noqa: E402
from kit import *  # noqa: E402,F401,F403
import designs  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "modules/salvage/src/main/resources/data/ashfall/lostcities"


def main():
	ap = argparse.ArgumentParser()
	ap.add_argument("--plan")
	ap.add_argument("--blockstates")
	args = ap.parse_args()
	landmarks = designs.all_landmarks()
	if args.plan:
		lm = landmarks[args.plan]
		g = lm["grid"]
		levels = lm["cellars"] + lm["floors"]
		for level in range(levels + 1):
			print(f"== level {level} (floor {level - lm['cellars']}) y={level * 6 + 1}")
			print(plan(g, level * 6 + 1))
		return
	errors = []
	if args.blockstates:
		for name, lm in landmarks.items():
			errors += [f"{name}: {e}" for e in validate(lm["grid"], args.blockstates)]
	# Navigability: every sample room must be walkable from the front door (stairs, no jumping, no
	# ladders), and the sealed deep ward must not be.
	for name, lm in landmarks.items():
		seen, _ = reachable(lm["grid"], lm["entry"])
		errors += [f"{name}: {c} is not reachable from the entrance" for c in lm.get("must_reach", []) if c not in seen]
		errors += [f"{name}: {c} should be sealed but is reachable" for c in lm.get("must_not_reach", []) if c in seen]
	if errors:
		print("\n".join(errors))
		sys.exit(1)
	shutil.rmtree(OUT / "parts" / "landmarks", ignore_errors=True)
	for name, lm in landmarks.items():
		for old in list((OUT / "buildings").glob(f"{name}*.json")) + list((OUT / "multibuildings").glob(f"{name}.json")):
			old.unlink()
		lm["grid"].connect()
		emit(lm["grid"], OUT, "ashfall", name, lm["cx"], lm["cz"], lm["cellars"], lm["floors"], lm["filler"])
		print(f"wrote {name}: {lm['cx']}x{lm['cz']} chunks, {lm['cellars']} cellars, {lm['floors']} floors, {len(lm['grid'].cells)} blocks")


if __name__ == "__main__":
	main()
