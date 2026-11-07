#!/usr/bin/env python3
"""Storm Repeater 3D item model: skin PNG + block-style model JSON + icon model + items/ definition.

A very rare heavy automatic from the crashed vessel: bulky gunmetal receiver, drum-like cell housing
under the front, a rotating cluster of four emitter barrels ringed by red (#ff2d2d) coils, a top
carry handle and red power conduits. The gun points north (-Z): muzzle at z=0, stock at z=16.

Faces are painted per pixel from their world position, so stripes and rings line up across
elements. Each face is packed into a 64x64 skin at 2 texels per model unit.

Usage: python3 -I tools/models/storm_repeater.py
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "storm_repeater"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64
DENSITY = 2  # texels per model unit

# Hue-shifted ramps: [highlight, light, base, shadow, deep]; cool shadows, warmer highlights.
GUNMETAL = [rgb(h) for h in ("b4bcc2", "7e8894", "566070", "3a4150", "252a35")]
DARKMETAL = [rgb(h) for h in ("8c949e", "5f6774", "434a57", "2e333f", "1d2029")]
COMPOSITE = [rgb(h) for h in ("8a8172", "665f55", "4c4741", "35322e", "221f1d")]
STEEL = [rgb(h) for h in ("e4e7ea", "b5bbc2", "8a919c", "5f6572", "3d414c")]
RED = [rgb(h) for h in ("fff1ea", "ff8a78", "ff2d2d", "b3161f", "640c16")]

# ---------------------------------------------------------------- geometry helpers
FACES = ("north", "south", "east", "west", "up", "down")


def face_quad(fr, to, face):
	"""(p00, p10, p01): world points at uv (u0,v0), (u1,v0), (u0,v1), matching vanilla auto-UV."""
	x0, y0, z0 = fr
	x1, y1, z1 = to
	return {
		"north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)),
		"south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
		"east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)),
		"west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
		"up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)),
		"down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0)),
	}[face]


def face_dims(fr, to, face):
	dx, dy, dz = (to[i] - fr[i] for i in range(3))
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]


ELEMENTS = []


def box(name, fr, to, ramp, paint=None, faces=FACES, rot=None):
	ELEMENTS.append(dict(name=name, fr=fr, to=to, ramp=ramp, paint=paint, faces=faces, rot=rot))


# ---------------------------------------------------------------- painting
def bevel(ramp, face, i, j, w, h):
	"""Base material colour: top-left light, bottom-right shadow, face-direction tone."""
	if face == "up":
		c = 1
		if i == 0 or j == 0:
			c = 0
		elif i == w - 1 or j == h - 1:
			c = 2
	elif face == "down":
		c = 3 if (j > 0 and i > 0) else 2
	else:
		c = 2
		if j == 0:
			c = 1
		elif j == h - 1:
			c = 3
		elif face in ("west", "south") and i == 0 and h > 2:
			c = 1
		elif face in ("east", "north") and i == w - 1 and h > 2:
			c = 3
	return ramp[c]


def side(face):
	return face in ("east", "west")


DRUM_C = (3.5, 7.75)  # (y, z) centre of the drum cell housing
STRIPE_Y = (6.25, 6.75)


def paint_receiver(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if side(face):
		if 5.75 <= y <= 7.75 and 11.5 <= z <= 12.75:  # cell charge window: three lit segments
			if y < 6.0 or y > 7.5 or z < 11.75 or z > 12.5:
				return ramp[4]
			return RED[1] if y > 7.0 else RED[2] if y > 6.5 else RED[3]
		if 5.75 <= y <= 6.25 and 6.5 <= z <= 11.0:  # lower panel seam
			return ramp[3]
		if 6.75 <= y <= 7.75 and 7.0 <= z <= 10.5:  # cooling vent slots
			return ramp[4] if int((z - 7.0) * 2) % 2 == 0 else ramp[1]
		if 10.75 <= z <= 11.25 and y > 7:  # panel seam
			return ramp[4]
		if 11.75 <= z <= 12.25 and 8.5 <= y <= 9.0:  # rivet
			return ramp[0]
	if face == "up" and 7.25 <= x <= 8.75:
		return ramp[2] if int(z * 2) % 2 else ramp[3]  # accessory rail teeth
	return base


def paint_stock(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if face == "south":
		return ramp[4] if j % 2 else ramp[3]  # rubber butt pad ribs
	if side(face) and 14.0 <= z <= 15.0 and 6.0 <= y <= 8.0:
		return ramp[4]  # lightening cut-out
	if side(face) and 13.0 <= z <= 13.5:
		return ramp[4]
	return base


def paint_grip(face, p, base, ramp, i, j, w, h):
	if face in ("north", "south", "east", "west") and 0 < j < h - 1:
		return ramp[3] if j % 2 == 0 else ramp[1] if face in ("north", "west") else ramp[2]
	return base


def paint_drum(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if side(face):
		d = math.hypot(y - DRUM_C[0], z - DRUM_C[1])
		if 1.4 <= d <= 1.9:
			ang = math.atan2(y - DRUM_C[0], z - DRUM_C[1])
			return RED[1] if ang > 1.2 and ang < 2.6 else RED[2] if ang > -1.5 else RED[3]
		if 1.9 < d <= 2.2:
			return ramp[4]
		if d < 1.4:
			return ramp[1] if y > DRUM_C[0] else ramp[2]
	else:
		if side_strip(x):
			return ramp[4]  # seams where the drum's end caps meet the body
		return ramp[1] if face == "up" else base
	return base


def side_strip(x):
	return 5.5 <= x <= 6.0 or 10.0 <= x <= 10.5


def paint_hub(face, p, base, ramp, i, j, w, h):
	if side(face):
		return RED[0] if (i, j) in ((w // 2, h // 2), (w // 2 - 1, h // 2 - 1), (w // 2, h // 2 - 1), (w // 2 - 1, h // 2)) else (ramp[1] if j == 0 or i == 0 else ramp[3])
	return base


def paint_shroud(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if side(face) and 7.0 <= y <= 9.0 and 0 < i < w - 1:
		return ramp[4] if j % 2 == 0 else ramp[1]
	if face == "up" and 0 < i < w - 1 and 0 < j < h - 1:
		return ramp[2]
	return base


def paint_coil(face, p, base, ramp, i, j, w, h):
	if face in ("north", "south"):
		return RED[3]
	# Wound-coil look: alternating bright/deep windings across the ring band.
	if face in ("up", "down"):
		return RED[1] if i % 2 == 0 else RED[3] if face == "down" else RED[2]
	return RED[1] if j % 2 == 0 else RED[3]


def paint_barrel(face, p, base, ramp, i, j, w, h):
	if face == "north":
		return RED[0] if 0 < i < w - 1 and 0 < j < h - 1 else RED[3] if w <= 2 else ramp[4]
	x, y, z = p
	if 1.25 <= z <= 1.75:
		return ramp[4]
	return base


def paint_spindle(face, p, base, ramp, i, j, w, h):
	if face == "north":
		return RED[1]
	return base


def paint_handle(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if face == "up" and 8.0 <= z <= 11.0:
		return COMPOSITE[2] if j % 2 else COMPOSITE[3]  # rubber grip strip
	return base


def paint_fin(face, p, base, ramp, i, j, w, h):
	return ramp[0] if face == "up" else base


def paint_conduit(face, p, base, ramp, i, j, w, h):
	x, y, z = p
	if int(z * 2) % 4 == 0:
		return GUNMETAL[1] if face == "up" or j == 0 else GUNMETAL[3]  # clamps
	if face == "down":
		return RED[3]
	return RED[1] if j == 0 or face == "up" else RED[2]


def paint_sight(face, p, base, ramp, i, j, w, h):
	if face == "north" or face == "south":
		return RED[1]
	return base


# ---------------------------------------------------------------- the gun
def build():
	ELEMENTS.clear()
	# Receiver and stock.
	box("receiver", (5.5, 5.0, 6.0), (10.5, 10.0, 13.0), GUNMETAL, paint_receiver)
	box("stock", (6.0, 4.5, 13.0), (10.0, 9.5, 16.0), COMPOSITE, paint_stock, faces=("south", "east", "west", "up", "down"))
	box("stock_cheek", (6.5, 9.5, 13.0), (9.5, 10.0, 15.5), COMPOSITE, faces=("south", "east", "west", "up"))
	# Pistol grip, raked back, plus trigger guard and trigger.
	box("grip", (7.0, 1.0, 11.0), (9.0, 5.25, 13.0), COMPOSITE, paint_grip, faces=("north", "south", "east", "west", "down"),
		rot=dict(origin=(8, 5, 12), axis="x", angle=-22.5))
	box("guard", (7.5, 3.25, 9.5), (8.5, 3.75, 11.25), DARKMETAL, faces=("east", "west", "up", "down", "north"))
	box("guard_post", (7.5, 3.75, 9.5), (8.5, 5.0, 10.0), DARKMETAL, faces=("north", "east", "west", "south"))
	box("trigger", (7.75, 4.0, 10.5), (8.25, 5.0, 11.0), RED, faces=("north", "east", "west", "south"))
	# Drum-like cell housing (an octagon from two crossed boxes) with a hub cap through it.
	yc, zc = DRUM_C
	box("drum_a", (5.0, yc - 2.25, zc - 1.6), (11.0, yc + 2.25, zc + 1.6), DARKMETAL, paint_drum)
	box("drum_b", (5.05, yc - 1.6, zc - 2.25), (10.95, yc + 1.6, zc + 2.25), DARKMETAL, paint_drum)
	box("drum_hub", (4.75, yc - 0.75, zc - 0.75), (11.25, yc + 0.75, zc + 0.75), STEEL, paint_hub,
		faces=("east", "west", "up", "down", "north", "south"))
	# Front shroud with two red coil rings.
	box("shroud", (6.0, 6.0, 3.5), (10.0, 10.0, 6.0), DARKMETAL, paint_shroud, faces=("north", "east", "west", "up", "down"))
	box("coil_front", (5.6, 5.6, 3.75), (10.4, 10.4, 4.5), RED, paint_coil)
	box("coil_rear", (5.6, 5.6, 5.0), (10.4, 10.4, 5.75), RED, paint_coil)
	# Rotating cluster of four emitter barrels around a spindle, turned 22.5 degrees.
	spin = dict(origin=(8, 8, 2), axis="z", angle=22.5)
	for k, (bx, by) in enumerate(((6.35, 6.35), (8.4, 6.35), (6.35, 8.4), (8.4, 8.4))):
		box(f"barrel_{k}", (bx, by, 0.0), (bx + 1.25, by + 1.25, 3.5), STEEL, paint_barrel,
			faces=("north", "east", "west", "up", "down"), rot=spin)
	box("spindle", (7.5, 7.5, 0.5), (8.5, 8.5, 3.5), DARKMETAL, paint_spindle, faces=("north", "east", "west", "up", "down"), rot=spin)
	box("clamp", (6.0, 6.0, 0.75), (10.0, 10.0, 1.5), GUNMETAL, faces=("north", "south", "east", "west", "up", "down"), rot=spin)
	# Carry handle with a red front sight.
	box("handle_post_f", (7.4, 10.0, 6.75), (8.6, 11.5, 7.75), GUNMETAL, faces=("north", "south", "east", "west"))
	box("handle_post_r", (7.4, 10.0, 11.5), (8.6, 11.5, 12.5), GUNMETAL, faces=("north", "south", "east", "west"))
	box("handle_bar", (7.25, 11.5, 6.5), (8.75, 12.5, 13.0), GUNMETAL, paint_handle)
	box("sight", (7.75, 10.0, 3.75), (8.25, 11.25, 4.25), RED, paint_sight, faces=("north", "south", "east", "west", "up"))
	# Heat-sink fins on the shroud.
	for k, z in enumerate((4.6, 5.2)):
		box(f"fin_{k}", (6.5, 10.0, z), (9.5, 10.6, z + 0.35), STEEL, paint_fin, faces=("north", "south", "east", "west", "up"))
	# Red power conduits from the drum's cells forward to the coils, one each side.
	box("conduit_w", (5.1, 8.25, 4.0), (5.6, 8.95, 9.75), RED, paint_conduit, faces=("north", "west", "up", "down"))
	box("conduit_e", (10.4, 8.25, 4.0), (10.9, 8.95, 9.75), RED, paint_conduit, faces=("north", "east", "up", "down"))


# ---------------------------------------------------------------- packing + output
def pack():
	"""Shelf-pack every face into the skin; returns {(element index, face): (px, py, w, h)}."""
	items = []
	for n, el in enumerate(ELEMENTS):
		for f in el["faces"]:
			if f == "west" and "east" in el["faces"]:
				continue  # mirrored: west reuses east's texels with u flipped
			du, dv = face_dims(el["fr"], el["to"], f)
			items.append(((n, f), max(1, round(du * DENSITY)), max(1, round(dv * DENSITY))))
	items.sort(key=lambda it: (-it[2], -it[1]))
	out, x, y, shelf = {}, 0, 0, 0
	for key, w, h in items:
		if x + w > SKIN:
			x, y, shelf = 0, y + shelf, 0
		if y + h > SKIN:
			raise SystemExit("skin overflow: shrink DENSITY or elements")
		out[key] = (x, y, w, h)
		x, shelf = x + w, max(shelf, h)
	return out, y + shelf


def main():
	build()
	layout, used = pack()
	skin = Sprite(SKIN, SKIN)
	elements = []
	for n, el in enumerate(ELEMENTS):
		faces = {}
		for f in el["faces"]:
			k = 16 / SKIN
			if f == "west" and "east" in el["faces"]:
				px, py, w, h = layout[(n, "east")]
				faces[f] = {"uv": [round((px + w) * k, 4), round(py * k, 4), round(px * k, 4), round((py + h) * k, 4)],
							"texture": "#skin"}
				continue
			px, py, w, h = layout[(n, f)]
			p00, p10, p01 = face_quad(el["fr"], el["to"], f)
			for j in range(h):
				for i in range(w):
					s, t = (i + 0.5) / w, (j + 0.5) / h
					p = tuple(p00[k] + s * (p10[k] - p00[k]) + t * (p01[k] - p00[k]) for k in range(3))
					c = bevel(el["ramp"], f, i, j, w, h)
					if el["paint"]:
						c = el["paint"](f, p, c, el["ramp"], i, j, w, h)
					skin.set(px + i, py + j, c)
			faces[f] = {"uv": [round(px * k, 4), round(py * k, 4), round((px + w) * k, 4), round((py + h) * k, 4)],
						"texture": "#skin"}
		e = {"from": list(el["fr"]), "to": list(el["to"])}
		if el["rot"]:
			r = el["rot"]
			e["rotation"] = {"origin": list(r["origin"]), "axis": r["axis"], "angle": r["angle"]}
		e["faces"] = faces
		elements.append(e)

	model = {
		"textures": {"skin": f"ashfall:item/{ID}_skin", "particle": f"ashfall:item/{ID}_skin"},
		"elements": elements,
		"gui_light": "front",
		"display": {
			# Hand frame (third person): +Y is forward out of the fist, +Z is up. Rotating 90 on X turns
			# the muzzle (-Z) forward; the translation puts the pistol grip in the fist.
			"thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, 3.0, 3.25], "scale": [0.75, 0.75, 0.75]},
			"thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, 3.0, 3.25], "scale": [0.75, 0.75, 0.75]},
			# First person: view space already looks down -Z, so the muzzle points at the crosshair.
			"firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 4.0, 0.5], "scale": [0.68, 0.68, 0.68]},
			"firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 4.0, 0.5], "scale": [0.68, 0.68, 0.68]},
			"head": {"rotation": [0, 180, 0], "translation": [0, 10, -4], "scale": [0.8, 0.8, 0.8]},
			"ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
			"gui": {"rotation": [20, -135, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
			"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
		},
	}
	icon_model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"ashfall:item/{ID}_icon"}}
	item_def = {"model": {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
				   "model": {"type": "minecraft:model", "model": f"ashfall:item/{ID}_icon"}}],
		"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{ID}"},
	}}

	writes = {
		ASSETS / f"textures/item/{ID}_skin.png": skin.png(),
		ASSETS / f"models/item/{ID}.json": json.dumps(model, indent="\t") + "\n",
		ASSETS / f"models/item/{ID}_icon.json": json.dumps(icon_model, indent="\t") + "\n",
		ASSETS / f"items/{ID}.json": json.dumps(item_def, indent="\t") + "\n",
	}
	for path, data in writes.items():
		path.parent.mkdir(parents=True, exist_ok=True)
		(path.write_bytes if isinstance(data, bytes) else path.write_text)(data)
		print(f"wrote {path.relative_to(ROOT)}")
	print(f"{len(elements)} elements, skin rows used: {used}/{SKIN}")

	# shared finishing pass: set-wide size, grip placement and display transforms (overrides the above)
	gunkit.finish(ASSETS / "models/item" / f"{ID}.json", ID)

if __name__ == "__main__":
	main()
