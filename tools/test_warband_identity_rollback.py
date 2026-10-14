import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('warband_rollback', Path(__file__).with_name('warband-identity-rollback.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class WarbandRollbackTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.path = Path(self.temporary.name) / 'warband_one.json'
        self.before = '{"id":"one","memberIds":["account"],"extension":{"null":null}}\n'
        self.after = dict(json.loads(self.before), **{'character-identity-version': 1,
                'memberCharacters': {'account': {'ownerId': 'account', 'characterId': 'character'}}})
        self.path.write_text(json.dumps(self.after), encoding='utf-8')
        self.journal = self.path.parent / 'character-identity-v1' / self.path.name
        self.journal.mkdir(parents=True)
        fingerprint = module.digest(self.before.encode())
        (self.journal / 'source.json').write_text(json.dumps({'source': self.before, 'hash': fingerprint}))
        planned = self.path.read_text()
        (self.journal / 'plan.json').write_text(json.dumps({'hash': fingerprint,
                'afterHash': module.digest(planned.encode()), 'after': planned}))

    def tearDown(self):
        self.temporary.cleanup()

    def test_preview_and_apply_restore_exact_original_and_preserve_recovery_bytes(self):
        current = self.path.read_bytes()
        preview = module.rollback(self.path)
        self.assertFalse(preview['changed_since_plan'])
        self.assertFalse(preview['applied'])
        self.assertEqual(current, self.path.read_bytes())
        backup = self.path.with_name('current.json')
        self.assertTrue(module.rollback(self.path, True, backup)['applied'])
        self.assertEqual(self.before.encode(), self.path.read_bytes())
        self.assertEqual(current, backup.read_bytes())
        self.assertTrue(module.rollback(self.path)['already_restored'])

    def test_changed_role_progress_or_null_metadata_blocks_restore(self):
        for field, value in [('memberIds', []), ('name', 'new'), ('extension', {'null': 'changed'})]:
            changed = dict(self.after, **{field: value})
            current = json.dumps(changed).encode()
            self.path.write_bytes(current)
            self.assertTrue(module.rollback(self.path)['changed_since_plan'])
            with self.assertRaisesRegex(ValueError, 'differs'):
                module.rollback(self.path, True, self.path.with_name('current.json'))
            self.assertEqual(current, self.path.read_bytes())

    def test_corrupt_archive_and_existing_or_missing_backup_are_refused(self):
        with self.assertRaisesRegex(ValueError, '--backup'):
            module.rollback(self.path, True)
        backup = self.path.with_name('current.json')
        backup.write_text('keep')
        with self.assertRaises(FileExistsError):
            module.rollback(self.path, True, backup)
        self.assertEqual('keep', backup.read_text())
        journal_path = self.journal / 'plan.json'
        plan = json.loads(journal_path.read_text())
        original_plan = journal_path.read_text()
        plan['after'] += ' '
        journal_path.write_text(json.dumps(plan))
        with self.assertRaisesRegex(ValueError, 'output hash conflict'):
            module.rollback(self.path)
        journal_path.write_text(original_plan)
        archive = json.loads((self.journal / 'source.json').read_text())
        archive['source'] = '{}'
        (self.journal / 'source.json').write_text(json.dumps(archive))
        with self.assertRaisesRegex(ValueError, 'hash conflict'):
            module.rollback(self.path)


if __name__ == '__main__':
    unittest.main()
