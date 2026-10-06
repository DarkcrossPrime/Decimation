#!/usr/bin/env bash
# Preview by default; --apply moves exact archived files into a recoverable backup.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
exec python3 "$repo_root/tools/migration/cleanup_legacy_sources.py" "$@"
