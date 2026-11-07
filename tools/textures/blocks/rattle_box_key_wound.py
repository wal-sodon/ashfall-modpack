"""Rattle Box winding key while it runs: polished brass, so a wound box is easy to spot."""
from sprite import Sprite, rgb
from _wood import h

OWNER = "director"

BRASS = [rgb("fff0a8"), rgb("e8c45a"), rgb("c39a34"), rgb("8a6522")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, BRASS[1] if h(x, y, salt=113) > 0.3 else BRASS[2])
	s.hline(0, 15, 0, BRASS[0])
	s.vline(0, 0, 15, BRASS[0])
	s.hline(0, 15, 15, BRASS[3])
	s.vline(15, 0, 15, BRASS[3])
	return s
