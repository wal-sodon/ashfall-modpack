# Phase 2 report: havens, infection and bosses, self-playtest (2026-10-26)

Phase 2 is plan milestone M4 (havens, infection and bosses), built in three parallel copies (havens, infection, bosses) and merged into main in stage 2. This report covers the stage 3 playtest: a fresh dedicated-server world from the merged tree, the client visual test attempt, what broke, and what was fixed.

## Setup
- **Server:** `.cache/fabric-server` (Fabric Loader 0.20.1, Minecraft 26.3, Java 25, `-Xmx6G`).
  - `pack/mods.json` has not changed since Phase 1, so the 33 Modrinth server jars were kept.
  - The 3 Ashfall jars were refreshed from `modules/*/build/libs`, and `pack/overrides/config/*` was copied in.
- **Nothing was deleted:**
  - The previous Ashfall jars are in `.cache/fabric-server/archive/mods-pre-p2/`.
  - Four configs that differed from the pack (contagion, lootr-common, mutantmonsters-common, zombifiedplayer) are in `.cache/fabric-server/archive/config-pre-p2/`.
  - `server.properties` before the change is in `.cache/fabric-server/archive/server.properties.pre-p2`.
  - The 0.7.0 pack from before this stage is in `archive/dist/Ashfall-0.7.0-pre-p2-playtest.mrpack`.
- **World:** a new `world-p2` with seed `ashfall-p2`. Afterwards, `level-name=world` and `level-seed=ashfall-phase1` were restored. The Phase 1 world is untouched.
- **Session scripts:** run each with `python3 -I tools/server_session.py .cache/fabric-server <script> --timeout 3600`.
  - `tools/sessions/phase2-havens-bosses.txt`: safe zones, all three bosses, crash-site plan, TPS.
  - `tools/sessions/phase2-siren-sites.txt`: boss drops on solid ground, siren across an Undead Nights schedule.
  - `tools/sessions/phase2-site-place.txt`: console placement of an unvisited crash site.
- **No player was connected.** Every check below is a console command or a gametest. That limits what a dedicated-server session can prove, and the known issues say where.

## What works, and how it was verified

### On the dedicated server (headless)
- **Clean boot.** All 36 mods load, the `ashfall` Lost Cities profile is selected, and there are no errors or exceptions from Ashfall in any of the three sessions. The Phase 1 "POI data mismatch" line did not appear.
- **Safe zones.**
  - `/ashfall safezone list` shows `ashfall:montford_safehouse` ("Montford Safehouse", chunk -10, -50, radius 1).
  - At startup the log shows "server-claimed 9 chunks" through OPAC.
  - `/ashfall city zone` at the safehouse returns Montford, tier 1, with x0.6 population for the first 3 days.
- **Director next to the safehouse.**
  - Setup: the safehouse's 3x3 chunks and a 4x4-chunk Downtown block were force-loaded, then a night was run (time 18000, 60 s).
  - Result: both areas had 0 zombies, and `/ashfall director status` reported 0/150.
  - Caveat: with no player online the Director and vanilla spawning do nothing anywhere, so this only shows that nothing misfires. The real exclusion is covered by the gametest below.
- **Bosses** (`/ashfall boss list|spawn|unseal`):
  - `list` starts with 3 lairs: Abomination ready/open, Juggernaut ready/open, Patient Zero ready/**sealed**.
  - **Abomination:** spawns on its Kiln & Co. warehouse marker at (-945, 120, 124) as `mutantmonsters:mutant_zombie`, with 300 max health from the lair JSON.
  - **Patient Zero:** `unseal` opens the deep ward ("3 doors opened"). It then spawns on its hospital marker at (727, 60, 649) with 260 health. Its summon only runs while it has a target, so with no player it never ran on the server. It is now covered by a new gametest (see Fixed, item 4). The glow is only set from the lair JSON; the client shot `p2-boss-patient-zero` will show it.
  - **Juggernaut:** in session 1 it spawned at the hull's boss marker, but that was the hull buried by the bug fixed below. In session 2 it was spawned with `here` on the ridge surface at (2400, 66, 0), with 360 health.
  - **Kills:** `/kill` on each boss is recorded: status `defeated`, kills +1, and "Boss lair … defeated" in the log. The Abomination's Mutant Monsters death animation delays this by about 5 s.
  - **Resonance Crystal: confirmed for all three.** Each boss dropped exactly one `ashfall:resonance_crystal`, checked with `execute as @e[type=item,…] run data get entity @s Item`. Each also dropped its loot-table extras:
    - Patient Zero: Antibiotics, Energy Cells, Arc Blaster Magnum, Scrap Electronics.
    - Juggernaut: Storm Blaster Repeater, Focusing Lens, Energy Cells, Netherite Scrap.
    - Abomination: Energy Cells and iron, plus Mutant Monsters' own Hulk Hammer (see known issues).
- **Crash sites.** `/ashfall site list 640` at the ridge (2300, -100) lists the fixed Wreck hull and 14 planned ridge crash sites (small, pod and debris templates). `/ashfall site place crash:ashfall:ridge:114,-5`, run from the console, placed a `crash_site_small` at (1827, 63, -77) on the ridge surface, with 1 loot container and 2 guards owed. A second `place` is refused ("already placed").
- **Horde siren with Undead Nights** (horde every 5 days). The clock was stepped through dusk each day:
  - Day 4 at 9500: "Horde siren: warning (nightfall in 2492 ticks)".
  - At nightfall: "Horde siren: horde", meaning Undead Nights really started its horde night.
  - The next day: "idle".
  - Undead Nights has no command to force a horde night (its commands are status, difficulty, spawn_horde, horde mobs and set default horde, from the jar), so stepping the clock is the only way to test this. No player was online, so the boss bar, titles and sound were not seen; the gametest covers who receives them.

### By gametests on a real 26.3 server (these need a player, and the gametest framework provides a mock one)
- **Havens (`HavenGameTest`):**
  - `montfordSafehouseIsASafeZone`
  - `noDirectorSpawnsOrBreakingInASafeZone`
  - `bunkerCorePlacementCreatesTheExclusion`
  - `respawnProtectionTimerAndCancelOnAttack`
  - `sirenFollowsAFakeHordeSchedule`
- **Infection (`InfectionGameTest`):**
  - `stagesProgressOverSimulatedTime`, `symptomsFollowTheStage`, `untreatedStageFourKills`
  - `antibioticsKnockBackAStageAndCureStageOne`, `bandageHealsAndPausesProgress`
  - `severEndingLetsAntibioticsCureAnyStage`: the story-flag hook. It is off by default; `/ashfall story sever|reset` sets it.
  - `zombieHitChanceFollowsVariant`, `onlyInfectedDeathsRise`, `infectionCommandGetSetClear`
  - `/ashfall infection set` needs a player, so it was not run on the dedicated server.
- **Bosses (`BossLairGameTest`, arms `BossDropsGameTest`):**
  - Spawn once, then stay dead.
  - Respawn after the ending only when a rule allows it.
  - The sealed lair waits for unseal and protects its door.
  - The landmark lair stands on its marker.
  - Guaranteed drops fall on any death.
  - New this stage: `summonCallsMinionsUpToTheCap`.
- **Risen players cannot fire powered weapons:** `GunLogic` allows players only (arms gametests).
- **Totals:**
  - `./gradlew build --console=plain` passes. Gametests: arms 46/46, director 54/54, salvage 19/19. JUnit: arms 19, director 83, salvage 49, no failures.
  - `python3 -I tools/build_mrpack.py --version 0.7.0` writes `dist/Ashfall-0.7.0.mrpack`: 48 Modrinth mods plus 3 Ashfall jars, with all 26.3 pins and dependencies checked.

## Fixed during the playtest
1. **Command-placed sites were buried at bedrock.**
   - **What happened:** `/ashfall site place fixed:ashfall:wreck_hull` put the hull at y = -67. The bottom layer of the world was replaced by the hull's air cells. When the Juggernaut was then killed there, its drops fell into the void, which is why the first drop check found nothing.
   - **Cause:** `Sites.place` reads the surface with `Level#getHeight`. Javap of 26.3 shows that this returns the world's minimum Y when `hasChunk` is false, and the footprint had not been loaded yet. The lazy placer used in play only calls `place` once the footprint is loaded, so worlds visited by players were not affected. The command path and `placeFixed`, whose Javadoc promised to load chunks, were.
   - **Fix:** `Sites.place` loads every footprint chunk before it reads the heightmap. For the lazy placer this does nothing.
   - **Test:** new gametest `CrashSiteGameTest#placeFindsTheSurfaceInUnloadedChunks`. It places a site at an unloaded column and asserts that the site is on the ground rather than at the world floor. Against the old code it fails with "site surface -64 is at the world floor -64"; with the fix it passes.
   - **Check on the server:** the ridge crash site placed from the console landed at y = 63 on the slope.
2. **New admin command `/ashfall site list [radius]|place <key>`** (salvage, `site/SiteCommand.java`, permission 2).
   - **Why:** sites were placed only near a player, so on a fresh server, or after a failed placement, there was no way to see the planned crash sites or to place one. The ridge could not be checked headless at all.
   - **list:** placed sites, fixed sites and planned crash sites near the caller, each with its key and whether it is placed.
   - **place:** places a site now and never places a key twice. A crash key (`crash:<rule>:<cx>,<cz>`) is looked up at its own chunk. The first version searched only within 2,048 blocks of the caller, and the console stands at the Montford spawn, about 2,110 blocks from the ridge site tested.
3. **The siren logs its phase changes** (`Horde siren: warning|horde|idle`, at INFO, once per change). Without this, an admin could not tell from the log that the siren fired, and neither could a headless test.

4. **Patient Zero's summon had no test.** It needs a target, so the headless session could not reach it. New gametest `BossLairGameTest#summonCallsMinionsUpToTheCap` uses the shipped `patient_zero` numbers, with only the radius shrunk to fit the test floor. Each call brings `count` tagged patients, calls stop adding at `max_alive`, and one more call adds none. It passes; no code change was needed.

## Performance (spark, dedicated server, macOS x86_64, -Xmx6G)
| Moment | TPS (5 s / 1 m) | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| Just after the Abomination spawned (fresh world; forceloads just generated chunks) | 19.98 / 15.89 | 1.1 / 1.6 / 3.3 / 9,807 |
| End of session 1 (right after `site place` and `forceload` generated the hull area) | 20.0 / 14.03 | 2.0 / 4.1 / 23.0 / 8,935 |
| End of session 2 (siren run, nothing force-loaded) | 20.0 / 20.0 | 0.2 / 0.3 / 1.5 / 14.1 |

Steady-state ticks are cheap. Bosses tick their ability loop every few ticks, with particles, the bar and the summon timer, and this does not show at all. The multi-second ticks come from admin commands that generate new chunks synchronously (`forceload add`, and `site place` now that it loads its footprint). That is expected for one-off admin jobs, but it should not be done while players are online. Players never trigger this path, because lazy placement waits until the chunks are loaded.

## Client visual test: not run (screen locked)
- `./gradlew :arms:runClientGameTest` was started at 06:34. The client stayed on the loading screen, and its last log line was the unifont load at 06:35:00. I stopped it after 10 minutes.
- `jstack` showed the render thread inside a native LWJGL downcall. This is the same `SDL_GL_SwapWindow` block as in Phase 1, and macOS reported `CGSSessionScreenIsLocked = true`.
- **New, compiled but not yet run:** `HavensVisualClientTest` (arms gametest, registered in its `fabric.mod.json`). It saves `p2-infection-stage3`, `p2-bunker-core`, `p2-death-screen`, `p2-respawn-choice`, `p2-boss-patient-zero` and `p2-boss-juggernaut`.
- **Already exists:** the crash-site shot is `wreck-crash-site` in `WreckSitesClientTest`.
- No screenshots were reviewed this stage. Re-run the task with the screen unlocked and review `modules/arms/run/screenshots/p2-*.png` and `wreck-*.png`.

## Known issues
- **Client screenshots are still owed** (see above). This is the only Phase 2 surface nobody has looked at: the infection HUD, the chat respawn prompt, the Bunker Core model, and the bosses' glow, particles and bars.
- **Abomination drops Mutant Monsters' Hulk Hammer.** The mutant zombie's own loot table runs alongside ours. The Hulk Hammer is a strong melee weapon, so it may undercut Plasma Blades (M5). To drop it, override `mutantmonsters:entities/mutant_zombie` in the datapack or clear drops in `BossLairs.drop`. **Fixed after the report:** arms overrides `mutantmonsters:entities/mutant_zombie` with an empty table, so the boss drops only Ashfall loot (crystal guaranteed, plus the 35% Energy Cell).
- **A boss spawned into chunks that were force-loaded seconds earlier is invisible to selectors for about 10 s.** The entity sections were still loading. `boss list` showed "health -" and `/kill @e[tag=ashfall_boss]` found nothing until they had loaded; the boss was there all along. This only affects admin testing, because in play a player's presence has already loaded the lair.
- **`world-p2` keeps a hull buried at bedrock** from before the fix. SiteState records it as placed, so it is never placed again. That world is a test world only. There is no `site forget` command yet; add one if a real world ever needs a site re-placed.
- **`/undeadnights status` prints nothing to the server console** (it only answers players), so the siren run used our own log line instead.
- **With no player online, the dedicated server cannot exercise** Director spawning near the safehouse, the respawn prompt, infection progression or boss triggers by proximity. All of them are covered by the gametests listed above.
- spark's "Timed out waiting for world statistics" and its startup NPE (`getPlayerList()` is null) are harmless and come from spark itself, as in Phase 1.

## NOT built yet
In plan order (`/Users/wal/.claude/plans/pasted-content-id-966b-create-a-fuzzy-scott.md`, `docs/endgame-design.md`):
- **M4.5 Grid I**, the core of the end goal "Relight Asheville". None of it exists yet:
  - Substation Core block, Grid Couplers, grid SavedData and Grid %.
  - The district loop (repair, energize, link, siege) and Reclaimed districts: OPAC claim, darkness -80%, cell-free relays, lazy relight from the atlas light log, reward caches.
  - The Field Radio item and Relay Logs, the "Relight" advancement tab, and the Grid Map/HUD.
  - What does exist: the four substation landmarks, the relay positions and the logged lamp positions. Resonance Crystal and Focusing Lens are placeholder items that drop and stack but do nothing yet.
- **M5 Plasma Blades:** Better Combat sweep, charge, crystal wear.
- **M6 Weaponsmith Bench:** Optic, Heat Sink, Regulator/Overcharger, district-gated recipes.
- **M7 admin GUI** for loot zones and mob drops.
- **M8 dynamic hordes and our own siege schedule.** Undead Nights is still the interim scheduler; the siren reads it through `HordeSource`, so swapping it out is contained. The rule Siege tier = max(day, Grid) and Breach events are not built either.
- **M9 special infected:** GeckoLib crawler, bloated, screamer, spitter, brute, stalker. The bosses are a vanilla-zombie variant plus Mutant Monsters for now.
- **M9.5 Black Start:**
  - Grid Uplink, finale controller, Relay Breakers, Heart pylons, the Choir.
  - Sever/Bind ending profiles, party vote, epilogue book, stats screen and the "Asheville Relit" advancement.
  - Only the story flag exists: `/ashfall story sever|reset`, which today switches Antibiotics to a full cure.
- **M10:** balance and performance pass, a 2-player LAN run through the whole arc, dedicated-server docs.
- **From the plan's M4 line, still open:** light burnout tuning and FEMA site templates. (Electric-light recipes are done: lanterns and redstone lamps need Scrap Electronics.)

## Logs (temporary)
The logs are in `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`:
- `p2s1.log`: havens, bosses, crash-site plan.
- `p2s2.log`: siren and drops.
- `p2s3.log`: crash-site placement.
- `client.log`: the client gametest that hung.
- `build5.log`: the final build.
