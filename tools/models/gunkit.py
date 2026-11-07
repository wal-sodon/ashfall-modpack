"""Shared finishing pass for Ashfall's 3D gun models (stdlib only; not a model script itself).

Every gun script builds its elements in its own way and at its own detail scale. finish() is called
at the end of each script's main() and makes the set consistent:

  * Size: the gun is scaled uniformly (from/to and rotation origins; UVs untouched) so its length
    along Z matches LENGTHS[id]: pistols ~7 units, magnum 9, carbine 12, repeater 13.5, rifle 14,
    longrifle 16, flamethrower 14. One model unit is one in-game pixel at display scale 1.
  * Placement: the gun is centred on x = 8 and its grip is moved as close to ANCHOR as the 0..16
    element bounds allow.
  * Particle: every gun's particle texture is its flat icon.
  * Display: every gun gets the same display transforms (DISPLAY_SCALE etc.), with translations
    worked out from that gun's grip so the grip sits in the fist in every hand context.

Grip: the first element rotated about X by -22.5 degrees (every gun's raked pistol grip), unless a
grip index is passed. The hand point is that element's centre after its rotation.

Third person: ItemInHandLayer maps the item's +Y to the holder's forward and -Z to down, so guns
(muzzle -Z, top +Y) are pitched +90 about X: muzzle -> forward, top -> up. First person: the camera
looks down -Z, so the muzzle already points forward; a small +Y yaw angles it toward the crosshair.
"""
import json
import math
from pathlib import Path

LENGTHS = {
	"spark_pistol": 7.0,
	"volt_sidearm": 7.5,
	"arc_magnum": 9.0,
	"pulse_carbine": 12.0,
	"storm_repeater": 13.5,
	"ion_rifle": 14.0,
	"lancer_longrifle": 16.0,
	"flamethrower": 14.0,
}
ANCHOR = (8.0, 7.0, 10.0)
# Fist position in each context's pre-rotation display frame, relative to the model centre
# (derived from where vanilla item/handheld puts a sword's handle).
FIST_TP = (0.0, -1.5, 1.5)    # third person: x right, y forward, z up
FIST_FP = (1.0, -1.0, -0.5)   # first person: camera space (x right, y up, -z forward)
TP_SCALE = 1.0
FP_SCALE = 0.9
FP_YAW = 4


def _rot_x(v, deg):
	a = math.radians(deg)
	c, s = math.cos(a), math.sin(a)
	return (v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c)


def _r(v):
	return round(v, 4)


def grip_point(el):
	f, t = el["from"], el["to"]
	c = tuple((f[i] + t[i]) / 2 for i in range(3))
	r = el.get("rotation")
	if r:
		assert r["axis"] == "x", "grip rotation must be about x"
		o = r.get("origin", [8, 8, 8])
		d = _rot_x(tuple(c[i] - o[i] for i in range(3)), r["angle"])
		c = tuple(o[i] + d[i] for i in range(3))
	return c


def find_grip(elements):
	for i, el in enumerate(elements):
		r = el.get("rotation")
		if r and r["axis"] == "x" and r["angle"] == -22.5:
			return i
	raise ValueError("no grip element (x-rotated by -22.5) found; pass grip=<index>")


def _bounds(elements):
	pts = []
	for el in elements:
		f, t = el["from"], el["to"]
		corners = [(x, y, z) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
		r = el.get("rotation")
		if r:
			o = r.get("origin", [8, 8, 8])
			a = math.radians(r["angle"])
			c, s = math.cos(a), math.sin(a)
			ax = "xyz".index(r["axis"])
			i, j = [k for k in range(3) if k != ax]
			if ax == 1:
				i, j = j, i  # rotation about y: z,x plane
			out = []
			for p in corners:
				d = [p[k] - o[k] for k in range(3)]
				d[i], d[j] = d[i] * c - d[j] * s, d[i] * s + d[j] * c
				out.append(tuple(o[k] + d[k] for k in range(3)))
			corners = out
		pts += corners
	return [min(p[k] for p in pts) for k in range(3)], [max(p[k] for p in pts) for k in range(3)]


def normalize(elements, length, grip=None):
	"""Scale and move elements in place; returns the final grip point."""
	gi = find_grip(elements) if grip is None else grip
	g = grip_point(elements[gi])
	lo, hi = _bounds(elements)
	s = length / (hi[2] - lo[2])
	# after scaling about g, then translating by d, a point p goes to g + s*(p-g) + d
	nlo = [g[k] + s * (lo[k] - g[k]) for k in range(3)]
	nhi = [g[k] + s * (hi[k] - g[k]) for k in range(3)]
	d = []
	for k in range(3):
		want = (8 - (nlo[k] + nhi[k]) / 2) if k == 0 else ANCHOR[k] - g[k]
		want = max(-nlo[k], min(16 - nhi[k], want))  # keep inside 0..16
		d.append(want)

	def tf(p):
		return [_r(g[k] + s * (p[k] - g[k]) + d[k]) for k in range(3)]

	for el in elements:
		el["from"], el["to"] = tf(el["from"]), tf(el["to"])
		if "rotation" in el:
			el["rotation"]["origin"] = tf(el["rotation"].get("origin", [8, 8, 8]))
	return tuple(g[k] + d[k] for k in range(3))


def display(grip):
	c = tuple(grip[k] - 8 for k in range(3))
	# third person: rotation x+90 maps (x, y, z) -> (x, -z, y)
	rc = (c[0], -c[2], c[1])
	tp_t = [_r(FIST_TP[k] - TP_SCALE * rc[k]) for k in range(3)]
	# first person: yaw about y, then scale
	a = math.radians(FP_YAW)
	yc = (c[0] * math.cos(a) + c[2] * math.sin(a), c[1], -c[0] * math.sin(a) + c[2] * math.cos(a))
	fp_t = [_r(FIST_FP[k] - FP_SCALE * yc[k]) for k in range(3)]
	tps, fps = [TP_SCALE] * 3, [FP_SCALE] * 3
	return {
		"thirdperson_righthand": {"rotation": [90, 0, 0], "translation": tp_t, "scale": tps},
		"thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [-tp_t[0], tp_t[1], tp_t[2]], "scale": tps},
		"firstperson_righthand": {"rotation": [0, FP_YAW, 0], "translation": fp_t, "scale": fps},
		"firstperson_lefthand": {"rotation": [0, -FP_YAW, 0], "translation": [-fp_t[0], fp_t[1], fp_t[2]], "scale": fps},
		# gui/ground/fixed/on_shelf normally use the flat icon (items/<id>.json); these cover any
		# other renderer that asks the 3D model directly
		"gui": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
		"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
		"head": {"rotation": [0, 180, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
	}


def finish(model_path, gun_id, grip=None):
	"""Normalize the model JSON at model_path in place (size, placement, display transforms)."""
	path = Path(model_path)
	model = json.loads(path.read_text())
	g = normalize(model["elements"], LENGTHS[gun_id], grip)
	model["display"] = display(g)
	model.setdefault("textures", {})["particle"] = f"ashfall:item/{gun_id}_icon"  # same particle for every gun
	model["gui_light"] = "front"
	path.write_text(json.dumps(model, indent="\t") + "\n")
	return model
