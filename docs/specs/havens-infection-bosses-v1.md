# Spec: Havens, Infection and Bosses I (2026-10-26, pack 0.7.0)

Phase 2 was built in three parallel tracks and merged into the main repo: **Havens** (track A), **Infection** (track B) and **Bosses, crystals and crash sites** (track C). This page is the merged record: what each track built, where its numbers live, and what was fixed during integration. All numbers below are the shipped datapack values; change them in the JSON, not in code.

Havens has its own detailed spec in [havens-v1.md](havens-v1.md). This page summarises it and covers the rest in full.

## 1. Havens (director, `director.haven`, `director.claims`)

Numbers: `data/ashfall/ashfall/haven/settings.json`.

| Setting | Value |
|---|---|
| `bunker_radius` | 2 chunks, so a Bunker Core covers 5×5 chunks |
| `respawn_protection_seconds` | 10 (Resistance V, untargetable by zombies, ends when the player hits something) |
| `respawn_choice_seconds` | 20 (time to click another respawn option) |
| `siren_warning_seconds` | 150 before nightfall (time of day 9000) |
| `siren_repeat_seconds` | 30 |
| `siren_sound` / `siren_volume` | `minecraft:event.raid.horn` / 0.8 |

- **One claim adapter.** `director.claims.Claims` replaced the three earlier copies. The old classes are archived under `archive/p2-merge-removed/`. Only `OpacClaimBackend` touches OPAC. "Claimed" means an OPAC claim **or** one of our haven regions, so protection still works without OPAC and in chunks OPAC refused. Arms no longer has an OPAC compile dependency.
- **Bunker Core** (`ashfall:bunker_core`). Recipe `SIS/SPS/SIS`: scrap metal, iron blocks and `#ashfall:bunker_core_power`, which holds the Energy Cell. Placing it claims the 5×5 area in the placer's name. Each party (or each solo player) can have one core. Breaking it releases the claim. Shared storage relies on OPAC's party container access. The core is fortified, cannot be moved by pistons, resists explosions and gives light 4.
- **Safe zones.** Defined in `data/<ns>/ashfall/safe_zone/*.json`. The shipped zone is `ashfall:montford_safehouse`, at chunk (-10, -50) with radius 1, and it respawns players at the world spawn. Protection is partial, by design:
  - Inside a safe zone there are no Director spawns, and ZB&B can neither build nor break.
  - Inside a Bunker Core area spawns and building stop, but hordes still break plain blocks. Fortified blocks hold.
- **Respawn choice.** A clickable chat prompt. The default is applied immediately, picking the first that exists: Bunker Core, then bed, then the nearest safe zone, then world spawn. You can switch once within 20 s.
- **Horde siren.** Predicts the Undead Nights horde night from its counters and shows a bossbar countdown. The siren is a per-player sound, so it never draws zombies.

## 2. Infection (director, `director.infection`, `director.story`)

Numbers: `data/ashfall/ashfall/infection/stages.json`, checked when the datapack loads (a broken file fails loudly at `/reload`).

### Timeline

Ashfall owns the timeline in a player attachment (`ashfall:infection`). Contagion's effect stays as the visible base effect. Each tick, `ContagionBridge` writes our remaining time into Contagion's countdown and never lets it fall below 40 ticks, so Contagion never kills anyone itself. If Contagion is missing, the system still works without it.

| Stage | Starts | Hunger / thirst exhaustion per tick | Symptoms |
|---|---|---|---|
| 1 | 0 s | none | action-bar message only |
| 2 | 300 s (5 min) | 0.005 / 0.008 | none |
| 3 | 720 s (12 min) | 0.006 / 0.010 | Slowness I and Weakness I always on, Nausea (8 s) 2 % per second |
| 4 | 1200 s (20 min) | 0.008 / 0.012 | Slowness II, Weakness II, Mining Fatigue I, Nausea (10 s) 5 % per second |
| death | 480 s after stage 4 starts | | damage type `ashfall:infection` (bypasses armour and effects) |

Creative and spectator players do not progress.

### Sources

- **Zombie melee.** A damaging melee hit from a zombie-family mob rolls an infection chance:
  - Base chance 0.06, and 0.06 for walker, runner and husk.
  - Armored 0.04, drowned 0.08, `patient` 0.2.
  - ×1.5 in the Mission zone.
  - Minus 0.02 for each armour point.
  - Capped at 0.75.
- **Dirty water** infects through the same `Infection.infect` path.
- **Contagion's own sources** (contagious flesh, potions) put the player at stage 1 of our timeline.
- `pack/overrides/config/contagion.json5` sets Contagion's own hit chance to 0 and turns off its random symptoms, so the two systems never stack.
- Removing the effect (milk, a Contagion potion) does not cure. While our state says infected, the effect is put back.

### Treatments (user decision: no painkillers, no antiviral item)

| Item | Effect |
|---|---|
| Bandage | Heals 3.0 health and pauses progress for 60 s |
| Antibiotics | Cure at stage 1. At stages 2–4: back to the **start of the previous stage**, plus a pause of at least 180 s |
| Antibiotics after the Sever ending | Cure at any stage |

The Sever ending is the story flag `ashfall:sever` in the world-wide `StoryFlags` saved data (`ashfall_story.dat`). It is off by default, and the future ending code sets it. `/ashfall story sever|reset` flips it for testing.

### Rising and powered weapons

- **Who rises.** A player rises as a Zombified Player only if they die *while infected*, from any cause. `InfectedRising` sets Contagion's "died from infection" flag before Zombified Player reads it. `pack/overrides/config/zombifiedplayer.json5` turns off "rise after any zombie kill" and keeps `spawnWhenKilledByInfection`. Healthy players never rise.
- **No powered weapons for the risen.** Mobs never fire energy weapons (`GunLogic#canFire`). A risen player carries their blasters and drops them on death (`MobGuns`, `MobGunDropMixin`).

### HUD

`InfectionHud` draws a stage icon beside the thirst droplets for stages 2–4, following the same hide/show rule as the droplets. The sprites are made by `tools/textures/hud_infection.py`.

## 3. Bosses, crystals and crash sites (director `boss`, salvage `site`, arms loot)

### Boss lairs

One JSON per lair in `data/ashfall/ashfall/boss_lair/*.json`.

- **Waking.** Lairs are checked once a second. A lair wakes the first time a player comes within `trigger_radius`, and its boss spawns on the lair's `ashfall:landmark/boss_marker` block.
- **What a boss is.** Bosses are persistent and have a boss bar. They are not Director zombies, so they don't count toward the caps.
- **State.** Kept per world in `ashfall_bosses.dat`. Each boss fights once per world.
- **Respawn.** With `respawn.after_ending_days` (10 for every shipped lair), a boss may come back that many days after the later of two events: the ending, or the boss's last defeat.
- **Ending hook.** It is `BossLairs.setEndingTickSource`; arms' `StoryEffects` feeds it the ending tick from `StoryState` (see [endings-v1.md](endings-v1.md)). A lair may also carry `requires_flag` (the Bind-only Ascendant).

| Lair | Where | Boss | Key numbers |
|---|---|---|---|
| Abomination | Kiln & Co. warehouse foundry, chunk (-60, 7), 2×2 | `mutantmonsters:mutant_zombie`, or the `abomination_standin` variant if Mutant Monsters is absent | 300 HP, follow 40; stand-in: 13 damage, scale 2.0, knockback resistance 0.9; trigger 28, bar 56 |
| Patient Zero | Hospital deep-ward cellar, chunk (44, 39), 3×2, **sealed** | `patient_zero` variant | 260 HP, 9 damage, 6 armour, scale 1.15, glows, sneeze particles; summons 2 `patient` every 300 ticks while it has a target, at most 6 alive, within 5 blocks; trigger 20, bar 40 |
| Juggernaut | The Wreck hull, a fixed ridge site at (2300, -100) | `juggernaut` variant | 360 HP, 14 damage, netherite and iron armour (never dropped), full knockback resistance, scale 1.4; trigger 36, bar 64 |

- **Patient Zero's seal.** The lair stays sealed until `BossLairs.unseal(level, id)` runs (the grid event will call it, and so does `/ashfall boss unseal`). Until then it never spawns, and survival players cannot break the iron doors within `seal_radius` 16.
- **Drops.** Every boss drops one guaranteed `ashfall:resonance_crystal` (the crystal the endgame needs), plus rolls on `ashfall:entities/boss/<lair>` (arms data).

### Crash sites (salvage)

- **Where the layout comes from.** Sites are planned from the world seed alone (`CrashSitePlanner`), so the layout is fixed per world.
- **When blocks are placed.** Lazily, on the server thread, once a player is near and the whole footprint is loaded (`Sites`, remembered in `SiteState`). Only worlds using our Lost Cities profile get sites.
- **Templates** are in `data/ashfall/ashfall/site_template/` (`crash_site_small`, `crash_site_pod`, `crash_site_debris`, `wreck_hull`). The fixed Wreck hull is listed under `sites` in the ridge zone JSON.

| Rule file | Zones | Region | Chance | Templates (weight) | Guards |
|---|---|---|---|---|---|
| `crash_sites/ridge.json` | ridge | one roll per 10×10 chunks | 0.40 | small 3, pod 2, debris 2 | 1–3 within 7 blocks |
| `crash_sites/wasteland.json` | wasteland | one roll per 16×16 chunks | 0.25 | small 2, pod 1, debris 3 | 1–2 within 6 blocks |

Both rules keep sites at least 64 blocks from fixed landmarks (`avoid_fixed`). Crash-site chests roll `ashfall:chests/crash_site`, and the rarer blasters come from `ashfall:blasters/rare` (Ion Rifle 4, Arc Magnum 4, Lancer 2, Storm Repeater 1). `CrashSiteGuards` (director) spawns the guards as ambient Director zombies the first time a player comes near.

`pack/overrides/config/mutantmonsters-common.toml` keeps Mutant Monsters from spawning naturally, so the only Abomination is the lair's.

## 4. Integration fixes (stage 2)

- **Rapid-fire blasters lost damage.** `EnergyBolt` cleared `Entity#invulnerableTime`, but in 26.x `LivingEntity#hurtServer` reads `LivingEntity#damageCooldownTime`. That timer is set to 20 on each hit, and while it is above 10 a new hit only lands if it beats `lastHurt`, and then only the difference. So the second and later equal bolts inside half a second did nothing, and a Storm Repeater dealt a fraction of its stated DPS. Each bolt now resets `damageCooldownTime`. Gametest: `ArmsBoltTest#rapidBoltsAllDealFullDamage` (4 bolts of 4 damage, 2 ticks apart, must deal 16).
- **`/ashfall atlas audit` reported 0 loaded chunks.** It walked a set built from chunk load/unload events, which missed force-loaded chunks and chunks loaded before the listener existed. The audit, `/ashfall atlas process` and the reload requeue now read the level's real chunk map (`LoadedChunks`, via the `ChunkMapAccessor` mixin). The audit logic is `AtlasCommands.audit(ServerLevel)`, and `AtlasProcessorTest` checks that it sees the test chunks and their containers.
- **"POI data mismatch: never registered" comes from Lost Cities, not from us.**
  - **Cause.** Lost Cities writes its buildings straight into `LevelChunkSection`s (`ChunkDriver`, `section.setBlockState` in `RAW_GENERATION`). That skips `WorldGenRegion#setBlock`, the only place a newly generated chunk's POIs are registered. So a Lost Cities lectern, barrel or bell has no POI record until the chunk is next read from disk, when `checkConsistencyWithBlocks` adds it. If a later feature replaces such a block through the proper API, vanilla queues a POI removal and logs "never registered".
  - **Evidence.** The logged position (-174, 96, 324) in the fresh-check world is now `cave_air`. Ashfall never writes cave_air, and Lost Cities doesn't either. The rare "already registered" lines are the same race in reverse under Chunky's load/unload churn.
  - **Impact.** None: the POI data corrects itself on the next load.
  - **Our passes.** The atlas passes never replace POI blocks: ContainerPass writes only into air, carpet or replaceable blocks, and LightStripper touches only light blocks. DamagePass now also explicitly skips any block with a POI type.

## 5. Commands added in phase 2

| Command | Permission | What |
|---|---|---|
| `/ashfall safezone list \| add <name> [radius] \| remove <id>` | op | Manage safe zones (removing a datapack zone only disables it for this world) |
| `/ashfall respawn bunker \| safezone \| spawn` | everyone (clickable) | Change the respawn point once, within 20 s of respawning |
| `/ashfall infection get \| set \| clear <player> [stage]` | op | Testing aid |
| `/ashfall story sever \| reset` | op | Flip the story flags (Sever: Antibiotics cure at any stage) |
| `/ashfall boss list \| spawn <lair> [here] \| unseal <lair>` | op | Lair status, force a boss, open Patient Zero's seal |

## 6. Tests

- **JUnit:** haven (`ChunkAreaTest`, `ProtectionTimersTest`, `SirenLogicTest`, `RespawnOptionTest`), infection rules and timeline, boss `LairRules`, crash-site planning.
- **Gametests on a real 26.3 server:**
  - director: `HavenGameTest`, `InfectionGameTest`, `BossLairGameTest`
  - arms: `ArmsMobGunTest`, `BossDropsGameTest`, `ArmsBoltTest#rapidBoltsAllDealFullDamage`
  - salvage: `CrashSiteGameTest`, and the audit check in `AtlasProcessorTest`

## Follow-ups

- Hook `BossLairs.setEndingTickSource` up to the story state when the endings exist. `StoryFlags` holds the Sever flag but not yet the tick when the ending happened.
- Playtest OPAC's own mob-griefing protection against ZB&B breaking in claims (havens-v1 follow-up).
- The Undead Nights `chanceForHordeNight` roll can cancel a predicted horde, so the siren text says "expected".
