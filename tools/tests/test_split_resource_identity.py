"""Standalone resource boundaries independent of the companion plugin."""
import json
import hashlib
import unittest
from pathlib import Path
from tools.package_resource_pack import validate_assets

ROOT = Path(__file__).resolve().parents[2]


class SplitResourceIdentityTest(unittest.TestCase):
    def test_all_registered_items_are_owned_casino_models(self):
        models = validate_assets(ROOT / "resource-pack")
        index = json.loads((ROOT / "src/main/resources/vanilla-models/index.json").read_text())
        self.assertEqual({key.removeprefix("casino/") for key in index if key.startswith("casino/")}, set(models))
        self.assertFalse(any(key.startswith("tabletop/") for key in index))
        self.assertEqual(105, len(models))

    def test_client_definitions_never_refer_to_companion_namespace(self):
        assets = ROOT / "resource-pack/assets"
        self.assertEqual(["3dcasino"], sorted(path.name for path in assets.iterdir() if path.is_dir()))
        for path in assets.rglob("*.json"):
            self.assertNotIn("3dgames:", path.read_text(), path)
        sounds = json.loads((assets / "3dcasino/sounds.json").read_text())
        self.assertTrue({"cards.deal", "machine.button", "machine.latch", "wheel.tick", "ball.contact", "ball.land", "result.win", "result.loss"}.issubset(sounds))
        names = [sample["name"] for sample in sounds["cards.deal"]["sounds"]]
        self.assertEqual(1, len(names))
        self.assertEqual("3dcasino:cards/deal/card_deal", names[0])
        self.assertTrue((assets / "3dcasino/sounds/cards/deal/card_deal.ogg").is_file())

    def test_no_image_menu_assets_are_bundled(self):
        assets = ROOT / "resource-pack/assets/3dcasino"
        self.assertFalse((assets / "textures/menu").exists())
        self.assertFalse(any((assets / "font").glob("*.json")))

    def test_deal_sample_is_self_contained_with_its_recorded_cc0_source(self):
        provenance = json.loads((ROOT / "resource-pack/sources.json").read_text(encoding="utf-8"))
        entries = [item for item in provenance["approved_audio"]["samples"]
                   if item["event"] == "cards.deal"]
        self.assertEqual(1, len(entries), "The bundled card sound must retain its own source record")
        entry = entries[0]
        original = (ROOT / entry["file"]).read_bytes()
        sample = (ROOT / entry["derived_file"]).read_bytes()
        identification = sample.index(b"\x01vorbis")
        self.assertEqual(1, sample[identification + 11], "The positional deal sample must be mono")
        self.assertEqual(entry["sha256"], hashlib.sha256(original).hexdigest())
        self.assertEqual(entry["derived_sha256"], hashlib.sha256(sample).hexdigest())
        self.assertIn("CC0-1.0", entry["license"])
        self.assertTrue(sample.startswith(b"OggS"))

    def test_native_card_heads_do_not_need_companion_resources(self):
        native = ROOT / "src/main/resources/vanilla-models"
        self.assertTrue((native / "shared/cards/head_textures.json").is_file())
        index = json.loads((native / "index.json").read_text())
        for path in index.values():
            self.assertTrue((native / path).is_file(), path)

    def test_provenance_only_describes_files_present_in_this_repository(self):
        provenance = json.loads((ROOT / "resource-pack/sources.json").read_text(encoding="utf-8"))
        for entry in provenance["files"]:
            self.assertTrue((ROOT / entry["file"]).is_file(), entry["file"])
            self.assertNotIn("/tabletop/", entry["file"])
        self.assertEqual(["approved-audio.txt", "kenney-casino.txt", "kenney-impact.txt", "kenney-interface.txt", "physical-audio.md", "playing-cards.txt"],
                         sorted(p.name for p in (ROOT / "resource-pack/licenses").iterdir()))
        self.assertFalse((ROOT / "src/main/resources/META-INF/NOTICE-board-rules.txt").exists())
