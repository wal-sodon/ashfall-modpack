#!/usr/bin/env python3
"""Ashfall charger blocks: Field Charger and Power Relay (stdlib only).

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/block/<id>_*.png        16x16 block textures; the glowing parts have an off and an _active texture
  models/block/<id>.json           element model, front facing north, glow parts dark and not emissive
  models/block/<id>_active.json    same geometry, glow parts use the _active textures with light_emission 15
  blockstates/<id>.json            facing (north/east/south/west -> y 0/90/180/270) x active (false/true)
  models/item/<id>.json            block item model (parent: the off block model)
  items/<id>.json                  item definition -> models/item/<id>
  textures/entity/energy_bolt.png  greyscale glowing bolt streak (tinted per gun by the renderer)

Field Charger: a scavenged workshop box of chipped teal-painted steel on skids, four charging bays on
top with cyan contact pads, a small screen and status lamp on the front, and a power cable down the side.
Power Relay: a city utility cabinet on a concrete plinth with hazard stripes, a warning plate and
indicator lamps, and a copper induction coil and antenna on the roof. The coil core and lamps glow
when active. The coil and antenna rise above the block (to y=28) as visual detail only.

Usage: python3 -I tools/models/blocks.py [--root <repo root>]
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "tools" / "textures"))
from sprite import Sprite, rgb, CLEAR  # noqa: E402

ASSETS = "modules/arms/src/main/resources/assets/ashfall"
NS = "ashfall"

# ---------------------------------------------------------------- palettes (light from the top-left)
TEAL = [rgb("93a69e"), rgb("6f827c"), rgb("566660"), rgb("3d4847"), rgb("2a3031")]  # painted steel
STEEL = [rgb("e6e1d6"), rgb("aeb1b8"), rgb("7b7f8c"), rgb("4c4f62"), rgb("2e3040")]  # bare steel
DARK = [rgb("5a5e6a"), rgb("44474f"), rgb("33353d"), rgb("24252c")]                  # dark gunmetal
RUST = [rgb("b06a36"), rgb("7e4426"), rgb("55301f")]
COPPER = [rgb("f6c98a"), rgb("d98f4c"), rgb("ad6630"), rgb("6e3a2e")]
CYAN = [rgb("f2ffff"), rgb("86f4ff"), rgb("26c4e4"), rgb("1478a0")]
GLASS = [rgb("3c5866"), rgb("2a3e4a"), rgb("1c2a34")]                                # unlit glass
BEIGE = [rgb("d4d0bf"), rgb("b3ae9c"), rgb("8f8a79"), rgb("6a6658"), rgb("47443b")]  # utility enclosure
CONCRETE = [rgb("a7a59c"), rgb("8b8981"), rgb("6f6d67"), rgb("53524e")]
HAZARD_Y, HAZARD_K = rgb("e8b830"), rgb("2b2a2e")
RED_OFF, RED_ON = rgb("6e2a2a"), rgb("ff5a4a")
GREEN_ON = rgb("8cff6a")


def h(x, y, salt=0):
	"""Deterministic per-pixel hash in 0..1 for sparse wear marks (no random module, stable output)."""
	n = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
	n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
	return (n ^ (n >> 16)) / 0xFFFFFFFF


def fill(s, c):
	s.rect(0, 0, s.w - 1, s.h - 1, c)


def bevel(s, x0, y0, x1, y1, ramp, inset=False):
	"""Panel with a light top-left edge and dark bottom-right edge (reversed when inset)."""
	hi, lo = (ramp[0], ramp[3]) if not inset else (ramp[3], ramp[0])
	s.rect(x0, y0, x1, y1, ramp[2] if inset else ramp[1])
	s.hline(x0, x1, y0, hi)
	s.vline(x0, y0, y1, hi)
	s.hline(x0, x1, y1, lo)
	s.vline(x1, y0, y1, lo)


def rivet(s, x, y, ramp=STEEL):
	s.set(x, y, ramp[0])
	s.set(x + 1, y + 1, ramp[3]) if x + 1 < s.w and y + 1 < s.h else None


def wear(s, x0, y0, x1, y1, base, chips, salt, density=0.06):
	"""Sparse paint chips (bare steel) and rust flecks on a painted area."""
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			if s.px[y][x] != base:
				continue
			r = h(x, y, salt)
			if r < density * 0.5:
				s.set(x, y, chips[0])
			elif r < density:
				s.set(x, y, chips[1])


def hazard(s, x0, y0, x1, y1, phase=0):
	"""Diagonal yellow/black hazard stripes, 2px wide."""
	for y in range(y0, y1 + 1):
		for x in range(x0, x1 + 1):
			s.set(x, y, HAZARD_Y if ((x + y + phase) // 2) % 2 == 0 else HAZARD_K)


# ---------------------------------------------------------------- field charger textures
def fc_side():
	"""East/west/south: chipped teal panel (visible rows 6..15), vent slots and a bolted patch plate."""
	s = Sprite()
	fill(s, TEAL[2])
	bevel(s, 0, 0, 15, 15, TEAL)
	s.hline(0, 15, 6, TEAL[0])  # top edge of the box (body spans v 6..15)
	s.hline(0, 15, 15, TEAL[4])
	# Vent slots.
	for y in (8, 10, 12):
		s.hline(3, 8, y, TEAL[4])
		s.hline(3, 8, y + 1, TEAL[1])
	# Scavenged patch plate, bare steel, riveted, on the right.
	bevel(s, 10, 8, 13, 13, STEEL)
	s.set(10, 8, STEEL[0])
	s.set(13, 13, STEEL[4])
	for x, y in ((11, 9), (12, 12)):
		s.set(x, y, STEEL[3])
	wear(s, 1, 7, 14, 14, TEAL[2], (STEEL[2], RUST[1]), 1, 0.08)
	# Rust streak under the patch.
	s.vline(12, 14, 14, RUST[1])
	for x, y in ((1, 7), (14, 7), (1, 14), (14, 14)):
		s.set(x, y, STEEL[1])
	return s


def fc_front():
	"""North face (visible u 1..14, v 6..15): screen bezel on the right, lamp and vent grille on the left."""
	s = fc_side()
	s.rect(1, 7, 14, 14, TEAL[2])
	# Bezel recess behind the screen element (screen pixels u 7..12, v 8..11).
	bevel(s, 6, 7, 13, 12, DARK, inset=True)
	# Lamp socket (lamp element at u 3..4, v 8) and vent grille below.
	s.rect(2, 7, 5, 9, DARK[2])
	for y in (11, 13):
		s.hline(2, 5, y, TEAL[4])
		s.hline(2, 5, y + 1, TEAL[1])
	# Hand-painted cyan stencil tick and wear.
	s.set(8, 14, CYAN[3])
	s.set(9, 14, CYAN[3])
	wear(s, 1, 7, 14, 14, TEAL[2], (STEEL[2], RUST[1]), 2, 0.07)
	for x, y in ((1, 7), (14, 7), (1, 14), (14, 14)):
		s.set(x, y, STEEL[1])
	return s


def fc_top():
	"""Top plate: dark tread plate between the bays with a teal rim."""
	s = Sprite()
	fill(s, DARK[1])
	for y in range(16):
		for x in range(16):
			if (x + 2 * y) % 5 == 0:
				s.set(x, y, DARK[0])
	s.rect(0, 0, 15, 0, TEAL[0])
	s.rect(0, 0, 0, 15, TEAL[0])
	s.rect(1, 1, 14, 1, TEAL[1])
	s.rect(1, 1, 1, 14, TEAL[1])
	s.rect(15, 0, 15, 15, TEAL[3])
	s.rect(0, 15, 15, 15, TEAL[3])
	s.rect(14, 1, 14, 14, TEAL[2])
	s.rect(1, 14, 14, 14, TEAL[2])
	return s


def fc_bottom():
	"""Dark gunmetal for skids, the plug box and bezels."""
	s = Sprite()
	fill(s, DARK[1])
	bevel(s, 0, 0, 15, 15, DARK)
	for y in range(1, 15):
		for x in range(1, 15):
			if h(x, y, 3) < 0.07:
				s.set(x, y, DARK[0] if h(x, y, 4) < 0.5 else RUST[2])
	return s


def fc_bay():
	"""Charging bay socket: top at uv 0..5 (dark rim, copper contacts), sides at uv 0..5 x 5..7."""
	s = Sprite()
	fill(s, DARK[1])
	bevel(s, 0, 0, 4, 4, DARK)
	s.rect(1, 1, 3, 3, DARK[3])
	s.set(0, 0, STEEL[1])
	s.set(4, 2, COPPER[1])
	s.set(2, 4, COPPER[2])
	# sides: steel band with a copper contact line
	s.hline(0, 4, 5, STEEL[1])
	s.hline(0, 4, 6, STEEL[3])
	s.set(0, 5, STEEL[0])
	s.set(2, 6, COPPER[2])
	return s


def fc_pad(active):
	"""Contact pad in each bay: top at uv 0..3, sides at uv 0..3 x 3..4.5. Cyan glow when active."""
	s = Sprite()
	if active:
		fill(s, CYAN[2])
		s.rect(0, 0, 2, 2, CYAN[1])
		s.set(1, 1, CYAN[0])
		s.set(2, 2, CYAN[2])
		s.hline(0, 2, 3, CYAN[1])
		s.hline(0, 2, 4, CYAN[3])
	else:
		fill(s, GLASS[1])
		s.rect(0, 0, 2, 2, GLASS[1])
		s.set(0, 0, GLASS[0])
		s.set(2, 2, GLASS[2])
		s.hline(0, 2, 3, GLASS[1])
		s.hline(0, 2, 4, GLASS[2])
	return s


def fc_screen(active):
	"""Screen face at uv 0..6 x 0..4, lamp at uv 8..10 x 0..1; elsewhere dark bezel."""
	s = Sprite()
	fill(s, DARK[2])
	if active:
		s.rect(0, 0, 5, 3, CYAN[3])
		# charge bars and a readout line
		for k, hgt in enumerate((1, 2, 3, 2)):
			s.vline(1 + k, 3 - hgt + 1, 3, CYAN[1])
		s.hline(0, 5, 0, CYAN[2])
		s.set(5, 1, CYAN[0])
		s.rect(8, 0, 9, 0, GREEN_ON)
	else:
		s.rect(0, 0, 5, 3, GLASS[2])
		s.hline(0, 5, 0, GLASS[1])
		s.set(0, 0, GLASS[0])
		s.rect(8, 0, 9, 0, RED_OFF)
	return s


def fc_cable():
	"""Black rubber cable with a worn highlight and a strip of yellow tape."""
	s = Sprite()
	fill(s, DARK[3])
	for y in range(16):
		s.set(0, y, DARK[1])
		if y % 5 == 2:
			s.hline(0, 15, y, HAZARD_Y)
	return s


# ---------------------------------------------------------------- power relay textures
def pr_side():
	"""Cabinet side (visible u 2..13, v 0..13): louvres, ID plate, hazard band low down."""
	s = Sprite()
	fill(s, BEIGE[1])
	bevel(s, 0, 0, 15, 15, BEIGE)
	s.hline(0, 15, 0, BEIGE[0])
	# Louvre vents.
	for y in range(2, 8, 2):
		s.hline(4, 11, y, BEIGE[4])
		s.hline(4, 11, y + 1, BEIGE[0])
	# Small ID plate.
	s.rect(5, 9, 10, 9, STEEL[1])
	s.set(5, 9, STEEL[0])
	s.hline(6, 9, 9, STEEL[3])
	s.set(7, 9, STEEL[1])
	# Hazard band low on the cabinet (v 12..13, just above the plinth).
	hazard(s, 1, 12, 14, 13)
	s.hline(1, 14, 11, BEIGE[3])
	wear(s, 1, 1, 14, 9, BEIGE[1], (BEIGE[3], RUST[1]), 11, 0.03)
	s.vline(13, 1, 2, RUST[1])  # rust weep from the roof seam
	return s


def pr_front():
	"""Cabinet front (visible u 1..14, v 0..13): double doors, warning plate, lamp housing, hazard band."""
	s = pr_side()
	s.rect(1, 1, 14, 9, BEIGE[1])
	# Door frames and centre seam.
	bevel(s, 1, 1, 7, 10, BEIGE)
	bevel(s, 8, 1, 14, 10, BEIGE)
	s.vline(7, 1, 10, BEIGE[4])
	# Lamp housing behind the lamp element (u 3..6, v 2..4).
	s.rect(2, 2, 6, 4, DARK[2])
	# Louvres on the left door.
	for y in (6, 8):
		s.hline(2, 6, y, BEIGE[4])
		s.hline(2, 6, y + 1, BEIGE[0])
	# Warning plate on the right door: yellow triangle with a dark bolt.
	tri = ["..y..", ".yky.", ".yky.", "yyyyy"]
	for dy, row in enumerate(tri):
		for dx, ch in enumerate(row):
			if ch != ".":
				s.set(9 + dx, 2 + dy, HAZARD_Y if ch == "y" else HAZARD_K)
	# Handles.
	s.vline(6, 5, 6, STEEL[1])
	s.vline(9, 7, 8, STEEL[1])
	s.set(6, 7, STEEL[3])
	s.set(9, 9, STEEL[3])
	# Padlock hasp on the seam.
	s.set(7, 6, STEEL[2])
	s.set(8, 6, STEEL[3])
	wear(s, 1, 1, 14, 9, BEIGE[1], (BEIGE[3], RUST[1]), 12, 0.025)
	return s


def pr_top():
	s = Sprite()
	fill(s, BEIGE[2])
	bevel(s, 0, 0, 15, 15, BEIGE)
	s.rect(2, 2, 13, 13, BEIGE[2])
	s.hline(2, 13, 2, BEIGE[3])
	s.vline(2, 2, 13, BEIGE[3])
	wear(s, 2, 2, 13, 13, BEIGE[2], (RUST[1], BEIGE[4]), 13, 0.06)
	return s


def pr_base():
	"""Concrete plinth with a hazard edge on top."""
	s = Sprite()
	fill(s, CONCRETE[1])
	for y in range(16):
		for x in range(16):
			r = h(x, y, 21)
			if r < 0.08:
				s.set(x, y, CONCRETE[2])
			elif r < 0.12:
				s.set(x, y, CONCRETE[0])
	# Plinth sides show rows 14..15: a worn top arris and a dark foot.
	s.hline(0, 15, 14, CONCRETE[0])
	s.hline(0, 15, 15, CONCRETE[3])
	return s


def pr_metal():
	s = Sprite()
	fill(s, DARK[1])
	for y in range(16):
		s.set(0, y, DARK[0])
		s.set(15, y, DARK[3])
		if y % 4 == 0:
			s.hline(0, 15, y, DARK[0])
	return s


def pr_coil():
	"""Copper winding: tight horizontal turns, highlight on top of each turn."""
	s = Sprite()
	for y in range(16):
		c = COPPER[1] if y % 2 == 0 else COPPER[3]
		s.hline(0, 15, y, c)
		for x in range(16):
			if y % 2 == 0 and x % 6 == 1:
				s.set(x, y, COPPER[0])
			if y % 2 == 0 and x % 6 == 4:
				s.set(x, y, COPPER[2])
	return s


def pr_core(active):
	"""Coil core: dark glass when idle, a hot cyan column when active."""
	s = Sprite()
	ramp = CYAN if active else [GLASS[0], GLASS[0], GLASS[1], GLASS[2]]
	for x in range(16):
		c = ramp[1] if x % 4 == 1 else ramp[2] if x % 4 in (0, 2) else ramp[3]
		s.vline(x, 0, 15, c)
	if active:
		for y in range(0, 16, 3):
			s.set((y * 5) % 16, y, CYAN[0])
	return s


def pr_lamp(active):
	"""2x2 lamp cells tiled: amber + green + cyan when active, dull dark when idle."""
	s = Sprite()
	fill(s, DARK[3])
	colours = [rgb("ffb33c"), GREEN_ON, CYAN[1], RED_ON] if active else [rgb("5a4426"), rgb("2f4a2c"), GLASS[1], RED_OFF]
	for cy in range(0, 16, 2):
		for cx in range(0, 16, 2):
			c = colours[(cx // 2) % 4]
			s.set(cx, cy, c)
			if active:
				s.set(cx + 1, cy, c)
				s.set(cx, cy + 1, c)
			s.set(cx + 1, cy + 1, DARK[2])
	return s


# ---------------------------------------------------------------- energy bolt (entity texture)
def energy_bolt():
	"""16x16 greyscale bolt streak, horizontal, for the renderer to tint with the gun's bolt colour.

	Stepped bands (pixel-art, no dithering): white-hot core, light and mid grey sheath, and a faint
	translucent halo, tapering to a point at the leading (right) end and a softer tail on the left.
	"""
	s = Sprite()
	bands = [(255, 255), (208, 240), (156, 200), (110, 120)]  # (grey, alpha) from core outwards
	for y in range(16):
		for x in range(16):
			fx = (x + 0.5 - 8.0) / 7.6
			half = 3.4 * (1 - fx * fx) ** 0.5 if abs(fx) < 1 else 0  # lens-shaped profile
			if x >= 11:
				half *= 0.85  # slightly sharper nose
			if half <= 0:
				continue
			d = abs(y + 0.5 - 8.0) / half
			if d > 1:
				continue
			k = 0 if d < 0.3 else 1 if d < 0.55 else 2 if d < 0.8 else 3
			if abs(fx) > 0.8:
				k = max(k, 2)
			if abs(fx) > 0.93:
				k = 3
			g, a = bands[k]
			s.set(x, y, (g, g, g, a))
	return s


# ---------------------------------------------------------------- model building
FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, tex, uv=None, faces=FACES, emissive=False, cull=None):
	"""One element. tex: a texture key or {face: key}; uv: None (auto from position) or {face: [u0,v0,u1,v1]}."""
	el = {"from": list(frm), "to": list(to), "faces": {}}
	for f in faces:
		t = tex[f] if isinstance(tex, dict) else tex
		if t is None:
			continue
		face = {"texture": "#" + t}
		if uv and f in uv:
			face["uv"] = uv[f]
		if cull and f in cull:
			face["cullface"] = f
		el["faces"][f] = face
	if emissive:
		el["light_emission"] = 15
	return el


def side_uv(w, d, hgt, top):
	"""UVs for a small socket-like box: top face w x d at (0,0), sides w (or d) x hgt at v=top."""
	return {"up": [0, 0, w, d], "down": [0, 0, w, d],
			"north": [0, top, w, top + hgt], "south": [0, top, w, top + hgt],
			"east": [0, top, d, top + hgt], "west": [0, top, d, top + hgt]}


def field_charger(active):
	glow = "glow"
	els = [
		box((1, 0, 2), (15, 1, 4), "bottom", cull={"down"}),
		box((1, 0, 12), (15, 1, 14), "bottom", cull={"down"}),
		box((1, 1, 1), (15, 10, 15), {"north": "front", "south": "side", "east": "side", "west": "side",
									  "up": "top", "down": "bottom"}),
	]
	for bx in (2, 9):
		for bz in (2, 9):
			els.append(box((bx, 10, bz), (bx + 5, 12, bz + 5), "bay", uv=side_uv(5, 5, 2, 5), faces=FACES[:5]))
			els.append(box((bx + 1, 11, bz + 1), (bx + 4, 12.5, bz + 4), "pad", uv=side_uv(3, 3, 1.5, 3),
						   faces=FACES[:5], emissive=active))
	# Screen and status lamp on the front (the box front is at z=1).
	els.append(box((3, 4, 0.5), (9, 8, 1), "screen", uv={"north": [0, 0, 6, 4], "east": [6, 0, 6.5, 4],
														"west": [6, 0, 6.5, 4], "up": [0, 4, 6, 4.5],
														"down": [0, 4, 6, 4.5]},
				   faces=("north", "east", "west", "up", "down"), emissive=active))
	els.append(box((11, 7, 0.5), (13, 8, 1), "screen", uv={"north": [8, 0, 10, 1], "east": [8, 0, 8.5, 1],
														  "west": [8, 0, 8.5, 1], "up": [8, 0, 10, 0.5],
														  "down": [8, 0, 10, 0.5]},
				   faces=("north", "east", "west", "up", "down"), emissive=active))
	# Power cable: plug box on the east side, cable dropping to the floor.
	els.append(box((15, 6, 10), (16, 9, 13), "bottom", faces=("north", "south", "east", "up", "down")))
	els.append(box((15.25, 0, 11), (16, 6, 12), "cable", faces=("north", "south", "east", "west")))
	sfx = "_active" if active else ""
	tex = {
		"particle": f"{NS}:block/field_charger_side",
		"front": f"{NS}:block/field_charger_front",
		"side": f"{NS}:block/field_charger_side",
		"top": f"{NS}:block/field_charger_top",
		"bottom": f"{NS}:block/field_charger_bottom",
		"bay": f"{NS}:block/field_charger_bay",
		"pad": f"{NS}:block/field_charger_pad{sfx}",
		"screen": f"{NS}:block/field_charger_screen{sfx}",
		"cable": f"{NS}:block/field_charger_cable",
	}
	return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def power_relay(active):
	els = [
		box((0, 0, 0), (16, 2, 16), "base", cull={"down", "north", "south", "east", "west"}),
		box((1, 2, 2), (15, 16, 14), {"north": "front", "south": "side", "east": "side", "west": "side",
									  "up": "top", "down": None}),
		box((0, 16, 1), (16, 17, 15), {"north": "metal", "south": "metal", "east": "metal", "west": "metal",
									   "up": "top", "down": "metal"}),
		# Roof coil: insulator foot, glowing core, two copper windings, antenna mast and beacon.
		box((5, 17, 5), (11, 18, 11), "metal", faces=FACES[:5]),
		box((6.5, 18, 6.5), (9.5, 23, 9.5), "core", faces=FACES[:5], emissive=active),
		box((5.5, 18.75, 5.5), (10.5, 20.25, 10.5), "coil"),
		box((5.5, 21, 5.5), (10.5, 22.5, 10.5), "coil"),
		box((7.5, 23, 7.5), (8.5, 27, 8.5), "metal", faces=FACES[:5]),
		box((7, 27, 7), (9, 28, 9), "beacon", faces=FACES[:5], emissive=active),
		# Conduit up the east side into a junction box.
		box((15, 2, 6), (16, 9, 8), "metal", faces=("north", "south", "east")),
		box((14.5, 9, 5), (16, 13, 9), "metal", faces=("north", "south", "east", "up", "down")),
		# Indicator lamp strip on the front (cabinet front at z=2).
		box((9, 12, 1), (13, 13.5, 2), "lamp", faces=("north", "east", "west", "up", "down"), emissive=active),
	]
	sfx = "_active" if active else ""
	tex = {
		"particle": f"{NS}:block/power_relay_side",
		"front": f"{NS}:block/power_relay_front",
		"side": f"{NS}:block/power_relay_side",
		"top": f"{NS}:block/power_relay_top",
		"base": f"{NS}:block/power_relay_base",
		"metal": f"{NS}:block/power_relay_metal",
		"coil": f"{NS}:block/power_relay_coil",
		"core": f"{NS}:block/power_relay_core{sfx}",
		"lamp": f"{NS}:block/power_relay_lamp{sfx}",
		"beacon": f"{NS}:block/power_relay_lamp{sfx}",
	}
	return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def blockstate(block_id):
	variants = {}
	for active in ("false", "true"):
		for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
			v = {"model": f"{NS}:block/{block_id}" + ("_active" if active == "true" else "")}
			if y:
				v["y"] = y
			variants[f"active={active},facing={facing}"] = v
	return {"variants": variants}


TEXTURES = {
	"field_charger_front": fc_front,
	"field_charger_side": fc_side,
	"field_charger_top": fc_top,
	"field_charger_bottom": fc_bottom,
	"field_charger_bay": fc_bay,
	"field_charger_pad": lambda: fc_pad(False),
	"field_charger_pad_active": lambda: fc_pad(True),
	"field_charger_screen": lambda: fc_screen(False),
	"field_charger_screen_active": lambda: fc_screen(True),
	"field_charger_cable": fc_cable,
	"power_relay_front": pr_front,
	"power_relay_side": pr_side,
	"power_relay_top": pr_top,
	"power_relay_base": pr_base,
	"power_relay_metal": pr_metal,
	"power_relay_coil": pr_coil,
	"power_relay_core": lambda: pr_core(False),
	"power_relay_core_active": lambda: pr_core(True),
	"power_relay_lamp": lambda: pr_lamp(False),
	"power_relay_lamp_active": lambda: pr_lamp(True),
}

# Item display: the relay (with its roof coil) is shrunk and lowered so it fits a slot.
ITEM_DISPLAY = {
	"field_charger": None,
	"power_relay": {
		"gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.45, 0.45, 0.45]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
		"fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
		"thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]},
		"thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]},
		"firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.32, 0.32, 0.32]},
		"firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.32, 0.32, 0.32]},
	},
}


def write_json(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")
	print(f"wrote {path}")


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", type=Path, default=ROOT)
	args = ap.parse_args()
	assets = args.root / ASSETS
	tex_dir = assets / "textures" / "block"
	tex_dir.mkdir(parents=True, exist_ok=True)
	for name, fn in TEXTURES.items():
		(tex_dir / f"{name}.png").write_bytes(fn().png())
		print(f"wrote {tex_dir / name}.png")
	ent_dir = assets / "textures" / "entity"
	ent_dir.mkdir(parents=True, exist_ok=True)
	(ent_dir / "energy_bolt.png").write_bytes(energy_bolt().png())
	print(f"wrote {ent_dir / 'energy_bolt.png'}")
	for block_id, build in (("field_charger", field_charger), ("power_relay", power_relay)):
		write_json(assets / "models" / "block" / f"{block_id}.json", build(False))
		write_json(assets / "models" / "block" / f"{block_id}_active.json", build(True))
		write_json(assets / "blockstates" / f"{block_id}.json", blockstate(block_id))
		item_model = {"parent": f"{NS}:block/{block_id}"}
		if ITEM_DISPLAY[block_id]:
			item_model["display"] = ITEM_DISPLAY[block_id]
		write_json(assets / "models" / "item" / f"{block_id}.json", item_model)
		write_json(assets / "items" / f"{block_id}.json",
				   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{block_id}"}})


if __name__ == "__main__":
	main()
