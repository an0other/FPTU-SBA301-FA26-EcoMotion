# **GIẢI THÍCH TỪNG ENTITY — SƠ ĐỒ KHÁI NIỆM v10.1**

*Tài liệu đi kèm `Conceptual_v10.1.mmd` · 15 thực thể · 25 quan hệ.*

*Mục đích: mỗi entity trả lời 5 câu — **để làm gì**, **trường nào quan trọng**, **nối với ai**, **luật nghiệp vụ nào**, **bẫy khi code**.*

---

## **SƠ ĐỒ TỔNG QUAN 4 TẦNG**

```
TẦNG 1 — CON NGƯỜI          USER ─┬─ PROVIDER
                                 ├─ RENTER
                                 └─ DRIVER

TẦNG 2 — TÀI SẢN & SẢN PHẨM  PROVIDER ─ VEHICLE ─┬─ VEHICLE_IMAGE
                                                 ├─ VEHICLE_DOCUMENT
                                                 ├─ RENTAL_PLAN
                                                 └─ VEHICLE_SCHEDULE

TẦNG 3 — GIAO DỊCH           RENTER + VEHICLE + RENTAL_PLAN + DRIVER ─ BOOKING
                             BOOKING ─┬─ BOOKING_POLICY_SNAPSHOT   (LUẬT)
                                      ├─ BOOKING_CHARGE            (KẾT QUẢ)
                                      ├─ PAYMENT_TRANSACTION       (TIỀN)
                                      └─ VEHICLE_SCHEDULE          (LỊCH)

TẦNG 4 — SAU CHUYẾN          BOOKING ─┬─ REVIEW      (đánh giá xe / tài xế)
                                      └─ DISPUTE     (tranh chấp)
```

**Ba tầng tiền — không chồng nhau (đây là điểm dễ hiểu sai nhất):**

| Bảng                      | Vai trò     | Trả lời câu hỏi                       | Ai tạo                              |
| ------------------------- | ----------- | ------------------------------------- | ----------------------------------- |
| `BOOKING_POLICY_SNAPSHOT` | **LUẬT**    | *Tính bằng công thức nào?*            | Hệ thống, lúc khách gửi yêu cầu     |
| `BOOKING_CHARGE`          | **KẾT QUẢ** | *Phải trả những khoản gì, bao nhiêu?* | Hệ thống tự sinh + provider ghi tay |
| `PAYMENT_TRANSACTION`     | **TIỀN**    | *Đã chuyển thật bao nhiêu, lúc nào?*  | Cổng thanh toán / ghi nhận thủ công |

---

# **TẦNG 1 — CON NGƯỜI**

## **1. `USER` — Tài khoản đăng nhập**

**Để làm gì:** Bảng gốc của mọi người dùng. Một tài khoản = một email = **một vai trò chính**. Đây là bảng duy nhất lưu thông tin xác thực (đăng nhập).

**Trường quan trọng:**

| Trường                 | Ý nghĩa                                                                                          |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| `email` / `phone` (UK) | Định danh đăng nhập. Unique để chống trùng tài khoản                                             |
| `password_hash`        | Không bao giờ lưu plaintext                                                                      |
| `role`                 | `RENTER \| PROVIDER \| DRIVER \| ADMIN` — quyết định hồ sơ nào được gắn vào                      |
| `status`               | `ACTIVE \| LOCKED \| PENDING` — `PENDING` là **chưa xác thực eKYC**, không phải chưa duyệt hồ sơ |
| `ekyc_status`          | `UNVERIFIED \| PENDING \| VERIFIED \| REJECTED` — xác thực danh tính cấp tài khoản               |

**Quan hệ:** `1-0..1` tới `PROVIDER`, `RENTER`, `DRIVER`; `1-n` tới `DISPUTE` (mở đơn / xử lý đơn).

**Luật nghiệp vụ:**

- **Muốn vừa thuê vừa cho thuê → phải đăng ký 2 tài khoản** (2 email). Không có cơ chế nhiều vai trò trên một tài khoản.
- Mỗi nhà cung cấp chỉ có **một tài khoản duy nhất** — không có nhân viên dùng chung hồ sơ provider.

**Bẫy:** `status` và `ekyc_status` là hai trục khác nhau, đừng gộp. Một tài khoản có thể `status=ACTIVE` nhưng `ekyc_status=UNVERIFIED`.

---

## **2. `PROVIDER` — Nhà cung cấp (chủ xe / doanh nghiệp cho thuê)**

**Để làm gì:** Hồ sơ nghiệp vụ của bên cung cấp — nơi chứa **chính sách giá ở cấp nhà xe** (hoa hồng, % cọc) và **thông tin nhận tiền**.

**Trường quan trọng:**

| Trường                            | Ý nghĩa                                                             |
| --------------------------------- | ------------------------------------------------------------------- |
| `user_id` (FK, UK)                | 1-1 với `USER.role=PROVIDER`                                        |
| `provider_type`                   | `INDIVIDUAL` (chủ xe cá nhân) | `BUSINESS` (doanh nghiệp thuê xe)   |
| `depot_address`                   | **Gốc để tính bán kính phục vụ** — không phải địa chỉ hành chính    |
| `commission_rate`                 | Hoa hồng nền tảng giữ lại                                           |
| `deposit_ratio`                   | **% cọc do nhà xe tự đặt** — CHỈ áp cho gói giờ/ngày/tháng          |
| `bank_*`                          | Thông tin nhận chi trả (đối soát payout)                            |
| `business_license_no`, `tax_code` | **Chỉ bắt buộc khi `BUSINESS`** → hai cột này null với `INDIVIDUAL` |
| `status`                          | `PENDING \| APPROVED \| LOCKED`                                     |
| `rating_avg`, `rating_count`      | **Cache** — xem bẫy bên dưới                                        |

**Quan hệ:** `1-n` tới `VEHICLE`, `DRIVER`.

**Luật nghiệp vụ:**

- `deposit_ratio` **KHÔNG áp cho gói năm** — gói năm dùng `upfront_amount` (số tuyệt đối) thay cọc.
- `rating_avg` ở đây là **dẫn xuất bậc 2**: gộp review của **xe** + review của **tài xế** thuộc nhà xe này.

**Bẫy:**

1. Có **3 bảng cùng cache rating** (`PROVIDER`, `VEHICLE`, `DRIVER`). Một review mới phải cập nhật **đủ 3** — lệch một chỗ là 3 con số khác nhau. **Nguồn sự thật vẫn là `REVIEW`**; cache chỉ để đọc nhanh.
2. Vì rating của provider là bậc 2, **con số sẽ nhảy khi danh mục xe thay đổi** (thêm/bớt xe, xe bị HIDDEN). Đây là hành vi đúng, không phải bug.

---

## **3. `RENTER` — Người thuê (khách hàng cá nhân)**

**Để làm gì:** Hồ sơ định danh của khách. **Chỉ cá nhân** — nền tảng đã bỏ B2B ở phía cầu.

**Trường quan trọng:**

| Trường                                          | Ý nghĩa                                                        |
| ----------------------------------------------- | -------------------------------------------------------------- |
| `user_id` (FK, UK)                              | 1-1 với `USER.role=RENTER`                                     |
| `cccd_no`, `cccd_image_url`                     | CMND/CCCD gắn chip — **bắt buộc** để thuê                      |
| `license_no`, `license_class`, `license_expiry` | **Chỉ cần cho gói năm** (tự lái dài hạn)                       |
| `license_verified`                              | **Cờ mở khoá gói năm** — chưa duyệt thì không được đặt gói năm |

**Quan hệ:** `1-n` tới `BOOKING`.

**Luật nghiệp vụ:** Khách thuê ngắn hạn (giờ/ngày/tháng) **không cần GPLX** vì đã có tài xế của nhà xe. Chỉ gói năm mới cần — vì lúc đó xe bàn giao cho khách và **tài xế không tham gia**.

**Bẫy:** Bốn cột `license_*` **nullable theo thiết kế**. Đừng đặt `NOT NULL` — sẽ chặn toàn bộ khách thuê ngắn hạn.

---

## **4. `DRIVER` — Tài xế**

**Để làm gì:** Hồ sơ tài xế thuộc **đúng một** nhà cung cấp. Đây là entity có **vòng đời duyệt phức tạp nhất** trong sơ đồ.

**Trường quan trọng:**

| Trường                                            | Ý nghĩa                                                                |
| ------------------------------------------------- | ---------------------------------------------------------------------- |
| `user_id` (FK, UK)                                | 1-1 với `USER.role=DRIVER`                                             |
| `provider_id` (FK)                                | Thuộc **đúng một** nhà cung cấp — không chia sẻ tài xế giữa các nhà xe |
| `license_no` / `license_class` / `license_expiry` | GPLX + hạn — dùng để **cảnh báo sắp hết hạn**                          |
| `portrait_url`, `license_image_url`               | Ảnh chân dung + ảnh GPLX (bắt buộc hồ sơ)                              |
| `status`                                          | `PENDING \| APPROVED \| REJECTED \| SUSPENDED`                         |
| `reject_reason`                                   | **Lý do admin từ chối** → nhà xe đọc, sửa, gửi lại                     |
| `rating_avg`, `rating_count`                      | Cache từ `REVIEW`                                                      |

**Quan hệ:** `1-n` tới `BOOKING` (được phân công), `1-n` tới `REVIEW` (được đánh giá).

**Luồng nghiệp vụ (khác mọi hệ thống thông thường):**

```
Nhà xe TẠO tài khoản hộ (nhập email/GPLX/ảnh)
      → USER.role=DRIVER + DRIVER.status=PENDING
      → ADMIN duyệt → APPROVED (mới hoạt động)
      → Nhà xe được SỬA / VÔ HIỆU HOÁ (SUSPENDED) tài xế mình tạo
```

**Luật nghiệp vụ — điểm cốt lõi:**

- **Sửa BẤT KỲ thông tin nào của hồ sơ đã duyệt → tụt về `PENDING`, admin duyệt lại.** Không có ngoại lệ phân tầng (khác `VEHICLE` — xem entity #5).
- Lý do: **tài xế là định danh con người**, không phải tài sản có phân tầng trường.
- Tài xế bị vô hiệu hoá → **phải tạo tài khoản mới**, không kích hoạt lại tài khoản cũ.
- Nhà xe mất tài xế → **hệ thống không can thiệp**, chỉ ghi vết ở `BOOKING.cancel_reason = PROVIDER_NO_DRIVER`.

**Bẫy:**

1. `PENDING` ≠ `REJECTED`. `PENDING` = đang chờ / mới sửa lại. `REJECTED` = admin đã từ chối, có `reject_reason`.
2. **Không có `approved_by` / `approved_at`** — admin là người duyệt duy nhất nên cột đó là hằng số, không mang thông tin.
3. `SUSPENDED` là do **nhà xe** làm, `REJECTED` là do **admin** làm. Hai nguồn quyền khác nhau.

---

# **TẦNG 2 — TÀI SẢN & SẢN PHẨM**

## **5. `VEHICLE` — Xe**

**Để làm gì:** Entity trung tâm của nền tảng. Khách **chọn đúng một chiếc xe cụ thể** (xem ảnh, biển số, đánh giá) — đây là điểm khác biệt cốt lõi so với gọi xe công nghệ.

**Trường quan trọng — chia 3 tầng (quy tắc quan trọng nhất của entity này):**

| Tầng                      | Trường                                                                                                                         | Được sửa?                                                 |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------- |
| **1 — Định danh vật lý**  | `chassis_no` (UK), `engine_no` (UK)                                                                                            | **BẤT BIẾN.** Sai thì phải **tạo xe mới**                 |
| **2 — Định danh pháp lý** | `license_plate` (UK), giấy tờ xe (`VEHICLE_DOCUMENT`)                                                                          | **Sửa được nhưng PHẢI DUYỆT LẠI** (gắn chứng từ nhà nước) |
| **3 — Mô tả**             | `brand`, `model`, `manufacture_year`, `color`, `seat_count`, `transmission`, `fuel_type`, `maintenance_buffer_minutes`, ảnh xe | **Sửa tự do, KHÔNG duyệt lại**                            |

Các trường khác: `provider_id` (chủ sở hữu), `status` (`PENDING | APPROVED | REJECTED | HIDDEN`), `rating_avg` / `rating_count` (cache), `maintenance_buffer_minutes` (**đệm bảo trì giữa 2 booking liên tiếp**).

**Quan hệ:** `1-n` tới `VEHICLE_IMAGE`, `VEHICLE_DOCUMENT`, `RENTAL_PLAN`, `VEHICLE_SCHEDULE`, `BOOKING`, `REVIEW`.

**Luật nghiệp vụ:**

- `HIDDEN` = nhà xe tự ẩn khỏi danh sách (khác `REJECTED` = admin từ chối).
- Đệm bảo trì: `grace hiệu dụng = min(allowed_overtime_minutes, maintenance_buffer_minutes)` — nếu không, khách vượt trong grace sẽ đâm vào lịch người sau.

**Bẫy:**

1. **`license_plate` KHÔNG bất biến.** Nó là định danh **pháp lý** → sửa được + duyệt lại. Chỉ `chassis_no` / `engine_no` bất biến.
2. Trong bảng `BOOKING`, `vehicle_id` là **MUTABLE** (đổi được khi dời xe) → kéo theo `VEHICLE_SCHEDULE` phải cập nhật theo.
3. **Xe được review trực tiếp** (không chỉ qua booking) → `VEHICLE` có rating riêng, độc lập với rating của tài xế.

---

## **6. `VEHICLE_IMAGE` — Ảnh xe**

**Để làm gì:** Thư viện ảnh của xe. Tách bảng vì **số lượng không giới hạn**.

**Trường quan trọng:** `vehicle_id` (FK), `image_url`, `display_order` (**`0` = ảnh bìa**), `caption`.

**Quan hệ:** `n-1` với `VEHICLE`.

**Luật nghiệp vụ:** Ảnh bìa (`display_order=0`) là ảnh hiển thị trong kết quả tìm kiếm.

**Bẫy:** Không có `is_cover` boolean — **dùng `display_order=0` làm ảnh bìa**. Query lấy bìa: `WHERE display_order = 0`.

---

## **7. `VEHICLE_DOCUMENT` — Giấy tờ xe**

**Để làm gì:** Lưu **4 loại giấy tờ**, **mỗi loại có hạn riêng** → tách bảng để query cảnh báo hết hạn ở **một chỗ**.

**Trường quan trọng:**

| Trường        | Ý nghĩa                                                                                                               |
| ------------- | --------------------------------------------------------------------------------------------------------------------- |
| `doc_type`    | `REGISTRATION` (đăng ký) | `INSPECTION` (đăng kiểm) | `INSURANCE` (bảo hiểm) | `BUSINESS_BADGE` (phù hiệu kinh doanh) |
| `doc_number`  | Số giấy tờ                                                                                                            |
| `expiry_date` | **`null` với loại không hết hạn** (ví dụ đăng ký xe)                                                                  |
| `status`      | `PENDING \| VERIFIED \| REJECTED \| EXPIRED`                                                                          |
| `file_url`    | File scan                                                                                                             |

**Quan hệ:** `n-1` với `VEHICLE`.

**Luật nghiệp vụ — vì sao tách bảng thay vì nhúng cột:**

> **Giấy tờ NGƯỜI (1–2 loại) nhúng cột vào bảng cha** (`RENTER.cccd_*`, `DRIVER.license_*`).  
> **Giấy tờ XE (4 loại, mỗi loại một hạn) tách bảng** → query cảnh báo hết hạn tập trung một chỗ.


**Bẫy:**

1. `expiry_date` **nullable theo thiết kế** (có loại không hết hạn) → đừng `NOT NULL`.
2. Giấy tờ xe **thay mới → duyệt lại** (tầng 2 — định danh pháp lý), khác ảnh xe (tầng 3 — sửa tự do).
3. `EXPIRED` là trạng thái **suy ra từ `expiry_date`** — cần job định kỳ cập nhật, không tự nhảy.

---

## **8. `RENTAL_PLAN` — Gói giá (niêm yết theo từng xe)**

**Để làm gì:** Bảng **giá + toàn bộ tham số tính tiền** của một xe. Đây là nguồn cho `BOOKING_POLICY_SNAPSHOT` chép sang lúc đặt.

**Trường quan trọng:**

| Nhóm      | Trường                                  | Ý nghĩa                                                  |
| --------- | --------------------------------------- | -------------------------------------------------------- |
| Định danh | `vehicle_id` (FK)                       | Gói giá **theo TỪNG XE**, không theo nhà xe              |
|           | `plan_type`                             | `HOURLY \| DAILY \| MONTHLY \| YEARLY`                   |
|           | `slot_length_minutes`                   | `60` (giờ) · `1440` (ngày) · tháng · năm                 |
|           | `slot_price`                            | Đơn giá 1 slot — **ĐÃ gồm chi phí tài xế**               |
|           | `upfront_amount`                        | **Gói năm — THAY cọc**, provider đặt theo xe             |
|           | `status`                                | `ACTIVE \| INACTIVE`                                     |
| Vượt giờ  | `allowed_overtime_minutes`              | Grace — vượt thì **làm tròn lên slot**                   |
|           | `overtime_surcharge_ratio`              | % phạt cho slot vượt                                     |
| Bán kính  | `free_radius_km`                        | Bán kính đón miễn phí (tính từ `PROVIDER.depot_address`) |
|           | `out_of_radius_rate`                    | Phụ phí ngoài bán kính **/ km**                          |
| Chờ       | `waiting_free_minutes` / `waiting_rate` | Chờ miễn phí / phụ phí chờ **/ phút**                    |
| Cuối tuần | `weekend_surcharge_ratio`               | Phụ thu cuối tuần / lễ Tết                               |
| Quy đổi   | `hourly_to_daily_threshold_minutes`     | Mốc quy đổi giá ngày — **chỉ gói giờ**                   |

**Quan hệ:** `n-1` với `VEHICLE`; `1-n` tới `BOOKING` (áp gói giá).

**Luật nghiệp vụ:**

- **Giá gói năm cố định suốt kỳ hạn** — không áp phụ phí vượt/chờ/cuối tuần.
- Gói năm dùng `upfront_amount` (số tuyệt đối) **thay** `deposit_ratio`.
- Vượt giờ = **làm tròn phần vượt thành slot, KHÔNG tạo booking mới**.

**Bẫy quan trọng nhất của cả sơ đồ:**

1. `RENTAL_PLAN` được **tự do sửa** sau khi có đơn — vì **mọi tính tiền của đơn cũ đọc từ `BOOKING_POLICY_SNAPSHOT`**, không đọc từ đây. Nếu code đọc thẳng `RENTAL_PLAN` để tính lại tiền → **sai**.
2. `hourly_to_daily_threshold_minutes` chỉ có nghĩa với `plan_type=HOURLY` → null/không dùng với các gói khác.

---

## **9. `VEHICLE_SCHEDULE` — Lịch xe (trục thời gian)**

**Để làm gì:** **Trục thời gian của xe** — thực thể trung tâm thứ hai của nền tảng. Mỗi slot thuê chiếm một segment ở đây. Đây là bảng chống **double-booking**.

**Trường quan trọng:**

| Trường                | Ý nghĩa                                                                      |
| --------------------- | ---------------------------------------------------------------------------- |
| `vehicle_id` (FK)     | Xe nào                                                                       |
| `type`                | `BOOKED` (do đơn thuê) | `MAINTENANCE` (bảo trì) | `BLOCKED` (chặn thủ công) |
| `start_at` / `end_at` | **KẾ HOẠCH** (dự kiến) — không phải thực tế                                  |
| `booking_id` (FK)     | **`null` khi là `MAINTENANCE` / `BLOCKED`**                                  |
| `is_extension`        | `true` = dòng sinh do **gia hạn / vượt giờ**                                 |
| `status`              | `ACTIVE \| CANCELLED`                                                        |
| `reason`              | Lý do chặn                                                                   |

**Quan hệ:** `n-1` với `VEHICLE`; `n-1` với `BOOKING` (1 booking → n dòng khi gia hạn).

**Luật nghiệp vụ:**

- **`VEHICLE_SCHEDULE` = KẾ HOẠCH · `BOOKING` = THỰC TẾ.** Đừng lẫn hai mốc thời gian.
- Ràng buộc **"các slot của cùng một xe không giao nhau"** chỉ đúng **ở thời điểm đặt**.

**Bẫy:**

1. **Ràng buộc không-giao-nhau bị phá vỡ trong thực tế** khi khách về trễ. Cách xử lý: ghi nhận bằng `status=CANCELLED` + mở `DISPUTE`. Hệ quả: xe không kịp giao chuyến sau → provider huỷ chuyến sau, **hoàn 100% cọc cho khách sau, KHÔNG bồi thường**.
2. Phụ phí vượt ghi trên **`BOOKING` gốc** (qua `BOOKING_CHARGE`), **không** ghi trên dòng lịch vượt — dòng lịch chỉ mô tả thời gian bị chiếm.
3. `end_at` = `scheduled_end_at`; thời gian thực tế nằm ở `BOOKING.actual_end_at`.

---

# **TẦNG 3 — GIAO DỊCH (trung tâm)**

## **10. `BOOKING` — Đơn thuê**

**Để làm gì:** **Trung tâm của toàn bộ hệ thống.** Mọi thứ tiền, lịch, đánh giá, tranh chấp đều treo vào đây.

**Trường quan trọng:**

| Nhóm            | Trường                                             | Ý nghĩa                                                                                                             |
| --------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Định danh       | `booking_code` (UK)                                | **Giữ để bảo mật — không lộ id tuần tự ra ngoài**                                                                   |
| Chủ thể         | `renter_id` → `RENTER`                             | Ai thuê                                                                                                             |
|                 | `vehicle_id` → `VEHICLE`                           | **MUTABLE** — đổi được khi dời xe                                                                                   |
|                 | `plan_id` → `RENTAL_PLAN`                          | Gói giá áp dụng                                                                                                     |
|                 | `driver_id` → `DRIVER`                             | **Gán theo chuyến, KHÔNG gán trước**                                                                                |
| Trạng thái      | `status`                                           | `PENDING \| REJECTED \| ACCEPTED \| CONFIRMED \| IN_PROGRESS \| COMPLETED \| CANCELLED`                             |
| Thời gian thực  | `actual_start_at`                                  | Tài xế xác nhận bắt đầu                                                                                             |
|                 | `actual_end_at`                                    | Để tính **phần vượt**                                                                                               |
| Xác nhận 2 phía | `renter_confirmed_at`, `provider_confirmed_at`     | Xong khi **CẢ HAI** xác nhận                                                                                        |
| Chuyến đi       | `pickup_address`, `pickup_time`, `passenger_count` | **MỘT điểm đón duy nhất**                                                                                           |
| Hợp đồng        | `contract_url`                                     | Gói tháng (tuỳ chọn) / **gói năm (bắt buộc)**                                                                       |
| Tiền            | `total_amount`                                     | Tổng tiền đơn                                                                                                       |
| Huỷ             | `cancelled_by` → `USER`                            | Renter / Provider / Admin                                                                                           |
|                 | `cancel_reason`                                    | `RENTER_REQUEST \| PROVIDER_NO_VEHICLE \| PROVIDER_NO_DRIVER \| PREVIOUS_RENTER_OVERDUE \| FORCE_MAJEURE \| SYSTEM` |
|                 | `cancelled_at`                                     | **BẮT BUỘC** — mốc tính % hoàn cọc                                                                                  |

**Quan hệ:** `n-1` với `RENTER`, `VEHICLE`, `RENTAL_PLAN`, `DRIVER`; `1-1` với `BOOKING_POLICY_SNAPSHOT`; `1-n` với `VEHICLE_SCHEDULE`, `BOOKING_CHARGE`, `PAYMENT_TRANSACTION`, `REVIEW`, `DISPUTE`.

**Luật nghiệp vụ (chính sách huỷ — chốt, KHÔNG có bồi thường):**

| Ai huỷ                   | Hoàn cọc                                                | Bồi thường               |
| ------------------------ | ------------------------------------------------------- | ------------------------ |
| **Renter**               | Hoàn **% cọc theo mốc thời gian** do **ADMIN cấu hình** | Không                    |
| **Provider** (mọi lý do) | **Hoàn 100% cọc**                                       | **KHÔNG bồi thường**     |
| **Gói năm**              | Không áp (không có cọc)                                 | Theo điều khoản hợp đồng |

> **Tỷ lệ % hoàn cọc là QUY TẮC NGHIỆP VỤ (admin config), KHÔNG phải bảng dữ liệu** → cố ý **không có** `CANCELLATION_POLICY` trong sơ đồ.
>
> **Cố ý KHÔNG có:** bồi thường, phạt provider, quỹ bồi thường, `caused_by_booking_id` (FK tự trỏ), `charge_type=COMPENSATION`. Nghiệp vụ truy trách nhiệm chuỗi quá phức tạp so với giá trị mang lại → **chỉ hoàn cọc + ghi vết**.

**Bẫy:**

1. `cancelled_at` là **BẮT BUỘC** để tính % hoàn cọc theo mốc thời gian. **`updated_at` KHÔNG thay thế được** (nó nhảy mỗi lần sửa bất kỳ field nào).
2. `vehicle_id` MUTABLE → phải cập nhật `VEHICLE_SCHEDULE` theo, và **ảnh hưởng tới `REVIEW`** (xem entity #14).
3. `driver_id` **không gán trước** — gán theo chuyến, vì tài xế có thể ốm/đổi.
4. `booking_code` tồn tại để **không lộ id tuần tự** — dùng nó ở mọi API public, không dùng `id`.

---

## **11. `BOOKING_POLICY_SNAPSHOT` — Ảnh chụp luật tính tiền** ⭐

**Để làm gì:** **Chốt toàn bộ LUẬT TÍNH TIỀN tại thời điểm khách đặt.** Đây là bảng giải quyết bài toán: *"provider sửa giá thì đơn cũ tính sao?"*

**Trường quan trọng** — toàn bộ là **bản chép lúc đặt** từ `RENTAL_PLAN` + `PROVIDER`:

| Nhóm            | Trường (đều mang nghĩa **"LÚC ĐẶT"**)                                                                  |
| --------------- | ------------------------------------------------------------------------------------------------------ |
| Giá             | `slot_price`, `slot_length_minutes`, `plan_type`                                                       |
| Cọc / trả trước | `deposit_ratio` (**null với gói năm**), `upfront_amount` (**null với gói có cọc**)                     |
| Vượt giờ        | `allowed_overtime_minutes`, `overtime_surcharge_ratio`                                                 |
| Chờ             | `waiting_free_minutes`, `waiting_rate`                                                                 |
| Khác            | `weekend_surcharge_ratio`, `free_radius_km`, `out_of_radius_rate`, `hourly_to_daily_threshold_minutes` |
| Mốc             | `created_at` = **thời điểm tạo booking**                                                               |

**Quan hệ:** `1-1` với `BOOKING` (`booking_id` FK + UK, tạo cùng lúc).

**Bốn quy tắc bắt buộc khi code:**

1. Chốt snapshot **LÚC KHÁCH GỬI YÊU CẦU (`status=PENDING`)**, **KHÔNG phải lúc provider accept** — vì giữa 2 mốc đó giá có thể đổi, và khách phải được giữ đúng con số mình đã nhìn thấy.
2. **MỌI tính tiền sau đó** (vượt giờ, chờ, cuối tuần, ngoài bán kính, quy đổi giá ngày, **hoàn cọc**) **ĐỌC TỪ SNAPSHOT** — không đọc `RENTAL_PLAN` / `PROVIDER`. Hai bảng đó **chỉ dùng cho đơn MỚI**.
3. Snapshot là **BẤT BIẾN** — không sửa sau khi tạo.
4. **Cọc hoàn khi huỷ tính theo `snap.deposit_ratio`**, KHÔNG theo `PROVIDER.deposit_ratio` hiện tại.

**Lợi ích:** nhờ đó `RENTAL_PLAN` / `PROVIDER` **được tự do sửa mà không phá đơn cũ**.

**Bẫy:**

1. **Phải tạo CÙNG LÚC với `BOOKING` (1-1) — không có đơn nào thiếu snapshot.** Dùng transaction khi insert.
2. `deposit_ratio` và `upfront_amount` **loại trừ nhau** (một cái null) — gói giờ/ngày/tháng có cọc, gói năm có upfront.
3. **Vì sao tách bảng thay vì nhúng vào `BOOKING`:** `BOOKING` đã 21 dòng; nhúng 14 cột nữa sẽ làm bảng cao thêm và **đè nhãn quan hệ** trong layout Mermaid. Tách bảng vừa sạch về mô hình, vừa tránh vấn đề layout.
4. **Giá đổi trong lúc đơn còn `PENDING`** → provider không đồng ý thì **DENY**, khách đặt lại theo giá mới. **Không có cơ chế "thông báo giá mới"**.

---

## **12. `BOOKING_CHARGE` — Các khoản phải trả (bóc tách hoá đơn)**

**Để làm gì:** **KẾT QUẢ** — bóc tách đơn thành các khoản phải trả để **giải trình** cho khách ("vì sao tổng tiền là X"). Đây là **hoá đơn tự chứa**.

**Trường quan trọng:**

| Trường            | Ý nghĩa                                                                     |
| ----------------- | --------------------------------------------------------------------------- |
| `charge_type`     | `BASE_RENTAL \| DEPOSIT \| OVERTIME \| WAITING \| WEEKEND \| OUT_OF_RADIUS` |
| `description`     | Diễn giải — **VÌ SAO** có khoản này                                         |
| `quantity`        | Số slot / phút / km — **nghĩa tuỳ `charge_type`**                           |
| `unit_price`      | Đơn giá 1 đơn vị                                                            |
| `applied_rate`    | % áp dụng — **null nếu không có**                                           |
| `amount`          | **Số tiền cuối cùng**                                                       |
| `created_by` (FK) | → `USER` — provider ghi tay / hệ thống tự sinh                              |
| `is_waived`       | **Nhà xe miễn khoản này**                                                   |
| `evidence_url`    | Ảnh/mốc chứng minh — **chỉ phụ phí ghi tay**                                |

**Quan hệ:** `n-1` với `BOOKING`; `1-n` tới `PAYMENT_TRANSACTION` (được tất toán bởi); `1-n` tới `DISPUTE` (bị khiếu nại).

**Luật nghiệp vụ:**

- **4 loại phụ phí tách thành `charge_type` riêng** → báo cáo bằng `GROUP BY charge_type`, không cần cột riêng cho từng loại.
- **Cọc là một charge** (`charge_type=DEPOSIT`) — không phải một cột trên `BOOKING`.

**Bẫy:**

1. **`quantity` ĐA NGHĨA theo `charge_type`** (cảnh báo 10):
   | `charge_type`         | Nghĩa của `quantity` |
   | --------------------- | -------------------- |
   | `BASE_RENTAL`         | số slot              |
   | `OVERTIME`            | số slot vượt         |
   | `WAITING`             | số phút              |
   | `OUT_OF_RADIUS`       | số km                |
   | `DEPOSIT` / `WEEKEND` | `null`               |
2. **`base_amount = quantity * unit_price` → SUY RA, KHÔNG lưu.** Thêm cột này là thừa và có nguy cơ lệch.
3. **`status` (`SETTLED` / `DISPUTED`) đã bị bỏ** — **suy diễn được**: `SETTLED` = có `PAYMENT_TRANSACTION` liên kết; `DISPUTED` = có `DISPUTE` liên kết. Lưu lại là **dữ liệu dẫn xuất**, dễ lệch.
4. **`is_waived` là boolean riêng, không phải một giá trị `status`** — nhà xe miễn khoản này nhưng khoản vẫn tồn tại trong lịch sử để đối soát.
5. **`evidence_url` chỉ có ý nghĩa với phụ phí ghi tay** (do provider tạo) — với khoản hệ thống tự sinh thì null.


# **TẦNG 4 — SAU CHUYẾN**

## **14. `REVIEW` — Đánh giá**

**Để làm gì:** Lưu đánh giá **sau chuyến** — có thể đánh giá **xe** và/hoặc **tài xế**. Đây là **nguồn sự thật** cho mọi `rating_avg` / `rating_count`.

**Trường quan trọng:**

| Trường                          | Ý nghĩa                                                             |
| ------------------------------- | ------------------------------------------------------------------- |
| `booking_id` (FK)               | Đánh giá thuộc đơn nào — **unique cùng `vehicle_id` / `driver_id`** |
| `vehicle_id` (FK, **nullable**) | Đánh giá xe                                                         |
| `driver_id` (FK, **nullable**)  | Đánh giá tài xế                                                     |
| `rating`                        | `1..5`                                                              |
| `comment`                       | Nội dung                                                            |
| `media`                         | Ảnh đính kèm                                                        |
| `created_at`                    | Ngày đánh giá — **người đánh giá suy từ `BOOKING.renter_id`**       |

**Quan hệ:** `n-1` với `BOOKING`; `n-1` với `VEHICLE`; `n-1` với `DRIVER`.

**Luật nghiệp vụ:**

- Một đơn có thể sinh **tối đa 2 review** (một cho xe, một cho tài xế) — hoặc 1, hoặc 0.
- **Không có cột `reviewer_id`** — người đánh giá **suy ra** từ `BOOKING.renter_id`. Thêm cột là dư thừa.

**Bẫy:**

1. **Ràng buộc "CHECK đúng 1 trong 2 khác null"**: mỗi review phải có **hoặc** `vehicle_id` **hoặc** `driver_id` (không cả hai null, không cả hai cùng có).
2. `REVIEW` **unique với cột nullable** → cần **2 PARTIAL UNIQUE INDEX**:
   ```sql
   UNIQUE(booking_id, vehicle_id) WHERE vehicle_id IS NOT NULL
   UNIQUE(booking_id, driver_id)  WHERE driver_id  IS NOT NULL
   ```
   Không dùng được một `UNIQUE` thường — vì `NULL` không bằng `NULL` trong SQL, khiến ràng buộc vô hiệu.
3. **Vì sao GIỮ cả `vehicle_id` và `driver_id` dù `BOOKING` đã có 2 FK đó?**
   - `BOOKING.vehicle_id` là **MUTABLE** (đổi được khi dời xe). Nếu review chỉ suy qua booking, một review viết cho **xe A** sẽ bị **gán nhầm sang xe B** sau khi booking đổi xe.
   - Giữ FK trực tiếp trên review = **chốt cứng "review này nói về ai"**, không phụ thuộc trạng thái mutable của booking.
   - Kết luận: **đây chính là lý do không nên refactor 2 FK thành 1 cột `target_type`** — sẽ mất khả năng chặn gán nhầm.
4. **`REVIEW` cố ý chỉ giữ 8 dòng.** Thêm lại field (ví dụ `updated_at`) sẽ làm bảng cao thêm ~47px và **nhãn cạnh `BOOKING→DISPUTE` đè lên đỉnh bảng `REVIEW`** (giới hạn layout Sugiyama của Mermaid — đã kiểm chứng bằng thực nghiệm).

---

## **15. `DISPUTE` — Tranh chấp / khiếu nại**

**Để làm gì:** Lưu tranh chấp **sau chuyến** giữa khách và nhà xe, do admin xử lý.

**Trường quan trọng:**

| Trường                         | Ý nghĩa                                                             |
| ------------------------------ | ------------------------------------------------------------------- |
| `booking_id` (FK)              | **Tranh chấp chung của chuyến**                                     |
| `charge_id` (FK, **nullable**) | **Khiếu nại MỘT khoản phí cụ thể**                                  |
| `raised_by` (FK)               | → `USER` (renter **HOẶC** provider)                                 |
| `handled_by` (FK)              | → `USER` (admin)                                                    |
| `category`                     | `CHARGE \| SERVICE_QUALITY \| DEPOSIT_REFUND \| TRIP_NOT_CONFIRMED` |
| `status`                       | `OPEN \| IN_REVIEW \| RESOLVED \| REJECTED`                         |
| `resolution`                   | Kết luận xử lý                                                      |

**Quan hệ:** `n-1` với `BOOKING`; `n-1` với `BOOKING_CHARGE`; `n-1` với `USER` (**2 FK khác vai: người mở + người xử lý**).

**Luật nghiệp vụ:**

- **Cả renter và provider đều mở được** dispute → `raised_by` không giới hạn bên nào.
- Tranh chấp có thể ở **2 mức**: chung của chuyến (`charge_id=null`) hoặc **nhắm đúng một khoản phí** (`charge_id≠null`) khi `category=CHARGE`.

**Bẫy:**

1. **`USER` nối tới `DISPUTE` bằng 2 quan hệ khác nghĩa** (`raised_by`, `handled_by`) — khi JOIN phải alias rõ, đừng quên phân biệt.
2. `charge_id` **nullable theo thiết kế** — tranh chấp về chất lượng dịch vụ không gắn với khoản phí nào.
3. **`DISPUTE` là nguồn suy diễn `BOOKING_CHARGE.status=DISPUTED`** (đã bỏ cột `status` ở `BOOKING_CHARGE`) → query trạng thái tranh chấp phải JOIN qua đây.

---

## **PHỤ LỤC — 25 QUAN HỆ, ĐỌC MỘT LƯỢT**

| #  | Quan hệ                                   | Ý nghĩa nghiệp vụ                     |
| -- | ----------------------------------------- | ------------------------------------- |
| 1  | `USER ‖—o\| PROVIDER`                     | Tài khoản là nhà cung cấp (1-0..1)    |
| 2  | `USER ‖—o\| RENTER`                       | Tài khoản là người thuê               |
| 3  | `USER ‖—o\| DRIVER`                       | Tài khoản là tài xế                   |
| 4  | `USER ‖—o{ DISPUTE`                       | Mở / xử lý tranh chấp                 |
| 5  | `PROVIDER ‖—o{ VEHICLE`                   | Sở hữu xe                             |
| 6  | `PROVIDER ‖—o{ DRIVER`                    | Quản lý đội tài xế                    |
| 7  | `VEHICLE ‖—o{ VEHICLE_IMAGE`              | Có ảnh                                |
| 8  | `VEHICLE ‖—o{ VEHICLE_DOCUMENT`           | Có giấy tờ                            |
| 9  | `VEHICLE ‖—o{ RENTAL_PLAN`                | Niêm yết gói giá                      |
| 10 | `VEHICLE ‖—o{ VEHICLE_SCHEDULE`           | Có trục thời gian                     |
| 11 | `RENTER ‖—o{ BOOKING`                     | Đặt xe                                |
| 12 | `VEHICLE ‖—o{ BOOKING`                    | Được thuê                             |
| 13 | `RENTAL_PLAN ‖—o{ BOOKING`                | Áp gói giá                            |
| 14 | `DRIVER ‖—o{ BOOKING`                     | Được phân công                        |
| 15 | `BOOKING ‖—o{ VEHICLE_SCHEDULE`           | Chiếm lịch (n dòng khi gia hạn)       |
| 16 | `BOOKING ‖—‖ BOOKING_POLICY_SNAPSHOT`     | **Chốt luật tính tiền lúc đặt (1-1)** |
| 17 | `BOOKING ‖—o{ BOOKING_CHARGE`             | Bóc tách khoản phải trả               |
| 18 | `BOOKING ‖—o{ PAYMENT_TRANSACTION`        | Các lần tiền thực chuyển              |
| 19 | `BOOKING_CHARGE ‖—o{ PAYMENT_TRANSACTION` | Được tất toán bởi                     |
| 20 | `VEHICLE ‖—o{ REVIEW`                     | Đánh giá xe                           |
| 21 | `DRIVER ‖—o{ REVIEW`                      | Đánh giá tài xế                       |
| 22 | `BOOKING ‖—o{ REVIEW`                     | Được đánh giá                         |
| 23 | `BOOKING ‖—o{ DISPUTE`                    | Phát sinh tranh chấp                  |
| 24 | `BOOKING_CHARGE ‖—o{ DISPUTE`             | Bị khiếu nại khoản phí                |

*Ghi chú: bảng trên liệt kê **24 dòng quan hệ** có trong `Conceptual_v10.1.mmd`. Con số **25** ở tiêu đề sơ đồ là số **cạnh hình học** sau khi Mermaid vẽ — riêng quan hệ 1-1 `BOOKING ‖—‖ BOOKING_POLICY_SNAPSHOT` được vẽ thành 2 cạnh (một cho `BOOKING` giữ FK, một cho chiều ngược), nên chênh 1. **Về mặt mô hình dữ liệu chỉ có 24 quan hệ.***

---

## **PHỤ LỤC — 10 CẢNH BÁO KHI TRIỂN KHAI**

| #  | Cảnh báo                                                                                                                                                                       |
| -- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1  | `REVIEW` unique với cột nullable → cần **2 PARTIAL UNIQUE INDEX**                                                                                                              |
| 2  | `BOOKING.vehicle_id` **MUTABLE** → phải cập nhật `VEHICLE_SCHEDULE` theo                                                                                                       |
| 3  | Ràng buộc "không giao nhau" của `VEHICLE_SCHEDULE` chỉ đúng **lúc đặt** → ghi nhận `CANCELLED` + `DISPUTE`, **hoàn 100% cọc, không bồi thường**                                |
| 4  | `grace hiệu dụng = min(allowed_overtime_minutes, maintenance_buffer_minutes)`                                                                                                  |
| 5  | Phụ phí vượt ghi trên **`BOOKING` gốc** (qua `BOOKING_CHARGE`), không trên dòng lịch vượt                                                                                      |
| 6  | `PROVIDER.deposit_ratio` **KHÔNG áp cho gói năm**                                                                                                                              |
| 7  | `rating_avg` / `rating_count` là **CACHE ở CẢ 3 bảng** (`VEHICLE`, `DRIVER`, `PROVIDER`) — phải cập nhật đủ 3; `PROVIDER` là **dẫn xuất bậc 2**; **nguồn sự thật là `REVIEW`** |
| 8  | `BOOKING.cancelled_at` **BẮT BUỘC** để tính % hoàn cọc; **`updated_at` KHÔNG thay thế được**                                                                                   |
| 9  | `BOOKING_POLICY_SNAPSHOT` phải tạo **CÙNG LÚC** với `BOOKING` (1-1)                                                                                                            |
| 10 | `BOOKING_CHARGE.quantity` **đa nghĩa** theo `charge_type` (xem bảng ở entity #12)                                                                                              |
