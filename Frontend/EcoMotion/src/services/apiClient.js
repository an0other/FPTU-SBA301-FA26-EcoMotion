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
