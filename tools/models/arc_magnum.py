#!/usr/bin/env python3
"""Arc Magnum 3D item model: a heavy energy hand cannon (stdlib only).

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/arc_magnum_skin.png   64x64 skin, 1 texel per model unit, auto-packed
  models/item/arc_magnum.json         block-style cuboid model + display transforms
  models/item/arc_magnum_icon.json    flat icon model (layer0 = textures/item/arc_magnum_icon)
  items/arc_magnum.json               3D in hand, flat icon in gui/ground/fixed

Model axes: the gun points north (-Z, muzzle at z=0), up is +Y, centred on x=8. A chunky revolver
silhouette: thick barrel with full underlug and emitter muzzle, a drum of capacitor coils with two
magenta (#ff3cb4) glowing rings (light_emission), a back-raked composite grip.

Usage: python3 -I tools/models/arc_magnum.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "arc_magnum"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64  # texels; 16 uv units span the skin, so 1 uv unit = 4 texels and 1 model unit = 1 texel

# ---------------------------------------------------------------- palette
STEEL = [rgb("17181f"), rgb("262831"), rgb("3a3d48"), rgb("545967"), rgb("767c8a"), rgb("a3a9b3")]
BLUED = [rgb("1c1d25"), rgb("2a2c37"), rgb("3a3d4a"), rgb("4f5363"), rgb("6c7182"), rgb("959aa8")]
GRIP = [rgb("1a1619"), rgb("2a2327"), rgb("3c3236"), rgb("524548"), rgb("6b5c5e")]
COPPER = [rgb("4a2a1e"), rgb("8a5230"), rgb("c47e44"), rgb("e8b070")]
GLOW = [rgb("4a0d34"), rgb("a01a6e"), rgb("ff3cb4"), rgb("ff8ad6"), rgb("ffe2f5")]
MATS = {"steel": STEEL, "blued": BLUED, "grip": GRIP, "copper": COPPER, "glow": GLOW}

FACES = ("north", "south", "east", "west", "up", "down")


def el(name, fr, to, mat, faces=FACES, rot=None, light=0):
	return {"name": name, "from": fr, "to": to, "mat": mat, "faces": faces, "rot": rot, "light": light}


# Grip/hammer rake about the frame's rear bottom.
GRIP_ROT = {"origin": [8, 8, 13], "axis": "x", "angle": -22.5}
HAMMER_ROT = {"origin": [8, 12, 14], "axis": "x", "angle": 22.5}
SIDES = ("north", "south", "east", "west", "up", "down")

ELEMENTS = [
	# --- barrel assembly
	el("lens", [7, 10.5, -0.5], [9, 12.5, 0], "glow", ("north", "east", "west", "up", "down"), light=15),
	el("muzzle", [6, 9.5, 0], [10, 13.5, 2], "steel"),
	el("barrel", [6.5, 10, 2], [9.5, 13, 8], "blued", ("east", "west", "up", "down")),
	el("underlug", [7, 8, 2], [9, 10, 8], "steel", ("north", "east", "west", "down")),
	el("rib", [7.5, 13, 2], [8.5, 14, 8], "steel", ("north", "east", "west", "up")),
	el("front_sight", [7.5, 14, 2], [8.5, 15, 3], "steel", ("north", "south", "east", "west", "up")),
	# --- frame and capacitor drum
	el("frame", [6.5, 8, 8], [9.5, 13, 14], "blued", ("east", "west", "up", "down", "south")),
	el("drum_wide", [5.5, 9.5, 8.5], [10.5, 12.5, 12.5], "steel"),
	el("drum_tall", [6.5, 8.5, 8.5], [9.5, 13.5, 12.5], "steel"),
	el("coil_a_wide", [5.25, 9.25, 9.25], [10.75, 12.75, 10.25], "glow", ("east", "west", "up", "down"), light=12),
	el("coil_a_tall", [6.25, 8.25, 9.25], [9.75, 13.75, 10.25], "glow", ("east", "west", "up", "down"), light=12),
	el("coil_b_wide", [5.25, 9.25, 10.75], [10.75, 12.75, 11.75], "glow", ("east", "west", "up", "down"), light=12),
	el("coil_b_tall", [6.25, 8.25, 10.75], [9.75, 13.75, 11.75], "glow", ("east", "west", "up", "down"), light=12),
	el("rear_sight", [7, 13, 12.5], [9, 14, 13.5], "steel", ("north", "south", "east", "west", "up")),
	el("hammer", [7, 12, 13.5], [9, 14, 15], "steel", rot=HAMMER_ROT),
	# --- lower: guard, trigger, grip
	el("guard_post", [7.5, 6.5, 8], [8.5, 8, 9], "steel", ("north", "south", "east", "west")),
	el("guard", [7.5, 6, 8], [8.5, 7, 11.5], "steel", ("north", "east", "west", "up", "down")),
	el("trigger", [7.5, 7, 10], [8.5, 8, 11], "copper", ("north", "south", "east", "west")),
	el("grip", [6.5, 2, 11], [9.5, 8, 15], "grip", ("north", "south", "east", "west"), rot=GRIP_ROT),
	el("butt", [6, 1, 10.5], [10, 2, 15.5], "steel", rot=GRIP_ROT),
]


def face_size(e, face):
	(x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
	dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]


# ---------------------------------------------------------------- face painting
def ceil(v):
	return int(-(-v // 1))


def paint_face(s, x, y, w, h, e, face):
	"""Paint one face region (w x h texels) at (x, y). Lit from top-left; MC adds its own face shading."""
	ramp = MATS[e["mat"]]
	n = e["name"]
	mid = len(ramp) // 2
	base = ramp[mid + 1] if face == "up" else ramp[mid - 1] if face == "down" else ramp[mid]
	s.rect(x, y, x + w - 1, y + h - 1, base)
	if e["mat"] == "glow":
		# bright core, glowing body; thin coils read as solid light
		s.rect(x, y, x + w - 1, y + h - 1, GLOW[2])
		if w >= 3 and h >= 3:
			s.rect(x + 1, y + 1, x + w - 2, y + h - 2, GLOW[3])
			s.set(x + w // 2 - 1, y + h // 2 - 1, GLOW[4])
		return
	# bevel: light top/left edges, dark bottom/right edges
	if w >= 3 and h >= 2:
		s.hline(x, x + w - 1, y, ramp[min(mid + 1, len(ramp) - 1)])
		s.hline(x, x + w - 1, y + h - 1, ramp[max(mid - 1, 0)])
	if face == "up" and w >= 2:
		s.vline(x, y, y + h - 1, ramp[-1])

	if n == "muzzle":
		if face == "north":  # dark bore ring around the lens (lens covers the centre 2x2)
			s.rect(x, y, x + 3, y + 3, STEEL[3])
			s.hline(x, x + 3, y, STEEL[4])
			s.rect(x + 1, y + 1, x + 2, y + 2, GLOW[1])
		elif face in ("east", "west"):  # vent slot with magenta heat glow
			s.hline(x, x + w - 1, y + 1, STEEL[1])
			s.hline(x, x + w - 1, y + 2, GLOW[1])
		elif face == "up":
			s.hline(x, x + w - 1, y, STEEL[5])
	elif n == "barrel" and face in ("east", "west"):
		# energy conduit line along the barrel, dark seam above it
		s.hline(x, x + w - 1, y, BLUED[4])
		s.hline(x, x + w - 1, y + 1, GLOW[2])
		s.set(x + (w - 1 if face == "east" else 0), y + 1, GLOW[3])  # brightest at the muzzle end
		s.hline(x, x + w - 1, y + 2, BLUED[2])
	elif n == "underlug" and face in ("east", "west"):
		s.hline(x, x + w - 1, y, STEEL[3])
		s.hline(x, x + w - 1, y + 1, STEEL[2])
		s.set(x + (1 if face == "east" else w - 2), y, STEEL[5])  # rivet near the muzzle
	elif n == "rib" and face == "up":
		for i in range(1, h, 2):  # vent holes along the top
			s.set(x, y + i, STEEL[1])
	elif n in ("drum_wide", "drum_tall"):
		if face in ("north", "south"):
			# chamber ends glowing through the drum face
			s.rect(x, y, x + w - 1, y + h - 1, STEEL[2])
			cx, cy = x + w // 2, y + h // 2
			for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
				if 0 <= cx + dx - x < w and 0 <= cy + dy - y < h:
					s.set(cx + dx, cy + dy, GLOW[2])
			s.set(cx, cy, STEEL[0])
		else:
			# axial flutes: copper capacitor windings between steel ribs
			along_u = face in ("east", "west")
			length, across = (w, h) if along_u else (h, w)
			for a in range(across):
				col = COPPER[2] if a % 2 == 1 else STEEL[3]
				if a in (0, across - 1):
					col = STEEL[4] if a == 0 else STEEL[2]
				for l in range(length):
					c = col
					if col == COPPER[2] and l in (0, length - 1):
						c = COPPER[1]
					if along_u:
						s.set(x + l, y + a, c)
					else:
						s.set(x + a, y + l, c)
	elif n == "frame":
		if face in ("east", "west"):
			s.rect(x, y + 1, x + w - 1, y + h - 2, BLUED[3])  # side plate
			s.set(x + (1 if face == "west" else w - 2), y + 2, STEEL[5])  # pin
			s.hline(x, x + w - 1, y + h - 1, BLUED[1])
		elif face == "south":
			s.rect(x + 1, y + 1, x + w - 2, y + 2, BLUED[2])
			s.set(x + 1, y + 1, GLOW[2])  # charge indicator, visible to the shooter
	elif n == "hammer":
		if face == "south":
			for i in range(h):
				s.hline(x, x + w - 1, y + i, STEEL[4] if i % 2 == 0 else STEEL[2])
		if face in ("east", "west"):
			s.set(x + w // 2, y + h // 2, GLOW[2])
	elif n == "rear_sight" and face == "south":
		s.set(x + w // 2, y, STEEL[0])
	elif n == "grip":
		if face in ("east", "west"):
			# composite slab with grip grooves and a magenta cell window
			s.rect(x, y, x + w - 1, y + h - 1, GRIP[2])
			s.vline(x if face == "west" else x + w - 1, y, y + h - 1, GRIP[0])
			s.vline(x + w - 1 if face == "west" else x, y, y + h - 1, GRIP[3])
			s.hline(x, x + w - 1, y, GRIP[4])
			for i in range(2, h - 1, 2):
				s.hline(x + 1, x + w - 2, y + i, GRIP[1])
			s.set(x + w // 2, y + 1, GLOW[2])
		elif face == "south":
			for i in range(1, h, 2):
				s.hline(x, x + w - 1, y + i, GRIP[1])
		elif face == "north":
			for i in range(1, h, 3):
				s.hline(x, x + w - 1, y + i, GRIP[3])
	elif n == "butt":
		if face == "down":
			s.rect(x + 1, y + 1, x + w - 2, y + h - 2, STEEL[1])
			s.rect(x + 1, y + 2, x + 2, y + 2, GLOW[2])
		elif face in ("east", "west", "south"):
			s.set(x + (w - 2 if face == "south" else 1), y, GLOW[3])
	elif n == "trigger" and face in ("east", "west"):
		s.set(x, y + h - 1, COPPER[3])


# ---------------------------------------------------------------- packing + output
def build():
	skin = Sprite(SKIN, SKIN)
	rects = []
	for i, e in enumerate(ELEMENTS):
		for face in e["faces"]:
			w, h = face_size(e, face)
			rects.append((ceil(h), ceil(w), i, face))
	rects.sort(key=lambda r: (-r[0], -r[1]))
	x = y = shelf = 0
	uvs = {}
	for h, w, i, face in rects:
		if x + w > SKIN:
			x, y, shelf = 0, y + shelf, 0
		if y + h > SKIN:
			raise SystemExit("skin atlas overflow")
		e = ELEMENTS[i]
		fw, fh = face_size(e, face)
		paint_face(skin, x, y, w, h, e, face)
		k = 16 / SKIN
		uvs[(i, face)] = [round(x * k, 4), round(y * k, 4), round((x + fw) * k, 4), round((y + fh) * k, 4)]
		x += w
		shelf = max(shelf, h)
	elements = []
	for i, e in enumerate(ELEMENTS):
		out = {"name": e["name"], "from": e["from"], "to": e["to"]}
		if e["rot"]:
			out["rotation"] = e["rot"]
		if e["light"]:
			out["light_emission"] = e["light"]
		out["faces"] = {f: {"uv": uvs[(i, f)], "texture": "#skin"} for f in e["faces"]}
		elements.append(out)
	return skin, elements


DISPLAY = {
	# Third person: the item frame after the hand is +Y forward, +Z up, and the "holding" arm pose
	# pitches it ~18 degrees up, so X 72 levels the barrel (-Z) forward with the grip in the fist.
	"thirdperson_righthand": {"rotation": [72, 0, 0], "translation": [0, 1, 1.5], "scale": [0.7, 0.7, 0.7]},
	"thirdperson_lefthand": {"rotation": [72, 0, 0], "translation": [0, 1, 1.5], "scale": [0.7, 0.7, 0.7]},
	# First person: view space, so -Z already points away from the camera; slight inward yaw.
	"firstperson_righthand": {"rotation": [0, 4, 0], "translation": [-2, 2.5, -1], "scale": [0.75, 0.75, 0.75]},
	"firstperson_lefthand": {"rotation": [0, -4, 0], "translation": [-2, 2.5, -1], "scale": [0.75, 0.75, 0.75]},
	"head": {"rotation": [0, 180, 0], "translation": [0, 10, -1], "scale": [0.8, 0.8, 0.8]},
	"ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
	"gui": {"rotation": [20, 125, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
	"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
}


def write_json(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent=2) + "\n")
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
		"display": DISPLAY,
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
