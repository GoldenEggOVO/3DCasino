import unittest
from pathlib import Path

from PIL import Image

from tools.tests.test_casino_models import BODIES
from tools.tests.test_casino_resource_pack import ASSETS, NATIVE, model


def face_colors(data, part):
    colors = set()
    for face in part['faces'].values():
        reference = data['textures'][face['texture'][1:]]
        image = Image.open(ASSETS / 'textures' / (reference.split(':', 1)[1] + '.png')).convert('RGBA')
        colors.update(pixel for pixel in image.get_flattened_data() if pixel[3] > 0)
    return colors


class CasinoFuturePaletteTest(unittest.TestCase):
    def test_shell_chassis_and_lamps_resolve_to_their_visible_colors(self):
        colors = {'WHITE_CONCRETE': (225, 232, 237, 255), 'SMOOTH_QUARTZ': (225, 232, 237, 255),
                  'BLACK_CONCRETE': (30, 35, 43, 255), 'LIGHT_BLUE_CONCRETE': (35, 148, 205, 255)}
        for name in BODIES:
            data = model(name)
            native = {part['name']: part for part in NATIVE[name]['boxes']}
            for part in data['elements']:
                part_name = part.get('name', '')
                if part_name not in native or part_name.startswith(('lane_', 'finish_')):
                    continue
                material = native[part_name]['material']
                if material not in colors:
                    continue
                if material == 'BLACK_CONCRETE' and part_name not in (
                        'plinth', 'lower_body', 'rear_shell', 'base', 'lower_chassis', 'slot_floor',
                        'foot_-1.31', 'foot_1.31', 'foot_-1.46', 'foot_1.46', 'foot_-1.61', 'foot_1.61',
                        'console_bracket', 'console_bracket_l', 'console_bracket_r', 'launch_bracket_l',
                        'launch_bracket_r', 'start_pedestal', 'control_sill', 'select_control_sill',
                        'multiplier_console_support'):
                    continue
                with self.subTest(model=name, part=part_name):
                    self.assertEqual({colors[material]}, face_colors(data, part))
                    if material == 'LIGHT_BLUE_CONCRETE':
                        self.assertGreaterEqual(part.get('light_emission', 0), 6)

    def test_no_casino_surface_keeps_the_retired_warm_furniture_materials(self):
        retired = {'wood', 'warm_bronze', 'warm_ivory', 'warm_wheel_back'}
        for name in NATIVE:
            data = model(name)
            used = {data['textures'][face['texture'][1:]]
                    for part in data['elements'] for face in part['faces'].values()}
            with self.subTest(model=name):
                self.assertFalse(retired.intersection(Path(reference).name for reference in used))

    def test_wheel_backings_keep_transparent_corners_and_dark_filled_centers(self):
        for name, cap in (('money_wheel_machine', 'back_wheel_cap'),
                          ('wheel_of_fortune_machine', 'back_wheel_cap')):
            data = model(name)
            part = next(part for part in data['elements'] if part.get('name') == cap)
            reference = data['textures'][next(iter(part['faces'].values()))['texture'][1:]]
            with Image.open(ASSETS / 'textures' / (reference.split(':', 1)[1] + '.png')) as image:
                self.assertEqual(0, image.convert('RGBA').getpixel((0, 0))[3])
                self.assertEqual((30, 35, 43, 255), image.convert('RGBA').getpixel((image.width // 2, image.height // 2)))

    def test_only_the_decorative_frame_changes_on_function_colored_buttons(self):
        for name in ('duck_race_button_1', 'duck_race_button_2', 'duck_race_button_3', 'duck_race_button_4',
                     'money_wheel_button_2x', 'money_wheel_button_3x', 'money_wheel_button_5x', 'money_wheel_button_10x'):
            data = model(name)
            parts = {part['name']: part for part in data['elements']}
            with self.subTest(model=name):
                reference = data['textures'][next(iter(parts['light_plate']['faces'].values()))['texture'][1:]]
                self.assertTrue(reference.endswith(('/gold_light', '/cyan_light', '/magenta_light', '/lime_light',
                                                    '/blue_light', '/red_light')))
                self.assertEqual({(30, 35, 43, 255)}, face_colors(data, parts['socket']))
                self.assertEqual({(225, 232, 237, 255)}, face_colors(data, parts['button_face']))


if __name__ == '__main__':
    unittest.main()
