"""Shared palettes and helpers for Ashfall's fortification block textures (stdlib only).

Block faces tile, so unlike item icons they have no outline and fill all 16x16 pixels. Light comes from
the top-left as in the item set: each plank has a lit top edge and a dark bottom seam.
"""
from sprite import rgb

# Weathered oak, highlight to deep shadow (warm highlights, cool plum-brown shadows like the icon set).
OAK = [rgb("c9a26a"), rgb("a8834e"), rgb("8c6a3d"), rgb("6c4f2e"), rgb("4a3322")]
# Old pine for the barricade boards: greyer and more battered than the oak.
PINE = [rgb("b9a383"), rgb("9a8465"), rgb("7d6a50"), rgb("5f4f3c"), rgb("3c3129")]
# Bare iron (rivets, straps).
IRON = [rgb("eceae4"), rgb("b9bcc2"), rgb("8b8f9a"), rgb("5c6070"), rgb("363a4a")]
# Rust on the scrap-metal straps (as in the scrap_metal icon).
RUST = [rgb("d08a48"), rgb("a65a2e"), rgb("6e3628")]
GAP = rgb("231c1a")


def h(x, y, salt=0):
	"""Deterministic per-pixel hash in 0..1 (no random module, so output is stable across runs)."""
	n = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
	n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
	return (n ^ (n >> 16)) / 0xFFFFFFFF


def plank_rows(s, ramp, rows, salt, gap=None, x0=0, x1=15):
	"""Horizontal planks. rows: list of (top, bottom) inclusive. Grain streaks plus a lit top edge and a
	dark bottom seam; when gap is given the seam is a dark gap instead."""
	for k, (top, bottom) in enumerate(rows):
		for y in range(top, bottom + 1):
			for x in range(x0, x1 + 1):
				r = h(x, y, salt + k)
				c = ramp[1] if r < 0.55 else ramp[2]
				# long grain streaks: the same row segment shares a tone
				if h(x // 5, y, salt + 7 * k) > 0.8:
					c = ramp[2]
				if r > 0.96:
					c = ramp[3]  # knots and nail holes
				s.set(x, y, c)
		s.hline(x0, x1, top, ramp[0])
		s.hline(x0, x1, bottom, gap if gap is not None else ramp[3])
		# a butt joint somewhere along each plank
		joint = x0 + 3 + int(h(k, 0, salt) * (x1 - x0 - 6))
		s.vline(joint, top + 1, bottom - 1, ramp[3])
		s.vline(joint + 1, top + 1, bottom - 1, ramp[1])


def rivet(s, x, y):
	"""Iron nugget rivet: lit top-left, shaded bottom-right."""
	s.set(x, y, IRON[0])
	s.set(x + 1, y, IRON[2])
	s.set(x, y + 1, IRON[2])
	s.set(x + 1, y + 1, IRON[4])
