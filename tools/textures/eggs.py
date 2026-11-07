"""Spawn egg icons for Ashfall's mobs: one egg silhouette, a two-tone body and spots per mob.

Vanilla 26.x spawn eggs are per-mob textures rather than tinted layers, so each Ashfall egg is drawn here from the
same egg shape with its own colours (base shading ramp plus spot colour), keeping the set consistent.
"""
from sprite import Sprite, OUTLINE

# Egg silhouette, 16x16: '#' body, 'h' highlight, 's' shade side.
SHAPE = [
	"................",
	"......####......",
	".....#hh###.....",
	"....#hh#####....",
	"....#h######....",
	"...#h#######s...",
	"...#h#######s...",
	"...#########s...",
	"..##########ss..",
	"..##########ss..",
	"..#########sss..",
	"..##########ss..",
	"...########ss...",
	"...#######sss...",
	".....#sssss.....",
	"................",
]


def egg(light, base, shade, spot, spots):
	"""The egg in base colours with highlight and shade, then the given spot pixels (x, y) in spot colour."""
	s = Sprite()
	for y, row in enumerate(SHAPE):
		for x, ch in enumerate(row):
			if ch == "#":
				s.set(x, y, base)
			elif ch == "h":
				s.set(x, y, light)
			elif ch == "s":
				s.set(x, y, shade)
	for x, y in spots:
		if s.px[y][x][3]:
			s.set(x, y, spot)
	s.outline(OUTLINE)
	return s
