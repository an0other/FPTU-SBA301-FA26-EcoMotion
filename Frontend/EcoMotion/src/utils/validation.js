/** Lightweight client-side form validation helpers. */

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(email) {
  return EMAIL_RE.test((email ?? '').trim())
}

export function isBlank(value) {
  return (value ?? '').trim().length === 0
}
