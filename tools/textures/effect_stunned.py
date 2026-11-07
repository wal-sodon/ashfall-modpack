#!/usr/bin/env python3
"""Generate the 18x18 Stunned mob effect icon (stdlib only).

A pale blue jagged spark ring over a dim halo, with two small stars orbiting it: the "seeing stars" a
stun bolt leaves on a zombie. Mob effect icons are 18x18 and live in textures/mob_effect.

Usage: python3 -I tools/textures/effect_stunned.py [--root <repo root>]
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sprite import Sprite, rgb  # noqa: E402

OUT_DIR = "modules/director/src/main/resources/assets/ashfall/textures/mob_effect"

# o outline, r ring, h ring highlight, s star, c star centre.
SHAPE = [
	"..................",
	"...o..............",
	"..oso.............",
	".oscso......o.....",
	"..oso......oso....",
	"...o....ooooco....",
	".......orrrhoo....",
	"......orrooorro...",
	".....orroo..orro..",
	".....oro.....oro..",
	".....orro...orro..",
	"......orrooorro...",
	".......orrrrro....",
	"........ooooo.....",
	"..................",
	"..................",
	"..................",
	"..................",
]

PALETTE = {
	"o": rgb("1b2433"),
	"r": rgb("7fc4ff"),
	"h": rgb("e6f6ff"),
	"s": rgb("bfe8ff"),
	"c": rgb("ffffff"),
}


def draw():
	s = Sprite(18, 18)
	# Shift the drawing down two rows so it sits centred in the icon frame.
	for y, row in enumerate(SHAPE):
		for x, ch in enumerate(row):
			if ch in PALETTE:
				s.set(x, y + 2, PALETTE[ch])
	return s


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent.parent, type=Path)
	args = ap.parse_args()
	out = args.root / OUT_DIR
	out.mkdir(parents=True, exist_ok=True)
	(out / "stunned.png").write_bytes(draw().png())
	print(f"wrote {out / 'stunned.png'}")


if __name__ == "__main__":
	main()
