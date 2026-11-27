"""Geometry regressions for cabinet supports and moving clearances."""
import math
import unittest
from PIL import Image

from tools.tests.test_casino_resource_pack import ASSETS, NATIVE, box_points, model


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def sub(a, b):
    return [x - y for x, y in zip(a, b)]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]]


def shapes(name, packed=False):
    source = model(name)["elements"] if packed else NATIVE[name]["boxes"]
    return {part["name"]: box_points(part, packed) for part in source}


def pose(points, at=(0, 0, 0), pitch=0, yaw=0, roll=0, scale=(1, 1, 1)):
    cp, sp, cy, sy, cr, sr = (function(angle) for angle in (pitch, yaw, roll)
                              for function in (math.cos, math.sin))
    result = []
    for point in points:
        x, y, z = [value * factor for value, factor in zip(point, scale)]
        y, z = y * cp - z * sp, y * sp + z * cp
        x, z = x * cy + z * sy, -x * sy + z * cy
        x, y = x * cr - y * sr, x * sr + y * cr
        result.append([value + offset for value, offset in zip((x, y, z), at)])
    return result


def overlaps(a, b, tolerance=1e-7):
    for axis in range(3):
        if max(p[axis] for p in a) < min(p[axis] for p in b) - tolerance or (
                max(p[axis] for p in b) < min(p[axis] for p in a) - tolerance):
            return False
    axes_a = [sub(a[i], a[0]) for i in (4, 2, 1)]
    axes_b = [sub(b[i], b[0]) for i in (4, 2, 1)]
    axes = [*axes_a, *axes_b, *(cross(u, v) for u in axes_a for v in axes_b)]
    for axis in axes:
        length = math.sqrt(dot(axis, axis))
        if length < 1e-10:
            continue
        axis = [value / length for value in axis]
        first, second = [dot(p, axis) for p in a], [dot(p, axis) for p in b]
        if max(first) < min(second) - tolerance or max(second) < min(first) - tolerance:
            return False
    return True


def button_socket(name, x, y, z, width, pitch, size, packed):
    return pose(shapes(name, packed)["socket"], (x, y, z), math.radians(pitch),
                scale=(width / .6, size, size))


def joined_to_ground(parts, target):
    connected = {name for name, points in parts.items() if min(p[1] for p in points) <= 1e-7}
    while True:
        previous = len(connected)
        connected.update(name for name, points in parts.items()
                         if any(overlaps(points, parts[other]) for other in list(connected)))
        if len(connected) == previous:
            return any(overlaps(target, parts[name]) for name in connected)


def side_overlap(a, b):
    def cross2(u, v):
        return u[0] * v[1] - u[1] * v[0]

    def area(points):
        return sum(cross2(points[i], points[(i + 1) % len(points)])
                   for i in range(len(points))) / 2 if len(points) >= 3 else 0

    def clip(subject, cutter):
        if area(cutter) < 0:
            cutter = list(reversed(cutter))
        points = subject
        for u, v in zip(cutter, cutter[1:] + cutter[:1]):
            output = []
            for s, e in zip(points, points[1:] + points[:1]):
                ds, de = cross2(sub(v, u), sub(s, u)), cross2(sub(v, u), sub(e, u))
                if (ds >= -1e-9) != (de >= -1e-9):
                    output.append([s[i] + (e[i] - s[i]) * ds / (ds - de) for i in range(2)])
                if de >= -1e-9:
                    output.append(e)
            points = output
            if not points:
                break
        return abs(area(points))

    result = 0
    for sign in (min, max):
        x_a, x_b = sign(p[0] for p in a), sign(p[0] for p in b)
        if abs(x_a - x_b) > 1e-7:
            continue
        polygons = []
        for points, x in ((a, x_a), (b, x_b)):
            vertices = [(p[2], p[1]) for p in points if abs(p[0] - x) < 1e-7]
            center = [sum(p[i] for p in vertices) / len(vertices) for i in range(2)]
            polygons.append(sorted(vertices, key=lambda p: math.atan2(p[1] - center[1], p[0] - center[0])))
        result += clip(*polygons)
    return result


class CasinoMountsTest(unittest.TestCase):
    def test_load_bearing_changes_have_the_same_native_and_packed_geometry(self):
        changed = {
            "money_wheel_machine": ("multiplier_control_dock", "multiplier_console_support"),
            "hilo_machine": ("left_support", "right_support"),
            "blackjack_score_panel": ("screen_body",),
            "penguin_cross_machine": ("bank_left", "console_bracket_l", "console_bracket_r"),
            "mines_machine": ("console_bracket",),
            "dragon_tower_machine": ("control_dock",),
            "wheel_of_fortune_pointer": ("mount", "lit_tip"),
            "money_wheel_pointer": ("mount", "lit_tip"),
        }
        for name, parts in changed.items():
            native, packed = shapes(name), shapes(name, True)
            for part in parts:
                with self.subTest(model=name, part=part):
                    for point in native[part]:
                        self.assertLess(min(math.dist(point, other) for other in packed[part]), 2e-6)

    def test_money_wheel_multiplier_buttons_join_the_grounded_cabinet(self):
        for packed in (False, True):
            body = shapes("money_wheel_machine", packed)
            for i, value in enumerate((2, 3, 5, 10)):
                with self.subTest(packed=packed, multiplier=value):
                    socket = button_socket(f"money_wheel_button_{value}x", (i - 1.5) * .58,
                                           .65, 1.22, .5, -35, 1, packed)
                    self.assertTrue(joined_to_ground(body, socket), "Multiplier socket has no cabinet support")

    def test_hilo_panel_reaches_both_existing_rear_supports(self):
        for packed in (False, True):
            body = shapes("hilo_machine", packed)
            panel = pose(shapes("hilo_number_panel", packed)["panel"], (0, 1.1, 0), math.radians(-35))
            for side in ("left", "right"):
                with self.subTest(packed=packed, side=side):
                    self.assertTrue(overlaps(panel, body[side + "_support"]), "Tilted panel floats above its support")

    def test_blackjack_screen_body_reaches_the_table(self):
        for packed in (False, True):
            with self.subTest(packed=packed):
                self.assertTrue(overlaps(shapes("blackjack_score_panel", packed)["screen_body"],
                                         shapes("blackjack_machine", packed)["felt"]))

    def test_sloped_console_sides_do_not_share_the_bracket_plane(self):
        for packed in (False, True):
            for name, dock, brackets in (
                    ("penguin_cross_machine", "control_dock", ("console_bracket_l", "console_bracket_r")),
                    ("mines_machine", "lower_control_dock", ("console_bracket",))):
                body = shapes(name, packed)
                for bracket in brackets:
                    with self.subTest(packed=packed, model=name, bracket=bracket):
                        self.assertLess(side_overlap(body[dock], body[bracket]), 1e-7)

    def test_pointer_mount_reaches_the_fixed_rim_and_never_enters_the_rotating_disc(self):
        disc = shapes("wheel_of_fortune_disc")
        pointer = {name: pose(points, (0, 2.93, .36))
                   for name, points in shapes("wheel_of_fortune_pointer").items()}
        for packed in (False, True):
            for game in ("wheel_of_fortune", "money_wheel"):
                body = shapes(game + "_machine", packed)
                mount = pose(shapes(game + "_pointer", packed)["mount"], (0, 2.93, .36))
                with self.subTest(packed=packed, model=game):
                    self.assertTrue(any(overlaps(mount, points) for name, points in body.items()
                                        if name.startswith("rim_")), "Pointer is detached from the fixed rim")
        # Every outcome and all 81 frames of the five-turn easing curve.
        for segment in range(20):
            end = 10 * math.pi + (segment + .5) * math.tau / 20
            for frame in range(81):
                angle = end * (1 - (1 - frame / 80) ** 3)
                for name, points in disc.items():
                    rotated = pose(points, (0, 2, .25), roll=angle)
                    for part, fixed in pointer.items():
                        self.assertFalse(overlaps(fixed, rotated), (segment, frame, part, name))

    def test_native_plinko_pin_backs_reach_the_pin_board(self):
        body = shapes("plinko_machine")
        for name, points in body.items():
            if name.startswith("pin_") and name != "pin_board":
                with self.subTest(pin=name):
                    self.assertTrue(overlaps(points, body["pin_board"]), "Pin floats in front of its board")

    def test_pointer_parts_clear_the_entire_native_and_packed_rotation_envelopes(self):
        for game in ("wheel_of_fortune", "money_wheel"):
            for packed in (False, True):
                if packed:
                    data = model(game + "_disc")
                    walls = [p for e in data["elements"] if e["name"].startswith("wheel_wall_")
                             for p in box_points(e, True)]
                    radius = max(math.hypot(p[0], p[1]) for p in walls)
                    cap = next(e for e in data["elements"] if e["name"] == "wheel_cap")
                    texture = data["textures"][cap["faces"]["north"]["texture"][1:]]
                    with Image.open(ASSETS / "textures" / (texture.split(":", 1)[1] + ".png")) as image:
                        width, height = image.size
                        for index, pixel in enumerate(image.convert("RGBA").get_flattened_data()):
                            if pixel[3]:
                                x = ((index % width + .5) / width - .5) * (cap["to"][0] - cap["from"][0]) / 4
                                y = ((index // width + .5) / height - .5) * (cap["to"][1] - cap["from"][1]) / 4
                                radius = max(radius, math.hypot(x, y))
                        radius += math.hypot((cap["to"][0] - cap["from"][0]) / width,
                                             (cap["to"][1] - cap["from"][1]) / height) / 8
                    points = [p for e in data["elements"] for p in box_points(e, True)]
                else:
                    points = [p for part in shapes(game + "_disc").values() for p in part]
                    radius = max(math.hypot(p[0], p[1]) for p in points)
                front = .25 + max(p[2] for p in points)
                for name, part in shapes(game + "_pointer", packed).items():
                    fixed = pose(part, (0, 2.93, .36))
                    with self.subTest(model=game, packed=packed, part=name):
                        self.assertTrue(min(p[1] for p in fixed) > 2 + radius + .005
                                        or min(p[2] for p in fixed) > front + .005,
                                        "Pointer intersects the disc's full swept envelope")

    def test_penguin_initial_feet_are_supported_by_the_left_bank(self):
        for packed in (False, True):
            bank = shapes("penguin_cross_machine", packed)["bank_left"]
            for name, points in shapes("penguin_cross_penguin", packed).items():
                if name.startswith("foot_"):
                    with self.subTest(packed=packed, foot=name):
                        foot = pose(points, (-1.1, 1.08, 0), yaw=math.pi / 2)
                        self.assertTrue(overlaps(bank, foot), "Initial foot has no bank underneath")

    def test_legacy_dragon_buttons_reach_the_control_dock(self):
        for packed in (False, True):
            body = shapes("dragon_tower_machine", packed)
            for name, x in (("play", -.29), ("cashout", .29)):
                with self.subTest(packed=packed, action=name):
                    socket = button_socket("casino_button_" + name, x, .65, 1.22, .46, -35, 1, packed)
                    self.assertTrue(overlaps(socket, body["control_dock"]))
                    self.assertTrue(joined_to_ground(body, socket))


if __name__ == "__main__":
    unittest.main()
