import { expect, test } from '@playwright/test'

async function authenticated(page, handler) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'lifecycle-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Demo renter' } })
    const data = handler(path)
    return route.fulfill({ json: { data } })
  })
}

test('repaired confirmed deposit shows verified receipt and never offers another payment', async ({ page }) => {
  const writes = []
  page.on('request', r => { if (r.method() === 'POST' && !r.url().includes('/auth/')) writes.push(r.url()) })
  await authenticated(page, path => {
    if (path.endsWith('/payment-requests')) return [{ id: 12, bookingCode: 'EM-SEED-R12-001', paymentCode: 'VQ000012',
      paymentType: 'DEPOSIT', status: 'CONFIRMED', amount: 330000, paidAmount: 330000, remainingAmount: 0 }]
    if (path.endsWith('/payments/receipt')) return { status: 'SUCCESS', bookingCode: 'EM-SEED-R12-001',
      transactionCode: 'VQ000012', amount: 330000, paymentMethod: 'BANK_TRANSFER', verificationMethod: 'PROVIDER_CONFIRMED', paidAt: '2026-11-05T03:58:00Z' }
    return {}
  })
  await page.goto('/renter/bookings/EM-SEED-R12-001/payment')
  await expect(page.getByRole('heading', { name: 'Thanh toán thành công!' })).toBeVisible()
  await expect(page.getByText('330.000 ₫', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /Tiếp tục với VNPAY/ })).toHaveCount(0)
  expect(writes).toEqual([])
})

test('cancelled booking awaiting refund never claims the refund has completed', async ({ page }) => {
  await authenticated(page, path => {
    if (path.endsWith('/workflow')) return {
      bookingCode: 'EM-SEED-R12-001', bookingStatus: 'CANCELLATION_REVIEW', paymentStatus: 'REFUND_PENDING',
      driverStatus: 'NOT_REQUIRED', tripStatus: 'CANCELLED', settlementStatus: 'NOT_READY', confirmedPaid: 330000,
      refundedAmount: 0, pendingRefundAmount: 330000, refundableAmount: 0, remainingAmount: 0,
      needsDriver: false, nextAction: 'Đang đối soát hoàn tiền.', refunds: [{ id: 1, amount: 330000, status: 'PROCESSING' }],
    }
    if (path.endsWith('/EM-SEED-R12-001')) return {
      bookingCode: 'EM-SEED-R12-001', status: 'CANCELLATION_REVIEW', planType: 'DAILY', vehicleId: 1,
      vehicleName: 'VinFast VF 6', manufactureYear: 2024, licensePlate: '51A-11111', pickupAddress: 'Điểm đón',
      pickupTime: '2026-11-12T03:00:00Z', endTime: '2026-11-13T03:00:00Z', passengerCount: 1,
      slotCount: 1, totalAmount: 1100000, charges: [], timeline: [], disputeCategories: [],
    }
    return {}
  })
  await page.goto('/renter/bookings/EM-SEED-R12-001')
  const summary = page.getByRole('region', { name: 'Tiến trình đơn thuê' })
  await expect(summary.getByText('Chưa hoàn tất', { exact: true })).toBeVisible()
  await expect(summary.getByText('Đang chờ cổng thanh toán hoặc ngân hàng', { exact: true })).toBeVisible()
  await expect(summary.getByText('Đã xác nhận tiền hoàn', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Huỷ đơn', exact: true })).toHaveCount(0)
})

