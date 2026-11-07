#!/usr/bin/env python3
"""Generate Ashfall's 16x16 item textures (stdlib only).

Each icon is code in tools/textures/items/<item id>.py: a module with OWNER (the mod module whose
resources hold the item: arms, director or salvage) and draw() returning a Sprite. Keeping the art
as code means it lives in version control and is regenerated after a palette tweak.

Usage: python3 -I tools/gen_textures.py [--root <repo root>] [--only <item id>...] [--sheet <png>]
"""
import argparse
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent / "textures"
sys.path.insert(0, str(HERE))
from sprite import Sprite  # noqa: E402

TEXTURE_DIR = "modules/{owner}/src/main/resources/assets/ashfall/textures/item"


def load_items():
	items = {}
	for path in sorted((HERE / "items").glob("*.py")):
		spec = importlib.util.spec_from_file_location(f"ashfall_icon_{path.stem}", path)
		module = importlib.util.module_from_spec(spec)
		spec.loader.exec_module(module)
		items[path.stem] = module
	return items


def contact_sheet(sprites, scale=10, pad=6):
	"""All icons side by side, scaled up, on a dark background, for reviewing the set."""
	n = len(sprites)
	sheet = Sprite.__new__(Sprite)
	width, height = n * (16 * scale + pad) + pad, 16 * scale + 2 * pad
	sheet.px = [[(40, 40, 48, 255)] * width for _ in range(height)]
	for k, sprite in enumerate(sprites):
		for y in range(16):
			for x in range(16):
				c = sprite.px[y][x]
				if c[3] == 0:
					continue
				for dy in range(scale):
					for dx in range(scale):
						sheet.px[pad + y * scale + dy][pad + k * (16 * scale + pad) + x * scale + dx] = c
	return sheet.png_sized(width, height)


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent, type=Path)
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--sheet", type=Path, help="also write a scaled contact sheet of the generated icons")
	args = ap.parse_args()
	drawn = []
	for name, module in load_items().items():
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
