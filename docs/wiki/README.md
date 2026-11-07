# Ashfall Field Manual (player wiki)

- Live page: https://claude.ai/artifact/JpfqXRU3ZMTcpQDNZYGFHJ (private until shared from the page's Share menu)
- Source: `ashfall-wiki.html` in this folder. It is plain HTML: each section is a `<section id="...">` block with
  ordinary tables and lists, so a fact is changed by editing its text in place.

## Updating it
- Ask Claude: "update the Ashfall wiki: <change>". Claude edits this file and republishes it to the same link
  (Artifact publish with `url` set to the live page above), so the link never changes.
- By hand: edit the file, then ask Claude to republish it.
- Add a section: copy an existing `<section>` block, give it a new `id`, and add a matching
  `<li><a href="#id">` line to the `nav.index` list at the top. Search and the section highlight pick it up
  automatically.
- When recipes, loot or stats change in the mod, update the matching table here too (recipes: `data/ashfall/recipe`,
  blaster stats: `data/ashfall/ashfall/energy_gun`, landmarks: `lostcities/predefinedcities`).

## Item images
- Icons are the mod's real 16×16 item textures, shown at 32×32 with pixelated scaling. They are published as separate
  files (`img/<id>.png`) next to the page, not embedded, so the HTML stays readable.
- `images.json` maps each published `img/...png` path to its texture in `modules/*/src/main/resources/assets`. Pass it
  as the Artifact `files` map when republishing. After regenerating textures (`tools/gen_textures.py`), republish
  and the wiki picks up the new art.
- Add an icon: put `<img class="tex" src="img/<id>.png" alt="" width="32" height="32">` before the item's name and
  add the `img/<id>.png` entry to `images.json`.

## All recipes (generated)
The "All recipes" section is written by `python3 -I tools/wiki_recipes.py` from the mod's recipe JSON. Run it after
any recipe change, then republish. Edit the section's text in `tools/wiki_recipes.py`, not in the HTML between the
`<!-- recipes:start -->` and `<!-- recipes:end -->` markers, which is overwritten.

## Pages (from stage 1 of the full wiki, 2026-10-29)
The manual is paged: each `<section id="...">` inside `<main>` is one page, shown one at a time, in the order of the
sidebar links. The sidebar has group labels (`li.group`) and "coming soon" entries (`span.soon`); to add a page, write
its section, then replace its `soon` entry with `<li><a href="#id">Title</a></li>` in the right group. Every page
has its own link: the artifact link plus `#id`.

Planned order (stages): starter pages (done) → reclaiming districts, bosses and crystals → attachments and Plasma
Blades → the Black Start and endings → co-op and server admin.
