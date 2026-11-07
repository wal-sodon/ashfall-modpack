"""Burnt-out Torch: the torch's stick with its head burnt down to ash and the wood charred below it.

Only the torch strip is drawn (columns 7-8, rows 6-15), where the vanilla torch models read their faces from; the
rest stays clear. Rows 6-7 are also the top face: grey ash over a black crust. The lit column (x=7) is on the
left, as everywhere in the set.
"""
from sprite import Sprite, rgb
from _wood import OAK, h

OWNER = "salvage"

ASH = [rgb("8a8580"), rgb("5e5a58")]
CHAR = [rgb("3a302c"), rgb("261f1d"), rgb("17120f")]
SCORCH = [rgb("5a4030"), rgb("44301f")]


def draw():
	s = Sprite()
	for y in range(6, 16):
		for x in (7, 8):
			lit = x == 7
			if y <= 7:
				# ash head: grey flecks on a black crust
				c = ASH[0] if h(x, y, salt=41) > 0.5 else (CHAR[1] if lit else CHAR[2])
				if not lit and y == 7:
					c = ASH[1]
			elif y <= 9:
				c = CHAR[0] if lit else CHAR[1]
			elif y <= 11:
				# scorch line where the burn stopped, with a few charred flecks running down
				c = SCORCH[0] if lit else SCORCH[1]
				if h(x, y, salt=43) > 0.7:
					c = CHAR[0]
			else:
				# the stick itself, darker than a fresh torch's
				c = OAK[2] if lit else OAK[3]
				if y == 15:
					c = OAK[3] if lit else OAK[4]
			s.set(x, y, c)
	return s
