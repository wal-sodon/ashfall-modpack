"""Stalker spawn egg: near-black with two pale eyes."""
from sprite import rgb
from eggs import egg

OWNER = "director"


def draw():
	return egg(rgb("585e66"), rgb("383c42"), rgb("222428"), rgb("c8f0f0"),
			[(6, 7), (9, 7)])
