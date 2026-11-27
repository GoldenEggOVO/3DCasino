"""Cabinet load paths and exposed control faces in machine-local world units."""
import math
import unittest
from itertools import product

from tools.tests.test_casino_mounts import joined_to_ground, overlaps, pose, shapes


class CasinoSupportContinuityTest(unittest.TestCase):
    def test_duck_selection_console_has_a_load_path_to_the_floor(self):
        for packed in (False, True):
            body = shapes("duck_race_machine", packed)
            for name in ("select_control_dock", "select_control_sill"):
                with self.subTest(packed=packed, part=name):
                    self.assertTrue(joined_to_ground(body, body[name]),
                                    "Selection console floats apart from the cabinet")

    def test_duck_start_pedestal_reaches_the_floor(self):
        for packed in (False, True):
            with self.subTest(packed=packed):
                pedestal = shapes("duck_race_machine", packed)["start_pedestal"]
                self.assertLessEqual(min(p[1] for p in pedestal), 1e-7,
                                     "Start pedestal has a gap below its foot")

    def test_slots_tilted_dock_reaches_its_grounded_shelf(self):
        for packed in (False, True):
            body = shapes("slots_machine", packed)
            with self.subTest(packed=packed):
                self.assertTrue(overlaps(body["control_dock"], body["control_shelf"]),
                                "Tilted control dock floats above the shelf")
                self.assertTrue(joined_to_ground(body, body["control_dock"]))

    def test_plinko_launch_console_brackets_reach_the_lower_body(self):
        for packed in (False, True):
            body = shapes("plinko_machine", packed)
            for name in ("launch_bracket_l", "launch_bracket_r"):
                with self.subTest(packed=packed, part=name):
                    self.assertTrue(overlaps(body[name], body["lower_body"]),
                                    "Launch bracket stops short of the lower body")
            for name in ("launch_dock", "front_control"):
                with self.subTest(packed=packed, part=name):
                    self.assertTrue(joined_to_ground(body, body[name]),
                                    "Launch console has no load path to the floor")

    def test_keno_front_identity_hangs_from_the_grounded_chassis(self):
        for packed in (False, True):
            body = shapes("keno_machine", packed)
            with self.subTest(packed=packed):
                self.assertTrue(joined_to_ground(body, body["front_identity"]),
                                "Front identity plate has no cabinet attachment")

    def test_supports_and_tilted_parts_match_between_rendering_modes(self):
        changed = {
            "duck_race": ("select_control_dock", "select_control_sill", "start_pedestal",
                          "select_bracket_l", "select_bracket_r"),
            "slots": ("control_shelf", "control_dock"),
            "plinko": ("launch_bracket_l", "launch_bracket_r", "launch_dock", "front_control"),
            "keno": ("front_identity", "identity_bracket_l", "identity_bracket_r"),
        }
        for game, names in changed.items():
            native, packed = shapes(game + "_machine"), shapes(game + "_machine", True)
            for name in names:
                with self.subTest(game=game, part=name):
                    self.assertIn(name, tuple(native))
                    self.assertIn(name, tuple(packed))
                    for first, second in ((native[name], packed[name]), (packed[name], native[name])):
                        for point in first:
                            self.assertLess(min(math.dist(point, other) for other in second), 2e-6)

    def test_supports_leave_control_faces_and_their_front_approaches_clear(self):
        # BuiltinMachineLayouts fixtures before the common machine scale. Press is
        # applied along local -Z before the button rotation, as in buttonPose.
        controls = [
            ("duck_race", "duck_race_button_" + str(i + 1), -.9 + i * .6,
             .78, 1.18, .5, -35, .8, .04) for i in range(4)
        ] + [
            ("duck_race", "casino_button_play", 1.43, 1.05, .9, .65, -35, 1.2, .04),
            ("slots", "casino_button_play", 0, .84, .76, 1.5, -35, .75, .04),
            ("plinko", "casino_button_play", 0, .30, .79, .90 / .75, -25, .65 / .75, .055 / .75),
            ("keno", "casino_button_play", 0, .43, 1.22, .55, -35, .8, .04),
        ]
        supports = {
            "duck_race": ("select_bracket_l", "select_bracket_r", "start_pedestal"),
            "slots": ("control_shelf",),
            "plinko": ("launch_bracket_l", "launch_bracket_r"),
            "keno": ("identity_bracket_l", "identity_bracket_r"),
        }
        for packed in (False, True):
            for game, model, x, y, z, width, pitch, size, press in controls:
                body = shapes(game + "_machine", packed)
                face = shapes(model, packed)["button_face" if packed else "face"]
                scaled = pose(face, scale=(width / .6, size, size))
                bounds = [(min(p[i] for p in scaled), max(p[i] for p in scaled)) for i in range(3)]
                bounds[2] = (bounds[2][0], bounds[2][1] + .20)
                for travel in (0, press):
                    approach = [[a, b, c - travel] for a, b, c in product(*bounds)]
                    approach = pose(approach, (x, y, z), math.radians(pitch))
                    for name in supports[game]:
                        if name in body:
                            with self.subTest(packed=packed, game=game, button=model,
                                              support=name, press=travel):
                                self.assertFalse(overlaps(approach, body[name]),
                                                 "Support obscures the control face or its front approach")


if __name__ == "__main__":
    unittest.main()
