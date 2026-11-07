"""Relay Breaker side, tripped: a grid cabinet with its big breaker lever thrown down and a red fault lamp."""
from sprite import Sprite
from _finale import STEEL, HAZARD, RED, GREEN
from _wood import h

OWNER = "arms"


def draw(tripped=True):
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, STEEL[2] if h(x, y, 313) < 0.88 else STEEL[3])
	s.hline(0, 15, 0, STEEL[1])
	s.hline(0, 15, 15, STEEL[4])
	s.vline(0, 0, 15, STEEL[1])
	s.vline(15, 0, 15, STEEL[4])
	# hazard band at the foot
	for x in range(1, 15):
		for y in (13, 14):
			s.set(x, y, HAZARD[0] if ((x + y) // 2) % 2 == 0 else HAZARD[1])
	# lever slot and lever
	s.rect(6, 3, 9, 11, STEEL[4])
	if tripped:
		s.rect(7, 8, 8, 11, STEEL[0])
		s.rect(6, 10, 9, 11, RED[1])
	else:
		s.rect(7, 3, 8, 6, STEEL[0])
		s.rect(6, 3, 9, 4, GREEN[1])
	# status lamps
	lamp = RED if tripped else GREEN
	s.rect(2, 3, 4, 5, STEEL[4])
	s.set(3, 4, lamp[0])
	s.rect(11, 3, 13, 5, STEEL[4])
	s.set(12, 4, lamp[1])
	return s
