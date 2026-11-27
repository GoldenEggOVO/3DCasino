import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / 'src/main/resources/vanilla-models/casino'
PACK = ROOT / 'resource-pack/assets/3dcasino/models/item/casino'
HEADERS = ('plinko', 'crash', 'slots', 'dragon_tower')

def load(path):
    return json.loads(path.read_text(encoding='utf-8'))

def native(game, compact=False):
    return {p['name']:p for p in load(NATIVE/game/('machine_compact.json' if compact else 'machine.json'))['blocks']}

def packed(game, compact=False):
    return load(PACK/game/(game+'_machine'+('_compact' if compact else '')+'.json'))

def bound(part, axis, high):
    return part['position'][axis] + (.5 if high else -.5)*part['size'][axis]

class CasinoRefinementTest(unittest.TestCase):
    def test_header_lamps_have_exposed_fronts_and_matching_packed_elements(self):
        for game,compact in [(g,False) for g in HEADERS]+[('dragon_tower',True)]:
            with self.subTest(game=game,compact=compact):
                parts=native(game,compact)
                lamp,cover=parts['header_light'],parts['canopy']
                self.assertGreaterEqual(bound(lamp,2,True)-bound(cover,2,True),.00049)
                self.assertLess(bound(lamp,2,False),bound(cover,2,True))
                data=packed(game,compact)
                element=next((p for p in data['elements'] if p['name']=='header_light'),None)
                self.assertIsNotNone(element)
                expected_from=[8-16*bound(lamp,0,True),8+16*bound(lamp,1,False),8-16*bound(lamp,2,True)]
                expected_to=[8-16*bound(lamp,0,False),8+16*bound(lamp,1,True),8-16*bound(lamp,2,False)]
                for actual,expected in zip(element['from']+element['to'],expected_from+expected_to):
                    self.assertAlmostEqual(actual,expected,places=6)
                self.assertIn('north',element['faces'])
                texture=data['textures'][element['faces']['north']['texture'][1:]]
                self.assertTrue(texture.endswith('/future_accent'))
                self.assertEqual(15,element.get('light_emission'))

    def test_finish_pylon_lamps_connect_while_preserving_their_fronts(self):
        parts=native('duck_race')
        packed_parts={p['name']:p for p in packed('duck_race')['elements']}
        for side in ('l','r'):
            with self.subTest(side=side):
                lamp,pillar=parts['pylon_light_'+side],parts['finish_pylon_'+side]
                self.assertLessEqual(bound(lamp,2,False),bound(pillar,2,True))
                self.assertAlmostEqual(-.585,bound(lamp,2,True),places=8)
                for axis in (0,1):
                    self.assertLess(bound(lamp,axis,False),bound(pillar,axis,True))
                    self.assertGreater(bound(lamp,axis,True),bound(pillar,axis,False))
                element=packed_parts['pylon_light_'+side]
                self.assertAlmostEqual(17.36,element['from'][2],places=6)
                self.assertAlmostEqual(17.604,element['to'][2],places=6)

    def test_score_panel_frame_stands_proud_of_the_screen(self):
        parts={p['name']:p for p in load(NATIVE/'blackjack/score_panel.json')['blocks']}
        pp={p['name']:p for p in load(PACK/'blackjack/blackjack_score_panel.json')['elements']}
        for name in ('screen_trim_left','screen_trim_right','screen_trim_lower','screen_trim_upper'):
            with self.subTest(part=name):
                trim=parts[name]
                self.assertAlmostEqual(-.2025,bound(trim,2,False),places=8)
                self.assertAlmostEqual(.0015,bound(trim,2,True)-bound(parts['screen'],2,True),places=8)
                self.assertAlmostEqual(11.116,pp[name]['from'][2],places=6)
                self.assertAlmostEqual(11.24,pp[name]['to'][2],places=6)

    def test_compact_dragon_has_no_fully_enclosed_backdrop(self):
        self.assertNotIn('tower_back',native('dragon_tower',True))
        self.assertNotIn('tower_back',{p['name'] for p in packed('dragon_tower',True)['elements']})

if __name__=='__main__':
    unittest.main()
