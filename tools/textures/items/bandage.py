"""Bandage icon: a rolled off-white cloth bandage with a loose, slightly stained tail."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "director"

# Off-white cloth ramp: warm highlights, cool grey-blue shadows (hue-shifted).
KEY = {
	".": CLEAR,
	"H": rgb("f7f1e1"),  # highlight
	"L": rgb("e2dccb"),  # light
	"B": rgb("c4bfb2"),  # base
	"S": rgb("9a99a0"),  # shadow
	"D": rgb("6f6f7c"),  # deep shadow / spiral gaps
	"K": rgb("45434f"),  # hollow core
	"t": rgb("a9846a"),  # old stain, light
	"T": rgb("856453"),  # old stain, dark
}

ART = [
	"................",
	"....HHHHLL......",
	"...HLLLLBLLL....",
	"..HLSSSSLBLLL...",
	".HLSLLLSSBBLL...",
	".HLSLDDLSBBBB...",
	".HSLDKKBSDBBBS..",
	".HSLBKKBSDBBBS..",
	".LSLBDDBSDBBSS..",
	".LBSBBBSDBSSSS..",
	"..LtSSSDBSDDD...",
	"...BtBBBSDHHHL..",
	"....SSSDDLLLtLH.",
	"........SBBBTBS.",
	"................",
	"................",
]


def draw():
	"""Pixel map of the roll (end face left, cylinder body right) with the tail hanging off it."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
