"""Show what a legacy Mulya backup would become. Reads the file, writes NOTHING.

    python scripts/legacy_report.py  C:\\path\\to\\my_backup.json

Prints counts, the balance reconciliation and every issue. Personal data is never printed
(only which fields would be dropped). Exit code 0 = clean, 1 = blocking problem.
Run it on your RAW backup locally; never commit that file.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.legacy.mapper import check_plan_invariants, map_backup, reconcile


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    raw = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    plan = map_backup(raw)
    print(
        f"workspaces {len(plan.workspaces)} · accounts {len(plan.accounts)} · categories "
        f"{len(plan.categories)} · transactions {len(plan.transactions)} · recurring rules "
        f"{len(plan.recurring_rules)} · not imported {plan.unmapped or 'nothing'}"
    )
    print("\nBalance reconciliation (legacy balance vs rebuilt from history):")
    bad = 0
    for r in reconcile(plan):
        flag = "OK" if r.ok else "MISMATCH"
        bad += not r.ok
        print(
            f"  {r.workspace:<18} {r.account:<6} legacy {r.legacy_balance:>12}  rebuilt {r.derived_balance:>12}  {flag}"
        )
    print("\nIssues:")
    for i in plan.issues or []:
        print(f"  [{i.severity.upper():<7}] {i.code}: {i.message}")
    problems = check_plan_invariants(plan)
    for p in problems:
        print(f"  [INVARIANT] {p}")
    blocked = bool(plan.blocking_issues) or bool(problems) or bad > 0
    print(
        "\nRESULT:",
        "BLOCKED - fix the items marked ERROR/MISMATCH first" if blocked else "OK to import",
    )
    return 1 if blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
