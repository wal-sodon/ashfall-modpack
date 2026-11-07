"""Reinforced Door, bottom half: oak boards in an alloy frame, a kick plate and the handle."""
from sprite import Sprite
from _door import ALLOY, boards, frame, strap

OWNER = "director"


def draw():
	s = Sprite()
	boards(s, salt=37)
	frame(s)
	strap(s, 2)
	# kick plate
	s.rect(0, 11, 15, 15, ALLOY[2])
	s.hline(0, 15, 11, ALLOY[1])
	s.hline(0, 15, 15, ALLOY[4])
	for x in (2, 7, 13):
		s.set(x, 13, ALLOY[0])
	# handle
	s.rect(11, 6, 12, 7, ALLOY[0])
	s.set(12, 8, ALLOY[3])
	return s
