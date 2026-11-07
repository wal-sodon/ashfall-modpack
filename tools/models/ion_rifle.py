#!/usr/bin/env python3
"""Ion Rifle 3D item model: skin texture, block-style model JSON, flat-icon model and item definition.

Rare engineered rifle from the wreck: pale ceramic shell over gunmetal, a caged green (#5aff8c) ion
chamber at the receiver, long steel barrel with two ion coils and an emitter muzzle, short optic rail.

Convention (shared with tools/models/preview.py): the muzzle points north (-Z), up is +Y, the rifle is
centred on x = 8. Parts are written in "gun space" (L along the gun, 0 = butt .. 16 = muzzle; Y up;
W across, centred on 8) and converted to model space as x = W, y = Y, z = 16 - L.

The skin is generated: every face gets its own region in a 64x64 sheet at 2 texels per model unit and
is painted by sampling a material function at each texel's 3D position, so details (vents, ribs,
glow strips) line up across faces.

Usage: python3 -I tools/models/ion_rifle.py
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb, CLEAR  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "ion_rifle"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64
DENSITY = 2  # texels per model unit

# ---------------------------------------------------------------- palette (hue-shifted ramps)
SHELL = [rgb("eef0e8"), rgb("cdd2c9"), rgb("a3aaa4"), rgb("737b7a")]   # pale ceramic composite
METAL = [rgb("808a98"), rgb("596170"), rgb("3c4250"), rgb("262a35")]   # gunmetal (cool)
STEEL = [rgb("c9cfd6"), rgb("98a0ab"), rgb("6a7280"), rgb("464c58")]   # barrel steel
POLY = [rgb("5c6461"), rgb("414745"), rgb("2e3231"), rgb("1f2222")]    # grip polymer
ION = [rgb("f0fff4"), rgb("5aff8c"), rgb("2bc966"), rgb("15834a")]     # ion green

# ---------------------------------------------------------------- parts (gun space)
# name, (L0, Y0, W0), (L1, Y1, W1), material, optional rotation (axis in model space, angle, gun origin)
PARTS = [
	("butt", (0, 4.75, 7.05), (1, 9.25, 8.95), "poly"),
	("stock_top", (1, 8.25, 7.3), (4.25, 9.5, 8.7), "shell"),
	("stock_low", (1, 5, 7.3), (3.5, 6.1, 8.7), "shell"),
	("stock_web", (3.25, 5, 7.3), (4.5, 9.5, 8.7), "shell"),
	("receiver_low", (4.25, 7, 7), (9.75, 8.75, 9), "metal"),
	("receiver_up", (4.25, 8.75, 6.9), (9.75, 10, 9.1), "shell"),
	("chamber", (6.25, 7.4, 6.4), (8.75, 9.4, 9.6), "ion"),
	("cap_back", (5.75, 7.2, 6.2), (6.25, 9.6, 9.8), "metal"),
	("cap_front", (8.75, 7.2, 6.2), (9.25, 9.6, 9.8), "metal"),
	("grip", (4.75, 3.5, 7.35), (6, 7, 8.65), "poly", ("x", -22.5, (5.4, 7, 8))),
	("guard", (5.9, 5.5, 7.6), (8, 5.9, 8.4), "metal"),
	("trigger", (6.8, 5.9, 7.8), (7.2, 7, 8.2), "metal"),
	("cell", (8, 4.75, 7.3), (9.5, 7, 8.7), "cell"),
	("handguard", (9.75, 7.25, 7.1), (12, 9.75, 8.9), "shell"),
	("barrel", (9.75, 8.1, 7.6), (16, 8.9, 8.4), "steel"),
	("coil_a", (13.25, 7.8, 7.3), (13.75, 9.2, 8.7), "coil"),
	("coil_b", (14.1, 7.8, 7.3), (14.6, 9.2, 8.7), "coil"),
	("muzzle", (15.1, 7.6, 7.2), (16, 9.4, 8.8), "muzzle"),
	("rail", (5.25, 10, 7.5), (9, 10.4, 8.5), "metal"),
	("optic", (5.75, 10.4, 7.45), (7.75, 11.4, 8.55), "optic"),
	("optic_hood", (7.75, 10.3, 7.3), (8.5, 11.7, 8.7), "optic"),
]

FACES = ("north", "south", "east", "west", "up", "down")


def to_model(p):
	L, Y, W = p
	return (W, Y, 16 - L)


def box(part):
	a, b = to_model(part[1]), to_model(part[2])
	return tuple(min(a[i], b[i]) for i in range(3)), tuple(max(a[i], b[i]) for i in range(3))


def face_frame(name, f, t):
	"""(origin, u-axis, v-axis) of a face in model units, matching vanilla UV orientation."""
	x0, y0, z0 = f
	x1, y1, z1 = t
	return {
		"north": ((x1, y1, z0), (x0 - x1, 0, 0), (0, y0 - y1, 0)),
		"south": ((x0, y1, z1), (x1 - x0, 0, 0), (0, y0 - y1, 0)),
		"east": ((x1, y1, z1), (0, 0, z0 - z1), (0, y0 - y1, 0)),
		"west": ((x0, y1, z0), (0, 0, z1 - z0), (0, y0 - y1, 0)),
		"up": ((x0, y1, z0), (x1 - x0, 0, 0), (0, 0, z1 - z0)),
		"down": ((x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1)),
	}[name]


def length(v):
	return math.sqrt(sum(c * c for c in v))


# ---------------------------------------------------------------- materials
def ramp_edge(ramp, s, t, face, base=1):
	"""Base tone with a light top/left bevel and a dark bottom/right edge on side faces."""
	if face == "up":
		return ramp[max(0, base - 1)] if t < 0.34 else ramp[base]
	if face == "down":
		return ramp[min(3, base + 2)]
	if t < 0.12:
		return ramp[max(0, base - 1)]
	if t > 0.86:
		return ramp[min(3, base + 1)]
	return ramp[base]


def paint(part, face, p, s, t, fw, fh):
	"""Colour for the texel of `part`'s `face` whose centre is at model point p; (s, t) in 0..1 across the face."""
	name, mat = part[0], part[3]
	x, y, z = p
	L, W = 16 - z, x
	side = face in ("east", "west")
	edge_t = min(t, 1 - t) * fh  # distance to top/bottom edge in texels
	edge_s = min(s, 1 - s) * fw

	if mat == "shell":
		c = ramp_edge(SHELL, s, t, face)
		if name == "receiver_up":
			if side and abs(L - 5.2) < 0.25:
				c = SHELL[3]  # panel seam behind the chamber
			if face == "up" and 7.6 < W < 8.4 and L > 8.9:
				c = SHELL[2]
		if name == "handguard":
			if side and 7.55 < y < 7.95:
				c = ION[2] if t < 0.9 else ION[3]  # conduit strip along the handguard
			if side and 8.45 < y < 9.35 and int((L - 9.75) * 2) in (1, 3):
				c = METAL[3] if y < 9.1 else METAL[2]  # vent slots
			if face == "up" and abs(W - 8) < 0.25:
				c = SHELL[2]
		if name in ("stock_top", "stock_low", "stock_web") and side:
			if name == "stock_web" and 6.0 < y < 8.4 and abs(L - 3.85) < 0.3:
				c = SHELL[2]
		return c

	if mat == "metal":
		c = ramp_edge(METAL, s, t, face)
		if name in ("cap_back", "cap_front") and side and 7.95 < y < 8.65:
			c = METAL[2]  # bolt line across the cap
		if name == "rail" and face == "up" and int(L * 2) % 2 == 0:
			c = METAL[2]  # rail notches
		if name == "receiver_low" and side and 4.5 < L < 5.5 and 7.4 < y < 8.3:
			c = ION[3] if 7.65 < y < 8.05 else METAL[3]  # small status window
		return c

	if mat == "steel":
		if face == "up":
			return STEEL[0] if abs(W - 8) < 0.25 else STEEL[1]
		if face == "down":
			return STEEL[3]
		if 12.2 < L < 13 and int(L * 4) % 2 == 0:
			return STEEL[3]  # heat-sink grooves ahead of the handguard
		return STEEL[0] if t < 0.3 else STEEL[1] if t < 0.65 else STEEL[2]

	if mat == "poly":
		c = ramp_edge(POLY, s, t, face)
		if name == "grip" and side and int(t * fh) % 3 == 1 and 0.2 < s < 0.8:
			c = POLY[2]  # grip texture ridges
		if name == "butt" and face == "south" and int(t * fh) % 2 == 1:
			c = POLY[2]
		return c

	if mat == "ion":
		# caged ion chamber: metal ribs over a glowing core
		rib = int((L - 6.25) * 2) % 2 == 1 if face in ("east", "west", "up", "down") else False
		if face in ("north", "south"):
			return METAL[2]
		if face == "down":
			return METAL[3] if rib else ION[3]
		if rib:
			return METAL[1] if t < 0.5 else METAL[2]
		if face == "up":
			return ION[1] if abs(W - 8) < 0.8 else ION[2]
		if edge_t < 1:
			return ION[3] if t > 0.5 else ION[2]
		if abs(y - 8.4) < 0.3:
			return ION[0]
		return ION[1]

	if mat == "coil":
		if face in ("north", "south"):
			return ION[3]
		if face == "down":
			return ION[3]
		if face == "up":
			return ION[1]
		return ION[1] if t < 0.3 else ION[2] if t < 0.75 else ION[3]

	if mat == "muzzle":
		if face == "north":
			r = math.hypot(x - 8, y - 8.5)
			if r < 0.3:
				return ION[0]
			if r < 0.65:
				return ION[1]
			return METAL[2] if r > 0.85 else ION[3]
		c = ramp_edge(METAL, s, t, face)
		if side and abs(L - 15.5) < 0.2:
			c = METAL[3]  # vent groove round the emitter
		return c

	if mat == "cell":
		c = ramp_edge(METAL, s, t, face, base=2)
		if side and 8.4 < L < 9.1 and 5.2 < y < 6.6:
			c = ION[1] if abs(L - 8.75) < 0.2 else ION[2]  # charge window
		if face == "down":
			c = METAL[3]
		return c

	if mat == "optic":
		if name == "optic" and face in ("north", "south"):
			return METAL[2]
		if face == "north":
			return ION[0] if (s < 0.5 and t < 0.5) else ION[1] if edge_s >= 1 and edge_t >= 1 else METAL[2]
		if face == "south":
			return ION[3] if edge_s >= 1 and edge_t >= 1 else METAL[2]
		return ramp_edge(METAL, s, t, face)

	raise ValueError(mat)


# ---------------------------------------------------------------- skin packing + model
def build():
	skin = Sprite(SKIN, SKIN)
	rects = []
	for part in PARTS:
		f, t = box(part)
		for face in FACES:
			o, du, dv = face_frame(face, f, t)
			fw = max(1, math.ceil(length(du) * DENSITY - 1e-6))
			fh = max(1, math.ceil(length(dv) * DENSITY - 1e-6))
			rects.append((part, face, f, t, o, du, dv, fw, fh))
	# shelf packing, tallest first
	order = sorted(range(len(rects)), key=lambda i: (-rects[i][8], -rects[i][7]))
	pos, x, y, shelf = {}, 0, 0, 0
	for i in order:
		fw, fh = rects[i][7], rects[i][8]
		if x + fw > SKIN:
			x, y, shelf = 0, y + shelf, 0
		pos[i] = (x, y)
		x += fw
		shelf = max(shelf, fh)
	assert y + shelf <= SKIN, f"skin overflow: {y + shelf} > {SKIN}"

	elements = {}
	for i, (part, face, f, t, o, du, dv, fw, fh) in enumerate(rects):
		px, py = pos[i]
		for j in range(fh):
			for k in range(fw):
				s, tt = (k + 0.5) / fw, (j + 0.5) / fh
				p = tuple(o[a] + s * du[a] + tt * dv[a] for a in range(3))
				skin.set(px + k, py + j, paint(part, face, p, s, tt, fw, fh))
		uv = [round(v * 16 / SKIN, 4) for v in (px, py, px + fw, py + fh)]
		el = elements.setdefault(part[0], {"from": list(f), "to": list(t), "faces": {}})
		if len(part) > 4:
			axis, angle, org = part[4]
			el["rotation"] = {"angle": angle, "axis": axis, "origin": list(to_model(org))}
		el["faces"][face] = {"uv": uv, "texture": "#skin"}

	display = {
		"thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3.5, 2], "scale": [0.85, 0.85, 0.85]},
		"thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3.5, 2], "scale": [0.85, 0.85, 0.85]},
		"firstperson_righthand": {"rotation": [0, 5, 0], "translation": [2.5, 3, 2], "scale": [0.85, 0.85, 0.85]},
		"firstperson_lefthand": {"rotation": [0, -5, 0], "translation": [-2.5, 3, 2], "scale": [0.85, 0.85, 0.85]},
		"head": {"rotation": [0, 0, 0], "translation": [0, 6, -3], "scale": [0.8, 0.8, 0.8]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
		"gui": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
		"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
	}
	model = {
		"gui_light": "front",
		"textures": {"skin": f"ashfall:item/{ID}_skin", "particle": f"ashfall:item/{ID}_skin"},
		"elements": list(elements.values()),
		"display": display,
	}
	return skin, model


def main():
	skin, model = build()
	tex = ASSETS / "textures/item"
	models = ASSETS / "models/item"
	items = ASSETS / "items"
	for d in (tex, models, items):
		d.mkdir(parents=True, exist_ok=True)
	(tex / f"{ID}_skin.png").write_bytes(skin.png())
	(models / f"{ID}.json").write_text(json.dumps(model, indent="\t") + "\n")
	(models / f"{ID}_icon.json").write_text(json.dumps(
		{"parent": "minecraft:item/generated", "textures": {"layer0": f"ashfall:item/{ID}_icon"}}, indent="\t") + "\n")
	(items / f"{ID}.json").write_text(json.dumps({"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
				   "model": {"type": "minecraft:model", "model": f"ashfall:item/{ID}_icon"}}],
		"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{ID}"},
	}}, indent="\t") + "\n")
	print(f"wrote {ID}: {len(model['elements'])} elements")

	# shared finishing pass: set-wide size, grip placement and display transforms (overrides the above)
	gunkit.finish(ASSETS / "models/item" / f"{ID}.json", ID)

if __name__ == "__main__":
	main()
