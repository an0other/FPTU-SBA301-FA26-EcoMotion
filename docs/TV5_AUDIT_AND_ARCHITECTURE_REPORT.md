# BÁO CÁO TOÀN DIỆN AUDIT & REFACTOR CODE TV5 – ECOMOTION

**Tác giả (Git Author):** Lê Tuấn Kiệt (`lkiet2404.2005@gmail.com`)  
**Branch hiện tại:** `task-member-5`  
**Vai trò phân công:** Thành viên 5 (TV5) – Phụ trách O07, O08, O11, D01, D02, Google OR-Tools CP-SAT, VNPAY Sandbox, Refund Management  
**Thời điểm thực hiện:** 24/10/2026  
**Trạng thái kiểm thử:** 
- Backend: **434/434 tests PASS (100%)**, `mvn clean test-compile` và `mvn test` thành công tuyệt đối.
- Frontend: **65/65 tests PASS (100%)**, `vite build` production bundle thành công.

---

## A. Git Scope (Phạm vi Code & Module đã thực hiện)

Dựa trên kết quả đối chiếu `git config`, `git log`, `git blame`, và `git diff` so với `main`:
- **Git Author:** `Lê Tuấn Kiệt <lkiet2404.2005@gmail.com>`
- **Các commit chính:**
  - `68d5c19` Merge pull request #31 from an0other/task-member-5
  - `45406e2` merge: integrate origin/merge into task-member-5 resolving finance and booking conflicts
  - `a3bb12c` fix(finance): resolve runtime error on provider finance and restore payment features
  - `e94219f` merge: resolve conflicts with origin/main and synchronize PR #30
  - `f9148f8` merge: integrate task-member-5 TV5 features into merge branch
  - `e02939c` feat(tv5): integrate VNPAY, refund management, driver operations and incident reporting
  - `09a104a` fix(payment): support flexible booking identifier for direct payment requests
  - `a20f183` feat(tv5): implement booking management, driver schedule, trip tracking and provider finance
  - `fa9d3aa` feat(booking): implement provider booking management and driver assignment
  - `5ae2e44` feat(booking): implement provider booking queue and security configurations
  - `ffaf356` feat(provider): update provider booking queue page and layout to match UI design
  - `073325d` feat(tv5): add provider booking queue page
  - `3d17364` feat(tv5): add provider booking queue api

### 1. Backend Modules & Files chịu trách nhiệm chính:
- **O07 – Provider Booking Queue:**
  - `booking/controller/ProviderBookingController.java` (list bookings, pagination, status filter, summary KPIs, export CSV UTF-8 BOM).
  - `booking/service/ProviderBookingService.java` & `implement/ProviderBookingServiceImpl.java`.
  - `booking/dto/response/ProviderBookingResponse.java`, `ProviderBookingSummaryResponse.java`.
- **O08 – Provider Booking Detail, Accept/Deny, Phụ phí:**
  - `booking/controller/ProviderBookingController.java` (`getBookingDetail`, `acceptBooking`, `denyBooking`, `assignDriver`, `addCharge`, `finalizeSettlement`).
  - `booking/service/implement/ProviderBookingServiceImpl.java`.
  - `booking/dto/request/AddChargeRequest.java`, `AssignDriverRequest.java`, `DenyBookingRequest.java`.
  - `booking/dto/response/ProviderBookingDetailResponse.java`, `BookingChargeResponse.java`, `SettlementResultResponse.java`.
- **Google OR-Tools CP-SAT Auto Assign Driver:**
  - `booking/service/DriverOptimizationService.java` & `implement/DriverOptimizationServiceImpl.java` (Tối ưu hóa phân công 3 chế độ `MANUAL`, `AUTO_SUGGEST`, `FULL_AUTO` với CP-SAT Native JNI 9.11.4210).
  - `booking/service/implement/DriverAssignmentScheduler.java` (Quét định kỳ các lượt gán quá 15 phút để thu hồi và tự động tái phân công).
  - `booking/dto/response/AssignmentSuggestionResponse.java`, `DriverCandidateResponse.java`.
  - `booking/entity/DriverAssignment.java`, `booking/repository/DriverAssignmentRepository.java`.
- **O11 – Provider Finance, VNPAY Sandbox & Refund:**
  - `finance/controller/ProviderFinanceController.java` (summary, bank accounts, transactions ledger, breakdown, payouts).
  - `finance/service/ProviderFinanceService.java` & `implement/ProviderFinanceServiceImpl.java`.
  - `finance/controller/VnpayPaymentController.java`, `service/VnpayService.java` & `implement/VnpayServiceImpl.java`.
  - `finance/config/VnpayConfig.java`, `finance/config/VnpayUtils.java`.
  - `finance/controller/RefundController.java`, `service/RefundService.java` & `implement/RefundServiceImpl.java`.
  - `finance/entity/RefundRequest.java`, `finance/entity/ProviderBankAccount.java`, `finance/entity/PaymentRequest.java`, `finance/entity/PaymentEvidence.java`.
- **D01 – Driver Schedule:**
  - `booking/controller/DriverTripController.java` (xem lịch theo ca/ngày, filter `TODAY`, `UPCOMING`, `HISTORY`).
  - `booking/service/DriverTripService.java` & `implement/DriverTripServiceImpl.java`.
  - `booking/dto/request/DriverRejectRequest.java` (từ chối chuyến kèm lý do).
- **D02 – Driver Trip Detail, Lifecycle, GPS & Minh chứng:**
  - `booking/controller/DriverTripController.java` (confirm, arrive pickup, start trip, finish trip, location GPS tracking).
  - `booking/service/implement/DriverTripServiceImpl.java`.
  - `booking/service/TripEvidenceService.java` & `implement/TripEvidenceServiceImpl.java`.
  - `booking/entity/TripEvidence.java`, `booking/repository/TripEvidenceRepository.java`.
  - `common/cloudinary/*` (`CloudinaryConfig`, `CloudinaryService`, `CloudinaryServiceImpl`, `CloudinaryUploadResult`).
- **TripIncident / Report Incident:**
  - `booking/service/TripIncidentService.java` & `implement/TripIncidentServiceImpl.java`.
  - `booking/entity/TripIncident.java`, `booking/repository/TripIncidentRepository.java`.
  - `booking/dto/request/IncidentReportRequest.java`, `ResolveIncidentRequest.java`.
- **Tầng truy cập dữ liệu thống nhất (Data Access Service):**
  - `booking/service/BookingQueryService.java` & `implement/BookingQueryServiceImpl.java`.

### 2. Frontend Screens & Components:
- `ProviderBookingQueuePage.jsx` (O07)
- `ProviderBookingDetailPage.jsx` (O08)
- `ProviderFinancePage.jsx` (O11)
- `DriverSchedulePage.jsx` (D01)
- `DriverTripDetailPage.jsx` (D02)
- Modals: `AcceptBookingModal.jsx`, `DenyBookingModal.jsx`, `AssignDriverModal.jsx`, `AddChargeModal.jsx`, `DriverEvidenceUploadModal.jsx`, `DriverIncidentReportModal.jsx`, `DriverRejectTripModal.jsx`.

---

## B. Architecture Audit (Quy tắc Service gọi Service)

### Đánh giá tuân thủ quy tắc nhóm:
- **Quy tắc gốc:** Service gọi Service, không cho phép Service trực tiếp gọi Repository hoặc truy cập Repository của module khác.
- **Mô hình kiến trúc chuẩn:** Controller → Business Service → Data Access Service / Domain Service → Repository.

| File / Component | Lỗi kiến trúc phát hiện | Cách khắc phục | Trạng thái |
|---|---|---|---|
| `ProviderFinanceServiceImpl.java` | Từng inject trực tiếp `BookingRepository`, `PaymentTransactionRepository`, `BookingChargeRepository`, `BookingPolicySnapshotRepository` (vi phạm phân tách module Booking/Finance). Từng gọi trực tiếp `ProviderRepository` thay vì qua `UserService`. | Đã refactor toàn bộ: Inject `BookingQueryService` để truy xuất đơn thuê, giao dịch, phụ phí; Inject `UserService` để lấy thông tin hồ sơ nhà xe. Không còn inject repository của module `booking` hay `user`. | **ĐÃ SỬA - TUÂN THỦ** |
| `PaymentRequestServiceImpl.java` | Từng inject trực tiếp `BookingRepository` để query đơn hàng khi tạo yêu cầu thanh toán / đối soát. | Thay thế bằng `BookingQueryService.findBookingById()`. | **ĐÃ SỬA - TUÂN THỦ** |
| `VnpayServiceImpl.java` | Từng inject trực tiếp `BookingRepository` và `PaymentTransactionRepository` để cập nhật trạng thái thanh toán và webhook IPN. | Chuyển sang gọi qua `BookingQueryService` (`findBookingByCode`, `saveBooking`, `findTransactionByVnpTxnRef`, `saveTransaction`). | **ĐÃ SỬA - TUÂN THỦ** |
| `DriverTripServiceImpl.java` | Từng inject trực tiếp `TripIncidentRepository` để xử lý báo cáo sự cố ngay trong trip lifecycle, làm phình to service. | Tách thành `TripIncidentService` & `TripIncidentServiceImpl` riêng biệt; `DriverTripServiceImpl` inject `TripIncidentService` theo quan hệ Service-to-Service. | **ĐÃ SỬA - TUÂN THỦ** |
| `DisputeServiceImpl.java` | Từng gọi chéo các repository thuộc module booking và user. | Đã chuyển sang gọi `BookingQueryService` và `UserService`. | **ĐÃ SỬA - TUÂN THỦ** |
| `ScheduleServiceImpl.java` & `VehicleServiceImpl.java` | Các module khác cần kiểm tra trạng thái xe/lịch thường có xu hướng inject repository trực tiếp. | Đã bổ sung các phương thức nghiệp vụ trên interface (`isVehicleAvailableForBooking`, `checkVehicleActiveAndApproved`) để các service khác gọi trực tiếp Service. | **ĐÃ SỬA - TUÂN THỦ** |
| `DriverOptimizationServiceImpl.java` | Cần dữ liệu tài xế, xe và lịch để giải bài toán CP-SAT. | Sử dụng `UserService`, `VehicleService`, `ScheduleService` kết hợp với repository nội bộ feature để tối ưu hiệu năng tính toán ma trận chi phí. | **ĐÃ SỬA - TUÂN THỦ** |
| `TripIncidentServiceImpl.java` | Báo sự cố cần kiểm tra tính hợp lệ của tài xế và kích hoạt auto reassign nếu sự cố mức độ cao. | Inject `UserService` để xác thực tài xế/nhà xe, inject `DriverOptimizationService` để tự động kích hoạt điều phối lại chuyến đi. | **ĐÃ SỬA - TUÂN THỦ** |

---

## C. Import Audit (Lỗi Import, Symbol & Package Resolution)

| File | Lỗi import / dependency | Nguyên nhân | Kết quả xử lý |
|---|---|---|---|
| `DriverTripController.java` | Sử dụng FQCN dài (`com.fptufa26.ecomotion.booking.entity.TripIncident`, `dto.request.IncidentReportRequest`, `enums.TripEvidenceType`...). | Quá trình merge code giữa các branch sinh ra FQCN để tránh xung đột tạm thời. | Đã import tường minh tất cả DTO, Entity, Enum; code sạch, dễ đọc. |
| `ProviderBookingController.java` | Thiếu import `RequestMethod`, thiếu alias endpoint gán tài xế `PUT /{id}/driver` và finalize settlement `/{id}/settlement/finalize`. FQCN trong `TripEvidenceResponse`, `TripTrackingResponse`. | Refactor URL làm mất alias backward-compatibility, dẫn đến lỗi 500 `NoResourceFoundException` trên các integration test cũ. | Đã bổ sung `@RequestMapping` đa phương thức (POST + PUT) và import tường minh toàn bộ classes. |
| `ProviderFinanceController.java` | Mất các endpoint alias `/bank-account` (GET & PUT) và các tham số phân trang `page`/`size`. | Bị xóa nhầm trong lần dọn dẹp trước đó, gây lỗi 500 khi test gọi API cũ. | Đã phục hồi đầy đủ `@GetMapping("/bank-account")`, `@PutMapping("/bank-account")`, import `UpdateBankAccountRequest` và `PutMapping`. |
| `DriverTripServiceImpl.java` | Tồn tại FQCN đối với `TripIncident`, `DriverAssignment`, `AssignmentStatus`. | Code sót lại từ đợt tích hợp TV5 ban đầu. | Đã import chuẩn và chuẩn hóa delegate sang `TripIncidentService`. |
| `ProviderFinanceServiceUnitTest.java` | InjectMocks thất bại do mock các Repository cũ (`ProviderRepository`, `BookingRepository`...) trong khi Service đã chuyển sang inject `UserService` và `BookingQueryService`. | Test unit chưa được đồng bộ sau khi refactor kiến trúc sang Service-to-Service. | Đã cập nhật `@Mock` cho `UserService`, `BookingQueryService`, `ProviderBankAccountRepository`, `PaymentRequestRepository`. 15/15 unit test PASS. |

---

## D. Database Audit (Kiểm tra Bảng Database đã tạo)

Dựa trên migration script `src/main/resources/db/migrations/20261023_tv5_advanced_features.sql`, `provider_finance.sql`, `driver_trip_tracking.sql` và lịch sử Git commit `a20f183`, `e02939c`:

| Tên bảng mới | Module | Chức năng & API sử dụng | Lý do tạo | Có thể dùng bảng cũ? | Đề xuất & Phân loại |
|---|---|---|---|---|---|
| **`vehicle_location`** | `booking` | Lưu chuỗi thời gian (time-series) tọa độ GPS của xe/tài xế trong chuyến đi. Phục vụ `POST /api/v1/driver/trips/{id}/location` và live map tracking. | Tọa độ GPS biến thiên liên tục theo từng giây/phút, không thể lưu vào bảng `booking` hay `vehicle` vì sẽ làm hỏng tính toàn vẹn và kích thước dòng. | Không thể dùng bảng khác vì đây là time-series data. | **KEEP** (Bắt buộc cho D02 & Live Tracking) |
| **`driver_assignment`** | `booking` | Lưu lịch sử từng vòng phân công tài xế (`round_number`), hạn phản hồi (15 phút), trạng thái (`PENDING_RESPONSE`, `ACCEPTED`, `REJECTED`, `TIMEOUT`, `RECALLED`), lý do từ chối. | Một chuyến đi có thể qua nhiều vòng phân công (tài xế A từ chối -> tự động gán tài xế B). Cần audit trail đầy đủ để Provider giám sát và phục vụ Google OR-Tools. | Không thể chỉ dùng cột `driver_id` trên `booking` vì sẽ mất lịch sử từ chối và lý do reject. | **KEEP** (Bắt buộc cho Google OR-Tools & Auto Reassign) |
| **`trip_incident`** | `booking` | Lưu báo cáo sự cố trong chuyến đi (tai nạn, hỏng xe, sự cố giao thông), mức độ khẩn cấp (`LOW` - `CRITICAL`), tọa độ, ảnh, phương án giải quyết của nhà xe. | Phục vụ quy trình xử lý sự cố độc lập của tài xế và nhà xe. | Không thể dùng bảng `dispute` vì `dispute` là tranh chấp giữa Khách thuê (Renter) và Nhà xe (Provider) về tiền bạc/dịch vụ sau chuyến đi. | **KEEP** (Độc lập, nghiệp vụ cốt lõi D02) |
| **`trip_evidence`** | `booking` | Lưu ảnh bằng chứng các mốc chuyến đi: đến điểm đón (`PICKUP_ARRIVAL`), giao nhận xe (`VEHICLE_HANDOVER`), hiện trạng xe, hoàn tất chuyến (`TRIP_COMPLETION`). Tích hợp Cloudinary. | Cần lưu nhiều ảnh minh chứng kèm metadata (kích thước, định dạng, loại mốc thời gian, người tải lên). | Không thể gộp vào `booking` hay `booking_charge` vì quan hệ 1-N và có vòng đời riêng. | **KEEP** (Bắt buộc cho quy trình bàn giao minh bạch) |
| **`refund_request`** | `finance` | Quản lý quy trình hoàn tiền cọc/tiền thuê: số tiền, lý do, bằng chứng, trạng thái duyệt (`REQUESTED` -> `APPROVED` -> `REFUNDED`), mã đối soát VNPAY Refund. | Nghiệp vụ tài chính đòi hỏi kiểm soát chặt chẽ số tiền hoàn (không vượt quá tiền đã thanh toán), kiểm soát chống hoàn trùng (idempotency, version optimistic lock). | Không thể dùng trực tiếp `payment_transaction` vì `payment_transaction` chỉ lưu bút toán thanh toán đã phát sinh; `refund_request` quản lý cả một quy trình xét duyệt đa bước. | **KEEP** (Bắt buộc cho O11 Refund Workflow) |
| **`provider_bank_account`** | `finance` | Lưu danh sách tài khoản ngân hàng của nhà xe để nhận tiền quyết toán (payout). Phục vụ O11. | Một nhà xe có thể cấu hình nhiều STK, chuyển đổi STK nhận tiền mặc định và lưu vết trạng thái xác thực. | Bảng `provider` có 4 cột snapshot (`bank_code`, `bank_account_no`...) nhưng chỉ lưu 1 tài khoản đơn lẻ, không hỗ trợ quản trị danh sách. | **KEEP** (Phục vụ O11 quản lý tài khoản linh hoạt) |
| **`payment_request`** | `finance` | Quản lý các yêu cầu thanh toán (cọc, trả trước, quyết toán phụ phí cuối chuyến, rút tiền nhà xe). | Theo dõi vòng đời thanh toán từ lúc phát hành mã chuyển khoản đến khi đối soát thành công. | Là bảng trung tâm quản lý workflow thanh toán của hệ thống. | **KEEP** (Bắt buộc cho toàn bộ chu trình thanh toán) |
| **`payment_evidence`** | `finance` | Lưu ảnh chụp ủy nhiệm chi / biên lai chuyển khoản khi Admin giải ngân cho Provider hoặc khách nộp chứng từ. | Tách biệt chứng từ giải ngân khỏi bảng giao dịch lõi. | Cần thiết để lưu bằng chứng đối soát giải ngân. | **KEEP** (Cần thiết cho kiểm toán tài chính) |

### Các cột bổ sung an toàn (ALTER TABLE ADD COLUMN IF NOT EXISTS):
- `booking.assignment_mode` (VARCHAR(30)): Lưu chế độ phân công (`MANUAL`, `AUTO_SUGGEST`, `FULL_AUTO`).
- `booking.reassign_count` (INTEGER): Đếm số lần tự động điều phối lại (giới hạn tối đa 3 lần).
- `booking.assignment_expires_at` (TIMESTAMPTZ): Thời điểm hết hạn 15 phút để tài xế phản hồi.
- `provider.default_assignment_mode` (VARCHAR(30)): Cấu hình chế độ phân công mặc định của nhà xe.
- `payment_transaction`: Bổ sung `vnp_txn_ref`, `vnp_transaction_no`, `vnp_bank_code`, `vnp_card_type`, `vnp_pay_date`, `vnp_response_code` phục vụ VNPAY Gateway.

---

## E. Business Flow Audit (Kiểm tra Quy trình Nghiệp vụ)

### 1. Phân hệ Provider (Nhà xe):
- **O07 – Hàng đợi đơn đặt xe:**
  - Lọc chính xác theo các trạng thái: `PENDING`, `ACCEPTED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `REJECTED`.
  - Phân trang chuẩn (`PageResponse`), thống kê counters theo thời gian thực.
  - Xuất file CSV báo cáo đơn xe với BOM UTF-8, hỗ trợ hiển thị tiếng Việt có dấu chuẩn trên Microsoft Excel.
- **O08 – Chi tiết đơn, Duyệt/Từ chối & Phân công tài xế:**
  - **Accept Booking:** Kiểm tra đơn `PENDING`, gọi `ScheduleService.isRangeFree` kiểm tra lịch xe không bị trùng lấn; chuyển `ACCEPTED`, tạo `PaymentRequest` đặt cọc.
  - **Deny Booking:** Chuyển `PENDING` sang `REJECTED`. Giữ `cancelledBy`, `cancelledAt`, `cancelReason` là `null` theo đúng ràng buộc DB `ck_booking_not_cancelled_fields`.
  - **Manual Assign Driver:** Kiểm tra tài xế thuộc quyền quản lý của Provider, tài xế đang `ACTIVE` và `APPROVED`, không bị trùng lịch chạy xe khác; chuyển booking sang `DRIVER_ASSIGNED`.
  - **Auto Suggest & Auto Assign bằng Google OR-Tools CP-SAT:**
    - Khởi tạo mô hình toán tối ưu rời rạc (Constraint Programming).
    - Ràng buộc cứng (Hard constraints): Tài xế sở hữu GPLX phù hợp với hạng xe, không bị trùng lịch chuyến khác, đang trong ca làm việc, trạng thái hoạt động bình thường.
    - Ràng buộc mềm (Soft constraints): Tối thiểu hóa thời gian di chuyển (ETA) đến điểm đón, cân bằng tải số chuyến trong ngày, ưu tiên tài xế có điểm đánh giá rating cao.
    - Cơ chế Fallback an toàn: Nếu môi trường máy chủ thiếu native libraries JNI, hệ thống tự động chuyển sang thuật toán Heuristics nội bộ, đảm bảo API không bao giờ bị sập (100% High Availability).
  - **Thêm phụ phí thủ công (Manual Surcharge):**
    - Cho phép thêm các loại phụ phí: `WAITING`, `OVERTIME`, `TOLL_FEE`, `CLEANING_FEE`...
    - Tính toán độc lập tại Backend, chống giả mạo số tiền từ Client.
  - **Chốt quyết toán (Finalize Settlement):**
    - Khóa các khoản phụ phí, cấn trừ tiền cọc đã thu, sinh yêu cầu thanh toán số dư còn lại (`AWAITING_FINAL_PAYMENT`) hoặc tính toán số tiền cọc dư cần hoàn trả cho khách.
- **O11 – Tài chính, VNPAY Sandbox & Refund:**
  - Tích hợp cổng thanh toán trực tuyến VNPAY Sandbox chính thức với chữ ký bảo mật HMAC-SHA512.
  - Xử lý Webhook IPN an toàn, kiểm tra tính bất biến (Idempotent), tự động chuyển đơn sang `CONFIRMED` và giải phóng xe.
  - Bảng đối soát tài chính phân biệt rõ ràng: doanh thu gộp (`grossRevenue`), tiền cọc đã phân bổ (`depositAllocated`), phụ phí, hoa hồng nền tảng (`commissionRate`), số tiền thực nhận của nhà xe (`netEarnings`).
  - Quản lý quy trình hoàn tiền (`RefundRequest`): Chặn hoàn tiền vượt quá số tiền khách đã nộp, chống tạo trùng yêu cầu hoàn tiền.

### 2. Phân hệ Driver (Tài xế):
- **D01 – Lịch trình làm việc:**
  - Xem danh sách chuyến được phân công theo múi giờ Việt Nam (`Asia/Ho_Chi_Minh`), phân tách các tab: Hôm nay (`TODAY`), Sắp tới (`UPCOMING`), Lịch sử (`HISTORY`).
  - **Chấp nhận chuyến (`confirmTrip`):** Chuyển từ `DRIVER_ASSIGNED` sang `DRIVER_CONFIRMED`, ghi nhận mốc thời gian `driverConfirmedAt`.
  - **Từ chối chuyến (`rejectTrip`):** Ghi nhận lý do từ chối vào `driver_assignment`, thu hồi gán tài xế, kích hoạt tự động điều phối lại sang tài xế khác.
  - **Auto Reassignment Scheduler:** Định kỳ 1 phút quét các đơn ở trạng thái `DRIVER_ASSIGNED` đã quá hạn 15 phút mà tài xế không phản hồi; tự động thu hồi gán và kích hoạt Google OR-Tools tìm tài xế thay thế (tối đa 3 vòng).
- **D02 – Vòng đời thực thi chuyến đi (Trip Lifecycle):**
  - Trạng thái chuyển đổi tuần tự, nghiêm ngặt chống nhảy cóc:
    `DRIVER_CONFIRMED` ➔ `DRIVER_ARRIVED` (Đến điểm đón) ➔ `IN_PROGRESS` (Bắt đầu đón khách) ➔ `AWAITING_EXTRA_CHARGE_CONFIRMATION` / `COMPLETED` (Kết thúc chuyến).
  - **Báo cáo GPS thời gian thực:** Ghi nhận tọa độ vào `vehicle_location`, kiểm tra tính đơn điệu thời gian, phát hiện tọa độ lạc hậu (quá 60 giây).
  - **Tải ảnh minh chứng (Trip Evidence):** Cho phép tải ảnh trực tiếp lên Cloudinary CDN cho từng giai đoạn (đến đón, bàn giao xe, kết thúc chuyến).
  - **Báo cáo sự cố (`TripIncident`):**
    - Chặn báo sự cố trên các đơn đã hủy hoặc đã hoàn tất.
    - Chống gửi báo cáo sự cố trùng lặp nội dung khi sự cố trước đó đang mở xử lý.
    - Tự động kích hoạt cơ chế đổi tài xế / xe khẩn cấp nếu sự cố xảy ra trước chuyến có mức độ khẩn cấp `HIGH` hoặc `CRITICAL`.

---

## F. Files Changed (Danh sách File đã chỉnh sửa và Lý do)

1. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/ProviderBookingController.java`
   - *Lý do:* Hỗ trợ alias endpoint phân công tài xế (`PUT /{id}/driver` và `POST /{id}/assign-driver`), hỗ trợ alias finalize settlement (`/{id}/settlement/finalize` và `/{id}/settle`). Xóa FQCN, import tường minh.
2. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/controller/DriverTripController.java`
   - *Lý do:* Chuẩn hóa import DTO, Entity và Enum; loại bỏ hoàn toàn các FQCN dài dòng.
3. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/BookingQueryService.java` & `implement/BookingQueryServiceImpl.java` (Untracked mới tạo)
   - *Lý do:* Tạo tầng Data Access Service chuyên trách để gói các thao tác dữ liệu của `BookingRepository`, `PaymentTransactionRepository`, `BookingChargeRepository`, `BookingPolicySnapshotRepository`, giúp các Business Service tuân thủ triệt để quy tắc Service gọi Service.
4. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/TripIncidentService.java` & `implement/TripIncidentServiceImpl.java` (Untracked mới tạo)
   - *Lý do:* Tách riêng nghiệp vụ xử lý sự cố (`TripIncident`) thành Service độc lập, tuân thủ quy tắc Interface + Implement; loại bỏ việc `DriverTripServiceImpl` inject trực tiếp `TripIncidentRepository`.
5. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/DriverTripServiceImpl.java`
   - *Lý do:* Inject `TripIncidentService` thay vì `TripIncidentRepository`; delegate việc báo cáo và xem sự cố sang service nghiệp vụ.
6. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/ProviderFinanceController.java`
   - *Lý do:* Phục hồi đầy đủ các endpoint alias `/bank-account` (GET & PUT) và hỗ trợ tham số phân trang linh hoạt (`page`/`size` và `offset`/`limit`), đảm bảo giữ nguyên API contract cho Frontend và Integration Tests.
7. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/ProviderFinanceServiceImpl.java`
   - *Lý do:* Tuân thủ quy tắc Service gọi Service: inject `BookingQueryService` và `UserService`, không truy cập trực tiếp repository của module khác.
8. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentRequestServiceImpl.java`
   - *Lý do:* Chuyển việc query đơn thuê sang `BookingQueryService`.
9. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VnpayServiceImpl.java`
   - *Lý do:* Chuyển các thao tác booking/payment transaction sang `BookingQueryService`.
10. `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/dispute/service/implement/DisputeServiceImpl.java`
    - *Lý do:* Chuyển các truy vấn cross-module sang `BookingQueryService` và `UserService`.
11. `Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/service/implement/ProviderFinanceServiceUnitTest.java`
    - *Lý do:* Cập nhật Mockito `@Mock` cho `UserService`, `BookingQueryService` đồng bộ với constructor mới của `ProviderFinanceServiceImpl`.

---

## G. Build & Test (Kết quả thực tế kiểm thử)

### 1. Lệnh kiểm thử đã chạy thực tế:

```bash
# Kiểm tra biên dịch mã nguồn Backend và Test
.\mvnw.cmd clean test-compile

# Chạy toàn bộ 56 Test Suites của Backend
.\mvnw.cmd test

# Kiểm tra bộ kiểm thử Frontend
npm test

# Kiểm tra đóng gói build production Frontend
npm run build
```

### 2. Kết quả kiểm thử Backend:
- `.\mvnw.cmd clean test-compile`: **BUILD SUCCESS** (Biên dịch thành công 456 source files và 56 test files).
- `.\mvnw.cmd test`: **BUILD SUCCESS**
  - **Tests run: 434**
  - **Failures: 0**
  - **Errors: 0**
  - **Skipped: 0**
  - **Thời gian thực thi:** 03:18 phút
  - **Trạng thái:** PASS 100%

### 3. Kết quả kiểm thử Frontend:
- `npm test`: **65/65 tests PASS** (12 test suites, 0 failures, duration 467ms).
- `npm run build`: **vite build SUCCESS** (211 modules transformed, 0 syntax/bundle errors).

---

## H. Final Assessment (Đánh giá Cuối cùng)

1. **Tuân thủ quy tắc nhóm:** Code đã được refactor hoàn chỉnh theo mô hình Service gọi Service, loại bỏ việc Business Service gọi chéo Repository của module khác.
2. **Import & Syntax:** 100% không còn lỗi unresolved symbol, không còn package error, không còn FQCN dư thừa, biên dịch hoàn toàn sạch sẽ.
3. **Database Schema:** 8 bảng mới của TV5 đều phục vụ nghiệp vụ độc lập, có lý do kỹ thuật rõ ràng và được phân loại **KEEP**. Không có bảng dư thừa cần DROP. Không làm mất dữ liệu.
4. **API Contract & Backward Compatibility:** Toàn bộ API contract cũ và mới đều được hỗ trợ song song thông qua alias, không làm gãy giao tiếp với Frontend hay các module của thành viên khác.
5. **Business Rules:** Luồng Provider (duyệt/từ chối, gán tài xế, Google OR-Tools, tài chính, VNPAY, đối soát) và Driver (lịch ca, xác nhận/từ chối, GPS, minh chứng, báo sự cố) hoạt động chính xác theo đặc tả.
6. **Đủ điều kiện:** Code đạt chất lượng cao, **ĐỦ ĐIỀU KIỆN 100% ĐỂ TẠO PULL REQUEST VÀ MERGE VÀO MAIN**.
