"""Matches icon: worn matchbox with its drawer slid out and red-tipped matches poking up."""
from sprite import Sprite, rgb, OUTLINE, CLEAR, W, H

OWNER = "salvage"

PALETTE = {
	".": CLEAR,
	# match heads
	"h": rgb("ff9a6a"),  # head highlight (warm)
	"R": rgb("d2392f"),  # head base
	"r": rgb("852235"),  # head shadow (cool crimson)
	# match sticks (pale wood)
	"w": rgb("eed3a0"),  # stick light
	"v": rgb("b8925f"),  # stick shadow
	# sleeve (faded teal-blue printed card)
	"T": rgb("8fb3ad"),  # sleeve top highlight
	"B": rgb("4f7d80"),  # sleeve base
	"b": rgb("35505e"),  # sleeve shadow
	"L": rgb("e2d6b4"),  # label band (grimy cream)
	"l": rgb("b3a687"),  # label band shadow
	# drawer (raw cardboard)
	"D": rgb("c99a62"),  # drawer light
	"d": rgb("97693f"),  # drawer base
	"k": rgb("5a3a2e"),  # drawer interior / shadow
}

PIXELS = [
	"................",
	"................",
	"...........hR...",
	".......hR..Rr...",
	".......Rr...w...",
	"........w...w...",
	".........w..w...",
	".........w..w...",
	"..TTTTTTTDwDwDD.",
	".TTBBBBBbkvkvhR.",
	".BBBBBBBbDDDDDd.",
	".LLhRLLlbDddddd.",
	".LLRrLLlbDddddd.",
	".BBBBBBBbDddddk.",
	".bbbbbbbbdkkkkk.",
	"................",
]


def draw():
	"""Teal matchbox sleeve with a cream label band; cardboard drawer slid right, matches up top."""
	s = Sprite()
	for y, row in enumerate(PIXELS):
		for x, ch in enumerate(row):
			s.set(x, y, PALETTE[ch])
	s.outline(OUTLINE)
	return s
