"""Read the fixture's displayed dialog, then click Save using the action from the packet."""

import struct


class Compound(dict):
    # Preserve native action additions verbatim, including Paper's typed callback identifier.
    entries: bytes


def utf(stream):
    size = struct.unpack(">H", stream.read(2))[0]
    return stream.read(size).decode("utf-8")


def read_tag(stream, tag):
    if tag == 10:
        start = stream.tell()
        result = Compound()
        while (child := stream.read(1)[0]) != 0:
            name = utf(stream)
            result[name] = read_tag(stream, child)
        result.entries = bytes(stream.getbuffer()[start:stream.tell() - 1])
        return result
    if tag == 8:
        return utf(stream)
    if tag == 9:
        child, count = struct.unpack(">bi", stream.read(5))
        return [read_tag(stream, child) for _ in range(count)]
    if tag in (7, 11, 12):
        count = struct.unpack(">i", stream.read(4))[0]
        return stream.read(count * {7: 1, 11: 4, 12: 8}[tag])
    fmt = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}[tag]
    return struct.unpack(fmt, stream.read(struct.calcsize(fmt)))[0]


def label(component):
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(label(part) for part in component)
    return component.get("text", "") + "".join(label(part) for part in component.get("extra", []))


def dialog_packet(version, packet, frame, send, counters):
    if packet != (143 if version == "26.3" else 140):
        return False
    from matrix_client import read_int, string, varint

    if read_int(frame) != 0 or frame.read(1) != b"\x0a":
        raise ValueError("Expected an inline native dialog")
    screen = read_tag(frame, 10)
    counters["dialogs"] += 1
    if label(screen["title"]) != "Casino matrix amount":
        raise ValueError(f"Unexpected dialog title {screen['title']!r}")
    if screen["inputs"][0]["key"] != "amount" or screen["inputs"][0]["initial"] != "1.00":
        raise ValueError("Expected the original amount field")
    button = screen["actions"][0]
    body = screen["body"]
    back = (body[-1] if isinstance(body, list) else body)["contents"]
    if (label(back) != "[Back]" or not back.get("click_event")
            or [label(entry["label"]) for entry in screen["actions"]] != ["[Save]"]
            or label(screen["exit_action"]["label"]) != "[Close]"):
        raise ValueError("Expected a Back text link, a Save button, and a separate Close exit")
    action = button["action"]
    if action["type"].split(":")[-1] != "dynamic/custom":
        raise ValueError("Expected an input-aware custom action")
    entries = action["additions"].entries if "additions" in action else b""
    # Unnamed network NBT compound: original additions plus the text input selected by the client.
    payload = b"\x0a" + entries + b"\x08\x00\x06amount\x00\x0512.34\x00"
    send(68, string(action["id"]) + varint(len(payload)) + payload)
    counters["dialog_clicks"] = counters.get("dialog_clicks", 0) + 1
    return True
