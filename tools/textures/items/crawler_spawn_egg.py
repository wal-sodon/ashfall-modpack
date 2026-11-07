"""Crawler spawn egg: pale grey-green with raw red scrapes."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("b4c0a0"), rgb("8a9a78"), rgb("5e6c50"), rgb("8a2a22"),
			[(6, 4), (7, 4), (9, 7), (10, 7), (5, 10), (6, 10), (11, 11), (8, 12)])
