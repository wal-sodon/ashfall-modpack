"""Strongbox side: green safe enamel with a riveted steel band round the middle."""
from sprite import Sprite
from _strongbox import body, ENAMEL
from _wood import IRON, rivet

OWNER = "salvage"


def draw():
	s = Sprite()
	body(s, salt=211)
	s.hline(1, 14, 6, IRON[2])
	s.hline(1, 14, 7, IRON[3])
	s.hline(1, 14, 8, IRON[3])
	s.hline(1, 14, 9, IRON[4])
	for x in (2, 7, 12):
		rivet(s, x, 7)
	s.hline(1, 14, 10, ENAMEL[3])
	return s
