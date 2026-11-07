"""Arc Charge icon: a taped-up battery canister with two steel prongs and a pale blue arc jumping between them."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

# Lit from the top-left like the Battery: charcoal body, grey tape band, copper base, the stun effect's blues.
KEY = {
	".": CLEAR,
	# arc
	"W": rgb("e6f6ff"),  # hot core
	"b": rgb("7fc4ff"),  # glow
	# steel prongs and cap
	"H": rgb("e6e1d6"),
	"M": rgb("aeb1b8"),
	"m": rgb("7b7f8c"),
	# charcoal body
	"L": rgb("767079"),
	"k": rgb("4a4853"),
	"j": rgb("312f3a"),
	"d": rgb("24222d"),
	# faded yellow bolt on the label
	"y": rgb("c9a94a"),
	# duct tape band
	"T": rgb("b9bcc2"),
	"t": rgb("878a93"),
	# copper base
	"O": rgb("f6c98a"),
	"C": rgb("d98f4c"),
	"c": rgb("ad6630"),
	"B": rgb("6e3a2e"),
}

ART = [
	"................",
	"................",
	".....H....H.....",
	".....m....m.....",
	"....HMMMMMMm....",
	"....Lkkjjjjd....",
	"....Lkjjyjjd....",
	"....Lkjyyjjd....",
	"....TTTTTTtt....",
	"....TtTTTTtt....",
	"....Lkjyjjjd....",
	"....Lkkjjjjd....",
	"....OCCCCccB....",
	"....BBBBBBBB....",
	"................",
	"................",
]


# Drawn over the outline, so the arc glows against the sky instead of reading as a dark blob.
ARC = [
	"..b...bWWb......",
	".....W....W..b..",
	"................",
]


def draw():
	"""Upright canister: blue arc over two prongs, charcoal body with a bolt, tape band, copper base."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	for y, row in enumerate(ARC):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	return s
