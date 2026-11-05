ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS recipient_bank_name varchar(150);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS recipient_account_holder varchar(150);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS recipient_account_number varchar(255);
ALTER TABLE payment_transaction ADD COLUMN IF NOT EXISTS manual_confirmation_id varchar(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_manual_confirmation ON payment_transaction(manual_confirmation_id);
