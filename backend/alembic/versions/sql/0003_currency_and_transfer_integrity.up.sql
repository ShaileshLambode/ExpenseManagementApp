-- =====================================================================
-- 0003  Currency catalogue, decimals per currency, transfer integrity
--       (schema v0.1 -> v0.2)
--
-- Closes two gaps found in the Day 1/Day 2 audits:
--   I3  A cross-currency transfer was accepted with fx_rate NULL and legs
--       that did not match (1,000 INR became 1,000,000 USD).
--   I10 The NUMERIC(20,8) column stored 450.123456789 rupees.
--
-- Error contract: SQLSTATE 23514 (check_violation) with a CONSTRAINT name:
--   amount_scale_per_currency · account_currency_locked · transfer_consistent
-- =====================================================================

-- 1. Currency catalogue -------------------------------------------------
-- `exponent` = how many decimals the currency allows (INR 2, JPY 0, KWD 3).
-- Keep in step with CURRENCY_EXPONENT in app/core/money.py (a test compares them).
CREATE TABLE currencies (
  code      char(3)  PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
  exponent  smallint NOT NULL CHECK (exponent BETWEEN 0 AND 4),
  name      text     NOT NULL
);
INSERT INTO currencies (code, exponent, name) VALUES
  ('INR', 2, 'Indian rupee'),
  ('USD', 2, 'US dollar'),
  ('EUR', 2, 'Euro'),
  ('GBP', 2, 'Pound sterling'),
  ('AED', 2, 'UAE dirham'),
  ('SGD', 2, 'Singapore dollar'),
  ('JPY', 0, 'Japanese yen'),
  ('KWD', 3, 'Kuwaiti dinar'),
  ('BHD', 3, 'Bahraini dinar');

ALTER TABLE workspaces ADD CONSTRAINT workspaces_currency_fk
  FOREIGN KEY (currency_code) REFERENCES currencies (code);
ALTER TABLE accounts ADD CONSTRAINT accounts_currency_fk
  FOREIGN KEY (currency_code) REFERENCES currencies (code);

-- 2. Decimals per currency ----------------------------------------------
CREATE FUNCTION assert_scale(p_value numeric, p_currency char(3), p_what text) RETURNS void
LANGUAGE plpgsql STABLE AS $$
DECLARE v_exp smallint;
BEGIN
  IF p_value IS NULL THEN
    RETURN;
  END IF;
  SELECT exponent INTO v_exp FROM currencies WHERE code = p_currency;
  IF v_exp IS NULL THEN
    RETURN;   -- unknown currency: the foreign key reports it
  END IF;
  IF scale(trim_scale(p_value)) > v_exp THEN
    RAISE EXCEPTION '% % has more than % decimal place(s), the maximum for %',
      p_what, p_value, v_exp, p_currency
      USING ERRCODE = '23514', CONSTRAINT = 'amount_scale_per_currency';
  END IF;
END $$;

CREATE FUNCTION check_transaction_scale() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM assert_scale(NEW.amount,
                       (SELECT currency_code FROM accounts WHERE id = NEW.account_id),
                       'amount');
  RETURN NEW;
END $$;

CREATE TRIGGER transactions_scale_check
  BEFORE INSERT OR UPDATE OF amount, account_id ON transactions
  FOR EACH ROW EXECUTE FUNCTION check_transaction_scale();

CREATE FUNCTION check_account_scale() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM assert_scale(NEW.opening_balance, NEW.currency_code, 'opening_balance');
  PERFORM assert_scale(NEW.credit_limit,    NEW.currency_code, 'credit_limit');
  -- An account's currency cannot change once it has any transaction (even deleted ones):
  -- every amount and every transfer leg was validated against the old currency.
  IF TG_OP = 'UPDATE' AND OLD.currency_code IS DISTINCT FROM NEW.currency_code
     AND EXISTS (SELECT 1 FROM transactions WHERE account_id = NEW.id) THEN
    RAISE EXCEPTION 'account % has transactions, so its currency cannot change', NEW.id
      USING ERRCODE = '23514', CONSTRAINT = 'account_currency_locked';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER accounts_scale_check
  BEFORE INSERT OR UPDATE OF opening_balance, credit_limit, currency_code ON accounts
  FOR EACH ROW EXECUTE FUNCTION check_account_scale();

-- 3. Transfer integrity -------------------------------------------------
-- A transfer is a `transfers` row plus exactly two LIVE legs in `transactions`:
-- one transfer_out and one transfer_in, on two different accounts.
--   same currency  -> fx_rate must be NULL and both amounts equal
--   cross currency -> fx_rate required (units of destination per unit of source)
--                     and |amount_in - amount_out * fx_rate| <= half a unit of the
--                     destination currency + the rate's own quantisation error
--                     (fx_rate has 10 decimals, so out * 5e-11)
-- A soft-deleted transfer must have no live legs.
-- Checked at COMMIT (deferred constraint triggers), so the API can insert the
-- transfer and its legs in any order inside ONE transaction.
CREATE FUNCTION assert_transfer_consistent(p_transfer_id uuid) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  t       RECORD;
  n_live  integer;
  n_out   integer;
  n_in    integer;
  o       RECORD;
  i       RECORD;
  v_exp   smallint;
  v_tol   numeric;
BEGIN
  SELECT id, fx_rate, deleted_at INTO t FROM transfers WHERE id = p_transfer_id;
  IF NOT FOUND THEN
    RETURN;   -- hard-deleted by the purge job
  END IF;

  SELECT count(*),
         count(*) FILTER (WHERE kind = 'transfer_out'),
         count(*) FILTER (WHERE kind = 'transfer_in')
    INTO n_live, n_out, n_in
    FROM transactions
   WHERE transfer_id = p_transfer_id AND deleted_at IS NULL;

  IF t.deleted_at IS NOT NULL THEN
    IF n_live <> 0 THEN
      RAISE EXCEPTION 'transfer % is deleted but still has % live leg(s)', p_transfer_id, n_live
        USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
    END IF;
    RETURN;
  END IF;

  IF n_live <> 2 OR n_out <> 1 OR n_in <> 1 THEN
    RAISE EXCEPTION 'transfer % needs exactly one transfer_out and one transfer_in leg (found % out, % in)',
      p_transfer_id, n_out, n_in
      USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
  END IF;

  SELECT x.account_id, x.amount, a.currency_code AS ccy INTO o
    FROM transactions x JOIN accounts a ON a.id = x.account_id
   WHERE x.transfer_id = p_transfer_id AND x.deleted_at IS NULL AND x.kind = 'transfer_out';
  SELECT x.account_id, x.amount, a.currency_code AS ccy INTO i
    FROM transactions x JOIN accounts a ON a.id = x.account_id
   WHERE x.transfer_id = p_transfer_id AND x.deleted_at IS NULL AND x.kind = 'transfer_in';

  IF o.account_id = i.account_id THEN
    RAISE EXCEPTION 'transfer % moves money to the same account', p_transfer_id
      USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
  END IF;

  IF o.ccy = i.ccy THEN
    IF t.fx_rate IS NOT NULL THEN
      RAISE EXCEPTION 'transfer %: same-currency transfers must not have an fx_rate', p_transfer_id
        USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
    END IF;
    IF o.amount <> i.amount THEN
      RAISE EXCEPTION 'transfer %: same-currency legs differ (out %, in %)', p_transfer_id, o.amount, i.amount
        USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
    END IF;
  ELSE
    IF t.fx_rate IS NULL THEN
      RAISE EXCEPTION 'transfer %: fx_rate is required when the accounts use different currencies (% -> %)',
        p_transfer_id, o.ccy, i.ccy
        USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
    END IF;
    SELECT exponent INTO v_exp FROM currencies WHERE code = i.ccy;
    v_tol := 0.5 * power(10::numeric, -COALESCE(v_exp, 2)) + o.amount * 0.00000000005;
    IF abs(i.amount - o.amount * t.fx_rate) > v_tol THEN
      RAISE EXCEPTION 'transfer %: received % % does not match % % x rate % (allowed difference %)',
        p_transfer_id, i.amount, i.ccy, o.amount, o.ccy, t.fx_rate, v_tol
        USING ERRCODE = '23514', CONSTRAINT = 'transfer_consistent';
    END IF;
  END IF;
END $$;

CREATE FUNCTION check_transfer_leg_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.transfer_id IS NOT NULL THEN
    PERFORM assert_transfer_consistent(NEW.transfer_id);
  END IF;
  IF TG_OP = 'DELETE' AND OLD.transfer_id IS NOT NULL THEN
    PERFORM assert_transfer_consistent(OLD.transfer_id);
  END IF;
  IF TG_OP = 'UPDATE' AND OLD.transfer_id IS NOT NULL
     AND OLD.transfer_id IS DISTINCT FROM NEW.transfer_id THEN
    PERFORM assert_transfer_consistent(OLD.transfer_id);
  END IF;
  RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER transactions_transfer_check
  AFTER INSERT OR UPDATE OR DELETE ON transactions
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION check_transfer_leg_change();

CREATE FUNCTION check_transfer_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM assert_transfer_consistent(NEW.id);
  RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER transfers_consistency_check
  AFTER INSERT OR UPDATE OF fx_rate, deleted_at ON transfers
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION check_transfer_change();
