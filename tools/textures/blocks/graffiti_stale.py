"""Graffiti stale overlay: grime and grey wash streaked over an old Cleared mark, which is no longer true."""
from sprite import Sprite
from _graffiti import GREY
from _wood import h

OWNER = "director"


def draw():
	"""Diagonal streaks of grey wash, patchy so the mark under it still shows through."""
	s = Sprite()
	for y in range(16):
		for x in range(16):
			band = (x + y) % 5
			r = h(x, y, salt=307)
			if (band == 0 and r > 0.25) or r > 0.9:
				s.set(x, y, GREY[0] if r > 0.6 else GREY[1])
			elif band == 1 and r > 0.7:
				s.set(x, y, GREY[2])
	return s
