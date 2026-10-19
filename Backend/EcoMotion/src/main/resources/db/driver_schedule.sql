-- D01 Driver Schedule indexes
-- Optimizes queries filtering by driver_id and pickup_time or driver_id, status and pickup_time.

CREATE INDEX IF NOT EXISTS idx_booking_driver_pickup
    ON booking (driver_id, pickup_time)
    WHERE driver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_booking_driver_status_pickup
    ON booking (driver_id, status, pickup_time)
    WHERE driver_id IS NOT NULL;
