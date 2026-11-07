# Balance v1: sieges, the Black Start, cells and the Director's day (phase 7)

This is the first balance pass over the whole "Relight Asheville" arc. It is aimed at singleplayer and 2-4 player co-op. The numbers come from an offline simulation, `tools/balance/sim.py`. It is pure Python, reads the shipped datapack JSON, and gives every table below:

```sh
python3 -I tools/balance/sim.py                       # this tree (after)
python3 -I tools/balance/sim.py --root <old checkout>  # before
python3 -I tools/balance/sim.py --runs 200 --only siege,finale
```

The tables here come from `--runs 60 --seed 2026`. "Before" is the 0.10.0 tree and "after" is this one.

**Short version**
- **An undefended core falls about 2.5 minutes after the first zombie reaches it, in every tier.** Before, it fell in 17-37 s.
- **Act-gear parties now lose about 10-35 % of the core in a siege.** Before, they lost 0 % in 25 of 30 cases.
- **Siege waves now grow with the number of players at the core.** Before, four players faced the same siege as one.
- **The finale needs about 14 extra Energy Cells solo, 17 for two players and 20 for four.** Before, it needed 8-11. An undefended Uplink lasts about a minute instead of 20 s.
- **Fighting specials pays back some cells.** A siege now returns roughly a quarter to a half of the 4 cells it takes to energize the core.
- **Two code changes fall outside the JSON:**
  - Every difficulty rule now counts days on the same clock.
  - `/ashfall story reset` leaves the Grid Uplink in its socket, and `/ashfall finale phase` relights the bands it skips.

## 1. What the simulation models (and what it doesn't)

| Piece | Model |
|---|---|
| Enemies | HP, armour, toughness and speed come from `zombie_variant/*.json` and `special_infected/*.json`. Zombies keep their 2 natural armour. The armoured variant's expected gear is worked out from its equipment sets. The Choir uses `finale/settings.json`. |
| Movement | Speed attribute × 10 blocks/s, the convention blaster-balance-v2 uses (walker 2.3, runner 3.0). Siege zombies walk 1.4× the straight-line distance (the route through the door). Finale zombies walk 1.3× the 40-64-block ring. |
| Guns | `energy_gun/*.json` plus attachments (Heat Sink on mid and late gear), simulated shot by shot: rpm rounded to whole ticks, heat and lockout as in `HeatModel` (locked guns cool at 0.8×), and the 26.3 armour formula. Damage is the shipped v1 numbers. blaster-balance-v2 is a design that has not been implemented (see section 7). |
| Players | They stand at the objective and shoot the nearest zombie they can see: within 16 blocks of path in a substation, 32 on the ridge. 75 % of shots hit (88 % against a zombie already at the core). 20 % of hits are headshots. Players feather the trigger instead of locking the gun unless something is battering the objective. They spend 35 % of each minute not shooting (moving, healing, looking for targets). An empty Cell Pack (30/60/120) costs 1.5 s to swap. One late player carries a Plasma Blade: 9 damage, 3 targets with 60 % after the first, 1.6 swings/s. |
| Rules | The siege wave rules from `GridSiege`: a new wave when at most N are alive or after the interval, damage per zombie-second within the attack radius, integer damage per second, and the Director's 120-zombie cap. The finale follows `FinaleController`: buffer, cells fed below 15 s, a coupler patch below 55 % HP, a pause and its 60 s setback, breakers (solo holds them one after the other at half effectiveness against the Uplink), vents, arcs, 10 s per pylon with the blade, and the Choir killed before the last pylons. |
| Left out | Players getting hurt or dying, screamer speed buffs, bloated clouds, spitter range, stalkers hiding (only a 0.6× hit chance), doors and walls, and zombies picking a player over the objective. A real fight is messier than this model. Read the tables as relative numbers for comparing tunings, not as promises. |

Typical gear by act: **early** is Volt (solo) or Volt + Spark. **Mid** is Pulse (solo) or Pulse + Ion, with Heat Sinks once South Slope is lit. **Late** is Storm + blade (solo) or Storm + blade and Lancer, with Heat Sinks. The siege tier for each district follows the act table and an ordinary pace: River Arts and South Slope at tier II (days 7 and 10), Downtown at III (day 14), Mission at IV (day 20), and a late Breach at V (day 28). The finale is on day 25 at horde tier V (grid tier 1 + 4).

## 2. Time to kill (unchanged numbers, for reference)

Field conditions: 75 % hits and 20 % headshots, measured from the first shot. The Lancer+HS and Storm+HS columns were
re-run for 10.2.2 (Storm 400 rpm, 2 energy, 7 heat, cool 24; Lancer cool 22); the rest are the phase 7 numbers.

| Enemy (HP/armour) | Spark | Volt | Pulse+HS | Ion+HS | Arc | Lancer+HS | Storm+HS | Blade |
|---|---|---|---|---|---|---|---|---|
| walker (20/2) | 1.2 s | 0.9 s | 0.6 s | 0.7 s | 1.1 s | 0.5 s | 0.5 s | 1.2 s |
| runner (14/2) | 0.7 s | 0.6 s | 0.5 s | 0.4 s | 0.9 s | 0.5 s | 0.4 s | 0.6 s |
| armored (20/4.8) | 1.2 s | 0.9 s | 0.7 s | 0.8 s | 1.0 s | 0.5 s | 0.6 s | 1.2 s |
| crawler (12/2) | 0.9 s | 0.8 s | 0.4 s | 0.5 s | 0.4 s | 0.8 s | 0.5 s | 0.6 s |
| bloated (26/2) | 1.5 s | 1.2 s | 0.8 s | 0.9 s | 1.1 s | 0.4 s | 0.7 s | 1.2 s |
| stalker (20/2) | 2.9 s | 1.9 s | 1.1 s | 1.4 s | 3.3 s | 1.8 s | 1.0 s | 1.2 s |
| brute (60/8, tough 2) | 8.9 s | 5.8 s | 2.1 s | 2.5 s | 4.9 s | 3.3 s | 1.9 s | 5.0 s |
| Choir (420/10, tough 4) | 84 s | 54 s | 22 s | 28 s | 63 s | 28 s | 21 s | 40 s |

Sustained DPS on a walker without locking (10.2.2): Spark 8.2, Volt 11.8, Pulse+HS 24.9, Ion+HS 20.6, Arc 8.1, Lancer+HS 15.8, Storm+HS 26.3. Before 10.2.2 the Storm+HS led at 38.4 and the Lancer+HS trailed at 8.4. On the Ascendant (20 armour, 17 toughness) the Storm+HS and Lancer+HS now come out about even (6.4 against 6.1 DPS).

## 3. District sieges

### Before (0.10.0)

| Siege | Tier | Players | Wave | Core | HP lost (mean, p90) | Time |
|---|---|---|---|---|---|---|
| River Arts | II | 1 / 2 / 4 | 3×8 for all | 400 | 23 %, 36 % / 0 % / 0 % | 57 / 36 / 29 s |
| South Slope | II | 1 / 2 / 4 | 3×8 | 400 | 24 %, 37 % / 0 % / 0 % | 59 / 37 / 29 s |
| Downtown | III | 1 / 2 / 4 | 4×10 | 400 | 8 %, 20 % / 0 % / 0 % | 61 / 44 / 38 s |
| Mission | IV | 1 / 2 / 4 | 4×12 | 400 | 1 % / 0 % / 0 % | 50 / 47 / 40 s |
| Breach | V | 1 / 2 / 4 | 5×14 | 400 | 4 % / 1 % / 0 % | 67 / 62 / 51 s |

Undefended, the core fell **17-37 s** after the first zombie arrived (tier VI 17 s, tier I 37 s). The phase 4.5 playtest saw about 100 s for a tier-III core, with the waves walking in through a real building.

### After

| Siege | Tier | Players | Wave | Core | HP lost (mean, p90) | Time | Gun energy (cells) | Cells dropped |
|---|---|---|---|---|---|---|---|---|
| River Arts | II | 1 | 6×12 | 1920 | 33 %, 38 % | 161 s | 1.7 | 0.5 |
| | | 2 | 6×19 | | 27 %, 32 % | 151 s | 2.7 | 0.8 |
| | | 4 | 6×34 | | 20 %, 25 % | 137 s | 5.1 | 1.4 |
| South Slope | II | 1 | 6×12 | 1920 | 34 %, 42 % | 166 s | 1.8 | 0.9 |
| | | 2 | 6×19 | | 28 %, 33 % | 153 s | 2.8 | 1.5 |
| | | 4 | 6×34 | | 22 %, 26 % | 141 s | 5.2 | 2.7 |
| Downtown | III | 1 | 7×20 | 3200 | 20 %, 25 % | 176 s | 4.2 | 2.2 |
| | | 2 | 7×32 | | 14 %, 18 % | 155 s | 7.3 | 3.6 |
| | | 4 | 7×56 | | 10 %, 14 % | 145 s | 12.9 | 6.4 |
| Mission | IV | 1 | 6×22 | 3520 | 6 %, 12 % | 118 s | 1.7 | 1.8 |
| | | 2 | 6×35 | | 22 %, 29 % | 166 s | 3.0 | 3.4 |
| | | 4 | 6×62 | | 10 %, 14 % | 134 s | 7.5 | 6.6 |
| Breach | V | 1 | 6×24 | 3840 | 9 %, 14 % | 131 s | 1.6 | 2.0 |
| | | 2 | 6×38 | | 26 %, 33 % | 176 s | 3.1 | 3.5 |
| | | 4 | 6×67 | | 17 %, 21 % | 144 s | 7.6 | 6.8 |

| Undefended, tier | I | II | III | IV | V | VI |
|---|---|---|---|---|---|---|
| Core health | 1280 | 1920 | 3200 | 3520 | 3840 | 4160 |
| Falls after one solo wave | 163 s | 164 s | 164 s | 165 s | 165 s | 165 s |
| ... with the following waves coming | 144 s | 144 s | 144 s | 145 s | 145 s | 145 s |

"Gun energy" is what the party fires. It drains Cell Packs and carried cells, which Power Relays recharge for free, so it is not consumed the way the 4 energize cells are. "Cells dropped" is the expected Energy Cells from the specials killed.

### What changed and why

| File | Change | Why |
|---|---|---|
| `grid/settings.json` | `core.damage_per_zombie_second` 2 → **1**; `core.hp` 400 → 1600 (a fallback now) | Damage is an integer per zombie-second, so halving it is the only way to get finer steps. The health then comes from the tier. |
| `grid/siege.json` tiers | New per-tier **`core_hp`**, 160 × the solo wave size: 1280 / 1920 / 3200 / 3520 / 3840 / 4160 | **Undefended target:** one solo wave alone needs about 160 s to break the core in any tier. With the next waves coming, the core lasts about 145 s. A party that steps away briefly is not punished at once. A single core health could not do this, because wave sizes grow with the tier (code: `SiegeSettings.Tier.coreHp`). |
| `grid/siege.json` | New **`party_scale`** `[1.0, 1.6, 2.3, 2.8]` (players within the 96-block presence radius when a wave spawns) | The siege used to be the same for one player and four, so a party never felt it (0 % lost). Code: `SiegeSettings.waveSize`, `GridSiege.playersAround`. |
| `grid/siege.json` tiers | Waves and sizes: I 3×6 → 4×8; II 3×8 → 6×12; III 4×10 → 7×20; IV 4×12 → 6×22; V 5×14 → 6×24; VI 5×16 → 7×26. Tiers IV-VI lean more armoured. | **Defended target:** a party with the act's gear loses 20-40 %. Loss is zombie-seconds at the core divided by core health, and core health is fixed by the undefended target. So the only lever is enough zombies, arriving densely enough, that some reach the core. |
| `grid/siege.json` | `wave_interval_seconds` 90 → 120; `next_wave_alive_at_most` 2 → 4 | Waves now overlap a little while the party is winning (pressure). A losing party gets two minutes before the next one (mercy). |

**Where the targets are missed, and why:**
- **Solo late gear (Mission and a late Breach, 6-9 % lost).** A Storm with a Heat Sink plus a blade was by far the strongest loadout: 38 sustained DPS against 8.4 for the shipped Lancer (10.2.2 cut this to 26.3 against 15.8; the siege runs were not repeated). The duo, which pairs it with a Lancer, lands on target (22-26 %).
- **Mid and late gear at four players (10-17 % lost).** The Director's 120-zombie cap limits how big a wave can usefully get.

Both come from blaster numbers, not siege numbers (section 7), so the sieges were not pushed further. A bigger tier IV-V would swamp an early-gear party that reaches Mission sooner than usual.

**Load.** At four players the top waves are 62-73 zombies. Two overlapping waves stay under the Director's 150 world cap. A JUnit check guards this, and phase 6 measured 120 finale mobs at a 13 ms median.

## 4. The Black Start

### Before

| Players | Wave | Minutes | Pauses | Cells fed | Couplers | Gun energy | Extra cells |
|---|---|---|---|---|---|---|---|
| 1 | 8 | 10.2 | 0 | 6 | 0 | 2.4 | **8.4** |
| 2 | 11 | 8.5 | 0 | 6 | 0 | 3.7 | 9.7 |
| 4 | 16 | 8.0 | 0 | 6 | 0 | 5.3 | 11.3 |

Undefended Uplink: down **20 s** after the first zombie reached it. The phase 6 headless run saw about 40 s.

### After

| Players | Wave | Minutes | Pauses | Cells fed | Couplers | Gun energy | Extra cells |
|---|---|---|---|---|---|---|---|
| 1 | 13 | 11.9 | 0 | 9 | 0 | 5.4 | **14.4** |
| 2 | 18 | 9.9 | 0 | 9 | 0 | 8.2 | 17.2 |
| 4 | 25 | 9.5 | 0 | 9 | 0 | 11.4 | 20.4 |

Undefended Uplink: down **68 s** after the first zombie reaches it.

| Setting | Before → after | Why |
|---|---|---|
| `uplink.hp` / `damage_per_zombie_second` | 200 / 2 → **1200 / 1** | A solo player has to leave the Uplink for about 160 s to hold both breakers. One wave used to knock it out in seconds, so solo play was "brutal or fine" depending on luck (phase 6 known issue). |
| `uplink.coupler_patch` | 40 → 200 | Stays one-sixth of the Uplink, so resuming still takes 3 couplers. |
| `uplink.arc_damage` | 12 → 60 | Stays 5 % of the Uplink per arc. |
| `surge_seconds` | 360 → **450** | The solo run was 10 minutes, below the design's 12-15. The fixed parts still fit the JUnit 12-15 minute check (9.6 minutes fixed, 10.8 solo). |
| `buffer.cell_seconds` | 60 → **45** | The target is "winnable solo with late gear and 16-24 extra cells". Before, the Surge took 6 cells and the run about 8 in total. Now it takes 9 for the buffer plus about 5 of gun energy: about 14 on average. **16 gives a margin of one pause, and 24 is comfortable.** |
| `waves.size` | 14 → 22 (solo 8 → 13, four players 16 → 25) | With late gear the old waves never reached the Uplink. |
| Wave intervals (Surge / Feedback / Heart) | 40/60/75 → 30/45/60 s | Same reason. |

Nothing paused in the simulation, because the late gear is strong (section 7). The bigger Uplink buys time for what the model leaves out: players getting hurt, the Choir chasing someone and spitters at range. The finale stays under its 120-mob cap; at four players the peak is 25 alive.

## 5. Energy Cells: income against what the arc consumes

Cells consumed over the arc (one party):
- 4 districts × 4 energize cells = 16, plus 4 for each lost siege.
- The Grid Uplink: 16.
- The finale's extra cells: about 14-20.

**Total: about 46-56 cells.** On top of that, batteries go into couplers: 16 for repairs, 8 for the Uplink, and 2 for each Breach re-repair.

Income, from the simulation's economy section (one player; the container counts per day are assumptions):

| Source | Early | Mid | Late |
|---|---|---|---|
| Building loot (30 / 22 / 15 containers a day) | 0.05 cells + 4.2 batteries | 0.04 + 3.1 | 0.03 + 2.1 |
| Ambient kills (specials' drops) | 0.07 → **0.16** | 0.19 → **0.40** | 0.25 → **0.53** |
| A horde night | 0.03 → **0.08** | 0.20 → **0.43** | 0.23 → **0.51** |
| A siege (section 3) | about 0.1-0.6 → **0.5-6.8** for the party | | |

One-off sources:
- 2 cells per player per district from the reward cache.
- 11 cells from the three bosses.
- About 1 cell and 0.6 batteries per crash site.

Over about 25 days a solo player sees roughly 75 batteries plus 20-30 cells from one-off sources and fights. About 26 of those batteries go into couplers. **That leaves roughly 70-80 cell-equivalents against about 50 needed:** scarce enough that cells are always worth picking up, but not blocking.

A battery is only a cell once it has its wire. Since 10.2.2 a cell is a battery plus 4 wire spools (2 Scrap Electronics; it was 8 spools, 4 SE), and a Grid Coupler is a battery plus 2 spools and 1 SE (2 SE). Building containers give about 0.063 SE and 0.085 spools each (sim economy section), so about 1.9 / 1.4 / 0.9 SE a day of loot plus 2.5 / 1.9 / 1.3 spools, before radio jobs (about 1.6-2 SE-equivalent each), dumpsters and zombie drops (SE 3 %). Crafting about 30 cells and 26 couplers costs about 110-115 SE-equivalent against roughly 200 income over the arc: tight, not short. At the old 8-spool cell it was about 175-240.

A party consumes the same district and Uplink cells but earns per player, so co-op is looser. That is deliberate, because co-op also spends more gun energy.

| File | Change | Why |
|---|---|---|
| `mob_drops/ashfall/brute.json` | Energy Cell 25 % → **50 %** | Before, a siege returned almost nothing (0.06-0.6 cells). Fights should feed the cell economy, not only drain it. Brutes are rare (15 per 1000 siege members) and tanky. |
| `mob_drops/ashfall/screamer.json` | 20 % → **30 %** | Same reason. |
| `mob_drops/ashfall/bloated.json` | New Energy Cell drop at **8 %** (bandage kept) | It is the commonest siege special after the crawler, and the drop is a reason to kill it at range. |

Horde wave loot tables (`director/.../loot_table/horde/*.json`) were left alone. They are director data, and Energy Cells and batteries are arms items that director-only test servers cannot resolve.

## 6. Horde schedule and specials: no change

- **Horde nights.** They cost 0.4-1.1 gun-energy cells per player and now pay back 0.1-0.5. They have no core to defend, so the simulation's only measure is contact time, and that stays near zero for act gear. Their danger comes from being caught outside, which the model does not capture.
- **Specials.** The siege share is 138 per 1000 at full unlock: about 2 specials in a solo tier-II wave and 5 in a four-player one. Their per-player caps scale with party size. Neither needed tuning to reach the targets, so both were left for a real co-op run to judge.

## 7. Findings outside this pass

1. **blaster-balance-v2 is approved but not shipped.**
   - Only its section 0 fix (bolts reset `damageCooldownTime`) is in the code.
   - The `energy_gun/*.json` values are still v1, except the Storm row (10.2.2: 400 rpm, 2 energy, 7 heat, cool 24, the v2 values) and the Lancer's cooling (15 → 22, an interim step until charge and pierce ship). The Lancer is still 28 damage at 6 energy and 50 heat with no charge or pierce.
   - Pack capacities are still 30/60/120, against the spec's 40/60/150.
   - Effect before 10.2.2: the Storm with a Heat Sink outclassed everything (38 sustained DPS), and the Lancer was the weakest late gun (8.4). After: Storm+HS 26.3, Lancer+HS 15.8 sustained, and about even on the Ascendant. The Overcharger's heat multiplier dropped from 1.4 to 1.2 in the same release (it had lowered kills-before-lockout on every blaster); it shipped with the Storm change, not before it.
   - The rest of v2 (charge, pierce, pack capacities) is still the next balance step; rerun the siege and finale sections of `sim.py` after it, because the late numbers will shift.
2. **No district siege happens at tier I.** Every district's zone tier is at least II, so the tier-I row only matters to a modded district.

## 8. Code changes in this pass

- **One day clock.** `DirectorDay.today(level)` is the overworld day clock, the same day `HordeSchedule.day` gives. These now read it:
  - the ambient cap, ambient specials and variant `min_day`
  - building populations and Montford's gentle days
  - crash-site guards
  - `/ashfall director status`
  - the reclaim day
  - `GridSiege.siegeDay`

  Sleeping no longer leaves the ambient layer behind the horde nights. Tests: JUnit `DirectorDayTest` (including a source guard against `SpawnRules.day(...getGameTime())`) and gametest `theDirectorDayIsTheHordeDay`.
- **Siege numbers.** `SiegeSettings.Tier.coreHp` (`core_hp`), `SiegeSettings.partyScale` / `waveSize` (`party_scale`), `DistrictLoop.startSiege/startBreach(…, coreHp)` and `GridSiege.coreHp` (bar and status maximum). Old JSON loads unchanged.
- **Replay hand-back** (black-start spec section 9). `/ashfall story reset` leaves the spent Uplink in its socket (`FinaleRun.standby`, saved), and the socket glows. An empty hand on the socket starts the replay with the same Grid gate (`FinaleActions.restart`). Bringing a new Uplink to it powers up the old one and keeps the new one in hand. Tests: JUnit `theReplayResetKeepsTheUplinkInItsSocket`, `aResetWithNoUplinkSeatedHandsNothingBack`, `theStandbySocketIsSaved`; gametest `theReplayResetLeavesTheUplinkInItsSocket`.
- **`/ashfall finale phase` relights skipped bands** (`FinaleController.jumpTo`, `Effects.bandRelit`) quietly, once each, without a second title or bonus. Gametest `aPhaseJumpRelightsTheSkippedBands`.
- **Gear fix found by the build.** `DirectorSpawns.applyVariant` now also empties the main hand of a variant that has its own gear. Vanilla's `finalizeSpawn` sometimes hands a zombie a weapon, and on top of the variant's pieces that broke the armored variant's 0-2 piece cap. The `variantAttributesAndTagsApplied` gametest caught it as a flaky "3 pieces".

`./gradlew build --console=plain` passes: gametests arms 96, director 71, salvage 22, plus JUnit.

## Weapon Parts and Grid Couplers (rc15, now 9.9.1)

Weapon Parts are found only, and the coupler recipe took 2. A full run needs about 30 couplers (16 district repairs, 1 to
link relays, 8 for the Uplink, about 3 Uplink patches and 1-3 Breach re-repairs). Coupler drops cover about 5 (grid
rewards 0.47 each, supply drops 0.2, Breaches 0.27), so about 25 are crafted. A solo player finds roughly 27 Weapon Parts
in 25 days (police containers 0.14 each, supply drops 0.49, grid rewards 0.71-1.71, crash sites 0.32, bosses 0.4-0.6,
Breaches 0.53, the Kiln office and police armory about 1.1 together; building-loot share estimated). At 2 per coupler that
was half the need, with the first district alone costing 10 before any reward. rc15 makes it 1 Weapon Part plus 1 Scrap
Metal, which brings the need to about 25 against about 27 found.

Later in rc15 the renewable Weapon Parts sources were raised as well, so a first district doesn't stall on them:
workshop containers 0.04 → 0.10, police 0.14 → 0.20, government 0.02 → 0.07, supply drops 0.49 → 0.70. Solo income over
25 days goes from about 27 to about 35, against about 25 needed. One-off caches and bosses are unchanged.

Those police figures assumed police stations to loot, but until 9.10.0 the Downtown landmark was the only one, so real
income was a few parts lower (about 26-30). Police stations now generate randomly in every city style except the
wasteland (multibuilding `ashfall:police_precinct`, weight 0.06-0.1 against about 1.4 for the base set, near town-hall
rarity). They use the landmark's rooms and the `police` profile without the armory cache; the landmark is now profile
`police_armory`, which keeps the cache.

## Police Weapon Parts (10.3.1)

10.3.1 lowers the loot rate of Weapon Parts in police stations: the `police` building table halves its Weapon Parts
weight, 16 to 8 (total weight 94 to 86): about 0.14 Weapon Parts a container instead of 0.26, and a container holds at
least one about 14% of the time instead of 24%, level with workshops (13%). The police armory cache
(`ashfall_cache:police_armory`), supply drops, grid rewards, bosses and other buildings are unchanged.
