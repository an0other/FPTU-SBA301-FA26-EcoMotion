import { test, expect } from '@playwright/test'

const MOCK_BOOKINGS = [
  {
    bookingId: 101,
    bookingCode: 'BK-261001',
    renterName: 'Nguyễn Văn Minh',
    renterPhone: '0901234567',
    vehicleName: 'VinFast VF 8 Plus',
    licensePlate: '51K-888.99',
    pickupTime: '2026-10-29T08:30:00Z',
    pickupAddress: '123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    depositAmount: 1500000,
    paymentStatus: 'DEPOSIT_PAID',
    driverId: null,
    driverName: null,
    status: 'CONFIRMED',
    totalAmount: 5000000,
    rentalType: 'Theo ngày',
    updatedAt: '2026-10-29T06:00:00Z',
  },
  {
    bookingId: 102,
    bookingCode: 'BK-261002',
    renterName: 'Trần Thị Ngọc Lan',
    renterPhone: '0912345678',
    vehicleName: 'VinFast VF 9 Eco',
    licensePlate: '51K-999.88',
    pickupTime: '2026-10-30T09:00:00Z',
    pickupAddress: 'Sân bay Tân Sơn Nhất, Quận Tân Bình, TP. Hồ Chí Minh',
    depositAmount: 2100000,
    paymentStatus: 'AWAITING_DEPOSIT',
    driverId: null,
    driverName: null,
    status: 'PENDING',
    totalAmount: 7000000,
    rentalType: 'Theo ngày',
    updatedAt: '2026-10-29T05:30:00Z',
  },
  {
    bookingId: 103,
    bookingCode: 'BK-261003',
    renterName: 'Lê Hoàng Nam',
    renterPhone: '0987654321',
    vehicleName: 'VinFast VF 8 Plus',
    licensePlate: '51K-888.99',
    pickupTime: '2026-10-28T07:00:00Z',
    pickupAddress: 'Landmark 81, Quận Bình Thạnh, TP. Hồ Chí Minh',
    depositAmount: 1200000,
    paymentStatus: 'FULLY_PAID',
    driverId: 12,
    driverName: 'Phạm Quốc Bảo',
    driverConfirmedAt: '2026-10-28T06:00:00Z',
    status: 'IN_PROGRESS',
    totalAmount: 4000000,
    rentalType: 'Theo ngày',
    updatedAt: '2026-10-29T04:00:00Z',
  },
]

function boxesOverlap(a, b) {
  return (
    a.left < b.right - 1 &&
    a.right > b.left + 1 &&
    a.top < b.bottom - 1 &&
    a.bottom > b.top + 1
  )
}

async function setupProviderMocks(page, queryLog = [], exportCalls = []) {
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'tv5-audit-token')
    sessionStorage.setItem(
      'ecomotion_provider_depot',
      JSON.stringify({
        name: 'EcoMotion Depot Quận 1 — Chi nhánh Trung Tâm Sài Gòn',
        address: '123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
      })
    )
  })

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/stream')) return route.abort()
    if (url.pathname.endsWith('/auth/me')) {
      return route.fulfill({
        json: {
          id: 2,
          fullName: 'Trần Quốc Tuấn',
          email: 'provider@ecomotion.vn',
          role: 'PROVIDER',
        },
      })
    }
    if (url.pathname.includes('/provider/dashboard')) {
      return route.fulfill({
        json: {
          data: {
            providerName: 'EcoMotion Depot Quận 1 — Chi nhánh Trung Tâm Sài Gòn',
            depotAddress: '123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
          },
        },
      })
    }
    if (url.pathname.endsWith('/provider/bookings/export')) {
      exportCalls.push(url.search)
      return route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/csv; charset=utf-8' },
        body: 'bookingCode,renterName\nBK-261001,Nguyen Van Minh\n',
      })
    }
    if (url.pathname.endsWith('/provider/bookings/summary')) {
      return route.fulfill({
        json: {
          data: {
            total: 25,
            pending: 4,
            accepted: 3,
            confirmed: 6,
            inProgress: 5,
            completed: 4,
            rejected: 2,
            cancelled: 1,
            vehicleOptions: ['VinFast VF 8 Plus', 'VinFast VF 9 Eco'],
          },
        },
      })
    }
    if (url.pathname.endsWith('/provider/bookings/paged') || url.pathname.endsWith('/provider/bookings')) {
      queryLog.push(Object.fromEntries(url.searchParams.entries()))
      return route.fulfill({
        json: {
          data: {
            items: MOCK_BOOKINGS,
            total: 25,
            offset: 0,
            limit: 10,
            hasMore: true,
          },
        },
      })
    }
    return route.fulfill({ json: { data: {} } })
  })
}

for (const width of [1440, 1024, 390]) {
  test(`TV5 Provider Booking Queue layout, header, icons, and filters at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const queryLog = []
    const exportCalls = []
    await setupProviderMocks(page, queryLog, exportCalls)

    await page.goto('/provider/bookings')
    await expect(page.getByRole('heading', { name: 'Quản lý đơn đặt xe' })).toBeVisible()
    await expect(page.getByText('BK-261001')).toBeVisible()
    await page.evaluate(() => document.fonts?.ready)

    // 1. Verify no horizontal page overflow
    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth)

    // 2. Verify ProviderHeader has no overlap between depot box, nav items, and user profile
    if (width >= 768) {
      const headerCheck = await page.evaluate(() => {
        const header = document.querySelector('header')
        const depotBox = header.querySelector('div[title*="EcoMotion Depot"]')?.getBoundingClientRect()
        const nav = header.querySelector('nav[aria-label="Điều hướng Provider"]')
        const navLinks = nav ? Array.from(nav.querySelectorAll('a')) : []
        const firstNav = navLinks[0]?.getBoundingClientRect()
        const lastNav = navLinks[navLinks.length - 1]?.getBoundingClientRect()
        const userBtn = header.querySelector('button')?.getBoundingClientRect()
        return { depotBox, firstNav, lastNav, userBtn }
      })
      expect(headerCheck.depotBox).toBeTruthy()
      expect(headerCheck.firstNav).toBeTruthy()
      expect(headerCheck.lastNav).toBeTruthy()
      expect(headerCheck.userBtn).toBeTruthy()
      expect(boxesOverlap(headerCheck.depotBox, headerCheck.firstNav)).toBe(false)
      expect(boxesOverlap(headerCheck.lastNav, headerCheck.userBtn)).toBe(false)
    } else {
      await expect(page.getByRole('button', { name: 'Mở menu điều hướng' })).toBeVisible()
    }

    // 3. Verify all filter controls & header buttons are ~40-44px tall and icons are strictly inside & centered
    const controlMetrics = await page.evaluate(() => {
      const checkControl = (controlEl) => {
        const rect = controlEl.getBoundingClientRect()
        const wrapper = controlEl.parentElement
        const iconEl = wrapper.querySelector('.material-symbols-rounded')
        const iconRect = iconEl ? iconEl.getBoundingClientRect() : null
        const controlCenterY = rect.top + rect.height / 2
        const iconCenterY = iconRect ? iconRect.top + iconRect.height / 2 : null
        const iconInside = iconRect
          ? iconRect.top >= rect.top - 1 &&
            iconRect.bottom <= rect.bottom + 1 &&
            iconRect.left >= rect.left - 1 &&
            iconRect.right <= rect.right + 1
          : true
        return {
          height: rect.height,
          top: rect.top,
          bottom: rect.bottom,
          iconInside,
          centerDiffY: iconCenterY != null ? Math.abs(controlCenterY - iconCenterY) : 0,
        }
      }

      const searchInput = document.getElementById('booking-search-input')
      const driverSelect = document.getElementById('booking-driver-filter')
      const vehicleSelect = document.getElementById('booking-vehicle-filter')
      const dateFrom = document.getElementById('booking-date-from')
      const dateTo = document.getElementById('booking-date-to')
      const sortSelect = document.getElementById('booking-sort-select')

      return {
        search: checkControl(searchInput),
        driver: checkControl(driverSelect),
        vehicle: checkControl(vehicleSelect),
        dateFrom: checkControl(dateFrom),
        dateTo: checkControl(dateTo),
        sort: checkControl(sortSelect),
      }
    })

    for (const [name, metric] of Object.entries(controlMetrics)) {
      expect(metric.height, `${name} height`).toBeGreaterThanOrEqual(38)
      expect(metric.height, `${name} height`).toBeLessThanOrEqual(44)
      expect(metric.iconInside, `${name} icon inside control`).toBe(true)
      expect(metric.centerDiffY, `${name} icon vertically centered`).toBeLessThanOrEqual(2.5)
    }

    // 4. Verify dateFrom and dateTo alignment
    expect(Math.abs(controlMetrics.dateFrom.height - controlMetrics.dateTo.height)).toBeLessThanOrEqual(1)
    if (width >= 640) {
      expect(Math.abs(controlMetrics.dateFrom.top - controlMetrics.dateTo.top)).toBeLessThanOrEqual(2)
    } else {
      expect(controlMetrics.dateTo.top).toBeGreaterThan(controlMetrics.dateFrom.bottom)
    }

    await page.screenshot({
      path: test.info().outputPath(`tv5-booking-queue-${width}px.png`),
      fullPage: false,
    })
  })
}

test('TV5 Provider Booking Queue interactive filters, clear filters, sort, pagination, refresh, and CSV export', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  const queryLog = []
  const exportCalls = []
  await setupProviderMocks(page, queryLog, exportCalls)

  await page.goto('/provider/bookings')
  await expect(page.getByText('BK-261001')).toBeVisible()

  // Initial sort is NEWEST
  await expect(page.getByLabel('Sắp xếp đơn đặt xe')).toHaveValue('NEWEST')

  // Switch status tab
  await page.getByRole('tab', { name: /Chờ duyệt/ }).click()
  await expect.poll(() => queryLog.at(-1)?.status).toBe('PENDING')

  // Search input
  await page.getByLabel('Tìm kiếm').fill('BK-261001')
  await expect.poll(() => queryLog.at(-1)?.search).toBe('BK-261001')

  // Driver filter
  await page.getByLabel('Lọc theo tài xế').selectOption('UNASSIGNED')
  await expect.poll(() => queryLog.at(-1)?.driver).toBe('UNASSIGNED')

  // Date filters + Vietnamese date badge verification
  await page.getByLabel('Từ ngày').fill('2026-10-01')
  await page.getByLabel('Đến ngày').fill('2026-10-15')
  await expect(page.getByText('01/10/2026')).toBeVisible()
  await expect(page.getByText('15/10/2026')).toBeVisible()
  await expect.poll(() => queryLog.at(-1)?.dateFrom).toBe('2026-10-01')
  await expect.poll(() => queryLog.at(-1)?.dateTo).toBe('2026-10-15')

  // Clear filters button ("Xóa bộ lọc")
  const clearBtn = page.getByRole('button', { name: 'Xóa bộ lọc' }).first()
  await expect(clearBtn).toBeVisible()
  await clearBtn.click()
  await expect(page.getByLabel('Tìm kiếm')).toHaveValue('')
  await expect(page.getByLabel('Lọc theo tài xế')).toHaveValue('ALL')
  await expect(page.getByLabel('Từ ngày')).toHaveValue('')
  await expect(page.getByLabel('Đến ngày')).toHaveValue('')

  // Pagination next page
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect.poll(() => queryLog.at(-1)?.offset).toBe('10')

  // Refresh button
  const prevCount = queryLog.length
  await page.getByRole('button', { name: 'Làm mới' }).click()
  await expect.poll(() => queryLog.length).toBeGreaterThan(prevCount)

  // Export CSV button
  await page.getByRole('button', { name: 'Xuất CSV' }).click()
  await expect.poll(() => exportCalls.length).toBe(1)
})

