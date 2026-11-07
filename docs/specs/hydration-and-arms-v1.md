# Spec: hydration + energy weapons v1 (2026-10-25)

Shared contract for the parallel build. Item and block ids are final. Numbers are starting balance and live in datapack JSON or named constants.

---

## A. Hydration (module: director)

The user's choices: clean vs dirty water, and thirst that works like hunger.

### State
Player attachment `ashfall:thirst`:
- Fields: `thirst` int 0–20, `saturation` float 0–20, `exhaustion` float 0–40.
- Persistent; synced to the owning player only.
- A new player starts at 20 / 5 / 0.
- After death, thirst resets to 20 / 5.

### Drain
- **From activity:** whatever exhaustion the player's hunger receives, thirst receives **1.5×**. Hook `Player.causeFoodExhaustion` or the 26.3 equivalent.
- **From heat:** +0.005 exhaustion per tick in biomes with base temperature > 1.0 (deserts, badlands, savanna).
- **Draining a point:** at exhaustion ≥ 4, subtract 4. Then remove 1 saturation if any is left, otherwise 1 thirst. This mirrors vanilla FoodData.
- Skip drain entirely for creative, spectator, Peaceful difficulty, and when the gamerule-like config `enabled=false`.

### Effects
| Thirst | Effect |
|---|---|
| 6 or less (3 droplets) | No natural health regeneration. |
| 0 | Can't sprint. Takes 1 damage every 80 ticks from a new damage type `ashfall:dehydration` ("X died of thirst"). The floor follows starvation: Easy stops at 10 HP, Normal at 1 HP, Hard kills. |

### Drinks
| Drink | Thirst | Saturation | Notes |
|---|---|---|---|
| `ashfall:bottled_water` (exists, salvage) | +8 | +4 | Clean. Keep its current food values: 2 nutrition, 0.3 saturation. Returns a glass bottle. |
| `ashfall:dirty_water` (new item, salvage) | +5 | +1 | Returns a glass bottle. 40% chance of Nausea for 10 s. 8% chance of `contagion:infection` for 600 s, applied by registry lookup, skipped if Contagion is absent. |
| Vanilla water bottle (`minecraft:potion` holding water) | — | — | Counts as dirty water when drunk. |

How players get dirty water:
- A glass bottle used on a water source gives `ashfall:dirty_water` in place of the vanilla water bottle.
- Cauldron bottles may stay vanilla; they're handled by the rule above.

Purifying: smelting, campfire_cooking and smoking `ashfall:dirty_water` gives `ashfall:bottled_water`.

### HUD
- A row of 10 droplets **directly above the hunger bar** (full, half, empty sprites 9×9 under `assets/ashfall/textures/gui/sprites/hud/`).
- Shown only when the player takes damage (survival or adventure).
- The vanilla air-bubble row moves up one row so it never overlaps the droplets.

### Command
`/thirst get|set <player> <value>` (op level 2), for testing.

---

## B. Energy weapons (module: arms)

### Items
| Id | Name | Class | Source |
|---|---|---|---|
| `spark_pistol` | Spark Blaster | pistol | crafted |
| `volt_sidearm` | Volt Blaster | pistol | crafted |
| `pulse_carbine` | Pulse Blaster Carbine | rifle, auto | crafted with Weapon Parts |
| `ion_rifle` | Ion Blaster Rifle | rifle | loot tier 4 |
| `arc_magnum` | Arc Blaster Magnum | pistol | loot tier 4 |
| `lancer_longrifle` | Lancer Blaster Rifle | rifle | loot tier 5 / police armory cache |
| `storm_repeater` | Storm Blaster Repeater | heavy, auto | loot tier 5 / police armory cache |
| `cell_pack_small` | Small Cell Pack | ammo for pistols | — |
| `cell_pack_standard` | Standard Cell Pack | ammo for rifles | — |
| `cell_pack_heavy` | Heavy Cell Pack | ammo for heavy | — |
| `dampener` | Dampener | attachment | — |

- `energy_cell` already exists and becomes an energy item holding 240.
- Blocks with block items: `field_charger`, `power_relay`.
- Entity: `energy_bolt`.

### Gun stats
Datapack: `data/ashfall/ashfall/energy_gun/<id>.json`, reloadable.

| Gun | Damage | RPM | Energy/shot | Heat/shot | Cool/s | Noise radius | Bolt speed | Spread° |
|---|---|---|---|---|---|---|---|---|
| spark_pistol | 4 | 300 | 1 | 12 | 25 | 24 | 4.0 | 1.5 |
| volt_sidearm | 6 | 240 | 1.5 | 15 | 30 | 32 | 4.5 | 1.0 |
| pulse_carbine (auto) | 7 | 300 | 2 | 9 | 25 | 40 | 5.0 | 1.2 |
| ion_rifle | 9 | 200 | 3 | 14 | 25 | 48 | 6.0 | 0.6 |
| arc_magnum | 14 | 90 | 3 | 34 | 20 | 56 | 5.0 | 0.8 |
| lancer_longrifle | 28 | 40 | 6 | 50 | 15 | 64 | 8.0 | 0.2 |
| storm_repeater (auto) | 6 | 600 | 1 | 4 | 20 | 64 | 5.0 | 2.5 |

- Every gun has heat capacity 100.
- Headshots do 1.75× damage.
- Bolts have range 96 and no gravity.
- Optional `scope_zoom`: the highest magnification with an Optic fitted (marksman rifles; the Lancer ships `7`). Without it the Optic's 4x applies. See blades-bench-v1, *Optic scope*.
- `bolt_color` is per gun: spark `#ff5a3c`, volt `#ffb33c`, pulse `#3cc8ff`, ion `#5aff8c`, arc `#ff3cb4`, lancer `#b45aff`, storm `#ff2d2d`.

### Overheat lockout
Heat is stored as `(heat, lastShotTick, locked)` in the data component `ashfall:gun_state` and computed lazily from game time, so no item ticking is needed.
- Firing adds heat per shot.
- Heat cools at **cool/s**, or **0.8× cool/s while locked**.
- Reaching 100 locks the gun: an overheat sound, steam particles and a loud noise event. It unlocks only when heat reaches 0.
- `allowComponentsUpdateAnimation` returns false, so heat updates don't make the held item bob.

### Ammo
- A gun holds one Cell Pack in the data component `ashfall:loaded_pack` (an ItemStack template of the pack, including its stored energy).
- Each shot spends energy_per_shot from the loaded pack. It can't fire when the pack is empty or missing; that plays a click.
- Pack capacities (energy units), through the Team Reborn Energy `SimpleEnergyItem` API:

| Pack | Capacity | Fits |
|---|---|---|
| small | 30 | pistols |
| standard | 60 | rifles |
| heavy | 120 | heavy |

- **Reload:** the **R** key (keybinding) sends a C2S payload. The server swaps the loaded pack for the fullest matching pack in the inventory, taking about 1.2 s (cooldown on the item, plus a sound). The old pack goes back into the inventory.
- A newly crafted gun is empty.

### Firing
- **Right-click (use):** semi guns fire once per click, respecting RPM through the item cooldown. Auto guns fire continuously while held (onUseTick), up to RPM.
- **Server-side projectile:** `energy_bolt`, a no-gravity projectile carrying damage, colour, a bolt style and a headshot check. It doesn't break or ignite blocks.
- **Bolt visuals (client):** an additive, full-bright beam (lightning render type): a near-white core inside a soft glow in the bolt colour, pointed head, fading tail, small bloom at the head. Length and thickness come from the gun's `BoltStyle` (pistols short and thin, Lancer 2 blocks long and thin, Storm Repeater short and thick), synced as a byte. The beam is drawn from the gun's muzzle (an offset that fades over the first 8 blocks) and never behind the bolt's start or within 0.5 blocks of the camera.
- **Shot effects:** muzzle flash (white core + coloured halo, firework flash sprite) and muzzle sparks, plus a few trail sparks per tick, all spawned client-side from the bolt itself (no packets). An impact sends one emitter particle (`ashfall:bolt_impact` / `ashfall:bolt_hit`, direction = surface normal x impact size); the client expands it into a flash, coloured sparks and orange embers that bounce, smoke, and a scorch mark (`ashfall:scorch`, big-smoke sprite tinted dark, flat on the surface, ~5 s). Particles honour the particle setting.
- **Dynamic light:** `assets/ashfall/dynamiclights/entity/energy_bolt.json` makes bolts emit light 12 when LambDynamicLights is installed (its data-driven entity light sources; no code or dependency, ignored without the mod).
- **Noise:** each shot emits a vanilla GameEvent (`PROJECTILE_SHOOT`) and calls `ArmsNoise.emit(level, pos, radius)`. That's a small static hook which only logs at debug level for now; the Director will consume it later.
- **Sounds:** our own `sounds.json` events over CC0 Kenney Sci-Fi Sounds (kenney.nl), converted to mono so they are positional and stored under `assets/ashfall/sounds/<category>/`. Each event has several variations with their own pitch and volume. Events:
  - `ashfall:gun.fire.light`, `gun.fire.heavy`, `gun.fire.auto` (generic shots)
  - per gun family: `gun.fire.pistol` (Spark Pistol, Volt Sidearm: quiet, high), `gun.fire.magnum`, `gun.fire.carbine`, `gun.fire.rifle` (Ion Rifle), `gun.fire.lancer` (deep, loud), `gun.fire.storm` (fast, rattly)
  - `gun.overheat` (force field), `gun.empty` (short click), `gun.reload` (computer noise)
  - `bolt.impact` (metal impact, blocks), `bolt.hit` (crunch, entities)
  - `charger.hum`, `relay.hum` (engine drones cut to ~2.3 s so the 2 s relay repeats overlap)
  - How far a shot carries is the server-side volume (`FireSound.loudness`, 16 blocks per 1.0): pistols 1.0, carbine 1.5, magnum 1.75, rifle and storm 2.0, Lancer 3.0; the Dampener multiplies it by 0.45.

### Dampener
- A crafting recipe, gun + dampener (shapeless, custom recipe type `ashfall:attach_dampener`), gives the gun with component `ashfall:attachments` holding `dampener`.
- Effect: noise ×0.4, damage ×0.9.
- Shown in the tooltip.

### HUD
While holding a gun:
- A heat bar (blue to orange to red, flashing when locked).
- Pack charge (e.g. `42/60`).
- The gun's name.

All drawn near the hotbar through `HudElementRegistry`.

### Chargers
| | Field Charger | Power Relay |
|---|---|---|
| Craftable | Yes | No. Admin-placed, and placed by the atlas at its recorded relay candidates. |
| Slots | 4 charge slots, 1 Energy Cell slot | Same |
| Buffer | 960 | 960 |
| Rate | 0.25 energy/tick per slot | **2 energy/tick** per slot |
| Where it works | **Only inside a claim** (OPAC) when OPAC is loaded; anywhere when it isn't. | Anywhere. |
| While charging | — | Plays `relay.hum` and calls `ArmsNoise.emit` every 40 ticks, radius 48 (draws zombies later). |

Both:
- Pull energy from an Energy Cell in the cell slot into the buffer. A drained cell stays as a 0-energy cell, rechargeable only at a Power Relay. Energy Cells are themselves energy items, so a cell in a charge slot of a Power Relay gets recharged.
- Charge any item exposing `EnergyStorage.ITEM`: Cell Packs, Energy Cells, and Plasma Blades later.
- Blockstate properties: `facing` (horizontal) and `active` (boolean, true while charging).
- Menu plus screen.

### Recipes (crafting table)
| Result | Ingredients |
|---|---|
| spark_pistol | 3 scrap_metal, 1 scrap_electronics, 1 duct_tape, 1 iron_ingot |
| volt_sidearm | 2 scrap_metal, 2 iron_ingot, 1 scrap_electronics, 1 battery, 1 duct_tape |
| pulse_carbine | 2 weapon_parts, 1 focusing_lens, 2 iron_ingot, 1 scrap_electronics, 1 duct_tape |
| cell_pack_small | 2 copper_ingot, 1 redstone, 1 scrap_electronics |
| cell_pack_standard | 3 copper_ingot, 2 redstone, 1 scrap_electronics, 1 iron_ingot |
| cell_pack_heavy | 4 copper_ingot, 2 redstone, 2 scrap_electronics, 1 iron_ingot |
| dampener | 2 iron_ingot, 2 cloth, 1 duct_tape |
| field_charger | 4 iron_ingot, 2 copper_ingot, 1 scrap_electronics, 1 battery, 1 redstone_block |

Shapes are up to the implementer; they must be shaped where that matters.

### Loot
- Add cell packs (rare, empty or partly charged through a loot function setting energy) to police, electronics and warehouse.
- Add arc_magnum and ion_rifle very rarely to police and the `ashfall_cache:police_armory`.
- lancer_longrifle and storm_repeater go only in `police_armory`, very rarely.
- Keep the overall scarcity.

### Models and art (art agents; not the code agents)
For each gun:
- `assets/ashfall/items/<id>.json` uses `minecraft:select` on `minecraft:display_context`: `gui`, `ground` and `fixed` use the flat icon model `ashfall:item/<id>_icon` (16×16 icon generated from `tools/textures/items/<id>_icon.py`); every other context uses the 3D model `ashfall:item/<id>`. A 26.3 example is in the client jar, e.g. `assets/minecraft/items/trident.json`.
- The 3D model is block-style elements, with a skin texture `textures/item/<id>_skin.png` of 32×32 or 64×64.
- The 3D model has display transforms for first and third person in both hands, holding it like a gun pointing forward.

Other art:
- **Cell packs and dampener:** flat 16×16 icons via `tools/textures/items/<id>.py`.
- **Blocks:** `field_charger` and `power_relay` get a blockstate, block model, textures (an active variant that glows) and item models.
- **Bolt and effect textures:** none of our own. The beam is untextured; particles reuse vanilla particle sprites (`spark_*`, `flash`, `big_smoke_*`) listed in `assets/ashfall/particles/*.json`.

---

## File ownership

### Code agents
| Agent | Owns |
|---|---|
| hydration | director module: `hydration` package, mixins, HUD, damage type, command, director lang and assets for HUD sprites; plus salvage: `dirty_water` item registration and purification recipes and lang, and the bottled water drink hook. |
| guns | arms: items (ArmsItems), gun logic, components, bolt entity and renderer, reload networking and keybind, HUD, dampener recipe, sounds.json, stats datapack, gun and pack recipes, loot additions, arms gametests for guns. |
| chargers | arms: `charger` package (blocks, block entities, menus, screens, registration in ArmsBlocks), charger recipe, the atlas relay placement in salvage (by registry lookup of `ashfall:power_relay`), charger gametests. |

Both arms agents may each add one init line to AshfallArms / AshfallArmsClient and their own keys to the arms lang file. Merging is done by the integrator.

### Art agents
Model JSON, textures and items/ definitions, in the main repo. Code agents must **not** create those files.
