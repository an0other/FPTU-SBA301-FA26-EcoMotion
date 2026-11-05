import { expect, test } from '@playwright/test'

async function renter(page, state = 'WAITING', deadline = new Date(Date.now() + 12 * 3600000).toISOString()) {
  const mutations = []
  let current = state
  let method = state === 'CONFIRMED' ? 'AUTO' : null
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'trip-window-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    if (route.request().method() === 'POST') {
      mutations.push({ path, body: route.request().postDataJSON() })
      current = path.endsWith('/disputes') ? 'PAUSED' : 'CONFIRMED'; method = 'RENTER'
      return route.fulfill({ json: { data: { disputeId: 17 } } })
    }
    if (path.endsWith('/workflow')) return route.fulfill({ json: { data: {
      bookingCode: 'END-TEST', bookingStatus: current === 'CONFIRMED' ? 'AWAITING_EXTRA_CHARGE_CONFIRMATION' : 'AWAITING_END_CONFIRMATION',
      refunds: [], disputes: [], endConfirmations: [{ id: 7, reportedAt: new Date().toISOString(), deadline,
        state: current, confirmationMethod: method, confirmedAt: current === 'CONFIRMED' ? new Date().toISOString() : null }]
    } } })
    if (path.endsWith('/END-TEST')) return route.fulfill({ json: { data: { bookingCode: 'END-TEST',
      status: 'AWAITING_END_CONFIRMATION', vehicleName: 'Xe kiểm thử', planType: 'DAILY', totalAmount: 1000000,
      disputeCategories: [], timeline: [], charges: [] } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/renter/bookings/END-TEST')
  return mutations
}

test('renter sees a twelve-hour countdown and confirmation hides both actions', async ({ page }) => {
  const mutations = await renter(page)
  const panel = page.getByRole('region', { name: 'Xác nhận kết thúc chuyến' })
  await expect(panel.getByRole('timer')).toHaveText(/(?:11:59|12:00):\d\d/)
  await panel.getByRole('button', { name: 'Xác nhận kết thúc', exact: true }).click()
  await expect(panel).toContainText('Người thuê đã xác nhận kết thúc')
  await expect(panel.getByRole('button')).toHaveCount(0)
  expect(mutations[0].path).toContain('/confirm-end')
})

test('objection requires a reason and pauses automatic confirmation', async ({ page }) => {
  const mutations = await renter(page)
  const panel = page.getByRole('region', { name: 'Xác nhận kết thúc chuyến' })
  await panel.getByRole('button', { name: 'Chuyến chưa kết thúc / Có vấn đề' }).click()
  await panel.getByRole('textbox').fill('Xe vẫn đang di chuyển, chuyến chưa kết thúc')
  await panel.getByRole('button', { name: 'Gửi phản đối và dừng tự động' }).click()
  await expect(panel).toContainText('Đã dừng tự động xác nhận')
  await expect(panel.getByRole('timer')).toHaveCount(0)
  expect(mutations[0].body.category).toBe('TRIP_NOT_CONFIRMED')
})

test('automatic completion is labeled as system action and leaves no confirmation buttons', async ({ page }) => {
  await renter(page, 'CONFIRMED')
  const panel = page.getByRole('region', { name: 'Xác nhận kết thúc chuyến' })
  await expect(panel).toContainText('Hệ thống tự xác nhận do hết thời hạn')
  await expect(panel.getByRole('button')).toHaveCount(0)
})

test('elapsed deadline hides actions while awaiting the server result', async ({ page }) => {
  await renter(page, 'WAITING', new Date(Date.now() - 1000).toISOString())
  const panel = page.getByRole('region', { name: 'Xác nhận kết thúc chuyến' })
  await expect(panel.getByRole('timer')).toHaveText('00:00:00')
  await expect(panel.getByRole('button')).toHaveCount(0)
})

test('provider can finalize manually after system end confirmation without a fabricated renter timestamp', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'provider-trip-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    if (path.endsWith('/workflow')) return route.fulfill({ json: { data: {
      bookingCode: 'END-TEST', bookingStatus: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', refunds: [], disputes: [],
      endConfirmations: [{ id: 7, state: 'CONFIRMED', confirmationMethod: 'AUTO', reportedAt: new Date().toISOString(), confirmedAt: new Date().toISOString() }]
    } } })
    if (path.endsWith('/END-TEST')) return route.fulfill({ json: { data: {
      id: 1, bookingCode: 'END-TEST', status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', vehicleName: 'Xe kiểm thử',
      providerConfirmedAt: new Date().toISOString(), renterConfirmedAt: null, charges: [], paymentTransactions: [], totalAmount: 1000000
    } } })
    return route.fulfill({ json: { data: [] } })
  })
  await page.goto('/provider/bookings/END-TEST')
  await expect(page.getByRole('button', { name: 'Chốt quyết toán', exact: true }).first()).toBeVisible()
  await expect(page.getByRole('region', { name: 'Xác nhận kết thúc chuyến' })).toContainText('Hệ thống tự xác nhận do hết thời hạn')
})
