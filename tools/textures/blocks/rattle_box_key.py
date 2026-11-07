"""Rattle Box winding key at rest: dull tin."""
from sprite import Sprite
from _wood import IRON, h

OWNER = "director"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, IRON[2] if h(x, y, salt=109) > 0.3 else IRON[3])
	s.hline(0, 15, 0, IRON[1])
	s.vline(0, 0, 15, IRON[1])
	s.hline(0, 15, 15, IRON[4])
	s.vline(15, 0, 15, IRON[4])
	return s
