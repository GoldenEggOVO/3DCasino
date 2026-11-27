"""Build and verify the plugin with its bundled default resource pack."""
import hashlib
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def build():
    if sys.version_info < (3, 12):
        raise RuntimeError("Python 3.12 or newer is required")
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    javac = shutil.which("javac")
    if javac:
        candidates.append(Path(javac).resolve().parent.parent)
    jdk = next((path for path in candidates
                if (path / "bin/javac.exe").is_file() and (path / "release").is_file()
                and re.search(r'^JAVA_VERSION="25(?:\.|\")', (path / "release").read_text(), re.MULTILINE)), None)
    if jdk is None:
        raise RuntimeError("JDK 25 is required; set JAVA_HOME or add its bin directory to PATH")
    environment = dict(os.environ, JAVA_HOME=str(jdk))
    bundled = next((parent / ".tools/apache-maven-3.9.11/bin/mvn.cmd"
                    for parent in ROOT.parents
                    if (parent / ".tools/apache-maven-3.9.11/bin/mvn.cmd").is_file()),
                   ROOT.parent / ".tools/apache-maven-3.9.11/bin/mvn.cmd")
    maven = str(bundled) if bundled.is_file() else shutil.which("mvn")
    if not maven:
        raise RuntimeError("Maven 3.9 or newer is required; add its bin directory to PATH")
    arguments = [maven, "-B", "-ntp"]
    cache = bundled.parents[2] / "m2"
    if bundled.is_file() and cache.is_dir():
        arguments.extend(["-o", "-Dmaven.repo.local=" + str(cache)])
    arguments.append("package")

    print("[1/3] Cleaning previous build...", flush=True)
    subprocess.run(arguments[:-1] + ["clean"], cwd=ROOT, env=environment, check=True)
    print("[2/3] Packaging resource pack...", flush=True)
    subprocess.run([sys.executable, "-X", "utf8", str(ROOT / "tools/package_resource_pack.py")],
                   cwd=ROOT, check=True)
    print("[3/3] Compiling and testing plugin...", flush=True)
    subprocess.run(arguments, cwd=ROOT, env=environment, check=True)

    version = ET.parse(ROOT / "pom.xml").getroot().find("{*}version").text
    plugin = ROOT / "target" / f"3DCasino-{version}.jar"
    pack = ROOT / "target/3DCasino-resource-pack.zip"
    checksum = hashlib.sha1(pack.read_bytes()).hexdigest()
    with zipfile.ZipFile(plugin) as archive:
        if archive.read("resource-pack.sha1").decode("ascii").strip() != checksum:
            raise RuntimeError("Plugin/resource-pack checksum mismatch")
        if hashlib.sha1(archive.read("3DCasino-resource-pack.zip")).hexdigest() != checksum:
            raise RuntimeError("Bundled resource pack differs from the packaged ZIP")
    print(f"\nBuild complete. Checksums match.\nPlugin: {plugin}\nResource pack: {pack}", flush=True)


if __name__ == "__main__":
    try:
        build()
    except (RuntimeError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile, KeyError) as error:
        print(f"\nBuild failed: {error}", file=sys.stderr)
        raise SystemExit(1)
