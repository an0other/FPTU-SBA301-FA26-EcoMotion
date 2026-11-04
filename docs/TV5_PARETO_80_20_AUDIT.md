# TV5 Pareto 80/20 audit — 04/11/2026

## A. Executive summary

**Verdict: INSUFFICIENT EVIDENCE.** Không thể khẳng định sẵn sàng merge: có hai sai lệch quyền riêng tư xác định bằng truy vết mã, nhưng chưa có HTTP test nhiều tài khoản; các kiểm thử backend không chạy được trong phiên này, PostgreSQL cô lập và sandbox VNPAY chưa chạy. Không coi báo cáo lịch sử là kết quả mới. Không có bằng chứng đủ để kết luận P0 hay double booking đã xảy ra.

Năm việc ưu tiên theo tác động và mức độ chắc chắn:

1. P1: bảo vệ endpoint tra cứu giao dịch VNPAY khỏi truy cập không có quyền (F01).
2. P1: thu hẹp dữ liệu cá nhân và lịch booking trả về cho ứng viên phân công (F02).
3. Chạy PostgreSQL concurrency cô lập với migration thật: kiểm tra khóa và trạng thái; hiện là khoảng trống chứng cứ, không phải bug đã xác nhận.
4. Chạy security HTTP với nhiều tài khoản và tất cả alias; hiện chưa chứng minh IDOR khác.
5. Chạy sandbox VNPAY thật với IPN HTTPS, đối chiếu database và ba actor; hiện chưa có chứng cứ giao dịch thật.

Nguồn audit: nhánh `task-member-5`, HEAD `945718e5ccf98247873800d19d764ae887bd53f3`, **bao gồm các thay đổi chưa commit**. `git status` có nhiều thay đổi TV5, VNPAY và renter từ trước phiên audit; do đó verdict không áp dụng riêng cho HEAD sạch. Không sửa mã ứng dụng, không commit, reset hoặc chỉnh database trong phiên này.

## B. Task coverage matrix

| Task | Requirement | Implemented (theo source) | Verified trong phiên | Issue | Priority |
|---|---|---|---|---|---|
| O07 | Queue, lọc, export | Có controller/service | Chưa kiểm HTTP/DB | Chưa đủ chứng cứ về phân trang và quyền | Chưa xếp |
| O08 | Candidate, manual/auto, timeout, extra charge | Có service, khóa booking/driver, kiểm eligibility | Đọc source; chưa chạy integration | F02; đồng thời chưa được kiểm chứng | P1 |
| O11 | Finance, refund, payout, VNPAY | Có workflow, khóa ledger, mock payout gắn nhãn | FE unit 31 pass; source review một phần | F01; refund concurrency chưa kiểm chứng | P1 |
| D01 | Schedule, confirm/reject | Có API/service và test nguồn | Chưa chạy backend | Thiếu kiểm thử thực thi và IDOR | Chưa xếp |
| D02 | Arrive, start, finish, timestamp | Có chuyển trạng thái và timestamp | Đọc source một phần | Thiếu fullstack và race test | Chưa xếp |
| Renter/Provider sync | Refetch sau thanh toán | Có SSE/refetch | Không chạy E2E mới trong phiên | Sandbox và đồng bộ ba actor chưa chứng minh | Chưa xếp |

## C. Findings

### F01 — Tra cứu VNPAY không kiểm tra quyền trên giao dịch

- **Severity:** P1; **Task:** O11/tích hợp VNPAY. **Trạng thái:** sai lệch authorization xác nhận bằng source; chưa tái hiện HTTP.
- **Tác động:** người biết cả booking code và `vnpTxnRef` có thể tra thông tin giao dịch và kích hoạt nhánh QueryDR; response chứa số tiền, mã giao dịch, ngân hàng, loại thẻ, thời gian và trạng thái. Không khẳng định có thể đoán được reference hay có thanh toán giả.
- **Nguyên nhân:** `SecurityConfig.java:53` cho phép công khai toàn bộ bốn alias `/payment/vnpay/**`; `finance/controller/VnpayPaymentController.java:80` không nhận principal; `finance/service/implement/VnpayServiceImpl.java:628` chỉ kiểm tra reference thuộc booking, không kiểm tra actor. `finance/dto/response/VnpayCallbackResultResponse.java:5` định nghĩa dữ liệu trả về.
- **API:** `GET /api/v1/payment/vnpay/query?bookingCode=...&vnpTxnRef=...` và các alias controller tương đương.
- **Kịch bản:** lấy cặp code/reference hợp lệ từ một giao dịch test, gọi endpoint không có token hoặc bằng tài khoản không liên quan. **Mong đợi:** từ chối hoặc chỉ cấp thông tin theo quyền. **Thực tế theo source:** endpoint mở, tìm transaction theo cặp code và reference, trả `transactionResult`; chưa xác nhận bằng HTTP trong phiên này.
- **Sửa tối thiểu:** chỉ mở IPN và callback cần thiết; yêu cầu principal cho query, kiểm tra renter sở hữu booking hoặc provider/admin phù hợp trước khi đọc/gọi QueryDR. Giữ callback browser/IPN đúng hợp đồng. Rà soát toàn bộ alias.
- **Regression:** không token, renter khác, provider khác, driver, owner và admin; kiểm tra response không lộ tiền/mã khi từ chối; IPN và callback vẫn hoạt động.

### F02 — Candidate trả dữ liệu cá nhân và chi tiết lịch không cần thiết

- **Severity:** P1 theo yêu cầu tối thiểu hóa dữ liệu của audit; **Task:** O08. **Trạng thái:** contract trả dư được xác nhận qua source; chưa chứng minh truy cập chéo provider.
- **Tác động:** người có quyền điều phối nhìn thấy số điện thoại, số GPLX và mã/thời gian các booking khác trong response và UI; tăng phạm vi lộ PII và lịch. Không kết luận lộ sang provider khác: service đã lọc booking và driver theo provider.
- **Nguyên nhân:** `booking/dto/response/DriverCandidateResponse.java:6` chứa phone/licenseNo; `AssignmentAvailabilityResponse.java:5` chứa current/conflicting/next booking và thời gian; `DriverAvailabilityServiceImpl.java:103` điền các trường; `DriverOptimizationServiceImpl.java:522` còn nối booking code vào lý do. UI `components/provider/AssignmentDriverStatus.jsx:42` và `modals/AssignDriverModal.jsx:621` hiển thị chúng. `ProviderBookingServiceImpl.java:546` kiểm tra provider/booking trước khi gọi candidate.
- **API:** `GET /api/v1/provider/bookings/{id}/assign-candidates` và alias `/api/provider/bookings`, `/api/v1/provider/trips` theo `ProviderBookingController.java:52,204`.
- **Kịch bản:** provider sở hữu booking có driver có lịch xung đột; gọi candidate. **Mong đợi:** ID, tên, eligibility, trạng thái và reason code đủ quyết định. **Thực tế theo DTO/mapping:** response bao gồm PII và thông tin booking khác; chưa chụp HTTP response trong phiên này.
- **Sửa tối thiểu:** DTO riêng cho assignment với reason code tổng quát, bỏ phone/licenseNo và mã/thời gian booking khác; rà soát chuỗi lý do và UI, không chỉ ẩn JSX. Giữ thông tin cần thiết trong API quản trị tài xế được phân quyền riêng.
- **Regression:** assert JSON không chứa các trường nhạy cảm hoặc booking code trong text; provider A/B, renter, driver và các alias; kiểm tra candidate vẫn dùng được và eligibility không đổi.

## D. Pareto fix plan

**Fix immediately:** F01 kiểm tra principal/ownership tại service và thu hẹp security matcher; bổ sung authorization HTTP tests trước khi triển khai. F02 dùng DTO tối thiểu và sửa màn điều phối cùng lúc.

**Fix before merge:** chạy lại backend với dependency đọc được; chạy bộ PostgreSQL cô lập có migration, cạnh tranh manual/auto, confirm/timeout/reassign, IPN và refund; chạy HTTP privacy/IDOR nhiều actor; kiểm tra đồng bộ ba actor. Sửa lỗi tái hiện được theo kết quả, không suy đoán.

**Improve later:** rà soát N+1, UX và log theo bằng chứng đo được; không ưu tiên hơn quyền và ledger.

## E. Truy vết và checklist từng task

- **O07:** controller có GET queue/paged/summary/export (`ProviderBookingController.java:52–80,192`). Cần chạy lọc múi giờ, cùng bộ lọc export, phân trang ổn định, provider khác và alias.
- **O08:** `DriverOptimizationServiceImpl.java:199–226,283–302` khóa booking rồi driver và đánh giá lại; availability dùng overlap, buffer, tình trạng quá giờ và lịch (`DriverAvailabilityServiceImpl.java:55–156`). Test nguồn `AssignmentAvailabilityTest` có case đang chạy hôm nay nhưng chuyến tương lai không trùng, buffer, thiếu duration; chưa chạy trong phiên. Cần kiểm tra tranh driver, manual/auto, timeout cũ, reject/reassignment, GPLX và no candidate trên PostgreSQL. `DriverSelectionServiceImpl.java:36–62` gọi native CP-SAT, truyền timeout, phân biệt solver status và fallback trên danh sách eligible; `NativeDriverSelectionTest` có hai ứng viên nhưng chưa chạy thành công, nên chưa chứng minh native load trong môi trường hiện tại.
- **O11:** `RefundServiceImpl.java:75–83,132–140,213–220` có transaction và lock; FE `tv5Contracts.js:3–7` gửi `{action,rejectionReason}` đúng DTO, do đó lỗi hợp đồng `{approved,note}` cũ không còn trong mã được xem. `ProviderFinanceServiceImpl.java:533,584–596` tạo payout PENDING/MOCK_SIMULATED và FE `ProviderFinancePage.jsx:1188` gắn nhãn chưa chuyển ngân hàng. Cần chạy approve/proof đồng thời, refund vượt hạn mức, duplicate bank ref, evidence và đối chiếu ledger.
- **VNPAY:** `VnpayServiceImpl.java:359–384,601–650` có đường IPN khóa booking và callback read-only, QueryDR có writer riêng; migration `20261029_vnpay_verification.sql:11` có unique reference. Cần kiểm checksum/merchant/amount/status, duplicate/concurrent IPN, callback trước/sau IPN, expired, QueryDR signed và refresh bằng backend thật. Không kết luận thành công chỉ từ return URL.
- **D01/D02:** `DriverTripServiceImpl.java:175,304,321,356,402,427,620` có confirm/reject, arrive, start/finish và timestamps; cần xác minh HTTP IDOR, token driver cũ, deadline/timeout, xác nhận hai actor đồng thời, hủy, offline/GPS và DB thực.
- **Đồng bộ:** `useRealtimeSync.js` và `VnpayDepositSynchronizationIntegrationTest` có cơ chế/test nguồn cho after-commit và refetch; chưa chạy lại bộ đó thành công trong phiên. Cần browser ba actor với backend/DB thật, stale response, reconnect và refund.

## F. Test evidence và giới hạn

| Command / môi trường | Kết quả trong phiên này | Giới hạn |
|---|---|---|
| `npm.cmd test`, Node frontend local | **31 pass, 0 fail** | Unit test, không chứng minh backend hay HTTP thật; output trực tiếp phiên audit |
| `mvnw.cmd -Dtest=... test` | **Không chạy**: wrapper báo `Cannot index into a null array` | Không có test nào được thực thi |
| Maven 3.9.16 offline với local repository và 6 suite backend được chọn | **BUILD FAILURE** tại `testCompile`, báo lỗi truy cập `spring-boot-jdbc-test-4.1.1.jar` | Không có test nào được thực thi; không suy đoán nguyên nhân sâu hơn |
| PostgreSQL concurrency | **Không chạy** | Test cấu hình `create-drop` và chỉ chấp nhận DB local `tv5_test`; không dùng DB shared. Chưa tạo cluster riêng trong phiên |
| Fullstack E2E / multi-account HTTP | **Không chạy** | Không xác nhận JSON runtime, quyền hay đồng bộ ba actor |
| VNPay sandbox | **Không chạy** | Cần merchant sandbox, callback HTTPS công khai đã đăng ký, transaction reference và log/DB tương ứng |

Các báo cáo trước trong `docs/TV5_*`, `docs/DRIVER_ASSIGNMENT_AVAILABILITY_REPORT.md`, `docs/VNPAY_DEPOSIT_SYNC_P0.md` là nguồn ngữ cảnh, không được tính là kiểm thử đã chạy trong phiên này. Kiểm toán này là **ưu tiên rủi ro có giới hạn**, chưa đọc/xác thực toàn bộ nhánh, migration, endpoint alias và tài liệu; không thay thế full audit được yêu cầu.

## F. Final verdict

**INSUFFICIENT EVIDENCE.** Hai finding có căn cứ mã nguồn cần sửa và kiểm thử; chưa có kết quả backend, PostgreSQL, security HTTP, E2E thật hoặc sandbox trong phiên. Sau khi xử lý F01/F02 và chạy các kiểm thử trên, đánh giá lại khả năng merge theo kết quả thực tế.

## Cập nhật targeted fix — 04/11/2026

F01 đã được sửa bằng authentication cho `/query` trên bốn alias và kiểm tra ownership tại service; IPN/callback vẫn public. F02 được giảm dữ liệu bằng giới hạn serialization candidate/availability và available-driver, bỏ mã booking trong lý do và trên giao diện. Xem [TV5_TARGETED_FIX_REPORT.md](TV5_TARGETED_FIX_REPORT.md) để biết contract, kiểm thử và giới hạn. Bộ backend H2 đầy đủ chạy sau sửa: 212 test, 0 lỗi, 20 skip; PostgreSQL cô lập 20/20; không phải xác thực sandbox VNPAY thật. Verdict giữ **INSUFFICIENT EVIDENCE** đến khi kiểm thử sandbox thật và fullstack ba actor với backend/database thật.
