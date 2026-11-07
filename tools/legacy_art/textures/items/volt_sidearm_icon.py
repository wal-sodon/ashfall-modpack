"""Volt Sidearm icon: chunky scavenged energy pistol in side profile, muzzle right, amber accents.

Steel emitter shroud with an amber bore and vent, gunmetal slide with an amber charge line, composite
frame, angled grip with a glowing battery window and a duct-tape wrap.
"""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

KEY = {
	".": CLEAR,
	# gunmetal slide
	"A": rgb("c3c7cf"), "B": rgb("8f95a3"), "C": rgb("646a7a"), "D": rgb("424656"),
	# steel shroud
	"S": rgb("e0dcd0"), "T": rgb("a6a8aa"), "U": rgb("5a5e68"),
	# composite frame / dark parts
	"F": rgb("6e6552"), "f": rgb("413b32"), "k": rgb("2a2c38"),
	# grip rubber
	"R": rgb("545459"), "r": rgb("323236"),
	# amber energy
	"a": rgb("fff4cc"), "b": rgb("ffb33c"), "c": rgb("d9821f"),
	# duct tape
	"t": rgb("9a9a92"),
}

ART = [
	"................",
	"................",
	"...kk......k....",
	"..ABBBBBBBSSSS..",
	"..BCCCCCCCTcTTb.",
	"..CbbbbbaCTcTTa.",
	"..DDDDDDDDUUUU..",
	"..fFFFFFFFFcbc..",
	"...tttt.T.U.....",
	"...RbaRUUUU.....",
	"..RbcR..........",
	"..RbcR..........",
	".rrrrr..........",
	".TTTTU..........",
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
