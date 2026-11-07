#!/usr/bin/env python3
"""Generate Ashfall's 16x16 block textures (stdlib only).

The block counterpart of gen_textures.py: each texture is code in tools/textures/blocks/<texture>.py,
a module with OWNER (the mod module whose resources hold the block) and draw() returning a Sprite.
Files starting with "_" are shared helpers, not textures. Output goes to
modules/<owner>/src/main/resources/assets/ashfall/textures/block/<texture>.png.

Usage: python3 -I tools/gen_block_textures.py [--root <repo root>] [--only <texture>...] [--sheet <png>]
"""
import argparse
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent / "textures"
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "blocks"))
sys.path.insert(0, str(HERE.parent))  # -I leaves the script folder off sys.path
from gen_textures import contact_sheet  # noqa: E402

TEXTURE_DIR = "modules/{owner}/src/main/resources/assets/ashfall/textures/block"


def load_blocks():
	blocks = {}
	for path in sorted((HERE / "blocks").glob("*.py")):
		if path.stem.startswith("_"):
			continue
		spec = importlib.util.spec_from_file_location(f"ashfall_block_{path.stem}", path)
		module = importlib.util.module_from_spec(spec)
		spec.loader.exec_module(module)
		blocks[path.stem] = module
	return blocks


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent, type=Path)
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--sheet", type=Path, help="also write a scaled contact sheet of the generated textures")
	args = ap.parse_args()
	drawn = []
	for name, module in load_blocks().items():
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
