# Phase 6 report: Black Start, endings, admin tools, self-playtest (2026-10-26)

Phase 6 covers plan milestones **M7** (the admin GUI for loot zones and mob drops) and **M9.5** (Black Start: the Wreck hull, the Grid Uplink, the three-phase finale, the Choir, the Sever or Bind vote, ending profiles, the Field Report and epilogue books, and `/ashfall story reset`). The work was done in three tracks (p6-finale, p6-endings, p6-admin) and merged in 0.10.0; see [specs/black-start-v1.md](specs/black-start-v1.md), [specs/endings-v1.md](specs/endings-v1.md) and [specs/admin-gui-v1.md](specs/admin-gui-v1.md). This report covers the stage 3 playtest: five headless dedicated-server sessions on a new world, what broke, and what was fixed.

**Short version:** the whole arc runs on a real server from the console:
- four districts reclaimed, and the hull opens at Grid 80 %;
- the Surge, the Feedback and the Heart, with waves, vents, Relay Breakers, pylons and the Choir;
- the ending vote, both ending profiles, and `/ashfall story reset`;
- loot zones and mob drop overrides.

Ashfall logged no errors or exceptions. The playtest found one real balance bug and four gaps that blocked headless or console use. All are fixed:
1. **Finale waves stopped at 60 mobs for every party size, not the designed 120.** The horde scheduler's per-player cap was counted at the Uplink.
2. **A headless server could not run the finale.** With nobody near, the run paused as abandoned after 20 s, and a Relay Breaker only closes while a player holds it.
3. **Mob drop overrides could only be written from the screen.** There was no console path.
4. **`/ashfall director status` did not show the ending's spawn multiplier.**
5. **Small issues:**
   - The console could close a running vote only by recording an ending for nobody.
   - A zero-voter vote resolved silently.
   - `/ashfall finale start` on a finished world said "already running".

A flaky director gametest also surfaced in the final build and is fixed (see Fixed, item 6).

## Setup
- **Server:** `.cache/fabric-server` (Fabric Loader 0.20.1, Minecraft 26.3, Java 25, `-Xmx6G`, macOS x86_64). `pack/mods.json` has not changed since 0.9.0, so the 47 Modrinth jars stayed as they were.
  - The 3 Ashfall jars were refreshed from `modules/*/build/libs`, and refreshed again after the fixes.
  - `pack/overrides/config/zombies-break-build.toml` was copied in, because the server's copy still listed the removed Undead Nights mobs.
- **Nothing was deleted:**
  - The previous Ashfall jars are in `.cache/fabric-server/archive/mods-pre-p6/`.
  - The old `zombies-break-build.toml` is in `archive/config-pre-p6/`.
  - `server.properties` is in `archive/server.properties.pre-p6`.
- **World:** `world-p6`, seed `ashfall-p6` (new). Afterwards `level-name=world` and `level-seed=ashfall-phase1` were restored. No earlier world was touched.
  - `world-p6/datapacks/ashfall-p6-soak` is a test datapack made for session 2. It is disabled and left in place.
  - `world-p6/datapacks/ashfall_overrides` holds the console-written mob drop override, which was reset and kept as `husk.json.disabled`.
- **Session scripts.** Run each with `caffeinate -i python3 -I tools/server_session.py .cache/fabric-server <script> --timeout 3600`.
  - `tools/sessions/phase6-hull-probe.txt`: generates the ridge and places the hull from the console (`/ashfall site place fixed:ashfall:wreck_hull`, because sites only place near players). The socket lands at **2304 64 -101**.
  - `tools/sessions/phase6-black-start-arc.txt`: the full arc twice (Bind closed from the console, then the Sever default), relays, director status, `/lootzone` and `/mobdrops` from the console.
  - `tools/sessions/phase6-finale-soak.txt`: the finale at its mob cap, with the soak datapack. It was run solo before fix 1 and with 2 stand-in players after it.
  - `tools/sessions/phase6-recheck.txt`: after the fixes, the finished-run message, console `vote` and `set` during a vote, and the reset.
- **No player was connected.** The clock was set to day 11 at midnight (`time set 258000`), as in phase 4.5. Two areas were force-loaded: 121 chunks around the hull and 144 downtown chunks.

## What works, and how it was verified

### Districts and the hull (session 1)
- `/ashfall grid set <district> reclaimed` for River Arts, South Slope and Downtown gave Grid 20 → 40 → 60 %. Downtown relit 131 logged lights in its loaded chunks. `/ashfall finale status` still read "hatch sealed" at 60 %.
- Mission campus brought the Grid to 80 %. 2 s later the log read "Finale: the Grid reached 80%, the Wreck hull unseals" and "Wreck hull hatch opened (4 seal blocks)". Status: "hatch open, Grid 80%". `execute if block 2304 64 -101 ashfall:uplink_socket` passed.

### Presence (session 1)
- `/ashfall finale start` with nobody near ran for 20 s and then logged "Finale: paused (abandoned), surge phase".
- Status read "paused in Surge (nobody held the Wreck)… buffer 41 s". `abort` returned the run to "no Uplink seated".

### The Surge (session 1, unattended solo)
- Wave 1 came at 40 s: 8 zombies (14 × the solo 0.6) at (2272, 69, -142), about 50 blocks from the socket, sent as one horde. A second wave of 8 came at 80 s.
- The buffer drained as designed: the Surge reached 12 % with 16 s of buffer left at 44 s.
- **Undefended, the Uplink fell to wave 1** about 40 s after it spawned: "paused (uplink_down)", with 8 zombies within 4 blocks of the socket. A party defends it in real play. This is noted under Known issues for the balance pass.

### The Feedback (session 1)
- `/ashfall finale phase feedback` raised both Relay Breakers and set Grid 87 %.
- The vents sent one special every 20 s (2 × 0.6 for a solo player). A crawler, a spitter and others were counted with `execute if entity @e[tag=ashfall_finale,type=!minecraft:zombie]`.
- The arc hit the Uplink every 30 s for 12 HP: 200 → 188 → 172 → 156.
- With unattended mode holding both breakers, they closed together **75 s** after the phase began, exactly the hold time. The log read "feedback phase ended, band 2 lit ([downtown]), Grid 94%" and "the Choir rises at 2300, 64, -101".

### The Heart (sessions 1, 3, 4)
- All four pylons stood at their offsets: `execute if block` passed at 2296/2310 × -103/-99.
- The Choir spawned at the socket offset (-4, 0, 0). In session 1 the script's own `kill @e[tag=ashfall_finale]` killed it, because the Choir carries the finale tag. The Choir stays dead, as designed, and the Heart went on.
- Breaking three pylons gave "pylons 3/4 broken". The fourth gave "heart phase ended, band 3 lit ([mission]), Grid 100%" and then "Ending vote started with 0 participant(s)".

### The ending (sessions 1 and 4)
- **Run 1, closed from the console:** `/ashfall ending status` read "The ending vote is running", and `/ashfall ending set bind` recorded "Ending chosen: bind… on day 11".
  - Grid 100 %.
  - Director status: "Story spawn multiplier x1.00: ambient cap 30 per player".
  - A fresh Power Relay outside every district held 480 energy after 3 s. That is its own 8/tick generation, so relays are not free under Bind.
- **`/ashfall story reset`:**
  - Log: "Finale: rearmed by the story reset" and "Story reset (had an ending: true)".
  - Status: "No ending yet", "no Uplink seated", Grid back to 80 %.
- **Run 2, the vote with no voters:**
  - `start`, `phase heart` and the four pylons were run straight through. The vote opened, re-prompted after 30 s, and at 60 s logged "Ending vote decided: SEVER (round 2)". That is the decision-3 default for silence.
  - Director status: "Story spawn multiplier **x0.40**: ambient cap **12** per player".
  - A fresh Power Relay outside every district held **960** after 3 s, double the Bind relay, so Sever's free relays are working.
  - After the reset the multiplier read x1.00 again.
- **Session 4:** `/ashfall ending vote sever` from the console answers "A player is required" (the buttons are for players). `/ashfall ending set sever` closes the vote, and a later `finale start` now says "This world's Black Start is complete; /ashfall story reset rearms it for a replay".

### Loot zones from the console (sessions 1 and 2)
- Two chests and a barrel were placed. `execute positioned 2333 200 -60 run lootzone create p6zone 6 minecraft:chest ashfall_cache:kiln_office regen true delay 1200` gave "Created loot zone 'p6zone' with 2 container(s)". The barrel was correctly left out.
- `info` printed the centre, radius, type, table and refill settings.
- `edit … table ashfall_cache:hospital_supply`, `type any`, `regen false` and `radius 8` each answered "Updated". After `type any` the zone counted 3 containers, so the barrel joined.
- `refill` gave "Refilled 3 container(s)". `list` and `export` ("Exported 1 zone(s) to ashfall/loot_zones.json") worked. `delete` gave "3 loaded container(s) reverted to building rules".
- A zone container keeps its table until it is opened, like vanilla. Its block data held `LootTable: "ashfall_cache:kiln_office"`, a seed and the `ashfall:loot_state` attachment `{table, zone}`.

### Mob drop overrides from the console (session 1, new commands)
- `mobdrops add minecraft:husk minecraft:notanitem 0.5` was refused: "Drop 3: unknown item minecraft:notanitem".
- `mobdrops add minecraft:husk minecraft:emerald 1 2 2 true`:
  - wrote `world-p6/datapacks/ashfall_overrides/data/ashfall/ashfall/mob_drops/minecraft/husk.json`;
  - enabled the override pack and reloaded;
  - answered "Saved 3 drop(s)… datapacks reloaded" only after the reload.
  - `list` showed "minecraft:husk (override): … minecraft:emerald 100.0% x2 (any kill)".
- A husk killed by `/kill` (no player) dropped **2 emeralds**, so the rebuilt loot table works.
- `remove minecraft:husk 9` was refused ("has 3 drop(s)").
- `reset` kept the file as `husk.json.disabled` and reloaded. The next husk dropped no emerald.

## Fixed during the playtest
1. **Finale waves stopped at 60 for every party size.**
   - **What happened:** the solo soak (session 2, waves of 24 every 8 s) stopped spawning at 60 finale mobs. Its third wave was cut to 12. Nothing more came for 3 minutes, against a designed cap of about 120.
   - **Cause:** `HordeNights.spawnWaveAt` applies the schedule's `max_alive_per_player` (60), counted around the place it aims at. Every finale wave aims at the Uplink, so a party of four got the same 60 as one player, and `waves.max_mobs` could never be reached.
   - **Fix:** `spawnWaveAt` takes the horde cap as a parameter (the old signature keeps 60). The finale passes `FinaleLogic.hordeCap`: the per-player cap times the party, never above `max_mobs`. Solo still holds 60, and two or more players reach 120. The Director's hard caps (120 near the place, 150 in the world) still apply on top.
   - **Tests:** new JUnit `FinaleLogicTest.theHordeCapAtTheUplinkGrowsWithThePartyUpToTheFinaleCap`.
   - **Server check:** session 2 with 2 stand-in players. Waves of 32, 32, 32 and 24 reached **120** and held there for 2 minutes. Vents were correctly held back by the finale cap.
2. **The finale could not run without players.**
   - **Fix:** new `/ashfall finale unattended on|off|<players>`. It counts stand-in players at the Uplink and at both breakers, so presence holds, breakers close after their hold, and waves and the horde cap scale with the count.
   - It is kept in memory only (cleared on server stop) and never applies to gametest instances.
   - Documented in the README and [specs/black-start-v1.md](specs/black-start-v1.md).
3. **No console path for mob drop overrides.**
   - **Fix:** new `/mobdrops add <entity> <item> <chance> [<min> <max> [<any_kill>]] | remove <entity> <index> | clear <entity> | reset <entity>`.
   - They go through the screen's own validated `save` and `reset` (now keyed by an actor name instead of a player), and reply after the reload.
   - Documented in the README and [specs/admin-gui-v1.md](specs/admin-gui-v1.md).
4. **Director status did not show the ending's effect on spawning.** It now prints "Story spawn multiplier x… : ambient cap … per player".
5. **The console and the vote:**
   - `/ashfall ending set` during a vote now decides it for the vote's online participants, who get their books and rewards. Before, only the caller was rewarded.
   - A vote that opens with nobody online logs a warning that it will default to Sever in 60 s.
   - `/ashfall finale start` on a COMPLETE run now points at `/ashfall story reset` instead of "already running; abort it first".
6. **Flaky gametest** `SpecialInfectedGameTest.cloudInfectsAPlayerStandingInIt`. It failed once in the final build ("a low roll infects"). The live cloud rolls by itself every second and could infect the player before the test's explicit rolls. The test now clears that infection first. The next full build passed.

## Performance (spark, dedicated server, macOS x86_64, -Xmx6G, 265 force-loaded chunks)
| Moment | TPS (5 s / 1 m) | MSPT min / median / 95th / max (last 10 s) |
|---|---|---|
| Baseline, no mobs (session 2) | 20.0 / 20.0 | 2.4 / 3.1 / 4.8 / 19.1 |
| Default Surge, 16 finale zombies, Uplink down (session 1) | 20.0 / 20.0 | 2.1 / 2.8 / 3.8 / 13.0 |
| Solo soak: 60 finale mobs moving in (session 2, before fix 1) | 20.0 / 20.0 | 4.6 / 6.2 / 24.3 / 67.4 |
| Solo soak: 60 at the Uplink, 60 s later | 20.0 / 20.0 | 3.1 / 3.8 / 6.6 / 8.5 |
| 2-player soak: 96 moving in, 30 s after start | 20.0 / 20.0 | 6.2 / 10.0 / 28.6 / 51.6 |
| 2-player soak: **120 (the cap)**, 60 s | 20.0 / 20.0 | 7.9 / 13.0 / 21.7 / 31.6 |
| Same, 90 s | 20.0 / 20.0 | 7.1 / 11.7 / 17.7 / 23.8 |
| Same, 150 s (crowd at the Uplink) | 20.0 / 20.0 | 4.9 / 7.8 / 11.2 / 12.8 |
| Feedback at the cap (120 + vents held back) | 20.0 / 20.0 | 4.7 / 6.9 / 11.2 / 20.2 |

The server held 20 TPS throughout. The worst 10-second window (120 zombies walking in as four hordes) had a median of 13 ms and a 95th percentile of 29 ms, well inside the 50 ms budget. It eased to 8 ms once the crowd reached the Uplink. The one-minute maxima of 100+ ms come from the admin `forceload`, `time set` and datapack reloads.

## Client visual test: not run (screen locked)
Client gametests were not started, because the screen is locked. The integration added the finale shots to `ArmsVisualClientTest` next to the earlier ones. **Still owed, compiled but never viewed:**
- the finale, Choir and ending shots;
- the Mob Drops and Loot Zone screens;
- the resonant blade;
- the phase 4.5 blade, bench and specials shots;
- the Phase 2 and 3 HUD, map and core shots.

Re-run `./gradlew :arms:runClientGameTest` with the screen unlocked and review `modules/arms/run/screenshots/`.

## Known issues
- **An undefended Uplink falls fast.** One solo wave of 8 knocked it out about 40 s after spawning (2 HP per zombie per second, 200 HP). The arc alone costs 12 HP every 30 s. A real party may find the Surge brutal or fine. The numbers are in `finale/settings.json` (`uplink.*`, `waves.*`).
- **The Mac slept again during session 4** (15:56 to 16:12) under `caffeinate -i` with the screen locked. No ticks were lost and the results were correct. Long sessions should also use `caffeinate -s` or `-d`, or run with the screen unlocked.
- **A zero-voter vote ends the story for nobody.** If every participant is offline when the Heart falls, the vote defaults to Sever after 60 s, and nobody gets the Field Report or the advancement from the ending. The advancement still comes from the finale on next join, and `/ashfall ending epilogue` re-issues the epilogue. The Field Report cannot be re-issued.
- **`/ashfall finale phase` does not relight skipped bands.** It applies their Grid bonus only. This is harmless in a world where the districts are reclaimed (their lights came on at reclaim), and it only affects the admin jump.
- **The Choir carries `ashfall_finale`,** so a broad `kill @e[tag=ashfall_finale]` also kills it. Admins clearing waves should add `type=!ashfall:choir`.
- **Not run headless (no player):**
  - seating a crafted Uplink and feeding cells;
  - Coupler repairs;
  - breaking pylons with a Plasma Blade (the blade bonus);
  - the vote buttons, solo confirmation and majority with real players;
  - the books and the advancement landing in an inventory;
  - the screens.

  All are covered by gametests (arms 94, including `FinaleGameTest.theHeartFallingOpensTheVoteAndTheEndingApplies`, `EndingGameTest` and `MobDropsAdminGameTest`).
- **Ambient and horde days still differ** (see phase 4.5), so the Director status reads day 1 on clock day 11.
- **Relay Breakers on steep ground:** both were raised and both closed, but nobody looked at where they stand.

## NOT built
- **The ending "stats screen"** is the Field Report book, not a GUI screen. No ending cinematic was made.
- **A replay still needs a newly crafted Grid Uplink.** No hand-back on reset (black-start spec section 9).
- **Admin GUI extras** (admin-gui spec section 6): a dimension picker, authoring loot tables (the GUI only picks one), per-zone stats.
- **A console or gametest for `/mobdrops add`.** The session covers it. A second reload gametest would share the reload environment's batch.
- **M10:**
  - a balance pass (finale numbers, Uplink HP, specials, the Director day question);
  - a 2-player LAN run through the whole arc;
  - client screenshot review;
  - dedicated-server docs.

## Build and package
- `./gradlew build --console=plain` passes.
  - Gametests: arms 94/94, director 70/70, salvage 22/22.
  - JUnit: arms 92 (including the new `theHordeCapAtTheUplinkGrowsWithThePartyUpToTheFinaleCap`), director 119, salvage 60, no failures.
- `python3 -I tools/build_mrpack.py --version 0.10.0` wrote `dist/Ashfall-0.10.0.mrpack`: 47 mods, all targeting 26.3 Fabric, plus the 3 Ashfall jars.
  - The packed arms jar is byte-identical to the build output and contains `FinaleLogic.hordeCap`, the new `/mobdrops` commands and the new lang keys.
  - The packed director jar is the one the server ran.

## Logs (temporary)
In `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/`:
- `p6-s0.log` and `p6-s0b.log`: the hull probe.
- `p6-s1.log`: the arc.
- `p6-s2.log` and `p6-s3.log`: the soak, solo and with 2 stand-ins.
- `p6-s4.log`: the recheck.
- `p6-build.log`: the final build.
