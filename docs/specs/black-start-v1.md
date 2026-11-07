# Spec: Black Start finale (2026-10-26, plan M9.5, phase 6; integrated in 0.10.0)

Module: **arms**, package `arms.finale`. One template change in **salvage** (the Uplink Socket placeholder in the Wreck hull). This covers endgame-design section 4, phases 1-3: the Grid Uplink, the sealed hull, the finale controller, the Relay Breakers, the Heart pylons and the Choir. The ending choice (Sever or Bind) is the ending layer ([endings-v1.md](endings-v1.md)). The finale calls it through `arms.story.Ending.beginChoice(ServerLevel, Collection<ServerPlayer>)`; section 10 describes the full chain. All numbers are in `data/ashfall/ashfall/finale/settings.json` and reload with `/reload`.

## 1. The Grid Uplink (three crafts)

The design's cost is 3 Resonance Crystals, 4 Focusing Lenses, 8 Grid Couplers and 16 Energy Cells, 31 items in total. A crafting grid uses up one item per slot, so this cannot be done in one craft, or in two (18 slots). Two intermediate items carry the cost instead. The totals match the design exactly.

| Craft | Recipe | Pattern |
|---|---|---|
| Uplink Frame (`ashfall:uplink_frame`) | 4 lenses, 3 crystals, 2 couplers | `LRL / CRC / LRL` |
| Capacitor Bank (`ashfall:capacitor_bank`), made twice | 8 Energy Cells around 1 coupler | `EEE / ECE / EEE` |
| Grid Uplink (`ashfall:grid_uplink`) | frame, 2 banks, 4 couplers | `CBC / _F_ / CBC` |

The Uplink and the frame are fire resistant, because their crystals are.

## 2. The hull and the socket

- **Socket.** The hull template (`tools/sites/build_sites.py`) has a lodestone on the deck, where the hatch corridor arrives. It carries the marker `uplink_socket`. Arms replaces it with the **Uplink Socket** (`ashfall:uplink_socket`: unbreakable, immovable, no block entity, light follows `power` 0-2) when the hull is placed (`Sites.PLACED`). For a hull placed before this change, the swap happens the first time its chunks are loaded. Older placements find the socket's position from the stored origin plus the current template.
- **Seal.** The hatch's two iron doors become the **Hull Seal** (`ashfall:hull_seal`, unbreakable). This matters because a button placed next to an iron door would open it. While the hull is sealed, survival players also cannot break any block in its footprint, since a seal you can tunnel around does nothing.
- **Opening.** When the Grid reaches 80% (all four districts RECLAIMED), the finale broadcasts that the hatch has unsealed. The seal is removed the next time the hatch's chunk is loaded. `/ashfall finale open` does the same on demand. Once open, the hatch stays open even if a district later breaks. The state lives in `data/ashfall_finale.dat` (`FinaleState`: the run plus `hull_prepared`, `open_requested` and `hull_open`).

## 3. The run

Seating the Uplink in the socket starts the run. In the world this needs Grid 80%, and only one run can be active at a time. `FinaleLogic` holds the rules as pure functions. `FinaleController` measures the world and acts on it once a second, and only while the socket's chunk is loaded. Otherwise the run waits, which also makes restarts harmless.

| | Surge | Feedback | The Heart |
|---|---|---|---|
| Goal | 450 s of **powered** time | Close both Relay Breakers | Break the four Heart pylons |
| Buffer | The Uplink brings 60 s of charge. Each full Energy Cell fed into the socket adds 45 s (cap 600 s; balance-v1, was 60 s). An empty buffer stops progress (the boss bar reads "buffer empty") | Not used | Not used |
| Threat | Waves every 30 s | Waves every 45 s; vents every 20 s; a feedback arc every 30 s (60 HP) | Waves every 60 s; the Choir |
| End | Band 1 lit, Grid 87% | Band 2 lit, Grid 94% | Band 3 lit, Grid 100%; ending choice; advancement |

- **Noise and light.** Every 4 s the running Uplink emits a 192-block SOUND and a 128-block LIGHT stimulus, with the relay hum. These are the largest radii in the game (the Substation hum is 144).
- **Waves** go through the horde scheduler (`HordeNights.spawnWaveAt` aimed at the socket). They appear in the 40-64 block ring at the ridge edge, out of sight and outside claims, with the scheduler's specials roll. Each wave becomes one horde sent at the Uplink, so nothing paths in from across the map. The table is tonight's horde tier, which at Grid 80% is at least the grid tier. The size is 22 times the party scale.
- **Mob cap.** Every finale mob carries the `ashfall_finale` tag. No wave or vent spawns while 120 of them are alive within 128 blocks of the socket. The horde scheduler's own caps also apply: its per-player horde cap (60, `max_alive_per_player`) is counted around the Uplink and multiplied by the party size (so solo holds 60 and two or more players reach 120), and the Director's hard caps (120 near the socket, 150 world) stay on top. Before the phase 6 playtest the per-player cap was not scaled, so every party size stopped at 60.
- **Relay Breakers** (`ashfall:relay_breaker`, unbreakable, `triggered` true or false). When Feedback starts, two breakers are raised on the ground 20 blocks either side of the socket, 40 apart. Each needs a living player within 3 blocks for 75 s. An unattended breaker keeps its progress for a grace period, then slips back at 1x. The grace is 10 s times the party scale: 20 s solo, 15 s for two players, 10 s for three or more. Those are the "longer breaker timers" for solo play.
- **Vents.** During Feedback, specials (crawler, spitter, screamer, bloated, brute) come out of two spots inside the hull, 9 blocks west and 8 east of the socket, and head for the Uplink. Their count is 2 times the party scale. A kind that does not fit falls back to a crawler.
- **Uplink health** is 1200. Every zombie within 6 blocks of the socket horizontally (and 2 vertically) takes 1 HP per second off it, in every phase, and the Feedback arcs add their damage. Right-clicking with a **Grid Coupler** patches 200 HP. (Balance-v1: the shipped 200 HP at 2 HP per zombie-second fell to one solo wave in about 20 s; now it lasts about a minute, so a solo player can hold the breakers. 1.0.0-rc1: `uplink.attack_radius` 3 → 6, because waves arriving from the north stop against the hull wall 4.3-5.5 blocks from the socket and never hurt it; see [../rc1-report.md](../rc1-report.md).)
- **The Heart.** Four **Heart pylons** (`ashfall:heart_pylon`, in `#ashfall:crystal_pylons`) rise in the hull at socket offsets (-8,0,±2) and (6,0,±2). They have hardness 80 and are in no mineable tag. A pickaxe is no better than a fist (about 120 s each). A lit Plasma Blade mines at speed 12 (blade stats), about 10 s. The pylons drop nothing, and the count only goes up: the controller reads the four positions, so a pylon removed in any way counts.
- **The Choir** (`ashfall:choir`, in `#minecraft:zombies`) is a zombie with no new AI. It has 420 HP, 12 damage, armour 10, toughness 4, full knockback resistance, 1.8x scale, does not burn in sunlight, never despawns, and does not spawn babies, reinforcements or gear. Its attributes come from `heart.choir_attributes`. It rises once per run beside the Heart, has its own pink boss bar, and walks back to the Heart when it has no target and is more than 14 blocks away. A Choir the players kill stays dead. When the last pylon breaks, it shatters with the Heart. The client draws it with the vanilla zombie renderer under a pale crystal tint, plus a shard overlay (`tools/textures/entities/choir.py`).
- **Party scaling** (`scale`, indexed by the number of players within the presence radius): waves x0.6, 0.8, 1.0, 1.15, 1.3. Solo play gets 13 per wave instead of 22.

## 4. Failing costs time, not the run

- **Two ways to fail.** The run drops to **PAUSED** if the Uplink reaches 0 HP (`uplink_down`), or if no living player stays within 96 blocks for 20 s (`abandoned`: everyone died or left).
- **What is kept.** The Uplink and its crystals stay seated. Phases already won stay won, with their bands lit and their Grid bonus. Broken pylons stay broken.
- **What is lost.** 60 s of Surge progress, or both Feedback breakers tripping open again.
- **Coming back.** Feeding a cell or patching with a coupler resumes the run, once the Uplink has at least 50% HP and the buffer holds charge. The boss bar and the status line say what is missing.
- **Waiting while paused.** A paused run takes no damage and spawns nothing.

## 5. Phase ends and the finish

- **Lighting a band.** Each phase end lights a band of districts below the ridge in real time: River Arts and South Slope, then Downtown, then Mission.
  - Every loaded chunk of those districts replays its logged lights through `GridRelight.relightLoaded`. The rest of the district relights lazily, as before.
  - A soundless lightning bolt flashes over the Wreck, and every player hears thunder and a beacon chime.
  - A title and a chat line show the new Grid %.
- **Grid % and sync.** The Grid % comes from `GridState.setBlackStartBonus` (7, 14, 20). The change is sent to every HUD (`GridNetworking.sendAll`) and fires the Grid % advancement triggers (`Story.fireGrid`).
- **The finish.** The run is COMPLETE. Every participant who is online gets `ashfall:relight/asheville_relit`, through its `black_start` criterion. A participant who was offline gets it on their next join. Then `Ending.beginChoice(level, participants)` runs. Participants are everyone who stood within the presence radius while the run was going.

## 6. Commands

`/ashfall finale status | start [<pos>] | phase <surge|feedback|heart> | abort | open` (op).

- `start`: seats an Uplink in the hull's socket, or at `pos` (a socket is placed there if missing). It needs no item and ignores the Grid gate.
- `phase`: jumps to a phase. Earlier phases count as won, so their bonus applies, the bands they would have lit relight in loaded chunks (phase 7; `FinaleController.jumpTo`, `Effects.bandRelit`), and the phase's blocks and the Choir appear. Jumping back relights and darkens nothing.
- `abort`: removes the breakers, pylons and the Choir, resets the run to IDLE and sets the bonus back to 0.
- `open`: opens the hatch now, or when it next loads.

## 7. Art

- **Block textures:** `tools/textures/blocks/{uplink_socket_*,relay_breaker_*,heart_pylon,hull_seal}.py`, with the shared `_finale.py`.
- **Item icons:** `tools/textures/items/{uplink_frame,capacitor_bank,grid_uplink}.py`.
- **Choir overlay:** `tools/textures/entities/choir.py`, which `gen_entity_textures.py` writes into arms.
- **Models, blockstates and item definitions:** `tools/models/finale_blocks.py`, run by `tools/gen_models.py`.

## 8. Tests

- **JUnit `FinaleLogicTest` (18 tests).**
  - Seating, the buffer pausing the Surge, the buffer cap.
  - Phase ends stepping the Grid 87, 94, 100.
  - Breaker holds and grace; solo grace and wave size; the mob cap.
  - Failing into PAUSED with its setback; resuming by patching or with a cell; the abandon timer; the Feedback re-trip.
  - Pylon counting; phase jumps; clocks.
  - The shipped JSON equals the code defaults and the design numbers.
  - The fixed parts of the timing fit a 12-15 minute finale.
- **Gametest `FinaleGameTest` (12 tests).** Each test owns its own socket and run, with tiny numbers and recording effects, so no test touches the world's run or the real Grid %. The exception is the end-to-end test (section 10), which sets and undoes the world's ending inside one tick.
  - A full run through all three phases.
  - An empty buffer pausing the Surge.
  - A breaker that needs a player within 3 blocks.
  - Broken pylons counted, including the Plasma Blade's mining speed on the real block.
  - A battered Uplink pausing, then resuming with couplers.
  - Everyone leaving pausing the run.
  - The Choir rising and shattering.
  - The hull seal.
  - The real hull template, placed under a test key, gets its socket and seal, with room for the pylons, vents and the Choir.
  - The three-step craft.
  - The settings loading.
- **Client shot `p6-finale-blocks-choir`** (added to `ArmsVisualClientTest`, not run because the screen is locked): the six block states and the Choir. The inventory shot also gains the three craft items.

## 9. Left for later

- Playtest the numbers. Wave sizes, Uplink HP and arc damage were retuned by the offline sim ([../balance-v1.md](../balance-v1.md)); the hold time and the Choir's stats are still first guesses. The phase 6 playtest ran it headless on a real ridge (docs/phase6-report.md): both breakers were raised and closed, the four pylons and the Choir appeared at their offsets, and waves spawned 40-60 blocks out, but where the breakers stand on steep ground was not inspected, and an undefended Uplink falls to one 8-zombie solo wave in about 40 s, which only a real party can judge.
- ~~A replay needs a new Grid Uplink.~~ Done in phase 7: the reset leaves the Uplink in its socket (section 10, step 5).

## 10. From the Heart to the ending (integration, 0.10.0)

The three phase 6 tracks were merged in 0.10.0. The track A stub `Ending` was replaced by the real class from the endings track. The whole chain now runs:

1. **The Heart falls.** The last pylon breaks, `FinaleController.endPhase` marks the run COMPLETE, the Choir shatters, and band 3 lights (Grid 100 %).
2. **Relit.** `FinaleEffects.completed` awards `ashfall:relight/asheville_relit` (criterion `black_start`) to every online participant. An offline participant gets it on their next join. The same advancement also has the `ashfall:story_ending` trigger, so the ending awards it as well, and it does not matter which comes first.
3. **The vote.** `Ending.beginChoice(level, participants)` opens the vote. The voters are the participants plus their online OPAC party members. It lasts 30 s, a strict majority ends it early, a tie re-prompts once and then defaults to Sever, and a solo player must confirm. It does nothing when the world already has an ending or the gamerule `ashfall:objective` is false.
4. **The ending applies.** `Ending.apply` does the following:
   - records `StoryState`;
   - sets the director story flags (`ashfall:sever`, the Antibiotics full cure, or `ashfall:bind`);
   - applies the data-driven profile in `data/ashfall/ashfall/ending/{sever,bind}.json`;
   - keeps the Black Start bonus at 20 or more;
   - rebuilds havens;
   - gives each voter the Field Report and the epilogue book plus the profile's rewards (Bind: the Resonant Plasma Blade);
   - fires the advancement trigger and `Ending.CHOSEN`.
5. **The replay.** `/ashfall story reset` runs `Ending.reset`. That cancels any vote, forgets the ending and its flags, sets the bonus to 0 and fires `Ending.RESET`.
   - **The finale rearms on `RESET`** (`Finale.init`). A COMPLETE (or interrupted) run refuses a new start, so the world's run is aborted back to IDLE: pylons, breakers and the Choir are removed and the socket is shown empty.
   - The hull stays open, because the reset keeps the reclaimed districts it was opened for.
   - **The hand-back (phase 7).** The spent Uplink stays in its socket, powered down (`FinaleRun.standby`, saved; the socket glows at power 1 and reads "waits in its socket"). Using the socket with an empty hand starts the replay from a fresh Surge (`FinaleActions.restart`, same Grid gate as seating). A new Uplink brought to a socket in standby powers up the old one and stays in hand. Why the socket and not the participants: a socket cannot be offline, die with it, lose it as a despawning item, or get one copy per participant. `/ashfall finale abort` still takes the Uplink out; `/ashfall finale start` uses it up.

Gametest `theHeartFallingOpensTheVoteAndTheEndingApplies` (in `FinaleGameTest`) covers this chain:

- a test-owned run whose completion goes through the world's effects;
- the Heart falls;
- Asheville Relit is awarded;
- the vote opens, and the solo player votes Sever and confirms;
- the Sever flag and Grid 100 % are set, and both books arrive;
- `Ending.reset` takes a COMPLETE world finale back to IDLE.

`./gradlew build --console=plain` passes on the merged tree: gametests arms 94, director 70, salvage 22, plus JUnit.

## Headless and admin runs (phase 6)
- `/ashfall finale feed [<cells>]` and `/ashfall finale patch [<couplers>]` (1.0.0-rc1) feed Energy Cells and patch Grid Couplers from the console through the same rules as a player at the socket, with no items. With stand-ins nobody can do either, so a headless run stalled for good the first time a wave knocked the Uplink out.
- `/ashfall finale unattended on|off|<players>` counts that many stand-in players at the Uplink and at both Relay Breakers, so a dedicated server with nobody online can run the whole finale (it never pauses as abandoned, breakers close after their 75 s hold, waves and the horde cap scale with the stand-in count). It is kept in memory only and never applies to gametest instances.
- When the Heart falls with nobody online, the ending vote has no voters: two silent 30 s rounds fall to the Sever default (decision 3), and the log says so. `/ashfall ending set sever|bind` from the console closes a running vote instead, rewarding the vote's online participants.
- `/ashfall finale start` on a finished world says the run is complete and points at `/ashfall story reset`.
