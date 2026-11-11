# Demo xem đánh giá và hình ảnh

## Vào màn hình

1. Chạy FE và BE bản mới, đăng nhập renter.demo@ecomotion.vn (mật khẩu seed EcoMotion@123 nếu chưa đổi).
2. Mở `/renter/bookings/BK-1X8C44`. Khi đơn ở trạng thái được hiển thị tài xế (IN_PROGRESS ở fixture mới), bấm **Xem đánh giá tài xế**.
3. Trang `/drivers/{driverId}/reviews` dùng ảnh portrait như thẻ tài xế, có fallback avatar/chữ cái. Không dùng ảnh review làm ảnh hồ sơ.

## Các điểm kiểm tra UI

- Điểm trung bình hiển thị sao có phần lẻ, không làm tròn 4,7 thành đúng 5 sao.
- Gallery ảnh ở đầu trang và ảnh trong từng nhận xét mở cùng lightbox. Dùng nút Trước/Sau hoặc phím trái/phải; Escape/Đóng thoát, focus quay lại điểm mở.
- Kết hợp bộ lọc sao, có ảnh và từ khóa; đổi thứ tự mới/cũ/điểm cao/thấp; Bỏ bộ lọc để trở lại.
- Xem thêm chỉ mở thêm nhận xét đã tải, không phải phân trang API. Khi tổng vượt số đã tải, trang ghi rõ API giới hạn 100 nhận xét gần nhất; bộ lọc/gallery chỉ áp dụng tập đã tải.
- Kiểm tra ảnh lỗi, dữ liệu rỗng, không khớp bộ lọc, API lỗi và nút Thử lại. Mobile: filter/gallery cuộn ngang, không ép trang tràn chiều ngang.
- Chuyển từ tài xế này sang tài xế khác phải reset filter/gallery, không hiện dữ liệu người trước trong lúc tải.

## Seeder ảnh review riêng

File `DriverReviewMediaDemoSeeder.java`, không thêm code vào DevDataSeeder. Chỉ active với profile `dev` và không có `prod`; `app.demo-seed.enabled` và `app.demo-seed.review-media.enabled` mặc định true như hệ seed demo hiện tại. Đặt một trong hai false để tắt.

Khi backend khởi động, seeder xét whitelist booking seed BK-6A1M01/BK-6E5R05/BK-6J9V09/BK-AUG-01/BK-AUG-05/BK-SEP-01/BK-SEP-05, chỉ thêm tối đa 3 review tài xế khi booking COMPLETED, có tài xế/xe và **chưa có review tài xế**. Tạo review DEMO 4 sao, gắn tối đa 2 ảnh có sẵn từ gallery xe. Bình luận ghi rõ đây là ảnh minh họa từ bộ ảnh xe, không phải ảnh khách chụp chuyến thật. Điểm/count tài xế được tính lại từ các review sau khi thêm, nên dữ liệu tổng hợp dev có thể thay đổi.

Không sửa review đã có, không tạo booking, không đổi trạng thái/tiền/GPLX. Chạy lại sẽ bỏ qua các review đã tồn tại. Nếu không còn booking đủ điều kiện hoặc thiếu ảnh xe, log ghi added 0; không khẳng định mọi DB cũ tự xuất hiện ảnh. Không reset DB hoặc xóa review để ép có ảnh.

Muốn test ảnh upload thật trong hệ thống: dùng một booking demo đã COMPLETED và chưa được review, vào màn hình viết đánh giá, upload JPG/PNG/WebP theo giới hạn hiện có rồi gửi. Đó là luồng khác với ảnh fixture. Đợt chỉnh UI không tự khởi động backend/seeder trên DB đang dùng.

## Nguồn tham khảo bố cục

- [Airbnb — review sorting](https://www.airbnb.com/help/article/1885): cách cho người dùng đổi thứ tự nhận xét.
- [Google Maps — reviews, ratings and photos](https://support.google.com/maps/answer/6230175): phân biệt nhận xét và hình ảnh do khách chia sẻ.

Áp dụng vào ngôn ngữ thiết kế EcoMotion, không sao chép branding hoặc giả lập nhãn xác minh chưa có dữ liệu.
