import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'

async function prepare(page) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'tv5-ui-test'))
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 2, role: 'PROVIDER', fullName: 'Nhà cung cấp' } })
    if (path.includes('/operational-risk') || path.includes('/incidents') || path.includes('/evidence')) return route.fulfill({ json: { data: [] } })
    if (path.endsWith('/workflow')) return route.fulfill({ json: { data: { settlementStatus: 'REVIEWING_CHARGES', paymentStatus: 'COVERED' } } })
    if (path.includes('/provider/bookings/TV5-OVERLAY')) return route.fulfill({ json: { data: {
      bookingCode: 'TV5-OVERLAY', status: 'AWAITING_EXTRA_CHARGE_CONFIRMATION', slotPrice: 100000,
      overtimeSurchargeRatio: 0.2, charges: [], vehicleName: 'Xe kiểm thử',
      pickupLatitude: 10.7769, pickupLongitude: 106.7009, dispatchOriginLatitude: 10.77, dispatchOriginLongitude: 106.70,
    } } })
    return route.fulfill({ json: { data: {} } })
  })
  await page.goto('/provider/bookings/TV5-OVERLAY')
}

for (const size of [{ width: 1920, height: 1080 }, { width: 1366, height: 768 }, { width: 390, height: 844 }]) {
  test(`modal stays above Leaflet at ${size.width}x${size.height}`, async ({ page }) => {
    await page.setViewportSize(size)
    await prepare(page)
    await expect(page.locator('.leaflet-marker-icon').first()).toBeAttached()
    await expect(page.locator('.leaflet-control-zoom')).toBeAttached()
    await page.evaluate(() => {
      const popup = document.createElement('div')
      popup.className = 'leaflet-popup'
      popup.textContent = 'Điểm đón'
      popup.style.cssText = 'position:absolute;left:50%;top:50%;background:white;z-index:700'
      document.querySelector('.leaflet-popup-pane').appendChild(popup)
    })
    await page.getByRole('button', { name: 'Thêm phụ phí' }).first().click()
    const dialog = page.getByRole('dialog', { name: 'Thêm phụ phí' })
    await expect(dialog).toBeVisible()
    expect(await dialog.evaluate((el) => el.parentElement.parentElement === document.body)).toBe(true)
    const footer = dialog.getByRole('button', { name: 'Tiếp tục' })
    await expect(footer).toBeInViewport()
    expect(await footer.evaluate((el) => {
      const rect = el.getBoundingClientRect()
      return el.contains(document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2))
    })).toBe(true)
    await dialog.locator('.overflow-y-auto').evaluate((el) => { el.scrollTop = el.scrollHeight })
    await expect(footer).toBeInViewport()
    await dialog.getByRole('button', { name: 'Đóng', exact: true }).click()
    await expect(dialog).toBeHidden()
    await page.getByRole('button', { name: 'Thêm phụ phí' }).first().click()
    await expect(dialog).toBeVisible()
  })
}

test('select, replace and remove evidence with Vietnamese controls', async ({ page }) => {
  await prepare(page)
  await page.getByRole('button', { name: 'Thêm phụ phí' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Thêm phụ phí' })
  await dialog.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(dialog.getByText('Vui lòng tải ảnh bằng chứng.')).toBeVisible()
  const file = (name) => ({ name, mimeType: 'image/png', buffer: Buffer.from('89504e470d0a1a0a', 'hex') })
  await dialog.locator('input[type=file]').setInputFiles(file('anh-cu.png'))
  await expect(dialog.getByText('anh-cu.png')).toBeVisible()
  const chooser = page.waitForEvent('filechooser')
  await dialog.getByRole('button', { name: 'Thay ảnh anh-cu.png' }).click()
  await (await chooser).setFiles(file('anh-moi.png'))
  await expect(dialog.getByText('anh-moi.png')).toBeVisible()
  await expect(dialog.getByRole('link', { name: 'Xem ảnh anh-moi.png' })).toBeVisible()
  await dialog.getByRole('button', { name: 'Xóa ảnh anh-moi.png' }).click()
  await expect(dialog.getByText('anh-moi.png')).toHaveCount(0)
})

test('upload failure permits retry and success is shown only after the server responds', async ({ page }) => {
  await prepare(page)
  let attempts = 0
  await page.route('**/api/provider/bookings/TV5-OVERLAY/charges', async (route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    attempts += 1
    await new Promise((resolve) => setTimeout(resolve, 300))
    if (attempts === 1) return route.fulfill({ status: 500, json: { message: 'Không thể tải ảnh' } })
    return route.fulfill({ json: { data: { id: 1 } } })
  })
  await page.getByRole('button', { name: 'Thêm phụ phí' }).first().click()
  const dialog = page.getByRole('dialog', { name: 'Thêm phụ phí' })
  await dialog.getByPlaceholder('Nhập chi tiết lý do phát sinh phụ phí...').fill('Trả xe quá giờ')
  await dialog.locator('input[type=file]').setInputFiles({ name: 'minh-chung.png', mimeType: 'image/png', buffer: Buffer.from('89504e470d0a1a0a', 'hex') })
  await dialog.getByRole('button', { name: 'Tiếp tục' }).click()
  await dialog.getByRole('button', { name: 'Xác nhận thêm phụ phí' }).click()
  await expect(dialog.getByText('Đang tải lên...')).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Đang tải ảnh và lưu...' })).toBeDisabled()
  await expect(dialog.getByText('Không thể tải lên hoặc lưu')).toBeVisible()
  expect(attempts).toBe(1)
  await dialog.getByRole('button', { name: 'Xác nhận thêm phụ phí' }).click()
  await expect(dialog).toBeHidden()
  expect(attempts).toBe(2)
  await expect(page.getByText('Tải ảnh lên thành công và đã ghi nhận phụ phí.')).toBeVisible()
})
