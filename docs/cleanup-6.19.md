# 6.19 — source and log cleanup

Apply this incremental overlay over **6.18**, at the repository root on
`migration`. No content, pose offsets, ammunition semantics or network wire
formats changed. No additional live multiplayer test was requested or run:
the developer accepted LAN interruptions/mid-animation joins and server features.
The next large multiplayer pass is deferred until after migration.

## Code removed

| Removal | Evidence / replacement |
| --- | --- |
| `WeaponDefinition.firstFireMode()` | No callers; live state indexes `fireModes()` directly |
| Three old `WeaponVisualData` constructors | No production or test callers; complete prepared snapshot remains |
| Three abbreviated payload constructors | Only legacy test fixtures used them; fixtures now specify protocol-4 entity/dimension identity explicitly |
| `WeaponAdsPresentation.hideCrosshair(float)` | Obsolete policy shim used only by a test; the registered global HUD replacement actually hides the crosshair and remains unchanged |
| Four `FirstPersonRigPose` fields | `cameraPitch` duplicated shoulder pitch; `torsoPitch`, `cameraSafetyForward`, `adsProgress` were zero placeholders with no runtime readers |
| Three unused imports | Confirmed by source references and recompilation |

Eight obsolete methods/constructors and four dead record fields removed. The arm
solver still returns the same three values that the renderer actually consumes;
its tests retain the viewing-angle, shoulder travel, safety and downward-anchor
contracts. Reflection/Mixin/Fabric entrypoints and Minecraft renderer overrides
were checked and retained, even where ordinary source call counts are zero.

## Logging

Default Decimation logging now has **one startup summary**, instead of separate
foundation/client/weapon/ammunition/sound registration chatter. Per-model bake
and binding reports are debug-only. Validation exceptions and actionable failure
messages remain. Test programs still print results. This does not suppress
Minecraft/Fabric/other-mod logging, and no FPS improvement is claimed.

## 1.7.10 files and workspace cleanup

The supplied active `src/` has **no remaining 1.7.10 platform code** or imports of
the legacy Decimation, Requiem, Forge/FML or Kryonet packages. The known obsolete
source manifest has 33 paths, already absent from this snapshot. A user's checkout
that accumulated older files can still remove exact archived versions safely.
It is not safe to delete files just because they mention "legacy": importer tools
read the old asset formats, the runtime generator still redirects old sound names,
and the OBJ/DANIM loader is the active 26.3 asset path.

Quit client and server, then run from the repository root:

```bash
bash tools/migration/remove_legacy_sources.sh --logs
# Review the preview, then explicitly apply:
bash tools/migration/remove_legacy_sources.sh --apply --logs
```

Apply requires branch `migration`. Whole-manifest preflight and hashes protect
modified sources; log targets are limited to known log directories. Eligible
files move into a unique `.migration-backups/<timestamp-id>/` with their relative
paths and a recovery inventory. They are removed from the active locations but
remain recoverable; no permanent deletion, broad recursive removal, or Git reset.
Do not discard that backup until it is no longer needed. Saves/settings and
`content/` are not targets. Preview here found **0 sources and 16 logs**; no user
log files were moved in this snapshot because it has no Git branch metadata.

Keep original reference archives outside the active repo for menu reconstruction;
they are not shipped in the mod or executed by the 26.3 source sets. Do not delete
the converters, notices, archive branch, canonical models or shaders as a generic
"1.7.10 purge". Their future use cannot be inferred from current weapon registration.
Generated `build/` output can be discarded via `./gradlew clean`; it is rebuilt
from canonical content. Cache deletion is not runtime optimization.

## Verification

Main/client/test sources compiled against actual 26.3 and pinned Fabric artifacts
on Java 25. Headless behavior, codecs, audio, catalogue, multiplayer contracts,
server-boundary, body, visual and pure-arm suites pass. Cleanup has **8 isolated
worktree tests**, including source guards, log backup, symlinks and save/settings
protection. Content audit remains **217 known findings, zero new**; no canonical
content file was edited. Full Loom build and graphical launch are not claimed
from this runner; run your normal local `./gradlew clean build` after applying.

Cleanup does not implement new joints, menu screens, multiplayer features or
content repairs. Menus remain the first feature work after closing migration.
