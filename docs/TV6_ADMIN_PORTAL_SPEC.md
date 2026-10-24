# TV6 — Admin Management Portal Specification & Delivery Report

**Thành viên:** TV6 (Member 6)  
**Phân hệ đảm nhiệm:** A01, A02, A03, A04  
**Nguồn tham chiếu:** `docs/PHAN_CONG_6_THANH_VIEN.md` · `docs/SBA301_FALL26.md` · `AGENT_CONTEXT.md`  

---

## 1. Tổng quan các màn hình TV6

Hệ thống Admin Portal được thiết kế theo quy chuẩn hiện đại, sử dụng TailwindCSS, Lucide/Material icons, tích hợp bảo mật phân quyền `ROLE_ADMIN` ở cả Backend (Spring Security `@PreAuthorize("hasRole('ADMIN')")`) và Frontend (`ProtectedRoute allowedRoles={['ADMIN']}`).

| Mã màn hình | Tên màn hình | Vai trò | Mục đích & Nghiệp vụ cốt lõi |
| :--- | :--- | :---: | :--- |
| **A01** | **Admin Home / Overview** | Admin | Thống kê số lượng theo thời gian thực (Xe chờ duyệt, Provider chờ duyệt, Driver chờ duyệt, eKYC chờ duyệt, Đơn thuê, Tranh chấp, Doanh thu) + Quick action links điều hướng nhanh. |
| **A02** | **Users & Profiles** | Admin | Quản lý người dùng toàn hệ thống (Khóa / Mở khóa tài khoản `ACTIVE`/`LOCKED`); Thẩm định GPKD & MST nhà cung cấp; Duyệt bằng lái tài xế; Thẩm định hồ sơ eKYC (CCCD mặt trước/sau, ảnh chân dung selfie). |
| **A03** | **Vehicle Approval** | Admin | Thẩm định & duyệt xe mở bán của Provider: Xem thông tin xe, ảnh ngoại thất, giấy tờ xe (đăng ký, đăng kiểm, bảo hiểm); Quyết định Approve hoặc Reject kèm lý do cụ thể (không đòi tài xế). |
| **A04** | **Operations & Refunds** | Admin | Giám sát đơn thuê toàn hệ thống thời gian thực; Phân xử tranh chấp (Dispute) phụ phí với quyết định `KEEP_CHARGE` hoặc `WAIVE_CHARGE`; Xử lý hoàn tiền mô phỏng (Mock Refund) cho các đơn hủy hoặc có thỏa thuận hoàn trả. |

---

## 2. Danh sách API Endpoints Backend

Tất cả các API dưới đây đều nằm dưới tiền tố `/api/admin` và bắt buộc có header `Authorization: Bearer <jwt_admin_token>`.

### A01 — Dashboard Stats
* `GET /api/admin/dashboard/stats`: Lấy số lượng thống kê động từ cơ sở dữ liệu.

### A02 — Người dùng & Hồ sơ Thẩm định
* `GET /api/admin/users`: Danh sách người dùng (hỗ trợ lọc theo `role`, `status`, `keyword`).
* `PATCH /api/admin/users/{id}/status`: Khóa hoặc mở khóa tài khoản người dùng (`ACTIVE` / `LOCKED`).
* `GET /api/admin/providers`: Danh sách nhà cung cấp (hỗ trợ lọc theo `status`).
* `PATCH /api/admin/providers/{id}/status`: Phê duyệt hoặc khóa nhà cung cấp (`APPROVED` / `LOCKED`).
* `GET /api/admin/drivers`: Danh sách tài xế (hỗ trợ lọc theo `status`).
* `PATCH /api/admin/drivers/{id}/status`: Phê duyệt hoặc từ chối tài xế (`APPROVED` / `REJECTED` kèm lý do).
* `GET /api/admin/ekyc`: Danh sách hồ sơ eKYC của khách thuê.
* `PATCH /api/admin/ekyc/{userId}/status`: Xác nhận duyệt (`VERIFIED`) hoặc từ chối (`REJECTED`) hồ sơ eKYC.

### A03 — Duyệt xe mở bán
* `GET /api/admin/vehicles`: Danh sách xe mở bán (hỗ trợ lọc theo `status`, `keyword`).
* `GET /api/admin/vehicles/{id}`: Xem chi tiết xe, thông số kỹ thuật, ảnh xe và toàn bộ giấy tờ pháp lý.
* `PATCH /api/admin/vehicles/{id}/approval`: Quyết định duyệt (`APPROVED`) hoặc từ chối (`REJECTED` kèm `reason`).

### A04 — Vận hành, Tranh chấp & Hoàn tiền
* `GET /api/admin/operations/bookings`: Giám sát toàn bộ đơn thuê với đầy đủ timeline, phí, tài xế, xe và khách thuê.
* `GET /api/admin/operations/disputes`: Danh sách khiếu nại, tranh chấp giữa khách thuê và nhà cung cấp.
* `PATCH /api/admin/operations/disputes/{id}/resolve`: Phán quyết tranh chấp (chọn `KEEP_CHARGE` hoặc `WAIVE_CHARGE` kèm nội dung giải quyết).
* `GET /api/admin/operations/payments`: Danh sách lịch sử giao dịch thanh toán toàn hệ thống.
* `POST /api/admin/operations/refund`: Xử lý hoàn tiền mô phỏng (Mock Refund) kèm sinh mã giao dịch hoàn trả.

---

## 3. Thông tin kiểm thử & Tài khoản mẫu

* **Tài khoản Admin:**
  * **Email:** `admin01@ecomotion.vn`
  * **Mật khẩu:** `EcoMotion@123`
  * **Role:** `ADMIN`
* **URL Admin Portal:** `http://localhost:5173/admin`
* **Swagger API UI:** `http://localhost:8080/swagger-ui.html`

---

## 4. Kiểm thử tự động (Automated Test Suite)
Đã bổ sung bộ kiểm thử tích hợp tự động [AdminApiIntegrationTest.java](file:///Backend/EcoMotion/src/test/java/com/fptufa26/ecomotion/admin/controller/AdminApiIntegrationTest.java) kiểm tra đầy đủ tính bảo mật (401 Unauthorized khi chưa đăng nhập, 403 Forbidden khi là Renter/Driver/Provider, và 200 OK cho Admin) trên toàn bộ các endpoint A01 - A04.
