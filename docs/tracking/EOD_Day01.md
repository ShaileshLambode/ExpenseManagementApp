# Mulya Rebuild — End of Day (EOD) Report: Day 1
**Date:** Wednesday, 14 October 2026  
**Project:** Mulya — Personal Expense Management App Rebuild  
**Builder:** Shailesh Lambode (Solo)  
**Branch:** `mulya-v2`  
**Base Repository:** `./ExpenseManagementApp` (GitHub: `ShaileshLambode/ExpenseManagementApp`)

---

## 1. Executive Summary

Day 1 focused on laying the immutable foundations for the Mulya rebuild:
1. **Freezing the legacy codebase** via git tag `native-v2.26` so future development cleanly builds atop modern architecture without legacy drift.
2. **Standardizing project conventions** (`./docs/CONVENTIONS.md` v0.1) with critical user architectural decisions: major-unit currency representation (`NUMERIC(20,8)`), immediate multi-currency support, and strict database-enforced non-overdraft protection.
3. **Designing and verifying PostgreSQL 18 Schema v0** (`./db/schema_v0.sql`), executing an automated test suite with **23/23 passing checks** on PostgreSQL 18.6 with native `uuidv7()`.

All core blocking milestones for Day 1 were successfully achieved and verified.

---

## 2. Block-by-Block Execution Summary

| Block | Description | Planned | Actual Status | Evidence / Artifact |
|---|---|---|---|---|
| **A** | Housekeeping & Repo Setup | 15 min | ✅ **Completed** | Git tag `native-v2.26`, commit `2b7eb12` |
| **B** | Legacy UX Capture (MLY-004) | 60 min | ⏭ **Skipped / Deferred** | Directory `./docs/legacy-ux/` scaffolded; non-blocking for backend/API |
| **C** | Decisions Review (20_Decisions) | 20 min | ⏭ **Skipped / Deferred** | Non-blocking for schema & ledger foundation |
| **D** | Conventions Spec (MLY-007) | 45 min | ✅ **Completed** | `./docs/CONVENTIONS.md` accepted v0.1, commit `e9be007` |
| **E** | Postgres Schema v0 (MLY-009) | 75 min | ✅ **Completed** | `./db/schema_v0.sql`, commit `fdbbf94`, **23/23 tests pass** |
| **F** | Wrap-up & Tracker Sync | 15 min | ✅ **Completed** | Tracker updated; EOD documentation generated |

---

## 3. Key Deliverables & Git Evidence

### 3.1 Git Commits & Tags
- **Tag:** `native-v2.26`
  - *Annotation:* `"Final native Android version (frozen). Rebuild continues in this repo."`
- **Commit `2b7eb12`:** `chore: add rebuild structure, conventions draft, schema v0 draft`
- **Commit `e9be007`:** `docs: accept data & API conventions v0.1 (MLY-007)`
- **Commit `f5f8382`:** `db: update schema v0 + tests for CONVENTIONS.md v0.1`
- **Commit `fdbbf94`:** `db: schema v0 on PostgreSQL 18 — 23/23 tests pass (MLY-009)`

### 3.2 Database Verification (`mulya_dev` & `mulya_test`)
- **PostgreSQL Version:** `PostgreSQL 18.6 on x86_64-windows, compiled by msvc-19.44.35228, 64-bit`
- **Native UUIDv7 Verification:** Confirmed via `SELECT uuidv7();` -> `01a13b8b-97c7-76d9-be7c-210413bad6e7`
- **Automated Schema Test Suite:** Ran `./db/schema_v0_tests.sql` against `mulya_test`.
- **Result:** **23/23 tests passed (0 failures)**. Evidence exported to `./db/data-1792000822428.csv`.

---

## 4. Key Architectural Decisions & Schema Customizations

During Block D and Block E, the following architectural decisions were solidified and implemented directly into `./db/schema_v0.sql`:

1. **Money Representation (Splitwise-style Major Units):**
   - User selected: Major decimal units instead of minor integer units (paise/cents).
   - Columns defined as `NUMERIC(20,8)` for monetary amounts and `NUMERIC(20,10)` for FX rates.
   - Simplifies multi-currency calculations and avoids client-side division/formatting bugs.

2. **Multi-Currency from Day 1:**
   - Account currency specified via `accounts.currency_code CHAR(3)`.
   - Transfers support cross-currency exchanges with `transfers.fx_rate NUMERIC(20,10)`.

3. **Strict Non-Overdraft Trigger:**
   - User decision: Strict guard preventing any account from dropping below its effective credit limit / zero balance.
   - Implemented via `check_no_overdraft()` `AFTER INSERT OR UPDATE` trigger on `transactions` (balances are the derived view `v_account_balances`).
   - Dedicated Test 23 verified that overdraft transactions are rejected with SQLSTATE `P0001`.

4. **Audit Trail & Soft Deletion:**
   - 30-day recycle bin policy with `deleted_at TIMESTAMPTZ` (no `purged_at` column yet).
   - Balance adjustments are explicit transactions (`source='adjustment'`). Ledger rows are editable and soft-deletable; immutability is **not** implemented.

---

## 5. Master Tracker Status (`00_Master_Tracker`)

| Task ID | Task Title | Status | Progress | Priority | Notes / Next Action |
|---|---|---|---|---|---|
| **MLY-001** | Legacy repository strategy | Backlog | 0% | P2 | Deferred by owner; work continues in current repo. |
| **MLY-002** | Package & App ID definition | Backlog | 0% | P1 | Prerequisite before Google OAuth / Push notifications (MLY-015/020). |
| **MLY-003** | Freeze legacy Android v2.26 | **Done** | 100% | P0 | Tagged `native-v2.26`. |
| **MLY-004** | Legacy UX screenshots | Backlog | 0% | P1 | Deferred to before frontend design sprint (MLY-011/023). |
| **MLY-005** | Finalize target architecture | **Next** | 0% | P0 | **First task for Day 2** (Architecture doc + repo layout). |
| **MLY-006** | Test matrix & strategy | Planned | 0% | P0 | Day 2 task (Backend, Mobile, Web matrix). |
| **MLY-007** | Data & API conventions | **Done** | 100% | P0 | Spec accepted v0.1 in `./docs/CONVENTIONS.md`. |
| **MLY-009** | PostgreSQL schema v0 | **In Progress** | 60% | P0 | Core tables & tests pass (23/23). Reaches 100% with auth/sync tables. |
| **MLY-012** | Bootstrap FastAPI + Alembic | Planned | 0% | P0 | Day 2 task (migrations + dev connection). |
| **MLY-025** | Legacy SQLite data importer | Backlog | 0% | P2 | Reduced scope: single-user migration tool for owner data. |

---

## 6. Analysis of Deferred Items ("Skipped" Parts)

### Is it compulsory to complete Block B & Block C before jumping to tomorrow's tasks?
**No. It is not compulsory.**
- **Block B (UX Screenshots — MLY-004):** Capturing screenshots of the old Android app is only a design reference for the UI redesign (MLY-011, MLY-023, MLY-024). It has zero impact on the backend, database, migrations, or API architecture.
- **Block C (20_Decisions Sheet):** The core foundational decisions (database engine, money format, multi-currency, overdraft rules) were already finalized and implemented in `CONVENTIONS.md` and `schema_v0.sql`. The spreadsheet can be reviewed quickly during downtime without stalling Day 2 technical progress.

---

## 7. Action Plan for Tomorrow (Day 2)

1. **MLY-005: Finalize Target Architecture (~1h)**
   - Draft a clean one-page architecture specification: backend modular structure, FastAPI service boundary, sync engine overview, and monorepo/folder layout.
2. **MLY-006: Define Test Matrix (~1h)**
   - Formalize testing levels: Database integrity tests (expanding on the 23 checks), backend pytest suites, integration testing, and mobile offline synchronization tests.
3. **MLY-012: Bootstrap FastAPI + Alembic Backend (~1.5h)**
   - Initialize Python backend project structure (`./backend`).
   - Configure Alembic to manage migrations starting from `schema_v0.sql`.
   - Establish live connection to PostgreSQL database `mulya_dev`.
   - Validate healthcheck endpoint and initial migration runs.

---

*Report prepared and validated on 14 October 2026.*
