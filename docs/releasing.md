# Releasing a new version

## Automatic (merged pull requests)

Merging a pull request into `main` releases it (`.github/workflows/auto-version.yml`). Label the PR `major` or
`minor` to pick the bump; with neither it is a patch, and `no-release` skips it (docs-only changes, for example). The
workflow bumps `version=` in `gradle.properties`, the "Written for Ashfall X.Y.Z" line and adds a `docs/versions.md`
row from the PR title. `main` is protected (changes only through pull requests), so the bot pushes the bump to a
`release/vX.Y.Z` branch, opens a "Release vX.Y.Z" PR (labelled `no-release`), merges it itself, tags the merged
commit `vX.Y.Z` and builds the GitHub release. This needs the repository setting "Allow GitHub Actions to create and
approve pull requests" (Settings > Actions > General). Write the PR title as the release summary. Steps 4, 6 and 7
below (release repo, wiki, field manual page) are still done by hand. Pull before branching again, since the release
PR lands on `main` after the merge.

If a run fails half way, check for a leftover `release/vX.Y.Z` branch or PR and a `vX.Y.Z` tag that is not on
`main`, and delete them before re-running: the workflow refuses to reuse an existing tag.

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
5. **Source tag and GitHub release.** `main` only takes pull requests: open a PR with the version bump, label it
   `no-release` so the automatic bump skips it, merge it, then tag the merged commit:
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
