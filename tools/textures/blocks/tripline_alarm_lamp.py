"""Tripline Alarm lamp, off: dark red glass."""
from sprite import Sprite, rgb
from _wood import h

OWNER = "director"

GLASS = [rgb("8a3a36"), rgb("6a2a2a"), rgb("4a1e20")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, GLASS[1] if h(x, y, salt=137) > 0.25 else GLASS[2])
	s.rect(2, 2, 5, 5, GLASS[0])
	return s
