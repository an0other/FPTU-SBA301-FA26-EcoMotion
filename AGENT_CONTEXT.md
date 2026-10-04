# AGENT_CONTEXT.md — EcoMotion

> **Tài liệu ngữ cảnh chính cho AI agent.**  
> Đọc file này **trước tiên** mỗi khi mở lại phiên làm việc để khôi phục hiểu biết về dự án.  
> Cập nhật file này mỗi khi có thay đổi kiến trúc hoặc quyết định kỹ thuật mới.
>
> **Cập nhật lần cuối:** 2026-10-02 · **Phiên bản dự án:** Spec v8.0 / Data model v10.1 / Backend: auth module · **7 mục**
>
> **File này KHÔNG chứa trạng thái code hay danh sách task.** Nó chứa **ý định** (luật, quyết định, thứ tự làm). Trạng thái thật đọc từ code / Swagger / git.

---

## ĐIỂM VÀO — ĐỌC THEO THỨ TỰ NÀY

Đây là **checklist khởi động** cho agent khi mở phiên mới:

| # | File                                                            | Đọc khi nào                                |
| - | --------------------------------------------------------------- | ------------------------------------------ |
| 1 | **`AGENT_CONTEXT.md`** (file này)                               | **Luôn luôn — đầu tiên**                   |
| 2 | `Backend/EcoMotion/AGENT_CODE_STRUCTURE.md`                     | **Trước khi tạo/sửa bất kỳ file Java nào** |
| 3 | `docs/SBA301_FALL26.md`                                         | Khi cần hiểu nghiệp vụ sâu                 |
| 4 | `docs/Conceptual_v10.1.mmd` + `docs/GIAI_THICH_ENTITY_v10.1.md` | Khi chạm vào dữ liệu                       |
| 5 | Skill `mermaid-diagram-qc`                                      | Khi sửa sơ đồ Mermaid                      |

**5 nguyên tắc bất di bất dịch:**

> **1. `docs/SBA301_FALL26.md` là NGUỒN SỰ THẬT về nghiệp vụ.** Code phải theo spec, không ngược lại.  
> **2. Mọi tính tiền đọc từ `BOOKING_POLICY_SNAPSHOT`, không từ `RENTAL_PLAN`/`PROVIDER`.**  
> **3. Giữ module boundary — không truy cập repository của module khác.**  
> **4. Đơn giản hơn là tốt hơn — không tạo abstraction chỉ để "trông có kiến trúc".**  
> **5. Cập nhật file này khi có quyết định kiến trúc mới.**

---

## MỤC LỤC

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Cấu trúc thư mục & công nghệ](#2-cấu-trúc-thư-mục--công-nghệ)
3. [Kiến trúc hệ thống & luồng dữ liệu](#3-kiến-trúc-hệ-thống--luồng-dữ-liệu)
4. [Quy ước coding style & naming convention](#4-quy-ước-coding-style--naming-convention)
5. [Quyết định kỹ thuật quan trọng](#5-quyết-định-kỹ-thuật-quan-trọng)
6. [Dependencies & setup môi trường](#6-dependencies--setup-môi-trường)
7. [Bẫy & bài học đã gặp](#7-bẫy--bài-học-đã-gặp)

---

## 1. TỔNG QUAN DỰ ÁN

### 1.1. Mục tiêu

Xây dựng **nền tảng cho thuê xe hơi có tài xế theo khối thời gian** (chauffeur-hire) — kết nối **khách hàng cá nhân** với các **nhà cung cấp cung cấp trọn gói xe kèm tài xế**. Đây là **đồ án môn SBA301** (FPT University, Fall 2026).

**Không phải:**

- Không phải **thuê xe tự lái** (self-drive) — trừ gói năm.
- Không phải **gọi xe công nghệ** (Grab/Be/Xanh SM) — bán theo chuyến A→B.

**Sản phẩm bán là gì:** một **khối thời gian** của một **chiếc xe cụ thể** — khách chọn đúng chiếc xe (xem ảnh, biển số, đánh giá), thuê theo **gói giờ / ngày / tháng / năm**.

|                    | Gọi xe công nghệ            | Nền tảng này                                 |
| ------------------ | --------------------------- | -------------------------------------------- |
| Đơn vị bán         | Một chuyến A→B              | Một **khối thời gian** của **một xe cụ thể** |
| Cách tính tiền     | Theo km + thời gian thực tế | Theo **gói thời gian** (slot tuyến tính)     |
| Chọn xe            | Hệ thống ghép bất kỳ        | **Khách chọn đúng chiếc đó**                 |
| Xác nhận           | Tự động vài giây            | Nhà cung cấp **accept/deny** từng đơn        |
| Thực thể trung tâm | Chuyến đi, Tài xế           | **Xe, lịch xe**                              |
| Điểm đến           | Một điểm đến                | **Không quản điểm đến** — chỉ 1 điểm đón     |

### 1.2. Phạm vi

**Trong phạm vi:**

- 4 actor: **Renter** (người thuê) · **Provider** (nhà cung cấp) · **Driver** (tài xế) · **Admin**.
- 4 hình thức thuê: **giờ · ngày · tháng · năm**.
- Quản lý xe, gói giá, lịch xe (trục thời gian), đơn thuê, bóc tách chi phí, giao dịch tiền, đánh giá, tranh chấp.
- Chính sách huỷ hai chiều (chỉ hoàn cọc, không bồi thường).

**Ngoài phạm vi (đã cắt có chủ đích):**

- **B2B phía người thuê** — khách thuê **chỉ là cá nhân**. Không có bảng `COMPANY`, không tài khoản doanh nghiệp.
  - ⚠️ **Nhưng phía nhà cung cấp VẪN được là doanh nghiệp** (thường là doanh nghiệp nhỏ) — có `PROVIDER.provider_type = BUSINESS`.
- **Bồi thường / đền bù** — không có `charge_type=COMPENSATION`, không phạt provider, không quỹ bồi thường, không `caused_by_booking_id`.
- **Lộ trình / điểm trả / điểm ghé** — chỉ có **1 điểm đón duy nhất**.
- **Báo cáo sự cố** (hư hỏng/tai nạn) — nhà cung cấp xử lý ngoài nền tảng.
- **Phụ phí vệ sinh** — đã bỏ.
- **Phạt nguội** — xử lý ngoài nền tảng giữa nhà cung cấp và tài xế.
- **Thống kê cá nhân của renter**.

### 1.3. Đối tượng người dùng

| Actor                               | Mô tả                            | Việc chính                                                                                                                       |
| ----------------------------------- | -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Renter** — cá nhân                | Khách thuê xe, **chỉ cá nhân**   | Tìm xe, chọn gói + thời lượng + điểm đón, theo dõi chuyến, thanh toán, đánh giá                                                  |
| **Provider** — chủ xe / DN cho thuê | Có đội xe, bãi/depot, đội tài xế | Đăng xe, tạo gói giá, accept/deny, **phân công tài xế**, **tạo & quản lý tài khoản tài xế**, tất toán chuyến, đối soát doanh thu |
| **Driver** — tài xế                 | Thuộc **đúng một** nhà cung cấp  | Nhận chuyến được phân công, xác nhận đến điểm đón / bắt đầu / kết thúc, chạy theo yêu cầu của khách                              |
| **Admin** — quản trị viên           | Vận hành nền tảng                | Duyệt xe/hồ sơ, cấu hình chính sách, xử lý tranh chấp, thống kê                                                                  |

**Quy tắc định danh:** **một tài khoản — một vai trò chính** (`USER.role` ∈ `RENTER | PROVIDER | DRIVER | ADMIN`). Muốn vừa thuê vừa cho thuê → đăng ký **2 tài khoản** (2 email). Mỗi nhà cung cấp chỉ có **một tài khoản duy nhất**.

---

## 2. CẤU TRÚC THƯ MỤC & CÔNG NGHỆ

### 2.1. Vị trí & cây thư mục

```
FPTU-SBA301-FA26-EcoMotion/               ← GIT REPO (origin: GitHub, public)
├── README.md
├── .gitignore                            ← ignore cho CẢ repo (Java + Node/React)
├── AGENT_CONTEXT.md                      ← FILE NÀY — điểm vào cho agent
├── docs/                                 ← TÀI LIỆU DỰ ÁN
│   ├── SBA301_FALL26.md                  ← ĐẶC TẢ CHỨC NĂNG (v8.0) — NGUỒN SỰ THẬT nghiệp vụ
│   ├── Conceptual_v10.1.mmd              ← SƠ ĐỒ KHÁI NIỆM (Mermaid erDiagram) — 15 entity
│   ├── Conceptual_v10.1.svg              ← Bản vẽ xuất ra (615 KB)
│   └── GIAI_THICH_ENTITY_v10.1.md        ← Giải thích từng entity
├── Backend\
│   └── EcoMotion\                        ← Spring Boot app
│       ├── pom.xml
│       ├── AGENT_CODE_STRUCTURE.md       ← QUY ƯỚC CẤU TRÚC CODE (đọc trước khi code Java)
│       ├── mvnw / mvnw.cmd
│       ├── .gitignore                    ← ignore riêng cho backend (chi tiết hơn)
│       └── src\
│           ├── main\
│           │   ├── java\com\fptufa26\ecomotion\
│           │   │   ├── EcoMotionApplication.java
│           │   │   ├── auth\           ← module đăng nhập
│           │   │   ├── user\           ← module người dùng
│           │   │   ├── common\         ← config / exception / response / security
│           │   │   └── seed\           ← DevDataSeeder (gitignored)
│           │   └── resources\
│           │       ├── application.properties           ← CÓ credentials (gitignored)
│           │       └── application.properties.example  ← template, an toàn commit
│           └── test\
│               ├── java\com\fptufa26\ecomotion\
│               │   ├── auth\controller\AuthApiIntegrationTest.java
│               │   ├── auth\service\AuthServiceImplTest.java
│               │   ├── common\security\JwtServiceTest.java
│               │   └── EcoMotionApplicationTests.java
│               └── resources\application.properties     ← H2 in-memory, secret giả (an toàn)
└── Frontend\                             ← ⚠️ TRỐNG — chưa khởi tạo
```

**Quy tắc ignore:** có **2 tầng** `.gitignore` — root (bao quát cả repo: Java + Node/React + IDE + secret) và `Backend/EcoMotion/` (chi tiết cho Spring Boot). Cả 2 đều chặn `application.properties` chứa credentials.

**Ngoài repo (công cụ QA, không thuộc dự án):** `Group_Project\lib\mermaid.min.js`, `Group_Project\qc-scripts\`, `Group_Project\note.txt`  
→ Cân nhắc **không** đẩy vào repo vì chỉ là công cụ nội bộ, làm repo phình.

### 2.2. Công nghệ sử dụng

**Backend:**

| Thành phần  | Công nghệ                                   | Ghi chú                                      |
| ----------- | ------------------------------------------- | -------------------------------------------- |
| Ngôn ngữ    | **Java 21**                                 |                                              |
| Framework   | **Spring Boot 4.1.1**                       | Parent BOM                                   |
| Web         | `spring-boot-starter-webmvc`                |                                              |
| Persistence | **Spring Data JPA** + **Hibernate**         |                                              |
| Database    | **PostgreSQL**                              | `jdbc:postgresql://localhost:5432/ecomotion` |
| Security    | **Spring Security** + **JWT (JJWT 0.12.6)** | Access token 15m + refresh token cookie      |
| Validation  | `spring-boot-starter-validation`            | Jakarta Bean Validation                      |
| API Docs    | **springdoc-openapi 3.1.0**                 | Swagger UI tại `/swagger-ui.html`            |
| Boilerplate | **Lombok**                                  | `@Getter @Setter @NoArgsConstructor`         |
| Build       | **Maven** (`mvnw` wrapper)                  |                                              |
| Test DB     | **H2** (in-memory, scope test)              | Giữ test độc lập với PostgreSQL local        |
| DevTools    | `spring-boot-devtools`                      | Hot reload                                   |


**Frontend:**

| Thành phần       | Công nghệ                                                       | Trạng thái                                                                 |
| ---------------- | --------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Framework        | **ReactJS 19**                                                   | ✅ Đã khởi tạo tại `Frontend/EcoMotion/` (JSX, không TypeScript)           |
| Build tool       | **Vite 8**                                                       | ✅ Dev proxy `/api` → `http://localhost:8080` (tránh CORS, cookie first-party) |
| Routing          | **react-router-dom 7**                                           | ✅ `/login` public + route bảo vệ theo role                                |
| UI               | **Tailwind CSS v4** (`@tailwindcss/vite`) + design tokens EcoMotion trong `src/index.css` (`@theme`) | ✅ Font Be Vietnam Pro; brand-*/ink/surface/shadow-card; không còn file `.css` riêng |
| HTTP client      | **axios** (`services/apiClient.js`, `baseURL: '/api'`, `withCredentials`) | ✅ Interceptor gắn Bearer + chuẩn hoá lỗi                         |
| State (auth)     | **Context API** (`contexts/AuthProvider.jsx` + `hooks/useAuth.js`) | ✅ Login / Logout / khôi phục phiên qua `/api/auth/me`                  |

> **Ghi chú FE:** Đã hoàn thành **chức năng đăng nhập** (login-only): trang `pages/LoginPage.jsx` (port từ `Login-regis-template/code.html`, chỉ tab Đăng nhập), `AuthContext` + `ProtectedRoute`, dashboard placeholder 3 role. Lưu ý: `LoginRequest` của backend **chỉ nhận `email`** (không nhận SĐT) nên label ô nhập đã đổi thành "Email". Đăng ký / quên mật khẩu / auto-refresh 401 **chưa** làm (ngoài phạm vi). CORS backend vẫn mở cho cả 3000 và 5173.
>
> **UI dùng Tailwind CSS v4** (không dùng Tailwind CDN nữa): plugin `@tailwindcss/vite` gắn trong `vite.config.js`, `src/index.css` chứa `@import 'tailwindcss'` + `@theme` port palette EcoMotion từ `DESIGN.md` (`--color-brand-*`, `--color-ink`, `--color-surface`, `--shadow-card`, radius). Dùng utility class trực tiếp trong JSX (`bg-brand-600`, `text-slate-900`, `rounded-lg`…). Toàn bộ component đã chuyển sang Tailwind, **không còn file `.css` riêng lẻ** (chỉ giữ `src/index.css`).

**Công cụ QA tài liệu (không thuộc runtime app):**

- **Mermaid** + **Chrome headless/CDP** để validate và đo layout sơ đồ.
- Skill `mermaid-diagram-qc` (đã cài toàn cục) chứa quy trình + các bẫy đã gặp.

---

## 3. KIẾN TRÚC HỆ THỐNG & LUỒNG DỮ LIỆU

### 3.1. Kiến trúc tổng thể

```
┌──────────────────┐         ┌──────────────────────┐        ┌──────────────┐
│  ReactJS (FE)    │  HTTPS  │  Spring Boot (BE)    │  JDBC  │  PostgreSQL  │
│  localhost:5173  │ ──────► │  localhost:8080      │ ─────► │  :5432       │
│  (chưa init)     │  REST   │  Modular Monolith    │        │  ecomotion   │
└──────────────────┘         └──────────────────────┘        └──────────────┘
        │                              │
        │  JWT (Authorization header)  │  JWT sign/verify (HS256)
        │  Refresh token (httpOnly     │
        │  cookie, path=/api/auth)     │
        └──────────────────────────────┘
```

### 3.2. Kiến trúc backend — Modular Monolith + N-Layer

**Quy ước cốt lõi** (chi tiết đầy đủ ở `Backend/EcoMotion/AGENT_CODE_STRUCTURE.md` — **BẮT BUỘC đọc trước khi tạo file Java**):

Tổ chức theo **module nghiệp vụ trước, layer sau**:

```
com.fptufa26.ecomotion/
├── EcoMotionApplication.java
├── common/                     ← chỉ dùng cho thứ THỰC SỰ dùng chung
│   ├── config/                 ← CorsConfig, OpenApiConfig, SecurityConfig
│   ├── exception/              ← ErrorResponse, GlobalExceptionHandler
│   ├── response/               ← ApiResponse<T>
│   └── security/               ← JwtService, JwtAuthenticationFilter, SecurityUser, ...
├── auth/                       ← module: đăng nhập / token
│   ├── controller/  service/  repository/  entity/  dto/  mapper/  exception/
└── user/                       ← module: người dùng
    ├── entity/  repository/  service/  dto/
```

**Layer responsibilities:**

| Layer         | Trách nhiệm                                          | **KHÔNG được**                      |
| ------------- | ---------------------------------------------------- | ----------------------------------- |
| `controller/` | Nhận HTTP, validate input, gọi service, trả response | ❌ Chứa business rule                |
| `service/`    | Business logic, orchestration, transaction boundary  |                                     |
| `repository/` | Persistence, query (Spring Data JPA)                 | ❌ Chứa workflow nghiệp vụ           |
| `entity/`     | JPA mapping, trạng thái entity                       | ❌ HTTP concern, expose thẳng ra API |
| `dto/`        | Truyền dữ liệu qua boundary                          | ❌ Tên mơ hồ kiểu `DataDTO`          |
| `mapper/`     | Entity ↔ DTO khi phức tạp/tái sử dụng                | ❌ Tạo mapper cho phép gán 1 dòng    |
| `exception/`  | Exception riêng của module                           |                                     |

**Quy tắc phụ thuộc module (BẮT BUỘC):**

```
✅ Module A Service → Module B Service (public) → Module B Repository
❌ Module A Service → Module B Repository          (vi phạm ownership)
❌ A → B → A                                        (circular dependency)
```

**Quy tắc khác:**

- Transaction đặt ở **service layer**, không ở controller.
- **Không** dùng package layer toàn cục (`controller/ service/ repository/` ở cấp gốc).
- **Không** biến `common/` thành bãi rác.
- **Ưu tiên cấu trúc đơn giản nhất** giữ được module rõ ràng — không tạo abstraction chỉ để "trông có kiến trúc".

### 3.3. Luồng dữ liệu — luồng chính của một đơn thuê

```
1. Khách chọn xe + gói + thời lượng + điểm đón
        │
2. GỬI YÊU CẦU ──────────────────────────────────────────────┐
        │                                                      │
        │  ★ CHỐT SNAPSHOT TẠI ĐÂY (status=PENDING)            │
        │    → ghi BOOKING_POLICY_SNAPSHOT                     │
        │    (đơn giá slot, % cọc, grace, % phụ phí,           │
        │     bán kính, chờ miễn phí...)                       │
        ▼                                                      │
3. Provider ACCEPT / DENY  ────────────────────────────────────┤
        │  accept → kiểm tra slot trống, không giao lịch       │
        │  deny   → KHÔNG kèm lý do                            │
        ▼                                                      │
4. Khách trả CỌC → PAYMENT_TRANSACTION(type=DEPOSIT_PAY)      │
        │                                                      │
        │  Nếu khách về trễ / đổi xe → VEHICLE_SCHEDULE         │
        │  và BOOKING_POLICY_SNAPSHOT ĐÃ CHỐT, không đổi        │
        ▼                                                      │
5. Phân công DRIVER (gán theo chuyến, KHÔNG gán trước)         │
        ▼                                                      │
6. IN_PROGRESS → tài xế xác nhận bắt đầu (actual_start_at)     │
        │  (mọi tính tiền về sau ĐỌC TỪ SNAPSHOT)              │
        ▼                                                      └── Đây là nguyên tắc
7. COMPLETED — CẢ HAI bên xác nhận                             "giá chốt lúc gửi yêu cầu"
        │  renter_confirmed_at + provider_confirmed_at
        ▼
8. Bóc tách BOOKING_CHARGE (BASE_RENTAL, DEPOSIT, OVERTIME, ...)
        │
9. Tất toán → PAYMENT_TRANSACTION(type=SETTLEMENT)
        ▼
10. REVIEW (xe và/hoặc tài xế) + DISPUTE (nếu có khiếu nại)
```

### 3.4. Kiến trúc dữ liệu — 15 entity, 4 tầng

Sơ đồ khái niệm đầy đủ: `docs/Conceptual_v10.1.mmd` · giải thích chi tiết: `docs/GIAI_THICH_ENTITY_v10.1.md`.

```
TẦNG 1 — CON NGƯỜI          USER ─┬─ PROVIDER   (1-0..1)
                                 ├─ RENTER
                                 └─ DRIVER

TẦNG 2 — TÀI SẢN & SẢN PHẨM  PROVIDER ─ VEHICLE ─┬─ VEHICLE_IMAGE
                                                 ├─ VEHICLE_DOCUMENT
                                                 ├─ RENTAL_PLAN
                                                 └─ VEHICLE_SCHEDULE

TẦNG 3 — GIAO DỊCH           BOOKING ─┬─ BOOKING_POLICY_SNAPSHOT  (1-1) — LUẬT
                                      ├─ BOOKING_CHARGE                 — KẾT QUẢ
                                      ├─ PAYMENT_TRANSACTION            — TIỀN
                                      └─ VEHICLE_SCHEDULE               — LỊCH

TẦNG 4 — SAU CHUYẾN          BOOKING ─┬─ REVIEW   (đánh giá xe / tài xế)
                                      └─ DISPUTE  (tranh chấp)
```

**⭐ BA TẦNG TIỀN — không chồng nhau (điểm dễ hiểu sai nhất):**

| Bảng                      | Vai trò     | Trả lời câu hỏi                      | Ai tạo                              |
| ------------------------- | ----------- | ------------------------------------ | ----------------------------------- |
| `BOOKING_POLICY_SNAPSHOT` | **LUẬT**    | *Tính bằng công thức nào?*           | Hệ thống, lúc khách gửi yêu cầu     |
| `BOOKING_CHARGE`          | **KẾT QUẢ** | *Phải trả khoản gì, bao nhiêu?*      | Hệ thống sinh + provider ghi tay    |
| `PAYMENT_TRANSACTION`     | **TIỀN**    | *Đã chuyển thật bao nhiêu, lúc nào?* | Cổng thanh toán / ghi nhận thủ công |

**Bảng entity (15):**

| #  | Entity                    | Vai trò ngắn                                                 |
| -- | ------------------------- | ------------------------------------------------------------ |
| 1  | `USER`                    | Tài khoản đăng nhập — 1 account = 1 role                     |
| 2  | `PROVIDER`                | Nhà cung cấp — chính sách giá cấp nhà xe (hoa hồng, % cọc)   |
| 3  | `RENTER`                  | Người thuê — CCCD/eKYC, GPLX chỉ cần cho gói năm             |
| 4  | `DRIVER`                  | Tài xế — nhà xe tạo hộ, admin duyệt, thuộc 1 nhà xe          |
| 5  | `VEHICLE`                 | Xe — 3 tầng trường (bất biến / duyệt lại / tự do)            |
| 6  | `VEHICLE_IMAGE`           | Thư viện ảnh — `display_order=0` là ảnh bìa                  |
| 7  | `VEHICLE_DOCUMENT`        | 4 loại giấy tờ xe, mỗi loại có hạn riêng                     |
| 8  | `RENTAL_PLAN`             | Gói giá theo **từng xe** — toàn bộ tham số tính tiền         |
| 9  | `VEHICLE_SCHEDULE`        | **Lịch xe** = KẾ HOẠCH, trục thời gian, chống double-booking |
| 10 | `BOOKING`                 | **Đơn thuê** = THỰC TẾ — trung tâm hệ thống                  |
| 11 | `BOOKING_POLICY_SNAPSHOT` | ⭐ **Chốt luật tính tiền lúc đặt** — bất biến                 |
| 12 | `BOOKING_CHARGE`          | Bóc tách hoá đơn — các khoản phải trả                        |
| 13 | `PAYMENT_TRANSACTION`     | Giao dịch tiền thực chuyển — append-only                     |
| 14 | `REVIEW`                  | Đánh giá xe và/hoặc tài xế — nguồn sự thật của rating        |
| 15 | `DISPUTE`                 | Tranh chấp / khiếu nại                                       |

**Ánh xạ DB đã code:** hiện chỉ có `USER` (bảng `users`) + `auth.RefreshToken`. Các entity còn lại **chưa triển khai**.

---

## 4. QUY ƯỚC CODING STYLE & NAMING CONVENTION

### 4.1. Java / Spring Boot

**Naming:**

| Loại              | Quy ước                           | Ví dụ                                      |
| ----------------- | --------------------------------- | ------------------------------------------ |
| Controller        | `<Domain>Controller`              | `AuthController`                           |
| Service interface | `<Domain>Service`                 | `AuthService`, `UserService`               |
| Service impl      | `<Domain>ServiceImpl`             | `AuthServiceImpl`, `UserServiceImpl`       |
| Repository        | `<Domain>Repository`              | `RefreshTokenRepository`, `UserRepository` |
| Entity            | Tên domain                        | `User`, `RefreshToken`                     |
| Request DTO       | `<Action><Domain>Request`         | `LoginRequest`                             |
| Response DTO      | `<Domain>Response`                | `LoginResponse`, `UserResponse`            |
| Exception         | `<Reason>Exception`               | `InvalidCredentialsException`              |
| Enum              | PascalCase, giá trị `UPPER_SNAKE` | `UserStatus.ACTIVE`, `Role.RENTER`         |


**Package:** `com.fptufa26.ecomotion.<module>.<layer>`

**Entity conventions (theo `User.java`):**

```java
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Getter @Setter @NoArgsConstructor          // Lombok, KHÔNG dùng @Data trên entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")               // snake_case ở DB, camelCase ở Java
    private Long userId;

    @Enumerated(EnumType.STRING)            // LUÔN dùng STRING, không ORDINAL
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;              // Dùng Instant cho timestamp

    @PrePersist  void onCreate() { ... }    // normalize + set createdAt
    @PreUpdate   void onUpdate() { ... }    // set updatedAt
}
```

**Quy tắc entity bắt buộc:**

- **`@Enumerated(EnumType.STRING)`** — không bao giờ `ORDINAL`.
- **`Instant`** cho mốc thời gian (không `Date`/`LocalDateTime`).
- **`snake_case`** cho `@Column(name=...)`; **camelCase** cho field Java.
- Normalize trong `@PrePersist` / `@PreUpdate` (ví dụ email `trim().toLowerCase()`).
- **Không** dùng `@Data` (rủi ro `equals`/`hashCode` trên entity có quan hệ).
- Đặt tên unique constraint: `uk_<bảng>_<cột>`.

**API response wrapper:**

```java
public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>("SUCCESS", "OK", data); }
    public static <T> ApiResponse<T> error(String code, String message) { return new ApiResponse<>(code, message, null); }
}
```

`@JsonInclude(NON_NULL)` — field null bị bỏ khỏi JSON.

**REST API:**

- Endpoint hướng tài nguyên: `GET /api/examples`, `POST /api/examples`, `PUT /api/examples/{id}`.
- **Tránh** action-style: ❌ `POST /api/createExample`.
- Ngoại lệ: hành động nghiệp vụ đặc thù thì action endpoint hợp lệ (ví dụ `/api/auth/login`).
- Base path hiện có: `/api/auth` — gồm `login`, `logout`, `refresh`, `me`.

**Security:**

- **JWT HS256**, secret **≥ 32 bytes** (nếu ngắn hơn → `WeakKeyException`).
- Access token TTL **15 phút**; refresh token lưu **httpOnly cookie**, `path=/api/auth`.
- Refresh token TTL: mặc định **1d**, "remember me" **7d**.
- `JwtAuthenticationFilter` + `SecurityUser` + `CustomUserDetailsService`.
- Handler lỗi REST riêng: `RestAuthenticationEntryPoint` (401), `RestAccessDeniedHandler` (403).

**Config & secrets:**

- `application.properties` **có credentials → KHÔNG commit** (đã gitignore).
- `application.properties.example` là template an toàn để commit.
- Config có thể override bằng environment variable: `${DB_USERNAME:postgres}`, `${JWT_SECRET:...}`.

### 4.2. Frontend (ReactJS) — quy ước đề xuất

> ⚠️ Frontend **chưa khởi tạo**. Dưới đây là quy ước **đề xuất**, sẽ chốt khi khởi tạo.

| Loại        | Quy ước đề xuất                                                               |
| ----------- | ----------------------------------------------------------------------------- |
| Component   | `PascalCase.jsx` — `VehicleCard.jsx`, `BookingDetail.jsx`                     |
| Hook        | `useXxx.js` — `useAuth.js`, `useVehicles.js`                                  |
| Service/API | `xxxService.js` — `authService.js`, `vehicleService.js`                       |
| Utils       | `camelCase.js` — `formatCurrency.js`                                          |
| Hằng số     | `UPPER_SNAKE_CASE`                                                            |
| Tổ chức     | `src/{components,pages,hooks,services,utils,contexts,assets}/`                |
| Gọi API     | Tập trung một `apiClient` (axios/fetch) — token tự động gắn header            |
| State       | Context API cho auth; state cục bộ cho phần còn lại (chưa chốt Redux/Zustand) |

**Bắt buộc đồng bộ với backend:**

- Base URL: `http://localhost:8080/api`.
- Auth: gửi JWT ở header `Authorization: Bearer <token>`; refresh token **do browser tự gửi cookie** → phải set `withCredentials: true`.
- Endpoint refresh: gọi để lấy access token mới khi gặp 401.

### 4.3. Tài liệu & sơ đồ

- **Ngôn ngữ:** tiếng Việt cho tài liệu nghiệp vụ (`docs/SBA301_FALL26.md`, `docs/GIAI_THICH_ENTITY_*.md`), tiếng Anh cho code/comment kỹ thuật.
- **Sơ đồ:** dùng Mermaid `erDiagram`, lưu `.mmd` + xuất `.svg` cùng tên version, đặt trong `docs/`.
- **Đặt tên version:** `Conceptual_v<major>.<minor>.mmd` — ghi rõ diff ở header file.
- **Luôn validate sơ đồ** trước khi export (dùng skill `mermaid-diagram-qc`).

---

## 5. QUYẾT ĐỊNH KỸ THUẬT QUAN TRỌNG

### 5.1. Quyết định nghiệp vụ (đã chốt với người dùng)

| #   | Quyết định                                                                                        | Lý do                                                                                                               |
| --- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| D1  | **Một tài khoản — một vai trò**                                                                   | Đơn giản hoá phân quyền; tránh nhập nhằng "ai đang làm gì" trong một request                                        |
| D2  | **Bỏ B2B ở phía người thuê, GIỮ ở phía nhà cung cấp**                                             | Khách cá nhân là đủ cho đồ án; nhưng nhà cung cấp doanh nghiệp là mô hình thực tế phổ biến                          |
| D3  | **Chốt snapshot giá LÚC KHÁCH GỬI YÊU CẦU**, không phải lúc accept                                | Giữa 2 mốc đó giá có thể đổi; khách phải được giữ **đúng con số đã nhìn thấy**                                      |
| D4  | **`BOOKING_POLICY_SNAPSHOT` là bảng riêng, 1-1 với `BOOKING`**                                    | `BOOKING` đã 21 dòng; nhúng 14 cột nữa làm bảng quá cao và **đè nhãn quan hệ** trong layout Mermaid                 |
| D5  | **Chính sách huỷ: chỉ HOÀN CỌC, KHÔNG bồi thường**                                                | Truy trách nhiệm chuỗi (khách trễ → hỏng chuyến sau → ai trả ai) **quá phức tạp so với giá trị** → cắt khỏi phạm vi |
| D6  | **Nhà xe tạo tài khoản driver hộ, admin duyệt mới active**                                        | Tài xế không tự đăng ký; nhà xe là bên xác thực                                                                     |
| D7  | **Sửa hồ sơ driver đã duyệt → tụt về `PENDING`**                                                  | Nếu không, nhà xe thay ảnh/GPLX sau khi duyệt mà admin không xét lại                                                |
| D8  | **Driver bị vô hiệu hoá phải tạo tài khoản MỚI**                                                  | Giữ nguyên vết lịch sử của tài khoản cũ; không cho chuyển sang nhà xe khác                                          |
| D9  | **Deny đơn KHÔNG kèm lý do**                                                                      | Chỉ lý do **huỷ sau khi đã accept** mới được ghi nhận                                                               |
| D10 | **Giá tách 2 tầng: `BOOKING_CHARGE` (khoản phải trả) + `PAYMENT_TRANSACTION` (tiền thực chuyển)** | Một khoản phí có thể trả nhiều lần; khoản **chưa thanh toán** vẫn phải tồn tại để khiếu nại/đối soát                |
| D11 | **Bỏ cột `status` ở `BOOKING_CHARGE`**                                                            | `SETTLED`/`DISPUTED` **suy diễn được** từ `PAYMENT_TRANSACTION`/`DISPUTE`                                           |
| D12 | **Bỏ `base_amount` ở `BOOKING_CHARGE`**                                                           | `= quantity × unit_price` → suy ra, lưu là dư                                                                       |
| D13 | **Giữ comment hoá đơn tự chứa: `quantity × unit_price × applied_rate`**                           | Khoản phí phải **tự giải thích được cách tính** khi khách khiếu nại                                                 |
| D14 | **Xe sửa 3 tầng; driver sửa gì cũng duyệt lại**                                                   | Xe là **tài sản có phân tầng trường**; tài xế là **định danh con người** → không đối xứng là **cố ý**               |
| D15 | **`chassis_no`/`engine_no` bất biến**                                                             | Định danh in trên cà-vẹt, gắn với thân xe/động cơ vật lý → sai thì tạo xe mới                                       |
| D16 | **`license_plate` KHÔNG bất biến**                                                                | Là **định danh pháp lý** → sửa được nhưng phải duyệt lại                                                            |
| D17 | **`cancelled_at` bắt buộc; không dùng `updated_at` thay thế**                                     | Cần mốc thời gian để tính % hoàn cọc; `updated_at` nhảy mỗi lần sửa bất kỳ field                                    |
| D18 | **Không có `CANCELLATION_POLICY` trong sơ đồ**                                                    | Tỷ lệ % hoàn cọc là **quy tắc nghiệp vụ (admin config)**, không phải bảng dữ liệu                                   |
| D19 | **Không refactor `REVIEW.vehicle_id`/`driver_id` thành 1 cột `target_type`**                      | `BOOKING.vehicle_id` **MUTABLE** → bỏ FK sẽ **gán nhầm review xe** khi booking đổi xe                               |
| D20 | **Giữ `PAYMENT_TRANSACTION.charge_id` dù đã có `booking_id`**                                     | `charge_id` trả lời *"đã tất toán khoản nào"* → biết khoản nào còn nợ. `booking_id` chỉ để query nhanh              |
| D21 | **Gói năm: `upfront_amount` THAY cọc; giá cố định suốt kỳ hạn; không phụ phí**                    | Gói năm là **lộ trình sở hữu xe**, không phải dịch vụ di chuyển                                                     |
| D22 | **Vượt giờ làm tròn lên slot, KHÔNG tạo booking mới**                                             | Cùng cơ chế với gia hạn: mua thêm slot trên **chính đơn cũ**                                                        |
| D23 | **`booking_code` tồn tại để không lộ `id` tuần tự**                                               | Bảo mật — dùng ở mọi API public                                                                                     |


### 5.2. Quyết định kỹ thuật (code)

| #   | Quyết định                                                            | Lý do                                                                                         |
| --- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| T1  | **Modular Monolith + N-Layer**                                        | Đồ án vừa phải — microservice là over-engineering; nhưng cần module rõ ràng để chia việc nhóm |
| T2  | **JWT stateless + refresh token cookie**                              | Access token ngắn (15m) giảm rủi ro lộ; refresh token httpOnly chống XSS đọc token            |
| T3  | **Refresh token lưu DB (`RefreshToken` entity)**                      | Cho phép thu hồi (revoke) — JWT thuần không thu hồi được                                      |
| T4  | **`@Enumerated(EnumType.STRING)`**                                    | `ORDINAL` sẽ vỡ dữ liệu khi thêm/đổi thứ tự enum                                              |
| T5  | **`Instant` cho timestamp**                                           | Chuẩn UTC, không phụ thuộc timezone hệ thống                                                  |
| T6  | **H2 cho test, PostgreSQL cho runtime**                               | Test độc lập, không cần PostgreSQL local                                                      |
| T7  | **`ApiResponse<T>` wrapper thống nhất**                               | FE xử lý response đồng nhất; `code` cho FE phân nhánh lỗi                                     |
| T8  | **`ddl-auto=create-drop` ở dev, `update` ở example**                  | Dev reset schema mỗi lần chạy; production chuyển sang migration tool                          |
| T9  | **Hibernate `PostgreSQLDialect` khai báo tường minh**                 | Tránh auto-detect sai                                                                         |
| T10 | **Lombok cho boilerplate** (chỉ `@Getter/@Setter/@NoArgsConstructor`) | Giảm code lặp; **không** dùng `@Data` trên entity                                             |
| T11 | **SpringDoc OpenAPI**                                                 | Tài liệu API tự sinh cho FE tích hợp                                                          |
| T12 | **CORS mở cho cả 3000 và 5173**                                       | Chừa cả CRA và Vite, chốt FE sau                                                              |
| T13 | **Chrome headless + CDP để QC sơ đồ Mermaid**                         | jsdom **không chạy được** `mermaid.render()` — cần browser thật                               |
| T14 | **Đo layout bằng `getBoundingClientRect()` + scale viewBox**          | `getBBox()` trả giá trị sai trong layout Mermaid                                              |
| T15 | **Đưa tài liệu dự án vào `docs/` trong repo**                         | `git clone` về là có đủ ngữ cảnh; tài liệu có lịch sử thay đổi                                |

---

## 6. DEPENDENCIES & SETUP MÔI TRƯỜNG

### 6.1. Yêu cầu hệ thống

| Thành phần     | Phiên bản           | Ghi chú                                      |
| -------------- | ------------------- | -------------------------------------------- |
| **JDK**        | **21**              | `pom.xml`: `<java.version>21</java.version>` |
| **Maven**      | dùng `mvnw` wrapper | Không cần cài Maven                          |
| **PostgreSQL** | 14+                 | DB tên `ecomotion`, port `5432`              |
| **Node.js**    | 20+ (khi làm FE)    | Chưa chốt phiên bản                          |
| **Git**        |                     |                                              |

### 6.2. Dependencies backend (từ `pom.xml`)

**Runtime:**

- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-webmvc`
- `springdoc-openapi-starter-webmvc-ui` **3.1.0**
- `jjwt-api` / `jjwt-impl` / `jjwt-jackson` **0.12.6**
- `postgresql` (runtime)
- `spring-boot-devtools` (runtime, optional)
- `lombok` (optional)

**Test:**

- `spring-boot-starter-{data-jpa,security,validation,webmvc}-test`
- `h2` (in-memory)

### 6.3. Hướng dẫn setup

**Bước 1 — Tạo database**

```sql
CREATE DATABASE ecomotion;
```

**Bước 2 — Cấu hình**

Copy template và điền giá trị thật:

```bash
cd Backend/EcoMotion/src/main/resources
cp application.properties.example application.properties
```

Sửa `application.properties`: điền `spring.datasource.username`, `spring.datasource.password`, và `app.jwt.secret`.

> ⚠️ **`application.properties` KHÔNG được commit** — đã có trong `.gitignore` (`/src/main/resources/application.properties`).  
> 💡 Có thể override bằng env: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE`.

**Bước 3 — Chạy backend**

```bash
cd Backend/EcoMotion
./mvnw spring-boot:run           # Linux/macOS/Git Bash
mvnw.cmd spring-boot:run         # Windows CMD
```

Backend chạy tại **<http://localhost:8080>**

**Bước 4 — Kiểm tra**

| URL                                     | Mục đích         |
| --------------------------------------- | ---------------- |
| <http://localhost:8080/swagger-ui.html> | Swagger UI       |
| <http://localhost:8080/v3/api-docs>     | OpenAPI JSON     |
| <http://localhost:8080/api/auth/login>  | Đăng nhập (POST) |

**Bước 5 — Chạy test**

```bash
./mvnw test
```

Test dùng **H2 in-memory** + `src/test/resources/application.properties` → không cần PostgreSQL.

**Bước 6 — Frontend (khi khởi tạo)**

```bash
cd Frontend
# CRA:  npx create-react-app . --template typescript
# Vite: npm create vite@latest . -- --template react
npm install
npm start        # port 3000 (CRA) hoặc npm run dev → 5173 (Vite)
```

### 6.4. Biến môi trường

| Biến                     | Mặc định            | Ý nghĩa             |
| ------------------------ | ------------------- | ------------------- |
| `SPRING_PROFILES_ACTIVE` | `dev`               | Spring profile      |
| `DB_USERNAME`            | `postgres`          | User PostgreSQL     |
| `DB_PASSWORD`            | *(xem file local)*  | Password PostgreSQL |
| `JWT_SECRET`             | *(dev placeholder)* | **Phải ≥ 32 bytes** |

### 6.5. Endpoint hiện có

| Method | Path                | Mô tả                                             |
| ------ | ------------------- | ------------------------------------------------- |
| `POST` | `/api/auth/login`   | Đăng nhập → trả access token + set refresh cookie |
| `POST` | `/api/auth/logout`  | Đăng xuất (thu hồi refresh token)                 |
| `POST` | `/api/auth/refresh` | Làm mới access token từ refresh cookie            |
| `GET`  | `/api/auth/me`      | Thông tin user hiện tại                           |

---

## 7. BẪY & BÀI HỌC ĐÃ GẶP

Phần này ghi lại các lỗi **đã thực sự gặp** để không lặp lại.

### 7.1. Mermaid erDiagram

| Bẫy                                 | Triệu chứng                                           | Cách xử lý                                                                                   |
| ----------------------------------- | ----------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Quan hệ thiếu label**             | `Expecting 'COLON', 'STYLE_SEPARATOR', got 'NEWLINE'` | **MỌI** quan hệ erDiagram phải có nhãn: `A ‖—o{ B : "..."` — khác `flowchart`                |
| **Thứ tự khai báo ≠ thứ tự layout** | Khai báo lại entity không đổi vị trí                  | Layout **Sugiyama**: node rank do **đồ thị cạnh** quyết định, không do thứ tự khai báo       |
| **Nhãn đè lên bảng**                | `labelOverlaps > 0`                                   | Cách hiệu quả nhất: **giảm số dòng của bảng bị đè**. Sắp xếp lại khai báo **không** giúp     |
| **`{"notReady":true}`**             | Tưởng render bị treo                                  | #1 nguyên nhân là **HTTP server đã chết**, không phải render hang. Kiểm tra bằng cách mở PNG |
| **Subshell backgrounding bị kill**  | Server tự tắt khi lệnh Bash kết thúc                  | `(cmd &)` bị kill → dùng cơ chế **background của tool**, và verify trước mỗi lần đo          |
| **404 favicon**                     | `Failed to load resource`                             | **Vô hại** — bỏ qua                                                                          |

### 7.2. Entity / JPA

| Bẫy                                   | Cách xử lý                                                                                                        |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `@Enumerated` mặc định là `ORDINAL`   | **Luôn** ghi rõ `EnumType.STRING`                                                                                 |
| `@Data` trên entity                   | Tránh — `equals`/`hashCode` sinh ra gây lỗi với quan hệ lazy                                                      |
| Unique constraint với cột nullable    | `NULL ≠ NULL` trong SQL → ràng buộc **vô hiệu**. Dùng **partial unique index**: `UNIQUE(a,b) WHERE b IS NOT NULL` |
| `updated_at` dùng thay `cancelled_at` | Sai — `updated_at` nhảy mỗi lần sửa bất kỳ field                                                                  |
| JWT secret ngắn                       | `WeakKeyException` — secret **≥ 32 bytes**                                                                        |

### 7.3. Nghiệp vụ (dễ code sai)

| Bẫy                                                    | Cách xử lý                                                                 |
| ------------------------------------------------------ | -------------------------------------------------------------------------- |
| Đọc giá từ `RENTAL_PLAN` khi tính tiền đơn cũ          | **SAI** — mọi tính tiền đọc từ `BOOKING_POLICY_SNAPSHOT`                   |
| Tính % hoàn cọc theo `PROVIDER.deposit_ratio` hiện tại | **SAI** — dùng `snap.deposit_ratio`                                        |
| Nghĩ `license_plate` bất biến                          | Không — nó là **định danh pháp lý**, sửa được nhưng duyệt lại              |
| Thêm cột `status` cho `BOOKING_CHARGE`                 | Thừa — suy diễn từ `PAYMENT_TRANSACTION` / `DISPUTE`                       |
| Lưu `base_amount`                                      | Thừa — `= quantity × unit_price`                                           |
| Quên cập nhật `rating_avg` ở cả 3 bảng                 | Phải cập nhật `VEHICLE` + `DRIVER` + `PROVIDER`; nguồn sự thật là `REVIEW` |
| Nghĩ `RENTER.license_*` bắt buộc                       | Nullable **theo thiết kế** — chỉ gói năm mới cần GPLX                      |

### 7.4. Git / tài liệu

| Bẫy                                                | Cách xử lý                                                                                                                                                        |
| -------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **File `.md` tạo ngoài git repo rồi mới move vào** | File tạo ở thư mục cha (ngoài git) có thể **mất** khi có tiến trình khác tác động lên repo root. **Luôn tạo file trực tiếp trong repo**, không tạo ngoài rồi move |
| Tài liệu quan trọng nằm ngoài git                  | `git clone` về không có spec → **đặt tài liệu trong `docs/`** ngay từ đầu                                                                                         |
| Commit secret                                      | Kiểm tra `git log --all -- <path>` để biết file có **từng** bị commit không — xoá file ở commit mới **không xoá được** khỏi lịch sử                               |
| **Commit `node_modules/` / `target/`**             | Rất khó gỡ (hàng chục nghìn file). Phải có `.gitignore` **ở root** trước khi tạo `Frontend/`                                                                       |
| **Ép commit file đã bị ignore**                    | `git add -f <file>` — chỉ dùng khi thật sự cần, và phải hiểu mình đang bỏ qua lớp bảo vệ nào                                                                      |
| Sửa `.gitignore` cho file **đã lỡ track**          | Thêm vào `.gitignore` **không** tự bỏ track. Phải `git rm --cached <file>` rồi commit                                                                             |
