import test from 'node:test'
import assert from 'node:assert/strict'
import { formatLicensePlateInput } from '../src/utils/validation.js'

test('automatically adds hyphen after typing 2 digits and 1 letter', () => {
  assert.equal(formatLicensePlateInput('51k', '51'), '51K-')
  assert.equal(formatLicensePlateInput('30h', '30'), '30H-')
  assert.equal(formatLicensePlateInput('29a', '29'), '29A-')
  assert.equal(formatLicensePlateInput('43b', '43'), '43B-')
})

test('supports 2 digits and 2 letters before hyphen', () => {
  assert.equal(formatLicensePlateInput('51ld', '51l'), '51LD-')
})

test('preserves existing plate with numbers and dot', () => {
  assert.equal(formatLicensePlateInput('51K-334.12', '51K-334.1'), '51K-334.12')
  assert.equal(formatLicensePlateInput('51K-33412', '51K-3341'), '51K-33412')
})

test('auto formats pasted string without hyphen', () => {
  assert.equal(formatLicensePlateInput('51k334.12', ''), '51K-334.12')
  assert.equal(formatLicensePlateInput('30h998.24', ''), '30H-998.24')
  assert.equal(formatLicensePlateInput('51k33412', ''), '51K-33412')
})

test('allows safe backspacing without locking on hyphen', () => {
  assert.equal(formatLicensePlateInput('51K', '51K-'), '51K')
  assert.equal(formatLicensePlateInput('51', '51K'), '51')
  assert.equal(formatLicensePlateInput('5', '51'), '5')
  assert.equal(formatLicensePlateInput('', '5'), '')
})

