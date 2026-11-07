"""Uplink Socket top, empty: a heavy steel collar around a three-pin port (the Uplink's copper prongs fit it), the
crystal ring around the port dark."""
from _finale import plating, DIM, STEEL, COPPER

OWNER = "arms"


def draw(ring=DIM, core=None):
	s = plating(salt=303)
	s.rect(3, 3, 12, 12, STEEL[2])
	s.hline(3, 12, 3, STEEL[1])
	s.vline(3, 3, 12, STEEL[1])
	s.hline(3, 12, 12, STEEL[4])
	s.vline(12, 3, 12, STEEL[4])
	# crystal ring
	for x in range(5, 11):
		for y in range(5, 11):
			if x in (5, 10) or y in (5, 10):
				s.set(x, y, ring[1] if (x + y) % 3 else ring[0])
	s.rect(6, 6, 9, 9, STEEL[4] if core is None else core)
	for x in (6, 8):
		s.set(x + (1 if x == 8 else 0), 7, COPPER[2])
	s.set(7, 8, COPPER[2])
	return s
