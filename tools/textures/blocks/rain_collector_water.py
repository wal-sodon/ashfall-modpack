"""Rain Collector water: clear grey-blue rainwater with ripples."""
from sprite import Sprite, rgb
from _wood import h

OWNER = "director"

WATER = [rgb("9cc4e4", 220), rgb("6f9fcc", 210), rgb("4f7fae", 210)]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, WATER[2] if h(x, y, salt=79) > 0.7 else WATER[1])
	for cx, cy, r in ((4, 5, 2), (11, 10, 3)):
		for a in range(16):
			import math
			x = round(cx + r * math.cos(a * math.pi / 8))
			y = round(cy + r * math.sin(a * math.pi / 8))
			if 0 <= x < 16 and 0 <= y < 16:
				s.set(x, y, WATER[0])
	return s
