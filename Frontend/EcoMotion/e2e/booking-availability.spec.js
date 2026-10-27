import { expect, test } from '@playwright/test'

const freeMessage = 'Xe còn chỗ trong khung giờ này'
const nextButton = (page) => page.getByRole('button', { name: 'Tiếp tục: Điểm đón' })

async function openBooking(page) {
  await page.addInitScript(() => localStorage.setItem('ecomotion_access_token', 'e2e-token'))
  await page.route('**/api/auth/me', (route) => route.fulfill({ json: {
    id: 10, email: 'renter@e2e.vn', fullName: 'Khách E2E', role: 'RENTER',
  } }))
  await page.route('**/api/renter/bookings/vehicles', (route) => route.fulfill({ json: { data: [{
    id: 3, name: 'Xe kiểm tra lịch', licensePlate: '51A12345', seatCount: 5,
    depositRatio: 0.3, plans: [
      { id: 1, planType: 'HOURLY', slotLengthMinutes: 60, slotPrice: 100000 },
      { id: 2, planType: 'DAILY', slotLengthMinutes: 1440, slotPrice: 1000000 },
      { id: 3, planType: 'HOURLY', slotLengthMinutes: 60, slotPrice: 200000 },
    ],
  }] } }))
  await page.goto('/renter/bookings/new?vehicleId=3&planId=1')
  await expect(page.getByText('Thời điểm bắt đầu', { exact: true })).toBeVisible()
}

async function chooseTime(page, label) {
  await page.getByText('Giờ nhận xe', { exact: true }).locator('..').getByRole('button').first().click()
  await page.getByRole('button', { name: label, exact: true }).last().click()
}

function queryRange(request) {
  const url = new URL(request.url())
  return { from: url.searchParams.get('from'), to: url.searchParams.get('to') }
}

test('checks the exact rental range on date, time, slot and plan changes', async ({ page }) => {
  const requests = []
  await page.route('**/api/vehicles/3/availability?**', async (route) => {
    requests.push(queryRange(route.request()))
    await route.fulfill({ json: { data: { vehicleId: 3, occupiedSlots: [] } } })
  })
  await openBooking(page)
  await expect(nextButton(page)).toBeDisabled()
  await page.locator('input[type="date"]').fill('2030-11-01')
  await expect(page.getByText(freeMessage)).toHaveCount(0)
  await chooseTime(page, '10:00 SA')
  await expect(page.getByText(freeMessage)).toBeVisible()
  expect(requests).toEqual([{ from: '2030-11-01T03:00:00.000Z', to: '2030-11-01T06:00:00.000Z' }])

  await chooseTime(page, '11:00 SA')
  await expect.poll(() => requests.length).toBe(2)
  await expect(nextButton(page)).toBeEnabled()
  expect(requests[1]).toEqual({ from: '2030-11-01T04:00:00.000Z', to: '2030-11-01T07:00:00.000Z' })

  await page.locator('input[type="date"]').fill('2030-11-02')
  await expect.poll(() => requests.length).toBe(3)
  await expect(nextButton(page)).toBeEnabled()
  expect(requests[2]).toEqual({ from: '2030-11-02T04:00:00.000Z', to: '2030-11-02T07:00:00.000Z' })

  await page.getByRole('button', { name: '+', exact: true }).click()
  await expect.poll(() => requests.length).toBe(4)
  await expect(nextButton(page)).toBeEnabled()
  expect(requests[3]).toEqual({ from: '2030-11-02T04:00:00.000Z', to: '2030-11-02T08:00:00.000Z' })

  // A different plan must trigger a check even when its slot duration is identical.
  await page.getByRole('button', { name: /Gói giờ/ }).last().click()
  await expect.poll(() => requests.length).toBe(5)
  await expect(nextButton(page)).toBeEnabled()
  expect(requests[4]).toEqual(requests[3])

  await page.getByRole('button', { name: /Gói ngày/ }).click()
  await expect.poll(() => requests.length).toBe(6)
  await expect(nextButton(page)).toBeEnabled()
  expect(requests[5]).toEqual({ from: '2030-11-02T04:00:00.000Z', to: '2030-11-06T04:00:00.000Z' })
})

test('blocks pending, conflicting and failed checks and ignores stale responses', async ({ page }) => {
  const pending = []
  await page.route('**/api/vehicles/3/availability?**', (route) => { pending.push(route) })
  await openBooking(page)
  await page.locator('input[type="date"]').fill('2030-11-01')
  await chooseTime(page, '10:00 SA')
  await expect.poll(() => pending.length).toBe(1)
  await expect(page.getByRole('status')).toHaveText('Đang kiểm tra lịch xe...')
  await expect(nextButton(page)).toBeDisabled()
  await expect(page.getByText(freeMessage)).toHaveCount(0)

  await chooseTime(page, '11:00 SA')
  await expect.poll(() => pending.length).toBe(2)
  await pending[1].fulfill({ json: { data: { occupiedSlots: [
    { startAt: '2030-11-01T05:00:00Z', endAt: '2030-11-01T06:00:00Z', type: 'BOOKED' },
  ] } } })
  await expect(page.getByRole('status')).toHaveText('Khung giờ này đã có lịch')
  await pending[0].fulfill({ json: { data: { occupiedSlots: [] } } })
  await expect(page.getByRole('status')).toHaveText('Khung giờ này đã có lịch')
  await expect(nextButton(page)).toBeDisabled()

  await chooseTime(page, '12:00 PM')
  await expect.poll(() => pending.length).toBe(3)
  await pending[2].fulfill({ status: 500, json: { message: 'Không tải được lịch' } })
  await expect(page.getByRole('status')).toHaveText('Chưa xác định được lịch xe')
  await expect(page.getByText(freeMessage)).toHaveCount(0)
  await expect(nextButton(page)).toBeDisabled()

  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('button', { name: 'Đã hiểu', exact: true }).click()
  await page.getByRole('button', { name: 'Kiểm tra lại lịch' }).click()
  await expect.poll(() => pending.length).toBe(4)
  await expect(page.getByRole('status')).toHaveText('Đang kiểm tra lịch xe...')
  await expect(nextButton(page)).toBeDisabled()
  await pending[3].fulfill({ json: { data: { occupiedSlots: [] } } })
  await expect(page.getByRole('status')).toHaveText(freeMessage)
  await expect(nextButton(page)).toBeEnabled()
})
