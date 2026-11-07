"""Reinforced Door, top half: oak boards in an alloy frame, a barred vision slit and a top strap."""
from sprite import Sprite
from _door import ALLOY, boards, frame, strap
from _wood import GAP

OWNER = "director"


def draw():
	s = Sprite()
	boards(s, salt=31)
	frame(s)
	strap(s, 0)
	strap(s, 12)
	# vision slit with two bars
	s.rect(4, 4, 11, 8, GAP)
	s.hline(4, 11, 4, ALLOY[3])
	for x in (6, 9):
		s.vline(x, 5, 8, ALLOY[1])
	s.hline(3, 12, 9, ALLOY[2])
	return s
