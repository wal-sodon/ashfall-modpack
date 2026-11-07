"""Shared helpers for the Graffiti mark textures (stdlib only).

A mark is paint on someone else's wall, so the texture is mostly transparent (the block renders cut out):
a hand-sprayed stroke in one paint ramp, a little overspray dotted around it and a drip or two running down.
The strokes are a 16-row map where "#" is paint, so each symbol module is just its shape and its colour.
"""
from sprite import Sprite, rgb
from _wood import h

# Paint ramps, light to dark: the light tone is the wet centre of a stroke, the dark one its thin edge.
GREEN = [rgb("8fe37c"), rgb("5cbf4e"), rgb("3c8f36")]
RED = [rgb("ff6a55"), rgb("d93a2f"), rgb("9e2525")]
YELLOW = [rgb("ffe46b"), rgb("f2c230"), rgb("b88a1c")]
BLUE = [rgb("7cc4ff"), rgb("3f8fe0"), rgb("2a5fa8")]
WHITE = [rgb("fbfaf4"), rgb("d9d6cc"), rgb("a8a49a")]
ORANGE = [rgb("ffb35c"), rgb("f28a1e"), rgb("b85d12")]
GREY = [rgb("8a8a86"), rgb("6c6c69"), rgb("4e4e4c")]


def paint(art, ramp, salt, drips=()):
	"""Draws the stroke map: centre tone where a pixel is surrounded by paint, edge tone at the rim, sparse
	overspray dots next to the strokes and a drip running down from each (x, y) in drips."""
	s = Sprite()
	filled = {(x, y) for y, row in enumerate(art) for x, ch in enumerate(row) if ch == "#"}
	for (x, y) in filled:
		inner = all((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
		s.set(x, y, ramp[0] if inner or h(x, y, salt) > 0.7 else ramp[1])
	for y in range(16):
		for x in range(16):
			if (x, y) in filled:
				continue
			near = any((x + dx, y + dy) in filled for dx in (-1, 0, 1) for dy in (-1, 0, 1))
			if near and h(x, y, salt + 1) > 0.88:
				s.set(x, y, ramp[2])
	for (x, y) in drips:
		length = 1 + int(h(x, y, salt + 2) * 3)
		for k in range(length):
			s.set(x, y + k, ramp[1] if k < length - 1 else ramp[2])
	return s
