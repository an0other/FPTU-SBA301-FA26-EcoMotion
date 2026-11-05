import { expect, test } from '@playwright/test'

test('provider surcharge form only offers overtime, toll, parking and other', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') {
      return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà cung cấp kiểm thử' } })
    }
    if (path.includes('/operational-risk') || path.includes('/incidents') || path.includes('/evidence')) {
      return route.fulfill({ json: { data: [] } })
    }
    if (path.endsWith('/workflow')) {
      return route.fulfill({ json: { data: { settlementStatus: 'REVIEWING_CHARGES', paymentStatus: 'COVERED' } } })
    }
    if (path.includes('/provider/bookings/TV5-CHARGE')) {
      return route.fulfill({
        json: {
          data: {
            bookingCode: 'TV5-CHARGE',
            status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION',
            slotPrice: 100000,
            overtimeSurchargeRatio: 0.2,
            charges: [],
            vehicleName: 'Xe kiểm thử',
          },
        },
      })
    }
    return route.fulfill({ json: { data: {} } })
  })

  await page.goto('/provider/bookings/TV5-CHARGE')
  await page.getByRole('button', { name: 'Thêm phụ phí' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Thêm phụ phí' })
  await expect(dialog.getByRole('option', { name: 'Phí cầu đường' })).toHaveCount(1)
  await expect(dialog.getByRole('option', { name: 'Phí gửi xe' })).toHaveCount(1)
  await expect(dialog.getByRole('option', { name: 'Phụ phí khác' })).toHaveCount(1)
  await expect(dialog.getByRole('option', { name: /Cuối tuần|Chờ/ })).toHaveCount(0)
})
