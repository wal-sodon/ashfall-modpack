"""Wire Spool icon: a small wooden spool wound with red-orange insulated wire, a loose tail end."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

PALETTE = {
	".": CLEAR,
	# wooden flanges
	"W": rgb("e3c08a"),  # wood highlight
	"w": rgb("b98a52"),  # wood base
	"v": rgb("7a5536"),  # wood shadow
	"h": rgb("4e3426"),  # hub hole
	# wire windings
	"O": rgb("ffb27a"),  # wire highlight
	"C": rgb("e2672f"),  # wire light
	"c": rgb("b24426"),  # wire base
	"x": rgb("7a2a22"),  # wire shadow
	# bare copper tip
	"T": rgb("f6c98a"),
}

PIXELS = [
	"................",
	"................",
	"...WWWWWWWWW....",
	"..WwwwhhwwwwW...",
	"..vvvvvvvvvvv...",
	"...OCCCCCCCx....",
	"...CcCcCcCcx....",
	"...OCCCCCCCx....",
	"...CcCcCcCcx....",
	"...OCCCCCCCx....",
	"...CcCcCcCcxCC..",
	"..WwwwwwwwwwWxcT",
	"..vvvvvvvvvvv...",
	"................",
	"................",
	"................",
]


def draw():
	"""Wooden spool flanges top and bottom, banded red-orange windings, a loose copper-tipped tail."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
