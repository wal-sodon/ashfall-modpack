#!/usr/bin/env python3
"""Regenerate all Ashfall blaster art from the Kenney Blaster Kit (CC0) in one command (stdlib only).

Usage: python3 -I tools/art/build_blasters.py [--only <id> ...] [--preview <dir>] [--jobs N]
       [--blender /Applications/Blender.app/Contents/MacOS/Blender]

Reads tools/art/blasters.json and, using headless Blender (tools/art/blender/*.py):
  1. palette.py   -> tools/art/palettes/gunmetal_<id>.png (+ _emit.png, + _icon variants) per blaster
  2. mc_model.py  -> <module>/models/item/<id>.json + textures/item/<id>_skin.png (3D held model)
  3. render_icon.py -> <module>/textures/item/<id>_icon.png (32x32)
  4. writes models/item/<id>_icon.json (flat, parent minecraft:item/generated) and items/<id>.json
     (gui/ground/fixed/on_shelf -> icon, otherwise the 3D model).
--preview <dir> also writes <dir>/<id>_hires.png (256px icon render) for inspection.
Requires the kit unpacked at the "kit" path in blasters.json (.cache/assets/blaster-kit).
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


def blender(exe, script, *args):
	cmd = [exe, "-b", "--factory-startup", "-P", str(BL / script), "--", *map(str, args)]
	r = subprocess.run(cmd, capture_output=True, text=True, cwd=ROOT)
	out = [ln for ln in r.stdout.splitlines() if ln.startswith(("wrote", "WARNING")) or ": grid" in ln]
	if r.returncode != 0 or "Traceback" in r.stdout + r.stderr:
		raise RuntimeError(f"{script} {args[:1]} failed:\n{r.stdout[-3000:]}\n{r.stderr[-3000:]}")
	return "\n".join(out)


def item_json(gid):
	return {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
			"model": {"type": "minecraft:model", "model": f"ashfall:item/{gid}_icon"}}],
		"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{gid}"},
	}}


def build_one(exe, cfg, gid, b, preview):
	kit = ROOT / cfg["kit"]
	mod = ROOT / cfg["module"]
	glb = kit / f"{b['kenney']}.glb"
	pal = ART / "palettes" / f"gunmetal_{gid}.png"
	emit = ART / "palettes" / f"gunmetal_{gid}_emit.png"
	logs = []
	args = [glb, "--id", gid, "--length", b["length"], "--palette", pal, "--emit-mask", emit,
		"--accent", b["accent"], "--model-out", mod / "models/item" / f"{gid}.json",
		"--skin-out", mod / "textures/item" / f"{gid}_skin.png"]
	for k in ("res", "colors", "threshold"):
		if k in b:
			args += [f"--{k}", b[k]]
	if "grip" in b:
		args += ["--grip", ",".join(map(str, b["grip"]))]
	logs.append(blender(exe, "mc_model.py", *args))
	ic = b.get("icon", {})
	ipal = ART / "palettes" / f"gunmetal_{gid}_icon.png"  # wider glow bands, readable at 32px
	iemit = ART / "palettes" / f"gunmetal_{gid}_icon_emit.png"
	iargs = [glb, mod / "textures/item" / f"{gid}_icon.png", "--palette", ipal, "--emit-mask", iemit]
	for k in ("yaw", "pitch", "roll"):
		if k in ic:
			iargs += [f"--{k}", ic[k]]
	if preview:
		iargs += ["--hires", Path(preview) / f"{gid}_hires.png"]
	logs.append(blender(exe, "render_icon.py", *iargs))
	(mod / "models/item" / f"{gid}_icon.json").write_text(json.dumps(
		{"parent": "minecraft:item/generated", "textures": {"layer0": f"ashfall:item/{gid}_icon"}}, indent="\t") + "\n")
	(mod / "items" / f"{gid}.json").write_text(json.dumps(item_json(gid), indent="\t") + "\n")
	return f"== {gid} ({b['kenney']})\n" + "\n".join(logs)


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--preview")
	ap.add_argument("--jobs", type=int, default=4)
	ap.add_argument("--blender", default=DEFAULT_BLENDER)
	a = ap.parse_args()
	cfg = json.loads((ART / "blasters.json").read_text())
	todo = {k: v for k, v in cfg["blasters"].items() if not a.only or k in a.only}
	if not (ROOT / cfg["kit"]).is_dir():
		sys.exit(f"missing Kenney Blaster Kit at {cfg['kit']}")
	if a.preview:
		Path(a.preview).mkdir(parents=True, exist_ok=True)
	print(blender(a.blender, "palette.py", *[f"{k}={v['accent']}" for k, v in todo.items()]))
	failed = []
	with ThreadPoolExecutor(a.jobs) as ex:
		futs = {k: ex.submit(build_one, a.blender, cfg, k, v, a.preview) for k, v in todo.items()}
		for k, f in futs.items():
			try:
				print(f.result())
			except Exception as e:  # keep going so one failure does not hide the others
				failed.append(k)
				print(f"FAILED {k}: {e}", file=sys.stderr)
	if failed:
		sys.exit(f"failed: {', '.join(failed)}")


if __name__ == "__main__":
	main()
