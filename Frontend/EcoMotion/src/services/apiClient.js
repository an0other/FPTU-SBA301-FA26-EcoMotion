/**
 * TODO (chưa làm — ghi lại 2026-10-06): access token hết hạn thì KHÔNG tự động refresh.
 *
 * Hiện trạng: response interceptor bên dưới chỉ bọc lỗi thành tiếng Việt rồi reject. Nó KHÔNG
 * phân biệt 401-do-hết-hạn với 401-do-sai-mật-khẩu, và không hề gọi `POST /api/auth/refresh`.
 * `authService.refresh()` đã có sẵn nhưng không có call site nào trong toàn bộ `src/`.
 *
 * Hệ quả:
 *  - Access token TTL 15 phút, refresh token TTL 1d/7d. Sau 15 phút, F5 là
 *    `AuthProvider.restoreSession()` gọi `/auth/me` → 401 → `clearAccessToken()` + `setUser(null)`
 *    → bị đá thẳng ra trang login, dù cookie refresh token vẫn còn hợp lệ.
 *  - Đang dùng giữa chừng thì mọi request 401 và user thấy thông báo
 *    "Email hoặc mật khẩu không đúng." (xem `utils/errorMessage.js` map mọi 401) — sai ngữ cảnh.
 *  - Vì thế TTL 7 ngày của refresh token gần như vô dụng.
 *
 * Cách sửa dự kiến:
 *  1. Thêm interceptor xử lý 401: gọi `/api/auth/refresh` MỘT lần, lưu token mới, chạy lại
 *     request cũ.
 *  2. Giữ một promise refresh dùng chung (queue) để N request 401 đồng thời chỉ bắn 1 lần refresh.
 *  3. Loại trừ chính `/api/auth/refresh` khỏi retry, nếu không sẽ thành vòng lặp vô hạn.
 *  4. Sửa `AuthProvider.restoreSession()`: thử refresh trước, chỉ logout khi REFRESH cũng thất bại.
 *  5. Tách 401 trong `utils/errorMessage.js` để không còn báo sai về mật khẩu.
 *
 * Liên quan: xem thêm TODO trong `utils/tokenStore.js` (bỏ localStorage sau khi có bước 1-4).
 */

import axios from 'axios'
import { getAccessToken } from '../utils/tokenStore'
import { toVietnameseError } from '../utils/errorMessage'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // Let the browser set the multipart Content-Type (with its boundary) for FormData bodies.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const friendly = new Error(toVietnameseError(error))
    friendly.status = error.response?.status
    friendly.original = error

    return Promise.reject(friendly)
  },
)

export default apiClient
