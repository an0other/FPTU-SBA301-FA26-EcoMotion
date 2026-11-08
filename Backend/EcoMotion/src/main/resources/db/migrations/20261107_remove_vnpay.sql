-- Remove the VNPAY payment gateway integration.
-- Online payments are replaced by manual bank transfer (BANK_TRANSFER + payment proof + provider/admin confirmation).
--
-- NOTE: historical rows with method = 'VNPAY' are re-labelled as BANK_TRANSFER so the enum/check constraint
-- can be tightened. Gateway-specific identifiers (vnp_txn_ref, vnp_transaction_no, ...) are dropped.
-- Take a backup before running on any database that holds real money data.

BEGIN;

UPDATE payment_transaction
SET method = 'BANK_TRANSFER'
WHERE method = 'VNPAY';

ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_method;
ALTER TABLE payment_transaction
    ADD CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'E_WALLET', 'CARD'));

ALTER TABLE payment_transaction
    DROP COLUMN IF EXISTS vnp_txn_ref,
    DROP COLUMN IF EXISTS vnp_transaction_no,
    DROP COLUMN IF EXISTS vnp_bank_code,
    DROP COLUMN IF EXISTS vnp_card_type,
    DROP COLUMN IF EXISTS vnp_pay_date,
    DROP COLUMN IF EXISTS vnp_response_code;

ALTER TABLE refund_request
    DROP COLUMN IF EXISTS vnp_request_id,
    DROP COLUMN IF EXISTS vnp_response_code,
    DROP COLUMN IF EXISTS vnp_transaction_status;

COMMIT;
