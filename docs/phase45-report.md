# Phase 4.5 report: blades, bench, hordes and special infected, self-playtest (2026-10-26)

Phase 4.5 covers plan milestones **M5** (Plasma Blades), **M6** (Weaponsmith Bench and attachments), **M8** (our own hordes, Breach, `BROKEN`, siege routes) and **M9** (six special infected). They were built in three tracks and merged in stage 2 (see [specs/blades-bench-v1.md](specs/blades-bench-v1.md), [specs/hordes-v1.md](specs/hordes-v1.md), [specs/hordes-specials-v1.md](specs/hordes-specials-v1.md)). This report covers the stage 3 playtest: four headless dedicated-server sessions on a fresh world, what broke, and what was fixed.

**Short version:** every special spawns and behaves on a real server, horde waves spawn as one group with specials in them, and a Breach on a reclaimed district breaks it when the core falls. Ashfall logged no errors or exceptions. The playtest found four problems, all now fixed:
1. Horde and siege zombies re-pathed every other tick when their goal could not be reached. Per zombie, a horde cost several times what a plain zombie costs.
2. Sieges and Breaches counted days by game time while horde nights and Breach scheduling used the day clock, so a Breach on clock day 11 was rolled as day 1 with no specials.
3. A Breach started late in the night ran until the next noon instead of ending at dawn.
4. A server with nobody online had no way to send a horde wave, so horde nights could not be checked headless.

## Setup
- **Server:** `.cache/fabric-server` (Fabric Loader 0.20.1, Minecraft 26.3, Java 25, `-Xmx6G`, macOS x86_64).
  - `pack/mods.json` dropped Undead Nights this phase. `python3 -I tools/build_mrpack.py --version 0.9.0 --server-mods <scratch>` was compared with the server's mods folder: Undead Nights was the only removal, and no other jar changed.
  - The 3 Ashfall jars were refreshed from `modules/*/build/libs` (and again after each fix). `pack/overrides/config/*` was copied in.
- **Nothing was deleted:**
  - The Undead Nights jar and the previous Ashfall jars are in `.cache/fabric-server/archive/mods-pre-p45/`.
  - The configs that the override copy replaced are in `.cache/fabric-server/archive/config-pre-p45/`.
  - `server.properties` before the change is in `.cache/fabric-server/archive/server.properties.pre-p45`.
- **World:** `world-p45`, seed `ashfall-p45` (new). Afterwards `level-name=world` and `level-seed=ashfall-phase1` were restored. The Phase 1-3 worlds were not touched.
- **Session scripts** (run each with `python3 -I tools/server_session.py .cache/fabric-server <script> --timeout 3600`; wrap it in `caffeinate -i` on a Mac, see Known issues):
  - `tools/sessions/phase45-specials-hordes-breach.txt`: the six specials downtown at night, three horde waves, a Breach to `BROKEN`, and a 130-zombie stress run.
  - `tools/sessions/phase45-breach-specials.txt`: after fixes 1 and 2, a Breach that now brings specials, and the horde load again.
  - `tools/sessions/phase45-blade-charge.txt`: after fix 3, a late Breach held at dawn, and Plasma Blade charging at a Field Charger.
  - `tools/sessions/phase45-stalker.txt`: the stalker hiding in the dark by itself.
- **No player was connected.** The clock was set to day 11 at midnight (`time set 258000`), so every special and the tier-3 horde table were unlocked. Elapsed game time stayed at day 1, which is how fix 2 was found.

## What works, and how it was verified

### Special infected (session 1)
- `summon ashfall:<kind>` works for all six at night in Downtown. Each spawns with its file's stats:
  - Health: crawler 12, bloated 26, spitter 18, screamer 14, stalker 20, brute 60.
  - The brute's `minecraft:scale` is 1.6.
- All six were still alive and had moved after 30 s, with no errors.
- **Bloated:** killing it leaves its `ashfall_infection_cloud` area cloud.
- **Stalker** (session 4, `tools/sessions/phase45-stalker.txt`): at midnight one stalker sealed in a stone box and one in open air at y 210 both stood at light ≤ 5 (a `location_check` predicate), and both hid by themselves within 5 s (the `entity_properties` `is_invisible` flag). Session 1 had read invisibility from entity NBT; 26.3 does not save it there, so that first check meant nothing. Revealing in light is covered by the gametest `stalkerCannotHideInTheLight`.

### Horde waves (sessions 1 and 2)
- `/ashfall horde status` reads "Day 11: next horde night on day 15, tier 3 (grid tier 1), unlocked: armored, husk, runner, walker, next breach day 14".
- `/ashfall horde wave at 64 80 -16` (new, fix 4) sends tonight's first tier-3 wave, 10 zombies. Each wave spawned as one group 40-64 blocks from the target and out of the claim:
  - Session 1: three waves at (73, 84, -63), (24, 78, -43) and (23, 78, -34) brought 3, 1 and 1 specials. Live counts: 1 crawler, 3 spitters, 1 screamer.
  - Session 2: 1, 0 and 2 specials.
  - The per-player cap works: a fourth wave sent while 12 specials stood near the target brought 0.
- **Grouping:** three waves became 2 hordes as they met (the merge rule). `/ashfall director status` counted "Director zombies loaded: 30/150", the HORDE zombies inside the Director's caps.

### Breach and BROKEN (sessions 1 and 3)
- `/ashfall grid set downtown reclaimed`, then `/ashfall horde breach downtown`, gave:
  - "Grid: Breach at downtown (tier 3)"
  - the siren "Breach / The dead are breaking into the Downtown substation. Hold the core"
  - "1 security doors forced open"
  - wave 1/4 of 10
- **Session 1, the core falls:** the siege routes brought the wave in and the core went 400 → 396 → 280 → 160 → 26 in about 100 s. `grid damage` then finished it through the siege's own loss path.
  - Log: "downtown broken, 29 grid lamps went dark in loaded chunks", "downtown lost its Breach, the district is broken", and the doors closed again.
  - Status afterwards: "Downtown: broken by a Breach, couplers 0/2 to re-repair". That is half of 4, as designed, and it survived a restart.
- **Session 2, specials join Breach waves (after fix 2):** wave 1 had a brute (unlock day 8), which before the fix could not appear until game-time day 8.
- **Session 3, a late Breach held at dawn (after fix 3):** a Breach started at midnight (clock 258000) was still under siege at 262900 and was held at 263100, just after dawn (23000): "downtown held its Breach", "back on the grid", and the doors closed. Status: "reclaimed", Grid 20%.
- **Restart mid-Breach:** session 2 ended with all four waves spawned and then killed. On the next start the Breach was decided as won at once ("downtown held its Breach").

### Plasma Blade at a Field Charger (session 3)
- A new Plasma Blade (no charge) was put in a Field Charger with no cell in reclaimed Downtown (104, 200, -24). After 15 s the charger buffer read 960 and the blade held `team_reborn_energy:energy` 75 of 120. That is free grid charging, with no change to the chargers.
- The control: the same setup with Downtown set back to energized (unlit) gave a buffer of 0 and the blade stayed empty.

### By gametests and JUnit on a real 26.3 server
- `./gradlew build --console=plain` passes (totals below).
- The blade's sweep cap, hunger drain, crystal wear and re-socketing, pylon bonus and chain-lightning safety are covered by `PlasmaBladeGameTest`. The bench's slots, swaps and district gate are covered by `WeaponsmithBenchGameTest`. These need a player, so they were not repeated headless. Totals are in the "Build and package" section below.

## Fixed during the playtest
1. **Hordes and sieges re-pathed every other tick when stuck. Per zombie, a horde cost several times what a plain zombie costs.**
   - **What happened:** 30 horde zombies ran at a median 20.3 ms per tick. 130 persistent zombies (including 12 specials) ran at 14.2 ms, and the baseline with nothing spawned was 4.0 ms.
   - **Cause:** `HordeGoal` and `RouteGoal` re-path when their navigation is done, as well as on a timer. A leader whose destination is inside a building, a follower cut off from its leader, or a siege zombie battering the control-room wall (never within 2.5 blocks of the core) gets a path that ends short and is "done" at once. So it started a new long-range path search every other tick for as long as it lived.
   - **Fix:** both goals keep their timer re-path, but a re-path because the path is done now waits at least 10 ticks (`DONE_REPATH_TICKS`).
   - **Server check:** session 2 measured three horde waves plus a Breach wave (40 zombies) at a median 7.8 ms.
2. **Sieges and Breaches used a different day than horde nights.**
   - **What happened:** a Breach on clock day 11 ran with no specials, even though horde waves the same night had them.
   - **Cause:** `GridSiege` took the day from elapsed game time (`SpawnRules.day(getGameTime())`). The horde schedule, the siren and the Breach days use the day clock (`HordeSchedule.day`). Sleeping or `/time set` moves the clock but not game time, so in a world where players sleep the two drift apart.
   - **Fix:** the siege tier and the specials roll in siege waves now use the day clock (`GridSiege.siegeDay`), the same day as the horde schedule. The reclaim day keeps game time, as before.
   - **Server check:** session 2, a brute in Breach wave 1.
3. **A late Breach ran until the next noon.**
   - **What happened:** a Breach started at midnight was still running after dawn, and its wave 4 spawned at 23500.
   - **Cause:** `DistrictLoop.startBreach` set the siege night to start at the current clock, so it lasted 11000 ticks from whenever it began. A scheduled Breach starts at nightfall, so it was only wrong for admin Breaches and Breaches restarted mid-night. The Javadoc already promised "until dawn".
   - **Fix:** `DistrictLoop.breachNight` anchors a Breach to tonight's nightfall if it is night now, or to the coming night if it is day.
   - **Tests:** new JUnit `DistrictLoopTest.aLateBreachStillEndsAtThisDawn`.
4. **No headless way to send a horde.**
   - `/ashfall horde wave` aims at the calling player, so the console of a server with nobody online could not use it.
   - **Fix:** new `/ashfall horde wave at <pos>` (`HordeNights.spawnWaveAt`). It uses the same ring, sight and claim rules, the same specials roll and the same caps, counted around the place.
   - The horde log line now also counts the specials in each wave.
   - Documented in the README and [specs/hordes-v1.md](specs/hordes-v1.md).

## Performance (spark, dedicated server, macOS x86_64, -Xmx6G, 144 force-loaded city chunks)
| Moment | TPS (5 s / 1 m) | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| Baseline, no zombies (session 2) | 20.0 / 20.0 | 3.0 / 4.0 / 6.3 / 102 |
| 30 horde zombies, 30 s after spawning, **before fix 1** | 20.0 / 20.0 | 11.6 / 20.3 / 27.1 / 36.7 |
| Breach, 10-20 siege zombies at the core, **before fix 1** | 20.0 / 20.0 | 12.7 / 15.4 / 20.7 / 46.2 |
| Stress: 108 zombies + 12 specials + a 10-zombie horde (130), after 60 s | 20.0 / 20.0 | 12.6 / 14.2 / 20.2 / 23.7 |
| Same, after 120 s | 20.0 / 20.0 | 11.9 / 13.2 / 18.1 / 21.7 |
| 30 horde zombies + a 10-zombie Breach wave, 40 s, **after fix 1** | 20.0 / 20.0 | 5.2 / 7.8 / 16.1 / 25.8 |

The server held 20 TPS throughout. The stress run was measured before fix 1, so its horde and siege share is an upper bound. The one-minute maxima of 80-106 ms come from the admin `forceload` and `time set` jumps.

## Client visual test: not run (screen locked)
Client gametests were not started, because the screen is locked (see the Phase 1-3 reports). A new shot, `p45-specials` (all six special infected in a row at dusk), was added to `ArmsVisualClientTest` next to the existing `p45-blade-*` and `p45-bench` shots. **Still owed, compiled but never viewed:** the blade, bench and specials shots, plus the Phase 2 and 3 HUD, map and core shots. Re-run `./gradlew :arms:runClientGameTest` with the screen unlocked and review `modules/arms/run/screenshots/p45-*.png`.

## Known issues
- **The Mac slept during session 1** for about 15 minutes (12:50 to 13:05). The server's monotonic clock paused with it, so no ticks were lost and nothing broke. Later sessions ran under `caffeinate -i`.
- **The Director's day still counts game time.** Ambient spawn caps, the ambient specials' `min_day`, building populations and `/ashfall director status` ("Director: day 1" on clock day 11) use elapsed game time. Horde nights, Breaches and now sieges use the day clock. The two agree in a world where nobody sleeps; with sleeping, ambient difficulty rises more slowly than the horde schedule. Whether ambient difficulty should follow nights survived (the clock) is a design call for the M10 balance pass.
- **Specials and hordes are untuned.** Weights, caps, siege multipliers, wave sizes and the Breach core health (an undefended Downtown core fell in about 100 s to tier 3) all need a real co-op run. The numbers are in `special_infected/*.json`, `horde/*.json` and `grid/*.json`.
- **Ambient specials can stand on rooftops.** `summon … positioned over motion_blocking_no_leaves` put four of six on roofs (y 107-114 against a street at y 84). The Director's own spawns use street-level ground, so this only affects admin summons.
- **Not run headless (no player):** blade swings, the sweep cap, hunger drain and crystal wear; the bench menu; the screamer's shriek (it needs a target); spitter acid on a player; the Breach siren warning before a scheduled nightfall; a real scheduled horde night aimed at players. All are covered by gametests.
- **Undead Nights leftovers** (harmless): building profiles still give `undeadnights:horde_zombie` weight 3, and `MobDrops` still lists `undeadnights:elite_zombie`. See [specs/hordes-specials-v1.md](specs/hordes-specials-v1.md) section 5.

## NOT built yet
In plan order (`docs/endgame-design.md`, `docs/deceasedcraft-style-plan.md`):
- **M7 admin GUI** for loot zones and mob drops.
- **M9.5 Black Start:**
  - The ridge climb, Grid Uplink, finale controller, Relay Breakers, Heart pylons (`ashfall:heart_pylon` is already listed in `#ashfall:crystal_pylons`, so the blade bonus will apply) and the Choir.
  - The Sever/Bind vote, ending profiles, epilogue book and stats screen.
  - The Grid % stops at 80 until then.
- **M10:** a balance and performance pass (including the Director day question above), a 2-player LAN run through the whole arc, client screenshot review, and dedicated-server docs.

## Build and package
- `./gradlew build --console=plain` passes.
  - Gametests: arms 76/76, director 70/70, salvage 20/20.
  - JUnit: arms 61 (including the new `aLateBreachStillEndsAtThisDawn`), director 110, salvage 51, no failures.
- `python3 -I tools/build_mrpack.py --version 0.9.0` wrote `dist/Ashfall-0.9.0.mrpack`: 47 Modrinth mods plus the 3 Ashfall jars, with no Undead Nights. All four fixes were checked inside the packed jars (`DistrictLoop.breachNight`, `HordeGoal.DONE_REPATH_TICKS`, `HordeNights.spawnWaveAt`).
- The server's Ashfall jars are the same build.

## Logs (temporary)
In `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`:
- `p45-s1.log` to `p45-s4.log`: the four sessions.
- `p45-build.log`: the final build.
