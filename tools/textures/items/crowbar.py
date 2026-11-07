"""Crowbar icon: a red-painted steel pry bar, hooked claw at the top, flat chisel end at the bottom."""
from sprite import Sprite, rgb, CLEAR

OWNER = "arms"

PALETTE = {
	".": CLEAR,
	"K": rgb("2a1c1c"),
	"R": rgb("c8433a"),
	"r": rgb("8e2a26"),
	"H": rgb("e8776b"),
	"M": rgb("9aa0a6"),
	"m": rgb("62676e"),
}

PIXELS = [
	"................",
	"...........KKK..",
	"..........KHRRK.",
	"..........KRKrRK",
	"...........K.KrK",
	"............KRrK",
	"...........KRrK.",
	"..........KRrK..",
	".........KRrK...",
	"........KRrK....",
	".......KRrK.....",
	"......KRrK......",
	".....KRrK.......",
	"....KMmK........",
	"...KMmK.........",
	"...KKK..........",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
