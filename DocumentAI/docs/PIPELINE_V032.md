# Pipeline v0.32.0 và cách mở rộng

## Trình tự thực thi

1. Decode/validate hai file; chặn file lỗi và hai ảnh giống hệt.
2. Localize/căn thẻ rồi kiểm tra chất lượng hai mặt; quality fail dừng và yêu cầu nộp lại.
3. QR quick precheck để phát hiện cặp xung đột sớm; nhận diện chiều, loại giấy tờ/mặt thẻ và pair gate.
4. IMAGE_ANALYSIS hook: phân tích trên nguồn ảnh gốc đã EXIF-orient, không dùng OCR/QR/ảnh đã warp làm căn cứ.
5. OCR chi tiết, rescue có giới hạn; đọc/đối chiếu QR; extraction theo ownership FRONT/BACK; tổng hợp.

QR quick precheck đã có từ luồng tối ưu trước đây, chỉ phục vụ phát hiện conflict sớm, không kết luận thật–giả. Stage summary DOCUMENT_AND_PAIR và OCR_QR nhóm nhiều bước; summary không có nghĩa mọi decode QR chỉ diễn ra sau image hook.

Image hook chỉ chạy khi quality và pair/orientation prerequisites đạt. Lỗi bộ phân tích ảnh được báo riêng và chuyển tới manual review; không hủy một kết quả OCR tốt hoặc biến lỗi thành PASS. Bản này chạy observe-only, chưa có policy auto-reject dựa vào image signals, kể cả custom analyzer trả FAIL. Không tùy tiện thêm gate trước khi model được kiểm định.

## Contract analyzer

`reader/image_analysis/contracts.py` định nghĩa:

- AnalysisInput: image, side, sha256 của file upload, source_format; không có tên, số thẻ, OCR hoặc QR.
- AnalysisResult: status, reasons, metrics và evidence bytes.
- ImageAnalyzer Protocol: id/version/capabilities và analyze(source).

Runner kiểm tra id duy nhất, JSON finite metrics, lỗi exception, tên evidence an toàn. Mỗi analyzer nhận copy của ảnh EXIF-oriented để không sửa ảnh nguồn OCR. Ở development, `DOCUMENT_AI_PIPELINE_TRACE=1` (mặc định) truyền `evidence_dir` và lưu artifact theo từng pha trong `runs/requests/<traceId>/stages/`; `runs/pipeline.log` ghi event theo thứ tự thực thi. Đặt biến thành `0` để tắt. Production không ghi ảnh hoặc trace. Mask localization là vùng hình học được căn theo góc thẻ, không phải mask phát hiện giả mạo.

Mặc định có:

| Analyzer | Hiện trạng | Verdict |
|---|---|---|
| ela_fft_diagnostics | Gọi diagnostics đã có, cạnh tối đa 1200, JPEG re-encode95 + FFT ratio | INCONCLUSIVE, không có ngưỡng fraud |
| digital_tampering | Chưa cấu hình model được kiểm định | NOT_IMPLEMENTED |
| screen_print_recapture | Chưa cấu hình model được kiểm định | NOT_IMPLEMENTED |

Nếu off, tất cả ghi SKIPPED, capabilities vẫn nêu chức năng chưa có. Không coi việc chạy qua các thuật toán là bằng chứng thẻ thật. ELA có thể phản ánh JPEG edges/nén lại; FFT có thể phản ánh hoa văn, texture, ánh sáng. Không có authenticity score hay phần trăm giả.

## Thêm model sau này

1. Viết adapter độc lập implement analyze(AnalysisInput), giữ id/version cố định cho model/config.
2. Dùng dataset được phép sử dụng, có nhãn phù hợp; chia train/test theo thẻ/người và thiết bị, tránh các ảnh cùng một thẻ rơi cả hai tập. Đo false positive/false negative, điều kiện ảnh và ngưỡng.
3. Test contract/timeout/error/numerical parity; model nặng nên chạy worker với deadline riêng. Runner hiện là đồng bộ, không hứa hard timeout cho code plugin tùy ý.
4. Inject `ImageAnalysisRunner('diagnostic', analyzers=[...])` vào `StagedReader(image_analysis=runner, ...)`, hoặc chỉnh composition root `configured_analysis()`. Không cần sửa OCR/QR/fusion.
5. Ban đầu observe-only và review cảnh báo. Chỉ thêm decision policy/gate khi đã có kiểm định và yêu cầu nghiệp vụ rõ, vẫn phân biệt không đủ điều kiện, nghi vấn, lỗi kỹ thuật và xác minh nguồn cấp.

Đây là điểm ghép module trong process. Không có HTTP endpoint cho client tải/nạp code hoặc chọn class tùy ý. Không tự tải model/API mới, không gọi dịch vụ bên ngoài, không thêm Rust vào đường chạy mới.

## Báo cáo và quyết định

`pipelineReport.stages` gồm INPUT_QUALITY, DOCUMENT_AND_PAIR, IMAGE_ANALYSIS, OCR_QR, DATA_CONSISTENCY. Trạng thái stage tổng hợp là PASS/FAIL/INCONCLUSIVE/SKIPPED/ERROR; phải đọc scope/reasons, không gọi một PASS ảnh là giấy tờ được cấp thật.

WorkflowStatus là TECHNICAL_FAILURE, NEEDS_RETAKE hoặc AWAITING_ADMIN_REVIEW. READING status và approval readiness vẫn riêng. cùng dữ liệu OCR–QR/cùng loại/mặt không chứng minh hai mặt thuộc cùng thẻ vật lý: samePhysicalCardVerified=false.

CCCD nay cũng có readingAssessment, expiryCheck theo ngày in và approval. Expiry không thay thế kiểm tra thu hồi, cơ quan cấp hoặc quyền điều khiển phương tiện. Tất cả automaticApproval/documentVerified vẫn false. Schema report bổ sung, không bỏ các field v31.

`assessment.documentAuthenticity` là bản tổng hợp bằng chứng eKYC riêng, không phải kết luận thật/giả. Các chiều hiện gồm toàn vẹn ảnh, chống ảnh màn hình/in lại, nguồn/cơ quan cấp, hiệu lực hiện tại, liên kết người giữ giấy tờ và liên kết hai mặt. Chỉ hai chiều đầu có thể nhận signal từ image analyzer; các chiều còn lại hiện rõ `NOT_IMPLEMENTED` hoặc `NOT_VERIFIED`. Ngay cả analyzer trả `PASS` cũng chỉ được diễn giải là `NO_ANOMALY_DETECTED`, không phải giấy tờ thật.

Analyzer khai báo metadata `implementation` và `scope`; runner không suy ra năng lực bằng cách kiểm tra class cụ thể. Analyzer mới vẫn nhận ảnh đã EXIF-orient, không nhận OCR/QR hay dữ liệu định danh. Muốn xác minh nguồn cấp, hiệu lực hoặc chủ giấy tờ, hãy thêm adapter ở đúng domain đó và chỉ tổng hợp theo policy có kiểm định; không đưa các bước này thành image analyzer. Schema report bổ sung, không bỏ các field v31.
