"""Standard Cell Pack icon: boxy copper-capped steel pack with a glowing cyan cell window and a charge stripe.

The three cell packs (small, standard, heavy) are one family drawn by pack() below with growing
proportions; cell_pack_small.py and cell_pack_heavy.py load this module and call pack() themselves.
"""
from sprite import Sprite, rgb, OUTLINE

OWNER = "arms"

# Lit from the top-left: highlights lean warm, shadows lean cool/plum.
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630"), rgb("6e3a2e")]  # hi, light, base, shadow
STEEL = [rgb("e6e1d6"), rgb("aeb1b8"), rgb("7b7f8c"), rgb("4c4f62")]
CYAN = [rgb("f2ffff"), rgb("86f4ff"), rgb("26c4e4"), rgb("1478a0")]  # core, bright, base, deep
CHARGE = rgb("b4f05a")  # lit charge segment (lime, reads apart from the cyan window)


def pack(x0, x1, y0, y1, cap=2, window=3, terminals=(), lit=3, handle=False, ribs=False):
	"""Draw one pack with its body spanning columns x0..x1 and rows y0..y1 (top cap included).

	cap: copper cap rows at top and bottom; window: cyan window rows; terminals: columns of the
	steel contact nubs that sit one row above y0; lit/segments: charge stripe; handle: a carry bail
	above the pack (heavy); ribs: copper side rails down the body (heavy).
	"""
	s = Sprite()
	hi, li, ba, sh = COPPER
	shi, sli, sba, ssh = STEEL

	# Contact nubs / handle above the top cap.
	for tx in terminals:
		s.set(tx, y0 - 1, shi)
		s.set(tx + 1, y0 - 1, sba)
	if handle:
		hx0, hx1 = x0 + 2, x1 - 2
		s.hline(hx0, hx1, y0 - 2, sli)
		s.set(hx0, y0 - 2, shi)
		s.set(hx1, y0 - 2, sba)
		s.vline(hx0, y0 - 1, y0 - 1, sli)
		s.vline(hx1, y0 - 1, y0 - 1, ssh)

	# Copper top cap: bright top edge, darker right side.
	for r in range(cap):
		y = y0 + r
		s.hline(x0, x1, y, li if r == 0 else ba)
		s.set(x0, y, hi if r == 0 else li)
		s.set(x1, y, ba if r == 0 else sh)
		if r == 0:
			s.hline(x0 + 1, x0 + 2, y, hi)
	# Copper bottom cap.
	for r in range(cap):
		y = y1 - cap + 1 + r
		last = r == cap - 1
		s.hline(x0, x1, y, sh if last else ba)
		s.set(x0, y, sh if last else li)
		s.set(x1, y, sh)

	# Steel body between the caps.
	by0, by1 = y0 + cap, y1 - cap
	s.rect(x0, by0, x1, by1, sli)
	s.vline(x0, by0, by1, shi)
	s.vline(x1, by0, by1, ssh)
	s.vline(x1 - 1, by0, by1, sba)
	s.hline(x0, x1 - 1, by0, shi)
	s.set(x1, by0, sba)

	# Copper side rails on the heavy pack.
	ix0, ix1 = x0 + 2, x1 - 2
	if ribs:
		s.vline(x0 + 1, by0, by1, ba)
		s.set(x0 + 1, by0, li)
		s.vline(x1 - 1, by0, by1, sh)
		ix0, ix1 = x0 + 3, x1 - 3

	# Glowing cyan window in a recessed dark frame, bright core at top-left, deep at bottom-right.
	wy0, wy1 = by0 + 1, by0 + window
	s.rect(ix0 - 1, wy0 - 1, ix1 + 1, wy1 + 1, ssh)
	s.rect(ix0, wy0, ix1, wy1, CYAN[2])
	s.hline(ix0, ix1, wy0, CYAN[1])
	s.set(ix0, wy0, CYAN[0])
	if window >= 3:
		s.set(ix0, wy0 + 1, CYAN[1])
		s.set(ix0 + 1, wy0, CYAN[0])
	s.hline(ix0 + 1, ix1, wy1, CYAN[3])
	s.vline(ix1, wy0 + 1, wy1, CYAN[3])

	# Charge stripe under the window: 1px segments with 1px gaps, the first `lit` glowing.
	sy = min(wy1 + 3, by1)
	k = 0
	for x in range(ix0 - 1, ix1 + 2, 2):
		s.set(x, sy, CHARGE if k < lit else ssh)
		k += 1
	s.outline(OUTLINE)
	return s


def draw():
	return pack(4, 12, 3, 13, cap=2, window=3, terminals=(5, 10), lit=2)
