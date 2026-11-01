import { expect, test } from '@playwright/test'

test('received money requiring reconciliation never claims the booking was confirmed', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'payment-test'))
  let requestStatus = 'PENDING'
  let payload
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe kiểm thử' } })
    let data = {}
    if (path.endsWith('/bank-accounts') || path.endsWith('/refunds')) data = []
    if (path.endsWith('/transactions')) data = { items: [], total: 0 }
    if (path.endsWith('/payment-requests')) data = {
      items: [{ id: 10, bookingCode: 'EM-LATE', paymentCode: 'VQ000010', paymentType: 'DEPOSIT',
        amount: 30000, paidAmount: 0, remainingAmount: 30000, transferReference: 'EMLATEDEP',
        status: requestStatus, evidences: [], createdAt: '2026-10-30T10:00:00Z' }], total: 1,
    }
    if (path.endsWith('/10/confirm')) {
      payload = route.request().postDataJSON()
      requestStatus = 'REVIEW_REQUIRED'
      data = { id: 10, status: requestStatus, paidAmount: 0 }
    }
    await route.fulfill({ json: { data } })
  })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
  await page.getByPlaceholder('FT24...').fill('LATE-TRANSFER')
  await page.getByRole('button', { name: 'Xác nhận đã nhận tiền', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Booking chưa được xác nhận; cần đối soát hoặc hoàn tiền.')
  expect(payload).toMatchObject({ receivedAmount: 30000, bankTransactionRef: 'LATE-TRANSFER' })
})
