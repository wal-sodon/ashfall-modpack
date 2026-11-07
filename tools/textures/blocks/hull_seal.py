"""Hull Seal: the Wreck's blast plate across its hatch, heavier and darker than the hull around it, crossed by two
locking bars, with a dead crystal lock in the middle that only the Grid at 80% opens."""
from _finale import plating, HULL, STEEL, DIM

OWNER = "arms"


def draw():
	s = plating(salt=331)
	for y in (3, 12):
		s.hline(0, 15, y, STEEL[3])
		s.hline(0, 15, y + 1, HULL[4])
	s.rect(6, 6, 9, 9, HULL[4])
	s.rect(7, 7, 8, 8, DIM[1])
	s.set(7, 7, DIM[0])
	return s
