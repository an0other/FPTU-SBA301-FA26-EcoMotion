import { expect, test } from '@playwright/test'

const mockActiveTrip = {
  tripId: '101',
  bookingCode: 'BK-1P9A55',
  status: 'IN_PROGRESS',
  scheduledPickupAt: '2026-10-30T09:00:00Z',
  pickupAddress: '227 Nguyễn Văn Cừ, Quận 5, TP.HCM',
  customer: {
    fullName: 'Lê Hoàng Nam',
    phone: '0912345678',
  },
  vehicle: {
    brand: 'VinFast',
    model: 'VF8',
    licensePlate: '51K-889.99',
  },
}

test('Driver Dashboard Header - ON_TRIP state and clean Next Trip display', async ({ page }) => {
  test.setTimeout(60000)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'driver-mock-token')
    localStorage.setItem('ecomotion_user', JSON.stringify({ id: 5, role: 'DRIVER', fullName: 'Tài xế Nguyễn Văn A' }))
  })

  // Mock APIs
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname

    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') {
      return route.fulfill({
        json: { id: 5, role: 'DRIVER', fullName: 'Tài xế Nguyễn Văn A' },
      })
    }
    if (path === '/api/v1/driver/status') {
      return route.fulfill({
        json: {
          data: {
            driverId: 5,
            userId: 5,
            fullName: 'Tài xế Nguyễn Văn A',
            activityStatus: 'AVAILABLE',
            operationalStatus: 'ON_TRIP',
            statusUpdatedAt: '2026-10-30T08:00:00Z',
            activeTrip: {
              bookingCode: 'BK-1P9A55',
              vehicleName: 'VinFast VF8',
              pickupAddress: '227 Nguyễn Văn Cừ, Quận 5, TP.HCM',
              pickupTime: '2026-10-30T09:00:00Z',
              status: 'IN_PROGRESS',
            },
            nextTrip: {
              bookingCode: 'BK-9Q2Z11',
              vehicleName: 'VinFast VF8',
              pickupAddress: 'Sân bay Tân Sơn Nhất, Ga Quốc Nội',
              pickupTime: '2026-10-30T14:30:00Z',
              status: 'DRIVER_CONFIRMED',
            },
          },
        },
      })
    }
    if (path === '/api/v1/driver/trips') {
      return route.fulfill({
        json: {
          data: {
            items: [mockActiveTrip],
            total: 1,
            totalPages: 1,
            size: 10,
            page: 0,
          },
        },
      })
    }
    return route.fulfill({ json: { data: {} } })
  })

  await page.goto('/driver/schedule')

  // 1. Verify Card "Trạng thái làm việc"
  const headerCard = page.getByRole('region', { name: 'Trạng thái làm việc' })
  await expect(headerCard).toBeVisible()

  // 2. Verify Badge "Đang thực hiện chuyến"
  await expect(headerCard.getByText('Đang thực hiện chuyến', { exact: true })).toBeVisible()

  // 3. Verify Segmented Radiogroup "Chế độ nhận lịch mới" is disabled when ON_TRIP
  const radioGroup = headerCard.getByRole('radiogroup', { name: 'Chế độ nhận lịch mới' })
  await expect(radioGroup).toBeVisible()
  const availableBtn = headerCard.getByRole('radio', { name: /Sẵn sàng/ })
  await expect(availableBtn).toBeDisabled()
  await expect(availableBtn).toHaveAttribute('aria-checked', 'true')
  await expect(headerCard.getByText('Khóa tùy chọn khi đang thực hiện chuyến')).toBeVisible()

  // 4. Verify Active Trip information in Zone C
  await expect(headerCard.getByText('Chuyến đang thực hiện')).toBeVisible()
  await expect(headerCard.getByText('#BK-1P9A55')).toBeVisible()
  await expect(headerCard.getByText('227 Nguyễn Văn Cừ, Quận 5, TP.HCM')).toBeVisible()

  // 5. Verify ActiveTripBanner below also displays active trip
  const activeBanner = page.getByRole('region', { name: 'Chuyến đang thực hiện' })
  await expect(activeBanner).toBeVisible()
  await expect(activeBanner.getByText('BK-1P9A55')).toBeVisible()

  // Capture Screenshot
  await page.screenshot({ path: 'docs/screenshots/driver_dashboard_on_trip.png', fullPage: true })
})

test('Driver Dashboard Header - Toggle work modes and empty state', async ({ page }) => {
  test.setTimeout(60000)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'driver-mock-token')
    localStorage.setItem('ecomotion_user', JSON.stringify({ id: 5, role: 'DRIVER', fullName: 'Tài xế Nguyễn Văn A' }))
  })

  let currentActivity = 'AVAILABLE'
  let currentOp = 'AVAILABLE'

  const mockNextTrip = {
    bookingCode: 'BK-9Q2Z11',
    vehicleName: 'VinFast VF8',
    pickupAddress: 'Sân bay Tân Sơn Nhất, Ga Quốc Nội',
    pickupTime: '2026-10-30T14:30:00Z',
    status: 'DRIVER_CONFIRMED',
  }

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname

    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') {
      return route.fulfill({ json: { id: 5, role: 'DRIVER', fullName: 'Tài xế Nguyễn Văn A' } })
    }
    if (path === '/api/v1/driver/status') {
      if (route.request().method() === 'PUT') {
        const body = route.request().postDataJSON()
        currentActivity = body.activityStatus
        currentOp = body.activityStatus === 'AVAILABLE' ? 'SCHEDULED' : body.activityStatus
        return route.fulfill({
          json: {
            data: {
              driverId: 5,
              userId: 5,
              fullName: 'Tài xế Nguyễn Văn A',
              activityStatus: currentActivity,
              operationalStatus: currentOp,
              statusUpdatedAt: '2026-10-30T09:30:00Z',
              activeTrip: null,
              nextTrip: mockNextTrip,
            },
          },
        })
      }
      return route.fulfill({
        json: {
          data: {
            driverId: 5,
            userId: 5,
            fullName: 'Tài xế Nguyễn Văn A',
            activityStatus: currentActivity,
            operationalStatus: currentActivity === 'AVAILABLE' ? 'SCHEDULED' : currentActivity,
            statusUpdatedAt: '2026-10-30T08:00:00Z',
            activeTrip: null,
            nextTrip: mockNextTrip,
          },
        },
      })
    }
    if (path === '/api/v1/driver/trips') {
      return route.fulfill({
        json: {
          data: {
            items: [],
            total: 0,
            totalPages: 0,
            size: 10,
            page: 0,
          },
        },
      })
    }
    return route.fulfill({ json: { data: {} } })
  })

  await page.goto('/driver/schedule')

  const headerCard = page.getByRole('region', { name: 'Trạng thái làm việc' })
  await expect(headerCard).toBeVisible()

  // 1. Verify initial AVAILABLE state
  const availableBtn = headerCard.getByRole('radio', { name: /Sẵn sàng/ })
  await expect(availableBtn).toBeEnabled()
  await expect(availableBtn).toHaveAttribute('aria-checked', 'true')

  // 2. Click "Tạm nghỉ"
  const unavailableBtn = headerCard.getByRole('radio', { name: /Tạm nghỉ/ })
  await unavailableBtn.click()

  // 3. Verify updated state to UNAVAILABLE
  await expect(unavailableBtn).toHaveAttribute('aria-checked', 'true')
  await expect(headerCard.getByText('Đang tạm nghỉ')).toBeVisible()

  // 4. Verify schedule empty state contextual message for TODAY
  await expect(page.getByText('Hôm nay bạn chưa có chuyến đi nào')).toBeVisible()
  const nextTripsBtn = page.getByRole('button', { name: 'Xem chuyến sắp tới' })
  await expect(nextTripsBtn).toBeVisible()

  // 5. Verify Next Trip display in Zone C
  await expect(headerCard.getByText('Chuyến tiếp theo')).toBeVisible()
  await expect(headerCard.getByText('#BK-9Q2Z11')).toBeVisible()
  await expect(headerCard.getByText('Đã xác nhận chuyến')).toBeVisible()

  // Capture Screenshot of Available state
  await page.screenshot({ path: 'docs/screenshots/driver_dashboard_available.png', fullPage: true })
})
