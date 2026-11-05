# Kết quả sửa 3 lỗi vòng đời đơn

Đã triển khai và nghiệm thu ngày 05/11/2026.

| Lỗi | Kết quả |
|---|---|
| Kết thúc chuyến trả lỗi máy chủ | Lỗi kết nối thông báo được cách ly khỏi giao dịch đã lưu. API xác nhận kết thúc `BK-1P9A55` trả thành công; gọi lại giữ nguyên phản hồi và các mốc xác nhận. |
| Hủy có cọc nhưng không có yêu cầu hoàn | `EM-SEED-R12-001` ở `CANCELLATION_REVIEW`, có nghĩa vụ hoàn 330.000đ và nhà xe xem được yêu cầu hoàn đang xử lý. |
| Seed thanh toán thiếu liên kết | Đã bổ sung yêu cầu cọc `CONFIRMED`, liên kết giao dịch thu cũ và phục hồi metadata phân bổ dựa trên snapshot. Biên lai thật đọc được số tiền 330.000đ. |

Không tạo thêm giao dịch thu tiền. Số tiền thu, thời điểm thu, mã ngân hàng và thời điểm hủy cũ được giữ nguyên. Không sửa đơn ngoài R12; đơn BK đã đúng trạng thái nên không bị viết lại. Khoản hoàn đang chờ xử lý, chưa được đánh dấu đã chuyển tiền.

## Kiểm chứng

- Backend: 244 ca tổng cộng, 223 ca đạt, 21 ca bỏ qua theo điều kiện; không có ca thất bại.
- PostgreSQL riêng: 4 ca xác nhận hai phía/hủy đồng thời và kiểm tra thứ tự đều đạt.
- Frontend: 31 kiểm thử đơn vị và 22 kiểm thử trình duyệt đều đạt.
- Máy chủ thật: đã thử kết nối SSE hết hạn và xác nhận thao tác đã commit vẫn trả thành công.
- API và giao diện thật: đọc biên lai, trạng thái đối soát hủy, danh sách hoàn của nhà xe; đã lưu ảnh và kết quả cục bộ.
- Xem trước không ghi dữ liệu; bản xem trước cuối báo `UNCHANGED` cho cả hai đơn. Kiểm thử áp dụng hai lần không tạo trùng.
- Đã sao lưu database trước khi sửa; bản sao lưu cục bộ `before-fix.dump` không đưa vào Git.

## Sau khi dọn code

Bộ chạy phục hồi và các script kiểm tra một lần đã được gỡ sau khi nghiệm thu. [Ghi chú dọn code](README.md).

Không thay đổi API công khai hoặc schema. Các file video của lần quay trước được giữ nguyên; chúng phản ánh trạng thái trước khi sửa.
