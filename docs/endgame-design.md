# Ashfall: Relight Asheville (End Goal Design)

## 1. Pitch

Asheville didn't lose power all at once. The Wreck on Beaucatcher ridge pulled the grid down district by district through the Power Relays. Players get the city back the same way. They repair a dead substation in each district, hold it while the hum brings the horde in, and survive the siege that follows. Each district then lights up again. It gets free charging and a safe zone, but its lit streets also draw the dead at night. A Field Radio picks up broken Relay Logs that explain what the Wreck is: a living core, the Heart, which drained the grid and uses the virus to repair itself. Once four districts are lit, the group climbs the ridge, plugs the city into the Wreck and survives the Black Start. At the Heart they choose: **Sever** it or **Bind** it. The choice decides what kind of world the sandbox continues in.

## 2. End goal (win condition)

All 4 districts are Reclaimed, which brings the City Grid to 80%. The players then finish the Black Start at the Wreck, which brings it to 100%, and the party confirms Sever or Bind at the Heart. Grid % is shared across the whole world and shows on the HUD and on a Grid Map. The win is recorded as the advancement **"Asheville Relit"** for every party member, along with the ending flag.

## 3. Progression arc

| Act | Where | What players do | Unlocks | Reuses |
|---|---|---|---|---|
| **0. Static** (days 1-5) | Spawn area, any relay | Scavenge, set up a haven, build Spark/Volt blasters. A spawn-area Lootr cache holds the **Field Radio**. Right-clicking any of the 75 relays gives a **Relay Log**: one of about 30 written fragments, picked by hash(relay pos). Reading logs is optional. | "Relight" advancement tab (the quest log). The Grid Map shows 4 dark districts. | Power Relays, Lootr, advancements, building loot |
| **1. River Arts District** (tier 2) | Pump-house substation, Kiln & Co. Salvage warehouse | The district loop (below). The salvage crew that stripped the Wreck explains why blasters are loose in the city. The warehouse foundry is the **Abomination** lair. | City pump: clean water from taps in Reclaimed zones. Crystal 1. Ion Rifle cache. | Hydration, warehouse loot, Mutant Monsters |
| **2. South Slope** (tier 2) | Brewery-yard substation | Loop | Heat Sink and Regulator recipes at the Bench. Free relay charging on the way into downtown. | M6 Bench |
| **3. Downtown** (tier 3) | Parking-deck substation near Pack Square, police Lootr armory | Loop. Restored street lights now act as light stimuli, so the streets get more dangerous at night. | Pulse/Arc tier. Downtown relay ring. | Police Lootr, Director light sense |
| **4. Mission campus** (tier 4) | Hospital generator plant | Loop. Restoring the plant unseals the deep ward where **Patient Zero** waits. Infection pressure peaks here. | Crystal 2. **Patient Zero's Sample**. Hospital becomes a high-tier safe zone. | Contagion, Antibiotics, special infected |
| **5. Ridge approach** (tier 5) | Beaucatcher woods, Wreck crash sites | Collect Focusing Lenses and Wreck Alloy. The **Juggernaut** guards the sealed hull. | Crystal 3. The hull opens at Grid 80%. | Crash-site templates, Juggernaut |

**The district loop**, the same for each of the four districts:

- **Repair:** socket **Grid Couplers** into the Substation Core. A Coupler is Scrap Electronics, Battery, copper and Weapon Parts. It never needs a crystal.
- **Energize:** load Energy Cells. This starts a 5-minute hold while the Core hums at 3x relay radius.
- **Link:** touch the 6-8 key relays in the district with a Coupler.
- **Siege:** survive the next night's siren and the District Siege.

Building clearance is an optional bonus whose threshold is set in JSON. It becomes active only once Director I populations exist.

**Reclaimed** means:

- The district gets an OPAC claim, and darkness spawning drops by 80%.
- Linked relays and Field Chargers run without cells.
- Lamps relight lazily as each chunk loads, using the district's state.
- A per-player Lootr reward cache appears.

**Siege tier = max(day tier, Grid tier).** Making progress raises the danger, so a lit district is never a free safe zone.

## 4. Finale: Black Start, then the Heart

Players craft the **Grid Uplink** from the 3 boss crystals, 4 Focusing Lenses, 8 Couplers and 16 Energy Cells, then seat it in the hull. The event lasts 12-15 minutes:

1. **Surge.** The Uplink is the loudest noise and brightest light in the game. Waves spawn at the ridge edge. They don't path from across the map, and the finale is capped at about 120 mobs. Each Energy Cell fed into the Uplink buys 60 seconds; if the buffer runs dry, progress pauses.
2. **Feedback.** Two **Relay Breakers** about 40 blocks apart trip, and each needs a player holding it. Special infected pour out of hull vents, and the Uplink takes damage that is patched with Couplers.
3. **The Heart.** Four crystal pylons, as blocks, have to be broken, and Plasma Blades do bonus damage to them. Meanwhile **the Choir**, a crystal-shard Abomination variant, defends the Heart. It is a reskin with no new boss AI.

As each phase ends, a band of districts lights up below the ridge in real time. Waves and timers scale with how many players are online: solo gets about 60% of the waves and longer breaker timers. Roles come from gear rather than classes: engineer (cells and repairs), marksman (Lancer or Storm), blade (pylons and the Choir), runner (Antibiotics and Cell Packs). **Failing costs time, not the run.** The Uplink drops back to a repairable state and the crystals are kept.

**The choice.** A party vote, or a confirm screen when playing solo, picks the ending:

- **Sever.** The Heart goes dark and the virus goes dormant: infection no longer progresses past stage 2. Antibiotics stay the only medicine, and no cure item is added. Spawning falls to about 40%, horde nights become rare Aftershocks, and every relay becomes a free Grid Point. This is the rebuild ending.
- **Bind.** The party controls the Heart. They get a Resonant Plasma Blade, blaster Overcharge mods and Bunker Cores powered by the Heart. The virus keeps evolving: horde nights grow weekly up to a **hard cap**, and an Ascendant boss returns every 10 nights. This is the veteran ending.

Both endings show an ending stats screen (days survived, districts lit, friends lost to the infection). Both also give an epilogue book assembled from the Relay Logs the players found.

## 5. After the ending

The sandbox carries on in the world the ending produced:

- **Breach events:** a horde hits one random substation each week. If the Core breaks, that district goes dark until it is re-repaired at half cost.
- **Aftershock crash sites:** these keep Resonance Crystals and rare blasters obtainable, which offsets blade wear.
- **Wild outskirts:** the far bank of the French Broad and the map edges stay dark with full spawning.
- **Bind only:** a nights-survived counter on the Bunker Core.
- **Lore:** Relay Logs you missed stay collectible and fill in the epilogue.
- **Replay:** `/ashfall story reset` lets a group replay and pick the other ending.
- **Pure sandbox:** the gamerule `ashfall:objective=false` keeps the original sandbox. Even with it on, nothing forces players into the arc.

## 6. New vs reused

**New:**

- Grid/story SavedData, shared world-wide so any player's progress counts for everyone.
- District JSON: 4 polygons, tiers and rewards. Districts are tagged in `buildings.json`.
- Substation Core block and template.
- Grid Coupler, Wreck Alloy, Grid Uplink.
- Field Radio and the Relay Log pool (about 30 texts plus 3 site documents).
- Grid Map screen and HUD Grid %.
- District Siege and Breach scheduling.
- Black Start controller: phases, cell buffer, breakers, scaling.
- Heart pylon structure, the Choir variant and the hull template.
- Ending vote, the Sever/Bind rule profiles and the epilogue builder.
- **An atlas pass that logs stripped light positions.** M1 only counts them today (glowstone went from 201,785 to 5), so relighting can't work without this.

**Reused:**

- 75 Power Relays and their hum/SOUND stimulus.
- Energy Cells, Field Charger, blasters, Dampener.
- Building loot and Lootr caches (radio, police, hospital, rewards).
- OPAC claims, behind an adapter with a fallback exclusion radius.
- ZB&B fortified blocks.
- Director I darkness, noise and light senses.
- Undead Nights, then M8 hordes.
- M9 special infected.
- Mutant Monsters bosses, each with a guaranteed crystal.
- Contagion, Antibiotics. (Zombified Player was removed in 10.3.0; an infected death drops items like any other death.)
- Hydration.
- Plasma Blades and the Weaponsmith Bench.
- Crash-site and FEMA templates.
- Ashfall's own torch burnout (10.3.0, replacing Realistic Torches), which makes grid lamps valuable.

## 7. Fit with the milestone plan

| Milestone | Change |
|---|---|
| M2 | Spawn-weight hook per district. Atlas logs light positions. (+0.5 wk) |
| M4 | Bosses get guaranteed crystal drops and story lairs. Hull and substation templates are added. (+0.5 wk) |
| **M4.5 Grid I (new)** | Substation Core, Coupler, grid SavedData, the loop on Undead Nights sieges, Field Radio and logs, advancement tab, lazy relight. (2 wk) Ships with the MVP, so the end goal exists early. |
| M6 | Recipe unlocks gated by district. |
| M8 | Siege tier = max(day, grid). Breach events. |
| **M9.5 Black Start (new)** | Finale controller, Heart pylons, the Choir, ending profiles, epilogue, stats screen. (2.5 wk) |
| M10 | Bind cap and finale mob budget checked with spark. 2-player LAN run through the whole arc. |

**Total: about 6 weeks** on top of the existing plan.

**Status (2026-10-26, pack 0.8.0).** M2 and M4 rows are done (phases 1-2). **M4.5 Grid I is done** ([specs/grid-v1.md](specs/grid-v1.md)): Substation Core (the landmark placeholder converts on first load), Grid Coupler, `GridState` saved data with the 20 %-per-district Grid % capped at 80, the full DARK → REPAIRED → ENERGIZED → SIEGE → RECLAIMED loop with the hum, hold, key-relay linking and District Siege, the Reclaimed effects (safe compound, 0.2× darkness spawning, free charging, reward cache, city pump, Mission ward unseal), lazy relight from the Relight Log, the Field Radio, 30 Relay Logs and 3 site documents, the 22-advancement "Relight" tab, the Grid HUD and the Grid Map. Deviation from the row above: district sieges run on the Director's own spawning (`DirectorSpawns`), not on Undead Nights horde nights; the warning goes through the haven horde siren (`HordeSiren.alert`). M8 (hordes, Breach, `BROKEN`, siege routes) is in [specs/hordes-v1.md](specs/hordes-v1.md). Not yet: M6 district recipe gates, M9.5 Black Start (`blackStartBonus` and the `asheville_relit` advancement are in place, waiting for it). **Update (Phase 6, track A):** the Black Start's phases 1-3 are built ([specs/black-start-v1.md](specs/black-start-v1.md)): the three-step Grid Uplink, the hull sealed until Grid 80%, the Surge, Feedback and Heart phases, the Choir, and Grid 80 → 87 → 94 → 100. The ending choice is track B's.

**Status (phase 6, endings).** The ending layer of M9.5 is in place ([specs/endings-v1.md](specs/endings-v1.md)): `Ending.beginChoice` is called by the finale and opens the party vote (30 s, majority, re-prompt once and then Sever, a confirm step when solo). The data-driven Sever/Bind profiles (`data/ashfall/ashfall/ending/`) do the following:

- Sever: Antibiotics cure, spawning x0.4, horde nights x3 rarer, free relays.
- Bind: the Resonant Plasma Blade, Overcharger unlocked, Bunker Core havens +2 chunks, weekly horde growth capped at tier VI, and the Ascendant at the hull every 10 days.

Also in place: the Field Report and epilogue books, the `asheville_relit` advancement, Aftershock crash-site passes, the `ashfall:objective` gamerule, and `/ashfall story reset`, which resets the ending, the finale and the Black Start bonus but keeps reclaimed districts.

**Status (2026-10-26, pack 0.10.0: phase 6 integrated).** M9.5 is complete end to end. The finale ([specs/black-start-v1.md](specs/black-start-v1.md)) and the ending layer ([specs/endings-v1.md](specs/endings-v1.md)) are joined:

1. The Heart falls and band 3 lights (Grid 100 %).
2. Asheville Relit is awarded.
3. The party vote opens: 30 s, majority of online party members, Sever by default after one re-prompt.
4. The Sever or Bind profile applies, the Field Report and epilogue books arrive, and the ending advancement fires. Sever means Antibiotics fully cure.
5. `/ashfall story reset` forgets the ending and rearms the finale to IDLE for a replay.

One gametest drives this whole chain. Also in 0.10.0: the M7 admin GUIs ([specs/admin-gui-v1.md](specs/admin-gui-v1.md)), the Loot Zone screen and the data-driven Mob Drops with their screen. Still open: M6 district recipe gates, and the M10 spark check plus a 2-player LAN run of the whole arc (the finale has not yet been played on a real ridge).

## 8. Decisions for you

1. **Sever and infection.** Does Sever stop infection at stage 2, or let Antibiotics fully cure it? *Recommend stopping at stage 2.* It adds no new cure and respects the removal of the Antiviral.
2. **Number of districts.** *Recommend 4 plus the ridge* rather than 6, to keep the grind down. Montford stays the unscored starting area.
3. **Ending lock-in.** *Recommend a party vote* (majority of online party members, 30-second window) plus `/ashfall story reset`.
4. **Building clearance.** *Recommend an optional bonus at 40%* in JSON, required only if playtests show the loop feels too thin.
5. **Bind cap.** *Recommend capping escalation at Siege tier VI* and the finale mob limit (about 120), so LAN hosts stay stable.
---

## Decisions (user, 2026-10-25)
- **End goal adopted as designed:** Relight Asheville, the Black Start finale, and the Sever or Bind choice.
- **Sever ending: Antibiotics fully cure infection** after Sever. Overrides the recommendation of stopping at stage 2. There is still no cure item, and Antiviral stays removed.
- **Districts:** 4 (River Arts District, South Slope, Downtown, Mission campus) plus the ridge.
- **Ending choice:** a party vote (majority of online party members, 30-second window), plus `/ashfall story reset` to replay.
