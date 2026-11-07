# Blaster Balance v2

Status: design, not yet implemented. Replaces the balance numbers and the blaster sections of `hydration-and-arms-v1.md`. Ids, items and the energy model stay the same.

Built on the "role-clarity" proposal, which the judges scored highest (61 against 58 for feel-spectacle and 55 for resource-economy), with judge fixes grafted in. Every number below comes from `final.py`. It uses the 26.3 armour formula, a tick-accurate HeatModel, the Storm's spin-up and a horde damage-pool model. To reproduce, run `python3 /private/tmp/claude-503/-Users-wal-projects-mod/5e3b2350-84fa-4752-b39c-ac4c8e6cb88f/scratchpad/balance/final.py`. Its full output is in `final_output.txt` next to it.

## 0. Fix first: bolts currently lose damage on rapid fire

I checked this against the 26.3 jar (`LivingEntity.hurtServer`, bytecode offsets 184-261). In 26.3 the hit cooldown is the public field `LivingEntity.damageCooldownTime`. `Entity.setInvulnerableTime(int)` writes a separate private field, `Entity.invulnerableTime`, which tracks temporary or spawn invulnerability.

That means `EnergyBolt.onHitEntity`'s `living.setInvulnerableTime(0)` does not reset the hit cooldown at all. Any bolt that reaches the same target within 10 ticks of the previous hit deals only `amount - lastHurt`. For a gun firing equal-damage bolts that is 0.

Every blaster's cycle is under 10 ticks:

| Blaster | Cycle (ticks) |
|---|---|
| Storm | 2 |
| Spark | 4 |
| Pulse | 4 |
| Volt | 5 |
| Ion | 6 |

So in the current build every blaster lands at most one full hit per 10 ticks on a single target. Playtest DPS today is far below the numbers in the v1 spec. Section 5 has the fix, and every number in this document assumes it.

## 1. Per-blaster stats

Craftable: Spark, Volt, Pulse. Loot tier 4: Ion, Arc. Loot tier 5 / armoury: Lancer, Storm. Heat capacity is 100 for all of them.

| Blaster (id) | Role | Dmg | rpm (ticks) | Auto | Energy/shot | Heat/shot | Cool/s | Noise | Bolt speed (b/t) | Spread | Range | Headshot | Colour |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Spark (`spark_pistol`) | Stealth sidearm | 5 | 300 (4) | semi | 1 | 11 | 25 | 12 | 4.0 | 1.5 | 48 | 2.0 | #ff5a3c |
| Volt (`volt_sidearm`) | Crowd-control sidearm | 7 | 171 (7) | semi | 1.5 | 17 | 28 | 28 | 4.5 | 1.0 | 64 | 1.75 | #ffb33c |
| Pulse (`pulse_carbine`) | Craftable all-rounder / panic button | 6 | 300 (4) | AUTO | 1.5 | 8 | 24 | 40 | 5.0 | 1.2 (+bloom) | 96 | 1.75 | #3cc8ff |
| Ion (`ion_rifle`) | Armour piercer | 10 | 150 (8) | semi | 3 | 16 | 25 | 48 | 6.0 | 0.6 | 128 | 1.75 | #5aff8c |
| Arc (`arc_magnum`) | Chain-lightning horde breaker | 13 | 92.3 (13) | semi | 5 | 28 | 24 | 56 | 5.0 | 0.8 | 64 | 1.75 | #ff3cb4 |
| Lancer (`lancer_longrifle`) | Charged marksman / boss killer | 28 (charged) | 40 (30) | semi, charge | 10 | 45 | 15 | 72 | 8.0 | 0.2 | 160 | 2.5 | #b45aff |
| Storm (`storm_repeater`) | Suppression | 6 | 400 (3) | AUTO | 2 | 7 | 24 | 80 | 5.0 | 3.0 (+bloom) | 80 | 1.5 | #ff2d2d |

### Signature effects and drawbacks

**Spark: Ambush Coil**
- The bolt deals x1.5 damage when the target `Mob`'s `getTarget()` is not the shooter. That covers mobs with no target, mobs chasing a co-op partner, and mobs chasing a decoy.
- An ambush hit plays its own crit hit-marker and sound (a higher-pitched zap with an `ENCHANTED_HIT` burst) so players learn the mechanic.
- The headshot multiplier is 2.0, so a head hit does 10, or 15 on an unaware target.
- Honest numbers:
  - Solo: only the first hit is an ambush, so a walker takes 4 body shots (7.4 + 3 x 4.9). Without the ambush it takes 5.
  - Co-op, while a partner holds aggro: 3 body shots.
- Drawbacks:
  - Lowest damage of the set.
  - Range 48: the bolt fizzles beyond that.
  - Hopeless against gear: 2.1 per hit against iron, 1.25 against diamond.
  - A horde of 15 walkers takes 34 s.

**Volt: Stun Bolt**
- A hit applies Slowness II for 20 ticks and knockback 0.8 (twice the bolt default).
- The slow is rate-limited: after it is applied, that target cannot be slowed by any Volt for 40 ticks. Track this with a transient tag or a tick stamp on the entity. Uptime is therefore at most 1 s in every 3 s on any one target, so the Volt cannot keep a target permanently slowed.
- A headshot also applies Weakness I for 40 ticks (-4 melee). That takes a walker to 0 and a horde zombie to 1.
- Drawbacks:
  - 7-tick cycle.
  - Only 27 shots per Small pack.
  - Locks out after 13 rapid shots, for 4.5 s.
  - Sustained DPS 11.7.
  - Bosses with knockback resistance 1.0 ignore the knockback. Mobs in `#ashfall:stun_immune` (Mutant and future bosses) ignore the slow.

**Pulse: Vent Blast**
- Fires on the shot that locks the gun (`HeatModel.Shot.justLocked`, already exposed in GunLogic line 94).
- A shockwave hits every `LivingEntity` within 3 blocks of the shooter except the shooter, players and tamed or named mobs. Each takes 6 damage, damage type `ashfall:vent_blast`, plus knockback 1.2 pointing away from the shooter.
- It fires once per lockout.
- VFX: a ring of 24 `ELECTRIC_SPARK` and cyan dust particles. SFX: a steam hiss followed by a low thump.
- From 30 shots at the trigger, the lockout turns into a get-off-me button.
- The HUD heat bar ticks and flashes at 80 heat, so the blast is something you plan for.
- Drawbacks:
  - Loudest craftable (40).
  - Bloom: +0.15 degrees per consecutive shot, up to 3.0. It resets after 10 ticks off the trigger.
  - Needs weapon parts to craft.
  - The blast always costs a 5.2 s lockout.

**Ion: Ion Lance**
- 50% armour pierce: one hit in which half the damage skips armour (see section 4).
- The bolt pierces 1 more entity at 70% damage.
- Results:

| Target | Damage per hit | Shots to kill |
|---|---|---|
| Iron-geared zombie | 7.1 | 3 (Pulse needs 8) |
| Diamond zombified player | 6.25 | 4 (Spark needs 16) |

- VFX: a green ion trail with an `END_ROD` puff where it passes through.
- Drawbacks:
  - 3.33 damage per energy, 800 per cell.
  - 20 shots per Standard pack.
  - Locks out after 15 rapid shots.
  - Noise 48.
  - Against unarmoured mobs it is no better than the Pulse: burst 25 against 30.
- Ion owns armour piercing. The Lancer has none.

**Arc: Chain Lightning**
- On an entity hit, an arc jumps to up to 3 more hostile `LivingEntity`s. Each jump goes from the previous target to the nearest unvisited mob within 4 blocks.
- Jump damage falls off at 50%, 35% and 20% of the bolt's damage: 6.5, 4.55 and 2.6. Damage type is `ashfall:arc_discharge` and armour applies. Jumps get no headshot bonus.
- Each jump applies a 10-tick Slowness IV micro-stun and respects `#ashfall:stun_immune`.
- Arcs never jump to players, tamed animals, named mobs or villagers. A config flag `arcFriendlyFire` defaults to false.
- Arcs ground out in rain or water: no jumps if the target `isInWaterOrRain()`.
- VFX: a jagged line of `ELECTRIC_SPARK` between targets (8 points with random lateral jitter). SFX: a crack.
- Drawbacks:
  - 5 energy a shot, so only 8 shots per Small pack.
  - 28 heat a shot, so it locks after 7 shots, for 5.2 s.
  - Noise 56.
  - Useless for its effect when it rains.

**Lancer: Thermal Lance (charged)**
- Hold use to charge for 15 ticks, then release.
- Partial charge:
  - Releasing before 6 ticks cancels the shot at no cost.
  - Releasing between 6 and 15 ticks fires a shot at the charge fraction c, from 0.4 to 1.0. Damage is 28 x c, and energy and heat scale by c too.
  - A full charge adds the pierce and the fire.
- A full charge pierces 2 more entities at 75% and 50% damage, takes a x2.5 headshot multiplier and ignites the target for 5 s (`setRemainingFireTicks(100)` on entities only; it never places fire blocks).
- This is the designed tier-5 Mutant Zombie counter. A kill while the Mutant is burning sets its lives to 0, which takes it from 3 x 150 HP to 150. That is 6 charged body shots or 3 to the head, and each hit refreshes the 5 s burn.
- No armour pierce: 28 raw already reduces iron armour to its floor (24.2 per hit, a one-shot) and diamond to 13.4 (2 shots).
- VFX: a white-violet beam drawn for 6 ticks. While charging, a rising whine plays and a ring fills around the crosshair.
- Drawbacks:
  - 10 energy, so 6 shots per Standard pack.
  - Locks after 4 charged shots, for 8.3 s.
  - Noise 72.
  - The shooter has Slowness II while charging. Sprinting or taking damage drops the charge.
  - Lowest burst DPS (18.7).

**Storm: Suppressing Fire**
- Each hit adds a stack of `ashfall:suppressed`: 2 s duration, refreshed on hit, up to 8 stacks.
- Each stack is -5% movement speed, up to -40%, through one transient `MOVEMENT_SPEED` attribute modifier (id `ashfall:suppressed`, `ADD_MULTIPLIED_TOTAL`, value -0.05 x stacks). It is removed when the stacks expire.
- Static particles scale with the stack count, so the effect is readable.
- `#ashfall:stun_immune` mobs take half the effect.
- With the bolt's 0.4 knockback 6.7 times a second, this lets the Storm hold a corridor.
- It is a different control from the Volt: a gradual, sustained drag where the Volt gives a burst stun.
- Drawbacks:
  - Spin-up: the first 10 ticks of every trigger pull fire at half rate.
  - Noise 80, the loudest.
  - 2 energy a shot, 3.0 damage per energy, the worst of the set.
  - A horde of 15 walkers costs a full Heavy pack. A horde-zombie wave costs 1.1 cells.
  - Headshot multiplier 1.5.
  - 3.0 degrees of spread, plus 0.1 degrees of bloom per shot up to 4.5.
  - The wielder moves 25% slower while firing.
  - Cannot take the Dampener.
  - Poor against gear: 2.6 per hit against iron.

Dropped from the proposals:
- The Storm's "loses its attack goal" rule (invisible and hacky).
- The Storm's Shred (-1 armour does nothing at mob armour 2-5, which is already at the 1.6-4% floor for these hits).
- The Arc's random fire blocks (griefing in the city map).
- Ion burn at tier 4 (it would make the boss check arrive early).
- The Spark's 25% random slow (unreadable).
- The Lancer tap-fire (it duplicated the Ion).

## 2. Cell Pack capacities and Dampener

| Pack | Capacity (energy) | Shots |
|---|---|---|
| Small (pistols) | 40 (was 30) | Spark 40, Volt 27, Arc 8 |
| Standard (rifles) | 60 | Pulse 40, Ion 20, Lancer 6 charged |
| Heavy | 150 (was 120) | Storm 75 |

An Energy Cell is still 240, and cells, not pack size, are the real limit. Fractional energy keeps accumulating through `EnergyCost`.

Dampener v2:

| Change | Value |
|---|---|
| Noise | x0.5 (x0.75 on the Lancer, whose charge vents loudly) |
| Damage | x0.9 |
| Bolt speed | x0.85 |
| Heat per shot | x1.15 |
| Bolt trail | hidden for the whole flight |

- It fits the pistol and rifle classes. It cannot fit the Storm (heavy class).
- Noise from the Pulse Vent Blast and from Arc jumps is also x0.5.
- With the Dampener:

| Blaster | Noise | Damage | Walker body shots |
|---|---|---|---|
| Spark | 6 | 4.5 | 5 |
| Volt | 14 | 6.3 | 4 |
| Pulse | 20 | 5.4 | 4 |

## 3. Computed tables

Normal difficulty, player-fired, no Dampener, every hit lands (section 5 fix). TTK is counted from the first shot.

### Summary against a walker or vanilla zombie (20 HP, 2 armour)

| Blaster | Burst DPS | Sustained DPS (30 s) | Shots to lock | Lockout | Shots/pack | Dmg/energy | Dmg/cell | Horde multiplier | Effective dmg/energy | Walker kills/pack | Noise radius x shots per kill |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Spark | 25.0 | 10.7 | 16 | 5.0 s | 40 | 5.00 | 1200 | 1.00 | 5.00 | 8.0 | 60 |
| Volt | 20.0 | 11.7 | 13 | 4.5 s | 27 | 4.67 | 1120 | 1.00 | 4.67 | 8.9 | 84 |
| Pulse | 30.0 | 18.0 | 30 | 5.2 s | 40 | 4.00 | 960 | 1.00 | 4.00 | 10.0 | 160 |
| Ion | 25.0 | 15.0 | 15 | 5.0 s | 20 | 3.33 | 800 | 1.49 | 4.97 | 6.7 | 144 |
| Arc | 20.0 | 10.8 | 7 | 5.2 s | 8 | 2.60 | 624 | 1.87 | 4.86 | 4.0 | 112 |
| Lancer | 18.7 | 9.3 | 4 | 8.3 s | 6 | 2.80 | 672 | 1.72 | 4.83 | 6.0 | 72 |
| Storm | 40.0 | 19.0 | 29 | 5.2 s | 75 | 3.00 | 720 | 1.00 | 3.00 | 18.8 | 320 |

The horde multiplier is the kill-weighted number of extra targets per shot from pierce or chain in a packed horde.

### Shots to kill (body/head), body TTK and kills per pack

| Blaster | Zombie / husk 20/2 | Horde / demolition 40/4 | Elite 40/5 | Armored (iron) 20/17 | Brute* 60/8/2 tough | Zombified player (diamond) 20/20/8 |
|---|---|---|---|---|---|---|
| Spark | 5/3, 0.80 s, 8.0 | 9/5, 1.60 s, 4.4 | 9/5, 1.60 s, 4.4 | 10/4, 1.80 s, 4.0 | 16/8, 3.00 s, 2.5 | 16/7, 3.00 s, 2.5 |
| Volt | 3/2, 0.70 s, 8.9 | 6/4, 1.75 s, 4.4 | 7/4, 2.10 s, 3.8 | 7/3, 2.10 s, 3.8 | 11/6, 3.50 s, 2.4 | 11/6, 3.50 s, 2.4 |
| Pulse | 4/2, 0.60 s, 10.0 | 7/4, 1.20 s, 5.7 | 8/4, 1.40 s, 5.0 | 8/4, 1.40 s, 5.0 | 13/7, 2.40 s, 3.1 | 13/7, 2.40 s, 3.1 |
| Ion | 3/2, 0.80 s, 6.7 | 5/3, 1.60 s, 4.0 | 5/3, 1.60 s, 4.0 | 3/2, 0.80 s, 6.7 | 7/4, 2.40 s, 2.9 | 4/2, 1.20 s, 5.0 |
| Arc | 2/1, 0.65 s, 4.0 | 4/2, 1.95 s, 2.0 | 4/2, 1.95 s, 2.0 | 3/2, 1.30 s, 2.7 | 6/3, 3.25 s, 1.3 | 5/3, 2.60 s, 1.6 |
| Lancer (charged) | 1/1, 0.75 s, 6.0 | 2/1, 2.25 s, 3.0 | 2/1, 2.25 s, 3.0 | 1/1, 0.75 s, 6.0 | 3/1, 3.75 s, 2.0 | 2/1, 2.25 s, 3.0 |
| Storm | 4/3, 0.75 s, 18.8 | 7/5, 1.20 s, 10.7 | 8/5, 1.35 s, 9.4 | 8/5, 1.35 s, 9.4 | 13/9, 2.10 s, 5.8 | 13/8, 2.10 s, 5.8 |

Notes:
- The husk is identical to the zombie.
- The demolition zombie is identical to the horde zombie, and it should be shot first.
- The Lancer TTK includes its 15-tick charge.
- *The brute is an ASSUMED placeholder (60 HP, 8 armour, 2 toughness) until M9 defines it. "Armored" means a zombie wearing full iron (17 armour).

### Mutant Zombie (150 HP, 0 armour, knockback resistance 1.0, 3 lives unless burning at death)

| Blaster | Body/head per life | Lives | Body shots total | Firing time | Lockouts | Energy |
|---|---|---|---|---|---|---|
| Spark | 30/15 | 3 | 90 | 42.0 s | 5 | 90 (0.38 cells) |
| Volt | 22/13 | 3 | 66 | 43.8 s | 5 | 99 |
| Pulse | 25/15 | 3 | 75 | 25.0 s | 2 | 112 |
| Ion | 15/9 | 3 | 45 | 26.9 s | 3 | 135 |
| Arc | 12/7 | 3 | 36 | 46.0 s | 5 | 180 (0.75 cells) |
| **Lancer (charged, ignites)** | 6/3 | **1** | 6 | 15.8 s | 1 | 60 (0.25 cells) |
| Storm | 25/17 | 3 | 75 | 21.7 s | 2 | 150 |

Firing time excludes the 7 s revive pauses. Heals of 2 HP per 5 s in the dark are not counted. The Mutant stays a loadout check: either bring the tier-5 Lancer, or bring 3 lives' worth of energy plus another fire source.

### Horde of 15, packed and approaching

Walkers arrive in 10.4 s from 24 blocks. Horde zombies (speed 0.30) arrive in 8.0 s. The Pulse is assumed to hit 90% of its shots and the Storm 80% (spread and bloom).

| Blaster | Walkers: clear time | Lockouts | Energy | Dead when they arrive | Horde zombies: clear time | Energy | Dead when they arrive |
|---|---|---|---|---|---|---|---|
| Spark | 34.2 s | 4 | 75 | 5.6/15 | 65.6 s | 135 | 1.8/15 |
| Volt | 28.0 s | 3 | 68 | 6.0 | 56.4 s | 135 | 2.2 |
| Pulse | 23.4 s | 2 | 100 | 6.8 | 38.5 s | 176 | 3.9 |
| Ion | 21.3 s | 2 | 93 | 7.5 | 34.0 s | 153 | 4.4 |
| Arc | 19.7 s | 2 | 85 | 8.4 | 39.4 s | 165 | 3.2 |
| Lancer | 27.9 s | 2 | 90 | 6.9 | 56.6 s | 180 | 3.4 |
| Storm | 21.7 s | 2 | 150 | 6.6 | 40.5 s | 264 (1.1 cells) | 3.5 |

The Storm's suppression slow and the Volt's stun buy extra time that the table does not model. Even so, no single gun stops a horde before contact. Walls, positioning, Plasma Blades, teammates and a mixed loadout are still needed.

### Checks (all pass in final.py)

- Craftables kill a vanilla zombie in 3-5 body shots: Spark 5, Volt 3, Pulse 4. Spark at 5 damage fixes the v1 bug where the 1.6% armour floor made it 6 shots.
- Every loot gun is worse per energy on a single target: 2.6-3.33 against 4.0-5.0 for the craftables. Counting pierce and chain, no loot gun beats the Spark's 5.0: Ion 4.97, Arc 4.86, Lancer 4.83, Storm 3.0.
- Every loot gun is louder than every craftable: 48-80 against 12-40.
- Top burst DPS is within 1.5x of the next gun: Storm 40, Pulse 30. That is down from 60 against 35 in v1.
- Spark and Pulse are distinct: the Spark has 5.0 dmg/energy against 4.0, and noise 12 against 40.
- Nothing kills 15 walkers or 15 horde zombies before contact. The best manages 8.4/15 and 4.4/15.
- Only the tier-5 charged Lancer ignites, so the Mutant revive counter stays a tier-5 gate.
- No gun leads everything:

| Leads | Blaster |
|---|---|
| DPS | Storm |
| Efficiency and stealth | Spark |
| Control | Volt |
| Craftable versatility plus the vent | Pulse |
| Armour | Ion |
| Clusters | Arc |
| Per-shot damage and bosses | Lancer |

## 4. Implementation notes (Minecraft 26.3, Fabric)

### New optional GunStats codec fields

All of these default to off, so v1 JSON still loads. Group them as `optionalFieldOf` inside a nested `"effects"` object, so the record stays under the RecordCodecBuilder arity limit. Mirror them on `EnergyBolt` as synced or saved fields where the hit logic needs them.

| Field | Type / default | Used by |
|---|---|---|
| `armor_pierce` | float 0 | Ion 0.5 |
| `pierce` | `{count:int, falloff:[float]}` | Ion `{1,[0.7]}`, Lancer charged `{2,[0.75,0.5]}` |
| `chain` | `{count, radius, falloff:[float], ground_in_rain:bool}` | Arc `{3, 4.0, [0.5,0.35,0.2], true}` |
| `ignite_ticks` | int 0 | Lancer charged 100 |
| `knockback` | float 0.4 | Volt 0.8 |
| `on_hit_effects` | `[{effect, amplifier, ticks, headshot_only, immunity_ticks}]` | Volt Slowness II/20/immunity 40, plus Weakness I/40 headshot only. Arc jumps use Slowness IV/10 |
| `unaware_multiplier` | float 1 | Spark 1.5 |
| `charge` | `{ticks, min_ticks, min_fraction, charged_only:[...]}` | Lancer `{15, 6, 0.4, ["pierce","ignite_ticks","headshot_multiplier"]}` |
| `spinup_ticks` | int 0 | Storm 10 |
| `bloom` | `{per_shot, max, reset_ticks}` | Pulse `{0.15, 3.0, 10}`, Storm `{0.1, 4.5, 10}` |
| `vent_blast` | `{radius, damage, knockback}` | Pulse `{3, 6, 1.2}` |
| `suppression` | `{max_stacks, ticks, speed_per_stack}` | Storm `{8, 40, 0.05}` |
| `wielder_slow` | float 0 | Storm 0.25 while firing, Lancer 0.3 while charging (Slowness II) |
| `dampener_noise_mult` | float 0.5 | Lancer 0.75 |
| `dampener_allowed` | bool true | Storm false (or keep it class-based through GunClass.HEAVY) |

Change `headshot_multiplier` and `range` per the table. Pack capacities are constants in the cell-pack items (Small 40, Heavy 150).

### Damage sources and armour pierce

- New damage types, each with a JSON under `data/ashfall/damage_type/`: `ashfall:energy_bolt` (exists), `ashfall:arc_discharge` and `ashfall:vent_blast`.
- All three go in `#minecraft:is_projectile` (bolt only), in `#minecraft:bypasses_cooldown` (all three) and in `#minecraft:no_knockback` (arc and vent, which apply their own knockback).
- Armour pierce is ONE `hurtServer` call, not two damage sources. Two sources would mean two hurt sounds, two knockbacks, and a `lastHurt` left holding only the second fragment.
- Mixin `LivingEntity.getDamageAfterArmorAbsorb(DamageSource, float)`, which is protected. Wrap its call to `CombatRules.getDamageAfterAbsorb(LivingEntity, float, DamageSource, float, float)` with a `@WrapOperation` (MixinExtras).
- When `source.getDirectEntity() instanceof EnergyBolt b && b.armorPierce() > 0`, return `ap*amount + original.call(entity, (1-ap)*amount, source, armor, toughness)`. That matches what final.py models.
- Armour durability damage (`hurtArmor`) still happens. Projectile Protection still applies at the magic-absorb step, which is intended.

### Pierce (Ion, charged Lancer)

- `EnergyBolt` keeps `piercesLeft` and an `IntOpenHashSet hitIds`.
- In `onHitEntity`, skip any entity already in `hitIds`. After damaging, if `piercesLeft > 0`, decrement it, scale `damage` by the next falloff and do not discard.
- Override `canHitEntity` to exclude ids already hit.
- Blocks still stop the bolt (`onHitBlock` discards it as now).

### Chain (Arc)

- In `onHitEntity`, after the primary hit (and only when it is not grounded: `!target.isInWaterOrRain()`), walk the chain from the target:
  - Search `level.getEntitiesOfClass(LivingEntity.class, prev.getBoundingBox().inflate(radius), filter)`.
  - Choose the nearest that is not visited, is alive, is not the owner, is not a `Player`, is not `OwnableEntity` with an owner, has no custom name, and is not a villager.
  - Check line of sight with `level.clip` between eye positions, so arcs do not pass through walls.
- Each jump calls `hurtServer` with the `ashfall:arc_discharge` source (direct = bolt, causing = owner) for `damage * falloff[i]`.
- Each jump adds the micro-stun and sends spark particles along the segment with `ServerLevel.sendParticles`.
- Each jump adds +8 noise to the shot's `ArmsNoise.emit`, capped at 80.

### Status effects

- Use `living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp), owner)`.
- For the Volt's per-target immunity, store the next allowed game tick on the entity, through an attachment (Fabric Data Attachment API, non-persistent) keyed `ashfall:stun_cd`.
- Skip mobs in `#ashfall:stun_immune`, an entity-type tag that starts with `mutantmonsters:mutant_zombie`.

### Suppression (Storm)

- Attachment `ashfall:suppressed` holds `{stacks, expireTick}`.
- On each hit, add a stack (up to the max), refresh the expiry, and re-apply the transient `AttributeModifier(Identifier("ashfall","suppressed"), -0.05*stacks, ADD_MULTIPLIED_TOTAL)` on `Attributes.MOVEMENT_SPEED` with `addOrReplaceTransientModifier`.
- A server tick hook (or a lazy check on the next hit, plus an entity tick mixin) removes the modifier once `expireTick` has passed.

### Ignite (charged Lancer)

- Call `target.setRemainingFireTicks(max(current, 100))` before `hurtServer`. That way the killing blow is already burning, which zeroes the Mutant's `DATA_REMAINING_LIVES` revive path.
- Never place fire blocks. Fire does nothing on wet or submerged targets.

### Vent Blast (Pulse)

- In `GunLogic` at `shot.justLocked()` (line 94), run an AABB search with radius 3 around the shooter, using the same exclusion filter as the chain.
- Call `hurtServer` with `ashfall:vent_blast` for 6 and `knockback(1.2, dx, dz)` away from the shooter, then play the particles and sound.

### Charge (Lancer)

- Switch the item to a use-duration flow: `getUseDuration` 72000 and `ItemUseAnimation.BOW`.
- Fire in `releaseUsing` with `c = clamp((useTicks - 0)/15, 0, 1)`. Cancel when `useTicks < 6`. Fire is gated by the rpm cooldown.
- Scale damage, energy and heat by `c`, where `c >= 0.4` once past `min_ticks`. Apply the `charged_only` fields only when `c == 1`.
- Cancel on sprint or on damage taken: check `isSprinting()` or `hurtTime > 0` in `onUseTick` and call `stopUsingItem()`.

### Spin-up and bloom

Track both in `GunState`: the tick the trigger was pulled, the consecutive shot count, and the last shot tick. Spin-up doubles `ticksBetweenShots` while `now - pullTick < spinup_ticks`. Bloom adds `min(max, per_shot*count)` to the spread passed to `shootFromRotation`.

### Ricochet

Not adopted. It was feel-spectacle's Volt effect and both judges called it unmodelled. If it comes back later: in `onHitBlock`, reflect `v - 2(v·n)n` with `n` taken from `BlockHitResult.getDirection()`, keep 70% damage, allow one bounce, and give the owner 10 ticks of immunity.

### Muzzle position and vision (user report)

**Cause.** `EnergyBolt.create` spawns the bolt at the shooter's X/Z at `eyeY - 0.1`, with no offset. On the client, `tick()` then spawns 3 dust particles of size 0.7 per tick, so the first ticks of every shot sit right over the crosshair.

**Fix.**
1. **Muzzle point.** `muzzle = eye + look*0.7 + right*0.30*side - up*0.22`.
   - `side` is +1 when the gun is in the main hand of a right-handed player and is mirrored for the off hand or `HumanoidArm.LEFT`.
   - `right` and `up` come from the view rotation.
   - If `level.clip(eye -> muzzle)` hits a block, spawn at the clip point minus 0.1 along `look`, so you cannot shoot through walls.
2. **Converge on the crosshair.**
   - Raycast from the eye along `look` for `range`, testing both blocks and entities (`ProjectileUtil.getEntityHitResult` and `level.clip`).
   - Aim the velocity from `muzzle` toward that hit point, or toward `eye + look*range` on a miss.
   - Then apply spread through `shoot(dir, speed, spread)` instead of `shootFromRotation`.
3. **Owner-view suppression.** On the client, while the bolt's owner is the local player and the camera is in first person, skip the bolt render and its trail for the first 2 ticks or while it is within 2 blocks of the eye.
4. **Lighter trail.** 2 motes per tick at size 0.45 (was 3 at 0.7). With the Dampener there is no trail at all.
5. **Muzzle flash.** On the client only, at the first-person muzzle: one `ELECTRIC_SPARK` and a 2-tick dynamic light if a lighting mod is present.

## 5. Invulnerability-frame decision

What 26.3 actually does, from bytecode:
- `hurtServer` takes the reduced-damage branch when `damageCooldownTime > 10` and the source is not in `#minecraft:bypasses_cooldown`.
- In every other case it sets `lastHurt = amount` and `damageCooldownTime = 20`, then calls `actuallyHurt`.
- So the `bypasses_cooldown` tag alone gives full bolt damage, but every bolt hit still opens a 20-tick window holding `lastHurt = boltDamage`. A Plasma Blade (9) swung right after an Ion bolt (10) would deal 0.

Decision:
1. Remove `living.setInvulnerableTime(0)`. It writes `Entity.invulnerableTime`, the wrong field, and does nothing for mob hit cooldowns.
2. Add `ashfall:energy_bolt`, `ashfall:arc_discharge` and `ashfall:vent_blast` to `#minecraft:bypasses_cooldown`. Every bolt and every secondary hit then lands in full, even in the same tick as another.
3. Around every bolt, arc or vent `hurtServer` call, save `damageCooldownTime` (public) and `lastHurt` (protected; read it through an accessor mixin `LivingEntityAccessor`), then restore both afterwards. Do not restore if the target died.
4. The result:
   - Blaster hits never consume or extend i-frames.
   - Melee and Plasma Blade hits after bolts deal full damage.
   - A co-op teammate's bolt cannot wipe the i-frames a player earned from a zombie hit.
   - The hurt flash, sound and knockback still play on every bolt (`hurtDuration` is not restored), so the Storm's stagger-push stays.
5. Friendly fire on players follows the server PvP setting as before. Arcs and vents never target players.

Ship this before any balance change, because the current build's DPS is limited by the cooldown and not by the numbers above.
