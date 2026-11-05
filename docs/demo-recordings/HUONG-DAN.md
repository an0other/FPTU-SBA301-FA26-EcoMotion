# EcoMotion — Bộ video hướng dẫn

Mở `index.html` bằng Chrome/Edge, hoặc truy cập http://127.0.0.1:5174 khi máy chủ xem video đang chạy. Chọn phần ở bên trái và bấm mục lục để nhảy đến từng cảnh. Video WebM 1440×900, có chú thích tiếng Việt, không có lời thoại. Có thể mở từng file `.webm` riêng.

## Thứ tự xem

1. Tổng quan công khai: tìm xe, chi tiết, chính sách, đăng ký, kích hoạt.
2. Người thuê: hồ sơ, danh sách, chi tiết, thanh toán, đánh giá, thuê năm.
3. Nhà xe: đội xe, đăng xe, hồ sơ, lịch, tài xế, đơn và tài chính.
4. Tài xế: trạng thái làm việc, lịch và chi tiết chuyến.
5. Admin: tổng quan, người dùng, hồ sơ, xe và vận hành.
6. Các phần bổ sung: GPLX hợp lệ/không hợp lệ, tài xế chờ duyệt, giấy tờ và bảng giá, thao tác đặt xe, kết thúc chuyến, hủy và hoàn.

## Phân biệt phạm vi

- Các video tổng quan mở màn hình và xem dữ liệu seed thật từ backend. Chúng không phải bằng chứng mọi nhánh đã chạy đến cuối.
- Video 13 ghi thao tác tạo đơn thật trên cơ sở dữ liệu local, gồm báo giá, chọn điểm đón, xác nhận và giữ chỗ. Mã đơn tạo trong bản quay là `EM-2026-000043`.
- Các video 16–20 ghi các thao tác tiếp theo và kết quả thực tế, trong đó có xác nhận kết thúc chuyến seed, hủy đơn seed có cọc, nghĩa vụ hoàn và màn yêu cầu cọc của đơn vừa tạo.
- Không chạy thanh toán/hoàn tiền ngân hàng thật, không giả IPN thành công và không dùng ảnh biên lai giả.
- Chưa hoàn tất đăng ký/kích hoạt bằng OTP, CCCD, khuôn mặt; chưa đọc GPLX mới bằng OCR; chưa upload hợp đồng đã ký. Những bước này cần dữ liệu và dịch vụ thật tương ứng.
- Các bước tạo/sửa xe, giấy tờ, tài xế và duyệt được trình bày qua màn hình, biểu mẫu và dữ liệu mẫu; không khẳng định đã thực hiện đầy đủ mọi nhánh lưu/duyệt/từ chối.
- Nhánh tranh chấp, đổi xe/dời giờ, hoàn tiền và quyết toán có phần giao diện minh họa. Không coi việc xem màn hình là hoàn tất toàn bộ nghiệp vụ.
- Đơn seed `EM-SEED-R12-001` đã ghi nhận tiền cọc nhưng không có yêu cầu thanh toán tương ứng; trang thanh toán báo không tìm thấy. Video 20 dùng đơn mới tạo để xem yêu cầu cọc đúng luồng.
- Phiên đăng nhập được tạo qua API đăng nhập thật với tài khoản demo. CAPTCHA tắt trong phiên backend local. Các video tổng quan đầu dùng địa chỉ 127.0.0.1; các thao tác ghi dữ liệu dùng localhost đúng cấu hình CORS của backend.

## Môi trường chạy

Frontend: http://localhost:5173. Backend: http://localhost:8080. PostgreSQL: Docker `ecomotion-postgres`, cổng 5433. Backend dùng profile `dev`, upload đặt trong thư mục `upload` của dự án.

Cấu hình gốc có `spring.jpa.hibernate.ddl-auto=create-drop`: lần khởi động đầu đã chạy tạo lại bảng rồi dừng do đường dẫn upload `D:/ecomotion` không được phép. Phiên đang chạy dùng `ddl-auto=update` và nạp lại dữ liệu demo. Các thao tác trong video thay đổi dữ liệu demo local; không sửa mã nghiệp vụ.

Để chạy lại an toàn hơn, dùng `Start-Demo.ps1` ở thư mục này. Không chạy thẳng backend với cấu hình `create-drop` nếu cần giữ dữ liệu.

## Điểm quan sát khác tài liệu

- Đơn `BK-1P9A55`: sau thao tác xác nhận kết thúc, giao diện ghi nhận các mốc xác nhận nhưng xuất hiện thông báo lỗi máy chủ trong phiên demo. Chưa chứng minh luồng chuyển tiếp qua phụ phí và quyết toán đã hoàn tất.
- Hủy `EM-SEED-R12-001`: giao diện chuyển sang “Đã huỷ”, đồng thời hiển thị “Nghĩa vụ hoàn tiền còn tồn tại”. Danh sách hoàn tiền nhà xe lúc kiểm tra chưa có yêu cầu. Chưa xác minh nguyên nhân và không coi đây là hoàn tiền thành công.
- Trang VietQR của đơn mới hiển thị hướng dẫn tự động đối soát và không yêu cầu tải biên lai thủ công như một phần tài liệu mô tả. Video giữ đúng giao diện chạy thực tế.

Tài liệu ban đầu và dữ liệu seed giúp hiểu ý đồ nghiệp vụ; các điểm trên cần đối chiếu code nếu dùng để nghiệm thu. Phần quay này không bao gồm sửa lỗi nghiệp vụ.

## Đối chiếu 13 nhóm luồng của tài liệu

| Nhóm | Nơi xem | Mức độ đã quay |
|---|---|---|
| 1. Đăng ký, đăng nhập, kích hoạt | Video 1 | Màn hình và giải thích; đăng nhập demo qua API; chưa hoàn tất OTP/eKYC |
| 2. Nhà cung cấp, tài xế vào hệ thống | Video 3, 5, 8, 9, 10 | Hồ sơ, biểu mẫu, hàng chờ, nhánh tài xế chưa duyệt |
| 3. Xe, giấy tờ, giá, lịch | Video 3, 9 | Màn hình, các tab giấy tờ/bảng giá và lịch; chưa tạo/duyệt xe mới từ đầu |
| 4. Tìm xe và báo giá | Video 1, 11 | Tìm xe, chi tiết và báo giá thật |
| 5. Đặt xe có tài xế | Video 11, 12, 13, 15 | Tạo đơn thành công, giữ chỗ, yêu cầu cọc, tác nghiệp trên chuyến seed; chưa hoàn tất một đơn xuyên suốt từ tạo tới quyết toán |
| 6. Cọc, hủy và thanh toán | Video 14, 15 | Hủy đơn có cọc, kết quả thật và lựa chọn VietQR/VNPay; chưa chuyển tiền |
| 7. Đổi xe hoặc dời giờ | Video 3, 9 | Giao diện phương án vận hành; chưa gửi và chấp nhận một đề xuất xuyên hai vai trò |
| 8. Thuê năm | Video 2, 6, 7 | GPLX, hồ sơ gói năm, seed đang chạy và nhánh chưa đủ điều kiện; chưa ký/trả trước/kỳ tháng thành công |
| 9. Tài xế trên chuyến | Video 4, 12, 13 | Lịch, trạng thái làm việc, chi tiết chuyến và gửi xác nhận kết thúc; chưa quay đủ nhận/đến/bắt đầu trên một chuyến mới |
| 10. Hoàn tiền | Video 14, 17 | Kết quả hủy, nghĩa vụ trên giao diện, danh sách hoàn; chưa hoàn qua ngân hàng |
| 11. Tranh chấp | Video 5, 10, 16 | Màn hình vận hành và biểu mẫu khiếu nại theo trạng thái; chưa tạo/xử lý tranh chấp xuyên vai trò |
| 12. Các việc admin | Video 5, 10 | Tổng quan, hồ sơ, xe và vận hành; chưa thực hiện toàn bộ thao tác duyệt/khóa/chi hộ |
| 13. Đánh giá | Video 2 | Màn đánh giá xe và tài xế trên đơn COMPLETED; chưa xác minh gửi đánh giá thành công |

Các số “Video” trong bảng là thứ tự trong trang xem. Tên file giữ số thứ tự của các phiên quay để tiện tra lại bằng chứng.
