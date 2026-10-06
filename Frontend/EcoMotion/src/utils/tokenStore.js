/**
 * TODO (chưa làm — ghi lại 2026-10-06): bỏ localStorage, chỉ giữ access token trong memory.
 *
 * Lý do: localStorage đọc được bằng JavaScript, nên bất kỳ XSS nào cũng lấy được access token
 * (`localStorage.getItem('ecomotion_access_token')`). Đây là điểm yếu bảo mật lớn nhất trong
 * luồng token hiện tại — xem thêm ghi chú trong `services/apiClient.js`.
 *
 * Điều kiện tiên quyết: PHẢI làm xong interceptor tự động refresh ở `services/apiClient.js`
 * trước. Nếu bỏ localStorage khi chưa có bước đó, F5 sẽ mất phiên hoàn toàn vì access token
 * không còn được khôi phục từ đâu cả.
 *
 * Sau khi có interceptor: bỏ hẳn `localStorage.setItem/getItem` ở đây, chỉ giữ biến
 * `inMemoryToken`, và khôi phục phiên lúc khởi động bằng `POST /api/auth/refresh` (cookie
 * httpOnly vẫn còn).
 */

const STORAGE_KEY = 'ecomotion_access_token'

let inMemoryToken = null

export function getAccessToken() {
  if (inMemoryToken) return inMemoryToken
  try {
    inMemoryToken = localStorage.getItem(STORAGE_KEY)
  } catch {
    inMemoryToken = null
  }
  return inMemoryToken
}

export function setAccessToken(token) {
  inMemoryToken = token ?? null
  try {
    if (token) {
      localStorage.setItem(STORAGE_KEY, token)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    void 0
  }
}

export function clearAccessToken() {
  setAccessToken(null)
}
