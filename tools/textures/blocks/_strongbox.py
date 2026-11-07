"""Shared paint for the Strongbox faces: dark green safe enamel over steel, worn at the edges."""
from sprite import rgb
from _wood import IRON, h

# Safe enamel, highlight to deep shadow: an old bank-safe green, scuffed to the steel.
ENAMEL = [rgb("5d7a63"), rgb("4a644f"), rgb("3b5040"), rgb("2a3a2f")]


def body(s, salt):
	"""Mottled enamel with scuffs down to bare steel, a lit top-left edge and a dark bottom-right edge."""
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt)
			c = ENAMEL[1] if r < 0.62 else ENAMEL[2]
			if r > 0.95:
				c = IRON[2]  # scuffed to the steel
			s.set(x, y, c)
	s.hline(0, 15, 0, ENAMEL[0])
	s.vline(0, 0, 15, ENAMEL[0])
	s.hline(0, 15, 15, ENAMEL[3])
	s.vline(15, 0, 15, ENAMEL[3])
