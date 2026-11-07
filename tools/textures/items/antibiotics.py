"""Antibiotics icon: amber prescription pill bottle, white child-proof cap, green-cross label."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "director"

PALETTE = {
	".": CLEAR,
	"H": rgb("f4efe4"),  # cap / label highlight (warm white)
	"C": rgb("d3d1cf"),  # cap / label base
	"S": rgb("9a9fae"),  # cap / label shadow (cool grey)
	"1": rgb("ffcf7c"),  # amber highlight
	"2": rgb("e3963a"),  # amber light
	"3": rgb("b9661f"),  # amber base
	"4": rgb("743a24"),  # amber shadow (cooler, plum-brown)
	"G": rgb("4aa557"),  # green cross
	"g": rgb("2b6b3c"),  # green cross shadow
}

PIXELS = [
	"................",
	"....HHHHHHHC....",
	"...HCSCSCSCSS...",
	"...SSSSSSSSSS...",
	"....44444444....",
	"....21333334....",
	"....21333334....",
	"....HSSSHHCS....",
	"....HHHGGHCS....",
	"....HHGGGGCS....",
	"....HHGGGGCS....",
	"....HHHGGHCS....",
	"....CCCCCCSS....",
	"....21334334....",
	".....3444444....",
	"................",
]


def draw():
	"""Amber pill bottle with a ridged white cap and a white label bearing a green cross."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
