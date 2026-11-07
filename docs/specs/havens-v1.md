# Spec: Havens I (2026-10-26)

Havens are the M4 part of the plan: Bunker Cores, server safe zones, the respawn choice with respawn protection, the horde siren, and electric lights. They live in the **director** module (`xyz.toesinthesand.ashfall.director.haven`). Numbers are in `data/ashfall/ashfall/haven/settings.json`.

## 1. One claim adapter

`director.claims.Claims` is now the only claim adapter. It replaces three copies: `director/claims`, `director/spawn/claim` and arms' `charger/OpacClaims`. `OpacClaimBackend` is the only class that names OPAC types. Without OPAC, `ClaimBackend.NONE` is used.

"Claimed" means inside an OPAC claim **or** inside a haven region (a safe zone or a Bunker Core area). The haven regions are checked even when OPAC is present. They are the fallback exclusion when OPAC is absent, and they also cover chunks OPAC refused, for example when the claim limit is reached or someone else already owns the chunk.

## 2. Bunker Core (`ashfall:bunker_core`)

- **Recipe** (`arms` data, because the Energy Cell is an arms item): `SIS / SPS / SIS`, where S is scrap metal, I is an iron block and P is `#ashfall:bunker_core_power`. That tag is defined in director and holds `ashfall:energy_cell` as an optional entry, so other mods can add power items.
- **Placing it** claims a square of chunks around it (`bunker_radius`, default 2, so 5×5 chunks). The claim goes through OPAC `tryToClaim` in the placer's name, which keeps OPAC's limits. The same square is always added to the haven regions.
- **One core per party or player.** The key is the player's OPAC party id, or their UUID when they are not in a party. The item refuses to place a second core.
- **Breaking it** releases the OPAC chunks it claimed. Records whose block disappeared without a neighbour update (for example from `/setblock`) are dropped the next time they are looked up.
- **Shared storage uses OPAC**, not slots in the core. OPAC already locks containers in a claim to the party, so a second storage system would duplicate it.
- **Fortified:** the core is in `#ashfall:fortified`, cannot be pushed by pistons, and has obsidian-level blast resistance.

## 3. Safe zones

- **Defined in** `data/<ns>/ashfall/safe_zone/*.json`, with these fields: `name`, `dimension`, `area {chunk_x, chunk_z, radius}`, `respawn`, `use_world_spawn` and `landmark`.
- **Shipped zone:** `ashfall:montford_safehouse`, at chunk (-10, -50) with radius 1. The pharmacy at (-8, -50) stays outside. The zone respawns players at the world spawn, which `CitySpawn` puts in the safehouse front hall.
- **At server start and after every datapack reload**, each active zone server-claims its chunks through OPAC (if OPAC is installed). Chunks someone has already claimed are left alone. The claimed chunks are recorded so removing the zone releases exactly those.
- **Rules inside a safe zone:** no Director spawns, no ZB&B building and no ZB&B breaking, not even of plain blocks. A Bunker Core area only stops spawns and building. That is the plan's partial protection: hordes break plain blocks and fortified blocks hold.
- **Commands** (op): `/ashfall safezone list | add <name> [radius] | remove <id>`.
  - `add` centres the zone on your chunk and sets your block as its respawn point.
  - `remove` on a datapack zone only disables it for this world, in `HavenState`. The file is never touched.

## 4. Respawn choice and protection

- **A clickable chat prompt, not a screen.** It needs no client code and works on vanilla clients. It also cannot leave a player stuck behind a screen.
- **The default is applied at once**, in this order:
  1. the Bunker Core;
  2. the player's own bed or anchor, where vanilla already put them;
  3. the safe zone nearest the death spot;
  4. the world spawn.
- **Changing your mind:** the other options are buttons (`/ashfall respawn bunker|safezone|spawn`) that stay valid for `respawn_choice_seconds` (default 20). One change per death.
- **Protection:** for `respawn_protection_seconds` (default 10) the player has Resistance V. No zombie can target them, enforced by a `Mob#setTarget` mixin, and zombies already chasing them drop the target. Protection restarts after a choice teleport and ends the moment the player damages anything.

## 5. Horde siren

- **How it reads Undead Nights:** `UndeadNightsHordeSource` polls `UndeadNights.serverState` once a second. Undead Nights only sets `hordeNight` at nightfall (time of day 12000), so the warning has to predict it. `HordeSnapshot#hordeTonight` mirrors the counters in `HordeSpawner#tick`, checked against the 2.2.6 bytecode: horde tonight when grace is 1, or when grace is 0 and the day counter is ≤ 1.
- **Warning:** starts `siren_warning_seconds` before nightfall (default 150 s, so at time of day 9000). It shows a yellow bossbar countdown and a title. The siren (vanilla raid horn) sounds at the start and every `siren_repeat_seconds`.
- **Horde night:** when the flag goes true there is one siren, a title and a red bar until morning.
- **Not a stimulus:** the siren is a per-player sound packet, not a world sound, so it never draws zombies.

## 6. Electric lights

Arms overrides `minecraft:lantern`, `soul_lantern`, `copper_lantern` and `redstone_lamp` so each needs `ashfall:scrap_electronics` in place of a torch or glowstone. Torches burn out (Realistic Torches); electric lights are the permanent option.

## 7. Tests

- **JUnit:** `ChunkAreaTest`, `ProtectionTimersTest`, `SirenLogicTest` (fake horde flag), `RespawnOptionTest`.
- **Gametests** (`HavenGameTest`):
  - the safehouse zone is loaded and excludes spawns;
  - no spawns, building or breaking in a safe zone;
  - placing a Bunker Core creates the exclusion, and breaking it removes it;
  - the protection timer works, blocks targeting, and is cancelled by attacking;
  - the siren follows a fake schedule.

## Follow-ups

- **OPAC itself** may block ZB&B block breaking in claims through its mob-griefing protection. We have not configured that, and it needs a playtest with OPAC to decide (M0 correction 7).
- **The Undead Nights `chanceForHordeNight` roll** can still cancel a predicted horde. The text says "expected" for that reason.
