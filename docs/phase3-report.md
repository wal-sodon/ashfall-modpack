# Phase 3 report: Grid I district loop, self-playtest (2026-10-26)

Phase 3 is plan milestone M4.5, **Grid I**, the core of the end goal "Relight Asheville". It was built in two parallel tracks (A: grid; B: story, radio, HUD, map, advancements) and merged into main in stage 2. This report covers the stage 3 playtest: headless dedicated-server runs of a whole district loop, the client visual test attempt, what broke, and what was fixed.

**Short version:** a district can now go DARK → REPAIRED → ENERGIZED → SIEGE → RECLAIMED on a real server, and back to REPAIRED when its core falls. The playtest found three bugs that would have broken the end goal in real play. All three are fixed and covered by tests:
1. On some seeds a vanilla village erased the Downtown substation.
2. Siege zombies could never damage a core.
3. Siege waves spawned on rooftops.

## Setup
- **Server:** `.cache/fabric-server` (Fabric Loader 0.20.1, Minecraft 26.3, Java 25, `-Xmx6G`).
  - `pack/mods.json` has not changed since Phase 2, so the Modrinth server jars were kept.
  - The 3 Ashfall jars were refreshed from `modules/*/build/libs`, and `pack/overrides/config/*` was copied in.
- **Nothing was deleted:**
  - The previous Ashfall jars are in `.cache/fabric-server/archive/mods-pre-p3/`.
  - Three configs that the server had rewritten (contagion, lootr-common, zombifiedplayer) are in `.cache/fabric-server/archive/config-pre-p3/`.
  - `server.properties` before the change is in `.cache/fabric-server/archive/server.properties.pre-p3`.
  - The 0.8.0 pack from before this stage is in `archive/dist/Ashfall-0.8.0-pre-p3-playtest.mrpack`.
- **Worlds:** all use seed `ashfall-p3`.
  - `world-p3`: first run. Its Downtown is broken by bug 1 below.
  - `world-p3b`: after the city fix.
  - `world-p3c`: after every fix. This is the clean run.
  - `world-p3probe` and `world-p3probe2`: diagnosis only. The second uses seed `ashfall-phase1`.
  - Afterwards `level-name=world` and `level-seed=ashfall-phase1` were restored. The Phase 1 and Phase 2 worlds were not touched.
- **Session scripts:** run each with `python3 -I tools/server_session.py .cache/fabric-server <script> --timeout 3600`.
  - `tools/sessions/phase3-river-arts.txt`: the whole River Arts loop to RECLAIMED, relight, darkness weight, free charging.
  - `tools/sessions/phase3-downtown-fail.txt`: a Downtown siege lost: the core falls and the district goes back to REPAIRED.
  - `tools/sessions/phase3-river-arts-reach.txt`: a River Arts siege after the fixes, to show the core really takes damage.
- **No player was connected.** Repair (couplers), energize (cells, the 5-minute hold) and relay linking were skipped with `/ashfall grid set`. The gametests cover those steps with a mock player. Everything from the siege on ran for real.

## What works, and how it was verified

### District loop on the dedicated server (world-p3c, headless)
- **Clean boot, no errors or exceptions from Ashfall** in any session.
  - The log shows "Grid settings loaded (4 couplers, 4 cells, 300 s hold)" and "Loaded 6 Grid Map markers".
  - It shows "Planned 24 district key relays" (6 for each district).
- **Finding a core:** `/ashfall grid status` now prints each core's position once its chunk has loaded (new this stage). Before that it prints "core not located yet". The landmark placeholder converts on first load:
  - River Arts: "Substation Core placed at -854, 103, 67".
  - Downtown: 73, 73, -6.
  - South Slope: 106, 73, 810.
  - Mission: 786, 79, 650.
- **River Arts, DARK → RECLAIMED:**
  - **Siege start:** `grid set … repaired`, then `energized`, schedules the siege for nightfall. Setting the clock to 12000 starts it: "District Siege started at river_arts (tier 2)", and "siege surge, 1 security doors forced open".
  - **Damage (after the fixes):**
    - Wave 1 had 8 zombies.
    - Core health went 400 → 394 → 338 within 25 s.
    - Two zombies came through the forced-open door and stood beside the core at (-853.7, 102, 68.7).
  - **Winning:** clearing each wave with `/kill` brings the next one: waves 2/3 and 3/3, 8 each. Then:
    - "river_arts reclaimed (grid 20%, 44 lights relit in loaded chunks)".
    - "security doors closed".
    - Grid status reads "City Grid 20%: 1 of 4 districts reclaimed".
- **What Reclaimed changes, checked on the server:**
  - **Safe compound:** `/ashfall safezone list` shows `ashfall:grid_river_arts` "River Arts District Substation", 9 chunks server-claimed through OPAC. Setting the district back to energized removes it, and reclaiming adds it again (1 → 2 zones).
  - **Darkness spawning −80 %:** `director status` now prints the spawn weight where it runs (new this stage).
    - In River Arts, at -824, 80: "darkness x1.00, modifiers x0.20, effective darkness x0.20".
    - In unlit Downtown: x1.00.
  - **Free charging:** a Field Charger was placed in River Arts outside the compound, with an empty Standard Cell Pack and no cell.
    - Before the reclaim: buffer 0, pack 0.
    - 10 s after the reclaim: buffer 960 and pack 60 (full).
    - The same charger in unlit Downtown stayed at 0.
  - **Lazy relight:** 44 logged lamps were relit in the loaded River Arts chunks on reclaim. Reclaiming again after a set-back relights 0, because those chunks carry the `grid_relit` flag. The replay of lights that load later is covered by `GridGameTest.relightReplaysLoggedLamps`.
  - **Restart:** River Arts was still RECLAIMED, with its core position, after a server restart.
- **Downtown, the failure path:**
  - The siege started at tier 3 (wave 1/4, 10 zombies), and 1 security door was forced open.
  - The core fell on its own, with no admin command: 160/400 after 26 s, destroyed at about 47 s.
  - Log: "downtown siege lost, core destroyed"; chat: "Downtown core destroyed: the siege is lost".
  - Status afterwards: "repaired, cells 0/4, relays linked 6/6". The couplers and links are kept and the cells have to be loaded again, as designed.
  - The doors closed again.

### By gametests on a real 26.3 server
- **Grid (`GridGameTest`, arms):**
  - Couplers repair the core.
  - The energize hold pauses without players, then links.
  - A lost siege returns to REPAIRED; a won siege reclaims.
  - The Field Charger charges free in a reclaimed district.
  - Relight replays the logged lamps.
  - A reclaimed district spawns a fifth as much in the dark.
  - The placeholder core becomes the real core.
  - The coupler crafts without a crystal.
  - New: `siegeForcesSecurityDoorsOpenUntilItEnds`.
- **Story (`StoryGameTest`, track B):** the real grid change grants the Relight advancement, and the radio and map follow the real grid.
- **Totals:**
  - `./gradlew build --console=plain` passes.
  - Gametests: arms 62/62, director 54/54, salvage 20/20.
  - JUnit: arms 44, director 83, salvage 51, no failures.
  - `python3 -I tools/build_mrpack.py --version 0.8.0` wrote `dist/Ashfall-0.8.0.mrpack`: 48 Modrinth mods plus 3 Ashfall jars, with the fixes checked inside the jars.

## Fixed during the playtest
1. **A vanilla village erased the Downtown substation, so Downtown could never be relit.**
   - **What happened:** on seed `ashfall-p3` the Downtown core was never found. Probing showed only bare natural ground above y = 70 across the whole 2×2-chunk footprint, even though `/ashfall city info` planned `substation_downtown_*` there. Chunk (0, 0), the centre of Downtown, was not even city.
   - **Cause:**
     - Lost Cities' `avoidVillages = true` drops city generation wherever a village stands, and for a multibuilding it drops the whole footprint. Seed `ashfall-p3` has a village at the origin.
     - Inside districts, our hooks also left the city decision to Lost Cities' rarity noise and the world style's river ×0.3 multiplier, so district chunks could fall out of the city as well.
     - Seed `ashfall-phase1` (Phase 1) happened to be fine, which is why nobody saw this before. The landmark gametests run in a flat test dimension with no villages.
   - **Fix 1 (salvage `CityZoneRules.forcesCity`, used in `CityMixin`):** every chunk of the four scored districts is city under the `ashfall` profile, whatever the noise, biome or height says. This needs no world, so it also holds for Lost Cities' calls with a null world. The suburbs, wasteland and Montford keep Lost Cities' patchier mix.
   - **Fix 2 (new salvage mixin `ChunkGeneratorStructureMixin`, new tag `ashfall:kept_out_of_districts`):** villages, pillager outposts, mansions, pyramids, igloos and swamp huts do not start in a district or within 6 chunks of one. Lost Cities still avoids villages elsewhere as before. Turning avoidance off was rejected, because it would pile village houses onto city blocks.
   - **Tests:** new JUnit `ZoneMapTest.everyDistrictChunkIsForcedCityButNotTheRest` and `villagesAreKeptOutOfDistrictsAndTheirEdges`.
   - **Server check:** on a fresh world with the same seed (world-p3c):
     - The nearest village is 936 blocks from the origin.
     - Chunk (0, 0) is a Downtown street.
     - The Downtown core converts at 73, 73, -6.
2. **Siege zombies could never damage a core, so a siege could not be lost.**
   - **What happened:** in two River Arts sieges the core stayed at 400/400 the whole time.
   - **Cause:** every control room is behind the landmark kit's `secure_door`, an iron door "shut to zombies", and the building's own wooden doors are closed too. Each zombie's path to the core ended at the nearest reachable point outside the control-room wall, 4.3 blocks away, and the old attack radius was 2.5.
   - **Fix:**
     - **Doors:** new `SiegeDoors`. For the length of the siege the surge holds the non-wooden doors within `core.door_radius` (14) open. Players are told: "The surge has failed the … substation's security doors open. Hold the control room!" The doors close when the siege ends, won or lost.
     - **Reach:** a zombie now counts as attacking when it is within `core.attack_radius` 6.5 blocks of the core horizontally and within `core.attack_height` 3 blocks vertically, which means battering the control room. Zombies on the roof or in the cellar don't count. Both are new numbers in `data/ashfall/ashfall/grid/settings.json`.
   - **Tests:** new gametest `siegeForcesSecurityDoorsOpenUntilItEnds` and JUnit `GridDataTest.coreReachCoversTheControlRoomWallsOnly`, which uses the real positions measured in the playtest.
3. **Siege waves spawned on rooftops.**
   - **What happened:** the spawn scan went from 6 blocks above the core downwards, so it found roofs first. At River Arts the wave stood on the roof right above the core, out of reach, and several fell to their death.
   - **Fix:** the scan now starts at the core's floor height and alternates down and up.
   - **Server check:** on the next run the wave walked in through the door and hit the core.
4. **Admin and headless tooling.** All three are needed to test a district without a player:
   - `/ashfall grid status` shows the core positions.
   - `/ashfall grid damage <district> <hp>` hurts a besieged core through the siege's own loss path.
   - `/ashfall director status` prints the effective darkness weight where it runs.

## Performance (spark, dedicated server, macOS x86_64, -Xmx6G)
| Moment | TPS (5 s / 1 m) | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| River Arts siege start (fresh world, 7×7 chunks just generated) | 20.0 / 14.7 | 2.4 / 13.8 / 70.1 / 1,257 |
| River Arts siege, wave 1 at the core | 20.0 / 20.0 | 2.1 / 2.8 / 13.6 / 39 |
| Downtown siege, 10 zombies at the core | 20.0 / 15.3 | 2.0 / 3.0 / 6.6 / 39 |
| After the sieges | 20.0 / 20.0 | 0.4 / 0.7 / 1.2 / 17 |

A siege costs about 1-2 ms per tick: one entity box query and one door scan per besieged district per second, plus the zombies' own AI. The 1-minute dips and the multi-second maximums come from `forceload` generating new chunks synchronously, which is the same admin-only effect as in Phase 2.

## Client visual test: not run (screen locked)
- `./gradlew :arms:runClientGameTest` was started at 10:30 with the screen locked (`CGSSessionScreenIsLocked = Yes`).
- The client stopped on the loading screen after the unifont load at 10:31:52, and I stopped it at 10:41. `jstack` showed the render thread blocked in a native LWJGL downcall: the same `SDL_GL_SwapWindow` block as in Phases 1 and 2.
- **Still owed, compiled but never viewed:**
  - `StoryVisualClientTest`: the Grid HUD, the Grid Map, the Relay Log and the Field Radio.
  - The Substation Core shot.
  - The Phase 2 `HavensVisualClientTest` shots.
- Re-run with the screen unlocked and review `modules/arms/run/screenshots/p3-*.png` (and `p2-*.png`).

## Known issues
- **Client screenshots are owed** (see above). Nobody has looked at the Grid HUD, the Grid Map, the Relay Log screen or the Substation Core model in game.
- **Not every siege zombie reaches the substation.** Some stand in the cellar, on other roofs or in the street and never attack. The siege is still won by clearing every wave or holding until dawn, so they are only padding. *Fixed in M8: siege routes through the building entrance ([specs/hordes-v1.md](specs/hordes-v1.md) section 4).*
- **Siege numbers are untuned.**
  - A tier-3 wave destroyed an undefended Downtown core in about 47 s. With players defending, that is probably fine, but it should be balanced in a real co-op run.
  - All the numbers are in `grid/settings.json` and `grid/siege.json`.
- **`world-p3` and `world-p3b` keep a Downtown with no substation** (generated before fix 1). They are test worlds only.
  - The same applies to any existing world whose Downtown was generated on an unlucky seed. Already-generated chunks are not rebuilt.
  - The Phase 1 world (seed `ashfall-phase1`) is fine.
- **Forced-open doors are any non-wooden doors within 14 blocks of the core,** so an iron door a player builds near the core is opened during a siege too. This is intended (the surge), but players should know it.
- **Not run headless (no player):** coupler socketing, cell loading, the 5-minute hold, relay linking, the bossbars, the HUD sync and the radio. All of them are covered by gametests.

## NOT built yet
In plan order (`docs/endgame-design.md`, `docs/deceasedcraft-style-plan.md`):
- **M5 Plasma Blades:** Better Combat sweep, charge, crystal wear. The Resonance Crystal and Focusing Lens still only drop and stack. *Built in 0.9.0: [specs/blades-bench-v1.md](specs/blades-bench-v1.md).*
- **M6 Weaponsmith Bench:** Optic, Heat Sink, Regulator/Overcharger, and recipes gated by district. *Built in 0.9.0 (gated at the bench by district): [specs/blades-bench-v1.md](specs/blades-bench-v1.md).*
- **M7 admin GUI** for loot zones and mob drops.
- **M8:** *Built in 0.9.0: [specs/hordes-v1.md](specs/hordes-v1.md).*
  - Dynamic hordes and our own siege schedule. Undead Nights still drives the horde siren.
  - Breach events and the `BROKEN` district state, with re-repair at half cost.
  - Smarter siege pathing.
  - The siege tier rule max(day, grid) is in `siege.json`, but there is no weekly Breach.
- **M9 special infected:** crawler, bloated, screamer, spitter, brute, stalker. *Built in 0.9.0, wired into horde nights, sieges and Breaches: [specs/hordes-specials-v1.md](specs/hordes-specials-v1.md).*
- **M9.5 Black Start:**
  - The ridge climb, Grid Uplink, finale controller, Relay Breakers, Heart pylons and the Choir.
  - The Sever/Bind vote, ending profiles, epilogue book and stats screen.
  - The Grid % stops at 80 until then. `blackStartBonus` and the `asheville_relit` advancement are in place and waiting for it.
- **M10:** a balance and performance pass, a 2-player LAN run through the whole arc, and dedicated-server docs.

## Logs (temporary)
The logs are in `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`:
- `p3c-s1.log`, `p3c-s2.log`, `p3c-s3.log`: the clean run.
- `p3s1.log`, `p3s2.log`, `p3b-s*.log`: the runs before the fixes.
- `p3diag*.log`: the Downtown diagnosis.
- `p3-client.log`: the client gametest that hung.
- `p3-build.log`: the final build.
