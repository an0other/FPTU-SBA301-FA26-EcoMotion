# Chính sách thanh toán EcoMotion — 08/11/2026

Quyết định nhóm trong chat: checkout thông thường dùng VietQR động hoặc VNPay với số tiền do backend tạo; khách không tự nhập số tiền. Không triển khai tự động hoàn phần dư. Tiền thực nhận sai số tiền phải được giữ đầy đủ và đối soát thủ công, không tự công nhận thành công.

## Nguồn xác minh thực tế

| Kênh | Backend thực hiện | Điều không chứng minh đã nhận tiền |
|---|---|---|
| VietQR | `PaymentRequestServiceImpl.buildVietQrUrl`: tạo URL ảnh `img.vietqr.io` với amount/addInfo/accountName. Provider đối chiếu tài khoản nhận và xác nhận, lưu `PROVIDER_CONFIRMED`. Chưa tích hợp bank webhook/account statement API. | Quét QR, tạo ảnh QR, tải biên lai và số tiền do khách khai không tự sinh ledger PAID. Không khẳng định mọi ngân hàng khóa sửa số tiền. |
| VNPay IPN | Strategy xác thực HMAC SHA-512 và merchant; service đối chiếu TxnRef, amount với transaction trong DB, mã giao dịch gateway và trạng thái. Khóa booking/transaction, xử lý idempotent. | Browser return hoặc callback thiếu chữ ký/sai số tiền không chứng minh thu tiền. |
| VNPay QueryDR | Strategy xác thực chữ ký phản hồi server, merchant, reference, command/type/status. `applyVerifiedPayment` tiếp tục kiểm tra amount từ phản hồi thực tế. | Không coi trạng thái return URL là thành công; query chưa rõ giữ PENDING. |

Đối chiếu QueryDR: có chỗ dựng verification result bằng expected amount ở luồng định kỳ, nhưng guard cuối **vẫn so sánh `params.vnp_Amount` thật với DB**, nên chưa có bằng chứng số tiền sai được xác nhận chỉ vì đoạn dựng DTO này. Kiểm thử guard phải được giữ.

## Thanh toán đúng số tiền

Backend chốt nghĩa vụ và mã tham chiếu, giữ booking thường `AWAITING_DEPOSIT` 15 phút. Thanh toán hợp lệ và hold/lịch còn hiệu lực mới được áp nghĩa vụ, xác nhận booking. Gateway nhận lặp không ghi tiền lần hai. Khoản xác thực đến muộn hoặc booking không đủ điều kiện được đối soát, không tái kích hoạt lịch trái phép. Không thay đổi 7 bản sửa P1.

Xác nhận thủ công legacy vẫn hỗ trợ số thực nhận một phần và metadata tùy chọn để giữ contract hiện có; không phải cơ chế auto-verify VietQR. Chỉ thao tác sau khi provider đối chiếu ngân hàng. Checkout chuẩn luôn dùng số backend; khi khoản sai số tiền cần tách khỏi nghĩa vụ để tra soát, sử dụng thao tác ngoại lệ dưới đây. Chưa có bằng chứng kỹ thuật tự động kiểm tra sự trung thực của provider khi họ bấm xác nhận.

## Ngoại lệ tiền ngân hàng đã thực nhận

Provider trên `/provider/finance` chọn **Tiền nhận cần đối soát**, nhập số thực nhận, mã giao dịch ngân hàng, thời điểm ghi có, tham chiếu sao kê và xác nhận đã đối chiếu tài khoản nhận. Có thể dùng với yêu cầu đã hết hạn/hủy hoặc gateway còn pending để không làm thất lạc khoản ngân hàng đã nhận; không đóng hoặc sửa giao dịch gateway chưa rõ.

Endpoint: `POST /api/provider/payment-requests/{id}/bank-reconciliation` (alias `/api/v1/...`). Chỉ provider sở hữu booking được thao tác. Body ví dụ minh họa, không gọi ngân hàng:

```json
{
  "receivedAmount": "40000",
  "bankTransactionRef": "BANK-TEST-001",
  "bankStatementReference": "STATEMENT-TEST-001",
  "bankReceiptVerified": true,
  "reconciliationId": "stable-unique-request-key",
  "receivedAt": "2026-11-08T01:00:00Z",
  "note": "Đã đối chiếu khoản ghi có trong tài khoản nhận"
}
```

- Xác minh sao kê là **attestation của provider được phân quyền**, không phải xác minh trực tiếp với ngân hàng qua API. Biên lai khách không thay thế điều kiện này.
- Ledger lưu toàn bộ số thực nhận là IN/PAID, nhưng `appliedAmount = 0`; không cộng vào `PaymentRequest.paidAmount`, không tạo refund tự động. Receipt hiển thị `RECONCILIATION_REQUIRED`.
- Lưu tài khoản nhận gốc, mã ngân hàng, thời điểm thực nhận, người xác minh và tham chiếu sao kê. JSON đầy đủ lưu trong `FinancialReviewRecord` hiện có, target `BANK_RECEIPT`; lịch sử giao dịch hiện có trả processingNote/mã ngân hàng. Không thêm bảng/module tài chính.
- Khóa booking → payment request → tài khoản nhận. Retry cùng key/payload idempotent; cùng key khác payload bị chặn. Mã ngân hàng theo tài khoản chỉ được ghi một lần; confirm thủ công dùng cùng khóa tài khoản để tránh race giữa hai đường vào.
- Booking chờ cọc hoặc đã hủy/từ chối chuyển sang `CANCELLATION_REVIEW` và nhả lịch; không thành CONFIRMED. Chuyến đang thực hiện giữ trạng thái nghiệp vụ nhưng khoản chưa phân bổ tiếp tục chặn quyết toán theo guard hiện có.
- Chưa chốt phân bổ thì giữ review. Không tự áp phần vừa đủ, không tự hoàn phần dư và không tạo ví. Quyết định hoàn cụ thể do người vận hành thực hiện riêng.

## Đóng đối soát bằng hoàn thủ công

Nếu operator quyết định trả toàn bộ khoản ngoại lệ, dùng API/UI hoàn hiện có: tạo yêu cầu hoàn (`POST /api/refunds` theo route controller), duyệt, chuyển thật ngoài hệ thống và xác nhận chứng từ/mã trả theo quy trình hiện hành. Các test chỉ mô phỏng, không thực hiện chuyển tiền thật.

`CancellationClosureServiceImpl` chỉ tự kết thúc **review mới thuộc BANK_STATEMENT** khi mọi khoản thực nhận liên kết đã được hoàn đủ qua refund xác minh, không còn gateway PENDING hoặc biên lai chưa xử lý. Yêu cầu thành VOIDED; còn nghĩa vụ/refund/dispute khác thì booking vẫn review. Hoàn một phần hoặc chỉ dự trữ khoản hoàn không đủ để đóng. Khách báo chưa nhận tiếp tục dùng P1 reconciliation, không tự gửi lại tiền.

Không bổ sung thao tác phân bổ số tiền ngoại lệ sau review trong đợt này. Đường tối thiểu an toàn là giữ tiền thực nhận để đối soát và hoàn thủ công có phê duyệt; không hứa tự hoàn tất booking từ khoản sai số tiền.

## Những việc chưa được chứng minh ngoài đời

Chưa có bank callback cho VietQR; chưa xác minh sao kê/provider attestation với ngân hàng thật; chưa thực hiện thanh toán/hoàn thật hoặc chứng nhận VNPay production. Unit/integration/MockMvc/PostgreSQL và frontend build không thay thế browser E2E bốn vai trò hoặc nghiệm thu ngân hàng.
