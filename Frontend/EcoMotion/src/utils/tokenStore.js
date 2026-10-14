
const STORAGE_KEY = 'ecomotion_access_token'

let inMemoryToken = null
const listeners = new Set()

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
  listeners.forEach((listener) => listener(inMemoryToken))
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function subscribeAccessToken(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
