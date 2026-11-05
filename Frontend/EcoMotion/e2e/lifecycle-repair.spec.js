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


test('completed policy refund finishes the stepper even when less than the original deposit', async ({ page }) => {
  await authenticated(page, path => {
    if (path.endsWith('/workflow')) return {
      bookingCode: 'EM-SEED-R12-001', bookingStatus: 'CANCELLED', paymentStatus: 'REFUNDED',
      driverStatus: 'NOT_REQUIRED', tripStatus: 'CANCELLED', settlementStatus: 'NOT_READY', confirmedPaid: 330000,
      refundedAmount: 165000, pendingRefundAmount: 0, refundableAmount: 0, remainingAmount: 0,
      needsDriver: false, nextAction: 'Nhà xe đã xác nhận chuyển khoản hoàn tiền theo chính sách.', refunds: [{ id: 1, amount: 165000, status: 'REFUNDED' }],
    }
    if (path.endsWith('/EM-SEED-R12-001')) return {
      bookingCode: 'EM-SEED-R12-001', status: 'CANCELLED', planType: 'DAILY', vehicleId: 1,
      vehicleName: 'VinFast VF 6', manufactureYear: 2024, licensePlate: '51A-11111', pickupAddress: 'Điểm đón',
      pickupTime: '2026-11-12T03:00:00Z', endTime: '2026-11-13T03:00:00Z', passengerCount: 1,
      slotCount: 1, totalAmount: 1100000, charges: [], timeline: [], disputeCategories: [],
    }
    return {}
  })
  await page.goto('/renter/bookings/EM-SEED-R12-001')
  const summary = page.getByRole('region', { name: 'Tiến trình đơn thuê' })
  await expect(summary.getByText('Đã hoàn tất toàn bộ', { exact: true })).toBeVisible()
  await expect(summary.getByText('Nghĩa vụ hoàn tiền còn tồn tại', { exact: true })).toHaveCount(0)
  await expect(summary.getByText('Đã xác nhận tiền hoàn', { exact: true })).toBeVisible()
})

test('renter acknowledges a transfer and can stop automatic acknowledgement by reporting missing funds', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-11-05T05:00:00Z'))
  let receiptStatus = 'WAITING'
  await authenticated(page, path => {
    if (path.endsWith('/workflow')) return {
      bookingCode: 'EM-SEED-R12-001', bookingStatus: 'CANCELLED', paymentStatus: 'REFUNDED',
      driverStatus: 'NOT_REQUIRED', tripStatus: 'CANCELLED', settlementStatus: 'NOT_READY', confirmedPaid: 330000,
      refundedAmount: 165000, pendingRefundAmount: 0, refundableAmount: 165000, remainingAmount: 0,
      needsDriver: false, nextAction: 'Nhà xe đã xác nhận chuyển khoản hoàn tiền theo chính sách.', refunds: [{ id: 1, amount: 165000, status: 'REFUNDED', receiptStatus, receiptWaitingAt: '2026-11-05T05:00:00Z', receiptDeadline: '2026-11-08T05:00:00Z', receiptConfirmedAt: receiptStatus === 'CONFIRMED' ? '2026-11-05T06:00:00Z' : null, receiptReportedAt: receiptStatus === 'REPORTED_NOT_RECEIVED' ? '2026-11-05T06:00:00Z' : null }],
    }
    if (path.endsWith('/EM-SEED-R12-001')) return {
      bookingCode: 'EM-SEED-R12-001', status: 'CANCELLED', planType: 'DAILY', vehicleId: 1,
      vehicleName: 'VinFast VF 6', manufactureYear: 2024, licensePlate: '51A-11111', pickupAddress: 'Điểm đón',
      pickupTime: '2026-11-12T03:00:00Z', endTime: '2026-11-13T03:00:00Z', passengerCount: 1,
      slotCount: 1, totalAmount: 1100000, charges: [], timeline: [], disputeCategories: [],
    }
    return {}
  })
  await page.route('**/api/refunds/1/*', async route => {
    receiptStatus = route.request().url().endsWith('/not-received') ? 'REPORTED_NOT_RECEIVED' : 'CONFIRMED'
    await route.fulfill({ json: { data: null } })
  })
  await page.goto('/renter/bookings/EM-SEED-R12-001')
  const summary = page.getByRole('region', { name: 'Tiến trình đơn thuê' })
  await expect(summary.getByRole('timer')).toContainText('72:00:00')
  await page.clock.setFixedTime(new Date('2026-11-05T05:00:01Z'))
  await expect(summary.getByRole('timer')).toContainText('71:59:59')
  await expect(summary.getByText('Yêu cầu xem xét hoàn tiền', { exact: true })).toHaveCount(0)
  await page.screenshot({ path: `${process.env.TEMP}/ecomotion-refund-countdown-desktop.png`, fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(summary.getByRole('timer')).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: `${process.env.TEMP}/ecomotion-refund-countdown-mobile.png`, fullPage: true })
  await expect(summary.getByRole('button', { name: 'Đã nhận tiền', exact: true })).toBeVisible()
  await expect(summary.getByText(/Hạn phản hồi của người thuê/)).toBeVisible()
  await summary.getByRole('button', { name: 'Chưa nhận được tiền', exact: true }).click()
  await expect(summary.getByText(/Đã dừng tự động xác nhận; nhà xe cần đối soát/)).toBeVisible()
  await summary.getByRole('button', { name: 'Đã nhận tiền', exact: true }).click()
  await expect(summary.getByText('Đã hoàn tất toàn bộ', { exact: true })).toBeVisible()
  await expect(summary.getByText(/Người thuê đã xác nhận nhận tiền:/)).toBeVisible()
  await expect(summary.getByRole('timer')).toHaveCount(0)
  await expect(summary.getByText('Yêu cầu xem xét hoàn tiền', { exact: true })).toHaveCount(0)
  await expect(summary.getByRole('button', { name: 'Đã nhận tiền', exact: true })).toHaveCount(0)
})


test('expired receipt refreshes automatically, hides actions and renter can view provider proof', async ({ page }) => {
  let reads = 0
  await authenticated(page, path => {
    if (path.endsWith('/workflow')) {
      reads++
      return { bookingCode: 'BK-RECEIPT', bookingStatus: 'CANCELLED', paymentStatus: 'REFUNDED',
        confirmedPaid: 540000, refundedAmount: 270000, pendingRefundAmount: 0, refundableAmount: 270000,
        remainingAmount: 0, refunds: [{ id: 7, paymentTransactionId: 70, amount: 270000, status: 'REFUNDED',
          proofAvailable: true, receiptStatus: reads > 1 ? 'AUTO_CONFIRMED' : 'WAITING',
          receiptWaitingAt: '2026-11-05T05:00:00Z', receiptDeadline: '2026-11-08T05:00:00Z',
          receiptAutoConfirmedAt: reads > 1 ? '2026-11-08T05:00:01Z' : null }] }
    }
    if (path.endsWith('/BK-RECEIPT')) return { bookingCode: 'BK-RECEIPT', status: 'CANCELLED', planType: 'DAILY',
      vehicleName: 'VinFast VF 6', totalAmount: 1800000, charges: [], timeline: [], disputeCategories: [] }
    return {}
  })
  await page.clock.setFixedTime(new Date('2026-11-08T05:00:02Z'))
  let proofRequests = 0
  await page.route('**/api/payment-transactions/70/refund-proof', async route => {
    expect(route.request().headers().authorization).toBe('Bearer lifecycle-test')
    proofRequests++
    await route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aH1cAAAAASUVORK5CYII=', 'base64') })
  })
  await page.goto('/renter/bookings/BK-RECEIPT')
  const summary = page.getByRole('region', { name: 'Tiến trình đơn thuê' })
  await expect(summary.getByText(/Tự động xác nhận do hết thời hạn:/)).toBeVisible()
  expect(reads).toBeGreaterThan(1)
  await expect(summary.getByRole('timer')).toHaveCount(0)
  await expect(summary.getByRole('button', { name: 'Đã nhận tiền', exact: true })).toHaveCount(0)
  await expect(summary.getByRole('button', { name: 'Chưa nhận được tiền', exact: true })).toHaveCount(0)
  await expect(summary.getByText('Yêu cầu xem xét hoàn tiền', { exact: true })).toHaveCount(0)
  await summary.getByRole('button', { name: 'Xem biên lai hoàn tiền' }).click()
  const proof = page.getByRole('dialog', { name: 'Biên lai hoàn tiền của nhà xe' })
  await expect(proof.getByRole('img')).toBeVisible()
  await expect(proof.getByRole('link', { name: 'Tải biên lai' })).toBeVisible()
  expect(proofRequests).toBe(1)
  await proof.getByRole('button', { name: 'Đóng', exact: true }).click()
  await expect(proof).toHaveCount(0)
})
