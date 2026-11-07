"""Mission campus hospital: a 3x2-chunk multibuilding with two cellars (plan section 1.6, asset 4).

Layout on every storey: a 2-wide east-west corridor at z=15..16, rooms north (z1..13) and south
(z18..30), the main stair core at x22..24 (cellar 2 to roof) and a west stair at x1..3 (cellar 1 to the
top floor). Cellar 2 is the Patient Zero deep ward: the main stairs end in a vestibule shut by iron doors
with no buttons, so nobody gets in until the grid code opens them (the boss marker sits in the central
isolation chamber). The hospital_supply per-player cache is in the ground-floor pharmacy.
"""
from lcgen import AIR, Grid, b
from kit import (BOSS_MARKER, barrel, bed, bookcase, carpet, chair, chest, counter, desk, door, double_chest,
				 double_door, fridge, lamp, opening, plant, sealed_door, secure_door, shelf_wall, sink, slab, stair,
				 stove, switchback, table, toilet, window_strip)
import designs as d

W, D = 48, 32
C2, C1, F0, F1, F2, RF = 0, 6, 12, 18, 24, 30
INNER = b("white_concrete")
CURTAIN = b("white_stained_glass_pane")
GLASS = b("light_blue_stained_glass_pane")


def partitions(g, y, xs, z0, z1, mat=INNER):
	for x in xs:
		g.fill(x, y + 1, z0, x, y + 5, z1, mat)


def iv_stand(g, x, y, z):
	g.set(x, y + 1, z, b("iron_bars"))
	g.set(x, y + 2, z, b("iron_bars"))
	g.set(x, y + 3, z, b("tripwire_hook", facing="north", attached=False, powered=False))


def ward(g, y, x0, x1, z_head, facing_in, beds=4, loot=d.HOSPITAL):
	"""Beds with their heads against a wall row ``z_head``, curtain panes between them, a side cabinet
	(barrel) by each bed and a drip stand."""
	xs = list(range(x0 + 1, x1, max(2, (x1 - x0) // beds)))[:beds]
	zf = z_head + (1 if facing_in == "south" else -1) * 1
	for i, x in enumerate(xs):
		hz = z_head
		fz = z_head + (1 if facing_in == "south" else -1)
		head_dir = "north" if facing_in == "south" else "south"
		g.set(x, y + 1, fz, b("white_bed", facing=head_dir, part="foot", occupied=False))
		g.set(x, y + 1, hz, b("white_bed", facing=head_dir, part="head", occupied=False))
		chair(g, x, y, fz + (1 if facing_in == "south" else -1), "north" if facing_in == "south" else "south", "birch")
		if x + 1 < x1:
			if i % 2 == 0:
				g.set(x + 1, y + 1, hz, barrel(loot, facing_in) if i % 4 == 0 else b("barrel", facing=facing_in, open=False))
			else:
				iv_stand(g, x + 1, y, hz)
		if x - 1 >= x0 and i > 0:
			g.fill(x - 1, y + 1, fz, x - 1, y + 3, fz + (1 if facing_in == "south" else -1), CURTAIN)


def side_ward(g, y, x0, x1, z0, z1, loot=d.HOSPITAL):
	"""A narrow ward: beds down the west wall with their heads against it, a cabinet or drip stand and a
	curtain between each pair, a visitor chair opposite every bed."""
	for i, z in enumerate(range(z0 + 1, z1, 3)):
		g.set(x0, y + 1, z, b("white_bed", facing="west", part="head", occupied=False))
		g.set(x0 + 1, y + 1, z, b("white_bed", facing="west", part="foot", occupied=False))
		if z + 1 < z1:
			if i % 2 == 0:
				g.set(x0, y + 1, z + 1, barrel(loot, "east") if i % 4 == 0 else b("barrel", facing="east", open=False))
			else:
				iv_stand(g, x0, y, z + 1)
			g.fill(x0 + 1, y + 1, z + 1, x0 + 1, y + 3, z + 1, CURTAIN)
		if x1 - x0 >= 3:  # only where a chair still leaves an aisle
			chair(g, x1, y, z, "west", "birch")


def exam_room(g, y, x0, x1, z0, z1, door_x, door_z, door_facing):
	door(g, door_x, y, door_z, door_facing, "birch")
	g.set(x0, y + 1, z0 + 1, slab("smooth_quartz", "top"))
	g.set(x0, y + 1, z0 + 2, slab("smooth_quartz", "top"))
	g.set(x0, y + 2, z0, b("white_carpet"))
	sink(g, x1, y, z0, "west")
	g.set(x1, y + 1, z0 + 1, barrel(d.CLINIC, "west"))
	chair(g, x1 - 1, y, z0 + 3, "west", "birch")
	lamp(g, (x0 + x1) // 2, y, (z0 + z1) // 2)


def stair_cores(g, y, main_from_below, west, west_from_below, up=True):
	if west is not None:
		switchback(g, 3, 14, y, "north", "west", "quartz", b("smooth_quartz"), b("iron_bars"), up=west, from_below=west_from_below)
	switchback(g, 24, 14, y, "north", "west", "quartz", b("smooth_quartz"), b("iron_bars"), up=up, from_below=main_from_below)


def skeleton(g, y, west_core=True):
	"""Corridor walls and stair-core enclosures for one storey."""
	g.fill(1, y + 1, 17, 46, y + 5, 17, INNER)
	g.fill(4, y + 1, 14, 21, y + 5, 14, INNER)
	g.fill(25, y + 1, 14, 46, y + 5, 14, INNER)
	partitions(g, y, (21, 25), 10, 14)
	g.fill(21, y + 1, 1, 21, y + 5, 9, INNER)
	g.fill(22, y + 1, 10, 24, y + 5, 10, INNER)
	if west_core:
		g.fill(4, y + 1, 10, 4, y + 5, 14, INNER)
		g.fill(1, y + 1, 10, 3, y + 5, 10, INNER)
	else:
		g.fill(1, y + 1, 14, 3, y + 5, 14, INNER)
	# sealed lift beside the main stairs
	g.fill(28, y + 1, 10, 28, y + 5, 13, INNER)
	g.fill(26, y + 1, 10, 27, y + 5, 10, INNER)
	g.fill(26, y + 1, 11, 27, y + 5, 13, b("smooth_stone"))
	for x in (26, 27):
		sealed_door(g, x, y, 14, "south")
	carpet(g, 1, y, 15, 46, 16, "cyan")


def hospital():
	g = Grid(W, D, 36)
	wall, band = b("white_concrete"), b("light_blue_terracotta")
	# Cellars in stone, storeys in white with blue bands; flat roof with a helipad.
	g.fill(0, C2, 0, W - 1, C2, D - 1, b("deepslate_tiles"))
	g.walls(0, 1, 0, W - 1, 11, D - 1, b("stone_bricks"))
	g.fill(0, C1, 0, W - 1, C1, D - 1, b("polished_andesite"))
	d.shell(g, 0, 0, W - 1, D - 1, 13, 29, wall, (F0, F1, F2), b("smooth_quartz"), band=band)
	g.fill(0, RF, 0, W - 1, RF, D - 1, b("gray_concrete"))
	g.walls(0, RF, 0, W - 1, RF, D - 1, band)
	d.parapet(g, 0, 0, W - 1, D - 1, RF + 1, wall, slab("smooth_quartz"))
	for y in (F0, F1, F2):
		for x0 in range(2, W - 2, 4):
			if y == F0 and 20 <= x0 <= 26:
				continue
			window_strip(g, x0, D - 1, x0 + 1, D - 1, y, GLASS)
			window_strip(g, x0, 0, x0 + 1, 0, y, GLASS)
		for z0 in (3, 7, 19, 23, 27):
			window_strip(g, 0, z0, 0, z0 + 1, y, GLASS)
			window_strip(g, W - 1, z0, W - 1, z0 + 1, y, GLASS)
	# Entrance: glazed double doors, a red cross high on the facade.
	double_door(g, 23, F0, D - 1, "south", "east", "birch")
	window_strip(g, 20, D - 1, 22, D - 1, F0, GLASS, sill=1, height=3)
	window_strip(g, 25, D - 1, 27, D - 1, F0, GLASS, sill=1, height=3)
	g.fill(21, F0 + 4, D - 1, 26, F0 + 4, D - 1, b("light_blue_terracotta"))
	g.fill(23, F2 + 1, D - 1, 24, F2 + 5, D - 1, b("red_concrete"))
	g.fill(21, F2 + 3, D - 1, 26, F2 + 3, D - 1, b("red_concrete"))
	double_door(g, W - 1, F0, 15, "east", "south", "birch")  # ambulance bay doors

	for y in (C1, F0, F1, F2):
		skeleton(g, y)
	# stairs: main core C2 -> roof, west core C1 -> F2
	switchback(g, 24, 14, C2, "north", "west", "quartz", b("smooth_quartz"), b("iron_bars"), up=True, from_below=False)
	stair_cores(g, C1, True, True, False)
	for y in (F0, F1):
		stair_cores(g, y, True, True, True)
	stair_cores(g, F2, True, False, True)
	switchback(g, 24, 14, RF, "north", "west", "quartz", b("smooth_quartz"), b("iron_bars"), up=False)

	ground_floor(g, F0)
	wards_floor(g, F1)
	surgery_floor(g, F2)
	cellar_one(g, C1)
	deep_ward(g, C2)
	roof(g, RF)
	# A blue skirting band along every partition: breaks up the white walls and marks the floor line.
	for y in (C1, F0, F1, F2):
		g.replace(1, y + 1, 1, W - 2, y + 1, D - 2, INNER, b("light_blue_terracotta"))
	return {"grid": g, "cx": 3, "cz": 2, "cellars": 2, "floors": 3, "filler": "minecraft:white_concrete",
			"entry": (23, F0 + 1, D - 2),
			"must_reach": [(3, F0 + 1, 26), (34, F0 + 1, 24), (41, F0 + 1, 22), (5, F0 + 1, 6), (32, F0 + 1, 4),
						   (42, F0 + 1, 6), (5, F1 + 1, 6), (36, F1 + 1, 6), (5, F1 + 1, 24), (40, F1 + 1, 24),
						   (24, F1 + 1, 22), (11, F1 + 1, 2), (37, F1 + 1, 2), (5, F2 + 1, 6), (32, F2 + 1, 4), (8, F2 + 1, 24), (24, F2 + 1, 24),
						   (40, F2 + 1, 23), (10, C1 + 1, 6), (36, C1 + 1, 6), (8, C1 + 1, 24), (24, C1 + 1, 24),
						   (40, C1 + 1, 24), (23, C2 + 1, 15), (24, RF + 1, 20)],
			"must_not_reach": [(23, C2 + 1, 25), (10, C2 + 1, 24)]}


def ground_floor(g, y):
	# South: ER x1..14 | lobby x16..31 | pharmacy x33..39 | imaging x41..46
	partitions(g, y, (15, 32, 40), 18, 30)
	g.fill(22, y + 1, 17, 25, y + 3, 17, AIR)  # wide lobby opening onto the corridor
	door(g, 8, y, 17, "south", "birch")
	# ER: five curtained bays along the south wall and a nurse desk
	for i, x in enumerate((2, 5, 8, 11)):
		g.set(x, y + 1, 29, b("white_bed", facing="south", part="foot", occupied=False))
		g.set(x, y + 1, 30, b("white_bed", facing="south", part="head", occupied=False))
		g.fill(x + 1, y + 1, 28, x + 1, y + 3, 30, CURTAIN)
		if i % 2 == 0:
			iv_stand(g, x - 1, y, 30) if x > 2 else iv_stand(g, 1, y, 28)
	g.set(13, y + 1, 30, barrel(d.HOSPITAL, "north"))
	counter(g, 5, y, 22, 10, 22, "north")
	g.fill(5, y + 1, 23, 5, y + 1, 24, b("smooth_quartz"))
	chair(g, 7, y, 23, "north", "birch")
	chair(g, 9, y, 23, "north", "birch")
	g.set(10, y + 2, 22, b("note_block"))
	g.set(1, y + 1, 18, barrel(d.CLINIC, "east"))
	g.set(1, y + 1, 19, barrel(d.HOSPITAL, "east"))
	shelf_wall(g, 1, y, 18, 1, 22, "east", "birch", rows=(3,))
	lamp(g, 4, y, 25)
	lamp(g, 10, y, 25)
	# Lobby: reception desk, waiting rows, plants, a dead vending machine
	counter(g, 20, y, 22, 27, 22, "south")
	g.fill(20, y + 1, 21, 20, y + 1, 20, b("smooth_quartz"))
	g.fill(27, y + 1, 21, 27, y + 1, 20, b("smooth_quartz"))
	chair(g, 22, y, 21, "south", "birch")
	chair(g, 25, y, 21, "south", "birch")
	for z in (25, 27):
		for x in list(range(17, 21)) + list(range(27, 31)):
			chair(g, x, y, z, "north", "light_blue_terracotta" if False else "birch")
	for x, z in ((16, 30), (31, 30), (16, 18), (31, 18)):
		plant(g, x, y, z, "potted_fern" if (x + z) % 2 else "potted_dead_bush")
	g.fill(16, y + 1, 23, 16, y + 2, 23, b("red_concrete"))
	g.set(16, y + 2, 24, b("glass_pane"))
	carpet(g, 21, y, 23, 26, 30, "light_blue")
	lamp(g, 19, y, 26)
	lamp(g, 28, y, 26)
	lamp(g, 23, y, 19)
	# Hospital pharmacy (secure door): shelves, stock barrels and the hospital_supply cache
	secure_door(g, 36, y, 17, "south")
	g.fill(33, y + 2, 17, 34, y + 3, 17, b("glass_pane"))
	shelf_wall(g, 33, y, 30, 39, 30, "north", "birch", rows=(2, 3, 4))
	for x in range(33, 40):
		g.set(x, y + 1, 30, barrel(d.HOSPITAL if x % 2 else d.PHARMACY, "north"))
	g.set(39, y + 1, 24, chest(d.HOSPITAL_SUPPLY, "west"))
	for z in (21, 22, 26, 27):
		g.fill(35, y + 1, z, 37, y + 1, z, b("white_concrete"))
	shelf_wall(g, 35, y, 21, 37, 21, "north", "birch", rows=(2, 3))
	shelf_wall(g, 35, y, 22, 37, 22, "south", "birch", rows=(2, 3))
	shelf_wall(g, 35, y, 26, 37, 26, "north", "birch", rows=(2, 3))
	shelf_wall(g, 35, y, 27, 37, 27, "south", "birch", rows=(2, 3))
	counter(g, 33, y, 18, 34, 18, "north")
	lamp(g, 36, y, 24)
	# Imaging: a scanner ring round a bed
	door(g, 43, y, 17, "south", "birch")
	g.fill(42, y + 1, 23, 45, y + 4, 23, b("white_concrete"))
	g.fill(43, y + 2, 23, 44, y + 3, 23, AIR)
	g.fill(43, y + 1, 21, 44, y + 1, 26, slab("smooth_quartz", "top"))
	g.fill(43, y + 1, 23, 44, y + 1, 23, slab("smooth_quartz", "top"))
	g.fill(41, y + 1, 29, 42, y + 1, 29, slab("spruce", "top"))
	chair(g, 41, y, 28, "south", "dark_oak")
	g.set(42, y + 2, 29, b("note_block"))
	lamp(g, 44, y, 27)
	# North: records/security L (x1..8) | exam rooms x9..20 | cafeteria x29..37 | stores x39..46
	partitions(g, y, (8, 12, 16), 1, 13)
	partitions(g, y, (38,), 1, 13)
	door(g, 6, y, 14, "north", "birch")
	desk(g, 2, y, 2, "north", width=3, along="east")
	g.fill(1, y + 1, 6, 1, y + 3, 9, b("bookshelf"))
	for z in (4, 5):
		g.set(7, y + 1, z, barrel(d.OFFICE, "west"))
	g.set(2, y + 2, 2, b("note_block"))
	lamp(g, 4, y, 5)
	for x0 in (9, 13, 17):
		exam_room(g, y, x0, x0 + 2 if x0 != 17 else 20, 1, 13, x0 + 1, 14, "north")
	# cafeteria
	opening(g, 31, y, 14, 3)
	opening(g, 32, y, 14, 3)
	counter(g, 29, y, 2, 37, 2, "south")
	stove(g, 30, y, 1, "south")
	stove(g, 31, y, 1, "south")
	sink(g, 33, y, 1, "south")
	fridge(g, 35, y, 1, "south")
	g.set(37, y + 1, 1, barrel(d.GROCERY, "south"))
	for x in (29, 34):
		g.set(x, y + 1, 1, barrel(d.GROCERY, "south"))
	for tx in (30, 34):
		for tz in (6, 10):
			table(g, tx, y, tz, tx + 1, tz, "birch")
			for xx in (tx, tx + 1):
				chair(g, xx, y, tz - 1, "south", "birch")
				chair(g, xx, y, tz + 1, "north", "birch")
	lamp(g, 32, y, 6)
	lamp(g, 32, y, 10)
	# stores
	door(g, 42, y, 14, "north", "birch")
	for rx in (40, 43, 46):
		for z in range(2, 12):
			if rx == 46 and z > 9:
				continue
			g.set(rx, y + 1, z, barrel(d.HOSPITAL, "west") if (z + rx) % 5 == 0 else b("barrel", facing="up", open=False))
			g.set(rx, y + 2, z, b("barrel", facing="up", open=False) if z % 2 else b("white_concrete"))
	lamp(g, 42, y, 6)
	for x in (10, 30, 40):
		lamp(g, x, y, 15)


def wards_floor(g, y):
	partitions(g, y, (8, 12, 16), 1, 13)
	partitions(g, y, (33, 38, 42), 1, 13)
	partitions(g, y, (8, 15, 20, 28, 32, 39), 18, 30)
	# north wards
	door(g, 6, y, 14, "north", "birch")
	for x in (1, 2, 3):
		g.set(x, y + 1, 1, b("cauldron"))
	g.fill(1, y + 1, 3, 3, y + 1, 4, b("white_wool"))
	g.set(1, y + 2, 3, b("white_carpet"))
	lamp(g, 2, y, 6)
	for x0, x1 in ((9, 11), (13, 15), (17, 20)):
		door(g, x0 + 1, y, 14, "north", "birch")
		side_ward(g, y, x0, x1, 0, 13)
		lamp(g, (x0 + x1) // 2, y, 7)
	# nurse station x29..32 opening onto the corridor
	opening(g, 30, y, 14, 3)
	opening(g, 31, y, 14, 3)
	counter(g, 29, y, 11, 32, 11, "south")
	chair(g, 30, y, 10, "south", "birch")
	g.set(32, y + 2, 11, b("note_block"))
	g.set(29, y + 1, 2, barrel(d.HOSPITAL, "south"))
	g.set(30, y + 1, 2, barrel(d.CLINIC, "south"))
	shelf_wall(g, 29, y, 1, 32, 1, "south", "birch", rows=(2, 3))
	lamp(g, 31, y, 6)
	for x0, x1 in ((34, 37), (39, 41), (43, 46)):
		door(g, x0 + 1, y, 14, "north", "birch")
		side_ward(g, y, x0, x1, 0, 13)
		lamp(g, (x0 + x1) // 2, y, 7)
	# south wards and a day room
	for x0, x1 in ((1, 7), (9, 14), (33, 38), (40, 46)):
		door(g, x0 + 2, y, 17, "south", "birch")
		ward(g, y, x0 - 1, x1 + 1, 30, "north", beds=3)
		lamp(g, (x0 + x1) // 2, y, 24)
	# private rooms x16..19 and x29..31
	for x0, x1 in ((16, 19), (29, 31)):
		door(g, x0 + 1, y, 17, "south", "birch")
		bed(g, x0 + 1, y, 29, "south", "light_blue")
		g.set(x0, y + 1, 30, barrel(d.HOSPITAL, "north"))
		chair(g, x1, y, 26, "west", "birch")
		lamp(g, x0 + 1, y, 24)
	# day room x21..27: sofas, tables, a dead TV
	door(g, 24, y, 17, "south", "birch")
	d.couch(g, 22, y, 26, "south", "east", 4, "light_gray_concrete" if False else "dark_oak")
	g.fill(22, y + 1, 30, 25, y + 1, 30, b("dark_oak_planks"))
	g.fill(23, y + 2, 30, 24, y + 2, 30, b("black_concrete"))
	table(g, 26, y, 21, 26, 22, "birch")
	chair(g, 27, y, 21, "west", "birch")
	chair(g, 27, y, 22, "west", "birch")
	bookcase(g, 21, y, 19, 21, 21, 2)
	plant(g, 27, y, 30, "potted_fern")
	lamp(g, 24, y, 23)
	for x in (10, 36):
		lamp(g, x, y, 16)


def surgery_floor(g, y):
	partitions(g, y, (8, 15), 1, 13)
	partitions(g, y, (37, 42), 1, 13)
	partitions(g, y, (15, 32), 18, 30)
	partitions(g, y, (20, 26), 18, 30)
	door(g, 6, y, 14, "north", "birch")
	desk(g, 2, y, 2, "north", width=3, along="east")
	g.set(7, y + 1, 1, barrel(d.OFFICE, "south"))
	bookcase(g, 1, y, 6, 1, 9, 3)
	lamp(g, 4, y, 5)
	# two operating theatres
	for x0, x1 in ((9, 14), (16, 20)):
		double_door(g, x0 + 1, y, 14, "north", "east", "birch")
		cx = (x0 + x1) // 2
		g.set(cx, y + 1, 6, b("iron_bars"))
		g.set(cx, y + 1, 7, b("iron_bars"))
		g.fill(cx, y + 2, 5, cx, y + 2, 8, slab("smooth_quartz", "bottom"))
		g.set(cx, y + 4, 6, b("iron_chain", axis="y"))
		g.set(cx, y + 5, 6, b("iron_chain", axis="y"))
		g.set(cx, y + 3, 6, b("redstone_lamp", lit=False))
		g.set(cx + 2, y + 1, 5, b("iron_block"))
		g.set(cx + 2, y + 2, 5, b("observer", facing="west", powered=False))
		g.fill(cx - 2, y + 1, 8, cx - 2, y + 1, 9, slab("smooth_quartz", "top"))
		g.set(x0, y + 1, 1, barrel(d.HOSPITAL, "south"))
		g.set(x1, y + 1, 1, barrel(d.CLINIC, "south"))
		sink(g, x0, y, 12, "east")
		sink(g, x0, y, 11, "east")
	# ICU x29..36: six beds with monitors
	opening(g, 32, y, 14, 3)
	for i, x in enumerate((23, 25, 27, 29, 31, 33, 35)):
		g.set(x, y + 1, 2, b("white_bed", facing="north", part="foot", occupied=False))
		g.set(x, y + 1, 1, b("white_bed", facing="north", part="head", occupied=False))
		g.set(x + 1, y + 1, 1, b("iron_block"))
		g.set(x + 1, y + 2, 1, b("observer", facing="south", powered=False))
	for x in (29, 33):
		g.set(x, y + 1, 9, b("white_bed", facing="south", part="foot", occupied=False))
		g.set(x, y + 1, 10, b("white_bed", facing="south", part="head", occupied=False))
		iv_stand(g, x + 1, y, 10)
	counter(g, 35, y, 8, 36, 8, "west")
	g.set(36, y + 1, 10, barrel(d.HOSPITAL, "west"))
	lamp(g, 32, y, 5)
	# doctors' offices x38..46
	for x0, x1 in ((38, 41), (43, 46)):
		door(g, x0 + 1, y, 14, "north", "birch")
		desk(g, x0 + 1, y, 2, "north", width=2, along="east")
		g.set(x1, y + 1, 6, b("bookshelf"))
		g.set(x1, y + 1, 7, barrel(d.OFFICE, "west"))
		lamp(g, x0 + 2, y, 6)
	# recovery x1..14
	door(g, 7, y, 17, "south", "birch")
	ward(g, y, 0, 15, 30, "north", beds=6)
	lamp(g, 5, y, 24)
	lamp(g, 11, y, 24)
	# administration x16..19, x21..25, x27..31
	for x0, x1 in ((16, 19), (21, 25), (27, 31)):
		door(g, x0 + 1, y, 17, "south", "birch")
		desk(g, x0 + 1, y, 29, "south", width=2, along="east")
		g.set(x1, y + 1, 19, barrel(d.OFFICE, "west"))
		g.set(x0, y + 1, 22, b("bookshelf"))
		lamp(g, x0 + 2, y, 24)
	# records x33..46: long shelves of files
	door(g, 36, y, 17, "south", "birch")
	for rz in (21, 24, 27):
		g.fill(34, y + 1, rz, 45, y + 3, rz, b("bookshelf"))
		g.set(34, y + 1, rz, barrel(d.OFFICE, "north"))
	table(g, 34, y, 19, 37, 19, "spruce")
	chair(g, 35, y, 20, "north", "spruce")
	lamp(g, 39, y, 22)
	lamp(g, 39, y, 28)
	for x in (10, 36):
		lamp(g, x, y, 16)


def cellar_one(g, y):
	partitions(g, y, (20,), 1, 13)
	partitions(g, y, (15, 32), 18, 30)
	# west L: maintenance office
	door(g, 6, y, 14, "north", "spruce")
	desk(g, 2, y, 2, "north", width=2, along="east")
	g.set(7, y + 1, 1, barrel(d.WORKSHOP, "south"))
	lamp(g, 4, y, 6)
	g.fill(8, y + 1, 1, 8, y + 5, 13, INNER)
	# morgue x9..19: drawer wall, autopsy tables, body trolleys
	door(g, 14, y, 14, "north", "iron")
	g.openable.update({(14, y + 1, 14), (14, y + 2, 14)})
	g.set(13, y + 2, 15, b("stone_button", face="wall", facing="south", powered=False))
	g.set(13, y + 2, 13, b("stone_button", face="wall", facing="north", powered=False))
	g.fill(9, y + 1, 1, 19, y + 3, 1, b("iron_block"))
	for x in range(9, 20):
		for dy in (1, 2, 3):
			g.set(x, y + dy, 2, b("lever", face="wall", facing="south", powered=False)) if (x + dy) % 2 == 0 else None
	for cx in (11, 16):
		g.set(cx, y + 1, 7, b("iron_bars"))
		g.fill(cx, y + 2, 6, cx, y + 2, 8, slab("smooth_quartz", "bottom"))
		g.set(cx + 1, y + 1, 7, b("cauldron"))
		lamp(g, cx, y, 7)
	for x in (10, 13, 18):
		g.set(x, y + 1, 11, b("gray_bed", facing="east", part="foot", occupied=False))
		g.set(x + 1, y + 1, 11, b("gray_bed", facing="east", part="head", occupied=False))
	# plant room x29..46: boilers, tanks, pipes
	opening(g, 34, y, 14, 3)
	opening(g, 35, y, 14, 3)
	for bx in (30, 36, 42):
		g.fill(bx, y + 1, 2, bx + 2, y + 3, 4, b("iron_block"))
		g.set(bx + 1, y + 1, 5, b("blast_furnace", facing="south", lit=False))
		g.set(bx + 1, y + 4, 3, b("iron_chain", axis="y"))
		g.set(bx + 1, y + 5, 3, b("iron_chain", axis="y"))
	g.fill(29, y + 4, 8, 46, y + 4, 8, b("iron_chain", axis="x"))
	g.fill(29, y + 3, 10, 46, y + 3, 10, b("iron_chain", axis="x"))
	for x in (30, 33, 40, 43):
		g.fill(x, y + 1, 11, x + 1, y + 2, 12, b("light_gray_concrete"))
	g.set(46, y + 1, 7, barrel(d.FUEL, "west"))
	g.set(46, y + 1, 6, barrel(d.WORKSHOP, "west"))
	lamp(g, 33, y, 7)
	lamp(g, 40, y, 7)
	# laundry x1..14
	door(g, 7, y, 17, "south", "spruce")
	for x in range(2, 14, 2):
		g.set(x, y + 1, 30, b("cauldron"))
		g.set(x + 1, y + 1, 30, b("loom", facing="north"))
	g.fill(2, y + 1, 23, 6, y + 1, 24, b("white_wool"))
	g.fill(9, y + 1, 23, 12, y + 1, 24, b("light_blue_wool"))
	g.set(3, y + 2, 23, b("white_carpet"))
	lamp(g, 7, y, 25)
	# stores x16..31
	door(g, 23, y, 17, "south", "spruce")
	for rz in (20, 23, 26, 29):
		for x in range(17, 31):
			if x in (23, 24):
				continue
			g.set(x, y + 1, rz, barrel(d.HOSPITAL, "south") if (x + rz) % 7 == 0 else b("barrel", facing="up", open=False))
			if rz != 29:
				g.set(x, y + 2, rz, b("barrel", facing="up", open=False) if x % 2 else b("white_concrete"))
	lamp(g, 23, y, 22)
	lamp(g, 23, y, 27)
	# workshop x33..46
	door(g, 38, y, 17, "south", "spruce")
	g.set(34, y + 1, 30, b("crafting_table"))
	g.set(35, y + 1, 30, b("smithing_table"))
	g.set(36, y + 1, 30, b("anvil", facing="east"))
	g.set(37, y + 1, 30, b("grindstone", face="floor", facing="north"))
	double_chest(g, 40, y, 30, "north", "east", d.WORKSHOP)
	table(g, 42, y, 24, 45, 25, "spruce")
	chair(g, 43, y, 23, "south", "spruce")
	g.set(46, y + 1, 18, barrel(d.FUEL, "west"))
	lamp(g, 40, y, 24)
	for x in (10, 36):
		lamp(g, x, y, 16)


def deep_ward(g, y):
	"""Cellar 2: sealed. Bedrock-grey stone everywhere except the stair vestibule and the ward itself."""
	g.fill(1, y + 1, 1, 46, y + 5, 30, b("stone"))
	# the main stairs' footprint and the vestibule in front of them
	g.fill(22, y + 1, 11, 24, y + 5, 14, AIR)
	switchback(g, 24, 14, y, "north", "west", "quartz", b("smooth_quartz"), b("iron_bars"), up=True, from_below=False)
	g.fill(21, y + 1, 15, 25, y + 4, 16, AIR)
	g.fill(21, y, 15, 25, y, 16, b("cracked_deepslate_tiles"))
	for x in (23, 24):
		sealed_door(g, x, y, 17, "south")
	g.fill(21, y + 3, 17, 22, y + 3, 17, b("iron_bars"))
	g.fill(25, y + 3, 17, 26, y + 3, 17, b("iron_bars"))
	lamp(g, 23, y, 15, ceiling=4)
	g.set(21, y + 1, 15, b("cobweb"))
	# the ward behind the doors: a corridor of isolation cells round the central chamber
	g.fill(4, y + 1, 18, 43, y + 4, 29, AIR)
	g.fill(4, y + 1, 18, 43, y + 1, 29, AIR)
	g.fill(4, y, 18, 43, y, 29, b("cracked_deepslate_tiles"))
	for x0 in range(4, 17, 4):
		g.fill(x0 + 3, y + 1, 18, x0 + 3, y + 4, 23, b("deepslate_tiles"))
		g.fill(x0, y + 1, 23, x0 + 2, y + 4, 23, b("iron_bars"))
		bed(g, x0, y, 20, "north", "gray")
		g.set(x0 + 2, y + 1, 18, b("cauldron"))
		g.set(x0 + 1, y + 1, 21, b("iron_chain", axis="y"))
	for x0 in range(31, 40, 4):
		g.fill(x0 - 1, y + 1, 18, x0 - 1, y + 4, 23, b("deepslate_tiles"))
		g.fill(x0, y + 1, 23, x0 + 2, y + 4, 23, b("iron_bars"))
		bed(g, x0 + 2, y, 20, "north", "gray")
		g.set(x0, y + 1, 18, b("cauldron"))
	# central isolation chamber x19..28 z21..29: glass observation wall, the Patient Zero marker
	g.walls(19, y + 1, 21, 28, y + 4, 29, b("polished_deepslate"))
	g.fill(20, y + 2, 21, 27, y + 3, 21, b("glass_pane"))
	sealed_door(g, 23, y, 21, "north")
	g.set(23, y + 3, 21, b("polished_deepslate"))
	g.fill(20, y + 1, 22, 27, y + 4, 28, AIR)
	g.set(23, y, 25, BOSS_MARKER)
	g.set(24, y, 25, BOSS_MARKER)
	for x, z in ((20, 22), (27, 22), (20, 28), (27, 28)):
		g.set(x, y + 1, z, b("iron_chain", axis="y"))
		g.set(x, y + 2, z, b("iron_chain", axis="y"))
	g.fill(21, y + 1, 28, 22, y + 1, 28, b("bone_block", axis="y"))
	g.set(26, y + 1, 27, b("bone_block", axis="x"))
	bed(g, 25, y, 25, "east", "red")
	for x, z in ((6, 26), (12, 28), (36, 27), (42, 25), (30, 19), (17, 19), (24, 23), (21, 26)):
		g.set(x, y + 1, z, b("cobweb"))
	for x, z in ((8, 27), (35, 26), (14, 25), (40, 28)):
		g.set(x, y + 1, z, b("red_carpet"))
	for x in (10, 23, 37):
		lamp(g, x, y, 26, ceiling=4)


def roof(g, y):
	# stair houses for both cores and the lift motor room
	g.walls(21, y + 1, 9, 29, y + 3, 16, INNER)
	g.fill(21, y + 4, 9, 29, y + 4, 16, slab("smooth_quartz"))
	g.fill(26, y + 1, 10, 28, y + 3, 13, b("light_gray_concrete"))
	door(g, 23, y, 16, "south", "spruce")
	# helipad, painted into the roof slab so it stays walkable
	g.fill(14, y, 19, 33, y, 30, b("gray_concrete"))
	g.walls(14, y, 19, 33, y, 30, b("white_concrete"))
	for z in range(21, 29):
		g.set(20, y, z, b("yellow_concrete"))
		g.set(27, y, z, b("yellow_concrete"))
	g.fill(21, y, 24, 26, y, 25, b("yellow_concrete"))
	# HVAC units and vents
	for x0, z0 in ((4, 4), (38, 4), (4, 22), (38, 22)):
		g.fill(x0, y + 1, z0, x0 + 4, y + 2, z0 + 3, b("light_gray_concrete"))
		g.fill(x0 + 1, y + 3, z0 + 1, x0 + 3, y + 3, z0 + 2, b("iron_trapdoor", facing="north", half="bottom", open=False, powered=False, waterlogged=False))
	for dy in range(1, 5):
		g.set(45, y + dy, 2, b("lightning_rod", facing="up", powered=False))
