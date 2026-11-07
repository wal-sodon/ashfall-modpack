# Endings v1: the vote, Sever and Bind, the books, after the ending

This is the M9.5 ending layer of [endgame-design.md](../endgame-design.md) sections 4 and 5. The Black Start finale (track A) ends when the Heart falls. It then calls `Ending.beginChoice(level, participants)`, and everything from there on lives in `xyz.toesinthesand.ashfall.arms.story`.

## The choice

| | |
|---|---|
| Entry point | `Ending.beginChoice(ServerLevel, Collection<ServerPlayer>)` |
| Voters | The participants, plus every online member of their OPAC parties (`Claims.partyId`). With no participants, everyone online votes. |
| Prompt | Clickable chat buttons `[Sever] [Bind]` (they run `/ashfall ending vote sever\|bind`), with each ending's consequences on hover. A boss bar shows the tally and the countdown. |
| Window | 30 s per round (`EndingVote.WINDOW_SECONDS`) |
| Early end | One option has a strict majority of the online voters |
| Window over, or everyone voted | The option with more votes wins. A tie or no votes re-prompts once. A second tie or silence goes to **Sever**. |
| Solo | A click asks for confirmation (`/ashfall ending confirm`), and only the confirmation counts |
| Guards | No vote in a world that already has an ending, no second vote while one runs, and no vote with `ashfall:objective` off |

The arithmetic is `VoteTally` (pure, JUnit `EndingLogicTest`). A restart in the middle of a vote drops it. The finale, or `/ashfall ending begin`, starts it again.

## What the ending records

`Ending.apply` does the following:

- Stores the ending in `StoryState` (`data/ashfall_story_state.dat`): NONE, SEVER or BIND, the ending day, the ending game tick, the participants and the frozen field-report numbers.
- Sets the Director's story flags: `ashfall:sever` (the existing Antibiotics-cure hook) when the profile has `antibiotics_cure`, `ashfall:bind` for Bind, and any `flags` the profile lists.
- Raises the Black Start bonus to 20, so the grid reads 100 %.
- Rebuilds the haven regions so the Bunker Core radius bonus applies.
- Gives each participant the following:
  - the `ashfall:relight/asheville_relit` advancement, now on the `ashfall:story_ending` trigger
  - the **"Asheville Relit — Field Report"** book
  - the **"Asheville Relit — Epilogue"** book
  - the profile's `rewards`
  - the ending title
- Fires `Ending.CHOSEN`.

## Ending profiles (`data/ashfall/ashfall/ending/{sever,bind}.json`)

| Field | Sever | Bind | Applied by |
|---|---|---|---|
| `antibiotics_cure` | true | false | `StoryFlags` `ashfall:sever` → `InfectionRules.severEnding` |
| `spawn_multiplier` | 0.4 | 1.0 | `StoryHooks.spawnMultiplier` → `SpawnDirector.ambientCap`, building populations |
| `horde_interval_multiplier` | 3 (rare "Aftershocks") | 1 | `HordeHooks.intervalMultiplier` → `HordeSchedule.get(server)` |
| `horde_growth` | none | +1 tier and +10 % wave size per 7 days, from tier 4, capped at tier 6 / x1.5 | `HordeHooks.storyTier` and `waveSizeMultiplier`, clamped in code to tier VI / x2 |
| `free_relays` | true | false | `GridPower.powered`: every Power Relay charges without cells |
| `unlock_attachments` | false | true | `WeaponsmithBenchMenu`: the Overcharger fits without Downtown |
| `bunker_radius_bonus` | 0 | 2 chunks | `StoryHooks.bunkerRadiusBonus` → `Havens.rebuild` (haven region only, not the OPAC claim) |
| `rewards` | none | `ashfall:resonant_plasma_blade` | `Ending.apply` (charged, fresh 2400 crystal) |
| `aftershocks` | every 7 days, chance x0.5 | same | `Sites.setAftershocks` |
| `closing` | epilogue closing pages | same | `Epilogue` |

Profiles are read live (`StoryEffects`). A `/reload` rebalances an ending at once, and a reset undoes every effect by forgetting the ending. A missing file makes that ending neutral.

**Resonant Plasma Blade** (`ashfall:resonant_plasma_blade`) is a `PlasmaBladeItem` with its own stats file. It does 12 damage, takes 2400 crystal integrity (wear at a quarter of the plain blade's rate) and gives light 14. Its beam is cyan with a gold edge (`tools/models/resonant_plasma_blade.py`, `tools/textures/items/resonant_plasma_blade*_icon.py`). It is never crafted.

**The Ascendant** (`boss_lair/ascendant.json`, `zombie_variant/ascendant.json`) is a stronger Juggernaut at the Wreck hull: 520 HP, scale 1.6, netherite, glowing, end-rod particles, summons armored zombies. It drops 2 crystals plus `entities/boss/ascendant`. The lair has the new `requires_flag: "ashfall:bind"` field. With the flag the lair counts as defeated at the ending, so it first appears 10 days after the Bind and returns every 10 days after each defeat (`LairRules.maySpawn(..., endingGated)`). In a Sever world it never exists.

## Field report and epilogue

- **Field Report.** The report has three pages:
  - a cover with the ending
  - the party's numbers: days survived, districts lit, story-boss kills (sum of `BossState` kills), distinct Relay Logs heard by the party, players risen, deaths
  - the reader's own lines: their logs, deaths, and how many times they rose
- **Counters.** `StoryStats` (`data/ashfall_story_stats.dat`) and the per-player `ashfall:survivor_stats` attachment count deaths and deaths while infected, using the same gate that decides rising (`InfectedRising.shouldRise`). Neither is reset by a replay.
- **Epilogue.** The epilogue has these pages:
  - a cover
  - one page per log the reader heard, in log order (title, author, an excerpt of at most 200 characters)
  - a note on how many logs are still out there
  - the ending's closing pages (original text in the profile)
- **Page limit.** The epilogue stays within the 100-page book limit.
- **Reprinting.** `/ashfall ending epilogue` prints a fresh copy, because logs missed before the ending stay collectible and fill it in.

## After the ending

- **Breach events** continue unchanged.
- **Aftershock crash sites.** Every `aftershocks.every_days` after the ending, a new pass of every crash-site rule (ridge, wasteland) is planned. Each pass has its own salt (`Sites.Aftershocks.SALT_STEP`) and the scaled chance. It keeps 24 blocks from any placed site and uses the key `aftershock<N>:<rule>:<cx>,<cz>`. At most 24 passes are planned. Sites are placed lazily by the same placer and loot table (`chests/crash_site`, which holds crystals and rare blasters). Each new pass is announced once.
- **Gamerule `ashfall:objective`** (boolean, default true). When false, there is no vote when the Heart falls, no ending titles or broadcast, and no Aftershock news. The radio, logs, Grid HUD and grid keep working. An op can still use `/ashfall ending set`.
- **Replay: `/ashfall story reset`** (op, owned by the Director's `StoryCommand`).
  - It clears every story flag and runs `StoryHooks.fireReset`. Arms then cancels any vote, resets `StoryState`, sets the Black Start bonus to 0, rebuilds havens and fires `Ending.RESET`, where the finale rearms.
  - It keeps reclaimed districts, Relay Logs, rewards already given, boss lair records and the death counters. The replay is of the finale and the choice only, so a group can pick the other ending without re-running the whole arc.

## Commands

| Command | Who | |
|---|---|---|
| `/ashfall ending vote sever\|bind`, `confirm` | anyone | What the buttons run |
| `/ashfall ending status` | anyone | Ending, day, days since, participants, objective rule |
| `/ashfall ending epilogue` | anyone, after the ending | A fresh epilogue |
| `/ashfall ending begin` | op | Start the vote for everyone online |
| `/ashfall ending set sever\|bind` | op | Record an ending outright |
| `/ashfall story reset` | op | The replay reset above |

## Undead Nights cleanup

Undead Nights left the pack in 0.9.0. Its leftovers are gone:

- the `undeadnights:horde_zombie` population weights in 25 building profiles
- the `undeadnights:elite_zombie` drop in `MobDrops` (the cell carriers are now our brute and screamer, plus the mutant zombie)
- `undeadnights:demolition_zombie` in `#ashfall:bosses`
- the two `undeadnights` ids in `pack/overrides/config/zombies-break-build.toml`

## Tests

- **JUnit.** `EndingLogicTest` covers the vote tally, the Bind growth cap, the aftershock clock and the shipped profiles. `HordeStoryScalingTest`, `LairEndingGateTest` and `AftershockPlannerTest` cover the rest, and `LairRulesTest` counts only ungated story lairs.
- **Gametests (`EndingGameTest`).** These cover:
  - profiles and content load
  - the Sever profile (spawn x0.4, rarer horde nights, free relays, Antibiotics cure, books, advancement), then the reset undoing all of it
  - the Bind profile (flag, tier floor, unlocks, bunker bonus, the charged Resonant blade, growth stopping at tier VI after a year)
  - the vote for a party and a solo player, including objective off
  - epilogue and report assembly
