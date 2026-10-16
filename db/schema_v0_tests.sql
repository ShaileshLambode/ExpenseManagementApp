-- =====================================================================
-- Mulya schema v0 — self-checking tests  (MLY-009 acceptance)
-- Run in pgAdmin > Query Tool on database mulya_dev or mulya_test
-- AFTER loading schema_v0.sql on an empty DB.
--
-- WARNING: starts with TRUNCATE … CASCADE.  Refuses to run unless the
-- database is named mulya_dev or mulya_test.
--
-- Amounts are in MAJOR units (rupees/dollars, not paise/cents):
--   450.00 = ₹450.00
--
-- Expected at schema v0.1 (after migration 0002): 33 rows PASS + final row
--   "ALL PASS | 33 of 33 checks passed".
-- On schema v0 (migration 0001 only) tests 24, 25, 26, 27, 29, 30 and 31 FAIL on
-- purpose: they are the reproducible bug list that migration 0002 fixes.
-- Tests 28, 32 and 33 already pass on v0 (they pin behaviour that was correct).
-- =====================================================================

DO $$
BEGIN
  IF current_database() NOT IN ('mulya_dev', 'mulya_test') THEN
    RAISE EXCEPTION 'Refusing to run: database is %, expected mulya_dev or mulya_test',
      current_database();
  END IF;
END $$;

TRUNCATE users CASCADE;

DROP TABLE IF EXISTS pg_temp.t_results;
CREATE TEMP TABLE t_results (seq serial, name text, ok boolean, detail text);

CREATE OR REPLACE FUNCTION pg_temp.expect_eq(
  p_name text, p_actual numeric, p_expected numeric)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO t_results (name, ok, detail)
  VALUES (p_name,
          p_actual IS NOT DISTINCT FROM p_expected,
          'actual=' || COALESCE(p_actual::text, 'NULL')
            || ' expected=' || p_expected);
END $$;

CREATE OR REPLACE FUNCTION pg_temp.expect_err(
  p_name text, p_sql text, p_state text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_noerr boolean := false;
BEGIN
  BEGIN
    EXECUTE p_sql;
    v_noerr := true;
    -- Statement unexpectedly succeeded: raise a sentinel so its side effects are rolled back.
    RAISE EXCEPTION 'rollback-sentinel' USING ERRCODE = 'XX999';
  EXCEPTION WHEN OTHERS THEN
    IF v_noerr THEN
      INSERT INTO t_results (name, ok, detail)
        VALUES (p_name, false, 'no error was raised');
    ELSIF SQLSTATE = p_state THEN
      INSERT INTO t_results (name, ok, detail)
        VALUES (p_name, true, 'rejected as expected (' || SQLSTATE || ')');
    ELSE
      INSERT INTO t_results (name, ok, detail)
        VALUES (p_name, false, 'wrong error ' || SQLSTATE || ': ' || SQLERRM);
    END IF;
  END;
END $$;

-- expect_ok: the statement must SUCCEED; its side effects are always rolled back.
CREATE OR REPLACE FUNCTION pg_temp.expect_ok(p_name text, p_sql text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_done boolean := false;
BEGIN
  BEGIN
    EXECUTE p_sql;
    v_done := true;
    RAISE EXCEPTION 'rollback-sentinel' USING ERRCODE = 'XX999';
  EXCEPTION WHEN OTHERS THEN
    IF v_done THEN
      INSERT INTO t_results (name, ok, detail) VALUES (p_name, true, 'accepted as expected');
    ELSE
      INSERT INTO t_results (name, ok, detail)
        VALUES (p_name, false, 'unexpected error ' || SQLSTATE || ': ' || SQLERRM);
    END IF;
  END;
END $$;

-- ---------------------------------------------------------------------
-- Fixtures  (fixed UUIDs so the expected numbers below are easy to follow)
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, display_name) VALUES
  ('00000000-0000-7000-8000-0000000000a1', 'test@example.test', 'Test User');

INSERT INTO workspaces (id, owner_user_id, name) VALUES
  ('00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000a1', 'Personal'),
  ('00000000-0000-7000-8000-0000000000b2', '00000000-0000-7000-8000-0000000000a1', 'Business');

INSERT INTO accounts (id, workspace_id, name, type, currency_code, opening_balance) VALUES
  -- Personal
  ('00000000-0000-7000-8000-0000000000c1',
   '00000000-0000-7000-8000-0000000000b1', 'Bank', 'bank', 'INR',  50000.00),  -- ₹50,000
  ('00000000-0000-7000-8000-0000000000c2',
   '00000000-0000-7000-8000-0000000000b1', 'Cash', 'cash', 'INR',   2000.00),  -- ₹2,000
  -- Business — opening balance must cover the hosting expense (₹199) so
  -- the overdraft guard does not fire during the recurring-rule test.
  ('00000000-0000-7000-8000-0000000000c3',
   '00000000-0000-7000-8000-0000000000b2', 'Biz',  'bank', 'INR',   1000.00);  -- ₹1,000

INSERT INTO categories (id, workspace_id, name, kind) VALUES
  ('00000000-0000-7000-8000-0000000000d1',
   '00000000-0000-7000-8000-0000000000b1', 'Groceries', 'expense'),
  ('00000000-0000-7000-8000-0000000000d2',
   '00000000-0000-7000-8000-0000000000b1', 'Salary',    'income'),
  ('00000000-0000-7000-8000-0000000000d3',
   '00000000-0000-7000-8000-0000000000b1', 'Transport', 'expense');

INSERT INTO transfers (id, workspace_id, occurred_on) VALUES
  ('00000000-0000-7000-8000-0000000000e1',
   '00000000-0000-7000-8000-0000000000b1', '2026-09-30');

INSERT INTO transactions
  (id, workspace_id, account_id, kind, amount, category_id, transfer_id, title, occurred_on, source)
VALUES
  -- f1  Bank  expense  ₹450.00  Groceries
  ('00000000-0000-7000-8000-0000000000f1',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c1',
   'expense', 450.00,
   '00000000-0000-7000-8000-0000000000d1', NULL,
   'Groceries', '2026-09-28', 'manual'),

  -- f2  Bank  income  ₹30,000.00  Salary
  ('00000000-0000-7000-8000-0000000000f2',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c1',
   'income', 30000.00,
   '00000000-0000-7000-8000-0000000000d2', NULL,
   'Salary', '2026-09-30', 'manual'),

  -- f3  Cash  expense  ₹120.00  Auto
  ('00000000-0000-7000-8000-0000000000f3',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c2',
   'expense', 120.00,
   '00000000-0000-7000-8000-0000000000d3', NULL,
   'Auto', '2026-09-29', 'manual'),

  -- f4  Bank  transfer_out  ₹5,000.00  → Cash
  ('00000000-0000-7000-8000-0000000000f4',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c1',
   'transfer_out', 5000.00,
   NULL, '00000000-0000-7000-8000-0000000000e1',
   'To cash', '2026-09-30', 'manual'),

  -- f5  Cash  transfer_in  ₹5,000.00  ← Bank
  ('00000000-0000-7000-8000-0000000000f5',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c2',
   'transfer_in', 5000.00,
   NULL, '00000000-0000-7000-8000-0000000000e1',
   'From bank', '2026-09-30', 'manual'),

  -- f6  Cash  income (adjustment)  ₹500.00  cash-count fix
  ('00000000-0000-7000-8000-0000000000f6',
   '00000000-0000-7000-8000-0000000000b1',
   '00000000-0000-7000-8000-0000000000c2',
   'income', 500.00,
   NULL, NULL,
   'Cash count fix', '2026-09-30', 'adjustment');

-- ---------------------------------------------------------------------
-- 1. Derived balances
--    Bank = 50000.00 - 450.00 + 30000.00 - 5000.00      = 74550.00
--    Cash =  2000.00 - 120.00  + 5000.00  +  500.00      =  7380.00
--    Total personal                                       = 81930.00
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('balance: Bank',
  (SELECT balance FROM v_account_balances WHERE name = 'Bank'),
  74550.00);

SELECT pg_temp.expect_eq('balance: Cash',
  (SELECT balance FROM v_account_balances WHERE name = 'Cash'),
  7380.00);

SELECT pg_temp.expect_eq('balance: total personal (transfer nets to zero)',
  (SELECT SUM(balance) FROM v_account_balances
   WHERE workspace_id = '00000000-0000-7000-8000-0000000000b1'),
  81930.00);

-- ---------------------------------------------------------------------
-- 2. Transfers: both legs must cancel out  (same-currency invariant)
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('transfers: unbalanced transfer count',
  (SELECT COUNT(*) FROM (
     SELECT transfer_id FROM transactions
     WHERE  transfer_id IS NOT NULL AND deleted_at IS NULL
     GROUP  BY transfer_id
     HAVING SUM(signed_amount) <> 0) x),
  0);

-- ---------------------------------------------------------------------
-- 3. Analytics rule: exclude transfers AND adjustments
--    expenses = 450.00 + 120.00 = 570.00
--    income   = 30000.00  (salary only; cash-count fix is 'adjustment')
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('analytics: Sept expenses',
  (SELECT COALESCE(SUM(amount), 0) FROM transactions
   WHERE  workspace_id = '00000000-0000-7000-8000-0000000000b1'
     AND  kind = 'expense' AND source <> 'adjustment' AND deleted_at IS NULL
     AND  occurred_on >= '2026-09-01' AND occurred_on < '2026-10-01'),
  570.00);

SELECT pg_temp.expect_eq('analytics: Sept income',
  (SELECT COALESCE(SUM(amount), 0) FROM transactions
   WHERE  workspace_id = '00000000-0000-7000-8000-0000000000b1'
     AND  kind = 'income' AND source <> 'adjustment' AND deleted_at IS NULL
     AND  occurred_on >= '2026-09-01' AND occurred_on < '2026-10-01'),
  30000.00);

-- ---------------------------------------------------------------------
-- 4. Soft delete (tombstone) changes the derived balance; restore reverses it.
--    Soft-deleting a row sets deleted_at IS NOT NULL, so the overdraft
--    trigger skips the balance check — the deletion always succeeds.
-- ---------------------------------------------------------------------
UPDATE transactions SET deleted_at = now()
WHERE id = '00000000-0000-7000-8000-0000000000f1';

SELECT pg_temp.expect_eq('soft delete: Bank after deleting groceries',
  (SELECT balance FROM v_account_balances WHERE name = 'Bank'),
  75000.00);   -- 74550.00 + 450.00

UPDATE transactions SET deleted_at = NULL
WHERE id = '00000000-0000-7000-8000-0000000000f1';

SELECT pg_temp.expect_eq('soft delete: Bank after restore',
  (SELECT balance FROM v_account_balances WHERE name = 'Bank'),
  74550.00);

-- ---------------------------------------------------------------------
-- 5. version + updated_at are bumped by the trigger.
--    Two UPDATEs above already touched f1: delete → v2, restore → v3.
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('version: groceries after delete + restore',
  (SELECT version FROM transactions
   WHERE id = '00000000-0000-7000-8000-0000000000f1'),
  3);

UPDATE transactions SET note = 'milk and eggs'
WHERE id = '00000000-0000-7000-8000-0000000000f1';

SELECT pg_temp.expect_eq('version: after note edit',
  (SELECT version FROM transactions
   WHERE id = '00000000-0000-7000-8000-0000000000f1'),
  4);

-- ---------------------------------------------------------------------
-- 6. Recurring: the same (rule, date) can only be generated once.
--    Biz opening_balance = 1000.00 so the ₹199.00 expense doesn't
--    trigger the overdraft guard.
-- ---------------------------------------------------------------------
INSERT INTO recurring_rules
  (id, workspace_id, account_id, kind, amount, title, frequency, anchor_on)
VALUES
  ('00000000-0000-7000-8000-0000000000aa',
   '00000000-0000-7000-8000-0000000000b2',
   '00000000-0000-7000-8000-0000000000c3',
   'expense', 199.00, 'Hosting', 'monthly', '2026-01-31');

INSERT INTO transactions
  (id, workspace_id, account_id, kind, amount, title, occurred_on, source)
VALUES
  ('00000000-0000-7000-8000-0000000000f7',
   '00000000-0000-7000-8000-0000000000b2',
   '00000000-0000-7000-8000-0000000000c3',
   'expense', 199.00, 'Hosting', '2026-09-30', 'recurring');

INSERT INTO recurring_occurrences (rule_id, occurrence_on, transaction_id) VALUES
  ('00000000-0000-7000-8000-0000000000aa', '2026-09-30',
   '00000000-0000-7000-8000-0000000000f7');

SELECT pg_temp.expect_err('recurring: duplicate occurrence rejected',
  $q$INSERT INTO recurring_occurrences (rule_id, occurrence_on, transaction_id)
     VALUES ('00000000-0000-7000-8000-0000000000aa', '2026-09-30',
             '00000000-0000-7000-8000-0000000000f1')$q$,
  '23505');   -- unique_violation

-- ---------------------------------------------------------------------
-- 7. Overdraft guard: an expense that would push a balance below zero
--    must be rejected with ERRCODE P0001.
--    Cash balance is ₹7,380.00 — spending ₹99,999.00 must fail.
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_err('reject: overdraft not allowed',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c2',
             'expense', 99999.00, '2026-09-30')$q$,
  'P0001');

-- ---------------------------------------------------------------------
-- 8. Constraints that must refuse bad data
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_err('reject: amount = 0',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c1', 'expense', 0, '2026-09-30')$q$,
  '23514');   -- check_violation

SELECT pg_temp.expect_err('reject: negative amount',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c1', 'expense', -5, '2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: transfer leg without transfer_id',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c1', 'transfer_out', 100, '2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: plain expense that carries a transfer_id',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, transfer_id, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c1', 'expense', 100,
             '00000000-0000-7000-8000-0000000000e1', '2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: account from ANOTHER workspace',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1',
             '00000000-0000-7000-8000-0000000000c3', 'expense', 100, '2026-09-30')$q$,
  '23503');   -- foreign_key_violation

SELECT pg_temp.expect_err('reject: category from ANOTHER workspace',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount, category_id, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b2',
             '00000000-0000-7000-8000-0000000000c3', 'expense', 100,
             '00000000-0000-7000-8000-0000000000d1', '2026-09-30')$q$,
  '23503');

SELECT pg_temp.expect_err('reject: duplicate account name (case-insensitive)',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code)
     VALUES ('00000000-0000-7000-8000-0000000000b1', 'bank', 'bank', 'INR')$q$,
  '23505');

SELECT pg_temp.expect_err('reject: duplicate email (different case)',
  $q$INSERT INTO users (email, display_name)
     VALUES ('TEST@example.test', 'Dup')$q$,
  '23505');

SELECT pg_temp.expect_err('reject: credit limit on a non-card account',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code, credit_limit)
     VALUES ('00000000-0000-7000-8000-0000000000b1', 'Wallet X', 'wallet', 'INR', 100000)$q$,
  '23514');

SELECT pg_temp.expect_err('reject: lower-case currency code',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code)
     VALUES ('00000000-0000-7000-8000-0000000000b1', 'Odd', 'bank', 'inr')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: writing the generated column',
  $q$UPDATE transactions SET signed_amount = 1
     WHERE id = '00000000-0000-7000-8000-0000000000f1'$q$,
  '428C9');   -- generated_always

-- ---------------------------------------------------------------------
-- 9. Overdraft guard hardening (v0.1)  -- tests 24-33
--    Each scenario runs inside expect_err / expect_ok, which roll back
--    everything the scenario did, so the fixtures above stay untouched.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.mk_account(
  p_name text, p_type text, p_opening numeric, p_limit numeric DEFAULT NULL)
RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE v_id uuid := uuidv7();
BEGIN
  INSERT INTO accounts (id, workspace_id, name, type, currency_code, opening_balance, credit_limit)
  VALUES (v_id, '00000000-0000-7000-8000-0000000000b1', p_name, p_type, 'INR', p_opening, p_limit);
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.mk_tx(p_account uuid, p_kind text, p_amount numeric)
RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE v_id uuid := uuidv7();
BEGIN
  INSERT INTO transactions (id, workspace_id, account_id, kind, amount, occurred_on)
  VALUES (v_id, '00000000-0000-7000-8000-0000000000b1', p_account, p_kind, p_amount, '2026-10-14');
  RETURN v_id;
END $$;

-- X starts at 0, receives 1000 income, spends 800. The income funds the spending.
CREATE OR REPLACE FUNCTION pg_temp.sc_soft_delete_funding_income() RETURNS void LANGUAGE plpgsql AS $$
DECLARE x uuid := pg_temp.mk_account('X', 'bank', 0); inc uuid;
BEGIN
  inc := pg_temp.mk_tx(x, 'income', 1000);
  PERFORM pg_temp.mk_tx(x, 'expense', 800);
  UPDATE transactions SET deleted_at = now() WHERE id = inc;      -- balance would become -800
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_hard_delete_funding_income() RETURNS void LANGUAGE plpgsql AS $$
DECLARE x uuid := pg_temp.mk_account('X', 'bank', 0); inc uuid;
BEGIN
  inc := pg_temp.mk_tx(x, 'income', 1000);
  PERFORM pg_temp.mk_tx(x, 'expense', 800);
  DELETE FROM transactions WHERE id = inc;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_move_funding_income() RETURNS void LANGUAGE plpgsql AS $$
DECLARE x uuid := pg_temp.mk_account('X', 'bank', 0); y uuid := pg_temp.mk_account('Y', 'bank', 0); inc uuid;
BEGIN
  inc := pg_temp.mk_tx(x, 'income', 1000);
  PERFORM pg_temp.mk_tx(x, 'expense', 800);
  UPDATE transactions SET account_id = y WHERE id = inc;          -- X would be -800
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_lower_opening_balance() RETURNS void LANGUAGE plpgsql AS $$
DECLARE x uuid := pg_temp.mk_account('X', 'bank', 1000);
BEGIN
  PERFORM pg_temp.mk_tx(x, 'expense', 800);
  UPDATE accounts SET opening_balance = 500 WHERE id = x;         -- 500 - 800 = -300
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_restore_overdrawing_expense() RETURNS void LANGUAGE plpgsql AS $$
DECLARE x uuid := pg_temp.mk_account('X', 'bank', 1000); e1 uuid;
BEGIN
  e1 := pg_temp.mk_tx(x, 'expense', 800);
  UPDATE transactions SET deleted_at = now() WHERE id = e1;       -- legitimately deleted
  PERFORM pg_temp.mk_tx(x, 'expense', 900);                       -- balance now 100
  UPDATE transactions SET deleted_at = NULL WHERE id = e1;        -- restoring 800 -> -700
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_card_up_to_limit_ok() RETURNS void LANGUAGE plpgsql AS $$
DECLARE c uuid := pg_temp.mk_account('Card', 'credit_card', 0, 10000);
BEGIN
  PERFORM pg_temp.mk_tx(c, 'expense', 10000);                     -- exactly at the limit: allowed
END $$;

CREATE OR REPLACE FUNCTION pg_temp.sc_card_over_limit() RETURNS void LANGUAGE plpgsql AS $$
DECLARE c uuid := pg_temp.mk_account('Card', 'credit_card', 0, 10000);
BEGIN
  PERFORM pg_temp.mk_tx(c, 'expense', 10000.01);                  -- one paisa over
END $$;

SELECT pg_temp.expect_err('guard: soft-deleting an income that funds spending',   'SELECT pg_temp.sc_soft_delete_funding_income()', 'P0001');
SELECT pg_temp.expect_err('guard: hard-deleting an income that funds spending',   'SELECT pg_temp.sc_hard_delete_funding_income()', 'P0001');
SELECT pg_temp.expect_err('guard: moving a funding income to another account',    'SELECT pg_temp.sc_move_funding_income()',        'P0001');
SELECT pg_temp.expect_err('guard: lowering opening_balance below spending',       'SELECT pg_temp.sc_lower_opening_balance()',      'P0001');
SELECT pg_temp.expect_err('guard: restoring a deleted expense that overdraws',    'SELECT pg_temp.sc_restore_overdrawing_expense()','P0001');
SELECT pg_temp.expect_err('guard: bank account with negative opening balance',
  $q$SELECT pg_temp.mk_account('Neg', 'bank', -1)$q$, '23514');
SELECT pg_temp.expect_err('guard: credit card without a credit limit',
  $q$SELECT pg_temp.mk_account('NoLimit', 'credit_card', 0)$q$, '23514');
SELECT pg_temp.expect_err('guard: credit card opening balance beyond its limit',
  $q$SELECT pg_temp.mk_account('Deep', 'credit_card', -2000, 1000)$q$, '23514');
SELECT pg_temp.expect_ok ('guard: credit card may spend exactly up to its limit', 'SELECT pg_temp.sc_card_up_to_limit_ok()');
SELECT pg_temp.expect_err('guard: credit card cannot exceed its limit',           'SELECT pg_temp.sc_card_over_limit()',            'P0001');

-- ---------------------------------------------------------------------
-- RESULT
-- ---------------------------------------------------------------------
SELECT seq,
       CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result,
       name,
       detail
FROM   t_results
UNION ALL
SELECT 9999,
       CASE WHEN bool_and(ok) THEN 'ALL PASS' ELSE 'HAS FAILURES' END,
       COUNT(*) FILTER (WHERE ok) || ' of ' || COUNT(*) || ' checks passed',
       NULL
FROM   t_results
ORDER  BY seq;
