"""Shared palettes and pieces for the Black Start's blocks: the Wreck's dark hull plating (deepslate-blue gunmetal),
its violet crystal, city-grid steel and the breakers' hazard paint. Not a texture itself (leading underscore)."""
from sprite import Sprite, rgb
from _wood import h

HULL = [rgb("6a6f84"), rgb("4d5266"), rgb("3a3e4f"), rgb("2a2d3a"), rgb("1b1d26")]
CRYSTAL = [rgb("fff3fa"), rgb("ee8ff0"), rgb("cf4fe6"), rgb("9a3ad8"), rgb("6a2bb5"), rgb("3b1c78")]
DIM = [rgb("6a5a7e"), rgb("4e4262"), rgb("3a3148"), rgb("2a2434")]
STEEL = [rgb("e6e1d6"), rgb("aeb1b8"), rgb("7b7f8c"), rgb("4c4f62"), rgb("2e3040")]
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630")]
HAZARD = [rgb("e3b23c"), rgb("1d1d22")]
RED = [rgb("ff6a5a"), rgb("c02a2a"), rgb("5a1e1e")]
GREEN = [rgb("b4ff8a"), rgb("5ad04a"), rgb("1e4a22")]


def plating(salt):
	"""A hull plate: mottled gunmetal with seams every 8 px, a lit top edge and sparse scorch marks."""
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt)
			s.set(x, y, HULL[2] if r < 0.7 else HULL[1] if r < 0.9 else HULL[3])
	for y in (0, 8):
		s.hline(0, 15, y, HULL[0])
	for y in (7, 15):
		s.hline(0, 15, y, HULL[4])
	s.vline(0, 0, 15, HULL[1])
	s.vline(15, 0, 15, HULL[4])
	for x, y in ((2, 2), (13, 2), (2, 10), (13, 10)):
		s.set(x, y, STEEL[1])
		s.set(x + 1, y + 1, HULL[4])
	return s


def gem(s, cx, cy, r, ramp):
	"""A faceted diamond-shaped crystal centred at (cx, cy): bright top-left facet, dark bottom-right."""
	for y in range(cy - r, cy + r + 1):
		for x in range(cx - r, cx + r + 1):
			d = abs(x - cx) + abs(y - cy)
			if d > r:
				continue
			if d == r:
				c = ramp[4]
			elif x <= cx and y <= cy:
				c = ramp[1]
			elif x > cx and y > cy:
				c = ramp[3]
			else:
				c = ramp[2]
			s.set(x, y, c)
	s.set(cx - 1, cy - 1, ramp[0])
