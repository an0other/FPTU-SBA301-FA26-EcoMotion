-- Remove the VNPAY payment method and its gateway columns.
-- Forward-only. Does not edit 20261107_remove_vnpay.sql or 20261108_vnpay_gateway.sql.
--
-- Safety rule: the migration REFUSES to run while any payment_transaction row has method = 'VNPAY'.
-- Those rows are financial records of online collections. They must be archived or reconciled by a person
-- first; they are not relabelled, because that would misstate how the money was collected.
-- Take a backup before running on any database that holds real money data.

BEGIN;

DO $$
DECLARE
    vnpay_rows bigint;
BEGIN
    SELECT COUNT(*) INTO vnpay_rows FROM payment_transaction WHERE method = 'VNPAY';
    IF vnpay_rows > 0 THEN
        RAISE EXCEPTION 'Refusing to remove VNPAY: % payment_transaction rows use method VNPAY. Archive and reconcile them first.',
            vnpay_rows;
    END IF;
END $$;

ALTER TABLE payment_transaction DROP CONSTRAINT IF EXISTS ck_payment_method;
ALTER TABLE payment_transaction
    ADD CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'E_WALLET', 'CARD'));

DROP INDEX IF EXISTS uk_payment_vnp_transaction_no;

ALTER TABLE payment_transaction
    DROP COLUMN IF EXISTS vnp_transaction_no,
    DROP COLUMN IF EXISTS vnp_response_code,
    DROP COLUMN IF EXISTS vnp_transaction_status,
    DROP COLUMN IF EXISTS vnp_bank_code,
    DROP COLUMN IF EXISTS vnp_pay_date,
    DROP COLUMN IF EXISTS vnp_ipn_received_at;

COMMIT;
