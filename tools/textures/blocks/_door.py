"""Shared drawing for the Reinforced Door halves: vertical oak boards inside a riveted alloy frame."""
from sprite import rgb
from _wood import OAK, IRON, h, rivet

# Alloy plate (the same cool grey as the alloy_plate icon, a touch darker so the frame reads on the boards).
ALLOY = [rgb("d9dde3"), rgb("aab0ba"), rgb("7f8794"), rgb("565d6b"), rgb("343947")]


def boards(s, salt):
	"""Four vertical oak boards (x 2-13) with a dark seam between them."""
	for x in range(2, 14):
		for y in range(16):
			r = h(x, y, salt)
			c = OAK[1] if r < 0.55 else OAK[2]
			if h(x, y // 4, salt + 5) > 0.82:
				c = OAK[2]
			if r > 0.97:
				c = OAK[3]
			s.set(x, y, c)
	for x in (4, 7, 10):
		s.vline(x, 0, 15, OAK[4])
		s.vline(x + 1, 0, 15, OAK[0])


def frame(s):
	"""Alloy stiles down both edges (x 0-1 and 14-15), lit on the left, shaded on the right."""
	for x, tones in ((0, (ALLOY[1], ALLOY[2])), (14, (ALLOY[2], ALLOY[3]))):
		s.vline(x, 0, 15, tones[0])
		s.vline(x + 1, 0, 15, tones[1])


def strap(s, y):
	"""A horizontal alloy strap across the door at row y (2 px tall) with rivets at the boards."""
	s.hline(0, 15, y, ALLOY[1])
	s.hline(0, 15, y + 1, ALLOY[3])
	for x in (3, 8, 12):
		s.set(x, y, ALLOY[0])
