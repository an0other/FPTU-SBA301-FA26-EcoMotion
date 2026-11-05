-- One-shot upgrade for an existing EcoMotion database.
-- Adds columns and relaxes checks. Does not delete payments or overwrite refund/charge rows.

BEGIN;

ALTER TABLE vehicle ADD COLUMN IF NOT EXISTS dispatch_latitude numeric(9,6);
ALTER TABLE vehicle ADD COLUMN IF NOT EXISTS dispatch_longitude numeric(9,6);

DO $$
BEGIN
    ALTER TABLE vehicle ADD CONSTRAINT ck_vehicle_dispatch_pair CHECK (
        (dispatch_latitude IS NULL AND dispatch_longitude IS NULL)
        OR (dispatch_latitude IS NOT NULL AND dispatch_longitude IS NOT NULL)
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE booking_policy_snapshot ADD COLUMN IF NOT EXISTS dispatch_origin_latitude numeric(9,6);
ALTER TABLE booking_policy_snapshot ADD COLUMN IF NOT EXISTS dispatch_origin_longitude numeric(9,6);
ALTER TABLE booking_policy_snapshot ADD COLUMN IF NOT EXISTS distance_km numeric(10,3);
ALTER TABLE booking_policy_snapshot ADD COLUMN IF NOT EXISTS distance_source varchar(20);
ALTER TABLE booking_policy_snapshot ADD COLUMN IF NOT EXISTS pickup_fee numeric(14,2);

ALTER TABLE booking_charge ALTER COLUMN charge_type TYPE varchar(30);
ALTER TABLE booking_charge ADD COLUMN IF NOT EXISTS confirmation_status varchar(20);
ALTER TABLE booking_charge ADD COLUMN IF NOT EXISTS client_request_id varchar(64);

UPDATE booking_charge
SET confirmation_status = 'ACCEPTED'
WHERE confirmation_status IS NULL
  AND charge_type IN ('BASE_RENTAL', 'DEPOSIT', 'OUT_OF_RADIUS');

UPDATE booking_charge c
SET confirmation_status = 'DISPUTED'
WHERE c.confirmation_status IS NULL
  AND EXISTS (
      SELECT 1 FROM dispute d
      WHERE d.charge_id = c.id AND d.status = 'OPEN'
  );

UPDATE booking_charge c
SET confirmation_status = 'PENDING'
FROM booking b
WHERE c.booking_id = b.id
  AND c.confirmation_status IS NULL
  AND b.status = 'AWAITING_EXTRA_CHARGE_CONFIRMATION'
  AND c.is_waived = false
  AND c.charge_type NOT IN ('BASE_RENTAL', 'DEPOSIT', 'OUT_OF_RADIUS');

UPDATE booking_charge
SET confirmation_status = 'ACCEPTED'
WHERE confirmation_status IS NULL;

ALTER TABLE booking_charge ALTER COLUMN confirmation_status SET DEFAULT 'ACCEPTED';
ALTER TABLE booking_charge ALTER COLUMN confirmation_status SET NOT NULL;

ALTER TABLE booking_charge DROP CONSTRAINT IF EXISTS ck_booking_charge_type;
ALTER TABLE booking_charge ADD CONSTRAINT ck_booking_charge_type CHECK (charge_type IN (
    'BASE_RENTAL', 'DEPOSIT', 'OVERTIME', 'WAITING', 'WEEKEND', 'OUT_OF_RADIUS',
    'TOLL', 'PARKING', 'OTHER'
));

ALTER TABLE booking_charge DROP CONSTRAINT IF EXISTS ck_booking_charge_confirmation;
ALTER TABLE booking_charge ADD CONSTRAINT ck_booking_charge_confirmation CHECK (
    confirmation_status IN ('PENDING', 'ACCEPTED', 'DISPUTED')
);

ALTER TABLE booking_charge DROP CONSTRAINT IF EXISTS ck_booking_charge_quantity;
ALTER TABLE booking_charge ADD CONSTRAINT ck_booking_charge_quantity CHECK (
    (charge_type IN ('DEPOSIT', 'WEEKEND') AND quantity IS NULL)
    OR (
        charge_type IN ('BASE_RENTAL', 'OVERTIME', 'WAITING', 'OUT_OF_RADIUS', 'TOLL', 'PARKING', 'OTHER')
        AND quantity IS NOT NULL
        AND quantity > 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_charge_client_request
    ON booking_charge (booking_id, client_request_id)
    WHERE client_request_id IS NOT NULL;

ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS source_event_id varchar(120);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS source_payment_transaction_id bigint;
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS vnp_request_id varchar(32);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS refund_transaction_no varchar(40);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS vnp_response_code varchar(10);
ALTER TABLE refund_request ADD COLUMN IF NOT EXISTS vnp_transaction_status varchar(10);

DO $$
BEGIN
    ALTER TABLE refund_request
        ADD CONSTRAINT fk_refund_request_source_payment
        FOREIGN KEY (source_payment_transaction_id) REFERENCES payment_transaction (id) ON DELETE SET NULL;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_request_source_event
    ON refund_request (source_event_id)
    WHERE source_event_id IS NOT NULL;

COMMIT;
