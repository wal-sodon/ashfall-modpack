#!/usr/bin/env python3
"""Audit a Minecraft Java world's region files without third-party dependencies.

Counts block entities (containers), item stack formats inside containers, loot-table references,
and light-emitting blocks. Used to check what survives the Arnis -> 26.3 upgrade and what the
Ashfall post-processor must strip.

Usage: python3 -I tools/region_audit.py <world-dir> [--json out.json]
"""
import argparse
import collections
import gzip
import io
import json
import struct
import sys
import zlib
from pathlib import Path

LIGHT_BLOCKS = {
    "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:lantern", "minecraft:soul_lantern",
    "minecraft:light", "minecraft:end_rod", "minecraft:shroomlight", "minecraft:redstone_lamp",
    "minecraft:fire", "minecraft:soul_fire", "minecraft:torch", "minecraft:wall_torch",
    "minecraft:campfire", "minecraft:soul_campfire", "minecraft:jack_o_lantern",
    "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight",
    "minecraft:netherrack", "minecraft:magma_block", "minecraft:red_bed", "minecraft:white_bed",
}

# --- NBT -------------------------------------------------------------------------------------------

def _read_nbt(buf, tag_type):
    if tag_type == 1:
        return struct.unpack(">b", buf.read(1))[0]
    if tag_type == 2:
        return struct.unpack(">h", buf.read(2))[0]
    if tag_type == 3:
        return struct.unpack(">i", buf.read(4))[0]
    if tag_type == 4:
        return struct.unpack(">q", buf.read(8))[0]
    if tag_type == 5:
        return struct.unpack(">f", buf.read(4))[0]
    if tag_type == 6:
        return struct.unpack(">d", buf.read(8))[0]
    if tag_type == 7:
        (n,) = struct.unpack(">i", buf.read(4))
        return buf.read(n)
    if tag_type == 8:
        (n,) = struct.unpack(">H", buf.read(2))
        return buf.read(n).decode("utf-8", "replace")
    if tag_type == 9:
        item_type, n = struct.unpack(">bi", buf.read(5))
        return [_read_nbt(buf, item_type) for _ in range(n)]
    if tag_type == 10:
        out = {}
        while True:
            (t,) = struct.unpack(">b", buf.read(1))
            if t == 0:
                return out
            (n,) = struct.unpack(">H", buf.read(2))
            name = buf.read(n).decode("utf-8", "replace")
            out[name] = _read_nbt(buf, t)
    if tag_type == 11:
        (n,) = struct.unpack(">i", buf.read(4))
        return list(struct.unpack(f">{n}i", buf.read(4 * n)))
    if tag_type == 12:
        (n,) = struct.unpack(">i", buf.read(4))
        return list(struct.unpack(f">{n}q", buf.read(8 * n)))
    raise ValueError(f"unknown NBT tag {tag_type}")


def read_root(data):
    buf = io.BytesIO(data)
    (t,) = struct.unpack(">b", buf.read(1))
    (n,) = struct.unpack(">H", buf.read(2))
    buf.read(n)
    return _read_nbt(buf, t)

# --- Regions ---------------------------------------------------------------------------------------

def iter_chunks(region_path):
    data = region_path.read_bytes()
    if len(data) < 8192:
        return
    for i in range(1024):
        (loc,) = struct.unpack(">I", data[i * 4:i * 4 + 4])
        offset, sectors = (loc >> 8) * 4096, loc & 0xFF
        if offset == 0 or sectors == 0:
            continue
        length, compression = struct.unpack(">IB", data[offset:offset + 5])
        payload = data[offset + 5:offset + 4 + length]
        if compression == 2:
            raw = zlib.decompress(payload)
        elif compression == 1:
            raw = gzip.decompress(payload)
        elif compression == 3:
            raw = payload
        else:
            raise ValueError(f"{region_path}: unsupported chunk compression {compression} (LZ4?)")
        yield read_root(raw)


def palette_name(entry):
    # 1.21: {"Name": ...}; 26.x: {"id": ..., "properties": ...}, and states without properties are
    # bare strings, which NBT stores inside a mixed list as {"": "minecraft:stone"}.
    if isinstance(entry, str):
        return entry
    return entry.get("Name") or entry.get("id") or entry.get("", "?")


def section_block_counts(section, counter):
    states = section.get("block_states")
    if not states:
        return
    palette = [palette_name(p) for p in states.get("palette", [])]
    if len(palette) == 1:
        counter[palette[0]] += 4096
        return
    longs = states.get("data")
    if not longs:
        return
    bits = max(4, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    seen = 0
    for value in longs:
        value &= (1 << 64) - 1
        for _ in range(per_long):
            if seen == 4096:
                return
            index = value & mask
            counter[palette[index] if index < len(palette) else "?"] += 1
            value >>= bits
            seen += 1


def audit(world):
    regions = sorted(p for p in world.rglob("*.mca") if p.parent.name == "region" and "entities" not in p.parts)
    report = {
        "regions": [str(p.relative_to(world)) for p in regions],
        "chunks": 0,
        "data_versions": collections.Counter(),
        "block_entities": collections.Counter(),
        "containers_with_items": 0,
        "containers_with_loot_table": 0,
        "item_stacks_legacy_Count": 0,
        "item_stacks_modern_count": 0,
        "light_blocks": collections.Counter(),
    }
    for region in regions:
        for chunk in iter_chunks(region):
            report["chunks"] += 1
            report["data_versions"][chunk.get("DataVersion", "?")] += 1
            blocks = collections.Counter()
            for section in chunk.get("sections", []):
                section_block_counts(section, blocks)
            for name, count in blocks.items():
                if name in LIGHT_BLOCKS:
                    report["light_blocks"][name] += count
            for be in chunk.get("block_entities", []):
                report["block_entities"][be.get("id", "?")] += 1
                items = be.get("Items")
                if items:
                    report["containers_with_items"] += 1
                    for stack in items:
                        if "Count" in stack:
                            report["item_stacks_legacy_Count"] += 1
                        if "count" in stack:
                            report["item_stacks_modern_count"] += 1
                if "LootTable" in be:
                    report["containers_with_loot_table"] += 1
    for key in ("data_versions", "block_entities", "light_blocks"):
        report[key] = dict(report[key].most_common())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("world", type=Path)
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    report = audit(args.world)
    text = json.dumps(report, indent=2, default=str)
    if args.json:
        args.json.write_text(text)
    print(text)


if __name__ == "__main__":
    sys.exit(main())
