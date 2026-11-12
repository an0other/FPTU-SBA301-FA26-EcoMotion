# Thiết kế chuyển đổi và kế hoạch triển khai

**Trạng thái:** đã triển khai bước gửi/review renter trên cấu trúc hiện có; các phần còn lại là kế hoạch. Xem [mục lục](../README.md).

## Phần đã triển khai

- Renter gửi CCCD/video đủ điều kiện vào SUBMITTED, không tự duyệt theo điểm hoặc yêu cầu cấu hình ngưỡng tự động.
- Admin: Xác minh & Provider mới → loại hồ sơ Danh tính renter → xem bằng chứng → chấp nhận/yêu cầu bổ sung/từ chối; có mở lại hồ sơ bị từ chối.
- Endpoint `/api/admin/identity-attempts` liệt kê renter. Endpoint review trực tiếp chỉ cho renter; provider vẫn dùng duyệt kết hợp.
- Tận dụng khóa version và dấu vân tay bằng chứng; yêu cầu bổ sung video/giấy tờ đánh dấu bằng chứng cần thay.
- Chưa cần migration cho phần này; không đổi hồ sơ đã APPROVED hoặc DB seed.

## Hợp đồng bằng chứng đã bổ sung

`IdentityAiAssessmentResponse.DocumentRead.evidence` lưu projection có giới hạn từ DocumentAI: requestId/schema/pipeline, statusReasons, warnings, trường thiếu/mâu thuẫn, nguồn và confidence từng trường, kết quả các bước đọc và tín hiệu ảnh. Lưu cùng documentJson đã mã hóa và revision hiện có; không có migration cho phần này.

Không lưu raw response, OCR toàn ảnh, payload QR, đường dẫn nội bộ hoặc metrics tự do trong API mới. Điểm thiếu/không hữu hạn/ngoài thang 0–1 trả null. Trạng thái check không nhận biết chuyển INCONCLUSIVE, không PASS. Không tự suy ra ngưỡng OCR từ frontend.

UI chung `VerificationEvidenceSummary` dùng cho người nộp, Admin và snapshot Provider. User thấy QR, trường cần đối chiếu, lý do và giới hạn. Admin thấy thêm điểm OCR thô (không phải xác suất), phiên bản và check nguồn. Video hiển thị điểm cosine riêng cho Admin, không đổi thành phần trăm. Snapshot cũ không có evidence được hiển thị thiếu dữ liệu, không giả định đã đạt.

Đây là bổ sung khả năng giải thích, không đổi tiêu chí gửi hồ sơ, không nâng cấp model, không tự chấp nhận theo điểm. Lịch sử đầy đủ và GPLX tiếp tục nằm trong phạm vi chưa triển khai.

## Lịch sử và ghi chú đã bổ sung

Renter gửi hồ sơ và Admin quyết định tạo dòng `identity_review_event`. Mỗi dòng giữ event/actor/time/note, revision giấy tờ, phiên bản trước thao tác, checksum và snapshot DocumentRead/video. Snapshot giấy tờ và ghi chú dùng converter mã hóa hiện có. Entity immutable, không có API sửa/xóa lịch sử; đây không phải cam kết bất biến trước người có quyền sửa trực tiếp DB.

Yêu cầu bổ sung/từ chối/mở lại qua endpoint renter cần ghi chú 5–1.000 ký tự. UI user hiển thị phản hồi gần nhất và timeline; Admin xem snapshot và tải bằng chứng lịch sử qua endpoint `/api/admin/identity-attempts/{id}/history/{eventId}/evidence/{kind}`. Backend kiểm tra quyền Admin và event thuộc đúng hồ sơ; không trả storage key ra client.

Các file được giữ qua `evidenceRetained` để tránh mất bằng chứng lịch sử khi upload lại. Chưa có job tự dọn theo hạn lưu: cần chốt retention trước vận hành lâu dài; không được tự xóa file vẫn có lịch sử tham chiếu. Các thao tác trên cùng attempt dùng khóa/version hiện có và ghi lịch sử trong cùng transaction.

### Cập nhật database

DB đã tồn tại cần áp dụng **chỉ migration bổ sung** `Backend/EcoMotion/src/main/resources/db/migrations/20261110_identity_review_history.sql` trước khi chạy bản mới. File này tạo bảng/index, không đổi dữ liệu cũ. Chưa được chạy tự động trong đợt phát triển này. Không chạy `schema.sql` trên DB đang giữ dữ liệu: đây là script tái tạo có DROP. Kiểm tra cấu hình `DB_DDL_AUTO` trước khi khởi động, không dùng chế độ create để cập nhật DB đang dùng.

Sau migration kiểm tra bảng/cột/index và quyền kết nối. Nếu rollback ứng dụng, có thể giữ bảng mới để bảo toàn lịch sử; không drop bảng để rollback. Lịch sử bắt đầu từ lúc tính năng mới chạy, không tạo sự kiện giả cho hồ sơ cũ.

## Giới hạn của phần triển khai đầu

Đã có lịch sử và ghi chú renter theo cơ chế mới, không backfill lịch sử cũ; chưa mở manual transcription cho OCR thiếu; đã bổ sung projection check/confidence cho CCCD, chưa đầy đủ registry/policy cho mọi loại giấy tờ; đã bổ sung GPLX theo hồ sơ tách biệt như mô tả bên dưới. Provider đã đồng bộ sang điều kiện đủ bằng chứng/động tác để gửi duyệt kết hợp; không dùng ngưỡng tương đồng để chặn hàng chờ. Chưa kiểm thử trình duyệt với dịch vụ AI/DB thật; không coi unit test/build là nghiệm thu end-to-end.

## Khoảng cách tại thời điểm lập đặc tả (trước triển khai)

Đối chiếu source ngày 10/11/2026; không phải xác nhận cấu hình dịch vụ đang chạy.

| Hiện tại | Đích cần triển khai |
|---|---|
| IdentityDecisionServiceImpl có tự duyệt renter khi đạt median/ngưỡng và frame coverage | Renter gửi hàng chờ Admin; không tự APPROVED |
| Ngưỡng mặc định application.properties là 0.55; tối thiểu 4 frame và coverage 0.75 | Giữ thành metric/policy có phiên bản nếu dùng tham khảo; không diễn giải là độ chính xác |
| IdentityAiAssessmentServiceImpl chỉ giữ một phần thông tin DocumentAI | Giữ check, reasons, confidence, nguồn và giới hạn cần thiết theo revision |
| RenterDrivingLicenseServiceImpl có ngoại lệ demo REVIEW_REQUIRED/OCR-only | Định tuyến Admin rõ ràng, không coi ngoại lệ là đã xác minh |
| Luồng danh tính dùng requiredFieldsReadable để mở bước tiếp theo | Phân biệt đủ dữ liệu để phân tích với đủ bằng chứng để Admin nhận review; hỗ trợ manual transcription có provenance |
| Admin identity hiện thiên về provider | Bổ sung hàng chờ và quyền review renter/GPLX, giữ provider combined review |
| UI user ít giải thích kết quả check | Tiến trình, lý do cụ thể, hành động tiếp theo, không dùng xanh cho chưa đạt |

Source chính thức DocumentAI: `D:/PYTHON/DocumentAI-v0.2.0/DocumentAI`; API source khai báo 0.32.0. Bản trong EcoMotion không phải nguồn chuẩn để nâng cấp AI. Cần pin phiên bản/schema khi tích hợp, không dựa vào tên thư mục. DocumentAI chính thức đã bổ sung snapshot tiến trình job; IdentityVerification bổ sung job video. Không thay model hoặc ngưỡng quyết định.

## Thứ tự triển khai

1. Chốt mapping enum hiện có, schema kết quả chuẩn hóa và bảng policy; bổ sung test quyết định trước.
2. Mở rộng lưu evidence revision/run/check/audit và khóa phiên bản; migration có phương án rollback và không phá hồ sơ cũ.
3. API nhận/gửi/bổ sung, review renter và GPLX; provider kết hợp giữ transaction và snapshot đúng revision.
4. Quyền sử dụng dựa trên hồ sơ hiệu lực; kiểm tra các điểm đặt thuê, hợp đồng, thanh toán và nhận xe liên quan. Không đổi tài chính hay nghiệp vụ gói thuê ngoài phạm vi.
5. UI người nộp/Admin theo hợp đồng mới, ánh xạ mã lỗi, xử lý retry/idempotency.
6. Seed demo tách file riêng, chỉ dữ liệu giả có provenance; cập nhật docs yearly/provider và chạy kiểm thử hồi quy.

Không đổi toàn bộ hồ sơ cũ APPROVED về chưa duyệt bằng migration hàng loạt. Phân loại phương thức cũ AUTOMATED_POLICY/ADMIN_REVIEW/DEMO_SEED, hiển thị provenance; quy tắc chuyển đổi hồ sơ đang dùng phải được team duyệt trước khi phát hành. Bộ tài liệu này không tự cấp quyền cho seed hoặc tự hủy booking cũ.

## Việc còn phải chốt trước phát hành

Thời gian lưu bằng chứng và phiên bổ sung; người trực hàng chờ/thời gian phản hồi; điểm tiếp nhận yêu cầu xem xét lại; kế hoạch chuyển đổi hồ sơ cũ. Đây là cấu hình và quy trình vận hành còn mở, không ngăn triển khai đặc tả cốt lõi nhưng phải hoàn tất trước khi tuyên bố tính năng sẵn sàng vận hành.

## Tài liệu liên quan

- [Kiểm thử nghiệm thu](../testing/ACCEPTANCE-TESTS-VI.md).
- [Kịch bản demo](../demo/DEMO-SCENARIOS-VI.md).

## GPLX — hồ sơ nộp và bản hiệu lực

Luồng mới dùng `driving_license_submission`, không ghi dữ liệu OCR trực tiếp lên renter. Hai mặt ảnh hợp lệ và các kết quả READ_SUCCESS/REVIEW_REQUIRED/PARTIALLY_READ/INCONSISTENT_DATA có thể chuẩn bị hồ sơ. Người dùng khai báo không QR chỉ để Admin đối chiếu, không tắt bộ đọc QR. Kết quả không có dữ liệu đủ đọc tự động vẫn có thể được Admin đối chiếu ảnh và hoàn thiện trường bắt buộc.

Chuẩn bị → READY_TO_SUBMIT → người dùng xác nhận → SUBMITTED → Admin APPROVED/NEEDS_CHANGES/REJECTED. Hủy bản chuẩn bị tạo CANCELLED. Bổ sung tạo hồ sơ mới; bản cũ và ảnh giữ nguyên. Một renter chỉ có một bản READY_TO_SUBMIT/SUBMITTED; dùng khóa renter, version hồ sơ và partial unique index khi áp dụng SQL. Chưa có tự hết hạn bản chuẩn bị hoặc mở lại REJECTED tại chỗ; có thể nộp bản mới.

Admin xem ảnh, kết quả AI và có thể sửa sai OCR để xác nhận thông tin. Giá trị AI gốc giữ riêng; giá trị được xác nhận lưu trong lịch sử quyết định. Khi chấp nhận phải có số/họ tên/ngày sinh/hạng/ngày cấp và hạn hợp lệ, đồng thời đối chiếu họ tên (chuẩn hóa dấu/khoảng trắng) và ngày sinh với danh tính VERIFIED còn hạn. Đây là đối chiếu dữ liệu, không chứng minh giấy tờ thật. Họ tên thực tế khác cách ghi cần yêu cầu bổ sung, chưa có cơ chế override tùy ý.

Chỉ APPROVED cập nhật các trường GPLX hiệu lực của renter và licenseVerified. Các bản chờ/từ chối không thay GPLX đã có; giấy tờ hiệu lực hết hạn vẫn bị kiểm tra ở booking/hợp đồng/thanh toán/nhận xe theo policy cũ. Điều kiện hạng/tuổi/kỳ thuê/số chỗ không thay đổi. Hồ sơ cũ/seed được giữ nguyên, không tự gán đã được Admin duyệt mới.

Các endpoint upload/PUT GPLX cũ trả 410 LICENSE_REVIEW_REQUIRED, ngăn client cũ đi đường OCR tự xác minh. GET GPLX và ảnh hiệu lực giữ tương thích. UI nộp dùng `/api/renter/driving-license/submissions`, xác nhận `/.../{id}/submit`, hủy `/.../{id}/cancel`. Admin dùng `/api/admin/driving-license-submissions` và review theo version. Ảnh nộp riêng tư: endpoint theo owner/Admin; không trả đường dẫn lưu trữ.

DB hiện hữu cần áp dụng migration `20261110_driving_license_submission.sql` trước khi chạy bản mới, ngoài migration lịch sử danh tính trước đó. Chưa chạy SQL trên DB người dùng. Không chạy schema.sql để cập nhật DB đang dùng; không dùng ddl-auto=create. Các file lịch sử giữ lại, cần chính sách retention sau MVP. Chưa kiểm chứng persistence/concurrency bằng PostgreSQL thật.

Cách xem: renter tại phần GPLX hồ sơ cá nhân → chuẩn bị và xác nhận gửi; Admin → Xác minh & Provider mới → chọn Hồ sơ GPLX. Sau Admin chấp nhận renter bấm Cập nhật trạng thái để tải lại GPLX hiệu lực. Chức năng này chưa thêm seed giấy tờ thật.

## Tích hợp trước kiểm thử web

Đã sửa endpoint read-only activation-status để token kích hoạt còn hạn đọc được trạng thái ACTIVE sau Admin duyệt. UI nhận diện kết quả và dẫn về đăng nhập. Endpoint sửa/xem bằng chứng qua activation token vẫn yêu cầu PENDING, không nới quyền ghi cho tài khoản đã kích hoạt.

Provider giữ duyệt kết hợp; điều kiện gửi xét duyệt là workflow READY_FOR_REVIEW và motion PASS, không dùng median/minimum frame coverage để thay quyết định Admin. Các cấu hình ngưỡng cũ không còn là gate quyết định hoặc điều kiện canSubmit; nếu vẫn khai báo phải hợp lệ theo kiểm tra cấu hình tương thích. Điểm vẫn hiển thị riêng cho người xét duyệt.

Lịch sử GPLX owner có phân trang GET `/api/renter/driving-license/submissions?page=0`; chỉ trả bản thuộc renter hiện tại, không nhận userId do client chỉ định. Hướng dẫn chạy trực tiếp tại [demo](../demo/DEMO-SCENARIOS-VI.md), dùng DB_DDL_AUTO=create cho database local chấp nhận xóa dữ liệu. Chưa chạy DB migration hay suy luận AI thật trong bước tích hợp này.

## Kết quả UI và mức hoàn thiện thông tin

Đã bổ sung `DocumentAiEvidence.completeness` theo policy `document-completeness/v1`, tính tại backend từ giá trị dữ liệu/định dạng/cờ review và nguồn đã đọc. Hợp đồng thêm trường JSON, không cần migration DB mới. Snapshot cũ không có trường này được giữ nguyên; muốn thấy thanh cần một lượt đọc mới, không tự tính lại lịch sử.

UI kết quả CCCD/GPLX hiển thị tiêu đề, thanh % và x/y mục bắt buộc ở bước chuẩn bị. Tín hiệu bất thường độc lập với số đếm; không cộng confidence và không tự APPROVED. User không thấy bảng kỹ thuật mặc định, dữ liệu đã đọc thu gọn, lịch sử rỗng ẩn. SUBMITTED hiển thị chờ duyệt thay thanh; Admin vẫn mở chi tiết nguồn/điểm/check. Nút tiếp tục chỉ theo khả năng hiện có của luồng, không theo phần trăm.

Chưa đổi engine hoặc thêm mô hình phát hiện ảnh AI/chỉnh sửa; chưa có ngưỡng kiểm định mới để tự từ chối vì gian lận. Các thiếu sót CCCD manual correction, lịch sử trong lúc bổ sung, checksum GPLX được ghi ở ma trận coverage vẫn còn, không được che bởi UI mới.

## Theo dõi xử lý và xem lại ảnh/video

Endpoint progress riêng trả status/processingStage/startedAt/observedAt/deadlineAt từ operation hiện có; không thêm cột DB. UI upload dùng số byte của trình duyệt, sau đó poll trạng thái theo mốc DOCUMENT/VIDEO. Đã tích hợp job-progress thực tế của DocumentAI và IdentityVerification qua adapter; trường aiProgress chứa bước/side/thời điểm cập nhật theo operation ID. Không suy ra phần trăm inference hay tuyên bố liveness từ tiến trình. API đồng bộ cũ vẫn còn; CCCD, video và GPLX đều gọi AI jobs; API chuẩn bị hồ sơ giữ phản hồi tương thích, tiến trình được đọc qua endpoint riêng.

Owner có danh sách phiên theo trang và endpoint xem bằng chứng lịch sử theo eventId, cả phiên đăng nhập và token kích hoạt đúng quyền. Gallery hiện tại tự tải ảnh sau đọc; xem video theo yêu cầu. Lịch sử event lấy key snapshot, không lấy key mới của attempt. Quyền Admin xem lịch sử không còn phụ thuộc duy nhất vào submittedAt hiện tại đã bị xóa khi bổ sung; event history là bằng chứng đã từng gửi.

Không cần migration mới ngoài hai migration lịch sử/GPLX đã chuẩn bị. Các snapshot trước khi triển khai lưu event không được dựng lại file đã mất. GPLX có tiến trình riêng gắn UUID và chủ hồ sơ, dùng cùng pipeline DocumentAI jobs; UI phục hồi theo endpoint processing/current khi tải lại trang. Lịch sử step chỉ chứa metadata theo whitelist, không chứa dữ liệu giấy tờ.

Lịch sử bằng chứng identity hiện ghi SUBMIT và quyết định cho cả renter lẫn provider trong luồng kết hợp. Không tạo queue provider riêng. Note chi tiết provider vẫn thuộc hồ sơ hoạt động và lịch sử provider đã có; timeline identity ghi action/reason/snapshot tương ứng. Các file của lần gửi provider mới được giữ qua evidenceRetained ngay tại SUBMIT để owner xem sau bổ sung.


## Cập nhật kiểm chứng

Đã bổ sung lưu pipeline vào DB, đọc lại sau tải trang, ảnh CCCD đối chiếu trong duyệt GPLX, lịch chọn ngày/giờ thuê năm có kiểm tra đủ 12 tháng, và hợp đồng đánh giá khuôn mặt theo phiên bản/hash model. Không bật ngưỡng từ dữ liệu một người.

Kiểm tra tự động: Python 4 ca, frontend 193 ca và build; backend nhóm hồi quy xác minh, duyệt hồ sơ, gói năm và điều phối đã qua. Đây chưa phải kiểm chứng thực tế trình duyệt/AI/DB mới sau thay đổi. Khởi động lại BE với cấu hình create theo lựa chọn của nhóm sẽ tạo bảng mới và xóa dữ liệu cũ; cần chạy lại seed rồi thử luồng demo.
