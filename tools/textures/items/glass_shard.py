"""Glass Shard icon: two jagged pale-cyan window shards with bright edge glints, slightly see-through."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"W": rgb("ffffff"),  # glint
	"H": rgb("dff6f6"),  # highlight
	"L": rgb("b4e2e6"),  # light
	"B": rgb("86c3cc"),  # base
	"S": rgb("5e97a8"),  # shadow edge
}

PIXELS = [
	"................",
	"........W.......",
	".......WH.......",
	"......WHL.......",
	"......HLLB......",
	".....HLLBB......",
	".....HLBBS......",
	"....HLLBBS..W...",
	"....HLBBS..WH...",
	"...HLBBS..WHLB..",
	"...HLBS...HLBS..",
	"..HLBS...HLBS...",
	"..HBS....HBS....",
	"..SS.....SS.....",
	"................",
	"................",
]


def draw():
	"""A tall shard and a small one, glints on the lit edges, cool shadow on the broken edges."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			c = PALETTE[ch]
			if ch in "LB":
				c = (c[0], c[1], c[2], 210)
			s.set(x, y, c)
	s.outline(OUTLINE)
	return s
