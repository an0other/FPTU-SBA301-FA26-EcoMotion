import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './authContext'
import { authService } from '../services/authService'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '../utils/tokenStore'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [accessToken, setTokenState] = useState(() => getAccessToken())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    const unsubscribe = subscribeAccessToken((token) => {
      if (!active) return
      setTokenState(token)
      if (!token) setUser(null)
    })

    async function restoreSession() {
      try {
        if (!getAccessToken()) {
          await authService.refresh()
        }
        const me = await authService.fetchMe()
        if (!active) return
        setUser(me)
        setTokenState(getAccessToken())
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
      unsubscribe()
    }
  }, [])

  const login = useCallback(async (email, password, rememberMe, captchaToken) => {
    const data = await authService.login(email, password, rememberMe, captchaToken)
    setAccessToken(data.accessToken)
    setTokenState(data.accessToken)
    let resolvedUser = data.user
    try {
      const me = await authService.fetchMe()
      if (me) resolvedUser = me
    } catch {
    }
    setUser(resolvedUser)
    return resolvedUser
  }, [])

  const logout = useCallback(async () => {
    try {
      await authService.logout()
    } catch {
      void 0
    }
    clearAccessToken()
    setUser(null)
    setTokenState(null)
  }, [])

  const register = useCallback((payload) => authService.register(payload), [])

  const sendRegisterOtp = useCallback((email) => authService.sendRegisterOtp(email), [])

  const verifyRegisterOtp = useCallback((email, otp) => authService.verifyRegisterOtp(email, otp), [])

  const activateStatus = useCallback((activationToken) => authService.activateStatus(activationToken), [])

  const activateSendPhoneOtp = useCallback(
    (activationToken, phone) => authService.activateSendPhoneOtp(activationToken, phone),
    [],
  )

  const activateVerifyPhoneOtp = useCallback(
    (activationToken, otp) => authService.activateVerifyPhoneOtp(activationToken, otp),
    [],
  )

  const activateScanCccd = useCallback(
    (activationToken, files) => authService.activateScanCccd(activationToken, files),
    [],
  )

  const activateFaceMatch = useCallback(
    (activationToken, portrait) => authService.activateFaceMatch(activationToken, portrait),
    [],
  )

  const activateResumeOtp = useCallback((email) => authService.activateResumeOtp(email), [])

  const activateResume = useCallback((email, otp) => authService.activateResume(email, otp), [])

  const value = useMemo(
    () => ({
      user,
      accessToken,
      loading,
      isAuthenticated: Boolean(user),
      login,
      logout,
      register,
      sendRegisterOtp,
      verifyRegisterOtp,
      activateStatus,
      activateSendPhoneOtp,
      activateVerifyPhoneOtp,
      activateScanCccd,
      activateFaceMatch,
      activateResumeOtp,
      activateResume,
    }),
    [
      user,
      accessToken,
      loading,
      login,
      logout,
      register,
      sendRegisterOtp,
      verifyRegisterOtp,
      activateStatus,
      activateSendPhoneOtp,
      activateVerifyPhoneOtp,
      activateScanCccd,
      activateFaceMatch,
      activateResumeOtp,
      activateResume,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
