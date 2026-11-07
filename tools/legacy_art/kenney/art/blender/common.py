"""Shared helpers for the Ashfall headless Blender art scripts (imported, not run directly).

Works both inside Blender's Python (bpy available) and in plain Python 3.8 with numpy for the PNG
helpers. The other scripts in this folder add this folder to sys.path and `import common`.

Contents:
  read_png(path) -> numpy uint8 array (h, w, 4)       stdlib zlib decoder, 8-bit RGB/RGBA/palette
  write_png(path, arr)                                  RGBA uint8 (h, w, 4) writer
  script_args()                                         argv after "--" (Blender convention)
  reset_scene(), import_glb(path) -> [mesh objects]     Blender scene helpers
  apply_palette(objs, png_path)                         swap every image texture for png_path
                                                        (nearest-neighbour; Kenney colormap UVs)
  tint_materials(objs, rgb, strength)                   optional base-colour tint instead
  world_bounds(objs) -> (min Vector, max Vector)
"""
import struct
import sys
import zlib
from pathlib import Path

import numpy as np


# ---------------------------------------------------------------- PNG I/O (no PIL needed)
def read_png(path):
	data = Path(path).read_bytes()
	assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
	pos, idat, plte, trns = 8, b"", None, None
	while pos < len(data):
		ln, tag = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + ln]
		if tag == b"IHDR":
			w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
		elif tag == b"PLTE":
			plte = np.frombuffer(body, np.uint8).reshape(-1, 3)
		elif tag == b"tRNS":
			trns = np.frombuffer(body, np.uint8)
		elif tag == b"IDAT":
			idat += body
		pos += 12 + ln
	assert depth == 8 and interlace == 0, f"only 8-bit non-interlaced PNGs: {path}"
	bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
	raw = np.frombuffer(zlib.decompress(idat), np.uint8)
	stride = w * bpp
	out = np.zeros((h, stride), np.int32)
	prev = np.zeros(stride, np.int32)
	i = 0
	for y in range(h):
		f = raw[i]
		line = raw[i + 1:i + 1 + stride].astype(np.int32)
		i += 1 + stride
		if f == 0:
			cur = line
		elif f == 2:
			cur = (line + prev) & 255
		else:
			cur = np.zeros(stride, np.int32)
			for x in range(stride):
				a = cur[x - bpp] if x >= bpp else 0
				b = prev[x]
				c = prev[x - bpp] if x >= bpp else 0
				if f == 1:
					p = a
				elif f == 3:
					p = (a + b) >> 1
				else:
					pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
					p = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
				cur[x] = (line[x] + p) & 255
		out[y] = cur
		prev = cur
	px = out.astype(np.uint8).reshape(h, w, bpp)
	if ctype == 3:
		rgb = plte[px[..., 0]]
		a = np.full((h, w, 1), 255, np.uint8)
		if trns is not None:
			t = np.full(256, 255, np.uint8)
			t[:len(trns)] = trns
			a = t[px[..., 0]][..., None]
		return np.concatenate([rgb, a], axis=2)
	if ctype == 0:
		px = np.repeat(px, 3, axis=2)
	elif ctype == 4:
		px = np.concatenate([np.repeat(px[..., :1], 3, axis=2), px[..., 1:]], axis=2)
	if px.shape[2] == 3:
		px = np.concatenate([px, np.full((h, w, 1), 255, np.uint8)], axis=2)
	return px


def write_png(path, arr):
	arr = np.ascontiguousarray(arr, dtype=np.uint8)
	h, w, c = arr.shape
	assert c == 4
	raw = b"".join(b"\x00" + arr[y].tobytes() for y in range(h))

	def chunk(tag, body):
		return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

	Path(path).parent.mkdir(parents=True, exist_ok=True)
	Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
		+ chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def hex_rgb(s):
	s = s.lstrip("#")
	return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def script_args():
	return sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


# ---------------------------------------------------------------- Blender helpers
def reset_scene():
	import bpy
	bpy.ops.wm.read_factory_settings(use_empty=True)


def import_glb(path):
	import bpy
	before = set(bpy.data.objects)
	bpy.ops.import_scene.gltf(filepath=str(path))
	objs = [o for o in bpy.data.objects if o not in before and o.type == "MESH"]
	bpy.context.view_layer.update()
	return objs


def apply_palette(objs, png_path):
	"""Point every image texture node on the objects' materials at png_path (closest filtering)."""
	import bpy
	img = bpy.data.images.load(str(Path(png_path).resolve()), check_existing=True)
	for o in objs:
		for m in o.data.materials:
			if not m or not m.use_nodes:
				continue
			for n in m.node_tree.nodes:
				if n.type == "TEX_IMAGE":
					n.image = img
					n.interpolation = "Closest"
	return img


def tint_materials(objs, rgb, strength=0.6):
	"""Multiply every material's colour by rgb (0..1) mixed by strength (for kits without a colormap)."""
	for o in objs:
		for m in o.data.materials:
			if not m or not m.use_nodes:
				continue
			nt = m.node_tree
			bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
			if not bsdf:
				continue
			src = bsdf.inputs["Base Color"].links[0].from_socket if bsdf.inputs["Base Color"].links else None
			mix = nt.nodes.new("ShaderNodeMix")
			mix.data_type = "RGBA"
			mix.blend_type = "MULTIPLY"
			mix.inputs["Factor"].default_value = strength
			mix.inputs[7].default_value = (*rgb, 1)
			if src:
				nt.links.new(src, mix.inputs[6])
			else:
				mix.inputs[6].default_value = bsdf.inputs["Base Color"].default_value[:]
			nt.links.new(mix.outputs[2], bsdf.inputs["Base Color"])


def world_bounds(objs):
	from mathutils import Vector
	pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
	lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
	hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
	return lo, hi
