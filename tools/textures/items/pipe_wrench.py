"""Pipe Wrench icon: a rusty steel wrench with an adjustable jaw and a taped grip."""
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
	"T": rgb("9aa0a6"),
	"t": rgb("6b7178"),
}

PIXELS = [
	"................",
	"..........KKK...",
	".........KWMMK..",
	"........KWMKK...",
	"........KMmK.KK.",
	".........KmmKMK.",
	"........KmMmmK..",
	".......KmMK.....",
	"......KmMK......",
	".....KRrK.......",
	"....KTtK........",
	"...KTtK.........",
	"..KRrK..........",
	".KTtK...........",
	".KKK............",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
