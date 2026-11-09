# API đăng ký Provider

Base path là `/api`. Các bảng dưới ghi đầy đủ path. JSON request dùng `Content-Type: application/json`. API có đăng nhập dùng `Authorization: Bearer <accessToken>`; refresh dùng cookie theo cấu hình Spring.

`userId` là ID số của tài khoản. `attemptId` và `identityAttemptId` là UUID public của phiên CCCD/video. Không thay thế các ID này cho nhau.

## 1. Đăng ký và phiên đăng nhập

| Method | Endpoint | Request chính | Kết quả |
| --- | --- | --- | --- |
| POST | `/api/auth/register/send-otp` | `{ "email": "provider@example.com" }` | Gửi OTP email; trả hạn dùng/cooldown |
| POST | `/api/auth/register/verify-otp` | `{ "email": "provider@example.com", "otp": "<OTP>" }` | `registrationToken`, thời hạn |
| POST | `/api/auth/register` | Ví dụ bên dưới | HTTP 201, activation token; tài khoản PENDING |
| POST | `/api/auth/login` | `{ "email": "provider@example.com", "password": "<password>", "rememberMe": false, "captchaToken": "<nếu bật>" }` | Access token, thông tin tài khoản/scope, refresh cookie |
| GET | `/api/auth/me` | Bearer token | Trạng thái tài khoản, scope và next action |
| POST | `/api/auth/refresh` | Refresh cookie | Access token mới và trạng thái phiên |
| POST | `/api/auth/logout` | Refresh cookie | Thu hồi phiên, xóa cookie |

```json
{
  "registrationToken": "<token-sau-xac-nhan-email>",
  "password": "<mat-khau-hop-le>",
  "role": "PROVIDER",
  "agreeTerms": true,
  "captchaToken": "<neu-captcha-duoc-bat>"
}
```

Provider đăng nhập tài khoản PENDING để vào `/provider/onboarding`. Không sử dụng token registration/activation thay cho access token ở các API Provider dưới đây.

## 2. OTP điện thoại và trạng thái

| Method | Endpoint | Request | Quyền |
| --- | --- | --- | --- |
| POST | `/api/provider/onboarding/phone/send-otp` | `{ "phone": "0900000000" }` | `ROLE_PROVIDER_ONBOARDING` |
| POST | `/api/provider/onboarding/phone/verify-otp` | `{ "otp": "<OTP>" }` | `ROLE_PROVIDER_ONBOARDING` |
| GET | `/api/identity/status` | Không có body | Tài khoản Renter/Provider phù hợp, kể cả onboarding |
| GET | `/api/identity/constraints` | Không có body | Không cần Bearer |

Constraints trả giới hạn ảnh/video, chuỗi động tác và các cờ `verificationEnabled`, `acceptanceConfigured`. FE lấy giới hạn runtime, không tự giả định Python và Spring dùng cùng cấu hình.

## 3. Chuẩn bị CCCD/video

Quyền: chủ tài khoản Renter/Provider hoặc Provider onboarding. Chỉ chủ phiên được thay đổi phiên đó.

| Method | Endpoint | Request |
| --- | --- | --- |
| POST | `/api/identity/attempts` | Không có body; tạo hoặc tiếp tục phiên đang hoạt động |
| GET | `/api/identity/attempts/current` | Lấy phiên hiện tại |
| GET | `/api/identity/attempts/{attemptId}` | Lấy chi tiết và version mới |
| POST | `/api/identity/attempts/{attemptId}/documents` | Multipart: `version`, `cccdFront`, `cccdBack` |
| POST | `/api/identity/attempts/{attemptId}/video` | Multipart: `version`, `video` |
| POST | `/api/identity/attempts/{attemptId}/submit` | `{ "version": 3, "confirmed": true }` |
| POST | `/api/identity/attempts/{attemptId}/cancel` | `{ "version": 3 }` |
| GET | `/api/identity/attempts/{attemptId}/evidence/{kind}` | Binary; `kind` là `front`, `back`, `video` |

Khi gửi FormData, để trình duyệt tự tạo multipart boundary. Wrapper FE đặt timeout upload/phân tích 420 giây; timeout này khác thời hạn operation trên BE.

### Response phiên xác minh

`IdentityAttemptResponse` gồm:

- Định danh/concurrency: `attemptId`, `version`, `documentRevision`, `role`.
- Tiến độ: `status`, `processingStage`, `nextAction`, `lastErrorCode`, `expiresAt`.
- Bằng chứng: `hasFront`, `hasBack`, `hasVideo`.
- OCR trong `document`: `documentNumber`, `fullName`, `dateOfBirth`, `expiryDate`, `address`, `sex`, `requiredFieldsReadable` và metadata đọc tài liệu.
- AI trong `video`: `motionChallenge`, `faceMatching`, `workflow`, `identityDecision`, `automaticApproval`.
- Gửi/duyệt: `canSubmit`, `identityDecision`, `automaticApproval`, `decisionActor`, `decisionReason`, `policyVersion`, `submittedAt`, `decidedAt`, `reviewedBy`, `resultingIdentityId`.
- Thông tin tài khoản: `userId`, `email`, `fullName`.

`POST .../submit` trả `IdentityDecisionResponse`, không phải toàn bộ response phiên. FE đọc lại phiên để cập nhật màn hình.

**Provider:** endpoint submit phiên chỉ sàng lọc/chuẩn bị. Nếu đạt, phiên vẫn `READY_TO_SUBMIT`, không tạo lượt Admin duyệt riêng và không kích hoạt tài khoản. **Renter:** endpoint này áp dụng quyết định hệ thống theo policy, có thể kích hoạt tài khoản khi đạt điều kiện.

## 4. Hồ sơ Provider và gửi chung

Quyền: `ROLE_PROVIDER_ONBOARDING` hoặc `ROLE_PROVIDER`, đồng thời phải đạt điều kiện trạng thái trong service.

| Method | Endpoint | Request/kết quả |
| --- | --- | --- |
| GET | `/api/provider/onboarding/options` | Danh sách `{code, name, minimumAccountLength, maximumAccountLength}` của ngân hàng |
| GET | `/api/provider/onboarding` | Hồ sơ hiện tại và các cờ quyền thao tác |
| PUT | `/api/provider/onboarding` | Lưu thông tin theo ví dụ bên dưới |
| POST | `/api/provider/onboarding/submit` | `{ "version": 4 }`; gửi chung hồ sơ và phiên bằng chứng |

```json
{
  "version": 0,
  "displayName": "Dich vu xe Demo",
  "hotline": "0900000000",
  "depotAddress": "Dia chi diem giao nhan",
  "latitude": 10.776889,
  "longitude": 106.700806,
  "bankCode": null,
  "bankAccountNo": null,
  "bankAccountHolder": null
}
```

Đây là dữ liệu minh họa cấu trúc, không phải thông tin tài khoản có sẵn. Tọa độ tối đa 6 số thập phân. Nếu khai báo ngân hàng, nhập đủ 3 trường và dùng `bankCode` từ endpoint options.

### Response hồ sơ

| Field | Ý nghĩa |
| --- | --- |
| `userId`, `version` | Tài khoản và version riêng của hồ sơ dịch vụ |
| `status` | DRAFT/SUBMITTED/NEEDS_CHANGES/REJECTED/APPROVED |
| `profile` | Thông tin dịch vụ đã lưu, có thể null khi chưa lưu |
| `identityVerified` | Danh tính Provider đã được duyệt; không bắt buộc true trước khi gửi hồ sơ tổng hợp mới |
| `canEdit`, `canSubmit` | Cờ thao tác; backend vẫn kiểm tra lại khi mutation |
| `identityAttemptId` | Phiên CCCD/video được gắn tại lúc gửi; có thể null với dữ liệu đã xác minh từ luồng cũ |
| `submittedAt`, `reviewedAt`, `reviewedBy`, `reasonCode` | Thời gian và phản hồi |
| `nextAction`, `email`, `fullName` | Điều hướng và thông tin tài khoản |

Không dùng `identityVerified=false` làm lý do bắt người dùng đợi duyệt riêng. FE dựa vào `canSubmit`; backend tự gắn phiên phù hợp khi gửi chung.

## 5. Admin duyệt một lần

Tất cả API trong mục này yêu cầu role ADMIN.

| Method | Endpoint | Request/kết quả |
| --- | --- | --- |
| GET | `/api/admin/provider-applications?page=0&status=SUBMITTED` | Mảng hồ sơ, tối đa 20 mục mỗi trang; page bắt đầu từ 0 |
| GET | `/api/admin/provider-applications/{userId}` | Chi tiết hồ sơ, có `identityAttemptId` |
| GET | `/api/admin/identity-attempts/{identityAttemptId}` | Chi tiết OCR/AI của phiên đi kèm |
| GET | `/api/admin/identity-attempts/{identityAttemptId}/evidence/{kind}` | Tải CCCD/video riêng tư |
| POST | `/api/admin/provider-applications/{userId}/review` | Một quyết định cho cả danh tính và thông tin dịch vụ |
| GET | `/api/admin/provider-applications/{userId}/history` | Mảng `{version, adminId, decision, reasonCode, reviewedAt}` |

Chấp nhận:

```json
{ "version": 5, "decision": "APPROVE", "reasonCode": null }
```

Yêu cầu bổ sung:

```json
{ "version": 5, "decision": "NEEDS_CHANGES", "reasonCode": "ADDRESS_REQUIRED" }
```

Từ chối:

```json
{ "version": 5, "decision": "REJECT", "reasonCode": "DETAILS_INCONSISTENT" }
```

Quyết định ngoài APPROVE phải có lý do dạng mã viết hoa theo regex `[A-Z][A-Z0-9_]{2,99}`. Các mã có nhãn trên FE gồm `DOCUMENT_UNREADABLE`, `FACE_UNCLEAR`, `DETAILS_INCONSISTENT`, `ADDRESS_REQUIRED`, `BANK_FIELDS_REQUIRED`, `OTHER_REVIEW_REQUIRED`.

`REOPEN` còn được API hỗ trợ cho dữ liệu cũ bị từ chối, nhưng không phải bước bắt buộc của flow gửi lại mới.

**Không gọi** `POST /api/admin/identity-attempts/{id}/review`: endpoint duyệt danh tính riêng đã trả HTTP 410 với `PROVIDER_COMBINED_REVIEW_REQUIRED`. Endpoint danh sách danh tính cũ còn tồn tại, nhưng không phải hàng đợi Admin chính của flow mới.

## 6. Liên kết danh tính hai tài khoản

| Method | Endpoint | Request |
| --- | --- | --- |
| POST | `/api/identity/links` | `{ "targetEmail": "provider@example.com", "password": "<mat-khau-tai-khoan-nguon>" }` |
| GET | `/api/identity/links?page=0` | Danh sách yêu cầu liên quan, 20 mục/trang |
| POST | `/api/identity/links/{id}/confirm` | `{ "version": 0, "password": "<mat-khau-tai-khoan-dich>" }` |
| POST | `/api/identity/links/{id}/cancel` | `{ "version": 0 }` |

Nguồn phải là tài khoản đủ điều kiện đã xác minh; đích là tài khoản PENDING khác role, chưa có danh tính. Provider onboarding có thể xem/xác nhận yêu cầu nhưng không tự làm nguồn chưa xác minh. Nếu tài khoản đích đang có phiên xác minh hoạt động thì cần hủy phiên trước khi xác nhận liên kết.

Các biến thể `/api/auth/activate/...` phục vụ activation token của tài khoản chưa đăng nhập; FE Provider hiện dùng các API Bearer phía trên. Không cần thêm activation endpoint vào chuỗi đăng ký Provider đang đăng nhập.

## 7. Lỗi và concurrency

Response lỗi Spring thường có `timestamp`, `status`, `error`, `message`, `path`, `errorCode`, `traceId`. Không phải mọi lỗi validation đều có mã nghiệp vụ riêng.

| Mã/tình huống | Cách xử lý |
| --- | --- |
| `IDENTITY_ATTEMPT_STALE`, `PROVIDER_APPLICATION_STALE` | GET lại đúng tài nguyên, dùng version mới; không tự gửi đè dữ liệu đã đổi |
| `IDENTITY_FACE_RETRY_REQUIRED` | Kiểm tra/video lại; không hiểu là đã gửi Admin |
| `IDENTITY_THRESHOLD_NOT_CONFIGURED` | Kiểm tra cấu hình ngưỡng BE |
| `IDENTITY_EVIDENCE_INCOMPLETE` | Hoàn thiện CCCD/video |
| `IDENTITY_LINK_REQUIRED` | CCCD đã thuộc danh tính có sẵn; thực hiện liên kết hai tài khoản |
| `IDENTITY_PHONE_VERIFICATION_REQUIRED` | Xác nhận OTP điện thoại |
| `PROVIDER_ACCOUNT_VERIFICATION_REQUIRED` | Kiểm tra email, phone OTP, điều khoản |
| `PROVIDER_BANK_FIELDS_REQUIRED`, `PROVIDER_BANK_ACCOUNT_INVALID` | Bổ sung đúng thông tin ngân hàng |
| HTTP 400 validation tọa độ | Phạm vi hợp lệ và tối đa 6 số thập phân |
| AI timeout/422 | Đọc lại phiên vì backend có thể đã ghi ERROR và tăng version |
| HTTP 410 duyệt danh tính riêng | Chuyển sang review hồ sơ Provider tổng hợp |

## 8. Spring gọi AI

FE không gọi trực tiếp các service AI và không giữ API key AI.

- Spring gọi DocumentAI đọc hai mặt CCCD.
- Spring gọi IdentityVerification `/v1/verifications` để đối chiếu khuôn mặt/video và `/v1/constraints` để lấy giới hạn.
- IdentityVerification dùng header `X-API-Key` theo cấu hình bridge.
- HMAC fingerprint secret, key AI và mật khẩu không được đặt trong tài liệu hoặc bundle FE.
