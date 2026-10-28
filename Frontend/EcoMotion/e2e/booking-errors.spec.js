import { expect, test } from '@playwright/test'

const vehicle = {
  id: 3, name: 'VinFast VF 6', licensePlate: '51A-12345', seatCount: 5,
  manufactureYear: 2026, depositRatio: 0.3,
  plans: [{ id: 1, planType: 'DAILY', slotLengthMinutes: 1440, slotPrice: 1000000 }],
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: {
    id: 10, email: 'renter@e2e.vn', fullName: 'Khách kiểm thử', role: 'RENTER',
  } }))
})

test('vehicle loading errors open a dismissible dialog and can be retried', async ({ page }) => {
  let fail = true
  await page.route('**/api/renter/bookings/vehicles', (route) => {
    return fail
      ? route.fulfill({ status: 503, json: { message: 'Không tải được danh sách xe. Vui lòng thử lại.' } })
      : route.fulfill({ json: { data: [vehicle] } })
  })
  await page.goto('/renter/bookings/new?vehicleId=3')
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Đã hiểu', exact: true })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect.poll(() => dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(page.getByText('Chưa tải được thông tin xe để đặt.')).toBeVisible()
  fail = false
  await page.getByRole('button', { name: 'Tải lại thông tin xe' }).click()
  await expect(page.getByText('Thời điểm bắt đầu', { exact: true })).toBeVisible()
})

test('booking submission errors preserve the form, restore focus and reopen on repeated failures', async ({ page }, testInfo) => {
  const message = 'Bạn đã có đơn thuê trùng khoảng thời gian này. Vui lòng chọn lịch nhận xe khác để tiếp tục đặt xe.'
  await page.route('**/api/renter/bookings/vehicles', (route) => route.fulfill({ json: { data: [vehicle] } }))
  await page.route('**/api/vehicles/3/availability?**', (route) => route.fulfill({ json: { data: { occupiedSlots: [] } } }))
  await page.route('**/api/location/map/search?**', (route) => route.fulfill({ json: { data: [
    { displayName: 'Điểm đón kiểm thử', latitude: 10.7769, longitude: 106.7009 },
  ] } }))
  await page.route('**/api/renter/bookings/pickup-quote', (route) => route.fulfill({ json: { data: {
    distanceKm: 5, freeRadiusKm: 10, chargeableKm: 0, outOfRadiusRate: 10000, pickupFee: 0,
  } } }))
  await page.route('**/api/renter/bookings', (route) => route.fulfill({ status: 409, json: { message } }))
  await page.goto('/renter/bookings/new?vehicleId=3')
  await page.locator('input[type="date"]').fill('2030-11-01')
  await page.getByRole('button', { name: 'Chọn giờ', exact: true }).click()
  await page.getByRole('button', { name: '10:00 SA', exact: true }).click()
  await page.getByRole('button', { name: 'Tiếp tục: Điểm đón' }).click()
  await page.getByPlaceholder('Tìm số nhà, tên đường hoặc địa điểm').fill('Điểm đón')
  await page.getByRole('button', { name: 'Điểm đón kiểm thử', exact: true }).click()
  await page.locator('textarea').fill('Gọi trước khi đến điểm đón')
  await page.getByRole('button', { name: 'Tiếp tục: Xác nhận' }).click()
  const submit = page.getByRole('button', { name: /^Gửi yêu cầu đặt xe/ })
  await submit.click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText(message)
  await expect(page.getByText(message, { exact: true })).toHaveCount(1)
  await page.screenshot({ path: testInfo.outputPath('booking-error-desktop.png') })
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(submit).toBeFocused()
  await expect(page.getByText('Gọi trước khi đến điểm đón', { exact: true })).toBeVisible()
  await submit.click()
  await expect(dialog).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  const bounds = await dialog.boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(390)
  await page.screenshot({ path: testInfo.outputPath('booking-error-mobile.png') })
  await dialog.getByRole('button', { name: 'Đóng thông báo lỗi' }).click()
  await expect(dialog).not.toBeVisible()
})

test('submitted booking fetch errors can be retried without showing exceptions at the top', async ({ page }) => {
  let fail = true
  await page.route('**/api/renter/bookings/BK-ERROR', (route) => {
    return fail
      ? route.fulfill({ status: 503, json: { message: 'Không tải được đơn thuê kiểm thử.' } })
      : route.fulfill({ json: { data: {
        bookingCode: 'BK-ERROR', vehicleName: vehicle.name, manufactureYear: 2026,
        licensePlate: vehicle.licensePlate, slotCount: 3, planType: 'DAILY',
        pickupTime: '2030-11-01T03:00:00Z', pickupAddress: 'Điểm đón kiểm thử', totalAmount: 3000000,
      } } })
  })
  await page.goto('/renter/bookings/BK-ERROR/submitted')
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('Không tải được đơn thuê kiểm thử.')
  fail = false
  await dialog.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await expect(page.getByRole('heading', { name: 'Lịch xe đã được giữ thành công!' })).toBeVisible()
  await expect(page.getByText('Đơn BK-ERROR', { exact: true })).toBeVisible()
})

test('yearly rental loading errors use the same dialog and retry successfully', async ({ page }) => {
  let fail = true
  await page.route('**/api/renter/driving-license', (route) => {
    return fail
      ? route.fulfill({ status: 503, json: { message: 'Không tải được thông tin GPLX kiểm thử.' } })
      : route.fulfill({ json: { data: { licenseNo: '', licenseClass: '', licenseExpiry: '' } } })
  })
  await page.goto('/renter/yearly-rental')
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('Không tải được thông tin GPLX kiểm thử.')
  fail = false
  await dialog.getByRole('button', { name: 'Thử lại', exact: true }).click()
  await expect(page.locator('input[name="licenseNo"]')).toBeVisible()
  await expect(dialog).not.toBeVisible()
})

test('payment errors stay visible in a dialog even before payment requests have loaded', async ({ page }) => {
  await page.route('**/api/v1/renter/bookings/BK-ERROR/payment-requests', (route) => route.fulfill({
    status: 503, json: { message: 'Chưa tải được yêu cầu thanh toán kiểm thử.' },
  }))
  await page.route('**/api/v1/payment/vnpay/create-url', (route) => route.fulfill({
    status: 409, json: { message: 'Đơn thuê chưa đủ điều kiện thanh toán.' },
  }))
  await page.goto('/renter/bookings/BK-ERROR/payment')
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('Chưa tải được yêu cầu thanh toán kiểm thử.')
  await dialog.getByRole('button', { name: 'Đã hiểu', exact: true }).click()
  const pay = page.getByRole('button', { name: 'Thanh toán qua VNPAY', exact: true })
  await pay.click()
  await expect(dialog).toContainText('Đơn thuê chưa đủ điều kiện thanh toán.')
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(pay).toBeFocused()
})
