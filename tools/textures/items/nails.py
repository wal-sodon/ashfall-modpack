"""Nails icon: a small handful of steel nails."""
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
}

PIXELS = [
	"................",
	"................",
	"................",
	"....KKK.........",
	"....KWK...KKK...",
	".....M....KWK...",
	".....M.....M....",
	".....m.....M....",
	".....m.KKK.m....",
	".....K.KWK.m....",
	"........M..K....",
	"........M.......",
	"........m.......",
	"........m.......",
	"........K.......",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
