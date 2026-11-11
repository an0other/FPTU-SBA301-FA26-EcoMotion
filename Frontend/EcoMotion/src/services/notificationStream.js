import { createSseParser } from '../utils/sseParser.js'
import { getAccessToken, subscribeAccessToken } from '../utils/tokenStore.js'
import { refreshAccessToken } from './apiClient.js'

// Một kết nối SSE duy nhất cho cả tab, dùng chung bởi mọi hook realtime.
// JWT gửi qua header Authorization (fetch), không đưa vào URL.
//
// Trạng thái: idle | connecting | open | reconnecting | auth-failed
// Listener nhận: { type: 'open' } | { type: <tên event>, data: string, id } | { type: 'status', status }
//
// - Mất kết nối: reconnect với backoff nhân đôi, tối đa maxBackoffMs.
// - Token không hợp lệ: thử refresh đúng một lần; vẫn 401/403 thì dừng ở 'auth-failed',
//   không reconnect cho tới khi có token mới (đăng nhập lại hoặc refresh thành công).
// - Server đóng stream bình thường (timeout 2 phút): reconnect ngay.
// - Logout (token = null) hoặc không còn listener: abort kết nối, hủy timer.

export function createNotificationStream({
  url,
  getToken,
  refreshToken,
  subscribeToken,
  fetchImpl,
  setTimer = setTimeout,
  clearTimer = clearTimeout,
  now = () => Date.now(),
  minBackoffMs = 1000,
  maxBackoffMs = 30000,
  minHealthyMs = 5000,
}) {
  const listeners = new Set()
  let status = 'idle'
  let controller = null
  let connToken = null
  let attempt = 0
  let retryTimer = null
  let unsubscribeToken = null
  let lastEventId = null
  let serverRetryMs = null

  function setStatus(next) {
    if (status === next) return
    status = next
    emit({ type: 'status', status: next })
  }

  function emit(event) {
    for (const listener of [...listeners]) {
      try {
        listener(event)
      } catch {
        // Một listener lỗi không được làm hỏng các listener khác.
      }
    }
  }

  function clearRetry() {
    if (retryTimer !== null) {
      clearTimer(retryTimer)
      retryTimer = null
    }
  }

  function stopConnection(nextStatus) {
    clearRetry()
    if (controller) controller.abort()
    controller = null
    connToken = null
    attempt = 0
    setStatus(nextStatus)
  }

  function backoffDelay() {
    const base = serverRetryMs ?? minBackoffMs
    return Math.min(maxBackoffMs, base * 2 ** Math.max(0, attempt - 1))
  }

  function scheduleReconnect(delayMs) {
    clearRetry()
    setStatus('reconnecting')
    retryTimer = setTimer(() => {
      retryTimer = null
      if (listeners.size === 0) return
      connect()
    }, delayMs)
  }

  function connect() {
    if (controller) return // đã có một kết nối đang chạy: không tạo trùng
    const token = getToken()
    if (!token) {
      setStatus('idle')
      return
    }
    clearRetry()
    controller = new AbortController()
    connToken = token
    setStatus('connecting')
    run(controller, false)
  }

  async function run(ctrl, authRetried) {
    const startedAt = now()
    let parser = null
    try {
      const token = getToken()
      if (!token) {
        if (controller === ctrl) stopConnection('idle')
        return
      }
      const headers = { Accept: 'text/event-stream', Authorization: `Bearer ${token}` }
      const response = await fetchImpl(url, { headers, signal: ctrl.signal, cache: 'no-store' })

      if (response.status === 401 || response.status === 403) {
        if (ctrl.signal.aborted) return
        if (!authRetried) {
          try {
            await refreshToken()
          } catch {
            if (controller === ctrl) stopConnection('auth-failed')
            return
          }
          if (ctrl.signal.aborted || controller !== ctrl) return
          return run(ctrl, true)
        }
        if (controller === ctrl) {
          controller = null
          connToken = null
          attempt = 0
          setStatus('auth-failed')
        }
        return
      }
      if (!response.ok || !response.body) {
        throw new Error(`SSE HTTP ${response.status}`)
      }

      attempt = 0
      setStatus('open')
      emit({ type: 'open' })

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      parser = createSseParser((event) => {
        lastEventId = event.id ?? lastEventId
        emit(event)
      })
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        parser.push(decoder.decode(value, { stream: true }))
      }
      if (ctrl.signal.aborted || controller !== ctrl) return

      // Server đóng stream. Nếu đóng quá nhanh (proxy/server lỗi lặp lại) thì coi là lỗi để backoff.
      controller = null
      connToken = null
      if (now() - startedAt < minHealthyMs) {
        attempt += 1
        scheduleReconnect(backoffDelay())
      } else {
        scheduleReconnect(serverRetryMs ?? 1000)
      }
    } catch {
      if (ctrl.signal.aborted || controller !== ctrl) return
      controller = null
      connToken = null
      attempt += 1
      scheduleReconnect(backoffDelay())
    } finally {
      if (parser) serverRetryMs = parser.retryMs ?? serverRetryMs
    }
  }

  function handleTokenChange(token) {
    if (listeners.size === 0) return
    if (!token) {
      stopConnection('idle')
      return
    }
    if (status === 'auth-failed') {
      connect()
      return
    }
    // Đang refresh trong run() (status 'connecting') thì không ngắt kết nối.
    if (status === 'connecting') return
    if (token !== connToken) {
      stopConnection('idle')
      connect()
    }
  }

  function ensureTokenWatch() {
    if (unsubscribeToken) return
    unsubscribeToken = subscribeToken(handleTokenChange)
  }

  function dropTokenWatch() {
    if (!unsubscribeToken) return
    unsubscribeToken()
    unsubscribeToken = null
  }

  return {
    subscribe(listener) {
      listeners.add(listener)
      ensureTokenWatch()
      if (status === 'idle' || status === 'reconnecting') connect()
      return () => {
        listeners.delete(listener)
        if (listeners.size === 0) {
          stopConnection('idle')
          dropTokenWatch()
        }
      }
    },
    getStatus() {
      return status
    },
    getLastEventId() {
      return lastEventId
    },
  }
}

const baseUrl = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

const notificationStream = createNotificationStream({
  url: `${baseUrl}/notifications/stream`,
  getToken: getAccessToken,
  refreshToken: refreshAccessToken,
  subscribeToken: subscribeAccessToken,
  fetchImpl: (input, init) => fetch(input, { ...init, credentials: 'same-origin' }),
})

export function subscribeNotificationStream(listener) {
  return notificationStream.subscribe(listener)
}

export function getNotificationStreamStatus() {
  return notificationStream.getStatus()
}
