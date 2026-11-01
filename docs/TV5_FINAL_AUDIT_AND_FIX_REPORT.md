# TV5 — Final Audit and Fix Report

Ngày kiểm tra: 29/10/2026, múi giờ Asia/Saigon. Nhánh: `task-member-5`. HEAD đầu phiên: `64abdef06b289c5e4f685b6c25d1494ac95254a2`.

## 1. Executive Summary

Đã sửa trực tiếp các lỗi còn tồn tại của O07/O08/O11/D01/D02 và các luồng thanh toán liên quan. Đây là audit tiếp nối trên working tree **đã có nhiều thay đổi chưa commit**; không coi toàn bộ diff với HEAD là công việc của phiên này. Không checkout/reset, sửa main, commit, push, merge hoặc tạo PR. Đối chiếu `main...HEAD` cho thấy nhánh có thay đổi của nhiều module; chỉ bổ sung vào các luồng liên quan, giữ nguyên công việc có trước.

Nguồn đối chiếu: `AGENT_CONTEXT.md`, `Backend/EcoMotion/AGENT_CODE_STRUCTURE.md`, `docs/PHAN_CONG_6_THANH_VIEN.md`, `docs/SBA301_FALL26.md`, các tài liệu TV5, báo cáo availability, báo cáo triển khai/VNPay trước đó, entity/ERD và SQL migration. Một số sơ đồ/tài liệu cũ chưa mô tả đúng trạng thái chờ hai phía; implementation và test được đối chiếu với quy tắc nghiệp vụ, không mặc định báo cáo cũ là bằng chứng đã PASS.

Quyết định được người dùng xác nhận trong phiên: **giữ hai nguồn thu riêng; chỉ tiền nền tảng thu mới được xét payout**. Chuyển khoản vào tài khoản Provider không trở thành tiền nền tảng đang giữ.

Thay đổi chính:

- Availability dùng khoảng thời gian và buffer; bỏ nhánh suy ra rảnh khi sang ngày mới. Chuyến quá giờ/thiếu dữ liệu đáng tin cậy trả `UNCERTAIN`; chuyến đang chạy còn đúng kế hoạch vẫn có thể nhận lịch tương lai không trùng.
- Không tạo thanh toán cọc cho booking `PENDING`. Đơn `ACCEPTED` cũ được giữ lịch trước khi tạo URL; hold còn hiệu lực mới được thanh toán.
- VNPay xác minh đã thu tiền nhưng booking/hold/dispute không hợp lệ: lưu `PAID`, không áp dụng vào nghĩa vụ (`appliedAmount=0`), lưu lý do đối soát; UI trả `RECONCILIATION_REQUIRED`, không báo booking thành công, không cho retry.
- Refund giữ hạn mức ngay từ REQUESTED/UNDER_REVIEW; gồm refund tự động chưa có RefundRequest. Sửa thứ tự khóa, refresh entity, chống mã chuyển khoản lặp bằng index PostgreSQL.
- Payout sandbox chuyển vào backend, Admin ACTIVE tạo, mặc định tắt, cấm prod/production, lưu PENDING/MOCK_SIMULATED. Không gọi ngân hàng. Snapshot số tiền nguồn và phí tránh thay đổi lịch sử khi đổi hoa hồng.
- Sửa KPI/ledger O11 đang đọc sai trường DTO và mặc định mọi giao dịch là VNPay; khôi phục xử lý nguy cơ xe về trễ trên O08 và revalidate tài xế khi đổi xe/dời lịch.
- `actualEndAt` chốt khi hai bên xác nhận; không suy ra COMPLETED từ timestamp. Bổ sung event định danh/thời điểm và sửa đăng ký event thanh toán hai tầng afterCommit.

Kết quả cuối cùng được ghi ở mục 9. Browser dùng API mock; các test backend có Spring/H2 và PostgreSQL 18 cô lập. Chưa có kiểm thử liên thông toàn hệ thống với VNPay/Cloudinary/GPS thật. Không kết luận mọi happy/bad case đã sẵn sàng demo thật.

## 2. Issue List và nguyên nhân

Đường dẫn `BE` = `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion`; `FE` = `Frontend/EcoMotion/src`. Số dòng chỉ vị trí phương thức/đoạn xử lý trong source sau sửa, có thể dịch khi tích hợp tiếp.

| ID | Mức | Vị trí | Root cause | Trạng thái |
|---|---|---|---|---|
| AV-01 | P0 | BE/booking/service/implement/DriverAvailabilityServiceImpl.java:114 | Nhánh so ngày bỏ qua chuyến quá giờ khi yêu cầu là ngày sau | FIXED |
| AV-02 | P0 | Cùng file:160; DriverOptimizationServiceImpl.calculateBookingEndTime | Thiếu thời lượng/snapshot vẫn lấy plan hiện tại hoặc mặc định một giờ; không bù trễ lúc bắt đầu | FIXED: dữ liệu thiếu chặn an toàn; thời lượng mục tiêu phải xác định từ snapshot/base charge hoặc phép chia giá snapshot chính xác |
| PAY-01 | P0 | BE/finance/service/implement/VnpayServiceImpl.java:87 | Cho thanh toán PENDING; ACCEPTED cũ không giữ lịch trước checkout | FIXED |
| PAY-02 | P0 | Cùng file:514 | Đã xác minh gateway thành công nhưng booking lỗi vẫn giữ Payment PENDING | FIXED: tiền PAID, booking không nhảy trạng thái, cờ đối soát riêng |
| PAY-03 | P0 | Cùng file:489; schedule/service/implement/ScheduleServiceImpl.confirmUnexpiredHold | IPN chưa kiểm lại dispute và hold còn hiệu lực/xung đột với booking khác | FIXED |
| REF-01 | P0 | BE/finance/repository/RefundRequestRepository.java; RefundServiceImpl.java:63 | Hạn mức chỉ tính APPROVED/PROCESSING/REFUNDED, bỏ REQUESTED/UNDER_REVIEW và refund ledger tự động | FIXED |
| REF-02 | P0 | RefundServiceImpl.java:120; booking/service/implement/BookingPaymentLedgerServiceImpl | Khóa refund trước booking, preload entity version cũ trước chờ khóa; race mới tái hiện lỗi optimistic locking | FIXED: booking trước, bỏ snapshot refund cũ, đọc/refresh dưới khóa |
| REF-03 | P0 | RefundServiceImpl.java:201; migration 20261029_tv5_refund_reference.sql | Không kiểm reference trùng; check ứng dụng riêng lẻ không bảo vệ hai booking | FIXED: chuẩn hóa/check trước upload và unique partial index PostgreSQL |
| REF-04 | P0 | RefundServiceImpl.completeRefund | Hoàn một khoản có thể CANCELLED dù còn refund/giao dịch đang chờ | FIXED: giữ CANCELLATION_REVIEW khi còn nghĩa vụ |
| FIN-01 | P0 | FE/pages/ProviderFinancePage.jsx; BE/finance/service/implement/ProviderFinanceServiceImpl.java:528 | React setTimeout tạo payout giả, số tiền/STK fallback; backend mock ghi PAID khi chưa chuyển tiền | FIXED |
| FIN-02 | P1 | ProviderFinanceServiceImpl.getFinanceSummary; FE/pages/ProviderFinancePage.jsx | KPI đọc tên không có trong DTO; method/reference bị thay bằng VNPay hardcode; không phân nguồn thu | FIXED |
| TRIP-01 | P1 | BE/booking/service/implement/DriverTripServiceImpl.java:413,690; BookingServiceImpl.confirmEnd | Driver finish đã ghi actualEnd; trạng thái chờ quyết toán suy thành COMPLETED | FIXED |
| OPS-01 | P1 | FE/components/provider/OperationalRiskPanel.jsx; ProviderBookingServiceImpl.java:1126 | Hook tải risk nhưng page bỏ hiển thị/thao tác; đổi xe/dời giờ chưa kiểm lại tài xế/buffer | FIXED |
| RT-01 | P1 | VnpayServiceImpl.notifyPaymentAfterCommit; BookingNotificationServiceImpl.changed | Đăng ký afterCommit mới bên trong afterCommit có thể làm mất phát event; payload thiếu định danh/thời điểm | FIXED |
| UI-01 | P1 | FE/pages/booking/BookingRequestPage.jsx | Cùng một lỗi vừa hiển thị trong dialog vừa truyền xuống trang xác nhận | FIXED; test lỗi lặp/focus giữ nguyên |
| QA-01 | P2 | FE/e2e/tv5-ui-audit.spec.js | Ảnh ghi vào đường dẫn cá nhân ngoài workspace | FIXED: test.info().outputPath |
| PERF-01 | P2 | ProviderBookingServiceImpl; ProviderFinanceServiceImpl | O07 lọc/sort bộ dữ liệu trước cắt trang; summary finance lặp query charges theo booking | REMAINING, chưa benchmark dữ liệu lớn |

## 3. Fixed Issues và bằng chứng implementation

### Availability, assignment và OR-Tools

Overlap tiếp tục dùng `[start,end)` và buffer một lần cho mỗi biên trong `AvailabilityPolicy`. Không đổi buffer thành cấu hình solver. Batch inspect và kiểm tra ghi dùng chung logic; dữ liệu lịch bị hủy/đã kết thúc không nằm trong tập chiếm tài xế. Pending hết deadline không giữ lịch.

Current readiness (`workStatus`) khác availability của slot. DTO giữ current booking/start, booking xung đột, lịch kế tiếp, giờ dự kiến rảnh và lastUpdatedAt. Bổ sung `UNCERTAIN`/`DRIVER_RETURN_UNCERTAIN`; OFFLINE/UNAVAILABLE vẫn chặn. Thời gian bắt đầu trễ kéo dài occupation dự kiến. Đã quá dự kiến nhưng chưa có xác nhận kết thúc thì không đoán ngày mai sẽ rảnh. Giờ dự kiến rảnh vẫn là ước tính, không phải bằng chứng đã kết thúc.

Manual, suggest và full auto vẫn dùng cùng evaluator: provider ownership, account ACTIVE, profile APPROVED, readiness, GPLX qua cuối khoảng thuê, hạng xe, incident, lịch. Manual/auto kiểm lại sau khóa driver. Full auto không tìm được người trả INFEASIBLE; retry giới hạn theo cấu hình, không chọn người đã REJECTED/TIMEOUT; hết lượt giữ booking CONFIRMED chưa có tài xế để Provider điều phối. Event DRIVER_UNAVAILABLE được bổ sung cho trường hợp tự động không tìm được người/hết lượt. Recall thông báo driver cũ; assignment thông báo driver mới; thay đổi booking gửi Provider/Renter.

CP-SAT là bài toán **một booking chọn một driver** từ các ứng viên hợp lệ, không phải tối ưu toàn đội xe nhiều booking. Native test kiểm OPTIMAL thật và thay đổi trọng số; fallback giữ eligibility, objective chung, timeout và lý do fallback. Không tự mở rộng mô hình MVP. GPS thiếu/cũ không giả làm vị trí TP.HCM; ETA ước lượng được phân biệt với dữ liệu vị trí.

### Thanh toán và đối soát

Return URL chỉ xác thực và đọc ledger; IPN/QueryDr xác minh server mới ghi tiền. Khóa booking trước transaction, refresh trạng thái dưới khóa, reference/amount/method/direction được đối chiếu. Duplicate IPN không cộng tiền lần hai. Auto assignment chạy transaction riêng sau commit nên lỗi chọn tài xế không rollback tiền đã ghi.

Khi gateway xác nhận thành công nhưng không thể thực hiện đơn, `recordPaymentForReconciliation` giữ PAID/GATEWAY_VERIFIED/paidAt, appliedAmount=0, processingNote bắt đầu RECONCILIATION_REQUIRED. PaymentRequest liên quan chưa CONFIRMED chuyển REVIEW_REQUIRED. Event gửi các bên sau commit. Callback/query trả trạng thái cần đối soát và retryAllowed=false. Không tự hủy dispute, không xác nhận xe đã giao, không tạo giao dịch thanh toán lại. Đối soát/hoàn tiền dùng quy trình thủ công có actor/chứng từ hiện có; chưa bổ sung API hoàn tiền ngân hàng tự động.

### Refund

REQUESTED, UNDER_REVIEW, APPROVED, PROCESSING, PENDING_RECONCILIATION và REFUNDED giữ hạn mức. Tổng gồm các refund PENDING/PAID trên ledger chưa liên kết request để không bỏ sót refund tự động; không đếm hai lần request đã có refund transaction. Khi approve, request hiện tại đã nằm trong tổng nên không cộng lần nữa.

Create khóa booking để serialize hai yêu cầu. Review dùng booking → refund; proof dùng booking → transaction, refresh entity đã đọc trước khi chờ. Duplicate approval/proof không tạo dòng tiền thứ hai. Bank reference trim/uppercase/giới hạn 100, kiểm trước lưu file, index bảo vệ cạnh tranh khác booking. Rollback xóa file mới. Manual proof ghi confirmedBy, paidAt, verification method của actor; ảnh là tài liệu cho **xác nhận thủ công có trách nhiệm**, không phải tích hợp xác nhận từ ngân hàng.

Booking có payout đang tồn tại phải chuyển Admin đối soát trước khi tạo refund mới. Hạn mức được kiểm lại cả khi xác nhận proof, để ledger cũ/luồng tự động vượt thực thu không thể trở thành PAID. Còn yêu cầu/giao dịch chờ hoặc tiền đang RECONCILIATION_REQUIRED thì không tự chuyển cancellation sang CANCELLED.

### Hai nguồn thu và payout

- Chuyển khoản trực tiếp Provider: tổng thu riêng, không đưa vào số tiền nền tảng chi trả.
- VNPay PAID/GATEWAY_VERIFIED: nguồn nền tảng; giao dịch pending/đang đối soát không đủ điều kiện payout.
- Chỉ Admin ACTIVE tạo sandbox payout, cấu hình mặc định false, chỉ dev/test, prod/production luôn chặn. Booking phải COMPLETED, không dispute, không refund chưa xử lý, tiền thực thu ròng phải khớp quyết toán; không trả chỉ vì đã thu một phần cọc.
- Không tạo trùng: khóa booking, trả lại payout hiện có. Lưu PaymentTransaction PAYOUT/OUT/PENDING/MOCK_SIMULATED cùng actor, tài khoản được kích hoạt và gross/fee snapshot. Không gắn paidAt/biên lai giả, không gọi ngân hàng.
- Ví dụ có 30.000đ VNPay + 70.000đ chuyển trực tiếp, tổng nghĩa vụ 100.000đ, hoa hồng 10%: sandbox payout gross 30.000, fee 3.000, net 27.000; không payout 90.000 từ tổng hai nguồn.
- Nếu vừa có nhiều nguồn thu vừa có tiền hoàn, schema hiện tại chưa ghi đủ nguồn tiền tài trợ cho từng refund; **chặn payout để Admin đối soát**, không tự phân bổ theo suy đoán. Với nguồn VNPay thuần, trừ refund trước tính phí.
- Hoa hồng ở thời điểm tạo lệnh được chốt vào payout snapshot; phần hoa hồng của tiền Provider thu trực tiếp là công nợ/ước tính riêng, không giả lập đã khấu trừ bằng một payout của nền tảng.

## 4. Remaining Issues và giới hạn

| ID | Mức/rủi ro | Trạng thái và việc còn cần |
|---|---|---|
| EXT-01 | P0 vận hành | NEEDS_VERIFICATION: VNPay merchant/IPN public HTTPS, QueryDr thực tế và ngân hàng. Localhost không phải IPN public. Không tự triển khai tunnel hoặc thay credential. |
| EXT-02 | P0 vận hành | NEEDS_VERIFICATION: gateway đã capture nhưng DB không ghi được do outage. Transaction sẽ rollback, cần gateway retry IPN/QueryDr/đối soát. Không có bằng chứng khả năng phục hồi toàn bộ sự cố hạ tầng bằng các test hiện tại. |
| REC-01 | P1 | Đối soát hiện dựa trên IPN và QueryDr được gọi khi kiểm tra giao dịch; chưa có job nền quét mọi pending transaction. Khi thiếu IPN và khách đóng trình duyệt, vận hành phải chủ động đối soát. Không giả lập FAILED để cho thanh toán lại. |
| REF-05 | P1 | Chính sách mức tiền được hoàn theo từng nguyên nhân hủy/điều khoản năm cần kiểm chứng liên thông với toàn bộ luồng cancellation. Test mới chứng minh hạn mức thực thu và giữ chỗ tiền, không chứng minh mọi eligibility nghiệp vụ của refund. |
| FIN-03 | P1 | Payout có mixed-source refund chặn an toàn vì thiếu allocation nguồn tiền. Chưa có payout ngân hàng thật; sandbox không có quyền xác nhận tiền đã chi. Không kết luận demo giải ngân thật đã sẵn sàng. |
| LEGACY-01 | P1 | Không sửa ngược dữ liệu MOCK/PAID hoặc reference trùng đã tồn tại. Migration có thể thất bại nếu dữ liệu trùng; cần điều tra, không xóa dữ liệu để ép migration qua. Payout cũ thiếu gross/fee snapshot vẫn đọc theo cơ chế legacy. |
| RT-02 | P1 | SSE không có outbox/replay bảo đảm giao sự kiện khi server crash. FE có focus/reconnect/poll 15 giây; chưa thử mạng mất kéo dài hoặc tải cao. Cảnh báo quá giờ trên UI được refetch, chưa có SLA push/email chủ động cho mọi tình huống trễ. |
| PERF-01 | P2 | N+1 finance summary, lọc O07 trên bộ dữ liệu và một số danh sách lớn chưa có benchmark/push-down hoàn chỉnh. Không tuyên bố đạt tải production. |
| QA-02 | P1 | Chưa chạy toàn bộ mọi module backend hoặc browser nối thật FE + BE + DB + dịch vụ ngoài. Test cũ phụ thuộc seed dev không được mặc định là regression đã đạt. Java runtime kiểm tra là 25, target compile vẫn 21. |

Không có lý do để đánh dấu các mục này FIXED chỉ vì package hoặc mock test đã PASS.

## 5. Happy Case & Bad Case Matrix

| Tình huống | Mong đợi | Bằng chứng/kết quả |
|---|---|---|
| Chuyến sáng chưa quá giờ, nhận chuyến tối/ngày sau không trùng | Cho chọn, vẫn hiển thị current booking | AssignmentAvailabilityTest: PASS |
| Quá giờ rồi sang ngày mới | UNCERTAIN, không chọn, không đoán giờ rảnh | AssignmentAvailabilityTest: PASS |
| Thiếu snapshot/thời lượng | Chặn, không mặc định 60 phút | Availability + eligibility tests: PASS |
| Start trễ hoặc khoảng cách nhỏ hơn buffer | Chiếm thêm thời gian, chặn xung đột | Availability tests: PASS |
| OFFLINE/profile bị khóa/GPLX không qua ngày cuối | Manual/suggest/auto cùng loại | DriverAssignmentEligibilityTest: PASS |
| Hai request gán cùng driver, manual/auto | Tối đa một booking được gán | Tv5PostgresConcurrencyTest: PASS |
| Auto lặp, reject/timeout, confirm hết hạn | Không duplicate; driver cũ không nhận được assignment mới | PostgreSQL suite: PASS |
| Không có driver | INFEASIBLE/chờ Provider, không ép gán | Eligibility + PostgreSQL tests; event bổ sung |
| PENDING chưa được Provider nhận | Không được checkout cọc | Tv5FinancialSafetyIntegrationTest: PASS |
| Return trước IPN, duplicate/concurrent IPN | Return chưa báo thành công; chỉ ghi tiền một lần | VNPay integration + PostgreSQL + browser: PASS |
| Gateway thành công nhưng booking hủy/hold hết hạn | PAID, applied=0, cần đối soát, không retry | Financial safety integration + browser: PASS |
| Dispute mở sau tạo URL | Không COMPLETED; tiền đã thu không mất dấu | Financial safety integration: PASS |
| Hai refund 70k trên thực thu 100k | Một yêu cầu thắng, giữ hạn mức từ REQUESTED | H2 + PostgreSQL: PASS |
| Hai người cùng approve hoặc cùng proof | Một dòng refund, không trả hai lần | H2 + PostgreSQL: PASS |
| Hai booking dùng cùng bank reference | Một PAID, một rollback/409 | PostgreSQL unique-index race: PASS |
| Refund tự động không có RefundRequest | Vẫn trừ vào hạn mức | Financial safety integration: PASS |
| Ledger refund cũ vượt thực thu, gửi proof hợp lệ | Vẫn chặn trước lưu proof/PAID | Financial safety integration: PASS |
| Phụ phí gửi sai amount và click lặp | Server tính snapshot, trả charge cũ khi replay | PostgreSQL suite: PASS |
| Driver finish trước renter | Chờ xác nhận, actualEnd chưa chốt; lần finish lặp không đổi mốc | Financial safety integration: PASS |
| actualEnd có nhưng còn chờ tiền | Execution không gọi COMPLETED | DriverTripServiceUnitTest: PASS |
| Đổi sang xe driver không đủ GPLX | Rollback cả xe/lịch; không giữ thay đổi nửa chừng | Financial safety integration: PASS |
| Payout từ tiền trực tiếp hoặc chưa quyết toán/còn refund | Bị chặn | Financial safety integration: PASS |
| Sandbox default/prod, actor Provider | Bị chặn; chỉ Admin đủ điều kiện | Unit + integration: PASS |
| Payout refresh | Đọc backend, không tạo fake success | Browser finance safety: PASS |
| Mobile/tablet/desktop, lỗi/retry, SSE stale/refocus | Không overflow, refetch có sequence | Browser suite: PASS với API mock |
| VNPay/Cloudinary/GPS thật; DB outage | Phục hồi được và đúng tiền | NEEDS_VERIFICATION |

## 6. API Mapping

| Màn hình/nút | HTTP API | Controller → Service |
|---|---|---|
| O07 lọc/sort/page | GET /api/provider/bookings/paged | ProviderBookingController → ProviderBookingService.filterBookings |
| O07 CSV | GET /api/provider/bookings/export | ProviderBookingController → exportFilteredBookings |
| O08 accept/deny | POST /api/provider/bookings/{code}/accept hoặc /deny | ProviderBookingController → acceptBooking/denyBooking |
| O08 mở ứng viên | GET /api/provider/bookings/{code}/assign-candidates | ProviderBookingController → driver evaluator |
| O08 gợi ý/chạy tự động | POST .../{code}/assign-suggest hoặc /auto-assign | ProviderBookingController → ProviderBookingService/DriverOptimizationService |
| O08 gán tay | PUT .../{code}/driver | ProviderBookingController → assignDriver → evaluator dưới khóa |
| O08 đổi xe/dời lịch/hủy hoàn | POST .../{code}/operational-resolution | ProviderBookingController → resolveOperationalRisk → ScheduleService và evaluator |
| O08 phụ phí/quyết toán | POST .../{code}/charges hoặc /settlement/finalize | ProviderBookingController → addCharge/finalizeSettlement |
| O08 xử lý incident | POST .../{code}/incidents/{id}/resolve | ProviderBookingController → TripIncidentService |
| O11 tổng quan/giao dịch/payout | GET /api/v1/provider/finance/{summary,transactions,payouts} | ProviderFinanceController → ProviderFinanceService |
| Admin tạo sandbox payout | POST /api/v1/provider/finance/sandbox/payouts/{bookingId} | ProviderFinanceController, ADMIN → createSandboxPayout |
| O11 duyệt refund | POST /api/v1/provider/finance/refunds/{id}/review | ProviderFinanceController → RefundService.reviewRefundRequest |
| O11 biên lai refund | POST /api/v1/payment-transactions/{id}/refund-proof | RefundController → RefundService.completeRefund |
| D01/D02 lịch/chi tiết | GET /api/v1/driver/trips và /{id-or-code} | DriverTripController → DriverTripService |
| D02 nhận/từ chối/arrive/start/finish | POST /api/v1/driver/trips/{id-or-code}/{confirm,reject,arrive,start,finish} | DriverTripController → DriverTripService |
| Renter xác nhận hai phía | POST /api/renter/bookings/{code}/{confirm-start,confirm-end} | RenterBookingController → BookingService |
| VNPay checkout | POST /api/v1/payment/vnpay/create-url | VnpayPaymentController → VnpayService |
| VNPay return/query/IPN | GET .../callback, GET .../query, GET/POST .../ipn | VnpayPaymentController → VnpayService → PaymentGatewayStrategy |

API cũ được giữ; trường mới mang tính bổ sung. Refund giữ contract action=APPROVE/REJECT, rejectionReason khi từ chối. Payout summary bổ sung verificationMethod; finance summary bổ sung directReceived/platformReceived/reconciliationRequiredAmount. Callback bổ sung giá trị trạng thái RECONCILIATION_REQUIRED, success=false/retryAllowed=false. Caller FE đã cập nhật. Endpoint sandbox mới trả DTO, không trả entity.

## 7. State Transition Matrix

| Đối tượng | Từ → sang | Điều kiện |
|---|---|---|
| Booking | PENDING → AWAITING_DEPOSIT | Provider accept/hold; PENDING không được trả cọc |
| Booking legacy | ACCEPTED → AWAITING_DEPOSIT | Checkout khóa xe, giữ lịch hợp lệ trước tạo URL |
| Booking | AWAITING_DEPOSIT → CONFIRMED | Gateway/nhận tiền hợp lệ, hold chưa hết hạn và không xung đột |
| Assignment | PENDING_RESPONSE → ACCEPTED/REJECTED/TIMEOUT/RECALLED | Đúng assignment hiện hành, owner và deadline; không ghi đè ACCEPTED tùy tiện |
| Booking | CONFIRMED → DRIVER_ASSIGNED → DRIVER_CONFIRMED → DRIVER_ARRIVED | Qua evaluator, driver nhận và báo đến hợp lệ |
| Booking | DRIVER_ARRIVED → AWAITING_START_CONFIRMATION → IN_PROGRESS | Hai bên xác nhận, actualStartAt server |
| Booking | IN_PROGRESS → AWAITING_END_CONFIRMATION → AWAITING_EXTRA_CHARGE_CONFIRMATION | Hai bên xác nhận, actualEndAt chốt sau hai phía |
| Booking | AWAITING_EXTRA_CHARGE_CONFIRMATION → AWAITING_FINAL_PAYMENT → COMPLETED | Hai phía kết thúc, không dispute, tiền quyết toán hợp lệ |
| Payment | PENDING → PAID/FAILED | Server xác minh gateway hoặc actor manual được phép; return URL không ghi tiền |
| Payment + fulfillment | PAID + RECONCILIATION_REQUIRED | Đã thu nhưng booking lỗi/hold hết hạn/dispute; không ép CONFIRMED/COMPLETED |
| Refund | REQUESTED/UNDER_REVIEW → PROCESSING hoặc REJECTED | Đúng quyền, hạn mức còn hợp lệ; reject có lý do |
| Refund | PROCESSING → REFUNDED | Manual proof/reference hợp lệ, actor chịu trách nhiệm; ledger OUT PAID |
| Booking hủy | CANCELLATION_REVIEW → CANCELLED | Không còn refund hoặc giao dịch pending theo guard hiện tại |
| Payout sandbox | Chưa có → PENDING/MOCK_SIMULATED | Admin, cấu hình dev/test, đúng nguồn tiền, quyết toán sạch |
| Driver readiness | AVAILABLE/OFFLINE/UNAVAILABLE | Độc lập trạng thái vận hành và availability của slot |

Event thay đổi gửi sau commit; payload BOOKING_CHANGED gồm bookingId/bookingCode/driverId/status/version/occurredAt. FE dùng refetch và sequence thay vì gán thẳng status từ sự kiện; polling/focus/reconnect là phương án dự phòng.

## 8. Database Changes

Không tạo entity/bảng mới. Tái sử dụng PaymentTransaction, RefundRequest, Booking, DriverAssignment, VehicleSchedule.

Migration mới: `Backend/EcoMotion/src/main/resources/db/migrations/20261029_tv5_refund_reference.sql`:

- Thêm `payment_transaction.payout_gross_amount`, `payout_fee_amount`, NUMERIC(14,2), nullable để giữ tương thích lịch sử. Lưu snapshot nguồn tiền/phí tại lúc tạo payout; không backfill bằng suy đoán.
- Unique partial index `uk_refund_paid_bank_reference` trên UPPER(TRIM(gateway_ref)) khi REFUND/OUT/PAID và reference khác null; chống cùng biên lai dùng cho nhiều booking khi cạnh tranh.
- `schema.sql` cập nhật hai cột và index cho schema mới. Không xóa migration cũ. Migration VNPay verification đã có trước phiên này vẫn cần được áp dụng theo thứ tự của dự án.
- SQL mới chạy thành công trên PostgreSQL 18 cluster riêng; test hai proof khác booking chứng minh index có hiệu lực. Không chạy trên DB phát triển/dùng chung.
- Index driver/status/pickup, booking charge và refund đã tồn tại trong schema/migration; không thêm index trùng tên cho đẹp báo cáo.

Triển khai hiện hữu phải chạy SQL migration theo cơ chế thủ công của repository trước khi bật code mới. Nếu reference cũ trùng, migration rollback; điều tra nghiệp vụ, không tự xóa dữ liệu. Nullable snapshot không làm thay đổi amount của giao dịch lịch sử.

## 9. Test Results

| Bộ kiểm chứng | Kết quả cuối | Phạm vi |
|---|---|---|
| Backend package, Java target 21 | PASS, **140 test**, 0 failure/error/skip | 13 lớp chọn lọc; unit, Spring/H2, native CP-SAT, gateway HTTP server cục bộ |
| PostgreSQL 18 | PASS, **19 test**, 0 failure/error/skip | Hai transaction/request thật, assignment/refund/IPN races, migration và unique index |
| Frontend Node | PASS, **86 test** | Bộ unit/contract hiện có, bổ sung trạng thái UNCERTAIN |
| Chrome Playwright | PASS, **32 test** | Toàn bộ e2e directory, API mock; viewport desktop/tablet/mobile |
| Frontend production build | PASS | Còn cảnh báo bundle >500 kB |
| ESLint các file FE sửa | 0 error, 4 warning | Warning ref sequence trong effect cleanup; chưa tuyên bố toàn repository lint sạch |
| git diff --check | PASS | Không lỗi whitespace |

Backend chọn lọc cuối: AssignmentAvailabilityTest, BookingNotificationServiceTest, DriverAssignmentEligibilityTest, DriverAvailabilityServiceTest, DriverTripServiceUnitTest, NativeDriverSelectionTest, NotificationStreamServiceTest, Tv5FinancialSafetyIntegrationTest, VnpayPaymentFlowIntegrationTest, FinalPaymentFlowIntegrationTest, PaymentGatewayStrategyUnitTest, VnpayReconciliationTest, ProviderFinanceServiceUnitTest. Không cộng số test từ các lượt chạy lại vào tổng. PostgreSQL suite là Tv5PostgresConcurrencyTest, riêng với 140 test trên H2/unit.

Log: `Backend/EcoMotion/target/tv5-final-regression.log`, `target/tv5-final-postgres.log`, XML/TXT trong `target/tv5-final-validation/surefire-reports`; frontend có `tv5-unit-validation.log`, `tv5-build-validation.log`, `tv5-browser-all.log`, `tv5-lint-validation.log`. Artifact backend: `target/tv5-final-validation/EcoMotion-0.0.1-SNAPSHOT.jar`. Build/test log và cluster test nằm dưới đường dẫn ignored, không đưa dữ liệu test vào Git.

Các lệnh đã dùng:

```text
npm.cmd test
npm.cmd run build
npx.cmd playwright test --config=.tv5-playwright.config.js --reporter=line --workers=2
npx.cmd eslint <các file FE thay đổi>
mvn.cmd -o -f .tv5-validation.pom.xml -Dmaven.repo.local=C:/Users/KIETLT/.m2/repository -Dtest=<TV5-selected-classes> package
mvn.cmd -o -f .tv5-validation.pom.xml -Dtest=Tv5PostgresConcurrencyTest -Dtv5.postgres=true -Dtv5.postgres.url=jdbc:postgresql://127.0.0.1:64334/tv5_test test
```

Maven dùng binary 3.9.16 sẵn trong cache, offline; sandbox cần quyền đọc JAR. POM kiểm chứng chỉ thay output directory sang `target/tv5-final-validation`, giữ dependency/plugin/source và target Java 21. Không sửa pom.xml gốc hoặc dừng ứng dụng đang chạy. Playwright dùng Chrome; config tạm bỏ quản lý webServer để tái sử dụng Vite riêng, tránh tiến trình cleanup webServer bị treo trên Windows. Các file config tạm không thuộc thay đổi sản phẩm.

Cluster PostgreSQL riêng được initdb mới trong `Backend/EcoMotion/target/tv5-isolated-postgres`, user/database tv5_test, chỉ 127.0.0.1:64334; không dùng database phát triển, không đọc credential thật. Dữ liệu test create-drop; cluster được dừng sau kiểm chứng.

Lỗi trong quá trình kiểm chứng đã được xử lý theo nguyên nhân: đường dẫn screenshot không được phép; entity version cũ khi hai approve cạnh tranh; status lỗi sau bổ sung hold; test fixture thiếu snapshot/base charge; fixture booking code phải theo chuẩn uppercase; test O08 trước đó để các API phụ đi ra backend thật với token giả. Không bỏ test/giảm assertion để che lỗi. Assertion actualEnd và AWAITING_DEPOSIT được cập nhật theo thay đổi state machine và bổ sung kiểm tra timestamp/hold thực tế.

## 10. Git Diff Summary và ảnh hưởng

Các bổ sung của phiên này nằm trong:

- Booking availability/optimization/repository, ledger/query locks, notification, trip end, Provider operational resolution.
- Finance VNPay/refund/payout, DTO summary/payout và endpoint sandbox Admin.
- PaymentTransaction thêm hai snapshot field; migration/schema như mục 8.
- FE Provider finance, Provider detail/risk panel, callback VNPay, assignment labels, realtime event, payout service và bỏ thông báo lỗi renter bị lặp.
- Test availability/eligibility/trip/finance, PostgreSQL race, browser finance/callback/operational/ảnh; báo cáo này và quyết định kiến trúc trong AGENT_CONTEXT.

| File/nhóm file chính | Lý do thay đổi |
|---|---|
| DriverAvailabilityServiceImpl.java, DriverOptimizationServiceImpl.java, BookingRepository.java | Thời lượng đáng tin cậy, overdue, chung rule và cảnh báo hết driver |
| BookingPaymentLedgerServiceImpl.java, BookingQueryServiceImpl.java | Khóa/refresh entity, tránh dùng dữ liệu trước chờ khóa |
| VnpayServiceImpl.java, ScheduleService.java, ScheduleServiceImpl.java | Giữ chỗ trước checkout, xác nhận hold, tách tiền và fulfillment, dispute/IPN/event |
| RefundServiceImpl.java, RefundRequestRepository.java | Hạn mức có reservation, proof, thứ tự khóa và trạng thái cancellation |
| ProviderFinanceService.java, ProviderFinanceServiceImpl.java, ProviderFinanceController.java | Hai nguồn thu, quyền Admin và payout sandbox có persistence |
| ProviderFinanceSummaryResponse.java, ProviderPayoutSummaryResponse.java | Contract nguồn thu/verificationMethod |
| PaymentTransaction.java, schema.sql, 20261029_tv5_refund_reference.sql | Snapshot gross/fee và unique bank reference |
| DriverTripServiceImpl.java, BookingServiceImpl.java | actualEnd hai phía, execution status chờ tiền |
| ProviderBookingServiceImpl.java, BookingNotificationServiceImpl.java | Revalidate khi thay xe/lịch, event có booking/driver/time |
| ProviderFinancePage.jsx, providerFinanceService.js | Đọc đúng DTO, bỏ fake payout/reference, tải danh sách backend |
| ProviderBookingDetailPage.jsx, OperationalRiskPanel.jsx | Khôi phục xử lý xe trễ, loading/error, sequence incident |
| VnpayCallbackPage.jsx, useRealtimeSync.js, assignmentAvailability.js | Đối soát riêng, event mới, nhãn UNCERTAIN |
| BookingRequestPage.jsx | Không lặp lỗi dialog và inline |
| Các test nêu ở mục 9; e2e/tv5-finance-safety.spec.js | Regression cho các lỗi mới, giữ assertion nghiệp vụ và fixture hợp lệ |
| AGENT_CONTEXT.md, báo cáo này, application.properties.example | Lưu quyết định mô hình tiền, giới hạn và cấu hình mặc định an toàn |

Không chỉnh quản lý đội xe/hồ sơ O04/O06, thuật toán map, upload adapter hoặc Admin portal ngoài endpoint có phân quyền mới. Finance tiếp tục gọi booking qua BookingQueryService/BookingPaymentLedgerService, không thêm truy cập repository chéo. Có ảnh hưởng có chủ đích tới Renter checkout/confirm-end và ScheduleService vì đây là các đầu nối bắt buộc của TV5. Các consumer trong repository được cập nhật, nhưng client ngoài repository cần hiểu thêm trạng thái callback mới.

### Trả lời các câu hỏi cuối

- **Còn lỗi nghiêm trọng?** Các lỗi tái hiện được ở mục 2 đã sửa; chưa đủ bằng chứng kết luận mọi P0 vận hành đã hết. EXT-01/EXT-02 và eligibility refund toàn bộ cancellation vẫn cần kiểm chứng.
- **Driver nhận chuyến tương lai?** Có, nếu khoảng + buffer không giao nhau, chuyến hiện tại chưa quá giờ và dữ liệu đáng tin cậy. Quá giờ/thiếu dữ liệu là UNCERTAIN.
- **Hết driver?** INFEASIBLE, giữ đơn chờ Provider điều phối; retry tự động có giới hạn, không chọn lại người đã reject/timeout; event DRIVER_UNAVAILABLE hỗ trợ cảnh báo.
- **Reject/về trễ thông báo ai?** Reject/recall/assignment gửi Provider và driver liên quan; thay đổi chung gửi cả Renter. Risk xe về trễ được Provider/Renter refetch trên UI; không tuyên bố có email/push bảo đảm cho mọi sự kiện trễ.
- **VNPay thu nhưng booking lỗi?** Tiền PAID, nghĩa vụ chưa áp dụng, cờ đối soát/PR REVIEW_REQUIRED, thông báo các bên, chặn retry. Hoàn tiền tiếp tục qua actor/chứng từ thủ công, chưa phải ngân hàng tự hoàn.
- **Refund chống trùng/vượt?** Có bằng chứng khóa booking/refund/ledger và index reference trên PostgreSQL; eligibility chi tiết theo mọi điều khoản cancellation chưa được chứng minh đầy đủ.
- **Finance đúng nguồn tiền?** Các trường tổng hợp/ledger và sandbox payout đã tách direct/VNPay. Mixed-source refund chặn để tránh phân bổ sai; lịch sử cũ không bị sửa tự động.
- **FE/BE đồng bộ hoàn toàn?** Các contract và browser case đã kiểm chứng; chưa thể gọi hoàn toàn ở môi trường thật. Có polling/reconnect/sequence, còn giới hạn outbox/mạng/dịch vụ ngoài.
- **Ảnh hưởng thành viên khác?** Có các điểm nối Renter/Schedule/Finance/notification và hai cột mới; cần áp dụng migration. Không ghi đè các thay đổi có sẵn hoặc thay kiến trúc module khác.
- **Demo toàn bộ happy/bad case?** Có thể dùng các test tự động và demo cô lập cho các case đã PASS; chưa xác nhận demo sandbox ngân hàng, upload/GPS thật hoặc DB outage.
