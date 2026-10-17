"""Strip personal data from a Mulya backup so it can be committed as a test fixture.

    python scripts/sanitize_legacy_backup.py  my_backup.json  tests/fixtures/legacy/my_fixture.json

Removed: the whole `profile` object (name, date of birth, gender, mobile, city, state, image),
`profileImageBase64`, and every free-text name or title (replaced by neutral text such as
"Food #18"). Kept: ids, amounts, dates, modes, categories, preferences (they hold no personal data).

NEVER commit a raw backup. Keep your real backups outside the repository.
"""

import json
import sys


def sanitize(raw: dict) -> dict:
    out = dict(raw)
    out.pop("profile", None)
    out.pop("profileImageBase64", None)
    out["profile"] = None
    out["profileImageBase64"] = None
    for key in ("transactions", "pendingPlans", "recurringTransactions"):
        if key not in raw:
            continue  # keep the backup's SHAPE: an absent key stays absent
        rows = []
        for row in raw.get(key) or []:
            row = dict(row)
            label = (row.get("category") or "Item").strip() or "Item"
            row["title"] = f"{label} #{row['id']}"
            if "message" in row:
                row["message"] = None
            rows.append(row)
        out[key] = rows
    if raw.get("goals") is not None:
        out["goals"] = [{**g, "name": f"Goal #{g['id']}"} for g in raw["goals"]]
    if raw.get("budgetProfiles") is not None:
        out["budgetProfiles"] = [
            {**p, "name": f"Profile #{p['id']}"} for p in raw["budgetProfiles"]
        ]
    return out


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    with open(sys.argv[1], encoding="utf-8") as f:
        raw = json.load(f)
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(sanitize(raw), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {sys.argv[2]}")


if __name__ == "__main__":
    main()
