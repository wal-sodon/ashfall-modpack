#!/usr/bin/env python3
"""Lancer Longrifle 3D item model: skin PNG, block-style model JSON, flat-icon model and item definition.

A very rare marksman rifle salvaged from the crashed vessel: a very long, slender barrel threaded with
violet focusing rings, a slim composite handguard with a folded bipod, a tall receiver with a violet
power window, a mounted scope and a skeletal composite stock.

Model space: the barrel points north (-Z, muzzle at z=0), +Y is up, the rifle is centred on x=8.
Skin: 64x64, 2 skin pixels per model unit (uv unit = 4 skin pixels).

Usage: python3 -I tools/models/lancer_longrifle.py [--root <repo root>]
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb, CLEAR  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "lancer_longrifle"
ASSETS = "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64
PX = 2  # skin pixels per model unit

# ---------------------------------------------------------------- palette (hue-shifted ramps)
METAL = [rgb("aab3c4"), rgb("7a8296"), rgb("525a6c"), rgb("363b4a"), rgb("23262f")]   # hi .. deep
COMP = [rgb("a59cae"), rgb("7d7588"), rgb("5c5568"), rgb("403a4a"), rgb("2a2632")]    # plum-slate composite
VIOLET = [rgb("f4e6ff"), rgb("d79bff"), rgb("b45aff"), rgb("7d34c8"), rgb("4a1f80")]
LENS = [rgb("e8fbff"), rgb("8fd8ef"), rgb("3b7fa8"), rgb("1f3f5e")]
RUBBER = [rgb("4a4654"), rgb("363340"), rgb("27252e")]

SIDES = ("north", "south", "east", "west")


# ---------------------------------------------------------------- painters
def plate(ramp):
	"""Generic armour plate: lit top edge, dark bottom edge on side faces; lit up face, dark down face."""
	def paint(s, face, x, y, w, h):
		if face == "up":
			s.rect(x, y, x + w - 1, y + h - 1, ramp[1])
			if w > 2 and h > 2:
				s.rect(x + 1, y + 1, x + w - 2, y + h - 2, ramp[0] if w * h < 12 else ramp[1])
			return
		if face == "down":
			s.rect(x, y, x + w - 1, y + h - 1, ramp[3])
			return
		s.rect(x, y, x + w - 1, y + h - 1, ramp[2])
		if h >= 2:
			s.hline(x, x + w - 1, y, ramp[1])
		if h >= 3:
			s.hline(x, x + w - 1, y + h - 1, ramp[3])
	return paint


def glow_ring(s, face, x, y, w, h):
	if face in ("north", "south"):
		s.rect(x, y, x + w - 1, y + h - 1, VIOLET[3])
		s.rect(x + 1, y + 1, x + w - 2, y + h - 2, VIOLET[1])
		return
	s.rect(x, y, x + w - 1, y + h - 1, VIOLET[2])
	if face == "up":
		s.hline(x, x + w - 1, y, VIOLET[1])
	elif face == "down":
		s.rect(x, y, x + w - 1, y + h - 1, VIOLET[3])
	else:
		s.hline(x, x + w - 1, y, VIOLET[1])
		s.hline(x, x + w - 1, y + 1, VIOLET[0])
		s.hline(x, x + w - 1, y + h - 1, VIOLET[3])


def barrel(s, face, x, y, w, h):
	plate(METAL)(s, face, x, y, w, h)
	if face in ("east", "west") and h >= 2:
		# fine fluting: a dark groove every 3 px along the length
		for i in range(1, w - 1, 3):
			s.set(x + i, y + h - 1, METAL[3])


def muzzle(s, face, x, y, w, h):
	if face == "north":
		s.rect(x, y, x + w - 1, y + h - 1, METAL[3])
		s.rect(x + 1, y + 1, x + w - 2, y + h - 2, VIOLET[2])
		s.set(x + 1, y + 1, VIOLET[0])
		return
	plate(METAL)(s, face, x, y, w, h)
	if face in ("east", "west", "up"):
		# vent ports cut through the brake
		for i in range(0, w, 2):
			s.set(x + i, y + (h // 2), METAL[4])


def handguard(s, face, x, y, w, h):
	plate(COMP)(s, face, x, y, w, h)
	if face in ("east", "west"):
		# a row of cooling slots
		for i in range(1, w - 1, 2):
			s.set(x + i, y + 2, COMP[3])
		s.hline(x, x + w - 1, y + h - 1, COMP[3])


def receiver(s, face, x, y, w, h):
	plate(METAL)(s, face, x, y, w, h)
	if face in ("east", "west"):
		# panel seam, violet power window, and a bolt head
		s.vline(x + w // 2 - 1, y + 1, y + h - 2, METAL[3])
		s.hline(x + 1, x + w - 2, y + 2, VIOLET[3])
		s.hline(x + 1, x + w - 2, y + 3, VIOLET[1])
		s.set(x + (2 if face == "west" else w - 3), y + 3, VIOLET[0])
		s.hline(x + 1, x + w - 2, y + 4, VIOLET[3])
		s.set(x + 1, y + h - 2, METAL[0])
		s.set(x + w - 2, y + h - 2, METAL[0])
	elif face == "up":
		s.rect(x, y, x + w - 1, y + h - 1, METAL[1])
		s.vline(x + w // 2, y, y + h - 1, METAL[3])  # top rail groove


def cell(s, face, x, y, w, h):
	plate(METAL)(s, face, x, y, w, h)
	if face in ("east", "west"):
		s.rect(x + 1, y + 1, x + w - 2, y + h - 2, VIOLET[2])
		s.hline(x + 1, x + w - 2, y + 1, VIOLET[1])


def grip(s, face, x, y, w, h):
	plate(RUBBER + [RUBBER[2], RUBBER[2]])(s, face, x, y, w, h)
	if face in SIDES:
		for j in range(1, h - 1, 2):  # stippled grip ribs
			s.hline(x, x + w - 1, y + j, RUBBER[0])


def stock(s, face, x, y, w, h):
	plate(COMP)(s, face, x, y, w, h)
	if face in ("east", "west") and w >= 4:
		s.hline(x + 1, x + w - 2, y + 1, COMP[0])  # moulded top ridge
		s.set(x + (w - 3 if face == "east" else 2), y + h - 2, VIOLET[3])  # serial-plate glint


def pad(s, face, x, y, w, h):
	s.rect(x, y, x + w - 1, y + h - 1, RUBBER[1])
	if face == "south":
		for j in range(1, h, 2):
			s.hline(x, x + w - 1, y + j, RUBBER[2])
	elif face in SIDES:
		s.hline(x, x + w - 1, y, RUBBER[0])


def scope_tube(s, face, x, y, w, h):
	plate(METAL)(s, face, x, y, w, h)
	if face in ("east", "west") and w > 4:
		s.hline(x + 1, x + w - 2, y, METAL[0])


def scope_bell(lens_face):
	def paint(s, face, x, y, w, h):
		if face == lens_face:
			s.rect(x, y, x + w - 1, y + h - 1, METAL[3])
			s.rect(x + 1, y + 1, x + w - 2, y + h - 2, LENS[2])
			s.set(x + 1, y + 1, LENS[0])
			s.set(x + w - 2, y + h - 2, LENS[3])
			return
		plate(METAL)(s, face, x, y, w, h)
		if face in ("east", "west", "up"):
			s.vline(x + (0 if lens_face == "north" else w - 1), y, y + h - 1, VIOLET[2])
	return paint


def dark(s, face, x, y, w, h):
	plate([METAL[2], METAL[3], METAL[3], METAL[4], METAL[4]])(s, face, x, y, w, h)


def coil(s, face, x, y, w, h):
	glow_ring(s, face, x, y, w, h)
	if face in ("east", "west"):
		s.vline(x, y, y + h - 1, VIOLET[3])


# ---------------------------------------------------------------- geometry
# (name, from, to, painter, rotation or None, faces to omit)
PARTS = [
	("muzzle", (7.25, 7.75, 0), (8.75, 9.25, 1.25), muzzle, None, ()),
	("barrel", (7.5, 8, 1.25), (8.5, 9, 7.5), barrel, None, ("north", "south")),
	("ring1", (7.25, 7.75, 2.25), (8.75, 9.25, 2.75), glow_ring, None, ()),
	("ring2", (7.25, 7.75, 3.75), (8.75, 9.25, 4.25), glow_ring, None, ()),
	("ring3", (7.25, 7.75, 5.25), (8.75, 9.25, 5.75), glow_ring, None, ()),
	("handguard", (7.25, 7.5, 7.5), (8.75, 9.5, 10), handguard, None, ()),
	("coil", (7, 7.25, 10), (9, 9.75, 10.5), coil, None, ()),
	("receiver", (7.1, 7, 10.5), (8.9, 9.5, 12.5), receiver, None, ()),
	("cell", (7.5, 5.75, 10.75), (8.5, 7, 11.75), cell, None, ("up",)),
	("trigger_guard", (7.75, 6, 11.75), (8.25, 6.5, 12.25), dark, None, ("up",)),
	("grip", (7.35, 4.25, 12), (8.65, 7, 13.25), grip, {"origin": [8, 7, 12.625], "axis": "x", "angle": -22.5}, ("up",)),
	("stock_neck", (7.3, 6.5, 12.5), (8.7, 7.75, 13.25), plate(COMP), None, ("up",)),
	("stock", (7.25, 7.75, 12.5), (8.75, 9.25, 15.5), stock, None, ()),
	("stock_strut", (7.4, 6, 13.25), (8.6, 6.75, 15.5), plate(COMP), None, ()),
	("cheek", (7.5, 9.25, 13.25), (8.5, 9.75, 15.25), plate(COMP), None, ("down",)),
	("butt_pad", (7.15, 5.75, 15.5), (8.85, 9.5, 16), pad, None, ()),
	("scope_tube", (7.5, 10.5, 9), (8.5, 11.5, 12), scope_tube, None, ("north", "south")),
	("scope_objective", (7.25, 10.25, 8), (8.75, 11.75, 9), scope_bell("north"), None, ()),
	("scope_eyepiece", (7.35, 10.35, 12), (8.65, 11.65, 13), scope_bell("south"), None, ()),
	("mount_front", (7.75, 9.5, 9.5), (8.25, 10.5, 10), dark, None, ("up",)),
	("mount_rear", (7.75, 9.5, 11.5), (8.25, 10.5, 12), dark, None, ("up",)),
	("turret_top", (7.75, 11.5, 10.5), (8.25, 12, 11), glow_ring, None, ("down",)),
	("turret_side", (8.5, 10.75, 10.5), (9, 11.25, 11), dark, None, ("west",)),
	("bipod_hinge", (7.25, 7, 7), (8.75, 7.5, 8), dark, None, ()),
	("bipod_leg_l", (7.5, 7.25, 2), (7.75, 7.75, 7), dark, None, ("north",)),
	("bipod_leg_r", (8.25, 7.25, 2), (8.5, 7.75, 7), dark, None, ("north",)),
	("bipod_feet", (7.25, 7, 1.75), (8.75, 7.5, 2.25), dark, None, ()),
]


def face_size(face, f, t):
	dx, dy, dz = (t[i] - f[i] for i in range(3))
	w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]
	return max(1, round(w * PX)), max(1, round(h * PX))


def pack():
	"""Shelf-pack every face rect into the skin; returns {(part, face): (x, y, w, h)}."""
	rects = []
	for name, f, t, _, _, omit in PARTS:
		for face in ("north", "south", "east", "west", "up", "down"):
			if face not in omit:
				rects.append((name, face) + face_size(face, f, t))
	rects.sort(key=lambda r: (-r[3], -r[2]))
	out, x, y, shelf = {}, 0, 0, 0
	for name, face, w, h in rects:
		if x + w > SKIN:
			x, y, shelf = 0, y + shelf, 0
		if y + h > SKIN:
			raise SystemExit("skin atlas overflow")
		out[(name, face)] = (x, y, w, h)
		x += w
		shelf = max(shelf, h)
	return out


def build():
	layout = pack()
	skin = Sprite(SKIN, SKIN)
	elements = []
	for name, f, t, painter, rotation, omit in PARTS:
		faces = {}
		for face in ("north", "south", "east", "west", "up", "down"):
			if face in omit:
				continue
			x, y, w, h = layout[(name, face)]
			painter(skin, face, x, y, w, h)
			u = SKIN / 16  # skin px per uv unit
			faces[face] = {"uv": [x / u, y / u, (x + w) / u, (y + h) / u], "texture": "#skin"}
		el = {"name": name, "from": list(f), "to": list(t)}
		if rotation:
			el["rotation"] = rotation
		el["faces"] = faces
		elements.append(el)
	return skin, elements


# Rifle centre is (8, 8, 8); the pistol grip sits at about (8, 6, 13), i.e. 5 units behind and 2 below centre.
HAND = {"rotation": [0, 0, 0], "translation": [0, 4, -3.5], "scale": [1.0, 1.0, 1.0]}
FIRST = {"rotation": [2, 4, 0], "translation": [-1.5, 3.5, -4.5], "scale": [0.9, 0.9, 0.9]}
DISPLAY = {
	"thirdperson_righthand": HAND,
	"thirdperson_lefthand": HAND,      # the renderer mirrors left-hand transforms itself
	"firstperson_righthand": FIRST,
	"firstperson_lefthand": FIRST,
	"head": {"rotation": [0, -90, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
	"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
	"gui": {"rotation": [30, -135, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
	"fixed": {"rotation": [0, -90, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
}


def dump(path, obj):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(obj, indent="\t") + "\n")
	print(f"wrote {path}")


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", type=Path, default=ROOT)
	args = ap.parse_args()
	assets = args.root / ASSETS
	skin, elements = build()
	skin_path = assets / "textures/item" / f"{ID}_skin.png"
	skin_path.parent.mkdir(parents=True, exist_ok=True)
	skin_path.write_bytes(skin.png())
	print(f"wrote {skin_path}")
	dump(assets / "models/item" / f"{ID}.json", {
		"gui_light": "front",
		"textures": {"skin": f"ashfall:item/{ID}_skin", "particle": f"ashfall:item/{ID}_skin"},
		"elements": elements,
		"display": DISPLAY,
	})
	dump(assets / "models/item" / f"{ID}_icon.json", {
		"parent": "minecraft:item/generated",
		"textures": {"layer0": f"ashfall:item/{ID}_icon"},
	})
	dump(assets / "items" / f"{ID}.json", {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"],
					   "model": {"type": "minecraft:model", "model": f"ashfall:item/{ID}_icon"}}],
			"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{ID}"},
		}
	})

	# shared finishing pass: set-wide size, grip placement and display transforms (overrides the above)
	gunkit.finish(assets / "models/item" / f"{ID}.json", ID)

if __name__ == "__main__":
	main()
