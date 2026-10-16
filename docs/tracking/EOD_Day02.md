# Mulya Rebuild — End of Day (EOD) Report: Day 2
**Date:** Friday, 16 October 2026  
**Project:** Mulya — Personal Expense Management App Rebuild  
**Builder:** Shailesh Lambode (Solo)  
**Branch:** `mulya-v2`  
**Repository:** `./ExpenseManagementApp` (GitHub: `ShaileshLambode/ExpenseManagementApp`)

---

## 1. Executive Summary

Day 2 achieved the foundational technical milestone: establishing the first backend services, running real database migrations, demonstrating red-to-green integrity testing, and finalizing the target system architecture:

1. **Safety & Hygiene First (Block A):**
   - Protected database credentials by updating `.gitignore` with `.env`, `.env.*`, and Python virtual environment rules before creating any local secrets.
   - Fixed historical wording errata in `./docs/tracking/EOD_Day01.md`.
2. **Backend Bootstrap (Block B — MLY-012):**
   - Initialized Python virtual environment (`./backend/.venv`) with FastAPI 0.143, SQLAlchemy 2.1, psycopg 3.3, Alembic 1.20, and Pydantic 2.14.
   - Successfully ran Alembic migrations `0001_baseline_schema_v0` and `0002_overdraft_hardening` against PostgreSQL 18.
   - Validated unversioned infrastructure health endpoints (`/health/live` and `/health/ready`).
3. **Red → Green Test Validation (Block C — MLY-009 Hardening):**
   - **RED:** Ran `pytest --revision 0001` against schema v0, reproducing 2 distinct failures: concurrent expense race condition overdrawing the account, and 7 bypass scenarios (e.g. soft-deleting an income that funded spending).
   - **GREEN:** Ran `pytest` at head revision, achieving **17/17 passing tests** with 0 failures, plus clean `ruff` linting and 33/33 passing checks in the SQL test suite.
4. **Architecture & Test Matrix Finalized (Blocks D & E — MLY-005 & MLY-006):**
   - Approved `./docs/ARCHITECTURE.md` (v0.1 accepted) and accepted all 5 Architecture Decision Records (ADRs).
   - Approved `./docs/TEST_MATRIX.md` (v0.1 accepted), formalizing the testing layers and prioritizing gap I3 (cross-currency transfer constraint) for Day 3.

---

## 2. Block-by-Block Execution Summary

| Block | Description | Planned | Actual Status | Evidence / Artifact |
|---|---|---|---|---|
| **A** | Safety (`.gitignore`), Unpack & Docs Errata | 20 min | ✅ **Completed** | Commit `2735dc2`, `.env` gitignored |
| **B** | Backend Setup, Migrations & Health (MLY-012) | 45 min | ✅ **Completed** | `alembic current` → `0002 (head)`, `/health/ready` 200 OK |
| **C** | Red → Green Test Validation (MLY-009) | 25 min | ✅ **Completed** | `pytest --revision 0001` (2 failed), `pytest` (17 passed), commit `72f7c62` |
| **D** | Architecture Review & ADRs (MLY-005) | 50 min | ✅ **Completed** | `./docs/ARCHITECTURE.md` accepted v0.1, commit `2275550` |
| **E** | Test Matrix Review (MLY-006) | 30 min | ✅ **Completed** | `./docs/TEST_MATRIX.md` accepted v0.1, commit `2275550` |
| **F** | Wrap-up & Tracker Sync | 15 min | ✅ **Completed** | Master tracker updated; `./docs/tracking/EOD_Day02.md` committed |

---

## 3. Git Commits & Evidence Trail

### 3.1 Commits on `mulya-v2`
- **Commit `2735dc2`:** `docs: conventions v0.2 errata; architecture and test matrix drafts (MLY-005/006/007)`
- **Commit `72f7c62`:** `feat(backend): FastAPI + Alembic bootstrap; migration 0002 hardens overdraft guard; SQL tests 33 (MLY-012, MLY-009)`
- **Commit `2275550`:** `docs: accept architecture v0.1 and test matrix v0.1 with ADR decisions (MLY-005, MLY-006)`

### 3.2 Test Results & Invariant Proofs
* **Red State Demonstration (`pytest --revision 0001`):**
  - Result: `2 failed, 15 passed in 4.72s`
  - Failure 1: `test_two_concurrent_expenses_cannot_overdraw_the_account` → `account overdrawn: balance=-600.00000000`
  - Failure 2: `test_sql_suite_all_pass` → failures #24, #25, #26, #27, #29, #30, #31 (overdraft bypasses allowed on v0)
* **Green State Verification (`pytest` at head):**
  - Result: `17 passed, 1 warning in 4.21s`
  - Includes: `test_health.py` (4), `test_migrations.py` (2), `test_money.py` (9), `test_overdraft_concurrency.py` (1), `test_sql_suite.py` (1, running all 33 SQL checks)
* **Code Linter:** `ruff check .` → `All checks passed!`
* **API Health Check:**
  - `GET /health/live` → `{"status": "ok"}`
  - `GET /health/ready` → `{"status": "ready", "schema_revision": "0002"}`

---

## 4. Architectural Decisions Accepted (ADR Log)

During Block D, the following 5 Architecture Decision Records were accepted into `./docs/ARCHITECTURE.md`:

| ADR | Topic | Decision | Rationale |
|---|---|---|---|
| **ADR-B1** | Database Engine Concurrency | **Synchronous** | SQLAlchemy 2 + psycopg 3 synchronous engine in FastAPI threadpools; avoids Windows asyncio loop driver quirks and unifies app/Alembic engines. |
| **ADR-M1** | Mobile Money Storage | **Option A (TEXT)** | Store money as TEXT decimal strings in mobile SQLite; exact math via decimal library in JS; validated via spike in MLY-013. |
| **ADR-R1** | Legacy Android Isolation | **Accept** | Move legacy Android code to `./legacy-android/` via single `git mv` commit prior to starting `apps/mobile/`. |
| **ADR-L1** | Ledger Editing Model | **Accept** | Retain transaction editability and soft-deletes with automatic `version` bumping; defer append-only immutability to an audit log table. |
| **ADR-S1** | Offline Overdraft Conflicts | **Option A (Acknowledge)** | Client enforces balance floor locally; writes rejected by server upon sync are flagged "needs attention", never silently dropped (formalized in MLY-010). |

---

## 5. Master Tracker Status (`00_Master_Tracker`)

| Task ID | Task Title | Status | Progress | Priority | Notes / Evidence |
|---|---|---|---|---|---|
| **MLY-003** | Freeze legacy Android v2.26 | **Done** | 100% | P0 | Tagged `native-v2.26` on GitHub. |
| **MLY-004** | Legacy UX screenshots | **Done** | 100% | P1 | 30 screenshots in `./docs/legacy-ux/` + observation README. |
| **MLY-005** | Finalize target architecture | **Done** | 100% | P0 | `./docs/ARCHITECTURE.md` accepted v0.1 with 5 ADR calls. |
| **MLY-006** | Test matrix & strategy | **Done** | 100% | P0 | `./docs/TEST_MATRIX.md` accepted v0.1 with 12 ledger invariants. |
| **MLY-007** | Data & API conventions | **Done** | 100% | P0 | v0.2 errata accepted in `./docs/CONVENTIONS.md`. |
| **MLY-009** | PostgreSQL schema & ledger | **In Progress** | 70% | P0 | Migration 0002 hardening merged; 33 checks pass. Remaining: I3 gap, auth & sync tables. |
| **MLY-012** | Bootstrap FastAPI + Alembic | **Done** | 100% | P0 | Alembic head 0002; 17 pytest passed; `/health/ready` 200 OK. |
| **MLY-010** | Sync engine spike | Planned | 0% | P0 | Next milestone (client outbox, cursors, ADR-S1). |
| **MLY-015** | Auth & session management | Planned | 0% | P0 | Next milestone (devices, refresh tokens). |

---

## 6. Action Plan for Tomorrow (Day 3)

1. **Migration 0003 (I3 Gap):**
   - Add database constraint and tests for cross-currency transfers (enforcing `fx_rate` when currencies differ).
2. **Auth Tables & Session Engine (MLY-015):**
   - Design and migrate `devices`, `sessions`, and `refresh_tokens` tables.
   - Implement the first authenticated endpoint flow.
3. **Golden Fixtures (MLY-008):**
   - Ingest user backup JSON (`mulya_backup_2026_04_26_19_33.json`) as automated test fixtures to guarantee backward data compatibility and balance integrity.

---

*Report prepared and validated on 16 October 2026.*
