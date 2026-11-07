"""Shortwave Set front: olive steel case, a lit tuning dial with a red needle, a speaker grille and two knobs."""
from sprite import Sprite, rgb
from _wood import IRON, h

OWNER = "arms"
CASE = [rgb("7d8061"), rgb("666a4e"), rgb("51553f"), rgb("3b3e2f")]
DIAL = [rgb("f2d98c"), rgb("d9b968")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, CASE[2] if h(x, y, salt=11) > 0.85 else CASE[1])
	s.hline(0, 15, 0, CASE[0])
	s.hline(0, 15, 15, CASE[3])
	s.vline(0, 0, 15, CASE[0])
	s.vline(15, 0, 15, CASE[3])
	# tuning dial window, the top half (the model shows rows 7-15 of the 16 on a 9-high face)
	s.rect(2, 8, 9, 11, DIAL[1])
	s.rect(3, 9, 8, 10, DIAL[0])
	for x in (3, 5, 7):
		s.set(x, 9, IRON[3])
	s.vline(6, 8, 11, rgb("c62828"))
	# speaker grille
	for y in (8, 10, 12):
		s.hline(11, 14, y, CASE[3])
	# knobs
	for x in (3, 7):
		s.rect(x, 13, x + 1, 14, IRON[4])
		s.set(x, 13, IRON[2])
	return s
