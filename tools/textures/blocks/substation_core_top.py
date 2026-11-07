"""Substation Core top: a bolted steel lid with three insulator bushings (the cable chains in the control room
hang from these) on a dark hazard-ringed plate."""
from sprite import Sprite, rgb
from _wood import h, rivet

OWNER = "arms"

STEEL = [rgb("8a8f9c"), rgb("6b707d"), rgb("51555f"), rgb("3a3d46"), rgb("26282f")]
CERAMIC = [rgb("efe9dc"), rgb("c9c0ad"), rgb("8f8676")]
HAZARD = [rgb("e3b23c"), rgb("1d1d22")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, STEEL[2] if h(x, y, salt=91) < 0.85 else STEEL[1])
	# hazard ring around the edge
	for i in range(16):
		for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
			s.set(x, y, HAZARD[0] if (i // 2) % 2 == 0 else HAZARD[1])
	s.rect(1, 1, 14, 1, STEEL[0])
	s.rect(1, 14, 14, 14, STEEL[4])
	# three bushings: ceramic discs with a dark centre
	for cx, cy in ((4, 5), (11, 5), (7, 11)):
		s.rect(cx - 1, cy - 1, cx + 1, cy + 1, CERAMIC[1])
		s.set(cx - 1, cy - 1, CERAMIC[0])
		s.set(cx + 1, cy + 1, CERAMIC[2])
		s.set(cx, cy, STEEL[4])
	for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
		rivet(s, x, y)
	return s
