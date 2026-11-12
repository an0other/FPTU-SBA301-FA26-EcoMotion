# Kiểm chứng chung trên web — giấy tờ và danh tính

Luồng đã được nối trong source. Đây là hướng dẫn kiểm chứng, không phải báo cáo đã chạy thành công với DB/AI thật. Xem [đặc tả](../specification/BUSINESS-FLOW-VI.md) và [nghiệm thu](../testing/ACCEPTANCE-TESTS-VI.md).

## Chạy trực tiếp với database tạo mới

1. Đây là cách chạy local đã chọn: đặt `DB_DDL_AUTO=create` trong terminal chạy backend. Spring sẽ tạo lại schema, dữ liệu cũ trong schema sẽ mất. Không dùng cách này cho database cần giữ dữ liệu.
2. Không cần chạy SQL preflight hoặc chạy thủ công các migration trước lần khởi động `create`. Các migration vẫn được giữ cho môi trường cần nâng cấp database hiện có; cách chạy này chưa xác nhận các index/constraint riêng trong migration đã được Hibernate tạo tương đương.
3. Khởi động DocumentAI chính thức tại `D:/PYTHON/DocumentAI-v0.2.0/DocumentAI` (cổng 8001), IdentityVerification (8002), rồi backend và frontend. Key backend phải khớp từng AI, không đưa key vào frontend.
4. Tại `Backend/EcoMotion`, chạy PowerShell:

   ```powershell
   $env:DB_DDL_AUTO = "create"
   .\mvnw.cmd spring-boot:run
   ```

5. Nếu môi trường đang bật profile dev, dùng các tài khoản seed được tạo sau khi khởi động. Không mặc định dữ liệu seed đã đi qua quy trình xác minh mới. Đăng ký renter mới để đi toàn bộ luồng CCCD/video; dùng hai profile trình duyệt riêng cho renter và Admin.
6. Thao tác trực tiếp các tuyến bên dưới và ghi nhận lỗi trên UI/log. Mỗi lần khởi động lại backend với `create` là bắt đầu lại dữ liệu, không dùng để kiểm tra việc giữ lịch sử qua lần khởi động server. Chỉ dùng ảnh/video được phép sử dụng, không đưa vào Git.

## Tuyến A — danh tính renter

1. Đăng ký/kích hoạt renter theo luồng hiện có tại `/activate`; hoàn tất xác nhận số điện thoại.
2. Tải hai mặt CCCD; xem dữ liệu trích xuất, nguồn OCR/QR, cảnh báo. Quay video đủ hướng dẫn. Kiểm tra thông tin rồi xác nhận gửi.
3. Mong đợi SUBMITTED/chờ Admin, chưa tự mở quyền vì điểm cao. Không tạo phiên mới khi đã gửi.
4. Admin tại `/admin` → Xác minh & Provider mới → Danh tính renter → Chờ xét duyệt. Mở hồ sơ, xem đủ ảnh/video và kết quả AI.
5. Chọn yêu cầu bổ sung, lý do FACE_UNCLEAR và ghi rõ hướng dẫn. Renter cập nhật trạng thái, thấy note/timeline, thay video rồi gửi lại. Gửi lại video cũ bị chặn theo dấu vân tay.
6. Admin xem được bằng chứng của lần trước trong lịch sử, duyệt bản mới. Quyết định và snapshot cũ vẫn giữ.
7. Renter tại trang kích hoạt bấm Cập nhật trạng thái: nhận kết quả hoàn tất và chuyển đăng nhập, không báo phiên lỗi chỉ vì tài khoản đã ACTIVE. Token thực sự hết hạn vẫn cần khôi phục hoặc đăng nhập; token kích hoạt không mở quyền sửa hồ sơ ACTIVE.

## Tuyến B — GPLX cũ không QR / OCR cần đối chiếu

1. Đăng nhập renter đã được chấp nhận danh tính; mở phần GPLX trong hồ sơ.
2. Tải hai mặt, chọn khai báo không QR nếu đúng. Mong đợi bộ đọc không bị tắt bởi lựa chọn này; UI phân biệt khai báo với kết quả QR.
3. Khi OCR còn thiếu hoặc mâu thuẫn nhưng nhận diện được GPLX và ảnh sử dụng được, hồ sơ có thể READY_TO_SUBMIT. Xem ảnh/kết quả, xác nhận gửi.
4. Admin → Xác minh & Provider mới → Hồ sơ GPLX → Chờ xét duyệt. Xem cả hai ảnh, đối chiếu và sửa sai OCR nếu cần.
5. Thử yêu cầu bổ sung với note cụ thể; renter thấy note và có thể nộp hồ sơ mới. Mục Các lần nộp GPLX trước cho xem bản cũ theo trang, không mất ảnh khi nộp lại.
6. Admin chấp nhận bản mới sau khi kiểm tra số/họ tên/ngày sinh/hạng/ngày cấp/hạn. Họ tên/ngày sinh phải khớp danh tính đã duyệt; danh tính bị hạn chế hoặc hết hạn không được duyệt GPLX.
7. Renter cập nhật trạng thái: GPLX hiệu lực thay đổi sau chấp nhận. Dữ liệu AI gốc và dữ liệu Admin xác nhận được lưu riêng.
8. Thử đặt gói năm: vẫn kiểm tra tuổi, hạng, số chỗ và hạn GPLX bao phủ kỳ thuê. Chờ duyệt không mở quyền của bản mới. Nếu có GPLX cũ hiệu lực, nó tiếp tục chịu policy hiện có cho đến khi được thay thế.

## Tuyến C — lỗi và giới hạn quyền

- Ảnh không đọc được/quá dung lượng: báo lỗi đầu vào, không thành hồ sơ được duyệt.
- Dừng dịch vụ AI: thông báo lỗi dịch vụ, không kết luận giấy tờ giả. Không bấm gửi lặp liên tục.
- Hai cửa sổ Admin cùng mở một version: sau quyết định ở cửa sổ thứ nhất, quyết định cũ phải bị chặn/tải lại.
- Renter khác không xem được ảnh/lịch sử GPLX hoặc danh tính; URL storage không public.
- Client cũ gọi upload/PUT GPLX tự xác minh: nhận 410 LICENSE_REVIEW_REQUIRED và hướng dẫn cập nhật.

## Tuyến D — hồi quy provider

Provider vẫn điền và gửi hồ sơ hoạt động kèm CCCD/video; Admin duyệt kết hợp một lần. Điểm tương đồng chỉ tham khảo, không còn ngưỡng cũ chặn hồ sơ khỏi hàng chờ khi đủ bằng chứng/động tác. Thiếu video sử dụng được hoặc thiếu động tác vẫn yêu cầu quay lại. Không tạo hàng chờ danh tính provider độc lập.

## Ghi nhận lỗi khi test

Ghi tuyến/bước, trạng thái trước/sau, thời điểm, mã lỗi và requestId nếu có. Không đưa ảnh giấy tờ, token, key hoặc thông tin nhận dạng vào issue công khai. Các case ảnh AI/chỉnh sửa chỉ là điểm mở rộng; chưa tuyên bố đã phát hiện được nếu model chưa tích hợp/kiểm định.

## Kiểm tra tiến trình và ảnh/video lưu lại

1. Gửi hai mặt CCCD: thấy % truyền file nếu trình duyệt có tổng dung lượng; sau đó thanh chờ xử lý không định lượng. Backend ghi nhận DOCUMENT thì UI hiện phân tích ảnh; VIDEO hiện phân tích video. Thời gian chờ và lần kiểm tra cuối được cập nhật, không khẳng định AI đến bước OCR/QR bên trong.
2. Reload khi backend còn PROCESSING: UI tiếp tục theo dõi. Mất kết nối trạng thái không tự coi là treo hay từ chối. Không gửi trùng file chỉ vì thấy chờ lâu.
3. Sau đọc xong: ảnh mặt trước/mặt sau tải lại từ kho riêng tư, dù input chọn ảnh đã được xóa. Bấm ảnh để xem lớn; video có nút xem riêng.
4. Sau xác nhận gửi: ảnh/video vẫn xem được, không sửa. Mở từng mốc lịch sử để so với ảnh trước khi bị yêu cầu bổ sung/từ chối.
5. Sau tạo phiên mới: mở Các phiên hồ sơ trước, tải danh sách và chọn phiên cũ. Chỉ owner đúng quyền hoặc Admin mới đọc được file; snapshot đã mất từ trước không được phục dựng giả.
6. Với provider, SUBMIT được ghi khi gửi hồ sơ hoạt động kết hợp. Timeline bằng chứng giữ ảnh/video; ghi chú hoạt động chi tiết vẫn xem trong phản hồi hồ sơ provider.


## Kiểm tra tiến trình AI và ảnh hồ sơ

Khởi động lại DocumentAI chính thức, IdentityVerification, backend và frontend sau cập nhật.
Không cần migration mới cho tiến trình. Dùng API key backend khớp từng dịch vụ; không đưa vào frontend.
Upload hai ảnh CCCD: quan sát upload, bước AI đọc từng mặt rồi ảnh lưu sau khi có kết quả.
Upload video: quan sát bước phân tích video rồi kết quả; tác vụ nhanh có thể xong giữa hai lần polling.
Sau xác nhận gửi, mở lại hồ sơ chờ duyệt và bằng chứng lịch sử; ảnh cũ không đổi theo lần chụp mới.
Mất kết nối polling chỉ hiện cảnh báo kết nối, không báo giấy tờ bị từ chối và không tự gửi lại ảnh.
API AI đã kiểm chứng bằng ảnh/video test thật; vẫn cần kiểm tra xuyên suốt browser + BE + DB khi demo.


Khi đọc CCCD hoặc GPLX, xem danh sách “Các bước AI đã báo cáo”: định vị/đọc mặt trước,
định vị/đọc mặt sau, đối chiếu QR, kiểm tra thông tin, tổng hợp. Danh sách chỉ chứa bước thực sự phát ra;
nhánh dừng sớm không được tô hoàn tất các bước chưa chạy. Khi xem video, theo dõi chuẩn bị model,
mặt tham chiếu, đọc/phân tích video, kiểm tra động tác và tổng hợp. Dừng mạng tạm thời phải hiện thông tin gần nhất,
không tự báo hồ sơ bị từ chối. Không dùng % hoàn thiện thông tin để thay % tiến trình.
