"""Render Minecraft block-style item model JSONs (cuboid elements + skin) side by side (headless Blender).

Usage:
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/render_mc_model.py -- \
      <out.png> <model.json> [<model.json> ...] [--cell 320] [--cols 4] [--yaw 35] [--pitch 25]
      [--root <repo root>] [--bg #3a3c44]

Each model gets one cell, rendered like the game draws item models: textures nearest-filtered and
unlit, with Minecraft's fixed face shading (up 1.0, north/south 0.8, east/west 0.6, down 0.5).
The view is a 3/4 orthographic view with the gun's muzzle (-Z, north) pointing right, so it reads
like an inventory/side view. All cells share one scale (16 model units fill a cell) so relative
sizes are honest. Texture refs "ns:path" resolve to modules/*/src/main/resources/assets/<ns>/textures/
<path>.png under --root. Supports element rotation (single axis) and face uv (rotation ignored).
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402

import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def parse(argv):
	import argparse
	ap = argparse.ArgumentParser(prog="render_mc_model.py")
	ap.add_argument("out")
	ap.add_argument("models", nargs="+")
	ap.add_argument("--cell", type=int, default=320)
	ap.add_argument("--cols", type=int, default=4)
	ap.add_argument("--yaw", type=float, default=30)
	ap.add_argument("--pitch", type=float, default=25)
	ap.add_argument("--root", default=str(ROOT))
	ap.add_argument("--bg", default="#3a3c44")
	return ap.parse_args(argv)


def resolve(ref, textures, root, depth=0):
	while ref.startswith("#") and depth < 8:
		ref = textures.get(ref[1:], "")
		depth += 1
	ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
	for p in Path(root).glob(f"modules/*/src/main/resources/assets/{ns}/textures/{path}.png"):
		return p
	raise FileNotFoundError(ref)


def face_quad(name, f, t):
	"""Corners (MC coords) in order top-left, top-right, bottom-right, bottom-left as seen from outside."""
	x0, y0, z0 = f
	x1, y1, z1 = t
	return {
		"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
		"south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
		"west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
		"east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
		"up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
		"down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
	}[name]


def rotate(p, rot):
	if not rot:
		return p
	o = rot.get("origin", [8, 8, 8])
	ax = "xyz".index(rot["axis"])
	a = math.radians(rot["angle"])
	m = Matrix.Rotation(a, 3, "XYZ"[ax])
	return tuple(Vector(o) + m @ (Vector(p) - Vector(o)))


def mc_to_bl(p, off):
	# MC (x right, y up, z south) -> Blender (x, -z, y)
	return Vector((p[0] - 8 + off[0], -(p[2] - 8) + off[1], p[1] - 8 + off[2]))


def material_for(path, cache):
	if path in cache:
		return cache[path]
	img = bpy.data.images.load(str(path))
	m = bpy.data.materials.new(path.stem)
	m.use_nodes = True
	nt = m.node_tree
	for n in list(nt.nodes):
		nt.nodes.remove(n)
	out = nt.nodes.new("ShaderNodeOutputMaterial")
	tex = nt.nodes.new("ShaderNodeTexImage")
	tex.image = img
	tex.interpolation = "Closest"
	attr = nt.nodes.new("ShaderNodeAttribute")
	attr.attribute_name = "shade"
	mul = nt.nodes.new("ShaderNodeMix")
	mul.data_type = "RGBA"
	mul.blend_type = "MULTIPLY"
	mul.inputs["Factor"].default_value = 1.0
	nt.links.new(tex.outputs["Color"], mul.inputs[6])
	nt.links.new(attr.outputs["Color"], mul.inputs[7])
	em = nt.nodes.new("ShaderNodeEmission")
	nt.links.new(mul.outputs[2], em.inputs["Color"])
	tr = nt.nodes.new("ShaderNodeBsdfTransparent")
	mix = nt.nodes.new("ShaderNodeMixShader")
	nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
	nt.links.new(tr.outputs[0], mix.inputs[1])
	nt.links.new(em.outputs[0], mix.inputs[2])
	nt.links.new(mix.outputs[0], out.inputs["Surface"])
	m.blend_method = "CLIP"
	cache[path] = (m, img.size[0], img.size[1])
	return cache[path]


def build(model_path, root, off, cache):
	model = json.loads(Path(model_path).read_text())
	textures = model.get("textures", {})
	verts, faces, uvs, shades, mats = [], [], [], [], []
	matlist = []
	for el in model["elements"]:
		for name, fc in el.get("faces", {}).items():
			path = resolve(fc["texture"], textures, root)
			m, w, h = material_for(path, cache)
			if m not in matlist:
				matlist.append(m)
			q = face_quad(name, el["from"], el["to"])
			q = [rotate(p, el.get("rotation")) for p in q]
			base = len(verts)
			verts += [mc_to_bl(p, off) for p in q]
			faces.append((base, base + 1, base + 2, base + 3))
			u0, v0, u1, v1 = fc.get("uv", [0, 0, 16, 16])
			# uv per corner TL, TR, BR, BL (MC v grows downwards)
			cuv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
			uvs.append([(u / 16, 1 - v / 16) for (u, v) in cuv])
			shades.append(SHADE[name])
			mats.append(matlist.index(m))
	me = bpy.data.meshes.new(Path(model_path).stem)
	me.from_pydata([tuple(v) for v in verts], [], faces)
	me.uv_layers.new()
	me.color_attributes.new("shade", "FLOAT_COLOR", "CORNER")
	uvl = me.uv_layers[0]  # fetch after adding both layers: adding one invalidates earlier refs
	col = me.color_attributes["shade"]
	for pi, poly in enumerate(me.polygons):
		poly.material_index = mats[pi]
		for li in poly.loop_indices:
			idx = me.loops[li].vertex_index % 4  # position within the quad as built (TL, TR, BR, BL)
			uvl.data[li].uv = uvs[pi][idx]
			s = shades[pi]
			col.data[li].color = (s, s, s, 1)
	for m in matlist:
		me.materials.append(m)
	ob = bpy.data.objects.new(me.name, me)
	bpy.context.scene.collection.objects.link(ob)
	return ob


def main():
	args = parse(common.script_args())
	common.reset_scene()
	scene = bpy.context.scene
	cache = {}
	n = len(args.models)
	cols = min(args.cols, n)
	rows = (n + cols - 1) // cols
	cells = []
	for i, mp in enumerate(args.models):
		ob = build(mp, args.root, (0, 0, 0), cache)
		cells.append(ob)
	cam_d = bpy.data.cameras.new("cam")
	cam_d.type = "ORTHO"
	cam_d.ortho_scale = 17
	cam = bpy.data.objects.new("cam", cam_d)
	scene.collection.objects.link(cam)
	scene.camera = cam
	yaw, pitch = math.radians(args.yaw), math.radians(args.pitch)
	# muzzle is Blender +Y; view from +X (muzzle to the right), yawed towards the muzzle end and pitched down
	d = Vector((math.cos(pitch) * math.cos(yaw), -math.cos(pitch) * math.sin(yaw), math.sin(pitch)))
	cam.location = d * 60
	cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
	cam_d.clip_end = 200
	scene.render.engine = "CYCLES"
	scene.cycles.device = "CPU"
	scene.cycles.samples = 16
	scene.cycles.use_denoising = False
	scene.render.film_transparent = True
	scene.view_settings.view_transform = "Standard"
	scene.render.resolution_x = scene.render.resolution_y = args.cell
	bg = np.array(common.hex_rgb(args.bg), float)
	sheet = np.zeros((rows * args.cell, cols * args.cell, 4), np.uint8)
	sheet[..., :3] = bg
	sheet[..., 3] = 255
	tmp = Path(args.out).with_suffix(".cell.png")
	for i, ob in enumerate(cells):
		for o in cells:
			o.hide_render = o is not ob
		scene.render.filepath = str(tmp)
		bpy.ops.render.render(write_still=True)
		img = common.read_png(tmp).astype(float)
		a = img[..., 3:4] / 255
		r, c = divmod(i, cols)
		reg = sheet[r * args.cell:(r + 1) * args.cell, c * args.cell:(c + 1) * args.cell, :3]
		reg[...] = (img[..., :3] * a + reg * (1 - a)).astype(np.uint8)
	tmp.unlink()
	common.write_png(args.out, sheet)
	print(f"wrote {args.out}")


if __name__ == "__main__":
	main()
