"""Strongbox front, unlocked: the same door swung ajar, a dark gap down the right edge and the lever dropped."""
from sprite import Sprite, rgb
from _strongbox import body, ENAMEL
from _wood import IRON
from strongbox_front import BRASS, door

OWNER = "salvage"
GAP = rgb("141a16")


def draw():
	s = Sprite()
	body(s, salt=227)
	door(s)
	# the door stands off the frame: black gap on the latch side
	s.vline(12, 2, 13, GAP)
	s.vline(13, 2, 13, GAP)
	s.vline(11, 2, 13, ENAMEL[0])
	# dial, turned
	s.rect(5, 4, 8, 7, BRASS[2])
	s.rect(5, 5, 8, 6, BRASS[1])
	s.rect(6, 4, 7, 7, BRASS[1])
	s.set(7, 5, BRASS[0])
	s.set(8, 5, IRON[4])
	# lever hanging down
	s.rect(9, 6, 9, 9, IRON[2])
	s.set(9, 6, IRON[1])
	# keyhole plate
	s.rect(6, 9, 8, 11, BRASS[2])
	s.set(7, 9, IRON[4])
	s.set(7, 10, IRON[4])
	return s
