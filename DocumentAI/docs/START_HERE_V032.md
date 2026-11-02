# DocumentAI v0.32.0 — pipeline module và hợp đồng BE/FE

## Đã làm đến đâu?

Bản này nối tầng phân tích ảnh vào reader hiện tại, có contract/runner để đổi hoặc thêm analyzer, báo cáo từng stage, progress và assessment cho cả GPLX lẫn CCCD. Preset đọc khuyến nghị để test là `device` (exact width, FP32, NumPy, device decoder); `fixed` vẫn làm đối chứng. CPU/GPU tiếp tục cấu hình được. Không cần train lại, tải model mới hoặc cài Rust.

**Chưa triển khai model xác định Photoshop/screen/print recapture.** ELA/FFT chỉ trả số đo thử nghiệm và INCONCLUSIVE. Placeholder model trả NOT_IMPLEMENTED. Không gắn nhãn giả, không tự reject/approve từ những số đo này. Đây là pipeline đọc + đối chiếu + image signals có thể mở rộng, chưa phải dịch vụ xác thực giấy tờ hoàn chỉnh.

V1 vẫn một layout GPLX PET và một dạng CCCD đã quan sát. QR/OCR khớp không chứng minh nguồn cấp hay chủ sở hữu. CCCD bổ sung readingAssessment, printed expiry check và manual approval; legalValidity luôn NOT_VERIFIED.

Để trình bày rõ ranh giới giữa đọc ảnh và eKYC, xem [EKYC_ASSURANCE.md](EKYC_ASSURANCE.md). Report bổ sung `assessment.documentAuthenticity`; hiện trạng luôn `NOT_ESTABLISHED`, kể cả khi OCR đọc được hoặc image analyzer không thấy bất thường.

## Cập nhật

Giữ nguyên `.venv*`, ảnh, training, runs, model `.pt` và `.card.json`. Chép source/scripts/tools/docs mới; sao lưu profile đã sửa riêng trước khi cập nhật configs. ZIP không chứa model, ảnh cá nhân hoặc native binaries.

Nếu là checkout mới, `pip install -e . --no-deps` **không đủ**: lệnh này chỉ
cài project dạng editable và bỏ qua dependencies. Dùng `scripts/setup.ps1` để
tạo môi trường kiểm thử cơ bản; xem [SETUP.md](SETUP.md) để biết các runtime
inference còn cần cài riêng.

Launcher khuyến nghị cần checkpoint localizer
`models/localizer/document-card-seg-v1.pt` cùng file
`models/localizer/document-card-seg-v1.card.json` khớp hash. Hai file không
được commit và hiện không có bước tự tải checkpoint này; người chạy phải nhận
đúng cặp file từ chủ model. PaddleOCR/VietOCR cũng cần runtime và model assets
của chúng; lần khởi tạo đầu có thể tải model vào cache nếu máy chưa có. Thiếu
checkpoint localizer hoặc runtime tương ứng thì chưa thể chạy luồng inference
đầy đủ dù cài được package.

```powershell
.\.venv\Scripts\python.exe -m pip install -e . --no-deps
```

## Terminal 1 — chạy API

Lưu API key trong file `.env` tại thư mục gốc DocumentAI:

```powershell
DOCUMENT_AI_API_KEY=your-high-entropy-secret-at-least-32-characters
```

Thay placeholder bằng key của bạn, không commit `.env` hoặc gửi key xuống
trình duyệt. Launcher `serve-v32.ps1` tự nạp `.env` vào process environment; giá trị
`DOCUMENT_AI_API_KEY` trong `.env` được ưu tiên hơn biến cùng tên đã tồn tại
trong PowerShell để tránh dùng nhầm key cũ. Launcher in đường dẫn `.env` và độ
dài key để xác nhận file đã nạp; không in secret. Smoke test cũng đọc key từ cùng
file. API từ chối khởi động nếu key ngắn hơn 32 ký tự. Các endpoint đọc
tài liệu/jobs yêu cầu header `X-API-Key`; `/health`,
`/ready` và `/v1/capabilities` chỉ trả trạng thái/capability.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/serve-v32.ps1 `
  -Python .\.venv\Scripts\python.exe `
  -VietocrPython .\.venv-vietocr-gpu\Scripts\python.exe `
  -RecognizerDevice cuda:0 -Experiment device `
  -ImageAnalysis diagnostic -Environment development -Port 8001
```

Development mode mặc định bật trace (`DOCUMENT_AI_PIPELINE_TRACE=1`). Mỗi
request được ghi dưới `runs/requests/<traceId>/`: ảnh input, thư mục riêng cho
từng pha, JSON/JSONL diễn giải trạng thái, ảnh thẻ đã crop/căn chỉnh, mask vùng
thẻ, ảnh orientation, ảnh phân tích và overlay/crop OCR nếu bước đó chạy.
`runs/pipeline.log` ghi thứ tự các pha và kết quả check; không ghi nội dung
OCR/QR vào log tổng. Ảnh và OCR artifacts có thể chứa dữ liệu cá nhân nên chỉ
dùng local cho demo, không commit, và xóa sau khi kiểm tra.

Đặt `DOCUMENT_AI_PIPELINE_TRACE=0` trong `.env` để tắt việc lưu trace và ảnh
trong development. `-Environment production` luôn tắt trace và xử lý ảnh trong
bộ nhớ, kể cả khi biến trace đang bật. Thư mục `runs/` và log đã được ignore
bởi Git.

Chế độ `diagnostic` chạy ELA/FFT trên mỗi ảnh gốc EXIF-oriented trước rectification, tối đa cạnh 1200 trong thuật toán hiện có. Vì có thể resize/re-encode nên không phải phân tích byte gốc hay bằng chứng pháp y. Với PNG, không trả ELA compression metrics như dấu vết nén JPEG trước đó; FFT vẫn có số đo thử nghiệm.

`-ImageAnalysis off` giữ hook nhưng ghi SKIPPED và không chạy phép phân tích. Mode là cấu hình server, không cho client tự bật model tùy ý. Không nới quality/confidence threshold hoặc thay pipeline bằng kết luận QR để tăng tốc.

Đợi Application startup complete. Kiểm tra readiness tại
`http://127.0.0.1:8001/ready` và capability tại
`http://127.0.0.1:8001/v1/capabilities`. Inference chỉ dành cho backend/client
được cấp key; không gọi API trực tiếp từ browser.

Trong Swagger, bấm **Authorize**, nhập đúng value sau dấu `=` của
`DOCUMENT_AI_API_KEY` (không nhập tên biến, `=`, dấu nháy, hay tiền tố `Bearer`),
rồi bấm **Authorize** và **Close**. Nếu trước đó đã nhập sai key, bấm
**Logout** trước khi nhập lại. `Missing X-API-Key header` nghĩa là Swagger
chưa gửi header; `Invalid X-API-Key` nghĩa là header đã gửi nhưng value không
khớp với key server đã nạp.

Warm-up hiện bằng GPLX; lần CCCD đầu vẫn có thể phát sinh chi phí đặc thù. API giữ model resident, một inference cùng lúc; jobs RAM-only có TTL, không durable queue. Chưa có global deadline cho mọi model/module, chưa benchmark concurrency. Script không mở host ra mạng ngoài.

## Terminal 2 — một lượt test HTTP gọn

Smoke test tự đọc `.env` tại thư mục gốc DocumentAI. Nếu Spring gọi API, cấu
hình backend cần đọc cùng secret từ cấu hình môi trường riêng; không đưa key
vào frontend.

```powershell
.\.venv\Scripts\python.exe tools/test_api_v32.py `
  --base-url http://127.0.0.1:8001 `
  --output runs/v32-api-check-01
```

Đọc GPLX và CCCD bằng `/read`, đọc lại mỗi cặp bằng `/jobs` + GET poll, sau đó thử cặp trộn/ảnh trùng/file rỗng. Tổng bảy lượt inference hoặc early exit; không chạy lại 13 preset. Script dùng urllib chuẩn Python, không cần thư viện HTTP mới. Tên ảnh mặc định như v31, có thể ghi đè --gplx-front/--gplx-back/--cccd-front/--cccd-back.

Lưu các response và api-test.json. Thời gian POST jobs là nhận việc; wallUntilPollCompletionMs mới là chờ tới kết quả, còn gồm polling. Không xem equality giữa hai lần đọc là ground truth. First CCCD request ghi riêng từng ca; đây chưa phải thống kê p95/p99 hay độ chính xác tổng quát.

Nếu cần kiểm tra tác động thời gian của diagnostics, khởi động lại API với `-ImageAnalysis off`, chạy cùng lệnh vào thư mục output mới. Không tải lại model. Kiểm tra số đo/timing thực tế trước khi đưa diagnostics vào demo; chưa cam kết mức overhead.

## BE/FE đọc response thế nào?

- `status`: trạng thái đọc cũ, giữ tương thích READ_SUCCESS/REVIEW_REQUIRED/RETAKE_REQUIRED…
- `data`, `fields`, `ocrData`, `ocrLines`, `qrData`: giữ dữ liệu cuối và nguồn độc lập.
- `imageAnalysis`: kết quả từng analyzer, mode, phiên bản, lý do, input hash, metrics, evidence nếu được lưu. decisionUsed=false.
- `pipelineReport`: năm stage và workflowStatus riêng.
- `assessment.approval`: PENDING_MANUAL_REVIEW hoặc NOT_READY; automaticApproval=false, documentVerified=false.

READ_SUCCESS + IMAGE_ANALYSIS.INCONCLUSIVE là hợp lệ về contract: OCR có thể đọc được, còn ảnh chưa được xác thực. Không chuyển READ_SUCCESS/COMPLETED của job thành trạng thái admin APPROVED.

API /read và /jobs dùng cùng reader/hook và serialize cùng report. GET /v1/capabilities cho biết chức năng nào thực có, chức năng nào chưa được cấu hình. Giữ kết quả hoàn tất ở DB của BE; job trong Python mất khi restart. Tài liệu model adapter: PIPELINE_V032.md.
