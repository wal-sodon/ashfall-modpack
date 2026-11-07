"""Shortwave Set side and back: olive steel case with cooling slots."""
from sprite import Sprite, rgb
from _wood import h

OWNER = "arms"
CASE = [rgb("7d8061"), rgb("666a4e"), rgb("51553f"), rgb("3b3e2f")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, CASE[2] if h(x, y, salt=13) > 0.85 else CASE[1])
	s.hline(0, 15, 7, CASE[0])
	s.hline(0, 15, 15, CASE[3])
	for x in range(3, 13, 3):
		s.vline(x, 9, 13, CASE[3])
	return s
