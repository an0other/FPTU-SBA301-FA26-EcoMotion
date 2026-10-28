# Driver real-time status & assignment availability

Ngày triển khai: 28/10/2026.

## Audit và lựa chọn thay đổi

Source đã có `Driver.activityStatus`, trạng thái duyệt hồ sơ, `Booking`, `DriverAssignment`, lịch xe, DTO ứng viên, OR-Tools CP-SAT, khóa tài xế và SSE sau commit. Không cần entity, bảng, migration hoặc endpoint mới.

Các điểm cần sửa: optimizer tự tính buffer khác availability service, bỏ sót các trạng thái chờ xác nhận, chuyến quá giờ dự kiến có thể được coi là hết bận, danh sách ứng viên thiếu trạng thái và lịch trình, màn phân công thiếu bộ lọc và cơ chế hiển thị lịch trước xác nhận. Các truy vấn sự cố, workload, vị trí và lịch trước đây chạy theo từng tài xế.

Giải pháp: mở rộng contract ứng viên hiện có, dùng chung availability service và policy giao khoảng thời gian; giữ các điều kiện GPLX, hồ sơ, tài khoản, sự cố và cách chấm điểm CP-SAT trong optimizer. Provider vẫn là phạm vi truy cập dữ liệu.

## Contract và quy tắc

`GET /api/provider/bookings/{bookingCode}/assign-candidates` giữ các trường cũ, bổ sung object `availability`:

| Trường | Ý nghĩa |
|---|---|
| workStatus | Readiness hiện có: AVAILABLE / OFFLINE / UNAVAILABLE |
| availabilityStatus | AVAILABLE / ON_TRIP / SCHEDULE_CONFLICT / PENDING_ASSIGNMENT / OFFLINE / UNAVAILABLE |
| reason | Mã nguyên nhân lịch/readiness; lý do hồ sơ khác nằm trong ineligibilityReason |
| currentBookingCode, currentTripStartedAt | Chuyến chưa kết thúc hợp lệ và thời gian bắt đầu thực tế |
| conflictingBookingCode, conflictStart, conflictEnd | Booking chiếm lịch và khoảng thời gian dự kiến |
| nextBookingCode, nextPickupAt | Chuyến tiếp theo theo lịch hiện tại |
| nextAvailableAt | Thời điểm hết lịch xung đột đang hiển thị cộng buffer; null với chuyến chưa kết thúc hợp lệ |
| lastUpdatedAt | Thời điểm server tính projection |

`eligible` vẫn là kết luận cuối của backend cho booking đang xét. Nhãn hiển thị không thay thế eligibility. Một hồ sơ không hợp lệ vẫn hiển thị trong danh sách cùng nguyên nhân và không chọn được.

Buffer đọc từ `Vehicle.maintenanceBufferMinutes`; null hoặc số âm dùng 0. Công thức dùng đúng biên mở: `requestedStart < occupiedEnd + occupiedBuffer` và `requestedEnd + requestedBuffer > occupiedStart`. Không cộng buffer hai lần. Hai booking tiếp giáp được phép khi buffer bằng 0. Mốc lưu trữ tiếp tục là `Instant`; ngày GPLX và giao diện dùng múi giờ Việt Nam.

Lịch ngày mai không khóa booking hôm nay khi không giao khoảng thời gian. `IN_PROGRESS` và `AWAITING_END_CONFIRMATION` tiếp tục chiếm tài xế cho đến kết thúc hợp lệ, kể cả đã quá giờ dự kiến hoặc driver đã ghi nhận giờ kết thúc nhưng renter chưa xác nhận. Các yêu cầu `DRIVER_ASSIGNED` hết hạn không còn giữ slot; yêu cầu cũ không thể được tài xế xác nhận lại sau hạn.

Manual và Auto đánh giá lại trong transaction sau khóa tài xế. Luồng Provider khóa booking trước khi phân công; luồng xác nhận tài xế cũng khóa booking rồi tài xế và kiểm tra hạn phản hồi. CP-SAT chỉ nhận ứng viên eligible; khi không có ứng viên trả INFEASIBLE, không ép gán.

Danh sách dùng các truy vấn theo lô cho hồ sơ kèm user, lịch booking, snapshot, base charge, sự cố, workload và vị trí mới nhất. Không truy cập repository của feature khác qua các dependency mới.

## Giao diện và đồng bộ

Modal phân công của O08/O07 hiển thị đủ sáu badge tiếng Việt, tìm kiếm kết hợp bộ lọc, ưu tiên tài xế eligible, giữ các tài xế bị chặn trong danh sách. Hiển thị chuyến đang chạy, lịch xung đột, chuyến tiếp theo và phần xác nhận lịch trình khi chọn tài xế. Nút chọn và xác nhận bị khóa khi không đủ điều kiện, đang tải lại hoặc tải lỗi. Tab gợi ý dùng trạng thái ứng viên đang được đồng bộ thay cho chỉ giữ snapshot đề xuất cũ.

Tái sử dụng `useAvailableDrivers` và SSE `DRIVER_STATUS_CHANGED` hiện có; fallback polling 15 giây khi SSE mất kết nối, bỏ qua tab ẩn, đồng bộ lại khi focus/online và cleanup khi đóng/unmount. Refetch ứng viên sau phân công thành công. Khi hết hạn giữ chỗ, scheduler phát trạng thái mới cho Provider kể cả không tìm được tài xế thay thế. Khi renter xác nhận bắt đầu/kết thúc, flush booking rồi cập nhật timestamp và phát trạng thái qua service hiện có; SSE tiếp tục chỉ gửi sau commit.

## File thay đổi

Đường dẫn Java dưới `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/`:

| File | Thay đổi |
|---|---|
| booking/dto/response/AssignmentAvailabilityResponse.java | Projection trạng thái và lịch trình mới |
| booking/dto/response/DriverCandidateResponse.java | Mở rộng contract tương thích với constructor cũ |
| booking/service/DriverAvailabilityService.java | API nội bộ tổng hợp theo lô |
| booking/service/AvailabilityPolicy.java | Quy tắc overlap thống nhất |
| booking/service/implement/DriverAvailabilityServiceImpl.java | Tổng hợp trạng thái, lịch và slot hợp lệ |
| booking/service/implement/DriverOptimizationServiceImpl.java | Tái sử dụng availability, tải scoring theo lô, thông báo timeout |
| booking/service/implement/ProviderBookingServiceImpl.java | Khóa booking ở luồng phân công |
| booking/service/implement/DriverTripServiceImpl.java | Khóa khi nhận chuyến, từ chối yêu cầu hết hạn |
| booking/service/implement/BookingServiceImpl.java | Đồng bộ trạng thái sau xác nhận bắt đầu/kết thúc của renter |
| booking/repository/BookingRepository.java | Query lịch và workload theo lô |
| booking/repository/BookingPolicySnapshotRepository.java | Snapshot theo danh sách booking |
| booking/repository/BookingChargeRepository.java | Base charge theo danh sách booking |
| booking/repository/TripIncidentRepository.java | Danh sách tài xế có sự cố |
| booking/repository/VehicleLocationRepository.java | Vị trí gần nhất theo lô, có tie-break theo ID |
| user/service/implement/UserServiceImpl.java | Fetch tài xế cùng user |
| user/service/implement/DriverStatusServiceImpl.java | Giữ ON_TRIP khi chờ kết thúc hợp lệ |

Frontend dưới `Frontend/EcoMotion/`:

- `src/components/provider/modals/AssignDriverModal.jsx`
- `src/components/provider/AssignmentDriverStatus.jsx`
- `src/utils/assignmentAvailability.js`
- `test/assignmentAvailability.test.js`
- `e2e/assignment-availability.spec.js`

Test backend dưới `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/`:

- `booking/service/AssignmentAvailabilityTest.java`
- `booking/service/DriverAvailabilityServiceTest.java`
- `booking/service/DriverAssignmentEligibilityTest.java`
- `booking/controller/ProviderBookingApiIntegrationTest.java`
- `booking/controller/DriverTripLifecycleIntegrationTest.java`
- `booking/controller/BookingConflictRegressionIntegrationTest.java`
- `user/service/DriverStatusServiceTest.java`

Tài liệu: `AGENT_CONTEXT.md` và báo cáo này. File `DRIVER_STATUS_IMPLEMENTATION_REPORT.md` đã tồn tại ở working tree trước phiên này, được giữ nguyên.

## Kiểm chứng

Frontend: 83/83 unit/contract test PASS; production build PASS; ESLint trên ba file source frontend sửa/thêm PASS. Hai browser test Playwright dùng API mock PASS ở viewport 1280px và 390px: giữ tài xế bận trong danh sách, khóa chọn, tìm/lọc, hiển thị lịch trước xác nhận, focus refresh khóa lại lựa chọn vừa chuyển offline và không tràn ngang modal.

Backend: **94/94 test PASS, 0 failure, 0 error**, Maven BUILD SUCCESS. Chạy với H2 và cấu hình test hiện có, không truy cập PostgreSQL của developer.

| Nhóm Maven test | Số ca PASS |
|---|---:|
| AssignmentAvailabilityTest | 12 |
| AvailabilityPolicyTest | 2 |
| DriverAssignmentEligibilityTest | 12 |
| DriverAvailabilityServiceTest | 2 |
| DriverAssignmentConcurrencyIntegrationTest | 1 |
| DriverTripLifecycleIntegrationTest | 7 |
| DriverTripServiceUnitTest | 15 |
| ProviderBookingApiIntegrationTest | 12 |
| RenterBookingApiIntegrationTest | 7 |
| BookingConflictRegressionIntegrationTest | 5 |
| NotificationStreamServiceTest | 2 |
| DriverStatusIntegrationTest | 4 |
| DriverStatusServiceTest | 7 |
| ProviderDriverServiceTest | 6 |

Lệnh kiểm tra frontend: `npm.cmd test`, `npm.cmd run build`, `npx.cmd playwright test e2e/assignment-availability.spec.js --reporter=line` và ESLint trên ba file source sửa/thêm. Backend dùng Maven `-Dtest=` với 14 nhóm trong bảng trên rồi `test`. `git diff --check` PASS.

| Ca yêu cầu | Kiểm chứng |
|---|---|
| 1. Rảnh có thể phân công | Availability unit, manual happy path, Provider API |
| 2. Đang chạy hiển thị ON_TRIP | Projection unit, Driver status, lifecycle API |
| 3. Trùng lịch không gán | Unit dùng chung Manual/Auto, concurrency integration |
| 4. Lịch ngày mai không khóa hôm nay | Projection và manual regression |
| 5–6. Offline/tạm nghỉ | Parameterized unit Manual/Auto, API giữ tài xế offline trong danh sách |
| 7–8. Bắt đầu/kết thúc tính lại trạng thái | Lifecycle API và Driver status; cơ chế event sau commit được kiểm tra riêng |
| 9. Từ chối giải phóng lịch | Lifecycle reject và tính lại projection; timeout cũng có test giải phóng và event |
| 10. Đồng thời chỉ một lần gán | Hai thread/transaction thật với khóa trên H2 |
| 11. Provider ngoài quyền quản lý | Unit chặn truy cập booking và gán driver của Provider khác |
| 12. OR-Tools loại tài xế không đủ điều kiện | Offline/tạm nghỉ, hồ sơ chưa duyệt/khóa, lịch xung đột và kiểm tra lại sau khóa |
| 13. Khoảng đệm và biên thời gian | Zero-buffer adjacency, thiếu 1 giây buffer, buffer chỉ cộng một lần |
| 14. Quá giờ dự kiến chưa kết thúc | IN_PROGRESS / AWAITING_END_CONFIRMATION, nextAvailableAt không suy đoán |
| 15. Refresh/reconnect | Browser focus refresh; hook SSE/polling hiện có; kiểm thử event sau commit/rollback |

Các test đặt xe cũ dùng năm 2036–2038 trong khi GPLX seed hết hạn trước đó, kỳ vọng PENDING dù code hiện tại tạo AWAITING_DEPOSIT, và dùng cùng xe để kiểm tra riêng xung đột renter. Fixture được tách khỏi chuyến seed chưa hoàn tất, GPLX fixture còn hạn theo năm test, cập nhật assertion theo contract hiện hành và dùng xe khác cùng Provider cho ca xung đột renter. Không thay đổi nghiệp vụ đặt xe để làm test qua.

## Giới hạn

- Browser test dùng API mock; chưa chạy E2E hai phiên Driver/Provider kết nối backend thật.
- Concurrency test chạy H2; chưa kiểm thử tải hoặc execution plan trên PostgreSQL.
- SSE hiện có giữ emitter theo instance; chưa xác minh triển khai nhiều instance.
- `nextAvailableAt` mô tả lịch xung đột được hiển thị, không bảo đảm tài xế sẽ đủ mọi điều kiện vào thời điểm đó; backend luôn đánh giá lại khi gán.
- Vite còn cảnh báo bundle lớn hơn 500 kB. Không khẳng định toàn repository sạch lint hoặc toàn bộ test suite đã chạy.
