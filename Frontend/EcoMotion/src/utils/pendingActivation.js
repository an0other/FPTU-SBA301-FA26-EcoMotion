/**
 * In-memory hand-off of the activation session (token + email) between the register page and the
 * standalone activation page.
 *
 * Deliberately NOT persisted. Nothing is written to localStorage or sessionStorage, so neither the
 * token nor the identity fields scanned from the CCCD are readable from web storage by an injected
 * script. The value lives only in this module's closure for the lifetime of the tab; a reload drops
 * it and the activation page falls back to its resume gate (email + OTP), which is the intended
 * trade-off.
 */
let pending = null

export function setPendingActivation(value) {
  pending = value
}

/** Non-destructive read: safe to call from a `useState` initializer (React may invoke it twice). */
export function getPendingActivation() {
  return pending
}

export function clearPendingActivation() {
  pending = null
}
