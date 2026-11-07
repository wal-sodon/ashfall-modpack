"""Grid Uplink icon: a heavy steel spike-plug standing upright, a window down its body showing the glowing violet
crystal core, cyan status lamps on the collar, and three copper prongs at the foot that go into the Uplink Socket."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

PIXELS = [
	"......HMm.......",
	".....HMMms......",
	"....HMwlms......",
	"....Mwlcbs......",
	"....Mlcbds......",
	"....Mcbbds......",
	"....Mbbdds......",
	"...HMMMMmms.....",
	"...MaMaMams.....",
	"...MmmmmmMs.....",
	"....sssssss.....",
	"....yYyYyYy.....",
	"....O.O.O.......",
	"....C.C.C.......",
	"....c.c.c.......",
	"................",
]

KEY = {
	".": CLEAR,
	"H": rgb("e6e1d6"),
	"M": rgb("aeb1b8"),
	"m": rgb("7b7f8c"),
	"s": rgb("4c4f62"),
	"w": rgb("fff3fa"),
	"l": rgb("ee8ff0"),
	"c": rgb("cf4fe6"),
	"b": rgb("9a3ad8"),
	"d": rgb("3b1c78"),
	"a": rgb("26c4e4"),
	"y": rgb("d9a934"),
	"Y": rgb("2a2830"),
	"O": rgb("f6c98a"),
	"C": rgb("d98f4c"),
}


def draw():
	"""Shifted to the centre of the icon; the core window is the brightest thing on it, as the Uplink is in play."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x + 3, y, KEY[ch])
	# darker copper at the prong tips
	for x in (7, 9, 11):
		s.set(x, 14, rgb("ad6630"))
	s.outline(OUTLINE)
	return s
