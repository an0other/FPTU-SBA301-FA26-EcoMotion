const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const VN_PHONE_RE = /^0\d{9,10}$/

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 128
export const PASSWORD_MAX_UTF8_BYTES = 72

export function getUtf8ByteLength(value) {
  return new TextEncoder().encode(value ?? '').length
}

export function getFirstLoginPasswordRules({
  temporaryPassword = '',
  newPassword = '',
  confirmPassword = '',
} = {}) {
  const cleanTemp = (temporaryPassword ?? '').trim()
  const rawNew = newPassword ?? ''
  const rawConfirm = confirmPassword ?? ''
  const byteLength = getUtf8ByteLength(rawNew)

  return {
    notBlank: rawNew.length > 0 && !isBlank(rawNew),
    minLength: rawNew.length >= PASSWORD_MIN_LENGTH,
    maxLength: rawNew.length <= PASSWORD_MAX_LENGTH && byteLength <= PASSWORD_MAX_UTF8_BYTES,
    differentFromTemp: rawNew.length > 0 && (cleanTemp.length === 0 || rawNew !== cleanTemp),
    sameAsTemp: cleanTemp.length > 0 && rawNew.length > 0 && rawNew === cleanTemp,
    hasConfirm: rawConfirm.length > 0,
    confirmMatches: rawNew.length > 0 && rawConfirm.length > 0 && rawConfirm === rawNew,
  }
}

export function validateFirstLoginForm({
  email = '',
  temporaryPassword = '',
  newPassword = '',
  confirmPassword = '',
} = {}) {
  const fieldErrors = {}
  const cleanEmail = (email ?? '').trim()
  const cleanTemp = (temporaryPassword ?? '').trim()
  const rawNew = newPassword ?? ''
  const rawConfirm = confirmPassword ?? ''
  const rules = getFirstLoginPasswordRules({
    temporaryPassword: cleanTemp,
    newPassword: rawNew,
    confirmPassword: rawConfirm,
  })

  if (isBlank(cleanEmail)) {
    fieldErrors.email = 'Vui lòng nhập email đăng nhập.'
  } else if (!isValidEmail(cleanEmail)) {
    fieldErrors.email = 'Email không đúng định dạng (ví dụ: taixe@ecomotion.vn).'
  }

  if (isBlank(cleanTemp)) {
    fieldErrors.temporaryPassword = 'Vui lòng nhập mật khẩu tạm thời nhận từ email.'
  } else if (cleanTemp.length > PASSWORD_MAX_LENGTH) {
    fieldErrors.temporaryPassword = 'Mật khẩu tạm thời vượt quá độ dài cho phép.'
  }

  if (rawNew.length === 0 || !rules.notBlank) {
    fieldErrors.newPassword = 'Vui lòng nhập mật khẩu mới (không chỉ gồm khoảng trắng).'
  } else if (!rules.minLength) {
    fieldErrors.newPassword = `Mật khẩu mới phải có ít nhất ${PASSWORD_MIN_LENGTH} ký tự.`
  } else if (!rules.maxLength) {
    fieldErrors.newPassword = 'Mật khẩu mới quá dài. Vui lòng dùng mật khẩu ngắn hơn.'
  } else if (rules.sameAsTemp) {
    fieldErrors.newPassword = 'Mật khẩu mới phải khác mật khẩu tạm thời.'
  }

  if (rawConfirm.length === 0) {
    fieldErrors.confirmPassword = 'Vui lòng nhập lại mật khẩu mới để xác nhận.'
  } else if (rawConfirm !== rawNew) {
    fieldErrors.confirmPassword = 'Xác nhận mật khẩu mới không khớp.'
  }

  const summaryError =
    fieldErrors.email
    || fieldErrors.temporaryPassword
    || fieldErrors.newPassword
    || fieldErrors.confirmPassword
    || ''

  return {
    valid: Object.keys(fieldErrors).length === 0,
    fieldErrors,
    summaryError,
    rules,
  }
}

export function isValidEmail(email) {
  return EMAIL_RE.test((email ?? '').trim())
}

export function isValidVnPhone(phone) {
  return VN_PHONE_RE.test((phone ?? '').trim())
}

export function isBlank(value) {
  return (value ?? '').trim().length === 0
}

export function formatLicensePlateInput(rawInput, prevInput = '') {
  if (!rawInput) return ''
  let val = rawInput.toUpperCase().trimStart()

  const isDeleting = prevInput && val.length < prevInput.length
  if (isDeleting) {
    return val
  }

  if (/^[0-9]{2}[A-Z]{1,2}[0-9]/.test(val)) {
    const match = val.match(/^([0-9]{2}[A-Z]{1,2})([0-9].*)$/)
    if (match) {
      return match[1] + '-' + match[2]
    }
  }

  if (/^[0-9]{2}[A-Z]$/.test(val) || /^[0-9]{2}[A-Z]{2}$/.test(val)) {
    return val + '-'
  }

  val = val.replace(/-{2,}/g, '-')
  return val
}
