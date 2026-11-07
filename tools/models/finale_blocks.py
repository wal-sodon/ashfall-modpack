#!/usr/bin/env python3
"""Ashfall Black Start blocks and items: model JSON, blockstates and item definitions (stdlib only).

The textures are pixel art from tools/gen_block_textures.py (blocks) and tools/gen_textures.py (items); this script
only wires them up, so it is run after those. Writes, under modules/arms/src/main/resources/assets/ashfall/:
  models/block/<id>[_<variant>].json   cube models over the generated textures
  blockstates/<id>.json                uplink_socket power 0-2, relay_breaker triggered, single-state pylon and seal
  models/item/<id>.json                block items use their block model; the craft-chain items are flat icons
  items/<id>.json                      item definitions -> models/item/<id>

Uplink Socket: hull plating with a crystal conduit; dark when empty, violet once the Uplink is seated (power 1-2).
Relay Breaker: a grid cabinet, red lamps and the lever down while tripped (triggered=true), green once forced closed.
Heart Pylon and Hull Seal: plain cubes.

Usage: python3 -I tools/models/finale_blocks.py [--root <repo root>]
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
ASSETS = "modules/arms/src/main/resources/assets/ashfall"
NS = "ashfall"

FLAT_ITEMS = ["uplink_frame", "capacitor_bank", "grid_uplink"]


def write(root, rel, data):
	path = root / ASSETS / rel
	path.parent.mkdir(parents=True, exist_ok=True)
	path.write_text(json.dumps(data, indent="\t") + "\n")
	print(f"wrote {path}")


def column(end, side):
	return {"parent": "minecraft:block/cube_column", "textures": {"end": f"{NS}:block/{end}", "side": f"{NS}:block/{side}"}}


def cube_all(tex):
	return {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{tex}"}}


def item_def(model):
	return {"model": {"type": "minecraft:model", "model": f"{NS}:item/{model}"}}


def main():
	ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
	ap.add_argument("--root", default=ROOT, type=Path)
	root = ap.parse_args().root

	# Uplink Socket
	write(root, "models/block/uplink_socket.json", column("uplink_socket_top", "uplink_socket_side"))
	write(root, "models/block/uplink_socket_lit.json", column("uplink_socket_top_lit", "uplink_socket_side_lit"))
	write(root, "blockstates/uplink_socket.json", {"variants": {
		"power=0": {"model": f"{NS}:block/uplink_socket"},
		"power=1": {"model": f"{NS}:block/uplink_socket_lit"},
		"power=2": {"model": f"{NS}:block/uplink_socket_lit"},
	}})
	# Relay Breaker
	write(root, "models/block/relay_breaker.json", column("relay_breaker_top", "relay_breaker_side_tripped"))
	write(root, "models/block/relay_breaker_closed.json", column("relay_breaker_top", "relay_breaker_side_closed"))
	write(root, "blockstates/relay_breaker.json", {"variants": {
		"triggered=true": {"model": f"{NS}:block/relay_breaker"},
		"triggered=false": {"model": f"{NS}:block/relay_breaker_closed"},
	}})
	# Heart Pylon, Hull Seal
	for block in ("heart_pylon", "hull_seal"):
		write(root, f"models/block/{block}.json", cube_all(block))
		write(root, f"blockstates/{block}.json", {"variants": {"": {"model": f"{NS}:block/{block}"}}})

	# Block items: the block's model (the lit socket, so it reads as the finale's piece in a hotbar).
	for block, model in (("uplink_socket", "uplink_socket_lit"), ("relay_breaker", "relay_breaker"),
						 ("heart_pylon", "heart_pylon"), ("hull_seal", "hull_seal")):
		write(root, f"models/item/{block}.json", {"parent": f"{NS}:block/{model}"})
		write(root, f"items/{block}.json", item_def(block))
	# Flat items
	for item in FLAT_ITEMS:
		write(root, f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})
		write(root, f"items/{item}.json", item_def(item))


if __name__ == "__main__":
	main()
