"""Scrap Machete icon: a long blade cut from sheet scrap, a glass-sharpened edge and a cloth-wrapped grip."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

PALETTE = {
	".": CLEAR,
	"K": rgb("2a2d33"),
	"M": rgb("8a9099"),
	"m": rgb("5f656e"),
	"W": rgb("c4cad2"),
	"R": rgb("a0522d"),
	"r": rgb("6e3a22"),
	"G": rgb("d6eef0"),
	"C": rgb("c8b89a"),
	"c": rgb("8f7f62"),
}

PIXELS = [
	"................",
	".............KK.",
	"............KGWK",
	"...........KGMK.",
	"..........KGMK..",
	".........KGMmK..",
	"........KGMmK...",
	".......KGMmK....",
	"......KGMmK.....",
	".....KWMmK......",
	"....KKmKK.......",
	"...KCcK.........",
	"..KCcK..........",
	".KCcK...........",
	".KKK............",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
