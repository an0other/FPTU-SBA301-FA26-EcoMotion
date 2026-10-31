# Đặt xe theo thời gian và ghép gói giá

Khách nhập giờ nhận và giờ trả mong muốn theo giờ tròn. Hệ thống tính hai loại ứng viên: ghép đúng thời lượng theo đơn vị lớn đến nhỏ (tháng → ngày → giờ), và làm tròn lên gói lớn hơn bất kể đắt hơn hay rẻ hơn, với điều kiện xe còn trống đủ thời gian. Hiển thị tối đa hai phương án: phương án ghép đúng thời lượng đứng trước, phương án làm tròn gần nhất còn đủ lịch xe đứng sau. Đánh dấu Tối ưu ở phương án rẻ nhất; khách tự chọn và xác nhận giờ trả trước khi tiếp tục. Phương án 2 chọn mốc gói lớn hơn gần nhất, kể cả khi bằng giá hoặc đắt hơn. Nếu thời lượng đã tròn gói, tăng thêm một gói tại mốc kế tiếp. Nếu vượt giới hạn thuê hoặc không còn lịch thì không cho đặt phương án đó. Khách chọn phương án trước khi đặt. Giờ trả chính thức là giờ nhận cộng toàn bộ thời lượng các gói đã mua; lịch xe được giữ hết khoảng đó.

## Dữ liệu và giá

- Booking lưu pickup_time và planned_end_at. Không suy thời gian thuê từ tổng tiền hoặc giá gói.
- booking_pricing_item lưu ID gói, loại gói, thời lượng, đơn giá snapshot và số lượng.
- Một dòng BASE_RENTAL trong booking_charge lưu tổng phí thuê, giữ tương thích thanh toán.
- booking.plan_id và booking_policy_snapshot dùng chính sách gói dài nhất trong tổ hợp; cùng độ dài thì chọn ID nhỏ nhất. Giá chi tiết đọc từ booking_pricing_item.
- Gói giờ là 60 phút. Giá chuyển đổi bằng giá cũ chia số giờ của slot cũ, làm tròn đến đồng theo HALF_UP. Đơn cũ giữ nguyên snapshot.
- Gói tháng vẫn dùng slot_length_minutes; giới hạn thuê là 360 ngày. Thuê năm dùng luồng riêng.

## API

POST /api/renter/bookings/rental-quote nhận vehicleId, pickupTime, requestedEndAt. Response trả available, reason, searchFrom, searchTo, availableWindows, occupiedWindows và options. Mỗi option có plannedEndAt, coveredHours, policyPlanId, rentalAmount, depositAmount và items.

POST /api/renter/bookings nhận pickupTime, plannedEndAt, pricingItems [{planId, quantity}], expectedRentalAmount cùng thông tin điểm đón/hành khách. Backend tính lại tiền và thời lượng, khóa xe, kiểm tra lịch và điều kiện hiện có trước khi lưu. Giá hoặc lịch thay đổi trả 409 để khách lấy báo giá mới. Dạng request planId/slotCount cũ vẫn được chấp nhận để tương thích các client cũ.

Các khoảng trống được tìm trong 30 ngày từ giờ nhận đã chọn, loại lịch bận và thời gian đệm, làm tròn đầu lên và cuối xuống giờ tròn. occupiedWindows trả các khoảng xe bận giao với thời gian yêu cầu, đã bao gồm thời gian đệm; giao diện hiển thị rõ từ/đến để khách đổi lịch. Báo giá không giữ xe. Ngay khi báo giá, kiểm tra lịch đơn thuê của người đang đăng nhập bằng cùng quy tắc lúc tạo đơn. Nếu khoảng yêu cầu trùng lịch thì trả 409 để hiển thị lỗi ngay ở bước chọn giờ; nếu chỉ phương án làm tròn trùng thì loại phương án đó và thông báo. Dời giờ nhận của đơn chưa cọc giữ nguyên thời lượng và giá đã mua, đồng thời kiểm tra lại lịch. Flow duyệt/phân công tài xế giữ nguyên.

## Nâng cấp database

Chạy Backend/EcoMotion/src/main/resources/db/migrations/20261029_mixed_rental_pricing.sql một lần trước khi chạy bản mới. Không chạy lại schema.sql trên database có dữ liệu cần giữ.

Migration backfill giờ trả từ lịch booking gốc, hoặc snapshot và dòng phí thuê nếu thiếu lịch. Chi tiết đơn cũ chỉ backfill khi có một dòng BASE_RENTAL hợp lệ khớp đơn giá × số lượng. Nếu dữ liệu không đủ, toàn bộ migration rollback và thông báo mã đơn cần rà soát. schema.sql dành cho database khởi tạo mới.

Đã thử schema mới, nâng cấp dữ liệu cũ, chuyển giá 4 giờ sang 1 giờ và rollback khi dữ liệu không nhất quán trên PostgreSQL tạm riêng. Chưa áp dụng migration lên database dự án.

## Kiểm thử còn được giữ

Theo yêu cầu, toàn bộ test cũ đã được xóa. Bộ test hiện chỉ bao gồm RentalPriceOptimizerTest, RentalPricingServiceTest, RenterQuoteAvailabilityTest, MixedRentalBookingIntegrationTest, frontend test/rentalPricing.test.js và E2E e2e/mixed-rental-pricing.spec.js. Cấu hình H2 và Mockito được giữ để các test mới chạy độc lập.

Chạy backend bằng mvnw.cmd clean test, frontend bằng npm test và npm run test:e2e. Các test mới kiểm tra tối ưu tổ hợp, giới hạn lịch có đệm, xác nhận giá, lưu giờ trả, dời lịch, tranh chấp đặt xe đồng thời, lựa chọn phương án và phản hồi báo giá cũ.
