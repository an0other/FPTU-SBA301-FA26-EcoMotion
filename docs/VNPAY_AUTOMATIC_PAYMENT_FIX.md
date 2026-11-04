# VNPAY automatic payment — investigation and targeted fix

Ngày: 04/11/2026. **Chưa thể kết luận FIXED hoặc MERGE READY:** chưa có IPN thực hoặc giao dịch sandbox mới được đối chiếu với database. Tất cả thay đổi chưa commit trước đó được giữ nguyên.

## 1. Root cause và bằng chứng

- `finance/service/implement/VnpayServiceImpl.java`, `processIpn` và `applyVerifiedPayment`: source hiện tại đã tự xác nhận thanh toán khi IPN/QueryDR được xác thực và điều kiện booking còn hợp lệ. PaymentTransaction dùng `PAID` (không phải enum `SUCCESS`), `appliedAmount` và `GATEWAY_VERIFIED`; PaymentRequest chuyển `CONFIRMED`, Booking chuyển `CONFIRMED`, và thông báo phát sau commit. Provider không phải gọi API xác nhận VNPAY. Booking approval vẫn là nghiệp vụ riêng.
- `ProviderFinancePage.jsx`, bảng payment request: trước thay đổi, mọi request chưa `CONFIRMED` đều hiện nút “Xác nhận” mà không chỉ ra đó là chuyển khoản trực tiếp. `PaymentRequestServiceImpl.confirmPayment` tạo transaction **BANK_TRANSFER** / `PROVIDER_CONFIRMED`, không xác thực transaction VNPAY. Hai phương thức chia sẻ payment request, nên giao diện có thể khiến provider tưởng phải xác nhận VNPAY. Đây là lỗi UI đã xác định; không phải bằng chứng provider thực sự đã thao tác hoặc IPN của giao dịch lịch sử đã thất bại.
- Cấu hình local file `Backend/EcoMotion/src/main/resources/application.properties` mặc định IPN là `http://localhost:8080/api/v1/payment/vnpay/ipn`, không phải HTTPS công khai. Biến `VNPAY_IPN_URL` không có trong shell kiểm tra; process khác vẫn có thể có môi trường riêng. `VnpayPaymentGatewayStrategy.createPaymentUrl` gửi `vnp_ReturnUrl`, không gửi `app.vnpay.ipn-url` cho gateway. Vì vậy chỉ thay property không tự đăng ký IPN ở VNPAY. Chưa biết merchant portal thực tế đang đăng ký URL nào, chưa có log IPN thật hay transaction reference thật để kết luận root cause giao dịch cụ thể.

## 2. Thay đổi

- `PaymentRequestResponse` thêm projection `vnpayVerifying`; `PaymentRequestServiceImpl.toResponse` tính từ transaction VNPAY PENDING cùng booking/loại nghĩa vụ, trừ request đã confirmed. Đây là trạng thái trình bày, không thêm entity/trạng thái lưu. `ProviderFinancePage` hiển thị “Đang xác minh thanh toán VNPAY”, giải thích xác minh tự động và đặt tên thao tác hiện hữu là “Xác nhận chuyển khoản” để phân biệt. Khi cọc confirmed, màn hình ghi “Đã thanh toán cọc”. `ProviderBookingDetailPage` dùng cùng nhãn. Xác nhận chuyển khoản thực tế vẫn hoạt động độc lập; API thủ công không biến transaction VNPAY thành SUCCESS.
- `VnpayDepositSynchronizationIntegrationTest.providerRequestShowsGatewayVerificationUntilIpnIsApplied` kiểm tra projection trước và sau IPN ký bằng secret test. Không sửa thuật toán phân công, cơ chế nhận IPN hoặc workflow chuyển khoản. Không có migration. Lưu ý projection hiện tra ledger theo booking trong `toResponse`; danh sách request lớn có thể phát sinh thêm query cho mỗi dòng, cần đo và batch nếu tải cao.

## 3. Payment state machine và an toàn

Trước/sau thay đổi này, đường backend giữ nguyên: VNPAY `PENDING` → IPN hợp lệ hoặc QueryDR xác thực → `PAID`/`GATEWAY_VERIFIED`/applied amount → payment request `CONFIRMED` → booking `CONFIRMED`, nếu hold, số tiền, xe và thời hạn hợp lệ. Giao dịch thành công không áp dụng được lưu `PAID`, applied amount 0, `RECONCILIATION_REQUIRED`, không xác nhận booking. Duplicate IPN trả `02`, không ghi tiền hai lần. Return callback là read-only và status query có xác thực; chưa nhận IPN thì hiển thị đang xác minh. Payment request không có field lưu tên `PAID`: trạng thái là `CONFIRMED`; UI booking tính projection `DEPOSIT_PAID`.

`VnpayPaymentGatewayStrategy.verifyInternal` kiểm merchant, HMAC và hai mã thành công; `processIpn` kiểm reference, khóa booking và transaction; `applyVerifiedPayment` kiểm amount và điều kiện. Cơ chế notification dùng after commit. HTTP IPN public được `SecurityConfig` cho phép; response DTO dùng JSON `RspCode`, `Message`. Chưa quan sát HTTP thực từ VNPAY, do đó chưa thể khẳng định delivery, retry, hay response thực tại merchant.

## 4. IPN và database thực

- Mặc định local là HTTP localhost như trên; không thể là callback public trực tiếp. Chưa xác minh URL đăng ký ở merchant sandbox hoặc trạng thái retry trên dashboard.
- Để kiểm thử thật, chạy backend trên staging HTTPS hoặc tunnel HTTPS tới cổng backend; đăng ký chính xác `https://<public-host>/api/v1/payment/vnpay/ipn` ở merchant sandbox theo hướng dẫn VNPAY. Đảm bảo tunnel chuyển GET và query string nguyên vẹn. `VNPAY_IPN_URL` chỉ là cấu hình ứng dụng; phải xác nhận URL thực sự được đăng ký phía VNPAY. Không gửi secret qua URL hay log.
- Tạo giao dịch sandbox mới với booking hợp lệ, lưu mã tham chiếu an toàn; ghi trạng thái database trước, Network response, IPN log sau commit và database sau. Kiểm tra payment transaction, payment request, hold, booking và provider/renter; gửi lại cùng IPN chỉ trong môi trường kiểm thử được phép. Kiểm tra cả trường hợp return trước IPN và đóng browser. Không dùng return query làm bằng chứng thanh toán.
- **Database thật trước/sau:** chưa đo trong phiên: chưa có giao dịch sandbox mới hoặc reference, không sửa dữ liệu shared. Trong test H2 cô lập, assert trước IPN là PENDING/đang xác minh; sau IPN ký test là PAID, request CONFIRMED, booking CONFIRMED, applied amount và hold ACTIVE. Đây không phải dữ liệu ngân hàng thực.

Theo [tài liệu VNPAY payment/IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), IPN là kênh server-to-server và return URL là browser redirect; [tài liệu thuật toán/HTTP IPN](https://sandbox.vnpayment.vn/apis/docs/chuyen-doi-thuat-toan/changeTypeHash.html) mô tả IPN GET và phản hồi JSON. Cấu hình merchant và việc retry thực tế cần xác minh với tài khoản sandbox của dự án.

## 5. Test evidence

- Backend: Maven test chọn `VnpayDepositSynchronizationIntegrationTest,Tv5FinancialSafetyIntegrationTest`: **36/36 pass**, H2 in-memory, secret/gateway test; log `Backend/EcoMotion/target/vnpay-auto-fix.log`. Bao gồm IPN signed test, duplicate/concurrent, return trước IPN, số tiền/chữ ký sai, hold hết hạn, projection provider và manual transfer. Không phải sandbox thật.
- Frontend unit: **31/31 pass**. Production build pass, có warning bundle size hiện hữu. ESLint hai file thay đổi: 0 lỗi, 4 warning ref cleanup hiện hữu.
- PostgreSQL, browser fullstack và VNPAY sandbox **không chạy lại trong lượt này**. Các kết quả trong báo cáo trước không được tính là xác minh code mới.

## 6. Giới hạn còn lại

Chưa có bằng chứng registered IPN, request/network response sandbox, retry, mapping/reference thực hoặc DB trước/sau thật. Cần chạy test sandbox và browser đa actor với backend/database thật trước khi gọi luồng đã được xác minh. Manual confirmation ghi nhận **chuyển khoản trực tiếp** theo quyền provider và có thể vẫn được hiển thị khi VNPAY đang chờ, vì payment request chung có thể đồng thời có giao dịch chuyển khoản thực; thao tác đó không chứng minh VNPAY. Việc ngăn/làm rõ chuyển khoản đồng thời cần đối soát nghiệp vụ khi có bằng chứng tiền thực, không giả định một phương thức duy nhất.
