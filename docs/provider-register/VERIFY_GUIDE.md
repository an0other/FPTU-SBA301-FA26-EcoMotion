# Hướng dẫn verify Provider trên FE

Đây là checklist cần thực hiện trên môi trường chạy thật, không phải báo cáo rằng mọi ca dưới đây đã được kiểm thử thành công.

## 1. Chuẩn bị

1. Chạy database, Spring, DocumentAI, IdentityVerification và FE.
2. Kiểm tra cấu hình datasource, mã hóa dữ liệu và secret fingerprint đã có giá trị. Không thay secret khi đang kiểm tra dữ liệu cũ.
3. Spring bật `app.identity.ai.enabled`, cấu hình đúng base URL/API key cho hai service AI và đặt `app.identity.acceptance-threshold` hợp lệ.
4. OTP email dùng cấu hình SMTP. Điện thoại dùng nhà cung cấp theo cấu hình hiện tại; nếu môi trường dev dùng mock, lấy OTP theo cách mock đó hỗ trợ, không mặc định OTP bất kỳ đều đúng.
5. Mở `/api/identity/constraints`; kiểm tra HTTP thành công, `verificationEnabled=true`, `acceptanceConfigured=true` và giới hạn phù hợp.
6. Camera trình duyệt cần HTTPS hoặc localhost, được cấp quyền; test với webcam/thiết bị thật.

Muốn giữ dữ liệu, đặt `DB_DDL_AUTO=update` trước khi khởi động lại. `create` sẽ tạo lại dữ liệu. Không chạy toàn bộ `schema.sql` vào database đang cần giữ vì file chứa thao tác dựng lại schema.

Luồng duyệt chung có cột `submitted_attempt_id` trong `provider_application` và `provider_application_review`. Nếu không dùng Hibernate update, áp dụng migration `Backend/EcoMotion/src/main/resources/db/migrations/20261109_provider_combined_review.sql` sau các migration nền tảng tương ứng. Đây không phải migration tự động chỉ vì file nằm trong thư mục migrations.

## 2. Luồng thành công

| Bước | Thao tác | Kết quả cần kiểm tra |
| --- | --- | --- |
| 1 | Dùng email mới, nhận/xác nhận OTP, đăng ký role PROVIDER | Tạo User PENDING; chưa có Provider hoạt động |
| 2 | Đăng nhập | Vào `/provider/onboarding`, session ONBOARDING |
| 3 | Gửi và xác nhận OTP điện thoại | Có trạng thái đã xác nhận; không chỉ lưu chuỗi phone |
| 4 | Tải hai mặt CCCD | OCR có đủ trường; xem lại thông tin trước khi tiếp tục |
| 5 | Quay đủ hướng dẫn và gửi video | Pipeline đọc được video, motion PASS, có frame chấm điểm |
| 6 | Xác nhận CCCD/video rồi tiếp tục hồ sơ | Không xuất hiện một lượt duyệt danh tính riêng cho Admin |
| 7 | Nhập dịch vụ, chọn ghim, lưu và tiếp tục | Lưu đúng địa chỉ/tọa độ; form có version mới |
| 8 | Xem tóm tắt, đánh dấu xác nhận và gửi | Một hồ sơ Provider SUBMITTED, có identityAttemptId; User vẫn PENDING |
| 9 | Admin mở hồ sơ | Xem cả thông tin dịch vụ, OCR, AI và bằng chứng |
| 10 | Admin APPROVE | Danh tính được duyệt, User ACTIVE, Provider APPROVED trong cùng lượt |
| 11 | Provider cập nhật quyền/refresh session | Vào dashboard Provider với quyền đầy đủ |

Điểm cosine không phải phần trăm. Không cố điều chỉnh ngưỡng chỉ để một video test bất kỳ qua mà không ghi nhận chất lượng dữ liệu.

## 3. Yêu cầu bổ sung

1. Tạo hồ sơ mới và gửi chung.
2. Admin chọn NEEDS_CHANGES, lý do ADDRESS_REQUIRED.
3. Provider cập nhật trạng thái, thấy đúng lý do, chỉnh được hồ sơ.
4. Sửa địa chỉ; khi nhập địa chỉ mới, chọn lại vị trí tương ứng.
5. Lưu, kiểm tra và gửi lại. Backend kiểm tra điều kiện bằng chứng hiện tại.
6. Hồ sơ lại ở SUBMITTED; lịch sử có quyết định cũ, Admin có thể duyệt lần gửi mới.

Nếu lý do liên quan CCCD/video, tải lại dữ liệu theo phản hồi. Đọc lại CCCD phải làm lại kết quả video đối chiếu với tài liệu mới.

## 4. Từ chối và gửi lại

1. Admin chọn REJECT với lý do phù hợp.
2. Provider thấy REJECTED và vẫn có thể sửa form, không cần Admin REOPEN.
3. Nếu phiên danh tính đi kèm bị từ chối và tài khoản chưa có danh tính đã duyệt: vào bước xác minh, tạo phiên mới, gửi lại CCCD/video.
4. Lưu thông tin dịch vụ và gửi chung lại; kiểm tra `identityAttemptId` trỏ sang phiên mới.
5. Admin xem hồ sơ vừa gửi, không dùng evidence của lần gửi cũ để quyết định lần mới.

Nếu chỉ hồ sơ dịch vụ bị từ chối trên tài khoản đã có danh tính được duyệt từ trước, không mặc định bắt xác minh lại danh tính đó.

## 5. Các ca lỗi quan trọng

| Ca | Kỳ vọng |
| --- | --- |
| Ảnh không có khuôn mặt ở tài liệu đối chiếu | Bị từ chối với lỗi phù hợp; không kích hoạt User |
| Chọn ảnh vào ô video | Bị kiểm tra container và từ chối |
| Video không quay đủ chuỗi động tác | Motion INCOMPLETE, yêu cầu thử lại |
| Video WebM từ MediaRecorder | Đọc theo timestamp, không bị FPS giả 1000 do metadata thiếu |
| Video quá thời lượng/dung lượng | Bị chặn đúng giới hạn từ constraints |
| Đọc lại CCCD sau khi đã kiểm tra video | Kết quả video cũ không còn được dùng để gửi |
| Upload AI lỗi rồi thử lại | FE đồng bộ version; không lặp IDENTITY_ATTEMPT_STALE do giữ version cũ |
| Tọa độ có nhiều số thập phân | FE gửi tối đa 6 chữ số; không lỗi validation do độ chính xác |
| Chọn ngân hàng nhưng thiếu số tài khoản/chủ tài khoản | Không lưu được; hiển thị trường cần bổ sung |
| Hai cửa sổ cùng sửa hồ sơ | Mutation stale bị chặn, cần cập nhật dữ liệu |
| Hồ sơ đã SUBMITTED | Không sửa form hoặc bằng chứng đang gửi |
| Duyệt gặp CCCD đã thuộc danh tính khác | Transaction rollback, không tạo Provider hoạt động dở dang |
| Token onboarding gọi API quản lý xe | Bị backend chặn theo phạm vi quyền |
| Gọi endpoint Admin duyệt riêng danh tính cũ | HTTP 410; dùng review hồ sơ tổng hợp |

## 6. Liên kết từ Renter

1. Dùng tài khoản Renter thật đã được xác minh, không dùng danh tính seed demo làm nguồn.
2. Tạo tài khoản Provider với email khác.
3. Renter yêu cầu liên kết bằng mật khẩu của Renter.
4. Provider đăng nhập, xác nhận yêu cầu bằng mật khẩu của Provider; hủy phiên xác minh đang hoạt động trước nếu hệ thống yêu cầu.
5. Xác nhận OTP riêng cho Provider.
6. Kiểm tra việc dùng lại bằng chứng; nếu không đủ bằng chứng nguồn thì bổ sung lại.
7. Nhập dịch vụ và gửi hồ sơ tổng hợp để Admin duyệt một lần.

## 7. Khi cần báo lỗi

Gửi endpoint, HTTP status, `errorCode`, `traceId`, bước thao tác và thời điểm. Với lỗi validation, gửi phần response liên quan trường lỗi. Không gửi mật khẩu, token, API key hoặc ảnh giấy tờ trong log công khai.

## 8. Các ca bổ sung ngày 10/11/2026

- Admin yêu cầu `FACE_UNCLEAR` kèm ghi chú: Provider gửi lại nguyên video phải bị chặn; thay video và đạt policy mới được gửi.
- `DOCUMENT_UNREADABLE`: thay ảnh CCCD, kiểm tra video lại. `ADDRESS_REQUIRED`: sửa địa chỉ/ghim mà không bắt buộc thay video hợp lệ.
- Admin để trống ghi chú khi yêu cầu bổ sung/từ chối: backend phải từ chối.
- Sau upload bản thay thế, mở lịch sử lượt duyệt trước: OCR/AI và ảnh/video cũ phải giữ nguyên.
- Duyệt Provider không khai báo ngân hàng: vào được dashboard nhưng có checklist; chấp nhận đơn thuê bị chặn đến khi thiết lập tài khoản nhận tiền.
- Lưu thông tin chưa đủ trường: validation thông báo đúng trường; giao diện không gọi thao tác này là lưu nháp.

Áp dụng migration `20261110_yearly_provider_business_closure.sql` sau các migration trước đó. Migration này cập nhật cả dữ liệu Provider và nhóm phân xử gói năm; không chạy schema.sql để thay migration trên DB cần giữ dữ liệu. Các unit test đã chạy không thay thế các ca verify thực tế trên đây.
