# Phase 1 report: the Lost Cities switch, self-playtest (2026-10-26)

Phase 1 is the DeceasedCraft-style plan (`docs/deceasedcraft-style-plan.md`) up to the first landmark batch, merged into main in stage 3. This report covers the stage 4 playtest: a fresh dedicated-server world from the 0.6.0 pack, the client visual tests, what broke and what was fixed.

## Setup
- **Server:** `.cache/fabric-server`, Fabric Loader 0.20.1, Minecraft 26.3, Java 25. The mods are the pack's server-side set from `python3 -I tools/build_mrpack.py --version 0.6.0 --server-mods .cache/fabric-server/mods`: 33 Modrinth jars and the 3 Ashfall jars. The configs are `pack/overrides/config/*`.
- **Nothing was deleted:**
  - The old jars are in `.cache/fabric-server/archive/mods-pre-stage4/`.
  - The overwritten configs are in `archive/config-pre-stage4/`.
  - The Arnis world was renamed to `world-arnis-archive/`.
- **World:** a new `world` with seed `ashfall-phase1` and `level-type` left at `minecraft:normal`. Lost Cities does not need a world preset: `selectedProfile = "ashfall"` in `config/lostcities-server.toml` is enough, and the server log confirms "Building context from The Lost Cities (profile ashfall)". FCAP reads that file from `config/`; nothing is copied into `world/serverconfig/`.
- **Session scripts:** `tools/sessions/phase1-zones.txt` (zones, Lost Cities plan, atlas status) and `tools/sessions/phase1-downtown.txt` (Chunky pregeneration, atlas, midnight stress test). Run them with `python3 -I tools/server_session.py .cache/fabric-server <script> --timeout 5400`.

## What works
- **Clean boot.** All 36 mods load. There are no errors from Ashfall or Lost Cities, and the `ashfall` profile is registered and selected.
- **Fixed zones.** `/ashfall city zone` returns the right zone at every centre in plan §1.2:

  | Point | Zone |
  |---|---|
  | (0, 0) | Downtown (district 3, tier 3) |
  | (-900, 100) | River Arts (district 1) |
  | (100, 800) | South Slope (district 2) |
  | (750, 650) | Mission (district 4; population x1.5, darkness x1.2) |
  | (-150, -800) | Montford (x0.6 for the first 3 days) |
  | (1200, -1200) and (2000, 1500) | Wasteland (population x0, darkness x1.3) |
  | (2300, -100) | Ridge |
  | (3000, 0) | Undiscovered |

- **Lost Cities plans follow the zones.**
  - Chunk (0, 0) is a Downtown street in style `ashfall:downtown`.
  - Chunk (3, -2) holds the predefined `ashfall:substation_downtown` multibuilding.
  - A grid of `/ashfall city info` samples (64-block spacing inside each district disc) found these city chunks:

    | District | City chunks |
    |---|---|
    | Downtown | 120/121 |
    | South Slope | 65/67 |
    | Mission | 67/68 |
    | Montford | 117/120 |
    | River Arts | 61/88 |

    River Arts is lower because the river runs through it.
- **Pregeneration.** Chunky took 9 min 25 s for 5,929 chunks (radius 600 around Downtown), about 10 chunks/s. That works out to roughly 75 minutes for the plan's radius 1,600.
- **Atlas.** `/ashfall atlas processall 600` processed 4,089 chunks in about 4 minutes. It recorded 35 relay candidates and 6 safe candidates, and logged 1,631 stripped lights for the relight.
- **Stress test** (midnight, Downtown, 120 persistent zombies and 5 invulnerable villagers as targets; see the perf numbers below). All 120 zombies were still loaded after 2 minutes, and TPS stayed at 20.
- **Director.** `/ashfall director status` works: "day 1, spawning on", "0/150". Building populations stay at 0 on a server with no players, as designed: populations spawn near players.
- **Client visuals** (116 screenshots reviewed; selection in `docs/screenshots/phase1/`):
  - **Blasters:** all 7 render in first and third person. The HUD charge readout counts down (60 → 54 → 33), "No pack" shows without a Cell Pack, and the charger models render.
  - **Thirst:** the low-thirst HUD shows 3 of 10 drops.
  - **Spawn:** a new player starts inside the Montford safehouse; the tour test asserts this.
  - **Landmarks:** all nine generate where they are pinned. They have no light sources and no ladders below the roof (asserted by the census), and each interior shot shows the intended room.

## Performance (spark, dedicated server, macOS x86_64, -Xmx6G)
| Moment | TPS (5 s / 1 m) | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| Idle after pregeneration | 20.0 / 18.9 | 0.3 / 0.5 / 5.4 / 1,810 (the max is a pregeneration tick) |
| Midnight, before the summon | 20.0 / 17.4 (the 1 m figure still includes processall) | 1.5 / 2.5 / 4.9 / 14.2 |
| 120 zombies, +1 min | 20.0 / 20.0 | 4.9 / 6.2 / 13.2 / 15.7 |
| 120 zombies, +2 min | 20.0 / 20.0 | 4.9 / 8.0 / 10.8 / 15.6 |

The Arnis-era M0 stress test is not directly comparable, because that city was flat. Here, 120 zombies in multi-storey Downtown cost about 6–8 ms per tick, which leaves plenty of headroom. `processall` causes single ticks of up to 3.6 s. That is acceptable for a one-off admin job, but it should not run while players are online.

## Fixed during the playtest
1. **Server crash during pregeneration (watchdog, 60 s tick).**
   - **What happened:** `forceSaplingGrowth = true` in our `lostcities-server.toml` makes Lost Cities grow park saplings from a server-thread todo (`LostCityTerrainFeature.handleTodo` lambda → `ServerLevel.getBlockState`). That call blocked on a neighbour chunk that was still generating, and after 60 s the watchdog killed the server. The crash report is `.cache/fabric-server/crash-reports/crash-2026-10-26_00.44.32-server.txt`.
   - **Fix:** `forceSaplingGrowth = false` in `pack/overrides/config/lostcities-server.toml`, the same as DeceasedCraft. Park saplings now stay saplings until they grow naturally.
   - **Check:** pregeneration then ran clean, twice: radius 600 on the crashed world and radius 300 on a fresh world.
   - **Note:** FCAP rewrites the file when it carries extra comments ("is not correct. Correcting"), so the reason is recorded here rather than in the toml.
2. **The spawn safehouse got a zombie population.**
   - **What happened:** `/ashfall city info` showed the landmarks falling through to the `generic` profile (0.3 zombies per 100 m²), and that included `ashfall:safehouse`, where every new player spawns.
   - **Fix:** a new `safehouse` building profile with no population, no containers and no refill, marked as a safe candidate. The Field Radio cache is placed separately.
   - **Other landmarks:** the four substations now classify as `workshop`, and the Kiln & Co. warehouse as `warehouse`, instead of `generic`.
   - **Tests:** `BuildingProfilesTest` covers all of this; there are now 26 shipped profiles.
3. **The landmark tour's generator "overlook" screenshot pointed into a wall.** The camera was one block from the control-room panel row (`0114_landmark-generator-overlook.png`). `Shot` gained an optional `lift`, and the overlook now stands one block higher at (23.5, 26.5), looking over the panels and through the glazing into the engine hall. The new position is worked out from the voxel model in `tools/landmarks/industrial.py`; it has not been rendered yet (see known issues).

## Known issues
- **Client gametests could not run in this session.** The Mac's screen was locked, so `SDL_GL_SwapWindow` blocked on the loading screen and the client hung for 20 minutes before I stopped it. The screenshots reviewed above come from the last successful run of the same landmark assets and arms code, the p1-assets build at 2026-10-25 23:38, before the stage 3 merge. Re-run `./gradlew :arms:runClientGameTest` with the screen unlocked to confirm the merged tree and the new overlook camera.
- **Districts can land on ocean. FIXED after the report:** salvage now overrides `minecraft:overworld/continents` (`data/minecraft/worldgen/density_function/overworld/continents.json`) with `max(0.5 × continentalness + 0.3, -0.08)`, so the overworld stays inland. Verified on seed `ashfall-landmarks`: before the fix South Slope, Mission and the Ridge were ocean; after it `/locate biome #minecraft:is_ocean` finds none in range of any district, while rivers and mountains still generate. A radius-200 pregen at Mission ran clean. Original note: In the tour test's world (seed `ashfall-landmarks`), the Mission hospital, the generator plant and the Downtown parking deck sit on city platforms surrounded by ocean (see the hospital, generator and parkdeck aerials). Districts have fixed positions but the terrain does not, so some seeds will give island districts. Options for M2: pick biomes or terrain around the origin (for example a land-only climate in the Ashfall world preset), or have Lost Cities' `cityChance` infill always fill water inside district discs.
- **POI mismatch log lines.** The same position appears on a fresh world with the same seed: "POI data mismatch: never registered at (-174, 96, 324)". That is in the cellar of a Modern Tweaks `building5` office in Downtown, and the block there is no longer a POI block. Vanilla repairs this itself and nothing breaks, but something rewrites a POI block during generation without updating the POI table. The likely causes are the Lost Cities ruin or explosion pass, or our atlas container or damage pass. Investigate with a block log before M2.
- **The atlas audit only counts chunks it sees load.** `/ashfall atlas audit` reported 0 loaded chunks after processall and again after `forceload`. It iterates `AtlasProcessor.loadedChunks()`, which apparently does not include chunks that were already loaded or force-loaded when the command ran. This needs a follow-up.
- **Missing "f" in text in client screenshots.** "Cra ting" and "Ion Blaster Ri le" appear in the test client, and vanilla labels are affected too. It is a font rendering quirk of the test environment, not Ashfall's lang files.
- **No player in the server test,** so building populations, darkness spawning and the Montford gentle days were not exercised end to end on the dedicated server. They are covered by gametests (director 34/34).
- **spark** prints "Timed out waiting for world statistics" and logs a startup NPE (`getPlayerList()` is null). Both are harmless and come from spark itself.

## Verification
- `./gradlew build --console=plain` passes. It runs JUnit, including `BuildingProfilesTest` (5/5), plus gametests on a real 26.3 server: arms 41/41, director 34/34 and salvage 15/15.
- `python3 -I tools/build_mrpack.py --version 0.6.0` writes `dist/Ashfall-0.6.0.mrpack` (48 Modrinth mods + 3 Ashfall jars). The test server's Ashfall jars were refreshed from this build.
- Logs from this playtest (temporary) are in `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`: `s1.log` (zones), `s3.log` (pregeneration, atlas and stress test) and `s4.log` (fresh-world check).
