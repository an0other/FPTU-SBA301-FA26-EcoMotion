# BÁO CÁO AUDIT SOURCE CODE & KIẾN TRÚC TRIỂN KHAI TOÀN DIỆN TV5 — ECOMOTION

**Dự án:** EcoMotion – Nền tảng cho thuê xe kèm tài xế (Chauffeur-hire)  
**Phạm vi:** TV5 (O07, O08, O11, D01, D02, VNPAY Sandbox, Google OR-Tools CP-SAT)  
**Ngày audit:** 23/10/2026  
**Trạng thái kiểm thử hiện tại:** 
- Backend: **341/341 tests PASS** (`mvn test` thành công 100%)
- Frontend: **43/43 tests PASS**, `vite build` thành công

---

## 1. NHỮNG CHỨC NĂNG ĐÃ TỒN TẠI VÀ ĐANG HOẠT ĐỘNG TỐT

### 1.1. O07 – Provider Booking Queue
- **Controller & Service:** `ProviderBookingController`, `ProviderBookingServiceImpl`.
- **Hàng đợi đơn:** `listBookings`, `listBookingsPaged` (phân trang chuẩn, lọc theo trạng thái).
- **Thống kê:** `getSummary` đếm số lượng đơn theo từng trạng thái (`PENDING`, `ACCEPTED`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `REJECTED`).
- **Xuất CSV:** `exportBookingsCsv` xuất danh sách ra file CSV kèm BOM UTF-8 hỗ trợ Excel.
- **Frontend O07:** `ProviderBookingQueuePage.jsx` có đủ summary cards, filter tabs, bảng đơn xe, pagination.

### 1.2. O08 – Provider Booking Detail, Accept/Deny, Phụ phí thủ công
- **Chi tiết đơn:** `getBookingDetail` hiển thị toàn diện thông tin đơn, xe, ảnh đại diện, khách thuê, snapshot chính sách, bảng phụ phí, lịch sử giao dịch.
- **Duyệt / Từ chối:**
  - `acceptBooking`: kiểm tra trạng thái `PENDING`, kiểm tra lịch xe trống (`scheduleService.isRangeFree`), chuyển sang `ACCEPTED`, tạo yêu cầu cọc.
  - `denyBooking`: chuyển `PENDING` sang `REJECTED`.
- **Manual Assign:** `assignDriver` cho phép Provider gán tài xế thuộc quyền quản lý (`driverRepository.findByIdAndProvider_Id`), chuyển `CONFIRMED` sang `DRIVER_ASSIGNED`.
- **Manual Surcharge:** `addCharge` cho phép Provider thêm phụ phí phát sinh (`WAITING`, `OVERTIME`, `TOLL_FEE`...) sau khi chuyến kết thúc; tính tiền độc lập tại Backend.
- **Finalize Settlement:** `finalizeSettlement` chốt phụ phí, tính số dư còn lại cần thanh toán.

### 1.3. D01 & D02 – Driver Schedule & Trip Execution
- **Lịch chuyến D01:** `listDriverTrips` lọc chuyến theo tab `TODAY`, `UPCOMING`, `HISTORY`, lọc theo ngày và trạng thái theo múi giờ Việt Nam (`Asia/Ho_Chi_Minh`).
- **Trip State Transitions cơ bản D02:**
  - `confirmTrip`: `DRIVER_ASSIGNED` -> `DRIVER_CONFIRMED`.
  - `arriveAtPickup`: `DRIVER_CONFIRMED` -> `DRIVER_ARRIVED`.
  - `startTrip`: `DRIVER_ARRIVED` -> `IN_PROGRESS` (ghi nhận `actualStartAt = NOW`).
  - `finishTrip`: `IN_PROGRESS` -> `AWAITING_EXTRA_CHARGE_CONFIRMATION` (ghi nhận `actualEndAt = NOW`).
- **GPS Location Tracking:**
  - `recordLocation`: lưu tọa độ vào `vehicle_location`, kiểm tra tính đơn điệu (`recordedAt >= latest.recordedAt`), phát hiện tọa độ lạc hậu `STALE_LOCATION_THRESHOLD` (60s).
  - Reverse Geocoding qua OpenStreetMap Nominatim.

---

## 2. NHỮNG CHỨC NĂNG CÒN THIẾU HOẶC CHƯA HOÀN THIỆN

| Module | Chức năng thiếu | Hiện trạng | Yêu cầu phải triển khai |
|---|---|---|---|
| **O08** | **Google OR-Tools CP-SAT Auto Assign** | Hiện chỉ có Manual Assign và lấy danh sách tài xế rảnh đơn giản, chưa có bộ tối ưu OR-Tools. | Tích hợp Google OR-Tools CP-SAT Java (9.11+). Triển khai 3 chế độ `MANUAL`, `AUTO_SUGGEST`, `FULL_AUTO`. Ràng buộc cứng (GPLX, lịch trùng, ca làm việc, độ tuổi/hạng xe) & ràng buộc mềm (ETA, cân bằng tải, rating). |
| **D01** | **Driver Reject & Rejection Reason** | Chưa có endpoint và UI cho tài xế từ chối chuyến. | Bổ sung API `POST /trips/{id}/reject`, lưu lý do từ chối, giải phóng assignment, thông báo Provider. |
| **D01** | **Auto Reassignment khi Reject / Timeout** | Chưa có cơ chế đếm lùi thời hạn phản hồi và tự động tìm tài xế thay thế. | Thêm background scheduler kiểm tra assignment quá hạn; kích hoạt lại OR-Tools tìm tài xế mới; giới hạn số lần retry; chuyển `WAITING_FOR_DRIVER` nếu hết ứng viên. |
| **D02** | **Driver Incident Workflow** | Chưa có chức năng báo sự cố trước chuyến và trong chuyến. | Bổ sung entity/table `trip_incident`, API cho tài xế báo sự cố (tai nạn, xe hỏng, sức khỏe), quy trình Provider điều phối hỗ trợ hoặc kích hoạt đổi tài xế/xe. Chặn tài xế tự hủy đơn khi đang `IN_PROGRESS`. |
| **D02** | **Bằng chứng giao nhận & kết thúc xe (Trip Evidence)** | Hiện chỉ có GPS tọa độ, chưa có lưu trữ minh chứng ảnh giao nhận và hoàn thành. | Bổ sung entity/table `trip_evidence` cho phép tải ảnh: đến điểm đón, giao nhận xe, kết thúc chuyến, ảnh sự cố; validate kích thước/MIME, lưu trữ an toàn. |
| **Payment** | **Thay VietQR bằng VNPAY Sandbox** | Đang dùng sinh mã VietQR tĩnh (`img.vietqr.io`) và đối soát thủ công bằng upload ảnh chuyển khoản. | Tích hợp VNPAY Sandbox chính thức: tạo Payment URL, xử lý Return URL và IPN webhook (xác thực HMAC-SHA512), hỗ trợ QueryDR đối soát, tự động kích hoạt xác nhận booking khi cọc thành công. |
| **O11** | **Đối soát thanh toán & Xử lý tiền cọc dư (Reconciliation)** | Bảng đối soát chưa bóc tách rõ phần tiền cọc, tiền trả trước vượt cọc, tiền còn phải thu và hoa hồng sàn. | Hoàn thiện logic phân bổ dòng tiền: phân biệt rõ `depositAllocated` vs `prepaymentAllocated` để không tính trùng; tính toán chính xác hoa hồng nền tảng và khoản phải trả cho Provider. |
| **O11** | **Quy trình Refund hoàn chỉnh** | Chưa có bảng và workflow quản lý yêu cầu hoàn tiền (`refund_request`). | Xây dựng quy trình `REQUESTED -> UNDER_REVIEW -> APPROVED -> PROCESSING -> REFUNDED`, chống hoàn trùng, kiểm tra trần số tiền hoàn, kết nối API Refund VNPAY (hoặc cơ chế internal settlement). |
| **Chung** | **Đồng bộ trạng thái Provider - Driver** | Hai actor giao tiếp rời rạc, chưa có cơ chế refresh đồng bộ tức thời khi một bên thay đổi trạng thái. | Chuẩn hóa State Machine, bổ sung sự kiện thông báo nội bộ / cơ chế polling/SSE/WebSocket và versioning optimistic locking chống lost update. |

---

## 3. CÁC ĐIỂM ĐANG SAI HOẶC LỆCH BUSINESS RULE

1. **Deny Booking đang set `cancelledBy` và `cancelledAt`:**
   - Trong `ProviderBookingServiceImpl.java` (dòng 404-405): `booking.setCancelledAt(Instant.now()); booking.setCancelledBy(provider.getUser());`.
   - **Lệch spec:** Database constraint `ck_booking_not_cancelled_fields` quy định rõ: `status = 'CANCELLED' OR (cancelled_at IS NULL AND cancel_reason IS NULL AND cancelled_by IS NULL)`. Nếu trạng thái là `REJECTED`, các trường `cancelled_*` phải là `NULL`! Set các trường này trên `REJECTED` vi phạm tính toàn vẹn ngữ nghĩa của spec `docs/SBA301_FALL26.md`. Cần sửa để `denyBooking` giữ `cancelledBy`, `cancelledAt` và `cancelReason` là `null`.
2. **Auto Assign cũ tự gán tài xế không qua xác nhận:**
   - Khi gán tài xế thủ công, hệ thống chuyển sang `DRIVER_ASSIGNED`. Tuy nhiên cần đảm bảo khi kích hoạt `FULL_AUTO`, tài xế vẫn phải nhận được thông báo và thực hiện thao tác Accept/Reject, không được coi booking `CONFIRMED` là tài xế đã tự động `ACCEPTED`.
3. **VietQR hiển thị trực tiếp tài khoản cá nhân thay vì luồng thanh toán cổng:**
   - Hiện khách hàng quét mã VietQR chuyển tiền trực tiếp cho tài khoản Provider và Provider bấm "Duyệt tiền", dẫn đến rủi ro gian lận và thiếu tự động hóa. Cần chuyển sang luồng VNPAY Sandbox chuẩn hóa.

---

## 4. TỔNG HỢP ENTITY VÀ DATABASE MIGRATION CẦN THIẾT

Để triển khai các chức năng bắt buộc mà không phá vỡ schema hiện có:

### 4.1. Entity mới cần thêm:
1. **`DriverAssignment`** (trong `booking.entity`): Lưu lịch sử phân công tài xế, trạng thái (`PENDING_RESPONSE`, `ACCEPTED`, `REJECTED`, `TIMEOUT`, `RECALLED`), lý do từ chối, thời hạn phản hồi.
2. **`TripIncident`** (trong `booking.entity`): Lưu báo cáo sự cố của tài xế, mức độ khẩn cấp, loại sự cố, bằng chứng và ghi chú xử lý của Provider.
3. **`TripEvidence`** (trong `booking.entity`): Lưu trữ ảnh bằng chứng các mốc chuyến đi (đến đón, giao nhận xe, kết thúc, sự cố).
4. **`RefundRequest`** (trong `finance.entity`): Quản lý vòng đời hoàn tiền, số tiền, lý do, người duyệt, mã tham chiếu VNPAY Refund.

### 4.2. Bổ sung trường trên Entity hiện có:
- **`Booking`**: Thêm `reassignCount` (đếm số lần auto reassign), `assignmentExpiresAt` (thời hạn tài xế phản hồi), `assignmentMode` (`MANUAL`, `AUTO_SUGGEST`, `FULL_AUTO`).
- **`PaymentTransaction`**: Thêm các trường lưu chi tiết VNPAY: `vnpTxnRef`, `vnpTransactionNo`, `vnpBankCode`, `vnpCardType`, `vnpPayDate`, `vnpResponseCode`.

### 4.3. Migration script:
Tạo file `db/migrations/20261023_tv5_advanced_features.sql` bổ sung an toàn bằng cú pháp `CREATE TABLE IF NOT EXISTS` và `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.

---

## 5. KẾ HOẠCH TRIỂN KHAI THEO TỪNG GIAI ĐOẠN

1. **Giai đoạn 1 – Hoàn tất Audit & Setup Thư viện:**
   - Thêm dependency `com.google.ortools:ortools-java:9.11.4210` vào `pom.xml`.
   - Tạo các Entity và Repository mới: `DriverAssignment`, `TripIncident`, `TripEvidence`, `RefundRequest`.
   - Tạo migration script `20261023_tv5_advanced_features.sql`.
2. **Giai đoạn 2 – O08: Google OR-Tools CP-SAT Service:**
   - Xây dựng `DriverOptimizationService` với CP-SAT solver, hỗ trợ 3 chế độ `MANUAL`, `AUTO_SUGGEST`, `FULL_AUTO`.
   - Tích hợp endpoint đề xuất ứng viên tối ưu và tự động phân công.
3. **Giai đoạn 3 – D01: Driver Accept/Reject & Auto Reassign Scheduler:**
   - Bổ sung endpoint tài xế từ chối chuyến kèm lý do.
   - Xây dựng `@Scheduled` job tự động tái phân công khi hết hạn phản hồi hoặc tài xế từ chối.
4. **Giai đoạn 4 – D02: Trip Lifecycle, Báo sự cố & Bằng chứng giao nhận:**
   - Chuẩn hóa State Machine và audit timestamps.
   - Thêm API báo cáo và xử lý sự cố (`TripIncident`).
   - Thêm API upload và tra cứu hình ảnh bằng chứng chuyến đi (`TripEvidence`).
5. **Giai đoạn 5 – Payment: Tích hợp VNPAY Sandbox thay thế VietQR:**
   - Xây dựng `VnpayService` sinh URL thanh toán, xác thực checksum HMAC-SHA512.
   - Triển khai endpoint IPN, Return URL và QueryDR.
   - Nối luồng thanh toán thành công với kích hoạt Auto Assign.
6. **Giai đoạn 6 – O11: Đối soát tài chính, Tiền cọc dư & Quy trình Refund:**
   - Bóc tách minh bạch tiền cọc, tiền trả trước, hoa hồng sàn, khoản thực nhận của Provider.
   - Triển khai workflow hoàn tiền `RefundRequest` chống hoàn trùng.
   - Mock Payout hiển thị rõ nhãn dữ liệu thử nghiệm.
7. **Giai đoạn 7 – Frontend & Đồng bộ trạng thái:**
   - Cập nhật giao diện O08, D01, D02, O11 và trang thanh toán VNPAY bằng tiếng Việt.
   - Kết nối toàn bộ API thật, hoàn thiện kiểm thử.
