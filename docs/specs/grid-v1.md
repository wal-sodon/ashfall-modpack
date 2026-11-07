# Spec: Grid I and the Relight story (2026-10-26, pack 0.8.0)

Phase 3 (plan milestone M4.5, endgame-design §3 and §7) was built in two parallel tracks and merged into the main repo: **Grid core, district loop and siege** (track A, `arms.grid`) and **Story, quests and grid UI** (track B, `arms.story` and `arms.client.grid`). This page is the merged record. All numbers below are the shipped datapack values; change them in the JSON, not in code.

With this phase the end goal "Relight Asheville" exists in play: each of the four districts can be taken from DARK to RECLAIMED, the City Grid climbs 20 % per district to 80 %, and the Black Start (M9.5) is the only piece left between 80 % and the ending.

## 1. Shared contract

Both tracks coded against the same small API. Everything else talks to the grid through it.

| Piece | What |
|---|---|
| `DistrictState` | `DARK, REPAIRED, ENERGIZED, SIEGE, RECLAIMED`, plus `BROKEN` after a lost Breach (M8, [hordes-v1.md](hordes-v1.md)). `id()` / `byId()` give the lower-case names used by commands, saved data and the payload. |
| `GridState` | World-wide saved data in `data/ashfall_grid.dat`. `get(server)`, `state(id)`, `districts()`, `gridPercent()` (20 per reclaimed district, capped at 80, plus `blackStartBonus` for M9.5), `isReclaimed(id)`, `reclaimedAtDay(id)` (-1 when not reclaimed). All writes go through `update(level, id, change)`. |
| `GridEvents.DISTRICT_CHANGED` | `onChange(ServerLevel, districtId, from, to)`, fired only on a real state change, on the server thread, after the save data changed. |
| District ids | The ZoneMap district zone ids: `river_arts`, `south_slope`, `downtown`, `mission`. |
| `ashfall:grid_sync` (S2C) | `GridSyncPayload(Map<String, DistrictState> states, int percent)`, sent to each player on join and to everyone after every change. The client keeps it in `GridClientState`. Players without the client receiver are skipped by `canSend`. |

## 2. The district loop (track A)

Numbers: `data/ashfall/ashfall/grid/settings.json` and `siege.json`.

### Blocks and items

- **Substation Core** (`ashfall:substation_core`): block entity, unbreakable, `power` 0-3 sets light and model. The landmark assets keep a vanilla placeholder (a heavy core on the control-room plinth slab), because salvage sits below arms and cannot name an arms block. `CoreConversion` swaps the placeholder for the real core the first time a district chunk loads with arms present. Both blocks are in the `ashfall:landmark/substation_core` block tag, which `LandmarkCensus` counts.
- **Grid Coupler** (`ashfall:grid_coupler`): recipe `CSC / WBM` (wire spool, scrap electronics, weapon parts, battery, scrap metal; one Weapon Part since rc15, see balance-v1). No Resonance Crystal.
- **Grid Lamp** (`ashfall:grid_lamp`): the lamp the relight puts back. It drops a plain redstone lamp.

### Steps

| Step | How | Numbers |
|---|---|---|
| Repair (DARK to REPAIRED) | Right-click the core with Grid Couplers, each one used up | `couplers_required` 4 |
| Energize | Load full Energy Cells into the core; the last one starts the hold | `cells_required` 4, `cell_min_energy` 240 |
| Hold | Counts only while a player is near; the core hums (a SOUND stimulus), the Director adds hidden zombies, a bossbar shows progress ("paused" when nobody is near) | `energize_seconds` 300, `hold_radius` 32, `hum_interval_seconds` 4, `hum_radius` 144; pressure every 15 s, 2 at a time, at most 8 alive, 24-40 blocks out |
| Link | Right-click each of the district's key relays (from the ZoneMap's `key_relay_targets`, found by chunk through `RelayPlan`) with a coupler. Linking uses nothing up: the district already paid its couplers. | - |
| ENERGIZED | Hold done and every key relay linked: a District Siege is set for the coming nightfall | warning 150 s ahead through `HordeSiren.alert` |
| SIEGE | Waves of Director zombies (`DirectorSpawns`, tagged `DirectorZombie.Source.SIEGE`) attack the core | core health per siege tier (`core_hp` in `siege.json`: 1280 at tier I up to 4160 at tier VI, balance-v1), 1 HP per zombie-second for every siege zombie within 6.5 blocks of the core horizontally and 3 vertically (battering the control room); the surge holds the control room's iron security doors open for the siege (`door_radius` 14, `SiegeDoors`); waves spawn 18-30 blocks out, nearest the core's floor height first; wave sizes times the party scale (`party_scale` 1.0, 1.6, 2.3, 2.8 for 1-4+ players within the presence radius); the next wave comes after 120 s, or as soon as at most 4 are left alive |
| Win or lose | Clear every wave: RECLAIMED. Or reach dawn (23000) with players within 96 blocks of the core for at least 240 s of the siege: RECLAIMED. Core at 0 HP: back to REPAIRED (couplers kept, load fresh cells). Dawn without that presence: siege postponed to the next nightfall. | - |

Siege tier is `max(day tier, grid tier)`, where the grid tier is the larger of the district's own zone tier and `grid_tier_base` (1) plus the districts already reclaimed, so every relit district makes the next siege harder. Day tiers start at day 1, 6, 11, 21, 31 and 46 (tiers I-VI). Tier I is 4 waves of 8, tier VI is 7 waves of 26 with armored and patient variants (solo sizes; see [../balance-v1.md](../balance-v1.md) for why).

### What Reclaimed changes

- The substation compound (radius 1 chunk) becomes a safe zone through the Director's haven regions, plus an OPAC server claim when OPAC is present. The rest of the district still spawns.
- Darkness spawning in the district is multiplied by 0.2.
- Field Chargers and Power Relays in the district run without cells.
- A per-player reward cache at the core (`ashfall_cache:grid_reward_<district>`; Lootr makes it per-player).
- **Lazy relight:** the lamp positions the atlas logged (`RelightLog`) are replayed chunk by chunk as players load them, at most 4 chunks per tick. Dead redstone lamps become Grid Lamps; other stripped lights are put back only where the stripper's leftover is still there, so player builds are never overwritten. At night lit lights near players are LIGHT stimuli, so a lit district draws the dead after dark.
- District extras: **River Arts** starts the city pump (bottles filled in any reclaimed district come up clean); **Mission** unseals Patient Zero's deep ward (`BossLairs.unseal`).

### Commands

`/ashfall grid status | set <district> <state> | damage <district> <hp> | reset` (op). `status` also shows each core's position once its chunk has loaded. `damage` hurts a besieged core through the siege's own loss path (admin and headless tests). `set` fills in the state's bookkeeping and fires the change event as if it happened in play. `reset` sets every district back to DARK and keeps core positions.

## 3. Story and UI (track B)

- **Field Radio** (`ashfall:field_radio`): found in a new radio-room chest (`ashfall_cache:field_radio`, the radio plus 1-3 survival items) upstairs in the Montford safehouse. Using it posts the grid %, every district's state, the direction and rough distance to the nearest substation that is not reclaimed, the Relay Log count and the nearest relay the player has not tuned into.
- **Relay Logs:** 30 original logs and 3 site documents in `data/ashfall/ashfall/relay_logs/`. Holding the radio and using a Power Relay gives "Relay Log #N" (picked by a salted hash of the relay position) instead of opening the charger menu. Each player gets each log once; progress is kept in the `ashfall:radio_progress` player attachment, which survives death. Site documents come from the `ashfall:relay_document` loot function and are in the Kiln & Co. office cache, the hospital supply cache and a new cache inside the Wreck hull (which also gives a Focusing Lens).
- **"Relight" advancement tab:** 22 advancements in `data/ashfall/advancement/relight/`, driven by four custom triggers (`ashfall:district`, `ashfall:grid_percent`, `ashfall:relay_logs`, `ashfall:boss_defeated`). District and grid criteria fire for every online player on each `DISTRICT_CHANGED` and again on join, so players offline at the time still get them. Boss kills are shared with players within 64 blocks. `asheville_relit` is a placeholder (`minecraft:impossible`) until the Black Start.
- **Grid HUD:** "Grid NN%" and one pip per district, left of the hotbar, shown once the player has found the radio.
- **Grid Map** (key **G**): a schematic of the zones coloured by district state, with the safehouse, the four substations, the Wreck hull and the relays this player tuned into. Zone shapes and markers arrive in `ashfall:grid_map` (S2C, on join and after `/reload`); markers are in `data/ashfall/ashfall/grid_map/markers.json`.
- **ContainerPass** no longer replaces chests that already carry an `ashfall_cache:` table with building loot. Without this the radio cache and the police armory could be rolled over. They still count toward the floor's container quota.

## 4. Integration notes

- Track A's grid classes replaced track B's stub classes. Track B's code compiled against the real API unchanged; `GridSync` (the stub sender) was dropped in favour of track A's `GridNetworking`.
- The HUD and Grid Map now receive the real `ashfall:grid_sync`: with track B's client receiver registered, `canSend` is true and the server sends on join and on every change.
- One vocabulary for states: the radio and the Grid Map now use track A's `district_state.ashfall.*` names, so a finished district reads "Reclaimed" everywhere. Track B's duplicate `grid.ashfall.state.*` keys were removed.
- The Field Radio points at the real Substation Core once the grid has registered it, and falls back to the authored map marker before that.
- New gametest `StoryGameTest.realGridChangeGrantsRelightAdvancement`: a real `GridState.update` grants the Relight advancement with no fake event.
- The salvage gametest world kept Lost Cities chunks generated before the safehouse and warehouse changes, so the new cache assertions failed on stale terrain. The old test dimensions were moved out of `modules/salvage/build/run/gameTest/world/` (kept in the session scratchpad); the next run regenerated them and passed.

## 5. Tests

`./gradlew build` passes: gametests arms 61, director 54, salvage 20; JUnit arms 43 (18 grid: `DistrictLoopTest`, `GridDataTest`; 6 story: `StoryLogicTest`, `RelayLogDataTest` including a check for borrowed franchise terms), director 83, salvage 49.

`StoryVisualClientTest` (client screenshots of the HUD and map) is written and registered but has not run yet: the client needs an unlocked screen.

## 6. Left for later

- Black Start (M9.5) sets `blackStartBonus` and grants `asheville_relit`.
- ~~`BROKEN` and Breach events (M8).~~ Done: see [hordes-v1.md](hordes-v1.md) section 3.
- Recipe unlocks gated by district (M6).
