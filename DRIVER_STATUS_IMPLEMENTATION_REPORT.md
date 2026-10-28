# Driver Status Management — báo cáo triển khai

Ngày: 28/10/2026.

## 1. Phạm vi và file thay đổi

Đã kiểm tra entity, API, service phân công OR-Tools, vòng đời chuyến đi, bảo mật JWT, SSE, schema/migration, các màn hình Driver/Provider và các test liên quan trước khi sửa. Các phần trạng thái đã có sẵn được tái sử dụng.

- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingServiceImpl.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/DriverOptimizationServiceImpl.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/controller/NotificationStreamController.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/service/NotificationStreamService.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/service/implement/NotificationStreamServiceImpl.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/repository/DriverRepository.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/DriverStatusServiceImpl.java
- Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/UserServiceImpl.java
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/DriverTripLifecycleIntegrationTest.java
- Frontend/EcoMotion/src/components/driver/DriverLayout.jsx
- Frontend/EcoMotion/src/components/provider/modals/AssignDriverModal.jsx
- Frontend/EcoMotion/src/hooks/useAvailableDrivers.js
- Frontend/EcoMotion/src/hooks/useRealtimeSync.js
- Frontend/EcoMotion/src/pages/provider/ProviderDriverPage.jsx
- Frontend/EcoMotion/src/services/bookingService.js
- Frontend/EcoMotion/src/services/providerBookingService.js
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/DriverAssignmentConcurrencyIntegrationTest.java
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/service/DriverAssignmentEligibilityTest.java
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/common/service/NotificationStreamServiceTest.java
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/controller/DriverStatusIntegrationTest.java
- Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/service/DriverStatusServiceTest.java
- Frontend/EcoMotion/src/components/driver/DriverAvailability.jsx
- Frontend/EcoMotion/src/components/driver/DriverStatusBadge.jsx
- Frontend/EcoMotion/src/services/driverStatusService.js
- Frontend/EcoMotion/src/utils/driverStatus.js
- Frontend/EcoMotion/test/driverStatus.test.js

## 2. Entity, enum, field

Không thêm entity, bảng, enum hay cột mới. Tái sử dụng bảng driver với activity_status và status_updated_at; DriverActivityStatus gồm AVAILABLE, UNAVAILABLE, OFFLINE. DriverOperationalStatus gồm thêm SCHEDULED và ON_TRIP được tính từ booking, không lưu trạng thái chuyến đi vào activity_status. DriverStatus tiếp tục là trạng thái duyệt hồ sơ.

Migration SQL 20261026_driver_status_management.sql đã tồn tại trước thay đổi. Repository hiện không cấu hình Flyway trong pom.xml; không tự thêm cơ chế migration mới vì lần sửa này không thay đổi schema. Chưa chạy migration trên database thực tế.

## 3. API sử dụng

Giữ nguyên contract backend, nối frontend vào các API đã có:

- GET /api/v1/driver/status: trạng thái của principal, chuyến hiện tại và tiếp theo.
- PUT /api/v1/driver/status: body activityStatus; không nhận danh tính tài xế từ client.
- GET /api/provider/drivers?operationalStatus=AVAILABLE&sort=status_updated_at: lọc trạng thái vận hành, mới cập nhật trước.
- GET /api/provider/bookings/{idOrCode}/assign-candidates: điều kiện theo booking cụ thể cho cả lựa chọn thủ công.
- Các API assign-suggest, auto-assign, gán tài xế và vòng đời chuyến đi được giữ nguyên.

Lỗi sở hữu booking/tài xế trong optimizer chuyển sang AccessDeniedException (403). Endpoint quản lý tài xế của Provider tiếp tục trả 404 khi tài xế nằm ngoài phạm vi sở hữu, tránh lộ thông tin.

## 4. Business rules và concurrency

- OFFLINE/UNAVAILABLE không nhận phân công mới; AVAILABLE có thể hiển thị SCHEDULED khi có lịch tương lai.
- Manual và Auto gọi cùng evaluateDriver để kiểm tra duyệt hồ sơ, tài khoản, readiness, GPLX, sự cố, lịch và buffer hiện có.
- Auto đánh giá lại sau khi lấy khóa tài xế, thay vì chỉ tin đề xuất trước đó.
- Cập nhật readiness dùng PESSIMISTIC_WRITE trên chính tài xế của principal, cùng tài nguyên khóa với phân công.
- UserService refresh entity sau khi lấy khóa, tránh dùng readiness cũ đã được nạp trong persistence context trước lúc chờ khóa.
- Không cho ghi đè readiness khi ON_TRIP. Khi hoàn thành, trạng thái được tính lại từ booking và readiness.
- Khi renter hủy booking, cập nhật timestamp và phát trạng thái mới cho tài xế/Provider.
- Fixture happy-path phân công trong test vòng đời được đặt vào khung thời gian riêng để không trùng booking seed khác.

## 5. Giao diện

DriverLayout hiển thị readiness, trạng thái vận hành, chuyến hiện tại/tiếp theo. Chọn trạng thái bị khóa khi đang chạy hoặc đang lưu. UI đọc lại backend sau ghi; không giả lập trạng thái thành công.

ProviderDriverPage có badge, bộ lọc vận hành, sắp xếp theo lần cập nhật, mã chuyến hiện tại/tiếp theo và giờ đón sắp tới. Hai luồng modal phân công dùng candidate theo booking thay cho danh sách khả dụng chung. Tài xế không đủ điều kiện bị chặn chọn; modal chính tải lại candidate khi phân công thất bại.

## 6. Đồng bộ Driver ↔ Provider

Tái sử dụng SSE hiện có. DRIVER_STATUS_CHANGED được đăng ký phía frontend. Chỉ gửi sự kiện sau transaction commit; rollback không gửi. Một tài khoản có thể giữ nhiều emitter cho nhiều tab/hook, đóng một kết nối không xóa các kết nối khác.

Sau kết nối SSE, client đọc lại nguồn dữ liệu backend. Khi SSE lỗi, fallback polling 15 giây, bỏ qua tab ẩn; focus/online cũng đồng bộ lại. Listener/interval/emitter có cleanup. Request counter loại phản hồi cũ trên trạng thái Driver, danh sách Provider và danh sách candidate.

## 7. Google OR-Tools

Giữ solver CP-SAT và cách tính điểm hiện có. Chỉ đưa candidate eligible vào solver. Cả phân công thủ công và tự động dùng cùng hàm đánh giá; Auto kiểm tra lại sau khóa để chặn trường hợp tài xế đã tắt nhận chuyến hoặc vừa nhận booking trùng. Không tạo repository dependency mới giữa feature và không thêm circular dependency.

## 8. Kiểm chứng

Backend: 54 test liên quan PASS, 0 failure/error, gồm:

| Nhóm | Số test |
|---|---:|
| DriverStatusServiceTest | 6 |
| DriverAssignmentEligibilityTest | 5 |
| NotificationStreamServiceTest | 2 |
| DriverStatusIntegrationTest | 4 |
| DriverAssignmentConcurrencyIntegrationTest | 1 |
| DriverTripServiceUnitTest | 15 |
| DriverTripLifecycleIntegrationTest | 5 |
| BookingConflictRegressionIntegrationTest | 5 |
| ProviderDriverServiceTest | 6 |
| RenterBookingApiIntegrationTest | 5 |

Test concurrency chạy hai thread/transaction thật trên H2: đúng một request gán thành công, booking còn lại không được đặt trùng. Test SSE kiểm tra sau commit, rollback và nhiều kết nối. Test API kiểm tra projection nhất quán, chặn Provider sửa readiness, enum dẫn xuất không hợp lệ, tài xế ngoài phạm vi sở hữu và ON_TRIP không bị ghi đè.

Frontend: 68/68 test PASS; production build PASS. Thêm test contract driver status, payload chỉ chứa readiness, truyền lỗi conflict và đủ nhãn cho năm trạng thái. git diff --check PASS.

Các ca yêu cầu 1–6, 8–10, 12–14 được phủ trực tiếp bằng các test trên. Ca 11 được bảo vệ bằng principal và endpoint không có tham số danh tính để ghi trạng thái; GET cũng được kiểm tra khi client thêm driverId khác. Luồng từ chối và hiển thị nhất quán (7, 15) có kiểm tra từng lớp/lifecycle; chưa chạy browser E2E hai phiên Driver/Provider.

## 9. Giới hạn còn lại

- Chưa chạy browser E2E, kiểm tra responsive bằng trình duyệt hay kiểm thử tải trên PostgreSQL. Concurrency integration chạy trên H2.
- SSE lưu emitter trong bộ nhớ của một instance; chưa kiểm chứng nhiều backend instance hoặc phục hồi lịch sử sự kiện. Sau mất kết nối, client đọc lại dữ liệu qua polling/focus.
- Chưa kiểm thử đầu cuối riêng luồng scheduler timeout/reassign và toàn bộ O11 ngoài nhóm regression đã nêu.
- Lint trên các component/hook có sẵn còn lỗi React hooks; các file component trạng thái mới không có lint error. Không khẳng định toàn repository sạch lint.
- Vite build có cảnh báo bundle lớn hơn 500 kB; không thay đổi cấu trúc bundle ngoài phạm vi task.
