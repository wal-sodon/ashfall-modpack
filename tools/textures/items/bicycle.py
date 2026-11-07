"""Bicycle icon: a side view of a red city bike with black tyres."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"T": rgb("26262b"),  # tyre
	"t": rgb("8b8f9a"),  # spoke / hub
	"R": rgb("c0483e"),  # frame light
	"r": rgb("8a2f2c"),  # frame shadow
	"S": rgb("6e4a32"),  # saddle
	"G": rgb("b9bcc2"),  # bars
}

PIXELS = [
	"................",
	"................",
	"................",
	"...SSS......GG..",
	"....R......G....",
	"....RRRRRRRr....",
	"....R.r...r.....",
	"..TTTT.r.rTTTT..",
	".T..R.T.rT.t..T.",
	"T...Rt.Tr.T.t..T",
	"T..tttTtt.T.tt.T",
	"T...t..TT.T.t..T",
	".T.t.tT...T.t.T.",
	"..TTTT.....TTTT.",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
