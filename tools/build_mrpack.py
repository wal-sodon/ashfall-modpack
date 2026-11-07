#!/usr/bin/env python3
"""Build the Ashfall .mrpack from pack/mods.json without packwiz.

- Resolves every pinned Modrinth version, checks it targets the pack's Minecraft version and Fabric,
  and fails if any *required* dependency is missing from the manifest.
- Writes modrinth.index.json (download URLs + hashes, so ARR mods are never redistributed).
- Adds pack/overrides/** and our own jars (modules/*/build/libs) as overrides.
- Optionally downloads the server-side jars into a mods folder for a test server (--server-mods DIR),
  verifying each file's sha512.

Usage: python3 -I tools/build_mrpack.py [--version 9.10.0] [--server-mods DIR] [--check-only]
"""
import argparse
import hashlib
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com/v2"
USER_AGENT = "toesinthesand/ashfall-pack-builder (agent@toesinthesand.xyz)"


def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def download(url, dest, sha512):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=300) as response:
        data = response.read()
    if hashlib.sha512(data).hexdigest() != sha512:
        raise SystemExit(f"sha512 mismatch for {url}")
    dest.write_bytes(data)


def resolve(manifest):
    mods = manifest["mods"]
    versions = {}
    for mod in mods:
        version = get_json(f"{API}/version/{mod['version']}")
        if manifest["minecraft"] not in version["game_versions"] or "fabric" not in version["loaders"]:
            raise SystemExit(f"{mod['slug']} {version['version_number']} does not target "
                             f"{manifest['minecraft']} Fabric ({version['game_versions']}, {version['loaders']})")
        versions[mod["slug"]] = version
    project_ids = {v["project_id"]: slug for slug, v in versions.items()}

    problems = []
    for slug, version in versions.items():
        for dep in version["dependencies"]:
            if dep["dependency_type"] != "required":
                continue
            if dep.get("project_id") and dep["project_id"] not in project_ids:
                name = get_json(f"{API}/project/{dep['project_id']}")["slug"]
                problems.append(f"{slug} requires {name} ({dep['project_id']}), which is not in pack/mods.json")
    if problems:
        raise SystemExit("\n".join(problems))
    return versions


def primary_file(version):
    files = version["files"]
    return next((f for f in files if f.get("primary")), files[0])


def env_for(mod):
    support = "optional" if mod.get("optional") else "required"
    client = "unsupported" if mod["side"] == "server" else support
    server = "unsupported" if mod["side"] == "client" else support
    return {"client": client, "server": server}


def gradle_version():
    """The mod version from gradle.properties (`version=`), which names the jars in build/libs."""
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if line.startswith("version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gradle.properties has no version=")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", default=None, help="pack version (default: gradle.properties version)")
    parser.add_argument("--server-mods", type=Path)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    if args.version is None:
        args.version = gradle_version()

    manifest = json.loads((ROOT / "pack" / "mods.json").read_text())
    versions = resolve(manifest)
    for mod in manifest["mods"]:
        v = versions[mod["slug"]]
        print(f"  {mod['slug']:28} {v['version_number']:36} {v['version_type']}")
    print(f"OK: {len(versions)} mods target {manifest['minecraft']} Fabric with all required dependencies present")
    if args.check_only:
        return

    files = []
    for mod in manifest["mods"]:
        f = primary_file(versions[mod["slug"]])
        files.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": env_for(mod),
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": args.version,
        "name": manifest["name"],
        "summary": "Zombie apocalypse in a generated, ruined Asheville, with energy weapons.",
        "files": files,
        "dependencies": {"minecraft": manifest["minecraft"], "fabric-loader": manifest["fabric_loader"]},
    }

    # Only the jars of the version gradle.properties builds now: a version bump leaves the previous build's jars in
    # build/libs, and packing both would load two copies of each Ashfall mod.
    mod_version = gradle_version()
    own_jars = sorted(p for p in ROOT.glob(f"modules/*/build/libs/ashfall-*-{mod_version}.jar"))
    if len(own_jars) != 3:
        raise SystemExit(f"Expected 3 Ashfall {mod_version} jars, found {[p.name for p in own_jars]}; "
                         "run ./gradlew build first")

    out_dir = ROOT / "dist"
    out_dir.mkdir(exist_ok=True)
    out = out_dir / f"Ashfall-{args.version}.mrpack"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("modrinth.index.json", json.dumps(index, indent=2))
        overrides = ROOT / "pack" / "overrides"
        if overrides.exists():
            for path in sorted(overrides.rglob("*")):
                if path.is_file():
                    zf.write(path, f"overrides/{path.relative_to(overrides)}")
        for jar in own_jars:
            zf.write(jar, f"overrides/mods/{jar.name}")
    print(f"Wrote {out} ({len(files)} Modrinth mods + {len(own_jars)} Ashfall jars)")

    if args.server_mods:
        args.server_mods.mkdir(parents=True, exist_ok=True)
        for mod, entry in zip(manifest["mods"], files):
            if entry["env"]["server"] == "unsupported":
                continue
            dest = args.server_mods / Path(entry["path"]).name
            if not dest.exists():
                download(entry["downloads"][0], dest, entry["hashes"]["sha512"])
        for jar in own_jars:
            (args.server_mods / jar.name).write_bytes(jar.read_bytes())
        print(f"Server mods in {args.server_mods}")


if __name__ == "__main__":
    sys.exit(main())
