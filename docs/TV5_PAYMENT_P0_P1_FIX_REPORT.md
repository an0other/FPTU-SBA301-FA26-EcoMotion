# TV5 — Sửa P0/P1: Payment integrity và booking consistency

Ngày kiểm chứng: **30/10/2026**, Asia/Ho_Chi_Minh. Nhánh `task-member-5`, nền HEAD `58133019a982d486dd161ad5f6ff36391be05ac3`. Không commit, push, merge hoặc tạo PR.

**Ba lỗi được yêu cầu đã được sửa và có regression test thực thi. Kết luận toàn nhánh vẫn là NOT MERGE READY** vì các P0/P1 và mâu thuẫn spec ngoài phạm vi trong [báo cáo audit](TV5_LOGIC_ARCHITECTURE_AUDIT.md) chưa được giải quyết hết; chưa xác minh kết nối ngân hàng thật.

## 1. Phạm vi và kiến trúc

Đã đối chiếu AGENT_CONTEXT, AGENT_CODE_STRUCTURE, SBA301_FALL26, phân công thành viên và các báo cáo Booking/VNPay/Finance/Refund liên quan với code hiện tại. Giữ Controller → Service interface → implementation → Repository trong từng feature. Finance truy cập ledger qua BookingPaymentLedgerService, xe qua VehicleService, dispute qua DisputeQueryService. Không thêm repository chéo feature, entity, library hoặc migration.

DisputeQueryService là boundary đọc trạng thái chặn thanh toán trong feature dispute. Không inject DisputeService vào PaymentRequestService vì DisputeService hiện gọi PaymentRequestService khi xử lý phí tranh chấp; inject ngược sẽ tạo vòng dependency. Query service mới không phụ thuộc payment workflow và chỉ thực hiện quy tắc OPEN/IN_REVIEW chặn áp dụng tiền.

PaymentReceiptService có trách nhiệm xác thực quyền và xuất DTO biên lai theo ledger. Đây không phải wrapper CRUD bổ sung: service quyết định giao dịch nào được xem, nguồn xác minh nào hợp lệ và trạng thái nào được hiển thị thành công. Quét import Java production ngoài seeder không thấy repository chéo feature hoặc import implementation từ feature khác. Application context của H2 và PostgreSQL khởi tạo thành công, không phát sinh circular bean.

## 2. Root cause và thay đổi thực tế

### 2.1. VNPay Return Callback

Root cause: `VnpayServiceImpl.processCallback()` gọi `applyVerifiedPayment()` khi ledger còn PENDING. Chữ ký return hợp lệ bị dùng làm quyền cập nhật tiền/booking. Test H2 cũ còn kỳ vọng chính hành vi này, trong khi regression PostgreSQL yêu cầu read-only thất bại.

Sửa:

- `processCallback` chuyển sang `@Transactional(readOnly = true)`.
- Giữ xác minh signature, merchant, reference và exact amount; dữ liệu sai không được trả receipt.
- Bỏ hoàn toàn gọi writer, transaction/booking write lock và save khỏi callback. Chỉ trả kết quả trạng thái đang lưu.
- Giữ nguyên IPN và QueryDR là đường ghi có xác minh; giữ khóa booking trước transaction, refresh và idempotency hiện có.
- IPN chưa tới: callback trả PENDING_VERIFICATION, success=false, retryAllowed=false. IPN đã đến: đọc SUCCESS/FAILED/RECONCILIATION_REQUIRED từ ledger.
- Giữ FULL_AUTO guard và after-commit assignment; callback không kích hoạt điều phối.
- Cập nhật test H2 cũ để assert PENDING trước IPN, SUCCESS sau IPN và duplicate IPN không áp dụng lại.

Không sửa gateway protocol, không bỏ HMAC-SHA512, không dùng browser redirect để thay IPN khi chạy localhost.

### 2.2. Xác nhận chuyển khoản thủ công

Root cause: `confirmPayment()` khóa PaymentRequest trước, chưa bảo đảm cùng order với gateway; không kiểm hold/deadline/overlap/dispute đầy đủ. Nhánh deposit gọi `confirmHold()` có thể kích hoạt lại HELD đã hết hạn. Nhánh final chỉ xét booking status rồi COMPLETED. Mỗi lần nhận tiền lại dùng paymentCode của request làm transactionCode unique, gây lỗi khi thanh toán nhiều lần.

Sửa `PaymentRequestServiceImpl.confirmPayment()` và helper `prepareManualPaymentApplication()`:

1. Xác định Provider và kiểm ownership trước khi xử lý; lấy booking lock trước PaymentRequest lock, đọc lại trạng thái dưới khóa và kiểm ownership lần nữa.
2. Detach PaymentRequest đã đọc trước khi chờ booking lock rồi lấy/refresh lại dưới PESSIMISTIC_WRITE. Regression ban đầu bắt được conflicting entity version; đây là sửa nguyên nhân, không nới exception trong test.
3. Chỉ hỗ trợ loại DEPOSIT/FINAL_BALANCE cho thao tác này. Số tiền phải dương, phù hợp precision/scale cột tiền; không biến receivedAmount=0 thành số tiền mặc định.
4. Yêu cầu bank reference hoặc ghi chú xác nhận thủ công. Giữ chống trùng bank reference theo tài khoản nhận; PR đã CONFIRMED hoặc đã ghi nhận khoản cần reconciliation không được ghi lại.
5. Partial payment không có bank reference: replay cùng PR/số tiền/ghi chú bị chặn. Hai khoản chuyển khác nhau nhưng cùng amount/note cần reference ngân hàng để phân biệt; không suy đoán chúng là hai receipt mới.
6. Mỗi lần nhận tiền sinh transactionCode VQ riêng; giữ paymentCode trên PR làm định danh yêu cầu, không bỏ unique constraint của transaction.
7. Deposit chỉ áp dụng khi booking AWAITING_DEPOSIT, chưa hủy, giờ đón còn hợp lệ, xe APPROVED và hold còn hiệu lực, không overlap. Khóa xe và gọi `confirmUnexpiredHold` khi đủ tiền, hoặc `hasUnexpiredHold` cho partial. Không tạo/renew hold chỉ vì Provider đã nhận tiền.
8. Final payment chỉ áp dụng khi AWAITING_FINAL_PAYMENT, chưa hủy, có actualEndAt và hai bên xác nhận kết thúc, không có active dispute, số còn lại của PR khớp nghĩa vụ hiện tại. Không chuyển COMPLETED chỉ từ một receipt thiếu điều kiện.
9. Thông báo PAYMENT_VERIFIED chỉ phát sau khi kết quả áp dụng hợp lệ; kết quả không áp dụng được phát PAYMENT_RECONCILIATION_REQUIRED qua cơ chế sau commit sẵn có.

Tiền thực nhận khi không còn điều kiện thực hiện:

| Dữ liệu | Kết quả persist |
|---|---|
| PaymentTransaction | PAID, amount = tiền Provider xác nhận thực nhận, appliedAmount = 0, PROVIDER_CONFIRMED, processingNote bắt đầu RECONCILIATION_REQUIRED |
| PaymentRequest | REVIEW_REQUIRED, issueNote nêu cần đối soát/hoàn; paidAmount không tăng vì chưa áp dụng vào nghĩa vụ |
| Deposit booking còn ở PENDING/ACCEPTED/AWAITING_DEPOSIT | CANCELLATION_REVIEW, giải phóng hold của chính booking |
| Booking đã CANCELLED/REJECTED | Giữ terminal status; không hồi sinh booking/lịch |
| Final booking đang bị dispute/thiếu điều kiện | Giữ state hiện tại; không COMPLETED |
| UI Provider | Báo đã ghi nhận tiền nhưng booking chưa được xác nhận, cần đối soát/hoàn; refresh dữ liệu |

Khi ghi khoản đối soát, Provider phải nhập rõ receivedAmount; không tự lấy số phải thu để suy ra tiền thật đã nhận. Không tự tạo refund PAID, không giả tiền chưa nhận, không đổi giao dịch đã nhận thành FAILED. Quy trình review/refund/proof hiện hữu tiếp tục xử lý bước vận hành sau đó; không bổ sung enum DB/schema.

### 2.3. Payment Success và biên lai

Root cause: PaymentSuccessPage luôn hiển thị success và đọc bookingCode/amount/transactionCode/method/date từ URL hoặc router state, không kiểm tra server. Truy cập URL tự tạo tạo được biên lai giả dù không có payment.

Sửa:

- Thêm endpoint receipt có authentication, ownership và `Cache-Control: no-store`.
- Backend chỉ đọc ledger; không gọi IPN, QueryDR, create-url hoặc xác nhận booking khi xem biên lai.
- Chỉ giao dịch IN loại DEPOSIT_PAY/UPFRONT_PAY/SETTLEMENT mới được xuất ở endpoint này. Refund/payout không biến thành receipt thu tiền.
- Tài khoản phải ACTIVE. Cho phép Renter sở hữu booking, Provider sở hữu booking hoặc Admin; reference không thuộc quyền trả 404 chung với reference không tồn tại. Anonymous trả 401.
- PAID chỉ thành SUCCESS khi nguồn xác minh phù hợp, paidAt tồn tại, appliedAmount dương và không có cờ reconciliation. VNPay bắt buộc GATEWAY_VERIFIED; bank transfer nhận PROVIDER_CONFIRMED/ADMIN_RECONCILED; MOCK_SIMULATED/MANUAL_UNVERIFIED không thành receipt verified thật.
- PENDING → PENDING_VERIFICATION; FAILED → FAILED; tiền PAID chưa áp dụng/xác minh → RECONCILIATION_REQUIRED.
- FE chỉ dùng reference làm khóa tra cứu. Các field tiền/method/time/booking trong URL/state không được dùng để render receipt hoặc quyết định success.
- VnpayCallbackPage chuyển sang `/payment/success?reference=<vnpTxnRef>`; CustomerPaymentPage chuyển bằng paymentCode của request. URL giữ được qua reload; trang phải đọc lại server.
- Receipt mới hỗ trợ transactionCode, vnpTxnRef và paymentCode của request có receipt ledger. Với request có nhiều lần nhận tiền, lookup paymentCode trả receipt mới nhất của request, không giả tổng PR là một bank transaction.
- Chỉ render chi tiết biên lai khi server trả SUCCESS; pending/failed/reconciliation có thông báo tương ứng. Thiếu reference, mất mạng, 401/403/404/500 hoặc DTO không hợp lệ đều không hiển thị receipt thành công. Có nút kiểm tra lại; request được abort/loại bỏ khi unmount hoặc đổi reference.
- Hiển thị đúng nguồn: VNPay được gateway xác minh; chuyển khoản được xác nhận thủ công, không gọi là bank tự động đối soát.

## 3. File và method đã sửa

Các đường Java bên dưới tương đối với `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion`.

| File | Method / trách nhiệm |
|---|---|
| `finance/service/implement/VnpayServiceImpl.java` | processCallback: verify/read, không writer |
| `finance/service/implement/PaymentRequestServiceImpl.java` | confirmPayment, prepareManualPaymentApplication: lock, guard, ledger, reconciliation, distinct transaction codes |
| `booking/service/BookingPaymentLedgerService.java` và `implement/BookingPaymentLedgerServiceImpl.java` | findTransactionByReference: truy cập repository trong đúng feature |
| `dispute/service/DisputeQueryService.java` và `implement/DisputeQueryServiceImpl.java` | Active dispute read boundary, tránh vòng dependency |
| `finance/service/PaymentReceiptService.java` và `implement/PaymentReceiptServiceImpl.java` | getReceipt: authorization, ledger projection và verification rules |
| `finance/controller/PaymentReceiptController.java` | GET receipt, no-store, delegate interface |
| `finance/dto/response/PaymentReceiptResponse.java` | DTO whitelist, không trả entity |
| `finance/enums/PaymentReceiptStatus.java` | Enum projection API, không phải enum persist DB |
| `Frontend/EcoMotion/src/pages/PaymentSuccessPage.jsx` | Receipt UI chỉ dựa kết quả backend |
| `Frontend/EcoMotion/src/pages/VnpayCallbackPage.jsx` | Navigation bằng reference |
| `Frontend/EcoMotion/src/pages/CustomerPaymentPage.jsx` | Navigation bằng request code; bỏ helper ngày không dùng trong file đã sửa |
| `Frontend/EcoMotion/src/pages/ProviderFinancePage.jsx` | Toast REVIEW_REQUIRED phân biệt tiền đã nhận với booking đã xác nhận |
| `Frontend/EcoMotion/src/services/paymentService.js` | getReceipt(reference, AbortSignal) |
| `AGENT_CONTEXT.md` | Lưu quyết định integrity và contract receipt |

Tests: cập nhật VnpayPaymentFlowIntegrationTest; thêm PaymentIntegrityIntegrationTest, PaymentReceiptServiceUnitTest, `e2e/payment-receipt.spec.js`, `e2e/manual-payment.spec.js`; cập nhật mock receipt trong `e2e/vnpay-callback.spec.js` để kiểm cả navigation mới.

Không sửa source schema, migration, seed, thư viện hoặc cấu hình credential runtime. Báo cáo audit trước được giữ làm baseline, không sửa thành tuyên bố các lỗi ngoài phạm vi đã hết.

## 4. API contract và tương thích

### Endpoint mới

```text
GET /api/v1/payments/receipt?reference=<transactionCode | vnpTxnRef | paymentCode>
Authorization: Bearer <access token>
Cache-Control: no-store
```

Response giữ envelope ApiResponse:

```json
{
  "data": {
    "status": "SUCCESS",
    "bookingCode": "EM-...",
    "transactionCode": "VP000010",
    "vnpTransactionNo": "gateway-reference",
    "amount": 30000,
    "paymentMethod": "VNPAY",
    "verificationMethod": "GATEWAY_VERIFIED",
    "paidAt": "2026-10-30T10:00:00Z"
  }
}
```

Giá trị minh họa trên không phải giao dịch thật. Các status projection: SUCCESS, PENDING_VERIFICATION, FAILED, RECONCILIATION_REQUIRED.

Không đổi URL/payload/DTO hiện hữu của VNPay hoặc manual confirmation. Thay đổi có chủ đích: callback không còn apply tiền; manual confirmation có thể trả PaymentRequestResponse.status=REVIEW_REQUIRED thay vì xác nhận booking hoặc rollback mất dấu tiền nhận. Các caller cần đọc status. PaymentSuccess route vẫn tồn tại; query/state cũ có reference hợp lệ có thể dùng để lookup, các field nội dung biên lai không còn có giá trị xác nhận.

## 5. Kiểm thử đã thực thi

### 5.1. Kết quả

| Lượt kiểm chứng | Kết quả thực tế | Phạm vi |
|---|---|---|
| Frontend Node unit/contract | **103 PASS**, 0 fail/skip | Bộ hiện hữu, không phải E2E ngân hàng |
| Frontend production build cuối | **PASS** | Vẫn warning chunk >500 kB |
| ESLint các file FE/test thay đổi | **0 errors, 3 warnings** | Ba warning ref cleanup có sẵn trong ProviderFinancePage; không disable rule |
| Backend regression rộng + package | **124 PASS**, 0 failure/error/skip; BUILD SUCCESS | 9 lớp; 52 ca PG, 72 ca H2/unit; native solver dùng qua workflow khi cần |
| Backend focused sau hardening replay partial | **28 PASS**, 0 failure/error/skip; BUILD SUCCESS | PaymentIntegrityIntegrationTest: 19 PostgreSQL; PaymentReceiptServiceUnitTest: 9 unit |
| Chrome Playwright cuối | **20 PASS**, 0 fail | 11 receipt cases + 8 VNPay callback cases + 1 manual-reconciliation UI case; API mocked |
| Quét import Java / git diff --check | **PASS** | Không repository chéo feature/implementation import mới; không whitespace error |

Không cộng 124+28 thành 152 test độc lập: hai lượt trùng 18 manual cases và 9 receipt unit cases. Lượt focused thêm một regression mới cho partial replay không bank reference. Tổng các case backend khác nhau đã được chạy qua hai lượt là 125; 53 trong số đó được kiểm trên PostgreSQL riêng. Không tuyên bố toàn bộ test repository hoặc toàn bộ browser directory đều xanh.

Lượt trước khi sửa fixture gặp NOT NULL description của BookingCharge; đã sửa fixture hợp lệ. Race sau đó bắt conflicting PaymentRequest version; đã sửa detach/reload dưới khóa và giữ expectation một winner. Lượt compile sandbox bị chặn đọc JAR Maven; chạy lại offline với quyền đọc cache, không tải dependency hay đổi ACL. Lượt browser dev-server bị reload/navigate giữa các ca; chuyển kiểm chứng sang production preview riêng để loại HMR và giữ nguyên assertion. Các failure trung gian không được tính PASS.

### 5.2. Ma trận các ca bắt buộc

| Nhóm / tình huống | Bằng chứng | Kết quả |
|---|---|---|
| Return trước IPN; chữ ký hợp lệ nhưng DB pending | VnpayPaymentFlowIntegrationTest.returnBeforeIpnOnlyReadsAndIpnAppliesPaymentExactlyOnce; PG repeatedIpnIsSerializedAndCallbackDoesNotConfirmMoney | PASS |
| IPN trước return; duplicate IPN; callback lặp | VNPay integration và PG callback/IPN regression | PASS |
| VNPay failed, signature/merchant/amount sai | VNPay integration và gateway tests hiện hữu đã chạy | PASS |
| QueryDR thành công/không rõ/chữ ký sai | VnpayReconciliationTest; VNPay query integration | PASS — HTTP server cục bộ / boundary test |
| IPN khi hold hết hạn | Tv5FinancialSafetyIntegrationTest; EndUserWorkflowIntegrationTest pendingGatewayKeepsExpiredHoldOccupiedAndLateCaptureRequiresReconciliation | PASS |
| Hai xử lý cùng giao dịch | PG repeated IPN và callback regression, independent transactions | PASS — hai worker, không phải hai máy/JVM |
| Manual hold hợp lệ | PaymentIntegrityIntegrationTest.validHoldConfirmsAndReceiptUsesPersistedPayment | PASS — PG |
| Manual hold hết hạn | expiredHoldRecordsMoneyWithoutActivatingSchedule | PASS — PAID/applied=0/review, không active schedule |
| Manual lịch xe xung đột | competingScheduleRecordsMoneyWithoutConfirmingBooking | PASS — PG |
| Manual booking CANCELLED/REJECTED | terminalBookingKeepsReceivedMoneyWithoutResurrection, parameterized | PASS — PG |
| Manual dispute ở PR PENDING/REVIEW_REQUIRED | disputedFinalPaymentCannotCompleteBooking, parameterized | PASS — PG |
| Manual đã PAID/đã reconciliation | paidRequestCannotBeConfirmedAgain; duplicateConfirmationAndReconciliationReplayNeverRecordTwice | PASS — PG |
| Manual hai request đồng thời | concurrentConfirmationsHaveOneWinner | PASS — một receipt, một winner; PG |
| Provider xác nhận booking người khác | invalidAmountAndWrongProviderDoNotRecordMoney | PASS — Provider khác có profile thật trong fixture |
| Tiền đã nhận nhưng booking không dùng được | Hold/terminal/conflict/dispute cases; receipt trạng thái reconciliation | PASS — tiền không bị chuyển FAILED/mất ledger |
| Partial hai receipt hợp lệ | partialPaymentsGetDistinctCodesAndOnlyCompleteAfterFullAmount | PASS — codes khác nhau, đủ tiền mới confirm |
| Replay partial không bank ref | repeatedPartialConfirmationWithoutBankReferenceCannotDoubleCount | PASS — PG |
| Final đủ điều kiện / amount stale | finalPaymentNeedsBothEndConfirmationsAndCompletesWhenEligible; staleFinalAmountRecordsMoneyForReconciliationInsteadOfCompleting | PASS — PG |
| URL `/payment/success` tự tạo, không reference | payment-receipt browser | PASS — không success, không receipt |
| URL đổi amount/code/method/date/booking | receipt browser + MockMvc receipt thêm query amount giả | PASS — server fields được giữ |
| Router state giả | Browser network retry test dùng forged state | PASS — chỉ reference dùng lookup |
| Reference của người khác, anonymous | receiptHttpReadsDatabaseAndRejectsOtherUsers, parameterized theo DB state | PASS — 404/401, không lộ receipt |
| Receipt PENDING/FAILED/PAID | Receipt unit + PG/MockMvc + browser | PASS |
| Receipt mock hoặc unapplied funds | mockedPaymentDoesNotBecomeVerifiedReceipt; receipt unit | PASS — không success |
| Reload receipt | receipt browser | PASS — đọc server lại, không tạo payment |
| Mất mạng / retry / HTTP 500 | receipt browser | PASS — không receipt thành công giả |
| Provider thấy manual receipt đang đối soát | manual-payment browser | PASS — thông báo chưa xác nhận booking |

Các test PG sử dụng Spring/service/JPA/MockMvc thật với fixture riêng; gateway/maps/storage/notification được mock hoặc dùng adapter local ở những lớp tương ứng. Browser chặn API bằng fixture để kiểm UI/contract; backend authorization và state transitions được kiểm độc lập. Không gọi các kết quả này là real integration E2E xuyên ngân hàng.

### 5.3. Môi trường, lệnh và artifact

Backend dùng Maven 3.9.16 trong cache, offline, Java target 21 theo POM gốc. PostgreSQL 18.6 riêng tại `target/tv5-audit-postgres`, bind `127.0.0.1:64335`, database/user `tv5_test`; test properties whitelist URL local/test database. Các lớp opt-in dùng Hibernate create-drop trong DB fixture. Không chạy migration trên dữ liệu thật. Cluster được dừng sau kiểm chứng.

Frontend build/preview riêng tại `127.0.0.1:5181`, Chrome headless, workers=2. Config kiểm chứng tạm nằm trong `node_modules/.cache/tv5-audit/payment-fix.config.mjs`, không sửa config production hoặc test assertion để bỏ lỗi. Preview chỉ phục vụ build; API browser đều mock.

```text
npm.cmd test
npm.cmd run build
npx.cmd eslint <các file FE và e2e thay đổi>
npx.cmd playwright test e2e/payment-receipt.spec.js e2e/vnpay-callback.spec.js e2e/manual-payment.spec.js --config=node_modules/.cache/tv5-audit/payment-fix.config.mjs --reporter=line

mvn.cmd -o -Dmaven.repo.local=C:/Users/KIETLT/.m2/repository -Dtest=PaymentIntegrityIntegrationTest,PaymentReceiptServiceUnitTest,VnpayPaymentFlowIntegrationTest,FinalPaymentFlowIntegrationTest,VnpayReconciliationTest,Tv5FinancialSafetyIntegrationTest,EndUserWorkflowIntegrationTest,PaymentReconciliationJobTest,Tv5PostgresConcurrencyTest -Dtv5.postgres=true -Dtv5.payment.postgres=true -Dtv5.workflow.postgres=true -Dtv5.postgres.url=jdbc:postgresql://127.0.0.1:64335/tv5_test package

mvn.cmd -o -Dmaven.repo.local=C:/Users/KIETLT/.m2/repository -Dtest=PaymentIntegrityIntegrationTest,PaymentReceiptServiceUnitTest -Dtv5.payment.postgres=true -Dtv5.postgres.url=jdbc:postgresql://127.0.0.1:64335/tv5_test package
```

Log backend: `Backend/EcoMotion/target/tv5-payment-fix-postgres.log`, `tv5-payment-fix-focused-final.log`, Surefire XML/TXT. Artifact build cuối: `target/EcoMotion-0.0.1-SNAPSHOT.jar`.

Log frontend: `Frontend/EcoMotion/tv5-payment-fix-{unit,build,lint,browser-final}.log`; traces tại `test-results/payment-fix` khi có failure. Artifacts kiểm chứng ignored, không đưa vào thay đổi sản phẩm.

## 6. Những phần chưa kiểm chứng và rủi ro còn lại

- VNPay merchant thật, IPN public HTTPS, QueryDR qua merchant thật, bank transfer/refund/payout thật: **NOT_RUN**. Không suy ra kết nối triển khai hoạt động từ chữ ký fixture hoặc HTTP server cục bộ.
- Multi-JVM/multi-host, fault injection crash giữa capture/commit, DB outage hoặc retry queue kéo dài: **NOT_RUN**. Các race đã chạy dùng worker/transaction độc lập trên PostgreSQL thật.
- Không chạy toàn bộ legacy dev-seed suite hoặc toàn browser directory; các lỗi test/lint ngoài phạm vi từ audit chưa được tuyên bố đã sửa. Targeted lint còn ba warning cũ, production build vẫn cảnh báo bundle lớn.
- Không đổi chính sách manual approval/Deny, generic refund entitlement/reservation giữa cancellation và refund request, duration legacy, entity REST incident, assignment mode hoặc N+1 trong báo cáo audit. Các vấn đề đó tiếp tục cần xử lý trước merge toàn nhánh.
- Legacy receipt PAID nhưng thiếu paidAt/applied allocation hoặc nguồn xác minh không phù hợp không được giả thành verified: endpoint trả reconciliation. Không tự backfill dữ liệu cũ bằng suy đoán.
- Lookup paymentCode của PR có nhiều receipt trả receipt mới nhất; không phải báo cáo tổng hợp tất cả partial transfers. TransactionCode từng receipt là khóa xem chính xác.
- Full confirmation của PR đã CONFIRMED vẫn bị chặn để chống double confirmation; khoản chuyển mới phát sinh ngoài nghĩa vụ cần vận hành đối soát riêng, không replay endpoint để tạo tiền.
- `calculateRemainingAmount` hiện hữu vẫn có các vấn đề allocation/refund rộng hơn được ghi trong audit A05; thay đổi này thêm guard final amount và không thực hiện refactor ledger ngoài ba lỗi được yêu cầu.
- Manual receipt là Provider/Admin xác nhận thông tin chuyển khoản, không phải ngân hàng độc lập xác minh. Ledger đối soát không có nghĩa tiền đã hoàn về khách; refund chỉ PAID sau proof/actor hợp lệ theo quy trình sẵn có.
- Không có schema/enum persist mới; cần bảo đảm các migration payment/verification/code đã có trong repository được triển khai đúng ở môi trường thật. Phiên này không thực thi migration upgrade/replay dữ liệu cũ.

## 7. Kết luận

Ba lỗi trong yêu cầu đã có thay đổi cụ thể và bằng chứng regression: callback không ghi tiền; manual capture không hồi sinh hold/booking hoặc vượt dispute guard và giữ dấu tiền thực nhận; receipt không tin URL/state và kiểm quyền bằng backend.

**NOT MERGE READY cho toàn nhánh.** Những P0/P1 khác và mâu thuẫn spec từ audit ngoài phạm vi chưa được xử lý hết; tích hợp ngân hàng thật và kiểm chứng triển khai vẫn chưa hoàn thành. Không đồng nhất bộ chọn lọc xanh với toàn hệ thống đã sẵn sàng merge.
