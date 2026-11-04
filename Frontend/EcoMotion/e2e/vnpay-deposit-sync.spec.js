import { expect, test } from '@playwright/test'

const bookingCode = 'P0-VNPAY-DEPOSIT'

async function setup(page, role, shared) {
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'p0-test')
    const sources = []
    window.EventSource = class {
      constructor() { this.listeners = {}; sources.push(this) }
      addEventListener(name, listener) { this.listeners[name] = listener }
      close() { this.closed = true }
    }
    window.emitPaymentVerified = () => sources.filter(source => !source.closed).forEach(source => {
      source.listeners.PAYMENT_VERIFIED?.({ type: 'PAYMENT_VERIFIED', data: JSON.stringify({ bookingCode: 'P0-VNPAY-DEPOSIT' }) })
    })
  })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') return route.fulfill({ json: { id: role === 'PROVIDER' ? 2 : 1, role, fullName: 'Kiểm thử P0' } })
    if (route.request().method() !== 'GET') shared.writes.push(path)
    const status = shared.confirmed ? 'CONFIRMED' : 'AWAITING_DEPOSIT'
    const paymentStatus = shared.confirmed ? 'DEPOSIT_PAID' : 'VERIFYING'
    let data = {}
    if (path.endsWith(`/${bookingCode}`)) data = {
      bookingCode, status, paymentStatus, planType: 'HOURLY', vehicleId: 1, vehicleName: 'Xe kiểm thử',
      manufactureYear: 2026, licensePlate: 'P0-001', pickupAddress: 'Điểm đón', pickupTime: '2030-11-03T03:00:00Z',
      endTime: '2030-11-03T07:00:00Z', plannedEndAt: '2030-11-03T07:00:00Z', passengerCount: 1, slotCount: 4,
      totalAmount: 3200000, depositRatio: 0.3, charges: [], transactions: [], timeline: [], disputeCategories: [],
      canAssignDriver: shared.confirmed,
    }
    if (path.endsWith(`/${bookingCode}`) && role === 'PROVIDER' && shared.pauseNext) {
      shared.pauseNext = false
      await new Promise(resolve => { shared.release = resolve })
    }
    if (path.endsWith('/workflow')) data = {
      bookingCode, bookingStatus: status, paymentStatus, confirmedPaid: shared.confirmed ? 960000 : 0,
      remainingAmount: shared.confirmed ? 2240000 : 3200000, driverStatus: 'NOT_ASSIGNED', tripStatus: 'NOT_STARTED',
      settlementStatus: 'NOT_READY', needsDriver: shared.confirmed, refunds: [], driverFeedback: [],
      nextAction: shared.confirmed ? 'Chọn tài xế cho đơn thuê.' : 'Đang xác minh thanh toán.',
    }
    if (path.endsWith('/incidents') || path.endsWith('/evidences')) data = []
    if (path.endsWith('/bank-accounts') || path.endsWith('/refunds')) data = []
    if (path.endsWith('/transactions')) data = { items: shared.confirmed ? [{ id: 91, bookingCode, type: 'DEPOSIT_PAY',
      amount: 960000, direction: 'IN', method: 'VNPAY', status: 'PAID', verificationMethod: 'GATEWAY_VERIFIED',
      paidAt: '2030-11-02T08:00:00Z', transactionCode: 'VP000091' }] : [], total: shared.confirmed ? 1 : 0 }
    return route.fulfill({ json: { data } })
  })
}

test('both participants update after PAYMENT_VERIFIED without reload or client money writes', async ({ browser }) => {
  test.slow()
  const context = await browser.newContext()
  const provider = await context.newPage()
  const renterContext = await browser.newContext()
  const renter = await renterContext.newPage()
  const shared = { confirmed: false, writes: [] }
  try {
    await setup(provider, 'PROVIDER', shared)
    await setup(renter, 'RENTER', shared)
    await provider.goto(`/provider/bookings/${bookingCode}`)
    await renter.goto(`/renter/bookings/${bookingCode}`)
    await expect(provider.getByText('Thanh toán: Đang xác minh thanh toán', { exact: true })).toBeVisible()
    const providerProgress = provider.getByRole('region', { name: 'Tiến trình đơn thuê' })
    const renterProgress = renter.getByRole('region', { name: 'Tiến trình đơn thuê' })
    await expect(providerProgress.getByText('Đang xác minh thanh toán', { exact: true })).toBeVisible()
    shared.pauseNext = true
    await provider.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect.poll(() => Boolean(shared.release)).toBe(true)
    shared.confirmed = true
    await provider.evaluate(() => window.emitPaymentVerified())
    await renter.evaluate(() => window.emitPaymentVerified())
    shared.release()
    await expect(provider.getByText('Thanh toán: Đã đặt cọc', { exact: true })).toBeVisible()
    await expect(providerProgress.getByText('Đã thanh toán cọc', { exact: true })).toBeVisible()
    await expect(renterProgress.getByText('Đã thanh toán cọc', { exact: true })).toBeVisible()
    await expect(providerProgress.getByText('Chưa có tài xế', { exact: true })).toBeVisible()
    await expect(providerProgress.getByText(/960.000/).first()).toBeVisible()
    await expect(renterProgress.getByText(/2.240.000/).first()).toBeVisible()
    await expect(provider.getByText('Thanh toán: Đã tất toán', { exact: true })).toHaveCount(0)
    await provider.reload()
    await expect(provider.getByText('Thanh toán: Đã đặt cọc', { exact: true })).toBeVisible()
    expect(shared.writes).toEqual([])
  } finally { await context.close(); await renterContext.close() }
})

test('provider finance refreshes persisted payment history after verification', async ({ page }) => {
  const shared = { confirmed: false, writes: [] }
  await setup(page, 'PROVIDER', shared)
  await page.goto('/provider/finance')
  await expect(page.getByText(bookingCode, { exact: true })).toHaveCount(0)
  shared.confirmed = true
  await page.evaluate(() => window.emitPaymentVerified())
  await expect(page.getByText(bookingCode, { exact: true })).toBeVisible()
  await expect(page.getByText(/960.000/).first()).toBeVisible()
  expect(shared.writes).toEqual([])
})
