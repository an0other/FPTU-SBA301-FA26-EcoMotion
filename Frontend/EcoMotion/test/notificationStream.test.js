import test from 'node:test'
import assert from 'node:assert/strict'
import { createNotificationStream } from '../src/services/notificationStream.js'

const encoder = new TextEncoder()
const URL_PATH = '/api/v1/notifications/stream'

// Giả lập fetch + body streaming. Mỗi phần tử của `responses` mô tả một lần gọi:
//   { reject: true } | { status, chunks, close: false, durationMs, noBody }
function createHarness(responses = [], options = {}) {
  const calls = []
  const timers = []
  const clock = { t: 0 }
  const tokenListeners = new Set()
  const queue = [...responses]
  const harness = {
    calls,
    timers,
    clock,
    refreshCount: 0,
    refreshBehavior: options.refreshBehavior ?? (async () => { throw new Error('no refresh') }),
    token: options.token === undefined ? 't1' : options.token,
    setToken(next) {
      harness.token = next
      for (const listener of [...tokenListeners]) listener(next)
    },
  }

  harness.stream = createNotificationStream({
    url: URL_PATH,
    getToken: () => harness.token,
    refreshToken: async () => {
      harness.refreshCount += 1
      return harness.refreshBehavior(harness)
    },
    subscribeToken: (listener) => {
      tokenListeners.add(listener)
      return () => tokenListeners.delete(listener)
    },
    fetchImpl: (url, init) => {
      calls.push({ url, init, token: init.headers.Authorization })
      const spec = queue.length > 0 ? queue.shift() : { status: 200, chunks: [], close: false }
      clock.t += spec.durationMs ?? 0
      if (spec.reject) return Promise.reject(new TypeError('Failed to fetch'))
      const body = spec.noBody
        ? null
        : new ReadableStream({
          start(controller) {
            for (const chunk of spec.chunks ?? []) controller.enqueue(encoder.encode(chunk))
            if (spec.close !== false) {
              controller.close()
            } else {
              init.signal.addEventListener('abort', () => {
                try { controller.error(new DOMException('Aborted', 'AbortError')) } catch { /* đã đóng */ }
              })
            }
          },
        })
      const status = spec.status ?? 200
      return Promise.resolve({ ok: status >= 200 && status < 300, status, body })
    },
    setTimer: (fn, ms) => {
      const handle = { fn, ms, cancelled: false }
      timers.push(handle)
      return handle
    },
    clearTimer: (handle) => {
      if (handle) handle.cancelled = true
    },
    now: () => clock.t,
    minBackoffMs: 1000,
    maxBackoffMs: 30000,
    minHealthyMs: 5000,
  })
  return harness
}

function pendingTimers(harness) {
  return harness.timers.filter((t) => !t.cancelled && !t.fired)
}

function runNextTimer(harness) {
  const next = pendingTimers(harness)[0]
  assert.ok(next, 'expected a scheduled reconnect timer')
  next.fired = true
  next.fn()
}

async function flush(times = 5) {
  for (let i = 0; i < times; i += 1) await new Promise((resolve) => setTimeout(resolve, 0))
}

async function waitFor(condition, timeoutMs = 2000) {
  const started = Date.now()
  while (!condition()) {
    if (Date.now() - started > timeoutMs) throw new Error('waitFor timed out')
    await new Promise((resolve) => setTimeout(resolve, 2))
  }
}

test('opens one connection with a Bearer header and no token in the URL', async () => {
  const h = createHarness([{ chunks: [] }])
  const received = []
  h.stream.subscribe((event) => received.push(event))
  await waitFor(() => h.calls.length === 1)

  assert.equal(h.calls[0].url, URL_PATH)
  assert.ok(!h.calls[0].url.includes('token'), 'URL must not carry the token')
  assert.equal(h.calls[0].token, 'Bearer t1')
  assert.equal(h.calls[0].init.headers.Accept, 'text/event-stream')
  assert.equal(h.calls[0].init.cache, 'no-store')
  h.stream.subscribe(() => {})
  await flush()
  assert.equal(h.calls.length, 1, 'a second subscriber must reuse the connection')
})

test('every subscriber receives open, named events and status changes; heartbeats are dropped', async () => {
  const h = createHarness([{ chunks: [': ping\n\nevent: BOOKING_CHANGED\ndata: {"a":1}\n\n'], close: false }])
  const first = []
  const second = []
  h.stream.subscribe((e) => first.push(e))
  h.stream.subscribe((e) => second.push(e))
  await waitFor(() => first.some((e) => e.type === 'BOOKING_CHANGED'))

  const types = (events) => events.filter((e) => e.type !== 'status').map((e) => e.type)
  assert.deepEqual(types(first), ['open', 'BOOKING_CHANGED'])
  assert.deepEqual(types(second), ['open', 'BOOKING_CHANGED'])
  assert.equal(h.stream.getStatus(), 'open')
  h.stream.subscribe(() => {})
})

test('network failure reconnects with exponential backoff capped at maxBackoffMs', async () => {
  const h = createHarness([{ reject: true }, { reject: true }, { reject: true }, { reject: true }, { reject: true }, { reject: true }])
  h.stream.subscribe(() => {})
  await flush()
  const delays = []
  for (let i = 0; i < 6; i += 1) {
    await flush()
    const next = pendingTimers(h)[0]
    assert.ok(next, `attempt ${i}`)
    delays.push(next.ms)
    runNextTimer(h)
    await flush()
  }
  assert.deepEqual(delays, [1000, 2000, 4000, 8000, 16000, 30000])
  h.stream.subscribe(() => {})
})

test('a 401 triggers exactly one refresh and retries with the new token', async () => {
  const h = createHarness([{ status: 401, noBody: true }, { chunks: [], close: false }], {
    refreshBehavior: async (harness) => harness.setToken('t2'),
  })
  h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 2 && h.stream.getStatus() === 'open')

  assert.equal(h.refreshCount, 1)
  assert.equal(h.calls[0].token, 'Bearer t1')
  assert.equal(h.calls[1].token, 'Bearer t2')
  h.stream.subscribe(() => {})
})

test('repeated 401 stops at auth-failed and never schedules reconnects', async () => {
  const h = createHarness([{ status: 401, noBody: true }, { status: 401, noBody: true }], {
    refreshBehavior: async () => {},
  })
  h.stream.subscribe(() => {})
  await waitFor(() => h.stream.getStatus() === 'auth-failed')
  await flush(10)

  assert.equal(h.refreshCount, 1, 'refresh only once per attempt')
  assert.equal(h.calls.length, 2)
  assert.equal(pendingTimers(h).length, 0, 'no reconnect while the token is invalid')
  h.stream.subscribe(() => {})
})

test('refresh failure stops at auth-failed without a retry loop', async () => {
  const h = createHarness([{ status: 403, noBody: true }])
  h.stream.subscribe(() => {})
  await waitFor(() => h.stream.getStatus() === 'auth-failed')
  await flush(10)

  assert.equal(h.calls.length, 1)
  assert.equal(pendingTimers(h).length, 0)
  h.stream.subscribe(() => {})
})

test('a new valid token after auth-failed reconnects immediately', async () => {
  const h = createHarness([{ status: 401, noBody: true }, { status: 401, noBody: true }, { chunks: [], close: false }], {
    refreshBehavior: async () => {},
  })
  h.stream.subscribe(() => {})
  await waitFor(() => h.stream.getStatus() === 'auth-failed')
  h.setToken('fresh-login')
  await waitFor(() => h.calls.length === 3)

  assert.equal(h.calls[2].token, 'Bearer fresh-login')
  h.stream.subscribe(() => {})
})

test('token rotation while open aborts the old connection and reconnects with the new token', async () => {
  const h = createHarness([{ chunks: [], close: false }, { chunks: [], close: false }])
  h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 1 && h.stream.getStatus() === 'open')
  const oldSignal = h.calls[0].init.signal

  h.setToken('t-rotated')
  await waitFor(() => h.calls.length === 2)

  assert.equal(oldSignal.aborted, true)
  assert.equal(h.calls[1].token, 'Bearer t-rotated')
  h.stream.subscribe(() => {})
})

test('logout (token cleared) aborts the stream and cancels pending reconnects', async () => {
  const h = createHarness([{ reject: true }, { chunks: [], close: false }])
  h.stream.subscribe(() => {})
  await flush()
  assert.equal(pendingTimers(h).length, 1, 'a reconnect is pending after the failure')

  h.setToken(null)
  await flush()
  assert.equal(h.stream.getStatus(), 'idle')
  assert.equal(pendingTimers(h).length, 0)
  assert.equal(h.calls.length, 1, 'no connection is opened after logout')
})

test('logout while open aborts the active request', async () => {
  const h = createHarness([{ chunks: [], close: false }])
  h.stream.subscribe(() => {})
  await waitFor(() => h.stream.getStatus() === 'open')
  const signal = h.calls[0].init.signal
  h.setToken(null)
  assert.equal(signal.aborted, true)
  assert.equal(h.stream.getStatus(), 'idle')
})

test('unsubscribing the last listener closes the connection; a new listener reconnects', async () => {
  const h = createHarness([{ chunks: [], close: false }, { chunks: [], close: false }])
  const unsubscribe = h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 1 && h.stream.getStatus() === 'open')
  const signal = h.calls[0].init.signal

  unsubscribe()
  assert.equal(signal.aborted, true)
  assert.equal(h.stream.getStatus(), 'idle')

  h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 2)
})

test('a stream that closes quickly is treated as a failure and backs off', async () => {
  const h = createHarness([{ chunks: [], durationMs: 0 }, { chunks: [], durationMs: 0 }])
  h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 1)
  await flush()
  const next = pendingTimers(h)[0]
  assert.ok(next, 'a reconnect is scheduled')
  assert.equal(next.ms, 1000)
  h.stream.subscribe(() => {})
})

test('a healthy stream that the server closes (2-minute timeout) reconnects without growing the backoff', async () => {
  const h = createHarness([{ chunks: ['event: X\ndata: 1\n\n'], durationMs: 120000 }, { chunks: [], close: false }])
  h.stream.subscribe(() => {})
  await waitFor(() => h.calls.length === 1)
  await flush()
  const next = pendingTimers(h)[0]
  assert.ok(next)
  assert.equal(next.ms, 1000)
  runNextTimer(h)
  await waitFor(() => h.calls.length === 2)
  h.stream.subscribe(() => {})
})

test('no duplicate connection while one is connecting', async () => {
  const h = createHarness([{ chunks: [], close: false }])
  h.stream.subscribe(() => {})
  h.stream.subscribe(() => {})
  h.stream.subscribe(() => {})
  await flush()
  assert.equal(h.calls.length, 1)
  h.stream.subscribe(() => {})
})

test('a throwing listener does not break other listeners', async () => {
  const h = createHarness([{ chunks: ['data: ok\n\n'], close: false }])
  const seen = []
  h.stream.subscribe(() => { throw new Error('boom') })
  h.stream.subscribe((e) => seen.push(e.type))
  await waitFor(() => seen.includes('message'))
  assert.ok(seen.includes('open'))
  h.stream.subscribe(() => {})
})

test('no token means no request at all', async () => {
  const h = createHarness([], { token: null })
  h.stream.subscribe(() => {})
  await flush()
  assert.equal(h.calls.length, 0)
  assert.equal(h.stream.getStatus(), 'idle')
})
