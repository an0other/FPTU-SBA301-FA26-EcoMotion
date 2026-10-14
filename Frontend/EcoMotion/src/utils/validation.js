const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const VN_PHONE_RE = /^0\d{9,10}$/

export const PASSWORD_MIN_LENGTH = 8

export function isValidEmail(email) {
  return EMAIL_RE.test((email ?? '').trim())
}

export function isValidVnPhone(phone) {
  return VN_PHONE_RE.test((phone ?? '').trim())
}

export function isBlank(value) {
  return (value ?? '').trim().length === 0
}
