# Hướng dẫn demo và kiểm tra gói năm

## 1. Một lần chuẩn bị, 4 tài khoản

Khởi động backend bình thường với profile dev và app.demo-seed.enabled=true. Chỉ dùng **3 renter + 1 provider**. Không chọn scenario bằng env, không đổi seed hoặc reset giữa buổi demo.

Seeder chỉ tạo mới hai booking: EM-Y2-MONTHLY và EM-Y2-HAPPY-END; renter new đặt qua web. Mỗi renter có một hợp đồng đang chạy để tránh trùng lịch. Xe 51Y-00009 dành thay thế.

Chuẩn bị DB demo sạch bằng create-drop theo lựa chọn của bạn rồi khởi động để seed lại. Seed chỉ tạo ba renter với email mới ở bảng bên dưới; không đổi email cũ, không chuyển chủ booking và không có bước dọn dữ liệu cũ. Nếu muốn giữ tiến trình sau lần khởi tạo, dùng ddl-auto=update; create-drop sẽ tạo lại dữ liệu mỗi lần chạy.

## 2. Tài khoản và điểm bắt đầu

Mật khẩu seed mặc định: **EcoMotion@123**.

| Email | Vai trò / booking | Các bước dùng chung |
|---|---|---|
| renter.yearly.new@ecomotion.vn | ID 108, tạo mới trên xe 51Y-00001 | GPLX → đặt xe → hợp đồng → cọc + tháng 1 → nhận xe; có thể kết thúc do lỗi provider để trình diễn nhánh hoàn tháng sự cố |
| renter.yearly.monthly@ecomotion.vn | ID 109, EM-Y2-MONTHLY, xe 51Y-00002 | Tháng tiếp theo → hao mòn/hoàn bảo trì → hư do renter → đổi sang xe 51Y-00009 → cuối cùng trả sớm |
| renter.yearly.happy-end@ecomotion.vn | ID 110, EM-Y2-HAPPY-END, xe 51Y-00010 | Trả xe → kiểm định → phí trễ/phụ phí nếu có → quyết toán → hoàn cọc → hoàn tất |
| provider.a@ecomotion.vn | Provider chung | Duyệt, nhận tiền, giao/nhận xe, xử lý sự cố, chuyển hoàn |

Mở hai cửa sổ khác phiên đăng nhập: một provider, một renter. Giữ provider, chỉ đổi renter khi sang giai đoạn khác. Renter vào /renter/bookings, provider tìm đúng mã trong quản lý đơn. Sau thao tác bên kia bấm **Tải lại**.

Các bước tiếp tục thuê thực hiện nối tiếp trên cùng booking, phải đóng report trước khi tạo report mới. Trả sớm hoặc kết thúc do lỗi provider là bước cuối của booking; không thực hiện hai kết thúc khác nhau trên cùng đơn.

**Ba nhánh kết thúc có thể demo trong cùng buổi:** renter new kết thúc do lỗi provider, renter monthly trả sớm, renter happy-end quyết toán cuối hợp đồng. Các biến thể số học (dư/thiếu tiền, tháng 11/12, đúng hạn/trễ) là lựa chọn hoặc kiểm tra bổ sung, không phải các hợp đồng đang chạy song song và không cần thêm tài khoản.

## 3. Giá, ngày và dữ liệu mẫu

Giá **181.000.000 đ/năm**, cọc **50.000.000 đ**; bước làm tròn **100.000 đ**. Mười tháng đầu 15.100.000 đ, hai tháng cuối 15.000.000 đ. Phí trả sớm trung bình làm tròn lên 15.100.000 đ, tối đa phần tiền thuê tương lai còn lại.

Điều khoản dựng sẵn: 24.000 km/năm, 5.000 đ/km vượt, ân hạn 1 ngày, 500.000 đ/ngày trễ, km nhận 10.000. Renter monthly bắt đầu khoảng ba tháng trước, đã trả ba tháng. Booking happy-end tạo mới đến hạn vào ngày seed để demo kết thúc suôn sẻ; nếu đã có từ seed cũ thì giữ nguyên ngày hạn. Nếu demo sau ngày hạn thì kiểm tra ân hạn/phí trễ thực tế.

Xem ngày thực tế trên web, không đoán phí từ tên booking. Để demo đúng hạn có thể dùng ngày nhận lại đúng dueOn nếu đó là ngày đã qua và biên bản demo tương ứng. Để demo trễ dùng ngày nhận lại hôm nay; không thêm một lượt hoàn tất khác trên cùng đơn.

Lịch sử seed là fixture có nhãn demo, không phải bằng chứng OCR/chuyển tiền/ký thật. Renter new dùng ảnh GPLX được phép sử dụng để gọi AI thật; GPLX dựng sẵn của hai renter còn lại không dùng chứng minh OCR. Không chuyển tiền thật vào tài khoản ngân hàng demo.

## 4. Chuẩn bị file và kiểm tra trước demo

- Hai ảnh GPLX rõ, đủ hai mặt, thuộc trường hợp DocumentAI hiện hỗ trợ; còn hạn đến hết 12 tháng. Mẫu DEMO dựng sẵn không dùng làm đầu vào để chứng minh OCR thật.
- Một PDF hợp đồng mẫu để upload cho đơn mới, nội dung thể hiện giá năm, cọc, 12 kỳ, km, phí sớm và phí trễ.
- Ảnh bàn giao/công-tơ-mét, ảnh sự cố/hóa đơn, ảnh biên lai thanh toán và hoàn tiền. Chỉ dùng dữ liệu được phép trình diễn; mẫu giao dịch cần ghi rõ DEMO.
- Ảnh JPG/PNG tối đa 8 MB theo form hiện tại; PDF theo giới hạn backend.
- Backend, frontend, DocumentAI và cấu hình URL/API key cần chạy đúng. Ảnh minh chứng dùng kho local giống GPLX; lỗi upload là lỗi cần sửa, không bỏ qua bằng cách nhập evidence ID.
- Kiểm tra xe 00001 và 00009 chưa bị dùng bởi buổi demo trước; tài khoản provider có tài khoản nhận tiền đang hoạt động.
- Dùng bước làm tròn 100.000 đ để đối chiếu các con số dưới đây. Nếu cấu hình khác, đọc lịch tiền thực tế.

## 5. Kịch bản 1 — người mới đặt và nhận xe

1. Renter new (ID 108) đăng nhập, vào thuê năm hoặc mở xe `51Y-00001` và chọn gói năm.
2. Upload hai mặt GPLX → gọi DocumentAI → kiểm tra thông tin trả về → lưu hồ sơ. GPLX không hợp lệ thì dừng và báo đúng lý do.
3. Chọn ngày nhận hợp lệ, điểm nhận, gửi yêu cầu thuê. Lưu mã đơn mới; không dùng mã seed của renter khác.
4. Provider mở đơn mới → **Chấp nhận**.
5. Provider vào **Hợp đồng thuê năm** → **Chốt điều khoản cho người thuê**: 24.000 km, 5.000 đ/km, ân hạn 1 ngày, 500.000 đ/ngày → xác nhận.
6. Renter tải lại → kiểm tra điều khoản → đánh dấu đồng ý và xác nhận.
7. Renter upload PDF hợp đồng. Xuất hiện **13 khoản**: một khoản cọc và 12 kỳ tiền thuê, không gộp cọc vào tổng tiền thuê.
8. Renter chọn cọc → chuyển khoản demo theo nội dung riêng → nhập số tiền, mã giao dịch và upload biên lai.
9. Provider tải lại → mở **Đối soát** khoản cọc → kiểm tra bằng chứng và nhập số thực nhận, mã giao dịch → xác nhận.
10. Làm tương tự với tháng 1, số tiền 15.100.000 đ. Tổng ban đầu là **65.100.000 đ**, trong đó 50 triệu là cọc riêng.
11. Provider **Xác nhận bắt đầu giao xe** → sau đó **Xe đã đến điểm giao**, nhập km và upload ảnh.
12. Renter kiểm tra xe/km/ảnh → **Tôi đã kiểm tra và nhận xe**.
13. Kiểm tra trạng thái đang thuê, ngày nhận thực tế và hạn sau đúng 12 tháng. Lịch 12 kỳ neo theo ngày nhận thực tế; không tự chuyển sang có tài xế.

Kiểm tra ngắn: chưa đủ cọc + tháng 1 thì không được dispatch bằng API hiện tại; không thanh toán tháng 2 trước khi hoàn tất hai khoản ban đầu. Đây là ràng buộc đã triển khai, không chứng minh hệ thống kiểm soát việc giao xe ngoài thực tế.

## 6. Kịch bản 2 — thanh toán tháng tiếp theo

Đăng nhập `renter.yearly.monthly@ecomotion.vn`, dùng `EM-Y2-MONTHLY`.

1. Xem cọc và ba tháng đầu đã xác nhận. Các tháng còn lại chưa trả.
2. Chọn tháng 4 → gửi biên lai, nhập 15.100.000 đ và mã giao dịch demo riêng.
3. Provider đối soát tháng 4, xác nhận số thực nhận.
4. Renter tải lại: tháng 4 đã trả, tháng 5 không tự đổi trạng thái; booking vẫn đang thuê.
5. Mở lịch sử thanh toán kiểm tra giao dịch; cọc không bị cộng thành tiền thuê. Nếu provider từ chối biên lai thì renter gửi lại bằng chứng đúng.

Không trình diễn tự động thu nợ, khóa xe hoặc chấm dứt do chậm tiền: các phần đó chưa thuộc scope.

## 7. Luồng chung trả xe và hoàn tất

Dùng các nhánh trả xe bên dưới; luôn hoàn thành theo thứ tự:

1. Renter xem ước tính, chọn hình thức trả phù hợp, gửi yêu cầu.
2. Provider nhận lại xe → nhập ngày nhận thực tế không vượt ngày hiện tại, công-tơ-mét cuối và ảnh kiểm định.
3. Renter xác nhận ngày, km và tình trạng xe.
4. Provider **Lập quyết toán**: phụ phí khác, khoản trừ cọc, ghi chú và bằng chứng nếu có phụ phí.
5. Renter kiểm tra từng dòng tiền → **Đồng ý quyết toán**.
6. Nếu còn phải trả: khoản thanh toán bổ sung xuất hiện → renter gửi biên lai → provider xác nhận. Không gọi khoản này là tháng thuê mới.
7. Nếu cần hoàn: provider chuyển trực tiếp, nhập mã giao dịch và upload biên lai → renter xác nhận đã nhận đủ.
8. Provider **Hoàn tất hợp đồng** khi đủ điều kiện. Hết quyền tiếp tục chạy xe sau khi đã nhận lại/chốt trả, dù còn chờ hoàn tiền.
9. Provider mở lại lịch cho thuê sau kiểm định, ngày sớm nhất là ngày mai. Xe chưa được mở lại không được coi tự động rảnh cho đơn tiếp theo.

Không gộp “đã trả xe”, “đã trả đủ tiền”, “đã nhận hoàn” và “hoàn tất” thành một trạng thái.

## 8. Buổi demo trên renter đang thuê — làm nối tiếp

Đăng nhập renter.yearly.monthly@ecomotion.vn, dùng duy nhất **EM-Y2-MONTHLY**.

1. Thực hiện thanh toán tháng 4 theo mục 6; provider xác nhận.
2. Renter báo OWNER_MAINTENANCE, ngày hôm nay, hóa đơn 1.000.000 đ và ảnh.
3. Provider chọn REIMBURSE, duyệt 1 triệu, chuyển trực tiếp và upload biên lai.
4. Renter xác nhận nhận tiền → report đóng; tiền tháng không bị trừ.
5. Renter tạo report RENTER_DAMAGE với hóa đơn 2 triệu.
6. Provider chọn RENTER_PAID, approvedAmount=0; renter xác nhận → report đóng. Không tính lại khoản đã tự trả.
7. Renter tạo report OWNER_MAINTENANCE để đổi xe.
8. Provider chọn REPLACE → chọn xe 51Y-00009 → hẹn bàn giao → renter đồng ý.
9. Provider nhập km xe cũ 10.500, xe mới 20.000, ngày đổi và hai ảnh; renter xác nhận.
10. Tổng đã đi là 500 km; xe mới bắt đầu tính từ 20.000, giữ nguyên giá/cọc/hạn.
11. Sau các report đã đóng, renter xem ước tính EARLY rồi gửi yêu cầu trả sớm.
12. Provider kiểm định xe mới ở 20.300 km → tổng đã đi 800 km. Tiếp tục quyết toán và hoàn cọc theo mục 7.

Nếu đang tháng 3 và đã trả thêm tháng 4 thì tiền thuê đã nhận 60.400.000 đ, giữ 3 tháng + phí sớm cũng 60.400.000 đ; chưa có phí khác thì không cần bổ sung/hoàn tiền thuê, chỉ hoàn cọc 50 triệu.

Muốn trình diễn có hoàn tiền thuê, renter có thể trả trước thêm tháng 5/6 trước bước trả sớm. Muốn trình diễn còn thiếu, chọn trả sớm trước khi thanh toán tháng 4. Đây là lựa chọn số tiền tại cùng điểm bắt đầu; **không cần chạy cả hai quyết toán trên một booking**.

## 9. Buổi demo người mới — thêm nhánh kết thúc do provider

Dùng renter.yearly.new@ecomotion.vn sau khi đã hoàn thành mục 5 và nhận xe.

1. Tạo report OWNER_MAINTENANCE, mô tả lỗi xe, ngày hôm nay và bằng chứng.
2. Provider kiểm tra, chọn END_PROVIDER, tiền hoàn bảo trì nhập 0 và ghi lý do.
3. Renter xác nhận kết quả report; không gửi thêm yêu cầu EARLY.
4. Provider nhận lại/kiểm định xe, renter xác nhận.
5. Provider quyết toán: hoàn **toàn tháng xảy ra sự cố + các tháng tương lai đã trả**, không có phí tự nguyện trả sớm; cọc tính riêng.
6. Nếu nhận và hỏng ngay tháng 1, chỉ trả tháng đầu 15.100.000 đ, không có phí/km vượt/khấu trừ thì hoàn tiền thuê 15.100.000 đ + cọc 50.000.000 đ.
7. Provider upload chứng từ hoàn → renter xác nhận → provider hoàn tất.

Đây là nhánh kết thúc riêng trên booking mới, không ảnh hưởng booking renter monthly.

## 10. Buổi demo cuối hợp đồng — trả xe và hoàn cọc

Dùng renter.yearly.happy-end@ecomotion.vn, **EM-Y2-HAPPY-END**, đã trả đủ 12 tháng.

1. Xem dueOn và ngày nhận lại dự định.
2. Chọn STANDARD; provider kiểm định, km cuối 20.000, upload ảnh.
3. Renter xác nhận → provider lập quyết toán → renter đồng ý.
4. Nếu nhận đúng dueOn hoặc trong ân hạn, không phí trễ; hoàn cọc 50 triệu nếu không có phụ phí.
5. Nếu thực tế trễ 3 ngày và ân hạn 1 ngày thì phí trễ 1 triệu. Booking mới seed không bị cố tình đặt quá hạn; đọc dueOn và ngày nhận lại thực tế để quyết định có phí hay không. Có thể xem ước tính ngày trả tương lai để giải thích phí, nhưng không nhập ngày kiểm định tương lai.
6. Chọn một cách: renter đóng bổ sung rồi hoàn đủ cọc, hoặc thống nhất khoản khấu trừ cọc tương ứng trong quyết toán; không thu trùng.
7. Provider chuyển hoàn, upload biên lai → renter xác nhận → provider hoàn tất.
8. Provider mở lại lịch xe sau kiểm định từ ngày mai hoặc muộn hơn.

Không bắt buộc demo cả đúng hạn và trễ bằng hai lần kết thúc; có thể đối chiếu preview hoặc giải thích ngày/đơn giá rồi thực hiện một quyết toán cuối cùng.

## 11. Các ca biên để giải thích, không cần tài khoản riêng

- Phí sớm = một tháng trung bình làm tròn lên, nhưng không vượt tiền thuê tương lai còn lại.
- Tháng 11 còn 15 triệu thì chỉ thu tối đa 15 triệu, dù phí trung bình 15,1 triệu.
- Tháng 12 đã trả đủ năm thì phí sớm bằng 0.
- Tiền tháng đã bắt đầu không chia lẻ ngày; kết thúc do lỗi provider hoàn cả tháng sự cố theo chính sách.
- Km cộng theo quãng đường từng xe, không lấy công-tơ-mét xe mới trừ mốc nhận của xe cũ.

Các con số này dùng để giải thích/kiểm tra công thức. Không dựng thêm tài khoản hoặc sửa ngày hợp đồng đang demo để ép trạng thái tháng 11/12.

## 12. Kiểm tra ngắn về quyền, tiền và ngày

Chỉ cần các kiểm tra gắn với nghiệp vụ, không cần tạo hàng loạt test mới:

- 108 không được xem/sửa đơn 109; renter không được xác nhận nhận tiền thay provider.
- Không được nhập ngày kiểm định tương lai, km trả nhỏ hơn mốc nhận, hoặc tiền âm.
- Bấm lại/nhiều tab phải kiểm tra phiên bản; nếu dữ liệu đổi, tải lại rồi xác nhận.
- Chưa xác nhận nhận xe mới thì không cộng km hoặc thay xe hoàn tất.
- Không hoàn tất khi còn nợ bổ sung, report chưa đóng hoặc tiền hoàn chưa xác nhận.
- Các khoản cọc, tiền thuê và hoàn chi phí sửa chữa phải tách biệt.
- Ngày 29/02 cộng 12 tháng về ngày cuối tháng 2 năm sau; mốc tháng 29–31 luôn neo ngày nhận ban đầu (ví dụ 31/01 → 28/02 → 31/03), không cộng dồn từ ngày đã bị rút ngắn.

Các trường hợp lịch đặc biệt có thể kiểm tra hàm ngày riêng; không đổi đồng hồ máy hoặc sửa ngày hợp đồng đã chạy chỉ để ép demo.

## 13. API dùng trong giao diện

Chuẩn URL: **/api**, FE apiClient đã có baseURL này nên service không thêm /v1. Xem [tham chiếu API và body mẫu](YEARLY_RENTAL_API_REFERENCE_VI.md).

Tiền tố: `/api/renter/yearly-bookings/{code}` hoặc `/api/provider/yearly-bookings/{code}`. Các thao tác thay đổi workflow cần `version` mới nhất; report/đổi xe dùng phiên bản của bản ghi tương ứng.

| Bên | Method / hậu tố | Mục đích |
|---|---|---|
| Cả hai | GET /workflow, /handover, /payments | Trạng thái nghiệp vụ, giao nhận, 13 khoản ban đầu |
| Renter | GET /schedule-preview | Dự kiến phân bổ tiền |
| Provider | POST /terms | Chốt km, phí vượt, ân hạn và phí trễ |
| Renter | POST /terms/accept | Đồng ý điều khoản |
| Provider | POST /handover/dispatch, /handover/arrive | Đang giao, đã đến + km/ảnh |
| Renter | POST /handover/receive | Nhận xe, chốt hạn thực tế |
| Renter | POST /returns/preview, /returns | Ước tính và yêu cầu trả |
| Provider | POST /returns/inspection | Kiểm định nhận lại xe |
| Renter | POST /returns/confirm | Đồng ý biên bản trả xe |
| Provider / renter | POST /settlement, /settlement/accept | Đề xuất và đồng ý quyết toán |
| Provider / renter | POST /refund/transfer, /refund/confirm | Bằng chứng chuyển hoàn và xác nhận nhận |
| Provider | POST /complete, /reopen | Hoàn tất hợp đồng, mở lại lịch xe |
| Renter | POST /incidents | Report kèm ảnh/hóa đơn và requestId |
| Provider | POST /incidents/{id}/resolution, /transfer | Xử lý report, ghi khoản hoàn trực tiếp |
| Renter | POST /incidents/{id}/confirm | Xác nhận kết quả report |
| Provider | POST /replacements | Đề nghị xe khác cùng provider |
| Renter | POST /replacements/{id}/accept, /confirm | Chấp thuận và xác nhận bàn giao xe mới |
| Provider | POST /replacements/{id}/inspection | Ngày đổi, km cũ/mới, hai bằng chứng |
| Cả hai | POST /replacements/{id}/cancel | Hủy đề nghị còn hiệu lực |

API chung:

- `GET /api/bookings/{code}/evidences`; `POST /api/bookings/{code}/evidences/upload` (multipart ảnh).
- `GET /api/payment-requests/{id}`.
- `POST /api/payment-requests/{id}/proofs/multipart` (renter gửi biên lai).
- `POST /api/provider/payment-requests/{id}/confirm`, `/reject` (provider đối soát).
- API GPLX/hợp đồng dùng qua `yearlyRentalService.js` và controller tương ứng; đọc Swagger đang chạy để lấy đầy đủ request.

## 14. Ghi kết quả và tái sử dụng

Ghi mã đơn, ngày kiểm tra, số tiền trước/sau, trạng thái và ảnh màn hình các bước chính. Không đánh dấu “đã test trên web” chỉ dựa vào seed hay build.

Các scenario thay đổi DB thật; restart không đưa về ban đầu. Chuyển sang nhánh khác bằng cách đổi tài khoản, không thay cấu hình seed. Ba renter và provider trong bảng được chuẩn bị trong một lần khởi động. Với nhánh đã kết thúc, tạo booking mới qua web để chạy lại hoặc dùng bản sao database demo được chuẩn bị trước. Không xóa giao dịch để tái sử dụng một đơn cũ và không tạo endpoint reset công khai.

Trạng thái kiểm chứng của thay đổi seed: đã compile backend; chưa tự chạy seeder vào PostgreSQL đang dùng và chưa xác minh end-to-end qua trình duyệt. Hướng dẫn này là quy trình cần chạy, không phải báo cáo kết quả đã đạt.


Danh sách endpoint, body và thứ tự gọi hiện hành: [API gói năm — chuẩn /api](YEARLY_RENTAL_API_REFERENCE_VI.md). Các bảng route đề xuất ban đầu cần đối chiếu với tài liệu API này.




Lưu ý storage khi create-drop: Hibernate chỉ tạo lại DB, không xóa file trên ổ đĩa. PDF hợp đồng seed dùng tên có UUID để không trùng file từ lần chạy trước; không cần xóa toàn bộ storage để chạy lại seed.


Chính sách GPLX demo hiện tại: chấp nhận B, B1 và B2, kể cả GPLX cấp trước năm 2025. Vẫn cần kết quả đọc thành công, đủ tuổi, xe tối đa 9 chỗ và GPLX còn hạn đến hết thời gian thuê. Đây là phạm vi demo, không phải đối chiếu đầy đủ các hạn chế theo từng hạng.


Giới hạn ngày nhận xe: GET /api/renter/bookings/start-policy trả maxAdvanceMonths và latestPickupTime theo cấu hình BE. Form năm dùng mốc này để đặt max và chặn bước tiếp theo; mặc định là 3 tháng, không phải 3 năm. POST /api/renter/bookings/pickup-quote hỗ trợ YEARLY để báo giá điểm nhận; báo giá không thay thế bước ký hợp đồng và không tạo booking.


Khi xác nhận tiền cọc/tháng: mỗi lần chuyển cần mã giao dịch ngân hàng riêng. Ví dụ demo: COC-001, THANG1-001, THANG4-001. Không dùng lại cùng mã giữa các khoản trên cùng tài khoản ngân hàng nhận. Lỗi 409 “Giao dịch ngân hàng này đã được đối soát trước đó” nghĩa là mã đã được ghi nhận; kiểm tra lịch sử trước, không tắt kiểm tra trùng hoặc xác nhận lại cùng giao dịch. Mã giao dịch khác với mã booking/nội dung chuyển khoản.


Ảnh minh chứng giao/nhận, sự cố, hóa đơn và hoàn tiền của gói năm được lưu local qua FileStorageService (app.upload.provider=local, app.upload.dir giống ảnh GPLX). POST /api/bookings/{code}/evidences/upload nhận JPG/PNG, tên UUID; GET /api/bookings/{code}/evidences/{id}/file trả binary với kiểm tra quyền và no-store. Client cần gửi Bearer token, không mở URL local bảo vệ trực tiếp bằng img không có Authorization. Không cần Cloudinary cho luồng minh chứng này.
