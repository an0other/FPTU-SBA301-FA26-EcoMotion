import axios from 'axios'

const ACCESS_TOKEN_KEY = 'ecomotion.accessToken'

export const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true,
})

export function setAccessToken(token) {
  if (token) {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  }
}

export function getAccessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY)
}

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function readErrorMessage(error) {
  return error?.response?.data?.message || 'Không kết nối được máy chủ'
}
