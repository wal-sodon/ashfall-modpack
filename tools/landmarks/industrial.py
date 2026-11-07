"""Industrial landmarks: Kiln & Co. Salvage (River Arts, the Abomination's lair) and the four district
substations (plan section 1.6, assets 2 and 5). All are 2x2-chunk multibuildings.

Every substation has the same control room: the Substation Core marker (heavy core, see
kit.SUBSTATION_CORE) on a plinth, switchgear, panels and dead lamps, behind an iron door with buttons.
Which district a core belongs to is not stored in the block: the grid code reads it from ZoneMap at the
core's position, so the four variants need no per-district palette entries.
"""
from lcgen import AIR, Grid, b
from kit import (BOSS_MARKER, SUBSTATION_CORE, barrel, bed, bookcase, carpet, chair, chest, counter, desk, door,
				 double_chest, double_door, fridge, lamp, opening, plant, ramp, secure_door, shelf_wall, sink, slab,
				 stair, stove, switchback, table, toilet, window_strip)
import designs as d

STRIPE_A, STRIPE_B = b("yellow_concrete"), b("black_concrete")


def control_room(g, y, x0, z0, x1, z1, door_x, door_z, door_facing, wall):
	"""The Substation Core room. Walls on the rectangle's border (shared walls may already exist)."""
	g.walls(x0, y + 1, z0, x1, y + 5, z1, wall)
	secure_door(g, door_x, y, door_z, door_facing)
	cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
	# hazard-striped plinth, the core on top, cable chains to the ceiling
	for dx in (-1, 0, 1):
		for dz in (-1, 0, 1):
			g.set(cx + dx, y, cz + dz, STRIPE_A if (dx + dz) % 2 == 0 else STRIPE_B)
	g.set(cx, y + 1, cz, b("polished_blackstone_slab", type="bottom", waterlogged=False))
	g.set(cx, y + 2, cz, SUBSTATION_CORE)
	for dy in (3, 4, 5):
		g.set(cx, y + dy, cz, b("iron_chain", axis="y"))
	# panels along the back wall: switchgear cabinets with gauges and levers
	for x in range(x0 + 1, x1):
		if x == door_x:
			continue
		g.set(x, y + 1, z0 + 1, b("iron_block"))
		g.set(x, y + 2, z0 + 1, b("observer", facing="south", powered=False) if x % 2 else b("target", power=0))
		g.set(x, y + 3, z0 + 2, b("lever", face="wall", facing="south", powered=False)) if x % 3 == 0 else None
	# desk and a parts barrel in the two front corners furthest from the door
	corners = sorted(((x0 + 1, z1 - 1), (x1 - 1, z1 - 1), (x0 + 1, z0 + 2), (x1 - 1, z0 + 2)),
					 key=lambda c: -abs(c[0] - door_x) - abs(c[1] - door_z))
	(dx_, dz_), (bx_, bz_) = corners[0], corners[1]
	g.set(dx_, y + 1, dz_, slab("spruce", "top"))
	g.set(bx_, y + 1, bz_, barrel(d.ELECTRONICS, "up"))
	lamp(g, cx - 1, y, cz)
	lamp(g, cx + 1, y, cz)
	return cx, cz


def transformer(g, x, y, z, h=3):
	"""A pad-mounted transformer: iron body, radiator fins (iron bars), bushings (lightning rods)."""
	g.fill(x, y + 1, z, x + 1, y + h, z + 1, b("iron_block"))
	g.fill(x - 1, y + 1, z, x - 1, y + h - 1, z + 1, b("iron_bars"))
	g.fill(x + 2, y + 1, z, x + 2, y + h - 1, z + 1, b("iron_bars"))
	g.set(x, y + h + 1, z, b("lightning_rod", facing="up", powered=False))
	g.set(x + 1, y + h + 1, z + 1, b("lightning_rod", facing="up", powered=False))


# =================================================================================================
# Kiln & Co. Salvage: an old brick kiln works turned scrapyard. Double-height hall = boss lair.
# =================================================================================================
def kiln_warehouse():
	g = Grid(32, 32, 24)
	F0, F1, RF = 0, 6, 12
	brick, trim = b("bricks"), b("dark_oak_planks")
	g.fill(0, F0, 0, 31, F0, 31, b("stone_bricks"))
	g.walls(0, 1, 0, 31, 11, 31, brick)
	g.walls(0, 6, 0, 31, 6, 31, trim)
	g.fill(0, RF, 0, 31, RF, 31, b("smooth_stone"))
	g.walls(0, RF, 0, 31, RF, 31, trim)
	d.parapet(g, 0, 0, 31, 31, RF + 1, brick, slab("stone_brick"))
	for x in (8, 16, 23):
		g.fill(x, RF, 2, x, RF, 21, b("glass"))
	# tall arched windows on the long walls, an orange sign band, the loading door and office door
	for z0 in range(3, 22, 5):
		window_strip(g, 0, z0, 0, z0 + 1, F0, b("glass_pane"), sill=3, height=6)
		window_strip(g, 31, z0, 31, z0 + 1, F0, b("glass_pane"), sill=3, height=6)
	g.fill(14, 7, 31, 25, 8, 31, b("orange_terracotta"))
	g.fill(16, 1, 31, 20, 4, 31, AIR)
	g.fill(16, 5, 31, 20, 5, 31, b("iron_bars"))
	door(g, 6, F0, 31, "south", "dark_oak")
	window_strip(g, 2, 31, 4, 31, F0, b("glass_pane"))
	window_strip(g, 8, 31, 10, 31, F0, b("glass_pane"))
	window_strip(g, 2, 31, 10, 31, F1, b("glass_pane"), every=2)

	# Office block x1..12 z24..30 on two floors; stairs x1..3 rising north from the z=29 corridor.
	g.fill(1, F1, 24, 12, F1, 30, b("spruce_planks"))
	g.fill(1, 1, 24, 13, 11, 24, brick)
	g.fill(13, 1, 24, 13, 11, 30, brick)
	for y in (F0, F1):
		g.fill(4, y + 1, 25, 4, y + 5, 28, b("spruce_planks"))
	switchback(g, 3, 28, F0, "north", "west", "spruce", b("spruce_planks"), b("spruce_fence"), up=True)
	switchback(g, 3, 28, F1, "north", "west", "spruce", b("spruce_planks"), b("spruce_fence"), up=False)
	y = F0
	g.fill(5, y + 1, 28, 12, y + 5, 28, b("spruce_planks"))
	door(g, 8, y, 28, "south", "spruce")
	door(g, 9, y, 24, "north", "spruce")
	door(g, 12, y, 30, "east", "spruce") if False else None
	opening(g, 13, y, 29)
	opening(g, 13, y, 30)
	counter(g, 6, y, 26, 9, 26, "south")
	chair(g, 7, y, 25, "south", "spruce")
	g.set(12, y + 1, 25, barrel(d.WORKSHOP, "west"))
	g.set(12, y + 1, 26, barrel(d.WAREHOUSE, "west"))
	g.set(5, y + 1, 25, b("bookshelf"))
	plant(g, 12, y, 27)
	lamp(g, 8, y, 26)
	chair(g, 10, y, 30, "north", "spruce")
	chair(g, 11, y, 30, "north", "spruce")
	lamp(g, 8, y, 29)
	y = F1
	# manager's office and the viewing gallery over the hall
	g.fill(5, y + 1, 24, 12, y + 1, 24, b("spruce_fence"))
	g.fill(5, y + 2, 24, 12, y + 5, 24, AIR)
	desk(g, 9, y, 29, "south", width=2, along="east")
	g.set(12, y + 1, 30, barrel(d.OFFICE, "west"))
	g.set(5, y + 1, 30, chest(d.KILN_OFFICE, "east"))  # the manager's cache: work order 0417
	bookcase(g, 12, y, 26, 12, 27, 2)
	d.couch(g, 6, y, 27, "east", "south", 2)
	carpet(g, 7, y, 26, 10, 28, "brown")
	lamp(g, 9, y, 27)

	# Hall: three brick kilns on the north wall, two of them with chimneys through the roof
	y = F0
	for kx in (3, 13, 23):
		g.fill(kx, 1, 1, kx + 4, 4, 4, brick)
		g.fill(kx + 1, 5, 1, kx + 3, 5, 3, brick)
		g.fill(kx + 1, 1, 4, kx + 3, 3, 4, AIR)
		g.fill(kx + 1, 1, 2, kx + 3, 3, 3, AIR)
		g.set(kx + 2, 1, 2, b("blast_furnace", facing="south", lit=False))
		g.set(kx + 1, 1, 3, b("cauldron"))
		g.fill(kx + 1, 0, 2, kx + 3, 0, 3, b("blackstone"))
	for kx in (3, 23):
		g.fill(kx + 1, 6, 1, kx + 3, 23, 3, brick)
		g.fill(kx + 2, 6, 2, kx + 2, 23, 2, AIR)
		g.set(kx + 2, 5, 2, b("iron_bars"))
	g.fill(14, 6, 1, 16, 11, 3, b("bricks"))
	# gantry crane across the hall with a hook over the casting floor
	for x in (1, 30):
		g.fill(x, 1, 12, x, 9, 12, b("iron_block"))
	g.fill(1, 10, 12, 30, 10, 12, b("iron_block"))
	for dy in range(5, 10):
		g.set(15, dy, 12, b("iron_chain", axis="y"))
	g.set(15, 4, 12, b("anvil", facing="east"))
	# the casting floor (lair): blackstone ring, crucibles, slag and bones, the boss marker at its heart
	for x in range(10, 22):
		for z in range(8, 18):
			if (x - 15.5) ** 2 + (z - 12.5) ** 2 <= 26:
				g.set(x, 0, z, b("polished_blackstone") if (x + z) % 3 else b("basalt", axis="y"))
	g.set(15, 0, 12, BOSS_MARKER)
	g.set(16, 0, 13, BOSS_MARKER)
	for x, z in ((11, 10), (20, 15), (12, 16), (19, 9)):
		g.set(x, 1, z, b("cauldron"))
	for x, z in ((10, 13), (21, 12), (14, 8), (17, 17)):
		g.set(x, 1, z, b("bone_block", axis="y"))
	g.set(13, 1, 15, b("anvil", facing="north"))
	for x, z in ((9, 9), (22, 16), (16, 7), (12, 18), (19, 11), (7, 14)):
		g.set(x, 1, z, b("cobweb"))
	g.fill(13, 9, 10, 18, 9, 10, b("cobweb"))
	# scrap heaps
	for x0, z0, mats in ((3, 15, ("iron_block", "raw_iron_block", "exposed_copper")),
						 (5, 7, ("chiseled_copper", "iron_block", "anvil")),
						 (22, 19, ("weathered_copper", "raw_iron_block", "iron_block"))):
		for i, (dx, dz, h) in enumerate(((0, 0, 3), (1, 0, 2), (0, 1, 2), (2, 1, 1), (1, 2, 1), (-1, 1, 1), (1, 1, 3))):
			for dy in range(1, h + 1):
				m = mats[(i + dy) % len(mats)]
				g.set(x0 + dx, dy, z0 + dz, b(m, facing="north") if m == "anvil" else b(m))
	# salvage racks on the east wall
	for z in range(2, 23):
		if z in (11, 12, 13):
			continue
		g.set(29, 1, z, barrel(d.WAREHOUSE if z % 4 == 0 else d.WORKSHOP, "west") if z % 2 == 0 else b("barrel", facing="up", open=False))
		g.set(29, 2, z, b("barrel", facing="up", open=False))
		g.set(29, 3, z, slab("spruce", "top"))
		g.set(30, 4, z, b("chest", facing="west", type="single", waterlogged=False)) if False else None
	shelf_wall(g, 30, 0, 2, 30, 22, "west", "spruce", rows=(4, 5))
	# workbenches on the west wall
	g.set(1, 1, 9, b("crafting_table"))
	g.set(1, 1, 10, b("smithing_table"))
	g.set(1, 1, 11, b("grindstone", face="floor", facing="east"))
	g.set(1, 1, 13, b("stonecutter", facing="east"))
	double_chest(g, 1, 0, 14, "east", "south", d.WORKSHOP)
	# loading area: pallets, crates, a dead forklift
	for x0, z0 in ((15, 26), (22, 27), (26, 25)):
		g.fill(x0, 1, z0, x0 + 1, 1, z0 + 1, slab("spruce"))
	g.set(15, 2, 26, barrel(d.WAREHOUSE, "up"))
	g.set(16, 2, 27, b("barrel", facing="up", open=False))
	g.set(22, 2, 27, barrel(d.WAREHOUSE, "up"))
	g.fill(26, 2, 25, 27, 3, 26, b("barrel", facing="up", open=False))
	g.fill(19, 1, 21, 20, 1, 22, b("yellow_concrete"))
	g.set(19, 2, 22, b("yellow_concrete"))
	g.fill(19, 1, 20, 20, 1, 20, b("iron_bars"))
	g.fill(19, 2, 20, 20, 3, 20, b("iron_bars"))
	g.set(20, 2, 21, stair("dark_oak", "south"))
	for x, z in ((8, 6), (23, 6), (8, 19), (23, 19), (15, 22)):
		lamp(g, x, 0, z, ceiling=11)
	return {"grid": g, "cx": 2, "cz": 2, "cellars": 0, "floors": 2, "filler": "minecraft:bricks",
			"entry": (18, 1, 30),
			"must_reach": [(6, 1, 30), (11, 1, 27), (15, 1, 14), (28, 1, 12), (9, 7, 27), (2, 1, 12)]}


# =================================================================================================
# River Arts pump house: brick pump hall over a water channel; the channel runs through the cellar.
# =================================================================================================
def substation_river_arts():
	g = Grid(32, 32, 22)
	C0, F0, RF = 0, 6, 12
	brick, base = b("bricks"), b("stone_bricks")
	g.fill(0, C0, 0, 31, C0, 31, b("stone_bricks"))
	g.walls(0, 1, 0, 31, 5, 31, base)
	d.shell(g, 0, 0, 31, 31, 7, 11, brick, (F0,), b("stone_bricks"), band=base)
	g.fill(0, RF, 0, 31, RF, 31, b("smooth_stone"))
	g.walls(0, RF, 0, 31, RF, 31, base)
	d.parapet(g, 0, 0, 31, 31, RF + 1, brick, slab("stone_brick"))
	for z0 in range(3, 29, 6):
		window_strip(g, 0, z0, 0, z0 + 1, F0, b("glass_pane"), sill=1, height=4)
		window_strip(g, 31, z0, 31, z0 + 1, F0, b("glass_pane"), sill=1, height=4)
	for x0 in range(3, 29, 6):
		if x0 in (15,):
			continue
		window_strip(g, x0, 31, x0 + 1, 31, F0, b("glass_pane"), sill=1, height=4)
		window_strip(g, x0, 0, x0 + 1, 0, F0, b("glass_pane"), sill=1, height=4)
	double_door(g, 15, F0, 31, "south", "east", "spruce")
	# the channel: two blocks of water through the cellar, grated where it passes the outer walls
	g.fill(0, 0, 14, 31, 1, 17, b("water", level=0))
	for x in (0, 31):
		g.fill(x, 0, 14, x, 4, 17, b("iron_bars"))
	g.fill(1, 1, 13, 30, 1, 13, b("stone_bricks"))
	g.fill(1, 1, 18, 30, 1, 18, b("stone_bricks"))
	g.fill(1, 2, 13, 30, 2, 13, b("iron_bars"))
	g.fill(1, 2, 18, 30, 2, 18, b("iron_bars"))
	for bx in (8, 23):
		g.fill(bx, 1, 13, bx + 1, 2, 18, AIR)
		g.fill(bx, 1, 13, bx + 1, 1, 18, slab("stone_brick"))
		g.fill(bx - 1, 2, 14, bx - 1, 2, 17, b("iron_bars"))
		g.fill(bx + 2, 2, 14, bx + 2, 2, 17, b("iron_bars"))
	# the hall floor is open over the channel, with railings and a footbridge
	g.fill(5, F0, 14, 26, F0, 17, AIR)
	g.fill(15, F0, 14, 16, F0, 17, b("stone_bricks"))
	g.fill(5, F0 + 1, 13, 26, F0 + 1, 13, b("spruce_fence"))
	g.fill(5, F0 + 1, 18, 26, F0 + 1, 18, b("spruce_fence"))
	g.fill(15, F0 + 1, 13, 16, F0 + 1, 13, AIR)
	g.fill(15, F0 + 1, 18, 16, F0 + 1, 18, AIR)
	g.fill(14, F0 + 1, 14, 14, F0 + 1, 17, b("spruce_fence"))
	g.fill(17, F0 + 1, 14, 17, F0 + 1, 17, b("spruce_fence"))
	# stairs: cellar to hall, x1..3, rising north from the z=6 cellar corridor
	g.fill(4, C0 + 1, 2, 4, C0 + 5, 5, base)
	g.fill(4, F0 + 1, 2, 4, F0 + 5, 5, b("spruce_planks"))
	g.fill(1, C0 + 1, 1, 3, C0 + 5, 1, base)
	g.fill(1, F0 + 1, 1, 3, F0 + 5, 1, b("spruce_planks"))
	switchback(g, 3, 5, C0, "north", "west", "stone_brick", base, b("iron_bars"), up=True)
	switchback(g, 3, 5, F0, "north", "west", "stone_brick", base, b("iron_bars"), up=False)
	# cellar: pumps on the north walkway dipping into the channel, filter tanks on the south side
	y = C0
	for px in (7, 12, 19, 24):
		g.fill(px, y + 1, 10, px + 1, y + 3, 11, b("iron_block"))
		g.set(px, y + 1, 12, b("piston", facing="down", extended=False))
		g.set(px + 1, y + 1, 12, b("piston", facing="down", extended=False))
		g.fill(px, y + 4, 11, px, y + 5, 11, b("iron_bars"))
		g.set(px, F0, 11, b("iron_bars"))
	for tx in (5, 11, 20, 26):
		g.fill(tx, y + 1, 22, tx + 2, y + 3, 24, b("light_gray_concrete"))
		g.fill(tx + 1, y + 4, 23, tx + 1, y + 4, 23, b("iron_trapdoor", facing="north", half="bottom", open=False, powered=False, waterlogged=False))
	g.fill(1, y + 4, 26, 30, y + 4, 26, b("iron_chain", axis="x"))
	g.set(1, y + 1, 5, barrel(d.WORKSHOP, "east"))
	for x in (28, 29, 30):
		g.set(x, y + 1, 30, barrel(d.FUEL, "north"))
	g.set(30, y + 1, 28, b("crafting_table"))
	for x, z in ((10, 7), (21, 7), (10, 27), (21, 27)):
		lamp(g, x, y, z)
	# hall: motors over the pumps, a workshop corner and the control room (north-east)
	y = F0
	for px in (7, 12, 19, 24):
		g.fill(px, y + 1, 8, px + 1, y + 2, 10, b("light_gray_concrete"))
		g.set(px, y + 3, 9, b("iron_block"))
		g.set(px + 1, y + 1, 11, b("lever", face="floor", facing="south", powered=False))
	control_room(g, y, 21, 0, 31, 6, 25, 6, "south", brick)
	for x in (2, 3):
		g.set(x, y + 1, 30, barrel(d.WORKSHOP, "north"))
	g.set(1, y + 1, 29, b("smithing_table"))
	g.set(1, y + 1, 28, b("anvil", facing="east"))
	table(g, 5, y, 26, 7, 27, "spruce")
	chair(g, 6, y, 25, "south", "spruce")
	chair(g, 6, y, 28, "north", "spruce")
	transformer(g, 26, y, 24)
	transformer(g, 26, y, 20)
	g.fill(24, y + 1, 19, 29, y + 1, 19, b("iron_bars"))
	g.fill(24, y + 1, 19, 24, y + 1, 27, b("iron_bars"))
	g.fill(24, y + 1, 23, 24, y + 1, 23, AIR)
	for x, z in ((10, 4), (10, 24), (21, 24), (16, 10)):
		lamp(g, x, y, z)
	# roof: an old brick chimney and a vent
	for dy in range(1, 10):
		g.fill(27, RF + dy, 26, 28, RF + dy, 27, brick)
	g.fill(6, RF + 1, 6, 8, RF + 2, 8, b("light_gray_concrete"))
	return {"grid": g, "cx": 2, "cz": 2, "cellars": 1, "floors": 1, "filler": "minecraft:stone_bricks",
			"entry": (15, F0 + 1, 30),
			"must_reach": [(24, F0 + 1, 4), (10, C0 + 1, 7), (10, C0 + 1, 27), (6, F0 + 1, 24), (16, F0 + 1, 15)]}


# =================================================================================================
# South Slope brewery yard: two-storey brewhouse on the north half, walled yard with outdoor
# fermenters and the fenced transformer compound + control hut on the south half.
# =================================================================================================
def substation_south_slope():
	g = Grid(32, 32, 20)
	F0, F1, RF = 0, 6, 12
	brick, base = b("bricks"), b("stone_bricks")
	g.fill(0, F0, 0, 31, F0, 31, b("cobblestone"))
	g.fill(1, F0, 15, 30, F0, 30, b("stone_bricks"))
	# brewhouse x0..31 z0..13
	d.shell(g, 0, 0, 31, 13, 1, 11, brick, (F0, F1), b("spruce_planks"), band=base)
	g.fill(0, F0, 0, 31, F0, 13, b("polished_andesite"))
	g.fill(0, RF, 0, 31, RF, 13, b("smooth_stone"))
	g.walls(0, RF, 0, 31, RF, 13, base)
	d.parapet(g, 0, 0, 31, 13, RF + 1, brick, slab("stone_brick"))
	for x0 in range(2, 30, 4):
		window_strip(g, x0, 0, x0 + 1, 0, F0, b("glass_pane"), sill=2, height=2)
		window_strip(g, x0, 0, x0 + 1, 0, F1, b("glass_pane"), sill=2, height=2)
		if x0 not in (6, 22):
			window_strip(g, x0, 13, x0 + 1, 13, F1, b("glass_pane"), sill=2, height=2)
	# yard wall with a gate on the street
	g.walls(0, 1, 13, 31, 4, 31, brick)
	g.walls(0, 5, 14, 31, 5, 31, slab("stone_brick"))
	g.fill(13, 1, 31, 18, 4, 31, AIR)
	g.fill(13, 5, 31, 18, 5, 31, AIR)
	g.fill(12, 1, 31, 12, 6, 31, base)
	g.fill(19, 1, 31, 19, 6, 31, base)
	double_door(g, 7, F0, 13, "south", "east", "spruce")
	door(g, 24, F0, 13, "south", "spruce")
	# brewhouse ground floor: mash tun, kettle, fermenters, keg racks
	y = F0
	for tx, mat in ((3, "cut_copper"), (8, "exposed_cut_copper"), (13, "cut_copper")):
		g.fill(tx, y + 1, 3, tx + 2, y + 4, 5, b(mat))
		g.set(tx + 1, y + 5, 4, b("lightning_rod", facing="up", powered=False))
		g.set(tx + 1, y + 1, 6, b("lever", face="wall", facing="south", powered=False)) if False else None
		g.set(tx + 1, y + 2, 6, b("copper_trapdoor", facing="south", half="top", open=True, powered=False, waterlogged=False))
	g.fill(3, y + 3, 2, 16, y + 3, 2, b("iron_chain", axis="x"))
	for x in range(18, 30):
		g.set(x, y + 1, 1, barrel(d.BREWERY, "south") if x % 3 == 0 else b("barrel", facing="south", open=False))
		g.set(x, y + 2, 1, b("barrel", facing="south", open=False))
	for x in range(18, 30, 2):
		g.set(x, y + 1, 4, b("barrel", facing="up", open=False))
		g.set(x, y + 1, 5, barrel(d.BREWERY, "up") if x % 4 == 0 else b("barrel", facing="up", open=False))
	# tap bar by the yard door
	counter(g, 18, y, 10, 22, 10, "south")
	g.fill(18, y + 1, 9, 18, y + 1, 8, b("dark_oak_planks"))
	for x in (19, 21):
		g.set(x, y + 2, 10, b("tripwire_hook", facing="south", attached=False, powered=False))
	for x in (19, 21):
		chair(g, x, y, 12, "north", "dark_oak")
	shelf_wall(g, 19, y, 7, 22, 7, "south", "dark_oak", rows=(2, 3))
	for x, z in ((8, 9), (24, 6)):
		lamp(g, x, y, z)
	# stairs east end x28..30 rising north from the z=12 corridor
	for yy in (F0, F1):
		g.fill(27, yy + 1, 8, 27, yy + 5, 11, b("spruce_planks"))
		g.fill(27, yy + 1, 7, 30, yy + 5, 7, b("spruce_planks"))
	switchback(g, 30, 11, F0, "north", "west", "spruce", b("spruce_planks"), b("spruce_fence"), up=True)
	switchback(g, 30, 11, F1, "north", "west", "spruce", b("spruce_planks"), b("spruce_fence"), up=False)
	# first floor: malt store, office, a walkway round the tank tops
	y = F1
	g.fill(2, y, 2, 17, y, 6, AIR)
	g.fill(2, y + 1, 7, 17, y + 1, 7, b("spruce_fence"))
	g.fill(1, y + 1, 2, 1, y + 1, 6, b("spruce_fence"))
	g.fill(18, y + 1, 2, 18, y + 1, 6, b("spruce_fence"))
	for x in range(20, 27):
		g.fill(x, y + 1, 1, x, y + 2, 2, b("hay_block", axis="y"))
	g.set(20, y + 1, 3, barrel(d.BREWERY, "south"))
	g.fill(9, y + 1, 9, 9, y + 5, 12, b("spruce_planks"))
	door(g, 9, y, 10, "east", "spruce")
	desk(g, 3, y, 12, "south", width=2, along="east")
	g.set(1, y + 1, 9, barrel(d.OFFICE, "east"))
	bookcase(g, 8, y, 12, 8, 12, 2)
	lamp(g, 5, y, 10)
	lamp(g, 15, y, 10)
	# yard: outdoor fermenters, a delivery truck, pallets
	y = F0
	for tx in (2, 7):
		for tz in (16, 22):
			g.fill(tx, y + 1, tz, tx + 2, y + 6, tz + 2, b("weathered_cut_copper") if tz == 16 else b("exposed_cut_copper"))
			g.set(tx + 1, y + 7, tz + 1, b("lightning_rod", facing="up", powered=False))
			g.set(tx, y + 1, tz + 3, b("iron_bars"))
	g.fill(2, y + 4, 19, 9, y + 4, 19, b("iron_chain", axis="x"))
	# truck
	g.fill(13, y + 1, 18, 15, y + 1, 23, b("white_concrete"))
	g.fill(13, y + 2, 18, 15, y + 3, 21, b("white_concrete"))
	g.fill(13, y + 2, 22, 15, y + 2, 23, b("red_concrete"))
	g.fill(13, y + 3, 22, 15, y + 3, 22, b("black_stained_glass"))
	for x in (12, 16):
		for z in (19, 22):
			g.set(x, y + 1, z, b("black_concrete"))
	for x0, z0 in ((3, 27), (8, 28)):
		g.fill(x0, y + 1, z0, x0 + 1, y + 1, z0 + 1, slab("spruce"))
		g.set(x0, y + 2, z0, barrel(d.BREWERY, "up"))
		g.set(x0 + 1, y + 2, z0 + 1, b("barrel", facing="up", open=False))
	# transformer compound x20..30 z17..30 behind iron bars, control hut inside
	g.fill(20, y + 1, 17, 30, y + 3, 17, b("iron_bars"))
	g.fill(20, y + 1, 17, 20, y + 3, 30, b("iron_bars"))
	g.fill(20, y + 1, 21, 20, y + 3, 22, AIR)
	transformer(g, 22, y, 18)
	transformer(g, 26, y, 18)
	for z in range(17, 31):
		g.set(20, y, z, STRIPE_A if z % 2 else STRIPE_B)
	g.fill(23, y + 1, 23, 30, y + 5, 30, b("light_gray_concrete"))
	g.fill(23, y + 6, 23, 30, y + 6, 30, slab("smooth_stone"))
	g.fill(24, y + 1, 24, 29, y + 5, 29, AIR)
	control_room(g, y, 23, 23, 30, 30, 23, 26, "west", b("light_gray_concrete"))
	lamp(g, 15, y, 27, ceiling=6) if False else None
	return {"grid": g, "cx": 2, "cz": 2, "cellars": 0, "floors": 2, "filler": "minecraft:cobblestone",
			"entry": (15, F0 + 1, 30),
			"must_reach": [(10, F0 + 1, 9), (25, F0 + 1, 8), (12, F1 + 1, 10), (4, F1 + 1, 10), (26, F0 + 1, 25),
						   (21, F0 + 1, 20)]}


# =================================================================================================
# Downtown parking deck: three decks and a roof deck, scissor ramps, a stair tower, the substation
# room on the ground floor (south-east).
# =================================================================================================
def car(g, x, y, z, along, color):
	"""A parked car three long: body, dark cabin glass, a darker roof."""
	from lcgen import DIRS
	dx, dz = DIRS[along]
	px, pz = (1, 0) if dz else (0, 1)
	for i in range(3):
		for w in range(2):
			cx, cz = x + dx * i + px * w, z + dz * i + pz * w
			g.set(cx, y + 1, cz, b(f"{color}_concrete"))
			if i == 1:
				g.set(cx, y + 2, cz, b("black_stained_glass"))


def substation_downtown():
	g = Grid(32, 32, 24)
	FL = (0, 6, 12)
	RF = 18
	conc = b("light_gray_concrete")
	floor = b("gray_concrete")
	g.fill(0, 0, 0, 31, 0, 31, floor)
	for y in (6, 12, 18):
		g.fill(0, y, 0, 31, y, 31, floor)
	# pillars and the open-sided parapet (concrete kerb, bars above)
	for y in FL:
		for x in (0, 8, 16, 23, 31):
			for z in (0, 8, 16, 23, 31):
				g.fill(x, y + 1, z, x, y + 5, z, conc)
		g.walls(0, y + 1, 0, 31, y + 1, 31, conc)
		g.walls(0, y + 2, 0, 31, y + 2, 31, b("iron_bars"))
		for x in (0, 8, 16, 23, 31):
			for z in (0, 8, 16, 23, 31):
				g.fill(x, y + 1, z, x, y + 5, z, conc)
	g.walls(0, RF + 1, 0, 31, RF + 1, 31, conc)
	g.walls(0, RF + 2, 0, 31, RF + 2, 31, b("iron_bars"))
	# street entrance on the ground floor
	g.fill(9, 1, 31, 15, 2, 31, AIR)
	g.fill(9, 0, 31, 15, 0, 31, b("yellow_concrete"))
	g.set(12, 1, 30, b("oak_fence"))
	g.set(12, 2, 29, b("oak_fence"))
	# painted bays: stripes in the deck slab
	for y in (0, 6, 12, 18):
		for x in range(5, 23, 3):
			g.fill(x, y, 1, x, y, 5, b("white_concrete"))
			g.fill(x, y, 26, x, y, 30, b("white_concrete")) if y > 0 else None
		for z in range(10, 22, 3):
			g.fill(9, y, z, 13, y, z, b("white_concrete"))
	# scissor ramps: lane A x25..27 rises north (even decks), lane B x28..30 rises south (odd decks)
	for i, y in enumerate((0, 6, 12)):
		if i % 2 == 0:
			g.fill(25, y + 6, 10, 27, y + 6, 16, AIR)
			ramp(g, 25, y, 21, "north", "east", 3, 12, slab("smooth_stone", "top"), slab("smooth_stone", "bottom"))
			top = y + 6
			g.fill(24, top + 1, 11, 24, top + 1, 16, b("iron_bars"))
			g.fill(25, top + 1, 17, 27, top + 1, 17, b("iron_bars"))
		else:
			g.fill(28, y + 6, 15, 30, y + 6, 21, AIR)
			ramp(g, 28, y, 10, "south", "east", 3, 12, slab("smooth_stone", "top"), slab("smooth_stone", "bottom"))
			top = y + 6
			g.fill(28, top + 1, 14, 30, top + 1, 14, b("iron_bars"))
			g.fill(31, top + 1, 15, 31, top + 1, 21, conc)
	for y in FL + (RF,):
		g.fill(25, y + 1, 9, 25, y + 1, 9, AIR)
	# pedestrian stair tower x1..3 z1..4, rising north from z=5
	for y in FL:
		g.fill(4, y + 1, 1, 4, y + 5, 4, conc)
		opening(g, 4, y, 4) if False else None
	for i, y in enumerate(FL):
		switchback(g, 3, 4, y, "north", "west", "smooth_stone" if False else "stone", b("smooth_stone"), b("iron_bars"),
				   up=True, from_below=i > 0)
	switchback(g, 3, 4, RF, "north", "west", "stone", b("smooth_stone"), b("iron_bars"), up=False)
	g.walls(0, RF + 1, 0, 4, RF + 3, 6, conc)
	g.fill(0, RF + 4, 0, 4, RF + 4, 6, slab("smooth_stone"))
	door(g, 2, RF, 6, "south", "iron")
	g.openable.update({(2, RF + 1, 6), (2, RF + 2, 6)})
	g.set(3, RF + 2, 7, b("stone_button", face="wall", facing="south", powered=False))
	g.set(3, RF + 2, 5, b("stone_button", face="wall", facing="north", powered=False))
	# cars, some burnt out
	cars = {0: ((6, 1, "south", "red"), (12, 1, "south", "black"), (18, 2, "south", "blue"), (10, 18, "east", "white")),
			6: ((6, 2, "south", "white"), (15, 1, "south", "black"), (21, 1, "south", "gray"), (6, 27, "north", "yellow"),
				(18, 27, "north", "red"), (10, 12, "east", "light_blue")),
			12: ((9, 1, "south", "green"), (6, 27, "north", "black"), (12, 27, "north", "white"), (10, 15, "east", "red")),
			18: ((12, 2, "south", "black"), (15, 27, "north", "gray"), (9, 20, "east", "white"))}
	for y, lst in cars.items():
		for x, z, along, color in lst:
			car(g, x, y, z, along, color)
	for y in FL:
		for x, z in ((6, 10), (14, 22), (20, 6)):
			lamp(g, x, y, z)
	# ground floor: ticket booth and the substation room (south-east)
	y = 0
	g.fill(17, y + 1, 25, 18, y + 3, 27, b("white_concrete"))
	g.fill(17, y + 2, 26, 17, y + 2, 26, b("glass_pane"))
	g.fill(17, y + 4, 24, 19, y + 4, 28, slab("smooth_stone"))
	control_room(g, y, 20, 23, 30, 30, 24, 23, "north", b("light_gray_concrete"))
	transformer(g, 28, y, 25)
	g.set(27, y + 1, 28, barrel(d.FUEL, "west"))
	# a few loot crates in the bays
	g.set(1, 1, 29, barrel(d.GENERIC, "east"))
	g.set(30, 7, 3, barrel(d.GENERIC, "west"))
	g.set(14, 13, 30, barrel(d.GENERIC, "north"))
	return {"grid": g, "cx": 2, "cz": 2, "cellars": 0, "floors": 3, "filler": "minecraft:gray_concrete",
			"entry": (12, 1, 28),
			"must_reach": [(24, 1, 26), (12, 7, 20), (12, 13, 20), (12, 19, 20), (26, 7, 19), (29, 13, 12), (2, 19, 8), (26, 3, 17), (29, 9, 14)]}


# =================================================================================================
# Mission generator plant: double-height engine hall with three gensets, the control room on the
# office block's first floor looking over the hall.
# =================================================================================================
def substation_mission():
	g = Grid(32, 32, 24)
	F0, F1, RF = 0, 6, 12
	wall, band = b("light_gray_concrete"), b("cyan_terracotta")
	g.fill(0, F0, 0, 31, F0, 31, b("polished_andesite"))
	g.walls(0, 1, 0, 31, 11, 31, wall)
	g.walls(0, F1, 0, 31, F1, 31, band)
	g.fill(0, RF, 0, 31, RF, 31, b("smooth_stone"))
	g.walls(0, RF, 0, 31, RF, 31, band)
	d.parapet(g, 0, 0, 31, 31, RF + 1, wall, slab("smooth_stone"))
	for z0 in range(3, 20, 4):
		window_strip(g, 0, z0, 0, z0 + 1, F0, b("glass_pane"), sill=7, height=3)
	for x0 in range(3, 29, 4):
		window_strip(g, x0, 0, x0 + 1, 0, F0, b("glass_pane"), sill=7, height=3)
		window_strip(g, x0, 31, x0 + 1, 31, F1, b("glass_pane"))
	# roller door (open) on the east wall into the hall, office door on the street
	g.fill(31, 1, 9, 31, 4, 13, AIR)
	g.fill(31, 5, 9, 31, 5, 13, b("iron_trapdoor", facing="west", half="top", open=False, powered=False, waterlogged=False))
	double_door(g, 15, F0, 31, "south", "east", "spruce")
	# office block z24..30 on two floors; the hall z1..22 is open to the roof
	g.fill(1, F1, 24, 30, F1, 30, b("polished_andesite"))
	g.fill(1, 1, 23, 30, 11, 23, wall)
	for y in (F0, F1):
		g.fill(4, y + 1, 25, 4, y + 5, 28, wall)
	switchback(g, 3, 28, F0, "north", "west", "stone", b("smooth_stone"), b("iron_bars"), up=True)
	switchback(g, 3, 28, F1, "north", "west", "stone", b("smooth_stone"), b("iron_bars"), up=False)
	y = F0
	g.fill(5, y + 1, 28, 30, y + 5, 28, wall)
	door(g, 14, y, 23, "north", "spruce")
	door(g, 8, y, 28, "south", "spruce")
	door(g, 22, y, 28, "south", "spruce")
	opening(g, 15, y, 28) if False else None
	g.fill(14, y + 1, 28, 17, y + 3, 28, AIR)
	# lockers + workshop (x5..18), fuel store (x19..30)
	g.fill(18, y + 1, 24, 18, y + 5, 27, wall)
	for x in range(5, 14):
		g.set(x, y + 1, 24, barrel(d.WORKSHOP, "south") if x % 4 == 1 else b("iron_block"))
		g.set(x, y + 2, 24, b("iron_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False))
	g.set(15, y + 1, 24, b("crafting_table"))
	g.set(16, y + 1, 24, b("anvil", facing="east"))
	g.set(17, y + 1, 24, b("grindstone", face="floor", facing="south"))
	chair(g, 8, y, 26, "north", "spruce")
	chair(g, 11, y, 26, "north", "spruce")
	door(g, 18, y, 26, "east", "spruce")
	for x in range(19, 31):
		g.set(x, y + 1, 24, barrel(d.FUEL, "south") if x % 3 == 0 else b("barrel", facing="south", open=False))
		if x != 22:  # keep the gangway to the x=22 door clear
			g.set(x, y + 1, 27, b("barrel", facing="north", open=False) if x % 2 else barrel(d.FUEL, "north"))
	lamp(g, 10, y, 25)
	lamp(g, 24, y, 25)
	lamp(g, 15, y, 29)
	# first floor: control room (core) with glazing over the hall, and an office
	y = F1
	g.fill(6, y + 2, 23, 27, y + 4, 23, b("glass_pane"))
	control_room(g, y, 5, 23, 31, 30, 5, 29, "west", wall)
	g.fill(6, y + 2, 23, 27, y + 4, 23, b("glass_pane"))
	g.fill(6, y + 1, 24, 28, y + 1, 24, b("iron_block"))
	for x in range(6, 29, 2):
		g.set(x, y + 2, 24, b("observer", facing="south", powered=False))
	# engine hall: three gensets with exhaust stacks through the roof
	y = F0
	for gx in (4, 13, 22):
		g.fill(gx, y + 1, 4, gx + 5, y + 1, 9, b("iron_block"))
		g.fill(gx, y + 2, 5, gx + 5, y + 4, 8, b("light_gray_concrete"))
		g.fill(gx + 1, y + 2, 9, gx + 4, y + 3, 9, b("iron_bars"))
		g.set(gx, y + 2, 4, b("blast_furnace", facing="north", lit=False))
		g.fill(gx + 1, y + 5, 6, gx + 1, RF + 6, 6, b("polished_basalt", axis="y"))
		g.set(gx + 1, RF + 7, 6, b("iron_bars"))
		g.fill(gx, y + 2, 10, gx + 5, y + 2, 10, b("iron_chain", axis="x"))
		g.set(gx + 5, y + 1, 11, b("lever", face="floor", facing="south", powered=False))
	# fuel day tanks along the north wall and pipework
	for tx in range(3, 29, 5):
		g.fill(tx, y + 1, 1, tx + 2, y + 3, 2, b("white_concrete"))
	g.fill(1, y + 4, 3, 30, y + 4, 3, b("iron_chain", axis="x"))
	# radiators and a cable run on the south side of the hall
	g.fill(3, y + 1, 18, 28, y + 2, 18, b("iron_bars"))
	g.fill(3, y + 1, 18, 3, y + 2, 18, b("iron_block"))
	g.fill(28, y + 1, 18, 28, y + 2, 18, b("iron_block"))
	g.fill(14, y + 1, 18, 17, y + 2, 18, AIR)
	for x, z in ((8, 13), (17, 13), (26, 13), (12, 20)):
		lamp(g, x, y, z, ceiling=11)
	for x in (2, 29):
		for z in (20, 21):
			g.set(x, y + 1, z, barrel(d.FUEL, "up") if z == 20 else b("barrel", facing="up", open=False))
	return {"grid": g, "cx": 2, "cz": 2, "cellars": 0, "floors": 2, "filler": "minecraft:light_gray_concrete",
			"entry": (15, F0 + 1, 30),
			"must_reach": [(10, F0 + 1, 15), (24, F0 + 1, 26), (10, F0 + 1, 26), (18, F1 + 1, 27), (29, F0 + 1, 11)]}
