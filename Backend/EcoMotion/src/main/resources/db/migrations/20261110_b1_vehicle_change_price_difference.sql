-- B1: chênh lệch giá khi đổi xe. Chỉ thêm cột vào bảng hiện có (không tạo bảng mới); idempotent.
BEGIN;
-- Booking thường: xe cũ = previous_vehicle_id; lưu giá hai xe và giảm trừ tại thời điểm đổi.
ALTER TABLE booking ADD COLUMN IF NOT EXISTS vehicle_change_at timestamptz;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS vehicle_change_old_amount numeric(14,2);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS vehicle_change_new_amount numeric(14,2);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS vehicle_change_discount numeric(14,2);

-- Gói năm: giá xe cũ/mới và xác nhận chất lượng trên yêu cầu đổi xe; giảm trừ trên quyết toán.
ALTER TABLE yearly_replacement ADD COLUMN IF NOT EXISTS old_plan_price numeric(14,2);
ALTER TABLE yearly_replacement ADD COLUMN IF NOT EXISTS new_plan_price numeric(14,2);
ALTER TABLE yearly_replacement ADD COLUMN IF NOT EXISTS lower_quality boolean;
ALTER TABLE yearly_replacement ADD COLUMN IF NOT EXISTS quality_notes varchar(1000);
ALTER TABLE yearly_replacement ADD COLUMN IF NOT EXISTS quality_acknowledged boolean;
ALTER TABLE yearly_workflow ADD COLUMN IF NOT EXISTS vehicle_change_discount numeric(14,2) DEFAULT 0;
COMMIT;
