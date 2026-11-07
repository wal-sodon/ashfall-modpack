"""Spray Can icon: a dented steel aerosol can with a red paint band, a white cap and a nozzle mist."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "director"

KEY = {
	".": CLEAR,
	"W": rgb("f4f2ea"),  # cap highlight
	"w": rgb("c9c6bc"),  # cap shadow
	"n": rgb("3c3c46"),  # nozzle
	"m": rgb("ffb0a0"),  # paint mist
	"H": rgb("e9ebee"),  # steel highlight
	"L": rgb("b9bcc2"),  # steel light
	"S": rgb("8b8f9a"),  # steel shadow
	"D": rgb("5c6070"),  # steel deep
	"R": rgb("e2453a"),  # paint band
	"r": rgb("a8302b"),  # paint band shadow
	"Y": rgb("f2c230"),  # label stripe
}

ART = [
	"................",
	"..........m.m...",
	"........nn.m..m.",
	".......Wwn..m...",
	"......WWWw......",
	"......WWww......",
	".....HLLLSD.....",
	".....HLLLSD.....",
	".....RRRRrr.....",
	".....RYYYrr.....",
	".....RRRRrr.....",
	".....HLLSSD.....",
	".....HLLLSD.....",
	".....HLLSSD.....",
	"......LSSD......",
	"................",
]


def draw():
	s = Sprite()
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	# outline the can only, not the loose mist dots
	mist = [(x, y) for y, row in enumerate(ART) for x, ch in enumerate(row) if ch == "m"]
	for x, y in mist:
		s.set(x, y, CLEAR)
	s.outline(OUTLINE)
	for x, y in mist:
		s.set(x, y, KEY["m"])
	return s
