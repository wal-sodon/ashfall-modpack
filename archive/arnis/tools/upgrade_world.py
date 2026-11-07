#!/usr/bin/env python3
"""Force-upgrade an Arnis world to the current game version with a vanilla dedicated server.

Copies <src-world> to <universe>/<name>, runs `server.jar --forceUpgrade --eraseCache --nogui`,
turns mob spawning back on (Arnis disables it) and sets Normal difficulty, then stops the server.
Vanilla only, so the upgrade is isolated from any mods.

Usage: python3 -I tools/upgrade_world.py <server.jar> <src-world> <universe-dir> <name>
"""
import shutil
import subprocess
import sys
from pathlib import Path


def main():
    server_jar, src, universe, name = Path(sys.argv[1]).resolve(), Path(sys.argv[2]), Path(sys.argv[3]).resolve(), sys.argv[4]
    target = universe / name
    if target.exists():
        sys.exit(f"{target} already exists; remove it first")
    universe.mkdir(parents=True, exist_ok=True)
    shutil.copytree(src, target)
    (universe / "eula.txt").write_text("eula=true\n")
    (universe / "server.properties").write_text(f"level-name={name}\nonline-mode=false\nspawn-protection=0\n")
    proc = subprocess.Popen(
        ["java", "-Xmx4G", "-jar", str(server_jar), "--forceUpgrade", "--eraseCache", "--nogui", "--world", name],
        cwd=universe, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    for line in proc.stdout:
        if "Unpacking" in line:
            continue
        print(line, end="", flush=True)
        if "Done (" in line and "For help" in line:
            # Arnis makes sightseeing worlds: peaceful, with mob spawning off. Ashfall needs zombies.
            for command in ("gamerule minecraft:spawn_mobs true", "gamerule minecraft:spawn_monsters true",
                            "difficulty normal", "gamerule minecraft:spawn_patrols false",
                            "gamerule minecraft:spawn_phantoms false", "gamerule minecraft:spawn_wandering_traders false",
                            "stop"):
                proc.stdin.write(command + "\n")
            proc.stdin.flush()
    sys.exit(proc.wait())


if __name__ == "__main__":
    main()
