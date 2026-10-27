import { expect, test } from '@playwright/test'

const vehicle = {
  id: 3, name: 'VinFast VF 6', licensePlate: '51A-12345', seatCount: 5,
  manufactureYear: 2026, depositRatio: 0.3,
  plans: [{ id: 1, planType: 'DAILY', slotLengthMinutes: 1440, slotPrice: 1000000 }],
}
const paidQuote = { distanceKm: 12.2, freeRadiusKm: 10, chargeableKm: 3, outOfRadiusRate: 10000, pickupFee: 30000 }
const freeQuote = { distanceKm: 5, freeRadiusKm: 10, chargeableKm: 0, outOfRadiusRate: 10000, pickupFee: 0 }

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: {
    id: 10, email: 'renter@e2e.vn', fullName: 'Khách kiểm thử', role: 'RENTER',
  } }))
  await page.route('**/api/renter/bookings/vehicles', (route) => route.fulfill({ json: { data: [vehicle] } }))
  await page.route('**/api/vehicles/3/availability?**', (route) => route.fulfill({ json: { data: { occupiedSlots: [] } } }))
  await page.route('**/api/location/map/search?**', (route) => route.fulfill({ json: { data: [
    { address: 'Điểm đón xa', latitude: 10.9, longitude: 106.7 },
    { address: 'Điểm đón gần', latitude: 10.8, longitude: 106.7 },
  ] } }))
})

async function openPickupStep(page) {
  await page.goto('/renter/bookings/new?vehicleId=3')
  await page.locator('input[type="date"]').fill('2030-11-01')
  await page.getByRole('button', { name: 'Chọn giờ', exact: true }).click()
  await page.getByRole('button', { name: '10:00 SA', exact: true }).click()
  await page.getByRole('button', { name: 'Tiếp tục: Điểm đón' }).click()
}

async function selectPickup(page, name) {
  await page.getByPlaceholder('Tìm số nhà, tên đường hoặc địa điểm').fill('Điểm đón')
  await page.getByRole('button', { name, exact: true }).click()
}

test('pickup fees update with the point and are included once in confirmation totals', async ({ page }, testInfo) => {
  const pageErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.route('**/api/renter/bookings/pickup-quote', (route) => {
    const request = route.request().postDataJSON()
    expect(request.vehicleId).toBe(3)
    expect(request.planId).toBe(1)
    return route.fulfill({ json: { data: request.pickupLatitude === 10.9 ? paidQuote : freeQuote } })
  })
  await openPickupStep(page)
  await selectPickup(page, 'Điểm đón xa')
  await expect(page.getByText('3 km vượt (làm tròn lên)', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Tiếp tục: Xác nhận' }).click()
  const total = page.getByText('Tổng dự kiến (gồm phí đón)').locator('..')
  await expect(total).toContainText('3.030.000')
  await expect(page.getByText('900.000 đ', { exact: true })).toBeVisible()
  await total.scrollIntoViewIfNeeded()
  await page.screenshot({ path: testInfo.outputPath('pickup-fee-confirmation-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await total.scrollIntoViewIfNeeded()
  await expect(total).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('pickup-fee-confirmation-mobile.png') })
  await page.getByRole('button', { name: 'Quay lại chỉnh sửa' }).click()
  await selectPickup(page, 'Điểm đón gần')
  await expect(page.getByText('Điểm đón trong bán kính miễn phí:', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Tiếp tục: Xác nhận' }).click()
  await expect(page.getByText('Tổng dự kiến (gồm phí đón)').locator('..')).toContainText('3.000.000')
  expect(pageErrors).toEqual([])
})

test('a failed quote blocks continuing until retry succeeds', async ({ page }) => {
  let fail = true
  await page.route('**/api/renter/bookings/pickup-quote', (route) => fail
    ? route.fulfill({ status: 503, json: { message: 'Không tính được phí đón' } })
    : route.fulfill({ json: { data: paidQuote } }))
  await openPickupStep(page)
  await selectPickup(page, 'Điểm đón xa')
  await expect(page.getByText('Không tính được phí đón', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tiếp tục: Xác nhận' })).toBeDisabled()
  fail = false
  await page.getByRole('button', { name: 'Thử lại phí đón' }).click()
  await expect(page.getByRole('button', { name: 'Tiếp tục: Xác nhận' })).toBeEnabled()
  await expect(page.getByText('3 km vượt (làm tròn lên)', { exact: false })).toBeVisible()
})
