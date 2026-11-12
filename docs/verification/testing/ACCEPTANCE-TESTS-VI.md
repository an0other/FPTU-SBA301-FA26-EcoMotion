# Kiểm thử và nghiệm thu xét duyệt giấy tờ

**Trạng thái:** checklist nghiệm thu cho source đã nối luồng; chưa xác nhận E2E với DB/AI thật. Xem [mục lục](../README.md).

## Nghiệm thu bắt buộc

- Giấy tờ không QR gửi Admin được, không tự APPROVED; QR DISABLED không hiển thị như mẫu cũ đã xác nhận.
- QR/OCR khớp vẫn chờ Admin; mâu thuẫn giữ hai nguồn để đối chiếu.
- OCR thiếu nhưng ảnh đủ xem có đường kiểm tra thủ công; Admin điền trường phải có lịch sử, không ghi đè OCR gốc.
- Frame/điểm tương đồng không tự duyệt renter; motion PASS không mang nhãn xác nhận chống giả mạo.
- ERROR/timeout/NOT_SUPPORTED không thành PASS hoặc REJECTED; trạng thái PROCESSING hết lease có thể phục hồi.
- Hai Admin cùng duyệt: chỉ quyết định trên version hợp lệ được lưu. Callback AI cũ không sửa revision mới.
- Nộp bổ sung chỉ xử lý phiên bản mới, giữ lịch sử cũ; ảnh được yêu cầu thay phải thực sự thay theo yêu cầu, không chỉ sửa tên file.
- Pending/rejected không mở quyền phụ thuộc giấy tờ; dịch vụ không yêu cầu GPLX không bị chặn nhầm.
- Giấy tờ đã duyệt nhưng hết hạn không vượt qua kiểm tra tại giao dịch; bản thay thế đang chờ không tự có hiệu lực.
- Người khác không đọc/sửa bằng chứng được; Admin không có quyền không gọi endpoint review được.
- Thêm check IMAGE_MANIPULATION hoặc AI_GENERATION bằng fixture không làm đổi vòng đời; mã check/status lạ không được duyệt ngầm.
- Không phá flow giờ/ngày/năm, provider kết hợp, liên kết tài khoản và review đã triển khai.


## Lịch sử và ghi chú renter

- Gửi → yêu cầu bổ sung có note → thay video → gửi lại: lịch sử giữ cả ba sự kiện và checksum video cũ/mới đúng thời điểm.
- Ghi chú trống/quá ngắn/quá dài bị chặn; quyết định lỗi không tạo dòng lịch sử.
- Admin xem được snapshot/file lịch sử của đúng hồ sơ; non-admin và event thuộc hồ sơ khác không đọc được.
- Upload lại không xóa file đã được lưu trong lịch sử. UI không dựng lại sự kiện cho hồ sơ cũ thiếu lịch sử.
- Sau áp dụng migration trên DB test: xác nhận transaction rollback không lưu quyết định hoặc sự kiện đơn lẻ. Kiểm tra này cần PostgreSQL thực tế, chưa được thay thế bằng unit test mock.

## Kiểm tra tích hợp web

- Token kích hoạt hợp lệ đọc được kết quả sau Admin chấp nhận; mutation qua token đó vẫn bị chặn khi tài khoản ACTIVE. Token sai/hết hạn, khác email/role hoặc tài khoản khóa không được nới quyền.
- Provider đủ video/động tác đi được tới xét duyệt kết hợp dù không cấu hình ngưỡng tự duyệt hoặc điểm thấp; không bị tự APPROVED.
- Chủ hồ sơ GPLX xem các bản nộp trước theo trang; không truy cập lịch sử/ảnh của renter khác.
- Luồng chạy local hiện chọn DB_DDL_AUTO=create: khởi động Spring để tạo schema rồi thao tác trực tiếp trên UI. Mỗi lần khởi động lại mất dữ liệu; cần kiểm tra lỗi constraint/index thực tế vì create không thay thế việc kiểm chứng migration trên môi trường giữ dữ liệu.

## Kết quả UI và mức hoàn thiện thông tin

- Kiểm tra số đếm theo policy backend: 3/4 hiển thị 75%, không có confidence trung bình.
- Thiếu, sai định dạng hoặc cờ mâu thuẫn/cần review của trường bắt buộc không được tính READ.
- Thiếu trường tùy chọn không kéo tụt thanh; UNLIMITED trên GPLX không tự mở quyền gói năm.
- Dù 100%, tín hiệu bất thường vẫn hiển thị độc lập, hồ sơ không tự APPROVED.
- SUBMITTED ở user hiển thị chờ duyệt, không có thanh hoàn thiện giả làm tiến độ xử lý.
- Hồ sơ cũ thiếu completeness không tự nhận 100%; lịch sử rỗng không xuất hiện cho user.
- Nút chụp lại/tiếp tục chỉ xuất hiện khi có callback/thao tác backend hỗ trợ; không hứa gửi thủ công CCCD khi tuyến chưa có.

## Tiến trình và bằng chứng người nộp

- Upload ảnh/video có số byte tổng thì hiển thị đúng % upload; không biết tổng thì không tự điền %.
- Backend PROCESSING hiển thị đúng DOCUMENT/VIDEO, thời gian và lần kiểm tra; không dựng tiến độ nội bộ model.
- Mất kết nối polling chỉ là trạng thái kết nối; backend timeout hiện lỗi riêng. Reload trang giữa lượt xử lý tiếp tục theo dõi.
- Sau đọc ảnh, hai mặt xuất hiện lại dù file input đã được xóa. SUBMITTED vẫn mở được ảnh/video chỉ xem.
- Sau bổ sung, lịch sử dùng đúng key của event cũ; owner khác/event không thuộc hồ sơ bị từ chối trước khi đọc storage.
- Mở/đóng lịch sử không tải toàn bộ video; đổi bản/rời trang abort request và thu hồi blob URL.


## Kiểm tra phần bổ sung

- Hoàn thành CCCD/video, tải lại trang: hai pipeline vẫn còn; nộp phiên bản mới vẫn xem được lịch sử cũ. Kiểm tra lỗi, hủy và hết thời gian không hiển thị hoàn tất thành công.
- Cùng CCCD: tối đa một Renter và một Provider, mỗi tài khoản được duyệt riêng; cùng số điện thoại cần OTP và CCCD phải khớp khi duyệt.
- Admin GPLX: bật/tắt từng ảnh GPLX và mở CCCD đối chiếu; tài khoản thường không truy cập ảnh người khác.
- Gói năm: ngày/giờ không đủ khoảng trống 12 tháng bị vô hiệu hóa; gửi yêu cầu vẫn kiểm tra lại để xử lý tranh chấp đồng thời.
- Video hiện chỉ có dữ liệu một người: phải thấy chưa hiệu chuẩn; không coi phát hiện mặt hoặc đủ động tác là đã xác minh đúng người.

Chưa có bộ cặp nhiều người có nhãn nên chưa công bố FMR/FNMR thực nghiệm. Bộ kiểm tra đơn vị dùng điểm tổng hợp chỉ kiểm tra logic, không chứng minh độ chính xác model.


## Đối chiếu sự cố GPLX và đăng nhập ngày 12/11

Đã đọc API local của tài khoản được cung cấp: pipeline GPLX ERROR với LICENSE_PROCESSING_FAILED, bước AI cuối COMPLETED; bản nộp hiện APPROVED. Chưa có stack trace của lần lỗi cũ nên không kết luận chính xác ngoại lệ lịch sử. Đã xử lý rủi ro entity progress giữ version cũ sau checkpoint ở transaction riêng: refresh khóa dòng trước cập nhật và hoàn tất trong REQUIRES_NEW. Lỗi ghi trace sau khi hồ sơ đã commit không làm POST báo hồ sơ thất bại; thêm kiểm tra hồi quy tương ứng. Không sửa trạng thái ERROR của lượt lịch sử để giả thành thành công.

Kiểm tra lại email trong ảnh: tài khoản ACTIVE và lần gọi login local đã trả 200. Chưa tái hiện lỗi đăng nhập cũ; không đổi mật khẩu hoặc bỏ kiểm tra bảo mật.

Kiểm thử tiếp: khởi động lại BE/Identity để dùng code và env mới; nộp GPLX mới khi không còn bản READY_TO_SUBMIT/SUBMITTED; kiểm tra POST, pipeline và hồ sơ sau tải lại cùng khớp. Nếu lỗi tiếp diễn, đối chiếu dòng `License preparation failed` bằng operation ID để lấy exception/cause/location (không cần gửi ảnh giấy tờ hoặc token).
