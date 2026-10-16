# Mulya — Data & API Conventions (v0.2 — errata over v0.1, accepted 14 October 2026)

Task: **MLY-007**. Status: v0.1 was accepted on 14 Oct 2026. **v0.2 fixes drift** between this document and
`schema_v0.sql` / the Day 1 decisions (see "Changes in v0.2" at the end) and becomes binding when you commit it.
Every rule below exists to prevent a specific defect found in the v2.26 Android code
(finding IDs `CR-xx` refer to sheet `25_Codebase_Review`). If a rule is wrong for the product,
change it **here first**, then change the code — never the other way round.

---

## 1. Money  *(prevents CR-01)*

> **Decision (14 Oct 2026):** Amounts stored in **major units** (₹450.00, not 45000 paise). Multi-currency supported from launch. Alternative rejected: minor-unit integers (Splitwise-style paise) — rejected because the display layer matches storage, reducing conversion bugs.

| Rule | Detail |
|---|---|
| **Major units, decimal-safe storage** | Every amount is stored as a decimal number in the currency's major unit. INR: `450.00` = ₹450.00. PostgreSQL type: `NUMERIC(20, 8)` — never `float`, `real`, or `double precision`. |
| **No native floats, ever** | Not in Python (`Decimal`, never `float`), not in TypeScript (`string`-parsed `Decimal` library, never a JS `number` for arithmetic), not in JSON (send as string `"450.00"` to avoid JS float loss). |
| **Multi-currency from launch** | Each account carries its own `currency_code` (ISO 4217, upper-case: `INR`, `USD`, `EUR`). A transaction inherits its account's currency. Cross-currency transfers carry an `fx_rate` on the `transfers` row. |
| **Amounts are positive** | `amount > 0` always. Direction comes from `kind` (`income`, `expense`, `transfer_in`, `transfer_out`). Signed amount is computed as needed. |
| **Upper bound** | `amount <= 100_000_000_000.00` (100 billion major units). |
| **Decimals per currency** *(v0.2)* | The column allows 8 decimals (headroom for FX results), but a currency's own amounts never carry more than its exponent: INR/USD/EUR 2, JPY 0, KWD 3. **The API rejects extra digits** (it does not round). Schema v0 alone would store `450.123456789` rupees — verified. |
| **Wire format** *(v0.2)* | JSON number → rejected; decimal string → accepted (`app/core/money.py`). Always return money through a response model: FastAPI's raw-dict encoder turns `Decimal` into a float. |
| **Local (mobile) storage** *(v0.2)* | SQLite has no exact decimal type, and `SUM()` over text/real values is floating point. Store money as TEXT decimal strings, do all arithmetic in one `Money` module on a decimal library, and **never use SQL aggregates on money in SQLite**. (ADR-M1 in ARCHITECTURE.md; confirmed by a spike before mobile work starts.) |
| **Formatting is a client concern** | The API returns decimal strings. One formatter per client turns `(amount_string, currency_code, locale)` into display text (`₹1,23,456.00` for `en-IN`). No `₹` literals scattered through screens (CR-12). |
| **Division rule** | Splitting and percentages round half-up using decimal arithmetic; the remainder goes on the last item so parts always sum to the whole. |

## 2. Identifiers  *(prevents CR-09)*

- Every entity id is a **UUIDv7**. PostgreSQL 18 generates it with the built-in `uuidv7()`; clients may generate their own (needed for offline creation).
- **Client-generated ids make creates idempotent**: retrying `INSERT` with the same id must be a no-op / return the existing row, never a duplicate.
- The server never trusts a client id for anything except uniqueness: ownership always comes from the authenticated user + workspace.
- Legacy Android integer ids are **never** reused as ids. If old data is ever imported, keep a `legacy_id → new uuid` map in an import log.

## 3. Time & dates  *(prevents CR-17)*

| Column | Type | Meaning |
|---|---|---|
| `occurred_on` | `date` | The calendar day the user says it happened, in **their** timezone. All day/month/year reports group by this. Required. |
| `occurred_at` | `timestamptz` | Exact moment, optional (only if the user entered a time). |
| `created_at`, `updated_at`, `deleted_at` | `timestamptz` | Bookkeeping, stored in UTC by PostgreSQL. |

- Users have an IANA timezone (`Asia/Kolkata`); recurring rules carry their own.
- **Never** derive a report bucket from a device's current offset or from epoch milliseconds.
- Date pickers return a date, not an instant: send `YYYY-MM-DD`, never a UTC-midnight timestamp.
- "This month" is a half-open range on `occurred_on`: `>= first_day AND < first_day_of_next_month`. Never use "now" as the end of a range (CR-11).

## 4. Row lifecycle: tombstones & versions  *(prevents CR-10, prepares sync)*

Every synced table has `created_at`, `updated_at`, `deleted_at`, `version`.

- **Delete = set `deleted_at`.** Nothing user-visible is hard-deleted by the app. A background job hard-deletes rows whose `deleted_at` is older than **30 days** (the recycle-bin window).
- `updated_at` and `version` are maintained by a database trigger, not by client code. `version` starts at 1 and increases by 1 on every update.
- **Stale-write protection**: an update request carries the `version` it was based on. If the row's version is newer, the API answers `409 Conflict` with the current row. Same-field conflicts: last write wins by server time, and the overwritten value goes to the audit log.
- **Uniqueness ignores deleted rows** (partial unique indexes `WHERE deleted_at IS NULL`), so a deleted name can be reused.
- Restore = clear `deleted_at`.

## 5. Ledger rules  *(prevents CR-02, CR-03, CR-07)*

1. **Balances are derived, never stored, never synced.**
   `balance = opening_balance + SUM(signed_amount)` over the account's non-deleted transactions (view `v_account_balances`).
2. **No `balance_after` column.** Running balances are computed on read (window function) when a screen needs them.
3. **No silent balance edits.** "Adjust balance" creates a real transaction with `source = 'adjustment'`, so history always explains the balance.
4. **Adjustments move balances but are excluded from income/expense analytics** (as are transfers). Analytics filter: `kind IN ('income','expense') AND source <> 'adjustment' AND deleted_at IS NULL`.
5. **Transfers are two transactions** (`transfer_out` on the source account, `transfer_in` on the destination) joined by a `transfers` row. Both are written in **one database transaction**. Invariant (tested): for same-currency transfers the two legs sum to zero.
6. **Savings goals never book expenses.** A goal contribution is a transfer into the goal's envelope; deleting a goal must return the money or ask. (Design lands with MLY-027.)
7. **Credit cards** use the same sign rule: spending pushes the balance negative (amount owed). Statement cycles and interest are out of scope for v1.
8. **Overspending is always blocked** *(enforced since migration 0002, schema v0.1)*. No account may fall below its floor:
   `0` for every type except `credit_card`, whose floor is `-credit_limit`. There is no per-account override flag (CR-14).
   - Enforced **by the database**: triggers run on INSERT, UPDATE and DELETE of `transactions` (so soft-deleting or moving an income that funds later spending is rejected too) and on changes to an account's `opening_balance`, `credit_limit` or `type`.
   - Each check first locks the account row (`FOR NO KEY UPDATE`), so two simultaneous expenses cannot both pass.
   - Account rules: bank/cash/wallet/other need `opening_balance >= 0`; a credit card **must** have a `credit_limit` and `opening_balance >= -credit_limit`.
   - Violation = SQLSTATE `P0001` with constraint name `account_no_overdraft`; the API maps that to **422** with a clear message. Never match on message text.
   - Known limit (accepted): the guard checks the balance *now*, not the running balance on each past date, so a back-dated expense is allowed if today's balance stays above the floor.
   - Concurrency rule for the API: write transfer legs in `account_id` order and retry once on SQLSTATE `40P01` (deadlock).
9. **Every mutation that touches more than one row runs in one database transaction** and has a test (CR-04, CR-05).
10. Categories are referenced by **id**, are typed (`income`/`expense`), and belong to one workspace (CR-08). Cross-workspace references are rejected by composite foreign keys.

## 6. Recurring rules  *(prevents CR-06)*

- A rule stores `anchor_on` (first occurrence **and** the day-of-month to aim for), `frequency`, `interval_count`, optional `ends_on`, and its own `timezone`.
- **Occurrence n** of a monthly rule = anchor month + n·interval, day = `min(anchor day, last day of that month)`.
  Example: anchor Jan 31 → Feb 28, Mar 31, Apr 30, May 31 (it does **not** drift to the 28th forever).
- **The generator catches up**: every run creates *all* missing occurrences up to today (user's timezone), not one per run.
- **Idempotent**: `recurring_occurrences` has primary key `(rule_id, occurrence_on)`; a second insert fails, so double-posting after a crash is impossible.
- If the user deletes a generated transaction, its occurrence row remains, so it is not re-created.
- Paused or deleted rules, and rules of deleted workspaces, generate nothing.

## 7. Naming

- Tables: plural `snake_case` (`transactions`). Columns: `snake_case`.
- Suffixes: `_on` = `date` · `_at` = `timestamptz` · `_id` = uuid FK. Money columns carry **no** suffix (they are `NUMERIC` major units: `amount`, `opening_balance`, `credit_limit`).
- Enumerations are `text` + `CHECK (... IN (...))`, **not** PostgreSQL `ENUM` types (adding a value is a one-line constraint change, not a type migration).
- Constraints and indexes are named explicitly when they matter (`accounts_ws_id_uq`).
- API JSON uses the same `snake_case` names; the generated TypeScript client is the only translation layer.

## 8. Privacy defaults  *(prevents CR-26)*

- Profile = display name + timezone + locale. No date of birth, phone, city, state or gender unless a feature needs it and the user opts in.
- Backups/exports are versioned, exclude photos by default, and may be passphrase-encrypted.

---

## Decisions taken with this document

| # | Decision | Alternative rejected |
|---|---|---|
| 1 | Currency on the account, not the transaction | Per-transaction currency (allows mismatches; needed only for multi-currency wallets, later) |
| 2 | Adjustments are transactions with `source='adjustment'` | A separate adjustments table (a second ledger to reconcile) |
| 3 | Enums as `text + CHECK` | PostgreSQL `ENUM` types |
| 4 | `date` + optional `timestamptz` | One `timestamptz` and derive the day from device offset |
| 5 | No stored `next_run` on recurring rules | Stored next-run column (drifts, can't catch up safely) |
| 6 | Money in major units, `NUMERIC(20,8)`, decimal strings on the wire *(Day 1, owner decision)* | Integer minor units (paise) — rejected so storage matches display |
| 7 | Multi-currency from launch (`accounts.currency_code`, `transfers.fx_rate`) *(Day 1)* | INR-only launch |
| 8 | Overspending always blocked, database-enforced *(Day 1; hardened in v0.1)* | Per-account allow-negative flag |

## Open questions (answer when the feature is designed, not now)

- **Ledger edits.** Schema v0.1 lets transactions be edited (every edit bumps `version`) and soft-deleted; nothing makes them immutable. Recommendation: keep editing (users expect it; the old app allowed it) and add an audit log of old values later. If you want a strictly immutable ledger, say so: it removes PATCH on amount/account and changes the API.
- **Cross-currency transfers are not enforced yet.** `fx_rate` exists but nothing requires it when account currencies differ, or checks the two legs against it (verified: INR→USD 1,000 → 1,000,000 with `fx_rate` NULL is accepted). Planned as a database constraint under MLY-009; until then the API must reject it.
- Do goals need their own table of envelopes or can an envelope be an account with `type = 'goal'`?
- Attachments (receipts): object storage keys on `transactions`, or a separate `attachments` table?

---

## Changes in v0.2 (errata — review these)

1. §1: added *Decimals per currency*, *Wire format*, *Local (mobile) storage* (consequences of the major-unit decision).
2. §5.1: formula now uses the real column names (`opening_balance`, `signed_amount`).
3. §5.8: rewritten to match what the database enforces after migration 0002 (the v0 trigger could be bypassed by deleting or moving an income, by lowering an opening balance, and by two concurrent writes).
4. §7: removed the `_minor` suffix rule (obsolete after the major-unit decision).
5. Decisions table: recorded the three Day 1 decisions that were missing.
6. Open questions: resolved the fx question into a concrete gap; added the ledger-immutability question (the Day 1 EOD says "immutable ledger"; the schema does not implement that).
