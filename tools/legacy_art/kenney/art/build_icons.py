#!/usr/bin/env python3
"""Regenerate every Ashfall item icon (32x32) from CC0 3D models in one command (stdlib only).

Usage: python3 -I tools/art/build_icons.py [--only <id> ...] [--preview <dir>] [--sheet <png>] [--jobs N]
       [--blender /Applications/Blender.app/Contents/MacOS/Blender]

Reads tools/art/items.json (render recipes) and renders each item with headless Blender
(tools/art/blender/render_item.py: same camera/light rig and 1 px outline as the blaster icons) into
modules/<module>/src/main/resources/assets/ashfall/textures/item/<id>.png. It also makes sure
models/item/<id>.json is a flat item/generated model on ashfall:item/<id> and items/<id>.json points at
it. Source models must be unpacked under .cache/assets (see tools/art/sources.json for packs, URLs and
CC0 licences).
--preview <dir> also keeps <dir>/<id>_hires.png (the 256 px render) for inspection.
--sheet <png> writes a contact sheet of the item icons plus the 7 blaster icons.
"""
import argparse
import json
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "tools" / "art"
BL = ART / "blender"
DEFAULT_BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
ASSETS = "modules/{module}/src/main/resources/assets/ashfall"


def blender(exe, script, *args):
	cmd = [exe, "-b", "--factory-startup", "-P", str(BL / script), "--", *map(str, args)]
	r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
	if r.returncode != 0 or "Traceback" in r.stdout + r.stderr:
		raise RuntimeError(f"{script} {args[:2]} failed:\n{r.stdout[-3000:]}\n{r.stderr[-3000:]}")
	return "\n".join(ln for ln in r.stdout.splitlines() if ln.startswith(("wrote", "WARNING")))


def ensure_model(mod, iid):
	model = mod / "models/item" / f"{iid}.json"
	want = {"parent": "minecraft:item/generated", "textures": {"layer0": f"ashfall:item/{iid}"}}
	if not model.exists() or json.loads(model.read_text()) != want:
		model.parent.mkdir(parents=True, exist_ok=True)
		model.write_text(json.dumps(want, indent="\t") + "\n")
	item = mod / "items" / f"{iid}.json"
	if not item.exists():
		item.parent.mkdir(parents=True, exist_ok=True)
		item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"ashfall:item/{iid}"}}, indent="\t") + "\n")


def build_one(exe, iid, spec, preview):
	mod = ROOT / ASSETS.format(module=spec["module"])
	out = mod / "textures/item" / f"{iid}.png"
	args = [ART / "items.json", iid, out, "--assets", ROOT / ".cache" / "assets"]
	if preview:
		args += ["--hires", Path(preview) / f"{iid}_hires.png"]
	log = blender(exe, "render_item.py", *args)
	ensure_model(mod, iid)
	return out, log


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--preview")
	ap.add_argument("--sheet")
	ap.add_argument("--jobs", type=int, default=6)
	ap.add_argument("--blender", default=DEFAULT_BLENDER)
	a = ap.parse_args()
	items = json.loads((ART / "items.json").read_text())["items"]
	todo = {k: v for k, v in items.items() if not a.only or k in a.only}
	if a.only and set(a.only) - set(items):
		sys.exit(f"unknown item(s): {', '.join(sorted(set(a.only) - set(items)))}")
	if not (ROOT / ".cache" / "assets").is_dir():
		sys.exit("missing .cache/assets (download the CC0 packs listed in tools/art/sources.json)")
	if a.preview:
		Path(a.preview).mkdir(parents=True, exist_ok=True)
	failed = []
	with ThreadPoolExecutor(a.jobs) as ex:
		futs = {k: ex.submit(build_one, a.blender, k, v, a.preview) for k, v in todo.items()}
		for k, f in futs.items():
			try:
				print(f.result()[1])
			except Exception as e:  # keep going so one failure does not hide the others
				failed.append(k)
				print(f"FAILED {k}: {e}", file=sys.stderr)
	if a.sheet:
		icons = [ROOT / ASSETS.format(module=v["module"]) / "textures/item" / f"{k}.png" for k, v in items.items()]
		cfg = json.loads((ART / "blasters.json").read_text())
		icons += [ROOT / cfg["module"] / "textures/item" / f"{g}_icon.png" for g in cfg["blasters"]]
		print(blender(a.blender, "contact_sheet.py", Path(a.sheet).resolve(), *[p for p in icons if p.exists()]))
	if failed:
		sys.exit(f"failed: {', '.join(failed)}")


if __name__ == "__main__":
	main()
