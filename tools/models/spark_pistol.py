#!/usr/bin/env python3
"""Spark Pistol 3D item model: block-style elements + a generated 64x64 skin (stdlib only).

A cheap improvised holdout pistol cobbled from scrap: a small boxy sheet-metal housing with a riveted
top plate, an exposed red-orange emitter coil (copper windings round a glowing core) on a bent steel
strap, a salvaged battery cell zip-tied to the left side with a bare wire running to the coil, and a
stubby grip wrapped in duct tape. The barrel points to -Z (north) and the top is +Y, matching the other
Ashfall guns, so the display transforms below hold it like a pistol pointing forward.

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/spark_pistol_skin.png, models/item/spark_pistol.json,
  models/item/spark_pistol_icon.json, items/spark_pistol.json
Usage: python3 -I tools/models/spark_pistol.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "spark_pistol"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64          # skin size in pixels
PPU = 2            # skin pixels per model unit (every element dimension sits on the 0.5 grid)

# ---------------------------------------------------------------- palette (hue-shifted ramps)
SHEET = [rgb("b9bcc0"), rgb("8a8f98"), rgb("666a76"), rgb("454857"), rgb("2b2c39")]   # scrap sheet metal
STEEL = [rgb("dcd6c8"), rgb("aaa9a6"), rgb("7d7f86"), rgb("54565f"), rgb("34353f")]   # bare steel
RUST = [rgb("c08250"), rgb("94573a"), rgb("6a3a2c"), rgb("45251f")]                  # rust patches
CU = [rgb("ffd29a"), rgb("e39a52"), rgb("b4672f"), rgb("7a3b22")]                    # copper windings
EN = [rgb("fff0dc"), rgb("ffb38a"), rgb("ff5a3c"), rgb("d2352a"), rgb("7a1e22")]      # red-orange energy
TAPE = [rgb("c6c6be"), rgb("a3a39b"), rgb("80807a"), rgb("5f5f5b")]                  # duct tape
CELL = [rgb("7fb3a6"), rgb("4f8579"), rgb("33584f"), rgb("1f3631")]                  # salvaged battery wrap
TIE = rgb("26262c")                                                                   # black zip tie

FACE_TONE = {"up": 1, "north": 2, "south": 2, "east": 2, "west": 2, "down": 3}

# ---------------------------------------------------------------- elements
# (name, from, to, material, rotation or None, faces to skip, light emission)
GRIP_ROT = {"origin": [8, 7.5, 11.25], "axis": "x", "angle": -22.5}
ELEMENTS = [
	# boxy sheet-metal housing
	("body", (6.75, 8.5, 6), (9.25, 11.5, 12.5), "body", None, (), 0),
	("plate", (7, 11.5, 6.5), (9, 12, 11.5), "plate", None, ("down",), 0),
	("rear_sight", (7.25, 12, 11), (8.75, 12.5, 11.5), "dark", None, ("down",), 0),
	("front_sight", (7.75, 12, 6.5), (8.25, 12.5, 7), "dark", None, ("down",), 0),
	("rear_cap", (7, 9, 12.5), (9, 11, 13), "cap", None, ("north",), 0),
	# exposed emitter: mount plate, glowing core, copper windings, steel prong tip, strap underneath
	("mount", (6.5, 8.5, 5.5), (9.5, 11.5, 6), "mount", None, ("south",), 0),
	("core", (7.25, 9.25, 2.5), (8.75, 10.75, 5.5), "core", None, ("south",), 12),
	("ring_a", (6.75, 8.75, 4.5), (9.25, 11.25, 5), "ring", None, (), 0),
	("ring_b", (6.75, 8.75, 3.5), (9.25, 11.25, 4), "ring", None, (), 0),
	("ring_c", (6.75, 8.75, 2.5), (9.25, 11.25, 3), "ring", None, (), 0),
	("tip", (7, 9, 1.5), (9, 11, 2.5), "tip", None, ("south",), 0),
	("strap", (7.5, 8.25, 1.5), (8.5, 8.75, 5.5), "strap", None, ("south",), 0),
	# lower frame + trigger group
	("frame", (7, 7.5, 7), (9, 8.5, 12.5), "frame", None, ("up",), 0),
	("guard_front", (7.5, 6, 7), (8.5, 7.5, 7.5), "dark", None, ("up",), 0),
	("guard_bottom", (7.5, 5.5, 7), (8.5, 6, 10.5), "dark", None, (), 0),
	("trigger", (7.75, 6.5, 8.5), (8.25, 7.5, 9), "steel_s", None, ("up",), 0),
	# stubby grip, angled back, wrapped in duct tape
	("grip", (7, 3, 10), (9, 8.5, 12.5), "grip", GRIP_ROT, ("up",), 0),
	("tape_hi", (6.85, 5.5, 9.85), (9.15, 7.5, 12.65), "tape", GRIP_ROT, ("up", "down"), 0),
	("tape_lo", (6.85, 3.5, 9.85), (9.15, 4.5, 12.65), "tape2", GRIP_ROT, ("up", "down"), 0),
	("grip_base", (6.75, 2.5, 9.75), (9.25, 3, 12.75), "base", GRIP_ROT, (), 0),
	# salvaged cell zip-tied to the left (-X) side, bare wire to the coil
	("cell", (6, 9, 7.5), (6.75, 11, 11), "cell", None, ("east",), 0),
	("wire", (6.25, 10, 5.5), (6.75, 10.5, 7.5), "wire", None, ("east",), 6),
]

FACES = ("north", "south", "east", "west", "up", "down")


def face_dims(fr, to, face):
	"""(width, height) of a face in model units, in its uv orientation."""
	dx, dy, dz = (to[i] - fr[i] for i in range(3))
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]


def face_world(fr, to, face, fu, fv):
	"""Model-space point of a face at fractional uv (fu, fv) (same corner convention as preview.py)."""
	x0, y0, z0 = fr
	x1, y1, z1 = to
	p00, p10, p01 = {
		"north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
		"south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
		"east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
		"west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
		"up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
		"down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)),
	}[face]
	return tuple(p00[i] + fu * (p10[i] - p00[i]) + fv * (p01[i] - p00[i]) for i in range(3))


# ---------------------------------------------------------------- face painters
def edge_tone(ramp, t, i, j, w, h, face):
	"""Base tone t, with a worn highlight on the top edge and a darker bottom edge of side faces."""
	last = len(ramp) - 1
	if face in ("up", "down"):
		if i == 0 or j == 0:
			return ramp[max(t - 1, 0)]
		if i == w - 1 or j == h - 1:
			return ramp[min(t + 1, last)]
		return ramp[min(t, last)]
	if j == 0 and h > 1:
		return ramp[max(t - 1, 0)]
	if j == h - 1 and h > 2:
		return ramp[min(t + 1, last)]
	return ramp[min(t, last)]


def paint(mat, face, i, j, w, h, p):
	"""Colour of skin pixel (i, j) on a w x h face of material mat; p is its model-space centre."""
	x, y, z = p
	t = FACE_TONE[face]
	side = face in ("east", "west")
	if mat == "body":
		c = edge_tone(SHEET, t, i, j, w, h, face)
		if side:
			if j == 1 and i in (1, w - 2):
				return STEEL[0]                              # rivets at the top corners
			if j == h - 2 and i in (1, w - 2):
				return SHEET[1]                              # rivets at the bottom corners
			if j == h // 2 and 2 < i < w - 3:
				return SHEET[3]                              # folded sheet seam
			if j == h // 2 - 1 and 2 < i < w - 3:
				return SHEET[1]                              # seam catch-light
			if face == "east" and 9 < z < 10.5 and j == h - 2:
				return RUST[1]                               # rust streak under the seam
			if face == "west" and 11.5 < z < 12.5 and 9 < y < 10.5:
				return RUST[1] if (i + j) % 3 else RUST[2]   # rust patch behind the cell
		if face == "south" and 7.5 < x < 8.5 and 9.5 < y < 10.5:
			return EN[3]                                     # tiny charge lamp on the back
		return c
	if mat == "plate":
		c = edge_tone(STEEL, t, i, j, w, h, face)
		if face == "up":
			if i in (0, w - 1) and j in (1, h - 2):
				return STEEL[0]                              # corner rivets
			if 0 < i < w - 1 and 2 < j < h - 3 and j % 2 == 0:
				return STEEL[4]                              # punched vent slots
		return c
	if mat == "cap":
		c = edge_tone(SHEET, t + 1, i, j, w, h, face)
		if face == "south" and 1 <= i <= w - 2 and 1 <= j <= h - 2 and (i + j) % 2 == 0:
			return SHEET[2]                                  # crosshatched end grille
		return c
	if mat == "mount":
		c = edge_tone(STEEL, t + 1, i, j, w, h, face)
		if face == "north":
			if i in (0, w - 1) and j in (0, h - 1):
				return STEEL[1]                              # bolt heads in the corners
			if 1 < i < w - 2 and 1 < j < h - 2:
				return STEEL[4]                              # scorched centre
		return c
	if mat == "core":
		if face == "north":
			return EN[0]
		if j == 0:
			return EN[0]
		if j == 1:
			return EN[1]
		return EN[2] if j < h - 1 else EN[3]
	if mat == "ring":
		if face in ("north", "south"):
			if 0 < i < w - 1 and 0 < j < h - 1:
				return CU[3]                                 # inner turns (mostly hidden by the core)
			return CU[1] if j == 0 or i == 0 else CU[2]
		# windings run round the coil: alternating bright / dark turns along the barrel axis
		hi = ((i + j) % 2 == 0)
		if face == "up":
			return CU[0] if j % 2 == 0 else CU[1]
		if face == "down":
			return CU[3] if j % 2 == 0 else CU[2]
		if j == 0:
			return CU[0] if hi else CU[1]
		if j == h - 1:
			return CU[3]
		if j >= h - 2:
			return CU[2] if hi else CU[3]
		return CU[1] if hi else CU[2]
	if mat == "tip":
		c = edge_tone(STEEL, t, i, j, w, h, face)
		if face == "north":
			cx, cy = abs(x - 8), abs(y - 10)
			if cx < 0.3 and cy < 0.3:
				return EN[0]
			if cx < 0.55 and cy < 0.55:
				return EN[1]
			if cx < 0.8 and cy < 0.8:
				return EN[3]
			return STEEL[3] if (i in (0, w - 1) or j in (0, h - 1)) else STEEL[4]
		if side and j == h // 2:
			return STEEL[4]                                  # notch
		return c
	if mat == "strap":
		c = edge_tone(STEEL, t + 1, i, j, w, h, face)
		if face == "down" and (i + j) % 3 == 0:
			return RUST[2]
		if side and int(z * 2) % 3 == 0:
			return RUST[1]                                   # rusty bent strap
		return c
	if mat == "dark":
		return edge_tone(SHEET, min(t + 1, 4), i, j, w, h, face)
	if mat == "steel_s":
		return STEEL[2] if face != "down" else STEEL[3]
	if mat == "frame":
		c = edge_tone(SHEET, t + 1, i, j, w, h, face)
		if side and j == 0 and i % 3 == 1:
			return STEEL[1]                                  # screw heads
		if side and 11 < z < 12.5 and j == 1:
			return RUST[2]
		return c
	if mat == "grip":
		# bare grip showing between tape wraps: rough cut scrap plate
		c = edge_tone(SHEET, t + 1, i, j, w, h, face)
		if face == "down":
			return SHEET[4]
		if (i * 5 + j * 3) % 11 == 0:
			return RUST[2]
		return c
	if mat in ("tape", "tape2"):
		base = 1 if mat == "tape" else 2
		c = TAPE[base] if face != "north" else TAPE[min(base + 1, 3)]
		if j == 0:
			c = TAPE[base - 1]                               # wrap edge catch-light
		elif j == h - 1:
			c = TAPE[min(base + 1, 3)]
		elif (i * 3 + j * 5) % 7 == 0:
			c = TAPE[min(base + 1, 3)]                       # wrinkles
		if mat == "tape" and side and i == w - 1 and j == h - 1:
			c = TAPE[3]                                      # frayed end
		return c
	if mat == "base":
		c = edge_tone(STEEL, t + 1, i, j, w, h, face)
		if face == "down" and w // 2 - 1 <= i <= w // 2 and h // 2 - 1 <= j <= h // 2:
			return EN[3]                                     # charge contact
		return c
	if mat == "cell":
		if face in ("west", "up", "down"):
			if 9.75 < z < 10.25:
				return TIE                                   # zip tie around the cell
			if face == "west":
				if j == 0:
					return CELL[0]
				if j == h - 1:
					return CELL[3]
				if 8 < z < 9 and j == h // 2:
					return EN[2]                             # red "+" stripe on the label
				return CELL[1] if j < h // 2 else CELL[2]
			return CELL[1] if face == "up" else CELL[3]
		# ends: steel caps
		if face == "north":
			return STEEL[1] if (i, j) == (w // 2, h // 2) else STEEL[3]
		return STEEL[3]
	if mat == "wire":
		if face == "north":
			return EN[1]
		return EN[2] if (int(z * 2) % 2 == 0) else EN[3]
	return (255, 0, 255, 255)


# ---------------------------------------------------------------- skin packing + model
def build():
	skin = Sprite(SKIN, SKIN)
	faces = []          # (name, face, wpx, hpx)
	for name, fr, to, mat, r, skip, _ in ELEMENTS:
		for f in FACES:
			if f in skip:
				continue
			w, h = face_dims(fr, to, f)
			faces.append((name, f, round(w * PPU), round(h * PPU)))
	# shelf packer, tallest first, 1px gutter so mip/filter bleed never touches a neighbour
	order = sorted(faces, key=lambda q: (-q[3], -q[2]))
	place, x, y, shelf = {}, 0, 0, 0
	for name, f, w, h in order:
		if x + w > SKIN:
			x, y, shelf = 0, y + shelf + 1, 0
		if y + h > SKIN:
			raise SystemExit("skin overflow; enlarge SKIN or reduce PPU")
		place[(name, f)] = (x, y, w, h)
		x += w + 1
		shelf = max(shelf, h)
	elements = []
	for name, fr, to, mat, r, skip, glow in ELEMENTS:
		el = {"name": name, "from": list(fr), "to": list(to)}
		if r:
			el["rotation"] = dict(r)
		if glow:
			el["light_emission"] = glow
		el["faces"] = {}
		for f in FACES:
			if f in skip:
				continue
			px, py, w, h = place[(name, f)]
			for j in range(h):
				for i in range(w):
					p = face_world(fr, to, f, (i + 0.5) / w, (j + 0.5) / h)
					skin.set(px + i, py + j, paint(mat, f, i, j, w, h, p))
			k = 16 / SKIN
			el["faces"][f] = {"uv": [px * k, py * k, (px + w) * k, (py + h) * k], "texture": "#skin"}
		elements.append(el)
	return skin, elements


def mirror(d):
	return {"rotation": [d["rotation"][0], -d["rotation"][1], -d["rotation"][2]],
			"translation": list(d["translation"]), "scale": list(d["scale"])}


def display():
	# Same pose as the other Ashfall pistols; the spark pistol is simply a smaller gun in the hand.
	tp = {"rotation": [70, 0, 0], "translation": [0, 1.5, 2.5], "scale": [0.75, 0.75, 0.75]}
	fp = {"rotation": [0, -4, 0], "translation": [1, 1.5, -2.5], "scale": [0.85, 0.85, 0.85]}
	return {
		"thirdperson_righthand": tp,
		"thirdperson_lefthand": mirror(tp),
		"firstperson_righthand": fp,
		"firstperson_lefthand": mirror(fp),
		"head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
		"gui": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
		"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
	}


def write_json(path, obj):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(obj, indent="\t") + "\n")
	print(f"wrote {path}")


def main():
	skin, elements = build()
	tex = ASSETS / "textures/item" / f"{ID}_skin.png"
	tex.parent.mkdir(parents=True, exist_ok=True)
	tex.write_bytes(skin.png())
	print(f"wrote {tex}")
	write_json(ASSETS / "models/item" / f"{ID}.json", {
		"gui_light": "front",
		"textures": {"skin": f"ashfall:item/{ID}_skin", "particle": f"ashfall:item/{ID}_icon"},
		"elements": elements,
		"display": display(),
	})
	write_json(ASSETS / "models/item" / f"{ID}_icon.json", {
		"parent": "minecraft:item/generated",
		"textures": {"layer0": f"ashfall:item/{ID}_icon"},
	})
	write_json(ASSETS / "items" / f"{ID}.json", {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [{
				"when": ["gui", "ground", "fixed", "on_shelf"],
				"model": {"type": "minecraft:model", "model": f"ashfall:item/{ID}_icon"},
			}],
			"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{ID}"},
		}
	})

	# shared finishing pass: set-wide size, grip placement and display transforms (overrides the above)
	gunkit.finish(ASSETS / "models/item" / f"{ID}.json", ID)

if __name__ == "__main__":
	main()
