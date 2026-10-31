# Đồng bộ code và lọc thời gian cập nhật danh sách tài xế

Ngày: 28/10/2026, giờ Việt Nam.

## Git và quy tắc dự án

- Repository: `FPTU-SBA301-FA26-EcoMotion`, remote `origin` là `https://github.com/an0other/FPTU-SBA301-FA26-EcoMotion.git`.
- Không tìm thấy `AGENTS.md` trong repository hoặc các thư mục cha. Đã đọc `AGENT_CONTEXT.md`, mục Git của tài liệu này, và `Backend/EcoMotion/AGENT_CODE_STRUCTURE.md` trước khi sửa Java.
- Branch làm việc: `task-member-5`, HEAD giữ nguyên `dc537ce`. Hai commit local `dc537ce` và `9967d1a` được bảo toàn.
- Trước khi làm: không có thay đổi tracked; `DRIVER_STATUS_IMPLEMENTATION_REPORT.md` là file untracked. File này được giữ nguyên và không stage.
- Nguồn tích hợp: `origin/merge` tại `4fe35b3882512507be5a66dbd3f76cb8c08ac260`, PR #39. Căn cứ chọn nhánh: lịch sử tích hợp `45406e2`, `f9148f8` và báo cáo `docs/TV5_AUDIT_AND_ARCHITECTURE_REPORT.md`; không chọn theo tên nhánh mặc định của remote.
- Đã fetch thành công, sau đó chạy `git merge --no-commit --no-ff origin/merge`. Không tích hợp thêm `origin/main` vì đây không phải nguồn tích hợp đã xác định cho lần đồng bộ này.
- Đã giải quyết 30 file conflict, `git ls-files -u` không còn kết quả. Thay đổi đã stage để đánh dấu giải quyết conflict. `MERGE_HEAD` còn trỏ tới `4fe35b3`: merge đang chờ commit theo yêu cầu không tự commit/push.
- Không discard, reset hard, stash, rebase hoặc ghi đè thay đổi local. Không commit/push.

## Phạm vi chức năng

Màn hình được xác định từ tab báo cáo đang mở và phần triển khai hiện tại: `/provider/drivers`, component `ProviderDriverPage.jsx`.

Trường dùng để sắp xếp/lọc là `Driver.updatedAt` → `driver.updated_at`, kiểu Java `Instant`, PostgreSQL `timestamptz`. Không dùng `statusUpdatedAt` thay cho thời gian sửa hồ sơ. Entity đã có `@PrePersist`/`@PreUpdate`, schema đã có cột và cơ chế timestamp; tính năng này không cần thêm migration.

- Mặc định: `updatedAt DESC`, sau đó `id DESC`.
- “Cập nhật cũ nhất”: `updatedAt ASC`, sau đó `id DESC`.
- Null luôn ở cuối cả hai chiều, không thay bằng thời gian tạo; khi lọc ngày thì loại null.
- Từ ngày/đến ngày theo `Asia/Ho_Chi_Minh` (UTC+7). Khoảng lọc là `[đầu ngày bắt đầu, đầu ngày sau ngày kết thúc)`, giữ đủ phần giây lẻ cuối ngày.
- Có thể chỉ nhập một đầu khoảng. Khoảng ngược, ngày sai định dạng và tham số phân trang không hợp lệ trả 400.
- Giữ tìm kiếm tên/email/điện thoại/GPLX, trạng thái duyệt, trạng thái vận hành, sắp xếp đánh giá/GPLX/ngày tạo và chế độ thẻ/bảng.
- Lọc, tìm kiếm và sắp xếp thực hiện trên toàn bộ tập tài xế thuộc Provider ở backend, trước khi cắt trang. Frontend không tự lọc/sắp xếp riêng trang hiện tại.
- Đổi điều kiện đưa offset về 0; dữ liệu cũ từ request chậm bị bỏ qua. Trường hợp trang cuối không còn dữ liệu sau cập nhật sẽ tải lại trang đầu.
- Hiển thị thời gian cập nhật trên cả thẻ và bảng theo giờ Việt Nam, có nhãn riêng khi thiếu timestamp.
- Khi Provider chỉ sửa thông tin nằm trên User như tên/điện thoại, vẫn touch `Driver.updatedAt` và flush trước khi tạo response. Đọc danh sách là transaction read-only, không save/touch timestamp.

## API

`GET /api/provider/drivers?offset=0&limit=12&sort=updated_desc&updatedFrom=2026-10-28&updatedTo=2026-10-28`

Các tham số kết hợp: `search`, `status`, `operationalStatus`. `sort=updated_asc` cho chiều cũ nhất. Response phân trang: `data.items`, `data.total`, `data.offset`, `data.limit`, `data.hasMore`. `offset >= 0`, `1 <= limit <= 100`.

Giữ contract danh sách cũ khi không truyền `offset` để các caller hiện có tiếp tục hoạt động. Màn hình mới luôn truyền `offset` để dùng API lọc ngày và phân trang.

## File triển khai tính năng

- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/controller/ProviderDriverController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/ProviderDriverService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/ProviderDriverServiceImpl.java`
- `Frontend/EcoMotion/src/pages/provider/ProviderDriverPage.jsx`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/service/ProviderDriverUpdatedTimeTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/controller/ProviderDriverUpdatedTimeIntegrationTest.java`
- `Frontend/EcoMotion/e2e/provider-driver-updated-time.spec.js`
- Báo cáo này.

## Giải quyết conflict

Kết hợp chức năng mới từ remote về booking hold, xử lý rủi ro vận hành, đổi tài xế, chứng từ/hoàn tiền, lịch xe và giao diện đặt xe với cơ chế local về readiness, khóa tài xế, kiểm tra điều kiện phân công, OR-Tools và SSE. Giữ các service query tương thích mà code local đang gọi.

Sửa contract DTO sau merge, tham số URL gán tài xế, fixture tài khoản ngân hàng trùng seed và các đoạn test ghép sai. Hạng GPLX `B` có trong `RenterReviewDataSeeder` được bổ sung vào bộ kiểm tra phân công local và có regression test cùng hạng `B2`. Giữ kiểm tra điều kiện sau khi lấy khóa và cập nhật trạng thái sau phân công. Giữ chặn submit lặp và thông báo lỗi của giao diện xác nhận đặt xe, đồng thời giữ giao diện wizard mới.

Thao tác thay toàn bộ file test bằng bản remote đã bị automatic approval review từ chối; không thực hiện thao tác đó. Đã dùng bản vá riêng cho đoạn fixture conflict và giữ các test local còn lại. Không còn phần việc bị chặn bởi xét duyệt.

## Kiểm chứng

| Kiểm tra | Kết quả |
| --- | --- |
| Maven package, 13 nhóm test liên quan | PASS: 91 test, 0 failure/error |
| Test thời gian cập nhật mới | PASS: 8 service test + 2 API/persistence test |
| Frontend node test | PASS: 79/79 |
| Playwright Chrome, danh sách tài xế | PASS: 1/1 |
| Frontend production build | PASS |
| ESLint `ProviderDriverPage.jsx` | PASS: 0 error/warning |
| `git diff --cached --check -- Backend Frontend` | PASS |
| Conflict marker trong source và unmerged index | Không còn |

Backend regression gồm phân công đồng thời, điều kiện phân công, vòng đời chuyến đi, API booking Provider/Renter, thanh toán, tài chính, lịch xe, trạng thái tài xế và boundary xe. Test mới xác minh ngày UTC+7 với phần giây lẻ, timestamp null, thứ tự tie ổn định qua trang, search/status kết hợp, khoảng một đầu, không kết quả, validation, sửa riêng tên lưu timestamp thật và hai lần đọc không làm đổi timestamp.

E2E xác minh request có đúng sort/ngày/search/trạng thái, chuyển trang, reset trang, giao diện không kết quả và khoảng ngày ngược. API trong E2E được mock; kiểm tra persistence/backend chạy riêng trên H2.

Log local (được Git ignore): `backend-final.log`, `frontend-test-final.log`, `frontend-build-final.log`, `driver-e2e-final.log`, `driver-lint-final.log`.

## Giới hạn

- Không chạy toàn bộ test suite của repository; đã chạy các nhóm liên quan nêu trên.
- Chưa kiểm thử đầu cuối trên PostgreSQL và backend thật bằng trình duyệt. Không chạy migration trên database thật.
- Backend tiếp tục nạp toàn bộ đội tài xế của Provider rồi lọc/sắp xếp trước phân trang, theo cấu trúc có sẵn để giữ tìm kiếm điện thoại đã mã hóa và trạng thái vận hành tính động. Cách này đúng thứ tự toàn tập nhưng cần tối ưu thêm khi đội tài xế rất lớn.
- Vite còn cảnh báo bundle lớn hơn 500 kB.
- `git diff --check` cho toàn bộ merge có trailing whitespace trong tài liệu remote `docs/BAO_CAO_TONG_QUAN_UI.md`; không sửa tài liệu ngoài phạm vi. Phần Backend/Frontend đã pass.
- `package-lock.json` bị ignore theo `.gitignore` dự án. Đã chạy `npm install --ignore-scripts` để đồng bộ dependency/lockfile local và cài Playwright; không force-add lockfile.

## Toàn bộ file trong kết quả tích hợp

Danh sách dưới gồm cả code nhập từ remote, các file giải quyết conflict và tính năng mới; không phải chỉ các file viết mới trong phiên này.

- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/admin/controller/AdminOperationsController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/admin/dto/response/AdminDisputeDto.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/admin/dto/response/AdminStatsResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/admin/service/implement/AdminDashboardServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/DriverTripController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/ProviderBookingController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/RenterBookingController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/AssignDriverRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/OpenDisputeRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/ReportDriverIssueRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/request/ResolveOperationalRiskRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingChargeLineResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingDetailResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/BookingSummaryResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/ConfirmEndResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/ConfirmStartResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/CreateBookingResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/OperationalResolutionResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/OperationalRiskResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/ProviderBookingResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/ProviderBookingSummaryResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/dto/response/TripIncidentResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/entity/Booking.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/entity/BookingPolicySnapshot.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/entity/PaymentTransaction.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/BookingActionKind.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/BookingStatus.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/CancelReason.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/DriverAssignmentMode.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/enums/OperationalResolutionType.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/job/BookingHoldExpiryJob.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/mapper/BookingMapper.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/repository/BookingRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/repository/PaymentTransactionRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingHoldExpiryService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingPaymentLedgerService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingStateMachine.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingTrackingService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/DriverAvailabilityService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/DriverTripService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/ProviderBookingService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/VehicleOperationalRiskService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingHoldExpiryServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingPaymentLedgerServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingTrackingServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/DriverAvailabilityServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/DriverOptimizationServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/DriverTripServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/ProviderBookingServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/RenterPaymentServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/VehicleOperationalRiskServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/cloudinary/CloudinaryService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/cloudinary/CloudinaryServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/storage/StorageObjectPath.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/storage/controller/FileDownloadController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/dispute/dto/response/DisputeOpenedResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/dispute/entity/Dispute.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/dispute/service/DisputeService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/dispute/service/implement/DisputeServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/RefundController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/PaymentRequestResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/ProviderTransactionResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/RefundProofResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/entity/PaymentRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/repository/PaymentRequestRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/repository/RefundRequestRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/PaymentRequestService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/RefundService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentRequestServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/ProviderFinanceServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/RefundServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VnpayServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/dto/response/OccupiedScheduleWindow.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/entity/VehicleSchedule.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/enums/ScheduleStatus.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/repository/VehicleScheduleRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/service/ScheduleService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/service/implement/ProviderScheduleServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/schedule/service/implement/ScheduleServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/seed/DevDataSeeder.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/controller/ProviderDriverController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/repository/DriverRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/DriverAssignmentService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/ProviderDriverService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/UserService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/DriverAssignmentServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/ProviderDriverServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/service/implement/UserServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/controller/ProviderVehicleController.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/request/CreateVehicleRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/request/UpdateRentalPlansRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/request/UpdateVehicleDepositPolicyRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/request/UpdateVehicleRequest.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/response/BookableVehicleResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/response/VehicleDepositPolicyResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/dto/response/VehicleLicensePlateHistoryResponse.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/entity/Vehicle.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/entity/VehicleLicensePlateHistory.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/mapper/VehicleCatalogMapper.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/repository/VehicleLicensePlateHistoryRepository.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/LicensePlateHistoryService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/LicensePlatePolicy.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/ProviderVehicleService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/VehicleCatalogService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/VehicleDepositPolicyService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/VehicleService.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/LicensePlateHistoryServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/ProviderVehicleServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/VehicleCatalogServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/VehicleDepositPolicyServiceImpl.java`
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/vehicle/service/implement/VehicleServiceImpl.java`
- `Backend/EcoMotion/src/main/resources/db/migrations/20261023_r07_r10_vietqr_stabilization_v31.sql`
- `Backend/EcoMotion/src/main/resources/db/provider_finance.sql`
- `Backend/EcoMotion/src/main/resources/db/schema.sql`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/DriverTripApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/DriverTripLifecycleIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/ProviderBookingApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingDetailApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterBookingListApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/RenterCheckoutApiIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/controller/VietQrHappyPathIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/service/DriverAssignmentEligibilityTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/service/DriverAvailabilityServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/booking/service/VehicleOperationalRiskServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestLifecycleIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/RefundProofIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/service/implement/ProviderFinanceServiceUnitTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/schedule/service/ProviderScheduleServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/controller/ProviderDriverUpdatedTimeIntegrationTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/user/service/ProviderDriverUpdatedTimeTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/vehicle/controller/ProviderVehicleApiTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/vehicle/service/LicensePlateHistoryServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/vehicle/service/ProviderVehicleServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/vehicle/service/VehicleDepositPolicyServiceTest.java`
- `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/vehicle/service/VehicleServiceBoundaryTest.java`
- `Frontend/EcoMotion/.gitignore`
- `Frontend/EcoMotion/README.md`
- `Frontend/EcoMotion/e2e/my-bookings-action.spec.js`
- `Frontend/EcoMotion/e2e/provider-driver-updated-time.spec.js`
- `Frontend/EcoMotion/e2e/r07-r10-operational-risk.spec.js`
- `Frontend/EcoMotion/package.json`
- `Frontend/EcoMotion/playwright.config.js`
- `Frontend/EcoMotion/src/App.jsx`
- `Frontend/EcoMotion/src/components/AppHeader.jsx`
- `Frontend/EcoMotion/src/components/booking/AcceptBookingModal.jsx`
- `Frontend/EcoMotion/src/components/booking/AssignDriverModal.jsx`
- `Frontend/EcoMotion/src/components/booking/BookingDetail.jsx`
- `Frontend/EcoMotion/src/components/booking/BookingStepper.jsx`
- `Frontend/EcoMotion/src/components/booking/BookingSubnav.jsx`
- `Frontend/EcoMotion/src/components/booking/BookingWizardShell.jsx`
- `Frontend/EcoMotion/src/components/booking/CancelBookingModal.jsx`
- `Frontend/EcoMotion/src/components/booking/ChoosePlanStep.jsx`
- `Frontend/EcoMotion/src/components/booking/ConfirmRequestStep.jsx`
- `Frontend/EcoMotion/src/components/booking/MyBookingsList.jsx`
- `Frontend/EcoMotion/src/components/booking/PickupMap.jsx`
- `Frontend/EcoMotion/src/components/booking/PickupStep.jsx`
- `Frontend/EcoMotion/src/components/booking/RejectBookingModal.jsx`
- `Frontend/EcoMotion/src/components/booking/RequestSubmitted.jsx`
- `Frontend/EcoMotion/src/components/icons/Icons.jsx`
- `Frontend/EcoMotion/src/components/map/LiveMapViewer.jsx`
- `Frontend/EcoMotion/src/components/payments/HoldCountdown.jsx`
- `Frontend/EcoMotion/src/components/payments/ReceiptDropzone.jsx`
- `Frontend/EcoMotion/src/components/provider/BookingStatusBadge.jsx`
- `Frontend/EcoMotion/src/components/provider/ProviderHeader.jsx`
- `Frontend/EcoMotion/src/components/provider/modals/AssignDriverModal.jsx`
- `Frontend/EcoMotion/src/components/vehicles/FilterSidebar.jsx`
- `Frontend/EcoMotion/src/components/vehicles/VehicleCard.jsx`
- `Frontend/EcoMotion/src/components/vehicles/VehicleMap.jsx`
- `Frontend/EcoMotion/src/hooks/useDriverLocationTracker.js`
- `Frontend/EcoMotion/src/hooks/useDriverSchedule.js`
- `Frontend/EcoMotion/src/hooks/useDriverTripDetail.js`
- `Frontend/EcoMotion/src/hooks/useProviderBookingDetail.js`
- `Frontend/EcoMotion/src/hooks/useProviderBookings.js`
- `Frontend/EcoMotion/src/hooks/useProviderTripTracking.js`
- `Frontend/EcoMotion/src/hooks/useRenterTripTracking.js`
- `Frontend/EcoMotion/src/index.css`
- `Frontend/EcoMotion/src/pages/ActivatePage.jsx`
- `Frontend/EcoMotion/src/pages/AdminDashboard.jsx`
- `Frontend/EcoMotion/src/pages/CustomerPaymentPage.jsx`
- `Frontend/EcoMotion/src/pages/DriverSchedulePage.jsx`
- `Frontend/EcoMotion/src/pages/MePage.jsx`
- `Frontend/EcoMotion/src/pages/PaymentHistoryPage.jsx`
- `Frontend/EcoMotion/src/pages/PolicyPage.jsx`
- `Frontend/EcoMotion/src/pages/ProviderFinancePage.jsx`
- `Frontend/EcoMotion/src/pages/VehicleDetailPage.jsx`
- `Frontend/EcoMotion/src/pages/VehicleListPage.jsx`
- `Frontend/EcoMotion/src/pages/VnpayCallbackPage.jsx`
- `Frontend/EcoMotion/src/pages/YearlyRentalPage.jsx`
- `Frontend/EcoMotion/src/pages/admin/AdminOperationsTab.jsx`
- `Frontend/EcoMotion/src/pages/admin/AdminOverviewTab.jsx`
- `Frontend/EcoMotion/src/pages/admin/AdminUsersTab.jsx`
- `Frontend/EcoMotion/src/pages/admin/AdminVehiclesTab.jsx`
- `Frontend/EcoMotion/src/pages/admin/modals/DisputeDetailModal.jsx`
- `Frontend/EcoMotion/src/pages/booking/BookingDetailPage.jsx`
- `Frontend/EcoMotion/src/pages/booking/BookingRequestPage.jsx`
- `Frontend/EcoMotion/src/pages/booking/MyBookingsPage.jsx`
- `Frontend/EcoMotion/src/pages/booking/SubmittedBookingPage.jsx`
- `Frontend/EcoMotion/src/pages/provider/ProviderDriverPage.jsx`
- `Frontend/EcoMotion/src/pages/provider/ProviderSchedulePage.jsx`
- `Frontend/EcoMotion/src/pages/provider/ProviderVehicleCreatePage.jsx`
- `Frontend/EcoMotion/src/pages/provider/ProviderVehicleHubPage.jsx`
- `Frontend/EcoMotion/src/pages/provider/ProviderVehicleListPage.jsx`
- `Frontend/EcoMotion/src/services/bookingService.js`
- `Frontend/EcoMotion/src/services/driverTripService.js`
- `Frontend/EcoMotion/src/services/providerBookingService.js`
- `Frontend/EcoMotion/src/services/providerFinanceService.js`
- `Frontend/EcoMotion/src/services/providerService.js`
- `Frontend/EcoMotion/src/utils/bookingAvailability.js`
- `Frontend/EcoMotion/src/utils/bookingCopy.js`
- `Frontend/EcoMotion/src/utils/bookingDateTime.js`
- `Frontend/EcoMotion/src/utils/bookingStatus.js`
- `Frontend/EcoMotion/src/utils/driverTripUtils.js`
- `Frontend/EcoMotion/src/utils/paymentDisplay.js`
- `Frontend/EcoMotion/src/utils/planLabels.js`
- `Frontend/EcoMotion/test/bookingFlowUx.test.js`
- `Frontend/EcoMotion/test/bookingRequest.test.js`
- `Frontend/EcoMotion/test/bookingStatus.test.js`
- `Frontend/EcoMotion/test/driverSchedule.test.js`
- `docs/BAO_CAO_TONG_QUAN_UI.md`
- `docs/DRIVER_LIST_UPDATED_TIME_REPORT.md`
