"""Convert a GLB (painted with a palette texture) into a Minecraft 3D item model + skin (headless Blender).

Usage:
  /Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup -P tools/art/blender/mc_model.py -- \
      <model.glb> --id <item_id> --length <px> --palette tex.png [--emit-mask mask.png] \
      --model-out models/item/<id>.json --skin-out textures/item/<id>_skin.png \
      [--namespace ashfall] [--res 2] [--colors 9] [--threshold 0.4] [--forward +y] \
      [--grip zfrac,yfrac] [--accent #rrggbb] [--glow-boost 0.15] [--skin-size 32] [--max-elements 140]

Pipeline:
  1. Orient: the GLB's --forward axis (Blender coords; Kenney blasters point +Y) becomes Minecraft
     -Z (north, the muzzle direction used by tools/models/gunkit.py), Blender +Z becomes +Y.
  2. Scale so the model's length along Z is --length model units (1 unit = 1 in-game pixel = 1/16
     block); voxels are 1/--res units (res 1 = one voxel per pixel, 2 = half-pixel voxels).
  3. Solid test per voxel: 2x2x2 sub-samples, each tested with mathutils BVHTree rays along X, Y and
     Z using a winding count (entering faces +1, leaving -1; robust to overlapping closed parts),
     majority of the three axes; a voxel is solid when >= --threshold of its sub-samples are.
  4. Colour: every solid voxel samples the palette at the UV of the nearest surface points of its
     sub-samples; colours are reduced to --colors with k-means. If >= 2 samples glow (white in
     --emit-mask) the voxel's SIDE faces get the brightened accent (thin energy strips survive) while
     its up/down faces keep the metal colour. Enclosed voxels are wildcards (any colour).
  5. Lone metal voxels are despeckled; greedy merge: same-colour voxels become boxes (best of 6
     axis orders); if there are more than --max-elements boxes the colour count is lowered (down to
     4) and it retries. Faces whose every neighbouring cell is solid are dropped. Skin: one 4x4 swatch per colour in a --skin-size PNG.
  6. Placement/display: grip found as the rearmost low-hanging part that is not the stock (or
     --grip zfrac,yfrac of the bounds: 0 = muzzle / bottom), then gunkit's placement rule (x centred,
     grip as near ANCHOR as 0..16 allows) and gunkit.display() transforms.
"""
import json
import math
import sys
from itertools import permutations
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "models"))
import gunkit  # noqa: E402

import bpy  # noqa: E402
import numpy as np  # noqa: E402
from mathutils import Vector  # noqa: E402
from mathutils.bvhtree import BVHTree  # noqa: E402


def parse(argv):
	import argparse
	ap = argparse.ArgumentParser(prog="mc_model.py")
	ap.add_argument("glb")
	ap.add_argument("--id", required=True)
	ap.add_argument("--length", type=float, required=True)
	ap.add_argument("--palette", required=True)
	ap.add_argument("--emit-mask")
	ap.add_argument("--model-out", required=True)
	ap.add_argument("--skin-out", required=True)
	ap.add_argument("--namespace", default="ashfall")
	ap.add_argument("--res", type=float, default=2)
	ap.add_argument("--colors", type=int, default=9)
	ap.add_argument("--threshold", type=float, default=0.4)
	ap.add_argument("--forward", default="+y")
	ap.add_argument("--grip")
	ap.add_argument("--glow-boost", type=float, default=0.15)
	ap.add_argument("--accent", help="#rrggbb for glowing voxels (default: mean glowing palette texel)")
	ap.add_argument("--skin-size", type=int, default=32)
	ap.add_argument("--max-elements", type=int, default=140)
	return ap.parse_args(argv)


# ---------------------------------------------------------------- geometry
def load_triangles(path, forward):
	"""World-space triangles in Minecraft axes (x right, y up, z = backwards), with UVs."""
	common.reset_scene()
	objs = common.import_glb(path)
	dg = bpy.context.evaluated_depsgraph_get()
	f = {"+y": (0, 1), "-y": (0, -1), "+x": (1, 0), "-x": (-1, 0)}[forward]
	verts, tris, uvs = [], [], []
	for o in objs:
		ev = o.evaluated_get(dg)
		me = ev.to_mesh()
		me.calc_loop_triangles()
		mw = o.matrix_world
		uvl = me.uv_layers.active.data if me.uv_layers.active else None
		base = len(verts)
		for v in me.vertices:
			p = mw @ v.co
			# forward (fx, fy) in Blender XY -> MC -Z; right-hand: MC x = forward x up
			fwd = p.x * f[0] + p.y * f[1]
			side = p.x * f[1] - p.y * f[0]
			verts.append((side, p.z, -fwd))
		for lt in me.loop_triangles:
			tris.append(tuple(base + i for i in lt.vertices))
			uvs.append([tuple(uvl[li].uv) if uvl else (0.5, 0.5) for li in lt.loops])
		ev.to_mesh_clear()
	return np.array(verts, float), tris, uvs


def winding_axis(bvh, lo, hi, axis, coords):
	"""Inside flags for points on a grid: coords = (xs, ys, zs) centre arrays; rays along axis."""
	xs, ys, zs = coords
	shape = (len(xs), len(ys), len(zs))
	inside = np.zeros(shape, bool)
	axes = [xs, ys, zs]
	o1, o2 = [k for k in range(3) if k != axis]
	d = Vector((0, 0, 0))
	d[axis] = 1.0
	start = lo[axis] - 1.0
	span = hi[axis] - lo[axis] + 2.0
	along = axes[axis]
	for i, a in enumerate(axes[o1]):
		for j, b in enumerate(axes[o2]):
			org = Vector((0, 0, 0))
			org[o1], org[o2], org[axis] = a, b, start
			events = []
			travelled = 0.0
			while travelled < span:
				loc, nrm, idx, dist = bvh.ray_cast(org, d, span - travelled)
				if loc is None:
					break
				events.append((loc[axis], -1 if nrm.dot(d) > 0 else 1))
				org = loc + d * 1e-4
				travelled = org[axis] - start
			if not events:
				continue
			w, k = 0, 0
			events.sort()
			for n, c in enumerate(along):
				while k < len(events) and events[k][0] < c:
					w += events[k][1]
					k += 1
				if w > 0:
					idx3 = [0, 0, 0]
					idx3[o1], idx3[o2], idx3[axis] = i, j, n
					inside[tuple(idx3)] = True
	return inside


def voxelize(verts, tris, args):
	lo, hi = verts.min(0), verts.max(0)
	scale = args.length / (hi[2] - lo[2]) * args.res  # model -> voxel units
	v = (verts - lo) * scale
	dims = np.ceil(v.max(0) - 1e-6).astype(int)
	dims = np.maximum(dims, 1)
	bvh = BVHTree.FromPolygons([Vector(p) for p in v], tris, all_triangles=True)
	ss = 2  # sub-samples per voxel axis
	coords = [(np.arange(dims[k] * ss) + 0.5) / ss for k in range(3)]
	lo3, hi3 = np.zeros(3), v.max(0)
	votes = sum(winding_axis(bvh, lo3, hi3, ax, coords).astype(int) for ax in range(3))
	sub = votes >= 2
	occ = sub.reshape(dims[0], ss, dims[1], ss, dims[2], ss).mean(axis=(1, 3, 5))
	solid = occ >= args.threshold
	return solid, bvh, v, scale, lo, sub, ss


# ---------------------------------------------------------------- colour
def sample_colours(solid, bvh, v, tris, uvs, pal, emit, sub, ss):
	ph, pw = pal.shape[:2]
	res = {}
	for (x, y, z) in zip(*np.nonzero(solid)):
		cols, glows = [], 0
		for dx in range(ss):
			for dy in range(ss):
				for dz in range(ss):
					p = Vector(((x * ss + dx + 0.5) / ss, (y * ss + dy + 0.5) / ss, (z * ss + dz + 0.5) / ss))
					loc, nrm, idx, dist = bvh.find_nearest(p)
					if idx is None:
						continue
					a, b, c = (Vector(v[i]) for i in tris[idx])
					w = barycentric(loc, a, b, c)
					uv = [sum(w[k] * uvs[idx][k][m] for k in range(3)) for m in range(2)]
					px = min(pw - 1, max(0, int(uv[0] % 1.0 * pw)))
					py = min(ph - 1, max(0, int((1 - uv[1] % 1.0) * ph)))
					if emit is not None and emit[py, px, 0] > 127:
						glows += 1
					else:
						cols.append(pal[py, px, :3].astype(float))
		res[(x, y, z)] = (np.mean(cols, axis=0) if cols else None, glows)
	return res


def barycentric(p, a, b, c):
	v0, v1, v2 = b - a, c - a, p - a
	d00, d01, d11 = v0.dot(v0), v0.dot(v1), v1.dot(v1)
	d20, d21 = v2.dot(v0), v2.dot(v1)
	den = d00 * d11 - d01 * d01
	if abs(den) < 1e-12:
		return (1.0, 0.0, 0.0)
	bv = (d11 * d20 - d01 * d21) / den
	cw = (d00 * d21 - d01 * d20) / den
	return (1 - bv - cw, bv, cw)


def kmeans(points, k, iters=30):
	pts = np.array(points, float)
	uniq = np.unique(np.round(pts), axis=0)
	if len(uniq) <= k:
		cent = uniq
	else:
		# farthest-point init (deterministic)
		cent = [pts[np.argmin(pts.sum(1))]]
		for _ in range(k - 1):
			d = np.min([((pts - c) ** 2).sum(1) for c in cent], axis=0)
			cent.append(pts[np.argmax(d)])
		cent = np.array(cent)
		for _ in range(iters):
			lab = np.argmin(((pts[:, None] - cent[None]) ** 2).sum(2), axis=1)
			new = np.array([pts[lab == i].mean(0) if np.any(lab == i) else cent[i] for i in range(len(cent))])
			if np.allclose(new, cent):
				break
			cent = new
	lab = np.argmin(((pts[:, None] - cent[None]) ** 2).sum(2), axis=1)
	return np.round(cent).astype(int), lab


# ---------------------------------------------------------------- boxes
def neighbours_solid(solid, x, y, z):
	X, Y, Z = solid.shape
	for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
		a, b, c = x + dx, y + dy, z + dz
		if not (0 <= a < X and 0 <= b < Y and 0 <= c < Z) or not solid[a, b, c]:
			return False
	return True


def greedy(colour, order):
	"""colour: int grid, -1 empty, -2 wildcard (enclosed). Returns boxes (lo, hi exclusive, colour)."""
	X = colour.shape
	done = np.zeros(X, bool)
	boxes = []
	ax0, ax1, ax2 = order  # extend along ax0 first, then ax1, then ax2

	def ok(sl, c):
		reg = colour[sl]
		return not done[sl].any() and np.all((reg == c) | (reg == -2))

	it = [range(X[a]) for a in (ax2, ax1, ax0)]
	for i2 in it[0]:
		for i1 in it[1]:
			for i0 in it[2]:
				p = [0, 0, 0]
				p[ax0], p[ax1], p[ax2] = i0, i1, i2
				c = colour[tuple(p)]
				if c < 0 or done[tuple(p)]:
					continue
				lo, hi = list(p), [q + 1 for q in p]
				for ax in (ax0, ax1, ax2):
					while hi[ax] < X[ax]:
						h2 = list(hi)
						l2 = list(lo)
						l2[ax], h2[ax] = hi[ax], hi[ax] + 1
						sl = tuple(slice(l2[k], h2[k]) for k in range(3))
						if not ok(sl, c):
							break
						hi[ax] += 1
				sl = tuple(slice(lo[k], hi[k]) for k in range(3))
				done[sl] = True
				boxes.append((lo, hi, int(c)))
	return boxes


def despeckle(grid, cap, passes=2):
	"""Recolour lone exposed voxels (no neighbour shares their colour) to the colour most of their
	exposed neighbours have (>= 3), metal only (glowing voxels and glow targets are left alone)."""
	X, Y, Z = grid.shape
	offs = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
	for _ in range(passes):
		changes = []
		for x, y, z in np.argwhere(grid >= 0):
			c = grid[x, y, z]
			if c // cap != c % cap:
				continue
			nb = []
			for dx, dy, dz in offs:
				a, b, e = x + dx, y + dy, z + dz
				if 0 <= a < X and 0 <= b < Y and 0 <= e < Z and grid[a, b, e] >= 0:
					nb.append(int(grid[a, b, e]))
			if c in nb or not nb:
				continue
			best = max(set(nb), key=nb.count)
			if nb.count(best) >= 3 and best // cap == best % cap:
				changes.append(((x, y, z), best))
		for key, val in changes:
			grid[key] = val
		if not changes:
			break


FACES = {  # face name: (axis, side)  MC: north = -Z, south = +Z, west = -X, east = +X
	"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1),
}


def face_hidden(solid, lo, hi, axis, side):
	n = hi[axis] if side else lo[axis] - 1
	if n < 0 or n >= solid.shape[axis]:
		return False
	sl = [slice(lo[k], hi[k]) for k in range(3)]
	sl[axis] = slice(n, n + 1)
	return bool(solid[tuple(sl)].all())


# ---------------------------------------------------------------- grip
def find_grip(solid, override):
	X, Y, Z = solid.shape
	pts = np.argwhere(solid).astype(float) + 0.5
	if override:
		zf, yf = override
		z = zf * Z
		y = yf * Y
		near = pts[np.abs(pts[:, 2] - z) < max(1.0, Z * 0.06)]
		x = near[:, 0].mean() if len(near) else X / 2
		return np.array([x, y, z])
	ymin = pts[:, 1].min()
	H = pts[:, 1].max() - ymin
	low = solid.copy()
	low[:, int(math.ceil(ymin + 0.42 * H)):, :] = False
	zs = np.nonzero(low.any(axis=(0, 1)))[0]
	clusters, cur = [], []
	for z in zs:
		if cur and z != cur[-1] + 1:
			clusters.append(cur)
			cur = []
		cur.append(z)
	if cur:
		clusters.append(cur)
	# drop a stock: a cluster that reaches the rear end, if any other remains
	cand = [c for c in clusters if c[-1] < Z - 1 - 0.02 * Z] or clusters
	grip = cand[-1]  # rearmost (muzzle at z=0)
	sel = pts[(pts[:, 2] >= grip[0]) & (pts[:, 2] <= grip[-1] + 1)]
	sel = sel[sel[:, 1] < ymin + 0.42 * H]
	return sel.mean(0)


# ---------------------------------------------------------------- main
def main():
	args = parse(common.script_args())
	verts, tris, uvs = load_triangles(args.glb, args.forward)
	solid, bvh, v, scale, lo, sub, ss = voxelize(verts, tris, args)
	pal = common.read_png(args.palette)
	emit = common.read_png(args.emit_mask) if args.emit_mask else None
	samples = sample_colours(solid, bvh, v, tris, uvs, pal, emit, sub, ss)

	# glow decision, then k-means on the rest; enclosed voxels become wildcards. Each voxel carries a
	# (side colour, cap colour) pair packed as side * CAP + cap: glowing voxels glow on their side
	# faces only, their up/down faces keep the metal colour, so energy strips read as strips.
	CAP = 256
	colour = np.full(solid.shape, -1, int)
	keys, pts, glow = [], [], []
	for key, (col, glows) in samples.items():
		if neighbours_solid(solid, *key):
			colour[key] = -2
			continue
		keys.append(key)
		pts.append(col if col is not None else np.array([60, 60, 66.0]))
		glow.append(glows >= 2 or col is None)
	if any(glow):
		if args.accent:
			acc = np.array(common.hex_rgb(args.accent), float)
		else:  # mean glowing texel of the palette = the accent
			acc = pal[emit[..., 0] > 127][:, :3].astype(float).mean(0)
		glow_rgb = tuple(int(c) for c in np.clip(acc + (255 - acc) * args.glow_boost, 0, 255))

	def assign(k):
		cent, lab = kmeans(pts, k)
		palette = [tuple(int(c) for c in cc) for cc in cent]
		gi = None
		if any(glow):
			palette.append(glow_rgb)
			gi = len(palette) - 1
		grid = colour.copy()
		for key, l, gl in zip(keys, lab, glow):
			grid[key] = (gi if gl else l) * CAP + l
		despeckle(grid, CAP)
		best = None
		for order in permutations(range(3)):
			b = greedy(grid, order)
			if best is None or len(b) < len(best):
				best = b
		return palette, best

	# fewer colours until the (pre-culling) box count fits the element budget
	k = args.colors
	palette, boxes = assign(k)
	while len(boxes) > args.max_elements and k > 4:
		k -= 1
		palette, boxes = assign(k)

	# skin: 4x4 swatch per colour
	S = args.skin_size
	per_row = S // 4
	assert len(palette) <= per_row * per_row, "too many colours for the skin"
	skin = np.zeros((S, S, 4), np.uint8)
	uvunit = 16 / S
	sw_uv = []
	for i, c in enumerate(palette):
		r, q = divmod(i, per_row)
		skin[r * 4:r * 4 + 4, q * 4:q * 4 + 4, :3] = c
		skin[r * 4:r * 4 + 4, q * 4:q * 4 + 4, 3] = 255
		u0, v0 = q * 4 * uvunit, r * 4 * uvunit
		sw_uv.append([round(u0 + uvunit, 4), round(v0 + uvunit, 4), round(u0 + 3 * uvunit, 4), round(v0 + 3 * uvunit, 4)])

	# grip & placement (gunkit rule), in model units
	vox = 1.0 / args.res
	gv = find_grip(solid, [float(t) for t in args.grip.split(",")] if args.grip else None)
	size = np.array(solid.shape) * vox
	g = gv * vox
	d = []
	for k in range(3):
		want = (8 - size[0] / 2) if k == 0 else gunkit.ANCHOR[k] - g[k]
		want = max(0.0, min(16 - size[k], want))
		d.append(want)
	# snap the offset so voxel faces land on a 1/16-of-a-pixel grid
	d = np.round(np.array(d) * 16) / 16
	grip = tuple(g + d)

	elements = []
	for lo_, hi_, c in boxes:
		faces = {}
		for name, (axis, side) in FACES.items():
			if face_hidden(solid, lo_, hi_, axis, side):
				continue
			ci = c % CAP if axis == 1 else c // CAP
			faces[name] = {"uv": sw_uv[ci], "texture": "#skin"}
		if not faces:
			continue
		f = [round(lo_[k] * vox + d[k], 4) for k in range(3)]
		t = [round(hi_[k] * vox + d[k], 4) for k in range(3)]
		elements.append({"from": f, "to": t, "faces": faces})

	ns = args.namespace
	model = {
		"credit": "Ashfall; geometry voxelized from Kenney Blaster Kit (CC0) by tools/art/blender/mc_model.py",
		"textures": {"skin": f"{ns}:item/{args.id}_skin", "particle": f"{ns}:item/{args.id}_icon"},
		"gui_light": "front",
		"elements": elements,
		"display": gunkit.display(grip, (tuple(d), tuple(d + size))),
	}
	Path(args.model_out).parent.mkdir(parents=True, exist_ok=True)
	Path(args.model_out).write_text(json.dumps(model, indent="\t") + "\n")
	common.write_png(args.skin_out, skin)
	print(f"{args.id}: grid {solid.shape} solid {int(solid.sum())} colours {len(palette)} "
		f"elements {len(elements)} grip {tuple(round(c, 2) for c in grip)}")
	if len(elements) > args.max_elements:
		print(f"WARNING {args.id}: {len(elements)} elements > {args.max_elements}")


if __name__ == "__main__":
	main()
