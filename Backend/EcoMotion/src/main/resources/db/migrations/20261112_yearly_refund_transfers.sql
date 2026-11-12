CREATE TABLE IF NOT EXISTS yearly_refund_transfer (
    booking_id BIGINT NOT NULL REFERENCES yearly_workflow(booking_id),
    transfer_index INTEGER NOT NULL,
    transfer_key VARCHAR(80) NOT NULL,
    bank_reference VARCHAR(80) NOT NULL,
    submitted_amount NUMERIC(14,2) NOT NULL,
    verified_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    verification_status VARCHAR(30) NOT NULL,
    evidence_id BIGINT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by BIGINT,
    PRIMARY KEY (booking_id, transfer_index),
    CONSTRAINT uk_yearly_refund_bank_ref UNIQUE (booking_id, bank_reference)
);
