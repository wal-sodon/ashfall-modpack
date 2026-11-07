# Spec: Plasma Blades, Weaponsmith Bench and attachments (2026-10-26, plan M5 + M6)

Module: **arms** (one small hook in **director**: `ItemLight.addSource`). All numbers are datapack JSON and reload with `/reload`. Names are Ashfall's own.

## 1. Plasma Blade (`ashfall:plasma_blade`, `blade/PlasmaBladeItem`)

A melee weapon for crowd control, but it does not clear hordes. It costs charge, hunger and crystal wear, so a horde night is still won with blasters, walls and noise.

**Recipe** (shaped): Resonance Crystal on top; Focusing Lens between two Scrap Electronics; Scrap Metal, Battery, Duct Tape on the bottom row. A new blade has no charge and a fresh crystal. The Resonance Crystal is rare: it comes from the three bosses and rarely from crash-site loot, so a blade is a mid-game goal.

**Stats file:** `data/ashfall/ashfall/plasma_blade/plasma_blade.json` (`BladeStats`, `BladeStatsManager`).

| Field | Shipped | Meaning |
|---|---|---|
| `damage` | 9 | First target's damage while powered (the item tooltip shows 9 at sword speed; each hit is rescaled from this file) |
| `unpowered_damage` | 2 | Blunt hilt: no charge, or a dull crystal. No wear and no drain |
| `energy_per_swing` | 1 | Charge per swing that lands, taken once, not per target |
| `crystal_integrity`, `wear_per_hit` | 600, 1 | Crystal life in hits. Wear is per target hit |
| `max_targets` | 3 | Targets one sweep can damage |
| `extra_target_multiplier` | 0.6 | Damage for targets 2 and 3 |
| `extra_target_exhaustion` | 1.0 | Extra hunger exhaustion for each of targets 2 and 3 |
| `noise_radius` | 6 | A small Director SOUND stimulus per landed hit |
| `light` | 12 | A lit blade's light level (Director LIGHT sense and LambDynamicLights) |
| `pylon_damage_multiplier`, `pylon_mining_speed` | 2.0, 12 | Against `#ashfall:crystal_pylons` (section 3) |

**Charge.** A Team Reborn Energy store of 120. The capacity is fixed in code so the client can draw the charge bar. Energy output is 0, so no charger can drain a blade. Field Chargers and Power Relays already charge any energy item, so blades charge at chargers with no change to them.

**Sweep cap.** Better Combat (`data/ashfall/weapon_attributes/plasma_blade.json`: two 150° horizontal slashes and a stab) hits every target of a sweep in the same server tick. The blade counts hits per player per tick, so it needs no Better Combat API: target 1 takes 100%, targets 2-3 take 60%, and damage to a 4th target is cancelled in `ServerLivingEntityEvents.ALLOW_DAMAGE`, which also stops its knockback.

**Crystal wear (durability).** The `ashfall:blade_crystal` component holds integrity and max. When it reaches 0 the blade goes dull: a shatter sound and an action-bar message. The item is never destroyed. The `ashfall:socket_crystal` recipe (blade + Resonance Crystal) puts in a fresh crystal and keeps the charge. It refuses a blade whose crystal is still full, so a crystal is never wasted.

**Light.** The marker component `ashfall:blade_lit` is kept up to date each tick. The item model switches between the lit beam and the bare hilt from it. `assets/ashfall/dynamiclights/item/plasma_blade.json` makes it a LambDynamicLights source on clients, and `ItemLight.addSource` (director) lets the Director's LIGHT sense see it on a dedicated server too. Zombies notice a lit blade in the dark, so it is not a stealth weapon.

**Art.** `tools/models/plasma_blade.py` (3D hilt and beam models, the same style as the blasters) and `tools/textures/blade_art.py`, `tools/textures/items/plasma_blade_*`. All original designs.

## 2. Weaponsmith Bench and attachments

**Bench** (`ashfall:weaponsmith_bench`, `bench/`): crafted from Scrap Metal, Scrap Electronics, Duct Tape, a crafting table and planks. Its menu works like the anvil's: a blaster slot, an attachment slot and a result. The block stores nothing (no block entity), so breaking it never loses items, and the inputs come back when the menu closes. The result is computed only on the server, because the district gate reads `GridState`. The verdict (`BenchRules.Status`: EMPTY, READY, NOT_A_GUN, NOT_AN_ATTACHMENT, ALREADY_FITTED, LOCKED) is synced so the screen can say why nothing is offered.

**Slots.** A blaster holds one attachment per slot. Fitting into a full slot swaps the old attachment out and hands it back. Because the Cell Regulator and the Overcharger share the `core` slot, they cannot be fitted together.

**Attachments** (`data/ashfall/ashfall/gun_attachment/<id>.json`, `AttachmentStats`). Every number multiplies the blaster's `GunStats`, so one file works for every blaster. `AttachmentMath` is shared by the server and the HUD, so the HUD heat bar matches the server.

| Attachment | Slot | Effect | Gate (district Reclaimed) | Source |
|---|---|---|---|---|
| Optic | optic | aim spread ×0.4 and a 4x scope (`zoom` 0.25) while sneaking; see *Optic scope* below | none | recipe; police armory cache 25% |
| Dampener | muzzle | damage ×0.9, noise ×0.4, volume ×0.45 | none | recipe (existing); South Slope reward |
| Heat Sink | cooling | heat capacity ×1.35, cooling ×1.3 | South Slope | recipe; South Slope reward cache |
| Cell Regulator | core | energy per shot ×0.7 | South Slope | recipe |
| Overcharger | core | damage ×1.25, heat per shot ×1.4, energy per shot ×1.2 | Downtown | recipe |

Attachments are gated at the bench, not at crafting: one found in loot early still waits for its district. The server sends attachment stats to clients with `AttachmentStatsPayload` on join and after `/reload`, for the heat bar, the Optic's zoom and the tooltips.

**Optic scope.** Sneaking in first person with an Optic-fitted blaster aims down a scope (the right button still fires):
- **Zoom.** The high step is the gun's optional `scope_zoom` in `energy_gun/<id>.json` (a magnification, e.g. the Lancer Blaster Rifle's `7` = 7x), or else the Optic's own `zoom` (a field-of-view multiplier, 0.25 = 4x). The low step is half the high one (2x, or 3.5x on the Lancer); with a scope too weak for two steps (under 3x) there is one step. Aiming starts at the high step. While aiming, the **scroll wheel** switches steps (up zooms in, down out) and does not move the hotbar; the rest of the time scrolling is untouched. Capped at 12x. `ScopeMath` holds the numbers; `ScopeClient` drives them.
- **Easing.** The view eases in and out per frame (exponential approach, about 0.25 s to 95%, frame-rate independent), like PSWG's smooth zoom transition.
- **Accessibility.** The zoom multiplies the camera's final field of view (`OpticZoomMixin` on `Camera.calculateFov`), after vanilla's FOV modifier. So the **FOV Effects** slider does not weaken it: at 0 the scope still zooms, because scoping is a mechanic (like the spyglass's view), not a speed effect. Sprint/speed FOV changes still follow the slider.
- **Overlay.** Past 1.4x a full-screen overlay (`textures/misc/blaster_scope.png`, drawn by `tools/textures/hud_scope.py`: black vignette, round sight, cyan duplex reticle) opens like the spyglass's, with the current step (e.g. "4x") under the reticle. While it is up the crosshair (`ArmsGunsClient` replaces the vanilla crosshair element with one that skips drawing while scoped) and the first-person hands and blaster (`ScopeHandsMixin`) are hidden. The blaster's heat and charge HUD draws above it.
- **Sensitivity.** Mouse turning is scaled by 1/magnification (`ScopeMouseMixin`), following the eased zoom, so aiming at 4x or 7x is as controllable as unscoped.
- **Third person.** No zoom and no overlay, as in PSWG: scoping is first-person only. Aim spread still tightens whenever you sneak.
- The blaster tooltip shows the steps (`Scope: 2x / 4x`) once an Optic is fitted.

**Loot changes in the same track.** Crash sites have a 6% roll on `ashfall:blasters/rare`. The Mission reward cache rolls `ashfall:blasters/rare` once. The police building table lost its 0.4% blaster pool, which had a malformed `condition` key; police blasters now come from the armory cache.

**Art.** `tools/models/weaponsmith_bench.py`, `tools/textures/items/{optic,heat_sink,cell_regulator,overcharger}.py` and the scope overlay `tools/textures/hud_scope.py`.

## 3. Crystal pylons (ready for M9.5)

`#ashfall:crystal_pylons` exists as both a **block** tag and an **entity** tag:
- A pylon entity takes ×2 damage from a powered blade.
- A pylon block is mined at speed 12 by a lit blade.

The Heart pylons themselves are M9.5 and are not built yet. Both tags already list `ashfall:heart_pylon` as an optional entry (`"required": false`). **M9.5 must register its pylon under that id** (as a block, per endgame-design), and the blade bonus then works with no code change.

## 4. Tests

- JUnit: `BladeLogicTest` (sweep shares, hunger only for extra targets, damage rescaled from the attribute, wear never negative, swings counted per tick) and `AttachmentMathTest` (no attachments change nothing, the Dampener matches the old hard-coded numbers, Heat Sink, Regulator vs Overcharger trade-offs, the Optic only while aiming, multipliers stack) and `ScopeMathTest` (no Optic no scope, 4x/2x steps, the Lancer's `scope_zoom` overrides, clamping and one-step scopes, scroll direction, frame-rate-independent easing, sensitivity, overlay threshold and opening).
- Gametests arms `PlasmaBladeGameTest`: a sweep stops after the third target; unpowered and dull blades are blunt hilts; a crystal wears out and can be resocketed; chargers charge blades but never draw on them; lit blades shine for the Director.
- Gametests arms `WeaponsmithBenchGameTest`: attachment data loads; attachments change the firing stats; bench rules gate by district and keep slots exclusive; the bench menu fits and refuses.
- Client screenshots: not run (the Mac screen is locked). The blade in hand and the bench screen still need a look in game.
