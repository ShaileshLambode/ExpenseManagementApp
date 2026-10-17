# Mulya — Day 3 Implementation Guide
**Saturday 17 October 2026** · Builder: Shailesh (solo) · Repo: `ShaileshLambode/ExpenseManagementApp`, branch `mulya-v2`

> **How to use this file.** Work top to bottom. Every block has *Steps*, a *Done when* checklist and the tracker changes.
> Don't mark a task Done until its checklist is true. If something fails, stop and send me the exact error text.
> Everything marked "verified" I ran myself; the last section lists what I could **not** verify.

---

## 0. Day 2 audit — what I checked in the repo

I cloned the latest `mulya-v2`, read the five commits pushed on Friday 16 Oct, read `docs/tracking/EOD_Day02.md` in full, and
**ran your committed backend unchanged** (fresh virtualenv, fresh databases).

| Commit (Fri 16 Oct) | What |
|---|---|
| `e43981a` 21:36 | Legacy UX screenshots + audit table (MLY-004) |
| `2735dc2` 22:51 | CONVENTIONS v0.2, architecture and test-matrix drafts, EOD Day 1 errata |
| `72f7c62` 23:34 | FastAPI + Alembic bootstrap, migration 0002, SQL tests 33 |
| `2275550` 23:38 | Architecture + test matrix accepted with your ADR answers |
| `26f393a` 23:40 | EOD Day 2 |

### Claimed vs. found

| EOD / commit says | Found | Verdict |
|---|---|---|
| `alembic upgrade head` → 0002; `pytest` = 17 passed | Re-ran your code unchanged: migrations 0001→0002 apply; **17 passed**; `ruff check` clean | ✅ |
| `pytest --revision 0001` → 2 failed, 15 passed | Reproduced exactly | ✅ |
| Five ADRs accepted | `ARCHITECTURE.md` diff shows your answers on ADR-B1, M1, R1, L1, S1 | ✅ |
| EOD Day 1 errata applied | `purged_at`, `account_balances` and "immutable" wording are gone | ✅ |
| No secrets committed | Scanned **every commit**: no `.env` was ever added (only `.env.example`), no password in any commit, `.gitignore` covers `.env` | ✅ |
| MLY-004 done: 30 screenshots + README | 30 files, a keep/redesign/remove verdict for every screen, the Plans/Recurring answer ("unreachable"), five insights. Spot-checked one image (profile switcher): fresh-install data, nothing personal | ✅ |
| *(not in the EOD)* | `requires-python` was lowered from 3.12 to **3.11**. The ruff `target-version` stayed `py312`, so lint was checking against a Python you don't run | ⚠️ fixed in today's bundle (`py311`) |
| ADR-B1 rationale | You dropped my "(verify before relying on this)". I verified it: psycopg's own source (`connection_async.py`) refuses to run async on Windows' `ProactorEventLoop` | ✅ claim is true |
| *(not in the EOD)* | Whether `20_Decisions` was updated in the workbook | ❓ I can't see your workbook — upload it if you want me to check |
| Repo visibility | An anonymous clone still works: the repo is **public**, and `mulya_backup_2026_04_26_19_33.json` and `s` are still tracked | ℹ️ your call; today adds a guard against *new* raw backups |

### A flaw in my own Day 2 bundle
`tests/test_health.py` hard-coded `"schema_revision": "0002"`. Migration 0003 would have broken it. Today's bundle makes it read the
expected head from Alembic instead, and `test_migrations.py` now knows the table list for every revision.

### Verdict
Day 2 is accepted. **MLY-004, 005, 006, 012: Done.** MLY-009 stays at 70% until Day 3's two migrations land.

---

## 1. Today's goal

| Task | Deliverable | Evidence |
|---|---|---|
| **MLY-009** (finish) | Migration **0003**: currency catalogue, decimals per currency, transfer integrity. Migration **0004**: auth tables | `pytest` shows 45 passed; SQL suite **67 of 67** |
| **MLY-008** Golden fixtures + reconciliation | A legacy-backup mapper, three fixtures (one is your real data, sanitized), and tests that prove balances reconcile **through the real database** | `pytest tests/golden` → 27 passed |
| **MLY-015** (preparation only) | Auth **design proposal** in `ARCHITECTURE.md` §9 for you to accept or edit | commit |

**Why the auth endpoints moved to Day 4.** Your Day 2 plan said "first authenticated endpoint flow". I deliberately split it: endpoints
need decisions only you can make (code length, token lifetimes) and an e-mail provider can't be tested end-to-end today. The schema and a
concrete proposal are ready, so Day 4 starts writing code, not debating.

| Block | Task | Time |
|---|---|---|
| A | Pull, unpack, safety, install | 15 min |
| B | Migration 0003 — red → green (MLY-009) | 45 min |
| C | Migration 0004 — auth tables (MLY-009) | 25 min |
| D | Golden fixtures and the legacy mapper (MLY-008) | 60 min |
| E | Docs review and decisions (MLY-005/015) | 30 min |
| F | Wrap-up, tracker, report | 15 min |
| | **Total** | **≈ 3 h 10 min** (+30 min buffer) |

**Short on time?** Do **A → B → D**.

---

## Block A — Pull, unpack, safety, install (15 min)

### A1. Pull
```powershell
cd <path>\ExpenseManagementApp
git checkout mulya-v2
git pull
git status            # expect: working tree clean
```

### A2. Unpack the bundle at the repo root
Download `mulya_day03_bundle.zip` from this chat, then:
```powershell
Expand-Archive -Path "$HOME\Downloads\mulya_day03_bundle.zip" -DestinationPath . -Force
git status
```
Expect — **modified (intentionally):** `backend/pyproject.toml`, `backend/README.md`, `backend/tests/test_health.py`,
`backend/tests/test_migrations.py`, `db/schema_v0_tests.sql`, `docs/ARCHITECTURE.md`, `docs/CONVENTIONS.md`, `docs/TEST_MATRIX.md`.
**New:** migrations `0003_*` and `0004_*` (+ their `sql/` files), `backend/app/legacy/`, `backend/scripts/`,
`backend/tests/golden/`, `backend/tests/fixtures/`, `backend/tests/test_currencies.py`, `docs/tracking/Day03.md`, `gitignore_additions.txt`.
Nothing else should be listed. If it is, tell me.

### A3. `.gitignore`
```powershell
Get-Content gitignore_additions.txt | Add-Content .gitignore
Remove-Item gitignore_additions.txt
git check-ignore -v mulya_backup_new.json     # must print a rule
```
(This stops a *new* raw backup being committed. The old tracked file stays tracked until you `git rm --cached` it — your call.)

### A4. Install (the dependency list changed)
```powershell
cd backend
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
```
It now also installs `tzdata` **on Windows only**: Windows has no time-zone database, and the mapper needs `Asia/Kolkata`.

### Done when
- [ ] `git status` lists exactly the files above; nothing under `.env` / `.venv`.
- [ ] `pip install` finished without errors.

---

## Block B — Migration 0003: red → green (45 min)

**What 0003 closes.** Both gaps were reproduced in the Day 2 audit: (1) a cross-currency transfer accepted with `fx_rate` NULL — 1,000 INR
became 1,000,000 USD; (2) `450.123456789` rupees stored. It does three things:

| Part | What it adds | Why |
|---|---|---|
| **Currency catalogue** | table `currencies(code, exponent)`; foreign keys from `accounts` and `workspaces` | one source of truth for "INR has 2 decimals"; an unknown code is rejected |
| **Decimals per currency** | triggers on `transactions` and `accounts` (`amount_scale_per_currency`) | the `NUMERIC(20,8)` column allows 8 decimals; a currency must not. Also: an account's currency can't change once it has transactions |
| **Transfer integrity** | *deferred* constraint triggers (`transfer_consistent`) | a transfer = exactly two live legs (one out, one in, different accounts); same currency → equal amounts and no `fx_rate`; cross currency → `fx_rate` required and the legs must agree with it; a deleted transfer has no live legs |

### B1. RED first
```powershell
pytest --revision 0002
```
Expected: **`1 failed, 43 passed, 1 skipped`**. The failure is the SQL suite: **`39 of 67 checks passed`**. Read the list: #36–#54 are the fx and
decimal bugs ("no error was raised": the database *accepted* bad data); #56–#67 are auth tests (tables don't exist yet).
(`pytest` wipes **only** `mulya_test`; `mulya_dev` is untouched.)

### B2. Read `alembic/versions/sql/0003_currency_and_transfer_integrity.up.sql` (20 min)
Three ideas to understand:

1. **Why "deferred"?** A transfer is three rows (the `transfers` row and two legs). While you insert them one by one the data is
   *temporarily* inconsistent. A deferred constraint trigger waits until `COMMIT`. **Consequence for the API:** write a transfer and both
   legs in **one transaction** — you already agreed to that in CONVENTIONS §5.9. (This is also why the test fixtures are now wrapped in
   `BEGIN; … COMMIT;`.)
2. **The tolerance rule.** Real banks round. The check allows `|amount_in − amount_out × fx_rate| ≤ ½ unit of the destination currency + a
   tiny allowance for the rate's own 10 decimals`. Worked example: send **100.00 INR** at rate **0.012345** → exactly 1.2345 USD.
   Receiving **1.23** is accepted (off by 0.0045, less than half a cent); **1.22** is rejected (off by 0.0145). Tests #38 and #39.
3. **Errors are stable.** `SQLSTATE 23514` plus a constraint name (`transfer_consistent`, `amount_scale_per_currency`,
   `account_currency_locked`) — the API will map these to HTTP 422 without reading message text.

### B3. See 0003 go green, 0004 still red
```powershell
pytest --revision 0003
```
Expected: **`1 failed, 44 passed`**, SQL suite **`55 of 67`** — the only failures left are the auth tests (#56–#67), which Block C fixes.

### Done when
- [ ] You saw `39 of 67` at 0002 and `55 of 67` at 0003.
- [ ] You can explain in one sentence why a transfer and its legs must be written in one transaction.

---

## Block C — Migration 0004: auth tables (25 min)

Four tables, designed in `ARCHITECTURE.md` §9 (proposal — you accept it in Block E):

| Table | Holds | Key rules the **database** enforces |
|---|---|---|
| `email_login_codes` | one-time e-mail login codes | only a 32-byte **hash**; lower-case e-mail; `attempts <= max_attempts`; expires after it was created |
| `auth_identities` | Google / Apple identities | a `(provider, subject)` links to **one** user only |
| `devices` | one row per installed app / browser | platform ∈ android, ios, web |
| `refresh_tokens` | rotating tokens, **hash only** | a token's device must belong to the **same user** (composite foreign key); `revoked_at` ⇔ `revoked_reason`; unique hash; 32-byte hash |

**Design choice to know:** there is **no `sessions` table**. A session is simply a device's chain of refresh tokens (they share
`session_id`). That is the one place I merged the three tables your Day 2 plan listed; the reason is that a separate sessions table would
duplicate what the token chain already says.

### C1. Apply and verify
```powershell
alembic upgrade head          # on mulya_dev: runs 0003 then 0004
alembic current               # 0004 (head)
pytest                        # 45 passed
```
pgAdmin → refresh `mulya_dev`: you should now see 14 tables (the 9 from Day 2 + `currencies`, `auth_identities`, `email_login_codes`,
`devices`, `refresh_tokens`) plus `alembic_version` and the view.

Optional: in pgAdmin open `db\schema_v0_tests.sql` on **`mulya_test`** and run it (F5) → **`ALL PASS | 67 of 67`**. This is a different
execution mode from pytest (each statement commits separately); I verified it passes in both.

### C2. Commit the schema work
```powershell
cd ..
git add db backend/alembic backend/tests/test_health.py backend/tests/test_migrations.py backend/tests/test_currencies.py
git commit -m "feat(db): migrations 0003 (currencies, decimals, transfer integrity) and 0004 (auth tables); SQL tests 67 (MLY-009)"
```

### Done when
- [ ] `alembic current` → `0004 (head)`; `pytest` → 45 passed.
- [ ] Schema work committed.

---

## Block D — MLY-008: golden fixtures and the legacy mapper (60 min)

**The point.** Tests with invented data only prove your code agrees with *your imagination*. Real data surprises you. Your real backup did:
it is an **older shape** — no `budgetProfiles`, no goals, no recurring rules — and its four transactions point at a profile id (6) that
doesn't exist in the file. The mapper must cope, and its answers must **reconcile**: rebuild every balance from history and compare with
what the old app said.

### D1. What was built
| File | Role |
|---|---|
| `app/legacy/mapper.py` | **Pure** function `map_backup(json) → plan` (no database, no files). Anything surprising becomes an *issue*, not a crash |
| `tests/fixtures/legacy/pre_profile_v1_sanitized.json` | **Your real backup**, personal data removed (profile object, photo, every title) |
| `tests/fixtures/legacy/v226_multi_profile.json` | Synthetic v2.26 data covering cases your file can't: 2 profiles + 1 in the recycle bin, case-variant categories, a goal contribution booked as an expense, float noise, a month-end recurring rule, scheduled payments, goals |
| `tests/fixtures/legacy/v226_negative_balance.json` | The old app allowed negative balances by default; the new model can't hold one |
| `scripts/sanitize_legacy_backup.py` | Makes a safe fixture from any real backup |
| `scripts/legacy_report.py` | **Read-only** report: what would this backup become? |
| `tests/golden/` | 27 tests: 20 mapper (pure), 3 report-script, 4 that load the plan into the real database |

### D2. Run it and read the output
```powershell
pytest tests/golden -v          # 27 passed
python scripts\legacy_report.py tests\fixtures\legacy\pre_profile_v1_sanitized.json
```
Expected (your real data):
```
Primary Budget  Bank   legacy 525.00  rebuilt 525.00  OK
Primary Budget  Cash   legacy   0.00  rebuilt   0.00  OK
[INFO] profile_synthesized …  [INFO] category_name_normalized: 'Gadgets ' imported as 'Gadgets'  …
RESULT: OK to import
```
Notice the derived **opening balance of Bank = 1000.00** (525 left + 475 spent): the old app never stored it; the mapper works it out.
Then run the same on `v226_multi_profile.json` (exit code 0, many warnings) and `v226_negative_balance.json` (**BLOCKED**, exit code 1).

### D3. What the issue codes mean (and what *you* decide)
| Issue | Meaning | Your decision |
|---|---|---|
| `profile_synthesized` (info) | Older backup: profile 6 is referenced but absent; a workspace "Primary Budget" is created | none |
| `category_name_normalized` (info) | `'Gadgets '` (trailing space) imported as `Gadgets`; `Food`/`food`/`FOOD` merge into one | none |
| `categories_typed_as_expense` (info) | Old categories had no type; income categories are created from how transactions use them | none |
| `amount_rounded` (warning) | A legacy float like 19.999 has more than 2 decimals → rounded, and reported. Float noise like 0.30000000000000004 is cleaned silently | none |
| `goal_contribution_as_expense` (warning) | The old app booked savings-goal contributions as expenses (finding CR-07). They import as ordinary expenses | when to build goals (later) |
| `negative_implied_opening` (warning) | History's income exceeds the final balance: the old "Adjust balance" lowered it without a ledger entry. Imported as opening 0 + an explicit **adjustment expense** | none — CONVENTIONS §5.3 |
| `negative_legacy_balance` (**error**) | An account ended **below zero**. Your strict no-overdraft decision means it can't be imported as-is | fix it in the old app first, or model it as a credit card |
| `pendingPlans/goals_not_imported` (warning) | No target table yet; counted, never silently dropped | priority of those tables |
| `recurring_not_backposted` (info) | Rules import without generating past occurrences | none |

### D4. The bug reconciliation caught while I built this
My first mapper put the correction for `negative_implied_opening` in the **wrong direction** (an *income* adjustment). The reconciliation
test showed *Business Cash* at 220.00 instead of 20.00. That is exactly why every fixture is reconciled: the numbers disagreeing is the alarm.

### D5. Try it on your own current data (optional, 10 min)
Your phone/emulator may hold newer data than the April backup. In the old app: drawer → **Backup & Restore** → create a backup. Copy the
file **outside the repository** (e.g. `C:\mulya-private\`), then:
```powershell
python scripts\legacy_report.py C:\mulya-private\mulya_backup_<date>.json
```
It prints counts, reconciliation and issues, and never prints personal fields. **Never commit that raw file.** To turn it into a fixture:
`python scripts\sanitize_legacy_backup.py <raw> tests\fixtures\legacy\<name>.json`, check the output by eye, then commit the sanitized copy.

### D6. Commit
```powershell
git add .gitignore backend/pyproject.toml backend/README.md backend/app/legacy backend/scripts backend/tests/golden backend/tests/fixtures
git commit -m "feat(legacy): backup mapper, golden fixtures and reconciliation tests (MLY-008)"
```

### Done when
- [ ] `pytest tests/golden` → 27 passed; the report on your real fixture prints `OK to import`.
- [ ] You can say why the *Business Cash* mismatch mattered, and what `negative_legacy_balance` means for your own data.
- [ ] Committed.

---

## Block E — Docs review and decisions (30 min)

```powershell
git diff docs/        # CONVENTIONS v0.3, ARCHITECTURE v0.2, TEST_MATRIX v0.2
```
Read the "Changes in v0.3" list at the end of CONVENTIONS, then decide:

| # | Decision | My recommendation |
|---|---|---|
| 1 | **FX tolerance** (CONVENTIONS §5.11): half a destination unit + rate quantisation | **Accept.** If you want bank *fees* on a transfer, record them as a separate expense, not hidden inside the received amount. |
| 2 | **Auth proposal** (ARCHITECTURE §9): 6-digit code, 10 min, 5 attempts, 5 codes/hour; access token 15 min; refresh token 60 days, rotating, reuse = revoke the session; console mailer in dev | **Accept or edit each row now.** Day 4 codes exactly what is written here. |
| 3 | **ADR-A1** session = refresh-token chain, secrets stored hashed | **Accept.** |
| 4 | **ADR-I1** legacy import = pure mapper, deterministic ids, owner-only, goals/plans counted not imported | **Accept.** |

Two dependencies to put in your head now (no action today):
- **Google sign-in needs the final application ID** (MLY-002): the Google credential is tied to the app's package name. Settle MLY-002 before that step.
- **A real e-mail provider** is needed before any beta. Day 4 uses a console mailer, so it does not block.

```powershell
git add docs
git commit -m "docs: conventions v0.3, architecture v0.2 (auth proposal), test matrix v0.2 (MLY-005/006/007/015)"
git push
```

### Done when
- [ ] You answered the four decisions (edit the docs where you disagree) and pushed.

---

## Block F — Wrap-up (15 min)

### F1. Tracker (`00_Master_Tracker`)
| Task | Change |
|---|---|
| MLY-009 | `Done` / 100 — evidence: "migrations 0003+0004; SQL suite 67/67; pytest 45 passed". Note: "sync tables re-homed to MLY-022" |
| MLY-008 | `Done` / 100 — evidence: "tests/golden 27 passed; real backup reconciles through the database" |
| MLY-025 | stays `P2`; note "mapper + report exist; remaining: run on real data + a loader command" |
| MLY-015 | `In Progress` / 10 — note "schema + design proposal done; endpoints Day 4" |
| MLY-004 | stays Done; add note "populated states not captured: Add-income toggle, populated History/Goals, filter/edit/export/Adjust-balance dialogs, recurring creation, Recycle Bin with an item, dark Add/Analytics" |
| Add rows | (1) Capture those populated UX states — P2, before MLY-011 · (2) Decide e-mail provider for beta — P2 · (3) Google OAuth client — P1, **depends on MLY-002** · (4) Goals + scheduled-payment tables (so legacy rows can import) — P2 |
| `Next` (column N) | the first Day 4 task: auth endpoints |

### F2. Send me a Day 3 report
```
Day 3 report
- Done:               (task IDs)
- Evidence:           (commit hashes; screenshots of "39 of 67" at 0002, "55 of 67" at 0003, "45 passed" at head)
- Time per block:     A __ B __ C __ D __ E __ F __
- Decisions (only where you changed my recommendation):
- What the report said about your own backup (counts + any ERROR/WARNING codes; no personal data):
- Surprises / anything different on Windows or PostgreSQL 18:
```

### Day 4 preview (not part of today)
1. **Auth endpoints** exactly as accepted in §9: request code → verify → refresh (with rotation + reuse detection) → logout → `GET /me`, with a console mailer.
2. **Accounts and categories endpoints** (workspace comes from the token).
3. Your answers to the 4 decisions above decide how much I need to adjust.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ZoneInfoNotFoundError: No time zone found with key Asia/Kolkata` | `pip install tzdata` (re-run `pip install -e ".[dev]"`). Windows needs it. |
| `pytest --revision 0002` shows more than 1 failure | Send me the output; I want to see it. |
| `alembic upgrade head` fails on 0003 with a foreign-key error | A row in `accounts`/`workspaces` has a currency not in the catalogue. Check with `SELECT DISTINCT currency_code FROM accounts;` |
| `transfer … needs exactly one transfer_out and one transfer_in leg` when you try transfers by hand | You inserted the transfer and legs in separate transactions. Wrap them in one `BEGIN; … COMMIT;` |
| The SQL suite errors in pgAdmin with "current transaction is aborted" | Run it on a **new** Query Tool tab; an earlier failed statement left the session in an error state. |
| `legacy_report.py` prints `BLOCKED` on your own backup | Read the `ERROR` / `MISMATCH` lines; send me the codes (not the file). |
| A raw backup appears in `git status` | It matched nothing in `.gitignore`: rename it `mulya_backup_*.json` or keep it outside the repo. **Do not add it.** |

---

## What I could and could not verify

**Verified (run by me):** your Day 2 repo state and history scan (no secrets); your backend unchanged (17 passed; red at 0001 = 2 failed / 15 passed);
the psycopg/Windows claim in the library source; migrations 0003 and 0004 up, down and up again (0004 → base → head); the SQL suite **67/67 at head** in
both pytest mode and `psql` statement-by-statement mode; the red states — `--revision 0001` (3 failed), `0002` (1 failed, 39/67), `0003` (1 failed, 55/67);
**45 passed at head**; `ruff check` clean, also at `--target-version py311`; the mapper on all three fixtures, including reconciliation through the real database;
the sanitizer (no personal value remains in the committed fixture; keys and amounts unchanged); that the synthetic fixtures are deterministic; and a fresh unpack of the bundle onto a clean copy of your repo.

**Not verified:**
- Anything on **your machine**: I ran Linux, Python 3.12, PostgreSQL **16** with a stand-in for `uuidv7()` — not Windows, not Python 3.11, not PostgreSQL 18. (Every Postgres feature used — deferred constraint triggers, `trim_scale`, `sha256`, `gen_random_uuid` — is standard in 16 and is expected to behave the same in 18, but that is expectation, not a test.) Your Day 2 EOD shows your environment ran the earlier code green, which is a good sign.
- The `tzdata` dependency marker is standard, but I couldn't test a Windows install.
- The **auth design** is a proposal; no auth code exists yet.
- The scripts have a read-only report and tests, but the mapper has only seen **four** real transactions. Your own recent backup (D5) is the better test.
