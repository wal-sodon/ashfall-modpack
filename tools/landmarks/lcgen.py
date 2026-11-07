"""Tiny voxel toolkit that writes Lost Cities parts, buildings and multibuildings.

Why a generator instead of Lost Cities' in-game edit mode: the landmarks are big (the hospital is
48x32 blocks over five levels), share a lot of furniture and stair logic, and must be reproducible and
reviewable in a diff. A building is modelled as one voxel grid; ``emit`` slices it into Lost Cities'
16x16 chunk columns and 6-block floors, gives every part its own palette (so nothing depends on the city
style's palette), and wires the building/multibuilding JSON with fixed floor and cellar counts.

Coordinates: x grows east, z grows south, y grows up. y = 0 is the floor slab of the lowest cellar.
Lost Cities floors are 6 blocks: the slab at 6*L and five blocks of room above it.
"""
import json
import re
from pathlib import Path

AIR = "minecraft:air"
FLOOR_HEIGHT = 6

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}


def b(name, **props):
	"""A block state string; ``b('oak_stairs', facing='east')`` -> 'minecraft:oak_stairs[facing=east]'."""
	if ":" not in name:
		name = "minecraft:" + name
	if not props:
		return name
	return name + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in sorted(props.items())) + "]"


class Loot:
	"""A container whose block entity points at a loot table (written as the palette entry's ``tag``)."""

	def __init__(self, state, table):
		self.state = state
		self.table = table

	def key(self):
		return (self.state, self.table)


def block_id(state):
	return state.split("[", 1)[0]


def props_of(state):
	if "[" not in state:
		return {}
	inner = state.split("[", 1)[1].rstrip("]")
	return dict(p.split("=", 1) for p in inner.split(",") if p)


def with_props(state, **extra):
	p = props_of(state)
	p.update({k: str(v).lower() for k, v in extra.items()})
	name = block_id(state)
	return name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(p.items())) + "]" if p else "")


# Blocks that do not count as a full face for pane/fence connections.
_NON_SOLID = re.compile(r"(stairs|slab|door|carpet|_bed|pane|fence|bars|chest|cauldron|pressure_plate|button|lever|sign|"
						r"potted|flower_pot|rail|torch|lantern|chain|shelf|lectern|bell|hopper|anvil|banner|_wall$|_rod|grindstone|"
						r"campfire|sapling|dead_bush|candle|air|hook|water|lava|brewing|composter|stonecutter|scaffolding|"
						r"skull|head|ladder|vine|cobweb|snow$|leaves|glass_pane|end_rod|flower|fern|grass$|short_grass|bush)")


def is_solid(state):
	if state is None:
		return False
	if isinstance(state, Loot):
		state = state.state
	name = block_id(state).split(":", 1)[1]
	if name in ("barrel", "crafting_table", "furnace", "smoker", "blast_furnace", "bookshelf", "chiseled_bookshelf",
				"lodestone", "redstone_lamp", "note_block", "jukebox", "loom", "cartography_table", "fletching_table",
				"smithing_table", "dispenser", "dropper", "observer", "piston", "sticky_piston", "target", "beehive"):
		return True
	return not _NON_SOLID.search(name)


def family(state):
	"""Connection family for panes, bars and fences."""
	if state is None or isinstance(state, Loot):
		return None
	name = block_id(state)
	if name.endswith("_pane") or name.endswith("_bars") or name == "minecraft:iron_bars":
		return "pane"
	if name.endswith("_fence"):
		return "nether" if "nether" in name else "fence"
	if name.endswith("_wall") and "sign" not in name:
		return "wall"
	return None


class Grid:
	def __init__(self, sx, sz, sy):
		self.sx, self.sz, self.sy = sx, sz, sy
		self.cells = {}
		# Iron doors that have buttons: shut to mobs, but a player can open them (see reachable()).
		self.openable = set()

	# -- primitives -------------------------------------------------------------------------------
	def inside(self, x, y, z):
		return 0 <= x < self.sx and 0 <= z < self.sz and 0 <= y < self.sy

	def set(self, x, y, z, state):
		if not self.inside(x, y, z):
			raise ValueError(f"({x},{y},{z}) outside {self.sx}x{self.sy}x{self.sz}")
		if state is None:
			self.cells.pop((x, y, z), None)
		else:
			self.cells[(x, y, z)] = state

	def get(self, x, y, z):
		return self.cells.get((x, y, z))

	def fill(self, x0, y0, z0, x1, y1, z1, state):
		for x in range(min(x0, x1), max(x0, x1) + 1):
			for y in range(min(y0, y1), max(y0, y1) + 1):
				for z in range(min(z0, z1), max(z0, z1) + 1):
					self.set(x, y, z, state)

	def clear(self, x0, y0, z0, x1, y1, z1):
		self.fill(x0, y0, z0, x1, y1, z1, AIR)

	def walls(self, x0, y0, z0, x1, y1, z1, state):
		"""The four vertical sides of a box (no floor or ceiling)."""
		for y in range(y0, y1 + 1):
			for x in range(x0, x1 + 1):
				self.set(x, y, z0, state)
				self.set(x, y, z1, state)
			for z in range(z0, z1 + 1):
				self.set(x0, y, z, state)
				self.set(x1, y, z, state)

	def replace(self, x0, y0, z0, x1, y1, z1, old, new):
		for x in range(x0, x1 + 1):
			for y in range(y0, y1 + 1):
				for z in range(z0, z1 + 1):
					if self.get(x, y, z) == old:
						self.set(x, y, z, new)

	# -- post processing --------------------------------------------------------------------------
	def connect(self):
		"""Sets the side properties of panes, bars, fences and walls from their neighbours, since Lost
		Cities writes states verbatim with no neighbour updates (an unconnected pane renders as a post)."""
		updates = {}
		for (x, y, z), st in self.cells.items():
			fam = family(st)
			if fam is None:
				continue
			sides = {}
			for d, (dx, dz) in DIRS.items():
				n = self.get(x + dx, y, z + dz)
				nf = family(n)
				ok = nf == fam or (fam in ("pane", "wall") and nf in ("pane", "wall")) or (is_solid(n) and n != AIR)
				if fam == "wall":
					sides[d] = "low" if ok else "none"
				else:
					sides[d] = "true" if ok else "false"
			if fam == "wall":
				sides["up"] = "true"
			updates[(x, y, z)] = with_props(st, **sides)
		self.cells.update(updates)


# -- emitting -------------------------------------------------------------------------------------
_POOL = [c for c in (chr(i) for i in range(33, 127)) if c not in '"\\'] + [chr(i) for i in range(0xC0, 0x250)]


def _part_json(grid, x0, z0, y0, height, name):
	"""One 16x16 part: slices[y][z] = row of 16 chars indexed by x, plus its own palette."""
	keys = {}
	slices = []
	for y in range(y0, y0 + height):
		rows = []
		for z in range(z0, z0 + 16):
			row = []
			for x in range(x0, x0 + 16):
				st = grid.get(x, y, z)
				if st is None or st == AIR:
					row.append(" ")
					continue
				k = st.key() if isinstance(st, Loot) else st
				if k not in keys:
					if len(keys) >= len(_POOL):
						raise ValueError(f"part {name} needs more than {len(_POOL)} palette entries")
					keys[k] = (_POOL[len(keys)], st)
				row.append(keys[k][0])
			rows.append("".join(row))
		slices.append(rows)
	palette = [{"char": " ", "block": AIR}]
	for k, (ch, st) in keys.items():
		if isinstance(st, Loot):
			palette.append({"char": ch, "block": st.state, "tag": {"LootTable": st.table}})
		else:
			palette.append({"char": ch, "block": st})
	return {
		"xsize": 16, "zsize": 16,
		"meta": [{"key": "dontconnect", "boolean": True}],
		"palette": {"palette": palette},
		"slices": slices,
	}


def emit(grid, out_root, ns, name, chunks_x, chunks_z, cellars, floors, filler):
	"""Writes parts/buildings (and a multibuilding when larger than one chunk) for a landmark grid.

	``floors`` counts the storeys below the roof part; the roof ("top") part starts at
	(cellars + floors) * 6 and holds everything up to the top of the grid.
	"""
	out_root = Path(out_root)
	parts_dir = out_root / "parts" / "landmarks" / name
	parts_dir.mkdir(parents=True, exist_ok=True)
	(out_root / "buildings").mkdir(parents=True, exist_ok=True)
	levels = cellars + floors
	top_height = grid.sy - levels * FLOOR_HEIGHT
	if top_height < 1:
		raise ValueError(f"{name}: no room for a roof part")
	building_ids = [[None] * chunks_z for _ in range(chunks_x)]
	for cx in range(chunks_x):
		for cz in range(chunks_z):
			bname = name if chunks_x == chunks_z == 1 else f"{name}_{cx}{cz}"
			part_refs = []
			for level in range(levels + 1):
				floor = level - cellars
				height = FLOOR_HEIGHT if level < levels else top_height
				pname = f"{bname}_{'c' + str(-floor) if floor < 0 else ('top' if level == levels else 'f' + str(floor))}"
				part = _part_json(grid, cx * 16, cz * 16, level * FLOOR_HEIGHT, height, pname)
				(parts_dir / f"{pname}.json").write_text(json.dumps(part, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
				part_refs.append({"part": f"{ns}:landmarks/{name}/{pname}", "floor": floor})
			building = {
				"filler": "#",
				"palette": {"palette": [{"char": "#", "block": filler}]},
				"allowDoors": False,
				"allowFillers": False,
				"overrideFloors": True,
				"minfloors": floors, "maxfloors": floors,
				"mincellars": cellars, "maxcellars": cellars,
				"parts": part_refs,
			}
			(out_root / "buildings" / f"{bname}.json").write_text(json.dumps(building, indent=1) + "\n", encoding="utf-8")
			building_ids[cx][cz] = f"{ns}:{bname}"
	if chunks_x * chunks_z > 1:
		(out_root / "multibuildings").mkdir(parents=True, exist_ok=True)
		multi = {"dimx": chunks_x, "dimz": chunks_z, "buildings": building_ids}
		(out_root / "multibuildings" / f"{name}.json").write_text(json.dumps(multi, indent=1) + "\n", encoding="utf-8")


def plan(grid, y, legend=None):
	"""ASCII floor plan at height y, for eyeballing layouts while authoring."""
	out = []
	for z in range(grid.sz):
		row = []
		for x in range(grid.sx):
			st = grid.get(x, y, z)
			if st is None or st == AIR:
				row.append(".")
				continue
			n = block_id(st.state if isinstance(st, Loot) else st).split(":")[1]
			if isinstance(st, Loot):
				c = "C"
			elif "stairs" in n:
				c = {"north": "^", "south": "v", "east": ">", "west": "<"}.get(props_of(st).get("facing"), "s")
			elif "door" in n and "trap" not in n:
				c = "D"
			elif "pane" in n or "bars" in n:
				c = "="
			elif "fence" in n:
				c = "f"
			elif "_bed" in n:
				c = "B"
			elif "slab" in n:
				c = "_"
			elif is_solid(st):
				c = "#"
			else:
				c = "o"
			row.append(c)
		out.append("".join(row))
	return "\n".join(out)


# -- validation -----------------------------------------------------------------------------------
_EXTRA_PROPS = {"waterlogged", "powered", "occupied", "triggered"}


def validate(grid, blockstates_dir):
	"""Checks every block id exists in 26.3 and every property name appears in its blockstate file."""
	bs = Path(blockstates_dir)
	cache = {}
	errors = set()
	for st in set(s.state if isinstance(s, Loot) else s for s in grid.cells.values()):
		name = block_id(st)
		ns, path = name.split(":")
		if ns != "minecraft":
			continue
		if path not in cache:
			f = bs / f"{path}.json"
			if not f.exists():
				cache[path] = None
			else:
				text = f.read_text()
				# Entity-rendered blocks (chests, signs) have a single "" variant: no property names to check.
				cache[path] = {"*"} if re.search(r'"variants"\s*:\s*\{\s*""', text) else set(re.findall(r'([a-z_0-9]+)=', text)) | set(re.findall(r'"([a-z_0-9]+)"\s*:\s*"', text))
		known = cache[path]
		if known is None:
			if path != "air":
				errors.add(f"unknown block {name}")
			continue
		for p in props_of(st):
			if "*" not in known and p not in known and p not in _EXTRA_PROPS:
				errors.add(f"{name}: unknown property {p}")
	return sorted(errors)


# -- navigability -----------------------------------------------------------------------------------
_PASSABLE = re.compile(r"(air|carpet|_door|pressure_plate|button|lever|tripwire_hook|dead_bush|flower|sapling|"
					   r"short_grass|fern|cobweb|redstone_wire|rail)")


def _passable(st):
	if st is None:
		return True
	if isinstance(st, Loot):
		return False
	name = block_id(st)
	if "iron_door" in name:
		return False  # sealed: only redstone opens it
	if "trapdoor" in name:
		return props_of(st).get("open") == "true" and props_of(st).get("half") != "top"
	return bool(_PASSABLE.search(name))


def _half(st):
	"""True for blocks a player stands half a block up on (bottom slabs, stairs)."""
	if st is None or isinstance(st, Loot):
		return False
	name = block_id(st)
	return "stairs" in name or ("slab" in name and props_of(st).get("type") == "bottom")


def reachable(grid, start):
	"""Cells a player can walk to from ``start`` (feet block x, y, z) without jumping: steps of at most
	half a block up (stairs, slabs), drops of up to three blocks, doors count as open, iron doors as shut.
	Returns the set of standable feet cells reached and the set of all standable cells."""
	def free(x, y, z):
		return (x, y, z) in grid.openable or _passable(grid.get(x, y, z))

	def stand_h(x, y, z):
		"""Feet height in half blocks if a player can stand with feet in cell y, else None."""
		here, below = grid.get(x, y, z), grid.get(x, y - 1, z)
		if _half(here) and free(x, y + 1, z) and free(x, y + 2, z):
			return 2 * y + 1
		if free(x, y, z) and free(x, y + 1, z) and not free(x, y - 1, z) and not _half(below):
			return 2 * y
		return None

	standable = {}
	for x in range(grid.sx):
		for z in range(grid.sz):
			for y in range(1, grid.sy - 1):
				h = stand_h(x, y, z)
				if h is not None:
					standable[(x, y, z)] = h
	def stair_facing(x, y, z):
		st = grid.get(x, y, z)
		if st is None or isinstance(st, Loot) or "stairs" not in block_id(st) or props_of(st).get("half") == "top":
			return None
		return props_of(st).get("facing")

	def edge_h(c, d, leaving):
		"""Height at the edge of cell c on side d: a stair is high on its facing side, low on the other."""
		x, y, z = c
		f = stair_facing(x, y, z)
		if f is None:
			return standable[c]
		high = (d == f) if leaving else (d == OPP[f])
		return 2 * y + 2 if high else 2 * y + 1

	seen = set()
	todo = [start]
	if start not in standable:
		raise ValueError(f"start {start} is not standable")
	while todo:
		c = todo.pop()
		if c in seen:
			continue
		seen.add(c)
		x, y, z = c
		for d, (dx, dz) in DIRS.items():
			h = edge_h(c, d, True)
			for ny in range(y - 3, y + 2):
				n = (x + dx, ny, z + dz)
				if n not in standable or n in seen:
					continue
				nh = edge_h(n, d, False)
				if nh - h <= 1 and h - nh <= 6:
					# Headroom while stepping up: the block above our head must be free.
					if nh > h and not _passable(grid.get(x, int(nh / 2 + 1.79), z)):
						continue
					todo.append(n)
	return seen, set(standable)
