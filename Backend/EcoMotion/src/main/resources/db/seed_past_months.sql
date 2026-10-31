BEGIN;

-- Helper temporary table or inserts
INSERT INTO booking (
    booking_code, renter_id, vehicle_id, plan_id, driver_id, status,
    pickup_address, pickup_time, actual_start_at, actual_end_at, passenger_count,
    total_amount, created_at, updated_at, version, driver_replacement_required,
    pickup_latitude, pickup_longitude, driver_note, driver_confirmed_at
) VALUES
-- THANG 08/2026
('BK-AUG-01', 3, 1, 2, 2, 'COMPLETED', 'San bay Tan Son Nhat - Ga Quoc noi', '2026-08-05 08:00:00+07', '2026-08-05 08:05:00+07', '2026-08-06 08:00:00+07', 4, 1100000.00, '2026-08-04 10:00:00+07', '2026-08-06 08:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-05 07:30:00+07'),
('BK-AUG-02', 4, 2, 5, 1, 'COMPLETED', 'Vincom Center Landmark 81', '2026-08-08 09:30:00+07', '2026-08-08 09:35:00+07', '2026-08-08 13:30:00+07', 1, 600000.00, '2026-08-07 14:00:00+07', '2026-08-08 13:35:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-08 09:00:00+07'),
('BK-AUG-03', 5, 3, 10, 3, 'COMPLETED', 'Mui Ne, Phan Thiet', '2026-08-12 06:00:00+07', '2026-08-12 06:05:00+07', '2026-08-14 06:00:00+07', 2, 3200000.00, '2026-08-11 11:00:00+07', '2026-08-14 06:10:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-12 05:30:00+07'),
('BK-AUG-04', 6, 4, 14, 1, 'COMPLETED', 'InterContinental Saigon, Q.1', '2026-08-16 14:00:00+07', '2026-08-16 14:05:00+07', '2026-08-17 14:00:00+07', 2, 1400000.00, '2026-08-15 09:00:00+07', '2026-08-17 14:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-16 13:30:00+07'),
('BK-AUG-05', 2, 1, 2, 2, 'COMPLETED', 'Aeon Mall Tan Phu', '2026-08-20 10:00:00+07', '2026-08-20 10:05:00+07', '2026-08-21 10:00:00+07', 3, 1100000.00, '2026-08-19 16:00:00+07', '2026-08-21 10:10:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-20 09:30:00+07'),
('BK-AUG-06', 7, 2, 5, 3, 'COMPLETED', 'Gigamall Thu Duc', '2026-08-23 13:00:00+07', '2026-08-23 13:05:00+07', '2026-08-23 16:00:00+07', 2, 450000.00, '2026-08-22 18:00:00+07', '2026-08-23 16:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-23 12:30:00+07'),
('BK-AUG-07', 8, 4, 14, 1, 'COMPLETED', 'Khu du lich Dai Nam', '2026-08-27 08:30:00+07', '2026-08-27 08:35:00+07', '2026-08-28 08:30:00+07', 4, 1400000.00, '2026-08-26 12:00:00+07', '2026-08-28 08:35:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-08-27 08:00:00+07'),

-- THANG 09/2026
('BK-SEP-01', 3, 1, 1, 2, 'COMPLETED', 'Takashimaya Saigon, Q.1', '2026-09-03 15:00:00+07', '2026-09-03 15:05:00+07', '2026-09-03 19:00:00+07', 2, 720000.00, '2026-09-02 11:00:00+07', '2026-09-03 19:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-03 14:30:00+07'),
('BK-SEP-02', 4, 2, 5, 1, 'COMPLETED', 'Saigon Centre, 65 Le Loi, Q.1', '2026-09-06 08:00:00+07', '2026-09-06 08:05:00+07', '2026-09-06 12:00:00+07', 1, 600000.00, '2026-09-05 15:00:00+07', '2026-09-06 12:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-06 07:30:00+07'),
('BK-SEP-03', 5, 3, 10, 3, 'COMPLETED', 'Vung Tau - Bai Sau', '2026-09-10 07:00:00+07', '2026-09-10 07:05:00+07', '2026-09-12 07:00:00+07', 2, 3200000.00, '2026-09-09 10:00:00+07', '2026-09-12 07:10:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-10 06:30:00+07'),
('BK-SEP-04', 6, 4, 14, 1, 'COMPLETED', 'Sheraton Saigon, 88 Dong Khoi, Q.1', '2026-09-14 09:00:00+07', '2026-09-14 09:05:00+07', '2026-09-15 09:00:00+07', 2, 1400000.00, '2026-09-13 14:00:00+07', '2026-09-15 09:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-14 08:30:00+07'),
('BK-SEP-05', 7, 1, 2, 2, 'COMPLETED', 'San bay Tan Son Nhat - Ga Quoc te', '2026-09-18 06:30:00+07', '2026-09-18 06:35:00+07', '2026-09-20 06:30:00+07', 4, 2200000.00, '2026-09-17 12:00:00+07', '2026-09-20 06:35:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-18 06:00:00+07'),
('BK-SEP-06', 8, 3, 10, 3, 'COMPLETED', 'Cao toc Long Thanh - Dau Giay', '2026-09-21 10:00:00+07', '2026-09-21 10:05:00+07', '2026-09-22 10:00:00+07', 2, 1600000.00, '2026-09-20 09:00:00+07', '2026-09-22 10:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-21 09:30:00+07'),
('BK-SEP-07', 9, 4, 14, 1, 'COMPLETED', 'Diamond Island, TP. Thu Duc', '2026-09-25 14:00:00+07', '2026-09-25 14:05:00+07', '2026-09-26 14:00:00+07', 4, 1400000.00, '2026-09-24 16:00:00+07', '2026-09-26 14:05:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-25 13:30:00+07'),
('BK-SEP-08', 2, 3, 10, 2, 'COMPLETED', 'Ho Tram Resort, Xuyen Moc', '2026-09-28 07:30:00+07', '2026-09-28 07:35:00+07', '2026-09-30 07:30:00+07', 3, 3200000.00, '2026-09-27 10:00:00+07', '2026-09-30 07:35:00+07', 0, false, 10.773300, 106.703600, 'Vui long lien he truoc khi den', '2026-09-28 07:00:00+07')
ON CONFLICT (booking_code) DO NOTHING;

-- Insert corresponding booking_charge
INSERT INTO booking_charge (booking_id, charge_type, description, quantity, unit_price, amount, is_waived, created_by, created_at)
SELECT b.id, 'BASE_RENTAL', 'Tien thue chuyen di (' || b.booking_code || ')', 1, b.total_amount, b.total_amount, false, r.user_id, b.created_at
FROM booking b
JOIN renter r ON b.renter_id = r.id
WHERE b.booking_code LIKE 'BK-AUG-%' OR b.booking_code LIKE 'BK-SEP-%'
ON CONFLICT DO NOTHING;

-- Insert corresponding booking_policy_snapshot
INSERT INTO booking_policy_snapshot (booking_id, plan_type, slot_length_minutes, slot_price, deposit_ratio, created_at)
SELECT b.id, p.plan_type, p.slot_length_minutes, p.slot_price, 0.3000, b.created_at
FROM booking b
JOIN rental_plan p ON b.plan_id = p.id
WHERE (b.booking_code LIKE 'BK-AUG-%' OR b.booking_code LIKE 'BK-SEP-%')
AND NOT EXISTS (SELECT 1 FROM booking_policy_snapshot s WHERE s.booking_id = b.id);

COMMIT;
