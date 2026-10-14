# Mulya — Day 1 Implementation Guide
**Wednesday 30 September 2026** · Builder: Shailesh (solo) · Launch scope: Android + Web

> **How to use this file.** Work top to bottom. Every block has *Steps*, a *Done when* checklist, and the exact
> tracker changes to make. Do not mark a task Done until its checklist is true (your own Rule 2).
> If something fails, stop and send me the error text or a screenshot — do not improvise around it.

---

## 0. Where we stand (what your answers changed)

| You told me | What it changes |
|---|---|
| No users, the app was only for you | Native Android **freezes at v2.26** — no hotfixes (closes **MLY-003**). The legacy importer (**MLY-025**) drops to **P2** and only ever exists to import *your own* data. Your real backup is still the best test data we have, so golden fixtures (**MLY-008**) stay P0. |
| Launch = Android + Web | iOS is **out of launch scope**. Sign in with Apple is not needed until iOS ships. Store work = Google Play only. Expo keeps iOS cheap to add later. |
| Solo builder | Strictly sequential. **One row "In Progress" at a time.** Spikes are timeboxed. |
| Application ID "later" | Fine for today. It becomes a hard blocker before Google sign-in / push setup (**MLY-015, MLY-020**). Until then, never publish anything using `com.example.*`. |
| Public repo, "make it private later" | Your call. I suggest starting the rebuild in a **new private repo** (Block A) so no history from the old app comes with it. |

Where the code stands today: `mulya-v2` builds and runs on your Pixel 9 emulator (your screenshot proves this), and
PostgreSQL 18 + pgAdmin are installed. That unlocks two things I could not do myself: seeing the real UI (MLY-004)
and running SQL against a real Postgres 18 (MLY-009).

---

## 1. Today's goal

Three deliverables, each with evidence:

| # | Tracker task | Deliverable | Evidence |
|---|---|---|---|
| 1 | **MLY-004** Capture current UX | ~30 screenshots + a keep/redesign/remove table | `docs/legacy-ux/` committed |
| 2 | **MLY-007** Conventions spec | `CONVENTIONS.md` reviewed by you and committed | commit hash |
| 3 | **MLY-009** PostgreSQL schema | `schema_v0.sql` running on Postgres 18, **22/22 tests passing** | screenshot of the result grid |

Plus two small closers: **MLY-003** (decision: freeze v2.26) and a 20-minute pass over **20_Decisions**.

| Block | Task | Time |
|---|---|---|
| A | Housekeeping, new repo | 15 min |
| B | Capture the current UX (MLY-004) | 60 min |
| C | Decisions review | 20 min |
| D | Conventions spec (MLY-007) | 45 min |
| E | Postgres schema v0 (MLY-009) | 75 min |
| F | Wrap-up and tracker | 15 min |
| | **Total** | **≈ 3 h 50 min** |

**Short on time?** Do **A → D → E**. That gets the foundation (rules + a working ledger) done; B and C can move to tomorrow morning.

---

## Block A — Housekeeping (15 min)

*Assumption: Windows + PowerShell (I inferred it from the CRLF in your Android Studio status bar). Tell me if not.*

### A1. Freeze the legacy app with a tag
```powershell
cd <path>\ExpenseManagementApp
git checkout mulya-v2
git tag -a native-v2.26 -m "Final native Android version (frozen). Rebuild lives in the 'mulya' repo."
git push origin native-v2.26
```
Optional but recommended: GitHub → that repo → **Settings → General → Danger Zone → Change visibility → Private**.
It takes 30 seconds. Note it only stops *new* viewing; copies or clones made while it was public are not recalled.
Since the personal backup in it is your own data, that trade-off is yours to make.

### A2. Create the rebuild repo (recommended)
1. GitHub → **New repository** → name `mulya` → **Private** → do *not* add a README/licence → Create.
2. Locally:
```powershell
cd <where you keep projects>
mkdir mulya; cd mulya
git init -b main
New-Item -ItemType Directory -Force docs\legacy-ux, docs\tracking, db, services, apps | Out-Null
@"
.env
*.pem
.venv/
__pycache__/
node_modules/
mulya_backup_*.json
*.backup.json
"@ | Set-Content .gitignore
```
3. Download today's files from this chat and place them:

| File | Put it at |
|---|---|
| `CONVENTIONS.md` | `docs\CONVENTIONS.md` |
| `mulya_schema_v0.sql` | `db\schema_v0.sql` |
| `mulya_schema_v0_tests.sql` | `db\schema_v0_tests.sql` |
| `Mulya_Day01_Implementation_Guide.md` | `docs\tracking\Day01.md` |
| your Progress Tracker `.xlsx` | `docs\tracking\` (optional) |

4. First commit:
```powershell
git add .
git commit -m "chore: initial structure, conventions draft, schema v0 draft"
git remote add origin https://github.com/ShaileshLambode/mulya.git
git push -u origin main
```

### Done when
- [ ] Tag `native-v2.26` exists on GitHub.
- [ ] `mulya` repo exists, private, one commit pushed.

**Tracker:** MLY-003 → Status `Done`, Progress `100`, Owner `Shailesh`, Evidence
`Decision: v2.26 frozen, no users (Q-14, D-20). Tag native-v2.26`.

---

## Block B — MLY-004 Capture the current UX (60 min)

**Why now:** the whole redesign (MLY-011 design tokens, MLY-023 transaction flow, MLY-024 dashboard) starts from
what the app actually does. I read the code; only you can see it. Every screenshot is a requirement.

### B1. Capture order matters — empty states first
1. Emulator: long-press the app → App info → **Storage → Clear storage** (wipes data; that is what we want).
2. Launch fresh and capture **in this order** (fresh install shows screens that disappear once you have data):
   splash → 3 onboarding slides → initial-balance dialog → empty Home → empty History → empty Goals.
3. Enter the initial balances, then **populate** the app so screens aren't empty:
   - Either restore the sample: drag `mulya_backup_2026_04_26_19_33.json` (in the old repo root) onto the emulator window
     (it lands in Downloads) → drawer → **Backup & Restore** → restore. It only has 4 transactions, so also…
   - …add by hand: 1 income, 5–6 expenses across different categories, 1 upcoming action, 1 recurring item,
     1 goal with a contribution, and delete one transaction (so the Recycle Bin has content).

### B2. How to take a screenshot
- **Easiest:** the camera-frame icon in Android Studio's *Running Devices* toolbar (top right of your emulator panel) → Save.
- **Alternative:**
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
cmd /c "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe exec-out screencap -p > docs\legacy-ux\10_home.png"
```
(Use `cmd /c` — PowerShell's own `>` corrupts binary files.)

Name files `NN_screen-name.png` using the numbers below. Keep them in `docs\legacy-ux\` of the **new private repo**, not the old public one.

### B3. Screen checklist

Bottom bar: **Home · Analytics · Add · Goals · Profile**. Drawer: Transaction History, Backup & Restore, Recycle Bin,
Review & Feedback, Settings, Help & Docs, About Us, What's New.

| # | Screen | How to reach it |
|---|---|---|
| 01 | Splash | Kill the app, cold start |
| 02–04 | Onboarding slides 1–3 | After a fresh start |
| 05 | Initial balance dialog | After onboarding |
| 06 | Home — empty | Fresh data |
| 07 | History — empty | Drawer → Transaction History |
| 08 | Goals — empty | Bottom bar → Goals |
| 09 | Home — populated (top) | After populating |
| 10 | Home — populated (scrolled: charts, upcoming actions) | Scroll down |
| 11 | Workspace ("budget profile") switcher / create | Tap the profile/workspace control on Home |
| 12 | Adjust balance dialog | From the balance card |
| 13 | Add transaction — expense | Bottom bar → Add |
| 14 | Add transaction — income | Toggle on the same sheet |
| 15 | Category picker + manage/add category | Inside the add sheet |
| 16 | Add Upcoming Action sheet | Home |
| 17 | All Upcoming Actions sheet | Home |
| 18 | Recurring — create form | Wherever the app lets you create one (note the path) |
| 19 | Plans screen | Try to reach it. **If you can't, write "unreachable" — that is a finding.** |
| 20 | Analytics — Spending tab | Bottom bar → Analytics |
| 21 | Analytics — Income tab, and week/month/year toggles | Same screen |
| 22 | Goals — populated | Bottom bar → Goals |
| 23 | Add goal dialog | Goals |
| 24 | Contribute dialog | Goals → a goal |
| 25 | Profile | Bottom bar → Profile |
| 26 | History — populated | Drawer |
| 27 | History filter dialog | History |
| 28 | Edit transaction dialog | History → tap an item |
| 29 | Export dialog(s) | History and/or Settings |
| 30 | Settings | Drawer |
| 31 | Backup & Restore | Drawer |
| 32 | Recycle Bin (with one item) | Drawer |
| 33–36 | Help & Docs · About Us · What's New · Review & Feedback | Drawer |
| 37–39 | **Dark theme**: Home, Add, Analytics | Settings → theme |

### B4. Write the observation table
Create `docs\legacy-ux\README.md` with one row per screen:

```markdown
| # | Screen | File | What it does (1 line) | Keep / Redesign / Remove | Note |
|---|--------|------|-----------------------|--------------------------|------|
| 13 | Add transaction | 13_add-expense.png | Keypad, category, BANK/CASH | Keep | Fast; no date or note field |
```
While clicking, write down (at the bottom of the file) honest answers to:
1. Which flow do you use most, and how many taps is it?
2. What did you fumble or have to think about?
3. Which screens have you never opened since building them?
4. What would you delete without regret?
5. What is the one thing this app does better than the apps you tried?

### Done when
- [ ] ≥ 30 screenshots committed, named by the numbers above.
- [ ] README table has a Keep/Redesign/Remove verdict for every screen.
- [ ] Question 19 (Plans) and 18 (Recurring) answered with the actual path, or "unreachable".
- [ ] Five answers to the questions above.

**Tracker:** MLY-004 → `In Progress` when you start; `Done` when the boxes are ticked. Evidence: commit hash + count.

---

## Block C — Decisions review (20 min)

Open sheet **20_Decisions**. Each row has my verdict and note. Your job is to accept or overrule — nothing is "Approved" until you say so.

1. **Verdict = Agree** (D-01, 02, 08, 09, 13, 14, 15, 17, 19, 21): set *Decision Status* = `Approved`, *Validation* = `Approved`.
2. **Verdict = Agree with changes** (D-03, 04, 05, 06, 07, 10, 11, 12): read the note; if you accept it, approve.
   If not, edit the *Choice* text to what you want and approve that.
3. Three decisions need your judgement. My recommendation for each:

| ID | Question | Recommendation |
|---|---|---|
| **D-20** | What happens to the native app? | **Approve "freeze v2.26."** No users, so nothing to maintain. |
| **D-18** | Keep a "use without an account" mode? | **Keep it, mobile only.** An offline-first app already stores everything in local SQLite, so "no account" just means sync is off until sign-in — near-zero extra cost, and it preserves the offline/privacy pitch. Web requires an account. |
| **D-16** | Build or adopt the sync engine? | **Leave as `Needs Research`.** The MLY-010 spike answers it. Don't decide today. |

### Done when
- [ ] Every row in 20_Decisions is Approved, Rejected, or explicitly left `Needs Research` (only D-16).

*This is what unblocks **MLY-005** (Finalize target architecture) tomorrow.*

---

## Block D — MLY-007 Conventions spec (45 min)

**Why before the schema:** conventions are the rules the schema and the API obey. They exist to stop the specific bugs
found in v2.26 (floating-point money, editable balances, integer ids, name-based categories, drifting recurring dates).

### Steps
1. Read `docs\CONVENTIONS.md` slowly (≈ 20 min). It is short on purpose.
2. Answer these six questions. Each is a yes, or a one-line edit to the document:

| # | Question | My default in the doc |
|---|---|---|
| 1 | Store money in paise (integers), INR only at launch? | Yes |
| 2 | Currency belongs to the **account**, not each transaction? | Yes |
| 3 | "Adjust balance" becomes a visible ledger entry in history? | Yes — the old dialog silently overwrote the balance |
| 4 | May an expense exceed the account balance? | Allowed by default; a per-account flag later. (The old Add sheet blocked it; the old setting said allowed — they contradicted each other.) |
| 5 | Deleted items stay recoverable for 30 days? | Yes (matches today's Recycle Bin) |
| 6 | A monthly item set for the 31st lands on Feb 28, then **Mar 31**, Apr 30? | Yes — the old app drifted to the 28th forever |

3. Edit the file where you disagree. Change the top line from `DRAFT` to `v0.1 — accepted <date>`.
4. Commit:
```powershell
git add docs/CONVENTIONS.md
git commit -m "docs: accept data & API conventions v0.1"
git push
```

### Done when
- [ ] You answered all six questions (edits made where you disagreed).
- [ ] Committed and pushed.

**Tracker:** MLY-007 → `Done`, 100. Evidence: commit hash.

---

## Block E — MLY-009 PostgreSQL schema v0 (75 min)

You will create two databases, load the schema, and run a script that proves the ledger rules hold.

**Honest note on verification.** I ran `schema_v0.sql` and the tests on PostgreSQL **16** in my sandbox, with a one-line stand-in
for `uuidv7()` (which only exists natively in Postgres 18). All 22 checks passed there. **You are the first person to run it on 18**,
so if anything differs, that is exactly the information I need.

### E1. Create the databases
pgAdmin → expand **Servers → PostgreSQL 18** (enter the password you set at install) → right-click **Databases → Create → Database…**
→ name `mulya_dev` → Save. Repeat for `mulya_test`.

*Why two?* `mulya_dev` is for the app later. `mulya_test` is disposable: the test script wipes it every run.

### E2. Confirm you are really on 18
Right-click `mulya_test` → **Query Tool**, run:
```sql
SELECT version();
SELECT uuidv7();
```
- First result must say `PostgreSQL 18.x`. Second must return a UUID.
- If `uuidv7()` does not exist you are connected to an older server (pgAdmin can list several, or Postgres may be on port 5433). Fix that before going on.

### E3. Load the schema into **both** databases
For each of `mulya_dev` and `mulya_test`: Query Tool → **Open File** icon → `db\schema_v0.sql` → **Execute (F5)**.
Expected: "Query returned successfully". Verify:
```sql
SELECT table_name, table_type FROM information_schema.tables
WHERE table_schema = 'public' ORDER BY 1;
```
Expected **10 rows**: 9 tables (`accounts, categories, recurring_occurrences, recurring_rules, transactions, transfers, users, workspace_members, workspaces`) and 1 view (`v_account_balances`).

### E4. Run the tests — on `mulya_test` only
Query Tool on **mulya_test** → open `db\schema_v0_tests.sql` → F5.
(The script begins with `TRUNCATE … CASCADE` and **refuses to run on any database not named `mulya_dev` or `mulya_test`.**)

Expected final grid: 22 rows `PASS` and a last row **`ALL PASS | 22 of 22 checks passed`**. Screenshot it — that is your evidence.

### E5. Read the tests as a spec
| Test group | The rule it protects | v2.26 defect prevented |
|---|---|---|
| Balances (Bank 7,455,000 · Cash 738,000 · total 8,193,000) | Balance = opening + signed sum; a transfer moves money without changing the total | CR-02, CR-07 |
| Unbalanced transfers = 0 | Both legs of a transfer cancel out | no transfer concept |
| Analytics excludes transfers and adjustments | Income/expense reports aren't polluted | goals booked as expenses |
| Soft delete / restore | Deleting a transaction changes the balance; restoring reverses it | balances drifting |
| Version 3 → 4 | The trigger bumps `version` on every update | prepares sync |
| Duplicate recurring occurrence rejected | Double-posting is impossible | CR-06 |
| 11 "reject" cases | Bad data is refused by the **database**, not by hoping the app is careful | CR-04/05/08/09 |

### E6. Explore it (10 min)
In `mulya_test` right after the tests (the data stays):
```sql
-- 1. Derived balances (nothing is stored). Expect 3 rows: Bank 7455000 · Biz -19900 · Cash 738000
SELECT name, opening_balance_minor, balance_minor FROM v_account_balances ORDER BY name;

-- 2. Ledger with a running balance, computed on read (no balance_after column!)
SELECT t.occurred_on, t.title, t.kind, t.amount_minor,
       a.opening_balance_minor
         + SUM(t.signed_amount_minor) OVER (PARTITION BY t.account_id ORDER BY t.occurred_on, t.id)
         AS balance_after_minor
FROM transactions t JOIN accounts a ON a.id = t.account_id
WHERE t.deleted_at IS NULL AND a.name = 'Bank'
ORDER BY t.occurred_on, t.id;
```
Query 2 should end at **7455000**, the same number as the view. Then break something on purpose and read the error:
```sql
INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, occurred_on)
VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c1','expense',-5,'2026-09-30');
```

### E7. Why the schema looks the way it does
- **Composite foreign keys** `(workspace_id, account_id)` make cross-workspace references impossible at the database level.
- **`signed_amount_minor` is a generated column**, so nobody can write a sign that disagrees with `kind`.
- **Partial unique indexes** (`WHERE deleted_at IS NULL`) let a deleted name be reused.
- **`recurring_occurrences` primary key** `(rule_id, occurrence_on)` is the entire idempotency mechanism for recurring items.
- **No `balance` and no `next_run` columns** — both are derived, because stored copies are what drifted in the Android app.

### E8. What v0 deliberately does *not* cover (so MLY-009 stays open)
| Gap | Lands with |
|---|---|
| Goals / savings envelopes | MLY-027 |
| Scheduled one-off payments (today's "Plans") | after the ledger API (MLY-019) |
| Devices, sessions, refresh tokens | MLY-015 (auth) |
| Sync tables (change feed, cursors) | MLY-010 spike → MLY-022 |
| Idempotency-key table, audit log, attachments, notifications | when each feature is designed |

### Done when
- [ ] Both databases exist and hold the 9 tables + 1 view.
- [ ] `schema_v0_tests.sql` on `mulya_test` shows **ALL PASS 22 of 22** (screenshot saved).
- [ ] Both SQL files committed:
```powershell
git add db/
git commit -m "db: schema v0 + self-checking tests (22/22 on PostgreSQL 18)"
git push
```

**Tracker:** MLY-009 → `In Progress`, Progress `60`. Evidence: commit hash + "22/22 tests on PG18".
(It reaches 100 once the gaps in E8 that block the P0 path — auth tables and sync — are designed.)

---

## Block F — Wrap-up (15 min)

### F1. Tracker changes (`00_Master_Tracker`)
Status list is exactly: Backlog · Planned · In Progress · Blocked · Review · Done · Cancelled.

| Task | Change |
|---|---|
| MLY-001 | Priority `P2`. Note: "Owner deferred; rebuild uses new private repo; make legacy repo private." (There is no "Deferred" status; keep `Backlog`.) |
| MLY-002 | Dependency: "Needed before Google sign-in setup (MLY-015/020)". Stays Backlog. |
| MLY-003 | `Done` / 100 (Block A) |
| MLY-004 | `Done` / 100 after Block B |
| MLY-005 | Dependency: "Approve 20_Decisions (D-16 deferred to MLY-010)". Put the word `Next` in column **N** — the dashboard's "Next Task" cell reads it |
| MLY-007 | `Done` / 100 after Block D |
| MLY-009 | `In Progress` / 60 |
| MLY-025 | Priority `P2`. Note: "Only to import the owner's own data." |
| all touched rows | Owner `Shailesh`, Start Date today |

### F2. Send me a Day 1 report
```
Day 1 report
- Done:            (task IDs)
- Evidence:        (commit hashes, screenshot count, test result screenshot)
- Time per block:  A __ B __ C __ D __ E __ F __
- Surprises/blockers:
- Answers to Block D questions (only where you changed my default):
- Anything in the running app that surprised you:
```
I will audit it against the acceptance criteria before we call anything Done.

---

## Tomorrow (preview, not part of today)

1. **MLY-005** Finalize target architecture (≈ 1 h): turn the approved decisions into a one-page architecture and a repo layout.
2. **MLY-006** Test matrix (≈ 1 h): what is tested where (backend, mobile, web, migration), starting from the 22 checks you just ran.
3. **MLY-012** Bootstrap FastAPI + Alembic: the first code, which loads today's schema through migrations and connects to `mulya_dev`.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `function uuidv7() does not exist` | You are connected to a Postgres older than 18. Check `SELECT version();` and the port (5432 vs 5433). |
| pgAdmin can't connect | Service `postgresql-x64-18` must be running (Windows *Services*). The password is the one set during install. |
| Tests print `HAS FAILURES` | Screenshot the grid and send it. Each row's `detail` column says what it expected and what it got. |
| Tests say "Refusing to run" | You're in the wrong database. Switch to `mulya_test`. This guard is intentional. |
| `adb` not found | Use the full path in B2, or add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH. |
| Screenshot files won't open | You used PowerShell `>` instead of `cmd /c …`. Redo with B2. |
| Restore in the app does nothing | Check the file is in the emulator's Downloads; otherwise skip it and add data by hand. |
