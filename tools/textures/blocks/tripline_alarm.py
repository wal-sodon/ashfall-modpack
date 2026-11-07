"""Tripline Alarm casing: a dented olive-drab metal box with a wire spool and screws."""
from sprite import Sprite, rgb
from _wood import IRON, RUST, h, rivet

OWNER = "director"

DRAB = [rgb("8f9468"), rgb("727852"), rgb("5a5f40"), rgb("40442f")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt=127)
			c = DRAB[1] if r < 0.55 else DRAB[2]
			if r > 0.95:
				c = IRON[2]
			elif r < 0.04:
				c = RUST[1]
			s.set(x, y, c)
	s.hline(0, 15, 0, DRAB[0])
	s.vline(0, 0, 15, DRAB[0])
	s.hline(0, 15, 15, DRAB[3])
	s.vline(15, 0, 15, DRAB[3])
	for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
		rivet(s, x, y)
	# wire spool across the middle
	s.rect(4, 6, 11, 9, IRON[3])
	for x in range(4, 12):
		s.set(x, 7 if x % 2 else 8, IRON[1])
	s.hline(4, 11, 6, IRON[2])
	s.hline(4, 11, 9, IRON[4])
	return s
