#!/usr/bin/env python3
"""Software preview of a block-style item model (stdlib only).

Renders the model's cuboid elements with their skin UVs into one PNG of several orthographic views,
so a 3D item model can be checked without launching the game:
  side   - looking at the east face (+X), so a gun pointing north (-Z) has its muzzle on the right
  top    - looking down (+Y), north to the right
  3/4    - yaw/pitch three-quarter view from the upper right-front
  plus one view per display context present among --contexts (default: thirdperson_righthand,
  firstperson_righthand): the model posed by that display transform, then seen from the holder's
  right side (holder's forward points right; up is up). Third-person panels include the hand
  layer's frame change (item +Y -> forward, -Z -> down). Translation is ignored.

Usage: python3 -I tools/models/preview.py <model.json> <out.png> [--scale N] [--root <repo root>]
       [--contexts ctx ...]
Texture references "ns:path" resolve to modules/*/src/main/resources/assets/<ns>/textures/<path>.png.
Supports element rotation (any axis/angle about origin). Face uv rotation is not supported.
"""
import argparse
import json
import math
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite  # noqa: E402

BG = (40, 40, 48, 255)
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


# ---------------------------------------------------------------- PNG reading
def read_png(path):
	data = Path(path).read_bytes()
	assert data[:8] == b"\x89PNG\r\n\x1a\n", path
	pos, idat, w = 8, b"", 0
	while pos < len(data):
		ln, tag = struct.unpack(">I4s", data[pos:pos + 8])
		body = data[pos + 8:pos + 8 + ln]
		if tag == b"IHDR":
			w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
			assert depth == 8 and interlace == 0 and ctype in (2, 6), "only 8-bit RGB/RGBA PNGs"
		elif tag == b"IDAT":
			idat += body
		pos += 12 + ln
	bpp = 4 if ctype == 6 else 3
	raw = zlib.decompress(idat)
	stride = w * bpp
	rows, prev, i = [], bytearray(stride), 0
	for _ in range(h):
		f = raw[i]
		line = bytearray(raw[i + 1:i + 1 + stride])
		i += 1 + stride
		for x in range(stride):
			a = line[x - bpp] if x >= bpp else 0
			b = prev[x]
			c = prev[x - bpp] if x >= bpp else 0
			if f == 1:
				line[x] = (line[x] + a) & 255
			elif f == 2:
				line[x] = (line[x] + b) & 255
			elif f == 3:
				line[x] = (line[x] + (a + b) // 2) & 255
			elif f == 4:
				p = a + b - c
				pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
				line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
		rows.append([tuple(line[x * bpp:x * bpp + bpp]) + ((255,) if bpp == 3 else ()) for x in range(w)])
		prev = line
	return w, h, rows


def resolve_texture(ref, textures, root):
	seen = 0
	while ref.startswith("#") and seen < 8:
		ref = textures[ref[1:]]
		seen += 1
	ns, _, path = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
	for cand in root.glob(f"modules/*/src/main/resources/assets/{ns}/textures/{path}.png"):
		return cand
	raise FileNotFoundError(ref)


# ---------------------------------------------------------------- maths
def mat_mul(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mat_vec(m, v):
	return (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
			m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
			m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2])


def rot(axis, deg):
	c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
	if axis == "x":
		return [[1, 0, 0], [0, c, -s], [0, s, c]]
	if axis == "y":
		return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
	return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def display_matrix(t):
	"""Minecraft ItemTransform: rotation is XYZ euler (R = Rx*Ry*Rz), then scale."""
	rx, ry, rz = t.get("rotation", [0, 0, 0])
	sx, sy, sz = t.get("scale", [1, 1, 1])
	r = mat_mul(mat_mul(rot("x", rx), rot("y", ry)), rot("z", rz))
	return mat_mul(r, [[sx, 0, 0], [0, sy, 0], [0, 0, sz]])


# ---------------------------------------------------------------- faces
def face_frame(name, f, t):
	"""(origin, u-axis vector, v-axis vector) in model units for a face of box f..t, per vanilla UV mapping."""
	x0, y0, z0 = f
	x1, y1, z1 = t
	if name == "north":
		return (x1, y1, z0), (x0 - x1, 0, 0), (0, y0 - y1, 0)
	if name == "south":
		return (x0, y1, z1), (x1 - x0, 0, 0), (0, y0 - y1, 0)
	if name == "east":
		return (x1, y1, z1), (0, 0, z0 - z1), (0, y0 - y1, 0)
	if name == "west":
		return (x0, y1, z0), (0, 0, z1 - z0), (0, y0 - y1, 0)
	if name == "up":
		return (x0, y1, z0), (x1 - x0, 0, 0), (0, 0, z1 - z0)
	return (x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1)  # down


def default_uv(name, f, t):
	x0, y0, z0 = f
	x1, y1, z1 = t
	return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
			"east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0],
			"up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[name]


def load_model(path, root):
	model = json.loads(Path(path).read_text())
	textures = dict(model.get("textures", {}))
	parent = model.get("parent")
	while parent and "elements" not in model:
		ns, _, p = parent.partition(":") if ":" in parent else ("minecraft", "", parent)
		cands = list(root.glob(f"modules/*/src/main/resources/assets/{ns}/models/{p}.json"))
		if not cands:
			break
		pm = json.loads(cands[0].read_text())
		textures = {**pm.get("textures", {}), **textures}
		model = {**pm, **model, "elements": pm.get("elements", model.get("elements"))}
		parent = pm.get("parent")
	return model, textures


def build_quads(model, textures, root):
	cache, quads = {}, []
	for el in model.get("elements", []):
		f, t = el["from"], el["to"]
		r = el.get("rotation")
		if r:
			rm, org = rot(r["axis"], r["angle"]), r.get("origin", [8, 8, 8])
		for name, face in el.get("faces", {}).items():
			tex = resolve_texture(face["texture"], textures, root)
			if tex not in cache:
				cache[tex] = read_png(tex)
			o, du, dv = face_frame(name, f, t)
			uv = face.get("uv") or default_uv(name, f, t)
			pts = [o, tuple(o[i] + du[i] for i in range(3)), tuple(o[i] + dv[i] for i in range(3))]
			if r:
				pts = [tuple(a + b for a, b in zip(mat_vec(rm, tuple(p[i] - org[i] for i in range(3))), org)) for p in pts]
			o2 = pts[0]
			du2 = tuple(pts[1][i] - o2[i] for i in range(3))
			dv2 = tuple(pts[2][i] - o2[i] for i in range(3))
			quads.append((o2, du2, dv2, uv, cache[tex], SHADE[name]))
	return quads


# ---------------------------------------------------------------- render
def render(quads, view, size, scale, center=(8, 8, 8), offset=None, autofit=True):
	"""Orthographic splat render; view maps model-centred coords to (right, up, toward-viewer).

	autofit centres the model's bounding box in the panel; autofit=False keeps the model centre (plus
	offset, a view-space translation such as a display transform's) at the panel centre, so several
	models rendered at one scale can be compared for size and placement."""
	w, h = size
	img = [[BG] * w for _ in range(h)]
	zb = [[-1e9] * w for _ in range(h)]
	tq = []
	for o, du, dv, uv, tex, shade in quads:
		oc = tuple(o[i] - center[i] for i in range(3))
		tq.append((mat_vec(view, oc), mat_vec(view, du), mat_vec(view, dv), uv, tex, shade))
	xs = [p[0] + a * q[0] + b * r[0] for p, q, r, *_ in tq for a in (0, 1) for b in (0, 1)]
	ys = [p[1] + a * q[1] + b * r[1] for p, q, r, *_ in tq for a in (0, 1) for b in (0, 1)]
	cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
	if not autofit:
		cx, cy = (-offset[0], -offset[1]) if offset else (0, 0)
	for o, du, dv, uv, (tw, th, rows), shade in tq:
		lu = math.hypot(du[0], du[1]) * scale
		lv = math.hypot(dv[0], dv[1]) * scale
		nu, nv = max(2, int(lu * 1.6) + 2), max(2, int(lv * 1.6) + 2)
		for i in range(nu):
			s = (i + 0.5) / nu
			for j in range(nv):
				tt = (j + 0.5) / nv
				px = o[0] + s * du[0] + tt * dv[0]
				py = o[1] + s * du[1] + tt * dv[1]
				pz = o[2] + s * du[2] + tt * dv[2]
				sx = int(w / 2 + (px - cx) * scale)
				sy = int(h / 2 - (py - cy) * scale)
				if not (0 <= sx < w and 0 <= sy < h) or pz < zb[sy][sx]:
					continue
				u = uv[0] + s * (uv[2] - uv[0])
				v = uv[1] + tt * (uv[3] - uv[1])
				tx = min(tw - 1, max(0, int(u / 16 * tw)))
				ty = min(th - 1, max(0, int(v / 16 * th)))
				c = rows[ty][tx]
				if c[3] < 8:
					continue
				zb[sy][sx] = pz
				img[sy][sx] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
	return img


# Third person: ItemInHandLayer maps the item's +Y to the holder's forward and its -Z to down
# (arm hanging), i.e. a -90 degree pitch from the display frame into the holder frame. First
# person needs nothing: the camera looks down -Z.
HOLDER_TP = rot("x", -90)

VIEWS = {
	"side": rot("y", -90),                                      # east face toward viewer
	"top": mat_mul(rot("x", 90), rot("y", -90)),                 # looking down, north to the right
	"3/4": mat_mul(rot("x", 25), rot("y", -125)),
}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("model", type=Path)
	ap.add_argument("out", type=Path)
	ap.add_argument("--scale", type=float, default=22)
	ap.add_argument("--root", type=Path, default=ROOT)
	ap.add_argument("--contexts", nargs="*", default=["thirdperson_righthand", "firstperson_righthand"])
	args = ap.parse_args()
	model, textures = load_model(args.model, args.root)
	quads = build_quads(model, textures, args.root)
	panels = [(name, view) for name, view in VIEWS.items()]
	disp = model.get("display", {})
	for ctx in args.contexts:
		if ctx in disp:
			holder = HOLDER_TP if ctx.startswith("thirdperson") else rot("x", 0)
			panels.append((ctx, mat_mul(mat_mul(VIEWS["side"], holder), display_matrix(disp[ctx]))))
	pw = ph = int(args.scale * 20)
	sheet = Sprite.__new__(Sprite)
	pad = 6
	sheet.w, sheet.h = len(panels) * (pw + pad) + pad, ph + 2 * pad
	sheet.px = [[(24, 24, 30, 255)] * sheet.w for _ in range(sheet.h)]
	for k, (name, view) in enumerate(panels):
		img = render(quads, view, (pw, ph), args.scale)
		ox = pad + k * (pw + pad)
		for y in range(ph):
			sheet.px[pad + y][ox:ox + pw] = img[y]
		print(f"panel {k}: {name}")
	args.out.write_bytes(sheet.png_sized(sheet.w, sheet.h))
	print(f"wrote {args.out}")


if __name__ == "__main__":
	main()
