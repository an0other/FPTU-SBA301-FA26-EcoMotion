# **ĐẶC TẢ CHỨC NĂNG — NỀN TẢNG CHO THUÊ XE HƠI (Cá nhân · Có tài xế · Cho thuê theo khối thời gian)**

*Phiên bản 8.0 — thu hẹp về **khách hàng cá nhân**, củng cố mô hình **slot tuyến tính theo thời gian**, chuyển toàn bộ thủ tục vận hành chuyến về **nhà cung cấp và tài xế của nhà cung cấp**.*

*Cập nhật: **chính sách huỷ hai chiều** (chỉ hoàn cọc, không bồi thường); **nhà cung cấp tạo tài khoản tài xế hộ, Admin duyệt mới hoạt động**; tách **bóc tách khoản phải trả** khỏi **giao dịch tiền thực chuyển**; **chốt tham số tính tiền lúc khách gửi yêu cầu** để nhà cung cấp được tự do sửa giá mà không phá đơn cũ.*

---

## **1. Tổng quan hệ thống**

### **1.1. Định vị sản phẩm**

Nền tảng kết nối **khách hàng cá nhân** có nhu cầu di chuyển với các nhà cung cấp **cung cấp trọn gói xe kèm tài xế** theo khối thời gian. Đây là mô hình **thuê xe có tài xế / xe hợp đồng** (chauffeur-hire) — không phải thuê xe tự lái, và cũng không phải gọi xe công nghệ.

**Phân biệt với gọi xe công nghệ (Grab, Be, Xanh SM):**

|                    | Gọi xe công nghệ                                | Nền tảng này                                                                                     |
| ------------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Đơn vị bán         | Một chuyến đi A → B                             | Một **khối thời gian** của một **chiếc xe cụ thể**                                               |
| Cách tính tiền     | Theo quãng đường + thời gian thực tế            | Theo **gói thời gian** (giờ / ngày / tháng / năm)                                                |
| Chọn xe            | Không — hệ thống ghép xe bất kỳ trong phân khúc | **Có** — khách xem ảnh, đánh giá, biển số và chọn đúng chiếc đó                                  |
| Xác nhận           | Tự động, ghép tài xế trong vài giây             | Nhà cung cấp **accept/deny** từng đơn                                                            |
| Đặt trước          | Gần như tức thì                                 | Giữ chỗ trước, có đặt cọc giữ chỗ                                                                |
| Thực thể trung tâm | Chuyến đi, Tài xế                               | **Xe, lịch xe** (trục thời gian của xe)                                                          |
| Điểm đến           | Một điểm đến                                    | **Không quản điểm đến** — khách thuê theo khối thời gian, tài xế chạy theo yêu cầu trong khối đó |

Điểm khác biệt cốt lõi: gọi xe công nghệ **không có khái niệm giữ chỗ một chiếc xe cụ thể trong 3 ngày**. Nền tảng này có, và đó là lý do **Xe** và **lịch xe** là hai thực thể trung tâm.

### **1.2. Loại xe và chủ thể cung cấp**

- Hỗ trợ **2 loại xe**: xe điện (EV) và xe động cơ đốt trong (xăng/dầu/hybrid). Loại nhiên liệu chỉ dùng để khách lọc khi tìm xe, không phát sinh nghiệp vụ đối chiếu năng lượng.
- **Nhà cung cấp là doanh nghiệp cho thuê xe (B2C — thường là doanh nghiệp nhỏ)**: có đội xe riêng, bãi/depot đỗ xe, đội tài xế và nhân viên điều phối.
- **Nhà cung cấp là chủ xe cá nhân (P2P)**: tự đăng xe, và cũng phải có tài xế thuộc quyền quản lý của mình (mục 3.2.e). Bản thân chủ xe có thể đăng ký làm tài xế cho chính xe của mình.

**Ranh giới phạm vi — chỉ bỏ B2B ở phía người thuê, không bỏ ở phía nhà cung cấp:**

| Phía                        | Được là doanh nghiệp? | Ghi chú                                                                                                                                                  |
| --------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Người thuê (Renter)**     | **Không**             | Chỉ cá nhân. Không có pháp nhân công ty, không tài khoản doanh nghiệp, không hợp đồng khung nhiều xe. Mọi người thuê đều là cá nhân với danh tính đầy đủ |
| **Nhà cung cấp (Provider)** | **Có**                | Vẫn có thể là **doanh nghiệp cho thuê xe (thường là doanh nghiệp nhỏ)** với đội xe, depot và đội tài xế, hoặc là chủ xe cá nhân (P2P)                    |

*Nói ngắn: **B2B bị bỏ ở phía cầu (người thuê), không bị bỏ ở phía cung (nhà cung cấp).*** Loại tài khoản `BUSINESS` và bảng `COMPANY` bị gỡ vì chúng phục vụ khách doanh nghiệp — không phải vì nền tảng không cho doanh nghiệp tham gia với vai trò nhà cung cấp.

### **1.3. Nhóm hình thức thuê**

| Nhóm                                  | Gói                 | Tài xế    | Hợp đồng     | Quyền kiểm soát xe         |
| ------------------------------------- | ------------------- | --------- | ------------ | -------------------------- |
| **Ngắn hạn** (mục 4.1)                | Theo giờ, theo ngày | Bắt buộc  | Không        | Thuộc nhà cung cấp         |
| **Định kỳ theo tháng** (mục 4.2)      | Theo tháng          | Bắt buộc  | Tuỳ chọn     | Thuộc nhà cung cấp         |
| **Thuê để sở hữu theo năm** (mục 4.3) | Theo năm, 3–5 năm   | **Không** | **Bắt buộc** | **Chuyển sang người thuê** |

Ba nhóm là ba mốc thời gian tăng dần của cùng một chiếc xe. Mốc **theo năm** là ranh giới đổi bản chất sản phẩm: từ mua dịch vụ di chuyển sang lộ trình sở hữu xe — chi tiết ở mục 4.3.

**Quy tắc slot áp dụng thống nhất:** mọi gói đều bán theo **slot tuyến tính thời gian**. Mỗi slot chiếm một segment trên **trục thời gian của xe (lịch xe)**; các slot của cùng một xe **không giao nhau**, và lịch thuê của cùng một người thuê cũng **không chồng lên nhau**.

**Phân biệt kế hoạch và thực tế:** *lịch xe* lưu **khoảng thời gian dự kiến** (kế hoạch), còn *đơn thuê* lưu **thời gian thực tế** (bắt đầu / kết thúc thực tế). Ràng buộc "không giao nhau" chỉ được đảm bảo **ở thời điểm đặt** — khách về trễ có thể phá vỡ nó trong thực tế, và trường hợp đó xử lý theo mục 3.2.c.

Nền tảng xoay quanh 4 thực thể dữ liệu chính: **Xe, Đơn thuê, Lịch xe, Người thuê**.

---

## **2. Actor (tác nhân)**

| Actor                                                           | Vai trò                                                                                                                                                                                                                                                                                                                                        |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Người thuê (Renter)** — cá nhân                               | Tìm xe, chọn gói và thời lượng thuê, chọn điểm đón tận nơi, theo dõi chuyến, thanh toán, đánh giá                                                                                                                                                                                                                                              |
| **Nhà cung cấp (Provider)** — chủ xe / doanh nghiệp cho thuê xe | Đăng xe, tạo gói giá, accept/deny đơn, **phân công tài xế khi có chuyến**, **tạo và quản lý tài khoản tài xế**, **thực hiện thủ tục nhận xe và kết thúc chuyến cùng tài xế**, đối soát doanh thu                                                                                                                                               |
| **Tài xế (Driver)**                                             | Có tài khoản riêng (role `DRIVER`) do **nhà cung cấp tạo hộ**, thuộc đúng một nhà cung cấp. Được phân công khi có chuyến; **thực hiện thủ tục nhận xe và kết thúc chuyến thay mặt nhà cung cấp**; xác nhận đã đến điểm đón, chạy theo yêu cầu của khách trong khối thời gian đã thuê. **Không tham gia gói thuê để sở hữu theo năm** (mục 4.3) |
| **Quản trị viên (Admin)**                                       | Duyệt xe, duyệt hồ sơ nhà cung cấp/tài xế/người thuê, cấu hình chính sách, xử lý tranh chấp, thống kê toàn nền tảng                                                                                                                                                                                                                            |

*Ghi chú thuật ngữ: "nhà cung cấp", "chủ xe" và "nhân viên cho thuê" cùng chỉ một actor — bên cung cấp cả xe lẫn tài xế cho mỗi chuyến.*

**Một tài khoản — một vai trò chính (**`USER.role`**).** Bốn giá trị hợp lệ: `RENTER`, `PROVIDER`, `DRIVER`, `ADMIN`. Muốn vừa thuê vừa cho thuê thì đăng ký hai tài khoản (hai email). Hồ sơ `PROVIDER` / `RENTER` / `DRIVER` gắn 1-1 với tài khoản tương ứng.

**Mỗi nhà cung cấp chỉ có MỘT tài khoản duy nhất.** Không có cơ chế nhiều nhân viên cùng dùng chung một hồ sơ nhà cung cấp — mọi thao tác của nhà cung cấp đều thực hiện từ tài khoản đó.

**Ranh giới trách nhiệm giữa Provider và Driver — chốt để không nhập nhằng:**

| Việc                                                                        | Ai làm                               |
| --------------------------------------------------------------------------- | ------------------------------------ |
| Đăng xe, tạo gói giá, cấu hình lịch và bảo trì, chặn lịch                   | Provider                             |
| Accept/deny đơn thuê                                                        | Provider                             |
| **Tạo / sửa / vô hiệu hoá tài khoản tài xế**                                | Provider                             |
| **Phân công tài xế cho chuyến** (gán lúc có chuyến, không gán trước vào xe) | Provider                             |
| **Duyệt tài khoản tài xế** (bắt buộc mới hoạt động)                         | Admin                                |
| **Nhận xe & bàn giao xe cho tài xế** trước chuyến                           | Provider, cùng tài xế được phân công |
| Đã đến điểm đón → bắt đầu chuyến                                            | Driver                               |


| **Kết thúc chuyến**                                                         | Driver, cùng Provider xác nhận       |

| Ghi nhận phụ phí, đối soát doanh thu                                        | Provider                             |  
| Xem lịch chạy và thu nhập của mình                                          | Driver                               |

---

## **3. Chi tiết chức năng theo Actor**


### **3.1. Người thuê (Renter — cá nhân)**

**a. Đăng ký & xác thực**

- Tạo tài khoản bằng số điện thoại/email, xác thực OTP *(OTP không lưu trong CSDL)*.
- Khai báo CMND/CCCD gắn chip; xác thực eKYC hoặc xác thực thủ công (`USER.ekyc_status`).
- **Không có nhánh khách doanh nghiệp** — không khai báo công ty, không mã số thuế, không gắn `company_id`.
- **Giấy phép lái xe**: không yêu cầu với gói giờ/ngày/tháng (người thuê không tự lái). **Bắt buộc khai báo và được duyệt** nếu muốn đặt **gói thuê để sở hữu theo năm** — số GPLX, hạng, ngày hết hạn (mục 4.3).

**b. Tìm & đặt xe**

- Tìm xe theo khu vực/bản đồ, lọc theo loại xe (điện/xăng/dầu/hybrid), số chỗ, hình thức thuê, khoảng giá.
- Xem chi tiết xe: hình ảnh, hãng/dòng/năm sản xuất, số chỗ, đánh giá và số chuyến đã chạy, giá theo từng gói, **lịch còn trống** của xe.
- Mỗi tin đăng tương ứng đúng 1 xe thật, hiển thị đúng biển số (không có "xe ảo").
- Gọi trực tiếp cho nhà cung cấp qua hotline hiển thị trên tin đăng để hỏi trước khi đặt.
- **Chọn gói và thời lượng thuê**: chọn gói (giờ / ngày / tháng / năm) → **nhập số lượng thuê** (số giờ, số ngày, số tháng, số năm) → **chọn thời điểm bắt đầu**. Hệ thống quy đổi thời lượng thành N slot liên tiếp và kiểm tra tuyến tính trên trục thời gian của xe. Khách không chọn slot rời.
- **Chọn điểm đón tận nơi**: **một điểm đón duy nhất** trên bản đồ, kèm mốc thời gian đón dự kiến. **Không khai báo lộ trình, không có điểm trả, không có điểm ghé.** Xe và tài xế đến tận điểm đón; sau đó khách sử dụng xe trong suốt khối thời gian đã thuê, tài xế chạy theo yêu cầu của khách.
- **Không khai báo hành khách** ở dạng bảng riêng. Có trường **số người đi** (chỉ để nhà cung cấp điều đúng loại xe) và ghi chú tối đa một dòng cho tài xế (ví dụ "có trẻ nhỏ", "cần cốp rộng").
- Gửi yêu cầu → chờ nhà cung cấp **accept/deny**. Sau khi được chấp nhận, thanh toán **cọc giữ chỗ** để khoá slot.
- **Giá được chốt lúc gửi yêu cầu, không phải lúc accept.** Ngay khi khách bấm gửi, hệ thống **lưu lại một bản chốt các tham số tính tiền** của gói giá áp tại thời điểm đó (đơn giá slot, độ dài slot, hình thức thuê, tỷ lệ cọc, mức vượt cho phép, % phụ phí, bán kính phục vụ, thời gian chờ miễn phí, mốc quy đổi giá ngày). Bản chốt này **không đổi trong suốt vòng đời đơn**.
  - Nếu nhà cung cấp **sửa giá sau đó**, đơn cũ **không bị ảnh hưởng** — mọi khoản về sau đều tính theo bản chốt.
  - Nếu nhà cung cấp thấy giá đã thay đổi và không muốn bán theo giá cũ, họ **deny** đơn — khách đặt lại theo giá mới. Không có cơ chế "thông báo giá mới" riêng.
  - **Không** lưu bản chốt cho thông tin mô tả của xe (hãng, dòng, màu, biển số) — các thông tin này gần như không đổi trong vòng đời một chiếc xe, nên chi tiết đơn hiển thị theo giá trị hiện tại.
- **Riêng gói theo năm**: sau khi được chấp nhận, khách ký hợp đồng và thanh toán **khoản trả trước** (số tiền tuyệt đối do nhà cung cấp đặt cho từng xe) thay cho cọc giữ chỗ (mục 4.3).

**c. Trước chuyến**

- Nhận thông tin tài xế được phân công: họ tên, ảnh, số điện thoại, đánh giá.
- Nhận thông báo khi tài xế nhận chuyến và khi tài xế đã đến điểm đón.
- Sửa điểm đón hoặc thời điểm đón trước giờ khởi hành (trong giới hạn nhà cung cấp cho phép).
- Nhà cung cấp và tài xế thực hiện **thủ tục nhận xe (bàn giao nội bộ)** — khách không tham gia nghiệp vụ này, không có biên bản bàn giao với khách.

**d. Trong chuyến**

- Theo dõi trạng thái chuyến: tài xế đã đến điểm đón / đang chạy / kết thúc.
- Theo dõi **vị trí xe và thời gian đã sử dụng trên bản đồ**; liên lạc trực tiếp với tài xế.
- **Gia hạn thời gian thuê** nếu xe còn slot trống kế tiếp — hệ thống báo giá phần gia hạn và thu thêm. Gia hạn dùng **cùng cơ chế với vượt giờ** (mục 4.4): mua thêm slot trên **chính đơn thuê hiện tại**, không tạo đơn thuê mới.
- Đi trong phạm vi **mức vượt cho phép** của gói (mục 4.4); vượt quá mức đó hệ thống ghi nhận phụ phí quá giờ.
- **Không có nghiệp vụ báo sự cố** ở các gói có tài xế. Sự cố hư hỏng/tai nạn do nhà cung cấp xử lý ngoài nền tảng.

**e. Kết thúc chuyến & thanh toán**

- **Chuyến kết thúc khi cả hai bên xác nhận.** Tài xế (thay mặt nhà cung cấp) và người thuê **cùng xác nhận chuyến thuê đã kết thúc**. Chuyến chỉ chuyển sang trạng thái hoàn tất khi cả hai xác nhận; nếu một bên không xác nhận trong thời hạn cấu hình, hệ thống đẩy sang Admin xử lý.
- **Không có điểm cuối** — gói được tính theo khối thời gian, không theo điểm đến. Kết thúc chuyến là kết thúc khối thời gian đã thuê.
- Xem bảng bóc tách chi phí: tiền thuê gói, phụ phí phát sinh (chờ, quá giờ, cuối tuần/lễ Tết, đón ngoài bán kính), trừ cọc giữ chỗ đã đóng. **Mỗi khoản hiển thị được cách tính** — số lượng × đơn giá × tỷ lệ áp dụng (nếu có) — để khách kiểm tra lại con số, ví dụ *"quá giờ: 2 slot × 200.000đ × 120% = 480.000đ"*.
- Thanh toán phần còn lại qua cổng thanh toán; nhận **biên lai điện tử** (tải lại được ở lịch sử thanh toán).
- Khiếu nại phụ phí nếu không đồng ý (mục 3.4.d).

**f. Huỷ đặt xe**

Chính sách huỷ áp dụng **đối xứng cho cả hai bên**, và **không có cơ chế bồi thường** — chỉ có **hoàn cọc**. Hệ thống ghi nhận **ai huỷ, lý do huỷ và mốc thời gian huỷ** trên mỗi đơn (phục vụ đối soát và phân xử sau này).

**Người thuê huỷ** — hoàn **tỷ lệ % của tiền cọc** theo **mốc thời gian huỷ**. Tỷ lệ hoàn do **Admin cấu hình**, riêng cho từng nhóm hình thức thuê (mục 3.4.c):

- Huỷ **trước mốc X** → hoàn **100%** cọc. *(Ngắn hạn tính theo giờ, ví dụ trước 24h; dài hạn tính theo ngày, ví dụ trước 30 ngày.)*
- Huỷ **sau mốc X** → hoàn theo tỷ lệ thấp hơn do Admin cấu hình. Phần cọc **không hoàn** thuộc về **nhà cung cấp**.

**Nhà cung cấp huỷ** — **hoàn 100% cọc**, không phạt, **không bồi thường**:

- Nhà cung cấp huỷ vì bất kỳ lý do gì đều **hoàn trả toàn bộ cọc** cho khách.
- Nền tảng **hỗ trợ tìm xe thay thế** cùng loại; nếu tìm được, cọc được **chuyển sang đơn mới** thay vì hoàn tiền.
- **Không có bồi thường thiệt hại** — đây là **quyết định có chủ đích** trong phạm vi đồ án: nghiệp vụ truy trách nhiệm theo chuỗi (khách về trễ làm hỏng chuyến sau, ai chịu thiệt hại, thu tiền người này trả người kia) có độ phức tạp cao mà giá trị mang lại thấp, nên **cắt khỏi phạm vi**.

**Lý do huỷ được phân loại** (để đối soát, không dùng để tính tiền): khách tự huỷ · nhà cung cấp hết xe · nhà cung cấp hết tài xế · khách của chuyến trước chưa trả xe · bất khả kháng · hệ thống huỷ.

*Gói thuê để sở hữu theo năm **không áp dụng chính sách này** — không có cọc giữ chỗ; xử lý theo điều khoản hợp đồng (mục 4.3).*

**g. Lịch sử & đánh giá**

- Xem lịch sử chuyến, chi tiết chi phí, tải lại biên lai điện tử.
- Đánh giá riêng **xe** và **tài xế** sau mỗi chuyến; đính kèm ảnh. Điểm của **nhà cung cấp** được **suy ra** từ đánh giá xe và tài xế thuộc nhà cung cấp đó (không đánh giá riêng).


### **3.2. Nhà cung cấp (Provider — chủ xe cá nhân / doanh nghiệp cho thuê xe)**

**a. Đăng ký & quản lý xe**

- Đăng thông tin xe: biển số, số khung, số máy, hãng, dòng xe, năm sản xuất, loại xe, số chỗ, hộp số, màu, loại nhiên liệu, hình ảnh.
- Tải giấy tờ xe: đăng ký xe, đăng kiểm, bảo hiểm, phù hiệu kinh doanh vận tải — kèm ngày hết hạn. Hệ thống cảnh báo sắp hết hạn và chặn xe hết hạn khỏi kết quả tìm kiếm.
- Biển số, số khung và số máy đều duy nhất toàn hệ thống — chặn đăng trùng.
- **Quy tắc sửa thông tin xe — ba tầng, theo mức ảnh hưởng pháp lý:**
  - **Tầng 1 — định danh vật lý** (`chassis_no`, `engine_no`): **bất biến**. Không sửa được sau khi đã duyệt; nếu ghi sai thì phải **tạo xe mới**. *(Vì đây là định danh in trên cà-vẹt, gắn với thân xe và động cơ vật lý.)*
  - **Tầng 2 — định danh pháp lý** (`license_plate`, giấy tờ xe): **sửa được nhưng phải duyệt lại**. Đổi biển số hay thay giấy đăng ký/đăng kiểm/bảo hiểm → xe về trạng thái **chờ duyệt**, Admin xét lại trước khi xe tiếp tục được bán.
  - **Tầng 3 — mô tả** (hãng, dòng, năm sản xuất, màu, số chỗ, hộp số, loại nhiên liệu, ảnh xe, khoảng đệm bảo trì): **sửa tự do, không cần duyệt lại**. Đây là thông tin mô tả, không ảnh hưởng đến tính pháp lý của xe.
  - *Lưu ý: quy tắc này **không đối xứng** với tài xế (mục 3.2.e) — tài xế sửa bất kỳ thông tin gì cũng phải duyệt lại, vì hồ sơ tài xế là định danh con người. Xe là tài sản có phân tầng trường nên kiểm soát theo tầng.*
- Điều kiện để một xe được Admin duyệt: đủ giấy tờ còn hạn và thông tin định danh không trùng. Không có yêu cầu về tài xế ở bước này — **tài xế được phân công khi có chuyến** (mục 3.2.c).

**b. Thiết lập giá & lịch bán**

- Tạo **gói giá** cho từng xe theo hình thức thuê: độ dài 1 slot (1 giờ / 24 giờ / 1 tháng / 1 năm), đơn giá 1 slot, **khoản trả trước** (chỉ gói năm), các mức phụ phí, **mức vượt thời gian cho phép** kèm **% phụ phí cho phần vượt** (mục 4.4), bán kính phục vụ miễn phí và phụ phí ngoài bán kính. Giá đã bao gồm chi phí tài xế.
- **Tỷ lệ cọc giữ chỗ không nằm trong gói giá** — nó là tham số **cấp nhà cung cấp**, áp dụng thống nhất cho mọi gói (mục 4.5).
- **Lịch bán luôn luôn mở** — hệ thống tự tính các **khoảng trống** trên trục thời gian của xe, nhà cung cấp **không cần thao tác mở bán**.
- **Khoảng cách bảo trì cố định giữa hai lịch xe.** Cấu hình một khoảng đệm bắt buộc (ví dụ 1 giờ cho gói giờ, 2 giờ cho gói ngày) giữa hai booking liên tiếp của cùng một xe để bảo dưỡng/dọn xe. Hệ thống tự chặn khoảng đệm này khi một booking được xác nhận.
- **Chặn lịch**: tạo khoảng chặn cho thời gian bảo dưỡng/nghỉ đột xuất, không bán ra.

**c. Xử lý yêu cầu thuê**

- Nhận và phản hồi cuộc gọi hỏi thuê qua hotline đã đăng ký.
- **Accept/deny** từng đơn — bắt buộc cho mọi đơn, không có tự động duyệt. Khi accept, hệ thống chỉ kiểm tra **các slot yêu cầu còn trống và không giao với lịch hiện có của xe** (kể cả lịch của chính người thuê đó). Kiểm tra tài xế rảnh thực hiện ở bước phân công bên dưới.
  - **Deny không kèm lý do** — kể cả khi nhà cung cấp từ chối vì giá đã thay đổi. Khách nhận thông báo bị từ chối và **đặt lại theo giá hiện hành**. *(Lý do từ chối không lưu riêng; chỉ lý do **huỷ** sau khi đã accept mới được ghi nhận — mục 3.1.f.)*
- **Phân công tài xế cho chuyến** — đây là bước gán tài xế duy nhất trong toàn hệ thống. Hệ thống kiểm tra tài xế được chọn thuộc cùng nhà cung cấp với xe và rảnh trong khoảng thời gian đó; cho phép **đổi tài xế** trước giờ khởi hành.
- Duyệt yêu cầu **gia hạn** thời gian thuê.
- **Xử lý khi xe chưa được trả về mà đã có đơn khác xếp sau** (khách trước về trễ, lấn vào lịch đã bán của cùng chiếc xe):
  - Hệ thống phát hiện và **đề xuất đổi xe khác** cùng loại cho khách của đơn xếp sau. Khách **đồng ý** → chuyển đơn sang xe mới.
  - Khách **không đồng ý** → **huỷ đơn** (huỷ mềm, giữ lịch sử), **hoàn 100% cọc**. **Không bồi thường** — theo chính sách huỷ (mục 3.1.f).
  - Đơn bị ảnh hưởng được **gỡ khỏi lịch xe nhưng không xoá cứng** — đánh dấu huỷ để còn bằng chứng cho việc phân xử tranh chấp (mục 3.4.d).
  - Khách về trễ **khai báo thời gian vượt dự kiến**; phần vượt được ghi thêm vào lịch xe dưới dạng **dòng lịch mới gắn vào chính đơn cũ** — không tạo đơn thuê mới (mục 4.4).

**d. Theo dõi & tất toán chuyến**

- **Thủ tục nhận xe**: nhà cung cấp cùng tài xế được phân công bàn giao xe (tình trạng xe, chìa khoá, giấy tờ) — đây là quy trình **nội bộ**, không có khách tham gia và không có biên bản hai bên với khách.
- Theo dõi các chuyến đang chạy của đội xe.
- Ghi nhận phụ phí phát sinh (chờ, quá giờ) kèm **lý do, số lượng, đơn giá và bằng chứng** — mỗi khoản là một **khoản phải trả** (một dòng `BOOKING_CHARGE`) gắn vào chuyến. Nhờ lưu đủ **số lượng × đơn giá × tỷ lệ áp dụng**, khoản phí **tự giải thích được cách tính** khi khách khiếu nại.
- **Kết thúc chuyến cùng tài xế**: tài xế và nhà cung cấp hoàn tất thủ tục trả xe nội bộ, đối chiếu **thời gian sử dụng thực tế** với khối thời gian đã bán để xác định phần vượt mức.
- Xác nhận hoàn tất chuyến (cùng người thuê — mục 3.1.e), đối soát doanh thu.

**e. Quản lý tài xế**

- **Nhà cung cấp tạo tài khoản tài xế hộ** — nhà cung cấp nhập hộ **email, số GPLX (số, hạng, ngày hết hạn), ảnh chân dung, ảnh GPLX và số điện thoại**; hệ thống tạo ngay tài khoản role `DRIVER` ở trạng thái **chờ duyệt** và gắn tài khoản vào **đội của chính nhà cung cấp đó**. Tài xế **không cần tự đăng ký**.
- **Admin duyệt mới hoạt động**: tài khoản tài xế ở trạng thái chờ duyệt **không nhận được chuyến** cho tới khi Admin duyệt. Admin **từ chối** kèm **lý do** → nhà cung cấp sửa hồ sơ và **gửi duyệt lại** (vòng lặp sửa → gửi lại → duyệt).
- **Sửa hồ sơ đã duyệt** (GPLX, ảnh) → tài khoản **tụt về trạng thái chờ duyệt**, Admin **duyệt lại**. Đây là ràng buộc bắt buộc: nếu không, nhà cung cấp có thể thay ảnh/GPLX sau khi đã được duyệt mà Admin không xét lại.
- **Vô hiệu hoá tài xế**: nhà cung cấp **tự do** đánh dấu tài xế không còn thuộc đội của mình, **không cần Admin can thiệp**. Một tài xế bị vô hiệu hoá **không thể chuyển sang nhà cung cấp khác** — muốn quay lại nền tảng phải **tạo tài khoản mới** (giữ nguyên vết lịch sử của tài khoản cũ).
- Mỗi tài xế chỉ thuộc **một** nhà cung cấp, để đảm bảo xe và tài xế của một chuyến luôn cùng nguồn.
- Lưu hồ sơ tài xế: GPLX (số, hạng, ngày hết hạn), ảnh chân dung, ảnh GPLX, số điện thoại.
- Phân công tài xế cho từng chuyến, theo dõi đánh giá.
- Tự xử lý phạt nguội phát sinh trong chuyến với tài xế phụ trách — **ngoài nền tảng**.

**f. Quản lý tài chính**

- Xem doanh thu theo xe, theo tài xế, theo tháng.
- Xem lịch sử giao dịch và phụ phí đã thu.
- **Nhận tiền từ nền tảng**: khai báo tài khoản ngân hàng nhận chi trả; xem lịch sử các khoản nền tảng đã chuyển về **theo từng chuyến**, sau khi trừ tỷ lệ hoa hồng đã thoả thuận.

### **3.3. Tài xế (Driver)**

- **Tài khoản do nhà cung cấp tạo hộ** (role `DRIVER`): nhà cung cấp nhập hộ họ tên, số điện thoại, số GPLX + hạng + ngày hết hạn, ảnh chân dung, ảnh GPLX. Tài khoản ở trạng thái **chờ duyệt**; **Admin duyệt** thì mới nhận được chuyến. Tài xế **không tự đăng ký** trên nền tảng. Nhà cung cấp **có thể sửa / vô hiệu hoá** tài khoản do mình tạo; sửa hồ sơ đã duyệt sẽ đưa tài khoản **về chờ duyệt lại**.
- **Hệ thống không can thiệp sâu vào tài xế** — vai trò của tài xế là **bên trung gian để xác thực và cung cấp thông tin cho người thuê** (họ tên, ảnh, đánh giá, số điện thoại). Mọi nghiệp vụ vận hành (nhận xe, chạy chuyến, trả xe) do nhà cung cấp quản lý.
- Nhận thông báo **chuyến được phân công**: thời gian bắt đầu – kết thúc, **điểm đón tận nơi**, **số người đi**, ghi chú của khách. **Không có lộ trình các điểm dừng** để hiển thị.
- **Thực hiện thủ tục nhận xe** cùng nhà cung cấp trước chuyến — tài xế là người trực tiếp nhận xe và chịu trách nhiệm vận hành trong chuyến.
- Xác nhận **đã đến điểm đón** → **bắt đầu chuyến** → **kết thúc chuyến**.
- **Kết thúc chuyến cùng nhà cung cấp**: tài xế xác nhận kết thúc và bàn giao lại xe; nhà cung cấp đối chiếu thời gian sử dụng thực tế để xác định phần vượt mức. Người thuê xác nhận độc lập (mục 3.1.e).
- Ghi nhận **thời gian sử dụng thực tế** của chuyến khi kết thúc.
- Xem lịch chạy và lịch sử chuyến, thu nhập theo chuyến (nếu hưởng lương theo chuyến).

### **3.4. Quản trị viên (Admin)**

**a. Quản lý người dùng**

- Duyệt hồ sơ nhà cung cấp / người thuê / tài xế (xác minh giấy tờ, eKYC).
- **Duyệt hồ sơ nhà cung cấp doanh nghiệp**: với nhà cung cấp là doanh nghiệp cho thuê xe, xác minh giấy phép kinh doanh, mã số thuế, người đại diện pháp luật (lưu trên `PROVIDER`). Không có bảng công ty riêng vì không cần gom nhân sự nhiều người dùng chung một pháp nhân.
- Khoá/mở tài khoản vi phạm.

**b. Quản lý xe & danh mục**

- Duyệt xe mới đăng ký: đối chiếu biển số và số khung với giấy đăng ký xe, kiểm tra đủ giấy tờ còn hạn.
- Gỡ xe vi phạm chính sách hoặc hết hạn đăng kiểm/bảo hiểm.

**c. Cấu hình chính sách**

- Cấu hình khung các nhóm hình thức thuê, các loại phụ phí được phép thu, mức trần/sàn tham chiếu.
- Cấu hình **mức vượt thời gian cho phép mặc định** và **% phụ phí cho phần vượt** (mục 4.4).
- Cấu hình **khoảng cách bảo trì mặc định giữa hai lịch xe** (mục 3.2.b).
- Cấu hình **tỷ lệ % hoàn cọc theo mốc thời gian huỷ**, riêng cho từng nhóm hình thức thuê (mục 3.1.f). Cần phân biệt **hai tham số % khác chủ thể**: **tỷ lệ cọc thu** (bao nhiêu % trên tổng tiền thuê) do **nhà cung cấp** đặt ở cấp nhà cung cấp — mục 4.5; còn **tỷ lệ hoàn cọc** (hoàn bao nhiêu % khi huỷ) do **Admin** cấu hình ở đây.
- **Không có cấu hình bồi thường/đền bù** — chính sách huỷ chỉ có hoàn cọc, không có cơ chế bồi thường (mục 3.1.f).
- *(Các tham số toàn nền tảng ở mục này là **giá trị mặc định gợi ý** khi nhà cung cấp tạo gói giá mới; giá và chính sách áp cho từng đơn vẫn lấy từ gói giá của chính chiếc xe.)*

**d. Xử lý tranh chấp**

- Tiếp nhận khiếu nại giữa người thuê và nhà cung cấp — chủ yếu về **phụ phí**, **chất lượng dịch vụ/tài xế**, **hoàn cọc**, và **chuyến không được xác nhận kết thúc bởi một trong hai bên**. Khiếu nại về tiền **gắn được vào một khoản phí cụ thể** của chuyến (cọc, quá giờ, chờ, cuối tuần, ngoài bán kính) thay vì chỉ gắn vào cả đơn.
- Xem lại bằng chứng (lịch sử trạng thái chuyến, mốc thời gian nhận/trả xe nội bộ, lịch sử giao dịch, bằng chứng kèm phụ phí) để phân xử.
- *(Không tiếp nhận tranh chấp về phạt nguội, hư hỏng xe, phụ phí vệ sinh — ngoài phạm vi.)*

**e. Đối soát chi trả**

- Theo dõi dòng tiền chi trả cho nhà cung cấp **theo từng chuyến**: tỷ lệ hoa hồng nền tảng giữ lại và tài khoản ngân hàng nhận tiền của từng nhà cung cấp.

**f. Thống kê & báo cáo**

- Doanh thu toàn nền tảng theo thời gian, hình thức thuê, loại xe, khu vực.
- Tần suất thuê, xe/khu vực/khung giờ được thuê nhiều nhất.

---

## **4. Các hình thức thuê — mô tả & giá cả**

Cách khách chọn thuê, áp dụng cho **mọi gói**: khách chọn **gói** → **nhập số lượng thuê** (số giờ / số ngày / số tháng / số năm) → **chọn thời điểm bắt đầu**. Hệ thống quy đổi số lượng thành N slot liên tiếp trên trục thời gian của xe và chỉ cho đặt nếu toàn bộ dải slot còn trống.

### **4.1. Ngắn hạn**

**a. Theo giờ**

- Dành cho chuyến ngắn trong ngày: đi công việc vài giờ, đưa đón sân bay, sự kiện/xe cưới.
- 1 slot = 1 giờ. Khách nhập số giờ muốn thuê; hệ thống chiếm đúng số slot giờ liên tiếp đó.
- **Không quản điểm đến**: khách chỉ chọn **một điểm đón tận nơi** và mốc thời gian đón. Sau đó khách đi đâu trong khối giờ đã thuê là do khách yêu cầu tài xế; nền tảng không ghi nhận điểm đến.
- Ngưỡng quy đổi sang giá ngày: khi tổng thời gian vượt một mốc do nhà cung cấp cấu hình (Gia Đình Việt áp dụng mốc **21 giờ**), hệ thống tự chuyển sang tính giá trọn ngày để khách không bị thiệt.
- Giá bao gồm chi phí tài xế, không có mức giá tự lái song song để so sánh.

**b. Theo ngày**

- 1 slot = 24 giờ kể từ giờ bắt đầu chuyến. Khách nhập số ngày muốn thuê; ví dụ 2 ngày = 2 slot.
- Phù hợp công tác, du lịch, sự kiện nhiều ngày — tài xế đi cùng suốt gói.
- Giá quan sát thật: xe phổ thông 600.000–1.500.000đ/ngày (Gia Đình Việt); xe điện VinFast 550.000đ/ngày (VF3) đến 2.100.000đ/ngày (VF9 Plus, Green Future) — lưu ý đây là giá **tự lái**, nền tảng này cao hơn vì đã gồm tài xế.
- Không giới hạn quãng đường.

### **4.2. Định kỳ theo tháng (vẫn có tài xế)**

- 1 slot = 1 tháng. Đơn giá có chiết khấu theo bậc thang, thuê càng nhiều tháng giá/tháng càng thấp.
- Dành cho khách cần xe đưa đón thường xuyên: lãnh đạo, gia đình có nhu cầu đưa đón cố định, chuyên gia nước ngoài công tác dài ngày.
- Giá trọn gói bao gồm: chi phí tài xế, bảo trì, bảo dưỡng, bảo hiểm, phí đường bộ, và với xe điện có thể gồm chi phí sạc.
- Không giới hạn quãng đường/tháng.
- Hợp đồng là **tuỳ chọn**: hai bên có thể ký ngoài hệ thống rồi tải bản đã ký (PDF/scan) gắn vào đơn. Huỷ cần báo trước xa hơn gói ngày (ví dụ 30 ngày).
- Trong kỳ, mỗi lần khách cần dùng xe, nhà cung cấp **phân công tài xế cho lần sử dụng đó**; khách cung cấp điểm đón tận nơi cho từng lần.
- **MVP dữ liệu:** một đơn gói tháng = một `BOOKING` + một dòng lịch xe tháng; các lần đi trong tháng **không** tách bảng chuyến con — cập nhật lại điểm đón trên `BOOKING` trước mỗi lần đi (hoặc phase 2 thêm thực thể chuyến con).

### **4.3. Thuê để sở hữu theo năm (tự lái, bắt buộc hợp đồng)**

Đây là **ngoại lệ duy nhất** của quy tắc bắt buộc có tài xế.

- 1 slot = 1 năm. Thời hạn 3–5 năm ⇒ 3–5 slot liên tiếp.
- **Không có tài xế** — người thuê tự lái, nên bắt buộc đã khai báo GPLX hợp lệ và được duyệt.
- **Hợp đồng bắt buộc**, ký trước khi nhận xe. Hợp đồng là cơ chế quy trách nhiệm thay cho tài xế: chiếc xe được giao cho đúng một người thuê có danh tính đầy đủ trong một hợp đồng nhiều năm, nên vấn đề "không xác định được ai cầm lái" không tồn tại.
- Cấu trúc tiền: **khoản trả trước** (số tiền tuyệt đối do nhà cung cấp đặt cho từng xe) + **trả góp hàng tháng** trong suốt thời hạn. Chi phí hàng tháng đã gồm bảo trì, bảo hiểm, và với xe điện có thể gồm chi phí sạc.
- **Giá trả góp cố định suốt kỳ hạn** — không điều chỉnh giữa kỳ. Con số này được ghi trong hợp đồng và chốt lúc đặt (mục 3.1.b); mọi thay đổi (nếu có) xử lý ngoài nền tảng theo điều khoản hợp đồng.
- **Gói năm không có phụ phí** (chờ, quá giờ, cuối tuần, ngoài bán kính) — khách tự lái, tự vận hành. Vì vậy bản chốt tham số tính tiền của đơn gói năm chỉ chứa **khoản trả trước và giá trả góp**, các tham số phụ phí để trống.
- Kết thúc kỳ hạn, khách chọn một trong ba: **mua lại xe** theo giá ưu đãi đã định trước trong hợp đồng, **trả lại xe**, hoặc **gia hạn**. Khách cũng có thể **mua đứt sớm** bất kỳ lúc nào trong kỳ.
- **Không có điểm đích đến, điểm trả hay lộ trình.** Xe giao một lần cho người thuê giữ suốt kỳ; nền tảng không ghi nhận hành trình khách đi.
- Mô hình này Green Future/GSM đã triển khai thật tại Việt Nam, không phải giả định.

### **4.4. Mức vượt cho phép (áp dụng cho mọi gói có tài xế)**

- Mỗi gói có **một mức vượt thời gian cho phép** do nhà cung cấp cấu hình (ví dụ gói 4 giờ cho vượt 30 phút).
- Phần thời gian sử dụng **trong mức vượt** không tính thêm tiền.
- Phần **vượt quá mức cho phép** được **làm tròn lên thành slot**: số slot tính thêm = làm tròn lên của (phần vượt ÷ độ dài 1 slot). Mỗi slot vượt tính theo **đơn giá slot của gói cộng thêm % phụ phí** do gói cấu hình, ghi nhận thành khoản **phụ phí quá giờ** (một dòng `BOOKING_CHARGE` loại `OVERTIME`).
  - *Ví dụ: gói giờ, mức vượt cho phép 30 phút. Khách thuê 2 giờ nhưng trả thực tế 3h30 → phần vượt 1h30 → làm tròn thành **2 slot** → phụ phí bằng giá 2 slot (cộng % phụ phí).*
  - *Ví dụ khác: thuê 2 giờ, trả thực tế 2h01 → vẫn trong mức vượt → không tính thêm.*
- **Mức vượt không có trần** — phần vượt cứ tiếp tục được tính theo slot cho tới khi xe được trả.
- **Cơ chế dùng chung với gia hạn** (mục 3.1.d): cả hai đều là **mua thêm slot trên cùng một đơn thuê**. Phần vượt **không tạo đơn thuê mới** — hệ thống chỉ ghi thêm một dòng lịch xe gắn vào đơn hiện tại.
- **Mức vượt chỉ áp dụng cho thời gian** — không áp dụng cho số điểm đón hay số điểm dừng, vì đơn thuê chỉ có một điểm đón.
- Cách đo: so **thời điểm kết thúc thực tế** (do tài xế xác nhận, nhà cung cấp đối chiếu) với **thời điểm kết thúc theo slot đã bán**.
- Nếu phần vượt **lấn vào lịch của một đơn khác** đã bán trên cùng chiếc xe, xử lý theo mục 3.2.c.

---

### **4.5. Đặt cọc & phụ phí**

**Đặt cọc — chỉ một loại: cọc giữ chỗ (booking deposit).** **Tỷ lệ cọc do nhà cung cấp đặt** — một tỷ lệ % áp dụng thống nhất cho **mọi gói có tài xế** (giờ / ngày / tháng) của nhà cung cấp đó, tính trên **tổng tiền thuê dự tính**. Thanh toán ngay sau khi nhà cung cấp accept, để khoá slot. Được trừ vào tổng chi phí khi tất toán chuyến. Hoàn theo chính sách huỷ (mục 3.1.f).

*Phân biệt hai tham số %: **tỷ lệ cọc thu** do **nhà cung cấp** đặt (mục này), còn **tỷ lệ hoàn cọc khi huỷ** do **Admin** cấu hình theo mốc thời gian (mục 3.4.c).*

*Mọi con số ở mục này **được chốt vào bản chốt tham số của đơn lúc khách gửi yêu cầu** (mục 3.1.b). Nhà cung cấp sửa tỷ lệ cọc, bán kính hay đơn giá sau đó **không ảnh hưởng đơn cũ** — kể cả tiền cọc hoàn khi huỷ cũng tính theo tỷ lệ đã chốt, không theo tỷ lệ hiện hành.*

**Cơ chế ghi nhận tiền của một chuyến — hai tầng:**

- **Bóc tách khoản phải trả** (`BOOKING_CHARGE`): liệt kê từng khoản cấu thành giá chuyến — tiền thuê gói (`BASE_RENTAL`), cọc giữ chỗ (`DEPOSIT`), và các phụ phí (`OVERTIME`, `WAITING`, `WEEKEND`, `OUT_OF_RADIUS`). Mỗi khoản lưu **số lượng × đơn giá × tỷ lệ áp dụng** (nếu có) cùng số tiền cuối cùng, để **tự giải thích được cách tính**; kèm cờ **miễn khoản này** và **bằng chứng** khi ghi thủ công.
- **Giao dịch tiền thực chuyển** (`PAYMENT_TRANSACTION`): chỉ ghi những lần tiền **thực sự** vào hoặc ra — thanh toán cọc, trả trước gói năm, tất toán, hoàn tiền, chi trả nhà cung cấp. Mỗi giao dịch có chiều (vào/ra), phương thức, mã giao dịch cổng thanh toán (đối soát & chống trùng), và có thể gắn vào một khoản phải trả cụ thể (hoặc không, với chi trả/hoàn tiền).

*Lý do tách hai tầng: một khoản phí có thể được trả nhiều lần, và một khoản phí **chưa thanh toán** vẫn phải tồn tại để khiếu nại/đối soát — điều mà bảng giao dịch đơn thuần không biểu diễn được. Việc "đã tất toán" hay "đang khiếu nại" **suy ra được** từ bảng giao dịch và bảng tranh chấp, nên không cần cột trạng thái riêng.*

*Gói thuê để sở hữu theo năm không dùng cọc giữ chỗ — thay bằng **khoản trả trước**: một **số tiền tuyệt đối do nhà cung cấp đặt cho từng xe**. Khoản này là một phần của giá mua chứ không phải tiền cọc, nên **không hoàn lại** khi khách đơn phương chấm dứt (xử lý theo điều khoản hợp đồng).*

**Các loại phụ phí được phép thu:**

| Phụ phí                        | Căn cứ                                                                                            |
| ------------------------------ | ------------------------------------------------------------------------------------------------- |
| **Chờ** (waiting)              | Khách trễ hẹn tại điểm đón quá thời gian miễn phí                                                 |
| **Quá giờ** (overtime)         | Thời gian sử dụng thực tế **vượt quá mức vượt cho phép** của gói (mục 4.4) mà không gia hạn trước |
| **Cuối tuần / lễ Tết**         | Tỷ lệ phụ thu do nhà cung cấp cấu hình sẵn trong gói giá                                          |
| **Đón ngoài bán kính phục vụ** | Điểm đón nằm ngoài bán kính miễn phí của nhà cung cấp, tính theo km                               |

**Bán kính phục vụ**: mỗi gói giá có bán kính đón miễn phí (ví dụ 10km quanh depot), ngoài bán kính phụ thu theo km. Đây là phụ phí duy nhất liên quan đến địa điểm, vì đơn thuê chỉ có một điểm đón.

**Giới hạn quãng đường (km): không áp dụng** cho mọi hình thức thuê. Không có field cấu hình giới hạn km và không có phụ phí vượt km ở bất kỳ đâu.

*(Gói theo năm không có phụ phí nào — khách tự lái, tự vận hành; chi phí phát sinh xử lý theo điều khoản hợp đồng, ngoài nền tảng.)*
