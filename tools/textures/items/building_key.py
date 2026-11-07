"""Building Key icon: an old brass door key on a paper tag, the kind a building manager kept on a board."""
from sprite import Sprite, rgb, CLEAR

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	"K": rgb("2e2618"),
	"B": rgb("f0d27a"),
	"b": rgb("c9a54a"),
	"d": rgb("8f7230"),
	"P": rgb("e8dfc8"),
	"p": rgb("bfb49a"),
	"s": rgb("8a7f6a"),
}

PIXELS = [
	"................",
	"..KKKK..........",
	".KPPPPK.........",
	".KPpspK.........",
	".KPPPPKK........",
	"..KKKK.sK.......",
	"........sKKK....",
	".......KbBBBK...",
	"......KbBKKbBK..",
	"......KbK..KbK..",
	"......KbBKKbdK..",
	".....KbdbbbdK...",
	"....KbdKKKKK....",
	"...KbdKK........",
	"..KbdKbK........",
	"..KKK.KK........",
]


def draw():
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	return s
