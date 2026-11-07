#!/usr/bin/env python3
"""Generate the 9x9 infection stage HUD sprites (stdlib only).

One spiky "germ" icon per visible stage (2, 3 and 4), drawn in the same size and outline style as the
thirst droplets so it sits next to that row like a vanilla icon. Only the colour changes between
stages, from sickly yellow through orange to red, so the stage reads at a glance without text.
Stage 1 has no sprite: it is cosmetic and the HUD stays clean.

Usage: python3 -I tools/textures/hud_infection.py [--root <repo root>]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sprite import Sprite, rgb  # noqa: E402

OUT_DIR = "modules/director/src/main/resources/assets/ashfall/textures/gui/sprites/hud"

# o outline, s spike tip, f fill, h highlight, d shade.
SHAPE = [
	"....s....",
	".s.ooo.s.",
	"..ohffo..",
	".ohfffdo.",
	"soffffdos",
	".offfddo.",
	"..ofddo..",
	".s.ooo.s.",
	"....s....",
]

OUTLINE = rgb("1b1b22")
STAGES = {
	2: {"f": rgb("b8c43a"), "h": rgb("e6ef8a"), "d": rgb("7f8a1f"), "s": rgb("7f8a1f")},
	3: {"f": rgb("e08a2c"), "h": rgb("ffc77a"), "d": rgb("a35a14"), "s": rgb("a35a14")},
	4: {"f": rgb("d4312a"), "h": rgb("ff8a7a"), "d": rgb("8e1712"), "s": rgb("8e1712")},
}


def germ(palette):
	s = Sprite(9, 9)
	for y, row in enumerate(SHAPE):
		for x, ch in enumerate(row):
			if ch == "o":
				s.set(x, y, OUTLINE)
			elif ch in palette:
				s.set(x, y, palette[ch])
	return s


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent.parent, type=Path)
	args = ap.parse_args()
	out = args.root / OUT_DIR
	out.mkdir(parents=True, exist_ok=True)
	for stage, palette in STAGES.items():
		name = f"infection_stage_{stage}"
		(out / f"{name}.png").write_bytes(germ(palette).png())
		print(f"wrote {out / name}.png")


if __name__ == "__main__":
	main()
