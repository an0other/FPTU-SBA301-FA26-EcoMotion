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
