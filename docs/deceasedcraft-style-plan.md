# Plan: DeceasedCraft-style generation without Arnis (2026-10-25)

**User request:** "Make the generation like DeceasedCraft, without Arnis, and implement any other mods that would make the project get closer to its final goal."

**Short version:** The Lost Cities now has a Fabric 26.3 release, and it is the same generator DeceasedCraft uses. Ashfall switches to it. We ship one custom Lost Cities profile, plus an `ashfall` asset set (world style, city styles, landmark buildings and loot conditions) inside our own jar. A small `ZoneMap` hook gives the districts fixed positions. The salvage, director and grid code read building context from the Lost Cities API instead of from Arnis/OSM `buildings.json`. The Arnis map, its world zip and the post-processing pipeline are retired. Players create a fresh world with the pack and the city is generated for them.

Sources:
- Research run on 2026-10-25. It covered DeceasedCraft Beta 5.10.17 (manifest, configs, DCTweaks jar), the Lost Cities `26.3_fabric` source, and the Modrinth API. Work files are in `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`.
- Every version id below was re-checked on 2026-10-25 against `api.modrinth.com/v2/project/<slug>/version?game_versions=["26.3"]&loaders=["fabric"]`.
- DeceasedCraft's own assets (DCTweaks) are **All Rights Reserved**. They serve as a design reference only, and nothing from them is copied.

---

## 1. World generation that replaces Arnis

### 1.1 What we copy from DeceasedCraft and what we change

| DeceasedCraft does | Ashfall does |
|---|---|
| The Lost Cities "onlycities" profile: the whole world is city, with no ruins or explosions | A city of about 3.2 km × 3.2 km with zones. The core and districts are dense. Light ruins and explosions increase toward the outskirts. Wilderness lies beyond. |
| Districts are **biome tags**. Each tag has its own city style at factor 99999. Biomes O' Plenty and Biome Size make the districts large. | Districts sit at **fixed positions** (the `ZoneMap`). Every world has the same city layout, so the Grid Map, sieges and story can be authored. Building contents still change per seed. Biome-tag districts are the fallback (Decision 1). |
| Lost Cities spawners are on. In Control adds spawns. Lost Souls fills buildings. | `generateSpawners: false`. The Director owns all spawning, including the per-building populations from the spec. |
| Lost Cities lighting is off | `generateLighting: false`, the **Lights Off** edition of Modern Tweaks, and our LightStripper as a safety net. Stripped lamps are logged so the relight can restore them. |
| 1,527 per-floor, per-container loot tables (ARR) | Loot conditions are generated from our existing 25 `building_profile` files. We start with building-level tables and add per-floor variants later. |
| The player is forced to spawn near residential buildings | We force the spawn into `ashfall:safehouse` in Montford, the unscored start district. It holds the Field Radio cache. |
| Hordes every 15 days of each player's playtime, with zombie classes unlocked on set days | Same shape for our sieges (M8): siege tier = max(day tier, Grid tier), with data tables of the form "class unlocked on day N". |
| Military loot is too generous (the top review complaint) | Keep the scarcity: `chestWithoutLootChance` 0.35, and the top tier only in Lootr landmark caches. |
| Lost Cities generation lag (the top performance complaint) | Floors capped at 8, no spawner blocks, vanilla-block parts, and Chunky pregeneration of the city before play. |

### 1.2 Zones (city layout, in block coordinates; origin = Downtown)

| Zone | Where | Lost Cities city style | Character | Director |
|---|---|---|---|---|
| **Downtown** (core, district 3, tier 3) | Disc at (0, 0), r 400 | `ashfall:downtown` | 4–8 floors, offices, hotels, the parking-deck substation, the police armory | Dense building populations, armored ×4 at police sites |
| **River Arts District** (district 1, tier 2) | Disc at (−900, 100), r 350 | `ashfall:river_arts` | Warehouses and brick, 1–3 floors, the pump-house substation on a water channel, Kiln & Co. Salvage (Abomination lair) | Normal |
| **South Slope** (district 2, tier 2) | Disc at (100, 800), r 300 | `ashfall:south_slope` | Mixed low-rise, bars, the brewery-yard substation | Normal |
| **Mission campus** (district 4, tier 4) | Disc at (750, 650), r 300 | `ashfall:mission` | Hospital multibuilding with the generator plant and a sealed deep-ward cellar (Patient Zero) | Highest infection pressure |
| **Montford** (start area, unscored) | Disc at (−150, −800), r 400 | `ashfall:residential` | Houses 1–3 floors, `ashfall:safehouse` spawn | Gentle: populations ×0.6 for the first 3 days |
| **Suburbs** (residential filler) | Any other city chunk within 1,600 of the origin | `ashfall:residential` | Houses, shops, gas stations | Normal |
| **Outskirts / wasteland** | Ring from 1,600 to 2,600 | `ashfall:wasteland` | Building chance 0.15, frequent explosions, rubble. Scattered relay huts, FEMA camps, gas stations, cabins and crash sites. | Darkness spawning ×1.3, few buildings |
| **Ridge** (tier 5 approach) | Wedge where x > 1,700 and \|z\| < 900 | No city (`isCity = false`) | Woods. The Wreck hull sits at about (2,300, −100), with crash sites around it. | Juggernaut guards the hull |
| **Undiscovered** | Beyond 2,600. World border radius 4,000. | No city | Wild terrain, rare Aftershock crash sites | Full darkness spawning, no populations |

The disc and ring values are starting points. They live in `data/ashfall/ashfall/zone/*.json`, and the same file feeds the Lost Cities hook, the Director, the Grid Map and district membership.

```json
// data/ashfall/ashfall/zone/river_arts.json
{
  "id": "river_arts", "kind": "district", "district_index": 1, "tier": 2,
  "shape": { "type": "disc", "x": -900, "z": 100, "r": 350 },
  "citystyle": "ashfall:river_arts",
  "landmarks": ["ashfall:substation_river_arts", "ashfall:kiln_warehouse"],
  "key_relay_targets": [[-1050, 0], [-950, 250], [-800, -50], [-700, 150], [-1000, 350], [-850, 300]],
  "damage_scale": 1.0, "population_scale": 1.0
}
```

### 1.3 How the zones reach Lost Cities (two layers)

**Layer A, data only, tried first.**
1. The `ashfall` profile (below) turns the whole area into one large city that fades out past about 2.6 km, using `citySpawnDistance2` and `citySpawnMultiplier2`.
2. `data/ashfall/lostcities/predefinedcities/*.json` holds one predefined city per district, with a centre, a radius and the district city style. Each lists its **landmark buildings at fixed chunks** with `preventruins: true`, so substations, the hospital, the police station and the warehouse always exist and are never ruined.

```json
// data/ashfall/lostcities/predefinedcities/downtown.json (centre chunk 0,0; r 400 blocks = 25 chunks)
{
  "dimension": "minecraft:overworld", "chunkx": 0, "chunkz": 0, "radius": 400,
  "citystyle": "ashfall:downtown",
  "buildings": [
    { "building": "ashfall:substation_downtown", "chunkx": 3, "chunkz": -2, "multi": true, "preventruins": true },
    { "building": "ashfall:police_station", "chunkx": -4, "chunkz": 1, "multi": true, "preventruins": true }
  ]
}
```

**Layer B, about 150 lines of Java in salvage (`city/ZoneMap.java`).** It is needed for exact zone borders that Layer A can't express:
- A `LostCityEvents.CHARACTERISTICS` listener assigns the suburb and wasteland city styles by ring. It writes **both** `cityStyleId` and the resolved `cityStyle`.
- The same listener sets `isCity = false` in the Ridge and Undiscovered zones.
- It is a pure function of chunk coordinates, with no level state, because it runs speculatively on worldgen threads.
- Registering it turns off Lost Cities' forced-spawn prefilter, so the spawn search falls back to `spawnCheckAttempts`. That is acceptable because the safehouse sits at a known position.

The M1R spike decides whether Layer A alone is enough. The open questions are how overlapping predefined cities merge and whether ring styling works without code.

### 1.4 Profile and config (concrete starting values)

The profile ships in `pack/overrides/config/lostcities/profiles/ashfall.json`. Its name is our own because Lost Cities overwrites its standard profiles at every start. Keys left out keep their defaults. Check the key names in game with `/lost saveprofile ashfall` during the spike.

```json
{
  "public": true,
  "lostcity": {
    "description": "Ashfall: a dead, dark city waiting to be relit",
    "worldStyle": "ashfall:relight",
    "generateSpawners": false,
    "generateLighting": false,
    "generateLoot": true,
    "chestWithoutLootChance": 0.35,
    "buildingWithoutLootChance": 0.15,
    "ruinChance": 0.12,
    "ruinMinlevelPercent": 0.6,
    "ruinMaxlevelPercent": 1.0,
    "rubbleLayer": true,
    "rubbleDirtScale": 2.5,
    "rubbleLeaveScale": 5.0,
    "vineChance": 0.03,
    "randomLeafBlockChance": 0.05,
    "buildingChance": 0.5,
    "buildingMinFloors": 1,
    "buildingMaxFloors": 8,
    "buildingMinFloorsChance": 2,
    "buildingMaxFloorsChance": 6,
    "buildingMinCellars": 0,
    "buildingMaxCellars": 2,
    "buildingDoorwayChance": 0.6,
    "buildingFrontChance": 0.25,
    "parkChance": 0.3,
    "fountainChance": 0.05,
    "highwayRequiresTwoCities": true,
    "railwaysEnabled": true,
    "railwayDungeonChance": 0.02,
    "spawnCity": "montford",
    "forceSpawnInBuilding": true,
    "forceSpawnBuildings": ["ashfall:safehouse"],
    "spawnCheckRadius": 200
  },
  "cities": {
    "cityChance": 0.2,
    "cityMinRadius": 120,
    "cityMaxRadius": 256,
    "cityThreshold": 0.15,
    "citySpawnDistance1": 0,
    "citySpawnMultiplier1": 1.0,
    "citySpawnDistance2": 2600,
    "citySpawnMultiplier2": 0.0
  },
  "explosions": {
    "explosionChance": 0.002,
    "explosionMaxRadius": 30,
    "miniExplosionChance": 0.04,
    "debrisToNearbyChunkFactor": 180,
    "explosionsInCitiesOnly": true
  }
}
```

**Selecting the profile for new worlds.** Ship `pack/overrides/defaultconfigs/lostcities-server.toml` containing:

```toml
[profiles]
selectedProfile = "ashfall"
```

Confirm on first boot that FCAP copies it into `<world>/serverconfig/` on Fabric, and check the exact file name. If FCAP doesn't honour it, set the profile from code at server start, before the overworld generates. Do not use `setOverworldProfile`, because it touches client GUI classes.

`config/lostcities/common.toml` keeps `avoidVillages = true` and adds the outpost, mansion, pyramids, igloo and swamp hut to `avoidStructures`. The Nether and End stay without Lost Cities.

**Zone differences inside one profile.** Ruin chance is a profile-wide value. Per-zone character therefore comes from each city style:
- `buildingsettings` (min and max floors, building chance)
- `explosionchance` (high in `ashfall:wasteland`)
- `stuff_tags` (cobwebs, chains, rubble)
- our DamagePass `damage_scale`

The spike checks whether the city-style key `profile_overrides` can also set ruin values per zone.

**World style `ashfall:relight`** (in `data/ashfall/lostcities/worldstyles/relight.json`):
- `outsidestyle` and the default city styles come from Modern Tweaks (Lights Off), whose ids are read during the spike.
- `citystyles` lists the 6 Ashfall styles. They are picked by ZoneMap or predefined cities, not by biome.
- `scattered` (outside cities): `ashfall:relay_hut`, `ashfall:fema_camp`, `ashfall:gas_station`, `ashfall:crash_site`, plus the built-in cabin. Area size 7, chance 0.6.
- `citybiomemultipliers` keeps the standard ocean and river reduction.

**City style example:**

```json
// data/ashfall/lostcities/citystyles/river_arts.json
{
  "inherit": "lostcities:citystyle_common",
  "explosionchance": 0.002,
  "buildingsettings": { "minfloors": 1, "maxfloors": 3, "mincellars": 0, "maxcellars": 1, "buildingchance": 0.55 },
  "selectors": {
    "buildings": [
      { "factor": 0.30, "value": "ashfall:warehouse_small" },
      { "factor": 0.20, "value": "<modern-tweaks warehouse id>" },
      { "factor": 0.20, "value": "<modern-tweaks brick shop id>" },
      { "factor": 0.10, "value": "ashfall:pharmacy" }
    ],
    "multibuildings": [ { "factor": 0.05, "value": "ashfall:kiln_warehouse" } ]
  }
}
```

### 1.5 Spawn placement

- `spawnCity: "montford"` together with `forceSpawnInBuilding` and `forceSpawnBuildings: ["ashfall:safehouse"]`. Lost Cities' server mixin moves the new-world spawn into the safehouse.
- The safehouse palette holds:
  - a Lootr cache with `ashfall:chests/field_radio_cache`, set as a palette `loot` condition or through the palette `tag` NBT;
  - one Field Charger;
  - blackout-curtain windows.
- The Director's existing 16-block bed and respawn exclusion also covers the safehouse. On the first server start we add a small OPAC server claim around it, using the safe-zone code from M4.

### 1.6 Buildings and assets

**Start with what exists.** Use the Lost Cities built-in assets (37 buildings, 207 parts, 14 multibuildings) and **Lost Cities Modern Tweaks (Lights Off)** for all generic houses, offices, shops, streets and parks. Modern Tweaks is MIT, so we may fork any part we need to change. Credit it in `CREDITS.md`.

**Custom buildings to author first**, in priority order. Each one is vanilla blocks only, under 8 floors, with no modded block entities except our own:

| # | Asset | Type | Why first | Special palette chars |
|---|---|---|---|---|
| 1 | `ashfall:safehouse` | building, 1 chunk | Spawn and the Act 0 Field Radio | Lootr cache, Field Charger |
| 2 | `ashfall:substation_{river_arts,south_slope,downtown,mission}` | multibuilding 2×2 (4 variants: pump house with water channel, brewery yard, parking deck with 3 floors and a roof, hospital generator plant) | The core of the end-goal loop (M4.5) | `S` Substation Core (`tag` NBT `{district:"…"}`), `L` dead lamp (unlit redstone lamp, logged for relight) |
| 3 | `ashfall:police_station` | multibuilding 2×1 | Police armory Lootr cache, armored weighting, Pulse Carbine parts | Lootr armory cache |
| 4 | `ashfall:hospital` | multibuilding 3×2 with 2 cellars | Mission campus, the hospital Lootr cache, and the Patient Zero deep ward (a cellar sealed until the plant is restored) | Lootr cache, sealed-door block |
| 5 | `ashfall:kiln_warehouse` | multibuilding 2×2 | River Arts story site and the Abomination foundry lair | Boss marker block |
| 6 | `ashfall:pharmacy`, `ashfall:clinic`, `ashfall:gas_station` | buildings | The medicine and fuel loot that the design depends on | — |
| 7 | `ashfall:relay_hut`, `ashfall:fema_camp`, `ashfall:crash_site` | scattered | Outskirts content, relays outside the city, crystals | Power Relay, Wreck alloy |
| 8 | Wreck hull | **not** Lost Cities. A structure template that our code places at the fixed ridge position on first load. | Finale (M9.5) | — |

**Authoring workflow.**
1. Create a dev profile with `editMode: true`.
2. Build with `/lost createbuilding <name> <floors> <cellars>` and `/lost createpart`.
3. Export with `/lost exportpart <name>`.
4. Commit the JSON to `modules/salvage/src/main/resources/data/ashfall/lostcities/{parts,buildings,multibuildings,palettes,...}`.
5. Lint it with a small `tools/check_lc_assets.py` (block ids exist on 26.3, palette characters resolve).

**As built (first batch).** The first batch is generated, not hand-exported: `tools/landmarks/build_landmarks.py` models each landmark as one voxel grid (furniture, U-shaped stairs and facades come from `kit.py`), slices it into per-chunk, per-floor parts with their own palettes, validates block ids against the 26.3 blockstates (`--blockstates DIR`) and checks that sample rooms are walkable from the entrance (stairs only, no ladders). The predefined cities in `data/ashfall/lostcities/predefinedcities/` pin the landmarks:

| Landmark | Id | North-west chunk | Street chunks (south) |
|---|---|---|---|
| Safehouse (spawn) | `ashfall:safehouse` | (-10, -50) | row z = -49 |
| Pharmacy | `ashfall:pharmacy` (also a random city-style building) | (-8, -50) | row z = -49 |
| Police station 2x1 | `ashfall:police_station` | (-4, 1) | (-4..-3, 2) |
| Parking-deck substation 2x2 | `ashfall:substation_downtown` | (3, -2) | (3..4, 0) |
| Pump-house substation 2x2 | `ashfall:substation_river_arts` | (-55, 4) | (-55..-54, 6) |
| Kiln & Co. Salvage 2x2 | `ashfall:kiln_warehouse` | (-60, 7) | (-60..-59, 9) |
| Brewery-yard substation 2x2 | `ashfall:substation_south_slope` | (5, 49) | (5..6, 51) |
| Hospital 3x2, 2 cellars | `ashfall:hospital` | (44, 39) | (44..46, 41) |
| Generator-plant substation 2x2 | `ashfall:substation_mission` | (48, 39) | (47, 39..41), (48..49, 41) |

Markers until the real blocks exist (block tags in `data/ashfall/tags/block/landmark/`): Substation Core = `minecraft:heavy_core` (not lodestone: lodestone is a POI block that Lost Cities swaps in after generation, and the swap was lost on upper floors), dead lamp = unlit `minecraft:redstone_lamp`, boss marker = `minecraft:reinforced_deepslate`, sealed door = an iron door with no button. Caches are plain chests whose loot table is in the `ashfall_cache` namespace, so Lootr converts them. A `PRE_EXPLOSION` listener cancels explosions in landmark chunks and a chunk-load pass removes cave-decoration light (glow lichen) from them.

### 1.7 How our systems plug in through the Lost Cities API

The entry point is the static field `mcjty.lostcities.LostCities.lostCitiesImp`:
- `getLostInfo(level).getChunkInfo(cx, cz)` returns `isCity`, `getBuildingId`, `getMultiBuildingInfo` (type, offset, w, h), `getNumFloors`, `getNumCellars`, `getCityLevel`, `getDamage(y)`, `getRuinLevel` and `getCityStyle`.
- It is deterministic and cached, and it works for chunks that are not loaded yet.

A new class `salvage/city/LostCityContext` wraps it behind an interface, `BuildingSource`. Gametests can then run with a fake source, and the old Arnis `BuildingIndex` could be kept behind the same interface if it is ever wanted again.

| System | Today (Arnis) | With Lost Cities |
|---|---|---|
| **Loot by building type and zone** | `building_profile` matches OSM tags and Arnis unit uses. ContainerPass rewrites chests and tops each unit up to its profile count. | (1) `building_profile` gets `match.lc_buildings`, `match.lc_multibuildings` and `match.zones` in place of the OSM rules. (2) `tools/gen_lc_conditions.py` **generates** `data/lostcities/lostcities/conditions/chestloot.json` from the profiles, using `inbuilding` and `cellar` filters with factors, so a single source sets loot at generation time. (3) ContainerPass is reduced to registering containers for refill and placing the per-player caches. Top-up is kept only for profiles whose parts have too few containers. (4) A zone multiplier applies via Loot Table Modifier: Downtown and Mission get +1 tier for weapon parts, Montford −1. |
| **Director building populations** | Keyed by OSM id, sized by unit floor area | Keyed by **building instance**: `dim | origin chunk (multibuilding origin, or the chunk itself) | buildingId`. Count = round(per_100m² × (floors + cellars) × 16·16·w·h × 0.6 / 100), capped at 12, × the zone's `population_scale`, × (1 − damage) for ruined buildings. Spawn Y comes from `getRealHeight(cityLevel) + 6·floor`, at an air cell inside the footprint. The trigger is unchanged: first player within 48 blocks. The cleared timer is unchanged. |
| **Director spawn context** | — | New conditions in the spawn tables: `zone`, `in_building`, `on_street` (city chunk with no building), `building` id. This is the In Control idea, implemented in our SpawnDirector. In Control itself is not installed. |
| **Light stripping** | Strips glowstone, sea lanterns and lamps baked in by Arnis (201,785 → 5) | Most of the work is gone: `generateLighting: false` and the Lights Off assets cover it. LightStripper **stays as a cheap safety net** in the first-load chunk pass, for built-in Lost Cities parts that still contain glowstone or lanterns. It now **logs every stripped position**, plus every `L` dead-lamp palette char, into district SavedData. That log is the relight data M4.5 needs, which closes the gap called out in endgame-design §6. |
| **Damage pass** | Broken and boarded windows, weathered walls, vines, debris, cars, barricades | Lost Cities already provides ruins, explosions, rubble and vines, so our **weathering, vines and debris are turned off** to avoid doubling up. **Kept:** broken and boarded windows, barricades, and abandoned cars on street chunks (`isCity && buildingId == null`). Intensity scales with zone `damage_scale`: Downtown 0.6, districts 1.0, Montford 0.7, wasteland 1.5. Landmarks (`preventruins`) get boarding only. |
| **Power Relays** | Candidates ranked from the Arnis building data | **Deterministic and computable before generation.** For each district, the 6–8 **key relays** go on the street chunk nearest each `key_relay_targets` point, found with `getChunkInfo` on chunks that may not exist yet. **Ambient relays** go on about 1 in 40 street chunks by `hash(seed, chunk)`, for about 75 in total as now. Relay huts sit in the outskirts. RelayPlacer is reused; CandidateRanking is replaced by the street query. Because positions are known in advance, the Grid Map and the Link step can show them before players visit. |
| **Substations** | A template at a candidate spot | A landmark multibuilding at a fixed chunk per district, with `preventruins`. The Substation Core is a palette char with district NBT. A `PRE_EXPLOSION` listener cancels explosions that touch a landmark. |
| **Districts for the end goal** | 4 OSM polygons in district JSON, tagged in `buildings.json` | The zone JSON above. District membership is `ZoneMap.zoneAt(x, z)`. Grid %, sieges, OPAC claims on Reclaim and spawn reduction all use it. |
| **Grid Map** | Drawn from the real map | Drawn from the zone shapes, with a street overlay from `getChunkInfo` (cached per region) and landmark icons. |
| **Safe zones** | Candidates marked in the atlas | The safehouse, plus each Reclaimed district's claim. The hospital becomes the tier-4 safe zone, as designed. |

**Processing model.** Everything above that changes blocks (strip, damage, relays, cache placement) stays in the existing **first-load chunk pass on the server thread** (`AtlasProcessor`), with the chunk's Lost Cities context added. No worldgen-thread mutation is needed. Lost Cities event listeners are used only for pure decisions: `CHARACTERISTICS` and `PRE_EXPLOSION`. `/ashfall atlas processall` becomes `/ashfall city processall <radius>` and runs after the Chunky pregeneration.

---

## 2. Mods

All entries below are verified on Modrinth for MC 26.3 Fabric (2026-10-25). "Side" is the `pack/mods.json` side. **Use `both` for anything the host needs**, because Open to LAN runs the server inside the host's client, and `build_mrpack.py` excludes `side: server` mods from client installs.

### 2.1 Must-have

| Mod | Slug | Version | Licence | Side | Purpose | Conflicts and config |
|---|---|---|---|---|---|---|
| The Lost Cities | `the-lost-cities` | `xuEuPjKk` (26.3-12.0.0-fabric, release, 2026-10-21) | MIT | **both** (Modrinth says server-required, client-unsupported, but the LAN host needs it) | The generator that replaces Arnis | Needs `fabric-api` and `forge-config-api-port` `JpKvrr9J` (both already pinned). Java 25 or newer and loader 0.19.5 or newer (we pin 0.20.1). Ship the `ashfall` profile and the server toml. `generateSpawners: false`. |
| Lost Cities Modern Tweaks (Lights Off) | `lost-cities-modern-tweaks-nolighting` | `AqUDSjNd` (6.0.0, release, 2026-10-25) | MIT | both | Modern vanilla-block buildings and streets, with street lamps and interior lights removed | **Never** install alongside `lost-cities-modern-tweaks` (`DyFEv6Vt`). Our world style references its city styles and buildings. |
| FerriteCore | `ferrite-core` | `d5ddUdiB` (9.0.0-fabric, release) | MIT | both | Memory. Lost Cities cities are block-state heavy, and DeceasedCraft needed 5–8 GB. | None |

**Pin change:** `chunky` goes from `side: server, optional` to **`side: both`, not optional**. Pregeneration becomes part of setting up a world, and the LAN host must have it.

### 2.2 Recommended

| Mod | Slug | Version | Licence | Side | Purpose (link to end goal) | Conflicts and config |
|---|---|---|---|---|---|---|
| Better Advancements | `better-advancements` | `FQp3HIDr` (0.6.0.81, **beta**) | Don't Be a Jerk | client | A readable "Relight" advancement tab, which is our quest log (FTB Quests isn't on Modrinth) | None |
| Advancement Plaques | `advancement-plaques` | `CWKBEIsY` (1.7.3, release) | CC-BY-NC-ND-4.0 (reference only) | client | Big banners for district Reclaimed and "Asheville Relit" | Needs `iceberg` `zhVGUJyW` (1.4.3.1, release, verified) |
| Down But Not Out | `down-but-not-out` | `cLfXmsBA` (0.5.2+26.3, release) | MIT | both | Co-op revive: downed for 45 s, revive by right-click. Fits the Black Start roles (the runner). | Gametest that a bleed-out still fires the Contagion death hook. Don't combine with First Aid New. |
| Traveler's Backpack | `travelersbackpack` | `SmhjfeWr` (26.3-11.4.1, release) | LGPL-3.0 | both | Space for scavenging runs into the districts | Needs FCAP (pinned). Disable or limit the fluid tanks so they don't bypass Hydration. |
| Jade | `jade` | `71CTWqdE` (26.3.5+fabric, release) | CC-BY-NC-SA-4.0 (reference only) | both | Shows block and mob info. We add a small plugin for Substation Core, Relay and charger status. | None |
| Carry On | `carry-on` | `6kJQXHUF` (2.12.0, release) | LGPL-3.0 | both | Move chests and blocks when setting up a haven | Blacklist `lootr:*`, `ashfall:substation_core`, `ashfall:power_relay` and `ashfall:field_charger` |
| AppleSkin | `appleskin` | `PHjDtQay` (3.0.10+mc26.3, release) | Unlicense | both | Hunger and saturation HUD | None |
| Farmer's Delight Refabricated | `farmers-delight-refabricated` | `dRScyUeE` (26.3-3.6.30, release) | MIT | both | Food and farming depth for havens and Reclaimed districts (the city pump water) | Add its foods to the grocery, restaurant and home loot tables |
| AmbientSounds | `ambientsounds` | `LFUkYKC1` (6.3.11, release) | LGPL-3.0 | client | Atmosphere in a dead city | Needs `creativecore` `7YUEsxpw` (release) |
| Particle Rain | `particle-rain` | `ecVAaY1R` (v4.0.2+26.3-fabric, release) | MIT | client | Rain, mist and fog | None |

### 2.3 Optional

| Mod | Slug | Version | Licence | Side | Purpose | Notes |
|---|---|---|---|---|---|---|
| EnhancedVisuals | `enhancedvisuals` | `qaccmSuj` (1.8.35, release) | LGPL-2.1 | client | Blood and damage screen effects | Needs creativecore |
| Presence Footsteps | `presence-footsteps` | `UUNiN7Tm` (1.14.0+26.3, release) | Polyform Shield | client | Footstep sounds | Reference only |
| Distant Horizons | `distanthorizons` | `TGgEbP9A` (3.3.4-26.3, release) | LGPL-3.0 | client | Ruined skyline at long range (DeceasedCraft ships a Distant Horizons edition) | GPU-heavy. Off by default. |
| Automobility: Unofficial Port | `automobility-unofficial-port` | `jwuGmUu9` (1.3.1+26.3-fabric, release) | MIT | both | Cars for crossing a 3 km city, with fuel as loot | Later: feed headlights and engine noise into Director stimuli |
| Waystones | `waystones` | `UKjvFJAV` (26.3.0.1+fabric-26.3, release) | ARR (reference only) | both | Fast-travel reward between Reclaimed districts | Needs `balm` `MYj31XNG` and `shogi` `9gkJoFhj` (both release). Turn worldgen waystones off and place them only on Reclaim. |
| Pufferfish's Skills | `skills` | `VCmaVWxo` (0.19.2, **beta**) | Custom | both | Scavenger, engineer and marksman trees, like DeceasedCraft's | After M6 |
| JustQuests | `justquests` | `TgsTbMQV` (fabric-26.3-0.4.1, release) | GPL-3.0 | both | Quest-book screen with team quests | Only if the advancement tab proves too thin |

### 2.4 Considered and not added

- **In Control** (`aMi8J70R`, beta, MIT). A spawn engine that overlaps the Director. Its condition ideas are copied into SpawnDirector instead.
- **Biomes O' Plenty, TerraBlender, Biome Replacer** (all beta). Needed only if biome-tag districts are chosen in Decision 1.
- **Homeostatic, Tough As Nails.** Our own Hydration covers this.
- **Universal Graves.** Was left out because it conflicted with Zombified Player carrying the dead player's items. Zombified Player was removed in 10.3.0 (an infected death now drops items normally), so that conflict no longer applies.
- **First Aid New.** Conflicts with the revive mod and Contagion.
- **Hardcore Revival.** ARR; Down But Not Out does the same job.
- **C2ME.** Alpha, and the plan says no C2ME.
- **Zombie Horde, Mutants and Zombies.** Both add spawns outside the Director.
- **Serene Seasons.** Its version number is odd, so it is unverified.
- **Lost Cities Modern Tweaks** (standard edition). It conflicts with the Lights Off edition.
- **Not on 26.3 Fabric:** Lost Souls, Lost Radar, The Hordes, Zombie Awareness, ParCool, Create, Immersive Engineering and FTB Quests. Their roles are covered by the Director, the Field Radio, M8 sieges and the advancement tab.

### 2.5 Remove or retire

None of the 33 pinned mods is Arnis-specific, and none becomes redundant: Undead Nights stays until M8. What retires is the **Arnis toolchain and code**. It moves to `archive/arnis/` rather than being deleted (Decision 5):
- `map/arnis-patch/`, `map/osm/`, `map/out/`, `map/upgrade/`, `map/audit/`
- `tools/map-pipeline.sh`, `tools/upgrade_world.py`, `tools/region_audit.py`
- salvage: `atlas/ArnisProjection` and its test, the `buildings.json` import in `building/BuildingIndex`, `Footprint` and `Unit`, `/ashfall atlas audit|calibrate`
- The **world zip** deliverable. Players now create a world from the pack.
- Plan risks 1 (Arnis alignment) and 2 (Arnis lights) are closed. New risks are listed in §3.4.

---

## 3. Changes to the milestone plan and the end goal

### 3.1 New milestone M1R: City Rework (about 2.5 weeks, before M2)

| Step | Work | Gate |
|---|---|---|
| Spike (3–4 days) | Pin Lost Cities and Lights Off. Create a world with the `ashfall` profile. Check: the FCAP server toml path; `/lost saveprofile` key names; predefined-city overlap; the `profile_overrides` key; the `getLostInfo` cast from event worlds; that Lootr converts Lost Cities containers; that a palette `tag` can set a Lootr chest's `LootTable`; the Modern Tweaks asset ids. Pregenerate radius 1,600 with Chunky. Profile with spark alongside Lithium and ServerCore. | Pregeneration of radius 1,600 in under 30 min on the dev machine. TPS 19+ while walking Downtown. No dropped or broken containers. |
| Zones | Zone JSON, `ZoneMap` (Layer B if needed), world style, 6 city styles, predefined cities | `/ashfall city zone` reports the right zone everywhere. `/lost map` shows the layout. |
| Assets | First batch: safehouse, 4 substations, police station, hospital, Kiln warehouse, pharmacy | Each landmark spawns intact at its fixed chunk on 3 seeds |
| Salvage rework | `BuildingSource` + `LostCityContext`; profile `match.lc_*`; `gen_lc_conditions.py`; ContainerPass reduced; LightStripper logging; DamagePass made zone-aware; RelayPlacer on streets | Loot the test city: pharmacies hold medicine, police hold blaster parts, 6–8 key relays per district |
| Pack | Add the must-have and recommended mods. Chunky to `both`. Overrides for the profile, the toml and the Carry On blacklist. | A fresh `.mrpack` in Prism creates a world that spawns in the safehouse |

### 3.2 Changes to later milestones

| Milestone | Change |
|---|---|
| **M2 Director I** | Populations keyed by Lost Cities building instance instead of OSM id. Spawn-table conditions `zone`, `in_building`, `on_street` and `building`. Zone population and darkness scales. `/ashfall director clear-building` takes the instance key or the block you look at. Spec `director-v1.md` §1 and §5 need updating. |
| **M3 Arms I** | No change. Relay positions now come from RelayPlacer on streets. |
| **M4 Havens** | Crash-site and FEMA templates become Lost Cities `scattered` assets. The safehouse gets its OPAC claim. The Wreck hull is a template at its fixed ridge position. |
| **M4.5 Grid I** | Substations already exist from M1R. Lazy relight replays the logged lamp positions per district. District membership comes from ZoneMap. The Grid Map is drawn from zones and the street overlay. About 0.5 weeks saved. |
| **M8 Hordes** | Siege tables follow DeceasedCraft's shape: a class is unlocked on day N, and each wave has its own drop table. The tier is max(day, Grid). |
| **M9 Special infected** | Bosses are tied to landmark multibuildings: Abomination at `kiln_warehouse`, Patient Zero in the `hospital` deep ward, Juggernaut at the hull. |
| **M10** | Pregeneration guide (Chunky radius 1,600, then `/ashfall city processall`). RAM guidance: host 6–8 GB. Second asset batch: offices, school, mall multibuilding, per-floor loot variants. |

**Net effect on the schedule:** +2.5 weeks for M1R, −0.5 weeks in M4.5. The Arnis fork fallback, the world-upgrade step and world-zip distribution are removed. Total is about +2 weeks.

### 3.3 End-goal changes (`docs/endgame-design.md`)

- **Name and setting.** "Relight Asheville" keeps the name. The city becomes a *fictional, generated* Asheville with the same district names (Decision 2). The real street layout and the French Broad river are gone.
- The River Arts pump-house substation sits on an authored water channel inside its multibuilding.
- §2 and §3: "4 polygons" becomes "4 fixed zones from the zone JSON". "Districts are tagged in `buildings.json`" becomes "district = `ZoneMap.zoneAt`".
- Act 0: "75 relays" stays. They are now placed on streets deterministically, with 6–8 key relays per district.
- §5 "Wild outskirts: the far bank of the French Broad and map edges" becomes "the wasteland ring and the Undiscovered zone beyond 2.6 km".
- §6 New: "atlas pass logs stripped light positions" becomes "LightStripper and the `L` dead-lamp palette log positions". Add "Lost Cities profile, zone JSON, ZoneMap, the first landmark asset batch". Remove the OSM and `buildings.json` dependencies.
- §7: add M1R. Adjust M4.5 as above.
- Unchanged: the Sever/Bind choice, the party vote, Antibiotics fully curing infection after Sever, 4 districts plus the ridge, the Black Start phases, and the siege tier rule.

### 3.4 New risks

| Risk | Mitigation |
|---|---|
| The Lost Cities Fabric port is 4 days old, with a one-line changelog | The version is pinned, the M1R spike tests it, and the source is MIT, so we can patch it if needed |
| Generation lag on weaker hosts | Floors capped at 8, no spawner blocks, vanilla-only parts, a FerriteCore pin, mandatory Chunky pregeneration, and `heightSampleSize` 3 |
| The FCAP server-config default isn't honoured on Fabric | Fall back to selecting the profile from code at server start |
| Lootr doesn't convert Lost Cities chests | Landmark caches use explicit `lootr:chest` palette entries. Shared chests use our own refill system anyway. |
| Modern Tweaks asset ids change | Pinned version, and we vendor any building our city styles depend on |

---

## 4. Decisions for the user (with recommendations)

1. **District layout.** Should districts sit at fixed positions (the ZoneMap), or be scattered at random by biome as in DeceasedCraft? *Recommend fixed positions.* The Grid Map, siege balance, landmarks and the story need known places. Biome districts would add three beta mods (Biomes O' Plenty, TerraBlender, Biome Replacer), and districts could end up missing or duplicated on some seeds.
2. **City identity.** Keep the "Asheville" name and the district names on a generated city, or rename? *Recommend keeping them.* No design or lore rewrite is needed, and only the real geography is lost.
3. **Modern Tweaks.** Depend on the pinned Lights Off mod, or vendor all its JSON into our jar? *Recommend depending on it* and vendoring only the buildings we change. Less to maintain, and the MIT licence keeps both options open.
4. **Revive.** Down But Not Out, none, or First Aid New? *Recommend Down But Not Out.* It is simple, MIT and works server-side, and it fits co-op Black Start roles. First Aid New fights Contagion and Antibiotics.
5. **Arnis work.** Archive it or delete it? *Recommend archiving* to `archive/arnis/`. M1's patch and the processed map are sunk cost, but the `BuildingSource` interface keeps a real-map mode possible later.
6. **Quest UI.** Advancement tab with Better Advancements and Plaques, or JustQuests? *Recommend the advancement tab* as planned (DeceasedCraft's quests are also building-clear advancements). Revisit JustQuests after the M4.5 playtest.
