# 1.0.0-rc1 report: the final headless arc run (2026-10-26)

This is the release-candidate check after Phase 7 (balance, bugs, docs). One session script,
[`tools/sessions/rc-arc.txt`](../tools/sessions/rc-arc.txt), walks the whole "Relight Asheville" arc on a new world
using op commands only, with no player connected:
- the city and the atlas pass;
- a horde night;
- four district loops, each with a real siege;
- the three bosses;
- the Wreck hull opening at 80 %;
- the Black Start;
- both endings;
- the story reset.

**Short version:** the arc runs from start to finish. Ashfall logged **0 errors**. Its only warnings were the 2
expected "nobody online to vote" notices. The server held **20 TPS** at every sample, including 100 finale mobs during
the Surge. The run found two real bugs and one gap that stopped a headless run. All three are fixed:
1. **The Abomination dropped Mutant Monsters' Hulk Hammer again.** The phase 2 fix was a datapack file that never won on
   a real server.
2. **About half of the Surge waves never hurt the Uplink.** Waves that came from the north stood against the hull wall
   for minutes, 4-5 blocks from the socket.
3. **A headless finale stalled for good once the Uplink went down.** With 2 fixed, the Uplink does go down. With
   stand-in players nobody can feed cells or patch couplers, so there was no way back.

After the fixes the whole script ran again on a fresh world (`world-rc3`) and passed. The mod version is now
**1.0.0-rc1** and the pack is `dist/Ashfall-1.0.0-rc1.mrpack`.

## Setup
- **Server:** `.cache/fabric-server`, as in [phase6-report.md](phase6-report.md). Fabric Loader 0.20.1, Minecraft 26.3,
  Java 25, `-Xmx6G`, macOS x86_64. The 47 Modrinth jars have not changed since 0.9.0.
  - The three Ashfall jars were swapped for `ashfall-*-1.0.0-rc1.jar` and refreshed after each fix.
  - The old 0.1.0 jars are kept in `archive/mods-pre-rc1/`.
- **Each session** ran under `caffeinate -dims python3 -I tools/server_session.py .cache/fabric-server <script>
  --timeout 3600`. The final run used `--timeout 900`. The Mac did not sleep: the log timestamps are continuous.
- **Worlds.** All are new and use seed `ashfall-rc`. Each was given the optional `ashfall_core` datapack before its
  first boot.
  - `world-rc`: the first full run (`rc-s1`) and `tools/sessions/rc-recheck.txt` (`rc-s2`).
  - `world-rc-hull`: two hull probes, `tools/sessions/rc-hull-probe.txt` (`rc-s3`) and a variant run after a
    Juggernaut kill (`rc-s4`).
  - `world-rc2`: the first rerun after fix 2. It stalled in the Feedback phase, which is how gap 3 was found, and was
    stopped cleanly (`rc-s5`).
  - `world-rc3`: **the final full run** (`rc-s6`), with every fix in.
- **Restored afterwards:** `level-name=world` and `level-seed=ashfall-phase1`. The file matches
  `archive/server.properties.pre-rc1` except for its timestamp line. No world was deleted, and no earlier world was
  touched.
- **This seed's calendar.** It was computed from `HordeSchedule.jitter` and confirmed by `/ashfall horde status`.
  - Horde nights fall on days 5, 11, 16 and 19.
  - Breach days are 7, 14 and 21, so the script skips day 7.

## Results (final run, `world-rc3`)

| Step | What was run | Result |
|---|---|---|
| Boot | Fresh world | The `ashfall` Lost Cities profile is registered. "Loaded 30 Relay Logs", 24 district key relays planned, the safehouse safe zone claims 9 chunks. The Director turned off patrols, phantoms and traders on first start |
| Zones | `city zone` at the 8 zone centres | downtown (tier 3), river_arts (2), south_slope (2), mission (4, population x1.5, darkness x1.2), montford (start, x0.6 for 3 days), ridge (5), undiscovered (wild). All correct |
| Landmarks | `city info` at 9 fixed chunks | safehouse, pharmacy, police_station, all 4 substations, kiln_warehouse and hospital, each at its planned chunk. `site list` shows the Wreck hull planned at 2300, -100 |
| Pregen | `chunky radius 400` around 0, 0 | 2601 chunks in 1:07 |
| Atlas | `atlas processall 400` | 1961 city chunks in 18 s. 31 relay candidates, 6 safe candidates, 811 lights logged for the relight |
| Director | `director status` | Day 1, cap 30 per player, multiplier x1.00. It grows with the day: 32 on day 5, 35 on day 10 |
| Horde night | `time set 114000` (day 5, midnight), then 2 × `horde wave at` Downtown | Status reads "horde night now: true", tier 1. Two hordes of 6 (one special), 12 zombies alive. The siren logged "horde (nightfall…)" |
| River Arts (day 6) | `grid set repaired/energized`, siren at 9500, nightfall at 12000 | Siege tier 2, 6 waves of 12. The core took 92 damage in 20 s (1828/1920). Waves were cleared with `/kill`. **RECLAIMED** on day 6: Grid 20 %, 64 lights relit, safe compound claimed, darkness ×0.20 in the district |
| Abomination | `boss spawn`, `/kill` | Defeated. **1 Resonance Crystal**, 2 charged Energy Cells and salvage. **No Hulk Hammer** (it was in run 1; see Fixed, item 1) |
| South Slope (day 8) | Same loop | Tier 2, 6 × 12. **RECLAIMED** on day 8: Grid 40 %, 36 lights |
| Downtown (day 9) | Same loop, with wave 2 left to join wave 1 | Tier 3, 7 waves of 20. 19 alive at 20 s and 35 when wave 2 came on its 120 s timer. The core was at 2209/3200 after about 2 minutes. **RECLAIMED** on day 9: Grid 60 %, 51 lights |
| Hull | Forceload the ridge, `site place fixed:ashfall:wreck_hull` | Placed at 2284, 62, -108 with its socket at **2304, 65, -101**. "Hatch sealed" at 60 % |
| Mission (day 10) | Same loop | Tier 4, 6 waves of 22. **RECLAIMED** on day 10: Grid 80 %, 133 lights, "unsealed ashfall:patient_zero (3 doors)". 2 s later: "the Grid reached 80%, the Wreck hull unseals" and "hatch opened (4 seal blocks)" |
| Patient Zero | `boss spawn` in the opened ward | Defeated. **1 crystal** |
| Juggernaut | `boss spawn` at the hull | Defeated. **1 crystal**. `boss list`: all 3 story lairs defeated (kills 1 each), Ascendant ready |
| Relay, no ending | `setblock … power_relay`, 3 s | 480 energy, its own 8/tick |
| Surge | `finale unattended 4`, `finale start` | Waves of 25 every 30 s. At 60 s: 75 finale mobs, 34 within 7 blocks of the socket, Uplink 895/1200. At 100 s: 100 mobs, 62 near it. The Uplink went down: "paused (uplink_down)". With no cells fed the buffer runs dry at Surge 13 %, as designed |
| Console help | `finale feed 2`, `finale patch 6` (new) | "Fed 2 Energy Cell(s): buffer 90 s", "resumed in the surge phase", "Patched 6: 1200/1200 HP". The Surge moved again (4 % → buffer 70 s) |
| Feedback | `finale phase feedback`, kills every 10 s | Grid 87 %. The vents sent specials. Arcs and one wave took 60 HP and were patched back. Both breakers closed after the 75 s hold: "feedback phase ended", band 2 (Downtown), Grid 94 %. "The Choir rises" with 420 HP |
| Heart | `fill … air replace ashfall:heart_pylon` | 4 pylons broken: "heart phase ended", band 3 (Mission), **Grid 100 %**. "Ending vote started with 0 participant(s)" and the expected warning |
| Ending: Sever | `ending set sever` from the console | "Ending chosen: sever… on day 10". Story objective true. **Multiplier ×0.40** (cap 14). The Choir is gone. A fresh relay holds **960**, which shows Sever's free relays |
| Reset | `finale start` (refused: "complete; story reset rearms it"), then `story reset` | "Finale: rearmed by the story reset; the Grid Uplink waits in its socket". No ending, Grid back to 80 %, multiplier ×1.00 |
| Ending: Bind | `finale start`, `phase heart`, pylons, `ending set bind` | Bind recorded. Relay 480, so Bind has no free relays. Horde status reads tier 5 (grid tier 5). The Ascendant stays "ready" (it is gated 10 days after the Bind) |
| Final reset | `story reset`, `unattended off` | Clean: "No ending yet", Grid 80 %, ×1.00 |

**Crystals:** 3 of 3 bosses each dropped exactly 1 Resonance Crystal. This was true in both full runs.

**Log hygiene, final run:**
- 0 `ERROR` lines in total.
- Ashfall `WARN` lines: 2, both "Ending vote has nobody online to vote…" (by design, once for each vote).
- No exception or stack trace from any `xyz.toesinthesand` class.
- Other mods' warnings are the usual ones: OPAC and LCMT mod-json entries, the carryon and OPAC refmaps, a Create
  class probe, offline mode, voicechat, OPAC's permission and party systems.
- `Missing data pack undeadnights` comes from this test server's own `server.properties` (`initial-enabled-packs`
  still lists the removed mod). It is harmless, and nothing in the pack ships it.

## Performance (spark, final run unless noted)

| Moment | TPS 5 s / 1 m | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| Right after Chunky and `processall` (world generation still settling) | 20.0 / 20.0 | 0.2 / 13.6 / 146.7 / 584.1 |
| Horde night, 12 horde zombies at Downtown | 20.0 / 20.0 | 1.2 / 1.9 / 2.8 / 3.8 |
| River Arts siege, wave 1 (11 alive) | 20.0 / 20.0 | 0.7 / 1.5 / 3.5 / 5.7 |
| South Slope siege, wave 1 (11) | 20.0 / 18.4 * | 0.7 / 1.6 / 3.6 / 6.4 |
| Downtown siege, wave 1 (19) | 20.0 / 20.0 | 0.8 / 1.5 / 3.2 / 6.0 |
| Downtown siege, waves 1 + 2 (35) | 20.0 / 20.0 | 1.0 / 2.2 / 3.6 / 5.2 |
| Mission siege, wave 1 (19), with 121 ridge chunks force-loaded | 20.0 / 19.3 * | 1.3 / 3.5 / 7.3 / 11.0 |
| **Surge, 75 finale mobs (4 stand-ins)** | 20.0 / 20.0 | 1.5 / 3.1 / 5.9 / 7.9 |
| **Surge, 100 finale mobs** | 20.0 / 20.0 | 1.6 / 3.2 / 7.6 / 9.9 |
| Feedback, waves cleared | 20.0 / 20.0 | 0.8 / 1.4 / 1.7 / 4.0 |
| End of session | 20.0 / 20.0 | 1.0 / 1.5 / 1.9 / 5.1 |
| Run 1 (`world-rc`), Surge with 100 mobs piled against the hull wall | 20.0 / 20.0 | 4.0 / 8.8 / 23.9 / 126.6 |

\* The one-minute dips, and the three "Can't keep up" lines (2.2-5.4 s), all came while chunks were generated for a
new force-load: South Slope, the 121-chunk ridge and Mission. They are not caused by mobs. Every 10-second window
during a siege or the finale had a median under 4 ms and a 95th percentile under 8 ms (under 24 ms in run 1), against
the 50 ms budget.

## Fixed during the run
1. **The Abomination dropped the Hulk Hammer** (run 1: "Hulk Hammer has the following entity data"). The phase 2 report
   called this fixed by an empty `data/mutantmonsters/loot_table/entities/mutant_zombie.json` in the arms jar.
   - **Cause:** each mod's data pack is ordered by id. `level.dat` lists `ashfall_arms` before `mutantmonsters`, so
     Mutant Monsters' own table wins. Gametests never saw this because Mutant Monsters isn't on their classpath.
   - **Fix:** `MobDrops` now empties that table in code, with Fabric's `LootTableEvents.REPLACE` on the built-in table.
     It then adds back Ashfall's own rule pools for it (the 35 % player-kill Energy Cell), so nothing else changes. A
     world datapack that replaces the table still wins. The table is listed in `MobDrops.EMPTIED_TABLES`, whose
     Javadoc explains why.
   - **Server check:** `loot spawn … loot mutantmonsters:entities/mutant_zombie` gives "Dropped 0 items". The final
     run's Abomination dropped only Ashfall loot.
2. **North-side Surge waves never hurt the Uplink.**
   - **Run 1:** 100 finale mobs for about 3 minutes, and the Uplink stayed at 1200/1200.
   - **Probes** (`rc-recheck`, `rc-hull-probe`): waves that spawn south of the hull come in through the hatch and
     crowd the socket (24 within 4 blocks after 40 s). Waves from the north end their path against the hull's north
     wall, at z = -105.3. That is 4.3-5.5 blocks from the socket, outside the 3-block `uplink.attack_radius`, and
     they stay there. The first full run got two north waves, which is why it looked like a Juggernaut side effect
     until the probe positions showed the pattern.
   - **Fix:** `finale/settings.json` `uplink.attack_radius` 3 → **6**. The vertical limit stays at 2 blocks.
     This is how the siege core already counts zombies battering its control room wall (`core.attack_radius` 6.5,
     phase 3). The Javadoc on `FinaleSettings.Uplink` and the spec and player guide explain it.
   - **Result:** in the final run 34 of 75 were in range at 60 s and 62 of 100 at 100 s, and the Uplink took real
     damage.
   - **Gameplay consequence** (for the playtest): zombies outside the north wall now hurt the Uplink, and players
     inside the hull can't hit them. Holding only the hatch is no longer enough.
3. **No console path to feed or patch the Uplink.** The first rerun (`world-rc2`) stalled in the Feedback phase,
   paused at "uplink_down". Resuming needs Couplers (600 HP) and buffer, and stand-ins can supply neither.
   - **Fix:** new `/ashfall finale feed [<cells>]` and `/ashfall finale patch [<couplers>]` (1-20 each). They go
     through `FinaleLogic.feedCell` and `patch`, the same caps and resume rules a player gets at the socket, and use
     no items.
   - Documented in [install-and-server.md](install-and-server.md) section 7 (including a troubleshooting row) and
     [specs/black-start-v1.md](specs/black-start-v1.md).
   - `rc-arc.txt` uses them, so the script no longer depends on luck in the Feedback phase.
4. **Packaging:** `tools/build_mrpack.py` packed every `ashfall-*.jar` in `build/libs`. After the version bump that
   would have included the old 0.1.0 jars too, so two copies of each mod would load.
   - It now packs only the jars of the version in `gradle.properties` (exactly 3, or it stops).
   - `--version` defaults to that version.

## Remaining known issues
- **Client visuals since Phase 2 have never been looked at.** The Mac screen was locked through Phases 2-7, so
  `./gradlew :arms:runClientGameTest` was never run. Everything below compiles and is in the client gametests, but
  nobody has seen it:
  - the Phase 2 and 3 HUD, the Grid HUD and the Grid Map (key G), substation core and siege boss bars;
  - the special infected models and textures, Plasma Blades (including the resonant blade), the Weaponsmith Bench
    screen and attachments;
  - the finale (socket light, breakers, pylons, the Choir, phase titles), the ending vote buttons and books;
  - the Loot Zone and Mob Drops admin screens.

  Run it with the screen unlocked and review `modules/arms/run/screenshots/`.
- **Not exercised headless** (gametests cover them, but no real player has done them on a server):
  - seating a crafted Uplink, feeding real cells and patching with real Couplers;
  - Plasma Blade pylon breaks;
  - the vote buttons, solo confirm, majority;
  - books and advancements landing in inventories;
  - ambient Director spawning and building populations (they need a player online);
  - infection, thirst, blasters, the Bench, Breaches (last run in phase 4.5);
  - an actual 2-4 player LAN session.
- **Siege pacing depends on kill speed.** A new wave comes as soon as 4 or fewer are alive. Clearing each wave at
  once finished a whole siege in about 45 s here. A strong party chains the waves quickly, and a weak one meets the
  120 s timer.
- **Surge buffer:** without cells the buffer runs out about 60 s in and the Surge stalls at about 13 %, as designed.
  Players must feed cells, and the playtest should check whether 45 s per cell feels right.
- **`/ashfall finale status` counts 0 participants under `unattended`.** The stand-ins scale waves and caps but are
  not added to the participant list. This is cosmetic and only matters to admins.
- **A zero-voter vote ends the story for nobody** (as in phase 6). Nobody gets a Field Report. The advancement still
  comes from the finale, and `/ashfall ending epilogue` re-issues the epilogue.
- **The Choir carries `ashfall_finale`.** Clear waves with `type=!ashfall:choir` (documented).
- **"0 guards" at the Wreck hull.** The fixed hull site has no guards of its own. The Juggernaut is its lair boss.
- `data/mutantmonsters/loot_table/entities/mutant_zombie.json` is now redundant. It is kept because it is harmless
  wherever its pack does win.

## What a human playtest should focus on
1. **The client look**, all of the list above. This is the biggest unknown in rc1.
2. **The Surge with real players,** now that north-side waves hurt the Uplink:
   - Is defending both the hatch and the outside of the hull fun or frustrating?
   - Are 1200 HP, 200 per Coupler and 45 s per cell right for 1, 2 and 4 players?
3. **District sieges at each party size:** wave sizes ×1.0/1.6/2.3/2.8, core HP by tier, and whether zombies route
   through the forced-open security door the way they do headless.
4. **The ending vote with 2-4 players:** buttons, majority, ties, the solo confirm, the Field Report and epilogue
   books, the "Asheville Relit" advancement.
5. **LAN and e4mc join flow** from the `.mrpack` in Prism, memory with 4 players, and Chunky and `processall` on the
   host.
6. **Early game feel** (days 1-5) with the ambient Director, building populations, dark-and-noise rules and
   thirst. This can't be judged headless.
7. **The Abomination's drops** with a player kill: crystal, cells and salvage, no Hulk Hammer.

## Build and package
- `./gradlew build --console=plain` passes.
  - Gametests: salvage 22/22, director 72/72, arms 97/97.
  - JUnit: salvage 60, director 122, arms 101. That is 283, with 0 failures.
- Versions: `gradle.properties` `version=1.0.0-rc1`, so all three `fabric.mod.json` files read `1.0.0-rc1`.
- `python3 -I tools/build_mrpack.py --version 1.0.0-rc1` wrote **`dist/Ashfall-1.0.0-rc1.mrpack`**.
  - It holds 47 Modrinth mods, all checked against 26.3 Fabric, plus the 3 Ashfall 1.0.0-rc1 jars.
  - The packed jars are byte-identical to the ones the final server run used.

## Logs (temporary)
In `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`:
- `rc-s1.log`: run 1.
- `rc-s2.log`: the recheck.
- `rc-s3.log` and `rc-s4.log`: the hull probes.
- `rc-s5.log`: the stalled rerun.
- `rc-s6.log`: **the final run**.
- `rc-build1.log` to `rc-build4.log`: the builds.
