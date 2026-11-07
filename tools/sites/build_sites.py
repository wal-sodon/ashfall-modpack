#!/usr/bin/env python3
"""Generates the Wreck site templates (salvage `data/ashfall/ashfall/site_template/*.json`).

Run: python3 -I tools/sites/build_sites.py

Like tools/landmarks, the templates are modelled as voxel grids in code and written out as text grids that
`xyz.toesinthesand.ashfall.salvage.site.SiteTemplate` reads: layers bottom-up, rows north to south, characters
west to east, ' ' = leave the world's block. Rerun after editing; the output is deterministic.

Design rules (plan section 1.6 and the Director's light sense):
- No light-emitting blocks: the city is dark and Director zombies hunt light, so a glowing wreck would be a
  zombie magnet nobody asked for. Amethyst *blocks* stand in for crystal veins (clusters and buds glow).
- No gravel or sand above ground: templates are placed without block ticks, and a floating gravel block
  would fall the first time a neighbour updates.
- The hull hatch is a pair of iron doors with no button (`#ashfall:landmark/sealed_door`): it opens with the
  Black Start at Grid 80%, not before. The Juggernaut waits on the `B` boss marker in front of it. Arms swaps the
  doors for its unbreakable Hull Seal when the hull is placed (salvage cannot name an arms block).
- The `S` lodestone on the deck is the Uplink Socket's placeholder (marker `uplink_socket`); arms turns it into the
  real socket, the same way the landmarks' substation placeholders become Substation Cores.
"""
import json
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "modules", "salvage", "src", "main", "resources", "data", "ashfall", "ashfall",
				   "site_template")

CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]"
CRASH_LOOT = "ashfall:chests/crash_site"
# Per-player cache inside the sealed hull (Lootr converts ashfall_cache tables): the crew chief's last log.
HULL_LOOT = "ashfall_cache:wreck_hull"


class Grid:
	def __init__(self, w, h, d):
		self.w, self.h, self.d = w, h, d
		self.cells = {}

	def set(self, x, y, z, c):
		if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
			self.cells[(x, y, z)] = c

	def get(self, x, y, z):
		return self.cells.get((x, y, z), " ")

	def layers(self):
		return [["".join(self.get(x, y, z) for x in range(self.w)) for z in range(self.d)] for y in range(self.h)]


def write(name, grid, palette, sink, foundation="", loot=None, markers=None):
	used = {c for c in grid.cells.values()}
	missing = used - set(palette)
	assert not missing, f"{name}: characters without palette entries: {missing}"
	data = {
		"palette": {k: v for k, v in sorted(palette.items()) if k in used},
		"sink": sink,
		"layers": grid.layers(),
	}
	if foundation:
		data["foundation"] = foundation
	if loot:
		data["loot"] = {k: v for k, v in loot.items() if k in used}
	if markers:
		data["markers"] = {k: v for k, v in markers.items() if k in used}
	os.makedirs(OUT, exist_ok=True)
	with open(os.path.join(OUT, name + ".json"), "w") as f:
		json.dump(data, f, indent="\t")
		f.write("\n")
	print(f"{name}: {grid.w}x{grid.h}x{grid.d}, {len(grid.cells)} cells")


def crater(g, cx, cz, r, ground, rng, rim="c", floor="d"):
	"""A shallow impact bowl: the ground layer dug out inside r, a scorched floor below, a raised rim."""
	for x in range(g.w):
		for z in range(g.d):
			dist = math.hypot(x - cx, z - cz)
			if dist <= r - 1:
				g.set(x, ground, z, ".")
				g.set(x, ground - 1, z, floor if rng.random() < 0.7 else "k")
			elif dist <= r + 0.5:
				g.set(x, ground, z, rim if rng.random() < 0.8 else "k")
				if rng.random() < 0.35:
					g.set(x, ground + 1, z, rim)


# Shared palette: hull plating, scorched ground and crystal veins.
PALETTE = {
	".": "minecraft:air",
	"#": "minecraft:polished_deepslate",
	"t": "minecraft:deepslate_tiles",
	"T": "minecraft:cracked_deepslate_tiles",
	"p": "minecraft:polished_blackstone",
	"o": "minecraft:oxidized_cut_copper",
	"O": "minecraft:weathered_cut_copper",
	"a": "minecraft:amethyst_block",
	"g": "minecraft:tinted_glass",
	"v": "minecraft:iron_bars",
	"i": "minecraft:iron_block",
	"f": "minecraft:polished_blackstone_bricks",
	"c": "minecraft:coarse_dirt",
	"k": "minecraft:blackstone",
	"d": "minecraft:rooted_dirt",
	"s": "minecraft:smooth_basalt",
	"C": CHEST,
	"B": "minecraft:reinforced_deepslate",
	"L": "minecraft:iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]",
	"U": "minecraft:iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]",
	"M": "minecraft:iron_door[facing=south,half=lower,hinge=right,open=false,powered=false]",
	"N": "minecraft:iron_door[facing=south,half=upper,hinge=right,open=false,powered=false]",
	"w": "minecraft:cobweb",
	"S": "minecraft:lodestone",
}


def wreck_hull():
	"""The Wreck: a long, tilted hull nose-down in the ridge, its sealed hatch on the south flank.

	Grid 33 (x) x 12 (y) x 17 (z), 3 layers buried. The hull runs west-east; a furrow of torn ground trails off
	to the west where it skidded in. The south apron in front of the hatch is levelled blackstone with the boss
	marker at its centre.
	"""
	w, h, d, sink = 33, 12, 17, 3
	g = Grid(w, h, d)
	rng = random.Random(2300100)
	ground = sink - 1  # the layer that holds the ground surface blocks
	cx, cz = 18.0, 7.0
	rx, ry, rz = 13.5, 4.6, 5.2

	def centre_y(x):
		# Nose (east) dug in deeper than the tail.
		return sink + 1.8 - (x - cx) * 0.12

	inside = set()
	for x in range(w):
		for y in range(h):
			for z in range(d):
				dx, dy, dz = (x - cx) / rx, (y - centre_y(x)) / ry, (z - cz) / rz
				if dx * dx + dy * dy + dz * dz <= 1.0:
					inside.add((x, y, z))

	def is_shell(x, y, z):
		return any((x + ox, y + oy, z + oz) not in inside
				   for ox, oy, oz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))

	# Clear trees and brush around the hull above ground, so the clearing reads as an impact.
	for x in range(w):
		for z in range(d):
			for y in range(sink, h):
				dx, dz = (x - cx) / (rx + 2.5), (z - cz) / (rz + 3.5)
				if dx * dx + dz * dz <= 1.0:
					g.set(x, y, z, ".")
	# Furrow: torn ground skidding off to the west.
	for x in range(0, 9):
		half = 1 + x // 3
		for z in range(int(cz) - half, int(cz) + half + 1):
			g.set(x, ground, z, "c" if rng.random() < 0.6 else ("d" if rng.random() < 0.5 else "k"))
			if abs(z - cz) == half and rng.random() < 0.5:
				g.set(x, ground + 1, z, "c")

	for (x, y, z) in inside:
		if is_shell(x, y, z):
			r = rng.random()
			c = "#" if r < 0.45 else "t" if r < 0.62 else "T" if r < 0.72 else "o" if r < 0.82 else "O" if r < 0.88 else "a" if r < 0.94 else "p"
			g.set(x, y, z, c)
		elif y < sink:
			g.set(x, y, z, "f")  # buried interior: the deck
		else:
			g.set(x, y, z, ".")
	# Viewports along both flanks at mid height, vents along the spine.
	for x in range(8, 30, 4):
		yv = round(centre_y(x)) + 1
		for z in range(d):
			if (x, yv, z) in inside and is_shell(x, yv, z) and abs(z - cz) > 3:
				g.set(x, yv, z, "g")
	for x in range(9, 28, 3):
		ys = max(y for y in range(h) if (x, y, int(cz)) in inside)
		g.set(x, ys, int(cz), "v")
	# Interior: cobwebs, a crystal growth from the deck, plating debris.
	for x, z in ((12, 6), (21, 8), (25, 7)):
		g.set(x, sink, z, "w")
	g.set(17, sink, 7, "a")
	g.set(17, sink + 1, 7, "a")
	g.set(18, sink, 6, "a")
	g.set(14, sink, 8, "i")
	# Dell Harrow's cache: inside, past the crystal growth, only reachable once the hatch opens.
	g.set(23, sink, 6, "C")
	# The Uplink Socket's placeholder, on the deck where the hatch corridor arrives: the Black Start is fought
	# around it, with the Heart pylons to either side (offsets in arms' finale/settings.json).
	g.set(20, sink, 7, "S")

	# The hatch: two iron doors in the south flank at x = 17..18, cut square through the curve.
	hx0, hx1 = 17, 18
	south = max(z for z in range(d) if (hx0, sink, z) in inside)
	for x in (hx0, hx1):
		for z in range(int(cz), south + 3):
			for y in (sink, sink + 1):
				g.set(x, y, z, ".")
		g.set(x, sink + 2, south, "i")
		g.set(x, sink - 1, south, "i")  # door sill
	g.set(hx0 - 1, sink, south, "i")
	g.set(hx0 - 1, sink + 1, south, "i")
	g.set(hx0 - 1, sink + 2, south, "i")
	g.set(hx1 + 1, sink, south, "i")
	g.set(hx1 + 1, sink + 1, south, "i")
	g.set(hx1 + 1, sink + 2, south, "i")
	g.set(hx0, sink, south, "L")
	g.set(hx0, sink + 1, south, "U")
	g.set(hx1, sink, south, "M")
	g.set(hx1, sink + 1, south, "N")
	# Apron: levelled blackstone south of the hatch, the boss marker at its heart.
	for x in range(hx0 - 4, hx1 + 5):
		for z in range(south + 1, min(d, south + 6)):
			g.set(x, ground, z, "k" if (x + z) % 3 else "s")
			for y in range(sink, h):
				g.set(x, y, z, ".")
	g.set(hx0, ground, south + 3, "B")
	write("wreck_hull", g, PALETTE, sink, foundation="minecraft:blackstone",
		  loot={"C": HULL_LOOT}, markers={"B": "boss", "S": "uplink_socket"})


def crash_site_small():
	"""A torn plate of hull in a shallow crater, the salvage chest half-buried under it."""
	w, h, d, sink = 11, 6, 11, 2
	g = Grid(w, h, d)
	rng = random.Random(11)
	ground = sink - 1
	crater(g, 5, 5, 5, ground, rng)
	# the plate: a tilted slab of plating leaning on its own debris
	for x in range(3, 8):
		for z in range(4, 7):
			y = sink + (x - 3) // 2
			g.set(x, y, z, "#" if (x + z) % 3 else "t")
	g.set(7, sink + 3, 5, "v")
	g.set(3, sink, 3, "o")
	g.set(8, sink, 7, "O")
	g.set(6, sink - 1, 7, "a")
	g.set(2, sink - 1, 6, "a")
	g.set(4, sink, 7, "C")
	g.set(6, sink, 3, "i")
	write("crash_site_small", g, PALETTE, sink, loot={"C": CRASH_LOOT})


def crash_site_pod():
	"""An escape pod tipped on its side in a scorched bowl, hatch torn open, the chest inside."""
	w, h, d, sink = 11, 7, 11, 2
	g = Grid(w, h, d)
	rng = random.Random(22)
	ground = sink - 1
	crater(g, 5, 5, 5, ground, rng)
	# pod: a capsule lying west-east, 7 long, radius 2
	for x in range(2, 9):
		for y in range(sink - 1, sink + 4):
			for z in range(3, 8):
				dy, dz = y - (sink + 1), z - 5
				r = math.hypot(dy, dz)
				cap = 2.2 if 3 <= x <= 7 else 1.5
				if r <= cap:
					shell = r > cap - 1.0 or x in (2, 8)
					g.set(x, y, z, ("o" if (x + y) % 4 else "g") if shell else ".")
	# torn hatch on the south side
	for x in (4, 5, 6):
		for y in (sink, sink + 1):
			g.set(x, y, 7, ".")
	g.set(5, sink - 1, 5, "p")
	g.set(4, sink - 1, 5, "p")
	g.set(6, sink - 1, 5, "p")
	g.set(5, sink, 5, "C")
	g.set(3, sink, 5, "a")
	g.set(7, sink, 6, "w")
	write("crash_site_pod", g, PALETTE, sink, loot={"C": CRASH_LOOT})


def crash_site_debris():
	"""A long debris field: plating strewn along a skid mark, a cracked crate of salvage at the end."""
	w, h, d, sink = 15, 5, 9, 2
	g = Grid(w, h, d)
	rng = random.Random(33)
	ground = sink - 1
	for x in range(w):
		half = 1 + x // 5
		for z in range(4 - half, 4 + half + 1):
			g.set(x, ground, z, "c" if rng.random() < 0.6 else ("k" if rng.random() < 0.5 else "d"))
	for x, z, c in ((2, 4, "#"), (3, 3, "T"), (5, 5, "o"), (6, 4, "#"), (7, 3, "t"), (9, 5, "O"), (10, 4, "#"),
					(11, 6, "a"), (12, 2, "i"), (8, 6, "#")):
		g.set(x, sink, z, c)
	g.set(6, sink + 1, 4, "t")
	g.set(10, sink + 1, 4, "v")
	g.set(13, sink, 4, "C")
	g.set(13, sink, 3, "#")
	g.set(13, sink + 1, 3, "t")
	g.set(14, sink, 4, "#")
	write("crash_site_debris", g, PALETTE, sink, loot={"C": CRASH_LOOT})


if __name__ == "__main__":
	wreck_hull()
	crash_site_small()
	crash_site_pod()
	crash_site_debris()
