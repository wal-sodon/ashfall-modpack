"""Overcharger icon: a fat red capacitor can with hazard bands and a bright arc jumping between its terminals."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

RED = [rgb("ff8a6a"), rgb("e04a32"), rgb("a8301e"), rgb("6a1c16")]
STEEL = [rgb("e6e1d6"), rgb("aeb1b8"), rgb("7b7f8c")]
HAZ_Y, HAZ_K = rgb("e8b830"), rgb("2b2a2e")
ARC = [rgb("fff6d8"), rgb("ffd47e")]


def draw():
	s = Sprite()
	# can body, shaded left to right
	for x in range(4, 12):
		c = RED[0] if x == 4 else RED[1] if x < 8 else RED[2] if x < 11 else RED[3]
		s.vline(x, 5, 13, c)
	# hazard band
	for x in range(4, 12):
		s.set(x, 10, HAZ_Y if x % 2 == 0 else HAZ_K)
	# steel cap and terminals
	s.hline(4, 11, 4, STEEL[1])
	s.hline(5, 10, 4, STEEL[0])
	s.vline(5, 2, 3, STEEL[1])
	s.vline(10, 2, 3, STEEL[2])
	# the arc
	for x, y in ((6, 1), (7, 2), (8, 1), (9, 2)):
		s.set(x, y, ARC[1])
	s.set(7, 1, ARC[0])
	s.set(8, 2, ARC[0])
	s.outline(OUTLINE)
	return s
