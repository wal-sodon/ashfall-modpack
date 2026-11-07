#!/usr/bin/env python3
"""Flamethrower 3D item model: a scrap-built fuel sprayer (stdlib only).

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/flamethrower_skin.png   64x64 skin, auto-packed
  models/item/flamethrower.json         block-style cuboid model + display transforms
  models/item/flamethrower_icon.json    flat icon model (layer0 = textures/item/flamethrower_icon)
  items/flamethrower.json               3D in hand, flat icon in gui/ground/fixed

Model axes as every gun: the nozzle points north (-Z, tip at z=0), up is +Y, centred on x=8. A long steel
wand with a perforated heat shield and a glowing pilot flame at the tip, a boxy valve body at the back, a red
Lighter Fluid tank strapped underneath with duct tape, a copper feed hose, a front grip and a raked rear grip.

Usage: python3 -I tools/models/flamethrower.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gunkit  # noqa: E402

ID = "flamethrower"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64

# ---------------------------------------------------------------- palette
STEEL = [rgb("17181f"), rgb("262831"), rgb("3a3d48"), rgb("545967"), rgb("767c8a"), rgb("a3a9b3")]
SOOT = [rgb("121214"), rgb("1e1e22"), rgb("2c2b30"), rgb("3d3b40"), rgb("55525a")]
TANK = [rgb("3a0e0b"), rgb("6a1a16"), rgb("a82c24"), rgb("d8483a"), rgb("f0846e")]
TAPE = [rgb("6e6a5e"), rgb("8e897a"), rgb("b8b2a0"), rgb("d6d0bd")]
GRIP = [rgb("1a1619"), rgb("2a2327"), rgb("3c3236"), rgb("524548"), rgb("6b5c5e")]
COPPER = [rgb("4a2a1e"), rgb("8a5230"), rgb("c47e44"), rgb("e8b070")]
FLAME = [rgb("7a2a0c"), rgb("c4501a"), rgb("ff9a2a"), rgb("ffc860"), rgb("fff2c0")]
MATS = {"steel": STEEL, "soot": SOOT, "tank": TANK, "tape": TAPE, "grip": GRIP, "copper": COPPER, "flame": FLAME}

FACES = ("north", "south", "east", "west", "up", "down")
GRIP_ROT = {"origin": [8, 9, 14], "axis": "x", "angle": -22.5}


def el(name, fr, to, mat, faces=FACES, rot=None, light=0):
	return {"name": name, "from": fr, "to": to, "mat": mat, "faces": faces, "rot": rot, "light": light}


ELEMENTS = [
	# --- wand
	el("pilot", [7.25, 10.75, -0.75], [8.75, 12.25, 0], "flame", ("north", "east", "west", "up", "down"), light=15),
	el("nozzle", [6.5, 10, 0], [9.5, 13, 1.5], "soot"),
	el("wand", [7, 10.5, 1.5], [9, 12.5, 9], "steel", ("east", "west", "up", "down")),
	el("shield", [6.25, 9.75, 2.5], [9.75, 13.25, 6], "steel", ("north", "south", "east", "west", "up", "down")),
	el("igniter", [8.75, 9.5, 1.5], [9.75, 10.5, 3], "copper", ("north", "east", "up", "down")),
	# --- valve body
	el("body", [6, 9, 9], [10, 13.5, 15], "steel", ("north", "south", "east", "west", "up", "down")),
	el("valve", [7, 13.5, 10], [9, 14.5, 12], "copper", ("north", "south", "east", "west", "up")),
	el("gauge", [10, 11, 10.5], [10.5, 12.5, 12], "steel", ("north", "south", "east", "up", "down")),
	# --- grips and trigger
	el("grip", [6.75, 3.5, 13], [9.25, 9, 15.5], "grip", ("north", "south", "east", "west", "down"), rot=GRIP_ROT),
	el("trigger", [7.5, 7.5, 11.5], [8.5, 9, 12.5], "copper", ("north", "south", "east", "west")),
	el("front_grip", [7, 5.5, 3.5], [9, 9.75, 5.5], "grip", ("north", "south", "east", "west", "down")),
	# --- fuel tank, taped under the wand
	el("tank", [5.5, 5, 6], [10.5, 9, 11.5], "tank"),
	el("tape_a", [5.25, 4.75, 7], [10.75, 9.25, 8], "tape", ("east", "west", "up", "down")),
	el("tape_b", [5.25, 4.75, 9.5], [10.75, 9.25, 10.5], "tape", ("east", "west", "up", "down")),
	el("cap", [7.25, 6, 5.25], [8.75, 7.5, 6], "steel", ("north", "east", "west", "up", "down")),
	el("hose", [7.5, 9, 6.5], [8.5, 10.5, 7.5], "copper", ("north", "south", "east", "west")),
]


def face_size(e, face):
	(x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
	dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]


def ceil(v):
	return int(-(-v // 1))


def paint_face(s, x, y, w, h, e, face):
	"""Paint one face region (w x h texels) at (x, y), lit from the top-left like the other guns."""
	ramp = MATS[e["mat"]]
	n = e["name"]
	mid = len(ramp) // 2
	base = ramp[mid + 1] if face == "up" else ramp[mid - 1] if face == "down" else ramp[mid]
	s.rect(x, y, x + w - 1, y + h - 1, base)
	if e["mat"] == "flame":
		s.rect(x, y, x + w - 1, y + h - 1, FLAME[2])
		if w >= 2 and h >= 2:
			s.set(x + w // 2, y + h // 2, FLAME[4])
			s.set(x, y + h - 1, FLAME[1])
		return
	if w >= 3 and h >= 2:
		s.hline(x, x + w - 1, y, ramp[min(mid + 1, len(ramp) - 1)])
		s.hline(x, x + w - 1, y + h - 1, ramp[max(mid - 1, 0)])

	if n == "shield" and face in ("east", "west", "up", "down"):
		# perforations: a grid of dark holes over the hot wand
		for i in range(1, w - 1, 2):
			for j in range(1, h - 1, 2):
				s.set(x + i, y + j, STEEL[1])
	elif n == "nozzle":
		if face == "north":
			s.rect(x, y, x + w - 1, y + h - 1, SOOT[1])
			s.rect(x + 1, y + 1, x + w - 2, y + h - 2, FLAME[0])
		else:
			s.hline(x, x + w - 1, y + h // 2, SOOT[0])
	elif n == "wand" and face in ("east", "west"):
		s.hline(x, x + w - 1, y, STEEL[4])
		# soot creeping back from the nozzle
		for i in range(min(3, w)):
			s.set(x + (i if face == "east" else w - 1 - i), y + h - 1, SOOT[2])
	elif n == "body":
		if face in ("east", "west"):
			s.rect(x + 1, y + 1, x + w - 2, y + h - 2, STEEL[2])
			s.set(x + 1, y + 1, STEEL[5])
			s.set(x + w - 2, y + 1, STEEL[5])
			s.set(x + 1, y + h - 2, STEEL[5])
			s.set(x + w - 2, y + h - 2, STEEL[5])
		elif face == "south":
			s.rect(x + 1, y + 1, x + w - 2, y + 2, STEEL[1])
			s.set(x + w // 2, y + 1, FLAME[2])  # pilot lamp, visible to the shooter
	elif n == "gauge" and face == "east":
		s.rect(x, y, x + w - 1, y + h - 1, TAPE[3])
		s.set(x + w // 2, y + h // 2, TANK[2])
	elif n == "tank":
		if face in ("east", "west"):
			s.hline(x, x + w - 1, y + 1, TANK[4])
			s.hline(x, x + w - 1, y + h - 2, TANK[1])
			# a scuffed warning label
			if w >= 6 and h >= 4:
				s.rect(x + 2, y + 2, x + 3, y + 2, TAPE[3])
		elif face == "up":
			s.vline(x, y, y + h - 1, TANK[4])
	elif n in ("tape_a", "tape_b"):
		for i in range(0, max(w, h), 3):
			if face in ("east", "west"):
				s.set(x + min(i, w - 1), y + min(i // 2, h - 1), TAPE[1])
			else:
				s.set(x + min(i, w - 1), y, TAPE[0])
	elif n in ("grip", "front_grip") and face in ("east", "west"):
		for i in range(1, h - 1, 2):
			s.hline(x, x + w - 1, y + i, GRIP[1])
	elif n == "hose":
		for i in range(0, h, 2):
			s.hline(x, x + w - 1, y + i, COPPER[3])


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
			out["rotation"] = dict(e["rot"])
		if e["light"]:
			out["light_emission"] = e["light"]
		out["faces"] = {f: {"uv": uvs[(i, f)], "texture": "#skin"} for f in e["faces"]}
		elements.append(out)
	return skin, elements


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
	# shared finishing pass: set-wide size, grip placement and display transforms
	gunkit.finish(ASSETS / "models/item" / f"{ID}.json", ID)


if __name__ == "__main__":
	main()
