"""Run account-free two-client mixed-rendering tests against local 26.2 server artifacts."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import os
from pathlib import Path
from queue import Queue
import re
import shutil
import subprocess
import threading
import time
import zipfile
from pack_client import play, zip_inventory

SOURCE=Path(__file__).resolve().parent
PLATFORMS=("paper","spigot","folia")


def compile_probe(args):
    classes=args.output/"classes"; classes.mkdir()
    cp=(args.classpaths/"spigot-26.2/jars.txt").read_text().strip()
    cp=os.pathsep.join(map(str,[args.plugin,args.paper_api,*args.compile_lib,cp]))
    subprocess.run(["javac","-proc:none","-encoding","UTF-8","-cp",cp,"-d",str(classes),str(SOURCE/"PackLayerProbe.java")],check=True)
    jar=args.output/"PackLayerProbe.jar"
    with zipfile.ZipFile(jar,"w",zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("plugin.yml","name: PackLayerProbe\nversion: 1\nmain: PackLayerProbe\napi-version: '26.1'\nfolia-supported: true\ndepend: [3DCasino]\n")
        for path in classes.glob("*.class"): archive.write(path,path.name)
    return jar


def run(args, probe, expected, catalog, platform, early):
    port=26910+PLATFORMS.index(platform)
    folder=args.output/(platform+("-early" if early else "-normal")); plugins=folder/"plugins"; config=plugins/"3DCasino"
    config.mkdir(parents=True)
    shutil.copyfile(args.plugin,plugins/"3DCasino.jar"); shutil.copyfile(probe,plugins/probe.name)
    shutil.copyfile(args.eula,folder/"eula.txt")
    (folder/"server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
        "level-seed=17\ngenerate-structures=false\nallow-nether=false\nmax-tick-time=-1\n")
    (folder/"bukkit.yml").write_text("settings:\n  allow-end: false\n")
    (config/"config.yml").write_text("rendering:\n  mode: mixed\n  resource-pack:\n    source: local\n    local:\n      bind-address: '127.0.0.1'\n"
          f"      port: {port+10}\n      public-url: 'http://127.0.0.1:{port+10}'\n")
    if platform=="folia":
        (folder/"config").mkdir()
        (folder/"config/paper-global.yml").write_text("threaded-regions:\n  threads: 2\n")
    java=["java","-XX:ActiveProcessorCount=3","-Xms160M","-Xmx850M","-XX:TieredStopAtLevel=1","-Dterminal.jline=false","-Dterminal.ansi=false",
          "-DPaper.IgnoreJavaVersion=true","-DIReallyKnowWhatIAmDoingISwear=true","-Dfile.encoding=UTF-8"]
    if early: java.append("-Dpackprobe.early=true")
    if platform=="spigot":
        launch=["-cp",(args.classpaths/"spigot-26.2/jars.txt").read_text().strip(),"org.bukkit.craftbukkit.Main"]
    else:
        bundle=args.downloads/(platform+"-26.2.jar")
        with zipfile.ZipFile(bundle) as archive: fields=archive.read("META-INF/download-context").decode().split()
        cached=folder/"cache"/fields[-1]; cached.parent.mkdir(); os.link(args.downloads/"mojang_26.2.jar",cached)
        launch=["-jar",str(bundle)]
    log=folder/"pack-layer.log"; details={}; clients=[]; stop=threading.Event(); clicks=Queue()
    def connect(name):
        row=details[name]={}
        try: play(port,name,folder,expected,catalog,clicks,stop,row)
        except Exception as ex:
            if not stop.is_set(): row["error"]=repr(ex)
    with log.open("w",encoding="utf8") as out:
        server=subprocess.Popen([*java,*launch,"nogui"],cwd=folder,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True,creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        try:
            deadline=time.monotonic()+170; sent=0
            while server.poll() is None and time.monotonic()<deadline:
                data=log.read_text(encoding="utf8",errors="replace")
                if not clients and re.search(r"Done \([\d.]+s\)",data):
                    for name in ("PackFirst","PackSecond"):
                        thread=threading.Thread(target=connect,args=(name,),daemon=True); clients.append(thread); thread.start()
                ids=re.findall(r"PACK_LAYER_CLICK id=(\d+)",data)
                for i in ids[sent:]: clicks.put(int(i))
                sent=len(ids)
                if "PACK_LAYER_PASS" in data or "PACK_LAYER_FAIL" in data or "FAILED TO BIND" in data or any("error" in row for row in details.values()): break
                time.sleep(.1)
        finally:
            stop.set()
            for thread in clients: thread.join(3)
            if server.poll() is None:
                try: server.stdin.write("stop\n"); server.stdin.flush(); server.wait(40)
                except (OSError,subprocess.TimeoutExpired): server.kill(); server.wait()
    data=log.read_text(encoding="utf8",errors="replace")
    errors=[row for row in data.splitlines() if re.search(r"PACK_LAYER_FAIL|Cannot initialize Casino|Could not fully close|Machine command failed|Could not pass event|generated an exception|Thread failed main thread check|Thread context does not own",row)]
    ok=server.returncode==0 and "PACK_LAYER_PASS" in data and not errors and len(details)==2 and all(len(row.get("marks",{}))==7 and not row.get("error") for row in details.values())
    if ok:
        ok = ([r["accepted"] for r in details["PackFirst"]["requests"]]==[True]
              and [r["accepted"] for r in details["PackSecond"]["requests"]]==[False,True]
              and details["PackFirst"]["interactions"]==2 and details["PackFirst"]["pops"]>=1)
    early_observed = "PACK_LAYER_EARLY_DECLINE before_join_tick_19=true" in data
    if early: ok = ok and early_observed
    result={"early_toggle":early,"decline_event_before_join_tick_19":early_observed,"platform":platform,"version":"26.2","pass":ok,"server_exit":server.returncode,"errors":errors,"markers":[row.split("PACK_LAYER_SAMPLE ")[-1] for row in data.splitlines() if "PACK_LAYER_SAMPLE " in row],"clients":details}
    (folder/"receipt.json").write_text(json.dumps(result,indent=2),encoding="utf8")
    print(platform,ok,errors,[r.get("error") for r in details.values()],flush=True)
    return result


def main():
    ap=argparse.ArgumentParser()
    for arg in ("plugin","source-pack","output","classpaths","downloads","eula","paper-api"): ap.add_argument("--"+arg,required=True,type=lambda p:Path(p).resolve())
    ap.add_argument("--compile-lib",action="append",default=[],type=lambda p:Path(p).resolve())
    ap.add_argument("--timing",choices=("early","normal","both"),default="both")
    ap.add_argument("--jobs",type=int,default=1)
    ap.add_argument("platforms",nargs="+",choices=PLATFORMS)
    args=ap.parse_args()
    assert "eula=true" in args.eula.read_text().splitlines()
    args.output.mkdir(parents=True,exist_ok=False)
    probe=compile_probe(args)
    source=args.source_pack.read_bytes()
    with zipfile.ZipFile(args.plugin) as archive:
        embedded=archive.read("3DCasino-resource-pack.zip")
        catalog=json.loads(archive.read("vanilla-models/shared/cards/head_textures.json"))
    expected=zip_inventory(source)
    assert expected==zip_inventory(embedded),"candidate embedded zip differs from source build"
    def scenarios(platform):
        modes=(False, True) if args.timing=="both" else (args.timing=="early",)
        return [run(args,probe,expected,catalog,platform,early) for early in modes]
    with ThreadPoolExecutor(args.jobs) as pool:
        results=[row for group in pool.map(scenarios,args.platforms) for row in group]
    receipt={"plugin_sha256":hashlib.sha256(args.plugin.read_bytes()).hexdigest(),"source_pack_sha1":hashlib.sha1(source).hexdigest(),"embedded_pack_sha1":hashlib.sha1(embedded).hexdigest(),"source_files":len(expected),"servers":results}
    (args.output/"receipt.json").write_text(json.dumps(receipt,indent=2),encoding="utf8")
    return int(not all(r["pass"] for r in results))


if __name__=="__main__": raise SystemExit(main())
