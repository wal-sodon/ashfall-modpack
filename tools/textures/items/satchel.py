"""Satchel icon: a patched canvas shoulder bag with a strap loop, a flap, a taped seam and a buckle."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

KEY = {
	".": CLEAR,
	"r": rgb("6b4a2e"),  # leather-brown strap
	"q": rgb("4a3220"),  # strap shadow
	"h": rgb("c9b48a"),  # canvas highlight
	"l": rgb("a8925f"),  # light canvas
	"b": rgb("8a7546"),  # base canvas
	"d": rgb("6a5834"),  # canvas shadow
	"s": rgb("4c3e24"),  # deep shadow
	"t": rgb("9aa3a8"),  # duct tape patch
	"u": rgb("6e777d"),  # tape shadow
	"k": rgb("d8c25a"),  # brass buckle
}

MAP = [
	"................",
	".....rrrrrr.....",
	"....rq....qr....",
	"...rq......qr...",
	"...r........r...",
	"..hhhhhhhhhhhh..",
	"..hlllllllllld..",
	"..lbbbbkkbbbbd..",
	"..dddddkkddddd..",
	"..lbbbbbbbbbbd..",
	"..lbttbbbbbbbd..",
	"..lbuubbbbbbbd..",
	"..lbbbbbbbbbds..",
	"..lbbbbbbbbdds..",
	"...ssssssssss...",
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
