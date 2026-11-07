"""Spitter spawn egg: zombie green with glowing acid drips."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("6e9a50"), rgb("4e7b36"), rgb("385226"), rgb("b8ec38"),
			[(7, 3), (7, 4), (7, 5), (10, 6), (10, 7), (5, 8), (5, 9), (5, 10), (9, 11), (9, 12)])
