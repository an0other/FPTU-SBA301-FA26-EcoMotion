BEGIN;
ALTER TABLE payment_request ADD COLUMN IF NOT EXISTS installment_number integer NOT NULL DEFAULT 0;
ALTER TABLE payment_request ADD COLUMN IF NOT EXISTS period_start date;
ALTER TABLE payment_request ADD COLUMN IF NOT EXISTS period_end date;
ALTER TABLE payment_request DROP CONSTRAINT IF EXISTS uk_payment_request_booking_type;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_request_booking_type_period
    ON payment_request (booking_id, payment_type, installment_number);
ALTER TABLE payment_request DROP CONSTRAINT IF EXISTS ck_yearly_payment_period;
ALTER TABLE payment_request ADD CONSTRAINT ck_yearly_payment_period CHECK (
    (installment_number = 0 AND period_start IS NULL AND period_end IS NULL)
    OR (payment_type = 'MONTHLY_INSTALLMENT' AND installment_number BETWEEN 1 AND 12
        AND period_start IS NOT NULL AND period_end IS NOT NULL AND period_end > period_start)
);
COMMIT;
