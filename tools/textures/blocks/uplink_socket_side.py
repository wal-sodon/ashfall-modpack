"""Uplink Socket side, unpowered: Wreck hull plating with a vertical conduit of dead crystal glass up the middle."""
from _finale import plating, DIM, STEEL

OWNER = "arms"


def draw(lit=False, ramp=DIM):
	s = plating(salt=301)
	s.rect(6, 1, 9, 14, STEEL[3])
	s.rect(7, 2, 8, 13, ramp[2] if not lit else ramp[2])
	s.vline(7, 2, 13, ramp[1])
	for y in (4, 8, 12):
		s.hline(6, 9, y, STEEL[2])
	return s
