"""Recoloured copies of existing block textures: the Rapid Charger is the Field Charger repainted warm (its
green-grey casing turned rust-tan, its blue accents amber), so the two read apart at a glance while sharing
every shape."""
import sys
from pathlib import Path

from sprite import Sprite

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from gen_armor_layers import read_png  # noqa: E402


def amber(source):
	"""The arms block texture {source}.png with green casing swapped to warm tan and blue accents to amber."""
	path = ROOT / "modules/arms/src/main/resources/assets/ashfall/textures/block" / f"{source}.png"
	w, h, rows = read_png(path.read_bytes())
	s = Sprite(w, h)
	for y, row in enumerate(rows):
		for x, (r, g, b, a) in enumerate(row):
			if a == 0:
				pass
			elif b > r + 24:
				r, g, b = b, min(255, int(g * 0.8)), r
			elif g > r:
				r, g, b = g, r, int(b * 0.8)
			s.set(x, y, (r, g, b, a))
	return s
