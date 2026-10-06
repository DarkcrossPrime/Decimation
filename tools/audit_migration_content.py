#!/usr/bin/env python3
"""Reject new audit errors while reporting the frozen pre-migration baseline."""

import json
import sys
from collections import Counter
from pathlib import Path

from audit_assets import run


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    content = root / "content"
    baseline_path = root / "tools/migration/content-audit-baseline.json"
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    summary, errors = run(content)
    current = Counter(error.replace(str(content.resolve()) + "/", "") for error in errors)
    known = Counter(baseline["errors"])
    new = current - known
    fixed = known - current
    print(json.dumps({"summary": summary, "known_errors_remaining": sum((current & known).values()),
                      "new_errors": sum(new.values()), "resolved_errors": sum(fixed.values())}, indent=2))
    for error in sorted(new.elements()):
        print(f"NEW ERROR: {error}", file=sys.stderr)
    if current:
        print("Known content issues remain; auditContent is the strict gate.", file=sys.stderr)
    raise SystemExit(1 if new else 0)


if __name__ == "__main__":
    main()
