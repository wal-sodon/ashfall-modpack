# Ashfall player guide

Welcome to Asheville. The power went out district by district, the dead are in the streets, and the only
working tech left is what a salvage crew stripped off the thing that crashed on the ridge. Your long goal
is to **relight the city**: repair four substations, survive the sieges that follow, climb to the Wreck and
finish the **Black Start**, then decide what to do with the Heart at its centre.

This guide is for new players, solo or in a 2-4 player group. It starts with your first night and ends after
the ending. The numbers come from the shipped datapack files (Ashfall 1.0-rc). A server owner can change
any of them, so treat them as defaults. Setting up a world or server is covered in
[install-and-server.md](install-and-server.md).

**Before your first world:** give the game **6-8 GB of memory** (Prism: Edit Instance -> Settings -> Java ->
Memory, Maximum memory allocation 6144-8192 MB), and **pregenerate the city with Chunky** before you set out
(`/chunky center 0 0`, `/chunky radius 1600`, `/chunky start`, then `/ashfall atlas processall` when it
finishes). Generating new city chunks is what makes the game lag while you explore; see
[install-and-server.md section 6](install-and-server.md#6-pregenerating-the-city).

**Short version:**
0. Read the **Mission Briefing** you are handed when you first join. Lost at any point? Type **`/ashfall mission`**.
1. Grab the Field Radio and the **Survivor's Notes** upstairs in the safehouse.
2. Find water, boil it, and stay out of sight at night.
3. Build a Spark Blaster, Cell Packs and a Bunker Core.
4. Relight River Arts, South Slope, Downtown and Mission.
5. Kill the three bosses for their crystals, build the Grid Uplink, and survive the Black Start.
6. Sever or Bind the Heart.

---

## Contents

1. [Controls and screens](#1-controls-and-screens)
2. [The first night](#2-the-first-night)
3. [Water and thirst](#3-water-and-thirst)
4. [Light, noise and how zombies find you](#4-light-noise-and-how-zombies-find-you)
5. [Your first blasters, Cell Packs and chargers](#5-your-first-blasters-cell-packs-and-chargers)
6. [Havens: the Bunker Core, safe zones and respawning](#6-havens-the-bunker-core-safe-zones-and-respawning)
7. [Infection and treatment](#7-infection-and-treatment)
8. [Horde nights, dynamic hordes and Breaches](#8-horde-nights-dynamic-hordes-and-breaches)
9. [Special infected and how to fight each](#9-special-infected-and-how-to-fight-each)
10. [The district loop: relighting a substation](#10-the-district-loop-relighting-a-substation)
11. [Bosses and Resonance Crystals](#11-bosses-and-resonance-crystals)
12. [The Weaponsmith Bench and attachments](#12-the-weaponsmith-bench-and-attachments)
13. [Plasma Blades](#13-plasma-blades)
14. [The Black Start finale](#14-the-black-start-finale)
15. [Sever or Bind](#15-sever-or-bind)
16. [After the ending](#16-after-the-ending)
17. [Recipe reference](#17-recipe-reference)
18. [Where things are](#18-where-things-are)
19. [Survivor Skills](#19-survivor-skills)

---

## 1. Controls and screens

| Key or action | What it does |
|---|---|
| **Right-click** with a blaster | Fire. Semi-auto blasters fire once per click; the Pulse Carbine and Storm Repeater keep firing while held |
| **R** | Reload: swaps the loaded Cell Pack for the fullest matching pack in your inventory (about 1.2 s) |
| **G** | Grid Map: the city's zones coloured by district state, with the safehouse, the substations, the Wreck and the relays you have tuned into |
| **K** | Survivor Skills: your class and skill tree; spend XP on permanent buffs (see [section 19](#19-survivor-skills)) |
| **Sneak** with an Optic fitted | Aim down the scope: 4x zoom (7x on the Lancer Blaster Rifle), a steadier aim and slower mouse turning. Right-click still fires |
| **Scroll** while aiming a scope | Switch between the two zoom steps (2x / 4x, or 3.5x / 7x on the Lancer). The hotbar does not move while you aim |
| Right-click with the **Field Radio** | Grid report in chat, with your current objective and the way home |
| **Sneak** + right-click with the **Field Radio** | Just the objective and the way home (your Bunker Core, else the safehouse) |
| `/ashfall mission` | The mission checklist: what is done, the current step, where it is and how to do it. `[Read the full briefing]` opens the briefing; `/ashfall mission book` gives a new copy of the book |
| `/ashfall radio` | A replacement Field Radio, if you don't carry one. One every three in-game days (60 minutes of play; sleeping doesn't shorten it) |
| **Toggle Objective Line** (unbound) | Hides or shows the objective line. Bind it in Controls if you want it |
| Right-click a **Power Relay** while holding the Field Radio | Tune in to that relay's Relay Log |
| Advancements screen, **Relight** tab | Your quest log for the whole arc |

**HUD:**
- A row of **water droplets** sits above the hunger bar (your thirst).
- An **infection icon** appears next to it from stage 2.
- While you hold a blaster you see its **heat bar**, its pack charge (for example `42/60`) and its name.
- Once you have found the Field Radio, **"Grid NN%"** and one pip per district appear to the left of the hotbar.
- Above it, the **objective line** names your next step and, when it has a place, how far and which way
  (for example `Objective: River Arts: socket Grid Couplers 2/4 · 640 m W`).

All keys can be rebound in Controls, under "Ashfall: Arms" (Survivor Skills is under "Ashfall: Survival").

## 2. The first night

**Your briefing.** A few seconds after you first join a world you see "ASHFALL / Asheville has gone dark", a short
debrief in chat and a **Mission Briefing** book in your inventory (at your feet if it is full). The book covers the
situation, the mission, how a district is relit (with this server's numbers), getting stronger, staying alive and the
finale. It comes once per player per world; `/ashfall mission book` replaces a lost copy. The chat links open the full
briefing and the checklist.

**The objective chain** (what `/ashfall mission` and the objective line follow): find the Field Radio, arm yourself
(a Spark or Volt Blaster and a Cell Pack), reclaim River Arts, South Slope, Downtown
and Mission, collect 3 Resonance Crystals, craft the Grid Uplink, finish the Black Start at the Wreck, choose Sever or
Bind, then hold the relit city. Districts can be done in any order: the objective follows the district you are already
working on (with its sub-step, such as couplers 2/4, relays linked 1/3, cells or hold %), and otherwise suggests the next
one in that order. A Bunker Core is an optional checklist row: it never holds up the objective. Once every story boss
is down and crystals are still short, the objective sends you to crash sites. The radio and arms steps are your own;
district and finale steps are shared by the whole server, and endgame items count across everyone online; once made
they stay counted, even stored in a chest. Outside the overworld the objective line and the radio say "in the
overworld" instead of a distance, since every target is an overworld spot.

You start in the front hall of the **Montford safehouse**. The safehouse and the chunks around it are a
**safe zone**: no zombies spawn there, and zombies cannot build or break blocks inside it. It is the safest
place in the city, but it is only a few rooms.

**Do these first:**
1. **Go upstairs to the radio room** and open the cache chest. Each player gets their own copy (it is a Lootr
   chest). It holds:
   - the **Field Radio** (it has no recipe: lost it? `/ashfall radio` gives a new one, once every three in-game days);
   - the **Survivor's Notes**, a four-page primer you can reread any time;
   - 1-3 survival items (batteries, bottled water, bandages, canned food or scrap electronics).
2. **Use the radio.** You get the grid report, and the Grid HUD turns on. The report shows:
   - the Grid %;
   - each district's state;
   - the direction and rough distance to the nearest substation that is not yet reclaimed;
   - your current objective, with its direction and distance;
   - the way home: your Bunker Core if you have one, else the safehouse;
   - how many Relay Logs you have;
   - the nearest relay you have not tuned into.
3. **Check the pharmacy** two chunks east of the safehouse. It often has bandages, batteries and sometimes
   antibiotics. It is outside the safe zone.
4. **Get water before you need it.** See [section 3](#3-water-and-thirst).
5. **Be indoors and dark by nightfall.** The dark spawns more zombies, and zombies notice light and noise
   (see [section 4](#4-light-noise-and-how-zombies-find-you)).

Montford is gentle for the first 3 days: fewer building zombies and less darkness spawning. Use that time to
scavenge the houses nearby. Common loot is cloth, scrap metal, duct tape, scrap electronics, batteries,
canned food and water.

Only zombies and their variants spawn as hostile mobs in the overworld. There are no creepers, skeletons,
spiders or phantoms. Animals are unchanged, so you can hunt and farm. The Nether and the End are vanilla.

**Playing with friends:** a downed player can be revived. Down But Not Out gives you about 45 s to reach a
downed friend and right-click them.

## 3. Water and thirst

Thirst works like hunger and drains **1.5 times as fast** from the same activity (sprinting, jumping,
fighting). It drains faster still in hot biomes such as desert, badlands and savanna. The city itself is plains
(see section 18), so that only happens in the wild beyond it.

| Thirst | Effect |
|---|---|
| 6 or less (3 droplets) | No natural health regeneration |
| 0 | You can't sprint, and you take 1 damage every 4 s. On Easy it stops at 10 HP, on Normal at 1 HP, and on Hard it kills ("died of thirst") |

**Drinks:**

| Drink | Thirst | Saturation | Risk |
|---|---|---|---|
| **Bottled Water** (clean) | +8 | +4 | None. You get the glass bottle back |
| **Dirty Water** | +5 | +1 | 40% chance of Nausea for 10 s, and an **8% chance of infection** |
| Vanilla water bottle (cauldron, old stock) | +5 | +1 | Counts as dirty water |

**Clean vs dirty water:**
- A glass bottle used on any open water (a river, a pond or rain-filled water) gives **Dirty Water**.
- **Purify it** by cooking: put Dirty Water in a furnace, smoker or campfire and you get Bottled Water. One
  bottle per cook, with no other cost than fuel. This is the habit that keeps you alive.
- Bottled Water also turns up in houses, shops and caches.
- **The city pump.** Once **River Arts** is Reclaimed, bottles filled with open water **inside any reclaimed
  district** come up clean. That is one of the best rewards in the game.

Death resets your thirst to full.

## 4. Light, noise and how zombies find you

Zombies have senses. They react to **sound**, **light** and **blood**, and they investigate what they notice.

### Noise
Every one of these sends out a sound that nearby zombies walk towards:

| Source | Radius (blocks) |
|---|---|
| Opening or closing a door, gate or trapdoor | 10 |
| Breaking a block | 12 |
| Opening a container | 6 |
| Eating or drinking | 4 |
| Sprinting (sampled steps) | 8 |
| An arrow or thrown item landing | 8 |
| An explosion | 96 |
| A blaster shot | 24 (Spark) to 64 (Lancer, Storm) |
| A Power Relay charging (every 2 s) | 48 |
| A Plasma Blade hit | 6 |
| The Flamethrower (every half second while firing) | 40 |
| Forcing a Strongbox with a Crowbar | 48 |
| An Arc Charge going off (where it lands) | 32 |

A **Dampener** on a blaster cuts its noise to 40%.

### Light
- **At night**, if you stand in or hold light of level **7 or more** (a held torch is 14, a lantern 15),
  zombies within **4 blocks per light level** can notice you. A torch in your hand is seen from about 56 blocks.
- **By day**, only a bright room (level 10 or more, indoors) gives you away, and from less than a third as far.
- **Light only matters near players.** Lamps in an empty base attract nothing.
- **A zombie needs a line of sight** to the light to react. Light behind a solid wall does nothing.

**Blackout curtains.** Wool, carpets (including moss carpet), wool slabs and stairs, banners, closed doors and
closed trapdoors all block a zombie's view of light, even though some let light through. Hang wool or a
banner over the windows of a lit room and the street can't see in. Glass does not block it.

### Light burnout
- **Torches burn out.** A torch you place (standing or on a wall) burns for about **12 minutes** (give or take a
  minute) and then becomes a **burnt-out torch**: a charred stick that gives no light, so zombies see nothing either.
  The timer only runs while the area is loaded and carries on after a restart. Torches that came with the world
  (buildings, ruins) never burn out, and nor do soul torches, copper torches or the torch in your hand.
- **Relight** a burnt-out torch by using **Matches** (like flint and steel, 8 uses), **Lighter Fluid** (16 uses, and
  still furnace fuel) or flint and steel on it. Each relight costs one use and the torch burns for another 12
  minutes. Break a burnt-out torch and you get a stick back.
- **Campfires burn out** after about 5 minutes. You can add fuel to stretch that to 10 minutes, and rain puts
  them out in seconds.
- **Electric lights last.** Lanterns, soul lanterns, copper lanterns and redstone lamps are crafted with Scrap
  Electronics instead of a torch or glowstone (recipes in [section 17](#17-recipe-reference)). They are the
  permanent lights for a base.
- **Lighter Fluid** is furnace fuel worth 1.5 coal.

### Blood
At half health or below you leave a **blood trail** that zombies can follow, with a fresh drop every 2 s once
you have moved 3 blocks. Heal up before you lead them home.

### Where zombies come from
- **The dark.** The darker a spot, the more zombies spawn there, 24-64 blocks from you and out of sight. Outdoors
  in daylight spawning drops to 40%, but dark interiors stay dangerous all day.
- **Buildings.** Every Lost Cities building has its own population, which spawns the first time someone comes
  within 48 blocks. A building whose zombies are all dead stays cleared for 7 in-game days. The timer pauses
  while a player is near, and after it runs out the building gets half as many zombies again. Police stations,
  fire stations and government buildings have more armoured zombies. Hospitals and Mission are the worst.
- **No spawning** inside claims (your Bunker Core area, safe zones, reclaimed compounds), or within 16 blocks of
  your bed or respawn point.
- The number of zombies around each player grows from 30 on day 1 to 60 by day 120 (about 36 by the finale).
- Every "from day N" in this guide counts the world's day clock, the same day horde nights use. Sleeping
  through a night moves it forward, so it counts as a day survived.
- **Sleeping costs water and food.** Waking from a skipped night takes 3 droplets of thirst and 2 shanks of food,
  and beds refuse you below 7 thirst or 7 food (3.5 droplets or shanks). Lying down and getting up again costs nothing.
- **You can only sleep at home.** Beds, and Traveler's Backpack sleeping bags, work only inside your own (or your party's) Bunker Core claim,
  or inside a reclaimed substation compound. Anywhere else, the safehouse included, the bed refuses, so it can't set your
  respawn point either. Until you place a core or reclaim a district you can't sleep at all. A host can turn this off with `/gamerule ashfall:bunker_beds_only false`.

**Zombie types.** Walkers (20 HP, slow). Runners from day 7 (14 HP, fast). Armoured zombies from day 16 (police or
worker gear). Husks. Drowned, in water only. Patients, in hospitals and Mission: their bites infect far more
often. Then come the six special infected ([section 9](#9-special-infected-and-how-to-fight-each)).

### Zombies break and build
Zombies can break blocks to reach you, and can pillar up with blocks (Zombies Break & Build). Some blocks are
**fortified**: zombies can't break them outright, only wear them down, and only a boss breaks them at once:
- iron blocks, iron bars, iron doors and iron trapdoors;
- copper doors and trapdoors;
- obsidian and crying obsidian;
- Ashfall's **Reinforced Planks**, **Barricades** and **Reinforced Doors** (crafted from Alloy Plate and planks,
  opened by hand);
- the Bunker Core (which never wears).

Build your outer walls and doors from these, and keep them repaired. Each zombie hitting one adds about 1 wear
point a second, and a horde adds up fast. Cracks show the wear (Jade shows "Worn 40%"). Barricades hold 60 points,
Reinforced Planks and copper doors 120, Reinforced and iron doors and iron bars 180, iron blocks 240, obsidian 480.
A worn-out block gives way to a weaker one: Reinforced Planks become oak planks, doors and Barricades break
(copper doors drop 2 Scrap Metal; Reinforced and iron doors drop nothing), iron and obsidian become cobblestone.
Right-click a worn block with its repair material to take 45 wear off: Scrap Metal for metal and obsidian, a plank
and 2 Nails for wood. After each repair the material in your hand needs 1 second before it repairs again. A full
repair of a Reinforced Door takes 4 Scrap Metal, the same as building a new one. Wear stays on the block when you break
and re-place it (the item shows "Worn 40%" and doesn't stack with unworn ones) or push it with a piston, so
rebuilding is no free repair. Mechanic survivors repair worn blocks
in their Bunker Core's claim from the Supply Lockers, one set of materials (45 wear) every 30 seconds, so they slow a
wall's fall during a horde but don't hold it forever. The core warns you at dawn after a horde night if any
block in the claim is below half.

**Zombie damage heals.** Blocks zombies break grow back after about 1.5 in-game days, and blocks they place
vanish after 5 minutes (Zombie Break & Build's block restoration). Only game time counts, so sleeping does not speed it up.

## 5. Your first blasters, Cell Packs and chargers

Ashfall's guns are **energy blasters**. Each one holds one **Cell Pack**, spends energy per shot and heats up as
it fires. At 100 heat it **locks** (steam, a hiss and a loud noise) and only unlocks once it has cooled back to 0.
Cooling is slower while locked. A newly crafted blaster is empty, so load a pack with **R**.

**Polished Arms (optional look).** The pack ships with a resource pack, **Ashfall Polished Arms**, that repaints
every blaster, both Plasma Blades, the Flamethrower and the Arc Charge as dark machined gunmetal at twice the texture
detail: no rust or tape, knurled rubber grips, screws and sharp highlights. Each gun keeps its shape and glow colour.
It only changes how they look. Turn it on in **Options → Resource Packs** (move it to the right). It is off by default.

### The seven blasters

| Blaster | How to get it | Class / pack | Damage | Fire rate | Energy per shot | Heat per shot | Noise |
|---|---|---|---|---|---|---|---|
| **Spark Blaster** | Craft | Pistol, Small pack | 4 | 300/min | 1 | 12 | 24 |
| **Volt Blaster** | Craft | Pistol, Small pack | 6 | 240/min | 1.5 | 15 | 32 |
| **Pulse Blaster Carbine** | Craft (needs a Focusing Lens) | Rifle, full-auto, Standard pack | 7 | 300/min | 2 | 9 | 40 |
| **Ion Blaster Rifle** | Loot (police armory, crash sites, River Arts reward) | Rifle, Standard pack | 9 | 200/min | 3 | 14 | 48 |
| **Arc Blaster Magnum** | Loot (police armory, crash sites, Downtown reward) | Pistol, Small pack | 14 | 90/min | 3 | 34 | 56 |
| **Lancer Blaster Rifle** | Loot (police armory, crash sites, Wreck hull) | Rifle, Standard pack | 28 | 40/min | 6 | 50 | 64 |
| **Storm Blaster Repeater** | Loot (police armory, crash sites, Wreck hull) | Heavy, full-auto, Heavy pack | 6 | 400/min | 2 | 7 | 64 |

- **Headshots** do 1.75 times damage.
- Bolts fly up to 96 blocks with no drop.
- Bolts never break or set fire to blocks.
- Blasters only work from your **main hand**: in the off hand they don't fire, reload or scope.

**Your first build:** the **Spark Blaster** is the quietest and cheapest. Craft it and two **Small Cell Packs** as
soon as you have the scrap. Add the **Volt** when you find a Battery.

### Cell Packs

| Pack | Capacity | Fits | Shots per full pack |
|---|---|---|---|
| Small Cell Pack | 30 | Pistols | Spark 30, Volt 20, Arc 10 |
| Standard Cell Pack | 60 | Rifles | Pulse 30, Ion 20, Lancer 10 |
| Heavy Cell Pack | 120 | Heavy | Storm 60 |

Packs come out of the crafting grid empty. Carry spares: reloading takes the fullest matching pack in your
inventory and puts the old one back.

### Energy Cells and chargers
- **Energy Cell:** a battery that holds 240. You can craft it, and it also turns up as loot and as a drop from
  brutes, screamers, bloated and bosses. Chargers drain cells to charge packs. A drained cell can only be recharged at a
  Power Relay.
- **Field Charger:** craftable, with 4 charge slots and 1 cell slot. It charges slowly (0.25 energy per tick
  per slot, so a Small pack takes about 6 s) and runs on the Energy Cell you put in it. **It only works inside a
  claim:** your Bunker Core area, the Montford safehouse, or a reclaimed district.
- **Rapid Charger:** an upgraded Field Charger (a Field Charger plus Alloy Plate, Scrap Electronics and Wire
  Spools). Same slots, cell and claim rule, but three times as fast (a Small pack in about 2 s).
- **Power Relay:** found in the streets (about 75 across the city), and it cannot be crafted. It **powers
  itself**, charges 8 times as fast as a Field Charger, and works anywhere. The catch is that it **hums every 2 s
  while charging, a 48-block noise**, so every zombie nearby comes to see.
- Every charger also charges Plasma Blades, and a Power Relay recharges drained Energy Cells.
- **In a Reclaimed district,** Field Chargers and Power Relays charge for free, with no cell needed.

## 6. Havens: the Bunker Core, safe zones and respawning

### Bunker Core
Your home base. Recipe: 4 Alloy Plate, 4 Scrap Metal and an Energy Cell, so no iron is needed (see
[section 17](#17-recipe-reference)).
- **Placing it claims a 5×5-chunk square** (80×80 blocks) around it, in your name or your party's (Open
  Parties and Claims).
- Inside that square:
  - no zombies spawn;
  - zombies can't build;
  - Field Chargers work;
  - your party's containers are locked to the party.
- The claim doesn't shield survivors or pets: zombies that get inside can hurt them and infect survivors, so
  wall them in on horde nights.
- **Hordes still break plain blocks inside the claim.** Fortified blocks hold for a while, so wall with them and repair them.
- **Sealing yourself in doesn't stop a horde night.** A wave sent at your core that finds no way in stands at your
  wall for a few seconds and then **breaks through toward the core**, even if it never sees anyone. Once a zombie sees
  you or a survivor, it goes for them instead. Zombies can't place blocks inside the claim, so they come through your
  walls, not over them.
- **One core per party**, or per player if you are not in a party. Breaking the core releases the claim.
- The core itself is fortified, blast resistant and can't be pushed by pistons.
- Horde-night waves aim for your core if you are within 96 blocks of it (see [section 8](#8-horde-nights-dynamic-hordes-and-breaches)).
- **Finding your way back:** the Grid Map (**G**) marks your (or your party's) core as **Bunker**, and the Field Radio
  gives its direction and distance as "Home". Sneak and use the radio for just that line and the objective.
- **Right-click the core** with an empty hand to open the transit network (below).

### Fast travel: the transit network
Travel instantly between your stops, paid for with Cell Pack charge.
- **Stops:**
  - the safehouse, and any other safe zone;
  - your (or your party's) Bunker Core;
  - the substation of every reclaimed district.
- **To travel:**
  - Stand at a stop: inside the safe zone, inside your core's claim, or in a reclaimed compound.
  - Right-click your Bunker Core or a reclaimed Substation Core, or type `/ashfall travel` (needed at the safehouse).
  - Click a stop in the chat list, or open the Grid Map (**G**) and click the safehouse, your Bunker or a
    reclaimed substation.
- **Warm-up:** hold still for 3 seconds. Moving or being hurt cancels the trip, and nothing is charged.
- **Cost:** 10 charge, plus 5 for every 100 blocks, at most 40. For example, 300 blocks costs 25.
  - **Trips leaving your Bunker Core cost double**, because its Field Chargers refill packs for free.
  - It is taken from loose Cell Packs in your inventory, emptiest first.
  - Packs loaded in a blaster are not touched.
- **Not allowed:**
  - on horde nights;
  - while a District Siege is set or running;
  - while a zombie is hunting you.
- You arrive with 5 seconds of respawn protection.
- Server admins can tune the numbers in `data/ashfall/ashfall/travel/travel.json`.
- **Waypoints:** Xaero's Minimap is included. Press **B** to mark a waypoint, and it shows on the minimap and
  the world map (**M**).

### Safe zones
The Montford safehouse is a server safe zone. Inside it nothing spawns, and zombies can neither build nor
break. Anyone can open its doors, chests, barrels and furnaces, but nobody can break or place blocks there. The claim may also block right-clicking
items inside it (including the Field Radio); if it does, step outside, or use `/ashfall mission` for the same report. Each **reclaimed substation compound** (the core's chunk and the chunks around it) becomes a safe zone
too, and an **open** one: there every player can do everything they could in their own Bunker Core claim. You can
build, break, place and use items, sleep in beds, and put down Rain Collectors, while nothing spawns and zombies still
break nothing. Server admins can add more.

### Respawn choice
When you die, you respawn at once at the best place you have, in this order:
1. your Bunker Core;
2. your bed (which must be inside your Bunker Core claim or a reclaimed compound) or respawn anchor;
3. the safe zone nearest where you died;
4. the world spawn (the safehouse).

A clickable chat line offers the other options (`[Bunker] [Safe zone] [Spawn]`). You can **change your mind once
within 20 s**.

**Respawn protection:** for 10 s after respawning you have Resistance V and no zombie can target you. The
protection restarts after you pick another option, and **ends the moment you hit anything**.

**Dying while infected** is different: see [section 7](#7-infection-and-treatment).

### Bicycles
- **Never crafted.** A few lean against building walls on city streets; look along the pavements.
- **Ride it like a horse, no saddle:** use it to get on, steer with the mouse, W to pedal, S to back up slowly, sneak
  to get off. Faster than sprinting, silent, climbs single steps, can't jump, slow in water.
- **Sneak-use** (or hit it) to pick it up; use the item on a block to set it down.

### Supply drops
- Every 2-3 days, in the morning, chat names a spot near one of 12 set points (3 per district). A green boss bar counts
  down **10 minutes**; the Field Radio gives distance, bearing and time left.
- The crate lands once a player is within about 100 blocks: a loud crash, signal smoke, and **6-10 zombies** around it.
- Loot: a charged Cell Pack or Energy Cell, food, water, bandages, parts, Arc Charges, sometimes antibiotics, an
  attachment or a Grid Coupler. **Unclaimed at the deadline, it is lost.** `/ashfall supplydrop start|here|status|clear`
  runs them by hand (admins).

### Street loot
- About 1 in 8 city street chunks has a **dumpster** (a barrel: mostly junk and salvage) or a **street crate** (a
  chest: food, water, tools, sometimes a Cell Pack). Each is a one-time find.

### Quiet weapons
Guns are loud; melee makes no noise at all. These scrap weapons are for sneaking. Melee ones are repaired with Scrap
Metal in an anvil.
- **Pipe Wrench** (`S S / S / T`: Scrap Metal, Duct Tape): heavy hits (7 damage, 1.4 swings a second), so armour
  takes the least off each hit, though the faster Machete still kills a Brute a little sooner. Sometimes found in workshops.
- **Scrap Machete** (Scrap Metal, a Glass Shard, Cloth): fast swings, low durability.
- **Rebar Spear** (3 Scrap Metal, Duct Tape): longer reach, so you can hit through gaps and over barricades.
- **Nail Launcher** (Scrap Metal, Wire Spool, Scrap Electronics, Battery): fires one **Nail** from your inventory per shot
  (1 Scrap Metal makes 8 Nails; workshops have some). Weak, but zombies only hear it within **6 blocks**.
- **Bottle Lure** (glass bottle + Scrap Metal): thrown, it shatters loudly where it lands and pulls zombies within
  **32 blocks** of that spot away from you.
- **Arc Charge** (shapeless: Battery, Scrap Electronics, Duct Tape makes **2**; stacks to 8): a thrown stun grenade.
  It sticks to whatever it hits, crackles for 1 s, then discharges: every zombie or other hostile mob within
  **5 blocks** that the burst can see (walls block it) is **stunned** for 3 s (special infected 1.5 s, bosses half a
  second), the same hold as a blaster's stun shot, with the same 3 s lockout before a mob can be stunned again. No
  damage and no knockback, and it never stuns players or survivors, or pets unless friendly fire is on. It is **loud**: the bang is heard
  **32 blocks** away, so anything it doesn't hold comes running. Also found in police stations, supply drops and
  Strongboxes (tier 2 and up).

**Friendly fire.** Off by default: players can't hurt pets, and area weapons (the Flamethrower, Arc Charge) spare
players and pets. The host can turn on the **Friendly fire** game rule (Game Rules screen, or
`/gamerule ashfall:friendly_fire true`): then players and their weapons can hurt pets, the Flamethrower burns other
players and Arc Charges stun pets. Survivors stay protected either way. Direct hits between players still follow the
server's normal PvP setting.

### Rain Collector
- 6 Scrap Metal, 2 Duct Tape and a Glass Shard (`S S / SGS / TST`). **Only placeable inside your Bunker Core's area or a reclaimed substation compound.**
- Put empty glass bottles in (up to 4); while rain falls on it, one fills with clean Bottled Water every minute.
  Use it with an empty hand to take the water out.

### Bags

- **Satchel:** a small 9-slot bag you sew from 5 Cloth, 1 Duct Tape and 1 String. Right-click it to open it. Whatever is inside stays in the item, so it drops with it if you die. Bags can't go inside it.
- **Traveler's Backpacks** are the big bags, and they can't be crafted. Find them in gyms, schools, hotels, stations, warehouses, police and fire stations (about 2–3% a container), now and then in homes, in the police armory, and in Breach supply caches. Once you have one, its upgrades and themed versions craft from it as usual.

### Barricades
| Block | Recipe | Notes |
|---|---|---|
| **Reinforced Planks** ×4 | Iron nuggets and planks (any wood) in a checkerboard | Fortified wood |
| **Barricade** ×2 | Planks and scrap metal | Fortified |

### Armour sets
Three Ashfall sets; each piece's tooltip names its perk, and perks stack per piece.

| Set | Armour (head/chest/legs/feet) | Toughness | Perk | Where |
|---|---|---|---|---|
| **Scavenger** | 1/3/2/1 (7) | 0 | Running footsteps 15% quieter per piece | Craft from Cloth, Duct Tape and Scrap Metal; homes, shops, workshops, warehouses |
| **Hazmat** | 1/4/3/1 (9) | 0 | Every infection chance 15% lower per piece (bites, clouds, acid, Dirty Water) | Hospitals, clinics, pharmacies, fire stations, Patient Zero; worn by some patients |
| **Riot** | 2/7/5/2 (16) | 1 | 10% knockback resistance per piece | Police, the armory, government buildings, crash sites, bosses; worn by about 1 in 5 geared armoured zombies; brutes 5% |

## 7. Infection and treatment

A zombie's melee hit can infect you. The base chance is **6% per damaging hit**. It changes as follows:
- Patients infect at 20%, drowned at 8%, armoured zombies at 4%.
- Bloated and stalkers infect at 10%, brutes at 8%, crawlers, spitters, walkers, runners and husks at 6%, and
  screamers at 5%.
- Hits in **Mission** are 1.5 times as likely to infect.
- **Each point of armour removes 2% of the chance.** Full iron (15 points) cuts it by 30%.
- The chance never goes above 75%.

You can also catch it from:
- **Dirty Water**: 8% per drink;
- a **bloated's death cloud**: 10% per second while you stand in it;
- **spitter acid**: 5% per hit.

### The four stages
Time only counts while no treatment pause is running.

| Stage | Starts after | What you feel |
|---|---|---|
| 1 | 0 min | A message only |
| 2 | 5 min | Hunger and thirst drain faster. An icon appears on the HUD |
| 3 | 12 min | Slowness I and Weakness I all the time, plus bouts of nausea |
| 4 | 20 min | Slowness II, Weakness II, Mining Fatigue, more nausea |
| Death | 8 min after stage 4 | You die of the infection, through armour |

Milk does not cure it. The infection comes back while you are infected.

### Treatment

| Item | Effect | Where to find it |
|---|---|---|
| **Bandage** | Hold use like food (1.6 s). Heals 3 health (1.5 hearts) and **pauses** the infection for 60 s | Craft from 2 Cloth. Also houses, police and horde drops |
| **Antibiotics** | **Cures at stage 1.** At stages 2-4 it puts you back to the **start of the previous stage** and pauses it for at least 180 s | Hospitals, clinics, pharmacies, fire stations, the hospital supply cache, the Mission reward cache, Patient Zero and late horde waves. There is no recipe |

**Rule of thumb:** a bite you catch early (stage 1, the first 5 minutes) is cured by one dose of antibiotics. Later
it takes one dose per stage, so a stage 4 infection needs four doses, spaced by their pauses. Bandages buy time on
the way to the pharmacy.

**After the Sever ending,** antibiotics cure at any stage.

### Dying infected
Dying of the infection (or of anything else while infected) is an ordinary death: you respawn healthy and your
items drop where you died, as with any other death. Go back for them before the zombies and the despawn timer do.

## 8. Horde nights, dynamic hordes and Breaches

### Horde nights
- **No sleeping:** beds refuse sleep from sunset to dawn on a horde night, and while a District Siege is set for
  tonight or running.
- **When:** a horde night comes about every **6 nights**, starting on night 7. Each one can shift by up to 1 night,
  but the first is never early. The dates are fixed per world.
- **Warning:** a **siren** sounds 150 s before nightfall (in-game time 9000), with a yellow countdown bar. It
  repeats every 30 s, and a red bar stays up until dawn. The siren is only a sound for you, so it never draws
  zombies.
- **Waves:** from nightfall, waves arrive one after another. Each wave spawns as one group **40-64 blocks** from
  your Bunker Core if you are within 96 blocks of it, otherwise from you. It spawns out of sight and outside
  claims, then comes for your haven, or for you. A wave heading for your core breaks through any wall in its way
  (see [section 6](#6-havens-the-bunker-core-safe-zones-and-respawning)). If you have no core, or are more than 96
  blocks from it, the wave hunts you as before.
- **Tier = the higher of your day tier and your Grid tier.** Day tiers start on days 1, 14, 28, 45, 65 and 90
  (tiers I-VI). The Grid tier is 1 plus the number of reclaimed districts. **Relighting the city makes horde
  nights harder.**

| Tier | Waves (zombies each) |
|---|---|
| I | 6, then 8 |
| II | 8, 8, 10 |
| III | 10, 10, 12 |
| IV | 12, 12, 14 |
| V | 14, 14, 16, 12 |
| VI | 16, 16, 16, 16 |

- **New zombie classes** join waves as days pass: walkers and husks from day 1, runners from day 7, armoured
  zombies from day 16, patients from day 30. Horde and siege tiers rise on days 14, 28, 45, 65 and 90.
- **Special infected** can take a wave member's place once they have unlocked.
- Horde zombies carry extra loot: cloth, scrap, duct tape, bandages, food, and rarely antibiotics.

**Defending:** fortified walls and doors around a lit, curtained core room (a sealed base still gets broken into, so
layer the walls and repair between waves); a choke point; overheat discipline
(swap blasters rather than locking one). The Pulse Carbine and Storm Repeater are made for this.

### Dynamic hordes
Wandering zombies group up. Three or more within 8 blocks form a **horde** with a leader, and hordes that meet merge,
up to 40 zombies. The leader **hears and sees twice as far** as a single zombie, so one gunshot can turn a whole
street. Light still needs a line of sight, so curtains work against hordes too.

### Breaches
Once you have a reclaimed district, a **Breach** can hit one of its substations:
- It comes **every 7 days from day 7**, on a random reclaimed district. It never lands on a horde night, the night
  after one, or a District Siege night; it waits for the next free night.
- The siren names the district 150 s before nightfall.
- The Breach is a siege on that core, with the same waves and core health as a district siege of its tier. While it runs, the district loses its
  safe compound and free power.
- **If the core is still standing at dawn, the Breach is held**, whether or not you were there. Every held Breach
  leaves a **supply cache** at the core, with a separate roll for each player: an Energy Cell, salvage and parts,
  sometimes a charged Standard pack or a Resonance Crystal.
- **If the core is destroyed,** the district goes **BROKEN**: dark again, no free power, no safe compound.
  Socket **2 Grid Couplers** (half the original cost) into the core to restore it at once. You keep your links,
  your reward, and your Grid % once it is back.

## 9. Special infected and how to fight each

Special infected appear from their unlock day. They are rarer in Montford and more common in some districts. Each
kind has a cap per player, so you never face a crowd of one kind.

| Special | From day | Health | What it does | How to fight it |
|---|---|---|---|---|
| **Crawler** | 5 | 12 | Small and fast. It **fits under one-block gaps** and comes in under fences and half-walls | Don't trust 1-block gaps in your walls. Two Spark headshots kill it. Look down |
| **Bloated** | 8 | 26 | Slow and tanky. **When it dies it bursts** (a 16-block noise) into a 3.5-block cloud that lasts 12 s. The cloud poisons and gives nausea, and rolls a **10% infection chance every second** you stand in it | Kill it **at range**, never in a doorway you need. Don't walk through the cloud. Common in Mission and River Arts |
| **Spitter** | 10 | 18 | Spits acid **from 3 to 16 blocks** every 3.5 s: 3 damage, poison, and a 5% infection chance | Break line of sight, or **close inside 3 blocks**, where it can't spit. Armour helps. Prioritise it in sieges |
| **Screamer** | 12 | 14 | When it has a target it **shrieks**: a **48-block** noise, and **Speed II for 8 s** to every zombie within 16 blocks. 20 s cooldown | **Kill it first.** A dynamic horde's leader hears the shriek from twice as far. Use a Dampener, stay unseen, and take it from range |
| **Stalker** | 15 | 20 | **Invisible in the dark** (light 5 or less) while no player is within 5 blocks. It reveals itself with a **hiss**, a burst of speed and **+4 damage on its first hit** | **Carry light** and light your base. Listen for the hiss. Hurting it keeps it visible for 4 s. It hides less in groups, because someone is usually close |
| **Brute** | 20 | 60 | Big (1.6× scale), armour 8, hits for 8 with strong knockback, and breaks blocks **2.5 times as fast**. Fortified blocks still hold | Use **fortified walls**. Fight it at range with high-damage shots (Ion, Arc, Lancer). Don't fight it at a cliff edge. It drops an Energy Cell 50% of the time. Common in the wasteland and on the ridge |

Specials also turn up in horde nights, district sieges, Breaches and the finale's vents. Screamers drop an Energy
Cell 30% of the time and bloated 8%, so a siege pays back a few of its cells; the other specials sometimes
drop Scrap Electronics.

## 10. The district loop: relighting a substation

The four districts each have a dead **Substation Core** in a fortified control room. The Field Radio and the Grid
Map point to them.

| District | Zone tier | Substation | Reclaim extra | Nearby |
|---|---|---|---|---|
| **River Arts** | 2 | Riverside pump house, around (-864, 80) | **City pump:** clean water in reclaimed districts. Reward cache with an **Ion Blaster Rifle** | Kiln & Co. Salvage, the Abomination's lair |
| **South Slope** | 2 | Brewery-yard substation, around (96, 800) | **Heat Sink** and **Cell Regulator** fit at the Bench. Reward cache with a Heat Sink, plus a Dampener or Focusing Lens | - |
| **Downtown** | 3 | Parking-deck substation, around (64, -16) | **Overcharger** fits at the Bench. Reward cache with a Pulse Carbine or Arc Magnum | The police station and its armory cache |
| **Mission** | 4 | Hospital generator plant, around (784, 640) | **Patient Zero's deep ward unseals.** Reward cache with antibiotics, a Heavy Cell Pack and a rare blaster roll | The hospital, Patient Zero |

You can do them in any order. **River Arts first** is the usual choice, for clean water and an easier siege.
**Mission last**: it is tier 4 and infection pressure peaks there.

### Step 1: Repair (4 Grid Couplers)
- Craft **Grid Couplers**: 2 Wire Spools, Scrap Electronics, 1 Weapon Parts, a Battery and Scrap Metal. No crystal needed.
- **Right-click the core** with a coupler, once per coupler. Each one is used up. After 4 the core is **REPAIRED**.

### Step 2: Link 3 key relays
- Each district has **6 key Power Relays** around it. **Link any 3** by right-clicking them with a Grid Coupler.
  The coupler is not used up, because the district already paid for its couplers. Pick the 3 nearest the core.
- **Linking works as soon as the core is repaired.** Do it by day, before you load any cells: then the noisy hold
  and the siege come together on one night. (You can still link during the hold, so in co-op one player can run
  relays while the others hold the core.)
- The Field Radio names the nearest relay you haven't tuned into, and tuned relays show on the Grid Map. The key
  relays are roughly at these spots (block x, z):

<details><summary>Key relay locations (spoilers)</summary>

| District | Around |
|---|---|
| River Arts | (-1050, 0), (-950, 250), (-800, -50), (-700, 150), (-1000, 350), (-850, 300) |
| South Slope | (0, 700), (200, 700), (250, 900), (0, 950), (-100, 800), (150, 1000) |
| Downtown | (-200, -150), (150, -200), (250, 100), (-100, 250), (0, -300), (300, -50) |
| Mission | (650, 550), (850, 550), (900, 750), (650, 800), (750, 450), (550, 650) |

</details>

### Step 3: Energize (4 full Energy Cells and a 5-minute hold)
- **Right-click the core with fully charged Energy Cells** (240 each), 4 in all.
- The last cell starts a **5-minute hold**:
  - it only counts while a player is **within 32 blocks** of the core, and pauses otherwise;
  - the core **hums every 4 s**, a **144-block** sound that pulls zombies in from blocks around;
  - the Director sends hidden zombies at you: 2 every 15 s, up to 8 at once, from 24-40 blocks out;
  - a boss bar shows the progress.
- Hold the control room. Fortify the doors before you load the last cell.

Once the hold is done and 3 relays are linked, the district is **ENERGIZED**, and a **District Siege** is set
for the coming nightfall. The siren warns you 150 s ahead.

### Step 4: Survive the District Siege
- At nightfall waves of zombies attack the core. Siege routes bring them **in through the building's entrance**,
  and the control room's iron security doors are held open for the siege.
- **The core's health depends on the siege tier** (table below). Every zombie within about 6 blocks of it takes
  1 HP per second off it. Left alone, the core falls in about 2.5 minutes, so you can step away briefly, but not
  for long.
- A new wave comes every 120 s, or sooner once 4 or fewer are left alive.
- **Waves grow with the party:** the size below is for one player. With 2, 3 or 4 players within 96 blocks of the
  core when a wave spawns, it is ×1.6, ×2.3 or ×2.8.
- **Your first siege** (nothing reclaimed yet) counts the day tier as II at most, so a slow start isn't punished. The
  district's own tier still applies.
- **Siege tier = the higher of the day tier and the Grid tier.** The Grid tier is the district's own tier or 1 plus
  the districts already reclaimed, whichever is higher.

| Siege tier | Waves × zombies (solo) | Core HP |
|---|---|---|
| I | 4 × 8 | 1280 |
| II | 6 × 12 | 1920 |
| III | 7 × 20 | 3200 |
| IV | 6 × 22 (patients join) | 3520 |
| V | 6 × 24 | 3840 |
| VI | 7 × 26 | 4160 |

Special infected join siege waves too. Expect a party with the gear of its act to lose a quarter of the core or so;
brutes, screamers and bloated drop cells, which pays back part of the cost.

**Winning:** clear every wave, **or** reach dawn having had players within 96 blocks of the core for at least 4
minutes of the siege. The district is then **RECLAIMED**.

**Losing:** if the core reaches 0 HP, it drops back to REPAIRED. You **keep the couplers and the relay links**, but
you have to load 4 fresh cells and redo the hold. If nobody attended at all, the siege is postponed to the next
night, not lost. Failing costs time, not the district.

### What reclaiming gives
- **+20% Grid** (shared by the whole world), up to 80% with all four districts.
- **A safe compound:** the core's chunk and the chunks around it become a safe zone that works like a Bunker Core
  claim for everyone: build, break, sleep and set up Rain Collectors there.
- **80% less darkness spawning** in the whole district. The district still has zombies, just fewer.
- **Free charging:** Field Chargers and Power Relays in the district need no cells.
- **A reward cache** at the core, one per player.
- **The lights come back** as you explore: dead lamps become Grid Lamps. Lit streets draw zombies at night, so a lit
  district is never completely safe.
- **The district's extra** (table above).
- Relight advancements for everyone, and a banner.

Each reclaimed district also raises the tier of every later siege and horde night.

### Keeping the power on (district decay)
A reclaimed district has to be kept up. It needs **3 working relays** to stay lit.
- **Wear:** each linked relay loses 8% a day, starting 3 days after the reclaim. A relay left alone lasts about 12 days.
- **Horde nights:** after each one, one reclaimed district takes the hit: 1–2 of its relays lose 30%.
- **Flicker:** with fewer than 3 working relays the district flickers, and everyone is told. You have **2 days**.
- **Blackout:** the district goes dark. It loses its safe compound, free power, Grid % and spawn reduction. There is
  **no new siege**: repair enough relays and it comes straight back.
- **Repair:** use a **Wire Spool** on a linked relay with **Duct Tape** in your inventory (both are used up). The relay
  is back to 100%. Use a Grid Coupler on a relay to read its condition.
- **Spares:** after the reclaim you can link more of the district's 6 key relays. Each spare is one more relay that
  has to fail before the lights do.
- The **Field Radio** lists worn relays (30% or less), flickering districts and blackouts.
- Admins: `data/ashfall/ashfall/grid/decay.json` (about 4 wear a day is relaxed, 15 is daily upkeep, `"enabled": false`
  turns it off), and `/ashfall grid relays <district> <0-100>` for testing.

### Relay Logs (optional story)
Hold the Field Radio and use any Power Relay to tune in. You get "Relay Log #N" as a book, one of **30** fragments
that tell what happened to Asheville. Each player collects their own, and they survive death. Three more documents
are in the Kiln & Co. office, the hospital supply room and the Wreck hull. The logs you collect fill your epilogue
at the end.

## 11. Bosses and Resonance Crystals

**Resonance Crystals** power the endgame: the Plasma Blade and the Grid Uplink. The three story bosses each drop
**one guaranteed crystal**, plus a loot roll. Crash sites have a 5% chance of a crystal. Each boss fights once per
world, until the ending (see [section 16](#16-after-the-ending)).

| Boss | Where | Wakes when | Health and threat | Tips |
|---|---|---|---|---|
| **The Abomination** | The foundry of the **Kiln & Co. Salvage** warehouse in River Arts, around (-960, 112) | You come within 28 blocks | A Mutant Zombie with 300 HP, hard hits and big leaps | Keep moving and keep your distance. Mutant zombies can get back up after a "death" unless they die burning, so finish it with fire if you can. Flamethrower fire doesn't count: it has to be burning from real fire, so lead it through fire or lava or light it with Flint and Steel, and kill it before it goes out. Drops Weapon Parts, cells and electronics |
| **Patient Zero** | The **sealed deep ward** under the Mission hospital, around (704, 624) | You come within 20 blocks, but **only after Mission is reclaimed** (until then the ward's iron doors can't be broken, and zombies can't wear them down) | 260 HP, 9 damage, armour 6. Glows. **Summons 2 patients every 15 s** (up to 6) while it has a target | Patients infect at 20%, so bring antibiotics and armour. Kill the summons or focus the boss hard. Drops antibiotics and a Focusing Lens |
| **The Juggernaut** | At the **Wreck hull** on Beaucatcher ridge, around (2300, -100) | You come within 36 blocks | 360 HP, 14 damage, netherite and iron armour (never dropped), immune to knockback | High per-shot damage and headshots (Lancer, Arc). Don't trade blows. Drops a Focusing Lens and Weapon Parts |

The ridge (far east, past x 1700) is tier 5: brutes, crash sites and the Wreck. Go there well equipped.

**Crash sites** are wrecks scattered across the ridge and the wasteland (beyond 1.6 km from Downtown). Each has a
few guards. Their chests hold a 70% chance of a Focusing Lens, scrap, batteries and cells, a 5% Resonance Crystal and
a 6% rare blaster.

**Focusing Lenses** (you need several for the endgame) come from crash sites, electronics shops, museums, the police
armory, the South Slope reward, the Wreck hull cache, Patient Zero and the Juggernaut.

## 12. The Weaponsmith Bench and attachments

Craft a **Weaponsmith Bench** and use it like an anvil: blaster in one slot, attachment in the other, take the
result. It stores nothing, so breaking it never loses items, and if you close the screen the inputs come back. The
screen tells you why nothing is offered: not a blaster, already fitted, or locked.

A blaster has one attachment per slot. Fitting into a full slot swaps the old attachment out and gives it back.

| Attachment | Slot | Effect | Needs Reclaimed | Source |
|---|---|---|---|---|
| **Optic** | Optic | Sneak to aim: a 4x scope (scroll for 2x) and aim spread ×0.4 | - | Craft; police armory (25%) |
| **Dampener** | Muzzle | Noise ×0.4, sound volume ×0.45, damage ×0.9 | - | Craft; South Slope reward |
| **Heat Sink** | Cooling | Heat capacity ×1.35, cooling ×1.3 | South Slope | Craft; South Slope reward |
| **Cell Regulator** | Core | Energy per shot ×0.7 | South Slope | Craft |
| **Overcharger** | Core | Damage ×1.25, heat per shot ×1.2, energy per shot ×1.2 | Downtown (or the Bind ending) | Craft |

- **The Optic's scope** works in first person. The view eases in, the screen goes dark around a round sight with a
  cyan reticle, and the crosshair and your hands are hidden; the heat bar and charge stay visible. The Lancer
  Blaster Rifle's scope reaches 7x. The zoom ignores the FOV Effects setting, because aiming needs it.
- The **Cell Regulator and the Overcharger share the core slot**, so pick one.
- An attachment found early still waits for its district before the Bench will fit it.
- A Dampener can also be fitted without the Bench, by crafting the blaster and the Dampener together.

## 13. Plasma Blades

The **Plasma Blade** is a rare melee weapon for crowd control. It costs charge, hunger and crystal wear, so it
doesn't replace blasters on a horde night.

- **Recipe:** a Resonance Crystal on top; a Focusing Lens between two Scrap Electronics; Scrap Metal, a Battery and
  Duct Tape on the bottom row. A new blade has no charge.
- **Charge:** it holds 120 and uses 1 per swing that lands, however many targets it hits. Charge it at a Field
  Charger or a Power Relay like a Cell Pack.
- **Damage:** 9 to the first target. One sweep hits **at most 3 targets**: the 2nd and 3rd take 60% damage and each
  costs you extra hunger. A 4th target takes nothing.
- **Crystal wear:** the crystal lasts **600 hits** (1 per target hit). When it is used up the blade goes **dull**:
  a shatter sound and a message. The blade is never destroyed. **Re-socket** it by crafting the blade with a new
  Resonance Crystal. That keeps the charge, and it only works on a worn crystal.
- **Unpowered** (no charge or a dull crystal), it is a blunt hilt doing 2 damage.
- **A lit blade is a light source** (level 12, seen by zombies in the dark) and each hit makes a small noise.
  It is not a stealth weapon.
- **Against crystal pylons** (the finale's Heart) a lit blade mines at speed 12, about 10 s per pylon instead of
  about 2 minutes with a pickaxe.

Swings use Better Combat's movesets: two wide horizontal slashes and a stab.

## 14. The Black Start finale

With all four districts reclaimed (**Grid 80%**) the **Wreck hull's hatch unseals**, and everyone online is told.
Inside the hull is the **Uplink Socket**.

### Build the Grid Uplink (three crafts)

| Craft | Ingredients |
|---|---|
| **Uplink Frame** | 4 Focusing Lenses, 3 Resonance Crystals, 2 Grid Couplers |
| **Capacitor Bank** (make 2) | 8 Energy Cells around 1 Grid Coupler |
| **Grid Uplink** | The frame, 2 Capacitor Banks, 4 Grid Couplers |

In total that is 3 crystals, 4 lenses, 8 couplers and 16 Energy Cells.

### Prepare
- **Energy Cells**, as many as you can charge. Each one buys 45 s of Surge, so the Surge alone takes 9 beyond
  the Uplink's own charge, and your blasters burn about 5 more over the run. **Bring 16 or more** (about 14 is
  the bare minimum solo; 24 is comfortable). Parties spend more gun energy: about 17 cells for two, 20 for four.
- **Grid Couplers** to patch the Uplink (200 HP each; 3 bring it from 0 back to the 50% it needs to resume).
- Charged **Plasma Blades** for the pylons.
- Antibiotics, bandages, food, clean water and spare Cell Packs.
- Fortify the hull approach if you like. Waves come from 40-64 blocks out at the ridge edge.

**Roles** come from gear, not classes:

| Role | Job |
|---|---|
| **Engineer** | Feeds cells and patches the Uplink with couplers |
| **Marksman** | Lancer or Storm: thins waves before they reach the socket |
| **Blade** | Breaks the pylons and fights the Choir |
| **Runner** | Carries antibiotics and Cell Packs, revives the downed, and holds a Relay Breaker |

### Seat the Uplink
Right-click the socket with the Grid Uplink. The run lasts about **10-12 minutes**, longer if it pauses. While it runs, the Uplink is the
**loudest and brightest thing in the game**: a 192-block sound and a 128-block light every 4 s. It has **1200 HP**,
and every zombie within 6 blocks of the socket (on its level) takes 1 HP per second off it. That includes zombies
battering the **outside of the hull wall** next to it: waves that come from the north end up there, so don't hold
only the hatch. Left alone it lasts about a minute once zombies reach it. Right-click it with a Grid Coupler to patch 200 HP.

### Phase 1: Surge
- **Goal:** 450 s of **powered** time.
- The Uplink brings 60 s of charge. **Each full Energy Cell you feed it adds 45 s**, up to 600 s banked. If the
  buffer runs dry, progress stops ("buffer empty").
- Waves arrive every 30 s.
- **When it ends:** River Arts and South Slope light up below the ridge. Grid 87%.

### Phase 2: Feedback
- **Goal:** close both **Relay Breakers**. They rise 20 blocks either side of the socket, 40 blocks apart.
- **Each breaker needs a living player within 3 blocks of it for 75 s.** Both can run at once. An unattended
  breaker keeps its progress for a grace period (20 s solo, 15 s for two players, 10 s for three or more), then
  slips back.
- **Special infected pour out of two vents** inside the hull every 20 s.
- A feedback arc hits the Uplink for 60 HP (5%) every 30 s, so keep patching.
- Waves arrive every 45 s.
- **When it ends:** Downtown lights up. Grid 94%.

### Phase 3: The Heart
- **Goal:** break the **four Heart pylons** around the Heart. They are very hard (hardness 80). A lit Plasma Blade
  takes about 10 s each, a pickaxe or fist about 2 minutes.
- **The Choir** rises beside the Heart: a crystal-shard giant zombie with 420 HP, 12 damage, armour 10, immune to
  knockback and to sunlight. It has its own pink boss bar and stays near the Heart. Kill it or keep it busy while
  someone works the pylons. It shatters when the last pylon falls.
- Waves arrive every 60 s.
- **When it ends:** Mission lights up, Grid 100%, and everyone who took part gets **Asheville Relit**. Then the
  ending vote opens.

### Scaling and failing
- Waves scale with how many players are within 96 blocks of the socket: 60% solo (13 zombies a wave), 80% with
  two (18), 100% with three (22), and more beyond that (25 for four). At most 120 finale mobs are alive at once (60 solo).
- **Failing costs time, not the run.** The run **pauses** if the Uplink reaches 0 HP or if no living player stays
  within 96 blocks for 20 s. When paused:
  - you lose 60 s of Surge progress, or both breakers trip open again;
  - phases already won stay won, broken pylons stay broken, and the Uplink and its crystals stay seated;
  - nothing spawns and nothing takes damage.
- **To resume,** feed a cell or patch with a coupler, once the Uplink has at least 50% HP and the buffer holds
  charge. The boss bar says what is missing.

## 15. Sever or Bind

When the Heart falls, everyone who took part, plus their online party members, gets a **vote**:
- Clickable **[Sever] [Bind]** buttons appear in chat. Hover them to see the consequences.
- The vote lasts **30 s**. A strict majority ends it early.
- A tie or silence re-prompts once, then defaults to **Sever**.
- **Playing solo,** you click and then confirm.

| | **Sever** (the rebuild ending) | **Bind** (the veteran ending) |
|---|---|---|
| Infection | **Antibiotics cure at any stage** | Unchanged |
| Spawning | **Ambient spawning ×0.4** | Unchanged |
| Horde nights | **Three times as rare** ("Aftershocks") | **Grow every 7 days** from tier IV: +1 tier and +10% wave size per step, capped at **tier VI and ×1.5** |
| Power | **Every Power Relay becomes a free Grid Point**: no cells, anywhere | Unchanged |
| Gear | - | **The Resonant Plasma Blade** for each voter (12 damage, a 2400-hit crystal, light 14), and the **Overcharger** fits without Downtown |
| Havens | - | **Bunker Cores protect 2 more chunks** in every direction |
| A new boss | - | **The Ascendant** climbs out of the Wreck hull 10 days after the ending and every 10 days after each defeat (520 HP, summons armoured zombies, drops 2 crystals) |

Both endings give every voter:
- **"Asheville Relit — Field Report"**: days survived, districts lit, bosses killed, Relay Logs heard, players
  lost to the infection, deaths, and your own numbers.
- **"Asheville Relit — Epilogue"**: one page per Relay Log you collected, closed by the ending's own pages.

## 16. After the ending

The world carries on in the shape you chose:
- **Breaches** keep coming every 7 days on reclaimed substations.
- **Aftershock crash sites:** every 7 days a new wave of crash sites can appear on the ridge and in the wasteland
  (at half the usual chance), with crystals and rare blasters. That keeps blades re-socketable.
- **Bosses return** 10 days after the ending, and 10 days after each later defeat.
- **The Ascendant** (Bind only), as above.
- **Relay Logs** you missed stay collectible. `/ashfall ending epilogue` prints a fresh epilogue with whatever you
  have heard since.
- `/ashfall ending status` shows the world's ending, the day it happened and who took part.
- **Replay:** an operator can run `/ashfall story reset` to forget the ending and rearm the finale. Reclaimed
  districts, logs, rewards and boss records are kept. The spent Grid Uplink **stays in its socket**, powered down;
  use the socket with an empty hand to start a fresh Surge (the same Grid gate applies). Run the Black Start
  again and pick the other ending if you like.
- **Pure sandbox:** with the game rule `ashfall:objective` set to false there is no vote, no ending titles, no
  Aftershock news, no first-join briefing and no objective line. The radio, logs, grid and `/ashfall mission` still
  work.

---

## 17. Recipe reference

Patterns are shown row by row, with `_` for an empty slot. REI, the recipe viewer in the pack, shows every recipe
in game: search for the item and click it. Every Ashfall recipe is also in the vanilla **recipe book** (the green
book in the crafting and furnace screens); each one unlocks when you first pick up one of its key ingredients. The two
special recipes (socketing a crystal into a Plasma Blade, fitting a Dampener by crafting) have no fixed layout, so the
book can't show them. **Wooden ingredients take any wood**: "any planks" means oak, spruce, birch, bamboo, crimson or
any other planks, mixed if you like.

### Survival

| Result | Pattern | Key |
|---|---|---|
| Bandage | Shapeless: 2 Cloth | - |
| Cloth | Shapeless: 4 String | - |
| Cloth ×3 | Shapeless: 1 Wool (any colour) | - |
| Iron Nugget ×3 | Shapeless: 2 Scrap Metal | - |
| Glass | `SS / SS` | S Glass Shard. Broken windows drop 2–4 shards (panes 0–2); Silk Touch still drops the block |
| Bottled Water | Cook Dirty Water in a furnace, smoker or campfire | - |
| Satchel | `_S_ / CTC / CCC` | S String, C Cloth, T Duct Tape. 9 slots, right-click to open |
| Reinforced Planks ×4 | `NPN / PNP / NPN` | N iron nugget, P any planks |
| Barricade ×2 | `PSP / PSP` | P any planks, S Scrap Metal |
| Reinforced Door | `AA / PP / AA` | A Alloy Plate, P any planks |
| Lantern | `XXX / XEX / XXX` | X iron nugget, E Scrap Electronics |
| Soul Lantern | `XXX / XEX / XSX` | X iron nugget, E Scrap Electronics, S soul sand or soul soil |
| Copper Lantern | `XXX / XEX / XXX` | X copper nugget, E Scrap Electronics |
| Redstone Lamp | `_W_ / WEW / _G_` | W Wire Spool, E Scrap Electronics, G glass |

### Power and blasters

No mining needed: **Alloy Plate** (smelt 1 Scrap Metal) stands in for iron and **Wire Spool** (looted from homes, shops, offices, workshops and electronics stores, or craft 1 Scrap Electronics into 2) stands in for copper and redstone.

| Result | Pattern | Key |
|---|---|---|
| Alloy Plate | Smelt or blast 1 Scrap Metal | - |
| Wire Spool ×2 | Shapeless: 1 Scrap Electronics | - |
| Spark Blaster | `SSE / TI_ / S__` | S Scrap Metal, E Scrap Electronics, T Duct Tape, I Alloy Plate |
| Volt Blaster | `SIE / BIT / S__` | S Scrap Metal, I Alloy Plate, E Scrap Electronics, B Battery, T Duct Tape |
| Pulse Blaster Carbine | `LWW / IEI / T__` | L Focusing Lens, W Weapon Parts, I Alloy Plate, E Scrap Electronics, T Duct Tape |
| Small Cell Pack | `CCC / _E_` | C Wire Spool, E Scrap Electronics |
| Standard Cell Pack | `CCC / IEC / _C_` | C Wire Spool, E Scrap Electronics, I Alloy Plate |
| Heavy Cell Pack | `CCC / EIE / CCC` | C Wire Spool, E Scrap Electronics, I Alloy Plate |
| Energy Cell | `_C_ / CBC / _C_` | C Wire Spool, B Battery |
| Field Charger | `ICI / ECB / ICI` | I Alloy Plate, C Wire Spool, E Scrap Electronics, B Battery |
| Rapid Charger | `AEA / WFW / AEA` | A Alloy Plate, E Scrap Electronics, W Wire Spool, F Field Charger |
| Bunker Core | `ASA / SPS / ASA` | A Alloy Plate, S Scrap Metal, P Energy Cell |

The Ion, Arc, Lancer and Storm blasters can't be crafted: find them.

### Attachments and the Bench

| Result | Pattern | Key |
|---|---|---|
| Weaponsmith Bench | `MEM / TCT / P_P` | M Scrap Metal, E Scrap Electronics, T Duct Tape, C crafting table, P any planks |
| Optic | `III / GEG / _T_` | I Alloy Plate, G glass pane, E Scrap Electronics, T Duct Tape |
| Dampener | `ICI / CT_` | I Alloy Plate, C Cloth, T Duct Tape |
| Heat Sink | `CCC / MWM / CCC` | C Wire Spool, M Scrap Metal, W Weapon Parts |
| Cell Regulator | `CEC / BWB / CEC` | C Wire Spool, E Scrap Electronics, B Battery, W Weapon Parts |
| Overcharger | `WLW / CEC / WLW` | W Weapon Parts, L Focusing Lens, C Wire Spool, E Energy Cell |
| Fit a Dampener without the Bench | Shapeless: any blaster + Dampener | - |

### Grid and endgame

| Result | Pattern | Key |
|---|---|---|
| Grid Coupler | `CSC / WBM` | C Wire Spool, S Scrap Electronics, W Weapon Parts, B Battery, M Scrap Metal |
| Plasma Blade | `_C_ / ELE / MBT` | C Resonance Crystal, E Scrap Electronics, L Focusing Lens, M Scrap Metal, B Battery, T Duct Tape |
| Re-socket a Plasma Blade | Shapeless: worn Plasma Blade + Resonance Crystal | - |
| Uplink Frame | `LRL / CRC / LRL` | L Focusing Lens, R Resonance Crystal, C Grid Coupler |
| Capacitor Bank | `EEE / ECE / EEE` | E Energy Cell, C Grid Coupler |
| Grid Uplink | `CBC / _F_ / CBC` | C Grid Coupler, B Capacitor Bank, F Uplink Frame |

### Loot-only materials

| Item | Best places to look |
|---|---|
| Scrap Electronics | Almost everywhere: homes, offices, shops, workshops, electronics stores |
| Battery | Homes, shops, offices, workshops, warehouses, the radio cache, reward caches |
| Weapon Parts | Workshops, police, government buildings, the police armory, Kiln & Co., reward caches, bosses |
| Focusing Lens | Crash sites (70%), electronics shops, museums, the police armory, South Slope reward, Wreck hull cache, Patient Zero, the Juggernaut |
| Resonance Crystal | Bosses (guaranteed), crash sites (5%) |
| Antibiotics | Hospitals, clinics, pharmacies, fire stations, the hospital supply cache, Mission reward, Patient Zero |
| Duct Tape, Scrap Metal, Cloth, Canned Food, Matches, Lighter Fluid | Homes, shops and most buildings |

## 18. Where things are
City streets sit at two heights at most, one 6-block step apart (the Ashfall Lost Cities profile caps the city
levels), so the city is walkable. This needs a world made with rc11 or later; older worlds keep their terrain.

The whole city (the districts, Montford, the suburbs and the wasteland, out to 2,600 blocks from Downtown) is the
plains biome from just below the cellars up, so it never snows there, only rains, and Rain Collectors work
everywhere in town. Caves underneath keep their own biomes. Inside the four districts vanilla doesn't decorate the
land either: no trees, mangroves, boulders, lakes, ice, snow or dripstone grow through the streets. The Ridge and
the land past the wasteland stay wild. This needs a world made with Ashfall 1.4.0 or later (only newly generated
chunks of an older world get it).

Each district has its own look, inside and out (Ashfall 1.4.0 or later, new chunks only):

| District | Buildings | Walls and glass | Inside |
|---|---|---|---|
| **Downtown** | Tall offices, hotels, banks, big multi-block towers; fewer parks, more fountains and shopfronts | Concrete, grey and dark stone, tinted glass | Dark oak and spruce, desks and drafting tables, more wrecked rooms |
| **Mission** | Clinics, pharmacies, libraries, the odd hospital block | Pale brick, white concrete, clear glass | Birch and oak, apothecary stands |
| **River Arts** | Warehouses, workshops, factories, the art museum | Red brick, terracotta, tuff, mud brick | Spruce and dark oak, smithing benches |
| **South Slope** | Bars, restaurants, breweries, shops and malls | Painted terracotta and brick | Cherry, acacia and jungle wood, kitchens and music stands |
| **Montford and the suburbs** | Houses, corner shops, cabins, small parks | Brick and painted houses | Mixed homely woods, kitchens |
| **Wasteland** | Few, low and wrecked | Mossy brick and mud | Dark wood, mostly wrecked |

The building type also picks the loot, so a district's lean shows in what you find (see "Loot-only materials" above).


Coordinates are block x, z. The layout is the same in every world; ordinary buildings change with the seed.

| Place | Around | Notes |
|---|---|---|
| **Montford safehouse** (spawn) | (-160, -800) | Safe zone. Radio cache upstairs |
| Pharmacy | (-128, -800) | Two chunks east of the safehouse |
| **Downtown** substation | (64, -16) | Downtown is a 400-block disc around (0, 0) |
| Police station (armory cache) | (-64, 16) | Downtown |
| **River Arts** substation | (-864, 80) | Disc of 350 around (-900, 100) |
| Kiln & Co. Salvage (Abomination) | (-960, 112) | River Arts |
| **South Slope** substation | (96, 800) | Disc of 300 around (100, 800) |
| **Mission** substation | (784, 640) | Disc of 300 around (750, 650) |
| Hospital (Patient Zero, supply cache) | (704, 624) | Mission |
| **The Wreck hull** (Juggernaut, Uplink Socket) | (2300, -100) | On the ridge, x 1700-2600 |
| Suburbs | Out to 1600 from (0, 0) | Tier 1 |
| Wasteland | 1600-2600 from (0, 0) | Crash sites, brutes, more darkness spawning |

The Grid Map (**G**) shows the safehouse, the four substations, the Wreck hull, your Bunker Core and the current
objective's target (a blinking frame).

## 19. Survivor Skills

**Pick a class.** The first time you join a world, the class picker opens and stays open until you choose. Each
class has three **core skills** that train to **rank 5** and cost less; every other skill stops at **rank 2**. So
anyone can pick up a little of everything, but only a Medic is a great medic.

| Class | Core skills | Plays as |
|---|---|---|
| **Medic** | Field Medic, Immune System, Iron Gut | Patches people up and shrugs off infection and bad water |
| **Brawler** | Brawler, Fleet Foot, Hardy | Up close and fast |
| **Sniper** | Steady Aim, Headhunter, Light Step | Quiet and deadly at range |
| **Tank** | Hardy, Bulwark, Brawler | Soaks up hits for the group |
| **Scout** | Fleet Foot, Light Step, Pack Rat | Fast, quiet, finds loot |
| **Engineer** | Tinker, Pack Rat, Steady Aim | Keeps walls standing and blasters firing |

**Train skills.** Press **K** to open your skill tree: core skills are at the top in gold. **Train** buys the next
rank with XP.

- **Skills and class are kept when you die.** The XP you were carrying still drops as usual.
- **Ranks cost XP points, not levels**, so a rank costs the same at level 5 as at level 30 and there is no reason to
  save XP up first.
- A zombie drops 5 XP. Special infected drop more: Crawler 8, Spitter and Bloated 10, Stalker and Screamer 15,
  Brute 25. Mining, smelting and anything else that gives XP counts too.

| Rank | Core skill (XP points) | Other skill (XP points) |
|---|---|---|
| 1 | 40 (8 zombies) | 60 (12 zombies) |
| 2 | 100 (20) | 180 (36) |
| 3 | 180 (36) | - |
| 4 | 280 (56) | - |
| 5 | 400 (80) | - |

A core skill to rank 5 is 1000 XP (about 200 zombies); all three is 3000. Every other skill to rank 2 is 240 XP.

| Skill | Each rank |
|---|---|
| **Hardy** | +1 max heart |
| **Fleet Foot** | +3% movement speed (adds to weapon weight) |
| **Brawler** | +6% melee damage, Plasma Blades included |
| **Steady Aim** | +6% blaster damage |
| **Headhunter** | +10% blaster headshot damage |
| **Bulwark** | +1 armour |
| **Field Medic** | +20% healing from Bandages |
| **Iron Gut** | Thirst drains 12% slower; Dirty Water's nausea and infection chances 12% lower |
| **Immune System** | Every infection chance 10% lower: bites, clouds, acid and Dirty Water (stacks with Hazmat) |
| **Light Step** | The noise you make (footsteps, doors, breaking blocks) reaches 10% less far. Gunfire is unchanged |
| **Pack Rat** | 10% chance that a container of fresh loot rolls its loot a second time when you open it |
| **Tinker** | Fortify repairs restore 20% more |

**Changing class.** In the skill tree, **Change class** opens the picker again. A change only works inside your
Bunker Core's area and once every 7 in-game days (the first change after your first pick has no wait). It resets
every skill and gives back half the XP you spent on them.

`/ashfall skills` shows your class and ranks. Operators can use `/ashfall skills class <player> <class>`,
`/ashfall skills set <player> <skill> <rank>` and `/ashfall skills reset <player>` (which reopens the picker); none of
them refunds XP. The numbers live in `data/ashfall/ashfall/skills/skills.json`.
