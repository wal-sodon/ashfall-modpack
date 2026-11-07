# Releasing a new version

Everything a release needs lives in the project:

- `tools/build_mrpack.py` builds the pack;
- `tools/gh_wiki.py` turns the field manual into GitHub wiki pages, with diagrams from `docs/wiki/figs/`
  (drawn by `tools/render_wiki_figs.py`);
- `.cache/release/repo` and `.cache/release/wiki` are clones of `wal-sodon/ashfall-modpack-release` and its wiki.

Commits are made with the global git identity, `walsodon-hash <263426854+walsodon-hash@users.noreply.github.com>`. GitHub
matches the noreply address by account id, so they show up under `wal-sodon`.

## Steps

1. **Version.** Set `version=X.Y.Z` in `gradle.properties`, the "Written for Ashfall X.Y.Z" line in
   `docs/wiki/ashfall-wiki.html`, and add a row to `docs/versions.md`. The scheme is in that file: medium for new
   features or a world-generation change, minor for fixes and balance tweaks.
2. **Test.** Run `./gradlew build`, then `:salvage:runGameTest`, `:director:runGameTest` and `:arms:runGameTest`, one at
   a time. Write the output to log files.
3. **Pack.** Run `python3 -I tools/build_mrpack.py`. It writes `dist/Ashfall-X.Y.Z.mrpack`.
4. **Release repo.**
   ```
   cd .cache/release/repo && git pull
   cp ../../../dist/Ashfall-X.Y.Z.mrpack .
   # add a "### X.Y.Z: ..." entry at the top of the Releases list in README.md (Added / Changed / Removed / Fixed)
   git add README.md Ashfall-X.Y.Z.mrpack && git commit -m "Add Ashfall X.Y.Z modpack: ..."
   git -c http.postBuffer=524288000 push
   ```
   The larger post buffer is needed: a 2 MB push fails with HTTP 400 without it.
5. **Wiki.**
   ```
   python3 -I tools/gh_wiki.py
   cp -R .cache/release/wikiout/. .cache/release/wiki/
   cd .cache/release/wiki && git pull && git add -A && git commit -m "Field manual for Ashfall X.Y.Z: ..."
   git -c http.postBuffer=524288000 push origin master
   ```
   The wiki's default branch is `master`.
6. **Field manual page.** Republish `docs/wiki/ashfall-wiki.html` to https://claude.ai/artifact/JpfqXRU3ZMTcpQDNZYGFHJ,
   adding any new images it references.

## If something is missing

- **Clones gone:** clone them again into `.cache/release/`:
  `git clone https://github.com/wal-sodon/ashfall-modpack-release.git repo` and
  `git clone https://github.com/wal-sodon/ashfall-modpack-release.wiki.git wiki`.
- **A diagram changed:** run `python3 -I tools/render_wiki_figs.py <section>-<n>` (e.g. `blasters-1`), or with no
  arguments to redo all of them. It screenshots the diagram from the field manual with headless Chrome into
  `docs/wiki/figs/`. Then do step 5 again.
