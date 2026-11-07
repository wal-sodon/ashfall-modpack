"""Brute spawn egg: dark bruised flesh with red scars."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("8a7a70"), rgb("6a5a52"), rgb("463a36"), rgb("a02a22"),
			[(5, 6), (6, 7), (7, 8), (8, 9), (10, 5), (10, 6), (4, 11), (9, 12), (10, 12)])
