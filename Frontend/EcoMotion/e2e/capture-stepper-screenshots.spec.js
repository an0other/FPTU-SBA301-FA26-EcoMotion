import { test } from '@playwright/test'

const baseBooking = {
  id: 101,
  bookingId: 101,
  bookingCode: 'BK-TEST-2026',
  pickupTime: '2026-11-03T08:30:00Z',
  pickupAddress: 'Tòa nhà Landmark 81, Vinhomes Central Park, Bình Thạnh, TP.HCM',
  pickupLatitude: 10.795,
  pickupLongitude: 106.721,
  driverNote: 'Khách có 2 vali lớn',
  passengerCount: 2,
  totalAmount: 1250000,
  createdAt: '2026-10-30T07:00:00Z',
  updatedAt: '2026-10-30T07:15:00Z',
  renterName: 'Trần Minh Quang',
  renterPhone: '0909123456',
  renterEmail: 'quang.tm@gmail.com',
  vehicleId: 1,
  vehicleName: 'VinFast VF8 Plus 2024',
  licensePlate: '51K-988.22',
  brand: 'VinFast',
  model: 'VF8 Plus',
  modelYear: 2024,
  seats: 5,
  transmission: 'AUTOMATIC',
  fuelType: 'EV',
  color: 'Xanh dương đậm',
  rentalType: 'Theo ngày',
  rentalDuration: '1 ngày',
  slotPrice: 1250000,
  depositRatio: 0.3,
  allowedOvertimeMinutes: 30,
  freeRadiusKm: 50,
  canAssignDriver: false,
  canAutoAssignDriver: false,
  charges: [],
  paymentTransactions: [
    {
      id: 1,
      type: 'DEPOSIT',
      amount: 375000,
      status: 'PAID',
      paidAt: '2026-10-30T07:15:00Z',
    },
  ],
  timeline: [
    {
      title: 'Đơn thuê được khởi tạo',
      timestamp: '2026-10-30T07:00:00Z',
      description: 'Khách hàng đặt xe qua ứng dụng',
    },
    {
      title: 'Thanh toán cọc thành công',
      timestamp: '2026-10-30T07:15:00Z',
      description: 'Giao dịch VNPAY đã khớp số tiền cọc 375.000 ₫',
    },
  ],
}

function mockSetup(page, bookingOverride, workflowOverride) {
  return page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname

    if (path.endsWith('/stream')) return route.abort()
    if (path === '/api/auth/me') {
      return route.fulfill({
        json: { id: 2, role: 'PROVIDER', fullName: 'Nhà Xe EcoMotion Sài Gòn' },
      })
    }
    if (path.includes('/api/provider/bookings/')) {
      if (path.endsWith('/workflow')) {
        return route.fulfill({
          json: {
            data: {
              bookingCode: bookingOverride.bookingCode,
              bookingStatus: bookingOverride.status,
              paymentStatus: 'PARTIALLY_PAID',
              driverStatus: 'NOT_ASSIGNED',
              tripStatus: 'NOT_STARTED',
              settlementStatus: 'NOT_READY',
              confirmedPaid: 375000,
              refundedAmount: 0,
              pendingRefundAmount: 0,
              refundableAmount: 375000,
              remainingAmount: 875000,
              resolutionDeadline: bookingOverride.pickupTime,
              needsDriver: true,
              canProposeAlternative: true,
              nextAction: 'Nhà xe cần phân công tài xế trước giờ đón khách.',
              ...workflowOverride,
            },
          },
        })
      }
      if (path.endsWith('/operational-risk')) {
        return route.fulfill({
          json: { data: { atRisk: false, riskScore: 'LOW', details: [] } },
        })
      }
      if (path.endsWith('/tracking')) {
        return route.fulfill({
          json: { data: { latestLocation: { latitude: 10.795, longitude: 106.721 } } },
        })
      }
      if (path.endsWith('/incidents') || path.endsWith('/evidence')) {
        return route.fulfill({ json: { data: [] } })
      }
      return route.fulfill({ json: { data: { ...baseBooking, ...bookingOverride } } })
    }
    return route.fulfill({ json: { data: {} } })
  })
}

test('Capture Screenshots for Provider Booking Detail Stepper states', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.addInitScript(() => {
    localStorage.setItem('ecomotion_access_token', 'provider-token-mock')
    localStorage.setItem('ecomotion_user', JSON.stringify({ id: 2, role: 'PROVIDER', fullName: 'Nhà Xe EcoMotion Sài Gòn' }))
  })

  // State 1: CONFIRMED - Needs Driver
  await mockSetup(page, {
    status: 'CONFIRMED',
    canAssignDriver: true,
    canAutoAssignDriver: true,
  }, {
    driverStatus: 'NOT_ASSIGNED',
    needsDriver: true,
    nextAction: 'Chưa có tài xế phù hợp. Hãy mở giao diện điều phối để phân công tài xế ngay.',
  })
  await page.goto('/provider/bookings/BK-TEST-2026')
  await page.waitForSelector('text=Tiến trình vận hành đơn thuê')
  await page.screenshot({ path: 'docs/screenshots/provider_booking_detail_active.png', fullPage: true })

  // State 2: CANCELLED with Pending Refund
  await mockSetup(page, {
    status: 'CANCELLED',
    cancelReason: 'Khách hủy trước giờ đón',
  }, {
    bookingStatus: 'CANCELLED',
    paymentStatus: 'REFUND_PENDING',
    confirmedPaid: 375000,
    refundedAmount: 0,
    pendingRefundAmount: 375000,
    remainingAmount: 0,
    needsDriver: false,
    nextAction: 'Có khoản hoàn tiền đang xử lý. Nhà xe mở mục Tài chính để chuyển khoản và nộp biên lai.',
  })
  await page.reload()
  await page.waitForSelector('text=Tiến trình hoàn trả tiền cọc')
  await page.screenshot({ path: 'docs/screenshots/provider_booking_detail_cancelled_refund.png', fullPage: true })
})
