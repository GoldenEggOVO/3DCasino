import json
import math
import unittest
from pathlib import Path

from tools.tests.test_casino_resource_pack import model, NATIVE


ROOT = Path(__file__).resolve().parents[2]
BODIES = (
    "plinko_machine", "mines_machine", "mines_machine_sloped", "blackjack_machine",
    "crash_machine", "slots_machine", "duck_race_machine", "wheel_of_fortune_machine",
    "money_wheel_machine", "penguin_cross_machine", "keno_machine", "hilo_machine",
    "dragon_tower_machine", "dragon_tower_machine_compact",
)


def rotate(point, roll, pitch):
    x, y, z = point
    x, y = x * math.cos(roll) - y * math.sin(roll), x * math.sin(roll) + y * math.cos(roll)
    return x, y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)


def ray_distance(box, point, direction):
    a, b = box["from"], box["to"]
    center = [(u + v) / 2 for u, v in zip(a, b)]
    local = rotate([p - c for p, c in zip(point, center)], -box.get("roll", 0), 0)
    local = rotate(local, 0, -box.get("pitch", 0))
    vector = rotate(direction, -box.get("roll", 0), 0)
    vector = rotate(vector, 0, -box.get("pitch", 0))
    near, far = 0, math.inf
    for i in range(3):
        half = (b[i] - a[i]) / 2
        if abs(vector[i]) < 1e-8:
            if abs(local[i]) > half:
                return math.inf
            continue
        first, last = sorted(((-half - local[i]) / vector[i], (half - local[i]) / vector[i]))
        near, far = max(near, first), min(far, last)
    return near if near <= far else math.inf


def ray_box(box, point, direction):
    return math.isfinite(ray_distance(box, point, direction))


class CasinoModelsTest(unittest.TestCase):
    def test_native_machine_shells_use_white_armor_and_independent_cyan_lights(self):
        for name in BODIES:
            with self.subTest(name=name):
                boxes = NATIVE[name]["boxes"]
                materials = {box["material"] for box in boxes}
                self.assertTrue({"WHITE_CONCRETE", "SMOOTH_QUARTZ"}.intersection(materials))
                self.assertIn("BLACK_CONCRETE", materials)
                self.assertFalse({"DARK_OAK_PLANKS", "WAXED_EXPOSED_COPPER"}.intersection(materials))
                self.assertTrue(any(box["material"] == "LIGHT_BLUE_CONCRETE" and box.get("emission") == 15
                                    for box in boxes))

    def test_packed_machine_shells_use_white_armor_dark_chassis_and_cyan_lights(self):
        for name in BODIES:
            with self.subTest(name=name):
                packed = model(name)
                used = {packed["textures"][face["texture"][1:]]
                        for element in packed["elements"] for face in element["faces"].values()}
                for material in ("future_shell", "future_chassis", "future_accent"):
                    self.assertTrue(any(reference.endswith("/" + material) for reference in used),
                                    (name, material))
                self.assertFalse(any(reference.endswith(("/wood", "/warm_bronze", "/warm_ivory", "/warm_wheel_back"))
                                     for reference in used), name)

    def test_repeated_parts_keep_small_native_entity_budgets(self):
        for name, data in NATIVE.items():
            if "button" in name:
                self.assertLessEqual(len(data["boxes"]), 12 if "round" in name else 9, name)
        for name in ("keno_tile", "keno_tile_selected"):
            self.assertLessEqual(len(NATIVE[name]["boxes"]), 3, name)
        for name in ("wheel_of_fortune_disc", "money_wheel_disc"):
            self.assertLessEqual(len(NATIVE[name]["boxes"]), 110, name)
        for name in BODIES:
            self.assertLessEqual(len(NATIVE[name]["boxes"]), 125 if name == "plinko_machine" else 65, name)

    def test_only_lights_primary_buttons_and_animated_flame_are_full_bright(self):
        for name, data in NATIVE.items():
            for box in data["boxes"]:
                if box.get("emission", 0) > 6:
                    if (name, box.get("name")) == ("crash_rocket", "flame"):
                        continue
                    self.assertTrue("light" in box["name"] or box["name"] == "front_kick"
                                    or (name.endswith("_pointer") and box["name"] == "lit_tip")
                                    or ("button" in name and box["name"] in
                                        ("face", "button_middle", "button_lower", "button_upper")),
                                    (name, box["name"]))
                    self.assertTrue(box["material"].endswith("_CONCRETE"), (name, box["material"]))

    def test_light_levels_and_box_geometry_are_valid_without_duplicate_parts(self):
        for name, data in NATIVE.items():
            with self.subTest(name=name):
                parts = set()
                for box in data["boxes"]:
                    self.assertIn(box.get("emission", 0), range(16))
                    self.assertTrue(all(a < b for a, b in zip(box["from"], box["to"])))
                    key = json.dumps({k: v for k, v in box.items() if k not in ("name", "emission")}, sort_keys=True)
                    self.assertNotIn(key, parts)
                    parts.add(key)
                for element in model(name)["elements"]:
                    self.assertIn(element.get("light_emission", 0), range(16))

    def test_machine_shells_have_no_exposed_coplanar_side_or_top_faces(self):
        for name in BODIES:
            boxes = NATIVE[name]["boxes"]
            for first, a in enumerate(boxes):
                if a.get("roll", 0) or a.get("pitch", 0) or "matrix" in a:
                    continue
                for second, b in enumerate(boxes[first + 1:], first + 1):
                    if b.get("roll", 0) or b.get("pitch", 0) or "matrix" in b:
                        continue
                    for axis in range(3):
                        across = [i for i in range(3) if i != axis]
                        low = [max(a["from"][i], b["from"][i]) for i in across]
                        high = [min(a["to"][i], b["to"][i]) for i in across]
                        if any(end - start < 1e-6 for start, end in zip(low, high)):
                            continue
                        for key, sign in (("from", -1), ("to", 1)):
                            if abs(a[key][axis] - b[key][axis]) > 1e-7:
                                continue
                            for u in (.1, .5, .9):
                                for v in (.1, .5, .9):
                                    point = [0., 0., 0.]
                                    point[axis] = a[key][axis] + sign * 1e-5
                                    for dimension, fraction in enumerate((u, v)):
                                        point[across[dimension]] = low[dimension] + fraction * (
                                                high[dimension] - low[dimension])
                                    normal = tuple(sign if i == axis else 0 for i in range(3))
                                    self.assertTrue(any(ray_box(box, point, normal)
                                                        for index, box in enumerate(boxes)
                                                        if index not in (first, second)),
                                                    (name, a.get("name"), b.get("name"), key, axis))

    def test_selected_tiles_keep_the_same_geometry_for_recoloring(self):
        for first, second in (("keno_tile", "keno_tile_selected"),
                              ("dragon_tile_hidden", "dragon_tile_safe"),
                              ("dragon_tile_hidden", "dragon_tile_trap")):
            geometry = lambda name: [{k: v for k, v in box.items()
                                      if k not in ("name", "material", "emission")}
                                     for box in NATIVE[name]["boxes"]]
            self.assertEqual(geometry(first), geometry(second))

    def test_machine_labels_stay_in_front_of_their_shells(self):
        for name, data in NATIVE.items():
            for label in data["labels"]:
                x, y, z = label["position"]
                for u in (-.45, -.225, 0, .225, .45):
                    for v in (-.45, 0, .45):
                        point = x + u * label["width"], y + v * label["height"], z + .0001
                        for box in data["boxes"]:
                            self.assertFalse(ray_box(box, point, (0, 0, 1)),
                                             (name, label["text"], box.get("name"), point))

    def test_mines_counter_stays_in_front_of_the_tilted_dock(self):
        pitch = math.radians(-35)
        point = rotate((0, .56, .04), 0, pitch)
        point = point[0], point[1] + .67, point[2] + 1.96
        normal = rotate((0, 0, 1), 0, pitch)
        for box in NATIVE["mines_machine_sloped"]["boxes"]:
            self.assertFalse(ray_box(box, point, normal), box.get("name"))

    def test_default_and_custom_layouts_keep_pressed_button_faces_visible(self):
        layouts = {
            "mines_machine": ((-.79, 1.18, 1.57, .30, .45, .09625),
                              (-.31, 1.18, 1.57, .30, .45, .09625),
                              (-.55, .72, 1.80, .92, 1, .09625), (.55, .72, 1.80, .92, 1, .09625)),
            "mines_machine_sloped": ((-.78, 1.125050011, 1.7207209, .31, .34, .04),
                                      (.78, 1.125050011, 1.7207209, .31, .34, .04),
                                      (-.60, .768718872, 1.97022665, 1.03, .86, .04),
                                      (.60, .768718872, 1.97022665, 1.03, .86, .04)),
            "duck_race_machine": (*((x, .78, z, .5, .8, .04)
                                      for x in (-.9, -.3, .3, .9) for z in (1.18, 1.4)),
                                   (0, .31, 1.73, .65, 1.2, .04),
                                   (1.43, 1.05, .9, .65, 1.2, .04)),
            "penguin_cross_machine": tuple((x, .65, z, .46, 1, .04)
                                             for x in (-.58, 0, .58) for z in (1.22, 1.42)),
        }
        pitch = math.radians(-35)
        normal = rotate((0, 0, 1), 0, pitch)
        for name, buttons in layouts.items():
            for x, y, z, width, size, travel in buttons:
                for press in (0, travel):
                    for across in (-.4, -.2, 0, .2, .4):
                        for along in (.065, .095, .125, .155, .185, .215, .245, .275, .305, .335):
                            point = rotate((across * width, along * size, .1301 * size - press), 0, pitch)
                            point = point[0] + x, point[1] + y, point[2] + z
                            for box in NATIVE[name]["boxes"]:
                                for direction in (normal, (0, 0, 1)):
                                    self.assertFalse(ray_box(box, point, direction),
                                                     (name, box.get("name"), press, point, direction))

    def test_round_backing_stays_inside_the_outer_rim(self):
        for name, cy, radius in (("wheel_of_fortune_machine", 2, .975),
                                 ("money_wheel_machine", 2, .975),
                                 ("wheel_of_fortune_disc", 0, .869),
                                 ("money_wheel_disc", 0, .869)):
            for box in NATIVE[name]["boxes"]:
                if not box.get("name", "").startswith("disk_"):
                    continue
                for x in (box["from"][0], box["to"][0]):
                    for y in (box["from"][1], box["to"][1]):
                        self.assertLessEqual(math.hypot(x, y - cy), radius, name)

    def test_native_wheel_colors_match_the_payout_under_each_label(self):
        fortunes = (-1, 3, .1, .5, .25, 5, .1, .25, 2, .1, -1, 3, .5, .1, .25, 0, .5, .1, 2, .25)
        fortune_colors = {-1: "GRAY_CONCRETE", 3: "YELLOW_CONCRETE", .1: "RED_CONCRETE",
                          .5: "PURPLE_CONCRETE", .25: "ORANGE_CONCRETE", 5: "LIME_CONCRETE",
                          2: "BLUE_CONCRETE", 0: "BLACK_CONCRETE"}
        money = (0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1)
        money_colors = ("LIME_CONCRETE", "BLUE_CONCRETE", "RED_CONCRETE", "YELLOW_CONCRETE")
        for name, radius, expected in (
                ("wheel_of_fortune_disc", .64, [fortune_colors[v] for v in fortunes]),
                ("money_wheel_disc", .78, [money_colors[v] for v in money])):
            for i, color in enumerate(expected):
                for r in (.20, .36, .52, radius, .81):
                    for offset in (-8.75, -4, 0, 4, 8.75):
                        angle = (i + .5) * math.tau / 20 + math.radians(offset)
                        eye = (r * math.sin(angle), r * math.cos(angle), 1)
                        depth, material = min((ray_distance(b, eye, (0, 0, -1)), b["material"])
                                              for b in NATIVE[name]["boxes"])
                        self.assertTrue(math.isfinite(depth), (name, i, r, offset))
                        self.assertEqual(color, material, (name, i, r, offset))

    def test_native_wheel_sectors_meet_the_rim_without_exposing_the_stepped_backing(self):
        for name in ("money_wheel_disc", "wheel_of_fortune_disc"):
            boxes = NATIVE[name]["boxes"]
            for degree in range(360):
                angle = math.radians(degree + .25)
                for radius in (.834, .846, .854, .859, .866):
                    eye = (radius * math.sin(angle), radius * math.cos(angle), 1)
                    depth, part = min((ray_distance(b, eye, (0, 0, -1)), b["name"]) for b in boxes)
                    self.assertTrue(math.isfinite(depth) and part.startswith(("segment_", "rim_")),
                                    (name, degree, radius, part))


if __name__ == "__main__":
    unittest.main()
