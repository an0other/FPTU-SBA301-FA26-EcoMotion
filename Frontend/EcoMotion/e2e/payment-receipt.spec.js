import { expect, test } from '@playwright/test'

const receipt = {
  status: 'SUCCESS', bookingCode: 'EM-RECEIPT', transactionCode: 'VP000010',
  vnpTransactionNo: 'BANK-VERIFIED', amount: 30000, paymentMethod: 'VNPAY',
  verificationMethod: 'GATEWAY_VERIFIED', paidAt: '2026-10-30T10:00:00Z',
}

async function mockReceipt(page, handler) {
  await page.route('**/api/**', async route => {
    if (new URL(route.request().url()).pathname.endsWith('/payments/receipt')) return handler(route)
    await route.fulfill({ json: { data: null } })
  })
}

test('direct URL without a reference never invents a success receipt', async ({ page }) => {
  let reads = 0
  await mockReceipt(page, route => { reads += 1; return route.fulfill({ json: { data: receipt } }) })
  await page.goto('/payment/success?bookingCode=FORGED&amount=999999&paymentMethod=VNPAY')
  await expect(page.getByRole('heading', { name: 'Không thể xác minh thanh toán' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toHaveCount(0)
  expect(reads).toBe(0)
})

test('query amount, code, date and method cannot override server receipt and reload revalidates', async ({ page }) => {
  let reads = 0
  const mutations = []
  page.on('request', request => { if (request.method() !== 'GET' && !request.url().includes('/auth/')) mutations.push(request.url()) })
  await mockReceipt(page, route => {
    reads += 1
    expect(new URL(route.request().url()).searchParams.get('reference')).toBe('VP000010')
    return route.fulfill({ json: { data: receipt } })
  })
  await page.goto('/payment/success?reference=VP000010&amount=999999&transactionCode=FORGED&bookingCode=FAKE&paidAt=2099-01-01&paymentMethod=CASH')
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await expect(page.getByText('30.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('EM-RECEIPT', { exact: true })).toBeVisible()
  await expect(page.getByText('FORGED', { exact: true })).toHaveCount(0)
  await expect(page.getByText('FAKE', { exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('30.000 ₫', { exact: true })).toBeVisible()
  expect(reads).toBe(2)
  expect(mutations).toEqual([])
})

for (const [status, title] of [
  ['PENDING_VERIFICATION', 'Đang xác minh thanh toán'],
  ['FAILED', 'Thanh toán không thành công'],
  ['RECONCILIATION_REQUIRED', 'Đã nhận tiền, cần đối soát đơn thuê'],
]) {
  test(`server ${status} never displays a successful receipt`, async ({ page }) => {
    await mockReceipt(page, route => route.fulfill({ json: { data: { ...receipt, status } } }))
    await page.goto('/payment/success?reference=VP000010&status=SUCCESS')
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toHaveCount(0)
    await expect(page.getByLabel('Biên lai thanh toán')).toHaveCount(0)
  })
}

for (const status of [401, 403, 404, 500]) {
  test(`receipt HTTP ${status} hides all success data`, async ({ page }) => {
    await mockReceipt(page, route => route.fulfill({ status, json: { message: 'Không thể đọc giao dịch' } }))
    await page.goto('/payment/success?transactionCode=OTHER-USERS-CODE&amount=30000')
    await expect(page.getByRole('heading', { name: 'Không thể xác minh thanh toán' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toHaveCount(0)
    await expect(page.getByLabel('Biên lai thanh toán')).toHaveCount(0)
  })
}

test('network failure can be retried without trusting router state', async ({ page }) => {
  let offline = true
  await page.addInitScript(() => {
    history.replaceState({ ...history.state, usr: { reference: 'VP000010', amount: 999999, transactionCode: 'FORGED', status: 'SUCCESS' } }, '')
  })
  await mockReceipt(page, route => offline ? route.abort() : route.fulfill({ json: { data: receipt } }))
  await page.goto('/payment/success')
  await expect(page.getByRole('heading', { name: 'Không thể xác minh thanh toán' })).toBeVisible()
  offline = false
  await page.getByRole('button', { name: 'Kiểm tra lại' }).click()
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await expect(page.getByText('30.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByText('FORGED', { exact: true })).toHaveCount(0)
})

test('a successful manual receipt is labelled manual confirmation', async ({ page }) => {
  await mockReceipt(page, route => route.fulfill({ json: { data: { ...receipt, paymentMethod: 'BANK_TRANSFER', verificationMethod: 'PROVIDER_CONFIRMED' } } }))
  await page.goto('/payment/success?reference=VQ000010')
  await expect(page.getByText('Khoản chuyển khoản đã được xác nhận thủ công và máy chủ ghi nhận.')).toBeVisible()
})
