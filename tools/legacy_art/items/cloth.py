"""Cloth icon: a crumpled, torn rag of faded denim with a folded corner and frayed hem."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

KEY = {
	".": CLEAR,
	"h": rgb("b9c2c4"),  # warm-grey highlight on lit folds
	"l": rgb("8597ad"),  # light denim
	"b": rgb("5f7290"),  # base denim (faded)
	"d": rgb("46566f"),  # crease / shadow
	"s": rgb("2f3a52"),  # deep cool shadow
	"e": rgb("a7b2bd"),  # folded-over underside catching light
	"f": rgb("7c8aa0"),  # underside shading
	"m": rgb("6a6a60"),  # grime stain
}

MAP = [
	"................",
	"................",
	"....hh.....h....",
	"...hllh..hhlh...",
	"..hllbllhllbbd..",
	"..hlbbbllbbbbd..",
	"..lbbmbbbbdbbd..",
	"..lbmmbbbdbbbd..",
	"...lbbbbdblbd...",
	"..hlbbbbdbbbbd..",
	"..eebbbdbbbbbd..",
	"..eefdbbbbmbbd..",
	"..effdbbbbbbsd..",
	"...fsbdsbbdbs...",
	"....s..s.d..s...",
	"................",
]


def draw():
	"""Hand-placed pixel map, then the standard dark outline."""
	s = Sprite()
	for y, row in enumerate(MAP):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
