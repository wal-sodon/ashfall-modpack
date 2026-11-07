#!/usr/bin/env python3
"""Generate the survivors' 64x64 player-model skins (stdlib only; Python 3.8).

Survivors (arms survivor/Survivor) are drawn with the vanilla wide player model, so each skin uses the standard
player box-UV layout, base layer plus the outer layer (hat, jacket, sleeves, trouser legs). Every survivor is a
short description below: skin tone, hair, top, trousers, boots and a few extras (beanie, cap, bandana, backpack
straps, hi-vis vest, beard). All original: plain scavenger clothes, no logos, no characters from anything else.

Output: modules/arms/src/main/resources/assets/ashfall/textures/entity/survivor/survivor_<n>.png; the renderer
expects arms Survivor.SKINS of them (8).

Usage: python3 -I tools/textures/survivors.py [--root <repo root>] [--sheet <png>]
"""
import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from sprite import Sprite, rgb  # noqa: E402

TEXTURE_DIR = "modules/arms/src/main/resources/assets/ashfall/textures/entity/survivor"
CLEAR = (0, 0, 0, 0)

# Box UV origins (u, v) and sizes (w, h, d) of the wide player model, base layer then outer layer.
HEAD, HAT = (0, 0), (32, 0)
BODY, JACKET = (16, 16), (16, 32)
R_ARM, R_SLEEVE = (40, 16), (40, 32)
L_ARM, L_SLEEVE = (32, 48), (48, 48)
R_LEG, R_PANTS = (0, 16), (0, 32)
L_LEG, L_PANTS = (16, 48), (0, 48)
HEAD_SIZE = (8, 8, 8)
BODY_SIZE = (8, 12, 4)
LIMB_SIZE = (4, 12, 4)


def h(x, y, salt=0):
	"""Deterministic per-pixel hash in 0..1, so the cloth noise is the same every run."""
	n = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
	n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
	return (n ^ (n >> 16)) / 0xFFFFFFFF


def ramp(hex_base):
	"""Light, base and dark shades of one colour."""
	r, g, b, _ = rgb(hex_base)

	def scale(f):
		return (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))), max(0, min(255, int(b * f))), 255)
	return [scale(1.18), (r, g, b, 255), scale(0.78), scale(0.58)]


def faces(origin, size):
	"""The six faces of a box as name -> (x0, y0, width, height) in the texture."""
	u, v = origin
	w, hgt, d = size
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"right": (u, v + d, d, hgt),
		"front": (u + d, v + d, w, hgt),
		"left": (u + d + w, v + d, d, hgt),
		"back": (u + d + w + d, v + d, w, hgt),
	}


def paint(s, origin, size, colour):
	"""Fills every face of a box with colour(face, x, y, width, height), which may return None to leave it clear."""
	for face, (x0, y0, w, hgt) in faces(origin, size).items():
		for y in range(hgt):
			for x in range(w):
				c = colour(face, x, y, w, hgt)
				if c is not None:
					s.set(x0 + x, y0 + y, c)


def cloth(shades, x, y, salt):
	"""A cloth pixel: mostly the base shade, some darker weave, a few light flecks."""
	r = h(x, y, salt)
	return shades[2] if r > 0.82 else shades[0] if r < 0.06 else shades[1]


class Look:
	"""One survivor's outfit."""

	def __init__(self, skin, hair, eyes, top, trousers, boots, hair_style="short", top_style="tee",
				 extras=(), accent=None, salt=0):
		self.skin, self.hair, self.eyes = ramp(skin), ramp(hair), rgb(eyes)
		self.top, self.trousers, self.boots = ramp(top), ramp(trousers), ramp(boots)
		self.hair_style, self.top_style, self.extras = hair_style, top_style, set(extras)
		self.accent = ramp(accent) if accent else None
		self.salt = salt


def head(s, look):
	sk, hr = look.skin, look.hair
	long_hair = look.hair_style in ("long", "ponytail")

	def colour(face, x, y, w, hgt):
		if face == "top":
			return hr[1] if look.hair_style != "buzz" else hr[2] if h(x, y, look.salt) > 0.4 else sk[1]
		if face == "bottom":
			return sk[2]
		fringe = {"short": 1, "buzz": 0, "long": 2, "ponytail": 1, "curly": 2, "bald": -1}[look.hair_style]
		if face == "front":
			if y <= fringe:
				return hr[1] if (x + y) % 3 else hr[2]
			if look.hair_style in ("long", "curly") and (x == 0 or x == 7) and y <= 5:
				return hr[2]
			c = sk[1]
			if y == 7:
				c = sk[2]  # chin shadow
			if "beard" in look.extras and y >= 5:
				c = hr[2] if (x + y) % 2 else hr[1]
				if y == 6 and 3 <= x <= 4:
					c = rgb("5a3a34")
				return c
			return c
		if face == "back":
			if look.hair_style == "bald":
				return sk[2] if y < 2 else sk[1]
			if look.hair_style == "buzz":
				return hr[2] if y < 5 else sk[1]
			return hr[1] if (x * 3 + y) % 5 else hr[2] if y < (8 if long_hair else 6) else sk[2]
		# sides
		if look.hair_style == "bald":
			return sk[1]
		edge = x >= 5 if face == "right" else x <= 2
		limit = 7 if long_hair else 5 if look.hair_style == "curly" else 2
		if y <= limit and (edge or y <= 1):
			return hr[2] if h(x, y, look.salt + 3) > 0.6 else hr[1]
		return sk[1] if y < 7 else sk[2]

	paint(s, HEAD, HEAD_SIZE, colour)
	fx, fy = 8 + 0, 8 + 0  # front face origin
	# eyes: white then iris, a brow above each
	for ex in (1, 5):
		s.set(fx + ex, fy + 4, rgb("f4f1ea"))
		s.set(fx + ex + 1, fy + 4, look.eyes)
		s.set(fx + ex, fy + 3, hr[2])
		s.set(fx + ex + 1, fy + 3, hr[2])
	if "beard" not in look.extras:
		s.set(fx + 3, fy + 6, sk[3])
		s.set(fx + 4, fy + 6, sk[3])
	s.set(fx + 3, fy + 5, sk[2])  # nose shade
	s.set(fx + 4, fy + 5, sk[2])


def hat(s, look):
	"""Outer head layer: beanie, cap, bandana over the mouth, ponytail or curls."""
	hr = look.hair
	acc = look.accent or look.top

	def colour(face, x, y, w, hgt):
		if "beanie" in look.extras:
			if face == "top":
				return cloth(acc, x, y, look.salt + 11)
			if face != "bottom" and y <= 2:
				return acc[2] if y == 2 else cloth(acc, x, y, look.salt + 13)
		if "cap" in look.extras:
			if face == "top":
				return acc[1]
			if face != "bottom" and y <= 1:
				return acc[2] if y == 1 else acc[1]
		if "bandana" in look.extras and face in ("front", "left", "right", "back") and 5 <= y <= 7:
			if face == "back" and y > 5:
				return None
			return acc[1] if (x + y) % 2 else acc[2]
		if look.hair_style == "ponytail" and face == "back" and 2 <= y <= 7 and 3 <= x <= 4:
			return hr[2] if y % 2 else hr[1]
		if look.hair_style == "curly" and face in ("top", "left", "right", "back") and (face == "top" or y <= 4):
			return hr[1] if h(x, y, look.salt + 17) > 0.35 else None
		return None

	paint(s, HAT, HEAD_SIZE, colour)


def torso(s, look):
	t = look.top

	def base(face, x, y, w, hgt):
		if face in ("top", "bottom"):
			return t[2]
		c = cloth(t, x, y, look.salt + 21)
		if look.top_style == "flannel":
			c = t[1] if (x // 2 + y // 2) % 2 else t[3]
			if x % 4 == 0 or y % 4 == 0:
				c = t[2]
		if look.top_style == "scrubs" and face == "front" and y <= 2 and 3 <= x <= 4:
			return look.skin[1]  # v-neck
		if look.top_style == "tee" and face == "front" and y == 0 and 3 <= x <= 4:
			return look.skin[1]
		if y == hgt - 1:
			return look.trousers[2]  # belt line
		return c

	paint(s, BODY, BODY_SIZE, base)
	jacket(s, look)


def jacket(s, look):
	"""Outer body layer: an open jacket or hoodie, a vest, backpack straps."""
	acc = look.accent

	def colour(face, x, y, w, hgt):
		if face in ("top", "bottom"):
			return None
		c = None
		if acc and look.top_style in ("jacket", "hoodie", "denim", "leather"):
			c = cloth(acc, x, y, look.salt + 31)
			if look.top_style == "leather" and h(x, y, look.salt + 33) > 0.9:
				c = acc[0]  # worn shine
			if look.top_style == "denim" and (x + y) % 5 == 0:
				c = acc[2]
			if face == "front" and 3 <= x <= 4:
				c = None if look.top_style != "hoodie" or y > 1 else acc[2]  # open front, hood strings
			if face == "back" and look.top_style == "hoodie" and y <= 2:
				c = acc[2]  # the hood down on the back
		if acc and look.top_style == "vest":
			if face == "front" and 2 <= x <= 5:
				c = None
			elif face in ("front", "back", "left", "right"):
				c = acc[1]
				if y in (6, 7) and face in ("front", "back"):
					c = rgb("d8dcd8")  # reflective band
		if "straps" in look.extras:
			if face == "front" and x in (1, 6) and y <= 9:
				c = rgb("3a3330")
			if face == "back" and 1 <= x <= 6 and 1 <= y <= 9:
				c = rgb("4a5038") if (x + y) % 4 else rgb("3a3f2c")  # the pack itself
		return c

	paint(s, JACKET, BODY_SIZE, colour)


def arm(s, origin, sleeve_origin, look, left):
	sk = look.skin
	t = look.top
	sleeve_rows = {"tee": 3, "vest": 3, "scrubs": 3}.get(look.top_style, 11)

	def base(face, x, y, w, hgt):
		if face == "top":
			return t[1]
		if face == "bottom" or y == hgt - 1:
			return sk[2]
		if y < sleeve_rows:
			c = cloth(t, x, y, look.salt + (41 if left else 43))
			if look.top_style == "flannel":
				c = t[1] if (x // 2 + y // 2) % 2 else t[3]
			return c
		return sk[1] if y < hgt - 2 else sk[2]

	paint(s, origin, LIMB_SIZE, base)
	acc = look.accent

	def outer(face, x, y, w, hgt):
		if not acc or look.top_style not in ("jacket", "hoodie", "denim", "leather"):
			return None
		if face == "bottom" or y >= hgt - 2:
			return None
		if face == "top":
			return acc[1]
		c = cloth(acc, x, y, look.salt + (51 if left else 53))
		if y == hgt - 3:
			c = acc[2]  # cuff
		return c

	paint(s, sleeve_origin, LIMB_SIZE, outer)


def leg(s, origin, pants_origin, look, left):
	tr, bt = look.trousers, look.boots

	def base(face, x, y, w, hgt):
		if face == "bottom" or y >= hgt - 3:
			return bt[2] if y == hgt - 1 or face == "bottom" else bt[1]
		if face == "top":
			return tr[2]
		c = cloth(tr, x, y, look.salt + (61 if left else 63))
		if face == "front" and y == 5:
			c = tr[2] if h(x, y, look.salt) > 0.5 else c  # knee crease
		return c

	paint(s, origin, LIMB_SIZE, base)

	def outer(face, x, y, w, hgt):
		if "cargo" in look.extras and face in ("left" if left else "right",) and 4 <= y <= 6:
			return tr[2] if y == 4 else tr[1]  # cargo pocket
		if face in ("front", "right", "left", "back") and y == hgt - 4:
			return bt[0] if "boots" in look.extras else None  # boot tops
		return None

	paint(s, pants_origin, LIMB_SIZE, outer)


def draw(look):
	s = Sprite(64, 64)
	head(s, look)
	hat(s, look)
	torso(s, look)
	arm(s, R_ARM, R_SLEEVE, look, False)
	arm(s, L_ARM, L_SLEEVE, look, True)
	leg(s, R_LEG, R_PANTS, look, False)
	leg(s, L_LEG, L_PANTS, look, True)
	return s


# Eight survivors. Index = the saved skin number, so keep the order stable once shipped.
LOOKS = [
	# 0: scavenger in an olive field jacket, backpack, jeans and boots
	Look("c68c62", "3b2a20", "4a6a3a", "8c8478", "3d5470", "4a3a2c", top_style="jacket", accent="5f6a3c",
		 extras=("straps", "boots"), salt=1),
	# 1: long auburn ponytail, grey hoodie, black cargo trousers
	Look("f0c8a8", "8a3c22", "3a6a8a", "6e6e72", "2c2c30", "3a3434", hair_style="ponytail", top_style="hoodie",
		 accent="7a7a80", extras=("cargo",), salt=2),
	# 2: buzz cut, hi-vis vest over a navy work shirt
	Look("6a4430", "1c1614", "3a2a20", "2c3a5a", "4a4a44", "3a2e24", hair_style="buzz", top_style="vest",
		 accent="e0a020", extras=("boots",), salt=3),
	# 3: black beanie, red flannel, brown work trousers
	Look("c8986c", "2a2018", "5a4030", "a03028", "6a5038", "3c2c20", top_style="flannel", accent="23262c",
		 extras=("beanie", "boots"), salt=4),
	# 4: grey-bearded older man in a worn leather jacket and khakis
	Look("e4b494", "9a968e", "4a6070", "c8c0a8", "a89a70", "3c3028", top_style="leather", accent="5c3a24",
		 extras=("beard",), salt=5),
	# 5: curly hair, denim jacket over a mustard tee, dark trousers
	Look("5a3a28", "18120e", "2a1c14", "c8a030", "2e3038", "5a4a3c", hair_style="curly", top_style="denim",
		 accent="4a6890", salt=6),
	# 6: short blond hair, teal hospital scrubs, white trainers
	Look("f4d4bc", "d8b868", "3a5a8a", "3a8a88", "3a8a88", "d8d8d0", top_style="scrubs", extras=("cargo",), salt=7),
	# 7: bandana over the face, camo-green hoodie, grey trousers, a cap
	Look("d8a878", "141414", "2a2018", "6a7048", "5a5c60", "2c2c2c", top_style="hoodie", accent="5a6a40",
		 extras=("bandana", "cap", "straps"), salt=8),
]


def contact_sheet(sprites, scale=6, pad=6):
	n = len(sprites)
	sheet = Sprite.__new__(Sprite)
	width, height = n * (64 * scale + pad) + pad, 64 * scale + 2 * pad
	sheet.w, sheet.h = width, height
	sheet.px = [[(40, 40, 48, 255)] * width for _ in range(height)]
	for k, sprite in enumerate(sprites):
		for y in range(64):
			for x in range(64):
				c = sprite.px[y][x]
				if c[3] == 0:
					continue
				for dy in range(scale):
					for dx in range(scale):
						sheet.px[pad + y * scale + dy][pad + k * (64 * scale + pad) + x * scale + dx] = c
	return sheet.png_sized(width, height)


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=HERE.parent.parent, type=Path)
	ap.add_argument("--sheet", type=Path, help="also write a scaled contact sheet of the skins")
	args = ap.parse_args()
	out = args.root / TEXTURE_DIR
	out.mkdir(parents=True, exist_ok=True)
	drawn = []
	for i, look in enumerate(LOOKS):
		sprite = draw(look)
		(out / "survivor_{}.png".format(i)).write_bytes(sprite.png())
		drawn.append(sprite)
		print("wrote {}".format(out / "survivor_{}.png".format(i)))
	if args.sheet:
		args.sheet.write_bytes(contact_sheet(drawn))
		print("wrote {}".format(args.sheet))


if __name__ == "__main__":
	main()
