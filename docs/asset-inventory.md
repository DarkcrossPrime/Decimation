# Cleaned asset inventory — 0.2.2

0.2.2 reorganizes the cleaned library by content ownership instead of file type.

Preserved binary inventory:

- 197 GLB models
- 1,732 PNG textures
- 1,241 OGG sounds
- 30 deduplicated `.danim.json` clips

Metadata inventory:

- 232 converted object definitions
- 98 weapon animation sets

All 232 converted objects now have a canonical `definition.json` inside their own object directory. Unique models/textures are local. Reused geometry/textures and byte-identical animation clips remain single-copy under `content/_shared/`.

100 object-owned sound directories were localized into their corresponding object directories and 308 sound resource references were rewritten. The remaining sounds stay as global/runtime resources because no unambiguous single object owner was established.

The original migration/audit datasets are retained under `content/migration/` together with `0.2.2_reorganization_report.json`.
