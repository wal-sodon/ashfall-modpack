"""Survivor spawn egg: warm skin tones with olive scavenger-jacket spots."""
from sprite import rgb
from eggs import egg

OWNER = "arms"


def draw():
	return egg(rgb("e8c09a"), rgb("c68c62"), rgb("8a5a3c"), rgb("5f6a3c"),
			[(5, 6), (6, 6), (9, 5), (10, 7), (4, 10), (5, 11), (8, 10), (9, 11), (10, 11), (7, 8)])
