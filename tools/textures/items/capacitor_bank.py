"""Capacitor Bank icon: eight Energy Cells (copper caps, dark cases, cyan charge stripes) strapped in two rows to a
copper bus bar with a Grid Coupler plug in the middle. Two of these go into the Grid Uplink."""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

CASE = [rgb("5a5e6a"), rgb("44474f"), rgb("2e3040")]
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630")]
CYAN = [rgb("86f4ff"), rgb("26c4e4")]
STRAP = rgb("d9a934")


def cell(s, x, y):
	"""One 3x5 cell: copper cap, dark case, a cyan charge stripe, light from the left."""
	s.hline(x, x + 2, y, COPPER[1])
	s.set(x, y, COPPER[0])
	s.rect(x, y + 1, x + 2, y + 4, CASE[1])
	s.vline(x, y + 1, y + 4, CASE[0])
	s.vline(x + 2, y + 1, y + 4, CASE[2])
	s.set(x + 1, y + 2, CYAN[0])
	s.set(x + 1, y + 3, CYAN[1])


def draw():
	s = Sprite()
	for y in (1, 9):
		for col in range(4):
			cell(s, 1 + col * 4, y)
	# copper bus bar between the rows, the coupler plug at its centre
	s.hline(1, 14, 7, COPPER[1])
	s.hline(1, 14, 8, COPPER[2])
	s.rect(6, 6, 9, 9, STRAP)
	s.set(6, 6, rgb("f2d06b"))
	s.set(9, 9, rgb("9c6f22"))
	s.set(7, 7, CYAN[0])
	s.set(8, 8, CYAN[1])
	# straps around each row
	for y in (3, 12):
		s.hline(1, 14, y, STRAP)
	s.outline(OUTLINE)
	return s
