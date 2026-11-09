BEGIN;
ALTER TABLE payment_request ADD COLUMN IF NOT EXISTS security_deposit boolean NOT NULL DEFAULT false;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_type;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS payment_transaction_type_check;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_type
    CHECK (type IN ('DEPOSIT_PAY','SECURITY_DEPOSIT','SECURITY_DEPOSIT_REFUND','UPFRONT_PAY','SETTLEMENT','REFUND','PAYOUT'));
COMMIT;
