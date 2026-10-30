import { expect, test } from '@playwright/test'

test('finance uses server collection fields and persistent payouts without invented bank transactions', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  const mutations = []
  let payoutReads = 0
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (route.request().method() !== 'GET') mutations.push(path)
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    let data = {}
    if (path.endsWith('/summary')) data = { totalConfirmedRevenue: 100000, directReceived: 70000, platformReceived: 30000, refundedAmount: 0 }
    if (path.endsWith('/bank-accounts') || path.endsWith('/refunds')) data = []
    if (path.endsWith('/transactions') || path.endsWith('/payment-requests')) data = { items: [], total: 0 }
    if (path.endsWith('/payouts')) {
      payoutReads += 1
      data = { items: [{ id: 12, bookingCode: 'TV5-PERSISTENT', netAmount: 27000, status: 'PENDING', verificationMethod: 'MOCK_SIMULATED' }], total: 1 }
    }
    await route.fulfill({ json: { data } })
  })
  await page.goto('/provider/finance')
  await expect(page.getByText('70.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('30.000 ₫', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Lịch sử chi trả' }).click()
  await expect(page.getByText('TV5-PERSISTENT', { exact: true })).toBeVisible()
  await expect(page.getByText('27.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('PENDING · MOCK/SANDBOX', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tạo lệnh rút tiền thử nghiệm' })).toHaveCount(0)
  await page.reload()
  await page.getByRole('button', { name: 'Lịch sử chi trả' }).click()
  await expect(page.getByText('TV5-PERSISTENT', { exact: true })).toBeVisible()
  expect(payoutReads).toBe(2)
  expect(mutations).toEqual([])
})
