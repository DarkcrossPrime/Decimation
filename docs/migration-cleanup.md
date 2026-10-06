# Prepared migration cleanup — preview first

No user source, runtime directory or canonical content was deleted in 6.17.
This snapshot has **zero remaining eligible old platform files**: all 33 obsolete
paths in the revised manifest are already absent. Your checkout may still contain
some of those exact originals if it accumulated the earlier overlays.

The old removal list contained 61 paths and is **not safe to reuse wholesale**.
Twenty-eight paths now contain active migration code, including five preserved
data records whose bytes still match the old snapshot (`ArmRotation`,
`WeaponArmPose`, `WeaponPresentation`, `WeaponSound`, `WeaponTransform`). Identical
old bytes do not imply unused code. The revised manifest protects every current
source path and retains only the 33 already-obsolete paths and their original hashes.

Before cleanup, apply 6.19, verify your working changes and make a backup/commit.
Multiplayer/server acceptance has been confirmed by the developer; it is not a
new blocker for this cleanup. Keep `main` and
`1.20.1` untouched.

```bash
# Safe preview: works even with no .git directory. Changes nothing.
bash tools/migration/remove_legacy_sources.sh

# Optional, only after reviewing the preview; requires branch migration.
bash tools/migration/remove_legacy_sources.sh --apply

# Include only .log/.log.gz files in known development log folders.
# Quit the client/server first; preview before applying.
bash tools/migration/remove_legacy_sources.sh --logs
bash tools/migration/remove_legacy_sources.sh --apply --logs
```

The wrapper name is retained for compatibility, but **apply moves, not deletes**.
Log cleanup only covers files directly in `logs/`, `run/logs/`, `run-26.3/logs/`
and `run-server-26.3/logs/`. It never traverses saves, settings, archives or backup
directories. New log files will be produced normally on the next game launch.
Every path/hash is checked before any source moves. Modified files, duplicate or
escaping paths and source/ancestor symlinks stop cleanup. Eligible originals move
to a unique `.migration-backups/<timestamp-id>/src/...` tree, with `recovery.json`.
The backup is ignored by Git, so keep it separately if you later remove the checkout.
For recovery, copy an individual backed-up file to its recorded repository-relative
path only after checking the destination does not contain newer code. If a disk or
filesystem error interrupts the moves, already-moved files remain in that backup
and unmoved ones remain in `src`; inspect the inventory before resuming. No broad
directory removal or `git reset` is involved.

| Workspace area | Cleanup decision |
| --- | --- |
| `content/`, importers/converters, active `src/` | Keep; no generic unused-file purge |
| Original mod, Requiem and performance-mod reference ZIPs | Keep for the menu reconstruction and future comparisons |
| Old `run/`, `run-26.3/`, `run-server-26.3/` | Keep/back up; these contain saves and user settings, not just disposable output |
| `build/`, IDE `out/` | Rebuildable; Gradle `clean` removes its own build outputs when wanted |
| `.gradle/`, project JDK, wrapper and IDE project settings | Keep during migration; removing caches does not improve runtime performance |
| `main`, `1.20.1`, local commits/branches | Preserve; this script never targets them |

Keep the accepted migration checkpoint before considering further manual archive
cleanup. Future multiplayer/profiling passes are separate work, not a reason to
repeat accepted server tests now. Menus come first after migration; new vehicles, mobs,
ambiance and other unfinished systems are separate feature work, not a reason to
delete their reference material now.
