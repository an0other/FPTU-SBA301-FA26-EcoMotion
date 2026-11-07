BEGIN;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_type;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS payment_transaction_type_check;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_type CHECK (
    type IN ('DEPOSIT_PAY','SECURITY_DEPOSIT','SECURITY_DEPOSIT_REFUND','UPFRONT_PAY','SETTLEMENT','REFUND','PAYOUT')
);
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_verification_method;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS payment_transaction_verification_method_check;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_verification_method CHECK (
    verification_method IS NULL OR verification_method IN ('PROVIDER_CONFIRMED','RENTER_CONFIRMED','GATEWAY_VERIFIED','ADMIN_RECONCILED','MOCK_SIMULATED','MANUAL_UNVERIFIED')
);
COMMIT;
