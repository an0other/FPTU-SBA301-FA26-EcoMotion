# Flow đăng ký thuê năm — cập nhật 12/11/2026

Phạm vi: đăng ký đến nhận xe. Không thay flow sự cố, trả xe và quyết toán.

1. Renter chọn xe, lịch 12 tháng, kiểm tra GPLX và điểm giao. Gửi yêu cầu chưa chuyển tiền.
2. Provider chấp nhận trong hạn giữ xe. GPLX phủ ngày hết hợp đồng, không cộng ngày đệm lịch xe.
3. Provider đề xuất km/phí. Renter xem và đồng ý; BE kiểm tra phiên bản và hạn giữ.
4. Renter nộp PDF đã ký. BE khóa booking, kiểm tra quyền, điều khoản, GPLX và hạn giữ trước khi tạo khoản thu.
5. Hai giao dịch riêng: cọc; tháng đầu cộng phí giao xe snapshot. Mỗi giao dịch có mã ngân hàng riêng. Tháng 2–12 chỉ là tiền thuê. Phí giao được thu một lần và loại khỏi tiền thuê dùng trong quyết toán; không nhập lại phí đã thu vào phụ phí.
6. Gửi biên lai chỉ là chờ đối soát. Provider xác nhận tiền thực nhận; đủ cọc + tháng đầu và không có tiền chờ đối soát mới giao/nhận xe.
7. Provider bắt đầu giao, ghi ảnh và công-tơ-mét khi đến. Renter xem và đồng ý ngày bắt đầu thực tế/hạn trả trước khi nhận.
8. Nhận thành công mới IN_PROGRESS. Hạn trả và kỳ tháng tính theo ngày nhận thực tế; giá/cọc không đổi. Lịch xe và GPLX được kiểm tra lại.

GET /api/renter/yearly-bookings/{code}/registration và /api/provider/yearly-bookings/{code}/registration trả phase, responsibleRole, message, quyền thao tác, hạn giữ, chi tiết tiền và ngày nhận/hạn dự kiến nếu nhận hôm nay. BE kiểm tra lại khi ghi dữ liệu.

Demo: new renter gửi yêu cầu → provider duyệt/chốt điều khoản → renter đồng ý/nộp PDF → renter chuyển hai khoản/gửi biên lai → provider đối soát từng khoản → provider giao/ghi ảnh km → renter xác nhận. Kiểm tra nhánh hết hạn, từ chối biên lai và thiếu tiền; không nhận xe khi chưa đủ điều kiện.

Các yêu cầu thanh toán đã tạo trước bản sửa không tự được viết lại. Nếu có phí giao nhưng kỳ đầu cũ chưa chứa phí, phải đối soát dữ liệu trước demo; không sửa âm thầm giao dịch đã nhận tiền. Không tự reset DB.

## Sau nhận xe: trả sớm và trả theo hạn
- Đang thuê: chọn hình thức, xem ước tính cho ngày hiện tại, tích xác nhận và gửi yêu cầu. Gửi yêu cầu chưa đóng hợp đồng hoặc xác nhận trả xe.
- Trước hạn chỉ chọn trả sớm theo chính sách hoặc thanh toán đủ hợp đồng. Trả theo hạn được chọn từ ngày hết hạn; backend kiểm tra cả lúc gửi yêu cầu và lập kiểm định.
- RETURN_REQUESTED: provider nhận lại, kiểm định, gửi ảnh/km/ngày trả.
- RETURN_INSPECTED: renter xác nhận hoặc yêu cầu xem xét.
- RETURNED: kết thúc sử dụng, chưa hoàn tất tài chính; provider lập quyết toán.
- SETTLEMENT_PROPOSED: renter kiểm tra, đồng ý hoặc phản hồi.
- SETTLEMENT_ACCEPTED: trả khoản bổ sung nếu có, provider đối soát; sau đó chuyển tiền hoàn nếu có.
- REFUND_TRANSFER_RECORDED: renter xác nhận nhận đủ hoặc báo thiếu/chưa nhận.
- REFUND_RECEIVED: provider hoàn tất khi không còn report, tiền treo hoặc nghĩa vụ.
- COMPLETED: hồ sơ hoàn tất. Đã trả xe / đã trả tiền không tự đồng nghĩa hoàn tất hợp đồng.
- Trả sớm tính trọn các tháng đã bắt đầu + phí trung bình tháng làm tròn, giới hạn tiền thuê tương lai. Đúng hạn không có phí sớm; trễ tính theo ngày và ân hạn. Km, phụ phí và cọc đối soát riêng.

## Sự cố, đổi xe và bố cục sau nhận
- Trang đang thuê có 5 tab: Tổng quan & xe; Thanh toán; Sự cố & đổi xe; Trả xe & quyết toán; Hợp đồng & lịch sử. Khối trạng thái và nút đi đến việc cần làm luôn hiện trên cùng.
- Hoàn bảo trì: duyệt khoản hoàn -> provider ghi nhận chuyển và ảnh biên lai -> renter xác nhận đủ -> đóng report. Chưa có biên lai phải ghi rõ chờ provider; gửi biên lai xong phải ghi rõ chờ renter. Không cần Admin trong happy path.
- GET /api/provider/yearly-bookings/{code}/replacement-options?incidentId=...&plannedOn=yyyy-MM-dd chỉ trả xe cùng provider đã duyệt, không phải xe hiện tại, trống đủ khoảng còn lại, có gói năm và GPLX phù hợp. Danh sách phụ thuộc ngày; đổi ngày phải tải lại và xóa lựa chọn cũ. Gửi đề nghị và bàn giao vẫn kiểm tra lịch lại.
- Renter xem ảnh, tên, biển số, năm, số chỗ, hộp số/nhiên liệu xe cũ và mới trước khi đồng ý. Đồng ý đề nghị và nhận xe thay thế là hai bước riêng.
- Seed dự phòng: Y9 biển 51Y-00009 và Y12 biển 51Y-00012. Seeder chỉ tạo nếu chưa có, không gắn booking hoặc xóa lịch đã test; xe đã bị dùng sẽ không xuất hiện nếu không còn trống. Cần khởi động BE với dev seed đang bật để xe bổ sung được tạo, không cần reset DB.

## Đối chiếu trả xe và km qua đổi xe
- Sau kiểm định, cả hai bên thấy GET /api/{renter|provider}/yearly-bookings/{code}/return-assessment: ngày nhận/hạn/ngày trả, số tháng, từng chặng công-tơ-mét, hạn mức, km vượt, ngày trễ/ân hạn và bảng tiền.
- Mỗi lần đổi hoàn tất: cộng km kết thúc xe cũ trừ km nhận xe cũ, rồi bắt đầu chặng xe mới từ km nhận xe mới. Chỉ cộng khi renter xác nhận nhận xe mới. Không trừ hai công-tơ-mét khác xe.
- Đối chiếu dựng lại từng chặng từ biên bản nhận và các lần đổi COMPLETED. Tổng phải khớp completedKm và totalKm đã lưu. Sai chuỗi xe/ngày/km thì chặn chốt và yêu cầu đối soát, không dùng số 0.
- Thanh toán đủ hợp đồng dùng hạn mức năm. Trả sớm thường dùng số tháng đã bắt đầu, không cộng thêm tháng phí phạt. Kết thúc do provider giữ hạn mức theo tháng sử dụng thực tế, tính tiền thuê theo ngoại lệ đã chốt.
- Provider thêm từng khoản bằng chứng; tổng tự cộng. POST /api/provider/yearly-bookings/{code}/settlement/preview dùng cùng bộ tính với quyết toán chính. Bù cọc là phương thức thanh toán, giới hạn bởi cọc và khoản thiếu; không phải phí mới.
- Renter đồng ý toàn bộ phiên bản bảng tiền để chốt. Sửa bảng trước khi đồng ý làm đổi version. Sau khi đã đồng ý không sửa qua endpoint đề xuất.
- Chi tiết khoản bổ sung lưu ở yearly_workflow.settlement_charges_json. Với DB hiện hữu dùng migration 20261112_yearly_settlement_details.sql hoặc Hibernate update. Không chạy lại với ddl-auto=create nếu muốn giữ tiến độ.
- Trước chạy BE giữ dữ liệu trong PowerShell: $env:DB_DDL_AUTO = 'update'. Chưa áp migration/reset DB tự động trong lần sửa source này.


## Giữ bản nhập biên lai khi đồng bộ

Polling không xóa số tiền, mã giao dịch, ảnh hoặc xác nhận đang nhập. Trong lúc chờ chi tiết mới, giữ form và tạm khóa gửi/đối soát; QR chỉ hiện khi chi tiết đã đồng bộ. Nếu số tiền, trạng thái hoặc thông tin chuyển khoản thay đổi, bỏ dấu xác nhận để người dùng kiểm tra lại, nhưng giữ các trường nhập. Khoản đã đóng hoặc không còn quyền thao tác vẫn bị khóa; giữ bản nhập không có nghĩa được gửi theo trạng thái cũ. Nếu tải chi tiết lỗi, dùng Thử đồng bộ lại mà không cần mở lại form. Gửi thành công mới đóng form; BE tiếp tục kiểm tra quyền, trạng thái và chống ghi nhận tiền trùng.
