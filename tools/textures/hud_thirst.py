#!/usr/bin/env python3
"""Generate the 9x9 thirst droplet HUD sprites (stdlib only).

Three sprites, drawn like vanilla's hunger icons: an empty droplet (dark outline and a dim inside)
that is always drawn, and full and half droplets drawn on top of it. The half droplet keeps its
right half, like the hunger bar, which also empties from the left end of each icon.

Usage: python3 -I tools/textures/hud_thirst.py [--root <repo root>]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sprite import Sprite, rgb  # noqa: E402

OUT_DIR = "modules/director/src/main/resources/assets/ashfall/textures/gui/sprites/hud"

# o outline, f fill, h highlight, d shade.
SHAPE = [
	"....o....",
	"...ofo...",
	"..offfo..",
	"..ohffo..",
	".ohfffdo.",
	".ohfffdo.",
	".offfddo.",
	"..odddo..",
	"...ooo...",
]

OUTLINE = rgb("1b1b22")
FULL = {"f": rgb("3a8ee6"), "h": rgb("a8dcff"), "d": rgb("2563b0")}
EMPTY = {"f": rgb("3a3a44"), "h": rgb("4a4a56"), "d": rgb("2c2c34")}


def droplet(palette, min_x=0):
	s = Sprite(9, 9)
	for y, row in enumerate(SHAPE):
		for x, ch in enumerate(row):
			if ch == "o":
				if x >= min_x or palette is EMPTY:
					s.set(x, y, OUTLINE)
			elif ch in palette and x >= min_x:
				s.set(x, y, palette[ch])
	return s


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent.parent, type=Path)
	args = ap.parse_args()
	out = args.root / OUT_DIR
	out.mkdir(parents=True, exist_ok=True)
	for name, sprite in (("thirst_empty", droplet(EMPTY)), ("thirst_full", droplet(FULL)), ("thirst_half", droplet(FULL, 4))):
		(out / f"{name}.png").write_bytes(sprite.png())
		print(f"wrote {out / name}.png")


if __name__ == "__main__":
	main()
