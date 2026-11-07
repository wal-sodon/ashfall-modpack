#!/usr/bin/env python3
"""Weaponsmith Bench block: textures, element model, blockstate and item model (stdlib only).

A scavenged workbench: a thick scarred plank top with a steel edge strip, a bench vise and a cut-down
blaster receiver clamped on top, a pegboard apron hung with tools, splayed plank legs and a lower
shelf holding a crate of parts. Same palettes and helpers as the charger blocks (blocks.py).

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/block/weaponsmith_bench_{top,plank,apron,metal}.png
  models/block/weaponsmith_bench.json, blockstates/weaponsmith_bench.json (facing -> y rotation)
  models/item/weaponsmith_bench.json, items/weaponsmith_bench.json
Usage: python3 -I tools/models/weaponsmith_bench.py [--root <repo root>]
"""
import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import blocks  # noqa: E402  (palettes and drawing helpers; importing does not run its main)
from blocks import Sprite, rgb, box, bevel, rivet, h, fill, STEEL, DARK, RUST, COPPER, write_json  # noqa: E402

BLOCK = "weaponsmith_bench"
NS = blocks.NS
WOOD = [rgb("c09a68"), rgb("9c7a4e"), rgb("7a5c38"), rgb("553f27"), rgb("3a2a1a")]  # worn pine
PEG = [rgb("b9a383"), rgb("a08a6a"), rgb("7e6a4e")]                                   # pegboard


def planks(s, y0, y1, seams, salt):
	"""Horizontal planks with dark seams, grain streaks and a few nail heads."""
	for y in range(y0, y1 + 1):
		for x in range(16):
			r = h(x, y, salt)
			c = WOOD[1] if r > 0.18 else WOOD[2]
			if (x * 7 + y * 3 + salt) % 11 == 0:
				c = WOOD[2]
			s.set(x, y, c)
	for y in seams:
		s.hline(0, 15, y, WOOD[4])
		if y + 1 <= y1:
			s.hline(0, 15, y + 1, WOOD[0])
	for x, y in ((2, y0 + 1), (13, y0 + 1)):
		s.set(x, y, STEEL[2])


def top():
	s = Sprite()
	planks(s, 0, 15, (4, 9, 13), 3)
	s.hline(0, 15, 0, STEEL[1])
	s.hline(0, 15, 15, STEEL[2])
	# scorch mark and cut grooves where blasters were opened up
	for x, y in ((9, 6), (10, 6), (10, 7), (11, 7), (9, 7)):
		s.set(x, y, WOOD[4])
	s.line(3, 11, 7, 11, WOOD[3])
	return s


def plank():
	s = Sprite()
	planks(s, 0, 15, (5, 11), 7)
	return s


def apron():
	"""Pegboard apron: perforated board, a hanging wrench and screwdriver, a copper coil on a hook."""
	s = Sprite()
	fill(s, PEG[1])
	for y in range(1, 16, 3):
		for x in range(1, 16, 3):
			s.set(x, y, PEG[2])
	s.hline(0, 15, 0, WOOD[2])
	s.hline(0, 15, 15, WOOD[3])
	s.vline(3, 3, 11, STEEL[1])            # wrench
	s.rect(2, 2, 4, 3, STEEL[1])
	s.set(3, 2, PEG[2])
	s.vline(7, 3, 8, STEEL[0])             # screwdriver
	s.rect(6, 9, 8, 12, RUST[0])
	s.set(6, 12, RUST[1])
	for y in (5, 7, 9):                     # coil of copper wire
		s.hline(11, 14, y, COPPER[1])
		s.set(11, y + 1, COPPER[2])
		s.set(14, y + 1, COPPER[2])
	s.set(12, 3, STEEL[2])
	return s


def metal():
	s = Sprite()
	bevel(s, 0, 0, 15, 15, STEEL)
	bevel(s, 3, 3, 12, 12, DARK, inset=True)
	for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
		rivet(s, x, y)
	return s


def model():
	els = [
		# plank top with an apron on the front and back
		box((0, 12, 0), (16, 16, 16), {"up": "top", "down": "plank", "north": "apron", "south": "plank",
										 "east": "plank", "west": "plank"}, cull={"up"}),
		# legs
		box((1, 0, 1), (3, 12, 3), "plank", cull={"down"}),
		box((13, 0, 1), (15, 12, 3), "plank", cull={"down"}),
		box((1, 0, 13), (3, 12, 15), "plank", cull={"down"}),
		box((13, 0, 13), (15, 12, 15), "plank", cull={"down"}),
		# lower shelf and a crate of parts on it
		box((2, 3, 2), (14, 4, 14), "plank"),
		box((4, 4, 5), (10, 8, 11), "metal"),
		# bench vise on the front-left corner, jaws towards the user
		box((2, 16, 1), (6, 18, 5), "metal"),
		box((3, 18, 1), (5, 20, 3), "metal"),
		# a blaster receiver being worked on
		box((8, 16, 6), (14, 17.5, 8), "metal"),
		box((12.5, 16, 7.5), (13.5, 17, 9.5), "metal"),
	]
	return {"parent": "minecraft:block/block", "textures": {
		"particle": f"{NS}:block/{BLOCK}_plank",
		"top": f"{NS}:block/{BLOCK}_top",
		"plank": f"{NS}:block/{BLOCK}_plank",
		"apron": f"{NS}:block/{BLOCK}_apron",
		"metal": f"{NS}:block/{BLOCK}_metal",
	}, "elements": els}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", type=Path, default=blocks.ROOT)
	args = ap.parse_args()
	assets = args.root / blocks.ASSETS
	tex_dir = assets / "textures" / "block"
	tex_dir.mkdir(parents=True, exist_ok=True)
	for name, fn in (("top", top), ("plank", plank), ("apron", apron), ("metal", metal)):
		path = tex_dir / f"{BLOCK}_{name}.png"
		path.write_bytes(fn().png())
		print(f"wrote {path}")
	write_json(assets / "models" / "block" / f"{BLOCK}.json", model())
	variants = {}
	for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
		v = {"model": f"{NS}:block/{BLOCK}"}
		if y:
			v["y"] = y
		variants[f"facing={facing}"] = v
	write_json(assets / "blockstates" / f"{BLOCK}.json", {"variants": variants})
	write_json(assets / "models" / "item" / f"{BLOCK}.json", {"parent": f"{NS}:block/{BLOCK}"})
	write_json(assets / "items" / f"{BLOCK}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{BLOCK}"}})


if __name__ == "__main__":
	main()
