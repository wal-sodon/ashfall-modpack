"""Render a GLB to a small pixel-art-friendly item icon (headless Blender, Cycles CPU).

Usage:
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/render_icon.py -- \
      <model.glb> <out.png> [--palette tex.png] [--emit-mask mask.png] [--tint #rrggbb] [--tint-strength 0.6]
      [--size 32] [--render-size 256] [--yaw 18] [--pitch 14] [--roll 0] [--forward -y]
      [--metallic 0.55] [--roughness 0.42] [--outline #1b1b22|none] [--margin 1] [--hires out_256.png]
      [--samples 48] [--exposure 0]

  * --palette replaces every image texture of the model (e.g. a repainted Kenney colormap from
    tools/art/palettes); --emit-mask is a same-size PNG whose bright pixels make that colour emissive
    (glowing energy accents). --tint multiplies material colours instead (kits without a colormap).
  * Camera: orthographic, auto-fit to the model's projected silhouette. The model's long axis
    (--forward, the muzzle direction in Blender axes; Kenney blasters point +Y) is turned to screen
    right. yaw turns the model about the vertical axis towards the viewer (0 = pure side view),
    pitch tilts the camera down onto the top, roll rotates the result in the image plane
    (positive = muzzle up, for diagonal icons).
  * Output: rendered at --render-size with transparent film, downscaled with premultiplied area
    filtering, alpha hard-thresholded, light unsharp, then a 1px outline (vanilla readability).
    --margin is the transparent border left inside the icon, outline included in it.
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402

import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Matrix, Vector  # noqa: E402


def parse(argv):
	import argparse
	ap = argparse.ArgumentParser(prog="render_icon.py")
	ap.add_argument("glb")
	ap.add_argument("out")
	ap.add_argument("--palette")
	ap.add_argument("--emit-mask")
	ap.add_argument("--emit-strength", type=float, default=1.6)
	ap.add_argument("--tint")
	ap.add_argument("--tint-strength", type=float, default=0.6)
	ap.add_argument("--size", type=int, default=32)
	ap.add_argument("--render-size", type=int, default=256)
	ap.add_argument("--yaw", type=float, default=18)
	ap.add_argument("--pitch", type=float, default=14)
	ap.add_argument("--roll", type=float, default=0)
	ap.add_argument("--forward", default="+y", help="model muzzle/long axis in Blender coords: +x,-x,+y,-y")
	ap.add_argument("--metallic", type=float, default=0.55)
	ap.add_argument("--roughness", type=float, default=0.42)
	ap.add_argument("--outline", default="#1b1b22")
	ap.add_argument("--margin", type=int, default=1)
	ap.add_argument("--hires")
	ap.add_argument("--samples", type=int, default=48)
	ap.add_argument("--exposure", type=float, default=0.0)
	ap.add_argument("--sharpen", type=float, default=0.6)
	return ap.parse_args(argv)


AXES = {"+x": Vector((1, 0, 0)), "-x": Vector((-1, 0, 0)), "+y": Vector((0, 1, 0)), "-y": Vector((0, -1, 0))}


def setup_materials(objs, args):
	if args.palette:
		common.apply_palette(objs, args.palette)
	if args.tint:
		common.tint_materials(objs, [c / 255 for c in common.hex_rgb(args.tint)], args.tint_strength)
	mask = bpy.data.images.load(str(Path(args.emit_mask).resolve())) if args.emit_mask else None
	if mask:
		mask.colorspace_settings.name = "Non-Color"
	for o in objs:
		for m in o.data.materials:
			if not m or not m.use_nodes:
				continue
			nt = m.node_tree
			bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
			if not bsdf:
				continue
			bsdf.inputs["Metallic"].default_value = args.metallic
			bsdf.inputs["Roughness"].default_value = args.roughness
			tex = next((n for n in nt.nodes if n.type == "TEX_IMAGE"), None)
			if mask and tex:
				mt = nt.nodes.new("ShaderNodeTexImage")
				mt.image = mask
				mt.interpolation = "Closest"
				uvsrc = tex.inputs["Vector"].links[0].from_socket if tex.inputs["Vector"].links else None
				if uvsrc:
					nt.links.new(uvsrc, mt.inputs["Vector"])
				# emissive parts: colour from the palette, strength from the mask; and no metal sheen
				nt.links.new(tex.outputs["Color"], bsdf.inputs["Emission Color"])
				mul = nt.nodes.new("ShaderNodeMath")
				mul.operation = "MULTIPLY"
				mul.inputs[1].default_value = args.emit_strength
				nt.links.new(mt.outputs["Color"], mul.inputs[0])
				nt.links.new(mul.outputs[0], bsdf.inputs["Emission Strength"])
				inv = nt.nodes.new("ShaderNodeMath")
				inv.operation = "SUBTRACT"
				inv.inputs[0].default_value = args.metallic
				nt.links.new(mt.outputs["Color"], inv.inputs[1])
				clamp = nt.nodes.new("ShaderNodeClamp")
				nt.links.new(inv.outputs[0], clamp.inputs["Value"])
				nt.links.new(clamp.outputs[0], bsdf.inputs["Metallic"])


def orient(objs, forward):
	"""Parent everything to an empty and rotate so the long axis is +X (screen right at yaw 0), up +Z."""
	root = bpy.data.objects.new("icon_root", None)
	bpy.context.scene.collection.objects.link(root)
	tops = {o for o in objs if o.parent is None or o.parent not in objs}
	for o in tops:
		mw = o.matrix_world.copy()
		o.parent = root
		o.matrix_world = mw
	f = AXES[forward]
	ang = math.atan2(f.y, f.x)  # rotate f onto +X about Z
	root.rotation_euler = (0, 0, -ang)
	bpy.context.view_layer.update()
	return root


def setup_camera_and_lights(objs, args):
	scene = bpy.context.scene
	cam_data = bpy.data.cameras.new("cam")
	cam_data.type = "ORTHO"
	cam = bpy.data.objects.new("cam", cam_data)
	scene.collection.objects.link(cam)
	scene.camera = cam
	# camera on -Y side looking +Y sees +X to the right and +Z up; then yaw/pitch/roll
	yaw, pitch, roll = map(math.radians, (args.yaw, args.pitch, args.roll))
	rot = Matrix.Rotation(-yaw, 4, "Z") @ Matrix.Rotation(-pitch, 4, "X")
	base = Matrix.Rotation(math.radians(90), 4, "X")  # camera looks along +Y
	cam_rot = rot @ base @ Matrix.Rotation(-roll, 4, "Z")
	lo, hi = common.world_bounds(objs)
	center = (lo + hi) / 2
	dist = (hi - lo).length * 4 + 1
	fwd = (cam_rot.to_3x3() @ Vector((0, 0, -1))).normalized()
	cam.matrix_world = Matrix.Translation(center - fwd * dist) @ cam_rot
	cam_data.clip_end = dist * 4
	bpy.context.view_layer.update()
	# fit: project every vertex into camera space
	inv = cam.matrix_world.inverted()
	xs, ys = [], []
	for o in objs:
		mw = o.matrix_world
		for v in o.data.vertices:
			p = inv @ (mw @ v.co)
			xs.append(p.x)
			ys.append(p.y)
	w, h = max(xs) - min(xs), max(ys) - min(ys)
	usable = (args.size - 2 * args.margin) / args.size
	cam_data.ortho_scale = max(w, h) / usable
	cam_data.shift_x = ((max(xs) + min(xs)) / 2) / cam_data.ortho_scale
	cam_data.shift_y = ((max(ys) + min(ys)) / 2) / cam_data.ortho_scale

	def sun(name, energy, rx, rz, color=(1, 1, 1), angle=8):
		ld = bpy.data.lights.new(name, "SUN")
		ld.energy = energy
		ld.color = color
		ld.angle = math.radians(angle)
		lo_ = bpy.data.objects.new(name, ld)
		lo_.rotation_euler = (math.radians(rx), 0, math.radians(rz))
		scene.collection.objects.link(lo_)

	sun("key", 4.0, 40, -35, (1.0, 0.97, 0.92))       # upper front-left (viewer side)
	sun("fill", 1.2, 70, 120, (0.85, 0.9, 1.0))        # soft from the other side
	sun("rim", 3.0, -60, 160, (0.9, 0.95, 1.0), 4)     # back/top rim for silhouette sheen
	world = bpy.data.worlds.new("w")
	scene.world = world
	world.use_nodes = True
	bg = world.node_tree.nodes["Background"]
	# vertical gradient environment so metal has something to reflect
	nt = world.node_tree
	tc = nt.nodes.new("ShaderNodeTexCoord")
	sep = nt.nodes.new("ShaderNodeSeparateXYZ")
	ramp = nt.nodes.new("ShaderNodeValToRGB")
	ramp.color_ramp.elements[0].position = 0.35
	ramp.color_ramp.elements[0].color = (0.05, 0.05, 0.06, 1)
	ramp.color_ramp.elements[1].position = 0.75
	ramp.color_ramp.elements[1].color = (0.75, 0.78, 0.85, 1)
	mapr = nt.nodes.new("ShaderNodeMapRange")
	mapr.inputs["From Min"].default_value = -1
	nt.links.new(tc.outputs["Generated"], sep.inputs[0])
	nt.links.new(sep.outputs["Z"], mapr.inputs["Value"])
	nt.links.new(mapr.outputs[0], ramp.inputs["Fac"])
	nt.links.new(ramp.outputs["Color"], bg.inputs["Color"])
	bg.inputs["Strength"].default_value = 0.35


def render(args, out_path):
	scene = bpy.context.scene
	scene.render.engine = "CYCLES"
	scene.cycles.device = "CPU"
	scene.cycles.samples = args.samples
	scene.cycles.use_denoising = False
	scene.render.film_transparent = True
	scene.render.resolution_x = scene.render.resolution_y = args.render_size
	scene.render.resolution_percentage = 100
	scene.render.filter_size = 1.0
	scene.view_settings.view_transform = "Standard"
	scene.view_settings.look = "None"
	scene.view_settings.exposure = args.exposure
	scene.render.image_settings.file_format = "PNG"
	scene.render.image_settings.color_mode = "RGBA"
	scene.render.filepath = str(out_path)
	bpy.ops.render.render(write_still=True)


def downscale(hi, size, sharpen):
	"""Area-average a square RGBA uint8 image to size x size (premultiplied), threshold alpha."""
	f = hi.astype(np.float64) / 255
	n = hi.shape[0] // size
	a = f[..., 3:4]
	pm = f[..., :3] * a
	pm = pm.reshape(size, n, size, n, 3).mean(axis=(1, 3))
	aa = a.reshape(size, n, size, n, 1).mean(axis=(1, 3))
	rgb = np.where(aa > 1e-6, pm / np.maximum(aa, 1e-6), 0)
	if sharpen > 0:
		# unsharp mask on colour, only among opaque pixels
		pad = np.pad(rgb, ((1, 1), (1, 1), (0, 0)), mode="edge")
		blur = (pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:] + 4 * rgb) / 8
		rgb = np.clip(rgb + sharpen * (rgb - blur), 0, 1)
	alpha = (aa[..., 0] >= 0.45)
	out = np.zeros((size, size, 4), np.uint8)
	out[..., :3] = np.round(rgb * 255).astype(np.uint8)
	out[..., 3] = np.where(alpha, 255, 0)
	out[~alpha, :3] = 0
	return out


def outline(img, color):
	op = img[..., 3] > 0
	pad = np.pad(op, 1)
	nb = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
	ring = nb & ~op
	out = img.copy()
	out[ring, :3] = color
	out[ring, 3] = 255
	return out


def main():
	args = parse(common.script_args())
	common.reset_scene()
	objs = common.import_glb(args.glb)
	setup_materials(objs, args)
	orient(objs, args.forward)
	setup_camera_and_lights(objs, args)
	out = Path(args.out).resolve()
	tmp = Path(args.hires).resolve() if args.hires else out.with_name(out.stem + "_hires_tmp.png")
	render(args, tmp)
	hi = common.read_png(tmp)
	small = downscale(hi, args.size, args.sharpen)
	if args.outline != "none":
		small = outline(small, common.hex_rgb(args.outline))
	common.write_png(out, small)
	if not args.hires:
		tmp.unlink()
	print(f"wrote {out}")


if __name__ == "__main__":
	main()
