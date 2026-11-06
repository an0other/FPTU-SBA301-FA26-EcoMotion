import { expect, test } from '@playwright/test'

test('manual partial transfer shows reconciliation controls and remaining amount', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'provider-test'))
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà cung cấp' } })
    if (path === '/api/v1/provider/payment-requests') return route.fulfill({ json: { data: { items: [
      { id: 3, bookingCode: 'EM-MANUAL', paymentType: 'DEPOSIT', status: 'PARTIALLY_PAID', amount: 100000, paidAmount: 40000, remainingAmount: 60000, evidences: [] },
    ], total: 1 } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  const row = page.getByRole('row').filter({ hasText: 'EM-MANUAL' })
  await expect(row.getByRole('button', { name: /Xác nhận/ })).toBeVisible()
  await expect(row.getByRole('button', { name: 'Từ chối' })).toHaveCount(0)
  await row.getByRole('button', { name: /Xác nhận/ }).click()
  await expect(page.locator('input[type=number]')).toHaveValue('60000')
  await expect(page.getByText('Còn thiếu:', { exact: false }).last()).toBeVisible()
})

test('a pending return waits for a verified server result', async ({ page }) => {
  let queries = 0
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/callback')) return route.fulfill({ json: { data: { status: 'PENDING_VERIFICATION', bookingCode: 'BK1', vnpTxnRef: 'REF1', amount: 544000, message: 'Đang xác nhận giao dịch' } } })
    if (path.endsWith('/query')) {
      queries += 1
      return route.fulfill({ json: { data: { status: 'SUCCESS', bookingCode: 'BK1', vnpTxnRef: 'REF1', amount: 544000, message: 'Thanh toán đã được xác minh' } } })
    }
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/payment/vnpay/return?vnp_TxnRef=REF1')
  await expect(page.getByRole('heading', { name: 'Đang xác nhận giao dịch' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible({ timeout: 10000 })
  expect(queries).toBe(1)
})

test('a temporary callback error retries without showing payment success', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/callback')) {
      attempts += 1
      if (attempts === 1) return route.fulfill({ status: 503, json: { message: 'Tạm thời không kết nối được' } })
      return route.fulfill({ json: { data: { status: 'PENDING_VERIFICATION', bookingCode: 'BK1', vnpTxnRef: 'REF1', amount: 544000, message: 'Đang xác nhận giao dịch' } } })
    }
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/payment/vnpay/return?vnp_TxnRef=REF1')
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toHaveCount(0)
  await expect.poll(() => attempts, { timeout: 10000 }).toBe(2)
  await expect(page.getByRole('heading', { name: 'Đang xác nhận giao dịch' })).toBeVisible()
})

test('provider sees verified VNPAY status without manual controls', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'provider-test'))
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà cung cấp' } })
    if (path === '/api/v1/provider/payment-requests') return route.fulfill({ json: { data: { items: [
      { id: 1, bookingCode: 'EM-PENDING', paymentType: 'DEPOSIT', status: 'PENDING', amount: 486000, paidAmount: 0, remainingAmount: 486000, vnpayVerifying: true, evidences: [] },
      { id: 2, bookingCode: 'EM-PAID', paymentType: 'DEPOSIT', status: 'CONFIRMED', amount: 486000, paidAmount: 486000, remainingAmount: 0, vnpayPaid: true, vnpayTransactionNo: '123456789', vnpayPaidAt: '2026-11-05T12:00:00Z', evidences: [] },
    ], total: 2 } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  const pending = page.getByRole('row').filter({ hasText: 'EM-PENDING' })
  await expect(pending.getByText('Đang kiểm tra thanh toán VNPAY')).toBeVisible()
  await expect(pending.getByRole('button', { name: 'Xác nhận chuyển khoản' })).toHaveCount(0)
  await expect(pending.getByRole('button', { name: 'Từ chối' })).toHaveCount(0)
  const paid = page.getByRole('row').filter({ hasText: 'EM-PAID' })
  await expect(paid.getByText('Đã thanh toán qua VNPAY')).toBeVisible()
  await expect(paid.getByText('123456789')).toBeVisible()
})
