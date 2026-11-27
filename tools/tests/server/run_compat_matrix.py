"""Run the Casino probe on prepared localhost CraftBukkit/Spigot classpaths and Paperclip bundles."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
from queue import Queue
import shutil
import subprocess
import threading
import time
import zipfile

from matrix_client import play


TARGETS = [f"{platform}-{version}" for platform, versions in (
    ("paper", "26.1.1 26.1.2 26.2 26.3"), ("spigot", "26.1 26.1.1 26.1.2 26.2 26.3"),
    ("craftbukkit", "26.1 26.1.1 26.1.2 26.2 26.3"), ("purpur", "26.1.2 26.2 26.3"),
    ("folia", "26.1.2 26.2 26.3")) for version in versions.split()]


def sha256(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def compile_probe(args):
    classes = args.output / "probe-classes"
    classes.mkdir()
    dependencies = (args.classpaths / "spigot-26.2/jars.txt").read_text().strip()
    cp = os.pathsep.join([str(args.plugin), str(args.paper_api), dependencies, *map(str, args.compile_lib)])
    subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-cp", cp, "-d", str(classes),
                    str(Path(__file__).with_name("CasinoMatrixProbe.java")),
                    str(Path(__file__).with_name("CasinoMatrixDialogs.java"))], check=True)
    probe = args.output / "CasinoMatrixProbe.jar"
    with zipfile.ZipFile(probe, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("plugin.yml", "name: CasinoMatrixProbe\nversion: 1\nmain: CasinoMatrixProbe\n"
                         "api-version: '26.1'\nfolia-supported: true\ndepend: [3DCasino]\n")
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    return probe


def boot(folder, command, stage, port, version, use_client):
    log = folder / f"{stage}.log"
    with log.open("w", encoding="utf-8") as out:
        server = subprocess.Popen(command, cwd=folder, stdin=subprocess.PIPE, stdout=out,
                                  stderr=subprocess.STDOUT, text=True,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        stop = threading.Event()
        clicks = Queue()
        client = None
        details = {}
        sent = 0
        try:
            deadline = time.monotonic() + 200
            while time.monotonic() < deadline and server.poll() is None:
                data = log.read_text(encoding="utf-8", errors="replace")
                if use_client and client is None and re.search(r"Done \([\d.]+s\)", data):
                    def connect():
                        try:
                            details.update(play(version, port, clicks, stop))
                        except Exception as ex:
                            details["error"] = str(ex)
                    client = threading.Thread(target=connect, daemon=True)
                    client.start()
                ids = re.findall(r"CASINO_MATRIX_CLICK id=(\d+)", data)
                for target in ids[sent:]:
                    clicks.put(int(target))
                sent = len(ids)
                if re.search(r"CASINO_MATRIX_(PASS|FAIL)|Failed to load plugin|Could not load (?:plugin|'plugins)|"
                             r"Cannot initialize Casino|Error occurred while enabling|FAILED TO BIND", data):
                    break
                if details.get("error"):
                    break
                if not use_client and "3DCasino enabled." in data:
                    break
                time.sleep(.15)
        finally:
            stop.set()
            if client:
                client.join(5)
            if server.poll() is None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(40)
                except (OSError, subprocess.TimeoutExpired):
                    server.kill()
                    server.wait()
    data = log.read_text(encoding="utf-8", errors="replace")
    marks = [line.split("CASINO_MATRIX_", 1)[1] for line in data.splitlines() if "CASINO_MATRIX_" in line]
    errors = [line for line in data.splitlines() if re.search(
        r"Cannot initialize Casino|Could not fully close|Machine command failed|Could not pass event|"
        r"Could not load (?:plugin|'plugins)|Error occurred while (?:enabling|disabling)|"
        r"Thread failed main thread check|Thread context does not own|generated an exception|CASINO_MATRIX_FAIL", line)]
    wire_ok = (details.get("joined") and details.get("chunks") and details.get("interactions") == sent
               and details.get("dialog_clicks") == 1 and any(m.startswith("DIALOG_PASS ") for m in marks))
    return {"pass": bool(server.returncode == 0 and any(m.startswith("PASS ") for m in marks) and not errors and wire_ok),
            "exit": server.returncode, "wire": details, "markers": marks, "errors": errors, "log": str(log)}


def run_target(args, probe, target):
    folder = args.output / target
    plugins = folder / "plugins"
    plugins.mkdir(parents=True)
    shutil.copyfile(args.plugin, plugins / "3DCasino.jar")
    if probe:
        shutil.copyfile(probe, plugins / probe.name)
    port = 26880 + TARGETS.index(target)
    (folder / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
        "level-seed=17\ngenerate-structures=false\nallow-nether=false\nmax-tick-time=-1\n")
    (folder / "bukkit.yml").write_text("settings:\n  allow-end: false\n")
    # Newer servers default to a whitelist. Admit the fixture's one offline test profile.
    (folder / "whitelist.json").write_text(json.dumps([
        {"uuid": "59598afe-3dff-354b-a1ea-a989e2e98155", "name": "CasinoMatrix"}]))
    shutil.copyfile(args.eula, folder / "eula.txt")
    platform, version = target.split("-")
    if platform == "folia":
        config = folder / "config"
        config.mkdir()
        (config / "paper-global.yml").write_text("threaded-regions:\n  threads: 2\n")
    java = ["java", "-XX:ActiveProcessorCount=4", "-Xms192M", "-Xmx1100M", "-XX:TieredStopAtLevel=1",
            "-Dterminal.jline=false", "-Dterminal.ansi=false", "-DPaper.IgnoreJavaVersion=true",
            "-DIReallyKnowWhatIAmDoingISwear=true", "-Dpaper.disablePluginRemapping=true",
            "-Dpaper.disableOldApiSupport=true", "-Dfile.encoding=UTF-8"]
    if platform in ("spigot", "craftbukkit"):
        cp = (args.classpaths / target / "jars.txt").read_text().strip()
        server_artifact = next(Path(jar) for jar in cp.split(os.pathsep)
                               if Path(jar).name.startswith(platform + "-" + version + "-"))
        launch = ["-cp", cp, "org.bukkit.craftbukkit.Main"]
    else:
        bundle = args.downloads / f"{target}.jar"
        server_artifact = bundle
        with zipfile.ZipFile(bundle) as archive:
            fields = archive.read("META-INF/download-context").decode().split()
        destination = folder / "cache" / fields[-1]
        destination.parent.mkdir()
        vanilla = args.downloads / f"mojang_{version}.jar"
        try:
            os.link(vanilla, destination)
        except OSError:
            shutil.copyfile(vanilla, destination)
        launch = ["-jar", str(bundle)]
    checks = {}
    for stage in ("create", "restore"):
        checks[stage] = boot(folder, [*java, f"-Dcasino.matrix.stage={stage}", *launch, "nogui"],
                             stage, port, version, probe is not None)
        if not checks[stage]["pass"]:
            break
    result = {"target": target, "server_jar_sha256": sha256(server_artifact),
              "pass": all(s in checks and checks[s]["pass"] for s in ("create", "restore")),
              "boots": checks}
    (folder / "receipt.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(target, result["pass"], checks[stage]["markers"][-1:], flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plugin", type=Path, required=True, help="Shaded Casino JAR under test")
    parser.add_argument("--classpaths", type=Path, required=True,
                        help="Directory of <platform>-<version>/jars.txt files with absolute Maven classpaths")
    parser.add_argument("--downloads", type=Path, required=True,
                        help="Directory containing <platform>-<version>.jar bundles and mojang_<version>.jar")
    parser.add_argument("--eula", type=Path, required=True, help="Accepted eula.txt from a local fixture")
    parser.add_argument("--output", type=Path, required=True, help="Fresh output location for logs and worlds")
    parser.add_argument("--paper-api", type=Path, required=True, help="Local Paper API used to compile the probe")
    parser.add_argument("--compile-lib", action="append", default=[], type=Path,
                        help="Extra compile-time classpath JAR; repeat for additional dependencies")
    parser.add_argument("--probe-jar", type=Path, help="Use a precompiled probe with the same binary API")
    parser.add_argument("--startup-red", action="store_true", help="Check startup failures with an old plugin before probe compilation")
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("targets", choices=TARGETS, nargs="*")
    args = parser.parse_args()
    if "eula=true" not in args.eula.read_text().splitlines():
        parser.error("Supply the accepted EULA of an existing local fixture")
    args.output = args.output.resolve()
    args.downloads = args.downloads.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    probe = None if args.startup_red else args.probe_jar or compile_probe(args)
    with ThreadPoolExecutor(args.jobs) as pool:
        rows = list(pool.map(lambda t: run_target(args, probe, t), args.targets or TARGETS))
    receipt = {"plugin_sha256": sha256(args.plugin), "probe_sha256": sha256(probe) if probe else None,
               "servers": rows, "client_visual_test": False}
    (args.output / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    return 0 if all(row["pass"] for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
