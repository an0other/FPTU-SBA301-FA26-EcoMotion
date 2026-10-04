import axios from 'axios'
import { getAccessToken } from '../utils/tokenStore'

/**
 * Centralized axios instance.
 *
 * `baseURL: '/api'` relies on the Vite dev proxy (vite.config.js) forwarding
 * /api/* to http://localhost:8080, so the app is same-origin in dev — no CORS
 * preflight and the httpOnly refresh cookie is first-party.
 *
 * `withCredentials` is still required: the refresh token lives in an httpOnly
 * cookie that must be attached to /api/auth/* requests.
 */
const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

// Attach the Bearer token on every request. Read fresh each time so login /
// logout changes are picked up immediately.
apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Normalize the backend ErrorResponse ({timestamp,status,error,message,path})
// into a plain Error carrying a human-friendly `message` and the `status`.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data

    let message = 'Đã xảy ra lỗi, vui lòng thử lại.'
    if (payload?.message) {
      message = payload.message
    } else if (error.code === 'ERR_NETWORK') {
      message = 'Không kết nối được máy chủ. Vui lòng kiểm tra backend.'
    }

    const friendly = new Error(message)
    friendly.status = status
    friendly.original = error

    // NOTE: future hook point — on 401 (excluding /auth/login, /auth/refresh)
    // attempt POST /api/auth/refresh once and retry the original request.
    return Promise.reject(friendly)
  },
)

export default apiClient
