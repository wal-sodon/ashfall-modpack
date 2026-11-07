"""Screamer spawn egg: bone white with black gaping-mouth marks."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("ece6dc"), rgb("c8c0b4"), rgb("948c80"), rgb("1a1a1a"),
			[(6, 5), (9, 5), (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10), (5, 12), (10, 12)])
