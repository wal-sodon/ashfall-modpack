"""Rain Collector rim and floor: worn steel lip around a dark, damp basin floor."""
from sprite import Sprite, rgb
from _wood import IRON, RUST, h

OWNER = "director"

DAMP = [rgb("4d5560"), rgb("3c434d"), rgb("2e343c")]


def draw():
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt=71)
			s.set(x, y, DAMP[0] if r > 0.8 else DAMP[2] if r < 0.2 else DAMP[1])
			if h(x, y, salt=73) > 0.95:
				s.set(x, y, RUST[2])
	s.hline(0, 15, 0, IRON[0])
	s.vline(0, 0, 15, IRON[1])
	s.hline(0, 15, 15, IRON[3])
	s.vline(15, 0, 15, IRON[3])
	return s
