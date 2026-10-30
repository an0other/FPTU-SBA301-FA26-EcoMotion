# TV5 — End-user workflow fixes

Ngày kiểm chứng: 30/10/2026, Asia/Ho_Chi_Minh. Branch `task-member-5`; giữ nguyên working tree chưa commit của nhóm. Không reset/stash/clean, push, PR hoặc merge. Báo cáo này bổ sung cho [audit trước](TV5_FINAL_AUDIT_AND_FIX_REPORT.md); không cộng lặp số test giữa các lượt chạy.

## 1. Lỗi và vấn đề UX phát hiện

| Vấn đề | Nguyên nhân | Sửa thực tế |
|---|---|---|
| Provider đổi xe/giờ trực tiếp | Operational resolution vừa chọn phương án vừa áp dụng | Lưu đề xuất; đúng Renter phải ACCEPT; kiểm tra lại và áp dụng cùng transaction |
| Không có tài xế nhưng không có phương án tiếp theo | Panel chỉ xuất hiện khi xe chuyến trước về trễ | Workflow xác định needsDriver; mở tìm lại, đề xuất xe/giờ, hủy và quy trình hoàn tiền |
| Driver đã nhận điều khoản cũ vẫn được giữ là đã xác nhận sau đổi lịch | Dùng chung assignment cũ | Thu hồi assignment; gửi yêu cầu mới; assignedAt tăng đơn điệu; tab cũ không nhận thay điều khoản mới |
| Renter không hiểu tiền/driver/trip/quyết toán | Chủ yếu suy diễn từ một booking status | Workflow API và UI thể hiện riêng năm nhóm trạng thái, tiền đã nhận/đã hoàn/đang hoàn và bước tiếp theo |
| Hold hết hạn được xem là trống khi VNPay còn chờ | Predicate occupied và job expiry chỉ xét expiresAt | Giữ occupied nếu có giao dịch VNPay PENDING; job không giải phóng nhầm |
| Provider duyệt nhưng xe chưa được giữ lịch | accept chỉ tạo PaymentRequest, còn hold đợi checkout | Khóa booking/xe; kiểm thời hạn và availability; giữ lịch 15 phút và chuyển AWAITING_DEPOSIT ngay trong transaction duyệt |
| Khách đóng tab thì giao dịch chờ khó được xác minh | Đối soát phụ thuộc người mở lại callback | Job bounded batch gọi QueryDR qua service/adapter đã kiểm chữ ký, amount và timeout |
| Capture thành công nhưng hold lỗi vẫn giống chờ nộp cọc | Không chuyển trạng thái vận hành rõ ràng | PAID/appliedAmount=0 giữ nguyên; deposit booking chuyển CANCELLATION_REVIEW, giải phóng slot và hướng dẫn đối soát; không retry thu tiền |
| Tiền cọc kích hoạt auto assign dù Provider chưa chọn | Callback luôn auto assign | Chỉ FULL_AUTO mới tự phân công sau capture; lưu MANUAL/AUTO_SUGGEST/FULL_AUTO đúng lựa chọn |
| Refund tự động sau hủy không có nút nộp biên lai | UI chỉ có proof cho RefundRequest | Thêm nút proof cho ledger REFUND/PENDING, dùng cùng API và modal hiện có |
| Hàng đợi Provider khó biết việc cần làm | Lọc theo trạng thái kỹ thuật; lọc/sort đầy đủ trong bộ nhớ | Bộ lọc action phía DB, trước pagination; CSV dùng cùng điều kiện; thêm CREATED_DESC |
| REQUESTS trên UI vẫn gửi TODAY | Serializer chưa nhận view mới | API/spec/serializer cùng hỗ trợ REQUESTS; không giới hạn chỉ ngày hiện tại |
| Gói giờ hiển thị như gói ngày; tài chính chưa xong bị hiểu đã xong | Nhãn rentalType và execution mapping thiếu | Nhãn theo gói; AWAITING_FINAL_PAYMENT có thông báo chờ quyết toán, không CTA hoàn tất tác nghiệp |
| Đổi xe rồi dời giờ lại hiển thị tên xe snapshot cũ | Mapper chỉ xét resolution cuối | Dùng previousVehicleId để tiếp tục hiển thị xe phục vụ thực tế; giữ snapshot giá |
| Phản hồi đọc cũ ghi đè trang mới | Các request song song không có sequence/key | Refetch sau mutation; route key và sequence loại bỏ phản hồi cũ |

## 2. File/method và phạm vi sửa

Backend mới: `BookingWorkflowController`, `BookingWorkflowService`/`BookingWorkflowServiceImpl`, `BookingWorkflowResponse`, `RespondOperationalProposalRequest`, embeddable `OperationalProposal`; `ProviderWorkflowSpecification`; `PaymentReconciliationService`/implementation, job và DTO target. Các lớp nằm trong module booking/finance, theo Controller → Service → Repository; không tạo bảng mới.

Backend cập nhật: `ProviderBookingController.resolveOperationalRisk` gửi đề xuất; `ProviderBookingServiceImpl.resolveOperationalRisk` kiểm consent; `acceptBooking` khóa booking/xe; assignment lưu mode và reissue khi đổi điều khoản; `DriverTripServiceImpl.confirmTrip` nhận snapshot assignedAt; DTO Driver có assignmentExpiresAt; `BookingSpecification` thêm REQUESTS và các bước xác nhận; `BookingMapper` giữ đúng xe thực tế sau nhiều thay đổi; `BookingHoldExpiryServiceImpl`, predicate occupied trong `VehicleScheduleRepository`, `VnpayServiceImpl`, query bounded pending transactions; `RefundService.refundableAmount`; `BookingQueueFilter` thêm action, giữ constructor cũ; `ProviderBookingResponse.actionRequired` là trường suy diễn.

Frontend mới: `BookingWorkflowSummary`, `bookingWorkflowService`, `bookingWorkflow` và `useDeadlineExpired`. Cập nhật detail Renter/Provider, operational panel, queue Provider, Finance ledger, Driver schedule/detail/card, API confirm và serializer. Dùng design system hiện tại, thông báo tiếng Việt, dialog xác nhận; không đưa mock vào dữ liệu ứng dụng.

Các sửa an toàn tài chính/availability từ audit trước được giữ. Không mở rộng optimizer thành tối ưu cả đội xe: OR-Tools hiện chọn một driver cho một booking; fallback chỉ dùng ứng viên hợp lệ.

## 3. Business rule trước và sau

- Đề xuất chưa ACCEPT không thay xe, giờ đón, tài xế hoặc giá của đơn. Không ghi đè đề xuất đang chờ. Provider có thể rút; Renter có thể từ chối. Khi nhận, xe/lịch/seat/license/driver availability được kiểm tra lại, kể cả dữ liệu thay đổi sau lúc gửi.
- UUID đề xuất chống gửi phản hồi cho đề xuất khác. Cùng phản hồi cùng actor là idempotent; phản hồi trái kết quả đã lưu trả conflict. Hai phản hồi đua nhau được tuần tự hóa bằng booking lock. Apply thất bại rollback cả consent và lịch, không tạo ACCEPTED giả.
- Đổi điều khoản yêu cầu Driver xác nhận lại. FE gửi assignedAt đã đọc. Backend từ chối snapshot cũ; caller cũ không có snapshot vẫn dùng được cho assignment thông thường, nhưng không được chấp nhận sau operational change đã đồng ý.
- Mốc xử lý hiển thị là giờ đón đã đặt, không tự tạo SLA hoặc hứa chắc tìm được driver. Không có ứng viên thì cho Provider phương án cụ thể và Renter quyền phản hồi.
- Payout tiếp tục theo quyết định người dùng: chỉ tiền nền tảng thu được gateway xác minh. Chuyển khoản trực tiếp Provider được tách riêng. Sandbox payout không có nghĩa đã chuyển tiền thật.
- REQUESTED refund đã giữ hạn mức nhưng chưa chuyển tiền. APPROVE tạo PROCESSING và ledger PENDING. Chỉ proof hợp lệ được actor có quyền xác nhận mới chuyển PAID/REFUNDED. Khóa, tổng reserve và unique bank reference từ audit trước tiếp tục bảo vệ concurrent/duplicate refund.
- Trip kết thúc cần xác nhận hai bên. Settlement còn tranh chấp thì bị chặn. Phụ phí được tính lại từ snapshot và quantity; không tin amount/unitPrice tự nhập. Đơn chỉ COMPLETED khi đủ state machine và tiền còn lại được giải quyết.
- Giữ hai nhánh có sẵn: Renter create hiện tại tạo AWAITING_DEPOSIT/hold ngay; booking PENDING đi qua Provider accept rồi mới có hold/payment request. Không ép module Renter quay lại manual approval hoặc sửa seeder để giả lập luồng khác.

## 4. State machine và dữ liệu suy diễn

| Trục | Chuyển trạng thái / quy tắc |
|---|---|
| Booking | PENDING → Provider ACCEPT → AWAITING_DEPOSIT; hoặc create trực tiếp AWAITING_DEPOSIT theo flow hiện có → gateway/proof hợp lệ CONFIRMED |
| Assignment | CONFIRMED → DRIVER_ASSIGNED/PENDING → Driver ACCEPT → DRIVER_CONFIRMED; REJECT/TIMEOUT lưu lịch sử, thu hồi giữ chỗ và tìm ứng viên khác theo retry limit |
| Thay điều khoản | Không proposal → PENDING → ACCEPTED + apply, hoặc REJECTED/WITHDRAWN; terminal booking làm proposal đang chờ thành EXPIRED khi đọc |
| Trip | DRIVER_CONFIRMED → DRIVER_ARRIVED → AWAITING_START_CONFIRMATION → IN_PROGRESS → AWAITING_END_CONFIRMATION → AWAITING_EXTRA_CHARGE_CONFIRMATION |
| Settlement | Chờ chốt phụ phí; DISPUTED chặn chốt → giải quyết → AWAITING_FINAL_PAYMENT hoặc COMPLETED nếu đã đủ tiền |
| Payment | PENDING/VERIFYING → PAID hoặc FAILED theo server; PAID nhưng fulfillment lỗi → RECONCILIATION_REQUIRED, không đổi thành FAILED |
| Refund | REQUESTED/UNDER_REVIEW → REJECTED có lý do hoặc PROCESSING/PENDING → proof xác nhận → REFUNDED/PAID; reconciliation chưa phải đã hoàn |
| Cancellation | Không còn nghĩa vụ: CANCELLED; có tiền cần đối soát/hoàn: CANCELLATION_REVIEW → CANCELLED khi hoàn tất nghĩa vụ |

Workflow driverStatus/tripStatus/paymentStatus/settlementStatus và actionRequired là projection, không thêm enum lưu DB để nhân đôi state machine. Driver đang ON_TRIP vẫn được xét cho lịch tương lai nếu khoảng thực tế/dự kiến, snapshot, GPLX và buffer hợp lệ; quá giờ/thiếu dữ liệu là UNCERTAIN.

## 5. Mapping nút UI → API → backend

| UI | Contract | Backend |
|---|---|---|
| Tiến trình Renter/Provider | GET `/api/{renter|provider}/bookings/{code}/workflow` | BookingWorkflowController → getWorkflow, kiểm ACTIVE/ownership |
| Provider gửi đề xuất/hủy theo chính sách | POST `/api/provider/bookings/{code}/operational-resolution`, `{resolution, replacementVehicleId?, newPickupTime?, reason}` | Workflow proposeAlternative; chỉ CANCEL_AND_REFUND thực hiện ngay theo policy hiện có |
| Renter đồng ý/từ chối | PUT `/api/renter/bookings/{code}/operational-proposal/{uuid}`, `{action:"ACCEPT"|"REJECT",reason}` | respondToProposal; Renter owner; lock và revalidate |
| Provider rút đề xuất | DELETE `/api/provider/bookings/{code}/operational-proposal/{uuid}` | withdrawProposal |
| Yêu cầu xem xét hoàn tiền | POST `/api/v1/refunds`, `{bookingCode,amount,reason}` | RefundController → RefundService |
| Provider duyệt/từ chối refund | POST `/api/v1/refunds/{id}/review`, `{action,rejectionReason?}` | ReviewRefundRequest chuẩn, không gửi `{approved,note}` |
| Xác nhận biên lai khoản hoàn | POST multipart `/api/v1/payment-transactions/{id}/refund-proof`, `file,bankReference,note?` | RefundService.completeRefund; chưa có proof không báo đã hoàn |
| Queue việc cần xử lý | GET `/api/provider/bookings` với `action,search,dateFrom,dateTo,sort,offset,limit` và bộ lọc sẵn có | filterBookings → Specification + Pageable; CSV cùng filter, giới hạn 10000 |
| Driver chờ phản hồi | GET `/api/v1/driver/trips?view=REQUESTS` | DriverTripController → BookingSpecification |
| Driver nhận | POST `/api/v1/driver/trips/{code}/confirm`, `{assignedAt}` | confirmTrip overload; chống tab cũ |
| Driver đến/bắt đầu/kết thúc | POST `.../{arrive|start|finish}` | DriverTripService; xác nhận Renter giữ API hiện có |
| Thanh toán VNPay | Create URL / IPN / query hiện có | Return chỉ đọc; job dùng query server đã xác minh |

Mutation thành công refetch từ server. API lỗi hiển thị và tải lại state, không tự suy diễn mutation đã thành công. Event OPERATIONAL_PROPOSAL_CHANGED dùng cơ chế after-commit hiện có; SSE reconnect/focus/poll fallback tiếp tục đồng bộ các vai trò.

## 6. Entity, schema và migration

`OperationalProposal` là embeddable của `Booking`, không phải entity/table riêng. Thêm 11 cột nullable `proposal_*` chứa UUID, loại thay đổi, xe/giờ đề xuất, lý do, người gửi/thời điểm, kết quả và người phản hồi/thời điểm/lý do. Không sao chép bảng giá hoặc số tiền. Schema nguồn `Backend/EcoMotion/src/main/resources/db/schema.sql` được đồng bộ.

Migration: `db/migrations/20261030_tv5_operational_consent.sql`. Chạy bằng quy trình DB của nhóm **trước khi triển khai app**; file không tự động chạy chỉ vì nằm trong resources. Có IF NOT EXISTS và unique partial index proposal_id; nullable cho booking cũ, không backfill consent cho thay đổi lịch sử. Migration tham chiếu vehicle/users. PostgreSQL integration chạy lại migration nhiều lần trên schema test và thực hiện nghiệp vụ sau đó. Chưa chạy vào DB dev/prod của nhóm.

Giới hạn audit được chủ động công khai: chỉ giữ đề xuất gần nhất. Những lần đã áp dụng vẫn có previous vehicle/time và operational resolution hiện có; đây chưa phải lịch sử bất biến của mọi đề xuất từng gửi. Không quảng cáo audit log đầy đủ.

## 7. Kết quả chạy thực tế

| Kiểm chứng | Kết quả thực chạy |
|---|---|
| Backend regression + package, Java target 21 | **171 PASS**, 0 failure/error/skip; JAR đóng gói thành công |
| PostgreSQL 18.6, workflow mới | **15 PASS**, gồm approval/hold, capture, consent, rollback, queue/pagination/export, refund proof và settlement/dispute |
| PostgreSQL 18.6, concurrency regression | **19 PASS**, phân công/refund/proof/duplicate reference/lifecycle |
| Node tests | **88 PASS**, 0 fail; thêm timezone Việt Nam, ngày không tồn tại, REQUESTS và rental label |
| Chrome Playwright toàn bộ | **40 PASS**; API fixture, không phải gateway/browser live integration |
| Chrome chạy lại phần bị tác động cuối | **14 PASS**; không cộng lặp vào 40 test |
| Vite production build | PASS; cảnh báo bundle lớn hơn 500 kB vẫn còn |
| ESLint 18 file TV5 liên quan | 0 error, 6 warning về đọc sequence ref khi cleanup; kiểm tra vẫn giữ cơ chế bỏ phản hồi cũ |
| git diff --check | PASS |

Log backend nằm trong `Backend/EcoMotion/target/tv5-workflow-*.log`; báo cáo Surefire trong `target/tv5-workflow-validation/surefire-reports`. Log frontend `Frontend/EcoMotion/tv5-workflow-*.log`. PG dùng cluster riêng dưới target, port 64334, DB `tv5_test`; đã dừng sau kiểm chứng. Vite do phiên kiểm thử khởi chạy cũng đã dừng. Không dùng DB development của nhóm cho PG create/drop.

171 test cuối chọn các lớp: AssignmentAvailabilityTest, BookingNotificationServiceTest, DriverAssignmentEligibilityTest, DriverAvailabilityServiceTest, DriverTripServiceUnitTest, NativeDriverSelectionTest, NotificationStreamServiceTest, Tv5FinancialSafetyIntegrationTest, VnpayPaymentFlowIntegrationTest, FinalPaymentFlowIntegrationTest, PaymentGatewayStrategyUnitTest, VnpayReconciliationTest, ProviderFinanceServiceUnitTest, EndUserWorkflowIntegrationTest, PaymentReconciliationJobTest, DriverTripApiIntegrationTest và BookingConflictRegressionIntegrationTest.

Tái lập với Maven cache/runtime của môi trường nhóm: `mvn -Dtest=<các lớp trên> package`. Riêng workflow PG: `mvn -Dtest=EndUserWorkflowIntegrationTest -Dtv5.workflow.postgres=true -Dtv5.postgres.url=jdbc:postgresql://127.0.0.1:<private-port>/tv5_test test`; chỉ dùng DB riêng vì test tạo/xóa schema. Concurrency PG dùng `-Dtest=Tv5PostgresConcurrencyTest -Dtv5.postgres=true` và cùng URL riêng. Frontend: `npm test`, `npm run build`, `npx playwright test` khi Vite đang phục vụ source tương ứng. Lượt thực tế dùng POM/config tạm để tách output khỏi IDE; đã xóa hai file tạm sau chạy.

Đã phát hiện bộ legacy dev-seed thất bại khi mở rộng: `ProviderBookingApiIntegrationTest` và `DriverTripLifecycleIntegrationTest` đòi các mã như BK-4M1P88/BK-8F3K29. `DevBookingSeedTest` hiện lại yêu cầu chính các mã đó không còn được seed. Không sửa seeder hoặc bỏ validation để các test này xanh. Lượt mở rộng 187 test: 6 failure, 8 error, đều thuộc hai lớp này; không tính lượt này là PASS.

## 8. Bảy hành trình và bad case

| Hành trình yêu cầu | Kiểm chứng nghiệp vụ thực tế |
|---|---|
| 1. Happy path | EndUserWorkflowIntegrationTest: tạo đơn/hold, capture cọc ký hợp lệ, Provider phân công, Driver nhận/đến/start, Renter start, Driver finish, Renter end, settle, capture phần còn lại → SETTLED; nhánh PENDING/accept được kiểm riêng |
| 2. Driver từ chối | Reject lưu feedback riêng Provider; Renter không đọc lý do riêng; ứng viên kế tiếp được phân công và xác nhận |
| 3. Không còn driver | Đã thu tiền, driver OFFLINE, auto assign không có ứng viên; gửi phương án, Renter REJECT, lịch cũ giữ nguyên; hủy → reserve full refund; proof hợp lệ mới chốt hoàn/CANCELLED |
| 4. Future availability | AssignmentAvailabilityTest và Tv5PostgresConcurrencyTest: đang chạy nhưng có khoảng rảnh tương lai, trùng lịch/buffer, overdue/missing snapshot không được suy ra rảnh |
| 5. Capture/fulfillment exception | Hold hết hạn trong lúc gateway PENDING vẫn occupied; IPN thành công giữ PAID, booking review, RECONCILIATION_REQUIRED và chặn thu lại; IPN lặp không nhân tiền |
| 6. Dispute | Driver và Renter kết thúc; Provider thêm WAITING có evidence, giá bị backend tính lại theo snapshot; Renter dispute chặn settle; Admin waiver rồi chốt đúng số tiền |
| 7. Concurrency | PostgreSQL driver overlap/confirm/timeout/refund reserve/duplicate bank reference và concurrent ACCEPT/REJECT; một kết quả hợp lệ, không mất tiền hoặc hai phản hồi trái nhau |

Test trình duyệt bổ sung phản hồi, reload, hai tab và HTTP 400/401/403/404/409/500; refund dùng payload chuẩn, không hiển thị tiền đã hoàn khi mới gửi yêu cầu. Các bài Playwright dùng API fixture để kiểm UI/contract. Các journey backend dùng Spring/service/DB thật, MockMvc/JWT cho quyền và endpoint; chỉ mock ranh giới Maps/notification/file storage khi cần. **Chưa có bảy journey chạy xuyên browser → server thật → VNPay/Cloudinary/bank thật.**

## 9. Chưa xác minh và giới hạn vận hành

- Chưa chuyển tiền, refund merchant hay payout ngân hàng thật. Proof là xác nhận chứng từ bởi actor có quyền, không phải bằng chứng từ API ngân hàng độc lập. Luồng sandbox được ghi nhãn rõ.
- Chưa xác minh VNPay merchant/allowlist/IPN public/return URL triển khai thật, Cloudinary thật, Google Maps thật hoặc GPS điện thoại. Gateway unit test dùng HTTP server cục bộ và chữ ký; không thể suy ra kết nối merchant thật đã hoạt động.
- Nếu gateway tiếp tục UNKNOWN/PENDING, hệ thống giữ slot và tiếp tục đối soát; không tự giải phóng để tránh double booking. Cần vận hành theo dõi và xử lý giao dịch treo. Job mặc định batch 25, tối đa 100, delay 60000ms; config `app.vnpay.reconciliation-enabled` có thể tắt rõ ràng.
- GPS không có dữ liệu không được dùng làm vị trí xe; viewport bản đồ có thể có tâm mặc định, marker xe cần tọa độ thật. ETA/location stale tiếp tục dùng quy tắc audit trước. Chưa có test GPS thiết bị ngoài đời.
- Queue đã lọc/phân trang tại DB và batch ledger/image; tính duration/risk của từng dòng còn truy vấn bổ sung hiện có. Chưa benchmark production hoặc tuyên bố triệt tiêu mọi N+1 của toàn module.
- Hai lớp legacy seed nói trên cần chuyển sang fixture tự tạo; không dùng chúng làm bằng chứng manual approval/lifecycle đã được kiểm hết. Không tuyên bố toàn bộ repository xanh.

## 10. Ảnh hưởng module khác

Booking thêm cột nullable và API projection; cần phối hợp người quản lý schema/deploy. Renter detail được sửa vì chính người thuê phải duyệt phương án và thấy trạng thái tiền. Provider accept nay trả AWAITING_DEPOSIT kèm hold 15 phút thay cho ACCEPTED chưa giữ xe; caller phải đọc status trả về. Không đổi registration, catalog pricing, public booking creation policy hoặc seeder. DTO Driver thêm field cuối và giữ constructor tương thích; API confirm có body optional cho caller thường nhưng điều khoản thay đổi bắt buộc snapshot mới. Finance giữ refund ledger và payout source isolation hiện có. Service khác không được gọi apply operational resolution để bỏ qua consent.

SSE gửi dữ liệu nghiệp vụ tối thiểu theo ownership; feedback từ chối/quá hạn riêng Provider/Admin. Không lộ cho Renter lý do cá nhân của driver. Optimizer, amount calculation và authorization không được mock ở runtime để che lỗi tích hợp.

## 11. Demo Renter / Provider / Driver

1. Dùng dữ liệu/tài khoản test của môi trường nhóm, chọn booking tương lai hợp lệ. Với flow create giữ lịch ngay, Renter xem thời hạn cọc; với booking PENDING, Provider duyệt trước. Thanh toán qua môi trường VNPay được cấu hình; chỉ IPN/query đã xác minh mới ghi tiền.
2. Provider mở Queue → “Việc cần xử lý” → chờ phân công; kiểm candidate theo đúng booking, chọn Manual/Auto Suggest/Auto Assign. Driver mở “Chờ phản hồi”, kiểm giờ/xe và nhận hoặc từ chối. Provider/Renter refetch thấy đã chấp nhận khác với mới gửi yêu cầu.
3. Để demo hết tài xế, cho driver không đủ điều kiện sau khi đã đặt cọc. Provider thử lại hoặc gửi đề xuất xe/giờ, nhập lý do. Renter mở chi tiết xem tiền và phương án, đồng ý/từ chối. Trước ACCEPT xe/giờ gốc không đổi; sau ACCEPT driver cần xác nhận lại. Tab driver cũ gửi assignedAt cũ phải conflict.
4. Nếu từ chối và không thực hiện được, Provider xác nhận hủy/hoàn. Renter thấy đang xử lý, không “đã hoàn”. Provider mở Tài chính, xem REFUND/PENDING, chỉ sau khi đã chuyển tiền theo quy trình mới nộp biên lai/mã ngân hàng. Sau proof hợp lệ Renter thấy số đã hoàn.
5. Demo trip: Driver đến → Driver start + Renter start → Driver finish + Renter end. Provider thêm phụ phí có chứng từ rồi chốt. Renter có thể dispute; chốt bị chặn đến khi Admin xử lý. Thanh toán còn lại được server xác minh rồi booking mới COMPLETED.
6. Mở hai tab để thử phản hồi trái nhau, refresh callback trước IPN hoặc mất mạng. Chỉ trạng thái lưu tại server quyết định kết quả; không tạo thanh toán mới từ reload.
