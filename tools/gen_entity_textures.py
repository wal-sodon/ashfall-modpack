#!/usr/bin/env python3
"""Generate Ashfall's 64x64 entity overlay skins (stdlib only).

Each skin is code in tools/textures/entities/<name>.py: a module with OWNER (the mod module whose resources hold
it) and draw() returning a 64x64 Sprite. The special infected draw these over the vanilla zombie texture, so the
art is only the additions (pustules, acid, scars) and the base stays whatever zombie texture the player has.

Usage: python3 -I tools/gen_entity_textures.py [--root <repo root>] [--only <name>...] [--sheet <png>]
"""
import argparse
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent / "textures"
sys.path.insert(0, str(HERE))
from sprite import Sprite  # noqa: E402

TEXTURE_DIR = "modules/{owner}/src/main/resources/assets/ashfall/textures/entity/special"


def load_skins():
	skins = {}
	for path in sorted((HERE / "entities").glob("*.py")):
		if path.stem.startswith("_"):
			continue
		spec = importlib.util.spec_from_file_location(f"ashfall_skin_{path.stem}", path)
		module = importlib.util.module_from_spec(spec)
		spec.loader.exec_module(module)
		skins[path.stem] = module
	return skins


def contact_sheet(sprites, scale=6, pad=6):
	"""All skins side by side, scaled up, on a dark background, for reviewing the set."""
	n = len(sprites)
	sheet = Sprite.__new__(Sprite)
	width, height = n * (64 * scale + pad) + pad, 64 * scale + 2 * pad
	sheet.w, sheet.h = width, height
	sheet.px = [[(40, 40, 48, 255)] * width for _ in range(height)]
	for k, sprite in enumerate(sprites):
		for y in range(64):
			for x in range(64):
				c = sprite.px[y][x]
				if c[3] == 0:
					continue
				for dy in range(scale):
					for dx in range(scale):
						sheet.px[pad + y * scale + dy][pad + k * (64 * scale + pad) + x * scale + dx] = c
	return sheet.png_sized(width, height)


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent, type=Path)
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--sheet", type=Path, help="also write a scaled contact sheet of the generated skins")
	args = ap.parse_args()
	drawn = []
	for name, module in load_skins().items():
		if args.only and name not in args.only:
			continue
		sprite = module.draw()
		out = args.root / TEXTURE_DIR.format(owner=module.OWNER)
		out.mkdir(parents=True, exist_ok=True)
		(out / f"{name}.png").write_bytes(sprite.png())
		drawn.append(sprite)
		print(f"wrote {out / name}.png")
	if args.sheet:
		args.sheet.write_bytes(contact_sheet(drawn))
		print(f"wrote {args.sheet}")


if __name__ == "__main__":
	main()
