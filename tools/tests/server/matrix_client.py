"""Minimal normal offline Minecraft client used by the Casino localhost fixture."""

import hashlib
import io
from queue import Empty
import select
import socket
import struct
import uuid
import zlib

from matrix_dialog import dialog_packet


def varint(n):
    result = bytearray()
    while n >= 128:
        result.append((n & 127) | 128)
        n >>= 7
    result.append(n)
    return result


def read_int(stream):
    n = 0
    for i in range(5):
        part = stream.read(1)
        if not part:
            raise EOFError("Server closed connection")
        n |= (part[0] & 127) << (i * 7)
        if part[0] < 128:
            return n
    raise ValueError("Invalid Minecraft VarInt")


def string(s):
    b = s.encode("utf-8")
    return varint(len(b)) + b


def play(version, port, clicks, stop):
    # Packet IDs from ProtocolInfo.DetailsProvider in each corresponding vanilla server.
    protocol = 777 if version == "26.3" else 776 if version == "26.2" else 775
    newer = protocol == 777
    username = "CasinoMatrix"
    player = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + username).encode()).digest(), version=3)
    state, compression = "login", False
    counters = {"joined": False, "teleports": 0, "interactions": 0, "chunks": 0, "dialogs": 0}
    with socket.create_connection(("127.0.0.1", port), timeout=10) as connection:
        connection.settimeout(12)
        with connection.makefile("rb", buffering=0) as stream:
            def send(packet, body=b""):
                data = varint(packet) + body
                if compression:
                    data = b"\0" + data  # Every clientbound request here fits below the server threshold.
                connection.sendall(varint(len(data)) + data)

            send(0, varint(protocol) + string("127.0.0.1") + struct.pack(">H", port) + varint(2))
            send(0, string(username) + player.bytes)
            while not stop.is_set():
                if state == "play":
                    try:
                        target = clicks.get_nowait()
                        # entity id, main hand, zero local interaction vector, secondary use false
                        send(26, varint(target) + b"\0\0\0")
                        counters["interactions"] += 1
                    except Empty:
                        pass
                if not select.select([connection], [], [], .1)[0]:
                    continue
                size = read_int(stream)
                if not 0 < size < 8 * 1024 * 1024:
                    raise ValueError(f"Invalid packet size {size}")
                data = bytearray()
                while len(data) < size:
                    chunk = stream.read(size - len(data))
                    if not chunk:
                        raise EOFError("Truncated packet")
                    data.extend(chunk)
                frame = io.BytesIO(data)
                if compression:
                    raw = read_int(frame)
                    if raw:
                        frame = io.BytesIO(zlib.decompress(frame.read()))
                packet = read_int(frame)
                if state == "login":
                    if packet == 0:
                        raise RuntimeError("Login rejected: " + str(frame.read()[:160]))
                    if packet == 3:
                        threshold = read_int(frame)
                        if threshold < 128:
                            raise ValueError("Expected standard compression threshold")
                        compression = True
                    if packet == 2:
                        send(3)
                        state = "config"
                        send(0, string("en_us") + b"\x02\x00\x01\x7f\x01\x00\x00\x00")
                elif state == "config":
                    if packet == 2:
                        raise RuntimeError("Configuration rejected: " + str(frame.read()[:160]))
                    if packet == (15 if newer else 14):
                        send(7, b"\0")
                    elif packet == 3:
                        send(3)
                        state = "play"
                    elif packet in (4, 5):
                        send(packet, frame.read())
                elif dialog_packet(version, packet, frame, send, counters):
                    continue
                elif packet == 32:
                    raise RuntimeError("Disconnected: " + str(frame.read()[:160]))
                elif packet == 49 + newer:
                    counters["joined"] = True
                elif packet == 44 + newer:
                    send(28, frame.read())
                elif packet == 61 + newer:
                    send(45, frame.read())
                elif packet == 72 + newer:
                    reply = varint(read_int(frame))
                    if newer:
                        position = frame.read(24)
                        frame.read(24)
                        reply += position + frame.read(8)
                    send(0, reply)
                    if not counters["teleports"]:
                        send(44)
                    counters["teleports"] += 1
                elif packet == 45 + newer:
                    counters["chunks"] += 1
                elif packet == 11:
                    send(11, struct.pack(">f", 8))
    return counters
