# Đăng ký Provider cá nhân

Tài liệu đối chiếu mã nguồn ngày 09/11/2026. Luồng hiện tại: chuẩn bị CCCD/video, nhập thông tin dịch vụ, gửi một hồ sơ và Admin duyệt một lần.

| Tài liệu | Nội dung |
| --- | --- |
| [FLOW.md](FLOW.md) | Luồng đăng ký, xử lý AI, xét duyệt và gửi lại |
| [API_REFERENCE.md](API_REFERENCE.md) | API Spring mà FE sử dụng, request và response chính |
| [BUSINESS_RULES.md](BUSINESS_RULES.md) | Điều kiện nghiệp vụ, trạng thái, quyền và dữ liệu |
| [VERIFY_GUIDE.md](VERIFY_GUIDE.md) | Chuẩn bị môi trường và checklist kiểm tra trên FE |

## Phạm vi

- Provider cá nhân; chưa mô tả đăng ký doanh nghiệp.
- Tài khoản Renter và Provider tách riêng, có thể liên kết danh tính sau khi xác nhận quyền sở hữu hai tài khoản.
- Renter tự xác minh theo policy; Provider phải được Admin duyệt hồ sơ tổng hợp.
- AI đọc dữ liệu và hỗ trợ sàng lọc; kết quả AI không tự kích hoạt Provider.
- Các tài liệu này mô tả code hiện tại, không thay thế việc verify thực tế với database và camera.

## Điểm vào mã nguồn

- FE: `Frontend/EcoMotion/src/pages/provider/ProviderOnboardingPage.jsx`.
- Admin: `Frontend/EcoMotion/src/pages/admin/AdminIdentityTab.jsx`.
- CCCD/video: `Frontend/EcoMotion/src/components/identity/IdentityVerificationPanel.jsx`, `VideoCapture.jsx`.
- API hồ sơ: `Backend/EcoMotion/src/main/java/com/fptufa26/ecomotion/user/controller/ProviderOnboardingController.java`.
- Nghiệp vụ hồ sơ: `user/service/implement/ProviderOnboardingServiceImpl.java` trong cùng root package.
- Xác minh: `verification/identity/service/implement/IdentityDecisionServiceImpl.java`, `IdentityVerificationAttemptServiceImpl.java`.
- Python khuôn mặt/video: `IdentityVerification/src/identity_verification/`.

Thư mục `../yearly/` dành cho 5 tài liệu yearly do người dùng tự chuyển vào. Không thay đổi các file yearly gốc trong lần tổ chức này.
