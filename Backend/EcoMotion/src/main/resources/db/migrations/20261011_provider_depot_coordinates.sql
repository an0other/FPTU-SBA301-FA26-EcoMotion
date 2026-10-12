ALTER TABLE provider
    ADD COLUMN IF NOT EXISTS depot_latitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS depot_longitude numeric(9,6);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_provider_depot_coordinates'
    ) THEN
        ALTER TABLE provider
            ADD CONSTRAINT ck_provider_depot_coordinates CHECK (
                (depot_latitude IS NULL AND depot_longitude IS NULL)
                OR (depot_latitude IS NOT NULL AND depot_longitude IS NOT NULL
                    AND depot_latitude BETWEEN -90 AND 90 AND depot_longitude BETWEEN -180 AND 180)
            );
    END IF;
END $$;

UPDATE provider
SET depot_latitude = 10.773300, depot_longitude = 106.703600
WHERE depot_address IN (
    '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',
    '15 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh'
)
AND depot_latitude IS NULL AND depot_longitude IS NULL;

UPDATE provider
SET depot_address = '45 Lê Lợi, Quận 1, TP. Hồ Chí Minh'
WHERE display_name = 'EcoMotion Depot B'
AND depot_address = '45 Lê Lợi, Quận 3, TP. Hồ Chí Minh';

UPDATE provider
SET depot_latitude = 10.774900, depot_longitude = 106.698500
WHERE depot_address = '45 Lê Lợi, Quận 1, TP. Hồ Chí Minh'
AND depot_latitude IS NULL AND depot_longitude IS NULL;
