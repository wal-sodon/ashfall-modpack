"""Ion Rifle icon: pale ceramic rifle, caged green ion chamber, long steel barrel with coil, short optic."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

KEY = {
	".": CLEAR,
	"1": rgb("eef0e8"),  # ceramic highlight
	"2": rgb("cdd2c9"),  # ceramic light
	"3": rgb("a3aaa4"),  # ceramic base
	"4": rgb("737b7a"),  # ceramic shadow
	"m": rgb("808a98"),  # gunmetal light
	"n": rgb("596170"),  # gunmetal base
	"o": rgb("3c4250"),  # gunmetal shadow
	"s": rgb("c9cfd6"),  # steel light
	"t": rgb("6a7280"),  # steel shadow
	"a": rgb("f0fff4"),  # ion core
	"b": rgb("5aff8c"),  # ion bright
	"c": rgb("2bc966"),  # ion base
	"d": rgb("15834a"),  # ion deep
}

ART = [
	"................",
	"................",
	"................",
	"................",
	".....omnb.......",
	".111211111122...",
	".3..3babn33.c.n.",
	".4..ncacn4dsbsb.",
	".344onnno44.d.n.",
	".o..mnonc.......",
	"...mn..od.......",
	"...mo...........",
	"................",
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
