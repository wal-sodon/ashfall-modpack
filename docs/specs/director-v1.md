# Spec: Director I (2026-10-25)

The Director owns zombie spawning, senses and targeting. It underpins the end goal (`docs/endgame-design.md`): district sieges, relay hums drawing hordes, safe zones and the Black Start all build on it. Module: **director**. All numbers are datapack JSON or named constants.

## 1. Spawning (`SpawnDirector`)

**Only zombies and their variants spawn as hostile mobs** (the user's requirement, 2026-10-25).
- **Every** `MobCategory.MONSTER` natural spawn in overworld biomes is removed through `BiomeModifications`: skeletons, creepers, spiders, cave spiders, endermen, witches, slimes, the zombie family, and any modded monsters. Bogged, strays, parched and other new types are covered by removing the whole category rather than a list.
- The Director spawns zombies and zombie variants instead. The zombie family is walker, runner, armored, husk-style and drowned (in water only).
- Pillager patrols, phantoms and wandering traders are turned off with game rules (`spawn_patrols`, `spawn_phantoms`, `spawn_wandering_traders` = false). These are set once when the server first starts on an Ashfall world, stored in SavedData so admins can change them later. The map pipeline sets them too.
- **Passive animals are unchanged** for now (food source). This is to be confirmed with the user.
- Other mods' own systems still apply: Mutant Monsters bosses (natural spawning off) and Zombified Player. Horde nights are ours since M8 ([hordes-v1.md](hordes-v1.md)); Undead Nights is out of the pack.
- The Nether and the End stay vanilla.
- **A safety net:** an entity-load check discards any non-zombie overworld monster that spawns naturally anyway, logged once per type. This doesn't touch spawn eggs, commands or spawners.

**Budget loop:** every 20 ticks, per player in the overworld.
- **Ambient cap:** zombies tracked by the Director within 96 blocks of the player. It is 30 on day 1, rising linearly to 60 by day 120, configurable.
- **Which day (phase 7):** every "day N" rule (the ambient cap, variants' and specials' `min_day`, Montford's gentle days, building populations, crash-site guards, `/ashfall director status`) reads `DirectorDay.today`: the overworld day clock, the same day horde nights, Breaches and sieges use. Before, these counted elapsed game time, which sleeping does not advance.
- **Hard caps:** 120 Director zombies per player, and 150 per world in co-op. Zombies from Undead Nights, bosses and players' Zombified Players don't count.

**Ambient spawning:**
- Candidate spots are 24–64 blocks from the player, on valid ground, not visible to any player (no line of sight), and with air headroom.
- The spot's light sets the spawn weight:

| Light level (max of block and sky) | Weight |
|---|---|
| 0 | 1.0 |
| 1–4 | 0.6 |
| 5–7 | 0.3 |
| 8–11 | 0.1 |
| 12+ | 0.02 |

- **Outdoors during the day the weight is ×0.4.** Indoors it isn't, since dark interiors stay dangerous.
- **No spawns inside any OPAC claim.** Use the OPAC API, behind an adapter so the Director still works without OPAC. Also no spawns inside safe-zone server claims, or within 16 blocks of a player's respawn point/bed.

**Building populations** (one-time, from the building data):
- **Buildings** come from salvage's `BuildingSource`, which in a Lost Cities world is one **building instance** per origin chunk (`dimension | origin chunk | building id`; a multibuilding's chunks share one instance). *Updated 2026-10-25 for the Lost Cities city; it was keyed by OSM id under Arnis.*
- **When it spawns:** the first time a player comes within 48 blocks of a building whose profile has `population` (salvage `BuildingProfile.Population`), the building gets its population. Count = round(per_100m² × (floors + cellars) × 16·16·w·h × 0.6 / 100), capped at 12, then × the zone's scale (below). Storeys above the building's Lost Cities ruin height are left out. Zombies spawn inside random free cells on random storeys, picked by the profile's entity weights.
- **Zone scale** (zone JSON `population_scale` and `director` block): Mission ×1.5 (highest infection pressure; patients ×5 there through `zone_bias`), Montford ×0.6 for the first 3 days (`gentle_days`/`gentle_scale`, which also damp ambient spawns), wasteland, ridge and undiscovered ×0 (no populations: darkness spawning only). The wasteland's dark spots (light ≤ 7) spawn ×1.3 (`darkness_scale`); Mission's ×1.2.
- **Persistence:** each building's population is saved in SavedData `ashfall_director`, keyed by the building id (the packed origin chunk). A zombie that leaves its building despawns only under vanilla rules (persistence required while it is inside the building).
- **Cleared:** a building is cleared once all its population is dead. It **stays cleared for 7 in-game days**. The timer **pauses while any player is within 48 blocks**. After that it gets a fresh population, half the size.
- Building zombies don't count toward the ambient cap, but they do count toward the hard caps.

**Variants for the MVP:** vanilla zombie entities with attributes and gear, applied on spawn and tagged `ashfall_variant=<id>`. Variants live in datapack `data/ashfall/ashfall/zombie_variant/*.json`, with fields for weight, min_day, attributes and equipment.

**Spawn conditions** (In Control's idea, done in `SpawnDirector`): a variant may carry `"conditions": {"zones": [...], "in_building": bool, "on_street": bool, "buildings": [...]}` and `"zone_bias": {"<zone>": factor}`. Every set condition must hold at the spot; `on_street` means a Lost Cities city chunk with no building. Only zombie-family entities can spawn whatever the conditions say.

| Variant | Stats | Availability |
|---|---|---|
| **walker** | 20 HP, 0.23 speed | 70% |
| **runner** | 14 HP, 0.30 speed, +follow range | 20%, from day 3 |
| **armored** | Random police or worker gear, iron or chainmail pieces, 0–2 pieces | 10%, from day 7 |

Police, fire station and government buildings weight armored ×4. Hospitals and clinics add more zombie_villager-looking variants (cosmetic).

## 2. Senses: the `Stimuli` API (director, used by arms)

A per-level spatial field of stimuli (SOUND, LIGHT). Each stimulus has a position, a radius and a short expiry (SOUND 100 ticks, LIGHT 40 ticks, refreshed). It is stored by chunk section for cheap queries.

**Public API:** `Stimuli.emit(ServerLevel, Vec3 pos, StimulusType type, double radius, @Nullable Entity source)`.

**Sources:**
- **The arms hook:** `ArmsNoise.emit` in arms forwards to `Stimuli.emit(..., SOUND, radius)`. Arms already depends on director. This covers blaster shots and the Power Relay hum.
- **Vanilla game events → SOUND:** one mixin or listener on `ServerLevel#gameEvent` with a table `data/ashfall/ashfall/noise/game_events.json`:

| Event | Radius |
|---|---|
| door or trapdoor open/close | 10 |
| block destroy | 12 |
| container open | 6 |
| explode | 96 |
| eat or drink | 4 |
| sprint or step while running | 8 (sampled, not every step) |
| projectile land | 8 |

- **Light:** every 40 ticks for each player, light = max(the block light where the player stands, the light level of held items). Use LambDynamicLights item light values if present, otherwise vanilla `minecraft:light_emission` from the block item. Torches, lanterns, glowstone and the Plasma Blade later. If light is ≥ 7 at night, or ≥ 10 by day indoors, emit LIGHT at the player with radius 4 × level, ×0.3 by day. **Light attracts only when a player is near it** (the user's rule), so this is player-centred and placed lamps far from players do nothing.

## 3. Targeting (`SenseGoal`)

- Added to every zombie the Director spawns, and also to vanilla `Zombie` subclasses.
- Every 20 ticks, staggered by entity id, the zombie queries the stimuli field within its senses (hearing radius × variant hearing, sight 32).
- **SOUND:** walk to the stimulus position (investigate), unless it already has a target.
- **LIGHT:** react only if it has **line of sight from outside** to the light source position. Raycast from the zombie's eyes to the stimulus. The ray is blocked by any solid block and also by the block tag `#ashfall:blocks_light_sight`, which holds all wool, all carpets, all banners, closed trapdoors and closed doors. This makes blackout curtains (wool or banner over windows) work.
- If it sees a player during the investigation, it targets that player (normal vanilla target selection takes over).
- **Only the Director sets targets.** ZB&B targeting is already off in config.

## 4. Fortified blocks (ZB&B hook)

- Block tag `#ashfall:fortified` holds iron_block, iron_bars, iron_door, iron_trapdoor, obsidian, crying_obsidian, reinforced_deepslate, all copper doors and trapdoors, and the two new Ashfall blocks below.
- One mixin `@Inject(HEAD, cancellable)` on ZB&B's `BreakAction#canExecute(MobActionContext, BreakRequest)`: refuse when the target block is in `#ashfall:fortified` and the mob is not in `#ashfall:bosses`. The bosses tag holds mutantmonsters:mutant_zombie and undeadnights:demolition_zombie (TNT).
- A second veto on `BuildAction#canExecute`: no mob building inside OPAC claims.
- **Gating:** the mixin config is separate and only applies when `zbb` is loaded. Use an IMixinConfigPlugin; ZB&B stays compileOnly.
- **New blocks:**
  - `ashfall:reinforced_planks` (oak planks + iron nuggets).
  - `ashfall:barricade` (a partial block, planks + scrap metal, walkable for zombies? no, solid).
  - Both in `#ashfall:fortified`.
  - Both get simple textures generated with the existing sprite tool (pixel-art style like the restored icons).

## 5. Commands and debug

- `/ashfall director status`: budget, counts by source, cleared buildings, next spawn.
- `/ashfall director clear-building <building id>|here`, `/ashfall director repopulate <building id>|here` (`here` = the building you stand in).
- `/ashfall debug stimuli [seconds]`: renders active stimuli as particles to ops.

## 6. Tests

**JUnit:** light weight table, cap maths, cleared-timer pause logic, stimulus expiry and spatial query.

**Gametests:**
- No spawns in a claim (fake claim adapter) or at light 15.
- A zombie walks to a SOUND stimulus.
- A zombie ignores LIGHT behind wool and reacts through glass.
- ZB&B can't break `#ashfall:fortified`. Only run this when zbb is present; otherwise test the predicate method directly.
- Building population spawns once and stays cleared.

## Out of scope (later milestones)
Dynamic horde merging and leaders (M8, done: [hordes-v1.md](hordes-v1.md)), special infected entities (M9), sieges for districts (M4.5), havens and Bunker Cores (M4).
