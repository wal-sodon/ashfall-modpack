#!/usr/bin/env python3
"""Plasma Blade 3D item model: block-style elements + a generated 64x64 skin (stdlib only).

A scavenged-looking hilt (gunmetal pommel, rubber-wrapped grip with an activation stud, copper collar,
vented emitter with a small crystal window) and, when lit, a beam in the Resonance Crystal's violet:
a near-white core with a violet edge, full-bright (light_emission 15). Same style and tooling as the
blaster models. The blade runs up +Y with the hilt at the bottom; the display transforms tilt it onto
vanilla's sword diagonal so it is held like a sword.

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/plasma_blade_skin.png
  models/item/plasma_blade.json (lit), models/item/plasma_blade_hilt.json (dark, no beam)
  models/item/plasma_blade_icon.json, models/item/plasma_blade_hilt_icon.json
  items/plasma_blade.json  (beam on while the stack has ashfall:blade_lit; flat icons in GUI/ground/frames)
Usage: python3 -I tools/models/plasma_blade.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb  # noqa: E402

ID = "plasma_blade"
ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
SKIN = 64
PPU = 2

GUN = [rgb("c3c7cf"), rgb("8f95a3"), rgb("646a7a"), rgb("424656"), rgb("2a2c38")]
RUB = [rgb("6a6a70"), rgb("545459"), rgb("404045"), rgb("2c2c31"), rgb("1c1c20")]
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630"), rgb("6e3a2e")]
BEAM = [rgb("fff3fa"), rgb("ffd2ee"), rgb("ee8ff0"), rgb("cf4fe6"), rgb("9a3ad8")]

FACE_TONE = {"up": 1, "north": 2, "south": 2, "east": 2, "west": 2, "down": 3}
FACES = ("north", "south", "east", "west", "up", "down")

# (name, from, to, material, faces to skip, glows)
HILT = [
	("pommel", (7, 0, 7), (9, 1, 9), "pommel", (), False),
	("grip", (7.25, 1, 7.25), (8.75, 5, 8.75), "grip", ("up", "down"), False),
	("stud", (8.75, 3, 7.75), (9.25, 3.5, 8.25), "copper", ("west",), False),
	("collar", (7, 5, 7), (9, 5.5, 9), "copper", (), False),
	("emitter", (6.75, 5.5, 6.75), (9.25, 7.5, 9.25), "emitter", (), False),
	("window", (7.5, 6, 6.5), (8.5, 7, 6.75), "window", ("south",), True),
]
BEAM_ELEMENTS = [
	("beam", (7.25, 7.5, 7.25), (8.75, 23, 8.75), "beam", ("down",), True),
	("tip", (7.5, 23, 7.5), (8.5, 24, 8.5), "beam", ("down",), True),
]


def face_dims(fr, to, face):
	dx, dy, dz = (to[i] - fr[i] for i in range(3))
	return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy),
			"up": (dx, dz), "down": (dx, dz)}[face]


def paint(mat, face, i, j, w, h):
	"""Colour of skin pixel (i, j) on a w x h face of material mat."""
	t = FACE_TONE[face]
	if mat == "pommel":
		return GUN[3] if face == "down" else GUN[t + 1] if j == h - 1 else GUN[t]
	if mat == "grip":
		return RUB[t + 1] if j % 3 == 0 else RUB[t]             # wrap ridges
	if mat == "copper":
		return COPPER[0] if j == 0 and face != "down" else COPPER[min(t, 3)]
	if mat == "emitter":
		if face in ("up", "down"):
			return GUN[4] if 1 <= i < w - 1 and 1 <= j < h - 1 else GUN[2]
		if j in (1, 2) and i % 2 == 1:
			return GUN[4]                                       # cooling vents
		return GUN[0] if j == 0 else GUN[t]
	if mat == "window":
		return BEAM[2] if (i + j) % 2 else BEAM[3]
	if mat == "beam":
		if face in ("up", "down"):
			return BEAM[0]
		edge = i == 0 or i == w - 1
		return BEAM[3] if edge else BEAM[1] if i in (1, w - 2) else BEAM[0]
	return (255, 0, 255, 255)


def build():
	skin = Sprite(SKIN, SKIN)
	faces = []
	for name, fr, to, mat, skip, _ in HILT + BEAM_ELEMENTS:
		for f in FACES:
			if f in skip:
				continue
			w, h = face_dims(fr, to, f)
			faces.append((name, f, max(1, round(w * PPU)), max(1, round(h * PPU))))
	# Tallest first: the 31 px beam faces open the first shelf.
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
	models = {}
	for key, parts in (("lit", HILT + BEAM_ELEMENTS), ("hilt", HILT)):
		elements = []
		for name, fr, to, mat, skip, glows in parts:
			el = {"name": name, "from": list(fr), "to": list(to), "faces": {}}
			if glows:
				el["light_emission"] = 15
				el["shade"] = False
			for f in FACES:
				if f in skip:
					continue
				px, py, w, h = place[(name, f)]
				for j in range(h):
					for i in range(w):
						skin.set(px + i, py + j, paint(mat, f, i, j, w, h))
				k = 16 / SKIN
				el["faces"][f] = {"uv": [px * k, py * k, (px + w) * k, (py + h) * k], "texture": "#skin"}
			elements.append(el)
		models[key] = elements
	return skin, models


def display():
	"""Vanilla item/handheld transforms with the vertical blade pre-tilted -45 degrees onto the sword diagonal."""
	return {
		"thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 3.0, 0.5], "scale": [0.75, 0.75, 0.75]},
		"thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 3.0, 0.5], "scale": [0.75, 0.75, 0.75]},
		"firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 2.2, 1.13], "scale": [0.6, 0.6, 0.6]},
		"firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 2.2, 1.13], "scale": [0.6, 0.6, 0.6]},
		"head": {"rotation": [0, 0, 0], "translation": [0, 6, 0], "scale": [0.6, 0.6, 0.6]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
		"gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
		"fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
	}


def write_json(path, obj):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(obj, indent="\t") + "\n")
	print(f"wrote {path}")


def icon_select(icon, model):
	return {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{
			"when": ["gui", "ground", "fixed", "on_shelf"],
			"model": {"type": "minecraft:model", "model": f"ashfall:item/{icon}"},
		}],
		"fallback": {"type": "minecraft:model", "model": f"ashfall:item/{model}"},
	}


def main():
	skin, models = build()
	tex = ASSETS / "textures/item" / f"{ID}_skin.png"
	tex.parent.mkdir(parents=True, exist_ok=True)
	tex.write_bytes(skin.png())
	print(f"wrote {tex}")
	for name, key, icon in ((ID, "lit", f"{ID}_icon"), (f"{ID}_hilt", "hilt", f"{ID}_hilt_icon")):
		write_json(ASSETS / "models/item" / f"{name}.json", {
			"gui_light": "front",
			"textures": {"skin": f"ashfall:item/{ID}_skin", "particle": f"ashfall:item/{icon}"},
			"elements": models[key],
			"display": display(),
		})
		write_json(ASSETS / "models/item" / f"{icon}.json", {
			"parent": "minecraft:item/handheld",
			"textures": {"layer0": f"ashfall:item/{icon}"},
		})
	# The beam (and its light, through LambDynamicLights' own data) follows the ashfall:blade_lit marker.
	write_json(ASSETS / "items" / f"{ID}.json", {
		"model": {
			"type": "minecraft:condition",
			"property": "minecraft:has_component",
			"component": "ashfall:blade_lit",
			"on_true": icon_select(f"{ID}_icon", ID),
			"on_false": icon_select(f"{ID}_hilt_icon", f"{ID}_hilt"),
		}
	})


if __name__ == "__main__":
	main()
