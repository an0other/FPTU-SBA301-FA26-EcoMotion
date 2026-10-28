import { expect, test } from '@playwright/test'

for (const width of [1280, 390]) {
  test(`assignment status, filters and focus refresh at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 850 })
    await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'availability-test'))
    let online = true
    const states = ['AVAILABLE', 'ON_TRIP', 'SCHEDULE_CONFLICT', 'PENDING_ASSIGNMENT', 'OFFLINE', 'UNAVAILABLE']
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/stream')) return route.abort()
      let data = []
      if (path === '/api/auth/me') {
        return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà xe' } })
      }
      if (path.endsWith('/BK-TEST')) data = {
        bookingCode: 'BK-TEST', status: 'CONFIRMED', bookingStatus: 'CONFIRMED',
        pickupTime: '2026-10-29T03:00:00Z', pickupAddress: 'Quận 1', totalAmount: 1000000,
        vehicleName: 'VinFast VF 8', vehicleImageUrls: [], charges: [], paymentTransactions: [], timeline: [],
      }
      if (path.endsWith('/operational-risk')) data = { atRisk: false }
      if (path.endsWith('/summary')) data = { providerName: 'Nhà xe' }
      if (path.endsWith('/assign-candidates')) data = states.map((status, index) => ({
        driverId: index + 1, fullName: `Tài xế ${index + 1}`, licenseClass: 'B2',
        eligible: index === 0 && online,
        availability: { availabilityStatus: index === 0 && !online ? 'OFFLINE' : status,
          workStatus: index === 0 && online ? 'AVAILABLE' : 'OFFLINE',
          reason: index === 0 && !online ? 'DRIVER_OFFLINE' : null,
          currentBookingCode: index === 1 ? 'BK-RUNNING' : null,
          currentTripStartedAt: index === 1 ? '2026-10-28T02:00:00Z' : null,
          nextBookingCode: index === 0 ? 'BK-TOMORROW' : null,
          nextPickupAt: index === 0 ? '2026-10-30T03:00:00Z' : null },
      }))
      await route.fulfill({ json: { data } })
    })
    await page.goto('/provider/bookings/BK-TEST')
    await page.getByRole('button', { name: '+ Phân công tài xế ngay', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Phân công tài xế' })
    await expect(dialog.getByRole('button', { name: /Tài xế 1/ })).toBeEnabled()
    await expect(dialog.getByRole('button', { name: /Tài xế 2/ })).toBeDisabled()
    await expect(dialog.getByText('BK-RUNNING', { exact: true })).toBeVisible()
    await dialog.getByLabel('Trạng thái tài xế').selectOption('ELIGIBLE')
    await expect(dialog.getByRole('button', { name: /Tài xế 2/ })).toHaveCount(0)
    await dialog.getByRole('button', { name: /Tài xế 1/ }).click()
    await expect(dialog.getByText('Xác nhận lịch trình: Tài xế 1')).toBeVisible()
    await expect(dialog.getByRole('button', { name: 'Xác nhận phân công' })).toBeEnabled()
    online = false
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(dialog.getByRole('button', { name: 'Xác nhận phân công' })).toBeDisabled()
    await dialog.getByLabel('Trạng thái tài xế').selectOption('ALL')
    await expect(dialog.getByRole('button', { name: /Tài xế 1/ })).toBeDisabled()
    await expect(dialog.getByText('Tài xế đang ngoại tuyến.')).toBeVisible()
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  })
}
