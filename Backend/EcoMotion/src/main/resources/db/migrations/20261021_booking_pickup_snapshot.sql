ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS pickup_latitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS pickup_longitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS driver_note varchar(160);

-- Repair rows written while an older, three-valued CHECK expression was active.
UPDATE booking
SET pickup_latitude = NULL,
    pickup_longitude = NULL
WHERE (pickup_latitude IS NULL) <> (pickup_longitude IS NULL);

ALTER TABLE booking
    DROP CONSTRAINT IF EXISTS ck_booking_pickup_coordinates;

ALTER TABLE booking
    ADD CONSTRAINT ck_booking_pickup_coordinates CHECK (
        (pickup_latitude IS NULL AND pickup_longitude IS NULL)
        OR (pickup_latitude IS NOT NULL AND pickup_longitude IS NOT NULL
            AND pickup_latitude BETWEEN -90 AND 90
            AND pickup_longitude BETWEEN -180 AND 180)
    );
