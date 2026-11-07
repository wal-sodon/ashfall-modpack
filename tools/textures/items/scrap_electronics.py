"""Scrap electronics icon: a snapped-off green circuit board with a chip, traces and a cut wire."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Hand-placed pixel map; light from the top-left, board tilted slightly with a jagged broken edge.
ART = [
	"................",
	"................",
	"..hgggggggg.....",
	"..gGGGGGGGGg....",
	"..gGyyyyGGGGg...",
	"..gGyCCCyGtGGg..",
	"..gGyCcCyGtGGGg.",
	"..gGyCCCyGtttGg.",
	"..gGGyyyGGGGtGd.",
	"..gGtttGGsGGtGd.",
	"..gGGGtGGGGGGd..",
	"..dGGGtGGsGGd...",
	"...dGGtGGGd.....",
	"....dGtGd..rr...",
	".....dd...r.wW..",
	"...........r....",
]

KEY = {
	".": CLEAR,
	# board: highlight, light, base, shadow (cooler shadows)
	"h": rgb("b7e08a"),
	"g": rgb("6fae4c"),
	"G": rgb("3f7d36"),
	"d": rgb("234a2e"),
	# copper traces and solder pads
	"t": rgb("d0a24c"),
	"s": rgb("e8e2cf"),
	# chip: pins and body
	"y": rgb("b8b6a8"),
	"C": rgb("2f3038"),
	"c": rgb("595b68"),
	# cut wire: red insulation, bare copper end
	"r": rgb("b0413a"),
	"w": rgb("e0a36a"),
	"W": rgb("f6d8a8"),
}


def draw():
	"""Broken circuit board: chip with pins, copper traces, solder pads, and a dangling cut wire."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
