"""Spark Pistol icon: small boxy scrap pistol in profile, muzzle right, exposed red-orange coil, taped grip."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

# Ramps lit from the top-left; shadows lean cool/plum, highlights warm.
KEY = {
	# scrap sheet metal
	"1": rgb("b9bcc0"),  # highlight / rivets
	"3": rgb("767a86"),  # base
	"4": rgb("4f5262"),  # shadow
	"5": rgb("2e2f3c"),  # deep shadow / dark parts
	# duct tape
	"t": rgb("c6c6be"),  # light
	"v": rgb("8a8a83"),  # shade
	# copper windings
	"k": rgb("e39a52"),  # light
	"n": rgb("8a4526"),  # shadow
	# red-orange energy (#ff5a3c)
	"a": rgb("ffe6c8"),  # core glow
	"r": rgb("ff5a3c"),  # base
	"d": rgb("b02a26"),  # deep
	# salvaged cell
	"f": rgb("4f8579"),
}

ART = [
	"................",
	"................",
	"................",
	"...5...5........",
	"..1111111.k.k...",
	"..1ff5f3d5kaka3.",
	"..3ff5f3r5kakaa.",
	"..4444444dkrkr4.",
	"...4433335n.n...",
	"...tttv3.5......",
	"..vvv45555......",
	"..334...........",
	".tttv...........",
	".vvv5...........",
	".455............",
	"................",
]


def draw():
	"""Boxy riveted housing with a teal scavenged cell zip-tied on, a bare wire feeding an exposed copper
	coil round a glowing red-orange core, a short trigger guard and a stubby grip wrapped in duct tape."""
	s = Sprite()
	for y, row in enumerate(ART):
		assert len(row) == 16, (y, row)
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
