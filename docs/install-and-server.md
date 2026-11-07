# Installing Ashfall and running a server

This page is for whoever sets up the game: a solo player, the host of a LAN or e4mc co-op game, or the
admin of a small dedicated server (2-4 players). How to play is in [player-guide.md](player-guide.md).

| | |
|---|---|
| Minecraft | **26.3** |
| Loader | Fabric Loader **0.20.1** |
| Java | **25** or newer (Prism and the Modrinth app pick it for you) |
| Pack file | `Ashfall-<version>.mrpack`: 47 Modrinth mods plus the 3 Ashfall jars |
| World | A **new** world. The city generates as players explore |

**Contents**
1. [Install the pack (Prism Launcher or the Modrinth app)](#1-install-the-pack)
2. [Memory](#2-memory)
3. [Singleplayer and the first world](#3-singleplayer-and-the-first-world)
4. [Co-op: Open to LAN and e4mc](#4-co-op-open-to-lan-and-e4mc)
5. [Dedicated server](#5-dedicated-server)
6. [Pregenerating the city (Chunky and the atlas)](#6-pregenerating-the-city)
7. [Admin command reference](#7-admin-command-reference)
8. [Game rules](#8-game-rules)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Install the pack

### Prism Launcher (recommended)
1. **Add Instance -> Import**, then pick `Ashfall-<version>.mrpack` (a file or a URL).
2. Prism downloads every mod from Modrinth, checks its hash, and copies in the Ashfall jars and the config
   overrides.
3. Set the memory: **Edit Instance -> Settings -> Java -> Memory**, tick the box and set **Maximum memory
   allocation to 6144-8192 MB** (see [section 2](#2-memory)). Prism's default is too small for Lost Cities and
   causes lag spikes.
4. Launch.
5. Before you start playing, pregenerate the city with Chunky ([section 6](#6-pregenerating-the-city)). It works
   in singleplayer too.

### Modrinth app
1. **+ (Create instance) -> From file**, then pick the `.mrpack`.
2. Set the memory in the instance's **Options -> Java and memory**.
3. Launch.

**Optional mods** (the launcher asks about these or lists them as optional):
- Sodium
- Sound Physics Remastered
- Xaero's Minimap and World Map
- e4mc

Everything else is required. Never add **Lost Cities Modern Tweaks** (the standard edition) next to the
"Lights Off" edition the pack ships.

## 2. Memory

| Role | Max memory (`-Xmx`) |
|---|---|
| Joining someone else's game | 4-6 GB |
| Singleplayer | **6-8 GB** |
| **Hosting** co-op (Open to LAN, e4mc) | **6-8 GB** |
| Dedicated server, 2-4 players | 6 GB (tested), 8 GB for headroom |

**In Prism:** Edit Instance -> Settings -> Java -> Memory, tick the box, and set Maximum memory allocation to
6144-8192 MB (minimum allocation can stay as it is). In singleplayer your computer runs the server and the game in
the same Java process, so it needs the server's memory as well as the game's. Prism's default heap is below that,
and with too little memory the game pauses more often to collect garbage, which adds to the "Can't keep up" lag.

Lost Cities cities are full of block states, which is why FerriteCore is in the pack. Ashfall's playtests ran a
dedicated server at `-Xmx6G` with 120 finale mobs at 20 TPS.

## 3. Singleplayer and the first world

1. **Create a new world** with the default world type. The pack's `config/lostcities-server.toml` already selects
   the `ashfall` Lost Cities profile (`selectedProfile = "ashfall"`), so there is nothing to pick.
2. Turn **Allow Commands** on if you want the admin commands (they need operator permission, level 2).
3. You spawn in the **Montford safehouse**. If you spawn in a plain world with no city, see
   [Troubleshooting](#9-troubleshooting).
4. **Recommended before you play:** pregenerate the city ([section 6](#6-pregenerating-the-city)). Chunky is in
   the pack and works in singleplayer. Generating Lost Cities chunks is the heaviest thing the game does. Without a
   pregeneration it happens while you walk, fly or drive into new streets, and the server falls behind ("Can't
   keep up!" in the log, blocks missing under your feet for a moment). Start the Chunky run, let it finish (stay in
   the world, standing still or AFK in the safehouse), then play.

**Torch burnout is built in.** Since 10.3.0 Ashfall's salvage mod burns torches out itself (the Realistic Torches
mod and the old `ashfall_core` datapack are gone): a torch a player places lasts about 12 minutes, and Matches,
Lighter Fluid or flint and steel relight it. Nothing needs copying into the world. The burn time is data
(`data/ashfall/ashfall/torch_burnout/default.json` in the salvage jar, `burn_ticks` and `spread_ticks`), so a
datapack can change it.

Old Arnis worlds (`Ashfall-Asheville-*-world.zip`, up to 0.5.0) still open, but loot and building populations need
a new world.

## 4. Co-op: Open to LAN and e4mc

Ashfall is built for 2-4 players. Every player needs **the same pack version**.

### Same network
1. The host loads the world and picks **Esc -> Open to LAN**. Turn **Allow Commands** on if the host wants the admin
   commands.
2. Friends on the same network find the game under **Multiplayer**, or join with the host's local IP and the port
   shown in chat.

### Over the internet with e4mc
e4mc is an optional client mod in the pack. With it installed on the host, **Open to LAN** also prints a public
address in chat (something like `xxxx.e4mc.link`). Friends paste that address into **Direct Connect**. You don't need
port forwarding, and only the host needs e4mc.

**Notes for hosts:**
- The host's game is the server. Give it 6-8 GB and don't let the host's computer sleep.
- The pack has no voice chat (removed in 10.3.0). Use whatever your group already talks on.
- **Parties:** Open Parties and Claims groups players into parties (`/openpac-parties`). A party shares one Bunker
  Core and one claim, and a party's online members join its ending vote.
- **Down But Not Out:** a downed player has about 45 s to be revived by a friend's right-click.

## 5. Dedicated server

### 5.1 Quick install with mrpack-install
[mrpack-install](https://github.com/nothub/mrpack-install) reads the `.mrpack` and sets up a Fabric server with only
the server-side mods (it skips mods marked client-only) and the overrides:

```sh
mrpack-install ./Ashfall-<version>.mrpack --server-dir ashfall-server
```

Run `mrpack-install --help` for the options in your version (the server jar name, a proxy and so on). Then continue
with [5.3](#53-configs).

### 5.2 Manual install
1. Install the **Fabric server for 26.3 with loader 0.20.1** (the Fabric installer, or the server launcher jar from
   fabricmc.net).
2. Put the **server-side mods** into `mods/`. These are all the mods marked `both` or `server` in `pack/mods.json`,
   plus the three Ashfall jars:

   | Group | Mods |
   |---|---|
   | Ashfall | `ashfall-salvage`, `ashfall-director`, `ashfall-arms` |
   | Libraries | Fabric API, Cloth Config, Forge Config API Port, Puzzles Lib, MonkeyLib538, Text Placeholder API, Player Animation Library, GeckoLib |
   | World | The Lost Cities, Lost Cities Modern Tweaks (Lights Off Edition) |
   | Zombies and survival | Zombie Break & Build, Contagion, Mutant Monsters, Unlit Campfire |
   | Claims and loot | Open Parties and Claims, Lootr, Loot Table Modifier |
   | Gameplay | Better Combat, Down But Not Out, Traveler's Backpack, Jade, Carry On, AppleSkin, Farmer's Delight Refabricated |
   | Performance and tools | FerriteCore, Lithium, ServerCore (server only), spark, Chunky |

   **Do not** put client-only mods on the server: Sodium, LambDynamicLights, Mod Menu, Sound Physics Remastered,
   REI, Architectury API, Better Advancements, Advancement Plaques, Iceberg, AmbientSounds, CreativeCore, Particle
   Rain, Xaero's Minimap and World Map, e4mc.

   From a checkout of the repository, `python3 -I tools/build_mrpack.py --version <v> --server-mods <dir>` downloads
   exactly this set at the pinned versions (each file checked by its sha512) and copies in the Ashfall jars.
3. Accept the EULA (`eula=true` in `eula.txt`) and start the server once to create its files, then stop it.

### 5.3 Configs
Copy everything from the pack's `overrides/config/` (in the repository: `pack/overrides/config/`) into the server's
`config/` folder, **before the world is created**:

| File | What Ashfall changes |
|---|---|
| `lostcities-server.toml` | `selectedProfile = "ashfall"` (the city and its districts), and `forceSaplingGrowth = false`, because sapling growth during pregeneration once froze the server thread long enough for the watchdog to kill it |
| `contagion.json5` | Contagion's own hit chance is 0 and its random symptoms are off, because Ashfall runs the infection timeline |
| `mutantmonsters-common.toml` | No natural Mutant Monsters spawns. The only Abomination is the boss |
| `zombies-break-build.toml` | Which mobs break and build |
| `unlitcampfire.toml` | Campfire burn times |
| `lootr-common.jsonc` | Only Ashfall's landmark caches become per-player Lootr chests. Building loot stays shared, and Ashfall refills it |
| `sound_physics_remastered/soundphysics.properties` | Client only. 16 reverb rays with 3 bounces instead of 32 with 4: every zombie step is traced, and in singleplayer that runs on the same CPU as the server |

FCAP reads `config/lostcities-server.toml`. A copy in `<world>/serverconfig/` overrides it for that world, if you
want different profiles per world. **The profile only affects chunks generated after it is set**, so set it before
the first start. The server log confirms it with "Building context from The Lost Cities (profile ashfall)".

### 5.4 server.properties

| Setting | Recommended | Why |
|---|---|---|
| `level-type` | `minecraft:normal` | Lost Cities needs no world preset |
| `level-seed` | Anything | The districts and landmarks are fixed; ordinary buildings change with the seed |
| `view-distance` / `simulation-distance` | 10 / 8-10 | Tested at 10 / 10 |
| `spawn-protection` | `0` | The spawn is the safehouse, which is already a safe zone |
| `pause-when-empty-seconds` | `60` (vanilla default) for normal play; **`-1` while pregenerating or running `processall`** | A paused server stops ticking, so a Chunky run or `/ashfall atlas processall` started from the console would stall. Paused, the day clock also stops, so horde nights and day tiers don't advance while nobody is online |
| `max-players` | 4-8 | The Director's caps are 120 zombies per player and 150 per world |

### 5.5 Start command

```sh
java -Xms6G -Xmx6G -jar <fabric server jar> nogui
```

If several people join at once, `-Xmx8G` gives headroom. Open TCP **25565**.

### 5.6 First boot checklist
1. Start the server and watch for "Building context from The Lost Cities (profile ashfall)" in the log.
2. Op yourself from the console: `op <name>`.
3. Pregenerate and run the atlas ([section 6](#6-pregenerating-the-city)) **before** inviting players.
4. Set `pause-when-empty-seconds` back to `60` and restart.
5. Join, check that you spawn in the safehouse, and run `/ashfall city zone` (it should say `montford`).

## 6. Pregenerating the city

The city generates as players explore. Pregenerating it once makes exploring smooth, and running Ashfall's first-load
pass ahead of time spreads its cost.

### Step 1: Chunky
**Centre on Downtown (0, 0), not on the spawn.** Chunky defaults to the world spawn, which is the safehouse at
(-160, -800).

```
/chunky world minecraft:overworld
/chunky center 0 0
/chunky radius 1600
/chunky start
```

- A radius of 1600 covers Downtown, the four districts and the suburbs, out to where the wasteland starts: about
  40,000 chunks.
- **Time:** the phase 1 test server (macOS, 6 GB) generated Lost Cities chunks at about 10 chunks per second, so a
  radius of 1600 takes **about 60-75 minutes**. A faster CPU is quicker.
- `/chunky progress` shows progress. `/chunky pause` and `/chunky continue` let you stop and resume, even across
  restarts.

**Optional, the ridge:** the Wreck hull is at (2300, -100), outside that radius. To pregenerate the ridge as well
(about 2,500 chunks, 5 minutes), run:

```
/chunky center 2300 -100
/chunky radius 400
/chunky start
```

### Step 2: the atlas pass
Every city chunk gets a one-time **atlas pass** once it and the eight chunks around it are loaded:
- it strips lights and logs them for the relight;
- it adds zone-aware damage;
- it places street Power Relays;
- it fills the landmark caches.

This happens lazily anyway, spread over the server ticks (at most about 3 ms of each tick). A chunk waits until its
neighbours are loaded because the pass reads across chunk borders. Earlier builds processed chunks straight away,
so those reads made the server load or generate the neighbour on the spot, which was the main cause of lag while
exploring. Running it in bulk
after Chunky means nobody waits for it while exploring:

```
/ashfall atlas processall
```

- The default radius is 1600 around Downtown, covering only the city chunks Lost Cities planned. You can pass a
  radius from 16 to 4000: `/ashfall atlas processall 1600`. `/ashfall city processall` is the same command.
- `/ashfall atlas status` shows progress ("processall done/total") and how many relay candidates and logged lights it
  has found so far.
- **Run it with no players online**, from the server console. It causes single ticks of up to about 3.6 s, which is
  fine for a one-off job and unpleasant for anyone playing. Keep `pause-when-empty-seconds=-1` while it runs.
- Time: in testing, 4,089 chunks took about 4 minutes. Expect roughly 30-40 minutes for the full radius.

In singleplayer you can run both steps from chat while standing in the safehouse.

## 7. Admin command reference

All commands need **operator permission level 2** (`/op`, or Allow Commands in singleplayer and LAN), except where
**Everyone** is marked. Arguments in `<>` are required and arguments in `[]` are optional.

### City and atlas (salvage)

| Command | What it does |
|---|---|
| `/ashfall city zone` | The zone of the chunk you stand in |
| `/ashfall city info` | The Lost Cities plan of your chunk (building, floors, profile) |
| `/ashfall city processall [radius]` | Same as `/ashfall atlas processall` |
| `/ashfall atlas status` | Atlas version, building source, chunks processed this session, queue size, relay and safe candidates, lights logged, and processall progress |
| `/ashfall atlas process <radius>` | Queue the loaded chunks within `radius` chunks (0-32) of you for the atlas pass |
| `/ashfall atlas processall [radius]` | Process every city chunk within `radius` blocks (16-4000, default 1600) of Downtown, loading them in batches of 16. Restarting it cancels a running job |
| `/ashfall atlas audit` | Count the loaded chunks, unprocessed chunks, remaining light blocks and containers per building profile |
| `/ashfall site list [radius]` | Fixed and planned crash sites near you (radius 16 and up) |
| `/ashfall site place <key>` | Place a fixed or planned site now, for example `fixed:ashfall:wreck_hull`. Sites normally place themselves when a player comes near |

### Loot zones (salvage)

| Command | What it does |
|---|---|
| `/lootzone create <id> <radius> <container_type> <table> [regen <bool>] [delay <ticks>]` | Create a zone centred on you. `radius` is 1-512. `container_type` is a block id (for example `minecraft:chest`) or `any`. `table` is a loot table id |
| `/lootzone list` | All zones |
| `/lootzone info <id>` | A zone's centre, radius, container type, table and refill settings |
| `/lootzone edit <id> table <table>` | Change the loot table |
| `/lootzone edit <id> radius <1-512>` | Change the radius (shrinking releases containers outside it) |
| `/lootzone edit <id> regen <bool>` | Turn refilling on or off |
| `/lootzone edit <id> delay <ticks>` | Refill delay |
| `/lootzone edit <id> type <block id or any>` | Container type |
| `/lootzone edit <id> tag <block tag>` | Container type as a block tag, for example `c:chests` |
| `/lootzone edit <id> quiet <ticks>` | Quiet period before a refill |
| `/lootzone scan <id>` | Claim the loaded containers inside the zone |
| `/lootzone refill <id>` | Refill the zone's containers now |
| `/lootzone delete <id>` | Delete the zone. Its containers go back to the building rules |
| `/lootzone tp <id>` | Teleport to the zone |
| `/lootzone gui` | Open the Loot Zone screen. It needs Ashfall on the client. There is also an unbound **Loot Zones (op)** key |
| `/lootzone export` | Write every zone to `<world>/ashfall/loot_zones.json`. An existing export is renamed with a timestamp first |
| `/lootzone import` | Read that file and add or replace zones by id |

### Mob drops (arms)
Changes are saved into the world datapack `world/datapacks/ashfall_overrides` and reloaded. `/datapack disable
"file/ashfall_overrides"` turns every edit off at once. Nothing is ever deleted.

| Command | What it does |
|---|---|
| `/mobdrops gui` | Open the Mob Drops screen (needs Ashfall on the client). There is also an unbound **Mob Drops (op)** key |
| `/mobdrops list` | Every entity's extra drops |
| `/mobdrops add <entity> <item> <chance> [<min> <max> [<any_kill>]]` | Append a drop. `chance` is 0-1. `any_kill true` drops even without a player kill |
| `/mobdrops remove <entity> <index>` | Remove drop number `index` (1-based, as `list` shows) |
| `/mobdrops clear <entity>` | Save an empty rule, which turns off Ashfall's extra drops for that entity |
| `/mobdrops reset <entity>` | Move the override aside (`*.disabled`) and go back to the shipped drops |

### Director: zombies, hordes, infection (director)

| Command | What it does |
|---|---|
| `/ashfall director status` | Spawn budget, counts by source, cleared buildings and the ending's spawn multiplier |
| `/ashfall director clear-building <building_id>\|here` | Mark a building as cleared |
| `/ashfall director repopulate <building_id>\|here` | Give a building a fresh population |
| `/ashfall debug stimuli [seconds]` | Show the noise, light and blood stimuli around you as particles |
| `/ashfall horde status` | Today's day, the next horde night and its tier, the Grid tier, unlocked classes and the next Breach day |
| `/ashfall horde wave` | Send tonight's first horde wave at yourself, now |
| `/ashfall horde wave at <pos>` | Send a wave at a place. Works from the console |
| `/ashfall horde breach [substation]` | Start a Breach on a reclaimed district (`river_arts`, `south_slope`, `downtown`, `mission`), or a random one |
| `/ashfall infection get <player>` | A player's infection stage and time |
| `/ashfall infection set <player> <stage>` | Infect a player at stage 1-4 |
| `/ashfall infection clear <player>` | Cure a player |
| `/thirst get <player>` | A player's thirst |
| `/thirst set <player> <value>` | Set thirst, 0-20 |

### Havens (director)

| Command | What it does |
|---|---|
| `/ashfall safezone list` | Active safe zones |
| `/ashfall safezone add <name> [radius]` | A safe zone centred on your chunk, with radius 0-16 chunks (default 1). Your position becomes its respawn point |
| `/ashfall safezone remove <id>` | Remove a zone. A datapack zone (like the safehouse) is only disabled for this world; its file is never touched |
| `/ashfall respawn bunker\|safezone\|spawn` | **Everyone.** Change your respawn point once, within 20 s of respawning. These are the clickable chat buttons |

### Bosses (director)

| Command | What it does |
|---|---|
| `/ashfall boss list` | Each lair's state and kills |
| `/ashfall boss spawn <lair> [here]` | Spawn a lair's boss at its marker, or at you with `here`. Lairs are `ashfall:abomination`, `ashfall:patient_zero`, `ashfall:juggernaut` and `ashfall:ascendant` |
| `/ashfall boss unseal <lair>` | Open a sealed lair (Patient Zero's ward opens by itself when Mission is reclaimed) |

### City Grid (arms)

| Command | What it does |
|---|---|
| `/ashfall grid status` | Every district's state and progress, the Grid %, and core positions once their chunks have loaded |
| `/ashfall grid set <district> <state>` | Jump a district to `dark`, `repaired`, `energized`, `siege`, `reclaimed` or `broken`, as if it happened in play |
| `/ashfall grid damage <district> <amount>` | Damage a besieged core through the siege's own loss path |
| `/ashfall grid reset` | Every district back to DARK (core positions are kept) |

### Black Start and the ending (arms and director)

| Command | What it does |
|---|---|
| `/ashfall finale status` | The run: phase, buffer, Uplink HP, breakers, pylons, or why it is paused, plus whether the hull is open |
| `/ashfall finale start [<pos>]` | Seat an Uplink in the hull's socket (or at `pos`) without the item and without the Grid gate |
| `/ashfall finale phase surge\|feedback\|heart` | Jump to a phase. Earlier phases count as won: their Grid bonus applies and the bands they would have lit relight in loaded chunks |
| `/ashfall finale abort` | Remove the breakers, pylons and the Choir, and reset the run to idle |
| `/ashfall finale open` | Open the hull hatch now, or when its chunk next loads |
| `/ashfall finale feed [<cells>]` | Feed 1-20 Energy Cells into the Uplink's buffer, as a player would at the socket (no items used). A paused run resumes once it may |
| `/ashfall finale patch [<couplers>]` | Patch 1-20 Grid Couplers into the Uplink (200 HP each), as a player would. The console's way to bring back a knocked-out Uplink |
| `/ashfall finale unattended on\|off\|<players>` | Count 1 (or 1-8) stand-in players at the Uplink and both breakers, so a server with nobody online can run the finale. Kept in memory only |
| `/ashfall ending vote sever\|bind` | **Everyone.** What the vote buttons run |
| `/ashfall ending confirm` | **Everyone.** Confirm a solo vote |
| `/ashfall ending status` | **Everyone.** The world's ending, its day, days since, participants and the objective rule |
| `/ashfall ending epilogue` | **Everyone, after the ending.** Print a fresh epilogue book |
| `/ashfall ending begin` | Start the ending vote for everyone online |
| `/ashfall ending set sever\|bind` | Record an ending outright. During a vote it decides the vote for its voters (the console's way to close one) |
| `/ashfall story sever` | Flip the Sever flag by hand (Antibiotics cure at any stage) |
| `/ashfall story reset` | The replay reset: forget the ending, its effects and the Black Start bonus, and rearm the finale. Reclaimed districts, logs, rewards, boss records and death counters are kept. The spent Grid Uplink stays in its socket, powered down: use the socket with an empty hand to start the replay |

**Admin tip:** the Choir carries the `ashfall_finale` tag like the finale's waves, so clear waves with
`/kill @e[tag=ashfall_finale,type=!ashfall:choir]`.

## 8. Game rules

| Rule | Default | What it does |
|---|---|---|
| `ashfall:thirst` | `true` | Thirst on or off. Off keeps everyone topped up |
| `ashfall:director_spawning` | `true` | All Director spawning. Off stops it; the zombies-only filter stays |
| `ashfall:director_ambient_cap_start` | `30` | Ambient zombies per player on day 1 |
| `ashfall:director_ambient_cap_max` | `60` | Ambient zombies per player from day 120 |
| `ashfall:objective` | `true` | The story's objective layer. Off: no ending vote, no ending titles and no Aftershock news. The radio, logs and grid still work |

Ashfall also sets `spawn_patrols`, `spawn_phantoms` and `spawn_wandering_traders` to false once, on the first start
of a world. You can change them back afterwards.

Most balance numbers live in datapack JSON under `data/ashfall/ashfall/` (in the mod jars). A world datapack with the
same paths overrides them, and `/reload` applies the change. The specs in [specs/](specs/) list every file.

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| **No city: a plain world, or not in the safehouse** | The `ashfall` profile was not selected when the world was created. Check `config/lostcities-server.toml` (`selectedProfile = "ashfall"`) and the log line "Building context from The Lost Cities (profile ashfall)". Chunks already generated keep their terrain, so start a new world |
| **The server froze or the watchdog killed it during pregeneration** | Make sure `forceSaplingGrowth = false` in `lostcities-server.toml`. FCAP may rewrite the file's comments, but keeps the value |
| **Chunky or processall stops when you log out** | `pause-when-empty-seconds` paused the server. Set it to `-1` while the job runs |
| **Out of memory, or long garbage-collection pauses** | Raise `-Xmx` (see [section 2](#2-memory)) and keep FerriteCore installed |
| **Lag ("Can't keep up!" in the log) while exploring, flying or near many zombies** | Most of it is chunk generation, not the zombies. Give the game 6-8 GB ([section 2](#2-memory)), pregenerate with Chunky and run `/ashfall atlas processall` ([section 6](#6-pregenerating-the-city)), and don't fly through ungenerated land in creative. In singleplayer a lower simulation distance (8-10) also helps. The pack's lighter Sound Physics settings only reach new installs; on an existing instance set the reverb ray count to 16 in that mod's config screen |
| **Safehouse doors, chests or beds say the area is protected** | Older builds claimed safe zones with OPAC's locked server claim settings. Safe zones now use their own, so everyone can open doors and containers and sleep there. Breaking and placing blocks inside stay protected. Existing worlds are moved over on the next start |
| **A friend can't join** | Everyone needs the same pack version. A "mismatched mod" message names the culprit |
| **The server crashes on start mentioning a rendering or client class** | A client-only mod is in the server's `mods/`. Remove it (see the list in [5.2](#52-manual-install)) |
| **The Loot Zone or Mob Drops screen won't open** | They need operator level 2, and Ashfall on the client. Use the `/lootzone` and `/mobdrops` commands instead |
| **A Field Charger doesn't charge** | It only works inside a claim: a Bunker Core area, the safehouse, or a reclaimed district. A Power Relay works anywhere |
| **Placing a Bunker Core fails** | Each party, or each player outside a party, can have one core. Break the old one first |
| **A Bunker Core's chunks don't show as claimed in OPAC** | OPAC can refuse chunks (a claim limit, or someone else's claim). Ashfall's own haven region still protects the full square |
| **A radio cache has no Survivor's Notes** | A cache someone opened before 1.0-rc kept its old roll. Players who haven't opened it yet get the notes. The primer is also summarised in [player-guide.md](player-guide.md) |
| **A torch never burns out** | Only torches placed by a player after 10.3.0 burn out. Torches that came with the world, were placed with `/setblock`, or were placed before the update keep burning; break and place one again to start its timer |
| **The finale keeps pausing ("abandoned")** | A living player has to stay within 96 blocks of the socket. On an empty server, use `/ashfall finale unattended on` |
| **The Uplink is down and nobody can patch it** | A downed Uplink needs Grid Couplers (to 600 HP) and some buffer. An op can use `/ashfall finale patch 3` and `/ashfall finale feed` |
| **Nobody got the Field Report** | If every voter was offline when the Heart fell, the vote defaults to Sever with no voters. `/ashfall ending epilogue` reprints the epilogue; the Field Report can't be reprinted |
| **"POI data mismatch" lines in the log** | Harmless: Lost Cities writes some blocks without registering them, and vanilla fixes it on the next load |
| **A Mac host stops ticking** | macOS sleeps even with `caffeinate -i` when the screen locks. Use `caffeinate -s` or `-d`, or keep the screen awake |

For performance, `/spark tps` shows the server's tick rate.
