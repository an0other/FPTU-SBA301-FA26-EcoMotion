# Demo gói năm: 3 renter, một tuyến chính và các nhánh kiểm tra


> Đối chiếu sau gộp conflict với main `56a47dc`, ngày 10/11/2026. Đây là hướng dẫn UAT theo implementation hiện có, chưa phải xác nhận team đã chốt toàn bộ nghiệp vụ sự cố/đổi xe. Frontend đã đạt 178 unit tests và build; backend compile và 17 test policy/ngoại lệ năm đạt. Chưa xác nhận E2E trên DB demo thật. Build thành công không thay thế việc đối chiếu tiền.

Cập nhật 10/11/2026, sau khi cập nhật YearlyRentalDataSeeder. Các trạng thái bên dưới là dữ liệu khi tạo fixture mới; seed không đặt lại tiến trình đã chạy. Đây là kịch bản UAT, không phải báo cáo tất cả bước đã được kiểm thử trên database thật.

## 1. Tài khoản và nguyên tắc trình diễn

Chỉ dùng 3 renter, 1 Provider và 1 Admin. Không có account failure/cancel riêng.

| Tài khoản | Điểm bắt đầu | Phần trình diễn |
| --- | --- | --- |
| renter.yearly.new@ecomotion.vn | Chưa có booking trên DB demo mới, B2 có sẵn | Đặt mới trên 51Y-00001, nhận xe; sau đó kết thúc do lỗi Provider |
| renter.yearly.monthly@ecomotion.vn | EM-Y2-MONTHLY, xe 51Y-00002, đang thuê, trả 3 kỳ | Thanh toán kỳ 4, sự cố, hoàn ứng, đổi xe sang 51Y-00009 rồi trả sớm |
| renter.yearly.happy-end@ecomotion.vn | EM-Y2-HAPPY-END, xe 51Y-00010, đã trả 12 kỳ | Trả xe cuối hạn, quyết toán, hoàn tiền và hoàn tất |
| Cùng renter happy-end | EM-Y2-CANCEL, xe 51Y-00011, CONFIRMED, chưa nhận xe | Nhánh hủy trước bàn giao và Admin phân xử tiền |
| provider.a@ecomotion.vn | Provider đã hoạt động, có ngân hàng nhận tiền | Duyệt, đối soát, bàn giao, sự cố, quyết toán |
| admin01@ecomotion.vn | Admin | Phân xử report, hoàn tiền, quyết toán/hủy |

Mật khẩu mặc định theo DevDataSeeder hiện tại: EcoMotion@123, trừ khi đã được thay đổi trên môi trường của bạn.

Mở ba phiên trình duyệt độc lập: Renter, Provider, Admin. Hai tab cùng một phiên trình duyệt không phải hai phiên đăng nhập độc lập. Giữ nguyên Provider/Admin, chỉ đổi renter khi chuyển câu chuyện.

Mỗi nhánh kết thúc chỉ thực hiện một lần trên booking. Sau khi hoàn tất, không sửa trạng thái bằng SQL hoặc trông chờ restart tự dựng lại.

## 2. Dữ liệu seed và mốc ngày

Gọi D là ngày Việt Nam khi fixture được tạo lần đầu.

| Fixture | Ngày/tiền khởi tạo | Bằng chứng/trạng thái |
| --- | --- | --- |
| MONTHLY | Bắt đầu D trừ 3 tháng rồi trừ 2 ngày; đã trả 3 kỳ và cọc | IN_PROGRESS, đã bàn giao, không có report đang mở |
| HAPPY-END | Hạn trả gốc D trừ 3 ngày; ngày nhận lùi 12 tháng từ hạn đó; đã trả đủ năm và cọc | IN_PROGRESS, đã bàn giao |
| CANCEL | Ngày nhận là mốc muộn hơn giữa D+7 và ngày sau hạn gốc HAPPY-END | CONFIRMED, cọc và kỳ đầu đã trả; actualStartAt null, chưa tạo biên bản nhận xe |
| NEW / xe 00001 | Không dựng booking mới thay renter | Tạo qua FE |
| Xe 00009 | Xe dành đổi xe | Không dựng booking chiếm dụng |

CANCEL dùng chung renter happy-end nhưng khác xe và không trùng khoảng hợp đồng gốc. Nếu dữ liệu HAPPY-END cũ làm ngày nhận CANCEL vượt cửa sổ đặt xe hiện tại, seed bỏ qua CANCEL và ghi cảnh báo. Nếu xe đã có lịch/custody xung đột, seed cũng bỏ qua fixture thay vì ghi đè.

Ví dụ khởi tạo ngày 10/11/2026: MONTHLY nhận 08/08/2026, HAPPY-END hạn 07/11/2026, CANCEL nhận 17/11/2026. Ngày thực tế trên màn hình là nguồn đối chiếu khi dùng dữ liệu đã tạo trước đó.

Giá năm 181.000.000 đ; cọc 50.000.000 đ; bước chia 100.000 đ. Mười kỳ đầu 15.100.000 đ, hai kỳ cuối 15.000.000 đ; tổng đúng giá năm. Điều khoản: 24.000 km/năm, 5.000 đ/km vượt, ân hạn 1 ngày, trễ 500.000 đ/ngày. Km nhận 10.000.

Các booking mới có snapshot giá, pricing item, 13 payment request, giao dịch mô phỏng và PDF DEMO có lịch tiền/chính sách. Seed tự kiểm tra tổng tiền và số kỳ trả trước.

## 3. Chuẩn bị một lần trước buổi demo

1. Chạy backend profile dev. Không có yêu cầu bật app.demo-seed.enabled hoặc chọn scenario bằng env.
2. Áp dụng các migration cần thiết, gồm 20261110_yearly_provider_business_closure.sql cho nhóm phân xử mới và 20261110_b1_vehicle_change_price_difference.sql cho dữ liệu giá/chất lượng đổi xe. Đối chiếu schema/migration đã áp dụng; không chạy lại schema.sql để khắc phục lỗi thiếu cột.
3. Nếu giữ tiến trình thì dùng DB_DDL_AUTO=update. Chỉ khởi tạo lại trên database demo riêng khi chủ động muốn xóa dữ liệu; không chạy schema.sql hoặc create/create-drop vào DB cần giữ.
4. Đọc log: phải có đủ MONTHLY, HAPPY-END và CANCEL hoặc biết rõ fixture nào được giữ/bỏ qua. Kiểm tra xe 00001 và 00009 chưa bị sử dụng.
5. Kiểm tra Provider có tài khoản nhận tiền; chưa có sẽ bị chặn chấp nhận đơn.
6. Ba renter đã có GPLX B2 demo hợp lệ. Không cần upload GPLX thật để đi tuyến gói năm. Ảnh GPLX seed không dùng chứng minh OCR thật; không ghi đè GPLX đã upload thật.
7. Chuẩn bị PDF mẫu và ảnh JPG/PNG: giao xe/km, hóa đơn sửa chữa, hai xe khi đổi, biên lai thu/hoàn. Ghi rõ DEMO, không chuyển tiền thật. PDF fixture có nhãn chưa ký, không khẳng định là chữ ký thật.
8. Kiểm tra FE/BE, upload local, dịch vụ bản đồ/tuyến đường. Đặt xe mới vẫn cần các dependency hiện tại; seed B2 không làm mọi dependency trở thành offline.
9. Lưu bảng ngày, tiền, mã booking và bản sao DB + thư mục bằng chứng tương ứng trước buổi demo.

Seed giữ booking đã tồn tại, kể cả đã hoàn tất hoặc ngày cũ. Muốn đúng trạng thái/ngày mẫu mới thì cần fixture mới trên DB demo được chuẩn bị chủ động; không tự dịch ngày hay xóa tiến trình của buổi trước.

## 4. Các màn hình dùng chung

| Vai trò | Màn hình |
| --- | --- |
| Renter | /renter/bookings; mở đúng booking |
| Renter gói năm | /renter/bookings/{bookingCode}/yearly-rental |
| Đặt xe | /vehicles/{id}, hoặc luồng /renter/bookings/new |
| Provider | /provider/bookings → /provider/bookings/{bookingCode}, mở khu vực hợp đồng/gói năm |
| Admin | /admin → mục khiếu nại/phân xử, mở đúng hồ sơ theo mã booking |

Không dùng ID xe thay mã booking. Mã booking NEW lấy từ kết quả đặt thật, không gõ EM-Y2-MONTHLY để thao tác thay.

Sau thao tác ở phiên khác, bấm Tải lại. Trước mỗi bước, xác nhận đúng email, mã booking, biển số và trạng thái.

## 5. Tuyến trình diễn chính

Mục tiêu khoảng 15–20 phút khi đã chuẩn bị file và phiên đăng nhập. Đây là thời lượng dự kiến; không bỏ qua validation để đạt thời gian.

### A — Đặt mới và nhận xe: renter new

| Bước | Người/màn hình/thao tác | Kết quả cần thấy |
| --- | --- | --- |
| A1 | Renter mở xe 00001, chọn năm, ngày/giờ nhận hợp lệ và điểm nhận | Giá, cọc, thời gian thuê; B2 đã có sẵn |
| A2 | Gửi yêu cầu thuê | Một booking PENDING mới; ghi mã vào bảng demo |
| A3 | Provider mở đúng booking → Chấp nhận | ACCEPTED; chưa đồng nghĩa đã nhận xe |
| A4 | Provider chốt điều khoản 24.000 km, 5.000 đ/km, 1 ngày ân hạn, 500.000 đ/ngày | TERMS_DRAFT |
| A5 | Renter đọc và đồng ý | TERMS_ACCEPTED |
| A6 | Renter upload PDF demo | Một cọc + 12 kỳ; không gộp cọc vào 181 triệu |
| A7 | Renter gửi biên lai cọc 50 triệu → Provider kiểm tra và xác nhận | Chỉ khoản cọc được xác nhận |
| A8 | Lặp với kỳ đầu 15,1 triệu, dùng mã giao dịch khác | Ban đầu đã nhận 65,1 triệu, gồm hai nguồn khác nhau |
| A9 | Provider bắt đầu giao → xe đã đến, km 10.000 và ảnh | ARRIVED; chưa tự thành đang thuê |
| A10 | Renter kiểm tra rồi nhận xe | IN_PROGRESS; ngày thực nhận neo lại hạn 12 tháng/lịch kỳ |

Có thể chọn giờ nhận cùng ngày đủ điều kiện thời gian của BE để các mốc ngày dễ theo dõi. Nếu ngày thực nhận khác dự kiến, phải đọc lại hạn/lịch tiền trên màn hình.

Dừng tại đây để chuyển sang MONTHLY; nhánh kết thúc do Provider ở mục 7, không thực hiện trả sớm EARLY trên booking NEW trước nhánh đó.

### B — Trong thời gian thuê: renter monthly

1. Mở EM-Y2-MONTHLY. Chỉ cọc và kỳ 1–3 được xác nhận; kỳ 4 chưa được thanh toán.
2. Gửi biên lai kỳ 4 là 15,1 triệu, mã giao dịch mới. Provider xác nhận → tiền thuê đã nhận 60,4 triệu; tiền bảo đảm vẫn 50 triệu.
3. Báo hao mòn OWNER_MAINTENANCE, hóa đơn 500.000 đ. Provider chọn REIMBURSE 500.000 → ghi nhận chuyển → renter xác nhận. Report đóng; không tự giảm tiền tháng 5.
4. Nếu cần chứng minh trách nhiệm renter: tạo RENTER_DAMAGE, hóa đơn 300.000 đ → Provider chọn RENTER_PAID, approvedAmount=0 → renter xác nhận. Không thu lại khoản renter đã thanh toán.
5. Tạo một report hao mòn khác để đổi xe → Provider chọn REPLACE, approvedAmount=0 → chọn xe 00009 → đọc giá cũ/mới và cảnh báo chất lượng → renter đồng ý. Nếu lowerQuality=true thì phải chọn xác nhận nhận xe chất lượng thấp hơn; không chỉ tick xác nhận thao tác chung.
6. Provider ghi giao nhận: xe cũ kết thúc 14.000 km, xe mới bắt đầu 2.000 km, có ảnh riêng. Renter xác nhận.
7. Kiểm tra giá hợp đồng gốc/cọc/hạn/kỳ tiền giữ nguyên. Với fixture mới chưa chỉnh sửa, xe 00009 cùng giá năm 181 triệu nên giảm trừ đổi xe dự kiến bằng 0. Nếu dữ liệu cũ hoặc giá đã đổi, đọc oldPlanPrice/newPlanPrice và vehicleChangeDiscount; không dùng nguyên các con số quyết toán mẫu phía dưới. Quãng đường xe cũ là 4.000 km; không lấy đồng hồ xe mới trừ mốc xe cũ.

Chỉ tạo report tiếp theo khi report trước đã đóng. Nếu ít thời gian, chọn một nhánh sửa chữa và nhánh đổi xe để thao tác; mở lịch sử/phần chính sách để giải thích nhánh còn lại.

### C — Trả sớm: tiếp tục MONTHLY sau B

1. Xem ước tính EARLY cho ngày trả hợp lệ, rồi gửi yêu cầu.
2. Provider kiểm định xe mới: km cuối 3.500 → thêm 1.500 km; tổng hợp đồng 5.500 km.
3. Renter kiểm tra ngày/km/ảnh rồi xác nhận nhận lại xe.
4. Provider lập quyết toán. Với fixture mới tạo đúng mốc trên và trả trong tháng thứ 4: E=60,4 triệu, phí sớm=15,1 triệu; chưa có phí km hoặc phụ phí.
5. Cách nhanh: chọn khấu trừ cọc 15,1 triệu, phụ phí khác 0 → cần trả thêm 0, hoàn cọc 34,9 triệu. Đây là lựa chọn khấu trừ minh bạch được renter chấp nhận, không tự động dùng cọc thanh toán nợ.
6. Nếu thầy chọn khấu trừ 0: cần thanh toán bổ sung 15,1 triệu, sau đó hoàn đủ cọc 50 triệu. Không vừa thu 15,1 triệu vừa khấu trừ 15,1 triệu.
7. Renter đồng ý quyết toán → thực hiện khoản thiếu nếu có → Provider ghi chuyển hoàn → renter xác nhận → Provider hoàn tất.

Các con số phải tính lại nếu ngày trả, km, số kỳ đã thanh toán hoặc phí thay đổi. Hoàn ứng sửa chữa không được cộng thành tiền thuê đã trả.

### D — Cuối hợp đồng: renter happy-end

1. Mở EM-Y2-HAPPY-END, không mở EM-Y2-CANCEL. Kiểm tra đủ 12 kỳ 181 triệu và cọc 50 triệu.
2. Chọn STANDARD. Provider nhập ngày nhận lại, km 20.000 và ảnh.
3. Nếu chọn ngày hạn gốc D-3 (biên bản DEMO tương ứng), không phí trễ; nếu chọn ngày D, trễ 3 ngày, miễn 1 → tính 2 ngày = 1 triệu. Không nhập ngày kiểm định tương lai.
4. Renter xác nhận → Provider lập quyết toán. Không có phí khác/km vượt: hoàn cọc 50 triệu ở nhánh đúng hạn.
5. Ở nhánh trễ, chọn khấu trừ 1 triệu → hoàn 49 triệu; hoặc thu bổ sung 1 triệu rồi hoàn 50 triệu, không thu trùng.
6. Renter đồng ý → Provider ghi chuyển → renter xác nhận → Provider hoàn tất.
7. Mở lại lịch xe từ ngày mai hoặc muộn hơn sau kiểm định. Sẵn sàng nhận lịch và hoàn tất tài chính là hai vấn đề độc lập.

## 6. Các điểm dừng khi thầy muốn kiểm tra

| Điểm dừng | Thử thay đổi | Kết quả và cách tiếp tục |
| --- | --- | --- |
| Trước nhận xe | Thiếu cọc/kỳ đầu rồi giao xe | Bị chặn; hoàn tất khoản thiếu rồi thử lại |
| Lịch thanh toán | Dùng lại mã giao dịch đã nhận | Bị chặn trùng; kiểm tra lịch sử, không bịa mã mới cho cùng giao dịch |
| Kiểm định | Km cuối nhỏ hơn đầu; ngày trước nhận hoặc trong tương lai | Bị chặn; dữ liệu hợp lệ trước đó phải còn nguyên |
| Quyết toán | Đổi ngày/km/khấu trừ | BE tính lại; đọc từng dòng thay vì cố giữ số mẫu |
| Hoàn tiền | Renter chưa nhận/nhận thiếu | Dùng nhánh Admin mục 8, không bấm nhận đủ cho nhanh |
| Report | Không đồng ý/Provider chưa xử lý | Gửi Admin xem xét, không sửa DB để đóng report |
| Hai phiên cùng thao tác | Gửi version cũ hoặc bấm lặp | Không ghi tiền hai lần; tải lại trạng thái/kiểm tra kết quả đã xử lý trước khi tiếp tục |
| Sai vai trò/chủ booking | Gọi thao tác của bên kia | Bị chặn quyền, quay về đúng phiên |
| Đã nhận xe | Muốn dùng hủy trước bàn giao | Dùng flow trả xe; nhánh hủy trình diễn trên booking CANCEL riêng |

Sau mỗi lỗi, kiểm tra tiền, trạng thái và số giao dịch không bị thay đổi ngoài dự kiến. Một thao tác bị chặn đúng là kết quả kiểm tra hợp lệ.

## 7. Kết thúc do Provider: dùng booking NEW sau A

1. Renter tạo OWNER_MAINTENANCE, ngày sự cố thuộc tháng đầu và bằng chứng phù hợp.
2. Provider chọn END_PROVIDER, approvedAmount=0, ghi lý do không thể tiếp tục cung cấp xe.
3. Renter xác nhận report → Provider kiểm định/nhận lại → renter xác nhận. Không tạo yêu cầu EARLY khác.
4. Quyết toán với không phụ phí, không vượt km: phí kết thúc sớm 0; tiền thuê tháng sự cố đã nhận được hoàn 15,1 triệu, cọc hoàn 50 triệu.
5. Chuyển hoàn tổng 65,1 triệu, renter xác nhận rồi hoàn tất. Không hoàn tiền các tháng chưa từng nhận.

Nếu thầy chọn FIXED hoặc REPLACE thì booking tiếp tục theo phương án đó; không ép END_PROVIDER trên report đã đóng. Ghi nhận nhánh đã chọn và dùng các booking còn độc lập để tiếp tục buổi demo.

## 8. Admin can thiệp: không thêm tài khoản

### Report chưa xử lý

- Renter mở report chưa đóng → Yêu cầu Admin xem xét, ghi vấn đề và ảnh.
- Admin mở hồ sơ YEARLY_INCIDENT theo đúng booking/report.
- HOLD giữ hồ sơ mở. Hai bên không tự thay kết luận khi Admin đang xem xét.
- FIXED/RENTER_PAID là kết luận đã xử lý; REIMBURSE tạo nghĩa vụ hoàn ứng, END_PROVIDER mở nhánh trả do Provider.
- Nếu đang có đề nghị đổi xe thì xử lý/hủy đề nghị đó trước khi phân xử report.
- Khoản hoàn ứng đã có kết luận, đang chờ chuyển/xác nhận không chặn riêng yêu cầu trả xe; hoàn tất tài chính vẫn cần report đóng.
- Đã có biên lai hoàn ứng thì dùng TRANSFER_VERIFIED hoặc FAILED_TRANSFER theo bằng chứng, không bỏ qua giao dịch để đổi kết luận tùy ý.

### Chưa nhận/nhận thiếu tiền hoàn

- Dùng MONTHLY hoặc HAPPY-END ở REFUND_TRANSFER_RECORDED. Renter chọn Chưa nhận/nhận thiếu, nhập tổng thực nhận và ảnh.
- Admin mở YEARLY_REFUND. Số renter khai chưa tự thành số đã xác minh.
- HOLD: giữ chờ; không chuyển thêm khi giao dịch cũ chưa rõ kết quả hoặc chuyển nhầm chưa thu hồi.
- FAILED_VERIFIED: đã xác minh chuyển thất bại/tiền quay về; mở chuyển lại phần còn thiếu, yêu cầu mã giao dịch mới.
- PARTIAL_RECEIVED: xác minh số nhận cộng dồn, phần thiếu không còn treo; chỉ chuyển thiếu.
- CONFIRMED_RECEIVED: số đã xác minh phải bằng tổng phải hoàn; ghi nhận đúng Admin, không giả thành renter tự xác nhận.
- Cấm giảm số đã xác minh hoặc vượt tổng phải hoàn. Không hoàn tất khi hồ sơ/tiền còn chưa xử lý.

## 9. Hủy trước nhận xe: cùng renter happy-end

1. Mở EM-Y2-CANCEL, xe 00011. Kiểm tra CONFIRMED, actualStartAt chưa có, cọc 50 triệu và kỳ đầu 15,1 triệu đã nhận.
2. Renter bấm Hủy yêu cầu trước nhận xe. Không bấm nhận xe trên đơn này trước khi demo hủy.
3. Đơn vào CANCELLATION_REVIEW, ngừng thu các kỳ tương lai, giải phóng lịch xe, có hồ sơ YEARLY_CANCELLATION.
4. Admin xem hợp đồng, tiền thực nhận và bằng chứng; quyết định nghĩa vụ/tiền hoàn theo căn cứ. Tổng nhận là 65,1 triệu, không được hoàn quá nguồn còn lại.
5. Thực hiện luồng hoàn/đối soát hiện tại và đóng hủy khi không còn nghĩa vụ treo.

Không khẳng định mọi hủy trước bàn giao mặc định hoàn toàn bộ. Số Admin nhập cần có căn cứ, không lấy phí EARLY sau nhận xe áp ngược. Nếu thầy đã nhận xe trên đơn CANCEL thì nó trở thành hợp đồng đang thuê và phải đi flow trả xe.

## 10. Phạm vi và khôi phục

- Tuyến chính chứng minh đặt/nhận, vận hành, trả sớm và kết thúc bình thường. Nhánh 7–9 được mở theo câu hỏi; không bắt làm tất cả trong 15 phút.
- Không cần account cho từng phép tính dư/thiếu tiền hoặc tháng 11/12. Dùng preview cho số học trước khi xác nhận một kết quả cuối.
- Không tự reset, đổi ngày hay chuyển chủ booking đã tồn tại. Khi một nhánh kết thúc đã được chọn, chuyển tình huống khác hoặc dùng snapshot demo đã chuẩn bị.
- Khôi phục DB cần kèm thư mục file đúng snapshot. Chỉ khôi phục trên môi trường demo được chủ động chọn.
- Các fixture/cọc/biên lai/PDF mô phỏng không chứng minh giao nhận hoặc chuyển tiền ngoài đời.
- Luồng đăng ký Provider demo riêng một lần theo docs/provider-register. Gói năm dùng Provider đã sẵn sàng để không phụ thuộc kết quả AI đăng ký ngay trong buổi trình bày.


## 11. Checklist sau merge — tách tuyến demo và bài hồi quy

Tuyến A–D ưu tiên fixture cùng giá, không phát sinh giảm trừ đổi xe. Trước buổi trình bày kiểm tra config, tình trạng xe, giá snapshot và tiền đã nhận trên chính DB đang dùng; seed không reset dữ liệu cũ.

| Bài kiểm tra riêng | Kỳ vọng/điểm đối chiếu | Trạng thái hướng dẫn |
| --- | --- | --- |
| Đổi xe cùng giá 00009 | Giá hợp đồng/cọc/hạn/kỳ tiền không đổi; giảm trừ 0, km cộng từng chặng | Tuyến chính, cần UAT trên fixture thực |
| Xe thay thế chất lượng thấp | UI cảnh báo; gọi accept không acknowledgeLowerQuality=true phải bị chặn; không tự đổi xe khi mới accept | Có code kiểm tra, chưa xác nhận E2E |
| Đổi xe đắt hơn/rẻ hơn | Code hiện giữ giá gốc: đắt hơn không thu thêm; rẻ hơn tính giảm trừ theo chặng khi quyết toán | Nhánh sau merge, chính sách còn cần team xác nhận |
| Xe rẻ hơn rồi kết thúc PROVIDER_FAILURE | Đối chiếu để không giảm thêm trên tháng đã miễn toàn bộ tiền thuê; không lấy công thức hiện tại làm expected độc lập | Rủi ro đã phát hiện, chưa xác nhận đã sửa; không demo như case đạt |
| Ngày trả trước lần đổi xe đã hoàn tất | Timeline cần nhất quán; nếu API vẫn nhận là lỗi cần ghi lại, không gọi đó là kết quả đúng | Cần hồi quy, chưa xác nhận đã chặn |
| Admin đang xem xét | Các nút đổi xe/xác nhận bị khóa theo reviewOpen và API có kiểm tra; dùng đúng hồ sơ YEARLY_* | Cần test UI và gọi API trực tiếp |
| Hủy đề nghị đổi xe đang treo khi có tranh chấp | Kiểm tra đường hủy và khả năng Admin tiếp tục phân xử, không tự suy ra mọi thao tác đều được khóa | Chính sách/đường xử lý cần rà riêng |
| Hoàn thiếu rồi chuyển phần còn lại | Xem refundVerifiedReceived/refundRemaining; số đã xác minh là cộng dồn, không cộng lại số renter tự khai | Nhánh Admin mục 8, cần UAT |
| Trước nhận xe: hủy khi đang chờ đối soát tiền | Theo dõi receipt/refund theo luồng tài chính dùng chung mới; không tái dùng công thức EARLY | Bài hồi quy riêng, không chốt mặc định hoàn 100% mọi trường hợp |

Phân xử YEARLY_INCIDENT/YEARLY_REFUND đi qua panel/API năm riêng; không dùng lựa chọn OTHER của luồng tranh chấp chung thay cho chúng. Với lỗi tiền hoặc trạng thái, lưu request/response, ảnh và log; không chỉnh SQL hoặc thay expected để demo tiếp như thành công.

Kiểm tra sau cùng: cùng một chi phí sửa chữa không bị thu lại qua otherCharges/cọc; hoàn ứng không cộng thành rentPaid; tổng tiền hoàn được xác minh không vượt tổng nghĩa vụ. Hai hướng khác nhau phải dùng booking/snapshot demo riêng, không đảo trạng thái booking đã kết thúc.
