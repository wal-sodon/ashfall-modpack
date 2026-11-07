"""Tripline Alarm lamp, tripped: bright red glass with a hot centre."""
from sprite import Sprite, rgb

OWNER = "director"

GLOW = [rgb("ffe0c8"), rgb("ff8a6a"), rgb("ff4a3a"), rgb("d42a24")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			d = max(abs(x - 7.5), abs(y - 7.5))
			s.set(x, y, GLOW[0] if d < 2.5 else GLOW[1] if d < 4.5 else GLOW[2] if d < 6.5 else GLOW[3])
	return s
