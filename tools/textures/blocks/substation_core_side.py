"""Substation Core side, dark: a heavy switchgear drum with riveted gunmetal plates, a hazard band and a dead
glass gauge window. Reads as the heavy core marker's replacement: the thing the whole district runs through."""
from sprite import Sprite, rgb
from _wood import h, rivet

OWNER = "arms"

STEEL = [rgb("8a8f9c"), rgb("6b707d"), rgb("51555f"), rgb("3a3d46"), rgb("26282f")]
HAZARD = [rgb("e3b23c"), rgb("1d1d22")]
GLASS_DEAD = [rgb("5d6670"), rgb("3b424c"), rgb("252a31")]


def body(s):
	"""Shared housing: plates, seams, band, rivets. The window is drawn by the caller (dark or lit)."""
	for y in range(16):
		for x in range(16):
			s.set(x, y, STEEL[1] if h(x, y, salt=57) > 0.82 else STEEL[2])
	s.vline(0, 0, 15, STEEL[0])
	s.vline(15, 0, 15, STEEL[4])
	s.hline(0, 15, 0, STEEL[0])
	s.hline(0, 15, 15, STEEL[4])
	# hazard band along the bottom: dark lip above
	s.hline(1, 14, 11, STEEL[4])
	for y in range(12, 15):
		for x in range(1, 15):
			s.set(x, y, HAZARD[0] if ((x + y) // 2) % 2 == 0 else HAZARD[1])
	# vertical bus bars either side of the window
	for x in (3, 12):
		s.vline(x, 2, 9, STEEL[3])
		s.vline(x - 1, 2, 9, STEEL[0])
	for x, y in ((1, 1), (14, 1), (1, 9), (14, 9)):
		rivet(s, x, y)


def window(s, ramp):
	"""The round-cornered gauge window above the band."""
	s.rect(5, 3, 10, 8, ramp[2])
	s.rect(6, 4, 9, 7, ramp[1])
	s.set(6, 4, ramp[0])
	s.set(7, 4, ramp[0])
	s.set(6, 5, ramp[0])
	for x, y in ((5, 3), (10, 3), (5, 8), (10, 8)):
		s.set(x, y, STEEL[3])


def draw():
	s = Sprite()
	body(s)
	window(s, GLASS_DEAD)
	return s
