"""Read actual add/remove/metadata traffic and mixed resource-pack negotiation on 26.2."""
import base64
import json
from collections import Counter
import hashlib
import io
import re
import select
import socket
import struct
import sys
import urllib.request
import uuid
import zipfile
import zlib
from pathlib import Path
from queue import Empty
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from matrix_client import read_int, string, varint


def read_string(frame):
    return frame.read(read_int(frame)).decode()


def zip_inventory(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = [name for name in archive.namelist() if not name.endswith("/")]
        assert len(set(entries)) == len(entries)
        return {name: hashlib.sha256(archive.read(name)).digest() for name in entries}


def texture_id(encoded):
    try:
        skin=json.loads(base64.b64decode(encoded,validate=True))["textures"]["SKIN"]["url"]
        return skin.split("/")[-1]
    except (ValueError, KeyError, UnicodeDecodeError, TypeError): return None


def play(port, name, folder, expected, catalog, commands, stop, report):
    first = name == "PackFirst"
    state, compression = "login", False
    head_textures = {}
    player = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3)
    known, metadata, removed, prior = set(), {}, set(), set()
    report.update(marks={}, requests=[], interactions=0, pops=0)
    with socket.create_connection(("127.0.0.1", port), 10) as sock, sock.makefile("rb", buffering=0) as stream:
        def send(packet, data=b""):
            frame = varint(packet) + data
            if compression: frame = b"\0" + frame
            sock.sendall(varint(len(frame)) + frame)
        send(0, varint(776) + string("127.0.0.1") + struct.pack(">H", port) + varint(2))
        send(0, string(name) + player.bytes)
        while not stop.is_set():
            if state == "play" and first:
                try: target = commands.get_nowait()
                except Empty: pass
                else:
                    assert target in known, f"clicked target {target} missing from wire"
                    send(26, varint(target) + b"\0\0\0")
                    report["interactions"] += 1
            if not select.select([sock], [], [], .1)[0]: continue
            size = read_int(stream)
            if not 0 < size < 8*1024*1024: raise ValueError("Unexpected packet length")
            data = bytearray()
            while len(data)<size:
                piece = stream.read(size-len(data))
                if not piece: raise EOFError("Truncated packet")
                data.extend(piece)
            frame = io.BytesIO(data)
            if compression and read_int(frame): frame = io.BytesIO(zlib.decompress(frame.read()))
            packet = read_int(frame)
            if state == "login":
                if packet == 0: raise RuntimeError(str(frame.read()))
                if packet == 3: compression = read_int(frame) > 0
                if packet == 2:
                    send(3); state = "config"
                    send(0, string("en_us") + b"\x02\x00\x01\x7f\x01\x00\x00\x00")
            elif state == "config":
                if packet == 2: raise RuntimeError(str(frame.read()))
                if packet == 14: send(7, b"\0")
                elif packet == 3: send(3); state = "play"
                elif packet in (4,5): send(packet, frame.read())
            elif packet == 32: raise RuntimeError(str(frame.read()))
            elif packet == 44: send(28,frame.read())
            elif packet == 61: send(45,frame.read())
            elif packet == 72:
                send(0,varint(read_int(frame)))
                if not report.get("joined"): send(44); report["joined"] = True
            elif packet == 11: send(11,struct.pack(">f",8))
            elif packet == 81:
                ident, url, sha1 = frame.read(16),read_string(frame),read_string(frame)
                accept = first or len(report["requests"]) > 0
                report["requests"].append({"url":url,"sha1":sha1,"accepted":accept})
                if not accept: send(49,ident+varint(1)); continue
                send(49,ident+varint(3))
                parts=urlsplit(url)
                assert (parts.scheme,parts.hostname,parts.port)==("http","127.0.0.1",port+10)
                with urllib.request.urlopen(url, timeout=10) as response: data = response.read()
                assert hashlib.sha1(data).hexdigest()==sha1, "downloaded hash mismatch"
                actual=zip_inventory(data)
                assert actual==expected, f"archive contents differ: {set(actual)^set(expected)}"
                report["requests"][-1].update(bytes=len(data),matching_files=len(actual))
                send(49,ident+varint(4)); send(49,ident+varint(0))
            elif packet == 80: report["pops"]+=1
            elif packet == 1: known.add(read_int(frame))
            elif packet == 77:
                for _ in range(read_int(frame)):
                    eid=read_int(frame); known.discard(eid); removed.add(eid); metadata.pop(eid,None)
            elif packet == 99:
                eid=read_int(frame)
                raw=frame.read()
                keys=re.findall(rb"3dcasino:[a-z0-9_./-]+",raw)
                if b"textures" in raw:
                    for encoded in re.findall(rb"[A-Za-z0-9+/=]{48,}",raw):
                        skin=texture_id(encoded)
                        if skin: head_textures[eid]=skin
                if keys: metadata.setdefault(eid,set()).update(s.decode() for s in keys)
            elif packet == 121:
                match=re.search(rb"PACK_TEST_MARK/([a-z0-9]+)",frame.read())
                if not match: continue
                phase=match.group(1).decode()
                packed=first != (phase in ("swap","finish"))
                rows=[line.split() for line in (folder/"plugins/PackLayerProbe"/(phase+".txt")).read_text().splitlines()]
                positive={int(i) for layer,i,key in rows if layer == ("P" if packed else "N")}
                negative={int(i) for layer,i,key in rows if layer == ("N" if packed else "P")}
                controls={int(i) for layer,i,key in rows if layer=="H"}
                assert positive and positive<=known and not negative&known and controls<=known, (phase,packed,"missing",list(positive-known)[:4],"leaked",list(negative&known)[:4],"controls",controls-known)
                keys_seen=0
                for layer,i,key in rows:
                    if layer=="P" and packed:
                        namespace, resource=key.split(":")
                        assert f"assets/{namespace}/items/{resource}.json" in expected
                        assert key in metadata.get(int(i),()),(phase,"missing model metadata",i,key,metadata.get(int(i)))
                        keys_seen+=1
                heads={int(i):key.split("/")[1:] for layer,i,key in rows if not packed and key.startswith("head/")}
                if heads:
                    faces=[key.rsplit("/poker_",1)[1] for layer,i,key in rows if layer=="P" and "/poker_" in key and not key.endswith("poker_back")]
                    expected_tiles=Counter(texture_id(tile["value"]) for face in faces for tile in catalog[face])
                    for eid,(face,tile) in heads.items():
                        assert head_textures.get(eid)==texture_id(catalog[face][int(tile)]["value"]),(phase,face,tile,"wrong tile")
                    assert Counter(head_textures.get(i) for i in heads)==expected_tiles,(phase, "native card skin texture mismatch",sum(i in head_textures for i in heads),len(heads))
                if phase=="swap": assert prior<=removed and not prior&known,(phase,"old layer retained",len(prior-removed))
                if phase=="cards": prior=positive.copy()
                report["marks"][phase]={"native_profile_tiles_verified":len(heads),"packed":packed,"expected_visible":len(positive),"excluded":len(negative),"controls":len(controls),"item_models_in_metadata":keys_seen,"old_layer_removed":len(prior) if phase=="swap" else 0}
