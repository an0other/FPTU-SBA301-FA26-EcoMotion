CREATE TABLE IF NOT EXISTS yearly_handover (
    booking_id bigint PRIMARY KEY REFERENCES booking(id),
    version bigint NOT NULL DEFAULT 0,
    stage varchar(30) NOT NULL,
    dispatched_at timestamptz,
    arrived_at timestamptz,
    received_on date,
    initial_km bigint CHECK (initial_km >= 0),
    evidence_id bigint REFERENCES trip_evidence(id),
    evidence_url varchar(2000)
);
