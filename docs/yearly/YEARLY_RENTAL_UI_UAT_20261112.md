# Gói năm: cập nhật màn hình và kiểm thử UI — 12/11/2026

## Phạm vi và môi trường

Đã sửa trực tiếp trong repo EcoMotion, kiểm tra qua Chrome/Playwright bằng giao diện thật tại localhost:5173, Spring localhost:8080 và PostgreSQL cấu hình `DB_DDL_AUTO=create`. Database được tạo lại và chạy seed trước vòng kiểm thử cuối. Không dùng H2, không gọi API trực tiếp để thay cho thao tác booking/thanh toán trên UI, không mock response nghiệp vụ.

Tài khoản dùng trong vòng này: renter yearly new/monthly/happy-end, provider.a, admin01. Chưa thay đổi cách tổ chức tài khoản/seed trong đợt UI này. Đăng ký tài khoản mới và AI OCR không nằm trong phạm vi kiểm thử; dùng hồ sơ GPLX hợp lệ của seed. CAPTCHA phía FE được tắt tạm bằng `.env.local` để đăng nhập tự động, sau kiểm thử đã khôi phục cấu hình trước đó. Ảnh biên lai là ảnh ghi rõ DEMO, hợp đồng là PDF fixture; không có chuyển tiền ngân hàng thật. Vị trí GPS được giả lập; reverse geocode và báo phí đón dùng luồng VIETMAP thật.

## Những gì đã chỉnh

- Thêm thẻ tóm tắt riêng cho cọc, khoản chưa hoàn tất, sự cố và quyết toán. Không cộng cọc vào tiền thuê.
- Bước trả xe chia thành ba màn hình con: **Trả xe & kiểm định → Chốt tiền & thanh toán → Hoàn tiền & hoàn tất**. Chỉ mở bước tiếp theo khi đạt trạng thái; có thể xem lại bước cũ. Nút việc cần làm đưa về bước hiện tại.
- Khi đến hạn và không có sự cố/khiếu nại cần ưu tiên, hướng dẫn làm thủ tục trả xe thay vì mặc định dẫn sang thanh toán kỳ.
- Tải lại dữ liệu workflow khi trạng thái booking từ màn hình cha thay đổi. Sau provider duyệt đơn, form đề xuất điều khoản xuất hiện ngay.
- Mở chi tiết thanh toán tự cuộn tới vùng thao tác. Giữ input số thực nhận trống, có hiển thị tiền định dạng; không tự dùng số phải trả làm số thực nhận. Sửa thông báo trùng mã ngân hàng không nhắc cứng tháng 1.
- Sự cố đã đóng được thu gọn; sự cố đang xử lý vẫn hiển thị phương án và thao tác theo vai trò.
- Cho phép hủy đề nghị đổi xe chưa hoàn tất ngay cả khi đang có khiếu nại. Đây là hành động BE đã cho phép; trước đây FE ẩn nút khiến Admin không thể tiếp tục phân xử.
- Admin chỉ được chọn kết luận phù hợp với giao dịch hoàn ứng và đề nghị đổi xe hiện tại. Có đề nghị đổi xe chưa kết thúc thì hiển thị lý do và chỉ cho tiếp tục xem xét; có giao dịch chuyển hoàn thì ưu tiên đối soát giao dịch cũ.
- Payload idempotency của Admin tính cả version, tránh thử lại mãi với version cũ sau xung đột.
- BE chấp nhận kết thúc do Admin đối với **YEARLY**, khi đã có ngày kết thúc thực tế và providerConfirmedAt. Không tạo xác nhận giả của renter, không mở rộng quyền này cho gói khác.
- Bảng quyết toán có nhãn riêng khi Admin phân xử. Phân biệt **số tiền khi chốt** với **khoản bổ sung đã đối soát/còn thiếu hiện tại**; hợp đồng hoàn tất không hiển thị nghĩa vụ cũ như yêu cầu trả lại tiền.
- Không thêm API hoặc migration cho đợt chỉnh UI này. Dùng lại API, ledger, bằng chứng và phép tính hiện có.

## Các kịch bản đã thao tác trên UI ở DB sạch

| # | Kịch bản | Kết quả quan sát |
|---|---|---|
| 1 | New chọn xe Y1, ngày nhận, vị trí đón, gửi yêu cầu | Tạo EM-2026-000058, PENDING; chưa yêu cầu chuyển tiền |
| 2 | Provider duyệt → đề xuất km/phí → renter đồng ý → upload PDF | Form điều khoản xuất hiện sau duyệt; chuyển đúng bước hợp đồng rồi thanh toán |
| 3 | Cọc 50 triệu + tháng đầu 15,1 triệu, hai giao dịch riêng | Provider đối soát từng khoản; mở giao nhận sau khi đủ |
| 4 | Dispatch → arrived, ảnh/km → renter nhận xe | Form provider chuyển sang đã gửi, chờ renter; sau nhận hiển thị đang thuê |
| 5 | MONTHLY tháng 4: trả 1,5 triệu rồi 13,6 triệu | PARTIALLY_PAID rồi CONFIRMED 15,1 triệu, không nhân số tiền theo nghĩa vụ |
| 6 | Hao mòn 100.000đ → đề xuất → đồng ý → biên lai hoàn → renter xác nhận | Report CLOSED; không trừ tiền thuê tháng sau |
| 7 | Đổi Y2 sang Y9 | Danh sách theo ngày và khả dụng; renter thấy thông tin hai xe; có chấp nhận và xác nhận bàn giao riêng |
| 8 | Hư do renter, tự trả 200.000đ → hai bên xác nhận | Report CLOSED; khoản tự trả không bị cộng lại vào quyết toán |
| 9 | Trả sớm sau đổi xe | 3.000 km xe cũ + 1.500 km xe mới = 4.500 km; không trừ đồng hồ khác xe |
| 10 | Quyết toán sớm, dùng 1,1 triệu từ cọc | Phí sớm 15,1 triệu; bổ sung 14 triệu; hoàn cọc 48,9 triệu; đủ xác nhận rồi COMPLETED |
| 11 | HAPPY-END kiểm định → renter khiếu nại trước xác nhận trả → Admin phân xử | Bổ sung 6 triệu gồm km vượt 5 triệu và phí trễ 1 triệu; provider xác nhận thành công, không bị REVIEW_REQUIRED vì thiếu cờ kết thúc |
| 12 | Hoàn cọc sau phán quyết Admin | Provider gửi biên lai 50 triệu → renter xác nhận → provider hoàn tất; UI dùng nhãn Admin phân xử |
| 13 | Khiếu nại khi đề nghị đổi xe đang PROPOSED | Admin chỉ có HOLD; renter vẫn hủy được đề nghị; mở lại hồ sơ Admin có thể FIXED và đóng report |
| 14 | Lỗi provider không tiếp tục cung cấp | Không tính phí sớm; hoàn thuê tháng sự cố 15,1 triệu + cọc 50 triệu; xác nhận và COMPLETED |
| 15 | Mở lịch xe sau đổi/trả | Provider mở riêng Y2 và Y9 từ 13/11/2026; thao tác thành công và form hết danh sách xe chờ |
| 16 | Xem lại sau hoàn tất, màn hình rộng 390px | Ba booking chính đều COMPLETED; bảng chốt giữ được lịch sử, khoản bổ sung đã trả hiển thị còn 0; document width = viewport width = 390px |

Vòng cuối ghi nhận **90 request thay đổi dữ liệu qua UI** (bao gồm upload), tất cả HTTP 200/201. Đây không phải số lượng test case độc lập. GET payments trước khi nộp hợp đồng còn trả 404 theo API hiện có; FE xử lý thành danh sách trống, không chặn flow.

## Đối chiếu tiền cụ thể

### Trả sớm sau đổi xe — EM-Y2-MONTHLY

- Tổng thuê 181 triệu, 4 tháng bắt đầu: 60,4 triệu, đã trả đủ 60,4 triệu.
- Phí sớm: trung bình năm làm tròn theo hợp đồng = 15,1 triệu.
- Km: (13.000 − 10.000) + (6.500 − 5.000) = 4.500 km. Hạn mức áp dụng 24.000 × 4/12 = 8.000 km; không vượt.
- Dùng cọc bù 1,1 triệu; renter trả bổ sung 14 triệu; cọc hoàn 48,9 triệu.
- Báo hư renter đã tự trả 200.000đ không được thu trùng.

### Phán quyết Admin — EM-Y2-HAPPY-END

- Đã trả đủ 181 triệu. Hạn 09/11/2026, trả thực tế 12/11/2026.
- Đi 25.000 km, hạn 24.000 km: 1.000 × 5.000đ = 5 triệu.
- Trễ 3 ngày, ân hạn 1 ngày: 2 × 500.000đ = 1 triệu.
- Thu bổ sung 6 triệu, hoàn cọc 50 triệu. Sau hoàn tất: nghĩa vụ bổ sung khi chốt 6 triệu, đã đối soát 6 triệu, còn 0.
- Seed tên HAPPY-END hiện có hạn trước ngày test 3 ngày, nên vòng này kiểm chứng nhánh trễ; không gọi đây là test trả đúng ngày với phí trễ bằng 0.

### Lỗi provider — EM-2026-000058

- Nhận và kết thúc ngày 12/11/2026, report lỗi provider được hai bên đồng ý.
- Hoàn toàn bộ thuê tháng xảy ra sự cố 15,1 triệu; không có phí sớm, không vượt km/trễ; cọc 50 triệu.
- Tổng hoàn 65,1 triệu, renter xác nhận và provider hoàn tất.

## Kiểm tra kỹ thuật

- Backend compile thành công.
- Chạy 30 unit test tập trung, không database: YearlyEndAcceptanceTest (1), YearlyExceptionWorkflowTest (22), YearlyPaymentAmountsTest (5), YearlyRefundGuardTest (2). Tất cả pass.
- ESLint các component chỉnh sửa pass; FE production build pass. Cảnh báo bundle lớn hiện có vẫn còn, chưa làm tối ưu bundle trong đợt này.
- Các ảnh bên dưới đã được mở xem để kiểm tra trực quan; không chỉ dựa vào build hoặc response API.

## Ảnh minh chứng

- [Provider đã gửi biên bản giao xe, chờ renter](ui-20261112-handover.png)
- [Quyết toán theo phán quyết Admin và số bổ sung đã trả/còn thiếu](ui-20261112-admin-settlement.png)
- [Kết thúc do provider, hoàn 65,1 triệu và hoàn tất](ui-20261112-provider-failure.png)

## Giới hạn và phần cần rà tiếp

- Chưa test lại toàn bộ nhánh biên trên UI: ngày 29–31/02, phí sớm bị giới hạn ở tháng 11, trả sớm trong tháng 12, hoàn ứng/hoàn cọc một phần hoặc chuyển thất bại, mạng mất giữa submit và response, hai tab thao tác đồng thời.
- Nhánh chuyển dư 2 triệu, từ chối biên lai, chặn dùng lại mã ngân hàng và Admin xử lý báo chưa nhận đã thử ở vòng UI trước cùng ngày; không gộp chúng vào ma trận DB sạch cuối này.
- Hạn chế Admin về trách nhiệm ở vòng đầu đã được sửa và kiểm thử trong vòng 1–5 bên dưới.
- Chưa thay seed để gom nhiều booking vào một renter demo. Tài khoản và bố trí dữ liệu sẽ chốt riêng sau khi duyệt flow.
- DB đang chạy đã chứa các thao tác test và booking hoàn tất. Muốn diễn lại từ đầu, khởi động lại BE với create để tạo seed mới; không dùng trạng thái cuối test như dữ liệu đầu demo.


## Vòng sửa và kiểm chứng 1–5

Đã hoàn thành cả năm mục sau lượt rà soát. Khởi tạo PostgreSQL bằng create; những lần restart để sửa lỗi giữ dữ liệu bằng validate. Không chạy H2. CAPTCHA chỉ tắt tạm cho Chrome tự động và được khôi phục sau test. Tiếp tục dùng chứng từ DEMO, không chuyển tiền thật.

| Mục | Thay đổi | Kiểm chứng |
|---|---|---|
| 1 | Handover trả version và receive kiểm tra version đó | New nhận biên bản 10.000 km, provider sửa 12.000 km; xác nhận cũ 409 đúng thông báo; tải lại nhận mới 200 và ACTIVE. Checkbox xác nhận reset khi version biên bản thay đổi. |
| 2 | Admin kết luận trách nhiệm độc lập và lưu căn cứ | Renter báo hao mòn, provider đề xuất lỗi renter; Admin chọn lại trách nhiệm provider, duyệt hoàn 100.000đ; report chuyển AWAITING_TRANSFER, provider gửi biên lai và renter xác nhận được. |
| 3 | Admin dùng danh sách phụ phí cùng bộ kiểm tra với provider | UI thêm vệ sinh 500.000đ có mô tả/ảnh, cộng phí trễ tự tính 1 triệu; bù cọc 1,5 triệu, còn hoàn 48,5 triệu. Unit chặn Admin thu lại hóa đơn renter đã trả. |
| 4 | Mỗi lần hoàn có số tiền/mã/ảnh/trạng thái riêng và ledger riêng | Hoàn 20 triệu => còn 28,5 triệu, chưa complete. Hoàn tiếp 28,5 triệu => hoàn tất. Admin lịch sử thanh toán hiển thị hai dòng SECURITY_DEPOSIT_REFUND: TXN-116 (-20 triệu), TXN-117 (-28,5 triệu). |
| 5 | Chi tiết đang mở tự tải lại khi bảng tiền đổi; ẩn QR/thao tác khi chưa đồng bộ | Giữ mở chi tiết tháng 4; provider ghi nhận 1,5 triệu; renter tải lại mà không đóng chi tiết: đã nhận 1,5 triệu, còn 13,6 triệu, URL QR amount=13600000. |

Trong quá trình test phát hiện khóa ledger theo lần hoàn dài hơn cột 64 ký tự. Đã sửa sang khóa định danh ổn định có độ dài tối đa 62 ký tự; retry không ghi trùng. Các lỗi 409 kỹ thuật trước bản sửa không được tính là pass; đã thử lại cùng khoản đang chờ thành công. 409 của biên bản cũ là kết quả mong đợi.

Kiểm tra cuối: **37 unit test tập trung pass**, gồm HandoverVersion (1), ExceptionWorkflow (25), PaymentAmounts (6), RefundGuard (2), Registration (3). Có kiểm tra partial receipt qua Admin, lưu hai lần hoàn riêng, chặn nhận xe dữ liệu cũ và chặn thu trùng hóa đơn. FE build và lint các component đã sửa pass. Không tuyên bố đã kiểm thử mọi tổ hợp đồng thời/mất mạng hoặc toàn bộ nghiệp vụ ngoài năm mục này.

Schema mới: `yearly_refund_transfer`, migration [20261112_yearly_refund_transfers.sql](../../Backend/EcoMotion/src/main/resources/db/migrations/20261112_yearly_refund_transfers.sql). Không tự backfill những lần hoàn lịch sử không có đủ chứng cứ. DB demo hiện đã có thao tác test, không còn ở điểm bắt đầu seed.

Ảnh vòng này:

- [Từ chối biên bản nhận xe cũ và tải lại km mới](ui-20261112-handover-version.png).
- [Hai lần hoàn 20 triệu và 28,5 triệu được xác nhận riêng](ui-20261112-refund-parts.png).


## Kiểm tra lại lỗi nhập tiền cọc/tháng đầu — 12/11/2026

Đã đọc lại code đang có thay đổi mới và chạy lại flow đăng ký trên PostgreSQL thật. Maven package ban đầu đã pass; lỗi startup tái hiện được là PostgreSQL Connection refused. Sau khi người dùng bật DB, BE khởi động thành công. Database khởi tạo bằng create; restart sau build dùng validate để giữ tiến trình test.

Lỗi UI: ActionForm dùng cùng một cờ để khóa toàn bộ ô nhập và khóa gửi. Khi chi tiết thanh toán đồng bộ, fieldset bị disabled, làm gián đoạn nhập/focus. Đã tách submissionDisabled: đồng bộ chỉ khóa nút gửi và xác nhận; trường dữ liệu vẫn nhập được nếu khoản thanh toán còn được phép thao tác. Khi trạng thái không còn cho phép thanh toán hoặc đang submit thật, form vẫn khóa đúng.

Ô tiền nhận số nguyên hoặc phân nhóm hợp lệ (50000000, 50.000.000, 50,000,000, 50 000 000), hiển thị số tiền tương ứng và chuẩn hóa về số nguyên trước khi gửi API. Không tự diễn giải 25.5/25,5 thành 255 hoặc chấp nhận ký hiệu khoa học. Dấu phân nhóm là hỗ trợ của UI, không thay đổi kiểu tiền của API.

Hạn tháng đầu trước khi nhận xe không còn mặc định là hôm trước ngày bắt đầu, gây báo quá hạn cho yêu cầu tạo trong ngày. API trả dueOn=null ở giai đoạn này, UI hiển thị Trước bàn giao; kỳ tiếp theo giữ hạn cũ. Hold và điều kiện đủ cọc/tháng đầu trước nhận xe giữ nguyên.

| Case UI thực | Kết quả |
|---|---|
| Đăng ký → provider duyệt → điều khoản → PDF | Thành công, booking EM-2026-000058 |
| Nhập cọc 50.000.000 | Renter khai và provider ghi nhận đúng 50 triệu, CONFIRMED |
| Nhập số mơ hồ 15,1 | Browser báo lỗi rõ ràng, không cho gửi |
| Nhập tháng đầu 1.510.000 | Provider thấy đúng 1,51 triệu, không bị hiển thị thành 15,1 triệu |
| Provider yêu cầu gửi lại biên lai | Renter gửi lại được, chưa ghi nhận tiền từ biên lai bị từ chối |
| Đối soát 1.510.000 | PARTIALLY_PAID; bổ sung 13.590.000 rồi CONFIRMED tổng 15.100.000 |
| Dispatch → arrive → renter receive | Thành công, booking ACTIVE |
| Giữ một GET chi tiết để mô phỏng mạng chậm | Input vẫn enabled, giữ focus, nút gửi disabled |
| Nhập khi GET đang chờ rồi cho response tiếp tục | Giữ nguyên 15.000.000 và DRAFT-KEPT-DURING-SYNC, không gửi khoản test nháp này |

Test độ trễ dùng giữ/tiếp tục request thật và đồng hồ browser để kích hoạt poll; không mock kết quả thanh toán. Không có chuyển tiền ngân hàng thật. Sau test đã khôi phục CAPTCHA tạm.

Kiểm tra cuối: BE package pass và đang chạy với PostgreSQL; FE build/lint pass; 2 test chuẩn hóa VND và 7 unit test luồng tiền pass, không chạy H2. Lượt này kiểm tra đăng ký–cọc/tháng đầu–nhận xe, giữ draft khi đồng bộ và kiểm tra tiếp bảo trì–trả sớm–hoàn cọc tới COMPLETED. Các nhánh đổi xe/phân xử Admin khác chưa chạy lại trong lượt này. Data hiện đã chứa thao tác demo, chưa reset lại về seed ban đầu.


### Kiểm tra khép kín sau sửa ô tiền

Trên chính booking EM-2026-000058 vừa nhận xe: báo hao mòn 100.000đ → provider đề xuất hoàn trực tiếp → renter đồng ý → gửi biên lai → renter xác nhận → report CLOSED. Sau đó renter trả sớm trong tháng đầu → provider kiểm định 10.100 km (nhận 10.000 km) → renter xác nhận → provider tính quyết toán: giữ tháng đầu 15,1 triệu, phí sớm 15,1 triệu, bù phí sớm từ cọc → renter đồng ý → hoàn cọc còn 34,9 triệu bằng ô nhập “34.900.000” → renter xác nhận → provider complete. UI đạt COMPLETED, tiền hoàn đã nhận 34,9 triệu và còn 0.

[Ảnh hoàn tất flow hiện tại](ui-20261112-current-completed.png).

Sau khi flow đạt COMPLETED, lúc 11:55:56 có thêm một JVM EcoMotion khác; lúc 11:56:03 DB của BE test báo relation users does not exist rồi bảng xuất hiện trở lại. Đây là dấu hiệu DB bị tạo lại từ tiến trình khác, không phải lỗi compile. Đã dừng đúng BE test do agent mở để giải phóng cổng 8080; không dừng JVM bên ngoài. Nếu chạy từ IDE, chỉ dùng một BE trên cùng DB/cổng. Không khẳng định DB hiện còn nguyên kết quả test sau đợt tạo lại ngoài phiên test. File log, driver và chứng từ giả nằm trong TEMP; không có chuyển khoản thật.
