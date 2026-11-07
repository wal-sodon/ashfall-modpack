"""Heart Pylon: a block of the Wreck's living crystal, violet facets around brighter veins, a darker core showing
through. Lit (light 10), so its own glow carries the brightness rather than the texture."""
from sprite import Sprite
from _finale import CRYSTAL, gem
from _wood import h

OWNER = "arms"


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, 321)
			s.set(x, y, CRYSTAL[4] if r < 0.55 else CRYSTAL[3] if r < 0.85 else CRYSTAL[5])
	# veins running up the block
	for x0, phase in ((3, 0), (8, 2), (12, 1)):
		for y in range(16):
			x = x0 + ((y + phase) // 4) % 2
			s.set(x, y, CRYSTAL[2])
			if y % 5 == phase:
				s.set(x, y, CRYSTAL[1])
	gem(s, 5, 5, 2, CRYSTAL)
	gem(s, 11, 11, 2, CRYSTAL)
	s.set(13, 3, CRYSTAL[0])
	s.set(2, 12, CRYSTAL[1])
	return s
