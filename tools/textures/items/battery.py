"""Battery icon: a mundane, scuffed 9-volt block with snap terminals, charcoal body and copper band."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Three material ramps, lit from the top-left: shadows lean cool/plum, highlights lean warm.
KEY = {
	".": CLEAR,
	# copper top
	"O": rgb("f6c98a"),  # highlight
	"C": rgb("d98f4c"),  # light
	"c": rgb("ad6630"),  # base
	"b": rgb("6e3a2e"),  # shadow
	# charcoal body
	"L": rgb("767079"),  # highlight
	"k": rgb("4a4853"),  # light
	"j": rgb("312f3a"),  # base
	"d": rgb("24222d"),  # shadow
	# steel ends (negative cap and + nub)
	"H": rgb("e6e1d6"),  # highlight
	"M": rgb("aeb1b8"),  # light
	"m": rgb("7b7f8c"),  # base
	"s": rgb("4c4f62"),  # shadow
	# faded yellow bolt on the label
	"y": rgb("c9a94a"),
}

ART = [
	"................",
	"................",
	"....HMm..HM.....",
	"....msm..Ms.....",
	"...HMMMMMMMMm...",
	"...Lkkkkkkkjd...",
	"...Lkjjjjyjjd...",
	"...Lkjjjyyjjd...",
	"...Lkjjjyjkjd...",
	"...OCCCCCCCcb...",
	"...Ccccccccbb...",
	"...Ccccccccbb...",
	"...Ccccccccbb...",
	"...bbbbbbbbbb...",
	"................",
	"................",
]


def draw():
	"""Upright 9V block: steel snap terminals and top plate, charcoal label with bolt, copper band."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
