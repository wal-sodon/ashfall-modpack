"""Heat Sink icon: a block of copper cooling fins on a steel clamp, warm at the base and cool at the tips."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630"), rgb("6e3a2e")]
STEEL = [rgb("aeb1b8"), rgb("7b7f8c"), rgb("4c4f62")]


def draw():
	s = Sprite()
	# steel base clamp
	s.rect(2, 11, 13, 13, STEEL[1])
	s.hline(2, 13, 11, STEEL[0])
	s.hline(2, 13, 13, STEEL[2])
	s.set(3, 12, STEEL[0])
	s.set(12, 12, STEEL[0])
	# six fins, lit from the left
	for i, x in enumerate(range(3, 14, 2)):
		top = 3 if i % 2 == 0 else 4
		s.vline(x, top, 10, COPPER[1])
		s.set(x, top, COPPER[0])
		s.vline(x, 8, 10, COPPER[2])
	# a dark gap behind each fin
	for x in range(4, 13, 2):
		s.vline(x, 5, 10, COPPER[3])
	s.outline(OUTLINE)
	return s
