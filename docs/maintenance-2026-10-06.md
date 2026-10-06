# Maintenance pass — 6 October 2026

Applies over the supplied `latest(1).zip`, checkpoint `0.2.0-migration.6.19`.
The overlay contains only changed/new files, preserving repository-relative paths.
Extract at the repository root. No source deletion is required.

## Fixed

| Finding | Change | Result |
| --- | --- | --- |
| Main GitHub workflow used Java 17 | Set its step and JDK version to 25 | Matches Java toolchain, bytecode target, Fabric API and mod metadata |
| Audio conversion relied on the runner's preinstalled tools | Both workflows explicitly install ffmpeg | Positional sample downmixing has its declared dependency |
| 210 MTL links retained paths from before content reorganization | Point to the existing texture in the same owning object and original item/model role | Material inspection/export paths resolve |
| Six sound definitions used `sound/sound/...` IDs despite living under `sound/...` | Correct IDs and matching import-map/sound-event references | Runtime ownership paths agree with the canonical folders |
| Duplicate review contained old filenames | Refresh paths, retain existing dispositions, review three identical FAMAS/custom-FAMAS audio pairs | All 142 exact duplicate groups are accounted for |
| Resource compiler recursively removed its output before validating the path or completing conversion | Reject source/output overlap and unknown nonempty destinations; stage conversion, rename completed output into place, restore old output if publication fails | A mistaken path or conversion error preserves sources and the previous pack |
| Build accepted findings in the old migration baseline | `check` now uses strict `auditContent` | Any content finding, including recurrence of an old error, fails the build |

- No OBJ, PNG, OGG, DANIM or weapon-definition bytes changed.
- No gameplay, network protocol, arm offsets or renderer code changed.
- Material repairs preserve the texture selected by the original exporter; they do not reselect artwork or certify its visual quality.
- The frozen migration audit baseline stays unchanged: 217 resolved, zero remaining, zero new.
- New output-safety tests are part of Gradle `check` through `verifyRuntimeResourceSafety`.
- `README.md` now describes the current audit and resource-generation behavior.

## Java requirement

- The screenshot's Loom error requires at least Java 21.
- This repository requires **Java 25**: `build.gradle` selects toolchain/release 25, `fabric.mod.json` requires 25, and the cached Minecraft 26.3 manifest and pinned Fabric API also require 25.
- Setting CI to 21 would only clear the first error; it would not meet the complete build requirement.

## Backups and cleanup

- The current cleanup script writes `.migration-backups/`, plural.
- Only rollback copies of exact obsolete sources and selected development logs go there.
- No build, source set or runtime code reads those backups.
- They may be permanently discarded after the accepted migration if recovery is no longer needed.
- The uploaded archive contains neither `.migration-backups/` nor the singular `.migration-backup`; the contents of a differently named local folder were not inspected.

From the repository root, to discard the cleanup script's rollback copies:

```bash
rm -rf -- .migration-backups/
```

This does not remove active sources, canonical assets, development worlds or settings.

## Remaining findings and priorities

### Menu work: missing reference audio

The generator excludes six genuinely absent sample references. These were already
missing; their absence is separate from the repaired sound ownership IDs.

| Event | Missing reference |
| --- | --- |
| `entity.mortar.conceal` | `entity/mortar/conceal` |
| `gui.menu.music_battlegrounds` | `gui/menu/music_battlegrounds` |
| `item.rocket.hit` | `items/rocket/hit` |
| `mob.human.beep` | `mob/human/beep` |
| `mob.human.clap` | `mob/human/clap7` |
| `vehicle.btr70.reverse` | `vehicle/btr70/reverse` |

- Recover the original menu music or choose its replacement when implementing menus.
- Keep the five other gaps recorded for their respective systems.
- The four current weapons have their required sounds: 27 events, 28 mono samples.
- A zero-error structural content audit does not mean all archived audio references exist; missing-sample placeholders are metadata, and the generator filters them.

### Distribution size

- Generated resources: **235,997,944 bytes**, 6,212 files, before JAR compression.
- The compiler packages all 1,291 imported objects, 233 OBJ models and 388 animations.
- Only four weapons and three ammo items are registered by the current gameplay module.
- Before distributing builds, decide whether to ship the full asset library or select runtime assets by enabled systems.
- Keep canonical assets and reference archives: menus and later features still need them.

### Rendering and performance

- Current weapons bake to two draw groups each; there is no per-frame OBJ/DANIM parsing.
- Geometry still streams through Minecraft's vertex consumers; no persistent weapon GPU mesh was added.
- Body extraction creates captured model/pose snapshots every frame.
- Profile frame time, allocations and GPU submission before changing those paths. This pass makes no FPS claim.
- The historical atlas-initialization fault is already covered by the current bake implementation and its regression suite; it was not a remaining source bug in this archive.

### CI and ongoing maintenance

- Generic and migration workflows both run on migration pushes/PRs: duplicate builds remain until the team retires the migration workflow.
- Migration documentation remains historical reference. Its older statements about 217 known findings describe the pre-maintenance checkpoint.
- Preserve the existing multiplayer contract checks as features grow; they do not replace dedicated-server/client play tests.
- This pass did not introduce menu screens, new joints, scopes, health/armor systems, vehicles or other feature work.

## Verification

| Check | Result |
| --- | --- |
| Main/client/test source compilation on Java 25 | Pass: 41 main, 45 client, 8 test sources |
| Strict content audit | Pass: zero errors |
| Migration audit against unchanged baseline | Pass: 217 resolved, zero new |
| Resource generation | Pass: all resources generated; eight stereo weapon samples downmixed |
| Weapon catalogue | Pass: four weapons, three ammo items, 15 malformed catalogues rejected |
| Weapon behavior | Pass: 4,222 assertions |
| Persistent/network codecs | Pass: 204 assertions |
| Audio | Pass: 620 assertions |
| Weapon visuals, including baking before atlas upload | Pass: 407,986 assertions |
| Captured first-person body | Pass: 44,837 assertions |
| Multiplayer contracts | Pass: 4,094 assertions |
| Common/server linkage boundary | Pass: 47 compiled common classes |
| Both pure arm-solver programs | Pass |
| Migration cleanup safety | Pass: eight isolated tests |
| Resource output safety | Pass: five tests, including overlapping paths, symlinks, unknown destinations and conversion failure |
| Unchanged asset/weapon bytes | Pass against the uploaded archive |

- Compilation and Java suites used cached Minecraft 26.3, Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3 artifacts. Common/client/test outputs were separate.
- The cached Minecraft validation JAR has Fabric access changes applied; this compilation is not a Loom build.
- `./gradlew build --no-daemon` could not download Gradle 9.6.0 because this runner's network was unavailable.
- Full Gradle execution, actual GitHub Actions execution, graphical rendering and a new live multiplayer session are **not claimed**.

After applying, run locally:

```bash
source tools/dev/env.sh
./gradlew clean build
./gradlew runClient -PquickPlayWorld=
```

The last command opens the title screen for the upcoming menu work.
