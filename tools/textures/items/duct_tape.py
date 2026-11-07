"""Duct Tape icon: silver-grey roll at a slight angle, cardboard core, torn tail strip."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"T": rgb("e9e6dc"),  # tape highlight (warm)
	"L": rgb("c3c5c8"),  # tape light
	"B": rgb("989ca5"),  # tape base
	"S": rgb("858996"),  # tape side base
	"D": rgb("6a6f7e"),  # tape shadow (cool)
	"d": rgb("4a4e60"),  # tape deep shadow
	"K": rgb("d8b47a"),  # cardboard light
	"k": rgb("a9804c"),  # cardboard base
	"3": rgb("6b4b36"),  # cardboard inner wall (shadow)
}

PIXELS = [
	"................",
	"....TTTTTT......",
	"..TTLLLLLLLL....",
	".TLLLkkkkLLLB...",
	".TLLk3333kLLB...",
	".TLLk....KLLB...",
	".DLLLKKKKLLBD...",
	".BDDLLLLLLDDd...",
	".BTLDDDDDDSDdTL.",
	".BTLSSSSSSSDdLB.",
	".BTLSSSSSSSDdLB.",
	".BTLSSSSSSDDdLD.",
	"..TLSSSSSDDd.LD.",
	"....DDDDDd...L..",
	"..............D.",
	"................",
]


def draw():
	"""Silver duct tape roll: lit top face with a cardboard core, shaded side, torn tail."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
