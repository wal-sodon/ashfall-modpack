"""Bloated spawn egg: swollen sickly green with yellow boils."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("98c070"), rgb("6a9a46"), rgb("456a2c"), rgb("d0dc48"),
			[(7, 3), (10, 5), (5, 7), (6, 7), (9, 9), (10, 9), (4, 11), (8, 12), (11, 12)])
