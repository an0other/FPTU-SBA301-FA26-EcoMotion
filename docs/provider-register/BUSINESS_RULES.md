# Nghiệp vụ đăng ký Provider cá nhân

## 1. Tài khoản, danh tính và hồ sơ dịch vụ

| Dữ liệu | Vai trò |
| --- | --- |
| `User` | Email, mật khẩu, role, trạng thái tài khoản và dấu thời gian OTP/điều khoản |
| `PersonIdentity` | Danh tính cá nhân, CCCD mã hóa và fingerprint, dữ liệu xác minh |
| `IdentityVerificationAttempt` | Một phiên chuẩn bị CCCD/video, kết quả OCR/AI, phiên bản và trạng thái |
| `ProviderApplication` | Thông tin dịch vụ chờ duyệt; gắn phiên bằng chứng khi gửi |
| `ProviderApplicationReview` | Lịch sử quyết định, Admin, lý do, snapshot form và mã phiên bằng chứng |
| `Provider` | Hồ sơ hoạt động, chỉ tạo khi hồ sơ được chấp nhận |

Một người có thể có hai tài khoản Renter và Provider với hai email riêng. Việc dùng chung danh tính phải qua flow liên kết, không tự gắn hai tài khoản chỉ vì upload cùng số CCCD.

CCCD được chuẩn hóa, tạo fingerprint có khóa để tìm trùng; dữ liệu CCCD gốc được mã hóa. `fingerprint-key-version` ghi phiên bản khóa. Không đổi khóa tùy tiện trên hệ thống đã có dữ liệu vì cùng CCCD sẽ sinh fingerprint khác.

Unique theo danh tính và role ngăn tạo nhiều tài khoản cùng vai trò trên cùng danh tính. Hai vai trò có thể dùng chung điện thoại sau khi liên kết hợp lệ; mỗi tài khoản vẫn xác nhận OTP riêng. Danh tính seed demo không được dùng làm nguồn liên kết tài khoản thật.

## 2. Điều kiện và dữ liệu đăng ký

| Trường | Điều kiện hiện tại |
| --- | --- |
| Email | Xác nhận OTP trước khi tạo tài khoản; không trùng email đã đăng ký |
| Mật khẩu | DTO từ 8–128 ký tự, đồng thời phải đạt cấu hình tối thiểu của service |
| Điều khoản | `agreeTerms=true` |
| Role | `PROVIDER` cho luồng này |
| Điện thoại | Có giá trị và có `phoneVerifiedAt`, không chỉ nhập chuỗi số |
| Tên hiển thị | Bắt buộc, tối đa 150 ký tự |
| Hotline | Bắt buộc; regex hiện tại `^0[0-9]{9,10}$` |
| Địa chỉ giao nhận | Bắt buộc, tối đa 500 ký tự |
| Vĩ độ | Bắt buộc, từ -90 đến 90, tối đa 6 số thập phân |
| Kinh độ | Bắt buộc, từ -180 đến 180, tối đa 6 số thập phân |
| Ngân hàng | Tùy chọn, chọn từ danh sách ngân hàng được hỗ trợ |
| Số tài khoản | Nếu khai báo ngân hàng phải có; 6–30 chữ số và đạt giới hạn riêng của ngân hàng |
| Chủ tài khoản | Nếu khai báo ngân hàng phải có; tối đa 150 ký tự |

FE làm tròn tọa độ trước khi gọi API lưu. Đổi địa chỉ bằng nhập tay sẽ xóa tọa độ cũ để tránh lưu địa chỉ mới với ghim cũ.

## 3. Điều kiện AI

- Hai mặt CCCD phải được đọc đủ trường bắt buộc. Khi duyệt, họ tên và ngày sinh phải hợp lệ; ngày sinh không ở tương lai, ngày hết hạn nếu có không được nằm trong quá khứ.
- Video có chuỗi động tác `CENTER → POSITIVE → NEGATIVE → CENTER` theo quy ước yaw của pipeline.
- Workflow video phải là `READY_FOR_REVIEW`, motion challenge phải `PASS`.
- Trung vị cosine similarity đạt `app.identity.acceptance-threshold`.
- Tối thiểu 4 frame chấm được; tỷ lệ frame chấm được / frame ứng viên tối thiểu 75%.
- Ngưỡng phải được cấu hình trong khoảng `(0, 1]`; thiếu ngưỡng thì không hoàn tất gửi hồ sơ.

Trung vị là điểm cosine similarity, không phải phần trăm hay xác suất đúng. Bốn frame và độ phủ 75% hiện là hằng số trong service, không phải biến cấu hình riêng.

Chuẩn bị dữ liệu Provider chưa tạo danh tính đã xác minh. Khi gửi hồ sơ tổng hợp, backend đánh giá lại dữ liệu theo policy và gắn đúng phiên xác minh vào hồ sơ.

## 4. Trạng thái

### Tài khoản và quyền

- Trước khi hồ sơ được duyệt: User `PENDING`, đăng nhập Provider bằng session scope `ONBOARDING`.
- Chỉ truy cập nhóm tính năng onboarding được BE cho phép; không dựa vào việc FE ẩn nút để bảo vệ API.
- Sau duyệt: User `ACTIVE`, Provider `APPROVED`; cần refresh phiên hoặc đăng nhập lại để nhận quyền đầy đủ.
- Tài khoản `LOCKED` không được tự bổ sung hồ sơ để vượt khóa tài khoản.

### Hồ sơ Provider

| Trạng thái | Sửa/lưu | Gửi hồ sơ | Ý nghĩa |
| --- | --- | --- | --- |
| `DRAFT` | Có khi User còn PENDING | Có nếu đủ điều kiện | Chưa gửi hoặc đã lưu bản sửa |
| `SUBMITTED` | Không | Gửi lặp trả hồ sơ hiện tại | Chờ Admin duyệt chung |
| `NEEDS_CHANGES` | Có | Có sau khi đủ điều kiện | Admin yêu cầu bổ sung |
| `REJECTED` | Có | Có sau khi đủ điều kiện | Bị từ chối, được bổ sung và gửi lại |
| `APPROVED` | Không qua API onboarding | Không tạo lượt duyệt mới | Provider đã hoạt động |

API vẫn hỗ trợ quyết định `REOPEN` cho tương thích, nhưng người dùng không cần chờ thao tác này để sửa hồ sơ bị từ chối.

### Phiên CCCD/video

Các trạng thái chính: `DRAFT`, `PROCESSING`, `DOCUMENT_READY`, `READY_TO_SUBMIT`, `SUBMITTED`, `APPROVED`, `NEEDS_CHANGES`, `REJECTED`, `ERROR`, `CANCELLED`, `EXPIRED`.

- `READY_TO_SUBMIT`: dữ liệu đã chuẩn bị; chưa được Admin chấp nhận.
- `SUBMITTED`: bằng chứng đã được gắn vào hồ sơ gửi chung; không chỉnh sửa bằng chứng đang chờ duyệt.
- `REJECTED`: không upload tiếp vào phiên cũ; tạo phiên mới nếu cần xác minh lại.
- Đọc lại CCCD tăng document revision và xóa kết quả video cũ. Phải đối chiếu video lại với CCCD mới.
- Phiên chưa gửi có TTL; quá thời gian xử lý có cơ chế khôi phục để thử lại. Không mặc định tạo phiên mới khi request AI đang chạy.

## 5. Tính nhất quán khi gửi và duyệt

- Mỗi mutation gửi `version` hiện tại. Phiên xác minh và hồ sơ dịch vụ có version độc lập.
- Gửi chung giữ `submittedAttemptId`; Admin xem đúng phiên này, không lấy tùy ý phiên mới nhất.
- AI được gọi ngoài transaction dài trong luồng upload; operation ID và document revision bảo vệ kết quả trả muộn.
- Admin review hồ sơ chạy trong transaction chung với duyệt danh tính và tạo Provider. Lỗi tạo Provider phải rollback cả lượt duyệt danh tính.
- Lưu lại form sau phản hồi chuyển hồ sơ về `DRAFT`; gửi lại chuyển về `SUBMITTED`.
- Lịch sử review giữ quyết định và snapshot form. API history hiện trả metadata; không trả toàn bộ snapshot hoặc toàn bộ lịch sử file bằng chứng.

## 6. Phạm vi MVP và giới hạn

- Camera dùng hướng dẫn theo thời gian, không xác nhận realtime người dùng quay đúng đầu.
- Kiểm tra động tác chưa đủ để khẳng định chống phát lại video/deepfake hoặc giấy tờ giả.
- Thông tin ngân hàng hiện kiểm tra cấu trúc và ngân hàng hỗ trợ; không phải xác minh quyền sở hữu tài khoản ngân hàng qua nhà cung cấp.
- Bằng chứng được lưu riêng tư và lấy qua API có phân quyền; không dùng URL public trong form đăng ký.
- Cùng danh tính Renter/Provider được dùng để chặn tự thuê xe của chính mình ở các bước nghiệp vụ thuê xe. Không kiểm chứng quan hệ người quen chỉ bằng thông tin tài khoản.
