#!/usr/bin/env python3
"""Regenerate every Ashfall 3D model (stdlib only).

Runs main() of each tools/models/<name>.py (except preview.py), which writes that model's skin
textures, model JSON and item definitions into the module resources.

Usage: python3 -I tools/gen_models.py [--only <name>...]
"""
import argparse
import importlib.util
import sys
from pathlib import Path

MODELS = Path(__file__).resolve().parent / "models"
SKIP = {"preview", "gunkit"}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--only", nargs="*")
	args = ap.parse_args()
	failed = []
	for path in sorted(MODELS.glob("*.py")):
		name = path.stem
		if name in SKIP or name.startswith("_") or (args.only and name not in args.only):
			continue
		print(f"== {name}")
		spec = importlib.util.spec_from_file_location(f"ashfall_model_{name}", path)
		module = importlib.util.module_from_spec(spec)
		saved_argv = sys.argv
		sys.argv = [str(path)]  # model scripts with their own argparse see no extra arguments
		try:
			spec.loader.exec_module(module)
			module.main()
		except Exception as e:  # keep going so one broken script does not hide the others
			failed.append(name)
			print(f"FAILED {name}: {e!r}", file=sys.stderr)
		finally:
			sys.argv = saved_argv
	if failed:
		sys.exit(f"{len(failed)} model script(s) failed: {', '.join(failed)}")


if __name__ == "__main__":
	main()
