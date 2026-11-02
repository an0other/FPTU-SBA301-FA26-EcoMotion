# Thanh toán trực tiếp qua VietQR Callback

Ngày triển khai: 02/11/2026. Trạng thái: đã kiểm thử cục bộ; **chưa nghiệm thu kết nối Sandbox VietQR thực tế** vì chưa có tài khoản, ngân hàng liên kết và URL callback công khai.

## Thay đổi luồng

Trước đây backend dựng URL ảnh `img.vietqr.io`, Renter gửi chứng từ và Provider xác nhận. Một API Sandbox nội bộ có thể tạo kết quả PAID.

Hiện tại Renter chủ động tạo QR động cho PaymentRequest có sẵn. Backend khóa booking rồi payment request, kiểm tra giai đoạn, hold, tranh chấp, số tiền và tài khoản ACTIVE đã liên kết. Sau đó gọi Get Token / Generate Dynamic QR của VietQR, lưu orderId, nội dung, số tiền đã đối chiếu và payload QR thực tế. Trình duyệt vẽ payload này bằng thư viện `qrcode`, không dùng API ảnh VietQR Kit.

VietQR gọi API cấp token bằng Basic credentials riêng rồi gửi callback Bearer. Security filter chain riêng không dùng JWT Renter. Callback đối chiếu orderId, mã VQ, nội dung thực tế, tài khoản snapshot, số tiền, transType C và transaction ID. Ledger, audit và cập nhật booking nằm trong một transaction. Callback trùng cùng payload trả lại kết quả cũ; transaction ID tái sử dụng với payload khác bị từ chối. Unique constraint tại DB và retry ngoài transaction bảo vệ trường hợp nhận trùng đồng thời, kể cả order không tồn tại.

Callback cọc hợp lệ chuyển PaymentTransaction sang PAID, PaymentRequest sang CONFIRMED, hold còn hạn sang ACTIVE và booking sang CONFIRMED. Thanh toán cuối chỉ chuyển booking sang COMPLETED khi hai bên đã xác nhận kết thúc và số dư còn khớp. Không thay đổi nghiệp vụ duyệt đơn, phân công tài xế hoặc xác nhận chuyến. Email và sự kiện realtime dùng cơ chế sau commit hiện có. Renter làm mới trạng thái mỗi 5 giây và khi quay lại tab, sau đó mở biên lai đọc từ DB.

Tiền BANK_TRANSFER/VIETQR_CALLBACK thuộc Provider. Chỉ tiền VNPAY/GATEWAY_VERIFIED được xét là tiền nền tảng để payout. Tiền đã nhận nhưng sai số tiền, hết hold, booking hủy, đang tranh chấp hoặc nghĩa vụ đã có giao dịch được ghi PAID với `appliedAmount=0`, processingNote RECONCILIATION_REQUIRED; không xác nhận booking. Số dư ở PaymentRequest, Provider breakdown, Renter charges và phần cọc áp dụng đều tôn trọng giá trị 0 này. Các fixture mô phỏng tiền đã áp dụng phải đặt appliedAmount đúng số tiền.

Sai tài khoản, ghi nợ, sai nội dung hoặc order không tồn tại chỉ được lưu trong audit, không gắn nhầm khoản thu vào booking. Provider xem callback gắn với yêu cầu của mình; Admin xem cả order không tìm thấy. Báo lệch và hoàn tiền hiện có vẫn được giữ. API audit chỉ đọc, không có thao tác biến callback thành thành công.

## API

| Method | Endpoint | Quyền và hành vi |
| --- | --- | --- |
| POST | `/api/v1/payments/vietqr/create` | Renter ACTIVE sở hữu booking; body `{"paymentRequestId":123}`; tạo hoặc trả lại QR đã phát hành |
| POST | `/api/v1/integrations/vietqr/token_generate` | Basic credentials callback riêng; trả `access_token`, `token_type`, `expires_in` |
| POST | `/api/v1/integrations/vietqr/transaction-sync` | Bearer callback riêng; lưu audit và đối soát; ACK chỉ xác nhận tiếp nhận |
| GET | `/api/v1/payments/{paymentCode}/status` | Tài khoản ACTIVE có quyền trên booking; chỉ đọc DB |
| GET | `/api/v1/provider/vietqr/callbacks?page=0` | Provider ACTIVE; 20 callback/trang, chỉ dữ liệu thuộc Provider |
| GET | `/api/v1/admin/vietqr/callbacks?page=0` | Admin ACTIVE; gồm callback không tìm thấy order |
| GET | `/api/v1/payments/receipt?reference=...` | Biên lai DB hiện có, hỗ trợ VIETQR_CALLBACK |

Body callback gồm `transactionid`, `transactiontime` (epoch milliseconds), `referencenumber`, `amount` (VND nguyên), `content`, `bankaccount`, `orderId`, `transType`. Trường không dùng như terminalCode/sign không được coi là bằng chứng thanh toán; xác thực dùng token riêng theo hợp đồng callback.

Response thành công theo hợp đồng VietQR: `{"error":false,"errorReason":"","toastMessage":"...","object":{"reftransactionid":"..."}}`. Đây là ACK sau khi lưu DB, kể cả trường hợp cần đối soát. Lỗi xác thực trả 401; payload ID bị thay đổi trả 409; lỗi hạ tầng/transaction không trả ACK thành công để VietQR có thể retry.

QR đã phát hành không cho đổi snapshot tài khoản, cấp lại hoặc chuyển sang VNPay. Pending/paid VNPay cũng chặn tạo QR. QR hết hạn hoặc cần đối soát bị ẩn khỏi màn hình thanh toán. Mã VQ có đúng 6 chữ số, hết không gian thì dừng cấp mã; không quay vòng hoặc tăng lên 7 chữ số. Mã ledger callback dùng prefix VT riêng.

API mô phỏng và seed thanh toán nội bộ đã bị loại bỏ cùng service/DTO/frontend client liên quan. Xác nhận/chứng từ thủ công chỉ giữ cho yêu cầu cũ chưa phát hành QR callback; backend chặn các thao tác này đối với VietQR Callback. Sandbox payout hiện có vẫn chỉ ghi mô phỏng payout và không được xem là xác minh ngân hàng.

## Cấu hình và migration

Chạy `Backend/EcoMotion/src/main/resources/db/migrations/20261102_vietqr_callback.sql` sau các migration hiện có, trước khi triển khai code. Migration thêm bảng `vietqr_callback`, trạng thái liên kết ngân hàng và các trường QR trên PaymentRequest, unique index orderId/transaction ID. Không tự đánh dấu bất kỳ ngân hàng hiện có nào là đã liên kết. Migration đã được kiểm tra chạy lặp trên PostgreSQL 18 trong cluster kiểm thử riêng; chưa chạy trên DB ứng dụng.

Sao chép cấu hình từ `application.properties.example` vào cấu hình triển khai hoặc dùng biến Spring tương ứng. Không commit file cấu hình thật. Các biến trong template:

- `VIETQR_ENABLED=false` mặc định.
- `VIETQR_ENVIRONMENT=sandbox`, `VIETQR_BASE_URL=https://dev.vietqr.org`; production dùng `production` và `https://api.vietqr.org`. Profile prod/production không được bật Sandbox.
- `VIETQR_USERNAME`, `VIETQR_PASSWORD`: do VietQR cấp cho API đi ra.
- `VIETQR_CALLBACK_USERNAME`, `VIETQR_CALLBACK_PASSWORD`: credentials riêng cung cấp cho VietQR gọi vào.
- `VIETQR_CALLBACK_SECRET`: khóa riêng tối thiểu 32 byte để ký token callback, khác JWT đăng nhập.
- `VIETQR_CONNECT_TIMEOUT`, `VIETQR_READ_TIMEOUT`, token skew và callback token TTL có trong template.

Không bật DEBUG logging body HTTP trên môi trường vận hành vì response cấp token chứa bearer token.

Sau khi vận hành xác minh tài khoản Provider đã đăng ký thành công trong dịch vụ đồng bộ VietQR, ghi nhận `provider_bank_account.vietqr_linked_at` trên đúng tài khoản qua quy trình DB có kiểm soát. Chưa có API tự động onboarding ngân hàng; Provider không có API tự đánh dấu linked. Đổi ngân hàng/số tài khoản/chủ tài khoản sẽ xóa marker liên kết; snapshot của yêu cầu đã phát hành không đổi. Callback hợp lệ tới snapshot cũ vẫn được xử lý sau khi Provider đổi tài khoản.

## Kiểm thử và nghiệm thu

Các suite chính:

- `VietQrCallbackIntegrationTest`: QR, quyền truy cập, token HTTP riêng, cọc/cuối kỳ, giao dịch lệch, dispute, hết hạn, lịch chồng, hai gateway, retry, concurrency, rollback, snapshot và tách nguồn tiền.
- `VietQrGatewayTest`: hợp đồng HTTP Get Token/Generate, cache token, timeout, tài khoản trả về sai, token callback hết hạn, host production và cạn mã VQ.
- `PaymentIntegrityIntegrationTest`, `EndUserWorkflowIntegrationTest`: hồi quy thanh toán, biên lai và workflow hiện có.
- `e2e/vietqr-callback.spec.js`: QR động, lỗi tài khoản chưa liên kết, phục hồi sau reload, tự mở biên lai và Provider không xác nhận tay. Các route API trong Playwright được mock; không phải callback ngân hàng thực tế.

Kết quả kiểm tra ngày 02/11/2026:

| Kiểm tra | Kết quả |
| --- | --- |
| Toàn bộ Maven backend trên H2 | 235 ca: 216 pass, 19 skipped theo điều kiện suite, 0 failure/error |
| PostgreSQL 18 cô lập | 23 ca VietQR + 29 ca PaymentIntegrity pass; 6 ca gateway unit pass trong cùng lượt chạy |
| Migration PostgreSQL | Chạy hai lần thành công, giữ nguyên hàng dữ liệu có sẵn |
| Frontend unit | 28/28 pass |
| Frontend build | Pass; còn cảnh báo chunk trên 500 kB |
| Playwright toàn bộ | 53/53 pass; API mock |
| Playwright VietQR sau chỉnh sửa cuối | 3/3 pass |
| ESLint các file frontend thay đổi | 0 error; 3 warning có sẵn về cleanup ref ở ProviderFinancePage |
| VietQR Sandbox bên ngoài | Chưa chạy; thiếu tài khoản/cấu hình liên kết và callback công khai |

Maven wrapper hiện có lỗi khởi động trên môi trường này nên đã dùng Maven 3.9.16 đã cache, JDK 21 và repo dependency cục bộ. Kiểm thử PostgreSQL dùng cluster riêng trong `Backend/EcoMotion/target/vietqr-pg`, cổng 55439, database `tv5_test`; không dùng database ứng dụng và đã dừng cluster sau kiểm tra. Không commit log, screenshot sinh từ test, database test hoặc credentials.

Để nghiệm thu Sandbox thực tế:

1. Lấy credentials Sandbox, đăng ký tài khoản nhận của Provider với VietQR và xác minh liên kết trước khi đặt marker trong DB.
2. Đăng ký hai URL HTTPS công khai token_generate/transaction-sync của EcoMotion và Basic credentials callback riêng.
3. Với booking có hold còn hạn, tạo QR qua API của Renter. Ghi nhận đúng amount/content/account của response, không tự sửa nội dung.
4. Dùng token API đi ra gọi POST `https://dev.vietqr.org/vqr/bank/api/test/transaction-callback`, body gồm bankAccount, bankCode, amount, content và transType C.
5. Kiểm tra audit có transaction ID, ledger chỉ tăng một lần, nguồn tiền là trực tiếp Provider, hold ACTIVE, booking CONFIRMED, biên lai DB SUCCESS và UI tự cập nhật. Lặp callback, thử sai số tiền, hết hạn và mạng gián đoạn.
6. Chỉ nghiệm thu sau khi quan sát callback từ VietQR thực sự. Không gọi API Test Callback trên production.

Người dùng xác nhận ngày 02/11/2026 chưa có/cấu hình Sandbox. Vì vậy bước này còn chờ điều kiện ngoài repository; không có kết quả nhận tiền thực tế hoặc nghiệm thu live trong thay đổi này. Audit lệch cần vận hành đối soát/hoàn tiền; hệ thống không tự áp dụng lại một audit đã REVIEW_REQUIRED khi retry. QR đã phát hành không được quay vòng để tránh nhận tiền theo mã cũ. Dữ liệu lịch sử có appliedAmount=0 cần đối soát riêng, không tự suy đoán để backfill.

## Tài liệu tích hợp

- [VietQR Get Token](https://api.vietqr.vn/vi/api-vietqr-callback/goi-api-get-token)
- [Generate Dynamic QR](https://api.vietqr.vn/vi/api-vietqr-callback/goi-api-generate-vietqr-code)
- [Callback Get Token](https://api.vietqr.vn/vi/api-vietqr-callback/api-get-token)
- [Transaction Sync](https://api.vietqr.vn/vi/api-vietqr-callback/api-transaction-sync)
- [Sandbox Test Callback](https://api.vietqr.vn/vi/api-vietqr-callback/goi-api-test-callback)

## Danh sách file thay đổi

So với commit nền `31f063d`; gồm 53 file (có hai DTO mô phỏng bị xóa). Không xóa thêm báo cáo Markdown hoặc tài liệu kiến trúc chung. Báo cáo TV5 cũ đã được dọn ở commit nền; các tham chiếu cũ trong AGENT_CONTEXT chuyển sang lệnh đọc lịch sử Git.

```text
AGENT_CONTEXT.md
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/BookingServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/service/implement/RenterPaymentServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/config/SecurityConfig.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/common/exception/GlobalExceptionHandler.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/config/VietQrClientConfig.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/config/VietQrProperties.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/PaymentRequestController.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/controller/VietQrController.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/request/SimulatePaymentRequest.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/request/VietQrTransactionSyncRequest.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/PaymentRequestResponse.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/SimulatePaymentResponse.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/dto/response/VietQrCallbackResponse.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/entity/PaymentRequest.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/entity/ProviderBankAccount.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/entity/VietQrCallback.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/enums/PaymentVerificationMethod.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/repository/PaymentRequestRepository.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/repository/VietQrCallbackRepository.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/PaymentRequestService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/VietQrCallbackAuthService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/VietQrPaymentReconciliationService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/VietQrPaymentService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/VietQrTokenService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/VietQrTransactionSyncService.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/gateway/VietQrPaymentGatewayStrategy.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/gateway/implement/VietQrPaymentGatewayStrategyImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentCodeGeneratorImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentReceiptServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/PaymentRequestServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/ProviderFinanceServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VietQrCallbackAuthServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VietQrPaymentReconciliationServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VietQrPaymentServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VietQrTokenServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VietQrTransactionSyncServiceImpl.java
Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/finance/service/implement/VnpayServiceImpl.java
Backend/EcoMotion/src/main/resources/application.properties.example
Backend/EcoMotion/src/main/resources/db/migrations/20261102_vietqr_callback.sql
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/EndUserWorkflowIntegrationTest.java
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/controller/VietQrCallbackIntegrationTest.java
Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/finance/service/gateway/VietQrGatewayTest.java
Frontend/EcoMotion/e2e/vietqr-callback.spec.js
Frontend/EcoMotion/package.json
Frontend/EcoMotion/src/components/payments/DynamicVietQr.jsx
Frontend/EcoMotion/src/components/payments/VietQrCallbackHistory.jsx
Frontend/EcoMotion/src/pages/CustomerPaymentPage.jsx
Frontend/EcoMotion/src/pages/PaymentSuccessPage.jsx
Frontend/EcoMotion/src/pages/ProviderFinancePage.jsx
Frontend/EcoMotion/src/services/paymentService.js
Frontend/EcoMotion/src/services/providerFinanceService.js
docs/VIETQR_CALLBACK.md
```
