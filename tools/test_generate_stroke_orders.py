#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check the real generator and bundled source data without an Android build."""
import json
import unittest
from generate_stroke_orders import DEST, SOURCE, generate


class StrokeDataTest(unittest.TestCase):
    def setUp(self):
        self.generated = generate()
        self.rows = dict(line.split("\t") for line in self.generated.splitlines() if not line.startswith("#"))

    def test_generated_asset_is_current(self):
        self.assertEqual(DEST.read_text(), self.generated)

    def test_every_entry_uses_five_categories(self):
        self.assertEqual(len(self.rows), 6896)
        for char, code in self.rows.items():
            self.assertEqual(len(char), 1)
            self.assertTrue(code)
            self.assertLessEqual(set(code), set("12345"))

    def test_traditional_only_variants_are_excluded(self):
        variants = json.loads((SOURCE / "trad-simple.json").read_text())
        self.assertFalse(set(self.rows) & (set(variants["trad"]) - set(variants["simple"])))
        for char in "漢國門舊後發":
            self.assertNotIn(char, self.rows)
        for char in "汉国门旧后发":
            self.assertIn(char, self.rows)

    def test_known_orders_and_hook_classification(self):
        for char, code in {"一": "1", "丁": "12", "乙": "5", "九": "35", "旧": "22511", "汉": "44154", "你": "3235234"}.items():
            self.assertEqual(self.rows[char], code)

    def test_prefix_selection_preserves_candidate_indices(self):
        candidates = ["就是", "救世", "旧时", "九十", "旧", "救", "酒", "九", "舊"]
        matches = [(i, char) for i, char in enumerate(candidates) if char in self.rows and self.rows[char].startswith("2")]
        self.assertEqual(matches, [(4, "旧")])


if __name__ == "__main__":
    unittest.main()
