"""Screamer overlay: jaw hanging wide open, pale staring eyes, a torn throat and a gaunt chest."""
from zombie_skin import *

OWNER = "director"
MOUTH = rgb("5a1414")
TOOTH = rgb("cfc8a8")
EYE = rgb("e8eedc")
BONE = rgb("c8c2a8")


def draw():
	s = skin()
	# wide open mouth reaching down off the chin
	rows(s, HEAD_FRONT, 5, 7, BLACK)
	for x in range(2, 6):
		at(s, HEAD_FRONT, x, 6, MOUTH)
	for x in (1, 3, 4, 6):
		at(s, HEAD_FRONT, x, 5, TOOTH)
	at(s, HEAD_FRONT, 0, 5, SKIN_DARK)
	at(s, HEAD_FRONT, 7, 5, SKIN_DARK)
	# pale staring eyes
	at(s, HEAD_FRONT, 1, 3, EYE)
	at(s, HEAD_FRONT, 6, 3, EYE)
	at(s, HEAD_FRONT, 2, 3, BLACK)
	at(s, HEAD_FRONT, 5, 3, BLACK)
	# torn throat on the top of the shirt
	rows(s, BODY_FRONT, 0, 1, MOUTH)
	at(s, BODY_FRONT, 3, 2, MOUTH)
	at(s, BODY_FRONT, 4, 2, MOUTH)
	# gaunt: collarbones and ribs through a shredded shirt
	for x in (1, 2, 5, 6):
		at(s, BODY_FRONT, x, 3, BONE)
	for y in (5, 7, 9):
		for x in (1, 2, 5, 6):
			at(s, BODY_FRONT, x, y, BONE)
		for x in (0, 3, 4, 7):
			at(s, BODY_FRONT, x, y, SKIN_DARK)
	spots(s, BODY_BACK, 8, 11, [SKIN_DARK, SHIRT_DEEP])
	return s
