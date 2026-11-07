"""Lancer Longrifle icon: a very long, slender rifle laid flat (stock left, muzzle right) with violet
focusing rings on the barrel, a mounted scope, a violet power window and a skeletal composite stock."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

KEY = {
	".": CLEAR,
	"1": rgb("c4ccda"),  # gunmetal highlight (warm)
	"2": rgb("8a93a6"),  # gunmetal light
	"3": rgb("5a6274"),  # gunmetal base
	"4": rgb("363b4a"),  # gunmetal shadow (cool)
	"c": rgb("a59cae"),  # composite light
	"C": rgb("7a7186"),  # composite base
	"D": rgb("4a4356"),  # composite shadow
	"r": rgb("2c2a33"),  # rubber
	"w": rgb("f4e6ff"),  # violet core
	"v": rgb("d79bff"),  # violet bright
	"V": rgb("b45aff"),  # violet base (bolt colour)
	"u": rgb("6e2fb0"),  # violet deep
	"L": rgb("8fd8ef"),  # scope lens
}

ART = [
	"................",
	"................",
	"................",
	"................",
	".....31112L.....",
	".....43334L.....",
	"......4..4.v.v..",
	".ccc112CC11w1w12",
	".rCC3wVDD44V4V43",
	".rDD434DD4.u.u.4",
	".rDD.r4.........",
	"....rr..........",
	"....r...........",
	"................",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
