"""Rebar Spear icon: a length of ribbed rebar with a ground point and a duct-tape grip."""
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
	"..............KK",
	".............KWK",
	"............KWMK",
	"...........KWmK.",
	"..........KMmK..",
	".........KrRK...",
	"........KRrK....",
	".......KrRK.....",
	"......KRrK......",
	".....KTtK.......",
	"....KTtK........",
	"...KTtK.........",
	"..KrRK..........",
	".KRrK...........",
	".KK.............",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
