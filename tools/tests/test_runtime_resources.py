#!/usr/bin/env python3
"""Resource generation must not destroy sources, unrelated files or a working pack."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_runtime_resources import build


class RuntimeOutputSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.content = self.root / "content"
        self.content.mkdir()
        self.source = self.content / "source.txt"
        self.source.write_text("canonical source")

    def test_overlapping_output_preserves_sources(self):
        for output in [self.content, self.content / "generated", self.root]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                build(self.content, output)
            self.assertEqual(self.source.read_text(), "canonical source")
        self.assertFalse((self.content / "generated").exists())

    def test_output_symlink_cannot_bypass_overlap_guard(self):
        output = self.root / "link"
        output.symlink_to(self.content, target_is_directory=True)
        with self.assertRaises(ValueError):
            build(self.content, output)
        self.assertEqual(self.source.read_text(), "canonical source")

    def test_unknown_output_preserves_existing_files(self):
        output = self.root / "src"
        output.mkdir()
        source = output / "valuable.java"
        source.write_text("user source")
        with self.assertRaises(ValueError):
            build(self.content, output)
        self.assertEqual(source.read_text(), "user source")

    def test_missing_content_preserves_existing_output(self):
        with self.assertRaises(ValueError):
            build(self.root / "missing", self.content)
        self.assertEqual(self.source.read_text(), "canonical source")

    def test_failed_conversion_preserves_complete_pack(self):
        output = self.root / "generated"
        index = output / "assets/decimation/content/index.json"
        index.parent.mkdir(parents=True)
        index.write_text(json.dumps({"format": "decimation:content_index"}))
        previous = output / "previous.txt"
        previous.write_text("working pack")
        object_root = self.content / "object"
        object_root.mkdir()
        (object_root / "definition.json").write_text(json.dumps({
            "id": "object", "assets": ["missing.png"],
        }))
        with self.assertRaises(FileNotFoundError):
            build(self.content, output)
        self.assertEqual(previous.read_text(), "working pack")
        self.assertTrue(index.is_file())
        self.assertEqual(list(self.root.glob(".generated-*")), [])


if __name__ == "__main__":
    unittest.main()
