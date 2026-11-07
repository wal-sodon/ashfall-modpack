"""Barricade front: three battered pine boards with dark gaps, bolted to two rusty scrap-metal straps."""
from sprite import Sprite
from _wood import PINE, IRON, RUST, GAP, h, plank_rows, rivet

OWNER = "director"


def draw():
	s = Sprite()
	s.rect(0, 0, 15, 15, GAP)
	plank_rows(s, PINE, [(0, 4), (5, 10), (11, 15)], salt=23, gap=GAP)
	# torn board ends: chew a few pixels off the corners so it reads as scavenged
	for x, y in ((0, 0), (15, 5), (15, 6), (0, 15), (1, 15)):
		s.set(x, y, GAP)
	for x0 in (3, 11):
		for y in range(16):
			r = h(x0, y, 5)
			base = RUST[1] if r < 0.35 else IRON[2]
			s.set(x0, y, base)
			s.set(x0 + 1, y, RUST[2] if r < 0.35 else IRON[3])
		s.vline(x0 + 2, 0, 15, PINE[4])  # shadow cast to the right of the strap
		s.set(x0, 0, IRON[1])
	for y in (2, 7, 13):
		rivet(s, 3, y)
		rivet(s, 11, y)
	return s
