import test from 'node:test'
import assert from 'node:assert/strict'
import { parseYearlyVnd } from '../src/utils/yearlyRental.js'

test('yearly payment preserves VND amounts with supported grouping', () => {
  for (const text of ['50000000', '50.000.000', '50,000,000', '50 000 000']) assert.equal(parseYearlyVnd(text), 50000000)
  assert.equal(parseYearlyVnd('15.100.000'), 15100000)
})
test('yearly payment never turns malformed decimals or exponents into larger transfers', () => {
  for (const text of ['', '-1000', '25.5', '25,5', '1e6', '50.00.000', '50.000,000', '9999999999999']) assert.equal(parseYearlyVnd(text), null)
})
