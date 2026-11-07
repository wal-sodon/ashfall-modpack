# Ashfall

**Version 1.0-rc.** A Fabric **Minecraft 26.3** modpack for singleplayer and 2-4 player co-op: a zombie
apocalypse in a ruined, generated **Asheville**, with original energy weapons and a story you finish.

The power went out district by district. You start in the Montford safehouse with a Field Radio, and work your
way towards **relighting Asheville**:
1. **Scavenge.** Boil dirty water, keep your lights hidden, build energy blasters.
2. **Set up a haven.** A Bunker Core claims your base.
3. **Relight four districts.** Repair each substation, hold it while the hum brings the dead, link its relays, and
   survive the District Siege.
4. **Kill the bosses.** The Abomination, Patient Zero and the Juggernaut each drop a Resonance Crystal.
5. **Finish the Black Start.** Climb to the Wreck on the ridge, seat the Grid Uplink and hold out through the Surge,
   the Feedback and the Heart.
6. **Choose.** The party votes to **Sever** the Heart or **Bind** it, and the world carries on in the shape that
   choice gives it.

Every new world builds the same city layout with [The Lost Cities](https://modrinth.com/mod/the-lost-cities) and
Ashfall's own profile:
- fixed districts: Downtown, River Arts, South Slope, Mission, the Montford start area, suburbs, wasteland and ridge;
- fixed landmarks: the safehouse spawn, police station, hospital, Kiln & Co. Salvage, four substations, pharmacy
  and the Wreck hull.

The ordinary buildings change with the seed.

## Guides

| Guide | For |
|---|---|
| [docs/player-guide.md](docs/player-guide.md) | **How to play**, from the first night to after the ending: water, light and noise, blasters and chargers, havens, infection, hordes, special infected, the district loop, bosses, the Bench, Plasma Blades, the Black Start, Sever vs Bind, and every recipe |
| [docs/install-and-server.md](docs/install-and-server.md) | **Installing and hosting**: Prism or the Modrinth app, memory, Open to LAN and e4mc, dedicated servers (mrpack-install or manual), configs, Chunky pregeneration and the atlas pass, the full admin command reference, game rules, troubleshooting |
| [CREDITS.md](CREDITS.md) | Every bundled asset and library and its licence, and the mods the pack references |
| [docs/versions.md](docs/versions.md) | **Version numbers**: what makes an update major, medium or minor, and the old rc labels mapped to the new numbers |
| [docs/endgame-design.md](docs/endgame-design.md) and [docs/specs/](docs/specs/) | The design and the per-system specs, with every number and the file it lives in |
| `docs/phase*-report.md` | Milestone playtest reports and known issues |

In game, every player is briefed the first time they join a world: a title, a short chat debrief and a **Mission
Briefing** book (the situation, the mission, the district loop with the live datapack numbers, getting stronger,
staying alive and the finale). After that:
- **`/ashfall mission`** shows the whole mission as a checklist, the current step, where it is and how to do it;
- the **objective line** left of the hotbar names the next step with its distance and heading (hide it with the
  "Toggle Objective Line" key, unbound by default);
- the **Field Radio** repeats the objective and points home (your Bunker Core, else the safehouse); sneak and use it
  for just those two lines;
- the **Grid Map** (G) marks your Bunker Core and the objective's target.

The safehouse radio cache still holds the **Survivor's Notes**, a four-page primer, next to the Field Radio.

## Layout

| Path | What |
|---|---|
| `modules/salvage` | City zones over The Lost Cities, building context (`BuildingSource`), the first-load salvage passes (loot, lights, damage, relays), crash sites, loot zones (`ashfall_salvage`) |
| `modules/director` | Zombie spawning/senses/targeting, building populations per Lost Cities building and zone, horde nights and dynamic hordes, special infected, havens and claims, infection, boss lairs, hooks into reused zombie mods (`ashfall_director`) |
| `modules/arms` | Energy weapons, Plasma Blades, the Weaponsmith Bench and attachments, Cell Packs, Energy Cells, chargers, the City Grid and Breaches, boss and crash-site loot (`ashfall_arms`) |
| `pack/mods.json` | Pinned third-party mods (Modrinth version ids) |
| `pack/overrides/` | Config files shipped in the pack |
| `tools/` | Pack builder, landmark asset generator (`tools/landmarks/`), texture/model generators, scripted server sessions |
| `archive/arnis/` | The retired real-world Arnis map: patch, OSM data, worlds, pipeline, old sidecar code (not built; see its README) |

## City generation

The Lost Cities generates the city as players explore; nothing is downloaded or post-processed offline.

| Piece | Where |
|---|---|
| Lost Cities profile `ashfall` (picked for the overworld at server start) | `modules/salvage/src/main/resources/ashfall/lostcities/ashfall_profile.json`, `pack/overrides/config/lostcities-server.toml` |
| Zones: fixed discs and rings in block coordinates, with city style, ruin chance, damage, population and darkness scales | `modules/salvage/src/main/resources/data/ashfall/ashfall/zones/*.json` (`ZoneMap`) |
| World style, city styles, landmark buildings, multibuildings, parts, predefined landmark chunks | `modules/salvage/src/main/resources/data/ashfall/lostcities/` (generated by `tools/landmarks/build_landmarks.py`) |
| Generic houses, offices, shops and streets | Lost Cities Modern Tweaks, Lights Off edition (pinned in `pack/mods.json`) |
| Building context for loot, refill and the Director | `BuildingSource` / `LostCitiesBuildingSource`: one instance per building (a multibuilding's chunks share one), read from Lost Cities' chunk plans |
| First-load chunk pass: light stripping (logged for the relight), zone-aware damage, street relays, landmark caches | `modules/salvage/.../atlas/` (`/ashfall city processall [radius]` after pregeneration) |

Use `/ashfall city zone` and `/ashfall city info` in game to see the zone and plan of the chunk you stand in.

## Features

| Area | What | Spec |
|---|---|---|
| Zombies | Director spawning by zone, time and building (every difficulty rule counts days on the day clock, so sleeping never desyncs it); senses (noise, light, smell, blood trails); per-building populations; ZB&B building/breaking with fortified blocks | `docs/specs/director-v1.md` |
| Hordes | Our own horde nights (every 5 nights, fixed per world, tier = max(day, Grid)) with waves that converge on your Bunker Core; dynamic hordes that form from wandering zombies and hear twice as far; zombie classes unlock by day | `docs/specs/hordes-specials-v1.md`, `docs/specs/hordes-v1.md` |
| Special infected | Crawler, Bloated, Spitter, Screamer, Stalker, Brute: per-mille spawn layer by day and zone, capped per player, and they join horde nights, sieges and Breaches | `docs/specs/hordes-specials-v1.md` |
| Weapons | Seven energy blasters, Cell Packs, Energy Cells, chargers and relays | `docs/specs/hydration-and-arms-v1.md`, `docs/specs/blaster-balance-v2.md` |
| Plasma Blade | Rare melee weapon (needs a Resonance Crystal): charged at chargers, hits at most 3 targets per sweep (60% past the first), costs hunger per extra target, its crystal wears out and can be resocketed; a lit blade draws zombies | `docs/specs/blades-bench-v1.md` |
| Weaponsmith Bench | Fit attachments to blasters: Optic, Dampener, Heat Sink and Cell Regulator (South Slope reclaimed), Overcharger (Downtown reclaimed); Regulator and Overcharger share a slot | `docs/specs/blades-bench-v1.md` |
| Survival | Thirst (dirty water infects), bandages, antibiotics | `docs/specs/hydration-and-arms-v1.md` |
| Havens | Bunker Core (5×5-chunk claim, one per party), Montford safehouse safe zone, respawn choice with 10 s protection, horde siren, electric lights | `docs/specs/havens-v1.md` |
| Infection | Four stages over 20 min, then death 8 min later; Bandage pauses, Antibiotics step back a stage (cure at stage 1, cure at any stage after the Sever ending); infected players rise as zombies with their gear (and can't fire it) | `docs/specs/havens-infection-bosses-v1.md` |
| Bosses | Abomination (Kiln & Co.), Patient Zero (sealed hospital deep ward), Juggernaut (the Wreck hull); each drops a Resonance Crystal | `docs/specs/havens-infection-bosses-v1.md` |
| Crash sites | Seed-placed wreck sites in the ridge and wasteland with guards and rare blaster loot | `docs/specs/havens-infection-bosses-v1.md` |
| City Grid | Relight Asheville: repair each district's Substation Core with Grid Couplers, energize it with Energy Cells under a 5-minute hold, link its key relays, survive the District Siege at nightfall. A Reclaimed district gets a safe compound, 80% less darkness spawning, free charging, a reward cache, its lights back, and an extra (River Arts: clean water; Mission: Patient Zero's ward opens). Grid +20% per district, 80% before the Black Start. Weekly Breaches hit a reclaimed substation; losing one leaves the district BROKEN until re-repaired at half cost | `docs/specs/grid-v1.md`, `docs/specs/hordes-v1.md` |
| Story | Field Radio (Montford safehouse) with grid status and headings; 30 Relay Logs tuned from Power Relays plus 3 site documents; the Survivor's Notes primer in the radio cache; the "Relight" advancement tab; Grid HUD and Grid Map (key G) | `docs/specs/grid-v1.md` |
| Black Start | The finale at Grid 80%: craft the Grid Uplink (three crafts), seat it in the sealed Wreck hull's socket, hold out through Surge (feed Energy Cells), Feedback (two Relay Breakers) and the Heart (four pylons and the Choir); each phase lights a band of the city, up to Grid 100% | `docs/specs/black-start-v1.md` |
| Endings | When the Heart falls the party votes Sever or Bind (30 s, majority, solo confirms). Sever: Antibiotics fully cure, spawning ×0.4, rare Aftershock hordes, free relays. Bind: the Resonant Plasma Blade, Overcharger unlocked, bigger havens, hordes that keep growing (capped at tier VI), the Ascendant. Field Report and epilogue books; `/ashfall story reset` replays the finale and the choice (the Grid Uplink stays in its socket: use it with an empty hand) | `docs/specs/endings-v1.md` |
| Mission | First-join briefing (title, chat, Mission Briefing book with numbers read from the datapack), a live objective computed from the real grid, boss, finale and ending state (HUD line, `/ashfall mission` checklist, Field Radio), the way home on the radio and the Bunker Core on the Grid Map; quiet under `ashfall:objective false` | `docs/specs/mission-v1.md` |
| Admin GUIs | Loot Zone screen (container type, radius, loot table, regen, refill timing; export/import) and Mob Drops screen (data-driven per-entity drops, saved into a world override datapack); op-only, checked on the server | `docs/specs/admin-gui-v1.md` |

## Commands

The full admin reference, with every argument, is in
[docs/install-and-server.md, section 7](docs/install-and-server.md#7-admin-command-reference). In short (op-only
unless marked):

| Root | Covers |
|---|---|
| `/ashfall city`, `/ashfall atlas`, `/ashfall site` | Zones, the first-load atlas pass (`processall`), crash sites |
| `/lootzone` | Hand-made loot zones and the Loot Zone screen |
| `/mobdrops` | Data-driven mob drops and the Mob Drops screen |
| `/ashfall director`, `/ashfall debug stimuli`, `/ashfall horde`, `/ashfall infection`, `/thirst` | Spawning, senses, horde nights and Breaches, testing aids |
| `/ashfall safezone`, `/ashfall respawn` (everyone) | Safe zones, the respawn choice |
| `/ashfall boss`, `/ashfall grid`, `/ashfall finale` | Boss lairs, the City Grid, the Black Start |
| `/ashfall ending` (vote, confirm, status and epilogue for everyone), `/ashfall story` | The ending vote and the replay reset |
| `/ashfall mission` (everyone) | The mission checklist; `briefing` opens the full briefing, `book` gives a new copy of the book |

## Build

```sh
./gradlew build                        # 3 mod jars + unit tests + gametests on a real 26.3 server
python3 -I tools/build_mrpack.py       # dist/Ashfall-<version>.mrpack (checks every pin targets 26.3)
python3 -I tools/landmarks/build_landmarks.py   # regenerate the landmark Lost Cities assets
python3 -I tools/balance/sim.py        # offline balance sim over the shipped JSON (docs/balance-v1.md)
```

## Play

1. In Prism Launcher (or the Modrinth app): **Add Instance -> Import -> `Ashfall-<version>.mrpack`**. Give it
   **6-8 GB of RAM**, in singleplayer too (Prism: Edit Instance -> Settings -> Java -> Memory, Maximum memory
   allocation 6144-8192 MB). Prism's default is too small and causes lag.
2. Create a **new world** with the default world type. The pack selects the `ashfall` Lost Cities profile, and you
   start in the Montford safehouse.
3. Recommended before playing, singleplayer included: pregenerate the city with Chunky centred on Downtown
   (`/chunky center 0 0`, `/chunky radius 1600`, `/chunky start`), then run `/ashfall atlas processall`.
   Generating new Lost Cities chunks while you explore is the main source of lag.
4. Friends with the same pack join through **Open to LAN**, or over the internet with e4mc.

Details, dedicated servers and troubleshooting: [docs/install-and-server.md](docs/install-and-server.md).

Old Arnis worlds (`dist/Ashfall-Asheville-*-world.zip`, up to 0.5.0) still open, but their building data is no
longer read: loot and building populations need a new world.
