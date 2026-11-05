# Dữ liệu và đánh giá

V1: GPLX một mẫu trước, CCCD một mẫu sau. Cần ghi rõ nguồn ảnh và điều đã biết.
“Không sửa ảnh” khác “giấy thật đã đối chiếu nguồn phát hành”.

Nhóm ảnh: nguyên bản đủ rõ; mờ/chói/thiếu góc; sai giấy/mặt; hai mặt khác giấy;
chỉnh sửa có kiểm soát kèm mask; nén/resize/chỉnh sáng hợp lệ; chụp lại màn
hình/bản in nếu nghiên cứu khả năng đó.

Nhãn theo tác vụ: loại/mẫu/mặt; vùng giấy/keypoint; nội dung OCR ground truth;
vùng chỉnh sửa; cặp hai mặt và trường liên kết. Đo chất lượng theo tiêu chí có
định nghĩa, không chỉ nhãn cảm tính. Ảnh đã che thông tin có chỉnh sửa phải
được ghi nhận, không dùng làm clean original cho tamper evaluation.

Chia tập theo giấy gốc và người; mọi lần chụp/biến thể của cùng giấy nằm cùng
tập. Chọn threshold trên validation, không tối ưu bằng test. Theo dõi thiết
bị, ánh sáng, mức nén, nguồn ảnh để phân tích domain shift. Bộ nhỏ là pilot,
không tuyên bố accuracy đại diện thị trường.

Đo: OCR exact match theo trường/CER nếu phù hợp, đúng loại/mặt, false positive
và false negative theo kiểu bất thường, độ chính xác đối chiếu, inconclusive
rate, end-to-end coverage, latency và peak RAM/VRAM. Luôn ghi kích thước tập.

Ảnh/nhãn chứa PII để trong data/ (đã ignore), chỉ dùng với quyền cho phép.
Không ghi số giấy/tên trong log hoặc dữ liệu test commit. Không có dataset
hoặc ảnh cá nhân trong ZIP này.
