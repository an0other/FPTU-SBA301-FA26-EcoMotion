ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_status varchar(255);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_waiting_at timestamptz;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_deadline timestamptz;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_confirmed_at timestamptz;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_confirmed_by bigint;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_reported_at timestamptz;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS receipt_auto_confirmed_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_refund_receipt_deadline ON refund_request(receipt_status, receipt_deadline);
-- Existing proven manual refunds start a new response window at rollout, never expire retroactively.
UPDATE refund_request r SET receipt_status='WAITING', receipt_waiting_at=CURRENT_TIMESTAMP,
 receipt_deadline=CURRENT_TIMESTAMP + INTERVAL '72 hours'
FROM payment_transaction p WHERE r.payment_transaction_id=p.id AND r.status='REFUNDED'
 AND p.method='BANK_TRANSFER' AND p.status='PAID' AND p.receipt_url IS NOT NULL
 AND r.receipt_status IS NULL;
