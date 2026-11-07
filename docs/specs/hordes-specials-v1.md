# Spec: Hordes and special infected (2026-10-26, plan M8 + M9)

Module: **director**; the Breach and the Grid tier are in **arms**. All numbers are datapack JSON. This spec covers the two milestones together and how they connect. The horde tables and the Breach/BROKEN rules are written out in full in [hordes-v1.md](hordes-v1.md); this spec summarises them and adds the special infected and the wiring between the two.

## 1. Hordes (M8, summary)

- **Our own schedule replaces Undead Nights**, which is removed from the pack and the build. The schedule is `data/ashfall/ashfall/horde/schedule.json` (`HordeSchedule`).
  - A horde night every 5 nights from night 5, moved by up to 1 night. The shift is a hash of the world seed, so it is fixed per world.
  - Tier = max(day tier, Grid tier). Arms supplies the Grid tier through `HordeHooks.setGridTier`.
  - Zombie classes unlock by day: walker and husk day 1, runner day 3, armored day 7, patient day 12.
  - Each wave has its own spawn table and its own extra-drop loot table.
- **Scheduled waves** (`HordeNights`): each wave spawns as one group 40-64 blocks from the player's Bunker Core (or from the player).
  - Spawns never land in a claim or haven, or in a player's sight.
  - The group spawns inside the hard caps (120 per player, 150 per world) plus 60 horde zombies per player, and heads for the haven or the player.
- **Dynamic hordes** (`Hordes`, `HordeGoal`, `horde/hordes.json`):
  - Every 40 ticks, AMBIENT and HORDE Director zombies without a target group up: 3 or more within 8 blocks form a horde, with a leader.
  - Hordes within 16 blocks merge (max 40).
  - The leader senses SOUND, LIGHT and BLOOD at twice the range. Light still needs line of sight.
- **Blood trails** (`BloodTrails`): a player at or below half health leaves BLOOD stimuli that zombies track.
- **Breach** (arms `GridBreach`):
  - Every 7 days the siren names a random RECLAIMED substation 150 s before nightfall.
  - At nightfall the Breach starts. It is a siege flagged `breach`.
  - A lost core makes the district BROKEN; re-repair costs half the couplers.
- **Siege routes** (`EntranceFinder`, `SiegeRoutes`, `RouteGoal`): siege zombies walk in through the building's entrance to the core.

## 2. Special infected (M9)

Six entity types, all extending one base class, `SpecialInfectedEntity` (which extends `Zombie`). They are in `#minecraft:zombies` and `#minecraft:burn_in_daylight`, so the following treat them as zombies with no extra code:
- ZB&B
- the senses goal
- infection on hit
- the zombies-only rule
- respawn protection
- the hordes

The base class blocks babies, chicken jockeys, random gear, reinforcement calls and drowned conversion. Each spawn, spawn eggs included, applies the stats from the kind's file. Matching defaults are also registered in code, because an entity type needs its attributes before any datapack loads.

| Kind | Day | Weight / 1000 | Siege × | Cap per player | What it does |
|---|---|---|---|---|---|
| Crawler | 2 | 30 | 1.5 | 4 | Hitbox 0.7 × 0.8, so it fits under one-block gaps. 12 HP, fast |
| Bloated | 3 | 20 | 1.5 | 3 | On death, a 3.5-block lingering cloud (poison, nausea) tagged `ashfall_infection_cloud`. `InfectionClouds` rolls Ashfall's own infection (10%) once a second for players inside. Its burst is a 16-block SOUND |
| Spitter | 4 | 15 | 2.0 | 3 | `AcidSpit` (a `LlamaSpit` subclass), range 3-16 every 3.5 s: 3 damage, poison, 5% infection on players. Never hurts zombies |
| Screamer | 5 | 8 | 1.5 | 2 | With a target, it shrieks: a 48-block SOUND, and Speed II for 8 s to zombies within 16. 20 s cooldown, saved with the entity |
| Stalker | 6 | 12 | 0.5 | 2 | Invisible at light ≤ 5 while no player is within 5 blocks and it has not just been hurt. Reveals itself with a hiss, a speed burst and +4 damage on its first hit |
| Brute | 8 | 6 | 2.5 | 1 | 60 HP, armour 8, toughness 2, knockback 2, scale 1.6. ZB&B block damage ×2.5 (`BlockDamageCalculatorMixin`). Not a boss, so fortified blocks still hold |

- **Files:** `data/ashfall/ashfall/special_infected/<kind>.json` (`SpecialInfectedRule`).
  - Fields: `weight` (per 1000 Director spawns, not relative), `min_day`, `zone_multiplier` (for example Mission ×2.5 bloated, wasteland ×2.5 brutes, Montford low or 0), `max_alive_per_player` (counted within 64 blocks, times the players there), `siege_multiplier`, `attributes` and `tuning`.
  - A kind at its cap, or one too big for the spot, counts as weight 0 for that roll.
- **Ambient spawns:** `SpawnDirector` asks `SpecialInfected.roll` first, on land only. When no special comes up, the variant table decides as before.
- **Drops:** each kind has `loot_table/entities/<kind>.json`. Arms' `MobDrops` adds an Energy Cell to the brute (50%), the screamer (30%) and the bloated (8%) (balance-v1; was 25% and 20%, bloated none), and Scrap Electronics (5%) to the others. That replaces the Energy Cells that Undead Nights' elite zombie used to drop.
- **Infection:** `infection/stages.json` gives each kind its own bite chance (crawler 0.06 up to bloated/stalker 0.1).
- **Client:** `SpecialInfectedRenderer` is the zombie model with per-kind textures (`textures/entity/special/`, generated by `tools/gen_entity_textures.py`) and an overlay layer. The spawn-egg textures come from `tools/textures/eggs.py`.

## 3. How they connect (Stage 2 integration)

- **Horde nights roll specials.** In `HordeNights.spawnWave`, each wave member first calls `SpecialInfected.roll(..., siege = true)`. On a hit, a special takes that member's place in the wave; otherwise the wave's table picks. Specials therefore get:
  - the HORDE source and the horde cap
  - the wave's loot table
  - a place in the horde chasing the player
  - The special's `min_day` acts as its unlock day, so the schedule's `unlocks` list does not name specials. One file per kind stays the only place that sets when and how often it appears.
- **Sieges and Breaches roll specials.** `GridSiege.spawnWave` calls `SpecialInfected.rollForSiege` for each member, with the cap counted around the core. A Breach is a siege, so Breach waves bring specials the same way.
  - Since the Phase 4.5 playtest the siege tier and the specials' `min_day` read the **day clock** (`HordeSchedule.day`), the same day horde nights and Breaches use. Before, sieges counted elapsed game time, which sleeping does not advance, so a Breach on clock day 11 was rolled as day 1: its day tier was I (Downtown's zone tier still made it III) and no special infected were unlocked.
- **Dynamic hordes take specials.** Ambiently spawned specials are AMBIENT Director zombies, so they group into hordes like walkers.
  - A screamer's 48-block shriek is heard by horde leaders at twice the range, so one screamer can pull a whole horde.
- **The Undead Nights hook is gone.** The specials track rolled a special beside each fresh Undead Nights horde mob. With Undead Nights out of the pack, that hook (`ALLOW_LOAD` listener, pending list) was removed.
- **The Breach uses the real grid.** At start-up arms registers `GridBreach` as the Director's `BreachHook`. The scheduler starts Breaches only through that hook, and only on RECLAIMED districts with a known core. Gametest districts are never picked.

## 4. Tests

- JUnit director:
  - `HordeScheduleTest`, `HordeGroupingTest`, `StimulusWideQueryTest` (see hordes-v1.md)
  - `SpecialInfectedRulesTest`: weights are per mille; min_day, zone and siege multipliers combine; a capped kind is skipped without changing the others; the cap scales with players; the shipped files parse and cover every kind.
- Gametests director:
  - `HordeGameTest` (hordes form then merge; a horde hears further; blood trails; routed through a door)
  - **`specialInfectedJoinHordes`** (new): a HORDE crawler groups with two walkers into one horde.
  - `SpecialInfectedGameTest` (11): every special spawns as a tagged Director zombie; loot tables; the spawn-egg path; brute size and fortified blocks; the bloated cloud and its infection; the screamer shriek; spitter acid and auto-fire; the stalker in dark and light.
- Gametests arms:
  - `BreachGameTest`: lost Breach goes BROKEN and re-repair costs half; held at dawn; the Grid tier hook; door routing.
  - **`directorBreachHookDrivesTheRealGrid`** (new): the registered hook is `GridBreach`, the Director's own path refuses a test district, and a Breach started through the hook is a real grid siege that is held at dawn.

## 5. Left for later

- Horde-night spawning itself is not gametested: it needs hidden ground 40+ blocks out. It was checked headless in the Phase 4.5 playtest with `/ashfall horde wave at <pos>` (see [../phase45-report.md](../phase45-report.md)).
- Building profiles still give `undeadnights:horde_zombie` weight 3. The id no longer resolves, so those rolls fall back to the ordinary variant table, which does no harm. Replacing them with specials would bypass `min_day` and the caps, so it is left as is.
- `MobDrops` still lists `undeadnights:elite_zombie` (harmless without the mod).
- No client screenshots of the specials yet (the Mac screen is locked).
- Special numbers (weights, caps, siege multipliers) are untuned. M10 balance pass.
