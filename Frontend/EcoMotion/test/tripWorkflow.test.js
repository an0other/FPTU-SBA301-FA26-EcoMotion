import test from 'node:test'
import assert from 'node:assert/strict'
import { getExecutionStatusConfig } from '../src/utils/driverTripUtils.js'

test('driver starts from ARRIVED, ends from IN_PROGRESS, and legacy waiting states offer no renter-dependent action', () => {
  assert.equal(getExecutionStatusConfig('ARRIVED').actionType, 'start')
  assert.equal(getExecutionStatusConfig('AWAITING_START_CONFIRMATION').actionType, null)
  assert.equal(getExecutionStatusConfig('AWAITING_END_CONFIRMATION').actionType, null)
  assert.equal(getExecutionStatusConfig('IN_PROGRESS').actionType, 'finish')
  assert.equal(getExecutionStatusConfig('AWAITING_EXTRA_CHARGE_CONFIRMATION').actionType, null)
})
