"""Distress Beacon side: a battered battery box with hazard stripes and a hand-painted SOS."""
from sprite import Sprite, rgb
from _wood import IRON, RUST, h

OWNER = "arms"
RED = rgb("c62828")
YELLOW = rgb("f2c230")
DARK = rgb("2b2b30")


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			c = IRON[3] if h(x, y, salt=23) > 0.2 else IRON[2]
			if h(x, y, salt=29) > 0.93:
				c = RUST[1]
			s.set(x, y, c)
	for x in range(16):
		for y in (0, 1, 14, 15):
			s.set(x, y, YELLOW if (x + y) // 2 % 2 == 0 else DARK)
	# S O S in red paint
	for i, ox in enumerate((2, 6, 10)):
		if i == 1:
			s.rect(ox, 5, ox + 3, 10, RED)
			s.rect(ox + 1, 6, ox + 2, 9, IRON[3])
		else:
			s.hline(ox, ox + 3, 5, RED)
			s.vline(ox, 5, 7, RED)
			s.hline(ox, ox + 3, 7, RED)
			s.vline(ox + 3, 7, 10, RED)
			s.hline(ox, ox + 3, 10, RED)
	return s
