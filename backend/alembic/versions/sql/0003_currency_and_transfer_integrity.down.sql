DROP TRIGGER transfers_consistency_check ON transfers;
DROP FUNCTION check_transfer_change();
DROP TRIGGER transactions_transfer_check ON transactions;
DROP FUNCTION check_transfer_leg_change();
DROP FUNCTION assert_transfer_consistent(uuid);

DROP TRIGGER accounts_scale_check ON accounts;
DROP FUNCTION check_account_scale();
DROP TRIGGER transactions_scale_check ON transactions;
DROP FUNCTION check_transaction_scale();
DROP FUNCTION assert_scale(numeric, char, text);

ALTER TABLE accounts   DROP CONSTRAINT accounts_currency_fk;
ALTER TABLE workspaces DROP CONSTRAINT workspaces_currency_fk;
DROP TABLE currencies;
