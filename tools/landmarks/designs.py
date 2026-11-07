"""The landmark designs. Each function returns a dict with the grid and its Lost Cities shape.

Loot tables are Ashfall's (data/ashfall/loot_table/buildings/* and data/ashfall_cache/*, both in the arms
jar). ashfall_cache tables are the per-player caches: Lootr converts those chests because their
namespace is not on its blacklist. Every building's street front is its south side (z max); the
predefined cities put a street chunk there.
"""
from lcgen import AIR, Grid, b
from kit import (BOSS_MARKER, DEAD_LAMP, SUBSTATION_CORE, barrel, sealed_door, secure_door, bathtub, bed, bookcase, carpet, chair, chest,
				 counter, couch, desk, door, double_chest, double_door, fridge, lamp, opening, plant, ramp,
				 shelf_wall, sink, slab, stair, stove, switchback, table, toilet, trapdoor, tv, window_strip)

HOME = "ashfall:buildings/home"
FUEL = "ashfall:buildings/fuel"
GROCERY = "ashfall:buildings/grocery"
ELECTRONICS = "ashfall:buildings/electronics"
PHARMACY = "ashfall:buildings/pharmacy"
CLINIC = "ashfall:buildings/clinic"
HOSPITAL = "ashfall:buildings/hospital"
POLICE = "ashfall:buildings/police"
OFFICE = "ashfall:buildings/office"
WAREHOUSE = "ashfall:buildings/warehouse"
WORKSHOP = "ashfall:buildings/workshop"
BREWERY = "ashfall:buildings/brewery"
GENERIC = "ashfall:buildings/generic"
POLICE_ARMORY = "ashfall_cache:police_armory"
HOSPITAL_SUPPLY = "ashfall_cache:hospital_supply"
# Per-player story caches: the Field Radio in the safehouse radio room, the Kiln & Co. work order upstairs.
FIELD_RADIO = "ashfall_cache:field_radio"
KILN_OFFICE = "ashfall_cache:kiln_office"


def shell(g, x0, z0, x1, z1, y0, y1, wall, floors, floor_mat, band=None, roof=None):
	"""Outer walls from y0 to y1 plus a slab at every floor height; ``band`` replaces the slab's edge."""
	g.walls(x0, y0, z0, x1, y1, z1, wall)
	for fy in floors:
		g.fill(x0, fy, z0, x1, fy, z1, floor_mat)
		if band:
			g.walls(x0, fy, z0, x1, fy, z1, band)
	if roof:
		g.fill(x0, y1 + 1, z0, x1, y1 + 1, z1, roof)


def parapet(g, x0, z0, x1, z1, y, wall, cap):
	g.walls(x0, y, z0, x1, y, z1, wall)
	g.walls(x0, y + 1, z0, x1, y + 1, z1, cap)


# =================================================================================================
# Montford safehouse: spawn building. One chunk, cellar + two storeys + roof with a stair bulkhead.
# =================================================================================================
def safehouse():
	g = Grid(16, 16, 24)
	C0, F0, F1, RF = 0, 6, 12, 18
	brick, stone = b("bricks"), b("stone_bricks")
	plank = b("spruce_planks")
	inner = b("birch_planks")
	rail = b("spruce_fence")

	# Structure: stone cellar, brick storeys, stone belt courses at each floor, flat roof with parapet.
	g.fill(0, C0, 0, 15, C0, 15, b("polished_andesite"))
	g.walls(0, 1, 0, 15, 5, 15, stone)
	shell(g, 0, 0, 15, 15, 7, 17, brick, (F0, F1), plank, band=stone)
	g.fill(0, RF, 0, 15, RF, 15, b("smooth_stone"))
	g.walls(0, RF, 0, 15, RF, 15, stone)
	parapet(g, 0, 0, 15, 15, RF + 1, brick, slab("stone_brick"))

	# Windows: steel grilles downstairs, blackout glass upstairs.
	grille, blackout = b("iron_bars"), b("black_stained_glass_pane")
	for y, glass in ((F0, grille), (F1, blackout)):
		window_strip(g, 2, 15, 3, 15, y, glass)
		window_strip(g, 10, 15, 13, 15, y, glass)
		window_strip(g, 15, 9, 15, 11, y, glass)
		window_strip(g, 0, 10, 0, 12, y, glass)
		window_strip(g, 11, 0, 12, 0, y, glass)
	window_strip(g, 6, 15, 7, 15, F1, blackout)
	# Front double door (south) and back door (north, from the mudroom).
	double_door(g, 6, F0, 15, "south", "east", "spruce")
	door(g, 6, F0, 0, "north", "spruce")

	# Interior partitions, the same skeleton on both storeys.
	for y in (F0, F1):
		g.fill(4, y + 1, 1, 4, y + 5, 4, inner)        # stairwell east wall
		g.fill(1, y + 1, 6, 4, y + 5, 6, inner)        # west room north wall
		g.fill(5, y + 1, 6, 5, y + 5, 14, inner)       # west room east wall
		g.fill(8, y + 1, 1, 8, y + 5, 14, inner)       # hall east wall
		g.fill(9, y + 1, 6, 14, y + 5, 6, inner)       # east rooms divider
	g.fill(1, 1, 6, 4, 5, 6, stone)
	g.fill(4, 1, 1, 4, 5, 4, stone)

	# Stairs: one stacked switchback from the cellar to the roof (u runs north from the z=5 corridor).
	for y, below in ((C0, False), (F0, True), (F1, True)):
		switchback(g, 3, 4, y, "north", "west", "spruce", plank, rail, up=True, from_below=below)
	switchback(g, 3, 4, RF, "north", "west", "spruce", plank, rail, up=False)

	# ---- ground floor -------------------------------------------------------------------------
	y = F0
	# living room x1..4 z7..14, archway from the hall
	for z in (9, 10):
		opening(g, 5, y, z, 3)
	g.fill(2, y + 1, 7, 3, y + 1, 7, b("dark_oak_planks"))
	g.fill(2, y + 2, 7, 3, y + 2, 7, b("black_concrete"))
	couch(g, 2, y, 14, "north", "east", 2)
	g.fill(2, y + 1, 11, 3, y + 1, 11, slab("spruce"))
	chair(g, 1, y, 10, "east", "dark_oak")
	bookcase(g, 1, y, 7, 1, 8, 3)
	plant(g, 4, y, 8)
	carpet(g, 2, y, 9, 3, 13, "brown")
	lamp(g, 3, y, 10)
	# mudroom x5..7 z1..4: bench, boot barrels
	chair(g, 7, y, 2, "west", "spruce")  # one seat: a second at z=3 would block the storeroom door
	g.set(5, y + 1, 1, barrel(GENERIC, "south"))
	g.set(7, y + 1, 1, barrel(GENERIC, "south"))
	carpet(g, 6, y, 1, 6, 4, "gray")
	# kitchen x9..14 z7..14
	door(g, 8, y, 9, "east")
	g.set(14, y + 1, 8, barrel(HOME, "west"))
	g.set(14, y + 1, 9, barrel(HOME, "west"))
	sink(g, 14, y, 10, "west")
	stove(g, 14, y, 11, "west")
	counter(g, 14, y, 12, 14, 13, "west")
	fridge(g, 14, y, 14, "west")
	g.set(9, y + 1, 7, barrel(GROCERY, "south"))
	g.set(10, y + 1, 7, barrel(GROCERY, "south"))
	counter(g, 11, y, 7, 13, 7, "south")
	g.set(14, y + 1, 7, b("smooth_quartz"))
	shelf_wall(g, 14, y, 8, 14, 13, "west", "spruce", rows=(3,))
	shelf_wall(g, 9, y, 7, 13, 7, "south", "spruce", rows=(3,))
	table(g, 10, y, 11, 11, 12)
	for z in (11, 12):
		chair(g, 9, y, z, "east")
		chair(g, 12, y, z, "west")
	chair(g, 10, y, 10, "south")
	chair(g, 11, y, 13, "north")
	lamp(g, 11, y, 11)
	# storeroom x9..14 z1..5: the survivors' stash
	door(g, 8, y, 3, "east")
	double_chest(g, 10, y, 1, "south", "east", HOME)
	g.set(12, y + 1, 1, barrel(HOME, "south"))
	g.set(13, y + 1, 1, barrel(FUEL, "south"))
	shelf_wall(g, 10, y, 1, 13, 1, "south", "oak", rows=(3, 4))
	g.set(14, y + 1, 2, b("crafting_table"))
	g.set(14, y + 1, 3, b("smithing_table"))
	g.set(14, y + 1, 4, b("fletching_table"))
	table(g, 9, y, 5, 10, 5)
	g.set(9, y + 2, 5, b("note_block"))  # the dead field radio
	g.set(9, y + 3, 5, b("lightning_rod", facing="up", powered=False))
	chair(g, 11, y, 5, "west")
	lamp(g, 12, y, 3)
	# lower closet under the cellar flight is filled; hall rug
	carpet(g, 6, y, 6, 7, 13, "red")

	# ---- first floor --------------------------------------------------------------------------
	y = F1
	# bathroom x5..7 z1..3
	g.fill(5, y + 1, 4, 7, y + 5, 4, inner)
	door(g, 6, y, 4, "south", "birch")
	toilet(g, 5, y, 1, "south")
	sink(g, 7, y, 1, "south")
	bathtub(g, 5, y, 2, "south")
	g.set(7, y + 1, 3, b("white_carpet"))
	# bunk room x1..4 z7..14
	door(g, 2, y, 6, "north", "birch")
	for x in (1, 4):
		for zf in (9, 13):
			bed(g, x, y, zf, "north", "gray")
			g.set(x, y + 2, zf, slab("spruce", "top"))
			g.set(x, y + 2, zf - 1, slab("spruce", "top"))
			bed(g, x, y + 2, zf, "north", "green")
	g.set(2, y + 1, 14, barrel(HOME, "north"))
	g.set(3, y + 1, 14, barrel(HOME, "north"))
	carpet(g, 2, y, 8, 3, 13, "gray")
	lamp(g, 2, y, 11)
	# radio room x9..14 z1..5
	door(g, 8, y, 3, "east")
	desk(g, 14, y, 2, "east", width=3, along="south")
	g.set(14, y + 2, 3, b("note_block"))
	g.set(14, y + 2, 2, b("lectern", facing="west"))
	g.set(9, y + 1, 1, b("cartography_table"))
	# the Field Radio cache, under the dead set's desk corner: the start of the "Relight" story
	g.set(13, y + 1, 5, chest(FIELD_RADIO, "north"))
	double_chest(g, 10, y, 1, "south", "east", ELECTRONICS)
	shelf_wall(g, 10, y, 1, 13, 1, "south", "dark_oak", rows=(3,))
	lamp(g, 11, y, 3)
	# bedroom x9..14 z7..14
	door(g, 8, y, 10, "east")
	bed(g, 13, y, 10, "east", "red")
	bed(g, 13, y, 11, "east", "red")
	g.set(14, y + 1, 9, barrel(HOME, "west"))
	g.set(14, y + 1, 12, barrel(HOME, "west"))
	plant(g, 14, y + 1, 12, "potted_dead_bush")
	g.set(11, y + 1, 7, barrel(HOME, "south"))
	g.set(12, y + 1, 7, barrel(HOME, "south"))
	desk(g, 9, y, 13, "west", width=2, along="south")
	carpet(g, 10, y, 9, 12, 12, "light_gray")
	lamp(g, 11, y, 10)
	carpet(g, 6, y, 6, 7, 14, "gray")

	# ---- roof -----------------------------------------------------------------------------------
	y = RF
	g.walls(0, y + 1, 0, 4, y + 3, 6, brick)
	g.fill(0, y + 4, 0, 4, y + 4, 6, slab("stone_brick"))
	door(g, 2, y, 6, "south", "spruce")
	for x in (11, 13):
		for z in (2, 4):
			g.set(x, y + 1, z, b("spruce_fence"))
	g.fill(11, y + 2, 2, 13, y + 3, 4, b("stripped_spruce_wood"))
	g.fill(11, y + 4, 2, 13, y + 4, 4, slab("spruce"))
	g.fill(11, y + 1, 10, 12, y + 1, 10, b("light_gray_concrete"))
	g.fill(11, y + 2, 10, 12, y + 2, 10, b("iron_trapdoor", facing="south", half="bottom", open=False, powered=False, waterlogged=False))
	for dy in range(1, 5):
		g.set(14, y + dy, 14, b("lightning_rod", facing="up", powered=False))
	g.fill(7, y + 1, 12, 9, y + 1, 13, b("coarse_dirt"))
	g.set(7, y + 2, 12, b("dead_bush"))
	g.set(9, y + 2, 13, b("dead_bush"))

	# ---- cellar ---------------------------------------------------------------------------------
	y = C0
	g.set(1, y + 1, 4, barrel(HOME, "east"))
	# generator corner
	g.fill(11, y + 1, 1, 12, y + 2, 2, b("iron_block"))
	g.set(13, y + 1, 1, b("blast_furnace", facing="south", lit=False))
	g.set(13, y + 2, 1, b("lever", face="wall", facing="south", powered=False))
	for x in (9, 10):
		g.set(x, y + 1, 1, barrel(FUEL, "south"))
	g.set(14, y + 1, 1, b("iron_chain", axis="y"))
	g.set(14, y + 2, 1, b("iron_chain", axis="y"))
	# pantry along the south wall
	for x in range(2, 14):
		g.set(x, y + 1, 14, barrel(GROCERY if x % 3 else HOME, "north"))
	shelf_wall(g, 2, y, 14, 13, 14, "north", "oak", rows=(2, 3))
	# water store
	for z in range(6, 10):
		g.set(14, y + 1, z, b("cauldron"))
	# cots and a workbench
	bed(g, 1, y, 9, "north", "brown")
	bed(g, 1, y, 12, "north", "brown")
	g.set(7, y + 1, 9, b("crafting_table"))
	g.set(8, y + 1, 9, b("chipped_anvil", facing="east"))
	g.set(9, y + 1, 9, b("grindstone", face="floor", facing="north"))
	for x, z in ((6, 7), (10, 7), (6, 11), (10, 11)):
		g.fill(x, y + 1, z, x, y + 5, z, stone)
	lamp(g, 8, y, 4)
	lamp(g, 8, y, 12)
	return {"grid": g, "cx": 1, "cz": 1, "cellars": 1, "floors": 2, "filler": "minecraft:stone_bricks",
			"entry": (6, F0 + 1, 14),
			"must_reach": [(8, C0 + 1, 10), (11, F0 + 1, 9), (12, F0 + 1, 3), (2, F1 + 1, 10), (11, F1 + 1, 9),
						   (11, F1 + 1, 3), (6, F1 + 1, 2), (8, RF + 1, 9)]}


# =================================================================================================
# Pharmacy: one chunk, shop floor + dispensary downstairs, stockroom/office/break room upstairs.
# =================================================================================================
def pharmacy():
	g = Grid(16, 16, 18)
	F0, F1, RF = 0, 6, 12
	wall, trim = b("light_gray_concrete"), b("green_concrete")
	inner = b("white_concrete")
	floor = b("smooth_quartz")
	rail = b("birch_fence")
	shell(g, 0, 0, 15, 15, 1, 11, wall, (F0, F1), floor, band=trim)
	g.fill(1, F1, 1, 14, F1, 14, b("light_gray_concrete"))
	g.fill(0, RF, 0, 15, RF, 15, b("smooth_stone"))
	g.walls(0, RF, 0, 15, RF, 15, trim)
	parapet(g, 0, 0, 15, 15, RF + 1, wall, slab("smooth_stone"))
	# Storefront: tall glass either side of the double door, a green cross sign above it.
	glass = b("glass_pane")
	window_strip(g, 1, 15, 5, 15, F0, glass, sill=1, height=3)
	window_strip(g, 10, 15, 14, 15, F0, glass, sill=1, height=3)
	double_door(g, 7, F0, 15, "south", "east", "birch")
	g.fill(6, F0 + 3, 15, 9, F0 + 3, 15, glass)
	g.fill(7, F1 + 1, 15, 8, F1 + 4, 15, b("lime_concrete"))
	g.fill(6, F1 + 2, 15, 9, F1 + 3, 15, b("lime_concrete"))
	for y in (F1,):
		window_strip(g, 2, 15, 4, 15, y, glass)
		window_strip(g, 11, 15, 13, 15, y, glass)
		window_strip(g, 15, 9, 15, 12, y, glass)
		window_strip(g, 0, 9, 0, 12, y, glass)
	window_strip(g, 15, 8, 15, 12, F0, glass, sill=2, height=2)
	# Back door to the alley (north): a randomly placed pharmacy may have a building against its front.
	door(g, 13, F0, 0, "north", "spruce")

	# Stairs (west, rising north from the z=5 back corridor), dispensary partition at z=6.
	for y in (F0, F1):
		g.fill(4, y + 1, 1, 4, y + 5, 4, inner)
	switchback(g, 3, 4, F0, "north", "west", "birch", b("birch_planks"), rail, up=True)
	switchback(g, 3, 4, F1, "north", "west", "birch", b("birch_planks"), rail, up=False)

	# ---- shop floor (z7..14) ----------------------------------------------------------------------
	y = F0
	g.fill(1, y + 1, 6, 4, y + 5, 6, inner)
	g.fill(12, y + 1, 6, 12, y + 5, 6, inner)
	g.fill(14, y + 1, 6, 14, y + 5, 6, inner)
	door(g, 13, y, 6, "south", "birch")
	counter(g, 5, y, 6, 11, 6, "south", top=b("smooth_quartz"))
	g.fill(5, y + 3, 6, 11, y + 5, 6, inner)
	g.fill(5, y + 2, 6, 11, y + 2, 6, AIR)
	for gx, face_a, face_b in ((3, "west", "east"), (11, "west", "east")):
		for z in range(8, 12):
			g.set(gx, y + 1, z, b("white_concrete"))
			g.set(gx + 1, y + 1, z, b("white_concrete"))
		shelf_wall(g, gx, y, 8, gx, 11, face_a, "birch", rows=(2, 3))
		shelf_wall(g, gx + 1, y, 8, gx + 1, 11, face_b, "birch", rows=(2, 3))
		g.set(gx, y + 4, 8, slab("smooth_quartz"))
		g.fill(gx, y + 4, 8, gx + 1, y + 4, 11, slab("smooth_quartz"))
	shelf_wall(g, 1, y, 7, 1, 12, "east", "birch", rows=(2, 3))
	shelf_wall(g, 14, y, 7, 14, 7, "west", "birch", rows=(2, 3))
	for z in (8, 10):
		g.set(1, y + 1, z, barrel(PHARMACY, "east"))
	g.set(7, y + 1, 9, barrel(PHARMACY, "up"))
	g.set(8, y + 1, 9, barrel(GENERIC, "up"))
	g.set(7, y + 1, 10, barrel(GENERIC, "up"))
	g.set(8, y + 1, 10, barrel(PHARMACY, "up"))
	counter(g, 10, y, 13, 12, 13, "south", top=b("smooth_quartz"))
	g.set(12, y + 2, 13, b("dropper", facing="up", triggered=False))
	g.set(11, y + 2, 13, b("light_weighted_pressure_plate", power=0))
	chair(g, 11, y, 14, "north", "birch")
	carpet(g, 5, y, 7, 10, 7, "green")
	carpet(g, 6, y, 11, 9, 14, "green")
	lamp(g, 7, y, 8)
	lamp(g, 7, y, 12)
	lamp(g, 12, y, 10)
	plant(g, 14, y, 14, "potted_dead_bush")

	# ---- dispensary (z1..5, behind the counter) -------------------------------------------------
	g.set(1, y + 1, 4, barrel(PHARMACY, "east"))
	shelf_wall(g, 5, y, 1, 12, 1, "south", "birch", rows=(2, 3, 4))
	for x in (5, 7, 9, 11):
		g.set(x, y + 1, 1, barrel(PHARMACY, "south"))
	for x in (6, 8, 10, 12):
		g.set(x, y + 1, 1, barrel(GENERIC, "south"))
	g.set(14, y + 1, 2, b("potted_cactus"))
	g.set(14, y + 1, 3, b("smooth_quartz"))
	g.set(14, y + 1, 4, b("cauldron"))
	double_chest(g, 5, y, 4, "south", "east", PHARMACY)
	lamp(g, 9, y, 3)

	# ---- first floor ----------------------------------------------------------------------------
	y = F1
	g.fill(5, y + 1, 4, 14, y + 5, 4, inner)
	g.fill(10, y + 1, 1, 10, y + 3, 3, inner)
	g.fill(1, y + 1, 6, 14, y + 5, 6, inner)
	g.fill(10, y + 1, 7, 10, y + 5, 14, inner)
	door(g, 7, y, 4, "north", "birch")
	door(g, 12, y, 4, "north", "birch")
	door(g, 5, y, 6, "south", "birch")
	door(g, 12, y, 6, "south", "birch")
	# office x5..9 z1..3
	desk(g, 7, y, 1, "north", width=2, along="east")
	g.set(5, y + 1, 1, barrel(OFFICE, "east"))
	g.set(5, y + 2, 1, b("barrel", facing="east", open=False))
	bookcase(g, 9, y, 3, 9, 3, 2)
	lamp(g, 7, y, 2)
	# restroom x11..14 z1..3
	toilet(g, 11, y, 1, "south")
	sink(g, 14, y, 1, "south")
	g.fill(13, y + 1, 1, 13, y + 2, 2, b("white_stained_glass_pane"))
	toilet(g, 14, y, 3, "west")
	# stockroom x1..9 z7..14: racks of stock
	for rx in (3, 6):
		for z in range(8, 14):
			lootish = (z + rx) % 3 == 0
			g.set(rx, y + 1, z, barrel(PHARMACY if lootish else CLINIC, "west" if rx == 3 else "east") if lootish or z % 2 else b("barrel", facing="up", open=False))
			g.set(rx, y + 2, z, b("barrel", facing="up", open=False) if z % 2 else b("birch_planks"))
			g.set(rx, y + 3, z, slab("birch", "bottom"))
	shelf_wall(g, 1, y, 7, 1, 14, "east", "spruce", rows=(2, 3))
	shelf_wall(g, 9, y, 8, 9, 14, "west", "spruce", rows=(2, 3))
	g.set(8, y + 1, 14, chest(CLINIC, "north"))
	lamp(g, 4, y, 10)
	lamp(g, 7, y, 12)
	# break room x11..14 z7..14
	counter(g, 14, y, 7, 14, 9, "west")
	sink(g, 14, y, 10, "west")
	fridge(g, 14, y, 11, "west")
	g.set(14, y + 1, 13, barrel(GROCERY, "west"))
	table(g, 12, y, 11, 12, 12, "birch")
	chair(g, 11, y, 11, "east", "birch")
	chair(g, 11, y, 12, "east", "birch")
	chair(g, 12, y, 10, "south", "birch")
	chair(g, 12, y, 13, "north", "birch")
	g.fill(11, y + 1, 14, 12, y + 2, 14, b("red_concrete"))
	lamp(g, 12, y, 9)
	carpet(g, 1, y, 5, 3, 5, "gray")
	# roof plant
	y = RF
	g.fill(3, y + 1, 9, 5, y + 2, 10, b("light_gray_concrete"))
	g.fill(3, y + 3, 9, 5, y + 3, 10, b("iron_trapdoor", facing="north", half="bottom", open=False, powered=False, waterlogged=False))
	g.fill(10, y + 1, 3, 11, y + 1, 4, b("iron_block"))
	for dy in range(1, 4):
		g.set(13, y + dy, 13, b("lightning_rod", facing="up", powered=False))
	return {"grid": g, "cx": 1, "cz": 1, "cellars": 0, "floors": 2, "filler": "minecraft:light_gray_concrete",
			"entry": (7, F0 + 1, 14),
			"must_reach": [(9, F0 + 1, 3), (7, F1 + 1, 2), (12, F1 + 1, 2), (4, F1 + 1, 11), (12, F1 + 1, 9)]}


def bulkhead(g, x0, z0, x1, z1, y, wall, door_x, door_z, facing, cap):
	"""A roof stair house: walls round (x0..x1, z0..z1) above roof slab y, a cap, and a door."""
	g.walls(x0, y + 1, z0, x1, y + 3, z1, wall)
	g.fill(x0, y + 4, z0, x1, y + 4, z1, cap)
	door(g, door_x, y, door_z, facing, "spruce")


def office_desks(g, y, x0, z0, x1, z1, faces, table=OFFICE, step_x=3, step_z=3):
	"""Rows of two-wide desks with a chair each and a filing barrel at the end of every row."""
	for z in range(z0, z1 + 1, step_z):
		for x in range(x0, x1, step_x):
			g.set(x, y + 1, z, slab("spruce", "top"))
			g.set(x + 1, y + 1, z, slab("spruce", "top"))
			chair(g, x, y, z + (1 if faces == "north" else -1), faces, "dark_oak")
			if (x + z) % 2 == 0:
				g.set(x + 1, y + 2, z, b("note_block"))
		g.set(x1, y + 1, z, barrel(table, "west"))


# =================================================================================================
# Police station: 2x1 chunks. Cellar firing range + records, ground floor lobby/cells/bullpen, first
# floor lockers, evidence and the armory (per-player cache), roof access by the stair house.
# =================================================================================================
def police_station():
	g = Grid(32, 16, 26)
	C0, F0, F1, RF = 0, 6, 12, 18
	wall, band = b("light_gray_concrete"), b("blue_concrete")
	base = b("polished_andesite")
	inner = b("white_concrete")
	floor = b("polished_andesite")
	rail = b("dark_oak_fence")
	g.fill(0, C0, 0, 31, C0, 15, b("gray_concrete"))
	g.walls(0, 1, 0, 31, 5, 15, b("stone_bricks"))
	shell(g, 0, 0, 31, 15, 7, 17, wall, (F0, F1), floor, band=band)
	g.walls(0, 7, 0, 31, 7, 15, base)
	g.fill(0, RF, 0, 31, RF, 15, b("smooth_stone"))
	g.walls(0, RF, 0, 31, RF, 15, band)
	parapet(g, 0, 0, 31, 15, RF + 1, wall, slab("smooth_stone"))
	glass = b("light_blue_stained_glass_pane")
	for y in (F0, F1):
		for x0 in (2, 6, 23, 27):
			window_strip(g, x0, 15, x0 + 2, 15, y, glass)
		window_strip(g, 2, 0, 3, 0, y, glass)
		window_strip(g, 19, 0, 20, 0, y, glass)
		window_strip(g, 0, 9, 0, 12, y, glass)
		window_strip(g, 31, 9, 31, 12, y, glass)
	window_strip(g, 12, 15, 13, 15, F0, glass, sill=1, height=3)
	window_strip(g, 18, 15, 19, 15, F0, glass, sill=1, height=3)
	window_strip(g, 12, 15, 19, 15, F1, glass)
	double_door(g, 15, F0, 15, "south", "east", "dark_oak")
	g.fill(13, F1 + 1, 15, 18, F1 + 1, 15, band)  # sign band over the entrance
	door(g, 31, F0, 5, "east", "dark_oak")

	# Skeleton on every level: stairwell (x13..15, z1..4, rising north from the z=5 corridor), corridor
	# z5..6, north rooms z1..3 behind a wall at z=4, south rooms z8..14 behind a wall at z=7.
	for y in (C0, F0, F1):
		g.fill(12, y + 1, 1, 12, y + 5, 4, inner)
		g.fill(16, y + 1, 1, 16, y + 5, 4, inner)
	for y in (F0, F1):
		g.fill(1, y + 1, 4, 11, y + 5, 4, inner)
		g.fill(17, y + 1, 4, 30, y + 5, 4, inner)
		g.fill(1, y + 1, 7, 30, y + 5, 7, inner)
	for y, below in ((C0, False), (F0, True), (F1, True)):
		switchback(g, 15, 4, y, "north", "west", "dark_oak", b("dark_oak_planks"), rail, up=True, from_below=below)
	switchback(g, 15, 4, RF, "north", "west", "dark_oak", b("dark_oak_planks"), rail, up=False)

	# ---- ground floor -----------------------------------------------------------------------------
	y = F0
	# holding cells x1..11 z1..3: three cells, barred fronts, iron doors with buttons outside
	g.fill(4, y + 1, 1, 4, y + 5, 3, inner)
	g.fill(8, y + 1, 1, 8, y + 5, 3, inner)
	g.fill(1, y + 1, 4, 11, y + 4, 4, b("iron_bars"))
	for cx in (1, 5, 9):
		bed(g, cx, y, 2, "north", "gray")
		toilet(g, cx + 2, y, 1, "south")
		secure_door(g, cx + 1, y, 4, "south")
	# interview + observation + captain x17..30 z1..3
	g.fill(22, y + 1, 1, 22, y + 5, 3, inner)
	g.fill(25, y + 1, 1, 25, y + 5, 3, inner)
	g.fill(22, y + 2, 2, 22, y + 3, 2, b("glass_pane"))
	door(g, 19, y, 4, "north", "dark_oak")
	door(g, 23, y, 4, "north", "dark_oak")
	door(g, 27, y, 4, "north", "dark_oak")
	table(g, 19, y, 2, 20, 2, "dark_oak")
	chair(g, 19, y, 1, "south", "dark_oak")
	chair(g, 20, y, 3, "north", "dark_oak")
	lamp(g, 20, y, 2)
	chair(g, 23, y, 2, "west", "spruce")
	g.set(24, y + 1, 1, b("note_block"))
	desk(g, 28, y, 1, "north", width=2, along="east")
	g.set(30, y + 1, 3, barrel(POLICE, "west"))
	bookcase(g, 26, y, 1, 26, 1, 3)
	plant(g, 30, y, 1)
	lamp(g, 28, y, 2)
	# booking x1..9 z8..14
	g.fill(10, y + 1, 8, 10, y + 5, 14, inner)
	door(g, 5, y, 7, "south", "dark_oak")
	desk(g, 3, y, 10, "south", width=3, along="east")
	g.set(1, y + 1, 9, barrel(POLICE, "east"))
	g.set(1, y + 1, 10, barrel(POLICE, "east"))
	for z in (12, 13):
		chair(g, 9, y, z, "west", "spruce")
	g.fill(9, y + 2, 12, 9, y + 2, 13, b("iron_bars"))
	g.fill(1, y + 1, 14, 3, y + 4, 14, b("white_concrete"))
	g.fill(1, y + 2, 14, 3, y + 2, 14, b("black_concrete"))
	g.set(6, y + 1, 13, b("dispenser", facing="north", triggered=False))
	lamp(g, 5, y, 11)
	# lobby z11..14 x11..20, front counter with glass at z=10, clerk area z8..9
	g.fill(11, y + 1, 10, 18, y + 1, 10, b("smooth_quartz"))
	g.fill(11, y + 2, 10, 18, y + 4, 10, b("glass_pane"))
	g.fill(11, y + 5, 10, 20, y + 5, 10, inner)
	g.fill(20, y + 1, 10, 20, y + 4, 10, inner)
	secure_door(g, 19, y, 10, "south")
	g.fill(21, y + 1, 8, 21, y + 5, 14, inner)
	opening(g, 13, y, 7)
	opening(g, 18, y, 7)
	for z in (12, 13):
		chair(g, 11, y, z, "east", "spruce")
		chair(g, 20, y, z, "west", "spruce")
	plant(g, 11, y, 14)
	plant(g, 20, y, 14)
	carpet(g, 13, y, 11, 18, 14, "blue")
	chair(g, 14, y, 9, "south", "dark_oak")
	chair(g, 17, y, 9, "south", "dark_oak")
	g.set(12, y + 1, 8, barrel(OFFICE, "south"))
	lamp(g, 15, y, 12)
	# bullpen x22..30 z8..14
	door(g, 25, y, 7, "south", "dark_oak")
	office_desks(g, y, 23, 10, 29, 13, "north", OFFICE)
	g.set(30, y + 1, 9, b("cauldron"))
	g.set(30, y + 1, 8, b("smoker", facing="west", lit=False))
	g.fill(22, y + 2, 14, 24, y + 3, 14, b("white_concrete"))
	lamp(g, 26, y, 11)
	carpet(g, 1, y, 5, 30, 6, "gray")

	# ---- first floor ------------------------------------------------------------------------------
	y = F1
	# locker room x1..11 z1..3
	door(g, 6, y, 4, "north", "dark_oak")
	for x in range(1, 12, 1):
		if x in (6,):
			continue
		g.set(x, y + 1, 1, barrel(POLICE if x % 3 == 0 else HOME, "south") if x % 2 else b("iron_block"))
		g.set(x, y + 2, 1, b("iron_trapdoor", facing="south", half="bottom", open=True, powered=False, waterlogged=False) if x % 2 else b("iron_block"))
	for x in (3, 4, 8, 9):
		chair(g, x, y, 3, "north", "spruce")
	g.fill(11, y + 1, 2, 11, y + 3, 3, b("white_stained_glass_pane"))
	lamp(g, 6, y, 2)
	# evidence x17..22 z1..3
	g.fill(23, y + 1, 1, 23, y + 5, 3, b("polished_blackstone_bricks"))
	door(g, 19, y, 4, "north", "dark_oak")
	for x in (17, 18, 20, 21, 22):
		g.set(x, y + 1, 1, barrel(POLICE if x % 2 else OFFICE, "south"))
		g.set(x, y + 2, 1, b("barrel", facing="south", open=False))
	shelf_wall(g, 17, y, 1, 22, 1, "south", "spruce", rows=(3,))
	g.fill(22, y + 1, 2, 22, y + 3, 3, b("iron_bars"))
	lamp(g, 20, y, 2)
	# ARMORY x24..30 z1..3, blackstone walls, iron door with buttons; the per-player cache sits at the back
	g.fill(24, y + 1, 4, 30, y + 5, 4, b("polished_blackstone_bricks"))
	secure_door(g, 27, y, 4, "south")
	g.set(27, y + 1, 1, chest(POLICE_ARMORY, "south"))
	shelf_wall(g, 24, y, 1, 26, 1, "south", "dark_oak", rows=(2, 3))
	shelf_wall(g, 28, y, 1, 30, 1, "south", "dark_oak", rows=(2, 3))
	g.set(24, y + 1, 3, barrel(POLICE, "east"))
	g.set(24, y + 1, 2, barrel(POLICE, "east"))
	g.set(30, y + 1, 3, b("smithing_table"))
	g.set(30, y + 1, 2, b("anvil", facing="north"))
	g.fill(24, y + 4, 1, 30, y + 4, 1, b("iron_bars"))
	lamp(g, 27, y, 2)
	# offices x1..10 z8..14
	g.fill(5, y + 1, 8, 5, y + 5, 14, inner)
	g.fill(10, y + 1, 8, 10, y + 5, 14, inner)
	door(g, 3, y, 7, "south", "dark_oak")
	door(g, 8, y, 7, "south", "dark_oak")
	for ox in (1, 6):
		desk(g, ox + 1, y, 13, "south", width=2, along="east")
		g.set(ox, y + 1, 9, barrel(OFFICE, "east"))
		g.set(ox + 3, y + 1, 9, b("bookshelf"))
		lamp(g, ox + 2, y, 11)
	# conference x11..20 z8..14
	g.fill(21, y + 1, 8, 21, y + 5, 14, inner)
	door(g, 15, y, 7, "south", "dark_oak")
	table(g, 13, y, 10, 18, 11, "dark_oak")
	for x in range(13, 19):
		chair(g, x, y, 9, "south", "spruce")
		chair(g, x, y, 12, "north", "spruce")
	g.fill(13, y + 2, 14, 18, y + 3, 14, b("white_concrete"))
	lamp(g, 15, y, 10)
	lamp(g, 17, y, 11)
	# break room x22..25 and restrooms x27..30
	g.fill(26, y + 1, 8, 26, y + 5, 14, inner)
	door(g, 23, y, 7, "south", "dark_oak")
	door(g, 28, y, 7, "south", "birch")
	counter(g, 22, y, 14, 25, 14, "north")
	sink(g, 23, y, 14, "north")
	fridge(g, 25, y, 13, "west")
	table(g, 23, y, 10, 24, 11, "birch")
	chair(g, 22, y, 10, "east", "birch")
	chair(g, 25, y, 11, "west", "birch")
	for x in (27, 29):
		toilet(g, x, y, 14, "north")
	g.fill(28, y + 1, 12, 28, y + 2, 14, b("white_stained_glass_pane"))
	sink(g, 30, y, 9, "west")
	sink(g, 30, y, 10, "west")
	carpet(g, 1, y, 5, 30, 6, "blue")

	# ---- cellar: records/server room west, firing range east -------------------------------------
	y = C0
	g.set(13, y + 1, 4, barrel(OFFICE, "east"))
	g.fill(1, y + 1, 7, 15, y + 5, 7, inner)
	door(g, 8, y, 7, "south", "iron")
	g.openable.update({(8, y + 1, 7), (8, y + 2, 7)})
	g.set(9, y + 2, 6, b("stone_button", face="wall", facing="north", powered=False))
	g.set(9, y + 2, 8, b("stone_button", face="wall", facing="south", powered=False))
	for rx in (2, 5, 8, 11, 14):
		for z in range(9, 14):
			g.set(rx, y + 1, z, barrel(OFFICE, "east") if (z + rx) % 4 == 0 else b("barrel", facing="up", open=False))
			g.set(rx, y + 2, z, b("barrel", facing="up", open=False))
	g.fill(1, y + 1, 1, 1, y + 3, 3, b("iron_block"))
	g.fill(3, y + 1, 1, 3, y + 3, 3, b("iron_block"))
	g.fill(1, y + 2, 2, 1, y + 2, 2, b("observer", facing="east", powered=False))
	g.fill(3, y + 2, 2, 3, y + 2, 2, b("observer", facing="west", powered=False))
	for x in (5, 7, 9):
		g.fill(x, y + 1, 1, x, y + 2, 2, b("smooth_stone"))
		chair(g, x, y, 3, "north", "spruce")
	lamp(g, 8, y, 5)
	lamp(g, 8, y, 11)
	# range x17..30 z1..14: door from the corridor end
	g.fill(16, y + 1, 5, 16, y + 5, 14, inner)
	door(g, 16, y, 6, "east", "dark_oak")
	for x in range(18, 30):
		g.set(x, y + 1, 11, slab("smooth_stone", "top"))
	for x in (20, 23, 26):
		g.fill(x, y + 1, 10, x, y + 3, 12, b("iron_bars"))
	for x in (19, 22, 25, 28):
		g.set(x, y + 1, 2, b("hay_block", axis="y"))
		g.set(x, y + 2, 2, b("target", power=0))
	g.fill(17, y + 1, 1, 30, y + 1, 1, b("sand"))
	g.fill(29, y + 1, 13, 30, y + 1, 14, barrel(POLICE, "up"))
	for x in (19, 24, 29):
		lamp(g, x, y, 6)
	lamp(g, 23, y, 13)

	# ---- roof ---------------------------------------------------------------------------------------
	y = RF
	bulkhead(g, 12, 0, 16, 6, y, wall, 14, 6, "south", slab("smooth_stone"))
	for dy in range(1, 7):
		g.set(5, y + dy, 3, b("lightning_rod", facing="up", powered=False) if dy > 2 else b("iron_bars"))
	g.fill(22, y + 1, 9, 24, y + 2, 11, b("light_gray_concrete"))
	g.fill(27, y + 1, 9, 28, y + 2, 10, b("light_gray_concrete"))
	g.set(8, y + 1, 11, b("white_concrete"))
	g.set(8, y + 2, 11, b("white_concrete"))
	g.set(8, y + 2, 12, b("iron_trapdoor", facing="south", half="top", open=True, powered=False, waterlogged=False))
	for y in (F0, F1):
		g.replace(1, y + 1, 1, 30, y + 1, 14, inner, b("gray_concrete"))
	return {"grid": g, "cx": 2, "cz": 1, "cellars": 1, "floors": 2, "filler": "minecraft:light_gray_concrete",
			"entry": (15, F0 + 1, 14),
			"must_reach": [(2, F0 + 1, 2), (20, F0 + 1, 1), (28, F0 + 1, 3), (5, F0 + 1, 9), (25, F0 + 1, 12),
						   (27, F1 + 1, 2), (8, F1 + 1, 2), (20, F1 + 1, 2), (15, F1 + 1, 13), (24, F1 + 1, 12),
						   (7, C0 + 1, 10), (24, C0 + 1, 12), (20, RF + 1, 12)]}


def all_landmarks():
	import hospital
	import industrial
	return {
		"safehouse": safehouse(),
		"pharmacy": pharmacy(),
		"police_station": police_station(),
		"hospital": hospital.hospital(),
		"kiln_warehouse": industrial.kiln_warehouse(),
		"substation_river_arts": industrial.substation_river_arts(),
		"substation_south_slope": industrial.substation_south_slope(),
		"substation_downtown": industrial.substation_downtown(),
		"substation_mission": industrial.substation_mission(),
	}
