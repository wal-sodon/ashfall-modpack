"""Distress Beacon top: a red strobe lens in a steel ring."""
from sprite import Sprite, rgb
from _wood import IRON

OWNER = "arms"
LENS = [rgb("ff8a80"), rgb("e53935"), rgb("b71c1c")]


def draw():
	s = Sprite()
	s.rect(0, 0, 15, 15, IRON[3])
	s.disc(7.5, 7.5, 6, IRON[1])
	s.disc(7.5, 7.5, 5, LENS[2])
	s.disc(7.5, 7.5, 4, LENS[1])
	s.rect(5, 5, 6, 6, LENS[0])
	return s
