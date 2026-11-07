"""Furniture, stairs and facade pieces built from vanilla blocks, shared by every landmark.

Everything takes a floor base ``y`` (the slab's height) so a call places its blocks in the room above.
Directions are 'north'/'south'/'east'/'west'. No piece places a light source: Ashfall's cities are dark
until the grid is restored, and the only lamps are ``dead_lamp`` markers that the relight system logs.
"""
from lcgen import AIR, CCW, CW, DIRS, OPP, Loot, b, is_solid

# Marker blocks. Real Ashfall blocks replace these once they exist; data/ashfall/tags/block/landmark/*
# lists them so code can find markers without hard-coding vanilla ids.
# SUBSTATION_CORE stays a vanilla placeholder for good: salvage owns these assets and sits below arms, so a palette
# naming ashfall:substation_core would break salvage-only worlds. Arms swaps the marker for the real core when the
# chunk first loads (arms grid/CoreConversion.java).
# heavy_core, not lodestone: lodestone is a POI block, which Lost Cities places as dirt and swaps in after
# generation, and that swap was lost for cores on upper floors.
SUBSTATION_CORE = b("heavy_core", waterlogged=False)
DEAD_LAMP = b("redstone_lamp", lit=False)
BOSS_MARKER = b("reinforced_deepslate")


def stair(mat, facing, half="bottom", shape="straight"):
	return b(f"{mat}_stairs", facing=facing, half=half, shape=shape, waterlogged=False)


def slab(mat, kind="bottom"):
	return b(f"{mat}_slab", type=kind, waterlogged=False)


def trapdoor(mat, facing, half="bottom", open_=False):
	return b(f"{mat}_trapdoor", facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def step(x, z, d, n=1):
	dx, dz = DIRS[d]
	return x + dx * n, z + dz * n


def door(g, x, y, z, facing, mat="spruce", hinge="left"):
	"""A closed door standing on slab ``y`` (blocks y+1, y+2). ``facing`` is the side it opens away from."""
	g.set(x, y + 1, z, b(f"{mat}_door", facing=facing, half="lower", hinge=hinge, open=False, powered=False))
	g.set(x, y + 2, z, b(f"{mat}_door", facing=facing, half="upper", hinge=hinge, open=False, powered=False))


def secure_door(g, x, y, z, facing):
	"""An iron door with a stone button beside it on both faces of the wall: shut to zombies, opened by
	players. The wall must run across ``facing`` (the door's neighbours along the wall are solid)."""
	door(g, x, y, z, facing, "iron")
	g.openable.update({(x, y + 1, z), (x, y + 2, z)})
	for side in (facing, OPP[facing]):
		for along in (CW[facing], CCW[facing]):
			ax, az = step(x, z, along)
			bx, bz = step(ax, az, side)
			if is_solid(g.get(ax, y + 2, az)) and g.get(bx, y + 2, bz) in (None, AIR):
				g.set(bx, y + 2, bz, b("stone_button", face="wall", facing=side, powered=False))
				break


def sealed_door(g, x, y, z, facing):
	"""An iron door with no button anywhere: the Patient Zero deep ward stays shut until code opens it."""
	door(g, x, y, z, facing, "iron")


def double_door(g, x, y, z, facing, along, mat="spruce"):
	"""Two doors side by side: (x, z) and one step ``along``."""
	x2, z2 = step(x, z, along)
	left_is_first = along == CW[facing]
	door(g, x, y, z, facing, mat, "left" if left_is_first else "right")
	door(g, x2, y, z2, facing, mat, "right" if left_is_first else "left")


def opening(g, x, y, z, height=2):
	for dy in range(1, height + 1):
		g.set(x, y + dy, z, AIR)


def bed(g, x, y, z, facing, color="white"):
	"""Foot at (x, z), head one step towards ``facing``."""
	hx, hz = step(x, z, facing)
	g.set(x, y + 1, z, b(f"{color}_bed", facing=facing, part="foot", occupied=False))
	g.set(hx, y + 1, hz, b(f"{color}_bed", facing=facing, part="head", occupied=False))


def chest(table, facing, kind="single"):
	return Loot(b("chest", facing=facing, type=kind, waterlogged=False), table)


def double_chest(g, x, y, z, facing, along, table):
	"""A double chest at (x, z) and one step ``along``; both halves roll the same table."""
	x2, z2 = step(x, z, along)
	# A LEFT half connects to the block clockwise of its facing.
	first = "left" if along == CW[facing] else "right"
	second = "right" if first == "left" else "left"
	g.set(x, y + 1, z, chest(table, facing, first))
	g.set(x2, y + 1, z2, chest(table, facing, second))


def barrel(table, facing):
	return Loot(b("barrel", facing=facing, open=False), table)


def chair(g, x, y, z, faces, mat="spruce"):
	"""A stair seat whose sitter looks towards ``faces`` (the back is on the opposite side)."""
	g.set(x, y + 1, z, stair(mat, OPP[faces]))


def table(g, x0, y, z0, x1, z1, mat="spruce"):
	"""A table top of upside-down slabs (surface at seat height + 1)."""
	g.fill(x0, y + 1, z0, x1, y + 1, z1, slab(mat, "top"))


def desk(g, x, y, z, faces, mat="spruce", chair_mat="dark_oak", width=2, along=None):
	"""A desk against a wall with a chair in front; ``faces`` is the direction the sitter looks."""
	along = along or CW[faces]
	cx, cz = x, z
	for i in range(width):
		g.set(cx, y + 1, cz, slab(mat, "top"))
		cx, cz = step(cx, cz, along)
	sx, sz = step(x, z, OPP[faces])
	chair(g, sx, y, sz, faces, chair_mat)


def couch(g, x, y, z, faces, along, length=3, mat="dark_oak", arm="spruce"):
	"""A couch of stairs with open trapdoor armrests; the sitter looks towards ``faces``."""
	cx, cz = x, z
	for _ in range(length):
		g.set(cx, y + 1, cz, stair(mat, OPP[faces]))
		cx, cz = step(cx, cz, along)
	ax, az = step(x, z, OPP[along])
	g.set(ax, y + 1, az, trapdoor(arm, OPP[along], open_=True))
	g.set(cx, y + 1, cz, trapdoor(arm, along, open_=True))


def toilet(g, x, y, z, faces):
	"""Cauldron bowl with a raised lid (an open trapdoor) against the wall behind it."""
	g.set(x, y + 1, z, b("cauldron"))
	g.set(x, y + 2, z, trapdoor("birch", faces, open_=True))


def sink(g, x, y, z, faces):
	"""Cauldron basin with a tripwire-hook tap on the wall behind it."""
	g.set(x, y + 1, z, b("cauldron"))
	bx, bz = step(x, z, OPP[faces])
	if is_solid(g.get(bx, y + 2, bz)):
		g.set(x, y + 2, z, b("tripwire_hook", facing=faces, attached=False, powered=False))


def bathtub(g, x, y, z, along):
	x2, z2 = step(x, z, along)
	g.set(x, y + 1, z, b("cauldron"))
	g.set(x2, y + 1, z2, b("cauldron"))


def fridge(g, x, y, z, faces):
	g.set(x, y + 1, z, b("white_concrete"))
	g.set(x, y + 2, z, b("white_concrete"))
	fx, fz = step(x, z, faces)
	g.set(fx, y + 2, fz, b("lever", face="wall", facing=faces, powered=False))


def stove(g, x, y, z, faces):
	g.set(x, y + 1, z, b("smoker", facing=faces, lit=False))


def counter(g, x0, y, z0, x1, z1, faces, table=None, top=None):
	"""A run of kitchen/shop counter. With a loot table the cabinets are lootable barrels."""
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			if table:
				g.set(x, y + 1, z, barrel(table, faces))
			else:
				g.set(x, y + 1, z, top or b("smooth_quartz"))


def shelf_wall(g, x0, y, z0, x1, z1, faces, mat="oak", rows=(2, 3)):
	"""Wall shelving (the 26.x shelf block) at the given heights above the slab."""
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			for r in rows:
				g.set(x, y + r, z, b(f"{mat}_shelf", facing=faces, powered=False, side_chain="unconnected", waterlogged=False))


def bookcase(g, x0, y, z0, x1, z1, height=3):
	g.fill(x0, y + 1, z0, x1, y + height, z1, b("bookshelf"))


def tv(g, x, y, z, faces):
	"""Dead television on a low cabinet."""
	g.set(x, y + 1, z, b("dark_oak_planks"))
	g.set(x, y + 2, z, b("black_concrete"))


def plant(g, x, y, z, kind="potted_dead_bush"):
	g.set(x, y + 1, z, b(kind))


def lamp(g, x, y, z, ceiling=5):
	"""A dead ceiling lamp: an unlit redstone lamp hanging from the slab above on an iron chain."""
	g.set(x, y + ceiling, z, DEAD_LAMP)


def carpet(g, x0, y, z0, x1, z1, color="gray"):
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			if g.get(x, y + 1, z) in (None, AIR):
				g.set(x, y + 1, z, b(f"{color}_carpet"))


def window_strip(g, x0, z0, x1, z1, y, glass, sill=2, height=2, every=1, skip=()):
	"""Windows cut into an existing wall line at y+sill .. y+sill+height-1."""
	i = 0
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			if (x, z) not in skip and i % every == 0:
				for dy in range(sill, sill + height):
					g.set(x, y + dy, z, glass)
			i += 1


# -- stairs ---------------------------------------------------------------------------------------
def switchback(g, ox, oz, y, forward, side, mat, solid, rail, up=True, from_below=False):
	"""A U-shaped staircase climbing one 6-block floor inside a 4 x 3 footprint.

	Local axes: u runs ``forward`` from the corridor (u = -1, left clear by the caller) to the far wall
	(u = 4), v runs ``side`` across three lanes. Climbing: lane v=0 at u=0..2 (heights 1..3), a landing
	across all lanes at u=3 (height 3), then lane v=2 back at u=2, u=1 (heights 4, 5) to arrive on the
	next slab at u=0, v=2. Lane v=1 is a stepped half-wall topped with fence balusters (the railing).
	The next slab gets a hole over lane 2 (u=1..3) for headroom. Floors are stacked: the arrival from the
	floor below lands on lane 2, u=0 of this floor, so lane 2 is never filled when ``from_below``.
	With ``up`` false the floor only receives a staircase from below and gets a railing round the hole.
	"""
	fx, fz = DIRS[forward]
	sx, sz = DIRS[side]

	def at(u, v):
		return ox + u * fx + v * sx, oz + u * fz + v * sz

	back = OPP[forward]
	if up:
		for u in range(3):
			x, z = at(u, 0)
			g.set(x, y + 1 + u, z, stair(mat, forward))
			for dy in range(1, 1 + u):
				g.set(x, y + dy, z, solid)
		for v in range(3):
			x, z = at(3, v)
			g.fill(x, y + 1, z, x, y + 3, z, solid)
		for u, h in ((2, 4), (1, 5)):
			x, z = at(u, 2)
			g.set(x, y + h, z, stair(mat, back))
			if not from_below:
				g.fill(x, y + 1, z, x, y + h - 1, z, solid)
		for u in range(3):
			x, z = at(u, 1)
			g.fill(x, y + 1, z, x, y + 1 + u, z, solid)
			for dy in range(2 + u, 6):
				g.set(x, y + dy, z, rail)
		for u in (1, 2, 3):
			x, z = at(u, 2)
			g.set(x, y + 6, z, AIR)
		# The last step sits in the next floor's slab: six one-block rises need six stairs.
		x, z = at(0, 2)
		g.set(x, y + 6, z, stair(mat, back))
	else:
		for u in (1, 2, 3):
			x, z = at(u, 1)
			g.set(x, y + 1, z, rail)
	return at


def ramp(g, x, y, z, forward, width_dir, width, length, top_mat, bottom_mat):
	"""A slab ramp rising one block every two steps (half a block per step), walkable without jumping.
	Rises length/2 blocks over ``length`` cells starting at slab height y (first cell is a bottom slab
	at y+1)."""
	fx, fz = DIRS[forward]
	wx, wz = DIRS[width_dir]
	for i in range(length):
		h = y + 1 + i // 2
		kind = "bottom" if i % 2 == 0 else "top"
		for w in range(width):
			cx, cz = x + fx * i + wx * w, z + fz * i + wz * w
			g.set(cx, h, cz, top_mat if kind == "top" else bottom_mat)
			if h - 1 > y:
				g.fill(cx, y + 1, cz, cx, h - 1, cz, b("smooth_stone"))
