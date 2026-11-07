"""Relay Breaker top: a steel lid with hazard edging and a cable gland."""
from sprite import Sprite
from _finale import STEEL, HAZARD
from _wood import h

OWNER = "arms"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, STEEL[2] if h(x, y, 311) < 0.85 else STEEL[3])
	for i in range(16):
		for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
			s.set(x, y, HAZARD[0] if (i // 2) % 2 == 0 else HAZARD[1])
	s.rect(6, 6, 9, 9, STEEL[4])
	s.rect(7, 7, 8, 8, STEEL[1])
	return s
