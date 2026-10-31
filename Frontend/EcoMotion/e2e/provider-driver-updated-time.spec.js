import { expect, test } from '@playwright/test'

test('driver list sends sorting and date filters to backend and resets pagination', async ({ page }) => {
  const requests = []
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'driver-list-test'))
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    let body = { data: {} }
    if (url.pathname === '/api/auth/me') {
      body = { id: 10, email: 'provider@test.vn', fullName: 'Provider', role: 'PROVIDER' }
    } else if (url.pathname === '/api/provider/drivers/stats') {
      body = { data: { total: 25, approved: 25, pending: 0, suspended: 0, rejected: 0 } }
    } else if (url.pathname === '/api/provider/drivers') {
      requests.push(Object.fromEntries(url.searchParams))
      const offset = Number(url.searchParams.get('offset'))
      const total = url.searchParams.get('search') === 'missing' ? 0 : 25
      body = { data: {
        items: total ? [{ id: offset + 1, fullName: 'Driver ' + (offset + 1), status: 'APPROVED',
          operationalStatus: 'AVAILABLE', updatedAt: '2026-10-27T17:00:00Z', ratingCount: 0 }] : [],
        total, offset, limit: 12, hasMore: offset + 12 < total,
      } }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('/provider/drivers')
  await expect(page.getByRole('heading', { name: 'Driver 1', exact: false })).toBeVisible()
  expect(requests.at(-1)).toMatchObject({ sort: 'updated_desc', offset: '0', limit: '12' })
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page.getByRole('heading', { name: 'Driver 13', exact: false })).toBeVisible()
  await page.getByLabel('Sắp xếp tài xế').selectOption('updated_asc')
  await expect.poll(() => requests.at(-1)).toMatchObject({ sort: 'updated_asc', offset: '0' })
  await page.getByLabel('Cập nhật từ ngày').fill('2026-10-28')
  await page.getByLabel('Đến ngày', { exact: true }).fill('2026-10-28')
  await page.getByPlaceholder('Tìm kiếm theo tên, SĐT, email, GPLX...').fill('Driver')
  await page.getByLabel('Lọc trạng thái nhận chuyến').selectOption('AVAILABLE')
  await expect.poll(() => requests.at(-1)).toMatchObject({ offset: '0', sort: 'updated_asc',
    updatedFrom: '2026-10-28', updatedTo: '2026-10-28', search: 'Driver', operationalStatus: 'AVAILABLE' })
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page.getByRole('heading', { name: 'Driver 13', exact: false })).toBeVisible()
  await page.getByPlaceholder('Tìm kiếm theo tên, SĐT, email, GPLX...').fill('missing')
  await expect(page.getByText('Không tìm thấy tài xế nào')).toBeVisible()
  expect(requests.at(-1)).toMatchObject({ offset: '0', updatedFrom: '2026-10-28', search: 'missing' })
  await expect(page.getByRole('button', { name: 'Trang sau' })).toBeDisabled()
  await page.getByLabel('Cập nhật từ ngày').fill('2026-10-29')
  await expect(page.getByRole('alert')).toHaveText('Ngày bắt đầu không được sau ngày kết thúc.')
})
