-- Restores the v0 behaviour exactly (as in 0001_schema_v0.sql).
DROP TRIGGER accounts_overdraft_check ON accounts;
DROP FUNCTION check_account_limits();

DROP TRIGGER transactions_overdraft_check ON transactions;

CREATE OR REPLACE FUNCTION check_no_overdraft() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_balance      NUMERIC(20,8);
  v_credit_limit NUMERIC(20,8);
  v_type         text;
BEGIN
  IF NEW.deleted_at IS NOT NULL THEN
    RETURN NEW;
  END IF;
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

CREATE TRIGGER transactions_overdraft_check
  AFTER INSERT OR UPDATE ON transactions
  FOR EACH ROW EXECUTE FUNCTION check_no_overdraft();

DROP FUNCTION assert_account_within_limit(uuid);

ALTER TABLE accounts
  DROP CONSTRAINT accounts_card_opening_within_limit,
  DROP CONSTRAINT accounts_card_needs_limit,
  DROP CONSTRAINT accounts_opening_nonneg;
