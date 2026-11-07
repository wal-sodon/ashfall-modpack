"""Field Radio icon: a scuffed olive handheld transceiver with a whip antenna, speaker grille and amber dial."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Upright handheld, light from the top-left; antenna on the left shoulder, knob on the right.
ART = [
	"...a............",
	"...A............",
	"...a............",
	"...A......kk....",
	"...a......Kk....",
	"..OOOOOOOOOOo...",
	"..OLLLLLLLLLd...",
	"..OLsssssssLd...",
	"..OLsSsSsSsLd...",
	"..OLsssssssLd...",
	"..OLLLLLLLLLd...",
	"..OLyYyLbbbLd...",
	"..OLLLLLLLLLd...",
	"..OLLLLLLLLLd...",
	"..oddddddddddd..",
	"................",
]

KEY = {
	".": CLEAR,
	# olive drab body: highlight, light, base, shadow
	"O": rgb("9aa35a"),
	"L": rgb("6f7a3c"),
	"o": rgb("525a2c"),
	"d": rgb("343a20"),
	# speaker grille
	"s": rgb("2a2d24"),
	"S": rgb("4a4f40"),
	# antenna and knob (steel)
	"a": rgb("7b7f8c"),
	"A": rgb("c4c7cf"),
	"k": rgb("4c4f62"),
	"K": rgb("aeb1b8"),
	# amber tuning dial and its readout, buttons
	"y": rgb("c98a2c"),
	"Y": rgb("ffd27a"),
	"b": rgb("23251c"),
}


def draw():
	"""Olive handheld radio: whip antenna, knob, speaker grille, amber dial and a button row."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
