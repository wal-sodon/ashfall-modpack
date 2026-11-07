"""The Choir overlay: violet crystal shards grown through the body. Clusters burst from the shoulders, spine and back
of the skull, veins of crystal run down the arms and chest, and the eyes are lit crystal. Drawn over the vanilla
zombie under the renderer's pale crystal tint (arms ChoirRenderer), so the body reads as glassy and the shards as the
brightest thing on it."""
from zombie_skin import *

OWNER = "arms"
C_LIGHT = rgb("ffd2ee")
C_BRIGHT = rgb("ee8ff0")
C_MID = rgb("cf4fe6")
C_BASE = rgb("9a3ad8")
C_DARK = rgb("6a2bb5")
C_DEEP = rgb("3b1c78")


def shard(s, region, dx, dy, height):
	"""A pointed shard growing up from (dx, dy): a dark root, a mid body and a bright tip, two pixels wide."""
	for i in range(height):
		c = C_DEEP if i == 0 else C_BRIGHT if i == height - 1 else C_MID if i % 2 else C_BASE
		at(s, region, dx, dy - i, c)
		if i < height - 1:
			at(s, region, dx + 1, dy - i, C_DARK)
	at(s, region, dx, dy - height + 1, C_LIGHT)


def vein(s, region, points):
	for i, (dx, dy) in enumerate(points):
		at(s, region, dx, dy, C_MID if i % 3 else C_BRIGHT)


def draw():
	s = skin()
	# lit crystal eyes and a crack of crystal across the brow
	at(s, HEAD_FRONT, 2, 4, C_LIGHT)
	at(s, HEAD_FRONT, 5, 4, C_LIGHT)
	at(s, HEAD_FRONT, 1, 4, C_MID)
	at(s, HEAD_FRONT, 6, 4, C_MID)
	vein(s, HEAD_FRONT, [(0, 2), (1, 2), (2, 1), (3, 2), (4, 1), (5, 2), (6, 2), (7, 1)])
	# shards out of the back and top of the skull
	shard(s, HEAD_BACK, 1, 7, 6)
	shard(s, HEAD_BACK, 4, 7, 7)
	shard(s, HEAD_TOP, 2, 6, 4)
	shard(s, HEAD_TOP, 5, 5, 3)
	spots(s, HEAD_LEFT, 6, 31, [C_MID, C_BASE, C_DARK])
	spots(s, HEAD_RIGHT, 6, 37, [C_MID, C_BASE, C_DARK])
	# a cluster bursting from the chest, veins spreading from it
	shard(s, BODY_FRONT, 3, 6, 4)
	shard(s, BODY_FRONT, 5, 7, 3)
	vein(s, BODY_FRONT, [(2, 7), (1, 8), (1, 9), (0, 10), (6, 8), (7, 9), (6, 10), (7, 11)])
	# the spine: a ridge of shards down the back
	for dy, hgt in ((4, 3), (7, 4), (10, 3)):
		shard(s, BODY_BACK, 3, dy, hgt)
	spots(s, BODY_LEFT, 5, 41, [C_MID, C_DARK])
	spots(s, BODY_RIGHT, 5, 43, [C_MID, C_DARK])
	# shoulders and arms: shards at the shoulder, veins down to crystal knuckles
	for region, seed in ((ARM_OUTER, 51), (ARM_FRONT, 53), (ARM_BACK, 57)):
		shard(s, region, 1, 4, 4)
		vein(s, region, [(2, 6), (1, 7), (2, 8), (1, 9), (2, 10)])
	rows(s, ARM_FRONT, 11, 11, C_BASE)
	fill(s, ARM_BOTTOM, C_DARK)
	at(s, ARM_BOTTOM, 1, 1, C_BRIGHT)
	# crystal creeping up from the feet
	for region, seed in ((LEG_FRONT, 61), (LEG_OUTER, 63), (LEG_BACK, 67)):
		rows(s, region, 11, 11, C_DARK)
		spots(s, (region[0], region[1] + 7, region[2], region[3] - 1), 4, seed, [C_MID, C_BASE])
	return s
