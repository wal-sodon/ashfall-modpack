"""Nail Launcher icon: a rebuilt pneumatic nail gun, orange body, black grip and a nail magazine."""
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
	"O": rgb("e07b2a"),
	"o": rgb("a8541c"),
	"B": rgb("30333a"),
	"b": rgb("1d1f24"),
	"Y": rgb("e8c547"),
}

PIXELS = [
	"................",
	"................",
	"................",
	"..KKKKKKKKKKK...",
	".KOOOOOOOOOoMK..",
	".KOoooooooooMWK.",
	".KKKKKBBKKKKMK..",
	"......KBbKMMK...",
	".....KBbK.KYK...",
	".....KBbK.KYK...",
	"....KBbK..KYK...",
	"....KBbK..KKK...",
	"....KKKK........",
	"................",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
