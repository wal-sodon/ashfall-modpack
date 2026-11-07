#!/usr/bin/env python3
"""Resonant Plasma Blade 3D item model (Bind ending): the Plasma Blade's model with the beam recoloured.

Reuses tools/models/plasma_blade.py wholesale (elements, display transforms, items JSON) and only swaps the item id
and the beam palette for the bound Heart's cyan core and gold edge, so the two blades stay identical in shape and a
change to the hilt reaches both.

Writes, under modules/arms/src/main/resources/assets/ashfall/:
  textures/item/resonant_plasma_blade_skin.png
  models/item/resonant_plasma_blade{,_hilt,_icon,_hilt_icon}.json
  items/resonant_plasma_blade.json
Usage: python3 -I tools/models/resonant_plasma_blade.py
"""
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent


def _base():
	spec = importlib.util.spec_from_file_location("ashfall_model_plasma_blade_base", HERE / "plasma_blade.py")
	module = importlib.util.module_from_spec(spec)
	spec.loader.exec_module(module)
	return module


def main():
	base = _base()
	rgb = base.rgb
	base.ID = "resonant_plasma_blade"
	base.BEAM = [rgb("f4ffff"), rgb("c6fbff"), rgb("6fe6f2"), rgb("2bb7d4"), rgb("d9a43a")]
	base.main()


if __name__ == "__main__":
	main()
