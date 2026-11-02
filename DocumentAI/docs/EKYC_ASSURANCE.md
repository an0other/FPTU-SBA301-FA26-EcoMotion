# eKYC: đọc được không đồng nghĩa giấy tờ thật

## Thông điệp cho demo

DocumentAI hiện là **bộ đọc và đối chiếu giấy tờ**, chưa phải dịch vụ chứng minh tính thật của giấy tờ. OCR đọc được tên/số/hạng GPLX chỉ nói rằng ảnh có nội dung máy đọc được. QR đọc được hoặc khớp OCR cũng không tự chứng minh QR do cơ quan cấp ký, giấy tờ còn hiệu lực, ảnh chưa bị sửa, hay người tải ảnh là chủ giấy tờ.

Vì vậy:

- `READ_SUCCESS` là trạng thái đọc dữ liệu, không phải eKYC pass.
- `NO_ANOMALY_DETECTED` nghĩa là một analyzer cụ thể không phát hiện dấu hiệu trong phạm vi của nó; không đồng nghĩa ảnh/giấy tờ là thật.
- `INCONCLUSIVE`, `NOT_IMPLEMENTED`, `NOT_VERIFIED` và lỗi kỹ thuật không được đổi thành pass hoặc fake.
- Quyết định nghiệp vụ hiện vẫn cần người có thẩm quyền duyệt.

## Các bằng chứng cần thiết để tăng mức đảm bảo

| Chiều kiểm tra | Câu hỏi cần trả lời | Bằng chứng phù hợp | Trạng thái hiện tại |
|---|---|---|---|
| Đọc và nhất quán dữ liệu | Có đọc được các trường cần thiết; dữ liệu các nguồn có mâu thuẫn không? | OCR, QR, rule đối chiếu có lưu provenance | Có một phạm vi GPLX/CCCD pilot; không chứng minh thật |
| Toàn vẹn giấy tờ | Có dấu hiệu sửa nội dung/ghép ảnh không? | Model pháp chứng ảnh được đánh giá trên dữ liệu đại diện; kiểm tra bảo mật vật lý nếu có | Chưa có model đã kiểm định |
| Nguồn gốc/cơ quan cấp | Giấy tờ có được cấp bởi nguồn được tin cậy không? | Chữ ký số/chip hoặc tra cứu API/nguồn chính thức có xác thực | Chưa triển khai |
| Hiệu lực hiện tại | Giấy tờ có hết hạn, bị thu hồi hoặc không đủ điều kiện không? | Quy tắc ngày in kết hợp tra cứu trạng thái từ nguồn có thẩm quyền | Report có thể nêu ngày hết hạn in trên giấy; chưa tra cứu thu hồi/nguồn cấp |
| Chống ảnh phát lại | Ảnh là bản chụp trực tiếp hay ảnh màn hình/in/chụp lại? | Capture challenge, liveness hoặc detector đã kiểm định theo thiết bị/điều kiện thực | Chưa có detector đã kiểm định |
| Liên kết người và giấy tờ | Người đang xác minh có phải chủ thể trên giấy tờ không? | So khớp chân dung và liveness với consent, ngưỡng đã đánh giá | Không thuộc image analyzer GPLX hiện tại |
| Liên kết hai mặt | Hai ảnh có thực sự thuộc cùng một giấy tờ vật lý không? | Bằng chứng bảo mật/thu thập liên kết, ngoài việc hai mặt có cùng loại/số đọc được | Chưa xác minh |

“Giấy tờ thật” không nên được định nghĩa là một điểm số AI duy nhất. Với eKYC, cần nói rõ **mức đảm bảo** và **bằng chứng**: định danh được đọc nhất quán, nguồn cấp đáng tin cậy, trạng thái hiệu lực được kiểm tra, tính toàn vẹn/cách thu thập được đánh giá, và chủ thể được liên kết với giấy tờ. Từng thủ tục có thể yêu cầu tập bằng chứng khác nhau; phải định nghĩa policy theo loại giấy tờ và nghiệp vụ, không suy rộng từ một layout mẫu.

## Ranh giới module trong source

- `reader.service` / `reader.staged`: nhận ảnh, chất lượng, định vị, hướng ảnh, OCR/QR và đối chiếu.
- `reader.image_analysis`: adapter cho signal **từ ảnh** như chỉnh sửa số hoặc screen/print recapture. Adapter không nhận OCR/QR hay số giấy.
- `reader.document_authenticity`: tổng hợp trạng thái bằng chứng thành các chiều eKYC có tên rõ ràng. Module này không đưa ra quyết định thật/giả.
- `reader.assessment` và `reader.pipeline_report`: completeness, ngày in, trạng thái duyệt thủ công và response API.
- API nghiệp vụ/BE: xác thực người dùng, lưu hồ sơ cần thiết, kiểm tra nguồn đáng tin cậy nếu có, quy định người duyệt và quyết định cuối.

Muốn thêm detector ảnh, triển khai `ImageAnalyzer` độc lập, khai báo `id`, `version`, `capabilities`, `implementation`, `scope`, rồi đăng ký qua `ImageAnalysisRunner`. Muốn kiểm tra issuer, hiệu lực hoặc chủ giấy tờ thì không viết chúng thành detector ảnh; thêm adapter ở đúng ranh giới domain và định nghĩa input/output riêng. Bắt đầu ở chế độ quan sát, đo false positive/false negative trên tập kiểm tra tách theo giấy tờ/người và thiết bị, rồi mới cân nhắc policy nghiệp vụ.

Hiện `assessment.documentAuthenticity.status` luôn `NOT_ESTABLISHED` và `verified` luôn `false`. `dimensions.printedExpiryDate` có thể phản ánh ngày in là đã qua hạn hay chưa, nhưng `dimensions.currentValidity` vẫn `NOT_IMPLEMENTED` nếu chưa tra cứu nguồn có thẩm quyền. Đây là trạng thái trung thực để demo: source đã có điểm mở rộng, nhưng chưa tuyên bố tự chủ được toàn bộ xác minh tính thật cho đến khi có các adapter, dữ liệu kiểm định và quy trình duyệt tương ứng.
