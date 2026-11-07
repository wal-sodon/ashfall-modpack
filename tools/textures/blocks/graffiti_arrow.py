"""Graffiti arrow: a white sprayed arrow pointing up; the models turn it to point down, left or right."""
from _graffiti import WHITE, paint

OWNER = "director"

ART = [
	"................",
	".......##.......",
	"......####......",
	".....##..##.....",
	"....##.##.##....",
	"...##..##..##...",
	"..##...##...##..",
	".......##.......",
	".......##.......",
	".......##.......",
	".......##.......",
	".......##.......",
	".......##.......",
	".......##.......",
	"................",
	"................",
]


def draw():
	return paint(ART, WHITE, salt=305)
