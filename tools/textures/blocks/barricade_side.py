"""Barricade side: the board ends seen edge-on (only the middle six columns are used by the model)."""
from sprite import Sprite
from _wood import PINE, RUST, GAP, h

OWNER = "director"


def draw():
	s = Sprite()
	s.rect(0, 0, 15, 15, GAP)
	for top, bottom in ((0, 4), (5, 10), (11, 15)):
		for y in range(top, bottom):
			for x in range(0, 16):
				# end grain: concentric-ish rings by distance from a point in the board
				ring = int(((x - 8) ** 2 + (y - (top + bottom) / 2) ** 2) ** 0.5 + h(x, y, 3) * 1.2)
				s.set(x, y, PINE[1] if ring % 3 else PINE[2])
		s.hline(0, 15, top, PINE[0])
	# the straps' edges on the face side of the panel
	s.vline(5, 0, 15, RUST[1])
	s.vline(10, 0, 15, RUST[2])
	return s
