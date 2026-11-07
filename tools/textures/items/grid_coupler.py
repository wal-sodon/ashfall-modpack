"""Grid Coupler icon: a heavy three-pin grid plug, copper pins in a steel collar, a hazard-yellow grip with
a cyan "live" lamp, and a stub of black cable. Lit from the top-left like the other salvage icons."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

KEY = {
	".": CLEAR,
	# copper pins
	"O": rgb("f6c98a"),  # highlight
	"C": rgb("d98f4c"),  # light
	"c": rgb("ad6630"),  # base
	# steel collar
	"H": rgb("e6e1d6"),  # highlight
	"M": rgb("aeb1b8"),  # light
	"m": rgb("7b7f8c"),  # base
	"s": rgb("4c4f62"),  # shadow
	# hazard-yellow grip with black bands
	"Y": rgb("f2d06b"),  # highlight
	"y": rgb("d9a934"),  # base
	"u": rgb("9c6f22"),  # shadow
	"k": rgb("2a2830"),  # band
	# live lamp
	"a": rgb("f2ffff"),
	"b": rgb("26c4e4"),
	# cable
	"L": rgb("5a5762"),
	"j": rgb("312f3a"),
}

ART = [
	"................",
	"...O..O..O......",
	"...C..C..C......",
	"...c..c..c......",
	"..HMMMMMMMMm....",
	"..MmmmmmmmmS....",
	"..sssssssss.....",
	"..YyyyyyyyyU....",
	"..YkkkkkkkkU....",
	"..Yyyyabyyyu....",
	"..Yyyybbyyyu....",
	"..YkkkkkkkkU....",
	"..uuuuuuuuuu....",
	".....LLjj.......",
	"......Ljjj......",
	"........jjj.....",
]


def draw():
	"""Three copper pins over a steel collar, a banded yellow grip with a cyan lamp, cable trailing off."""
	s = Sprite()
	pal = dict(KEY, S=KEY["s"], U=KEY["u"])
	for y, row in enumerate(ART):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, pal[ch])
	s.outline(OUTLINE)
	return s
