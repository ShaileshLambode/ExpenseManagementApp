# Mulya — Day 2 Implementation Guide
**Thursday 15 October 2026** · Builder: Shailesh (solo) · Repo: `ShaileshLambode/ExpenseManagementApp`, branch `mulya-v2`

> **How to use this file.** Work top to bottom. Every block has *Steps*, a *Done when* checklist, and the tracker changes to make.
> Do not mark a task Done until its checklist is true. If something fails, stop and send me the exact error text
> (or a screenshot) — don't work around it.
> Everything marked "verified" was run by me; the last section lists what I could **not** verify.

---

## 0. Day 1 audit — what I checked in the repo

I cloned the latest `mulya-v2`, read `docs/tracking/EOD_Day01.md`, `docs/CONVENTIONS.md`, `db/schema_v0.sql` and the tests,
and **re-ran your two SQL files unchanged**. Day 1 is accepted, with errata below.

### Claimed in the EOD vs. what I found

| EOD says | Found | Verdict |
|---|---|---|
| Tag `native-v2.26` created | On `origin`, points at `f1bffd4` (the v2.26 release commit) | ✅ |
| Commits `2b7eb12`, `e9be007`, `f5f8382`, `fdbbf94` | All present in `git log` | ✅ |
| 23/23 tests pass | Re-ran your files: **23/23** (my sandbox is PostgreSQL 16 with a stand-in for `uuidv7()`; your PG 18.6 result is consistent) | ✅ |
| Overdraft trigger "on `account_balances`" | The trigger is on **`transactions`**. There is no `account_balances` table: balances are the derived view `v_account_balances` (which is better than the EOD describes) | ⚠️ wording |
| `purged_at TIMESTAMPTZ` column | **No such column** in `schema_v0.sql` | ❌ |
| "Immutability on ledger entries" | **Not implemented.** I edited an amount and hard-deleted a ledger row successfully | ❌ |
| "Strict non-overdraft" | Exists, but can be bypassed and raced (table below) | ⚠️ |
| Multi-currency from day 1 | Columns exist (`currency_code`, `fx_rate`); nothing enforces them (table below) | ⚠️ |
| CONVENTIONS v0.1 accepted | Only §1 and §5.8 were updated; **§5.1, §7 and the decisions table still describe the old design** (`_minor`, `opening_balance_minor`) | ⚠️ |
| Work continues "in this repo" | Public repo; `.env` is **not** in `.gitignore`; the personal backup JSON is still tracked; MLY-001 was retitled so nothing tracks that file any more | ⚠️ see A3 |

### The overdraft guard: what I reproduced on your schema (all verified)

| # | Scenario | Result on schema v0 | Fixed by |
|---|---|---|---|
| 1 | Income 1,000, expense 800, then **soft-delete the income** | Accepted → balance **−800** (the trigger returns early when `deleted_at` is set) | migration 0002 |
| 2 | **Move** that income to another account | Accepted → old account **−800** (only the NEW account is checked) | 0002 |
| 3 | **Lower an opening balance** after spending | Accepted → account at **−1,300** | 0002 |
| 4 | Bank account created with a **negative opening balance**; credit card without a limit | Accepted. Worse: an account already below zero then *blocks even an income that improves it* | 0002 |
| 5 | **Two simultaneous 800 expenses** on a 1,000 account | Both accepted → balance **−600** (each trigger cannot see the other's uncommitted row) | 0002 |
| 6 | Amount `450.123456789` on an INR account | Stored as `450.12345679` | API validation (`money.py`), not the DB |
| 7 | INR→USD transfer, `fx_rate` NULL, 1,000 became 1,000,000 | Accepted | **Open gap** (I3 in the test matrix) |
| 8 | Edit a past amount, hard-delete a row | Accepted | **Your decision** (ADR-L1) |

Items 1–5 are fixed today by migration `0002`; 6 by the API layer; 7 and 8 are recorded as open items, not hidden.

### Your Day 1 decisions — accepted, with their consequences

| Decision | What it forces (all written into CONVENTIONS v0.2 / ARCHITECTURE) |
|---|---|
| Money as major-unit `NUMERIC(20,8)` | Decimal **strings** in JSON; `Decimal` in Python; and on the phone **SQLite cannot sum decimals exactly**, so money is stored as TEXT and summed in app code (ADR-M1). Also each currency must limit its own decimals (INR 2, JPY 0) at the API. |
| Multi-currency now | `fx_rate` must be enforced (gap above). Totals across currencies will need exchange rates — a question for later, not today. |
| Overdraft always blocked | With offline-first sync, two devices can each make a valid offline spend that the server later rejects. That needs a policy (ADR-S1, decided in the sync spike). |

### Verdict

- **MLY-003 Done** · **MLY-007 Done with errata** (v0.2 fixes the drift) · **MLY-009 stays In Progress** (60% → 70% once 0002 is merged).
- **Blocks B and C skipped: I agree they don't block backend work.** But MLY-005 *is* the decision review, so Block D below absorbs Block C. MLY-004 (screenshots) must happen before `apps/mobile` starts — I've added that as a dependency.

---

## 1. Today's goal

| Task | Deliverable | Evidence |
|---|---|---|
| **MLY-012** Bootstrap FastAPI + Alembic | `alembic upgrade head` on `mulya_dev`; `/health/ready` returns 200; `pytest` = **17 passed** | commit + terminal screenshot |
| **MLY-009** (hardening part) | Migration `0002` + 10 new SQL tests (33 total); red→green demonstrated | `pytest --revision 0001` shows 2 failed, then `pytest` shows 17 passed |
| **MLY-005** Target architecture | `docs/ARCHITECTURE.md` reviewed; D-01…D-21 statuses set in the workbook | commit |
| **MLY-006** Test matrix | `docs/TEST_MATRIX.md` reviewed | commit |

| Block | Task | Time |
|---|---|---|
| A | Pull, safety (`.gitignore`), unpack the bundle, docs commit | 20 min |
| B | Backend setup + migrations + health check (MLY-012) | 45 min |
| C | Red → green: see the bugs fail, then fixed (MLY-009) | 25 min |
| D | Architecture review + decisions (MLY-005) | 50 min |
| E | Test matrix review (MLY-006) | 30 min |
| F | Wrap-up, tracker, report | 15 min |
| | **Total** | **≈ 3 h 05 min** (+30 min buffer if Python/venv setup misbehaves) |

**Short on time?** Do **A → B → C**. That is the code; D and E are reading-and-deciding and can move to tomorrow morning.

---

## Block A — Pull, safety, unpack (20 min)

*Assumption: Windows + PowerShell, as on Day 1.*

### A1. Pull
```powershell
cd <path>\ExpenseManagementApp
git checkout mulya-v2
git pull
git status          # expect: nothing to commit, working tree clean
```

### A2. Unpack the Day 2 bundle at the repo root
Download `mulya_day02_bundle.zip` from this chat, then:
```powershell
Expand-Archive -Path "$HOME\Downloads\mulya_day02_bundle.zip" -DestinationPath . -Force
git status
```
Expect:
- **New:** `backend/`, `docs/ARCHITECTURE.md`, `docs/TEST_MATRIX.md`, `docs/tracking/Day02.md`, `gitignore_additions.txt`
- **Modified (intentionally):** `docs/CONVENTIONS.md` (v0.2) and `db/schema_v0_tests.sql` (23 → 33 checks)

### A3. Safety first — `.gitignore` BEFORE any `.env` exists
This repo is **public**. The backend needs a database password in `backend/.env`; it must never be committable.
```powershell
Get-Content gitignore_additions.txt | Add-Content .gitignore
Remove-Item gitignore_additions.txt
git check-ignore -v backend/.env     # must print a rule from .gitignore (".env")
```
Separately (your call, deferred twice already): `mulya_backup_2026_04_26_19_33.json` and the stray file `s` are still tracked in this public repo.
Since the rebuild now lives here too, the cheapest fix is **GitHub → Settings → Danger Zone → Change visibility → Private** (30 seconds).
It does not recall copies made while public. I won't mention it again.

### A4. Review the document changes, then commit them
```powershell
git diff docs/CONVENTIONS.md        # read it: about 40 changed lines, listed under "Changes in v0.2"
```
Answer for yourself: *do I agree?* Edit anything you don't. Then commit **docs only** (the tests and backend go in the next commit with the migration, so no commit is deliberately red):
```powershell
git add .gitignore docs
git commit -m "docs: conventions v0.2 errata; architecture and test matrix drafts (MLY-005/006/007)"
```

### A5. Correct the EOD (recommended — it is your audit trail)
In `docs/tracking/EOD_Day01.md`:
- §4 item 3: replace "trigger on `account_balances`" with "trigger on `transactions` (balances are the derived view `v_account_balances`)".
- §4 item 4: replace "`deleted_at TIMESTAMPTZ` and `purged_at TIMESTAMPTZ`" with "`deleted_at TIMESTAMPTZ` (no `purged_at` column yet)", and replace "Immutability on ledger entries…" with "Balance adjustments are explicit transactions (`source='adjustment'`). Ledger rows are editable and soft-deletable; immutability is **not** implemented."
```powershell
git add docs/tracking/EOD_Day01.md
git commit -m "docs: EOD Day 1 errata"
```

### Done when
- [ ] `git status` clean after the commits; `git check-ignore -v backend/.env` prints a rule.
- [ ] You have read the CONVENTIONS diff and edited anything you disagree with.

---

## Block B — MLY-012: backend setup, migrations, health check (45 min)

### B1. Python
```powershell
py --version        # needs 3.12 or newer (3.12 or 3.13 are the safe choices)
```
If missing: install from python.org and tick **"Add python.exe to PATH"**, then reopen PowerShell.

### B2. Virtual environment and dependencies
```powershell
cd backend
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
```
If PowerShell refuses ("running scripts is disabled"): `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, then activate again.
```powershell
python -m pip install --upgrade pip
pip install -e ".[dev]"
```
Today pip resolves (these are what I tested): FastAPI 0.143.1 · SQLAlchemy 2.1.4 · psycopg 3.3.6 · Alembic 1.20.0 · Pydantic 2.14.1 · uvicorn 0.54.0 · pytest 9.1.1.

### B3. Settings (`.env`)
```powershell
Copy-Item .env.example .env
notepad .env
```
Replace `YOUR_PASSWORD` (both lines) with your PostgreSQL password. If it contains `@ : / # %`, URL-encode them (`@` → `%40`).
```powershell
git status          # .env must NOT be listed
```

### B4. Start from empty databases
On Day 1 you loaded `schema_v0.sql` into both databases by hand. Alembic must create the schema itself, so recreate them (they hold only Day 1 test data).
pgAdmin → Query Tool connected to the **`postgres`** database:
```sql
DROP DATABASE IF EXISTS mulya_dev  WITH (FORCE);
DROP DATABASE IF EXISTS mulya_test WITH (FORCE);
CREATE DATABASE mulya_dev;
CREATE DATABASE mulya_test;
```
(If pgAdmin says "cannot run inside a transaction block", do each one by right-click → Delete / Create → Database instead.)

### B5. Run the migrations
```powershell
alembic upgrade head
alembic current
```
Expected log: `Running upgrade -> 0001, baseline: schema v0…` then `Running upgrade 0001 -> 0002, overdraft guard hardening…`; `current` prints `0002 (head)`.
In pgAdmin, refresh `mulya_dev` → 9 tables + `alembic_version`, and the view `v_account_balances`.

### B6. Start the API
```powershell
uvicorn app.main:app --reload
```
Open `http://127.0.0.1:8000/docs`. In a second PowerShell window:
```powershell
Invoke-RestMethod http://127.0.0.1:8000/health/live      # status: ok
Invoke-RestMethod http://127.0.0.1:8000/health/ready     # status: ready, schema_revision: 0002
```
Optional (2 min): stop the `postgresql-x64-18` service in Windows *Services*, run `curl.exe -i http://127.0.0.1:8000/health/ready` → **503** while `/health/live` stays 200. Start the service again.

### B7. What you just got (read the files — each is short)
| File | Why it exists |
|---|---|
| `app/core/config.py` | Reads `.env`. The database URL has **no default** so a missing `.env` fails loudly. |
| `app/db/session.py` | One request = one database transaction (CONVENTIONS §5.9). Services never call `commit()`. |
| `app/core/money.py` | The `Amount` type (decimal strings in/out, JSON numbers rejected) and per-currency decimals. |
| `app/api/health.py` | `/health/ready` is true only if the DB answers **and** its migration revision equals the code's head. |
| `alembic/versions/0001…` | Your Day 1 schema, frozen (a byte-for-byte copy of `db/schema_v0.sql`). |
| `alembic/versions/0002…` | The hardening migration (Block C). |
| `tests/` | The pytest suite (Block C). |

### Done when
- [ ] `alembic current` → `0002 (head)`.
- [ ] `/health/live` ok, `/health/ready` ready with `schema_revision: 0002`.
- [ ] `git status` shows no `.env` and no `.venv`.

---

## Block C — MLY-009: red → green (25 min)

**The point:** a bug you can't reproduce is a bug you can't prove you fixed. You will watch the schema v0 bugs fail, then watch migration 0002 fix them.

### C1. RED — migrate the test database only to revision 0001 and run everything
```powershell
pytest --revision 0001
```
Expected: **`2 failed, 15 passed`**.
- `test_two_concurrent_expenses_cannot_overdraw_the_account` → message like `account overdrawn: balance=-600…`. Two people spent the same last ₹1,000.
- `test_sql_suite_all_pass` → lists failures **#24, #25, #26, #27, #29, #30, #31** ("no error was raised"): the bypasses from §0.
(Tests #28, #32, #33 already pass on v0: they pin behaviour that was correct.)

### C2. GREEN — migrate to head and run again
```powershell
pytest
ruff check .
```
Expected: **`17 passed`** (one harmless warning about `httpx` from Starlette's TestClient) and `All checks passed!`.

### C3. Read migration 0002 (15 min): `alembic/versions/sql/0002_overdraft_hardening.up.sql`
| Problem (from §0) | What 0002 does |
|---|---|
| 1, 2 — delete/move bypass | Triggers now fire on INSERT, UPDATE **and DELETE**; a move checks **both** accounts |
| 3 — opening balance / limit changed | New trigger on `accounts` for `opening_balance`, `credit_limit`, `type` |
| 4 — bad account shapes | CHECK constraints: no negative opening balance (except a card within its limit); a card must have a limit |
| 5 — race | Each check first takes `SELECT … FOR NO KEY UPDATE` on the account row. The balance is read in a **separate** statement so it sees the other writer's commit |
| Stable error | Raises `P0001` with constraint name `account_no_overdraft` — the API maps this to 422 without parsing message text |

Why `FOR NO KEY UPDATE` and not `FOR UPDATE`: foreign keys take a weaker lock on the account row when a transaction row is inserted; `FOR UPDATE` conflicts with it, so two concurrent inserts could **deadlock**. `FOR NO KEY UPDATE` serialises writers without that.

Optional: open `db\schema_v0_tests.sql` in pgAdmin on `mulya_test` and run it → `ALL PASS | 33 of 33`.

### C4. Commit the code
```powershell
cd ..
git add backend db
git ls-files backend | findstr /i "\.env"      # must show ONLY backend/.env.example
git commit -m "feat(backend): FastAPI + Alembic bootstrap; migration 0002 hardens overdraft guard; SQL tests 33 (MLY-012, MLY-009)"
git push
```

### Done when
- [ ] `pytest --revision 0001` → 2 failed, 15 passed (you saw the red).
- [ ] `pytest` → 17 passed.
- [ ] Pushed; no `.env` in the commit.

---

## Block D — MLY-005: architecture review (50 min)

### D1. Read `docs/ARCHITECTURE.md`
It is a draft I wrote from your Day 1 decisions and the code review. Your job is to **accept, edit or overrule** it.

### D2. Make five calls (write your answer into the document next to each ADR)
| # | Decision | My recommendation |
|---|---|---|
| 1 | **ADR-B1** Database access synchronous (SQLAlchemy 2 + psycopg 3) | **Accept.** This revises my earlier "async" suggestion: one driver for app + Alembic, and psycopg's async mode is a known problem on Windows' default event loop (worth verifying before you rely on it). Revisit only if profiling demands. |
| 2 | **ADR-M1** Mobile money storage (TEXT + app-side decimal math) | **Accept option A**, confirmed by a 30–60 min spike inside MLY-013. |
| 3 | **ADR-R1** Move legacy Android to `legacy-android/` in one `git mv` commit before `apps/mobile` starts | **Accept.** Not today. |
| 4 | **ADR-L1** Ledger edits: editable + soft-delete + audit log later (no immutability) | **Accept** unless you truly want an immutable ledger (it changes the API). |
| 5 | **ADR-S1** Offline overdraft conflicts: client enforces the floor too; a server-rejected write is kept and flagged, never dropped | **Acknowledge now, decide in the sync spike (MLY-010).** |

### D3. Set statuses in `20_Decisions` (your tracker workbook)
All 21 rows are covered below (the same table is in `ARCHITECTURE.md` §11).
- **Approve:** D-01, D-02, D-03 (note "money is major units"), D-05, D-08, D-09, D-10, D-11, D-14, D-15, D-17, D-19 (note "reduced scope: owner-only"), D-20, D-21.
- **Approve with a condition:** D-06 (needs ADR-M1), D-07 (Apple sign-in deferred until iOS ships), D-12 (needs the money and charts spikes), D-18 (mobile only).
- **D-04 Redis:** set to *Deferred* — not in v1.
- **D-13:** the row still says "Integer minor units". **Edit the text** to "Decimal major units NUMERIC(20,8); UUIDv7; date + timestamptz", then approve.
- **D-16** stays `Needs Research` (decided by the MLY-010 spike).

### Done when
- [ ] Your answers to the five ADRs are in `ARCHITECTURE.md`.
- [ ] Every row of `20_Decisions` is Approved or Deferred, except D-16.
- [ ] Committed:
```powershell
git add docs
git commit -m "docs: accept architecture v0.1 with ADR decisions (MLY-005)"
```

---

## Block E — MLY-006: test matrix review (30 min)

1. Read `docs/TEST_MATRIX.md`. The tables are the content: §1 layers, §2 ledger invariants and which ones are still **gaps**.
2. Confirm three things (edit the file if you disagree):
   - Principles 1–5 (especially *red first* and *a gap is a to-do, not a secret*).
   - **Which gap goes first.** My recommendation: **I3 (cross-currency transfers)** — it is the only integrity hole the database lets through right now (it goes into migration 0003 on Day 3).
   - **CI timing.** My recommendation: add a GitHub Action (PostgreSQL 18 service + `pytest`) right after the first real endpoint exists, not today.
3. Commit:
```powershell
git add docs
git commit -m "docs: accept test matrix v0.1 (MLY-006)"
git push
```

### Done when
- [ ] You confirmed the three items above and committed.

---

## Block F — Wrap-up (15 min)

### F1. Tracker (`00_Master_Tracker`)
| Task | Change |
|---|---|
| MLY-005 | `Done` / 100 — evidence: commit hash |
| MLY-006 | `Done` / 100 — evidence: commit hash |
| MLY-007 | stays `Done`; note "v0.2 errata committed" |
| MLY-012 | `Done` / 100 — evidence: "alembic head 0002; pytest 17 passed; /health/ready 200" |
| MLY-009 | `In Progress` / **70** — note "0002 hardening merged; remaining: cross-currency constraint (I3), auth + sync tables" |
| MLY-004 | stays `Backlog`; Dependency: "Before `apps/mobile` starts (MLY-011/013)" |
| Add rows | (1) Cross-currency transfer constraint + tests (I3) — P1, under MLY-009 · (2) Spike: money storage in SQLite (ADR-M1) — P1, inside MLY-013 · (3) Offline overdraft conflict policy (ADR-S1) — P1, inside MLY-010 · (4) Add a secret scanner to CI — P2 |
| `Next` marker (column N) | the MLY-009 remainder (migration 0003) |

### F2. Send me a Day 2 report
```
Day 2 report
- Done:               (task IDs)
- Evidence:           (commit hashes; screenshot of "17 passed"; screenshot of "2 failed, 15 passed" from --revision 0001)
- Time per block:     A __ B __ C __ D __ E __ F __
- Surprises/blockers:
- ADR answers (only where you changed my recommendation):
- Anything that behaved differently on Windows / PostgreSQL 18:
```

### Day 3 preview (not part of today)
1. **Migration 0003:** cross-currency transfer constraint (I3) and its tests.
2. **Auth tables** (`devices`, `refresh_tokens`) and the first auth endpoints (MLY-015).
3. **Golden fixtures** from your own backup JSON (MLY-008): the real-data safety net for everything after.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Activate.ps1 cannot be loaded… running scripts is disabled` | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, then activate again. |
| `ValidationError: database_url Field required` | `backend/.env` is missing or you're not in `backend/`. Re-do B3. |
| `password authentication failed` | Wrong password in `.env`, or special characters not URL-encoded. |
| `RuntimeError: uuidv7() was not found` | You're connected to a PostgreSQL older than 18 (check `SELECT version();` and the port 5432 vs 5433). |
| `relation "users" already exists` during `alembic upgrade` | The database still holds Day 1's hand-loaded schema. Redo B4. |
| `pytest` exits with "must point at a database named 'mulya_test'" | `TEST_DATABASE_URL` in `.env` points elsewhere. This guard is intentional: the test session wipes that database. |
| `permission denied` on `DROP SCHEMA public` | Use the `postgres` superuser in `TEST_DATABASE_URL`. |
| `pytest --revision 0001` shows more than 2 failures | Send me the output; I want to see it. |
| A `StarletteDeprecationWarning` about `httpx` | Harmless; it comes from the test client library. |
| Port 8000 already in use | `uvicorn app.main:app --reload --port 8001`. |

---

## What I could and could not verify

**Verified (run by me):** the Day 1 repo state; your schema and 23 tests (23/23); every bug in §0 (reproduced); migration 0002 up, down and up again; 33/33 SQL checks on v0.1; 17/17 pytest at head; the red state (2 failed, 15 passed) at revision 0001; the live server (`/health/live`, `/health/ready`, `/api/v1/system/info`, `/docs`) including a database outage (ready → 503, live stays 200); `ruff check` clean; a fresh unpack of the bundle running green.

**Not verified:** anything on **your machine**. I ran on Linux, Python 3.12.3, PostgreSQL **16** with a stand-in for `uuidv7()`; I have not run on Windows, PowerShell, your Python, or PostgreSQL 18. The package versions are whatever pip resolved today (newer than I would have guessed from memory); I tested exactly those. The claim in ADR-B1 about psycopg async on Windows is from memory of its documentation — check it before relying on it. If anything differs on your side, that difference is the most useful thing you can send me.
