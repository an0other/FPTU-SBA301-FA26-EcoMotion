BEGIN;

ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_id VARCHAR(36);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_resolution VARCHAR(30);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_vehicle_id BIGINT REFERENCES vehicle(id);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_pickup_time TIMESTAMPTZ;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_reason VARCHAR(500);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_created_by BIGINT REFERENCES users(id);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_created_at TIMESTAMPTZ;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_accepted BOOLEAN;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_responded_by BIGINT REFERENCES users(id);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_responded_at TIMESTAMPTZ;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS proposal_response_reason VARCHAR(500);

CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_operational_proposal ON booking(proposal_id) WHERE proposal_id IS NOT NULL;

COMMIT;
