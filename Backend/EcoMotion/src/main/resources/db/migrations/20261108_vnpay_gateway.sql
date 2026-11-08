-- Re-introduce VNPAY as an online payment method for DEPOSIT and FINAL_PAYMENT collections.
-- Forward-only: does not edit 20261029_vnpay_verification.sql or 20261107_remove_vnpay.sql.
--
-- Refunds (VNPAY refund API) are handled in a later migration.
-- Take a backup before running on any database that holds real money data.

BEGIN;

ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_method;
ALTER TABLE payment_transaction
    ADD CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'E_WALLET', 'CARD', 'VNPAY'));

-- VNPAY gateway fields. vnp_txn_ref is stored in transaction_code (unique), so it is not duplicated here.
ALTER TABLE payment_transaction
    ADD COLUMN IF NOT EXISTS vnp_transaction_no  VARCHAR(40),
    ADD COLUMN IF NOT EXISTS vnp_response_code   VARCHAR(4),
    ADD COLUMN IF NOT EXISTS vnp_transaction_status VARCHAR(4),
    ADD COLUMN IF NOT EXISTS vnp_bank_code       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS vnp_pay_date        VARCHAR(14),
    ADD COLUMN IF NOT EXISTS vnp_ipn_received_at TIMESTAMPTZ;

-- A VNPAY transaction number identifies exactly one settled payment, so it must not be applied twice.
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_vnp_transaction_no
    ON payment_transaction (vnp_transaction_no)
    WHERE vnp_transaction_no IS NOT NULL;

COMMIT;
