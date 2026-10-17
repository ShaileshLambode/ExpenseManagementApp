# Mulya Rebuild — End of Day (EOD) Report: Day 3
**Date:** Saturday, 17 October 2026  
**Branch:** `mulya-v2`  
**Builder:** Shailesh Lambode (Solo)  

---

## 1. Executive Summary

All Day 3 goals from the official guide ([`./docs/tracking/Day03.md`](./Day03.md)) have been executed, verified, and committed locally:

- **MLY-009 (Schema & Ledger — 100%)**:
  - **Migration 0003**: Currency catalogue table (`currencies`), decimals-per-currency triggers on `transactions` and `accounts`, and deferred constraint triggers enforcing transfer integrity with the half-up rounding tolerance rule (closes gap I3 and I10).
  - **Migration 0004**: Four authentication tables (`email_login_codes`, `auth_identities`, `devices`, and rotating `refresh_tokens` with session chaining and family reuse detection).
  - **Verification**: `pytest` passes **45/45 tests**; SQL test suite passes **67 of 67 checks** at head.
  - **Red-first proof**: At `--revision 0002`, SQL suite reports `39 of 67 passed` (catching fx/decimal/auth gaps); at `--revision 0003`, reports `55 of 67 passed` (only auth remaining).

- **MLY-008 (Golden Fixtures & Legacy Mapper — 100%)**:
  - **Pure Mapper**: Implemented [`./backend/app/legacy/mapper.py`](../../backend/app/legacy/mapper.py) mapping legacy Android v2.26 backups to the v2 model without crashes.
  - **Fixtures**: Included sanitized real backup (`pre_profile_v1_sanitized.json`), synthetic v2.26 edge-case backup (`v226_multi_profile.json`), and negative balance test case (`v226_negative_balance.json`).
  - **Tools**: Added [`./backend/scripts/legacy_report.py`](../../backend/scripts/legacy_report.py) (read-only inspection & derived opening balance reconciliation) and [`./backend/scripts/sanitize_legacy_backup.py`](../../backend/scripts/sanitize_legacy_backup.py).
  - **Golden Tests**: **27/27 passed** in `backend/tests/golden/` proving mapper correctness and database balance reconciliation.

- **MLY-015 / MLY-005 / MLY-007 (Decisions Accepted)**:
  - Accepted **FX tolerance rule** (CONVENTIONS §5.11): half a destination unit + rate quantisation.
  - Accepted **Auth proposal** (ARCHITECTURE §9): 6-digit code, 10 min expiry, 5 attempts, access token 15 min, refresh token 60 days with family revocation.
  - Accepted **ADR-A1**: Session = refresh-token chain, secrets stored hashed (no redundant `sessions` table).
  - Accepted **ADR-I1**: Legacy import = pure mapper, deterministic UUIDv7, owner-only tenancy.

---

## 2. Task Outcomes

| Task | Planned | Actual | Status | Evidence |
|---|---|---|---|---|
| **MLY-009** | Migrations 0003 + 0004, close I3 & I10 | Applied `0003_currency_and_transfer_integrity` & `0004_auth_tables`; expanded SQL suite to 67 checks | ✅ Done (100%) | `alembic current` = `0004 (head)`, `pytest` = 45 passed, SQL checks 67/67 passed |
| **MLY-008** | Golden fixtures & legacy mapper | Built pure mapper, 3 fixtures (one real sanitized), CLI report, database reconciliation tests | ✅ Done (100%) | `pytest tests/golden` = 27 passed, `legacy_report.py` on real fixture = `OK to import` |
| **MLY-015** | Auth schema & design | Schema applied in migration 0004; architecture §9 proposal reviewed and accepted | ✅ Done (Design & Schema) | Migration 0004 tables, [`./docs/ARCHITECTURE.md`](../ARCHITECTURE.md) §9 |
| **MLY-005** | Target architecture v0.2 | Accepted ADR-A1 (auth/session model) and ADR-I1 (legacy import strategy) | ✅ Done | [`./docs/ARCHITECTURE.md`](../ARCHITECTURE.md) v0.2 |
| **MLY-007** | Data conventions v0.3 | Accepted FX tolerance rule, currency catalogue rules, and auth rules | ✅ Done | [`./docs/CONVENTIONS.md`](../CONVENTIONS.md) v0.3 |

---

## 3. Verification & Evidence

- **Alembic Head**: `0004 (head)`
- **Full Test Suite (`pytest`)**: **45 passed, 1 warning** (starlette deprecation notice) in 5.87s
  - `tests/golden/test_legacy_mapper.py`: 20 passed
  - `tests/golden/test_legacy_reconciliation_db.py`: 4 passed
  - `tests/golden/test_legacy_report_cli.py`: 3 passed
  - `tests/test_currencies.py`: 1 passed
  - `tests/test_health.py`: 4 passed
  - `tests/test_migrations.py`: 2 passed (downgrade to base and upgrade back to head verified)
  - `tests/test_money.py`: 9 passed
  - `tests/test_overdraft_concurrency.py`: 1 passed
  - `tests/test_sql_suite.py`: 1 passed (executing all 67 SQL checks)
- **SQL Level Suite ([`./db/schema_v0_tests.sql`](../../db/schema_v0_tests.sql))**: **67 of 67 checks passed**
- **Code Linter**: `ruff check .` clean (0 errors) on target `py311`
- **Red State Reproduction**:
  - `pytest --revision 0002`: `1 failed, 43 passed, 1 skipped` (SQL suite: `39 of 67 checks passed`, catching all transfer, decimal, and auth gaps)
  - `pytest --revision 0003`: `1 failed, 44 passed` (SQL suite: `55 of 67 checks passed`, only auth tables remaining)
- **Legacy Backup Report Output (`pre_profile_v1_sanitized.json`)**:
  ```
  Primary Budget     Bank   legacy       525.00  rebuilt       525.00  OK
  Primary Budget     Cash   legacy         0.00  rebuilt         0.00  OK
  RESULT: OK to import
  ```
  (Derived opening balance of Bank = 1000.00 correctly calculated from history).

---

## 4. Commits

All Day 3 commits are recorded locally on branch `mulya-v2`:

1. `fce50b0`: `feat(db): migrations 0003 (currencies, decimals, transfer integrity) and 0004 (auth tables); SQL tests 67 (MLY-009)`
2. `098202d`: `feat(legacy): backup mapper, golden fixtures and reconciliation tests (MLY-008)`
3. `a451ae7`: `docs: conventions v0.3, architecture v0.2 (auth proposal), test matrix v0.2 (MLY-005/006/007/015)`
4. Current commit: `docs: add Day 3 EOD report (MLY-009, MLY-008, MLY-015)`

**Pushed to remote**: No (held locally per user instruction).

---

## 5. Day 4 Preview

With database migrations 0001–0004, the test suites (45 tests, 67 SQL checks), and architecture decisions locked in, Day 4 can move directly to implementation:

1. **Authentication Endpoints**:
   - `POST /api/v1/auth/request-code` (email login code)
   - `POST /api/v1/auth/verify-code` (returns short-lived JWT + rotating refresh token)
   - `POST /api/v1/auth/refresh` (token rotation + family reuse revocation)
   - `POST /api/v1/auth/logout`
   - `GET /api/v1/auth/me` (current authenticated profile)
   - Dev console mailer for code delivery.
2. **First Resource Endpoints**:
   - Accounts and Categories CRUD scoped to authenticated workspace context.
