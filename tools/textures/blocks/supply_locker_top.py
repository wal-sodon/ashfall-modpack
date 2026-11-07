"""Supply Locker top: the painted lid, scuffed where things get stacked on it."""
from sprite import Sprite
from _locker import body
from _wood import IRON, h

OWNER = "arms"


def draw():
	s = Sprite()
	body(s, salt=317)
	for y in range(4, 12):
		for x in range(4, 12):
			if h(x, y, 319) > 0.8:
				s.set(x, y, IRON[3])
	return s
