#!/usr/bin/env python3
"""Volt Sidearm 3D item model: block-style elements + a generated 64x64 skin (stdlib only).

A sturdy scavenged-but-solid energy pistol: chunky steel emitter shroud with an amber bore, gunmetal
slide with an amber charge line and top vents, under-barrel capacitor coil, a composite frame, and an
angled grip whose battery housing glows amber through a side window (a strip of duct tape holds the
grip plate on). The barrel points to -Z (north) and the top is +Y, so the display transforms below
hold it like a pistol pointing forward.

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/volt_sidearm_skin.png, models/item/volt_sidearm.json,
  models/item/volt_sidearm_icon.json, items/volt_sidearm.json
Usage: python3 -I tools/models/volt_sidearm.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "volt_sidearm"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64          # skin size in pixels
PPU = 2            # skin pixels per model unit (every element dimension sits on the 0.5 grid)

# ---------------------------------------------------------------- palette (hue-shifted ramps)
GUN = [rgb("c3c7cf"), rgb("8f95a3"), rgb("646a7a"), rgb("424656"), rgb("2a2c38")]      # gunmetal
STEEL = [rgb("e0dcd0"), rgb("b2b3b2"), rgb("85898f"), rgb("5a5e68"), rgb("373a44")]    # bare steel
COMP = [rgb("8e8268"), rgb("6e6552"), rgb("524a3d"), rgb("37322a"), rgb("221f1b")]     # composite frame
RUB = [rgb("6a6a70"), rgb("545459"), rgb("404045"), rgb("2c2c31"), rgb("1c1c20")]      # grip rubber
AMB = [rgb("fff4cc"), rgb("ffd47e"), rgb("ffb33c"), rgb("d9821f"), rgb("8a4a12")]      # amber energy
TAPE = [rgb("b4b4ac"), rgb("9a9a92"), rgb("7c7c76"), rgb("5e5e5a")]                    # duct tape
RUST = rgb("8a5a3a")

FACE_TONE = {"up": 1, "north": 2, "south": 2, "east": 2, "west": 2, "down": 3}

# ---------------------------------------------------------------- elements
# (name, from, to, material, rotation or None, faces to skip)
GRIP_ROT = {"origin": [8, 8, 10.5], "axis": "x", "angle": -22.5}
ELEMENTS = [
	# emitter shroud + bore
	("shroud", (6.5, 8, 2.5), (9.5, 11.5, 5), "shroud", None, ()),
	("bore", (7.25, 8.75, 2), (8.75, 10.25, 2.5), "bore", None, ("south",)),
	("front_sight", (7.5, 11.5, 3), (8.5, 12, 4), "dark", None, ("down",)),
	# slide / upper receiver
	("slide", (6.75, 8.5, 5), (9.25, 11, 13), "slide", None, ("north",)),
	("charge_line", (6.5, 9.5, 5.5), (9.5, 10, 10.5), "glow", None, ("north", "south")),
	("vents", (7, 11, 6), (9, 11.5, 10), "vents", None, ("down",)),
	("rear_sight", (7, 11, 11.5), (9, 12, 12.5), "dark", None, ("down",)),
	("heatsink", (7, 8.5, 13), (9, 10.5, 14), "fins", None, ("north",)),
	# under-barrel capacitor coil
	("coil", (7.25, 6.5, 3), (8.75, 8, 7.5), "coil", None, ("up",)),
	("coil_cap", (7, 6.25, 2.5), (9, 8, 3), "dark", None, ()),
	# frame
	("frame", (6.75, 7, 5), (9.25, 8.5, 13), "frame", None, ("up",)),
	# trigger group
	("guard_front", (7.5, 5, 7.5), (8.5, 7, 8), "dark", None, ("up",)),
	("guard_bottom", (7.5, 4.5, 7.5), (8.5, 5, 10.5), "dark", None, ()),
	("trigger", (7.75, 5.5, 9), (8.25, 7, 9.5), "steel_s", None, ("up",)),
	# grip (angled back)
	("grip", (7, 2.5, 9.5), (9, 8.5, 12.5), "grip", GRIP_ROT, ("up",)),
	("battery", (6.75, 3.5, 10), (9.25, 6.5, 12), "battery", GRIP_ROT, ("up",)),
	("grip_base", (6.75, 1.5, 9.25), (9.25, 2.5, 12.75), "base", GRIP_ROT, ()),
	("tape", (6.85, 6.5, 9.4), (9.15, 7.5, 12.6), "tape", GRIP_ROT, ("up", "down")),
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
	if face in ("up", "down"):
		if i == 0 or j == 0:
			return ramp[max(t - 1, 0)]
		if i == w - 1 or j == h - 1:
			return ramp[min(t + 1, len(ramp) - 1)]
		return ramp[t]
	if j == 0 and h > 1:
		return ramp[max(t - 1, 0)]
	if j == h - 1 and h > 2:
		return ramp[min(t + 1, len(ramp) - 1)]
	return ramp[t]


def paint(mat, face, i, j, w, h, p):
	"""Colour of skin pixel (i, j) on a w x h face of material mat; p is its model-space centre."""
	x, y, z = p
	t = FACE_TONE[face]
	side = face in ("east", "west")
	if mat == "slide":
		c = edge_tone(GUN, t, i, j, w, h, face)
		if side and z > 11.5 and z < 13 and j in (1, 2, 3) and int(z * 2) % 2 == 0:
			return GUN[4]                                  # rear grip serrations
		if side and 6 < z < 7 and 8.5 < y < 9.3:
			return STEEL[1] if j == h - 2 else c             # takedown pin head
		if side and j == 1:
			return GUN[1]                                   # bevel catch-light
		if face == "up" and (i in (0, w - 1)) and int(z * 2) % 5 == 0:
			return GUN[0]                                   # worn edges
		if face == "south":
			if 7.5 < x < 8.5 and 9.3 < y < 10.3:
				return AMB[3]                               # charge readout on the back
		return c
	if mat == "shroud":
		c = edge_tone(STEEL, t, i, j, w, h, face)
		if face == "north":
			if 7 < x < 9 and 8.5 < y < 10.5:
				return STEEL[4]                             # dark ring around the bore
			if (i in (0, w - 1)) and (j in (0, h - 1)):
				return STEEL[3]                             # bolt corners
			return c
		if side and 1 <= j <= h - 3 and int(z * 2) in (6, 8):
			return AMB[3] if j % 2 else AMB[4]            # amber-lit vent slots
		if face == "up" and int(z * 2) in (6, 8):
			return STEEL[3]
		if side and j == h - 2 and int(z * 2) == 5 and i % 2 == 0:
			return RUST                                    # a little scavenged wear
		return c
	if mat == "bore":
		if face == "north":
			cx, cy = abs(x - 8), abs(y - 9.5)
			if cx < 0.3 and cy < 0.3:
				return AMB[0]
			if cx < 0.6 and cy < 0.6:
				return AMB[1]
			return AMB[2]
		return AMB[3]
	if mat == "glow":
		if face == "up" or face == "down":
			return AMB[3]
		if j == 0:
			return AMB[1]
		return AMB[2] if int(z * 2) % 4 else AMB[1]
	if mat == "vents":
		if face == "up":
			return GUN[4] if int(z * 2) % 2 == 0 and 0 < i < w - 1 else GUN[1]
		return edge_tone(GUN, 2, i, j, w, h, face)
	if mat == "fins":
		if face == "south":
			return GUN[3] if j % 2 else GUN[1]
		if side:
			return GUN[4] if j % 2 else GUN[2]
		return edge_tone(GUN, t, i, j, w, h, face)
	if mat == "coil":
		band = int(z * 2) % 3
		if face == "down":
			return AMB[3] if band == 0 else STEEL[3]
		if band == 0:
			return AMB[1] if j < h - 1 else AMB[3]
		return STEEL[2] if j < h - 1 else STEEL[3]
	if mat == "dark":
		return edge_tone(GUN, min(t + 1, 4), i, j, w, h, face)
	if mat == "steel_s":
		return STEEL[2] if face != "down" else STEEL[3]
	if mat == "frame":
		c = edge_tone(COMP, t, i, j, w, h, face)
		if side and 10 < z < 11 and j == 1:
			return STEEL[2]                                 # frame screw
		if side and 5.5 < z < 7 and j == 1:
			return COMP[3]                                  # rail groove
		return c
	if mat == "grip":
		c = edge_tone(RUB, t, i, j, w, h, face)
		if face in ("south", "north") and j % 2 == 1 and 0 < i < w - 1:
			return RUB[min(t + 1, 4)]                       # finger grooves
		if side and j % 2 == 1 and j < h - 1:
			return RUB[3]
		return c
	if mat == "battery":
		if side:
			if i == 0 or i == w - 1 or j == 0 or j == h - 1:
				return COMP[3]                              # housing window frame
			if j == 1:
				return AMB[0] if i == 1 else AMB[1]
			if j == h - 2:
				return AMB[3]
			return AMB[4] if i % 3 == 0 else AMB[2]       # cell bands
		return edge_tone(COMP, t, i, j, w, h, face)
	if mat == "base":
		c = edge_tone(STEEL, t + 1, i, j, w, h, face)
		if face == "down" and w // 2 - 1 <= i <= w // 2 and h // 2 - 1 <= j <= h // 2:
			return AMB[3]                                   # charge contact
		return c
	if mat == "tape":
		c = TAPE[1] if face != "north" else TAPE[2]
		if j == 0:
			c = TAPE[0]
		if (i * 3 + j * 5) % 7 == 0:
			c = TAPE[2]
		if side and i == w - 1 and j == h - 1:
			c = TAPE[3]                                     # frayed end
		return c
	return (255, 0, 255, 255)


# ---------------------------------------------------------------- skin packing + model
def build():
	skin = Sprite(SKIN, SKIN)
	faces = []          # (name, face, wpx, hpx)
	for name, fr, to, mat, r, skip in ELEMENTS:
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
	for name, fr, to, mat, r, skip in ELEMENTS:
		el = {"name": name, "from": list(fr), "to": list(to)}
		if r:
			el["rotation"] = dict(r)
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
