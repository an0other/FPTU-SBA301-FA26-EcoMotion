# 01. Nghiệp vụ và phạm vi

## Mục tiêu và giới hạn

Tiếp nhận giấy tờ/video, giải thích kết quả kiểm tra, cho phép bổ sung và lưu quyết định Admin có dấu vết. “Đã chấp nhận” nghĩa đạt chính sách kiểm tra hồ sơ của EcoMotion, không đồng nghĩa đã tra cứu cơ quan cấp, chứng minh giấy tờ thật hoặc phát hiện được mọi giả mạo.

Phân biệt bốn khái niệm: đọc được dữ liệu; kiểm tra nhất quán; chấp nhận hồ sơ; đủ điều kiện sử dụng dịch vụ tại thời điểm giao dịch. Không gộp chúng thành một confidence hoặc một cờ verified.

## Đối tượng và trách nhiệm

| Hồ sơ | Bằng chứng | Người quyết định |
|---|---|---|
| Danh tính renter | Hai mặt CCCD, video, thông tin trích xuất và kết quả kiểm tra | Admin |
| Danh tính provider | CCCD/video gắn cùng phiên bản hồ sơ hoạt động | Admin trong luồng xét duyệt kết hợp |
| GPLX renter | Hai mặt GPLX, dữ liệu đọc và đối chiếu với danh tính | Admin |

Người nộp được xem hồ sơ của mình, bổ sung, gửi và hủy khi chưa có quyết định. Admin được truy cập theo quyền, phải xem bằng chứng trước khi chấp nhận. Hệ thống quản lý phiên bản, trạng thái, quyền sử dụng và lịch sử. Không cho chủ hồ sơ tự duyệt.

## Luồng chung

1. Người dùng biết rõ mục đích thu thập, giới hạn định dạng/dung lượng và thông báo quyền riêng tư trước khi tải lên.
2. Tạo phiên hồ sơ; tải bằng chứng vào kho riêng tư. Kiểm tra file và giới hạn tài nguyên ở backend.
3. Chạy các bước đang hỗ trợ: chất lượng, OCR, QR và/hoặc video; lưu kết quả theo đúng phiên bản bằng chứng.
4. Dữ liệu không sử dụng được: hướng dẫn chụp/quay lại. Lỗi dịch vụ: giữ hồ sơ và cho thử lại. Trường hợp thiếu QR, OCR chưa chắc chắn hoặc tín hiệu nghi ngờ: vẫn có đường gửi Admin nếu đủ bằng chứng tối thiểu.
5. Người dùng xem thông tin đọc được, xác nhận quyền sở hữu và gửi hồ sơ. Dữ liệu chưa chắc chắn phải giữ cờ cần kiểm tra, không biến việc xác nhận của người dùng thành bằng chứng AI đã đúng.
6. Admin xem ảnh/video, trường chưa rõ, mâu thuẫn và các kiểm tra chưa hỗ trợ; chọn chấp nhận, yêu cầu bổ sung hoặc từ chối.
7. Sau chấp nhận, backend kiểm tra điều kiện sử dụng tại từng điểm nghiệp vụ liên quan. Giấy tờ hết hạn hoặc quyền bị thu hồi không được bỏ qua vì từng được duyệt.

Với provider, thao tác gửi cuối phải gắn CCCD/video với phiên bản hồ sơ hoạt động. Không đánh dấu danh tính được chấp nhận trước khi giao dịch duyệt kết hợp hoàn tất.

## Quyền sử dụng và bằng chứng mới

- Danh tính chưa được chấp nhận: chưa mở quyền phụ thuộc vào xác minh danh tính; vẫn cho đăng nhập vào vùng hoàn thiện hồ sơ/theo dõi nếu chính sách tài khoản cho phép.
- GPLX chưa được chấp nhận: chưa mở quyền thuê tự lái phụ thuộc GPLX. Không tự chặn dịch vụ có tài xế chỉ vì thiếu GPLX nếu luồng đó không yêu cầu GPLX.
- Hồ sơ mới không tự ghi đè hồ sơ đã chấp nhận. Lưu bản đang hiệu lực và bản đang đề nghị thay thế riêng; bản mới chỉ trở thành bản hiệu lực sau quyết định hợp lệ.
- Nếu bản đang hiệu lực hết hạn hoặc bị hạn chế, việc đang chờ xét bản mới không khôi phục quyền sử dụng.
- Cách xử lý booking đang chạy khi phát hiện rủi ro cần chính sách vận hành riêng; không tự hủy booking, trừ tiền hoặc thay đổi thanh toán trong phạm vi đợt này.
- Giữ nguyên các điều kiện tuổi, hạng, hạn bao phủ kỳ thuê và loại xe đang áp dụng; không coi xét duyệt hồ sơ là thay thế các điều kiện đó.

## QR và mẫu giấy tờ cũ

Không có QR không phải lý do tự động từ chối. Người dùng có thể khai báo giấy tờ không có QR; Admin xác nhận mẫu giấy tờ trong quá trình duyệt. Không đồng nhất khai báo này với kết quả máy phát hiện.

QR bị tắt do cấu hình hoặc lỗi kỹ thuật phải được ghi đúng nguyên nhân. Không dùng nút phía client để bỏ kiểm tra bắt buộc. QR khớp OCR chỉ chứng minh tính nhất quán của dữ liệu đang quan sát, không chứng minh nguồn cấp hay tính thật của thẻ.

## Phạm vi sau MVP

Phát hiện ảnh AI, chỉnh sửa, ảnh chụp màn hình và chống giả mạo video được bổ sung dưới dạng kiểm tra có phiên bản. Chỉ dùng quyết định tự động sau khi nhóm phê duyệt chính sách và có bằng chứng kiểm định tương ứng. Trước mắt mọi quyết định chấp nhận thuộc Admin.


## Hai tài khoản theo vai trò, không có thao tác liên kết

Mỗi CCCD tối đa một tài khoản Renter và một Provider. Hai tài khoản có thông tin đăng nhập và hồ sơ xét duyệt riêng.
CCCD đã được duyệt ở vai trò khác không tự kích hoạt tài khoản mới: người đăng ký vẫn gửi CCCD/video và Admin duyệt đúng hồ sơ.
BE đối chiếu số CCCD, họ tên và ngày sinh với bản ghi đã duyệt; ràng buộc identity + role ngăn hai tài khoản cùng vai trò.
API liên kết cũ ngừng hoạt động (410); không chuyển quyền hay tự duyệt qua tài khoản khác. Vẫn chặn thuê xe của chính mình.
Bản ghi định danh nội bộ có thể dùng chung để giữ tính duy nhất của một người; mật khẩu, quyền và quyết định trên từng hồ sơ không dùng chung.
