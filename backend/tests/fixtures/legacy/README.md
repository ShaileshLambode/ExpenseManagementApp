# Legacy backup fixtures

Inputs for `tests/golden/`. All three are safe to commit: **no personal data**.

| File | What it is | What it proves |
|---|---|---|
| `pre_profile_v1_sanitized.json` | Your real v1 backup, sanitized (`scripts/sanitize_legacy_backup.py`) | The "older backup" shape: no profiles, goals or recurring rules; transactions point at a profile id that does not exist; a category with a trailing space |
| `v226_multi_profile.json` | Synthetic v2.26 backup (`build_synthetic.py`) | Two profiles + one in the recycle bin, BANK/CASH, case-variant categories, a goal contribution booked as an expense, float noise, month-end recurring rule, a balance lowered outside the ledger, scheduled payments and goals |
| `v226_negative_balance.json` | Synthetic | The old app allowed negative balances; the new model cannot import one |

## Rules
- **Never commit a raw backup.** Keep real backups outside the repository.
- To turn a newer real backup into a fixture:
  `python scripts/sanitize_legacy_backup.py C:\path\to\real_backup.json tests\fixtures\legacy\my_fixture.json`
  then check the result by eye before committing.
- The synthetic fixtures are generated: change `build_synthetic.py`, run it, commit the output.
