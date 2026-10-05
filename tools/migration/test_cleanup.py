#!/usr/bin/env python3
"""Exercises cleanup only inside disposable, isolated worktrees."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class CleanupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='decimation-cleanup-test-')
        self.root = Path(self.temp.name)
        tools = self.root / 'tools/migration'
        tools.mkdir(parents=True)
        shutil.copy2(Path(__file__).with_name('cleanup_legacy_sources.py'), tools)
        self.script = tools / 'cleanup_legacy_sources.py'
        self.source = self.root / 'src/main/java/Archived.java'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('archived')
        self.entry = {'path': self.source.relative_to(self.root).as_posix(), 'sha256': hashlib.sha256(b'archived').hexdigest()}
        self.manifest = tools / 'legacy-source-removals.json'
        self.manifest.write_text(json.dumps([self.entry]))

    def tearDown(self):
        self.temp.cleanup()

    def run_cleanup(self, *args):
        return subprocess.run(['python3', str(self.script), *args], capture_output=True, text=True)

    def git(self, *args):
        subprocess.run(['git', '-C', str(self.root), *args], check=True, capture_output=True)

    def test_preview_and_branch_gate(self):
        self.assertEqual(self.run_cleanup().returncode, 0)
        self.assertTrue(self.source.exists())
        self.assertFalse((self.root / '.migration-backups').exists())
        self.assertNotEqual(self.run_cleanup('--apply').returncode, 0)
        self.git('init', '-b', 'main')
        self.assertNotEqual(self.run_cleanup('--apply').returncode, 0)
        self.assertTrue(self.source.exists())

    def test_recoverable_apply(self):
        self.git('init', '-b', 'migration')
        self.assertEqual(self.run_cleanup('--apply').returncode, 0)
        self.assertFalse(self.source.exists())
        backups = list((self.root / '.migration-backups').iterdir())
        self.assertEqual(len(backups), 1)
        self.assertEqual((backups[0] / self.entry['path']).read_text(), 'archived')
        self.assertEqual(json.loads((backups[0] / 'recovery.json').read_text()), [self.entry])
        self.assertEqual(self.run_cleanup('--apply').returncode, 0)
        self.assertEqual(len(list((self.root / '.migration-backups').iterdir())), 1)

    def test_whole_manifest_preflight(self):
        self.git('init', '-b', 'migration')
        other = self.source.with_name('Changed.java')
        other.write_text('modified')
        self.manifest.write_text(json.dumps([self.entry, {**self.entry, 'path': other.relative_to(self.root).as_posix()}]))
        self.assertNotEqual(self.run_cleanup('--apply').returncode, 0)
        self.assertTrue(self.source.exists())
        self.assertTrue(other.exists())
        self.assertFalse((self.root / '.migration-backups').exists())

    def test_path_escape_and_symlinks(self):
        for path in ['../outside', '/absolute', 'content/weapon.obj']:
            self.manifest.write_text(json.dumps([{**self.entry, 'path': path}]))
            self.assertNotEqual(self.run_cleanup().returncode, 0)
        self.manifest.write_text(json.dumps([self.entry]))
        original = self.source.with_name('preserved')
        self.source.rename(original)
        self.source.symlink_to(original)
        self.assertNotEqual(self.run_cleanup().returncode, 0)
        self.assertEqual(original.read_text(), 'archived')

    def test_ancestor_and_backup_symlink(self):
        actual = self.root / 'preserved-tree'
        (self.root / 'src').rename(actual)
        (self.root / 'src').symlink_to(actual, target_is_directory=True)
        self.assertNotEqual(self.run_cleanup().returncode, 0)
        self.assertEqual((actual / 'main/java/Archived.java').read_text(), 'archived')
        (self.root / 'src').unlink()
        actual.rename(self.root / 'src')
        self.git('init', '-b', 'migration')
        (self.root / '.migration-backups').symlink_to(actual, target_is_directory=True)
        self.assertNotEqual(self.run_cleanup('--apply').returncode, 0)
        self.assertTrue(self.source.exists())

    def test_duplicate_manifest(self):
        self.manifest.write_text(json.dumps([self.entry, self.entry]))
        self.assertNotEqual(self.run_cleanup().returncode, 0)
        self.assertTrue(self.source.exists())


if __name__ == '__main__':
    unittest.main()
