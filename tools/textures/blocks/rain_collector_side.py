"""Rain Collector side: corrugated scrap sheet, rust streaks, a taped seam and a riveted rim band."""
from sprite import Sprite
from _wood import IRON, RUST, h, rivet

OWNER = "director"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			# corrugation: a light and a dark column every 4 pixels
			c = IRON[1] if x % 4 == 0 else IRON[3] if x % 4 == 2 else IRON[2]
			if h(x, y, salt=61) > 0.9:
				c = RUST[1]
			s.set(x, y, c)
	for x in (5, 10, 13):
		for y in range(int(4 + h(x, 0, 3) * 4), 15):
			if h(x, y, salt=67) > 0.35:
				s.set(x, y, RUST[2] if y > 11 else RUST[1])
	# rim band and bottom edge
	s.hline(0, 15, 0, IRON[0])
	s.hline(0, 15, 1, IRON[3])
	s.hline(0, 15, 15, IRON[4])
	for x in (2, 7, 12):
		rivet(s, x, 1)
	# grey duct-tape patch over a seam
	s.rect(7, 6, 9, 11, IRON[1])
	s.vline(7, 6, 11, IRON[2])
	return s
