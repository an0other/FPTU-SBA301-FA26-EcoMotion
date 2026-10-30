-- TV5: Driver Status Management migration
-- Thêm activity_status và status_updated_at trên bảng driver

ALTER TABLE driver ADD COLUMN IF NOT EXISTS activity_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE driver ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_driver_activity_status ON driver (activity_status);
CREATE INDEX IF NOT EXISTS idx_driver_status_updated_at ON driver (status_updated_at);
