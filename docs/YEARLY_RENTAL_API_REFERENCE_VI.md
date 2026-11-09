# API gói năm — mô tả và cách sử dụng

Đối chiếu controller/service hiện tại ngày 07/11/2026. Chuẩn URL của luồng này là **/api**, không thêm /v1 trong request mới. Các alias /api/v1 đã có ở API dùng chung được giữ để tương thích; tài liệu này chỉ dùng đường dẫn chính /api.

Nghiệp vụ: [đặc tả](YEARLY_RENTAL_BUSINESS_SPEC_VI.md). Tài khoản và thao tác demo: [hướng dẫn demo](YEARLY_RENTAL_DEMO_TEST_GUIDE_VI.md).

## 1. Quy ước gọi

- Base URL backend local: `http://localhost:8080/api`; thay host/cổng theo môi trường.
- Header: `Authorization: Bearer <access_token>`. JSON dùng `Content-Type: application/json`.
- FE `apiClient` mặc định baseURL `/api`: gọi `/renter/yearly-bookings/...`, không gọi `/api/renter/...` hoặc `/v1/renter/...`. Nếu cấu hình `VITE_API_BASE_URL` thì giá trị phải kết thúc bằng `/api`.
- Renter chỉ thao tác đơn của mình; provider chỉ thao tác xe/đơn thuộc mình. Không truyền userId để giả vai trò.
- `{code}` là bookingCode; `{id}` tùy endpoint là paymentRequestId, incidentId hoặc replacementId, không dùng lẫn với booking ID.
- Ngày nghiệp vụ: `yyyy-MM-dd` theo Asia/Ho_Chi_Minh. Timestamp tạo đơn: ISO 8601 có timezone, ví dụ `2026-11-10T09:00:00+07:00`.
- Tiền là số VND, không có dấu phân cách hàng nghìn. Km là số nguyên không âm.
- Mọi ID, ngày và version dưới đây chỉ minh họa; lấy dữ liệu thật từ response trước đó.
- Response JSON thường là `{"code":"SUCCESS","message":"OK","data":...}`. PDF/ảnh trả binary, không bọc trong data.

Các ký hiệu rút gọn trong bảng:

| Ký hiệu | Tiền tố đầy đủ |
|---|---|
| R | /api/renter/yearly-bookings/{code} |
| P | /api/provider/yearly-bookings/{code} |
| R/P | Chọn R nếu đăng nhập renter, P nếu đăng nhập provider |

### Version và chống gửi lặp

`GET R/workflow` hoặc `GET P/workflow` trả version của hợp đồng. Các thao tác điều khoản/trả xe/quyết toán/hoàn cọc/mở lịch gửi version này.

Report có `incidents[].version`; đổi xe có `replacements[].version`. API thao tác bản ghi nào thì gửi version của chính bản ghi đó. Không lấy version hợp đồng thay cho report/đổi xe.

Sau mỗi mutation, đọc lại workflow trước thao tác tiếp theo. Gặp xung đột version thì tải lại, kiểm tra rồi xác nhận lại; không tự tăng số version.

Tạo report dùng requestId riêng, tối đa 64 ký tự. Retry cùng request phải giữ nguyên requestId và nội dung. Xác nhận tiền dùng confirmationId riêng cho một lần ghi nhận, giữ nguyên khi retry cùng giao dịch.

## 2. GPLX — trước khi đặt năm

| Method / URL | Quyền | Cách dùng và kết quả |
|---|---|---|
| GET /api/renter/driving-license | Renter | Đọc hồ sơ và điều kiện thuê năm hiện tại |
| GET /api/renter/driving-license/classes | Renter | Đọc các hạng và chính sách đang hỗ trợ |
| POST /api/renter/driving-license/upload | Renter | multipart: front và back; Spring gọi DocumentAI thật, kiểm tra kết quả, lưu ảnh và thông tin đọc được |
| GET /api/renter/driving-license/images/{side} | Renter | side=front hoặc back; trả ảnh riêng tư của chính renter |
| PUT /api/renter/driving-license | Renter | Khai báo/chỉnh tay: licenseNo, licenseClass, licenseExpiry; khi thay đổi thông tin sẽ bỏ xác nhận AI và xóa liên kết ảnh/kết quả đọc cũ |

Ví dụ PUT khai báo: `{"licenseNo":"DEMO123456","licenseClass":"B","licenseExpiry":"2030-12-31"}`. Đây không thay thế POST upload để chứng minh đã đọc giấy tờ bằng AI. GPLX upload hiện nhận JPG/PNG theo backend; giới hạn ảnh bằng cấu hình tương ứng.

## 3. Tạo và duyệt đơn — API booking dùng chung

| Method / URL | Quyền | Cách dùng |
|---|---|---|
| GET /api/renter/bookings/vehicles | Renter | Lấy xe và gói đang cho phép đặt |
| POST /api/renter/bookings/pickup-quote | Renter | Báo giá đón: vehicleId, planId, pickupLatitude, pickupLongitude |
| POST /api/renter/bookings | Renter | Tạo yêu cầu thuê, planId phải là YEARLY, slotCount=1; thành công trả HTTP 201 kèm data |
| GET /api/renter/bookings | Renter | Danh sách; query page, size, group, keyword, sort, status tùy nhu cầu |
| GET /api/renter/bookings/{code} | Renter | Chi tiết booking, thông tin xe và trạng thái tổng |
| GET /api/provider/bookings | Provider | Danh sách đơn của provider |
| GET /api/provider/bookings/{code} | Provider | Chi tiết đơn cần xử lý |
| POST /api/provider/bookings/{code}/accept | Provider | Chấp nhận đơn PENDING, không cần body |
| POST /api/provider/bookings/{code}/deny | Provider | Từ chối đơn PENDING; body {"reason":"Lý do cụ thể"} |

Body tạo đơn ví dụ:
```json
{
  "vehicleId": 301,
  "planId": 401,
  "slotCount": 1,
  "pickupTime": "2026-11-10T09:00:00+07:00",
  "pickupAddress": "Điểm nhận xe đã thống nhất",
  "pickupLatitude": 10.7733,
  "pickupLongitude": 106.7036,
  "passengerCount": 1,
  "expectedPickupFee": 0
}
```

Thay expectedPickupFee bằng báo giá vừa nhận. Backend tự tính hạn đúng 12 tháng và tổng giá từ plan; không tự gửi giá cuối cùng để áp đặt. Phải đáp ứng GPLX, thời gian đặt trước, lịch xe và không trùng lịch renter. plannedEndAt/pricingItems của flow ghép gói không dùng để thay thời hạn năm.

## 4. Đọc dữ liệu và chốt điều khoản

| Method / URL | Quyền | Body / kết quả |
|---|---|---|
| GET R/P/workflow | Đúng bên của đơn | View tổng: version, stage, contract, điều khoản, money, incidents, replacements |
| GET R/P/handover | Đúng bên của đơn | stage, dispatchedAt, arrivedAt, receivedOn, initialKm, evidenceUrl |
| GET R/P/payments | Đúng bên của đơn | Các khoản cọc, 12 kỳ và bổ sung nếu có; có thể chưa có khoản trước upload hợp đồng |
| GET R/schedule-preview | Renter | Lịch dự kiến: months[number,from,until,rent], tổng giá, bước làm tròn, phí sớm tối đa; scope=PREVIEW_ONLY_NOT_PAYMENT_REQUESTS |
| POST P/terms | Provider | Chỉ sau ACCEPTED, trước renter chốt; body bên dưới; trả View |
| POST R/terms/accept | Renter | {"version":1}; đồng ý điều khoản provider đã cấu hình, trả View |

Body POST P/terms:
```json
{"version":0,"annualKm":24000,"excessKmRate":5000,"graceDays":1,"lateDailyRate":500000}
```

annualKm là hạn mức cả năm; excessKmRate là đ/km vượt; graceDays là số ngày ân hạn nguyên; lateDailyRate là đ/ngày tính phí. Tất cả không âm. Giá/cọc lấy snapshot của booking, không gửi trong body này. RoundingUnit do backend cấu hình và được lưu vào chính sách.

Các trường cần đọc trong workflow:

| Nhóm | Trường chính |
|---|---|
| contract | totalRent, securityDeposit, plannedStart, actualStart, dueOn, currentVehicleId |
| Điều khoản | termsAccepted, annualKm, excessKmRate, graceDays, lateDailyRate, roundingUnit |
| Trả xe / km | returnMode, returnedOn, failureOn, currentInitialKm, finalKm, completedKm, totalKm |
| money | rentDue, earlyFee, lateFee, excessKmFee, otherCharges, rentPaid, depositHeld, depositDeduction, balanceDue, rentRefund, depositRefund, note |
| Minh chứng | returnEvidenceId, settlementEvidenceId, refundEvidenceId, refundReference |
| Bản ghi con | incidents và replacements; mỗi bản ghi có id, version, status riêng |

## 5. Upload hợp đồng đã ký

| Method / URL | Quyền | Cách dùng |
|---|---|---|
| GET /api/renter/bookings/{code}/contract/status | Renter | contractUploaded, canUpload, canDownload, restrictionReason |
| PUT /api/renter/bookings/{code}/contract | Renter | multipart file=PDF; chỉ khi ACCEPTED và còn hold, điều khoản phải đủ để tạo lịch tiền |
| GET /api/renter/bookings/{code}/contract | Renter | Tải PDF binary, không đọc như JSON |

Upload thành công trả data `{"bookingCode":"...","contractUploaded":true}`. Booking chuyển sang AWAITING_DEPOSIT; hệ thống tạo khoản cọc riêng và đủ 12 kỳ tiền thuê. Không tự gọi API tạo cọc thủ công hoặc dùng schedule-preview như danh sách khoản phải thu.

## 6. Thanh toán cọc, từng tháng và bổ sung

| Method / URL | Bên | Cách dùng |
|---|---|---|
| GET R/P/payments | Renter/provider | Lấy paymentRequestId; mỗi dòng có purpose, installmentNumber, periodStart, periodEnd, amount, paidAmount, status, transferReference, dueOn, overdue, remainingAmount |
| GET /api/payment-requests/{id} | Đúng bên của đơn | Chi tiết, tài khoản nhận tiền, QR và các biên lai |
| POST /api/payment-requests/{id}/proofs/multipart | Renter | file bắt buộc; transferredAmount, transactionReference, transferredAt, note tùy chọn theo controller |
| GET /api/payment-evidences/{id}/file | Đúng bên được cấp quyền | Ảnh biên lai binary, phải gửi Authorization |
| POST /api/provider/payment-requests/{id}/confirm | Provider của đơn | Ghi nhận số tiền thực nhận, body bên dưới |
| POST /api/provider/payment-requests/{id}/reject | Provider của đơn | {"reason":"Biên lai chưa khớp giao dịch thực tế"} |
| GET /api/bookings/{bookingId}/payment-history | Bên có quyền | Lịch sử; bookingId là ID số, không phải bookingCode |

Body confirm:
```json
{
  "receivedAmount":15100000,
  "bankTransactionRef":"DEMO-BANK-MONTH-04",
  "note":"Đã kiểm tra tài khoản nhận",
  "confirmationId":"9ae93c11-8b84-407a-b0a4-a28aa5d370f4"
}
```

receivedAmount nếu nhập tối thiểu 1.000 đ. Nên gửi rõ số thực nhận; không coi upload ảnh là đã thanh toán thành công. Giữ confirmationId khi gửi lại cùng một lần nhận tiền.

purpose=SECURITY_DEPOSIT là cọc; MONTHLY_INSTALLMENT kèm installmentNumber 1–12 là tiền thuê; ADJUSTMENT là khoản bổ sung sau quyết toán. Kiểm tra purpose, không suy luận cọc chỉ dựa vào paymentType=DEPOSIT ở API dùng chung.

Cọc + tháng 1 phải được xác nhận trước khi giao xe hoặc trả trước các tháng sau. Các khoản phải thu mới của flow năm dùng chuyển khoản trực tiếp; không dùng VNPay/simulate để bỏ qua đối soát. Cọc không cộng thành doanh thu tiền thuê.

## 7. Upload ảnh minh chứng nghiệp vụ

| Method / URL | Bên | Body / kết quả |
|---|---|---|
| POST /api/bookings/{code}/evidences/upload | Renter/provider thuộc đơn | multipart: file, type=OTHER, note tùy chọn; trả TripEvidenceResponse có id và imageUrl |
| GET /api/bookings/{code}/evidences | Renter/provider thuộc đơn | Danh sách ảnh dùng để xem lại |

Upload trước, lấy data.id đưa vào evidenceId/oldEvidenceId/newEvidenceId của API nghiệp vụ tiếp theo. Ảnh phải thuộc đúng booking và đúng người có quyền; các minh chứng provider phải do provider upload khi service yêu cầu. Không dùng paymentEvidenceId thay tripEvidenceId.

Không dùng DELETE ảnh làm thao tác thường của flow năm: minh chứng hợp đồng năm được giữ để đối soát. Ảnh minh chứng khác với PDF hợp đồng và ảnh GPLX; không gộp chúng vào một endpoint.

## 8. Giao xe và renter xác nhận

| Method / URL | Bên | Body | Điều kiện / tác dụng |
|---|---|---|---|
| POST P/handover/dispatch | Provider | Không cần | Điều khoản đã chấp nhận, booking CONFIRMED, cọc + tháng 1 đã đủ; trả Handover |
| POST P/handover/arrive | Provider | {"initialKm":10000,"evidenceId":501} | Đang giao hoặc đã đến nhưng chưa nhận; ghi km và ảnh, trả Handover |
| POST R/handover/receive | Renter | Không cần | Xe ARRIVED; xác nhận đã kiểm tra và nhận, trả Handover |

Thứ tự NOT_DISPATCHED → DISPATCHED → ARRIVED → RECEIVED. Receive chốt actualStart, dueOn sau 12 tháng, kiểm tra GPLX/lịch xe lại và cập nhật mốc 12 kỳ. Không truyền version hợp đồng vào các endpoint handover này vì DTO không yêu cầu.

## 9. Trả xe, kiểm định và quyết toán

| Method / URL | Bên | Body mẫu | Kết quả |
|---|---|---|---|
| POST R/returns/preview | Renter | {"mode":"EARLY","returnedOn":"2026-11-07"} | Preview {returnedOn,money,scope:"ESTIMATE_ONLY"}, không đổi trạng thái |
| POST R/returns | Renter | {"version":3,"mode":"EARLY"} | View, RETURN_REQUESTED |
| POST P/returns/inspection | Provider | {"version":4,"returnedOn":"2026-11-07","finalKm":10500,"evidenceId":502} | View, RETURN_INSPECTED |
| POST R/returns/confirm | Renter | {"version":5} | View, RETURNED; booking AWAITING_FINAL_PAYMENT, ghi nhận xe đã trả |
| POST P/settlement | Provider | {"version":6,"otherCharges":0,"depositDeduction":0,"note":"Không có hư hỏng thêm","evidenceId":null} | View, SETTLEMENT_PROPOSED; hệ thống tính tiền |
| POST R/settlement/accept | Renter | {"version":7} | View, SETTLEMENT_ACCEPTED; tạo ADJUSTMENT nếu còn thiếu, dừng các kỳ chưa thu |

mode của renter:
- EARLY: giữ tiền các tháng đã bắt đầu + phí sớm bằng trung bình năm làm tròn lên, tối đa tiền thuê tương lai còn lại.
- STANDARD: trả theo hạn; không dùng trước ngày đến hạn.
- FULL_CONTRACT: trả xe và giữ nghĩa vụ toàn bộ tiền thuê năm, không thêm phí sớm.
- PROVIDER_FAILURE không phải giá trị renter được gửi; hệ thống đặt khi provider chấp nhận report kết thúc do lỗi provider.

Ngày preview chỉ để ước tính. Ngày cuối cùng được provider ghi ở inspection, không gửi returnedOn vào returns. Ngày inspection không trước ngày nhận và không sau hôm nay; finalKm không nhỏ hơn mốc nhận hiện tại.

Preview chưa gồm kiểm định/km vượt/phụ phí. Khi settlement, hệ thống tự tính rentDue/earlyFee/lateFee/excessKmFee, không nhận các số này từ client. otherCharges và depositDeduction không âm; có phụ phí khác cần note + ảnh; khấu trừ cọc không vượt cọc hoặc tính trùng nghĩa vụ đã thanh toán. Renter cần đồng ý trước khi xử lý bổ sung/hoàn tiền.

## 10. Hoàn tiền, hoàn tất và mở lại lịch xe

| Method / URL | Bên | Body mẫu | Điều kiện / tác dụng |
|---|---|---|---|
| POST P/refund/transfer | Provider | {"version":8,"bankReference":"DEMO-REFUND-001","evidenceId":503} | Đã đồng ý quyết toán, khoản bổ sung đã đủ; ghi bằng chứng chuyển tiền, REFUND_TRANSFER_RECORDED |
| POST R/refund/confirm | Renter | {"version":9} | Xác nhận đã nhận tiền; ghi các khoản hoàn tiền thuê/cọc riêng, REFUND_RECEIVED |
| POST P/complete | Provider | {"version":10} | Đủ tiền, tiền hoàn đã xác nhận nếu có, report đã đóng; COMPLETED |
| POST P/reopen | Provider | {"version":11,"availableOn":"2026-11-08","vehicleId":301} | Mở lịch xe đã kiểm định từ ngày mai trở đi; vehicleId có thể bỏ để dùng xe hiện tại |

bankReference tối đa 80 ký tự, evidenceId bắt buộc. API transfer chỉ ghi nhận giao dịch bên ngoài, không tự chuyển tiền ngân hàng. Nếu không có tiền cần hoàn thì không tạo giao dịch hoàn giả; complete kiểm tra nghĩa vụ thực tế. Xe mở lại phải thuộc lịch sử hợp đồng và đã đủ điều kiện kiểm định, không chọn xe bất kỳ.

## 11. Báo sự cố và xử lý chi phí trực tiếp

| Method / URL | Bên | Body mẫu | Response |
|---|---|---|---|
| POST R/incidents | Renter | {"requestId":"0fcdaed6-bbcb-4a36-9496-3a85c7fe2381","category":"OWNER_MAINTENANCE","occurredOn":"2026-11-07","description":"Lỗi hao mòn, đề nghị kiểm tra","evidenceId":504,"invoiceAmount":1000000} | Incident |
| POST P/incidents/{id}/resolution | Provider | {"version":0,"outcome":"REIMBURSE","note":"Chấp thuận hóa đơn bảo trì","approvedAmount":1000000} | Incident |
| POST P/incidents/{id}/transfer | Provider | {"version":1,"bankReference":"DEMO-MAINT-001","evidenceId":505} | Incident |
| POST R/incidents/{id}/confirm | Renter | {"version":2} | Incident đã đóng nếu đủ điều kiện |

category:
- OWNER_MAINTENANCE: hao mòn/lỗi phía provider.
- RENTER_DAMAGE: hư hỏng do renter.

outcome:
- FIXED: đã sửa, tiếp tục thuê; approvedAmount=0.
- REIMBURSE: provider hoàn trực tiếp khoản đã duyệt, không vượt invoiceAmount.
- RENTER_PAID: chỉ cho RENTER_DAMAGE; renter đã tự trả, approvedAmount=0.
- END_PROVIDER: chỉ cho lỗi phía provider; chuyển workflow sang yêu cầu nhận lại xe, không có phí tự nguyện trả sớm.
- REPLACE: chỉ cho lỗi phía provider; xử lý bằng các API đổi xe, không đóng bằng incidents/{id}/confirm.

Chỉ một report chưa đóng mỗi booking; occurredOn nằm từ ngày nhận xe đến hôm nay. Bằng chứng đã dùng cho report khác không được tái dùng để tạo report mới. Phương án khác REIMBURSE phải nhập approvedAmount=0.

REIMBURSE có tiền: AWAITING_TRANSFER → provider ghi chuyển → TRANSFER_RECORDED → renter confirm → CLOSED. Sửa xong/tự trả/kết thúc provider: renter xác nhận kết quả sau AWAITING_CONFIRMATION. END_PROVIDER cần hoàn tất cả tháng xảy ra sự cố và các tháng tương lai đã trả khi quyết toán, cọc riêng. Khoản hoàn bảo trì không bù vào kỳ thuê sau.

## 12. Đổi xe cùng provider

Lấy xe của provider bằng `GET /api/provider/vehicles?page=0&size=100&status=APPROVED`. Danh sách này không bảo đảm còn trống; API tạo/xác nhận đề nghị sẽ kiểm tra lịch lại.

| Method / URL | Bên | Body mẫu | Response |
|---|---|---|---|
| POST P/replacements | Provider | {"incidentId":601,"vehicleId":302,"plannedOn":"2026-11-07"} | Replacement, PROPOSED |
| POST R/replacements/{id}/accept | Renter | {"version":0} | Replacement, ACCEPTED |
| POST P/replacements/{id}/inspection | Provider | {"version":1,"exchangedOn":"2026-11-07","oldFinalKm":10500,"newInitialKm":20000,"oldEvidenceId":506,"newEvidenceId":507} | Replacement, HANDOVER_PROPOSED |
| POST R/replacements/{id}/confirm | Renter | {"version":2} | Replacement, COMPLETED; đóng report liên quan |
| POST R/P/replacements/{id}/cancel | Một bên của đơn | {"version":1} | Replacement, CANCELLED; report mở lại để provider xử lý phương án khác |

Phải có report REPLACE hợp lệ; xe mới cùng provider, đã duyệt, đáp ứng GPLX và trống lịch. Chỉ renter confirm mới áp dụng đổi xe: cộng quãng đường xe cũ, đổi mốc km cho xe mới, cập nhật xe/lịch và đánh dấu xe cũ chờ kiểm định. Giữ giá/cọc/hạn, không tạo hợp đồng 12 tháng mới. Không cộng ngày vì giao xe thay thế trễ trong scope hiện tại.

## 13. Ví dụ gọi bằng Postman/curl

Trong Postman tạo biến baseUrl=http://localhost:8080/api, renterToken, providerToken, bookingCode; chọn đúng Bearer token mỗi request. Body JSON ở chế độ raw/JSON; upload ở form-data, cột file chọn loại File. Không tự nhập Content-Type multipart vì client phải tạo boundary.

PowerShell dùng curl.exe; thay token và đường dẫn file trên máy:
```powershell
$base = 'http://localhost:8080/api'
$code = 'EM-Y2-MONTHLY'
curl.exe "$base/renter/yearly-bookings/$code/workflow" -H "Authorization: Bearer $env:RENTER_TOKEN"
curl.exe -X POST "$base/bookings/$code/evidences/upload" -H "Authorization: Bearer $env:PROVIDER_TOKEN" -F "file=@D:/demo/kiem-dinh.jpg" -F "type=OTHER" -F "note=Anh kiem dinh demo"
```

Dùng data.id của ảnh vừa upload khi gửi inspection. Đối với JSON có nhiều dấu nháy trong PowerShell, lưu body vào một file JSON rồi dùng `--data-binary @request.json`; tránh sửa số version bằng cách đoán.

## 14. Thứ tự gọi và xử lý lỗi

Luồng chính: đọc GPLX → tạo booking → provider accept → terms → renter accept terms → PUT hợp đồng → gửi/confirm cọc và tháng 1 → dispatch → upload ảnh/arrive → receive → thanh toán các tháng → returns → inspection → renter confirm → settlement → renter accept → bổ sung nếu cần → refund transfer/confirm nếu có → complete → reopen.

Nhánh sự cố diễn ra khi đang thuê, trước yêu cầu trả tự nguyện; đọc lại workflow sau khi xử lý report để biết tiếp tục thuê, đổi xe hay nhận lại xe do lỗi provider.

| Tình huống | Cách xử lý |
|---|---|
| 400 / validation | Kiểm tra field, định dạng ngày, tiền và multipart |
| 401 | Đăng nhập/refresh token theo cơ chế chung |
| 403 | Kiểm tra vai trò và quyền trên booking; không đổi userId để thử vượt quyền |
| 404 | Kiểm tra code/ID, hợp đồng/ảnh tồn tại; payments có thể chưa tạo trước upload hợp đồng |
| 409 / xung đột nghiệp vụ | Đọc message, tải lại workflow, lấy đúng version/trạng thái/lịch |
| Lỗi đọc giấy tờ | Kiểm tra message từ Spring/DocumentAI và ảnh đầu vào; không giả READ_SUCCESS |
| Lỗi sau khi gửi mutation | Đọc lại trạng thái trước retry để tránh ghi tiền hai lần |

Stage hợp đồng: TERMS_DRAFT → TERMS_ACCEPTED (API hiển thị ACTIVE sau khi nhận xe) → RETURN_REQUESTED → RETURN_INSPECTED → RETURNED → SETTLEMENT_PROPOSED → SETTLEMENT_ACCEPTED → REFUND_TRANSFER_RECORDED → REFUND_RECEIVED → COMPLETED. Nhánh không cần hoàn có thể hoàn tất sau SETTLEMENT_ACCEPTED khi đủ điều kiện.

Booking status, handover stage, payment status, report status và replacement status là các trạng thái riêng. Không lấy booking IN_PROGRESS để suy ra đã trả hết tiền hoặc COMPLETED của replacement để suy ra đã hoàn tất hợp đồng.

## 15. Đối chiếu source

- booking/controller: YearlyWorkflowController, YearlyHandoverController, YearlyRentalController, RenterBookingController, ProviderBookingController, BookingContractController, BookingEvidenceController.
- booking/service: YearlyWorkflowService và DTO record; YearlyRentalQuoteService.
- user/controller: RenterDrivingLicenseController.
- finance/controller: PaymentRequestController; finance/dto/request/ConfirmPaymentRequest.
- FE: services/yearlyWorkflowService.js, yearlyRentalService.js, providerFinanceService.js và apiClient.js.

Tài liệu mô tả code hiện tại, không phải báo cáo đã gọi thành công mọi endpoint trên database thật. Không dùng các route đề xuất cũ như /terms GET hay /installments thay cho /workflow và /payments đang triển khai.


Chính sách GPLX demo hiện tại: chấp nhận B, B1 và B2, kể cả GPLX cấp trước năm 2025. Vẫn cần kết quả đọc thành công, đủ tuổi, xe tối đa 9 chỗ và GPLX còn hạn đến hết thời gian thuê. Đây là phạm vi demo, không phải đối chiếu đầy đủ các hạn chế theo từng hạng.


Giới hạn ngày nhận xe: GET /api/renter/bookings/start-policy trả maxAdvanceMonths và latestPickupTime theo cấu hình BE. Form năm dùng mốc này để đặt max và chặn bước tiếp theo; mặc định là 3 tháng, không phải 3 năm. POST /api/renter/bookings/pickup-quote hỗ trợ YEARLY để báo giá điểm nhận; báo giá không thay thế bước ký hợp đồng và không tạo booking.

Gói năm chưa cấu hình bán kính/đơn giá giao xe dùng giá trị mặc định 0; phí đón bằng 0 nếu chưa có đơn giá. Gói có đơn giá đã cấu hình vẫn tính theo tuyến đường. Báo giá và tạo booking dùng chung cách xử lý này; vẫn cần dịch vụ tuyến đường hoạt động.


Ảnh minh chứng giao/nhận, sự cố, hóa đơn và hoàn tiền của gói năm được lưu local qua FileStorageService (app.upload.provider=local, app.upload.dir giống ảnh GPLX). POST /api/bookings/{code}/evidences/upload nhận JPG/PNG, tên UUID; GET /api/bookings/{code}/evidences/{id}/file trả binary với kiểm tra quyền và no-store. Client cần gửi Bearer token, không mở URL local bảo vệ trực tiếp bằng img không có Authorization. Không cần Cloudinary cho luồng minh chứng này.
