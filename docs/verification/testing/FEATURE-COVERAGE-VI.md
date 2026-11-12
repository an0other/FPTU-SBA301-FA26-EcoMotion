# Đối chiếu chức năng với đặc tả

Đối chiếu source ngày 11/11/2026. Đây là ma trận yêu cầu và bằng chứng code, không phải nhật ký triển khai hoặc báo cáo E2E. “Đã có” nghĩa đã tìm thấy luồng trong source, không đồng nghĩa đã kiểm chứng với PostgreSQL/AI/browser thật.

## Kết luận

**Chưa đủ toàn bộ đặc tả.** Có luồng chính Admin duyệt renter/provider/GPLX, nhưng manual fallback cho CCCD, hoàn thiện trường CCCD và một số quy tắc bổ sung/lịch sử còn thiếu. Không dùng số test tự động đã đạt để suy ra độ phủ toàn bộ yêu cầu.

## Phần đã có trong source

| Yêu cầu | Điểm đối chiếu | Giới hạn |
|---|---|---|
| Renter gửi Admin, không tự duyệt theo điểm | IdentityDecisionServiceImpl.assessSubmission/reviewRenter | Vẫn yêu cầu OCR đọc đủ trường lõi trước khi gửi |
| Provider duyệt kết hợp, không chặn bởi điểm tương đồng | providerEvidenceReady/submitProviderEvidence và ProviderOnboardingServiceImpl | Chưa có manual correction CCCD trong form kết hợp |
| GPLX không QR, OCR thiếu/mâu thuẫn có thể gửi Admin | DrivingLicenseReviewService.prepare/submit | Phải nhận được loại GPLX và response xử lý hợp lệ; AI lỗi chưa có fallback độc lập |
| GPLX ứng viên tách khỏi bản hiệu lực | DrivingLicenseSubmission và review | Không tự chuyển đổi nguồn gốc hồ sơ cũ/seed |
| Admin sửa sai OCR GPLX, giữ dữ liệu gốc | review/validateApproval và history.approvedData | Không áp dụng cho CCCD |
| Đối chiếu GPLX với danh tính và điều kiện thuê | validateApproval và DrivingLicenseEligibilityPolicy | Đây là nhất quán dữ liệu, không tra cứu cơ quan cấp |
| Ghi chú và lịch sử quyết định renter | IdentityReviewEvent/reviewRenter | Các lượt xử lý/ảnh nháp chưa phải lịch sử đầy đủ |
| Phiên bản, khóa, chặn callback AI cũ | requireVersion/requireOperation, repository lock | Cần xác minh transaction/concurrency bằng DB thật |
| Điểm OCR và cosine được trình bày đúng phạm vi | DocumentAiEvidence, VerificationEvidenceSummary | Chưa có registry/policy đầy đủ hoặc ngưỡng đã kiểm định |
| Chủ hồ sơ/Admin xem ảnh qua endpoint có quyền | identity/license controllers/services | Đã bổ sung chặn tên ảnh GPLX mới ở endpoint file public; bucket cloud cần kiểm chứng cấu hình thực tế |
| Theo dõi trạng thái sau Admin duyệt | IdentityReadController và ActivatePage | Cần token còn hạn hoặc đăng nhập lại; chưa test browser thật |

## Thiếu hoặc chưa khớp — ưu tiên xử lý

### P1. CCCD OCR thiếu vẫn chưa tới được Admin

Chuỗi chặn hiện có:

1. `IdentityAiAssessmentServiceImpl.parseDocument`: requiredFieldsReadable yêu cầu số 12 chữ số, họ tên và ngày sinh đọc được.
2. `IdentityVerificationAttemptServiceImpl.uploadDocuments`: kết quả thiếu trường quay về DRAFT.
3. `requireVideoReady` và `IdentityVerificationPanel`: chặn bước video khi requiredFieldsReadable=false.
4. `IdentityDecisionServiceImpl.assessSubmission`: tiếp tục yêu cầu requiredFieldsReadable trước SUBMITTED.
5. `approve`: Admin chỉ duyệt dữ liệu đã đọc, chưa nhận bộ dữ liệu đã đối chiếu/sửa từ ảnh.

Không được chỉ đổi boolean sang true: phải tách “bằng chứng đủ cho review” khỏi “đã đọc đủ dữ liệu”, mở bước video theo bằng chứng phù hợp, thêm form Admin sửa/điền trường và lưu giá trị gốc/giá trị xác nhận riêng. CCCD INCONSISTENT_DATA cũng chưa đi được tuyến này. Áp dụng cùng nguyên tắc cho renter và provider kết hợp.

### P1. Ngày hết hạn CCCD chưa có xác nhận thủ công đầy đủ

`approve` cho phép expiryDate trống (`date(value,false)`), còn logic trạng thái chỉ xem là hết hạn nếu có ngày cụ thể. Chưa có form Admin điền/xác nhận ngày hết hạn hoặc xác nhận trường hợp không áp dụng theo mẫu được hỗ trợ. Cần tránh biến “chưa đọc được” thành “không cần kiểm tra hạn”; không tự suy ra mọi CCCD đều có/không có hạn từ đặc tả này.

### Đã sửa ở source: quyền xem lịch sử khi bổ sung

`begin()` xóa submittedAt khi upload lại. `reviewableAttempt()` lại yêu cầu submittedAt khác null cho detail/evidence/historyEvidence. Vì thế khi người dùng đang bổ sung và chưa gửi lại, mục NEEDS_CHANGES có thể không còn trong hàng chờ và Admin mở bản cũ có thể nhận not found. Đã tách điều kiện xem thành submittedAt hoặc event history; bổ sung endpoint owner xem đúng file snapshot theo eventId. Vẫn cần kiểm chứng persistence thật, và không khôi phục được file cũ chưa từng được lưu/đã bị xóa trước cơ chế history.

### P1. GPLX bổ sung chưa kiểm tra thay ảnh theo nội dung

Luồng mới tạo submission khác nhưng chưa lưu/so sánh checksum ảnh với bản bị yêu cầu bổ sung; nộp lại cùng ảnh với tên khác vẫn tạo bản mới. Đặc tả yêu cầu bằng chứng được yêu cầu thay phải thay thực sự. Cần checksum và yêu cầu thay theo mặt/tiêu chí, không chặn ảnh cố ý giữ nguyên khi chỉ mặt còn lại cần sửa.

### P2. Bằng chứng mâu thuẫn chưa giữ đủ hai giá trị

DocumentAiEvidence giữ source/status/confidence và tên trường mâu thuẫn, nhưng FieldEvidence chưa có qrValue/ocrCandidates/rawValue/normalizedValue. Admin biết có mâu thuẫn mà chưa so trực tiếp hai giá trị từ adapter. Cần projection có whitelist, giữ đúng nguồn mà không trả raw response/đường dẫn nội bộ.

### P2. Vòng đời chưa đồng nhất hoàn toàn

- GPLX chỉ hủy READY_TO_SUBMIT, chưa hủy/rút SUBMITTED, chưa EXPIRED hoặc REOPEN tại chỗ; nộp mới sau REJECTED là đường hiện có.
- Danh tính còn DOCUMENT_READY/ERROR như tầng trạng thái, chưa tách đầy đủ trạng thái run và trạng thái hồ sơ.
- Chưa có luồng thay thế danh tính renter đã APPROVED; get/create/submit hiện ngăn tài khoản đã gắn danh tính. Bản GPLX thay thế đã tách riêng.
- Chưa có nơi người dùng gửi yêu cầu xem xét lại chính thức; không nên hiển thị nút khi chưa có nơi nhận.

### P2. Chính sách check và khả năng mở rộng mới ở mức projection

Đã chuẩn hóa status/unknown về INCONCLUSIVE, nhưng chưa có registry requiredFor/mode/onUnavailable, provenance modelVersion/thresholdVersion và run history đầy đủ. Mỗi loại giấy tờ vẫn có nhánh điều kiện riêng. Có chỗ để bổ sung tín hiệu ảnh nhưng chưa thể coi registry policy trong đặc tả đã triển khai.

### P2. Lưu trữ, truy cập và tài nguyên

- Có kiểm tra owner/Admin và không trả storage key; chưa tìm thấy audit đọc bằng chứng, retention/job dọn có giữ tranh chấp, hoặc throttling theo user cho toàn bộ luồng này.
- Identity AI có semaphore giới hạn lượt song song trong một instance; không tương đương rate limit toàn hệ thống.
- GPLX prepare gọi AI trong transaction giữ khóa renter. Chưa có job/lease/retry lưu trạng thái độc lập; AI lỗi trước store thì không có bản nộp bền vững để manual fallback.
- Cần xác nhận cấu hình bucket không public nếu dùng cloud; chặn endpoint backend không thay thế quyền truy cập trực tiếp ở bucket.

## Lỗi đã khắc phục trong lần đối chiếu này

`FileDownloadController` public từng chỉ chặn `driving-license-front/back-*`, trong khi luồng mới lưu `license-review-front/back-*`. Đã mở rộng chặn cả hai họ tên file, kể cả alias download, trước khi đọc storage. Test tập trung kiểm tra deny và giữ ảnh public hợp lệ hoạt động. Đây là sửa code, chưa phải bằng chứng đã bảo mật cấu hình bucket đang chạy.

## Còn phải kiểm chứng thực tế

Migration bảng/cột/index, cascade lưu history, rollback SQL+file, unique index và hai Admin đồng thời; giao tiếp schema/runtime với DocumentAI chính thức và IdentityVerification; luồng browser và phiên đăng nhập/activation. [Kịch bản chung](../demo/DEMO-SCENARIOS-VI.md) dùng cách chạy local tạo mới schema bằng Spring với DB_DDL_AUTO=create. Chưa xác nhận migration/index/rollback thực tế chỉ từ cách chạy tạo mới này.

## Thứ tự hoàn thiện đề xuất

1. CCCD manual review từ đầu đến cuối, kèm dữ liệu Admin xác nhận và quy tắc hạn giấy tờ.
2. Quyền xem lịch sử khi bổ sung và checksum GPLX theo yêu cầu thay ảnh.
3. Giữ đủ hai nguồn OCR/QR; đối chiếu và điều chỉnh các khác biệt trạng thái có chủ đích trong đặc tả.
4. Chốt vận hành MVP (retention, hạn hồ sơ, nơi nhận xem xét lại), sau đó kiểm chứng DB/AI/web thật.
5. Registry check đầy đủ, mở rộng model và chống giả mạo nâng cao làm sau; không tuyên bố đã có khi mới là hook/projection.

## Mức hoàn thiện thông tin trên UI

Đã có phép đếm theo policy backend và thanh % dành cho dữ liệu, không phải xác suất giấy tờ thật. Kết quả chờ duyệt/đã duyệt/từ chối được hiển thị độc lập; tín hiệu ảnh FLAGGED không bị 100% che mất. Đây là thay đổi khả năng giải thích, không khắc phục các thiếu sót nghiệp vụ P1 nêu trên và không bổ sung khả năng phát hiện gian lận thực tế.


## Tiến trình AI

CCCD/video: đã nối job của hai AI với progress theo operation ID, UI có nhãn bước thực tế;
chỉ upload có %. Test mock kiểm tra job bận, kết quả/lỗi, dọn tác vụ và không lẫn operation.
GPLX đã nối DocumentAI jobs và endpoint tiến trình kiểm tra chủ hồ sơ. Đã chạy API AI thật: CCCD và GPLX hoàn tất với 16 sự kiện mỗi job; video hoàn tất với 6 bước và 15 khung hình mẫu. Chưa kiểm chứng toàn luồng browser + BE + DB hoặc nhiều backend instance.
Tiến trình không bổ sung khả năng phát hiện giả mạo và không thay Admin quyết định.
