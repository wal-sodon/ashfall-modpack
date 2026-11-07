"""Weapon Parts icon: a salvaged pile of gunmetal receiver, coil spring and brass screw."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Hand-placed pixel map; the 1px OUTLINE is added afterwards by Sprite.outline.
ART = [
	"................",
	"................",
	"...hhhhhhhhhh...",
	"..hldldldldlld..",
	"..lmmmkkkkmmmd..",
	"..lmmmkxxkmkmd..",
	"..lmllmmmmmmds..",
	"..dddddddddds...",
	"............YYy.",
	".lh..h..h..dyxs.",
	".m.p..p..p.d.Yx.",
	".d..l..l..ld.ys.",
	".............Yx.",
	".............ys.",
	"..............s.",
	"................",
]

PALETTE = {
	# gunmetal ramp: cool shadows, warm highlights
	"h": rgb("aaa89f"),
	"l": rgb("7e848c"),
	"m": rgb("5b616c"),
	"d": rgb("3f4551"),
	"s": rgb("2b303c"),
	"k": rgb("262a33"),  # ejection port recess
	# spring steel
	"p": rgb("9ba1a6"),
	# brass screw
	"y": rgb("a08440"),
	"Y": rgb("d6bb72"),
	"x": rgb("7a6230"),
}


def draw():
	"""Paint ART with PALETTE, then outline."""
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
