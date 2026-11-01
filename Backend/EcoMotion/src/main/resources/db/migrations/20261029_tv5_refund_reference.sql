BEGIN;

ALTER TABLE payment_transaction ADD COLUMN IF NOT EXISTS payout_gross_amount NUMERIC(14, 2);
ALTER TABLE payment_transaction ADD COLUMN IF NOT EXISTS payout_fee_amount NUMERIC(14, 2);

CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_paid_bank_reference
    ON payment_transaction (UPPER(TRIM(gateway_ref)))
    WHERE type = 'REFUND' AND direction = 'OUT' AND status = 'PAID' AND gateway_ref IS NOT NULL;

COMMIT;
