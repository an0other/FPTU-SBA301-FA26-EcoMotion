BEGIN;

ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS payment_transaction_verification_method_check;
ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_verification_method;
ALTER TABLE payment_transaction ADD CONSTRAINT ck_payment_verification_method CHECK (
    verification_method IS NULL OR verification_method IN (
        'PROVIDER_CONFIRMED', 'GATEWAY_VERIFIED', 'ADMIN_RECONCILED', 'MOCK_SIMULATED', 'MANUAL_UNVERIFIED'
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_vnp_txn_ref ON payment_transaction (vnp_txn_ref);

COMMIT;
