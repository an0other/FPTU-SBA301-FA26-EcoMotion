-- Driver độc quyền bắt đầu/kết thúc chuyến. Renter không còn xác nhận bắt đầu hoặc kết thúc.
-- Chỉ chuyển tiếp dữ liệu đang dở: không xóa cột, không xóa giá trị enum, không xóa lịch sử.
-- Chạy lại an toàn: mọi câu lệnh chỉ đụng tới trạng thái legacy.

BEGIN;

-- 1) Chuyến đang chờ xác nhận bắt đầu của hai bên -> đang diễn ra.
--    Thời điểm bắt đầu lấy từ xác nhận của Tài xế nếu có, nếu không thì của Renter.
UPDATE booking
   SET status = 'IN_PROGRESS',
       actual_start_at = COALESCE(actual_start_at, driver_start_confirmed_at, renter_start_confirmed_at, updated_at, now()),
       driver_start_confirmed_at = COALESCE(driver_start_confirmed_at, renter_start_confirmed_at, actual_start_at, updated_at, now())
 WHERE status = 'AWAITING_START_CONFIRMATION';

-- 2) Chuyến đang chờ xác nhận kết thúc của hai bên -> chờ nhà cung cấp chốt phụ phí
--    (đúng trạng thái sau khi Tài xế kết thúc). Giữ nguyên thời điểm kết thúc đã ghi nhận.
UPDATE booking
   SET status = 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
       actual_end_at = COALESCE(actual_end_at, driver_end_confirmed_at, renter_end_confirmed_at, updated_at, now()),
       end_confirmation_method = COALESCE(end_confirmation_method, 'DRIVER')
 WHERE status = 'AWAITING_END_CONFIRMATION';

-- 3) Lịch sử báo kết thúc còn đang đếm ngược phản đối của Renter: không còn cửa sổ phản đối.
UPDATE trip_end_confirmation
   SET state = 'CONFIRMED',
       confirmation_method = 'MIGRATION',
       confirmed_at = COALESCE(confirmed_at, now())
 WHERE state = 'WAITING';

COMMIT;
