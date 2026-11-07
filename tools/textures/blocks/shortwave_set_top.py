"""Shortwave Set top: olive steel lid with a carry handle and the antenna socket."""
from sprite import Sprite, rgb
from _wood import IRON, h

OWNER = "arms"
CASE = [rgb("7d8061"), rgb("666a4e"), rgb("51553f"), rgb("3b3e2f")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, CASE[2] if h(x, y, salt=17) > 0.85 else CASE[0] if y in (4, 11) else CASE[1])
	s.hline(4, 11, 7, IRON[3])
	s.hline(4, 11, 8, IRON[4])
	s.rect(12, 5, 13, 6, IRON[4])
	return s
