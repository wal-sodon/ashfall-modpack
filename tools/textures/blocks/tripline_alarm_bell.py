"""Tripline Alarm bell: a small tarnished brass dome."""
from sprite import Sprite, rgb
from _wood import h

OWNER = "director"

BRASS = [rgb("f2d77a"), rgb("cfa64a"), rgb("a27a30"), rgb("6e5120")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			c = BRASS[1] if x < 9 else BRASS[2]
			if h(x, y, salt=131) > 0.9:
				c = BRASS[3]  # tarnish
			s.set(x, y, c)
	s.vline(2, 0, 15, BRASS[0])
	s.vline(3, 0, 15, BRASS[0])
	s.hline(0, 15, 15, BRASS[3])
	return s
