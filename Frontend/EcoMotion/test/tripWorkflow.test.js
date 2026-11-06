import test from 'node:test'
import assert from 'node:assert/strict'
import { getExecutionStatusConfig } from '../src/utils/driverTripUtils.js'

test('driver can continue legacy waiting trips and end an active trip', () => {
  assert.equal(getExecutionStatusConfig('AWAITING_START_CONFIRMATION').actionType, 'start')
  assert.equal(getExecutionStatusConfig('AWAITING_END_CONFIRMATION').actionType, 'finish')
  assert.equal(getExecutionStatusConfig('IN_PROGRESS').actionType, 'finish')
  assert.equal(getExecutionStatusConfig('AWAITING_EXTRA_CHARGE_CONFIRMATION').actionType, null)
})
