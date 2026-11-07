# Versions

Ashfall follows **semantic versioning**, MAJOR.MINOR.PATCH, set as `version=` in `gradle.properties`:

- **Major** (first number): breaks existing worlds or saves, such as removed items, blocks or mods, or an overhaul
  of a core system like the city generator or the finale.
- **Minor** (second number): new features or content, a notable rebalance, or anything that changes world
  generation in a way an existing world can live with (new chunks generate differently).
- **Patch** (last number): bug fixes, small tweaks and balance-number changes.

Raising a number resets the ones after it: after 1.2.3, a minor update is 1.3.0 and a major one is 2.0.0. Each
release is tagged `vX.Y.Z` on `wal-sodon/ashfall-modpack` and gets a GitHub release with the `.mrpack` attached.

## Releases

| Version | Class | What changed |
|---|---|---|
| 1.0.0 | first | Numbering restarts with the move to `wal-sodon/ashfall-modpack`. Same gameplay as 10.3.1 below |

## Before 1.0.0

Until 2026-11-07 the pack used a MAJOR.MEDIUM.MINOR scheme and shipped up to 10.3.1 from
`wal-sodon/ashfall-modpack-release`. Those numbers are kept below. The "New" column is the old scheme's number, so
the old 1.0.0 to 9.x rows are not the same releases as the 1.0.0 above.

### Old labels

Releases were first labelled 0.x and then 1.0.0-rcN. Every one of them shipped as a real release, so each is
counted and given its number under this scheme. Old files in `dist/` keep their original names.

| Old label | New | Class | What changed |
|---|---|---|---|
| 0.1.0-m0 | 1.0.0 | first | Foundation: pinned mods, burn times, Zombie Break & Build config, Arnis map |
| 0.2.0-m1 | 2.0.0 | major | Salvage I: building profiles and loot, Lootr caches, the Atlas pass |
| 0.2.1-m1 | 2.1.0 | medium | Zombie spawning fixed, 8 survival items, Battery |
| 0.3.0 | 3.0.0 | major | Hydration, energy guns, Cell Packs, chargers and relays |
| 0.3.1 | 3.0.1 | minor | Guns renamed to Blasters |
| 0.4.0 | 3.1.0 | medium | Blaster bolts, sounds and balance |
| 0.4.1 | 3.1.1 | minor | Item textures reverted |
| 0.5.0 | 4.0.0 | major | Director I: zombie senses, populations, variants, barricades |
| 0.6.0 | 5.0.0 | major | Lost Cities procedural city, districts, safehouse and landmarks |
| 0.7.0 | 6.0.0 | major | Bunker Cores, infection, bosses |
| 0.8.0 | 7.0.0 | major | Grid I: the district relight loop, story, radio, HUD and map |
| 0.9.0 | 8.0.0 | major | Plasma Blades, the Weaponsmith Bench, hordes and Breaches, special infected |
| 0.10.0 | 9.0.0 | major | The Black Start finale, Sever and Bind endings, admin GUIs |
| 1.0.0-rc1 | 9.1.0 | medium | Finale and siege balance, one shared day clock |
| 1.0.0-rc2 | 9.2.0 | medium | Lag fix, scopes, mission briefing and HUD tracker |
| 1.0.0-rc3 | 9.3.0 | medium | Alloy Plate and Wire Spool recipes, charged Cell Packs as loot |
| 1.0.0-rc4 | 9.3.1 | minor | Redstone Lamp recipe |
| 1.0.0-rc5 | 9.3.2 | minor | Blaster tooltips, windows drop Glass Shards |
| 1.0.0-rc6 | 9.3.3 | minor | Antibiotics in homes |
| 1.0.0-rc7 | 9.4.0 | medium | Scavenger, Hazmat and Riot armour; zombie block damage heals |
| 1.0.0-rc8 | 9.4.1 | minor | No sleeping on horde nights; zombie-placed blocks vanish |
| 1.0.0-rc9 | 9.5.0 | medium | Fast travel, Reinforced Door, Rapid Charger, Satchel, loot-only backpacks |
| 1.0.0-rc10 | 9.5.1 | minor | Repackage of rc9 |
| 1.0.0-rc11 | 9.6.0 | medium | Flatter city (new world generation), easier districts |
| 1.0.0-rc12 | 9.7.0 | medium | Rain Collector, street loot, supply drops, bicycle |
| 1.0.0-rc13 | 9.8.0 | medium | District decay, slower difficulty scaling |
| 1.0.0-rc14 | 9.9.0 | medium | Quiet weapons |
| 1.0.0-rc15 | 9.9.1 | minor | Food-style bandages, slower bicycle, cheaper Grid Couplers, more Weapon Parts |
| (rc16) | 9.10.0 | medium | Police stations generate randomly (changes world generation) |
| (rc17) | 9.11.0 | medium | Radio jobs (Shortwave Set), traps and lures (Rattle Box, Tripline Alarm, Spike Strip), horde waves retry instead of being lost |
| (rc18) | 9.12.0 | medium | Graffiti marks (Spray Can), dogs (tamed wolves guard and growl, Stray Dogs in residential zones tamed with Rotten Flesh, no friendly fire against pets), building keys and Strongboxes (Crowbar), Weapon Parts slightly more common |
| (rc19) | 10.0.0 | major | Survivors: rescue survivors from distress calls (day 7+), follow/stay/settle orders by clickable chat, Guard role, Supply Locker, zombies hunt survivors, survivor infection and turning, no friendly fire against survivors; Scavenger, Medic, Mechanic, food upkeep, role-change rules; district traders; building holdouts |
| (rc20) | 10.1.0 | medium | Weapon weight (heavier weapons slow you), draw and holster time, blaster stun mode (Spark, Volt, Ion; C to switch; silent, 3 s hold), Flamethrower (Lighter Fluid tank, cone of fire, never burns blocks); zombies attack the nearest of players and survivors; Carry On only picks up containers |
| (rc21) | 10.1.1 | minor | Medics treat only their own group, rescues skip full players, world-full message for rescues and holdouts, horde waves keep spawns owed after a partial wave, graffiti index keeps every mark, survivors refuse water and golden apples and ignore environmental damage (folded into 10.2.0, no separate pack) |
| (rc22) | 10.2.0 | medium | Fortified blocks wear down under zombie attack and downgrade; repair with materials; Mechanics repair walls; post-horde core warning; worn blocks keep their wear when broken, re-placed or pushed; Arc Charge stun grenade; `ashfall:friendly_fire` game rule (off by default) lets players hurt pets and burn each other with the Flamethrower |
| (rc23) | 10.2.1 | minor | Optional resource pack "Ashfall Polished Arms": dark machined-metal skins at 2x detail for the seven blasters, both Plasma Blades, the Flamethrower and the Arc Charge (generated by `tools/gen_polished_arms.py`, off by default) |
| (rc24) | 10.2.2 | minor | Balance: Mechanics repair one set a turn; obsidian repairs with Scrap Metal; wood repairs take 2 Nails; repairs take 45 wear with a 1 s cooldown; iron/Reinforced doors drop no scrap; Storm Repeater 400 rpm, 2 energy, 7 heat, cool 24; Lancer cool 22; Overcharger heat x1.2; Pipe Wrench 1.4 swings/s; Energy Cell takes 4 Wire Spools; Guards hit at full damage with 30 health; scavengers keep 1 in 4 Antibiotics; doc fixes (horde day tiers, ambient cap day 120) |
| (rc25) | 10.2.3 | minor | Zombies (bosses included) can no longer break or wear the sealed doors of Patient Zero's deep ward before the Mission district is reclaimed |
| (rc26) | 10.2.4 | minor | Guards refuse Plasma Blades (in a guard's hands the blade never used charge, wore its crystal or made noise); a guard already holding one hands it back |
| (rc27) | 10.3.0 | medium | Licence clean-up: Zombified Player, Simple Voice Chat and Realistic Torches removed. Dying infected is an ordinary death (items drop where you died; the field report counts "lost to the infection"). Ashfall's own torch burnout replaces Realistic Torches: a torch a player places burns about 12 minutes (data: `ashfall/torch_burnout`), then becomes a dark burnt-out torch (drops a stick) that Matches, Lighter Fluid or flint and steel relight for one use. World-generated torches never burn out; in an updated world, torches Realistic Torches had put out come back as ordinary lit torches |
| (rc28) | 10.3.1 | minor | Every Ashfall recipe is in the vanilla recipe book (unlocks when you pick up a key ingredient; the two special recipes, socketing a crystal and fitting a Dampener, stay out); wooden ingredients take any wood (Reinforced Planks now take any planks); police stations drop Weapon Parts half as often (building loot weight 16 to 8, about 24% to 14% a container; the armory cache is unchanged); beds only work inside your own or your party's Bunker Core claim (game rule `ashfall:bunker_beds_only`, on by default); fix: horde waves (and Black Start waves) no longer lose their room to building zombies, so a base in the dense city gets its horde (the "zombie caps full" log line now gives the counts) |
