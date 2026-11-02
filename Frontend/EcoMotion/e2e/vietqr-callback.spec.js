import { expect, test } from '@playwright/test'

const request = {
  id: 20, bookingCode: 'EM-VIETQR', paymentCode: 'VQ000020', paymentType: 'DEPOSIT',
  amount: 30000, paidAmount: 0, remainingAmount: 30000, status: 'PENDING', evidences: [],
  bankCodeSnapshot: 'VCB', bankNameSnapshot: 'Vietcombank', accountNumberSnapshot: '0123456789',
  accountHolderSnapshot: 'TEST OWNER', transferReference: 'VQ000020',
  expiresAt: new Date(Date.now() + 900000).toISOString(),
}

test('dynamic QR waits for backend confirmation and opens a persisted receipt', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'vietqr-test'))
  let issued = false
  let confirmed = false
  const writes = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    if (route.request().method() !== 'GET') writes.push(path)
    const current = { ...request, vietQrManaged: issued, qrCodePayload: issued ? '000201010212fixture' : null,
      status: confirmed ? 'CONFIRMED' : 'PENDING' }
    let data = {}
    if (path.endsWith('/vietqr/create')) { issued = true; data = { ...current, vietQrManaged: true, qrCodePayload: '000201010212fixture' } }
    if (path.endsWith('/payment-requests')) data = [current]
    if (path.endsWith('/receipt')) data = {
      status: confirmed ? 'SUCCESS' : 'PENDING_VERIFICATION', bookingCode: request.bookingCode,
      transactionCode: 'VT000001', amount: 30000, paymentMethod: 'BANK_TRANSFER',
      verificationMethod: 'VIETQR_CALLBACK', paidAt: '2026-11-02T08:00:00Z',
    }
    return route.fulfill({ json: { data } })
  })
  await page.goto('/renter/bookings/EM-VIETQR/payment')
  await page.getByRole('button', { name: /Chuyển khoản VietQR/ }).click()
  await expect(page.getByRole('img', { name: 'VietQR động' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Tạo mã QR tự động', exact: true }).click()
  await expect(page.getByRole('img', { name: 'VietQR động' })).toBeVisible()
  await expect(page.getByRole('button', { name: /VNPAY Sandbox/ })).toBeDisabled()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Tôi đã chuyển khoản/ })).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('img', { name: 'VietQR động' })).toBeVisible()
  confirmed = true
  await expect(page).toHaveURL(/payment\/success\?reference=VQ000020/, { timeout: 12000 })
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await expect(page.getByText('VT000001', { exact: true })).toBeVisible()
  expect(writes).toEqual(['/api/v1/payments/vietqr/create'])
})

test('unlinked beneficiary error never displays an automatic QR', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'vietqr-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    if (path.endsWith('/vietqr/create')) return route.fulfill({ status: 409, json: { message: 'Tài khoản nhận chưa liên kết đồng bộ VietQR' } })
    return route.fulfill({ json: { data: path.endsWith('/payment-requests') ? [request] : {} } })
  })
  await page.goto('/renter/bookings/EM-VIETQR/payment')
  await page.getByRole('button', { name: /Chuyển khoản VietQR/ }).click()
  await page.getByRole('button', { name: 'Tạo mã QR tự động', exact: true }).click()
  await expect(page.getByText('Tài khoản nhận chưa liên kết đồng bộ VietQR', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('img', { name: 'VietQR động' })).toHaveCount(0)
})

test('provider has history and discrepancy handling without a callback confirmation button', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'vietqr-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    let data = {}
    if (path.endsWith('/bank-accounts') || path.endsWith('/refunds')) data = []
    if (path.endsWith('/transactions')) data = { items: [], total: 0 }
    if (path.endsWith('/payment-requests')) data = { items: [{ ...request, vietQrManaged: true, status: 'REVIEW_REQUIRED' }], total: 1 }
    return route.fulfill({ json: { data } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  await expect(page.getByText('VietQR tự động xác minh', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Báo lệch', exact: true })).toBeVisible()
})
