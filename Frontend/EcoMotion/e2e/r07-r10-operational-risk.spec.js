import { expect, test } from '@playwright/test'

test('renter sees AT_RISK warning for a vehicle that has not returned', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))

  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ id: 10, email: 'renter@e2e.vn', fullName: 'Khách E2E', role: 'RENTER' }),
  }))
  await page.route('**/api/renter/bookings/BK-RISK/operational-risk', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ data: {
      atRisk: true,
      blockingBookingCode: 'BK-CURRENT',
      message: 'Xe đang phục vụ chuyến BK-CURRENT và chưa kết thúc; booking kế tiếp có nguy cơ trễ.',
    } }),
  }))
  await page.route('**/api/renter/bookings/BK-RISK', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ data: {
      bookingCode: 'BK-RISK', status: 'CONFIRMED', planType: 'DAILY', vehicleId: 3,
      vehicleName: 'Honda Civic Type R', manufactureYear: 2023, licensePlate: '51A12345',
      imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', pickupAddress: 'Vinhomes Central Park', pickupLatitude: 10.794,
      pickupLongitude: 106.721, pickupTime: '2026-10-24T03:00:00Z',
      endTime: '2026-10-25T03:00:00Z', passengerCount: 3, slotCount: 1,
      totalAmount: 2500000, depositAmount: 750000, createdAt: '2026-10-23T02:00:00Z',
      charges: [], timeline: [], disputeCategories: [], canCancel: true, canEditRequest: false,
      canPay: false, canConfirmStart: false, canConfirmEnd: false,
    } }),
  }))

  await page.goto('/renter/bookings/BK-RISK')

  await expect(page.getByRole('alert')).toContainText('Nguy cơ xe về trễ')
  await expect(page.getByRole('alert')).toContainText('BK-CURRENT')
  await expect(page.getByText('Nhà cung cấp sẽ chủ động đổi xe, dời lịch hoặc hủy và hoàn tiền nếu cần.')).toBeVisible()
})

test('provider can choose a replacement vehicle for an AT_RISK booking', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'provider-e2e-token'))
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ id: 2, email: 'provider@e2e.vn', fullName: 'Nhà xe E2E', role: 'PROVIDER' }),
  }))
  await page.route('**/api/provider/dashboard/summary', (route) => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ data: { providerName: 'Nhà xe E2E', depotAddress: 'TP.HCM' } }),
  }))
  await page.route('**/api/provider/bookings/BK-RISK/operational-risk', (route) => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ data: { atRisk: true, blockingBookingCode: 'BK-CURRENT', message: 'Xe chưa về từ chuyến BK-CURRENT.' } }),
  }))
  await page.route('**/api/provider/vehicles**', (route) => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ data: { items: [{ id: 4, licensePlate: '51B99999', brand: 'VinFast', model: 'VF 8' }] } }),
  }))
  await page.route('**/api/provider/bookings/BK-RISK', (route) => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ data: {
      id: 90, bookingId: 90, bookingCode: 'BK-RISK', status: 'CONFIRMED', bookingStatus: 'CONFIRMED',
      pickupTime: '2026-10-24T03:00:00Z', pickupAddress: 'Vinhomes Central Park', passengerCount: 3,
      totalAmount: 2500000, renterName: 'Khách E2E', renterPhone: '0900000000', renterEmail: 'renter@e2e.vn',
      vehicleId: 3, vehicleName: 'Honda Civic Type R', licensePlate: '51A12345', brand: 'Honda', model: 'Civic',
      modelYear: 2023, seats: 5, vehicleImageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=', vehicleImageUrls: [],
      rentalType: 'Theo ngày', rentalDuration: '1 ngày', planType: 'DAILY', charges: [], paymentTransactions: [], timeline: [],
      executionStatus: 'CONFIRMED', createdAt: '2026-10-23T02:00:00Z', updatedAt: '2026-10-23T02:00:00Z',
    } }),
  }))

  let resolutionPayload
  await page.route('**/api/provider/bookings/BK-RISK/operational-resolution', async (route) => {
    resolutionPayload = route.request().postDataJSON()
    await route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ data: { message: 'Đã đổi sang xe 51B99999' } }),
    })
  })

  await page.goto('/provider/bookings/BK-RISK')
  await page.getByRole('combobox').nth(1).selectOption('4')
  await page.getByRole('button', { name: 'Xác nhận phương án' }).click()

  await expect.poll(() => resolutionPayload).toMatchObject({
    resolution: 'CHANGE_VEHICLE', replacementVehicleId: 4,
  })
  await expect(page.getByText('Đã đổi sang xe 51B99999')).toBeVisible()
})
