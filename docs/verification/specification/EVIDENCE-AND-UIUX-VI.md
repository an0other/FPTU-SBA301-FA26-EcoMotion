# 03. Hợp đồng bằng chứng và UIUX

## Nguyên tắc mở rộng

Các adapter AI trả bằng chứng chuẩn hóa; chính sách backend định tuyến hồ sơ; frontend trình bày nextAction và reasonCode. Không để từng model tự đặt trạng thái APPROVED. Không cộng trung bình confidence OCR, cosine similarity và score phát hiện ảnh AI thành một “độ tin cậy danh tính”.

Hợp đồng đích (chưa phải API đã triển khai):

| Nhóm | Trường tối thiểu |
|---|---|
| Hồ sơ | attemptId, subjectType, status, version, evidenceRevision, processingStage, nextActions |
| Bằng chứng | evidenceId, loại FRONT/BACK/VIDEO, checksum, thời điểm nhận, định dạng; không trả khóa lưu trữ nội bộ |
| Lượt chạy | runId, evidenceRevision, startedAt, completedAt, status, errorCode, traceId |
| Check | checkId, status, reasonCodes, phạm vi, mode, requiredFor, methodVersion, modelVersion nếu có |
| Điểm đo | metricName, value, range, direction, threshold nếu có, thresholdVersion, calibrationStatus |
| Trường OCR | rawValue, normalizedValue, source, confidence nếu có, needsReview, các ứng viên/mâu thuẫn |
| Chính sách | policyVersion, routingDecision, reasons, missingRequirements |
| Quyết định | actorType, actorId, outcome, reasonCode, note, decidedAt, evidenceRevision, policyVersion |

Mỗi check chỉ tham chiếu bằng chứng mà nó đã thực sự chạy. OCR/QR gốc giữ nguyên; giá trị Admin sửa lưu riêng cùng lý do và provenance. Không ghi toàn bộ dữ liệu giấy tờ vào log ứng dụng thông thường.

## Danh mục check và trạng thái

Check khởi đầu: IMAGE_QUALITY, OCR_EXTRACTION, QR_READABILITY, OCR_QR_CONSISTENCY, PRINTED_DATE_CHECK, IDENTITY_DATA_CONSISTENCY, FACE_MATCH, MOTION_CHALLENGE.

Check mở rộng: IMAGE_MANIPULATION, AI_GENERATION, RECAPTURE_DETECTION, PRESENTATION_ATTACK_CHECK, ISSUER_LOOKUP nếu sau này thực sự có tích hợp.

Trạng thái chuẩn: NOT_RUN, NOT_SUPPORTED, NOT_APPLICABLE, PASS, FLAGGED, INCONCLUSIVE, ERROR. Mã QR chi tiết: DECODED, NOT_DETECTED, UNREADABLE, DISABLED; khai báo không QR của người dùng lưu riêng và có trạng thái Admin xác nhận.

Mã lạ từ phiên bản dịch vụ mới: ghi nhận là kết quả chưa hỗ trợ, không mặc định PASS và không tự duyệt. Giữ trace/version để điều tra. Mọi trường hợp adapter không ánh xạ được phải có kiểm thử.

## Diễn giải điểm

- Confidence OCR: mức của kết quả nhận dạng theo phương pháp hiện tại, không phải xác suất giấy tờ thật.
- Cosine similarity khuôn mặt: độ tương đồng embedding, không chuyển thành phần trăm xác minh đúng.
- Score ảnh AI/chỉnh sửa: chỉ dùng theo model và tập kiểm định tương ứng; chưa kiểm định thì ghi UNVALIDATED và không tự kết luận thật/giả.
- Null/chưa chạy khác điểm 0. Chưa có threshold khác đạt threshold. Thay model cần đối chiếu lại chính sách, không dùng ngầm ngưỡng model cũ.

MVP người dùng thấy trạng thái, lý do và hướng xử lý. Admin thấy thêm điểm/nguồn/ngưỡng và giới hạn kiểm tra. Nếu sau này hiện điểm cho user phải ghi đúng tên và phạm vi, không dùng nhãn “92% là bạn”.

## UI người nộp

Các bước: Chuẩn bị → Phân tích → Kiểm tra thông tin → Gửi xét duyệt → Nhận kết quả/bổ sung. Trạng thái chờ phải có thời điểm gửi và cách cập nhật. Chỉ hứa thời gian xử lý khi có quy trình vận hành thực tế.

| Trường hợp | Thông báo mẫu | Hành động |
|---|---|---|
| Mẫu cũ không QR | Giấy tờ không có QR vẫn có thể gửi kiểm tra thủ công. | Gửi xét duyệt |
| QR chưa đọc được | Vùng mã chưa rõ. Bạn có thể chụp lại hoặc gửi kiểm tra thủ công. | Chụp lại / Gửi xét duyệt |
| OCR ngày hết hạn chưa rõ | Chưa đọc rõ ngày hết hạn. Vui lòng kiểm tra ảnh hoặc chụp lại. | Xem ảnh / Chụp lại |
| Video chưa đủ động tác | Chưa ghi nhận đủ bước quay mặt theo hướng dẫn. | Quay lại |
| Điểm đối chiếu chưa chắc chắn | Hệ thống chưa đủ cơ sở đối chiếu tự động. Hồ sơ cần được xem xét. | Gửi xét duyệt |
| Dịch vụ lỗi | Chưa thể hoàn tất phân tích. Hồ sơ của bạn chưa bị từ chối. | Thử lại / Theo dõi trạng thái |
| Admin yêu cầu bổ sung | Hiển thị lý do và đúng ảnh/video/trường cần thay | Bổ sung hồ sơ |
| Từ chối | Hiển thị lý do có thể hiểu và cách yêu cầu xem xét lại | Xem lý do / Yêu cầu hỗ trợ theo luồng đã có |

Không dùng màu xanh cho check chưa đạt. Dùng chữ và biểu tượng ngoài màu; có trạng thái loading, lỗi, rỗng, bàn phím và focus hợp lý. Không hiện nút khi chưa có endpoint/quy trình tiếp nhận thực tế. Không tiết lộ chi tiết kỹ thuật chống giả mạo có thể hỗ trợ né kiểm tra; Admin vẫn cần đủ bằng chứng để giải thích quyết định.

## UI Admin

Ảnh/video riêng tư đặt cạnh dữ liệu OCR, highlight trường cần review và so sánh OCR–QR. Hiển thị rõ check chưa hỗ trợ, điểm chưa kiểm định và revision đang xem. Mỗi quyết định gắn revision; hồ sơ đổi thì yêu cầu tải lại trước khi duyệt.

Hành động: chấp nhận, yêu cầu bổ sung, từ chối, mở lại theo quyền. Không có checkbox “xác nhận giấy tờ chắc chắn thật”. Chấp nhận phải là kiểm tra theo checklist nội bộ, không thay thế tra cứu nguồn cấp chưa triển khai.

## Quyền riêng tư và lưu trữ

Ảnh/video không có URL public; endpoint kiểm tra quyền từng hồ sơ, URL tạm nếu dùng phải hết hạn. Mask thông tin nhạy cảm ở danh sách, không đưa file vào Git/seed/log. Audit truy cập và quyết định, giới hạn tốc độ/dung lượng/lượt xử lý.

Trước khi vận hành ngoài demo, nhóm phải cấu hình thời gian lưu bằng chứng theo loại hồ sơ, cơ chế dọn sau hạn, trường hợp giữ phục vụ khiếu nại và quyền truy cập. Chưa chốt số ngày trong đặc tả này; không giả định lưu vô thời hạn hoặc tự xóa ngay khi bị từ chối. Mọi export phục vụ demo phải dùng dữ liệu giả hoặc được phép sử dụng.

## Kết quả dành cho người dùng và thanh phần trăm

Thanh % mang tên **Mức hoàn thiện thông tin**, là số trường bắt buộc đọc được và đúng định dạng, không có cờ cần đối chiếu, chia tổng trường bắt buộc theo chính sách backend. Không phải confidence trung bình, độ chính xác AI, mức thật của giấy tờ hoặc tiến độ đang chạy. Dữ liệu không có đủ căn cứ không được tự dựng 0%/100%; hồ sơ cũ chưa lưu phép tính hiện thông báo chưa có chi tiết.

Chính sách hiển thị `document-completeness/v1`:

- CCCD: số giấy tờ, họ tên, ngày sinh, ngày hết hạn.
- GPLX: số giấy tờ, họ tên, ngày sinh, hạng, ngày cấp, ngày hết hạn. Giá trị UNLIMITED chỉ đáp ứng việc đọc trường hạn của GPLX; eligibility của gói thuê vẫn quyết định riêng.
- Trường bắt buộc có trạng thái READ/MISSING/INVALID/REVIEW. Chỉ READ được tính vào tử số. Chưa đọc được khác điểm 0 của model; cờ mâu thuẫn/cần đối chiếu không được biến thành READ. Khi upstream chỉ nêu còn trường bắt buộc cần review mà không xác định được trường nào, backend bảo thủ đánh dấu REVIEW thay vì suy ra tất cả đã ổn.
- Địa chỉ/giới tính/ngày cấp CCCD không nằm trong mẫu số hiển thị v1, không tự chặn chỉ vì thiếu trường tùy chọn. Đây là chính sách hiển thị dữ liệu, không thay điều kiện xét duyệt hiện tại.
- Không tạo ngưỡng confidence mới từ frontend. Backend tính một lần và trả policyVersion, completedCount, totalCount, percentage và tình trạng từng trường; UI chỉ trình bày.

100% chỉ có nghĩa đủ thông tin theo phép tính này. Một check bất thường vẫn hiển thị và xử lý độc lập, không bị điểm cao bù trừ. Chính sách Admin duyệt vẫn giữ nguyên; thanh không mở quyền thuê hoặc tự chuyển APPROVED.

### Một kết quả chính và hành động thật

| Tình huống | Tiêu đề chính | Nội dung và hành động |
|---|---|---|
| Dữ liệu hoàn thiện | Thông tin giấy tờ đã được đọc | Thanh + x/y mục; tiếp tục bước video hoặc xác nhận gửi nếu backend cho phép |
| Còn trường chưa rõ | Thông tin giấy tờ cần kiểm tra thêm | Chỉ rõ mục cần xử lý; chụp lại hoặc gửi thủ công nếu tuyến backend thực sự hỗ trợ |
| Ảnh không sử dụng được | Cần chụp lại giấy tờ | Nguyên nhân và nút quay về bước thay ảnh |
| Lỗi dịch vụ | Chưa thể hoàn tất kiểm tra | Thử lại/cập nhật; không hiển thị như bị từ chối |
| Tín hiệu ảnh bị gắn cờ | Hồ sơ cần được kiểm tra bổ sung | Nêu tín hiệu cần xem xét, không khẳng định giả; Admin xem chi tiết |
| SUBMITTED | Đã nhận hồ sơ — đang chờ xét duyệt | Ẩn thanh % ở user, chỉ theo dõi trạng thái; không giả lập tiến độ duyệt |
| APPROVED/REJECTED/NEEDS_CHANGES | Quyết định hồ sơ | Hiển thị kết quả/ghi chú cụ thể riêng; không lấy thanh % làm quyết định |

Nút gắn với thao tác có thật. CCCD thiếu dữ liệu hiện chưa có tuyến manual correction hoàn chỉnh: không hiện nút “Gửi kiểm tra thủ công” giả. GPLX READY_TO_SUBMIT có nút xác nhận gửi thật ở form GPLX, kể cả chưa đọc đủ thông tin.

### Phân tầng thông tin

User mặc định thấy: tiêu đề, thanh và x/y mục nếu còn ở bước chuẩn bị, checklist bắt buộc, tối đa các cảnh báo hữu ích, hướng dẫn bước tiếp theo. Dữ liệu đọc được đặt trong phần mở rộng. Không hiện bảng nguồn kỹ thuật, schema, version hay score ở màn hình chính. Ẩn lịch sử rỗng với user.

Admin thấy cùng số đo để hiểu nguồn nhưng có thêm bảng source/confidence, kết quả check, policy/version và ảnh gốc. Snapshot cũ không có completeness không được tính lại theo một chính sách mới mà không ghi provenance.

### Bất thường, tiếp nhận và từ chối

Phân biệt “tiếp nhận hồ sơ để Admin xét duyệt” với “chấp nhận hồ sơ để mở quyền”. Chất lượng ảnh không đạt yêu cầu bắt buộc có thể yêu cầu chụp lại. Tín hiệu nghi chỉnh sửa/ảnh AI chưa được kiểm định chỉ chuyển kiểm tra thủ công; không tự kết luận gian lận. Từ chối cuối vẫn là quyết định có lý do của Admin trong MVP. Không giả lập check ảnh AI/liveness chưa được triển khai.

### Tài liệu tham khảo cho thiết kế

- [Veriff — Verification session decisions](https://devdocs.veriff.com/docs/verification-session-decisions): phân biệt quyết định cuối, yêu cầu gửi lại và lý do.
- [Trusting Social — API Call Flow](https://ekyc.trustingsocial.com/api-reference/api-flow/): tách quality, tampering, OCR, face matching và liveness, hướng dẫn thử lại theo bước.
- [Persona — Inquiries](https://docs.withpersona.com/api-reference/inquiries): phân biệt hoàn thành thu thập/kiểm tra với quyết định review/approved/declined.

Thanh hoàn thiện là thiết kế của EcoMotion; không tuyên bố các nhà cung cấp trên dùng cùng thanh hoặc công thức. Không sao chép ngưỡng hoặc khả năng kiểm định của họ sang model local.

## Tiến trình xử lý ảnh/video và xem lại bằng chứng

Thanh khi xử lý và thanh hoàn thiện dữ liệu là hai số đo khác nhau. Upload có thể hiển thị % byte từ trình duyệt khi biết tổng dung lượng. Sau upload dùng chỉ báo chờ không định lượng; không tăng dần phần trăm giả để mô tả AI. Trạng thái backend DOCUMENT/VIDEO xác nhận nhóm tác vụ đang xử lý, không khẳng định đã đến bước OCR, QR hoặc liveness bên trong model.

UI có ba mốc Gửi ảnh/video → Phân tích/đối chiếu → Nhận kết quả, thời gian đã chờ và lần cuối kiểm tra backend. Polling 3 giây, không chồng request, timeout request trạng thái 10 giây; dừng/abort khi rời trang. Khi chờ từ 45 giây, nhắc vẫn đang kiểm tra; mất kết nối khác lỗi AI và không tự kết luận bị treo. Deadline/timeout do backend quản lý, không chuyển APPROVED vì hết thời gian. Có thể khôi phục hiển thị sau reload khi trạng thái còn PROCESSING.

Sau lượt đọc, ảnh hai mặt đang lưu được tải lại qua endpoint riêng tư và hiển thị chỉ xem; video tải theo yêu cầu. SUBMITTED vẫn xem được bộ bằng chứng đang chờ Admin. Người dùng mở lịch sử từng mốc gửi/xét duyệt để xem đúng bộ ảnh/video lúc đó, và xem các phiên trước theo trang để phân biệt bản bị từ chối với bản mới. Ảnh lịch sử không lấy từ đường dẫn hiện tại đã bị thay thế.

Ảnh/video dùng blob URL trong trình duyệt, không chuyển thành public URL; thu hồi khi unmount/đổi bản. Ảnh lớn mở bằng blob URL, không tiết lộ storage key. Chỉ tải bằng chứng của mốc lịch sử khi người dùng mở mốc đó. Token kích hoạt vẫn theo quyền PENDING; tài khoản đã kích hoạt xem qua phiên đăng nhập.


## Tiến trình thực tế từ hai dịch vụ AI

CCCD dùng DocumentAI **chính thức** tại `D:/PYTHON/DocumentAI-v0.2.0/DocumentAI`;
video dùng `IdentityVerification/`. Backend tạo job qua `POST /v1/documents/jobs`
hoặc `POST /v1/verifications/jobs`, kiểm tra mỗi giây bằng API key phía server.
API đồng bộ cũ giữ nguyên. Không tự gửi lại qua API cũ khi job lỗi: tránh chạy hai lượt.

`GET /api/identity/attempts/{id}/progress` (và route activation tương ứng) trả thêm
`aiProgress: {stage, side, updatedAt, lastObservedAt, sampledFrames, videoSeconds, steps}` khi có báo cáo thực tế. `updatedAt` là lúc backend đầu tiên quan sát bước/side hiện tại; polling cùng bước không làm mới thời điểm này, không giả thành nhịp hoạt động của model. Kiểm tra chủ hồ sơ
vẫn áp dụng. Không đưa job ID, đường dẫn nội bộ, API key hoặc dữ liệu OCR vào progress UI.
DocumentAI có các bước kiểm tra ảnh, định vị, đọc chữ, QR, đối chiếu và tổng hợp;
video có chuẩn bị model, khuôn mặt tham chiếu, đọc video, phân tích, kiểm tra động tác, tổng hợp.
Các bước có thể bị bỏ qua hoặc lặp theo nhánh pipeline, không quy đổi thành phần trăm.

Phần trăm chỉ đo số byte upload khi biết tổng dung lượng. Thanh mức hoàn thiện thông tin
là chỉ số khác. Bước AI không chứng minh giấy tờ thật, người thật hoặc xác suất nhận dạng.
Tiến trình chi tiết ở RAM, gắn với operation ID; xóa khi kết thúc. Backend khởi động lại
hoặc chạy nhiều instance chưa có kho progress chung thì UI có thể chỉ thấy giai đoạn tổng quát.
Job AI giữ kết quả tối đa 10 phút, giới hạn 20 mục, chỉ một inference; video tạm được dọn sau xử lý.
Hết thời gian chờ backend không có nghĩa là AI đã hủy job; không tự động retry.

GPLX dùng cùng DocumentAI jobs. POST chuẩn bị hồ sơ mang `X-Processing-ID` (UUID), và GET `/api/renter/driving-license/submissions/processing/{id}` trả bước thực tế theo đúng người gửi; GET `.../processing/current` giúp tải lại trang tiếp tục theo dõi. Phản hồi POST vẫn giữ tương thích, không thay cơ chế Admin duyệt.
Nâng cấp này không thêm phát hiện ảnh giả/chỉnh sửa và không thay đổi cơ chế Admin duyệt.


Trong lúc xử lý, nhãn bước AI là tiêu đề chính. Video hiển thị số khung hình mẫu đã phân tích,
không giả thành tỷ lệ hoàn tất. `lastObservedAt` là lần backend nhận phản hồi AI gần nhất;
khác `updatedAt` là lúc quan sát bước hiện tại. Kết nối còn phản hồi không có nghĩa model đã tiến triển.
Tải lại trang khi attempt PROCESSING vẫn hiện đang xử lý trước khi polling trả về.
Lỗi xử lý dừng animation và không đồng thời hiện thông báo “vẫn đang chờ lâu”.


### Thanh tiến trình bám sự kiện pipeline

API job của hai AI trả `events` giới hạn 80 sự kiện, BE chỉ chuyển `stage`, `side`, `elapsedMs` vào `aiProgress.steps`.
UI hiển thị các bước AI đã báo cáo và bước hiện tại; không tự chạy bộ đếm bước theo thời gian.
DocumentAI báo riêng mặt trước/mặt sau ở bước định vị và đọc thông tin. Nhãn “đã chuyển qua” không phải kết luận kiểm tra đã PASS.
BE lấy trạng thái AI mỗi 1 giây; FE lấy trạng thái BE mỗi 1,5 giây. Lịch sử giúp không bỏ mất bước nhanh giữa hai lần lấy trạng thái.
Chỉ upload có % từ số byte thực. Trong lúc suy luận, thanh chờ không mang một độ dài giả thành phần trăm;
UI hiển thị tên bước và số khung hình video đã phân tích khi có dữ liệu. Mất kết nối giữ lại thông tin gần nhất, không khẳng định AI vẫn đang tiến triển.


### Đối chiếu sau thao tác thực tế

CCCD và video có tab nội dung riêng. Sau xử lý, UI giữ riêng dấu vết pipeline CCCD/video trong phiên trình duyệt;
chỉ lưu metadata, không lưu ảnh hoặc thông tin CCCD vào sessionStorage. Thay CCCD làm mất hiệu lực dấu vết video cũ.
Lỗi mạng không được hiển thị như AI đã từ chối hồ sơ. Hai kết quả video luôn tách riêng: động tác và dữ liệu khuôn mặt để đối chiếu.
Không thấy mặt, không có chuỗi một mặt rõ, không đọc được góc đầu và thiếu động tác có lý do riêng.
Chưa có ngưỡng đối chiếu được hiệu chuẩn để tự khẳng định cùng/khác người; Admin có lý do FACE_MISMATCH khi đối chiếu thủ công.

Admin duyệt GPLX xem ngay hồ sơ danh tính đã duyệt cạnh họ tên/ngày sinh GPLX đang xét. API applicant chỉ dành cho Admin.
Ảnh GPLX có nút xem/ẩn. Không có checkbox cho phép tùy ý chuyển giấy tờ thành không thời hạn; thông tin hạn vẫn được giữ đúng dữ liệu,
Admin có thể nhập ngày hết hạn nếu OCR sai. Phản hồi yêu cầu bổ sung màu vàng, từ chối màu đỏ, kèm ghi chú và thời điểm.


## Tiến trình lưu bền và đối chiếu khuôn mặt

Backend lưu từng lượt DOCUMENT, VIDEO và LICENSE trong `verification_pipeline_run`, theo chủ hồ sơ và phiên bản giấy tờ. Sự kiện chỉ chứa bước, thời gian và số lượng xử lý; không chứa ảnh, OCR hoặc embedding. Lượt đã kết thúc không bị ghi đè. Khi mất trạng thái RAM, UI đọc bước cuối đã lưu; đây không phải bằng chứng AI vẫn đang chạy. Hai pipeline CCCD/video được giữ riêng sau kết quả.

Đối chiếu khuôn mặt mặc định `NOT_CALIBRATED`: không suy cosine thành phần trăm đúng danh tính. Một người chưa đủ dữ liệu để chọn ngưỡng. Chính sách thử nghiệm chỉ bật khi có cả hai ngưỡng, phiên bản và hash model. Trạng thái gồm NO_USABLE_FACE, INSUFFICIENT_EVIDENCE, NOT_CALIBRATED, LOW_SIMILARITY, INCONCLUSIVE, LIKELY_MATCH; mọi trạng thái vẫn cần quyết định Admin. LOW_SIMILARITY là tín hiệu đối chiếu, không tự kết luận gian lận.

Admin duyệt GPLX có thể mở ảnh CCCD đã được duyệt để so sánh, qua endpoint riêng kiểm tra quyền Admin. Tài khoản seed không có ảnh gốc sẽ báo không có bằng chứng, không tạo ảnh giả.


## Ngưỡng demo qua env

Local đang dùng `IDENTITY_FACE_MATCH_HIGH_THRESHOLD=0.5`, `IDENTITY_FACE_MATCH_LOW_THRESHOLD=0`, `IDENTITY_FACE_MATCH_MIN_FRAMES=3`, `IDENTITY_FACE_MATCH_CALIBRATION_ID=demo-single-subject-v1`, cùng `IDENTITY_FACE_MATCH_MODEL_SHA256` đúng model local. Đây là lựa chọn demo, không phải kết quả hiệu chuẩn. Khoảng [0;0.5) cần xem thủ công, từ 0.5 đạt ngưỡng thử nghiệm; không tự phê duyệt. Cosine 0.5 không được gọi là 80% đúng danh tính. Khởi động lại Identity để áp dụng, quay video mới để có kết quả theo chính sách mới; hồ sơ cũ giữ nguyên provenance. Để tắt chính sách demo, bỏ cả hai biến threshold cùng lúc.

GPLX được Admin duyệt sẽ mở sẵn thông tin đã xác nhận, vẫn có hai ảnh xem/ẩn; form thay thế được thu gọn. Lỗi lưu bước cuối của pipeline không được biến hồ sơ đã lưu thành thất bại. Log BE có operation ID, mã lỗi, bước cuối và loại ngoại lệ để đối chiếu; không ghi ảnh/OCR/token. HTTP 202/200 từ AI chưa đủ chứng minh BE đã lưu thành công.
