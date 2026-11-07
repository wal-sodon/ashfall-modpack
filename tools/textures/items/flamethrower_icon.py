"""Flamethrower icon: scrap-built sprayer in profile, nozzle right with a pilot flame, red fuel tank taped underneath."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

KEY = {
	"1": rgb("a3a9b3"),  # steel highlight
	"2": rgb("767c8a"),  # steel light
	"3": rgb("545967"),  # steel base
	"4": rgb("2e303b"),  # steel shadow
	"r": rgb("d8483a"),  # tank light
	"s": rgb("a82c24"),  # tank base
	"t": rgb("6a1a16"),  # tank shadow
	"w": rgb("b8b2a0"),  # duct tape
	"g": rgb("524548"),  # grip light
	"h": rgb("2a2327"),  # grip shadow
	"y": rgb("ffe28a"),  # flame core
	"o": rgb("ff9a2a"),  # flame
	"d": rgb("c4501a"),  # flame edge
	"c": rgb("c47e44"),  # copper hose
}

ART = [
	"................",
	"................",
	"................",
	"............od..",
	"...........oyo..",
	".1222222222333od",
	".2333333333443..",
	".3434..c...4....",
	".gh.c.c.........",
	".gh.rrrrrrr.....",
	"..h.swsssws.....",
	"....swsssws.....",
	"....ttttttt.....",
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
