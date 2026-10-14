#!/usr/bin/env python3
"""Preview a fixed warband source restore; apply only unchanged migration output while stopped."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile


def digest(value):
    return hashlib.sha256(value).hexdigest()


def force_directory(directory):
    descriptor = os.open(directory, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def rollback(path, apply=False, backup=None):
    path = Path(path).resolve()
    journal = path.parent / 'character-identity-v1' / path.name
    archived = json.loads((journal / 'source.json').read_text(encoding='utf-8'))
    plan = json.loads((journal / 'plan.json').read_text(encoding='utf-8'))
    original = archived['source'].encode('utf-8')
    if digest(original) != archived['hash'] or plan['hash'] != archived['hash']:
        raise ValueError('Immutable source/plan hash conflict')
    before = json.loads(original)
    planned = plan['after'].encode('utf-8')
    if digest(planned) != plan['afterHash']:
        raise ValueError('Fixed output hash conflict')
    after = json.loads(planned)
    if after.get('character-identity-version') != 1 or before.get('id') != after.get('id'):
        raise ValueError('Invalid fixed identity plan')
    current = path.read_bytes()
    json.loads(current)
    changed = digest(current) != plan['afterHash']
    report = {'file': str(path), 'source_hash': archived['hash'],
              'original_members': len(before.get('memberIds') or []),
              'original_invitations': len(before.get('invitedIds') or []),
              'changed_since_plan': changed, 'already_restored': current == original,
              'applied': False}
    if not apply:
        return report
    if changed:
        raise ValueError('Current warband differs from fixed output; reconcile post-migration changes manually')
    if backup is None:
        raise ValueError('--backup requires a new current-state recovery path')
    backup = Path(backup).resolve()
    with backup.open('xb') as recovery:
        recovery.write(current)
        recovery.flush()
        os.fsync(recovery.fileno())
    force_directory(backup.parent)
    if path.read_bytes() != current:
        raise ValueError('Warband changed during rollback; current-state backup retained')
    descriptor, pending = tempfile.mkstemp(prefix=path.name + '.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(original)
            output.flush()
            os.fsync(output.fileno())
        os.replace(pending, path)
        force_directory(path.parent)
    finally:
        if os.path.exists(pending):
            os.unlink(pending)
    report.update(applied=True, backup=str(backup))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('warband', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--backup', type=Path)
    arguments = parser.parse_args()
    print(json.dumps(rollback(arguments.warband, arguments.apply, arguments.backup), indent=2))


if __name__ == '__main__':
    main()
