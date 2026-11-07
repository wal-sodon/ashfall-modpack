"""Arc Magnum icon: chunky energy hand cannon in profile, muzzle right, magenta capacitor drum."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

KEY = {
	"1": rgb("a3a9b3"),  # steel highlight
	"2": rgb("6c7182"),  # steel light
	"3": rgb("4f5363"),  # steel base
	"4": rgb("2e303b"),  # steel shadow
	"g": rgb("524548"),  # grip light
	"h": rgb("3c3236"),  # grip base
	"j": rgb("241e21"),  # grip shadow
	"a": rgb("ffe2f5"),  # energy core
	"b": rgb("ff8ad6"),  # energy bright
	"c": rgb("ff3cb4"),  # energy base
	"d": rgb("a01a6e"),  # energy deep
	"o": rgb("c47e44"),  # copper trigger
}

ART = [
	"................",
	"................",
	"..12.........1..",
	"..23.b2b....122.",
	"...32a2a3111123.",
	"...33c3c3ccc34a.",
	"...34c4c444434b.",
	"...34d4d4222344.",
	"...ghd4d44......",
	"...ghj..o.3.....",
	"..ghj333333.....",
	"..ghj...........",
	".ghj............",
	".ghj............",
	".23c............",
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
