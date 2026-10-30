# Sửa luồng thanh toán VNPay — 29/10/2026

## Nguyên nhân có bằng chứng từ source

`VnpayServiceImpl.processCallback` trước sửa tính `success = verification.success() && transaction.getStatus() == PAID`, nhưng trả `message = verification.message()`. Gateway trả thông báo “Giao dịch thành công” cho `vnp_ResponseCode=00` ngay cả khi DB còn PENDING do IPN chưa đến. `VnpayCallbackPage` dùng duy nhất boolean success để chọn tiêu đề, hiển thị message nguyên trạng, không polling và luôn cho retry khi success=false. Vì vậy Return đến trước IPN đủ để tái hiện ba thông tin mâu thuẫn trong yêu cầu. HTTP 200 chỉ biểu thị API trả lời được, không chứng minh thanh toán thành công.

Các lỗi liên quan tìm thấy trong code:

- Gateway chỉ kiểm tra response code, chưa yêu cầu `vnp_TransactionStatus=00` và chưa đối chiếu merchant.
- Create URL chỉ tái sử dụng PENDING trong 14 phút, sau đó có thể tạo reference khác dù giao dịch trước chưa có kết quả cuối cùng.
- Query nhận bookingCode nhưng không đối chiếu với booking của giao dịch.
- Các route VNPay được public toàn bộ; service trước sửa cho phép currentUserId=null đi qua kiểm tra quyền tạo thanh toán và xem quyết toán.
- Có khóa transaction từ phần sửa có sẵn, nhưng chưa khóa booking đồng nhất giữa tạo giao dịch/IPN và chưa refresh entity đã đọc trước khi chờ khóa.
- Auto assignment được gọi ngay trong transaction thanh toán. Bắt exception không bảo đảm transaction ngoài không bị đánh dấu rollback-only khi service bên trong lỗi.
- Slot conflict hoặc booking không đủ điều kiện có thể biến một phản hồi gateway thành công thành Payment FAILED, khiến giao diện hiểu nhầm là có thể thanh toán lại.

Không có log giao dịch thật `EM-2026-000026` hoặc quyền truy cập merchant dashboard trong phiên này. Kết luận về thời điểm IPN của giao dịch đó dựa trên source và cấu hình, chưa phải xác nhận lịch sử giao dịch ngân hàng.

## Workflow sau sửa

1. Renter tạo URL qua API hiện có. Service bắt buộc định danh đúng renter, khóa và refresh booking, kiểm tra booking/payment request, đọc khoản cọc đã chốt từ charge/snapshot hoặc tính quyết toán từ ledger.
2. Giao dịch PENDING còn hiệu lực được tái sử dụng, giữ nguyên thời điểm tạo/hết hạn. Giao dịch chưa đối soát nhưng hết hạn hoặc đổi số tiền chặn tạo URL mới. Reference mới dùng UUID; DB có unique index bảo vệ reference.
3. Gateway ký HMAC-SHA512 trên tham số sắp xếp và URL encode UTF-8; số tiền gửi là VND nhân 100, chuyển sang long chính xác, không tràn hoặc làm tròn ngầm.
4. Return URL xác thực chữ ký, merchant, reference và amount, rồi chỉ đọc trạng thái DB. Không xác nhận booking hoặc chuyển trạng thái từ query của trình duyệt. Callback không hợp lệ không trả receipt của giao dịch.
5. IPN xác thực chữ ký và merchant, khóa booking trước transaction, refresh entity dưới khóa, đối chiếu số tiền, method/direction, yêu cầu đồng thời response code và transaction status bằng `00` để ghi PAID.
6. Trong cùng transaction, cập nhật payment, applied amount, verification method, charge, payment request, vehicle schedule và booking. Một nghĩa vụ đã có khoản PAID khác sẽ chuyển giao dịch đến đối soát thay vì xác nhận hai lần. Duplicate callback không thực hiện lại. Callback thất bại đến sau PAID không sửa payment hoặc booking.
7. Realtime notification chạy sau commit. Auto assignment chạy sau commit trong transaction riêng; lỗi phân công không rollback khoản tiền đã ghi nhận. Cơ chế phân công hiện có được giữ lại.
8. Frontend đọc `status` từ backend: SUCCESS, FAILED hoặc PENDING_VERIFICATION. Khi pending, gọi `/query` tối đa 40 lượt, mỗi lượt cách 3 giây sau khi request trước kết thúc; lỗi mạng cũng tính vào giới hạn. Cleanup hủy request và timer. Không suy ra tiền/mã đơn từ query chưa xác thực; retry chỉ hiển thị khi backend cho phép.
9. `/query` đối chiếu booking/reference và truy vấn server-to-server `querydr` tối đa theo khoảng cấu hình, mặc định 60 giây/giao dịch. Chỉ dùng phản hồi có chữ ký hợp lệ, đúng merchant/reference, amount, loại thanh toán, và trạng thái cuối cùng. `00` ghi nhận thành công; `02` ghi nhận thất bại. Chưa hoàn tất, không tìm thấy, lỗi checksum, trạng thái bất thường hoặc lỗi gateway tiếp tục pending và chặn retry. Dùng chung service cập nhật như IPN.

PaymentStatus lưu trữ vẫn là PENDING/PAID/FAILED; PENDING_VERIFICATION là trạng thái DTO, không thêm bảng hoặc trạng thái DB. Giao dịch nhận tiền nhưng không đủ điều kiện xác nhận booking được giữ PENDING với processing note yêu cầu đối soát, không tự động cho thanh toán lại hoặc giả lập thành công.

## API đã kiểm tra

Giữ nguyên controller và đường dẫn; các alias hiện có tiếp tục hoạt động.

| API chính | Hành vi |
| --- | --- |
| POST `/api/v1/payment/vnpay/create-url` và `/create-payment-url` | Kiểm tra renter, khóa booking, tái sử dụng giao dịch; chặn giao dịch chưa đối soát |
| GET/POST `/api/v1/payment/vnpay/ipn` | Gateway webhook, body RspCode/Message theo protocol; HTTP 200 có thể kèm RspCode=97 |
| GET `/api/v1/payment/vnpay/callback` | Xác thực browser return, trả receipt và trạng thái DB |
| GET `/api/v1/payment/vnpay/query?bookingCode=...&vnpTxnRef=...` | Kiểm tra cặp booking/reference, đọc DB và đối soát server-to-server khi cần |
| GET `/api/v1/payment/vnpay/settlement/{bookingCode}` | Đọc quyết toán, kiểm tra quyền |
| POST `/api/renter/bookings/{bookingCode}/payments/final/create` | Tái sử dụng VnpayService để tạo quyết toán |

DTO callback/query giữ các trường cũ, bổ sung `status` và `retryAllowed`. `success` tương thích cũ và chỉ true khi payment PAID. Query sử dụng reference ngẫu nhiên cùng booking code; endpoint này vẫn public theo thiết kế hiện có. Không dùng để cấp quyền sửa thanh toán.

SecurityConfig và CORS đã được đọc. Webhook vẫn public để gateway gọi được; thao tác tạo thanh toán/đọc quyết toán kiểm tra identity ở service. CORS dùng origin từ cấu hình. CORS của trình duyệt không giải quyết việc gateway không truy cập được localhost.

## File thay đổi trong lần sửa này

- `AGENT_CONTEXT.md`: ghi quyết định kiến trúc.
- `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/booking/entity/PaymentTransaction.java`: unique reference.
- `booking/service/BookingQueryService.java`, `booking/service/implement/BookingQueryServiceImpl.java` dưới cùng Java root: khóa booking, refresh booking/transaction dưới khóa.
- `finance/config/VnpayConfig.java`: bỏ credential/URL mặc định trong Java, thêm cấu hình đối soát.
- `finance/config/VnpayUtils.java`: encode UTF-8.
- `finance/dto/response/VnpayCallbackResultResponse.java`: trạng thái và retryAllowed.
- `finance/enums/PaymentVerificationMethod.java`: GATEWAY_VERIFIED.
- `finance/service/gateway/PaymentGatewayStrategy.java`: khả năng query tùy gateway, mặc định trả rỗng khi không hỗ trợ.
- `finance/service/gateway/implement/VnpayPaymentGatewayStrategy.java`: xác thực đầy đủ, constant-time checksum, QueryDr có timeout và xác thực phản hồi.
- `finance/service/implement/VnpayServiceImpl.java`: orchestration, idempotency, quyền, đối soát và đồng bộ booking/payment.
- `Backend/EcoMotion/src/main/resources/application.properties.example`: credential lấy từ môi trường, cấu hình đối soát.
- `Backend/EcoMotion/src/main/resources/db/migrations/20261029_vnpay_verification.sql`: mở rộng CHECK verification method và unique index.
- `Backend/EcoMotion/src/test/resources/application.properties`: credential giả cố định và tắt truy vấn gateway thật trong test.
- `finance/controller/VnpayPaymentFlowIntegrationTest.java`, `FinalPaymentFlowIntegrationTest.java`, `VnpayBookingFixture.java` dưới test Java root: fixture độc lập, các ca hồi quy và cạnh tranh.
- `finance/service/gateway/PaymentGatewayStrategyUnitTest.java`, `VnpayReconciliationTest.java` dưới test Java root: chữ ký thực sự được assert, gateway không phản hồi, checksum giả mạo, truy vấn có chữ ký.
- `Frontend/EcoMotion/src/pages/VnpayCallbackPage.jsx`, `src/services/paymentService.js`: ba trạng thái, bounded polling, AbortSignal, receipt từ backend và retry có điều kiện.
- `Frontend/EcoMotion/e2e/vnpay-callback.spec.js`: 7 ca browser, gồm mobile, refresh, polling và cleanup.
- Báo cáo này.

Các sửa đổi chưa commit khác đã có trong working tree trước khi bắt đầu được giữ nguyên. Không commit, push, merge hay tạo PR.

## Kiểm thử

Frontend: 86 unit test đã qua; 7 Playwright test đã qua; production build qua, còn cảnh báo bundle lớn hơn 500 KB. ESLint trên ba file frontend thay đổi qua.

Backend: bản cuối đã package thành công, 65 test qua, 0 failure, 0 error: 21 VNPay integration, 15 final payment integration, 6 gateway unit, 4 reconciliation, 6 notification và 13 driver eligibility. Integration test chạy với Spring/H2. QueryDr dùng HTTP server cục bộ trong test, ký phản hồi bằng test secret; không giả lập thanh toán thành công trong production.

| Yêu cầu | Kiểm chứng |
| --- | --- |
| Thành công | Deposit IPN → PAID/CONFIRMED, final IPN → PAID/COMPLETED; QueryDr thành công cũng áp dụng cùng workflow |
| Thất bại | IPN 24/02 ghi FAILED, booking không xác nhận; QueryDr 02 ghi FAILED |
| Chữ ký sai | Gateway unit test, IPN HTTP 200/RspCode=97; callback không lộ receipt |
| Số tiền sai | IPN RspCode=04; callback không nhận receipt và không ghi thành công |
| Booking/giao dịch không tồn tại | Lookup không tồn tại, signed unknown reference RspCode=01; query sai booking code bị từ chối |
| IPN trễ | PENDING hết hạn vẫn chặn tạo giao dịch; IPN trễ hợp lệ xác nhận |
| Callback lặp | Lần sau RspCode=02, không tạo booking/payment/charge mới |
| Return trước IPN | Callback và refresh đều pending; sau IPN query chuyển SUCCESS |
| Refresh kết quả | Backend đọc trạng thái; browser không gọi create-url |
| Retry | Chỉ giao dịch đã xác minh FAILED, booking/request còn hợp lệ và không còn nghĩa vụ PAID/PENDING mới được retry |
| Hai callback đồng thời | Hai thread, hai transaction thực qua service proxy; một 00, một 02; chỉ một payment PAID |
| Callback thất bại sau booking xác nhận | Payment vẫn PAID, booking vẫn CONFIRMED; callback trả SUCCESS theo ledger |

Bộ hồi quy mở rộng đã chạy thêm `PaymentRequestLifecycleIntegrationTest`: cả 8 ca lỗi ở fixture lookup `findByBookingCode(...).orElseThrow()` vì các mã seed cố định như `BK-4M1P88` không tồn tại. Đây là test chưa sửa trong task này; lỗi xảy ra trước khi gọi luồng thanh toán. Không coi toàn bộ test backend đã xanh.

Maven wrapper hiện lỗi `Cannot index into a null array`. Đã dùng Maven có sẵn trong cache, offline, với cache path được chỉ định rõ. Do `target` hiện hữu không clean được, build chạy qua một bản sao POM chỉ đổi output directory; dependency/plugin/source giữ nguyên. Build không thay pom.xml gốc và không dừng tiến trình ứng dụng đang chạy. Artifact/report được giữ dưới `Backend/EcoMotion/target/vnpay-validation` sau kiểm chứng.

## Cấu hình môi trường và giới hạn

Cấu hình local được kiểm tra có:

```text
return-url = http://localhost:5173/payment/vnpay/callback
ipn-url    = http://localhost:8080/api/v1/payment/vnpay/ipn
api-url    = https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
```

IPN mặc định là localhost HTTP, không phải endpoint HTTPS công khai. VNPay bên ngoài không gọi được địa chỉ này. Cần backend public HTTPS hoặc tunnel đến backend, đặt `VNPAY_IPN_URL=https://<public-host>/api/v1/payment/vnpay/ipn`, và đăng ký đúng URL này trong cấu hình merchant VNPay. `ipn-url` không được tự gửi như một tham số thanh toán; giá trị trong properties không tự đăng ký URL với gateway. Trong phiên này không truy cập được merchant dashboard và chưa xác nhận một IPN thật từ VNPay.

Điền `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAYMENT_URL`, `VNPAY_RETURN_URL`, `VNPAY_API_URL` theo merchant/môi trường của bạn. Template giữ ví dụ endpoint sandbox nhưng không chứa credential thật. Với frontend trên host khác, cập nhật origin CORS và Return URL tương ứng. Không đưa hash secret ra frontend.

Đối soát mặc định bật, cấu hình:

```properties
app.vnpay.reconciliation-enabled=${VNPAY_RECONCILIATION_ENABLED:true}
app.vnpay.server-ip=${VNPAY_SERVER_IP:127.0.0.1}
app.vnpay.query-timeout=${VNPAY_QUERY_TIMEOUT:5s}
app.vnpay.query-interval=${VNPAY_QUERY_INTERVAL:60s}
```

Đặt server IP thực theo yêu cầu gateway. Query là đối soát theo yêu cầu khi trang gọi API, không có job đối soát nền toàn bộ giao dịch. Timeout/kết quả chưa cuối cùng không được suy thành thất bại. Sau khi polling dừng, người dùng có thể kiểm tra lại trang kết quả/đơn thuê hoặc nhờ vận hành đối soát. Phản hồi thành công nhưng slot/booking không còn hợp lệ cần xử lý hoàn tiền/đối soát thủ công; không có hoàn tiền VNPay tự động mới trong thay đổi này.

Trước triển khai PostgreSQL hiện hữu, áp dụng migration theo cơ chế SQL thủ công của repository, sau các migration finance trước đó. Nếu có `vnp_txn_ref` trùng, unique index sẽ thất bại và rollback toàn bộ migration; cần điều tra các dòng trùng, không xóa/sửa cứng dữ liệu để migration qua. Không tự chạy migration hoặc sửa giao dịch thật trong phiên này. Migration PostgreSQL và khả năng cạnh tranh trên PostgreSQL chưa được chạy ở đây; test cạnh tranh đã chạy trên H2. Database có CHECK tùy chỉnh tên khác cho verification_method cần đối chiếu thêm trước triển khai.

Không thể kết luận giao dịch thật bị thiếu IPN đã được thanh toán chỉ từ response code trên Return URL. Cần IPN hợp lệ, QueryDr hợp lệ hoặc đối soát vận hành có chứng cứ.

## Tài liệu protocol đối chiếu

- [VNPay PAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [VNPay QueryDr/Refund: chữ ký request và response, response code so với transaction status](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html)
