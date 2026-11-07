"""Strongbox front, locked: a recessed door with a brass combination dial, a lever handle and a keyhole plate."""
from sprite import Sprite, rgb
from _strongbox import body, ENAMEL
from _wood import IRON

OWNER = "salvage"
BRASS = [rgb("f0d27a"), rgb("c9a54a"), rgb("8f7230"), rgb("5a4520")]


def door(s):
	"""The door panel inset in the frame: shadow on the top-left of the recess, light on the bottom-right."""
	s.rect(2, 2, 13, 13, ENAMEL[1])
	s.hline(2, 13, 2, ENAMEL[3])
	s.vline(2, 2, 13, ENAMEL[3])
	s.hline(2, 13, 13, ENAMEL[0])
	s.vline(13, 2, 13, ENAMEL[0])
	# hinges on the left edge
	for y in (4, 10):
		s.rect(1, y, 2, y + 1, IRON[2])
		s.set(1, y, IRON[1])


def draw():
	s = Sprite()
	body(s, salt=227)
	door(s)
	# combination dial
	s.rect(5, 4, 8, 7, BRASS[2])
	s.rect(5, 5, 8, 6, BRASS[1])
	s.rect(6, 4, 7, 7, BRASS[1])
	s.set(6, 5, BRASS[0])
	s.set(7, 6, BRASS[3])
	s.set(6, 4, IRON[4])  # the index mark
	# lever handle
	s.hline(9, 11, 6, IRON[1])
	s.hline(9, 11, 7, IRON[3])
	s.rect(10, 5, 10, 8, IRON[2])
	# keyhole plate
	s.rect(6, 9, 8, 11, BRASS[2])
	s.set(7, 9, IRON[4])
	s.set(7, 10, IRON[4])
	s.set(6, 9, BRASS[0])
	return s
