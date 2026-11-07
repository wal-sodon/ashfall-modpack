"""Spike Strip board: a scrap plank studded with nails, rustier at each wear stage (shared by spike_strip_<wear>)."""
from sprite import Sprite
from _wood import IRON, PINE, RUST, h


def board(wear):
	s = Sprite()
	for y in range(16):
		for x in range(16):
			r = h(x, y, salt=149)
			c = PINE[2] if r < 0.6 else PINE[3]
			if h(x // 4, y, salt=151) > 0.75:
				c = PINE[1]  # grain streaks
			# rust stains spread with wear
			if h(x, y, salt=157 + wear) < 0.06 + wear * 0.08:
				c = RUST[2] if r < 0.5 else RUST[1]
			s.set(x, y, c)
	# board seams every 4 pixels
	for y in (3, 7, 11, 15):
		s.hline(0, 15, y, PINE[4])
	# a scrap-metal strap across the boards
	s.rect(0, 9, 15, 10, IRON[3] if wear < 2 else RUST[1])
	s.hline(0, 15, 9, IRON[2] if wear < 2 else RUST[0])
	# nail heads punched through (fewer as it wears, matching the model's nails)
	heads = [(2, 1), (6, 5), (10, 1), (14, 5), (2, 13), (6, 13), (10, 13), (13, 12)]
	for x, y in heads[: 8 - 2 * wear]:
		s.set(x, y, IRON[0])
		s.set(x + 1, y, IRON[2])
	return s
