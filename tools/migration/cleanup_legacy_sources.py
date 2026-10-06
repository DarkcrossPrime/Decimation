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
    parser.add_argument('--logs', action='store_true', help='also archive .log/.log.gz files in known development log directories; never saves/settings')
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
    source_count = len(candidates)
    if args.logs:
        for relative_dir in ['logs', 'run/logs', 'run-26.3/logs', 'run-server-26.3/logs']:
            directory = root / relative_dir
            if any(path.is_symlink() for path in (directory, *directory.parents) if path.is_relative_to(root)):
                raise SystemExit(f'Refusing symlink log directory: {relative_dir}')
            if not directory.exists():
                continue
            if not directory.is_dir():
                raise SystemExit(f'Invalid log directory: {relative_dir}')
            for source in sorted(directory.iterdir()):
                if not (source.name.endswith('.log') or source.name.endswith('.log.gz')):
                    continue
                if source.is_symlink() or not source.is_file():
                    raise SystemExit(f'Refusing non-file or symlink log: {source.relative_to(root)}')
                relative = source.relative_to(root)
                entry = {'path': relative.as_posix(), 'sha256': hashlib.sha256(source.read_bytes()).hexdigest()}
                candidates.append((relative, source, entry))
    for relative, _, _ in candidates:
        print(f'Eligible: {relative}')
    if not args.apply:
        print(f'Preview only: {source_count} old sources eligible; {len(manifest) - source_count} already absent; {len(candidates) - source_count} logs eligible. No files changed.')
        return
    branch = subprocess.run(['git', '-C', str(root), 'branch', '--show-current'], text=True, capture_output=True)
    if branch.returncode or branch.stdout.strip() != 'migration':
        raise SystemExit('Apply requires a Git worktree on branch migration. Nothing moved.')
    if not candidates:
        print('No eligible sources/logs remain. Nothing moved.')
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
    print(f'Moved {source_count} old sources and {len(candidates) - source_count} logs to {backup.relative_to(root)}. No files deleted; content/saves/settings untouched.')


if __name__ == '__main__':
    main()
