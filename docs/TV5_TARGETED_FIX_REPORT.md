# TV5 targeted security fix — 04/11/2026

## Verdict

**INSUFFICIENT EVIDENCE.** Hai lỗi P1 đã sửa và được kiểm tra HTTP với backend/H2. PostgreSQL cô lập chạy các race test. Chưa có giao dịch VNPAY sandbox thật và browser fullstack ba actor với backend/database thật; không kết luận MERGE READY. Các thay đổi chưa commit có sẵn từ trước được giữ lại; kết quả áp dụng cho working tree hiện tại, không riêng HEAD. Không có migration mới.

## F01 — VNPAY query

**Nguyên nhân:** `SecurityConfig` mở toàn bộ nhánh VNPAY; controller `/query` không nhận principal, service chỉ đối chiếu booking code/reference. Bất kỳ ai có cặp này đều có thể đọc kết quả và kích hoạt QueryDR.

**Sửa:** `SecurityConfig.securityFilterChain` chỉ cho phép public các alias IPN và callback. `VnpayPaymentController.queryTransaction` chuyển principal tới `VnpayService.queryTransactionForUser`; `VnpayServiceImpl.queryTransactionForUser` tải booking, kiểm tra renter sở hữu, provider sở hữu hoặc admin, rồi gọi đường đối soát nội bộ. Scheduler tiếp tục dùng `queryTransaction` nội bộ; server mới giữ merchant credential. Return callback vẫn verify/read-only, IPN vẫn xác thực chữ ký và tự ghi nhận cọc. `VnpayCallbackResultResponse` không serialize số giao dịch VNPAY, ngân hàng, loại thẻ hoặc thời gian trả tiền; giữ code/reference/amount/status cần cho luồng hiện tại.

**Contract:** trước đây GET `/query` công khai; sau sửa yêu cầu token và quyền booking, sai reference/booking trả 404. Bốn alias `/api/v1/payment/vnpay`, `/api/payment/vnpay`, `/v1/payment/vnpay`, `/payment/vnpay` cùng policy. IPN GET/POST và callback vẫn public; không thay đổi cơ chế kiểm chứng gateway.

## F02 — Candidate

**Nguyên nhân:** DTO nội bộ được serialize trực tiếp, chứa số điện thoại, GPLX và các trường lịch của booking khác; lý do xung đột còn nối mã booking.

**Sửa:** `DriverCandidateResponse`, `AssignmentAvailabilityResponse`, `AvailableDriverResponse` bỏ các trường cá nhân/lịch khỏi JSON bằng `@JsonIgnore`; `DriverOptimizationServiceImpl.evaluateDriver` không đưa booking code vào lý do. Candidate không serialize lý do nội bộ có thể chứa GPLX; trường availability reason dùng mã chung `INELIGIBLE` khi không có reason code chuyên biệt. `DriverCandidateResponse.toString` không ghi PII ra log. `AssignmentDriverStatus`, modal provider và tìm kiếm frontend bỏ hiển thị/tìm bằng PII hoặc lịch khác. Thuật toán backend vẫn dùng dữ liệu đầy đủ để đánh giá eligibility, overlap và CP-SAT. Đây là giới hạn serialization của DTO nội bộ hiện có, chưa tách thành một lớp response độc lập; cần duy trì kiểm thử JSON khi bổ sung trường DTO mới.

**Contract:** JSON còn driverId, tên, eligibility, trạng thái/lý do tổng quát và thông tin đánh giá cần cho giao diện; không còn userId, phone, licenseNo/class/expiry, booking code của chuyến khác và các mốc xung đột. Suggestion dùng cùng DTO và không ghi booking xung đột trong reason. Endpoint available-drivers cũng ẩn dữ liệu tương ứng. Kiểm tra quyền provider trên booking vẫn nằm ở service hiện hữu.

## Test evidence

| Kiểm thử | Kết quả và phạm vi |
|---|---|
| Maven offline, toàn bộ backend `test` | 212 test, 0 failure/error, 20 skip (192 pass), H2 in-memory test profile; `Backend/EcoMotion/target/tv5-targeted-full.log`. Lần chạy trước khi bổ sung thêm actor driver vào security test; test được chạy lại riêng bên dưới. |
| `VnpayDepositSynchronizationIntegrationTest` | 16/16, H2 + MockMvc; `target/tv5-targeted-security.log`. Anonymous, renter/provider owner, renter/provider/driver khác, sai reference/booking, 4 alias query, IPN công khai ký bằng secret test; candidate JSON thật qua 3 alias, provider khác và renter bị chặn. Không phải VNPAY thật. |
| `Tv5PostgresConcurrencyTest` | 20/20, PostgreSQL 18, database `tv5_test` trong cụm riêng port 55489; `target/tv5-targeted-postgres.log`. Bao gồm hai booking tranh driver, manual/auto, auto đồng thời, confirm/timeout, assignment cũ, IPN đồng thời, refund approve/proof, hai actor start/end, candidate HTTP. Cụm đã dừng. Kết quả đầu tiên 19/20 do test mới dùng ID thay code; sửa fixture và chạy lại 20/20. |
| `npm.cmd test` | 31/31 pass. |
| `npm.cmd run build` | Pass; warning bundle lớn còn tồn tại. |
| ESLint ba file frontend thay đổi | Không lỗi/cảnh báo. |
| Playwright `e2e/vnpay-deposit-sync.spec.js` | 2/2 pass, dùng API/SSE test doubles; không phải fullstack thật. |

Compile thực tế thành công trong Maven test đầy đủ. Lần thử giới hạn sandbox ban đầu: wrapper không khởi động, Maven trỏ sai local repo; Maven trực tiếp cho stack trace `AccessDeniedException` tại `WindowsPath.toRealPath` đối với JAR, kể cả bản sao trong workspace. Chạy Maven với quyền ngoài sandbox và cache sẵn có đã compile/chạy được; Java 25.0.4 biên dịch target Java 21. Không xóa cache người dùng, không thay Java/dependency. Bộ test dùng datasource H2 trong `src/test/resources/application.properties`; PostgreSQL chỉ dùng cụm test riêng create-drop. Không chỉnh database chia sẻ.

## Files và giới hạn

Các thay đổi chính: `common/config/SecurityConfig.java`, `finance/controller/VnpayPaymentController.java`, `finance/service/VnpayService.java`, `finance/service/implement/VnpayServiceImpl.java`, `finance/dto/response/VnpayCallbackResultResponse.java`; `booking/dto/response/{DriverCandidateResponse,AssignmentAvailabilityResponse,AvailableDriverResponse}.java`, `booking/service/implement/DriverOptimizationServiceImpl.java`; frontend `components/provider/AssignmentDriverStatus.jsx`, `components/provider/modals/AssignDriverModal.jsx`, `utils/assignmentAvailability.js`. Test bổ sung/sửa: `VnpayDepositSynchronizationIntegrationTest`, `Tv5PostgresConcurrencyTest`, `DriverAssignmentEligibilityTest`. Báo cáo audit cũ được cập nhật. Không commit/push/PR.

Còn cần sandbox thật với HTTPS IPN đã đăng ký và merchant credentials hợp lệ; thực hiện giao dịch mới, lưu reference và đối chiếu IPN/ledger. Cần E2E thật renter/provider/driver qua backend và database, kiểm thử alias liên quan khác và hành vi reconnect/driver cũ toàn diện. Không khẳng định các race chưa được kiểm thử đều an toàn. Không tự động coi mock gateway hoặc browser doubles là bằng chứng ngân hàng đã chuyển tiền.
