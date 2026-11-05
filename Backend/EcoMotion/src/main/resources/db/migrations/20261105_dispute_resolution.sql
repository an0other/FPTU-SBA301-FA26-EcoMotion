ALTER TABLE dispute ADD COLUMN IF NOT EXISTS decision varchar(30);
ALTER TABLE dispute ADD COLUMN IF NOT EXISTS refund_award_total numeric(14,2);
ALTER TABLE dispute ADD COLUMN IF NOT EXISTS resolved_at timestamptz;
CREATE TABLE IF NOT EXISTS dispute_refund_link (
    dispute_id bigint NOT NULL REFERENCES dispute(id),
    refund_request_id bigint NOT NULL REFERENCES refund_request(id),
    PRIMARY KEY (dispute_id, refund_request_id)
);
