# Spec: Admin GUIs, loot zones and mob drops (2026-10-26, plan M7, phase 6 track C)

Modules: **salvage** (Loot Zone screen, the shared admin gate and override pack) and **arms** (Mob Drops screen, data-driven mob drops). This answers the original request: "a GUI plus command for customizable loot tables (container type, radius, loot table, regen)" and "changeable mob drops". Every screen is a convenience on top of a command or a datapack file. Nothing the GUI does is impossible without it, and nothing it writes is hidden from a server owner.

## 1. Who may use it

- **One gate.** `salvage.admin.AdminAccess` is the only permission check: operator level 2, the same as `/lootzone` and `/mobdrops`.
- **The server decides.** The client hides the screens and keys from non-ops as a courtesy, with the overlay hint "Admin screens need operator permission (level 2)". The server still checks every C2S payload, because a modified client can send anything.
- **Refusals.** A refused payload gets a refusal message and no zone or rule data. The server log records the player's name and UUID and the action.
- **Clients without Ashfall.** `/lootzone gui` and `/mobdrops gui` tell the player to use the commands or the data files instead.

## 2. Loot Zone screen (salvage)

Open it with `/lootzone gui` or with the unbound **Loot Zones (op)** key in the **Ashfall: Admin** key category.

- **Layout.** The left side lists the zones. The right side is a form:
  - name;
  - centre X/Y/Z, with a **Here** button that also takes the admin's dimension;
  - radius (1-512);
  - container type;
  - loot table;
  - regen on/off;
  - refill delay;
  - quiet period;
  - no-player radius.
- **Container type** is a text box with a preset cycle button: any, chest, barrel, trapped chest, `#c:chests`, `#c:barrels`. It accepts `any`, one block id, or a block tag (`#ns:path`). The new `ContainerFilter` record stores the string exactly as typed. A zone that names a block from a removed mod still loads and simply stops matching. Old saves load unchanged. `LootZone`'s old constructor and `withContainerType(Optional)` are kept.
- **Loot table autocomplete.** The box autocompletes from the server's loot table ids, which are sent each time the screen opens. Tab or the arrow keys pick a suggestion. Ranking is done by `IdFilter`, which is common code and unit tested: path-prefix matches come first, then full-id prefix matches, then any substring match.
- **Buttons:**
  - Create/Save
  - Scan (claim loaded containers)
  - Refill now
  - Delete (asks for a second click)
  - Teleport
  - Export
  - Import paste
  - Import file
- **Same paths as the commands.** Each button runs exactly what the matching `/lootzone` subcommand runs (`LootZoneAdmin`), with the same container claiming and the same release on shrink, so the GUI and the commands can be mixed. The client sends text (`LootZoneForm`), and the server parses and validates it, so every error message comes from one place.

### Export and import

- The format is `{"zones": [<zone>, ...]}`, with the same fields as the saved data (`LootZoneTransfer`).
- **Export** writes `<world>/ashfall/loot_zones.json` and copies the same JSON to the admin's clipboard. If an export already exists, it is renamed with a timestamp first and never overwritten.
- **Import file** reads that fixed path. Using one fixed path means no command takes a file path a player could abuse.
- **Import paste** takes JSON from the clipboard. Payloads are capped at 30 000 characters, just under the serverbound packet limit, so a bigger set has to go through the file.
- **What import does.** It adds or replaces each zone by id and claims that zone's loaded containers.
  - A zone whose id the commands could not name is skipped.
  - A zone in a dimension the server lacks is kept, and applies once that dimension exists.

### New commands

`/lootzone gui`, `/lootzone export`, `/lootzone import`, `/lootzone edit <id> tag <block tag>`, `/lootzone edit <id> quiet <ticks>`.

## 3. Mob drops are data (arms)

- **The files.** The extra drops Ashfall adds to mobs moved out of `MobDrops`' Java table into datapack files, one per entity: `data/<ns>/ashfall/mob_drops/<entity ns>/<entity path>.json` (`MobDropRule`).

  ```json
  {"entity": "ashfall:brute", "drops": [{"item": "ashfall:energy_cell", "chance": 0.25, "killed_by_player": true, "min": 1, "max": 1}]}
  ```

- **Shipped rules.** Nine files ship: zombie, husk, the six special infected that drop something (brute, screamer, spitter, stalker, bloated, crawler) and Mutant Monsters' mutant zombie. The rates are the same as before. Undead Nights' elite zombie is gone, because that mod left the pack in 0.9.0.
- **Override by path.** Each file is named after its entity, so a datapack replaces Ashfall's rule for that entity just by using the same path. An empty `drops` list switches Ashfall's extra drops off for that entity.
- **How drops reach the loot tables.** They are still injected into the real loot tables (`LootTableEvents.MODIFY`):
  - as killed-by-player and random-chance pools;
  - with a set-count when the range is not 1-1;
  - with full charge for Energy Cells.

  A datapack that replaces an entity's whole loot table still wins.
- **Load order.** The game builds loot tables before any reload listener runs, so a reload listener would always be one reload behind. `LootReloadMixin` reads the rules at the head of `ReloadableServerRegistries.reload` instead.

## 4. Mob Drops screen (arms)

Open it with `/mobdrops gui` or with the unbound **Mob Drops (op)** key. `/mobdrops list` prints the rules in chat.

**From the console (phase 6).** A dedicated-server admin has no screen, so the same edits are commands: `/mobdrops add <entity> <item> <chance> [<min> <max> [<any_kill>]]` appends a drop to the entity's current rule, `remove <entity> <index>` drops a row (1-based, as `list` prints), `clear <entity>` saves an empty rule, and `reset <entity>` moves the override aside. They call the screen's own save and reset, so the checks, the file and the reload are identical; the success line is sent once the reload is done.

- **Layout.** The left side lists the entities that have rules. The right side shows the selected entity's drop rows: item, chance in percent, player-kill only, min and max count. There are at most 16 rows. Entity and item ids autocomplete.
- **Save** sends the whole list. The server checks every item id, writes the entity's rule into the world override datapack `world/datapacks/ashfall_overrides` (`OverridePack`), runs `/reload`, and only then answers. The screen therefore always shows rules that are really in effect.
- **Why a datapack and not a config file.** The edits travel with the world, can be read and hand-edited, win over the shipped files with the same id (a world pack is selected after the mod packs), and `/datapack disable "file/ashfall_overrides"` turns every GUI edit off at once.
- **Reset** moves the entity's override aside as `*.disabled` and reloads, which brings back the shipped rule. Nothing is ever deleted, so an admin's hand edits survive.

## 5. Tests

- **JUnit:**
  - `IdFilterTest` (ranking);
  - `MobDropRuleTest` (the shipped rules keep the old rates, the codec refuses impossible drops, file paths follow the entity id);
  - `LootZoneTransferTest` (export round trip, junk refused);
  - `RefillRuleTest` (extended for container filters).
- **Gametests:**
  - `LootZoneAdminGameTest`: non-ops are refused for every action; an op creates and edits a zone through the payload handler.
  - `MobDropsAdminGameTest`: an override is written, reloaded, and drops from a kill.
  - The existing `zombieDropsAreInjected` still passes.
- **Client shots** `m7-loot-zones` and `m7-mob-drops` are in `AdminScreensClientTest`. They have not been run, because the screen was locked.

## 6. Left for later

- A dimension picker instead of a text field.
- Editing the loot tables themselves (the GUI picks a table, it does not author one).
- Per-zone stats (last refill, players seen).
