"""Reinforced Planks: four oak planks held by two riveted iron straps, a nugget rivet on each plank."""
from sprite import Sprite
from _wood import OAK, IRON, plank_rows, rivet

OWNER = "director"


def draw():
	s = Sprite()
	plank_rows(s, OAK, [(0, 3), (4, 7), (8, 11), (12, 15)], salt=11)
	for x0 in (2, 12):
		s.vline(x0, 0, 15, IRON[2])
		s.vline(x0 + 1, 0, 15, IRON[3])
		s.vline(x0 - 1, 0, 15, IRON[4])  # strap casts a shadow on the wood to its left
	for y in (1, 5, 9, 13):
		rivet(s, 2, y)
		rivet(s, 12, y)
	return s
