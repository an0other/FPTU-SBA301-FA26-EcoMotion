import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './authContext'
import { authService } from '../services/authService'
import { clearAccessToken, getAccessToken, setAccessToken } from '../utils/tokenStore'

/**
 * AuthProvider — holds the session state for the whole app.
 *
 * - `user`            current user ({userId,email,role,status}) or null
 * - `accessToken`     current JWT (mirrored in tokenStore)
 * - `loading`         true while the initial session restore is running
 * - `isAuthenticated` derived from `user`
 * - `login()`         authenticates and persists the session
 * - `logout()`        revokes the refresh token and clears local state
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [accessToken, setTokenState] = useState(() => getAccessToken())
  const [loading, setLoading] = useState(true)

  // On mount: if a token is stored, validate it via /auth/me and restore the user.
  useEffect(() => {
    let active = true

    async function restoreSession() {
      const token = getAccessToken()
      if (!token) {
        if (active) setLoading(false)
        return
      }
      try {
        const me = await authService.fetchMe()
        if (!active) return
        setUser(me)
        setTokenState(token)
      } catch {
        if (!active) return
        clearAccessToken()
        setUser(null)
        setTokenState(null)
      } finally {
        if (active) setLoading(false)
      }
    }

    restoreSession()
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (email, password, rememberMe) => {
    const data = await authService.login(email, password, rememberMe)
    setAccessToken(data.accessToken)
    setTokenState(data.accessToken)
    setUser(data.user)
    return data.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await authService.logout()
    } catch {
      /* clear locally regardless of the API result */
    }
    clearAccessToken()
    setUser(null)
    setTokenState(null)
  }, [])

  const value = useMemo(
    () => ({
      user,
      accessToken,
      loading,
      isAuthenticated: Boolean(user),
      login,
      logout,
    }),
    [user, accessToken, loading, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
