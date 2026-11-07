#!/usr/bin/env python3
"""Paint Ashfall's worn armour layers (64x32, stdlib only).

The vanilla iron layers are read only for their alpha mask, i.e. which pixels of the humanoid UV map an armour
layer covers; every colour is painted here from the set palettes in tools/textures/armor_art.py. The Minecraft
client jar is taken from Loom's cache, so run a Gradle build first.

Usage: python3 -I tools/gen_armor_layers.py [--root <repo root>]
"""
import argparse
import struct
import sys
import zipfile
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent / "textures"
sys.path.insert(0, str(HERE))
from sprite import Sprite, rgb  # noqa: E402
from armor_art import SETS  # noqa: E402

CLIENT_JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
OUT = "modules/director/src/main/resources/assets/ashfall/textures/entity/equipment/{layer}/{set}.png"


def read_png(data):
	"""RGBA rows of an 8-bit RGBA or a 1-8 bit palette PNG (the formats vanilla armour layers use)."""
	pos, chunks = 8, {}
	idat = b""
	while pos < len(data):
		length, kind = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + length]
		if kind == b"IDAT":
			idat += body
		else:
			chunks.setdefault(kind, body)
		pos += 12 + length
	w, h, depth, ctype = struct.unpack(">IIBB", chunks[b"IHDR"][:10])
	assert (ctype == 6 and depth == 8) or (ctype == 3 and depth in (1, 2, 4, 8)), (depth, ctype)
	bpp = 4 if ctype == 6 else 1
	stride = w * 4 if ctype == 6 else (w * depth + 7) // 8
	raw, rows, prev = zlib.decompress(idat), [], bytearray(stride)
	for y in range(h):
		f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
		for i in range(len(line)):
			a = line[i - bpp] if i >= bpp else 0
			b, c = prev[i], prev[i - bpp] if i >= bpp else 0
			if f == 1: line[i] = (line[i] + a) & 255
			elif f == 2: line[i] = (line[i] + b) & 255
			elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
			elif f == 4:
				p = a + b - c
				pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
				line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
		prev = line
		if ctype == 6:
			rows.append([tuple(line[x * 4:x * 4 + 4]) for x in range(w)])
		else:
			plte, trns = chunks[b"PLTE"], chunks.get(b"tRNS", b"")
			per = 8 // depth
			idx = [(line[x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1) for x in range(w)]
			rows.append([(*plte[i * 3:i * 3 + 3], trns[i] if i < len(trns) else 255) for i in idx])
	return w, h, rows


def paint(mask, set_name, leggings):
	pal = {k: rgb(v) for k, v in SETS[set_name].items()}
	w, h = len(mask[0]), len(mask)
	on = lambda x, y: 0 <= x < w and 0 <= y < h and mask[y][x][3] > 0
	out = [[(0, 0, 0, 0)] * w for _ in range(h)]
	for y in range(h):
		for x in range(w):
			if not on(x, y):
				continue
			c = pal["B"]
			if y < 16:                                   # head: lighter cap, visor across the face
				c = pal["H"] if y < 8 else pal["L"]
				if 9 <= x <= 14 and 10 <= y <= 12 and set_name != "scavenger":
					c = pal["V"]
				if set_name == "scavenger" and 9 <= x <= 14 and y == 11:
					c = pal["V"]                         # goggles strap line
			elif x < 16 and not leggings:                # boots on the main layer: sole and laces
				c = pal["A"] if y >= 30 else pal["L"] if y == 27 else pal["B"]
			elif leggings:
				if y in (20, 21) and 16 <= x < 40:
					c = pal["A"]                         # belt
				elif y == 25 and x < 16:
					c = pal["A"]                         # knee pads / seals
			else:
				if 20 <= x < 28 and 20 <= y < 32:        # chest front
					if set_name == "hazmat" and x in (23, 24):
						c = pal["A"]                     # zip seal
					elif set_name == "riot" and 21 <= y <= 24:
						c = pal["L"]                     # chest plate
					elif set_name == "scavenger" and (x, y) in ((21, 22), (22, 22), (21, 23), (22, 23), (25, 27), (26, 27)):
						c = pal["A"]                     # tape patches
				if 40 <= x < 56 and y == 26:
					c = pal["A"]                         # cuffs
			# darken pixels on a face edge, lighten the top row of each face
			if not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1)):
				c = tuple(max(0, int(v * 0.72)) for v in c[:3]) + (255,)
			elif (x * 7 + y * 13) % 11 == 0:
				c = tuple(min(255, int(v * 1.08)) for v in c[:3]) + (255,)
			out[y][x] = c if len(c) == 4 else c + (255,)
	return out


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=Path(__file__).resolve().parent.parent, type=Path)
	args = ap.parse_args()
	with zipfile.ZipFile(CLIENT_JAR) as jar:
		masks = {layer: read_png(jar.read(f"assets/minecraft/textures/entity/equipment/{layer}/iron.png"))[2]
				 for layer in ("humanoid", "humanoid_leggings")}
	for set_name in SETS:
		for layer, mask in masks.items():
			px = paint(mask, set_name, layer == "humanoid_leggings")
			s = Sprite.__new__(Sprite)
			s.px = px
			path = args.root / OUT.format(layer=layer, set=set_name)
			path.parent.mkdir(parents=True, exist_ok=True)
			path.write_bytes(s.png_sized(len(px[0]), len(px)))
			print("wrote", path.relative_to(args.root))


if __name__ == "__main__":
	main()
