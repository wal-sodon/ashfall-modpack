"""Supply Locker side: a plain steel panel with a pressed rib and two rivets."""
from sprite import Sprite
from _locker import body, PAINT
from _wood import rivet

OWNER = "arms"


def draw():
	s = Sprite()
	body(s, salt=313)
	s.rect(3, 3, 12, 12, PAINT[2])
	s.hline(3, 12, 3, PAINT[3])
	s.vline(3, 3, 12, PAINT[3])
	s.hline(3, 12, 12, PAINT[0])
	s.vline(12, 3, 12, PAINT[0])
	rivet(s, 1, 1)
	rivet(s, 13, 13)
	return s
