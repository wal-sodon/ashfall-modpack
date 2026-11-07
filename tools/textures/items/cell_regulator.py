"""Cell Regulator icon: a green circuit board with a capacitor bank and a brass dial, two contact pins."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

BOARD = [rgb("5fae6a"), rgb("3e8a4c"), rgb("2a6236"), rgb("1c4024")]
BRASS = [rgb("f4dc95"), rgb("d6a64a"), rgb("a27530")]
CAP = [rgb("8ac8ff"), rgb("3c7ad8"), rgb("234a8a")]
PIN = rgb("cfd6dc")


def draw():
	s = Sprite()
	s.rect(2, 4, 13, 12, BOARD[1])
	s.hline(2, 13, 4, BOARD[0])
	s.vline(2, 4, 12, BOARD[0])
	s.hline(2, 13, 12, BOARD[2])
	s.vline(13, 4, 12, BOARD[2])
	# traces
	s.hline(4, 8, 10, BOARD[3])
	s.vline(8, 7, 10, BOARD[3])
	# capacitor bank
	for x in (4, 6):
		s.vline(x, 5, 8, CAP[1])
		s.set(x, 5, CAP[0])
		s.set(x, 8, CAP[2])
	# regulator dial
	s.disc(10.5, 7.5, 2.2, BRASS[1])
	s.set(9, 6, BRASS[0])
	s.set(10, 6, BRASS[0])
	s.set(11, 9, BRASS[2])
	s.set(10, 7, BOARD[3])
	s.set(11, 6, BOARD[3])
	# contact pins
	for x in (5, 10):
		s.vline(x, 13, 14, PIN)
	s.outline(OUTLINE)
	return s
