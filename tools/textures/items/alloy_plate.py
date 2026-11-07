"""Alloy Plate icon: a brushed grey-blue metal plate seen at an angle, corner rivets, a bevelled lit edge."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Lit from the top-left: highlights lean warm, shadows lean cool.
PALETTE = {
	".": CLEAR,
	"H": rgb("e8ecef"),  # bevel highlight
	"L": rgb("b9c3cf"),  # face light
	"B": rgb("8f9cad"),  # face base
	"b": rgb("7d8a9c"),  # brushed streak
	"S": rgb("5f6a80"),  # side shadow
	"d": rgb("424a62"),  # deep shadow
	"R": rgb("f2efe6"),  # rivet head
	"r": rgb("4b5368"),  # rivet shadow
}

PIXELS = [
	"................",
	"................",
	"................",
	"....HHHHHHHHHH..",
	"...HRrLLLLLLRrS.",
	"..HLLLLLLLLLLLS.",
	"..HBbbBBBbbBBBS.",
	".HLBBBBBBBBBBSd.",
	".HBbbBBBbbBBBSd.",
	".HBBBBBBBBBBBSd.",
	"HBbbBBBbbBBBSd..",
	"HRrBBBBBBRrBSd..",
	"HSSSSSSSSSSSd...",
	".ddddddddddd....",
	"................",
	"................",
]


def draw():
	"""Grey-blue alloy plate: lit bevel on top, brushed face, riveted corners, shadowed side."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
