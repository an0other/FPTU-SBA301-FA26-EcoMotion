import { expect, test } from '@playwright/test'
import { Buffer } from 'node:buffer'

test('driver confirms, arrives and waits for renter before showing the trip as started', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  let state = 'ASSIGNED'
  const statuses = { ASSIGNED: 'DRIVER_ASSIGNED', CONFIRMED: 'DRIVER_CONFIRMED', ARRIVED: 'DRIVER_ARRIVED', AWAITING_START_CONFIRMATION: 'AWAITING_START_CONFIRMATION' }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 3, role: 'DRIVER', fullName: 'Tài xế kiểm thử' } })
    let data = {}
    if (path.includes('/driver/trips/')) {
      if (path.endsWith('/confirm')) state = 'CONFIRMED'
      if (path.endsWith('/arrive')) state = 'ARRIVED'
      if (path.endsWith('/start')) state = 'AWAITING_START_CONFIRMATION'
      data = { tripId: 1, bookingCode: 'TV5-DRIVER', status: statuses[state], bookingStatus: statuses[state], executionStatus: state,
        pickupAddress: 'Điểm đón kiểm thử', scheduledPickupAt: '2026-10-29T03:00:00Z',
        vehicle: { name: 'Xe kiểm thử', licensePlate: 'TEST-001' }, customer: { fullName: 'Khách kiểm thử' } }
      if (path.endsWith('/evidence') || path.endsWith('/incidents')) data = []
    }
    return route.fulfill({ json: { data } })
  })
  await page.goto('/driver/trips/TV5-DRIVER')
  for (const label of ['Xác nhận nhận chuyến', 'Đã đến điểm đón', 'Bắt đầu chuyến']) {
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.getByRole('button', { name: 'Xác nhận thực hiện', exact: true }).click()
  }
  await expect(page.getByText('Đã xác nhận bắt đầu; đang chờ người thuê xác nhận.', { exact: true })).toBeVisible()
  await expect(page.getByText('Chuyến xe này đã hoàn tất tác nghiệp.', { exact: true })).toHaveCount(0)
})

test('renter refreshes from DRIVER_CONFIRMED and ignores an older response', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  let releaseOld
  const oldBlocked = new Promise(resolve => { releaseOld = resolve })
  let firstSeen
  const firstRequest = new Promise(resolve => { firstSeen = resolve })
  let requests = 0
  const detail = {
    bookingCode: 'TV5-SYNC', status: 'DRIVER_CONFIRMED', planType: 'HOURLY', vehicleId: 1,
    vehicleName: 'VinFast VF 8', manufactureYear: 2026, licensePlate: 'TEST-001',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', pickupAddress: 'Điểm đón kiểm thử',
    pickupTime: '2026-10-29T03:00:00Z', endTime: '2026-10-29T05:00:00Z',
    passengerCount: 1, slotCount: 2, totalAmount: 200000, charges: [], timeline: [], disputeCategories: [],
  }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    if (path === '/api/renter/bookings/TV5-SYNC') {
      requests++
      if (requests === 1) {
        firstSeen()
        await oldBlocked
        return route.fulfill({ json: { data: detail } })
      }
      return route.fulfill({ json: { data: { ...detail, status: 'DRIVER_ARRIVED', canConfirmStart: true } } })
    }
    return route.fulfill({ json: { data: { atRisk: false } } })
  })
  await page.goto('/renter/bookings/TV5-SYNC')
  await firstRequest
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  const start = page.getByRole('button', { name: 'Xác nhận bắt đầu chuyến', exact: true })
  await expect(start).toBeVisible()
  const oldResponse = page.waitForResponse(async response => {
    if (!response.url().endsWith('/api/renter/bookings/TV5-SYNC')) return false
    return (await response.json()).data?.status === 'DRIVER_CONFIRMED'
  })
  releaseOld()
  await (await oldResponse).finished()
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(start).toBeVisible()
  await expect.poll(() => requests).toBeGreaterThan(1)
})

test('refund approval uses the DTO and only proof confirmation marks money refunded', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  let state = 'REQUESTED'
  let reviewBody
  let proofSeen
  const proofRequest = new Promise(resolve => { proofSeen = resolve })
  let releaseProof
  const proofBlocked = new Promise(resolve => { releaseProof = resolve })
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    let data = {}
    if (path.endsWith('/refunds')) data = [{ id: 1, bookingCode: 'TV5-REFUND', paymentTransactionId: 42, amount: 10000, reason: 'Kiểm thử', status: state }]
    if (path.endsWith('/bank-accounts')) data = []
    if (path.endsWith('/transactions') || path.endsWith('/payment-requests')) data = { items: [], total: 0 }
    if (path.endsWith('/refunds/1/review')) { reviewBody = route.request().postDataJSON(); state = 'PROCESSING'; data = { status: state } }
    if (path.endsWith('/payment-transactions/42/refund-proof')) {
      expect(route.request().headers()['content-type']).toContain('multipart/form-data')
      proofSeen()
      await proofBlocked
      state = 'REFUNDED'
      data = { status: 'PAID' }
    }
    return route.fulfill({ json: { data } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu hoàn tiền/ }).click()
  await page.getByRole('button', { name: 'Xem xét duyệt' }).click()
  await page.getByRole('button', { name: 'Xác nhận kết quả' }).click()
  await expect.poll(() => reviewBody).toEqual({ action: 'APPROVE' })
  await page.getByRole('button', { name: 'Tải biên lai hoàn tiền' }).click()
  const dialog = page.getByRole('dialog', { name: 'Biên lai hoàn tiền' })
  await dialog.locator('input[type=file]').setInputFiles({ name: 'proof.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 Test') })
  await dialog.getByLabel('Mã giao dịch').fill('TV5-REF-TEST')
  await dialog.getByRole('button', { name: 'Gửi biên lai xác nhận' }).click()
  await proofRequest
  await expect(page.getByText('Đã hoàn tiền', { exact: true })).toHaveCount(0)
  releaseProof()
  await expect(page.getByText('Đã hoàn tiền', { exact: true }).first()).toBeVisible()
})
