"""Crawler overlay: shirt torn off below the ribs, bare bone showing, knees scraped raw from dragging."""
from zombie_skin import *

OWNER = "director"
BONE = rgb("d8d2b8")
BONE_SHADE = rgb("a8a28a")
RAW = rgb("6a2a22")


def draw():
	s = skin()
	# shirt ripped away: bare skin on the lower torso, front and back
	rows(s, BODY_FRONT, 6, 11, SKIN_MID)
	rows(s, BODY_BACK, 7, 11, SKIN_MID)
	# ribs on the bare front
	for y in (7, 9):
		for x in (1, 2, 5, 6):
			at(s, BODY_FRONT, x, y, BONE)
		at(s, BODY_FRONT, 3, y, BONE_SHADE)
		at(s, BODY_FRONT, 4, y, BONE_SHADE)
	# ragged hem
	for x, d in ((0, 5), (1, 6), (2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 6)):
		at(s, BODY_FRONT, x, d, SHIRT_DEEP)
	# scraped knees and forearms
	rows(s, LEG_FRONT, 5, 7, RAW)
	spots(s, LEG_FRONT, 4, 3, [SKIN_DARK, RAW])
	rows(s, ARM_BOTTOM, 0, 3, RAW)
	spots(s, ARM_FRONT, 5, 7, [RAW, SKIN_DARK])
	# hollow eyes
	at(s, HEAD_FRONT, 2, 4, BLACK)
	at(s, HEAD_FRONT, 5, 4, BLACK)
	return s
