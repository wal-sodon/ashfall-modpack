"""Scrap Metal icon: a bent, rusty sheet-metal offcut with a torn edge, bolt hole and rust patches."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"H": rgb("dcd8cc"),  # steel highlight (warm)
	"L": rgb("aeb0b3"),  # steel light
	"B": rgb("82868f"),  # steel base
	"S": rgb("5a5f70"),  # steel shadow (cool)
	"D": rgb("363a4a"),  # bolt hole / crease depth
	"1": rgb("d08a48"),  # rust highlight
	"2": rgb("a65a2e"),  # rust base
	"3": rgb("6e3628"),  # rust shadow (plum-brown)
}

PIXELS = [
	"................",
	".....HH.........",
	"....HLLHH.......",
	"...HLLLLLHH.....",
	"..HL..LLLLLHH...",
	".HLL..HLLLLLLH..",
	".HLLLHHLLL12LLH.",
	"..HLLLLLB1223BH.",
	"...BLLLBBB23HSS.",
	"....BLLBBBBHSBS.",
	".....BB12BHSBBS.",
	"......B23HSB23S.",
	".......BHSBBS...",
	"........S.SS....",
	"................",
	"................",
]


def draw():
	"""Bent steel offcut: lit upper face, creased fold, shaded lower face, bolt hole and rust."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
