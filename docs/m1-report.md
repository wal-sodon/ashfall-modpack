# M1 report: Salvage I (2026-10-25)

## Built
- **Arnis patch** (`map/arnis-patch/0001-ashfall-building-sidecar.patch`, 143 lines). Arnis writes `<world>/ashfall/buildings.json`: every building's OSM id, tags, footprint and floor heights, plus every furnished unit with the use Arnis chose (Home, Shop(Pharmacy), Food(Cafe) and so on), all in block coordinates. Results:
  - **1,245 of 1,246 chests** on the test tile fall inside a recorded unit, so projection matching is no longer needed.
  - The full map has 3,701 buildings.
- **Building index and 25 building profiles**, all data-driven: `data/ashfall/ashfall/building_profile/*.json`. Classification order:
  1. OSM tag rules first, e.g. police, fire station, pharmacy, hospital, government.
  2. Otherwise Arnis's furnishing use.
  3. Otherwise `generic`.
- **Atlas post-processor.** It runs once per chunk, either lazily on first load or in bulk with `/ashfall atlas processall`. In order:
  1. **Strips city lights:** glowstone, sea lanterns, lanterns, end rods, redstone lamps and shroomlight. The power is out.
  2. **Converts containers:** Arnis containers get the building profile's loot table, and more are added so each unit reaches its profile's count. Count scales with floor area; tier comes from building type.
  3. **Places per-player caches:** one Lootr cache chest per police station and hospital.
  4. **Applies light damage:** broken windows, boarded ground-floor windows, cracked and mossy walls, vines, street debris, barricades and abandoned cars.
  5. **Records candidates** for Power Relays and safe zones, for later milestones.
- **Loot zones and refill.**
  - `/lootzone create|list|info|edit|scan|refill|delete|tp`.
  - Shared containers refill and reroll only when three things are true: they've been opened before, their delay has passed since they were last opened, and a quiet period with no player nearby has happened since then.
- **Items**, all placeholders for now: Energy Cell, Scrap Electronics, Weapon Parts, Focusing Lens, Resonance Crystal, Bandage, Antibiotics, Antiviral. They have textures and live in an "Ashfall" creative tab.
- **Loot tables:** 25 building tables plus 2 per-player cache tables (police armory, hospital supply), all kept scarce. Energy Cells are rare in buildings and never appear in homes.
- **Mob drops:**
  - Zombies and husks drop Scrap Electronics 3% of the time and Bandages 2%.
  - Elite horde zombies and mutant zombies drop an Energy Cell 35% of the time.
  - All of these need a player kill.
- **Lootr config:** set to ignore our shared `ashfall:` tables.

## Verified
- `./gradlew build` passes all unit tests and the gametests on a real 26.3 server: salvage 5, arms 7, director 2.
- **Full map, processed in about 100 seconds:**
  - **Glowstone went from 201,785 to 5.** Leftover street lights sit in chunks with no buildings and are stripped when a player first loads them.
  - **10,121 Ashfall loot containers**, up from 4,244 Arnis containers, spread by profile. For example: home 7,445, office 404, hotel 389, restaurant 375, warehouse 219, hospital 42, police 12.
  - Both cache chests are real `lootr:chest` blocks.
- **Damage in two of the map's regions:** about 460 windows broken, 349 boarded, about 1,670 wall blocks weathered, 257 vines, about 440 pieces of debris, and barricades and cars.

## Not built yet (later milestones)
- Energy weapons, Cell Packs and chargers (Arms I). The items above are materials only.
- Bandages, Antibiotics and Antiviral do nothing yet. Infection treatment comes with Havens and Infection.
- The admin GUI for loot zones and mob drops; for now there are commands and datapack JSON only.
- Zombie spawning by building, darkness rules and the light-sight rules (Director I).
- Power Relays and safe zones are recorded as candidates only.

## Known gaps
- No gametest covers the damage pass yet. It was checked on the real map instead.
- Refill timing has unit and gametest coverage but hasn't been played in a real session.
- The special-zombie Energy Cell drop isn't tested in game.
