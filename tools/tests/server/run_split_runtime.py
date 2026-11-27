"""Boot a clean localhost Purpur server three times to test create and restore."""

import socket
import json
import hashlib
import argparse
import os
import xml.etree.ElementTree as ET
import shutil
import subprocess
import time
import zipfile
from pathlib import Path

casino_project = Path(__file__).resolve().parents[3]
workspace = casino_project.parent
boards_project = workspace / ".worktrees/3dgames-classic-games"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--server-dir", type=Path, required=True,
                    help="Prepared Purpur 26.2 cache with purpur-2622.jar, libraries and versions")
parser.add_argument("--maven-repo", type=Path, default=Path.home() / ".m2/repository")
parser.add_argument("--rooms-snapshot", type=Path, help="Synthetic all-bot snapshot to restore on boots 2 and 3; remaps its anchors to the fresh fixture world")
parser.add_argument("--craftengine-jar", type=Path, help="Optional local CE binary; never bundled")
parser.add_argument("--craftengine-cache", type=Path, help="Existing CE dependency cache (libs directory)")
parser.add_argument("--mode", choices=("casino-only", "tabletop-only", "both"), default="casino-only")
parser.add_argument("--boards-project", type=Path, default=boards_project)
args = parser.parse_args()
boards_project = args.boards_project.resolve()
project = boards_project if args.mode == "tabletop-only" else casino_project
casino = args.mode != "tabletop-only"
plugin_name = "3DCasino" if casino else "3DGames"
probe_name = "CasinoStandaloneProbe" if casino else "BoardsStandaloneProbe"
probe_package = "GoldenEggOVO.TDCasino.probe" if casino else "GoldenEggOVO.TDGames.Tabletop.probe"
if casino and args.rooms_snapshot: parser.error("--rooms-snapshot requires tabletop-only")
with socket.socket() as port_guard:
    port_guard.bind(("127.0.0.1", 25617))
source = args.server_dir
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
runtime = project / "target" / f"standalone-smoke-{time.strftime('%Y%m%d-%H%M%S')}"
runtime.mkdir(parents=True)
shutil.copy2(source / "purpur-2622.jar", runtime / "purpur-2622.jar")
for name in ("libraries", "versions", "cache"):
    if (source / name).is_dir():
        shutil.copytree(source / name, runtime / name)
if "eula=true" not in (source / "eula.txt").read_text(encoding="utf-8"):
    raise RuntimeError("Source fixture must already have an accepted EULA")
shutil.copy2(source / "eula.txt", runtime / "eula.txt")
(runtime / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25617\nlevel-name=boards_smoke\n"
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
    "online-mode=false\nview-distance=2\nsimulation-distance=2\n"
    "spawn-protection=0\nenable-rcon=false\nenable-query=false\n", encoding="utf-8")
plugins = runtime / "plugins"
plugins.mkdir()
jar = project / "target" / f"{plugin_name}-{version}.jar"
with zipfile.ZipFile(jar) as artifact:
    assert f"version: {version}" in artifact.read("plugin.yml").decode().splitlines(), "Plugin version differs from build version"
shutil.copy2(jar, plugins / jar.name)
if args.craftengine_jar:
    shutil.copy2(args.craftengine_jar, plugins / "CraftEngine.jar")
    ce = plugins / "CraftEngine"
    ce.mkdir()
    if args.craftengine_cache:
        shutil.copytree(args.craftengine_cache / "libs", ce / "libs")
    (ce / "config.yml").write_text("metrics: false\nupdate-checker: false\nresource-pack:\n  merge-external-folders: []\n  delivery:\n    send-on-join: false\n    auto-upload: false\n    resend-on-upload: false\n    hosting: []\n", encoding="utf-8")

if args.mode == "both":
    peer_jar = boards_project / "target" / f"3DGames-{version}.jar"
    shutil.copy2(peer_jar, plugins / peer_jar.name)

tested_digest = hashlib.sha256((plugins / jar.name).read_bytes()).hexdigest()
paper_api = args.maven_repo / "io/papermc/paper/paper-api/26.2.build.111-stable/paper-api-26.2.build.111-stable.jar"
kyori = args.maven_repo / "net/kyori"
classpath = os.pathsep.join(map(str, (jar, paper_api,
    args.maven_repo / "org/joml/joml/1.10.8/joml-1.10.8.jar",
    kyori / "adventure-key/5.2.0/adventure-key-5.2.0.jar",
    kyori / "adventure-api/5.2.0/adventure-api-5.2.0.jar",
    args.maven_repo / "org/jetbrains/annotations/26.1.0/annotations-26.1.0.jar",
    args.maven_repo / "com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar")))
classes = runtime / "probe-classes"
classes.mkdir()
def compile_probe(root, name, package, dependency):
    output = runtime / (name + "-classes")
    output.mkdir()
    cp = classpath + os.pathsep + str(dependency)
    subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-cp", cp,
                    "-d", str(output), str(root / "tools/tests/server" / (name + ".java"))], check=True)
    with zipfile.ZipFile(plugins / (name + ".jar"), "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("plugin.yml", f"name: {name}\nversion: 1\nmain: {package}.{name}\napi-version: '26.2'\ndepend: [{'3DCasino' if name == 'CasinoStandaloneProbe' else '3DGames'}]\n")
        for path in output.rglob("*.class"):
            archive.write(path, path.relative_to(output).as_posix())
compile_probe(project, probe_name, probe_package, jar)
if args.mode == "both":
    compile_probe(boards_project, "BoardsStandaloneProbe", "GoldenEggOVO.TDGames.Tabletop.probe", peer_jar)

boots = []
snapshot_digest = None
prefix = "CASINO" if casino else "BOARDS"
boot_steps = tuple((number, f"{prefix}_STANDALONE_CREATE_PASS" if number == 1 else f"{prefix}_STANDALONE_RESTORE_PASS") for number in (1, 2, 3))
for number, marker in boot_steps:
    if args.rooms_snapshot and number == 2:
        snapshot_bytes = args.rooms_snapshot.read_bytes()
        snapshot_digest = hashlib.sha256(snapshot_bytes).hexdigest()
        snapshot = json.loads(snapshot_bytes)
        current_rooms = plugins / "3DGames/rooms.json"
        fixture_world = json.loads(current_rooms.read_text(encoding="utf-8"))["rooms"][0]["anchorWorld"]
        if not 1 <= len(snapshot["rooms"]) <= 32 or snapshot.get("returns"):
            raise ValueError("Expected 1-32 synthetic rooms and no player return locations")
        for room in snapshot["rooms"]:
            if not room["seats"] or not all(seat["bot"] for seat in room["seats"]):
                raise ValueError("Only synthetic all-bot snapshots are accepted")
            room["anchorWorld"] = fixture_world
        snapshot_text = json.dumps(snapshot, ensure_ascii=False, indent=2)
        current_rooms.write_text(snapshot_text, encoding="utf-8")
        (runtime / "multi-room-snapshot.json").write_text(snapshot_text, encoding="utf-8")
    snapshot_mode = bool(args.rooms_snapshot and number > 1)
    if snapshot_mode:
        marker = "BOARDS_SNAPSHOT_RESTORE_PASS"
    boot_stdout = runtime / f"boot-{number}.stdout.log"
    with boot_stdout.open("w", encoding="utf-8") as output:
        server = subprocess.Popen([shutil.which("java"), "-Xms512M", "-Xmx2G", "-XX:TieredStopAtLevel=1",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                   "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                                   f"-Dsplit.probe.boot={number}",
                                   f"-Dsplit.probe.both={str(args.mode == 'both').lower()}",
                                   f"-Dboards.probe.snapshot={str(snapshot_mode).lower()}",
                                   f"-Dboards.probe.craftengine={str(bool(args.craftengine_jar)).lower()}",
                                   "-jar", "purpur-2622.jar", "nogui"],
                                  cwd=runtime, stdin=subprocess.PIPE, stdout=output,
                                  stderr=subprocess.STDOUT, text=True,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        found = False
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline and server.poll() is None:
                # Each boot owns a fresh file; latest.log can still contain the prior boot's marker.
                log = boot_stdout.read_text(encoding="utf-8", errors="replace")
                peer_marker = "BOARDS_STANDALONE_CREATE_PASS" if number == 1 else "BOARDS_STANDALONE_RESTORE_PASS"
                if marker in log and (args.mode != "both" or peer_marker in log and "CASINO_COINSTALL_PASS" in log):
                    found = True
                    break
                if "BOARDS_STANDALONE_PROBE_FAIL" in log or "CASINO_STANDALONE_PROBE_FAIL" in log:
                    break
                time.sleep(1)
        finally:
            if server.poll() is None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(timeout=40)
                except (subprocess.TimeoutExpired, OSError):
                    server.kill()
                    server.wait()
        log = (runtime / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
        (runtime / f"boot-{number}.log").write_text(log, encoding="utf-8")
        ce_errors = [line for line in log.splitlines() if "ERROR]" in line and "[CraftEngine]" in line]
        classic_native = casino or "BOARDS_CLASSICS_NATIVE_PASS games=13" in log
        classic_packed = "BOARDS_CLASSICS_PACKED_PASS games=13" in log
        boots.append({"boot": number, "pass": found and server.returncode == 0 and classic_native and not ce_errors
                      and (casino or not args.craftengine_jar or "BOARDS_CRAFTENGINE_PASS" in log and classic_packed)
                      and (not casino or not args.craftengine_jar or "CASINO_CRAFTENGINE_PASS" in log),
                      "exit_code": server.returncode, "craftengine_errors": ce_errors,
                      "classic_native": classic_native, "classic_packed": classic_packed,
                      "casino_ce_models": "CASINO_CRAFTENGINE_PASS" in log,
                      "markers": [line for line in log.splitlines()
                                  if "BOARDS_" in line or "CASINO_" in line or "Error occurred while enabling" in line]})
        if not boots[-1]["pass"]:
            break

receipt = {"version": version, "jar_sha256": tested_digest, "runtime": str(runtime), "pass": len(boots) == len(boot_steps) and all(boot["pass"] for boot in boots),
           "craftengine": bool(args.craftengine_jar), "mode": args.mode, "classic_games": 13 if not casino else None, "casino_machines": 12 if casino else None, "boots": boots, "client_visual_test": False, "production_deployed": False}
receipt["installed_artifacts_sha256"] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in plugins.glob("*.jar")}
if snapshot_digest:
    receipt["snapshot_source_sha256"] = snapshot_digest
    receipt["snapshot_rooms"] = len(snapshot["rooms"])
    receipt["snapshot_world_remapped_for_fixture"] = True
(runtime / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(receipt, ensure_ascii=False, indent=2))
raise SystemExit(0 if receipt["pass"] else 1)
