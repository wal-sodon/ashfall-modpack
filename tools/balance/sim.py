#!/usr/bin/env python3
"""Ashfall offline balance simulation (phase 7, docs/balance-v1.md).

Pure Python, no dependencies. It reads the real datapack JSON of a source tree (blasters, attachments, the Plasma
Blade, zombie variants, special infected, the grid's settings/siege files, the horde schedule and the finale
settings) and estimates, for 1, 2 and 4 players with the typical gear of each act:

  * time-to-kill per enemy type for every blaster and the blade,
  * District Sieges: core HP lost, time, energy spent, and how fast an undefended core falls,
  * the Black Start finale: Uplink HP, pauses, Energy Cells fed, couplers used, energy spent,
  * horde nights: time and energy per tier,
  * the Energy Cell economy: expected income per in-game day against what the arc consumes.

It is a model, not the game. Zombies walk a path to the core at speed attribute x 10 blocks/s (the convention of
blaster-balance-v2), players stand at the objective and shoot the nearest zombie they can see, hits land with a fixed
chance, damage goes through the 26.3 armour formula, and heat is simulated per shot. Things it leaves out (players
getting hurt, screamer buffs, bloated clouds, doors and walls) are listed in docs/balance-v1.md.

Usage:
  python3 -I tools/balance/sim.py                 # this tree, markdown tables
  python3 -I tools/balance/sim.py --root <tree>   # another checkout (for before/after)
  python3 -I tools/balance/sim.py --runs 200 --seed 7
"""

import argparse
import json
import math
import os
import random
import statistics

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_ROOT = os.path.dirname(os.path.dirname(HERE))

# ---------------------------------------------------------------------------------------------------------- model
DT = 0.1                    # seconds per step (2 ticks)
SPEED_TO_BPS = 10.0         # movement_speed attribute -> blocks per second (blaster-balance-v2's convention)
BASE_HIT = 0.75             # chance a shot at a moving zombie lands
CLOSE_HIT = 0.88            # ... at a zombie already battering the objective
HEAD_SHARE = 0.20           # share of hits that are headshots
SIEGE_PATH_FACTOR = 1.4     # route through the substation's door vs straight line
SIEGE_ENGAGE = 16.0         # blocks of path from the core inside which defenders can see and shoot
FINALE_PATH_FACTOR = 1.3
FINALE_ENGAGE = 32.0        # open ridge: longer sight lines
BLADE_INTERVAL = 0.625      # sword cooldown (1.6 attacks/s, full charge)
BREAKER_WALK = 40 / 4.3     # sprint between the two Relay Breakers, seconds
PYLON_SECONDS = 10.0        # one pylon with a lit blade (black-start spec section 3)
PATCH_BELOW = 0.55          # a competent party patches the Uplink with a coupler under this share of its HP
FEED_BELOW = 15.0           # ... and feeds a cell when the Surge buffer drops under this many seconds
NIGHT_SECONDS = 11000 / 20  # nightfall to dawn
CELL_ENERGY = 240.0
DIRECTOR_CAP = 120          # SpawnRules.PLAYER_HARD_CAP: Director zombies near a player; spawns past it fail
UPTIME = 0.65               # share of seconds a player is actually shooting (moving, looking, healing, talking)
PACK_SWAP = 1.5             # seconds to swap an empty Cell Pack
PACKS = {"spark_pistol": 30, "volt_sidearm": 30, "arc_magnum": 30, "pulse_carbine": 60, "ion_rifle": 60,
         "lancer_longrifle": 60, "storm_repeater": 120}  # ArmsItems: small 30, standard 60, heavy 120
ZOMBIE_ARMOR = 2.0          # vanilla zombie natural armour (every zombie-family mob here keeps it)

# hit-chance multipliers for awkward targets
HIT_MULT = {"crawler": 0.85, "stalker": 0.6, "brute": 1.1, "choir": 1.15}

# ------------------------------------------------------------------------------------------------------- loading


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


class Data:
    def __init__(self, root):
        self.root = root
        a = os.path.join(root, "modules/arms/src/main/resources/data/ashfall/ashfall")
        d = os.path.join(root, "modules/director/src/main/resources/data/ashfall/ashfall")
        self.arms_data = os.path.join(root, "modules/arms/src/main/resources/data")
        self.guns = {f[:-5]: load(os.path.join(a, "energy_gun", f)) for f in sorted(os.listdir(os.path.join(a, "energy_gun")))}
        self.attachments = {f[:-5]: load(os.path.join(a, "gun_attachment", f))
                            for f in sorted(os.listdir(os.path.join(a, "gun_attachment")))}
        self.blades = {f[:-5]: load(os.path.join(a, "plasma_blade", f)) for f in sorted(os.listdir(os.path.join(a, "plasma_blade")))}
        self.variants = {f[:-5]: load(os.path.join(d, "zombie_variant", f)) for f in sorted(os.listdir(os.path.join(d, "zombie_variant")))}
        self.specials = {f[:-5]: load(os.path.join(d, "special_infected", f))
                         for f in sorted(os.listdir(os.path.join(d, "special_infected")))}
        self.grid = load(os.path.join(a, "grid/settings.json"))
        self.siege = load(os.path.join(a, "grid/siege.json"))
        self.finale = load(os.path.join(a, "finale/settings.json"))
        self.schedule = load(os.path.join(d, "horde/schedule.json"))
        self.mob_drops = {}
        for dirpath, _, files in os.walk(os.path.join(a, "mob_drops")):
            for f in files:
                rule = load(os.path.join(dirpath, f))
                self.mob_drops[rule["entity"]] = rule["drops"]
        self.loot_dir = os.path.join(self.arms_data, "ashfall/loot_table")
        self.cache_dir = os.path.join(self.arms_data, "ashfall_cache/loot_table")
        self.horde_loot_dir = os.path.join(root, "modules/director/src/main/resources/data/ashfall/loot_table/horde")

# --------------------------------------------------------------------------------------------------------- enemies

ARMOR_VALUES = {
    "minecraft:iron_helmet": 2, "minecraft:chainmail_helmet": 2, "minecraft:iron_chestplate": 6,
    "minecraft:chainmail_chestplate": 5, "minecraft:iron_leggings": 5, "minecraft:chainmail_leggings": 4,
    "minecraft:iron_boots": 2, "minecraft:chainmail_boots": 1, "minecraft:netherite_helmet": 3,
    "minecraft:netherite_chestplate": 8, "minecraft:netherite_leggings": 6, "minecraft:diamond_boots": 3,
}
ARMOR_TOUGH = {"minecraft:netherite_helmet": 3, "minecraft:netherite_chestplate": 3, "minecraft:netherite_leggings": 3,
               "minecraft:diamond_boots": 2}


def equipment_armor(eq):
    """Expected armour and toughness from a variant's equipment block (min..max distinct slots of one weighted set)."""
    if not eq:
        return 0.0, 0.0
    sets = eq.get("sets", [])
    tw = sum(s.get("weight", 1) for s in sets) or 1
    pieces = (eq.get("min", 0) + eq.get("max", 0)) / 2
    armor = tough = 0.0
    for s in sets:
        slots = s.get("slots", {})
        n = len(slots)
        if n == 0:
            continue
        per_a = per_t = 0.0
        for items in slots.values():
            per_a += sum(ARMOR_VALUES.get(i, 0) for i in items) / len(items)
            per_t += sum(ARMOR_TOUGH.get(i, 0) for i in items) / len(items)
        share = s.get("weight", 1) / tw
        armor += share * per_a / n * min(pieces, n)
        tough += share * per_t / n * min(pieces, n)
    return armor, tough


class Enemy:
    def __init__(self, kind, hp, armor, tough, speed, core=True):
        self.kind, self.hp, self.armor, self.tough, self.speed, self.core = kind, hp, armor, tough, speed, core


def enemy_table(data):
    out = {}
    for vid, v in data.variants.items():
        if v.get("weight", 0) == 0 and vid not in ("patient",):
            continue
        attrs = v.get("attributes", {})
        ea, et = equipment_armor(v.get("equipment"))
        out[vid] = Enemy(vid, attrs.get("minecraft:max_health", 20.0), ZOMBIE_ARMOR + ea + attrs.get("minecraft:armor", 0),
                         et + attrs.get("minecraft:armor_toughness", 0), attrs.get("minecraft:movement_speed", 0.23))
    for sid, s in data.specials.items():
        attrs = s["attributes"]
        out[sid] = Enemy(sid, attrs.get("minecraft:max_health", 20.0), attrs.get("minecraft:armor", ZOMBIE_ARMOR),
                         attrs.get("minecraft:armor_toughness", 0), attrs.get("minecraft:movement_speed", 0.23))
    ca = data.finale["heart"]["choir_attributes"]
    out["choir"] = Enemy("choir", ca["minecraft:max_health"], ca["minecraft:armor"], ca["minecraft:armor_toughness"],
                         ca["minecraft:movement_speed"], core=False)
    return out


def after_armor(dmg, armor, tough):
    """26.3 CombatRules.getDamageAfterAbsorb."""
    f = 2.0 + tough / 4.0
    g = min(20.0, max(armor * 0.2, armor - dmg / f))
    return dmg * (1.0 - g / 25.0)

# ------------------------------------------------------------------------------------------------------------ guns


class Gun:
    def __init__(self, data, gid, attachments=()):
        g = data.guns[gid]
        self.id = gid
        self.label = gid.split("_")[0].capitalize() + "".join("+" + a.split("_")[0].capitalize() for a in attachments)
        self.dmg = g["damage"]
        self.tbs = max(1, round(1200 / g["rpm"])) / 20.0
        self.heat = g["heat_per_shot"]
        self.cool = g["cool_per_second"]
        self.cap = g.get("heat_capacity", 100)
        self.energy = g["energy_per_shot"]
        self.head = g.get("headshot_multiplier", 1.75)
        for a in attachments:
            at = data.attachments[a]
            self.dmg *= at.get("damage", 1.0)
            self.heat *= at.get("heat_per_shot", 1.0)
            self.energy *= at.get("energy_per_shot", 1.0)
            self.cap *= at.get("heat_capacity", 1.0)
            self.cool *= at.get("cool_per_second", 1.0)


class Shooter:
    def __init__(self, gun, blade=None):
        self.gun, self.blade = gun, blade
        self.heat = 0.0
        self.locked = False
        self.wait = 0.0
        self.blade_wait = 0.0
        self.energy = 0.0
        self.blade_energy = 0.0
        self.busy = 0.0          # seconds of non-combat work (patching, mining, holding)
        self.efficiency = 1.0    # hit-chance multiplier (a player at a breaker sees the Uplink badly)
        self.can_melee = True
        self.pack = PACKS.get(gun.id, 60)
        self.in_pack = self.pack
        self.second = 0.0
        self.idle = False

    def cool_down(self, dt):
        rate = self.gun.cool * (0.8 if self.locked else 1.0)
        self.heat = max(0.0, self.heat - rate * dt)
        if self.locked and self.heat <= 0:
            self.locked = False
        self.wait -= dt
        self.blade_wait -= dt

    def step(self, rng, zombies, engage, dt):
        self.cool_down(dt)
        self.second -= dt
        if self.second <= 0:
            self.second += 1.0
            self.idle = rng.random() > UPTIME
        if self.idle and self.busy <= 0:
            self.wait = max(self.wait, 0.0)
            self.blade_wait = max(self.blade_wait, 0.0)
            return
        if self.busy > 0:
            self.busy -= dt
            self.wait = max(self.wait, 0.0)
            return
        at_core = [z for z in zombies if z.hp > 0 and z.dist <= 0]
        if self.blade and self.can_melee and at_core:
            if self.blade_wait <= 0:
                self.swing(rng, at_core)
                self.blade_wait += BLADE_INTERVAL
            self.blade_wait = max(self.blade_wait, -dt)
            return
        self.blade_wait = max(self.blade_wait, 0.0)
        while self.wait <= 0 and not self.locked:
            visible = [z for z in zombies if z.hp > 0 and z.dist <= engage]
            if not visible:
                self.wait = max(self.wait, 0.0)
                return
            target = min(visible, key=lambda z: (z.dist if z.core else z.dist + 6))
            emergency = any(z.dist <= 0 and z.core for z in visible)
            if self.heat + self.gun.heat >= self.gun.cap and not emergency:
                return  # feather: let it cool rather than lock while nothing hits the objective
            self.fire(rng, target)
            self.wait += self.gun.tbs

    def fire(self, rng, z):
        g = self.gun
        self.heat += g.heat
        if self.heat >= g.cap:
            self.heat = g.cap
            self.locked = True
        self.energy += g.energy
        self.in_pack -= g.energy
        if self.in_pack < g.energy:
            self.in_pack = self.pack
            self.busy = max(self.busy, PACK_SWAP)
        p = (CLOSE_HIT if z.dist <= 0 else BASE_HIT) * HIT_MULT.get(z.e.kind, 1.0) * self.efficiency
        if rng.random() < min(0.97, p):
            dmg = g.dmg * (g.head if rng.random() < HEAD_SHARE else 1.0)
            z.hp -= after_armor(dmg, z.e.armor, z.e.tough)

    def swing(self, rng, at_core):
        b = self.blade
        self.blade_energy += b["energy_per_swing"]
        ordered = sorted(at_core, key=lambda z: z.hp)
        for i, z in enumerate(ordered[:b["max_targets"]]):
            dmg = b["damage"] * (1.0 if i == 0 else b["extra_target_multiplier"])
            z.hp -= after_armor(dmg, z.e.armor, z.e.tough)


class Zombie:
    __slots__ = ("e", "hp", "dist", "speed", "core")

    def __init__(self, e, dist):
        self.e, self.hp, self.dist, self.speed, self.core = e, e.hp, dist, e.speed * SPEED_TO_BPS, e.core

# ----------------------------------------------------------------------------------------------------------- loadouts

ACTS = {
    # act: (label, guns per player slot, blade carriers)
    "early": ("Spark/Volt", [("volt_sidearm", ()), ("spark_pistol", ())], 0),
    "mid": ("Pulse/Ion + Heat Sink", [("pulse_carbine", ("heat_sink",)), ("ion_rifle", ("heat_sink",))], 0),
    "late": ("Storm/Lancer + Heat Sink, 1 blade", [("storm_repeater", ("heat_sink",)), ("lancer_longrifle", ("heat_sink",))], 1),
}


def party(data, act, n):
    _, guns, blades = ACTS[act]
    out = []
    for i in range(n):
        gid, att = guns[i % len(guns)]
        out.append(Shooter(Gun(data, gid, att), data.blades["plasma_blade"] if i < blades else None))
    return out

# ------------------------------------------------------------------------------------------------------- specials


def special_weights(data, day, zone, siege=True):
    out = {}
    for sid, s in data.specials.items():
        if day < s["min_day"]:
            continue
        w = s["weight"] * (s["siege_multiplier"] if siege else 1.0) * s.get("zone_multiplier", {}).get(zone, 1.0)
        if w > 0:
            out[sid] = (w, s["max_alive_per_player"])
    return out


def roll_member(rng, enemies, table, specials, alive_specials, players):
    """One wave member: a special at its per-mille siege weight (if under its cap), else the wave's own table."""
    roll = rng.random() * 1000
    for sid, (w, cap) in specials.items():
        if alive_specials.get(sid, 0) >= cap * max(1, players):
            continue
        roll -= w
        if roll < 0:
            return enemies[sid]
    total = sum(table.values())
    r = rng.random() * total
    for vid, w in table.items():
        r -= w
        if r < 0:
            key = vid.split(":")[1]
            return enemies.get(key, enemies["walker"])
    return enemies["walker"]


def count_specials(zombies):
    out = {}
    for z in zombies:
        if z.hp > 0:
            out[z.e.kind] = out.get(z.e.kind, 0) + 1
    return out

# ------------------------------------------------------------------------------------------------------------ siege


def siege_tier_row(data, tier):
    best = None
    for row in data.siege["tiers"]:
        if row["tier"] <= tier and (best is None or row["tier"] > best["tier"]):
            best = row
    return best


def pick_scale(lst, players):
    if not lst:
        return 1.0
    return lst[min(len(lst), max(1, players)) - 1]


def siege_core_hp(data, row):
    return row.get("core_hp", data.grid["core"]["hp"])


def cell_drop_table(data):
    return {ent.split(":")[1]: sum(d["chance"] for d in drops if d["item"] == "ashfall:energy_cell")
            for ent, drops in data.mob_drops.items() if ent.startswith("ashfall:")}


def run_siege(data, enemies, rng, tier, day, zone, shooters, players, single_wave=False):
    row = siege_tier_row(data, tier)
    drop_table = cell_drop_table(data)
    dropped = 0.0
    s = data.siege
    core = data.grid["core"]
    hp0 = hp = siege_core_hp(data, row)
    dps = core["damage_per_zombie_second"]
    size = max(1, round(row["size"] * pick_scale(s.get("party_scale"), players)))
    waves = 1 if single_wave else row["waves"]
    specials = special_weights(data, day, zone)
    zombies = []
    spawned = 0
    last_wave = 0.0
    t = 0.0
    acc = 0.0
    first_arrival = None
    while t < NIGHT_SECONDS:
        alive = [z for z in zombies if z.hp > 0]
        if spawned < waves and (spawned == 0 or len(alive) <= s["next_wave_alive_at_most"]
                                or t - last_wave >= s["wave_interval_seconds"]):
            for _ in range(min(size, max(0, DIRECTOR_CAP - len(alive)))):
                e = roll_member(rng, enemies, row["variants"], specials, count_specials(zombies), players)
                d = rng.uniform(s["spawn_radius_min"], s["spawn_radius_max"]) * SIEGE_PATH_FACTOR
                zombies.append(Zombie(e, d))
            spawned += 1
            last_wave = t
        for z in zombies:
            if z.hp > 0 and z.dist > 0:
                z.dist -= z.speed * DT
                if z.dist <= 0 and first_arrival is None:
                    first_arrival = t
        for sh in shooters:
            sh.step(rng, zombies, SIEGE_ENGAGE, DT)
        dropped += sum(drop_table.get(z.e.kind, 0.0) for z in zombies if z.hp <= 0)
        zombies = [z for z in zombies if z.hp > 0]
        acc += DT
        if acc >= 1.0 - 1e-9:
            acc -= 1.0
            attackers = sum(1 for z in zombies if z.dist <= 0)
            hp -= int(attackers * dps)
            if hp <= 0:
                return dict(won=False, lost=hp0, hp0=hp0, t=t, arrival=first_arrival, energy=sum(x.energy for x in shooters), drops=dropped)
        if spawned >= waves and not zombies:
            return dict(won=True, lost=hp0 - hp, hp0=hp0, t=t, arrival=first_arrival, energy=sum(x.energy for x in shooters), drops=dropped)
        t += DT
    return dict(won=True, lost=hp0 - hp, hp0=hp0, t=t, arrival=first_arrival, energy=sum(x.energy for x in shooters), drops=dropped, dawn=True)

# ----------------------------------------------------------------------------------------------------------- finale


def horde_tier(data, day, grid_tier):
    tier = 1
    for step in data.schedule["day_tiers"]:
        if day >= step["from_day"]:
            tier = max(tier, step["tier"])
    return max(tier, grid_tier)


def horde_row(data, tier):
    best = None
    for row in data.schedule["tiers"]:
        if row["tier"] <= tier and (best is None or row["tier"] > best["tier"]):
            best = row
    return best


def run_finale(data, enemies, rng, day, shooters, players):
    f = data.finale
    tier = horde_tier(data, day, data.siege.get("grid_tier_base", 1) + 4)
    table = horde_row(data, tier)["waves"][-1]["spawns"]
    specials = special_weights(data, day, "ridge")
    wscale = pick_scale(f["scale"]["waves"], players)
    wave_size = round(f["waves"]["size"] * wscale)
    vent_count = max(1, round(f["vents"]["count"] * wscale)) if f["vents"]["count"] > 0 else 0
    up = f["uplink"]
    hp_max = hp = up["hp"]
    hold = f["breakers"]["hold_seconds"]
    zombies = []
    t = 0.0
    phase = "surge"
    phase_t = 0.0
    surge_done = 0.0
    buffer = float(f["buffer"]["start_seconds"])
    cells = couplers = pauses = 0
    paused_time = 0.0
    wave_clock = vent_clock = arc_clock = 0.0
    acc = 0.0
    breakers_left = None
    pylons_left = 4
    choir = None
    mobs_peak = 0
    blade_holder = next((s for s in shooters if s.blade), None)
    timeline = {}
    while t < 3600:
        alive = [z for z in zombies if z.hp > 0]
        intervals = {"surge": f["waves"]["surge_interval_seconds"], "feedback": f["waves"]["feedback_interval_seconds"],
                     "heart": f["waves"]["heart_interval_seconds"]}
        wave_clock += DT
        if wave_clock >= intervals[phase]:
            wave_clock = 0.0
            room = max(0, min(wave_size, f["waves"]["max_mobs"] - len(alive)))
            room = min(room, max(0, 60 * players - len(alive)))
            for _ in range(room):
                e = roll_member(rng, enemies, table, specials, count_specials(zombies), players)
                zombies.append(Zombie(e, rng.uniform(40, 64) * FINALE_PATH_FACTOR))
        if phase == "feedback":
            vent_clock += DT
            if vent_clock >= f["vents"]["interval_seconds"]:
                vent_clock = 0.0
                kinds = f["vents"]["kinds"]
                for _ in range(min(vent_count, max(0, f["waves"]["max_mobs"] - len(alive)))):
                    zombies.append(Zombie(enemies[rng.choice(kinds)], 9.0))
            arc_clock += DT
            if arc_clock >= up["arc_interval_seconds"]:
                arc_clock = 0.0
                hp -= up["arc_damage"]
        mobs_peak = max(mobs_peak, len(alive))
        for z in zombies:
            if z.hp > 0 and z.dist > 0:
                z.dist -= z.speed * DT
        # the party's chores: cells, couplers
        if phase == "surge" and buffer < FEED_BELOW and buffer + f["buffer"]["cell_seconds"] <= f["buffer"]["max_seconds"]:
            buffer += f["buffer"]["cell_seconds"]
            cells += 1
            shooters[-1].busy = max(shooters[-1].busy, 1.0)
        if hp < hp_max * PATCH_BELOW:
            hp = min(hp_max, hp + up["coupler_patch"])
            couplers += 1
            shooters[-1].busy = max(shooters[-1].busy, 1.0)
        for sh in shooters:
            sh.step(rng, zombies, FINALE_ENGAGE, DT)
        zombies = [z for z in zombies if z.hp > 0]
        if choir is not None and choir.hp <= 0:
            choir = None
        acc += DT
        if acc >= 1.0 - 1e-9:
            acc -= 1.0
            attackers = sum(1 for z in zombies if z.dist <= 0 and z.core)
            hp -= int(attackers * up["damage_per_zombie_second"])
        if hp <= 0:
            # paused: nothing spawns or hurts; the party clears the field, patches to the resume share and feeds a cell
            pauses += 1
            pause_start = t
            while any(z.hp > 0 and z.core for z in zombies) and t - pause_start < 300:
                for z in zombies:
                    if z.hp > 0 and z.dist > 0:
                        z.dist -= z.speed * DT
                for sh in shooters:
                    sh.step(rng, zombies, FINALE_ENGAGE, DT)
                zombies = [z for z in zombies if z.hp > 0]
                t += DT
            need = math.ceil(hp_max * up["resume_fraction"] / up["coupler_patch"])
            couplers += need
            hp = need * up["coupler_patch"]
            t += 5.0
            if phase == "surge":
                surge_done = max(0.0, surge_done - f["fail_setback_seconds"])
                if buffer <= 0:
                    buffer += f["buffer"]["cell_seconds"]
                    cells += 1
            elif phase == "feedback":
                breakers_left = None  # both trip open again
            paused_time += t - pause_start
            continue
        if phase == "surge":
            if buffer > 0:
                buffer -= DT
                surge_done += DT
            if surge_done >= f["surge_seconds"]:
                timeline["surge"] = t
                phase, phase_t, wave_clock = "feedback", 0.0, 0.0
                breakers_left = None
        elif phase == "feedback":
            if breakers_left is None:
                # two holders at once with 2+ players; solo holds them one after the other plus the run between
                breakers_left = hold if players >= 2 else 2 * hold + BREAKER_WALK
                holders = shooters[:2] if players >= 2 else shooters[:1]
                for h in holders:
                    h.efficiency, h.can_melee = 0.5, False
            breakers_left -= DT
            if breakers_left <= 0:
                for h in shooters:
                    h.efficiency, h.can_melee = 1.0, True
                timeline["feedback"] = t
                phase, phase_t, wave_clock = "heart", 0.0, 0.0
                if f["heart"].get("choir", True):
                    choir = Zombie(enemies["choir"], 4.0)
                    zombies.append(choir)
        elif phase == "heart":
            # the blade carrier mines pylons whenever the Choir is down and nothing batters the Uplink
            miner = blade_holder or shooters[0]
            per = PYLON_SECONDS if blade_holder else 120.0
            if choir is None and miner.busy <= 0 and not any(z.dist <= 0 and z.core for z in zombies):
                miner.busy = per
                pylons_left -= 1
                if pylons_left <= 0:
                    t += per
                    timeline["heart"] = t
                    break
        phase_t += DT
        t += DT
    energy = sum(s.energy for s in shooters)
    return dict(t=t, cells=cells, couplers=couplers, pauses=pauses, energy=energy, peak=mobs_peak, wave=wave_size,
                paused=paused_time, timeline=timeline, done=pylons_left <= 0, tier=tier)


def run_undefended_finale(data, enemies, rng, day):
    """No defender: seconds from the first zombie reaching the Uplink until it is down (the playtest's 40 s)."""
    f = data.finale
    tier = horde_tier(data, day, data.siege.get("grid_tier_base", 1) + 4)
    table = horde_row(data, tier)["waves"][-1]["spawns"]
    specials = special_weights(data, day, "ridge")
    size = round(f["waves"]["size"] * pick_scale(f["scale"]["waves"], 1))
    zombies = [Zombie(roll_member(rng, enemies, table, specials, {}, 1), rng.uniform(40, 64) * FINALE_PATH_FACTOR) for _ in range(size)]
    hp = f["uplink"]["hp"]
    t, acc, first = 0.0, 0.0, None
    wave_clock = 0.0
    while t < 1200:
        wave_clock += DT
        if wave_clock >= f["waves"]["surge_interval_seconds"]:
            wave_clock = 0.0
            zombies += [Zombie(roll_member(rng, enemies, table, specials, count_specials(zombies), 1),
                               rng.uniform(40, 64) * FINALE_PATH_FACTOR) for _ in range(size)]
        for z in zombies:
            if z.dist > 0:
                z.dist -= z.speed * DT
                if z.dist <= 0 and first is None:
                    first = t
        acc += DT
        if acc >= 1.0:
            acc -= 1.0
            hp -= int(sum(1 for z in zombies if z.dist <= 0) * f["uplink"]["damage_per_zombie_second"])
            if hp <= 0:
                return t - (first or 0)
        t += DT
    return float("inf")

# -------------------------------------------------------------------------------------------------------- horde night


def run_horde_night(data, enemies, rng, day, shooters, players):
    """One player's horde-night waves at their haven (each player gets their own; we simulate per player share)."""
    tier = horde_tier(data, day, 1)
    row = horde_row(data, tier)
    specials = special_weights(data, day, "suburbs")
    zombies = []
    t = 0.0
    schedule = []
    clock = 0.0
    for w in row["waves"]:
        clock += w["delay_seconds"]
        schedule.append((clock, w))
    i = 0
    kills = 0
    reached = 0
    acc = 0.0
    while t < NIGHT_SECONDS:
        while i < len(schedule) and t >= schedule[i][0]:
            for _ in range(schedule[i][1]["size"] * players):
                e = roll_member(rng, enemies, schedule[i][1]["spawns"], specials, count_specials(zombies), players)
                zombies.append(Zombie(e, rng.uniform(40, 64) * 1.2))
            i += 1
        for z in zombies:
            if z.hp > 0 and z.dist > 0:
                z.dist -= z.speed * DT
        for sh in shooters:
            sh.step(rng, zombies, 24.0, DT)
        before = len(zombies)
        zombies = [z for z in zombies if z.hp > 0]
        kills += before - len(zombies)
        acc += DT
        if acc >= 1.0:
            acc -= 1.0
            reached += sum(1 for z in zombies if z.dist <= 0)
        if i >= len(schedule) and not zombies:
            break
        t += DT
    return dict(t=t, kills=kills, energy=sum(s.energy for s in shooters), contact=reached / max(1, players), tier=tier)

# ------------------------------------------------------------------------------------------------------------ TTK


def ttk(gun, e, rng, runs=400, field=True):
    """Seconds from the first shot to the kill; field = BASE_HIT and HEAD_SHARE, else every shot a body hit."""
    times = []
    shots_list = []
    for _ in range(runs if field else 1):
        hp, heat, locked, t, shots = e.hp, 0.0, False, 0.0, 0
        while hp > 0 and t < 120:
            if locked:
                heat -= gun.cool * 0.8 * gun.tbs
                if heat <= 0:
                    heat, locked = 0.0, False
                t += gun.tbs
                continue
            shots += 1
            heat += gun.heat
            if heat >= gun.cap:
                heat, locked = gun.cap, True
            hit = not field or rng.random() < BASE_HIT * HIT_MULT.get(e.kind, 1.0)
            if hit:
                dmg = gun.dmg * (gun.head if field and rng.random() < HEAD_SHARE else 1.0)
                hp -= after_armor(dmg, e.armor, e.tough)
            if hp > 0:
                t += gun.tbs
                heat = max(0.0, heat - gun.cool * (0.8 if locked else 1.0) * gun.tbs)
        times.append(t)
        shots_list.append(shots)
    return statistics.mean(times), statistics.mean(shots_list)


def blade_ttk(blade, e):
    per = after_armor(blade["damage"], e.armor, e.tough)
    swings = math.ceil(e.hp / per)
    return (swings - 1) * BLADE_INTERVAL, swings

# ----------------------------------------------------------------------------------------------------------- economy


def table_expectation(path, items=("ashfall:energy_cell", "ashfall:battery")):
    t = load(path)
    out = {i: 0.0 for i in items}

    def cnt(c):
        if isinstance(c, (int, float)):
            return c
        if c is None:
            return 1
        return (c["min"] + c["max"]) / 2

    for p in t.get("pools", []):
        rolls = cnt(p.get("rolls", 1))
        ch = 1.0
        cond = p.get("condition")
        conds = cond if isinstance(cond, list) else [cond] if cond else []
        for c in conds:
            if c.get("type") == "minecraft:random_chance":
                ch *= c["chance"]
        entries = p["entries"]
        tw = sum(e.get("weight", 1) for e in entries)
        for e in entries:
            if e.get("name") in out:
                mods = e.get("modifier") or []
                if isinstance(mods, dict):
                    mods = [mods]
                n = 1
                for m in mods:
                    if m.get("type") == "minecraft:set_count":
                        n = cnt(m["count"])
                out[e["name"]] += ch * rolls * e.get("weight", 1) / tw * n
    return out


# Assumed container mix one player loots in a day of city scavenging (building loot tables by share).
CONTAINER_MIX = {"home": 0.30, "office": 0.10, "shop": 0.15, "generic": 0.10, "warehouse": 0.08, "workshop": 0.07,
                 "electronics": 0.05, "police": 0.03, "hotel": 0.04, "school": 0.04, "fuel": 0.04}
CONTAINERS_PER_DAY = {"early": 30, "mid": 22, "late": 15}
AMBIENT_KILLS_PER_DAY = {"early": 30, "mid": 45, "late": 60}


ECONOMY_ITEMS = ("ashfall:energy_cell", "ashfall:battery", "ashfall:scrap_electronics", "ashfall:wire_spool")


def economy(data):
    per_container = {k: 0.0 for k in ECONOMY_ITEMS}
    for name, share in CONTAINER_MIX.items():
        ex = table_expectation(os.path.join(data.loot_dir, "buildings", name + ".json"), ECONOMY_ITEMS)
        for k in per_container:
            per_container[k] += share * ex[k]
    drop = {}
    for ent, drops in data.mob_drops.items():
        drop[ent] = sum(d["chance"] * (d.get("min", 1) + d.get("max", 1)) / 2 for d in drops if d["item"] == "ashfall:energy_cell")
    horde_loot = {}
    if os.path.isdir(data.horde_loot_dir):
        for f in sorted(os.listdir(data.horde_loot_dir)):
            horde_loot[f[:-5]] = table_expectation(os.path.join(data.horde_loot_dir, f))
    caches = {}
    for name in ("grid_reward_river_arts", "kiln_office", "hospital_supply", "police_armory", "wreck_hull"):
        p = os.path.join(data.cache_dir, name + ".json")
        if os.path.exists(p):
            caches[name] = table_expectation(p)
    bosses = {}
    for name in ("abomination", "patient_zero", "juggernaut"):
        p = os.path.join(data.loot_dir, "entities/boss", name + ".json")
        if os.path.exists(p):
            bosses[name] = table_expectation(p)["ashfall:energy_cell"]
    crash = table_expectation(os.path.join(data.loot_dir, "chests/crash_site.json"))
    return dict(per_container=per_container, drop=drop, horde_loot=horde_loot, caches=caches, bosses=bosses, crash=crash)


def special_kill_cells(data, day, zone, siege, drops):
    w = special_weights(data, day, zone, siege)
    return sum(wt / 1000 * drops.get("ashfall:" + sid, 0) for sid, (wt, _) in w.items())

# ------------------------------------------------------------------------------------------------------------- report

SIEGES = [  # district, act, siege tier, day, zone
    ("River Arts", "early", 2, 7, "river_arts"),
    ("South Slope", "early", 2, 10, "south_slope"),
    ("Downtown", "mid", 3, 14, "downtown"),
    ("Mission", "late", 4, 20, "mission"),
    ("Breach (late)", "late", 5, 28, "downtown"),
]
PARTIES = (1, 2, 4)
FINALE_DAY = 25


def fmt(x, nd=0):
    if x is None:
        return "-"
    if isinstance(x, float) and math.isinf(x):
        return "never"
    return f"{x:.{nd}f}"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", default=DEFAULT_ROOT, help="source tree to read the JSON from")
    ap.add_argument("--runs", type=int, default=120, help="Monte Carlo runs per cell")
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--only", default="", help="comma list of sections: ttk,siege,undefended,finale,horde,economy")
    args = ap.parse_args()
    data = Data(args.root)
    enemies = enemy_table(data)
    only = set(filter(None, args.only.split(",")))

    def want(s):
        return not only or s in only

    print(f"# Ashfall balance sim ({os.path.abspath(args.root)}, {args.runs} runs, seed {args.seed})\n")

    if want("ttk"):
        rng = random.Random(args.seed)
        guns = [Gun(data, "spark_pistol"), Gun(data, "volt_sidearm"), Gun(data, "pulse_carbine", ("heat_sink",)),
                Gun(data, "ion_rifle", ("heat_sink",)), Gun(data, "arc_magnum"), Gun(data, "lancer_longrifle", ("heat_sink",)),
                Gun(data, "storm_repeater", ("heat_sink",))]
        kinds = ["walker", "runner", "armored", "patient", "crawler", "bloated", "spitter", "screamer", "stalker", "brute", "choir"]
        print("## Time to kill (field: 75% hits, 20% headshots; seconds from the first shot / shots)\n")
        print("| Enemy (HP/armour) | " + " | ".join(g.label for g in guns) + " | Blade |")
        print("|---|" + "---|" * (len(guns) + 1))
        for k in kinds:
            e = enemies[k]
            cells = []
            for g in guns:
                t, n = ttk(g, e, rng)
                cells.append(f"{t:.1f} s / {n:.0f}")
            bt, bs = blade_ttk(data.blades["plasma_blade"], e)
            cells.append(f"{bt:.1f} s / {bs}")
            print(f"| {k} ({e.hp:.0f}/{e.armor:.1f}) | " + " | ".join(cells) + " |")
        print("\nSustained (no-lock) DPS on a walker: " + ", ".join(
            f"{g.label} {after_armor(g.dmg, 2, 0) * min(1 / g.tbs, g.cool / g.heat):.1f}" for g in guns) + "\n")

    if want("siege"):
        print("## District sieges (defended at the core, act gear)\n")
        print("| Siege | Tier | Players | Gear | Wave | Core HP | Won | HP lost (mean, p90) | Time | Energy (cells) | Cells dropped |")
        print("|---|---|---|---|---|---|---|---|---|---|---|")
        for name, act, tier, day, zone in SIEGES:
            for n in PARTIES:
                rng = random.Random(f"{args.seed}{name}{n}")
                res = [run_siege(data, enemies, rng, tier, day, zone, party(data, act, n), n) for _ in range(args.runs)]
                row = siege_tier_row(data, tier)
                lost = sorted(r["lost"] / r["hp0"] for r in res)
                won = sum(r["won"] for r in res) / len(res)
                size = max(1, round(row["size"] * pick_scale(data.siege.get("party_scale"), n)))
                print(f"| {name} | {tier} | {n} | {ACTS[act][0]} | {row['waves']}x{size} | {res[0]['hp0']} | {won:.0%} | "
                      f"{statistics.mean(lost):.0%}, {lost[int(0.9 * (len(lost) - 1))]:.0%} | "
                      f"{statistics.mean(r['t'] for r in res):.0f} s | {statistics.mean(r['energy'] for r in res) / CELL_ENERGY:.2f} | "
                      f"{statistics.mean(r['drops'] for r in res):.2f} |")
        print()

    if want("undefended"):
        print("## Undefended core (nobody at the substation; seconds from the first zombie at the core to its fall)\n")
        print("| Tier | Wave | Core HP | One wave only | With the following waves |")
        print("|---|---|---|---|---|")
        for tier in range(1, 7):
            rng = random.Random(f"{args.seed}u{tier}")
            row = siege_tier_row(data, tier)
            one, full = [], []
            for _ in range(args.runs):
                r = run_siege(data, enemies, rng, tier, 30, "downtown", [], 1, single_wave=True)
                one.append(float("inf") if r["won"] else r["t"] - (r["arrival"] or 0))
                r = run_siege(data, enemies, rng, tier, 30, "downtown", [], 1)
                full.append(float("inf") if r["won"] else r["t"] - (r["arrival"] or 0))
            print(f"| {tier} | {row['waves']}x{max(1, round(row['size'] * pick_scale(data.siege.get('party_scale'), 1)))} | "
                  f"{siege_core_hp(data, row)} | {fmt(statistics.median(one))} s | {fmt(statistics.median(full))} s |")
        rng = random.Random(f"{args.seed}uf")
        uf = statistics.median(run_undefended_finale(data, enemies, rng, FINALE_DAY) for _ in range(args.runs))
        print(f"\nUndefended Uplink (solo waves, Surge): falls {fmt(uf)} s after the first zombie reaches it.\n")

    if want("finale"):
        print(f"## Black Start (late gear, day {FINALE_DAY})\n")
        print("| Players | Wave | Done | Minutes | Pauses | Cells fed | Couplers | Gun energy (cells) | Extra cells total | Peak mobs |")
        print("|---|---|---|---|---|---|---|---|---|---|")
        for n in PARTIES:
            rng = random.Random(f"{args.seed}f{n}")
            res = [run_finale(data, enemies, rng, FINALE_DAY, party(data, "late", n), n) for _ in range(args.runs)]
            cells = statistics.mean(r["cells"] for r in res)
            gun = statistics.mean(r["energy"] for r in res) / CELL_ENERGY
            print(f"| {n} | {res[0]['wave']} (tier {res[0]['tier']}) | {sum(r['done'] for r in res) / len(res):.0%} | "
                  f"{statistics.mean(r['t'] for r in res) / 60:.1f} | {statistics.mean(r['pauses'] for r in res):.2f} | "
                  f"{cells:.1f} | {statistics.mean(r['couplers'] for r in res):.1f} | {gun:.1f} | {cells + gun:.1f} | "
                  f"{statistics.mean(r['peak'] for r in res):.0f} |")
        print()

    if want("horde"):
        print("## Horde nights (per player at their haven)\n")
        print("| Day | Tier | Players | Gear | Zombies | Time | Zombie-seconds at contact /player | Energy (cells) |")
        print("|---|---|---|---|---|---|---|---|")
        for day, act in ((5, "early"), (10, "early"), (15, "mid"), (20, "mid"), (25, "late"), (35, "late")):
            for n in (1, 4):
                rng = random.Random(f"{args.seed}h{day}{n}")
                res = [run_horde_night(data, enemies, rng, day, party(data, act, n), n) for _ in range(max(20, args.runs // 3))]
                print(f"| {day} | {res[0]['tier']} | {n} | {ACTS[act][0]} | {statistics.mean(r['kills'] for r in res):.0f} | "
                      f"{statistics.mean(r['t'] for r in res):.0f} s | {statistics.mean(r['contact'] for r in res):.0f} | "
                      f"{statistics.mean(r['energy'] for r in res) / CELL_ENERGY:.2f} |")
        print()

    if want("economy"):
        ec = economy(data)
        pc = ec["per_container"]
        print("## Energy Cell economy (one player)\n")
        print(f"Per looted building container: {pc['ashfall:energy_cell']:.3f} cells, {pc['ashfall:battery']:.3f} batteries, "
              f"{pc['ashfall:scrap_electronics']:.3f} Scrap Electronics, {pc['ashfall:wire_spool']:.3f} wire spools "
              "(a battery plus 4 wire spools crafts one cell, 2 Scrap Electronics' worth; each Grid Coupler also takes "
              "a battery, 2 spools and 1 Scrap Electronics).\n")
        print("Mob drops (expected cells per kill): " + ", ".join(f"{k} {v:.2f}" for k, v in sorted(ec["drop"].items()) if v) + "\n")
        hl = ec["horde_loot"]
        if hl:
            print("Horde wave extra drops (per zombie): " + ", ".join(
                f"{k} {v['ashfall:energy_cell']:.3f} cells / {v['ashfall:battery']:.3f} batteries" for k, v in hl.items()) + "\n")
        print("| Act | Containers/day | Cells/day from loot | Batteries/day | SE/day | Spools/day | Cells/day from kills | Horde night (cells) |")
        print("|---|---|---|---|---|---|---|---|")
        for act, day in (("early", 5), ("mid", 14), ("late", 22)):
            c = CONTAINERS_PER_DAY[act]
            amb = AMBIENT_KILLS_PER_DAY[act] * special_kill_cells(data, day, "downtown", False, ec["drop"])
            row = horde_row(data, horde_tier(data, day, 1))
            hn = 0.0
            for w in row["waves"]:
                lt = w.get("loot_table", "").split("/")[-1]
                per = hl.get(lt, {"ashfall:energy_cell": 0.0})["ashfall:energy_cell"]
                hn += w["size"] * (per + special_kill_cells(data, day, "suburbs", True, ec["drop"]))
            print(f"| {act} | {c} | {c * pc['ashfall:energy_cell']:.2f} | {c * pc['ashfall:battery']:.2f} | "
                  f"{c * pc['ashfall:scrap_electronics']:.2f} | {c * pc['ashfall:wire_spool']:.2f} | {amb:.2f} | {hn:.2f} |")
        print("\nOne-off sources: grid reward cache (per player, per district) "
              f"{ec['caches'].get('grid_reward_river_arts', {}).get('ashfall:energy_cell', 0):.1f} cells; bosses "
              + ", ".join(f"{k} {v:.1f}" for k, v in ec["bosses"].items())
              + f"; crash site {ec['crash']['ashfall:energy_cell']:.1f} cells + {ec['crash']['ashfall:battery']:.1f} batteries.\n")


if __name__ == "__main__":
    main()
