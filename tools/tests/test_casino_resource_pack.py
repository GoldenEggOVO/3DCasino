"""Contracts for the editable Casino assets, independent of generated archives."""
import json
import math
import unittest
from pathlib import Path
from PIL import Image
from tools.package_resource_pack import item_definitions

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "resource-pack/assets/3dcasino"
def native_models():
    root = ROOT / "src/main/resources/vanilla-models"
    index = json.loads((root / "index.json").read_text())
    models = {}
    for key, path in index.items():
        if not key.startswith("casino/"):
            continue
        model = json.loads((root / path).read_text())
        boxes = []
        for part in model["blocks"]:
            position, size = part["position"], part["size"]
            box = {"name": part["name"], "material": part["material"],
                   "from": [(position[i] - size[i] / 2) * 4 for i in range(3)],
                   "to": [(position[i] + size[i] / 2) * 4 for i in range(3)]}
            pitch, yaw, roll = part.get("rotation", [0, 0, 0])
            assert yaw == 0
            if pitch:
                box["pitch"] = math.radians(pitch)
            if roll:
                box["roll"] = math.radians(roll)
            if "brightness" in part:
                box["emission"] = part["brightness"]
            boxes.append(box)
        labels = []
        for part in model["labels"]:
            label = dict(part, position=[v * 4 for v in part["position"]], width=part["width"] * 4, height=part["height"] * 4)
            labels.append(label)
        models[key.removeprefix("casino/")] = {"boxes": boxes, "labels": labels}
    return models


NATIVE = native_models()
ITEMS = {name: path for name, path in item_definitions(ROOT / "resource-pack").items()
         if path.is_relative_to(ASSETS / "items/casino")}


def items():
    return ITEMS


def model(name):
    item = json.loads(items()[name].read_text())
    path = item["model"]["model"].replace("3dcasino:", "")
    return json.loads((ASSETS / "models" / (path + ".json")).read_text())


def rotate(point, axis, angle, origin=(0, 0, 0)):
    point = [value - center for value, center in zip(point, origin)]
    i, j = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[axis]
    a, b = point[i], point[j]
    point[i], point[j] = a * math.cos(angle) - b * math.sin(angle), a * math.sin(angle) + b * math.cos(angle)
    return [value + center for value, center in zip(point, origin)]


def box_points(box, packed=False):
    from itertools import product
    if "matrix" in box:
        matrix = box["matrix"]
        return [[sum(matrix[row * 4 + col] * value for col, value in enumerate((*point, 1)))
                 for row in range(3)] for point in product((0, 1), repeat=3)]
    center = [(a + b) / 2 for a, b in zip(box["from"], box["to"])]
    points = []
    for point in product(*zip(box["from"], box["to"])):
        if "rotation" in box:
            rotation = box["rotation"]
            point = rotate(point, rotation["axis"], math.radians(rotation["angle"]), rotation["origin"])
        elif not packed:
            point = rotate(point, "x", box.get("pitch", 0), center)
            point = rotate(point, "z", box.get("roll", 0), center)
        points.append([(value - 8) / factor for value, factor in zip(point, (-4, 4, -4))] if packed else point)
    return points


def geometry_bounds(points):
    return [fn(point[axis] for point in points) for fn in (min, max) for axis in range(3)]


class CasinoResourcePackTest(unittest.TestCase):
    def test_every_model_has_a_particle_texture_from_its_own_palette(self):
        for path in (ASSETS / "models/item").rglob("*.json"):
            with self.subTest(model=path.stem):
                palette = json.loads(path.read_bytes())["textures"]
                texture = palette["particle"]
                if texture.startswith("#"):
                    texture = palette[texture[1:]]
                self.assertIn(texture, [value for key, value in palette.items() if key != "particle"])
                namespace, name = texture.split(":")
                self.assertEqual("3dcasino", namespace)
                self.assertTrue((ASSETS / "textures" / (name + ".png")).is_file())

    def test_every_native_logical_model_has_one_editable_pack_item(self):
        self.assertEqual(set(NATIVE), set(items()))
        all_paths = list((ASSETS / "items").rglob("*.json"))
        self.assertEqual(len(all_paths), len({path.stem for path in all_paths}))
        for name in NATIVE:
            item = json.loads(items()[name].read_text())
            self.assertEqual("minecraft:model", item["model"]["type"])
            self.assertEqual(items()[name].stem, Path(item["model"]["model"]).name)
            self.assertTrue(item["model"]["model"].startswith("3dcasino:item/casino/"))

    def test_geometry_uses_native_origin_and_bounded_exterior_faces(self):
        for name in NATIVE:
            with self.subTest(name=name):
                data = model(name)
                self.assertNotIn("display", data)
                self.assertNotIn("parent", data)
                budget = 6 if name.startswith("card_") else 100 if (
                    "button" in name or name.startswith("dragon_tile") or name in (
                        "money_wheel_disc", "wheel_of_fortune_disc", "penguin_cross_penguin",
                        "crash_rocket", "hilo_slider", "keno_tile", "keno_tile_selected",
                        "wheel_of_fortune_pointer", "money_wheel_pointer")
                    or name.startswith("duck_race_duck_") and name != "duck_race_machine") else 250
                self.assertLessEqual(sum(len(e["faces"]) for e in data["elements"]), budget)
                for element in data["elements"]:
                    self.assertTrue(all(-16 <= value <= 32 for value in element["from"] + element["to"]))
                    self.assertTrue(all(a <= b for a, b in zip(element["from"], element["to"])))
                    self.assertTrue(all(math.isfinite(value) for value in element["from"] + element["to"]))
                    for face in element["faces"].values():
                        self.assertIn(face["texture"][1:], data["textures"])
                        self.assertTrue(all(0 <= value <= 16 for value in face["uv"]))

    def test_all_textures_are_local_and_in_the_item_atlas(self):
        referenced = set()
        for name in NATIVE:
            for reference in model(name)["textures"].values():
                self.assertTrue(reference.startswith("3dcasino:item/"), reference)
                path = ASSETS / "textures" / (reference.split(":", 1)[1] + ".png")
                self.assertTrue(path.is_file(), reference)
                referenced.add(path)
                with Image.open(path) as artwork:
                    artwork.verify()
        self.assertEqual(set((ASSETS / "textures/item/casino").rglob("*.png")),
                         {path for path in referenced if path.is_relative_to(ASSETS / "textures/item/casino")})

    def test_exterior_bounds_preserve_native_pivots_without_extra_normalization(self):
        for name, native in NATIVE.items():
            expected = geometry_bounds([point for box in native["boxes"] for point in box_points(box)])
            actual = geometry_bounds([point for box in model(name)["elements"] for point in box_points(box, packed=True)])
            for a, b in zip(expected, actual):
                self.assertAlmostEqual(a, b, delta=.035, msg=name)

    def test_craftengine_registers_every_logical_id_using_item_model(self):
        config = "\n".join(path.read_text() for path in sorted(
            (ROOT / "resource-pack/craftengine/resources/3dcasino/configuration").rglob("*.yml")))
        for name, path in items().items():
            item_path = path.relative_to(ASSETS / "items").with_suffix("").as_posix()
            self.assertEqual(1, config.count(f"  3dcasino:{name}:\n"), name)
            self.assertIn(f"  3dcasino:{name}:\n    material: paper\n    item_model: 3dcasino:{item_path}\n", config)
        self.assertEqual(set(NATIVE), set(items()))

    def test_cards_keep_casino_suit_order_and_reuse_shared_art(self):
        suits = ("spades", "hearts", "clubs", "diamonds")
        ranks = ("ace", *map(str, range(2, 11)), "jack", "queen", "king")
        for index in range(53):
            data = model(f"card_{index}")
            self.assertEqual(1, len(data["elements"]))
            element = data["elements"][0]
            self.assertEqual([0, -2.666668, 7.88], element["from"])
            self.assertEqual([16, 18.666668, 8.08], element["to"])
            face_key = element["faces"]["north"]["texture"][1:]
            reference = data["textures"][face_key]
            source_name = "back_blue_basic" if index == 52 else f"{suits[index // 13]}_{ranks[index % 13]}"
            shared_name = "poker_card_back_rear" if index == 52 else "poker_" + source_name + "_face"
            self.assertEqual("3dcasino:item/shared/playing_cards/" + shared_name, reference)
            source_file = "poker_back_blue.png" if index == 52 else "poker_" + source_name + ".png"
            source = ROOT / "resource-pack/originals/shared/textures/playing-cards" / source_file
            actual = ASSETS / "textures" / (reference.split(":", 1)[1] + ".png")
            with Image.open(source) as original, Image.open(actual) as artwork:
                expected = original.convert("RGB").resize((120, 168), Image.Resampling.NEAREST).resize((256, 256), Image.Resampling.NEAREST)
                if index != 52:
                    self.assertEqual(expected.tobytes(), artwork.convert("RGB").tobytes(), f"card_{index}: {source_name}")
                else:
                    colors = list(artwork.convert("RGBA").getdata())
                    self.assertGreater(sum(a == 255 and b > r and b > g for r, g, b, a in colors), 10000,
                                       "Card backs must keep their blue identity")
                    self.assertGreater(sum(a == 255 and r > 210 and g > 200 and b > 180 for r, g, b, a in colors), 1000,
                                       "Card backs must keep a readable ivory border and pattern")
                self.assertEqual(expected.size, artwork.size)
            self.assertEqual([0, 0, 16, 16], element["faces"]["north"]["uv"])
            back_key = element["faces"]["south"]["texture"][1:]
            self.assertEqual("3dcasino:item/shared/playing_cards/poker_card_back_rear", data["textures"][back_key])

    def test_wheels_have_one_filled_cap_and_closed_perimeter(self):
        def face_alpha(data, face):
            path = data["textures"][face["texture"][1:]].split(":", 1)[1]
            with Image.open(ASSETS / "textures" / (path + ".png")) as image:
                alpha = image.convert("RGBA").getchannel("A")
            return alpha.crop(tuple(round(uv * size / 16) for uv, size in
                                    zip(face["uv"], alpha.size * 2)))

        for wheel in ("wheel_of_fortune", "money_wheel"):
            for part, prefix in (("disc", "wheel_"), ("machine", "back_wheel_")):
                data = model(f"{wheel}_{part}")
                caps = [e for e in data["elements"] if e["name"] == prefix + "cap"]
                self.assertEqual(1, len(caps))
                self.assertEqual({"north", "south"}, set(caps[0]["faces"]))
                for side, face in caps[0]["faces"].items():
                    with self.subTest(wheel=wheel, part=part, face=side):
                        alpha = face_alpha(data, face)
                        width, height = alpha.size
                        for x in (0, width - 1):
                            for y in (0, height - 1):
                                self.assertEqual(0, alpha.getpixel((x, y)), "square corner outside rim")
                        self.assertEqual(255, alpha.getpixel((width // 2, height // 2)))
                        for i in range(64):
                            angle = i * math.tau / 64
                            x = int(width * (.5 + .495 * math.cos(angle)))
                            y = int(height * (.5 + .495 * math.sin(angle)))
                            self.assertGreater(alpha.getpixel((x, y)), 127, "cap stops short of rim")
                walls = [e for e in data["elements"] if e["name"].startswith(prefix + "wall_")]
                self.assertEqual(32, len(walls))
                with self.subTest(wheel=wheel, part=part, face="perimeter"):
                    for wall in walls:
                        self.assertEqual({"down"}, set(wall["faces"]))
                        self.assertEqual((255, 255), face_alpha(data, wall["faces"]["down"]).getextrema(),
                                         "transparent holes between rim segments")

    def test_rotating_wheel_walls_reach_the_housing_with_clearance(self):
        for wheel in ("wheel_of_fortune", "money_wheel"):
            with self.subTest(wheel=wheel):
                rotor = model(wheel + "_disc")["elements"]
                body = model(wheel + "_machine")["elements"]
                cap = next(e for e in rotor if e["name"] == "wheel_cap")
                housing = next(e for e in body if e["name"] == "back_wheel_cap")
                # The rotor sits 0.25 blocks forward, or -1 Z in these packed model units.
                clearance = housing["from"][2] - (cap["to"][2] - 1)
                self.assertGreater(clearance, 0)
                self.assertLess(clearance, .1)
                for elements, cover, prefix in ((rotor, cap, "wheel_wall_"), (body, housing, "back_wheel_wall_")):
                    for wall in (e for e in elements if e["name"].startswith(prefix)):
                        self.assertEqual((wall["from"][2], wall["to"][2]), (cover["from"][2], cover["to"][2]))

    def test_money_wheel_art_uses_the_same_segment_order_as_the_game(self):
        segments = (0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1)
        colors = ((82, 125, 103), (82, 125, 154), (169, 89, 80), (208, 173, 101))
        data = model("money_wheel_disc")
        cap = next(e for e in data["elements"] if e.get("name") == "wheel_cap")
        reference = data["textures"][cap["faces"]["north"]["texture"][1:]]
        with Image.open(ASSETS / "textures" / (reference.split(":", 1)[1] + ".png")) as artwork:
            for index, category in enumerate(segments):
                theta = (index + .5) * math.tau / len(segments)
                point = (int((.5 + .4 * math.sin(theta)) * artwork.width),
                         int((.5 - .4 * math.cos(theta)) * artwork.height))
                self.assertEqual(colors[category], artwork.getpixel(point)[:3], index)


if __name__ == "__main__":
    unittest.main()
