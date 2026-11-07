#!/usr/bin/env python3
"""Regenerate the wiki's "All recipes" section from the mod's recipe JSON (stdlib only).

Reads every modules/*/src/main/resources/data/*/recipe/*.json, names items from the mods' en_us.json files, and
rewrites the HTML between <!-- recipes:start --> and <!-- recipes:end --> in docs/wiki/ashfall-wiki.html. Result
icons that exist as Ashfall textures are added to docs/wiki/images.json, the file map the wiki is published with.

Usage: python3 -I tools/wiki_recipes.py
"""
import glob
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WIKI = ROOT / "docs/wiki/ashfall-wiki.html"
IMAGES = ROOT / "docs/wiki/images.json"

GROUPS = [
	("Survival and salvage", {"bandage", "cloth", "bottled_water", "iron_nugget", "glass", "alloy_plate", "wire_spool", "satchel", "rain_collector",
							  "spray_can"}),
	("Blasters and power", {"spark_pistol", "volt_sidearm", "pulse_carbine", "cell_pack_small", "cell_pack_standard",
							"cell_pack_heavy", "energy_cell", "field_charger", "rapid_charger", "bunker_core"}),
	("Quiet weapons", {"pipe_wrench", "scrap_machete", "rebar_spear", "nail_launcher", "nails", "bottle_lure", "crowbar"}),
	("Flamethrower and grenades", {"flamethrower", "arc_charge"}),
	("Armour", {"scavenger_hood", "scavenger_jacket", "scavenger_trousers", "scavenger_boots"}),
	("Attachments and blades", {"weaponsmith_bench", "optic", "dampener", "heat_sink", "cell_regulator", "overcharger",
								"plasma_blade"}),
	("Building and light", {"reinforced_planks", "barricade", "reinforced_door", "lantern", "soul_lantern", "copper_lantern", "redstone_lamp",
							"shortwave_set", "rattle_box", "tripline_alarm", "spike_strip", "supply_locker"}),
	("Grid and endgame", {"grid_coupler", "uplink_frame", "capacitor_bank", "grid_uplink"}),
]
COOK = {"minecraft:smelting": "furnace", "minecraft:blasting": "blast furnace", "minecraft:smoking": "smoker",
		"minecraft:campfire_cooking": "campfire"}


def names():
	out = {}
	for f in glob.glob(str(ROOT / "modules/*/src/main/resources/assets/ashfall/lang/en_us.json")):
		for k, v in json.load(open(f)).items():
			m = re.match(r"(?:item|block)\.ashfall\.(.+)", k)
			if m:
				out["ashfall:" + m.group(1)] = v
	return out


NAMES = names()
# Plural tag names read as one item ("1 any dye", not "1 any dyes").
TAG_NAMES = {"dyes": "dye"}


def name(ref):
	"""Display name for an item id, a #tag or a list of alternatives."""
	if isinstance(ref, list):
		return " or ".join(name(r) for r in ref)
	if isinstance(ref, dict):
		ref = ref.get("item") or ref.get("id") or ("#" + ref["tag"] if "tag" in ref else str(ref))
	if ref.startswith("#"):
		tag = ref.split(":")[-1].split("/")[-1].replace("_", " ")
		return "any " + TAG_NAMES.get(tag, tag)
	return NAMES.get(ref) or ref.split(":")[-1].replace("_", " ").title()


def icon(result_id, images):
	ns, path = result_id.split(":")
	if ns != "ashfall":
		return ""
	for pattern in (f"textures/item/{path}.png", f"textures/item/{path}_icon.png"):
		hits = glob.glob(str(ROOT / f"modules/*/src/main/resources/assets/ashfall/{pattern}"))
		if hits:
			images[f"img/{path}.png"] = str(Path(hits[0]).relative_to(ROOT))
			return f'<img class="tex" src="img/{path}.png" alt="" width="32" height="32">'
	return ""


def rows():
	cooking, out = {}, []
	for f in sorted(glob.glob(str(ROOT / "modules/*/src/main/resources/data/*/recipe/*.json"))):
		r = json.load(open(f))
		t = r["type"]
		res = r.get("result", {})
		rid = res.get("id") if isinstance(res, dict) else res
		if not rid:
			continue  # special recipes (socket a crystal, fit a Dampener) are described in the text
		count = res.get("count", 1) if isinstance(res, dict) else 1
		if t in COOK:
			cooking.setdefault((rid, name(r["ingredient"])), []).append(COOK[t])
			continue
		# The flamethrower's recipe is shaped with its own type, so the can's fuel carries over.
		if t in ("minecraft:crafting_shaped", "ashfall:flamethrower"):
			key = r["key"]
			pattern = "\n".join(" ".join(ch if ch != " " else "." for ch in row) for row in r["pattern"])
			legend = ", ".join(f"{k} {name(v)}" for k, v in key.items())
			out.append((rid, count, f'<span class="grid-pat">{html.escape(pattern)}</span>', html.escape(legend)))
		elif t == "minecraft:crafting_shapeless":
			counts = {}
			for ing in r["ingredients"]:
				counts[name(ing)] = counts.get(name(ing), 0) + 1
			out.append((rid, count, "Shapeless", html.escape(", ".join(f"{n} {k}" for k, n in counts.items()))))
	for (rid, ing), how in cooking.items():
		out.append((rid, 1, "Cook", html.escape(f"{ing} in a " + ", ".join(sorted(set(how))))))
	return out


def build():
	images = json.load(open(IMAGES)) if IMAGES.exists() else {}
	grouped = {g: [] for g, _ in GROUPS}
	grouped["Other"] = []
	for rid, count, how, what in rows():
		path = rid.split(":")[1]
		group = next((g for g, ids in GROUPS if path in ids), "Other")
		label = f"{icon(rid, images)}<strong>{html.escape(name(rid))}</strong>" + (f" ×{count}" if count > 1 else "")
		grouped[group].append(f"\t\t<tr><td>{label}</td><td>{how}</td><td>{what}</td></tr>")
	parts = ['<p>Every crafting and cooking recipe in the pack, generated from the mod\'s recipe files. A dot in a grid is an empty slot. "Any planks" means planks of any wood. Every recipe here is also in the vanilla recipe book, unlocked when you pick up one of its ingredients.</p>']
	for g, lines in grouped.items():
		if lines:
			parts.append(f"\t<h3>{g}</h3>\n\t<div class=\"table-wrap\"><table>\n\t\t<tr><th>Result</th><th>Recipe</th><th>Ingredients</th></tr>\n"
						 + "\n".join(lines) + "\n\t</table></div>")
	parts.append("\t<p>Two special recipes: put a Resonance Crystal with a Plasma Blade to socket it, and put a Dampener with a blaster to fit it.</p>")
	block = "\n".join(parts)
	page = WIKI.read_text()
	page, n = re.subn(r"(<!-- recipes:start -->)(.*?)(<!-- recipes:end -->)", lambda m: m.group(1) + "\n\t" + block + "\n\t" + m.group(3), page, flags=re.S)
	if n != 1:
		raise SystemExit("recipe markers not found in the wiki")
	WIKI.write_text(page)
	IMAGES.write_text(json.dumps(images, indent=1, sort_keys=True) + "\n")
	print(f"wrote {sum(len(v) for v in grouped.values())} recipes, {len(images)} images")


if __name__ == "__main__":
	build()
