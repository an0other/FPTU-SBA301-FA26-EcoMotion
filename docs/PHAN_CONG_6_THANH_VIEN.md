# Phân công 6 thành viên — EcoMotion

Nguồn: `docs/SBA301_FALL26.md` · `docs/GIAI_THICH_ENTITY_v10.1.md` · `AGENT_CONTEXT.md`  
**27 màn** · FE + BE theo lane · không thêm bảng ngoài `schema.sql`

### Quy tắc
1. Tính tiền sau đặt → `BOOKING_POLICY_SNAPSHOT`
2. API public dùng `booking_code`
3. Chỉ gọi service module khác (không gọi repository)
4. Auth `/api/auth/*` đã có — không viết lại
5. Redirect: `RENTER`→R01 · `PROVIDER`→O01 · `DRIVER`→D01 · `ADMIN`→A01

### Luồng chính
```
R07 → PENDING + SNAPSHOT (chưa thu tiền)
O08 Accept → ACCEPTED | Deny → REJECTED (không lý do)
R08 → DEPOSIT_PAY / UPFRONT_PAY → CONFIRMED + BOOKED
D02 / R10 → IN_PROGRESS → cả hai xác nhận → COMPLETED
```

---

## Phân công theo TV

### TV1 — R01, R02, R03, R05, R06
R01: Home - Renter - List xe APPROVED, giá min từ plan  
R02: Search - Renter - Lọc xe + list kết quả (map mock tùy chọn)  
R03: Vehicle Detail - Renter - Ảnh, gói giá, lịch trống, hotline, review, CTA đặt  
R05: Login - Tất cả - Đăng nhập, redirect theo role  
R06: Register - Renter/Provider - Đăng ký tài khoản, eKYC mock PENDING  

### TV2 — R07, R08, R09, R10
R07: Booking request - Renter - Chọn gói + điểm đón → PENDING + snapshot (chưa thu tiền)  
R08: Checkout - Renter - Trả cọc/trả trước sau khi chủ Accept → khóa lịch  
R09: My Bookings - Renter - Danh sách đơn theo trạng thái (`booking_code`)  
R10: Booking Detail - Renter - Timeline, huỷ, dispute; đang chạy → xác nhận kết thúc  

### TV3 — R12, R14, R16
R12: Payment History - Renter - Lịch sử giao dịch + breakdown charge  
R14: Gói năm - Renter - Khai GPLX + upload hợp đồng + CTA đặt năm  
R16: Review - Renter - Đánh giá xe và/hoặc tài xế sau COMPLETED  

### TV4 — O01, O02, O03, O04, O05, O06
O01: Owner home - Provider - Số đếm nhanh + link (không chart)  
O02: My Vehicles - Provider - List xe + badge trạng thái duyệt  
O03: Vehicle form - Provider - Tạo/sửa xe + ảnh, gửi duyệt  
O04: Vehicle hub - Provider - Giấy tờ, gói giá, xem lý do từ chối  
O05: Chặn lịch - Provider - BLOCKED / MAINTENANCE (không mở bán thủ công)  
O06: Drivers - Provider - Tạo hộ tài xế (không gán sẵn vào xe)  

### TV5 — O07, O08, O11, D01, D02
O07: Bookings - Provider - Hàng đợi đơn + filter trạng thái  
O08: Booking detail - Provider - Accept/Deny, gán tài xế, phụ phí tay  
O11: Finance - Provider - STK nhận tiền + list PAYOUT (mock)  
D01: Driver schedule - Driver - Lịch chuyến được phân công  
D02: Trip detail - Driver - Đến đón → bắt đầu → kết thúc (`actual_*`)  

### TV6 — A01, A02, A03, A04
A01: Admin home - Admin - Số đếm + link khu vực làm việc  
A02: Users - Admin - Duyệt provider/driver/eKYC, khóa user  
A03: Duyệt xe - Admin - Approve/Reject xe + lý do (không đòi tài xế)  
A04: Operations - Admin - Tab Đơn · Dispute · Thanh toán (refund mock)  

`5+4+3+6+5+4 = 27`

**Không còn page riêng:** R04, R11, R13, R15, O09, O10, D03, B01, A05, A06, A07

---

## Phụ thuộc E2E

TV4 đăng xe/tài xế → TV6 duyệt → TV1 hiện xe → TV2 đặt → TV5 accept → TV2 checkout → TV5 chạy chuyến → TV3 review/tiền

FE + API trong lane làm song song với seed; nối full flow theo thứ tự trên.
