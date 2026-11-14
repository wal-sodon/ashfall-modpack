# Spec: Hordes I, Breach and siege routes (2026-10-26, plan M8)

See also [hordes-specials-v1.md](hordes-specials-v1.md), which connects this to the special infected (M9).

Plan milestone M8 plus the grid pieces the Phase 3 report left open. Modules: **director** (schedule, hordes, routes, blood) and **arms** (Breach, `BROKEN`, the Grid tier). All numbers are datapack JSON.

## 1. Our horde-night schedule (replaces Undead Nights)

Undead Nights is out of the pack (`pack/mods.json`) and out of the build. The siren reads our schedule through the same `HordeSource` interface (`ScheduledHordeSource`).

File: `data/ashfall/ashfall/horde/schedule.json` (`HordeSchedule`).

| Field | Shipped | Meaning |
|---|---|---|
| `every_nights`, `jitter_nights`, `first_night` | 5, 1, 5 | A horde night every 5 nights from night 5, each moved by up to 1 night. The jitter is a hash of the world seed and the horde's index: fixed per world (the siren knows days ahead, restarts never reshuffle), clamped below half the interval (nights never swap), and never early for the first one (grace holds). |
| `night_start`, `dawn` | 12000, 23000 | The horde window on the day clock. |
| `day_tiers` | days 1/6/11/21/31/46 → tiers I-VI | Same steps as sieges. **Tier = max(day tier, Grid tier).** The Grid tier comes from arms through `HordeHooks.setGridTier(IntSupplier)`: `grid_tier_base` + districts reclaimed. |
| `unlocks` | walker, husk day 1; runner day 3; armored day 7; patient day 12 | DeceasedCraft-style class unlocks. A variant no unlock names is available from day 1. |
| `tiers[].waves[]` | 2-4 waves per tier, 6-16 zombies | Each wave has its own `spawns` table (locked classes are filtered out) and its own `loot_table` (extra drops rolled on death, `HordeLoot`). |
| `spawn` | ring 40-64, spread 4, haven 96, 60 per player | Where waves appear and the per-player horde cap. |
| `breach` | every 7 days from day 7, siren 150 s ahead | Breach events (section 3). |

Loot tables: `data/ashfall/loot_table/horde/wave_common|wave_scrap|wave_salvage.json` (cloth, scrap metal, duct tape, bandages, food, rarely antibiotics).

**Horde nights** (`HordeNights`): from nightfall on a horde day each overworld player gets the tier's waves `delay_seconds` apart. A wave is one group spawned with `addFreshEntity` (`DirectorSpawns`, source `HORDE`) at the edge of the area the player holds: 40-64 blocks from their Bunker Core if they are within 96 of it, else from them; loaded ground only, never in a claim or haven, never in a player's sight. Caps: the Director's 120 per player / 150 per world, plus 60 horde zombies per player. The wave becomes a horde sent after the player (their haven or them).

## 2. Dynamic hordes

File: `data/ashfall/ashfall/horde/hordes.json` (`HordeSettings`).

- **Forming** (`Hordes`, every 40 ticks): AMBIENT and HORDE Director zombies without a target, 3 or more within 8 blocks, become a horde (first one leads). A lone zombie within 8 of a leader joins. Building populations stay home; siege zombies have routes.
- **Merging:** hordes whose leaders are within 16 blocks merge into the bigger one (max 40).
- **Drift:** the leader perceives SOUND, LIGHT and BLOOD at **2x** the stimulus radius (one zombie caps at 1.3x); LIGHT still needs line of sight from the leader, so curtains work against hordes. The horde walks to the strongest stimulus; on a horde night with no stimulus it walks to the player's haven or the player.
- **Moving** (`HordeGoal`, priority 3, between attack 2 and senses 4): the leader walks to the destination; a follower more than 10 blocks from its leader walks back. Inside the leash followers keep their own senses, so a horde spreads over a street.
- Hordes are in memory only; after a restart the zombies regroup on the first update.

**Blood trails** (`BloodTrails`, `StimulusType.BLOOD`, 600-tick life): a player at or below half health leaves a radius-10 BLOOD drop every 40 ticks once they moved 3 blocks. `SenseGoal` treats BLOOD like SOUND and prefers fresh drops, so zombies follow the trail forwards.

## 3. Breach and BROKEN (arms)

- The scheduler picks a random RECLAIMED district with a known core (`BreachHook.targets`, implemented by arms' `GridBreach`), sounds the siren 150 s before nightfall naming it, and at nightfall calls `GridBreach.begin`. Saved state: `data/ashfall_hordes.dat` (`HordeState`: last breach day, warned day, target).
- A Breach is a SIEGE flagged `DistrictRecord.breach` (saved): same waves, routes, core health and bossbar ("Breach"). While it runs the district is not reclaimed (no safe compound, no free power).
- **Core destroyed:** the district goes **BROKEN**: dark again (Grid Lamps in loaded chunks turn back into dead redstone lamps and lose `RELIT`; other relit chunks go dark when they next load), no couplers in the core, links and reward flag kept.
- **Re-repair:** socket `ceil(couplers_required / 2)` Grid Couplers (2 with the shipped 4) and the district goes straight back to RECLAIMED, keeping its first reclaim day; no second reward cache or fanfare.
- **Core standing at dawn:** held, back to RECLAIMED, if players attended the core for the siege presence time; otherwise lost (BROKEN). (Changed from "attended or not" with the Defend trip.)
- Commands: `/ashfall horde status | wave [at <pos>] | breach [district]` (op; `wave at` aims the wave at a place, so a server console with nobody online can send one); `/ashfall grid set <district> broken` sets up a broken district.

## 4. Siege routes

`EntranceFinder` floods outwards from the core over standing spots (step up 1, drop 2, through doors and open gates) until it reaches open sky (heightmap, so a glass roof is still indoors), and picks the exit closest to the core's floor height, so the front door beats a roof hatch. `SiegeRoutes` turns the way out into waypoints (exit, every 6 steps, doors, level changes, the core), cached per core for 30 s. `RouteGoal` (priority 3) walks a zombie leg by leg: from the entrance if it is outside, else from the nearest waypoint. GridSiege assigns it to every wave zombie (and again after a reload). This fixes "some siege zombies stand in the cellar, on other roofs or in the street".

## 5. Tests

- JUnit director: `HordeScheduleTest` (interval, jitter window, order, grace, seed stability, next/is agreement, tier rule, unlocks and per-wave tables, breach days, siren snapshot, the shipped JSON parses with real variants and loot tables), `HordeGroupingTest` (forming, caps, merge and join rules, who bleeds), `StimulusWideQueryTest` (horde reach, blood life).
- JUnit arms: `DistrictLoopTest` breach cases (only from RECLAIMED, lost → BROKEN, half-cost re-repair keeps the day, held at dawn unattended, ordinary sieges still go back to REPAIRED).
- Gametests director `HordeGameTest`: hordes form then merge; a horde hears a sound one zombie can't; a wounded player leaves blood a zombie smells; a zombie is routed through a building's door round an inner wall to its target.
- Gametests arms `BreachGameTest`: lost Breach → BROKEN → one coupler re-repairs; Breach held at dawn; the Grid tier hook; a siege zombie walks in through the substation door and damages the core.

## 6. Left for later

- ~~Special infected (M9) need adding to `unlocks` and the wave tables.~~ Done differently in [hordes-specials-v1.md](hordes-specials-v1.md) section 3: every wave member rolls for a special at siege weights, and a special's `min_day` is its unlock.
- No client art was needed (no new blocks or items).
- Horde-night spawning is not gametested (it needs hidden ground 40+ blocks out); use `/ashfall horde wave` in a playtest.
