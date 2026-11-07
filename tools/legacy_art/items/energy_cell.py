"""Energy Cell icon: glowing cyan core in a worn metal casing with terminal caps."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Metal ramp (cool shadows, warm highlights) + cyan energy ramp (bright core).
KEY = {
	".": CLEAR,
	"1": rgb("e6e2d8"),  # metal highlight (warm)
	"2": rgb("aeb2b6"),  # metal light
	"3": rgb("7a808c"),  # metal base
	"4": rgb("4a4e60"),  # metal shadow (cool)
	"a": rgb("f2ffff"),  # energy core
	"b": rgb("86f4ff"),  # energy bright
	"c": rgb("26c4e4"),  # energy base
	"d": rgb("1478a0"),  # energy deep
}

ART = [
	"................",
	"......1234......",
	"....11222334....",
	"....23333444....",
	"....2dbabcd4....",
	"....2cbabcd4....",
	"....2cbabcd4....",
	"....3dcbccd4....",
	"....12234344....",
	"....2cbabcd4....",
	"....2cbabcd4....",
	"....2cbabcd4....",
	"....3dcbcdd4....",
	"....12233344....",
	"....33344444....",
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
