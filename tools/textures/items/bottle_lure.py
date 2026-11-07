"""Bottle Lure icon: a clear glass bottle stuffed with rattling scrap and stoppered with a rag."""
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
	"G": rgb("cfe6ea"),
	"g": rgb("8fb4bb"),
	"d": rgb("5d7f86"),
	"C": rgb("c8b89a"),
}

PIXELS = [
	"................",
	"......CC........",
	"......CC........",
	"......Gd........",
	"......Gd........",
	".....GGgd.......",
	"....GgggdK......",
	"....GMgmgd......",
	"....GgWgmd......",
	"....GmgMgd......",
	"....GgmgWd......",
	"....GMgmgd......",
	"....Ggggdd......",
	".....dddd.......",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
