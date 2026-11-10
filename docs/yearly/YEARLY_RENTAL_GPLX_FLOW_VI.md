> Chính sách demo đã mở rộng: B, B1, B2 được hỗ trợ, bỏ mốc ngày cấp 2025. Chính sách theo commit `11ef8eb`; điều kiện tuổi, hạn GPLX và số chỗ vẫn được kiểm tra.

> **Seed/demo cập nhật 10/11/2026:** cả ba renter new/monthly/happy-end đều có B2 demo, không cần upload GPLX thật để đi tuyến yearly. Dùng 3 renter + Provider + Admin; EM-Y2-CANCEL dùng chung renter happy-end, khác xe/khoảng thuê. Các bảng A1, EM-SEED-YEARLY-* và mô tả seed cũ bên dưới là lịch sử; lấy trạng thái hiện hành từ [kịch bản demo](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md).

> **Bộ demo hiện hành:** 3 renter + 1 provider, ghép các bước trên cùng booking; xem [danh sách đăng nhập và workflow](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md). Các mô tả seed lịch sử bên dưới không dùng cho demo mới.

> **Cập nhật 07/11/2026:** Phần seed/flow thuê năm trong tài liệu cũ này đã được thay bằng [hướng dẫn demo gói năm](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md). Dùng ba renter theo giai đoạn và booking chính EM-Y2-MONTHLY/EM-Y2-HAPPY-END, không dùng các mã EM-SEED-YEARLY-* để demo flow mới.

# GPLX và quy trình thuê xe tự lái theo năm

## Căn cứ pháp lý và giới hạn

Chính sách bản demo được tham khảo từ **Luật Trật tự, an toàn giao thông đường bộ số 36/2024/QH15**:

- Điều 57 quy định các hạng giấy phép lái xe hiện hành. Hạng B được lái ô tô chở người đến 8 chỗ, không kể chỗ của người lái; luật còn quy định điều kiện về khối lượng xe.
- Điều 88 quy định ngày có hiệu lực chung là 01/01/2025.
- Điều 89 quy định chuyển tiếp đối với giấy phép lái xe đã cấp theo luật trước đây. Giấy phép cũ tiếp tục được sử dụng theo thời hạn ghi trên giấy phép, cùng các quy định chuyển tiếp và đổi hạng có liên quan.
- Cổng văn bản pháp luật chính thức của Chính phủ: [vanban.chinhphu.vn](https://vanban.chinhphu.vn/?pageid=27160) (tìm `36/2024/QH15`).
- Bản tham khảo dễ đọc: [Luật số 36/2024/QH15](https://thuvienphapluat.vn/van-ban/Giao-thong-Van-tai/Luat-Trat-tu-an-toan-giao-thong-duong-bo-2024-so-36-2024-QH15-615463.aspx).

Đây là chính sách phục vụ đồ án, không phải tư vấn pháp lý hay kết luận đầy đủ về điều kiện lái xe. Mô hình xe hiện lưu số chỗ nhưng chưa có khối lượng toàn bộ và đầy đủ thông tin phân loại xe, nên hệ thống chưa thể kiểm tra mọi điều kiện theo luật. Bản demo tự động hỗ trợ GPLX B/B1/B2 còn hiệu lực theo commit `11ef8eb`, không áp dụng mốc ngày cấp 2025 (ngày cấp không quyết định eligibility). Phải có ngày hết hạn hữu hạn, không trước ngày hiện tại tại Việt Nam và bao phủ hết thời hạn thuê; người thuê phải đủ 18 tuổi khi bắt đầu thuê, đã xác minh OCR/QR và xe tối đa 9 chỗ kể cả người lái. Đây là quy tắc sản phẩm demo, không phải kết luận pháp lý cho mọi GPLX B1/B2.

## Nghiệp vụ đã triển khai

### Tiếp nhận GPLX

1. Người thuê tải ảnh mặt trước và mặt sau GPLX định dạng JPG/PNG (tối đa 8 MB mỗi ảnh). Giao diện cho phép chọn ảnh trong thư viện; không bắt buộc phải chụp trực tiếp bằng camera.
2. Spring gọi dịch vụ DocumentAI được triển khai riêng qua địa chỉ cấu hình. `DOCUMENT_AI_API_KEY` được cung cấp trong môi trường chạy backend; không gửi khóa API xuống trình duyệt.
3. Spring chỉ chấp nhận kết quả khi loại giấy tờ và trạng thái đọc hợp lệ, các trường chính đã được đọc đầy đủ, và kết quả đối chiếu OCR/QR khớp. Hai ảnh được lưu bằng kho lưu trữ riêng tư; chỉ các endpoint đã xác thực của người thuê mới cho phép xem ảnh.
4. Kết quả được dùng để điền số GPLX, họ tên, ngày sinh, hạng, ngày cấp và ngày hết hạn. Số GPLX được lưu theo cơ chế mã hóa chuỗi đang có trong hệ thống.
5. `licenseVerified` chỉ biểu thị lần đọc OCR/QR vượt qua các kiểm tra nêu trên. Trạng thái này **không** có nghĩa là đã xác minh với cơ quan nhà nước, chứng minh GPLX thật, xác nhận ảnh không bị chỉnh sửa hoặc tạo bằng AI, chứng minh quyền sở hữu, kiểm tra tình trạng bị thu hồi hay đối chiếu với CCCD. Các kiểm tra đó chưa được triển khai.

### Đặt thuê năm

- Chỉ chấp nhận một gói năm đang hoạt động và đúng một kỳ 365 ngày. Thời điểm nhận xe phải cách lúc gửi yêu cầu ít nhất 3 giờ.
- Người thuê phải đủ 18 tuổi tại ngày nhận xe; GPLX phải là hạng B hiện hành theo chính sách demo và còn hạn đến hết ngày kết thúc kỳ thuê.
- Bản demo hiện hỗ trợ xe có tối đa 9 chỗ tính cả người lái. Do hệ thống chưa lưu khối lượng toàn bộ của xe, chỉ kiểm tra số chỗ không thể thay thế việc xác định đầy đủ điều kiện theo luật.
- Người thuê là người lái; không phân công tài xế của nhà cung cấp và không kiểm tra lịch rảnh tài xế. Số hành khách được giới hạn theo số chỗ còn lại sau khi trừ chỗ người lái.
- Nhà cung cấp có 24 giờ để chấp nhận hoặc từ chối yêu cầu. Sau khi được chấp nhận, xe được giữ tối đa 24 giờ để người thuê tải lên bản PDF hợp đồng đã ký.
- Khi tải hợp đồng, hệ thống kiểm tra lại điều kiện GPLX. Tải hợp đồng hợp lệ sẽ tạo yêu cầu thanh toán khoản trả trước; không thể thanh toán trước khi hợp đồng được lưu. Điều kiện GPLX tiếp tục được kiểm tra khi thanh toán.
- Thanh toán khoản trả trước thành công sẽ xác nhận lịch xe. Lịch sử giao dịch hiển thị loại giao dịch `UPFRONT_PAY`; yêu cầu thanh toán ban đầu sử dụng nhóm loại `DEPOSIT` hiện có.
- Với đơn năm đang chạy trong bộ dữ liệu demo, trang chi tiết đơn hiển thị tổng tiền thuê, số đã thanh toán, số còn lại và các giao dịch đã ghi nhận. Yêu cầu `MONTHLY_INSTALLMENT` hiện tại có thể thanh toán qua VNPAY Sandbox hoặc chuyển khoản để nhà cung cấp xác nhận; thanh toán kỳ tháng không làm thay đổi trạng thái `IN_PROGRESS` của đơn.
- Đây chưa phải cơ chế thu tiền định kỳ: bộ seed chỉ tạo một yêu cầu kỳ 4/12, chưa tự sinh yêu cầu các tháng tiếp theo. Không diễn giải màn hình này như lịch tự động thu tiền hoặc cam kết thu nợ ngoài yêu cầu thanh toán đang hiển thị.
- Chính sách hủy/hoàn tiền thuê năm trong bản demo được giản lược, chưa mô hình hóa đầy đủ luật, điều khoản cụ thể trong hợp đồng hoặc thỏa thuận riêng. Không sử dụng chính sách này như quy định cho sản phẩm thực tế.

## Hướng dẫn demo từ đầu đến cuối

### Chuẩn bị

1. Chạy DocumentAI trên máy chủ có thể truy cập riêng tư; cấu hình backend Spring với `DOCUMENT_AI_BASE_URL` và `DOCUMENT_AI_API_KEY`. Hệ thống không có địa chỉ localhost dự phòng; nếu thiếu một trong hai giá trị, thao tác đọc GPLX sẽ báo lỗi rõ ràng. Không đặt khóa trong biến môi trường frontend hoặc đưa khóa vào mã nguồn.
2. Chạy backend Spring và frontend. Đăng nhập bằng tài khoản người thuê đã có hồ sơ renter.
3. Đảm bảo nhà cung cấp có xe đã được duyệt, gói YEARLY đang hoạt động với kỳ 365 ngày, khoản trả trước lớn hơn 0 và không vượt giá gói, đồng thời đã cấu hình thông tin nhận thanh toán.

### Luồng thành công

1. Mở xe có gói năm và chọn **Thuê theo năm**.
2. Tại mục **GPLX cho gói năm tự lái**, chọn ảnh cả hai mặt và gửi. Kiểm tra dữ liệu OCR, thông báo điều kiện và khả năng xem lại ảnh riêng tư sau khi tải lại trang.
3. Chọn thời điểm nhận xe cách hiện tại ít nhất 3 giờ. Xác nhận hạng cũ/không được hỗ trợ, GPLX hết hạn hoặc hết hạn trước ngày kết thúc kỳ thuê 365 ngày sẽ không cho tiếp tục.
4. Chọn điểm nhận xe trên bản đồ và số hành khách không vượt quá số chỗ còn lại; kiểm tra giá thuê năm, phí nhận/giao xe và khoản trả trước.
5. Gửi yêu cầu. Trạng thái phải là `PENDING`; chưa tạo yêu cầu thanh toán và chưa phân công tài xế.
6. Đăng nhập vai trò nhà cung cấp để chấp nhận yêu cầu. Sau đó quay lại vai trò người thuê và tải hợp đồng PDF đã ký trong thời hạn giữ xe.
7. Xác nhận đơn chuyển sang `AWAITING_DEPOSIT`, hiển thị nút **Thanh toán khoản trả trước** và chỉ sau đó mới mở trang thanh toán. Hoàn tất thanh toán qua cổng demo đã cấu hình.
8. Mở **Lịch sử thanh toán** để xem nhãn giao dịch và bảng chi tiết phí. Trang đánh giá hiện có cho phép đánh giá xe (và tài xế nếu đơn có tài xế) sau khi đơn chuyển sang `COMPLETED`.

### Demo khoản thanh toán tháng của đơn đang chạy

1. Đăng nhập `renter.yearly.monthly@ecomotion.vn` bằng mật khẩu dev `EcoMotion@123`.
2. Mở **Đơn của tôi** → chọn `EM-SEED-YEARLY-ACTIVE`, hoặc truy cập `/renter/bookings/EM-SEED-YEARLY-ACTIVE`.
3. Ở thẻ **Thanh toán và lịch sử kỳ thuê**, kiểm tra ba giao dịch tiền thuê đã ghi nhận và yêu cầu kỳ 4/12 sắp đến hạn.
4. Chọn **Thanh toán kỳ này** → chọn **VNPAY Sandbox** → hoàn tất giao dịch trong sandbox. Có thể chọn VietQR nếu muốn demo xác nhận chuyển khoản từ phía nhà cung cấp.
5. Quay lại chi tiết đơn: giao dịch mới xuất hiện trong lịch sử; đơn vẫn ở `IN_PROGRESS`. Ngày seed được tính tương đối theo ngày chạy: ngày tạo đơn trước ngày bắt đầu thuê đã qua, kỳ 4 đến hạn trong tương lai gần.

### Các trường hợp cần kiểm tra

- Chỉ tải một mặt, dùng tệp không phải JPG/PNG, tệp quá dung lượng, ảnh không đọc được hoặc dữ liệu OCR/QR không khớp: API phải từ chối và không đánh dấu GPLX đủ điều kiện.
- Dùng GPLX hết hạn trước ngày kết thúc thuê, hạng không được hỗ trợ hoặc xe có trên 9 chỗ: yêu cầu thuê năm phải bị chặn ở cả giao diện và backend.
- Thử tạo thanh toán năm trước khi nhà cung cấp chấp nhận và trước khi tải hợp đồng: backend phải từ chối.
- Để hết thời hạn nhà cung cấp phản hồi hoặc thời hạn tải hợp đồng: đơn phải bị hủy và lịch giữ xe phải được giải phóng.
- Thử xem ảnh GPLX riêng tư khi chưa đăng nhập hoặc bằng tài khoản người thuê khác: hệ thống không được cấp quyền truy cập.

## Giới hạn phạm vi đã biết

- Các giai đoạn AI phát hiện ảnh giả mạo/chỉnh sửa không được dùng để duyệt GPLX. OCR/QR khớp không phải là kết luận ảnh thật.
- Chưa kiểm tra tình trạng GPLX với cơ quan nhà nước, tình trạng bị thu hồi hoặc việc GPLX thuộc về người có CCCD của tài khoản.
- Cần có bảng quy đổi hạng GPLX cũ được kiểm chứng trước khi tự động duyệt các hạng cũ.
- Cần bổ sung khối lượng toàn bộ và các thuộc tính pháp lý khác của xe trước khi tuyên bố hệ thống kiểm tra đầy đủ điều kiện theo luật.
- Phạm vi hiện tại gồm gửi yêu cầu, nhà cung cấp chấp nhận, tải hợp đồng và thanh toán khoản trả trước. Quyền đánh giá vẫn phụ thuộc trạng thái `COMPLETED` hiện có; cần thống nhất riêng nghiệp vụ bắt đầu/trả xe tự lái theo năm trước khi có thể biểu diễn vòng đời đó là hoàn tất trong luồng hiện tại vốn thiết kế xoay quanh tài xế.

## Tài khoản và dữ liệu demo gói năm (chỉ profile `dev`)

Các tài khoản dưới đây dùng mật khẩu mặc định `EcoMotion@123`:

| Email | Tình huống |
|---|---|
| `renter.yearly.new@ecomotion.vn` | GPLX mẫu hạng A1 đã đọc nhưng không đủ điều kiện theo chính sách demo hạng B. |
| `renter.yearly.monthly@ecomotion.vn` | Có đơn `EM-SEED-YEARLY-ACTIVE`, đang thuê; đã ghi nhận 3 kỳ tiền thuê tháng và có yêu cầu kỳ 4 sắp đến hạn. Tài khoản này cũng có đơn lịch sử `EM-SEED-YEARLY-HISTORY` đã hoàn tất với 12 giao dịch theo tháng. |
| `renter.yearly.happy-end@ecomotion.vn` | GPLX mẫu hạng B đủ điều kiện và chưa có đơn gói năm để có thể demo luồng gửi yêu cầu mới. |

Ảnh GPLX của các tài khoản là ảnh placeholder do seeder tạo, có đóng dấu “DEMO ONLY”; `licenseVerified` được đặt để dựng tình huống giao diện và **không** phải kết quả gọi DocumentAI hay xác minh giấy tờ thật. Không dùng các tài khoản hoặc dữ liệu này ngoài môi trường dev.

Ba giao dịch tháng và yêu cầu kỳ 4 đang chờ là dữ liệu demo được seed trực tiếp. Yêu cầu kỳ 4 có thể thanh toán qua VNPAY Sandbox hoặc chuyển khoản chờ nhà cung cấp xác nhận; chi tiết đơn hiển thị các giao dịch đã ghi nhận. Hệ thống chưa tự tạo các yêu cầu tháng tiếp theo, chưa thu tiền định kỳ và chưa có lịch trả góp tổng quát cho mọi đơn. Khoản cọc và hoàn cọc được để ngoài seed, chờ chốt nghiệp vụ riêng.
