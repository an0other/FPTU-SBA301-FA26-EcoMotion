import { expect, test } from '@playwright/test'

const holdOpen = new Date(Date.now() + 10 * 60 * 1000).toISOString()
const holdExpired = new Date(Date.now() - 60 * 1000).toISOString()

const ALL_ITEMS = [
  {
    bookingCode: 'EM-PENDING',
    status: 'PENDING',
    planType: 'DAILY',
    vehicleName: 'VinFast VF 5',
    manufactureYear: 2025,
    licensePlate: '51A00001',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Quận 1, TP.HCM',
    pickupTime: '2026-12-20T01:00:00Z',
    endTime: '2026-12-20T05:00:00Z',
    passengerCount: 2,
    totalAmount: 1200000,
    depositAmount: 360000,
    canPay: false,
    actionKind: 'NONE',
    atRisk: false,
    riskMessage: null,
    holdExpiresAt: null,
    driverName: null,
  },
  {
    bookingCode: 'EM-PAY-1',
    status: 'AWAITING_DEPOSIT',
    planType: 'DAILY',
    vehicleName: 'Hyundai Stargazer',
    manufactureYear: 2024,
    licensePlate: '51A00002',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Thủ Đức, TP.HCM',
    pickupTime: '2026-12-21T01:00:00Z',
    endTime: '2026-12-21T05:00:00Z',
    passengerCount: 3,
    totalAmount: 1500000,
    depositAmount: 450000,
    canPay: true,
    actionKind: 'PAY_DEPOSIT',
    atRisk: false,
    riskMessage: null,
    holdExpiresAt: holdOpen,
    driverName: null,
  },
  {
    bookingCode: 'EM-EXPIRED',
    status: 'AWAITING_DEPOSIT',
    planType: 'DAILY',
    vehicleName: 'Mazda CX-5',
    manufactureYear: 2023,
    licensePlate: '51A00003',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Bình Thạnh, TP.HCM',
    pickupTime: '2026-12-22T01:00:00Z',
    endTime: '2026-12-22T05:00:00Z',
    passengerCount: 2,
    totalAmount: 1800000,
    depositAmount: 540000,
    canPay: false,
    actionKind: 'NONE',
    atRisk: false,
    riskMessage: null,
    holdExpiresAt: holdExpired,
    driverName: null,
  },
  {
    bookingCode: 'EM-RISK',
    status: 'CONFIRMED',
    planType: 'DAILY',
    vehicleName: 'Honda Civic',
    manufactureYear: 2023,
    licensePlate: '51A00004',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Vinhomes Central Park',
    pickupTime: '2026-12-23T01:00:00Z',
    endTime: '2026-12-24T01:00:00Z',
    passengerCount: 3,
    totalAmount: 2500000,
    depositAmount: 750000,
    canPay: false,
    actionKind: 'NONE',
    atRisk: true,
    riskMessage: 'Xe đang phục vụ chuyến trước và có thể về trễ.',
    holdExpiresAt: null,
    driverName: null,
  },
  {
    bookingCode: 'EM-DRIVER',
    status: 'DRIVER_ASSIGNED',
    planType: 'DAILY',
    vehicleName: 'Kia Carnival',
    manufactureYear: 2024,
    licensePlate: '51A00005',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Phú Nhuận, TP.HCM',
    pickupTime: '2026-12-25T01:00:00Z',
    endTime: '2026-12-25T08:00:00Z',
    passengerCount: 4,
    totalAmount: 3200000,
    depositAmount: 960000,
    canPay: false,
    actionKind: 'NONE',
    atRisk: false,
    riskMessage: '',
    holdExpiresAt: null,
    driverName: 'Nguyễn Văn A',
  },
  {
    bookingCode: 'EM-TRIP',
    status: 'IN_PROGRESS',
    planType: 'DAILY',
    vehicleName: 'Toyota Veloz',
    manufactureYear: 2024,
    licensePlate: '51A00006',
    imageUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=',
    pickupAddress: 'Quận 7, TP.HCM',
    pickupTime: '2026-10-26T01:00:00Z',
    endTime: '2026-10-26T05:00:00Z',
    passengerCount: 3,
    totalAmount: 900000,
    depositAmount: 270000,
    canPay: false,
    actionKind: 'CONFIRM_END',
    atRisk: false,
    riskMessage: null,
    holdExpiresAt: null,
    driverName: null,
  },
]

function wrap(items) {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      data: {
        items,
        total: items.length,
        offset: 0,
        limit: 10,
        hasMore: false,
      },
    }),
  }
}

async function mockRenterSession(page) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))
  await page.route('**/api/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ id: 10, email: 'renter@e2e.vn', fullName: 'Khách E2E', role: 'RENTER' }),
  }))
  await page.route('**/api/auth/refresh', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ accessToken: 'e2e-token' }),
  }))
  await page.route('**/api/renter/bookings**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.replace(/\/$/, '') !== '/api/renter/bookings') {
      await route.fallback()
      return
    }
    const group = url.searchParams.get('group') || 'ALL'
    const keyword = (url.searchParams.get('keyword') || '').toLowerCase()
    let items = ALL_ITEMS
    if (group === 'ACTION_NEEDED') {
      items = items.filter((item) => item.actionKind && item.actionKind !== 'NONE')
    } else if (group === 'UPCOMING') {
      items = items.filter((item) => ['CONFIRMED', 'DRIVER_ASSIGNED', 'DRIVER_CONFIRMED'].includes(item.status))
    } else if (group === 'ACTIVE') {
      items = items.filter((item) => ['DRIVER_ARRIVED', 'AWAITING_START_CONFIRMATION', 'IN_PROGRESS', 'AWAITING_END_CONFIRMATION'].includes(item.status))
    } else if (group === 'ENDED') {
      items = items.filter((item) => ['COMPLETED', 'CANCELLED', 'REJECTED'].includes(item.status))
    }
    if (keyword) {
      items = items.filter((item) => item.bookingCode.toLowerCase().includes(keyword)
        || item.vehicleName.toLowerCase().includes(keyword))
    }
    await route.fulfill(wrap(items))
  })
}

test('renter list is action-oriented with URL persistence', async ({ page }) => {
  await mockRenterSession(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/renter/bookings')

  await expect(page.getByRole('heading', { name: 'Đơn của tôi' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cần thao tác' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Sắp tới' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Đang thuê' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Đã kết thúc' })).toBeVisible()

  const pendingCard = page.getByRole('article').filter({ hasText: 'EM-PENDING' })
  await expect(pendingCard.getByText('Chờ chủ xe xác nhận')).toBeVisible()
  await expect(pendingCard.getByRole('button', { name: 'Thanh toán cọc' })).toHaveCount(0)
  await expect(pendingCard.getByRole('link', { name: 'Xem chi tiết' })).toBeVisible()

  const payCard = page.getByRole('article').filter({ hasText: 'EM-PAY-1' })
  await expect(payCard.getByText('Cọc cần thanh toán')).toBeVisible()
  await expect(payCard.getByRole('button', { name: 'Thanh toán cọc' })).toBeVisible()

  const expiredCard = page.getByRole('article').filter({ hasText: 'EM-EXPIRED' })
  await expect(expiredCard.getByText('Hết hạn giữ chỗ')).toBeVisible()
  await expect(expiredCard.getByRole('button', { name: 'Thanh toán cọc' })).toHaveCount(0)
  await expect(expiredCard.getByRole('link', { name: 'Xem chi tiết' })).toBeVisible()

  const riskCard = page.getByRole('article').filter({ hasText: 'EM-RISK' })
  await expect(riskCard.getByText('Xe có thể chưa sẵn sàng đúng giờ')).toBeVisible()
  await expect(riskCard.getByText('Nhà cung cấp đang kiểm tra.')).toBeVisible()
  await expect(riskCard.locator('aside')).toHaveCount(1)
  await expect(riskCard.getByRole('alert')).toHaveCount(0)

  await expect(page.getByRole('article').filter({ hasText: 'EM-DRIVER' }).getByText('Tài xế: Nguyễn Văn A')).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: 'EM-TRIP' }).getByText('Tài xế:')).toHaveCount(0)
  await expect(page.getByRole('article').filter({ hasText: 'EM-TRIP' }).getByRole('button', { name: 'Xác nhận kết thúc' })).toBeVisible()

  await page.getByRole('button', { name: 'Cần thao tác' }).click()
  await expect(page).toHaveURL(/group=ACTION_NEEDED/)
  await expect(page.getByRole('article').filter({ hasText: 'EM-PENDING' })).toHaveCount(0)
  await expect(page.getByRole('article').filter({ hasText: 'EM-EXPIRED' })).toHaveCount(0)
  await expect(page.getByRole('article').filter({ hasText: 'EM-PAY-1' })).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: 'EM-TRIP' })).toBeVisible()

  await page.getByRole('searchbox', { name: 'Tìm đơn thuê' }).fill('EM-PAY-1')
  await expect(page).toHaveURL(/q=EM-PAY-1/)
  await expect(page.getByRole('article')).toHaveCount(1)
  await expect(page.getByRole('article').filter({ hasText: 'EM-PAY-1' })).toBeVisible()

  await page.getByLabel('Sắp xếp đơn thuê').selectOption('NEWEST')
  await expect(page).toHaveURL(/sort=NEWEST/)

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: 'Cần thao tác' })).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: 'EM-PAY-1' }).getByRole('button', { name: 'Thanh toán cọc' })).toBeVisible()
})

test('empty group explains there are no bookings in the tab', async ({ page }) => {
  await mockRenterSession(page)
  await page.goto('/renter/bookings?group=ENDED')
  await expect(page.getByRole('heading', { name: 'Chưa có đơn ở nhóm này' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Khám phá' })).toHaveCount(0)
})
