import { expect, test } from '@playwright/test'

test('renter charge complaint sends a charge ID without an invalid charge type', async ({ page }) => {
  let payload
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'dispute-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    if (path.endsWith('/disputes') && route.request().method() === 'POST') {
      payload = route.request().postDataJSON()
      return route.fulfill({ json: { data: { disputeId: 9 } } })
    }
    if (path.endsWith('/workflow')) return route.fulfill({ json: { data: { bookingCode: 'DISP-TEST',
      bookingStatus: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', refunds: [], disputes: [] } } })
    if (path.endsWith('/DISP-TEST')) return route.fulfill({ json: { data: { bookingCode: 'DISP-TEST',
      status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', vehicleName: 'Xe kiểm thử', planType: 'DAILY', totalAmount: 1000000,
      disputeCategories: ['CHARGE'], timeline: [], charges: [{ chargeId: 123456, chargeType: 'PARKING', amount: 20000, isWaived: false }] } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/renter/bookings/DISP-TEST')
  await page.locator('select').filter({ has: page.getByRole('option', { name: '-- Chọn loại khiếu nại --' }) }).selectOption('CHARGE')
  await page.locator('select').filter({ has: page.getByRole('option', { name: '-- Chọn khoản --' }) }).selectOption('123456')
  await page.locator('textarea').last().fill('Tôi không đồng ý khoản phí gửi xe này')
  await page.getByRole('button', { name: 'Gửi khiếu nại', exact: true }).click()
  await expect.poll(() => payload).toBeTruthy()
  expect(payload.chargeId).toBe(123456)
  expect(payload).not.toHaveProperty('chargeType')
})


async function adminPage(page, disputes, onPatch) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'admin-dispute-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'ADMIN', fullName: 'Quản trị viên' } })
    if (path.endsWith('/resolve')) return onPatch(route)
    if (path.endsWith('/disputes')) return route.fulfill({ json: { data: disputes } })
    return route.fulfill({ json: { data: path.endsWith('/bookings') ? [] : {} } })
  })
  await page.goto('/admin')
  await page.getByRole('button', { name: 'Vận hành & Hoàn tiền', exact: true }).click()
  await page.getByRole('button', { name: 'Khiếu nại & Dispute' }).click()
}

test('admin uses IN_REVIEW and charge ruling cannot contradict the selected status', async ({ page }) => {
  const payloads = []
  const row = { id: 11, bookingCode: 'DISP-TEST', category: 'CHARGE', status: 'OPEN', description: 'Parking complaint', chargeId: 123456, chargeAmount: 20000 }
  await adminPage(page, [row], async route => {
    payloads.push(route.request().postDataJSON())
    await route.fulfill({ json: { data: { ...row, ...payloads.at(-1) } } })
  })
  await expect(page.getByRole('button', { name: 'Đang xem xét', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Phân xử & Chi tiết' }).click()
  const dialog = page.getByRole('dialog', { name: 'Phân xử khiếu nại #11' })
  await dialog.locator('select').selectOption('REJECTED')
  await expect(dialog.locator('input[value="WAIVE_CHARGE"]')).toBeDisabled()
  await expect(dialog.locator('input[value="KEEP_CHARGE"]')).toBeChecked()
  await dialog.locator('select').selectOption('IN_REVIEW')
  await expect(dialog.getByRole('radio')).toHaveCount(0)
  await dialog.locator('textarea').fill('Đang đối chiếu bằng chứng')
  await dialog.getByRole('button', { name: 'Lưu kết quả phân xử' }).click()
  await expect.poll(() => payloads.length).toBe(1)
  expect(payloads[0]).toEqual({ status: 'IN_REVIEW', resolution: 'Đang đối chiếu bằng chứng' })
})

test('deposit award sends total amount, retains form after errors, and resets when another complaint opens', async ({ page }) => {
  const rows = [11, 12].map(id => ({ id, bookingCode: `DISP-${id}`, category: 'DEPOSIT_REFUND', status: 'OPEN', description: 'Refund complaint' }))
  let submitted
  await adminPage(page, rows, async route => {
    submitted = route.request().postDataJSON()
    await route.fulfill({ status: 409, json: { message: 'Cần đối soát lịch sử hoàn tiền' } })
  })
  await page.getByRole('button', { name: 'Phân xử & Chi tiết' }).first().click()
  let dialog = page.getByRole('dialog', { name: 'Phân xử khiếu nại #11' })
  await dialog.getByRole('spinbutton').fill('540000')
  await dialog.locator('textarea').fill('Chấp thuận hoàn toàn bộ cọc')
  await dialog.getByRole('button', { name: 'Lưu kết quả phân xử' }).click()
  await expect(dialog.getByRole('alert')).toContainText('Cần đối soát')
  await expect(dialog.getByRole('spinbutton')).toHaveValue('540000')
  await expect(dialog.locator('textarea')).toHaveValue('Chấp thuận hoàn toàn bộ cọc')
  expect(submitted).toEqual({ status: 'RESOLVED', resolution: 'Chấp thuận hoàn toàn bộ cọc', refundAwardTotal: 540000 })
  await dialog.getByRole('button', { name: 'Đóng', exact: true }).click()
  await page.getByRole('button', { name: 'Phân xử & Chi tiết' }).nth(1).click()
  dialog = page.getByRole('dialog', { name: 'Phân xử khiếu nại #12' })
  await expect(dialog.getByRole('spinbutton')).toHaveValue('')
  await expect(dialog.locator('textarea')).toHaveValue('')
})

for (const role of ['RENTER', 'PROVIDER']) {
  test(`${role} sees ruling and pending refund as separate results`, async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'dispute-view-test'))
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/stream')) return route.abort()
      if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role, fullName: 'Người dùng kiểm thử' } })
      if (path.endsWith('/workflow')) return route.fulfill({ json: { data: { bookingCode: 'DISP-VIEW', bookingStatus: 'CANCELLATION_REVIEW',
        confirmedPaid: 540000, pendingRefundAmount: 540000, refunds: [], disputes: [{ id: 31, category: 'DEPOSIT_REFUND', status: 'RESOLVED',
          description: 'Yêu cầu xem xét cọc', handledByName: 'Admin xử lý', resolution: 'Chấp thuận hoàn toàn bộ cọc',
          resolvedAt: '2026-11-05T04:00:00Z', refundAwardTotal: 540000, refundRequestIds: [41, 42], financialStatus: 'AWAITING_REFUND' }] } } })
      if (path.endsWith('/DISP-VIEW')) return route.fulfill({ json: { data: { bookingCode: 'DISP-VIEW', status: 'CANCELLATION_REVIEW',
        vehicleName: 'Xe kiểm thử', planType: 'DAILY', totalAmount: 1000000, charges: [], timeline: [], disputeCategories: [] } } })
      return route.fulfill({ json: { data: [] } })
    })
    await page.goto(`/${role === 'RENTER' ? 'renter' : 'provider'}/bookings/DISP-VIEW`)
    const history = page.getByRole('region', { name: 'Lịch sử khiếu nại' })
    await expect(history.getByText('Đã phân xử, chờ hoàn tiền', { exact: true })).toBeVisible()
    await expect(history.getByText(/Admin xử lý/)).toBeVisible()
    await expect(history.getByText(/Chấp thuận hoàn toàn bộ cọc/)).toBeVisible()
    await expect(history.getByText(/#41, #42/)).toBeVisible()
  })
}


test('new dispute SSE event refreshes the ruling without waiting for polling', async ({ page }) => {
  let resolved = false
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'dispute-event-test')
    // SSE dùng fetch + Authorization header (không còn EventSource). Thay stream bằng controller để test đẩy event.
    const realFetch = window.fetch.bind(window)
    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : input.url
      if (!url.includes('/v1/notifications/stream')) return realFetch(input, init)
      const encoder = new TextEncoder()
      const body = new ReadableStream({
        start(controller) {
          window.disputeStream = { push: (text) => controller.enqueue(encoder.encode(text)) }
        },
      })
      return Promise.resolve(new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }))
    }
  })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Renter' } })
    if (path.endsWith('/workflow')) return route.fulfill({ json: { data: { bookingCode: 'DISP-SSE', bookingStatus: 'CANCELLED', refunds: [],
      disputes: [{ id: 1, category: 'DEPOSIT_REFUND', status: resolved ? 'RESOLVED' : 'OPEN', resolution: resolved ? 'Kết luận mới từ admin' : null }] } } })
    if (path.endsWith('/DISP-SSE')) return route.fulfill({ json: { data: { bookingCode: 'DISP-SSE', status: 'CANCELLED',
      planType: 'DAILY', totalAmount: 1000000, charges: [], timeline: [], disputeCategories: [] } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/renter/bookings/DISP-SSE')
  const history = page.getByRole('region', { name: 'Lịch sử khiếu nại' })
  await expect(history.getByText('Đã gửi, chờ thụ lý', { exact: true })).toBeVisible()
  resolved = true
  await page.evaluate(() => window.disputeStream.push('event: DISPUTE_UPDATED\ndata: {"bookingCode":"DISP-SSE"}\n\n'))
  await expect(history.getByText(/Kết luận mới từ admin/)).toBeVisible({ timeout: 3000 })
})

test('admin explicitly reopens the trip end window instead of implicitly finishing a trip', async ({ page }) => {
  let payload
  const row = { id: 21, bookingCode: 'END-TEST', category: 'TRIP_NOT_CONFIRMED', status: 'OPEN', description: 'Trip is still running' }
  await adminPage(page, [row], async route => {
    payload = route.request().postDataJSON()
    await route.fulfill({ json: { data: { ...row, ...payload } } })
  })
  await page.getByRole('button', { name: 'Phân xử & Chi tiết' }).click()
  const dialog = page.getByRole('dialog', { name: 'Phân xử khiếu nại #21' })
  await dialog.locator('select').nth(1).selectOption('REOPEN_CONFIRMATION')
  await dialog.locator('textarea').fill('Đã xác minh mốc tài xế báo kết thúc, mở lại 12 giờ')
  await dialog.getByRole('button', { name: 'Lưu kết quả phân xử' }).click()
  await expect.poll(() => payload?.tripEndAction).toBe('REOPEN_CONFIRMATION')
})
