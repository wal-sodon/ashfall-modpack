# Spec: Mission briefing and objectives (2026-10-26)

Players asked for an in-game debriefing that says what the mission is and how to achieve it, and how to find their base
again. This layer (`arms.mission`, client `arms.client.mission`) only reads the grid, story, boss and finale state: it
explains the game and never changes how it plays.

## 1. First-join briefing

Once per player per world (`ashfall:mission` player attachment, `briefed`), a few seconds after login
(`briefing_delay_ticks`):

- a title, "ASHFALL" / "Asheville has gone dark";
- a chat debrief: the situation, the mission, the first step (the player's current objective), and the links
  `[Read the full briefing]` (`/ashfall mission briefing`, a dialog with every page) and `[Show objectives]`
  (`/ashfall mission`);
- the **Mission Briefing** book, in the inventory or at the player's feet. 21 pages: the situation, the mission, the
  district loop step by step, what reclaiming gives, getting stronger (salvage, energy weapons, chargers, the Bench,
  Plasma Blades, bosses), staying alive (water, infection, havens, hordes, Breaches) and the finale (the Uplink, the
  three phases, the vote).

Words are in `en_us.json` (`briefing.ashfall.*`); the numbers are read from the datapack when the book is made:
couplers, cells, hold time and radius, hum radius and siren warning from `grid/settings.json`, the darkness cut from
`reclaimed.darkness_scale`, crystals from `mission/settings.json`, Surge seconds per cell and breaker hold from
`finale/settings.json`. The book title and author are plain strings (a written book's title cannot be translated).
The Survivor's Notes stay in the radio cache as the in-world primer for sandbox worlds, where nobody is briefed.

## 2. Live objective

`MissionWorld` reads a `MissionFacts` snapshot; `MissionResolver` (pure, unit-tested) picks the objective.

| Order | Step | Objective (sub-steps) | Target |
|---|---|---|---|
| World events first | Ending chosen | broken core: re-repair n/N; running Breach: defend; else "Asheville is relit (ending)" | core |
| | Black Start complete | Choose Sever or Bind while the vote is open; with no vote (rule off, or cut short) say how an ending is still picked | hull |
| | Black Start running | Surge: feed cells; Feedback: hold both breakers; Heart: pylons n/4; paused: patch or feed | hull |
| Personal | Radio | Find the Field Radio | safehouse |
| | Arm | Craft a Spark Blaster and a Cell Pack (sticky once armed) | - |
| World | District | couplers n/N; cells n/N; hold P% (radius, relays m/M); link relays m/M; siege at nightfall; defend the core; BROKEN: re-repair n/N | core, or the nearest unlinked key relay |
| | Standby | Restart the Black Start at the socket (after a story reset) | hull |
| Party | Crystals | Resonance Crystals n/3 (bosses b/3); every boss down: crash sites | nearest boss lair still standing, else none |
| | Uplink | Craft the Grid Uplink (frame, banks n/2) | - |
| World | Black Start | Seat the Uplink in the Wreck hull | hull |

District focus: the unreclaimed district furthest along the loop; ties and an untouched grid go by the recommended
order (River Arts, South Slope, Downtown, Mission). Endgame items (crystals, frame, banks, Uplink) are counted across
every online player, and each is sticky at world level once seen held or crafted (`MissionState`,
`ashfall_mission_state`; cleared by a story reset), so a Frame in a chest or an offline carrier never sends the party
back to crystals the dead bosses can no longer drop. The haven (a Bunker Core) is an optional checklist row only, never
the current objective: as one it had no target and hid the way to River Arts from a player who skipped it.

Shown as:

- **HUD line** left of the hotbar above the Grid HUD: `Objective: <text> · 640 m W` (distance and heading worked out on
  the client every frame; outside the overworld it reads "in the overworld" instead, since every target is an
  overworld spot). Wraps upward to at most 4 lines. Toggle key "Toggle Objective Line", unbound by default,
  remembered in `config/ashfall-objective.txt`.
- **`/ashfall mission`**: the checklist with ✔ / ▶ / ○, the current step with its heading, its how-to line
  (`objective.ashfall.<key>.how`) and links.
- **Field Radio**: the full report gains the objective and "Home" lines; sneak + use gives just those two.

`ashfall:objective` (S2C): `prompts`, step, text key and arguments, target, Bunker Core. The server recomputes each
player every `sync_interval_ticks` (20) and sends only on change.

## 3. Base finding

The player's own or party Bunker Core (`BunkerCores.forPlayer`, overworld) rides in the same payload. The Grid Map
draws it as an orange "Bunker" marker and the objective's target as a blinking frame. The radio's "Home" line points
at the core, else the safehouse marker.

## 4. Game rule

`ashfall:objective false`: no briefing (the flag stays unset, so turning the rule on later still briefs once, within
a second, without a rejoin), and the
HUD line is hidden. `/ashfall mission`, the dialog, the book command, the radio lines and the Grid Map markers keep
working: they are tools the player asked for, not prompts.

## 5. Tests

- JUnit `MissionResolverTest` (12): every chain step and district sub-step, district focus, world events before
  personal steps, the sandbox, checklist marks.
- JUnit `BriefingPagesTest` (4): every page, rendered from `en_us.json` with the shipped numbers, wraps to at most 14
  lines at 114 px; every objective has its text and how-to; the Relight root mentions `/ashfall mission`; no borrowed
  franchise vocabulary.
- Gametests `MissionGameTest` (4): the book comes once (not on a rejoin); the objective follows a real `GridState`
  change; the game rule silences the briefing and HUD but not the command; the Bunker Core reaches the payload and the
  radio's home line.
- Client `MissionVisualClientTest` (not run yet; needs a free screen): screenshots `mission-briefing-title`,
  `mission-briefing-chat`, `mission-hud-line`, `mission-checklist`, `mission-briefing-dialog`, `mission-book-cover`,
  `mission-book-energize`, `mission-grid-map-bunker`, and a real-font check that every page fits.
