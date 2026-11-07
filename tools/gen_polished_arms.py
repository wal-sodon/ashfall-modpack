#!/usr/bin/env python3
"""Ashfall Polished Arms: an optional resource pack that repaints the energy weapons as machined metal (stdlib only).

The default art is scrap: sheet metal, rust, duct tape and zip ties. This tool reads each weapon's current skin and
model and repaints every face at twice the resolution, in the same UV layout, so the pack only replaces textures and
every model, UV and glow stays as the base mod has it:

  * metal faces get a bevel highlight on the top edge and a shadow on the bottom, a specular band a third of the way
    down the sides, brushed grain along the part's long axis and bright wear on the edges;
  * rivets (a pixel brighter than its neighbours) become domed screws, seams and vents (darker) become grooves lit
    from above;
  * rust becomes the face's own metal, duct tape becomes a knurled rubber grip and zip ties steel clamps;
  * coloured parts (composite, polymer, ceramic, copper, the Flamethrower's tank) keep their colour with a softer
    sheen, and glowing parts (light_emission) keep their colour with a brighter core.

Roles come from the palettes in each weapon's tools/models/<id>.py (RUST, TAPE, TIE...), matched by exact colour;
anything else is judged by saturation. Flat icons (and the Arc Charge) are repainted at 2x with the light from the
top left. Output: pack/overrides/resourcepacks/Ashfall Polished Arms/ (shipped in the mrpack, off by default).

Usage: python3 -I tools/gen_polished_arms.py [--only <id>...] [--preview <dir>]
"""
import argparse
import colorsys
import importlib.util
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
sys.path.insert(0, str(ROOT / "tools" / "models"))
from sprite import Sprite  # noqa: E402
from preview import read_png  # noqa: E402

ASSETS = ROOT / "modules/arms/src/main/resources/assets/ashfall"
PACK = ROOT / "pack/overrides/resourcepacks/Ashfall Polished Arms"
CLIENT_JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-client.jar"
SCALE = 2

# weapon id -> its model script (palettes) ; models are models/item/<id>*.json
WEAPONS = {
	"spark_pistol": "spark_pistol", "volt_sidearm": "volt_sidearm", "arc_magnum": "arc_magnum",
	"pulse_carbine": "pulse_carbine", "storm_repeater": "storm_repeater", "ion_rifle": "ion_rifle",
	"lancer_longrifle": "lancer_longrifle", "flamethrower": "flamethrower", "plasma_blade": "plasma_blade",
	"resonant_plasma_blade": "plasma_blade", "arc_charge": None,
}
# flat items shaped like a canister, shaded as a cylinder
ROUND = {"arc_charge"}
# palette constant name -> role
ROLES = {
	"RUST": "rust", "TAPE": "rubber", "RUB": "rubber", "RUBBER": "rubber", "GRIP": "rubber", "TIE": "clamp",
	"CU": "copper", "COPPER": "copper", "COMP": "matte", "COMPOSITE": "matte", "SHELL": "matte", "POLY": "matte",
	"SOOT": "matte", "TANK": "matte", "CELL": "matte",
}
WARM = (255, 249, 236)
# how far bare metal is pushed toward dark gunmetal before shading (0 keeps the base skin's tone)
DARKEN = 0.28
COOL = (14, 16, 26)


# ---------------------------------------------------------------- colour helpers
def mix(c, t, a):
	a = max(0.0, min(1.0, a))
	return tuple(round(c[k] + (t[k] - c[k]) * a) for k in range(3)) + (c[3] if len(c) > 3 else 255,)


def shade(c, a):
	"""Positive a lights toward warm white, negative darkens toward cool shadow."""
	return mix(c, WARM, a) if a >= 0 else mix(c, COOL, -a)


def lum(c):
	return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def sat(c):
	return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)[1]


def h32(*v):
	"""Deterministic hash of ints to [0, 1)."""
	x = 2166136261
	for n in v:
		x = ((x ^ (n & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
	x ^= x >> 13
	x = (x * 0x5bd1e995) & 0xFFFFFFFF
	x ^= x >> 15
	return x / 2 ** 32


def roles_for(script):
	"""Exact colour -> role from a model script's palette constants."""
	if not script:
		return {}
	path = ROOT / "tools/models" / f"{script}.py"
	spec = importlib.util.spec_from_file_location(f"polish_{script}", path)
	mod = importlib.util.module_from_spec(spec)
	spec.loader.exec_module(mod)
	out = {}
	for name, role in ROLES.items():
		val = getattr(mod, name, None)
		cols = val if isinstance(val, list) else [val] if isinstance(val, tuple) else []
		for c in cols:
			if isinstance(c, tuple) and len(c) >= 3:
				out[tuple(c[:3])] = role
	return out


def role_of(c, roles, glow):
	if glow:
		return "energy"
	r = roles.get(tuple(c[:3]))
	if r:
		return r
	return "matte" if sat(c) > 0.35 else "metal"


# ---------------------------------------------------------------- material painters (one output pixel)
def metal(c, face, i, j, w, h, seed, strength=1.0, grain=1.0):
	"""Polished metal: a smooth light-to-dark sweep down each side with one sharp highlight, faint brushed lines
	and a crisp bevel."""
	a = {"up": 0.10, "down": -0.26, "north": -0.08, "south": -0.08}.get(face, 0.0)
	if face not in ("up", "down") and h >= 4:
		v = (j + 0.5) / h
		a += strength * (0.16 - 0.36 * v)                                   # sweep: lit top, shadowed underside
		a += 0.30 * strength * max(0.0, 1 - abs(v - 0.24) / 0.07)          # sharp specular line
	elif face == "up" and w >= 4 and h >= 4:
		u = (i + 0.5) / w
		a += strength * (0.10 - 0.16 * u) + 0.16 * strength * max(0.0, 1 - abs(u - 0.3) / 0.08)
	long_rows = w >= h if face != "up" else True
	line = j if long_rows else i
	a += grain * (h32(seed, line) - 0.5) * 0.05                              # brushed lines
	if w >= 4 and h >= 4:
		if j == 0:
			a += 0.36
		elif j == h - 1:
			a -= 0.32
		elif i == 0:
			a += 0.10
		elif i == w - 1:
			a -= 0.12
	return shade(c, a)


def rubber(c, i, j, w, h, seed):
	base = mix((46, 45, 50, 255), c, 0.15)
	if (i + j) % 3 == 0:
		base = shade(base, 0.12)                                             # knurl diamonds
	elif (i - j) % 3 == 0:
		base = shade(base, -0.25)
	if j == 0:
		base = shade(base, 0.18)
	elif j == h - 1:
		base = shade(base, -0.3)
	return base


def energy(c, i, j, w, h):
	cu = abs((i + 0.5) / w - 0.5) * 2
	cv = abs((j + 0.5) / h - 0.5) * 2
	core = max(0.0, 1 - max(cu, cv))
	return mix(c, (255, 255, 255), 0.35 * core * core)


# ---------------------------------------------------------------- detail from the base pixel's neighbours
def detail(src, x, y, rect):
	"""+1 for a rivet (brighter than its in-face neighbours), -1 for a seam or vent (darker), else 0."""
	x0, y0, x1, y1 = rect
	if not (x0 < x < x1 - 1 and y0 < y < y1 - 1):
		return 0
	lp = lum(src[y][x])
	n = [lum(src[y + dy][x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
	d = lp - sum(n) / 4
	if sat(src[y][x]) > 0.3 or any(sat(src[y + dy][x + dx]) > 0.3 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
		return 0
	return 1 if d > 0.16 else -1 if d < -0.16 else 0


SCREW = ((0.45, 0.2), (0.05, -0.3))       # 2x2 domed screw head, lit from the top left
GROOVE = ((-0.45, -0.45), (0.1, 0.1))       # recess: shadowed top, catch-light at the bottom


# ---------------------------------------------------------------- skins (3D models)
def face_rects(models):
	"""(x0, y0, x1, y1) base-pixel rect, face name, emissive, seed for every face using #skin."""
	out = []
	for mi, model in enumerate(models):
		for ei, el in enumerate(model.get("elements", [])):
			glow = el.get("light_emission", 0) > 0
			for face, f in el.get("faces", {}).items():
				if f.get("texture") != "#skin" or "uv" not in f:
					continue
				u0, v0, u1, v1 = f["uv"]
				out.append(((min(u0, u1), min(v0, v1), max(u0, u1), max(v0, v1)), face, glow, mi * 1000 + ei * 8 + len(face)))
	return out


def smooth(src, x, y, dx, dy, rect, c):
	"""Base colour at sub-pixel (dx, dy) of base pixel (x, y): blended with the in-face neighbour toward that corner when
	the two are close in tone, so flat metal loses its pixel steps but seams and edges stay sharp."""
	x0, y0, x1, y1 = rect
	nx, ny = x + (1 if dx else -1), y + (1 if dy else -1)
	acc, n = [c[0], c[1], c[2]], 1.0
	for qx, qy, wt in ((nx, y, 0.25), (x, ny, 0.25), (nx, ny, 0.12)):
		if x0 <= qx < x1 and y0 <= qy < y1:
			q = src[qy][qx]
			if q[3] and abs(lum(q) - lum(c)) < 0.09 and abs(sat(q) - sat(c)) < 0.15:
				for k in range(3):
					acc[k] += q[k] * wt
				n += wt
	return tuple(round(v / n) for v in acc) + (255,)


def polish_skin(src_png, models, roles):
	w, h, src = read_png(src_png)
	k = w / 16
	out = Sprite(w * SCALE, h * SCALE)
	for y in range(h):                      # unused gutter pixels: plain 2x copy
		for x in range(w):
			for dy in range(SCALE):
				for dx in range(SCALE):
					out.set(x * SCALE + dx, y * SCALE + dy, src[y][x])
	for (u0, v0, u1, v1), face, glow, seed in face_rects(models):
		rect = (round(u0 * k), round(v0 * k), round(u1 * k), round(v1 * k))
		x0, y0, x1, y1 = rect
		if x1 <= x0 or y1 <= y0:
			continue
		cols = [src[y][x] for y in range(y0, y1) for x in range(x0, x1)]
		plain = [c for c in cols if roles.get(tuple(c[:3])) != "rust" and c[3] > 0]
		mode = max(set(plain), key=plain.count) if plain else (110, 116, 128, 255)
		ow, oh = (x1 - x0) * SCALE, (y1 - y0) * SCALE
		for y in range(y0, y1):
			for x in range(x0, x1):
				c = src[y][x]
				if c[3] == 0:
					continue
				role = role_of(c, roles, glow)
				if role == "rust":
					c, role = mode, "metal"
				base = c
				d = detail(src, x, y, rect) if role == "metal" else 0
				for dy in range(SCALE):
					for dx in range(SCALE):
						i, j = (x - x0) * SCALE + dx, (y - y0) * SCALE + dy
						if role in ("metal", "matte", "copper"):
							c = smooth(src, x, y, dx, dy, rect, base)
						if role == "energy":
							p = energy(c, i, j, ow, oh)
						elif role == "rubber":
							p = rubber(c, i, j, ow, oh, seed)
						elif role == "clamp":
							p = metal((118, 122, 132, 255), face, i, j, ow, oh, seed, 1.2, 0.3)
						elif role == "copper":
							p = metal(c, face, i, j, ow, oh, seed, 1.3, 0.4)
						elif role == "matte":
							p = metal(c, face, i, j, ow, oh, seed, 0.45, 0.25)
						else:
							p = metal(shade(c, -DARKEN), face, i, j, ow, oh, seed)
						if d:
							p = shade(p, (SCREW if d > 0 else GROOVE)[dy][dx])
						out.set(x0 * SCALE + i, y0 * SCALE + j, p)
	return out


# ---------------------------------------------------------------- flat icons
def polish_icon(src_png, roles, round_body=False):
	w, h, src = read_png(src_png)
	out = Sprite(w * SCALE, h * SCALE)

	def empty(x, y):
		return not (0 <= x < w and 0 <= y < h) or src[y][x][3] == 0

	def is_outline(x, y):
		"""A dark pixel on the sprite's rim; dark pixels inside (a near-black body) are shaded like the rest."""
		return lum(src[y][x]) < 0.12 and any(empty(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

	def clear(x, y):
		return empty(x, y) or is_outline(x, y)

	spans = {}
	for y in range(h):
		xs = [x for x in range(w) if src[y][x][3] and not is_outline(x, y)]
		spans[y] = (min(xs), max(xs) + 1) if xs else (0, 1)

	for y in range(h):
		for x in range(w):
			c = src[y][x]
			if c[3] == 0:
				continue
			outline = is_outline(x, y)
			role = roles.get(tuple(c[:3])) or ("matte" if sat(c) > 0.35 else "metal")
			d = 0  # icons are too small for rivet detection: it reads ordinary shading as screws
			for dy in range(SCALE):
				for dx in range(SCALE):
					p = c
					if not outline:
						if role == "rubber":
							p = rubber(c, x * SCALE + dx, y * SCALE + dy, w * SCALE, h * SCALE, 0)
						else:
							s = 1.0 if role in ("metal", "copper", "clamp") else 0.5
							if role == "metal" and lum(src[y][x]) > 0.2:
								c = shade(src[y][x], -DARKEN)          # already-dark metal stays readable
							a = (h32(9, y * SCALE + dy) - 0.5) * 0.06 * s
							if dy == 0 and clear(x, y - 1):
								a += 0.32 * s
							if dy == SCALE - 1 and clear(x, y + 1):
								a -= 0.28
							if dx == 0 and clear(x - 1, y):
								a += 0.12 * s
							if dx == SCALE - 1 and clear(x + 1, y):
								a -= 0.12
							if round_body:                       # a canister: lit down the left third, rolling into shadow
								x0, x1 = spans[y]
								u = ((x - x0) * SCALE + dx + 0.5) / ((x1 - x0) * SCALE)
								a += 0.42 * max(0.0, 1 - abs(u - 0.3) / 0.12) + 0.12 - 0.45 * u
							p = shade(c, a)
						if d:
							p = shade(p, (SCREW if d > 0 else GROOVE)[dy][dx])
					out.set(x * SCALE + dx, y * SCALE + dy, p)
	return out


# ---------------------------------------------------------------- weapon -> textures
def load(name):
	return json.loads((ASSETS / "models/item" / f"{name}.json").read_text())


def tex_name(ref):
	t = ref.split(":", 1)[-1]
	return t[5:] if t.startswith("item/") else t


def jobs(wid):
	"""(texture name, kind, models using it as #skin) for one weapon."""
	names = sorted(p.stem for p in (ASSETS / "models/item").glob(f"{wid}*.json")
				   if p.stem == wid or p.stem.startswith(wid + "_"))
	if wid == "plasma_blade":
		names = [n for n in names if not n.startswith("plasma_blade_x")]
	skins, flats = {}, set()
	for n in names:
		m = load(n)
		for key, ref in m.get("textures", {}).items():
			t = tex_name(ref)
			if key == "skin":
				skins.setdefault(t, []).append(m)
			elif key in ("layer0", "particle") and (ASSETS / "textures/item" / f"{t}.png").exists():
				flats.add(t)
	flats -= set(skins)
	return [(t, "skin", ms) for t, ms in sorted(skins.items())] + [(t, "flat", []) for t in sorted(flats)]


def pack_meta():
	fmt = 97
	try:
		with zipfile.ZipFile(CLIENT_JAR) as z:
			fmt = json.loads(z.read("version.json"))["pack_version"]["resource_major"]
	except (OSError, KeyError, ValueError):
		pass
	return {"pack": {"description": "Ashfall: machined-metal skins for the energy weapons, Flamethrower and Arc Charge",
					 "min_format": fmt, "max_format": fmt}}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--only", nargs="*")
	ap.add_argument("--preview", type=Path, help="also render before/after model sheets into this folder")
	args = ap.parse_args()
	tex_out = PACK / "assets/ashfall/textures/item"
	tex_out.mkdir(parents=True, exist_ok=True)
	written = []
	for wid, script in WEAPONS.items():
		if args.only and wid not in args.only:
			continue
		roles = roles_for(script)
		for t, kind, models in jobs(wid):
			src = ASSETS / "textures/item" / f"{t}.png"
			img = polish_skin(src, models, roles) if kind == "skin" else polish_icon(src, roles, wid in ROUND)
			sw, sh, _ = read_png(src)
			assert (img.w, img.h) == (sw * SCALE, sh * SCALE), t
			(tex_out / f"{t}.png").write_bytes(img.png())
			written.append(t)
			print(f"wrote {t}.png ({img.w}x{img.h})")
	(PACK / "pack.mcmeta").write_text(json.dumps(pack_meta(), indent="\t") + "\n")
	icon = tex_out / "spark_pistol_icon.png"
	if icon.exists():
		shutil.copyfile(icon, PACK / "pack.png")
	if args.preview:
		preview(args.preview, [w for w in WEAPONS if not args.only or w in args.only])
	print(f"{len(written)} textures in {PACK}")


def preview(out, ids):
	"""Renders each 3D weapon twice (base, polished) with tools/models/preview.py into out/."""
	out.mkdir(parents=True, exist_ok=True)
	alt = out / "_root/modules/arms/src/main/resources/assets/ashfall/textures/item"
	if alt.exists():
		shutil.rmtree(alt)
	shutil.copytree(ASSETS / "textures/item", alt)
	for p in (PACK / "assets/ashfall/textures/item").glob("*.png"):
		shutil.copyfile(p, alt / p.name)
	for wid in ids:
		model = ASSETS / "models/item" / f"{wid}.json"
		if not model.exists() or "elements" not in json.loads(model.read_text()):
			continue
		for tag, root in (("base", ROOT), ("polished", out / "_root")):
			subprocess.run([sys.executable, "-I", str(ROOT / "tools/models/preview.py"), str(model),
							str(out / f"{wid}_{tag}.png"), "--root", str(root), "--contexts"], check=True,
						   capture_output=True)
		print(f"preview {wid}")


if __name__ == "__main__":
	main()
