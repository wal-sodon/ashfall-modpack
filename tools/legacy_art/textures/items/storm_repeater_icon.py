"""Storm Repeater icon: bulky heavy automatic in side profile, muzzle right, red (#ff2d2d) accents.

Gunmetal receiver with a carry handle and red charge line, two red coil rings around the front
shroud, a stacked multi-emitter barrel cluster with a glowing spindle, a drum cell housing with a
red ring underneath, composite stock and raked grip.
"""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "arms"

KEY = {
	".": CLEAR,
	# gunmetal (warm highlight -> cool shadow)
	"A": rgb("b4bcc2"), "B": rgb("7e8894"), "C": rgb("566070"), "D": rgb("3a4150"),
	# steel emitter barrels
	"S": rgb("e4e7ea"), "T": rgb("a8aeb6"),
	# composite stock / grip
	"G": rgb("a39886"), "F": rgb("736b5f"), "f": rgb("48433d"),
	# red energy
	"a": rgb("ffb4a2"), "b": rgb("ff2d2d"), "c": rgb("a8141c"),
	# dark gaps
	"k": rgb("252a35"),
}

ART = [
	"................",
	"................",
	"....BAAB........",
	"....D..D.a.a....",
	"....AAAAAbTbSST.",
	".GGGBBBBBbCbTTD.",
	".fFFCabbCbDbkka.",
	".fFFCCCCCbCbSST.",
	".fffDDDDDcDcTTD.",
	"...GfkDDDD......",
	"...FfDbaabD.....",
	"..Gf.DbCDbD.....",
	"..Ff.DcbbcD.....",
	"..ff..DDDD......",
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
