"""Shared paint for the Supply Locker faces: olive-drab painted steel, chipped to bare metal at the edges."""
from sprite import rgb
from _wood import IRON, h

# Locker paint, highlight to deep shadow: a faded military olive drab.
PAINT = [rgb("7d8458"), rgb("656b46"), rgb("50553a"), rgb("373b29")]


def body(s, salt):
	"""Mottled paint with chips down to steel, a lit top-left edge and a dark bottom-right edge."""
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt)
			c = PAINT[1] if r < 0.6 else PAINT[2]
			if r > 0.95:
				c = IRON[2]
			s.set(x, y, c)
	s.hline(0, 15, 0, PAINT[0])
	s.vline(0, 0, 15, PAINT[0])
	s.hline(0, 15, 15, PAINT[3])
	s.vline(15, 0, 15, PAINT[3])
