# Ghi chú sau khi dọn code

Dữ liệu demo đã được phục hồi và bộ chạy phục hồi một lần đã được gỡ khỏi code.

- Seeder giữ `SeedDepositPaymentSynchronizer` để tạo/liên kết yêu cầu cọc nhất quán, không thu tiền trùng và không đổi trạng thái đơn đã thao tác.
- Các cờ `demo-repair`, runner phục hồi và kiểm thử chỉ dành cho runner đã được xóa.
- Annotation Spring cần cho việc khởi tạo bean, giao dịch và profile `dev` vẫn được giữ. Annotation dùng tên đầy đủ đã chuyển sang import thông thường trong các file đã chỉnh.
- Video hướng dẫn, ảnh nghiệm thu và bản sao lưu `before-fix.dump` được giữ lại.
- Log, bản chụp JSON, script quay/kiểm tra tạm và video thô trùng lặp đã được dọn.

Xem [kết quả nghiệm thu trước khi dọn](RESULTS.md). Không cần chạy lại phục hồi trên database hiện tại.

Kiểm thử sau khi dọn: chạy lại từ bản biên dịch sạch, 215 ca backend đạt, 21 ca bỏ qua theo điều kiện, không có ca thất bại. Đã kiểm tra đủ 17 file video trong mục lục.
