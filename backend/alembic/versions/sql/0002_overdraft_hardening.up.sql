-- =====================================================================
-- 0002  Overdraft guard hardening  (schema v0 -> v0.1)
--
-- Closes the gaps found when auditing the v0 trigger (see Day 2 guide):
--   1. Soft-deleting / hard-deleting an income that funds later spending
--      was allowed (the old trigger returned early on deleted_at).
--   2. Moving a transaction to another account was only checked on the
--      NEW account; the OLD account could silently go negative.
--   3. Lowering opening_balance / credit_limit / changing type after the
--      fact was never checked.
--   4. Two concurrent expenses could both pass (each trigger could not
--      see the other's uncommitted row)  ->  per-account row lock.
--   5. Bank/cash accounts could be created with a negative opening
--      balance, and credit cards without a limit.
-- The violation is raised as SQLSTATE P0001 with constraint name
-- 'account_no_overdraft' so the API can map it to HTTP 422 reliably.
-- =====================================================================

-- 1. Account-level rules ------------------------------------------------
ALTER TABLE accounts
  ADD CONSTRAINT accounts_opening_nonneg
    CHECK (type = 'credit_card' OR opening_balance >= 0),
  ADD CONSTRAINT accounts_card_needs_limit
    CHECK (type <> 'credit_card' OR credit_limit IS NOT NULL),
  ADD CONSTRAINT accounts_card_opening_within_limit
    CHECK (type <> 'credit_card' OR opening_balance >= -credit_limit);

-- 2. The guard ----------------------------------------------------------
-- Locks the account row, then verifies balance >= floor.
--   floor = 0 for every account type except credit_card (= -credit_limit).
-- FOR NO KEY UPDATE serialises writers on the same account but does NOT
-- conflict with the FOR KEY SHARE lock that foreign keys take, so inserting
-- transactions concurrently cannot deadlock on this lock.
-- The balance is read in a SEPARATE statement so that, under READ COMMITTED,
-- it gets a fresh snapshot after the lock wait and sees the other writer's commit.
CREATE FUNCTION assert_account_within_limit(p_account_id uuid) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  v_balance NUMERIC(20,8);
  v_limit   NUMERIC(20,8);
  v_type    text;
  v_floor   NUMERIC(20,8);
BEGIN
  PERFORM 1 FROM accounts
   WHERE id = p_account_id AND deleted_at IS NULL
   FOR NO KEY UPDATE;
  IF NOT FOUND THEN
    RETURN;   -- account is deleted/absent: the API must reject writes to it
  END IF;

  SELECT vab.balance, a.credit_limit, a.type
    INTO v_balance, v_limit, v_type
    FROM v_account_balances vab
    JOIN accounts a ON a.id = vab.account_id
   WHERE vab.account_id = p_account_id;

  v_floor := CASE WHEN v_type = 'credit_card' THEN -COALESCE(v_limit, 0) ELSE 0 END;

  IF v_balance < v_floor THEN
    RAISE EXCEPTION 'Overdraft not allowed: account % balance would be % (minimum %)',
      p_account_id, v_balance, v_floor
      USING ERRCODE = 'P0001', CONSTRAINT = 'account_no_overdraft';
  END IF;
END $$;

-- 3. Trigger on the ledger: INSERT, UPDATE and DELETE -------------------
CREATE OR REPLACE FUNCTION check_no_overdraft() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    PERFORM assert_account_within_limit(NEW.account_id);

  ELSIF TG_OP = 'DELETE' THEN
    PERFORM assert_account_within_limit(OLD.account_id);

  ELSE  -- UPDATE
    -- Only re-check when something that moves a balance changed.
    IF (OLD.amount, OLD.kind, OLD.account_id, OLD.deleted_at)
         IS NOT DISTINCT FROM (NEW.amount, NEW.kind, NEW.account_id, NEW.deleted_at) THEN
      RETURN NULL;
    END IF;
    IF OLD.account_id IS DISTINCT FROM NEW.account_id THEN
      -- Lock both accounts in a fixed (id) order so two opposite moves cannot deadlock.
      PERFORM assert_account_within_limit(LEAST(OLD.account_id, NEW.account_id));
      PERFORM assert_account_within_limit(GREATEST(OLD.account_id, NEW.account_id));
    ELSE
      PERFORM assert_account_within_limit(NEW.account_id);
    END IF;
  END IF;
  RETURN NULL;  -- AFTER trigger: return value is ignored
END $$;

DROP TRIGGER transactions_overdraft_check ON transactions;
CREATE TRIGGER transactions_overdraft_check
  AFTER INSERT OR UPDATE OR DELETE ON transactions
  FOR EACH ROW EXECUTE FUNCTION check_no_overdraft();

-- 4. Trigger on accounts: changing the floor or the opening balance ----
CREATE FUNCTION check_account_limits() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM assert_account_within_limit(NEW.id);
  RETURN NULL;
END $$;

CREATE TRIGGER accounts_overdraft_check
  AFTER UPDATE OF opening_balance, credit_limit, type ON accounts
  FOR EACH ROW EXECUTE FUNCTION check_account_limits();
