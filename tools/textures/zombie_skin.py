"""Shared helpers for special-infected overlay skins (64x64, the vanilla zombie's box-UV layout).

The overlays are drawn ON TOP of the vanilla zombie texture by SpecialOverlayLayer, so they are mostly transparent
and only add features (pustules, acid, scars). Colours come from the vanilla zombie texture's own palette so the
additions sit naturally on it; nothing is copied from the vanilla image itself.

UV regions (x0, y0, x1, y1 inclusive) of the faces the overlays paint, named from the mob's point of view.
"""
from sprite import Sprite, rgb


# The vanilla zombie palette (skin greens, shirt cyan, trouser purple, shadow).
SKIN_DARK = rgb("385226")
SKIN_MID = rgb("3e692d")
SKIN = rgb("4e7b36")
SKIN_LIGHT = rgb("698756")
SKIN_PALE = rgb("71955b")
SHIRT = rgb("00afaf")
SHIRT_DARK = rgb("007f7f")
SHIRT_DEEP = rgb("005b5b")
TROUSER = rgb("463aa5")
TROUSER_DARK = rgb("302872")
SHADOW = rgb("282828")
BLACK = rgb("1a1a1a")
GREY = rgb("3f3f3f")

HEAD_FRONT = (8, 8, 15, 15)
HEAD_BACK = (24, 8, 31, 15)
HEAD_TOP = (8, 0, 15, 7)
HEAD_RIGHT = (0, 8, 7, 15)
HEAD_LEFT = (16, 8, 23, 15)
BODY_FRONT = (20, 20, 27, 31)
BODY_BACK = (32, 20, 39, 31)
BODY_RIGHT = (16, 20, 19, 31)
BODY_LEFT = (28, 20, 31, 31)
ARM_FRONT = (44, 20, 47, 31)
ARM_OUTER = (40, 20, 43, 31)
ARM_INNER = (48, 20, 51, 31)
ARM_BACK = (52, 20, 55, 31)
ARM_BOTTOM = (48, 16, 51, 19)
LEG_FRONT = (4, 20, 7, 31)
LEG_OUTER = (0, 20, 3, 31)
LEG_BACK = (12, 20, 15, 31)


def skin():
	return Sprite(64, 64)


def fill(s, region, c):
	x0, y0, x1, y1 = region
	s.rect(x0, y0, x1, y1, c)


def rows(s, region, first, last, c):
	"""Fill rows first..last (offsets from the region's top) across the region's width."""
	x0, y0, x1, _ = region
	s.rect(x0, y0 + first, x1, y0 + last, c)


def at(s, region, dx, dy, c):
	"""One pixel at an offset inside a region."""
	s.set(region[0] + dx, region[1] + dy, c)


def spots(s, region, count, seed, colours):
	"""Deterministic scatter of single pixels (same output every run), cycling through colours."""
	x0, y0, x1, y1 = region
	w, h = x1 - x0 + 1, y1 - y0 + 1
	state = seed * 2654435761 % 4294967296
	for i in range(count):
		state = (state * 1103515245 + 12345) % 2147483648
		x = x0 + state % w
		state = (state * 1103515245 + 12345) % 2147483648
		y = y0 + state % h
		s.set(x, y, colours[i % len(colours)])


def pustule(s, x, y, core, rim, shine):
	"""A 3x3 boil: rim cross, bright core, one highlight pixel."""
	for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
		s.set(x + dx, y + dy, rim)
	s.set(x, y, core)
	s.set(x - 1, y - 1, shine)
