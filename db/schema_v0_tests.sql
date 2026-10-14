-- =====================================================================
-- Mulya schema v0 -- self-checking tests (MLY-009 acceptance)
-- Run in pgAdmin > Query Tool on database mulya_dev (or mulya_test)
-- AFTER mulya_schema_v0.sql. The last result grid shows PASS / FAIL.
--
-- WARNING: this script starts with TRUNCATE ... CASCADE. It refuses to run
-- unless the database is named mulya_dev or mulya_test.
--
-- Money is in minor units (paise): 45000 = Rs 450.00
-- =====================================================================

DO $$
BEGIN
  IF current_database() NOT IN ('mulya_dev', 'mulya_test') THEN
    RAISE EXCEPTION 'Refusing to run: database is %, expected mulya_dev or mulya_test', current_database();
  END IF;
END $$;

TRUNCATE users CASCADE;

DROP TABLE IF EXISTS pg_temp.t_results;
CREATE TEMP TABLE t_results (seq serial, name text, ok boolean, detail text);

CREATE OR REPLACE FUNCTION pg_temp.expect_eq(p_name text, p_actual numeric, p_expected numeric)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO t_results (name, ok, detail)
  VALUES (p_name, p_actual IS NOT DISTINCT FROM p_expected,
          'actual=' || COALESCE(p_actual::text, 'NULL') || ' expected=' || p_expected);
END $$;

CREATE OR REPLACE FUNCTION pg_temp.expect_err(p_name text, p_sql text, p_state text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE p_sql;
    INSERT INTO t_results (name, ok, detail) VALUES (p_name, false, 'no error was raised');
  EXCEPTION WHEN OTHERS THEN
    IF SQLSTATE = p_state THEN
      INSERT INTO t_results (name, ok, detail) VALUES (p_name, true, 'rejected as expected (' || SQLSTATE || ')');
    ELSE
      INSERT INTO t_results (name, ok, detail) VALUES (p_name, false, 'wrong error ' || SQLSTATE || ': ' || SQLERRM);
    END IF;
  END;
END $$;

-- ---------------------------------------------------------------------
-- Fixtures (fixed ids so the expected numbers below are easy to follow)
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, display_name) VALUES
  ('00000000-0000-7000-8000-0000000000a1', 'test@example.test', 'Test User');

INSERT INTO workspaces (id, owner_user_id, name) VALUES
  ('00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000a1', 'Personal'),
  ('00000000-0000-7000-8000-0000000000b2', '00000000-0000-7000-8000-0000000000a1', 'Business');

INSERT INTO accounts (id, workspace_id, name, type, currency_code, opening_balance_minor) VALUES
  ('00000000-0000-7000-8000-0000000000c1', '00000000-0000-7000-8000-0000000000b1', 'Bank', 'bank', 'INR', 5000000),  -- Rs 50,000.00
  ('00000000-0000-7000-8000-0000000000c2', '00000000-0000-7000-8000-0000000000b1', 'Cash', 'cash', 'INR',  200000),  -- Rs  2,000.00
  ('00000000-0000-7000-8000-0000000000c3', '00000000-0000-7000-8000-0000000000b2', 'Biz',  'bank', 'INR',       0);

INSERT INTO categories (id, workspace_id, name, kind) VALUES
  ('00000000-0000-7000-8000-0000000000d1', '00000000-0000-7000-8000-0000000000b1', 'Groceries', 'expense'),
  ('00000000-0000-7000-8000-0000000000d2', '00000000-0000-7000-8000-0000000000b1', 'Salary',    'income'),
  ('00000000-0000-7000-8000-0000000000d3', '00000000-0000-7000-8000-0000000000b1', 'Transport', 'expense');

INSERT INTO transfers (id, workspace_id, occurred_on) VALUES
  ('00000000-0000-7000-8000-0000000000e1', '00000000-0000-7000-8000-0000000000b1', '2026-09-30');

INSERT INTO transactions (id, workspace_id, account_id, kind, amount_minor, category_id, transfer_id, title, occurred_on, source) VALUES
  ('00000000-0000-7000-8000-0000000000f1', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c1', 'expense',      45000, '00000000-0000-7000-8000-0000000000d1', NULL, 'Groceries',   '2026-09-28', 'manual'),
  ('00000000-0000-7000-8000-0000000000f2', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c1', 'income',    3000000, '00000000-0000-7000-8000-0000000000d2', NULL, 'Salary',      '2026-09-30', 'manual'),
  ('00000000-0000-7000-8000-0000000000f3', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c2', 'expense',      12000, '00000000-0000-7000-8000-0000000000d3', NULL, 'Auto',        '2026-09-29', 'manual'),
  ('00000000-0000-7000-8000-0000000000f4', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c1', 'transfer_out', 500000, NULL,                                   '00000000-0000-7000-8000-0000000000e1', 'To cash',  '2026-09-30', 'manual'),
  ('00000000-0000-7000-8000-0000000000f5', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c2', 'transfer_in',  500000, NULL,                                   '00000000-0000-7000-8000-0000000000e1', 'From bank','2026-09-30', 'manual'),
  ('00000000-0000-7000-8000-0000000000f6', '00000000-0000-7000-8000-0000000000b1', '00000000-0000-7000-8000-0000000000c2', 'income',       50000, NULL,                                   NULL, 'Cash count fix', '2026-09-30', 'adjustment');

-- ---------------------------------------------------------------------
-- 1. Derived balances
--    Bank = 5,000,000 - 45,000 + 3,000,000 - 500,000            = 7,455,000
--    Cash =   200,000 - 12,000 +   500,000 +  50,000            =   738,000
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('balance: Bank',  (SELECT balance_minor FROM v_account_balances WHERE name = 'Bank'), 7455000);
SELECT pg_temp.expect_eq('balance: Cash',  (SELECT balance_minor FROM v_account_balances WHERE name = 'Cash'),  738000);
SELECT pg_temp.expect_eq('balance: total (transfer nets to zero)',
       (SELECT SUM(balance_minor) FROM v_account_balances WHERE workspace_id = '00000000-0000-7000-8000-0000000000b1'), 8193000);

-- ---------------------------------------------------------------------
-- 2. Transfers: both legs must cancel out
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('transfers: unbalanced transfer count',
       (SELECT COUNT(*) FROM (
          SELECT transfer_id FROM transactions
          WHERE transfer_id IS NOT NULL AND deleted_at IS NULL
          GROUP BY transfer_id HAVING SUM(signed_amount_minor) <> 0) x), 0);

-- ---------------------------------------------------------------------
-- 3. Analytics rule: exclude transfers AND adjustments
--    expenses = 45,000 + 12,000 = 57,000 ; income = 3,000,000 (salary only)
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('analytics: Sept expenses',
       (SELECT COALESCE(SUM(amount_minor),0) FROM transactions
         WHERE workspace_id = '00000000-0000-7000-8000-0000000000b1'
           AND kind = 'expense' AND source <> 'adjustment' AND deleted_at IS NULL
           AND occurred_on >= '2026-09-01' AND occurred_on < '2026-10-01'), 57000);
SELECT pg_temp.expect_eq('analytics: Sept income',
       (SELECT COALESCE(SUM(amount_minor),0) FROM transactions
         WHERE workspace_id = '00000000-0000-7000-8000-0000000000b1'
           AND kind = 'income' AND source <> 'adjustment' AND deleted_at IS NULL
           AND occurred_on >= '2026-09-01' AND occurred_on < '2026-10-01'), 3000000);

-- ---------------------------------------------------------------------
-- 4. Soft delete (tombstone) changes the derived balance, restore reverses it
-- ---------------------------------------------------------------------
UPDATE transactions SET deleted_at = now() WHERE id = '00000000-0000-7000-8000-0000000000f1';
SELECT pg_temp.expect_eq('soft delete: Bank after deleting groceries',
       (SELECT balance_minor FROM v_account_balances WHERE name = 'Bank'), 7500000);
UPDATE transactions SET deleted_at = NULL WHERE id = '00000000-0000-7000-8000-0000000000f1';
SELECT pg_temp.expect_eq('soft delete: Bank after restore',
       (SELECT balance_minor FROM v_account_balances WHERE name = 'Bank'), 7455000);

-- ---------------------------------------------------------------------
-- 5. version + updated_at are bumped by the trigger.
--    (Two UPDATEs above already touched f1: delete = v2, restore = v3.)
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_eq('version: groceries after delete + restore',
       (SELECT version FROM transactions WHERE id = '00000000-0000-7000-8000-0000000000f1'), 3);
UPDATE transactions SET note = 'milk and eggs' WHERE id = '00000000-0000-7000-8000-0000000000f1';
SELECT pg_temp.expect_eq('version: after note edit',
       (SELECT version FROM transactions WHERE id = '00000000-0000-7000-8000-0000000000f1'), 4);

-- ---------------------------------------------------------------------
-- 6. Recurring: the same (rule, date) can only be generated once
-- ---------------------------------------------------------------------
INSERT INTO recurring_rules (id, workspace_id, account_id, kind, amount_minor, title, frequency, anchor_on) VALUES
  ('00000000-0000-7000-8000-0000000000aa', '00000000-0000-7000-8000-0000000000b2',
   '00000000-0000-7000-8000-0000000000c3', 'expense', 19900, 'Hosting', 'monthly', '2026-01-31');

INSERT INTO transactions (id, workspace_id, account_id, kind, amount_minor, title, occurred_on, source) VALUES
  ('00000000-0000-7000-8000-0000000000f7', '00000000-0000-7000-8000-0000000000b2',
   '00000000-0000-7000-8000-0000000000c3', 'expense', 19900, 'Hosting', '2026-09-30', 'recurring');

INSERT INTO recurring_occurrences (rule_id, occurrence_on, transaction_id) VALUES
  ('00000000-0000-7000-8000-0000000000aa', '2026-09-30', '00000000-0000-7000-8000-0000000000f7');

SELECT pg_temp.expect_err('recurring: duplicate occurrence rejected',
  $q$INSERT INTO recurring_occurrences (rule_id, occurrence_on, transaction_id)
     VALUES ('00000000-0000-7000-8000-0000000000aa', '2026-09-30', '00000000-0000-7000-8000-0000000000f1')$q$,
  '23505');   -- unique_violation

-- ---------------------------------------------------------------------
-- 7. Constraints that must refuse bad data
-- ---------------------------------------------------------------------
SELECT pg_temp.expect_err('reject: amount = 0',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c1','expense',0,'2026-09-30')$q$,
  '23514');   -- check_violation

SELECT pg_temp.expect_err('reject: negative amount',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c1','expense',-5,'2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: transfer leg without transfer_id',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c1','transfer_out',100,'2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: plain expense that carries a transfer_id',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, transfer_id, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c1','expense',100,
             '00000000-0000-7000-8000-0000000000e1','2026-09-30')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: account from ANOTHER workspace',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b1','00000000-0000-7000-8000-0000000000c3','expense',100,'2026-09-30')$q$,
  '23503');   -- foreign_key_violation

SELECT pg_temp.expect_err('reject: category from ANOTHER workspace',
  $q$INSERT INTO transactions (workspace_id, account_id, kind, amount_minor, category_id, occurred_on)
     VALUES ('00000000-0000-7000-8000-0000000000b2','00000000-0000-7000-8000-0000000000c3','expense',100,
             '00000000-0000-7000-8000-0000000000d1','2026-09-30')$q$,
  '23503');

SELECT pg_temp.expect_err('reject: duplicate account name (case-insensitive)',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code)
     VALUES ('00000000-0000-7000-8000-0000000000b1','bank','bank','INR')$q$,
  '23505');

SELECT pg_temp.expect_err('reject: duplicate email (different case)',
  $q$INSERT INTO users (email, display_name) VALUES ('TEST@example.test','Dup')$q$,
  '23505');

SELECT pg_temp.expect_err('reject: credit limit on a non-card account',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code, credit_limit_minor)
     VALUES ('00000000-0000-7000-8000-0000000000b1','Wallet X','wallet','INR',100000)$q$,
  '23514');

SELECT pg_temp.expect_err('reject: lower-case currency code',
  $q$INSERT INTO accounts (workspace_id, name, type, currency_code)
     VALUES ('00000000-0000-7000-8000-0000000000b1','Odd','bank','inr')$q$,
  '23514');

SELECT pg_temp.expect_err('reject: writing the generated column',
  $q$UPDATE transactions SET signed_amount_minor = 1 WHERE id = '00000000-0000-7000-8000-0000000000f1'$q$,
  '428C9');   -- generated_always: column can only be updated to DEFAULT

-- ---------------------------------------------------------------------
-- RESULT
-- ---------------------------------------------------------------------
SELECT seq,
       CASE WHEN ok THEN 'PASS' ELSE 'FAIL' END AS result,
       name,
       detail
FROM t_results
UNION ALL
SELECT 9999, CASE WHEN bool_and(ok) THEN 'ALL PASS' ELSE 'HAS FAILURES' END,
       COUNT(*) FILTER (WHERE ok) || ' of ' || COUNT(*) || ' checks passed', NULL
FROM t_results
ORDER BY seq;
