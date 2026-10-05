#!/usr/bin/env python3
"""Preview archived platform sources; --apply backs up exact originals on migration."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='move candidates to .migration-backups; migration branch required')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    manifest = json.loads((root / 'tools/migration/legacy-source-removals.json').read_text())
    candidates = []
    seen = set()
    for entry in manifest:
        relative = Path(entry['path'])
        if relative.is_absolute() or '..' in relative.parts or not relative.parts or relative.parts[0] != 'src':
            raise SystemExit('Invalid archived source path')
        if relative in seen:
            raise SystemExit(f'Duplicate archived source path: {relative}')
        seen.add(relative)
        source = root / relative
        if any(path.is_symlink() for path in (source, *source.parents) if path.is_relative_to(root)):
            raise SystemExit(f'Refusing symlink: {relative}')
        if not source.exists():
            continue
        if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != entry['sha256']:
            raise SystemExit(f'Changed file; nothing moved. Review manually: {relative}')
        candidates.append((relative, source, entry))
    for relative, _, _ in candidates:
        print(f'Eligible: {relative}')
    if not args.apply:
        print(f'Preview only: {len(candidates)} eligible; {len(manifest) - len(candidates)} already absent. No files changed.')
        return
    branch = subprocess.run(['git', '-C', str(root), 'branch', '--show-current'], text=True, capture_output=True)
    if branch.returncode or branch.stdout.strip() != 'migration':
        raise SystemExit('Apply requires a Git worktree on branch migration. Nothing moved.')
    if not candidates:
        print('No archived sources remain. Nothing moved.')
        return
    backup_root = root / '.migration-backups'
    if backup_root.is_symlink() or backup_root.exists() and not backup_root.is_dir():
        raise SystemExit('Unsafe backup directory. Nothing moved.')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = backup_root / f'{stamp}-{uuid.uuid4().hex[:12]}'
    backup.mkdir(parents=True, exist_ok=False)
    (backup / 'recovery.json').write_text(json.dumps([entry for _, _, entry in candidates], indent=2) + '\n')
    for relative, source, entry in candidates:
        target = backup / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if any(path.is_symlink() for path in (source, *source.parents) if path.is_relative_to(root)) or hashlib.sha256(source.read_bytes()).hexdigest() != entry['sha256']:
            raise SystemExit(f'File changed during cleanup: {relative}. Prior moves remain recoverable in {backup}')
        source.rename(target)
    print(f'Moved {len(candidates)} archived sources to {backup.relative_to(root)}. No files deleted; content/runtime untouched.')


if __name__ == '__main__':
    main()
