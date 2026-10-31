import { expect, test } from '@playwright/test'

const next = page => page.getByRole('button', { name: 'Tiếp tục chọn điểm đón' })
async function setTime(page, kind, value) {
  const [date, hour] = value.split('T')
  await page.getByLabel(kind === 'start' ? 'Ngày nhận xe' : 'Ngày trả xe', { exact: true }).fill(date)
  await page.getByLabel(kind === 'start' ? 'Giờ nhận xe' : 'Giờ trả xe', { exact: true }).selectOption(hour)
}
async function open(page) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 10, role: 'RENTER', fullName: 'Khách E2E' } }))
  await page.route('**/api/renter/bookings/vehicles', route => route.fulfill({ json: { data: [{
    id: 3, name: 'Xe kiểm thử', licensePlate: '51A12345', seatCount: 5, depositRatio: 0.3,
    plans: [{ id: 1, planType: 'HOURLY', slotLengthMinutes: 60, slotPrice: 100000 }],
  }] } }))
  await page.goto('/renter/bookings/new?vehicleId=3')
  await setTime(page, 'start', '2030-11-01T10:00')
  await setTime(page, 'end', '2030-11-04T12:00')
}
function option(end, hours, amount) {
  return { plannedEndAt: end, coveredHours: hours, policyPlanId: 1, rentalAmount: amount, depositAmount: 100000,
    items: [{ planId: 1, planType: 'HOURLY', slotLengthMinutes: 60, quantity: hours, unitPrice: 100000 }] }
}
test('offers exact and rounded quotes with the cheapest marked and requires a choice', async ({ page }, info) => {
  const requests = []
  await page.route('**/api/renter/bookings/rental-quote', route => {
    requests.push(route.request().postDataJSON())
    return route.fulfill({ json: { data: { available: true, availableWindows: [], options: [
      { ...option('2030-11-04T05:00:00Z',74,4400000), items: [
        { planId: 2, slotLengthMinutes: 1440, unitPrice: 1000000, quantity: 3 },
        { planId: 1, slotLengthMinutes: 60, unitPrice: 700000, quantity: 2 },
      ] },
      { ...option('2030-11-05T03:00:00Z',96,4000000), items: [
        { planId: 2, slotLengthMinutes: 1440, unitPrice: 1000000, quantity: 4 },
      ] },
    ] } } })
  })
  await open(page)
  await expect(page.getByRole('region', { name: 'Phương án giá đề xuất' })).toBeVisible()
  await expect(page.getByRole('radio')).toHaveCount(2)
  expect(requests[0]).toEqual({ vehicleId: 3, pickupTime: '2030-11-01T03:00:00.000Z', requestedEndAt: '2030-11-04T05:00:00.000Z' })
  await expect(next(page)).toBeDisabled()
  const quote = page.getByRole('region', { name: 'Phương án giá đề xuất' })
  await expect(quote).toContainText('4.000.000 đ')
  await expect(quote).toContainText('4 ngày')
  await expect(quote).toContainText('4 × 1.000.000 đ')
  await expect(page.getByText('Thêm 22 giờ sử dụng so với yêu cầu, đã gồm trong giá.')).toBeVisible()
  await expect(page.getByText('Tối ưu', { exact: true })).toHaveCount(1)
  await expect(page.getByRole('radio').first().locator('..')).not.toContainText('Tối ưu')
  await expect(page.getByRole('radio').last().locator('..')).toContainText('Tối ưu')
  await page.getByRole('radio').first().check()
  await expect(next(page)).toBeEnabled()
  await page.getByRole('radio').last().check()
  await page.screenshot({ path: info.outputPath('rental-pricing-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('rental-pricing-mobile.png'), fullPage: true })
  await next(page).click()
  await expect(page.getByText('05/11/2030', { exact: false }).first()).toBeVisible()
})
test('pending and stale quotes cannot enable continuing; errors clear when the selection changes', async ({ page }) => {
  const pending = []
  await page.route('**/api/renter/bookings/rental-quote', route => { pending.push(route) })
  await open(page)
  await expect.poll(() => pending.length).toBe(1)
  await expect(next(page)).toBeDisabled()
  await setTime(page, 'end', '2030-11-04T13:00')
  await expect.poll(() => pending.length).toBe(2)
  await pending[1].fulfill({ status: 503, json: { message: 'Không tải được báo giá' } })
  await expect(page.getByRole('alert')).toContainText('Không tải được báo giá')
  await pending[0].fulfill({ json: { data: { available: true, availableWindows: [], options: [option('2030-11-04T05:00:00Z',74,4000000)] } } })
  await expect(page.getByRole('region', { name: 'Phương án giá đề xuất' })).toHaveCount(0)
  await expect(next(page)).toBeDisabled()
  await expect(page.getByRole('button', { name: /Thử lại/ })).toHaveCount(0)
  await setTime(page, 'end', '2030-11-04T14:00')
  await expect.poll(() => pending.length).toBe(3)
  await pending[2].fulfill({ json: { data: { available: true, availableWindows: [], options: [option('2030-11-04T07:00:00Z',76,4500000)] } } })
  await expect(next(page)).toBeDisabled()
  await page.getByRole('radio').check()
  await expect(next(page)).toBeEnabled()
})
test('available windows shift the request while retaining desired duration', async ({ page }) => {
  const requests = []
  await page.route('**/api/renter/bookings/rental-quote', route => {
    requests.push(route.request().postDataJSON())
    return route.fulfill({ json: { data: { available: false, reason: 'Xe bận trong khoảng đã chọn',
      searchTo: '2030-12-01T03:00:00Z', options: [], occupiedWindows: [{ startAt: '2030-11-01T05:00:00Z', endAt: '2030-11-01T08:00:00Z' }], availableWindows: [{ startAt: '2030-11-06T03:00:00Z', endAt: '2030-11-12T03:00:00Z' }] } } })
  })
  await open(page)
  const busy = page.getByRole('region', { name: 'Các khoảng xe bận' })
  await expect(busy).toContainText('12:00')
  await expect(busy).toContainText('15:00')
  await expect(busy).toContainText('thời gian chuẩn bị xe')
  await page.getByRole('button', { name: /06\/11\/2030/ }).click()
  await expect.poll(() => requests.length).toBe(2)
  expect(requests[1]).toEqual({ vehicleId: 3, pickupTime: '2030-11-06T03:00:00.000Z', requestedEndAt: '2030-11-09T05:00:00.000Z' })
  await expect(next(page)).toBeDisabled()
})

test('only offers whole-hour options and explains reversed or equal rental times', async ({ page }) => {
  await page.route('**/api/renter/bookings/rental-quote', route => {
    const request = route.request().postDataJSON()
    return route.fulfill({ json: { data: { available: true, availableWindows: [], options: [
      option(request.requestedEndAt, 74, 4000000),
    ] } } })
  })
  await open(page)
  await expect(page.getByRole('region', { name: 'Phương án giá đề xuất' })).toBeVisible()
  for (const label of ['Giờ nhận xe', 'Giờ trả xe']) {
    const hour = page.getByLabel(label, { exact: true })
    const options = await hour.locator('option').allTextContents()
    expect(options.slice(1)).toHaveLength(24)
    expect(options.slice(1).every(value => /^\d{2}:00$/.test(value))).toBe(true)
  }
  await setTime(page, 'start', '2030-11-04T13:00')
  await expect(page.getByRole('alert')).toHaveText('Thời gian trả xe phải sau thời gian nhận xe.')
  await expect(page.getByLabel('Giờ trả xe', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByRole('radio')).toHaveCount(0)
  await expect(next(page)).toBeDisabled()
  await setTime(page, 'start', '2030-11-04T12:00')
  await expect(page.getByRole('alert')).toHaveText('Thời gian trả xe phải sau thời gian nhận xe.')
  await expect(page.getByRole('button', { name: /Thử lại/ })).toHaveCount(0)
  await setTime(page, 'end', '2030-11-05T00:00')
  await expect(page.getByRole('region', { name: 'Phương án giá đề xuất' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('shows the rounded choice even when it costs more and marks the exact choice optimal', async ({ page }) => {
  await page.route('**/api/renter/bookings/rental-quote', route => route.fulfill({ json: { data: {
    available: true, availableWindows: [], options: [
      { ...option('2030-11-04T05:00:00Z',74,3200000), items: [
        { planId: 2, slotLengthMinutes: 1440, unitPrice: 1000000, quantity: 3 },
        { planId: 1, slotLengthMinutes: 60, unitPrice: 100000, quantity: 2 },
      ] },
      { ...option('2030-11-05T03:00:00Z',96,4000000), items: [
        { planId: 2, slotLengthMinutes: 1440, unitPrice: 1000000, quantity: 4 },
      ] },
    ],
  } } }))
  await open(page)
  const choices = page.getByRole('radio')
  await expect(choices).toHaveCount(2)
  await expect(choices.first().locator('..')).toContainText('Tối ưu')
  await expect(choices.last().locator('..')).not.toContainText('Tối ưu')
  await choices.last().check()
  await expect(next(page)).toBeEnabled()
})

test('shows the renters schedule conflict immediately after selecting rental times', async ({ page }) => {
  const message = 'Bạn đã có đơn thuê trùng khoảng thời gian này. Vui lòng chọn thời gian khác.'
  await page.route('**/api/renter/bookings/rental-quote', route => route.fulfill({ status: 409, json: { message } }))
  await open(page)
  await expect(page.getByRole('alert')).toHaveText(message)
  await expect(page.getByRole('radio')).toHaveCount(0)
  await expect(next(page)).toBeDisabled()
  await expect(page.getByRole('heading', { name: '1. Chọn thời gian thuê' })).toBeVisible()
})
