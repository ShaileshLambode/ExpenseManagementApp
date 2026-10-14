-- =====================================================================
-- Mulya PostgreSQL schema v0  (aligns with CONVENTIONS.md v0.1)
-- Target: PostgreSQL 18 (uses the built-in uuidv7()).
-- Run against an EMPTY database (e.g. mulya_dev) in pgAdmin > Query Tool.
--
-- Changes from original draft (per user decisions, 14 Oct 2026):
--   * Amounts in MAJOR units (NUMERIC(20,8))  — not paise/cents integers.
--   * Multi-currency: accounts carry currency_code; transfers carry fx_rate.
--   * Overspending is ALWAYS blocked (DB trigger, no per-account override).
--   * Column renames: opening_balance_minor → opening_balance,
--                     credit_limit_minor   → credit_limit,
--                     amount_minor         → amount,
--                     signed_amount_minor  → signed_amount.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Helper 1: on every UPDATE bump updated_at and version.
-- ---------------------------------------------------------------------
CREATE FUNCTION touch_row() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  NEW.version    := OLD.version + 1;
  RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- Helper 2: block overdraft after every transaction insert / update.
--   * credit_card accounts may go down to -credit_limit (amount owed).
--   * All other accounts must stay >= 0.
--   * Soft-deleted rows are already excluded from the balance view,
--     so we skip the check when deleted_at is being set.
-- ---------------------------------------------------------------------
CREATE FUNCTION check_no_overdraft() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_balance      NUMERIC(20,8);
  v_credit_limit NUMERIC(20,8);
  v_type         text;
BEGIN
  -- Row is being (or already) soft-deleted — no overdraft risk.
  IF NEW.deleted_at IS NOT NULL THEN
    RETURN NEW;
  END IF;

  -- The AFTER trigger sees the committed state, so v_account_balances
  -- already reflects the new / updated row.
  SELECT vab.balance, a.credit_limit, a.type
  INTO   v_balance, v_credit_limit, v_type
  FROM   v_account_balances vab
  JOIN   accounts a ON a.id = vab.account_id
  WHERE  vab.account_id = NEW.account_id;

  IF v_type = 'credit_card' THEN
    IF v_balance < -COALESCE(v_credit_limit, 0) THEN
      RAISE EXCEPTION 'Overdraft not allowed: account % balance would be %',
        NEW.account_id, v_balance USING ERRCODE = 'P0001';
    END IF;
  ELSE
    IF v_balance < 0 THEN
      RAISE EXCEPTION 'Overdraft not allowed: account % balance would be %',
        NEW.account_id, v_balance USING ERRCODE = 'P0001';
    END IF;
  END IF;

  RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
  id            uuid        PRIMARY KEY DEFAULT uuidv7(),
  email         text        NOT NULL,
  display_name  text        NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 80),
  timezone      text        NOT NULL DEFAULT 'Asia/Kolkata',   -- IANA name
  locale        text        NOT NULL DEFAULT 'en-IN',
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       integer     NOT NULL DEFAULT 1,
  CONSTRAINT users_email_shape CHECK (position('@' in email) > 1)
);
CREATE UNIQUE INDEX users_email_uq ON users (lower(email)) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- workspaces  (what the Android app calls "budget profiles")
-- ---------------------------------------------------------------------
CREATE TABLE workspaces (
  id             uuid        PRIMARY KEY DEFAULT uuidv7(),
  owner_user_id  uuid        NOT NULL REFERENCES users (id),
  name           text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
  currency_code  char(3)     NOT NULL DEFAULT 'INR' CHECK (currency_code ~ '^[A-Z]{3}$'),
  color          text,
  icon           text,
  archived_at    timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  deleted_at     timestamptz,
  version        integer     NOT NULL DEFAULT 1
);
CREATE INDEX workspaces_owner_idx ON workspaces (owner_user_id) WHERE deleted_at IS NULL;

-- Single owner at launch; the table exists so shared households
-- (roles 'editor', 'viewer') are an ALTER TABLE, not a migration.
CREATE TABLE workspace_members (
  workspace_id  uuid        NOT NULL REFERENCES workspaces (id),
  user_id       uuid        NOT NULL REFERENCES users (id),
  role          text        NOT NULL DEFAULT 'owner' CHECK (role IN ('owner')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, user_id)
);

-- ---------------------------------------------------------------------
-- accounts.  Balance is NEVER stored — see view v_account_balances.
--   Credit card: spending pushes balance negative (amount owed).
--   opening_balance and credit_limit are in the account's own currency.
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
  id               uuid          PRIMARY KEY DEFAULT uuidv7(),
  workspace_id     uuid          NOT NULL REFERENCES workspaces (id),
  name             text          NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
  type             text          NOT NULL CHECK (type IN ('cash','bank','wallet','credit_card','other')),
  currency_code    char(3)       NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
  opening_balance  NUMERIC(20,8) NOT NULL DEFAULT 0,
  credit_limit     NUMERIC(20,8) CHECK (credit_limit IS NULL OR credit_limit >= 0),
  sort_order       integer       NOT NULL DEFAULT 0,
  archived_at      timestamptz,
  created_at       timestamptz   NOT NULL DEFAULT now(),
  updated_at       timestamptz   NOT NULL DEFAULT now(),
  deleted_at       timestamptz,
  version          integer       NOT NULL DEFAULT 1,
  CONSTRAINT accounts_ws_id_uq       UNIQUE (workspace_id, id),   -- target for composite FKs
  CONSTRAINT accounts_limit_cards_only CHECK (credit_limit IS NULL OR type = 'credit_card')
);
CREATE UNIQUE INDEX accounts_name_uq ON accounts (workspace_id, lower(name)) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- categories  (per workspace, typed, referenced by id — never by name)
-- ---------------------------------------------------------------------
CREATE TABLE categories (
  id            uuid        PRIMARY KEY DEFAULT uuidv7(),
  workspace_id  uuid        NOT NULL REFERENCES workspaces (id),
  name          text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 60),
  kind          text        NOT NULL CHECK (kind IN ('income','expense')),
  icon          text,
  color         text,
  sort_order    integer     NOT NULL DEFAULT 0,
  archived_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       integer     NOT NULL DEFAULT 1,
  CONSTRAINT categories_ws_id_uq UNIQUE (workspace_id, id)
);
CREATE UNIQUE INDEX categories_name_uq
  ON categories (workspace_id, kind, lower(btrim(name))) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- transfers: a grouping row.  The money lives in two transaction legs
-- (kind = transfer_out on one account, transfer_in on the other).
-- fx_rate: units of destination currency per unit of source currency.
-- NULL for same-currency transfers; required when currencies differ.
-- ---------------------------------------------------------------------
CREATE TABLE transfers (
  id            uuid           PRIMARY KEY DEFAULT uuidv7(),
  workspace_id  uuid           NOT NULL REFERENCES workspaces (id),
  occurred_on   date           NOT NULL,
  fx_rate       NUMERIC(20,10) CHECK (fx_rate IS NULL OR fx_rate > 0),
  note          text,
  created_at    timestamptz    NOT NULL DEFAULT now(),
  updated_at    timestamptz    NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       integer        NOT NULL DEFAULT 1,
  CONSTRAINT transfers_ws_id_uq UNIQUE (workspace_id, id)
);

-- ---------------------------------------------------------------------
-- transactions  (the ledger)
--   amount is always POSITIVE (major currency units, e.g. ₹450.00).
--   Direction comes from kind; signed_amount is the generated column
--   that balances sum over.
--   source = 'adjustment' marks reconciliation entries: they move
--   balances but are excluded from income/expense analytics.
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
  id             uuid          PRIMARY KEY DEFAULT uuidv7(),
  workspace_id   uuid          NOT NULL,
  account_id     uuid          NOT NULL,
  kind           text          NOT NULL CHECK (kind IN ('income','expense','transfer_in','transfer_out')),
  amount         NUMERIC(20,8) NOT NULL CHECK (amount > 0 AND amount <= 100000000000.00),
  signed_amount  NUMERIC(20,8) GENERATED ALWAYS AS (
                   CASE WHEN kind IN ('income','transfer_in') THEN amount ELSE -amount END
                 ) STORED,
  category_id    uuid,
  transfer_id    uuid,
  title          text          NOT NULL DEFAULT '',
  note           text,
  occurred_on    date          NOT NULL,          -- user's calendar date
  occurred_at    timestamptz,                     -- exact moment, optional
  source         text          NOT NULL DEFAULT 'manual'
                   CHECK (source IN ('manual','recurring','import','adjustment')),
  created_at     timestamptz   NOT NULL DEFAULT now(),
  updated_at     timestamptz   NOT NULL DEFAULT now(),
  deleted_at     timestamptz,
  version        integer       NOT NULL DEFAULT 1,
  -- composite FKs: a row can only reference accounts/categories/transfers
  -- of its OWN workspace — cross-workspace references are impossible.
  FOREIGN KEY (workspace_id, account_id)  REFERENCES accounts   (workspace_id, id),
  FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
  FOREIGN KEY (workspace_id, transfer_id) REFERENCES transfers  (workspace_id, id),
  CONSTRAINT transactions_transfer_link
    CHECK ((kind IN ('transfer_in','transfer_out')) = (transfer_id IS NOT NULL)),
  CONSTRAINT transactions_transfer_no_category
    CHECK (transfer_id IS NULL OR category_id IS NULL)
);
CREATE INDEX transactions_ws_date_idx
  ON transactions (workspace_id, occurred_on DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX transactions_account_date_idx
  ON transactions (account_id, occurred_on DESC)            WHERE deleted_at IS NULL;
CREATE INDEX transactions_transfer_idx
  ON transactions (transfer_id)                             WHERE transfer_id IS NOT NULL;
CREATE INDEX transactions_category_idx
  ON transactions (category_id)                             WHERE category_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- recurring rules + occurrences
--   No "next_run" column: the next date is computed from anchor_on and
--   the occurrences table, so a missed run can always catch up.
--   PRIMARY KEY (rule_id, occurrence_on) makes generation idempotent.
-- ---------------------------------------------------------------------
CREATE TABLE recurring_rules (
  id              uuid          PRIMARY KEY DEFAULT uuidv7(),
  workspace_id    uuid          NOT NULL,
  account_id      uuid          NOT NULL,
  category_id     uuid,
  kind            text          NOT NULL CHECK (kind IN ('income','expense')),
  amount          NUMERIC(20,8) NOT NULL CHECK (amount > 0 AND amount <= 100000000000.00),
  title           text          NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
  frequency       text          NOT NULL CHECK (frequency IN ('daily','weekly','monthly','yearly')),
  interval_count  integer       NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 366),
  anchor_on       date          NOT NULL,   -- first occurrence AND day-of-month to aim for
  ends_on         date,
  timezone        text          NOT NULL DEFAULT 'Asia/Kolkata',
  status          text          NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused')),
  created_at      timestamptz   NOT NULL DEFAULT now(),
  updated_at      timestamptz   NOT NULL DEFAULT now(),
  deleted_at      timestamptz,
  version         integer       NOT NULL DEFAULT 1,
  FOREIGN KEY (workspace_id, account_id)  REFERENCES accounts   (workspace_id, id),
  FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
  CONSTRAINT recurring_rules_end_after_anchor CHECK (ends_on IS NULL OR ends_on >= anchor_on)
);

CREATE TABLE recurring_occurrences (
  rule_id         uuid        NOT NULL REFERENCES recurring_rules (id),
  occurrence_on   date        NOT NULL,
  transaction_id  uuid        NOT NULL REFERENCES transactions (id),
  created_at      timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rule_id, occurrence_on),
  CONSTRAINT recurring_occ_tx_uq UNIQUE (transaction_id)
);
-- If the user deletes a generated transaction, its occurrence row stays,
-- so the engine will not silently re-create it.

-- ---------------------------------------------------------------------
-- Derived balances  (never stored, never synced)
-- ---------------------------------------------------------------------
CREATE VIEW v_account_balances AS
SELECT a.id            AS account_id,
       a.workspace_id,
       a.name,
       a.type,
       a.currency_code,
       a.opening_balance,
       a.opening_balance + COALESCE(SUM(t.signed_amount), 0) AS balance
FROM   accounts a
LEFT JOIN transactions t
       ON t.account_id = a.id AND t.deleted_at IS NULL
WHERE  a.deleted_at IS NULL
GROUP BY a.id;

-- ---------------------------------------------------------------------
-- Triggers: updated_at / version  (touch_row)
-- ---------------------------------------------------------------------
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY
    ARRAY['users','workspaces','accounts','categories','transfers','transactions','recurring_rules']
  LOOP
    EXECUTE format(
      'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION touch_row()',
      tbl || '_touch', tbl);
  END LOOP;
END $$;

-- Overdraft guard: fires AFTER INSERT or UPDATE so the view already
-- reflects the new / changed row.  Raises P0001 on violation.
CREATE TRIGGER transactions_overdraft_check
  AFTER INSERT OR UPDATE ON transactions
  FOR EACH ROW EXECUTE FUNCTION check_no_overdraft();

COMMIT;
