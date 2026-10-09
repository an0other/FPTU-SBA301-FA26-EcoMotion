let pending = null

export function setPendingActivation(value) {
  pending = value
}

export function getPendingActivation() {
  return pending
}

export function clearPendingActivation() {
  pending = null
}
