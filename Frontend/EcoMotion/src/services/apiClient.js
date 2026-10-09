
import axios from 'axios'
import { clearAccessToken, getAccessToken, setAccessToken } from '../utils/tokenStore'
import { toVietnameseError } from '../utils/errorMessage'

const clientConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
}

const apiClient = axios.create(clientConfig)
const refreshClient = axios.create(clientConfig)
let refreshPromise = null

export function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = refreshClient.post('/auth/refresh')
      .then(({ data }) => {
        setAccessToken(data.accessToken)
        return data
      })
      .catch((error) => {
        if ([401, 403].includes(error.response?.status)) {
          clearAccessToken()
        }
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config
    const path = request?.url?.split('?')[0]
    const isAuthAction = path === '/auth/login'
      || path === '/auth/logout'
      || path === '/auth/refresh'
      || path?.startsWith('/auth/register')
      || path?.startsWith('/auth/activate')

    if (error.response?.status === 401 && request && !request._retry
      && !isAuthAction && request.headers?.Authorization) {
      request._retry = true
      try {
        const currentToken = getAccessToken()
        if (!currentToken || request.headers.Authorization === `Bearer ${currentToken}`) {
          await refreshAccessToken()
        }
        return await apiClient(request)
      } catch (retryError) {
        if (retryError.original) return Promise.reject(retryError)
        error = retryError
      }
    }

    const friendly = new Error(toVietnameseError(error))
    friendly.status = error.response?.status
    friendly.original = error

    return Promise.reject(friendly)
  },
)

export default apiClient
