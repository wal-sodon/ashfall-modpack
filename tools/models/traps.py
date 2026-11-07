#!/usr/bin/env python3
"""Ashfall traps: Rattle Box, Tripline Alarm and Spike Strip model JSON, blockstates and item definitions (stdlib only).

The textures are pixel art from tools/gen_block_textures.py (rattle_box_*, tripline_alarm*, spike_strip_*); this
script only builds the element models over them. Writes, under modules/director/src/main/resources/assets/ashfall/:
  models/block/<id>[_<variant>].json   element models
  blockstates/<id>.json                rattle_box wound, tripline_alarm facing x powered, spike_strip wear 0-3
  models/item/<id>.json                block items use their block model (fresh strip, idle box, unlit alarm)
  items/<id>.json                      item definitions -> models/item/<id>

Rattle Box: a squat enamel tin with a winding key on top; the key turns brass while it runs (wound=true).
Tripline Alarm: an olive casing flat on the wall (front faces north, wall to the south), a brass bell and a red lamp
that lights while tripped (powered=true).
Spike Strip: a 1-pixel board with nails standing 2 pixels proud; each wear stage loses two nails and rusts more.

Usage: python3 -I tools/models/traps.py [--root <repo root>]
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
ASSETS = "modules/director/src/main/resources/assets/ashfall"
NS = "ashfall"
FACES = ("north", "south", "west", "east", "up", "down")
# Nail positions on the strip (x, z), in the order they disappear from the end as it wears.
NAILS = [(2, 1), (6, 5), (10, 1), (14, 5), (2, 13), (6, 13), (10, 13), (13, 12)]


def write(root, rel, data):
	path = root / ASSETS / rel
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")
	print(f"wrote {path}")


def face_uv(face, frm, to):
	"""The texture area matching a face's position on the block, like vanilla's automatic UVs."""
	x0, y0, z0 = frm
	x1, y1, z1 = to
	if face in ("up", "down"):
		return [x0, z0, x1, z1]
	if face in ("north", "south"):
		return [x0, 16 - y1, x1, 16 - y0]
	return [z0, 16 - y1, z1, 16 - y0]


def box(frm, to, textures):
	"""An element; textures maps a face to a texture variable, or "*" to one for every face."""
	faces = {}
	for face in FACES:
		tex = textures.get(face, textures.get("*"))
		if tex:
			faces[face] = {"texture": tex, "uv": face_uv(face, frm, to)}
	return {"from": list(frm), "to": list(to), "faces": faces}


def rattle_box(wound):
	return {
		"parent": "minecraft:block/block",
		"textures": {
			"particle": f"{NS}:block/rattle_box_side",
			"side": f"{NS}:block/rattle_box_side",
			"top": f"{NS}:block/rattle_box_top",
			"key": f"{NS}:block/rattle_box_key_wound" if wound else f"{NS}:block/rattle_box_key",
		},
		"elements": [
			box((3, 0, 3), (13, 9, 13), {"*": "#side", "up": "#top", "down": "#top"}),
			box((7.5, 9, 7.5), (8.5, 10, 8.5), {"*": "#key"}),
			box((5.5, 10, 7.5), (10.5, 12, 8.5), {"*": "#key"}),
		],
	}


def tripline_alarm(lit):
	return {
		"parent": "minecraft:block/block",
		"textures": {
			"particle": f"{NS}:block/tripline_alarm",
			"case": f"{NS}:block/tripline_alarm",
			"bell": f"{NS}:block/tripline_alarm_bell",
			"lamp": f"{NS}:block/tripline_alarm_lamp_lit" if lit else f"{NS}:block/tripline_alarm_lamp",
		},
		"elements": [
			box((4, 3, 14), (12, 13, 16), {"*": "#case"}),
			box((5, 8, 12), (11, 12, 14), {"*": "#bell"}),
			box((7, 4, 13), (9, 6, 14), {"*": "#lamp"}),
		],
	}


def spike_strip(wear):
	textures = {"particle": f"{NS}:block/spike_strip_{wear}", "board": f"{NS}:block/spike_strip_{wear}",
				"nail": f"{NS}:block/spike_strip_nail"}
	elements = [box((0, 0, 0), (16, 1, 16), {"*": "#board"})]
	for x, z in NAILS[: len(NAILS) - 2 * wear]:
		elements.append(box((x, 1, z), (x + 1, 3, z + 1), {"*": "#nail", "down": None}))
	return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def item_def(model):
	return {"model": {"type": "minecraft:model", "model": f"{NS}:item/{model}"}}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=ROOT, type=Path)
	root = ap.parse_args().root

	write(root, "models/block/rattle_box.json", rattle_box(False))
	write(root, "models/block/rattle_box_wound.json", rattle_box(True))
	write(root, "blockstates/rattle_box.json", {"variants": {
		"wound=false": {"model": f"{NS}:block/rattle_box"},
		"wound=true": {"model": f"{NS}:block/rattle_box_wound"},
	}})

	write(root, "models/block/tripline_alarm.json", tripline_alarm(False))
	write(root, "models/block/tripline_alarm_lit.json", tripline_alarm(True))
	variants = {}
	for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
		for powered in ("false", "true"):
			variant = {"model": f"{NS}:block/tripline_alarm" + ("_lit" if powered == "true" else "")}
			if y:
				variant["y"] = y
			variants[f"facing={facing},powered={powered}"] = variant
	write(root, "blockstates/tripline_alarm.json", {"variants": variants})

	for wear in range(4):
		write(root, f"models/block/spike_strip_{wear}.json", spike_strip(wear))
	write(root, "blockstates/spike_strip.json", {"variants": {
		f"wear={wear}": {"model": f"{NS}:block/spike_strip_{wear}"} for wear in range(4)}})

	for block, model in (("rattle_box", "rattle_box"), ("tripline_alarm", "tripline_alarm"), ("spike_strip", "spike_strip_0")):
		write(root, f"models/item/{block}.json", {"parent": f"{NS}:block/{model}"})
		write(root, f"items/{block}.json", item_def(block))


if __name__ == "__main__":
	main()
