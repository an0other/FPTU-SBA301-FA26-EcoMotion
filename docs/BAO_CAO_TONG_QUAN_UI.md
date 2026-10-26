# BÁO CÁO TỔNG QUAN GIAO DIỆN (UI) — ECOMOTION FRONTEND

> **Dự án:** EcoMotion — Nền tảng chia sẻ xe điện & điều phối vận tải  
> **Thư mục:** `Frontend/EcoMotion/`  
> **Ngày lập báo cáo:** 22/10/2026  
> **Phạm vi:** Toàn bộ phần giao diện người dùng (UI), công nghệ, cấu trúc, layout, component, phong cách thiết kế và các vấn đề tồn đọng.

---

## 1. CÔNG NGHỆ

- **Framework & Phiên bản:** 
  - React `19.2.8` (`react`, `react-dom`).
- **Build Tool:** 
  - Vite `8.3.0` (kết hợp plugin `@vitejs/plugin-react ^6.1.1`).
- **Ngôn ngữ:** 
  - JavaScript thuần (ES Modules, đuôi file `.js` và `.jsx`). Không sử dụng TypeScript trong mã nguồn (dù trong `devDependencies` của `package.json` có khai báo `@types/react`, `@types/react-dom`).
- **Thư viện UI / CSS:**
  - **Tailwind CSS v4** (`tailwindcss ^4.3.3`, `@tailwindcss/vite ^4.3.3`).
  - **Không dùng** bất kỳ thư viện UI component sẵn có nào: Không dùng MUI (Material-UI), Ant Design, Bootstrap, Chakra UI, hay shadcn/ui.
  - Toàn bộ button, input, card, modal, badge đều được code thủ công bằng Tailwind CSS utility classes kết hợp một số class CSS tùy biến.
- **Thư viện Router:** 
  - `react-router-dom ^7.18.4` (quản lý điều hướng tập trung qua `BrowserRouter`, `Routes`, `Route`, `Navigate`).
- **Quản lý State (State Management):**
  - **Context API:** Duy nhất 1 Context toàn cục là `authContext` (`src/contexts/AuthProvider.jsx`) dùng để lưu trữ trạng thái người dùng đăng nhập (`user`, `loading`, `error`, `login`, `logout`, `register`, `activate`).
  - **State cục bộ:** Sử dụng React Hooks cơ bản (`useState`, `useEffect`, `useCallback`, `useMemo`, `useRef`).
  - **Không có** thư viện quản lý state tập trung (không Redux, Zustand, MobX, Recoil, TanStack Query, SWR).
- **Gọi API (HTTP Client):** 
  - `axios ^1.20.0` với cấu hình tập trung tại `src/services/apiClient.js` (tự động đính kèm JWT token từ `tokenStore.js` trong `localStorage`, tự động xử lý refresh token khi gặp mã lỗi 401).
- **Thư viện Form:** 
  - **Không có** (không dùng `react-hook-form`, `formik`). Form hoàn toàn dùng controlled inputs với `useState` và thẻ `<form onSubmit={...}>`.
- **Thư viện Icon:** 
  - Đang bị phân mảnh giữa 3 cách thức:
    1. Google Web Font **Material Symbols Outlined** và **Material Symbols Rounded** (nhúng CDN tại `index.html`).
    2. Tập hợp ~50 icon SVG tự viết tại `src/components/icons/Icons.jsx`.
    3. Thẻ `<svg>` inline viết trực tiếp trong JSX của một số trang.
- **Bản đồ:** 
  - `leaflet ^1.9.4` (dùng cho bản đồ chọn xe `VehicleMap.jsx` và tracking hành trình `LiveMapViewer.jsx`).

---

## 2. CẤU TRÚC THƯ MỤC

### Cây thư mục của `src/` (tối đa 3 cấp)

```
src/
├── assets/
│   ├── hero.png
│   ├── react.svg
│   └── vite.svg
├── components/
│   ├── booking/            # 19 components phục vụ luồng đặt xe của Renter
│   ├── driver/             # 4 components giao diện cho Tài xế
│   ├── icons/              # File Icons.jsx chứa các biểu tượng SVG viết tay
│   ├── map/                # Component bản đồ Leaflet định vị xe trực tiếp
│   ├── payments/           # 5 components lịch sử thanh toán & bảng biểu phụ phí
│   ├── provider/           # Header, Layout và các modal của Nhà xe
│   │   └── modals/         # Modal duyệt đơn, gán tài xế, thêm phụ phí
│   ├── reviews/            # 3 components đánh giá sao & form review
│   ├── vehicles/           # Bộ lọc xe, phân trang, card xe và bản đồ
│   ├── yearly-rental/      # Form khai GPLX và ký hợp đồng thuê năm
│   ├── AppHeader.jsx       # Header dùng chung cho khách thuê & trang công khai
│   ├── AuthLayout.jsx      # Layout chia đôi màn hình cho Login/Register/Activate
│   ├── ProtectedRoute.jsx  # Route guard bảo vệ phân quyền đăng nhập
│   └── Toast.jsx           # Component hiển thị thông báo nổi góc màn hình
├── contexts/
│   ├── authContext.js
│   └── AuthProvider.jsx    # Provider quản lý thông tin User và Token
├── hooks/                  # 17 custom hooks tách logic fetch dữ liệu
├── pages/
│   ├── booking/            # 5 trang luồng đặt xe Renter
│   ├── provider/           # 6 trang luồng quản trị Nhà xe
│   ├── ActivatePage.jsx, AdminDashboard.jsx, BookingReviewsPage.jsx, ...
├── services/               # 11 file API service gọi axios theo từng domain
├── utils/                  # 9 file helper: format tiền, regex validate, token
├── App.jsx                 # Khai báo Router và Layout bọc ngoài
├── index.css               # File CSS toàn cục & định nghĩa Design Tokens Tailwind v4
└── main.jsx                # File khởi tạo React DOM
```

### Mục đích của từng thư mục chính:
- `src/assets/`: Chứa file tĩnh (hình ảnh, logo svg).
- `src/components/`: Chứa các component giao diện, được gom nhóm theo domain nghiệp vụ (`booking`, `driver`, `provider`, `vehicles`,...) và các layout cơ bản (`AppHeader`, `AuthLayout`).
- `src/contexts/`: Chứa `AuthProvider` quản lý phiên đăng nhập và định danh người dùng.
- `src/hooks/`: Đóng gói logic giao tiếp API và xử lý dữ liệu để các trang gọi lại (VD: `useVehicles`, `useDriverSchedule`, `useAuth`).
- `src/pages/`: Chứa toàn bộ các View/Trang hoàn chỉnh gắn với các URL Route.
- `src/services/`: Chứa client Axios và các hàm gọi API RESTful xuống Backend.
- `src/utils/`: Chứa các hàm tiện ích dùng chung (định dạng tiền tệ VNĐ, xử lý chuỗi ngày tháng, validate số điện thoại/biển số xe, parse thông báo lỗi).

---

## 3. DANH SÁCH TRANG VÀ ROUTE

Hệ thống có tổng cộng **28 trang/view** được định tuyến trong `src/App.jsx`:

### Nhóm 1: Trang công khai (Public — Không cần đăng nhập)
1. `/` — `VehicleListPage.jsx`: Trang chủ khám phá danh sách xe cho thuê, bộ lọc tìm kiếm theo hãng/nhiên liệu/chỗ ngồi và xem trên bản đồ.
2. `/vehicles/:id` — `VehicleDetailPage.jsx`: Xem chi tiết cấu hình xe, ảnh nội/ngoại thất, các gói giá giờ/ngày/tháng/năm và nút bấm đặt xe.
3. `/login` — `LoginPage.jsx`: Đăng nhập hệ thống (Email + Password), tự động chuyển hướng theo vai trò (Role).
4. `/register` — `RegisterPage.jsx`: Đăng ký tài khoản mới (Khách thuê hoặc Chủ xe) kèm Google ReCaptcha.
5. `/activate` — `ActivatePage.jsx`: Kích hoạt tài khoản, nhập OTP điện thoại và quét eKYC CCCD / khuôn mặt.
6. `/policy` — `PolicyPage.jsx`: Xem chính sách thuê xe, quy định cọc và điều khoản vận hành.

### Nhóm 2: Trang yêu cầu đăng nhập chung (Mọi Role)
7. `/me` — `MePage.jsx`: Xem và quản lý thông tin hồ sơ tài khoản cá nhân, trạng thái eKYC, đổi mật khẩu.

### Nhóm 3: Trang Quản trị viên (`ADMIN`)
8. `/admin` — `AdminDashboard.jsx`: Dashboard quản trị hệ thống (hiện tại là trang placeholder tạm bọc component `Dashboard.jsx`).

### Nhóm 4: Trang Khách thuê (`RENTER`)
9. `/renter/bookings/new` — `BookingRequestPage.jsx`: Tạo đơn đặt xe mới (chọn gói thuê, thời gian nhận/trả, địa chỉ đón, số khách).
10. `/renter/bookings/:bookingCode/submitted` — `SubmittedBookingPage.jsx`: Màn hình thông báo gửi đơn thành công, chờ chủ xe duyệt.
11. `/renter/bookings/:bookingCode/checkout` — `DepositPaymentPage.jsx`: Thanh toán tiền cọc giữ chỗ khi chủ xe đã chấp nhận đơn.
12. `/bookings/:bookingId/payment` & `/renter/bookings/:bookingId/payment` — `CustomerPaymentPage.jsx`: Khách quét mã VietQR chuyển khoản trực tiếp cho nhà xe và dán link ảnh biên lai để đối soát.
13. `/renter/bookings/:bookingCode` — `BookingDetailPage.jsx`: Chi tiết hành trình đơn thuê, timeline di chuyển, xác nhận nhận/trả xe, phụ phí và khiếu nại (dispute).
14. `/renter/bookings` — `MyBookingsPage.jsx`: Danh sách tất cả các đơn thuê xe của khách hàng theo từng trạng thái.
15. `/renter/payments` — `PaymentHistoryPage.jsx`: Bảng tra cứu lịch sử các giao dịch thanh toán và bảng kê chi tiết phụ phí (`booking_charge`).
16. `/renter/bookings/:bookingCode/reviews` — `BookingReviewsPage.jsx`: Đánh giá chấm sao (1-5 sao) và nhận xét chất lượng xe cũng như tài xế sau chuyến đi.
17. `/renter/yearly-rental` & `/renter/bookings/:bookingCode/yearly-rental` — `YearlyRentalPage.jsx`: Hồ sơ thuê xe dài hạn theo năm, kiểm tra GPLX và xem hợp đồng năm.

### Nhóm 5: Trang Chủ xe / Nhà xe (`PROVIDER`)
18. `/provider` — `ProviderHomePage.jsx`: Trang chủ tổng quan nhà xe (O01), hiển thị các thẻ KPI thống kê nhanh (số xe, đơn thuê, doanh thu, đánh giá).
19. `/provider/vehicles` — `ProviderVehicleListPage.jsx`: Danh sách đội xe của nhà xe (O02), lọc trạng thái duyệt xe, bật/tắt hiển thị xe.
20. `/provider/vehicles/new` — `ProviderVehicleCreatePage.jsx`: Form đăng ký thêm xe mới (O03), tải ảnh bìa, nhập biển số, số khung, số máy.
21. `/provider/vehicles/:id` & `/provider/vehicles/:id/edit` — `ProviderVehicleHubPage.jsx`: Hub quản lý xe toàn diện (O04) gồm 3 Tab: Thông tin xe, Quản lý 4 loại giấy tờ xe, và Cấu hình bảng giá thuê.
22. `/provider/bookings` — `ProviderBookingQueuePage.jsx`: Hàng đợi duyệt đơn đặt xe (O07), chấp nhận hoặc từ chối đơn thuê.
23. `/provider/bookings/:bookingCode` — `ProviderBookingDetailPage.jsx`: Chi tiết đơn thuê phía nhà xe (O08), gán tài xế nhận chuyến, kê khai phụ phí phát sinh.
24. `/provider/finance` — `ProviderFinancePage.jsx`: Quản lý tài chính đối tác (O11), cấu hình số tài khoản ngân hàng nhận tiền, theo dõi yêu cầu thanh toán trực tiếp.
25. `/provider/schedules` — `ProviderSchedulePage.jsx`: Quản lý chặn lịch xe (O05), khóa xe để bảo dưỡng hoặc xử lý sự cố.
26. `/provider/drivers` — `ProviderDriverPage.jsx`: Quản lý đội ngũ tài xế (O06), tạo tài xế mới, duyệt bằng lái GPLX, đình chỉ/kích hoạt tài xế.

### Nhóm 6: Trang Tài xế (`DRIVER`)
27. `/driver/schedule` — `DriverSchedulePage.jsx`: Lịch trình các cuốc xe được điều phối cho tài xế (D01), xem chuyến sắp chạy.
28. `/driver/trips/:bookingCode` — `DriverTripDetailPage.jsx`: Màn hình thực hiện chuyến xe của tài xế (D02), bấm chuyển trạng thái "Đến đón" -> "Bắt đầu chuyến" -> "Kết thúc chuyến" kèm live tracking GPS.

---

## 4. LAYOUT CHUNG

### Các Layout đang có:
1. **`BookingShell` (định nghĩa trực tiếp trong `src/App.jsx`):**
   - Cấu trúc: `<AppHeader />` + `<BookingSubnav />` + `<main className="w-full flex-1">`.
   - Phạm vi: Dùng bọc ngoài các trang đặt xe của Renter (`/policy`, `/renter/bookings/*`).
2. **`ProviderLayout.jsx` (`src/components/provider/ProviderLayout.jsx`):**
   - Cấu trúc: `<ProviderHeader />` + `<main>` + `<footer>`.
   - Phạm vi: **Chỉ được dùng ở 3 trang** (`ProviderFinancePage`, `ProviderBookingQueuePage`, `ProviderBookingDetailPage`).
3. **`DriverLayout.jsx` (`src/components/driver/DriverLayout.jsx`):**
   - Cấu trúc: Header tài xế riêng + `<main>` + Bottom Navigation Bar cố định trên mobile.
   - Phạm vi: Dùng cho `DriverSchedulePage` và `DriverTripDetailPage`.
4. **`AuthLayout.jsx` (`src/components/AuthLayout.jsx`):**
   - Cấu trúc: Chia đôi màn hình (nửa trái ảnh hero nghệ thuật, nửa phải form thao tác).
   - Phạm vi: Dùng cho `LoginPage`, `RegisterPage`, `ActivatePage`.

### Tình trạng dùng chung Layout:
**Bị phân mảnh nặng nề và không nhất quán**:
- **Trang Provider tự dựng lặp lại:** 6 trang Provider (`ProviderHomePage`, `ProviderVehicleListPage`, `ProviderVehicleCreatePage`, `ProviderVehicleHubPage`, `ProviderSchedulePage`, `ProviderDriverPage`) **không dùng `ProviderLayout`** mà tự import riêng `<ProviderHeader>`, rồi tự bọc thẻ `<main>` và tự viết lại thẻ `<footer>` giống hệt nhau ở cuối trang.
- **Trang Renter thiếu Footer:** Các trang `VehicleListPage`, `VehicleDetailPage`, `PaymentHistoryPage`, `CustomerPaymentPage` chỉ import `<AppHeader>` độc lập, hoàn toàn không có footer ở chân trang.
- **`CustomerPaymentPage`:** Tự dựng toàn bộ từ đầu, không dùng `AppHeader`, không dùng `BookingShell`, không có thanh điều hướng nào.

---

## 5. COMPONENT DÙNG CHUNG

### Các Component được tái sử dụng thực tế:
- **Thông báo & Điều hướng:**
  - `Toast.jsx`: Component thông báo nổi (success/error/info) có animation và tự tắt sau thời gian hẹn giờ.
  - `ProtectedRoute.jsx`: Component kiểm tra đăng nhập và phân quyền theo `allowedRoles`.
- **Hiển thị dữ liệu:**
  - `StarRating.jsx`: Hiển thị số sao đánh giá vàng và hỗ trợ chọn số sao (1 đến 5 sao).
  - `VehicleCard.jsx`: Thẻ hiển thị tóm tắt thông tin một chiếc xe ngoài trang chủ.
  - `BookingStatusBadge.jsx` và `PaymentStatusBadge.jsx`: Huy hiệu badge hiển thị trạng thái có màu nền tương ứng.
- **Icon:** `Icons.jsx` đóng gói ~50 biểu tượng SVG.

### Những chỗ đang lặp code hoặc tự style rời rạc (Thiếu Component cơ bản):
1. **Không có component `Button` dùng chung:** Mỗi file tự viết class Tailwind cho nút bấm. Cùng là nút chính (Primary Button) nhưng:
   - `AppHeader`: `bg-brand-600 hover:bg-brand-700 text-white rounded-lg`
   - `DepositPayment`: `bg-[#15803D] hover:bg-[#166534] text-white rounded-xl shadow-[0_8px_24px_rgba(20,34,26,0.06)]`
   - `CustomerPaymentPage`: `bg-[#059669] hover:bg-[#047857] text-white rounded-xl`
   - `ProviderVehicleCreatePage`: `bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl shadow-lg`
2. **Không có component `Input` / `Select` / `Textarea` dùng chung:**
   - `AuthLayout`: Định nghĩa biến chuỗi `INPUT_CLASS = 'w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-11...'`.
   - `CustomerPaymentPage`: Viết inline `rounded-lg border border-slate-300 p-2 text-xs`.
   - `ProviderVehicleCreatePage` & `ProviderDriverPage`: Viết inline `rounded-xl border border-slate-200 bg-white p-3 text-sm`.
3. **Không có component `Modal` dùng chung:** Mỗi khi cần mở popup modal, lập trình viên lại tự viết thẻ overlay `fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs...`.
4. **Bị duplicate hoàn toàn nhiều file Component:**
   - `src/components/booking/AcceptBookingModal.jsx` và `src/components/provider/modals/AcceptBookingModal.jsx` bị trùng lặp chức năng.
   - `src/components/booking/AddChargeModal.jsx` và `src/components/provider/modals/AddChargeModal.jsx` bị trùng lặp.
   - `src/components/booking/AssignDriverModal.jsx` và `src/components/provider/modals/AssignDriverModal.jsx` bị trùng lặp.
   - `src/components/vehicles/Pagination.jsx` và `src/components/payments/PaymentPagination.jsx` có code gần như giống hệt nhau.

---

## 6. CÁCH ĐANG STYLE

### Bảng màu thực tế (Color Palette):
- **Cấu hình Design Tokens tập trung trong `src/index.css` (`@theme`):**
  - Màu thương hiệu (Brand / Emerald):
    - `--color-brand-500: #10b981`
    - `--color-brand-600: #059669`
    - `--color-brand-700: #047857`
    - `--color-brand-900: #064e3b`
  - Màu nền:
    - `--color-surface: #f8f9ff`
    - `--color-surface-dim: #cbdbf5`
    - `--color-surface-container: #e4f1e7`
  - Màu chữ:
    - `--color-ink: #0b1c30`
    - `--color-ink-variant: #3d4a42`
  - Màu trạng thái:
    - `--color-error: #ba1a1a`
    - `--color-primary: #006948`
    - `--color-secondary: #006a63`
- **Tình trạng viết màu trong code:**
  - **Màu sắc bị viết rải rác và hardcode mã HEX tùy tiện trong từng file**, không tận dụng triệt để token của theme.
  - Cùng là màu xanh lá thương hiệu nhưng xuất hiện ít nhất 6 biến thể khác nhau:
    - `#059669` (Tailwind emerald-600)
    - `#047857` (Tailwind emerald-700)
    - `#006948` (Primary token)
    - `#064e3b` (Brand 900)
    - `#15803D` (Tailwind green-700 được hardcode trong `DepositPayment.jsx`)
    - Class `emerald-600` / `emerald-700` trong các trang Provider.

### Font chữ & Cỡ chữ (Typography):
- **Font chữ chính:** `Be Vietnam Pro` (Google Fonts) được gán làm mặc định cho toàn bộ body (`var(--font-sans)`).
- **Font số / Badge / Biển số xe:** `JetBrains Mono` (`var(--font-mono-badge)`).
- **Cỡ chữ:** Dùng các class Tailwind tiêu chuẩn (`text-xs: 12px`, `text-sm: 14px`, `text-base: 16px`, `text-lg: 18px`, `text-2xl: 24px`), nhưng xuất hiện nhiều cỡ chữ tùy ý (arbitrary values) như `text-[10px]`, `text-[11px]`, `text-[13px]`, `text-[13.5px]`, `text-[19px]`.

### Bo góc (Border Radius):
- Dùng không đồng bộ giữa các trang: `rounded-lg` (8px), `rounded-xl` (12px), `rounded-2xl` (16px) và `rounded-full`. Một số card dùng `rounded-xl`, một số card khác lại dùng `rounded-2xl`.

### Khoảng cách (Spacing & Padding):
- Max-width của trang không thống nhất:
  - Header: `max-w-[1400px]`
  - Trang xe & Renter: `max-w-350` (1400px) hoặc `max-w-[1240px]`
  - Trang Provider: Không giới hạn max-width mà dùng `w-full px-6 lg:px-8 xl:px-12`
  - Trang Driver: `max-w-4xl` (896px)

### Dark Mode:
- **KHÔNG HỖ TRỢ** (Không có cấu hình dark mode trong CSS, không có class tiền tố `dark:`, không có nút bấm chuyển theme).

---

## 7. RESPONSIVE VÀ TRẠNG THÁI

### Khả năng Responsive trên điện thoại:
- Đã áp dụng các breakpoint Tailwind: `sm` (640px), `md` (768px), `lg` (1024px), `xl` (1280px).
- **Điểm làm tốt:**
  - Header thu gọn thanh tìm kiếm trên mobile thành icon.
  - Giao diện tài xế (`DriverLayout`) có thanh bottom bar cố định phía dưới màn hình khi xem trên điện thoại.
  - Danh sách xe tự co từ 3-4 cột về 1 cột trên màn hình nhỏ.
- **Điểm yếu trên thiết bị di động:**
  - Các trang quản lý nhiều trường của Provider (`ProviderVehicleHubPage`, `ProviderFinancePage`, `ProviderDriverPage`) có form nhập liệu rất dài và nhiều bảng biểu cuộn ngang (`overflow-x-auto`) gây khó thao tác bằng ngón tay.
  - Các modal popup trên màn hình nhỏ có chiều cao lớn dễ bị tràn màn hình (overflow) nếu không có `max-h-[90vh] overflow-y-auto`.

### Trạng thái giao diện (Loading / Empty / Error):
- **Trạng thái Loading:**
  - Đã có Skeleton Loader ở `VehicleListPage` (`SkeletonCard`) và `DriverSchedulePage` (`TripListSkeleton`).
  - Ở nhiều trang khác chỉ dùng text đơn giản "Đang tải..." hoặc icon quay tròn `animate-spin`.
- **Trạng thái Rỗng (Empty State):**
  - Đã xử lý đầy đủ: Khi danh sách xe, danh sách đơn, hoặc danh sách tài xế không có dữ liệu, hệ thống đều hiển thị box rỗng kèm icon minh họa và nút điều hướng (VD: "Không tìm thấy xe phù hợp", "Chưa có đơn thuê nào").
- **Trạng thái Lỗi (Error State):**
  - Có component `Toast.jsx` nổi góc trên màn hình báo lỗi khi gọi API thất bại.
  - Có các khối thông báo lỗi inline dạng alert (`role="alert"`) kèm nút "Thử lại".
  - Các form có viền đỏ cảnh báo input không hợp lệ.

---

## 8. VẤN ĐỀ VỀ THIẾT KẾ

1. **Các trang khổng lồ nguyên khối (Monolithic File Anti-Pattern):**
   - `ProviderVehicleHubPage.jsx`: **2,270 dòng code** trong một file! Chứa cả thông tin xe, 4 loại giấy tờ, 4 bảng giá và các modal.
   - `ProviderVehicleCreatePage.jsx`: **1,343 dòng code**.
   - `ProviderFinancePage.jsx`: **1,214 dòng code**.
   - `ProviderDriverPage.jsx`: **1,148 dòng code**.
   *(File quá dài, khó bảo trì, nhiều logic state lồng nhau).*

2. **Lệch tông màu chủ đạo giữa các màn hình:**
   - Màn đặt cọc `DepositPayment.jsx` dùng xanh lá mạ `#15803D` và nền đen cỏ `#14221A`.
   - Màn Provider `ProviderHeader.jsx` dùng xanh ngọc `emerald-600`.
   - Màn thanh toán trực tiếp `CustomerPaymentPage.jsx` dùng `#059669` và `#064e3b`.
   *(Mỗi màn hình có cảm giác thuộc về một website khác nhau).*

3. **Bất cập UX ở màn hình gửi biên lai thanh toán:**
   - Trong `CustomerPaymentPage.jsx` (dòng 331-341): Yêu cầu người dùng dán text link ảnh biên lai (`https://example.com/receipt.jpg`) thay vì cho phép bấm tải file ảnh chụp màn hình chuyển khoản từ điện thoại!

4. **Trùng lặp mã nguồn (Code Duplication):**
   - Các file modal ở `src/components/booking/` và `src/components/provider/modals/` bị trùng lặp tên và code (`AcceptBookingModal.jsx`, `AddChargeModal.jsx`, `AssignDriverModal.jsx`).
   - Hàm `formatCurrency` đã được viết chuẩn ở `src/utils/formatCurrency.js`, nhưng nhiều file lại tự viết lại hàm `formatVnd(amount)` bên trong file trang.

5. **Trang Quản trị (Admin) chưa được phát triển:**
   - `AdminDashboard.jsx` và `Dashboard.jsx` chỉ có 1 card trắng hiện vài dòng text tạm bợ, chưa có tính năng quản trị thực tế nào.

---

## 9. TRÍCH CODE MẪU

### 9.1. File Layout chính tiêu biểu (`src/components/provider/ProviderLayout.jsx`)

```jsx
import ProviderHeader from './ProviderHeader'

export default function ProviderLayout({ active, children }) {
  // Mapping tên tab tiếng Việt của TV5 sang activeTab chuẩn của ProviderHeader
  const activeTabMap = {
    'Tổng quan': 'home',
    'Xe của tôi': 'vehicles',
    'Đội xe của tôi': 'vehicles',
    'Tài xế': 'drivers',
    'Đơn thuê': 'bookings',
    'Chặn lịch': 'schedules',
    'Tài chính': 'finance',
  }
  const activeTab = activeTabMap[active] || 'home'

  return (
    <div className="min-h-screen bg-[#f8f9ff] text-slate-800 font-sans">
      {/* Top Header toàn màn hình chuẩn TV4 */}
      <ProviderHeader activeTab={activeTab} />

      {/* Main Content Area */}
      <main className="w-full px-4 sm:px-6 lg:px-8 xl:px-12 py-8">
        {children}
      </main>

      {/* Footer chuẩn EcoMotion */}
      <footer className="mt-12 border-t border-slate-200 bg-white py-6">
        <div className="w-full px-6 lg:px-8 xl:px-12 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <span>© 2026 EcoMotion VN. Nền tảng điều phối vận tải &amp; chia sẻ phương tiện di chuyển xanh.</span>
          <div className="flex items-center gap-6">
            <span className="hover:text-emerald-700 transition cursor-pointer">Quy chế hoạt động</span>
            <span className="hover:text-emerald-700 transition cursor-pointer">Chính sách đối tác</span>
            <span className="hover:text-emerald-700 transition cursor-pointer">Biểu phí</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
```

---

### 9.2. Component dùng chung tiêu biểu (`src/components/Toast.jsx`)

```jsx
import { useEffect } from 'react'
import { IconCheckCircle, IconAlertTriangle, IconInfo } from './icons/Icons'

const VARIANT_CLASS = {
  error: 'border-red-200 bg-red-50 text-red-800 shadow-red-900/10',
  success: 'border-brand-500 bg-brand-700 text-white shadow-emerald-900/20',
  info: 'border-slate-600 bg-slate-900 text-white shadow-slate-900/20',
}

export default function Toast({ message, type = 'error', duration = 4000, onClose }) {
  useEffect(() => {
    if (!message) return undefined
    const timer = setTimeout(() => onClose?.(), duration)
    return () => clearTimeout(timer)
  }, [message, duration, onClose])

  if (!message) return null

  return (
    <div
      role="alert"
      className={`fixed right-4 left-4 top-24 z-[9999] flex items-center gap-3 rounded-xl border px-4 py-3.5 text-[14px] font-medium shadow-2xl transition-all sm:left-auto sm:right-6 sm:max-w-[420px] ${
        VARIANT_CLASS[type] ?? VARIANT_CLASS.info
      }`}
    >
      {type === 'error' ? (
        <IconAlertTriangle className="h-5 w-5 shrink-0 text-red-600" />
      ) : type === 'success' ? (
        <IconCheckCircle className="h-5 w-5 shrink-0 text-white" />
      ) : (
        <IconInfo className="h-5 w-5 shrink-0 text-slate-300" />
      )}
      <span className="flex-1 leading-snug">{message}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="Đóng"
        className="px-1 py-0.5 text-base leading-none opacity-70 hover:opacity-100 cursor-pointer"
      >
        ✕
      </button>
    </div>
  )
}
```

---

### 9.3. Trang sơ sài / xấu nhất (`src/pages/Dashboard.jsx` — dùng cho Admin)

```jsx
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { IconCar } from '../components/icons/Icons'

export default function Dashboard({ title, subtitle }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between gap-4 border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-slate-900">
          <IconCar className="h-5 w-5 text-brand-600" />
          EcoMotion <span className="text-[11px] font-semibold text-brand-600">VN</span>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/me"
            className="rounded-lg border border-slate-200 bg-white px-4.5 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            Trang của tôi
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-200 bg-white px-4.5 py-2.5 text-[13px] font-semibold text-slate-700 transition-all hover:border-slate-300 hover:bg-slate-100"
          >
            Đăng xuất
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-6 py-10">
        <div className="rounded-xl border border-slate-200 bg-white p-8 shadow-card">
          <h1 className="mb-1.5 text-2xl font-bold tracking-tight">{title}</h1>
          <p className="mb-6 text-sm text-slate-500">{subtitle}</p>

          <dl className="grid gap-3">
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Email</dt>
              <dd className="font-semibold text-slate-900">{user?.email}</dd>
            </div>
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Vai trò</dt>
              <dd>
                <span className="inline-block rounded-full bg-brand-100 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-brand-700">
                  {user?.role}
                </span>
              </dd>
            </div>
            <div className="flex justify-between gap-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm">
              <dt className="font-medium text-slate-500">Trạng thái tài khoản</dt>
              <dd className="font-semibold text-slate-900">{user?.status}</dd>
            </div>
          </dl>
        </div>
      </main>
    </div>
  )
}
```

---

### 9.4. Nội dung file CSS toàn cục (`src/index.css`)

```css
@import 'tailwindcss';

@theme {
  --font-sans: 'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;

  --color-brand-50: #ecfdf5;
  --color-brand-100: #d1fae5;
  --color-brand-200: #a7f3d0;
  --color-brand-300: #6ee7b7;
  --color-brand-400: #34d399;
  --color-brand-500: #10b981;
  --color-brand-600: #059669;
  --color-brand-700: #047857;
  --color-brand-800: #065f46;
  --color-brand-900: #064e3b;

  --color-surface: #f8f9ff;
  --color-surface-dim: #cbdbf5;
  --color-ink: #0b1c30;
  --color-ink-variant: #3d4a42;
  --color-outline: #6d7a72;
  --color-outline-variant: #bccac0;

  --color-error: #ba1a1a;
  --color-error-container: #ffdad6;
  --color-on-error-container: #93000a;

  --color-primary: #006948;
  --color-on-primary: #ffffff;
  --color-primary-container: #00855d;
  --color-on-primary-container: #f5fff7;
  --color-primary-fixed: #85f8c4;
  --color-secondary: #006a63;
  --color-secondary-fixed: #9cf2e8;
  --color-tertiary: #0051d5;
  --color-tertiary-fixed: #dbe1ff;
  --color-on-surface: #121e18;
  --color-on-surface-variant: #3d4a42;
  --color-surface-container-lowest: #ffffff;
  --color-surface-container-low: #e9f7ed;
  --color-surface-container: #e4f1e7;
  --color-surface-container-high: #deece2;
  --color-surface-container-highest: #d8e6dc;

  --font-mono-badge: 'JetBrains Mono', ui-monospace, monospace;

  --radius-lg: 0.75rem;
  --radius-xl: 1rem;

  --shadow-card: 0 2px 8px -2px rgba(15, 23, 42, 0.05);
  --shadow-lg: 0 12px 24px -4px rgba(15, 23, 42, 0.08),
    0 4px 8px -2px rgba(15, 23, 42, 0.03);
}

html,
body {
  margin: 0;
  padding: 0;
}

.leaflet-interactive[role='button']:focus-visible { outline: 3px solid #1d4ed8; outline-offset: 2px; }
.vehicle-map-popup { min-width: 0; font-family: 'Be Vietnam Pro', sans-serif; }
.vehicle-map-popup-address { margin: 0 0 8px; color: #475569; font-size: 11px; }
.vehicle-map-popup-hint { margin: 0 0 8px; color: #64748b; font-size: 10px; }
.vehicle-map-popup-layout { display: grid; grid-template-columns: minmax(0, 4fr) minmax(0, 6fr); gap: 12px; align-items: start; }
.vehicle-map-popup-list { min-width: 0; max-height: 240px; overflow-y: auto; }
.vehicle-map-popup-car { display: grid; gap: 4px; padding: 7px 0; border-top: 1px solid #e2e8f0; }
.vehicle-map-popup-car:hover, .vehicle-map-popup-car:focus-within { background: #f0fdf4; }
.vehicle-map-popup-car-info { display: grid; gap: 2px; width: 100%; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; }
.vehicle-map-popup-car-info:focus-visible, .vehicle-map-popup-car a:focus-visible { outline: 2px solid #047857; outline-offset: 2px; border-radius: 2px; }
.vehicle-map-popup-car strong { color: #0f172a; font-size: 12px; }
.vehicle-map-popup-car span { color: #475569; font-size: 11px; }
.vehicle-map-popup-car a { width: fit-content; color: #047857; font-size: 11px; font-weight: 700; }
.vehicle-map-popup-preview { min-width: 0; margin: 0; }
.vehicle-map-popup-preview[hidden], .vehicle-map-popup-preview img[hidden], .vehicle-map-popup-preview p[hidden] { display: none; }
.vehicle-map-popup-preview img { display: block; width: 100%; height: 150px; border-radius: 8px; object-fit: cover; }
.vehicle-map-popup-preview p { display: grid; min-height: 150px; place-items: center; margin: 0; padding: 12px; border-radius: 8px; background: #f1f5f9; color: #475569; text-align: center; font-size: 11px; }
.vehicle-map-popup-preview figcaption { margin-top: 4px; color: #334155; font-size: 11px; font-weight: 600; }

body {
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.5;
  color: var(--color-ink);
  background: var(--color-surface);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#root {
  min-height: 100vh;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: #f1f5f9;
}
::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}
::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* Material Symbols font helper (booking UI) */
.material-symbols-outlined {
  font-family: 'Material Symbols Outlined';
  font-weight: normal;
  font-style: normal;
  font-size: 24px;
  line-height: 1;
  letter-spacing: normal;
  text-transform: none;
  display: inline-block;
  white-space: nowrap;
  word-wrap: normal;
  direction: ltr;
  -webkit-font-feature-settings: 'liga';
  -webkit-font-smoothing: antialiased;
}

.scrollbar-none::-webkit-scrollbar {
  display: none;
}
.scrollbar-none {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
```

*(Lưu ý: Dự án không có file `tailwind.config.js` vì Tailwind CSS v4 cấu hình theme trực tiếp qua directive `@theme` trong `index.css`).*

