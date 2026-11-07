"""Focusing Lens icon: blue precision lens in a bevelled brass ring with a bright glint."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

KEY = {
	".": CLEAR,
	# brass ring: highlight, light, base, shadow (shadow hue-shifted cool)
	"H": rgb("f4dc95"),
	"L": rgb("d6a64a"),
	"B": rgb("a27530"),
	"S": rgb("5e4430"),
	# blue glass: deep, mid, light, caustic, glint
	"d": rgb("1c2a66"),
	"m": rgb("2e5cc0"),
	"l": rgb("4a96e6"),
	"c": rgb("92d6ff"),
	"w": rgb("f4fcff"),
	# steel mounting lugs and screws: light, base, shadow
	"k": rgb("cfd6dc"),
	"t": rgb("8a93a3"),
	"u": rgb("4b5164"),
}

MAP = [
	"................",
	"................",
	".....HHHLLB.....",
	"...HHHHLLLBBB...",
	"...HLBddddBkS...",
	"..HLBdwwmmmBBS..",
	"..HLdwwmmmmmBS..",
	".kHLdwmmmmmlBSt.",
	".uLLdmmmmmllBSu.",
	"..LBdmmmmllcLS..",
	"..BBBmmmlccLBS..",
	"...BkBmlccLBS...",
	"...BBBLLLLSSS...",
	".....BSSSSS.....",
	"................",
	"................",
]


def draw():
	"""Paint the pixel map, then add the vanilla-style dark outline."""
	s = Sprite()
	for y, row in enumerate(MAP):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
