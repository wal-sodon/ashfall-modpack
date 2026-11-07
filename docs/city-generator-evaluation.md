# City generator evaluation: Arnis vs alternatives

Date: 2026-10-25. Status: proposal, needs user decisions (bottom of page).

## The question

> Is Arnis really what we should be using, or is there a better mod to more accurately generate player-accessible city maps?

The complaint: Arnis buildings are large, generic shells with interiors that are hard to navigate. Ashfall needs buildings worth entering: stairs instead of ladders, sensible rooms, doors, furniture, explorable floors, room for loot and zombie fights, and ideally a ruined look.

## Short answer

**No other generator on 26.3 does this out of the box.** The only real alternative is The Lost Cities. We ran it in a real 26.3 client. Its rooms are cleaner than Arnis's, but about half its building types still use ladder shafts, most rooms are bare, its loot is fantasy loot, and its ruin effect is weak. It also cannot produce Asheville.

The Arnis problem has a specific, fixable cause. In Arnis v3.3.0 every building gets **one ladder shaft in a corner** (`interior/mod.rs`, `pick_shaft`, line 307). Houses, apartments and `building=yes` use a **3-block storey cycle**, which leaves 2 blocks of headroom (`buildings.rs`, `floor_cycle_for`, line 2512). The room planner itself is decent: it splits zones, cuts doorways, adds corridors and furnishes by building use.

**Recommendation:** keep Arnis and fix the interiors in our Arnis patch (stairwells, taller storeys, wider halls). Then hand-build 20-40 detailed, pre-ruined landmark buildings for the places the game is built around. This takes about 5-7 weeks, keeps real Asheville, and every system that depends on the city keeps working.

## Options compared

| | A. Arnis + interior fix | A+C. A plus landmark templates (**recommended**) | B. The Lost Cities | C. Arnis + templates everywhere | D. Arnis core + Lost Cities outskirts |
|---|---|---|---|---|---|
| Real Asheville (districts, river, Beaucatcher ridge) | Yes | Yes | **No** (procedural, chunk grid) | Yes | Yes in the core |
| Stairs between floors | Yes, after the fix | Yes | About half the building types use ladders | Yes | Core: as A; outskirts: mixed |
| Sensible rooms and doors | Decent (the Arnis room planner) | Decent, hand-made at landmarks | Good (hand-laid 16x16 floors) | Hand-made | Mixed |
| Furnished | Arnis per-use furnishers | Rich at landmarks | Sparse: 1-28 furniture blocks per building, 0-2 chests | Rich | Mixed |
| Ruined look | Our damage pass (Arnis main also has a new post-apocalyptic mode) | Same, plus templates built pre-ruined | Weak: explosion craters and broken top floors; "wasteland" looks almost the same as default | Same as A | Mixed |
| Available on 26.3 | Yes (our pipeline) | Yes | Yes, 26.3-12.0.0-fabric | Yes | Unverified: generator settings and terrain seam |
| Licence | Ours (Arnis is open source; we already ship its output) | Ours | MIT (LCMT add-on also MIT) | Ours. No usable permissive third-party builds found | Mixed |
| Effort | 2.5-4 wk | **5-7 wk** | 7-11 wk (includes 12-15 custom buildings) | 9-14 wk | +3-5 wk on top |
| Code that carries over | About 100% | About 100% | Loot tables, LightStripper, RelayPlacer, Director design. The Arnis pipeline, BuildingIndex.parse and most of the damage pass are thrown away | About 90% | All, plus a second index source |
| Fixes the complaint? | Mostly (quality ceiling is "decent") | **Yes, where players spend time** | Only with our own building set | Yes | No (all districts stay Arnis) |

### The Lost Cities prototype (what we actually saw)

We ran Lost Cities 26.3-12.0.0-fabric in a client gametest with the `default` and `wasteland` profiles and took 76 screenshots. The best ones:

| Screenshot | What it shows |
|---|---|
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/02_default-04-bld-building4-f2-in1.png` | **Best case:** a real stairwell with iron-bar railings, stone-brick office floor |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/06_default-12-bld-building6-f2-in0.png` | **Typical case:** a ladder through the floor, one bookshelf, an otherwise empty room |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/03_default-02-bld-building7-f1-in0.png` | The full "furnishing" of a room: crafting table, furnace and chest against a wall |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/04_default-03-bld-shopping_open10-f2-in0.png` | Shop floor with glass display cases, otherwise an empty hall |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/11_default-16-ruin-building6-overview.png` | Ruin effect: an explosion crater at the base of a tower; the streets around it look intact |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/12_wasteland-16-ruin-building6-ground.png` | `wasteland` profile at street level: crater rubble only |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/09_default-14-cellar-building4-in1.png` | Basement stair with railings |
| `/private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/citygen/lostcities/10_default-15-subway-in0.png` | Subway platform (a nice feature Arnis lacks) |

All 76 shots are in `.../scratchpad/citygen/lostcities/all/`. Block counts per building are in `.../scratchpad/citygen/lostcities/lostcities-tour.txt`.

Key numbers from the jar:
- About 14 building families. 21 of 113 building parts use ladders, including every part of building1, 2, 3 and 5.
- 24 chests and 46 spawners across all building parts.
- No police station, hospital, pharmacy, fire station, school or suburban houses.
- In LCMT (Lost Cities Modern Tweaks), 37 of its 71 floor parts also use ladders.

Its strengths:
- Buildings are data files made of hand-authored 16x16x6 floor layouts, and there are in-game editing commands for them.
- A per-chunk API, post-generation events and data-driven chest loot.

So it is a good *authoring platform*, not a drop-in fix.

### Other sources checked (none usable)

- **Other structure mods on 26.3** (Towns and Towers, MVS, Structures, AdoraBuild, Epic Structures, CityMod): none have modern or apocalyptic city buildings.
- **Abandoned City Buildings:** stops at 1.21.11, ARR, 4 small houses.
- **McOSM:** dead since 2022.
- **DeceasedCraft:** shows the right method (a large hand-built building set on Lost Cities), but it is ARR and asks that its content not be copied.
- **Prebuilt maps** (Mosslorn, Zombiepolis), Planet Minecraft builds, BuildTheEarth: no redistribution licence. BTE also leaves private interiors empty.
- **Free schematic packs:** none carry a CC0 or other permissive licence.

### Impact on Ashfall systems

| System | A / A+C | B (Lost Cities) |
|---|---|---|
| Building-type loot (BuildingIndex sidecar, 25 profiles, ContainerPass) | Unchanged | Rewritten as an adapter over `getChunkInfo`. Stock buildings cover about 8 of 25 profiles; the rest need custom buildings |
| Director per-building populations | Unchanged (still only a spec, so no cost under any option) | Keyed by origin chunk; densities need retuning |
| Light stripping | Unchanged | Works, or becomes a palette override |
| Damage pass | Keep debris out of stairwells (one new sidecar field) | Mostly dropped (overlaps LC ruins) |
| Power Relays, safe zones | Unchanged | Work if fed by the adapter |
| "Relight Asheville", 4 districts + ridge | Unchanged (real geography) | Real Asheville is gone. Districts become 4 predefined cities; the lore loses its real-place hook |

## Recommended plan (A + small C)

1. **Arnis patch (Rust), about 2.5-4 wk.** Doing this in Rust rather than the Java post-processor matters: the Arnis Canvas knows the floor plan, and our Java pass does not.
   - Replace the ladder in `pick_shaft` with a stairwell: a straight or switchback run with a landing and a matching ceiling hole, with its cells reserved so no rooms are built there.
   - Raise the residential storey cycle from 3 to 4 blocks.
   - Enforce 2-wide corridors in `split_corridor` and guarantee doors between units with `connect()`.
   - Write the stairwell cells into the `buildings.json` sidecar. The damage pass reads them to keep debris out of stairwells.
   - Re-run the pipeline and the full-map audit.
2. **Landmark templates, about 2.5-3 wk.** Hand-build 20-40 detailed, pre-ruined structure NBTs for the district substations, police armory, Mission hospital plant, Kiln & Co warehouse, pharmacies and clinics.
   - The patch skips Arnis interiors for those footprints.
   - A Java pass places the templates and writes container, room and relay markers into the index.
   - Lost Cities and LCMT floor parts are MIT, so they may be reused as raw material, with credit and with ladders swapped for stairs.
3. **Optional later:** upgrade to the next Arnis release.
   - PR #1414 (post-apocalyptic mode) could take over part of our damage pass.
   - PR #1395 improves facades.
   - It does not fix interiors, so step 1 is still needed.

Open technical point: the sidecar's measured gaps between floors are mostly 5 blocks (2,138 buildings). The code reading says residential storeys use a 3-block cycle. Check which one is right before sizing the stair core: a 5-block gap fits a 3x6 stair core, a 3-block gap does not.

## Decisions for the user

1. **Keep real Asheville, or switch to a procedural city?**
   *Recommended: keep real Asheville (Arnis).* The districts, the ridge, the Grid Map and the lore all depend on it. Switching to Lost Cities costs 7-11 weeks and still needs our own buildings to get stairs and furniture.

2. **How far to go on interiors: fix only the generator (A, 2.5-4 wk), or also hand-build the landmarks (A+C, 5-7 wk)?**
   *Recommended: A+C.* Players spend most of their time in a few dozen key buildings, so that is where hand-made detail pays off. The other ~5,500 buildings get stairs, headroom and wider halls.

3. **Make houses taller (3-block to 4-block storeys)?**
   *Recommended: yes.* 2 blocks of headroom is cramped for melee and leaves no room for a stair. Homes get about 33% taller, which is acceptable at this map scale.

4. **Add Lost Cities as outskirts beyond the 3x3 km map (D)?**
   *Recommended: not now.* It does not improve the districts and adds a second building-index source. Revisit once the districts are done if players want more scavenging ground.
