# Đặc tả nghiệp vụ thuê xe năm — bản chốt phục vụ triển khai


> **Trạng thái sau merge main 56a47dc — 10/11/2026:** tài liệu này là baseline nghiệp vụ trước đợt gộp. Code hiện đã bổ sung đổi xe khác giá, giảm trừ xe rẻ hơn và xác nhận xe chất lượng thấp hơn. Những phần đó chưa được team chốt/kiểm thử đầy đủ và khác scope mục 11 bên dưới; không dùng bản spec này để khẳng định code hiện chỉ cho xe cùng giá. Lần gộp giữ cả bảo vệ tranh chấp và số tiền hoàn đã xác minh của nhánh năm. Các đề xuất thảo luận về xác nhận trách nhiệm chưa mặc nhiên trở thành tính năng đã triển khai. Xem checklist mục 11 trong hướng dẫn demo; chỉ sửa baseline sau khi có quyết định nghiệp vụ và test phù hợp.

Cập nhật: 07/11/2026. Tài liệu tổng hợp các quyết định trong phiên trao đổi với người dùng.

Đây là đặc tả đích, không phải báo cáo rằng toàn bộ chức năng đã có. Code EcoMotion được đối chiếu tại commit `bbdfab2`; tài liệu được lưu trong workspace DocumentAI để cùng tham khảo. Không sửa code nghiệp vụ trong lần viết tài liệu này.

## 1. Phạm vi và vai trò

- EcoMotion kết nối renter với provider sở hữu/quản lý xe; gói năm là thuê tự lái, không phân công driver.
- Renter gửi yêu cầu, thanh toán, báo sự cố, xác nhận bàn giao và tiền hoàn.
- Provider chấp nhận hợp đồng, nhận tiền trực tiếp, giữ cọc, xử lý sự cố, nhận lại xe và chuyển tiền hoàn.
- Admin không giữ cọc theo mô hình đã chọn. Cơ chế thu hoa hồng cuối tháng thuộc nhóm tài chính, ngoài đợt triển khai này.
- Hệ thống ghi nhận xác nhận/bằng chứng; không chứng minh việc chuyển tiền hoặc giao xe ngoài đời đã xảy ra chỉ từ một ảnh upload.
- Tận dụng payment request, lịch xe, storage, minh chứng và thông báo hiện có; không tạo hệ thống thanh toán song song.

## 2. Thuật ngữ

| Khái niệm | Ý nghĩa |
|---|---|
| Tổng tiền thuê `T` | Giá thuê toàn bộ 12 tháng, không gồm tiền bảo đảm |
| Tiền bảo đảm `D` | Số tiền cố định provider cấu hình theo xe, được quyết toán/hoàn riêng |
| Giá tháng bình quân `A` | `T / 12`, cơ sở tính phí kết thúc sớm |
| Nghĩa vụ tháng `M[i]` | Tiền thuê phân bổ cho tháng i, tổng 12 tháng bằng T |
| Tiền thuê đã nhận `P` | Tiền thực nhận được xác nhận, phân bổ cho tiền thuê; không gồm cọc |
| Trả xe sớm | Trả xe trước hạn; có thể thanh toán đủ năm hoặc yêu cầu kết thúc sớm |
| Kết thúc sớm | Chấm dứt nghĩa vụ thuê tương lai theo chính sách, có thể phát sinh phí |
| Tiền hoàn | Tách tiền thuê dư, cọc và hoàn ứng sửa chữa; không gộp mất nguồn gốc |

## 3. Thời gian hợp đồng

1. Hợp đồng dài 12 tháng theo lịch, theo ngày Việt Nam (`Asia/Ho_Chi_Minh`), không phải cố định 365 ngày.
2. Ngày bắt đầu chính thức là ngày nhận xe thực tế được ghi nhận. Trước đó lưu ngày dự kiến để kiểm tra và giữ lịch.
3. Nhận 10/11/2026 → hạn trả 10/11/2027; trả trong ngày hạn trả không bị trễ, không xét giờ để tính phí.
4. Nhận 29/02 → hạn trả ngày cuối tháng 2 năm sau.
5. Mốc tháng i tính trực tiếp từ ngày bắt đầu cộng i tháng, không cộng nối từ mốc đã bị rút ngắn. Ví dụ 31/01 → cuối tháng 2 → 31/03.
6. Nghĩa vụ tháng dùng khoảng ngày `[mốc i, mốc i+1)`. Ngày hết hạn là ngày cho phép bàn giao lại, không tạo tháng thứ 13; trả sớm đúng mốc tháng mới trước hết hạn được tính thuộc tháng mới.
7. Ngày dự kiến, ngày bắt đầu thực tế, hạn gốc, ngày đề nghị trả, ngày trả thực tế và ngày đóng tài chính là các trường riêng.
8. Nếu ngày nhận thực tế thay đổi, phải kiểm tra lại toàn bộ lịch 12 tháng và snapshot liên quan trước khi cập nhật. Không âm thầm đè booking khác.

## 4. Giá, cọc và phân bổ tiền

- Provider nhập giá năm và cọc cố định. Giá/cọc/chính sách được hiển thị cho renter và lưu phiên bản khi chốt hợp đồng.
- Giá và chính sách của đơn đã chốt không thay đổi theo chỉnh sửa gói hiện tại.
- Chia gần đều 12 tháng. Đề xuất kỹ thuật cho bước làm tròn là `R = 100.000 đồng`; đây là giá trị mặc định đề xuất, cần cấu hình/chốt khi triển khai, không giả định người dùng đã duyệt con số cụ thể.
- Để tất cả kỳ là bội R và tổng đúng T, T phải là bội R. Chia xuống theo R rồi phân bổ phần dư R cho các tháng đầu theo thứ tự cố định; chênh lệch giữa các tháng tối đa R. Không làm tròn từng tháng độc lập rồi làm sai tổng.
- Nếu T quá nhỏ làm kỳ bằng 0 thì từ chối cấu hình hoặc dùng bước làm tròn nhỏ hơn đã được thống nhất.
- Tiền kỳ đầu và cọc có thể chuyển một lần nhưng phải phân bổ thành hai khoản riêng. Không coi cọc là doanh thu thuê, không dùng cọc tự động giảm nợ thuê.
- Thanh toán tháng tiếp theo trước ngày bắt đầu tháng đó. Cho phép thanh toán trước nhiều tháng/toàn bộ; phân bổ rõ cho từng kỳ.
- Lịch không được tạo lại trùng khi gọi API nhiều lần.
- Phạt/cưỡng chế quá hạn tiền thuê chưa làm; chỉ hiển thị chưa thanh toán/quá hạn. Không tự giải phóng xe hay đóng booking.

## 5. Flow thuê bình thường

1. Renter có tài khoản, hồ sơ và kết quả đọc GPLX theo tích hợp hiện có.
2. Chọn xe/gói năm, xem giá, cọc, chính sách km, trả trễ, trả sớm, lịch tiền; hệ thống kiểm tra lịch xe.
3. Gửi yêu cầu; provider chấp nhận và hai bên chốt hợp đồng.
4. Ghi nhận cọc và tiền thuê tháng đầu; upload bằng chứng, provider xác nhận thực nhận theo luồng hiện có.
5. Ghi nhận bàn giao: ngày, ảnh, km, nhiên liệu/pin, phụ kiện, hư hỏng có sẵn và xác nhận hai bên.
6. Trong thời gian thuê: theo dõi và thanh toán trước các tháng tiếp theo, ghi nhận sự cố nếu có.
7. Nhận lại xe, kiểm tra và chốt quyết toán.
8. Provider hoàn cọc/tiền dư bằng chuyển khoản trực tiếp, upload bằng chứng; renter xác nhận nhận tiền.
9. Hoàn tất khi các nghĩa vụ đã xử lý. Không tự coi upload bằng chứng là đã nhận tiền.

Không xây xác minh chữ ký, xác minh bàn giao vật lý, tự giám định ảnh hay cưỡng chế ngoài đời. Quyền, định dạng, tính nhất quán và trạng thái dữ liệu vẫn phải được kiểm tra ở backend.

## 6. Trả sớm tự nguyện

- Renter được gửi yêu cầu bất cứ ngày nào. Không áp dụng lại điều kiện báo trước 30 ngày từng được đề xuất trước đây.
- Tháng thuê đã bắt đầu tính trọn, không prorate theo ngày đối với trả sớm tự nguyện.
- Gọi `E` là tổng nghĩa vụ các tháng đã bắt đầu; `U = max(0, T - E)`.
- Phí chuẩn `F0 = ceil((T / 12) / R) * R`; phí thực `F = min(F0, U)`.
- Nếu renter chọn trả đủ T và không yêu cầu giảm/hoàn tiền thuê tương lai: không thu F bổ sung.
- Tháng cuối đã tính đủ nghĩa vụ nên U = 0, không phạt thêm.
- Phí kết thúc sớm không mua quyền chạy thêm tháng hoặc hưởng thêm km.

### Đối soát

`Nghĩa vụ = E + F + phụ phí đã được xác nhận`.

So với các khoản đã áp dụng đúng mục đích: dư ghi khoản hoàn; thiếu tạo yêu cầu bổ sung; bằng nhau không thu thêm. Tiền thuê tương lai đã trả không được vừa giữ hết vừa thu thêm F.

Flow: yêu cầu → bảng tính dự kiến → xử lý khoản bổ sung/ghi nhận tiền dư → xác nhận nhận lại xe → kiểm tra và quyết toán cuối → hoàn tiền thuê dư và cọc riêng → xác nhận nhận tiền → hoàn tất.

Bảng trước trả xe là dự kiến; phí phát sinh sau kiểm tra cần căn cứ và xác nhận. Nếu ngày trả thay đổi sang tháng mới, phải tính lại và cho renter xem. Không xóa hạn gốc hay lịch sử báo giá.

Ví dụ T = 120 triệu, trả giữa tháng 4: E = 40 triệu, F = 10 triệu. Đã trả tiền thuê 60 triệu → dư 10 triệu trước phụ phí. Cọc không nằm trong 60 triệu này.

## 7. Trả trễ

- Provider cấu hình số ngày miễn `G >= 0`, đơn giá/ngày `L >= 0`; không cấu hình giờ trong nghiệp vụ năm.
- `lateDays = max(0, số ngày giữa hạn trả và ngày trả thực tế - G)`.
- `lateFee = lateDays * L`.
- Hạn 10/11, G = 1: trả 11/11 không tính phí; trả 12/11 tính một ngày.
- Renter thấy chính sách trước khi ký; provider không thay đổi hồi tố.
- Thiết kế đích đề xuất hệ thống tính theo cấu hình, provider xác nhận cùng quyết toán. Nếu nhóm chọn nhập tay thay vì tự tính phải sửa đặc tả thống nhất, không chạy hai công thức.
- Không cộng tháng thuê mới sau hạn hợp đồng và thu thêm phí trễ cho cùng thời gian.

## 8. Km và hao mòn

- Không có GPS, mô phỏng hành trình, OCR đồng hồ hay tự phát hiện tua km.
- Lưu km nhận/trả dạng số nguyên, ảnh bắt buộc và xác nhận hai bên. Km trả không nhỏ hơn km nhận.
- Hạn mức năm và đơn giá vượt lưu trong snapshot. Khi trả sớm tự nguyện, hạn mức phân bổ theo số tháng đã tính tiền thuê, tối đa hạn mức năm; tháng phí phạt không tăng hạn mức.
- `kmUsed = tổng(km kết thúc từng xe - km bắt đầu từng xe)`.
- `excessFee = max(0, kmUsed - allowedKm) * ratePerKm`.
- Hỏng/thay đồng hồ, số liệu bất thường hoặc bất đồng cần xử lý thủ công; không tự tạo kết luận gian lận.
- Hao mòn thông thường không tự tạo phí sửa. Hư hỏng đã có phải lưu khi bàn giao để tránh thu lại.
- Nghĩa vụ sử dụng/bảo dưỡng và tiêu chí hao mòn cần có trong điều khoản; không tự động giám định trách nhiệm.

## 9. Sự cố và thanh toán sửa chữa

Renter tạo report kèm loại, mô tả, ảnh và ngày xảy ra. Provider kiểm tra rồi chọn kết quả cụ thể; một nút accept chung không được vừa mang nghĩa sửa xong vừa kết thúc hợp đồng.

| Kết quả | Hành vi |
|---|---|
| Hao mòn/bảo trì phía provider, tiếp tục thuê | Provider thanh toán gara hoặc hoàn ứng trực tiếp cho renter; lưu bằng chứng và xác nhận nhận tiền |
| Hỏng thuộc renter, renter đã trả gara | Lưu hóa đơn/bằng chứng và xác nhận xử lý; không cộng khoản đã trả vào tiền thuê/cọc lần nữa |
| Có yêu cầu renter hoàn trả chi phí còn thiếu | Phải có căn cứ và xác nhận, ghi riêng khoản chi phí; không tự trừ cọc từ report |
| Xe hỏng phía provider, kết thúc thuê | Áp dụng mục 10 |
| Xe hỏng phía provider, thay xe | Áp dụng mục 11 |

Không bù chi phí bảo trì vào tháng thuê sau. Khoản sửa chữa/hoàn ứng không phải tiền thuê; lưu loại tiền để không bị tính hoa hồng do gom mọi tiền vào.

Không tự giám định lỗi, định giá thiệt hại, xử lý bảo hiểm hay tự phân xử. Không xây timeout provider không phản hồi; report tiếp tục chờ, không tự PASS, tự thu hay tự đóng đơn.

## 10. Kết thúc vì xe hỏng thuộc phía provider

1. Renter tạo report; provider kiểm tra và chấp nhận kết quả kết thúc vì không tiếp tục cung cấp xe.
2. Ghi ngày sự cố được chấp nhận và tháng thuê chứa ngày đó; không dùng ngày bấm accept để tự dời tháng sự cố.
3. Không thu phí kết thúc sớm. Tiền thuê các tháng trước tháng sự cố giữ nguyên.
4. Hoàn tiền thuê đã trả của toàn tháng xảy ra sự cố và các tháng tương lai; hủy nghĩa vụ chưa trả của các tháng đó. Không hoàn tiền chưa từng nhận.
5. Nhận lại xe và kiểm tra; phụ phí/nghĩa vụ hợp lệ khác quyết toán riêng, không tự xóa.
6. Cọc hoàn phần còn lại sau khấu trừ hợp lệ. Provider chuyển trực tiếp, upload bằng chứng, renter xác nhận.

Đây là ngoại lệ rõ ràng với trả sớm tự nguyện không hoàn tháng đang dùng. Chỉ áp dụng khi report được chấp nhận theo đúng kết quả này, không phải mọi vết xước hoặc lần bảo trì.

## 11. Đổi xe

- Provider chọn xe của mình, đủ điều kiện và trống trong khoảng thời gian thuê còn lại, tạo request kèm ngày nhận.
- Renter xác nhận request; hai bên ghi bàn giao xe cũ và mới, ảnh và km.
- Giữ nguyên tổng giá, cọc, hạn gốc và lịch tiền; không bắt đầu lại 12 tháng.
- Chuyển lịch chiếm dụng sang xe mới bằng thao tác nhất quán; giữ lịch sử xe cũ.
- Xe cũ chỉ mở lại khi provider xác nhận sẵn sàng.
- Cộng km từng chặng; không trừ đồng hồ xe khác nhau.
- Không quản lý phạt/hoàn tiền/bù ngày vì giao xe thay thế trễ trong bản đầu; không tự gia hạn.
- Đổi xe khác giá/cọc, khác provider hoặc đàm phán hợp đồng mới ngoài scope.

## 12. Quyết toán, cọc và mở lại xe

- Phân biệt tiền thuê, phí kết thúc sớm, trả trễ, vượt km, chi phí sự cố được chấp nhận và cọc.
- Mỗi khoản có mã/nguồn để không thu hai lần hoặc vừa trừ tiền thuê dư vừa trừ cọc.
- Bảng quyết toán cho renter xem trước xác nhận. Khoản chưa thống nhất không tự coi là đã thu.
- Hoàn tiền trực tiếp: provider đánh dấu đã chuyển + bằng chứng → renter xác nhận → hoàn thành khoản hoàn. Có thể nhiều khoản hoàn nhưng không vượt nghĩa vụ đã duyệt.
- Rà soát 3 ngày làm việc/hoàn trong 7 ngày từng là đề xuất, chưa được chốt; không coi là SLA đã cam kết. Bản đầu không tự xử lý vi phạm SLA.
- Provider tự cấu hình maintenance buffer; không áp đặt 72 giờ riêng cho năm.
- Sau thuê năm, đề xuất khóa xe nhận booking mới đến khi provider xác nhận đã nhận/kiểm tra và chọn ngày mở lại, sớm nhất ngày tiếp theo. Chính sách khóa đặt xa trong tương lai cần thống nhất với nhóm lịch trước triển khai; không âm thầm hủy booking đã có.
- Xe sẵn sàng và booking hoàn tất tài chính độc lập; xe có thể mở lại trong khi chờ xác nhận hoàn cọc.

## 13. Trạng thái đích (đề xuất ánh xạ, chưa phải enum hiện hữu)

| Trục | Các trạng thái tối thiểu |
|---|---|
| Thuê/hợp đồng | REQUESTED, ACCEPTED, READY_FOR_HANDOVER, ACTIVE, RETURN_REQUESTED, SETTLEMENT_PENDING, COMPLETED; REJECTED/CANCELLED cho nhánh tương ứng |
| Bàn giao | NOT_HANDED_OVER, HANDOVER_PENDING, IN_RENTER_CUSTODY, RETURN_PENDING, RETURNED |
| Nghĩa vụ tiền | UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED |
| Cọc | NOT_RECEIVED, HELD_BY_PROVIDER, SETTLEMENT_PENDING, REFUND_PENDING, REFUND_CONFIRMATION_PENDING, CLOSED |
| Sự cố | OPEN, INVESTIGATING, RESOLUTION_PROPOSED, RESOLVED; kết quả sửa/đổi/kết thúc lưu riêng |
| Hoàn tiền | PROPOSED, CONFIRMED_DUE, TRANSFER_RECORDED, RECEIPT_CONFIRMED |

Không buộc thêm toàn bộ enum này vào BookingStatus. Ưu tiên dữ liệu/trạng thái các trục riêng và ánh xạ UI; không phá luồng driver gói thường. COMPLETED không được suy ra chỉ từ PAID hoặc RETURNED.

## 14. Quy ước URL và API dùng chung

Luồng gói năm dùng **/api** làm tiền tố chính. FE apiClient mặc định baseURL=/api, vì vậy service gọi /renter/... hoặc /provider/... mà không thêm /api hay /v1 lần nữa. Alias /api/v1 của API dùng chung cũ chỉ được giữ để tương thích.

| API | Cách dùng hiện tại |
|---|---|
| POST /api/renter/bookings | Tạo đơn năm đúng một kỳ 12 tháng theo lịch |
| GET /api/renter/bookings/{code} | Chi tiết đơn renter |
| POST /api/provider/bookings/{code}/accept hoặc /deny | Provider duyệt/từ chối yêu cầu |
| POST /api/renter/driving-license/upload | Multipart front/back, gọi DocumentAI |
| GET /api/renter/driving-license và /classes | Hồ sơ GPLX và chính sách hạng |
| GET /api/renter/driving-license/images/{side} | Ảnh GPLX có kiểm tra quyền |
| GET /api/renter/bookings/{code}/contract/status | Trạng thái hợp đồng PDF |
| PUT /api/renter/bookings/{code}/contract | Upload PDF bằng multipart file |
| GET /api/renter/bookings/{code}/contract | Tải PDF binary |
| GET /api/payment-requests/{id} | Chi tiết khoản phải thu |
| POST /api/payment-requests/{id}/proofs/multipart | Renter gửi biên lai |
| POST /api/provider/payment-requests/{id}/confirm hoặc /reject | Provider đối soát |
| GET /api/payment-evidences/{id}/file | Xem biên lai có xác thực |
| GET/POST /api/bookings/{code}/evidences và /evidences/upload | Xem/upload ảnh minh chứng nghiệp vụ |
| GET /api/bookings/{bookingId}/payment-history | Lịch sử thanh toán; bookingId là ID số |

Đối với ảnh minh chứng, GET gọi /evidences; POST gọi /evidences/upload. Không dùng API driver report hoặc quyết toán gói có tài xế để thay cho flow năm. Trả sớm dùng API returns bên dưới, không dùng cancel của luồng trước bàn giao.

## 15. API gói năm đã triển khai

Tiền tố renter R=/api/renter/yearly-bookings/{code}; provider P=/api/provider/yearly-bookings/{code}.

| Chức năng | Endpoint hiện hành |
|---|---|
| Xem trạng thái, lịch thanh toán, giao nhận | GET R/P/workflow, GET R/P/payments, GET R/P/handover |
| Lịch tiền dự kiến | GET R/schedule-preview |
| Chốt điều khoản | POST P/terms; POST R/terms/accept |
| Giao và nhận xe | POST P/handover/dispatch, P/handover/arrive; POST R/handover/receive |
| Dự tính và yêu cầu trả | POST R/returns/preview; POST R/returns |
| Kiểm định và xác nhận trả | POST P/returns/inspection; POST R/returns/confirm |
| Xem report/đổi xe | Đọc incidents/replacements trong GET R/P/workflow |
| Tạo và xử lý report | POST R/incidents; POST P/incidents/{id}/resolution |
| Hoàn chi phí trực tiếp | POST P/incidents/{id}/transfer; POST R/incidents/{id}/confirm |
| Đề nghị và chấp thuận đổi xe | POST P/replacements; POST R/replacements/{id}/accept |
| Bàn giao và xác nhận xe mới | POST P/replacements/{id}/inspection; POST R/replacements/{id}/confirm |
| Hủy đề nghị đổi xe | POST R/P/replacements/{id}/cancel |
| Chốt quyết toán | POST P/settlement; POST R/settlement/accept |
| Hoàn tiền thuê/cọc | POST P/refund/transfer; POST R/refund/confirm |
| Hoàn tất và mở lại lịch | POST P/complete; POST P/reopen |

Các mutation gửi đúng version của workflow/report/replacement theo DTO. EvidenceId lấy từ upload đúng booking, không nhận URL tùy ý. requestId/confirmationId dùng cho chống gửi lặp ở các API tương ứng; tiền cuối do backend tính. Tham chiếu đầy đủ field, body mẫu, điều kiện và thứ tự gọi tại [API gói năm](YEARLY_RENTAL_API_REFERENCE_VI.md).

## 16. Dữ liệu cần có và chênh lệch code

Tối thiểu cần snapshot chính sách năm, 12 nghĩa vụ tháng, phân bổ giao dịch, khoản cọc riêng, biên bản nhận/trả, lịch sử các xe thuê, report/kết quả, bảng quyết toán và khoản hoàn có bằng chứng.

Các điểm đã phát hiện tại bbdfab2:

- BookingServiceImpl còn kiểm tra 525600 phút và một slot; cần chuyển năm sang calendar-month, không ảnh hưởng gói khác.
- `upfrontAmount` đang đi theo tiền trả trước và UPFRONT_PAY; không chỉ đổi nhãn thành cọc mà giữ phép cộng cũ.
- RenterPaymentServiceImpl có fallback `appliedAmount == 0` sang amount; cần bỏ tính nhầm tiền chưa áp dụng.
- ProviderBookingServiceImpl còn chặn toàn bộ phụ phí năm.
- TripTransitions hiện driver-only; cần luồng provider/renter năm riêng.
- Report hiện yêu cầu driver; cần mở nghiệp vụ renter và không kích hoạt auto-reassign driver cho năm.
- Lịch có maintenanceBufferMinutes theo xe; cần áp dụng nhất quán và không tự coi xe đã trả khi hết plannedEndAt.
- Hoa hồng báo cáo hiện lọc COMPLETED và cộng tiền vào; khi bổ sung cọc/chi phí ngoài thuê phải phân loại, không mặc định mọi PAID IN là tiền thuê.
- Hủy trước nhận xe chưa có chính sách mới được chốt; không suy diễn từ quy tắc trả sớm sau nhận xe. Giữ/đánh giá luồng cũ riêng, không tuyên bố hoàn toàn bộ là quyết định đã duyệt.

## 17. Demo: 3 renter + 1 Provider + 1 Admin

Chỉ dùng renter.yearly.new@ecomotion.vn (108), renter.yearly.monthly@ecomotion.vn (109), renter.yearly.happy-end@ecomotion.vn (110), provider.a@ecomotion.vn và admin01@ecomotion.vn. Không tạo renter riêng cho failure/cancel.

- New: có B2 demo sẵn, đi từ đặt xe đến nhận xe; có thể kết thúc do lỗi Provider trên chính booking này. Kiểm tra OCR thật là phần riêng.
- Monthly: thanh toán → hoàn bảo trì → renter tự trả sửa chữa → đổi xe, đóng từng report trước bước tiếp theo → cuối cùng trả sớm.
- Happy-end: đã trả đủ năm, kiểm định → phí trễ/phụ phí nếu có → quyết toán → hoàn cọc → hoàn tất.
- Cancel: EM-Y2-CANCEL thuộc cùng renter happy-end, xe 51Y-00011 riêng, đã trả cọc/kỳ đầu nhưng chưa nhận xe. Ngày nhận sau hạn gốc happy-end và ít nhất một tuần từ ngày tạo fixture, trong cửa sổ đặt xe hiện hành.
- Provider chung xử lý tất cả thao tác bên cho thuê.
- Admin xử lý các hồ sơ sự cố, tiền hoàn, quyết toán hoặc hủy có tranh chấp.

Mỗi renter có tối đa một booking đã nhận xe trong fixture ban đầu; renter happy-end có thêm booking tương lai chưa bàn giao để demo hủy, không trùng khoảng thuê gốc. Ba kiểu kết thúc được trình diễn trên ba booking độc lập; các biến thể tiền/ngày không cần tài khoản riêng.

Seeder không dùng biến env chọn nhánh. Booking đã có được giữ nguyên, kể cả ngày và tiến trình; không reset khi restart. Xe bị chiếm lịch hoặc ngày CANCEL không hợp lệ làm fixture tương ứng bị bỏ qua kèm cảnh báo. Chỉ khởi tạo lại trên DB demo riêng khi chủ động chuẩn bị lại dữ liệu. Xem [hướng dẫn demo](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md) để theo dõi thứ tự màn hình, số tiền và nhánh xử lý khi thay đổi dữ liệu.

### Kiểm thử biên và chống sai nghiệp vụ

- 29/02, 31/01, đúng hạn và ngày sau hạn; không lệch mốc tháng.
- Giá không chia đều, tổng chính xác, làm tròn F và chặn vượt U.
- Trả tháng 12, đã trả đủ năm, đã trả dư các tháng tương lai.
- Chưa xác nhận chuyển tiền không ghi nhận PAID; cọc không giảm nợ thuê.
- Report sửa xong không tự kết thúc; kết thúc do provider chỉ hoàn phần thực nhận.
- Đổi xe hết lịch bị chặn; km không âm/không giảm; không trừ chéo đồng hồ.
- Gửi lại thao tác không tạo hai lịch, hai phí hoặc hai khoản hoàn.
- Sai renter/provider không được xem/xác nhận; tab cũ không ghi đè phương án mới.
- Tiền thuê dư, cọc và hoàn sửa chữa không hoàn/khấu trừ trùng.
- RETURNED chưa đồng nghĩa COMPLETED; mở lại xe không phụ thuộc chờ hoàn cọc.
- Quá hạn thanh toán chỉ hiển thị, không tự hủy/giải phóng xe.

## 18. Ngoài scope và thứ tự triển khai

Ngoài scope: thay đổi cơ chế hoa hồng, GPS, tự giám định lỗi/ảnh, bảo hiểm, tự phân xử, xử lý không phản hồi, cưỡng chế nợ, phạt giao xe thay thế trễ, gia hạn tự động, đổi xe khác giá/cọc/provider, mở rộng model DocumentAI.

Thứ tự: (1) ánh xạ dữ liệu và policy tháng/tiền; (2) lịch thanh toán và cọc; (3) nhận/trả xe; (4) trả sớm/trễ/km và quyết toán; (5) report/hoàn trực tiếp/đổi xe; (6) UI, fixture 3 renter và các luồng demo dùng chung; (7) kiểm thử chống sai quyền/trùng giao dịch và tài liệu demo.

Trước sửa Java đọc lại AGENT_CONTEXT.md và Backend/EcoMotion/AGENT_CODE_STRUCTURE.md. Khi có quyết định khác với tài liệu nghiệp vụ nhóm, ghi rõ thay đổi được người dùng chốt; không tự ghi đè flow gói thường.


## Hướng dẫn demo hiện hành

Bộ seed dùng ba renter 108–110 và một provider; các bước dùng chung được ghép trên cùng booking, không tạo tài khoản riêng từng nhánh, xem [quy trình demo và test chi tiết](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md). Không dùng mẫu thuê 3 năm hoặc các đơn EM-SEED-YEARLY-* cũ để kiểm tra flow mới.


Danh sách endpoint, body và thứ tự gọi hiện hành: [API gói năm — chuẩn /api](YEARLY_RENTAL_API_REFERENCE_VI.md). Các bảng route đề xuất ban đầu cần đối chiếu với tài liệu API này.

## Bổ sung xử lý ngoại lệ đã triển khai ngày 10/11/2026

- Renter có thể báo chưa nhận/nhận thiếu tiền hoàn sau khi Provider ghi nhận chuyển. Số tiền renter khai đã nhận không tự trở thành số tiền hệ thống xác minh.
- Hồ sơ `YEARLY_REFUND` chặn xác nhận/đóng hợp đồng và chuyển lại trong khi đang đối soát. Admin chọn giữ chờ, xác minh chuyển thất bại/tiền quay về, xác minh nhận một phần hoặc xác minh đã nhận đủ.
- Với hoàn một phần, số tiền Admin xác minh là tổng cộng dồn, không được giảm hoặc vượt tổng phải hoàn. Chỉ mở chuyển phần còn thiếu khi giao dịch cũ không còn treo; lần chuyển lại dùng mã giao dịch mới. Không tự chuyển lại nếu chứng cứ chưa rõ hoặc chuyển nhầm chưa thu hồi.
- Khi renter xác nhận đủ hoặc Admin xác minh đủ, hệ thống ghi khoản hoàn một lần theo khóa giao dịch nghiệp vụ. Lượt Admin đối soát dùng `ADMIN_RECONCILED`, không gán thành xác nhận của renter.
- Renter có thể chuyển report chưa giải quyết sang Admin qua hồ sơ `YEARLY_INCIDENT`. Admin có thể giữ chờ, đóng report đã xử lý, yêu cầu hoàn ứng, kết thúc vì phía Provider, hoặc đối soát khoản hoàn ứng đã chuyển. Lý do, tham chiếu bằng chứng, người xử lý và trạng thái trước được ghi audit.
- Khi report đang được Admin xem xét, hai bên không tự thay kết luận hoặc tiếp tục đổi xe; vẫn cho hủy đề nghị đổi xe đang chờ để có thể phân xử. Admin không kết luận report khi còn lượt đổi xe chưa xử lý/hủy.
- Khoản hoàn ứng đã có kết luận nhưng đang chờ chuyển/xác nhận nhận tiền không chặn riêng việc yêu cầu trả xe. Khoản đó vẫn phải hoàn tất trước khi đóng tài chính. Report OPEN hoặc tranh chấp đang mở vẫn chặn bước chốt liên quan và có đường yêu cầu Admin xử lý.
- Giao diện hiển thị ngày áp dụng bảng ước tính, các khoản tiền và số hoàn còn thiếu. Bảng trước kiểm định không phải số cuối cùng.
- Provider chưa có ngân hàng nhận tiền phải thiết lập ở Tài chính trước khi chấp nhận đơn, không đợi đến lúc renter upload hợp đồng mới báo thiếu.

Các thao tác này là phân xử thủ công có bằng chứng, không tự giám định và không tự động kết luận đúng/sai theo timeout. API và checklist kiểm tra nằm trong hai tài liệu cùng thư mục. Cần migration `20261110_yearly_provider_business_closure.sql` để cập nhật cột dữ liệu và CHECK constraint nhóm khiếu nại trên database hiện hữu.
