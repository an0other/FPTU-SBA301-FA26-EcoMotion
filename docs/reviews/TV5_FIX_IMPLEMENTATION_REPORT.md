# Báo cáo triển khai sửa lỗi TV5

Ngày: 29/10/2026, Asia/Ho_Chi_Minh. Repository `FPTU-SBA301-FA26-EcoMotion`, nhánh `task-member-5`, HEAD giữ nguyên `64abdef06b289c5e4f685b6c25d1494ac95254a2`.

Đã sửa Backend/Frontend và kiểm chứng: **90 kiểm tra backend đạt**, gồm **17 kiểm tra tích hợp/race trên PostgreSQL riêng** và kiểm tra **CP-SAT native thật**; **86 kiểm tra Node đạt**, **5 kiểm tra Chrome headless đạt**, frontend production build đạt. Browser dùng API giả lập; không gọi đây là kiểm thử E2E toàn bộ hệ thống thật.

## 1. Căn cứ, phạm vi và bảo toàn dữ liệu

- Phạm vi chính: O07/O08/O11/D01/D02 theo `docs/PHAN_CONG_6_THANH_VIEN.md`, đối chiếu spec `docs/SBA301_FALL26.md`, `AGENT_CONTEXT.md`, quy tắc `Backend/EcoMotion/AGENT_CODE_STRUCTURE.md` và DTO/service hiện tại.
- Working tree đầu phiên sạch; không checkout/reset/commit/push/PR. Không tìm thấy AGENTS.md trong repository và các thư mục cha đã kiểm tra.
- Tệp `docs/reviews/TV5_OR_TOOLS_BUTTON_API_AUDIT.md` không có trên filesystem ở đầu phiên; đã tìm theo tên và dùng đầy đủ nội dung báo cáo người dùng cung cấp trong IDE làm đầu vào, xác minh lại bằng source.
- Không sửa pom/dependency hoặc kết nối DB phát triển. Test PostgreSQL dùng container mới `ecomotion-tv5-audit-tests`, image có sẵn `postgres:17-alpine`, cổng localhost ngẫu nhiên, database riêng `tv5_test`, không mount volume của dự án, profile `test`, không chạy seed `dev`.
- Sau kiểm chứng đã dừng container test; Docker xác nhận không còn container mang tên này. Không dừng/xóa container hoặc database khác.
- Không tạo bảng/entity/migration mới. Các DTO, interface, implementation và test mới không phải bảng dữ liệu. `create-drop` chỉ chạy trên database của container test.
- Giao tiếp feature tiếp tục qua service: finance dùng `BookingPaymentLedgerService`/`BookingQueryService` để khóa ledger; không đưa repository booking sang finance. Repository booking bên trong service booking là truy cập nội bộ feature hợp lệ.

## 2. Vấn đề → nguyên nhân → bản sửa

Đường dẫn dưới đây tương đối từ repository; `BE` là `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion`, `FE` là `Frontend/EcoMotion/src`.

| Vấn đề | Nguyên nhân | Cách sửa và file chính |
|---|---|---|
| O11 không duyệt được hoàn tiền | FE chờ PENDING và gửi approved/note khác DTO | `FE/utils/tv5Contracts.js`, `pages/ProviderFinancePage.jsx`: REQUESTED/UNDER_REVIEW, gửi action/rejectionReason, trim/max 500, từ chối bắt buộc lý do; submit/đổi quyết định bị khóa khi đang gửi. APPROVE không gửi lý do giả. |
| Duyệt bị hiểu là đã hoàn tiền | Thiếu thao tác hoàn tiền và nhãn sai enum | Nhãn PROCESSING là chờ chuyển khoản/biên lai; thêm `FE/components/payments/RefundProofModal.jsx` dùng API/client/file storage sẵn có. Chỉ hiển thị REFUNDED sau response thành công và refetch. |
| Duyệt/biên lai lặp có nguy cơ ghi tiền lặp | Không khóa refund/transaction theo ID | `BE/finance/repository/RefundRequestRepository.java`, `RefundServiceImpl.java`, booking ledger service/repository: khóa refund lúc review, khóa transaction lúc xác nhận proof; chống yêu cầu hoàn trùng cùng nội dung, kiểm tổng tiền còn được hoàn dưới khóa booking. |
| Upload lưu file rồi transaction thất bại | File không tự rollback theo DB | Kiểm reference/note trước store; đăng ký xóa file nếu transaction rollback, dùng `FileStorageService` cũ. Không thêm hệ thống lưu file khác. |
| O08 resolve incident luôn sai validation | resolutionNotes/newStatus khác resolutionNote/status | `FE/pages/ProviderBookingDetailPage.jsx`, `tv5Contracts.js`; ghi chú trim/giới hạn 1000; refresh incidents/detail/tracking sau thành công. Backend phát INCIDENT_RESOLVED qua service notification. Không sửa incident khác. |
| Assignment cũ vẫn pending | Mỗi lần gán tạo row mới mà không thu hồi row cũ | `DriverOptimizationServiceImpl`, `ProviderBookingServiceImpl`, `DriverAssignmentRepository`: recall các PENDING_RESPONSE/ACCEPTED trước bản mới trong cùng transaction, giữ lịch sử RECALLED; nhận diện current bằng driver/assignedAt/expiresAt và ID. |
| Timeout cũ tác động assignment mới | Scheduler dùng snapshot trước khóa | Query ID booking hết hạn; khóa booking trước, đọc lại assignment, đối chiếu current và deadline; bản cũ chuyển RECALLED, không trigger reassign booking mới. |
| Auto bấm lặp tạo row | Thiếu nhánh idempotent | Phân công còn hiệu lực, không yêu cầu thay thế: auto trả ALREADY_ASSIGNED, không tạo row/deadline mới. Manual lặp không tạo thêm row; API vẫn chặn transition không hợp lệ. |
| Driver cũ/không còn đủ điều kiện nhận chuyến | Chỉ kiểm booking state và deadline | `DriverTripServiceImpl`: khóa booking theo ID/code trước khi đọc state; kiểm owner hiện hành, assignment hiện hành, deadline, account/activity/GPLX/lịch qua evaluator sau khóa driver. Driver thay thế không xác nhận assignment cũ được. |
| Nút FE lệch trạng thái BE | Rule UI quá rộng | `ProviderBookingDetailResponse` thêm canAssignDriver/canAutoAssignDriver, P và modal dùng capability. Q chỉ gán nhanh khi CONFIRMED/chưa có driver. Backend vẫn kiểm status/owner/eligibility; suggestion cũng kiểm stage. |
| Renter giữ state cũ, SSE lệch tên | Detail chỉ tải một lần, polling tắt khi SSE sống | `useRealtimeSync`, hook Provider/Driver và `pages/booking/BookingDetailPage.jsx`: event đúng tên, focus/online/visibility, EventSource tự reconnect; safety polling 15 giây vẫn chạy khi SSE sống. Sequence loại response cũ; cleanup khi đổi route/unmount. |
| Hiện trip đã chạy/hoàn thành quá sớm | Toast không phân biệt trạng thái chờ | `DriverTripDetailPage`: chờ renter start được nói rõ; finish chỉ ghi nhận kết thúc, còn hai phía/quyết toán; thanh toán cập nhật không mặc định đã đủ tiền. |
| Ngày O07 lệch gần nửa đêm | Cắt ngày UTC bằng slice | Filter backend chuyển Instant sang ngày VN; UI giờ đón dùng Intl với Asia/Ho_Chi_Minh. Khoảng ngày inclusive theo ngày VN, hỗ trợ một đầu và chặn khoảng ngược/ngày sai. |
| O07 sort/export khác nhau | Client filter/page riêng, export chỉ status | Dùng GET /paged; server filter/sort toàn bộ tập trước cắt trang; export dùng cùng hàm chọn và thứ tự, xuất toàn bộ kết quả phù hợp. NEWEST = coalesce(updatedAt,createdAt) DESC, id DESC. |
| Bộ lọc xe chỉ biết xe trong một trang | Chuyển paging khiến danh sách option không đầy đủ | Summary thêm vehicleOptions lấy distinct từ booking thuộc Provider; không lấy option từ trang hiện tại. Counts CONFIRMED/COMPLETED khớp trạng thái tab, không tính đơn chờ phụ phí thành hoàn tất. |
| Native OR-Tools chưa được chứng minh chạy | Tiến trình sandbox không đọc được JAR Maven | Đã xác minh bằng test native thực trong tiến trình được cấp quyền đọc cache; không đổi dependency, không tắt bảo mật/không chạy IDE bằng Administrator. Chi tiết §5. |
| Config/ranking khác solver | Properties không bind; hai hàm điểm khác nhau | `booking/config/OptimizationProperties`, `DriverSelectionService`/`implement/DriverSelectionServiceImpl`: config có validation, objective chung cho solver/ranking/fallback; trạng thái fallback rõ ràng. |
| Workload/GPS/GPLX chưa đúng thời gian booking | Dùng ngày hiện tại và vị trí không xét độ mới | Workload ngày đón VN; xét cả receivedAt/recordedAt của GPS, fallback depot có nguồn, không có thì ETA/distance null; ưu tiên tọa độ pickup đã lưu. GPLX đến ngày cuối khoảng thuê, end-exclusive. |
| Backend tin amount phụ phí | Lưu trực tiếp amount/unitPrice/rate từ client | `ProviderBookingServiceImpl.addCharge`: snapshot quyết định giá/tỷ lệ, server tính BigDecimal/HALF_UP 2 số lẻ; kiểm stage, quyền, loại, quantity/giới hạn, lý do và URL bằng chứng. Lặp cùng nội dung không thêm row. |
| Lý do deny bị bỏ | FE bắt nhập nhưng BE set null | Lưu văn bản vào trường operationalResolutionReason hiện có khi REJECTED; trả rejectionReason qua Provider/Renter detail và hiển thị. Không ghi text vào enum cancelReason, không thêm bảng. |
| IPN lặp/browser return có thể ghi tiền | Lookup/check PAID chưa khóa; callback gọi IPN | Khóa transaction theo txnRef; số tiền cents bắt buộc khớp chính xác; callback không ghi ledger, IPN mới xác nhận tiền. Unit gateway và race IPN test tách riêng, không gọi mạng VNPAY. |

## 3. Nút → API sau sửa

Base mặc định của Axios vẫn `/api`; Vite giữ nguyên path khi proxy. Các URL dưới đây đã ghép prefix; không tự thêm `/v1` vào Provider booking routes.

| Màn hình/thao tác | HTTP và input | Kết quả và cập nhật |
|---|---|---|
| O07 mở/lọc/tìm/sort/đổi trang | GET `/api/provider/bookings/paged?status=...&search=...&vehicle=...&driver=...&dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD&sort=NEWEST&offset=0&limit=10` | PageResponse items/total/offset/limit/hasMore. FE không lọc/sort lại trang. |
| O07 summary và option xe | GET `/api/provider/bookings/summary` | Counts theo trạng thái thực, vehicleOptions đầy đủ của Provider. |
| O07 CSV | GET `/api/provider/bookings/export` với cùng status/search/vehicle/driver/dateFrom/dateTo/sort | Toàn bộ kết quả phù hợp theo cùng thứ tự, không chỉ trang đang xem. |
| O08 mở điều phối | Chỉ mở modal; sau đó GET `/api/provider/bookings/{code}/assign-candidates` | Danh sách eligible/availability, chưa chạy solver/chưa lưu. |
| O08 gợi ý | POST `/api/provider/bookings/{code}/assign-suggest`, không body | suggestedDriver/candidates/solverStatus/explanation; chưa lưu. |
| O08 xác nhận tay/ứng viên | PUT `/api/provider/bookings/{code}/driver`, `{driverId,mode:"MANUAL"}` | DRIVER_ASSIGNED + PENDING_RESPONSE, chờ Driver; refetch detail/candidates. |
| O08 full auto | POST `/api/provider/bookings/{code}/auto-assign` | **Response mới:** `data: {booking: ProviderBookingResponse, solverStatus}`. Client chuẩn hóa lại object booking + solverStatus cho modal. ALREADY_ASSIGNED cho request lặp hợp lệ. |
| O08 đóng modal | Không API | Chỉ đóng cửa sổ; không nói đã hủy assignment đã lưu. |
| O08 xử lý sự cố | POST `/api/provider/bookings/{code}/incidents/{id}/resolve`, `{resolutionNote,status:"RESOLVED"}` | Cập nhật đúng incident, phát event sau commit, refetch dữ liệu liên quan. |
| O08 thêm phụ phí | POST `/api/provider/bookings/{code}/charges`, `{chargeType,description,quantity,unitPrice,appliedRate,amount,evidenceUrl}` | Giữ DTO cũ; server tính lại các trường tiền từ snapshot, không tin giá/amount client. Modal hiển thị công thức, không cho chỉnh tiền tùy ý. |
| O08 từ chối booking | POST `/api/provider/bookings/{code}/deny`, `{reason}` | REJECTED; Provider/Renter detail có rejectionReason. |
| O11 mở tab hoàn tiền | GET `/api/v1/provider/finance/refunds` | REQUESTED/UNDER_REVIEW có nút xem xét; PROCESSING có nút biên lai. |
| O11 duyệt/từ chối | POST `/api/v1/provider/finance/refunds/{id}/review`, APPROVE `{action:"APPROVE"}` hoặc REJECT `{action:"REJECT",rejectionReason}` | APPROVE chỉ PROCESSING + ledger REFUND PENDING; REJECT là REJECTED. Không nói đã hoàn tiền khi mới duyệt. |
| O11 tải biên lai | POST `/api/v1/payment-transactions/{id}/refund-proof`, multipart `file,bankReference,note` | Backend kiểm quyền/type/state/file/reference; sau thành công ledger PAID, refund REFUNDED; UI refetch. |
| D01/D02 nhận/từ chối | POST `/api/v1/driver/trips/{id-or-code}/confirm` hoặc `/reject` với `{rejectionReason,rejectionNote}` | Kiểm current/owner/deadline và transition; không tạo assignment lịch sử thay cho bản hiện hành bị thiếu. |
| D02 arrive/start/finish | POST `/api/v1/driver/trips/{id-or-code}/{arrive|start|finish}` | Giữ quy trình hai phía; timestamps do backend. Chờ end vẫn chiếm tài xế. |
| Renter cập nhật chi tiết | GET `/api/renter/bookings/{code}` và operational-risk; GET tracking khi stage cho phép | Detail mới điều khiển status/CTA/tracking, focus/poll/SSE không phụ thuộc trạng thái detail lúc mở trang. |

Các legacy alias vẫn có tại Controller như trước. Response full auto thay đổi có chủ đích để truyền trạng thái solver thật; caller trong repository đã được cập nhật. Client ngoài repository cần đọc wrapper mới này.

## 4. Assignment/reassign/timeout và đồng bộ

### 4.1. Quy tắc assignment

1. Booking lock trước khi đọc transition; manual/auto lấy driver lock trước đánh giá lần cuối/lưu. Driver confirm/reject/arrive/start/finish lấy booking lock theo ID/code và kiểm lại ownership.
2. Current assignment: driver, assignedAt và expiresAt khớp booking; khi lịch sử trùng cùng snapshot thì ID lớn nhất là current. Không lấy Optional một row từ tập nhiều pending như trước.
3. Gán lại hợp lệ thu hồi PENDING_RESPONSE/ACCEPTED cũ thành RECALLED trong transaction trước khi thêm row mới; không xóa lịch sử. Bản đã REJECTED/TIMEOUT giữ nguyên lịch sử phản hồi.
4. Auto request lặp khi còn assignment hiệu lực và không cần replacement trả kết quả hiện có, không gia hạn deadline. Manual lặp không tạo thêm assignment; HTTP guard vẫn có thể trả conflict khi stage đã thay đổi.
5. Scheduler lấy booking IDs, khóa booking rồi đọc lại assignment; chỉ timeout current nếu deadline đã tới. Bản expired cũ không khớp bị recall, không hủy driver/deadline của current. Deadline bằng now được coi hết hạn.
6. Driver REJECTED/TIMEOUT không được chọn lại trong toàn bộ vòng đời booking. Retry tự động sau reject/timeout/sự cố trước chuyến giới hạn bởi maxReassignRetries, mặc định 3; Provider có thể chủ động gán một người hợp lệ khác sau khi hết lượt tự động. RoundNumber là thông tin lịch sử, không phải khóa duy nhất/current ID.
7. Khi tự chọn người thay thế từ retry, các driver cũ/mới được khóa theo ID tăng dần trước lưu để giảm vòng chờ ngược. Các đường gán thông thường chỉ khóa driver được chọn. Driver cũ nhận event sau commit khi assignment bị recall.
8. Pending giữ lịch đến deadline; IN_PROGRESS/AWAITING_END_CONFIRMATION chưa kết thúc hợp lệ tiếp tục chặn lịch, kể cả quá giờ dự kiến. Buffer giữ policy giao khoảng trước đó, không nhân đôi.
9. Profile APPROVED, User ACTIVE, activity AVAILABLE, GPLX/hạng xe, incident và lịch được kiểm lại lúc gán/Driver nhận. GPLX phải còn hạn qua ngày cuối của khoảng dự kiến; điểm kết thúc đúng nửa đêm thuộc ngày trước vì khoảng thuê end-exclusive.

Đã giữ @Version/pessimistic locks hiện có, bổ sung query khóa cho refund/ledger. Không thêm constraint/migration vì các đường ghi được sửa giữ khóa và có kiểm chứng race cô lập; không tuyên bố bảo vệ được mọi writer ngoài service của ứng dụng.

### 4.2. Event và cập nhật UI

- Mở rộng `BookingNotificationService` hiện có, giữ nguyên email/SMS cũ, bổ sung changed gửi Provider/Driver/Renter qua `NotificationStreamService` sau commit.
- Có BOOKING_CHANGED cho thay đổi chung, DRIVER_ASSIGNED/DRIVER_CONFIRMED/DRIVER_ARRIVED và các event lifecycle sẵn có; INCIDENT_RESOLVED/CHARGES_CHANGED/PAYMENT_VERIFIED/REFUND_UPDATED cho phần tích hợp. Listener không còn dựa riêng DRIVER_ACCEPTED khi backend phát DRIVER_CONFIRMED.
- Safety polling 15 giây vẫn hoạt động khi SSE connected. EventSource giữ cơ chế reconnect chuẩn thay vì đóng hẳn trên onerror. Focus, online và visibility cũng resync.
- Hook sequence bỏ response cũ, vô hiệu hóa request đang bay khi unmount/đổi booking. Renter detail bắt đầu resync ngay từ DRIVER_ASSIGNED/DRIVER_CONFIRMED, sau đó mới bật tracking theo state server mới.
- Refetch đồng bộ được gom; bỏ các timer Provider cũ 5/8 giây trùng với hook chung và bỏ refetch trùng trong handler + onResync. Tracking GPS vẫn là luồng riêng với chu kỳ riêng phục vụ bản đồ.
- UI phân biệt gợi ý chưa lưu, assignment chờ phản hồi, Driver nhận, chờ hai phía start/end và quyết toán. Kết thúc vận hành không được dùng làm bằng chứng tiền đã hoàn tất.

## 5. OR-Tools chạy thật, cấu hình và fallback

### 5.1. Nguyên nhân native và kiểm chứng

Không thiếu dependency: OR-Tools 9.11.4210 và native Windows JAR đã có trong Maven cache. Lần audit trước, tiến trình sandbox không đọc được JAR, cùng môi trường này còn chặn JAR Spring khi compile. Chạy Maven offline bằng tiến trình được cấp quyền đọc cache đã gọi được JNI/CpSolver thật. Không sửa ACL hệ thống, tắt kiểm soát bảo mật hoặc yêu cầu nâng quyền toàn IDE.

`NativeDriverSelectionTest.nativeCpSatSelectsFromTwoEligibleCandidates` có hai người eligible, gọi implementation thật, assert chính xác OPTIMAL và driver được chọn; nếu fallback thì test fail. Test thứ hai thay trọng số để chứng minh config thực sự thay objective. Có kiểm tra MODEL_INVALID → FALLBACK_MODEL_INVALID và không chọn người ineligible, đồng thời kiểm Bean Validation bác cấu hình âm.

Runtime kiểm tra là Java 25.0.4, javac release 21; dependency/Java target 21 của dự án giữ nguyên. Java 25 cảnh báo native-access/Unsafe, không phải thất bại solver. Chưa chạy trên một bản cài JDK 21 riêng trong phiên này.

### 5.2. Cấu hình có hiệu lực

`booking/config/OptimizationProperties.java`, bind prefix `app.optimization`, @Validated:

| Property | Default trong code | Validation/công dụng |
|---|---:|---|
| max-solver-time-seconds | 5 | >0, CpSolver time limit |
| assignment-timeout-minutes | 15 | >0, deadline dùng cả Provider/manual/auto |
| max-reassign-retries | 3 | >=0, lượt retry tự động |
| weight-eta | 10 | >0, trọng số ETA |
| weight-workload | 50 | >0, trọng số workload |
| weight-rating | 5 | >0, trọng số phần phạt rating |
| location-max-age-seconds | 60 | >0, cả recordedAt/receivedAt phải còn mới |
| unknown-eta-minutes | 30 | >0, giá trị phạt xếp hạng khi ETA không biết; DTO vẫn ETA null |

Template properties đã thêm các giá trị default; test properties dùng trọng số 50/200/150 sẵn có để kiểm bind trong application context. Không sửa file cấu hình runtime chứa credentials. `default-buffer-minutes` cũ đã bỏ khỏi test config; buffer nghiệp vụ tiếp tục lấy từ `Vehicle.maintenanceBufferMinutes`, không phải tham số chung của solver.

Objective chung: minimize `eta × weightEta + tripsOnPickupDay × weightWorkload + round((5−rating)×20) × weightRating`. Ranking dùng số đối của penalty, cùng tie-break driver ID; fallback cũng lấy min penalty. Tất cả chỉ trên một booking, không tối ưu toàn đội xe. ETA là Haversine/tốc độ giả định, không phải giao thông thật; GPS cũ không dùng như GPS hiện tại, depot được ghi là nguồn dự phòng, không có dữ liệu thì ETA/distance null, không gán tọa độ TP.HCM làm vị trí driver.

Trạng thái: OPTIMAL, FEASIBLE, INFEASIBLE khi không eligible, FALLBACK_UNKNOWN khi solver chưa có nghiệm, FALLBACK_MODEL_INVALID khi lỗi mô hình, FALLBACK_ERROR khi lỗi native/runtime; ALREADY_ASSIGNED cho request auto lặp. Lỗi mô hình/native được log, không đổi tên thành OPTIMAL. Fallback vẫn lọc eligible và đường ghi đánh giá lại dưới khóa. Full auto trả solverStatus ra UI thay vì toast “tối ưu thành công” bất kể cách chọn.

## 6. Tiền và phụ phí

- Các loại phụ phí hiện có đều tính theo công thức từ snapshot: OVERTIME = quantity × slotPrice × (1 + overtimeSurchargeRatio); WAITING = quantity × waitingRate; WEEKEND = quantity × slotPrice × weekendSurchargeRatio; OUT_OF_RADIUS = quantity × outOfRadiusRate.
- Provider ghi số lượng phát sinh có lý do/bằng chứng; không có loại phí tự do mới hoặc workflow duyệt phí mới. Quantity integer trong giới hạn 1..1.000.000; server kiểm stage AWAITING_EXTRA_CHARGE_CONFIRMATION + actualEndAt, cấm BASE_RENTAL/DEPOSIT/gói năm. BigDecimal làm tròn HALF_UP 2 số lẻ, precision phù hợp cột tiền. Replay cùng loại/quantity/description/số tiền server tính trả lại charge cũ dưới booking lock.
- Server quyết định unitPrice/appliedRate/amount; trường giá client giữ để tương thích DTO nhưng không là nguồn tính. UI chỉ cho chỉnh loại/số lượng/lý do/URL bằng chứng, xem công thức trước xác nhận.
- Số lượng các khoản ghi thủ công vẫn do Provider khai báo cùng bằng chứng; lần sửa này không xây mới bộ đo tự động thời gian chờ/km/chạy quá giờ và không tự tạo charge tự động từ GPS. Các trường hợp số lượng sai thực tế tiếp tục thuộc quy trình bằng chứng/tranh chấp hiện hành.
- Refund APPROVE chỉ tạo REFUND PENDING/PROCESSING. Người cung cấp chuyển khoản ngoài hệ thống và gửi biên lai qua cơ chế cũ; server mới chuyển PAID/REFUNDED. Test chứng minh hai approve/proof cạnh tranh không tạo hai ledger. Không gọi ngân hàng để tự chuyển tiền.
- VNPAY callback chỉ đọc/trả kết quả từ chữ ký và ledger hiện có; IPN mới thay đổi tiền sau verification/amount/state. Race test mock gateway verification, unit gateway kiểm các contract/chữ ký riêng; không thanh toán sandbox/thật.
- Payout vẫn mock local, có nhãn; không chuyển mock thành giao dịch tiền thật.

## 7. Kiểm chứng thực tế

### 7.1. Backend

Maven offline compile + test: **90 tests, failures 0, errors 0, skipped 0, BUILD SUCCESS**.

| Nhóm | Số test | Kết quả |
|---|---:|---|
| AssignmentAvailabilityTest | 12 | Buffer/biên mở/lịch tương lai/giữ slot pending/chuyến chưa kết thúc |
| DriverAssignmentEligibilityTest | 13 | Điều kiện mandatory, quyền, revalidate sau lock, timeout, GPLX qua nửa đêm |
| DriverTripServiceUnitTest | 15 | Guard ownership/transition và DTO trip |
| NativeDriverSelectionTest | 4 | Native ≥2 eligible, đổi weight, không eligible, fallback MODEL_INVALID + validation |
| Tv5PostgresConcurrencyTest | 17 | PostgreSQL thật, profile test, dữ liệu tự tạo; danh sách chi tiết dưới đây |
| ProviderFinanceServiceUnitTest | 15 | Hồi quy tài chính Provider |
| BookingNotificationServiceTest | 6 | Giữ email/SMS/fallback sau khi mở rộng notification service |
| BookingPickupQuoteTest | 2 | Hồi quy phần tích hợp booking bị chạm constructor/mock |
| PaymentGatewayStrategyUnitTest | 6 | Resolver, URL và verification contract/signature, không gửi HTTP ra VNPAY |

17 bài PostgreSQL gồm: manual/manual trùng lịch; manual/auto; hai auto cùng booking; stale timeout; reject/confirm; hai phía start/end; confirm hết hạn/timeout; approve và proof refund cạnh tranh/replay; filter ngày VN/CSV/sort; HTTP incident đúng/sai DTO và quyền Driver; phụ phí amount client bị ghi đè và chống trùng; HTTP refund đúng/sai DTO và lý do bắt buộc; IPN replay/callback không ghi tiền; Provider capability/deadline/auto idempotent; replacement thu hồi bản cũ và chặn Driver cũ; workload ngày tương lai/account offline lúc confirm; GPS cũ/mới.

### 7.2. Frontend

- `npm.cmd test`: **86/86 đạt** (12 suites), gồm helper contract dùng trực tiếp trong form refund/incident và công thức preview charge.
- `npm.cmd run build`: **đạt**; còn cảnh báo bundle >500 kB, không phải lỗi build.
- ESLint trên toàn bộ source đã sửa: **0 errors, 8 warnings** về đọc ref sequence khi cleanup. Không disable rule. Full-repository lint trước đó có lỗi ở AppHeader/ProviderHeader/admin và component ngoài thay đổi; không mở rộng sửa toàn dự án.
- Chrome headless: **5/5 đạt**: modal availability desktop 1280px và mobile 390px, Driver confirm/arrive/start chờ Renter, Renter focus cập nhật từ DRIVER_CONFIRMED và bỏ response chậm, refund APPROVE đúng DTO → proof multipart → chỉ hiển thị hoàn sau backend response.
- `git diff --check` cuối phiên trả 0. Các dấu cách cuối dòng sẵn có trong AGENT_CONTEXT là hard-break Markdown cũ, được giữ nguyên.
- Các bài browser chặn API bằng fixture cô lập. Không dùng kết quả đó để khẳng định UI đã chạy liên thông với PostgreSQL/gateway thật. MockMvc/PostgreSQL là lớp kiểm chứng backend riêng.

### 7.3. Lệnh và artifact

```powershell
& '<Maven>/bin/mvn.cmd' -o '-Dmaven.repo.local=<existing-maven-cache>' `
  '-Dtest=NativeDriverSelectionTest,AssignmentAvailabilityTest,DriverAssignmentEligibilityTest,DriverTripServiceUnitTest,ProviderFinanceServiceUnitTest,Tv5PostgresConcurrencyTest,BookingNotificationServiceTest,BookingPickupQuoteTest,PaymentGatewayStrategyUnitTest' `
  '-Dtv5.postgres=true' '-Dtv5.postgres.url=jdbc:postgresql://127.0.0.1:<test-port>/tv5_test' test

npm.cmd test
npm.cmd run build
node_modules/.bin/playwright.cmd test e2e/assignment-availability.spec.js e2e/tv5-sync-refund.spec.js --workers=1
```

Chỉ bật `tv5.postgres=true` cho container riêng: test từ chối URL không phải localhost và database `tv5_test`. Không dùng profile dev hoặc sửa connection của ứng dụng chính. Container dùng image đã có, không tải dependency/image mới.

Artifact local: `Backend/EcoMotion/target/tv5-postgres.log`, Surefire reports; `Frontend/EcoMotion/{test-tv5,build-tv5,lint-changed-tv5,browser-tv5}.log`. Không commit artifact. Những lần compile/test trung gian lỗi đã được sửa trước kết quả cuối; không tính một lần test fail là pass. Warning PowerShell NativeCommandError do chương trình ghi warning vào stderr được đối chiếu với kết quả Maven/Vite/Playwright thực tế ở cuối log.

## 8. File ngoài phạm vi màn TV5 và giới hạn còn lại

Phần tích hợp tối thiểu ngoài UI TV5:

- `FE/pages/booking/BookingDetailPage.jsx`: resync Renter, cảnh báo dữ liệu cũ và hiển thị rejectionReason.
- `BE/booking/service/implement/BookingServiceImpl.java`: lock start/end hai phía, notification; `BookingMapper.java`, `BookingDetailResponse.java`: lý do từ chối trả cho Renter.
- `BE/finance/service/implement/{RefundServiceImpl,VnpayServiceImpl,PaymentRequestServiceImpl}.java`: khóa/refund validation/IPN và event tích hợp; không thiết kế lại payment request/gateway.
- Booking ledger/query/notification service hiện có được mở rộng, giữ interface và ownership feature. Không sửa quản lý đội xe/tài xế O04/O06 hoặc admin portal.

Chưa kiểm chứng bằng một browser session chạy liên thông toàn bộ FE + backend + PostgreSQL: browser fixtures và backend integration test được chạy riêng. Chưa thử GPS thiết bị thật, độ trễ/mất mạng kéo dài, Cloudinary thật, VNPAY sandbox ngoài mạng, Java 21 runtime riêng hoặc tải dữ liệu lớn. Các trường hợp này không được coi đã đạt E2E chỉ nhờ unit test.

Lọc/sort O07 hiện chạy ở service trên toàn bộ tập booking đã truy vấn theo Provider/status, rồi mới cắt PageResponse; đảm bảo đúng kết quả/paging/export, nhưng chưa tối ưu push toàn bộ predicate/order xuống database cho tập rất lớn. Đây là giới hạn hiệu năng đã biết, không phải sort một trang client. Không thêm native SQL.

Lý do deny tái sử dụng operationalResolutionReason và chỉ trả rejectionReason khi REJECTED. Không ghi lẫn nội dung vào cancelReason enum. Nếu mở rộng nhiều loại ghi chú vận hành sau này cần xét lại mô hình, không tạo bảng mới trong lần này.

Các race đã thử dùng hai request/transaction độc lập trên PostgreSQL thật; không phải chứng minh hình thức mọi thứ tự mọi số lượng request. Không chạy toàn bộ integration suite cũ vì nhiều lớp còn @ActiveProfiles("dev") phụ thuộc seed. Không có migration chạy trên database dùng chung.

## 9. Test thủ công ngắn

1. Chuẩn bị môi trường test riêng với Provider, ít nhất hai Driver APPROVED/ACTIVE/AVAILABLE, xe/gói/snapshot và booking đã xác nhận cọc. Không dùng tài khoản hoặc tiền thật.
2. Provider: O07 lọc ngày VN gần nửa đêm, đổi trang/sort và xuất CSV; kiểm CSV có toàn bộ kết quả, thứ tự khớp. Booking chưa cọc/đã hoàn tất không có nút gán không hợp lệ.
3. Provider: O08 mở danh sách → Gợi ý, kiểm chưa có assignment; xác nhận tay hoặc full auto → trạng thái chờ Driver. Bấm lại auto không tạo deadline/row thứ hai. Quan sát solverStatus/fallback, không mặc định mọi success là OPTIMAL.
4. Driver: mở D01/D02 nhận → đến đón → start. Renter đang mở từ DRIVER_CONFIRMED phải thấy CTA mới sau event/focus/poll; trước xác nhận của Renter chưa được gọi là IN_PROGRESS.
5. Driver/Renter: xác nhận start/end hai phía, kiểm actual timestamps server; AWAITING_END_CONFIRMATION vẫn chặn tài xế. Thử Driver cũ confirm sau replacement: bị chặn.
6. Provider: xử lý đúng một incident bằng ghi chú; kiểm incident khác nguyên trạng và candidates/detail được cập nhật. Thêm charge có bằng chứng; sửa amount/unitPrice trong request không đổi tiền server ngoài snapshot; gửi lại không thêm charge trùng.
7. Provider: O11 refund REQUESTED/UNDER_REVIEW → reject bắt buộc lý do hoặc approve chỉ PROCESSING. Sau chuyển khoản thử ngoài hệ thống, tải biên lai đúng loại/size/mã; chỉ sau server xác nhận mới REFUNDED. Gửi lại approve/proof không ghi tiền lần hai.
8. VNPAY: ở môi trường sandbox có cấu hình riêng, return URL không được làm ledger PAID; IPN hợp lệ/đúng amount mới được ghi. Không áp dụng thử nghiệm này với tiền thật.

Không commit/push/PR trong phiên này.
