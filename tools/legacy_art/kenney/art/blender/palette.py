"""Repaint Kenney's blaster-kit colormap into realistic gunmetal palettes with an energy accent.

Usage (needs numpy, so run it with Blender's Python; no bpy is used):
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/palette.py -- \
      [--map tools/art/palettes/gunmetal.json] [--out-dir tools/art/palettes] name=#rrggbb [name=#rrggbb ...]

For each name=accent it writes <out-dir>/gunmetal_<name>.png (512x512, same layout as the Kenney
colormap, so the GLBs' UVs land on the repainted swatches) and gunmetal_<name>_emit.png (white where
the colour should glow: the energy accent; black elsewhere), plus gunmetal_<name>_icon(.png/_emit.png)
with wider glow bands for small icon renders (--icon-widen, 0 to skip). The mapping lives in the JSON: each
swatch rect gets a role; each role is a top->bottom gradient driven by the source swatch's own
luminance ramp, optionally with a slice of the ramp ('glow_band' [t0,t1], or 'glow_top') replaced by the
glowing accent.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import numpy as np  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]


def colour(expr, accent):
	expr = expr.replace(" ", "")
	m = re.fullmatch(r"mix\((.+?),(.+?),([0-9.]+)\)", expr)
	if m:
		a, b = colour(m.group(1), accent), colour(m.group(2), accent)
		t = float(m.group(3))
		return a * (1 - t) + b * t
	if expr == "ACCENT":
		return np.array(accent, float)
	return np.array(common.hex_rgb(expr), float)


def build(mapping, accent, widen=0.0):
	src = common.read_png(ROOT / mapping["source"]).astype(float)
	h, w = src.shape[:2]
	lum = src[..., :3] @ np.array([0.299, 0.587, 0.114])
	out = np.zeros((h, w, 4), np.uint8)
	emit = np.zeros((h, w, 4), np.uint8)
	emit[..., 3] = 255
	roles = mapping["roles"]
	todo = [{"rect": [0, 0, w, h], "role": mapping["default_role"]}] + mapping["swatches"]
	for sw in todo:
		x0, y0, x1, y1 = sw["rect"]
		role = roles[sw["role"]]
		top, bot = colour(role["top"], accent), colour(role["bottom"], accent)
		band = list(role.get("glow_band", [0.0, role.get("glow_top", 0.0)]))
		if widen and 0 < band[0] and band[1] < 1:
			band = [band[0] - widen, band[1] + widen]
		glow = colour(role.get("glow", "ACCENT"), accent)
		for cx in range(x0, x1, 32):  # Kenney swatches are 32px columns, flat or vertical gradient
			col = lum[y0:y1, cx:cx + 32]
			hi, lo = col.max(), col.min()
			t = (hi - col) / (hi - lo) if hi - lo > 4 else np.full(col.shape, 0.3)
			rgb = top[None, None] * (1 - t[..., None]) + bot[None, None] * t[..., None]
			if band[1] >= 1 and band[0] <= 0:
				g = np.ones(t.shape, bool)  # whole swatch is energy (keeps its own ramp)
			else:
				g = (t >= band[0]) & (t < band[1])
				rgb[g] = glow  # flat bright accent stripe in that slice of the ramp
			out[y0:y1, cx:cx + 32, :3] = np.clip(np.round(rgb), 0, 255)
			emit[y0:y1, cx:cx + 32, :3] = np.where(g[..., None], 255, 0)
	out[..., 3] = 255
	return out, emit


def main():
	import argparse
	ap = argparse.ArgumentParser(prog="palette.py")
	ap.add_argument("--map", default=str(ROOT / "tools/art/palettes/gunmetal.json"))
	ap.add_argument("--out-dir", default=str(ROOT / "tools/art/palettes"))
	ap.add_argument("--icon-widen", type=float, default=0.08,
		help="also write gunmetal_<name>_icon(.png/_emit.png) with glow bands widened by this much on each side "
		"(thin strips survive downscaling to 32px icons); 0 = skip")
	ap.add_argument("accents", nargs="+", help="name=#rrggbb")
	args = ap.parse_args(common.script_args())
	mapping = json.loads(Path(args.map).read_text())
	for spec in args.accents:
		name, hexc = spec.split("=")
		pal, emit = build(mapping, common.hex_rgb(hexc))
		out = Path(args.out_dir)
		common.write_png(out / f"gunmetal_{name}.png", pal)
		common.write_png(out / f"gunmetal_{name}_emit.png", emit)
		print(f"wrote {out / f'gunmetal_{name}.png'} (+ _emit)")
		if args.icon_widen:
			pal, emit = build(mapping, common.hex_rgb(hexc), args.icon_widen)
			common.write_png(out / f"gunmetal_{name}_icon.png", pal)
			common.write_png(out / f"gunmetal_{name}_icon_emit.png", emit)


if __name__ == "__main__":
	main()
