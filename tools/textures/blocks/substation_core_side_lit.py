"""Substation Core side, powered: the same housing with the gauge window glowing grid-cyan (the Energy Cell's
colour, so players read "power" the same way everywhere)."""
from sprite import Sprite, rgb
from substation_core_side import body, window

OWNER = "arms"

GLOW = [rgb("f2ffff"), rgb("86f4ff"), rgb("1478a0")]


def draw():
	s = Sprite()
	body(s)
	window(s, GLOW)
	return s
