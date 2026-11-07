"""Rattle Box lid: red enamel worn at the rim, a tin key boss in the middle."""
from sprite import Sprite, rgb
from _wood import IRON, RUST, h

OWNER = "director"

ENAMEL = [rgb("c4574a"), rgb("a2423a"), rgb("80322e"), rgb("5c2427")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt=107)
			c = ENAMEL[0] if r > 0.85 else ENAMEL[1]
			edge = x in (0, 15) or y in (0, 15)
			if edge:
				c = IRON[2] if r > 0.5 else ENAMEL[2]
			elif r < 0.05:
				c = RUST[1]
			s.set(x, y, c)
	s.hline(1, 14, 1, ENAMEL[0])
	s.vline(1, 1, 14, ENAMEL[0])
	s.hline(1, 14, 14, ENAMEL[3])
	s.vline(14, 1, 14, ENAMEL[3])
	# key boss
	s.rect(6, 6, 9, 9, IRON[2])
	s.hline(6, 9, 6, IRON[1])
	s.vline(6, 6, 9, IRON[1])
	s.hline(6, 9, 9, IRON[3])
	s.vline(9, 6, 9, IRON[3])
	s.rect(7, 7, 8, 8, IRON[4])
	return s
