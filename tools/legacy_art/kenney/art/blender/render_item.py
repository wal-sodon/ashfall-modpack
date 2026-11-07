"""Render one Ashfall item icon from CC0 3D models with per-face material overrides (headless Blender).

Usage:
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/render_item.py -- \
      <recipe.json> <item id> <out.png> [--hires out_256.png] [--assets .cache/assets]

The recipe file is tools/art/items.json (see its "_doc"). For the item it:
  1. imports every part (GLB, or OBJ for the OpenGameArt tape), normalises each to `size` (longest bbox
     side), applies `stretch` [sx,sy,sz], then `rot` (Euler XYZ degrees) and `loc` about the bbox centre;
  2. classifies every face by its original material name, the colour its UV centroid samples from the
     original texture (or the material base colour), and its normalised position in the part's bbox
     (fx, fy, fz in 0..1), using the first matching rule; the rule names a material from "materials"
     (or "DELETE" to remove the face). Unmatched faces get "default" when given, else keep the original;
  3. renders with render_icon.py's rig (orthographic auto-fit camera, key/fill/rim suns, gradient
     environment, transparent film) at 256 px and downsamples to 32x32 with the same 1 px outline, so
     items and blaster icons form one set.

Material spec keys: color (#hex) | tex (true: keep the original texture colour; tex_lum: use only its
luminance, times color), metal,
Item flags: fix_normals (clear custom normals and recalculate outward normals), smooth (smooth shading).
Remaining spec keys: rough, emit (#hex, defaults to color), emit_strength, color2 + noise
(scale) + noise_ramp [lo, hi] (procedural grime/rust between color and color2), coat.
"""
import colorsys
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import render_icon as ri  # noqa: E402

import bmesh  # noqa: E402
import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Euler, Matrix, Vector  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]

CAMERA_DEFAULTS = {"yaw": 30, "pitch": 24, "roll": 0, "forward": "+x", "margin": 1}


def parse(argv):
	import argparse
	ap = argparse.ArgumentParser(prog="render_item.py")
	ap.add_argument("recipe")
	ap.add_argument("item")
	ap.add_argument("out")
	ap.add_argument("--hires")
	ap.add_argument("--assets", default=str(ROOT / ".cache" / "assets"))
	return ap.parse_args(argv)


def srgb(c):
	return tuple(max(0.0, min(1.0, x)) ** (1 / 2.2) for x in c[:3])


def lin(hexs):
	return tuple((v / 255) ** 2.2 for v in common.hex_rgb(hexs))


# ------------------------------------------------------------------ import + placement
def import_part(path):
	before = set(bpy.data.objects)
	if path.suffix.lower() == ".obj":
		bpy.ops.wm.obj_import(filepath=str(path))
	else:
		bpy.ops.import_scene.gltf(filepath=str(path))
	new = [o for o in bpy.data.objects if o not in before]
	bpy.context.view_layer.update()
	for o in new:  # meshes without materials are helpers/colliders (e.g. the spring's icosphere)
		if o.type == "MESH" and not any(o.data.materials):
			bpy.data.objects.remove(o)
	new = [o for o in bpy.data.objects if o not in before]
	meshes = [o for o in new if o.type == "MESH"]
	for o in meshes:
		if o.data.users > 1:
			o.data = o.data.copy()
	return new, meshes


def place(new, meshes, part):
	lo, hi = common.world_bounds(meshes)
	center = (lo + hi) / 2
	s = part.get("size", 1.0) / max(hi - lo)
	st = part.get("stretch", [1, 1, 1])
	m = (Matrix.Translation(Vector(part.get("loc", [0, 0, 0])))
		@ Euler([math.radians(a) for a in part.get("rot", [0, 0, 0])], "XYZ").to_matrix().to_4x4()
		@ Matrix.Diagonal((st[0] * s, st[1] * s, st[2] * s, 1))
		@ Matrix.Translation(-center))
	root = bpy.data.objects.new("part_root", None)
	bpy.context.scene.collection.objects.link(root)
	for o in new:
		if o.parent is None:
			mw = o.matrix_world.copy()
			o.parent = root
			o.matrix_world = mw
	root.matrix_world = m
	bpy.context.view_layer.update()
	return lo, hi


# ------------------------------------------------------------------ face classification
_img_cache = {}


def img_array(img):
	if img.name not in _img_cache:
		w, h = img.size
		_img_cache[img.name] = np.array(img.pixels[:], np.float32).reshape(h, w, 4) if w and h else None
	return _img_cache[img.name]


def orig_tex(mat):
	if not mat or not mat.use_nodes:
		return None
	n = next((n for n in mat.node_tree.nodes if n.type == "TEX_IMAGE" and n.image), None)
	return n.image if n else None


def face_colour(o, poly, mat):
	img = orig_tex(mat)
	if img is not None and o.data.uv_layers:
		a = img_array(img)
		if a is not None:
			uv = o.data.uv_layers.active.data
			u = sum(uv[i].uv.x for i in poly.loop_indices) / poly.loop_total
			v = sum(uv[i].uv.y for i in poly.loop_indices) / poly.loop_total
			x = int((u % 1.0) * a.shape[1]) % a.shape[1]
			y = int((v % 1.0) * a.shape[0]) % a.shape[0]
			px = a[y, x]
			if img.colorspace_settings.name == "sRGB":
				return srgb(px)  # pixels[] are linear for sRGB images
			return tuple(px[:3])
	if mat and mat.use_nodes:
		b = next((n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
		if b:
			return srgb(b.inputs["Base Color"].default_value)
	return (0.8, 0.8, 0.8)


def matches(rule, matname, rgb, f):
	if "mat" in rule:
		names = rule["mat"] if isinstance(rule["mat"], list) else [rule["mat"]]
		if matname not in names:
			return False
	if "near" in rule:
		cols = rule["near"] if isinstance(rule["near"], list) else [rule["near"]]
		tol = rule.get("tol", 40) / 255
		if not any(math.dist(rgb, [c / 255 for c in common.hex_rgb(h)]) <= tol for h in cols):
			return False
	h, l, s = colorsys.rgb_to_hls(*rgb)
	if "hue" in rule:
		h0, h1 = rule["hue"]
		hd = h * 360
		if not (h0 <= hd <= h1 if h0 <= h1 else (hd >= h0 or hd <= h1)):
			return False
	if "sat" in rule and not (rule["sat"][0] <= s <= rule["sat"][1]):
		return False
	if "lum" in rule and not (rule["lum"][0] <= l <= rule["lum"][1]):
		return False
	for k, i in (("fx", 0), ("fy", 1), ("fz", 2)):
		if k in rule and not (rule[k][0] <= f[i] <= rule[k][1]):
			return False
	if "diag" in rule:  # a*fx + b*fy + c*fz >= d (cut planes)
		a, b, c, d = rule["diag"]
		if a * f[0] + b * f[1] + c * f[2] < d:
			return False
	return True


# ------------------------------------------------------------------ materials
def make_material(name, spec, img):
	m = bpy.data.materials.new(name)
	m.use_nodes = True
	nt = m.node_tree
	b = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
	b.inputs["Metallic"].default_value = spec.get("metal", 0.0)
	b.inputs["Roughness"].default_value = spec.get("rough", 0.5)
	if "coat" in spec:
		b.inputs["Coat Weight"].default_value = spec["coat"]
	col = None
	if spec.get("tex") or spec.get("tex_lum"):
		if img is not None:
			t = nt.nodes.new("ShaderNodeTexImage")
			t.image = img
			t.interpolation = "Closest"
			col = t.outputs["Color"]
			if spec.get("tex_lum"):
				bw = nt.nodes.new("ShaderNodeRGBToBW")
				nt.links.new(col, bw.inputs[0])
				# normalise luminance around 1 so the colour keeps its value but gains shading detail
				mul = nt.nodes.new("ShaderNodeMath")
				mul.operation = "MULTIPLY"
				mul.inputs[1].default_value = spec.get("tex_gain", 1.6)
				nt.links.new(bw.outputs[0], mul.inputs[0])
				mx = nt.nodes.new("ShaderNodeMix")
				mx.data_type = "RGBA"
				mx.blend_type = "MULTIPLY"
				mx.inputs["Factor"].default_value = 1.0
				mx.inputs[6].default_value = (*lin(spec.get("color", "#ffffff")), 1)
				comb = nt.nodes.new("ShaderNodeCombineColor")
				for i in range(3):
					nt.links.new(mul.outputs[0], comb.inputs[i])
				nt.links.new(comb.outputs[0], mx.inputs[7])
				col = mx.outputs[2]
	if col is None:
		base = (*lin(spec.get("color", "#cccccc")), 1)
		if "color2" in spec:
			tc = nt.nodes.new("ShaderNodeTexCoord")
			nz = nt.nodes.new("ShaderNodeTexNoise")
			nz.inputs["Scale"].default_value = spec.get("noise", 8.0)
			nz.inputs["Detail"].default_value = 6
			nt.links.new(tc.outputs["Generated"], nz.inputs["Vector"])
			ramp = nt.nodes.new("ShaderNodeValToRGB")
			lo, hi = spec.get("noise_ramp", [0.45, 0.6])
			ramp.color_ramp.elements[0].position = lo
			ramp.color_ramp.elements[0].color = base
			ramp.color_ramp.elements[1].position = hi
			ramp.color_ramp.elements[1].color = (*lin(spec["color2"]), 1)
			nt.links.new(nz.outputs["Fac"], ramp.inputs["Fac"])
			col = ramp.outputs["Color"]
		else:
			b.inputs["Base Color"].default_value = base
	if col is not None:
		nt.links.new(col, b.inputs["Base Color"])
	if "emit" in spec or "emit_strength" in spec:
		e = spec.get("emit", spec.get("color", "#ffffff"))
		b.inputs["Emission Color"].default_value = (*lin(e), 1)
		b.inputs["Emission Strength"].default_value = spec.get("emit_strength", 2.0)
	return m


def apply_rules(meshes, part_bounds, item):
	rules = item.get("rules", [])
	specs = item.get("materials", {})
	default = item.get("default")
	cache = {}
	lo, hi = part_bounds
	ext = hi - lo
	for o in meshes:
		me = o.data
		# world positions before placement were captured via part bounds; use the root-inverse trick:
		# positions in the part's original world space = parent_inv-free matrix of the imported node
		mw0 = o.get("_mw0")
		mw0 = Matrix([mw0[i * 4:(i + 1) * 4] for i in range(4)]) if mw0 is not None else o.matrix_world
		orig_mats = list(me.materials)
		assign, delete = {}, []
		for p in me.polygons:
			mat = orig_mats[p.material_index] if orig_mats else None
			c = mw0 @ p.center
			f = tuple((c[i] - lo[i]) / ext[i] if ext[i] > 1e-9 else 0.5 for i in range(3))
			rgb = face_colour(o, p, mat)
			name = next((r["use"] for r in rules if matches(r, mat.name if mat else "", rgb, f)), default)
			if name == "DELETE":
				delete.append(p.index)
			elif name:
				img = orig_tex(mat) if (specs[name].get("tex") or specs[name].get("tex_lum")) else None
				key = (name, img.name if img else None)
				if key not in cache:
					cache[key] = make_material(f"{name}", specs[name], img)
				assign[p.index] = cache[key]
		slots = {}
		for p_idx, m in assign.items():
			if m.name not in slots:
				me.materials.append(m)
				slots[m.name] = len(me.materials) - 1
			me.polygons[p_idx].material_index = slots[m.name]
		if delete:
			bm = bmesh.new()
			bm.from_mesh(me)
			bm.faces.ensure_lookup_table()
			bmesh.ops.delete(bm, geom=[bm.faces[i] for i in delete], context="FACES_ONLY")
			loose = [v for v in bm.verts if not v.link_faces]
			bmesh.ops.delete(bm, geom=loose, context="VERTS")
			bm.to_mesh(me)
			bm.free()
		if item.get("fix_normals"):  # some kit meshes import with inverted custom normals (render black)
			if me.has_custom_normals:
				bpy.context.view_layer.objects.active = o
				with bpy.context.temp_override(object=o, active_object=o):
					bpy.ops.mesh.customdata_custom_splitnormals_clear()
			bm = bmesh.new()
			bm.from_mesh(me)
			bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
			bm.to_mesh(me)
			bm.free()
		if item.get("smooth"):
			for p in me.polygons:
				p.use_smooth = True


def main():
	args = parse(common.script_args())
	recipe = json.loads(Path(args.recipe).read_text())
	item = recipe["items"][args.item]
	common.reset_scene()
	all_meshes = []
	for part in item["parts"]:
		new, meshes = import_part(Path(args.assets) / part["file"])
		for o in meshes:
			o["_mw0"] = [x for row in o.matrix_world for x in row]
		bounds = common.world_bounds(meshes)
		apply_rules(meshes, bounds, {**item, **{k: part[k] for k in ("rules", "default") if k in part}})
		place(new, meshes, part)
		all_meshes += meshes
	cam = {**CAMERA_DEFAULTS, **recipe.get("camera", {}), **item.get("camera", {})}
	opts = type("A", (), {})()
	for k, v in {**vars(ri.parse(["x", "y"])), **cam}.items():
		setattr(opts, k, v)
	opts.samples = recipe.get("samples", 64)
	opts.exposure = cam.get("exposure", 0.0)
	ri.orient(all_meshes, opts.forward)
	ri.setup_camera_and_lights(all_meshes, opts)
	out = Path(args.out).resolve()
	tmp = Path(args.hires).resolve() if args.hires else out.with_name(out.stem + "_hires_tmp.png")
	ri.render(opts, tmp)
	hi = common.read_png(tmp)
	small = ri.downscale(hi, opts.size, cam.get("sharpen", opts.sharpen))
	if opts.outline != "none":
		small = ri.outline(small, common.hex_rgb(opts.outline))
	common.write_png(out, small)
	if not args.hires:
		tmp.unlink()
	print(f"wrote {out}")


if __name__ == "__main__":
	main()
