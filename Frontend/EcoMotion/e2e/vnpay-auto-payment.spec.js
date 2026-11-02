import { expect, test } from '@playwright/test'

const request = {
  id: 10, bookingCode: 'EM-VNPAY', paymentCode: 'VP000010', paymentType: 'DEPOSIT',
  amount: 30000, paidAmount: 0, remainingAmount: 30000, status: 'PENDING', evidences: [],
}

test('renter defaults to VNPay without proof upload and follows backend confirmation', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'vnpay-test'))
  let confirmed = false
  const mutations = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (route.request().method() !== 'GET') mutations.push(path)
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    let data = {}
    if (path.endsWith('/payment-requests')) data = [{ ...request, status: confirmed ? 'CONFIRMED' : 'PENDING' }]
    if (path.endsWith('/receipt')) data = {
      status: 'SUCCESS', bookingCode: request.bookingCode, transactionCode: 'VP000011', amount: 30000,
      paymentMethod: 'VNPAY', verificationMethod: 'GATEWAY_VERIFIED', paidAt: '2026-11-02T08:00:00Z',
    }
    await route.fulfill({ json: { data } })
  })
  await page.goto('/renter/bookings/EM-VNPAY/payment')
  await expect(page.getByRole('button', { name: 'Tiếp tục với VNPAY' })).toBeVisible()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  confirmed = true
  await expect(page).toHaveURL(/payment\/success\?reference=VP000010/, { timeout: 12000 })
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  expect(mutations).toEqual([])
})

test('provider cannot manually confirm VNPay and keeps discrepancy handling', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'vnpay-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    let data = {}
    if (path.endsWith('/bank-accounts') || path.endsWith('/refunds')) data = []
    if (path.endsWith('/transactions')) data = { items: [], total: 0 }
    if (path.endsWith('/payment-requests')) data = { items: [{ ...request, gatewayManaged: true }], total: 1 }
    await route.fulfill({ json: { data } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  await expect(page.getByText('VNPay tự động xác minh', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Báo lệch', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /Yêu cầu hoàn tiền/ })).toBeVisible()
})
