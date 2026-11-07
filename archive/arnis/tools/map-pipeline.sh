#!/usr/bin/env bash
# Rebuilds an Ashfall Asheville world from scratch: Arnis generation -> vanilla 26.3 upgrade -> audit.
# Usage: archive/arnis/tools/map-pipeline.sh test|full   (retired 2026-10-25; see archive/arnis/README.md)
# Requires: cargo (to build Arnis v3.3.0 once), Java 25, python3.
set -euo pipefail

# ROOT is archive/arnis (map/ and tools/ moved here together); the tool caches stay in the repo's .cache/.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
AREA="${1:-test}"
case "$AREA" in
	# Downtown 1 km² test tile.
	test) BBOX="35.590,-82.557,35.599,-82.546" ;;
	# Full ~3x3 km map: downtown, Mission Hospital, River Arts District, French Broad river, suburbs, Beaucatcher ridge.
	full) BBOX="35.573,-82.572,35.600,-82.539" ;;
	*) echo "usage: $0 test|full" >&2; exit 2 ;;
esac
SPAWN_LAT=35.5951 # Pack Square
SPAWN_LNG=-82.5515

ARNIS_DIR="$REPO/.cache/arnis"
ARNIS="$ARNIS_DIR/target/release/arnis"
SERVER_JAR="$REPO/.cache/vanilla-server/server.jar"

PATCH="$ROOT/map/arnis-patch/0001-ashfall-building-sidecar.patch"
if [[ ! -d "$ARNIS_DIR" ]]; then
	git clone --depth 1 --branch v3.3.0 https://github.com/louis-e/arnis.git "$ARNIS_DIR"
fi
# Our one patch: writes <world>/ashfall/buildings.json (building shells + furnished units).
if ! grep -q ashfall_sidecar "$ARNIS_DIR/src/main.rs"; then
	(cd "$ARNIS_DIR" && git apply "$PATCH")
fi
if [[ ! -x "$ARNIS" || "$PATCH" -nt "$ARNIS" ]]; then
	# The CLI doesn't need the Tauri GUI.
	(cd "$ARNIS_DIR" && cargo build --release --no-default-features)
fi
if [[ ! -f "$SERVER_JAR" ]]; then
	echo "Missing $SERVER_JAR (vanilla 26.3 server, from Mojang's version manifest)" >&2
	exit 1
fi

mkdir -p "$ROOT/map/out" "$ROOT/map/osm" "$ROOT/map/audit"
rm -rf "$ROOT/map/out/$AREA" "$ROOT/map/upgrade/$AREA"

# --no-tile-archive: --save-json-file only writes when OSM data comes from Overpass, and the
# post-processor must read exactly the data the world was built from.
# Arnis v3.3.0 has no post-apocalyptic mode; the ruined look comes from our post-processor.
"$ARNIS" --bbox "$BBOX" --output-dir "$ROOT/map/out/$AREA" --interior true --fillground \
	--no-tile-archive --save-json-file "$ROOT/map/osm/$AREA.json" \
	--spawn-lat "$SPAWN_LAT" --spawn-lng "$SPAWN_LNG" | tee "$ROOT/map/out/$AREA-gen.log"

# Upgrade once, offline, with a vanilla server so DataFixer problems can't be blamed on mods.
python3 -I "$ROOT/tools/upgrade_world.py" "$SERVER_JAR" "$ROOT/map/out/$AREA/Arnis World 1" "$ROOT/map/upgrade" "$AREA"

python3 -I "$ROOT/tools/region_audit.py" "$ROOT/map/out/$AREA/Arnis World 1" --json "$ROOT/map/audit/$AREA-before.json" > /dev/null
python3 -I "$ROOT/tools/region_audit.py" "$ROOT/map/upgrade/$AREA" --json "$ROOT/map/audit/$AREA-after.json" > /dev/null
echo "Upgraded world: $ROOT/map/upgrade/$AREA (audits in map/audit/)"
