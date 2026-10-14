-- =====================================================================
-- Mulya PostgreSQL schema v0 (DRAFT for MLY-009)
-- Target: PostgreSQL 18 (uses the built-in uuidv7()).
-- Run against an EMPTY database (e.g. mulya_dev) in pgAdmin > Query Tool.
-- Rules this file follows are written down in CONVENTIONS.md.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- Helper: on every UPDATE bump updated_at and version.
-- (version is what the API/sync layer uses to detect stale writes.)
-- ---------------------------------------------------------------------
CREATE FUNCTION touch_row() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  NEW.version    := OLD.version + 1;
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
-- workspaces (what the Android app calls "budget profiles")
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
-- (roles 'editor', 'viewer') are an ALTER, not a migration.
CREATE TABLE workspace_members (
  workspace_id  uuid        NOT NULL REFERENCES workspaces (id),
  user_id       uuid        NOT NULL REFERENCES users (id),
  role          text        NOT NULL DEFAULT 'owner' CHECK (role IN ('owner')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, user_id)
);

-- ---------------------------------------------------------------------
-- accounts. Balance is NEVER stored: see view v_account_balances.
-- Credit card = same sign rule; spending pushes the balance negative (owed).
-- ---------------------------------------------------------------------
CREATE TABLE accounts (
  id                     uuid        PRIMARY KEY DEFAULT uuidv7(),
  workspace_id           uuid        NOT NULL REFERENCES workspaces (id),
  name                   text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
  type                   text        NOT NULL CHECK (type IN ('cash','bank','wallet','credit_card','other')),
  currency_code          char(3)     NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
  opening_balance_minor  bigint      NOT NULL DEFAULT 0,
  credit_limit_minor     bigint      CHECK (credit_limit_minor IS NULL OR credit_limit_minor >= 0),
  sort_order             integer     NOT NULL DEFAULT 0,
  archived_at            timestamptz,
  created_at             timestamptz NOT NULL DEFAULT now(),
  updated_at             timestamptz NOT NULL DEFAULT now(),
  deleted_at             timestamptz,
  version                integer     NOT NULL DEFAULT 1,
  CONSTRAINT accounts_ws_id_uq UNIQUE (workspace_id, id),   -- target for composite FKs
  CONSTRAINT accounts_limit_cards_only CHECK (credit_limit_minor IS NULL OR type = 'credit_card')
);
CREATE UNIQUE INDEX accounts_name_uq ON accounts (workspace_id, lower(name)) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- categories (per workspace, typed, referenced by id -- never by name)
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
CREATE UNIQUE INDEX categories_name_uq ON categories (workspace_id, kind, lower(btrim(name))) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- transfers: a grouping row. The money lives in two transactions legs
-- (kind = transfer_out on one account, transfer_in on the other).
-- ---------------------------------------------------------------------
CREATE TABLE transfers (
  id            uuid        PRIMARY KEY DEFAULT uuidv7(),
  workspace_id  uuid        NOT NULL REFERENCES workspaces (id),
  occurred_on   date        NOT NULL,
  note          text,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  deleted_at    timestamptz,
  version       integer     NOT NULL DEFAULT 1,
  CONSTRAINT transfers_ws_id_uq UNIQUE (workspace_id, id)
);

-- ---------------------------------------------------------------------
-- transactions (the ledger)
--   amount_minor is always POSITIVE; direction comes from kind.
--   signed_amount_minor is generated and is what balances sum.
--   source = 'adjustment' marks reconciliation entries: they move balances
--   but are excluded from income/expense analytics (see CONVENTIONS.md).
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
  id                   uuid        PRIMARY KEY DEFAULT uuidv7(),
  workspace_id         uuid        NOT NULL,
  account_id           uuid        NOT NULL,
  kind                 text        NOT NULL CHECK (kind IN ('income','expense','transfer_in','transfer_out')),
  amount_minor         bigint      NOT NULL CHECK (amount_minor > 0 AND amount_minor <= 10000000000000),
  signed_amount_minor  bigint      GENERATED ALWAYS AS (
                         CASE WHEN kind IN ('income','transfer_in') THEN amount_minor ELSE -amount_minor END
                       ) STORED,
  category_id          uuid,
  transfer_id          uuid,
  title                text        NOT NULL DEFAULT '',
  note                 text,
  occurred_on          date        NOT NULL,          -- the user's calendar date
  occurred_at          timestamptz,                   -- exact moment, optional
  source               text        NOT NULL DEFAULT 'manual' CHECK (source IN ('manual','recurring','import','adjustment')),
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  deleted_at           timestamptz,
  version              integer     NOT NULL DEFAULT 1,
  -- composite FKs: a row can only point at accounts/categories/transfers of ITS OWN workspace
  FOREIGN KEY (workspace_id, account_id)  REFERENCES accounts   (workspace_id, id),
  FOREIGN KEY (workspace_id, category_id) REFERENCES categories (workspace_id, id),
  FOREIGN KEY (workspace_id, transfer_id) REFERENCES transfers  (workspace_id, id),
  CONSTRAINT transactions_transfer_link
    CHECK ((kind IN ('transfer_in','transfer_out')) = (transfer_id IS NOT NULL)),
  CONSTRAINT transactions_transfer_no_category
    CHECK (transfer_id IS NULL OR category_id IS NULL)
);
CREATE INDEX transactions_ws_date_idx      ON transactions (workspace_id, occurred_on DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX transactions_account_date_idx ON transactions (account_id, occurred_on DESC)            WHERE deleted_at IS NULL;
CREATE INDEX transactions_transfer_idx     ON transactions (transfer_id)                              WHERE transfer_id IS NOT NULL;
CREATE INDEX transactions_category_idx     ON transactions (category_id)                              WHERE category_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- recurring rules + occurrences
--   No "next_run" column: the next date is computed from anchor_on and the
--   occurrences table, so a missed run can always catch up.
--   PRIMARY KEY (rule_id, occurrence_on) is what makes generation idempotent.
-- ---------------------------------------------------------------------
CREATE TABLE recurring_rules (
  id              uuid        PRIMARY KEY DEFAULT uuidv7(),
  workspace_id    uuid        NOT NULL,
  account_id      uuid        NOT NULL,
  category_id     uuid,
  kind            text        NOT NULL CHECK (kind IN ('income','expense')),
  amount_minor    bigint      NOT NULL CHECK (amount_minor > 0 AND amount_minor <= 10000000000000),
  title           text        NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 120),
  frequency       text        NOT NULL CHECK (frequency IN ('daily','weekly','monthly','yearly')),
  interval_count  integer     NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 366),
  anchor_on       date        NOT NULL,     -- first occurrence AND the day-of-month to aim for (e.g. the 31st)
  ends_on         date,
  timezone        text        NOT NULL DEFAULT 'Asia/Kolkata',
  status          text        NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused')),
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),
  deleted_at      timestamptz,
  version         integer     NOT NULL DEFAULT 1,
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
-- Derived balances (never stored, never synced)
-- ---------------------------------------------------------------------
CREATE VIEW v_account_balances AS
SELECT a.id            AS account_id,
       a.workspace_id,
       a.name,
       a.type,
       a.currency_code,
       a.opening_balance_minor,
       a.opening_balance_minor + COALESCE(SUM(t.signed_amount_minor), 0) AS balance_minor
FROM accounts a
LEFT JOIN transactions t
       ON t.account_id = a.id AND t.deleted_at IS NULL
WHERE a.deleted_at IS NULL
GROUP BY a.id;

-- ---------------------------------------------------------------------
-- updated_at / version triggers
-- ---------------------------------------------------------------------
DO $$
DECLARE tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY['users','workspaces','accounts','categories','transfers','transactions','recurring_rules']
  LOOP
    EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION touch_row()',
                   tbl || '_touch', tbl);
  END LOOP;
END $$;

COMMIT;
