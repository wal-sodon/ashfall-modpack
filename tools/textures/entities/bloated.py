"""Bloated overlay: belly swollen out of the shirt, veined, and covered in green-yellow boils."""
from zombie_skin import *

OWNER = "director"
BOIL = rgb("b8cc3a")
BOIL_RIM = rgb("7a9426")
BOIL_SHINE = rgb("e4ec8a")
VEIN = rgb("2e4420")


def draw():
	s = skin()
	# stretched belly: skin bursting through the shirt front, sides and back
	rows(s, BODY_FRONT, 4, 11, SKIN_LIGHT)
	rows(s, BODY_RIGHT, 5, 11, SKIN_LIGHT)
	rows(s, BODY_LEFT, 5, 11, SKIN_LIGHT)
	rows(s, BODY_BACK, 6, 10, SKIN_LIGHT)
	for x, y in ((1, 5), (2, 6), (2, 7), (3, 8), (6, 6), (5, 7), (5, 8), (4, 9)):
		at(s, BODY_FRONT, x, y, VEIN)
	# boils
	x0, y0 = BODY_FRONT[0], BODY_FRONT[1]
	for dx, dy in ((2, 2), (5, 4), (2, 10), (6, 9)):
		pustule(s, x0 + dx, y0 + dy, BOIL, BOIL_RIM, BOIL_SHINE)
	x0, y0 = BODY_BACK[0], BODY_BACK[1]
	for dx, dy in ((2, 3), (5, 7), (3, 10)):
		pustule(s, x0 + dx, y0 + dy, BOIL, BOIL_RIM, BOIL_SHINE)
	for region in (ARM_FRONT, ARM_OUTER):
		pustule(s, region[0] + 1, region[1] + 3, BOIL, BOIL_RIM, BOIL_SHINE)
		pustule(s, region[0] + 2, region[1] + 8, BOIL, BOIL_RIM, BOIL_SHINE)
	# swollen cheek and neck
	pustule(s, HEAD_FRONT[0] + 6, HEAD_FRONT[1] + 5, BOIL, BOIL_RIM, BOIL_SHINE)
	pustule(s, HEAD_LEFT[0] + 3, HEAD_LEFT[1] + 6, BOIL, BOIL_RIM, BOIL_SHINE)
	pustule(s, HEAD_BACK[0] + 4, HEAD_BACK[1] + 6, BOIL, BOIL_RIM, BOIL_SHINE)
	return s
