# Archived: the Arnis Asheville map (retired 2026-10-25)

Ashfall's city used to be real-world Asheville, NC, generated from OpenStreetMap by a patched
[Arnis](https://github.com/louis-e/arnis) v3.3.0 and shipped as a world zip. Since stage M1R the city is
generated in every new world by The Lost Cities with Ashfall's own profile, zones and landmark assets
(`docs/deceasedcraft-style-plan.md`, Decision 5: archive, don't delete). Nothing here is built or packaged.

| Path | What it was |
|---|---|
| `map/arnis-patch/` | Our one Arnis patch: writes `<world>/ashfall/buildings.json` (building shells and furnished units) |
| `map/osm/` | OSM downloads for the test tile and the full 3x3 km map |
| `map/out/` | Raw Arnis worlds plus generation logs |
| `map/upgrade/` | The worlds after the vanilla 26.3 upgrade (and the throwaway server used for it) |
| `map/audit/` | Region audits and atlas processing logs |
| `tools/map-pipeline.sh` | Arnis generation, upgrade, audit (`archive/arnis/tools/map-pipeline.sh test|full`) |
| `tools/upgrade_world.py`, `tools/region_audit.py` | The upgrade and audit steps it calls |
| `salvage/main/building/BuildingIndex.java` | The sidecar reader, once a `BuildingSource`; its chunk index lives on as `StaticBuildingSource` |
| `salvage/main/atlas/ArnisProjection.java` | Lat/long to block projection used for calibration |
| `salvage/test/...` | Their JUnit tests |

The world zips (`dist/Ashfall-Asheville-*-world.zip`) are left in `dist/` untouched.

**Bringing a real map back:** restore `BuildingIndex.java` to `modules/salvage/src/main/java/xyz/toesinthesand/ashfall/salvage/building/`,
have it implement `BuildingSource` again (it still does; only `StaticBuildingSource` shares its index code), and
pick it in `AshfallSalvage`'s `SERVER_STARTED` hook when `<world>/ashfall/buildings.json` exists. Every caller
(atlas passes, loot refill, the Director) reads `BuildingSource`, so nothing else changes.
