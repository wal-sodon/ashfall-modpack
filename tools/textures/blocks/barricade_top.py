"""Barricade top: the upper board's top face, grain running along the board, strap tops showing."""
from sprite import Sprite
from _wood import PINE, IRON, h

OWNER = "director"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x // 4, y, 31)
			s.set(x, y, PINE[1] if r < 0.6 else PINE[2])
			if h(x, y, 37) > 0.95:
				s.set(x, y, PINE[3])
	s.hline(0, 15, 5, PINE[0])
	s.hline(0, 15, 10, PINE[3])
	for x0 in (3, 11):
		s.rect(x0, 5, x0 + 1, 6, IRON[2])
	return s
