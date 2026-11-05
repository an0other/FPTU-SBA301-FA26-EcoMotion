import { expect, test } from '@playwright/test'

const code = 'REFUND-ACCOUNT-TEST'
const account = { bankName: 'Vietcombank', accountHolder: 'NGUYEN VAN TEST', accountNumber: '0123456789' }

test('cancellation explains the deadline, saves recipient and hides stale vehicle risk', async ({ page }) => {
  let cancelled = false
  let received
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'account-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, role: 'RENTER', fullName: 'Người thuê' } })
    let data = {}
    if (path.endsWith('/cancel')) {
      received = route.request().postDataJSON(); cancelled = true
      data = { bookingCode: code, status: 'CANCELLATION_REVIEW' }
    } else if (path.endsWith('/workflow')) data = {
      bookingCode: code, bookingStatus: cancelled ? 'CANCELLATION_REVIEW' : 'CONFIRMED',
      confirmedPaid: 300000, refundedAmount: 0, pendingRefundAmount: cancelled ? 150000 : 0,
      remainingAmount: 0, refunds: cancelled ? [{ id: 1, status: 'PROCESSING', amount: 150000, refundMethod: 'BANK_TRANSFER',
        recipientBankName: received.bankName, recipientAccountHolder: received.accountHolder, recipientAccountNumber: received.accountNumber }] : [],
    }
    else if (path.endsWith('/operational-risk')) data = { atRisk: true, message: 'Chuyến trước chưa kết thúc' }
    else if (path.endsWith('/' + code)) data = {
      bookingCode: code, status: cancelled ? 'CANCELLATION_REVIEW' : 'CONFIRMED', canCancel: !cancelled,
      planType: 'DAILY', vehicleId: 1, vehicleName: 'VinFast VF 6', manufactureYear: 2024, licensePlate: '51A-11111',
      pickupAddress: 'Điểm đón', pickupTime: '2026-12-10T03:00:00Z', endTime: '2026-12-11T03:00:00Z',
      passengerCount: 1, slotCount: 1, totalAmount: 1000000, depositAmount: 300000, charges: [], timeline: [], disputeCategories: [],
      refundPreview: { amount: 150000, ratio: 0.5, requiresBankAccount: true,
        explanation: 'Mốc hoàn 100% cọc: 10:00 ngày 09/12/2026 (ít nhất 24 giờ trước giờ nhận xe). Nếu hủy sau thời điểm này, bạn được hoàn 50% cọc.' },
    }
    return route.fulfill({ json: { data } })
  })
  await page.goto('/renter/bookings/' + code)
  await page.getByRole('button', { name: 'Huỷ đơn', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Huỷ đơn này?' })
  await expect(dialog.getByText(/24 giờ trước giờ nhận xe/)).toBeVisible()
  await expect(dialog.getByText(/Phần còn lại thuộc/)).toHaveCount(0)
  await dialog.getByLabel('Ngân hàng nhận hoàn').fill(account.bankName)
  await dialog.getByLabel('Tên chủ tài khoản').fill(account.accountHolder)
  await dialog.getByLabel('Số tài khoản nhận hoàn').fill(account.accountNumber)
  await dialog.getByRole('button', { name: 'Xác nhận huỷ', exact: true }).click()
  await expect.poll(() => received).toEqual(account)
  await expect(dialog).toHaveCount(0)
  await expect(page.getByText('Nguy cơ xe về trễ', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('Số tài khoản nhận hoàn')).toHaveValue(account.accountNumber)
})

async function mockFinance(page, onConfirm, onProof) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'finance-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe' } })
    let data = {}
    if (path.endsWith('/refunds')) data = [{ id: 1, bookingCode: code, amount: 150000, status: 'PROCESSING',
      paymentTransactionId: 12, refundMethod: 'BANK_TRANSFER', recipientBankName: account.bankName,
      recipientAccountHolder: account.accountHolder, recipientAccountNumber: account.accountNumber }]
    if (path.endsWith('/bank-accounts')) data = []
    if (path.endsWith('/transactions')) data = { items: [], total: 0 }
    if (path.endsWith('/payment-requests')) data = { items: [{ id: 10, bookingCode: code, amount: 30000,
      paidAmount: 0, remainingAmount: 30000, paymentType: 'DEPOSIT', status: 'PENDING', evidences: [] }], total: 1 }
    if (path.endsWith('/10/confirm')) { onConfirm(route.request().postDataJSON()); data = { id: 10, status: 'CONFIRMED' } }
    if (path.endsWith('/12/refund-proof') && route.request().method() === 'POST') { onProof(); data = { status: 'PAID' } }
    return route.fulfill({ json: { data } })
  })
}

test('provider sees refund recipient and submits proof without bank reference or note', async ({ page }) => {
  let submitted = false
  await mockFinance(page, () => {}, () => { submitted = true })
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu hoàn tiền/ }).click()
  const row = page.getByRole('row').filter({ hasText: code })
  await expect(row.getByText(account.accountHolder, { exact: true })).toBeVisible()
  await expect(row.getByText(account.accountNumber, { exact: true })).toBeVisible()
  await row.getByRole('button', { name: 'Xác nhận đã hoàn tiền' }).click()
  const dialog = page.getByRole('dialog', { name: 'Biên lai hoàn tiền' })
  await dialog.getByLabel('Biên lai', { exact: true }).setInputFiles({ name: 'refund.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test') })
  await dialog.getByRole('button', { name: 'Gửi biên lai xác nhận' }).click()
  await expect.poll(() => submitted).toBe(true)
  await expect(dialog).toHaveCount(0)
})

test('provider confirms received money with both optional metadata fields empty', async ({ page }) => {
  let received
  await mockFinance(page, value => { received = value }, () => {})
  await page.goto('/provider/finance')
  await page.getByRole('button', { name: /Yêu cầu thanh toán/ }).click()
  await page.getByRole('button', { name: 'Xác nhận', exact: true }).click()
  await page.getByRole('button', { name: 'Xác nhận đã nhận tiền', exact: true }).click()
  await expect.poll(() => received?.receivedAmount).toBe(30000)
  expect(received.bankTransactionRef).toBeUndefined()
  expect(received.note).toBeUndefined()
  expect(received.confirmationId).toBeTruthy()
})
