"""Reinforced Door icon: a narrow oak door in an alloy frame, vision slit up top, kick plate below."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "director"

PALETTE = {
	".": CLEAR,
	"o": OUTLINE,
	"A": rgb("d9dde3"),  # alloy highlight
	"a": rgb("aab0ba"),  # alloy
	"s": rgb("7f8794"),  # alloy shadow
	"W": rgb("c9a26a"),  # oak light
	"w": rgb("a8834e"),  # oak
	"d": rgb("6c4f2e"),  # board seam
	"g": rgb("231c1a"),  # slit
}

PIXELS = [
	"....oooooooo....",
	"....oAaaaaaso...",
	"....oaWwdWwso...",
	"....oagagagso...",
	"....oaggggaso...",
	"....oaWwdWwso...",
	"....oAaaaaaso...",
	"....oaWwdWwso...",
	"....oaWwdWAso...",
	"....oaWwdWsso...",
	"....oaWwdWwso...",
	"....oAaaaaaso...",
	"....oaWwdWwso...",
	"....oasssssso...",
	"....oaAsAsAso...",
	"....oooooooo....",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, PALETTE[ch])
	return s
