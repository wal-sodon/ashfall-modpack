"""Graffiti warn overlay: an orange question mark scrawled in the corner over a Cleared mark, "heard movement"."""
from _graffiti import ORANGE, paint

OWNER = "director"

ART = [
	"..........####..",
	".........#....#.",
	"..............#.",
	".............#..",
	"............#...",
	"............#...",
	"................",
	"............#...",
	"................",
	"................",
	"................",
	"................",
	"................",
	"................",
	"................",
	"................",
]


def draw():
	return paint(ART, ORANGE, salt=306, drips=[(12, 8)])
