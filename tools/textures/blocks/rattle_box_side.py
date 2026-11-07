"""Rattle Box side: an old tin box in chipped red enamel, a riveted lid seam and a row of sound holes."""
from sprite import Sprite, rgb
from _wood import IRON, RUST, h, rivet

OWNER = "director"

ENAMEL = [rgb("c4574a"), rgb("a2423a"), rgb("80322e"), rgb("5c2427")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt=101)
			c = ENAMEL[1] if r < 0.6 else ENAMEL[2]
			if r > 0.93:
				c = IRON[2]  # enamel chipped down to the tin
			elif r > 0.89:
				c = RUST[1]
			s.set(x, y, c)
	# lid seam with rivets
	s.hline(0, 15, 0, ENAMEL[0])
	s.hline(0, 15, 3, ENAMEL[3])
	s.hline(0, 15, 4, IRON[3])
	for x in (2, 7, 12):
		rivet(s, x, 1)
	# sound holes: the rattle inside is meant to be heard
	for x in (3, 6, 9, 12):
		s.set(x, 8, IRON[4])
		s.set(x + 1, 8, IRON[4])
		s.set(x, 9, IRON[4])
		s.set(x + 1, 9, ENAMEL[3])
	# worn bottom edge
	s.hline(0, 15, 15, ENAMEL[3])
	for x in range(16):
		if h(x, 14, salt=103) > 0.7:
			s.set(x, 14, RUST[2])
	return s
