BEGIN;
ALTER TABLE payment_transaction ALTER COLUMN type TYPE varchar(32);
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_type;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS payment_transaction_type_check;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_type CHECK (
    type IN ('DEPOSIT_PAY','SECURITY_DEPOSIT','SECURITY_DEPOSIT_REFUND','UPFRONT_PAY','SETTLEMENT','REFUND','PAYOUT')
);
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_direction_by_type;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_direction_by_type CHECK (
    (type IN ('DEPOSIT_PAY','SECURITY_DEPOSIT','UPFRONT_PAY','SETTLEMENT') AND direction = 'IN')
    OR (type IN ('REFUND','SECURITY_DEPOSIT_REFUND','PAYOUT') AND direction = 'OUT')
);
COMMIT;
