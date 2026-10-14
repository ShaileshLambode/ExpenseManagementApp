# Mulya — Data & API Conventions (v0.1, DRAFT)

Task: **MLY-007**. Status: draft, becomes binding once you commit it.
Every rule below exists to prevent a specific defect found in the v2.26 Android code
(finding IDs `CR-xx` refer to sheet `25_Codebase_Review`). If a rule is wrong for the product,
change it **here first**, then change the code — never the other way round.

---

## 1. Money  *(prevents CR-01)*

| Rule | Detail |
|---|---|
| **Integer minor units** | Every amount is an integer count of the currency's smallest unit. INR: `45000` = ₹450.00 (paise). |
| **No floats, ever** | Not in PostgreSQL (`bigint`, never `double precision`/`real`), not in Python (`int`, never `float`), not in TypeScript (`number` holding an integer, never a decimal), not in JSON. |
| **Currency lives on the account** | `accounts.currency_code` (ISO 4217, upper-case, `INR`). A transaction has no currency of its own; it inherits its account's. This makes a currency mismatch impossible. |
| **Amounts are positive** | `amount_minor > 0` always. Direction comes from `kind` (`income`, `expense`, `transfer_in`, `transfer_out`). Balances sum the generated `signed_amount_minor`. |
| **Upper bound** | `amount_minor <= 10_000_000_000_000` (₹100 billion). Keeps every value and every realistic sum below JavaScript's safe-integer limit (2⁵³−1), so JSON numbers never lose precision. |
| **Exponent by currency** | INR/USD/EUR use 2 decimal places; JPY uses 0; some currencies use 3. One shared lookup table in code (`currency_exponent`) drives parsing and formatting. Launch scope is INR only, but nothing else may hard-code `100`. |
| **Formatting is a client concern** | The API returns integers. One formatter per client turns `(amount_minor, currency_code, locale)` into text (`₹1,23,456.00` for `en-IN`). No `₹` literals scattered through screens (CR-12). |
| **Division rule** | Splitting, percentages and FX round in **integer arithmetic**, half-up, and put the leftover paise on the last item so parts always sum to the whole. |

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
   `balance = opening_balance_minor + SUM(signed_amount_minor)` over the account's non-deleted transactions (view `v_account_balances`).
2. **No `balance_after` column.** Running balances are computed on read (window function) when a screen needs them.
3. **No silent balance edits.** "Adjust balance" creates a real transaction with `source = 'adjustment'`, so history always explains the balance.
4. **Adjustments move balances but are excluded from income/expense analytics** (as are transfers). Analytics filter: `kind IN ('income','expense') AND source <> 'adjustment' AND deleted_at IS NULL`.
5. **Transfers are two transactions** (`transfer_out` on the source account, `transfer_in` on the destination) joined by a `transfers` row. Both are written in **one database transaction**. Invariant (tested): for same-currency transfers the two legs sum to zero.
6. **Savings goals never book expenses.** A goal contribution is a transfer into the goal's envelope; deleting a goal must return the money or ask. (Design lands with MLY-027.)
7. **Credit cards** use the same sign rule: spending pushes the balance negative (amount owed). Statement cycles and interest are out of scope for v1.
8. **Overspending policy is per account** (a future `allow_negative` flag), not a global toggle that some screens ignore (CR-14).
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
- Suffixes: `_minor` = integer money · `_on` = `date` · `_at` = `timestamptz` · `_id` = uuid FK.
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

## Open questions (answer when the feature is designed, not now)

- Cross-currency transfers: one `fx_rate` on the `transfers` row, each leg in its own account currency?
- Do goals need their own table of envelopes or can an envelope be an account with `type = 'goal'`?
- Attachments (receipts): object storage keys on `transactions`, or a separate `attachments` table?
