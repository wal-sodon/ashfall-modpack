"""Spitter overlay: acid drooling from a burnt mouth down the chin and chest, splash burns on the hands."""
from zombie_skin import *

OWNER = "director"
ACID = rgb("a8e030")
ACID_DARK = rgb("6a9a1c")
ACID_GLOW = rgb("dcf880")
BURN = rgb("2a3a14")


def draw():
	s = skin()
	# burnt mouth and dribble
	rows(s, HEAD_FRONT, 5, 6, BURN)
	for x in (2, 3, 4, 5):
		at(s, HEAD_FRONT, x, 6, ACID)
	at(s, HEAD_FRONT, 3, 7, ACID)
	at(s, HEAD_FRONT, 4, 7, ACID_GLOW)
	# drips running down the shirt
	for x, length in ((3, 6), (4, 9), (6, 4), (1, 3)):
		for y in range(length):
			at(s, BODY_FRONT, x, y, ACID if y % 3 else ACID_GLOW)
		at(s, BODY_FRONT, x, length, ACID_DARK)
	# swollen acid sacs on the neck
	at(s, HEAD_RIGHT, 5, 6, ACID)
	at(s, HEAD_RIGHT, 6, 7, ACID_DARK)
	at(s, HEAD_LEFT, 2, 6, ACID)
	at(s, HEAD_LEFT, 1, 7, ACID_DARK)
	# splash burns on the forearms
	spots(s, (44, 27, 47, 31), 5, 5, [ACID_DARK, BURN, ACID])
	spots(s, (40, 26, 43, 31), 4, 9, [BURN, ACID_DARK])
	return s
