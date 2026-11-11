import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatLicensePlateInput,
  getFirstLoginPasswordRules,
  validateFirstLoginForm,
} from '../src/utils/validation.js'

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

test('validateFirstLoginForm rejects missing or invalid email and temporary password', () => {
  const emptyResult = validateFirstLoginForm({
    email: ' ',
    temporaryPassword: '   ',
    newPassword: '',
    confirmPassword: '',
  })
  assert.equal(emptyResult.valid, false)
  assert.ok(emptyResult.fieldErrors.email)
  assert.ok(emptyResult.fieldErrors.temporaryPassword)
  assert.ok(emptyResult.fieldErrors.newPassword)
  assert.ok(emptyResult.fieldErrors.confirmPassword)
  assert.equal(emptyResult.summaryError, emptyResult.fieldErrors.email)

  const invalidEmailResult = validateFirstLoginForm({
    email: 'khong-hop-le',
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'NewPassword@1',
    confirmPassword: 'NewPassword@1',
  })
  assert.equal(invalidEmailResult.valid, false)
  assert.ok(invalidEmailResult.fieldErrors.email)
  assert.equal(invalidEmailResult.fieldErrors.temporaryPassword, undefined)
  assert.equal(invalidEmailResult.fieldErrors.newPassword, undefined)
})

test('validateFirstLoginForm enforces min length, non-blank, UTF-8 72-byte limit, difference, and confirm match', () => {
  const shortRes = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'short',
    confirmPassword: 'short',
  })
  assert.equal(shortRes.valid, false)
  assert.match(shortRes.fieldErrors.newPassword, /8/)

  const blankSpacesRes = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: 'Ab12Cd34',
    newPassword: '        ',
    confirmPassword: '        ',
  })
  assert.equal(blankSpacesRes.valid, false)
  assert.match(blankSpacesRes.fieldErrors.newPassword, /khoảng trắng/)

  const overBytesRes = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'á'.repeat(37),
    confirmPassword: 'á'.repeat(37),
  })
  assert.equal(overBytesRes.valid, false)
  assert.match(overBytesRes.fieldErrors.newPassword, /quá dài/)

  const sameAsTempRes = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: ' Ab12Cd34 ',
    newPassword: 'Ab12Cd34',
    confirmPassword: 'Ab12Cd34',
  })
  assert.equal(sameAsTempRes.valid, false)
  assert.equal(sameAsTempRes.rules.sameAsTemp, true)
  assert.match(sameAsTempRes.fieldErrors.newPassword, /khác mật khẩu tạm thời/)

  const mismatchRes = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'NewPassword@1',
    confirmPassword: 'NewPassword@2',
  })
  assert.equal(mismatchRes.valid, false)
  assert.match(mismatchRes.fieldErrors.confirmPassword, /không khớp/)
})

test('getFirstLoginPasswordRules and validateFirstLoginForm accept valid input', () => {
  const rules = getFirstLoginPasswordRules({
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'NewPassword@1',
    confirmPassword: 'NewPassword@1',
  })
  assert.equal(rules.notBlank, true)
  assert.equal(rules.minLength, true)
  assert.equal(rules.maxLength, true)
  assert.equal(rules.differentFromTemp, true)
  assert.equal(rules.sameAsTemp, false)
  assert.equal(rules.confirmMatches, true)

  const ok = validateFirstLoginForm({
    email: 'driver@ecomotion.vn',
    temporaryPassword: 'Ab12Cd34',
    newPassword: 'NewPassword@1',
    confirmPassword: 'NewPassword@1',
  })
  assert.equal(ok.valid, true)
  assert.deepEqual(ok.fieldErrors, {})
  assert.equal(ok.summaryError, '')
})

