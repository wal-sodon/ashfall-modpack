"""Resonance Crystal icon: a glowing faceted violet-magenta shard with a small companion shard."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

# Hand-placed pixel map; light from the top-left, cooler shadows, warmer highlights.
PIXELS = [
	"................",
	".......h........",
	"..*...hws.......",
	".*+*.hhwms......",
	"..*.hhwwmsd.....",
	"....lhwmbsd.....",
	"....lhwmbsd..l..",
	"....lhwmbsd.hms.",
	"....lhmmbsd.hms.",
	"....lhmmbsd.lms.",
	"....lmmbbsd.lbs.",
	"....bmbbssd.bsd.",
	".....bbssd..sd..",
	"......bsd.......",
	".......d........",
	"................",
]

KEY = {
	".": CLEAR,
	"d": rgb("3b1c78"),   # deep shadow, blue-violet
	"s": rgb("6a2bb5"),   # shadow
	"b": rgb("9a3ad8"),   # base violet
	"m": rgb("cf4fe6"),   # magenta glow
	"l": rgb("ee8ff0"),   # light
	"h": rgb("ffd2ee"),   # warm highlight
	"w": rgb("fff3fa"),   # bright core
	"*": rgb("f6a8ff"),   # sparkle arms
	"+": rgb("fff6fd"),   # sparkle centre
}


def draw():
	"""Faceted crystal with a glowing core, outlined like vanilla items."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
