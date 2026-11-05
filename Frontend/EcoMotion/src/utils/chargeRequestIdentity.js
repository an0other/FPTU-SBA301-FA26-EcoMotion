export function chargeRequestIdentity(previous, bookingCode, payload, createId) {
  const fingerprint = JSON.stringify([bookingCode, payload])
  return previous?.fingerprint === fingerprint ? previous : { fingerprint, id: createId() }
}
