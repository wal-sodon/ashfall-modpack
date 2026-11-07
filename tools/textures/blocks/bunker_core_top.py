"""Bunker Core top: a steel hatch with a round, green-lit cell socket in the middle and four bolts."""
from sprite import Sprite, rgb
from _wood import h, rivet

OWNER = "director"

STEEL = [rgb("8a8f9c"), rgb("6b707d"), rgb("51555f"), rgb("3a3d46"), rgb("26282f")]
GLOW = [rgb("b8f27a"), rgb("6cc24a"), rgb("2f6b2a")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			s.set(x, y, STEEL[1] if h(x, y, salt=47) > 0.8 else STEEL[2])
	# hatch rim: lit top-left, shadowed bottom-right
	s.hline(0, 15, 0, STEEL[0])
	s.vline(0, 0, 15, STEEL[0])
	s.hline(0, 15, 15, STEEL[4])
	s.vline(15, 0, 15, STEEL[4])
	s.hline(2, 13, 2, STEEL[3])
	s.vline(2, 2, 13, STEEL[3])
	s.hline(2, 13, 13, STEEL[0])
	s.vline(13, 2, 13, STEEL[0])
	# socket
	s.disc(7.5, 7.5, 3.2, STEEL[4])
	s.disc(7.5, 7.5, 2.2, GLOW[2])
	s.disc(7.5, 7.5, 1.3, GLOW[1])
	s.set(7, 7, GLOW[0])
	for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
		rivet(s, x, y)
	return s
