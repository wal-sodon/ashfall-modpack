"""Optic icon: a short scavenged sight tube with a blue front lens, rail clamp and duct-tape wrap."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

KEY = {
	".": CLEAR,
	"H": rgb("c3c7cf"), "L": rgb("8f95a3"), "B": rgb("646a7a"), "S": rgb("424656"),   # gunmetal tube
	"t": rgb("c4c4bc"), "T": rgb("8e8f8c"),                                         # duct tape
	"g": rgb("92d6ff"), "m": rgb("2e5cc0"), "d": rgb("1c2a66"), "w": rgb("f4fcff"),  # lens glass
	"k": rgb("33353d"),                                                             # clamp
}

MAP = [
	"................",
	"................",
	"................",
	"..........HHH...",
	".........HwgmL..",
	"...HHHHHHLgmmdS.",
	"..HLLtTLLLmmddS.",
	"..LBBtTBBBdddSS.",
	"..BSStTSSSSSSS..",
	"...SSSSSSSSS....",
	"....k....k......",
	"...kkk..kkk.....",
	"................",
	"................",
	"................",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(MAP):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
