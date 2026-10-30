import { expect, test } from '@playwright/test'

const detail = { bookingCode: 'TV5-CONSENT', status: 'CONFIRMED', planType: 'HOURLY', vehicleId: 1,
  vehicleName: 'Xe đã đặt', manufactureYear: 2026, licensePlate: 'TEST-001', pickupAddress: 'Điểm đón',
  pickupTime: '2026-11-03T03:00:00Z', endTime: '2026-11-03T04:00:00Z', passengerCount: 1,
  slotCount: 1, totalAmount: 100000, charges: [], timeline: [], disputeCategories: [] }
const initial = { bookingCode: detail.bookingCode, bookingStatus: 'CONFIRMED', paymentStatus: 'PARTIALLY_PAID',
  driverStatus: 'NOT_ASSIGNED', tripStatus: 'NOT_STARTED', settlementStatus: 'NOT_READY', confirmedPaid: 30000,
  refundedAmount: 0, pendingRefundAmount: 0, refundableAmount: 30000, remainingAmount: 70000,
  needsDriver: true, resolutionDeadline: detail.pickupTime, nextAction: 'Nhà xe đang tìm tài xế.', refunds: [],
  proposal: { id: 'proposal-1', resolution: 'RESCHEDULE', pickupTime: '2026-11-04T03:00:00Z',
    reason: 'Tài xế không còn khả dụng', status: 'PENDING' } }

async function setup(page, shared, failure) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Khách kiểm thử' } })
    let data = {}
    if (path.endsWith('/TV5-CONSENT')) data = detail
    if (path.endsWith('/workflow')) data = shared.workflow
    if (path.includes('/operational-proposal/')) {
      shared.responses.push(route.request().postDataJSON())
      if (failure) return route.fulfill({ status: failure, json: { message: 'Đề xuất chưa được áp dụng; tải lại để kiểm tra.' } })
      if (shared.workflow.proposal.status !== 'PENDING') return route.fulfill({ status: 409, json: { message: 'Đề xuất đã có phản hồi' } })
      data = shared.workflow = { ...shared.workflow, proposal: { ...shared.workflow.proposal,
        status: route.request().postDataJSON().action === 'ACCEPT' ? 'ACCEPTED' : 'REJECTED' } }
    }
    if (path.endsWith('/v1/refunds')) {
      shared.refunds.push(route.request().postDataJSON())
      shared.workflow = { ...shared.workflow, refundableAmount: 0,
        refunds: [{ id: 1, amount: 30000, status: 'REQUESTED' }] }
    }
    return route.fulfill({ json: { data } })
  })
}

test('renter rejects alternative, refreshes persisted decision and requests refund with canonical payload', async ({ page }) => {
  const shared = { workflow: structuredClone(initial), responses: [], refunds: [] }
  await setup(page, shared)
  await page.goto('/renter/bookings/TV5-CONSENT')
  const summary = page.getByRole('region', { name: 'Tiến trình đơn thuê' })
  await expect(summary.getByText('Chưa có tài xế', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Từ chối phương án', exact: true }).click()
  await page.getByLabel('Ghi chú phản hồi').fill('Tôi cần giữ giờ cũ')
  await page.getByRole('button', { name: 'Xác nhận phản hồi', exact: true }).click()
  await expect(summary.getByText('Khách đã từ chối phương án')).toBeVisible()
  expect(shared.responses).toEqual([{ action: 'REJECT', reason: 'Tôi cần giữ giờ cũ' }])
  await page.reload()
  await expect(summary.getByText('Khách đã từ chối phương án')).toBeVisible()
  await summary.getByText('Yêu cầu xem xét hoàn tiền', { exact: true }).click()
  await page.getByLabel('Số tiền yêu cầu hoàn').fill('30000')
  await page.getByLabel('Lý do yêu cầu hoàn').fill('Không có tài xế')
  await page.getByRole('button', { name: 'Gửi yêu cầu hoàn tiền', exact: true }).click()
  await expect.poll(() => shared.refunds).toEqual([{ bookingCode: 'TV5-CONSENT', amount: 30000, reason: 'Không có tài xế' }])
  await expect(summary.getByText(/Chờ xem xét/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Gửi yêu cầu hoàn tiền', exact: true })).toHaveCount(0)
})

for (const status of [400, 401, 403, 404, 409, 500]) {
  test(`consent HTTP ${status} never displays an accepted change`, async ({ page }) => {
    const shared = { workflow: structuredClone(initial), responses: [], refunds: [] }
    await setup(page, shared, status)
    await page.goto('/renter/bookings/TV5-CONSENT')
    await page.getByRole('button', { name: 'Đồng ý phương án', exact: true }).click()
    await page.getByRole('button', { name: 'Xác nhận phản hồi', exact: true }).click()
    await expect.poll(() => shared.responses.length).toBe(1)
    expect(shared.workflow.proposal.status).toBe('PENDING')
    if (status !== 401) {
      await expect(page.getByText('Khách đã đồng ý phương án')).toHaveCount(0)
      await expect(page.getByRole('alert').filter({ hasText: 'Đề xuất chưa được áp dụng' })).toBeVisible()
    }
  })
}

test('second tab cannot reverse a decision already saved by first tab', async ({ page, context }) => {
  const shared = { workflow: structuredClone(initial), responses: [], refunds: [] }
  await setup(page, shared)
  const second = await context.newPage(); await setup(second, shared)
  await Promise.all([page.goto('/renter/bookings/TV5-CONSENT'), second.goto('/renter/bookings/TV5-CONSENT')])
  await page.getByRole('button', { name: 'Đồng ý phương án', exact: true }).click()
  await page.getByRole('button', { name: 'Xác nhận phản hồi', exact: true }).click()
  await expect(page.getByText('Khách đã đồng ý phương án')).toBeVisible()
  await second.getByRole('button', { name: 'Từ chối phương án', exact: true }).click()
  await second.getByRole('button', { name: 'Xác nhận phản hồi', exact: true }).click()
  await expect(second.getByText('Khách đã đồng ý phương án')).toBeVisible()
  expect(shared.workflow.proposal.status).toBe('ACCEPTED')
})
