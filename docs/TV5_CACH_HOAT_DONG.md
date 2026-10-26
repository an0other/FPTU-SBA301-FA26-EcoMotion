# HƯỚNG DẪN CÁCH HOẠT ĐỘNG TOÀN DIỆN HỆ THỐNG TV5 – ECOMOTION
*(Hệ thống Đặt xe có Tài xế – Driver Booking System)*

---

## MỤC LỤC
1. [Tổng quan kiến trúc & Các Actor](#1-tổng-quan-kiến-trúc--các-actor)
2. [Luồng 1: Đặt cọc & Xác thực thanh toán VNPAY Sandbox](#2-luồng-1-đặt-cọc--xác-thực-thanh-toán-vnpay-sandbox)
3. [Luồng 2: Tối ưu hóa phân công tài xế với Google OR-Tools CP-SAT](#3-luồng-2-tối-ưu-hóa-phân-công-tài-xế-với-google-or-tools-cp-sat)
4. [Luồng 3: Lịch làm việc, Xác nhận/Từ chối & Tự động điều phối lại (Auto Reassign)](#4-luồng-3-lịch-làm-việc-xác-nhậntừ-chối--tự-động-điều-phối-lại-auto-reassign)
5. [Luồng 4: Vòng đời thực thi chuyến đi (Trip Lifecycle), GPS & Minh chứng](#5-luồng-4-vòng-đời-thực-thi-chuyến-đi-trip-lifecycle-gps--minh-chứng)
6. [Luồng 5: Báo cáo & Xử lý sự cố chuyến đi (Incident Workflow)](#6-luồng-5-báo-cáo--xử-lý-sự-cố-chuyến-đi-incident-workflow)
7. [Luồng 6: Quyết toán phụ phí, Đối soát tài chính & Hoàn tiền (Refund)](#7-luồng-6-quyết-toán-phụ-phí-đối-soát-tài-chính--hoàn-tiền-refund)
8. [Bảng ma trận trạng thái (State Matrix) & Business Rules cốt lõi](#8-bảng-ma-trận-trạng-thái-state-matrix--business-rules-cốt-lõi)

---

## 1. TỔNG QUAN KIẾN TRÚC & CÁC ACTOR

Hệ thống **EcoMotion** vận hành theo mô hình phân quyền đa người dùng với 3 tác nhân chính và 1 lõi xử lý trung tâm:

```
+-----------------------------------------------------------------------------------+
|                                 ECO_MOTION CORE                                   |
|                                                                                   |
|  +--------------------+   +-----------------------+   +------------------------+  |
|  | Spring Boot Web    |   | Google OR-Tools       |   | VNPAY Gateway Client   |  |
|  | JPA + PostgreSQL   |   | CP-SAT Solver (JNI)   |   | HMAC-SHA512 Checksum   |  |
|  +--------------------+   +-----------------------+   +------------------------+  |
|             ^                         ^                            ^              |
+-------------|-------------------------|----------------------------|--------------+
              |                         |                            |
    +---------+-----------+   +---------+-----------+      +---------+-----------+
    |      CUSTOMER       |   |      PROVIDER       |      |       DRIVER        |
    |  - Tìm xe & đặt cọc |   |  - Quản lý Booking  |      |  - Xem lịch trình   |
    |  - Thanh toán VNPAY |   |  - Phân công tài xế |      |  - Xác nhận/Từ chối |
    |  - Theo dõi chuyến  |   |  - Xử lý sự cố/phí  |      |  - Thực thi chuyến  |
    |  - Đánh giá sao     |   |  - Đối soát & Refund|      |  - Báo GPS & sự cố  |
    +---------------------+   +---------------------+      +---------------------+
```

- **Customer (Renter)**: Tìm kiếm xe, chọn gói thuê (theo giờ/ngày), thanh toán tiền cọc qua cổng VNPAY Sandbox, theo dõi lộ trình và xác nhận kết thúc thuê.
- **Provider (Chủ xe / Quản lý Depot)**: Duyệt đơn, lựa chọn phương thức phân công tài xế (Thủ công / Đề xuất AI / Tự động hoàn toàn), giám sát vị trí xe thời gian thực, xử lý sự cố và duyệt đối soát / hoàn tiền.
- **Driver (Tài xế)**: Quản lý lịch cá nhân theo ca/ngày, phản hồi nhận hoặc từ chối chuyến đi trong thời hạn 15 phút, cập nhật các mốc vòng đời (Đến điểm đón ➔ Đang chở khách ➔ Hoàn thành), gửi tọa độ GPS định kỳ và nộp ảnh chụp minh chứng.

---

## 2. LUỒNG 1: ĐẶT CỌC & XÁC THỰC THANH TOÁN VNPAY SANDBOX

Hệ thống sử dụng cổng thanh toán **VNPAY Sandbox** đảm bảo tính an toàn, bảo mật và toàn vẹn dữ liệu giao dịch:

```
Customer               Frontend               Backend EcoMotion               VNPAY Gateway
   |                      |                           |                             |
   |-- 1. Bấm Đặt cọc --->|                           |                             |
   |                      |-- 2. POST /create-url --->|                             |
   |                      |    (bookingId)            |-- 3. Tính tiền cọc từ DB    |
   |                      |                           |-- 4. Ký HMAC-SHA512 hash    |
   |                      |<-- 5. paymentUrl ---------|                             |
   |-- 6. Redirect đến Sandbox VNPAY ---------------------------------------------->|
   |                                                                                |
   |-- 7. Nhập thông tin thẻ test NCB (9704198526191432198) ----------------------->|
   |                                                                                |
   |                                                  |<-- 8. Webhook IPN ----------|
   |                                                  |   (Verify SHA512, Checksum, |
   |                                                  |    Amount, Idempotency)     |
   |                                                  |-- 9. Booking = CONFIRMED    |
   |                                                  |-- 10. Kích hoạt OR-Tools    |
   |<-- 11. Redirect Return URL (Hiển thị kết quả) ---|                             |
```

### Chi tiết nghiệp vụ:
1. **Kiểm soát số tiền cọc (Tamper-proof Amount)**:
   Backend tự tính toán số tiền cọc dựa trên `depositRatio` từ `BookingPolicySnapshot` và giá gói thuê, **không nhận số tiền do Frontend tự gửi lên** để chống gian lận.
2. **Khởi tạo giao dịch**:
   - Tạo mã giao dịch duy nhất `vnp_TxnRef`.
   - Lưu bản ghi `PaymentTransaction` với trạng thái `PENDING`.
   - Sắp xếp thứ tự từ điển các tham số VNPAY, băm bí mật `vnp_HashSecret` bằng giải thuật `HmacSHA512` để tạo `vnp_SecureHash`.
3. **Xác minh Webhook IPN (Server-to-Server)**:
   - VNPAY gọi trực tiếp đến `GET /api/v1/payment/vnpay/ipn`.
   - Backend tính lại checksum và so khớp với `vnp_SecureHash`.
   - Kiểm tra `vnp_ResponseCode == "00"` và `vnp_TransactionStatus == "00"`.
   - Kiểm soát tính bất biến (Idempotency): Nếu giao dịch đã được xử lý thành công trước đó thì không xử lý lặp lại.
   - Cập nhật `PaymentTransaction` sang `PAID`, chuyển trạng thái `Booking` sang `CONFIRMED`.
   - Tự động kích hoạt cơ chế điều phối tài xế Google OR-Tools.
4. **Xử lý ngoại lệ (Timeout/Late Payment)**:
   Nếu tiền thanh toán vào sau khi thời hạn giữ chỗ tạm (hold slot) đã hết hạn và xe đã được đơn khác thuê, giao dịch được chuyển sang trạng thái đối soát ngoại lệ (`PENDING_RECONCILIATION`), kích hoạt quy trình hoàn tiền tự động cho khách.

---

## 3. LUỒNG 2: TỐI ƯU HÓA PHÂN CÔNG TÀI XẾ VỚI GOOGLE OR-TOOLS CP-SAT

Thay thế các thuật toán gán đơn thông thường bằng **Google OR-Tools CP-SAT (Constraint Programming - Satisfiability)** chạy trực tiếp qua native libraries (JNI), hỗ trợ 3 chế độ vận hành:

```
                                [ Booking CONFIRMED ]
                                          |
                         +----------------+----------------+
                         |                                 |
                  [ AUTO_SUGGEST ]                    [ FULL_AUTO ]
                         |                                 |
                 Load Candidate Pool               Load Candidate Pool
                         |                                 |
            +------------+------------+       +------------+------------+
            | Check Hard Constraints: |       | Check Hard Constraints: |
            | - Provider Ownership    |       | - Provider Ownership    |
            | - Profile == APPROVED   |       | - Profile == APPROVED   |
            | - User == ACTIVE        |       | - User == ACTIVE        |
            | - License Expiry Valid  |       | - License Expiry Valid  |
            | - License Class Match   |       | - License Class Match   |
            | - No Active Incidents   |       | - No Active Incidents   |
            | - No Interval Conflict  |       | - No Interval Conflict  |
            |   (including buffer)    |       |   (including buffer)    |
            | - Not in Round Blacklist|       | - Not in Round Blacklist|
            +------------+------------+       +------------+------------+
                         |                                 |
              [ OR-Tools CP-SAT Solver ]        [ OR-Tools CP-SAT Solver ]
              Minimize Objective Penalty        Minimize Objective Penalty
                         |                                 |
              Trả về danh sách ứng viên,        Chọn tài xế điểm số tốt nhất,
              điểm tối ưu & lý do đề xuất.      Tạo DriverAssignment (vòng 1),
                         |                      Booking = DRIVER_ASSIGNED,
              Provider bấm xác nhận chọn.       Bắt đầu đếm ngược 15 phút.
```

### A. Ba chế độ phân công:
1. **MANUAL (Thủ công)**:
   - Provider chủ động lựa chọn tài xế trong danh sách nhân sự của mình.
   - Backend xác thực các điều kiện tiên quyết: tài khoản hoạt động (`ACTIVE`), hồ sơ đã duyệt (`APPROVED`), bằng lái còn hạn tại ngày đón khách, hạng bằng tương thích với số chỗ và hộp số của xe, không có sự cố mở (`OPEN`).
2. **AUTO_SUGGEST (AI Gợi ý)**:
   - OR-Tools tính toán điểm số cho toàn bộ tài xế đủ điều kiện.
   - Frontend hiển thị danh sách ứng viên kèm nhãn: *Thời gian đến đón (ETA)*, *Số chuyến trong ngày*, *Điểm đánh giá* và *Lý do được đề xuất* bằng tiếng Việt.
   - Provider kiểm tra và bấm nút giao việc.
3. **FULL_AUTO (Tự động hoàn toàn)**:
   - Kích hoạt ngay khi đơn thuê chuyển thành `CONFIRMED`.
   - Solver tìm tài xế có chi phí phạt thấp nhất, tự động tạo `DriverAssignment` và thông báo cho tài xế.

### B. Mô hình toán học trong CP-SAT:
- **Biến quyết định**: $x_i \in \{0, 1\}$ (Tài xế $i$ được chọn hay không).
- **Ràng buộc duy nhất**: $\sum_i x_i \le 1$.
- **Hàm mục tiêu (Minimize Penalty)**:
  $$\text{Minimize} \sum_i \left( 50 \cdot \text{ETA}_i + 200 \cdot \text{TripsToday}_i + 150 \cdot (5.0 - \text{Rating}_i) \right) \cdot x_i$$
- **Kiểm tra va chạm lịch trình (Interval Collision)**:
  Hai chuyến đi bị coi là trùng lặp nếu:
  $$(Start_{\text{new}} - Buffer) < End_{\text{cũ}} + Buffer \quad \text{và} \quad (End_{\text{new}} + Buffer) > Start_{\text{cũ}} - Buffer$$
  *(Hệ thống tính $End_{\text{cũ}}$ chính xác từ `BookingPolicySnapshot` và `RentalPlan` ngay cả khi chuyến đi chưa hoàn thành).*

---

## 4. LUỒNG 3: LỊCH LÀM VIỆC, XÁC NHẬN/TỪ CHỐI & TỰ ĐỘNG ĐIỀU PHỐI LẠI (AUTO REASSIGN)

```
[ Booking: DRIVER_ASSIGNED ]
[ DriverAssignment: PENDING_RESPONSE (Hạn phản hồi: 15 phút) ]
       |
       +------------------------------------+------------------------------------+
       |                                    |                                    |
[ Tài xế bấm XÁC NHẬN ]            [ Tài xế bấm TỪ CHỐI ]               [ HẾT GIỜ (Timeout) ]
       |                           (Bắt buộc chọn lý do)                         |
Assignment = ACCEPTED                       |                           Scheduler quét định kỳ
Booking = DRIVER_CONFIRMED                  |                           Assignment = TIMEOUT
(Provider nhận thông báo)                   +-----------------+------------------+
                                                              |
                                               [ KÍCH HOẠT AUTO REASSIGN ]
                                                              |
                                               1. Đưa tài xế cũ vào Blacklist vòng này.
                                               2. Giải phóng phân công cũ.
                                               3. Tăng roundNumber (1 -> 2 -> 3).
                                               4. Chạy lại Google OR-Tools CP-SAT.
                                                              |
                                           +------------------+------------------+
                                           |                                     |
                                 [ Tìm được tài xế mới ]              [ Không còn tài xế / Hết 3 lần ]
                                           |                                     |
                                 Tạo DriverAssignment mới             Booking = WAITING_FOR_DRIVER
                                 Gửi thông báo tài xế mới             Gửi cảnh báo cho Provider
                                 Đếm ngược 15 phút mới                (Không tự hủy đơn của khách)
```

### Các nguyên tắc an toàn:
- **Thời hạn phản hồi**: Cố định 15 phút (`ASSIGNMENT_TIMEOUT_DURATION = 15m`).
- **Lý do từ chối chuẩn hóa**: Bắt buộc chọn từ enum `DriverRejectReason` (*Không thể sắp xếp thời gian*, *Sự cố sức khỏe*, *Không thể đến điểm đón đúng giờ*, *Công việc đột xuất*, *Khác*).
- **Giới hạn thử lại**: Tối đa 3 vòng phân công (`MAX_AUTO_REASSIGN_RETRIES = 3`). Tránh vòng lặp vô tận.
- **Bảo vệ khách hàng**: Khi hết tài xế, hệ thống chuyển sang `WAITING_FOR_DRIVER` để Provider xử lý bằng tay hoặc điều chuyển xe khác, **tuyệt đối không tự động hủy booking của khách**.

---

## 5. LUỒNG 4: VÒNG ĐỜI THỰC THI CHUYẾN ĐI (TRIP LIFECYCLE), GPS & MINH CHỨNG

### A. Máy trạng thái (Trip State Machine)

```
+----------------------------------------------------------------------------------------------------+
|                                      VÒNG ĐỜI CHUYẾN ĐI (D02)                                      |
|                                                                                                    |
|  [ DRIVER_ASSIGNED ]                                                                               |
|          |                                                                                         |
|          |-- (Tài xế bấm "Xác nhận nhận chuyến")                                                    |
|          v                                                                                         |
|  [ DRIVER_CONFIRMED ]                                                                              |
|          |                                                                                         |
|          |-- (Tài xế di chuyển và bấm "Đã đến điểm đón")                                            |
|          v                                                                                         |
|  [ DRIVER_ARRIVED ]                                                                                |
|          |                                                                                         |
|          |-- (Khách lên xe, tài xế bấm "Bắt đầu chuyến")                                           |
|          v                                                                                         |
|  [ IN_PROGRESS ]                                                                                   |
|          |  * Cập nhật GPS liên tục (mỗi 15-30s)                                                   |
|          |  * Phát hiện tín hiệu Stale nếu > 60s không có dữ liệu                                  |
|          |  * Chặn tài xế tự ý hủy chuyến                                                          |
|          |                                                                                         |
|          |-- (Trả khách tại điểm đến, tài xế bấm "Kết thúc chuyến")                                |
|          v                                                                                         |
|  [ AWAITING_EXTRA_CHARGE_CONFIRMATION ]                                                            |
|          |                                                                                         |
|          +--> Chuyển đơn sang bước Provider đối soát phụ phí, không tự ý hoàn tất tài chính        |
+----------------------------------------------------------------------------------------------------+
```

### B. Theo dõi vị trí GPS & Phát hiện mất tín hiệu:
- Dữ liệu gửi lên: `latitude`, `longitude`, `speed`, `heading`, `accuracy`.
- **Phát hiện GPS Stale**: Nếu thời gian ghi nhận cuối cùng cách hiện tại quá 60 giây, hệ thống tự động gán trạng thái `LOCATION_STALE` và cảnh báo Provider mất tín hiệu định vị.
- **Bảo mật truy cập**: Chỉ tài xế được phân công mới có quyền cập nhật vị trí cho chuyến đi đó; Provider chỉ xem được vị trí các xe thuộc quyền quản lý của depot mình.

### C. Minh chứng chuyến đi (Trip Evidence):
Tài xế chụp và tải ảnh minh chứng lưu trữ vào hệ thống theo các danh mục:
1. `PICKUP_ARRIVAL`: Ảnh khi tài xế đến điểm đón khách hẹn trước.
2. `VEHICLE_HANDOVER`: Ảnh biên bản/tình trạng xe lúc đón khách.
3. `TRIP_COMPLETION`: Ảnh đồng hồ công tơ mét / hiện trạng xe khi kết thúc.
4. `INCIDENT_PROOF`: Ảnh chụp hiện trường nếu xảy ra sự cố.

---

## 6. LUỒNG 5: BÁO CÁO & XỬ LÝ SỰ CỐ CHUYẾN ĐI (INCIDENT WORKFLOW)

Quy trình hỗ trợ tài xế ứng phó khẩn cấp khi gặp trở ngại trên đường:

```
Tài xế                                   Hệ thống                                   Provider
  |                                         |                                          |
  |-- 1. Báo sự cố khẩn cấp --------------->|                                          |
  |    (Loại, mô tả, mức khẩn, tọa độ, ảnh) |-- 2. Lưu TripIncident (OPEN)            |
  |                                         |-- 3. Đánh dấu tài xế đang bận sự cố      |
  |                                         |-- 4. Bắn thông báo khẩn ---------------->|
  |                                         |                                          |-- 5. Xem hiện trường & tọa độ
  |                                         |                                          |-- 6. Điều phối xe/tài xế thay thế
  |                                         |<-- 7. PUT /incidents/{id}/resolve -------|
  |                                         |    (RESOLVED / INVESTIGATING)            |
  |<-- 8. Nhận hướng dẫn hỗ trợ ------------|                                          |
```

- **Phân loại sự cố (`TripIncidentType`)**: *Tai nạn giao thông*, *Vấn đề sức khỏe*, *Hỏng xe / sự cố kỹ thuật*, *Không thể tiếp cận điểm đón*, *Mất liên lạc với khách*, *Ùn tắc nghiêm trọng*, *Khác*.
- **Mức độ khẩn cấp (`TripIncidentUrgency`)**: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- **Ràng buộc an toàn**: Khi chuyến đi đang ở trạng thái `IN_PROGRESS`, tài xế **không được phép bấm hủy đơn thuê**. Mọi tình huống dừng chuyến bất thường đều phải thông qua báo cáo sự cố để Provider phối hợp xử lý.

---

## 7. LUỒNG 6: QUYẾT TOÁN PHỤ PHÍ, ĐỐI SOÁT TÀI CHÍNH & HOÀN TIỀN (REFUND)

### A. Đối soát doanh thu Provider (O11)
Trang tài chính phân định minh bạch các dòng tiền:
- **Tiền cọc đã thanh toán (Deposit Paid)**: Khách đã trả để giữ chỗ.
- **Tiền thanh toán trước (Prepayment)**: Tiền khách trả dôi ra ngoài tiền cọc.
- **Phụ phí sau chuyến (Surcharges)**: Tiền quá giờ (`OVERTIME`), tiền chờ khách (`WAITING`), phụ phí đi ngoài bán kính (`OUT_OF_RADIUS`).
- **Số dư còn phải thu (Remaining Balance)**: Khoản tiền khách cần trả nốt khi kết thúc chuyến.
- **Doanh thu dự kiến của Provider**: Sau khi trừ tỷ lệ hoa hồng nền tảng (`commission_rate`).

### B. Quy trình Hoàn tiền (Refund Workflow)

```
Provider / Admin                         RefundService                           Payment Gateway
       |                                       |                                        |
       |-- 1. Yêu cầu hoàn tiền -------------->|                                        |
       |    (bookingId, số tiền, lý do)        |-- 2. Kiểm tra: Tiền hoàn mới +         |
       |                                       |      Đã hoàn <= Tiền khách đã trả      |
       |                                       |-- 3. Kiểm tra: Không hoàn trùng        |
       |                                       |-- 4. Tạo RefundRequest (REQUESTED)     |
       |                                       |                                        |
       |-- 5. Duyệt hoàn tiền (review) ------->|                                        |
       |    (APPROVED / REJECTED)              |-- 6. Khóa bi quan bản ghi (Locking)    |
       |                                       |-- 7. Gọi API Gateway / Payout Mock --->|
       |                                       |-- 8. Ghi mã đối soát refund            |
       |<-- 9. Hoàn tất (REFUNDED) ------------|                                        |
```

- **Quy tắc an toàn tài chính**:
  - Không bao giờ cho phép hoàn tiền vượt quá tổng số tiền khách đã thanh toán thực tế.
  - Chặn tạo 2 yêu cầu hoàn tiền song song trên cùng một đơn khi yêu cầu trước chưa xử lý xong.
  - Payout Mock hiển thị rõ huy hiệu **Dữ liệu thử nghiệm**, không gây hiểu nhầm là tiền ngân hàng thật.

---

## 8. BẢNG MA TRẬN TRẠNG THÁI (STATE MATRIX) & BUSINESS RULES CỐT LÕI

| Ngữ cảnh | Trạng thái | Ý nghĩa nghiệp vụ | Hành động tiếp theo khả thi |
|---|---|---|---|
| **Booking** | `PENDING` | Khách vừa tạo đơn, chờ Provider duyệt | Provider: `Accept` hoặc `Reject` |
| **Booking** | `ACCEPTED` | Provider đã duyệt, chờ khách cọc | Customer: Tạo URL thanh toán VNPAY |
| **Booking** | `CONFIRMED` | Khách đã thanh toán cọc thành công | Kích hoạt Auto Assign / Manual Assign tài xế |
| **Booking** | `DRIVER_ASSIGNED` | Đã gán tài xế, tài xế chưa phản hồi | Driver: `Confirm` hoặc `Reject` (15 phút) |
| **Booking** | `DRIVER_CONFIRMED`| Tài xế đã xác nhận nhận chuyến | Driver: Bấm `Arrive` khi đến nơi đón |
| **Booking** | `DRIVER_ARRIVED`  | Xe đã đỗ tại điểm hẹn đón khách | Driver: Bấm `Start` khi khách lên xe |
| **Booking** | `IN_PROGRESS`     | Xe đang di chuyển chở khách | Driver: Báo GPS, Báo sự cố, Bấm `Finish` |
| **Booking** | `AWAITING_EXTRA_CHARGE_CONFIRMATION` | Chuyến đi kết thúc, chờ kiểm tra phí | Provider: Thêm phụ phí nếu có ➔ Chốt quyết toán |
| **Booking** | `AWAITING_FINAL_PAYMENT` | Đơn đã chốt phí, chờ khách trả nốt | Customer: Trả nốt tiền ➔ `COMPLETED` |
| **Booking** | `COMPLETED`       | Chuyến đi hoàn tất trọn vẹn cả dịch vụ & tiền | Khách đánh giá sao cho tài xế & xe |
| **Booking** | `REJECTED`        | Đơn bị từ chối trước khi cọc | Giữ nguyên các trường `cancelled_*` là null |
| **Booking** | `CANCELLED`       | Đơn bị hủy sau khi cọc | Lưu `cancelled_by`, `cancelled_at`, lý do hủy |
| **Assignment** | `PENDING_RESPONSE` | Đang đếm ngược 15 phút chờ tài xế | Tài xế bấm Nhận hoặc Từ chối |
| **Assignment** | `ACCEPTED`        | Tài xế đồng ý thực hiện | Chuyển sang vòng đời chuyến đi |
| **Assignment** | `REJECTED`        | Tài xế từ chối với lý do rõ ràng | Kích hoạt Auto Reassign tìm tài xế khác |
| **Assignment** | `TIMEOUT`         | Quá 15 phút tài xế không phản hồi | Hủy quyền, kích hoạt Auto Reassign |
| **Refund**    | `REQUESTED`       | Yêu cầu hoàn tiền vừa tạo | Admin / Provider vào xem xét hồ sơ |
| **Refund**    | `APPROVED`        | Đã được phê duyệt số tiền hoàn | Hệ thống chuyển sang xử lý thanh toán |
| **Refund**    | `REFUNDED`        | Tiền đã hoàn lại cho khách hàng | Cập nhật sổ cái đối soát |

---

## 9. HƯỚNG DẪN KIỂM TRA & XÁC MINH NHANH

### A. Kiểm tra Backend (Chạy toàn bộ 341 bài test tự động)
```powershell
cd "c:\Users\KIETLT\Documents\GitHub\Project SBA\FPTU-SBA301-FA26-EcoMotion\Backend\EcoMotion"
.\mvnw.cmd test
```
*Kết quả kỳ vọng: `BUILD SUCCESS` (341 tests, 0 failures, 0 errors, 0 skipped).*

### B. Kiểm tra Frontend (Chạy 43 bài test hợp đồng giao diện)
```powershell
cd "c:\Users\KIETLT\Documents\GitHub\Project SBA\FPTU-SBA301-FA26-EcoMotion\Frontend\EcoMotion"
npm test -- --run
```
*Kết quả kỳ vọng: `43 pass, 0 fail`.*

### C. Khởi động hệ thống
1. **Backend**:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   Swagger UI: `http://localhost:8080/swagger-ui/index.html`
2. **Frontend**:
   ```powershell
   npm run dev
   ```
   Giao diện: `http://localhost:5173`
