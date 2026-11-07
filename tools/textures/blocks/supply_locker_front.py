"""Supply Locker front: two narrow doors with vent slots, a hasp and padlock in the middle, a stencilled stripe."""
from sprite import Sprite, rgb
from _locker import body, PAINT
from _wood import IRON

OWNER = "arms"
BRASS = [rgb("f0d27a"), rgb("c9a54a"), rgb("8f7230")]
STENCIL = rgb("d8d2b8")


def draw():
	s = Sprite()
	body(s, salt=311)
	# the seam between the two doors
	s.vline(7, 1, 14, PAINT[3])
	s.vline(8, 1, 14, PAINT[0])
	# vent slots near the top of each door
	for x0 in (2, 10):
		for y in (2, 4, 6):
			s.hline(x0, x0 + 3, y, PAINT[3])
			s.hline(x0, x0 + 3, y + 1, PAINT[0])
	# hasp and padlock
	s.rect(6, 8, 9, 9, IRON[2])
	s.set(6, 8, IRON[1])
	s.rect(7, 10, 8, 12, BRASS[1])
	s.set(7, 10, BRASS[0])
	s.set(8, 12, BRASS[2])
	s.set(7, 11, IRON[4])
	# a pale stencilled stripe low on each door
	for x0 in (2, 10):
		s.hline(x0, x0 + 3, 13, STENCIL)
	return s
