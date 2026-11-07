"""Brute overlay: shirt burst open over a scarred chest, bruised knuckles, stitched scars on the arms and face."""
from zombie_skin import *

OWNER = "director"
SCAR = rgb("6a2a22")
STITCH = rgb("c8c0a0")
BRUISE = rgb("4a3448")


def draw():
	s = skin()
	# shirt burst open down the middle
	for y in range(12):
		half = 1 if y < 3 else 2 if y < 8 else 3
		for x in range(4 - half, 4 + half):
			at(s, BODY_FRONT, x, y, SKIN_MID)
	at(s, BODY_FRONT, 3, 5, SKIN_DARK)
	at(s, BODY_FRONT, 4, 5, SKIN_DARK)
	# chest scar with stitches
	for i in range(5):
		at(s, BODY_FRONT, 2 + i, 3 + i, SCAR)
		if i % 2 == 0:
			at(s, BODY_FRONT, 2 + i, 2 + i, STITCH)
	# arm scars
	for region in (ARM_FRONT, ARM_OUTER, ARM_BACK):
		for i in range(4):
			at(s, region, i, 2 + i, SCAR)
		at(s, region, 1, 2, STITCH)
		at(s, region, 3, 4, STITCH)
	# bruised fists
	fill(s, ARM_BOTTOM, BRUISE)
	rows(s, ARM_FRONT, 10, 11, BRUISE)
	rows(s, ARM_OUTER, 10, 11, BRUISE)
	# scar across the face and a heavy brow
	for i in range(5):
		at(s, HEAD_FRONT, 1 + i, 1 + i, SCAR)
	rows(s, HEAD_FRONT, 2, 2, SKIN_DARK)
	at(s, HEAD_FRONT, 2, 4, rgb("b02020"))
	at(s, HEAD_FRONT, 5, 4, rgb("b02020"))
	return s
