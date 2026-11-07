#!/usr/bin/env python3
"""Generate the blaster scope overlay, textures/misc/blaster_scope.png (stdlib only).

A 256x256 sight picture, Ashfall's own design: opaque black outside a round eyepiece, a stepped
pixel-art falloff and a gunmetal rim at its edge, a faint darkening toward the rim, and a duplex
reticle in the blasters' cyan energy accent: thick posts from the rim, thin lines with mil ticks
toward the centre, an open centre ring and a centre dot. The game stretches it over the shorter
screen side and blacks out the rest (ScopeOverlay).

Usage: python3 -I tools/textures/hud_scope.py [--root <repo root>]
"""
import argparse
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sprite import Sprite, rgb  # noqa: E402

OUT = "modules/arms/src/main/resources/assets/ashfall/textures/misc/blaster_scope.png"

SIZE = 256
C = SIZE / 2            # centre (between pixels 127 and 128)
R = 116                 # eyepiece radius
RIM = rgb("20242e")     # gunmetal rim
RIM_HI = rgb("3a4252")  # rim highlight, upper left
ACCENT = rgb("5ae6ff", 235)
ACCENT_DIM = rgb("5ae6ff", 150)
BLACK = (0, 0, 0, 255)

# Stepped falloff inside the rim: (distance from rim in px, alpha). Bands, not a smooth ramp, to stay pixel art.
FALLOFF = ((3, 200), (6, 130), (10, 70), (16, 30))


def eyepiece(s):
	for y in range(SIZE):
		for x in range(SIZE):
			dx, dy = x + 0.5 - C, y + 0.5 - C
			d = math.hypot(dx, dy)
			if d >= R + 2:
				s.set(x, y, BLACK)
			elif d >= R:
				# Two-pixel rim, lit from the upper left.
				s.set(x, y, RIM_HI if dx + dy < -R * 0.9 else RIM)
			else:
				inside = R - d
				for depth, alpha in FALLOFF:
					if inside < depth:
						s.set(x, y, (0, 0, 0, alpha))
						break


def blend(s, x, y, c):
	"""Draw c over whatever is there (the reticle sits on top of the falloff)."""
	if not (0 <= x < SIZE and 0 <= y < SIZE):
		return
	r, g, b, a = s.px[y][x]
	if a == 0:
		s.set(x, y, c)
		return
	t = c[3] / 255
	out_a = min(255, c[3] + int(a * (1 - t)))
	s.set(x, y, (int(c[0] * t + r * (1 - t)), int(c[1] * t + g * (1 - t)), int(c[2] * t + b * (1 - t)), out_a))


def reticle(s):
	lo, hi = int(C) - 1, int(C)          # the two centre rows/columns
	post_end = 62                        # thick posts stop this far from the centre
	gap = 9                              # thin lines stop this far from the centre
	edge = R - 1
	for k in range(gap, edge):
		thick = k >= post_end
		for side in (-1, 1):
			base = hi + k if side > 0 else lo - k
			if thick:
				for w in (lo - 1, lo, hi, hi + 1):
					blend(s, base, w, ACCENT)   # horizontal posts
					blend(s, w, base, ACCENT)   # vertical posts
			else:
				blend(s, base, lo, ACCENT)
				blend(s, base, hi, ACCENT)
				blend(s, lo, base, ACCENT)
				blend(s, hi, base, ACCENT)
	# Mil ticks along the thin lines, every 10 px, longer every 20.
	for k in range(gap + 10, post_end, 10):
		half = 4 if (k - gap) % 20 == 0 else 2
		for side in (-1, 1):
			base = hi + k if side > 0 else lo - k
			for w in range(lo - half, hi + half + 1):
				if w in (lo, hi):
					continue  # the line itself is already drawn
				blend(s, base, w, ACCENT_DIM)   # ticks across the horizontal line
				blend(s, w, base, ACCENT_DIM)   # ticks across the vertical line
	# Open centre ring and dot.
	for y in range(SIZE):
		for x in range(SIZE):
			d = math.hypot(x + 0.5 - C, y + 0.5 - C)
			if 5.0 <= d < 6.2:
				blend(s, x, y, ACCENT_DIM)
	for y in (lo, hi):
		for x in (lo, hi):
			blend(s, x, y, ACCENT)


def draw():
	s = Sprite(SIZE, SIZE)
	eyepiece(s)
	reticle(s)
	return s


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent.parent, type=Path)
	args = ap.parse_args()
	out = args.root / OUT
	out.parent.mkdir(parents=True, exist_ok=True)
	out.write_bytes(draw().png())
	print(f"wrote {out}")


if __name__ == "__main__":
	main()
