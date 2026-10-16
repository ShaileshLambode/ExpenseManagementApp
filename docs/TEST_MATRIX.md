# Mulya — Test Matrix (v0.1 — accepted 16 October 2026)

Task: **MLY-006**. Status: v0.1 accepted on 16 Oct 2026.
Companion documents: `CONVENTIONS.md` (the rules being tested) and `ARCHITECTURE.md` §6 (where each rule is enforced).

## Principles

1. **Every invariant in CONVENTIONS gets a test before its feature counts as Done.**
2. **Reproduce a bug with a failing test first** (red), then fix it (green). Day 2 does exactly this with `pytest --revision 0001`.
3. **Tests only touch `mulya_test`.** The test session wipes that database and refuses to run if the name differs.
4. **Prefer a real PostgreSQL to mocks.** The important rules live in the database, so a mock would test nothing.
5. **One command runs everything:** `pytest` (from `backend/`).

## 1. Layers

Status key: ✅ exists today · ⬜ planned (task in brackets).
17 tests pass today: health 4, migrations 2, money 9, concurrency 1, SQL suite 1 (which contains 33 SQL checks).

| Layer | What it proves | Tool | Status |
|---|---|---|---|
| Database invariants | CHECK / FK / trigger rules hold | SQL suite `db/schema_v0_tests.sql` (33 checks), executed by pytest | ✅ |
| Migrations | every migration reverses; app expects the head revision | pytest: `test_migrations`, `test_health` | ✅ |
| Database concurrency | races cannot break invariants | pytest + two psycopg connections | ✅ 1 test (overdraft); more with each money-moving feature |
| Money / units | exact decimals, per-currency decimals, no JSON numbers | pytest unit: `test_money` | ✅ |
| HTTP API | status codes, error mapping, auth | pytest + `TestClient` + test DB | ⬜ first endpoint [MLY-019] |
| Services | business rules (transfers write both legs, recurring catch-up) | pytest on a real DB | ⬜ [MLY-019, MLY-021] |
| Golden fixtures | your real backup imports and balances reconcile | pytest using your own backup JSON | ⬜ [MLY-008] |
| Sync | two simulated devices converge; retries; tombstones | pytest simulation (+ property tests) | ⬜ after the sync spike [MLY-010] |
| Mobile unit | Money module, local SQLite repo, outbox | Jest (+ expo-sqlite) | ⬜ [MLY-013] |
| Mobile end-to-end | add a transaction offline → it syncs | a UI-test tool, to be chosen | ⬜ later |
| Web | components + login/add-transaction flow | Vitest + Playwright (to be validated) | ⬜ later |
| Security | no secrets in git; dependency vulnerabilities | a secret scanner + `pip-audit` / `npm audit` (to be validated) | ⬜ add the secret scanner first |
| Performance | list and balance queries at 100k rows | pytest + `generate_series` | ⬜ later |

Tools named "to be validated / chosen" are suggestions I have not tested on this stack; verify each before adopting it.

## 2. Ledger invariants and their coverage today

| # | Invariant | Covered by | Status |
|---|---|---|---|
| I1 | balance = opening + signed sum of live transactions | SQL suite: balances | ✅ |
| I2 | same-currency transfer legs cancel out | SQL suite: transfers (a check, not a constraint) | ✅ |
| I3 | cross-currency transfer legs agree with `fx_rate`, and `fx_rate` is required when currencies differ | nothing | ❌ **GAP** (verified: accepted with `fx_rate` NULL) |
| I4 | no overdraft on insert / update / delete / move / limit change | SQL suite §9 + concurrency test | ✅ |
| I5 | analytics exclude transfers and adjustments | SQL suite: analytics | ✅ |
| I6 | soft-delete and restore change the balance and are reversible | SQL suite: soft delete | ✅ |
| I7 | `version` bumps on update | SQL suite: version | ✅ DB · ⬜ API returns 409 |
| I8 | a recurring occurrence is created once per date | SQL suite: recurring | ✅ DB · ⬜ catch-up generator [MLY-021] |
| I9 | no reference across workspaces | SQL suite: cross-workspace | ✅ DB · ⬜ API authorisation |
| I10 | money is exact; amounts respect the currency's decimals | `test_money` | ✅ module · ⬜ endpoint level |
| I11 | creating with the same client UUID twice creates one row | nothing | ⬜ [MLY-019] |
| I12 | the 30-day purge never changes a balance | nothing | ⬜ [MLY-021] |

## 3. Rules of the road

- **Naming:** `test_<invariant>_<situation>`, e.g. `test_overdraft_on_concurrent_expense`.
- **Red first:** a bug fix is committed together with (or after) the failing test that proves the bug.
- **Concurrency tests** need the second writer to reach the lock. `test_overdraft_concurrency.py` waits one second for that; if it ever flakes on a slow machine, replace the pause with polling `pg_locks`.
- **A gap in the table above is a to-do, not a secret.** When a feature lands, its rows must move from ⬜/❌ to ✅ in the same change.

## 4. Commands

```powershell
cd backend
pytest                      # everything (wipes mulya_test)        -> 17 passed
pytest --revision 0001      # show the schema v0 bugs              -> 2 failed, 15 passed (by design)
pytest -k money             # one area
ruff check .                # lint
```

## 5. Definition of Done for a feature

- [ ] Its invariants are listed in §2 (new rows if needed) and every row for it is ✅.
- [ ] Database-level rule exists where the rule is about data integrity (not only in Python).
- [ ] Happy path, one failure path, and one concurrency or idempotency case are tested.
- [ ] `pytest` and `ruff check .` pass from a clean checkout and a freshly migrated database.
- [ ] CONVENTIONS / ARCHITECTURE updated if the feature changed a rule.
