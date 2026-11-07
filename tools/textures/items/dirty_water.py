"""Dirty Water icon: a glass bottle of murky river water with floating grit."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

# Hand-placed pixel map (vanilla glass bottle silhouette, so it reads as "filled from a river").
MAP = [
	"................",
	"................",
	"......cCCk......",
	"......gGGh......",
	".......gh.......",
	".......gh.......",
	"......gSSh......",
	".....gSmmMh.....",
	"....gSmmdmMh....",
	"....gmmmmMMh....",
	"....gmdmmMdh....",
	"....gmmmMMMh....",
	".....gMMMMh.....",
	"......hhhh......",
	"................",
	"................",
]

KEY = {
	".": CLEAR,
	# cork stopper
	"c": rgb("c49a64"), "C": rgb("9c7246"), "k": rgb("6e4e34"),
	# glass: highlight, edge, shadow edge
	"G": rgb("e8f0ec"), "g": rgb("b9cbc6"), "h": rgb("7d918f"),
	# murky water: surface sheen, base, shadow, grit
	"S": rgb("a2a876"), "m": rgb("7d8452"), "M": rgb("5c6340"), "d": rgb("4a3c2a"),
}


def draw():
	"""Glass bottle with a cork, murky brown-green water and specks of grit."""
	s = Sprite()
	for y, row in enumerate(MAP):
		for x, ch in enumerate(row):
			s.set(x, y, KEY[ch])
	s.outline(OUTLINE)
	return s
