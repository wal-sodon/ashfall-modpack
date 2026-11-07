"""Strongbox top: plain green enamel plate with a rivet in each corner."""
from sprite import Sprite
from _strongbox import body, ENAMEL
from _wood import rivet

OWNER = "salvage"


def draw():
	s = Sprite()
	body(s, salt=223)
	s.rect(3, 3, 12, 12, ENAMEL[1])
	s.hline(3, 12, 3, ENAMEL[2])
	s.vline(3, 3, 12, ENAMEL[2])
	s.hline(3, 12, 12, ENAMEL[0])
	s.vline(12, 3, 12, ENAMEL[0])
	for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
		rivet(s, x, y)
	return s
