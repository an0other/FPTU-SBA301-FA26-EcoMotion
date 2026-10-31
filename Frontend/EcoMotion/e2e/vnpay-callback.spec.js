import { expect, test } from '@playwright/test'

const pending = {
  bookingCode: 'EM-2026-000026', vnpTxnRef: 'test-ref', amount: 1440000,
  status: 'PENDING_VERIFICATION', success: false, retryAllowed: false,
  message: 'Đang xác minh thanh toán. Vui lòng không thanh toán lại.',
}
const callbackUrl = '/payment/vnpay/callback?vnp_TxnRef=test-ref&vnp_ResponseCode=00&vnp_Amount=99999999'

test('verified funds requiring reconciliation never show booking success or retry', async ({ page }) => {
  await mockPayments(page, { ...pending, status: 'RECONCILIATION_REQUIRED', message: 'Đã nhận tiền, cần xử lý hoàn tiền hoặc đối soát.' })
  await page.goto(callbackUrl)
  await expect(page.getByRole('heading', { name: 'Đã nhận tiền, cần đối soát đơn thuê' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toHaveCount(0)
})

async function mockPayments(page, callback, query = callback) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    let data = {}
    if (url.pathname.endsWith('/callback')) data = typeof callback === 'function' ? callback() : callback
    if (url.pathname.endsWith('/query')) data = typeof query === 'function' ? query() : query
    await route.fulfill({ json: { data } })
  })
}

test('return before IPN stays pending and updates automatically when verified', async ({ page }) => {
  let queries = 0
  await mockPayments(page, pending, () => {
    queries += 1
    return { ...pending, status: 'SUCCESS', success: true, message: 'Thanh toán đã được xác minh và ghi nhận' }
  })
  await page.goto(callbackUrl)
  await expect(page.getByRole('heading', { name: 'Đang xác minh thanh toán' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await expect(page.getByText('1.440.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Đơn của tôi' })).toHaveAttribute('href', '/renter/bookings')
  await page.waitForTimeout(3300)
  expect(queries).toBe(1)
})

test('refresh pending result is read only and does not create another payment', async ({ page }) => {
  let creates = 0
  page.on('request', request => { if (request.url().includes('create-url')) creates += 1 })
  await mockPayments(page, pending)
  await page.goto(callbackUrl)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Đang xác minh thanh toán' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveCount(0)
  expect(creates).toBe(0)
})

test('failed result only offers retry when backend allows it', async ({ page }) => {
  let retryAllowed = false
  await mockPayments(page, () => ({ ...pending, status: 'FAILED', retryAllowed, message: 'Giao dịch đã được xác minh thất bại' }))
  await page.goto(callbackUrl)
  await expect(page.getByRole('heading', { name: 'Thanh toán không thành công' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveCount(0)
  retryAllowed = true
  await page.reload()
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveAttribute('href', '/renter/bookings/EM-2026-000026/payment')
})

test('invalid signature never uses query parameters as receipt data', async ({ page }) => {
  await mockPayments(page, { status: 'PENDING_VERIFICATION', message: 'Chữ ký không hợp lệ', retryAllowed: false })
  await page.goto(callbackUrl)
  await expect(page.getByText('Chữ ký không hợp lệ', { exact: true })).toBeVisible()
  await expect(page.getByText('Mã đơn đặt xe', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Thử lại thanh toán' })).toHaveCount(0)
})

test('polling stops after forty attempts', async ({ page }) => {
  let queries = 0
  await mockPayments(page, pending, () => { queries += 1; return pending })
  await page.clock.install()
  await page.goto(callbackUrl)
  await expect(page.getByText('Chờ đối soát', { exact: true })).toBeVisible()
  for (let i = 0; i < 40; i += 1) {
    await page.clock.runFor(3100)
    await expect.poll(() => queries).toBe(i + 1)
  }
  await page.clock.runFor(30000)
  expect(queries).toBe(40)
})

test('polling cleans up on navigation', async ({ page }) => {
  let queries = 0
  await mockPayments(page, pending, () => { queries += 1; return pending })
  await page.clock.install()
  await page.goto(callbackUrl)
  await expect(page.getByText('Chờ đối soát', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Về trang chủ', exact: true }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'Đang xác minh thanh toán' })).toHaveCount(0)
  await page.clock.runFor(6000)
  const before = queries
  await page.clock.runFor(10000)
  expect(queries).toBe(before)
})

test('verified receipt fits a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockPayments(page, { ...pending, status: 'SUCCESS', success: true, message: 'Đã xác minh', vnpTransactionNo: '1234567890' })
  await page.goto(callbackUrl)
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
