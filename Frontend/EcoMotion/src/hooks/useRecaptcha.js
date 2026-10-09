import { useCallback, useEffect, useState } from 'react'

const SITE_KEY = import.meta.env.VITE_RECAPTCHA_SITE_KEY || ''
const SCRIPT_ID = 'recaptcha-v3-script'

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
