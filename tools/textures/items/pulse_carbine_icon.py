"""Pulse Carbine icon: compact police-parts carbine, side view, muzzle right.

Navy polymer lower and raked grip, gunmetal upper with a cyan (#3cc8ff) cell window, vented barrel
shroud glowing cyan, tac light under the shroud, cyan emitter muzzle and a skeletal folding stock.
"""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

KEY = {
	"1": rgb("b4bac4"),  # metal highlight
	"2": rgb("7c8492"),  # metal light
	"3": rgb("565d6c"),  # metal base
	"4": rgb("3a3f50"),  # metal shadow
	"p": rgb("5a6890"),  # navy polymer light
	"q": rgb("3a4566"),  # navy polymer base
	"r": rgb("272e48"),  # navy polymer shadow
	"a": rgb("eafcff"),  # pulse core
	"b": rgb("3cc8ff"),  # pulse cyan
	"c": rgb("1c8ed6"),  # pulse deep
	"w": rgb("c9cfdc"),  # police stencil
	"L": rgb("fff6d8"),  # tac light lens
}

ART = [
	"................",
	"................",
	"................",
	"......4.....4...",
	".p222212121222c.",
	".p...32cab3b3ba.",
	".p...4wwqqqc4cc.",
	".p..3.rrrrr.rL..",
	".p.3...qr1qq....",
	".p3....qr4qb....",
	"......qr..33....",
	"......qr........",
	"......rr........",
	"................",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch in KEY:
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
