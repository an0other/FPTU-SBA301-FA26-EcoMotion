# Xét duyệt giấy tờ và danh tính — đặc tả MVP

Ngày chốt: 10/11/2026. Trạng thái: **đã triển khai phần đầu luồng renter gửi Admin duyệt; đặc tả tổng thể chưa hoàn tất**. Đã bổ sung chi tiết bằng chứng CCCD trên UI; Đã bổ sung lịch sử gửi/xét duyệt và ghi chú renter (cần migration bảng mới); Đã bổ sung GPLX chờ Admin duyệt với migration riêng; registry/policy kiểm tra đầy đủ còn trong kế hoạch.

Thư mục `verification` bao quát CCCD, GPLX và video đối chiếu danh tính; không gắn riêng với gói năm hoặc một nhà cung cấp AI.

## Thứ tự đọc

1. [Nghiệp vụ và phạm vi](specification/BUSINESS-FLOW-VI.md): quyết định đã chốt, vai trò, quyền sử dụng và giới hạn xác minh.
2. [Trạng thái và bảng quyết định](specification/STATES-AND-DECISIONS-VI.md): chuyển trạng thái, QR, ngoại lệ, bổ sung và xét duyệt.
3. [Hợp đồng bằng chứng và UIUX](specification/EVIDENCE-AND-UIUX-VI.md): cách thêm bộ kiểm tra AI, điểm số, thông báo và dữ liệu lịch sử.
4. [Kiểm thử và nghiệm thu](testing/ACCEPTANCE-TESTS-VI.md): các điều kiện cần kiểm chứng.
5. [Kịch bản demo](demo/DEMO-SCENARIOS-VI.md): các tuyến trình diễn chức năng.
6. [Thiết kế chuyển đổi và triển khai](implementation/IMPLEMENTATION-PLAN-VI.md): khác biệt với code hiện tại và thứ tự triển khai.

## Những điểm đã chốt

- Renter: CCCD và video cần Admin xét duyệt; không tự duyệt chỉ vì đạt điểm đối chiếu.
- Provider: giữ xét duyệt kết hợp với hồ sơ hoạt động, tránh hai quyết định Admin độc lập cho cùng một bộ bằng chứng.
- GPLX: Admin xét duyệt; mẫu cũ không QR vẫn được tiếp nhận.
- AI hỗ trợ thu thập và đánh giá bằng chứng. Điểm thấp hoặc tín hiệu nghi ngờ không tự kết luận giấy tờ giả.
- Giữ nguyên model/ngưỡng; đã mở rộng tiến trình job của DocumentAI chính thức và IdentityVerification để UI hiển thị bước thực tế.
- Giữ mã `SUBMITTED` cho trạng thái chờ duyệt để phù hợp code hiện tại; không thêm `PENDING_REVIEW` đồng nghĩa.

## Quan hệ với tài liệu cũ

[Flow GPLX gói năm](../yearly/YEARLY_RENTAL_GPLX_FLOW_VI.md) và [hướng dẫn demo năm](../yearly/YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md) mô tả bản đang chạy và seed hiện có. Bộ tài liệu này mô tả **đích triển khai tiếp theo**, không tuyên bố bản hiện tại đã chuyển sang Admin duyệt tất cả. Các quy tắc giá, tuổi, hạng GPLX, số chỗ và kỳ thuê không được thay đổi bởi đặc tả này.

Khi triển khai phải cập nhật tài liệu cũ và nhãn demo đồng bộ. Hồ sơ seed không phải bằng chứng đã trải qua quy trình xác minh mới.

## Quy ước tổ chức

- `specification/`: mô tả nghiệp vụ, trạng thái và hợp đồng chức năng.
- `testing/`: điều kiện kiểm thử và kết quả mong đợi; bổ sung test case theo nhóm chức năng tại đây.
- `demo/`: kịch bản trình diễn; hướng dẫn tài khoản/dữ liệu được bổ sung khi có seed thực tế.
- `implementation/`: thiết kế chuyển đổi và kế hoạch kỹ thuật, không chứa nhật ký tiến độ từng lượt làm việc.

Output chạy thử, log và dữ liệu giấy tờ không đặt trong docs. Không tạo một file mới cho mỗi lần trao đổi; cập nhật đúng tài liệu theo chủ đề và điều hướng từ README này.

## Mức độ đáp ứng đặc tả

Xem [ma trận đối chiếu chức năng](testing/FEATURE-COVERAGE-VI.md) để biết phần đã có, phần còn thiếu và phần chưa kiểm chứng. Luồng chính đã được nối nhưng **chưa đầy đủ toàn bộ đặc tả**, đặc biệt CCCD thiếu dữ liệu chưa có tuyến manual review hoàn chỉnh. Không coi test tự động đạt là xác nhận mọi tình huống nghiệp vụ hoặc bảo mật runtime đã được kiểm chứng.

Giao diện kết quả hiện có thanh **Mức hoàn thiện thông tin** do backend tính, checklist và hướng dẫn hành động. Xem [quy tắc hiển thị](specification/EVIDENCE-AND-UIUX-VI.md#kết-quả-dành-cho-người-dùng-và-thanh-phần-trăm). 100% không phải xác minh thành công; snapshot cũ cần lượt đọc mới để có số đo.
