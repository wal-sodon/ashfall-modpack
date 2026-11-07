"""Bicycle: a rusty red city-bike frame, black tyres on grey rims, a worn brown saddle and grey bars and cranks.

Regions match the texOffs in BicycleModel (salvage client); each box's UV net is filled with its part's colours.
"""
from sprite import Sprite, rgb, CLEAR

OWNER = "salvage"

TYRE = [rgb("2b2b30"), rgb("1c1c20")]
RIM = [rgb("b9bcc2"), rgb("8b8f9a")]
FRAME = [rgb("b0423a"), rgb("8a2f2c"), rgb("6b2a28"), rgb("a8682e")]
STEEL = [rgb("c9ccd2"), rgb("8b8f9a"), rgb("5c6070")]
SEAT = [rgb("6e4a32"), rgb("4f3423")]
GRIP = rgb("26262b")


def net(w, h, d):
	"""Size of a box's UV net: (2d + 2w) by (d + h)."""
	return 2 * d + 2 * w, d + h


def fill(s, u, v, w, h, d, ramp, salt=0):
	nw, nh = net(w, h, d)
	for y in range(v, v + nh):
		for x in range(u, u + nw):
			s.set(x, y, ramp[(x * 7 + y * 3 + salt) % len(ramp)] if (x + y + salt) % 5 == 0 else ramp[0])


def wheel(s, u, v, w, h, d):
	"""Tyre all round, with the rim showing on the side faces."""
	nw, nh = net(w, h, d)
	for y in range(v, v + nh):
		for x in range(u, u + nw):
			s.set(x, y, TYRE[(x + y) % 2])
	# side faces (west/east) are d wide and h tall, below the top strip of height d
	for fx in (u, u + d + w):
		for y in range(v + d + 1, v + d + h - 1):
			for x in range(fx + 1, fx + d - 1):
				s.set(x, y, RIM[(x + y) % 2])
		s.set(fx + d // 2, v + d + h // 2, STEEL[2])


def draw():
	s = Sprite(64, 64)
	for y in range(64):
		for x in range(64):
			s.set(x, y, CLEAR)
	wheel(s, 0, 0, 1, 8, 6)
	wheel(s, 14, 0, 1, 6, 8)
	fill(s, 0, 14, 1, 1, 11, FRAME)      # top tube
	fill(s, 0, 28, 1, 9, 1, FRAME, 1)    # seat post
	fill(s, 4, 28, 1, 9, 1, FRAME, 2)    # fork
	fill(s, 8, 28, 1, 7, 1, FRAME, 3)    # down tube
	fill(s, 26, 14, 1, 1, 7, FRAME, 4)   # chain stay
	fill(s, 26, 24, 8, 1, 1, STEEL)      # handlebar
	for x in (26, 27, 42, 43):           # grips at the bar ends
		for y in (24, 25):
			s.set(x, y, GRIP)
	fill(s, 26, 27, 3, 1, 4, SEAT)       # saddle
	fill(s, 44, 14, 5, 1, 1, STEEL, 1)   # cranks
	return s
