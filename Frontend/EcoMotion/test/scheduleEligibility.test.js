import test from 'node:test'
import assert from 'node:assert/strict'

const filterEligibleScheduleVehicles = (vehicles = []) =>
  vehicles.filter((v) => v.status === 'APPROVED' || v.status === 'HIDDEN')

const formatScheduleVehicleOptionLabel = (v) =>
  `${v.licensePlate} • ${v.brand} ${v.model} (${v.seatCount} chỗ)${v.status === 'HIDDEN' ? ' [Tạm ẩn]' : ''}`

test('filters out PENDING and REJECTED vehicles from block schedule selection', () => {
  const fleet = [
    { id: 1, licensePlate: '51K-111.11', brand: 'VinFast', model: 'VF 8', seatCount: 5, status: 'APPROVED' },
    { id: 2, licensePlate: '51K-222.22', brand: 'VinFast', model: 'VF 9', seatCount: 7, status: 'PENDING' },
    { id: 3, licensePlate: '51K-333.33', brand: 'VinFast', model: 'VF e34', seatCount: 5, status: 'REJECTED' },
    { id: 4, licensePlate: '51K-444.44', brand: 'VinFast', model: 'VF 5', seatCount: 5, status: 'HIDDEN' },
  ]

  const eligible = filterEligibleScheduleVehicles(fleet)
  assert.deepEqual(
    eligible.map((v) => v.id),
    [1, 4]
  )
})

test('returns empty list when provider only has PENDING or REJECTED vehicles', () => {
  const fleet = [
    { id: 2, licensePlate: '51K-222.22', status: 'PENDING' },
    { id: 3, licensePlate: '51K-333.33', status: 'REJECTED' },
  ]

  const eligible = filterEligibleScheduleVehicles(fleet)
  assert.equal(eligible.length, 0)
})

test('appends [Tạm ẩn] label for HIDDEN vehicles in modal dropdown', () => {
  const approvedVehicle = {
    id: 1,
    licensePlate: '51K-111.11',
    brand: 'VinFast',
    model: 'VF 8',
    seatCount: 5,
    status: 'APPROVED',
  }
  const hiddenVehicle = {
    id: 4,
    licensePlate: '51K-444.44',
    brand: 'VinFast',
    model: 'VF 5',
    seatCount: 5,
    status: 'HIDDEN',
  }

  assert.equal(
    formatScheduleVehicleOptionLabel(approvedVehicle),
    '51K-111.11 • VinFast VF 8 (5 chỗ)'
  )
  assert.equal(
    formatScheduleVehicleOptionLabel(hiddenVehicle),
    '51K-444.44 • VinFast VF 5 (5 chỗ) [Tạm ẩn]'
  )
})

