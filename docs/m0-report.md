# M0 report: foundation and risk spikes (2026-10-25)

## Built
- One Gradle 9.8.2 / Loom 1.19.3 project (`ashfall`) that builds 3 mod jars: `ashfall_salvage`, `ashfall_director` and `ashfall_arms`. The build runs unit tests and Fabric gametests on a real 26.3 server, and all of them pass.
- `ArnisProjection`, an exact Java port of Arnis's lat/lon to block mapping, with unit tests checked against the real test tile.
- `pack/mods.json`, which pins 33 third-party mods. `tools/build_mrpack.py` checks that every pin targets 26.3 Fabric and has its required dependencies, then writes the `.mrpack`.
- `tools/map-pipeline.sh`: Arnis generation, then a vanilla 26.3 upgrade, then an audit.
- `tools/region_audit.py`, which reads region files without dependencies.
- `tools/server_session.py`, which runs scripted dedicated-server sessions.
- Asheville worlds: a 1 km² downtown test tile and the full 2,984 × 3,002 block map. The full map has 187 m of real elevation.
- Shipped overrides:
  - Zombie Break & Build applies only to zombie types, and its see-through-walls targeting is off.
  - Campfires burn 5 minutes, or 10 with fuel.
  - Torches burn 12 minutes, set through the `ashfall_core` world datapack.

## Risk spikes

| Risk | Result |
|---|---|
| Block entities lost in the 1.21.1 to 26.3 upgrade | **Cleared.** The full map went from DataVersion 3955 to 5023. All 35,156 chunks, all 4,226 chests, all 18 barrels, all 605 banners and every light block are identical before and after. Beds aren't stored as block entities in 26.3; the bed blocks are all still there. |
| Old `Count` item stacks | 37 stacks in Arnis barrels keep the old format, because Arnis labels them as already modern. They're harmless: the post-processor replaces Arnis container contents with loot tables anyway. |
| Baked-in light | Confirmed and measured on the full map: **201,785 glowstone** (interior ceiling grids), 4,434 lanterns, 1,192 sea lanterns, 1,073 end rods, 74 redstone lamps and 29 soul lanterns. There are no invisible `minecraft:light` blocks and no fire. The post-processor strips these in M1. |
| Projection mismatch | Arnis's built-in transform nets out to zero, and the Java port matches `metadata.json` exactly. Only **32 buildings** in the full map come from Overture without OSM tags; they get the generic profile. |
| Hook points in reused mods | **All confirmed against the real 26.3 jars.** Zombie Break & Build: `BreakAction#canExecute` and `BuildAction#canExecute`, where the mob is available. Contagion: `InfectedEntity`, `ContagionInfectionEffect#applyEffectTick`, plus `AFTER_DEATH`. Zombified Player: gated with config only (`spawnWhenKilledByInfection`). Undead Nights: `StateSaverAndLoader#setHordeNight` for the siren. OPAC: `OpenPACServerAPI` claim/query/claim-creation API, and hostile-spawn blocking is config only. Team Reborn Energy 5.0.0: `SimpleEnergyItem` / `SimpleEnergyStorage`. |
| All mods together | **86 mods (including bundled libraries) load together on a 26.3 server** with no errors. |
| Performance | 120 zombies chasing 5 targets through dense downtown at midnight, with the full pack and ZB&B active: **20 TPS**, median tick **3.5–4 ms**, 95th percentile **6.6–9.4 ms**, one 75 ms spike. The budget is 50 ms, so there's about 5× headroom. Caveat: no real player was connected; this gets rechecked with clients at later milestones. |

## Corrections to the plan
1. **Arnis v3.3.0 has no `--post-apocalyptic` flag.** Only buildings tagged as ruins in OSM look damaged, so any ruined look has to come from our post-processor.
2. `--save-json-file` only writes when data comes from Overpass, so the pipeline uses `--no-tile-archive`.
3. **EMI has no 26.3 build**, so REI is the recipe viewer.
4. **Collective isn't needed** by anything in the pack, so it was dropped.
5. **Contagion is All Rights Reserved**, not MIT. That's fine, since we only hook it and never redistribute it.
6. **packwiz was replaced** by `tools/build_mrpack.py`. The prebuilt packwiz binary is an unsigned CI artifact and was blocked from running. The script needs no third-party code and also checks dependencies.
7. OPAC blocks vanilla zombie door-breaking inside claims. We'll decide in M4 whether sieges may break doors.
8. A siege spawner must not be registered as a vanilla custom spawner, because OPAC blocks those in claims. Spawn siege mobs with `addFreshEntity` instead.
9. 26.3 pauses empty dedicated servers after 60 seconds. Test servers set `pause-when-empty-seconds=-1`.

## Incident
The first stress run used `spark health`, which **uploaded two health reports to spark's public viewer** (spark.lucko.me). They contained server specs, TPS and the mod list, and nothing personal. That command has been removed from all test scripts, and future profiling stays local.
