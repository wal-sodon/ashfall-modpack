"""Spike Strip nail: bright steel shank with a dull tip."""
from sprite import Sprite
from _wood import IRON

OWNER = "director"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, IRON[1] if x < 8 else IRON[2])
	s.hline(0, 15, 0, IRON[0])
	s.hline(0, 15, 15, IRON[3])
	return s
