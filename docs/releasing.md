# Releasing a new version

## Automatic (merged pull requests)

Merging a pull request into `main` releases it (`.github/workflows/auto-version.yml`). Label the PR `major` or
`minor` to pick the bump; with neither it is a patch, and `no-release` skips it (docs-only changes, for example).
There is only the one PR: when you merge it, the bot

1. bumps `version=` in `gradle.properties` and the "Written for Ashfall X.Y.Z" line, and adds a `docs/versions.md`
   row from the PR title;
2. commits that straight to `main` as "Release vX.Y.Z (...)" and tags it `vX.Y.Z`;
3. the tag starts `.github/workflows/release.yml`, which builds the pack and creates the GitHub release.

The bot never merges or approves anything. `main` is protected (changes only through pull requests), so the bot
pushes with the repository secret `RELEASE_TOKEN`: a personal access token of a repository admin with Contents
read/write. The protection does not apply to admins ("Do not allow bypassing the above settings" stays off), and a
push made with that token, unlike the built-in one, starts other workflows. When the token expires, make a new one
and replace the secret (Settings > Secrets and variables > Actions).

Each merged PR is its own release. To release changes that are already on `main` without merging another PR, run
the workflow by hand (Actions > Auto version > Run workflow) with a bump and a summary line. Write the feature PR's
title as the release summary. Steps 4, 6 and 7 below (release repo, wiki, field manual page) are still done by hand.
Pull before branching again after a release.

If a run fails, read its log: the workflow refuses to reuse an existing `vX.Y.Z` tag, so delete a stray tag that is
not on `main` before running it again.

## By hand

Everything a release needs lives in the project:

- `tools/build_mrpack.py` builds the pack;
- `tools/gh_wiki.py` turns the field manual into GitHub wiki pages, with diagrams from `docs/wiki/figs/`
  (drawn by `tools/render_wiki_figs.py`);
- `.cache/release/repo` and `.cache/release/wiki` are clones of `wal-sodon/ashfall-modpack-release` and its wiki.

Commits are made with the global git identity, `walsodon-hash <263426854+walsodon-hash@users.noreply.github.com>`. GitHub
matches the noreply address by account id, so they show up under `wal-sodon`.

### Steps

1. **Version.** Set `version=X.Y.Z` in `gradle.properties`, the "Written for Ashfall X.Y.Z" line in
   `docs/wiki/ashfall-wiki.html`, and add a row to the Releases table in `docs/versions.md`. Ashfall uses semantic
   versioning (details in that file): bump **major** when existing worlds or saves break, **minor** for new
   features or a world-generation change, **patch** for fixes and balance tweaks. A bump resets the numbers after
   it (1.2.3 → 1.3.0 → 2.0.0).
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

   Keep the README's world compatibility up to date in the same commit:
   - **Does the release change world generation?** That means new chunks come out differently: the city generator,
     Lost Cities profiles or building placement, terrain or biomes. Things the salvage pass adds when a chunk is
     first visited (loot, street props, bicycles, relays, Strongboxes) are not world generation, because it re-runs
     on chunks visited under older versions.
   - **If it doesn't:** add the new version to the first row of the "World compatibility" table, give the new
     entry a `**World generation:** same as ...` line listing the rest of that row, and add the new version to the
     same line on every other release in that row.
   - **If it does:** add a new top row to the table naming the change, with only the new version in it, and give
     the new entry `**World generation:** new in X.Y.Z (...). Not the same as <previous versions>.` A new row
     starts a new group; later releases join it.
   - **If it removes a mod or something else an old world relies on,** add a note under the table like the
     10.3.0 one.
5. **Source tag and GitHub release.** `main` only takes pull requests (admins can push): open a PR with the version
   bump, label it `no-release` so the automatic bump skips it, merge it, then tag the merged commit:
   ```
   git switch main && git pull && git tag vX.Y.Z && git push origin vX.Y.Z
   ```
   The tag starts `.github/workflows/release.yml`. It checks the tag matches `gradle.properties`, builds the pack and
   creates the GitHub release with `Ashfall-X.Y.Z.mrpack` attached and the `docs/versions.md` row as notes. Rewrite
   the notes as Added / Changed / Removed / Fixed afterwards if needed. CI (`.github/workflows/ci.yml`) builds and
   runs the gametests on every push to `main` and every pull request.
6. **Wiki.**
   ```
   python3 -I tools/gh_wiki.py
   cp -R .cache/release/wikiout/. .cache/release/wiki/
   cd .cache/release/wiki && git pull && git add -A && git commit -m "Field manual for Ashfall X.Y.Z: ..."
   git -c http.postBuffer=524288000 push origin master
   ```
   The wiki's default branch is `master`.
7. **Field manual page.** Republish `docs/wiki/ashfall-wiki.html` to https://claude.ai/artifact/JpfqXRU3ZMTcpQDNZYGFHJ,
   adding any new images it references.

## If something is missing

- **Clones gone:** clone them again into `.cache/release/`:
  `git clone https://github.com/wal-sodon/ashfall-modpack-release.git repo` and
  `git clone https://github.com/wal-sodon/ashfall-modpack-release.wiki.git wiki`.
- **A diagram changed:** run `python3 -I tools/render_wiki_figs.py <section>-<n>` (e.g. `blasters-1`), or with no
  arguments to redo all of them. It screenshots the diagram from the field manual with headless Chrome into
  `docs/wiki/figs/`. Then do step 5 again.
