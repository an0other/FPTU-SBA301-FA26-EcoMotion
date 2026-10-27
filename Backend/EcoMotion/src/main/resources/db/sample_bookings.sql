-- Sample bookings for testing O07 (Provider Booking Queue).
-- Requires the dev seed (DevDataSeeder, profile "dev") to have run first:
--   users: provider.a@ecomotion.vn (vehicles 1-4), provider.b@ecomotion.vn (vehicles 5-6),
--          renter.demo@ecomotion.vn (renter profile); password for all: EcoMotion@123
-- Safe to re-run: existing booking_code rows are skipped.
-- Each vehicle must already have at least one rental_plan (seeded by DevDataSeeder).

INSERT INTO booking (
    booking_code, renter_id, vehicle_id, plan_id, status,
    pickup_address, pickup_time, passenger_count, total_amount,
    created_at, updated_at,
    cancelled_by, cancel_reason, cancelled_at
)
SELECT
    s.booking_code,
    (SELECT r.id FROM renter r JOIN users u ON u.id = r.user_id
      WHERE u.email = 'renter.demo@ecomotion.vn'),
    s.vehicle_id,
    (SELECT p.id FROM rental_plan p WHERE p.vehicle_id = s.vehicle_id ORDER BY p.id LIMIT 1),
    s.status,
    s.pickup_address,
    now() + s.pickup_offset,
    s.passenger_count,
    s.total_amount,
    now(),
    now(),
    CASE WHEN s.status = 'CANCELLED'
         THEN (SELECT id FROM users WHERE email = 'renter.demo@ecomotion.vn') END,
    CASE WHEN s.status = 'CANCELLED' THEN 'RENTER_REQUEST' END,
    CASE WHEN s.status = 'CANCELLED' THEN now() END
FROM (VALUES
    -- Provider A (vehicles 1-4)
    ('BK-9Q2Z11', 4, 'CONFIRMED',   'Sheraton Saigon, 88 Dong Khoi, Q.1',        interval '4 days',   2, 1750000.00),
    ('BK-1X8C44', 1, 'IN_PROGRESS', 'Tuyen TP.HCM - Ho Tram Resort',             interval '-3 hours', 5, 3400000.00),
    ('BK-6T9V32', 2, 'COMPLETED',   'Khu do thi Sala, TP. Thu Duc',              interval '-5 days',  2,  860000.00),
    ('BK-5L3M91', 4, 'CANCELLED',   'Thao Dien Pearl, TP. Thu Duc',              interval '-1 day',   1,  720000.00)
) AS s(booking_code, vehicle_id, status, pickup_address, pickup_offset, passenger_count, total_amount)
ON CONFLICT (booking_code) DO NOTHING;
