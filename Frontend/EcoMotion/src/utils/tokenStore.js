/**
 * Access-token store.
 *
 * Kept in memory for fast, synchronous access on every request, and mirrored to
 * localStorage so the session survives a page reload (the refresh token itself
 * lives in an httpOnly cookie the JS can never read).
 *
 * This module is the single access point for the access token, so switching to a
 * stricter strategy later (e.g. memory-only + refresh on load) only touches here.
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
    /* storage unavailable (private mode) — memory copy still works */
  }
}

export function clearAccessToken() {
  setAccessToken(null)
}
