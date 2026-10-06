import { useCallback, useEffect, useState } from 'react'

const SITE_KEY = import.meta.env.VITE_RECAPTCHA_SITE_KEY || ''
const SCRIPT_ID = 'recaptcha-v3-script'

/**
 * Feature flag for reCAPTCHA v3.
 *
 * The whole client flow degrades gracefully: as long as `VITE_RECAPTCHA_SITE_KEY` is empty the
 * hook reports `enabled === false`, `execute()` resolves to an empty string, and the auth payloads
 * simply omit `captchaToken`. That keeps local development working with the backend's
 * `app.captcha.enabled=false`, without any conditional sprinkled through the pages.
 */
export const isRecaptchaConfigured = () => SITE_KEY.length > 0

function loadScript() {
  if (typeof window === 'undefined') return Promise.resolve(false)
  if (window.grecaptcha?.execute) return Promise.resolve(true)
  if (window.__recaptchaLoading) return window.__recaptchaLoading

  window.__recaptchaLoading = new Promise((resolve) => {
    const existing = document.getElementById(SCRIPT_ID)
    if (existing) {
      existing.addEventListener('load', () => resolve(Boolean(window.grecaptcha?.execute)))
      existing.addEventListener('error', () => resolve(false))
      return
    }

    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = `https://www.google.com/recaptcha/api.js?render=${SITE_KEY}`
    script.async = true
    script.defer = true
    script.onload = () => {
      // grecaptcha may still be initialising when `onload` fires; wait for `.ready`.
      if (window.grecaptcha?.ready) {
        window.grecaptcha.ready(() => resolve(Boolean(window.grecaptcha?.execute)))
      } else {
        resolve(Boolean(window.grecaptcha?.execute))
      }
    }
    script.onerror = () => resolve(false)
    document.head.appendChild(script)
  })

  return window.__recaptchaLoading
}

/**
 * Loads the reCAPTCHA v3 script once per page and exposes an `execute(action)` helper that
 * returns a fresh token for the given action name ("login" or "register").
 */
export default function useRecaptcha() {
  const enabled = isRecaptchaConfigured()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!enabled) return undefined
    let active = true
    loadScript().then((ok) => {
      if (active) setReady(ok)
    })
    return () => {
      active = false
    }
  }, [enabled])

  const execute = useCallback(
    async (action) => {
      if (!enabled) return ''
      const ok = ready || (await loadScript())
      if (!ok || !window.grecaptcha?.execute) {
        throw new Error('Không tải được reCAPTCHA. Vui lòng tắt trình chặn quảng cáo và thử lại.')
      }
      return window.grecaptcha.execute(SITE_KEY, { action })
    },
    [enabled, ready],
  )

  return { ready, enabled, execute }
}
