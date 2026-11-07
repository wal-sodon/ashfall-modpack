"""Uplink Frame icon: a riveted steel hex ring holding three violet Resonance Crystal shards, with a cyan focusing
lens set into the ring at the bottom. The first of the Grid Uplink's three crafts."""
from sprite import Sprite, rgb, OUTLINE, CLEAR

OWNER = "arms"

PIXELS = [
	"................",
	".....HMMMMm.....",
	"....HMssssmm....",
	"...HMs.cl..mm...",
	"..HMs..cb...mm..",
	"..Ms.l.......m..",
	".HM..cb...l..mm.",
	".Ms..bd...cb..m.",
	".Ms.......bd..m.",
	".Ms...........m.",
	"..Ms.........ms.",
	"..sMs..aa...ms..",
	"...sMs.ab..ms...",
	"....sMmmmmms....",
	".....ssssss.....",
	"................",
]

KEY = {
	".": CLEAR,
	# steel ring
	"H": rgb("e6e1d6"),
	"M": rgb("aeb1b8"),
	"m": rgb("7b7f8c"),
	"s": rgb("4c4f62"),
	# crystal shards
	"l": rgb("ffd2ee"),
	"c": rgb("cf4fe6"),
	"b": rgb("9a3ad8"),
	"d": rgb("3b1c78"),
	# lens
	"a": rgb("f2ffff"),
}


def draw():
	"""The ring is open in the middle so the shards read as held, not stacked; the lens glints cyan."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			if ch != ".":
				s.set(x, y, KEY[ch])
	s.set(8, 12, rgb("26c4e4"))
	s.outline(OUTLINE)
	return s
