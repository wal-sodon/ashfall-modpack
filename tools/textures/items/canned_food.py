"""Canned food icon: a dented tin of beans with a faded label."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

# Hand-placed pixel map; light from the top-left, cylinder shading left to right.
ART = [
	"................",
	"....HRRRRRRR....",
	"...RmmMMMMMMm...",
	"...HRRRRRRMmd...",
	"...RMMMMMMMmd...",
	"...lLLLLLLLsS...",
	"...lLLLLLLLsS...",
	"...lcCCCCCksS...",
	"...lcCBbCCkSS...",
	"...lcBbBbCKS....",
	"...lcCCCCCklS...",
	"...lLLLLLLLsS...",
	"...RMLLLLLLsS...",
	"...RMMMMMMMmd...",
	"....mmmmmmmd....",
	"................",
]

KEY = {
	".": CLEAR,
	# tin metal: highlight, light, base, shadow, dark (cool shadows, warm highlights)
	"H": rgb("f0eadb"),
	"R": rgb("cfcabd"),
	"M": rgb("9c9a96"),
	"m": rgb("6f7180"),
	"d": rgb("4a4d5e"),
	# faded red label band
	"l": rgb("c27a62"),
	"L": rgb("a2503f"),
	"s": rgb("7c3a38"),
	"S": rgb("572b33"),
	# sun-bleached cream label panel
	"c": rgb("e2d6b0"),
	"C": rgb("cdbd92"),
	"k": rgb("a3946f"),
	"K": rgb("7a6e58"),
	# beans
	"B": rgb("a0603a"),
	"b": rgb("6e3e2c"),
}


def draw():
	"""Dented bean tin: metal lid and rims, faded red-and-cream label, dent on the right."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
