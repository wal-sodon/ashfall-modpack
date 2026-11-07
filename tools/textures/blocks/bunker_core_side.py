"""Bunker Core side: a riveted steel housing with a recessed hazard-striped band and a glowing cell window.

Dark gunmetal plates (the core is the heart of a base, it should read heavier than reinforced planks),
a yellow-and-black band across the middle, and a small green-lit window where the Energy Cell sits.
"""
from sprite import Sprite, rgb
from _wood import h, rivet

OWNER = "director"

STEEL = [rgb("8a8f9c"), rgb("6b707d"), rgb("51555f"), rgb("3a3d46"), rgb("26282f")]
HAZARD = [rgb("e3b23c"), rgb("1d1d22")]
GLOW = [rgb("b8f27a"), rgb("6cc24a"), rgb("2f6b2a")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			n = h(x, y, salt=31)
			s.set(x, y, STEEL[1] if n > 0.8 else STEEL[2])
	# plate seams and lit edges: two plates, top and bottom
	for y0, y1 in ((0, 4), (11, 15)):
		s.hline(0, 15, y0, STEEL[0])
		s.hline(0, 15, y1, STEEL[3])
	s.vline(0, 0, 15, STEEL[0])
	s.vline(15, 0, 15, STEEL[4])
	# hazard band, recessed: dark lip above, light lip below
	s.hline(0, 15, 5, STEEL[4])
	for y in range(6, 10):
		for x in range(16):
			s.set(x, y, HAZARD[0] if ((x + y) // 2) % 2 == 0 else HAZARD[1])
	s.hline(0, 15, 10, STEEL[0])
	# cell window in the band
	s.rect(6, 6, 9, 9, GLOW[2])
	s.rect(7, 7, 8, 8, GLOW[1])
	s.set(7, 7, GLOW[0])
	# corner rivets
	for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
		rivet(s, x, y)
	return s
