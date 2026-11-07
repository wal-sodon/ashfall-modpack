#!/usr/bin/env python3
"""Pulse Carbine 3D item model: skin texture, block-style model JSON, flat-icon model and item definition.

Compact automatic carbine built from police weapon parts: navy polymer lower and grip, gunmetal upper
with a cyan (#3cc8ff) cell window, a vented barrel shroud glowing cyan inside, a police tac light under
the shroud, a cyan pulse emitter at the muzzle and a skeletal folding stock with its hinge block.

Convention (shared with tools/models/preview.py and the other gun models): the muzzle points north
(-Z), up is +Y, the gun is centred on x = 8. Parts are written in "gun space" (L along the gun,
0 = butt .. 16 = muzzle; Y up; W across, centred on 8) and converted to model space as
x = W, y = Y, z = 16 - L.

Display: in first person the camera looks down -Z, so the muzzle already points forward. In third
person the hand layer maps the item's +Y to the holder's forward, so the model is pitched +90 about X
(muzzle -> up -> forward, top -> south -> world up) and moved so the grip sits in the fist. Left-hand
entries mirror the right ones the vanilla way (Y/Z rotation and X translation negated).

The skin is generated: every face gets its own region in a 64x64 sheet at 2 texels per model unit and
is painted by sampling a material function at each texel's 3D position, so details line up across faces.

Usage: python3 -I tools/models/pulse_carbine.py
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

ID = "pulse_carbine"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64
DENSITY = 2  # texels per model unit

# ---------------------------------------------------------------- palette (hue-shifted ramps)
METAL = [rgb("8a929e"), rgb("5f6674"), rgb("404654"), rgb("282c38")]   # gunmetal upper (cool)
STEEL = [rgb("d2d6da"), rgb("a2a8b0"), rgb("70778a"), rgb("4a4f60")]   # emitter/barrel steel
POLY = [rgb("56638a"), rgb("3a4566"), rgb("283049"), rgb("1a1f31")]    # police navy polymer
CYAN = [rgb("eafcff"), rgb("3cc8ff"), rgb("1c8ed6"), rgb("10558e")]    # pulse cyan
LENS = rgb("fff6d8")                                                  # tac light lens
WORN = rgb("a7a39a")                                                  # scuffed bare-metal edge

# ---------------------------------------------------------------- parts (gun space)
# name, (L0, Y0, W0), (L1, Y1, W1), material, optional rotation (model axis, angle, gun-space origin)
PARTS = [
	("butt", (1, 5.5, 7.25), (1.75, 9.75, 8.75), "poly"),
	("stock_top", (1.75, 8.75, 7.5), (5.25, 9.5, 8.5), "metal"),
	("stock_low", (1.75, 5.9, 7.6), (5.4, 6.5, 8.4), "metal", ("x", 22.5, (1.75, 6.2, 8))),
	("hinge", (5, 6.75, 7), (6, 9.75, 9), "hinge"),
	("lower", (6, 7, 6.9), (10.5, 8.75, 9.1), "poly"),
	("upper", (6, 8.75, 6.75), (11, 10.25, 9.25), "upper"),
	("charger", (9.25, 9.25, 9.25), (10.25, 9.75, 9.75), "metal"),
	("grip", (6.5, 3.4, 7.25), (8, 7, 8.75), "poly", ("x", -22.5, (7.25, 7, 8))),
	("guard", (7.9, 5.6, 7.6), (9, 6, 8.4), "metal"),
	("trigger", (8.3, 6, 7.8), (8.65, 7, 8.2), "metal"),
	("cell", (9, 4.6, 7.3), (10.4, 7, 8.7), "cell"),
	("shroud", (10.5, 7.5, 7), (14, 10, 9), "shroud"),
	("light", (11, 6.6, 7.35), (13, 7.5, 8.65), "light"),
	("barrel", (14, 8.1, 7.35), (14.75, 9.4, 8.65), "steel"),
	("muzzle", (14.6, 7.85, 7.1), (15.4, 9.65, 8.9), "muzzle"),
	("rail", (6.5, 10.25, 7.5), (10.75, 10.6, 8.5), "metal"),
	("rear_sight", (6.5, 10.6, 7.35), (7.25, 11.35, 8.65), "metal"),
	("front_sight", (13.2, 10, 7.75), (13.7, 10.9, 8.25), "metal"),
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
def bevel(ramp, t, face, fh, base=1):
	"""Base tone with a light top bevel and a dark bottom edge on side faces (light from top-left)."""
	if face == "up":
		return ramp[max(0, base - 1)]
	if face == "down":
		return ramp[min(3, base + 2)]
	row = int(t * fh)
	if row == 0:
		return ramp[max(0, base - 1)]
	if row == fh - 1 and fh > 2:
		return ramp[min(3, base + 1)]
	return ramp[base]


def paint(part, face, p, s, t, fw, fh):
	"""Colour of the texel of `part`'s `face` centred at model point p; (s, t) in 0..1 across the face."""
	name, mat = part[0], part[3]
	x, y, z = p
	L, W = 16 - z, x
	side = face in ("east", "west")
	ends = face in ("north", "south")
	col = int(s * fw)

	if mat == "poly":
		c = bevel(POLY, t, face, fh)
		if name == "grip" and side and int(t * fh) % 2 == 1 and 0 < col < fw - 1:
			c = POLY[2]  # stippled grip panel
		if name == "butt" and face == "south":
			c = POLY[2] if int(t * fh) % 2 else POLY[1]  # rubber pad ribs
		if name == "lower" and side:
			if abs(L - 9.0) < 0.25 and y < 8.5:
				c = POLY[3]  # magwell seam
			if 6.5 < L < 8.0 and 7.75 < y < 8.25:
				c = rgb("c9cfdc")  # white police stencil band
		return c

	if mat == "upper":
		c = bevel(METAL, t, face, fh)
		if side and 7.2 < L < 9.6 and 9.1 < y < 9.9:
			# cyan cell window: bright core line, deeper rim
			if abs(L - 7.2) < 0.3 or abs(L - 9.6) < 0.3:
				c = METAL[3]
			elif y > 9.6:
				c = CYAN[0] if 7.8 < L < 8.6 else CYAN[1]
			else:
				c = CYAN[2]
		if side and abs(L - 10.0) < 0.25:
			c = METAL[3]  # panel seam
		if face == "up" and int(t * fh) == 0:
			c = WORN  # scuffed front lip
		if face == "south":
			c = METAL[2]
		return c

	if mat == "metal":
		c = bevel(METAL, t, face, fh)
		if name == "rail" and face == "up":
			c = METAL[2] if int(L * 2) % 2 == 0 else METAL[0]  # rail notches
		if name == "rail" and side:
			c = METAL[2]
		if name == "rear_sight" and face == "north" and 0 < col < fw - 1 and int(t * fh) < 2:
			c = METAL[3]  # notch
		if name == "front_sight" and face == "up":
			c = CYAN[1]  # glowing sight dot
		if name == "stock_top" and face == "up" and int(L * 2) % 3 == 0:
			c = WORN
		return c

	if mat == "hinge":
		c = bevel(METAL, t, face, fh, base=2)
		if side and math.hypot(L - 5.5, y - 7.4) < 0.45:
			c = WORN  # hinge pin
		if side and abs(y - 9.0) < 0.25:
			c = METAL[3]
		return c

	if mat == "cell":
		c = bevel(POLY, t, face, fh, base=2)
		if side and 9.4 < L < 10.0 and 5.1 < y < 6.6:
			c = CYAN[1] if abs(L - 9.7) < 0.25 and y > 5.6 else CYAN[2]  # charge window
		if face == "down":
			c = METAL[2]  # metal base plate
		if side and int(t * fh) == fh - 1:
			c = METAL[2]
		return c

	if mat == "shroud":
		c = bevel(METAL, t, face, fh)
		# vent slots on the sides and top; cyan glow seen through them
		slot = any(abs(L - v) < 0.2 for v in (11.25, 12.25, 13.25))
		if side and slot and 8.0 < y < 9.6:
			c = CYAN[1] if 8.5 < y < 9.1 else CYAN[3]
		elif side and slot and 7.9 < y < 9.7:
			c = METAL[3]
		if face == "up" and slot and 7.4 < W < 8.6:
			c = CYAN[2]
		if side and int(t * fh) == fh - 1:
			c = METAL[3]
		if face == "north":
			c = METAL[3] if abs(x - 8) < 0.7 and abs(y - 8.75) < 0.7 else METAL[2]
		return c

	if mat == "light":
		c = bevel(POLY, t, face, fh, base=2)
		if face == "north":
			c = LENS if 0 < col < fw - 1 else POLY[3]
		if side and abs(L - 11.5) < 0.3:
			c = POLY[3]  # mount clamp seam
		return c

	if mat == "steel":
		if face == "up":
			return STEEL[0] if abs(W - 8) < 0.3 else STEEL[1]
		if face == "down":
			return STEEL[3]
		return STEEL[1] if t < 0.5 else STEEL[2]

	if mat == "muzzle":
		if face == "north":
			r = math.hypot(x - 8, y - 8.75)
			if r < 0.35:
				return CYAN[0]
			if r < 0.75:
				return CYAN[1]
			return CYAN[3] if r < 0.95 else METAL[2]
		c = bevel(METAL, t, face, fh)
		if side and abs(L - 15.0) < 0.2:
			c = CYAN[2]  # emitter glow ring
		if face == "up" and abs(L - 15.0) < 0.2:
			c = CYAN[1]
		return c

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

	tp = {"rotation": [90, 0, 0], "translation": [0, -2, 4.5], "scale": [0.85, 0.85, 0.85]}
	fp = {"rotation": [0, 4, 0], "translation": [1.5, 2.5, 1], "scale": [0.8, 0.8, 0.8]}

	def mirror(d):
		r, tr = d["rotation"], d["translation"]
		return {"rotation": [r[0], -r[1], -r[2]], "translation": [-tr[0], tr[1], tr[2]], "scale": d["scale"]}

	display = {
		"thirdperson_righthand": tp,
		"thirdperson_lefthand": mirror(tp),
		"firstperson_righthand": fp,
		"firstperson_lefthand": mirror(fp),
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
